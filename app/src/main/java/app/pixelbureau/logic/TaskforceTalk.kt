package app.pixelbureau.logic

/** @mentions in taskforce chat. */
object Mentions {
    /** Member ids mentioned with @Name in [text], in order of first appearance, unique. */
    fun ids(text: String, members: List<Pair<String, String>>): List<String> {
        val hits = ArrayList<Pair<Int, String>>()
        val taken = BooleanArray(text.length)
        for ((id, name) in members.sortedByDescending { it.second.length }) {
            if (name.isBlank()) continue
            val token = "@$name"
            var from = 0
            while (true) {
                val at = text.indexOf(token, from, ignoreCase = true)
                if (at < 0) break
                val end = at + token.length
                val boundary = (end >= text.length || !text[end].isLetterOrDigit()) && (at == 0 || !text[at - 1].isLetterOrDigit())
                if (boundary && !taken[at]) {
                    for (i in at until end) taken[i] = true
                    hits.add(at to id)
                }
                from = end
            }
        }
        return hits.sortedBy { it.first }.map { it.second }.distinct()
    }

    /** Character ranges of @Name mentions, for highlighting. */
    fun ranges(text: String, names: List<String>): List<IntRange> {
        val out = ArrayList<IntRange>()
        for (name in names.filter { it.isNotBlank() }.sortedByDescending { it.length }) {
            var from = 0
            while (true) {
                val at = text.indexOf("@$name", from, ignoreCase = true)
                if (at < 0) break
                val r = at until at + name.length + 1
                if ((at == 0 || !text[at - 1].isLetterOrDigit()) && out.none { it.first <= r.last && r.first <= it.last }) out.add(r)
                from = r.last + 1
            }
        }
        return out.sortedBy { it.first }
    }

    /** The partial "@abc" being typed at the end of [draft], or null. */
    fun typing(draft: String): String? {
        val at = draft.lastIndexOf('@')
        if (at < 0 || (at > 0 && !draft[at - 1].isWhitespace())) return null
        val tail = draft.substring(at + 1)
        return if (tail.any { it.isWhitespace() }) null else tail
    }

    /** Replace the trailing "@abc" with "@Name ". */
    fun complete(draft: String, name: String): String {
        val at = draft.lastIndexOf('@')
        return if (at < 0) "$draft@$name " else draft.substring(0, at) + "@$name "
    }

    /**
     * Turn queue for a directed exchange: start with the user's mentions, then
     * anyone an agent mentions in their reply gets the next turn. Bounded.
     */
    class Queue(first: List<String>, private val maxTurns: Int = 6) {
        private val pending = ArrayDeque(first)
        var taken = 0
            private set

        fun next(): String? = if (taken >= maxTurns) null else pending.removeFirstOrNull()?.also { taken++ }

        fun replied(speaker: String, mentioned: List<String>) {
            for (id in mentioned) if (id != speaker && id !in pending) pending.addLast(id)
        }
    }
}

/** Agent-to-agent debate. */
object Debate {
    const val DEFAULT_ROUNDS = 3
    const val MAX_ROUNDS = 5

    data class Command(val topic: String, val rounds: Int)

    /** "/debate topic", "/debate 4 topic" or "debate: topic"; null when not a debate request. */
    fun parse(text: String): Command? {
        val t = text.trim()
        val body = when {
            t.startsWith("/debate", ignoreCase = true) -> t.substring(7)
            t.startsWith("debate:", ignoreCase = true) -> t.substring(7)
            else -> return null
        }.trim()
        val m = Regex("^(\\d+)\\s+(.*)$", RegexOption.DOT_MATCHES_ALL).find(body)
        val rounds = m?.groupValues?.get(1)?.toIntOrNull()
        val topic = (if (m != null) m.groupValues[2] else body).trim()
        return Command(topic, (rounds ?: DEFAULT_ROUNDS).coerceIn(1, MAX_ROUNDS))
    }

    /** "AGREE: yes" in the hidden footer (or a bare AGREE line). */
    fun agrees(raw: String): Boolean {
        val tail = raw.substringAfterLast("\n---", raw)
        return Regex("(?im)^\\s*AGREE\\s*(:\\s*(yes|true|y))?\\s*$").containsMatchIn(tail)
    }

    /** Stop after the round cap, or as soon as every voice in a round agrees. */
    fun shouldStop(round: Int, rounds: Int, agreeThisRound: List<Boolean>): Boolean =
        round >= rounds || (agreeThisRound.isNotEmpty() && agreeThisRound.all { it })
}

/** Short-turn, chat-like taskforce conversation. */
object Conversation {
    const val MAX_WORDS = 60
    const val MAX_TOKENS = 150

    /** ~6–10 turns, scaled with the number of speakers. */
    fun turnBudget(speakers: Int): Int = (2 + speakers * 2).coerceIn(6, 10)

    /** User-chosen [steps] (1–6 rounds): every speaker gets one turn per step. */
    fun turnBudget(speakers: Int, steps: Int): Int = steps.coerceIn(1, 6) * speakers.coerceAtLeast(1)

    /** Soft cut to [maxWords], ending at the last sentence boundary when there is one. */
    fun clip(text: String, maxWords: Int = MAX_WORDS): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size <= maxWords) return text.trim()
        val cut = words.take(maxWords).joinToString(" ")
        val end = cut.indexOfLast { it == '.' || it == '!' || it == '?' }
        return if (end >= cut.length / 3) cut.substring(0, end + 1) else "$cut…"
    }

    /**
     * Who speaks next: someone the last speaker @mentioned (not themselves),
     * else round-robin over [speakers] skipping the last speaker. Stops after
     * [maxTurns] or once every speaker has spoken and their latest says AGREE.
     */
    class Order(private val speakers: List<String>, val maxTurns: Int, private val extra: Set<String> = emptySet()) {
        var turns = 0
            private set
        private var rr = 0
        private var last: String? = null
        private val agreed = HashMap<String, Boolean>()

        fun next(mentioned: List<String> = emptyList()): String? {
            if (turns >= maxTurns || converged()) return null
            val pick = mentioned.firstOrNull { it != last && (it in speakers || it in extra) } ?: run {
                var p: String? = null
                for (k in 0 until speakers.size) {
                    val c = speakers[rr % speakers.size]; rr++
                    if (c != last || speakers.size == 1) { p = c; break }
                }
                p
            } ?: return null
            turns++
            last = pick
            return pick
        }

        fun spoke(id: String, agree: Boolean) {
            agreed[id] = agree
        }

        fun converged(): Boolean = speakers.isNotEmpty() && speakers.all { agreed[it] == true }
    }
}

/** Slash commands for the taskforce chat input. */
object Slash {
    enum class Kind { DEBATE, DISCUSS, CONCLUDE, STOP, SIGNOFF, STATUS }
    data class Spec(val kind: Kind, val word: String, val icon: String, val usage: String, val desc: String)
    data class Parsed(val kind: Kind, val arg: String = "", val rounds: Int? = null)

    val all = listOf(
        Spec(Kind.DEBATE, "debate", "🗣", "/debate [rounds] <topic>", "Members argue it out, lead concludes"),
        Spec(Kind.DISCUSS, "discuss", "💬", "/discuss <topic>", "Quick short-turn team chat"),
        Spec(Kind.CONCLUDE, "conclude", "📋", "/conclude", "Lead wraps up now"),
        Spec(Kind.STOP, "stop", "⏹", "/stop", "Stop the current discussion"),
        Spec(Kind.SIGNOFF, "signoff", "✅", "/signoff", "Sign off the finished work"),
        Spec(Kind.STATUS, "status", "📊", "/status", "Lead posts a brief progress update"),
    )

    /** Partial command word while the draft is just "/xyz" (no space yet), else null. */
    fun typing(draft: String): String? =
        if (draft.startsWith("/") && draft.none { it.isWhitespace() }) draft.substring(1) else null

    /** Suggestions for [prefix] that apply now. */
    fun suggest(prefix: String, discussing: Boolean, canSignOff: Boolean, members: Int): List<Spec> = all.filter {
        it.word.startsWith(prefix, ignoreCase = true) && when (it.kind) {
            Kind.STOP -> discussing
            Kind.SIGNOFF -> canSignOff
            Kind.DEBATE, Kind.DISCUSS -> !discussing && members >= 2
            Kind.CONCLUDE, Kind.STATUS -> !discussing
        }
    }

    fun parse(text: String): Parsed? {
        val t = text.trim()
        if (!t.startsWith("/")) return null
        val word = t.drop(1).takeWhile { !it.isWhitespace() }.lowercase()
        val rest = t.drop(1 + word.length).trim()
        val spec = all.firstOrNull { it.word == word } ?: return null
        return when (spec.kind) {
            Kind.DEBATE -> Debate.parse(t)?.let { Parsed(Kind.DEBATE, it.topic, it.rounds) }
            Kind.DISCUSS -> Parsed(Kind.DISCUSS, rest)
            else -> Parsed(spec.kind)
        }
    }
}

/** Slash commands in the 1:1 agent chat. */
object ChatSlash {
    val task = Slash.Spec(Slash.Kind.STATUS, "task", "📝", "/task <what to do>", "Assign a task to this agent")
    val all = listOf(task)

    fun suggest(prefix: String): List<Slash.Spec> = all.filter { it.word.startsWith(prefix, ignoreCase = true) }

    /** "/task ..." → the text after the command (may be blank); else null. */
    fun parseTask(text: String): String? {
        val t = text.trim()
        if (!t.startsWith("/task", ignoreCase = true)) return null
        val rest = t.substring(5)
        if (rest.isNotEmpty() && !rest.first().isWhitespace()) return null
        return rest.trim()
    }

    /** Prefill: first line (≤ 80 chars, cut at a word) is the title, the rest is the brief. */
    fun split(text: String): Pair<String, String> {
        val t = text.trim()
        if (t.isEmpty()) return "" to ""
        val nl = t.indexOf('\n')
        val first = if (nl >= 0) t.substring(0, nl).trim() else t
        val more = if (nl >= 0) t.substring(nl + 1).trim() else ""
        if (first.length <= 80) return first to more
        val cut = first.lastIndexOf(' ', 80).takeIf { it > 20 } ?: 80
        return first.substring(0, cut).trim() to (first.substring(cut).trim() + if (more.isNotEmpty()) "\n$more" else "").trim()
    }
}
