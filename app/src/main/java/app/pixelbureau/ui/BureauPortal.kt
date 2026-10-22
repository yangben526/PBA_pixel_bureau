package app.pixelbureau.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import app.pixelbureau.R
import kotlin.math.roundToInt
import app.pixelbureau.logic.HeroCityLoopMs
import app.pixelbureau.logic.HeroCloudLoopMs
import app.pixelbureau.logic.heroTileLayout
import app.pixelbureau.logic.heroTileShift
import app.pixelbureau.modules.pixelnews.PixelNews
import app.pixelbureau.modules.pixelnews.PixelNewsCard
import app.pixelbureau.modules.pixelnews.PixelNewsPlace

private val PortalCard = RoundedCornerShape(18.dp)
private val PortalTile = RoundedCornerShape(14.dp)

private data class BureauShop(
    val id: String,
    val name: String,
    val line: String,
    val detail: String,
    val art: BureauArt,
    val shelf: String,
    val empty: String,
    val note: String = "This counter isn't open yet.",
)

private enum class BureauArt { PixelHire, Pmart, Pamazon, Pazada, Truck }

private val shops = listOf(
    BureauShop(
        PixelHirePlaceId,
        "PixelHire",
        "Find & Hire\nGreat Talent",
        "Find and hire great talent for the bureau.",
        BureauArt.PixelHire,
        "Openings",
        "No openings posted yet.",
        "Roles for the bureau will be posted here.",
    ),
    BureauShop(
        "pmart",
        "Pmart",
        "Everyday\nLow Prices",
        "Everyday low prices on office staples.",
        BureauArt.Pmart,
        "Aisles",
        "No prices posted yet.",
    ),
    BureauShop(
        "pamazon",
        "Pamazon",
        "Best Tooling Deals",
        "Fast shipping and the deals that come with it.",
        BureauArt.Pamazon,
        "Parcels",
        "No shipments yet.",
    ),
    BureauShop(
        "pazada",
        "Pazada",
        "Office Best Finds",
        "Local favourites, and finds from farther off.",
        BureauArt.Pazada,
        "Finds",
        "No favourites yet.",
    ),
)

internal const val PixelHirePlaceId = "pixelhire"
private const val ToolsId = "tools"

@Composable
internal fun BureauPortal(
    placeId: String?,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    if (placeId == null) {
        BureauHome(query, onQuery, onOpen)
    } else {
        BureauPlace(placeId, onBack, onOpen)
    }
}

@Composable
private fun BureauHome(query: String, onQuery: (String) -> Unit, onOpen: (String) -> Unit) {
    val q = query.trim()
    val shownShops = shops.filter { hit(q, it.name, it.line, it.detail) }
    val shownStories = PixelNews.matching(q)
    val showTools = hit(q, "Find tools for a better office", "desks", "decor")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(PortalCard)
                .background(KaiCard)
                .border(1.5.dp, KaiBorder, PortalCard),
        ) {
            PixegleHero()
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SearchRow(query, onQuery)
                if (shownShops.isEmpty() && q.isNotEmpty()) {
                    Text(
                        "No counters match that.",
                        color = KaiInkMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        shownShops.forEach { shop ->
                            ShopCard(shop, Modifier.weight(1f).fillMaxHeight()) { onOpen(shop.id) }
                        }
                    }
                }
            }
        }
        if (shownStories.isNotEmpty() || q.isEmpty()) {
            PixelNewsCard(shownStories, onOpen)
        }
        if (showTools) {
            ToolsBanner()
        }
        if (q.isNotEmpty() && shownShops.isEmpty() && shownStories.isEmpty() && !showTools) {
            Text(
                "Nothing on Pixegle matches that.",
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun BureauPlace(placeId: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "← Pixegle",
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
        when {
            PixelNews.owns(placeId) -> PixelNewsPlace(placeId, onOpen)
            placeId == ToolsId -> ToolsPlace()
            else -> {
                val shop = shops.firstOrNull { it.id == placeId }
                if (shop != null) ShopPlace(shop) else ToolsPlace()
            }
        }
    }
}

@Composable
private fun ShopPlace(shop: BureauShop) {
    PlaceHeader(shop.art, shop.name, shop.detail)
    PlaceShelf(shop.shelf, shop.empty, shop.note)
}

@Composable
private fun ToolsPlace() {
    PlaceHeader(BureauArt.Truck, "Office tools", "From desks to decor, everything your team needs.")
    PlaceShelf("Catalog", "No tools stocked yet.", "Desks, chairs, plants, and the small things that finish a desk.")
}

@Composable
private fun PlaceHeader(art: BureauArt, title: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(PortalCard)
            .background(KaiCard)
            .border(1.5.dp, KaiBorder, PortalCard)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BureauMark(
            art,
            if (art == BureauArt.Truck) {
                Modifier
                    .width(120.dp)
                    .aspectRatio(342f / 120f)
                    .clip(RoundedCornerShape(8.dp))
            } else {
                Modifier.size(64.dp)
            },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(detail, color = KaiInkMuted, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun PlaceShelf(title: String, empty: String, note: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PortalCard)
            .background(KaiCard)
            .border(1.5.dp, KaiBorder, PortalCard)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = KaiInk, fontWeight = FontWeight.Bold)
        Text(empty, color = KaiInk, style = MaterialTheme.typography.bodyMedium)
        Text(note, color = KaiInkMuted, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
    }
}

@Composable
private fun PixegleHero() {
    val context = LocalContext.current
    val base = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.portal_hero_base).asImageBitmap()
    }
    val city = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.portal_hero_city).asImageBitmap()
    }
    val clouds = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.portal_hero_clouds).asImageBitmap()
    }
    val ratio = base.width / base.height.toFloat()
    val loop = rememberInfiniteTransition(label = "pixegleHero")
    val cloudProgress by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(HeroCloudLoopMs, easing = LinearEasing)),
        label = "pixegleHeroClouds",
    )
    val cityProgress by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(HeroCityLoopMs, easing = LinearEasing)),
        label = "pixegleHeroCity",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .semantics { contentDescription = "Pixegle" },
    ) {
        val w = size.width.roundToInt().coerceAtLeast(1)
        val h = size.height.roundToInt().coerceAtLeast(1)
        drawImage(
            base,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(w, h),
            filterQuality = FilterQuality.None,
        )
        drawWrappedTile(city, heroTileShift(cityProgress, city.width), w, h)
        drawWrappedTile(clouds, heroTileShift(cloudProgress, clouds.width), w, h)
    }
}

/** Draws [tile] twice so a rightward [shiftPx] scroll wraps seamlessly. */
private fun DrawScope.drawWrappedTile(tile: ImageBitmap, shiftPx: Int, dstW: Int, dstH: Int) {
    val layout = heroTileLayout(shiftPx, tile.width, dstW)
    drawImage(
        tile,
        dstOffset = IntOffset(layout.firstX, 0),
        dstSize = IntSize(layout.firstW.coerceAtLeast(1), dstH),
        filterQuality = FilterQuality.None,
    )
    drawImage(
        tile,
        dstOffset = IntOffset(layout.secondX, 0),
        dstSize = IntSize(layout.secondW, dstH),
        filterQuality = FilterQuality.None,
    )
}

@Composable
private fun SearchRow(query: String, onQuery: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(Color(0xFFFFFBF4))
            .border(1.5.dp, KaiBorder, shape)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Magnifier(Modifier.size(18.dp), KaiInkMuted)
        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            if (query.isEmpty()) {
                Text("Search anything...", color = KaiInkMuted, fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = KaiInk, fontSize = 14.sp),
                cursorBrush = SolidColor(KaiAccent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier
                .width(52.dp)
                .fillMaxSize()
                .background(KaiAccent),
            contentAlignment = Alignment.Center,
        ) {
            Magnifier(Modifier.size(18.dp), KaiInk)
        }
    }
}

@Composable
private fun ShopCard(shop: BureauShop, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(PortalTile)
            .background(Color(0xFFFFFBF4))
            .border(1.5.dp, KaiBorder, PortalTile)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Fixed 42.dp mark (pre tablet-cutoff sizing). No BoxWithConstraints:
        // parent Row uses IntrinsicSize.Max and SubcomposeLayout cannot answer
        // intrinsic measurements (crash on Pixegle open).
        BureauMark(shop.art, Modifier.size(42.dp))
        Text(
            shop.name,
            color = KaiInk,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            shop.line,
            color = KaiInkMuted,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ToolsBanner() {
    val painter = painterResource(R.drawable.portal_banner)
    val ratio = painter.intrinsicSize.let { s ->
        if (s.width > 0f && s.height > 0f) s.width / s.height else 342f / 120f
    }
    Image(
        painter,
        contentDescription = "Find tools for a better office",
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(PortalCard),
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
private fun Magnifier(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val r = size.minDimension * 0.32f
        val c = Offset(size.width * 0.40f, size.height * 0.40f)
        drawCircle(color, radius = r, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.12f))
        drawLine(
            color,
            Offset(c.x + r * 0.7f, c.y + r * 0.7f),
            Offset(size.width * 0.82f, size.height * 0.82f),
            strokeWidth = size.minDimension * 0.14f,
        )
    }
}

@Composable
private fun BureauMark(art: BureauArt, modifier: Modifier) {
    val photo = art == BureauArt.Truck
    Image(
        painterResource(artDrawable(art)),
        contentDescription = null,
        modifier = modifier,
        contentScale = if (photo) ContentScale.Crop else ContentScale.Fit,
    )
}

private fun artDrawable(art: BureauArt): Int = when (art) {
    BureauArt.PixelHire -> R.drawable.portal_pixelhire
    BureauArt.Pmart -> R.drawable.portal_pmart
    BureauArt.Pamazon -> R.drawable.portal_pamazon
    BureauArt.Pazada -> R.drawable.portal_pazada
    BureauArt.Truck -> R.drawable.portal_truck
}

private fun hit(query: String, vararg fields: String): Boolean {
    if (query.isEmpty()) return true
    return fields.any { it.contains(query, ignoreCase = true) }
}
