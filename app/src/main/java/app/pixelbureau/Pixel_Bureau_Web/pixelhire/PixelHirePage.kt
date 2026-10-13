package app.pixelbureau.Pixel_Bureau_Web.pixelhire

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pixelbureau.R
import app.pixelbureau.Pixel_Bureau_Web.shared.SoftGameButton
import app.pixelbureau.Pixel_Bureau_Web.shared.WebSearchField
import app.pixelbureau.ui.*
import app.pixelbureau.logic.HireCandidate
import app.pixelbureau.logic.HireRoleAll
import app.pixelbureau.logic.hireRoleFilters
import app.pixelbureau.logic.OfficeMap
import app.pixelbureau.logic.hiredLook
import app.pixelbureau.logic.matches
import app.pixelbureau.logic.openingCandidatePool
import app.pixelbureau.logic.rollCandidatePool
import app.pixelbureau.logic.workplace
import kotlin.random.Random

private val HireCard = RoundedCornerShape(18.dp)
private val HireTile = RoundedCornerShape(14.dp)
private val HireBtn = RoundedCornerShape(12.dp)
private val SearchShape = RoundedCornerShape(16.dp)
private val ChipShape = RoundedCornerShape(14.dp)

private val StepBlue = Color(0xFF4C9ED9)
private val StepOrange = Color(0xFFE0923A)
private val PoolBlue = Color(0xFF3A7CA5)
private val TagFace = Color(0xFFF3E6D0)
private val FullTimeFace = Color(0xFFDFF3E0)
private val FullTimeInk = Color(0xFF3E7A45)
private val StarEmpty = Color(0xFFE4D3B0)

private val hirePortraitRes = mapOf(
    "ames" to R.drawable.hire_portrait_ames,
    "byte" to R.drawable.hire_portrait_byte,
    "cursor" to R.drawable.hire_portrait_cursor,
    "cleo" to R.drawable.hire_portrait_cleo,
    "hugo" to R.drawable.hire_portrait_hugo,
    "ivo" to R.drawable.hire_portrait_ivo,
    "june" to R.drawable.hire_portrait_june,
    "kai" to R.drawable.hire_portrait_kai,
    "leif" to R.drawable.hire_portrait_leif,
    "luna" to R.drawable.hire_portrait_luna,
    "mina" to R.drawable.hire_portrait_mina,
    "nola" to R.drawable.hire_portrait_nola,
    "ops" to R.drawable.hire_portrait_ops,
    "otto" to R.drawable.hire_portrait_otto,
    "pixel" to R.drawable.hire_portrait_pixel,
    "pax" to R.drawable.hire_portrait_pax,
    "reid" to R.drawable.hire_portrait_reid,
    "ryo" to R.drawable.hire_portrait_ryo,
    "sora" to R.drawable.hire_portrait_sora,
    "vera" to R.drawable.hire_portrait_vera,
    "you" to R.drawable.hire_portrait_you,
    "kenji" to R.drawable.hire_portrait_kenji,
    "sari" to R.drawable.hire_portrait_sari,
    "diego" to R.drawable.hire_portrait_diego,
)

private val roleIconRes = mapOf(
    "All Roles" to R.drawable.hire_role_all,
    "Design" to R.drawable.hire_role_design,
    "Engineering" to R.drawable.hire_role_engineering,
    "Marketing" to R.drawable.hire_role_marketing,
    "Operations" to R.drawable.hire_role_operations,
    "Finance" to R.drawable.hire_role_finance,
)

/**
 * Soft Kairosoft hire desk: search, role chips, then open a slot and hire
 * from the candidate pool. A hire still needs a free desk and uses [hiredLook].
 */
@Composable
internal fun PixelHirePage(
    model: BureauViewModel,
    onBack: () -> Unit,
) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val active = ui.agents.count { !it.archived }
    val cap = ui.bureau?.headcount ?: 1
    val stars = ui.bureau?.reputation ?: 0
    val free = (cap - active).coerceAtLeast(0)
    val canOpen = cap < OfficeMap.deskCount && stars >= 1
    val canHire = free > 0

    var query by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Finance") }
    var pool by remember { mutableStateOf(openingCandidatePool()) }
    var hired by remember { mutableStateOf(setOf<String>()) }
    val shown = pool.filter { it.matches(query, role) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "← Back",
            color = KaiAccentLo,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onBack,
                )
                .padding(vertical = 4.dp),
        )
        val context = LocalContext.current
        val portraits = remember(context) {
            hirePortraitRes.mapValues { (_, resId) ->
                BitmapFactory.decodeResource(context.resources, resId)
            }
        }
        val banner = remember(context) {
            BitmapFactory.decodeResource(context.resources, R.drawable.agent_hire_banner).asImageBitmap()
        }
        val bannerRatio = banner.width / banner.height.toFloat()
        Image(
            bitmap = banner,
            contentDescription = "Agent Hire. Find Amazing Agents. Grow Your Bureau.",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bannerRatio),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
        )

        WebSearchField(query, "Search for skills, roles, or keywords...") { query = it }

        val roleIcons = remember(context) {
            roleIconRes.mapValues { (_, resId) ->
                BitmapFactory.decodeResource(context.resources, resId).asImageBitmap()
            }
        }
        RoleChipRow(roleIcons = roleIcons, selected = role, onSelect = { role = it })

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatPill(
                Modifier.weight(1f),
                face = KaiButter,
                title = "Bureau ★",
                value = "$stars",
                hint = "reputation",
            )
            StatPill(
                Modifier.weight(1f),
                face = KaiMint,
                title = "Desks",
                value = "$active / $cap",
                hint = "$free open",
            )
        }

        HirePanel(
            wash = Color(0xFFF4F8FC),
            number = "1",
            numberFace = StepBlue,
            title = "Open a desk",
        ) {
            Text(
                "Each new hiring slot costs 1 bureau star. Firing later frees the slot for someone else — the star stays spent.",
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp,
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painterResource(R.drawable.hire_desk_scene),
                    contentDescription = null,
                    modifier = Modifier.width(112.dp).height(56.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.weight(1f))
                SoftGameButton(
                    label = when {
                        cap >= OfficeMap.deskCount -> "All desks open"
                        stars < 1 -> "Need a ★"
                        else -> "Open desk · ★1"
                    },
                    enabled = canOpen,
                    face = KaiAccent,
                    highlight = KaiAccentHi,
                    shadow = KaiAccentLo,
                    ink = Ink,
                    onClick = { model.openHiringSlot() },
                )
            }
        }

        HirePanel(
            wash = Color(0xFFFFF7F0),
            number = "2",
            numberFace = StepOrange,
            title = if (role == HireRoleAll) "All Candidates" else "$role Candidates",
            trailing = {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = { pool = rollCandidatePool(Random, avoid = hired, role = role) },
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("↻", color = PoolBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Refresh",
                        color = PoolBlue,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
        ) {
            if (!canHire) {
                Text(
                    "No free desk yet. Open one with a star above, or fire someone on the Team list to free a paid slot.",
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp,
                )
            }
            if (shown.isEmpty()) {
                Text(
                    "No one matches. Try another role, or refresh the pool.",
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            for (candidate in shown) {
                CandidateCard(
                    candidate = candidate,
                    portrait = portraits[candidate.portraitKey],
                    canHire = canHire,
                    onHire = {
                        model.hire(
                            candidate.name,
                            candidate.instructions,
                            candidate.outputPersonality,
                            candidate.hiredLook(),
                        )
                        val taken = hired + candidate.name
                        hired = taken
                        val next = pool.filter { it.name != candidate.name }
                        pool = if (next.isEmpty()) rollCandidatePool(Random, avoid = taken) else next
                    },
                )
            }
        }
    }
}

/** Horizontal scrolling role cards (matches Pazada's CategoryChipRow: LazyRow + edge fades). */
@Composable
private fun RoleChipRow(
    roleIcons: Map<String, androidx.compose.ui.graphics.ImageBitmap>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val showLeft by remember { androidx.compose.runtime.derivedStateOf { listState.canScrollBackward } }
    val showRight by remember { androidx.compose.runtime.derivedStateOf { listState.canScrollForward } }
    val fade = 20.dp
    androidx.compose.foundation.lazy.LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .edgeFades(showLeft, showRight, fade),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(hireRoleFilters.size, key = { hireRoleFilters[it] }) { i ->
            val filter = hireRoleFilters[i]
            RoleChip(
                icon = roleIcons.getValue(filter),
                label = filter,
                selected = selected == filter,
                onClick = { onSelect(filter) },
                modifier = Modifier.width(78.dp).height(72.dp),
            )
        }
    }
}

private fun Modifier.edgeFades(left: Boolean, right: Boolean, fade: androidx.compose.ui.unit.Dp): Modifier =
    this.then(
        Modifier.drawWithContent {
            drawContent()
            val w = fade.toPx()
            if (left) {
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(KaiPage, KaiPage.copy(alpha = 0f)), startX = 0f, endX = w,
                    ),
                    size = androidx.compose.ui.geometry.Size(w, size.height),
                )
            }
            if (right) {
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(KaiPage.copy(alpha = 0f), KaiPage), startX = size.width - w, endX = size.width,
                    ),
                    topLeft = Offset(size.width - w, 0f),
                    size = androidx.compose.ui.geometry.Size(w, size.height),
                )
            }
        },
    )

@Composable
private fun RoleChip(
    icon: androidx.compose.ui.graphics.ImageBitmap,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(ChipShape)
            .background(if (selected) KaiButter else Color.White)
            .border(if (selected) 2.5.dp else 1.5.dp, if (selected) KaiAccent else KaiBorder, ChipShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
        )
        Text(
            label,
            color = KaiInk,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatPill(
    modifier: Modifier,
    face: Color,
    title: String,
    value: String,
    hint: String,
) {
    Column(
        modifier
            .clip(HireTile)
            .background(face)
            .border(1.5.dp, KaiBorder, HireTile)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, color = KaiInkMuted, style = MaterialTheme.typography.labelMedium)
        Text(value, color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(hint, color = KaiInkMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HirePanel(
    wash: Color,
    number: String,
    numberFace: Color,
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = KaiBorderDeep.copy(alpha = 0.22f),
                    topLeft = Offset(0f, 3.dp.toPx()),
                    size = size.copy(height = size.height - 3.dp.toPx()),
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                )
            }
            .clip(HireCard)
            .background(wash)
            .border(1.5.dp, KaiBorder, HireCard),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(numberFace),
                contentAlignment = Alignment.Center,
            ) {
                Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text(
                title,
                color = KaiInk,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                trailing()
            }
        }
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = { content() },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateCard(
    candidate: HireCandidate,
    portrait: android.graphics.Bitmap?,
    canHire: Boolean,
    onHire: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(HireTile)
            .background(Color.White)
            .border(1.5.dp, KaiBorder, HireTile)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(KaiSky)
                .border(1.5.dp, KaiBorderDeep, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (portrait != null) {
                Image(
                    bitmap = portrait.asImageBitmap(),
                    contentDescription = candidate.name,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(46.dp),
                )
            } else {
                Text(
                    candidate.name.take(1),
                    color = KaiInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(candidate.name, color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                candidate.title,
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (skill in candidate.skills) {
                    Text(
                        skill,
                        color = KaiInkMuted,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(TagFace)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Full-time",
                color = FullTimeInk,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(FullTimeFace)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                HirePin(Modifier.size(10.dp), KaiInkMuted)
                Text(
                    candidate.workplace(),
                    color = KaiInkMuted,
                    fontSize = 11.sp,
                )
            }
            StarRow(candidate.stars)
            SoftGameButton(
                label = if (canHire) "Hire" else "Need a desk",
                enabled = canHire,
                face = KaiAccent,
                highlight = KaiAccentHi,
                shadow = KaiAccentLo,
                ink = Ink,
                onClick = onHire,
            )
        }
    }
}

@Composable
private fun StarRow(filled: Int) {
    val count = filled.coerceIn(0, 5)
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        repeat(5) { index ->
            Text(
                "★",
                color = if (index < count) KaiAccent else StarEmpty,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HirePin(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val r = size.minDimension * 0.28f
        val c = Offset(size.width * 0.5f, size.height * 0.34f)
        drawCircle(color, radius = r, center = c)
        drawCircle(Color.White, radius = r * 0.38f, center = c)
        drawLine(
            color,
            Offset(c.x, c.y + r * 0.2f),
            Offset(c.x, size.height * 0.92f),
            strokeWidth = size.minDimension * 0.16f,
            cap = StrokeCap.Round,
        )
    }
}

