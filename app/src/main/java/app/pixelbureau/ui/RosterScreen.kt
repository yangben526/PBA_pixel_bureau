package app.pixelbureau.ui

import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.activity.compose.BackHandler

import android.graphics.BitmapFactory
import app.pixelbureau.logic.DefaultOutputStyle
import app.pixelbureau.logic.instructionEditCostsStar
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pixelbureau.PixelBureauApp
import app.pixelbureau.R
import app.pixelbureau.Pixel_Bureau_Web.pamazon.PamazonPage
import app.pixelbureau.Pixel_Bureau_Web.pamazon.PamazonPlaceId
import app.pixelbureau.Pixel_Bureau_Web.pazada.PazadaPage
import app.pixelbureau.Pixel_Bureau_Web.pazada.PazadaPlaceId
import app.pixelbureau.Pixel_Bureau_Web.pixelhire.PixelHirePage
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopItem
import app.pixelbureau.Pixel_Bureau_Web.pmart.PmartPage
import app.pixelbureau.Pixel_Bureau_Web.pmart.PmartPlaceId
import app.pixelbureau.Pixel_Bureau_Web.shared.PixegleHeader
import app.pixelbureau.Pixel_Bureau_Web.shared.PixegleWordmark
import app.pixelbureau.data.Agent
import app.pixelbureau.data.Task
import app.pixelbureau.logic.OfficeMap
import app.pixelbureau.logic.SavedLlm
import app.pixelbureau.logic.displayTitle
import app.pixelbureau.logic.looks
import app.pixelbureau.logic.spriteFrame

private val KaiPanelShape = RoundedCornerShape(18.dp)
private val KaiTileShape = RoundedCornerShape(14.dp)
private val KaiChipShape = RoundedCornerShape(999.dp)
private val KaiBtnShape = RoundedCornerShape(12.dp)
private val KaiPillShape = RoundedCornerShape(10.dp)

@Composable
fun RosterScreen(model: BureauViewModel, modifier: Modifier = Modifier, onOpenOffice: () -> Unit = {}, startOnBureau: Boolean = false) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val active = ui.agents.filter { !it.archived }
    val cap = ui.bureau?.headcount ?: 1
    var editing by remember { mutableStateOf<Agent?>(null) }
    var assigning by remember { mutableStateOf(false) }
    var retrying by remember { mutableStateOf<Task?>(null) }
    var bindingAgent by remember { mutableStateOf<Agent?>(null) }
    var mcpAgent by remember { mutableStateOf<Agent?>(null) }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }
    var roleFilter by remember { mutableStateOf<app.pixelbureau.logic.RoleCategory?>(null) }
    var desk by remember { mutableStateOf(if (startOnBureau) CompanyDesk.Bureau else CompanyDesk.Hr) }
    var bureauStack by remember { mutableStateOf(listOf<String>()) }
    var bureauQuery by remember { mutableStateOf("") }
    val sprites = (LocalContext.current.applicationContext as PixelBureauApp).container.sprites

    LaunchedEffect(ui.openPixelHire) {
        if (ui.openPixelHire) {
            bureauQuery = ""
            bureauStack = listOf(PixelHirePlaceId)
            desk = CompanyDesk.Pixegle
            model.consumeOpenPixelHire()
        }
    }

    // System back inside Pixegle pops one page (e.g. Pazada → Pixegle home).
    BackHandler(enabled = desk == CompanyDesk.Pixegle && bureauStack.isNotEmpty()) {
        val top = bureauStack.lastOrNull()
        bureauStack = bureauStack.dropLast(1)
        if (top == PixelHirePlaceId && bureauStack.isEmpty()) {
            desk = CompanyDesk.Hr
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(KaiPage)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val webPage = bureauStack.lastOrNull()
        val onWeb = desk == CompanyDesk.Pixegle &&
            webPage in setOf(PixelHirePlaceId, PmartPlaceId, PamazonPlaceId, PazadaPlaceId)
        val stars = ui.bureau?.reputation ?: 0
        val buy: (ShopItem) -> Unit = { model.buyShopItem(it.id, it.name, it.salePrice) }
        CompanyHeader(
            bureau = desk == CompanyDesk.Pixegle && !onWeb,
            pixegle = onWeb,
            pixelBureau = desk == CompanyDesk.Bureau,
            reputation = ui.bureau?.reputation ?: 0,
        )

        if (desk != CompanyDesk.Pixegle) {
            Text(
                when (desk) {
                    CompanyDesk.Bureau ->
                        "Your cozy company floor — queue tasks, peek at desks and people, check inventory, and see which agent tools are on."
                    else ->
                        "Stars open desks. Hire in PixelHire, edit people here, and bind each one to a BYOK model from Settings."
                },
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
            )
        }

        if (ui.banner != null) {
            BannerChip(ui.banner ?: "")
        }

        DepartmentRow(selected = desk, onSelect = {
            desk = it
            bureauStack = emptyList()
            bureauQuery = ""
        })

        when (desk) {
            CompanyDesk.Pixegle -> {
                when (webPage) {
                    PixelHirePlaceId -> PixelHirePage(model = model, onBack = {
                        bureauStack = bureauStack.dropLast(1)
                        if (bureauStack.isEmpty()) {
                            desk = CompanyDesk.Hr
                        }
                    })
                    PmartPlaceId -> PmartPage(sprites = sprites, stars = stars, onBuy = buy, onBack = { bureauStack = bureauStack.dropLast(1) })
                    PamazonPlaceId -> PamazonPage(sprites = sprites, stars = stars, onBuy = buy, onBack = { bureauStack = bureauStack.dropLast(1) })
                    // Pazada opens only from its card on the Pixegle home; back returns there.
                    PazadaPlaceId -> PazadaPage(sprites = sprites, stars = stars, onBuy = buy, onBack = { bureauStack = bureauStack.dropLast(1) })
                    else -> BureauPortal(
                        placeId = webPage,
                        query = bureauQuery,
                        onQuery = { bureauQuery = it },
                        onOpen = { place -> bureauStack = bureauStack + place },
                        onBack = { bureauStack = bureauStack.dropLast(1) },
                    )
                }
            }
            CompanyDesk.Hr -> {
        val freeSlots = (cap - active.size).coerceAtLeast(0)
        PixelHireBanner {
            bureauQuery = ""
            bureauStack = listOf(PixelHirePlaceId)
            desk = CompanyDesk.Pixegle
        }
        // ── Reputation ─────────────────────────────────────────────────────
        KaiPanel(accent = KaiButter, title = "Bureau reputation", emoji = "★") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .clip(KaiPillShape)
                        .background(KaiAccent.copy(alpha = 0.35f))
                        .border(1.5.dp, KaiAccentLo, KaiPillShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "★ $stars",
                        color = KaiAccentLo,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Stars open hiring desks in PixelHire.",
                        color = KaiInk,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Earn them from chats and finished tasks. Editing instructions or output personality costs ★1.",
                        color = KaiInkMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        // ── Headcount ──────────────────────────────────────────────────────
        KaiPanel(accent = KaiMint, title = "Headcount", emoji = "🪑") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KaiStepperButton("−", enabled = cap > active.size.coerceAtLeast(1)) {
                    model.closeEmptySlot()
                }
                Box(
                    Modifier
                        .clip(KaiPillShape)
                        .background(KaiButter)
                        .border(1.5.dp, KaiBorder, KaiPillShape)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$cap",
                        color = KaiAccentLo,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                KaiStepperButton("+", enabled = cap < OfficeMap.deskCount && stars >= 1) {
                    model.openHiringSlot(promptHire = true)
                }
                Text(
                    "of ${OfficeMap.deskCount} desks",
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                SoftBadge(
                    label = "${active.size} on floor",
                    face = KaiSky,
                    ink = KaiInk,
                )
            }
            Text(
                if (freeSlots > 0) {
                    "$freeSlots empty desk${if (freeSlots == 1) "" else "s"}. Hire someone in AgentHire."
                } else {
                    "+ opens an empty desk for ★1. Hiring happens in AgentHire."
                },
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // ── Agents ─────────────────────────────────────────────────────────
        KaiPanel(
            accent = KaiPeach,
            title = "Team",
            emoji = "👥",
            trailing = { SoftBadge(label = "${active.size}", face = Color.White.copy(alpha = 0.7f), ink = KaiInk) },
        ) {
            if (active.isEmpty()) {
                EmptyHint("The office is empty. Open PixelHire to bring someone in.")
            }
            val cats = active.map { app.pixelbureau.logic.RoleCategory.of(it.duties) }.distinct().sortedBy { it.ordinal }
            if (cats.size > 1) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { ChoiceChip("All ${active.size}", roleFilter == null) { roleFilter = null } }
                    items(cats.size) { k ->
                        val c = cats[k]
                        ChoiceChip("${c.icon} ${c.label} ${active.count { app.pixelbureau.logic.RoleCategory.of(it.duties) == c }}", roleFilter == c) { roleFilter = c }
                    }
                }
            }
            for (agent in active.filter { roleFilter == null || app.pixelbureau.logic.RoleCategory.of(it.duties) == roleFilter }) {
                val boundId = ui.agentModelIds[agent.id]
                val bound = ui.models.firstOrNull { it.id == boundId }
                val llmLabel = when {
                    bound != null -> bound.displayTitle()
                    ui.activeModelId != null -> {
                        val def = ui.models.firstOrNull { it.id == ui.activeModelId }
                        "Default · ${def?.displayTitle() ?: "in use"}"
                    }
                    else -> "No model yet"
                }
                val mcpPick = ui.agentMcp[agent.id]
                val mcpOn = ui.mcpServers.count { it.enabled && (mcpPick == null || it.id in mcpPick) }
                AgentTile(
                    agent = agent,
                    llmLabel = llmLabel,
                    mcpLabel = when {
                        ui.mcpServers.isEmpty() -> null
                        mcpPick == null -> "Default · $mcpOn on"
                        else -> "$mcpOn server(s)"
                    },
                    onMcp = { mcpAgent = agent },
                    hasBound = bound != null,
                    spriteBitmap = sprites.get(spriteFrame(agent.spriteKey, "idle", 0)),
                    onEdit = { editing = agent },
                    onModel = { bindingAgent = agent },
                    onFire = { model.fire(agent.id) },
                )
            }
        }

            }
            CompanyDesk.Bureau -> BureauFloorOverview(
                ui = ui,
                sprites = sprites,
                onAssignTask = { assigning = true },
                onOpenTaskforce = { id -> model.selectTaskforce(id); onOpenOffice() },
                onChatAgent = { id -> meetingTaskforceFor(ui.taskforces, id)?.let { model.selectTaskforce(it.data.id) } ?: model.select(id); onOpenOffice() },
                onOpenHr = { desk = CompanyDesk.Hr },
                onRemoveItem = model::removeShopItem,
                onRetryTask = { retrying = it },
                onRemoveOrStopTask = { task ->
                    val running = task.status == "queued" || task.status == "working"
                    if (running) {
                        pendingDelete = task
                    } else {
                        model.deleteTask(task.id)
                    }
                },
            )
            else -> DepartmentHolder(desk)
        }

        Spacer(Modifier.height(8.dp))
    }

    val current = editing
    if (current != null) {
        AgentDialog(
            title = "Edit ${current.name}",
            initial = current,
            onDismiss = { editing = null },
            stars = ui.bureau?.reputation ?: 0,
            onSave = { name, personality, duties, sprite ->
                model.saveAgent(current.id, name, personality, duties, sprite)
                editing = null
            },
        )
    }
    val again = retrying
    if (again != null) {
        RetryTaskDialog(
            title = again.title,
            agents = active,
            initialAgentId = again.agentId,
            onDismiss = { retrying = null },
            onRetry = { agentId ->
                model.retryTask(again.id, agentId)
                retrying = null
            },
        )
    }
    if (ui.deskOpenedPrompt) {
        AlertDialog(
            onDismissRequest = { model.dismissDeskPrompt() },
            containerColor = KaiCard,
            title = {
                Text("Desk opened", color = KaiInk, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "★1 spent. The desk is empty. No one was hired. Go to AgentHire to hire an agent.",
                    color = KaiInkMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.dismissDeskPrompt()
                        bureauQuery = ""
                        bureauStack = listOf(PixelHirePlaceId)
                        desk = CompanyDesk.Pixegle
                    },
                ) { Text("Go to AgentHire", color = KaiAccentLo, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { model.dismissDeskPrompt() }) {
                    Text("Not now", color = KaiInkMuted)
                }
            },
        )
    }
    if (assigning) {
        TaskDialog(
            agents = active,
            onDismiss = { assigning = false },
            onSave = { title, brief, agentId ->
                model.assignTask(title, brief, agentId)
                assigning = false
            },
        )
    }
    val mcpFor = mcpAgent
    if (mcpFor != null) {
        AgentMcpDialog(
            agent = mcpFor,
            servers = ui.mcpServers,
            attached = ui.agentMcp[mcpFor.id],
            onDismiss = { mcpAgent = null },
            onSave = { ids ->
                model.bindAgentMcp(mcpFor.id, ids)
                mcpAgent = null
            },
        )
    }
    val binder = bindingAgent
    if (binder != null) {
        AgentModelDialog(
            agent = binder,
            models = ui.models,
            boundId = ui.agentModelIds[binder.id],
            activeId = ui.activeModelId,
            onDismiss = { bindingAgent = null },
            onPick = { modelId ->
                model.bindAgentModel(binder.id, modelId)
                bindingAgent = null
            },
        )
    }
    val doomed = pendingDelete
    if (doomed != null) {
        val working = doomed.status == "working"
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = KaiCard,
            title = {
                Text(
                    if (working) "Stop and remove?" else "Remove from queue?",
                    color = KaiInk,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    if (working) {
                        "“${doomed.title}” is in progress. They’ll stop and the task is removed."
                    } else {
                        "“${doomed.title}” hasn’t started yet. Remove it from the queue?"
                    },
                    color = KaiInkMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.deleteTask(doomed.id)
                        pendingDelete = null
                    },
                ) { Text(if (working) "Stop" else "Remove", color = Brick) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = KaiInkMuted)
                }
            },
        )
    }
}

private enum class CompanyDesk(val label: String, val title: String, val blurb: String) {
    Hr("HR", "HR", "Stars, desks, PixelHire, and the people on the floor."),
    Analytics("Analytics", "Analytics", "Charts for the bureau will live here."),
    Pixegle("Pixegle", "Pixegle", "Shops, news, and hiring for the bureau."),
    Bureau("Pixel Bureau", "Pixel Bureau", "Tasks first, then floor — agents, inventory, and agent tools."),
}

@Composable
private fun PixelHireBanner(onClick: () -> Unit) {
    val context = LocalContext.current
    val image = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.portal_pixelhire_banner).asImageBitmap()
    }
    val ratio = image.width / image.height.toFloat()
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "PixelHire. Find great talent. Build a better office. Post a job." },
    ) {
        drawImage(
            image,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(
                size.width.roundToInt().coerceAtLeast(1),
                size.height.roundToInt().coerceAtLeast(1),
            ),
            filterQuality = FilterQuality.None,
        )
    }
}

@Composable
private fun DepartmentRow(selected: CompanyDesk, onSelect: (CompanyDesk) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompanyDesk.entries.forEach { desk ->
            val on = desk == selected
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (on) Color(0xFFFFF3D6) else Color(0xFFFFFBF4))
                    .border(if (on) 2.5.dp else 1.5.dp, if (on) KaiAccent else KaiBorder, shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = { onSelect(desk) },
                    )
                    .padding(horizontal = 2.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DepartmentGlyph(desk, Modifier.size(36.dp))
                Text(
                    desk.label,
                    color = KaiInk,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .width(18.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (on) KaiAccent else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun DepartmentHolder(desk: CompanyDesk) {
    KaiPanel(accent = KaiButter, title = desk.title, emoji = "📌") {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DepartmentGlyph(desk, Modifier.size(72.dp))
            Text(
                desk.blurb,
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun DepartmentGlyph(desk: CompanyDesk, modifier: Modifier = Modifier) {
    val res = when (desk) {
        CompanyDesk.Hr -> R.drawable.portal_icon_hr
        CompanyDesk.Analytics -> R.drawable.portal_icon_analytics
        CompanyDesk.Pixegle -> R.drawable.pixegle_tab_icon
        CompanyDesk.Bureau -> R.drawable.pixel_bureau_tab_icon
    }
    Image(
        painterResource(res),
        contentDescription = desk.label,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun CompanyHeader(
    bureau: Boolean = false,
    pixegle: Boolean = false,
    pixelBureau: Boolean = false,
    reputation: Int = 0,
) {
    if (pixegle) {
        PixegleHeader()
        return
    }
    if (bureau) {
        // Wordmark replaces icon + "Pixegle" title text. Fixed height; no BoxWithConstraints.
        Row(
            Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixegleWordmark(heightDp = 32.dp)
            Spacer(Modifier.weight(1f))
        }
        return
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (pixelBureau) {
            Image(
                painterResource(R.drawable.portal_icon_bureau),
                contentDescription = "Pixel Bureau",
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KaiButter)
                    .border(2.dp, KaiBorderDeep, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🏢", fontSize = 18.sp)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    pixelBureau -> "Pixel Bureau"
                    else -> "Company"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = KaiInk,
                fontWeight = FontWeight.Bold,
            )
            // Soft pastel pixel underline accent
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .width(52.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KaiAccent),
            )
        }
        val pulse = rememberInfiniteTransition(label = "starPulse")
        val starScale by pulse.animateFloat(
            initialValue = 1f,
            targetValue = if (pixelBureau) 1.06f else 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "starScale",
        )
        Box(
            Modifier
                .scale(starScale)
                .clip(KaiPillShape)
                .background(KaiButter)
                .border(1.5.dp, KaiAccent, KaiPillShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                "★ $reputation",
                color = KaiAccentLo,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun BannerChip(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(KaiTileShape)
            .background(KaiRose)
            .border(1.5.dp, Brick.copy(alpha = 0.45f), KaiTileShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("⚠", fontSize = 14.sp)
        Text(text, color = Brick, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun EmptyHint(text: String) {
    Text(
        text,
        color = KaiInkMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .clip(KaiTileShape)
            .background(KaiCardNest)
            .border(1.dp, KaiBorder.copy(alpha = 0.55f), KaiTileShape)
            .padding(12.dp),
    )
}

/**
 * Soft cream panel with pastel header strip — light Kairosoft management card.
 * Keeps a warm double-frame (deep outer + soft inner) so it feels game-UI, not Material.
 * Optional [leading] replaces the emoji (e.g. pixel icon) when provided.
 */
@Composable
internal fun KaiPanel(
    accent: Color,
    title: String,
    emoji: String = "",
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // Soft drop plate under the card
                drawRoundRect(
                    color = KaiBorderDeep.copy(alpha = 0.28f),
                    topLeft = Offset(0f, 3.dp.toPx()),
                    size = size.copy(height = size.height - 3.dp.toPx()),
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                )
            }
            .clip(KaiPanelShape)
            .background(KaiCard)
            .border(2.dp, KaiBorderDeep, KaiPanelShape)
            .border(1.dp, Color.White.copy(alpha = 0.55f), KaiPanelShape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(accent)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (leading != null) {
                leading()
            } else if (emoji.isNotEmpty()) {
                Text(emoji, fontSize = 15.sp)
            }
            Text(
                title,
                color = KaiInk,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 0.2.sp,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = { content() },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AgentTile(
    agent: Agent,
    llmLabel: String,
    mcpLabel: String?,
    onMcp: () -> Unit,
    hasBound: Boolean,
    spriteBitmap: android.graphics.Bitmap?,
    onEdit: () -> Unit,
    onModel: () -> Unit,
    onFire: () -> Unit,
) {
    val lookLabel = looks.firstOrNull { it.first == agent.spriteKey }?.second ?: agent.spriteKey
    val cat = app.pixelbureau.logic.RoleCategory.of(agent.duties)
    val role = agent.duties.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
    val tint = Color(cat.tint)
    val (dot, statusLabel) = when (agent.status) {
        "working", "typing" -> Color(0xFF34A853) to "Working"
        "walking" -> Color(0xFF4A90E2) to "Walking"
        "done" -> Color(0xFF34A853) to "Done"
        else -> Color(0xFFB0B7C0) to "Idle"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(3.dp, KaiTileShape, ambientColor = KaiBorderDeep.copy(alpha = 0.3f), spotColor = KaiBorderDeep.copy(alpha = 0.3f))
            .clip(KaiTileShape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.22f), KaiCardNest, KaiCardNest)))
            .border(1.5.dp, KaiBorder, KaiTileShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.verticalGradient(listOf(Color.White, tint.copy(alpha = 0.35f))))
                    .border(1.5.dp, KaiBorderDeep, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (spriteBitmap != null) {
                    Image(spriteBitmap.asImageBitmap(), agent.name, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, modifier = Modifier.size(48.dp))
                } else {
                    Text(lookLabel.take(1), color = KaiInk, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(3.dp).size(10.dp).clip(CircleShape).background(dot)
                        .border(1.5.dp, Color.White, CircleShape).semantics { contentDescription = statusLabel },
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(agent.name, color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${cat.icon} ${cat.label}", color = KaiInk, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.35f))
                            .border(1.dp, tint, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                    Text("$statusLabel · $lookLabel", color = KaiInkMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (role != null) Text(role, color = KaiInkMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (agent.personality.isNotBlank()) {
            Text(agent.personality, color = KaiInkMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SoftBadge(label = "🧠 $llmLabel", face = if (hasBound) KaiButter else KaiPeach, ink = if (hasBound) KaiAccentLo else KaiInk, onClick = onModel)
            if (mcpLabel != null) SoftBadge(label = "🔌 MCP · $mcpLabel", face = KaiMint, ink = KaiInk, onClick = onMcp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(KaiBorder.copy(alpha = 0.5f)))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SoftActionChip("Edit", onEdit)
            SoftActionChip("Model", onModel)
            if (mcpLabel != null) SoftActionChip("MCP", onMcp)
            Spacer(Modifier.weight(1f))
            SoftActionChip("Fire", onFire, danger = true)
        }
    }
}

@Composable
internal fun TaskTile(
    task: Task,
    who: String,
    onRetry: (() -> Unit)?,
    onRemoveOrStop: () -> Unit,
) {
    val (statusFace, statusInk, statusLabel) = when (task.status) {
        "done" -> Triple(KaiMint, Color(0xFF3A6B2E), "done")
        "failed" -> Triple(KaiRose, Brick, "failed")
        "working" -> Triple(KaiButter, KaiAccentLo, "working")
        "queued" -> Triple(KaiSky, KaiInk, "queued")
        else -> Triple(KaiCardNest, KaiInkMuted, task.status)
    }
    val running = task.status == "queued" || task.status == "working"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(KaiTileShape)
            .background(KaiCardNest)
            .border(1.5.dp, KaiBorder, KaiTileShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                task.title,
                color = KaiInk,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                who,
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SoftBadge(label = statusLabel, face = statusFace, ink = statusInk)
        if (onRetry != null) {
            SoftActionChip(label = "Retry", onClick = onRetry)
        }
        SoftActionChip(
            label = if (running) "Stop" else "Remove",
            onClick = onRemoveOrStop,
            danger = true,
        )
    }
}

@Composable
internal fun SoftBadge(
    label: String,
    face: Color,
    ink: Color,
    onClick: (() -> Unit)? = null,
) {
    val shape = KaiChipShape
    val base = Modifier
        .clip(shape)
        .background(face)
        .border(1.dp, KaiBorder.copy(alpha = 0.7f), shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )
        .padding(horizontal = 10.dp, vertical = 4.dp)
    Text(
        label,
        color = ink,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = base,
    )
}

@Composable
private fun SoftActionChip(label: String, onClick: () -> Unit, danger: Boolean = false) {
    val face = if (danger) KaiRose else Color.White.copy(alpha = 0.65f)
    val ink = if (danger) Brick else KaiInk
    Text(
        label,
        color = ink,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(KaiChipShape)
            .background(face)
            .border(1.25.dp, if (danger) Brick.copy(alpha = 0.45f) else KaiBorder, KaiChipShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun KaiStepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(KaiAccent.copy(alpha = alpha))
            .border(1.5.dp, KaiAccentLo.copy(alpha = alpha), CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Ink.copy(alpha = alpha),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
    }
}

/**
 * Raised 3D game button — light top/left rim, dark bottom/right bevel.
 */
@Composable
internal fun KaiActionButton(
    label: String,
    enabled: Boolean,
    face: Color,
    highlight: Color,
    shadow: Color,
    ink: Color,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = Modifier
            .drawBehind {
                val r = 12.dp.toPx()
                drawRoundRect(
                    color = shadow.copy(alpha = alpha * 0.9f),
                    topLeft = Offset(0f, 2.dp.toPx()),
                    size = size.copy(height = size.height - 2.dp.toPx()),
                    cornerRadius = CornerRadius(r, r),
                )
            }
            .clip(KaiBtnShape)
            .background(face.copy(alpha = alpha))
            .drawBehind {
                val inset = 2.dp.toPx()
                drawLine(
                    color = highlight.copy(alpha = alpha),
                    start = Offset(inset, inset),
                    end = Offset(size.width - inset, inset),
                    strokeWidth = 2.5.dp.toPx(),
                )
                drawLine(
                    color = highlight.copy(alpha = alpha),
                    start = Offset(inset, inset),
                    end = Offset(inset, size.height - inset),
                    strokeWidth = 2.5.dp.toPx(),
                )
                drawLine(
                    color = shadow.copy(alpha = alpha),
                    start = Offset(inset, size.height - inset),
                    end = Offset(size.width - inset, size.height - inset),
                    strokeWidth = 2.5.dp.toPx(),
                )
                drawLine(
                    color = shadow.copy(alpha = alpha),
                    start = Offset(size.width - inset, inset),
                    end = Offset(size.width - inset, size.height - inset),
                    strokeWidth = 2.5.dp.toPx(),
                )
            }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = ink.copy(alpha = alpha),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Dark SectionCard — legacy helper; Roster/Settings use Kai panels. */
@Composable
internal fun SectionCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

internal val KaiFieldColors
    @Composable
    get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = KaiInk,
        unfocusedTextColor = KaiInk,
        focusedBorderColor = KaiAccent,
        unfocusedBorderColor = KaiBorder,
        focusedLabelColor = KaiInkMuted,
        unfocusedLabelColor = KaiInkMuted,
        cursorColor = KaiAccent,
        focusedContainerColor = KaiCard,
        unfocusedContainerColor = KaiCardNest,
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentModelDialog(
    agent: Agent,
    models: List<SavedLlm>,
    boundId: String?,
    activeId: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val usingDefault = boundId.isNullOrBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaiCard,
        title = {
            Text("Model for ${agent.name}", color = KaiInk, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Pick a BYOK model from Settings, or use the one marked In use.",
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (models.isEmpty()) {
                    Text("Add a model in Settings first.", color = Brick)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChoiceChip(
                            label = "Default" + (activeId?.let { id ->
                                models.firstOrNull { it.id == id }?.let { " · ${it.displayTitle()}" } ?: ""
                            } ?: ""),
                            selected = usingDefault,
                        ) { onPick(null) }
                        for (saved in models) {
                            ChoiceChip(saved.displayTitle(), boundId == saved.id) { onPick(saved.id) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = KaiInkMuted) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentDialog(
    title: String,
    initial: Agent?,
    stars: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var personality by remember { mutableStateOf(initial?.personality.orEmpty()) }
    var duties by remember { mutableStateOf(initial?.duties ?: DefaultOutputStyle) }
    var sprite by remember { mutableStateOf(initial?.spriteKey ?: looks.random().first) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaiCard,
        title = { Text(title, color = KaiInk, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = KaiFieldColors,
                )
                Text("Look", color = KaiInkMuted)
                Box(
                    Modifier
                        .heightIn(max = 168.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((key, label) in looks) {
                            ChoiceChip(label, sprite == key) { sprite = key }
                        }
                    }
                }
                OutlinedTextField(
                    personality,
                    { personality = it },
                    label = { Text("Custom instructions") },
                    minLines = 2,
                    colors = KaiFieldColors,
                )
                OutlinedTextField(
                    duties,
                    { duties = it },
                    label = { Text("Output personality") },
                    minLines = 2,
                    colors = KaiFieldColors,
                )
                val costsStar = instructionEditCostsStar(
                    initial?.personality.orEmpty(),
                    initial?.duties ?: DefaultOutputStyle,
                    personality,
                    duties,
                )
                Text(
                    when {
                        costsStar && stars < 1 -> "Need a ★ to change instructions or output personality. Name and look are free."
                        costsStar -> "Saving this change costs ★1. Name and look are free."
                        else -> "Instructions and output personality are this person's own rules. Changing them costs ★1."
                    },
                    color = if (costsStar && stars < 1) Brick else KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            val costsStar = instructionEditCostsStar(
                initial?.personality.orEmpty(),
                initial?.duties ?: DefaultOutputStyle,
                personality,
                duties,
            )
            val canSave = name.isNotBlank() && !(costsStar && stars < 1)
            TextButton(
                enabled = canSave,
                onClick = { onSave(name, personality, duties, sprite) },
            ) {
                Text(
                    if (costsStar) "Save · ★1" else "Save",
                    color = if (canSave) KaiAccentLo else KaiInkMuted,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = KaiInkMuted) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RetryTaskDialog(
    title: String,
    agents: List<Agent>,
    initialAgentId: String?,
    onDismiss: () -> Unit,
    onRetry: (String?) -> Unit,
) {
    val stillHere = agents.any { it.id == initialAgentId }
    var agentId by remember { mutableStateOf(if (stillHere) initialAgentId else null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaiCard,
        title = { Text("Retry this task?", color = KaiInk, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "“$title”",
                    color = KaiInk,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Choose who takes it from here.",
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Who", color = KaiInkMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("Whoever is free", agentId == null) { agentId = null }
                    for (agent in agents) {
                        ChoiceChip(agent.name, agentId == agent.id) { agentId = agent.id }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRetry(agentId) }) {
                Text("Retry", color = KaiAccentLo, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = KaiInkMuted) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskDialog(
    agents: List<Agent>,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var brief by remember { mutableStateOf("") }
    var agentId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaiCard,
        title = { Text("Assign a task", color = KaiInk, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = KaiFieldColors,
                )
                OutlinedTextField(
                    brief,
                    { brief = it },
                    label = { Text("What they should do") },
                    minLines = 2,
                    colors = KaiFieldColors,
                )
                Text("Who", color = KaiInkMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("Whoever is free", agentId == null) { agentId = null }
                    for (agent in agents) {
                        ChoiceChip(agent.name, agentId == agent.id) { agentId = agent.id }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, brief, agentId) }) {
                Text("Assign", color = KaiAccentLo, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = KaiInkMuted) }
        },
    )
}

@Composable
internal fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val face = if (selected) KaiButter else KaiCardNest
    val ink = if (selected) KaiAccentLo else KaiInk
    val border = if (selected) KaiAccent else KaiBorder
    Text(
        label,
        color = ink,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(KaiChipShape)
            .background(face)
            .border(1.5.dp, border, KaiChipShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentMcpDialog(
    agent: Agent,
    servers: List<app.pixelbureau.logic.McpServerConfig>,
    attached: Set<String>?,
    onDismiss: () -> Unit,
    onSave: (Set<String>?) -> Unit,
) {
    var useDefault by remember { mutableStateOf(attached == null) }
    var picked by remember { mutableStateOf(attached ?: servers.filter { it.enabled }.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaiCard,
        title = { Text("MCP for ${agent.name}", color = KaiInk, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Default gives this agent every enabled server from Settings. Or pick the servers it may use.",
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (servers.isEmpty()) {
                    Text("Add an MCP server in Settings first.", color = Brick)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChoiceChip("Default · all enabled", useDefault) { useDefault = true }
                        ChoiceChip("Custom", !useDefault) { useDefault = false }
                    }
                    if (!useDefault) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (server in servers) {
                                val label = server.name + if (server.enabled) "" else " (off)"
                                ChoiceChip(label, server.id in picked) {
                                    picked = if (server.id in picked) picked - server.id else picked + server.id
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(if (useDefault) null else picked) }, enabled = servers.isNotEmpty()) {
                Text("Save", color = KaiAccentLo, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = KaiInkMuted) }
        },
    )
}
