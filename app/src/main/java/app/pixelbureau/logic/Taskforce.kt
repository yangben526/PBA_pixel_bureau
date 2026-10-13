package app.pixelbureau.logic

/**
 * Taskforce (team meeting) rules. Pure Kotlin so the orchestration stop
 * conditions and state transitions are unit-testable.
 *
 * Lifecycle:
 *   active ──(lead: done)──────────▶ complete ──(user: Sign off)──▶ signed_off
 *     │ ▲                               │
 *     │ └──(user message)── blocked / paused / complete
 *     ├──(lead: blocked)───▶ blocked    (waiting for the user)
 *     └──(round cap hit)───▶ paused     (no infinite loops; user nudges to resume)
 *   any non-final ──(user: Disband)──▶ disbanded
 */
object TaskforceStatus {
    const val ACTIVE = "active"
    const val BLOCKED = "blocked"
    const val PAUSED = "paused"
    const val COMPLETE = "complete"
    const val SIGNED_OFF = "signed_off"
    const val DISBANDED = "disbanded"

    /** Still a live team: shown in the chat selector, members sit in the room. */
    fun isOpen(status: String): Boolean = status != SIGNED_OFF && status != DISBANDED
}

object TaskforceRules {
    /** Lead-plan → members → lead-review rounds per run before pausing for the user. */
    const val MAX_ROUNDS_PER_RUN = 3
    /** Member turns per round (lead turns not counted). */
    const val MAX_MEMBER_TURNS_PER_ROUND = 4
    /** Max people in one taskforce (the room has six chairs). */
    /** 4 conference chairs + 1 standing spot at the top of the table. */
    const val MAX_MEMBERS = 5

    /** The loop runs only while ACTIVE. */
    fun shouldRun(status: String): Boolean = status == TaskforceStatus.ACTIVE

    /**
     * After the lead's PLAN turn: a lead that already says done/blocked ends the
     * run right there (no member phase). Returns the new status, or null to go on.
     */
    fun afterPlan(control: AgentControl): String? = when (control.status) {
        ControlStatus.DONE -> TaskforceStatus.COMPLETE
        ControlStatus.BLOCKED -> TaskforceStatus.BLOCKED
        ControlStatus.CONTINUE -> null
    }

    /** After the lead's REVIEW turn closes round [roundsDone] (1-based count incl. this one). */
    fun afterReview(control: AgentControl, roundsDone: Int): String = when {
        control.status == ControlStatus.DONE -> TaskforceStatus.COMPLETE
        control.status == ControlStatus.BLOCKED -> TaskforceStatus.BLOCKED
        roundsDone >= MAX_ROUNDS_PER_RUN -> TaskforceStatus.PAUSED
        else -> TaskforceStatus.ACTIVE
    }

    /** A user message re-opens a waiting/paused/complete team; final states ignore it. */
    fun onUserMessage(status: String): String =
        if (TaskforceStatus.isOpen(status)) TaskforceStatus.ACTIVE else status

    fun canSignOff(status: String): Boolean = status == TaskforceStatus.COMPLETE

    /** Sign-off is only legal from COMPLETE. */
    fun signOff(status: String): String? = if (canSignOff(status)) TaskforceStatus.SIGNED_OFF else null

    /** "Conclude meeting" on the lead's Conclusion: user sign-off from any open state. */
    fun concludeMeeting(status: String): String? = if (TaskforceStatus.isOpen(status)) TaskforceStatus.SIGNED_OFF else null

    /** Disband is legal from any open state. */
    fun disband(status: String): String? = if (TaskforceStatus.isOpen(status)) TaskforceStatus.DISBANDED else null

    /**
     * Who works this round: names the lead ASSIGNed (matched case-insensitively,
     * lead excluded, member order kept), else every non-lead member. Capped.
     */
    /** Perspectives used when the lead's plan has no usable per-member split. */
    val FALLBACK_ROLES = listOf(
        "Proposer: draft the main proposal / solution",
        "Critic: find weaknesses and counter-arguments in the proposal",
        "Risk: list risks, edge cases and mitigations",
        "Implementation: concrete steps, owners, timeline and tooling",
        "User advocate: what the end user needs, and how we'd measure success",
    )

    const val AUTO_DEBATE_ROUNDS = 2

    /** Phases of one default round: plan → parallel work → worker debate (2+ workers) → lead conclusion. */
    fun roundPhases(workerCount: Int): List<TaskforcePhase> = buildList {
        add(TaskforcePhase.PLAN)
        if (workerCount > 0) add(TaskforcePhase.WORK)
        if (workerCount >= 2) add(TaskforcePhase.DEBATE)
        if (workerCount > 0) add(TaskforcePhase.CONCLUDE)
    }

    /** Selected members with the chosen lead first (lead persists as Taskforce.leadId). */
    fun withLead(picked: List<String>, leadId: String?): List<String> =
        if (leadId != null && leadId in picked) listOf(leadId) + (picked - leadId) else picked

    /**
     * One DISTINCT assignment per worker, from `ASSIGN: Name: task` footer lines or
     * `@Name: task` lines in the plan. Missing or duplicate tasks get a fallback
     * perspective by member order.
     */
    fun distinctAssignments(
        control: AgentControl,
        planText: String,
        members: List<Pair<String, String>>,
        leadId: String,
    ): Map<String, String> {
        val workers = members.filter { it.first != leadId }
        val byName = LinkedHashMap<String, String>()
        control.assignments.forEach { (k, v) -> byName[k.trim().lowercase()] = v.trim() }
        val at = Regex("""^\s*[-*]?\s*@([^:\n]{1,40}):\s*(.+)$""", RegexOption.MULTILINE)
        for (m in at.findAll(planText)) {
            val key = m.groupValues[1].trim().lowercase()
            if (key !in byName) byName[key] = m.groupValues[2].trim()
        }
        val out = LinkedHashMap<String, String>()
        val seen = HashSet<String>()
        var role = 0
        for ((id, name) in workers) {
            val task = byName[name.trim().lowercase()]?.takeIf { it.isNotBlank() }
            val norm = task?.lowercase()?.replace(Regex("[^a-z0-9]+"), " ")?.trim()
            if (task != null && norm != null && seen.add(norm)) {
                out[id] = task
            } else {
                out[id] = FALLBACK_ROLES[role % FALLBACK_ROLES.size]
                role++
            }
        }
        return out
    }

    /** Worker brief: only my assignment, what the others do, and don't duplicate. */
    fun workerBrief(me: String, assignments: Map<String, String>, names: Map<String, String>): String = buildString {
        append(assignments[me].orEmpty())
        val others = assignments.filterKeys { it != me }
        if (others.isNotEmpty()) {
            append("\nTeammates are covering (do NOT duplicate their work; take a distinct angle): ")
            append(others.entries.joinToString("; ") { "${names[it.key] ?: it.key} — ${it.value}" })
        }
    }

    fun assignees(
        control: AgentControl,
        members: List<Pair<String, String>>, // id to name, lead first
        leadId: String,
    ): List<String> {
        val others = members.filter { it.first != leadId }
        val named = control.assignments.keys.map { it.trim().lowercase() }.toSet()
        val picked = if (named.isEmpty()) others else others.filter { it.second.trim().lowercase() in named }
        return (picked.ifEmpty { others }).take(MAX_MEMBER_TURNS_PER_ROUND).map { it.first }
    }
}

enum class ControlStatus { CONTINUE, DONE, BLOCKED }

data class ChecklistItem(val text: String, val done: Boolean)

/** The machine-readable footer each agent appends to its thread message. */
data class AgentControl(
    val status: ControlStatus = ControlStatus.CONTINUE,
    val progress: Int? = null,
    val checklist: List<ChecklistItem> = emptyList(),
    /** Assignee name → sub-task (lead only). */
    val assignments: Map<String, String> = emptyMap(),
    /** Debate footer `AGREE: yes`. */
    val agree: Boolean = false,
)

data class ParsedReply(val display: String, val control: AgentControl)

private val controlKey = Regex("""^\s*(STATUS|PROGRESS|CHECKLIST|ASSIGN|AGREE)\s*:""", RegexOption.IGNORE_CASE)
private val checkLine = Regex("""^\s*[-*]?\s*\[( |x|X)]\s*(.+)$""")

/**
 * Split an agent reply into the text shown in the thread and its control
 * footer. The footer starts at the first STATUS/PROGRESS/CHECKLIST/ASSIGN line
 * (an optional "---" right above it is dropped too).
 */
fun parseAgentReply(raw: String): ParsedReply {
    val lines = raw.trim().lines()
    val start = lines.indexOfFirst { controlKey.containsMatchIn(it) }
    if (start < 0) return ParsedReply(raw.trim(), AgentControl())
    var cut = start
    while (cut > 0 && lines[cut - 1].trim().let { it.isEmpty() || it.all { c -> c == '-' } }) cut--
    val display = lines.subList(0, cut).joinToString("\n").trim()
    var status = ControlStatus.CONTINUE
    var progress: Int? = null
    val checklist = ArrayList<ChecklistItem>()
    val assignments = LinkedHashMap<String, String>()
    var inChecklist = false
    var agree = false
    for (line in lines.subList(start, lines.size)) {
        val key = controlKey.find(line)?.groupValues?.get(1)?.uppercase()
        val value = if (key != null) line.substringAfter(':').trim() else line.trim()
        when (key) {
            "STATUS" -> {
                inChecklist = false
                val v = value.lowercase()
                status = when {
                    v.startsWith("done") || v.startsWith("complete") -> ControlStatus.DONE
                    v.startsWith("blocked") || v.startsWith("waiting") -> ControlStatus.BLOCKED
                    else -> ControlStatus.CONTINUE
                }
            }
            "AGREE" -> {
                inChecklist = false
                agree = value.lowercase().let { it.startsWith("y") || it.startsWith("true") }
            }
            "PROGRESS" -> {
                inChecklist = false
                progress = Regex("""\d{1,3}""").find(value)?.value?.toIntOrNull()?.coerceIn(0, 100)
            }
            "CHECKLIST" -> {
                inChecklist = true
                checkLine.find(value)?.let { checklist.add(ChecklistItem(it.groupValues[2].trim(), it.groupValues[1] != " ")) }
            }
            "ASSIGN" -> {
                inChecklist = false
                val name = value.substringBefore(':').substringBefore('—').substringBefore(" - ").trim()
                val job = value.removePrefix(name).trimStart(':', '—', '-', ' ').trim()
                if (name.isNotEmpty()) assignments[name] = job
            }
            else -> if (inChecklist) {
                checkLine.find(value)?.let { checklist.add(ChecklistItem(it.groupValues[2].trim(), it.groupValues[1] != " ")) }
            }
        }
    }
    return ParsedReply(display.ifEmpty { raw.trim().lines().first().take(200) }, AgentControl(status, progress, checklist, assignments, agree))
}

fun encodeChecklist(items: List<ChecklistItem>): String =
    items.joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.text.replace('\n', ' ') }

fun decodeChecklist(raw: String): List<ChecklistItem> =
    raw.lines().mapNotNull { checkLine.find(it)?.let { m -> ChecklistItem(m.groupValues[2].trim(), m.groupValues[1] != " ") } }

/** Progress shown in the header: an explicit PROGRESS wins, else the checklist ratio. */
fun taskforceProgress(explicit: Int?, checklist: List<ChecklistItem>, previous: Int): Int = when {
    explicit != null -> explicit
    checklist.isNotEmpty() -> checklist.count { it.done } * 100 / checklist.size
    else -> previous
}

/** One line of the shared thread for prompt rendering. */
data class ThreadLine(
    val speaker: String,
    val text: String,
    val quoteSpeaker: String? = null,
    val quoteText: String? = null,
    val fromUser: Boolean = false,
)

/**
 * Shared transcript every member sees, newest last, with speaker names and
 * quote-replies. Older lines are dropped first to fit [maxChars].
 */
fun renderThread(lines: List<ThreadLine>, maxChars: Int = 14_000): String {
    val rendered = lines.map { l ->
        buildString {
            append("[").append(if (l.fromUser) "${l.speaker} (the boss/user)" else l.speaker).append("]: ")
            if (l.quoteText != null) {
                append("(replying to ").append(l.quoteSpeaker ?: "someone").append(": \"")
                append(l.quoteText.replace('\n', ' ').take(240)).append("\") ")
            }
            append(l.text.trim())
        }
    }
    val kept = ArrayDeque<String>()
    var total = 0
    for (r in rendered.asReversed()) {
        if (total + r.length > maxChars && kept.isNotEmpty()) break
        kept.addFirst(r)
        total += r.length + 1
    }
    if (kept.size < rendered.size) kept.addFirst("… (${rendered.size - kept.size} earlier messages omitted)")
    return kept.joinToString("\n\n")
}

enum class TaskforcePhase { PLAN, WORK, REVIEW, DIRECT, DEBATE, CONCLUDE, TALK }

/** Extra system prompt for a taskforce turn. */
fun taskforceRolePrompt(
    me: String,
    lead: String,
    members: List<String>,
    title: String,
    objective: String,
    phase: TaskforcePhase,
    assignment: String?,
): String = buildString {
    appendLine()
    appendLine("## Taskforce meeting")
    appendLine("You are in a team meeting (taskforce) in the office conference room, in a shared group chat.")
    appendLine("Meeting: $title")
    appendLine("Objective: ${objective.ifBlank { "(see thread)" }}")
    appendLine("Team: ${members.joinToString()}. Lead: $lead.")
    appendLine("Messages are labelled [Name]. Messages from the user (your boss) come first in priority: answer their questions and follow their corrections, especially anything they quote-reply to.")
    appendLine("Use your tools when they help. Be concise and concrete; real work output (drafts, code, findings) beats talk.")
    appendLine()
    when (phase) {
        TaskforcePhase.PLAN -> {
            appendLine("You are the LEAD. Plan the next step: review the thread, then split the work.")
            appendLine("OPEN the conversation: 1–2 short sentences framing the goal, then give EVERY teammate a DISTINCT angle (no overlap), one short line each as `@Name: angle`, and repeat them in the footer as `ASSIGN: Name: angle`. No headings, no essays.")
            appendLine("If the objective is already met, say so with STATUS: done. If you need the user's decision or info, ask them and use STATUS: blocked.")
        }
        TaskforcePhase.WORK -> {
            appendLine("Do your part now and post the result for the team.")
            if (!assignment.isNullOrBlank()) appendLine("Your assignment from $lead: $assignment")
            appendLine("Briefly review teammates' latest work if relevant. Use STATUS: continue (the lead decides when the team is done), or STATUS: blocked if you truly need the user.")
        }
        TaskforcePhase.DIRECT -> {
            appendLine("The user (or a teammate) @mentioned you directly. Reply to that message yourself, using the full meeting context.")
            if (!assignment.isNullOrBlank()) appendLine("Message to you: $assignment")
            appendLine("You may @Name a teammate if they should answer next. Use STATUS: continue.")
        }
        TaskforcePhase.TALK -> {
            appendLine("This is a live team CONVERSATION, like a quick chat. ${assignment.orEmpty()}")
            appendLine(SHORT_TURN_RULES)
            appendLine("React to what was just said, address teammates by name (@Name), build on or disagree with them, and keep to your own angle.")
            appendLine("In the footer add `AGREE: yes` once you think the team has converged, otherwise `AGREE: no`.")
        }
        TaskforcePhase.DEBATE -> {
            appendLine(SHORT_TURN_RULES)
            appendLine("This is a DEBATE. ${assignment.orEmpty()}")
            appendLine("State your position in a few sentences, rebut teammates by name (@Name), and concede points when convinced.")
            appendLine("In the footer add `AGREE: yes` if you now agree with the emerging consensus, otherwise `AGREE: no`.")
        }
        TaskforcePhase.CONCLUDE -> {
            appendLine("You are the LEAD. The debate is over. ${assignment.orEmpty()}")
            appendLine("Start with the line **Conclusion** then, briefly (under ~120 words): Decision, Key points (incl. any dissent), Next actions.")
            appendLine("Update CHECKLIST and PROGRESS. STATUS: done only if the objective is fully met, otherwise STATUS: continue (the user reviews next).")
        }
        TaskforcePhase.REVIEW -> {
            appendLine("You are the LEAD. Review what the team just posted, give feedback, and update the checklist.")
            appendLine("STATUS: done only when the objective is fully met and summarised for the user. STATUS: blocked if waiting for the user. Otherwise STATUS: continue.")
        }
    }
    appendLine()
    appendLine("End EVERY message with this footer (it is hidden from the chat):")
    appendLine("---")
    appendLine("STATUS: continue | done | blocked")
    appendLine("PROGRESS: <0-100>")
    if (phase == TaskforcePhase.DEBATE) appendLine("AGREE: yes | no")
    if (phase == TaskforcePhase.PLAN || phase == TaskforcePhase.REVIEW || phase == TaskforcePhase.CONCLUDE) {
        appendLine("CHECKLIST:")
        appendLine("[x] finished step")
        appendLine("[ ] open step")
    }
    if (phase == TaskforcePhase.PLAN) appendLine("ASSIGN: Name: sub-task")
    append("You are $me.")
}

/** Prompt rules for chat-like short turns. */
const val SHORT_TURN_RULES = "Keep it SHORT: 1–3 sentences, under 60 words, casual chat tone. No headings, no bullet lists, no long code unless the user asked."
