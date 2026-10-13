package app.pixelbureau.logic

private val directions = listOf(
    Cell(0, -1),
    Cell(1, 0),
    Cell(0, 1),
    Cell(-1, 0),
)

/**
 * Grid path from [start] to [goal]. The result includes [goal] and skips [start].
 * An empty list means "already there" when the two cells match, or "no route".
 * Callers that need to tell those apart should compare the cells first.
 * [start] and [goal] are walkable even when they sit in [blocked].
 * Steps that cross a wall edge ([OfficeMap.wallBetween], the glass conference
 * room) are never taken, so the room is entered only through its door.
 */
fun findPath(
    width: Int,
    height: Int,
    blocked: Set<Cell>,
    start: Cell,
    goal: Cell,
): List<Cell> {
    if (start == goal) {
        return emptyList()
    }
    if (!inside(width, height, start) || !inside(width, height, goal)) {
        return emptyList()
    }
    val queue = ArrayDeque<Cell>()
    val previous = HashMap<Cell, Cell>()
    queue.add(start)
    previous[start] = start
    var found = false
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current == goal) {
            found = true
            break
        }
        for (step in directions) {
            val next = Cell(current.x + step.x, current.y + step.y)
            if (next in previous || !inside(width, height, next) || OfficeMap.wallBetween(current, next)) {
                continue
            }
            val passable = next == goal || next == start || next !in blocked
            if (!passable) {
                continue
            }
            previous[next] = current
            queue.add(next)
        }
    }
    if (!found) {
        return emptyList()
    }
    val path = ArrayList<Cell>()
    var cursor = goal
    while (cursor != start) {
        path.add(cursor)
        cursor = previous.getValue(cursor)
    }
    path.reverse()
    return path
}

private fun inside(width: Int, height: Int, cell: Cell): Boolean {
    return cell.x in 0 until width && cell.y in 0 until height
}
