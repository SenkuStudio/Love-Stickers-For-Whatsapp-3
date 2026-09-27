package com.skstudio.lovestickers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "settings_prefs";
    private static final String KEY_THEME = "selected_theme";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        view.findViewById(R.id.settings_light_theme).setOnClickListener(v -> setTheme(AppCompatDelegate.MODE_NIGHT_NO));
        
        view.findViewById(R.id.settings_dark_theme).setOnClickListener(v -> setTheme(AppCompatDelegate.MODE_NIGHT_YES));

        view.findViewById(R.id.settings_how_to_use).setOnClickListener(v -> showHowToUse());
        view.findViewById(R.id.settings_share).setOnClickListener(v -> shareApp());
        view.findViewById(R.id.settings_rate).setOnClickListener(v -> rateApp());
        view.findViewById(R.id.settings_more_apps).setOnClickListener(v -> moreApps());
        view.findViewById(R.id.settings_contact_us).setOnClickListener(v -> contactUs());
        
        view.findViewById(R.id.btn_go_premium).setOnClickListener(v -> {
            Toast.makeText(getContext(), "Premium features coming soon!", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.settings_request_sticker).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "skstudio@example.com", null));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Sticker Request: " + getString(R.string.app_name));
            startActivity(Intent.createChooser(intent, "Send request..."));
        });

        view.findViewById(R.id.settings_clear_cache).setOnClickListener(v -> clearCache());

        // view.findViewById(R.id.settings_ad_inspector).setOnClickListener(v -> openAdInspector());

        loadNativeAd(view);

        return view;
    }

    private void loadNativeAd(View view) {
        if (getActivity() instanceof BaseActivity) {
            BaseActivity activity = (BaseActivity) getActivity();
            activity.loadNativeAd(getString(R.string.admob_native_ad_unit_id), view.findViewById(R.id.native_ad_container));
            
            // Ensure visibility state is respected in the fragment as well
            View nativeAdContainer = view.findViewById(R.id.native_ad_container);
            if (nativeAdContainer != null) {
                nativeAdContainer.setVisibility(activity.isAdsToggledVisible() ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void setTheme(int mode) {
        AppCompatDelegate.setDefaultNightMode(mode);
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME, mode).apply();
    }

    private void shareApp() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, "https://play.google.com/store/apps/details?id=" + requireContext().getPackageName()));
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share via"));
    }

    private void showHowToUse() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.how_to_use_title)
                .setMessage(R.string.how_to_use_content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void contactUs() {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "skstudio@example.com", null));
        intent.putExtra(Intent.EXTRA_SUBJECT, "Support: " + getString(R.string.app_name));
        startActivity(Intent.createChooser(intent, "Send email..."));
    }

    private void rateApp() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + requireContext().getPackageName())));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + requireContext().getPackageName())));
        }
    }

    private void moreApps() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://dev?id=8066128835537801410")));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/dev?id=8066128835537801410")));
        }
    }

    private void clearCache() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_clear_cache)
                .setMessage("Are you sure you want to clear the cached sticker packs? They will be reloaded next time you have internet connection.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    java.io.File file = new java.io.File(requireContext().getFilesDir(), "remote_packs.json");
                    if (file.exists()) {
                        file.delete();
                    }
                    // Also clear Fresco cache
                    com.facebook.drawee.backends.pipeline.Fresco.getImagePipeline().clearCaches();
                    Toast.makeText(getContext(), R.string.cache_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

/*
    private void openAdInspector() {
        MobileAds.openAdInspector(requireContext(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), "Ad Inspector error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
*/

}
