package app.pixelbureau.logic

import kotlin.random.Random

/**
 * Offline office banter (no model calls): short Kairosoft-style lines per
 * context, paired call/response exchanges with names, and a recent-line memory.
 * Placeholders: {me} speaker, {you} partner, {task} taskforce title, {duty} role word.
 */
object BanterBook {
    val work = listOf(
        "In the zone 🔥", "One more commit…", "Tests are green ✅", "Hmm, edge case.", "Coffee needed ☕",
        "Typing at max speed!", "This bug is sneaky 🐛", "Almost there!", "Refactor time.", "Nailed it 💪",
        "Who wrote this? Oh. Me.", "Inbox zero? Never.", "Deep focus mode.", "Saving… saved!", "Ctrl+S x10",
        "Let me think 🤔", "Ooh, clever idea!", "Shipping soon 🚀", "Docs first, then code.", "Spreadsheet magic ✨",
    )
    val idle = listOf(
        "Nice day at the bureau!", "Stretch break 🙆", "What's next?", "La la la ♪", "Feeling productive!",
        "Hmm… snack o'clock?", "Office looks great ✨", "Daydreaming 💭", "Who moved my mug?", "Ready for a task!",
        "Tiny walk, big ideas.", "Is it Friday yet?", "Pixel perfect ✓", "Hello, office plants!", "Brain recharging…",
    )
    val props: Map<String, List<String>> = mapOf(
        "vending" to listOf("Chips or cookies?", "It ate my coin! 😤", "Snack attack 🍫", "Ooh, new flavour!"),
        "coffee" to listOf("Fresh brew ☕", "Double shot please!", "Smells amazing~", "Latte art? Nope."),
        "arcade" to listOf("New high score! 🎮", "One more round…", "Insert coin!", "Boss level 😱"),
        "foosball" to listOf("Goal!! ⚽", "Rematch?", "Spin is illegal!", "Defense mode!"),
        "aquarium" to listOf("Hi fishies 🐟", "So calming~", "Bubbles!", "That one's Bob."),
        "bookshelf" to listOf("Ooh, a manual 📚", "Research time!", "Classic read.", "Who returns these?"),
        "server" to listOf("Servers humming…", "Uptime 99.9% 💻", "Is it warm in here?", "Blinky lights!"),
        "plant" to listOf("Grow, buddy 🌱", "Needs water?", "So green!", "Photosynthesis!"),
        "plant2" to listOf("Big leaves 🌿", "Jungle vibes!", "Hello, tall plant."),
        "clock" to listOf("Time flies ⏰", "Already?!", "Tick tock…", "Deadline soon?"),
        "fridge" to listOf("Who ate my lunch?", "Cold snacks ❄️", "Yogurt time!"),
        "watercooler" to listOf("Hydrate 💧", "Heard the news?", "Glug glug."),
        "whiteboard" to listOf("Big plan here!", "Arrows everywhere 📈", "Erase? Never."),
    )
    val enter = listOf("Morning, team! 👋", "I'm here!", "Reporting in!", "Hi everyone!", "Let's do this!", "Made it! 🚶")
    val leave = listOf("See you! 👋", "Heading out~", "Bye bye!", "Clocking off!", "Good work, all!")
    val toConf = listOf("Meeting time!", "To the war room!", "Bringing notes 📋", "Glass room, here I go!", "Don't start without me!")
    val meeting = listOf(
        "About {task}…", "{task}: next steps?", "Slide 3, please.", "Action items! ✍️", "Agreed 👍",
        "Can we scope {task}?", "Love this chart 📊", "Who owns this?", "Let's vote.", "Great point!",
    )
    val signOff = listOf("We did it! 🎉", "Stars for us! ⭐", "Taskforce complete ⭐", "High five! ✋", "Celebration snacks!", "Signed & shipped 🚀")
    val morning = listOf("Good morning ☀️", "Coffee first…", "Early bird!", "Fresh start!")
    val lunch = listOf("Lunch time? 🍱", "Noodles or rice?", "So hungry…", "Food break!")
    val evening = listOf("Long day 🌙", "Almost home time!", "Wrapping up…", "Sunset vibes~")
    val onlineCall = listOf(
        "Can you hear me?", "You're on mute! 🔇", "Sharing my screen…", "Let's sync async", "Next slide please",
        "Sorry, bad connection 📶", "Can everyone see this?", "I'll drop a link in chat", "Hi from the glass room 👋",
        "Quick {duty} update…", "Camera on? 🎥", "Let's take this offline",
    )
    val focusWork = listOf(
        "Deep focus mode 🎧", "Quiet room, finally", "Heads down…", "No meetings, just {duty}", "Shh, flow state ✨", "Big table, big ideas",
    )
    val confEnter = listOf("Borrowing the glass room!", "Room's free? Nice.", "Taking a call in here 📞", "Need a quiet spot…")
    val confLeave = listOf("Room's all yours!", "Call done ✅", "Back to my desk~", "That was productive!")
    val bought = listOf("Ooh, new stuff! ✨", "Nice purchase!", "Who bought that?", "Fancy! 😍", "Office upgrade!", "Love the new look!")
    val dutyLines = listOf("My {duty} skills!", "{duty} time 💼", "Best at {duty}!", "More {duty} today.")

    /** Paired call/response exchanges: lead speaks even lines, partner odd. */
    val exchanges: List<List<String>> = listOf(
        listOf("Hey {you}!", "Hi {you}! 👋", "Busy day?", "Super busy!"),
        listOf("{you}, coffee?", "Always ☕", "My treat!", "You're the best!"),
        listOf("Saw the new chart?", "So pretty 📊", "Your work, {you}?", "Team effort!"),
        listOf("{you}, help me?", "Sure, what's up?", "Tricky bug 🐛", "Let's squash it!"),
        listOf("Lunch later?", "Yes please 🍱"),
        listOf("Nice work, {you}!", "Thanks, {you}! 😊", "Keep it up!"),
        listOf("Did you rest?", "Napped on the sofa", "Lucky! 😴", "Try it later!"),
        listOf("Foosball, {you}?", "You're on! ⚽", "Loser buys snacks", "Deal!"),
        listOf("Big deadline?", "Friday!", "We got this 💪"),
        listOf("{you}, idea!", "Tell me!", "Robots + coffee", "Genius 🤖"),
        listOf("How's it going?", "Smooth so far!", "Love to hear it"),
        listOf("{you}! High five!", "✋ Yeah!", "What for?", "Just because!"),
    )

    fun fill(line: String, me: String = "", you: String = "", task: String = "", duty: String = ""): String =
        line.replace("{me}", me).replace("{you}", you)
            .replace("{task}", task.ifBlank { "the plan" }.take(18))
            .replace("{duty}", duty.ifBlank { "work" })

    /** Rendered exchange for a pair. */
    fun exchange(index: Int, me: String, you: String): List<String> =
        exchanges[index].mapIndexed { i, l -> if (i % 2 == 0) fill(l, me, you) else fill(l, you, me) }

    /** True when [line] could be a scripted chat line for any of these names. */
    fun isChatLine(line: String, names: Collection<String>): Boolean {
        if (line in officeChatLines) return true
        for (a in names) for (b in names) {
            if (a == b) continue
            for (i in exchanges.indices) if (line in exchange(i, a, b)) return true
        }
        return false
    }

    fun daypart(hour: Int): List<String> = when (hour) {
        in 5..10 -> morning
        in 11..13 -> lunch
        in 17..23 -> evening
        else -> emptyList()
    }

    private val dutyWords = listOf(
        "code", "design", "research", "writing", "data", "sales", "marketing", "support", "finance", "legal", "ops", "testing", "news",
    )

    /** First known duty keyword in an agent's instructions, or "". */
    fun dutyKeyword(role: String): String {
        val low = role.lowercase()
        return dutyWords.firstOrNull { low.contains(it) } ?: ""
    }

    /** Readable for longer lines, brief for emoji. */
    fun seconds(text: String): Float = (1.3f + text.length * 0.07f).coerceIn(1.5f, 4.2f)
}

/** Remembers the last [cap] lines so the office doesn't repeat itself. */
class BanterMemory(private val cap: Int = 16) {
    private val recent = ArrayDeque<String>()

    fun pick(pool: List<String>, random: Random): String? {
        if (pool.isEmpty()) return null
        val fresh = pool.filter { it !in recent }
        val line = (fresh.ifEmpty { pool.filter { it != recent.lastOrNull() }.ifEmpty { pool } }).random(random)
        note(line)
        return line
    }

    fun note(line: String) {
        recent.remove(line)
        recent.addLast(line)
        while (recent.size > cap) recent.removeFirst()
    }

    fun recentLines(): List<String> = recent.toList()
}
