package app.pixelbureau.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.pixelbureau.logic.ActorSnap
import app.pixelbureau.logic.OfficeMap
import app.pixelbureau.logic.PlacedProp
import app.pixelbureau.logic.OfficeSnapshot
import app.pixelbureau.logic.Prop
import app.pixelbureau.logic.desksWithActiveScreens
import app.pixelbureau.logic.screenCodeGlyphs
import app.pixelbureau.logic.screenCursorCol
import app.pixelbureau.logic.screenCursorRow
import app.pixelbureau.logic.screenCursorVisible
import app.pixelbureau.logic.bubbleSpeechLayout
import app.pixelbureau.logic.coffeeSteamProgress
import app.pixelbureau.logic.coffeeSteamWisps

import app.pixelbureau.logic.ServerFanRoofY
import app.pixelbureau.logic.serverFanSpin
import app.pixelbureau.logic.serverLedStrength
import app.pixelbureau.logic.serverLeds
import app.pixelbureau.logic.spriteFrame
import app.pixelbureau.sprites.SpriteStore
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class OfficeCamera {
    var scale: Float = 1f
    var originX: Float = 0f
    var originY: Float = 0f
    var tileW: Float = 120f
    var tileH: Float = 60f
    var hasFit: Boolean = false
    var fitWidth: Float = 0f
    var fitHeight: Float = 0f
    private var baseScale: Float = 1f
    private var baseOriginX: Float = 0f
    private var baseOriginY: Float = 0f

    /**
     * Fit the office once, then keep that framing when the chat panel slides up
     * and the canvas gets shorter. A later pinch or pan is the new framing.
     */
    fun present(viewW: Float, viewH: Float, tileW: Float, tileH: Float, userZoom: Float, pan: Offset) {
        this.tileW = tileW
        this.tileH = tileH
        val framed = userZoom > 1.02f || abs(pan.x) > 0.5f || abs(pan.y) > 0.5f
        if (officeCameraShouldRefit(hasFit, fitWidth, fitHeight, viewW, viewH, framed)) {
            fit(viewW, viewH, tileW, tileH)
            baseScale = scale
            baseOriginX = originX
            baseOriginY = originY
            fitWidth = viewW
            fitHeight = viewH
            hasFit = true
        } else if (!hasFit) {
            return
        } else {
            scale = baseScale
            originX = baseOriginX
            originY = baseOriginY
        }
        applyView(userZoom, pan, fitWidth, fitHeight)
    }

    fun fit(viewW: Float, viewH: Float, tileW: Float, tileH: Float) {
        this.tileW = tileW
        this.tileH = tileH
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (y in 0 until OfficeMap.height) {
            for (x in 0 until OfficeMap.width) {
                val wx = (x - y) * tileW / 2f
                val wy = (x + y) * tileH / 2f
                minX = min(minX, wx)
                maxX = max(maxX, wx)
                minY = min(minY, wy)
                maxY = max(maxY, wy)
            }
        }
        minX -= tileW * 0.7f
        maxX += tileW * 0.7f
        minY -= tileH * 2.4f
        maxY += tileH * 0.9f
        val worldW = (maxX - minX).coerceAtLeast(1f)
        val worldH = (maxY - minY).coerceAtLeast(1f)
        scale = min(viewW / worldW, viewH / worldH)
        originX = (viewW - worldW * scale) / 2f - minX * scale
        originY = (viewH - worldH * scale) / 2f - minY * scale
    }

    /** Pinch zoom around the view center, then pan. Call after [fit]. */
    fun applyView(userZoom: Float, pan: Offset, viewW: Float, viewH: Float) {
        val zoom = userZoom.coerceIn(MinZoom, MaxZoom)
        val cx = viewW / 2f
        val cy = viewH / 2f
        originX = (originX - cx) * zoom + cx + pan.x
        originY = (originY - cy) * zoom + cy + pan.y
        scale *= zoom
    }

    fun toScreen(x: Float, y: Float): Offset {
        val wx = (x - y) * tileW / 2f
        val wy = (x + y) * tileH / 2f
        return Offset(originX + wx * scale, originY + wy * scale)
    }
}

/**
 * Refit when the viewport first appears at a usable height, the width really
 * changes, or an unframed map gets taller. A shorter canvas (chat panel dragged
 * up) keeps the last framing, including a small width wobble during the drag.
 */
internal fun officeCameraShouldRefit(
    hasFit: Boolean,
    fitWidth: Float,
    fitHeight: Float,
    viewW: Float,
    viewH: Float,
    userFramed: Boolean,
): Boolean {
    if (viewW < 2f || viewH < 2f) {
        return false
    }
    if (!hasFit) {
        // A covered map is a sliver. Don't lock the first framing to it.
        return viewH >= 64f
    }
    if (abs(viewW - fitWidth) > 1f) {
        // Dragging the chat panel up keeps the width and shrinks the height.
        // A 1–2px width wobble must not refit onto that sliver. A real resize
        // (rotation) still does.
        val shorter = viewH + 1f < fitHeight
        if (shorter && abs(viewW - fitWidth) <= 48f) {
            return false
        }
        return true
    }
    if (!userFramed && viewH > fitHeight + 1f) {
        return true
    }
    return false
}

private const val MinZoom = 1f
private const val MaxZoom = 4.5f

/** Laptop glass overlay — terminal phosphor on a dark shell, no per-frame bitmaps. */
private val ScreenBase = Color(0xE6081210)
private val ScreenCodeBright = Color(0xFFB6FFCE)
private val ScreenCodeDim = Color(0xFF4A9A68)
private val ScreenPrompt = Color(0xFF7CFFA8)
private val ScreenCursor = Color(0xFFD4FFE4)

/** Server rack LED phosphor — green / amber matching the packed sprite art. */
private val ServerLedGreen = Color(0xFF7CFF4A)
private val ServerLedAmber = Color(0xFFFFC43A)

/** Soft coffee steam — warm off-white wisps over the carafe. */
private val CoffeeSteam = Color(0xE8F5F0E8)
private val CoffeeSteamSoft = Color(0x66EDE6DC)




@Composable
fun OfficeCanvas(
    snapshot: OfficeSnapshot,
    sprites: SpriteStore,
    ready: Boolean,
    onTapAgent: (String) -> Unit,
    speechScale: Float = 1f,
    modifier: Modifier = Modifier,
    /** Bought shop items standing on the floor (MapPlacement). */
    placed: List<PlacedProp> = emptyList(),
    /** Work-desk art: "desk" (default) or "desk_oak" (owned Oakwood desk). */
    deskSprite: String = "desk",
    /** Conference finish: [CONF_STYLE_PREMIUM] glass, or [CONF_STYLE_OAK] boards. */
    confStyle: String = CONF_STYLE_PREMIUM,
    /** Tap on the glass conference room (floor or walls) when no agent was hit. */
    onTapRoom: (() -> Unit)? = null,
    /** Pulse a glow outline around the conference room (meeting dialog open / live taskforce). */
    roomHighlight: Boolean = false,
    /** Live taskforce on the conference TV (null = TV off). */
    meetingTv: MeetingTv? = null,
) {
    val camera = remember { OfficeCamera() }
    // TV clock: seconds since the meeting started; frame-driven only while on.
    var tvTime by remember { mutableFloatStateOf(0f) }
    val callOn = meetingTv == null && snapshot.confCall
    val tvOn = meetingTv != null || callOn
    LaunchedEffect(tvOn) {
        tvTime = 0f
        if (tvOn) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now -> tvTime = (now - start) / 1_000_000_000f }
            }
        }
    }
    val tvState = meetingTv?.let { TvState(tvTime, it.title, it.progress) }
        ?: if (callOn) TvState(tvTime, "Online meeting", 0, call = true) else null
    val latestOnTapRoom by rememberUpdatedState(onTapRoom)
    var userZoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // Actors move every sim tick — keep stable gesture detectors and read latest via UpdatedState.
    val latestActors by rememberUpdatedState(snapshot.actors)
    val latestOnTapAgent by rememberUpdatedState(onTapAgent)
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#F3E6C8")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val bubblePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#161410")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }
    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    val nextZoom = (userZoom * zoomChange).coerceIn(MinZoom, MaxZoom)
                    var nextPan = pan + panChange
                    if (nextZoom <= 1.02f) {
                        nextPan = Offset.Zero
                    } else {
                        val maxPan = min(size.width, size.height).toFloat() * (nextZoom - 1f) * 0.65f
                        nextPan = Offset(
                            nextPan.x.coerceIn(-maxPan, maxPan),
                            nextPan.y.coerceIn(-maxPan, maxPan),
                        )
                    }
                    userZoom = nextZoom
                    pan = nextPan
                }
            }
            .pointerInput(ready) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        // < ~2.0 → zoom to 2.0× centered on tap; already ≥~1.9 → reset.
                        if (userZoom >= 1.9f) {
                            userZoom = 1f
                            pan = Offset.Zero
                        } else {
                            val target = 2.0f
                            val cx = if (camera.hasFit) camera.fitWidth / 2f else size.width / 2f
                            val cy = if (camera.hasFit) camera.fitHeight / 2f else size.height / 2f
                            val z0 = userZoom.coerceAtLeast(0.01f)
                            val nextPan = Offset(
                                tap.x - cx - (tap.x - cx - pan.x) * (target / z0),
                                tap.y - cy - (tap.y - cy - pan.y) * (target / z0),
                            )
                            val maxPan = min(size.width, size.height).toFloat() * (target - 1f) * 0.65f
                            userZoom = target
                            pan = Offset(
                                nextPan.x.coerceIn(-maxPan, maxPan),
                                nextPan.y.coerceIn(-maxPan, maxPan),
                            )
                        }
                    },
                    onTap = { tap ->
                        val hit = hitAgent(latestActors, camera, tap)
                        if (hit != null) {
                            latestOnTapAgent(hit)
                        } else if (camera.hasFit && hitsConfRoom(camera, tap)) {
                            latestOnTapRoom?.invoke()
                        }
                    },
                )
            },
    ) {
        drawRect(FloorVoid)
        if (!ready) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#F3E6C8")
                textAlign = Paint.Align.CENTER
                textSize = 42f
            }
            drawContext.canvas.nativeCanvas.drawText(
                "Opening the office…",
                size.width / 2f,
                size.height / 2f,
                paint,
            )
            return@Canvas
        }
        val floor = sprites.get("tiles/floor")
        val tileW = floor?.width?.toFloat() ?: 96f
        val tileH = floor?.height?.toFloat() ?: 48f
        camera.present(size.width, size.height, tileW, tileH, userZoom, pan)
        val floorImage = floor?.asImageBitmap()
        for (y in 0 until OfficeMap.height) {
            for (x in 0 until OfficeMap.width) {
                val center = camera.toScreen(x.toFloat(), y.toFloat())
                val dw = camera.tileW * camera.scale
                val dh = camera.tileH * camera.scale
                if (floorImage != null) {
                    drawSprite(floorImage, center, dw, dh)
                } else {
                    diamond(center, dw, dh, Color(0xFFC4A574))
                }
            }
        }
        val oakRoom = confStyle == CONF_STYLE_OAK
        // Premium: cool carpet under the glass. Oakwood: scuffed board floor.
        for (cell in OfficeMap.confCells) {
            val worn = oakRoom && ((cell.x + cell.y) % 5 == 0 || cell == OfficeMap.confPlant)
            diamond(
                camera.toScreen(cell.x.toFloat(), cell.y.toFloat()),
                camera.tileW * camera.scale,
                camera.tileH * camera.scale,
                if (!oakRoom) ConfCarpet else if (worn) OakFloorWear else OakFloor,
            )
        }
        val litScreens = desksWithActiveScreens(snapshot.actors)
        val pieces = buildList {
            OfficeMap.props.forEach { add(Piece.Furniture(it)) }
            confWallSegments().forEach { add(Piece.Wall(it)) }
            add(Piece.Door(snapshot.confDoorOpen))
            add(Piece.BackDoor(snapshot.backDoorOpen))
            placed.forEach { add(Piece.Placed(it)) }
            snapshot.actors.forEach { add(Piece.Person(it)) }
        }.sortedBy { it.depth }
        // Room highlight sits in the floor/wall layer: under every agent and bubble.
        if (roomHighlight) drawConfHighlight(camera, System.currentTimeMillis(), oakRoom)
        for (piece in pieces) {
            when (piece) {
                is Piece.Furniture -> drawFurniture(piece.prop, sprites, camera, litScreens, snapshot.fridgeOpen, deskSprite, oakRoom)
                is Piece.Placed -> drawPlaced(piece.item, sprites, camera)
                is Piece.Wall -> drawConfWall(piece.seg, camera, tvState, oakRoom)
                is Piece.Door -> if (oakRoom) drawOakDoor(piece.open, camera) else drawConfDoor(piece.open, camera)
                is Piece.BackDoor -> drawBackDoor(piece.open, camera)
                is Piece.Person -> drawActor(
                    piece.actor,
                    sprites,
                    camera,
                    labelPaint,
                    bubblePaint,
                    speechScale,
                    overlay = false,
                )
            }
        }
        // Final overlay pass: names + speech bubbles above every tile, prop, door and glass wall.
        for (actor in snapshot.actors.sortedBy { it.x + it.y }) {
            drawActor(actor, sprites, camera, labelPaint, bubblePaint, speechScale, overlay = true)
        }
    }
}

/** Conference chair this sitter is on (taskforce or solo use), else null. */
private fun confSeatOf(actor: ActorSnap): app.pixelbureau.logic.Cell? =
    if ((actor.anim == "sit" || actor.anim == "type") && !actor.useSeatTuck) OfficeMap.confSeatAt(actor.x, actor.y) else null

private sealed class Piece {
    abstract val depth: Float

    data class Furniture(val prop: Prop) : Piece() {
        override val depth: Float = if (prop.kind == "sofa") {
            // Under both seat tiles so sleepers (bias above their cell) draw ON the cushions.
            OfficeMap.sofaSpan.minOf { (it.x + it.y).toFloat() } - 0.05f
        } else if (prop.kind == "foosball") {
            OfficeMap.foosballSpan.minOf { (it.x + it.y).toFloat() } - 0.05f
        } else if (prop.kind == "conf_table") {
            // Centre of the 2×2 table: north-row sitters draw first, south/east after.
            OfficeMap.confTable.map { (it.x + it.y).toFloat() }.average().toFloat()
        } else if (prop.kind == "conf_chair") {
            confChairSortDepth(prop.x, prop.y)
        } else if (prop.kind == "chair") {
            // Above a tucked desk-sitter at the same tile so the rear backrest
            // stays toward the camera (agent peeks toward the laptop past it).
            (prop.x + prop.y).toFloat() - 0.01f
        } else {
            (prop.x + prop.y).toFloat()
        }
    }

    /** Shop purchase: same depth rule as the built-in prop of that shape. */
    data class Placed(val item: PlacedProp) : Piece() {
        override val depth: Float = when (item.kind) {
            "sofa", "foosball" -> item.cells.minOf { (it.x + it.y).toFloat() } - 0.05f
            else -> (item.cell.x + item.cell.y).toFloat()
        }
    }

    /** One glass wall panel of the conference room (one tile edge). */
    data class Wall(val seg: ConfWallSeg) : Piece() {
        override val depth: Float = seg.depth
    }

    /** Office back entrance on the north edge of [OfficeMap.backDoor]; behind anyone on that tile. */
    data class BackDoor(val open: Float) : Piece() {
        override val depth: Float = (OfficeMap.backDoor.x + OfficeMap.backDoor.y).toFloat() - 0.5f
    }

    /** The glass door in the west wall; [open] is the 0..1 inward swing. */
    data class Door(val open: Float) : Piece() {
        override val depth: Float =
            (OfficeMap.confDoorInside.x + OfficeMap.confDoorInside.y).toFloat() - 0.5f
    }

    data class Person(val actor: ActorSnap) : Piece() {
        // Desk sit/type with seat tuck sort under the chair (-0.04 < chair's -0.01)
        // so the rear 3/4 backrest reads toward the camera while the agent faces
        // the laptop. Sleepers stay above the loveseat. Walk/idle stay above chairs.
        // On the ground at a conference seat, in front of that chair. Seated, the chair draws over them.
        override val depth: Float
            get() = actorSortDepth(actor.x, actor.y, actor.anim, actor.useSeatTuck)
    }
}

/**
 * Painter's order for one actor. Higher draws later, in front.
 * A person standing or walking on a conference seat sorts just in front of
 * that chair ([confChairSortDepth] is tile + 0.05 on the camera-facing seats).
 * Sit and type stay behind it, so the backrest covers the body. The standing
 * bump stays under the front glass (those walls sort at tile + 0.5).
 */
internal fun actorSortDepth(x: Float, y: Float, anim: String, useSeatTuck: Boolean): Float {
    val confSeat = OfficeMap.confSeatAt(x, y)
    val seated = anim == "sit" || anim == "type"
    if (confSeat != null && !seated) return confSeat.x + confSeat.y + 0.12f
    val bias = when (anim) {
        "sleep" -> 0.12f
        "sit", "type" -> if (useSeatTuck) -0.04f else 0.02f
        else -> 0.01f
    }
    return x + y + bias
}

/** Conference chair painter's order. North row sits behind its sitter; the others are the camera-facing seats. */
internal fun confChairSortDepth(x: Int, y: Int): Float =
    if (y == OfficeMap.confY0) (x + y) - 0.01f else (x + y) + 0.05f

private fun DrawScope.drawActor(
    actor: ActorSnap,
    sprites: SpriteStore,
    camera: OfficeCamera,
    labelPaint: Paint,
    bubblePaint: Paint,
    speechScale: Float,
    overlay: Boolean,
) {
    val frame = spriteFrame(actor.spriteKey, actor.anim, actor.frame, actor.facing)
    val bitmap = sprites.get(frame)
        ?: sprites.get("chars/${actor.spriteKey}_idle")
        ?: return
    val sleeping = actor.anim == "sleep"
    val tuck = if ((actor.anim == "sit" || actor.anim == "type") && actor.useSeatTuck) {
        OfficeMap.agentSeatTuck
    } else {
        0f
    }
    val sleepNudge = if (sleeping) OfficeMap.sleepNudgeFor(actor.x, actor.y) else null
    val confSeat = confSeatOf(actor)
    val center = if (confSeat != null) {
        // Snap onto the exact chair tile so the sitter lines up with the seat.
        OfficeMap.confChairPos(confSeat).let { (cx, cy) -> camera.toScreen(cx, cy) }
    } else if (sleepNudge != null) {
        val base = camera.toScreen(actor.x, actor.y)
        // Per-cushion screen-x so each sleeper sits on their half of the loveseat.
        Offset(base.x + sleepNudge.dxTileW * camera.tileW * camera.scale, base.y)
    } else {
        val base = camera.toScreen(actor.x - tuck, actor.y)
        val deskNestX = if ((actor.anim == "sit" || actor.anim == "type") && actor.useSeatTuck) {
            OfficeMap.deskSeatNestTileW * camera.tileW * camera.scale
        } else {
            0f
        }
        Offset(base.x + deskNestX, base.y)
    }
    // Sleep sprites are wider/shorter; scale by width so the curled body fits a cushion.
    val destH = if (sleeping) {
        camera.tileW * camera.scale * 0.42f
    } else {
        camera.tileW * camera.scale * 0.56f
    }
    val destW = destH * bitmap.width / bitmap.height.toFloat()
    // Nest onto the seat surface (negative lifts off the floor into the cushions).
    // Desk sit/type tuck uses deskSeatNestTileH so the agent sits ON the chair
    // (head/torso visible) while depth still sorts under the rear backrest.
    val nest = when {
        confSeat != null -> OfficeMap.confSeatNestTileH * camera.tileH * camera.scale
        sleepNudge != null -> sleepNudge.nestTileH * camera.tileH * camera.scale
        (actor.anim == "sit" || actor.anim == "type") && actor.useSeatTuck ->
            OfficeMap.deskSeatNestTileH * camera.tileH * camera.scale
        else -> 0f
    }
    val foot = center.y + camera.tileH * camera.scale * 0.18f + nest
    val top = foot - destH
    val spriteCenter = Offset(center.x, top + destH / 2f)
    if (!overlay) {
    // Seated on a conference chair: no floor shadow (the chair base grounds them).
    if (confSeat == null) drawOval(
        color = Color(0x55000000),
        topLeft = Offset(center.x - destW * 0.28f, foot - destH * 0.06f),
        size = androidx.compose.ui.geometry.Size(destW * 0.56f, destH * 0.12f),
    )
    // Side facing: mirror the default (front) sheet. Walk/idle "back" also use front sheets.
    // Desk sit/type: default sheets face +x; mirror when seat→laptop is -x (seatMirrorX).
    // Prefer desk geometry over the facing string so a stale facing cannot face away
    // from the laptop. Sofa sleep stays unflipped.
    val flipX = when {
        sleeping -> false
        (actor.anim == "sit" || actor.anim == "type") && actor.useSeatTuck -> {
            val desk = actor.deskIndex
            if (desk != null) OfficeMap.seatMirrorX(desk) else actor.facing == "left"
        }
        else -> actor.facing == "left"
    }
    if (flipX) {
        scale(scaleX = -1f, scaleY = 1f, pivot = spriteCenter) {
            drawSprite(bitmap.asImageBitmap(), spriteCenter, destW, destH)
        }
    } else {
        drawSprite(bitmap.asImageBitmap(), spriteCenter, destW, destH)
    }
    return
    }
    val nameSize = (camera.tileH * camera.scale * 0.38f).coerceIn(18f, 36f)
    labelPaint.textSize = nameSize
    drawContext.canvas.nativeCanvas.drawText(actor.name, center.x, top - nameSize * 0.35f, labelPaint)
    val bubble = (if (actor.modelError) "🔌 " + (actor.bubble ?: "") else actor.bubble) ?: return
    val layout = bubbleSpeechLayout(nameSize, camera.tileW * camera.scale, speechScale)
    var textSize = layout.textSize
    bubblePaint.textSize = textSize
    var textWidth = bubblePaint.measureText(bubble)
    while (textWidth + textSize > layout.maxWidth && textSize > layout.minText) {
        textSize -= 1f
        bubblePaint.textSize = textSize
        textWidth = bubblePaint.measureText(bubble)
    }
    val width = textWidth + textSize
    val bubbleBottom = top + layout.bottomFromSpriteTop
    val bubbleTop = bubbleBottom - layout.height
    // Pop-in: quick ease-out-back scale from the bubble's tail.
    val t = actor.bubblePop.coerceIn(0f, 1f)
    val c1 = 1.70158f
    val pop = if (t >= 1f) 1f else (1f + (c1 + 1f) * (t - 1f) * (t - 1f) * (t - 1f) + c1 * (t - 1f) * (t - 1f)).coerceAtLeast(0.05f)
    scale(pop, pop, pivot = Offset(center.x, bubbleBottom)) {
    if (actor.modelError) {
        // Muted warning bubble: warm cream fill, amber→brick outline with a gentle pulse.
        val pulse = 0.55f + 0.45f * ((kotlin.math.sin(System.currentTimeMillis() / 380.0) + 1.0) / 2.0).toFloat()
        drawRoundRect(
            color = Color(0xFFFFF1E2),
            topLeft = Offset(center.x - width / 2f, bubbleTop),
            size = androidx.compose.ui.geometry.Size(width, layout.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(layout.corner),
        )
        drawRoundRect(
            color = Color(0xFFC8643A).copy(alpha = pulse),
            topLeft = Offset(center.x - width / 2f, bubbleTop),
            size = androidx.compose.ui.geometry.Size(width, layout.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(layout.corner),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = (layout.textSize * 0.14f).coerceAtLeast(2f)),
        )
    } else
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(center.x - width / 2f, bubbleTop),
        size = androidx.compose.ui.geometry.Size(width, layout.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(layout.corner),
    )
    drawContext.canvas.nativeCanvas.drawText(
        bubble,
        center.x,
        bubbleTop + layout.textSize * 1.02f,
        bubblePaint,
    )
    }
}


private fun DrawScope.drawSprite(
    image: androidx.compose.ui.graphics.ImageBitmap,
    center: Offset,
    destW: Float,
    destH: Float,
) {
    val left = (center.x - destW / 2f).toInt()
    val top = (center.y - destH / 2f).toInt()
    drawImage(
        image = image,
        dstOffset = IntOffset(left, top),
        dstSize = IntSize(destW.toInt().coerceAtLeast(1), destH.toInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.None,
    )
}

private fun DrawScope.diamond(center: Offset, width: Float, height: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - height / 2f)
        lineTo(center.x + width / 2f, center.y)
        lineTo(center.x, center.y + height / 2f)
        lineTo(center.x - width / 2f, center.y)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawFurniture(
    prop: Prop,
    sprites: SpriteStore,
    camera: OfficeCamera,
    litScreens: Map<Int, Int>,
    fridgeOpen: Boolean = false,
    deskSprite: String = "desk",
    oakRoom: Boolean = false,
) {
    if (prop.kind == "desk") {
        // Owned desk type swaps the art of every work desk (exclusive group "desk").
        val art = sprites.get("props/$deskSprite")
        val key = if (art != null) deskSprite else "desk"
        val bitmap = art ?: sprites.get("props/desk") ?: return
        val box = spriteBox(bitmap, prop.x.toFloat(), prop.y.toFloat(), propScale(key), camera)
        drawSprite(bitmap.asImageBitmap(), box.center, box.w, box.h)
        val deskIndex = OfficeMap.desks.indexOfFirst { it.x == prop.x && it.y == prop.y }
        val frame = litScreens[deskIndex]
        if (frame != null) {
            drawLaptopScreen(box, frame, laptopGlassFor(key))
        }
        return
    }
    if (prop.kind == "conf_table") {
        drawConfTable(camera, oakRoom)
        return
    }
    if (prop.kind == "conf_chair") {
        val toward = 0.22f
        val (cx, cy) = when {
            prop.y == OfficeMap.confY0 -> prop.x.toFloat() to prop.y + toward
            prop.y == OfficeMap.confY0 + 3 -> prop.x.toFloat() to prop.y - toward
            else -> prop.x - toward to prop.y.toFloat()
        }
        if (oakRoom) {
            drawOakChair(camera, cx, cy, backEast = prop.x == OfficeMap.confX0 + 3)
            return
        }
        val chair = sprites.get("props/chair") ?: return
        // Nudge the chair a little toward the table so it tucks in.
        val box = spriteBox(chair, cx, cy, propScale("chair"), camera)
        // Chair art faces -x; mirror for the north / south rows that face along y.
        if (prop.x == OfficeMap.confX0 + 3) {
            drawSprite(chair.asImageBitmap(), box.center, box.w, box.h)
        } else {
            scale(scaleX = -1f, scaleY = 1f, pivot = box.center) {
                drawSprite(chair.asImageBitmap(), box.center, box.w, box.h)
            }
        }
        return
    }
    if (prop.kind == "coffee") {
        // Combined magenta-keyed lounge station (desk + machine + accessories).
        val station = sprites.get("props/coffee") ?: return
        val box = spriteBox(station, prop.x.toFloat(), prop.y.toFloat(), propScale("coffee"), camera)
        drawSprite(station.asImageBitmap(), box.center, box.w, box.h)
        drawCoffeeSteam(box, System.currentTimeMillis())
        return
    }
    // Premium Startup has no plant. The corner pot belongs to the oakwood room.
    if (!oakRoom && prop.kind == "plant" && prop.x == OfficeMap.confPlant.x && prop.y == OfficeMap.confPlant.y) {
        return
    }
    val fridgeOpenBmp = if (prop.kind == "fridge" && fridgeOpen) sprites.get("props/fridge_open") else null
    val bitmap = fridgeOpenBmp ?: sprites.get("props/${prop.kind}") ?: return
    val (x, y) = when (prop.kind) {
        "sofa" -> {
            val span = OfficeMap.sofaSpan
            span.map { it.x }.average().toFloat() to span.map { it.y }.average().toFloat()
        }
        "foosball" -> {
            val span = OfficeMap.foosballSpan
            span.map { it.x }.average().toFloat() to span.map { it.y }.average().toFloat()
        }
        "chair" -> (prop.x - OfficeMap.seatTuck) to prop.y.toFloat()
        else -> prop.x.toFloat() to prop.y.toFloat()
    }
    val scaleKind = if (fridgeOpenBmp != null) "fridge_open" else prop.kind
    val baseBox = spriteBox(bitmap, x, y, propScale(scaleKind), camera)
    val box = if (prop.kind == "fridge") {
        val offset = OfficeMap.fridgeDrawOffset
        // Open art is wider (door to the right). Match cabinet height, then shift
        // right so the body stays put and the door grows outward.
        val doorShift = if (fridgeOpenBmp != null) {
            (propScale("fridge_open") - propScale("fridge")) * camera.tileW * camera.scale / 2f
        } else {
            0f
        }
        baseBox.copy(
            center = Offset(
                baseBox.center.x + offset.dxTileW * camera.tileW * camera.scale + doorShift,
                baseBox.center.y + offset.dyTileH * camera.tileH * camera.scale,
            ),
        )
    } else {
        baseBox
    }
    drawSprite(bitmap.asImageBitmap(), box.center, box.w, box.h)
    if (prop.kind == "server") {
        val now = System.currentTimeMillis()
        drawServerLeds(box, now)
        val spin = serverFanSpin(now)
        val frame = if (spin != null) sprites.get("props/server_fan_frame") else null
        val rotor = if (spin != null) sprites.get("props/server_fan_rotor") else null
        if (spin != null && frame != null && rotor != null) {
            drawServerFan(
                box,
                frame,
                rotor,
                spin.degrees,
                spin.alpha,
                sprites.serverFanPivotX,
                sprites.serverFanPivotY,
            )
        }
    }
}

/** Laptop glass quad as fractions of the cropped desk sprite (TL, TR, BR, BL). */
private data class LaptopGlass(
    val tlx: Float, val tly: Float,
    val trx: Float, val try_: Float,
    val brx: Float, val bry: Float,
    val blx: Float, val bly: Float,
)

// Glass quad remapped for v28 desk-only packed reference (wallpaper+dock hull inset → crop fracs).
private val DeskGlass = LaptopGlass(0.3629f, 0.1710f, 0.5553f, 0.1094f, 0.5735f, 0.2908f, 0.3784f, 0.3761f)

// Same laptop pasted onto the oak desk (scripts/gen_desk_oak.py prints these fractions).
private val OakGlass = LaptopGlass(0.1709f, 0.1050f, 0.4337f, 0.0296f, 0.4586f, 0.2517f, 0.1921f, 0.3561f)

private fun laptopGlassFor(deskSprite: String): LaptopGlass = if (deskSprite == "desk_oak") OakGlass else DeskGlass

/**
 * Bought item on its MapPlacement cell. Multi-tile kinds centre on their
 * footprint like the built-in sofa/foosball; everything else stands on the
 * anchor tile. Never animated except cheap overlays (coffee steam, server LEDs).
 */
private fun DrawScope.drawPlaced(item: PlacedProp, sprites: SpriteStore, camera: OfficeCamera) {
    val bitmap = sprites.get("props/${item.kind}") ?: return
    val (x, y) = when (item.kind) {
        "sofa", "foosball" -> item.cells.map { it.x }.average().toFloat() to item.cells.map { it.y }.average().toFloat()
        else -> item.cell.x.toFloat() to item.cell.y.toFloat()
    }
    val box = spriteBox(bitmap, x, y, propScale(item.kind), camera)
    drawSprite(bitmap.asImageBitmap(), box.center, box.w, box.h)
    when (item.kind) {
        "coffee" -> drawCoffeeSteam(box, System.currentTimeMillis())
        "server" -> drawServerLeds(box, System.currentTimeMillis())
    }
}

/**
 * Canvas overlay on the isometric laptop glass. Fractions are relative to the
 * cropped desk sprite (keyAndCrop). Quad tuned for the v28 desk-only reference
 * laptop (true-alpha source → magenta key; colorful wallpaper idle; terminal overlay while typing).
 */
private fun DrawScope.drawLaptopScreen(box: SpriteBox, frame: Int, glass: LaptopGlass = DeskGlass) {
    val left = box.center.x - box.w * 0.5f
    val top = box.center.y - box.h * 0.5f
    val tl = Offset(left + box.w * glass.tlx, top + box.h * glass.tly)
    val tr = Offset(left + box.w * glass.trx, top + box.h * glass.try_)
    val br = Offset(left + box.w * glass.brx, top + box.h * glass.bry)
    val bl = Offset(left + box.w * glass.blx, top + box.h * glass.bly)
    val glass = Path().apply {
        moveTo(tl.x, tl.y)
        lineTo(tr.x, tr.y)
        lineTo(br.x, br.y)
        lineTo(bl.x, bl.y)
        close()
    }
    clipPath(glass) {
        // Dark terminal base covers the wallpaper while an agent types.
        drawPath(glass, ScreenBase)
        val rows = 6
        val glyphs = screenCodeGlyphs(frame, rows)
        val stroke = (box.h * 0.011f).coerceIn(1.1f, 2.6f)
        for (glyph in glyphs) {
            val t = (glyph.row + 0.5f) / rows
            val x0 = tl.x * (1f - t) + bl.x * t
            val y0 = tl.y * (1f - t) + bl.y * t
            val x1 = tr.x * (1f - t) + br.x * t
            val y1 = tr.y * (1f - t) + br.y * t
            val sx = x0 * (1f - glyph.start) + x1 * glyph.start
            val sy = y0 * (1f - glyph.start) + y1 * glyph.start
            val ex = x0 * (1f - glyph.end) + x1 * glyph.end
            val ey = y0 * (1f - glyph.end) + y1 * glyph.end
            val color = when {
                glyph.prompt -> ScreenPrompt
                glyph.bright -> ScreenCodeBright
                else -> ScreenCodeDim
            }
            drawLine(
                color = color,
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = if (glyph.prompt) stroke * 1.15f else stroke,
            )
        }
        // Blinking block cursor at the end of the active typed line.
        if (screenCursorVisible(frame)) {
            val row = screenCursorRow(frame, rows)
            val col = screenCursorCol(frame, rows)
            val t = (row + 0.5f) / rows
            val x0 = tl.x * (1f - t) + bl.x * t
            val y0 = tl.y * (1f - t) + bl.y * t
            val x1 = tr.x * (1f - t) + br.x * t
            val y1 = tr.y * (1f - t) + br.y * t
            val cx = x0 * (1f - col) + x1 * col
            val cy = y0 * (1f - col) + y1 * col
            // Along-row unit for a short block; across-row for thickness.
            val rowDx = (x1 - x0)
            val rowDy = (y1 - y0)
            val rowLen = kotlin.math.sqrt(rowDx * rowDx + rowDy * rowDy).coerceAtLeast(1f)
            val block = (rowLen * 0.035f).coerceIn(2f, 5f)
            val ux = rowDx / rowLen
            val uy = rowDy / rowLen
            // Across-row (TL→BL) for cursor height.
            val across = (box.h * 0.018f).coerceIn(1.6f, 3.5f)
            val acrossDx = (bl.x - tl.x)
            val acrossDy = (bl.y - tl.y)
            val acrossLen = kotlin.math.sqrt(acrossDx * acrossDx + acrossDy * acrossDy).coerceAtLeast(1f)
            val ax = acrossDx / acrossLen * across * 0.5f
            val ay = acrossDy / acrossLen * across * 0.5f
            drawLine(
                color = ScreenCursor,
                start = Offset(cx - ax, cy - ay),
                end = Offset(cx + ax + ux * block, cy + ay + uy * block),
                strokeWidth = block,
            )
        }
    }
}

/**
 * Small cooler on the rack roof. The square housing stays still; only the blade disk spins.
 * [pivotX] and [pivotY] are the housing center as fractions of the fan bitmap.
 */
private fun DrawScope.drawServerFan(
    box: SpriteBox,
    frame: Bitmap,
    rotor: Bitmap,
    degrees: Float,
    alpha: Float,
    pivotX: Float,
    pivotY: Float,
) {
    if (alpha < 0.04f) {
        return
    }
    val width = box.w * 0.34f
    val height = width * frame.height / frame.width.toFloat()
    val cx = box.center.x
    val cy = box.center.y - box.h * 0.5f + box.h * ServerFanRoofY
    val left = (cx - width / 2f).toInt()
    val top = (cy - height / 2f).toInt()
    val dst = IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1))
    val offset = IntOffset(left, top)
    drawImage(
        image = frame.asImageBitmap(),
        dstOffset = offset,
        dstSize = dst,
        filterQuality = FilterQuality.None,
        alpha = alpha,
    )
    val pivot = Offset(left + dst.width * pivotX, top + dst.height * pivotY)
    rotate(degrees = degrees, pivot = pivot) {
        drawImage(
            image = rotor.asImageBitmap(),
            dstOffset = offset,
            dstSize = dst,
            filterQuality = FilterQuality.None,
            alpha = alpha,
        )
    }
}

/**
 * Overlay blink dots on the single-rack. Quads are crop-relative (keyAndCrop),
 * same pattern as [drawLaptopScreen]. Long bar / square glow overlays removed.
 */
private fun DrawScope.drawServerLeds(box: SpriteBox, nowMs: Long) {
    val left = box.center.x - box.w * 0.5f
    val top = box.center.y - box.h * 0.5f
    for (led in serverLeds()) {
        val strength = serverLedStrength(led, nowMs)
        if (strength < 0.05f) continue
        val l = left + box.w * led.left
        val t = top + box.h * led.top
        val r = left + box.w * led.right
        val b = top + box.h * led.bottom
        val w = (r - l).coerceAtLeast(1f)
        val h = (b - t).coerceAtLeast(1f)
        val core = if (led.green) ServerLedGreen else ServerLedAmber
        // Indicator dot: tiny bright square when lit.
        val alpha = (0.15f + 0.85f * strength).coerceIn(0f, 1f)
        drawRect(
            color = core.copy(alpha = alpha),
            topLeft = Offset(l, t),
            size = androidx.compose.ui.geometry.Size(w.coerceAtLeast(1.2f), h.coerceAtLeast(1.2f)),
        )
    }
}

/**
 * Rising steam wisps from the carafe / drip head. Same crop-relative overlay
 * pattern as [drawServerLeds] / laptop screen anim — no extra bitmaps.
 */
private fun DrawScope.drawCoffeeSteam(box: SpriteBox, nowMs: Long) {
    val left = box.center.x - box.w * 0.5f
    val top = box.center.y - box.h * 0.5f
    for (wisp in coffeeSteamWisps()) {
        val t = coffeeSteamProgress(wisp, nowMs)
        // Fade in, hold, fade out across the rise.
        val alpha = when {
            t < 0.12f -> t / 0.12f
            t > 0.72f -> ((1f - t) / 0.28f).coerceAtLeast(0f)
            else -> 1f
        } * 0.75f
        if (alpha < 0.04f) continue
        val rise = wisp.rise * t
        // Gentle sine sway so neighbors look independent.
        val sway = kotlin.math.sin((t * 6.283f) + wisp.phase * 6.283f) * wisp.drift
        val cx = left + box.w * (wisp.x + sway)
        val cy = top + box.h * (wisp.y - rise)
        val radius = (box.w * wisp.size * (0.65f + 0.55f * t)).coerceIn(1.4f, 9f)
        drawCircle(
            color = CoffeeSteam.copy(alpha = alpha),
            radius = radius,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = CoffeeSteamSoft.copy(alpha = alpha * 0.55f),
            radius = radius * 1.55f,
            center = Offset(cx + radius * 0.15f, cy - radius * 0.35f),
        )
    }
}

private data class SpriteBox(val center: Offset, val w: Float, val h: Float)

private fun spriteBox(bitmap: Bitmap, x: Float, y: Float, scale: Float, camera: OfficeCamera): SpriteBox {
    val anchor = camera.toScreen(x, y)
    val width = camera.tileW * camera.scale * scale
    val height = width * bitmap.height / bitmap.width.toFloat()
    val feet = anchor.y + camera.tileH * camera.scale * 0.08f
    return SpriteBox(Offset(anchor.x, feet - height / 2f), width, height)
}

private fun propScale(kind: String): Float = when (kind) {
    // Work desks: packed ~963×786 v28 desk-only reference — push draw scale so agents look small beside it.
    // Combined coffee station (~1343×1089 keyed) spans coffeeSpan; scale fits the lounge tiles.
    "desk" -> 1.18f
    "desk_bare" -> 0.70f
    "chair" -> 0.68f
    "sofa" -> 1.22f
    "server" -> 0.93f
    "plant" -> 0.42f
    "plant2" -> 0.50f
    "coffee" -> 1.20f
    "whiteboard" -> 0.88f
    "bookshelf" -> 0.82f
    // Orange beanbag ref (tight magenta pack like fridge); single-tile lounge cushion.
    "beanbag" -> 0.70f
    "fridge" -> 0.78f
    // Open door packed wider; scale keeps the cabinet the same height as closed.
    "fridge_open" -> 0.957f
    "watercooler" -> 0.54f
    "foosball" -> 1.08f
    "trash" -> 0.48f
    // Oak desk: bbox is 0.73× the default desk, so scale up to keep it from hiding behind the chair.
    "desk_oak" -> 1.08f
    "desk_top" -> 0.95f
    "bike" -> 0.62f
    "plant_top" -> 0.45f
    // v0.1.61 Pazada stock: 0.82 × (art bbox width / iso tile width) so tall pieces stay near fridge/bookshelf height.
    "aquarium" -> 0.61f
    "arcade" -> 0.56f
    "cactus" -> 0.37f
    "clock" -> 0.44f
    "filing_cabinet" -> 0.49f
    "floor_lamp" -> 0.40f
    "globe" -> 0.40f
    "guitar" -> 0.36f
    "neon_sign" -> 0.37f
    "picture_frame" -> 0.34f
    "pouf" -> 0.43f
    "printer" -> 0.56f
    "storage_box" -> 0.58f
    "vending" -> 0.54f
    else -> 0.5f
}

private fun hitAgent(actors: List<ActorSnap>, camera: OfficeCamera, tap: Offset): String? {
    var best: String? = null
    var bestDistance = Float.POSITIVE_INFINITY
    for (actor in actors) {
        val sleeping = actor.anim == "sleep"
        val tuck = if ((actor.anim == "sit" || actor.anim == "type") && actor.useSeatTuck) {
            OfficeMap.agentSeatTuck
        } else {
            0f
        }
        val sleepNudge = if (sleeping) OfficeMap.sleepNudgeFor(actor.x, actor.y) else null
        val confSeat = confSeatOf(actor)
        val center = if (confSeat != null) {
            val base = OfficeMap.confChairPos(confSeat).let { (cx, cy) -> camera.toScreen(cx, cy) }
            Offset(base.x, base.y + OfficeMap.confSeatNestTileH * camera.tileH * camera.scale)
        } else if (sleepNudge != null) {
            val base = camera.toScreen(actor.x, actor.y)
            Offset(
                base.x + sleepNudge.dxTileW * camera.tileW * camera.scale,
                base.y + sleepNudge.nestTileH * camera.tileH * camera.scale,
            )
        } else {
            val base = camera.toScreen(actor.x - tuck, actor.y)
            val deskNest = if ((actor.anim == "sit" || actor.anim == "type") && actor.useSeatTuck) {
                OfficeMap.deskSeatNestTileH * camera.tileH * camera.scale
            } else {
                0f
            }
            val deskNestX = if ((actor.anim == "sit" || actor.anim == "type") && actor.useSeatTuck) {
                OfficeMap.deskSeatNestTileW * camera.tileW * camera.scale
            } else {
                0f
            }
            Offset(base.x + deskNestX, base.y + deskNest)
        }
        val reach = camera.tileW * camera.scale * 0.7f
        val dx = tap.x - center.x
        val dy = tap.y - (center.y - reach * 0.4f)
        val distance = abs(dx) + abs(dy)
        if (distance < reach && distance < bestDistance) {
            best = actor.id
            bestDistance = distance
        }
    }
    return best
}


// ---------------------------------------------------------------------------
// Glass conference room (bottom-right). Walls, door and table are drawn as
// isometric vector pixel-art so they line up with the tile grid exactly;
// chairs and the plant reuse the existing prop sprites.
// ---------------------------------------------------------------------------

private val ConfCarpet = Color(0x2A1E2A36)
private val ConfFrame = Color(0xFF1C1F24)
private val ConfFrameHi = Color(0xFF3A3F47)
private val ConfGlass = Color(0x3D9ED4EC)
private val ConfGlassShine = Color(0x55E8F8FF)
private val ConfPanel = Color(0xFFD8C2A2)
private val ConfPanelDark = Color(0xFFBFA684)
private val ConfBlackTop = Color(0xFF17191F)
private val ConfBlackEdge = Color(0xFF3C4350)
private val ConfBlackSide = Color(0xFF0C0E12)
private val ConfSheen = Color(0x668E98A8)
private val ConfHubBody = Color(0xFF101216)
private val ConfHubFace = Color(0xFF2A2F38)
private val ConfHubRim = Color(0xFFB7C0CE)
private val ConfHubLed = Color(0xFF3DDCFF)
private val ConfTv = Color(0xFF15181D)
private val ConfTvGlass = Color(0xFF26303A)

/** Dream Starter oakwood: unfinished boards, no glass, short front walls. */
private val OakPlank = Color(0xFFD7B07A)
private val OakPlankDark = Color(0xFFC49558)
private val OakSeam = Color(0xFFA8743E)
private val OakPost = Color(0xFF6E4524)
private val OakKnot = Color(0xFF8C5A2C)
private val OakFloor = Color(0x66C9A066)
private val OakFloorWear = Color(0x55906A3A)
private val OakTableTop = Color(0xFFE0C08A)
private val OakTableEdge = Color(0xFFB8884E)
private val OakTableSide = Color(0xFF9A6A38)
private const val OakBackH = 1.15f
private const val OakFrontH = 0.46f

/** Wall height in tileH units (screen). */
private const val ConfWallH = 1.7f

/** A wall edge from grid point (ax, ay) to (bx, by); [back] walls get the panel wainscot. */
internal data class ConfWallSeg(
    val ax: Float, val ay: Float, val bx: Float, val by: Float,
    val back: Boolean, val depth: Float, val decor: String? = null,
)

internal fun confWallSegments(): List<ConfWallSeg> {
    val x0 = OfficeMap.confX0
    val y0 = OfficeMap.confY0
    val n = OfficeMap.confSize
    val x1 = x0 + n - 1
    val y1 = y0 + n - 1
    val door = OfficeMap.confDoorInside
    return buildList {
        for (x in x0..x1) {
            // North (back) wall; TV on the second panel.
            add(ConfWallSeg(x - 0.5f, y0 - 0.5f, x + 0.5f, y0 - 0.5f, back = true,
                depth = x + y0 - 0.5f, decor = if (x == x0 + 1) "tv" else null))
            // South (front) wall.
            add(ConfWallSeg(x - 0.5f, y1 + 0.5f, x + 0.5f, y1 + 0.5f, back = false,
                depth = x + y1 + 0.5f))
        }
        for (y in y0..y1) {
            // West (back, office-facing) wall with the door gap; whiteboard beside it.
            if (y != door.y) {
                add(ConfWallSeg(x0 - 0.5f, y - 0.5f, x0 - 0.5f, y + 0.5f, back = true,
                    depth = x0 + y - 0.5f, decor = if (y == y0 + 1) "chart" else null))
            }
            // East (front) wall.
            add(ConfWallSeg(x1 + 0.5f, y - 0.5f, x1 + 0.5f, y + 0.5f, back = false,
                depth = x1 + y + 0.5f))
        }
    }
}

private fun quad(a: Offset, b: Offset, c: Offset, d: Offset): Path = Path().apply {
    moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
}

/** Point on the wall plane: grid (gx, gy) lifted [h] tileH units up the screen. */
private fun OfficeCamera.wallPt(gx: Float, gy: Float, h: Float): Offset {
    val p = toScreen(gx, gy)
    return Offset(p.x, p.y - h * tileH * scale)
}

private fun DrawScope.drawGlassPanel(
    camera: OfficeCamera,
    ax: Float, ay: Float, bx: Float, by: Float,
    back: Boolean,
    alpha: Float = 1f,
) {
    val stroke = (camera.tileW * camera.scale * 0.035f).coerceAtLeast(1.5f)
    val a0 = camera.wallPt(ax, ay, 0f)
    val b0 = camera.wallPt(bx, by, 0f)
    val a1 = camera.wallPt(ax, ay, ConfWallH)
    val b1 = camera.wallPt(bx, by, ConfWallH)
    val panelH = if (back) ConfWallH * 0.42f else 0f
    if (back) {
        val am = camera.wallPt(ax, ay, panelH)
        val bm = camera.wallPt(bx, by, panelH)
        drawPath(quad(a0, b0, bm, am), ConfPanel.copy(alpha = alpha))
        // Vertical seam in the panel.
        val s0 = camera.wallPt((ax + bx) / 2f, (ay + by) / 2f, 0f)
        val s1 = camera.wallPt((ax + bx) / 2f, (ay + by) / 2f, panelH)
        drawLine(ConfPanelDark.copy(alpha = alpha), s0, s1, stroke * 0.5f)
        drawPath(quad(am, bm, b1, a1), ConfGlass.copy(alpha = ConfGlass.alpha * alpha))
        drawLine(ConfFrame.copy(alpha = alpha), am, bm, stroke * 0.8f)
    } else {
        drawPath(quad(a0, b0, b1, a1), ConfGlass.copy(alpha = ConfGlass.alpha * alpha))
        val am = camera.wallPt(ax, ay, ConfWallH * 0.5f)
        val bm = camera.wallPt(bx, by, ConfWallH * 0.5f)
        drawLine(ConfFrameHi.copy(alpha = alpha * 0.7f), am, bm, stroke * 0.5f)
    }
    // Diagonal shine streak.
    val sh0 = camera.wallPt(ax + (bx - ax) * 0.25f, ay + (by - ay) * 0.25f, ConfWallH * 0.92f)
    val sh1 = camera.wallPt(ax + (bx - ax) * 0.45f, ay + (by - ay) * 0.45f, maxOf(panelH, 0f) + ConfWallH * 0.1f)
    drawLine(ConfGlassShine.copy(alpha = ConfGlassShine.alpha * alpha), sh0, sh1, stroke * 0.7f)
    // Dark metal frame: bottom rail, top rail, posts.
    drawLine(ConfFrame.copy(alpha = alpha), a0, b0, stroke)
    drawLine(ConfFrame.copy(alpha = alpha), a1, b1, stroke * 1.2f)
    drawLine(ConfFrame.copy(alpha = alpha), a0, a1, stroke)
    drawLine(ConfFrame.copy(alpha = alpha), b0, b1, stroke)
}

/** Outer TV bezel quad on the north wall (TV panel x0+1 overhangs into the next panel). */
private fun tvBezel(camera: OfficeCamera): Path {
    val x = OfficeMap.confX0 + 1; val gy = OfficeMap.confY0 - 0.5f
    fun at(t: Float, h: Float) = camera.wallPt(x - 0.5f + t, gy, h)
    return quad(at(0.12f, 1.42f), at(1.18f, 1.42f), at(1.18f, 0.84f), at(0.12f, 0.84f))
}

private fun DrawScope.drawConfWall(seg: ConfWallSeg, camera: OfficeCamera, tv: TvState? = null, oak: Boolean = false) {
    if (oak) {
        drawOakWall(seg, camera, tv)
        return
    }
    // The panel right of the TV draws later (higher depth); cut the TV out of it so
    // its post/glass/shine never crosses the monitor.
    val tvNeighbour = seg.back && seg.ay == OfficeMap.confY0 - 0.5f && seg.ax == OfficeMap.confX0 + 1.5f
    if (tvNeighbour) {
        clipPath(tvBezel(camera), androidx.compose.ui.graphics.ClipOp.Difference) {
            drawGlassPanel(camera, seg.ax, seg.ay, seg.bx, seg.by, seg.back)
        }
    } else drawGlassPanel(camera, seg.ax, seg.ay, seg.bx, seg.by, seg.back)
    val stroke = (camera.tileW * camera.scale * 0.03f).coerceAtLeast(1.2f)
    fun at(t: Float, h: Float) = camera.wallPt(seg.ax + (seg.bx - seg.ax) * t, seg.ay + (seg.by - seg.ay) * t, h)
    when (seg.decor) {
        "tv" -> {
            val tl = at(0.12f, 1.42f); val tr = at(1.18f, 1.42f)
            val br = at(1.18f, 0.84f); val bl = at(0.12f, 0.84f)
            if (tv != null) {
                // Soft screen glow spilling onto the wall around the TV.
                val g = (0.55f + 0.08f * kotlin.math.sin(tv.t * 3f)).coerceIn(0f, 1f) * tvPower(tv.t)
                drawPath(quad(at(-0.1f, 1.62f), at(1.4f, 1.62f), at(1.4f, 0.62f), at(-0.1f, 0.62f)), Color(0xFF7FD3FF).copy(alpha = 0.16f * g))
                drawPath(quad(at(0.02f, 1.52f), at(1.28f, 1.52f), at(1.28f, 0.74f), at(0.02f, 0.74f)), Color(0xFF9FE4FF).copy(alpha = 0.22f * g))
            }
            drawPath(quad(tl, tr, br, bl), ConfTv)
            val i = 0.06f
            val gtl = at(0.12f + i, 1.42f - i); val gtr = at(1.18f - i, 1.42f - i)
            val gbl = at(0.12f + i, 0.84f + i)
            drawPath(quad(gtl, gtr, at(1.18f - i, 0.84f + i), gbl), if (tv == null) Color(0xFF111418) else ConfTvGlass)
            if (tv != null) drawTvSlides(tv, gtl, gtr, gbl)
            // Camera bar on top.
            drawLine(ConfFrame, at(0.55f, 1.47f), at(0.75f, 1.47f), stroke * 1.4f)
            // Power LED.
            drawCircle(if (tv != null) Color(0xFF5CF08A) else Color(0xFFB0392B), stroke * 0.6f, at(1.12f, 0.87f))
        }
        "chart" -> {
            // Motivational "TEAMWORK" pixel poster (same footprint as the old chart).
            drawPath(quad(at(0.14f, 1.43f), at(0.9f, 1.43f), at(0.9f, 0.84f), at(0.14f, 0.84f)), Color(0x33000000))
            drawPixelPoster(at(0.88f, 1.45f), at(0.12f, 1.45f), at(0.88f, 0.86f))  // West wall runs right-to-left on screen: start at t=0.88 so text reads correctly.
        }
    }
}

/**
 * Glass door in the west wall. Hinged on the north jamb; [open] 0..1 swings
 * the free edge INTO the room (+x) by up to 90°, so the panel ends up lying
 * along the inside of the north side of the doorway.
 */
private fun DrawScope.drawConfDoor(open: Float, camera: OfficeCamera) {
    val inside = OfficeMap.confDoorInside
    val hx = inside.x - 0.5f
    val hy = inside.y - 0.5f
    val theta = (open.coerceIn(0f, 1f) * 90f) * (Math.PI.toFloat() / 180f)
    // Ease so the swing reads as weighty glass (slow in, slow out).
    val fx = hx + kotlin.math.sin(theta) * 0.92f
    val fy = hy + kotlin.math.cos(theta) * 0.92f
    drawGlassPanel(camera, hx, hy, fx, fy, back = false, alpha = 0.95f)
    // Long vertical pull handle near the free edge.
    val stroke = (camera.tileW * camera.scale * 0.03f).coerceAtLeast(1.2f)
    val tx = hx + (fx - hx) * 0.82f
    val ty = hy + (fy - hy) * 0.82f
    drawLine(Color(0xFFB8C0C8), camera.wallPt(tx, ty, 0.55f), camera.wallPt(tx, ty, 1.05f), stroke * 1.4f)
    // Door frame header across the opening (always there).
    drawLine(ConfFrame, camera.wallPt(hx, hy, ConfWallH), camera.wallPt(hx, hy + 1f, ConfWallH), stroke * 1.6f)
    drawLine(ConfFrame, camera.wallPt(hx, hy + 1f, 0f), camera.wallPt(hx, hy + 1f, ConfWallH), stroke * 1.4f)
}

/**
 * Dream Starter walls: solid oak planks, no glass. Back walls are shorter than
 * the premium glass; front walls are pony-height so the table stays visible.
 */
private fun DrawScope.drawOakWall(seg: ConfWallSeg, camera: OfficeCamera, tv: TvState?) {
    val h = if (seg.back) OakBackH else OakFrontH
    val stroke = (camera.tileW * camera.scale * 0.035f).coerceAtLeast(1.5f)
    val a0 = camera.wallPt(seg.ax, seg.ay, 0f)
    val b0 = camera.wallPt(seg.bx, seg.by, 0f)
    val a1 = camera.wallPt(seg.ax, seg.ay, h)
    val b1 = camera.wallPt(seg.bx, seg.by, h)
    val mismatched = ((seg.ax + seg.ay) * 2f).toInt() % 3 == 0
    drawPath(quad(a0, b0, b1, a1), if (mismatched) OakPlankDark else OakPlank)
    for (k in 1..3) {
        val hh = h * k / 4f
        drawLine(OakSeam, camera.wallPt(seg.ax, seg.ay, hh), camera.wallPt(seg.bx, seg.by, hh), stroke * 0.45f)
    }
    if (seg.back) {
        val knot = camera.wallPt(
            seg.ax + (seg.bx - seg.ax) * 0.62f,
            seg.ay + (seg.by - seg.ay) * 0.62f,
            h * 0.36f,
        )
        drawCircle(OakKnot, stroke * 1.15f, knot)
    }
    drawLine(OakPost, a0, b0, stroke * 1.5f)
    drawLine(OakPost, a1, b1, stroke * 2.2f)
    drawLine(OakPost, a0, a1, stroke * 2.0f)
    drawLine(OakPost, b0, b1, stroke * 2.0f)
    val strokeThin = (camera.tileW * camera.scale * 0.03f).coerceAtLeast(1.2f)
    fun at(t: Float, lift: Float) = camera.wallPt(
        seg.ax + (seg.bx - seg.ax) * t,
        seg.ay + (seg.by - seg.ay) * t,
        lift,
    )
    when (seg.decor) {
        "tv" -> {
            // Small wood-cased set, sat low. Screen still runs the meeting slides.
            val tl = at(0.22f, 0.98f); val tr = at(0.82f, 0.98f)
            val br = at(0.82f, 0.52f); val bl = at(0.22f, 0.52f)
            drawPath(quad(at(0.16f, 1.04f), at(0.88f, 1.04f), at(0.88f, 0.46f), at(0.16f, 0.46f)), OakPost)
            drawPath(quad(tl, tr, br, bl), ConfTv)
            val i = 0.05f
            val gtl = at(0.22f + i, 0.98f - i); val gtr = at(0.82f - i, 0.98f - i)
            val gbl = at(0.22f + i, 0.52f + i)
            drawPath(quad(gtl, gtr, at(0.82f - i, 0.52f + i), gbl), if (tv == null) Color(0xFF2A241C) else Color(0xFF3A3428))
            if (tv != null) drawTvSlides(tv, gtl, gtr, gbl)
            drawCircle(if (tv != null) Color(0xFF5CF08A) else Color(0xFFB0392B), strokeThin * 0.55f, at(0.74f, 0.56f))
        }
        "chart" -> {
            // Crooked scrap taped to the board, instead of the framed poster.
            val sheet = quad(at(0.22f, 0.92f), at(0.78f, 0.86f), at(0.84f, 0.48f), at(0.16f, 0.52f))
            drawPath(sheet, Color(0xFFF3E4C0))
            drawLine(Color(0xFFD8C48A), at(0.30f, 0.90f), at(0.46f, 0.88f), strokeThin * 1.6f)
            drawLine(Color(0xFFD8C48A), at(0.58f, 0.84f), at(0.72f, 0.82f), strokeThin * 1.4f)
            drawLine(OakPost, at(0.26f, 0.78f), at(0.70f, 0.74f), strokeThin * 0.4f)
            drawLine(OakPost, at(0.26f, 0.66f), at(0.62f, 0.62f), strokeThin * 0.4f)
            drawLine(OakPost, at(0.26f, 0.56f), at(0.50f, 0.54f), strokeThin * 0.35f)
        }
    }
}

/** Solid oak slab. Same hinge and swing as the glass door, shorter and plain. */
private fun DrawScope.drawOakDoor(open: Float, camera: OfficeCamera) {
    val inside = OfficeMap.confDoorInside
    val hx = inside.x - 0.5f
    val hy = inside.y - 0.5f
    val theta = (open.coerceIn(0f, 1f) * 90f) * (Math.PI.toFloat() / 180f)
    val fx = hx + kotlin.math.sin(theta) * 0.92f
    val fy = hy + kotlin.math.cos(theta) * 0.92f
    val doorH = 1.02f
    val stroke = (camera.tileW * camera.scale * 0.035f).coerceAtLeast(1.5f)
    val a0 = camera.wallPt(hx, hy, 0f)
    val b0 = camera.wallPt(fx, fy, 0f)
    val a1 = camera.wallPt(hx, hy, doorH)
    val b1 = camera.wallPt(fx, fy, doorH)
    drawPath(quad(a0, b0, b1, a1), OakPlank)
    val aM = camera.wallPt(hx, hy, doorH * 0.30f)
    val bM = camera.wallPt(fx, fy, doorH * 0.30f)
    drawPath(quad(a0, b0, bM, aM), OakPlankDark)
    for (k in 1..3) {
        val t = k / 4f
        val sx = hx + (fx - hx) * t
        val sy = hy + (fy - hy) * t
        drawLine(OakSeam, camera.wallPt(sx, sy, 0.05f), camera.wallPt(sx, sy, doorH - 0.04f), stroke * 0.4f)
    }
    val kx = hx + (fx - hx) * 0.78f
    val ky = hy + (fy - hy) * 0.78f
    drawCircle(OakPost, stroke * 1.25f, camera.wallPt(kx, ky, 0.48f))
    drawLine(OakPost, a0, a1, stroke * 1.8f)
    drawLine(OakPost, b0, b1, stroke * 1.5f)
    drawLine(OakPost, a1, b1, stroke * 1.6f)
    drawLine(OakPost, camera.wallPt(hx, hy, OakBackH), camera.wallPt(hx, hy + 1f, OakBackH), stroke * 2.4f)
    drawLine(OakPost, camera.wallPt(hx, hy + 1f, 0f), camera.wallPt(hx, hy + 1f, OakBackH), stroke * 2.0f)
}

/** Four-leg oak chair with one slat. [backEast] puts the slat on the +x side. */
private fun DrawScope.drawOakChair(camera: OfficeCamera, x: Float, y: Float, backEast: Boolean) {
    val seat = 0.32f
    val top = 0.70f
    val s = 0.22f
    val stroke = (camera.tileW * camera.scale * 0.028f).coerceAtLeast(1.2f)
    val feet = listOf(
        x - s to y - s,
        x + s to y - s,
        x + s to y + s,
        x - s to y + s,
    )
    for ((lx, ly) in feet) {
        drawLine(OakPost, camera.wallPt(lx, ly, 0.02f), camera.wallPt(lx, ly, seat), stroke * 1.25f)
    }
    drawPath(
        quad(
            camera.wallPt(x - s, y - s, seat),
            camera.wallPt(x + s, y - s, seat),
            camera.wallPt(x + s, y + s, seat),
            camera.wallPt(x - s, y + s, seat),
        ),
        OakPlank,
    )
    val (p0, p1) = if (backEast) {
        (x + s to y - s) to (x + s to y + s)
    } else {
        (x - s to y + s) to (x + s to y + s)
    }
    drawLine(OakPost, camera.wallPt(p0.first, p0.second, seat), camera.wallPt(p0.first, p0.second, top), stroke * 1.4f)
    drawLine(OakPost, camera.wallPt(p1.first, p1.second, seat), camera.wallPt(p1.first, p1.second, top), stroke * 1.4f)
    drawLine(
        OakPlankDark,
        camera.wallPt(p0.first, p0.second, top - 0.08f),
        camera.wallPt(p1.first, p1.second, top - 0.08f),
        stroke * 2.2f,
    )
}

/**
 * NE office entrance, styled like the conference room's glass walls: a black
 * metal frame with a glass transom and glass side-lights either side of a
 * double glass sliding door (light tint, diagonal reflection streaks, pull
 * bars), a small lit ENTRANCE plate in the transom, and a doormat.
 * [open] 0..1 (smoothstep-eased here) slides the leaves apart behind the
 * side-lights. Default closed.
 */
private fun DrawScope.drawBackDoor(open: Float, camera: OfficeCamera) {
    val c = OfficeMap.backDoor
    val ax = c.x - 0.5f; val bx = c.x + 0.5f; val y = c.y - 0.5f
    val top = ConfWallH
    val doorH = 1.28f
    val side = 0.26f
    val s1 = ax + side; val s2 = bx - side; val mid = (s1 + s2) / 2f
    val stroke = (camera.tileW * camera.scale * 0.035f).coerceAtLeast(1.5f)
    fun p(x: Float, h: Float) = camera.wallPt(x, y, h)
    val t = open.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    // Doormat.
    val m0 = ax + 0.14f; val m1 = bx - 0.14f
    drawPath(quad(camera.wallPt(m0, y + 0.06f, 0f), camera.wallPt(m1, y + 0.06f, 0f),
        camera.wallPt(m1, y + 0.56f, 0f), camera.wallPt(m0, y + 0.56f, 0f)), Color(0xFF4B535C))
    drawPath(quad(camera.wallPt(m0 + 0.05f, y + 0.11f, 0f), camera.wallPt(m1 - 0.05f, y + 0.11f, 0f),
        camera.wallPt(m1 - 0.05f, y + 0.51f, 0f), camera.wallPt(m0 + 0.05f, y + 0.51f, 0f)), Color(0xFF2A2F35))
    // Lit lobby beyond the doorway.
    drawPath(quad(p(s1, 0f), p(s2, 0f), p(s2, doorH), p(s1, doorH)), Color(0xFFCFE3EC))
    drawPath(quad(p(s1, 0f), p(s2, 0f), p(s2, 0.16f), p(s1, 0.16f)), Color(0xFFA9BCC6))
    // Sliding leaves (drawn first so the side-light glass overlays them when open).
    val slide = t * (mid - s1) * 0.9f
    fun leaf(l: Float, r: Float, barX: Float) {
        drawPath(quad(p(l, 0f), p(r, 0f), p(r, doorH), p(l, doorH)), ConfGlass.copy(alpha = 0.42f))
        drawLine(ConfGlassShine, p(l + (r - l) * 0.2f, doorH * 0.9f), p(l + (r - l) * 0.62f, doorH * 0.18f), stroke * 0.7f)
        drawLine(ConfGlassShine.copy(alpha = ConfGlassShine.alpha * 0.6f), p(l + (r - l) * 0.5f, doorH * 0.95f), p(l + (r - l) * 0.8f, doorH * 0.55f), stroke * 0.45f)
        drawLine(ConfFrame, p(l, 0f), p(r, 0f), stroke * 1.2f)
        drawLine(ConfFrame, p(l, doorH), p(r, doorH), stroke)
        drawLine(ConfFrame, p(l, 0f), p(l, doorH), stroke)
        drawLine(ConfFrame, p(r, 0f), p(r, doorH), stroke)
        drawLine(Color(0xFFC9D1D9), p(barX, 0.42f), p(barX, 0.98f), stroke * 1.3f)
    }
    leaf(s1 - slide, mid - slide, mid - slide - 0.05f)
    leaf(mid + slide, s2 + slide, mid + slide + 0.05f)
    // Side-lights + transom (same glass + streak treatment as the conference walls).
    drawGlassPanel(camera, ax, y, s1, y, back = false)
    drawGlassPanel(camera, s2, y, bx, y, back = false)
    drawPath(quad(p(s1, doorH), p(s2, doorH), p(s2, top), p(s1, top)), ConfGlass)
    drawLine(ConfGlassShine, p(s1 + 0.08f, top * 0.97f), p(s1 + 0.22f, doorH + 0.04f), stroke * 0.6f)
    // Black frame: outer posts, head, transom bar, side-light mullions.
    drawLine(ConfFrame, p(ax, 0f), p(ax, top), stroke * 1.5f)
    drawLine(ConfFrame, p(bx, 0f), p(bx, top), stroke * 1.5f)
    drawLine(ConfFrame, p(ax, top), p(bx, top), stroke * 1.6f)
    drawLine(ConfFrame, p(ax, doorH), p(bx, doorH), stroke * 1.5f)
    drawLine(ConfFrame, p(s1, 0f), p(s1, top), stroke * 1.2f)
    drawLine(ConfFrame, p(s2, 0f), p(s2, top), stroke * 1.2f)
    drawLine(ConfFrame, p(ax, 0f), p(bx, 0f), stroke * 1.4f)
    // Lit ENTRANCE plate centred in the transom.
    val e0 = mid - 0.2f; val e1 = mid + 0.2f; val eb = doorH + 0.1f; val et = doorH + 0.28f
    drawPath(quad(p(e0 - 0.02f, et + 0.02f), p(e1 + 0.02f, et + 0.02f), p(e1 + 0.02f, eb - 0.02f), p(e0 - 0.02f, eb - 0.02f)), Color(0x5543F2B0))
    drawPath(quad(p(e0, et), p(e1, et), p(e1, eb), p(e0, eb)), Color(0xFF16212A))
    val glyph = Color(0xFF7CF5C4)
    for (k in 0 until 8) {
        val gx = e0 + 0.04f + k * 0.04f
        drawLine(glyph, p(gx, eb + 0.05f), p(gx, et - 0.05f), stroke * 0.5f)
    }
    drawLine(glyph, p(e0 + 0.04f, et - 0.05f), p(e1 - 0.05f, et - 0.05f), stroke * 0.35f)
}

/** What the conference TV shows while a taskforce meets. */
data class MeetingTv(val title: String, val progress: Int)

internal data class TvState(val t: Float, val title: String, val progress: Int, val call: Boolean = false)

/** Video-call grid: 2×2 tiles of faces, the active speaker outlined, mic/cam bar. */
private fun drawTvCall(c: android.graphics.Canvas, p: android.graphics.Paint, t: Float, W: Float, H: Float) {
    p.color = android.graphics.Color.rgb(28, 32, 40); c.drawRect(0f, 0f, W, H, p)
    val speaker = ((t / 1.8f).toInt()) % 4
    val skins = intArrayOf(android.graphics.Color.rgb(241, 194, 158), android.graphics.Color.rgb(198, 134, 94),
        android.graphics.Color.rgb(250, 214, 180), android.graphics.Color.rgb(141, 85, 56))
    val bgs = intArrayOf(android.graphics.Color.rgb(58, 92, 130), android.graphics.Color.rgb(70, 110, 80),
        android.graphics.Color.rgb(110, 76, 120), android.graphics.Color.rgb(120, 96, 60))
    val hair = intArrayOf(android.graphics.Color.rgb(40, 30, 24), android.graphics.Color.rgb(90, 60, 30),
        android.graphics.Color.rgb(200, 160, 80), android.graphics.Color.rgb(20, 20, 20))
    val tw = (W - 12f) / 2f; val th = (H - 18f) / 2f
    for (i in 0 until 4) {
        val x = 4f + (i % 2) * (tw + 4f); val y = 4f + (i / 2) * (th + 4f)
        p.style = android.graphics.Paint.Style.FILL
        p.color = bgs[i]; c.drawRoundRect(x, y, x + tw, y + th, 3f, 3f, p)
        val cx = x + tw / 2f; val cy = y + th * 0.52f; val r = th * 0.26f
        p.color = skins[i]; c.drawCircle(cx, cy, r, p)
        p.color = hair[i]; c.drawArc(cx - r, cy - r * 1.05f, cx + r, cy + r * 0.6f, 180f, 180f, true, p)
        p.color = android.graphics.Color.rgb(30, 30, 30)
        c.drawCircle(cx - r * 0.35f, cy + r * 0.05f, r * 0.09f, p); c.drawCircle(cx + r * 0.35f, cy + r * 0.05f, r * 0.09f, p)
        // Talking mouth on the active speaker.
        val open = if (i == speaker) (0.08f + 0.1f * kotlin.math.abs(kotlin.math.sin(t * 9f))) else 0.04f
        c.drawOval(cx - r * 0.22f, cy + r * 0.38f, cx + r * 0.22f, cy + r * (0.42f + open * 2f), p)
        p.color = skins[i]; c.drawRect(cx - r * 0.7f, y + th - 3f, cx + r * 0.7f, y + th, p)
        if (i == speaker) {
            p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 2f
            p.color = android.graphics.Color.rgb(124, 214, 124); c.drawRoundRect(x, y, x + tw, y + th, 3f, 3f, p)
            p.style = android.graphics.Paint.Style.FILL
        }
    }
    // Bottom control bar: mic, cam, red hang-up.
    p.color = android.graphics.Color.rgb(16, 18, 22); c.drawRect(0f, H - 9f, W, H, p)
    p.color = android.graphics.Color.rgb(200, 205, 214); c.drawCircle(W / 2f - 12f, H - 4.5f, 2.8f, p); c.drawCircle(W / 2f, H - 4.5f, 2.8f, p)
    p.color = android.graphics.Color.rgb(226, 72, 72); c.drawCircle(W / 2f + 12f, H - 4.5f, 2.8f, p)
}

/** 0..1 power-on: dark → white scanline flicker → full over ~0.6 s. */
private fun tvPower(t: Float): Float = when {
    t < 0.12f -> 0f
    t < 0.5f -> if (((t * 30f).toInt() % 3) == 0) 0.35f else 0.85f
    else -> (0.85f + (t - 0.5f) * 1.5f).coerceAtMost(1f)
}

/** Slide length on the meeting TV. */
private const val TvSlideSec = 2.5f

/**
 * Presentation slides drawn in a flat 160×90 space mapped onto the (sheared)
 * TV glass via polyToPoly: bar chart growing, line chart, pie, bullet list
 * with the taskforce title, progress bar. Starts with a power-on flicker.
 */
private fun DrawScope.drawTvSlides(tv: TvState, tl: Offset, tr: Offset, bl: Offset) {
    val W = 160f; val H = 90f
    val m = android.graphics.Matrix()
    m.setPolyToPoly(floatArrayOf(0f, 0f, W, 0f, 0f, H), 0, floatArrayOf(tl.x, tl.y, tr.x, tr.y, bl.x, bl.y), 0, 3)
    val c = drawContext.canvas.nativeCanvas
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    c.save()
    c.concat(m)
    val power = tvPower(tv.t)
    if (tv.t < 0.5f) {
        // Power-on: bright scanline opening from the centre.
        p.color = android.graphics.Color.argb((255 * power).toInt(), 230, 248, 255)
        val k = (tv.t / 0.5f).coerceIn(0f, 1f)
        c.drawRect(0f, H / 2f - 2f - k * 40f, W, H / 2f + 2f + k * 40f, p)
        c.restore(); return
    }
    if (tv.call) {
        drawTvCall(c, p, tv.t, W, H)
        c.restore(); return
    }
    val st = tv.t - 0.5f
    val slide = ((st / TvSlideSec).toInt()) % 4
    val f = ((st % TvSlideSec) / TvSlideSec).coerceIn(0f, 1f)
    val grow = (f / 0.5f).coerceAtMost(1f).let { 1f - (1f - it) * (1f - it) }
    // Slide background.
    p.color = android.graphics.Color.rgb(246, 249, 252); c.drawRect(0f, 0f, W, H, p)
    p.color = android.graphics.Color.rgb(43, 93, 138); c.drawRect(0f, 0f, W, 13f, p)
    p.color = android.graphics.Color.WHITE; p.textSize = 9f; p.isFakeBoldText = true
    val head = when (slide) { 0 -> "Q Results"; 1 -> "Trend"; 2 -> "Split"; else -> tv.title.ifBlank { "Agenda" } }
    c.drawText(head.take(26), 5f, 10f, p)
    p.isFakeBoldText = false
    when (slide) {
        0 -> {
            val vals = floatArrayOf(0.45f, 0.7f, 0.55f, 0.9f, 0.75f)
            val cols = intArrayOf(0xFF3B82F6.toInt(), 0xFF22A06B.toInt(), 0xFF3B82F6.toInt(), 0xFFF59E0B.toInt(), 0xFF22A06B.toInt())
            for (k in vals.indices) {
                p.color = cols[k]
                val x = 18f + k * 26f; val h = 60f * vals[k] * grow
                c.drawRect(x, 84f - h, x + 16f, 84f, p)
            }
            p.color = android.graphics.Color.rgb(154, 163, 173); c.drawRect(10f, 84f, 150f, 85.5f, p)
        }
        1 -> {
            p.color = android.graphics.Color.rgb(220, 226, 232)
            for (k in 0..3) c.drawRect(10f, 30f + k * 16f, 150f, 30.8f + k * 16f, p)
            val ys = floatArrayOf(72f, 64f, 68f, 50f, 54f, 36f, 28f)
            p.color = 0xFF22A06B.toInt(); p.strokeWidth = 3f; p.style = android.graphics.Paint.Style.STROKE
            val n = ((ys.size - 1) * grow).coerceAtLeast(0.001f)
            val path = android.graphics.Path(); path.moveTo(12f, ys[0])
            for (k in 1..ys.size - 1) {
                if (k - 1 >= n) break
                val tt = (n - (k - 1)).coerceAtMost(1f)
                val x0 = 12f + (k - 1) * 22f; val y0 = ys[k - 1]
                path.lineTo(x0 + 22f * tt, y0 + (ys[k] - y0) * tt)
            }
            c.drawPath(path, p); p.style = android.graphics.Paint.Style.FILL
        }
        2 -> {
            val parts = floatArrayOf(0.42f, 0.28f, 0.18f, 0.12f)
            val cols = intArrayOf(0xFF3B82F6.toInt(), 0xFF22A06B.toInt(), 0xFFF59E0B.toInt(), 0xFFE5484D.toInt())
            var a0 = -90f
            for (k in parts.indices) {
                p.color = cols[k]
                val sweep = 360f * parts[k] * grow
                c.drawArc(28f, 20f, 92f, 84f, a0, sweep, true, p)
                a0 += 360f * parts[k] * grow
                c.drawRect(104f, 28f + k * 13f, 112f, 36f + k * 13f, p)
                p.color = android.graphics.Color.rgb(60, 70, 80); p.textSize = 8f
                c.drawText("${(parts[k] * 100).toInt()}%", 116f, 35f + k * 13f, p)
            }
        }
        else -> {
            p.color = android.graphics.Color.rgb(40, 48, 58); p.textSize = 8.5f
            val lines = listOf("Goals agreed", "Owners assigned", "Ship & review")
            for ((k, l) in lines.withIndex()) {
                if (f < 0.12f + k * 0.15f) break
                p.color = 0xFF2B5D8A.toInt(); c.drawCircle(10f, 28f + k * 14f, 2.2f, p)
                p.color = android.graphics.Color.rgb(40, 48, 58); c.drawText(l, 16f, 31f + k * 14f, p)
            }
            // Progress bar = taskforce progress.
            val prog = tv.progress.coerceIn(0, 100) / 100f
            p.color = android.graphics.Color.rgb(220, 226, 232); c.drawRect(10f, 72f, 150f, 82f, p)
            p.color = 0xFF22A06B.toInt(); c.drawRect(10f, 72f, 10f + 140f * prog * grow, 82f, p)
            p.color = android.graphics.Color.rgb(40, 48, 58); p.textSize = 7.5f
            c.drawText("${tv.progress.coerceIn(0, 100)}%", 128f, 69f, p)
        }
    }
    // Slide transition flash + scanlines for the pixel-TV feel.
    if (f < 0.06f) { p.color = android.graphics.Color.argb(120, 255, 255, 255); c.drawRect(0f, 0f, W, H, p) }
    p.color = android.graphics.Color.argb(18, 0, 0, 0)
    var yy = 0f; while (yy < H) { c.drawRect(0f, yy, W, yy + 1f, p); yy += 3f }
    if (power < 1f) { p.color = android.graphics.Color.argb((255 * (1f - power)).toInt(), 0, 0, 0); c.drawRect(0f, 0f, W, H, p) }
    c.restore()
}

/** Long meeting table over [OfficeMap.confTable]. Premium is black lacquer with a hub; oakwood is bare boards. */
private fun DrawScope.drawConfTable(camera: OfficeCamera, oak: Boolean = false) {
    val cells = OfficeMap.confTable
    val inset = 0.12f
    val x0 = cells.minOf { it.x } - 0.5f + inset
    val x1 = cells.maxOf { it.x } + 0.5f - inset
    val y0 = cells.minOf { it.y } - 0.5f + inset + 0.08f
    val y1 = cells.maxOf { it.y } + 0.5f - inset - 0.08f
    val lift = 0.62f
    val thick = if (oak) 0.18f else 0.12f
    val topColor = if (oak) OakTableTop else ConfBlackTop
    val edgeColor = if (oak) OakTableEdge else ConfBlackEdge
    val sideColor = if (oak) OakTableSide else ConfBlackSide
    val legColor = if (oak) OakPost else ConfFrame
    val stroke = (camera.tileW * camera.scale * 0.035f).coerceAtLeast(1.5f)
    // Legs.
    for ((lx, ly) in listOf(x0 + 0.15f to y0 + 0.15f, x1 - 0.15f to y0 + 0.15f, x1 - 0.15f to y1 - 0.15f, x0 + 0.15f to y1 - 0.15f)) {
        drawLine(legColor, camera.wallPt(lx, ly, 0f), camera.wallPt(lx, ly, lift - thick), stroke * (if (oak) 1.8f else 1.3f))
    }
    val tA = camera.wallPt(x0, y0, lift); val tB = camera.wallPt(x1, y0, lift)
    val tC = camera.wallPt(x1, y1, lift); val tD = camera.wallPt(x0, y1, lift)
    val bB = camera.wallPt(x1, y0, lift - thick); val bC = camera.wallPt(x1, y1, lift - thick)
    val bD = camera.wallPt(x0, y1, lift - thick)
    // Camera-facing edges: +x face (B→C) and +y face (C→D).
    drawPath(quad(tB, tC, bC, bB), sideColor)
    drawPath(quad(tC, tD, bD, bC), edgeColor)
    drawPath(quad(tA, tB, tC, tD), topColor)
    if (oak) {
        // Plank lines. One board is a darker offcut.
        for (k in 1..3) {
            val t = k / 4f
            val gy = y0 + (y1 - y0) * t
            drawLine(OakSeam, camera.wallPt(x0, gy, lift), camera.wallPt(x1, gy, lift), stroke * (if (k == 2) 0.9f else 0.4f))
        }
    } else {
        // One cool sheen on the lacquer. No wood grain.
        drawLine(
            ConfSheen,
            camera.wallPt(x0 + 0.18f, y0 + 0.16f, lift),
            camera.wallPt(x1 - 0.45f, y0 + (y1 - y0) * 0.28f, lift),
            stroke * 0.35f,
        )
    }
    val mx = (x0 + x1) / 2f
    val my = (y0 + y1) / 2f
    if (oak) {
        drawCircle(OakKnot, stroke * 1.5f, camera.wallPt(mx + 0.28f, my + 0.12f, lift))
        drawLine(OakPost, camera.wallPt(mx - 0.15f, my - 0.05f, lift), camera.wallPt(mx + 0.05f, my + 0.22f, lift), stroke * 0.45f)
    } else {
        drawConfHub(camera, mx, my, lift, stroke)
    }
}

/** Conference speakerphone: low puck, silver rim, mic keys, a live status light. */
private fun DrawScope.drawConfHub(camera: OfficeCamera, mx: Float, my: Float, lift: Float, stroke: Float) {
    val rx = 0.22f
    val ry = 0.14f
    val z1 = lift + 0.05f
    drawPath(
        quad(
            camera.wallPt(mx + rx, my, z1),
            camera.wallPt(mx, my + ry, z1),
            camera.wallPt(mx, my + ry, lift),
            camera.wallPt(mx + rx, my, lift),
        ),
        ConfHubBody,
    )
    drawPath(
        quad(
            camera.wallPt(mx, my + ry, z1),
            camera.wallPt(mx - rx, my, z1),
            camera.wallPt(mx - rx, my, lift),
            camera.wallPt(mx, my + ry, lift),
        ),
        Color(0xFF1A1E26),
    )
    drawPath(
        quad(
            camera.wallPt(mx, my - ry, z1),
            camera.wallPt(mx + rx, my, z1),
            camera.wallPt(mx, my + ry, z1),
            camera.wallPt(mx - rx, my, z1),
        ),
        ConfHubFace,
    )
    val rim = 0.72f
    drawPath(
        quad(
            camera.wallPt(mx, my - ry * rim, z1),
            camera.wallPt(mx + rx * rim, my, z1),
            camera.wallPt(mx, my + ry * rim, z1),
            camera.wallPt(mx - rx * rim, my, z1),
        ),
        ConfHubRim,
    )
    drawPath(
        quad(
            camera.wallPt(mx, my - ry * 0.40f, z1),
            camera.wallPt(mx + rx * 0.40f, my, z1),
            camera.wallPt(mx, my + ry * 0.40f, z1),
            camera.wallPt(mx - rx * 0.40f, my, z1),
        ),
        ConfHubBody,
    )
    drawCircle(ConfHubLed, stroke * 0.65f, camera.wallPt(mx, my - ry * 0.02f, z1))
    drawCircle(Color(0xFF6B7380), stroke * 0.42f, camera.wallPt(mx - rx * 0.28f, my + ry * 0.08f, z1))
    drawCircle(Color(0xFF6B7380), stroke * 0.42f, camera.wallPt(mx + rx * 0.28f, my + ry * 0.08f, z1))
}

/** Screen → fractional grid (inverse of [OfficeCamera.toScreen]). */
internal fun OfficeCamera.toGrid(screen: Offset): Pair<Float, Float> {
    val wx = (screen.x - originX) / scale
    val wy = (screen.y - originY) / scale
    val a = wx * 2f / tileW // x - y
    val b = wy * 2f / tileH // x + y
    return (a + b) / 2f to (b - a) / 2f
}

/** True when [tap] lands on the room floor or its glass walls (walls rise above the floor). */
private fun hitsConfRoom(camera: OfficeCamera, tap: Offset): Boolean {
    val x0 = OfficeMap.confX0 - 0.5f
    val y0 = OfficeMap.confY0 - 0.5f
    val x1 = x0 + OfficeMap.confSize
    val y1 = y0 + OfficeMap.confSize
    // Sample the tap and points below it, so tapping the upper wall glass also counts.
    val steps = 6
    for (k in 0..steps) {
        val lift = ConfWallH * camera.tileH * camera.scale * k / steps
        val (gx, gy) = camera.toGrid(Offset(tap.x, tap.y + lift))
        if (gx in x0..x1 && gy in y0..y1) return true
    }
    return false
}

/** Pulsing amber outline + soft glow along the room footprint and wall tops. */
private fun DrawScope.drawConfHighlight(camera: OfficeCamera, nowMs: Long, oak: Boolean = false) {
    val t = ((nowMs % 1400L) / 1400f)
    val pulse = 0.5f + 0.5f * kotlin.math.sin(t * 6.2832f)
    val x0 = OfficeMap.confX0 - 0.5f
    val y0 = OfficeMap.confY0 - 0.5f
    val x1 = x0 + OfficeMap.confSize
    val y1 = y0 + OfficeMap.confSize
    val corners = listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1)
    val topH = if (oak) OakBackH else ConfWallH
    val base = (camera.tileW * camera.scale * 0.03f).coerceAtLeast(1.5f)
    val glow = Color(0xFFFFC94A)
    for (h in listOf(0f, topH)) {
        val pts = corners.map { (gx, gy) -> camera.wallPt(gx, gy, h) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }
        drawPath(path, glow.copy(alpha = 0.18f + 0.22f * pulse), style = androidx.compose.ui.graphics.drawscope.Stroke(width = base * (4f + 3f * pulse)))
        drawPath(path, glow.copy(alpha = 0.65f + 0.35f * pulse), style = androidx.compose.ui.graphics.drawscope.Stroke(width = base * 1.2f))
    }
    for ((gx, gy) in corners) {
        drawLine(glow.copy(alpha = 0.5f + 0.4f * pulse), camera.wallPt(gx, gy, 0f), camera.wallPt(gx, gy, topH), base * 1.2f)
    }
    for (cell in OfficeMap.confCells) {
        diamond(camera.toScreen(cell.x.toFloat(), cell.y.toFloat()), camera.tileW * camera.scale, camera.tileH * camera.scale, glow.copy(alpha = 0.06f + 0.08f * pulse))
    }
}

/** Paints [app.pixelbureau.logic.PosterArt] onto the wall quad, crisp (no AA), one rect per pixel run. */
private fun DrawScope.drawPixelPoster(tl: Offset, tr: Offset, bl: Offset) {
    val rows = app.pixelbureau.logic.PosterArt.rows
    val pal = app.pixelbureau.logic.PosterArt.palette
    val w = rows.first().length.toFloat(); val h = rows.size.toFloat()
    val m = android.graphics.Matrix()
    m.setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, 0f, h), 0, floatArrayOf(tl.x, tl.y, tr.x, tr.y, bl.x, bl.y), 0, 3)
    val c = drawContext.canvas.nativeCanvas
    val p = android.graphics.Paint().apply { isAntiAlias = false; style = android.graphics.Paint.Style.FILL }
    c.save(); c.concat(m)
    for ((y, row) in rows.withIndex()) {
        var x = 0
        while (x < row.length) {
            val ch = row[x]; var e = x + 1
            while (e < row.length && row[e] == ch) e++
            p.color = (pal[ch] ?: 0xFFFFF6E0).toInt()
            // Tiny overlap hides seams between runs after the skew transform.
            c.drawRect(x.toFloat(), y.toFloat(), e + 0.04f, y + 1.04f, p)
            x = e
        }
    }
    c.restore()
}
