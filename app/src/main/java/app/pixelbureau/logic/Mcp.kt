package app.pixelbureau.logic

/**
 * MCP (Model Context Protocol) helpers that stay free of Android and OkHttp so
 * they can be unit tested: a tiny JSON reader, server config encoding, JSON-RPC
 * response parsing (plain JSON or SSE), tools/list parsing, tool-name routing.
 */

// ── Minimal JSON ────────────────────────────────────────────────────────────

sealed interface Json {
    data class Obj(val fields: Map<String, Json>) : Json
    data class Arr(val items: List<Json>) : Json
    data class Str(val value: String) : Json
    data class Num(val raw: String) : Json
    data class Bool(val value: Boolean) : Json
    data object Null : Json
}

operator fun Json?.get(key: String): Json? = (this as? Json.Obj)?.fields?.get(key)
val Json?.str: String? get() = (this as? Json.Str)?.value
val Json?.arr: List<Json> get() = (this as? Json.Arr)?.items.orEmpty()

class JsonParseException(message: String) : RuntimeException(message)

fun parseJson(text: String): Json {
    val p = JsonReader(text)
    val value = p.value()
    p.skipWs()
    if (p.i != text.length) throw JsonParseException("trailing data at ${p.i}")
    return value
}

fun parseJsonOrNull(text: String): Json? = try {
    parseJson(text)
} catch (_: RuntimeException) {
    null
}

private class JsonReader(val s: String) {
    var i = 0
    fun skipWs() {
        while (i < s.length && s[i].isWhitespace()) i++
    }
    fun value(): Json {
        skipWs()
        if (i >= s.length) throw JsonParseException("eof")
        return when (val c = s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> Json.Str(string())
            't' -> lit("true", Json.Bool(true))
            'f' -> lit("false", Json.Bool(false))
            'n' -> lit("null", Json.Null)
            else -> if (c == '-' || c.isDigit()) num() else throw JsonParseException("bad char $c at $i")
        }
    }
    fun lit(word: String, v: Json): Json {
        if (!s.startsWith(word, i)) throw JsonParseException("bad literal at $i")
        i += word.length
        return v
    }
    fun num(): Json {
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
        return Json.Num(s.substring(start, i))
    }
    fun string(): String {
        i++ // opening quote
        val b = StringBuilder()
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> {
                    i++
                    return b.toString()
                }
                c == '\\' -> {
                    if (i + 1 >= s.length) throw JsonParseException("escape")
                    when (val e = s[i + 1]) {
                        'n' -> b.append('\n')
                        'r' -> b.append('\r')
                        't' -> b.append('\t')
                        'b' -> b.append('\b')
                        'f' -> b.append('\u000C')
                        'u' -> {
                            if (i + 6 > s.length) throw JsonParseException("unicode")
                            b.append(s.substring(i + 2, i + 6).toInt(16).toChar())
                            i += 4
                        }
                        else -> b.append(e)
                    }
                    i += 2
                }
                else -> {
                    b.append(c)
                    i++
                }
            }
        }
        throw JsonParseException("unterminated string")
    }
    fun obj(): Json {
        i++
        val out = LinkedHashMap<String, Json>()
        skipWs()
        if (i < s.length && s[i] == '}') {
            i++
            return Json.Obj(out)
        }
        while (true) {
            skipWs()
            if (i >= s.length || s[i] != '"') throw JsonParseException("key at $i")
            val k = string()
            skipWs()
            if (i >= s.length || s[i] != ':') throw JsonParseException("colon at $i")
            i++
            out[k] = value()
            skipWs()
            if (i >= s.length) throw JsonParseException("eof in object")
            if (s[i] == ',') {
                i++
                continue
            }
            if (s[i] == '}') {
                i++
                return Json.Obj(out)
            }
            throw JsonParseException("object at $i")
        }
    }
    fun arr(): Json {
        i++
        val out = ArrayList<Json>()
        skipWs()
        if (i < s.length && s[i] == ']') {
            i++
            return Json.Arr(out)
        }
        while (true) {
            out += value()
            skipWs()
            if (i >= s.length) throw JsonParseException("eof in array")
            if (s[i] == ',') {
                i++
                continue
            }
            if (s[i] == ']') {
                i++
                return Json.Arr(out)
            }
            throw JsonParseException("array at $i")
        }
    }
}

fun writeJson(value: Json): String = buildString { writeJsonTo(this, value) }

private fun writeJsonTo(out: StringBuilder, value: Json) {
    when (value) {
        is Json.Obj -> {
            out.append('{')
            value.fields.entries.forEachIndexed { n, (k, v) ->
                if (n > 0) out.append(',')
                out.append(jsonString(k)).append(':')
                writeJsonTo(out, v)
            }
            out.append('}')
        }
        is Json.Arr -> {
            out.append('[')
            value.items.forEachIndexed { n, v ->
                if (n > 0) out.append(',')
                writeJsonTo(out, v)
            }
            out.append(']')
        }
        is Json.Str -> out.append(jsonString(value.value))
        is Json.Num -> out.append(value.raw)
        is Json.Bool -> out.append(value.value)
        Json.Null -> out.append("null")
    }
}

// ── Server config ──────────────────────────────────────────────────────────

data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    /** Header name for the secret; blank means "Authorization: Bearer <token>". */
    val authHeader: String = "",
    /** Bearer token or raw header value. Blank = no auth. */
    val token: String = "",
    val enabled: Boolean = true,
)

/** "Authorization" + "Bearer x" by default, or a custom header with the raw value. */
fun McpServerConfig.authPair(): Pair<String, String>? {
    val secret = token.trim()
    if (secret.isEmpty()) return null
    val header = authHeader.trim()
    return if (header.isEmpty() || header.equals("Authorization", ignoreCase = true)) {
        val value = if (secret.contains(' ')) secret else "Bearer $secret"
        "Authorization" to value
    } else {
        header to secret
    }
}

fun encodeMcpServers(servers: List<McpServerConfig>): String = writeJson(
    Json.Arr(
        servers.map { s ->
            Json.Obj(
                linkedMapOf(
                    "id" to Json.Str(s.id),
                    "name" to Json.Str(s.name),
                    "url" to Json.Str(s.url),
                    "authHeader" to Json.Str(s.authHeader),
                    "token" to Json.Str(s.token),
                    "enabled" to Json.Bool(s.enabled),
                ),
            )
        },
    ),
)

fun decodeMcpServers(raw: String?): List<McpServerConfig> {
    if (raw.isNullOrBlank()) return emptyList()
    val root = parseJsonOrNull(raw) ?: return emptyList()
    return root.arr.mapNotNull { item ->
        val id = item["id"].str?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        McpServerConfig(
            id = id,
            name = item["name"].str.orEmpty(),
            url = item["url"].str.orEmpty(),
            authHeader = item["authHeader"].str.orEmpty(),
            token = item["token"].str.orEmpty(),
            enabled = (item["enabled"] as? Json.Bool)?.value ?: true,
        )
    }
}

// ── Per-agent attachment (mirrors per-agent BYOK binding) ───────────────────

/**
 * Stored value for an agent's MCP choice. Missing = Default (every enabled
 * server). "|a,b" = only servers a and b. "|" = none.
 */
fun encodeAgentMcp(serverIds: Set<String>): String = "|" + serverIds.sorted().joinToString(",")

/** null = Default (all enabled servers). */
fun decodeAgentMcp(raw: String?): Set<String>? {
    if (raw == null || !raw.startsWith("|")) return null
    return raw.removePrefix("|").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

/** Enabled servers this agent may use. [attached] null means all enabled servers. */
fun serversForAgent(all: List<McpServerConfig>, attached: Set<String>?): List<McpServerConfig> =
    all.filter { it.enabled && it.url.isNotBlank() && (attached == null || it.id in attached) }

// ── Tool naming / routing ───────────────────────────────────────────────────

const val McpNameSep = "__"
private const val MaxToolName = 64

/** OpenAI tool names allow [a-zA-Z0-9_-], max 64. */
fun mcpSlug(text: String): String {
    val cleaned = text.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_')
        .replace(Regex("_{2,}"), "_")
    return cleaned.ifEmpty { "mcp" }
}

fun mcpToolName(serverName: String, toolName: String): String {
    val server = mcpSlug(serverName).take(24)
    val tool = toolName.replace(Regex("[^A-Za-z0-9_-]+"), "_")
    return (server + McpNameSep + tool).take(MaxToolName)
}

/** Namespaced tool → (server id, real MCP tool name). */
data class McpRoute(val serverId: String, val toolName: String)

class McpRouteTable {
    private val routes = java.util.concurrent.ConcurrentHashMap<String, McpRoute>()
    fun put(name: String, route: McpRoute) {
        routes[name] = route
    }
    fun resolve(name: String): McpRoute? = routes[name]
    fun isMcp(name: String): Boolean = routes.containsKey(name)
}

// ── JSON-RPC ───────────────────────────────────────────────────────────────

fun jsonRpcRequest(id: Long, method: String, paramsJson: String = "{}"): String =
    "{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":${jsonString(method)},\"params\":$paramsJson}"

fun jsonRpcNotification(method: String): String =
    "{\"jsonrpc\":\"2.0\",\"method\":${jsonString(method)}}"

const val McpProtocolVersion = "2025-03-26"

fun mcpInitializeParams(): String =
    "{\"protocolVersion\":\"$McpProtocolVersion\",\"capabilities\":{}," +
        "\"clientInfo\":{\"name\":\"pixel-bureau\",\"version\":\"1.0\"}}"

sealed interface RpcOutcome {
    data class Result(val result: Json) : RpcOutcome
    data class Error(val message: String) : RpcOutcome
}

/**
 * Finds the JSON-RPC response with [id] in a plain JSON body (object or batch)
 * or an SSE stream (`data:` lines, events split by blank lines).
 */
fun parseJsonRpcResponse(body: String, id: Long, contentType: String? = null): RpcOutcome? {
    val trimmed = body.trim()
    val isSse = contentType?.contains("text/event-stream", ignoreCase = true) == true ||
        (!trimmed.startsWith("{") && !trimmed.startsWith("[") &&
            (trimmed.startsWith("data:") || trimmed.startsWith("event:") || trimmed.startsWith("id:") || trimmed.startsWith(":")))
    val candidates: List<Json> = if (isSse) {
        sseDataPayloads(body).mapNotNull { parseJsonOrNull(it) }
    } else {
        listOfNotNull(parseJsonOrNull(trimmed))
    }
    for (candidate in candidates) {
        val messages = if (candidate is Json.Arr) candidate.items else listOf(candidate)
        for (msg in messages) {
            val msgId = msg["id"]
            val matches = when (msgId) {
                is Json.Num -> msgId.raw.toDoubleOrNull()?.toLong() == id
                is Json.Str -> msgId.value == id.toString()
                else -> false
            }
            if (!matches) continue
            val error = msg["error"]
            if (error != null && error != Json.Null) {
                val message = error["message"].str ?: writeJson(error)
                return RpcOutcome.Error(message)
            }
            return RpcOutcome.Result(msg["result"] ?: Json.Null)
        }
    }
    return null
}

/** Joins multi-line `data:` fields per SSE event. */
fun sseDataPayloads(body: String): List<String> {
    val out = ArrayList<String>()
    val current = StringBuilder()
    var has = false
    for (rawLine in body.lineSequence()) {
        val line = rawLine.trimEnd('\r')
        if (line.isEmpty()) {
            if (has) out += current.toString()
            current.clear()
            has = false
            continue
        }
        if (line.startsWith("data:")) {
            if (has) current.append('\n')
            current.append(line.removePrefix("data:").removePrefix(" "))
            has = true
        }
    }
    if (has) out += current.toString()
    return out
}

data class McpToolDef(
    val name: String,
    val description: String,
    /** JSON Schema object for the arguments, as raw JSON. */
    val inputSchema: String,
)

fun parseToolsList(result: Json): List<McpToolDef> = result["tools"].arr.mapNotNull { t ->
    val name = t["name"].str?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    val schema = t["inputSchema"] as? Json.Obj
    val fixed = if (schema == null) {
        "{\"type\":\"object\",\"properties\":{}}"
    } else {
        val fields = LinkedHashMap(schema.fields)
        if (fields["type"] == null) fields["type"] = Json.Str("object")
        if (fields["properties"] == null) fields["properties"] = Json.Obj(emptyMap())
        writeJson(Json.Obj(fields))
    }
    McpToolDef(name, t["description"].str.orEmpty().trim(), fixed)
}

/** Text the model sees from tools/call. Non-text parts become short notes. */
fun toolCallResultText(result: Json, maxChars: Int = 12_000): String {
    val parts = result["content"].arr.mapNotNull { c ->
        when (c["type"].str) {
            "text" -> c["text"].str
            "image" -> "[image ${c["mimeType"].str.orEmpty()} omitted]"
            "audio" -> "[audio omitted]"
            "resource" -> c["resource"]["text"].str ?: "[resource ${c["resource"]["uri"].str.orEmpty()}]"
            "resource_link" -> "[resource ${c["uri"].str.orEmpty()}]"
            else -> null
        }
    }
    var text = parts.joinToString("\n").trim()
    if (text.isEmpty()) {
        text = result["structuredContent"]?.let { writeJson(it) } ?: "(empty result)"
    }
    val isError = (result["isError"] as? Json.Bool)?.value == true
    if (isError) text = "Tool error: $text"
    return if (text.length > maxChars) text.take(maxChars) + "\n…(truncated)" else text
}

/** Model arguments → JSON object for tools/call; garbage becomes {}. */
fun normalizeToolArguments(argumentsJson: String): String {
    val parsed = parseJsonOrNull(argumentsJson.trim().ifEmpty { "{}" })
    return if (parsed is Json.Obj) writeJson(parsed) else "{}"
}
