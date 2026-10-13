package app.pixelbureau.net

import app.pixelbureau.data.LlmSettings
import app.pixelbureau.logic.AgentTool
import app.pixelbureau.logic.ChatException
import app.pixelbureau.logic.LlmTurn
import app.pixelbureau.logic.SseEvent
import app.pixelbureau.logic.ToolCallAcc
import app.pixelbureau.logic.extractReasoning
import app.pixelbureau.logic.TurnMessage
import app.pixelbureau.logic.absorbStreamToolCalls
import app.pixelbureau.logic.chatCompletionsUrl
import app.pixelbureau.logic.chatRequestBody
import app.pixelbureau.logic.clipBanterLine
import app.pixelbureau.logic.extractErrorMessage
import app.pixelbureau.logic.ToolCall
import app.pixelbureau.logic.extractToolQuery
import app.pixelbureau.logic.isPlaceholderReply
import app.pixelbureau.logic.isScratchpad
import app.pixelbureau.logic.isSearchNudge
import app.pixelbureau.logic.isSearchPromise
import app.pixelbureau.logic.isSearchResultDump
import app.pixelbureau.logic.taskNeedsAnotherPass
import app.pixelbureau.logic.taskSearchQuery
import app.pixelbureau.logic.jsonString
import app.pixelbureau.logic.llmProvider
import app.pixelbureau.logic.recoverSearchQuery
import app.pixelbureau.logic.searchQueryFromContext
import app.pixelbureau.logic.timeArgumentsFromContext
import app.pixelbureau.logic.timeTool
import app.pixelbureau.logic.parseSseLine
import app.pixelbureau.logic.visibleReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/** Web lookup. [onVisit] receives a status such as "Checking out example.com…" for each page. */
fun interface AgentSearch {
    suspend fun run(query: String, onVisit: suspend (String) -> Unit): String
}

class ChatClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val search: AgentSearch = DefaultSearch,
    private val time: (String) -> String = { arguments -> timeTool(arguments) },
    private val silenceTimeoutMs: Long = SilenceTimeoutMs,
    private val retryDelayMs: Long = 1_500L,
    private val mcp: McpClient? = null,
) {
    /**
     * Streaming client. Read timeout is unlimited so a long think does not kill
     * the socket; [SilenceTimeoutMs] drops the call only when nothing arrives.
     */
    private val streamHttp: OkHttpClient = http.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Stream a reply. Each call uses its own OkHttp [okhttp3.Call] and local
     * buffers, so agents can stream in parallel on this shared client. Per-call
     * [settings] (HR base URL / key / model) stay scoped to that invocation.
     * Tool rounds stream too (same socket rules), up to [MaxToolRounds]
     * `internet_search` calls, then the answer is streamed. One retry if the
     * provider goes quiet, rate-limits, or drops the socket, including after a
     * partial briefing.
     */
    suspend fun stream(
        settings: LlmSettings,
        system: String,
        messages: List<TurnMessage>,
        tools: List<AgentTool> = emptyList(),
        deepWork: Boolean = false,
        onStatus: suspend (String) -> Unit = {},
        onDelta: suspend (String) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            val emittedText = StringBuilder()
            val emit: suspend (String) -> Unit = { text ->
                if (text.isNotEmpty()) {
                    emittedText.append(text)
                    onDelta(text)
                }
            }
            try {
                runRounds(settings, system, messages, tools, deepWork, onStatus, emit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!coroutineContext.isActive) {
                    throw CancellationException("Reply cancelled", error)
                }
                val rejectTools = emittedText.isBlank() && tools.isNotEmpty() && isToolsRejected(error)
                if (!shouldRetryStream(emittedText.toString(), error, tools.isNotEmpty())) {
                    throw error
                }
                onStatus("Trying again…")
                if (retryDelayMs > 0) {
                    delay(retryDelayMs)
                }
                emittedText.clear()
                runRounds(
                    settings,
                    system,
                    messages,
                    if (rejectTools) emptyList() else tools,
                    deepWork,
                    onStatus,
                    emit,
                )
            }
            if (emittedText.isBlank() || isPlaceholderReply(emittedText.toString())) {
                throw ChatException("The model returned no text. Try again.")
            }
        }
    }

    /**
     * One short office sentence for a speech bubble. No tools, no retry, no transcript.
     * max_tokens is omitted: reasoning models reject a tight cap and spend it on the
     * scratchpad. The prompt demands one sentence and [clipBanterLine] drops the rest.
     * Blank key or any failure returns null so the canned line stays.
     */
    suspend fun banter(settings: LlmSettings, system: String, user: String): String? =
        withContext(Dispatchers.IO) {
        if (settings.apiKey.isBlank() || system.isBlank() || user.isBlank()) {
            return@withContext null
        }
        val model = settings.model.trim().ifEmpty { llmProvider(settings.providerId).defaultModel }
        val payload = chatRequestBody(
            model,
            system,
            listOf(TurnMessage("user", user, null)),
            emptyList(),
            stream = false,
        )
        val request = Request.Builder()
            .url(chatCompletionsUrl(settings.baseUrl))
            .header("Authorization", "Bearer ${settings.apiKey.trim()}")
            .header("Accept", "application/json")
            .header("Accept-Encoding", "identity")
            .post(payload.toRequestBody(JSON))
            .build()
        val short = http.newBuilder()
            .callTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        val call = short.newCall(request)
        coroutineContext.job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }
                val body = response.body?.string().orEmpty()
                clipBanterLine(visibleReply(body))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        }

    private suspend fun runRounds(
        settings: LlmSettings,
        system: String,
        messages: List<TurnMessage>,
        tools: List<AgentTool>,
        deepWork: Boolean,
        onStatus: suspend (String) -> Unit,
        onDelta: suspend (String) -> Unit,
    ) {
        val model = settings.model.trim().ifEmpty { llmProvider(settings.providerId).defaultModel }
        val url = chatCompletionsUrl(settings.baseUrl)
        val extra = ArrayList<LlmTurn>()
        var round = 0
        var searchResult: String? = null
        var lastSearchQuery: String? = null
        var searchCount = 0
        var timeResult: String? = null
        val maxRounds = if (deepWork) MaxDeepRounds else MaxToolRounds
        val searchCap = if (deepWork) MaxDeepSearches else 2
        while (round <= maxRounds) {
            val roundTools = tools.filter { tool ->
                when (tool.id) {
                    "internet_search" -> if (deepWork) searchCount < MaxDeepSearches else searchResult == null
                    "time_tool" -> timeResult == null
                    else -> true
                }
            }
            val offerTools = roundTools.isNotEmpty() && round < maxRounds
            onStatus(
                when {
                    deepWork && extra.isEmpty() -> "Working the task…"
                    extra.isEmpty() -> "Thinking…"
                    else -> "Writing…"
                },
            )
            val turn = readRound(
                settings = settings,
                url = url,
                model = model,
                system = system,
                messages = messages,
                tools = if (offerTools) roundTools else emptyList(),
                extraTurns = extra,
                onStatus = onStatus,
                onDelta = onDelta,
            )
            val calls = turn.calls.values.mapNotNull { it.toCall() }
            val visible = turn.content.toString().trim()
            val reasoned = turn.reasoning.toString().trim()
            val plan = listOf(visible, reasoned).filter { it.isNotBlank() }.joinToString("\n")
            val scratchVisible = isScratchpad(visible)
            val scratch = scratchVisible || isScratchpad(reasoned)
            if (offerTools && calls.isNotEmpty()) {
                if (scratchVisible) {
                    onStatus("Answering…")
                }
                val prepared = calls.map { call -> fillSearchCall(call, plan, messages) }
                extra += LlmTurn.AssistantToolCalls(
                    prepared,
                    if (scratchVisible) "" else turn.content.toString(),
                )
                for (call in prepared) {
                    when (call.name) {
                        "internet_search" -> onStatus("Searching the web…")
                        "time_tool" -> onStatus("Checking the time…")
                    }
                    val result = executeTool(call.name, call.arguments, onStatus)
                    val searched = extractToolQuery(call.arguments)
                    if (call.name == "internet_search" && searched.isNotBlank()) {
                        searchResult = result
                        lastSearchQuery = searched
                        searchCount++
                    }
                    if (call.name == "time_tool") {
                        timeResult = result
                    }
                    extra += LlmTurn.Tool(call.id, result)
                }
                round++
                continue
            }
            if (scratch && searchResult == null && timeResult == null && round < maxRounds) {
                if (visible.isNotEmpty()) {
                    onStatus("Answering…")
                }
                val timeArgs = if (tools.any { it.id == "time_tool" }) {
                    timeArgumentsFromContext(plan, messages)
                } else {
                    null
                }
                val explicitSearch = recoverSearchQuery(plan)
                    ?: messages.firstNotNullOfOrNull { message ->
                        if (message.role == "assistant") null else recoverSearchQuery(message.text)
                    }
                val recovered = searchQueryFromContext("", plan, messages)
                if (timeArgs != null && (plan.contains("time_tool") || explicitSearch == null)) {
                    onStatus("Checking the time…")
                    val call = ToolCall(
                        id = "call_time_$round",
                        name = "time_tool",
                        arguments = timeArgs,
                    )
                    extra += LlmTurn.AssistantToolCalls(listOf(call), "")
                    val result = executeTool(call.name, call.arguments, onStatus)
                    timeResult = result
                    extra += LlmTurn.Tool(call.id, result)
                } else if (tools.any { it.id == "internet_search" } && !recovered.isNullOrBlank()) {
                    onStatus("Searching the web…")
                    val call = ToolCall(
                        id = "call_search_$round",
                        name = "internet_search",
                        arguments = "{\"query\":${jsonString(recovered)}}",
                    )
                    extra += LlmTurn.AssistantToolCalls(listOf(call), "")
                    val result = executeTool(call.name, call.arguments, onStatus)
                    searchResult = result
                    lastSearchQuery = recovered
                    searchCount++
                    extra += LlmTurn.Tool(call.id, result)
                } else {
                    extra += LlmTurn.Chat(
                        "user",
                        "Write the finished reply the user should read. Do not describe your plan or the tool format.",
                    )
                }
                round++
                continue
            }
            if (isSearchPromise(visible) && round < maxRounds) {
                onStatus("Answering…")
                val query = searchQueryFromContext("", plan, messages)
                val sameQuery = query != null && query.equals(lastSearchQuery, ignoreCase = true)
                if (tools.any { it.id == "internet_search" } &&
                    !query.isNullOrBlank() &&
                    !sameQuery &&
                    searchCount < searchCap
                ) {
                    onStatus("Searching the web…")
                    val call = ToolCall(
                        id = "call_search_$round",
                        name = "internet_search",
                        arguments = "{\"query\":${jsonString(query)}}",
                    )
                    extra += LlmTurn.AssistantToolCalls(listOf(call), "")
                    val result = executeTool(call.name, call.arguments, onStatus)
                    searchResult = result
                    lastSearchQuery = query
                    searchCount++
                    extra += LlmTurn.Tool(call.id, result)
                }
                extra += LlmTurn.Chat("user", TailoredAnswerNudge)
                round++
                continue
            }
            val dump = isSearchResultDump(visible)
            if (visible.isNotEmpty() &&
                !isPlaceholderReply(visible) &&
                !scratchVisible &&
                !dump &&
                !isSearchPromise(visible)
            ) {
                val searchOn = tools.any { it.id == "internet_search" }
                if (deepWork &&
                    round < maxRounds &&
                    taskNeedsAnotherPass(visible, searchCount, searchOn, MaxDeepSearches)
                ) {
                    onStatus("Answering…")
                    val query = taskSearchQuery(messages)
                    if (searchOn &&
                        searchCount == 0 &&
                        !query.isNullOrBlank() &&
                        !query.equals(lastSearchQuery, ignoreCase = true)
                    ) {
                        onStatus("Searching the web…")
                        val call = ToolCall(
                            id = "call_task_$round",
                            name = "internet_search",
                            arguments = "{\"query\":${jsonString(query)}}",
                        )
                        extra += LlmTurn.AssistantToolCalls(listOf(call), "")
                        val result = executeTool(call.name, call.arguments, onStatus)
                        searchResult = result
                        lastSearchQuery = query
                        searchCount++
                        extra += LlmTurn.Tool(call.id, result)
                    }
                    extra += LlmTurn.Chat("user", DeepWorkNudge)
                    round++
                    continue
                }
                return
            }
            val found = searchResult
            if (found != null && (scratch || dump || visible.isEmpty() || isPlaceholderReply(visible))) {
                if (visible.isNotEmpty()) {
                    onStatus("Answering…")
                }
                if (round < maxRounds) {
                    extra += LlmTurn.Chat("user", TailoredAnswerNudge)
                    round++
                    continue
                }
                throw ChatException("The model returned no text. Try again.")
            }
            if (dump) {
                if (visible.isNotEmpty()) {
                    onStatus("Answering…")
                }
                if (round < maxRounds) {
                    extra += LlmTurn.Chat("user", TailoredAnswerNudge)
                    round++
                    continue
                }
                throw ChatException("The model returned no text. Try again.")
            }
            val timed = timeResult
            if (timed != null && (scratch || visible.isEmpty() || isPlaceholderReply(visible))) {
                onStatus("Answering…")
                onDelta(timed)
                return
            }
            if (scratchVisible || (visible.isEmpty() && isScratchpad(reasoned))) {
                if (visible.isNotEmpty()) {
                    onStatus("Answering…")
                }
                throw ChatException("The model returned no text. Try again.")
            }
            if (visible.isNotEmpty()) {
                if (isPlaceholderReply(visible)) {
                    throw ChatException("The model returned no text. Try again.")
                }
                return
            }
            if (reasoned.isNotEmpty() && !isPlaceholderReply(reasoned) && !isScratchpad(reasoned)) {
                onDelta(reasoned)
                return
            }
            throw ChatException("The model returned no text. Try again.")
        }
    }

    private fun fillSearchCall(call: ToolCall, plan: String, messages: List<TurnMessage>): ToolCall {
        if (call.name != "internet_search") {
            return call
        }
        val raw = extractToolQuery(call.arguments)
        if (raw.isNotBlank() && !isSearchNudge(raw)) {
            return call
        }
        val recovered = searchQueryFromContext("", plan, messages) ?: return call
        return call.copy(arguments = "{\"query\":${jsonString(recovered)}}")
    }

    private suspend fun executeTool(
        name: String,
        argumentsJson: String,
        onStatus: suspend (String) -> Unit,
    ): String {
        return when (name) {
            "internet_search" -> {
                val query = extractToolQuery(argumentsJson)
                if (query.isBlank()) {
                    "Search was not run. Answer from the conversation. Do not describe tool syntax."
                } else {
                    search.run(query, onStatus)
                }
            }
            "time_tool" -> time(argumentsJson)
            else -> {
                val client = mcp
                if (client != null && client.isMcpTool(name)) {
                    onStatus("Using ${name.substringAfter("__")}…")
                    client.callNamespaced(name, argumentsJson)
                } else {
                    "Unknown tool: $name"
                }
            }
        }
    }

    private class Round {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val calls = LinkedHashMap<Int, ToolCallAcc>()
    }

    private suspend fun readRound(
        settings: LlmSettings,
        url: String,
        model: String,
        system: String,
        messages: List<TurnMessage>,
        tools: List<AgentTool>,
        extraTurns: List<LlmTurn>,
        onStatus: suspend (String) -> Unit,
        onDelta: suspend (String) -> Unit,
    ): Round {
        val cap = kotlin.coroutines.coroutineContext[ReplyTokenCap]?.tokens
        val payload = chatRequestBody(model, system, messages, tools, stream = true, extraTurns = extraTurns, maxTokens = cap)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${settings.apiKey.trim()}")
            .header("Accept", "text/event-stream")
            .header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-cache")
            .post(payload.toRequestBody(JSON))
            .build()
        val call = streamHttp.newCall(request)
        coroutineContext.job.invokeOnCompletion { call.cancel() }
        val round = Round()
        val timedOut = AtomicBoolean(false)
        val lastActivity = AtomicLong(System.currentTimeMillis())
        val monitor = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val heartbeat = monitor.launch {
            while (isActive) {
                delay(HeartbeatCheckMs)
                if (System.currentTimeMillis() - lastActivity.get() > silenceTimeoutMs) {
                    timedOut.set(true)
                    call.cancel()
                    break
                }
            }
        }
        try {
            try {
                call.execute().use { response ->
                    val body = response.body ?: throw ChatException("The model returned an empty body.")
                    if (!response.isSuccessful) {
                        val text = body.string()
                        val detail = extractErrorMessage(text)
                        throw ChatException(
                            if (detail.isNullOrBlank()) {
                                "The model returned HTTP ${response.code}."
                            } else {
                                "HTTP ${response.code}: $detail"
                            },
                        )
                    }
                    val source = body.source()
                    val raw = StringBuilder()
                    var sawSse = false
                    while (coroutineContext.isActive) {
                        val line = try {
                            source.readUtf8Line()
                        } catch (error: IOException) {
                            if (!coroutineContext.isActive) {
                                throw CancellationException("Reply cancelled", error)
                            }
                            if (timedOut.get()) {
                                break
                            }
                            throw error
                        } ?: break
                        raw.append(line).append('\n')
                        val payloadLine = line.trim()
                        if (payloadLine.startsWith("data:")) {
                            sawSse = true
                            val data = payloadLine.removePrefix("data:").trim()
                            // Tool-call fragments are progress even when content is still empty.
                            if (data.contains("\"tool_calls\"") || data.contains("\"function_call\"")) {
                                lastActivity.set(System.currentTimeMillis())
                            }
                            absorbStreamToolCalls(data, round.calls)
                        }
                        when (val event = parseSseLine(line)) {
                            is SseEvent.Delta -> {
                                lastActivity.set(System.currentTimeMillis())
                                round.content.append(event.text)
                                onStatus("Writing…")
                                onDelta(event.text)
                            }
                            is SseEvent.Reasoning -> {
                                lastActivity.set(System.currentTimeMillis())
                                round.reasoning.append(event.text)
                                if (round.content.isEmpty()) {
                                    onStatus("Thinking…")
                                }
                            }
                            is SseEvent.Failure -> throw ChatException(event.message)
                            SseEvent.Done -> break
                            null -> Unit
                        }
                    }
                    if (!sawSse && round.content.isEmpty() && round.calls.isEmpty()) {
                        val text = raw.toString().trim()
                        if (text.startsWith("{")) {
                            extractErrorMessage(text)?.let { throw ChatException(it) }
                            absorbStreamToolCalls(text, round.calls)
                            val reply = visibleReply(text)
                            if (reply.isNotEmpty()) {
                                round.content.append(reply)
                                onDelta(reply)
                            } else {
                                extractReasoning(text)?.let { round.reasoning.append(it) }
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                if (!coroutineContext.isActive) {
                    throw CancellationException("Reply cancelled", error)
                }
                if (!timedOut.get()) {
                    throw error
                }
            }
            if (timedOut.get()) {
                throw ChatException(
                    "The model went quiet for 3 minutes and the reply was interrupted. Try again.",
                )
            }
            return round
        } finally {
            heartbeat.cancel()
            monitor.cancel()
        }
    }

    /**
     * One more attempt after a quiet provider, a rate limit, or a dropped socket.
     * A partial briefing still counts as worth retrying. A bad key does not.
     */
    internal fun shouldRetryStream(emittedText: String, error: Exception, toolsOffered: Boolean): Boolean {
        val nothingKept = emittedText.isBlank() || isPlaceholderReply(emittedText)
        val rejectTools = nothingKept && toolsOffered && isToolsRejected(error)
        if (rejectTools) {
            return true
        }
        if (!isTransient(error)) {
            return false
        }
        val message = error.message.orEmpty().lowercase()
        val logicalMiss = message.contains("no text") || message.contains("empty body")
        // "No text" after a real paragraph is the model stopping, not a dropped call.
        return !logicalMiss || nothingKept
    }

    /** A model that cannot take tools should still answer, with tools left off. */
    private fun isToolsRejected(error: Exception): Boolean {
        val message = error.message?.lowercase().orEmpty()
        val rejected = message.contains("http 400") || message.contains("http 422")
        return rejected && message.contains("tool")
    }

    private fun isTransient(error: Exception): Boolean {
        val message = error.message?.lowercase().orEmpty()
        if (message.contains("http 401") || message.contains("http 400") || message.contains("http 403") || message.contains("http 404")) {
            return false
        }
        return message.contains("went quiet") ||
            message.contains("no text") ||
            message.contains("empty body") ||
            message.contains("timeout") ||
            message.contains("timed out") ||
            message.contains("reset") ||
            message.contains("socket") ||
            message.contains("closed") ||
            message.contains("http 429") ||
            message.contains("http 502") ||
            message.contains("http 503") ||
            message.contains("http 504")
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val MaxToolRounds = 3
        const val MaxDeepRounds = 6
        const val MaxDeepSearches = 4
        const val DeepWorkNudge =
            "This is an assigned task, not a short chat. Search for the facts still missing, then write the full briefing. " +
                "Use ## sections and a table when you have numbers. " +
                "For a company, include price, P/E, market cap, shares, the 52-week range, volume, and recent results. " +
                "For any other subject, go just as deep on its own facts. Say what you could not find. Do not stop at a short opinion."
        const val TailoredAnswerNudge =
            "Answer the user's question in your own words using the notes above. " +
                "Write the reply they should read. Do not paste the result list, the page text, or the query, and do not tell them to search."
        const val SilenceTimeoutMs = 180_000L
        const val HeartbeatCheckMs = 5_000L
        private val DefaultSearchClient = InternetSearchClient()
        val DefaultSearch = AgentSearch { query, onVisit ->
            DefaultSearchClient.search(query, onVisit = onVisit)
        }
    }
}

/** Optional max_tokens for replies streamed inside this coroutine (short taskforce turns). */
class ReplyTokenCap(val tokens: Int) : kotlin.coroutines.AbstractCoroutineContextElement(ReplyTokenCap) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<ReplyTokenCap>
}
