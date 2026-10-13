# Pazada sprites

Drop real shop/item sprites here (magenta-keyed PNGs, same style as `props/`).

## Shop catalog (Pazada / Pamazon / Pmart)

All store listings live in ONE shared catalog:
`app/src/main/java/app/pixelbureau/Pixel_Bureau_Web/catalog/ShopCatalog.kt`
(`ShopCatalog.curated`). UI shared by the stores is in `catalog/ShopParts.kt`.

Each `ShopItem` has:

- `propKey` – the map prop kind it places/replaces (`props/<propKey>` sprite).
- `store` – primary storefront: `ShopStore.Pazada`, `ShopStore.Pamazon`, or `ShopStore.Pmart`.
- `alsoSoldAt` – optional extra storefronts.
- `featured` / `discountPercent` – Featured shelf and Special Deals row (Pazada).

**Moving an item** = change its `store` field, e.g.

```kotlin
ShopItem("bike", "Office Bike", ..., propKey = "bike", store = ShopStore.Pamazon)
```

That's it — Pamazon/Pmart show "Coming soon" until they have at least one
listing, then switch to a real shelf automatically.

**New map props** registered in `SpriteStore.propNames` (or placed in
`OfficeMap.props`) are auto-listed in Pazada with a placeholder name/price.
Add a curated `ShopItem` for it to set a real name, price, category, or store.
State/layer sprites (`SpriteStore.propLayers`, e.g. `fridge_open`) are skipped.

## Using art from this folder

To use a new sprite from this folder as a thumbnail, add `"Pazada/<name>"` to
`SpriteStore.names`, bump the SpriteStore cache dir (`sprites-vNN`), and set the
item's `thumbSprite = "Pazada/<name>"`.

UI-only art (banner, promo, category icons) lives in
`app/src/main/res/drawable-nodpi/pazada_*.png`.
