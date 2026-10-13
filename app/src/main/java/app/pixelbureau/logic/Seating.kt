package app.pixelbureau.logic

data class SeatInput(val id: String, val archived: Boolean, val sortOrder: Int)

data class Seat(val id: String, val archived: Boolean, val deskIndex: Int?)

/**
 * Seats active (non-archived) people up to [headcount].
 * Firing removes someone entirely; empty paid slots stay open for PixelHire.
 * Archived rows stay off the floor — there is no bench/unbench.
 */
fun seatAgents(inputs: List<SeatInput>, headcount: Int): List<Seat> {
    val cap = headcount.coerceIn(1, OfficeMap.deskCount)
    val ordered = inputs.filter { !it.archived }.sortedBy { it.sortOrder }
    val deskOf = ordered.take(cap).mapIndexed { index, agent -> agent.id to index }.toMap()
    return inputs.map { agent ->
        val desk = deskOf[agent.id]
        if (desk == null) {
            Seat(agent.id, archived = true, deskIndex = null)
        } else {
            Seat(agent.id, archived = false, deskIndex = desk)
        }
    }
}
