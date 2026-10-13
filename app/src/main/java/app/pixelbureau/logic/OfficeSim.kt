package app.pixelbureau.logic

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

data class SimAgent(
    val id: String,
    val name: String,
    val spriteKey: String,
    val deskIndex: Int?,
    val status: String,
    /** Instructions / duties text; a keyword personalises banter. */
    val role: String = "",
)

data class ActorSnap(
    val id: String,
    val name: String,
    val spriteKey: String,
    val x: Float,
    val y: Float,
    val anim: String,
    val frame: Int,
    /** front | back | left | right — drives directional walk frames / flip. */
    val facing: String,
    val bubble: String?,
    /** Desk whose laptop should react, or null when not at a desk. */
    val deskIndex: Int? = null,
    /**
     * When true, canvas tucks the sprite toward the desk (chair sit/type).
     * Sofa and beanbag dozing leave this false so the body stays on the cushion.
     */
    val useSeatTuck: Boolean = true,
    /** Idle pair is walking over or standing together to talk. Visual only. */
    val chatting: Boolean = false,
    /** 0..1 pop-in progress of the current speech bubble. */
    val bubblePop: Float = 1f,
    /** Taskforce turn failed to reach the model: draw the muted warning bubble. */
    val modelError: Boolean = false,
)

data class OfficeSnapshot(
    val actors: List<ActorSnap>,
    /** True while an idle agent is holding at the fridge (door open). */
    val fridgeOpen: Boolean = false,
    /** Conference-room door swing, 0 = shut, 1 = fully open (swung inward). */
    val confDoorOpen: Float = 0f,
    /** NE office entrance sliding doors, 0 = closed, 1 = fully slid open. */
    val backDoorOpen: Float = 0f,
    /** A solo agent is on an online call in the conference room: TV shows the call grid. */
    val confCall: Boolean = false,
)

class OfficeSim(
    private val random: Random = Random.Default,
    /** When true, the next spoken line may be rewritten by that agent's model. */
    private val llmGate: () -> Boolean = { banterLlmRoll(random) },
    /** Wall clock for the server fan. Negative skips fan inspection (unit tests). */
    private val nowMs: () -> Long = { -1L },
) {
    private class Actor(
        val id: String,
        var x: Float,
        var y: Float,
    ) {
        var name: String = ""
        var spriteKey: String = "pixel"
        var status: String = "idle"
        var deskIndex: Int? = null
        var dest: Cell? = null
        var path: ArrayDeque<Cell> = ArrayDeque()
        var sitting: Boolean = false
        var announcedArrival: Boolean = false
        /** Dropped from the roster: walking out through the nearest door, then removed. */
        var exitHold: Float = 0f
        var leaving: Boolean = false
        var frameClock: Float = 0f
        /** front | back | left | right */
        var facing: String = "front"
        /** Seconds remaining before facing may change again (anti-flicker hold). */
        var facingHold: Float = 0f
        /** Grid cell where the current path leg began. Facing follows this→next cardinal. */
        var legOrigin: Cell? = null

        // --- Idle office activities (only while status == idle) ---
        /** none | chat | coffee | fridge | sofa | beanbag | pretend | wander */
        var activity: String = ACT_NONE
        /** Seconds left in the current activity's "do" phase (after arriving). */
        var activityHold: Float = 0f
        /** Seconds before another idle activity may start. */
        var cooldown: Float = 0f
        var talkWith: String? = null
        var activityDest: Cell? = null
        var pretendDesk: Int? = null
        var socialBubble: String? = null
        var bubbleTimer: Float = 0f
        /** Shared script index for a chat pair. */
        var chatScript: Int = 0
        /** Next line index in [officeChatExchanges]. Even lines belong to the lead. */
        var chatStep: Int = 0
        var chatLead: Boolean = false
        var lastChatLine: String = ""
        /** Non-zero while this speaker's current line may still be replaced. */
        var banterToken: Long = 0
        /** Sitting in the conference room for a taskforce (not a casual meeting). */
        var inTaskforce: Boolean = false
        /** Rendered call/response lines for the current chat pair. */
        var chatLines: List<String> = emptyList()
        var bubbleAge: Float = 99f
        var duty: String = ""
        /** Solo conference use: "" | CONF_FOCUS | CONF_CALL. */
        var confMode: String = ""
    }

    private val memory = BanterMemory()
    private var modelErrors: Set<String> = emptySet()

    /** Agents whose taskforce turn couldn't reach the model (until retried / skipped). */
    fun setModelErrors(ids: Set<String>) {
        modelErrors = ids
    }
    private var taskforceTitle: String = ""
    private var placedKinds: Map<Cell, String> = emptyMap()
    private var placedKnown: Set<Cell>? = null

    /** Title of the live taskforce, referenced in meeting lines. */
    fun setTaskforceTitle(title: String?) {
        taskforceTitle = title.orEmpty()
    }

    /** Shop prop kinds by cell, for "near the arcade" lines and new-purchase reactions. */
    fun setPlacedKinds(kinds: Map<Cell, String>) {
        val known = placedKnown
        placedKinds = kinds
        placedKnown = kinds.keys
        if (known == null) return
        val fresh = kinds.keys - known
        if (fresh.isEmpty()) return
        val cell = fresh.first()
        val watcher = actors.values.filter { !it.leaving }
            .minByOrNull { hypot((it.x - cell.x).toDouble(), (it.y - cell.y).toDouble()) } ?: return
        say(watcher, BanterBook.bought, force = true)
    }

    /** Taskforce signed off: members celebrate the +3★. */
    fun celebrate(ids: Collection<String>) {
        for (id in ids) actors[id]?.let { say(it, BanterBook.signOff, force = true) }
    }

    /** Lines currently remembered as recently said (tests). */
    fun recentBanter(): List<String> = memory.recentLines()

    private fun say(actor: Actor, pool: List<String>, force: Boolean = false, partner: String = ""): Boolean {
        val raw = memory.pick(pool, random) ?: return false
        val line = BanterBook.fill(raw, actor.name, partner, taskforceTitle, actor.duty)
        return showBubble(actor, line, BanterBook.seconds(line), force)
    }

    private val actors = LinkedHashMap<String, Actor>()
    private val arrivals = ArrayList<String>()
    private val banterAsks = ArrayList<BanterAsk>()
    private var banterSerial = 0L
    /** Built-in obstacles plus placed shop purchases ([setPlacedObstacles]). */
    private var blocked: Set<Cell> = OfficeMap.blocked()
    /** Conference door swing 0..1, eased toward open while someone is in the doorway. */
    private var confDoor: Float = 0f
    private var backDoor: Float = 0f
    /** agentId → conference chair for live taskforce members. */
    private var taskforceSeats: Map<String, Cell> = emptyMap()

    /**
     * Live taskforce members and their chairs. Idle members walk in through the
     * door and stay seated; anyone dropped from the map gets up and leaves.
     */
    fun setTaskforceSeats(seats: Map<String, Cell>) {
        taskforceSeats = seats
    }

    /**
     * Shop purchases standing on the floor become solid. Anyone whose current
     * route crosses a new obstacle hits the solid-tile check in [step] and
     * re-plans; anyone standing on one is nudged off by [anchorCell].
     */
    fun setPlacedObstacles(cells: Set<Cell>) {
        val next = OfficeMap.blocked() + cells
        if (next == blocked) return
        blocked = next
        for (actor in actors.values) {
            if (actor.path.any { it in cells }) {
                actor.path.clear()
                actor.dest = null
                actor.legOrigin = null
            }
        }
    }

    fun arm(id: String) {
        actors[id]?.announcedArrival = false
    }

    fun tick(dt: Float, agents: List<SimAgent>) {
        val live = agents.map { it.id }.toSet()
        tickDepartures(dt, live)
        // Spawn everyone before anyone picks a partner, so the first tick can pair them.
        for (agent in agents) {
            actors.getOrPut(agent.id) {
                spawnDoor(agent).let { d -> Actor(agent.id, d.x.toFloat(), d.y.toFloat()) }
            }
        }
        for (agent in agents) {
            val actor = actors.getOrPut(agent.id) {
                spawnDoor(agent).let { d -> Actor(agent.id, d.x.toFloat(), d.y.toFloat()) }
            }
            if (actor.name.isEmpty() && agent.name.isNotEmpty()) {
                actor.name = agent.name
                say(actor, BanterBook.enter)
            }
            actor.name = agent.name
            actor.spriteKey = agent.spriteKey
            actor.duty = BanterBook.dutyKeyword(agent.role)
            actor.bubbleAge += dt
            val prevStatus = actor.status
            actor.status = agent.status
            actor.deskIndex = agent.deskIndex
            if (agent.status != "walking") {
                actor.announcedArrival = false
            }
            if (agent.status != "idle" && agent.id !in taskforceSeats) {
                // Real tasks preempt lounge behaviors (once), then work-mood bubbles.
                if (actor.activity != ACT_NONE || actor.talkWith != null) {
                    clearIdleActivity(actor, releasePartner = true)
                } else if (prevStatus == "idle") {
                    // Drop leftover lounge chatter when work begins.
                    actor.socialBubble = null
                    actor.bubbleTimer = 0f
                }
                tickWorkMood(actor, dt)
            } else {
                tickIdleActivity(actor, dt)
            }
            val dest = destination(actor, agent)
            if (actor.dest != dest) {
                actor.dest = dest
                actor.sitting = false
                val start = anchorCell(actor, dest)
                val route = ArrayDeque(findPath(OfficeMap.width, OfficeMap.height, blocked, start, dest))
                val offTile = abs(actor.x - start.x) > 0.02f || abs(actor.y - start.y) > 0.02f
                if (offTile) {
                    route.addFirst(start)
                }
                actor.path = route
                actor.legOrigin = Cell(
                    actor.x.roundToInt().coerceIn(0, OfficeMap.width - 1),
                    actor.y.roundToInt().coerceIn(0, OfficeMap.height - 1),
                )
            }
            step(actor, dt)
            val atDest = actor.path.isEmpty() && near(actor, dest)
            if (agent.status == "walking" && !actor.announcedArrival && atDest) {
                actor.announcedArrival = true
                actor.sitting = true
                actor.facing = deskSeatFacing(actor, agent.deskIndex)
                actor.facingHold = 0f
                arrivals.add(agent.id)
            }
            if (agent.status == "idle" || agent.id in taskforceSeats) {
                applyIdleArrival(actor, atDest)
            }
            if (atDest && agent.status in seatedStatuses && agent.id !in taskforceSeats) {
                actor.sitting = true
                actor.facing = deskSeatFacing(actor, agent.deskIndex)
                actor.facingHold = 0f
            }
        }
        // Kick pretenders off desks that just got real work.
        evictPretendersFromBusyDesks(agents)
        tickConfDoor(dt)
        tickBackDoor(dt)
    }

    /** Current conference door swing (0 shut .. 1 open). Exposed for tests. */
    fun confDoorOpen(): Float = confDoor

    /**
     * Open the glass door inward while anyone is walking through the doorway
     * (outside approach tile, the door gap, or the first tile inside), then let
     * it swing shut again once they have passed.
     */
    /**
     * Agents no longer on the roster walk to the nearest entrance (the door
     * slides open as they reach it), pass through and are then removed from
     * the map — nobody lingers outside. Someone re-added mid-walk turns back.
     */
    private fun tickDepartures(dt: Float, live: Set<String>) {
        val gone = ArrayList<String>()
        for (actor in actors.values) {
            if (actor.id in live) {
                actor.leaving = false
                continue
            }
            if (!actor.leaving) {
                actor.leaving = true
                if (actor.activity != ACT_NONE || actor.talkWith != null) {
                    clearIdleActivity(actor, releasePartner = true)
                }
                actor.sitting = false
                actor.socialBubble = null
                actor.bubbleTimer = 0f
                say(actor, BanterBook.leave, force = true)
                val door = OfficeMap.nearestDoor(actor.x, actor.y)
                actor.dest = door
                val start = anchorCell(actor, door)
                val route = ArrayDeque(findPath(OfficeMap.width, OfficeMap.height, blocked, start, door))
                if (abs(actor.x - start.x) > 0.02f || abs(actor.y - start.y) > 0.02f) route.addFirst(start)
                actor.path = route
            }
            step(actor, dt)
            actor.bubbleAge += dt
            if (actor.bubbleTimer > 0f) {
                actor.bubbleTimer = (actor.bubbleTimer - dt).coerceAtLeast(0f)
                if (actor.bubbleTimer == 0f) actor.socialBubble = null
            }
            val door = actor.dest ?: OfficeMap.door
            if (actor.path.isEmpty() && near(actor, door)) {
                actor.exitHold += dt
                // Brief beat in the doorway so the door is seen open, then gone.
                if (actor.exitHold >= ExitDoorwayHoldSec) gone.add(actor.id)
            }
        }
        gone.forEach { actors.remove(it) }
    }

    /** True while [id] is on the map walking out (tests). */
    fun isLeaving(id: String): Boolean = actors[id]?.leaving == true

    /** Current back-entrance opening (0 closed .. 1 open). Exposed for tests. */
    fun backDoorOpen(): Float = backDoor

    /**
     * Slide the NE entrance open while anyone is on / approaching the door tile
     * or the tile just inside it; slide shut once they have passed. Default closed.
     */
    private fun tickBackDoor(dt: Float) {
        val d = OfficeMap.backDoor
        val someone = actors.values.any { a ->
            abs(a.x - d.x) < 1.1f && a.y - d.y < 1.6f && a.y - d.y > -0.6f
        }
        val target = if (someone) 1f else 0f
        val rate = if (someone) BackDoorOpenPerSec else BackDoorClosePerSec
        backDoor = if (backDoor < target) {
            (backDoor + rate * dt).coerceAtMost(target)
        } else {
            (backDoor - rate * dt).coerceAtLeast(target)
        }
    }

    private fun tickConfDoor(dt: Float) {
        val mx = (OfficeMap.confDoorInside.x + OfficeMap.confDoorOutside.x) / 2f
        val my = OfficeMap.confDoorInside.y.toFloat()
        val someone = actors.values.any { a ->
            a.path.isNotEmpty() && abs(a.x - mx) < 1.25f && abs(a.y - my) < 0.6f
        }
        val target = if (someone) 1f else 0f
        val rate = if (someone) ConfDoorOpenPerSec else ConfDoorClosePerSec
        confDoor = if (confDoor < target) {
            (confDoor + rate * dt).coerceAtMost(target)
        } else {
            (confDoor - rate * dt).coerceAtLeast(target)
        }
    }

    fun consumeArrivals(): List<String> {
        if (arrivals.isEmpty()) {
            return emptyList()
        }
        val copy = arrivals.toList()
        arrivals.clear()
        return copy
    }

    /** Lines the office wants rewritten. Empty unless the 1-in-20 gate hit. */
    fun consumeBanterAsks(): List<BanterAsk> {
        if (banterAsks.isEmpty()) {
            return emptyList()
        }
        val copy = banterAsks.toList()
        banterAsks.clear()
        return copy
    }

    /** True while [token] is still the line on screen, so a late reply can be ignored. */
    fun banterStillOpen(token: Long): Boolean {
        if (token == 0L) {
            return false
        }
        return actors.values.any { it.banterToken == token }
    }

    /**
     * Drop in a model line if this speaker is still on the same beat.
     * A miss leaves the canned sentence that is already showing.
     */
    fun offerBanterLine(agentId: String, token: Long, line: String) {
        val actor = actors[agentId] ?: return
        if (actor.activity != ACT_CHAT || actor.banterToken != token) {
            return
        }
        val clipped = clipBanterLine(line) ?: return
        actor.socialBubble = clipped
        actor.lastChatLine = clipped
        actor.bubbleTimer = 2.2f
        val partner = actor.talkWith?.let { actors[it] } ?: return
        if (partner.talkWith == actor.id && partner.socialBubble == null) {
            partner.bubbleTimer = actor.bubbleTimer
        }
    }

    fun snapshot(): OfficeSnapshot {
        return OfficeSnapshot(
            actors.values.map { actor ->
                val frame = (actor.frameClock / 0.16f).toInt()
                val anim = when {
                    actor.path.isNotEmpty() -> "walk"
                    actor.sitting && actor.activity == ACT_PRETEND -> "type"
                    // Taskforce members work at the table (typing); overflow members stand.
                    actor.sitting && actor.activity == ACT_MEETING && actor.inTaskforce ->
                        if (actor.activityDest in OfficeMap.confSeats) "type" else "idle"
                    actor.sitting && actor.activity == ACT_MEETING && actor.confMode == CONF_FOCUS -> "type"
                    actor.sitting && actor.activity == ACT_MEETING -> "sit"
                    actor.sitting && actor.isNapping() -> "sleep"
                    actor.sitting && actor.status == "done" -> "sit"
                    actor.sitting && actor.status in seatedStatuses -> "type"
                    actor.sitting -> "sit"
                    // At the machine with the sip hold running — drink0/drink1 arm+mug cycle.
                    actor.activity == ACT_COFFEE && actor.activityHold > 0f -> "drink"
                    else -> "idle"
                }
                // Sit/type: face the laptop (seat→desk). Default sheets; canvas
                // flips when facing == "left". Sleep sheet ignores facing.
                val meetingSeat = (anim == "sit" || anim == "type") && actor.activity == ACT_MEETING &&
                    actor.activityDest in OfficeMap.confSeats
                val facing = if (meetingSeat) {
                    actor.activityDest?.let { OfficeMap.confSeatFacing(it) } ?: actor.facing
                } else if (anim == "sit" || anim == "type") {
                    deskSeatFacing(actor)
                } else {
                    actor.facing
                }
                val deskForScreen = when {
                    actor.activity == ACT_PRETEND -> actor.pretendDesk
                    anim == "sleep" -> null
                    meetingSeat -> null
                    anim == "type" || anim == "sit" -> actor.deskIndex
                    else -> actor.deskIndex
                }
                ActorSnap(
                    id = actor.id,
                    name = actor.name,
                    spriteKey = actor.spriteKey,
                    x = actor.x,
                    y = actor.y,
                    anim = anim,
                    frame = frame,
                    facing = facing,
                    bubble = if (actor.id in modelErrors) ModelErrorBubble else bubbleFor(actor),
                    modelError = actor.id in modelErrors,
                    deskIndex = deskForScreen,
                    // Desk chair tuck only — sofa/beanbag sleep stays on the cushion.
                    useSeatTuck = (anim == "sit" || anim == "type") && !actor.isNapping() && !meetingSeat,
                    chatting = actor.activity == ACT_CHAT,
                    bubblePop = (actor.bubbleAge / 0.22f).coerceIn(0f, 1f),
                )
            },
            fridgeOpen = actors.values.any { it.activity == ACT_FRIDGE && it.activityHold > 0f },
            confDoorOpen = confDoor,
            backDoorOpen = backDoor,
            confCall = actors.values.any { it.confMode == CONF_CALL && it.sitting && it.activity == ACT_MEETING },
        )
    }


    /** Arrival entrance: the door nearest the agent's desk chair (main door otherwise). */
    private fun spawnDoor(agent: SimAgent): Cell {
        val desk = agent.deskIndex ?: return OfficeMap.door
        if (desk !in OfficeMap.desks.indices) return OfficeMap.door
        val seat = OfficeMap.chair(desk)
        return OfficeMap.nearestDoor(seat.x.toFloat(), seat.y.toFloat())
    }

    private fun Actor.isNapping(): Boolean = activity == ACT_SOFA || activity == ACT_BEANBAG

    private fun destination(actor: Actor, agent: SimAgent): Cell {
        // Live taskforce members hold their conference chair / stand tile, working or not.
        taskforceSeats[actor.id]?.let { return it }
        if (agent.status != "idle" && agent.deskIndex != null) {
            return OfficeMap.chair(agent.deskIndex)
        }
        if (agent.status != "idle") {
            return OfficeMap.nearestDoor(actor.x, actor.y)
        }
        // Idle: activity destination, or stay put during cooldown.
        val target = actor.activityDest
        if (target != null) {
            return target
        }
        // Cooldown / between activities: hold current tile (no aimless lounge hop).
        return Cell(
            actor.x.roundToInt().coerceIn(0, OfficeMap.width - 1),
            actor.y.roundToInt().coerceIn(0, OfficeMap.height - 1),
        ).let { cell ->
            if (cell in blocked) {
                anchorCell(actor, OfficeMap.door)
            } else {
                cell
            }
        }
    }

    private fun tickIdleActivity(actor: Actor, dt: Float) {
        if (actor.cooldown > 0f) {
            actor.cooldown = (actor.cooldown - dt).coerceAtLeast(0f)
        }
        if (actor.bubbleTimer > 0f) {
            actor.bubbleTimer = (actor.bubbleTimer - dt).coerceAtLeast(0f)
            if (actor.bubbleTimer == 0f) {
                actor.socialBubble = null
            }
        }
        val tfSeat = taskforceSeats[actor.id]
        if (tfSeat != null) {
            if (!actor.inTaskforce || actor.activity != ACT_MEETING || actor.activityDest != tfSeat) {
                clearIdleActivity(actor, releasePartner = true)
                actor.activity = ACT_MEETING
                actor.activityDest = tfSeat
                actor.activityHold = 0f
                actor.inTaskforce = true
                say(actor, BanterBook.toConf, force = true)
            } else if (actor.activityHold > 0f) {
                // Seated: stay for as long as the taskforce lives.
                actor.activityHold = 30f
                if (actor.bubbleTimer <= 0f && random.nextFloat() < dt * 0.15f) {
                    pulseBubble(actor)
                }
            }
            return
        }
        if (actor.confMode.isNotEmpty() && (taskforceSeats.isNotEmpty() || actor.activity != ACT_MEETING)) {
            // A taskforce always gets the room: solo occupants leave right away.
            if (actor.activity == ACT_MEETING) say(actor, BanterBook.confLeave, force = true)
            actor.confMode = ""
            if (actor.activity == ACT_MEETING) {
                finishIdleActivity(actor)
                actor.activityDest = wanderSpot(actor)
                actor.activity = ACT_WANDER
            }
            return
        }
        if (actor.inTaskforce) {
            // Taskforce ended (sign-off / disband): get up and leave the room.
            actor.inTaskforce = false
            finishIdleActivity(actor)
            actor.activityDest = wanderSpot(actor)
            actor.activity = ACT_WANDER
            return
        }
        considerFan(actor)
        if (actor.activity == ACT_FAN) {
            if (actor.activityHold > 0f) {
                actor.activityHold = (actor.activityHold - dt).coerceAtLeast(0f)
                if (actor.bubbleTimer <= 0f) {
                    pulseBubble(actor)
                }
                // Stay for the rest of the spin. The fan clock ends the look.
                if (actor.activityHold == 0f) {
                    actor.activityHold = 1.2f
                }
            }
            return
        }
        if (actor.activity == ACT_CHAT) {
            // Keep trading lines while they walk over and while they stand together.
            tickChatTurn(actor)
            if (actor.activityHold > 0f) {
                actor.activityHold = (actor.activityHold - dt).coerceAtLeast(0f)
                if (actor.activityHold == 0f) {
                    finishIdleActivity(actor)
                }
            }
            return
        }
        if (actor.activity == ACT_NONE) {
            // Standing around: greet neighbors / show emotion bubbles.
            tryAmbientSocial(actor, dt)
            if (actor.cooldown <= 0f) {
                pickIdleActivity(actor)
            }
            return
        }
        // Waiting to arrive — hold timer starts on arrival in applyIdleArrival.
        if (actor.activityHold > 0f) {
            actor.activityHold = (actor.activityHold - dt).coerceAtLeast(0f)
            // Refresh chatter / emotion bubbles while performing.
            if (actor.bubbleTimer <= 0f) {
                pulseBubble(actor)
            }
            if (actor.activityHold == 0f) {
                if (actor.confMode.isNotEmpty()) {
                    say(actor, BanterBook.confLeave)
                    actor.confMode = ""
                }
                finishIdleActivity(actor)
            }
        } else {
            // En route: pass-by greetings & vibes.
            tryAmbientSocial(actor, dt)
        }
    }

    private fun applyIdleArrival(actor: Actor, atDest: Boolean) {
        when (actor.activity) {
            ACT_SOFA, ACT_BEANBAG, ACT_PRETEND, ACT_MEETING -> {
                if (atDest) {
                    actor.sitting = true
                    // Sofa/beanbag sleep ignores facing; pretend work faces that desk's laptop;
                    // meeting chairs face the conference table.
                    actor.facing = if (actor.activity == ACT_PRETEND) {
                        deskSeatFacing(actor)
                    } else if (actor.activity == ACT_MEETING) {
                        actor.activityDest?.let { OfficeMap.confSeatFacing(it) } ?: actor.facing
                    } else {
                        "back"
                    }
                    actor.facingHold = 0f
                    if (actor.activityHold <= 0f) {
                        // Just arrived — start the hold window.
                        actor.activityHold = holdDuration(actor.activity)
                        pulseBubble(actor)
                    }
                } else {
                    actor.sitting = false
                }
            }
            ACT_COFFEE, ACT_FRIDGE, ACT_WANDER -> {
                actor.sitting = false
                if ((actor.activity == ACT_COFFEE || actor.activity == ACT_FRIDGE) && atDest) {
                    // Face the prop during the sip / snack hold.
                    val prop = if (actor.activity == ACT_COFFEE) OfficeMap.coffee else OfficeMap.fridge
                    val cdx = prop.x - actor.x
                    val cdy = prop.y - actor.y
                    actor.facing = facingFromDelta(cdx, cdy, actor.facing)
                    actor.facingHold = 0f
                }
                if (atDest && actor.activityHold <= 0f) {
                    actor.activityHold = holdDuration(actor.activity)
                    // Coffee sip, fridge snack, and wander pause get a short bubble beat.
                    pulseBubble(actor)
                }
            }
            ACT_FAN -> {
                actor.sitting = false
                if (atDest) {
                    val rack = OfficeMap.server
                    actor.facing = facingFromDelta(rack.x - actor.x, rack.y - actor.y, actor.facing)
                    actor.facingHold = 0f
                    if (actor.activityHold <= 0f) {
                        actor.activityHold = 2.4f
                        pulseBubble(actor)
                    }
                }
            }
            ACT_CHAT -> {
                actor.sitting = false
                if (atDest) {
                    faceTalkPartner(actor)
                    val partner = actor.talkWith?.let { actors[it] }
                    val partnerReady = partner != null &&
                        partner.activity == ACT_CHAT &&
                        partner.path.isEmpty() &&
                        partner.activityDest != null &&
                        near(partner, partner.activityDest!!)
                    if (partnerReady && actor.activityHold <= 0f) {
                        val hold = holdDuration(ACT_CHAT)
                        actor.activityHold = hold
                        partner!!.activityHold = hold
                    }
                }
            }
            else -> {
                actor.sitting = false
                // A finished desk task can leave the actor stationary on a
                // walkable chair tile. Restore the visible front idle pose
                // instead of retaining the desk sit/type facing toward the laptop.
                actor.facing = "front"
                actor.facingHold = 0f
            }
        }
    }

    private fun pickIdleActivity(actor: Actor) {
        val choices = ArrayList<Pair<String, Int>>(6)
        // Pair chat if another free idle agent exists (boosted vs other lounge acts).
        val partner = findChatPartner(actor)
        if (partner != null) {
            choices.add(ACT_CHAT to 50)
        }
        choices.add(ACT_COFFEE to 20)
        if (actors.values.none { it.activity == ACT_FRIDGE }) {
            choices.add(ACT_FRIDGE to 20)
        }
        if (freeSofaSlots().isNotEmpty()) {
            choices.add(ACT_SOFA to 20)
        }
        if (beanbagFree()) {
            choices.add(ACT_BEANBAG to 16)
        }
        if (vacantDesks().isNotEmpty()) {
            choices.add(ACT_PRETEND to 24)
        }
        if (confSoloAllowed()) {
            choices.add(ACT_MEETING to 22)
        }
        // Light lounge stroll so agents walk between destinations more often.
        choices.add(ACT_WANDER to 18)
        if (choices.isEmpty()) {
            return
        }
        val pick = weightedPick(choices) ?: return
        when (pick) {
            ACT_CHAT -> {
                val other = partner ?: return
                startChat(actor, other)
            }
            ACT_COFFEE -> {
                actor.activity = ACT_COFFEE
                actor.activityDest = OfficeMap.coffeeApproach.random(random)
                actor.activityHold = 0f
            }
            ACT_FRIDGE -> {
                actor.activity = ACT_FRIDGE
                // One stand tile: immediately right of the fridge.
                actor.activityDest = OfficeMap.fridgeApproach.random(random)
                actor.activityHold = 0f
            }
            ACT_SOFA -> {
                // One sleeper per loveseat cushion (sofaSpan tile); reserve on pick.
                val slot = freeSofaSlots().randomOrNull(random) ?: return
                actor.activity = ACT_SOFA
                actor.activityDest = slot
                actor.activityHold = 0f
            }
            ACT_BEANBAG -> {
                // One sleeper on the beanbag; reserve on pick.
                if (!beanbagFree()) return
                actor.activity = ACT_BEANBAG
                actor.activityDest = OfficeMap.beanbag
                actor.activityHold = 0f
            }
            ACT_PRETEND -> {
                val desk = vacantDesks().randomOrNull(random) ?: return
                actor.activity = ACT_PRETEND
                actor.pretendDesk = desk
                actor.activityDest = OfficeMap.chair(desk)
                actor.activityHold = 0f
            }
            ACT_WANDER -> {
                actor.activity = ACT_WANDER
                actor.activityDest = wanderSpot(actor)
                actor.activityHold = 0f
            }
            ACT_MEETING -> {
                // Solo focus work or an online call in the free glass room; a colleague may tag along.
                if (!startConfWork(actor.id, call = random.nextBoolean())) return
                val buddy = partner
                if (buddy != null && random.nextInt(3) == 0) {
                    startConfWork(buddy.id, call = actor.confMode == CONF_CALL)
                }
            }
        }
    }

    private fun confSoloAllowed(): Boolean =
        taskforceSeats.isEmpty() && freeMeetingSeats().isNotEmpty() &&
            actors.values.count { it.confMode.isNotEmpty() } < MAX_CONF_SOLO

    /**
     * Send an idle agent into the free conference room for focus work or an
     * online call (TV shows the call grid). Max [MAX_CONF_SOLO]; never while a
     * taskforce holds the room. Returns false when not allowed.
     */
    fun startConfWork(id: String, call: Boolean): Boolean {
        val actor = actors[id] ?: return false
        if (actor.status != "idle" || actor.leaving || id in taskforceSeats || !confSoloAllowed()) return false
        val seat = freeMeetingSeats().randomOrNull(random) ?: return false
        clearIdleActivity(actor, releasePartner = true)
        actor.activity = ACT_MEETING
        actor.activityDest = seat
        actor.activityHold = 0f
        actor.cooldown = 0f
        actor.confMode = if (call) CONF_CALL else CONF_FOCUS
        say(actor, BanterBook.confEnter)
        return true
    }

    /** Conference chairs not yet claimed (reserved on pick, like sofa cushions). */
    private fun freeMeetingSeats(): List<Cell> {
        val taken = actors.values.filter { it.activity == ACT_MEETING }.mapNotNull { it.activityDest }.toSet() +
            taskforceSeats.values
        return OfficeMap.confSeats.filter { it !in taken && it !in blocked }
    }

    private fun startChat(a: Actor, b: Actor) {
        val meetup = chatMeetup(a, b)
        val side = chatSideSpot(meetup, a, b)
        a.activity = ACT_CHAT
        b.activity = ACT_CHAT
        a.talkWith = b.id
        b.talkWith = a.id
        a.activityDest = meetup
        b.activityDest = side
        a.activityHold = 0f
        b.activityHold = 0f
        a.sitting = false
        b.sitting = false
        val script = random.nextInt(officeChatExchanges.size)
        val templated = if (random.nextBoolean()) BanterBook.exchange(random.nextInt(BanterBook.exchanges.size), a.name, b.name) else emptyList()
        a.chatLines = templated
        b.chatLines = templated
        a.chatScript = script
        b.chatScript = script
        a.chatStep = 0
        b.chatStep = 0
        a.chatLead = true
        b.chatLead = false
        a.lastChatLine = ""
        b.lastChatLine = ""
        a.banterToken = 0
        b.banterToken = 0
        // First sentence as they head over. The partner answers when that bubble ends.
        speakChatLine(a, b)
    }

    /**
     * While the roof fan is spinning, one idle agent already beside the rack
     * steps to a side tile and looks. Anyone farther away keeps what they were doing.
     */
    private fun considerFan(actor: Actor) {
        val clock = nowMs()
        if (clock < 0L) {
            return
        }
        val spinning = serverFanSpin(clock) != null
        if (actor.activity == ACT_FAN) {
            if (!spinning) {
                finishIdleActivity(actor)
            }
            return
        }
        if (!spinning) {
            return
        }
        if (actor.activity != ACT_NONE && actor.activity != ACT_WANDER && actor.activity != ACT_PRETEND) {
            return
        }
        if (distanceToServer(actor) > FanInspectRadius) {
            return
        }
        if (actors.values.any { it.activity == ACT_FAN }) {
            return
        }
        actor.activity = ACT_FAN
        actor.activityDest = fanStandTile(actor)
        actor.activityHold = 0f
        actor.sitting = false
        actor.pretendDesk = null
        showBubble(actor, officeFanInspectLines.random(random))
    }

    private fun distanceToServer(actor: Actor): Float {
        val rack = OfficeMap.server
        return hypot((actor.x - rack.x).toDouble(), (actor.y - rack.y).toDouble()).toFloat()
    }

    private fun fanStandTile(actor: Actor): Cell {
        val here = Cell(
            actor.x.roundToInt().coerceIn(0, OfficeMap.width - 1),
            actor.y.roundToInt().coerceIn(0, OfficeMap.height - 1),
        )
        val neighbors = listOf(
            Cell(OfficeMap.server.x - 1, OfficeMap.server.y),
            Cell(OfficeMap.server.x + 1, OfficeMap.server.y),
            Cell(OfficeMap.server.x, OfficeMap.server.y + 1),
            Cell(OfficeMap.server.x, OfficeMap.server.y - 1),
        ).filter { OfficeMap.inBounds(it) && it !in blocked }
        return neighbors.minByOrNull { abs(it.x - here.x) + abs(it.y - here.y) } ?: here
    }

    private fun findChatPartner(actor: Actor): Actor? {
        // Someone standing between tasks can be waved over, even during their cooldown.
        return actors.values.firstOrNull { other ->
            other.id != actor.id &&
                other.status == "idle" &&
                other.activity == ACT_NONE
        }
    }

    private fun chatMeetup(a: Actor, b: Actor): Cell {
        val ax = ((a.x + b.x) / 2f).roundToInt()
        val ay = ((a.y + b.y) / 2f).roundToInt()
        val ideal = Cell(
            ax.coerceIn(0, OfficeMap.width - 1),
            ay.coerceIn(0, OfficeMap.height - 1),
        )
        if (ideal !in blocked && OfficeMap.inBounds(ideal)) {
            return ideal
        }
        return OfficeMap.lounge.filter { it !in blocked }.randomOrNull(random)
            ?: OfficeMap.lounge.first()
    }

    private fun chatSideSpot(meetup: Cell, a: Actor, b: Actor): Cell {
        val neighbors = listOf(
            Cell(meetup.x + 1, meetup.y),
            Cell(meetup.x - 1, meetup.y),
            Cell(meetup.x, meetup.y + 1),
            Cell(meetup.x, meetup.y - 1),
        ).filter { OfficeMap.inBounds(it) && it !in blocked && it != meetup }
        if (neighbors.isEmpty()) {
            return OfficeMap.lounge.firstOrNull { it != meetup && it !in blocked } ?: meetup
        }
        // Prefer the neighbor closer to B so they don't both pile on meetup.
        return neighbors.minBy { abs(it.x - b.x.roundToInt()) + abs(it.y - b.y.roundToInt()) }
            .also { @Suppress("UNUSED_EXPRESSION") a }
    }

    private fun faceTalkPartner(actor: Actor) {
        val partnerId = actor.talkWith ?: return
        val partner = actors[partnerId] ?: return
        val dx = partner.x - actor.x
        val dy = partner.y - actor.y
        if (actor.facingHold > 0f) {
            return
        }
        val next = facingFromDelta(dx, dy, actor.facing)
        if (next != actor.facing) {
            actor.facing = next
            actor.facingHold = FACING_HOLD_SEC
        }
    }

    private fun finishIdleActivity(actor: Actor) {
        val partnerId = actor.talkWith
        clearIdleActivity(actor, releasePartner = false)
        actor.cooldown = cooldownDuration()
        if (partnerId != null) {
            val partner = actors[partnerId]
            if (partner != null && partner.talkWith == actor.id) {
                clearIdleActivity(partner, releasePartner = false)
                partner.cooldown = cooldownDuration()
            }
        }
    }

    private fun clearIdleActivity(actor: Actor, releasePartner: Boolean) {
        val partnerId = actor.talkWith
        actor.activity = ACT_NONE
        actor.activityHold = 0f
        actor.activityDest = null
        actor.pretendDesk = null
        actor.talkWith = null
        actor.socialBubble = null
        actor.bubbleTimer = 0f
        actor.confMode = ""
        actor.chatStep = 0
        actor.chatLead = false
        actor.lastChatLine = ""
        actor.banterToken = 0
        if (actor.status == "idle") {
            actor.sitting = false
        }
        if (releasePartner && partnerId != null) {
            val partner = actors[partnerId]
            if (partner != null && partner.talkWith == actor.id) {
                clearIdleActivity(partner, releasePartner = false)
                partner.cooldown = cooldownDuration() * 0.5f
            }
        }
    }

    private fun evictPretendersFromBusyDesks(agents: List<SimAgent>) {
        val busy = agents.mapNotNull { agent ->
            if (agent.status != "idle") agent.deskIndex else null
        }.toSet()
        if (busy.isEmpty()) {
            return
        }
        for (actor in actors.values) {
            val desk = actor.pretendDesk ?: continue
            if (desk in busy) {
                clearIdleActivity(actor, releasePartner = false)
                actor.cooldown = 2f
            }
        }
    }

    private fun vacantDesks(): List<Int> {
        val busy = HashSet<Int>()
        for (actor in actors.values) {
            if (actor.status != "idle" && actor.deskIndex != null) {
                busy.add(actor.deskIndex!!)
            }
            if (actor.activity == ACT_PRETEND && actor.pretendDesk != null) {
                busy.add(actor.pretendDesk!!)
            }
        }
        return (0 until OfficeMap.deskCount).filter { it !in busy }
    }

    /**
     * Loveseat seat slots still free. An agent reserves their [Actor.activityDest]
     * as soon as they pick sofa, so a second dozer cannot path onto the same cushion.
     */
    private fun freeSofaSlots(): List<Cell> {
        val taken = HashSet<Cell>()
        for (actor in actors.values) {
            if (actor.activity == ACT_SOFA) {
                actor.activityDest?.let { taken.add(it) }
            }
        }
        return OfficeMap.sofaSpan.filter { it !in taken }
    }

    /** Beanbag is a single cushion. Reserved as soon as someone picks it. */
    private fun beanbagFree(): Boolean {
        return actors.values.none { it.activity == ACT_BEANBAG }
    }

    /** Pick a free lounge tile away from the actor's current cell when possible. */
    private fun wanderSpot(actor: Actor): Cell {
        val here = Cell(
            actor.x.roundToInt().coerceIn(0, OfficeMap.width - 1),
            actor.y.roundToInt().coerceIn(0, OfficeMap.height - 1),
        )
        val options = OfficeMap.lounge.filter { it !in blocked && it != here }
        return options.randomOrNull(random)
            ?: OfficeMap.lounge.filter { it !in blocked }.randomOrNull(random)
            ?: OfficeMap.lounge.first()
    }

    /**
     * Desk sit/type work moods: rotate emoji/text bubbles at a calm rate while
     * the agent is seated on a real task. Between beats, [bubbleFor] falls back
     * to the status icon (···, </>, ★, …).
     */
    private fun tickWorkMood(actor: Actor, dt: Float) {
        if (actor.status !in seatedStatuses) {
            return
        }
        if (actor.bubbleTimer > 0f) {
            actor.bubbleTimer = (actor.bubbleTimer - dt).coerceAtLeast(0f)
            if (actor.bubbleTimer == 0f) {
                actor.socialBubble = null
            }
            return
        }
        // Only once seated at the desk — walking-to-chair stays bubble-quiet.
        if (!actor.sitting) {
            return
        }
        if (random.nextFloat() < WORK_MOOD_PER_SEC * dt) {
            when (random.nextInt(4)) {
                0, 1 -> showBubble(actor, WORK_MOOD_BUBBLES.random(random))
                2 -> if (actor.duty.isNotEmpty()) say(actor, BanterBook.dutyLines) else say(actor, BanterBook.work)
                else -> say(actor, BanterBook.work)
            }
        }
    }

    /**
     * One speaker at a time, from a shared script, so the pair reads as a conversation.
     * About one line in twenty is also sent to that agent's model; the canned line
     * stays up until a short reply replaces it.
     */
    private fun tickChatTurn(actor: Actor) {
        val partner = actor.talkWith?.let { actors[it] }
        if (partner == null || partner.activity != ACT_CHAT || partner.talkWith != actor.id) {
            finishIdleActivity(actor)
            return
        }
        if (actor.bubbleTimer > 0f || !isChatTurn(actor)) {
            return
        }
        speakChatLine(actor, partner)
    }

    private fun isChatTurn(actor: Actor): Boolean {
        val even = actor.chatStep % 2 == 0
        return if (actor.chatLead) even else !even
    }

    private fun speakChatLine(actor: Actor, partner: Actor) {
        if (actor.chatLines.isNotEmpty() && actor.chatStep >= actor.chatLines.size) {
            actor.chatLines = emptyList()
            partner.chatLines = emptyList()
            actor.chatStep = 0
            partner.chatStep = 0
        }
        if (actor.chatLines.isEmpty()) ensureChatLine(actor, partner)
        val line = if (actor.chatLines.isNotEmpty()) actor.chatLines[actor.chatStep] else officeChatExchanges[actor.chatScript][actor.chatStep]
        memory.note(line)
        actor.chatStep += 1
        partner.chatScript = actor.chatScript
        partner.chatStep = actor.chatStep
        // Previous beat is over, so a late model reply must not talk over the new line.
        actor.banterToken = 0
        partner.banterToken = 0
        // One short completion at a time. Other lines stay canned while one is in flight.
        val waitForModel = actors.values.none { it.banterToken != 0L } && llmGate()
        val seconds = if (waitForModel) 5.5f else 1.8f + random.nextFloat() * 1.0f
        showBubble(actor, line, seconds, force = true)
        actor.lastChatLine = line
        partner.socialBubble = null
        partner.bubbleTimer = actor.bubbleTimer
        if (!waitForModel) {
            return
        }
        val token = ++banterSerial
        actor.banterToken = token
        banterAsks.add(
            BanterAsk(
                agentId = actor.id,
                name = actor.name,
                partnerName = partner.name,
                heard = partner.lastChatLine,
                token = token,
            ),
        )
    }

    private fun ensureChatLine(actor: Actor, partner: Actor) {
        val scripts = officeChatExchanges
        if (actor.chatScript !in scripts.indices) {
            actor.chatScript = random.nextInt(scripts.size)
            partner.chatScript = actor.chatScript
            actor.chatStep = 0
            partner.chatStep = 0
        }
        if (actor.chatStep < scripts[actor.chatScript].size) {
            return
        }
        var next = random.nextInt(scripts.size)
        if (scripts.size > 1 && next == actor.chatScript) {
            next = (next + 1) % scripts.size
        }
        actor.chatScript = next
        partner.chatScript = next
        actor.chatStep = 0
        partner.chatStep = 0
    }

    private fun pulseBubble(actor: Actor) {
        val options = when (actor.activity) {
            ACT_FAN -> officeFanInspectLines
            ACT_COFFEE -> COFFEE_BUBBLES
            ACT_FRIDGE -> FRIDGE_BUBBLES
            ACT_SOFA, ACT_BEANBAG -> SOFA_BUBBLES
            ACT_PRETEND -> PRETEND_BUBBLES
            ACT_MEETING -> when {
                actor.inTaskforce -> BanterBook.meeting
                actor.confMode == CONF_CALL -> BanterBook.onlineCall
                actor.confMode == CONF_FOCUS -> BanterBook.focusWork
                else -> MEETING_BUBBLES
            }
            else -> EMOTION_BUBBLES
        }
        val near = nearbyPropLines(actor)
        val pool = when {
            actor.activity == ACT_MEETING -> options
            near != null && random.nextInt(2) == 0 -> near
            random.nextInt(6) == 0 -> daypartLines().ifEmpty { options }
            options === EMOTION_BUBBLES && random.nextBoolean() -> BanterBook.idle
            else -> options
        }
        if (pool === options && options !== BanterBook.meeting && options !== BanterBook.onlineCall && options !== BanterBook.focusWork) showBubble(actor, options.random(random)) else say(actor, pool)
    }

    private fun showBubble(
        actor: Actor,
        text: String,
        seconds: Float = BanterBook.seconds(text) * (0.85f + random.nextFloat() * 0.3f),
        force: Boolean = false,
    ): Boolean {
        // Keep the map readable: at most MAX_BUBBLES speech lines at once.
        if (!force && actor.socialBubble == null) {
            val open = actors.values.count { it !== actor && it.socialBubble != null && it.bubbleTimer > 0f }
            if (open >= MAX_BUBBLES) return false
        }
        if (actor.socialBubble != text) actor.bubbleAge = 0f
        actor.socialBubble = text
        actor.bubbleTimer = seconds
        return true
    }

    private fun daypartLines(): List<String> {
        val ms = nowMs()
        if (ms < 0L) return emptyList()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return BanterBook.daypart(cal.get(java.util.Calendar.HOUR_OF_DAY))
    }

    private fun nearbyPropLines(actor: Actor): List<String>? {
        var best: List<String>? = null
        var bestD = 1.8
        fun consider(kind: String, x: Int, y: Int) {
            val lines = BanterBook.props[kind] ?: BanterBook.props[kind.substringBefore('_')] ?: return
            val d = hypot((actor.x - x).toDouble(), (actor.y - y).toDouble())
            if (d < bestD) { bestD = d; best = lines }
        }
        for (p in OfficeMap.props) consider(p.kind, p.x, p.y)
        for ((c, k) in placedKinds) consider(k, c.x, c.y)
        return best
    }

    /**
     * Idle-mode social life without a full meetup: greet/react when another
     * idle agent is nearby, otherwise pop a solo emotion bubble.
     * Uses the existing speech-bubble field — no new sprites.
     */
    private fun tryAmbientSocial(actor: Actor, dt: Float) {
        if (actor.bubbleTimer > 0f) {
            return
        }
        // Active chat hold already refreshes via pulseBubble.
        if (actor.activity == ACT_CHAT && actor.activityHold > 0f) {
            return
        }
        val near = findNearbyIdle(actor, NEARBY_RADIUS)
        if (near != null) {
            // One initiator per pair so both don't greet on the same roll.
            if (actor.id <= near.id && random.nextFloat() < NEARBY_GREET_PER_SEC * dt) {
                showBubble(actor, GREET_BUBBLES.random(random))
                if (near.bubbleTimer <= 0f) {
                    showBubble(near, REACT_BUBBLES.random(random))
                }
                return
            }
        }
        if (actor.activity == ACT_NONE || actor.activity == ACT_WANDER) {
            if (random.nextFloat() < IDLE_EMOTE_PER_SEC * dt) {
                showBubble(actor, EMOTION_BUBBLES.random(random))
            }
        }
    }

    private fun findNearbyIdle(actor: Actor, radius: Float): Actor? {
        var best: Actor? = null
        var bestDist = radius
        for (other in actors.values) {
            if (other.id == actor.id || other.status != "idle") {
                continue
            }
            // Skip the agent we are already mid-meetup with (pair chat handles that).
            if (actor.activity == ACT_CHAT && actor.talkWith == other.id) {
                continue
            }
            val d = hypot((other.x - actor.x).toDouble(), (other.y - actor.y).toDouble()).toFloat()
            if (d < bestDist) {
                bestDist = d
                best = other
            }
        }
        return best
    }

    private fun holdDuration(activity: String): Float = when (activity) {
        ACT_CHAT -> 7.0f + random.nextFloat() * 5.0f       // ~7–12s standing conversation
        ACT_COFFEE -> 2.5f + random.nextFloat() * 2.0f     // ~2.5–4.5s
        ACT_FRIDGE -> 3.2f + random.nextFloat() * 2.4f     // ~3.2–5.6s snack
        ACT_SOFA, ACT_BEANBAG -> 5.0f + random.nextFloat() * 4.0f // ~5–9s
        ACT_PRETEND -> 6.0f + random.nextFloat() * 3.5f    // ~6–9.5s (+2s desk dwell)
        ACT_WANDER -> 1.2f + random.nextFloat() * 1.8f     // ~1.2–3s pause
        ACT_MEETING -> 10.0f + random.nextFloat() * 10.0f  // ~10–20s focus work / online call
        else -> 2.5f
    }

    /** Gap between idle activities; shorter so agents walk / switch more often. */
    private fun cooldownDuration(): Float = 2f + random.nextFloat() * 3.5f  // ~2–5.5s

    private fun weightedPick(choices: List<Pair<String, Int>>): String? {
        if (choices.isEmpty()) {
            return null
        }
        val total = choices.sumOf { it.second }
        var roll = random.nextInt(total)
        for ((name, weight) in choices) {
            roll -= weight
            if (roll < 0) {
                return name
            }
        }
        return choices.last().first
    }

    private fun bubbleFor(actor: Actor): String? {
        if (actor.socialBubble != null && actor.bubbleTimer > 0f) {
            return actor.socialBubble
        }
        return when (actor.status) {
            "thinking" -> "···"
            "writing" -> "</>"
            "done" -> "★"
            "waiting" -> "!"
            "error" -> "X"
            else -> null
        }
    }

    /**
     * Grid cell the body is standing on. A seat is allowed when it is the goal.
     * Any other solid tile is swapped for a free neighbor so a retarget can leave it.
     */
    private fun anchorCell(actor: Actor, dest: Cell): Cell {
        val rounded = Cell(
            actor.x.roundToInt().coerceIn(0, OfficeMap.width - 1),
            actor.y.roundToInt().coerceIn(0, OfficeMap.height - 1),
        )
        if (rounded !in blocked || rounded == dest) {
            return rounded
        }
        val neighbors = listOf(
            Cell(rounded.x, rounded.y - 1),
            Cell(rounded.x + 1, rounded.y),
            Cell(rounded.x, rounded.y + 1),
            Cell(rounded.x - 1, rounded.y),
        )
        return neighbors.firstOrNull { OfficeMap.inBounds(it) && it !in blocked } ?: rounded
    }

    private fun step(actor: Actor, dt: Float) {
        actor.frameClock += dt
        val next = actor.path.firstOrNull() ?: return
        var tx = next.x.toFloat()
        var ty = next.y.toFloat()
        val dx0 = tx - actor.x
        val dy0 = ty - actor.y
        // One axis at a time, so a retarget cannot cut the corner of a desk.
        if (abs(dx0) > 0.02f && abs(dy0) > 0.02f) {
            if (abs(dx0) >= abs(dy0)) ty = actor.y else tx = actor.x
        }
        val dx = tx - actor.x
        val dy = ty - actor.y
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val travel = 2.6f * dt
        val arrived = distance <= travel || distance == 0f
        val nx = if (arrived) tx else actor.x + dx / distance * travel
        val ny = if (arrived) ty else actor.y + dy / distance * travel
        // Facing follows the PATH SEGMENT cardinal (legOrigin → next waypoint),
        // never the float substep after axis-lock. Residual lateral corrections
        // at the end of a cell used to flick left/right every tile; with the
        // canvas left-flip that read as spinning (back sheet ↔ flipped front).
        // Seated desk agents stay locked toward their laptop — do not retarget
        // facing mid-sit. Sofa/beanbag sleep keeps arrival facing (unused for flip).
        if (!actor.sitting) {
            if (actor.facingHold > 0f) {
                actor.facingHold = (actor.facingHold - dt).coerceAtLeast(0f)
            }
            // Chat face-off overrides path facing once arrived; while walking use path.
            if (actor.activity != ACT_CHAT || actor.path.isNotEmpty()) {
                updateFacingFromPathSegment(actor, next)
            }
        } else if (actor.activity == ACT_MEETING) {
            actor.facing = actor.activityDest?.let { OfficeMap.confSeatFacing(it) } ?: actor.facing
            actor.facingHold = 0f
        } else if (!actor.isNapping()) {
            actor.facing = deskSeatFacing(actor)
            actor.facingHold = 0f
        }
        val leaving = Cell(
            actor.x.roundToInt().coerceIn(0, OfficeMap.width - 1),
            actor.y.roundToInt().coerceIn(0, OfficeMap.height - 1),
        )
        val entering = Cell(
            nx.roundToInt().coerceIn(0, OfficeMap.width - 1),
            ny.roundToInt().coerceIn(0, OfficeMap.height - 1),
        )
        val solid = entering in blocked && entering != actor.dest && entering != leaving
        if (solid) {
            actor.path.clear()
            actor.dest = null
            actor.legOrigin = null
            return
        }
        actor.x = nx
        actor.y = ny
        if (arrived && abs(actor.x - next.x) < 0.02f && abs(actor.y - next.y) < 0.02f) {
            actor.path.removeFirst()
            // Next leg starts at this waypoint — facing only changes when the
            // new segment's cardinal differs, not on every substep.
            actor.legOrigin = next
        }
    }

    /**
     * Facing toward the laptop for a seated desk worker. Prefer [overrideDesk],
     * else pretend-desk, else the actor's assigned desk. Falls back to "left"
     * only when no desk is known (should not happen for sit/type).
     */
    private fun deskSeatFacing(actor: Actor, overrideDesk: Int? = null): String {
        val desk = overrideDesk
            ?: when {
                actor.activity == ACT_PRETEND -> actor.pretendDesk
                else -> actor.deskIndex
            }
        return if (desk != null) OfficeMap.seatFacing(desk) else "left"
    }

    /**
     * Adopt facing from the stable grid leg (origin → next), not motion dx/dy.
     * Hold prevents rapid reface when the path turns or is rebuilt mid-stride.
     */
    private fun updateFacingFromPathSegment(actor: Actor, next: Cell) {
        val origin = actor.legOrigin ?: Cell(
            actor.x.roundToInt().coerceIn(0, OfficeMap.width - 1),
            actor.y.roundToInt().coerceIn(0, OfficeMap.height - 1),
        ).also { actor.legOrigin = it }
        var sdx = next.x - origin.x
        var sdy = next.y - origin.y
        if (sdx == 0 && sdy == 0) {
            return
        }
        // Pathfinding is 4-connected; if both nonzero (off-tile first leg), keep
        // the dominant axis so facing stays one cardinal for the whole approach.
        if (abs(sdx) >= abs(sdy)) {
            sdy = 0
        } else {
            sdx = 0
        }
        if (actor.facingHold > 0f) {
            return
        }
        val nextFacing = facingFromDelta(sdx.toFloat(), sdy.toFloat(), actor.facing)
        if (nextFacing != actor.facing) {
            actor.facing = nextFacing
            actor.facingHold = FACING_HOLD_SEC
        }
    }

    private fun near(actor: Actor, dest: Cell): Boolean {
        return abs(actor.x - dest.x) < 0.08f && abs(actor.y - dest.y) < 0.08f
    }

    private companion object {
        /** Min time a facing pose is held after a change (kills back↔side flicker). */
        const val FACING_HOLD_SEC = 0.45f

        const val ACT_NONE = "none"
        const val ACT_CHAT = "chat"
        const val ACT_COFFEE = "coffee"
        const val ACT_FRIDGE = "fridge"
        const val ACT_SOFA = "sofa"
        const val ACT_BEANBAG = "beanbag"
        const val ACT_PRETEND = "pretend"
        const val ACT_WANDER = "wander"
        const val ACT_FAN = "fan"
        const val ACT_MEETING = "meeting"

        /** Door swing speeds (fraction of the full swing per second). */
        const val ConfDoorOpenPerSec = 3.2f
        const val ConfDoorClosePerSec = 1.8f
        /** ~300 ms to slide open, ~400 ms to close (eased in the canvas). */
        const val BackDoorOpenPerSec = 3.3f
        const val BackDoorClosePerSec = 2.5f
        /** Seconds a departing agent stands in the open doorway before vanishing. */
        const val ExitDoorwayHoldSec = 0.35f

        val MEETING_BUBBLES = listOf(
            "sync?", "agenda", "Q3", "📊", "ship it", "+1", "next?", "notes", "KPI", "🤝",
            "blocker", "roadmap", "ok!", "hmm", "👀", "deadline", "LGTM",
        )

        val seatedStatuses = setOf("thinking", "writing", "done", "error", "waiting")

        /** Pass-by greet/react distance in tiles. */
        const val NEARBY_RADIUS = 2.4f
        /** Expected nearby greet attempts per second when a neighbor is in range. */
        const val NEARBY_GREET_PER_SEC = 0.48f
        /** Solo emotion bubbles per second while idle / wandering. */
        const val IDLE_EMOTE_PER_SEC = 0.30f
        /** Work-mood bubble attempts per second while seated typing (gaps between). */
        const val WORK_MOOD_PER_SEC = 0.20f
        /** Max simultaneous non-chat speech bubbles on the map. */
        const val MAX_BUBBLES = 3
        const val MAX_CONF_SOLO = 2
        const val CONF_FOCUS = "focus"
        const val CONF_CALL = "call"
        const val ModelErrorBubble = "Can't reach model…"

        val COFFEE_BUBBLES = listOf(
            "☕", "sip", "ahh", "···", "mm", "☕♥", "brew", "warm", "fuel", "yay",
            "☕?", "refill", "strong", "latte", "mmm",
        )
        /** Eating + rotating snacks while the fridge door is open. */
        val FRIDGE_BUBBLES = listOf(
            "🍽️", "🍔", "🥪", "🍌", "🍎", "🥤", "🍕", "🥗", "🍪", "🥛",
            "yum", "mmm", "snack", "bite", "nom",
        )
        val SOFA_BUBBLES = listOf(
            "zZz", "Zzz", "···", "nap", "tired", "5min", "soft", "yawn", "zzz",
            "doze", "comfy", "brb", "💤",
        )
        val PRETEND_BUBBLES = listOf(
            "</>", "···", "tap", "hmm", "fix?", "ship", "LGTM", "wip", "bug?", "todo",
            "🔥", "🤔", "focus", "fake it", "look busy",
        )
        /** Idle-mode emotion indicators (same amber bubble renderer). */
        val EMOTION_BUBBLES = listOf(
            "☺", "♪", "♥", "✦", "···", "?", "!", "☕♥",
            "tired", "focus", "bored", "yay", "meh", "hungry", "stretch", "✨",
            "🤔", "😤", "🔥", "👀", "☕?", "😴", "😅", "😌",
            "curious", "restless", "hype", "chill", "snack?",
        )
        val GREET_BUBBLES = listOf(
            "Hi", "Hey", "yo", "sup", "oh!", "hi!", "hey!",
            "howdy", "hola", "heyo", "👋", "psst",
        )
        val REACT_BUBBLES = listOf(
            "!", "lol", "nice", "same", "👀", "huh?", "yep", "ha", "ok!", "?",
            "omg", "true", "mood", "fr", "✨", "😅", "👀!",
        )
        /**
         * Desk sit/type work moods — rotate while agents grind on real tasks.
         * Mix of emoji + short text (fire, thinking, frustrated, focused, coffee…).
         */
        val WORK_MOOD_BUBBLES = listOf(
            "🔥", "🤔", "😤", "☕?", "focus", "ship", "wip", "bug?",
            "hmm", "grind", "almost", "ugh", "✨", "LGTM", "todo", "asap",
            "typed", "coffee!", "stuck", "go!", "👀", "</>", "···", "nice",
            "deep", "one more", "why", "ahh", "💪", "😅", "😤!", "🔥!",
        )
    }
}
