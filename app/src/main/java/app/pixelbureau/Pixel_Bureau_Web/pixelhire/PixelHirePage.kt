package app.pixelbureau.Pixel_Bureau_Web.pixelhire

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pixelbureau.R
import app.pixelbureau.Pixel_Bureau_Web.shared.SoftGameButton
import app.pixelbureau.Pixel_Bureau_Web.shared.WebSearchField
import app.pixelbureau.ui.*
import app.pixelbureau.logic.HireCandidate
import app.pixelbureau.logic.HireRoleAll
import app.pixelbureau.logic.HireRoleGeneral
import app.pixelbureau.logic.AgentHireActLoopMs
import app.pixelbureau.logic.AgentHireBobLoopMs
import app.pixelbureau.logic.AgentHireCard1Left
import app.pixelbureau.logic.AgentHireCard1Top
import app.pixelbureau.logic.AgentHireCard2Left
import app.pixelbureau.logic.AgentHireCard2Top
import app.pixelbureau.logic.AgentHireCard3Left
import app.pixelbureau.logic.AgentHireCard3Top
import app.pixelbureau.logic.AgentHireCloudArtW
import app.pixelbureau.logic.AgentHireCloudLoopMs
import app.pixelbureau.logic.AgentHireGreeterLeft
import app.pixelbureau.logic.AgentHireGreeterTop
import app.pixelbureau.logic.AgentHireHandLeft
import app.pixelbureau.logic.AgentHireHandTop
import app.pixelbureau.logic.AgentHireHireBottom
import app.pixelbureau.logic.AgentHireHireLeft
import app.pixelbureau.logic.AgentHireLidLLeft
import app.pixelbureau.logic.AgentHireLidLTop
import app.pixelbureau.logic.AgentHireLidRLeft
import app.pixelbureau.logic.AgentHireLidRTop
import app.pixelbureau.logic.AgentHireLogoLeft
import app.pixelbureau.logic.AgentHireLogoLoopMs
import app.pixelbureau.logic.AgentHireLogoTop
import app.pixelbureau.logic.AgentHireSteamLoopMs
import app.pixelbureau.logic.AgentHireSteamTop
import app.pixelbureau.logic.AgentHireSteamX
import app.pixelbureau.logic.AgentHireTitleLoopMs
import app.pixelbureau.logic.agentHireBlink
import app.pixelbureau.logic.agentHireBob
import app.pixelbureau.logic.agentHireCloudWrap
import app.pixelbureau.logic.agentHireHandPose
import app.pixelbureau.logic.agentHireLogo
import app.pixelbureau.logic.agentHireSteam
import app.pixelbureau.logic.agentHireTitleScale
import app.pixelbureau.logic.heroTileLayout
import app.pixelbureau.logic.candidatesNamed
import app.pixelbureau.logic.hireRoleFilters
import app.pixelbureau.logic.OfficeMap
import app.pixelbureau.logic.hiredLook
import app.pixelbureau.logic.matches
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
    "yuki" to R.drawable.hire_portrait_yuki,
    "minjun" to R.drawable.hire_portrait_minjun,
    "wei" to R.drawable.hire_portrait_wei,
    "sakura" to R.drawable.hire_portrait_sakura,
    "jia" to R.drawable.hire_portrait_jia,
    "haruto" to R.drawable.hire_portrait_haruto,
    "soojin" to R.drawable.hire_portrait_soojin,
    "chen" to R.drawable.hire_portrait_chen,
    "hana" to R.drawable.hire_portrait_hana,
    "bo" to R.drawable.hire_portrait_bo,
    "ren" to R.drawable.hire_portrait_ren,
    "lin" to R.drawable.hire_portrait_lin,
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
 * Soft Kairosoft hire desk. Post a job, then the next bureau day HR brings
 * back 3–6 people from a mix of roles. Role chips filter that group.
 * All Roles is the opening chip. A hire still needs a free desk and uses [hiredLook].
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
    val day = ui.bureau?.day
    val free = (cap - active).coerceAtLeast(0)
    val canOpen = cap < OfficeMap.deskCount && stars >= 1
    val canHire = free > 0
    val posting = ui.hirePosting
    val waitingOnHr = posting.postedDay > 0 && !posting.delivered

    var query by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(HireRoleAll) }
    var deck by remember { mutableStateOf(0) }
    var reading by remember { mutableStateOf<String?>(null) }
    val rosterNames = ui.agents.filter { !it.archived }.map { it.name }.toSet()
    val batch = candidatesNamed(posting.names).filter { it.name !in rosterNames }
    val waiting = batch.filter { it.matches(query, role) }
    val sections = if (role == HireRoleAll) {
        val order = listOf(HireRoleGeneral) + hireRoleFilters.filter { it != HireRoleAll }
        order.mapNotNull { label ->
            val people = waiting.filter { it.role.equals(label, ignoreCase = true) }
            val dealt = if (deck == 0) people else people.shuffled(Random(deck * 31 + label.hashCode()))
            if (dealt.isEmpty()) null else label to dealt
        }
    } else {
        val dealt = if (deck == 0) waiting else waiting.shuffled(Random(deck))
        if (dealt.isEmpty()) emptyList() else listOf(role to dealt)
    }

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
        AgentHireBanner()

        WebSearchField(query, "Search for skills, roles, or keywords...") { query = it }

        val roleIcons = remember(context) {
            roleIconRes.mapValues { (_, resId) ->
                BitmapFactory.decodeResource(context.resources, resId).asImageBitmap()
            }
        }
        RoleChipRow(roleIcons = roleIcons, selected = role, onSelect = { picked ->
            role = picked
            deck = 0
            reading = null
        })

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
            title = "Post a job",
        ) {
            Text(
                when {
                    waitingOnHr ->
                        "Posted on day ${posting.postedDay}. HR is out recruiting. Candidates arrive on day ${posting.postedDay + 1}."
                    posting.delivered && batch.isEmpty() ->
                        "This batch has joined the bureau. Post another job and a new group arrives the next day."
                    posting.delivered ->
                        "This list is the group HR brought back. Post another job to start a new search. The current list clears, and a new group arrives the next day."
                    else ->
                        "Post a job. A day later, HR brings back 3 to 6 candidates from a mix of roles."
                },
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SoftGameButton(
                    label = when {
                        waitingOnHr -> "Waiting · day ${posting.postedDay + 1}"
                        posting.delivered -> "Post another job"
                        else -> "Post a job"
                    },
                    enabled = day != null && !waitingOnHr,
                    face = KaiAccent,
                    highlight = KaiAccentHi,
                    shadow = KaiAccentLo,
                    ink = Ink,
                    onClick = {
                        deck = 0
                        reading = null
                        query = ""
                        role = HireRoleAll
                        model.postJob()
                    },
                )
            }
        }

        HirePanel(
            wash = Color(0xFFF4F8FC),
            number = "2",
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
                val deskScene = remember(context) {
                    BitmapFactory.decodeResource(context.resources, R.drawable.hire_desk_scene).asImageBitmap()
                }
                Image(
                    bitmap = deskScene,
                    contentDescription = "A desk ready to open",
                    modifier = Modifier.width(112.dp).height(56.dp),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
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
            number = "3",
            numberFace = StepOrange,
            title = if (role == HireRoleAll) "All Candidates" else "$role Candidates",
            trailing = if (posting.delivered && batch.isNotEmpty()) {
                {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                deck += 1
                                reading = null
                            },
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("↻", color = PoolBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Shuffle",
                        color = PoolBlue,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                }
            } else {
                null
            },
        ) {
            if (posting.delivered && !canHire) {
                Text(
                    "No free desk yet. Open one with a star above, or fire someone on the Team list to free a paid slot.",
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp,
                )
            }
            if (waiting.isEmpty()) {
                Text(
                    when {
                        posting.postedDay <= 0 ->
                            "Post a job above. The list fills in a day later."
                        !posting.delivered ->
                            "No candidates yet. HR reports back on day ${posting.postedDay + 1}."
                        batch.isEmpty() ->
                            "Everyone from this posting is already on the team."
                        query.isNotBlank() ->
                            "No one matches. Try another word, or another role."
                        else ->
                            "No one in this batch is $role."
                    },
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "${waiting.size} waiting. Tap a card to read how they work, then hire.",
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp,
                )
            }
            for ((label, people) in sections) {
                if (role == HireRoleAll) {
                    RoleShelf(label, people.size)
                }
                for (candidate in people) {
                    CandidateCard(
                        candidate = candidate,
                        portrait = portraits[candidate.portraitKey],
                        canHire = canHire,
                        expanded = reading == candidate.name,
                        onToggle = {
                            reading = if (reading == candidate.name) null else candidate.name
                        },
                        onHire = {
                            model.hire(
                                candidate.name,
                                candidate.instructions,
                                candidate.outputPersonality,
                                candidate.hiredLook(),
                            )
                            if (reading == candidate.name) reading = null
                        },
                    )
                }
            }
        }
    }
}

private const val BannerTwoPi = 6.2831855f

/**
 * AgentHire banner: the original 2000x667 art extracted into loop layers
 * (see scripts/extract_hire_layers.py). The structural rest frame is
 * bit-identical to the source banner; over it run ambient loops (card and
 * greeter bob, wrap-scrolling clouds, laptop glow, coffee steam, "Hire"
 * breathe) plus a 12s acting timeline for the planted agent: blinks plus
 * stepped integer hand poses.
 */
@Composable
private fun AgentHireBanner() {
    val context = LocalContext.current
    val base = remember { decodeHireLayer(context, R.drawable.agent_hire_base) }
    val clouds = remember { decodeHireLayer(context, R.drawable.agent_hire_clouds) }
    val card1 = remember { decodeHireLayer(context, R.drawable.agent_hire_card1) }
    val card2 = remember { decodeHireLayer(context, R.drawable.agent_hire_card2) }
    val card3 = remember { decodeHireLayer(context, R.drawable.agent_hire_card3) }
    val greeter = remember { decodeHireLayer(context, R.drawable.agent_hire_greeter) }
    val hand = remember { decodeHireLayer(context, R.drawable.agent_hire_hand) }
    val lidL = remember { decodeHireLayer(context, R.drawable.agent_hire_lid_l) }
    val lidR = remember { decodeHireLayer(context, R.drawable.agent_hire_lid_r) }
    val logo = remember { decodeHireLayer(context, R.drawable.agent_hire_logo) }
    val steam = remember { decodeHireLayer(context, R.drawable.agent_hire_steam) }
    val hire = remember { decodeHireLayer(context, R.drawable.agent_hire_hire) }
    val bannerRatio = base.width / base.height.toFloat()
    val loop = rememberInfiniteTransition(label = "agentHireBanner")
    val bobPhase by loop.animateFloat(
        initialValue = 0f,
        targetValue = BannerTwoPi,
        animationSpec = infiniteRepeatable(animation = tween(AgentHireBobLoopMs, easing = LinearEasing)),
        label = "agentHireBob",
    )
    val hirePhase by loop.animateFloat(
        initialValue = 0f,
        targetValue = BannerTwoPi,
        animationSpec = infiniteRepeatable(animation = tween(AgentHireTitleLoopMs, easing = LinearEasing)),
        label = "agentHireWord",
    )
    val logoPhase by loop.animateFloat(
        initialValue = 0f,
        targetValue = BannerTwoPi,
        animationSpec = infiniteRepeatable(animation = tween(AgentHireLogoLoopMs, easing = LinearEasing)),
        label = "agentHireLogo",
    )
    val cloudProgress by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(AgentHireCloudLoopMs, easing = LinearEasing)),
        label = "agentHireCloud",
    )
    val actProgress by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(AgentHireActLoopMs, easing = LinearEasing)),
        label = "agentHireAct",
    )
    val steamProgress by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(AgentHireSteamLoopMs, easing = LinearEasing)),
        label = "agentHireSteam",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(bannerRatio)
            .semantics { contentDescription = "Agent Hire. Find Amazing Agents. Grow Your Bureau." },
    ) {
        val scale = size.width / base.width.toFloat()
        val full = IntSize(size.width.roundToInt(), size.height.roundToInt())
        drawImage(
            base,
            dstOffset = IntOffset.Zero,
            dstSize = full,
            filterQuality = FilterQuality.None,
        )
        val tile = heroTileLayout(agentHireCloudWrap(cloudProgress), AgentHireCloudArtW, full.width)
        drawImage(
            clouds,
            dstOffset = IntOffset(tile.firstX, 0),
            dstSize = IntSize(tile.firstW.coerceAtLeast(1), full.height),
            filterQuality = FilterQuality.None,
        )
        drawImage(
            clouds,
            dstOffset = IntOffset(tile.secondX, 0),
            dstSize = IntSize(tile.secondW, full.height),
            filterQuality = FilterQuality.None,
        )
        val glow = agentHireLogo(logoPhase)
        if (glow > 0.005f) {
            drawImage(
                logo,
                dstOffset = IntOffset(
                    (AgentHireLogoLeft * scale).roundToInt(),
                    (AgentHireLogoTop * scale).roundToInt(),
                ),
                dstSize = IntSize(
                    (logo.width * scale).roundToInt(),
                    (logo.height * scale).roundToInt(),
                ),
                alpha = glow,
                filterQuality = FilterQuality.None,
            )
        }
        for (i in 0..2) {
            val wisp = agentHireSteam(steamProgress, i)
            if (wisp.alpha > 0.005f) {
                val wispW = (steam.width * wisp.scale * scale).roundToInt().coerceAtLeast(1)
                val wispH = (steam.height * wisp.scale * scale).roundToInt().coerceAtLeast(1)
                drawImage(
                    steam,
                    dstOffset = IntOffset(
                        ((AgentHireSteamX + wisp.dx) * scale - wispW / 2f).roundToInt(),
                        ((AgentHireSteamTop + wisp.dy) * scale - wispH / 2f).roundToInt(),
                    ),
                    dstSize = IntSize(wispW, wispH),
                    alpha = wisp.alpha,
                    filterQuality = FilterQuality.None,
                )
            }
        }
        val bob = (agentHireBob(bobPhase) * scale).roundToInt()
        fun bobbed(image: ImageBitmap, left: Int, top: Int) {
            drawImage(
                image,
                dstOffset = IntOffset((left * scale).roundToInt(), (top * scale).roundToInt() + bob),
                dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
                filterQuality = FilterQuality.None,
            )
        }
        bobbed(card1, AgentHireCard1Left, AgentHireCard1Top)
        bobbed(card2, AgentHireCard2Left, AgentHireCard2Top)
        bobbed(card3, AgentHireCard3Left, AgentHireCard3Top)
        drawImage(
            greeter,
            dstOffset = IntOffset(
                (AgentHireGreeterLeft * scale).roundToInt(),
                (AgentHireGreeterTop * scale).roundToInt(),
            ),
            dstSize = IntSize(
                (greeter.width * scale).roundToInt(),
                (greeter.height * scale).roundToInt(),
            ),
            filterQuality = FilterQuality.None,
        )
        val pose = agentHireHandPose(actProgress)
        drawImage(
            hand,
            dstOffset = IntOffset(
                (AgentHireHandLeft * scale).roundToInt() + (pose.x * scale).roundToInt(),
                (AgentHireHandTop * scale).roundToInt() + (pose.y * scale).roundToInt(),
            ),
            dstSize = IntSize(
                (hand.width * scale).roundToInt(),
                (hand.height * scale).roundToInt(),
            ),
            filterQuality = FilterQuality.None,
        )
        val blink = agentHireBlink(actProgress)
        if (blink > 0.005f) {
            fun lid(image: ImageBitmap, left: Int, top: Int) {
                drawImage(
                    image,
                    dstOffset = IntOffset(
                        (left * scale).roundToInt(),
                        (top * scale).roundToInt(),
                    ),
                    dstSize = IntSize(
                        (image.width * scale).roundToInt(),
                        (image.height * scale).roundToInt(),
                    ),
                    alpha = blink,
                    filterQuality = FilterQuality.None,
                )
            }
            lid(lidL, AgentHireLidLLeft, AgentHireLidLTop)
            lid(lidR, AgentHireLidRLeft, AgentHireLidRTop)
        }
        // "Hire": gentle breathe anchored at the slice bottom-center, so the
        // bottom rows stay fixed while the letters swell upward.
        val breathe = agentHireTitleScale(hirePhase)
        val hireW = (hire.width * breathe * scale).roundToInt().coerceAtLeast(1)
        val hireH = (hire.height * breathe * scale).roundToInt().coerceAtLeast(1)
        val hireCx = (AgentHireHireLeft + hire.width / 2f) * scale
        val hireBottom = AgentHireHireBottom * scale
        drawImage(
            hire,
            dstOffset = IntOffset((hireCx - hireW / 2f).roundToInt(), (hireBottom - hireH).roundToInt()),
            dstSize = IntSize(hireW, hireH),
            filterQuality = FilterQuality.None,
        )
    }
}

private fun decodeHireLayer(context: android.content.Context, resId: Int): ImageBitmap =
    BitmapFactory.decodeResource(context.resources, resId).asImageBitmap()

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

@Composable
private fun RoleShelf(label: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(roleInk(label)),
        )
        Text(label, color = KaiInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text("$count", color = KaiInkMuted, fontSize = 12.sp)
    }
}

private fun roleInk(role: String): Color = when (role) {
    "Design" -> Color(0xFFC45B8A)
    "Engineering" -> PoolBlue
    "Marketing" -> StepOrange
    "Operations" -> FullTimeInk
    "Finance" -> StepBlue
    else -> Color(0xFF8A7348)
}

/** The useful sentence, skipping a short "You are…" introduction when there is one. */
private fun workLine(text: String): String {
    val parts = text.trim().split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
    val line = when {
        parts.size >= 3 && parts[0].length < 90 && parts[1].length < 48 -> parts[1] + " " + parts[2]
        parts.size >= 2 && parts[0].length < 90 -> parts[1]
        else -> parts.firstOrNull().orEmpty()
    }
    return line.take(180)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateCard(
    candidate: HireCandidate,
    portrait: android.graphics.Bitmap?,
    canHire: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onHire: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(HireTile)
            .background(Color.White)
            .border(1.5.dp, if (expanded) roleInk(candidate.role) else KaiBorder, HireTile)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onToggle,
            )
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
                "${candidate.title} · ${candidate.workplace()}",
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded) {
                FlowRow(
                    Modifier.padding(top = 4.dp),
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
                Text(
                    workLine(candidate.instructions),
                    color = KaiInk,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Voice · ${workLine(candidate.outputPersonality)}",
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StarRow(candidate.stars)
            SoftGameButton(
                label = if (canHire) "Hire" else "No desk",
                enabled = canHire,
                compact = true,
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


