package app.pixelbureau.Pixel_Bureau_Web.catalog

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixelbureau.Pixel_Bureau_Web.shared.SoftGameButton
import app.pixelbureau.Pixel_Bureau_Web.shared.WebPanel
import app.pixelbureau.Pixel_Bureau_Web.shared.WebSearchField
import app.pixelbureau.Pixel_Bureau_Web.shared.WebTileShape
import app.pixelbureau.R
import app.pixelbureau.sprites.SpriteStore
import app.pixelbureau.ui.KaiAccent
import app.pixelbureau.ui.KaiBorder
import app.pixelbureau.ui.KaiButter
import app.pixelbureau.ui.KaiInk
import app.pixelbureau.ui.KaiInkMuted
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import app.pixelbureau.Pixel_Bureau_Web.shared.WebBackLink
import app.pixelbureau.Pixel_Bureau_Web.shared.WebComingSoonPage
import app.pixelbureau.ui.KaiAccentLo
import app.pixelbureau.ui.KaiCard
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Shared storefront UI for every shop backed by [ShopCatalog]. Pazada keeps its
 * own page layout (banner, chips, Featured, Special Deals); Pamazon and Pmart use
 * [ShopStorefront], which lists whatever the catalog assigns to that store.
 */

internal val ShopDealRed = Color(0xFFD9443A)
private val ThumbWash = Color(0xFFFFFBF4)

private val StarGold = Color(0xFFF2B630)
private val StarRim = Color(0xFFB8801C)

/**
 * Generic store page: "Coming soon" when [shop] has no listings, otherwise a
 * searchable grid of `ShopCatalog.forStore(shop)` plus a deals row.
 */
@Composable
internal fun ShopStorefront(
    shop: ShopStore,
    tagline: String,
    comingSoonDetail: String,
    artRes: Int,
    sprites: SpriteStore,
    stars: Int,
    onBuy: (ShopItem) -> Unit,
    onBack: () -> Unit,
) {
    val listings = ShopCatalog.forStore(shop)
    if (listings.isEmpty()) {
        WebComingSoonPage(name = shop.label, tagline = tagline, detail = comingSoonDetail, artRes = artRes, onBack = onBack)
        return
    }
    var query by remember { mutableStateOf("") }
    val shelf = listings.filter { it.matches(query, ShopCategory.All) }
    val deals = shelf.filter { it.discountPercent != null }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WebBackLink("← Pixegle", onBack)
        WebPanel(wash = KaiCard) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(painterResource(artRes), contentDescription = shop.label, modifier = Modifier.size(56.dp), contentScale = ContentScale.Fit)
                Column {
                    Text(shop.label, color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(tagline, color = KaiAccentLo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
        ShopStarsCard(stars, shop)
        WebSearchField(query, "Search ${shop.label}...") { query = it }
        WebPanel(wash = Color.White, header = { ShopSectionHeader("🛒", if (query.isBlank()) "All Items" else "Results") {} }) {
            if (shelf.isEmpty()) {
                Text(
                    "Nothing on the shelf matches that yet.",
                    color = KaiInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                ShopItemGrid(shelf, sprites, stars, onBuy)
            }
        }
        if (deals.isNotEmpty()) {
            WebPanel(wash = Color(0xFFFDF0EC), header = { ShopSectionHeader("🔥", "Special Deals") {} }) {
                ShopDealsRow(deals, sprites, stars, onBuy)
            }
        }
    }
}

@Composable
internal fun ShopSectionHeader(emoji: String, title: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, fontSize = 16.sp)
        Text(
            title,
            color = KaiInk,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}


/** Columns for the shop grid: ~150dp per card, 2 on phones up to 6 on wide tablets. */
internal fun shopGridColumns(width: Dp): Int = (width / 150.dp).toInt().coerceIn(2, 6)

@Composable
internal fun ShopItemGrid(items: List<ShopItem>, sprites: SpriteStore, stars: Int, onBuy: (ShopItem) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = shopGridColumns(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in items.chunked(cols)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    for (item in row) {
                        ShopItemCard(item, sprites, Modifier.weight(1f), stars) { onBuy(item) }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Special Deals strip. Wide screens: cards share the row width evenly (no fixed
 * widths). Narrow screens: a horizontal scroller of 112dp cards.
 */
@Composable
internal fun ShopDealsRow(deals: List<ShopItem>, sprites: SpriteStore, stars: Int, onBuy: (ShopItem) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val fits = deals.isNotEmpty() && (maxWidth - gap * (deals.size - 1)) / deals.size >= 112.dp
        if (fits) {
            ShopItemGrid(deals, sprites, stars, onBuy, deal = true, cols = maxOf(deals.size, shopGridColumns(maxWidth)))
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Top,
            ) {
                for (item in deals) {
                    ShopItemCard(item, sprites, Modifier.width(112.dp), stars, deal = true) { onBuy(item) }
                }
            }
        }
    }
}

@Composable
private fun ShopItemGrid(
    items: List<ShopItem>,
    sprites: SpriteStore,
    stars: Int,
    onBuy: (ShopItem) -> Unit,
    deal: Boolean,
    cols: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in items.chunked(cols)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                for (item in row) {
                    ShopItemCard(item, sprites, Modifier.weight(1f), stars, deal = deal) { onBuy(item) }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One product tile. Height follows the contents so Buy stays a full button.
 * Do not wrap the row in IntrinsicSize.Min: Text's intrinsic height ignores
 * minLines, the row is then locked too short, and Buy (measured last) is
 * crushed into a slit on titles that only need one line.
 */
@Composable
internal fun ShopItemCard(
    item: ShopItem,
    sprites: SpriteStore,
    modifier: Modifier,
    stars: Int,
    deal: Boolean = false,
    onBuy: () -> Unit,
) {
    val affordable = stars >= item.salePrice
    Column(
        modifier
            .clip(WebTileShape)
            .background(Color.White)
            .border(1.5.dp, KaiBorder, WebTileShape)
            .padding(6.dp)
            .semantics { contentDescription = "${item.name}, ${item.salePrice} stars" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ThumbWash),
        ) {
            ShopItemThumb(item, sprites, Modifier.align(Alignment.Center).fillMaxSize().padding(4.dp))
            val off = item.discountPercent
            if (deal && off != null) {
                Text(
                    "-$off%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ShopDealRed)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            item.name,
            color = KaiInk,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!deal) {
            Text(
                item.desc,
                color = KaiInkMuted,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ShopStar(Modifier.size(12.dp))
            Text(
                "${item.salePrice}",
                color = if (deal) ShopDealRed else KaiInk,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
            )
            if (deal && item.discountPercent != null) {
                Text(
                    "★${item.price}",
                    color = KaiInkMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
        }
        SoftGameButton(
            label = if (affordable) "Buy" else "Need ★${item.salePrice}",
            enabled = affordable,
            onClick = onBuy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp),
            compact = true,
        )
    }
}


@Composable
internal fun ShopItemThumb(item: ShopItem, sprites: SpriteStore, modifier: Modifier) {
    val thumb = item.thumbRes
    if (thumb != null) {
        Image(painterResource(thumb), contentDescription = item.name, modifier = modifier, contentScale = ContentScale.Fit)
        return
    }
    val bitmap = item.spriteKey?.let { sprites.get(it) }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = item.name,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.Low,
        )
    } else {
        Image(
            painterResource(item.iconRes ?: item.category.iconRes),
            contentDescription = item.name,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}

/** Five-point gold star: the shop currency glyph (HR reputation stars). */
@Composable
internal fun ShopStar(modifier: Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = size.minDimension / 2f
        val inner = outer * 0.45f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = -PI / 2 + i * PI / 5
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, StarGold)
        drawPath(path, StarRim, style = Stroke(width = outer * 0.14f))
    }
}

/**
 * Kairosoft-style wallet card (matches PixelHire's "Bureau ★" stat pill).
 * [stars] comes from BureauUi.bureau.reputation, so it updates live.
 */
@Composable
internal fun ShopStarsCard(stars: Int, shop: ShopStore) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KaiButter)
            .border(1.5.dp, KaiBorder, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = "Your stars: $stars" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShopStar(Modifier.size(30.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Your Stars", color = KaiInkMuted, style = MaterialTheme.typography.labelMedium)
            Text(
                "Stars are ${shop.label} currency, earned via reputation.",
                color = KaiInkMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text("★ $stars", color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    }
}
