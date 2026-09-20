/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.skstudio.BabyWAstickersApp;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

public abstract class BaseActivity extends AppCompatActivity {
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private boolean isAdsToggledVisible = true;

    protected void toggleAdsVisibility(boolean show) {
        isAdsToggledVisible = show;
        View adView = findViewById(R.id.adView);
        View adView1 = findViewById(R.id.adView1);
        View adView10 = findViewById(R.id.adView10);
        View nativeAdContainer = findViewById(R.id.native_ad_container);
        View bottomAdContainer = findViewById(R.id.bottom_ad_container);
        
        int visibility = show ? View.VISIBLE : View.GONE;
        
        if (adView != null) adView.setVisibility(visibility);
        if (adView1 != null) adView1.setVisibility(visibility);
        if (adView10 != null) adView10.setVisibility(visibility);
        if (nativeAdContainer != null) nativeAdContainer.setVisibility(visibility);
        if (bottomAdContainer != null) bottomAdContainer.setVisibility(visibility);
        
        View shimmerViewContainer = findViewById(R.id.shimmer_view_container);
        if (shimmerViewContainer != null) shimmerViewContainer.setVisibility(visibility);
    }

    protected void loadNativeAd(String adUnitId, ViewGroup adContainer) {
        if (adContainer == null || isFinishing() || isDestroyed()) return;
        
        if (!StickerApplication.isAdsInitialized()) {
            adContainer.postDelayed(() -> loadNativeAd(adUnitId, adContainer), 1000);
            return;
        }

        AdLoader adLoader = new AdLoader.Builder(this, adUnitId)
                .forNativeAd(nativeAd -> {
                    if (adContainer == null || isFinishing() || isDestroyed()) {
                        nativeAd.destroy();
                        return;
                    }
                    try {
                        NativeAdView adView = (NativeAdView) getLayoutInflater()
                                .inflate(R.layout.layout_native_ad, adContainer, false);
                        populateNativeAdView(nativeAd, adView);
                        adContainer.removeAllViews();
                        adContainer.addView(adView);
                        
                        // Only show if the global toggle allows it
                        adContainer.setVisibility(isAdsToggledVisible ? View.VISIBLE : View.GONE);
                        
                        View shimmer = adView.findViewById(R.id.shimmer_layout);
                        if (shimmer != null) shimmer.setVisibility(View.GONE);
                        View content = adView.findViewById(R.id.ad_content);
                        if (content != null) content.setVisibility(View.VISIBLE);
                    } catch (Exception e) {
                        e.printStackTrace();
                        nativeAd.destroy();
                    }
                })
                .build();
        adLoader.loadAd(new AdRequest.Builder().build());
    }

    public static void populateNativeAdView(NativeAd nativeAd, NativeAdView adView) {
        adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
        adView.setBodyView(adView.findViewById(R.id.ad_body));
        adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
        adView.setIconView(adView.findViewById(R.id.ad_app_icon));
        adView.setAdvertiserView(adView.findViewById(R.id.ad_advertiser));
        
        View mediaView = adView.findViewById(R.id.ad_media);
        if (mediaView instanceof MediaView) {
            adView.setMediaView((MediaView) mediaView);
        }

        if (adView.getHeadlineView() instanceof TextView) {
            ((TextView) adView.getHeadlineView()).setText(nativeAd.getHeadline());
        }

        View adAttribution = adView.findViewById(R.id.ad_attribution);
        if (adAttribution != null) {
            adAttribution.setVisibility(View.VISIBLE);
        }
        
        if (adView.getMediaView() != null && nativeAd.getMediaContent() != null) {
            adView.getMediaView().setMediaContent(nativeAd.getMediaContent());
        }

        if (nativeAd.getBody() == null) {
            if (adView.getBodyView() != null) adView.getBodyView().setVisibility(View.INVISIBLE);
        } else {
            if (adView.getBodyView() instanceof TextView) {
                adView.getBodyView().setVisibility(View.VISIBLE);
                ((TextView) adView.getBodyView()).setText(nativeAd.getBody());
            }
        }

        if (nativeAd.getCallToAction() == null) {
            if (adView.getCallToActionView() != null) adView.getCallToActionView().setVisibility(View.INVISIBLE);
        } else {
            if (adView.getCallToActionView() instanceof Button) {
                adView.getCallToActionView().setVisibility(View.VISIBLE);
                ((Button) adView.getCallToActionView()).setText(nativeAd.getCallToAction());
            }
        }

        if (nativeAd.getIcon() == null) {
            if (adView.getIconView() != null) adView.getIconView().setVisibility(View.GONE);
        } else {
            if (adView.getIconView() instanceof ImageView) {
                ((ImageView) adView.getIconView()).setImageDrawable(nativeAd.getIcon().getDrawable());
                adView.getIconView().setVisibility(View.VISIBLE);
            }
        }

        if (nativeAd.getAdvertiser() == null) {
            if (adView.getAdvertiserView() != null) adView.getAdvertiserView().setVisibility(View.INVISIBLE);
        } else {
            if (adView.getAdvertiserView() instanceof TextView) {
                ((TextView) adView.getAdvertiserView()).setText(nativeAd.getAdvertiser());
                adView.getAdvertiserView().setVisibility(View.VISIBLE);
            }
        }

        adView.setNativeAd(nativeAd);
    }

    public boolean isAdsToggledVisible() {
        return isAdsToggledVisible;
    }

    public static final class MessageDialogFragment extends DialogFragment {
        private static final String ARG_TITLE_ID = "title_id";
        private static final String ARG_MESSAGE = "message";

        static DialogFragment newInstance(@StringRes int titleId, String message) {
            DialogFragment fragment = new MessageDialogFragment();
            Bundle arguments = new Bundle();
            arguments.putInt(ARG_TITLE_ID, titleId);
            arguments.putString(ARG_MESSAGE, message);
            fragment.setArguments(arguments);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            @StringRes final int title = getArguments().getInt(ARG_TITLE_ID);
            String message = getArguments().getString(ARG_MESSAGE);

            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity())
                    .setMessage(message)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> dismiss());

            if (title != 0) {
                dialogBuilder.setTitle(title);
            }
            return dialogBuilder.create();
        }
    }
}
