package app.pixelbureau.Pixel_Bureau_Web.pmart

import androidx.compose.runtime.Composable
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopItem
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopStore
import app.pixelbureau.Pixel_Bureau_Web.catalog.ShopStorefront
import app.pixelbureau.R
import app.pixelbureau.sprites.SpriteStore

internal const val PmartPlaceId = "pmart"

/**
 * Pmart storefront. Lists every ShopCatalog entry with store = Pmart (or
 * alsoSoldAt containing it); shows "Coming soon" while there are none.
 * Art for future stock goes in media/sprites/Pmart/.
 */
@Composable
internal fun PmartPage(
    sprites: SpriteStore,
    stars: Int,
    onBuy: (ShopItem) -> Unit,
    onBack: () -> Unit,
) {
    ShopStorefront(
        shop = ShopStore.Pmart,
        tagline = "Everyday Low Prices",
        comingSoonDetail = "Office staples at everyday low prices. The aisles are still being stocked.",
        artRes = R.drawable.portal_pmart,
        sprites = sprites,
        stars = stars,
        onBuy = onBuy,
        onBack = onBack,
    )
}
