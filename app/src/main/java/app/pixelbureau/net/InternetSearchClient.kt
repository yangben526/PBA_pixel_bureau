package app.pixelbureau.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Key-free web search for agents. No API key.
 *
 * 1. DuckDuckGo lite HTML (GET, then POST) for ordinary web hits.
 * 2. DuckDuckGo Instant Answer JSON when it has an abstract.
 * 3. Wikipedia search.
 * 4. Open a few of those http(s) pages and read the article text.
 *
 * A DuckDuckGo bot wall is skipped so Wikipedia can still answer.
 * Page text stays in the notes. The chat shows which site is being read.
 */
class InternetSearchClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val allowPrivateHosts: Boolean = false,
) {
    private val pageHttp: OkHttpClient = http.newBuilder()
        .callTimeout(12, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class Hit(
        val title: String,
        val url: String,
        val snippet: String,
        val page: String = "",
    )

    suspend fun search(
        query: String,
        limit: Int = 6,
        onVisit: suspend (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) {
            return@withContext "Search query was empty."
        }
        val hits = LinkedHashMap<String, Hit>()
        var failure: String? = null
        try {
            for (hit in duckDuckGoHtml(q)) {
                rememberHit(hits, hit)
            }
        } catch (_: Exception) {
            // Fall through to the other sources.
        }
        try {
            for (hit in duckDuckGoInstant(q)) {
                rememberHit(hits, hit)
            }
        } catch (_: Exception) {
            // Fall through to Wikipedia.
        }
        try {
            for (hit in wikipediaSearch(q, limit)) {
                rememberHit(hits, hit)
            }
        } catch (error: Exception) {
            if (hits.isEmpty()) {
                failure = error.message ?: "network error"
            }
        }
        val opened = readChosenPages(hits, q, onVisit)
        if (hits.isEmpty()) {
            return@withContext "Search failed: ${failure ?: "network error"}"
        }
        return@withContext formatSearchNotes(orderedHits(hits.values.toList(), opened, limit))
    }

    private suspend fun readChosenPages(
        hits: LinkedHashMap<String, Hit>,
        query: String,
        onVisit: suspend (String) -> Unit,
    ): List<String> {
        val targets = pagesToOpen(hits.values.toList(), query, allowPrivateHosts = allowPrivateHosts)
        for (url in targets) {
            onVisit(checkingOutStatus(url))
            val existing = hits.entries.firstOrNull { it.value.url == url }
            if (!existing?.value?.page.isNullOrBlank()) {
                continue
            }
            val text = try {
                readHitPage(url, existing?.value?.title.orEmpty())
            } catch (_: Exception) {
                ""
            }
            val page = text.trim().ifBlank { "Could not read this page." }
            val clipped = if (page == "Could not read this page.") page else clipPageText(page)
            if (existing != null) {
                hits[existing.key] = existing.value.copy(page = clipped)
            } else {
                hits[url] = Hit(siteLabel(url), url, "", clipped)
            }
        }
        return targets
    }

    private fun readHitPage(url: String, titleHint: String): String {
        if ("wikipedia.org/wiki/" in url) {
            val title = titleHint.ifBlank { wikipediaTitle(url) }
            if (title.isNotBlank()) {
                val extract = try {
                    wikipediaExtract(title)
                } catch (_: Exception) {
                    ""
                }
                if (extract.isNotBlank()) {
                    return extract
                }
            }
        }
        return readPage(url)
    }

    /** Article text from one http(s) page. Empty when the page is blocked or not text. */
    internal fun readPage(url: String): String {
        val safe = openablePageUrl(url, allowPrivateHosts) ?: return ""
        if (isSearchEnginePage(safe)) {
            return ""
        }
        val request = Request.Builder()
            .url(safe)
            .header("User-Agent", BROWSER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.8")
            .header("Accept-Language", "en")
            .get()
            .build()
        return try {
            pageHttp.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (openablePageUrl(finalUrl, allowPrivateHosts) == null || isSearchEnginePage(finalUrl)) {
                    return ""
                }
                if (!response.isSuccessful) {
                    return ""
                }
                val mime = response.body?.contentType()?.toString().orEmpty().lowercase()
                if (mime.isNotBlank() &&
                    !mime.contains("html") &&
                    !mime.contains("xml") &&
                    !mime.contains("text/")
                ) {
                    return ""
                }
                readablePageText(response.peekBody(MaxPageBytes).string())
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * DuckDuckGo's HTML index. Instant Answer JSON often has no hits for news
     * and prices; this is the page a browser would search.
     */
    private fun duckDuckGoHtml(query: String): List<Hit> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val fromGet = fetchDuckDuckGo("https://lite.duckduckgo.com/lite/?q=$encoded", postBody = null)
        if (fromGet.isNotEmpty()) {
            return fromGet
        }
        return fetchDuckDuckGo("https://lite.duckduckgo.com/lite/", postBody = "q=$encoded")
    }

    private fun fetchDuckDuckGo(url: String, postBody: String?): List<Hit> {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_AGENT)
            .header("Accept", "text/html")
        if (postBody == null) {
            builder.get()
        } else {
            builder.post(postBody.toRequestBody(FORM))
        }
        http.newCall(builder.build()).execute().use { response ->
            val html = response.body?.string().orEmpty()
            if (!response.isSuccessful || html.isBlank() || isDuckDuckGoChallenge(html)) {
                return emptyList()
            }
            return parseDuckDuckGoHtml(html).mapNotNull { hit ->
                if (!hit.url.contains("duckduckgo.com")) {
                    hit
                } else if (hit.title.isBlank() && hit.snippet.isBlank()) {
                    null
                } else {
                    hit.copy(url = "")
                }
            }
        }
    }

    private fun duckDuckGoInstant(query: String): List<Hit> {
        val url = "https://api.duckduckgo.com/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("no_html", "1")
            .addQueryParameter("skip_disambig", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return emptyList()
            }
            return parseDuckDuckGoInstant(body)
        }
    }

    private fun wikipediaSearch(query: String, limit: Int): List<Hit> {
        val url = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("list", "search")
            .addQueryParameter("srsearch", query)
            .addQueryParameter("srlimit", limit.toString())
            .addQueryParameter("format", "json")
            .addQueryParameter("utf8", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Wikipedia HTTP ${response.code}")
            }
            return parseWikipediaSearch(body)
        }
    }

    /** Lead paragraph, so the model gets page text and not only a title. */
    private fun wikipediaExtract(title: String): String {
        val url = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("prop", "extracts")
            .addQueryParameter("exintro", "1")
            .addQueryParameter("explaintext", "1")
            .addQueryParameter("exchars", PageTextChars.toString())
            .addQueryParameter("titles", title)
            .addQueryParameter("format", "json")
            .addQueryParameter("utf8", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return ""
            }
            return extractJsonString(body, "extract").orEmpty().trim()
        }
    }

    companion object {
        private const val USER_AGENT = "PixelBureau/0.1 (Android; agent internet_search)"
        private const val BROWSER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private val FORM = "application/x-www-form-urlencoded".toMediaType()
        private const val MaxPageBytes = 600_000L
    }
}

/** Notes for the model. The user should see the written answer, not this list or the page text. */
internal fun formatSearchNotes(hits: List<InternetSearchClient.Hit>): String {
    if (hits.isEmpty()) {
        return "No web results. Answer from what you already know, and say if you are unsure."
    }
    return buildString {
        appendLine("Private notes for you. Use them to answer the user in your own words. Do not paste this list, the page text, or the query.")
        hits.forEachIndexed { index, hit ->
            append(index + 1)
            append(". ")
            append(hit.title.ifBlank { "Result" })
            if (hit.url.isNotBlank()) {
                append(" — ")
                append(hit.url)
            }
            appendLine()
            if (hit.snippet.isNotBlank()) {
                append("   ")
                appendLine(hit.snippet)
            }
            if (hit.page.isNotBlank()) {
                append("   Read from the page: ")
                appendLine(hit.page)
            }
        }
    }.trim()
}

/** How many result sites one search opens. */
internal const val PagesToRead = 3

/** Article text kept from each opened page. */
internal const val PageTextChars = 1400

/**
 * Pages to open after a search. Links written in the query come first.
 * Otherwise the top results, one page per site, skipping search engines.
 */
internal fun pagesToOpen(
    hits: List<InternetSearchClient.Hit>,
    query: String,
    limit: Int = PagesToRead,
    allowPrivateHosts: Boolean = false,
): List<String> {
    val chosen = ArrayList<String>()
    val seenHosts = HashSet<String>()
    fun consider(raw: String) {
        if (chosen.size >= limit) {
            return
        }
        val url = openablePageUrl(raw, allowPrivateHosts) ?: return
        if (isSearchEnginePage(url)) {
            return
        }
        val host = siteHost(url) ?: return
        if (!seenHosts.add(host)) {
            return
        }
        chosen += url
    }
    for (url in urlsInText(query)) {
        consider(url)
    }
    for (hit in hits) {
        consider(hit.url)
    }
    return chosen
}

internal fun orderedHits(
    hits: List<InternetSearchClient.Hit>,
    opened: List<String>,
    limit: Int,
): List<InternetSearchClient.Hit> {
    val openedSet = opened.toSet()
    val front = opened.mapNotNull { url -> hits.firstOrNull { it.url == url } }
    val rest = hits.filter { it.url !in openedSet }
    return (front + rest).take(maxOf(limit, front.size))
}

/** http(s) page the phone may open. Loopback and private addresses stay closed. */
internal fun openablePageUrl(raw: String, allowPrivateHosts: Boolean = false): String? {
    val trimmed = raw.trim().trimEnd('.', ',', ';', ')', ']', '>')
    if (trimmed.isEmpty()) {
        return null
    }
    val uri = try {
        java.net.URI(trimmed)
    } catch (_: Exception) {
        return null
    }
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") {
        return null
    }
    if (!uri.userInfo.isNullOrBlank()) {
        return null
    }
    val host = uri.host?.lowercase()?.trim('.') ?: return null
    if (host.isEmpty() || (!allowPrivateHosts && isPrivateWebHost(host))) {
        return null
    }
    if (!allowPrivateHosts && uri.port != -1 && uri.port != 80 && uri.port != 443) {
        return null
    }
    return trimmed
}

internal fun isPrivateWebHost(host: String): Boolean {
    val name = host.lowercase().trim('.')
    if (name == "localhost" ||
        name.endsWith(".localhost") ||
        name.endsWith(".local") ||
        name.endsWith(".internal")
    ) {
        return true
    }
    val ip = name.removePrefix("[").removeSuffix("]")
    if (':' in ip) {
        return ip == "::1" || ip.startsWith("fe80:") || ip.startsWith("fc") || ip.startsWith("fd")
    }
    val parts = ip.split('.')
    if (parts.size != 4) {
        return false
    }
    val nums = parts.map { it.toIntOrNull() ?: return false }
    if (nums.any { it !in 0..255 }) {
        return false
    }
    val a = nums[0]
    val b = nums[1]
    return a == 0 || a == 10 || a == 127 ||
        (a == 169 && b == 254) ||
        (a == 172 && b in 16..31) ||
        (a == 192 && b == 168) ||
        (a == 100 && b in 64..127)
}

internal fun isSearchEnginePage(url: String): Boolean {
    val host = siteHost(url) ?: return false
    return host == "duckduckgo.com" ||
        host.endsWith(".duckduckgo.com") ||
        host == "google.com" ||
        host.endsWith(".google.com") ||
        host == "bing.com" ||
        host.endsWith(".bing.com")
}

internal fun siteHost(url: String): String? {
    val uri = try {
        java.net.URI(url.trim())
    } catch (_: Exception) {
        return null
    }
    return uri.host?.lowercase()?.removePrefix("www.")?.trim('.')?.takeIf { it.isNotEmpty() }
}

internal fun siteLabel(url: String): String {
    val host = siteHost(url) ?: return "the site"
    return if (host.length <= 48) host else host.take(45).trimEnd('.') + "…"
}

/** Status stored while a page is opening. The loader drops the ellipsis. */
internal fun checkingOutStatus(url: String): String = "Checking out ${siteLabel(url)}…"

internal fun urlsInText(text: String): List<String> {
    if (text.isBlank()) {
        return emptyList()
    }
    return Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .map { it.value.trimEnd('.', ',', ';', ')', ']', '>') }
        .distinct()
        .take(6)
        .toList()
}

internal fun wikipediaTitle(url: String): String {
    val marker = "/wiki/"
    val at = url.indexOf(marker)
    if (at < 0) {
        return ""
    }
    val slug = url.substring(at + marker.length).substringBefore('#').substringBefore('?')
    if (slug.isBlank()) {
        return ""
    }
    return try {
        URLDecoder.decode(slug, StandardCharsets.UTF_8.name()).replace('_', ' ').trim()
    } catch (_: Exception) {
        ""
    }
}

internal fun readablePageText(html: String, maxChars: Int = PageTextChars): String {
    if (html.isBlank()) {
        return ""
    }
    val article = tagInner(html, "article")
    val main = tagInner(html, "main")
    val focused = when {
        article != null && stripHtml(article).length >= 80 -> article
        main != null && stripHtml(main).length >= 80 -> main
        else -> html
    }
    val stripped = dropBlocks(
        focused,
        listOf("script", "style", "noscript", "svg", "iframe", "nav", "footer", "form"),
    )
    return clipPageText(stripHtml(stripped), maxChars)
}

internal fun clipPageText(text: String, maxChars: Int = PageTextChars): String {
    val clean = text.trim()
    if (clean.length <= maxChars) {
        return clean
    }
    val cut = clean.take(maxChars)
    val space = cut.lastIndexOf(' ')
    val clipped = if (space > maxChars / 2) cut.take(space) else cut
    return clipped.trimEnd() + "…"
}

private fun tagInner(html: String, tag: String): String? {
    val open = html.indexOf("<$tag", ignoreCase = true)
    if (open < 0) {
        return null
    }
    val start = html.indexOf('>', open)
    if (start < 0) {
        return null
    }
    val close = html.indexOf("</$tag>", start, ignoreCase = true)
    if (close <= start) {
        return null
    }
    return html.substring(start + 1, close)
}

private fun dropBlocks(html: String, tags: List<String>): String {
    var text = html
    for (tag in tags) {
        val openToken = "<$tag"
        val closeToken = "</$tag>"
        var guard = 0
        while (guard++ < 40) {
            val start = text.indexOf(openToken, ignoreCase = true)
            if (start < 0) {
                break
            }
            val end = text.indexOf(closeToken, start + openToken.length, ignoreCase = true)
            if (end < 0) {
                text = text.removeRange(start, (start + openToken.length).coerceAtMost(text.length))
                break
            }
            text = text.removeRange(start, end + closeToken.length)
        }
    }
    return text
}

private fun rememberHit(hits: LinkedHashMap<String, InternetSearchClient.Hit>, hit: InternetSearchClient.Hit) {
    if (!isUsefulSearchHit(hit)) {
        return
    }
    val key = hit.url.ifBlank { hit.title }
    if (key.isNotBlank()) {
        hits.putIfAbsent(key, hit)
    }
}

/**
 * DuckDuckGo's instant API sometimes answers with an internal fathead record
 * (`perl_module`, "Just Another Test") instead of a page. That JSON is not a hit.
 */
internal fun isUsefulSearchHit(hit: InternetSearchClient.Hit): Boolean {
    val blob = "${hit.title}\n${hit.snippet}"
    if (blob.contains("perl_module") || blob.contains("DDG::")) {
        return false
    }
    val trimmed = blob.trim()
    if ((trimmed.startsWith("{") || trimmed.startsWith("[")) &&
        trimmed.contains("\"type\"") &&
        trimmed.contains("\"properties\"")
    ) {
        return false
    }
    return hit.title.isNotBlank() || hit.snippet.isNotBlank() || hit.url.startsWith("http")
}

internal fun isDuckDuckGoChallenge(html: String): Boolean {
    if (html.contains("result-link") || html.contains("result__a")) {
        return false
    }
    val lower = html.lowercase()
    return "anomaly" in lower || "challenge-form" in lower || "unfortunately, bots" in lower
}

internal fun parseDuckDuckGoHtml(html: String): List<InternetSearchClient.Hit> {
    val out = ArrayList<InternetSearchClient.Hit>()
    var cursor = 0
    while (cursor < html.length && out.size < 8) {
        val classAt = listOf(
            html.indexOf("result-link", cursor),
            html.indexOf("result__a", cursor),
        ).filter { it >= 0 }.minOrNull() ?: break
        val tagStart = html.lastIndexOf("<a", classAt).takeIf { it >= cursor - 20 && it >= 0 } ?: classAt
        val tagEnd = html.indexOf('>', classAt)
        if (tagEnd < 0) {
            break
        }
        val href = htmlAttribute(html.substring(tagStart, tagEnd), "href")
        val close = html.indexOf("</a>", tagEnd)
        val title = if (close > tagEnd) stripHtml(html.substring(tagEnd + 1, close)) else ""
        val url = href?.let { unwrapDuckDuckGoUrl(it) }.orEmpty()
        val snippetAt = listOf(
            html.indexOf("result-snippet", tagEnd),
            html.indexOf("result__snippet", tagEnd),
        ).filter { it in tagEnd until tagEnd + 1800 }.minOrNull()
        val snippet = if (snippetAt != null) {
            val open = html.indexOf('>', snippetAt)
            val end = html.indexOf("</", open + 1)
            if (open > 0 && end > open) stripHtml(html.substring(open + 1, end)) else ""
        } else {
            ""
        }
        if (title.isNotBlank() || url.startsWith("http")) {
            out += InternetSearchClient.Hit(title.ifBlank { url }, url, snippet)
        }
        cursor = (if (close > tagEnd) close else tagEnd) + 1
    }
    return out
}

internal fun htmlAttribute(tag: String, name: String): String? {
    val key = "$name="
    val at = tag.indexOf(key, ignoreCase = true)
    if (at < 0) {
        return null
    }
    var index = at + key.length
    if (index >= tag.length) {
        return null
    }
    val quote = tag[index]
    if (quote == '"' || quote == '\'') {
        val end = tag.indexOf(quote, index + 1)
        if (end < 0) {
            return null
        }
        return tag.substring(index + 1, end)
    }
    val end = tag.indexOfAny(charArrayOf(' ', '>'), index).let { if (it < 0) tag.length else it }
    return tag.substring(index, end)
}

internal fun unwrapDuckDuckGoUrl(href: String): String {
    val decodedAmp = href.replace("&amp;", "&")
    val uddg = Regex("[?&]uddg=([^&]+)").find(decodedAmp)?.groupValues?.getOrNull(1)
    if (!uddg.isNullOrBlank()) {
        return URLDecoder.decode(uddg, StandardCharsets.UTF_8.name())
    }
    return decodedAmp
}

internal fun parseDuckDuckGoInstant(json: String): List<InternetSearchClient.Hit> {
    val out = ArrayList<InternetSearchClient.Hit>()
    val abstractText = extractJsonString(json, "AbstractText").orEmpty()
    val abstractUrl = extractJsonString(json, "AbstractURL").orEmpty()
    val heading = extractJsonString(json, "Heading").orEmpty()
    val answer = extractJsonString(json, "Answer").orEmpty()
    if (abstractText.isNotBlank() || answer.isNotBlank()) {
        val snippet = when {
            answer.isNotBlank() && abstractText.isNotBlank() -> "$answer — $abstractText"
            answer.isNotBlank() -> answer
            else -> abstractText
        }
        out += InternetSearchClient.Hit(
            title = heading.ifBlank { "DuckDuckGo" },
            url = abstractUrl,
            snippet = stripHtml(snippet),
        )
    }
    // RelatedTopics: mix of {Text, FirstURL} and nested {Topics:[...]} groups.
    var cursor = 0
    while (cursor < json.length) {
        val textAt = json.indexOf("\"Text\"", cursor)
        if (textAt < 0) break
        val firstUrlAt = json.indexOf("\"FirstURL\"", textAt)
        if (firstUrlAt < 0 || firstUrlAt - textAt > 400) {
            cursor = textAt + 6
            continue
        }
        val text = extractJsonString(json.substring(textAt), "Text").orEmpty()
        val firstUrl = extractJsonString(json.substring(firstUrlAt), "FirstURL").orEmpty()
        cursor = firstUrlAt + 10
        if (text.isBlank() && firstUrl.isBlank()) continue
        val title = text.substringBefore(" - ").substringBefore(" – ").trim().ifBlank { "Related" }
        val snippet = text.trim()
        out += InternetSearchClient.Hit(title, firstUrl, stripHtml(snippet))
        if (out.size >= 8) break
    }
    return out
}

internal fun parseWikipediaSearch(json: String): List<InternetSearchClient.Hit> {
    val out = ArrayList<InternetSearchClient.Hit>()
    var cursor = 0
    while (cursor < json.length && out.size < 8) {
        val titleAt = json.indexOf("\"title\"", cursor)
        if (titleAt < 0) break
        // Stay inside search hits: look for nearby snippet.
        val snippetAt = json.indexOf("\"snippet\"", titleAt)
        if (snippetAt < 0 || snippetAt - titleAt > 800) {
            cursor = titleAt + 7
            continue
        }
        val title = extractJsonString(json.substring(titleAt), "title").orEmpty()
        val snippet = extractJsonString(json.substring(snippetAt), "snippet").orEmpty()
        cursor = snippetAt + 9
        if (title.isBlank()) continue
        val url = "https://en.wikipedia.org/wiki/" + wikiTitlePath(title)
        out += InternetSearchClient.Hit(title, url, stripHtml(snippet))
    }
    return out
}

/** Lightweight "field":"value" extractor for the flat JSON blobs we get back. */
internal fun extractJsonString(json: String, field: String): String? {
    val key = "\"$field\""
    val keyAt = json.indexOf(key)
    if (keyAt < 0) return null
    var i = keyAt + key.length
    while (i < json.length && json[i].isWhitespace()) i++
    if (i >= json.length || json[i] != ':') return null
    i++
    while (i < json.length && json[i].isWhitespace()) i++
    if (i >= json.length) return null
    if (json[i] != '"') return null
    i++
    val out = StringBuilder()
    while (i < json.length) {
        val ch = json[i]
        if (ch == '\\') {
            if (i + 1 >= json.length) return null
            when (val escaped = json[i + 1]) {
                '"', '\\', '/' -> out.append(escaped)
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (i + 5 >= json.length) return null
                    val hex = json.substring(i + 2, i + 6)
                    val code = hex.toIntOrNull(16) ?: return null
                    out.append(code.toChar())
                    i += 6
                    continue
                }
                else -> out.append(escaped)
            }
            i += 2
            continue
        }
        if (ch == '"') return out.toString()
        out.append(ch)
        i++
    }
    return null
}

internal fun stripHtml(raw: String): String {
    return raw
        .replace(Regex("<[^>]+>"), " ")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun wikiTitlePath(title: String): String {
    return URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8.name())
        .replace("+", "%20")
}
