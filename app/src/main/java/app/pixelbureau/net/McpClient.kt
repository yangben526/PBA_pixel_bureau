package app.pixelbureau.net

import app.pixelbureau.logic.AgentTool
import app.pixelbureau.logic.AgentToolKind
import app.pixelbureau.logic.McpRoute
import app.pixelbureau.logic.McpRouteTable
import app.pixelbureau.logic.McpServerConfig
import app.pixelbureau.logic.McpToolDef
import app.pixelbureau.logic.RpcOutcome
import app.pixelbureau.logic.authPair
import app.pixelbureau.logic.jsonRpcNotification
import app.pixelbureau.logic.jsonRpcRequest
import app.pixelbureau.logic.jsonString
import app.pixelbureau.logic.mcpInitializeParams
import app.pixelbureau.logic.mcpToolName
import app.pixelbureau.logic.normalizeToolArguments
import app.pixelbureau.logic.parseJsonRpcResponse
import app.pixelbureau.logic.parseToolsList
import app.pixelbureau.logic.toolCallResultText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

class McpException(message: String) : Exception(message)

/**
 * MCP client over Streamable HTTP (JSON-RPC 2.0 POST; JSON or SSE replies;
 * Mcp-Session-Id). One session and one cached tools/list per server config.
 * Every call is capped at [timeoutMs]. No stdio, no legacy HTTP+SSE transport.
 */
class McpClient(
    http: OkHttpClient = OkHttpClient(),
    private val timeoutMs: Long = 30_000L,
    private val toolsTtlMs: Long = 10 * 60_000L,
) {
    private val http: OkHttpClient = http.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private class Session(val id: String?)
    private class CachedTools(val key: String, val tools: List<McpToolDef>, val at: Long)

    private val nextId = AtomicLong(1)
    private val sessions = ConcurrentHashMap<String, Session>()
    private val toolsCache = ConcurrentHashMap<String, CachedTools>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val servers = ConcurrentHashMap<String, McpServerConfig>()
    val routes = McpRouteTable()

    private fun cacheKey(s: McpServerConfig) = "${s.url}|${s.authHeader}|${s.token}"

    /** Forget a server's session and tools (after edit/remove). */
    fun invalidate(serverId: String) {
        sessions.remove(serverId)
        toolsCache.remove(serverId)
    }

    /** Fresh discovery for the Settings "Test" button. */
    suspend fun test(server: McpServerConfig): List<McpToolDef> {
        invalidate(server.id)
        return listTools(server)
    }

    suspend fun listTools(server: McpServerConfig): List<McpToolDef> = withContext(Dispatchers.IO) {
        servers[server.id] = server
        val key = cacheKey(server)
        toolsCache[server.id]?.let { c ->
            if (c.key == key && System.currentTimeMillis() - c.at < toolsTtlMs) return@withContext c.tools
        }
        val result = rpc(server, "tools/list", "{}")
        val tools = parseToolsList(result)
        toolsCache[server.id] = CachedTools(key, tools, System.currentTimeMillis())
        tools
    }

    /**
     * Tools from [servers] as namespaced [AgentTool]s. Servers are discovered in
     * parallel; one that fails or times out is skipped.
     */
    suspend fun agentTools(servers: List<McpServerConfig>): List<AgentTool> {
        if (servers.isEmpty()) return emptyList()
        val perServer = coroutineScope {
            servers.map { s ->
                async {
                    val tools = try {
                        withTimeoutOrNull(timeoutMs) { listTools(s) }.orEmpty()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyList()
                    }
                    s to tools
                }
            }.awaitAll()
        }
        val out = ArrayList<AgentTool>()
        val used = HashSet<String>()
        for ((server, tools) in perServer) {
            for (tool in tools) {
                var name = mcpToolName(server.name.ifBlank { server.id }, tool.name)
                var n = 2
                while (!used.add(name)) {
                    name = name.take(60) + "_" + n++
                }
                routes.put(name, McpRoute(server.id, tool.name))
                val desc = tool.description.ifBlank { "Tool ${tool.name}" }.take(600)
                out += AgentTool(
                    id = name,
                    title = tool.name,
                    description = "[MCP ${server.name.ifBlank { "server" }}] $desc",
                    kind = AgentToolKind.Common,
                    parametersJson = tool.inputSchema,
                )
            }
        }
        return out
    }

    fun isMcpTool(name: String): Boolean = routes.isMcp(name)

    /** Runs a namespaced tool. Never throws (except cancellation); errors come back as text. */
    suspend fun callNamespaced(name: String, argumentsJson: String): String {
        val route = routes.resolve(name) ?: return "Tool error: unknown MCP tool $name"
        val server = servers[route.serverId] ?: return "Tool error: MCP server for $name is not configured"
        return try {
            withContext(Dispatchers.IO) {
                val params = "{\"name\":${jsonString(route.toolName)},\"arguments\":${normalizeToolArguments(argumentsJson)}}"
                val result = withTimeoutOrNull(timeoutMs) { rpc(server, "tools/call", params) }
                    ?: throw McpException("timed out after ${timeoutMs / 1000}s")
                toolCallResultText(result)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            "Tool error (${server.name.ifBlank { "MCP" }}): ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private suspend fun ensureSession(server: McpServerConfig): Session {
        sessions[server.id]?.let { return it }
        val lock = locks.getOrPut(server.id) { Mutex() }
        return lock.withLock {
            sessions[server.id]?.let { return@withLock it }
            val id = nextId.getAndIncrement()
            val (outcome, sessionId) = post(server, null, jsonRpcRequest(id, "initialize", mcpInitializeParams()), id)
            when (outcome) {
                is RpcOutcome.Error -> throw McpException("initialize failed: ${outcome.message}")
                is RpcOutcome.Result -> Unit
                null -> throw McpException("initialize: no response")
            }
            val session = Session(sessionId)
            runCatching { post(server, session.id, jsonRpcNotification("notifications/initialized"), null) }
            sessions[server.id] = session
            session
        }
    }

    private suspend fun rpc(server: McpServerConfig, method: String, params: String): app.pixelbureau.logic.Json {
        repeat(2) { attempt ->
            val session = ensureSession(server)
            val id = nextId.getAndIncrement()
            try {
                val (outcome, _) = post(server, session.id, jsonRpcRequest(id, method, params), id)
                return when (outcome) {
                    is RpcOutcome.Result -> outcome.result
                    is RpcOutcome.Error -> throw McpException(outcome.message)
                    null -> throw McpException("$method: no response")
                }
            } catch (expired: SessionExpired) {
                sessions.remove(server.id)
                if (attempt == 1) throw McpException("session expired")
            }
        }
        throw McpException("$method failed")
    }

    private class SessionExpired : Exception()

    /** POST one message. Returns the matching response (null for notifications) and any session id. */
    private suspend fun post(
        server: McpServerConfig,
        sessionId: String?,
        body: String,
        id: Long?,
    ): Pair<RpcOutcome?, String?> {
        val builder = Request.Builder()
            .url(server.url.trim())
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", app.pixelbureau.logic.McpProtocolVersion)
            .post(body.toRequestBody(JSON))
        server.authPair()?.let { (k, v) -> builder.header(k, v) }
        if (sessionId != null) builder.header("Mcp-Session-Id", sessionId)
        val call = http.newCall(builder.build())
        coroutineContext.job.invokeOnCompletion { call.cancel() }
        call.execute().use { response ->
            val newSession = response.header("Mcp-Session-Id") ?: sessionId
            if (response.code == 404 && sessionId != null) throw SessionExpired()
            if (id == null) return null to newSession
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty().take(200)
                throw McpException(
                    when (response.code) {
                        401, 403 -> "HTTP ${response.code}: check the auth token"
                        else -> "HTTP ${response.code}${if (detail.isNotBlank()) ": $detail" else ""}"
                    },
                )
            }
            val contentType = response.header("Content-Type")
            val text = readUntilResponse(response, id, contentType)
            return parseJsonRpcResponse(text, id, contentType) to newSession
        }
    }

    /** For SSE, stop reading once our response event has arrived (streams may stay open). */
    private fun readUntilResponse(response: okhttp3.Response, id: Long, contentType: String?): String {
        val body = response.body ?: return ""
        if (contentType?.contains("text/event-stream", ignoreCase = true) != true) return body.string()
        val source = body.source()
        val buf = StringBuilder()
        while (true) {
            val line = source.readUtf8Line() ?: break
            buf.append(line).append('\n')
            if (line.isEmpty() && parseJsonRpcResponse(buf.toString(), id, contentType) != null) break
        }
        return buf.toString()
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
