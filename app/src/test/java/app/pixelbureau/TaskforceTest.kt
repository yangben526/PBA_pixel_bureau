package app.pixelbureau

import app.pixelbureau.logic.Slash
import org.junit.Assert.assertNotNull

import app.pixelbureau.logic.AgentControl
import app.pixelbureau.logic.TaskforcePhase
import app.pixelbureau.logic.Debate
import app.pixelbureau.logic.Mentions
import app.pixelbureau.logic.ChecklistItem
import app.pixelbureau.logic.ControlStatus
import app.pixelbureau.logic.OfficeMap
import app.pixelbureau.logic.OfficeSim
import app.pixelbureau.logic.SimAgent
import app.pixelbureau.logic.TaskforceRules
import app.pixelbureau.logic.TaskforceStatus
import app.pixelbureau.logic.ThreadLine
import app.pixelbureau.logic.decodeChecklist
import app.pixelbureau.logic.encodeChecklist
import app.pixelbureau.logic.parseAgentReply
import app.pixelbureau.logic.renderThread
import app.pixelbureau.logic.taskforceProgress
import app.pixelbureau.data.Taskforce
import app.pixelbureau.ui.TaskforceUi
import app.pixelbureau.ui.taskforceSeatMap
import app.pixelbureau.ui.meetingTaskforceFor
import app.pixelbureau.ui.retryableTurn
import app.pixelbureau.ui.TurnDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random

class TaskforceTest {
    private val S = TaskforceStatus

    // --- control footer parsing ---

    @Test
    fun parsesFooterAndHidesItFromTheThread() {
        val raw = """
            Plan: Kai drafts the copy, Byte checks numbers.

            ---
            STATUS: continue
            PROGRESS: 35
            CHECKLIST:
            [x] Gather requirements
            [ ] Draft copy
            - [ ] Review numbers
            ASSIGN: Kai: draft the landing copy
            ASSIGN: Byte — verify Q3 numbers
        """.trimIndent()
        val p = parseAgentReply(raw)
        assertEquals("Plan: Kai drafts the copy, Byte checks numbers.", p.display)
        assertEquals(ControlStatus.CONTINUE, p.control.status)
        assertEquals(35, p.control.progress)
        assertEquals(3, p.control.checklist.size)
        assertTrue(p.control.checklist[0].done)
        assertFalse(p.control.checklist[2].done)
        assertEquals("draft the landing copy", p.control.assignments["Kai"])
        assertEquals("verify Q3 numbers", p.control.assignments["Byte"])
    }

    @Test
    fun statusWordsMapToStops() {
        assertEquals(ControlStatus.DONE, parseAgentReply("ok\nSTATUS: done").control.status)
        assertEquals(ControlStatus.DONE, parseAgentReply("ok\nstatus: Complete!").control.status)
        assertEquals(ControlStatus.BLOCKED, parseAgentReply("which brand?\nSTATUS: blocked - need user").control.status)
        assertEquals(ControlStatus.BLOCKED, parseAgentReply("x\nSTATUS: waiting for user").control.status)
        // No footer → continue, full text kept.
        val plain = parseAgentReply("just talking")
        assertEquals(ControlStatus.CONTINUE, plain.control.status)
        assertEquals("just talking", plain.display)
        assertEquals(100, parseAgentReply("x\nPROGRESS: 250%").control.progress)
    }

    @Test
    fun checklistRoundTripsAndDrivesProgress() {
        val items = listOf(ChecklistItem("a", true), ChecklistItem("b", false), ChecklistItem("c", true), ChecklistItem("d", false))
        assertEquals(items, decodeChecklist(encodeChecklist(items)))
        assertEquals(50, taskforceProgress(null, items, 10))
        assertEquals(80, taskforceProgress(80, items, 10))
        assertEquals(10, taskforceProgress(null, emptyList(), 10))
    }

    // --- orchestration stop conditions ---

    @Test
    fun planCanStopTheRunImmediately() {
        assertEquals(S.COMPLETE, TaskforceRules.afterPlan(AgentControl(ControlStatus.DONE)))
        assertEquals(S.BLOCKED, TaskforceRules.afterPlan(AgentControl(ControlStatus.BLOCKED)))
        assertNull(TaskforceRules.afterPlan(AgentControl(ControlStatus.CONTINUE)))
    }

    @Test
    fun reviewStopsOnDoneBlockedOrRoundCap() {
        val go = AgentControl(ControlStatus.CONTINUE)
        assertEquals(S.ACTIVE, TaskforceRules.afterReview(go, 1))
        assertEquals(S.ACTIVE, TaskforceRules.afterReview(go, TaskforceRules.MAX_ROUNDS_PER_RUN - 1))
        assertEquals(S.PAUSED, TaskforceRules.afterReview(go, TaskforceRules.MAX_ROUNDS_PER_RUN))
        assertEquals(S.COMPLETE, TaskforceRules.afterReview(AgentControl(ControlStatus.DONE), 1))
        assertEquals(S.BLOCKED, TaskforceRules.afterReview(AgentControl(ControlStatus.BLOCKED), 1))
        // Done wins over the cap.
        assertEquals(S.COMPLETE, TaskforceRules.afterReview(AgentControl(ControlStatus.DONE), 99))
    }

    @Test
    fun simulatedLoopAlwaysTerminates() {
        // Agents that never say done must still stop at the round cap.
        var status = S.ACTIVE
        var rounds = 0
        var turns = 0
        while (TaskforceRules.shouldRun(status)) {
            turns += 1 // plan
            TaskforceRules.afterPlan(AgentControl())?.let { status = it }
            if (!TaskforceRules.shouldRun(status)) break
            turns += TaskforceRules.assignees(AgentControl(), listOf("l" to "Lead", "a" to "A", "b" to "B"), "l").size
            turns += 1 // review
            rounds += 1
            status = TaskforceRules.afterReview(AgentControl(), rounds)
            assertTrue("runaway loop", turns < 100)
        }
        assertEquals(S.PAUSED, status)
        assertEquals(TaskforceRules.MAX_ROUNDS_PER_RUN, rounds)
    }

    @Test
    fun assigneesFollowTheLeadAndAreCapped() {
        val members = listOf("l" to "Lead", "k" to "Kai", "b" to "Byte", "o" to "Ops", "p" to "Pixel", "m" to "Mun", "z" to "Zed")
        val named = AgentControl(assignments = mapOf("kai" to "x", "Ops" to "y", "Lead" to "self"))
        assertEquals(listOf("k", "o"), TaskforceRules.assignees(named, members, "l"))
        // Nobody named (or only unknown names) → every non-lead, capped.
        val all = TaskforceRules.assignees(AgentControl(assignments = mapOf("Ghost" to "?")), members, "l")
        assertEquals(TaskforceRules.MAX_MEMBER_TURNS_PER_ROUND, all.size)
        assertFalse("l" in all)
        // Solo team has no workers: the plan turn is the whole round.
        assertTrue(TaskforceRules.assignees(AgentControl(), listOf("l" to "Lead"), "l").isEmpty())
    }

    // --- taskforce state transitions ---

    @Test
    fun userMessagesReopenButNeverResurrect() {
        for (s in listOf(S.ACTIVE, S.BLOCKED, S.PAUSED, S.COMPLETE)) {
            assertEquals(S.ACTIVE, TaskforceRules.onUserMessage(s))
        }
        assertEquals(S.SIGNED_OFF, TaskforceRules.onUserMessage(S.SIGNED_OFF))
        assertEquals(S.DISBANDED, TaskforceRules.onUserMessage(S.DISBANDED))
    }

    @Test
    fun signOffOnlyFromCompleteAndDisbandFromAnyOpenState() {
        assertEquals(S.SIGNED_OFF, TaskforceRules.signOff(S.COMPLETE))
        for (s in listOf(S.ACTIVE, S.BLOCKED, S.PAUSED, S.SIGNED_OFF, S.DISBANDED)) {
            assertNull(s, TaskforceRules.signOff(s))
        }
        for (s in listOf(S.ACTIVE, S.BLOCKED, S.PAUSED, S.COMPLETE)) {
            assertEquals(S.DISBANDED, TaskforceRules.disband(s))
        }
        assertNull(TaskforceRules.disband(S.SIGNED_OFF))
        assertNull(TaskforceRules.disband(S.DISBANDED))
        assertTrue(TaskforceStatus.isOpen(S.COMPLETE))
        assertFalse(TaskforceStatus.isOpen(S.SIGNED_OFF))
    }

    // --- shared thread ---

    @Test
    fun threadShowsSpeakersUserAndQuotesAndTrimsOldest() {
        val lines = listOf(
            ThreadLine("Kai", "First draft ready."),
            ThreadLine("You", "Shorter please", quoteSpeaker = "Kai", quoteText = "First draft ready.", fromUser = true),
        )
        val out = renderThread(lines)
        assertTrue(out.contains("[Kai]: First draft ready."))
        assertTrue(out.contains("[You (the boss/user)]: (replying to Kai: \"First draft ready.\") Shorter please"))
        val many = (1..200).map { ThreadLine("A", "message number $it with some padding text") }
        val trimmed = renderThread(many, maxChars = 500)
        assertTrue(trimmed.contains("earlier messages omitted"))
        assertTrue(trimmed.contains("message number 200"))
        assertFalse(trimmed.contains("message number 1 "))
    }

    // --- room seats ---

    private fun tf(id: String, members: List<String>, status: String = S.ACTIVE) = TaskforceUi(
        Taskforce(id, id, "", status, members.first(), 0, "", 0, "", 0, 0), members,
    )

    @Test
    fun seatsAreUniqueAcrossTeamsAndCappedByChairs() {
        val seats = taskforceSeatMap(listOf(tf("a", listOf("1", "2", "3")), tf("b", listOf("3", "4", "5", "6", "7", "8"))))
        // 8 unique members clamp to 5: 4 chairs + 1 standing spot at the table top.
        assertEquals(4, OfficeMap.confSeats.size)
        assertEquals(5, app.pixelbureau.logic.TaskforceRules.MAX_MEMBERS)
        assertEquals(5, seats.size)
        assertEquals(seats.size, seats.values.toSet().size)
        assertEquals(OfficeMap.confSeats.toSet() + OfficeMap.confStandSpot, seats.values.toSet())
        assertEquals(OfficeMap.confStandSpot, seats["5"])
        assertFalse("6" in seats)
        assertTrue(seats.values.all { OfficeMap.inConf(it) })
        assertEquals(OfficeMap.confY0, OfficeMap.confStandSpot.y)
        assertTrue(OfficeMap.confSeats.none { it.y == OfficeMap.confY0 })
    }

    @Test
    fun membersWalkInSitAndLeaveWhenDisbanded() {
        val sim = OfficeSim(Random(3))
        val agents = listOf(
            SimAgent("a", "Pixel", "pixel", 0, "idle"),
            SimAgent("b", "Kai", "kai", 1, "idle"),
            SimAgent("c", "Byte", "byte", 2, "idle"),
        )
        val seats = taskforceSeatMap(listOf(tf("t", listOf("a", "b"))))
        sim.setTaskforceSeats(seats)
        repeat(600) { sim.tick(0.1f, agents) }
        val snap = sim.snapshot().actors.associateBy { it.id }
        for ((id, seat) in seats) {
            val a = snap.getValue(id)
            assertEquals("$id seated", seat, app.pixelbureau.logic.Cell(a.x.roundToInt(), a.y.roundToInt()))
            assertEquals("type", a.anim)
        }
        sim.setTaskforceSeats(emptyMap())
        repeat(400) { sim.tick(0.1f, agents) }
        for (id in seats.keys) {
            val a = sim.snapshot().actors.first { it.id == id }
            // Eventually not glued to the taskforce chair (may come back casually, but not stuck).
            assertTrue("$id left", a.anim != "sit" || app.pixelbureau.logic.Cell(a.x.roundToInt(), a.y.roundToInt()) != seats[id])
        }
    }

    // --- Room migration matches the entities ---

    @Test
    fun migration2to3MatchesRoomGeneratedSchema() {
        val impl = File("build/generated/ksp/debug/java/app/pixelbureau/data/BureauDatabase_Impl.java")
        assumeTrue("generated Room impl not found", impl.exists())
        val generated = impl.readText()
        for (sql in TASKFORCE_SCHEMA_SQL) {
            assertTrue("migration SQL differs from Room schema:\n$sql", generated.contains(sql.replace("\"", "\\\"")))
        }
    }
}

class TaskforceSignOffStarsTest {
    @org.junit.Test
    fun signOffAwardsThreeStarsOnce() {
        val s0 = app.pixelbureau.logic.StarScore(reputation = 5, day = 1)
        val s1 = app.pixelbureau.logic.grantTaskforceSignOff(s0, alreadyAwarded = false)
        org.junit.Assert.assertEquals(8, s1.reputation)
        org.junit.Assert.assertEquals(3, app.pixelbureau.logic.TaskforceSignOffStars)
        // Guarded: a second award for the same taskforce changes nothing.
        org.junit.Assert.assertEquals(s1, app.pixelbureau.logic.grantTaskforceSignOff(s1, alreadyAwarded = true))
        // Only COMPLETE can be signed off; disband never routes through sign-off.
        org.junit.Assert.assertNull(app.pixelbureau.logic.TaskforceRules.signOff(app.pixelbureau.logic.TaskforceStatus.DISBANDED))
        org.junit.Assert.assertNull(app.pixelbureau.logic.TaskforceRules.signOff(app.pixelbureau.logic.TaskforceStatus.SIGNED_OFF))
    }
}

class TaskforceRoomTest {
    @org.junit.Test
    fun workingMembersGoToRoomOverflowStandsAndAllReturnAfter() {
        val om = app.pixelbureau.logic.OfficeMap
        val ids = (0 until app.pixelbureau.logic.TaskforceRules.MAX_MEMBERS).map { "m$it" }
        val agents = ids.mapIndexed { i, id ->
            app.pixelbureau.logic.SimAgent(id, "A$i", "pixel", i % om.desks.size, if (i % 2 == 0) "working" else "idle")
        }
        val sim = app.pixelbureau.logic.OfficeSim(kotlin.random.Random(3))
        val seats = LinkedHashMap<String, app.pixelbureau.logic.Cell>()
        val spots = om.confSeats + om.confStandSpot
        ids.forEachIndexed { i, id -> seats[id] = spots[i] }
        sim.setTaskforceSeats(seats)
        var doorSwung = false
        repeat(1500) {
            sim.tick(0.1f, agents)
            if (sim.confDoorOpen() > 0.5f) doorSwung = true
        }
        org.junit.Assert.assertTrue("conference door animated", doorSwung)
        val snap = sim.snapshot().actors.associateBy { it.id }
        for ((id, cell) in seats) {
            val a = snap.getValue(id)
            org.junit.Assert.assertEquals("$id in room", cell,
                app.pixelbureau.logic.Cell(kotlin.math.round(a.x).toInt(), kotlin.math.round(a.y).toInt()))
            org.junit.Assert.assertTrue(om.inConf(cell))
            if (cell in om.confSeats) org.junit.Assert.assertEquals("$id typing", "type", a.anim)
            else org.junit.Assert.assertEquals("$id standing", "idle", a.anim)
        }
        // Sign-off / disband: working members return to their desk chairs.
        sim.setTaskforceSeats(emptyMap())
        repeat(1500) { sim.tick(0.1f, agents) }
        val after = sim.snapshot().actors.associateBy { it.id }
        agents.filter { it.status == "working" }.forEach { ag ->
            val a = after.getValue(ag.id)
            org.junit.Assert.assertEquals("${ag.id} back at desk", om.chair(ag.deskIndex!!),
                app.pixelbureau.logic.Cell(kotlin.math.round(a.x).toInt(), kotlin.math.round(a.y).toInt()))
        }
    }

    @Test
    fun agentsInAnActiveMeetingAreBusyUntilSignOffOrDisband() {
        val S = TaskforceStatus
        fun tf(id: String, members: List<String>, status: String = S.ACTIVE) = TaskforceUi(
            Taskforce(id, id, "", status, members.first(), 0, "", 0, "", 0, 0), members,
        )
        val live = tf("t1", listOf("a", "b"))
        assertEquals("t1", meetingTaskforceFor(listOf(live), "a")?.data?.id)
        assertEquals("t1", meetingTaskforceFor(listOf(live), "b")?.data?.id)
        assertNull(meetingTaskforceFor(listOf(live), "c"))
        assertNull(meetingTaskforceFor(listOf(tf("t1", listOf("a"), S.SIGNED_OFF)), "a"))
        assertNull(meetingTaskforceFor(listOf(tf("t1", listOf("a"), S.DISBANDED)), "a"))
        assertNull(meetingTaskforceFor(emptyList(), "a"))
    }

    @Test
    fun retryReRunsOnlyTheFailedAgentsTurn() = kotlinx.coroutines.runBlocking {
        val calls = HashMap<String, Int>()
        val failOnce = mutableSetOf("kai")
        val cards = ArrayList<String>()
        val results = listOf("pixel", "kai", "byte").map { who ->
            retryableTurn(
                run = {
                    calls[who] = (calls[who] ?: 0) + 1
                    if (failOnce.remove(who)) throw IllegalStateException("timeout")
                    "$who ok"
                },
                onFailure = { why -> cards.add("$who:$why"); TurnDecision.RETRY },
            )
        }
        assertEquals(listOf("pixel ok", "kai ok", "byte ok"), results)
        assertEquals(mapOf("pixel" to 1, "kai" to 2, "byte" to 1), calls)
        assertEquals(listOf("kai:timeout"), cards)
        // Skip gives up on just that agent.
        var tries = 0
        val skipped = retryableTurn(run = { tries++; throw IllegalStateException("down") }, onFailure = { TurnDecision.SKIP })
        assertNull(skipped)
        assertEquals(1, tries)
    }

    @Test
    fun mentionsRouteToThoseAgentsInOrderAndChain() {
        val m = listOf("1" to "Kai", "2" to "Byte", "3" to "Kai Lee")
        assertEquals(listOf("2", "1"), Mentions.ids("@byte can you and @Kai check?", m))
        assertEquals(listOf("3"), Mentions.ids("ping @Kai Lee", m))
        assertEquals(emptyList<String>(), Mentions.ids("email kai@byte.com", m))
        assertEquals("Ka", Mentions.typing("hey @Ka"))
        assertNull(Mentions.typing("hey @Kai done"))
        assertEquals("hey @Kai ", Mentions.complete("hey @Ka", "Kai"))
        val q = Mentions.Queue(listOf("2", "1"))
        assertEquals("2", q.next())
        q.replied("2", listOf("3", "2"))
        assertEquals("1", q.next())
        assertEquals("3", q.next())
        assertNull(q.next())
        val capped = Mentions.Queue(listOf("1"), maxTurns = 2)
        capped.next(); capped.replied("1", listOf("2")); capped.next(); capped.replied("2", listOf("1"))
        assertNull(capped.next())
    }

    @Test
    fun debateParsesAndStopsOnAgreeOrRoundCap() {
        assertEquals(Debate.Command("tabs vs spaces", 3), Debate.parse("/debate tabs vs spaces"))
        assertEquals(Debate.Command("pricing", 5), Debate.parse("debate: 9 pricing"))
        assertNull(Debate.parse("let's not debate"))
        assertFalse(Debate.shouldStop(1, 3, listOf(true, false)))
        assertTrue(Debate.shouldStop(2, 3, listOf(true, true)))
        assertTrue(Debate.shouldStop(3, 3, listOf(false, false)))
        assertFalse(Debate.shouldStop(1, 3, emptyList()))
        val reply = parseAgentReply("I concede, @Kai is right.\n---\nSTATUS: continue\nAGREE: yes")
        assertTrue(reply.control.agree)
        assertEquals("I concede, @Kai is right.", reply.display)
        assertFalse(parseAgentReply("No.\n---\nSTATUS: continue\nAGREE: no").control.agree)
    }

    @Test
    fun planGivesDistinctAssignmentsWithFallbackRoles() {
        val members = listOf("L" to "Lead", "a" to "Kai", "b" to "Byte", "c" to "Pixel")
        val plan = parseAgentReply("Plan:\n@Kai: research pricing\n@Byte: draft the landing page\n---\nSTATUS: continue\nASSIGN: Pixel: research pricing")
        val got = TaskforceRules.distinctAssignments(plan.control, "Plan:\n@Kai: research pricing\n@Byte: draft the landing page", members, "L")
        assertEquals("research pricing", got["a"])
        assertEquals("draft the landing page", got["b"])
        // Pixel duplicated Kai's task -> first fallback perspective.
        assertEquals(TaskforceRules.FALLBACK_ROLES[0], got["c"])
        assertEquals(3, got.values.toSet().size)
        val none = TaskforceRules.distinctAssignments(AgentControl(), "no plan", members, "L")
        assertEquals(TaskforceRules.FALLBACK_ROLES.take(3), listOf(none["a"], none["b"], none["c"]))
        val brief = TaskforceRules.workerBrief("a", got, members.toMap())
        assertTrue(brief.startsWith("research pricing"))
        assertTrue(brief.contains("Byte — draft the landing page") && brief.contains("do NOT duplicate"))
        assertFalse(brief.contains("Kai —"))
    }

    @Test
    fun roundRunsWorkThenDebateThenLeadConclusion() {
        assertEquals(listOf(TaskforcePhase.PLAN, TaskforcePhase.WORK, TaskforcePhase.DEBATE, TaskforcePhase.CONCLUDE), TaskforceRules.roundPhases(3))
        assertEquals(listOf(TaskforcePhase.PLAN, TaskforcePhase.WORK, TaskforcePhase.CONCLUDE), TaskforceRules.roundPhases(1))
        assertEquals(listOf(TaskforcePhase.PLAN), TaskforceRules.roundPhases(0))
        assertEquals(2, TaskforceRules.AUTO_DEBATE_ROUNDS)
        assertTrue(Debate.shouldStop(TaskforceRules.AUTO_DEBATE_ROUNDS, TaskforceRules.AUTO_DEBATE_ROUNDS, listOf(false)))
    }

    @Test
    fun workerMentionsChainToTeammatesBounded() {
        val pairs = listOf("a" to "Kai", "b" to "Byte", "c" to "Pixel")
        val mentioned = Mentions.ids("Done. @Byte please verify, and @Pixel check copy.", pairs)
        val q = Mentions.Queue(mentioned, maxTurns = 3)
        assertEquals("b", q.next()); q.replied("b", listOf("a"))
        assertEquals("c", q.next()); assertEquals("a", q.next())
        q.replied("a", listOf("b")); assertNull(q.next())
    }

    @Test
    fun chosenLeadGoesFirstAndIsPersistedAsLeadId() {
        assertEquals(listOf("c", "a", "b"), TaskforceRules.withLead(listOf("a", "b", "c"), "c"))
        assertEquals(listOf("a", "b"), TaskforceRules.withLead(listOf("a", "b"), "zz"))
        assertEquals(listOf("a", "b"), TaskforceRules.withLead(listOf("a", "b"), null))
    }

    private fun soloAgents() = listOf(
        SimAgent("a", "Pixel", "pixel", 0, "idle"),
        SimAgent("b", "Kai", "kai", 1, "idle"),
        SimAgent("c", "Byte", "byte", 2, "idle"),
    )

    @Test
    fun soloConferenceUseIsCappedAtTwoAndCallTurnsOnTheTv() {
        val sim = OfficeSim(Random(5), llmGate = { false })
        val agents = soloAgents()
        sim.tick(0.05f, agents)
        assertTrue(sim.startConfWork("a", call = true))
        assertTrue(sim.startConfWork("b", call = false))
        assertFalse("max 2 solo occupants", sim.startConfWork("c", call = true))
        var sawCall = false
        repeat(600) {
            sim.tick(1f / 30f, agents)
            if (sim.snapshot().confCall) sawCall = true
        }
        assertTrue("TV shows the call grid once the caller sits", sawCall)
        val inRoom = sim.snapshot().actors.filter { it.x.toInt() in OfficeMap.confSeats.map { s -> s.x } }
        assertTrue(inRoom.size <= 2)
    }

    @Test
    fun taskforceEvictsSoloConferenceOccupants() {
        val sim = OfficeSim(Random(6), llmGate = { false })
        val agents = soloAgents()
        sim.tick(0.05f, agents)
        assertTrue(sim.startConfWork("a", call = true))
        repeat(600) { sim.tick(1f / 30f, agents) }
        assertTrue(sim.snapshot().confCall)
        sim.setTaskforceSeats(mapOf("b" to OfficeMap.confSeats[0], "c" to OfficeMap.confSeats[1]))
        sim.tick(1f / 30f, agents)
        assertFalse("caller left: TV no longer in call mode", sim.snapshot().confCall)
        assertFalse("no new solo use while a taskforce holds the room", sim.startConfWork("a", call = false))
    }

    @Test
    fun conversationTurnsAreCappedShortAndStopWhenConverged() {
        assertEquals(6, app.pixelbureau.logic.Conversation.turnBudget(1))
        assertEquals(8, app.pixelbureau.logic.Conversation.turnBudget(3))
        assertEquals(10, app.pixelbureau.logic.Conversation.turnBudget(4))
        // Round-robin, capped at the budget, never the same speaker twice in a row.
        val o = app.pixelbureau.logic.Conversation.Order(listOf("a", "b", "c"), maxTurns = 6)
        val seq = generateSequence { o.next() }.toList()
        assertEquals(6, seq.size)
        assertTrue(seq.zipWithNext().none { it.first == it.second })
        // @mention jumps the queue (incl. the lead as an extra), then early stop on all-AGREE.
        val m = app.pixelbureau.logic.Conversation.Order(listOf("a", "b"), maxTurns = 10, extra = setOf("L"))
        assertEquals("a", m.next()); m.spoke("a", false)
        assertEquals("L", m.next(listOf("L")))
        assertEquals("b", m.next()); m.spoke("b", true)
        assertEquals("a", m.next()); m.spoke("a", true)
        assertTrue(m.converged())
        assertNull("stops early once everyone agrees", m.next())
        // Soft clip at a sentence boundary.
        val long = (1..30).joinToString(" ") { "Point $it is fine." }
        val clipped = app.pixelbureau.logic.Conversation.clip(long)
        assertTrue(clipped.split(" ").size <= 60 && clipped.endsWith("."))
        assertEquals("Short one.", app.pixelbureau.logic.Conversation.clip("Short one."))
    }

    @Test
    fun leadConcludesAfterTheConversation() {
        val phases = TaskforceRules.roundPhases(2)
        assertTrue(phases.indexOf(TaskforcePhase.CONCLUDE) > phases.indexOf(TaskforcePhase.WORK))
        assertEquals(TaskforcePhase.CONCLUDE, phases.last())
        val prompt = app.pixelbureau.logic.taskforceRolePrompt("Kai", "Lead", listOf("Lead", "Kai"), "T", "O", TaskforcePhase.TALK, "Your angle: risk")
        assertTrue(prompt.contains("1–3 sentences") && prompt.contains("AGREE"))
    }

    @Test
    fun slashCommandsParseAndFilterByState() {
        assertEquals(Slash.Parsed(Slash.Kind.DEBATE, "tabs vs spaces", 2), Slash.parse("/debate 2 tabs vs spaces"))
        assertEquals(Slash.Parsed(Slash.Kind.DISCUSS, "pricing"), Slash.parse("/discuss pricing"))
        assertEquals(Slash.Kind.CONCLUDE, Slash.parse(" /conclude")?.kind)
        assertEquals(Slash.Kind.STOP, Slash.parse("/STOP")?.kind)
        assertNull(Slash.parse("/dance now"))
        assertNull(Slash.parse("hello /stop"))
        assertEquals("d", Slash.typing("/d"))
        assertNull(Slash.typing("/debate x"))
        assertEquals(listOf("debate", "discuss"), Slash.suggest("d", discussing = false, canSignOff = false, members = 3).map { it.word })
        assertEquals(listOf("stop"), Slash.suggest("", discussing = true, canSignOff = false, members = 3).map { it.word })
        assertTrue("signoff" in Slash.suggest("s", discussing = false, canSignOff = true, members = 3).map { it.word })
        assertTrue("signoff" !in Slash.suggest("s", discussing = false, canSignOff = false, members = 3).map { it.word })
    }

    @Test
    fun stepsSetTheDiscussionTurnBudget() {
        assertEquals(6, app.pixelbureau.logic.Conversation.turnBudget(2, 3))
        assertEquals(3, app.pixelbureau.logic.Conversation.turnBudget(3, 1))
        assertEquals(24, app.pixelbureau.logic.Conversation.turnBudget(4, 9))
        assertEquals(3, Taskforce("t", "t", "", "ACTIVE", "a", 0, "", 0, "", 0, 0).steps)
    }

    @Test
    fun conferenceSittersSnapOntoTheirChair() {
        val seat = app.pixelbureau.logic.OfficeMap.confSeats.first()
        assertEquals(seat, app.pixelbureau.logic.OfficeMap.confSeatAt(seat.x + 0.1f, seat.y - 0.2f))
        assertNull(app.pixelbureau.logic.OfficeMap.confSeatAt(app.pixelbureau.logic.OfficeMap.confStandSpot.x.toFloat(), app.pixelbureau.logic.OfficeMap.confStandSpot.y.toFloat()))
        assertTrue(app.pixelbureau.logic.OfficeMap.confSeatNestTileH < 0f)
    }

    @Test
    fun signOffRewardEqualsSteps() {
        val s0 = app.pixelbureau.logic.StarScore(reputation = 5, day = 1)
        assertEquals(6, app.pixelbureau.logic.grantTaskforceSignOff(s0, false, steps = 1).reputation)
        assertEquals(11, app.pixelbureau.logic.grantTaskforceSignOff(s0, false, steps = 6).reputation)
        assertEquals(s0, app.pixelbureau.logic.grantTaskforceSignOff(s0, true, steps = 6))
    }

    @Test
    fun meetingStepFollowsTheDiscussion() {
        assertEquals(1, app.pixelbureau.ui.meetingStep(4, 3, null, "ACTIVE"))
        assertEquals(2, app.pixelbureau.ui.meetingStep(4, 3, 3 to 8, "ACTIVE"))
        assertEquals(4, app.pixelbureau.ui.meetingStep(4, 3, null, app.pixelbureau.logic.TaskforceStatus.COMPLETE))
    }

    @Test
    fun concludeMeetingSignsOffFromAnyOpenState() {
        val R = TaskforceRules; val S = app.pixelbureau.logic.TaskforceStatus
        assertEquals(S.SIGNED_OFF, R.concludeMeeting(S.PAUSED))
        assertEquals(S.SIGNED_OFF, R.concludeMeeting(S.ACTIVE))
        assertNull(R.concludeMeeting(S.SIGNED_OFF))
        assertNull(R.concludeMeeting(S.DISBANDED))
        assertTrue(app.pixelbureau.ui.isConclusionText("**Conclusion**\nShip it."))
    }

    @Test
    fun removingBoughtItemsFreesTheCellAndProtectsEssentials() {
        val R = app.pixelbureau.logic.InventoryRules
        val C = app.pixelbureau.logic.Cell(1, 2)
        assertEquals(mapOf("lamp" to 1), R.afterRemove(mapOf("lamp" to 2), "lamp"))
        assertEquals(emptyMap<String, Int>(), R.afterRemove(mapOf("lamp" to 1), "lamp"))
        val pl = mapOf("lamp#0" to C, "lamp#1" to app.pixelbureau.logic.Cell(3, 3), "plant#0" to C)
        assertEquals(setOf("lamp#0", "plant#0"), R.placementsAfterRemove(pl, "lamp", 2).keys)
        assertEquals(0, R.refund(15)); assertEquals(0, R.refund(1))
        assertNull(R.blockReason(false, "lamp"))
        assertNotNull(R.blockReason(true, "desk"))
        assertNotNull(R.blockReason(true, "conf_room"))
    }

    @Test
    fun chatSlashTaskParsesAndPrefills() {
        val C = app.pixelbureau.logic.ChatSlash
        assertEquals("fix login bug", C.parseTask("/task fix login bug"))
        assertEquals("", C.parseTask("/task"))
        assertNull(C.parseTask("/tasks now"))
        assertNull(C.parseTask("hello /task x"))
        assertEquals(listOf("task"), C.suggest("ta").map { it.word })
        assertEquals("Fix login" to "Steps in README", C.split("Fix login\nSteps in README"))
        val long = "word ".repeat(30).trim()
        val (title, brief) = C.split(long)
        assertTrue(title.length <= 80 && brief.isNotEmpty())
    }

    @Test
    fun emptyChatTipsAreShortAndLeadWithTask() {
        val tips = app.pixelbureau.ui.EmptyChatTips
        assertTrue(tips.size in 2..3)
        assertTrue(tips.first().contains("task"))
        assertTrue(tips.all { it.length <= 32 })
    }

    @Test
    fun heroGreetingFollowsTheClock() {
        assertEquals("Good morning, boss", app.pixelbureau.ui.heroGreeting(8))
        assertEquals("Good afternoon, boss", app.pixelbureau.ui.heroGreeting(13))
        assertEquals("Good evening, boss", app.pixelbureau.ui.heroGreeting(20))
        assertTrue(app.pixelbureau.ui.heroGreeting(2).contains("midnight"))
    }

    @Test
    fun heroCrewIsHiredAgentsOldestFirst() {
        fun ag(id: String, order: Int, archived: Boolean = false) =
            app.pixelbureau.data.Agent(id, id, "", "", "m1", null, "idle", archived, order)
        val crew = app.pixelbureau.ui.heroCrew(listOf(ag("b", 2), ag("a", 1), ag("x", 0, archived = true)))
        assertEquals(listOf("a", "b"), crew.map { it.id })
        assertEquals("chars/m3_idle", app.pixelbureau.logic.spriteFrame("m3", "idle", 0))
    }
}
