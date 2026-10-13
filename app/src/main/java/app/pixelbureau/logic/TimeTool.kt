package app.pixelbureau.logic

import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Phone clock and time-zone conversion for the time_tool agent call.
 * A blank place is the phone zone. A blank time is the current moment.
 */
fun timeTool(argumentsJson: String, clock: Clock = Clock.systemDefaultZone()): String {
    val args = timeArguments(argumentsJson)
    val phone = clock.zone
    val fromPick = pickZone(args.fromPlace, phone)
    val toPick = pickZone(args.place, phone)
    val fromError = zoneError(args.fromPlace, fromPick, clock)
    if (fromError != null) {
        return fromError
    }
    val toError = zoneError(args.place, toPick, clock)
    if (toError != null) {
        return toError
    }
    val from = (fromPick as ZonePick.One).zone
    val to = (toPick as ZonePick.One).zone
    val instant = parseWhen(args.time, from, clock)
        ?: return "Could not read the time \"${args.time}\". Use 15:30, 3:30 PM, or 2026-01-15 15:30."
    val askedNow = args.time.isBlank() || args.time.equals("now", true) || args.time.equals("current", true)
    return if (askedNow && args.place.isBlank() && args.fromPlace.isBlank()) {
        phoneNow(instant, phone)
    } else if (askedNow && args.fromPlace.isBlank()) {
        nowInPlace(args.place, instant, to, phone)
    } else {
        convert(args, instant, from, to, phone)
    }
}

/**
 * Arguments for a planned time_tool call that never became a real tool call.
 * Null when the text is not asking for the clock.
 */
fun timeArgumentsFromContext(plan: String, messages: List<TurnMessage>): String? {
    val users = messages.filter { it.role != "assistant" }.map { it.text }
    val blob = (listOf(plan) + users).joinToString("\n")
    if (!isTimeRequest(blob)) {
        return null
    }
    val place = recoverTimePlace(plan)
        ?: users.asReversed().firstNotNullOfOrNull { recoverTimePlace(it) }
        ?: ""
    return "{\"place\":${jsonString(place)},\"time\":\"\",\"from_place\":\"\"}"
}

internal fun isTimeRequest(text: String): Boolean {
    val lower = text.lowercase()
    if (lower.contains("time_tool")) return true
    if (lower.contains("time zone") || lower.contains("timezone")) return true
    if (lower.contains("current time") || lower.contains("local time") || lower.contains("system time")) {
        return true
    }
    if (Regex("""\bwhat time is it\b""").containsMatchIn(lower)) return true
    if (Regex("""\btime in\b""").containsMatchIn(lower)) return true
    if (Regex("""\btime at\b""").containsMatchIn(lower)) return true
    return Regex("""\bconvert\b.{0,48}\b(time|clock)\b""").containsMatchIn(lower)
}

internal fun recoverTimePlace(text: String): String? {
    val patterns = listOf(
        Regex("""(?i)\bplace\s+"([^"\n]{2,60})""""),
        Regex("""(?i)\b(?:what time is it|current time|local time|time)\s+in\s+([A-Za-z][^?.!\n]{1,48})"""),
        Regex("""(?i)\b(?:timezone|time zone)\s+(?:of|for|in)\s+([A-Za-z][^?.!\n]{1,48})"""),
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        pattern.find(text)?.groupValues?.get(1)?.let(::cleanPlace)?.takeIf { it.length >= 2 }
    }
}

private data class TimeArgs(val place: String, val time: String, val fromPlace: String)

private fun timeArguments(argumentsJson: String): TimeArgs {
    val raw = argumentsJson.trim()
    if (!raw.startsWith("{")) {
        return TimeArgs(place = raw.trim('"').trim(), time = "", fromPlace = "")
    }
    return TimeArgs(
        place = extractStringField(raw, "place").orEmpty().trim(),
        time = extractStringField(raw, "time").orEmpty().trim(),
        fromPlace = extractStringField(raw, "from_place").orEmpty()
            .ifBlank { extractStringField(raw, "from").orEmpty() }
            .trim(),
    )
}

private sealed interface ZonePick {
    data class One(val zone: ZoneId) : ZonePick
    data class Many(val zones: List<ZoneId>) : ZonePick
    data object None : ZonePick
}

private fun pickZone(place: String, fallback: ZoneId): ZonePick {
    if (place.isBlank()) {
        return ZonePick.One(fallback)
    }
    return resolveZone(place)
}

private fun zoneError(place: String, pick: ZonePick, clock: Clock): String? {
    if (place.isBlank()) {
        return null
    }
    return when (pick) {
        is ZonePick.One -> null
        is ZonePick.Many -> ambiguity(place, pick.zones, clock)
        ZonePick.None -> "No time zone matched \"$place\". Use a city (Tokyo, Cairo), a region (America/Chicago), or an offset (UTC+8)."
    }
}

private fun phoneNow(instant: Instant, phone: ZoneId): String {
    return buildString {
        appendLine("Current time on this phone")
        appendLine(describe(instant, phone))
        append("UTC: ")
        append(describe(instant, ZoneOffset.UTC))
    }.trim()
}

private fun nowInPlace(place: String, instant: Instant, to: ZoneId, phone: ZoneId): String {
    val name = place.ifBlank { to.id }
    return buildString {
        append("Current time in ")
        appendLine(name)
        appendLine(describe(instant, to))
        append("UTC: ")
        appendLine(describe(instant, ZoneOffset.UTC))
        append(relation(to, phone, instant))
    }.trim()
}

private fun convert(args: TimeArgs, instant: Instant, from: ZoneId, to: ZoneId, phone: ZoneId): String {
    val fromName = args.fromPlace.ifBlank { from.id }
    val toName = args.place.ifBlank { to.id }
    return buildString {
        append(args.time.ifBlank { "Now" })
        append(" in ")
        appendLine(fromName)
        appendLine(describe(instant, from))
        appendLine()
        appendLine(toName)
        appendLine(describe(instant, to))
        appendLine(relation(to, from, instant))
        if (phone != from && phone != to) {
            append("At that moment the phone (")
            append(phone.id)
            append(") reads ")
            append(describe(instant, phone))
            append('.')
        }
    }.trim()
}

private fun relation(to: ZoneId, from: ZoneId, instant: Instant): String {
    if (to == from) {
        return "${to.id} is the same time as ${from.id}."
    }
    val delta = to.rules.getOffset(instant).totalSeconds - from.rules.getOffset(instant).totalSeconds
    if (delta == 0) {
        return "${to.id} is the same time as ${from.id}."
    }
    val minutes = kotlin.math.abs(delta) / 60
    val hours = minutes / 60
    val remain = minutes % 60
    val span = when {
        remain == 0 && hours == 1 -> "1 hour"
        remain == 0 -> "$hours hours"
        hours == 0 -> "$remain minutes"
        else -> "$hours hours $remain minutes"
    }
    val direction = if (delta > 0) "ahead of" else "behind"
    return "${to.id} is $span $direction ${from.id}."
}

private val pretty: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, h:mm a", Locale.US)

private fun describe(instant: Instant, zone: ZoneId): String {
    val local = instant.atZone(zone)
    return "${pretty.format(local)} (${zoneLabel(zone, instant)})"
}

private fun zoneLabel(zone: ZoneId, instant: Instant): String {
    val id = zone.id
    if (id == "Z" || id == "UTC" || id == "Etc/UTC" || id == "GMT") {
        return "UTC"
    }
    val offset = formatOffset(zone.rules.getOffset(instant))
    return "$id, $offset"
}

private fun formatOffset(offset: ZoneOffset): String {
    if (offset == ZoneOffset.UTC) {
        return "UTC"
    }
    val total = offset.totalSeconds
    val sign = if (total >= 0) "+" else "-"
    val minutes = kotlin.math.abs(total) / 60
    val hours = minutes / 60
    val remain = minutes % 60
    return if (remain == 0) {
        "UTC$sign$hours"
    } else {
        "UTC$sign$hours:${remain.toString().padStart(2, '0')}"
    }
}

private fun ambiguity(place: String, zones: List<ZoneId>, clock: Clock): String {
    val instant = clock.instant()
    return buildString {
        append(place)
        appendLine(" matches more than one time zone:")
        zones.take(8).forEach { zone ->
            append("- ")
            appendLine(describe(instant, zone))
        }
        append("Ask which place, or pass an IANA id such as ")
        append(zones.first().id)
        append('.')
    }.trim()
}

private fun resolveZone(place: String): ZonePick {
    val trimmed = place.trim()
    if (trimmed.isEmpty()) {
        return ZonePick.None
    }
    parseOffsetZone(trimmed)?.let { return ZonePick.One(it) }
    val key = normalizePlace(trimmed)
    singleAliases[key]?.let { id -> parseZoneId(id)?.let { return ZonePick.One(it) } }
    multiAliases[key]?.let { ids ->
        val zones = ids.mapNotNull { parseZoneId(it) }
        if (zones.size == 1) return ZonePick.One(zones.first())
        if (zones.size > 1) return ZonePick.Many(zones)
    }
    parseZoneId(trimmed)?.let { return ZonePick.One(it) }
    parseZoneId(trimmed.replace(' ', '_'))?.let { return ZonePick.One(it) }
    val zones = ZoneId.getAvailableZoneIds().filter { id ->
        id.contains('/') && !id.startsWith("SystemV/")
    }
    val exact = zones.filter { id -> normalizePlace(id.substringAfterLast('/')) == key }
    when (exact.size) {
        1 -> return ZonePick.One(ZoneId.of(exact.first()))
        in 2..8 -> return ZonePick.Many(exact.map { ZoneId.of(it) })
    }
    if (key.length >= 4) {
        val loose = zones.filter { id -> normalizePlace(id.substringAfterLast('/')).contains(key) }
        when (loose.size) {
            1 -> return ZonePick.One(ZoneId.of(loose.first()))
            in 2..6 -> return ZonePick.Many(loose.map { ZoneId.of(it) })
        }
    }
    return ZonePick.None
}

private fun parseZoneId(text: String): ZoneId? {
    return try {
        ZoneId.of(text)
    } catch (_: DateTimeException) {
        null
    }
}

private fun parseOffsetZone(text: String): ZoneId? {
    val upper = text.trim().uppercase(Locale.US).replace(" ", "")
    if (upper == "Z" || upper == "UTC" || upper == "GMT" || upper == "ETC/UTC" || upper == "ETC/GMT") {
        return ZoneOffset.UTC
    }
    val body = when {
        upper.startsWith("UTC") -> upper.removePrefix("UTC")
        upper.startsWith("GMT") -> upper.removePrefix("GMT")
        upper.startsWith("+") || upper.startsWith("-") -> upper
        else -> return null
    }
    if (body.isEmpty()) {
        return ZoneOffset.UTC
    }
    return try {
        ZoneOffset.of(normalizeOffset(body))
    } catch (_: DateTimeException) {
        null
    }
}

private fun normalizeOffset(body: String): String {
    val sign = if (body.startsWith("-")) "-" else "+"
    val digits = body.trimStart('+', '-')
    if (digits.contains(':')) {
        val parts = digits.split(':')
        val hours = parts[0].toInt()
        val minutes = parts.getOrElse(1) { "0" }.toInt()
        return "%s%02d:%02d".format(sign, hours, minutes)
    }
    if (digits.length == 4 && digits.all { it.isDigit() }) {
        return "%s%s:%s".format(sign, digits.substring(0, 2), digits.substring(2))
    }
    return "%s%02d:00".format(sign, digits.toInt())
}

private fun parseWhen(text: String, zone: ZoneId, clock: Clock): Instant? {
    val raw = text.trim()
    if (raw.isEmpty() || raw.equals("now", true) || raw.equals("current", true)) {
        return clock.instant()
    }
    try {
        return Instant.parse(raw)
    } catch (_: DateTimeParseException) {
    }
    try {
        return OffsetDateTime.parse(raw).toInstant()
    } catch (_: DateTimeParseException) {
    }
    try {
        return ZonedDateTime.parse(raw).toInstant()
    } catch (_: DateTimeParseException) {
    }
    val datePatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd h:mm a",
        "d MMM yyyy HH:mm",
        "d MMM yyyy h:mm a",
        "MMM d yyyy HH:mm",
        "MMM d, yyyy HH:mm",
        "MMM d, yyyy h:mm a",
        "MMMM d, yyyy h:mm a",
    )
    for (pattern in datePatterns) {
        parseLocalDateTime(raw, pattern)?.let { return it.atZone(zone).toInstant() }
    }
    val day = clock.instant().atZone(zone).toLocalDate()
    val clockText = Regex("""(?i)^(\d{1,2})(?::(\d{2}))?\s*(am|pm)$""").matchEntire(raw.replace(".", ""))
    if (clockText != null) {
        var hour = clockText.groupValues[1].toInt()
        val minute = clockText.groupValues[2].toIntOrNull() ?: 0
        if (hour !in 1..12 || minute !in 0..59) {
            return null
        }
        val marker = clockText.groupValues[3].lowercase(Locale.US)
        if (marker == "pm" && hour != 12) hour += 12
        if (marker == "am" && hour == 12) hour = 0
        return LocalTime.of(hour, minute).atDate(day).atZone(zone).toInstant()
    }
    for (pattern in listOf("H:mm:ss", "H:mm")) {
        parseLocalTime(raw, pattern)?.let { return it.atDate(day).atZone(zone).toInstant() }
    }
    return null
}

private fun parseLocalDateTime(text: String, pattern: String): LocalDateTime? {
    return try {
        formatter(pattern).parse(text, LocalDateTime::from)
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun parseLocalTime(text: String, pattern: String): LocalTime? {
    return try {
        formatter(pattern).parse(text, LocalTime::from)
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun formatter(pattern: String): DateTimeFormatter {
    return DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern(pattern)
        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
        .toFormatter(Locale.US)
}

private fun cleanPlace(raw: String): String {
    var text = raw.trim().trimEnd('?', '.', '!', ',', ';', ':')
    val suffixes = listOf(" right now", " please", " today", " currently", " now")
    var changed = true
    while (changed) {
        changed = false
        for (suffix in suffixes) {
            if (text.lowercase(Locale.US).endsWith(suffix)) {
                text = text.dropLast(suffix.length).trim()
                changed = true
            }
        }
    }
    return text.trim()
}

private fun normalizePlace(text: String): String {
    return text.lowercase(Locale.US)
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private val singleAliases = mapOf(
    "japan" to "Asia/Tokyo",
    "tokyo" to "Asia/Tokyo",
    "osaka" to "Asia/Tokyo",
    "jst" to "Asia/Tokyo",
    "china" to "Asia/Shanghai",
    "beijing" to "Asia/Shanghai",
    "shanghai" to "Asia/Shanghai",
    "prc" to "Asia/Shanghai",
    "hong kong" to "Asia/Hong_Kong",
    "hongkong" to "Asia/Hong_Kong",
    "hkt" to "Asia/Hong_Kong",
    "taiwan" to "Asia/Taipei",
    "taipei" to "Asia/Taipei",
    "korea" to "Asia/Seoul",
    "south korea" to "Asia/Seoul",
    "seoul" to "Asia/Seoul",
    "kst" to "Asia/Seoul",
    "india" to "Asia/Kolkata",
    "kolkata" to "Asia/Kolkata",
    "calcutta" to "Asia/Kolkata",
    "mumbai" to "Asia/Kolkata",
    "bombay" to "Asia/Kolkata",
    "delhi" to "Asia/Kolkata",
    "new delhi" to "Asia/Kolkata",
    "malaysia" to "Asia/Kuala_Lumpur",
    "kuala lumpur" to "Asia/Kuala_Lumpur",
    "kl" to "Asia/Kuala_Lumpur",
    "myt" to "Asia/Kuala_Lumpur",
    "singapore" to "Asia/Singapore",
    "sgt" to "Asia/Singapore",
    "thailand" to "Asia/Bangkok",
    "bangkok" to "Asia/Bangkok",
    "vietnam" to "Asia/Ho_Chi_Minh",
    "hanoi" to "Asia/Ho_Chi_Minh",
    "saigon" to "Asia/Ho_Chi_Minh",
    "ho chi minh" to "Asia/Ho_Chi_Minh",
    "ho chi minh city" to "Asia/Ho_Chi_Minh",
    "philippines" to "Asia/Manila",
    "manila" to "Asia/Manila",
    "jakarta" to "Asia/Jakarta",
    "bali" to "Asia/Makassar",
    "dubai" to "Asia/Dubai",
    "uae" to "Asia/Dubai",
    "abu dhabi" to "Asia/Dubai",
    "qatar" to "Asia/Qatar",
    "doha" to "Asia/Qatar",
    "saudi arabia" to "Asia/Riyadh",
    "riyadh" to "Asia/Riyadh",
    "israel" to "Asia/Jerusalem",
    "jerusalem" to "Asia/Jerusalem",
    "uk" to "Europe/London",
    "britain" to "Europe/London",
    "england" to "Europe/London",
    "scotland" to "Europe/London",
    "wales" to "Europe/London",
    "london" to "Europe/London",
    "bst" to "Europe/London",
    "ireland" to "Europe/Dublin",
    "dublin" to "Europe/Dublin",
    "france" to "Europe/Paris",
    "paris" to "Europe/Paris",
    "germany" to "Europe/Berlin",
    "berlin" to "Europe/Berlin",
    "cet" to "Europe/Berlin",
    "spain" to "Europe/Madrid",
    "madrid" to "Europe/Madrid",
    "italy" to "Europe/Rome",
    "rome" to "Europe/Rome",
    "netherlands" to "Europe/Amsterdam",
    "amsterdam" to "Europe/Amsterdam",
    "portugal" to "Europe/Lisbon",
    "lisbon" to "Europe/Lisbon",
    "greece" to "Europe/Athens",
    "athens" to "Europe/Athens",
    "poland" to "Europe/Warsaw",
    "warsaw" to "Europe/Warsaw",
    "sweden" to "Europe/Stockholm",
    "stockholm" to "Europe/Stockholm",
    "norway" to "Europe/Oslo",
    "oslo" to "Europe/Oslo",
    "denmark" to "Europe/Copenhagen",
    "copenhagen" to "Europe/Copenhagen",
    "switzerland" to "Europe/Zurich",
    "zurich" to "Europe/Zurich",
    "austria" to "Europe/Vienna",
    "vienna" to "Europe/Vienna",
    "turkey" to "Europe/Istanbul",
    "istanbul" to "Europe/Istanbul",
    "moscow" to "Europe/Moscow",
    "ukraine" to "Europe/Kiev",
    "kyiv" to "Europe/Kiev",
    "kiev" to "Europe/Kiev",
    "egypt" to "Africa/Cairo",
    "cairo" to "Africa/Cairo",
    "south africa" to "Africa/Johannesburg",
    "johannesburg" to "Africa/Johannesburg",
    "nigeria" to "Africa/Lagos",
    "lagos" to "Africa/Lagos",
    "kenya" to "Africa/Nairobi",
    "nairobi" to "Africa/Nairobi",
    "new york" to "America/New_York",
    "nyc" to "America/New_York",
    "est" to "America/New_York",
    "edt" to "America/New_York",
    "eastern" to "America/New_York",
    "eastern time" to "America/New_York",
    "chicago" to "America/Chicago",
    "cdt" to "America/Chicago",
    "central time" to "America/Chicago",
    "denver" to "America/Denver",
    "mst" to "America/Denver",
    "mdt" to "America/Denver",
    "mountain" to "America/Denver",
    "mountain time" to "America/Denver",
    "los angeles" to "America/Los_Angeles",
    "la" to "America/Los_Angeles",
    "san francisco" to "America/Los_Angeles",
    "seattle" to "America/Los_Angeles",
    "pst" to "America/Los_Angeles",
    "pdt" to "America/Los_Angeles",
    "pacific" to "America/Los_Angeles",
    "pacific time" to "America/Los_Angeles",
    "phoenix" to "America/Phoenix",
    "anchorage" to "America/Anchorage",
    "alaska" to "America/Anchorage",
    "honolulu" to "Pacific/Honolulu",
    "hawaii" to "Pacific/Honolulu",
    "hst" to "Pacific/Honolulu",
    "mexico city" to "America/Mexico_City",
    "toronto" to "America/Toronto",
    "montreal" to "America/Toronto",
    "vancouver" to "America/Vancouver",
    "sydney" to "Australia/Sydney",
    "aest" to "Australia/Sydney",
    "aedt" to "Australia/Sydney",
    "melbourne" to "Australia/Melbourne",
    "brisbane" to "Australia/Brisbane",
    "perth" to "Australia/Perth",
    "adelaide" to "Australia/Adelaide",
    "darwin" to "Australia/Darwin",
    "auckland" to "Pacific/Auckland",
    "new zealand" to "Pacific/Auckland",
    "nzst" to "Pacific/Auckland",
    "nzdt" to "Pacific/Auckland",
    "fiji" to "Pacific/Fiji",
    "utc" to "UTC",
    "gmt" to "UTC",
    "z" to "UTC",
    "zulu" to "UTC",
    "greenwich" to "UTC",
)

private val multiAliases = mapOf(
    "usa" to listOf("America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Anchorage", "Pacific/Honolulu"),
    "us" to listOf("America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Anchorage", "Pacific/Honolulu"),
    "united states" to listOf("America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Anchorage", "Pacific/Honolulu"),
    "america" to listOf("America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles"),
    "canada" to listOf("America/Toronto", "America/Winnipeg", "America/Edmonton", "America/Vancouver", "America/Halifax", "America/St_Johns"),
    "australia" to listOf("Australia/Sydney", "Australia/Brisbane", "Australia/Adelaide", "Australia/Perth", "Australia/Darwin", "Australia/Hobart"),
    "russia" to listOf("Europe/Moscow", "Asia/Yekaterinburg", "Asia/Novosibirsk", "Asia/Vladivostok"),
    "brazil" to listOf("America/Sao_Paulo", "America/Manaus", "America/Noronha"),
    "indonesia" to listOf("Asia/Jakarta", "Asia/Makassar", "Asia/Jayapura"),
    "mexico" to listOf("America/Mexico_City", "America/Cancun", "America/Mazatlan", "America/Tijuana"),
    "cst" to listOf("America/Chicago", "Asia/Shanghai", "America/Havana"),
    "ist" to listOf("Asia/Kolkata", "Europe/Dublin", "Asia/Jerusalem"),
    "ast" to listOf("America/Halifax", "America/Puerto_Rico", "Asia/Riyadh"),
    "gst" to listOf("Asia/Dubai", "Atlantic/South_Georgia"),
)
