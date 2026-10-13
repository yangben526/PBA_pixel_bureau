package app.pixelbureau

import app.pixelbureau.logic.Cell
import app.pixelbureau.logic.MapPlacement
import app.pixelbureau.logic.OfficeMap
import app.pixelbureau.logic.PlacedProp
import app.pixelbureau.logic.findPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPlacementTest {
    private val kinds = listOf(
        "aquarium", "arcade", "cactus", "clock", "filing_cabinet", "floor_lamp", "globe", "guitar",
        "neon_sign", "picture_frame", "pouf", "printer", "storage_box", "vending", "sofa", "coffee", "plant",
    )

    @Test
    fun baseMapKeepsPathsOpen() {
        assertTrue(MapPlacement.keepsPathsOpen(OfficeMap.blocked()))
    }

    @Test
    fun fillingTheFloorNeverOverlapsOrCutsPaths() {
        val placed = ArrayList<PlacedProp>()
        var n = 0
        while (true) {
            val kind = kinds[n % kinds.size]
            val cell = MapPlacement.findCell(kind, placed) ?: break
            val item = PlacedProp("x#$n", "x", kind, cell)
            val taken = MapPlacement.blockedWith(placed)
            for (c in item.cells) {
                assertTrue(OfficeMap.inBounds(c))
                assertFalse("overlap at $c", c in taken)
                assertFalse("reserved $c", c in MapPlacement.reserved)
            }
            placed.add(item)
            n++
            if (n > 200) break
        }
        assertTrue("room for a decent number of items, got $n", n >= 20)
        val blocked = MapPlacement.blockedWith(placed)
        for (desk in OfficeMap.desks.indices) {
            val seat = OfficeMap.chair(desk)
            assertTrue(findPath(OfficeMap.width, OfficeMap.height, blocked, OfficeMap.door, seat).isNotEmpty())
        }
        for (spot in OfficeMap.coffeeApproach + OfficeMap.fridgeApproach + OfficeMap.beanbag + OfficeMap.sofaSpan) {
            if (spot == OfficeMap.door) continue
            assertTrue("$spot", findPath(OfficeMap.width, OfficeMap.height, blocked, OfficeMap.door, spot).isNotEmpty())
        }
    }

    @Test
    fun resolveKeepsStoredCellsAndReallocatesInvalidOnes() {
        val first = MapPlacement.findCell("aquarium", emptyList())
        assertNotNull(first)
        val stored = mapOf("aquarium#0" to first!!, "pouf#0" to OfficeMap.desks[0])
        val (placed, cells) = MapPlacement.resolve(
            listOf("aquarium#0" to "aquarium", "pouf#0" to "pouf"),
            stored,
        ) { it.substringBefore('#') }
        assertEquals(first, cells["aquarium#0"])
        assertEquals(2, placed.size)
        assertFalse(cells["pouf#0"] == OfficeMap.desks[0])
    }

    @Test
    fun arcadeAndVendingPreferAdjacent() {
        val arcade = MapPlacement.findCell("arcade", emptyList())
        assertNotNull(arcade)
        val withArcade = listOf(PlacedProp("arcade_cabinet#0", "arcade_cabinet", "arcade", arcade!!))
        val vending = MapPlacement.findCell("vending", withArcade)
        assertNotNull(vending)
        val dist = kotlin.math.abs(vending!!.x - arcade.x) + kotlin.math.abs(vending.y - arcade.y)
        assertEquals("vending should sit beside arcade, got $arcade / $vending", 1, dist)

        // Both from empty via resolve: place as a neighbouring pair.
        val (placed, _) = MapPlacement.resolve(
            listOf("arcade_cabinet#0" to "arcade", "vending_machine#0" to "vending"),
            emptyMap(),
        ) { it.substringBefore('#') }
        assertEquals(2, placed.size)
        val a = placed.first { it.kind == "arcade" }.cell
        val v = placed.first { it.kind == "vending" }.cell
        assertEquals(1, kotlin.math.abs(a.x - v.x) + kotlin.math.abs(a.y - v.y))
    }


    @Test
    fun backDoorIsWalkableReservedAndReachable() {
        val bd = OfficeMap.backDoor
        assertEquals(Cell(OfficeMap.width - 1, 0), bd)
        assertTrue(bd !in OfficeMap.blocked())
        assertTrue(bd in MapPlacement.reserved)
        assertTrue(Cell(bd.x, bd.y + 1) in MapPlacement.reserved)
        assertEquals(listOf(OfficeMap.door, bd), OfficeMap.doors)
        assertEquals(bd, OfficeMap.nearestDoor(11f, 1f))
        assertEquals(OfficeMap.door, OfficeMap.nearestDoor(4f, 10f))
        // Shop placement never lands on either entrance.
        val placed = ArrayList<PlacedProp>()
        for (k in listOf("aquarium", "arcade", "vending", "printer", "clock", "globe", "plant")) {
            val c = MapPlacement.findCell(k, placed) ?: continue
            assertTrue(c !in OfficeMap.doors)
            placed.add(PlacedProp("$k#0", k, k, c))
        }
    }
}

class BuiltInInventoryTest {
    @org.junit.Test
    fun listsEveryBuiltInKind() {
        val inv = app.pixelbureau.logic.OfficeMap.builtInInventory().toMap()
        org.junit.Assert.assertEquals(app.pixelbureau.logic.OfficeMap.desks.size, inv["desk"])
        org.junit.Assert.assertEquals(1, inv["conf_room"])
        org.junit.Assert.assertTrue(inv.keys.none { it.startsWith("conf_") && it != "conf_room" })
    }
}

class BackDoorAnimTest {
    @org.junit.Test
    fun closedByDefaultOpensForArrivalAndClosesAfter() {
        val sim = app.pixelbureau.logic.OfficeSim(kotlin.random.Random(1))
        org.junit.Assert.assertEquals(0f, sim.backDoorOpen(), 0f)
        org.junit.Assert.assertEquals(0f, sim.snapshot().backDoorOpen, 0f)
    }

    @org.junit.Test
    fun leaverExitsThroughBackDoorThenDespawns() {
        val om = app.pixelbureau.logic.OfficeMap
        val desk = om.desks.indices.firstOrNull { i ->
            val c = om.chair(i); om.nearestDoor(c.x.toFloat(), c.y.toFloat()) == om.backDoor
        }
        org.junit.Assert.assertNotNull("some desk should be nearer the back door", desk)
        val sim = app.pixelbureau.logic.OfficeSim(kotlin.random.Random(2))
        val a = app.pixelbureau.logic.SimAgent("a", "Pixel", "pixel", desk!!, "working")
        // Arrive: spawns in the back doorway, door opens.
        sim.tick(0.05f, listOf(a))
        val first = sim.snapshot().actors.single()
        org.junit.Assert.assertEquals(om.backDoor.x.toFloat(), first.x, 0.6f)
        org.junit.Assert.assertEquals(om.backDoor.y.toFloat(), first.y, 0.6f)
        repeat(10) { sim.tick(0.05f, listOf(a)) }
        org.junit.Assert.assertTrue(sim.backDoorOpen() > 0f)
        // Walk to the desk; door closes behind.
        repeat(600) { sim.tick(0.1f, listOf(a)) }
        org.junit.Assert.assertEquals(0f, sim.backDoorOpen(), 0f)
        // Dropped from roster: walks out via the back door, door opens, then gone.
        sim.tick(0.1f, emptyList())
        org.junit.Assert.assertTrue(sim.isLeaving("a"))
        var sawOpen = false
        var steps = 0
        while (sim.snapshot().actors.isNotEmpty() && steps < 1000) {
            sim.tick(0.05f, emptyList()); steps++
            if (sim.backDoorOpen() > 0.9f) sawOpen = true
        }
        org.junit.Assert.assertTrue("despawned", sim.snapshot().actors.isEmpty())
        org.junit.Assert.assertTrue("door opened on the way out", sawOpen)
        repeat(40) { sim.tick(0.05f, emptyList()) }
        org.junit.Assert.assertEquals(0f, sim.backDoorOpen(), 0f)
    }
}
