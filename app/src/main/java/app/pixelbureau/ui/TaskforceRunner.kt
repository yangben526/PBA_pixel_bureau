package app.pixelbureau.ui

import app.pixelbureau.data.Agent
import app.pixelbureau.data.BureauRepository
import app.pixelbureau.data.LlmSettings
import app.pixelbureau.data.Taskforce
import app.pixelbureau.data.TaskforceMessage
import app.pixelbureau.logic.AgentControl
import app.pixelbureau.logic.AgentTool
import app.pixelbureau.logic.Conversation
import app.pixelbureau.logic.Debate
import app.pixelbureau.logic.Slash
import app.pixelbureau.logic.Mentions
import app.pixelbureau.logic.TaskforcePhase
import app.pixelbureau.logic.TaskforceRules
import app.pixelbureau.logic.TaskforceStatus
import app.pixelbureau.logic.ThreadLine
import app.pixelbureau.logic.TurnMessage
import app.pixelbureau.logic.buildSystemPrompt
import app.pixelbureau.logic.decodeChecklist
import app.pixelbureau.logic.encodeChecklist
import app.pixelbureau.logic.parseAgentReply
import app.pixelbureau.logic.renderThread
import app.pixelbureau.logic.stripThinkTags
import app.pixelbureau.logic.taskforceProgress
import app.pixelbureau.logic.taskforceRolePrompt
import app.pixelbureau.net.ChatClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drives taskforce group chats. One coroutine per live taskforce, only while
 * its status is ACTIVE. Each round:
 *   1. PLAN   — the lead (first member) reads the thread and ASSIGNs work.
 *   2. WORK   — assignees answer in parallel, each seeing the shared thread.
 *   3. REVIEW — the lead reviews, updates the checklist / progress and says
 *               continue | done | blocked.
 * Stops on done (→ complete, awaiting sign-off), blocked (→ waiting for user),
 * the round cap (→ paused) or a model error (→ blocked). Every transition is
 * written to Room, so an ACTIVE team resumes from the next PLAN after restart.
 */
internal class TaskforceRunner(
    private val scope: CoroutineScope,
    private val repository: BureauRepository,
    private val chat: ChatClient,
    private val settingsFor: (String) -> LlmSettings,
    private val toolsFor: suspend (String) -> List<AgentTool>,
    private val onSpeaking: (taskforceId: String, agentId: String, speaking: Boolean) -> Unit,
) {
    private val jobs = HashMap<String, Job>()
    private val lock = Mutex()
    private val decisions = HashMap<Pair<String, String>, CompletableDeferred<TurnDecision>>()
    private val pendingDirect = HashMap<String, Pair<List<String>, String>>()
    private val pendingSlash = HashMap<String, Slash.Parsed>()
    private val pendingDebate = HashMap<String, Pair<Debate.Command, List<String>>>()
    private val debateStop = HashSet<String>()
    private val _debates = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())

    /** taskforceId → (round, rounds) while a debate runs. */
    val debates: StateFlow<Map<String, Pair<Int, Int>>> = _debates

    /** Stop a running debate after the current speaker; the lead still concludes. */
    fun stopDebate(id: String) {
        if (id in _debates.value) debateStop.add(id)
    }

    private val _failures = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    /** taskforceId → (agentId → reason) for turns waiting on Retry / Skip. */
    val failures: StateFlow<Map<String, Map<String, String>>> = _failures

    /** User tapped Retry or Skip on an agent's model-error card. */
    fun resolveFailure(taskforceId: String, agentId: String, retry: Boolean) {
        decisions.remove(taskforceId to agentId)?.complete(if (retry) TurnDecision.RETRY else TurnDecision.SKIP)
    }

    private fun setFailure(tfId: String, agentId: String, reason: String?) {
        val cur = _failures.value[tfId].orEmpty()
        val next = if (reason == null) cur - agentId else cur + (agentId to reason)
        _failures.value = if (next.isEmpty()) _failures.value - tfId else _failures.value + (tfId to next)
    }

    /** Post the error card and wait for the user's Retry / Skip. */
    private suspend fun askOnFailure(tf: Taskforce, me: Agent, reason: String): TurnDecision {
        val settings = settingsFor(me.id)
        val target = settings.model.ifBlank { settings.providerId }.ifBlank { "the model" }
        val cardId = repository.addTaskforceMessage(tf.id, "$ModelErrorPrefix${me.id}", me.name, "${me.name} couldn't reach $target · ${reason.take(120)}")
        val wait = CompletableDeferred<TurnDecision>()
        decisions[tf.id to me.id] = wait
        setFailure(tf.id, me.id, reason)
        try {
            return wait.await()
        } finally {
            decisions.remove(tf.id to me.id)
            setFailure(tf.id, me.id, null)
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { repository.deleteTaskforceMessage(cardId) }
        }
    }

    private suspend fun turnWithRetry(
        tf: Taskforce,
        me: Agent,
        members: List<Agent>,
        phase: TaskforcePhase,
        assignment: String?,
    ): AgentControl? = retryableTurn(
        run = { turn(tf, me, members, phase, assignment) },
        onFailure = { why -> askOnFailure(tf, me, why) },
    )

    fun isRunning(id: String): Boolean = jobs[id]?.isActive == true

    /** Resume every ACTIVE team (app start). */
    fun resumeAll() {
        scope.launch {
            for (tf in repository.allTaskforces()) {
                if (TaskforceRules.shouldRun(tf.status)) kick(tf.id)
            }
        }
    }

    fun kick(id: String) {
        if (jobs[id]?.isActive == true) return
        jobs[id] = scope.launch {
            try {
                loop(id)
            } finally {
                jobs.remove(id)
            }
        }
    }

    fun stop(id: String) {
        jobs.remove(id)?.cancel()
        decisions.keys.filter { it.first == id }.forEach { decisions.remove(it)?.cancel() }
        _failures.value = _failures.value - id
    }

    /** User spoke: re-open the team, reset the round budget, wake the loop. */
    suspend fun userSpoke(id: String) {
        val tfNow = repository.taskforce(id)
        val last = repository.taskforceMessages(id).lastOrNull { it.speakerId == "user" }
        if (tfNow != null && last != null && id !in _debates.value) {
            val members = membersOf(tfNow).map { it.id to it.name }
            val mentioned = Mentions.ids(last.text, members)
            val slash = Slash.parse(last.text)
            val debate = Debate.parse(last.text)
            when {
                slash?.kind == Slash.Kind.DISCUSS -> pendingSlash[id] = slash
                slash?.kind == Slash.Kind.CONCLUDE || slash?.kind == Slash.Kind.STATUS -> pendingSlash[id] = slash
                debate != null -> pendingDebate[id] = debate to mentioned.ifEmpty { members.map { it.first } }
                mentioned.isNotEmpty() -> pendingDirect[id] = mentioned to last.text
            }
        }
        lock.withLock {
            val tf = repository.taskforce(id) ?: return
            val next = TaskforceRules.onUserMessage(tf.status)
            repository.saveTaskforce(tf.copy(status = next, roundsThisRun = 0, note = ""))
        }
        kick(id)
    }

    private suspend fun update(id: String, change: (Taskforce) -> Taskforce): Taskforce? = lock.withLock {
        val tf = repository.taskforce(id) ?: return null
        val next = change(tf)
        repository.saveTaskforce(next)
        next
    }

    private suspend fun loop(id: String) {
        while (true) {
            val tf = repository.taskforce(id) ?: return
            if (!TaskforceRules.shouldRun(tf.status)) return
            val members = membersOf(tf)
            val lead = members.firstOrNull { it.id == tf.leadId } ?: members.firstOrNull() ?: run {
                update(id) { it.copy(status = TaskforceStatus.BLOCKED, note = "No team members left.") }
                return
            }
            pendingSlash.remove(id)?.let { cmd ->
                when (cmd.kind) {
                    Slash.Kind.DISCUSS -> {
                        val voices = members.filter { it.id != lead.id }.ifEmpty { members }
                        val topic = cmd.arg.ifBlank { tf.objective.ifBlank { tf.title } }
                        repository.addTaskforceMessage(id, "system", "Taskforce", "Discussion: $topic")
                        conversation(tf, lead, members, voices, topic, Conversation.turnBudget(voices.size, tf.steps))
                        if (stillActive(id)) turnWithRetry(tf, lead, members, TaskforcePhase.CONCLUDE, "Topic: $topic.")?.let { applyControl(id, it) }
                    }
                    Slash.Kind.CONCLUDE -> turnWithRetry(tf, lead, members, TaskforcePhase.CONCLUDE, "The user asked you to conclude NOW from the discussion so far.")?.let { applyControl(id, it) }
                    else -> turnWithRetry(tf, lead, members, TaskforcePhase.DIRECT, "The user asked for /status: post a brief progress update (2–3 sentences: done, in progress, next).")
                }
                update(id) { if (it.status == TaskforceStatus.ACTIVE) it.copy(status = TaskforceStatus.PAUSED, note = "") else it }
                return
            }
            pendingDebate.remove(id)?.let { (cmd, who) ->
                runDebate(tf, lead, members, cmd, who)
                return
            }
            pendingDirect.remove(id)?.let { (who, text) ->
                runDirect(tf, members, who, text)
                update(id) { if (it.status == TaskforceStatus.ACTIVE) it.copy(status = TaskforceStatus.PAUSED, note = "") else it }
                return
            }
            // 1. PLAN
            val plan = turnWithRetry(tf, lead, members, TaskforcePhase.PLAN, null) ?: run {
                fail(id, "${lead.name} was skipped while planning.")
                return
            }
            applyControl(id, plan)
            TaskforceRules.afterPlan(plan)?.let { status ->
                update(id) { it.copy(status = status, roundsThisRun = it.roundsThisRun + 1) }
                return
            }
            if (!stillActive(id)) return
            // 2. CONVERSATION — short sequential turns; each keeps a distinct angle.
            val pairs = members.map { it.id to it.name }
            val names = members.associate { it.id to it.name }
            val planText = lastReply(id, lead.id)
            val assignments = TaskforceRules.distinctAssignments(plan, planText, pairs, lead.id)
            val workers = assignments.keys.mapNotNull { wid -> members.firstOrNull { it.id == wid } }
            val phases = TaskforceRules.roundPhases(workers.size)
            if (TaskforcePhase.WORK in phases) {
                conversation(tf, lead, members, workers, tf.objective.ifBlank { tf.title }, Conversation.turnBudget(workers.size, tf.steps), assignments, names)
                if (!stillActive(id)) return
            }
            val review = if (TaskforcePhase.CONCLUDE in phases) {
                turnWithRetry(tf, lead, members, TaskforcePhase.CONCLUDE, "Topic: ${tf.objective.ifBlank { tf.title }}.") ?: plan
            } else {
                plan
            }
            if (review !== plan) applyControl(id, review)
            update(id) { cur ->
                if (cur.status != TaskforceStatus.ACTIVE) return@update cur
                val rounds = cur.roundsThisRun + 1
                val st = TaskforceRules.afterReview(review, rounds)
                // Conclusion posted: wait for the user instead of looping again.
                val status = if (st == TaskforceStatus.ACTIVE) TaskforceStatus.PAUSED else st
                cur.copy(status = status, roundsThisRun = rounds, note = if (status == TaskforceStatus.PAUSED) "Lead posted the conclusion. Reply to continue." else "")
            } ?: return
            return
        }
    }

    private suspend fun lastReply(tfId: String, agentId: String): String =
        repository.taskforceMessages(tfId).lastOrNull { it.speakerId == agentId }?.text.orEmpty()

    /** Only the @mentioned agents reply, in order; anyone they @mention goes next. */
    private suspend fun runDirect(tf: Taskforce, members: List<Agent>, who: List<String>, text: String, maxTurns: Int = 6) {
        val pairs = members.map { it.id to it.name }
        val queue = Mentions.Queue(who, maxTurns)
        while (true) {
            val next = queue.next() ?: break
            val agent = members.firstOrNull { it.id == next } ?: continue
            turnWithRetry(tf, agent, members, TaskforcePhase.DIRECT, text) ?: continue
            queue.replied(agent.id, Mentions.ids(lastReply(tf.id, agent.id), pairs))
            if (!stillActive(tf.id)) return
        }
    }

    /** Turn-by-turn debate, bounded; ends early when everyone AGREEs. Lead concludes. */
    private suspend fun runDebate(tf: Taskforce, lead: Agent, members: List<Agent>, cmd: Debate.Command, who: List<String>) {
        val voices = who.mapNotNull { id -> members.firstOrNull { it.id == id } }.ifEmpty { members }
        val topic = cmd.topic.ifBlank { tf.objective.ifBlank { tf.title } }
        val budget = (cmd.rounds * voices.size).coerceIn(2, 10)
        repository.addTaskforceMessage(tf.id, "system", "Taskforce", "Debate: $topic · ${voices.joinToString { it.name }} · up to $budget turns")
        conversation(tf, lead, members, voices, topic, budget, phase = TaskforcePhase.DEBATE)
        if (repository.taskforce(tf.id)?.status != TaskforceStatus.ACTIVE) return
        val conclusion = turnWithRetry(tf, lead, members, TaskforcePhase.CONCLUDE, "Topic: $topic.")
        if (conclusion != null) applyControl(tf.id, conclusion)
        update(tf.id) { if (it.status == TaskforceStatus.ACTIVE) it.copy(status = TaskforceStatus.BLOCKED, note = "Debate concluded — your call.") else it }
    }

    /**
     * Chat-like conversation: one short turn at a time (sequential, streamed),
     * next speaker = whoever was @mentioned, else round-robin. Ends on the turn
     * budget, when everyone AGREEs, on Stop, or on a status change. The lead
     * may answer when addressed.
     */
    private suspend fun conversation(
        tf: Taskforce,
        lead: Agent,
        members: List<Agent>,
        voices: List<Agent>,
        topic: String,
        maxTurns: Int,
        assignments: Map<String, String> = emptyMap(),
        names: Map<String, String> = members.associate { it.id to it.name },
        phase: TaskforcePhase = TaskforcePhase.TALK,
    ) {
        val pairs = members.map { it.id to it.name }
        val order = Conversation.Order(voices.map { it.id }, maxTurns, extra = setOf(lead.id))
        var mentioned = Mentions.ids(lastReply(tf.id, lead.id), pairs)
        try {
            while (true) {
                if (tf.id in debateStop || !stillActive(tf.id)) break
                val nextId = order.next(mentioned) ?: break
                _debates.value = _debates.value + (tf.id to (order.turns to maxTurns))
                val v = members.firstOrNull { it.id == nextId } ?: continue
                val brief = buildString {
                    append("Topic: ").append(topic).append(". Turn ").append(order.turns).append(" of ").append(maxTurns).append(". ")
                    if (assignments[v.id] != null) append("Your angle: ").append(TaskforceRules.workerBrief(v.id, assignments, names))
                    else if (v.id == lead.id) append("You are the lead; answer briefly when addressed.")
                }
                val ctl = turnWithRetry(tf, v, members, phase, brief)
                if (ctl == null) { mentioned = emptyList(); continue }
                order.spoke(v.id, ctl.agree)
                mentioned = Mentions.ids(lastReply(tf.id, v.id), pairs).filter { it != v.id }
            }
        } finally {
            _debates.value = _debates.value - tf.id
            debateStop.remove(tf.id)
        }
    }

    private suspend fun stillActive(id: String): Boolean =
        repository.taskforce(id)?.status == TaskforceStatus.ACTIVE

    private suspend fun membersOf(tf: Taskforce): List<Agent> =
        repository.taskforceMembers(tf.id).mapNotNull { repository.agent(it) }.filter { !it.archived }

    private suspend fun applyControl(id: String, control: AgentControl, checklist: Boolean = true) {
        update(id) { cur ->
            val items = if (checklist && control.checklist.isNotEmpty()) control.checklist else decodeChecklist(cur.checklist)
            cur.copy(
                checklist = encodeChecklist(items),
                progress = taskforceProgress(control.progress, if (checklist) control.checklist else emptyList(), cur.progress),
            )
        }
    }

    /**
     * One agent turn on the shared thread. Returns the parsed control footer,
     * or throws when the model failed ([turnWithRetry] asks the user).
     */
    private suspend fun turn(
        tf: Taskforce,
        me: Agent,
        members: List<Agent>,
        phase: TaskforcePhase,
        assignment: String?,
    ): AgentControl {
        val settings = settingsFor(me.id)
        if (settings.apiKey.isBlank() || settings.baseUrl.isBlank()) {
            throw IllegalStateException("no model set up in Settings")
        }
        val thread = repository.taskforceMessages(tf.id).filterNot { it.speakerId.startsWith(ModelErrorPrefix) }
        val transcript = renderThread(thread.map { it.toLine() })
        val lead = members.firstOrNull { it.id == tf.leadId }?.name ?: members.first().name
        val tools = try {
            toolsFor(me.id)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            emptyList()
        }
        val system = buildSystemPrompt(me.name, me.personality, me.duties, tf.title, tf.objective, enabledTools = tools) +
            taskforceRolePrompt(me.name, lead, members.map { it.name }, tf.title, tf.objective, phase, assignment)
        val ask = when (phase) {
            TaskforcePhase.PLAN -> "Your turn, ${me.name} (lead): plan and assign."
            TaskforcePhase.WORK -> "Your turn, ${me.name}: do your part."
            TaskforcePhase.REVIEW -> "Your turn, ${me.name} (lead): review and decide."
            TaskforcePhase.DIRECT -> "Your turn, ${me.name}: you were @mentioned. Reply."
            TaskforcePhase.DEBATE -> "Your turn, ${me.name}: debate."
            TaskforcePhase.TALK -> "Your turn, ${me.name}: reply in the conversation (short)."
            TaskforcePhase.CONCLUDE -> "Your turn, ${me.name} (lead): post the Conclusion."
        }
        val user = buildString {
            append("Group chat so far:\n\n")
            append(transcript.ifBlank { "(no messages yet — the meeting just started)" })
            append("\n\n")
            append(ask)
        }
        onSpeaking(tf.id, me.id, true)
        var messageId: String? = null
        val buffer = StringBuilder()
        var lastFlush = 0L
        val short = phase == TaskforcePhase.TALK || phase == TaskforcePhase.DEBATE || phase == TaskforcePhase.PLAN || phase == TaskforcePhase.DIRECT
        val shape: (String) -> String = { if (short && phase != TaskforcePhase.PLAN) Conversation.clip(it) else it }
        try {
            streamMaybeCapped(short, reset = { buffer.clear() }) { chat.stream(
                settings,
                system,
                listOf(TurnMessage("user", user, null)),
                tools = tools,
                deepWork = phase == TaskforcePhase.WORK,
            ) { delta ->
                buffer.append(delta)
                val shown = shape(parseAgentReply(stripThinkTags(buffer.toString())).display)
                val idNow = messageId
                if (idNow == null) {
                    messageId = repository.addTaskforceMessage(tf.id, me.id, me.name, shown)
                } else if (System.currentTimeMillis() - lastFlush > 250) {
                    repository.updateTaskforceMessage(idNow, shown)
                    lastFlush = System.currentTimeMillis()
                }
            } }
            val parsed = parseAgentReply(stripThinkTags(buffer.toString()))
            if (parsed.display.isBlank()) throw IllegalStateException("The model returned no text.")
            val text = shape(parsed.display)
            val idNow = messageId
            if (idNow == null) {
                repository.addTaskforceMessage(tf.id, me.id, me.name, text)
            } else {
                repository.updateTaskforceMessage(idNow, text)
            }
            return parsed.control
        } catch (c: CancellationException) {
            messageId?.let { if (buffer.isBlank()) repository.deleteTaskforceMessage(it) }
            throw c
        } catch (e: Exception) {
            messageId?.let { if (buffer.isBlank()) repository.deleteTaskforceMessage(it) }
            throw e
        } finally {
            onSpeaking(tf.id, me.id, false)
        }
    }

    private suspend fun fail(id: String, why: String) {
        repository.addTaskforceMessage(id, "system", "Taskforce", "$why Send a message to retry.")
        update(id) { it.copy(status = TaskforceStatus.BLOCKED, note = why) }
    }
}

internal fun TaskforceMessage.toLine(): ThreadLine = ThreadLine(
    speaker = speakerName,
    text = text,
    quoteSpeaker = quoteSpeaker,
    quoteText = quoteText,
    fromUser = speakerId == "user",
)


/**
 * Short turns ask for max_tokens [Conversation.MAX_TOKENS]; some reasoning models
 * reject or exhaust a tight cap, so one failed capped call retries uncapped
 * (the soft clip still keeps the turn short).
 */
private suspend fun streamMaybeCapped(short: Boolean, reset: () -> Unit, block: suspend () -> Unit) {
    if (!short) return block()
    try {
        kotlinx.coroutines.withContext(app.pixelbureau.net.ReplyTokenCap(Conversation.MAX_TOKENS)) { block() }
    } catch (c: CancellationException) {
        throw c
    } catch (_: Exception) {
        reset()
        block()
    }
}

internal const val ModelErrorPrefix = "modelerror:"

internal enum class TurnDecision { RETRY, SKIP }

/**
 * Run one agent turn; on failure ask [onFailure] (the error card) and either
 * re-run just this turn (RETRY) or give up on it (SKIP → null).
 */
internal suspend fun <T : Any> retryableTurn(run: suspend () -> T, onFailure: suspend (String) -> TurnDecision): T? {
    while (true) {
        try {
            return run()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            when (onFailure(e.message ?: "request failed")) {
                TurnDecision.RETRY -> continue
                TurnDecision.SKIP -> return null
            }
        }
    }
}
