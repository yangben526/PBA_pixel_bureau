package app.pixelbureau.ui

import androidx.compose.material3.AlertDialog
import app.pixelbureau.data.Agent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopCategory
import app.pixelbureau.logic.TaskforceRules
import app.pixelbureau.logic.TaskforceStatus
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopCatalog
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopStar
import app.pixelbureau.R
import app.pixelbureau.data.Task
import app.pixelbureau.logic.CommonAgentTools
import app.pixelbureau.logic.OfficeMap
import app.pixelbureau.logic.displayTitle
import app.pixelbureau.logic.looks
import app.pixelbureau.logic.spriteFrame
import app.pixelbureau.sprites.SpriteStore
import kotlinx.coroutines.delay

private val FloorTileShape = RoundedCornerShape(14.dp)
private val FloorPillShape = RoundedCornerShape(12.dp)
private val FloorHeroShape = RoundedCornerShape(18.dp)
private val FloorIconWell = RoundedCornerShape(10.dp)

private val ColorInkDeep = Color(0xFF3A6B2E)
private val HeroWash = Color(0xFFFFF6E4)

/**
 * Soft Kairosoft-style Pixel Bureau company home: task board first, then floor summary,
 * agents, inventories, and agent tools — live from [BureauUi] / SettingsStore via the ViewModel.
 * Light tablet-friendly enter animations; data wiring unchanged.
 */
@Composable
internal fun BureauFloorOverview(
    ui: BureauUi,
    sprites: SpriteStore,
    onAssignTask: () -> Unit,
    onOpenTaskforce: (String) -> Unit = {},
    onChatAgent: (String) -> Unit = {},
    onOpenHr: (String) -> Unit = {},
    onRetryTask: (Task) -> Unit,
    onRemoveOrStopTask: (Task) -> Unit,
    onRemoveItem: (String) -> Unit = {},
) {
    val active = remember(ui.agents) { ui.agents.filter { !it.archived } }
    val stars = ui.bureau?.reputation ?: 0
    val cap = ui.bureau?.headcount ?: 1
    val owned = ui.ownedItems
    val placed = remember(ui.placedProps) {
        ui.placedProps.filterNot { it.slot.startsWith("preview:") }
    }
    val placedByItem = remember(placed) { placed.groupBy { it.itemId } }
    val ownedRows = remember(owned) {
        owned.entries
            .filter { it.value > 0 }
            .sortedBy { (id, _) -> ShopCatalog.byId(id)?.name ?: id }
    }
    val deskOwned = ownedRows.filter { (id, _) ->
        ShopCatalog.byId(id)?.exclusiveGroup == ShopCatalog.GROUP_DESK
    }
    val shelfOwned = ownedRows.filter { (id, _) ->
        ShopCatalog.byId(id)?.exclusiveGroup != ShopCatalog.GROUP_DESK
    }
    val activeModel = ui.models.firstOrNull { it.id == ui.activeModelId }
    val boundCount = ui.agentModelIds.count { (agentId, modelId) ->
        active.any { it.id == agentId } && modelId.isNotBlank()
    }

    // Staggered section visibility — short delays so tablets stay snappy.
    var showHero by remember { mutableStateOf(false) }
    var showTasks by remember { mutableStateOf(false) }
    var showFloor by remember { mutableStateOf(false) }
    var showAgents by remember { mutableStateOf(false) }
    var showInv by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showHero = true
        delay(40)
        showTasks = true
        delay(55)
        showFloor = true
        delay(55)
        showAgents = true
        delay(55)
        showInv = true
        delay(55)
        showTools = true
    }

    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FloorEnter(visible = showHero) {
            FloorHeroCard(
                company = ui.bureau?.name.orEmpty(),
                stars = stars,
                crew = heroCrew(active),
                sprites = sprites,
                spritesReady = ui.spritesReady,
                activeTasks = ui.tasks.count { it.status == "queued" || it.status == "working" },
                taskforces = ui.taskforces.count { TaskforceStatus.isOpen(it.data.status) },
            )
        }

        // ── Tasks (first on Pixel Bureau floor) ────────────────────────────
        FloorEnter(visible = showTasks) {
            KaiPanel(
                accent = KaiSky,
                title = "Tasks",
                leading = { FloorPixelIcon(R.drawable.task_done) },
                trailing = {
                    KaiActionButton(
                        label = "Assign",
                        enabled = true,
                        face = KaiMintDeep,
                        highlight = Color(0xFFB8DFA8),
                        shadow = Color(0xFF5A8F4A),
                        ink = Ink,
                        onClick = onAssignTask,
                    )
                },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Queue work for the floor — someone will walk to a desk.",
                        color = KaiInkMuted,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp,
                    )
                    // Sub-section: live taskforces (active only; gone after sign-off / disband).
                    val liveTf = ui.taskforces.filter { TaskforceStatus.isOpen(it.data.status) }
                    if (liveTf.isNotEmpty()) {
                        FloorSectionLabel("Taskforce")
                        for (tf in liveTf) {
                            FloorTaskforceRow(tf, ui, sprites) { onOpenTaskforce(tf.data.id) }
                        }
                        if (ui.tasks.isNotEmpty()) FloorSectionLabel("Tasks")
                    }
                    val tasks = ui.tasks.take(12)
                    if (tasks.isEmpty() && liveTf.isEmpty()) {
                        EmptyHint("Nothing queued yet. Tap Assign and pick a teammate.")
                    }
                    for ((index, task) in tasks.withIndex()) {
                        val who = ui.agents.firstOrNull { it.id == task.agentId }?.name ?: "Someone"
                        FloorEnter(visible = showTasks, staggerIndex = index) {
                            TaskTile(
                                task = task,
                                who = who,
                                onRetry = if (task.status == "failed") {
                                    { onRetryTask(task) }
                                } else {
                                    null
                                },
                                onRemoveOrStop = { onRemoveOrStopTask(task) },
                            )
                        }
                    }
                }
            }
        }

        // ── Floor / Bureau summary ─────────────────────────────────────────
        FloorEnter(visible = showFloor) {
            KaiPanel(
                accent = KaiButter,
                title = "Floor overview",
                leading = { FloorPixelIcon(R.drawable.portal_icon_bureau) },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FloorStatChip(
                            label = "Stars",
                            value = "$stars",
                            face = KaiAccent.copy(alpha = 0.28f),
                            ink = KaiAccentLo,
                            modifier = Modifier.weight(1f),
                            goldStar = true,
                            pulse = true,
                        )
                        FloorStatChip(
                            label = "Desks",
                            value = "$cap / ${OfficeMap.deskCount}",
                            face = KaiMint,
                            ink = KaiInk,
                            modifier = Modifier.weight(1f),
                        )
                        FloorStatChip(
                            label = "Agents",
                            value = "${active.size}",
                            face = KaiSky,
                            ink = KaiInk,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        when {
                            active.isEmpty() -> "Empty floor — open PixelHire from HR to hire."
                            cap > active.size ->
                                "${cap - active.size} open desk slot${if (cap - active.size == 1) "" else "s"} ready for new hires."
                            else -> "Every open desk has someone on it. Nice and busy!"
                        },
                        color = KaiInkMuted,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp,
                    )
                    SoftBadge(
                        label = when (ui.deskSprite) {
                            DESK_SPRITE_OAK -> "Work desks · Oakwood"
                            else -> "Work desks · Startup Premium / default"
                        },
                        face = KaiPeach,
                        ink = KaiInk,
                    )
                    SoftBadge(
                        label = when (ui.confStyle) {
                            CONF_STYLE_OAK -> "Conference · Dream Starter"
                            else -> "Conference · Premium Startup"
                        },
                        face = KaiSky,
                        ink = KaiInk,
                    )
                }
            }
        }

        // ── Agents ─────────────────────────────────────────────────────────
        FloorEnter(visible = showAgents) {
            KaiPanel(
                accent = KaiPeach,
                title = "Agents on floor",
                leading = { FloorPixelIcon(R.drawable.portal_icon_hr) },
            ) {
                FloorAgentsGrid(ui = ui, active = active, sprites = sprites, onChat = onChatAgent, onOpenHr = onOpenHr)
            }
        }

        // ── Inventories ────────────────────────────────────────────────────
        FloorEnter(visible = showInv) {
            KaiPanel(
                accent = KaiMint,
                title = "Inventories",
                leading = { FloorPixelIcon(R.drawable.portal_icon_shop) },
            ) {
                InventoryGrid(ui = ui, sprites = sprites, ownedRows = ownedRows, placedByItem = placedByItem, onRemove = onRemoveItem)
            }
        }

        // ── Agent tools + BYOK ─────────────────────────────────────────────
        FloorEnter(visible = showTools) {
            KaiPanel(
                accent = KaiSky,
                title = "Agent tools",
                leading = { FloorPixelIcon(R.drawable.portal_icon_analytics) },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Common tools (Settings) apply to every agent on the floor.",
                        color = KaiInkMuted,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp,
                    )
                    for (tool in CommonAgentTools) {
                        val on = when (tool.id) {
                            "internet_search" -> ui.internetSearchEnabled
                            "time_tool" -> ui.timeToolEnabled
                            else -> true
                        }
                        FloorToolRow(
                            title = tool.title,
                            id = tool.id,
                            detail = tool.description,
                            enabled = on,
                        )
                    }
                    Text(
                        "Per-agent Special tools are not wired yet — Common only.",
                        color = KaiInkMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.size(2.dp))
                    FloorSectionLabel("LLM BYOK")
                    if (ui.models.isEmpty()) {
                        EmptyHint("No models saved. Add a BYOK model in Settings.")
                    } else {
                        SoftBadge(
                            label = "In use · ${activeModel?.displayTitle() ?: "none marked"}",
                            face = if (activeModel != null) KaiButter else KaiPeach,
                            ink = if (activeModel != null) KaiAccentLo else KaiInk,
                        )
                        Text(
                            "${ui.models.size} model${if (ui.models.size == 1) "" else "s"} in catalog · $boundCount agent-specific bind${if (boundCount == 1) "" else "s"}",
                            color = KaiInkMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloorEnter(
    visible: Boolean,
    staggerIndex: Int = 0,
    content: @Composable () -> Unit,
) {
    val delayMs = (staggerIndex * 35).coerceAtMost(180)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(280, delayMillis = delayMs, easing = FastOutSlowInEasing)) +
            slideInVertically(
                animationSpec = tween(280, delayMillis = delayMs, easing = FastOutSlowInEasing),
                initialOffsetY = { (it * 0.08f).toInt().coerceAtLeast(10) },
            ),
    ) {
        content()
    }
}

@Composable
private fun FloorHeroCard(company: String, stars: Int, crew: List<Agent>, sprites: SpriteStore, spritesReady: Boolean, activeTasks: Int, taskforces: Int) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val idle = rememberInfiniteTransition(label = "hero")
    val t by idle.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "heroT")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(FloorHeroShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFFE6B8), Color(0xFFFFC9A8), Color(0xFFF7B3C2))))
            .border(1.5.dp, KaiBorder, FloorHeroShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(heroGreeting(hour), color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    company.ifBlank { "Pixel Bureau" },
                    color = KaiInk.copy(alpha = 0.65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            // Agents peeking over the card edge with a gentle staggered bob + one twinkle.
            Box(Modifier.height(44.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp), verticalAlignment = Alignment.Bottom) {
                    crew.take(HERO_FACES).forEachIndexed { k, a ->
                        val bob = kotlin.math.sin((t + k * 0.22f) * 2f * Math.PI.toFloat()) * 2f
                        Box(
                            Modifier.offset(y = bob.dp).size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.75f))
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { HeroHead(a.spriteKey, sprites, spritesReady) }
                    }
                    if (crew.size > HERO_FACES) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(KaiInk.copy(alpha = 0.75f)).border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("+${crew.size - HERO_FACES}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                    if (crew.isEmpty()) {
                        Image(painterResource(R.drawable.portal_icon_bureau), "Pixel Bureau", Modifier.size(36.dp), contentScale = ContentScale.Fit)
                    }
                }
                val tw = (kotlin.math.sin(t * 2f * Math.PI.toFloat()) + 1f) / 2f
                Text("✦", color = Color.White.copy(alpha = 0.35f + 0.6f * tw), fontSize = (9 + 3 * tw).sp, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HeroChip("👥", "${crew.size}", "agents", Modifier.weight(1f))
            HeroChip("★", "$stars", "stars", Modifier.weight(1f))
            HeroChip("📋", "$activeTasks", "tasks", Modifier.weight(1f))
            HeroChip("🤝", "$taskforces", "meetings", Modifier.weight(1f))
        }
    }
}

internal fun heroGreeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning, boss"
    in 12..17 -> "Good afternoon, boss"
    in 18..22 -> "Good evening, boss"
    else -> "Burning the midnight oil, boss?"
}

@Composable
private fun HeroChip(icon: String, value: String, label: String, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.62f))
            .border(1.dp, Color.White, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(icon, fontSize = 11.sp, color = if (icon == "★") Color(0xFFE0A21B) else KaiInk)
        Spacer(Modifier.width(3.dp))
        Text(value, color = KaiInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(3.dp))
        Text(label, color = KaiInk.copy(alpha = 0.6f), fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun FloorPixelIcon(resId: Int) {
    Box(
        Modifier
            .size(28.dp)
            .clip(FloorIconWell)
            .background(Color.White.copy(alpha = 0.55f))
            .border(1.dp, KaiBorder.copy(alpha = 0.65f), FloorIconWell),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun FloorSectionLabel(text: String) {
    Text(
        text,
        color = KaiInk,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 0.2.sp,
    )
}

private fun floorLlmLabel(ui: BureauUi, agentId: String): String {
    val boundId = ui.agentModelIds[agentId]
    val bound = ui.models.firstOrNull { it.id == boundId }
    return when {
        bound != null -> bound.displayTitle()
        ui.activeModelId != null -> {
            val def = ui.models.firstOrNull { it.id == ui.activeModelId }
            "Default · ${def?.displayTitle() ?: "in use"}"
        }
        else -> "No model yet"
    }
}

@Composable
private fun FloorStatChip(
    label: String,
    value: String,
    face: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    goldStar: Boolean = false,
    pulse: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "statPulse")
    val rawScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulse) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "statScale",
    )
    val scale = if (pulse) rawScale else 1f
    Column(
        // Card stays static (crisp 1.5dp border); only the star + number pulse.
        modifier
            .clip(FloorPillShape)
            .background(face)
            .border(1.5.dp, KaiBorder, FloorPillShape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, color = KaiInkMuted, style = MaterialTheme.typography.labelSmall)
        Row(
            Modifier.graphicsLayer { scaleX = scale; scaleY = scale; clip = false },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (goldStar) {
                ShopStar(Modifier.size(16.dp))
            }
            Text(
                value,
                color = ink,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FloorAgentCard(
    agent: app.pixelbureau.data.Agent,
    sprites: SpriteStore,
    llmLabel: String,
) {
    val lookLabel = looks.firstOrNull { it.first == agent.spriteKey }?.second ?: agent.spriteKey
    val role = agent.personality.trim().ifBlank { "No instructions yet" }
    val statusLabel = agent.status.ifBlank { "idle" }
    val (statusFace, statusInk) = when (statusLabel) {
        "working" -> KaiButter to KaiAccentLo
        "walking" -> KaiSky to KaiInk
        "idle" -> KaiMint to ColorInkDeep
        else -> KaiCardNest to KaiInkMuted
    }
    val spriteBitmap = sprites.get(spriteFrame(agent.spriteKey, "idle", 0))
    val busy = statusLabel == "working" || statusLabel == "walking"
    val workingPulse = rememberInfiniteTransition(label = "agentPulse")
    val rawBadge by workingPulse.animateFloat(
        initialValue = 1f,
        targetValue = if (busy) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "badgeScale",
    )
    val badgeScale = if (busy) rawBadge else 1f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(FloorTileShape)
            .background(KaiCardNest)
            .border(1.5.dp, KaiBorder, FloorTileShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(KaiSky)
                    .border(1.5.dp, KaiBorderDeep, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (spriteBitmap != null) {
                    Image(
                        bitmap = spriteBitmap.asImageBitmap(),
                        contentDescription = agent.name,
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(36.dp),
                    )
                } else {
                    Text(lookLabel.take(1), color = KaiInk, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    agent.name,
                    color = KaiInk,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = 0.1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Look · $lookLabel",
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.scale(badgeScale)) {
                SoftBadge(label = statusLabel, face = statusFace, ink = statusInk)
            }
        }
        Text(
            role,
            color = KaiInkMuted,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        SoftBadge(
            label = "LLM · $llmLabel",
            face = if (llmLabel.startsWith("No model")) KaiPeach else KaiButter,
            ink = if (llmLabel.startsWith("No model")) KaiInk else KaiAccentLo,
        )
        if (agent.deskIndex != null) {
            Text(
                "Desk #${agent.deskIndex + 1}",
                color = KaiInkMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun builtInLabel(kind: String, deskName: String): String = when (kind) {
    "desk" -> "Work desks · $deskName"
    "chair" -> "Desk chairs"
    "conf_room" -> "Premium Startup"
    "plant", "plant2" -> "Potted plant"
    "server" -> "Server rack"
    "coffee" -> "Coffee station"
    "sofa" -> "Loveseat"
    "whiteboard" -> "Whiteboard"
    "bookshelf" -> "Bookshelf"
    "beanbag" -> "Bean bag"
    "fridge" -> "Snack fridge"
    "watercooler" -> "Water cooler"
    "foosball" -> "Foosball table"
    "trash" -> "Trash can"
    else -> kind.split('_').joinToString(" ") { p -> p.replaceFirstChar { it.uppercase() } }
}

/** One live taskforce under Tasks: title, member faces (≤5), status + progress. Tap → chat. */
@Composable
private fun FloorTaskforceRow(tf: TaskforceUi, ui: BureauUi, sprites: SpriteStore, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val members = tf.memberIds.take(TaskforceRules.MAX_MEMBERS)
        .mapNotNull { id -> ui.agents.firstOrNull { it.id == id } }
    val status = tf.data.status
    val (face, ink) = when (status) {
        TaskforceStatus.COMPLETE -> KaiMint to ColorInkDeep
        TaskforceStatus.BLOCKED -> KaiPeach to KaiInk
        TaskforceStatus.PAUSED -> KaiButter to KaiAccentLo
        else -> KaiSky to ColorInkDeep
    }
    val label = when (status) {
        TaskforceStatus.COMPLETE -> "Awaiting sign-off"
        TaskforceStatus.ACTIVE -> "In progress"
        else -> taskforceStatusLabel(status)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.5.dp, KaiBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "🤝 ${tf.data.title}",
                color = KaiInk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy((-4).dp), verticalAlignment = Alignment.CenterVertically) {
                for (agent in members) {
                    val bmp = sprites.get(spriteFrame(agent.spriteKey, "idle", 0))
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(KaiPage)
                            .border(1.dp, KaiBorder, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = agent.name,
                                modifier = Modifier.size(20.dp),
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    "${members.size}/${TaskforceRules.MAX_MEMBERS} · ${tf.data.progress.coerceIn(0, 100)}%",
                    color = KaiInkMuted,
                    fontSize = 11.sp,
                )
            }
        }
        Text(
            label,
            color = ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(face)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun FloorInventoryRow(
    title: String,
    detail: String,
    count: Int,
    badge: String,
    badgeFace: Color,
    badgeInk: Color,
    thumbKey: String?,
    thumbRes: Int?,
    sprites: SpriteStore,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(FloorTileShape)
            .background(KaiCardNest)
            .border(1.5.dp, KaiBorder, FloorTileShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KaiButter)
                .border(1.5.dp, KaiBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = thumbKey?.let { sprites.get(it) }
            when {
                bmp != null -> Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(32.dp),
                )
                thumbRes != null -> Image(
                    painter = painterResource(thumbRes),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp),
                )
                else -> Text("📦", fontSize = 16.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = KaiInk,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "×$count",
                color = KaiInk,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
            SoftBadge(label = badge, face = badgeFace, ink = badgeInk)
        }
    }
}

@Composable
private fun FloorToolRow(
    title: String,
    id: String,
    detail: String,
    enabled: Boolean,
) {
    val onPulse = rememberInfiniteTransition(label = "toolOn")
    val rawChip by onPulse.animateFloat(
        initialValue = 1f,
        targetValue = if (enabled) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "toolScale",
    )
    val chipScale = if (enabled) rawChip else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(FloorTileShape)
            .background(KaiCardNest)
            .border(1.5.dp, KaiBorder, FloorTileShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = KaiInk, fontWeight = FontWeight.SemiBold)
            Text(id, color = KaiInkMuted, style = MaterialTheme.typography.labelSmall)
            Text(
                detail,
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.scale(chipScale)) {
            SoftBadge(
                label = if (enabled) "On" else "Off",
                face = if (enabled) KaiMint else KaiRose,
                ink = if (enabled) ColorInkDeep else Brick,
            )
        }
    }
}


/** One compact Kairosoft-style inventory tile entry. */
private data class InvEntry(
    val key: String,
    val title: String,
    val count: Int,
    val builtIn: Boolean,
    val category: ShopCategory,
    val thumbKey: String?,
    val thumbRes: Int?,
)

private fun builtInCategory(kind: String): ShopCategory =
    ShopCatalog.items.firstOrNull { it.propKey == kind }?.category ?: when (kind) {
        "desk", "chair", "sofa", "beanbag" -> ShopCategory.Furniture
        "conf_room" -> ShopCategory.Conference
        "plant", "plant2", "plant_top" -> ShopCategory.Plants
        "coffee", "watercooler", "fridge", "vending" -> ShopCategory.Pantry
        "server", "printer", "arcade", "tv" -> ShopCategory.Electronics
        "bookshelf", "filing", "trash" -> ShopCategory.Storage
        else -> ShopCategory.Decor
    }

private const val INV_COLS = 5

@Composable
private fun InventoryGrid(
    ui: BureauUi,
    sprites: SpriteStore,
    ownedRows: List<Map.Entry<String, Int>>,
    placedByItem: Map<String, List<*>>,
    onRemove: (String) -> Unit = {},
) {
    var detail by remember { mutableStateOf<InvEntry?>(null) }
    detail?.let { e -> InvDetailDialog(e, sprites, onRemove = { onRemove(e.key.removePrefix("o:")); detail = null }, onDismiss = { detail = null }) }
    val deskName = if (ui.deskSprite == DESK_SPRITE_OAK) "Dream Starter" else "Premium desk"
    val confName = if (ui.confStyle == CONF_STYLE_OAK) "Dream Starter - Oakwood simple" else "Premium Startup"
    val confThumb = if (ui.confStyle == CONF_STYLE_OAK) R.drawable.shop_thumb_oak_conference else R.drawable.shop_thumb_premium_conference
    val entries = remember(ui.deskSprite, ui.confStyle, ownedRows) {
        val built = OfficeMap.builtInInventory().mapNotNull { (kind, n) ->
            // The only map "plant" is the conference corner pot, and Premium Startup does not show it.
            val count = if (ui.confStyle != CONF_STYLE_OAK && kind == "plant") {
                n - OfficeMap.props.count { it.kind == "plant" && it.x == OfficeMap.confPlant.x && it.y == OfficeMap.confPlant.y }
            } else {
                n
            }
            if (count <= 0) return@mapNotNull null
            InvEntry(
                key = "b:$kind",
                title = when (kind) {
                    "desk" -> deskName
                    "conf_room" -> confName
                    else -> builtInLabel(kind, deskName)
                },
                count = count,
                builtIn = true,
                category = builtInCategory(kind),
                thumbKey = when (kind) {
                    "conf_room" -> null
                    "desk" -> "props/${ui.deskSprite}"
                    else -> "props/$kind"
                },
                thumbRes = if (kind == "conf_room") confThumb else null,
            )
        }
        val bought = ownedRows.map { (id, n) ->
            val item = ShopCatalog.byId(id)
            InvEntry("o:$id", item?.name ?: id, n, false, item?.category ?: ShopCategory.Decor, item?.spriteKey, item?.thumbRes)
        }
        built + bought
    }
    var cat by remember { mutableStateOf(ShopCategory.All) }
    var expanded by remember { mutableStateOf(false) }
    val shown = entries.filter { cat == ShopCategory.All || it.category == cat }
    val builtTotal = entries.filter { it.builtIn }.sumOf { it.count }
    val boughtTotal = entries.filterNot { it.builtIn }.sumOf { it.count }

    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200, easing = FastOutSlowInEasing)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Summary header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InvStat("Items", "${builtTotal + boughtTotal}", KaiButter, Modifier.weight(1f))
            InvStat("Built-in", "$builtTotal", KaiMint, Modifier.weight(1f))
            InvStat("Bought", "$boughtTotal", KaiPeach, Modifier.weight(1f))
        }
        // Category chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ShopCategory.values().toList()) { c ->
                val n = if (c == ShopCategory.All) entries.size else entries.count { it.category == c }
                val sel = c == cat
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) KaiMint else KaiCardNest)
                        .border(1.5.dp, KaiBorder, RoundedCornerShape(50))
                        .clickable { cat = c; expanded = false }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Image(painterResource(c.iconRes), null, Modifier.size(14.dp))
                    Text(
                        (if (c == ShopCategory.All) "All" else c.label) + " $n",
                        color = if (sel) ColorInkDeep else KaiInk,
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            EmptyHint("Nothing here yet. Shop on Pazada to fill this shelf.")
        } else {
            val rows = shown.chunked(INV_COLS)
            val visible = if (expanded) rows else rows.take(2)
            for (row in visible) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (e in row) InvTile(e, sprites, Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { detail = e })
                    repeat(INV_COLS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (rows.size > 2) {
                Text(
                    if (expanded) "Show less ▲" else "See all ${shown.size} ▼",
                    color = KaiAccentLo,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(50))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun InvStat(label: String, value: String, face: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(face)
            .border(1.5.dp, KaiBorder, RoundedCornerShape(10.dp))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = KaiInkMuted, fontSize = 10.sp)
    }
}

@Composable
private fun InvTile(e: InvEntry, sprites: SpriteStore, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(KaiCardNest)
            .border(1.5.dp, KaiBorder, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(KaiButter),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = e.thumbKey?.let { sprites.get(it) }
            when {
                // Share of the tile, not a fixed dp. ~34dp on a normal phone; larger screens scale up.
                bmp != null -> Image(bmp.asImageBitmap(), e.title, Modifier.fillMaxSize(0.64f), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                e.thumbRes != null -> Image(painterResource(e.thumbRes), e.title, Modifier.fillMaxSize(0.64f), contentScale = ContentScale.Fit)
                else -> Text("📦", fontSize = 14.sp)
            }
            Text(
                "x${e.count}",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(KaiInk)
                    .padding(horizontal = 3.dp),
            )
        }
        Text(e.title, color = KaiInk, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 10.sp)
        Text(
            if (e.builtIn) "Built-in" else "Bought",
            color = if (e.builtIn) ColorInkDeep else KaiAccentLo,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (e.builtIn) KaiMint else KaiPeach)
                .padding(horizontal = 4.dp),
        )
    }
}

/** Tap an inventory tile: small Kairosoft card with "Remove from office" (bought items) + confirm. */
@Composable
private fun InvDetailDialog(e: InvEntry, sprites: SpriteStore, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val itemId = e.key.removePrefix("o:").removePrefix("b:")
    val item = if (e.builtIn) null else ShopCatalog.byId(itemId)
    val block = app.pixelbureau.logic.InventoryRules.blockReason(e.builtIn, itemId)
    var confirm by remember { mutableStateOf(false) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = KaiCard,
            title = { Text("Remove ${e.title}?", color = KaiInk, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    (if (e.count > 1) "One of your ${e.count} copies leaves the map. " else "It leaves the map and frees its spot. ") +
                        "You can rebuy it in Pazada.\n\nNo refund.",
                    color = KaiInk,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = false; onRemove() }) { Text("Remove", color = Color(0xFFC0392B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep", color = KaiInkMuted) } },
        )
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 340.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KaiCard)
                .border(2.dp, KaiBorder, RoundedCornerShape(18.dp)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(KaiButter).border(1.5.dp, KaiBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = e.thumbKey?.let { sprites.get(it) }
                when {
                    bmp != null -> Image(bmp.asImageBitmap(), e.title, Modifier.size(64.dp), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                    e.thumbRes != null -> Image(painterResource(e.thumbRes), e.title, Modifier.size(64.dp), contentScale = ContentScale.Fit)
                    else -> Text("📦", fontSize = 28.sp)
                }
            }
            Text(e.title, color = KaiInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                (if (e.builtIn) "Built-in" else "Bought") + " · x${e.count}" + (item?.let { " · ★${it.price}" } ?: ""),
                color = KaiInkMuted, fontSize = 12.sp,
            )
            item?.desc?.takeIf { it.isNotBlank() }?.let { Text(it, color = KaiInk, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            val enabled = block == null
            Text(
                "🗑 Remove from office", color = if (enabled) Color.White else KaiInkMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                    .background(if (enabled) Color(0xFFD9534F) else KaiCardNest)
                    .border(1.5.dp, if (enabled) Color(0xFFA33A36) else KaiBorder, RoundedCornerShape(50))
                    .clickable(enabled = enabled) { confirm = true }.padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (block != null) Text(block, color = KaiInkMuted, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            else Text("No refund", color = KaiInkMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDismiss) { Text("Close", color = KaiInkMuted) }
        }
    }
}

internal const val HERO_FACES = 4

/** Hero crew: hired (non-archived) agents in hire order (sortOrder). The count chip uses this same list. */
internal fun heroCrew(agents: List<Agent>): List<Agent> = agents.filter { !it.archived }.sortedBy { it.sortOrder }

/** Head crop of the agent's real idle sheet — the exact frame key the map draws ([spriteFrame]). */
@Composable
private fun HeroHead(spriteKey: String, sprites: SpriteStore, ready: Boolean) {
    val head = remember(spriteKey, ready) {
        if (!ready) null else sprites.get(app.pixelbureau.logic.spriteFrame(spriteKey, "idle", 0))?.takeIf { !it.isRecycled }?.let { b ->
            val side = minOf(b.width, (b.height * 0.48f).toInt()).coerceAtLeast(1)
            android.graphics.Bitmap.createBitmap(b, ((b.width - side) / 2).coerceAtLeast(0), 0, side, side)
        }
    }
    if (head != null) {
        Image(head.asImageBitmap(), null, Modifier.size(32.dp), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
    }
}
