package app.pixelbureau

import app.pixelbureau.logic.Json
import app.pixelbureau.logic.McpServerConfig
import app.pixelbureau.logic.RpcOutcome
import app.pixelbureau.logic.authPair
import app.pixelbureau.logic.decodeAgentMcp
import app.pixelbureau.logic.decodeMcpServers
import app.pixelbureau.logic.encodeAgentMcp
import app.pixelbureau.logic.encodeMcpServers
import app.pixelbureau.logic.mcpToolName
import app.pixelbureau.logic.normalizeToolArguments
import app.pixelbureau.logic.parseJson
import app.pixelbureau.logic.parseJsonRpcResponse
import app.pixelbureau.logic.parseToolsList
import app.pixelbureau.logic.serversForAgent
import app.pixelbureau.logic.toolCallResultText
import app.pixelbureau.net.McpClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpTest {
    @Test
    fun parsesPlainJsonResult() {
        val out = parseJsonRpcResponse("""{"jsonrpc":"2.0","id":3,"result":{"tools":[]}}""", 3)
        assertTrue(out is RpcOutcome.Result)
    }

    @Test
    fun parsesSseAndSkipsOtherIds() {
        val body = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n" +
            "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"ok\":true}}\n\n"
        val out = parseJsonRpcResponse(body, 7, "text/event-stream")
        assertTrue(out is RpcOutcome.Result)
        assertNull(parseJsonRpcResponse(body, 8, "text/event-stream"))
    }

    @Test
    fun parsesRpcError() {
        val out = parseJsonRpcResponse("""{"jsonrpc":"2.0","id":"2","error":{"code":-32601,"message":"nope"}}""", 2)
        assertEquals(RpcOutcome.Error("nope"), out)
    }

    @Test
    fun parsesToolsListAndText() {
        val tools = parseToolsList(parseJson("""{"tools":[{"name":"get_weather","description":"Weather","inputSchema":{"properties":{"city":{"type":"string"}}}},{"description":"no name"}]}"""))
        assertEquals(1, tools.size)
        assertTrue(tools[0].inputSchema.contains("\"type\":\"object\""))
        val text = toolCallResultText(parseJson("""{"content":[{"type":"text","text":"Sunny \u00e9"}],"isError":false}"""))
        assertEquals("Sunny é", text)
        val err = toolCallResultText(parseJson("""{"content":[{"type":"text","text":"bad"}],"isError":true}"""))
        assertEquals("Tool error: bad", err)
    }

    @Test
    fun namesAreSafeAndShort() {
        assertEquals("my_github__search_repos", mcpToolName("My GitHub!", "search_repos"))
        assertTrue(mcpToolName("x".repeat(80), "y".repeat(80)).length <= 64)
        assertEquals("{}", normalizeToolArguments("not json"))
    }

    @Test
    fun configAndAgentAttachRoundTrip() {
        val s = listOf(McpServerConfig("a", "A", "https://a/mcp", "", "tok", true), McpServerConfig("b", "B \"q\"", "https://b", "X-Key", "k", false))
        assertEquals(s, decodeMcpServers(encodeMcpServers(s)))
        assertEquals("Authorization" to "Bearer tok", s[0].authPair())
        assertEquals("X-Key" to "k", s[1].authPair())
        assertNull(decodeAgentMcp(null))
        assertEquals(emptySet<String>(), decodeAgentMcp(encodeAgentMcp(emptySet())))
        assertEquals(listOf("a"), serversForAgent(s, null).map { it.id })
        assertEquals(emptyList<String>(), serversForAgent(s, setOf("b")).map { it.id })
    }

    @Test
    fun clientDiscoversAndRoutesOverHttp() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setHeader("Mcp-Session-Id", "s1").setHeader("Content-Type", "application/json")
            .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26","capabilities":{}}}"""))
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody("data: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"echo\",\"inputSchema\":{\"type\":\"object\"}}]}}\n\n"))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setBody("""{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"hi"}]}}"""))
        val client = McpClient(timeoutMs = 5_000)
        val cfg = McpServerConfig("id1", "Echo", server.url("/mcp").toString(), token = "t")
        val tools = client.agentTools(listOf(cfg))
        assertEquals("echo__echo", tools.single().id)
        assertEquals("hi", client.callNamespaced("echo__echo", "{\"x\":1}"))
        server.takeRequest()
        server.takeRequest()
        val list = server.takeRequest()
        assertEquals("s1", list.getHeader("Mcp-Session-Id"))
        assertEquals("Bearer t", list.getHeader("Authorization"))
        assertTrue(client.callNamespaced("nope__x", "{}").startsWith("Tool error"))
        server.shutdown()
    }

    @Test
    fun clientReturnsErrorStringWhenDown() = runBlocking {
        val client = McpClient(timeoutMs = 2_000)
        val tools = client.agentTools(listOf(McpServerConfig("d", "Down", "http://127.0.0.1:1/mcp")))
        assertTrue(tools.isEmpty())
        Unit
    }
}
