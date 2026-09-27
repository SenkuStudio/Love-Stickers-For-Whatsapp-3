package com.skstudio.lovestickers;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.ResponseInfo;
import com.google.android.gms.ads.AdapterResponseInfo;

/**
 * Production-ready Rewarded Ad Manager for AdMob with Mediation support.
 */
public class RewardedAdManager {
    private static final String TAG = "RewardedAdManager";

    private static RewardedAdManager instance;
    private RewardedAd rewardedAd;
    private boolean isLoading = false;

    public interface OnRewardListener {
        void onRewardEarned(RewardItem rewardItem);
        default void onAdClosed() {}
        default void onAdFailedToShow() {}
    }

    private RewardedAdManager() {}

    public static synchronized RewardedAdManager getInstance() {
        if (instance == null) {
            instance = new RewardedAdManager();
        }
        return instance;
    }

    /**
     * Loads a rewarded ad if one is not already loading or loaded.
     */
    public void loadAd(Context context) {
        if (isLoading || rewardedAd != null) {
            return;
        }

        isLoading = true;
        Log.d(TAG, "Ad loading started...");

        String adUnitId = context.getString(R.string.admob_rewarded_ad_unit_id);
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(context, adUnitId, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                isLoading = false;
                rewardedAd = null;
                Log.e(TAG, "Ad failed to load: " + loadAdError.getMessage());
                // Optional: Implementation of a retry policy with exponential backoff
            }

            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                isLoading = false;
                rewardedAd = ad;
                
                ResponseInfo responseInfo = ad.getResponseInfo();
                String adapterName = responseInfo != null ? getFriendlyAdapterName(responseInfo.getMediationAdapterClassName()) : "Unknown";
                
                Log.d(TAG, "✅ Ad loaded successfully!");
                Log.d(TAG, "📱 Current Ad Network: " + adapterName);
                
                if (responseInfo != null && responseInfo.getLoadedAdapterResponseInfo() != null) {
                    Log.d(TAG, "🔗 Full Adapter Class: " + responseInfo.getMediationAdapterClassName());
                }
            }
        });
    }

    private String getFriendlyAdapterName(String className) {
        if (className == null) return "Unknown";
        if (className.contains("AdMobAdapter")) return "Google AdMob";
        if (className.contains("UnityAdapter")) return "Unity Ads";
        if (className.contains("AppLovinAdapter")) return "AppLovin";
        if (className.contains("FacebookAdapter")) return "Meta Audience Network";
        return className; // Return raw name if it's something else
    }

    /**
     * Shows the rewarded ad if available.
     */
    public void showAd(Activity activity, OnRewardListener listener) {
        if (rewardedAd == null) {
            Log.w(TAG, "Ad shown failed: Ad not loaded yet.");
            loadAd(activity); // Try to load for next time
            return;
        }

        rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                String adapterName = "Unknown";
                if (rewardedAd != null && rewardedAd.getResponseInfo() != null) {
                    adapterName = getFriendlyAdapterName(rewardedAd.getResponseInfo().getMediationAdapterClassName());
                }
                Log.d(TAG, "📺 NOW SHOWING REWARDED AD: [" + adapterName + "]");
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                Log.e(TAG, "Ad failed to show: " + adError.getMessage());
                rewardedAd = null;
                if (listener != null) {
                    listener.onAdFailedToShow();
                }
                if (activity != null && !activity.isFinishing() && (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed())) {
                    loadAd(activity.getApplicationContext());
                }
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad dismissed.");
                rewardedAd = null;
                if (listener != null) {
                    listener.onAdClosed();
                }
                if (activity != null && !activity.isFinishing() && (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed())) {
                    loadAd(activity.getApplicationContext());
                }
            }
        });

        rewardedAd.show(activity, rewardItem -> {
            Log.d(TAG, "Reward earned: " + rewardItem.getAmount() + " " + rewardItem.getType());
            if (activity != null && !activity.isFinishing() && (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed())) {
                if (listener != null) {
                    listener.onRewardEarned(rewardItem);
                }
            }
        });
    }

    public boolean isAdLoaded() {
        return rewardedAd != null;
    }
}
