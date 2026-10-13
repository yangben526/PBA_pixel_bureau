package app.pixelbureau

import app.pixelbureau.data.LlmSettings
import app.pixelbureau.logic.AgentTool
import app.pixelbureau.logic.AgentToolKind
import app.pixelbureau.logic.ChatException
import app.pixelbureau.logic.TurnMessage
import app.pixelbureau.net.AgentSearch
import app.pixelbureau.net.ChatClient
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ChatClientTest {
    private val server = MockWebServer()

    @Before
    fun start() {
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    @Test
    fun reasoningOnlyIsTheReplyAndContentIsNotLabeledNoReply() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"reasoning_content":"The answer is 4."}}]}""",
                "data: [DONE]",
            ),
        )
        val statuses = ArrayList<String>()
        val reply = StringBuilder()
        client().stream(
            settings(),
            "system",
            listOf(TurnMessage("user", "2+2", null)),
            onStatus = { statuses += it },
        ) { reply.append(it) }
        assertEquals("The answer is 4.", reply.toString())
        assertFalse(reply.contains("no reply"))
        assertTrue(statuses.any { it.contains("Thinking") })
        val sent = server.takeRequest()
        assertEquals("identity", sent.getHeader("Accept-Encoding"))
        assertTrue(sent.body.readUtf8().contains("\"stream\":true"))
    }

    @Test
    fun reasoningThenContentKeepsOnlyTheAnswer() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"reasoning_content":"plan the sum"}}]}""",
                """data: {"choices":[{"delta":{"content":"Hi"}}]}""",
                "data: [DONE]",
            ),
        )
        val reply = StringBuilder()
        client().stream(settings(), "system", listOf(TurnMessage("user", "hi", null))) {
            reply.append(it)
        }
        assertEquals("Hi", reply.toString())
    }

    @Test
    fun internetSearchRoundFeedsTheResultIntoTheNextStream() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"internet_search","arguments":""}}]}}]}""",
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"query\":\"Kotlin release\"}"}}]}}]}""",
                "data: [DONE]",
            ),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Kotlin is current."}}]}""",
                "data: [DONE]",
            ),
        )
        val queries = ArrayList<String>()
        val reply = StringBuilder()
        val statuses = ArrayList<String>()
        client(search = { query, _ ->
            queries += query
            "1. Kotlin — https://kotlinlang.org\n   Current language."
        }).stream(
            settings(),
            "system",
            listOf(TurnMessage("user", "What is new in Kotlin?", null)),
            tools = listOf(searchTool()),
            onStatus = { statuses += it },
        ) { reply.append(it) }
        assertEquals("Kotlin is current.", reply.toString())
        assertEquals(listOf("Kotlin release"), queries)
        assertTrue(statuses.any { it.contains("Searching") })
        server.takeRequest()
        val followUp = server.takeRequest().body.readUtf8()
        assertTrue(followUp.contains("\"role\":\"tool\""))
        assertTrue(followUp.contains("https://kotlinlang.org"))
        assertTrue(followUp.contains("\"content\":\"\""))
        assertFalse(followUp.contains("\"content\":null"))
    }

    @Test
    fun openingAPageTellsTheUserWhichSite() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"internet_search","arguments":"{\"query\":\"Maybank news\"}"}}]}}]}""",
                "data: [DONE]",
            ),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Maybank outlined ROAR30."}}]}""",
                "data: [DONE]",
            ),
        )
        val statuses = ArrayList<String>()
        val reply = StringBuilder()
        client(search = { _, onVisit ->
            onVisit("Checking out reuters.com…")
            onVisit("Checking out theedgemalaysia.com…")
            "Private notes. ROAR30."
        }).stream(
            settings(),
            "system",
            listOf(TurnMessage("user", "latest Maybank news", null)),
            tools = listOf(searchTool()),
            onStatus = { statuses += it },
        ) { reply.append(it) }
        assertEquals("Maybank outlined ROAR30.", reply.toString())
        assertTrue(statuses.any { it == "Checking out reuters.com…" })
        assertTrue(statuses.any { it == "Checking out theedgemalaysia.com…" })
    }

    @Test
    fun anAssignedTaskResearchesBeforeTheBriefing() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Maybank looks fine."}}]}""",
                "data: [DONE]",
            ),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"## Price\n\nMaybank is 10.20, with a P/E of 12 and a market cap of RM 120 billion."}}]}""",
                "data: [DONE]",
            ),
        )
        val queries = ArrayList<String>()
        val reply = StringBuilder()
        client(search = { query, _ ->
            queries += query
            "Private notes. Price 10.20. P/E 12. Market cap RM 120 billion."
        }).stream(
            settings(),
            "system",
            listOf(TurnMessage("user", "Task: Maybank briefing\n\nPull the current figures.", "task-1")),
            tools = listOf(searchTool()),
            deepWork = true,
            onStatus = { status ->
                if (status.startsWith("Answering")) {
                    reply.clear()
                }
            },
        ) { reply.append(it) }
        assertTrue(reply.toString().contains("## Price"))
        assertTrue(reply.toString().contains("market cap"))
        assertEquals(1, queries.size)
        assertTrue(queries.single().contains("Maybank"))
        assertFalse(queries.single().contains("looks fine"))
    }

    @Test
    fun aChatReplyStaysASingleAnswer() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Maybank looks fine."}}]}""",
                "data: [DONE]",
            ),
        )
        val queries = ArrayList<String>()
        val reply = StringBuilder()
        client(search = { query, _ ->
            queries += query
            "notes"
        }).stream(
            settings(),
            "system",
            listOf(TurnMessage("user", "Is Maybank worth buying now?", null)),
            tools = listOf(searchTool()),
        ) { reply.append(it) }
        assertEquals("Maybank looks fine.", reply.toString())
        assertTrue(queries.isEmpty())
    }

    @Test
    fun aPromiseToSearchContinuesIntoTheAnswer() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Running the correct search now."}}]}""",
                "data: [DONE]",
            ),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Maybank is paying for the Ageas insurance business, and the 2024 profit was higher."}}]}""",
                "data: [DONE]",
            ),
        )
        val queries = ArrayList<String>()
        val reply = StringBuilder()
        val offer = "Want the deal price for the Ageas buy, and the 2024-2026 financial numbers?"
        client(search = { query, _ ->
            queries += query
            "Private notes. Ageas deal price and 2024 profit."
        }).stream(
            settings(),
            "system",
            listOf(
                TurnMessage("user", "why they wanna buy", null),
                TurnMessage("assistant", offer, null),
                TurnMessage("user", "find more info", null),
            ),
            tools = listOf(searchTool()),
            onStatus = { status ->
                if (status.startsWith("Answering")) {
                    reply.clear()
                }
            },
        ) { reply.append(it) }
        assertEquals(
            "Maybank is paying for the Ageas insurance business, and the 2024 profit was higher.",
            reply.toString(),
        )
        assertEquals(1, queries.size)
        assertTrue(queries.single().contains("deal price"))
        assertFalse(queries.single().contains("find more info"))
    }

    @Test
    fun aFollowUpIsNotUsedAsTheSearchQuery() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"internet_search","arguments":"{\"query\":\"find more info\"}"}}]}}]}""",
                "data: [DONE]",
            ),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"The Ageas deal adds insurance income."}}]}""",
                "data: [DONE]",
            ),
        )
        val queries = ArrayList<String>()
        val reply = StringBuilder()
        client(search = { query, _ ->
            queries += query
            "Private notes. Ageas insurance income."
        }).stream(
            settings(),
            "system",
            listOf(
                TurnMessage("user", "why they wanna buy", null),
                TurnMessage(
                    "assistant",
                    "Want the deal price for the Ageas buy, and the 2024-2026 financial numbers?",
                    null,
                ),
                TurnMessage("user", "run", null),
            ),
            tools = listOf(searchTool()),
        ) { reply.append(it) }
        assertEquals("The Ageas deal adds insurance income.", reply.toString())
        assertTrue(queries.single().contains("deal price"))
        assertFalse(queries.single().contains("find more info"))
    }

    @Test
    fun quietEmptyStreamRetriesOnce() = runBlocking {
        server.enqueue(sse("data: [DONE]"))
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Recovered"}}]}""",
                "data: [DONE]",
            ),
        )
        val reply = StringBuilder()
        client().stream(settings(), "system", listOf(TurnMessage("user", "hi", null))) {
            reply.append(it)
        }
        assertEquals("Recovered", reply.toString())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun providerThatRejectsToolsStillAnswers() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":{"message":"tools are not supported by this model"}}"""),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Plain answer"}}]}""",
                "data: [DONE]",
            ),
        )
        val reply = StringBuilder()
        client().stream(
            settings(),
            "system",
            listOf(TurnMessage("user", "hi", null)),
            tools = listOf(searchTool()),
        ) { reply.append(it) }
        assertEquals("Plain answer", reply.toString())
        assertTrue(server.takeRequest().body.readUtf8().contains("\"tools\""))
        assertFalse(server.takeRequest().body.readUtf8().contains("\"tools\""))
    }

    @Test
    fun concurrentRepliesStreamInParallel() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"First briefing."}}]}""",
                "data: [DONE]",
            ).setBodyDelay(600, TimeUnit.MILLISECONDS),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Second briefing."}}]}""",
                "data: [DONE]",
            ).setBodyDelay(600, TimeUnit.MILLISECONDS),
        )
        val chat = client()
        val first = async {
            chat.stream(settings(), "system", listOf(TurnMessage("user", "task one", null))) { }
        }
        delay(80)
        assertEquals(1, server.requestCount)
        val second = async {
            chat.stream(settings(), "system", listOf(TurnMessage("user", "task two", null))) { }
        }
        delay(200)
        // Both calls should be in flight — no global turn lock.
        assertEquals(2, server.requestCount)
        first.await()
        second.await()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aDroppedCallAfterPartialTextIsStillOneRetry() {
        val chat = client()
        assertTrue(
            chat.shouldRetryStream(
                "I'll pull the figures.",
                ChatException("HTTP 429: slow down"),
                toolsOffered = true,
            ),
        )
        assertFalse(
            chat.shouldRetryStream(
                "I'll pull the figures.",
                ChatException("HTTP 401: bad key"),
                toolsOffered = true,
            ),
        )
        assertFalse(
            chat.shouldRetryStream(
                "I'll pull the figures.",
                ChatException("The model returned no text. Try again."),
                toolsOffered = true,
            ),
        )
    }

    @Test
    fun cancelDoesNotStartAnotherRequest() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeadersDelay(5, TimeUnit.SECONDS)
                .setBody("data: [DONE]\n"),
        )
        var finished = false
        val job = launch {
            client().stream(settings(), "system", listOf(TurnMessage("user", "hi", null))) { }
            finished = true
        }
        delay(200)
        job.cancel()
        job.join()
        assertFalse(finished)
        assertTrue(server.requestCount <= 1)
    }

    @Test
    fun placeholderReplyIsRetriedInsteadOfKept() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"(No reply)"}}]}""",
                "data: [DONE]",
            ),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Maybank launched ROAR30."}}]}""",
                "data: [DONE]",
            ),
        )
        val statuses = ArrayList<String>()
        val reply = StringBuilder()
        client().stream(
            settings(),
            "system",
            listOf(TurnMessage("user", "find news on maybank", null)),
            onStatus = { status ->
                statuses += status
                if (status.startsWith("Trying again")) {
                    reply.clear()
                }
            },
        ) { reply.append(it) }
        assertEquals("Maybank launched ROAR30.", reply.toString())
        assertEquals(2, server.requestCount)
        assertTrue(statuses.any { it.startsWith("Trying again") })
        assertFalse(reply.contains("No reply"))
    }

    @Test
    fun scratchpadPlanRunsTheSearchAndFinishesTheAnswer() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"reasoning_content":"We need answer user. I can search \"Maybank latest news\". Let's do search."}}]}""",
                "data: [DONE]",
            ),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Maybank launched ROAR30."}}]}""",
                "data: [DONE]",
            ),
        )
        val queries = ArrayList<String>()
        val reply = StringBuilder()
        client(search = { query, _ ->
            queries += query
            "1. Maybank — https://example.com\n   ROAR30."
        }).stream(
            settings(),
            "system",
            listOf(TurnMessage("user", "find news on maybank", null)),
            tools = listOf(searchTool()),
        ) { reply.append(it) }
        assertEquals("Maybank launched ROAR30.", reply.toString())
        assertEquals(listOf("Maybank latest news"), queries)
        assertEquals(2, server.requestCount)
        server.takeRequest()
        val followUp = server.takeRequest().body.readUtf8()
        assertTrue(followUp.contains("Maybank latest news"))
        assertTrue(followUp.contains("ROAR30"))
        assertFalse(reply.toString().contains("We need"))
    }

    @Test
    fun emptyToolCallUsesTheQueryWrittenInThePlan() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"internet_search","arguments":"{}"}}]}}]}""",
                """data: {"choices":[{"delta":{"reasoning_content":"The user is asking me to find news on Maybank. Let me call internet_search with query \"Maybank latest news\". I'll then report."}}]}""",
                "data: [DONE]",
            ),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Maybank launched ROAR30."}}]}""",
                "data: [DONE]",
            ),
        )
        val queries = ArrayList<String>()
        val reply = StringBuilder()
        client(search = { query, _ ->
            queries += query
            "1. Maybank — https://example.com\n   ROAR30."
        }).stream(
            settings(),
            "system",
            listOf(TurnMessage("user", "hey, find news on maybank, latest", null)),
            tools = listOf(searchTool()),
        ) { reply.append(it) }
        assertEquals(listOf("Maybank latest news"), queries)
        assertEquals("Maybank launched ROAR30.", reply.toString())
        server.takeRequest()
        val followUp = server.takeRequest().body.readUtf8()
        assertTrue(followUp.contains("Maybank latest news"))
        assertFalse(followUp.contains("Missing search query"))
    }

    @Test
    fun emptyToolCallUsesTheUsersQuestion() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"internet_search","arguments":"{}"}}]}}]}""",
                """data: {"choices":[{"delta":{"content":"Use proper tool call format. I should search now and then write the news."}}]}""",
                "data: [DONE]",
            ),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Maybank shares moved today."}}]}""",
                "data: [DONE]",
            ),
        )
        val queries = ArrayList<String>()
        val reply = StringBuilder()
        client(search = { query, _ ->
            queries += query
            "1. Maybank — https://example.com\n   Shares."
        }).stream(
            settings(),
            "system",
            listOf(
                TurnMessage("user", "hey, find news on maybank, latest", null),
                TurnMessage("assistant", "We need answer user. Let's do search.", null),
                TurnMessage("user", "do it then. stop stopping", null),
                TurnMessage("user", "?", null),
            ),
            tools = listOf(searchTool()),
            onStatus = { status ->
                if (status.startsWith("Answering")) {
                    reply.clear()
                }
            },
        ) { reply.append(it) }
        assertEquals(listOf("hey, find news on maybank, latest"), queries)
        assertEquals("Maybank shares moved today.", reply.toString())
        assertFalse(reply.contains("tool call format"))
        server.takeRequest()
        assertFalse(server.takeRequest().body.readUtf8().contains("Missing search query"))
    }

    @Test
    fun searchPlanDoesNotBecomeAResultList() = runBlocking {
        val plan = """data: {"choices":[{"delta":{"content":"The user is asking me to find news on Maybank. Let me call internet_search with query \"Maybank latest news\". I'll do it in the message."}}]}"""
        repeat(4) { server.enqueue(sse(plan, "data: [DONE]")) }
        val queries = ArrayList<String>()
        val reply = StringBuilder()
        try {
            client(search = { query, _ ->
                queries += query
                "1. Maybank — https://example.com/news\n   ROAR30 strategy."
            }).stream(
                settings(),
                "system",
                listOf(TurnMessage("user", "find news on maybank, latest", null)),
                tools = listOf(searchTool()),
                onStatus = { status ->
                    if (status.startsWith("Answering")) {
                        reply.clear()
                    }
                },
            ) { reply.append(it) }
            org.junit.Assert.fail("expected an error")
        } catch (error: ChatException) {
            assertTrue(error.message.orEmpty().contains("no text"))
        }
        assertEquals(listOf("Maybank latest news"), queries)
        assertFalse(reply.toString().contains("https://example.com"))
        assertFalse(reply.toString().contains("Here's what I found"))
    }

    @Test
    fun searchHitListIsReplacedByTheWrittenAnswer() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"internet_search","arguments":"{\"query\":\"Maybank news\"}"}}]}}]}""",
                "data: [DONE]",
            ),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Here's what I found:\n\n1. Maybank — https://example.com/news\n2. Other — https://example.com/other"}}]}""",
                "data: [DONE]",
            ),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Maybank outlined the ROAR30 plan today."}}]}""",
                "data: [DONE]",
            ),
        )
        val reply = StringBuilder()
        client(search = { _, _ -> "Private notes. ROAR30 plan." }).stream(
            settings(),
            "system",
            listOf(TurnMessage("user", "what's the latest on Maybank?", null)),
            tools = listOf(searchTool()),
            onStatus = { status ->
                if (status.startsWith("Answering")) {
                    reply.clear()
                }
            },
        ) { reply.append(it) }
        assertEquals("Maybank outlined the ROAR30 plan today.", reply.toString())
        server.takeRequest()
        server.takeRequest()
        val followUp = server.takeRequest().body.readUtf8()
        assertTrue(followUp.contains("own words"))
        assertFalse(reply.contains("example.com"))
    }

    @Test
    fun emptyBodyIsAnErrorNotAFakeReply() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        try {
            client().stream(settings(), "system", listOf(TurnMessage("user", "hi", null))) { }
            org.junit.Assert.fail("expected an error")
        } catch (error: ChatException) {
            assertTrue(error.message.orEmpty().contains("no text"))
            assertFalse(error.message.orEmpty().contains("no reply"))
        }
    }

    @Test
    fun timeToolRoundFeedsTheClockIntoTheNextStream() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_t","function":{"name":"time_tool","arguments":"{\"place\":\"Tokyo\",\"time\":\"\",\"from_place\":\"\"}"}}]}}]}""",
                "data: [DONE]",
            ),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"It is 7:00 PM in Tokyo."}}]}""",
                "data: [DONE]",
            ),
        )
        val seen = ArrayList<String>()
        val statuses = ArrayList<String>()
        val reply = StringBuilder()
        client(time = { args ->
            seen += args
            "Current time in Tokyo\nThursday, 15 January 2026, 7:00 PM (Asia/Tokyo, UTC+9)"
        }).stream(
            settings(),
            "system",
            listOf(TurnMessage("user", "what time is it in Tokyo?", null)),
            tools = listOf(searchTool(), timeTool()),
            onStatus = { statuses += it },
        ) { reply.append(it) }
        assertEquals(listOf("""{"place":"Tokyo","time":"","from_place":""}"""), seen)
        assertEquals("It is 7:00 PM in Tokyo.", reply.toString())
        assertTrue(statuses.any { it.contains("Checking the time") })
        server.takeRequest()
        val followUp = server.takeRequest().body.readUtf8()
        assertTrue(followUp.contains("Asia/Tokyo"))
        assertFalse(followUp.contains("Unknown tool"))
    }

    @Test
    fun timePlanRunsTheClockAndFinishesTheAnswer() = runBlocking {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"reasoning_content":"The user is asking what time is it in Tokyo. Let me call time_tool."}}]}""",
                "data: [DONE]",
            ),
        )
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"It is 7:00 PM in Tokyo."}}]}""",
                "data: [DONE]",
            ),
        )
        val seen = ArrayList<String>()
        val reply = StringBuilder()
        client(time = { args ->
            seen += args
            "Current time in Tokyo\n7:00 PM"
        }).stream(
            settings(),
            "system",
            listOf(TurnMessage("user", "what time is it in Tokyo?", null)),
            tools = listOf(searchTool(), timeTool()),
        ) { reply.append(it) }
        assertEquals("It is 7:00 PM in Tokyo.", reply.toString())
        assertEquals(1, seen.size)
        assertTrue(seen.single().contains("Tokyo"))
        assertFalse(reply.contains("Let me call"))
    }

    @Test
    fun banterIsOneShortCompletionWithoutTools() = runBlocking {
        val skipped = client().banter(settings().copy(apiKey = ""), "You are Pixel.", "Say hi.")
        assertEquals(null, skipped)
        assertEquals(0, server.requestCount)

        server.enqueue(
            MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"Ship it today!"}}]}"""),
        )
        val line = client().banter(
            settings(),
            "You are Pixel. One short office sentence, under 8 words. No quotes.",
            "Kai said: Deadline is today. Reply in one short sentence.",
        )
        assertEquals("Ship it today!", line)
        val recorded = server.takeRequest(1, TimeUnit.SECONDS)
        assertTrue(recorded != null)
        val body = recorded!!.body.readUtf8()
        assertTrue(body.contains("\"stream\":false"))
        assertFalse(body.contains("\"tools\""))
        assertTrue(body.contains("under 8 words"))
        assertTrue(body.contains("Deadline is today."))
        assertTrue(recorded.path.orEmpty().endsWith("/chat/completions"))

        server.enqueue(
            MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"(No reply)"}}]}"""),
        )
        assertEquals(null, client().banter(settings(), "You are Pixel.", "Say hi."))

        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        assertEquals(null, client().banter(settings(), "You are Pixel.", "Say hi."))
    }

    private fun client(
        search: AgentSearch = AgentSearch { _, _ -> "no results" },
        time: (String) -> String = { "Thursday, 15 January 2026, 6:00 PM" },
    ): ChatClient {
        return ChatClient(search = search, time = time, retryDelayMs = 0)
    }

    private fun settings(): LlmSettings {
        val base = server.url("/v1").toString().trimEnd('/')
        return LlmSettings(providerId = "openai", baseUrl = base, apiKey = "test-key", model = "test-model")
    }

    private fun timeTool(): AgentTool {
        return AgentTool(
            id = "time_tool",
            title = "Time",
            description = "Read the clock",
            kind = AgentToolKind.Common,
        )
    }

    private fun searchTool(): AgentTool {
        return AgentTool(
            id = "internet_search",
            title = "Internet search",
            description = "Search the web",
            kind = AgentToolKind.Common,
        )
    }

    private fun sse(vararg lines: String): MockResponse {
        return MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(lines.joinToString("\n") + "\n")
    }
}
