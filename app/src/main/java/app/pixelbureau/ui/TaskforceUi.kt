package app.pixelbureau.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixelbureau.data.Agent
import app.pixelbureau.data.TaskforceMessage
import app.pixelbureau.logic.TaskforceRules
import app.pixelbureau.logic.TaskforceStatus
import app.pixelbureau.logic.decodeChecklist
import app.pixelbureau.sprites.SpriteStore
import kotlin.math.roundToInt

private val TfChipShape = RoundedCornerShape(10.dp)
private val TfBubbleShape = RoundedCornerShape(14.dp)
private val TfTeal = Color(0xFF3FA7A0)
private val TfTealDeep = Color(0xFF1F5E5A)
private val TfTile = Color(0xFF23413F)

internal fun taskforceStatusLabel(status: String): String = statusLabel(status)

private fun statusLabel(status: String): String = when (status) {
    TaskforceStatus.ACTIVE -> "In session"
    TaskforceStatus.BLOCKED -> "Waiting for you"
    TaskforceStatus.PAUSED -> "Paused"
    TaskforceStatus.COMPLETE -> "Done · needs sign-off"
    else -> status
}

/** Group chip for the chat selector: teal tile, "people" glyph, meeting title. */
@Composable
internal fun TaskforceChip(
    taskforce: TaskforceUi,
    selected: Boolean,
    speaking: Boolean,
    onClick: () -> Unit,
) {
    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val tile = if (selected) {
            Modifier
                .shadow(10.dp, TfChipShape, ambientColor = TfTeal, spotColor = TfTeal)
                .border(3.dp, TfTeal, TfChipShape)
                .clip(TfChipShape)
                .background(TfTealDeep)
        } else {
            Modifier
                .border(2.dp, TfTeal.copy(alpha = 0.6f), TfChipShape)
                .clip(TfChipShape)
                .background(TfTile)
        }
        Box(Modifier.fillMaxWidth()) {
            Column(
                tile
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(TfTeal.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("👥", fontSize = 22.sp)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    taskforce.data.title,
                    color = if (selected) TfTeal else Cream,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val dot = when {
                taskforce.data.status == TaskforceStatus.COMPLETE -> Color(0xFF7CD67C)
                taskforce.data.status != TaskforceStatus.ACTIVE -> Amber
                speaking -> TfTeal
                else -> null
            }
            if (dot != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(10.dp).clip(RoundedCornerShape(5.dp))
                        .background(dot).border(1.5.dp, Ink, RoundedCornerShape(5.dp)),
                )
            }
        }
        Box(Modifier.height(8.dp).width(16.dp).offset(y = (-1).dp))
    }
}

/**
 * Kairosoft-style "Set a Meeting" sheet opened by tapping the conference room:
 * painted glass-room header, sectioned field cards, agent card picker (sprite,
 * name, role line, ★ = finished tasks, ✓ badge), live seat counter (4 chairs +
 * 1 standing), and a pill Start button. Max [TaskforceRules.MAX_MEMBERS].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateTaskforceDialog(
    agents: List<Agent>,
    onDismiss: () -> Unit,
    onCreate: (String, String, List<String>, String?, Int) -> Unit,
    sprites: app.pixelbureau.sprites.SpriteStore? = null,
    doneCounts: Map<String, Int> = emptyMap(),
) {
    var title by remember { mutableStateOf("") }
    var objective by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(listOf<String>()) }
    var leadPick by remember { mutableStateOf<String?>(null) }
    val leadId = leadPick?.takeIf { it in picked } ?: picked.firstOrNull()
    var steps by remember { mutableStateOf(3) }
    val max = TaskforceRules.MAX_MEMBERS
    val canStart = title.isNotBlank() && picked.isNotEmpty()
    var shown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }
    val enter by androidx.compose.animation.core.animateFloatAsState(
        if (shown) 1f else 0.9f, androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 420f), label = "enter",
    )
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val shortScreen = conf.screenHeightDp < 720
    val wide = conf.screenWidthDp >= 600
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
        Column(
            Modifier
                .fillMaxWidth(if (wide) 0.7f else 0.94f)
                .widthIn(max = 640.dp)
                .heightIn(max = (conf.screenHeightDp * 0.9f).dp)
                .graphicsLayer { scaleX = enter; scaleY = enter; alpha = ((enter - 0.9f) * 10f).coerceIn(0f, 1f) }
                .clip(RoundedCornerShape(22.dp))
                .background(KaiPage)
                .border(2.dp, KaiBorder, RoundedCornerShape(22.dp)),
        ) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                MeetingHeader(onDismiss, if (shortScreen) 72.dp else 116.dp)
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MeetingFieldCard("📝", "Meeting title") {
                        OutlinedTextField(title, { title = it }, placeholder = { Text("e.g. Launch plan sync", color = KaiInkMuted) },
                            singleLine = true, colors = KaiFieldColors, modifier = Modifier.fillMaxWidth())
                    }
                    MeetingFieldCard("🎯", "Agenda / objective") {
                        OutlinedTextField(objective, { objective = it }, placeholder = { Text("What should the team deliver?", color = KaiInkMuted) },
                            minLines = if (shortScreen) 2 else 3, colors = KaiFieldColors, modifier = Modifier.fillMaxWidth())
                    }
                    MeetingFieldCard("👥", "Who attends · 👑 pick a lead") {
                        if (agents.isEmpty()) {
                            Text("Hire someone first.", color = Brick, fontSize = 12.sp)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (agent in agents) {
                                val index = picked.indexOf(agent.id)
                                AgentPickCard(
                                    agent = agent,
                                    sprites = sprites,
                                    stars = (doneCounts[agent.id] ?: 0).coerceIn(0, 5),
                                    selected = index >= 0,
                                    lead = agent.id == leadId,
                                    locked = index < 0 && picked.size >= max,
                                ) {
                                    picked = when {
                                        index >= 0 -> picked - agent.id
                                        picked.size >= max -> picked
                                        else -> picked + agent.id
                                    }
                                }
                            }
                        }
                        if (picked.size >= 2) {
                            Spacer(Modifier.height(8.dp))
                            Text("👑 Lead", color = KaiInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (pid in picked) {
                                    val a = agents.firstOrNull { it.id == pid } ?: continue
                                    val on = pid == leadId
                                    Text(
                                        (if (on) "👑 " else "") + a.name,
                                        color = KaiInk, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Color(0xFFFFE08A) else Color.White)
                                            .border(1.5.dp, if (on) Color(0xFFE2A23A) else KaiBorder, RoundedCornerShape(50))
                                            .clickable { leadPick = pid }.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        if (picked.size >= max) {
                            Spacer(Modifier.height(6.dp))
                            Text("Room is full: $max max (4 seated + 1 standing).", color = Brick, fontSize = 12.sp)
                        }
                    }
                }
            }
            // Pinned footer: always visible.
            Column(
                Modifier.fillMaxWidth().background(KaiCard)
                    .border(1.dp, KaiBorder.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeatCounter(picked.size, max)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Steps", color = KaiInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    androidx.compose.material3.Slider(
                        value = steps.toFloat(), onValueChange = { steps = it.toInt().coerceIn(1, 6) },
                        valueRange = 1f..6f, steps = 4,
                        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = TfTeal, activeTrackColor = TfTeal),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(32.dp),
                    )
                    Text("$steps ${if (steps == 1) "step" else "steps"} · +${app.pixelbureau.logic.taskforceReward(steps)}★", color = KaiInk, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFE08A)).padding(horizontal = 9.dp, vertical = 2.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = KaiInkMuted) }
                    Spacer(Modifier.weight(1f))
                    StartPill(enabled = canStart) { onCreate(title, objective, picked, leadId, steps) }
                }
            }
        }
        }
    }
}

/** Painted glass conference-room banner with the title. */
@Composable
private fun MeetingHeader(onClose: () -> Unit, height: androidx.compose.ui.unit.Dp = 116.dp) {
    val compact = height < 100.dp
    Box(Modifier.fillMaxWidth().height(height)) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF9FDCF2), Color(0xFFDDF3F9))))
            // Floor.
            drawRect(Color(0xFFD9925B), topLeft = Offset(0f, h * 0.74f), size = androidx.compose.ui.geometry.Size(w, h * 0.26f))
            for (i in 0..12) drawLine(Color(0xFFB9743F), Offset(i * w / 12f, h * 0.74f), Offset(i * w / 12f - 18f, h), 2f)
            // Glass wall panes with black mullions.
            val top = h * 0.12f; val bot = h * 0.74f; val n = 6
            drawRect(Color(0x553FA7C8), topLeft = Offset(w * 0.04f, top), size = androidx.compose.ui.geometry.Size(w * 0.92f, bot - top))
            for (i in 0..n) {
                val x = w * 0.04f + i * (w * 0.92f / n)
                drawLine(Color(0xFF1C1F24), Offset(x, top), Offset(x, bot), 5f)
                if (i < n) drawLine(Color(0x99FFFFFF), Offset(x + 14f, bot - 10f), Offset(x + 44f, top + 10f), 4f)
            }
            drawLine(Color(0xFF1C1F24), Offset(w * 0.04f, top), Offset(w * 0.96f, top), 6f)
            drawLine(Color(0xFF1C1F24), Offset(w * 0.04f, bot), Offset(w * 0.96f, bot), 6f)
            drawLine(Color(0xFF1C1F24), Offset(w * 0.04f, (top + bot) / 2f), Offset(w * 0.96f, (top + bot) / 2f), 3f)
            // Table + chairs silhouette.
            drawRoundRect(Color(0xFFA8703F), topLeft = Offset(w * 0.30f, h * 0.66f), size = androidx.compose.ui.geometry.Size(w * 0.40f, h * 0.1f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f))
            for (k in 0 until 4) {
                val cx = w * (0.34f + k * 0.1f)
                drawRoundRect(Color(0xFF2F343B), topLeft = Offset(cx, h * 0.52f), size = androidx.compose.ui.geometry.Size(w * 0.05f, h * 0.15f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f))
            }
        }
        Text(
            "Set a Meeting",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (compact) 19.sp else 24.sp,
            style = MaterialTheme.typography.titleLarge.copy(
                shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF1F5E5A), Offset(0f, 3f), 0f),
            ),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 14.dp),
        )
        Text(
            "Glass room · 4 chairs + 1 standing",
            color = Color(0xFF1F5E5A),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = if (compact) 38.dp else 48.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xCCFFFFFF)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
            "✕",
            color = KaiInk,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                .clip(CircleShape).background(Color(0xCCFFFFFF))
                .clickable(onClick = onClose).padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MeetingFieldCard(icon: String, label: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.5.dp, KaiBorder, RoundedCornerShape(16.dp)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Text(label, color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** "3/5 seats" with 4 chair pips + 1 standing pip, filled as members are picked. */
@Composable
private fun SeatCounter(count: Int, max: Int) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "seat")
    val glow by pulse.animateFloat(0.85f, 1.1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "glow")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (i in 0 until max) {
            val filled = i < count
            val standing = i == max - 1
            Box(
                Modifier.size(26.dp)
                    .graphicsLayer { if (filled && i == count - 1) { scaleX = glow; scaleY = glow } }
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (filled) TfTeal else Color(0xFFEDE4D3))
                    .border(1.5.dp, if (filled) TfTealDeep else KaiBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(if (standing) "🧍" else "🪑", fontSize = 13.sp) }
        }
        Spacer(Modifier.width(6.dp))
        Text("$count/$max seats", color = if (count >= max) Brick else TfTealDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun AgentPickCard(
    agent: Agent,
    sprites: app.pixelbureau.sprites.SpriteStore?,
    stars: Int,
    selected: Boolean,
    lead: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val scale by androidx.compose.animation.core.animateFloatAsState(if (selected) 1.04f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.5f), label = "pick")
    Box(Modifier.width(96.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (locked) 0.45f else 1f }) {
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(if (selected) KaiButter else Color.White)
                .border(if (selected) 2.5.dp else 1.5.dp, if (selected) KaiAccent else KaiBorder, shape)
                .clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val bmp = sprites?.get(app.pixelbureau.logic.spriteFrame(agent.spriteKey, "idle", 0))
            Box(Modifier.size(44.dp).clip(CircleShape).background(KaiPage), contentAlignment = Alignment.Center) {
                if (bmp != null) {
                    androidx.compose.foundation.Image(bmp.asImageBitmap(), agent.name, Modifier.size(40.dp),
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
                } else {
                    Text(agent.name.take(1), color = KaiInk, fontWeight = FontWeight.Bold)
                }
            }
            Text(if (lead) "★ ${agent.name}" else agent.name, color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(roleLine(agent), color = KaiInkMuted, fontSize = 10.sp, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("★".repeat(stars) + "☆".repeat(5 - stars), color = Color(0xFFE0A21B), fontSize = 10.sp)
        }
        if (selected) {
            Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp).clip(CircleShape).background(KaiMintDeep)
                .border(1.5.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Short role line from the agent's duties (first clause). */
private fun roleLine(agent: Agent): String =
    agent.duties.lineSequence().map { it.trim().trimStart('-', '*', '•', ' ') }.firstOrNull { it.isNotBlank() }
        ?.split('.', ',', ';')?.first()?.take(22) ?: "Teammate"

/** Primary pill with a press squash. */
@Composable
private fun StartPill(enabled: Boolean, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.93f else 1f, label = "press")
    Box(
        Modifier.graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(50))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF5FD39A), Color(0xFF2E9C68))))
            .border(2.dp, Color(0xFF1F6E49), RoundedCornerShape(50))
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) { Text("Start Taskforce", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
}

/** The taskforce group thread: header with progress/checklist, messages with quote-reply, composer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TaskforcePanel(
    taskforce: TaskforceUi,
    agents: List<Agent>,
    messages: List<TaskforceMessage>,
    speaking: Set<String>,
    quote: TaskforceMessage?,
    draft: String,
    sprites: SpriteStore,
    ready: Boolean,
    onDraft: (String) -> Unit,
    onQuote: (TaskforceMessage?) -> Unit,
    onSend: () -> Unit,
    onSignOff: () -> Unit,
    onConclude: () -> Unit = onSignOff,
    onDisband: () -> Unit,
    modifier: Modifier = Modifier,
    failures: Map<String, String> = emptyMap(),
    onResolveFailure: (agentId: String, retry: Boolean) -> Unit = { _, _ -> },
    debate: Pair<Int, Int>? = null,
    onStopDebate: () -> Unit = {},
    collapsed: Boolean = false,
    headerMode: Int = 0,
    onHeaderMode: (Int) -> Unit = {},
) {
    val tf = taskforce.data
    val byId = agents.associateBy { it.id }
    var showChecklist by remember(tf.id) { mutableStateOf(false) }
    var confirmDisband by remember(tf.id) { mutableStateOf(false) }
    val checklist = decodeChecklist(tf.checklist)
    Column(modifier.animateContentSize(androidx.compose.animation.core.tween(220))) {
        // Banner: 0 expanded → 1 slim title row → 2 hidden (floating pill restores).
        val swipe = Modifier.pointerInput(headerMode) {
            var acc = 0f
            detectVerticalDragGestures(
                onDragStart = { acc = 0f },
                onDragEnd = {
                    if (acc < -40f && headerMode < 2) onHeaderMode(headerMode + 1)
                    else if (acc > 40f && headerMode > 0) onHeaderMode(headerMode - 1)
                },
            ) { change, dy -> change.consume(); acc += dy }
        }
        if (!collapsed && headerMode == 1) Row(
            Modifier.fillMaxWidth().background(TfTealDeep.copy(alpha = 0.55f)).then(swipe)
                .clickable { onHeaderMode(0) }.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (tf.status == TaskforceStatus.ACTIVE) Color(0xFF7CD67C) else Amber))
            Spacer(Modifier.width(8.dp))
            Text(tf.title, color = Cream, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            PixelChip("Expand banner", onClick = { onHeaderMode(0) }) { ChevronGlyph(up = false) }
        } else if (!collapsed && headerMode == 0) Column(
            Modifier.fillMaxWidth().background(TfTealDeep.copy(alpha = 0.55f)).then(swipe).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            // Title first: full width, up to 2 lines.
            Text(tf.title, color = Cream, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val names = taskforce.memberIds.mapNotNull { byId[it]?.name }
            Text(
                "👥 ${statusLabel(tf.status)} · ${names.joinToString()}",
                color = CreamDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // Second row: progress + compact control cluster.
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable { showChecklist = !showChecklist }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = { tf.progress / 100f },
                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (tf.status == TaskforceStatus.COMPLETE) Color(0xFF7CD67C) else TfTeal,
                        trackColor = ChatInputWell,
                    )
                    Text(
                        "  ${tf.progress}%" + if (checklist.isNotEmpty()) " · ${checklist.count { it.done }}/${checklist.size} ${if (showChecklist) "▴" else "▾"}" else "",
                        color = CreamDim, fontSize = 11.sp, maxLines = 1,
                    )
                }
                if (TaskforceRules.canSignOff(tf.status)) {
                    Text(
                        "✓ Sign off", color = Color(0xFF14361A), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF7CD67C))
                            .clickable(onClick = onSignOff).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Box {
                    var menu by remember { mutableStateOf(false) }
                    PixelChip("More options", onClick = { menu = true }) { MoreGlyph() }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = KaiCard) {
                        DropdownMenuItem(text = { Text("Hide banner", color = KaiInk) }, onClick = { menu = false; onHeaderMode(2) })
                        DropdownMenuItem(text = { Text("Disband…", color = Brick) }, onClick = { menu = false; confirmDisband = true })
                    }
                }
                PixelChip("Collapse banner", onClick = { onHeaderMode(1) }) { ChevronGlyph(up = true) }
            }
            if (showChecklist && checklist.isNotEmpty()) {
                Column(Modifier.padding(top = 4.dp)) {
                    for (item in checklist) {
                        Text(
                            (if (item.done) "☑ " else "☐ ") + item.text,
                            color = if (item.done) CreamDim else Cream, fontSize = 12.sp,
                        )
                    }
                }
            }
            if (tf.status == TaskforceStatus.COMPLETE) {
                Text("✅ The team says the objective is done. Review it, then Sign off — or reply to send them back to work.", color = Color(0xFF9BE39B), fontSize = 11.sp)
            } else if (tf.note.isNotBlank()) {
                Text(tf.note, color = Amber, fontSize = 11.sp)
            }
        }
        // Thread
        val listState = rememberLazyListState()
        LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length, speaking.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex, scrollOffset = 100_000)
        }
        if (!collapsed && headerMode == 2) {
            // Hidden banner: a small floating pill over the thread restores it.
            Box(Modifier.fillMaxWidth().height(0.dp).zIndex(2f)) {
                Row(
                    Modifier.align(Alignment.TopEnd).wrapContentHeight(unbounded = true, align = Alignment.Top)
                        .padding(top = 8.dp, end = 10.dp).shadow(4.dp, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50)).background(TfTealDeep).border(1.dp, TfTeal, RoundedCornerShape(50))
                        .clickable { onHeaderMode(0) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Taskforce", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = Cream, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (!collapsed) LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().background(ChatPanelDeep.copy(alpha = 0.55f)),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val lastConclusionId = messages.lastOrNull { it.speakerId == tf.leadId && isConclusionText(it.text) }?.id
            items(messages, key = { it.id }) { m ->
                if (m.speakerId.startsWith(ModelErrorPrefix)) {
                    val aid = m.speakerId.removePrefix(ModelErrorPrefix)
                    ModelErrorCard(m.text, pending = aid in failures, onRetry = { onResolveFailure(aid, true) }, onSkip = { onResolveFailure(aid, false) })
                    return@items
                }
                val memberNames = taskforce.memberIds.mapNotNull { byId[it]?.name }
                TaskforceBubble(
                    mentions = app.pixelbureau.logic.Mentions.ranges(m.text, memberNames).map { m.text.substring(it.first, it.last + 1) }.distinct(),
                    message = m,
                    agent = byId[m.speakerId],
                    sprites = sprites,
                    ready = ready,
                    working = m.speakerId in speaking && m.id == messages.lastOrNull { it.speakerId == m.speakerId }?.id,
                    onQuote = { onQuote(m) },
                    footer = if (m.id != lastConclusionId) null else {
                        {
                            val reward = app.pixelbureau.logic.taskforceReward(tf.steps)
                            if (TaskforceStatus.isOpen(tf.status)) {
                                Text(
                                    "✓ Conclude meeting", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF3FA34D))
                                        .border(1.5.dp, Color(0xFF2B7A36), RoundedCornerShape(50))
                                        .clickable(onClick = onConclude).padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            } else if (tf.status == TaskforceStatus.SIGNED_OFF) {
                                Text(
                                    "Meeting concluded · +$reward★", color = Color(0xFF7A4A00), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFE08A)).padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    },
                )
            }
            val waiting = speaking.filter { id -> messages.none { it.speakerId == id } || messages.last().speakerId != id }
            if (waiting.isNotEmpty()) {
                item(key = "tf-typing") {
                    Text(
                        waiting.mapNotNull { byId[it]?.name }.joinToString() + " typing…",
                        color = TfTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        // Composer
        Column(Modifier.fillMaxWidth().background(ChatPanel).padding(8.dp)) {
            if (debate != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF3B2F5C)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("💬 Discussing… turn ${debate.first}/${debate.second}", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Stop", color = Brick, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onStopDebate).padding(4.dp))
                }
            }
            val typingAt = app.pixelbureau.logic.Mentions.typing(draft)
            val crew = taskforce.memberIds.mapNotNull { byId[it] }
            if (typingAt != null) {
                val matches = crew.filter { it.name.startsWith(typingAt, ignoreCase = true) }
                if (matches.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (a in matches) {
                            Row(
                                Modifier.clip(RoundedCornerShape(16.dp)).background(ChatInputWell).border(1.dp, TfTeal, RoundedCornerShape(16.dp))
                                    .clickable { onDraft(app.pixelbureau.logic.Mentions.complete(draft, a.name)) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AgentFace(spriteKey = a.spriteKey, sprites = sprites, ready = ready, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(a.name, color = Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            app.pixelbureau.logic.Slash.typing(draft)?.let { pre ->
                val cmds = app.pixelbureau.logic.Slash.suggest(pre, debate != null, TaskforceRules.canSignOff(tf.status), crew.size)
                if (cmds.isNotEmpty()) Column(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp)).background(KaiCard)
                        .border(2.dp, KaiBorder, RoundedCornerShape(10.dp)).padding(4.dp),
                ) {
                    for (c in cmds) Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { onDraft("/" + c.word + if (c.usage.contains('<')) " " else "") }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.icon, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.usage, color = KaiInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.desc, color = KaiInk.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (quote != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(8.dp))
                        .background(ChatInputWell).border(1.dp, TfTeal, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Replying to ${quote.speakerName}", color = TfTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(quote.text, color = CreamDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(
                        Icons.Filled.Close, contentDescription = "Cancel quote", tint = CreamDim,
                        modifier = Modifier.size(18.dp).clickable { onQuote(null) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(ChatInputWell)
                        .border(1.5.dp, ChatBorderInner, RoundedCornerShape(22.dp)).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (draft.isEmpty()) {
                        Text("Message the team · @ to mention", color = CreamDim.copy(alpha = 0.55f), fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraft,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Cream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif),
                        cursorBrush = SolidColor(TfTeal),
                        maxLines = 3,
                    )
                }
                val canSend = draft.isNotBlank()
                PixelRaisedButton(
                    face = ChatBtnAmber, highlight = ChatBtnAmberHi, shadow = ChatBtnAmberLo,
                    enabled = canSend, onClick = onSend, modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (canSend) Ink else Ink.copy(alpha = 0.45f), modifier = Modifier.size(22.dp))
                }
            }
        }
    }
    if (confirmDisband) {
        AlertDialog(
            onDismissRequest = { confirmDisband = false },
            containerColor = KaiCard,
            title = { Text("Disband “${tf.title}”?", color = KaiInk, fontWeight = FontWeight.SemiBold) },
            text = { Text("The team stops working and leaves the conference room. The thread is archived.", color = KaiInkMuted) },
            confirmButton = {
                TextButton(onClick = { confirmDisband = false; onDisband() }) { Text("Disband", color = Brick, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDisband = false }) { Text("Cancel", color = KaiInkMuted) } },
        )
    }
}

/** One thread line. Long-press or swipe right to quote-reply it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskforceBubble(
    message: TaskforceMessage,
    agent: Agent?,
    sprites: SpriteStore,
    ready: Boolean,
    working: Boolean,
    onQuote: () -> Unit,
    mentions: List<String> = emptyList(),
    footer: (@Composable () -> Unit)? = null,
) {
    val conclusion = isConclusionText(message.text)
    var drag by remember(message.id) { mutableFloatStateOf(0f) }
    val gestures = Modifier
        .offset { IntOffset(drag.roundToInt(), 0) }
        .pointerInput(message.id) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (drag > 120f) onQuote()
                    drag = 0f
                },
                onDragCancel = { drag = 0f },
            ) { change, amount ->
                change.consume()
                drag = (drag + amount).coerceIn(0f, 200f)
            }
        }
        .combinedClickable(onClick = {}, onLongClick = onQuote)
    val system = message.speakerId == "system"
    val mine = message.speakerId == "user"
    if (system) {
        Text(
            message.text,
            color = Amber, fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        return
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine && agent != null) {
            AgentFace(spriteKey = agent.spriteKey, sprites = sprites, ready = ready, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(6.dp))
        }
        Column(
            gestures
                .widthIn(max = 320.dp)
                .clip(TfBubbleShape)
                .background(if (mine) ChatBubbleUser else if (conclusion) Color(0xFF2E3A2A) else ChatBubbleAgent)
                .border(
                    if (conclusion) 2.5.dp else 1.5.dp,
                    when {
                        conclusion -> Color(0xFFFFD45C)
                        mentions.isNotEmpty() -> TfTeal
                        mine -> ChatBtnAmberLo
                        else -> ChatBubbleAgentBorder
                    },
                    TfBubbleShape,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (!mine) {
                Text(
                    if (conclusion) "📋 Conclusion · ${message.speakerName}" else message.speakerName,
                    color = if (conclusion) Color(0xFFFFD45C) else TfTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            }
            if (mentions.isNotEmpty()) {
                Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (tag in mentions) {
                        Text(
                            tag, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(TfTeal).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            if (message.quoteText != null) {
                Row(
                    Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.18f)).padding(6.dp),
                ) {
                    Box(Modifier.width(3.dp).height(30.dp).background(TfTeal))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(message.quoteSpeaker ?: "", color = if (mine) Ink else TfTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            message.quoteText, color = if (mine) Ink.copy(alpha = 0.75f) else CreamDim,
                            fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (message.text.isNotBlank()) {
                ChatMarkdownContent(text = message.text, onAgentBubble = !mine, modifier = Modifier.fillMaxWidth())
            }
            if (working) {
                ReplyWorking(label = null, modifier = Modifier.padding(top = 4.dp))
            }
            if (footer != null) {
                Spacer(Modifier.height(8.dp))
                footer()
            }
        }
    }
}

internal fun isConclusionText(text: String): Boolean =
    text.trimStart().removePrefix("#").trimStart('#', ' ').let { it.startsWith("**Conclusion**") || it.startsWith("Conclusion") }


/** "<Agent> couldn't reach <model>" with Retry (re-run just that turn) and Skip. */
@Composable
private fun ModelErrorCard(text: String, pending: Boolean, onRetry: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color(0xFF3A2A22))
            .border(1.5.dp, Color(0xFFD9824A), RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🔌 $text", color = Color(0xFFFFD9B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (pending) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Retry", color = Color(0xFF1C1F24), fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFB45C)).clickable(onClick = onRetry)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
                Text(
                    "Skip", color = Color(0xFFFFD9B8), fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFD9824A), RoundedCornerShape(8.dp))
                        .clickable(onClick = onSkip).padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 48dp touch target for banner controls. */
@Composable
private fun BannerButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick, onClickLabel = label)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Eye with a slash (no extended icon set). */
@Composable
private fun EyeOffGlyph() {
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val w = size.width; val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f)
        drawOval(Cream, topLeft = Offset(w * 0.08f, h * 0.28f), size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.44f), style = stroke)
        drawCircle(Cream, radius = w * 0.12f, center = Offset(w / 2f, h / 2f))
        drawLine(TfTealDeep, Offset(w * 0.14f, h * 0.14f), Offset(w * 0.86f, h * 0.86f), strokeWidth = w * 0.2f)
        drawLine(Cream, Offset(w * 0.14f, h * 0.14f), Offset(w * 0.86f, h * 0.86f), strokeWidth = w * 0.09f)
    }
}

/** Kairosoft-style 28dp soft-tinted circular chip (20dp glyph), ~40dp touch area, press bounce. */
@Composable
private fun PixelChip(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.86f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 700f), label = "chipPress",
    )
    Box(
        Modifier.size(40.dp).clickable(interactionSource = source, indication = null, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(28.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape).background(Color(0x33FFF1DC)).border(1.dp, Color(0x66FFF1DC), CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** Pixel-stepped chevron (20dp). */
@Composable
private fun ChevronGlyph(up: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val u = (size.width / 10f).let { kotlin.math.floor(it) }.coerceAtLeast(1f)
        val ox = (size.width - u * 9f) / 2f; val oy = (size.height - u * 5f) / 2f
        for (k in 0 until 5) {
            val row = if (up) 4 - k else k
            // Two pixel blocks per step, mirrored: a crisp V / ^ shape.
            drawRect(Cream, Offset(kotlin.math.round(ox + k * u), kotlin.math.round(oy + row * u)), androidx.compose.ui.geometry.Size(u * 1.5f, u * 1.5f))
            drawRect(Cream, Offset(kotlin.math.round(ox + (8 - k) * u), kotlin.math.round(oy + row * u)), androidx.compose.ui.geometry.Size(u * 1.5f, u * 1.5f))
        }
    }
}

/** Pixel "⋯" (three square dots). */
@Composable
private fun MoreGlyph() {
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val d = kotlin.math.floor(size.width / 7f).coerceAtLeast(2f)
        val y = kotlin.math.round(size.height / 2f - d / 2f)
        for (i in 0 until 3) drawRect(Cream, Offset(kotlin.math.round(size.width / 2f + (i - 1) * d * 2f - d / 2f), y), androidx.compose.ui.geometry.Size(d, d))
    }
}

/** Current step (1-based) of [steps]: from the live discussion turn when there is one. */
internal fun meetingStep(steps: Int, members: Int, turn: Pair<Int, Int>?, status: String): Int {
    val n = steps.coerceIn(1, 6)
    if (status == TaskforceStatus.COMPLETE) return n
    if (turn == null) return 1
    val speakers = (members - 1).coerceAtLeast(1)
    return ((turn.first - 1) / speakers + 1).coerceIn(1, n)
}

/** Tap on a live meeting (room or busy agent): soft Kairosoft-modern summary card. */
@Composable
internal fun MeetingInfoDialog(
    taskforce: TaskforceUi,
    agents: List<Agent>,
    sprites: app.pixelbureau.sprites.SpriteStore,
    ready: Boolean,
    discussTurn: Pair<Int, Int>?,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tf = taskforce.data
    val byId = agents.associateBy { it.id }
    val crew = taskforce.memberIds.mapNotNull { byId[it] }
    val step = meetingStep(tf.steps, crew.size, discussTurn, tf.status)
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = true, usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth(0.88f).clip(RoundedCornerShape(20.dp)).background(KaiCard)
                .border(2.dp, KaiBorder, RoundedCornerShape(20.dp)),
        ) {
            Row(
                Modifier.fillMaxWidth().background(TfTeal.copy(alpha = 0.18f)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("👥", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("In a meeting", color = KaiInk.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(tf.title, color = KaiInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (a in crew) {
                        val lead = a.id == tf.leadId
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                            Text(if (lead) "👑" else " ", fontSize = 12.sp)
                            Box(
                                Modifier.size(46.dp).clip(CircleShape).background(Color.White)
                                    .border(2.dp, if (lead) Color(0xFFE2A23A) else KaiBorder, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { AgentFace(spriteKey = a.spriteKey, sprites = sprites, ready = ready, modifier = Modifier.size(36.dp)) }
                            Text(a.name, color = KaiInk, fontSize = 11.sp, fontWeight = if (lead) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Step $step/${tf.steps}", color = KaiInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { step / tf.steps.coerceAtLeast(1).toFloat() },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = TfTeal, trackColor = KaiBorder.copy(alpha = 0.35f),
                        )
                    }
                    Text(
                        "+${app.pixelbureau.logic.taskforceReward(tf.steps)}★", color = Color(0xFF7A4A00), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFE08A))
                            .border(1.5.dp, Color(0xFFE2A23A), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Close", color = KaiInkMuted) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Open taskforce chat", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF3FA34D))
                            .border(2.dp, Color(0xFF2B7A36), RoundedCornerShape(50))
                            .clickable(onClick = onOpenChat).padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
