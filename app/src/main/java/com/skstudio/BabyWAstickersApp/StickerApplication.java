/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.skstudio.BabyWAstickersApp;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.facebook.cache.disk.DiskCacheConfig;
import com.facebook.common.util.ByteConstants;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.backends.okhttp3.OkHttpImagePipelineConfigFactory;
import android.graphics.Bitmap;
import com.facebook.imagepipeline.core.ImagePipelineConfig;

import okhttp3.OkHttpClient;

import com.facebook.imagepipeline.cache.MemoryCacheParams;
import com.facebook.common.internal.Supplier;
import android.app.ActivityManager;

public class StickerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        DiskCacheConfig diskCacheConfig = DiskCacheConfig.newBuilder(this)
                .setBaseDirectoryPath(getCacheDir())
                .setBaseDirectoryName("sticker_cache")
                .setMaxCacheSize(100 * ByteConstants.MB)
                .setMaxCacheSizeOnLowDiskSpace(20 * ByteConstants.MB)
                .setMaxCacheSizeOnVeryLowDiskSpace(10 * ByteConstants.MB)
                .build();

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .build();

        final ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        Supplier<MemoryCacheParams> memoryCacheParamsSupplier = new Supplier<MemoryCacheParams>() {
            @Override
            public MemoryCacheParams get() {
                if (activityManager == null) return new MemoryCacheParams(0, 0, 0, 0, 0);
                
                int maxMemory = Math.min(activityManager.getMemoryClass() * ByteConstants.MB, Integer.MAX_VALUE);
                int maxCacheSize = maxMemory / 4; // Use 25% of available memory for Fresco
                
                return new MemoryCacheParams(
                        maxCacheSize,
                        256, // Max entries in cache
                        maxCacheSize,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE
                );
            }
        };

        ImagePipelineConfig config = OkHttpImagePipelineConfigFactory
                .newBuilder(this, okHttpClient)
                .setMainDiskCacheConfig(diskCacheConfig)
                .setBitmapMemoryCacheParamsSupplier(memoryCacheParamsSupplier)
                .setDownsampleEnabled(true)
                .setBitmapsConfig(Bitmap.Config.ARGB_8888) // Reverting to ARGB_8888 because stickers need transparency
                .build();

        Fresco.initialize(this, config);
        
        SharedPreferences prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE);
        int savedTheme = prefs.getInt("selected_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedTheme);
    }
}
