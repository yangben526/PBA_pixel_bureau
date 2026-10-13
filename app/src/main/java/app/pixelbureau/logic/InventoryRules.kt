package app.pixelbureau.logic

/** Removing items from the office (Inventories). */
object InventoryRules {
    /** Removing never returns stars. */
    fun refund(@Suppress("UNUSED_PARAMETER") price: Int): Int = 0

    /** Null = removable; else the reason the Remove button is disabled. */
    fun blockReason(builtIn: Boolean, kind: String): String? = when {
        !builtIn -> null
        kind == "desk" -> "Desks are where your agents work — the bureau needs them."
        kind.startsWith("conf") -> "Conference room fixtures are needed for meetings."
        kind == "door" || kind.endsWith("door") -> "The office needs its doors."
        else -> "Built-in office essential — can't be removed."
    }

    /** Owned counts after removing one [itemId]; drops the key at zero. */
    fun afterRemove(owned: Map<String, Int>, itemId: String): Map<String, Int> {
        val n = owned[itemId] ?: return owned
        return if (n <= 1) owned - itemId else owned + (itemId to n - 1)
    }

    /** Placements after removing the newest copy (`id#(n-1)`) of [itemId]; other cells untouched. */
    fun placementsAfterRemove(placements: Map<String, Cell>, itemId: String, ownedBefore: Int): Map<String, Cell> {
        val newest = "$itemId#${ownedBefore - 1}"
        if (newest in placements) return placements - newest
        val any = placements.keys.filter { it.substringBefore('#') == itemId }.maxByOrNull { it.substringAfter('#').toIntOrNull() ?: 0 }
        return if (any != null) placements - any else placements
    }
}
