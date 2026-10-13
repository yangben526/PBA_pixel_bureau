package app.pixelbureau.logic

sealed interface SseEvent {
    data class Delta(val text: String) : SseEvent
    /** Model is still thinking. Not shown as the reply unless no answer text arrives. */
    data class Reasoning(val text: String) : SseEvent
    data object Done : SseEvent
    data class Failure(val message: String) : SseEvent
}

class ChatException(message: String) : Exception(message)

fun parseSseLine(line: String): SseEvent? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith(":")) {
        return null
    }
    if (!trimmed.startsWith("data:")) {
        return null
    }
    val data = trimmed.removePrefix("data:").trim()
    if (data == "[DONE]") {
        return SseEvent.Done
    }
    extractErrorMessage(data)?.let { return SseEvent.Failure(it) }
    val delta = extractDeltaContent(data)
    if (!delta.isNullOrEmpty()) {
        return SseEvent.Delta(delta)
    }
    val reasoning = extractReasoning(data)
    if (!reasoning.isNullOrEmpty()) {
        return SseEvent.Reasoning(reasoning)
    }
    return null
}

fun parseCompletionBody(json: String): String {
    extractErrorMessage(json)?.let { throw ChatException(it) }
    val text = visibleReply(json)
    if (text.isNotBlank()) {
        return text
    }
    if (json.trimStart().startsWith("{")) {
        throw ChatException("The model returned no text.")
    }
    throw ChatException("The model sent a response I couldn't read.")
}

/** Answer text, or reasoning when the provider left `content` empty. */
fun visibleReply(json: String): String {
    val content = extractMessageContent(json) ?: extractDeltaContent(json) ?: ""
    return content.ifBlank { extractReasoning(json).orEmpty() }
}

fun extractDeltaContent(json: String): String? {
    val deltaAt = json.indexOf("\"delta\"")
    if (deltaAt < 0) {
        return null
    }
    return extractTaggedText(json.substring(deltaAt), "content")
}

fun extractMessageContent(json: String): String? {
    val messageAt = json.indexOf("\"message\"")
    val slice = if (messageAt >= 0) json.substring(messageAt) else json
    return extractTaggedText(slice, "content")
}

fun extractReasoning(json: String): String? {
    for (field in listOf("reasoning_content", "reasoning", "thinking", "reasoning_details")) {
        extractTaggedText(json, field)?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

/**
 * String field, or an OpenAI-style content-parts array (`[{"type":"text","text":"..."}]`).
 * A JSON null returns null so callers can fall through to reasoning.
 */
fun extractTaggedText(json: String, field: String): String? {
    val key = "\"$field\""
    val keyAt = json.indexOf(key)
    if (keyAt < 0) {
        return null
    }
    var cursor = keyAt + key.length
    while (cursor < json.length && json[cursor].isWhitespace()) {
        cursor++
    }
    if (cursor >= json.length || json[cursor] != ':') {
        return null
    }
    cursor++
    while (cursor < json.length && json[cursor].isWhitespace()) {
        cursor++
    }
    if (cursor >= json.length) {
        return null
    }
    return when (json[cursor]) {
        '"' -> extractStringField(json.substring(keyAt), field)
        '[' -> {
            val end = matchingBracket(json, cursor)
            val parts = collectFieldStrings(json.substring(cursor, end), "text")
                .ifEmpty { collectFieldStrings(json.substring(cursor, end), "content") }
            parts.joinToString("").ifBlank { null }
        }
        else -> null
    }
}

private fun collectFieldStrings(json: String, field: String): List<String> {
    val key = "\"$field\""
    val out = ArrayList<String>()
    var cursor = 0
    while (cursor < json.length && out.size < 12) {
        val at = json.indexOf(key, cursor)
        if (at < 0) {
            break
        }
        val value = extractStringField(json.substring(at), field)
        if (value != null) {
            out += value
        }
        cursor = at + key.length
    }
    return out
}

private fun matchingBracket(json: String, open: Int): Int = matchingDelim(json, open, '[', ']')

private fun matchingDelim(json: String, open: Int, openChar: Char, closeChar: Char): Int {
    var depth = 0
    var index = open
    var inString = false
    while (index < json.length) {
        val ch = json[index]
        if (inString) {
            if (ch == '\\') {
                index += 2
                continue
            }
            if (ch == '"') {
                inString = false
            }
            index++
            continue
        }
        when (ch) {
            '"' -> inString = true
            openChar -> depth++
            closeChar -> {
                depth--
                if (depth == 0) {
                    return index + 1
                }
            }
        }
        index++
    }
    return json.length
}

/**
 * Provider error object only. A reply that mentions the word "error" must not
 * abort the stream.
 */
fun extractErrorMessage(json: String): String? {
    val match = ERROR_OBJECT.find(json) ?: return null
    return extractStringField(json.substring(match.range.first), "message")
}

fun extractStringField(json: String, field: String): String? {
    val key = "\"$field\""
    val keyAt = json.indexOf(key)
    if (keyAt < 0) {
        return null
    }
    var cursor = keyAt + key.length
    while (cursor < json.length && json[cursor].isWhitespace()) {
        cursor++
    }
    if (cursor >= json.length || json[cursor] != ':') {
        return null
    }
    cursor++
    while (cursor < json.length && json[cursor].isWhitespace()) {
        cursor++
    }
    if (cursor >= json.length || json[cursor] != '"') {
        return null
    }
    cursor++
    val out = StringBuilder()
    while (cursor < json.length) {
        val ch = json[cursor]
        if (ch == '\\') {
            if (cursor + 1 >= json.length) {
                return null
            }
            when (val escaped = json[cursor + 1]) {
                '"', '\\', '/' -> out.append(escaped)
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (cursor + 5 >= json.length) {
                        return null
                    }
                    val hex = json.substring(cursor + 2, cursor + 6)
                    val code = hex.toIntOrNull(16) ?: return null
                    out.append(code.toChar())
                    cursor += 6
                    continue
                }
                else -> out.append(escaped)
            }
            cursor += 2
            continue
        }
        if (ch == '"') {
            return out.toString()
        }
        out.append(ch)
        cursor++
    }
    return null
}

private val ERROR_OBJECT = Regex("\"error\"\\s*:\\s*\\{")

fun jsonString(value: String): String {
    val out = StringBuilder(value.length + 8)
    out.append('"')
    for (ch in value) {
        when (ch) {
            '\\' -> out.append("\\\\")
            '"' -> out.append("\\\"")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> {
                if (ch.code < 0x20) {
                    out.append("\\u%04x".format(ch.code))
                } else {
                    out.append(ch)
                }
            }
        }
    }
    out.append('"')
    return out.toString()
}

fun chatRequestBody(model: String, system: String, messages: List<TurnMessage>): String {
    return buildString {
        append("{\"model\":")
        append(jsonString(model))
        append(",\"stream\":true,\"messages\":[{\"role\":\"system\",\"content\":")
        append(jsonString(system))
        append('}')
        for (message in messages) {
            val role = if (message.role == "assistant") "assistant" else "user"
            append(",{\"role\":")
            append(jsonString(role))
            append(",\"content\":")
            append(jsonString(message.text))
            append('}')
        }
        append("]}")
    }
}

// ── Tool-calling helpers (OpenAI-compatible chat/completions) ───────────────

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

sealed interface ToolRoundResult {
    data class Content(val text: String) : ToolRoundResult
    data class Calls(val calls: List<ToolCall>) : ToolRoundResult
}

/**
 * Message used only inside the tool loop (user/assistant/tool roles).
 * History from the DB stays as [TurnMessage]; tool turns are ephemeral.
 */
sealed interface LlmTurn {
    data class Chat(val role: String, val content: String) : LlmTurn
    /** [content] is the text already streamed with these calls. Empty string, not JSON null. */
    data class AssistantToolCalls(val calls: List<ToolCall>, val content: String = "") : LlmTurn
    data class Tool(val toolCallId: String, val content: String) : LlmTurn
}

fun chatRequestBody(
    model: String,
    system: String,
    messages: List<TurnMessage>,
    tools: List<AgentTool>,
    stream: Boolean,
    extraTurns: List<LlmTurn> = emptyList(),
    maxTokens: Int? = null,
): String {
    return buildString {
        append("{\"model\":")
        append(jsonString(model))
        if (maxTokens != null) append(",\"max_tokens\":").append(maxTokens)
        append(",\"stream\":")
        append(stream)
        append(",\"messages\":[{\"role\":\"system\",\"content\":")
        append(jsonString(system))
        append('}')
        for (message in messages) {
            val role = if (message.role == "assistant") "assistant" else "user"
            append(",{\"role\":")
            append(jsonString(role))
            append(",\"content\":")
            append(jsonString(message.text))
            append('}')
        }
        for (turn in extraTurns) {
            append(',')
            append(llmTurnJson(turn))
        }
        append(']')
        if (tools.isNotEmpty()) {
            append(",\"tools\":[")
            tools.forEachIndexed { index, tool ->
                if (index > 0) append(',')
                append(agentToolJson(tool))
            }
            append("],\"tool_choice\":\"auto\"")
        }
        append('}')
    }
}

fun llmTurnJson(turn: LlmTurn): String {
    return when (turn) {
        is LlmTurn.Chat -> buildString {
            append("{\"role\":")
            append(jsonString(turn.role))
            append(",\"content\":")
            append(jsonString(turn.content))
            append('}')
        }
        is LlmTurn.AssistantToolCalls -> buildString {
            append("{\"role\":\"assistant\",\"content\":")
            append(jsonString(turn.content))
            append(",\"tool_calls\":[")
            turn.calls.forEachIndexed { index, call ->
                if (index > 0) append(',')
                append("{\"id\":")
                append(jsonString(call.id))
                append(",\"type\":\"function\",\"function\":{\"name\":")
                append(jsonString(call.name))
                append(",\"arguments\":")
                append(jsonString(call.arguments))
                append("}}")
            }
            append("]}")
        }
        is LlmTurn.Tool -> buildString {
            append("{\"role\":\"tool\",\"tool_call_id\":")
            append(jsonString(turn.toolCallId))
            append(",\"content\":")
            append(jsonString(turn.content))
            append('}')
        }
    }
}

fun parseToolRound(json: String): ToolRoundResult {
    extractErrorMessage(json)?.let { throw ChatException(it) }
    val calls = extractToolCalls(json)
    if (calls.isNotEmpty()) {
        return ToolRoundResult.Calls(calls)
    }
    val text = visibleReply(json)
    if (text.isBlank()) {
        throw ChatException("The model returned no text.")
    }
    return ToolRoundResult.Content(text)
}

/** One in-progress tool call while arguments arrive as SSE fragments. */
class ToolCallAcc {
    var id: String = ""
    var name: String = ""
    val arguments: StringBuilder = StringBuilder()

    fun toCall(): ToolCall? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        return ToolCall(
            id = id.ifBlank { "call_$trimmed" },
            name = trimmed,
            arguments = arguments.toString().ifBlank { "{}" },
        )
    }
}

/**
 * Merge one streamed or complete `tool_calls` payload into [calls].
 * Argument fragments are appended; a later full JSON object replaces a partial one.
 */
fun absorbStreamToolCalls(json: String, calls: MutableMap<Int, ToolCallAcc>) {
    val marker = json.indexOf("\"tool_calls\"")
    if (marker < 0) {
        absorbFunctionCall(json, calls)
        return
    }
    val slice = json.substring(marker)
    var cursor = 0
    var fallbackIndex = calls.size
    while (cursor < slice.length && calls.size < 6) {
        val indexAt = slice.indexOf("\"index\"", cursor)
        val functionAt = slice.indexOf("\"function\"", cursor)
        val next = listOf(indexAt, functionAt).filter { it >= 0 }.minOrNull() ?: break
        val index = if (indexAt >= 0 && (functionAt < 0 || indexAt <= functionAt)) {
            readJsonInt(slice, indexAt)
        } else {
            null
        }
        val slot = index ?: fallbackIndex
        val acc = calls.getOrPut(slot) { ToolCallAcc() }
        val windowEnd = slice.indexOf("\"index\"", next + 7).let { if (it < 0) slice.length else it }
        val window = slice.substring(next, windowEnd.coerceAtMost(slice.length))
        extractStringField(window, "id")?.takeIf { it.isNotBlank() }?.let { acc.id = it }
        extractStringField(window, "name")?.takeIf { it.isNotBlank() }?.let { mergeToolName(acc, it) }
        extractArguments(window)?.let { mergeToolArgs(acc, it) }
        val advanced = windowEnd.coerceAtLeast(next + 1)
        if (advanced <= cursor) {
            break
        }
        cursor = advanced
        if (index == null) {
            fallbackIndex++
        }
    }
    if (calls.isEmpty()) {
        absorbFunctionCall(json, calls)
    }
}

/** Older compatible APIs send one `function_call` object instead of `tool_calls`. */
private fun absorbFunctionCall(json: String, calls: MutableMap<Int, ToolCallAcc>) {
    val at = json.indexOf("\"function_call\"")
    if (at < 0) {
        return
    }
    val window = json.substring(at)
    val acc = calls.getOrPut(0) { ToolCallAcc() }
    extractStringField(window, "name")?.takeIf { it.isNotBlank() }?.let { mergeToolName(acc, it) }
    extractArguments(window)?.let { mergeToolArgs(acc, it) }
}

/**
 * OpenAI sends argument fragments as a string. Some gateways send a JSON object,
 * or put that object on `parameters` when `arguments` is absent.
 */
private fun extractArguments(json: String): String? {
    extractStringField(json, "arguments")?.let { return it }
    extractJsonObject(json, "arguments")?.let { return it }
    return extractJsonObject(json, "parameters")
}

private fun extractJsonObject(json: String, field: String): String? {
    val key = "\"$field\""
    val keyAt = json.indexOf(key)
    if (keyAt < 0) {
        return null
    }
    var cursor = keyAt + key.length
    while (cursor < json.length && json[cursor].isWhitespace()) {
        cursor++
    }
    if (cursor >= json.length || json[cursor] != ':') {
        return null
    }
    cursor++
    while (cursor < json.length && json[cursor].isWhitespace()) {
        cursor++
    }
    if (cursor >= json.length || json[cursor] != '{') {
        return null
    }
    return json.substring(cursor, matchingDelim(json, cursor, '{', '}'))
}

private fun mergeToolName(acc: ToolCallAcc, incoming: String) {
    acc.name = when {
        acc.name.isEmpty() -> incoming
        incoming.startsWith(acc.name) -> incoming
        acc.name.startsWith(incoming) -> acc.name
        else -> acc.name + incoming
    }
}

private fun mergeToolArgs(acc: ToolCallAcc, chunk: String) {
    if (chunk.isEmpty()) {
        return
    }
    // Constrained-decoding grammars arrive as their own argument chunk.
    // Searching that JSON returns schema articles instead of the web.
    if (isJsonSchema(chunk) && extractToolQuery(chunk).isEmpty()) {
        return
    }
    if (isJsonSchema(chunk)) {
        val query = extractToolQuery(chunk)
        acc.arguments.setLength(0)
        acc.arguments.append("{\"query\":")
        acc.arguments.append(jsonString(query))
        acc.arguments.append('}')
        return
    }
    val current = acc.arguments.toString()
    val chunkIsObject = chunk.trim().let { it.startsWith("{") && it.endsWith("}") }
    val currentIsObject = current.trim().let { it.startsWith("{") && it.endsWith("}") }
    if (chunkIsObject && (current.isEmpty() || currentIsObject)) {
        acc.arguments.setLength(0)
    }
    acc.arguments.append(chunk)
}

private fun readJsonInt(json: String, keyAt: Int): Int? {
    var cursor = keyAt
    val colon = json.indexOf(':', cursor)
    if (colon < 0) {
        return null
    }
    cursor = colon + 1
    while (cursor < json.length && json[cursor].isWhitespace()) {
        cursor++
    }
    val start = cursor
    while (cursor < json.length && (json[cursor].isDigit() || json[cursor] == '-')) {
        cursor++
    }
    if (start == cursor) {
        return null
    }
    return json.substring(start, cursor).toIntOrNull()
}

fun extractToolCalls(json: String): List<ToolCall> {
    val at = json.indexOf("\"tool_calls\"")
    if (at < 0) return emptyList()
    val slice = json.substring(at)
    val arrStart = slice.indexOf('[')
    if (arrStart < 0) return emptyList()
    // Bound the scan to this array (best-effort depth count).
    var depth = 0
    var arrEnd = slice.length
    for (i in arrStart until slice.length) {
        when (slice[i]) {
            '[' -> depth++
            ']' -> {
                depth--
                if (depth == 0) {
                    arrEnd = i + 1
                    break
                }
            }
        }
    }
    val array = slice.substring(arrStart, arrEnd)
    val calls = ArrayList<ToolCall>()
    var cursor = 0
    while (cursor < array.length && calls.size < 6) {
        val idAt = array.indexOf("\"id\"", cursor)
        if (idAt < 0) break
        val nameAt = array.indexOf("\"name\"", idAt)
        val argsAt = array.indexOf("\"arguments\"", idAt)
        if (nameAt < 0 || argsAt < 0) break
        val id = extractStringField(array.substring(idAt), "id") ?: break
        val name = extractStringField(array.substring(nameAt), "name") ?: break
        val args = extractStringField(array.substring(argsAt), "arguments") ?: "{}"
        calls += ToolCall(id = id, name = name, arguments = args)
        cursor = maxOf(nameAt, argsAt) + 1
    }
    return calls
}

/**
 * The search string inside a tool call.
 * A JSON schema (the curly-bracket grammar some models echo) is not a query.
 * When that grammar is glued in front of a real `{"query":"..."}`, keep the query.
 */
fun extractToolQuery(argumentsJson: String): String {
    val raw = argumentsJson.trim()
    if (raw.isEmpty()) {
        return ""
    }
    val usable = stringValuesFor(raw, "query").lastOrNull { value ->
        value.isNotBlank() && !isJsonSchema(value)
    }
    if (!usable.isNullOrBlank()) {
        return usable
    }
    if (!raw.startsWith("{") && !raw.startsWith("[")) {
        return raw.trim('"')
    }
    return ""
}

/** Tool-parameter grammar, not a search. */
fun isJsonSchema(text: String): Boolean {
    val trimmed = text.trim()
    return (trimmed.startsWith("{") || trimmed.startsWith("[")) &&
        trimmed.contains("\"properties\"") &&
        trimmed.contains("\"type\"")
}

private fun stringValuesFor(json: String, field: String): List<String> {
    val key = "\"$field\""
    val out = ArrayList<String>()
    var cursor = 0
    while (cursor < json.length && out.size < 8) {
        val at = json.indexOf(key, cursor)
        if (at < 0) {
            break
        }
        extractStringField(json.substring(at), field)?.let { value ->
            if (value.isNotBlank()) {
                out += value.trim()
            }
        }
        cursor = at + key.length
    }
    return out
}
