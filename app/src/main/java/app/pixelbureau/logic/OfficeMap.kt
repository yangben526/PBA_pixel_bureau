package app.pixelbureau.logic

data class Cell(val x: Int, val y: Int)

data class Prop(val kind: String, val x: Int, val y: Int)

object OfficeMap {
    /**
     * 13×13 floor (original 12×12 + one row south, one column east) (2-tile padding around the 8×8 desk layout). The glass
     * conference room sits in the bottom-right 4×4; built-ins that used to live
     * there (door, lounge tiles) were relocated so paths stay open.
     */
    const val width = 13
    const val height = 13
    const val deskCount = 8

    /**
     * Two rows of four desks, each with a gap so the tables do not overlap.
     * Chairs sit on the tile in front (x + 1) — desk faces bottom-right / +x,
     * matching the reference (back of chair toward the viewer). Lounge at front.
     * Coordinates are the original 8×8 layout shifted by (+2, +2) into the padded map.
     */
    val desks: List<Cell> = listOf(
        Cell(2, 2),
        Cell(4, 2),
        Cell(6, 2),
        Cell(8, 2),
        Cell(2, 6),
        Cell(4, 6),
        Cell(6, 6),
        Cell(8, 6),
    )

    /** NE corner plant removed (user request); list kept for the plant prop pipeline. */
    val plants: List<Cell> = listOf()
    /** Server on the east aisle above the conference room; solid for pathfinding. */
    val server = Cell(10, 5)
    /** Side table in the front lounge, clear of the desks. */
    val coffee = Cell(6, 9)
    /** The tabletop art also covers the tile behind the machine. */
    val coffeeSpan: List<Cell> = listOf(coffee, Cell(coffee.x, coffee.y - 1))
    /** Front edge of the room, clear of the desk rows. */
    val sofa = Cell(3, 9)

    /** Loveseat spans two tiles along the front wall; the aisle behind stays open. */
    val sofaSpan: List<Cell> = listOf(sofa, Cell(4, 9))
    val door = Cell(5, 11)
    /** Second entrance in the NE (top-right) corner; agents can arrive/leave here too. */
    val backDoor = Cell(width - 1, 0)
    /** Every office entrance (main first). */
    val doors: List<Cell> = listOf(door, backDoor)
    /** Nearest entrance to (x, y) by Manhattan distance (main door wins ties). */
    fun nearestDoor(x: Float, y: Float): Cell =
        doors.minBy { kotlin.math.abs(it.x - x) + kotlin.math.abs(it.y - y) }

    // --- Startup-office flavor props (fill empty floor; keep desk/sofa/coffee/server) ---
    /** Standup whiteboard along the west aisle between desk rows. */
    val whiteboard = Cell(0, 3)
    /** Tall bookshelf on the west wall toward the lounge. */
    val bookshelf = Cell(0, 7)
    /** Orange bean bag left of the loveseat. */
    val beanbag = Cell(1, 8)
    /** Snack fridge footprint remains north of the coffee/pantry desk (coffee at 6,9 / span 6,8). */
    val fridge = Cell(6, 7)

    /**
     * Draw-only half-step toward the coffee desk. The fridge keeps its logical
     * footprint at [fridge] because the intervening coffee span is blocked.
     * Values are screen-axis fractions of tileW/tileH; positive Y draws down.
     */
    val fridgeDrawOffset = PropDrawOffset(dxTileW = -0.45f, dyTileH = 0.45f)

    /**
     * Stand tile for idle fridge visits. Immediately grid-right of [fridge]
     * (desk above, coffee span below; the tile to the left is open now that
     * trash sits in the NW corner). Agents walk here and face the door; the
     * footprint itself stays blocked.
     */
    val fridgeApproach: List<Cell> = listOf(Cell(fridge.x + 1, fridge.y))
    /** Water cooler on the west wall, the tile north of the bookshelf. */
    val watercooler = Cell(0, 6)
    /**
     * Foosball just south of the beanbag. Span stays on +x so the long table
     * matches the art, and it stops at x=1 so the (2,9)/(2,10) aisle stays open.
     */
    val foosball = Cell(0, 10)
    val foosballSpan: List<Cell> = listOf(foosball, Cell(1, 10))
    /** Trash + recycling at the NW tip, clear of the whiteboard and plants. */
    val trash = Cell(0, 0)
    /** Extra leafy plant at the absolute SW tip of the walkable map (0, height-1). */
    val plant2 = Cell(0, height - 1)

    /**
     * How far chair art is drawn back toward its desk (-x).
     * The seat tile stays in front so the path does not enter the desk.
     */
    const val seatTuck = 0.68f

    /**
     * Extra tuck for a seated agent so they sit in the chair (toward the desk)
     * rather than stacking on the same pivot as the chair sprite. Combined with
     * canvas depth (agent under chair), the rear backrest stays toward the
     * camera while the agent peeks toward the laptop.
     */
    const val agentSeatTuck = 0.86f

    /**
     * Vertical screen nest for desk sit/type with seat tuck (fraction of tileH
     * added to foot Y; negative = up onto the chair seat). Larger magnitude lifts
     * the agent clearly onto the chair while working (was -0.26, too subtle).
     * Keeps depth under the backrest; upper body stays above the seat/desk draw
     * order. Sofa sleep uses SleepNudge (untouched).
     */
    const val deskSeatNestTileH = -0.65f

    /** Small screen-x adjustment so desk sit/type agents nest toward the right side of the chair. */
    const val deskSeatNestTileW = 0.12f

    /**
     * Per-cushion screen nudge for sofa sleepers so the curled body rests on the
     * seat surface (not the floor tile under the loveseat).
     * [SleepNudge.dxTileW] shifts screen-x by that fraction of tileW;
     * [SleepNudge.nestTileH] is added to the foot Y (negative = up onto cushions).
     * Tuned for sofa span (3,9)/(4,9) against props/sofa.png, and for the
     * beanbag cushion at (1,8) against props/beanbag.png.
     */
    data class SleepNudge(val dxTileW: Float, val nestTileH: Float)

    /** Screen-space furniture nudge used when visual nesting must not alter pathing. */
    data class PropDrawOffset(val dxTileW: Float, val dyTileH: Float)

    private val sleepNudges: Map<Cell, SleepNudge> = mapOf(
        // Elevated onto seat cushions of magenta-keyed ref sofa (not floor/legs).
        // Right slot is +0.5 tileH lower in iso screen-Y, so nest is ~0.5 more negative.
        Cell(3, 9) to SleepNudge(dxTileW = 0.16f, nestTileH = -0.38f),
        Cell(4, 9) to SleepNudge(dxTileW = -0.16f, nestTileH = -0.88f),
        // Beanbag cushion: lift the curled body onto the seat, not the floor.
        Cell(1, 8) to SleepNudge(dxTileW = 0f, nestTileH = -0.50f),
    )

    fun sleepNudgeFor(x: Float, y: Float): SleepNudge {
        val cell = Cell(kotlin.math.round(x).toInt(), kotlin.math.round(y).toInt())
        sleepNudges[cell]?.let { return it }
        val nearest = sleepNudges.keys.minByOrNull {
            kotlin.math.abs(it.x - cell.x) + kotlin.math.abs(it.y - cell.y)
        }
        return nearest?.let { sleepNudges[it] } ?: SleepNudge(0f, -0.38f)
    }

    /**
     * Open tiles in the lounge (and padded front/side aisles). Idle people
     * linger / meet here, not through the desks.
     */
    val lounge: List<Cell> = listOf(
        Cell(2, 8),
        Cell(5, 8),
        Cell(7, 8),
        Cell(2, 9),
        Cell(7, 9),  // open beside the coffee desk now that the fridge is north
        // (1,8) is the beanbag footprint — not a wander tile.
        Cell(1, 9),
        // (1,10) is the foosball span beside the beanbag — not a wander tile.
        Cell(2, 10),
        Cell(3, 10),
        Cell(4, 10),
        Cell(5, 10),
        Cell(7, 10),
        // South edge in front of the lounge (conference room owns x=9..12, y=9..12).
        Cell(2, 11),
        Cell(3, 11),
        Cell(4, 11),
        Cell(6, 11),
        Cell(7, 11),
        Cell(8, 11),  // conference door approach (confDoorOutside)
        door,
    )

    /**
     * Walkable tiles beside the coffee machine. Idle coffee visits stand here
     * (the machine footprint itself stays blocked).
     */
    val coffeeApproach: List<Cell> = listOf(
        Cell(5, 8),
        Cell(5, 9),
        Cell(7, 8),
        Cell(7, 9),
        Cell(5, 10),
        Cell(6, 10),
        Cell(7, 10),
    )

    // --- Glass conference room (bottom-right of the 13×13, 4×4) ---
    /** Interior tiles x in [confX0, confX0+3], y in [confY0, confY0+3]. */
    const val confX0 = 9
    const val confY0 = 9
    const val confSize = 4
    val confCells: List<Cell> = buildList {
        for (y in confY0 until confY0 + confSize) for (x in confX0 until confX0 + confSize) add(Cell(x, y))
    }

    fun inConf(cell: Cell): Boolean =
        cell.x in confX0 until confX0 + confSize && cell.y in confY0 until confY0 + confSize

    /** Door sits on the west (office-facing) wall between [confDoorOutside] and [confDoorInside]. */
    val confDoorInside = Cell(confX0, confY0 + 2)
    val confDoorOutside = Cell(confX0 - 1, confY0 + 2)

    /** Long 2×2 wooden table in the middle of the room (solid). */
    val confTable: List<Cell> = listOf(
        Cell(confX0 + 1, confY0 + 1), Cell(confX0 + 2, confY0 + 1),
        Cell(confX0 + 1, confY0 + 2), Cell(confX0 + 2, confY0 + 2),
    )

    /** Potted plant in the front (south-east) corner (solid). */
    val confPlant = Cell(confX0 + 3, confY0 + 3)

    /**
     * Meeting chairs around the table. Walkable seat tiles (like desk chairs);
     * [confSeatFacing] is the canvas facing that looks at the table.
     */
    val confSeats: List<Cell> = listOf(
        Cell(confX0 + 1, confY0 + 3), Cell(confX0 + 2, confY0 + 3),   // south row, face -y
        Cell(confX0 + 3, confY0 + 1), Cell(confX0 + 3, confY0 + 2),   // east end, face -x
    )

    /**
     * Where the 5th taskforce member stands: the top (north, screen-back) side
     * of the table, where the two back chairs used to be. Faces the table.
     */
    val confStandSpot = Cell(confX0 + 1, confY0)

    /** Lift (in tileH) that puts a conference sitter's hips on the chair seat, like [deskSeatNestTileH]. */
    const val confSeatNestTileH = -1.25f

    /** Drawn chair position: nudged 0.22 tile toward the table. Sitters snap here too. */
    fun confChairPos(seat: Cell): Pair<Float, Float> = when {
        seat.y == confY0 -> seat.x.toFloat() to seat.y + 0.22f
        seat.y == confY0 + 3 -> seat.x.toFloat() to seat.y - 0.22f
        else -> seat.x - 0.22f to seat.y.toFloat()
    }

    /** The conference chair a sit/type actor at (x, y) occupies, if any. */
    fun confSeatAt(x: Float, y: Float): Cell? =
        confSeats.firstOrNull { kotlin.math.abs(it.x - x) < 0.35f && kotlin.math.abs(it.y - y) < 0.35f }

    /** Default sheets face +x (screen down-right); "left" mirrors them. */
    fun confSeatFacing(seat: Cell): String = when {
        seat.y == confY0 -> "left"
        seat.y == confY0 + 3 -> "right"
        else -> "left"
    }

    /**
     * Glass walls are edges, not tiles: true when a 4-way step between [a] and
     * [b] crosses the conference-room wall anywhere except the door gap.
     */
    fun wallBetween(a: Cell, b: Cell): Boolean {
        if (inConf(a) == inConf(b)) return false
        val pair = setOf(a, b)
        return pair != setOf(confDoorInside, confDoorOutside)
    }

    val props: List<Prop> = buildList {
        desks.forEach { add(Prop("desk", it.x, it.y)) }
        for (index in desks.indices) {
            val seat = chair(index)
            add(Prop("chair", seat.x, seat.y))
        }
        plants.forEach { add(Prop("plant", it.x, it.y)) }
        add(Prop("server", server.x, server.y))
        add(Prop("coffee", coffee.x, coffee.y))
        add(Prop("sofa", sofa.x, sofa.y))
        add(Prop("whiteboard", whiteboard.x, whiteboard.y))
        add(Prop("bookshelf", bookshelf.x, bookshelf.y))
        add(Prop("beanbag", beanbag.x, beanbag.y))
        add(Prop("fridge", fridge.x, fridge.y))
        add(Prop("watercooler", watercooler.x, watercooler.y))
        add(Prop("foosball", foosball.x, foosball.y))
        add(Prop("trash", trash.x, trash.y))
        add(Prop("plant2", plant2.x, plant2.y))
        // Conference room: walls/table are drawn by the canvas; chairs reuse sprites.
        // The corner plant is drawn only for the oakwood room (see OfficeCanvas).
        add(Prop("conf_table", confTable[0].x, confTable[0].y))
        confSeats.forEach { add(Prop("conf_chair", it.x, it.y)) }
        add(Prop("plant", confPlant.x, confPlant.y))
    }

    /**
     * Seat tile in front of the desk (+x / bottom-right). Chair art shows the back
     * toward the viewer. Seat tiles are not path-blocked so odd columns stay open
     * as corridors; agents still path to them as destinations.
     */
    fun chair(deskIndex: Int): Cell {
        val desk = desks[deskIndex]
        return Cell(desk.x + 1, desk.y)
    }

    /**
     * Facing a seated desk worker should use to look at the laptop on [deskIndex].
     * Derived from seat → desk (computer) on the grid — not a hard-coded side —
     * so layout changes that move the chair stay correct. Current chairs sit at
     * desk+(1,0), so this is "left" (-x) for every seat.
     */
    fun seatFacing(deskIndex: Int): String {
        val desk = desks[deskIndex]
        val seat = chair(deskIndex)
        return facingFromDelta(
            (desk.x - seat.x).toFloat(),
            (desk.y - seat.y).toFloat(),
            current = "left",
        )
    }

    /**
     * Whether the default (right-facing) sit/type sheet must be mirrored so a
     * seated worker looks at the laptop on [deskIndex]. Current layout seats
     * are desk+(1,0) → seatFacing "left" → mirror.
     */
    fun seatMirrorX(deskIndex: Int): Boolean = seatFacing(deskIndex) == "left"

    fun blocked(): Set<Cell> = buildSet {
        desks.forEach { desk ->
            add(desk)
            // One tile behind (-y) stays solid for the raised isometric footprint,
            // while odd-x seat tiles stay walkable corridors beside each desk.
            val behind = Cell(desk.x, desk.y - 1)
            if (inBounds(behind)) add(behind)
        }
        // Chairs intentionally omitted: vacant seats are walkable corridors.
        plants.forEach { add(it) }
        add(server)
        coffeeSpan.forEach { add(it) }
        sofaSpan.forEach { add(it) }
        add(whiteboard)
        add(bookshelf)
        add(beanbag)
        add(fridge)
        add(watercooler)
        foosballSpan.forEach { add(it) }
        add(trash)
        add(plant2)
        confTable.forEach { add(it) }
        add(confPlant)
    }

    fun inBounds(cell: Cell): Boolean {
        return cell.x in 0 until width && cell.y in 0 until height
    }

    /**
     * Built-in props standing on the map, grouped by kind with counts, for the
     * floor Inventories list. Conference pieces collapse into "conf_room".
     */
    fun builtInInventory(): List<Pair<String, Int>> =
        props.map { if (it.kind.startsWith("conf_")) "conf_room" else it.kind }
            .groupingBy { it }.eachCount()
            .map { (k, n) -> k to if (k == "conf_room") 1 else n }
            .sortedBy { it.first }
}
