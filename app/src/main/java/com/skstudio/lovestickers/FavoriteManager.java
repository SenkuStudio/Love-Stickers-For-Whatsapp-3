package com.skstudio.lovestickers;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class FavoriteManager {
    private static final String PREF_NAME = "sticker_favorites";
    private static final String KEY_FAVORITES = "favorite_packs";
    private final SharedPreferences prefs;
    private final Set<String> cachedFavorites;

    public FavoriteManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        cachedFavorites = new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    public boolean isFavorite(String identifier) {
        return cachedFavorites.contains(identifier);
    }

    public void toggleFavorite(String identifier) {
        if (cachedFavorites.contains(identifier)) {
            cachedFavorites.remove(identifier);
        } else {
            cachedFavorites.add(identifier);
        }
        prefs.edit().putStringSet(KEY_FAVORITES, new HashSet<>(cachedFavorites)).apply();
    }
}
