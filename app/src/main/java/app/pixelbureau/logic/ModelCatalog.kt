package app.pixelbureau.logic

data class SavedLlm(
    val id: String,
    val providerId: String,
    val apiKey: String,
    val model: String,
    val label: String = "",
    val baseUrl: String = "",
)

data class LlmCatalog(
    val models: List<SavedLlm> = emptyList(),
    val activeId: String? = null,
) {
    fun active(): SavedLlm? = models.firstOrNull { it.id == activeId }
}

sealed interface CatalogWrite {
    data class Saved(val catalog: LlmCatalog, val message: String) : CatalogWrite
    data class Rejected(val message: String) : CatalogWrite
}

fun SavedLlm.displayTitle(): String = label.trim().ifEmpty { model }

fun SavedLlm.resolvedBaseUrl(): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
        return trimmed
    }
    return llmProvider(providerId).baseUrl.trimEnd('/')
}

fun SavedLlm.summary(): String {
    val provider = llmProvider(providerId)
    val url = resolvedBaseUrl()
    val where = if (url == provider.baseUrl.trimEnd('/')) provider.label else urlHost(url)
    return "$where · $model"
}

fun SavedLlm.displayDetail(): String {
    val where = summary().substringBefore(" · ")
    return if (label.isBlank()) where else "$where · $model"
}

/** Blank becomes the provider address. A host with no scheme is treated as https. */
fun resolveApiUrl(raw: String, fallback: String): String? {
    val trimmed = raw.trim()
    val withScheme = when {
        trimmed.isEmpty() -> fallback.trim()
        trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
        else -> "https://$trimmed"
    }
    if (withScheme.any { it.isWhitespace() }) {
        return null
    }
    val scheme = when {
        withScheme.startsWith("https://") -> "https://"
        withScheme.startsWith("http://") -> "http://"
        else -> return null
    }
    val rest = withScheme.removePrefix(scheme).trimEnd('/')
    if (rest.isEmpty() || rest.startsWith("/")) {
        return null
    }
    return scheme + rest
}

private fun urlHost(url: String): String {
    return url.removePrefix("https://").removePrefix("http://").substringBefore('/').ifEmpty { url }
}

/** A previous single provider/key/model becomes the first entry when a key was saved. */
fun catalogFromLegacy(providerId: String?, apiKey: String?, model: String?): LlmCatalog {
    if (apiKey.isNullOrBlank()) {
        return LlmCatalog()
    }
    val provider = llmProvider(providerId ?: "xai")
    val saved = SavedLlm(
        id = "legacy",
        providerId = provider.id,
        apiKey = apiKey.trim(),
        model = model?.trim().orEmpty().ifEmpty { provider.defaultModel },
        label = "",
        baseUrl = provider.baseUrl,
    )
    return LlmCatalog(listOf(saved), saved.id)
}

fun saveIntoCatalog(
    catalog: LlmCatalog,
    editingId: String?,
    providerId: String,
    apiKey: String,
    model: String,
    label: String,
    newId: String,
    baseUrl: String = "",
): CatalogWrite {
    val provider = llmProvider(providerId)
    val chosenModel = model.trim().ifEmpty { provider.defaultModel }
    val key = apiKey.trim()
    if (key.isEmpty()) {
        return CatalogWrite.Rejected("Paste an API key for this model.")
    }
    val url = resolveApiUrl(baseUrl, provider.baseUrl)
        ?: return CatalogWrite.Rejected("Enter an API URL, like https://api.x.ai/v1.")
    val duplicate = catalog.models.any {
        it.id != editingId && it.resolvedBaseUrl() == url && it.model == chosenModel
    }
    if (duplicate) {
        return CatalogWrite.Rejected("That model is already in the list.")
    }
    val id = editingId ?: newId
    val saved = SavedLlm(
        id = id,
        providerId = provider.id,
        apiKey = key,
        model = chosenModel,
        label = label.trim(),
        baseUrl = url,
    )
    val exists = editingId != null && catalog.models.any { it.id == editingId }
    val models = if (exists) {
        catalog.models.map { if (it.id == editingId) saved else it }
    } else {
        catalog.models + saved
    }
    val wasEmpty = catalog.models.isEmpty()
    val activeId = when {
        wasEmpty || catalog.activeId == null || catalog.models.none { it.id == catalog.activeId } -> saved.id
        else -> catalog.activeId
    }
    val detail = saved.summary()
    val message = when {
        exists -> "Updated $detail."
        wasEmpty -> "Added $detail. Chats use it."
        else -> "Added $detail."
    }
    return CatalogWrite.Saved(LlmCatalog(models, activeId), message)
}

fun selectInCatalog(catalog: LlmCatalog, id: String): CatalogWrite {
    val chosen = catalog.models.firstOrNull { it.id == id }
        ?: return CatalogWrite.Rejected("That model is already gone.")
    return CatalogWrite.Saved(
        catalog.copy(activeId = chosen.id),
        "Chats now use ${chosen.displayTitle()}.",
    )
}

fun removeFromCatalog(catalog: LlmCatalog, id: String): CatalogWrite {
    val doomed = catalog.models.firstOrNull { it.id == id }
        ?: return CatalogWrite.Rejected("That model is already gone.")
    val models = catalog.models.filter { it.id != id }
    val activeId = if (catalog.activeId == id) models.firstOrNull()?.id else catalog.activeId
    val next = models.firstOrNull { it.id == activeId }
    val message = if (catalog.activeId == id && next != null) {
        "Removed ${doomed.displayTitle()}. Chats now use ${next.displayTitle()}."
    } else {
        "Removed ${doomed.displayTitle()}."
    }
    return CatalogWrite.Saved(LlmCatalog(models, activeId), message)
}

fun encodeCatalog(catalog: LlmCatalog): String = buildString {
    append("{\"activeId\":")
    val active = catalog.activeId
    if (active == null) append("null") else append(jsonString(active))
    append(",\"models\":[")
    catalog.models.forEachIndexed { index, model ->
        if (index > 0) append(',')
        append("{\"id\":").append(jsonString(model.id))
        append(",\"providerId\":").append(jsonString(model.providerId))
        append(",\"apiKey\":").append(jsonString(model.apiKey))
        append(",\"model\":").append(jsonString(model.model))
        append(",\"label\":").append(jsonString(model.label))
        append(",\"baseUrl\":").append(jsonString(model.resolvedBaseUrl()))
        append('}')
    }
    append("]}")
}

fun decodeCatalog(raw: String?): LlmCatalog {
    if (raw.isNullOrBlank()) {
        return LlmCatalog()
    }
    return try {
        decodeCatalogOrThrow(raw)
    } catch (_: RuntimeException) {
        LlmCatalog()
    }
}

private fun decodeCatalogOrThrow(raw: String): LlmCatalog {
    val cursor = Cursor(raw)
    cursor.eat('{')
    var active: String? = null
    val models = mutableListOf<SavedLlm>()
    var expectComma = false
    while (cursor.peek() != '}') {
        if (expectComma) cursor.eat(',')
        when (val key = cursor.readString()) {
            "activeId" -> {
                cursor.eat(':')
                active = if (cursor.peek() == 'n') {
                    cursor.expectLiteral("null")
                    null
                } else {
                    cursor.readString()
                }
            }
            "models" -> {
                cursor.eat(':')
                cursor.eat('[')
                var modelComma = false
                while (cursor.peek() != ']') {
                    if (modelComma) cursor.eat(',')
                    models += readModel(cursor)
                    modelComma = true
                }
                cursor.eat(']')
            }
            else -> {
                cursor.eat(':')
                skipValue(cursor)
            }
        }
        expectComma = true
    }
    cursor.eat('}')
    val activeId = active?.takeIf { id -> models.any { it.id == id } }
    return LlmCatalog(models, activeId)
}

private fun readModel(cursor: Cursor): SavedLlm {
    cursor.eat('{')
    val fields = HashMap<String, String>()
    var expectComma = false
    while (cursor.peek() != '}') {
        if (expectComma) cursor.eat(',')
        val key = cursor.readString()
        cursor.eat(':')
        fields[key] = if (cursor.peek() == 'n') {
            cursor.expectLiteral("null")
            ""
        } else {
            cursor.readString()
        }
        expectComma = true
    }
    cursor.eat('}')
    val provider = llmProvider(fields["providerId"].orEmpty())
    val id = fields["id"].orEmpty().ifBlank { "model" }
    return SavedLlm(
        id = id,
        providerId = provider.id,
        apiKey = fields["apiKey"].orEmpty(),
        model = fields["model"].orEmpty().ifBlank { provider.defaultModel },
        label = fields["label"].orEmpty(),
        baseUrl = fields["baseUrl"].orEmpty().trim().trimEnd('/').ifEmpty { provider.baseUrl },
    )
}

private fun skipValue(cursor: Cursor) {
    when (cursor.peek()) {
        '"' -> cursor.readString()
        '{' -> {
            cursor.eat('{')
            var expectComma = false
            while (cursor.peek() != '}') {
                if (expectComma) cursor.eat(',')
                cursor.readString()
                cursor.eat(':')
                skipValue(cursor)
                expectComma = true
            }
            cursor.eat('}')
        }
        '[' -> {
            cursor.eat('[')
            var expectComma = false
            while (cursor.peek() != ']') {
                if (expectComma) cursor.eat(',')
                skipValue(cursor)
                expectComma = true
            }
            cursor.eat(']')
        }
        'n' -> cursor.expectLiteral("null")
        't' -> cursor.expectLiteral("true")
        'f' -> cursor.expectLiteral("false")
        else -> {
            while (cursor.i < cursor.text.length && cursor.text[cursor.i] !in ",}]") {
                cursor.i++
            }
        }
    }
}

private class Cursor(val text: String) {
    var i: Int = 0

    fun skip() {
        while (i < text.length && text[i].isWhitespace()) i++
    }

    fun peek(): Char? {
        skip()
        return text.getOrNull(i)
    }

    fun eat(expected: Char) {
        val found = peek() ?: throw IllegalArgumentException("expected $expected")
        if (found != expected) throw IllegalArgumentException("expected $expected")
        i++
    }

    fun expectLiteral(literal: String) {
        skip()
        if (!text.startsWith(literal, i)) throw IllegalArgumentException(literal)
        i += literal.length
    }

    fun readString(): String {
        eat('"')
        val out = StringBuilder()
        while (i < text.length) {
            val ch = text[i]
            if (ch == '\\') {
                if (i + 1 >= text.length) throw IllegalArgumentException("escape")
                when (val escaped = text[i + 1]) {
                    '"', '\\', '/' -> out.append(escaped)
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        if (i + 5 >= text.length) throw IllegalArgumentException("hex")
                        val code = text.substring(i + 2, i + 6).toIntOrNull(16)
                            ?: throw IllegalArgumentException("hex")
                        out.append(code.toChar())
                        i += 6
                        continue
                    }
                    else -> out.append(escaped)
                }
                i += 2
                continue
            }
            if (ch == '"') {
                i++
                return out.toString()
            }
            out.append(ch)
            i++
        }
        throw IllegalArgumentException("string")
    }
}
