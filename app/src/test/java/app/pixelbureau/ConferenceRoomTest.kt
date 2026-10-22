package app.pixelbureau

import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopCatalog
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopCategory
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopStore
import app.pixelbureau.logic.Cell
import app.pixelbureau.logic.MapPlacement
import app.pixelbureau.logic.OfficeMap
import app.pixelbureau.logic.OfficeSim
import app.pixelbureau.logic.PlacedProp
import app.pixelbureau.logic.SimAgent
import app.pixelbureau.logic.findPath
import app.pixelbureau.ui.CONF_STYLE_OAK
import app.pixelbureau.ui.CONF_STYLE_PREMIUM
import app.pixelbureau.ui.actorSortDepth
import app.pixelbureau.ui.confChairSortDepth
import app.pixelbureau.ui.CHAIR_SPRITE_DEFAULT
import app.pixelbureau.ui.CHAIR_SPRITE_OAK
import app.pixelbureau.ui.chairSpriteFor
import app.pixelbureau.ui.confStyleFor
import app.pixelbureau.ui.placeableCopies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

class ConferenceRoomTest {
    private val blocked = OfficeMap.blocked()

    private fun crossings(path: List<Cell>, start: Cell): List<Pair<Cell, Cell>> {
        val out = ArrayList<Pair<Cell, Cell>>()
        var prev = start
        for (c in path) {
            if (OfficeMap.inConf(prev) != OfficeMap.inConf(c)) out.add(prev to c)
            prev = c
        }
        return out
    }

    @Test
    fun roomIsFourByFourInTheBottomRightAndClearOfBuiltIns() {
        assertEquals(16, OfficeMap.confCells.size)
        assertTrue(OfficeMap.confCells.all { OfficeMap.inBounds(it) })
        assertEquals(OfficeMap.width - 1, OfficeMap.confCells.maxOf { it.x })
        assertEquals(OfficeMap.height - 1, OfficeMap.confCells.maxOf { it.y })
        val legacy = OfficeMap.props.filter { !it.kind.startsWith("conf_") && !(it.kind == "plant" && it.x == OfficeMap.confPlant.x && it.y == OfficeMap.confPlant.y) }
        for (p in legacy) assertFalse("${p.kind} inside room", OfficeMap.inConf(Cell(p.x, p.y)))
        assertFalse(OfficeMap.inConf(OfficeMap.door))
        assertFalse(OfficeMap.lounge.any { OfficeMap.inConf(it) })
    }

    @Test
    fun everySeatIsReachableOnlyThroughTheDoor() {
        for (seat in OfficeMap.confSeats) {
            val path = findPath(OfficeMap.width, OfficeMap.height, blocked, OfficeMap.door, seat)
            assertTrue("no route to $seat", path.isNotEmpty())
            val cross = crossings(path, OfficeMap.door)
            assertEquals(listOf(OfficeMap.confDoorOutside to OfficeMap.confDoorInside), cross)
            assertTrue(path.dropLast(1).none { it in blocked })
        }
        // And back out again through the same door.
        val out = findPath(OfficeMap.width, OfficeMap.height, blocked, OfficeMap.confSeats.first(), OfficeMap.door)
        assertEquals(
            listOf(OfficeMap.confDoorInside to OfficeMap.confDoorOutside),
            crossings(out, OfficeMap.confSeats.first()),
        )
    }

    @Test
    fun wallsBlockStraightStepsButNotTheDoor() {
        // West wall (office-facing) and north wall of the room at confX0=9, confY0=9.
        assertTrue(OfficeMap.wallBetween(Cell(8, 9), Cell(9, 9)))
        assertTrue(OfficeMap.wallBetween(Cell(10, 8), Cell(10, 9)))
        assertFalse(OfficeMap.wallBetween(OfficeMap.confDoorOutside, OfficeMap.confDoorInside))
        // Right next to the wall: walking around it must detour via the door.
        val path = findPath(OfficeMap.width, OfficeMap.height, blocked, Cell(8, 9), Cell(9, 9))
        assertTrue(path.contains(OfficeMap.confDoorInside))
        assertTrue(path.size > 1)
    }

    @Test
    fun purchasesNeverLandInsideTheRoom() {
        val placed = ArrayList<PlacedProp>()
        val kinds = listOf("aquarium", "pouf", "sofa", "plant", "vending", "arcade", "foosball", "coffee")
        var n = 0
        while (n < 120) {
            val kind = kinds[n % kinds.size]
            val cell = MapPlacement.findCell(kind, placed) ?: break
            val item = PlacedProp("x#$n", "x", kind, cell)
            for (c in item.cells) {
                assertFalse("$kind placed in room at $c", OfficeMap.inConf(c))
                assertFalse("$kind blocks the door approach", c == OfficeMap.confDoorOutside)
            }
            placed.add(item)
            n++
        }
        val all = MapPlacement.blockedWith(placed)
        for (seat in OfficeMap.confSeats) {
            assertTrue(findPath(OfficeMap.width, OfficeMap.height, all, OfficeMap.door, seat).isNotEmpty())
        }
    }

    @Test
    fun idleAgentsMeetInTheRoomAndTheDoorSwings() {
        val sim = OfficeSim(Random(7))
        val idle = listOf(
            SimAgent("a", "Pixel", "pixel", 0, "idle"),
            SimAgent("b", "Kai", "kai", 1, "idle"),
            SimAgent("c", "Byte", "byte", 2, "idle"),
            SimAgent("d", "Ops", "ops", 3, "idle"),
        )
        val last = HashMap<String, Cell>()
        var maxDoor = 0f
        var seatedInside = false
        repeat(4000) {
            sim.tick(0.1f, idle)
            maxDoor = maxOf(maxDoor, sim.confDoorOpen())
            for (a in sim.snapshot().actors) {
                val here = Cell(a.x.roundToInt(), a.y.roundToInt())
                val prev = last[a.id]
                if (prev != null && prev != here) {
                    assertFalse("${a.id} walked through glass $prev -> $here", OfficeMap.wallBetween(prev, here))
                }
                last[a.id] = here
                if (a.anim == "sit" && here in OfficeMap.confSeats) seatedInside = true
            }
        }
        assertTrue("nobody sat in the conference room", seatedInside)
        assertTrue("door never opened ($maxDoor)", maxDoor > 0.9f)
        // With everyone gone the door settles shut again.
        repeat(100) { sim.tick(0.1f, emptyList()) }
        assertEquals(0f, sim.confDoorOpen(), 0.001f)
    }

    @Test
    fun oakwoodChairReplacesTheErgonomicChair() {
        val oak = ShopCatalog.byId("oak_chair")
        val ergo = ShopCatalog.byId("ergo_chair")
        assertNotNull(oak)
        assertNotNull(ergo)
        assertEquals("Oakwood Chair", oak!!.name)
        assertEquals(1, oak.price)
        assertEquals(1, oak.salePrice)
        assertEquals(ShopCategory.Furniture, oak.category)
        assertEquals(ShopStore.Pazada, oak.store)
        assertEquals(ShopCatalog.GROUP_CHAIR, oak.exclusiveGroup)
        assertEquals(ShopCatalog.GROUP_CHAIR, ergo!!.exclusiveGroup)
        assertEquals(listOf("ergo_chair"), ShopCatalog.exclusiveRivals("oak_chair").map { it.id })
        assertEquals(listOf("oak_chair"), ShopCatalog.exclusiveRivals("ergo_chair").map { it.id })
        assertFalse(ShopCatalog.mapPropKeys.contains("chair_oak"))
        assertTrue(placeableCopies(mapOf("oak_chair" to 1)).isEmpty())
        assertTrue(placeableCopies(mapOf("ergo_chair" to 1)).isEmpty())
        assertEquals(CHAIR_SPRITE_DEFAULT, chairSpriteFor(emptyMap()))
        assertEquals(CHAIR_SPRITE_OAK, chairSpriteFor(mapOf("oak_chair" to 1)))
        assertEquals(CHAIR_SPRITE_DEFAULT, chairSpriteFor(mapOf("ergo_chair" to 1)))
        assertEquals(CHAIR_SPRITE_DEFAULT, chairSpriteFor(mapOf("oak_chair" to 1, "ergo_chair" to 1)))
    }

    @Test
    fun shopCatalogNeverListsConferenceProps() {
        for (item in ShopCatalog.items) {
            assertFalse(
                "shop must not sell conference built-in ${item.id}",
                item.id.startsWith("prop_conf_"),
            )
            assertFalse(
                "shop must not sell conference propKey ${item.propKey}",
                item.propKey.startsWith("conf_"),
            )
        }
        assertFalse(
            "conf_* keys must stay out of mapPropKeys",
            ShopCatalog.mapPropKeys.any { it.startsWith("conf_") },
        )
    }

    @Test
    fun pazadaSellsTwoConferenceStylesInTheirOwnCategory() {
        val oak = ShopCatalog.byId("oak_conference")
        val premium = ShopCatalog.byId("premium_conference")
        assertNotNull(oak)
        assertNotNull(premium)
        assertEquals("Dream Starter - Oakwood simple", oak!!.name)
        assertEquals("Premium Startup", premium!!.name)
        assertEquals(ShopCategory.Conference, oak.category)
        assertEquals(ShopCategory.Conference, premium.category)
        assertEquals(ShopStore.Pazada, oak.store)
        assertEquals(ShopStore.Pazada, premium.store)
        assertTrue(oak.featured && premium.featured)
        assertTrue("oakwood must cost less", oak.salePrice < premium.salePrice)
        assertEquals(ShopCatalog.GROUP_CONF, oak.exclusiveGroup)
        assertEquals(ShopCatalog.GROUP_CONF, premium.exclusiveGroup)
        assertEquals(listOf("premium_conference"), ShopCatalog.exclusiveRivals("oak_conference").map { it.id })
        assertEquals(listOf("oak_conference"), ShopCatalog.exclusiveRivals("premium_conference").map { it.id })
        // Styles swap the existing room. They must not take a floor cell.
        assertTrue(placeableCopies(mapOf("oak_conference" to 1, "premium_conference" to 1)).isEmpty())
    }

    @Test
    fun agentsOnConferenceSeatsDrawInFrontOfTheirChair() {
        for (seat in OfficeMap.confSeats) {
            val chair = confChairSortDepth(seat.x, seat.y)
            val sitting = actorSortDepth(seat.x.toFloat(), seat.y.toFloat(), "sit", useSeatTuck = false)
            val typing = actorSortDepth(seat.x.toFloat(), seat.y.toFloat(), "type", useSeatTuck = false)
            val walking = actorSortDepth(seat.x.toFloat(), seat.y.toFloat(), "walk", useSeatTuck = false)
            val idle = actorSortDepth(seat.x.toFloat(), seat.y.toFloat(), "idle", useSeatTuck = false)
            assertTrue("sitter at $seat should sit behind the chair", sitting < chair)
            assertTrue("typer at $seat should sit behind the chair", typing < chair)
            assertTrue("walker at $seat should draw in front of the chair", walking > chair)
            assertTrue("idle agent at $seat should draw in front of the chair", idle > chair)
            // Front glass is the tile edge at +0.5. Bodies stay inside the room.
            val frontGlass = seat.x + seat.y + 0.5f
            assertTrue("walker at $seat should stay behind the front glass", walking < frontGlass)
        }
        // Desk tuck is unchanged: still under a work-chair at the same tile.
        val desk = actorSortDepth(2f, 3f, "type", useSeatTuck = true)
        assertEquals(2f + 3f - 0.04f, desk, 0.001f)
    }

    @Test
    fun conferenceStyleFollowsOwnership() {
        assertEquals(CONF_STYLE_PREMIUM, confStyleFor(emptyMap()))
        assertEquals(CONF_STYLE_OAK, confStyleFor(mapOf("oak_conference" to 1)))
        assertEquals(CONF_STYLE_PREMIUM, confStyleFor(mapOf("premium_conference" to 1)))
        assertEquals(CONF_STYLE_PREMIUM, confStyleFor(mapOf("oak_conference" to 1, "premium_conference" to 1)))
    }
}
