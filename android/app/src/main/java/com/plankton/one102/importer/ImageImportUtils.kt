package com.plankton.one102.importer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val DEFAULT_MAX_SIZE = 2600
private const val DEFAULT_JPEG_QUALITY = 92

private fun scaleBitmap(src: Bitmap, maxSize: Int): Bitmap {
    val w = src.width
    val h = src.height
    if (w <= maxSize && h <= maxSize) return src
    val scale = maxSize.toFloat() / maxOf(w, h).toFloat()
    val nw = (w * scale).toInt().coerceAtLeast(1)
    val nh = (h * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, nw, nh, true)
}

/**
 * Decode close to the final OCR size instead of allocating the camera's full-resolution bitmap
 * first.  Keeping up to 1.5x target detail avoids making handwriting blurrier than necessary.
 */
internal fun visionDecodeSampleSize(width: Int, height: Int, targetMaxSize: Int): Int {
    if (width <= 0 || height <= 0 || targetMaxSize <= 0) return 1
    val threshold = targetMaxSize.toDouble() * 1.5
    var sample = 1
    val largest = maxOf(width, height).toDouble()
    while (largest / sample > threshold && sample <= Int.MAX_VALUE / 2) {
        sample *= 2
    }
    return sample.coerceAtLeast(1)
}

private fun decodeBitmap(contentResolver: ContentResolver, uri: Uri, targetMaxSize: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = visionDecodeSampleSize(bounds.outWidth, bounds.outHeight, targetMaxSize)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    }
}

private fun readRawBytes(contentResolver: ContentResolver, uri: Uri): ByteArray? {
    return contentResolver.openInputStream(uri)?.use { it.readBytes() }
}

private fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

private fun splitBitmap(src: Bitmap, rows: Int, cols: Int): List<Bitmap> {
    if (rows <= 1 && cols <= 1) return emptyList()
    val result = mutableListOf<Bitmap>()
    val w = src.width
    val h = src.height
    val tileW = w / cols
    val tileH = h / rows
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val x = c * tileW
            val y = r * tileH
            val tw = if (c == cols - 1) w - x else tileW
            val th = if (r == rows - 1) h - y else tileH
            if (tw <= 0 || th <= 0) continue
            result += Bitmap.createBitmap(src, x, y, tw, th)
        }
    }
    return result
}

suspend fun buildVisionImageUrls(
    contentResolver: ContentResolver,
    uris: List<Uri>,
    maxSize: Int = DEFAULT_MAX_SIZE,
    jpegQuality: Int = DEFAULT_JPEG_QUALITY,
    tileRows: Int = 1,
    tileCols: Int = 1,
    includeFull: Boolean = true,
): List<String> = withContext(Dispatchers.IO) {
    uris.flatMap { uri ->
        val bitmap = decodeBitmap(contentResolver, uri, maxSize)
        val bytesList = if (bitmap != null) {
            val scaled = scaleBitmap(bitmap, maxSize)
            val tiles = buildList {
                if (includeFull) add(scaled)
                addAll(splitBitmap(scaled, tileRows, tileCols))
            }
            val encoded = tiles.map { tile ->
                val jpeg = bitmapToJpegBytes(tile, jpegQuality)
                if (tile != scaled) tile.recycle()
                jpeg
            }
            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()
            encoded
        } else {
            listOf(readRawBytes(contentResolver, uri) ?: error("读取图片失败：$uri"))
        }
        bytesList.map { bytes ->
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$encoded"
        }
    }
}
