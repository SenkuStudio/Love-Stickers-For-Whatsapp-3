package com.skstudio.BabyWAstickersApp.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Helper class for processing and exporting stickers.
 * Converted to Java to avoid Kotlin K2 compiler internal error (IllegalArgumentException: source must not be null).
 */
public class StickerExportHelper {

    private static final int STICKER_SIZE = 512;

    public static Bitmap prepareSticker(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();

        float scale = (float) STICKER_SIZE / Math.max(width, height);

        int finalWidth;
        int finalHeight;
        if (width >= height) {
            finalWidth = STICKER_SIZE;
            finalHeight = Math.max(1, Math.round(height * scale));
        } else {
            finalHeight = STICKER_SIZE;
            finalWidth = Math.max(1, Math.round(width * scale));
        }

        Bitmap output = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        float left = (STICKER_SIZE - finalWidth) / 2f;
        float top = (STICKER_SIZE - finalHeight) / 2f;

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        
        Bitmap scaledSource = Bitmap.createScaledBitmap(source, finalWidth, finalHeight, true);
        canvas.drawBitmap(scaledSource, left, top, paint);
        
        if (scaledSource != source) {
            scaledSource.recycle();
        }

        return output;
    }

    public static void exportToWebP(Bitmap bitmap, OutputStream outputStream) throws IOException {
        exportToWebP(bitmap, outputStream, 100 * 1024);
    }

    public static void exportToWebP(Bitmap bitmap, OutputStream outputStream, int maxFileSize) throws IOException {
        int quality = 80;
        byte[] bytes = new byte[0];

        // Using a simple selection for the format. 
        // WEBP_LOSSY is available from API 30.
        Bitmap.CompressFormat format;
        if (Build.VERSION.SDK_INT >= 30) {
            format = Bitmap.CompressFormat.WEBP_LOSSY;
        } else {
            //noinspection deprecation
            format = Bitmap.CompressFormat.WEBP;
        }

        do {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (bitmap.compress(format, quality, bos)) {
                bytes = bos.toByteArray();
            }
            quality -= 10;
        } while (bytes.length > maxFileSize && quality >= 30);

        outputStream.write(bytes);
    }
}
