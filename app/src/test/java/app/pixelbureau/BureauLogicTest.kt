package app.pixelbureau

import app.pixelbureau.logic.CatalogWrite
import app.pixelbureau.modules.pixelnews.PixelNews
import app.pixelbureau.logic.Cell
import app.pixelbureau.logic.ChatException
import app.pixelbureau.logic.LlmCatalog
import app.pixelbureau.logic.OfficeMap
import app.pixelbureau.logic.SavedLlm
import app.pixelbureau.logic.SeatInput
import app.pixelbureau.logic.SseEvent
import app.pixelbureau.logic.TurnMessage
import app.pixelbureau.logic.DefaultOutputStyle
import app.pixelbureau.logic.HireRoleAll
import app.pixelbureau.logic.HireRoleGeneral
import app.pixelbureau.logic.candidatesForRole
import app.pixelbureau.logic.candidatesNamed
import app.pixelbureau.logic.hireRoleFilters
import app.pixelbureau.logic.instructionEditCostsStar
import app.pixelbureau.logic.matches
import app.pixelbureau.logic.hiredLook
import app.pixelbureau.logic.looks
import app.pixelbureau.logic.openingCandidatePool
import app.pixelbureau.logic.workplace
import app.pixelbureau.logic.HireBatchMax
import app.pixelbureau.logic.HireBatchMin
import app.pixelbureau.logic.HirePosting
import app.pixelbureau.logic.decodeHirePosting
import app.pixelbureau.logic.deliverHireBatch
import app.pixelbureau.logic.encodeHirePosting
import app.pixelbureau.logic.hireArrivalLine
import app.pixelbureau.logic.hirePostingWithout
import app.pixelbureau.logic.postHireJob
import app.pixelbureau.logic.rollCandidatePool
import app.pixelbureau.logic.rollMixedHireBatch
import app.pixelbureau.logic.buildSystemPrompt
import app.pixelbureau.net.parseWikipediaSearch
import app.pixelbureau.net.parseDuckDuckGoInstant
import app.pixelbureau.logic.ToolRoundResult
import app.pixelbureau.logic.parseToolRound
import app.pixelbureau.logic.extractToolQuery
import app.pixelbureau.logic.extractToolCalls
import app.pixelbureau.logic.enabledCommonTools
import app.pixelbureau.logic.catalogFromLegacy
import app.pixelbureau.logic.chatCompletionsUrl
import app.pixelbureau.logic.decodeCatalog
import app.pixelbureau.logic.encodeCatalog
import app.pixelbureau.logic.llmProvider
import app.pixelbureau.logic.llmProviders
import app.pixelbureau.logic.removeFromCatalog
import app.pixelbureau.logic.saveIntoCatalog
import app.pixelbureau.logic.selectInCatalog
import app.pixelbureau.logic.extractStringField
import app.pixelbureau.logic.findPath
import app.pixelbureau.logic.historyForNextTurn
import app.pixelbureau.logic.isJsonSchema
import app.pixelbureau.logic.isPlaceholderReply
import app.pixelbureau.logic.isScratchpad
import app.pixelbureau.logic.isSearchNudge
import app.pixelbureau.logic.isSearchPromise
import app.pixelbureau.logic.isThinTaskReply
import app.pixelbureau.logic.taskNeedsAnotherPass
import app.pixelbureau.logic.taskSearchQuery
import app.pixelbureau.logic.isSearchResultDump
import app.pixelbureau.logic.offeredLookup
import app.pixelbureau.logic.agentToolJson
import app.pixelbureau.logic.preferredUserRequest
import app.pixelbureau.logic.recoverSearchQuery
import app.pixelbureau.logic.replyWorkLabel
import app.pixelbureau.logic.searchQueryFromContext
import app.pixelbureau.logic.timeArgumentsFromContext
import app.pixelbureau.logic.timeTool
import app.pixelbureau.logic.jsonString
import app.pixelbureau.logic.StarScore
import app.pixelbureau.logic.grantChat
import app.pixelbureau.logic.grantTaskStar
import app.pixelbureau.logic.taskOrderBody
import app.pixelbureau.logic.officeClock
import app.pixelbureau.logic.parseCompletionBody
import app.pixelbureau.logic.LlmTurn
import app.pixelbureau.logic.ToolCall
import app.pixelbureau.logic.ToolCallAcc
import app.pixelbureau.logic.absorbStreamToolCalls
import app.pixelbureau.logic.llmTurnJson
import app.pixelbureau.logic.parseSseLine
import app.pixelbureau.logic.visibleReply
import app.pixelbureau.net.InternetSearchClient
import app.pixelbureau.net.checkingOutStatus
import app.pixelbureau.net.isDuckDuckGoChallenge
import app.pixelbureau.net.isPrivateWebHost
import app.pixelbureau.net.isUsefulSearchHit
import app.pixelbureau.net.openablePageUrl
import app.pixelbureau.net.pagesToOpen
import app.pixelbureau.net.parseDuckDuckGoHtml
import app.pixelbureau.net.readablePageText
import app.pixelbureau.logic.BanterLlmOdds
import app.pixelbureau.logic.BubbleSpeechScaleDefault
import app.pixelbureau.logic.BubbleSpeechScaleMax
import app.pixelbureau.logic.bubbleSpeechLayout
import app.pixelbureau.logic.coerceBubbleSpeechScale
import androidx.compose.ui.geometry.Offset
import app.pixelbureau.logic.OfficeSim
import app.pixelbureau.ui.TaskNotice
import app.pixelbureau.ui.acknowledgeTaskDone
import app.pixelbureau.ui.agentShowsTaskDone
import app.pixelbureau.ui.freshDoneTaskId
import app.pixelbureau.ui.ChatBlock
import app.pixelbureau.ui.OfficeCamera
import app.pixelbureau.ui.officeCameraShouldRefit
import app.pixelbureau.ui.headingLine
import app.pixelbureau.ui.parseChatBlocks
import app.pixelbureau.ui.tableColumnWidths
import app.pixelbureau.ui.tidyTableCell
import app.pixelbureau.logic.banterLlmRoll
import app.pixelbureau.logic.banterSystemPrompt
import app.pixelbureau.logic.banterUserPrompt
import app.pixelbureau.logic.clipBanterLine
import app.pixelbureau.logic.officeChatExchanges
import app.pixelbureau.logic.officeChatLines
import app.pixelbureau.logic.BanterBook
import app.pixelbureau.logic.BanterMemory
import app.pixelbureau.logic.seatAgents
import app.pixelbureau.logic.SimAgent
import app.pixelbureau.logic.facingFromDelta
import app.pixelbureau.logic.softenFacingTurn
import app.pixelbureau.logic.spriteFrame
import app.pixelbureau.ui.floorIdleLift
import app.pixelbureau.logic.desksWithActiveScreens
import app.pixelbureau.logic.coffeeSteamProgress
import app.pixelbureau.logic.coffeeSteamWisps

import app.pixelbureau.logic.FanInspectRadius
import app.pixelbureau.logic.ServerFanRoofY
import app.pixelbureau.sprites.ServerFanRotorFraction
import app.pixelbureau.sprites.splitServerFan
import app.pixelbureau.logic.ServerFanRunMs
import app.pixelbureau.logic.officeFanInspectLines
import app.pixelbureau.logic.ServerFanTurns
import app.pixelbureau.logic.serverFanCycleMs
import app.pixelbureau.logic.serverFanGapMs
import app.pixelbureau.logic.serverFanSpin
import app.pixelbureau.logic.serverLedStrength
import app.pixelbureau.logic.serverLeds
import app.pixelbureau.logic.screenCodeGlyphs
import app.pixelbureau.logic.ActorSnap
import kotlin.math.roundToInt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class BureauLogicTest {
    @Test
    fun pathGoesAroundABlockedTile() {
        val blocked = setOf(Cell(1, 0))
        val path = findPath(3, 1, blocked, Cell(0, 0), Cell(2, 0))
        assertTrue(path.isEmpty())
        val around = findPath(3, 2, blocked, Cell(0, 0), Cell(2, 0))
        assertEquals(Cell(2, 0), around.last())
        assertTrue(Cell(1, 0) !in around)
    }

    @Test
    fun everyChairIsReachableFromTheDoor() {
        val blocked = OfficeMap.blocked()
        for (desk in 0 until OfficeMap.deskCount) {
            val chair = OfficeMap.chair(desk)
            assertTrue(OfficeMap.inBounds(chair))
            assertTrue(chair !in blocked) // vacant seats stay walkable corridors
            val path = findPath(OfficeMap.width, OfficeMap.height, blocked, OfficeMap.door, chair)
            assertTrue("desk $desk", path.isNotEmpty())
            assertEquals(chair, path.last())
            assertTrue("desk $desk walks through furniture", path.dropLast(1).none { it in blocked })
        }
    }

    @Test
    fun bodiesStayOffFurnitureWhileTheyWalk() {
        val blocked = OfficeMap.blocked()
        val sim = OfficeSim(Random(1))
        val hires = listOf(
            SimAgent("a", "Pixel", "pixel", 0, "walking"),
            SimAgent("b", "Kai", "kai", 3, "walking"),
            SimAgent("c", "Byte", "byte", 5, "walking"),
        )
        val chairs = hires.associate { it.id to OfficeMap.chair(it.deskIndex!!) }
        repeat(80) {
            sim.tick(0.25f, hires)
            assertFeet(sim, blocked, chairs)
        }
        val idle = hires.map { it.copy(status = "idle") }
        repeat(160) {
            sim.tick(0.25f, idle)
            assertFeet(sim, blocked, chairs)
        }
    }

    private fun assertFeet(sim: OfficeSim, blocked: Set<Cell>, chairs: Map<String, Cell>) {
        for (actor in sim.snapshot().actors) {
            val cell = Cell(actor.x.roundToInt(), actor.y.roundToInt())
            if (cell in blocked) {
                val allowed = cell == chairs[actor.id] ||
                    cell in OfficeMap.sofaSpan ||
                    cell == OfficeMap.beanbag ||
                    cell in OfficeMap.coffeeSpan
                assertTrue(
                    "${actor.id} on blocked $cell (chair=${chairs[actor.id]})",
                    allowed,
                )
            }
        }
    }

    @Test
    fun desksSitInTwoEvenRowsAndTheLoungeIsOpen() {
        val rows = OfficeMap.desks.groupBy { it.y }.toSortedMap()
        assertEquals(listOf(2, 6), rows.keys.toList())
        for (row in rows.values) {
            assertEquals(listOf(2, 4, 6, 8), row.map { it.x }.sorted())
        }
        val blocked = OfficeMap.blocked()
        assertEquals(13, OfficeMap.width)
        assertEquals(13, OfficeMap.height)
        assertTrue(OfficeMap.plants.isEmpty())
        // Conference plant owns the SE corner; the NE corner plant was removed.
        assertEquals(OfficeMap.confPlant, Cell(12, 12))
        assertTrue(Cell(12, 12) in blocked)
        assertTrue(OfficeMap.props.any { it.kind == "plant" && it.x == 12 && it.y == 12 })
        assertEquals(Cell(10, 5), OfficeMap.server)
        assertTrue(OfficeMap.server in blocked)
        assertEquals(Cell(5, 11), OfficeMap.door)
        assertFalse(OfficeMap.inConf(OfficeMap.door))
        assertEquals(Cell(3, 9), OfficeMap.sofa)
        assertTrue(Cell(4, 9) in blocked)
        assertTrue(Cell(3, 8) !in blocked)
        assertTrue(Cell(4, 8) !in blocked)
        assertEquals(Cell(0, 7), OfficeMap.bookshelf)
        assertEquals(Cell(0, 6), OfficeMap.watercooler)
        assertTrue(OfficeMap.watercooler in blocked)
        assertTrue(Cell(0, 8) !in blocked)
        assertTrue(Cell(10, 8) !in blocked)
        assertEquals(Cell(1, 8), OfficeMap.beanbag)
        assertEquals(listOf(Cell(0, 10), Cell(1, 10)), OfficeMap.foosballSpan)
        assertTrue(OfficeMap.foosballSpan.all { it in blocked })
        // Foosball stays south of the beanbag with the lounge aisle open.
        assertTrue(Cell(2, 9) !in blocked)
        assertTrue(Cell(2, 10) !in blocked)
        assertTrue(Cell(1, 8) in blocked)
        assertTrue(Cell(1, 9) !in blocked)
        assertEquals(Cell(0, 0), OfficeMap.trash)
        assertTrue(OfficeMap.trash in blocked)
        // SW plant sits on the south-west tip; the former (0,11) cell is free floor.
        assertEquals(Cell(0, OfficeMap.height - 1), OfficeMap.plant2)
        assertEquals(Cell(0, 12), OfficeMap.plant2)
        assertTrue(OfficeMap.plant2 in blocked)
        assertTrue(Cell(0, 11) !in blocked)
        assertTrue(OfficeMap.props.any { it.kind == "plant2" && it.x == 0 && it.y == 12 })
        assertTrue(Cell(5, 7) !in blocked)
        assertEquals(Cell(6, 9), OfficeMap.coffee)
        assertTrue(OfficeMap.coffee in blocked)
        assertTrue(Cell(6, 8) in blocked)
        assertEquals(Cell(6, 7), OfficeMap.fridge)
        assertTrue(OfficeMap.fridge in blocked)
        // The fridge is visually nested toward the coffee span without occupying it.
        assertEquals(-0.45f, OfficeMap.fridgeDrawOffset.dxTileW, 0.001f)
        assertEquals(0.45f, OfficeMap.fridgeDrawOffset.dyTileH, 0.001f)
        assertTrue(Cell(2, 5) in blocked) // behind desk at (2,6)
        assertTrue(Cell(2, 4) !in blocked)
        assertTrue(OfficeMap.chair(0) !in blocked)
        for (spot in OfficeMap.lounge) {
            assertTrue(spot !in blocked)
            val path = findPath(OfficeMap.width, OfficeMap.height, blocked, OfficeMap.door, spot)
            if (spot != OfficeMap.door) {
                assertTrue("$spot", path.isNotEmpty())
            }
            assertTrue(path.none { it in blocked })
        }
    }

    @Test
    fun walkersCanCrossTheOfficeHorizontally() {
        val blocked = OfficeMap.blocked()
        val across = findPath(OfficeMap.width, OfficeMap.height, blocked, Cell(3, 4), Cell(7, 4))
        assertTrue(across.isNotEmpty())
        assertEquals(Cell(7, 4), across.last())
        assertTrue(across.none { it in blocked })
        assertTrue(across.any { it.x != 3 })
        val leftChair = OfficeMap.chair(0)
        val rightChair = OfficeMap.chair(3)
        val deskHop = findPath(OfficeMap.width, OfficeMap.height, blocked, leftChair, rightChair)
        assertTrue(deskHop.isNotEmpty())
        assertEquals(rightChair, deskHop.last())
        assertTrue(deskHop.dropLast(1).none { it in blocked })
    }

    @Test
    fun sameCellIsAlreadyThere() {
        assertTrue(findPath(4, 4, emptySet(), Cell(1, 1), Cell(1, 1)).isEmpty())
    }

    @Test
    fun seatingFillsDesksUpToTheCap() {
        val people = listOf(
            SeatInput("a", archived = false, sortOrder = 0),
            SeatInput("b", archived = false, sortOrder = 1),
            SeatInput("c", archived = false, sortOrder = 2),
        )
        val seated = seatAgents(people, 2)
        assertEquals(0, seated.first { it.id == "a" }.deskIndex)
        assertEquals(1, seated.first { it.id == "b" }.deskIndex)
        assertTrue(seated.first { it.id == "c" }.archived)
    }

    @Test
    fun seatingDoesNotUnarchiveFiredPeople() {
        val people = listOf(
            SeatInput("a", archived = false, sortOrder = 0),
            SeatInput("b", archived = false, sortOrder = 1),
            SeatInput("c", archived = true, sortOrder = 2),
        )
        val seated = seatAgents(people, 3)
        assertTrue(seated.first { it.id == "c" }.archived)
        assertEquals(null, seated.first { it.id == "c" }.deskIndex)
        assertEquals(0, seated.first { it.id == "a" }.deskIndex)
        assertEquals(1, seated.first { it.id == "b" }.deskIndex)
    }

    @Test
    fun promptCarriesPersonalityDutiesAndTask() {
        val prompt = buildSystemPrompt(
            name = "Pixel",
            personality = "You are upbeat.",
            duties = "Ship small answers\n\nStay concrete",
            taskTitle = "Write the note",
            taskBrief = "One paragraph.",
        )
        assertTrue(prompt.contains("Pixel"))
        assertTrue(prompt.contains("Instructions:\nYou are upbeat."))
        assertTrue(prompt.contains("Output style:\nShip small answers\n\nStay concrete"))
        assertFalse(prompt.contains("Standing duties:"))
        assertTrue(DefaultOutputStyle.isNotBlank())
        val hired = buildSystemPrompt("Ada", "", DefaultOutputStyle, null, null)
        assertFalse(hired.contains("Instructions:"))
        assertTrue(hired.contains("Output style:\n$DefaultOutputStyle"))
        assertTrue(prompt.contains("Current task: Write the note"))
        assertTrue(prompt.contains("One paragraph."))
        assertTrue(prompt.contains("assigned task, not a chat reply"))
        assertTrue(prompt.contains("Any other job"))
        val chat = buildSystemPrompt("Pixel", "Curious.", "Check facts", null, null)
        assertFalse(chat.contains("assigned task"))
        assertTrue(isThinTaskReply("Maybank looks fine today."))
        assertFalse(isThinTaskReply("## Price\n\nThe shares are 10.20."))
        assertFalse(taskNeedsAnotherPass("## Price\n\nThe shares are 10.20.", 1, true))
        assertTrue(taskNeedsAnotherPass("Maybank looks fine.", 0, true))
        assertFalse(taskNeedsAnotherPass("Maybank looks fine.", 4, true))
        val longBrief = "Task: Compare the two ovens\n\n" + "x".repeat(300)
        assertEquals(
            "Compare the two ovens",
            taskSearchQuery(listOf(TurnMessage("user", longBrief, "task-1"))),
        )
    }

    @Test
    fun nextTurnStopsAtTheFirstUnansweredUser() {
        val history = historyForNextTurn(
            listOf(
                TurnMessage("user", "one", null),
                TurnMessage("assistant", "done", null),
                TurnMessage("user", "two", "task-2"),
                TurnMessage("user", "three", null),
            ),
        )
        assertEquals(listOf("one", "done", "two"), history?.map { it.text })
        assertEquals("task-2", history?.last()?.taskId)
        val withPlaceholder = historyForNextTurn(
            listOf(
                TurnMessage("user", "find latest news", null),
                TurnMessage("assistant", "(No reply)", null),
                TurnMessage("user", "?", null),
            ),
        )
        assertEquals(listOf("find latest news", "?"), withPlaceholder?.map { it.text })
        assertTrue(isPlaceholderReply("(No reply)"))
        assertTrue(isPlaceholderReply("  (no reply). "))
        assertFalse(isPlaceholderReply("No reply yet — searching."))
        val plan = "We need answer user. I can search \"Maybank latest news\". Let's do search."
        assertTrue(isScratchpad(plan))
        assertEquals("Maybank latest news", recoverSearchQuery(plan))
        val xmlPlan = "We need respond to user. <invoke name=\"internet_search\"><parameter name=\"query\">Maybank latest news</parameter></invoke> Let's issue correct tool call."
        assertTrue(isScratchpad(xmlPlan))
        assertEquals("Maybank latest news", recoverSearchQuery(xmlPlan))
        assertFalse(isScratchpad("Hey! Byte here from Pixel Bureau. What can I dig into for you?"))
        assertFalse(isScratchpad("The answer is 4."))
        val formatEssay = "The user is asking me to find news on Maybank. I need to use the internet_search tool with a proper query. The previous assistant calls show missing search query because no query parameter was passed. Let me call internet_search with query \"Maybank latest news\". I'll then report. I'll do it in the message."
        assertTrue(isScratchpad(formatEssay))
        assertEquals("Maybank latest news", recoverSearchQuery(formatEssay))
        assertTrue(isScratchpad("We need answer. I should search now. Use proper tool call format."))
        assertTrue(isScratchpad("We need stop empty calls. Need call with query parameter. The format maybe:"))
        assertTrue(
            isSearchResultDump(
                "Here's what I found:\n\n1. Maybank — https://example.com/news\n2. Other — https://example.com/other",
            ),
        )
        assertTrue(isSearchResultDump("Web search results for “Maybank”:\n1. Maybank — https://example.com"))
        assertTrue(isSearchResultDump("Query: Maybank latest news"))
        assertFalse(isSearchResultDump("Maybank outlined the ROAR30 plan today, and the shares moved."))
        assertFalse(isSearchResultDump("Maybank rose today. Source: https://example.com/news"))
        val notes = app.pixelbureau.net.formatSearchNotes(
            listOf(
                app.pixelbureau.net.InternetSearchClient.Hit(
                    "Maybank",
                    "https://example.com/news",
                    "ROAR30 plan",
                    "Maybank launched the plan.",
                ),
            ),
        )
        assertTrue(notes.contains("Private notes"))
        assertTrue(notes.contains("ROAR30 plan"))
        assertTrue(notes.contains("Read from the page: Maybank launched the plan."))
        assertFalse(notes.contains("Web search results"))
        assertEquals("Searching the web", replyWorkLabel("Searching the web…"))
        assertEquals("Checking out reuters.com", replyWorkLabel("Checking out reuters.com…"))
        assertEquals(null, replyWorkLabel("Maybank outlined the plan."))
        assertEquals(
            "hey, find news on maybank, latest",
            searchQueryFromContext(
                "{}",
                "Use proper tool call format. I should search now and then write the news.",
                listOf(
                    TurnMessage("user", "hey, find news on maybank, latest", null),
                    TurnMessage("user", "do it then. stop stopping", null),
                    TurnMessage("user", "?", null),
                ),
            ),
        )
        assertEquals(
            "hey, find news on maybank, latest",
            preferredUserRequest(
                listOf("hey, find news on maybank, latest", "do it then. stop stopping", "?"),
            ),
        )
        assertTrue(isSearchNudge("find more info"))
        assertTrue(isSearchNudge("don't stop"))
        assertTrue(isSearchNudge("run"))
        assertTrue(isSearchNudge("yes do that"))
        assertFalse(isSearchNudge("find news on maybank"))
        assertFalse(isSearchNudge("why they wanna buy"))
        assertTrue(isSearchPromise("Running it now — not stopping."))
        assertTrue(
            isSearchPromise(
                "That search was my mistake. Running the real query now.\n\nRunning the correct search now.",
            ),
        )
        assertFalse(
            isSearchPromise("Maybank fell 1.2% after the redemption. The dip is about capital, not earnings."),
        )
        val offer = "Want the deal price for the Ageas buy, and the 2024-2026 financial numbers?"
        assertTrue(offeredLookup(offer).orEmpty().contains("deal price"))
        assertEquals(
            offeredLookup(offer),
            searchQueryFromContext(
                "{\"query\":\"find more info\"}",
                "Running the correct search now.",
                listOf(
                    TurnMessage("user", "why they wanna buy", null),
                    TurnMessage("assistant", offer, null),
                    TurnMessage("user", "find more info", null),
                ),
            ),
        )
        val hiddenEssay = historyForNextTurn(
            listOf(
                TurnMessage("user", "hey, find news on maybank, latest", null),
                TurnMessage("assistant", formatEssay, null),
                TurnMessage("user", "?", null),
            ),
        )
        assertEquals(listOf("hey, find news on maybank, latest", "?"), hiddenEssay?.map { it.text })
        val kept = historyForNextTurn(
            listOf(
                TurnMessage("user", "find news", null),
                TurnMessage("assistant", plan, null),
                TurnMessage("user", "do it then!", null),
            ),
        )
        assertEquals(listOf("find news", "do it then!"), kept?.map { it.text })
        assertNull(
            historyForNextTurn(
                listOf(
                    TurnMessage("user", "one", null),
                    TurnMessage("assistant", "done", null),
                ),
            ),
        )
    }

    @Test
    fun providersAreHostedApis() {
        assertEquals("https://api.x.ai/v1", llmProvider("xai").baseUrl)
        assertEquals("grok-4.7", llmProvider("xai").defaultModel)
        assertEquals("https://api.openai.com/v1", llmProvider("openai").baseUrl)
        assertEquals("grok-4.7", llmProvider("nope").defaultModel)
        assertTrue(llmProviders.none { it.baseUrl.startsWith("http://") || it.baseUrl.contains("localhost") })
    }

    @Test
    fun oneProviderCanHoldSeveralModels() {
        val first = saveIntoCatalog(LlmCatalog(), null, "xai", "key-a", "grok-4.7", "Desk", "id-1")
        val added = first as CatalogWrite.Saved
        assertEquals("id-1", added.catalog.activeId)
        assertEquals("Added xAI · grok-4.7. Chats use it.", added.message)

        val second = saveIntoCatalog(added.catalog, null, "xai", "key-a", "grok-4.5", "", "id-2")
        val both = second as CatalogWrite.Saved
        assertEquals(listOf("grok-4.7", "grok-4.5"), both.catalog.models.map { it.model })
        assertEquals("id-1", both.catalog.activeId)

        val duplicate = saveIntoCatalog(both.catalog, null, "xai", "other-key", "grok-4.5", "", "id-3")
        assertTrue(duplicate is CatalogWrite.Rejected)

        val blank = saveIntoCatalog(both.catalog, null, "openai", "", "gpt-4o", "", "id-4")
        assertTrue(blank is CatalogWrite.Rejected)

        val selected = selectInCatalog(both.catalog, "id-2") as CatalogWrite.Saved
        assertEquals("id-2", selected.catalog.activeId)
        assertEquals("Chats now use grok-4.5.", selected.message)

        val removed = removeFromCatalog(selected.catalog, "id-2") as CatalogWrite.Saved
        assertEquals("id-1", removed.catalog.activeId)
        assertEquals(listOf("grok-4.7"), removed.catalog.models.map { it.model })

        val edited = saveIntoCatalog(removed.catalog, "id-1", "openai", "sk-live", "gpt-4o", "Writer", "ignored") as CatalogWrite.Saved
        assertEquals("openai", edited.catalog.models.single().providerId)
        assertEquals("https://api.openai.com/v1", edited.catalog.models.single().baseUrl)
        assertEquals("Writer", edited.catalog.models.single().label)
        assertEquals("id-1", edited.catalog.activeId)
    }

    @Test
    fun aModelKeepsTheUrlTheUserTyped() {
        val custom = saveIntoCatalog(
            LlmCatalog(),
            null,
            "xai",
            "key",
            "my-model",
            "",
            "id-1",
            "https://example.com/v1/",
        ) as CatalogWrite.Saved
        assertEquals("https://example.com/v1", custom.catalog.models.single().baseUrl)
        assertEquals("Added example.com · my-model. Chats use it.", custom.message)

        val sameModelOtherHost = saveIntoCatalog(
            custom.catalog,
            null,
            "xai",
            "key",
            "my-model",
            "",
            "id-2",
            "api.other.test/v1",
        ) as CatalogWrite.Saved
        assertEquals("https://api.other.test/v1", sameModelOtherHost.catalog.models[1].baseUrl)

        val again = saveIntoCatalog(
            sameModelOtherHost.catalog,
            null,
            "openai",
            "key",
            "my-model",
            "",
            "id-3",
            "https://example.com/v1",
        )
        assertTrue(again is CatalogWrite.Rejected)

        val bad = saveIntoCatalog(custom.catalog, null, "xai", "key", "m", "", "id-4", "https://")
        assertTrue(bad is CatalogWrite.Rejected)

        val old = """{"activeId":"id-1","models":[{"id":"id-1","providerId":"xai","apiKey":"k","model":"grok-4.7","label":""}]}"""
        assertEquals("https://api.x.ai/v1", decodeCatalog(old).models.single().baseUrl)
    }

    @Test
    fun catalogRoundTripsKeysAndLegacyImport() {
        val catalog = LlmCatalog(
            models = listOf(
                SavedLlm("id-1", "xai", "line\n\"quote\"", "grok-4.7", "Desk", "https://api.x.ai/v1"),
                SavedLlm("id-2", "groq", "gsk", "llama-3.3-70b-versatile", "", "https://api.groq.com/openai/v1"),
            ),
            activeId = "id-2",
        )
        val decoded = decodeCatalog(encodeCatalog(catalog))
        assertEquals(catalog, decoded)
        assertEquals(LlmCatalog(), decodeCatalog("{"))
        assertEquals(LlmCatalog(), catalogFromLegacy("openai", "", "gpt-4o"))
        val legacy = catalogFromLegacy("openai", " sk ", "gpt-4o")
        assertEquals("openai", legacy.models.single().providerId)
        assertEquals("sk", legacy.models.single().apiKey)
        assertEquals("https://api.openai.com/v1", legacy.models.single().baseUrl)
        assertEquals("legacy", legacy.activeId)
        assertFalse(legacy.models.isEmpty())
    }

    @Test
    fun chatUrlJoinsTheCompletionsPathOnce() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            chatCompletionsUrl("https://api.openai.com/v1"),
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            chatCompletionsUrl("https://api.openai.com/v1/"),
        )
        assertEquals(
            "https://api.x.ai/v1/chat/completions",
            chatCompletionsUrl("https://api.x.ai/v1/chat/completions"),
        )
    }

    @Test
    fun sseDeltaDoneAndError() {
        val delta = parseSseLine("""data: {"choices":[{"delta":{"content":"Hi"}}]}""")
        assertEquals(SseEvent.Delta("Hi"), delta)
        assertEquals(SseEvent.Done, parseSseLine("data: [DONE]"))
        val failure = parseSseLine("""data: {"error":{"message":"no key"}}""")
        assertEquals(SseEvent.Failure("no key"), failure)
        assertNull(parseSseLine("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""))
        val reasoning = parseSseLine(
            """data: {"choices":[{"delta":{"reasoning_content":"Still thinking"}}]}""",
        )
        assertEquals(SseEvent.Reasoning("Still thinking"), reasoning)
        val parts = parseSseLine(
            """data: {"choices":[{"delta":{"content":[{"type":"text","text":"Hi"}]}}]}""",
        )
        assertEquals(SseEvent.Delta("Hi"), parts)
        val details = parseSseLine(
            """data: {"choices":[{"delta":{"reasoning_details":[{"type":"reasoning.text","text":"Still thinking"}]}}]}""",
        )
        assertEquals(SseEvent.Reasoning("Still thinking"), details)
        val mentioned = parseSseLine(
            """data: {"choices":[{"delta":{"content":"see the \"error\" message"}}]}""",
        )
        assertEquals(SseEvent.Delta("see the \"error\" message"), mentioned)
    }

    @Test
    fun emptyContentUsesReasoningInsteadOfNoReply() {
        val json = """{"choices":[{"message":{"role":"assistant","content":null,"reasoning_content":"The answer is 4."}}]}"""
        val round = parseToolRound(json)
        assertEquals("The answer is 4.", (round as ToolRoundResult.Content).text)
        assertEquals("The answer is 4.", visibleReply(json))
    }

    @Test
    fun streamedToolCallFragmentsBecomeInternetSearch() {
        val calls = LinkedHashMap<Int, ToolCallAcc>()
        absorbStreamToolCalls(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"internet_search","arguments":"{\"qu"}}]}}]}""",
            calls,
        )
        absorbStreamToolCalls(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"ery\":\"Kotlin\"}"}}]}}]}""",
            calls,
        )
        val call = calls.getValue(0).toCall()
        assertEquals("internet_search", call?.name)
        assertEquals("Kotlin", extractToolQuery(call!!.arguments))
        val objectArgs = LinkedHashMap<Int, ToolCallAcc>()
        absorbStreamToolCalls(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_2","function":{"name":"internet_search","arguments":{"query":"Kotlin news"}}}]}}]}""",
            objectArgs,
        )
        assertEquals("Kotlin news", extractToolQuery(objectArgs.getValue(0).toCall()!!.arguments))
        val legacy = LinkedHashMap<Int, ToolCallAcc>()
        absorbStreamToolCalls(
            """{"choices":[{"delta":{"function_call":{"name":"internet_search","arguments":"{\"query\":\"headlines\"}"}}}]}""",
            legacy,
        )
        assertEquals("headlines", extractToolQuery(legacy.getValue(0).toCall()!!.arguments))
        val turn = llmTurnJson(
            LlmTurn.AssistantToolCalls(
                listOf(ToolCall("call_1", "internet_search", """{"query":"Kotlin"}""")),
                "Looking",
            ),
        )
        assertTrue(turn.contains("\"content\":\"Looking\""))
        assertFalse(turn.contains("\"content\":null"))
    }

    @Test
    fun jsonStringRoundTrips() {
        val raw = "line\n\"quote\""
        val wrapped = "{\"content\":${jsonString(raw)}}"
        assertEquals(raw, extractStringField(wrapped, "content"))
    }

    @Test
    fun completionBodyReadsTheMessage() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"Ready"}}]}"""
        assertEquals("Ready", parseCompletionBody(body))
        try {
            parseCompletionBody("""{"error":{"message":"bad model"}}""")
            fail("expected the error body to throw")
        } catch (error: ChatException) {
            assertEquals("bad model", error.message)
        }
    }

    @Test
    fun twelveChatsMakeOneStarAndATaskIsItsOwnStar() {
        val start = StarScore(reputation = 0, day = 1, chatCredit = 0)
        val eleven = (1..11).fold(start) { score, _ -> grantChat(score) }
        assertEquals(0, eleven.reputation)
        assertEquals(11, eleven.chatCredit)
        assertEquals(1, eleven.day)
        val twelfth = grantChat(eleven)
        assertEquals(1, twelfth.reputation)
        assertEquals(0, twelfth.chatCredit)
        val tasked = grantTaskStar(twelfth)
        assertEquals(2, tasked.reputation)
        assertEquals(0, tasked.chatCredit)
        val fourth = (1..2).fold(tasked) { score, _ -> grantTaskStar(score) }
        assertEquals(4, fourth.reputation)
        assertEquals(2, fourth.day)
    }

    @Test
    fun taskOrderBodyLeavesTheTitleAndBrief() {
        assertEquals(
            "Compare the ovens\n\nSize, price, and noise.",
            taskOrderBody("Task: Compare the ovens\n\nSize, price, and noise."),
        )
        assertEquals("File the receipts", taskOrderBody("Task: File the receipts"))
        assertEquals("A normal question", taskOrderBody("A normal question"))
    }

    @Test
    fun clockStartsTheWorkdayAtNine() {
        assertEquals("9:00", officeClock(0))
        assertEquals("9:01", officeClock(1_000))
        assertEquals("10:00", officeClock(60_000))
    }

    @Test
    fun spriteFramesCycleWalkAndType() {
        assertEquals("chars/pixel_walk1", spriteFrame("pixel", "walk", 5))
        // Desk sit/type use default sheets; canvas flips when seatFacing is left.
        assertEquals("chars/kai_type0", spriteFrame("kai", "type", 0))
        assertEquals("chars/kai_type1", spriteFrame("kai", "type", 6))
        assertEquals("chars/kai_type0", spriteFrame("kai", "type", 10)) // mod 2 cycle
        assertEquals("chars/byte_sit", spriteFrame("byte", "sit", 3))
        assertEquals("chars/byte_sit", spriteFrame("byte", "sit", 3, "front"))
        assertEquals("chars/byte_sit", spriteFrame("byte", "sit", 3, "left"))
        assertEquals("chars/you_walk0", spriteFrame("you", "walk", 0, "back"))
        assertEquals("chars/you_idle", spriteFrame("you", "idle", 0, "back"))
        assertEquals("chars/you_sit", spriteFrame("you", "sit", 0, "back"))
        assertEquals("chars/pixel_sleep", spriteFrame("pixel", "sleep", 0))
        assertEquals("chars/byte_sleep", spriteFrame("byte", "sleep", 3, "back"))
        assertEquals("chars/ops_sleep", spriteFrame("ops", "sleep", 1, "left"))
        assertEquals("chars/kai_type0", spriteFrame("kai", "type", 0, "left"))
        assertEquals("chars/kai_type1", spriteFrame("kai", "type", 6, "left"))
        assertEquals("chars/pixel_walk2", spriteFrame("pixel", "walk", 2, "left"))
    }

    @Test
    fun floorPortraitsBobOutOfStep() {
        assertEquals(1f, floorIdleLift(0.1f, 0), 0f)
        assertEquals(0f, floorIdleLift(0.6f, 0), 0f)
        assertTrue(floorIdleLift(0.1f, 0) != floorIdleLift(0.1f, 128))
    }

    @Test
    fun facingFromDeltaPicksBackWhenWalkingUp() {
        // Same-family / already aligned: no softener hop.
        assertEquals("back", facingFromDelta(0f, -1f, "back"))
        assertEquals("front", facingFromDelta(0f, 1f, "front"))
        assertEquals("left", facingFromDelta(-1f, 0f, "left"))
        assertEquals("right", facingFromDelta(1f, 0f, "right"))
        assertEquals("back", facingFromDelta(0.2f, -1f, "back"))
        assertEquals("front", facingFromDelta(0f, 0f, "front"))
        // Adjacent family switches immediately (90°).
        assertEquals("left", facingFromDelta(-1f, 0f, "back"))
        assertEquals("back", facingFromDelta(0f, -1f, "right"))
    }

    @Test
    fun facingHysteresisHoldsBackThroughSideNudge() {
        // Residual -x while mostly walking -y must not flick to left.
        assertEquals("back", facingFromDelta(-0.05f, -1f, "back"))
        assertEquals("back", facingFromDelta(-0.4f, -1f, "back"))
        // Clear side dominance may switch.
        assertEquals("left", facingFromDelta(-1f, -0.2f, "back"))
        // Tiny motion keeps current.
        assertEquals("back", facingFromDelta(-0.005f, -0.005f, "back"))
    }

    @Test
    fun oppositeFacingSnapsDirectlyWithoutSoftenerHop() {
        // Soft 180° intermediates are disabled — they caused front↔side↔back loops.
        assertEquals("back", facingFromDelta(0f, -1f, "front"))
        assertEquals("back", facingFromDelta(-0.3f, -1f, "front"))
        assertEquals("front", facingFromDelta(0f, 1f, "back"))
        assertEquals("front", facingFromDelta(-0.2f, 1f, "back"))
        assertEquals("right", facingFromDelta(1f, 0.1f, "left"))
        assertEquals("right", facingFromDelta(1f, -0.1f, "left"))
        assertEquals("back", softenFacingTurn("front", "back", 0.5f, -1f))
    }

    @Test
    fun seatFacingPointsFromChairTowardDeskComputer() {
        for (desk in 0 until OfficeMap.deskCount) {
            val seat = OfficeMap.chair(desk)
            val computer = OfficeMap.desks[desk]
            val expected = facingFromDelta(
                (computer.x - seat.x).toFloat(),
                (computer.y - seat.y).toFloat(),
            )
            assertEquals("desk $desk", expected, OfficeMap.seatFacing(desk))
        }
        // Current layout: every chair is desk+(1,0), so seat→computer is -x → left.
        assertEquals("left", OfficeMap.seatFacing(0))
        // Default sit/type sheets face +x; every work desk must mirror toward the laptop.
        for (desk in 0 until OfficeMap.deskCount) {
            assertTrue("desk $desk should mirror toward laptop", OfficeMap.seatMirrorX(desk))
        }
    }

    @Test
    fun seatedAgentsFaceTowardComputerAtTheirDesk() {
        val sim = OfficeSim(Random(2))
        val agents = listOf(SimAgent("a", "Pixel", "pixel", 0, "thinking"))
        // Walk far enough that they reach the chair and sit.
        repeat(120) { sim.tick(0.25f, agents) }
        val snap = sim.snapshot().actors.single()
        assertEquals("type", snap.anim)
        assertEquals(OfficeMap.seatFacing(0), snap.facing)
        val frame = spriteFrame(snap.spriteKey, snap.anim, snap.frame, snap.facing)
        assertTrue(frame.startsWith("chars/pixel_type"))
        assertFalse(frame.endsWith("_back"))
    }

    @Test
    fun idleSpriteKeepsFacesVisibleAfterABackFacingWalk() {
        assertEquals("chars/pixel_idle", spriteFrame("pixel", "idle", 0, "back"))
    }

    @Test
    fun seatedFacingStaysTowardComputerOnDefaultTypeCycle() {
        val sim = OfficeSim(Random(5))
        val agents = listOf(SimAgent("a", "Kai", "kai", 1, "writing"))
        val towardComputer = OfficeMap.seatFacing(1)
        repeat(80) { sim.tick(0.25f, agents) }
        val facings = ArrayList<String>()
        val frames = ArrayList<String>()
        repeat(90) {
            sim.tick(1f / 60f, agents)
            val snap = sim.snapshot().actors.single()
            assertTrue("expected sit/type, got ${snap.anim}", snap.anim == "sit" || snap.anim == "type")
            assertEquals(towardComputer, snap.facing)
            facings.add(snap.facing)
            frames.add(spriteFrame(snap.spriteKey, snap.anim, snap.frame, snap.facing))
        }
        assertTrue(facings.all { it == towardComputer })
        assertTrue(frames.none { it.endsWith("_back") })
        assertTrue(frames.all { "_sit" in it || "_type" in it })
        // Typing should advance through default type sheets.
        val typeFrames = frames.filter { "_type" in it }.toSet()
        assertTrue("expected type cycle, got $typeFrames", typeFrames.size >= 2)
    }

    @Test
    fun walkingFacingDoesNotOscillate() {
        val sim = OfficeSim(Random(3))
        val agents = listOf(SimAgent("a", "Pixel", "pixel", 0, "walking"))
        val facings = ArrayList<String>()
        repeat(100) {
            sim.tick(0.1f, agents)
            val snap = sim.snapshot().actors.single()
            if (snap.anim == "walk") {
                facings.add(snap.facing)
            }
        }
        assertTrue("expected some walk frames", facings.size > 5)
        // No A→B→A flicker across consecutive walk samples.
        for (i in 0 until facings.size - 2) {
            val a = facings[i]
            val b = facings[i + 1]
            val c = facings[i + 2]
            assertFalse(
                "facing flicker $a→$b→$c at walk sample $i",
                a == c && a != b,
            )
        }
    }

    @Test
    fun walkingFacingChangesOnlyWithPathSegmentsNotSubsteps() {
        // 60fps walk door→desk: facing must track path legs, not axis-lock residuals.
        // A corridor snake can legitimately revisit a cardinal (left→back→left);
        // spin is a SHORT run that flicks back↔side within the hold window.
        val sim = OfficeSim(Random(7))
        val agents = listOf(SimAgent("a", "Pixel", "pixel", 4, "walking"))
        val facings = ArrayList<String>()
        var flips = 0
        var prev: String? = null
        repeat(600) {
            sim.tick(1f / 60f, agents)
            val snap = sim.snapshot().actors.single()
            if (snap.anim == "walk") {
                facings.add(snap.facing)
                if (prev != null && snap.facing != prev) {
                    flips++
                }
                prev = snap.facing
            }
        }
        assertTrue("expected a walk", facings.size > 30)
        // Desk 4 path has ~4 cardinal legs; substep chatter used to flip dozens of times.
        assertTrue("facing flipped too often ($flips): spin from substep chatter", flips <= 6)

        data class Run(val facing: String, val frames: Int)
        val runs = ArrayList<Run>()
        for (f in facings) {
            if (runs.isEmpty() || runs.last().facing != f) {
                runs.add(Run(f, 1))
            } else {
                runs[runs.lastIndex] = Run(f, runs.last().frames + 1)
            }
        }
        // Every completed facing pose (not the final possibly-cut run) must outlast
        // the 0.45s hold — proves we are not flipping every tick/substep.
        val minHoldFrames = 24 // 0.4s at 60fps
        for (i in 0 until runs.size - 1) {
            assertTrue(
                "facing run too short: ${runs[i].facing}×${runs[i].frames} in $runs",
                runs[i].frames >= minHoldFrames,
            )
        }
        // back↔left canvas-flip pairs only at real segment boundaries (few).
        var backLeftEdges = 0
        for (i in 0 until runs.size - 1) {
            val a = runs[i].facing
            val b = runs[i + 1].facing
            if ((a == "back" && b == "left") || (a == "left" && b == "back")) {
                backLeftEdges++
            }
        }
        assertTrue("back↔left edges=$backLeftEdges runs=$runs", backLeftEdges <= 4)
    }

    @Test
    fun idleWanderFacingHoldsPoseBetweenTurns() {
        val sim = OfficeSim(Random(11))
        val agents = listOf(SimAgent("a", "Pixel", "pixel", null, "idle"))
        val facings = ArrayList<String>()
        repeat(800) {
            sim.tick(1f / 60f, agents)
            val snap = sim.snapshot().actors.single()
            if (snap.anim == "walk") {
                facings.add(snap.facing)
            }
        }
        assertTrue("expected idle wander walk frames", facings.size > 20)
        data class Run(val facing: String, val frames: Int)
        val runs = ArrayList<Run>()
        for (f in facings) {
            if (runs.isEmpty() || runs.last().facing != f) {
                runs.add(Run(f, 1))
            } else {
                runs[runs.lastIndex] = Run(f, runs.last().frames + 1)
            }
        }
        // Facing hold is ~0.45s (~27 frames @60fps). With denser idle
        // retargets (wander + shorter cooldowns), allow a 1-frame-under
        // run but still reject flicker (<20 frames ≈ 0.33s).
        val minHoldFrames = 20
        var shortRuns = 0
        for (i in 0 until runs.size - 1) {
            if (runs[i].frames < minHoldFrames) {
                shortRuns++
            }
        }
        assertTrue(
            "idle wander short facing runs=$shortRuns detail=$runs",
            shortRuns == 0,
        )
    }

    @Test
    fun residualAxisCorrectionDoesNotFlickFacing() {
        // Reproduce pre-fix spin: motion-based facing flicked to the residual
        // axis after each primary-axis arrival. Segment facing must stay put.
        // Walk a long -y corridor (desk 6) and ensure no single-frame side flick.
        val sim = OfficeSim(Random(2))
        val agents = listOf(SimAgent("a", "Pixel", "pixel", 6, "walking"))
        var prev: String? = null
        var hold = 0
        var minGap = Int.MAX_VALUE
        repeat(500) {
            sim.tick(1f / 60f, agents)
            val snap = sim.snapshot().actors.single()
            if (snap.anim != "walk") {
                return@repeat
            }
            if (prev != null && snap.facing != prev) {
                if (hold > 0) {
                    minGap = minOf(minGap, hold)
                }
                hold = 0
            } else {
                hold++
            }
            prev = snap.facing
        }
        assertTrue("expected facing changes on the route", minGap < Int.MAX_VALUE)
        assertTrue("facing changed again after only $minGap frames", minGap >= 24)
    }

    @Test
    fun typingAgentsLightTheirDeskScreens() {
        val sim = OfficeSim(Random(9))
        val agents = listOf(
            SimAgent("a", "Pixel", "pixel", 0, "thinking"),
            SimAgent("b", "Kai", "kai", 2, "done"),
            SimAgent("c", "Byte", "byte", 4, "idle"),
        )
        repeat(120) { sim.tick(0.25f, agents) }
        val snap = sim.snapshot()
        val byId = snap.actors.associateBy { it.id }
        assertEquals("type", byId.getValue("a").anim)
        assertEquals(0, byId.getValue("a").deskIndex)
        assertEquals("sit", byId.getValue("b").anim)
        assertEquals(2, byId.getValue("b").deskIndex)
        // Idle agent is assigned desk 4. During lounge pretend/sofa the snap may
        // export a vacant pretend desk, null (sleep), or keep 4 while wandering.
        val idleDesk = byId.getValue("c").deskIndex
        val idleAnim = byId.getValue("c").anim
        assertTrue(
            "idle c desk=$idleDesk anim=$idleAnim",
            idleDesk == null || idleDesk == 4 || idleDesk !in setOf(0, 2),
        )

        val lit = desksWithActiveScreens(snap.actors)
        assertTrue("real thinker must light desk 0, lit=$lit", 0 in lit.keys)
        assertTrue(lit.getValue(0) >= 0)
        // Done-sitter stays dark; pretend typing may also light its vacant desk.
        assertTrue("done desk must stay dark, lit=$lit", 2 !in lit.keys)
        assertTrue(
            "unexpected lit desks $lit (idleAnim=$idleAnim idleDesk=$idleDesk)",
            lit.keys.all { it == 0 || it == idleDesk },
        )
    }

    @Test
    fun idleSitAndEmptyDesksKeepScreensDark() {
        val actors = listOf(
            ActorSnap("a", "Pixel", "pixel", 1f, 0f, "sit", 3, "left", "★", 0),
            ActorSnap("b", "Kai", "kai", 3f, 0f, "walk", 1, "front", null, 1),
            ActorSnap("c", "Byte", "byte", 5f, 4f, "type", 8, "left", "</>", 3),
            ActorSnap("d", "Ops", "ops", 0f, 6f, "idle", 0, "front", null, null),
        )
        val lit = desksWithActiveScreens(actors)
        assertEquals(setOf(3), lit.keys)
        assertEquals(8, lit.getValue(3))
    }

    @Test
    fun coffeeSteamWispsRiseAndFade() {
        val wisps = coffeeSteamWisps()
        assertTrue(wisps.isNotEmpty())
        assertTrue(wisps.all { it.periodMs > 0 })
        assertTrue(wisps.all { it.rise > 0f && it.size > 0f })
        // Origins sit on the carafe / drip head (crop-relative mid-upper).
        assertTrue(wisps.all { it.x in 0.45f..0.55f })
        assertTrue(wisps.all { it.y in 0.14f..0.22f })
        val wisp = wisps.first()
        val samples = (0..16).map { coffeeSteamProgress(wisp, it * 100L) }
        assertTrue(samples.any { it < 0.2f })
        assertTrue(samples.any { it > 0.5f })
        assertEquals(samples.first(), samples.first().mod(1f), 0.0001f)
    }

    @Test
    fun serverFanSitsHigherOnTheRoof() {
        assertEquals(0.10f, ServerFanRoofY, 0.001f)
        assertTrue(ServerFanRoofY < 0.16f)
    }

    @Test
    fun serverFanBladesSpinInsideAStillFrame() {
        assertEquals(0.80f, ServerFanRotorFraction, 0.001f)
        val width = 48
        val height = 48
        val magenta = 0xFFFF00FF.toInt()
        val housing = 0xFF3A3E4A.toInt()
        val screw = 0xFFF2F2F4.toInt()
        val blade = 0xFF27A1FC.toInt()
        val wind = 0xFF5ED7FC.toInt()
        val pixels = IntArray(width * height) { magenta }
        for (y in 12 until 36) {
            for (x in 12 until 36) {
                pixels[y * width + x] = housing
            }
        }
        pixels[14 * width + 14] = screw
        pixels[14 * width + 33] = screw
        pixels[33 * width + 14] = screw
        pixels[33 * width + 33] = screw
        pixels[24 * width + 18] = blade
        pixels[24 * width + 32] = blade
        pixels[24 * width + 2] = wind
        pixels[2 * width + 24] = wind

        val split = splitServerFan(pixels, width, height)
        assertEquals(23.5f, split.centerX, 0.01f)
        assertEquals(23.5f, split.centerY, 0.01f)
        assertEquals(blade, split.rotor[24 * width + 18])
        assertEquals(0, split.frame[24 * width + 18])
        assertEquals(blade, split.rotor[24 * width + 32])
        assertEquals(0, split.frame[24 * width + 32])
        assertEquals(screw, split.frame[14 * width + 14])
        assertEquals(0, split.rotor[14 * width + 14])
        assertEquals(screw, split.frame[33 * width + 33])
        assertEquals(0, split.rotor[33 * width + 33])
        assertEquals(0, split.frame[24 * width + 2])
        assertEquals(0, split.rotor[24 * width + 2])
        assertEquals(0, split.frame[2 * width + 24])
        assertEquals(0, split.rotor[2 * width + 24])
        assertEquals(housing, split.frame[12 * width + 24])
        assertEquals(0, split.rotor[12 * width + 24])
        assertEquals(0, split.frame[24 * width + 24])
        assertEquals(housing or 0xFF000000.toInt(), split.rotor[24 * width + 24])
    }

    @Test
    fun nearbyAgentInspectsTheFanWhileItSpins() {
        assertTrue(FanInspectRadius < 2.2f)
        var now = 0L
        val sim = OfficeSim(Random(1), nowMs = { now })
        val walking = listOf(SimAgent("a", "Pixel", "pixel", 7, "walking"))
        repeat(120) { sim.tick(0.25f, walking) }
        val idle = listOf(SimAgent("a", "Pixel", "pixel", 7, "idle"))
        var looked = false
        repeat(80) {
            sim.tick(0.1f, idle)
            val actor = sim.snapshot().actors.single()
            val apart = kotlin.math.hypot(
                (actor.x - OfficeMap.server.x).toDouble(),
                (actor.y - OfficeMap.server.y).toDouble(),
            )
            if (actor.bubble in officeFanInspectLines && actor.anim != "walk" && apart <= FanInspectRadius + 0.2) {
                looked = true
                assertEquals("right", actor.facing)
            }
        }
        assertTrue("expected a nearby agent to inspect the spinning fan", looked)
        now = 5_000L
        repeat(10) { sim.tick(0.1f, idle) }
        val after = sim.snapshot().actors.single()
        assertTrue(after.bubble !in officeFanInspectLines)

        val far = OfficeSim(Random(2), nowMs = { 0L })
        val door = listOf(SimAgent("a", "Pixel", "pixel", null, "idle"))
        var farLook = false
        repeat(6) {
            far.tick(0.05f, door)
            val actor = far.snapshot().actors.single()
            if (actor.bubble in officeFanInspectLines) {
                farLook = true
            }
        }
        assertFalse(farLook)
    }

    @Test
    fun serverFanSpinsForThreeSecondsThenHides() {
        assertEquals(3_000L, ServerFanRunMs)
        assertTrue(serverFanGapMs.any { it != serverFanGapMs[0] })
        val start = serverFanSpin(0L)
        assertTrue(start != null && start.degrees == 0f)
        val mid = serverFanSpin(1_500L)
        assertEquals(360f * ServerFanTurns / 2f, mid!!.degrees, 0.01f)
        assertTrue(mid.alpha > 0.9f)
        assertTrue(serverFanSpin(2_999L) != null)
        assertEquals(null, serverFanSpin(3_000L))
        val afterGap = ServerFanRunMs + serverFanGapMs[0]
        assertEquals(null, serverFanSpin(afterGap - 1))
        val next = serverFanSpin(afterGap)
        assertTrue(next != null && next.degrees == 0f)
        val cycle = serverFanCycleMs()
        assertEquals(start!!.degrees, serverFanSpin(cycle)!!.degrees, 0.01f)
        assertEquals(null, serverFanSpin(cycle + ServerFanRunMs))
    }

    @Test
    fun serverLedsAsyncBlinkDotsOnly() {
        val leds = serverLeds()
        assertTrue(leds.size >= 12)
        assertTrue(leds.none { it.bar })
        assertTrue(leds.any { it.green })
        assertTrue(leds.any { !it.green })
        val dot = leds.first()
        // Dot strength flips across its period — sample enough offsets.
        val samples = (0..20).map { serverLedStrength(dot, it * 50L + (dot.phase * dot.periodMs).toLong()) }
        assertTrue(samples.any { it > 0.9f })
        assertTrue(samples.any { it < 0.2f })
    }

    @Test
    fun idleAgentsPickOfficeActivitiesInsteadOfAimlessLoungeHop() {
        val sim = OfficeSim(Random(42))
        val agents = listOf(
            SimAgent("a", "Pixel", "pixel", 0, "idle"),
            SimAgent("b", "Kai", "kai", 1, "idle"),
            SimAgent("c", "Byte", "byte", 2, "idle"),
        )
        val anims = HashSet<String>()
        val bubbles = HashSet<String>()
        var sawTypeAtDesk = false
        var sawSofaSleep = false
        repeat(6000) {
            sim.tick(1f / 30f, agents)
            for (actor in sim.snapshot().actors) {
                anims.add(actor.anim)
                actor.bubble?.let { bubbles.add(it) }
                if (actor.anim == "type" && actor.deskIndex != null) {
                    sawTypeAtDesk = true
                }
                if (actor.anim == "sleep" && !actor.useSeatTuck) {
                    sawSofaSleep = true
                }
            }
        }
        // Should walk to props and perform sleep/type, not only idle.
        assertTrue("expected walk during idle activities, got $anims", "walk" in anims)
        assertTrue(
            "expected sleep or type from sofa/pretend, got $anims",
            "sleep" in anims || "type" in anims,
        )
        assertTrue("expected social/status bubbles, got $bubbles", bubbles.isNotEmpty())
        assertTrue("expected pretend-work typing at a desk", sawTypeAtDesk)
        assertTrue("expected sofa doze sleep sprite without desk tuck", sawSofaSleep)
    }

    @Test
    fun bubbleSpeechDefaultMatchesTheOriginalSizeAndGrowsUpward() {
        val name = 20f
        val span = 80f
        val base = bubbleSpeechLayout(name, span, BubbleSpeechScaleDefault)
        assertEquals(name, base.textSize, 0.01f)
        assertEquals(12f, base.minText, 0.01f)
        assertEquals(span * 3.4f, base.maxWidth, 0.01f)
        assertEquals(name * 1.35f, base.height, 0.01f)
        assertEquals(name * 0.35f, base.corner, 0.01f)
        assertEquals(-name * 0.95f, base.bottomFromSpriteTop, 0.01f)
        val big = bubbleSpeechLayout(name, span, 2f)
        assertEquals(base.textSize * 2f, big.textSize, 0.01f)
        assertEquals(base.height * 2f, big.height, 0.01f)
        assertEquals(base.bottomFromSpriteTop, big.bottomFromSpriteTop, 0.01f)
        assertEquals(name * BubbleSpeechScaleMax, bubbleSpeechLayout(name, span, 9f).textSize, 0.01f)
        assertEquals(BubbleSpeechScaleDefault, coerceBubbleSpeechScale(Float.NaN), 0.001f)
        assertEquals(120f, bubbleSpeechLayout(30f, 10f, 1f).maxWidth, 0.01f)
    }

    @Test
    fun officeBanterLinesAreShortAndTheModelGateIsRare() {
        assertEquals(20, BanterLlmOdds)
        assertTrue(officeChatExchanges.size >= 16)
        assertEquals(officeChatExchanges.sumOf { it.size }, officeChatLines.size)
        assertTrue(officeChatExchanges.all { it.size >= 4 && it.size % 2 == 0 })
        assertTrue(officeChatLines.all { line -> line.length in 2..42 && line.any { it.isLetter() } })
        val random = Random(99)
        val hits = (1..20_000).count { banterLlmRoll(random) }
        assertTrue("expected about 5% model lines, got $hits", hits in 850..1150)
        val voice = "x".repeat(400)
        val system = banterSystemPrompt("Pixel", voice)
        assertTrue(system.length < 180)
        assertTrue(system.contains("under 8 words"))
        assertFalse(system.contains(voice))
        val user = banterUserPrompt("Kai", "y".repeat(300))
        assertTrue(user.length < 140)
        assertTrue(user.startsWith("Kai said:"))
        assertEquals("Say one short line to Kai.", banterUserPrompt("Kai", ""))
        assertEquals("Ship it today!", clipBanterLine("  \"Ship it today!\" Then ignore this extra sentence."))
        assertEquals(null, clipBanterLine("(No reply)"))
        assertEquals(null, clipBanterLine(""))
        assertEquals(
            null,
            clipBanterLine(
                "The user is asking me to find news on Maybank. Let me call internet_search with query \"Maybank latest news\". I'll then report.",
            ),
        )
    }

    @Test
    fun mapStaysPutWhenTheChatPanelSlidesUp() {
        assertFalse(officeCameraShouldRefit(true, 400f, 800f, 400f, 300f, userFramed = false))
        assertTrue(officeCameraShouldRefit(true, 400f, 800f, 400f, 900f, userFramed = false))
        assertFalse(officeCameraShouldRefit(true, 400f, 800f, 400f, 900f, userFramed = true))
        assertTrue(officeCameraShouldRefit(true, 400f, 800f, 520f, 300f, userFramed = true))
        assertFalse(officeCameraShouldRefit(true, 400f, 800f, 402f, 40f, userFramed = false))
        assertFalse(officeCameraShouldRefit(false, 0f, 0f, 0f, 0f, userFramed = false))
        assertFalse(officeCameraShouldRefit(false, 0f, 0f, 400f, 20f, userFramed = false))
        assertTrue(officeCameraShouldRefit(false, 0f, 0f, 400f, 800f, userFramed = false))

        val camera = OfficeCamera()
        camera.present(400f, 800f, 96f, 48f, 1f, Offset.Zero)
        camera.present(400f, 800f, 96f, 48f, 2f, Offset(12f, -8f))
        val framed = camera.toScreen(5f, 5f)
        camera.present(400f, 280f, 96f, 48f, 2f, Offset(12f, -8f))
        val covered = camera.toScreen(5f, 5f)
        assertEquals(framed.x, covered.x, 0.01f)
        assertEquals(framed.y, covered.y, 0.01f)
        assertEquals(800f, camera.fitHeight, 0.01f)
        camera.present(402f, 36f, 96f, 48f, 2f, Offset(12f, -8f))
        val jittered = camera.toScreen(5f, 5f)
        assertEquals(framed.x, jittered.x, 0.01f)
        assertEquals(framed.y, jittered.y, 0.01f)
        assertEquals(800f, camera.fitHeight, 0.01f)
    }

    @Test
    fun idleAgentsWalkOverAndTradeShortSentences() {
        val sim = OfficeSim(Random(42), llmGate = { false })
        val agents = listOf(
            SimAgent("a", "Pixel", "pixel", 0, "idle"),
            SimAgent("b", "Kai", "kai", 1, "idle"),
            SimAgent("c", "Byte", "byte", 2, "idle"),
        )
        val said = HashSet<String>()
        var sawClose = false
        var sawChat = false
        repeat(6_000) {
            sim.tick(1f / 30f, agents)
            assertTrue(sim.consumeBanterAsks().isEmpty())
            val chatting = sim.snapshot().actors.filter { it.chatting }
            if (chatting.size >= 2) {
                sawChat = true
            }
            val lines = chatting.mapNotNull { it.bubble }
            assertTrue("two bubbles at once: $lines", lines.size <= 1)
            for (line in lines) {
                assertTrue("off-script bubble $line", BanterBook.isChatLine(line, listOf("Pixel", "Kai", "Byte")))
                said.add(line)
            }
            if (chatting.size >= 2 && chatting.all { it.anim != "walk" }) {
                val a = chatting[0]
                val b = chatting[1]
                val apart = kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.y - b.y)
                if (apart < 1.7f) {
                    sawClose = true
                }
            }
        }
        assertTrue("expected a chat to start", sawChat)
        assertTrue("expected agents to stand together and talk, lines=$said", sawClose)
        assertTrue("expected a range of lines, got ${said.size}: $said", said.size >= 4)
    }

    @Test
    fun modelBanterReplacesOnlyTheOpenLine() {
        val sim = OfficeSim(Random(4), llmGate = { true })
        val agents = listOf(
            SimAgent("a", "Pixel", "pixel", 0, "idle"),
            SimAgent("b", "Kai", "kai", 1, "idle"),
        )
        var replaced = false
        for (step in 1..4_000) {
            sim.tick(1f / 30f, agents)
            val asks = sim.consumeBanterAsks()
            if (asks.isEmpty()) {
                continue
            }
            val ask = asks.first()
            val before = sim.snapshot().actors.first { it.id == ask.agentId }.bubble
            assertTrue(BanterBook.isChatLine(before.orEmpty(), listOf("Pixel", "Kai")))
            sim.offerBanterLine(ask.agentId, ask.token + 9, "Ship it today!")
            assertEquals(before, sim.snapshot().actors.first { it.id == ask.agentId }.bubble)
            sim.offerBanterLine(
                ask.agentId,
                ask.token,
                "The user is asking me to search. Let me call internet_search and then write a long plan for the office.",
            )
            assertEquals(before, sim.snapshot().actors.first { it.id == ask.agentId }.bubble)
            sim.offerBanterLine(ask.agentId, ask.token, "Ship it today! And then a whole second sentence.")
            assertEquals("Ship it today!", sim.snapshot().actors.first { it.id == ask.agentId }.bubble)
            assertTrue(sim.banterStillOpen(ask.token))
            replaced = true
            break
        }
        assertTrue("expected a model ask during chat", replaced)
    }

    @Test
    fun realWorkPreemptsIdleActivitiesAndReturnsToDesk() {
        val sim = OfficeSim(Random(3))
        val idle = listOf(SimAgent("a", "Pixel", "pixel", 0, "idle"))
        // Let them start an idle activity.
        repeat(200) { sim.tick(0.25f, idle) }
        val working = listOf(SimAgent("a", "Pixel", "pixel", 0, "thinking"))
        repeat(80) { sim.tick(0.25f, working) }
        val snap = sim.snapshot().actors.single()
        assertEquals("type", snap.anim)
        assertEquals("left", snap.facing)
        assertEquals(0, snap.deskIndex)
        assertTrue(snap.useSeatTuck)
        // Status icon and/or rotating work-mood bubbles while typing.
        val seen = HashSet<String>()
        repeat(400) {
            sim.tick(0.25f, working)
            sim.snapshot().actors.single().bubble?.let { seen.add(it) }
        }
        assertTrue("expected thinking status icon ··· among bubbles, got $seen", "···" in seen)
        assertTrue(
            "expected rotating work-mood bubbles while typing, got $seen",
            seen.size >= 3,
        )
        assertTrue(
            "expected fire/think/frustrated moods, got $seen",
            seen.any { it in setOf("🔥", "🤔", "😤", "☕?", "focus", "grind", "stuck", "💪") },
        )
    }

    @Test
    fun coffeeApproachTilesAreWalkableBesideTheMachine() {
        val blocked = OfficeMap.blocked()
        assertTrue(OfficeMap.coffeeApproach.isNotEmpty())
        for (spot in OfficeMap.coffeeApproach) {
            assertTrue("$spot", OfficeMap.inBounds(spot))
            assertTrue("$spot", spot !in blocked)
            val path = findPath(OfficeMap.width, OfficeMap.height, blocked, OfficeMap.door, spot)
            assertTrue("$spot reachable", path.isNotEmpty() || spot == OfficeMap.door)
            assertTrue(path.none { it in blocked })
        }
    }

    @Test
    fun fridgeApproachIsTheTileImmediatelyRight() {
        val blocked = OfficeMap.blocked()
        assertEquals(listOf(Cell(7, 7)), OfficeMap.fridgeApproach)
        assertEquals(Cell(OfficeMap.fridge.x + 1, OfficeMap.fridge.y), OfficeMap.fridgeApproach.single())
        val spot = OfficeMap.fridgeApproach.single()
        assertTrue(spot !in blocked)
        assertTrue(OfficeMap.fridge in blocked)
        val path = findPath(OfficeMap.width, OfficeMap.height, blocked, OfficeMap.door, spot)
        assertTrue(path.isNotEmpty())
        assertEquals(spot, path.last())
        assertTrue(path.none { it in blocked })
    }

    @Test
    fun screenCodeGlyphsCycleWithoutHeavyAllocShape() {
        val a = screenCodeGlyphs(0)
        val b = screenCodeGlyphs(1)
        val c = screenCodeGlyphs(12)
        assertTrue(a.isNotEmpty())
        // Prompt stub + typed tokens; 6 rows, a few extra tokens.
        assertTrue(a.size <= 18)
        assertTrue(a.all { it.row in 0..5 && it.start < it.end && it.end <= 1f })
        assertTrue(a.any { it.prompt })
        // Frame advances change the pattern; full cycle of 12 repeats.
        assertTrue(a != b)
        assertEquals(a, c)
    }



    @Test
    fun sofaSleepNudgesArePerSlot() {
        val left = OfficeMap.sleepNudgeFor(3f, 9f)
        val right = OfficeMap.sleepNudgeFor(4f, 9f)
        assertEquals(0.16f, left.dxTileW, 0.001f)
        assertEquals(-0.38f, left.nestTileH, 0.001f)
        assertEquals(-0.16f, right.dxTileW, 0.001f)
        assertEquals(-0.88f, right.nestTileH, 0.001f)
        // Nearest-span fallback keeps a sleeper on a cushion even if slightly off-tile.
        val nearLeft = OfficeMap.sleepNudgeFor(2.7f, 9.1f)
        assertEquals(left, nearLeft)
        val bag = OfficeMap.sleepNudgeFor(1f, 8f)
        assertEquals(0f, bag.dxTileW, 0.001f)
        assertEquals(-0.50f, bag.nestTileH, 0.001f)
    }

    @Test
    fun sofaSeatSlotsAreExclusive() {
        val sim = OfficeSim(Random(11))
        val agents = listOf(
            SimAgent("a", "Pixel", "pixel", 0, "idle"),
            SimAgent("b", "Kai", "kai", 1, "idle"),
            SimAgent("c", "Byte", "byte", 2, "idle"),
            SimAgent("d", "Ops", "ops", 3, "idle"),
        )
        val occupiedPairs = HashSet<Set<Pair<Int, Int>>>()
        var sawTwoSleepers = false
        var sawBeanbag = false
        repeat(20000) {
            sim.tick(1f / 30f, agents)
            val sleepers = sim.snapshot().actors.filter { it.anim == "sleep" }
            if (sleepers.size >= 2) {
                sawTwoSleepers = true
            }
            if (sleepers.any { Cell(it.x.roundToInt(), it.y.roundToInt()) == OfficeMap.beanbag }) {
                sawBeanbag = true
            }
            // No two sleepers may share the same rounded tile.
            val cells = sleepers.map {
                it.x.roundToInt() to it.y.roundToInt()
            }
            assertEquals(
                "overlapping sofa sleepers at $cells",
                cells.size,
                cells.toSet().size,
            )
            if (cells.isNotEmpty()) {
                occupiedPairs.add(cells.toSet())
            }
            // Loveseat cushions plus one beanbag.
            assertTrue(sleepers.size <= OfficeMap.sofaSpan.size + 1)
            val onBag = sleepers.count {
                Cell(it.x.roundToInt(), it.y.roundToInt()) == OfficeMap.beanbag
            }
            assertTrue("more than one beanbag sleeper", onBag <= 1)
            for (actor in sleepers) {
                val cell = Cell(actor.x.roundToInt(), actor.y.roundToInt())
                assertTrue(
                    "sleeper off sofa/beanbag at $cell",
                    cell in OfficeMap.sofaSpan || cell == OfficeMap.beanbag,
                )
                assertTrue(!actor.useSeatTuck)
            }
        }
        assertTrue("expected at least one pair of simultaneous sofa sleepers", sawTwoSleepers)
        assertTrue("expected a beanbag nap using the sleep sprite", sawBeanbag)
        assertTrue(occupiedPairs.isNotEmpty())
    }

    @Test
    fun internetSearchToolIsOfferedInPromptWhenEnabled() {
        val tools = enabledCommonTools(internetSearchEnabled = true, timeToolEnabled = false)
        assertEquals(listOf("internet_search"), tools.map { it.id })
        val prompt = buildSystemPrompt(
            name = "Pixel",
            personality = "Curious.",
            duties = "Check facts",
            taskTitle = null,
            taskBrief = null,
            enabledTools = tools,
        )
        assertTrue(prompt.contains("internet_search"))
        assertTrue(prompt.contains("up-to-date") || prompt.contains("current facts"))
        assertTrue(prompt.contains("already available"))
        assertFalse(prompt.contains("Call internet_search"))
        val toolJson = agentToolJson(tools.first())
        assertTrue(toolJson.contains("\"additionalProperties\":false"))
        assertTrue(toolJson.contains("Web search query"))
        val off = buildSystemPrompt(
            name = "Pixel",
            personality = "Curious.",
            duties = "Check facts",
            taskTitle = null,
            taskBrief = null,
            enabledTools = enabledCommonTools(internetSearchEnabled = false, timeToolEnabled = false),
        )
        assertFalse(off.contains("internet_search"))
    }

    @Test
    fun timeToolReadsThePhoneClockAndConvertsPlaces() {
        val clock = java.time.Clock.fixed(
            java.time.Instant.parse("2026-01-15T10:00:00Z"),
            java.time.ZoneId.of("Asia/Kuala_Lumpur"),
        )
        val here = timeTool("{}", clock)
        assertTrue(here.contains("Current time on this phone"))
        assertTrue(here.contains("Thursday, 15 January 2026, 6:00 PM"))
        assertTrue(here.contains("Asia/Kuala_Lumpur"))
        assertTrue(here.contains("10:00 AM"))
        val tokyo = timeTool("""{"place":"Tokyo","time":"","from_place":""}""", clock)
        assertTrue(tokyo.contains("7:00 PM"))
        assertTrue(tokyo.contains("Asia/Tokyo"))
        assertTrue(tokyo.contains("1 hour ahead"))
        val converted = timeTool(
            """{"place":"Tokyo","time":"15:30","from_place":"London"}""",
            clock,
        )
        assertTrue(converted.contains("Thursday, 15 January 2026, 3:30 PM"))
        assertTrue(converted.contains("Europe/London"))
        assertTrue(converted.contains("Friday, 16 January 2026, 12:30 AM"))
        assertTrue(converted.contains("9 hours ahead"))
        val afternoon = timeTool(
            """{"place":"London","time":"3:30 PM","from_place":"New York"}""",
            clock,
        )
        assertTrue(afternoon.contains("8:30 PM"))
        assertTrue(afternoon.contains("Europe/London"))
        val offset = timeTool("""{"place":"UTC+8","time":"","from_place":""}""", clock)
        assertTrue(offset.contains("6:00 PM"))
        assertTrue(offset.contains("UTC+8"))
        val ambiguous = timeTool("""{"place":"CST","time":"","from_place":""}""", clock)
        assertTrue(ambiguous.contains("America/Chicago"))
        assertTrue(ambiguous.contains("Asia/Shanghai"))
        val missing = timeTool("""{"place":"Atlantis","time":"","from_place":""}""", clock)
        assertTrue(missing.contains("No time zone matched"))
        val planned = timeArgumentsFromContext(
            "The user is asking what time is it in Tokyo. Let me call time_tool.",
            listOf(TurnMessage("user", "what time is it in Tokyo?", null)),
        )
        assertTrue(planned.orEmpty().contains("Tokyo"))
        assertEquals(null, timeArgumentsFromContext("We need answer user. Let's do search.", emptyList()))
        val both = enabledCommonTools(internetSearchEnabled = true, timeToolEnabled = true)
        assertEquals(listOf("internet_search", "time_tool"), both.map { it.id })
        val timeJson = agentToolJson(both.first { it.id == "time_tool" })
        assertTrue(timeJson.contains("\"additionalProperties\":false"))
        assertTrue(timeJson.contains("\"required\":[\"place\",\"time\",\"from_place\"]"))
        val prompt = buildSystemPrompt("Pixel", "Curious.", "Check facts", null, null, both)
        assertTrue(prompt.contains("time_tool"))
        assertTrue(prompt.contains("Do not guess the clock"))
    }

    @Test
    fun toolRoundParsesFunctionCalls() {
        val json = """
            {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
              {"id":"call_1","type":"function","function":{"name":"internet_search","arguments":"{\"query\":\"Kotlin\"}"}}
            ]},"finish_reason":"tool_calls"}]}
        """.trimIndent()
        val round = parseToolRound(json)
        val calls = (round as ToolRoundResult.Calls).calls
        assertEquals(1, calls.size)
        assertEquals("internet_search", calls[0].name)
        assertEquals("Kotlin", extractToolQuery(calls[0].arguments))
    }

    @Test
    fun schemaGrammarIsNotASearchQuery() {
        val schema = """{"type":"object","properties":{"query":{"type":"string","description":"Web search query"}},"required":["query"]}"""
        assertTrue(isJsonSchema(schema))
        assertEquals("", extractToolQuery(schema))
        val glued = schema + """{"query":"maybank latest news"}"""
        assertEquals("maybank latest news", extractToolQuery(glued))
        val calls = LinkedHashMap<Int, ToolCallAcc>()
        absorbStreamToolCalls(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"internet_search","arguments":${app.pixelbureau.logic.jsonString(schema)}}}]}}]}""",
            calls,
        )
        absorbStreamToolCalls(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"query\":\"maybank latest news\"}"}}]}}]}""",
            calls,
        )
        assertEquals("maybank latest news", extractToolQuery(calls.getValue(0).toCall()!!.arguments))
        val parameters = LinkedHashMap<Int, ToolCallAcc>()
        absorbStreamToolCalls(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_9","function":{"name":"internet_search","parameters":{"query":"Maybank latest news"}}}]}}]}""",
            parameters,
        )
        assertEquals("Maybank latest news", extractToolQuery(parameters.getValue(0).toCall()!!.arguments))
    }

    @Test
    fun duckDuckGoAndWikipediaParsersYieldHits() {
        val ddg = """
            {"AbstractText":"Kotlin is a language.","AbstractURL":"https://example.com/k",
             "Heading":"Kotlin","Answer":"","RelatedTopics":[
               {"Text":"JetBrains - makers of Kotlin","FirstURL":"https://example.com/jb"}
             ]}
        """.trimIndent()
        val ddgHits = parseDuckDuckGoInstant(ddg)
        assertTrue(ddgHits.isNotEmpty())
        assertTrue(ddgHits.any { it.title.contains("Kotlin") })
        val wiki = """
            {"query":{"search":[
              {"title":"Kotlin","snippet":"A <span>programming</span> language","pageid":1}
            ]}}
        """.trimIndent()
        val wikiHits = parseWikipediaSearch(wiki)
        assertEquals(1, wikiHits.size)
        assertEquals("Kotlin", wikiHits[0].title)
        assertTrue(wikiHits[0].url.contains("Kotlin"))
        val html = """
            <a rel="nofollow" class="result-link" href="https://duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org%2F&amp;rut=1">Kotlin</a>
            <td class="result-snippet">A programming language.</td>
        """.trimIndent()
        val page = parseDuckDuckGoHtml(html)
        assertEquals(1, page.size)
        assertEquals("https://kotlinlang.org/", page[0].url)
        assertEquals("Kotlin", page[0].title)
        assertTrue(page[0].snippet.contains("programming"))
        assertFalse(wikiHits[0].snippet.contains("<span>"))
        val direct = parseDuckDuckGoHtml(
            """<a class="result-link" href="https://kotlinlang.org/">Kotlin</a><td class="result-snippet">Official site.</td>""",
        )
        assertEquals("https://kotlinlang.org/", direct.single().url)
        assertTrue(isDuckDuckGoChallenge("<html><title>DuckDuckGo</title><p>unfortunately, bots use DuckDuckGo</p></html>"))
        assertFalse(isDuckDuckGoChallenge(html))
        val fathead = """
            {"Abstract":"","AbstractText":"","Answer":"","RelatedTopics":[],"Results":[],
             "meta":{"description":"testing","name":"Just Another Test","perl_module":"DDG::Lontail::AnotherTest",
             "src_name":"hi there","src_url":"Hello there"}}
        """.trimIndent()
        assertTrue(parseDuckDuckGoInstant(fathead).isEmpty())
        assertFalse(
            isUsefulSearchHit(
                app.pixelbureau.net.InternetSearchClient.Hit(
                    "Just Another Test",
                    "",
                    """{"perl_module":"DDG::Lontail::AnotherTest"}""",
                ),
            ),
        )
    }

    @Test
    fun searchOpensAFewDistinctSitesAndReadsTheArticle() {
        val hits = listOf(
            InternetSearchClient.Hit("Same", "https://news.example/a", "one"),
            InternetSearchClient.Hit("Same again", "https://www.news.example/b", "two"),
            InternetSearchClient.Hit("Other", "https://other.example/c", "three"),
            InternetSearchClient.Hit("Wiki", "https://en.wikipedia.org/wiki/Maybank", "four"),
            InternetSearchClient.Hit("Fourth", "https://fourth.example/e", "five"),
            InternetSearchClient.Hit("Search", "https://duckduckgo.com/?q=maybank", "skip"),
        )
        val opened = pagesToOpen(hits, "maybank news")
        assertEquals(
            listOf(
                "https://news.example/a",
                "https://other.example/c",
                "https://en.wikipedia.org/wiki/Maybank",
            ),
            opened,
        )
        assertEquals("Checking out news.example…", checkingOutStatus(opened[0]))
        assertEquals("Checking out en.wikipedia.org…", checkingOutStatus(opened[2]))
        val named = pagesToOpen(
            hits,
            "read https://fourth.example/e and https://other.example/story",
        )
        assertEquals(
            listOf("https://fourth.example/e", "https://other.example/story"),
            named.take(2),
        )
        assertEquals(3, named.size)
        assertFalse(named.any { it.contains("127.0.0.1") || it.contains("duckduckgo") })
        assertEquals(null, openablePageUrl("http://127.0.0.1/secret"))
        assertEquals(null, openablePageUrl("http://192.168.1.4/admin"))
        assertEquals(null, openablePageUrl("file:///etc/passwd"))
        assertEquals(null, openablePageUrl("https://user:pass@example.com/a"))
        assertTrue(isPrivateWebHost("localhost"))
        val html = """
            <html><head><script>var secret = 1;</script><style>.x{}</style></head>
            <body><nav>Skip me</nav><article><h1>ROAR30</h1><p>Maybank launched the plan for shareholders today.</p></article>
            <footer>Copyright</footer></body></html>
        """.trimIndent()
        val text = readablePageText(html)
        assertTrue(text.contains("ROAR30"))
        assertTrue(text.contains("launched the plan"))
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("Skip me"))
        assertFalse(text.contains("Copyright"))
        val server = okhttp3.mockwebserver.MockWebServer()
        server.enqueue(
            okhttp3.mockwebserver.MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(html),
        )
        server.start()
        try {
            val client = InternetSearchClient(allowPrivateHosts = true)
            val page = client.readPage(server.url("/news").toString())
            assertTrue(page.contains("Maybank launched the plan"))
            assertFalse(page.contains("secret"))
            assertEquals("", InternetSearchClient().readPage(server.url("/news").toString()))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun chatTablesStayReadableAndProsePipesStayText() {
        val classic = parseChatBlocks(
            """
            Before

            | City | Time |
            | --- | --- |
            | Tokyo | 7:00 PM |

            After
            """.trimIndent(),
        )
        assertTrue(classic[0] is ChatBlock.Paragraph && "Before" in (classic[0] as ChatBlock.Paragraph).text)
        val table = classic.filterIsInstance<ChatBlock.Table>().single()
        assertEquals(listOf("City", "Time"), table.headers)
        assertEquals(listOf(listOf("Tokyo", "7:00 PM")), table.rows)
        assertTrue(classic.last() is ChatBlock.Paragraph && "After" in (classic.last() as ChatBlock.Paragraph).text)

        val plain = parseChatBlocks(
            """
            Name | Note
            --- | ---
            Pixel | Opens the office and greets anyone who walks in before nine
            """.trimIndent(),
        )
        val wide = plain.filterIsInstance<ChatBlock.Table>().single()
        assertEquals("Pixel", wide.rows.single()[0])
        assertEquals(2, wide.headers.size)

        val broken = parseChatBlocks(
            """
            | Item | Note |
            | --- | --- |
            | A \| B | one<br>two |
            """.trimIndent(),
        )
        val cells = broken.filterIsInstance<ChatBlock.Table>().single().rows.single()
        assertEquals(listOf("A | B", "one\ntwo"), cells)
        assertEquals("A & B", tidyTableCell("A &amp; B"))

        val prose = parseChatBlocks("Use a | b in the command, then stop.\n\n---\n\nDone")
        assertTrue(prose.all { it is ChatBlock.Paragraph })
        assertTrue((prose[0] as ChatBlock.Paragraph).text.contains("---"))

        val headed = parseChatBlocks(
            """
            ## Deal price

            Maybank paid for the Ageas business.

            ### Why it dipped
            A small first-day slip.

            # Overview
            ## **Bold title** ##
            Not a heading: ##no-space and use #channel in chat.
            ```
            ## stays code
            ```
            """.trimIndent(),
        )
        val headings = headed.filterIsInstance<ChatBlock.Heading>()
        assertEquals(listOf(2, 3, 1, 2), headings.map { it.level })
        assertEquals("Deal price", headings[0].text)
        assertEquals("Why it dipped", headings[1].text)
        assertEquals("Overview", headings[2].text)
        assertEquals("**Bold title**", headings[3].text)
        assertTrue(headed.filterIsInstance<ChatBlock.Paragraph>().any { it.text.contains("Ageas") })
        assertTrue(headed.filterIsInstance<ChatBlock.Paragraph>().any { it.text.contains("##no-space") })
        assertTrue(headed.filterIsInstance<ChatBlock.Code>().single().code.contains("## stays code"))
        assertEquals(null, headingLine("##"))
        assertEquals(2 to "Title", headingLine("## Title"))

        val notes = parseChatBlocks("| Notes |\n| --- |\n| hello |")
        val noteTable = notes.filterIsInstance<ChatBlock.Table>().single()
        assertEquals(listOf("Notes"), noteTable.headers)
        assertEquals(listOf(listOf("hello")), noteTable.rows)

        val tasks = listOf(
            TaskNotice("old", "pixel", "done", 1L),
            TaskNotice("new", "pixel", "done", 5L),
            TaskNotice("open", "kai", "working", 9L),
        )
        assertEquals("new", freshDoneTaskId("pixel", tasks))
        assertEquals(null, freshDoneTaskId("kai", tasks))
        assertTrue(agentShowsTaskDone("pixel", tasks, emptySet(), emptySet()))
        assertFalse(agentShowsTaskDone("pixel", tasks, setOf("new"), emptySet()))
        assertTrue(agentShowsTaskDone("byte", tasks, emptySet(), setOf("byte")))
        val tapped = acknowledgeTaskDone("pixel", "pixel", tasks, emptySet(), setOf("byte"))
        assertEquals(setOf("new"), tapped.first)
        assertEquals(setOf("byte"), tapped.second)
        assertFalse(agentShowsTaskDone("pixel", tasks, tapped.first, tapped.second))
        val switched = acknowledgeTaskDone("kai", "pixel", tasks, emptySet(), setOf("pixel", "byte"))
        assertEquals(setOf("new"), switched.first)
        assertEquals(setOf("byte"), switched.second)
        assertFalse(agentShowsTaskDone("pixel", tasks, switched.first, switched.second))
        assertFalse(agentShowsTaskDone("kai", tasks, switched.first, switched.second))
        val narrow = tableColumnWidths(listOf(40f, 40f), availablePx = 200f, minPx = 50f, maxPx = 180f)
        assertEquals(100f, narrow[0], 0.01f)
        assertEquals(100f, narrow[1], 0.01f)
        val scrolling = tableColumnWidths(listOf(300f, 300f), availablePx = 200f, minPx = 50f, maxPx = 180f)
        assertEquals(180f, scrolling[0], 0.01f)
        assertEquals(180f, scrolling[1], 0.01f)
        val mixed = tableColumnWidths(listOf(40f, 400f), availablePx = 100f, minPx = 60f, maxPx = 160f)
        assertEquals(60f, mixed[0], 0.01f)
        assertEquals(160f, mixed[1], 0.01f)
    }

    @Test
    fun pixelNewsKeepsTheDeskStories() {
        assertEquals(
            listOf("news-desks", "news-plants", "news-coffee"),
            PixelNews.stories.map { it.id },
        )
        assertEquals(PixelNews.stories, PixelNews.matching(""))
        assertEquals(listOf("news-desks"), PixelNews.matching("desks").map { it.id })
        assertEquals(listOf("news-plants"), PixelNews.matching("green").map { it.id })
        assertTrue(PixelNews.matching("zzznone").isEmpty())
        assertTrue(PixelNews.owns("news"))
        assertTrue(PixelNews.owns("news-coffee"))
        assertFalse(PixelNews.owns("pixelhire"))
    }

    @Test
    fun candidatePoolKeepsRoleRulesAndStarEdits() {
        val opening = openingCandidatePool()
        assertEquals(listOf("Kenji Tanaka", "Sari Kim", "Diego Santos"), opening.map { it.name })
        assertEquals(listOf("Finance", "Finance", "Finance"), opening.map { it.role })
        assertEquals(listOf("Accountant", "Financial Analyst", "Finance Manager"), opening.map { it.title })
        assertEquals(listOf("On-site", "Remote", "Hybrid"), opening.map { it.workplace() })
        assertFalse(opening[0].remote)
        assertTrue(opening[1].remote)
        assertFalse(opening[2].remote)
        assertEquals(4, opening[0].stars)
        assertEquals(5, opening[1].stars)
        assertEquals(4, opening[2].stars)
        assertEquals(listOf("Accounting", "Financial Modeling", "Budgeting"), opening.map { it.skills.first() })
        assertEquals(opening.size, opening.map { it.outputPersonality }.toSet().size)
        assertTrue(opening.all { it.instructions.isNotBlank() && it.outputPersonality != DefaultOutputStyle })
        assertTrue(opening[0].matches("excel", "Finance"))
        assertFalse(opening[0].matches("excel", "Engineering"))
        assertTrue(opening[1].matches("reporting", "Finance"))
        assertFalse(opening[2].matches("typescript", HireRoleAll))
        val rolled = rollCandidatePool(Random(1), count = 3, avoid = setOf("Mina", "Ryo", "Luna"))
        assertEquals(3, rolled.size)
        assertTrue(rolled.none { it.name in setOf("Mina", "Ryo", "Luna") })
        assertFalse(instructionEditCostsStar("a", "b", "a", "b"))
        assertFalse(instructionEditCostsStar("a", "b", " a ", "b"))
        assertTrue(instructionEditCostsStar("a", "b", "c", "b"))
        assertTrue(instructionEditCostsStar("a", "b", "a", "d"))
        assertTrue(instructionEditCostsStar("a", "b", "c", "d"))
        val withRules = buildSystemPrompt("Kenji Tanaka", opening[0].instructions, opening[0].outputPersonality, null, null)
        assertTrue(withRules.contains("Instructions:\n" + opening[0].instructions))
        assertTrue(withRules.contains("Output style:\n" + opening[0].outputPersonality))
        assertTrue(withRules.contains("your own rules"))
        assertEquals(
            listOf("All Roles", "Design", "Engineering", "Marketing", "Operations", "Finance"),
            hireRoleFilters,
        )
        val others = setOf(
            "Mina", "Sora", "Nola", "Ryo", "Hugo", "Leif",
            "Luna", "Vera", "Otto", "Reid", "Ames", "Cleo",
            "Pixel", "Kai", "Byte", "Ops", "Cursor", "You",
        )
        val finance = rollCandidatePool(Random(1), count = 3, avoid = others)
        assertEquals(3, finance.size)
        assertTrue(finance.all { it.name in setOf("June", "Pax", "Ivo", "Kenji Tanaka", "Sari Kim", "Diego Santos", "Bo Wang", "Ren Takahashi") })
        assertTrue(finance.all { it.role == "Finance" && it.skills.isNotEmpty() })
        assertTrue(finance.all { it.instructions.isNotBlank() && it.outputPersonality.isNotBlank() })
        assertTrue(finance.all { it.matches(it.skills.first(), "Finance") })
        assertTrue(finance.none { it.matches(it.name, "Operations") })
        assertTrue(opening.all { it.matches(it.name, "Finance") })
        assertEquals("kenji", opening[0].portraitKey)
        assertEquals("kenji", opening[0].hiredLook())
        assertEquals("sari", opening[1].portraitKey)
        assertEquals("sari", opening[1].hiredLook())
        assertEquals("diego", opening[2].portraitKey)
        assertEquals("diego", opening[2].hiredLook())
        val everyone = rollCandidatePool(Random(2), count = 40)
        assertEquals(36, everyone.map { it.name }.toSet().size)
        assertEquals(36, everyone.map { it.portraitKey }.toSet().size)
        assertTrue(everyone.all { person -> looks.any { it.first == person.hiredLook() } })
        for (name in listOf("Pixel", "Kai", "Byte", "Ops", "Cursor", "You")) {
            val person = everyone.first { it.name == name }
            assertEquals(name.lowercase(), person.portraitKey)
            assertEquals(name.lowercase(), person.hiredLook())
            assertTrue(person.skills.isNotEmpty())
            assertTrue(person.instructions.isNotBlank() && person.outputPersonality.isNotBlank())
            assertEquals(HireRoleGeneral, person.role)
            assertTrue(person.matches(person.name, HireRoleAll))
            assertTrue(hireRoleFilters.filter { it != HireRoleAll }.none { person.matches(person.name, it) })
        }
        for (dept in hireRoleFilters.filter { it != HireRoleAll }) {
            val batch = rollCandidatePool(Random(5), count = 12, role = dept)
            assertTrue(batch.isNotEmpty())
            assertTrue(batch.all { it.role == dept })
        }
        val mixed = rollCandidatePool(Random(2), count = 40, role = HireRoleAll)
        assertTrue(listOf("Pixel", "Kai", "Byte", "Ops", "Cursor", "You").all { name -> mixed.any { it.name == name } })
        val bench = candidatesForRole(HireRoleAll)
        assertEquals(36, bench.map { it.name }.toSet().size)
        assertEquals(7, candidatesForRole(HireRoleGeneral).size)
        assertTrue(candidatesForRole(HireRoleGeneral).all { it.role == HireRoleGeneral })
        assertTrue(candidatesForRole("Design").none { it.role == HireRoleGeneral })
        assertTrue(candidatesForRole("Finance").all { it.role == "Finance" })
        val byName = everyone.associateBy { it.name }
        assertEquals("ames", byName.getValue("Ames").hiredLook())
        assertEquals("cleo", byName.getValue("Cleo").hiredLook())
        assertEquals("june", byName.getValue("June").hiredLook())
        assertEquals("pax", byName.getValue("Pax").hiredLook())
        assertEquals("ivo", byName.getValue("Ivo").hiredLook())
        assertEquals("ryo", byName.getValue("Ryo").hiredLook())
        assertEquals("yuki", byName.getValue("Yuki Tanaka").hiredLook())
        assertEquals("minjun", byName.getValue("Minjun Park").hiredLook())
        assertEquals("wei", byName.getValue("Wei Zhang").hiredLook())
        assertEquals("sakura", byName.getValue("Sakura Ito").hiredLook())
        assertEquals("jia", byName.getValue("Jia Li").hiredLook())
        assertEquals("haruto", byName.getValue("Haruto Sato").hiredLook())
        assertEquals("soojin", byName.getValue("Soojin Han").hiredLook())
        assertEquals("chen", byName.getValue("Chen Liu").hiredLook())
        assertEquals("hana", byName.getValue("Hana Kim").hiredLook())
        assertEquals("bo", byName.getValue("Bo Wang").hiredLook())
        assertEquals("ren", byName.getValue("Ren Takahashi").hiredLook())
        assertEquals("lin", byName.getValue("Lin Xu").hiredLook())
        assertEquals("hugo", byName.getValue("Hugo").hiredLook())
        assertEquals("sora", byName.getValue("Sora").hiredLook())
    }

    @Test
    fun postedJobArrivesNextDayAsAMixedBatch() {
        assertEquals(HirePosting(), postHireJob(0))
        val posted = postHireJob(2)
        assertEquals(2, posted.postedDay)
        assertTrue(posted.names.isEmpty())
        assertFalse(posted.delivered)
        assertEquals(posted, deliverHireBatch(posted, 2, Random(1)))

        val ready = deliverHireBatch(posted, 3, Random(4), avoid = setOf("Pixel", "Kai", "Byte"))
        assertTrue(ready.delivered)
        assertFalse(ready.seen)
        assertTrue(ready.names.size in HireBatchMin..HireBatchMax)
        assertTrue(ready.names.none { it in setOf("Pixel", "Kai", "Byte") })
        val people = candidatesNamed(ready.names)
        assertEquals(ready.names, people.map { it.name })
        assertTrue(people.map { it.role }.distinct().size >= 2)
        assertEquals(ready, deliverHireBatch(ready, 9, Random(9)))

        val hired = hirePostingWithout(ready, ready.names.first())
        assertEquals(ready.names.size - 1, hired.names.size)
        assertTrue(hired.delivered)
        assertEquals(hired.names, deliverHireBatch(hired, 10, Random(1)).names)
        assertEquals(hired, decodeHirePosting(encodeHirePosting(hired)))
        assertEquals(HirePosting(), decodeHirePosting(null))
        assertEquals(HirePosting(), decodeHirePosting("nope"))
        assertEquals("There are 4 candidates for hire.", hireArrivalLine(4))
        assertEquals("There is 1 candidate for hire.", hireArrivalLine(1))

        for (seed in 0 until 24) {
            val batch = rollMixedHireBatch(Random(seed))
            assertTrue(batch.size in HireBatchMin..HireBatchMax)
            assertEquals(batch.size, batch.map { it.name }.toSet().size)
            assertTrue(batch.map { it.role }.distinct().size >= 2)
            val general = batch.filter { it.role == HireRoleGeneral }
            assertTrue(general.all { it.matches(it.name, HireRoleAll) })
            assertTrue(general.none { person ->
                hireRoleFilters.filter { it != HireRoleAll }.any { person.matches(person.name, it) }
            })
        }
    }

    @Test
    fun banterMemoryNeverRepeatsImmediately() {
        val memory = BanterMemory(cap = 6)
        val random = Random(7)
        var last: String? = null
        repeat(500) {
            val line = memory.pick(BanterBook.work, random)
            assertTrue("immediate repeat $line", line != last)
            last = line
        }
        val tiny = BanterMemory()
        var prev: String? = null
        repeat(50) {
            val l = tiny.pick(listOf("a", "b"), random)
            assertTrue(l != prev)
            prev = l
        }
        assertTrue(BanterBook.idle.size >= 12 && BanterBook.work.size >= 15)
        assertTrue(BanterBook.props.keys.containsAll(listOf("vending", "coffee", "arcade", "foosball", "aquarium", "bookshelf", "server", "plant", "clock")))
        assertEquals("About Launch…", BanterBook.fill("About {task}…", task = "Launch"))
        assertEquals("code", BanterBook.dutyKeyword("Writes Kotlin code daily"))
    }

    @Test
    fun multiTurnExchangesAlternateSpeakersWithNames() {
        assertTrue(BanterBook.exchanges.all { it.size in 2..4 })
        val lines = BanterBook.exchange(0, "Pixel", "Kai")
        assertEquals("Hey Kai!", lines[0])
        assertEquals("Hi Pixel! 👋", lines[1])
        assertTrue(BanterBook.exchanges.flatten().none { it.contains("{task}") })
        // Live sim: a pair takes turns, never two chat bubbles at once, and the
        // lead and partner each get at least one line.
        val sim = OfficeSim(Random(11), llmGate = { false })
        val agents = listOf(SimAgent("a", "Pixel", "pixel", 0, "idle"), SimAgent("b", "Kai", "kai", 1, "idle"))
        val speakers = HashSet<String>()
        var maxBubbles = 0
        repeat(8_000) {
            sim.tick(1f / 30f, agents)
            val snap = sim.snapshot().actors
            maxBubbles = maxOf(maxBubbles, snap.count { it.bubble != null && it.bubble!!.length > 1 })
            snap.filter { it.chatting && it.bubble != null }.forEach { speakers.add(it.id) }
        }
        assertEquals(setOf("a", "b"), speakers)
        assertTrue(maxBubbles <= 3)
    }
}
