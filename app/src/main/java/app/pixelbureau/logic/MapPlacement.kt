package app.pixelbureau.logic

import kotlin.math.abs

/**
 * One bought shop item standing on the map. [slot] is the persisted key
 * (`<itemId>#<n>`, n = copy index), [kind] the prop sprite (`props/<kind>`).
 * [cell] is the anchor tile; [cells] is the whole blocked footprint.
 */
data class PlacedProp(val slot: String, val itemId: String, val kind: String, val cell: Cell) {
    val cells: List<Cell> get() = MapPlacement.footprint(kind, cell)
}

/**
 * Where shop purchases go on the office floor.
 *
 * A cell is free only when it is in bounds, not part of any built-in prop
 * footprint ([OfficeMap.blocked]: desks + the tile behind, sofa, coffee span,
 * fridge, …), not an agent seat / interaction spot ([reserved]: desk chairs,
 * coffee + fridge approach, lounge wander tiles, door, server fan stand) and
 * not under another placed purchase. On top of that, a candidate is rejected
 * when blocking it would cut the agent pathfinder: after placement every
 * walkable tile must still be reachable from the door ([keepsPathsOpen]),
 * which covers every chair and interaction spot, and every nap cushion
 * (sofa tiles, beanbag) must keep a reachable neighbour.
 *
 * Among the valid cells the finder prefers the back walls (y = 0 / x = 0),
 * then the front edges for short items, next to other decor, away from desk
 * chairs, and not hidden behind tall furniture. Pure Kotlin so it is
 * unit-testable.
 */
object MapPlacement {
    private val dirs4 = listOf(Cell(0, -1), Cell(1, 0), Cell(0, 1), Cell(-1, 0))

    /** Multi-tile kinds mirror their built-in map footprints; everything else is one tile. */
    fun footprint(kind: String, anchor: Cell): List<Cell> = when (kind) {
        "sofa", "foosball" -> listOf(anchor, Cell(anchor.x + 1, anchor.y))
        "coffee", "desk_top" -> listOf(anchor, Cell(anchor.x, anchor.y - 1))
        else -> listOf(anchor)
    }

    /** Low props that disappear behind desks / tall furniture, so they avoid those spots. */
    private val shortKinds = setOf(
        "pouf", "storage_box", "trash", "cactus", "beanbag", "chair", "plant", "plant2", "plant_top",
        "foosball", "sofa", "bike",
    )

    /** Tall props that would hide agents if parked on the front (camera-side) edges. */
    private val tallKinds = setOf(
        "vending", "arcade", "clock", "floor_lamp", "bookshelf", "whiteboard", "server", "fridge",
        "neon_sign", "watercooler", "aquarium", "guitar", "globe", "filing_cabinet", "desk_top", "coffee",
    )

    /** Arcade + vending stick side by side (same wall / adjacent cells). */
    private val buddyOf = mapOf("arcade" to "vending", "vending" to "arcade")

    private val seats: Set<Cell> by lazy { OfficeMap.desks.indices.map { OfficeMap.chair(it) }.toSet() }

    /** Tiles agents stand on to do something. Never built on. */
    val reserved: Set<Cell> by lazy {
        buildSet {
            addAll(seats)
            addAll(OfficeMap.coffeeApproach)
            addAll(OfficeMap.fridgeApproach)
            addAll(OfficeMap.lounge)
            addAll(OfficeMap.doors)
            // Keep the back door's inward step free too.
            add(Cell(OfficeMap.backDoor.x, OfficeMap.backDoor.y + 1))
            // Server fan inspection stand tiles (OfficeSim.fanStandTile).
            add(Cell(OfficeMap.server.x - 1, OfficeMap.server.y))
            add(Cell(OfficeMap.server.x, OfficeMap.server.y + 1))
            // Glass conference room: every interior tile plus the door approach.
            addAll(OfficeMap.confCells.filter { it !in OfficeMap.blocked() })
            add(OfficeMap.confDoorOutside)
        }.filter { OfficeMap.inBounds(it) }.toSet()
    }

    /** Blocked cushions agents lie on; they need one reachable neighbour. */
    private val napSpots: List<Cell> by lazy { OfficeMap.sofaSpan + OfficeMap.beanbag }

    /** Built-in props that read as decor (not work furniture) for the "decor near decor" bonus. */
    private val decorCells: Set<Cell> by lazy {
        OfficeMap.blocked() - OfficeMap.desks.toSet() -
            OfficeMap.desks.map { Cell(it.x, it.y - 1) }.toSet()
    }

    /** Built-in obstacles plus every placed purchase. This is what OfficeSim walks around. */
    fun blockedWith(placed: Collection<PlacedProp>): Set<Cell> =
        OfficeMap.blocked() + placed.flatMap { it.cells }

    /**
     * True when, with [blocked] solid, every walkable tile is reachable from the
     * door (so every chair / interaction spot is) and every nap cushion keeps a
     * reachable neighbour. Same 4-way moves as [findPath].
     */
    fun keepsPathsOpen(blocked: Set<Cell>): Boolean {
        val start = OfficeMap.door
        if (start in blocked) return false
        val seen = HashSet<Cell>()
        val queue = ArrayDeque<Cell>()
        seen.add(start)
        queue.add(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (d in dirs4) {
                val n = Cell(c.x + d.x, c.y + d.y)
                if (!OfficeMap.inBounds(n) || n in blocked || OfficeMap.wallBetween(c, n) || !seen.add(n)) continue
                queue.add(n)
            }
        }
        for (y in 0 until OfficeMap.height) {
            for (x in 0 until OfficeMap.width) {
                val c = Cell(x, y)
                if (c !in blocked && c !in seen) return false
            }
        }
        if (reserved.any { it in blocked || it !in seen }) return false
        return napSpots.all { spot -> dirs4.any { Cell(spot.x + it.x, spot.y + it.y) in seen } }
    }

    /** Footprint check only: in bounds, not solid, not reserved. */
    fun footprintFree(cells: List<Cell>, occupied: Set<Cell>): Boolean =
        cells.all { OfficeMap.inBounds(it) && it !in occupied && it !in reserved }

    /** Best valid anchor for a new [kind], or null when the floor has no room left. */
    fun findCell(kind: String, placed: Collection<PlacedProp>): Cell? {
        val occupied = blockedWith(placed)
        val placedCells = placed.flatMap { it.cells }.toSet()
        val buddyKind = buddyOf[kind]
        val buddyAnchors = if (buddyKind == null) emptyList() else
            placed.filter { it.kind == buddyKind }.map { it.cell }
        val candidates = buildList {
            for (y in 0 until OfficeMap.height) {
                for (x in 0 until OfficeMap.width) add(Cell(x, y))
            }
        }.filter { footprintFree(footprint(kind, it), occupied) }

        fun pick(from: List<Cell>): Cell? = from
            .sortedWith(
                compareByDescending<Cell> { score(kind, it, occupied, placedCells, buddyAnchors) }
                    .thenBy { it.y }
                    .thenBy { it.x },
            )
            .firstOrNull { keepsPathsOpen(occupied + footprint(kind, it)) }

        // If the buddy is already down, force an orthogonally adjacent cell when any is valid.
        if (buddyAnchors.isNotEmpty()) {
            val adj = candidates.filter { c ->
                buddyAnchors.any { b -> abs(c.x - b.x) + abs(c.y - b.y) == 1 }
            }
            pick(adj)?.let { return it }
        } else if (buddyKind != null) {
            // Neither placed yet: prefer a cell that still has a free neighbour for the buddy.
            val pairReady = candidates.filter { c ->
                dirs4.any { d ->
                    val n = Cell(c.x + d.x, c.y + d.y)
                    footprintFree(footprint(buddyKind, n), occupied + footprint(kind, c)) &&
                        keepsPathsOpen(occupied + footprint(kind, c) + footprint(buddyKind, n))
                }
            }
            pick(pairReady)?.let { return it }
        }
        return pick(candidates)
    }

    private fun score(
        kind: String,
        anchor: Cell,
        occupied: Set<Cell>,
        placedCells: Set<Cell>,
        buddyAnchors: List<Cell> = emptyList(),
    ): Int {
        val cells = footprint(kind, anchor)
        val tall = kind in tallKinds
        val short = kind in shortKinds
        var s = 0
        // Strong pull toward the arcade/vending buddy (side-by-side on a wall).
        if (buddyAnchors.any { abs(anchor.x - it.x) + abs(anchor.y - it.y) == 1 }) s += 80
        // Vending prefers the arcade's screen-left wall tile ((x-1, 0) on the
        // north wall, (0, y+1) on the west wall).
        if (kind == "vending" && buddyAnchors.any {
                (it.y == 0 && anchor == Cell(it.x - 1, 0)) || (it.x == 0 && anchor == Cell(0, it.y + 1))
            }) s += 40
        for (c in cells) {
            val back = c.x == 0 || c.y == 0
            val front = c.x == OfficeMap.width - 1 || c.y == OfficeMap.height - 1
            s += when {
                back -> 30
                front -> if (tall) 6 else 20
                else -> -12
            }
            // Decor near decor (8-neighbourhood), capped.
            var near = 0
            for (dx in -1..1) for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val n = Cell(c.x + dx, c.y + dy)
                if (n in cells) continue
                if (n in decorCells || n in placedCells) near++
            }
            s += minOf(near, 3) * 4
            // Keep elbow room around desk chairs.
            if (dirs4.any { Cell(c.x + it.x, c.y + it.y) in seats }) s -= 10
            // Iso occlusion: tiles at +x, +y, +x+y draw in front of this one.
            val front3 = listOf(Cell(c.x + 1, c.y), Cell(c.x, c.y + 1), Cell(c.x + 1, c.y + 1))
            val hidden = front3.count { it in occupied && it !in cells }
            s -= hidden * if (short) 9 else 3
        }
        // Mild preference for the far side of the room from the door.
        s += (abs(anchor.x - OfficeMap.door.x) + abs(anchor.y - OfficeMap.door.y)) / 3
        return s
    }

    /**
     * Rebuilds the placed list from inventory + stored cells. Stored cells that
     * are no longer valid (layout changed, overlap) and owned copies with no cell
     * yet (bought before placement existed) are re-allocated in slot order.
     * Copies that still do not fit stay in inventory, unplaced.
     * Returns the list plus the slot → cell map to persist.
     */
    fun resolve(
        ownedCopies: List<Pair<String, String>>, // slot to kind, in buy order
        stored: Map<String, Cell>,
        itemOf: (String) -> String,
    ): Pair<List<PlacedProp>, Map<String, Cell>> {
        val placed = ArrayList<PlacedProp>()
        val pending = ArrayList<Pair<String, String>>()
        for ((slot, kind) in ownedCopies) {
            val cell = stored[slot]
            if (cell != null) {
                val occupied = blockedWith(placed)
                val fp = footprint(kind, cell)
                if (footprintFree(fp, occupied) && keepsPathsOpen(occupied + fp)) {
                    placed.add(PlacedProp(slot, itemOf(slot), kind, cell))
                    continue
                }
            }
            pending.add(slot to kind)
        }
        for ((slot, kind) in pending) {
            val cell = findCell(kind, placed) ?: continue
            placed.add(PlacedProp(slot, itemOf(slot), kind, cell))
        }
        return placed to placed.associate { it.slot to it.cell }
    }

}
