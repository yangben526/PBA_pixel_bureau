package app.pixelbureau.data

import app.pixelbureau.logic.DefaultOutputStyle
import app.pixelbureau.logic.instructionEditCostsStar
import app.pixelbureau.logic.OfficeMap
import app.pixelbureau.logic.SeatInput
import app.pixelbureau.logic.StarScore
import app.pixelbureau.logic.grantChat
import app.pixelbureau.logic.grantTaskStar
import app.pixelbureau.logic.grantTaskforceSignOff
import app.pixelbureau.logic.TaskforceSignOffStars
import app.pixelbureau.logic.seatAgents
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BureauRepository(private val db: BureauDatabase) {
    private val bureau = db.bureau()
    private val agents = db.agents()
    private val tasks = db.tasks()
    private val messages = db.messages()

    fun observeBureau(): Flow<Bureau?> = bureau.observe()
    fun observeAgents(): Flow<List<Agent>> = agents.observe()
    fun observeTasks(): Flow<List<Task>> = tasks.observe()
    fun observeMessages(agentId: String): Flow<List<Message>> = messages.observe(agentId)

    /** Seeds a brand-new bureau. Returns true only on that first run. */
    suspend fun seedIfEmpty(): Boolean {
        if (bureau.get() != null) {
            return false
        }
        bureau.upsert(Bureau(name = "Pixel Bureau", headcount = 3, reputation = 0, day = 1))
        agents.insert(starter("agent-pixel", "Pixel", "pixel", 0, PIXEL_VOICE, DefaultOutputStyle))
        agents.insert(starter("agent-kai", "Kai", "kai", 1, KAI_VOICE, DefaultOutputStyle))
        agents.insert(starter("agent-byte", "Byte", "byte", 2, BYTE_VOICE, DefaultOutputStyle))
        reseat(3)
        return true
    }

    /**
     * The original starter duty lines become [DefaultOutputStyle].
     * A style the user already wrote stays.
     */
    suspend fun giveDefaultOutputStyle() {
        val legacy = setOf(LEGACY_PIXEL_DUTIES, LEGACY_KAI_DUTIES, LEGACY_BYTE_DUTIES)
        for (row in agents.all()) {
            if (row.duties.trim() in legacy) {
                agents.update(row.copy(duties = DefaultOutputStyle))
            }
        }
    }

    /**
     * Spend 1 reputation star to open one hiring slot (raises headcount).
     * Returns an error message, or null on success.
     */
    suspend fun openHiringSlot(): String? {
        val current = bureau.get() ?: return "The bureau is not ready yet."
        if (current.headcount >= OfficeMap.deskCount) {
            return "Every desk is already open."
        }
        if (current.reputation < 1) {
            return "Need a ★ to open a hiring slot."
        }
        val cap = current.headcount + 1
        bureau.upsert(current.copy(headcount = cap, reputation = current.reputation - 1))
        reseat(cap)
        return null
    }

    /**
     * Spend [cost] reputation stars on a shop purchase.
     * Returns an error message, or null on success.
     */
    suspend fun refundStars(n: Int) {
        if (n <= 0) return
        val current = bureau.get() ?: return
        bureau.upsert(current.copy(reputation = current.reputation + n))
    }

    suspend fun spendStars(cost: Int): String? {
        if (cost <= 0) return null
        val current = bureau.get() ?: return "The bureau is not ready yet."
        if (current.reputation < cost) {
            return "Not enough stars · need ★$cost"
        }
        bureau.upsert(current.copy(reputation = current.reputation - cost))
        return null
    }

    /** Drop an unused paid slot. Does not refund the star. */
    suspend fun closeEmptySlot(): String? {
        val current = bureau.get() ?: return "The bureau is not ready yet."
        val active = agents.all().count { !it.archived }
        if (current.headcount <= active) {
            return "Fire someone before closing a desk."
        }
        if (current.headcount <= 1) {
            return "Keep at least one desk."
        }
        val cap = current.headcount - 1
        bureau.upsert(current.copy(headcount = cap))
        reseat(cap)
        return null
    }

    /** Legacy free adjust — prefer [openHiringSlot] / [closeEmptySlot]. */
    suspend fun setHeadcount(headcount: Int) {
        val current = bureau.get() ?: return
        val active = agents.all().count { !it.archived }
        val cap = headcount.coerceIn(active.coerceAtLeast(1), OfficeMap.deskCount)
        if (cap > current.headcount) {
            // Paid path only — refuse free raises.
            return
        }
        bureau.upsert(current.copy(headcount = cap))
        reseat(cap)
    }

    suspend fun createAgent(name: String, personality: String, duties: String, spriteKey: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return "Give them a name."
        }
        val current = bureau.get() ?: return "The bureau is not ready yet."
        val all = agents.all()
        if (all.count { !it.archived } >= current.headcount) {
            return "Open a hiring slot with a ★ in PixelHire first."
        }
        val order = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1
        agents.insert(
            Agent(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                personality = personality.trim(),
                duties = duties.trim(),
                spriteKey = spriteKey,
                deskIndex = null,
                status = "idle",
                archived = false,
                sortOrder = order,
            ),
        )
        reseat(current.headcount)
        return null
    }

    suspend fun updateAgent(id: String, name: String, personality: String, duties: String, spriteKey: String): String? {
        val row = agents.get(id) ?: return "That person is no longer here."
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return "Give them a name."
        }
        val nextInstructions = personality.trim()
        val nextPersonality = duties.trim()
        if (instructionEditCostsStar(row.personality, row.duties, nextInstructions, nextPersonality)) {
            val current = bureau.get() ?: return "The bureau is not ready yet."
            if (current.reputation < 1) {
                return "Need a ★ to change instructions or output personality."
            }
            bureau.upsert(current.copy(reputation = current.reputation - 1))
        }
        agents.update(
            row.copy(
                name = trimmed,
                personality = nextInstructions,
                duties = nextPersonality,
                spriteKey = spriteKey,
            ),
        )
        return null
    }

    /**
     * Fire removes the agent and frees their paid slot for a new hire.
     * Headcount (paid slots) stays the same.
     */
    suspend fun fire(id: String): String? {
        val current = bureau.get() ?: return null
        val row = agents.get(id) ?: return null
        if (row.archived) {
            return null
        }
        if (agents.all().count { !it.archived } <= 1) {
            return "Someone has to keep the lights on."
        }
        messages.deleteForAgent(id)
        tasks.deleteForAgent(id)
        agents.delete(id)
        reseat(current.headcount)
        return null
    }

    @Deprecated("Use fire — benching is gone.")
    suspend fun bench(id: String): String? = fire(id)

    suspend fun activeIds(): Set<String> = agents.all().filter { !it.archived }.map { it.id }.toSet()

    /** A killed process leaves agents mid-walk. They are not still working. */
    suspend fun releaseStuckAgents() {
        val stuck = setOf("walking", "thinking", "writing", "waiting")
        for (row in agents.all()) {
            if (row.status in stuck) {
                agents.update(row.copy(status = "idle"))
            }
        }
    }

    suspend fun agent(id: String): Agent? = agents.get(id)

    suspend fun messages(agentId: String): List<Message> = messages.all(agentId)

    suspend fun task(id: String): Task? = tasks.get(id)

    suspend fun addTask(agentId: String, title: String, brief: String): String {
        val id = UUID.randomUUID().toString()
        tasks.insert(
            Task(
                id = id,
                agentId = agentId,
                title = title.trim(),
                brief = brief.trim(),
                status = "queued",
                createdAt = stamp(),
            ),
        )
        return id
    }

    suspend fun setTaskStatus(id: String, status: String) {
        val row = tasks.get(id) ?: return
        tasks.update(row.copy(status = status))
    }

    /** Put a failed task back in the queue. Same id, so it stays one job. */
    suspend fun requeueTask(id: String, agentId: String): Boolean {
        val row = tasks.get(id) ?: return false
        if (row.status != "failed") {
            return false
        }
        tasks.update(row.copy(status = "queued", agentId = agentId))
        return true
    }

    suspend fun completeTask(id: String) {
        val row = tasks.get(id) ?: return
        if (row.status == "done") {
            return
        }
        tasks.update(row.copy(status = "done"))
        val current = bureau.get() ?: return
        writeScore(current, grantTaskStar(current.toScore()))
    }

    /** +[TaskforceSignOffStars] for a completed taskforce sign-off (caller guards once-per-id). */
    suspend fun awardTaskforceSignOff(steps: Int) {
        val current = bureau.get() ?: return
        writeScore(current, grantTaskforceSignOff(current.toScore(), alreadyAwarded = false, steps = steps))
    }

    /** One successful office chat. Twelve of these add a star. A task reply does not call this. */
    suspend fun noteChat() {
        val current = bureau.get() ?: return
        writeScore(current, grantChat(current.toScore()))
    }

    private suspend fun writeScore(current: Bureau, score: StarScore) {
        bureau.upsert(
            current.copy(
                reputation = score.reputation,
                day = score.day,
                chatCredit = score.chatCredit,
            ),
        )
    }

    /** Removes the task row. For unfinished work, also drops the assignment message. */
    suspend fun deleteTask(id: String): Task? {
        val row = tasks.get(id) ?: return null
        if (row.status == "queued" || row.status == "working") {
            messages.deleteForTask(id)
        }
        tasks.delete(id)
        return row
    }

    suspend fun setStatus(agentId: String, status: String) {
        val row = agents.get(agentId) ?: return
        if (row.status == status) {
            return
        }
        agents.update(row.copy(status = status))
    }

    suspend fun addMessage(agentId: String, role: String, text: String, taskId: String?): String {
        val id = UUID.randomUUID().toString()
        messages.insert(
            Message(
                id = id,
                agentId = agentId,
                role = role,
                text = text,
                taskId = taskId,
                createdAt = stamp(),
            ),
        )
        return id
    }

    suspend fun updateMessage(id: String, text: String) {
        messages.updateText(id, text)
    }

    suspend fun deleteMessage(id: String) {
        messages.deleteById(id)
    }

    /** Drops the chat thread and stops unfinished work for this person. */
    suspend fun clearSession(agentId: String) {
        messages.deleteForAgent(agentId)
        tasks.failOpenForAgent(agentId)
        setStatus(agentId, "idle")
    }

    // --- Taskforces ---
    private val taskforces get() = db.taskforces()

    fun observeTaskforces(): Flow<List<Taskforce>> = taskforces.observe()
    fun observeTaskforceMembers(): Flow<List<TaskforceMember>> = taskforces.observeMembers()
    fun observeTaskforceMessages(id: String): Flow<List<TaskforceMessage>> = taskforces.observeMessages(id)
    suspend fun taskforce(id: String): Taskforce? = taskforces.get(id)
    suspend fun allTaskforces(): List<Taskforce> = taskforces.all()
    suspend fun taskforceMembers(id: String): List<String> = taskforces.members(id).map { it.agentId }
    suspend fun taskforceMessages(id: String): List<TaskforceMessage> = taskforces.messages(id)

    suspend fun createTaskforce(title: String, objective: String, memberIds: List<String>, status: String, steps: Int = 3): String {
        val id = UUID.randomUUID().toString()
        val now = stamp()
        taskforces.upsert(
            Taskforce(
                id = id, title = title, objective = objective, status = status,
                leadId = memberIds.first(), progress = 0, checklist = "", roundsThisRun = 0,
                note = "", createdAt = now, updatedAt = now, steps = steps.coerceIn(1, 6),
            ),
        )
        taskforces.insertMembers(memberIds.mapIndexed { i, a -> TaskforceMember(id, a, i) })
        return id
    }

    suspend fun saveTaskforce(row: Taskforce) {
        taskforces.upsert(row.copy(updatedAt = stamp()))
    }

    suspend fun addTaskforceMessage(
        taskforceId: String,
        speakerId: String,
        speakerName: String,
        text: String,
        quote: TaskforceMessage? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        taskforces.insertMessage(
            TaskforceMessage(
                id = id, taskforceId = taskforceId, speakerId = speakerId, speakerName = speakerName,
                text = text, quoteId = quote?.id, quoteSpeaker = quote?.speakerName,
                quoteText = quote?.text?.take(400), createdAt = stamp(),
            ),
        )
        return id
    }

    suspend fun updateTaskforceMessage(id: String, text: String) = taskforces.updateMessageText(id, text)
    suspend fun deleteTaskforceMessage(id: String) = taskforces.deleteMessage(id)

    private val stampLock = Any()
    private var lastStamp = 0L

    private fun stamp(): Long = synchronized(stampLock) {
        val now = System.currentTimeMillis()
        lastStamp = if (now > lastStamp) now else lastStamp + 1
        lastStamp
    }

    private suspend fun reseat(headcount: Int) {
        val all = agents.all()
        val seats = seatAgents(all.map { SeatInput(it.id, it.archived, it.sortOrder) }, headcount)
        for (seat in seats) {
            val row = all.first { it.id == seat.id }
            val status = if (seat.archived) "idle" else row.status
            if (row.archived != seat.archived || row.deskIndex != seat.deskIndex || row.status != status) {
                agents.update(row.copy(archived = seat.archived, deskIndex = seat.deskIndex, status = status))
            }
        }
    }

    private fun Bureau.toScore(): StarScore = StarScore(reputation, day, chatCredit)

    private fun starter(id: String, name: String, sprite: String, order: Int, voice: String, duties: String): Agent {
        return Agent(
            id = id,
            name = name,
            personality = voice,
            duties = duties,
            spriteKey = sprite,
            deskIndex = null,
            status = "idle",
            archived = false,
            sortOrder = order,
        )
    }

    private companion object {
        const val PIXEL_VOICE =
            "You write the first version of things. You are upbeat, concrete, and you would rather ship a small clear answer than a vague essay."
        const val KAI_VOICE =
            "You review work before it leaves the building. You are calm, specific, and you point out risks without being harsh."
        const val BYTE_VOICE =
            "You research. You are curious, you separate what you know from what you are guessing, and you keep notes short enough to read on a phone."
        const val LEGACY_PIXEL_DUTIES =
            "Turn the request into something the user can use\nSay what you did in a few sentences"
        const val LEGACY_KAI_DUTIES =
            "Check the request for gaps\nName the risk and a fix"
        const val LEGACY_BYTE_DUTIES =
            "Answer from what you know\nMark guesses as guesses"
    }
}
