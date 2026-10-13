package app.pixelbureau.Pixel_Bureau_Web.pamazon

import androidx.compose.runtime.Composable
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopItem
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopStore
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopStorefront
import app.pixelbureau.R
import app.pixelbureau.sprites.SpriteStore

internal const val PamazonPlaceId = "pamazon"

/**
 * Pamazon storefront. Lists every ShopCatalog entry with store = Pamazon (or
 * alsoSoldAt containing it); shows "Coming soon" while there are none.
 * Art for future parcels goes in media/sprites/Pamazon/.
 */
@Composable
internal fun PamazonPage(
    sprites: SpriteStore,
    stars: Int,
    onBuy: (ShopItem) -> Unit,
    onBack: () -> Unit,
) {
    ShopStorefront(
        shop = ShopStore.Pamazon,
        tagline = "Fast Shipping, Great Deals",
        comingSoonDetail = "Fast shipping and the deals that come with it. The first parcels are on their way.",
        artRes = R.drawable.portal_pamazon,
        sprites = sprites,
        stars = stars,
        onBuy = onBuy,
        onBack = onBack,
    )
}
