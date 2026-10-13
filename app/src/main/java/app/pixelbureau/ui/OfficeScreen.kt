package app.pixelbureau.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pixelbureau.R
import app.pixelbureau.PixelBureauApp
import app.pixelbureau.data.Agent
import app.pixelbureau.data.Task
import app.pixelbureau.logic.isPlaceholderReply
import app.pixelbureau.logic.isScratchpad
import app.pixelbureau.logic.isSearchPromise
import app.pixelbureau.logic.isSearchResultDump
import app.pixelbureau.logic.replyWorkLabel
import app.pixelbureau.logic.ChatsPerStar
import app.pixelbureau.logic.officeClock
import app.pixelbureau.logic.taskOrderBody
import app.pixelbureau.sprites.SpriteStore
import java.util.UUID
import kotlinx.coroutines.delay

private val ChipShape = RoundedCornerShape(10.dp)
private val ChatPanelShape = RoundedCornerShape(14.dp)
private val BubbleShapeUser = RoundedCornerShape(18.dp)
private val BubbleShapeAgent = RoundedCornerShape(14.dp)
private val InputPillShape = RoundedCornerShape(22.dp)
private val RaisedBtnShape = RoundedCornerShape(10.dp)
private val KaiMenuShape = RoundedCornerShape(16.dp)
private val KaiMenuIconShape = RoundedCornerShape(10.dp)

private val TriangleDown = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width / 2f, size.height)
    close()
}

private val TriangleLeft = GenericShape { size, _ ->
    moveTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfficeScreen(model: BureauViewModel, modifier: Modifier = Modifier, onOpenBureau: () -> Unit = {}) {
    val ui by model.ui.collectAsStateWithLifecycle()
    var taskSheet by remember { mutableStateOf<Pair<String, String>?>(null) }
    val active = ui.agents.filter { !it.archived }
    val selected = active.firstOrNull { it.id == ui.selectedId }
    val sprites = (LocalContext.current.applicationContext as PixelBureauApp).container.sprites
    taskSheet?.let { (t0, b0) ->
        val who = selected
        if (who == null) taskSheet = null else ChatTaskSheet(
            agent = who, sprites = sprites, ready = ui.spritesReady, title0 = t0, brief0 = b0,
            onDismiss = { taskSheet = null },
            onAssign = { t, b -> taskSheet = null; model.assignTask(t, b, who.id) },
        )
    }
    var chatFrac by remember {
        mutableFloatStateOf(ui.chatPanelFraction)
    }
    var officeHeightPx by remember { mutableFloatStateOf(1f) }
    // Keep local drag state in sync when prefs reload (e.g. cold start).
    LaunchedEffect(ui.chatPanelFraction) {
        if (kotlin.math.abs(chatFrac - ui.chatPanelFraction) > 0.001f) {
            chatFrac = ui.chatPanelFraction
        }
    }
    // Map weight tracks 1 - chatFrac so MAX=1.0 fully hides the map.
    // Compose weight() requires > 0, so omit the viewport at ~full chat.
    // Collapsed: only the handle + input bar stay; the map takes the rest.
    val collapsed = chatFrac < app.pixelbureau.data.SettingsStore.COLLAPSE_SNAP_FRACTION
    val mapWeight = if (collapsed) 1f else (1f - chatFrac).coerceAtLeast(0f)
    val chatWeight = chatFrac.coerceIn(
        app.pixelbureau.data.SettingsStore.MIN_CHAT_FRACTION,
        app.pixelbureau.data.SettingsStore.MAX_CHAT_FRACTION,
    )
    val showMap = mapWeight > 0.001f
    var retryTask by remember { mutableStateOf<Task?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .onSizeChanged { officeHeightPx = it.height.toFloat().coerceAtLeast(1f) },
    ) {
        OfficeHeader(
            ui.bureau?.day ?: 1,
            ui.bureau?.reputation ?: 0,
            ui.bureau?.chatCredit ?: 0,
            ui.banner,
        )
        // Stay in the composition when the panel covers the floor, so zoom and
        // pan survive. A shorter map clips that framing instead of refitting.
        OfficeViewport(
            model,
            if (showMap) {
                Modifier.weight(mapWeight).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth().height(0.dp)
            },
        )

        var confirmClear by remember(selected?.id) { mutableStateOf(false) }
        var chatMenuOpen by remember(selected?.id) { mutableStateOf(false) }
        val canClear = selected != null && (ui.messages.isNotEmpty() || ui.streams[selected.id] != null)
        val stream = selected?.let { ui.streams[it.id] }
        val context = LocalContext.current

        fun resolveDisplayName(uri: Uri): String {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
            return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "attachment"
        }

        val pickFiles = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            model.addPendingAttachments(
                uris.map { uri ->
                    PendingAttachment(
                        id = UUID.randomUUID().toString(),
                        uri = uri.toString(),
                        name = resolveDisplayName(uri),
                        kind = "file",
                    )
                },
            )
        }
        val pickImages = rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
        ) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            model.addPendingAttachments(
                uris.map { uri ->
                    PendingAttachment(
                        id = UUID.randomUUID().toString(),
                        uri = uri.toString(),
                        name = resolveDisplayName(uri),
                        kind = "image",
                    )
                },
            )
        }

        // Agent selector chips — square tiles with face + name; selected gets amber glow + pointer.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Taskforces first (leftmost): group chats for team meetings.
                for (tf in ui.taskforces) {
                    TaskforceChip(
                        taskforce = tf,
                        selected = tf.data.id == ui.selectedTaskforceId,
                        speaking = ui.taskforceSpeaking[tf.data.id].orEmpty().isNotEmpty(),
                        onClick = { model.selectTaskforce(tf.data.id) },
                    )
                }
                for (agent in active) {
                    val on = agent.id == ui.selectedId && ui.selectedTaskforceId == null
                    val bringIntoView = remember(agent.id) { BringIntoViewRequester() }
                    LaunchedEffect(ui.selectedId) {
                        if (ui.selectedId == agent.id) {
                            bringIntoView.bringIntoView()
                        }
                    }
                    AgentChip(
                        agent = agent,
                        selected = on,
                        sprites = sprites,
                        ready = ui.spritesReady,
                        celebrating = agentShowsTaskDone(
                            agent.id,
                            ui.tasks.map { TaskNotice(it.id, it.agentId, it.status, it.createdAt) },
                            ui.dismissedTaskDone,
                            ui.replyDoneIds,
                        ),
                        modifier = Modifier.bringIntoViewRequester(bringIntoView),
                        busy = meetingTaskforceFor(ui.taskforces, agent.id) != null,
                        onClick = { model.select(agent.id) },
                    )
                }
            }
        }

        // Chat panel: dark charcoal with layered brown border + input bar.
        // Top drag handle resizes height (persisted as chat_panel_fraction).
        Column(
            Modifier
                .then(if (collapsed) Modifier else Modifier.weight(if (showMap) chatWeight else 1f))
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .shadow(6.dp, ChatPanelShape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.55f))
                .border(3.dp, ChatBorderOuter, ChatPanelShape)
                .clip(ChatPanelShape)
                .background(ChatPanel)
                .border(1.5.dp, ChatBorderInner, ChatPanelShape),
        ) {
            // New-reply badge while collapsed: count arrivals since collapsing.
            val replyCount = if (ui.selectedTaskforceId != null) ui.taskforceMessages.count { it.speakerId != "user" }
                else ui.messages.count { it.role == "assistant" }
            var seenAtCollapse by remember { mutableIntStateOf(replyCount) }
            LaunchedEffect(collapsed, ui.selectedId, ui.selectedTaskforceId) { if (collapsed) seenAtCollapse = replyCount }
            val unseen = if (collapsed) (replyCount - seenAtCollapse).coerceAtLeast(0) else 0
            ChatResizeHandle(
                badge = unseen,
                onDrag = { deltaPx ->
                    // Drag up (negative dy) grows chat; scale against ~half the screen.
                    val span = (officeHeightPx * 0.55f).coerceAtLeast(400f)
                    val next = (chatFrac - deltaPx / span).coerceIn(
                        app.pixelbureau.data.SettingsStore.MIN_CHAT_FRACTION,
                        app.pixelbureau.data.SettingsStore.MAX_CHAT_FRACTION,
                    )
                    chatFrac = next
                },
                onDragEnd = {
                    val snapped = when {
                        chatFrac < app.pixelbureau.data.SettingsStore.COLLAPSE_SNAP_FRACTION -> 0f
                        chatFrac < app.pixelbureau.data.SettingsStore.MIN_OPEN_CHAT_FRACTION -> app.pixelbureau.data.SettingsStore.MIN_OPEN_CHAT_FRACTION
                        else -> chatFrac
                    }
                    chatFrac = snapped
                    model.setChatPanelFraction(snapped)
                },
            )
            val openTf = ui.taskforces.firstOrNull { it.data.id == ui.selectedTaskforceId }
            if (openTf != null) {
                TaskforcePanel(
                    taskforce = openTf,
                    agents = ui.agents,
                    messages = ui.taskforceMessages,
                    speaking = ui.taskforceSpeaking[openTf.data.id].orEmpty(),
                    quote = ui.taskforceQuote,
                    draft = ui.draft,
                    sprites = sprites,
                    ready = ui.spritesReady,
                    onDraft = model::setDraft,
                    onQuote = model::setTaskforceQuote,
                    onSend = model::send,
                    onSignOff = { model.signOffTaskforce(openTf.data.id) },
                    onConclude = { model.signOffTaskforce(openTf.data.id, fromConclusion = true) },
                    onDisband = { model.disbandTaskforce(openTf.data.id) },
                    failures = ui.taskforceFailures[openTf.data.id].orEmpty(),
                    onResolveFailure = { aid, retry -> model.resolveTaskforceFailure(openTf.data.id, aid, retry) },
                    debate = ui.taskforceDebates[openTf.data.id],
                    onStopDebate = { model.stopTaskforceDebate(openTf.data.id) },
                    collapsed = collapsed,
                    headerMode = ui.taskforceHeaderModes[openTf.data.id] ?: 0,
                    onHeaderMode = { m -> model.setTaskforceHeaderMode(openTf.data.id, m) },
                    modifier = if (collapsed) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
            val listState = rememberLazyListState()
            val agentBusy = selected?.status == "walking" ||
                selected?.status == "thinking" ||
                selected?.status == "writing"
            val streamOnScreen = stream != null && ui.messages.any { it.id == stream.messageId }
            val workingRow = selected != null && agentBusy && !streamOnScreen
            LaunchedEffect(ui.messages.size, stream?.text, selected?.status, workingRow) {
                val last = ui.messages.lastIndex + if (workingRow) 1 else 0
                if (last >= 0) {
                    // A tall reply is taller than the panel. Pin the end so the
                    // newest lines and the working pips stay above the composer.
                    listState.animateScrollToItem(last, scrollOffset = 100_000)
                }
            }
            if (!collapsed) LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(ChatPanelDeep.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (selected == null) {
                    item {
                        Text(
                            "Hire someone in PixelHire and they will show up on the floor.",
                            color = CreamDim,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                } else if (ui.messages.isEmpty() && !agentBusy) {
                    item(key = "empty-hint") {
                        EmptyChatHint(selected.name, onTask = { taskSheet = "" to "" })
                    }
                }
                val taskDone = selected?.id?.let { id ->
                    agentShowsTaskDone(
                        id,
                        ui.tasks.map { TaskNotice(it.id, it.agentId, it.status, it.createdAt) },
                        ui.dismissedTaskDone,
                        ui.replyDoneIds,
                    )
                } == true
                val celebrateId = if (taskDone) {
                    ui.messages.lastOrNull { it.role == "assistant" }?.id
                } else {
                    null
                }
                items(ui.messages, key = { it.id }) { message ->
                    val live = if (stream != null && stream.messageId == message.id) stream.text else message.text
                    val working = stream != null && stream.messageId == message.id
                    val statusLabel = if (working) replyWorkLabel(live) else null
                    val placeholder = isPlaceholderReply(live) ||
                        isScratchpad(live) ||
                        isSearchResultDump(live) ||
                        isSearchPromise(live)
                    if ((live.isBlank() || placeholder) && statusLabel == null && !working) {
                        return@items
                    }
                    val linked = message.taskId?.let { id -> ui.tasks.firstOrNull { it.id == id } }
                    val failedHere = linked != null &&
                        linked.status == "failed" &&
                        linked.agentId == message.agentId
                    ChatBubble(
                        role = message.role,
                        text = if (statusLabel != null || placeholder) "" else live,
                        spriteKey = selected?.spriteKey,
                        sprites = sprites,
                        ready = ui.spritesReady,
                        working = working,
                        workLabel = statusLabel,
                        celebrating = message.id == celebrateId,
                        taskOrder = message.role == "user" && message.taskId != null,
                        taskStatus = linked?.status,
                        onViewTask = if (message.taskId != null) onOpenBureau else null,
                        onRetry = if (failedHere) {
                            { retryTask = linked }
                        } else {
                            null
                        },
                    )
                }
                if (workingRow) {
                    item(key = "agent-working") {
                        ChatBubble(
                            role = "assistant",
                            text = "",
                            spriteKey = selected.spriteKey,
                            sprites = sprites,
                            ready = ui.spritesReady,
                            working = true,
                            workLabel = if (selected.status == "walking") "Heading to the desk" else "Thinking",
                        )
                    }
                }
            }

            if (ui.settings.apiKey.isBlank()) {
                Text(
                    "Add a model in Settings before anyone can answer.",
                    color = CreamDim,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            val meetingTf = selected?.id?.let { meetingTaskforceFor(ui.taskforces, it) }
            // Bottom composer: pending chips + gray 3D More btn | recessed field | yellow 3D send.
            if (meetingTf != null) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF3B2F5C)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Busy — at meeting: ${meetingTf.data.title}",
                        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Open taskforce chat",
                        color = Color(0xFF1C1F24), fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFD45C))
                            .clickable { model.selectTaskforce(meetingTf.data.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            } else Column(
                Modifier
                    .fillMaxWidth()
                    .background(ChatPanel)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                if (ui.pendingAttachments.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ui.pendingAttachments.forEach { item ->
                            PendingAttachmentChip(
                                name = item.name,
                                kind = item.kind,
                                onRemove = { model.removePendingAttachment(item.id) },
                            )
                        }
                    }
                }
                app.pixelbureau.logic.Slash.typing(ui.draft)?.let { pre ->
                    val cmds = app.pixelbureau.logic.ChatSlash.suggest(pre)
                    if (cmds.isNotEmpty() && selected != null) SlashMenuCard(
                        items = cmds,
                        disabledReason = null,
                        onPick = { taskSheet = "" to ""; model.setDraft("") },
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box {
                        PixelRaisedButton(
                            face = ChatBtnGray,
                            highlight = ChatBtnGrayHi,
                            shadow = ChatBtnGrayLo,
                            enabled = selected != null,
                            onClick = { if (selected != null) chatMenuOpen = true },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "More",
                                tint = if (selected != null) Cream else CreamDim.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = chatMenuOpen,
                            onDismissRequest = { chatMenuOpen = false },
                            shape = KaiMenuShape,
                            containerColor = KaiCard,
                            tonalElevation = 0.dp,
                            shadowElevation = 6.dp,
                            border = BorderStroke(2.dp, KaiBorderDeep),
                        ) {
                            val menuColors = MenuDefaults.itemColors(
                                textColor = KaiInk,
                                leadingIconColor = KaiInk,
                                disabledTextColor = KaiInkMuted.copy(alpha = 0.45f),
                                disabledLeadingIconColor = KaiInkMuted.copy(alpha = 0.45f),
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Attach files",
                                        color = if (selected != null) KaiInk else KaiInkMuted.copy(alpha = 0.45f),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                    )
                                },
                                onClick = {
                                    chatMenuOpen = false
                                    pickFiles.launch(arrayOf("*/*"))
                                },
                                enabled = selected != null,
                                leadingIcon = {
                                    KaiMenuEmojiBadge(emoji = "📎", face = KaiSky)
                                },
                                colors = menuColors,
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Attach images",
                                        color = if (selected != null) KaiInk else KaiInkMuted.copy(alpha = 0.45f),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                    )
                                },
                                onClick = {
                                    chatMenuOpen = false
                                    pickImages.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                enabled = selected != null,
                                leadingIcon = {
                                    KaiMenuEmojiBadge(emoji = "🖼️", face = KaiPeach)
                                },
                                colors = menuColors,
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Clear session",
                                        color = when {
                                            canClear -> Brick
                                            else -> KaiInkMuted.copy(alpha = 0.45f)
                                        },
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                    )
                                },
                                onClick = {
                                    chatMenuOpen = false
                                    confirmClear = true
                                },
                                enabled = canClear,
                                leadingIcon = {
                                    KaiMenuEmojiBadge(
                                        emoji = "🧹",
                                        face = if (canClear) KaiRose else KaiCardNest,
                                    )
                                },
                                colors = menuColors,
                            )
                        }
                    }

                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .shadow(2.dp, InputPillShape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black)
                            .clip(InputPillShape)
                            .background(ChatInputWell)
                            .border(1.5.dp, ChatBorderInner, InputPillShape)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        val placeholder = if (selected == null) "No one is on the floor" else "Message ${selected.name}"
                        if (ui.draft.isEmpty()) {
                            Text(
                                placeholder,
                                color = CreamDim.copy(alpha = 0.55f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = ui.draft,
                            onValueChange = model::setDraft,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                color = Cream,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                            ),
                            cursorBrush = SolidColor(Amber),
                            maxLines = 3,
                            singleLine = false,
                        )
                    }

                    val canSend = selected != null && (ui.draft.isNotBlank() || ui.pendingAttachments.isNotEmpty())
                    PixelRaisedButton(
                        face = ChatBtnAmber,
                        highlight = ChatBtnAmberHi,
                        shadow = ChatBtnAmberLo,
                        enabled = canSend,
                        onClick = {
                            val t = app.pixelbureau.logic.ChatSlash.parseTask(ui.draft)
                            if (t != null && selected != null) {
                                taskSheet = app.pixelbureau.logic.ChatSlash.split(t); model.setDraft("")
                            } else model.send()
                        },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) Ink else Ink.copy(alpha = 0.45f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            } // end 1:1 chat (else of taskforce panel)
        }

        if (confirmClear && selected != null) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                containerColor = Panel,
                title = { Text("Clear ${selected.name}'s session?", color = Cream) },
                text = {
                    Text(
                        "This deletes the thread, clears pending attachments, and stops the current reply. The next message starts fresh.",
                        color = CreamDim,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmClear = false
                        model.clearSession()
                    }) { Text("Clear session", color = Brick) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
                },
            )
        }
    }
    if (ui.meetingDialogOpen) {
        CreateTaskforceDialog(
            agents = active,
            onDismiss = model::closeMeetingDialog,
            onCreate = model::createTaskforce,
            sprites = sprites,
            doneCounts = ui.tasks.filter { it.status == "done" }.groupingBy { it.agentId }.eachCount(),
        )
    }
    val again = retryTask
    if (again != null) {
        RetryTaskDialog(
            title = again.title.ifBlank { "Task" },
            agents = active,
            initialAgentId = again.agentId,
            onDismiss = { retryTask = null },
            onRetry = { agentId ->
                model.retryTask(again.id, agentId)
                retryTask = null
            },
        )
    }
}

@Composable
private fun AgentChip(
    agent: Agent,
    selected: Boolean,
    sprites: SpriteStore,
    ready: Boolean,
    modifier: Modifier = Modifier,
    celebrating: Boolean = false,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.width(64.dp).alpha(if (busy) 0.55f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tileMod = if (selected) {
            Modifier
                .shadow(
                    elevation = 10.dp,
                    shape = ChipShape,
                    ambientColor = AmberGlow,
                    spotColor = Amber,
                )
                .border(3.dp, Amber, ChipShape)
                .clip(ChipShape)
                .background(ChipTileSelected)
        } else {
            Modifier
                .border(2.dp, ChatBorderOuter.copy(alpha = 0.7f), ChipShape)
                .clip(ChipShape)
                .background(ChipTile)
        }
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = tileMod
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
                AgentFace(
                    spriteKey = agent.spriteKey,
                    sprites = sprites,
                    ready = ready,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    agent.name,
                    color = if (selected) Amber else Cream,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) {
                    Row(
                        Modifier.padding(top = 1.dp).clip(RoundedCornerShape(50)).background(Color(0xFFFFD45C)).padding(horizontal = 4.dp, vertical = 1.dp)
                            .semantics { contentDescription = "Busy, at meeting" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(Color(0xFFD9534F)))
                        Spacer(Modifier.width(2.dp))
                        Text("Busy", color = Color(0xFF1C1F24), fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            if (celebrating) {
                TaskDoneBadge(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .size(34.dp),
                )
            }
        }
        // Speech-bubble pointer into the chat panel (selected only).
        Box(
            Modifier
                .height(8.dp)
                .width(16.dp)
                .offset(y = (-1).dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(width = 16.dp, height = 8.dp)
                        .clip(TriangleDown)
                        .background(Amber),
                )
            }
        }
    }
}

@Composable
internal fun AgentFace(
    spriteKey: String,
    sprites: SpriteStore,
    ready: Boolean,
    modifier: Modifier = Modifier,
    celebrating: Boolean = false,
) {
    val bitmap = remember(spriteKey, ready) {
        if (!ready) null else sprites.get("chars/${spriteKey}_idle")
    }
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(8.dp))
                .background(PanelRaised),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (bitmap != null && !bitmap.isRecycled) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    filterQuality = FilterQuality.None,
                )
            }
        }
        if (celebrating) {
            TaskDoneBadge(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxSize(0.72f)
                    .padding(top = 1.dp, end = 1.dp),
            )
        }
    }
}

/** Clipboard stamp on a chat portrait. Pops in, then the board sways until the user taps or switches. */
@Composable
private fun TaskDoneBadge(modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0.35f) }
    LaunchedEffect(Unit) {
        pop.animateTo(
            1f,
            animationSpec = keyframes {
                durationMillis = 480
                0.35f at 0
                1.16f at 180
                0.94f at 320
                1f at 480
            },
        )
    }
    val motion = rememberInfiniteTransition(label = "taskDone")
    val sway by motion.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "taskDoneSway",
    )
    val hop by motion.animateFloat(
        initialValue = 0f,
        targetValue = -2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "taskDoneHop",
    )
    val context = LocalContext.current
    val stamp = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.task_done)?.asImageBitmap()
    } ?: return
    Image(
        bitmap = stamp,
        contentDescription = "Task done",
        modifier = modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
            alpha = ((pop.value - 0.35f) / 0.4f).coerceIn(0f, 1f)
            rotationZ = if (pop.value < 0.99f) (1f - pop.value) * -16f else sway
            translationY = if (pop.value >= 0.99f) hop.dp.toPx() else 0f
        },
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
    )
}


@Composable
private fun ChatResizeHandle(
    onDrag: (deltaPx: Float) -> Unit,
    onDragEnd: () -> Unit,
    badge: Int = 0,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(ChatPanel)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(44.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(ChatBorderOuter.copy(alpha = 0.9f))
                .border(1.dp, ChatBorderInner.copy(alpha = 0.55f), RoundedCornerShape(2.5.dp)),
        )
        if (badge > 0) {
            Text(
                if (badge > 9) "9+ new" else "$badge new",
                color = Color(0xFF1C1F24), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFD45C)).padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun ChatBubble(
    role: String,
    text: String,
    spriteKey: String?,
    sprites: SpriteStore,
    ready: Boolean,
    working: Boolean = false,
    workLabel: String? = null,
    celebrating: Boolean = false,
    taskOrder: Boolean = false,
    taskStatus: String? = null,
    onRetry: (() -> Unit)? = null,
    onViewTask: (() -> Unit)? = null,
) {
    val mine = role == "user"
    if (taskOrder) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TaskOrderBubble(text = text, status = taskStatus, onRetry = onRetry, onView = onViewTask)
        }
    } else if (mine) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ChatMarkdownContent(
                text = text,
                onAgentBubble = false,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .shadow(3.dp, BubbleShapeUser, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.4f))
                    .clip(BubbleShapeUser)
                    .background(ChatBubbleUser)
                    .border(1.5.dp, ChatBtnAmberLo, BubbleShapeUser)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (spriteKey != null) {
                AgentFace(
                    spriteKey = spriteKey,
                    sprites = sprites,
                    ready = ready,
                    celebrating = celebrating,
                    modifier = Modifier.size(32.dp),
                )
                // Tail pointing toward the avatar.
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .size(width = 8.dp, height = 12.dp)
                        .clip(TriangleLeft)
                        .background(ChatBubbleAgentBorder),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(BubbleShapeAgent)
                    .background(ChatBubbleAgent)
                    .border(1.5.dp, ChatBubbleAgentBorder, BubbleShapeAgent)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                if (text.isNotBlank()) {
                    ChatMarkdownContent(
                        text = text,
                        onAgentBubble = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (working) {
                    ReplyWorking(
                        label = workLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (text.isNotBlank()) 8.dp else 0.dp),
                    )
                }
            }
        }
    }
}

private val TaskSlipShape = RoundedCornerShape(10.dp)

/**
 * Assigned work stays on the user's side, but as a parchment job slip
 * instead of the gold chat bubble.
 */
@Composable
private fun TaskOrderBubble(text: String, status: String?, onRetry: (() -> Unit)? = null, onView: (() -> Unit)? = null) {
    Column(
        Modifier
            .widthIn(max = 320.dp)
            .shadow(3.dp, TaskSlipShape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(TaskSlipShape)
            .background(KaiCard)
            .border(2.dp, KaiBorderDeep, TaskSlipShape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(KaiButter)
                .drawBehind {
                    drawLine(
                        color = KaiAccentLo,
                        start = Offset(0f, size.height - 1.5.dp.toPx()),
                        end = Offset(size.width, size.height - 1.5.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PixelClipboard(Modifier.size(16.dp))
            Text(
                "TASK ASSIGNED",
                color = KaiInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.weight(1f))
            if (!status.isNullOrBlank()) {
                TaskStatusPip(status)
            }
        }
        val body = taskOrderBody(text)
        if (body.isNotBlank()) {
            ChatMarkdownContent(
                text = body,
                onAgentBubble = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (onView != null) {
            Text(
                "View in Pixel Bureau ›",
                color = KaiAccentLo, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp)).clickable(onClick = onView).padding(vertical = 2.dp),
            )
        }
        if (onRetry != null) {
            Text(
                "Retry",
                color = KaiAccentLo,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KaiButter)
                    .border(1.5.dp, KaiAccent, RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onRetry,
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun TaskStatusPip(status: String) {
    val (face, ink, label) = when (status) {
        "done" -> Triple(KaiMint, Color(0xFF3A6B2E), "done")
        "failed" -> Triple(KaiRose, Brick, "failed")
        "working" -> Triple(Color(0xFFFFE08A), KaiAccentLo, "working")
        "queued" -> Triple(KaiSky, KaiInk, "queued")
        else -> Triple(KaiCardNest, KaiInkMuted, status)
    }
    Text(
        label,
        color = ink,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(face)
            .border(1.dp, KaiBorderDeep.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Tiny clipboard so a job slip is obvious next to a gold chat bubble. */
@Composable
private fun PixelClipboard(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = size.minDimension / 10f
        fun block(x: Int, y: Int, w: Int, h: Int, color: Color) {
            drawRect(
                color = color,
                topLeft = Offset(x * cell, y * cell),
                size = Size(w * cell, h * cell),
            )
        }
        block(3, 0, 4, 1, Color(0xFFE4E4E4))
        block(3, 1, 4, 1, Color(0xFF6E6E6E))
        block(1, 1, 8, 8, Color(0xFF6B4423))
        block(2, 3, 6, 5, Color(0xFFFFF8EC))
        block(3, 4, 4, 1, Color(0xFFC9B090))
        block(3, 6, 3, 1, Color(0xFFC9B090))
    }
}

/** Hopping pixel pips, the same beat as the desk "···" bubble, so a slow reply still looks alive. */
@Composable
internal fun ReplyWorking(label: String?, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "replyWorking")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "replyPipPhase",
    )
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                label,
                color = Amber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            repeat(3) { index ->
                val local = (phase - index + 3f) % 1f
                val hop = if (local < 0.45f) {
                    kotlin.math.sin(local / 0.45f * Math.PI).toFloat()
                } else {
                    0f
                }
                Box(
                    Modifier
                        .offset(y = (-5f * hop).dp)
                        .size(7.dp)
                        .background(if (hop > 0.15f) Amber else CreamDim),
                )
            }
        }
    }
}

/**
 * Classic raised 3D game button: light top/left rim, dark bottom/right bevel.
 * No Material Surface / Button / ripple / clip scrub — soft scale + press-in pulse only.
 */
@Composable
internal fun PixelRaisedButton(
    face: Color,
    highlight: Color,
    shadow: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1f
            pressed -> 0.90f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pixelRaisedScale",
    )
    val pressDepth by animateDpAsState(
        targetValue = if (enabled && pressed) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 80),
        label = "pixelRaisedDepth",
    )
    val alpha = if (enabled) 1f else 0.45f
    // ChatPanel underlay fills the fixed 44dp cell so the scale pulse never flashes a
    // rectangular grey "hole" (parent charcoal showing through) that looks like Material scrub.
    Box(
        modifier = modifier
            .background(ChatPanel)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val r = 10.dp.toPx()
                // Outer dark plate (depth) — shrinks toward the face while pressed
                val plateY = (2.dp - pressDepth * 0.5f).coerceAtLeast(0.5.dp).toPx()
                drawRoundRect(
                    color = shadow.copy(alpha = alpha),
                    topLeft = Offset(0f, plateY),
                    size = size.copy(height = size.height - plateY),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
            }
            .offset(y = pressDepth)
            // Shape via background — no Modifier.clip (clip + press = grey box leftovers)
            .background(face.copy(alpha = alpha), RaisedBtnShape)
            .drawBehind {
                // Top/left highlight stroke
                val inset = 2.dp.toPx()
                val hi = if (pressed && enabled) highlight.copy(alpha = alpha * 0.55f) else highlight.copy(alpha = alpha)
                val lo = shadow.copy(alpha = alpha)
                drawLine(
                    color = hi,
                    start = Offset(inset, inset),
                    end = Offset(size.width - inset, inset),
                    strokeWidth = 3.dp.toPx(),
                )
                drawLine(
                    color = hi,
                    start = Offset(inset, inset),
                    end = Offset(inset, size.height - inset),
                    strokeWidth = 3.dp.toPx(),
                )
                // Bottom/right shadow stroke
                drawLine(
                    color = lo,
                    start = Offset(inset, size.height - inset),
                    end = Offset(size.width - inset, size.height - inset),
                    strokeWidth = 3.dp.toPx(),
                )
                drawLine(
                    color = lo,
                    start = Offset(size.width - inset, inset),
                    end = Offset(size.width - inset, size.height - inset),
                    strokeWidth = 3.dp.toPx(),
                )
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}



@Composable
private fun KaiMenuEmojiBadge(emoji: String, face: Color) {
    Box(
        Modifier
            .size(28.dp)
            .clip(KaiMenuIconShape)
            .background(face)
            .border(1.25.dp, KaiBorderDeep.copy(alpha = 0.55f), KaiMenuIconShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 13.sp)
    }
}

@Composable
private fun PendingAttachmentChip(
    name: String,
    kind: String,
    onRemove: () -> Unit,
) {
    val label = if (kind == "image") "🖼 $name" else "📎 $name"
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PanelRaised)
            .border(1.dp, ChatBorderInner, RoundedCornerShape(12.dp))
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            color = Cream,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = "Remove $name",
            tint = CreamDim,
            modifier = Modifier
                .size(22.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRemove,
                )
                .padding(2.dp),
        )
    }
}

@Composable
private fun OfficeHeader(day: Int, reputation: Int, chatCredit: Int, banner: String?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Day $day  ·  ${officeClock(now)}",
                color = Cream,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "★ $reputation  ·  $chatCredit/$ChatsPerStar",
                color = Amber,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (banner != null) {
            Spacer(Modifier.height(4.dp))
            Text(banner, color = Brick, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OfficeViewport(model: BureauViewModel, modifier: Modifier) {
    val office by model.office.collectAsStateWithLifecycle()
    val ui by model.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val sprites = (ctx.applicationContext as PixelBureauApp).container.sprites
    var meetingInfoId by remember { mutableStateOf<String?>(null) }
    val infoTf = ui.taskforces.firstOrNull { it.data.id == meetingInfoId && app.pixelbureau.logic.TaskforceStatus.isOpen(it.data.status) }
    if (infoTf != null) {
        MeetingInfoDialog(
            taskforce = infoTf,
            agents = ui.agents,
            sprites = sprites,
            ready = ui.spritesReady,
            discussTurn = ui.taskforceDebates[infoTf.data.id],
            onOpenChat = { meetingInfoId = null; model.selectTaskforce(infoTf.data.id) },
            onDismiss = { meetingInfoId = null },
        )
    }
    OfficeCanvas(
        snapshot = office,
        sprites = sprites,
        ready = ui.spritesReady,
        onTapAgent = { id ->
            val tf = meetingTaskforceFor(ui.taskforces, id)
            if (tf != null) meetingInfoId = tf.data.id else model.select(id)
        },
        speechScale = ui.bubbleSpeechScale,
        modifier = modifier,
        placed = ui.placedProps,
        deskSprite = ui.deskSprite,
        onTapRoom = {
            val open = ui.taskforces.firstOrNull { app.pixelbureau.logic.TaskforceStatus.isOpen(it.data.status) }
            if (open != null) meetingInfoId = open.data.id else model.openMeetingDialog()
        },
        roomHighlight = ui.meetingDialogOpen ||
            ui.taskforces.any { it.data.id == ui.selectedTaskforceId },
        meetingTv = ui.taskforces.firstOrNull { app.pixelbureau.logic.TaskforceStatus.isOpen(it.data.status) }
            ?.let { MeetingTv(it.data.title, it.data.progress) },
    )
}

/** Shared slash-command suggestion card (taskforce + 1:1 chat): icon, usage, short description. */
@Composable
internal fun SlashMenuCard(
    items: List<app.pixelbureau.logic.Slash.Spec>,
    disabledReason: String?,
    onPick: (app.pixelbureau.logic.Slash.Spec) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp)).background(KaiCard)
            .border(2.dp, KaiBorder, RoundedCornerShape(10.dp)).padding(4.dp),
    ) {
        for (c in items) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .clickable(enabled = disabledReason == null) { onPick(c) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(c.icon, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(c.usage, color = if (disabledReason == null) KaiInk else KaiInkMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(disabledReason ?: c.desc, color = KaiInk.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** /task from the 1:1 chat: compact assign sheet mirroring the Pixel Bureau task dialog (title, brief, assignee). */
@Composable
private fun ChatTaskSheet(
    agent: Agent,
    sprites: SpriteStore,
    ready: Boolean,
    title0: String,
    brief0: String,
    onDismiss: () -> Unit,
    onAssign: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(title0) }
    var brief by remember { mutableStateOf(brief0) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth(0.92f).heightIn(max = 560.dp).imePadding()
                .clip(RoundedCornerShape(20.dp)).background(KaiCard).border(2.dp, KaiBorder, RoundedCornerShape(20.dp)),
        ) {
            Row(
                Modifier.fillMaxWidth().background(KaiButter).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("📝", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("Assign a task", color = KaiInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(KaiCardNest).border(1.5.dp, KaiBorder, RoundedCornerShape(50))
                        .padding(start = 4.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AgentFace(spriteKey = agent.spriteKey, sprites = sprites, ready = ready, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("For ${agent.name}", color = KaiInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text("Title") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = brief, onValueChange = { brief = it }, minLines = 3, maxLines = 6,
                    label = { Text("Brief (optional)") }, modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                Modifier.fillMaxWidth().background(KaiCard).border(1.dp, KaiBorder.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = KaiInkMuted) }
                Spacer(Modifier.weight(1f))
                val ok = title.isNotBlank()
                Text(
                    "✓ Assign", color = if (ok) Color.White else KaiInkMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (ok) Color(0xFF3FA34D) else KaiCardNest)
                        .border(2.dp, if (ok) Color(0xFF2B7A36) else KaiBorder, RoundedCornerShape(50))
                        .clickable(enabled = ok) { onAssign(title.trim(), brief.trim()) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Rotating tips under the empty 1:1 chat heading. Index 0 carries the tappable /task chip. */
internal val EmptyChatTips = listOf("to assign a task", "Ask a question to chat", "Tap their avatar for details")

/** Subtle centred empty state: muted heading + one rotating low-contrast tip. */
@Composable
private fun EmptyChatHint(name: String, onTask: () -> Unit) {
    var tip by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(4500); tip = (tip + 1) % EmptyChatTips.size }
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Give $name something to do",
            color = CreamDim.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        androidx.compose.animation.Crossfade(targetState = tip, label = "tip") { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i == 0) {
                    Text("Tip: type ", color = CreamDim.copy(alpha = 0.55f), fontSize = 12.sp)
                    Text(
                        "/task", color = Cream.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                            .clickable(onClick = onTask).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    Text(" ${EmptyChatTips[0]}", color = CreamDim.copy(alpha = 0.55f), fontSize = 12.sp)
                } else {
                    Text("Tip: ${EmptyChatTips[i]}", color = CreamDim.copy(alpha = 0.55f), fontSize = 12.sp)
                }
            }
        }
    }
}
