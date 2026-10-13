package app.pixelbureau.logic

/** Reply style given to every agent at hire. The user can rewrite it per person. */
const val DefaultOutputStyle =
    "Write in plain sentences. Lead with the answer. Keep it short enough to read on a phone. Use a list or a table only when it makes the facts easier to scan."

fun buildSystemPrompt(
    name: String,
    personality: String,
    duties: String,
    taskTitle: String?,
    taskBrief: String?,
    enabledTools: List<AgentTool> = emptyList(),
): String {
    return buildString {
        append("You are ")
        append(name.trim().ifEmpty { "an employee" })
        appendLine(", an employee at Pixel Bureau, a small company the user runs.")
        val instructions = personality.trim()
        if (instructions.isNotEmpty()) {
            appendLine()
            appendLine("Instructions:")
            appendLine(instructions)
        }
        val style = duties.trim()
        if (style.isNotEmpty()) {
            appendLine()
            appendLine("Output style:")
            appendLine(style)
        }
        if (instructions.isNotEmpty() || style.isNotEmpty()) {
            appendLine("These are your own rules: follow the instructions for the work, and let the output style shape how the reply sounds.")
        }
        if (!taskTitle.isNullOrBlank()) {
            appendLine()
            append("Current task: ")
            appendLine(taskTitle.trim())
            if (!taskBrief.isNullOrBlank()) {
                appendLine(taskBrief.trim())
            }
            appendLine()
            append(taskDepthNote())
        }
        val toolsSection = toolsPromptSection(enabledTools)
        if (toolsSection.isNotEmpty()) {
            appendLine()
            appendLine(toolsSection)
        }
        appendLine()
        append("Reply in character and do the work in the message itself. Keep it readable on a phone. Finish the answer; do not stop after a plan, a tool call, or a promise to search. A short follow-up such as \"find more info\" or \"run\" continues the subject already on the table. Do not leave the message blank, and never answer with the placeholder (No reply).")
    }.trim()
}

/**
 * Assigned work, not a chat reply. The example facts are for a company;
 * every other subject gets the same depth in its own terms.
 */
fun taskDepthNote(): String {
    return "This is an assigned task, not a chat reply. Do the deep version. " +
        "Research with the tools before you write. Search more than once, for the different facts the job needs, and read the pages. " +
        "Then write a full briefing the user can act on. Use ## sections. Use a table when you have numbers. " +
        "Name dates and figures, and say what you could not find. " +
        "Match the subject. A company or stock needs the price, P/E, market cap, shares outstanding, 52-week high and low, volume, and recent results. " +
        "Any other job gets the same depth in its own facts: who, when, how much, what changed, and how the options compare. " +
        "Do not stop after a short opinion."
}

/**
 * A task reply that is still a sketch. A section heading or a table counts as the briefing.
 */
fun isThinTaskReply(text: String): Boolean {
    val clean = stripThinkTags(text).trim()
    if (clean.length >= 700) {
        return false
    }
    if (Regex("""(?m)^#{1,3} """).containsMatchIn(clean)) {
        return false
    }
    if (clean.contains("|---") || clean.contains("| ---")) {
        return false
    }
    return true
}

/** An assigned task should keep researching until it has a briefing, up to [searchCap] searches. */
fun taskNeedsAnotherPass(
    text: String,
    searchesDone: Int,
    searchEnabled: Boolean,
    searchCap: Int = 4,
): Boolean {
    if (searchEnabled && searchesDone == 0) {
        return true
    }
    if (searchesDone >= searchCap) {
        return false
    }
    return isThinTaskReply(text)
}

/** Search text for a task. A long brief falls back to its title line. */
fun taskSearchQuery(messages: List<TurnMessage>): String? {
    searchQueryFromContext("", "", messages)?.let { return it }
    val last = messages.lastOrNull { it.role != "assistant" }?.text?.trim().orEmpty()
    val title = last.removePrefix("Task:").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
    return title?.take(180)?.takeIf { it.length >= 3 }
}

/** Old failed turns stored this exact placeholder. It is not a model answer. */
fun isPlaceholderReply(text: String): Boolean {
    val trimmed = text.trim().trimEnd('.', '…').trim()
    return trimmed.equals("(No reply)", ignoreCase = true)
}

/**
 * Private planning that never reaches the user. Grok often ends the turn here
 * ("Let's do search") and the chat looks cut off.
 */
fun isScratchpad(text: String): Boolean {
    val lower = text.trim().lowercase()
    if (lower.length < 40) {
        return false
    }
    if (lower.startsWith("we need answer") ||
        lower.startsWith("we need respond") ||
        lower.startsWith("we need to answer") ||
        lower.startsWith("we need to respond") ||
        lower.startsWith("we need stop") ||
        lower.startsWith("the user is asking")
    ) {
        return true
    }
    if (lower.contains("tool call format") ||
        lower.contains("missing search query") ||
        lower.contains("empty invoke") ||
        lower.contains("i'll do it in the message") ||
        lower.contains("let's issue") ||
        lower.contains("let's do search") ||
        (lower.contains("<invoke") && lower.contains("internet_search"))
    ) {
        return true
    }
    if (!lower.contains("internet_search")) {
        return false
    }
    return lower.contains("let me call") ||
        lower.contains("i need to use") ||
        lower.contains("need to specify") ||
        lower.contains("proper query") ||
        lower.contains("i'll then report")
}

/**
 * Loader label while a reply is in flight. Site visits stay visible.
 * The hopping pips stand in for the ellipsis.
 */
fun replyWorkLabel(text: String): String? {
    val trimmed = text.trim()
    return when (trimmed) {
        "Thinking…" -> "Thinking"
        "Writing…" -> "Writing"
        "Searching the web…" -> "Searching the web"
        "Working the task…" -> "Working the task"
        "Checking the time…" -> "Checking the time"
        "Trying again…" -> "Trying again"
        "Answering…" -> "Writing"
        else -> {
            val visit = "Checking out "
            if (trimmed.startsWith(visit) && trimmed.length > visit.length) {
                trimmed.trimEnd('.', '…')
            } else {
                null
            }
        }
    }
}

/**
 * A pasted search page: numbered hits, a result list, or the query by itself.
 * A normal answer that mentions one source stays an answer.
 */
fun isSearchResultDump(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    val lower = trimmed.lowercase()
    if (lower.startsWith("private notes for you") ||
        lower.startsWith("web search results") ||
        lower.startsWith("search results for")
    ) {
        return true
    }
    val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val hitLines = lines.count { line ->
        Regex("""^\d+\.\s+\S.*https?://""", RegexOption.IGNORE_CASE).containsMatchIn(line)
    }
    if (hitLines >= 2) {
        val prose = lines.filter { line ->
            !Regex("""^\d+\.\s+""").containsMatchIn(line) && !line.startsWith("http")
        }.sumOf { it.length }
        if (prose < 80) return true
    }
    val urlCount = Regex("""https?://""").findAll(lower).count()
    val searchPage = lower.startsWith("here's what i found") ||
        lower.startsWith("here is what i found") ||
        lower.startsWith("i searched for") ||
        lower.startsWith("i found these results") ||
        lower.startsWith("here are the search results") ||
        lower.startsWith("here are the results")
    if (searchPage && (hitLines >= 1 || urlCount >= 2)) return true
    val queryOnly = Regex("""(?i)^(search\s+)?query\s*:\s*\S""").containsMatchIn(trimmed)
    return queryOnly && lines.size <= 2 && urlCount == 0 && trimmed.length < 240
}

/** Pull a search string out of a plan that never became a real tool call. */
fun recoverSearchQuery(text: String): String? {
    val patterns = listOf(
        Regex("""(?i)(?:search|query)\s+"([^"\n]{3,180})""""),
        Regex("""(?i)query(?:"|')?\s*[:=]\s*"([^"\n]{3,180})""""),
        Regex("""(?i)<parameter\b[^>]*\bname="query"[^>]*>([^<\n]{3,180})</parameter>"""),
        Regex("""(?i)name="query">([^<\n]{3,180})<"""),
    )
    var bestAt = -1
    var best: String? = null
    for (pattern in patterns) {
        for (match in pattern.findAll(text)) {
            val value = match.groupValues[1].trim()
            if (value.isEmpty() || isJsonSchema(value)) {
                continue
            }
            if (match.range.first >= bestAt) {
                bestAt = match.range.first
                best = value
            }
        }
    }
    return best
}

/**
 * Query for an internet_search call. A blank call, or a follow-up such as
 * "find more info", is filled from the subject already on the table.
 */
fun searchQueryFromContext(
    arguments: String,
    plan: String,
    messages: List<TurnMessage>,
): String? {
    val fromArgs = extractToolQuery(arguments)
    if (fromArgs.isNotBlank() && !isSearchNudge(fromArgs)) {
        return fromArgs
    }
    recoverSearchQuery(plan)?.takeIf { !isSearchNudge(it) }?.let { return it }
    val users = messages.filter { it.role != "assistant" }
    for (message in users.asReversed()) {
        recoverSearchQuery(message.text)?.takeIf { !isSearchNudge(it) }?.let { return it }
    }
    return searchTopicFromMessages(messages)
}

/**
 * "find more info", "run", and "yes do that" keep the current subject.
 * "find news on Maybank" is a subject.
 */
fun isSearchNudge(text: String): Boolean {
    val lower = text.trim().lowercase()
        .trim('"', '\'', '.', '!', '?', '…', ' ')
        .replace(Regex("""\s+"""), " ")
    if (lower.isEmpty() || lower.length > 42) {
        return false
    }
    if (lower in searchNudges) {
        return true
    }
    return lower.startsWith("find more") ||
        lower.startsWith("don't stop") ||
        lower.startsWith("dont stop") ||
        lower.startsWith("do not stop") ||
        lower.startsWith("do it") ||
        lower.contains("stop stopping")
}

private val searchNudges = setOf(
    "find more info",
    "more info",
    "find more",
    "don't stop",
    "dont stop",
    "do not stop",
    "stop stopping",
    "run",
    "go",
    "go on",
    "continue",
    "continues",
    "again",
    "yes",
    "yep",
    "yeah",
    "ok",
    "okay",
    "sure",
    "please",
    "pls",
    "yes do that",
    "yes do the former",
    "yes please",
    "yes pls",
    "yes pls do",
    "do it",
    "do it then",
    "do that",
    "do the former",
    "search",
    "search again",
    "try again",
    "what does it mean",
    "what do you mean",
)

/**
 * The model said it would search and then ended the turn.
 * A finished explanation that mentions a search stays an answer.
 */
fun isSearchPromise(text: String): Boolean {
    val clean = stripThinkTags(text).trim()
    if (clean.isEmpty() || clean.length > 700) {
        return false
    }
    if (Regex("""https?://|\d""").containsMatchIn(clean)) {
        return false
    }
    val lower = clean.lowercase()
    if (searchPromiseCues.none { lower.contains(it) }) {
        return false
    }
    val explained = clean.split(Regex("""\n\s*\n""")).any { paragraph ->
        val body = paragraph.trim()
        body.length > 160 && searchPromiseCues.none { body.lowercase().contains(it) }
    }
    return !explained
}

private val searchPromiseCues = listOf(
    "running the correct",
    "running the real",
    "running the right",
    "running the actual",
    "running it now",
    "let me run",
    "let me search",
    "i'll search",
    "i will search",
    "searching now",
    "searching the web",
    "correct search",
    "right quer",
    "not stopping",
    "run the right",
    "run the correct",
    "run the real",
    "i'll run",
    "i will run",
)

/** Drop leaked reasoning tags. The words around them stay. */
fun stripThinkTags(text: String): String {
    return text
        .replace(Regex("""(?is)<think>.*?</think>"""), " ")
        .replace(Regex("""(?i)</?think>"""), " ")
        .trim()
}

/**
 * Subject to search. A nudge uses the lookup the assistant just offered,
 * then the last real user question.
 */
fun searchTopicFromMessages(messages: List<TurnMessage>): String? {
    val users = messages.filter { it.role != "assistant" }.map { it.text }
    val latest = users.lastOrNull()?.trim().orEmpty()
    if (latest.isNotEmpty() && !isSearchNudge(latest)) {
        return preferredUserRequest(users)
    }
    val offer = messages.asReversed().firstNotNullOfOrNull { message ->
        if (message.role != "assistant") {
            return@firstNotNullOfOrNull null
        }
        if (isSearchPromise(message.text) ||
            isScratchpad(message.text) ||
            isSearchResultDump(message.text) ||
            isPlaceholderReply(message.text)
        ) {
            return@firstNotNullOfOrNull null
        }
        offeredLookup(message.text)
    }
    if (!offer.isNullOrBlank()) {
        return offer
    }
    return preferredUserRequest(users)
}

/** The question an assistant offered to look up, with the "want me to" wrapper removed. */
fun offeredLookup(text: String): String? {
    val clean = stripThinkTags(text).trim()
    if (clean.length < 12) {
        return null
    }
    val lower = clean.lowercase()
    val offered = lower.contains("want") && (
        lower.contains("price") ||
            lower.contains("financial") ||
            lower.contains("number") ||
            lower.contains("search")
        )
    if (!offered) {
        return null
    }
    val line = clean.lines().map { it.trim() }.lastOrNull { sentence ->
        sentence.contains('?') || sentence.lowercase().contains("want")
    } ?: return null
    val clipped = line
        .substringAfterLast("—", line)
        .substringAfterLast(" - ", line)
        .replace(Regex("""(?i)^(want( me to)?|search( for)?)\s+"""), "")
        .replace(Regex("""(?i),?\s*or something else$"""), "")
        .trim(' ', '?', '.', '—', '-')
    return clipped.takeIf { it.length in 8..220 }
}

private val requestHints = listOf("news", "latest", "find", "search", "what", "who", "price", "when")

/** Latest user line that can be searched. A nudge loses to the real question. */
fun preferredUserRequest(texts: List<String>): String? {
    val usable = texts.map { it.trim() }.filter { text ->
        text.length in 8..240 && text.any { it.isLetter() } && !isSearchNudge(text)
    }
    return usable.asReversed().firstOrNull { text ->
        val lower = text.lowercase()
        requestHints.any { hint -> lower.contains(hint) }
    } ?: usable.lastOrNull()
}

data class TurnMessage(val role: String, val text: String, val taskId: String?)

/** History through the oldest user message that does not yet have a reply. */
fun historyForNextTurn(messages: List<TurnMessage>): List<TurnMessage>? {
    val lastAssistant = messages.indexOfLast { it.role == "assistant" }
    val pending = (lastAssistant + 1 until messages.size).firstOrNull { messages[it].role == "user" }
        ?: return null
    return messages.subList(0, pending + 1).filterNot { message ->
        message.role == "assistant" && (
            isPlaceholderReply(message.text) ||
                isScratchpad(message.text) ||
                isSearchResultDump(message.text) ||
                isSearchPromise(message.text)
            )
    }
}

fun taskMessage(title: String, brief: String): String {
    return buildString {
        append("Task: ")
        append(title.trim())
        if (brief.isNotBlank()) {
            append("\n\n")
            append(brief.trim())
        }
    }
}

/** Body shown on a task slip. The "Task:" prefix is the slip's own header. */
fun taskOrderBody(text: String): String {
    val trimmed = text.trim()
    return if (trimmed.startsWith("Task:")) trimmed.removePrefix("Task:").trimStart() else trimmed
}

data class LlmProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
)

/** Hosted APIs the phone calls directly. There is no app server. */
val llmProviders = listOf(
    LlmProvider("xai", "xAI", "https://api.x.ai/v1", "grok-4.7"),
    LlmProvider("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    LlmProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
    LlmProvider("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
)

fun llmProvider(id: String): LlmProvider {
    return llmProviders.firstOrNull { it.id == id } ?: llmProviders.first()
}

fun chatCompletionsUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
}

/** Finished normal chats that add one star. A finished task is its own star. */
const val ChatsPerStar = 12

/** Bureau score. [chatCredit] is progress toward the next star from ordinary chats. */
data class StarScore(
    val reputation: Int,
    val day: Int,
    val chatCredit: Int = 0,
)

/** Stars awarded once when the user signs off a COMPLETE taskforce. */
const val TaskforceSignOffStars = 3

/** Sign-off reward: [TaskforceSignOffStars] stars, once per taskforce id ([alreadyAwarded] guards). */
fun grantTaskforceSignOff(score: StarScore, alreadyAwarded: Boolean, steps: Int = TaskforceSignOffStars): StarScore =
    if (alreadyAwarded) score else grantStars(score, taskforceReward(steps))

/** Sign-off reward = chosen discussion steps: 1 step = 1★ … 6 steps = 6★. */
fun taskforceReward(steps: Int): Int = steps.coerceIn(1, 6)

/** One finished task. The day still moves on every fourth star. */
fun grantTaskStar(score: StarScore): StarScore = grantStars(score, 1)

/** One finished chat. Twelve of these add one star, then the count starts over. */
fun grantChat(score: StarScore): StarScore {
    val next = score.chatCredit + 1
    if (next < ChatsPerStar) {
        return score.copy(chatCredit = next)
    }
    val stars = next / ChatsPerStar
    return grantStars(score, stars).copy(chatCredit = next % ChatsPerStar)
}

private fun grantStars(score: StarScore, stars: Int): StarScore {
    if (stars <= 0) {
        return score
    }
    var reputation = score.reputation
    var day = score.day
    repeat(stars) {
        reputation += 1
        if (reputation % 4 == 0) {
            day += 1
        }
    }
    return score.copy(reputation = reputation, day = day, chatCredit = score.chatCredit)
}

fun officeClock(nowMillis: Long): String {
    val minuteOfDay = ((nowMillis / 1000L) % (9 * 60)).toInt()
    val hour = 9 + minuteOfDay / 60
    val minute = minuteOfDay % 60
    return "%d:%02d".format(hour, minute)
}

/**
 * Map grid motion to a facing. Camera looks from high x+y toward the origin,
 * so -y walks up-screen / away → back. Holds the current axis unless the other
 * axis clearly dominates (hysteresis) so residual float nudges cannot flip
 * back ↔ left/right (looks like spinning).
 *
 * Callers that walk a grid path should prefer the path-segment cardinal (see
 * OfficeSim) instead of per-tick float substeps — axis-lock residual corrections
 * chatter left/right at the end of each cell and, with canvas left-flip, read as
 * spinning. Soft 180° intermediates are intentionally disabled: front→side→back
 * loops looked worse than a clean sheet swap.
 */
fun facingFromDelta(dx: Float, dy: Float, current: String = "front"): String {
    val ax = kotlin.math.abs(dx)
    val ay = kotlin.math.abs(dy)
    if (ax < 0.01f && ay < 0.01f) {
        return current
    }
    // Require the other axis to be clearly stronger before switching families.
    val bias = 1.45f
    val preferY = when (current) {
        "back", "front" -> ay * bias >= ax
        "left", "right" -> ay >= ax * bias
        else -> ay >= ax
    }
    return if (preferY) {
        when {
            dy < -0.01f -> "back"
            dy > 0.01f -> "front"
            else -> current
        }
    } else {
        when {
            dx < -0.01f -> "left"
            dx > 0.01f -> "right"
            else -> current
        }
    }
}

/**
 * Previously inserted a side/cardinal hop on 180° turns. Disabled: those hops
 * combined with path-substep chatter produced front↔side↔back↔side loops
 * (back sheet alternating with a flipped front). Kept as a passthrough so older
 * call sites compile; do not re-enable without segment-stable facing.
 */
fun softenFacingTurn(current: String, desired: String, dx: Float, dy: Float): String {
    return desired
}

fun spriteFrame(spriteKey: String, anim: String, frame: Int, facing: String = "front"): String {
    val pose = when (anim) {
        "walk" -> "walk${frame.mod(4)}"
        // Front sheets only ship type0/type1 (type2 was back-generator-only).
        "type" -> "type${(frame / 5).mod(2)}"
        // Sip cycle: drink0 chest hold ↔ drink1 mouth (arm+mug baked in).
        "drink" -> "drink${(frame / 4).mod(2)}"
        "sit" -> "sit"
        "sleep" -> "sleep"
        else -> "idle"
    }
    val base = "chars/${spriteKey}_$pose"
    // All poses use default (front) sheets. Desk sit/type use these sheets;
    // canvas flips when OfficeMap.seatFacing is "left" (toward the laptop).
    // No *_back assets for sit/type. Sleep is a single curled sheet.
    // Walk/idle "back" movement also uses front sheets so faces stay visible.
    return base
}

val looks = listOf(
    "pixel" to "Pixel",
    "kai" to "Kai",
    "byte" to "Byte",
    "ops" to "Ops",
    "cursor" to "Cursor",
    "you" to "You",
    // Hired later. Existing rows keep the spriteKey they were saved with.
    "luna" to "Luna",
    "mina" to "Mina",
    "vera" to "Vera",
    "sora" to "Sora",
    "nola" to "Nola",
    "reid" to "Reid",
    "hugo" to "Hugo",
    "nico" to "Nico",
    "otto" to "Otto",
    "leif" to "Leif",
)
