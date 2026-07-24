package com.example.sprocketslayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

fun loadPreviewBitmap(context: Context, uri: Uri): Bitmap {
    val source = decodeBitmap(context, uri)
    return cropToSprocketBitmap(source)
}

fun loadCroppedJpeg(context: Context, uri: Uri): ByteArray {
    val source = decodeBitmap(context, uri)
    val cropped = cropToSprocketBitmap(source)
    val out = ByteArrayOutputStream()
    cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    if (source !== cropped) source.recycle()
    cropped.recycle()
    return out.toByteArray()
}

private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
    return context.contentResolver.openInputStream(uri).use { stream ->
        BitmapFactory.decodeStream(stream) ?: error("Could not decode selected image")
    }
}

private fun cropToSprocketBitmap(source: Bitmap): Bitmap {
    val scale = max(
        SPROCKET_WIDTH.toFloat() / source.width.toFloat(),
        SPROCKET_HEIGHT.toFloat() / source.height.toFloat(),
    )
    val scaledWidth = (source.width * scale).toInt()
    val scaledHeight = (source.height * scale).toInt()
    val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
    val cropX = ((scaled.width - SPROCKET_WIDTH) / 2).coerceAtLeast(0)
    val cropY = ((scaled.height - SPROCKET_HEIGHT) / 2).coerceAtLeast(0)
    val cropped = Bitmap.createBitmap(scaled, cropX, cropY, SPROCKET_WIDTH, SPROCKET_HEIGHT)
    if (source !== scaled) source.recycle()
    if (scaled !== cropped) scaled.recycle()
    return cropped
}
