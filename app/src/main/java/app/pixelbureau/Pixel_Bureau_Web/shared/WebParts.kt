package app.pixelbureau.Pixel_Bureau_Web.shared

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixelbureau.R
import app.pixelbureau.ui.Ink
import app.pixelbureau.ui.KaiAccent
import app.pixelbureau.ui.KaiAccentHi
import app.pixelbureau.ui.KaiAccentLo
import app.pixelbureau.ui.KaiBorder
import app.pixelbureau.ui.KaiBorderDeep
import app.pixelbureau.ui.KaiButter
import app.pixelbureau.ui.KaiCard
import app.pixelbureau.ui.KaiInk
import app.pixelbureau.ui.KaiInkMuted

/** Shared bits for the Pixegle "web" pages: PixelHire, Pazada, Pmart, Pamazon. */

internal val WebCardShape = RoundedCornerShape(18.dp)
internal val WebTileShape = RoundedCornerShape(14.dp)
internal val WebButtonShape = RoundedCornerShape(12.dp)
internal val WebSearchShape = RoundedCornerShape(16.dp)

/** Pixegle wordmark (drawable-nodpi/pixegle_logo). Fixed height — no BoxWithConstraints. */
@Composable
internal fun PixegleWordmark(
    modifier: Modifier = Modifier,
    heightDp: Dp = 32.dp,
) {
    val context = LocalContext.current
    val mark = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.pixegle_logo).asImageBitmap()
    }
    val ratio = mark.width / mark.height.toFloat()
    Image(
        bitmap = mark,
        contentDescription = "Pixegle",
        modifier = modifier.height(heightDp).aspectRatio(ratio),
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
    )
}

/** Pixegle logo + hamburger, used on top of every web-style page. */
@Composable
internal fun PixegleHeader() {
    Row(
        Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixegleWordmark(heightDp = 32.dp)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFFFBF4))
                .border(1.5.dp, KaiBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .width(16.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(KaiInk),
                    )
                }
            }
        }
    }
}

@Composable
internal fun WebBackLink(label: String = "← Back", onBack: () -> Unit) {
    Text(
        label,
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
}

@Composable
internal fun WebMagnifier(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val r = size.minDimension * 0.30f
        val c = Offset(size.width * 0.38f, size.height * 0.38f)
        drawCircle(color, radius = r, center = c, style = Stroke(width = size.minDimension * 0.14f))
        drawLine(
            color,
            Offset(c.x + r * 0.65f, c.y + r * 0.65f),
            Offset(size.width * 0.84f, size.height * 0.84f),
            strokeWidth = size.minDimension * 0.16f,
            cap = StrokeCap.Round,
        )
    }
}

/** White search bar with a yellow search button on the right. */
@Composable
internal fun WebSearchField(query: String, placeholder: String, onQuery: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(WebSearchShape)
            .background(Color.White)
            .border(1.5.dp, KaiBorder, WebSearchShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WebMagnifier(Modifier.padding(start = 12.dp).size(16.dp), KaiInkMuted)
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = KaiInk, fontSize = 14.sp),
            cursorBrush = SolidColor(KaiAccent),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            placeholder,
                            color = KaiInkMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        Box(
            Modifier
                .fillMaxHeight()
                .width(48.dp)
                .background(KaiAccent),
            contentAlignment = Alignment.Center,
        ) {
            WebMagnifier(Modifier.size(16.dp), Ink)
        }
    }
}

/** Chunky Kairosoft button with a drop shadow plate. */
@Composable
internal fun SoftGameButton(
    label: String,
    enabled: Boolean,
    face: Color = KaiAccent,
    highlight: Color = KaiAccentHi,
    shadow: Color = KaiAccentLo,
    ink: Color = Ink,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            .drawBehind {
                val r = 12.dp.toPx()
                drawRoundRect(
                    color = shadow.copy(alpha = alpha * 0.9f),
                    topLeft = Offset(0f, 2.dp.toPx()),
                    size = size.copy(height = size.height - 2.dp.toPx()),
                    cornerRadius = CornerRadius(r, r),
                )
            }
            .clip(WebButtonShape)
            .background(face.copy(alpha = alpha))
            .border(1.5.dp, shadow.copy(alpha = alpha * 0.7f), WebButtonShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = if (compact) 6.dp else 14.dp,
                vertical = if (compact) 6.dp else 10.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = ink.copy(alpha = alpha),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = if (compact) 1 else Int.MAX_VALUE,
            overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
        )
    }
}

/** Soft card with a drop plate, used for page sections. */
@Composable
internal fun WebPanel(
    wash: Color,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = KaiBorderDeep.copy(alpha = 0.22f),
                    topLeft = Offset(0f, 3.dp.toPx()),
                    size = size.copy(height = size.height - 3.dp.toPx()),
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                )
            }
            .clip(WebCardShape)
            .background(wash)
            .border(1.5.dp, KaiBorder, WebCardShape),
    ) {
        if (header != null) header()
        Column(
            Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = if (header == null) 10.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = { content() },
        )
    }
}

/** Kairosoft "coming soon" storefront used by placeholder web shops. */
@Composable
internal fun WebComingSoonPage(
    name: String,
    tagline: String,
    detail: String,
    artRes: Int,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WebBackLink("← Pixegle", onBack)
        WebPanel(wash = KaiCard) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painterResource(artRes),
                    contentDescription = name,
                    modifier = Modifier.size(84.dp),
                    contentScale = ContentScale.Fit,
                )
                Text(name, color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text(tagline, color = KaiAccentLo, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(
                    detail,
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(KaiButter)
                        .border(1.5.dp, KaiAccent, RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("Coming soon", color = KaiAccentLo, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
