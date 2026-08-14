package com.skstudio.BabyWAstickersApp.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import java.io.OutputStream

object StickerExportHelper {

    private const val STICKER_SIZE = 512

    /**
     * Prepares a bitmap for WhatsApp sticker export.
     * - Ensures the output is exactly 512x512.
     * - Centers the original image on a transparent canvas.
     * - Scales the image so that its longest side is 512 pixels, preserving aspect ratio.
     * - Upscales small images to fit the 512x512 requirement.
     *
     * @param source The source bitmap to process.
     * @return A processed 512x512 bitmap.
     */
    @JvmStatic
    fun prepareSticker(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height

        // WhatsApp stickers must be exactly 512x512.
        // We scale the image so that its longest side is 512 pixels to "stretch" it to fit.
        val scale = STICKER_SIZE.toFloat() / Math.max(width, height)

        val finalWidth: Int
        val finalHeight: Int
        if (width >= height) {
            finalWidth = STICKER_SIZE
            finalHeight = Math.max(1, Math.round(height * scale))
        } else {
            finalHeight = STICKER_SIZE
            finalWidth = Math.max(1, Math.round(width * scale))
        }

        // Create the 512x512 transparent canvas
        val output = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Calculate centering offsets
        val left = (STICKER_SIZE - finalWidth) / 2f
        val top = (STICKER_SIZE - finalHeight) / 2f

        // Draw the bitmap on the canvas
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        
        val scaledSource = Bitmap.createScaledBitmap(source, finalWidth, finalHeight, true)
        canvas.drawBitmap(scaledSource, left, top, paint)
        
        if (scaledSource != source) {
            scaledSource.recycle()
        }

        return output
    }

    /**
     * Exports the bitmap to the given output stream as WebP.
     * Automatically adjusts quality to stay under the limit.
     */
    @JvmStatic
    @JvmOverloads
    fun exportToWebP(bitmap: Bitmap, outputStream: java.io.OutputStream, maxFileSize: Int = 100 * 1024) {
        var quality = 80
        
        var bytes: ByteArray
        do {
            val bos = java.io.ByteArrayOutputStream()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, bos)
            } else {
                bitmap.compress(Bitmap.CompressFormat.WEBP, quality, bos)
            }
            bytes = bos.toByteArray()
            quality -= 10
        } while (bytes.size > maxFileSize && quality >= 30)

        outputStream.write(bytes)
    }
}
