package app.pixelbureau.Pixel_Bureau_Web.pazada

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopCatalog
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopCategory
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopDealRed
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopItem
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopItemGrid
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopSectionHeader
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopStore
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopStarsCard
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopDealsRow
import app.pixelbureau.Pixel_Bureau_Web.shared.WebBackLink
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
import app.pixelbureau.ui.KaiPage
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

internal const val PazadaPlaceId = "pazada"

private val LinkBlue = Color(0xFF2F6FB5)
private val FeaturedWash = Color(0xFFF1F7FC)
private val DealsWash = Color(0xFFFDF0EC)
private val PromoWash = Color(0xFFDDF1FC)

/**
 * Pazada: Kairosoft-style office marketplace. Lists every [ShopCatalog] entry
 * assigned to [ShopStore.Pazada]. Prices are HR stars (★); Buy spends them via [onBuy].
 */
@Composable
internal fun PazadaPage(
    sprites: SpriteStore,
    stars: Int,
    onBuy: (ShopItem) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ShopCategory.All) }
    var showAll by remember { mutableStateOf(false) }
    val browsing = query.isNotBlank() || category != ShopCategory.All || showAll
    val shelf = if (browsing) {
        ShopCatalog.forStore(ShopStore.Pazada).filter { it.matches(query, category) }
    } else {
        ShopCatalog.featured(ShopStore.Pazada)
    }
    val deals = ShopCatalog.deals(ShopStore.Pazada).filter { it.matches(query, category) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WebBackLink("← Pixegle", onBack)
        Image(
            painterResource(R.drawable.pazada_banner),
            contentDescription = "Pazada. Office Essentials for a Better Bureau.",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2048f / 768f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, KaiBorder, RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
        )

        ShopStarsCard(stars, ShopStore.Pazada)

        WebSearchField(query, "Search office items, furniture, décor, tools...") { query = it }

        CategoryChipRow(
            selected = category,
            onSelect = { category = it },
        )

        WebPanel(
            wash = FeaturedWash,
            header = {
                ShopSectionHeader(
                    emoji = if (browsing) "🔎" else "👑",
                    title = when {
                        query.isNotBlank() -> "Results"
                        category != ShopCategory.All -> category.label
                        showAll -> "All Items"
                        else -> "Featured Items"
                    },
                ) {
                    Text(
                        if (browsing) "Featured ›" else "See All ›",
                        color = LinkBlue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                            ) {
                                if (browsing) {
                                    showAll = false
                                    query = ""
                                    category = ShopCategory.All
                                } else {
                                    showAll = true
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            },
        ) {
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
            WebPanel(
                wash = DealsWash,
                header = {
                    ShopSectionHeader(emoji = "🔥", title = "Special Deals") {
                        Text(
                            "⏱ Ends in ${dealCountdown()}",
                            color = ShopDealRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                },
            ) {
                ShopDealsRow(deals, sprites, stars, onBuy)
            }
        }

        PromoBanner()
    }
}

/**
 * Horizontally scrollable category strip. Fixed-width icon cards (not weight-
 * squeezed) so labels stay readable on phone; LazyRow + content padding keeps
 * first/last chips unclipped. Soft edge fades hint at more categories.
 * Avoid BoxWithConstraints inside IntrinsicSize (crash history).
 */
@Composable
private fun CategoryChipRow(
    selected: ShopCategory,
    onSelect: (ShopCategory) -> Unit,
) {
    val categories = remember { ShopCategory.entries.toList() }
    val listState = rememberLazyListState()
    val showLeft by remember { derivedStateOf { listState.canScrollBackward } }
    val showRight by remember { derivedStateOf { listState.canScrollForward } }
    val fadePx = 20.dp

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .drawWithContent {
                drawContent()
                val w = fadePx.toPx()
                if (showLeft) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(KaiPage, KaiPage.copy(alpha = 0f)),
                            startX = 0f,
                            endX = w,
                        ),
                        size = Size(w, size.height),
                    )
                }
                if (showRight) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(KaiPage.copy(alpha = 0f), KaiPage),
                            startX = size.width - w,
                            endX = size.width,
                        ),
                        topLeft = Offset(size.width - w, 0f),
                        size = Size(w, size.height),
                    )
                }
            },
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(categories, key = { it.name }) { cat ->
            CategoryChip(
                cat = cat,
                selected = selected == cat,
                modifier = Modifier.width(78.dp).height(72.dp),
            ) {
                onSelect(cat)
            }
        }
    }
}

@Composable
private fun CategoryChip(
    cat: ShopCategory,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(WebTileShape)
            .background(if (selected) KaiButter else Color.White)
            .border(
                if (selected) 2.5.dp else 1.5.dp,
                if (selected) KaiAccent else KaiBorder,
                WebTileShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = cat.label }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painterResource(cat.iconRes),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            cat.label,
            color = KaiInk,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}


@Composable
private fun PromoBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PromoWash)
            .border(1.5.dp, KaiBorder, RoundedCornerShape(16.dp))
            .semantics { contentDescription = "Make Your Bureau Unique. Furniture, plants, electronics, and more, all in one place." },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.pazada_promo),
            contentDescription = null,
            modifier = Modifier.weight(1f).aspectRatio(408f / 149f),
            contentScale = ContentScale.Crop,
        )
        Column(
            Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "Make Your Bureau Unique",
                color = KaiInk,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                lineHeight = 15.sp,
            )
            Text(
                "Furniture, plants, electronics, and more — all in one place.",
                color = KaiInkMuted,
                fontSize = 11.sp,
                lineHeight = 13.sp,
            )
        }
    }
}

/** Deals roll over every Monday 00:00 local time. */
private fun dealCountdown(): String {
    val now = LocalDateTime.now()
    val end = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay()
    val left = Duration.between(now, end)
    val days = left.toDays()
    val hours = left.minusDays(days).toHours()
    return "${days}d ${hours}h"
}
