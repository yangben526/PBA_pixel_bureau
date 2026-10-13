package app.pixelbureau.ui

import app.pixelbureau.logic.TaskforceSignOffStars
import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pixelbureau.PixelBureauApp
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopCatalog
import app.pixelbureau.data.Agent
import app.pixelbureau.data.Bureau
import app.pixelbureau.data.LlmSettings
import app.pixelbureau.data.Message
import app.pixelbureau.data.Task
import app.pixelbureau.data.toSettings
import app.pixelbureau.logic.BanterAsk
import app.pixelbureau.logic.BubbleSpeechScaleDefault
import app.pixelbureau.logic.ChatException
import app.pixelbureau.logic.banterLlmRoll
import app.pixelbureau.logic.coerceBubbleSpeechScale
import app.pixelbureau.logic.OfficeSim
import app.pixelbureau.logic.MapPlacement
import app.pixelbureau.logic.PlacedProp
import app.pixelbureau.logic.banterSystemPrompt
import app.pixelbureau.logic.banterUserPrompt
import app.pixelbureau.logic.OfficeSnapshot
import app.pixelbureau.logic.SimAgent
import app.pixelbureau.logic.TurnMessage
import app.pixelbureau.logic.CatalogWrite
import app.pixelbureau.logic.SavedLlm
import app.pixelbureau.logic.displayTitle
import app.pixelbureau.logic.buildSystemPrompt
import app.pixelbureau.logic.instructionEditCostsStar
import app.pixelbureau.logic.enabledCommonTools
import app.pixelbureau.logic.McpServerConfig
import app.pixelbureau.logic.decodeAgentMcp
import app.pixelbureau.logic.serversForAgent
import app.pixelbureau.logic.historyForNextTurn
import app.pixelbureau.logic.isPlaceholderReply
import app.pixelbureau.logic.isScratchpad
import app.pixelbureau.logic.isSearchPromise
import app.pixelbureau.logic.stripThinkTags
import app.pixelbureau.logic.isSearchResultDump
import app.pixelbureau.logic.removeFromCatalog
import app.pixelbureau.logic.saveIntoCatalog
import app.pixelbureau.logic.selectInCatalog
import app.pixelbureau.logic.taskMessage
import app.pixelbureau.logic.TaskforceStatus
import app.pixelbureau.logic.TaskforceRules
import app.pixelbureau.data.TaskforceMessage
import app.pixelbureau.data.Taskforce
import app.pixelbureau.logic.AgentTool
import app.pixelbureau.logic.OfficeMap
import app.pixelbureau.logic.Cell
import kotlinx.coroutines.flow.combine
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** A taskforce row plus its member ids (lead first). */
data class TaskforceUi(val data: Taskforce, val memberIds: List<String>)

data class StreamUi(val messageId: String, val text: String)

data class PendingAttachment(
    val id: String,
    val uri: String,
    val name: String,
    val kind: String, // "file" or "image"
)

data class BureauUi(
    val bureau: Bureau? = null,
    val agents: List<Agent> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val messages: List<Message> = emptyList(),
    val selectedId: String? = null,
    val draft: String = "",
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val banner: String? = null,
    val spritesReady: Boolean = false,
    val streams: Map<String, StreamUi> = emptyMap(),
    val settings: LlmSettings = LlmSettings(apiKey = ""),
    val models: List<SavedLlm> = emptyList(),
    val activeModelId: String? = null,
    /** agentId → SavedLlm.id; missing falls back to activeModelId. */
    val agentModelIds: Map<String, String> = emptyMap(),
    /** Settings → Agent tools → Common → Internet search (default on). */
    val internetSearchEnabled: Boolean = true,
    /** Settings → Agent tools → Common → Time (default on). */
    val timeToolEnabled: Boolean = true,
    /** Settings → MCP Servers (below LLM BYOK). */
    val mcpServers: List<McpServerConfig> = emptyList(),
    /** agentId → attached server ids; missing = Default (all enabled servers). */
    val agentMcp: Map<String, Set<String>> = emptyMap(),
    /** serverId → last Test result line. */
    val mcpTest: Map<String, String> = emptyMap(),
    /** Settings → System settings → Bubble speech. On: about one line in twenty may call a model. */
    val bubbleSpeechLlmEnabled: Boolean = true,
    /** Overhead bubble size. 1 is the original map size. */
    val bubbleSpeechScale: Float = BubbleSpeechScaleDefault,
    /** Chat panel height fraction of map+chat weighted space. */
    val chatPanelFraction: Float = 0.42f,
    /** Finished-task ids the user has already moved on from. */
    val dismissedTaskDone: Set<String> = emptySet(),
    /** Agents whose latest reply finished, including a chat with no task row. */
    val replyDoneIds: Set<String> = emptySet(),
    /** Shop purchases: catalog item id → count (SettingsStore.KEY_OWNED_ITEMS). */
    val ownedItems: Map<String, Int> = emptyMap(),
    /** Bought items standing on the map (SettingsStore.KEY_SHOP_PLACEMENTS). */
    val placedProps: List<PlacedProp> = emptyList(),
    /** Sprite the eight work desks use: "desk" (default / Startup Premium) or "desk_oak" (Oakwood). */
    val deskSprite: String = DESK_SPRITE_DEFAULT,
    /** Open taskforces (not signed off / disbanded), oldest first. */
    val taskforces: List<TaskforceUi> = emptyList(),
    /** When set, the chat panel shows this taskforce's group thread. */
    val selectedTaskforceId: String? = null,
    val taskforceMessages: List<TaskforceMessage> = emptyList(),
    /** Message the user is quote-replying to in the taskforce composer. */
    val taskforceQuote: TaskforceMessage? = null,
    /** taskforceId → agent ids currently writing in that thread. */
    val taskforceSpeaking: Map<String, Set<String>> = emptyMap(),
    /** Conference room tapped: highlight it and show the "Set a meeting" dialog. */
    val meetingDialogOpen: Boolean = false,
    /** taskforceId → (agentId → reason) for model errors waiting on Retry / Skip. */
    val taskforceFailures: Map<String, Map<String, String>> = emptyMap(),
    /** taskforceId → (round, rounds) while agents debate. */
    val taskforceDebates: Map<String, Pair<Int, Int>> = emptyMap(),
    /** taskforceId → chat banner mode: 0 expanded (default), 1 slim title row, 2 hidden. */
    val taskforceHeaderModes: Map<String, Int> = emptyMap(),
)

internal const val DESK_SPRITE_DEFAULT = "desk"
internal const val DESK_SPRITE_OAK = "desk_oak"

/** Owned desk-group item → work desk art. Nothing owned keeps the original desk. */
internal fun deskSpriteFor(owned: Map<String, Int>): String =
    if ((owned["office_desk"] ?: 0) > 0 && (owned["premium_desk"] ?: 0) <= 0) DESK_SPRITE_OAK else DESK_SPRITE_DEFAULT

/** Owned copies that stand on the map, as (slot, kind) in a stable order. */
internal fun placeableCopies(owned: Map<String, Int>): List<Pair<String, String>> = buildList {
    for (item in ShopCatalog.items) {
        if (item.exclusiveGroup != null) continue
        val count = owned[item.id] ?: 0
        for (n in 0 until count) add("${item.id}#$n" to item.propKey)
    }
}

internal data class TaskNotice(
    val id: String,
    val agentId: String,
    val status: String,
    val createdAt: Long,
)

/** Id of this agent's newest task when that task is finished. */
internal fun freshDoneTaskId(agentId: String, tasks: List<TaskNotice>): String? {
    val latest = tasks.filter { it.agentId == agentId }.maxByOrNull { it.createdAt } ?: return null
    return if (latest.status == "done") latest.id else null
}

private fun List<Task>.toNotices(): List<TaskNotice> =
    map { TaskNotice(it.id, it.agentId, it.status, it.createdAt) }

internal fun agentShowsTaskDone(
    agentId: String,
    tasks: List<TaskNotice>,
    dismissedTaskDone: Set<String>,
    replyDoneIds: Set<String>,
): Boolean {
    val doneId = freshDoneTaskId(agentId, tasks)
    if (doneId != null && doneId !in dismissedTaskDone) return true
    return agentId in replyDoneIds
}

/**
 * Tapping an agent, or leaving one for another, puts the clipboard away.
 * Returns the dismissed task ids and the agents whose reply stamp is cleared.
 */
internal fun acknowledgeTaskDone(
    tappedAgentId: String,
    previousAgentId: String?,
    tasks: List<TaskNotice>,
    dismissedTaskDone: Set<String>,
    replyDoneIds: Set<String>,
): Pair<Set<String>, Set<String>> {
    val agents = buildSet {
        add(tappedAgentId)
        if (!previousAgentId.isNullOrBlank() && previousAgentId != tappedAgentId) {
            add(previousAgentId)
        }
    }
    val taskIds = agents.mapNotNull { freshDoneTaskId(it, tasks) }
    return (dismissedTaskDone + taskIds) to (replyDoneIds - agents)
}

class BureauViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as PixelBureauApp).container
    private val repository = container.repository

    @Volatile
    private var bubbleSpeechLlmEnabled = true

    private val banterRandom = Random.Default
    private val sim = OfficeSim(
        nowMs = { System.currentTimeMillis() },
        llmGate = { bubbleSpeechLlmEnabled && banterLlmRoll(banterRandom) },
    )
    private val jobs = HashMap<String, Job>()
    private var banterJob: Job? = null
    private var banterToken = 0L
    private val arrivals = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private var messageJob: Job? = null
    private var watchingId: String? = null

    private val _ui = MutableStateFlow(BureauUi())
    val ui: StateFlow<BureauUi> = _ui

    private val _office = MutableStateFlow(OfficeSnapshot(emptyList()))
    val office: StateFlow<OfficeSnapshot> = _office

    private val buyLock = Mutex()

    private val taskforceRunner = TaskforceRunner(
        scope = viewModelScope,
        repository = repository,
        chat = container.chat,
        settingsFor = ::settingsForAgent,
        toolsFor = ::toolsFor,
        onSpeaking = { tfId, agentId, on ->
            _ui.update { state ->
                val cur = state.taskforceSpeaking[tfId].orEmpty()
                val next = if (on) cur + agentId else cur - agentId
                state.copy(taskforceSpeaking = state.taskforceSpeaking + (tfId to next))
            }
        },
    )
    private var taskforceMessageJob: Job? = null

    /** In-memory placement preview (debug intent only); never written to settings. */
    private var previewCopies: List<Pair<String, String>> = emptyList()


    init {
        reloadSettings()
        refreshPlacements(container.settings.readOwnedItems())
        viewModelScope.launch {
            if (repository.seedIfEmpty()) {
                container.settings.seedStarterInventory()
                refreshPlacements(container.settings.readOwnedItems())
            }
            repository.giveDefaultOutputStyle()
            repository.releaseStuckAgents()
            for (id in repository.activeIds()) {
                if (!hasKey(id)) {
                    continue
                }
                val stored = repository.messages(id)
                val pending = historyForNextTurn(stored.map { TurnMessage(it.role, it.text, it.taskId) })
                if (pending != null) {
                    kick(id)
                }
            }
            repository.observeBureau().collect { bureau ->
                _ui.update { it.copy(bureau = bureau) }
            }
        }
        viewModelScope.launch {
            repository.observeAgents().collect { agents ->
                _ui.update { state ->
                    val selected = state.selectedId
                    val stillHere = agents.any { it.id == selected && !it.archived }
                    val next = if (stillHere) selected else agents.firstOrNull { !it.archived }?.id
                    state.copy(agents = agents, selectedId = next)
                }
                val selected = _ui.value.selectedId
                if (selected != watchingId) {
                    watchMessages(selected)
                }
            }
        }
        viewModelScope.launch {
            combine(
                repository.observeTaskforces(),
                repository.observeTaskforceMembers(),
            ) { rows, members ->
                rows.filter { TaskforceStatus.isOpen(it.status) }.map { tf ->
                    TaskforceUi(tf, members.filter { it.taskforceId == tf.id }.sortedBy { it.sortOrder }.map { it.agentId })
                }
            }.collect { open ->
                _ui.update { state ->
                    val stillOpen = open.any { it.data.id == state.selectedTaskforceId }
                    state.copy(
                        taskforces = open,
                        selectedTaskforceId = if (stillOpen) state.selectedTaskforceId else null,
                        taskforceQuote = if (stillOpen) state.taskforceQuote else null,
                    )
                }
                if (_ui.value.selectedTaskforceId == null) {
                    taskforceMessageJob?.cancel()
                    taskforceMessageJob = null
                }
                sim.setTaskforceSeats(taskforceSeatMap(open))
                sim.setTaskforceTitle(open.firstOrNull()?.data?.title)
            }
        }
        taskforceRunner.resumeAll()
        _ui.update { it.copy(taskforceHeaderModes = container.settings.taskforceHeaderModes()) }
        viewModelScope.launch {
            taskforceRunner.debates.collect { d -> _ui.update { it.copy(taskforceDebates = d) } }
        }
        viewModelScope.launch {
            taskforceRunner.failures.collect { f ->
                _ui.update { it.copy(taskforceFailures = f) }
                sim.setModelErrors(f.values.flatMap { it.keys }.toSet())
            }
        }
        viewModelScope.launch {
            repository.observeTasks().collect { tasks ->
                _ui.update { it.copy(tasks = tasks) }
            }
        }
        viewModelScope.launch {
            container.sprites.load()
            _ui.update { it.copy(spritesReady = true) }
        }
        viewModelScope.launch {
            var last = System.nanoTime()
            while (true) {
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                last = now
                val agents = _ui.value.agents.filter { !it.archived }.map {
                    SimAgent(it.id, it.name, it.spriteKey, it.deskIndex, it.status, role = "${it.personality} ${it.duties}")
                }
                sim.tick(dt, agents)
                syncBanter()
                _office.value = sim.snapshot()
                for (id in sim.consumeArrivals()) {
                    arrivals.emit(id)
                }
                delay(16)
            }
        }
    }

    fun select(agentId: String) {
        val agent = _ui.value.agents.firstOrNull { it.id == agentId && !it.archived } ?: return
        if (_ui.value.selectedTaskforceId != null) {
            taskforceMessageJob?.cancel()
            taskforceMessageJob = null
            _ui.update { it.copy(selectedTaskforceId = null, taskforceQuote = null, taskforceMessages = emptyList()) }
        }
        _ui.update { state ->
            val switched = state.selectedId != agent.id
            val (dismissed, replies) = acknowledgeTaskDone(
                tappedAgentId = agent.id,
                previousAgentId = state.selectedId,
                tasks = state.tasks.toNotices(),
                dismissedTaskDone = state.dismissedTaskDone,
                replyDoneIds = state.replyDoneIds,
            )
            state.copy(
                selectedId = agent.id,
                pendingAttachments = if (switched) emptyList() else state.pendingAttachments,
                dismissedTaskDone = dismissed,
                replyDoneIds = replies,
            )
        }
        if (agent.id != watchingId) {
            watchMessages(agent.id)
        }
    }

    fun addPendingAttachments(items: List<PendingAttachment>) {
        if (items.isEmpty()) return
        _ui.update { state ->
            val existing = state.pendingAttachments.map { it.uri }.toSet()
            val merged = state.pendingAttachments + items.filter { it.uri !in existing }
            state.copy(pendingAttachments = merged)
        }
    }

    fun removePendingAttachment(id: String) {
        _ui.update { state ->
            state.copy(pendingAttachments = state.pendingAttachments.filterNot { it.id == id })
        }
    }

    fun setDraft(value: String) {
        _ui.update { it.copy(draft = value) }
    }

    fun openHiringSlot() {
        viewModelScope.launch {
            val error = repository.openHiringSlot()
            if (error != null) {
                showBanner(error)
            } else {
                showBanner("Desk opened · ★−1")
            }
        }
    }

    /**
     * Rebuilds map placements from inventory: keeps stored cells, allocates any
     * owned copy with no (valid) cell yet, persists the result, and pushes the
     * obstacles into the sim and the desk art into state.
     */
    private fun refreshPlacements(owned: Map<String, Int>) {
        val settings = container.settings
        val stored = settings.readPlacements()
        val copies = placeableCopies(owned)
        val (placed, cells) = MapPlacement.resolve(copies, stored) { it.substringBefore('#') }
        if (cells != stored) settings.writePlacements(cells)
        val preview = if (previewCopies.isEmpty()) {
            emptyList()
        } else {
            MapPlacement.resolve(copies + previewCopies, cells) { it.substringBefore('#') }.first
                .filter { p -> previewCopies.any { it.first == p.slot } }
        }
        val all = placed + preview
        sim.setPlacedObstacles(all.flatMap { it.cells }.toSet())
        sim.setPlacedKinds(all.associate { it.cell to it.kind })
        _ui.update { it.copy(ownedItems = owned, placedProps = all, deskSprite = deskSpriteFor(owned)) }
    }

    /**
     * Debug builds only (adb `--es pba_preview_items id,id,...`): shows those
     * catalog items on the map for a screenshot without buying or saving them.
     */
    fun previewShopItems(ids: List<String>) {
        previewCopies = ids.mapIndexedNotNull { index, id ->
            val item = ShopCatalog.byId(id) ?: return@mapIndexedNotNull null
            if (item.exclusiveGroup != null) return@mapIndexedNotNull null
            "preview:$id#$index" to item.propKey
        }
        refreshPlacements(container.settings.readOwnedItems())
    }

    /**
     * Pazada / Pamazon / Pmart Buy. Non-desk items need a free map cell first:
     * no room → refuse before any star is spent. Then spend stars, record the
     * item and pin the new copy to its cell. Desk-group items swap the desk art.
     */
    fun buyShopItem(itemId: String, itemName: String, cost: Int) {
        viewModelScope.launch {
            val text = buyLock.withLock { buyLocked(itemId, itemName, cost) }
            Toast.makeText(getApplication<Application>(), text, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun buyLocked(itemId: String, itemName: String, cost: Int): String {
        val item = ShopCatalog.byId(itemId)
        val settings = container.settings
        val before = settings.readOwnedItems()
        val placesOnMap = item != null && item.exclusiveGroup == null
        val cell = if (placesOnMap) {
            val placed = _ui.value.placedProps.filterNot { it.slot.startsWith("preview:") }
            MapPlacement.findCell(item!!.propKey, placed) ?: return "No room on the map for $itemName"
        } else {
            null
        }
        val error = repository.spendStars(cost)
        if (error != null) return error
        val rivals = ShopCatalog.exclusiveRivals(itemId)
        val replaced = rivals.filter { (before[it.id] ?: 0) > 0 }
        val owned = settings.addOwnedItem(itemId, replaces = rivals.map { it.id })
        if (cell != null) {
            val slot = "$itemId#${before[itemId] ?: 0}"
            settings.writePlacements(settings.readPlacements() + (slot to cell))
        }
        refreshPlacements(owned)
        return if (replaced.isEmpty()) {
            "Bought $itemName · ★−$cost"
        } else {
            "Bought $itemName · ★−$cost · replaced ${replaced.joinToString { it.name }}"
        }
    }

    /** Inventories → Remove from office: off the map, not owned (rebuyable), no refund. */
    fun removeShopItem(itemId: String) {
        viewModelScope.launch {
            val text = buyLock.withLock {
                val settings = container.settings
                if ((settings.readOwnedItems()[itemId] ?: 0) <= 0) return@withLock "Already removed"
                val item = ShopCatalog.byId(itemId)
                val owned = settings.removeOwnedItem(itemId)
                refreshPlacements(owned)
                "Removed ${item?.name ?: itemId}"
            }
            Toast.makeText(getApplication<Application>(), text, Toast.LENGTH_SHORT).show()
        }
    }

    fun closeEmptySlot() {
        viewModelScope.launch {
            val error = repository.closeEmptySlot()
            if (error != null) {
                showBanner(error)
            }
        }
    }

    fun setHeadcount(headcount: Int) {
        val current = ui.value.bureau?.headcount ?: return
        when {
            headcount > current -> openHiringSlot()
            headcount < current -> closeEmptySlot()
        }
    }

    fun hire(name: String, personality: String, duties: String, spriteKey: String) {
        viewModelScope.launch {
            val error = repository.createAgent(name, personality, duties, spriteKey)
            if (error != null) {
                showBanner(error)
            } else {
                showBanner("$name joined the bureau!")
            }
        }
    }

    fun saveAgent(id: String, name: String, personality: String, duties: String, spriteKey: String) {
        viewModelScope.launch {
            val before = repository.agent(id)
            val error = repository.updateAgent(id, name, personality, duties, spriteKey)
            if (error != null) {
                showBanner(error)
            } else if (
                before != null &&
                instructionEditCostsStar(before.personality, before.duties, personality, duties)
            ) {
                showBanner("Rules updated · ★−1")
            }
        }
    }

    fun fire(id: String) {
        viewModelScope.launch {
            val error = repository.fire(id)
            if (error != null) {
                showBanner(error)
            } else {
                jobs[id]?.cancel()
                jobs.remove(id)
                container.settings.clearAgentModel(id)
            }
        }
    }

    @Deprecated("Use fire")
    fun bench(id: String) = fire(id)

    fun saveModel(
        editingId: String?,
        providerId: String,
        apiKey: String,
        model: String,
        label: String,
        baseUrl: String,
    ): String? {
        val result = saveIntoCatalog(
            container.settings.readCatalog(),
            editingId,
            providerId,
            apiKey,
            model,
            label,
            UUID.randomUUID().toString(),
            baseUrl,
        )
        return applyCatalog(result)
    }

    fun useModel(id: String) {
        applyCatalog(selectInCatalog(container.settings.readCatalog(), id))
    }

    fun removeModel(id: String) {
        val result = removeFromCatalog(container.settings.readCatalog(), id)
        if (result is CatalogWrite.Saved) {
            container.settings.clearBindingsForModel(id)
        }
        applyCatalog(result)
    }

    /** Bind a SavedLlm to one agent; null/blank uses the Settings default. */
    fun bindAgentModel(agentId: String, modelId: String?) {
        val catalog = container.settings.readCatalog()
        if (!modelId.isNullOrBlank() && catalog.models.none { it.id == modelId }) {
            showBanner("That model is already gone.")
            return
        }
        container.settings.setAgentModel(agentId, modelId?.takeIf { it.isNotBlank() })
        reloadSettings()
        val name = _ui.value.agents.firstOrNull { it.id == agentId }?.name ?: "Agent"
        val label = when {
            modelId.isNullOrBlank() -> "default"
            else -> catalog.models.firstOrNull { it.id == modelId }?.displayTitle() ?: "model"
        }
        showBanner("$name now uses $label.")
    }

    /** Assign the currently selected Settings BYOK to every active agent. */
    fun applyActiveModelToAllAgents() {
        val catalog = container.settings.readCatalog()
        val activeId = catalog.activeId
        if (activeId == null || catalog.models.none { it.id == activeId }) {
            showBanner("Select a model in Settings first.")
            return
        }
        val agents = _ui.value.agents.filter { !it.archived }
        if (agents.isEmpty()) {
            showBanner("Hire someone in PixelHire first.")
            return
        }
        val bindings = agents.associate { it.id to activeId }
        container.settings.writeAgentModels(bindings)
        reloadSettings()
        val title = catalog.models.first { it.id == activeId }.displayTitle()
        val noun = if (agents.size == 1) "agent" else "agents"
        showBanner("Applied $title to ${agents.size} $noun.")
    }

    private fun applyCatalog(result: CatalogWrite): String? {
        return when (result) {
            is CatalogWrite.Rejected -> {
                showBanner(result.message)
                result.message
            }
            is CatalogWrite.Saved -> {
                container.settings.writeCatalog(result.catalog)
                reloadSettings()
                showBanner(result.message)
                null
            }
        }
    }


    fun setInternetSearchEnabled(enabled: Boolean) {
        container.settings.writeInternetSearchEnabled(enabled)
        _ui.update { it.copy(internetSearchEnabled = enabled) }
        showBanner(if (enabled) "Internet search on." else "Internet search off.")
    }

    fun saveMcpServer(id: String?, name: String, url: String, authHeader: String, token: String, enabled: Boolean): String? {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            showBanner("MCP URL must start with https:// or http://")
            return "url"
        }
        val list = container.settings.readMcpServers().toMutableList()
        val label = name.trim().ifBlank { cleanUrl.substringAfter("://").substringBefore('/') }
        val entry = McpServerConfig(
            id = id ?: UUID.randomUUID().toString(),
            name = label,
            url = cleanUrl,
            authHeader = authHeader.trim(),
            token = token.trim(),
            enabled = enabled,
        )
        val index = list.indexOfFirst { it.id == entry.id }
        if (index >= 0) list[index] = entry else list += entry
        container.settings.writeMcpServers(list)
        container.mcp.invalidate(entry.id)
        reloadSettings()
        showBanner(if (index >= 0) "Updated MCP server $label." else "Added MCP server $label.")
        return null
    }

    fun removeMcpServer(id: String) {
        val list = container.settings.readMcpServers()
        val gone = list.firstOrNull { it.id == id } ?: return
        container.settings.writeMcpServers(list.filter { it.id != id })
        container.mcp.invalidate(id)
        _ui.update { it.copy(mcpTest = it.mcpTest - id) }
        reloadSettings()
        showBanner("Removed MCP server ${gone.name}.")
    }

    fun setMcpServerEnabled(id: String, enabled: Boolean) {
        val list = container.settings.readMcpServers().map { if (it.id == id) it.copy(enabled = enabled) else it }
        container.settings.writeMcpServers(list)
        reloadSettings()
    }

    fun testMcpServer(id: String) {
        val server = container.settings.readMcpServers().firstOrNull { it.id == id } ?: return
        _ui.update { it.copy(mcpTest = it.mcpTest + (id to "Connecting…")) }
        viewModelScope.launch {
            val line = try {
                val tools = container.mcp.test(server)
                if (tools.isEmpty()) "Connected · no tools" else "Connected · ${tools.size} tools: " + tools.joinToString(", ") { it.name }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                "Failed: ${error.message ?: error.javaClass.simpleName}"
            }
            _ui.update { it.copy(mcpTest = it.mcpTest + (id to line)) }
        }
    }

    /** Per-agent MCP attachment, like per-agent BYOK. null = Default (all enabled servers). */
    fun bindAgentMcp(agentId: String, serverIds: Set<String>?) {
        container.settings.setAgentMcp(agentId, serverIds)
        reloadSettings()
        val name = _ui.value.agents.firstOrNull { it.id == agentId }?.name ?: "Agent"
        showBanner(
            when {
                serverIds == null -> "Applied default MCP servers to $name."
                serverIds.isEmpty() -> "Applied: $name uses no MCP servers."
                else -> "Applied ${serverIds.size} MCP server(s) to $name."
            },
        )
    }

    fun setTimeToolEnabled(enabled: Boolean) {
        container.settings.writeTimeToolEnabled(enabled)
        _ui.update { it.copy(timeToolEnabled = enabled) }
        showBanner(if (enabled) "Time tool on." else "Time tool off.")
    }

    fun setBubbleSpeechLlmEnabled(enabled: Boolean) {
        container.settings.writeBubbleSpeechLlmEnabled(enabled)
        bubbleSpeechLlmEnabled = enabled
        _ui.update { it.copy(bubbleSpeechLlmEnabled = enabled) }
        if (!enabled) {
            banterJob?.cancel()
            banterJob = null
            banterToken = 0L
        }
        showBanner(
            if (enabled) "Bubble speech can use a model." else "Bubble speech stays on local lines.",
        )
    }

    /** Live map size. [persist] writes the slider once the drag ends. */
    fun setBubbleSpeechScale(scale: Float, persist: Boolean = true) {
        val clamped = coerceBubbleSpeechScale(scale)
        if (persist) {
            container.settings.writeBubbleSpeechScale(clamped)
        }
        _ui.update { it.copy(bubbleSpeechScale = clamped) }
    }

    fun setChatPanelFraction(fraction: Float) {
        val clamped = fraction.coerceIn(
            app.pixelbureau.data.SettingsStore.MIN_CHAT_FRACTION,
            app.pixelbureau.data.SettingsStore.MAX_CHAT_FRACTION,
        )
        container.settings.writeChatPanelFraction(clamped)
        _ui.update { it.copy(chatPanelFraction = clamped) }
    }

    private fun reloadSettings() {
        val catalog = container.settings.readCatalog()
        val settings = catalog.active()?.toSettings() ?: LlmSettings(apiKey = "")
        val known = catalog.models.map { it.id }.toSet()
        val bindings = container.settings.readAgentModels().filterValues { it in known }
        bubbleSpeechLlmEnabled = container.settings.readBubbleSpeechLlmEnabled()
        _ui.update {
            it.copy(
                settings = settings,
                models = catalog.models,
                activeModelId = catalog.activeId,
                agentModelIds = bindings,
                internetSearchEnabled = container.settings.readInternetSearchEnabled(),
                timeToolEnabled = container.settings.readTimeToolEnabled(),
                mcpServers = container.settings.readMcpServers(),
                agentMcp = container.settings.readAgentMcp()
                    .mapNotNull { (agent, raw) -> decodeAgentMcp(raw)?.let { agent to it } }
                    .toMap(),
                bubbleSpeechLlmEnabled = container.settings.readBubbleSpeechLlmEnabled(),
                bubbleSpeechScale = container.settings.readBubbleSpeechScale(),
                chatPanelFraction = container.settings.readChatPanelFraction(),
            )
        }
    }

    /**
     * At most one short banter call. It uses the speaker's assigned model, not the
     * chat transcript. A miss or a slow reply leaves the canned sentence.
     */
    private fun syncBanter() {
        val asks = sim.consumeBanterAsks()
        if (asks.isNotEmpty()) {
            launchBanter(asks.first())
            return
        }
        if (banterToken != 0L && !sim.banterStillOpen(banterToken)) {
            banterJob?.cancel()
            banterJob = null
            banterToken = 0L
        }
    }

    private fun launchBanter(ask: BanterAsk) {
        val personality = _ui.value.agents.firstOrNull { it.id == ask.agentId }?.personality.orEmpty()
        val settings = settingsForAgent(ask.agentId)
        banterJob?.cancel()
        banterJob = null
        banterToken = ask.token
        if (settings.apiKey.isBlank()) {
            return
        }
        banterJob = viewModelScope.launch {
            val line = container.chat.banter(
                settings,
                banterSystemPrompt(ask.name, personality),
                banterUserPrompt(ask.partnerName, ask.heard),
            )
            if (!line.isNullOrBlank()) {
                sim.offerBanterLine(ask.agentId, ask.token, line)
            }
        }
    }

    /** Resolve BYOK for an agent: bound model if still in catalog, else global active. */
    private fun settingsForAgent(agentId: String): LlmSettings {
        val catalog = container.settings.readCatalog()
        val boundId = container.settings.readAgentModels()[agentId]
        val saved = boundId?.let { id -> catalog.models.firstOrNull { it.id == id } }
            ?: catalog.active()
        return saved?.toSettings() ?: LlmSettings(apiKey = "")
    }

    fun clearSession() {
        val agentId = _ui.value.selectedId ?: return
        val name = _ui.value.agents.firstOrNull { it.id == agentId }?.name ?: "Their"
        viewModelScope.launch {
            val running = jobs[agentId]
            running?.cancel()
            running?.join()
            repository.clearSession(agentId)
            clearStream(agentId)
            _ui.update {
                it.copy(
                    pendingAttachments = emptyList(),
                    draft = "",
                    replyDoneIds = it.replyDoneIds - agentId,
                )
            }
            showBanner("Cleared $name's chat.")
        }
    }

    // ------------------------------------------------------------------
    // Taskforces (team meetings in the conference room)
    // ------------------------------------------------------------------

    fun openMeetingDialog() {
        _ui.update { it.copy(meetingDialogOpen = true) }
    }

    fun closeMeetingDialog() {
        _ui.update { it.copy(meetingDialogOpen = false) }
    }

    fun createTaskforce(title: String, objective: String, memberIds: List<String>, leadId: String? = null, steps: Int = 3) {
        val name = title.trim()
        val active = _ui.value.agents.filter { !it.archived }.map { it.id }.toSet()
        // Lead first: it is stored as Taskforce.leadId and takes the first (head) chair.
        val members = TaskforceRules.withLead(memberIds.filter { it in active }.distinct(), leadId).take(TaskforceRules.MAX_MEMBERS)
        if (name.isEmpty()) {
            showBanner("Give the meeting a title.")
            return
        }
        if (members.isEmpty()) {
            showBanner("Pick at least one agent for the meeting.")
            return
        }
        if (!ensureKey(members.first())) {
            return
        }
        viewModelScope.launch {
            val id = repository.createTaskforce(name, objective.trim(), members, TaskforceStatus.ACTIVE, steps)
            val names = members.mapNotNull { m -> _ui.value.agents.firstOrNull { it.id == m }?.name }
            repository.addTaskforceMessage(
                id, "user", "You",
                buildString {
                    append("Meeting: ").append(name)
                    if (objective.isNotBlank()) append("\nObjective: ").append(objective.trim())
                    append("\nTeam: ").append(names.joinToString()).append(" (lead: ").append(names.first()).append(")")
                },
            )
            _ui.update { it.copy(meetingDialogOpen = false) }
            selectTaskforce(id)
            taskforceRunner.kick(id)
            showBanner("Taskforce “$name” is heading to the conference room.")
        }
    }

    fun selectTaskforce(id: String) {
        _ui.update { it.copy(selectedTaskforceId = id, taskforceQuote = null, taskforceMessages = emptyList()) }
        taskforceMessageJob?.cancel()
        taskforceMessageJob = viewModelScope.launch {
            repository.observeTaskforceMessages(id).collect { list ->
                _ui.update { state -> if (state.selectedTaskforceId == id) state.copy(taskforceMessages = list) else state }
            }
        }
    }

    fun setTaskforceQuote(message: TaskforceMessage?) {
        _ui.update { it.copy(taskforceQuote = message) }
    }

    private fun sendTaskforce(id: String) {
        val state = _ui.value
        val text = state.draft.trim()
        if (text.isEmpty()) return
        val quote = state.taskforceQuote
        when (app.pixelbureau.logic.Slash.parse(text)?.kind) {
            app.pixelbureau.logic.Slash.Kind.STOP -> {
                taskforceRunner.stopDebate(id); _ui.update { it.copy(draft = "") }; return
            }
            app.pixelbureau.logic.Slash.Kind.SIGNOFF -> {
                _ui.update { it.copy(draft = "") }
                val tf = state.taskforces.firstOrNull { it.data.id == id }
                if (tf != null && TaskforceRules.canSignOff(tf.data.status)) signOffTaskforce(id) else showBanner("Nothing to sign off yet.")
                return
            }
            else -> Unit
        }
        viewModelScope.launch {
            repository.addTaskforceMessage(id, "user", "You", text, quote)
            _ui.update { it.copy(draft = "", taskforceQuote = null) }
            taskforceRunner.userSpoke(id)
        }
    }

    /** Re-run just this agent's failed taskforce turn (true) or skip it (false). */
    fun stopTaskforceDebate(id: String) = taskforceRunner.stopDebate(id)

    fun setTaskforceHeaderMode(id: String, mode: Int) {
        val m = mode.coerceIn(0, 2)
        container.settings.writeTaskforceHeaderMode(id, m)
        _ui.update { it.copy(taskforceHeaderModes = if (m == 0) it.taskforceHeaderModes - id else it.taskforceHeaderModes + (id to m)) }
    }

    fun resolveTaskforceFailure(taskforceId: String, agentId: String, retry: Boolean) {
        taskforceRunner.resolveFailure(taskforceId, agentId, retry)
    }

    /** [fromConclusion]: the "Conclude meeting" button on the lead's Conclusion (any open state). */
    fun signOffTaskforce(id: String, fromConclusion: Boolean = false) {
        viewModelScope.launch {
            val tf = repository.taskforce(id) ?: return@launch
            val crew = _ui.value.taskforces.firstOrNull { it.data.id == id }?.memberIds.orEmpty()
            val next = (if (fromConclusion) TaskforceRules.concludeMeeting(tf.status) else TaskforceRules.signOff(tf.status)) ?: run {
                showBanner("The team hasn't marked this done yet.")
                return@launch
            }
            taskforceRunner.stop(id)
            repository.addTaskforceMessage(id, "system", "Taskforce", "Signed off by you. Taskforce disbanded and archived.")
            repository.saveTaskforce(tf.copy(status = next))
            if (container.settings.markTaskforceAwarded(id)) {
                repository.awardTaskforceSignOff(tf.steps)
                sim.celebrate(crew)
                showBanner("+${app.pixelbureau.logic.taskforceReward(tf.steps)} ★  Signed off “${tf.title}”! The team is leaving the room.")
            } else {
                showBanner("Signed off “${tf.title}”. The team is leaving the room.")
            }
        }
    }

    fun disbandTaskforce(id: String) {
        viewModelScope.launch {
            val tf = repository.taskforce(id) ?: return@launch
            val next = TaskforceRules.disband(tf.status) ?: return@launch
            taskforceRunner.stop(id)
            repository.addTaskforceMessage(id, "system", "Taskforce", "Disbanded by you.")
            repository.saveTaskforce(tf.copy(status = next))
            showBanner("Disbanded “${tf.title}”.")
        }
    }

    /** Common tools + this agent's MCP tools (same set a 1:1 chat gets). */
    private suspend fun toolsFor(agentId: String): List<AgentTool> {
        val mcpServers = serversForAgent(
            container.settings.readMcpServers(),
            decodeAgentMcp(container.settings.readAgentMcp()[agentId]),
        )
        val mcpTools = if (mcpServers.isEmpty()) {
            emptyList()
        } else {
            try {
                container.mcp.agentTools(mcpServers)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }
        return enabledCommonTools(
            container.settings.readInternetSearchEnabled(),
            container.settings.readTimeToolEnabled(),
        ) + mcpTools
    }

    fun send() {
        val state = _ui.value
        state.selectedTaskforceId?.let { sendTaskforce(it); return }
        val agentId = state.selectedId ?: return
        meetingTaskforceFor(state.taskforces, agentId)?.let { tf ->
            showBanner("Busy — at meeting: ${tf.data.title}. Talk in the taskforce chat.")
            return
        }
        val text = state.draft.trim()
        val attachments = state.pendingAttachments
        if (text.isEmpty() && attachments.isEmpty()) {
            return
        }
        if (!ensureKey(agentId)) {
            return
        }
        val body = buildString {
            if (text.isNotEmpty()) append(text)
            if (attachments.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                attachments.forEachIndexed { index, item ->
                    if (index > 0) append('\n')
                    val label = if (item.kind == "image") "Attached image" else "Attached file"
                    append("[$label: ${item.name}]")
                }
            }
        }
        viewModelScope.launch {
            repository.addMessage(agentId, "user", body, null)
            _ui.update { state ->
                val doneId = freshDoneTaskId(agentId, state.tasks.toNotices())
                state.copy(
                    draft = "",
                    pendingAttachments = emptyList(),
                    dismissedTaskDone = if (doneId == null) state.dismissedTaskDone else state.dismissedTaskDone + doneId,
                    replyDoneIds = state.replyDoneIds - agentId,
                )
            }
            kick(agentId)
        }
    }

    fun assignTask(title: String, brief: String, agentId: String?) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            showBanner("Give the task a title.")
            return
        }
        val active = _ui.value.agents.filter { !it.archived }
        val chosen = agentId ?: active.minByOrNull { busyRank(it.status) }?.id
        if (chosen == null || active.none { it.id == chosen }) {
            showBanner("Hire someone before assigning work.")
            return
        }
        if (!ensureKey(chosen)) {
            return
        }
        viewModelScope.launch {
            val taskId = repository.addTask(chosen, trimmed, brief)
            repository.addMessage(chosen, "user", taskMessage(trimmed, brief), taskId)
            select(chosen)
            kick(chosen)
        }
    }

    /**
     * Start a failed task again. [agentId] null hands it to whoever is free.
     * The same task row is queued, and a new assignment message is what they answer.
     */
    fun retryTask(id: String, agentId: String?) {
        val active = _ui.value.agents.filter { !it.archived }
        val chosen = agentId?.takeIf { wanted -> active.any { it.id == wanted } }
            ?: active.minByOrNull { busyRank(it.status) }?.id
        if (chosen == null) {
            showBanner("Hire someone before retrying.")
            return
        }
        if (!ensureKey(chosen)) {
            return
        }
        viewModelScope.launch {
            val task = repository.task(id) ?: return@launch
            if (!repository.requeueTask(id, chosen)) {
                showBanner("That task is no longer failed.")
                return@launch
            }
            repository.addMessage(chosen, "user", taskMessage(task.title, task.brief), id)
            select(chosen)
            kick(chosen)
            val name = _ui.value.agents.firstOrNull { it.id == chosen }?.name ?: "Someone"
            val label = task.title.ifBlank { "Task" }
            showBanner("$name is taking “$label” again.")
        }
    }

    /** Remove a finished task, or stop + remove an active/queued one. */
    fun deleteTask(id: String) {
        viewModelScope.launch {
            val task = repository.task(id) ?: return@launch
            val inFlight = task.status == "working"
            if (inFlight) {
                val running = jobs[task.agentId]
                running?.cancel()
                running?.join()
                clearStream(task.agentId)
                repository.setStatus(task.agentId, "idle")
            }
            val removed = repository.deleteTask(id) ?: return@launch
            val label = removed.title.ifBlank { "Task" }
            when (removed.status) {
                "working" -> showBanner("Stopped and removed “$label”.")
                "queued" -> showBanner("Removed “$label” from the queue.")
                else -> showBanner("Removed “$label”.")
            }
        }
    }

    private fun hasKey(agentId: String): Boolean {
        val settings = settingsForAgent(agentId)
        return settings.apiKey.isNotBlank() && settings.baseUrl.isNotBlank()
    }

    private fun ensureKey(agentId: String): Boolean {
        val settings = settingsForAgent(agentId)
        if (settings.apiKey.isBlank()) {
            showBanner("Add a model in Settings before anyone can work.")
            return false
        }
        if (settings.baseUrl.isBlank()) {
            showBanner("Pick a provider in Settings.")
            return false
        }
        return true
    }

    private fun kick(agentId: String) {
        if (jobs[agentId]?.isActive == true) {
            return
        }
        jobs[agentId] = viewModelScope.launch {
            try {
                work(agentId)
            } finally {
                jobs.remove(agentId)
            }
        }
    }

    private suspend fun work(agentId: String) {
        while (true) {
            val agent = repository.agent(agentId) ?: return
            if (agent.archived) {
                return
            }
            val stored = repository.messages(agentId)
            val history = historyForNextTurn(stored.map { TurnMessage(it.role, it.text, it.taskId) }) ?: return
            _ui.update { it.copy(replyDoneIds = it.replyDoneIds - agentId) }
            val last = history.last()
            val task = last.taskId?.let { repository.task(it) }
            if (task != null && task.status == "queued") {
                repository.setTaskStatus(task.id, "working")
            }
            sim.arm(agentId)
            val seated = withTimeoutOrNull(20_000) {
                coroutineScope {
                    val waiter = async { arrivals.first { it == agentId } }
                    repository.setStatus(agentId, "walking")
                    waiter.await()
                }
            }
            if (seated == null) {
                repository.setStatus(agentId, "error")
                repository.addMessage(agentId, "assistant", "Couldn't reach the desk.", null)
                if (task != null) {
                    repository.setTaskStatus(task.id, "failed")
                }
                delay(1600)
                repository.setStatus(agentId, "idle")
                continue
            }
            repository.setStatus(agentId, "thinking")
            val fresh = repository.agent(agentId) ?: return
            val mcpServers = serversForAgent(
                container.settings.readMcpServers(),
                decodeAgentMcp(container.settings.readAgentMcp()[agentId]),
            )
            val mcpTools = if (mcpServers.isEmpty()) {
                emptyList()
            } else {
                try {
                    container.mcp.agentTools(mcpServers)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val tools = enabledCommonTools(
                container.settings.readInternetSearchEnabled(),
                container.settings.readTimeToolEnabled(),
            ) + mcpTools
            val prompt = buildSystemPrompt(
                fresh.name,
                fresh.personality,
                fresh.duties,
                task?.title,
                task?.brief,
                enabledTools = tools,
            )
            val settings = settingsForAgent(agentId)
            val buffer = StringBuilder()
            var messageId: String? = null
            var lastFlush = 0L
            var sawText = false
            suspend fun showLive(text: String) {
                val id = messageId
                if (id == null) {
                    messageId = repository.addMessage(agentId, "assistant", text, task?.id)
                } else if (System.currentTimeMillis() - lastFlush > 80) {
                    repository.updateMessage(id, text)
                    lastFlush = System.currentTimeMillis()
                }
                val shown = messageId ?: return
                _ui.update { state ->
                    state.copy(streams = state.streams + (agentId to StreamUi(shown, text)))
                }
            }
            try {
                container.chat.stream(
                    settings,
                    prompt,
                    history,
                    tools = tools,
                    deepWork = task != null,
                    onStatus = { status ->
                        if (status.startsWith("Thinking") || status.startsWith("Working the task")) {
                            repository.setStatus(agentId, "thinking")
                        }
                        if (status.startsWith("Trying again") || status.startsWith("Answering")) {
                            sawText = false
                            buffer.clear()
                        }
                        if (!sawText) {
                            showLive(status)
                        }
                    },
                ) { delta ->
                    if (!sawText) {
                        sawText = true
                        buffer.clear()
                        repository.setStatus(agentId, "writing")
                    }
                    buffer.append(delta)
                    showLive(buffer.toString())
                }
                val finalText = stripThinkTags(buffer.toString()).trim()
                if (finalText.isEmpty() ||
                    isPlaceholderReply(finalText) ||
                    isScratchpad(finalText) ||
                    isSearchResultDump(finalText) ||
                    isSearchPromise(finalText)
                ) {
                    throw ChatException("The model returned no text. Try again.")
                }
                val id = messageId
                if (id == null) {
                    repository.addMessage(agentId, "assistant", finalText, task?.id)
                } else {
                    repository.updateMessage(id, finalText)
                }
                clearStream(agentId)
                if (task != null && task.status != "done") {
                    repository.completeTask(task.id)
                    repository.setStatus(agentId, "done")
                    delay(2600)
                } else if (task == null) {
                    repository.noteChat()
                }
                _ui.update { it.copy(replyDoneIds = it.replyDoneIds + agentId) }
                repository.setStatus(agentId, "idle")
            } catch (cancelled: CancellationException) {
                val id = messageId
                if (id != null && buffer.isEmpty()) {
                    repository.deleteMessage(id)
                } else if (id != null) {
                    repository.updateMessage(id, buffer.toString().trim())
                }
                clearStream(agentId)
                repository.setStatus(agentId, "idle")
                throw cancelled
            } catch (error: Exception) {
                val reason = error.message ?: "The request failed."
                val raw = buffer.toString()
                val text = if (raw.isBlank() ||
                    isPlaceholderReply(raw) ||
                    isScratchpad(raw) ||
                    isSearchResultDump(raw) ||
                    isSearchPromise(raw)
                ) {
                    "Couldn't reach the model. $reason"
                } else {
                    raw
                }
                val id = messageId
                if (id == null) {
                    repository.addMessage(agentId, "assistant", text, task?.id)
                } else {
                    repository.updateMessage(id, text)
                }
                if (task != null && task.status != "done") {
                    repository.setTaskStatus(task.id, "failed")
                }
                repository.setStatus(agentId, "error")
                clearStream(agentId)
                delay(1800)
                repository.setStatus(agentId, "idle")
            }
        }
    }

    private fun watchMessages(agentId: String?) {
        watchingId = agentId
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            if (agentId == null) {
                _ui.update { it.copy(messages = emptyList()) }
                return@launch
            }
            repository.observeMessages(agentId).collect { list ->
                _ui.update { it.copy(messages = list) }
            }
        }
    }

    private fun clearStream(agentId: String) {
        _ui.update { state -> state.copy(streams = state.streams - agentId) }
    }

    private fun showBanner(text: String) {
        _ui.update { it.copy(banner = text) }
        viewModelScope.launch {
            delay(4200)
            _ui.update { state -> if (state.banner == text) state.copy(banner = null) else state }
        }
    }

    private fun busyRank(status: String): Int = when (status) {
        "idle" -> 0
        "done", "error" -> 1
        else -> 2
    }
}

/**
 * Conference chairs for live taskforce members: first come, first seated,
 * one chair per agent across all teams (the room has six).
 */
/** Open taskforce [agentId] is meeting in, or null when free to chat 1:1. */
internal fun meetingTaskforceFor(taskforces: List<TaskforceUi>, agentId: String): TaskforceUi? =
    taskforces.firstOrNull { TaskforceStatus.isOpen(it.data.status) && agentId in it.memberIds }

internal fun taskforceSeatMap(open: List<TaskforceUi>): Map<String, Cell> {
    // 4 chairs, then the 5th member stands at the top of the table. Extra members
    // of older taskforces (>5) are clamped: they simply aren't placed in the room.
    val seats = OfficeMap.confSeats + OfficeMap.confStandSpot
    val out = LinkedHashMap<String, Cell>()
    for (tf in open) for (id in tf.memberIds) {
        if (id in out || out.size >= seats.size) continue
        out[id] = seats[out.size]
    }
    return out
}
