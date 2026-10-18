package app.pixelbureau.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.pixelbureau.data.Agent
import app.pixelbureau.logic.TaskforceStatus
import app.pixelbureau.logic.displayTitle
import app.pixelbureau.logic.spriteFrame
import app.pixelbureau.sprites.SpriteStore
import kotlinx.coroutines.delay

private enum class AgentFilter(val label: String) { All("All"), Working("Working"), Idle("Idle"), Meeting("Taskforce") }

private data class AgentState(val label: String, val dot: Color, val filter: AgentFilter)

private fun agentState(agent: Agent, inTf: Boolean): AgentState = when {
    inTf -> AgentState("Meeting", Color(0xFF7C5CD6), AgentFilter.Meeting)
    agent.status == "walking" -> AgentState("Walking", Color(0xFF4A90D9), AgentFilter.Working)
    agent.status in setOf("working", "thinking", "writing", "waiting") -> AgentState("Working", Color(0xFFE2A23A), AgentFilter.Working)
    agent.status == "error" -> AgentState("Error", Color(0xFFD9534F), AgentFilter.Working)
    agent.status == "done" -> AgentState("Done", Color(0xFF34A853), AgentFilter.Working)
    else -> AgentState("Idle", Color(0xFF8FBF78), AgentFilter.Idle)
}

private const val AGENT_COLS = 4

/** Compact Kairosoft-style "Agents on floor": header stats, chips, tile grid, detail dialog. */
@Composable
internal fun FloorAgentsGrid(
    ui: BureauUi,
    active: List<Agent>,
    sprites: SpriteStore,
    onChat: (String) -> Unit,
    onOpenHr: (String) -> Unit,
) {
    val tfMembers = remember(ui.taskforces) {
        ui.taskforces.filter { TaskforceStatus.isOpen(it.data.status) }.flatMap { it.memberIds }.toSet()
    }
    val done = remember(ui.tasks) { ui.tasks.filter { it.status == "done" }.groupingBy { it.agentId }.eachCount() }
    var filter by remember { mutableStateOf(AgentFilter.All) }
    var expanded by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<String?>(null) }
    val states = active.associate { it.id to agentState(it, it.id in tfMembers) }
    val shown = active.filter { filter == AgentFilter.All || states[it.id]?.filter == filter }
    Column(
        Modifier.fillMaxWidth().animateContentSize(tween(200, easing = FastOutSlowInEasing)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AgStat("Agents", active.size, KaiButter, Modifier.weight(1f))
            AgStat("Working", states.values.count { it.filter == AgentFilter.Working }, KaiPeach, Modifier.weight(1f))
            AgStat("Idle", states.values.count { it.filter == AgentFilter.Idle }, KaiMint, Modifier.weight(1f))
            AgStat("Taskforce", states.values.count { it.filter == AgentFilter.Meeting }, KaiSky, Modifier.weight(1f))
        }
        if (active.isEmpty()) {
            EmptyHint("No agents yet. Hire from PixelHire (Pixegle → PixelHire).")
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(AgentFilter.values().toList()) { f ->
                val n = if (f == AgentFilter.All) active.size else states.values.count { it.filter == f }
                val sel = f == filter
                Text(
                    "${f.label} $n",
                    color = KaiInk,
                    fontSize = 11.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) KaiMint else KaiCardNest)
                        .border(1.5.dp, KaiBorder, RoundedCornerShape(50))
                        .clickable { filter = f; expanded = false }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        if (shown.isEmpty()) {
            EmptyHint("Nobody here right now.")
        } else {
            val rows = shown.chunked(AGENT_COLS)
            for (row in if (expanded) rows else rows.take(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (a in row) AgentTileSmall(a, states.getValue(a.id), sprites, Modifier.weight(1f)) { detail = a.id }
                    repeat(AGENT_COLS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (rows.size > 2) {
                Text(
                    if (expanded) "Show less ▲" else "See all ${shown.size} ▼",
                    color = KaiAccentLo, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50))
                        .clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
    val open = detail?.let { id -> active.firstOrNull { it.id == id } }
    if (open != null) {
        AgentDetailDialog(ui, open, states.getValue(open.id), done[open.id] ?: 0, sprites,
            onDismiss = { detail = null },
            onChat = { detail = null; onChat(open.id) },
            onOpenHr = { detail = null; onOpenHr(open.id) })
    }
}

@Composable
private fun AgStat(label: String, value: Int, face: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(face)
            .border(1.5.dp, KaiBorder, RoundedCornerShape(10.dp)).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = KaiInkMuted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun AgentTileSmall(agent: Agent, st: AgentState, sprites: SpriteStore, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(KaiCardNest)
            .border(1.5.dp, KaiBorder, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(KaiSky),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = sprites.get(spriteFrame(agent.spriteKey, "idle", 0))
            if (bmp != null) {
                // Share of the tile, not a fixed dp, so tablets and large phones
                // keep the same portrait proportion as a normal phone (~40dp there).
                Image(bmp.asImageBitmap(), agent.name, Modifier.fillMaxSize(0.58f), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
            } else {
                Text(agent.name.take(1), color = KaiInk, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(8.dp).clip(CircleShape).background(st.dot).border(1.dp, Color.White, CircleShape))
        }
        Text(agent.name, color = KaiInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            st.label, color = KaiInk, fontSize = 8.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(st.dot.copy(alpha = 0.3f)).padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun AgentDetailDialog(
    ui: BureauUi,
    agent: Agent,
    st: AgentState,
    done: Int,
    sprites: SpriteStore,
    onDismiss: () -> Unit,
    onChat: () -> Unit,
    onOpenHr: () -> Unit,
) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(agent.id) { while (true) { delay(260); frame = (frame + 1) % 4 } }
    val tfs = ui.taskforces.filter { TaskforceStatus.isOpen(it.data.status) && agent.id in it.memberIds }
    val bound = ui.models.firstOrNull { it.id == ui.agentModelIds[agent.id] }
    val llm = bound?.displayTitle() ?: ui.models.firstOrNull { it.id == ui.activeModelId }?.let { "Default · ${it.displayTitle()}" }
    val mcpPick = ui.agentMcp[agent.id]
    val mcp = ui.mcpServers.filter { it.enabled && (mcpPick == null || it.id in mcpPick) }.map { it.name }
    val role = agent.duties.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
    val location = when {
        tfs.isNotEmpty() -> "Conference room"
        st.filter == AgentFilter.Working && agent.deskIndex != null -> "At desk ${agent.deskIndex + 1}"
        agent.status == "walking" -> "Walking to a desk"
        else -> "Around the office"
    }
    var showDuties by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KaiCard)
                .border(2.dp, KaiBorderDeep, RoundedCornerShape(18.dp)).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)).background(KaiSky).border(2.dp, KaiBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = sprites.get(spriteFrame(agent.spriteKey, "idle", frame)) ?: sprites.get(spriteFrame(agent.spriteKey, "idle", 0))
                    if (bmp != null) Image(bmp.asImageBitmap(), agent.name, Modifier.size(84.dp), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(agent.name, color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (role != null) Text(role, color = KaiInkMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        st.label, color = KaiInk, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(st.dot.copy(alpha = 0.3f)).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            DetailRow("Status", "${agent.status.ifBlank { "idle" }} · $location")
            if (agent.deskIndex != null) DetailRow("Desk", "#${agent.deskIndex + 1}")
            DetailRow("Tasks done", "$done")
            if (llm != null) DetailRow("LLM API", llm)
            if (ui.mcpServers.isNotEmpty()) DetailRow("MCP", mcp.joinToString().ifBlank { "None" })
            if (tfs.isNotEmpty()) DetailRow("Taskforce", tfs.joinToString { it.data.title })
            if (agent.duties.isNotBlank()) {
                Text(
                    agent.duties.trim(), color = KaiInkMuted, fontSize = 12.sp, lineHeight = 16.sp,
                    maxLines = if (showDuties) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(KaiCardNest).clickable { showDuties = !showDuties }.padding(8.dp).fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                KaiActionButton("Open in HR", true, KaiSky, Color(0xFFE8F2FA), Color(0xFF7A9AB5), KaiInk, onOpenHr)
                KaiActionButton("Chat", true, KaiMintDeep, Color(0xFFB8DFA8), Color(0xFF5A8F4A), KaiInk, onChat)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = KaiInkMuted, fontSize = 12.sp, modifier = Modifier.size(width = 78.dp, height = 18.dp))
        Text(value, color = KaiInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}
