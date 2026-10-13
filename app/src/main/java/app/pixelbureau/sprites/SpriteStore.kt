package app.pixelbureau.sprites

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SpriteStore(private val context: Context) {
    private val bitmaps = HashMap<String, Bitmap>()

    @Volatile
    var ready: Boolean = false
        private set

    /** Housing center of the server fan, as a fraction of the split bitmap. The blades spin around this. */
    var serverFanPivotX: Float = 0.5f
        private set

    var serverFanPivotY: Float = 0.5f
        private set

    fun get(name: String): Bitmap? = bitmaps[name]

    suspend fun load() = withContext(Dispatchers.Default) {
        if (ready) {
            return@withContext
        }
        val cacheDir = File(context.filesDir, "sprites-v54").apply { mkdirs() }
        for (name in names) {
            val cache = File(cacheDir, name.replace('/', '_') + ".png")
            val bitmap = if (cache.exists()) {
                BitmapFactory.decodeFile(cache.absolutePath)
            } else {
                val decoded = decodeAsset(name) ?: continue
                val cropped = keyAndCrop(decoded)
                if (cropped !== decoded) {
                    decoded.recycle()
                }
                FileOutputStream(cache).use { stream ->
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                cropped
            }
            if (bitmap != null) {
                bitmaps[name] = bitmap
            }
        }
        splitServerFanLayers()
        ready = true
    }

    private fun splitServerFanLayers() {
        val fan = bitmaps["props/server_fan"] ?: return
        val width = fan.width
        val height = fan.height
        if (width <= 0 || height <= 0) return
        val pixels = IntArray(width * height)
        fan.getPixels(pixels, 0, width, 0, 0, width, height)
        val split = splitServerFan(pixels, width, height)
        bitmaps["props/server_fan_frame"] = pixelsToBitmap(split.frame, width, height)
        bitmaps["props/server_fan_rotor"] = pixelsToBitmap(split.rotor, width, height)
        serverFanPivotX = split.centerX / width
        serverFanPivotY = split.centerY / height
    }

    private fun pixelsToBitmap(pixels: IntArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun decodeAsset(name: String): Bitmap? {
        return try {
            context.assets.open("sprites/$name.png").use { stream ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 4
                    inScaled = false
                }
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /**
         * Prop registry: every media/sprites/props/<name>.png the app loads.
         * Also feeds the shop catalog (Pixel_Bureau_Web/catalog/ShopCatalog), so a
         * prop added here is listed in Pazada automatically.
         */
        val propNames: List<String> = listOf(
            "beanbag", "bike", "bookshelf", "chair", "coffee", "desk", "desk_bare", "desk_top",
            "foosball", "fridge", "fridge_open", "plant", "plant2", "plant_top", "server", "server_fan", "sofa",
            "trash", "watercooler", "whiteboard",
            // v0.1.61 Pazada stock (scripts/gen_pazada_props.py) + oak desk map variant.
            "aquarium", "arcade", "cactus", "clock", "desk_oak", "filing_cabinet", "floor_lamp", "globe",
            "guitar", "neon_sign", "picture_frame", "pouf", "printer", "storage_box", "vending",
        )

        /**
         * State/layer art for another prop (not a standalone shop item).
         * desk_oak is the map art for the owned Oakwood desk (office_desk → desk_bare listing).
         */
        val propLayers: Set<String> = setOf("fridge_open", "server_fan", "desk_oak")

        val names: List<String> = buildList {
            val people = listOf("byte", "cursor", "hugo", "kai", "leif", "luna", "mina", "nico", "nola", "ops", "otto", "pixel", "reid", "sora", "vera", "you")
            val frames = listOf(
                // Desk sit/type use these default sheets + left flip (no *_back).
                "idle", "sit", "sleep", "type0", "type1",
                "drink0", "drink1",
                "walk0", "walk1", "walk2", "walk3",
                "work0", "work1",
            )
            for (person in people) {
                for (frame in frames) {
                    add("chars/${person}_$frame")
                }
            }
            for (prop in propNames) {
                add("props/$prop")
            }
            for (tile in listOf("floor", "grass", "windows")) {
                add("tiles/$tile")
            }
        }
    }
}

internal fun keyAndCrop(src: Bitmap): Bitmap {
    val width = src.width
    val height = src.height
    val pixels = IntArray(width * height)
    src.getPixels(pixels, 0, width, 0, 0, width, height)
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    for (index in pixels.indices) {
        val color = pixels[index]
        if (isSpriteKey(color)) {
            pixels[index] = 0
        } else {
            val x = index % width
            val y = index / width
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            pixels[index] = (0xFF shl 24) or (color and 0x00FFFFFF)
        }
    }
    if (maxX < minX || maxY < minY) {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
    val cropW = maxX - minX + 1
    val cropH = maxY - minY + 1
    val out = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
    out.setPixels(pixels, minY * width + minX, width, 0, 0, cropW, cropH)
    return out
}

/**
 * Magenta backdrop key. Prefer true magenta (high R&B), and only catch
 * darker purple edges when R≈B. Kai's dark maroon shirt (R-dominant,
 * low B) must NOT match — that keyed the whole torso invisible (v21).
 */
internal fun isSpriteKey(color: Int): Boolean {
    val red = (color shr 16) and 0xFF
    val green = (color shr 8) and 0xFF
    val blue = color and 0xFF
    if (red > 130 && blue > 130 && green < red && green < blue &&
        red + blue - 2 * green > 50
    ) {
        return true
    }
    return red > green * 1.6f &&
        blue > green * 1.6f &&
        red + blue - 2 * green > 55 &&
        maxOf(red, blue) > 60 &&
        minOf(red, blue) > 65 &&
        kotlin.math.abs(red - blue) <= 28
}
