package app.pixelbureau.Pixel_Bureau_Web.catalog

import androidx.annotation.DrawableRes
import kotlin.math.roundToInt
import app.pixelbureau.R
import app.pixelbureau.logic.OfficeMap
import app.pixelbureau.sprites.SpriteStore

/**
 * Single shop catalog shared by every Pixel Bureau web store (Pazada, Pamazon, Pmart).
 *
 * ── Store assignment ─────────────────────────────────────────────────────────
 * Every [ShopItem] has one primary [ShopItem.store] plus an optional
 * [ShopItem.alsoSoldAt] set. A store page shows `ShopCatalog.forStore(<store>)`,
 * so moving a listing is a one-field edit in [ShopCatalog.curated]:
 *
 *     ShopItem("bike", ..., store = ShopStore.Pamazon)          // move to Pamazon
 *     ShopItem("bike", ..., alsoSoldAt = setOf(ShopStore.Pmart)) // keep in Pazada, also Pmart
 *
 * No page code changes: Pamazon/Pmart flip from "Coming soon" to a real shelf as
 * soon as they have at least one listing.
 *
 * ── Prop coverage ────────────────────────────────────────────────────────────
 * [ShopItem.propKey] is the map prop kind (`Prop.kind` in OfficeMap, sprite
 * `props/<propKey>` in SpriteStore) a future purchase places/replaces.
 * [ShopCatalog.items] = curated listings + an auto listing (store = Pazada) for
 * every prop registered in [SpriteStore.propNames] or placed in [OfficeMap.props]
 * that has no curated listing yet, so a newly registered map prop shows up in the
 * shop automatically. Add it to [ShopCatalog.curated] to give it a real name/price.
 * State/layer sprites ([SpriteStore.propLayers], e.g. fridge_open) and conference
 * built-ins (`conf_*`) are never listed.
 *
 * Currency is the HR reputation star (★). Buy spends stars via
 * BureauViewModel.buyShopItem and records item id → count in SettingsStore
 * (KEY_OWNED_ITEMS). Every non-desk purchase gets its own map cell via
 * logic/MapPlacement (stored in SettingsStore KEY_SHOP_PLACEMENTS); buying is
 * refused before any star is spent when the floor has no valid room. The desk
 * group does not add furniture: it swaps the art of the eight work desks.
 */
internal enum class ShopStore(val label: String) {
    Pazada("Pazada"),
    Pamazon("Pamazon"),
    Pmart("Pmart"),
}

internal enum class ShopCategory(val label: String, @DrawableRes val iconRes: Int) {
    All("All Items", R.drawable.pazada_cat_all),
    Furniture("Furniture", R.drawable.pazada_cat_furniture),
    Plants("Plants", R.drawable.pazada_cat_plants),
    Electronics("Electronics", R.drawable.pazada_cat_electronics),
    Pantry("Pantry", R.drawable.pazada_cat_pantry),
    Decor("Decor", R.drawable.pazada_cat_decor),
    Storage("Storage", R.drawable.pazada_cat_storage),
}

internal data class ShopItem(
    val id: String,
    val name: String,
    val desc: String,
    /** Star (★) price before any deal. Stars are the HR reputation stars. */
    val price: Int,
    val category: ShopCategory,
    /** Map prop kind this listing places/replaces (sprite `props/<propKey>`). */
    val propKey: String,
    /** Primary storefront. Change this to move the listing. */
    val store: ShopStore = ShopStore.Pazada,
    /** Extra storefronts that also list this item. */
    val alsoSoldAt: Set<ShopStore> = emptySet(),
    /** Percent off, e.g. 30 for -30%. Non-null puts it in Special Deals. */
    val discountPercent: Int? = null,
    val featured: Boolean = false,
    /** SpriteStore key override for the thumbnail; defaults to `props/<propKey>`. */
    val thumbSprite: String? = null,
    /** Drawable fallback when the sprite is not loaded / not drawn yet. */
    @DrawableRes val iconRes: Int? = null,
    /** UI-only thumbnail drawable; wins over the map sprite (e.g. desk_bare + laptop + mug composite). */
    @DrawableRes val thumbRes: Int? = null,
    /**
     * Mutually-exclusive ownership group. Owning one item in a group replaces any
     * other owned item of the same group (e.g. only one desk type at a time).
     * Null = stackable / no exclusivity.
     */
    val exclusiveGroup: String? = null,
) {
    val spriteKey: String get() = thumbSprite ?: "props/$propKey"

    val salePrice: Int
        get() = discountPercent?.let { (price * (100 - it) / 100.0).roundToInt().coerceIn(1, price) } ?: price

    fun soldAt(shop: ShopStore): Boolean = store == shop || shop in alsoSoldAt

    fun matches(query: String, cat: ShopCategory): Boolean {
        if (cat != ShopCategory.All && category != cat) return false
        val q = query.trim()
        if (q.isEmpty()) return true
        return name.contains(q, true) || desc.contains(q, true) ||
            category.label.contains(q, true) || propKey.contains(q, true)
    }
}

internal object ShopCatalog {
    /** Exclusivity group: only one desk type owned at a time (office_desk ⇄ premium_desk). */
    const val GROUP_DESK = "desk"

    /** Hand-written listings. One entry per propKey; edit `store` here to move it. */
    val curated: List<ShopItem> = listOf(
        // ── Featured ──────────────────────────────────────────────────────
        ShopItem("office_desk", "Dream starter - Oakwood simple", "A reliable oak desk, laptop and mug included.", 3, ShopCategory.Furniture, propKey = "desk_bare", featured = true, thumbRes = R.drawable.shop_thumb_oakwood_desk, exclusiveGroup = GROUP_DESK),
        ShopItem("ergo_chair", "Ergonomic Chair", "Keep your team comfortable.", 2, ShopCategory.Furniture, propKey = "chair", featured = true),
        ShopItem("indoor_plant", "Indoor Plant", "A touch of green for productivity.", 1, ShopCategory.Plants, propKey = "plant", featured = true),
        ShopItem("coffee_machine", "Coffee Station", "Great ideas start here.", 3, ShopCategory.Pantry, propKey = "coffee", featured = true),
        ShopItem("aquarium", "Office Aquarium", "Three fish, zero meetings.", 4, ShopCategory.Decor, propKey = "aquarium", featured = true),
        ShopItem("floor_lamp", "Brass Floor Lamp", "Warm light for late sprints.", 2, ShopCategory.Decor, propKey = "floor_lamp", featured = true),
        // ── Special deals ─────────────────────────────────────────────────
        ShopItem("premium_desk", "Startup Premium", "A full workstation, ready to go.", 5, ShopCategory.Furniture, propKey = "desk", discountPercent = 40, exclusiveGroup = GROUP_DESK),
        ShopItem("bookshelf", "Bookshelf", "Room for every manual.", 3, ShopCategory.Storage, propKey = "bookshelf", discountPercent = 33),
        ShopItem("lounge_sofa", "Lounge Sofa", "Breaks are better on a sofa.", 4, ShopCategory.Furniture, propKey = "sofa", discountPercent = 25),
        ShopItem("large_plant", "Large Plant", "Big leaves, big ideas.", 2, ShopCategory.Plants, propKey = "plant2", discountPercent = 50),
        ShopItem("arcade_cabinet", "Retro Arcade Cabinet", "High scores, low stress.", 5, ShopCategory.Electronics, propKey = "arcade", discountPercent = 20),
        ShopItem("office_copier", "Office Copier", "Jams only on deadlines.", 3, ShopCategory.Electronics, propKey = "printer", discountPercent = 34),
        ShopItem("neon_sign", "Neon Coffee Sign", "Coffee + PB in glowing tubes.", 3, ShopCategory.Decor, propKey = "neon_sign", discountPercent = 34),
        // ── Rest of the shelf (every other map prop) ──────────────────────
        ShopItem("beanbag", "Beanbag", "Sink in and think.", 2, ShopCategory.Furniture, propKey = "beanbag"),
        ShopItem("retro_workstation", "Retro Workstation", "Desk, monitor, and keyboard in one.", 4, ShopCategory.Electronics, propKey = "desk_top"),
        ShopItem("server_rack", "Server Rack", "Keeps the bureau online.", 5, ShopCategory.Electronics, propKey = "server"),
        ShopItem("water_cooler", "Water Cooler", "Where the best gossip flows.", 2, ShopCategory.Pantry, propKey = "watercooler"),
        ShopItem("fridge", "Fridge", "Snacks for the whole team.", 3, ShopCategory.Pantry, propKey = "fridge"),
        ShopItem("monstera_pot", "Monstera Pot", "Lush leaves in a terracotta pot.", 1, ShopCategory.Plants, propKey = "plant_top"),
        ShopItem("whiteboard", "Whiteboard", "Map out the big plan.", 2, ShopCategory.Decor, propKey = "whiteboard"),
        ShopItem("foosball", "Foosball Table", "Team building, one goal at a time.", 4, ShopCategory.Decor, propKey = "foosball"),
        ShopItem("bike", "Office Bike", "Ride in, ride out.", 3, ShopCategory.Decor, propKey = "bike"),
        ShopItem("trash_bin", "Trash Bin", "Every bureau needs one.", 1, ShopCategory.Storage, propKey = "trash"),
        ShopItem("picture_frame", "Picture Frame", "A little view, on an easel.", 1, ShopCategory.Decor, propKey = "picture_frame", iconRes = R.drawable.pazada_cat_decor),
        ShopItem("storage_box", "Storage Boxes", "Tidy desks, tidy minds.", 1, ShopCategory.Storage, propKey = "storage_box", iconRes = R.drawable.pazada_cat_storage),
        // ── v0.1.61 new stock ─────────────────────────────────────────────
        ShopItem("vending_machine", "Snack Vending Machine", "Chips, soda, zero small talk.", 4, ShopCategory.Pantry, propKey = "vending"),
        ShopItem("filing_cabinet", "Filing Cabinet", "Three drawers of 'important'.", 2, ShopCategory.Storage, propKey = "filing_cabinet"),
        ShopItem("desk_globe", "Desk Globe", "Plan the world tour.", 2, ShopCategory.Decor, propKey = "globe"),
        ShopItem("acoustic_guitar", "Acoustic Guitar", "For the post-launch jam.", 3, ShopCategory.Decor, propKey = "guitar"),
        ShopItem("grandfather_clock", "Grandfather Clock", "Tick-tock, it's ship o'clock.", 3, ShopCategory.Decor, propKey = "clock"),
    )

    /**
     * Every prop the map can place: sprite registry ∪ props actually placed.
     * Conference-room built-ins (`conf_*`) stay on the floor but are never sold —
     * they have no shop sprite and would block cells invisibly if purchased.
     */
    /** Props pulled from sale (cactus, pouf); never listed or auto-listed. */
    val retiredPropKeys: Set<String> = setOf("cactus", "pouf")

    val mapPropKeys: List<String> = (SpriteStore.propNames + OfficeMap.props.map { it.kind })
        .distinct()
        .filterNot { it in SpriteStore.propLayers || it.startsWith("conf_") || it in retiredPropKeys }

    /** Default Pazada listing for any map prop that has no curated entry yet. */
    private val autoListed: List<ShopItem> = run {
        val covered = curated.map { it.propKey }.toSet()
        mapPropKeys.filterNot { it in covered }.map { key ->
            ShopItem(
                id = "prop_$key",
                name = key.split('_').joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } },
                desc = "Straight from the bureau floor.",
                price = 1,
                category = ShopCategory.Decor,
                propKey = key,
            )
        }
    }

    val items: List<ShopItem> = curated + autoListed

    fun byId(id: String): ShopItem? = items.firstOrNull { it.id == id }

    /** Other items sharing [id]'s exclusive group; buying [id] replaces these. */
    fun exclusiveRivals(id: String): List<ShopItem> {
        val group = byId(id)?.exclusiveGroup ?: return emptyList()
        return items.filter { it.exclusiveGroup == group && it.id != id }
    }

    fun forStore(shop: ShopStore): List<ShopItem> = items.filter { it.soldAt(shop) }
    fun featured(shop: ShopStore): List<ShopItem> = forStore(shop).filter { it.featured }
    fun deals(shop: ShopStore): List<ShopItem> = forStore(shop).filter { it.discountPercent != null }
}
