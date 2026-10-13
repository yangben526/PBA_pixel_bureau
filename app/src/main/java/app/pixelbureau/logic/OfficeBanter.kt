package app.pixelbureau.logic

import kotlin.random.Random

/** One in [BanterLlmOdds] spoken lines may be replaced by that agent's own model. */
const val BanterLlmOdds = 20

fun banterLlmRoll(random: Random): Boolean = random.nextInt(BanterLlmOdds) == 0

/** A visual-only line the office asked the assigned model to rewrite. Not a chat message. */
data class BanterAsk(
    val agentId: String,
    val name: String,
    val partnerName: String,
    val heard: String,
    val token: Long,
)

/**
 * Tiny system line. Personality is clipped so a banter call stays a few dozen tokens.
 * No tools, no duties, no task brief.
 */
fun banterSystemPrompt(name: String, personality: String): String {
    val who = name.trim().ifEmpty { "a coworker" }.take(24)
    val voice = personality.trim().replace('\n', ' ').replace(Regex("\\s+"), " ").take(40).trim()
    return buildString {
        append("You are ")
        append(who)
        append('.')
        if (voice.isNotEmpty()) {
            append(" Voice: ")
            append(voice)
            append('.')
        }
        append(" One short office sentence, under 8 words. No quotes.")
    }
}

fun banterUserPrompt(partner: String, heard: String): String {
    val who = partner.trim().ifEmpty { "a coworker" }.take(24)
    val line = heard.trim().replace('\n', ' ').replace(Regex("\\s+"), " ").take(48).trim()
    return if (line.isEmpty()) {
        "Say one short line to $who."
    } else {
        "$who said: $line Reply in one short sentence."
    }
}

/**
 * Keep a model line bubble-sized. Plans, placeholders, and extra sentences are dropped
 * so the office can fall back to the canned line already on screen.
 */
fun clipBanterLine(raw: String): String? {
    var text = raw.trim()
    if (text.isEmpty() || isPlaceholderReply(text) || isScratchpad(text)) {
        return null
    }
    val lower = text.lowercase()
    if (lower.contains("tool call") ||
        lower.contains("let's do search") ||
        lower.contains("let us do search") ||
        lower.startsWith("we need")
    ) {
        return null
    }
    text = text.trim('"', '\'', '“', '”', ' ')
    text = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    val stop = text.indexOfAny(charArrayOf('.', '!', '?'))
    if (stop >= 0) {
        text = text.substring(0, stop + 1).trim()
    }
    val words = text.split(' ').filter { it.isNotEmpty() }
    if (words.size > 8) {
        text = words.take(8).joinToString(" ")
    }
    if (text.length > 42) {
        text = text.take(42).trimEnd()
    }
    if (text.length < 2 || text.none { it.isLetter() }) {
        return null
    }
    return text
}

/**
 * Alternating office small-talk. Index 0 is the agent who walked over;
 * index 1 is the partner, then they trade. Even length so the lead keeps the openers.
 */
val officeChatExchanges: List<List<String>> = listOf(
    listOf(
        "Deadline is today.",
        "We can still make it.",
        "I need one more pass.",
        "I will cover you.",
    ),
    listOf(
        "Coffee is fresh.",
        "Save me a cup.",
        "It is extra strong.",
        "Perfect, I need that.",
    ),
    listOf(
        "Found a weird bug.",
        "Is it on my side?",
        "Only after the save.",
        "I will peek at it.",
    ),
    listOf(
        "Lunch in ten?",
        "Yes, I am starving.",
        "Fridge looks empty.",
        "We can grab noodles.",
    ),
    listOf(
        "The server is hot.",
        "Fan just kicked on.",
        "Those lights look busy.",
        "Leave it to cool.",
    ),
    listOf(
        "Can you review this?",
        "Send it over.",
        "It is a tiny diff.",
        "I will read it now.",
    ),
    listOf(
        "Meeting in five.",
        "Do we both go?",
        "Just one of us.",
        "You take this one.",
    ),
    listOf(
        "Tests went green.",
        "About time, nice.",
        "One flake remains.",
        "I will rerun it.",
    ),
    listOf(
        "Ready to ship?",
        "Give me a minute.",
        "The note is written.",
        "Then we ship it.",
    ),
    listOf(
        "Want a snack?",
        "What is left?",
        "Cookies and apples.",
        "Bring me a cookie.",
    ),
    listOf(
        "Office is quiet.",
        "Everyone is heads down.",
        "I like this pace.",
        "Same, do not jinx it.",
    ),
    listOf(
        "The plant looks sad.",
        "Did anyone water it?",
        "Not since Monday.",
        "I will do it later.",
    ),
    listOf(
        "Any weekend plans?",
        "Mostly sleep.",
        "Same, I am tired.",
        "We earned the rest.",
    ),
    listOf(
        "Client pinged again.",
        "What do they want?",
        "A status by noon.",
        "I can draft that.",
    ),
    listOf(
        "Printer is jammed.",
        "Again? Classic.",
        "Paper is crumpled.",
        "I will yank it.",
    ),
    listOf(
        "I need quiet focus.",
        "I will keep it down.",
        "Thanks, deep work.",
        "Ping me if stuck.",
    ),
    listOf(
        "Nice work yesterday.",
        "Thanks, that was tough.",
        "The fix was clean.",
        "Glad it helped.",
    ),
    listOf(
        "Did you hear that?",
        "Hear what?",
        "A beep from the rack.",
        "Probably just the fan.",
    ),
    listOf(
        "This chair squeaks.",
        "Swap with mine.",
        "Yours looks comfy.",
        "It is, take it.",
    ),
    listOf(
        "Still here late?",
        "Wrapping one task.",
        "I can stay too.",
        "Go home, I got it.",
    ),
    listOf(
        "Got a second?",
        "Yeah, what's up?",
        "The draft looks off.",
        "Show me the line.",
    ),
    listOf(
        "Who is on the door?",
        "Nobody, it is quiet.",
        "Good, I can focus.",
        "I will watch it.",
    ),
)

val officeChatLines: Set<String> = officeChatExchanges.flatten().toSet()
