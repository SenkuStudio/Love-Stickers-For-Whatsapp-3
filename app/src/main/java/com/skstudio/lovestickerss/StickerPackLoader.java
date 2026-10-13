/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.skstudio.lovestickerss;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.skstudio.lovestickerss.utils.StickerExportHelper;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.skstudio.lovestickerss.StickerContentProvider.ANDROID_APP_DOWNLOAD_LINK_IN_QUERY;
import static com.skstudio.lovestickerss.StickerContentProvider.ANIMATED_STICKER_PACK;
import static com.skstudio.lovestickerss.StickerContentProvider.AVOID_CACHE;
import static com.skstudio.lovestickerss.StickerContentProvider.IMAGE_DATA_VERSION;
import static com.skstudio.lovestickerss.StickerContentProvider.IOS_APP_DOWNLOAD_LINK_IN_QUERY;
import static com.skstudio.lovestickerss.StickerContentProvider.LICENSE_AGREENMENT_WEBSITE;
import static com.skstudio.lovestickerss.StickerContentProvider.PRIVACY_POLICY_WEBSITE;
import static com.skstudio.lovestickerss.StickerContentProvider.PUBLISHER_EMAIL;
import static com.skstudio.lovestickerss.StickerContentProvider.PUBLISHER_WEBSITE;
import static com.skstudio.lovestickerss.StickerContentProvider.STICKER_FILE_EMOJI_IN_QUERY;
import static com.skstudio.lovestickerss.StickerContentProvider.STICKER_FILE_NAME_IN_QUERY;
import static com.skstudio.lovestickerss.StickerContentProvider.STICKER_PACK_ICON_IN_QUERY;
import static com.skstudio.lovestickerss.StickerContentProvider.STICKER_PACK_IDENTIFIER_IN_QUERY;
import static com.skstudio.lovestickerss.StickerContentProvider.STICKER_PACK_NAME_IN_QUERY;
import static com.skstudio.lovestickerss.StickerContentProvider.STICKER_PACK_PUBLISHER_IN_QUERY;

class StickerPackLoader {

    public static class FetchResult {
        public final ArrayList<StickerPack> stickerPacks;
        public final boolean hasNextPage;

        public FetchResult(ArrayList<StickerPack> stickerPacks, boolean hasNextPage) {
            this.stickerPacks = stickerPacks;
            this.hasNextPage = hasNextPage;
        }
    }

    /**
     * Get the list of sticker packs for the sticker content provider
     */
    @NonNull
    static ArrayList<StickerPack> fetchStickerPacks(Context context) throws IllegalStateException {
        final Cursor cursor = context.getContentResolver().query(StickerContentProvider.AUTHORITY_URI, null, null, null, null);
        if (cursor == null) {
            throw new IllegalStateException("could not fetch from content provider, " + BuildConfig.CONTENT_PROVIDER_AUTHORITY);
        }
        HashSet<String> identifierSet = new HashSet<>();
        final ArrayList<StickerPack> stickerPackList = fetchFromContentProvider(cursor);
        for (StickerPack stickerPack : stickerPackList) {
            if (identifierSet.contains(stickerPack.identifier)) {
                throw new IllegalStateException("sticker pack identifiers should be unique, there are more than one pack with identifier:" + stickerPack.identifier);
            } else {
                identifierSet.add(stickerPack.identifier);
            }
        }
        if (stickerPackList.isEmpty()) {
            throw new IllegalStateException("There should be at least one sticker pack in the app");
        }
        for (StickerPack stickerPack : stickerPackList) {
            if (stickerPack.getStickers() == null || stickerPack.getStickers().isEmpty()) {
                final List<Sticker> stickers = getStickersForPack(context, stickerPack);
                stickerPack.setStickers(stickers);
            }
        }
        return stickerPackList;
    }

    public static ArrayList<StickerPack> fetchFromRemoteApi() throws Exception {
        return fetchFromRemoteApi(1, 200, null).stickerPacks;
    }

    public static FetchResult fetchFromRemoteApi(int page, int perPage, java.util.Map<String, StickerPack> packMap) throws Exception {
        ArrayList<StickerPack> stickerPackList = new ArrayList<>();
        String urlString = "https://senkustudio.com/api/v1/wallpapers?api_key=wapi_bb28a17432e27106346ed180197deecb078a64d56ab4673a"
                + "&page=" + page + "&per_page=" + perPage;
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.connect();

        boolean hasNextPage = false;
        if (connection.getResponseCode() == 200) {
            InputStream is = connection.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            String result = buffer.toString("UTF-8");
            JSONObject json = new JSONObject(result);
            if (json.getBoolean("success")) {
                if (json.has("pagination")) {
                    hasNextPage = json.getJSONObject("pagination").optBoolean("has_next", false);
                }
                JSONArray dataArray = json.getJSONArray("data");
                if (packMap == null) {
                    packMap = new java.util.LinkedHashMap<>();
                }

                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject item = dataArray.getJSONObject(i);
                    JSONObject category = item.getJSONObject("category");
                    String categorySlug = category.getString("slug");
                    String categoryName = category.getString("name");

                    int packIndex = 1;
                    String packId = categorySlug + "_" + packIndex;
                    StickerPack pack = packMap.get(packId);

                    while (pack != null && pack.getStickers().size() >= 30) {
                        packIndex++;
                        packId = categorySlug + "_" + packIndex;
                        pack = packMap.get(packId);
                    }

                    if (pack == null) {
                        pack = new StickerPack(
                                packId,
                                categoryName + (packIndex > 1 ? " " + packIndex : ""),
                                "SK Studio",
                                "tray_icon.webp",
                                item.getString("thumb_url"),
                                "skstudio@example.com",
                                "https://senkustudio.com",
                                "https://senkustudio.com/privacy",
                                "https://senkustudio.com/license",
                                "1",
                                false,
                                false
                        );
                        pack.category = categoryName;
                        pack.setStickers(new ArrayList<>());
                        packMap.put(packId, pack);
                    }

                    String slug = item.getString("slug");
                    String imageUrl = item.getString("image_url");
                    boolean isFeatured = item.optBoolean("is_featured", false);
                    Sticker sticker = new Sticker(slug + ".webp", Collections.singletonList("❤️"), imageUrl);
                    sticker.isFeatured = isFeatured;
                    if (isFeatured) {
                        pack.setAnimatedStickerPack(true);
                    }
                    pack.getStickers().add(sticker);
                }
                
                stickerPackList.addAll(packMap.values());
                // Filter out packs with less than 3 stickers
                java.util.Iterator<StickerPack> iterator = stickerPackList.iterator();
                while (iterator.hasNext()) {
                    if (iterator.next().getStickers().size() < 3) {
                        iterator.remove();
                    }
                }
            }
        }
        return new FetchResult(stickerPackList, hasNextPage);
    }

    private static final String REMOTE_PACKS_FILE = "remote_packs.json";

    public static void saveRemotePackInfo(Context context, StickerPack pack) {
        List<StickerPack> packs = loadRemotePacks(context);
        boolean found = false;
        for (int i = 0; i < packs.size(); i++) {
            if (packs.get(i).identifier.equals(pack.identifier)) {
                packs.set(i, pack);
                found = true;
                break;
            }
        }
        if (!found) {
            packs.add(pack);
        }
        saveRemotePacks(context, packs);
    }

    public static void saveRemotePacks(Context context, List<StickerPack> packs) {
        try {
            JSONArray array = new JSONArray();
            for (StickerPack pack : packs) {
                JSONObject packJson = new JSONObject();
                packJson.put("identifier", pack.identifier);
                packJson.put("name", pack.name);
                packJson.put("publisher", pack.publisher);
                packJson.put("tray_image_file", pack.trayImageFile);
                packJson.put("tray_image_url", pack.trayImageUrl);
                packJson.put("publisher_email", pack.publisherEmail);
                packJson.put("publisher_website", pack.publisherWebsite);
                packJson.put("privacy_policy_website", pack.privacyPolicyWebsite);
                packJson.put("license_agreement_website", pack.licenseAgreementWebsite);
                packJson.put("image_data_version", pack.imageDataVersion);
                packJson.put("avoid_cache", pack.avoidCache);
                packJson.put("animated_sticker_pack", pack.animatedStickerPack);
                packJson.put("android_play_store_link", pack.androidPlayStoreLink);
                packJson.put("ios_app_store_link", pack.iosAppStoreLink);

                JSONArray stickersArray = new JSONArray();
                for (Sticker sticker : pack.getStickers()) {
                    JSONObject stickerJson = new JSONObject();
                    stickerJson.put("image_file", sticker.imageFileName);
                    stickerJson.put("image_url", sticker.imageUrl);
                    stickerJson.put("is_featured", sticker.isFeatured);
                    JSONArray emojisArray = new JSONArray();
                    for (String emoji : sticker.emojis) {
                        emojisArray.put(emoji);
                    }
                    stickerJson.put("emojis", emojisArray);
                    stickersArray.put(stickerJson);
                }
                packJson.put("stickers", stickersArray);
                array.put(packJson);
            }
            File file = new File(context.getFilesDir(), REMOTE_PACKS_FILE);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(array.toString().getBytes());
            }
        } catch (Exception e) {
            Log.e("StickerPackLoader", "Error saving remote packs", e);
        }
    }

    public static List<StickerPack> loadRemotePacks(Context context) {
        File file = new File(context.getFilesDir(), REMOTE_PACKS_FILE);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (InputStream is = new java.io.FileInputStream(file)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            String result = buffer.toString("UTF-8");
            JSONArray array = new JSONArray(result);
            List<StickerPack> packs = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject packJson = array.getJSONObject(i);
                StickerPack pack = new StickerPack(
                        packJson.getString("identifier"),
                        packJson.getString("name"),
                        packJson.getString("publisher"),
                        packJson.getString("tray_image_file"),
                        packJson.optString("tray_image_url", null),
                        packJson.getString("publisher_email"),
                        packJson.getString("publisher_website"),
                        packJson.getString("privacy_policy_website"),
                        packJson.getString("license_agreement_website"),
                        packJson.getString("image_data_version"),
                        packJson.getBoolean("avoid_cache"),
                        packJson.optBoolean("animated_sticker_pack", false)
                );
                pack.setAndroidPlayStoreLink(packJson.optString("android_play_store_link", null));
                pack.setIosAppStoreLink(packJson.optString("ios_app_store_link", null));
                
                JSONArray stickersArray = packJson.getJSONArray("stickers");
                List<Sticker> stickers = new ArrayList<>();
                for (int j = 0; j < stickersArray.length(); j++) {
                    JSONObject stickerJson = stickersArray.getJSONObject(j);
                    Sticker sticker = new Sticker(
                            stickerJson.getString("image_file"),
                            new ArrayList<>(),
                            stickerJson.optString("image_url", null)
                    );
                    sticker.isFeatured = stickerJson.optBoolean("is_featured", false);
                    JSONArray emojisArray = stickerJson.getJSONArray("emojis");
                    for (int k = 0; k < emojisArray.length(); k++) {
                        sticker.emojis.add(emojisArray.getString(k));
                    }
                    stickers.add(sticker);
                }
                pack.setStickers(stickers);
                packs.add(pack);
            }
            return packs;
        } catch (Exception e) {
            Log.e("StickerPackLoader", "Error loading remote packs", e);
            return new ArrayList<>();
        }
    }

    public interface ProgressListener {
        void onProgressUpdate(int percent);
    }

    public static void downloadStickers(Context context, StickerPack stickerPack, Runnable callback) {
        downloadStickers(context, stickerPack, null, callback);
    }

    public static void downloadStickers(Context context, StickerPack stickerPack, ProgressListener progressListener, Runnable callback) {
        new Thread(() -> {
            try {
                // Save metadata first so ContentProvider can see it if it reloads
                saveRemotePackInfo(context, stickerPack);

                File stickerDir = new File(context.getFilesDir(), "stickers/" + stickerPack.identifier);
                if (!stickerDir.exists()) {
                    stickerDir.mkdirs();
                }

                List<Sticker> stickers = stickerPack.getStickers();
                int totalCount = stickers.size() + 1; // +1 for tray icon
                int completedCount = 0;

                for (Sticker sticker : stickers) {
                    File outputFile = new File(stickerDir, sticker.imageFileName);
                    boolean needsDownload = !outputFile.exists();
                    if (!needsDownload) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(outputFile.getAbsolutePath(), options);
                        if (options.outWidth != 512 || options.outHeight != 512) {
                            needsDownload = true;
                        }
                        
                        // Also check size limit (100KB for static, 500KB for animated)
                        long limit = stickerPack.animatedStickerPack ? 500 * 1024 : 100 * 1024;
                        if (outputFile.length() > limit) {
                            needsDownload = true;
                        }
                    }
                    if (needsDownload) {
                        if (sticker.imageUrl != null) {
                            try {
                                URL url = new URL(sticker.imageUrl);
                                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                                urlConnection.setConnectTimeout(15000);
                                urlConnection.setReadTimeout(30000);
                                urlConnection.connect();
                                try (InputStream input = urlConnection.getInputStream()) {
                                    if (sticker.isFeatured) {
                                        try (FileOutputStream output = new FileOutputStream(outputFile)) {
                                            byte[] bufferBytes = new byte[16384];
                                            int read;
                                            while ((read = input.read(bufferBytes)) != -1) {
                                                output.write(bufferBytes, 0, read);
                                            }
                                        }
                                    } else {
                                        Bitmap bitmap = BitmapFactory.decodeStream(input);
                                        if (bitmap != null) {
                                            // Process and export the sticker using the helper
                                            Bitmap finalBitmap = StickerExportHelper.prepareSticker(bitmap);
                                            try (FileOutputStream output = new FileOutputStream(outputFile)) {
                                                long limit = stickerPack.animatedStickerPack ? 500 * 1024 : 100 * 1024;
                                                StickerExportHelper.exportToWebP(finalBitmap, output, (int) limit);
                                            }
                                            if (finalBitmap != bitmap) {
                                                finalBitmap.recycle();
                                            }
                                            bitmap.recycle();
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Log.e("StickerPackLoader", "Error downloading/processing sticker: " + sticker.imageUrl, e);
                            }
                        }
                    }
                    if (outputFile.exists()) {
                        sticker.setSize(outputFile.length());
                    }
                    completedCount++;
                    if (progressListener != null) {
                        int progress = (int) ((completedCount / (float) totalCount) * 100);
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> progressListener.onProgressUpdate(progress));
                    }
                }

                File trayFile = new File(stickerDir, stickerPack.trayImageFile);
                if (trayFile.exists() && trayFile.getName().endsWith(".png")) {
                    trayFile.delete();
                    stickerPack.trayImageFile = "tray_icon.webp";
                    trayFile = new File(stickerDir, stickerPack.trayImageFile);
                }
                if (!trayFile.exists()) {
                    Bitmap bitmap = null;
                    if (stickerPack.trayImageUrl != null) {
                        try {
                            URL url = new URL(stickerPack.trayImageUrl);
                            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                            urlConnection.setConnectTimeout(15000);
                            urlConnection.setReadTimeout(30000);
                            urlConnection.setDoInput(true);
                            urlConnection.connect();
                            try (InputStream input = urlConnection.getInputStream()) {
                                bitmap = BitmapFactory.decodeStream(input);
                            }
                        } catch (Exception e) {
                            Log.e("StickerPackLoader", "Error downloading tray icon: " + stickerPack.trayImageUrl, e);
                        }
                    }

                        if (bitmap != null) {
                            // WhatsApp tray icons must be between 24x24 and 512x512, recommended 96x96
                            // We use StickerExportHelper.prepareSticker(bitmap) to ensure it's valid, but tray is usually smaller.
                            // Actually, WhatsApp says tray icon should be 96x96 and < 50KB.
                                Bitmap resizedBitmap = prepareStickerBitmap(bitmap, 96);
                                try (FileOutputStream out = new FileOutputStream(trayFile)) {
                                    // Tray icons must be < 50KB
                                    StickerExportHelper.exportToWebP(resizedBitmap, out, 50 * 1024);
                                }
                            if (resizedBitmap != bitmap) {
                                resizedBitmap.recycle();
                            }
                            bitmap.recycle();
                        }
                }
                
                completedCount++;
                if (progressListener != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> progressListener.onProgressUpdate(100));
                }
                
                // Recalculate total size
                long totalSize = 0;
                for (Sticker sticker : stickerPack.getStickers()) {
                    totalSize += sticker.size;
                }
                // We can't set it directly as it's private, but setStickers does it
                stickerPack.setStickers(stickerPack.getStickers());

                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(callback);
                }
            } catch (Exception e) {
                Log.e("StickerPackLoader", "Error downloading stickers for " + stickerPack.identifier, e);
            }
        }).start();
    }

    private static Bitmap prepareStickerBitmap(Bitmap bitmap, int size) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // If it's already the target size, just return it.
        if (width == size && height == size) {
            return bitmap;
        }

        // For stickers (size 512), WhatsApp recommends a 16px margin.
        // For tray icons (size 96), we can just scale to fit.
        float scale = Math.min((float) size / width, (float) size / height);

        int newWidth = Math.max(1, Math.round(width * scale));
        int newHeight = Math.max(1, Math.round(height * scale));
        
        // Ensure we don't exceed the target size due to rounding
        if (newWidth > size) newWidth = size;
        if (newHeight > size) newHeight = size;

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);

        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        float left = (size - newWidth) / 2f;
        float top = (size - newHeight) / 2f;
        canvas.drawBitmap(scaledBitmap, left, top, null);

        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle();
        }
        return output;
    }

    public static boolean isStickerPackDownloaded(Context context, StickerPack pack) {
        File stickerDir = new File(context.getFilesDir(), "stickers/" + pack.identifier);
        if (!stickerDir.exists()) return false;

        File trayFile = new File(stickerDir, pack.trayImageFile);
        if (!trayFile.exists() || trayFile.length() > 50 * 1024) return false;

        if (pack.getStickers() == null) return false;
        for (Sticker sticker : pack.getStickers()) {
            File stickerFile = new File(stickerDir, sticker.imageFileName);
            if (!stickerFile.exists()) return false;

            // Check size limit
            long limit = pack.animatedStickerPack ? 500 * 1024 : 100 * 1024;
            if (stickerFile.length() > limit) return false;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(stickerFile.getAbsolutePath(), options);
            if (options.outWidth != 512 || options.outHeight != 512) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private static List<Sticker> getStickersForPack(Context context, StickerPack stickerPack) {
        final List<Sticker> stickers = fetchFromContentProviderForStickers(stickerPack.identifier, context.getContentResolver());
        for (Sticker sticker : stickers) {
            final byte[] bytes;
            try {
                bytes = fetchStickerAsset(stickerPack.identifier, sticker.imageFileName, context.getContentResolver());
                if (bytes.length <= 0) {
                    throw new IllegalStateException("Asset file is empty, pack: " + stickerPack.name + ", sticker: " + sticker.imageFileName);
                }
                sticker.setSize(bytes.length);
            } catch (IOException | IllegalArgumentException e) {
                throw new IllegalStateException("Asset file doesn't exist. pack: " + stickerPack.name + ", sticker: " + sticker.imageFileName, e);
            }
        }
        return stickers;
    }


    @NonNull
    private static ArrayList<StickerPack> fetchFromContentProvider(Cursor cursor) {
        ArrayList<StickerPack> stickerPackList = new ArrayList<>();
        cursor.moveToFirst();
        do {
            final String identifier = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_IDENTIFIER_IN_QUERY));
            final String name = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_NAME_IN_QUERY));
            final String publisher = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_PUBLISHER_IN_QUERY));
            final String trayImage = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_ICON_IN_QUERY));
            final String androidPlayStoreLink = cursor.getString(cursor.getColumnIndexOrThrow(ANDROID_APP_DOWNLOAD_LINK_IN_QUERY));
            final String iosAppLink = cursor.getString(cursor.getColumnIndexOrThrow(IOS_APP_DOWNLOAD_LINK_IN_QUERY));
            final String publisherEmail = cursor.getString(cursor.getColumnIndexOrThrow(PUBLISHER_EMAIL));
            final String publisherWebsite = cursor.getString(cursor.getColumnIndexOrThrow(PUBLISHER_WEBSITE));
            final String privacyPolicyWebsite = cursor.getString(cursor.getColumnIndexOrThrow(PRIVACY_POLICY_WEBSITE));
            final String licenseAgreementWebsite = cursor.getString(cursor.getColumnIndexOrThrow(LICENSE_AGREENMENT_WEBSITE));
            final String imageDataVersion = cursor.getString(cursor.getColumnIndexOrThrow(IMAGE_DATA_VERSION));
            final boolean avoidCache = cursor.getShort(cursor.getColumnIndexOrThrow(AVOID_CACHE)) > 0;
            final boolean animatedStickerPack = cursor.getShort(cursor.getColumnIndexOrThrow(ANIMATED_STICKER_PACK)) > 0;
            final StickerPack stickerPack = new StickerPack(identifier, name, publisher, trayImage, publisherEmail, publisherWebsite, privacyPolicyWebsite, licenseAgreementWebsite, imageDataVersion, avoidCache, animatedStickerPack);
            stickerPack.setAndroidPlayStoreLink(androidPlayStoreLink);
            stickerPack.setIosAppStoreLink(iosAppLink);
            stickerPackList.add(stickerPack);
        } while (cursor.moveToNext());
        return stickerPackList;
    }

    @NonNull
    private static List<Sticker> fetchFromContentProviderForStickers(String identifier, ContentResolver contentResolver) {
        Uri uri = getStickerListUri(identifier);

        final String[] projection = {STICKER_FILE_NAME_IN_QUERY, STICKER_FILE_EMOJI_IN_QUERY};
        final Cursor cursor = contentResolver.query(uri, projection, null, null, null);
        List<Sticker> stickers = new ArrayList<>();
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                final String name = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_FILE_NAME_IN_QUERY));
                final String emojisConcatenated = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_FILE_EMOJI_IN_QUERY));
                List<String> emojis = new ArrayList<>(StickerPackValidator.EMOJI_MAX_LIMIT);
                if (!TextUtils.isEmpty(emojisConcatenated)) {
                    emojis = Arrays.asList(emojisConcatenated.split(","));
                }
                stickers.add(new Sticker(name, emojis));
            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
        }
        return stickers;
    }

    static byte[] fetchStickerAsset(@NonNull final String identifier, @NonNull final String name, ContentResolver contentResolver) throws IOException {
        try (final InputStream inputStream = contentResolver.openInputStream(getStickerAssetUri(identifier, name));
             final ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                throw new IOException("cannot read sticker asset:" + identifier + "/" + name);
            }
            int read;
            byte[] data = new byte[16384];

            while ((read = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private static Uri getStickerListUri(String identifier) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(BuildConfig.CONTENT_PROVIDER_AUTHORITY).appendPath(StickerContentProvider.STICKERS).appendPath(identifier).build();
    }

    static Uri getStickerAssetUri(String identifier, String stickerName) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(BuildConfig.CONTENT_PROVIDER_AUTHORITY).appendPath(StickerContentProvider.STICKERS_ASSET).appendPath(identifier).appendPath(stickerName).build();
    }

    static Uri getStickerUri(Context context, StickerPack pack, Sticker sticker) {
        if (pack.isDownloaded()) {
            return getStickerAssetUri(pack.identifier, sticker.imageFileName);
        }
        if (!TextUtils.isEmpty(sticker.imageUrl)) {
            return Uri.parse(sticker.imageUrl);
        }
        return getStickerAssetUri(pack.identifier, sticker.imageFileName);
    }

    static Uri getTrayIconUri(Context context, StickerPack pack) {
        if (pack.isDownloaded()) {
            return getStickerAssetUri(pack.identifier, pack.trayImageFile);
        }
        if (!TextUtils.isEmpty(pack.trayImageUrl)) {
            return Uri.parse(pack.trayImageUrl);
        }
        return getStickerAssetUri(pack.identifier, pack.trayImageFile);
    }
}
