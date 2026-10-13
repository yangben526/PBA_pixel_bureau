package app.pixelbureau.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.pixelbureau.logic.BubbleSpeechScaleDefault
import app.pixelbureau.logic.Cell
import app.pixelbureau.logic.LlmCatalog
import app.pixelbureau.logic.coerceBubbleSpeechScale
import app.pixelbureau.logic.SavedLlm
import app.pixelbureau.logic.catalogFromLegacy
import app.pixelbureau.logic.decodeCatalog
import app.pixelbureau.logic.encodeCatalog
import app.pixelbureau.logic.jsonString
import app.pixelbureau.logic.McpServerConfig
import app.pixelbureau.logic.decodeMcpServers
import app.pixelbureau.logic.encodeAgentMcp
import app.pixelbureau.logic.encodeMcpServers
import app.pixelbureau.logic.llmProvider
import app.pixelbureau.logic.resolvedBaseUrl

data class LlmSettings(
    val providerId: String = "xai",
    val baseUrl: String = "https://api.x.ai/v1",
    val apiKey: String = "",
    val model: String = "grok-4.7",
)

fun SavedLlm.toSettings(): LlmSettings {
    val provider = llmProvider(providerId)
    return LlmSettings(
        providerId = provider.id,
        baseUrl = resolvedBaseUrl(),
        apiKey = apiKey,
        model = model.ifBlank { provider.defaultModel },
    )
}

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = openPrefs(context)

    fun readCatalog(): LlmCatalog {
        val raw = prefs.getString(KEY_CATALOG, null)
        if (raw != null) {
            return decodeCatalog(raw)
        }
        val migrated = catalogFromLegacy(
            prefs.getString(KEY_PROVIDER, null),
            prefs.getString(KEY_TOKEN, null),
            prefs.getString(KEY_MODEL, null),
        )
        prefs.edit()
            .putString(KEY_CATALOG, encodeCatalog(migrated))
            .remove(KEY_PROVIDER)
            .remove(KEY_TOKEN)
            .remove(KEY_MODEL)
            .apply()
        return migrated
    }

    fun writeCatalog(catalog: LlmCatalog) {
        prefs.edit().putString(KEY_CATALOG, encodeCatalog(catalog)).apply()
    }

    /** The model marked in use, or a blank key when the list is empty. */
    fun read(): LlmSettings {
        val active = readCatalog().active() ?: return LlmSettings(apiKey = "")
        return active.toSettings()
    }

    /**
     * Per-agent BYOK binding: agentId → SavedLlm.id.
     * Missing / unknown ids fall back to the catalog's active (global default) model.
     */
    fun readAgentModels(): Map<String, String> = decodeAgentModels(prefs.getString(KEY_AGENT_MODELS, null))

    fun writeAgentModels(bindings: Map<String, String>) {
        prefs.edit().putString(KEY_AGENT_MODELS, encodeAgentModels(bindings)).apply()
    }

    fun setAgentModel(agentId: String, modelId: String?) {
        val next = readAgentModels().toMutableMap()
        if (modelId.isNullOrBlank()) {
            next.remove(agentId)
        } else {
            next[agentId] = modelId
        }
        writeAgentModels(next)
    }


    /** Common tool: agents may call internet_search when true (default on). */
    fun readInternetSearchEnabled(): Boolean =
        prefs.getBoolean(KEY_INTERNET_SEARCH, true)

    fun writeInternetSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INTERNET_SEARCH, enabled).apply()
    }

    /** Common tool: agents may call time_tool when true (default on). */
    fun readTimeToolEnabled(): Boolean =
        prefs.getBoolean(KEY_TIME_TOOL, true)

    fun writeTimeToolEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TIME_TOOL, enabled).apply()
    }

    /** System settings: agent-to-agent bubble lines may call the assigned model. Default on. */
    fun readBubbleSpeechLlmEnabled(): Boolean =
        prefs.getBoolean(KEY_BUBBLE_SPEECH_LLM, true)

    fun writeBubbleSpeechLlmEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUBBLE_SPEECH_LLM, enabled).apply()
    }

    /** Overhead bubble size on the map. 1 is the original size. */
    fun readBubbleSpeechScale(): Float =
        coerceBubbleSpeechScale(prefs.getFloat(KEY_BUBBLE_SPEECH_SCALE, BubbleSpeechScaleDefault))

    fun writeBubbleSpeechScale(scale: Float) {
        prefs.edit().putFloat(KEY_BUBBLE_SPEECH_SCALE, coerceBubbleSpeechScale(scale)).apply()
    }

    /**
     * Chat panel height as a fraction of the weighted map+chat space (not including
     * header/chips). Clamped on read. Default ~0.42 matches the old weight split.
     */
    fun readChatPanelFraction(): Float =
        prefs.getFloat(KEY_CHAT_FRACTION, DEFAULT_CHAT_FRACTION)
            .coerceIn(MIN_CHAT_FRACTION, MAX_CHAT_FRACTION)

    fun writeChatPanelFraction(fraction: Float) {
        prefs.edit()
            .putFloat(KEY_CHAT_FRACTION, fraction.coerceIn(MIN_CHAT_FRACTION, MAX_CHAT_FRACTION))
            .apply()
    }

    /**
     * Shop purchases: catalog item id → count owned. Bought with HR stars in
     * Pazada / Pamazon / Pmart; map spots live in [readPlacements].
     * Legacy `prop_conf_*` buys (conference built-ins that should never have been
     * sold) are dropped on read so their invisible map blocks clear. No star refund.
     */
    /** Retired shop items (cactus, pouf) dropped from inventory/placements on load. No refund. */
    private val retiredItemIds = setOf("desk_cactus", "cushy_pouf", "prop_cactus", "prop_pouf")

    /**
     * Records that taskforce [id] got its sign-off stars. Returns true only the
     * first time (persisted), so the +3 ★ can never be paid twice.
     */
    /** taskforceId → banner mode (0 expanded, 1 collapsed, 2 hidden), stored as "id=mode". */
    fun taskforceHeaderModes(): Map<String, Int> =
        prefs.getStringSet(KEY_TF_HEADER_COLLAPSED, emptySet()).orEmpty().mapNotNull { e ->
            val id = e.substringBeforeLast('=', e)
            val mode = if ('=' in e) e.substringAfterLast('=').toIntOrNull() ?: 1 else 1
            if (id.isBlank()) null else id to mode.coerceIn(0, 2)
        }.toMap()

    fun writeTaskforceHeaderMode(id: String, mode: Int) {
        val next = taskforceHeaderModes() - id + (if (mode != 0) mapOf(id to mode) else emptyMap())
        prefs.edit().putStringSet(KEY_TF_HEADER_COLLAPSED, next.map { "${it.key}=${it.value}" }.toSet()).apply()
    }

    fun markTaskforceAwarded(id: String): Boolean {
        val set = prefs.getStringSet(KEY_TF_AWARDED, emptySet()).orEmpty()
        if (id in set) return false
        prefs.edit().putStringSet(KEY_TF_AWARDED, set + id).commit()
        return true
    }

    /**
     * First run only: new users start with the Dream Starter (Oakwood) desk set.
     * No-op when any inventory was ever written, so existing data is untouched.
     */
    fun seedStarterInventory() {
        if (prefs.contains(KEY_OWNED_ITEMS)) return
        writeOwnedItems(mapOf(STARTER_DESK_ID to 1))
    }

    fun readOwnedItems(): Map<String, Int> {
        val raw = decodeAgentModels(prefs.getString(KEY_OWNED_ITEMS, null))
            .mapNotNull { (id, n) -> n.toIntOrNull()?.takeIf { it > 0 }?.let { id to it } }
            .toMap()
        val cleaned = raw.filterKeys { !it.startsWith("prop_conf_") && it !in retiredItemIds }
        if (cleaned.size != raw.size) writeOwnedItems(cleaned)
        return cleaned
    }

    private fun writeOwnedItems(owned: Map<String, Int>) {
        prefs.edit()
            .putString(KEY_OWNED_ITEMS, encodeAgentModels(owned.mapValues { it.value.toString() }))
            .apply()
    }

    /**
     * Adds [count] of [itemId]; any id in [replaces] (same exclusive group, e.g.
     * the other desk type) is removed from inventory in the same write. No refund.
     */
    fun addOwnedItem(itemId: String, count: Int = 1, replaces: Collection<String> = emptyList()): Map<String, Int> {
        val next = readOwnedItems().toMutableMap()
        replaces.filter { it != itemId }.forEach { next.remove(it) }
        next[itemId] = (next[itemId] ?: 0) + count
        writeOwnedItems(next)
        return next
    }

    /** Removes one [itemId] and its newest map slot. Returns the new owned map. */
    fun removeOwnedItem(itemId: String): Map<String, Int> {
        val before = readOwnedItems()
        val n = before[itemId] ?: return before
        val next = app.pixelbureau.logic.InventoryRules.afterRemove(before, itemId)
        writeOwnedItems(next)
        writePlacements(app.pixelbureau.logic.InventoryRules.placementsAfterRemove(readPlacements(), itemId, n))
        return next
    }

    /**
     * Map spots of bought items: slot `<itemId>#<n>` (n = copy index) → "x,y".
     * Allocated on buy by BureauViewModel via MapPlacement, so placements are
     * stable across launches.
     */
    fun readPlacements(): Map<String, Cell> {
        val raw = decodeAgentModels(prefs.getString(KEY_SHOP_PLACEMENTS, null))
            .mapNotNull { (slot, cellRaw) -> parseCell(cellRaw)?.let { slot to it } }
            .toMap()
        // Drop slots for purged conference items (`prop_conf_table#0`, …).
        val cleaned = raw.filterKeys { k -> k.substringBefore('#').let { !it.startsWith("prop_conf_") && it !in retiredItemIds } }
        if (cleaned.size != raw.size) writePlacements(cleaned)
        return cleaned
    }

    fun writePlacements(placements: Map<String, Cell>) {
        prefs.edit()
            .putString(
                KEY_SHOP_PLACEMENTS,
                encodeAgentModels(placements.mapValues { "${it.value.x},${it.value.y}" }),
            )
            .apply()
    }

    /** MCP servers (URL + secret) live in the same encrypted prefs as BYOK keys. */
    fun readMcpServers(): List<McpServerConfig> = decodeMcpServers(prefs.getString(KEY_MCP_SERVERS, null))

    fun writeMcpServers(servers: List<McpServerConfig>) {
        prefs.edit().putString(KEY_MCP_SERVERS, encodeMcpServers(servers)).apply()
    }

    /** agentId → encoded choice (see encodeAgentMcp). Missing = Default (all enabled). */
    fun readAgentMcp(): Map<String, String> = decodeAgentModels(prefs.getString(KEY_AGENT_MCP, null))

    fun setAgentMcp(agentId: String, serverIds: Set<String>?) {
        val next = readAgentMcp().toMutableMap()
        if (serverIds == null) next.remove(agentId) else next[agentId] = encodeAgentMcp(serverIds)
        prefs.edit().putString(KEY_AGENT_MCP, encodeAgentModels(next)).apply()
    }

    fun clearBindingsForModel(modelId: String) {
        val next = readAgentModels().filterValues { it != modelId }
        writeAgentModels(next)
    }

    fun clearAgentModel(agentId: String) {
        setAgentModel(agentId, null)
    }

    companion object {
        const val KEY_CATALOG = "models"
        /** "Dream starter - Oakwood simple" shop desk set. */
        const val STARTER_DESK_ID = "office_desk"
        const val KEY_TF_AWARDED = "taskforce_signoff_awarded"
        const val KEY_AGENT_MODELS = "agent_models"
        const val KEY_MCP_SERVERS = "mcp_servers"
        const val KEY_AGENT_MCP = "agent_mcp"
        const val KEY_PROVIDER = "provider"
        const val KEY_TOKEN = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_INTERNET_SEARCH = "internet_search_enabled"
        const val KEY_TIME_TOOL = "time_tool_enabled"
        const val KEY_BUBBLE_SPEECH_LLM = "bubble_speech_llm"
        const val KEY_BUBBLE_SPEECH_SCALE = "bubble_speech_scale"
        const val KEY_CHAT_FRACTION = "chat_panel_fraction"
        const val KEY_TF_HEADER_COLLAPSED = "tf_header_collapsed"
        const val KEY_OWNED_ITEMS = "shop_owned_items"
        const val KEY_SHOP_PLACEMENTS = "shop_placements"
        const val DEFAULT_CHAT_FRACTION = 0.42f
        const val MIN_CHAT_FRACTION = 0f
        /** Smallest open (non-collapsed) chat height; below the snap threshold it collapses to the input bar. */
        const val MIN_OPEN_CHAT_FRACTION = 0.22f
        const val COLLAPSE_SNAP_FRACTION = 0.14f
        const val MAX_CHAT_FRACTION = 1.0f

        fun openPrefs(context: Context): SharedPreferences {
            return try {
                val master = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "pixel_bureau_secrets",
                    master,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Exception) {
                context.getSharedPreferences("pixel_bureau_secrets_plain", Context.MODE_PRIVATE)
            }
        }
    }
}

internal fun parseCell(raw: String): Cell? {
    val parts = raw.split(',')
    if (parts.size != 2) return null
    val x = parts[0].trim().toIntOrNull() ?: return null
    val y = parts[1].trim().toIntOrNull() ?: return null
    return Cell(x, y)
}

/** Compact {"agentId":"modelId",...} for EncryptedSharedPreferences. */
internal fun encodeAgentModels(map: Map<String, String>): String = buildString {
    append('{')
    map.entries.forEachIndexed { index, (key, value) ->
        if (index > 0) append(',')
        append(jsonString(key)).append(':').append(jsonString(value))
    }
    append('}')
}

internal fun decodeAgentModels(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return try {
        decodeAgentModelsOrThrow(raw)
    } catch (_: RuntimeException) {
        emptyMap()
    }
}

private fun decodeAgentModelsOrThrow(raw: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    var i = 0
    fun skip() {
        while (i < raw.length && raw[i].isWhitespace()) i++
    }
    fun peek(): Char? {
        skip()
        return raw.getOrNull(i)
    }
    fun eat(expected: Char) {
        val found = peek() ?: throw IllegalArgumentException("expected $expected")
        if (found != expected) throw IllegalArgumentException("expected $expected")
        i++
    }
    fun readString(): String {
        eat('"')
        val buf = StringBuilder()
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\') {
                if (i + 1 >= raw.length) throw IllegalArgumentException("escape")
                when (val escaped = raw[i + 1]) {
                    '"', '\\', '/' -> buf.append(escaped)
                    'n' -> buf.append('\n')
                    'r' -> buf.append('\r')
                    't' -> buf.append('\t')
                    else -> buf.append(escaped)
                }
                i += 2
                continue
            }
            if (ch == '"') {
                i++
                return buf.toString()
            }
            buf.append(ch)
            i++
        }
        throw IllegalArgumentException("string")
    }
    eat('{')
    var expectComma = false
    while (peek() != '}') {
        if (expectComma) eat(',')
        val key = readString()
        eat(':')
        val value = readString()
        if (key.isNotBlank() && value.isNotBlank()) {
            out[key] = value
        }
        expectComma = true
    }
    eat('}')
    return out
}
