package app.pixelbureau.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pixelbureau.logic.BubbleSpeechScaleMax
import app.pixelbureau.logic.BubbleSpeechScaleMin
import app.pixelbureau.logic.SavedLlm
import app.pixelbureau.logic.McpServerConfig
import app.pixelbureau.logic.coerceBubbleSpeechScale
import kotlin.math.roundToInt
import app.pixelbureau.logic.displayDetail
import app.pixelbureau.logic.displayTitle
import app.pixelbureau.logic.llmProvider
import app.pixelbureau.logic.llmProviders
import app.pixelbureau.logic.resolvedBaseUrl

private val KaiPanelShape = RoundedCornerShape(18.dp)
private val KaiTileShape = RoundedCornerShape(14.dp)
private val KaiChipShape = RoundedCornerShape(999.dp)
private val KaiBtnShape = RoundedCornerShape(12.dp)

@Composable
fun SettingsScreen(model: BureauViewModel, modifier: Modifier = Modifier) {
    val ui by model.ui.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<SavedLlm?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<SavedLlm?>(null) }
    var mcpEditor by remember { mutableStateOf<McpServerConfig?>(null) }
    var mcpAdding by remember { mutableStateOf(false) }
    var mcpRemove by remember { mutableStateOf<McpServerConfig?>(null) }
    val banner = ui.banner
    val goodNews = banner != null && (
        banner.startsWith("Added") ||
            banner.startsWith("Updated") ||
            banner.startsWith("Chats") ||
            banner.startsWith("Removed") ||
            banner.startsWith("Applied")
        )

    Column(
        modifier
            .fillMaxSize()
            .background(KaiPage)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsHeader()

        Text(
            "Bring your own keys, mark one model In use, and bind the floor from Company. Agent tools stay here when they ship.",
            color = KaiInkMuted,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp,
        )

        if (banner != null) {
            SettingsBanner(banner, goodNews)
        }

        // ── LLM BYOK ───────────────────────────────────────────────────────
        SettingsKaiPanel(
            accent = KaiButter,
            title = "LLM BYOK",
            emoji = "🔑",
            trailing = {
                SettingsKaiButton(
                    label = "Add",
                    enabled = true,
                    face = KaiAccent,
                    highlight = KaiAccentHi,
                    shadow = KaiAccentLo,
                    ink = Ink,
                    onClick = { adding = true },
                )
            },
        ) {
            Text(
                "Add each model you want to call. Enter its API URL, key, and model id. The chips fill in a URL you can change. The one marked In use is the default; Company can override per agent, or apply it to everyone here.",
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
            )

            if (ui.models.isEmpty()) {
                SettingsEmptyHint("No models yet. Tap Add to save a BYOK entry.")
            }

            for (saved in ui.models) {
                val inUse = saved.id == ui.activeModelId
                ModelTile(
                    saved = saved,
                    inUse = inUse,
                    canApply = ui.agents.any { !it.archived },
                    onUse = { model.useModel(saved.id) },
                    onEdit = { editor = saved },
                    onRemove = { pendingRemove = saved },
                    onApplyAll = { model.applyActiveModelToAllAgents() },
                )
            }
        }

        // ── MCP Servers ─────────────────────────────────────────────────────
        SettingsKaiPanel(
            accent = KaiMint,
            title = "MCP Servers",
            emoji = "🔌",
            trailing = {
                SettingsKaiButton(
                    label = "Add",
                    enabled = true,
                    face = KaiAccent,
                    highlight = KaiAccentHi,
                    shadow = KaiAccentLo,
                    ink = Ink,
                    onClick = { mcpAdding = true },
                )
            },
        ) {
            Text(
                "Connect Model Context Protocol servers over HTTP. Their tools join every agent's toolbox as server__tool. Company can narrow which servers each agent uses.",
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
            )
            if (ui.mcpServers.isEmpty()) {
                SettingsEmptyHint("No MCP servers yet. Tap Add and paste a Streamable HTTP URL.")
            }
            for (server in ui.mcpServers) {
                McpServerTile(
                    server = server,
                    status = ui.mcpTest[server.id],
                    onToggle = { model.setMcpServerEnabled(server.id, !server.enabled) },
                    onTest = { model.testMcpServer(server.id) },
                    onEdit = { mcpEditor = server },
                    onRemove = { mcpRemove = server },
                )
            }
        }

        // ── Agent tools ────────────────────────────────────────────────────
        SettingsKaiPanel(
            accent = KaiSky,
            title = "Agent tools",
            emoji = "🧰",
        ) {
            Text(
                "Live capabilities are grouped by how they apply. Toggle a tool to offer or hide it.",
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
            )
            CapabilityGroup(
                title = "Common",
                detail = "Every agent can use these floor routines.",
                accent = KaiMint,
            ) {
                CapabilityRow(
                    emoji = "💬",
                    title = "Chat replies",
                    detail = "Streaming BYOK completions in Office",
                    face = KaiMint,
                )
                CapabilityRow(
                    emoji = "📋",
                    title = "Task queue",
                    detail = "Assign, walk to desk, reply in character",
                    face = KaiPeach,
                )
                CapabilityToggleRow(
                    emoji = "🌐",
                    title = "Internet search",
                    detail = "Search the web to validate and read up-to-date information",
                    face = KaiSky,
                    enabled = ui.internetSearchEnabled,
                    onToggle = { model.setInternetSearchEnabled(!ui.internetSearchEnabled) },
                )
                CapabilityToggleRow(
                    emoji = "🕐",
                    title = "Time",
                    detail = "Read the phone clock and convert a time to any place on Earth",
                    face = KaiButter,
                    enabled = ui.timeToolEnabled,
                    onToggle = { model.setTimeToolEnabled(!ui.timeToolEnabled) },
                )
            }
            CapabilityGroup(
                title = "Special",
                detail = "Optional routing for a specific agent.",
                accent = KaiPeach,
            ) {
                CapabilityRow(
                    emoji = "🔗",
                    title = "Per-agent model",
                    detail = "Bind any catalog model from Company",
                    face = KaiButter,
                )
            }
        }

        SettingsKaiPanel(
            accent = KaiPeach,
            title = "System settings",
            emoji = "🖥️",
        ) {
            Text(
                "How the office behaves on this phone.",
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
            )
            BubbleSpeechSettings(
                llmEnabled = ui.bubbleSpeechLlmEnabled,
                scale = ui.bubbleSpeechScale,
                onLlm = { model.setBubbleSpeechLlmEnabled(!ui.bubbleSpeechLlmEnabled) },
                onScale = { model.setBubbleSpeechScale(it, persist = false) },
                onScaleFinished = { model.setBubbleSpeechScale(it, persist = true) },
            )
        }

        Spacer(Modifier.height(8.dp))
    }

    if (adding || editor != null) {
        ModelDialog(
            initial = editor,
            onDismiss = {
                adding = false
                editor = null
            },
            onSave = { providerId, apiKey, modelName, label, apiUrl ->
                val error = model.saveModel(editor?.id, providerId, apiKey, modelName, label, apiUrl)
                if (error == null) {
                    adding = false
                    editor = null
                }
            },
        )
    }
    if (mcpAdding || mcpEditor != null) {
        McpServerDialog(
            initial = mcpEditor,
            onDismiss = {
                mcpAdding = false
                mcpEditor = null
            },
            onSave = { name, url, header, token, enabled ->
                if (model.saveMcpServer(mcpEditor?.id, name, url, header, token, enabled) == null) {
                    mcpAdding = false
                    mcpEditor = null
                }
            },
        )
    }
    val mcpDoomed = mcpRemove
    if (mcpDoomed != null) {
        AlertDialog(
            onDismissRequest = { mcpRemove = null },
            containerColor = KaiCard,
            title = { Text("Remove ${mcpDoomed.name}?", color = KaiInk, fontWeight = FontWeight.SemiBold) },
            text = { Text("Its URL and token are deleted, and agents lose its tools.", color = KaiInkMuted) },
            confirmButton = {
                TextButton(onClick = {
                    model.removeMcpServer(mcpDoomed.id)
                    mcpRemove = null
                }) { Text("Remove", color = Brick) }
            },
            dismissButton = {
                TextButton(onClick = { mcpRemove = null }) { Text("Cancel", color = KaiInkMuted) }
            },
        )
    }
    val removing = pendingRemove
    if (removing != null) {
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            containerColor = KaiCard,
            title = {
                Text(
                    "Remove ${removing.displayTitle()}?",
                    color = KaiInk,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    "The key saved for this model is deleted with it.",
                    color = KaiInkMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    model.removeModel(removing.id)
                    pendingRemove = null
                }) { Text("Remove", color = Brick) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text("Cancel", color = KaiInkMuted)
                }
            },
        )
    }
}

@Composable
private fun SettingsHeader() {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KaiButter)
                .border(2.dp, KaiBorderDeep, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("⚙️", fontSize = 18.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = KaiInk,
                fontWeight = FontWeight.Bold,
            )
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .width(52.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KaiAccent),
            )
        }
    }
}

@Composable
private fun SettingsBanner(text: String, goodNews: Boolean) {
    val face = if (goodNews) KaiMint else KaiRose
    val ink = if (goodNews) Color(0xFF3A6B2E) else Brick
    val emoji = if (goodNews) "✓" else "⚠"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(KaiTileShape)
            .background(face)
            .border(1.5.dp, ink.copy(alpha = 0.45f), KaiTileShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, fontSize = 14.sp)
        Text(
            text,
            color = ink,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingsEmptyHint(text: String) {
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

@Composable
private fun ModelTile(
    saved: SavedLlm,
    inUse: Boolean,
    canApply: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onApplyAll: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(KaiTileShape)
            .background(if (inUse) KaiMint.copy(alpha = 0.55f) else KaiCardNest)
            .border(
                1.5.dp,
                if (inUse) KaiMintDeep.copy(alpha = 0.7f) else KaiBorder,
                KaiTileShape,
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                saved.displayTitle(),
                color = KaiInk,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (inUse) {
                SettingsSoftBadge(label = "In use", face = KaiMint, ink = Color(0xFF3A6B2E))
            }
        }
        Text(
            saved.displayDetail(),
            color = KaiInkMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!inUse) {
                SettingsSoftChip("Use", onUse)
            }
            SettingsSoftChip("Edit", onEdit)
            SettingsSoftChip("Remove", onRemove, danger = true)
        }
        if (inUse) {
            SettingsKaiButton(
                label = "Apply to all agents",
                enabled = canApply,
                face = KaiMintDeep,
                highlight = Color(0xFFB8DFA8),
                shadow = Color(0xFF5A8F4A),
                ink = Ink,
                onClick = onApplyAll,
            )
        }
    }
}

@Composable
private fun BubbleSpeechSettings(
    llmEnabled: Boolean,
    scale: Float,
    onLlm: () -> Unit,
    onScale: (Float) -> Unit,
    onScaleFinished: (Float) -> Unit,
) {
    var thumb by remember { mutableFloatStateOf(scale) }
    LaunchedEffect(scale) { thumb = scale }
    val shown = coerceBubbleSpeechScale(thumb)
    val percent = (shown * 100f).roundToInt()
    val sizeLabel = if (percent == 100) "Default" else "$percent%"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(KaiTileShape)
            .background(KaiPeach.copy(alpha = 0.35f))
            .border(1.5.dp, KaiBorder, KaiTileShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Bubble speech",
            color = KaiInk,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Overhead lines when agents talk on the map.",
            color = KaiInkMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        CapabilityToggleRow(
            emoji = "💬",
            title = "LLM usage",
            detail = "About one line in twenty can use that agent's model. Off keeps the short local lines.",
            face = KaiButter,
            enabled = llmEnabled,
            onToggle = onLlm,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(KaiTileShape)
                .background(KaiCardNest)
                .border(1.5.dp, KaiBorder, KaiTileShape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Size", color = KaiInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(sizeLabel, color = KaiInkMuted, style = MaterialTheme.typography.labelMedium)
                Text(
                    "Hello",
                    color = Color(0xFF161410),
                    fontSize = (12f * shown).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape((8f * shown).dp))
                        .background(Color.White)
                        .border(1.dp, KaiBorder, RoundedCornerShape((8f * shown).dp))
                        .padding(horizontal = (8f * shown).dp, vertical = (3f * shown).dp),
                )
            }
            Text(
                "Default is the size on the map today.",
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = shown,
                onValueChange = {
                    thumb = it
                    onScale(it)
                },
                onValueChangeFinished = { onScaleFinished(thumb) },
                valueRange = BubbleSpeechScaleMin..BubbleSpeechScaleMax,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = KaiInk,
                    activeTrackColor = KaiAccent,
                    inactiveTrackColor = KaiBorder,
                    activeTickColor = KaiAccentLo,
                    inactiveTickColor = KaiBorderDeep.copy(alpha = 0.35f),
                ),
            )
        }
    }
}

@Composable
private fun CapabilityGroup(
    title: String,
    detail: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(KaiTileShape)
            .background(accent.copy(alpha = 0.22f))
            .border(1.5.dp, KaiBorder, KaiTileShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                color = KaiInk,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            SettingsSoftBadge(label = "Live", face = KaiMint, ink = Color(0xFF3A6B2E))
        }
        Text(
            detail,
            color = KaiInkMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
private fun CapabilityRow(
    emoji: String,
    title: String,
    detail: String,
    face: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(KaiTileShape)
            .background(KaiCardNest)
            .border(1.5.dp, KaiBorder, KaiTileShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(face)
                .border(1.25.dp, KaiBorderDeep.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 16.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = KaiInk, fontWeight = FontWeight.SemiBold)
            Text(detail, color = KaiInkMuted, style = MaterialTheme.typography.bodySmall)
        }
        SettingsSoftBadge(label = "On", face = KaiMint, ink = Color(0xFF3A6B2E))
    }
}


@Composable
private fun CapabilityToggleRow(
    emoji: String,
    title: String,
    detail: String,
    face: Color,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(KaiTileShape)
            .background(KaiCardNest)
            .border(1.5.dp, KaiBorder, KaiTileShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = onToggle,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(face)
                .border(1.25.dp, KaiBorderDeep.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 16.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = KaiInk, fontWeight = FontWeight.SemiBold)
            Text(detail, color = KaiInkMuted, style = MaterialTheme.typography.bodySmall)
        }
        SoftSwitch(on = enabled, onToggle = onToggle)
    }
}

@Composable
private fun SoftSwitch(on: Boolean, onToggle: () -> Unit) {
    val track = if (on) KaiMint else KaiCardNest
    val border = if (on) KaiMintDeep.copy(alpha = 0.75f) else KaiBorder
    val thumb = if (on) Color(0xFF3A6B2E) else KaiInkMuted
    Box(
        Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(KaiChipShape)
            .background(track)
            .border(1.5.dp, border, KaiChipShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = onToggle,
            )
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(thumb)
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        )
    }
}

/**
 * Soft cream panel with pastel header strip — mirrors Roster's KaiPanel.
 */
@Composable
private fun SettingsKaiPanel(
    accent: Color,
    title: String,
    emoji: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(emoji, fontSize = 14.sp)
            Text(
                title,
                color = KaiInk,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
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

@Composable
private fun SettingsSoftBadge(label: String, face: Color, ink: Color) {
    Text(
        label,
        color = ink,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(KaiChipShape)
            .background(face)
            .border(1.dp, KaiBorder.copy(alpha = 0.7f), KaiChipShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun SettingsSoftChip(label: String, onClick: () -> Unit, danger: Boolean = false) {
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
private fun SettingsKaiButton(
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

private val SettingsFieldColors
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
        focusedPlaceholderColor = KaiInkMuted,
        unfocusedPlaceholderColor = KaiInkMuted,
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelDialog(
    initial: SavedLlm?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
) {
    var providerId by remember { mutableStateOf(initial?.providerId ?: "xai") }
    var apiUrl by remember { mutableStateOf(initial?.resolvedBaseUrl() ?: llmProvider("xai").baseUrl) }
    var apiKey by remember { mutableStateOf(initial?.apiKey.orEmpty()) }
    var modelName by remember { mutableStateOf(initial?.model ?: llmProvider(providerId).defaultModel) }
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var showKey by remember { mutableStateOf(false) }
    val provider = llmProvider(providerId)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaiCard,
        title = {
            Text(
                if (initial == null) "Add a model" else "Edit model",
                color = KaiInk,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    placeholder = { Text("Optional") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = SettingsFieldColors,
                )
                Text("Provider", color = KaiInkMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (choice in llmProviders) {
                        val on = choice.id == providerId
                        val face = if (on) KaiButter else KaiCardNest
                        val ink = if (on) KaiAccentLo else KaiInk
                        val border = if (on) KaiAccent else KaiBorder
                        Text(
                            choice.label,
                            color = ink,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier
                                .clip(KaiChipShape)
                                .background(face)
                                .border(1.5.dp, border, KaiChipShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = {
                                        val previous = llmProvider(providerId)
                                        providerId = choice.id
                                        if (apiUrl.isBlank() || apiUrl.trimEnd('/') == previous.baseUrl.trimEnd('/')) {
                                            apiUrl = choice.baseUrl
                                        }
                                        if (modelName.isBlank() || modelName == previous.defaultModel) {
                                            modelName = choice.defaultModel
                                        }
                                    },
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = apiUrl,
                    onValueChange = { apiUrl = it },
                    label = { Text("API URL") },
                    placeholder = { Text(provider.baseUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = SettingsFieldColors,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = SettingsFieldColors,
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "Hide" else "Show", color = KaiAccentLo)
                        }
                    },
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model") },
                    placeholder = { Text(provider.defaultModel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = SettingsFieldColors,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(providerId, apiKey, modelName, label, apiUrl) }) {
                Text("Save", color = KaiAccentLo, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = KaiInkMuted) }
        },
    )
}

@Composable
private fun McpServerTile(
    server: McpServerConfig,
    status: String?,
    onToggle: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(KaiTileShape)
            .background(if (server.enabled) KaiMint.copy(alpha = 0.35f) else KaiCardNest)
            .border(1.5.dp, if (server.enabled) KaiMintDeep.copy(alpha = 0.6f) else KaiBorder, KaiTileShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    server.name,
                    color = KaiInk,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    server.url + if (server.token.isNotBlank()) " · 🔒 auth" else "",
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SettingsSoftChip(if (server.enabled) "On" else "Off", onToggle, danger = !server.enabled)
        }
        if (status != null) {
            val bad = status.startsWith("Failed")
            Text(
                status,
                color = if (bad) Brick else Color(0xFF3A6B2E),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsSoftChip("Test", onTest)
            SettingsSoftChip("Edit", onEdit)
            SettingsSoftChip("Remove", onRemove, danger = true)
        }
    }
}

@Composable
private fun McpServerDialog(
    initial: McpServerConfig?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var header by remember { mutableStateOf(initial?.authHeader.orEmpty()) }
    var token by remember { mutableStateOf(initial?.token.orEmpty()) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var showToken by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaiCard,
        title = {
            Text(
                if (initial == null) "Add an MCP server" else "Edit MCP server",
                color = KaiInk,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. github") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = SettingsFieldColors,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://example.com/mcp") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = SettingsFieldColors,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Bearer token / header value") },
                    placeholder = { Text("Optional") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = SettingsFieldColors,
                    trailingIcon = {
                        TextButton(onClick = { showToken = !showToken }) {
                            Text(if (showToken) "Hide" else "Show", color = KaiAccentLo)
                        }
                    },
                )
                OutlinedTextField(
                    value = header,
                    onValueChange = { header = it },
                    label = { Text("Auth header name") },
                    placeholder = { Text("Authorization (Bearer)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = SettingsFieldColors,
                )
                Text(
                    "Leave the header blank to send Authorization: Bearer <token>. Set a name like X-API-Key to send the value as-is.",
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                CapabilityToggleRow(
                    emoji = "🔌",
                    title = "Enabled",
                    detail = "Offer this server's tools to agents",
                    face = KaiMint,
                    enabled = enabled,
                    onToggle = { enabled = !enabled },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, url, header, token, enabled) }) {
                Text("Save", color = KaiAccentLo, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = KaiInkMuted) }
        },
    )
}
