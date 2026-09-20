/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.skstudio.BabyWAstickersApp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

public class StickerPackDetailsActivity extends AddStickerPackActivity {

    /**
     * Do not change below values of below 3 lines as this is also used by WhatsApp
     */
    public static final String EXTRA_STICKER_PACK_ID = "sticker_pack_id";
    public static final String EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority";
    public static final String EXTRA_STICKER_PACK_NAME = "sticker_pack_name";

    public static final String EXTRA_STICKER_PACK_WEBSITE = "sticker_pack_website";
    public static final String EXTRA_STICKER_PACK_EMAIL = "sticker_pack_email";
    public static final String EXTRA_STICKER_PACK_PRIVACY_POLICY = "sticker_pack_privacy_policy";
    public static final String EXTRA_STICKER_PACK_LICENSE_AGREEMENT = "sticker_pack_license_agreement";
    public static final String EXTRA_STICKER_PACK_TRAY_ICON = "sticker_pack_tray_icon";
    public static final String EXTRA_SHOW_UP_BUTTON = "show_up_button";
    public static final String EXTRA_STICKER_PACK_DATA = "sticker_pack";


    private RecyclerView recyclerView;
    private GridLayoutManager layoutManager;
    private StickerPreviewAdapter stickerPreviewAdapter;
    private int numColumns;
    private View addButton;
    private View alreadyAddedText;
    private StickerPack stickerPack;
    private View divider;
    private WhiteListCheckAsyncTask whiteListCheckAsyncTask;
    private SimpleDraweeView packTrayIcon;
    private AdView mAdView1;
    private InterstitialAd interstitialAd;
    private RewardedAdManager rewardedAdManager;
    private boolean isAdShowing = false;
    private boolean isRewardEarned = false;
    private View loadingOverlay;
    private View downloadProgressContainer;
    private android.widget.ProgressBar downloadProgressBar;
    private TextView downloadProgressText;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerFrameLayout;
    private String pendingIdentifier;
    private String pendingName;
    private boolean isDownloading = false;

    private final Runnable scrollingFinishedRunnable = () -> {
        if (stickerPreviewAdapter != null) {
            stickerPreviewAdapter.setScrolling(false);
        }
    };

    private final Handler scrollHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_details);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        boolean showUpButton = getIntent().getBooleanExtra(EXTRA_SHOW_UP_BUTTON, false);
        stickerPack = getIntent().getParcelableExtra(EXTRA_STICKER_PACK_DATA);
        
        TextView packNameTextView = findViewById(R.id.pack_name);
        TextView packPublisherTextView = findViewById(R.id.author);
        packTrayIcon = findViewById(R.id.tray_image);
        TextView packSizeTextView = findViewById(R.id.pack_size);
        SimpleDraweeView expandedStickerView = findViewById(R.id.sticker_details_expanded_sticker);
        View expandedStickerOverlay = findViewById(R.id.expanded_sticker_overlay);

        addButton = findViewById(R.id.add_to_whatsapp_button);
        alreadyAddedText = findViewById(R.id.already_added_text);
        layoutManager = new GridLayoutManager(this, 1);
        recyclerView = findViewById(R.id.sticker_list);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(pageLayoutListener);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (stickerPreviewAdapter != null) {
                    boolean isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                    stickerPreviewAdapter.setScrolling(isScrolling);
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (divider != null) {
                    boolean showDivider = recyclerView.computeVerticalScrollOffset() > 0;
                    divider.setVisibility(showDivider ? View.VISIBLE : View.INVISIBLE);
                }
            }
        });

        divider = findViewById(R.id.divider);
        loadingOverlay = findViewById(R.id.loading_overlay);
        downloadProgressContainer = findViewById(R.id.download_progress_container);
        downloadProgressBar = findViewById(R.id.download_progress_bar);
        downloadProgressText = findViewById(R.id.download_progress_text);
        if (stickerPreviewAdapter == null) {
            stickerPreviewAdapter = new StickerPreviewAdapter(getLayoutInflater(), R.drawable.sticker_error, getResources().getDimensionPixelSize(R.dimen.sticker_pack_details_image_size), getResources().getDimensionPixelSize(R.dimen.sticker_pack_details_image_padding), stickerPack, expandedStickerOverlay);
            recyclerView.setAdapter(stickerPreviewAdapter);
        }
        packNameTextView.setText(stickerPack.name);
        packPublisherTextView.setText(stickerPack.publisher);
        packTrayIcon.setController(Fresco.newDraweeControllerBuilder()
                .setUri(StickerPackLoader.getTrayIconUri(this, stickerPack))
                .setAutoPlayAnimations(true)
                .build());
        packSizeTextView.setText(Formatter.formatShortFileSize(this, stickerPack.getTotalSize()));
        addButton.setOnClickListener(v -> {
            // ❌ No Internet → Stop everything
            if (!isInternetAvailable()) {
                Toast.makeText(this, R.string.internet_not_available, Toast.LENGTH_SHORT).show();
                return;
           }

           showRewardedAndAddSticker(stickerPack.identifier, stickerPack.name);
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(showUpButton);
            getSupportActionBar().setTitle(showUpButton ? getResources().getString(R.string.title_activity_sticker_pack_details_multiple_pack) : getResources().getQuantityString(R.plurals.title_activity_sticker_packs_list, 1));
        }
        findViewById(R.id.sticker_pack_animation_indicator).setVisibility(stickerPack.animatedStickerPack ? View.VISIBLE : View.GONE);

        rewardedAdManager = RewardedAdManager.getInstance();
        rewardedAdManager.loadAd(this);
        loadInterstitialAd();
        mAdView1 = findViewById(R.id.adView1);
        if (mAdView1 != null) {
            if (StickerApplication.isAdsInitialized()) {
                mAdView1.loadAd(new AdRequest.Builder().build());
            } else {
                // If ads not initialized, check periodically or use a listener
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing() && !isDestroyed()) {
                            if (StickerApplication.isAdsInitialized()) {
                                mAdView1.loadAd(new AdRequest.Builder().build());
                            } else {
                                new Handler().postDelayed(this, 1000);
                            }
                        }
                    }
                }, 1000);
            }
        }
        loadingOverlay = findViewById(R.id.loading_overlay);
        shimmerFrameLayout = findViewById(R.id.shimmer_view_container);
    }

    private void showLoader() {
        addButton.setEnabled(false);
        loadingOverlay.setVisibility(View.VISIBLE);
        toggleAdsVisibility(false);
        if (shimmerFrameLayout != null) {
            shimmerFrameLayout.startShimmer();
        }
    }

    private void hideLoader() {
        addButton.setEnabled(true);
        loadingOverlay.setVisibility(View.GONE);
        toggleAdsVisibility(true);
        if (shimmerFrameLayout != null) {
            shimmerFrameLayout.stopShimmer();
        }
    }

    private void showDownloadProgress() {
        hideLoader(); // Ensure shimmer is hidden
        // Only show if we are actually downloading (StickerPackLoader.isStickerPackDownloaded will be false)
        if (!StickerPackLoader.isStickerPackDownloaded(this, stickerPack)) {
            if (downloadProgressContainer != null) {
                downloadProgressContainer.setVisibility(View.VISIBLE);
                toggleAdsVisibility(false);
                if (downloadProgressBar != null) {
                    downloadProgressBar.setProgress(0);
                }
                if (downloadProgressText != null) {
                    downloadProgressText.setText("0%");
                }
            }
        }
    }

    private void hideDownloadProgress() {
        if (downloadProgressContainer != null) {
            downloadProgressContainer.setVisibility(View.GONE);
            toggleAdsVisibility(true);
        }
    }

    private void showRewardedAndAddSticker(String identifier, String stickerPackName) {
        if (isAdShowing) return;

        if (!StickerPackLoader.isStickerPackDownloaded(this, stickerPack)) {
            pendingIdentifier = identifier;
            pendingName = stickerPackName;
            showDownloadProgress();
            StickerPackLoader.downloadStickers(this, stickerPack, progress -> {
                if (isFinishing() || isDestroyed()) return;
                if (downloadProgressBar != null) {
                    // Check if shimmer is still somehow active and kill it
                    if (loadingOverlay != null && loadingOverlay.getVisibility() == View.VISIBLE) {
                        hideLoader();
                    }
                    
                    // Smoothly animate progress bar
                    android.animation.ObjectAnimator.ofInt(downloadProgressBar, "progress", downloadProgressBar.getProgress(), progress)
                        .setDuration(400)
                        .start();
                    
                    // Smoothly animate percentage text
                    try {
                        if (downloadProgressText != null) {
                            String currentText = downloadProgressText.getText().toString().replace("%", "");
                            int startValue = currentText.isEmpty() ? 0 : Integer.parseInt(currentText);
                            android.animation.ValueAnimator textAnimator = android.animation.ValueAnimator.ofInt(startValue, progress);
                            textAnimator.setDuration(400);
                            textAnimator.addUpdateListener(animation1 -> {
                                if (!isFinishing() && !isDestroyed() && downloadProgressText != null) {
                                    downloadProgressText.setText(getString(R.string.percent_format, (int) animation1.getAnimatedValue()));
                                }
                            });
                            textAnimator.start();
                        }
                    } catch (NumberFormatException e) {
                        if (downloadProgressText != null) {
                            downloadProgressText.setText(getString(R.string.percent_format, progress));
                        }
                    }
                }
            }, () -> {
                if (isFinishing() || isDestroyed()) return;
                hideDownloadProgress();
                if (pendingIdentifier != null && !isAdShowing) {
                    showRewardedAndAddSticker(pendingIdentifier, pendingName);
                }
            });
            return;
        }

        // Temporarily bypassing ad for testing
        // performValidationAndAddPack(identifier, stickerPackName);
        
        if (rewardedAdManager != null && rewardedAdManager.isAdLoaded()) {
            //✅ Ad already ready → show immediately
            showAd(identifier, stickerPackName);
        } else {
            // ❌ Ad not ready → store request & load
            pendingIdentifier = identifier;
            pendingName = stickerPackName;

            showLoader();
            if (rewardedAdManager != null) {
                rewardedAdManager.loadAd(this);
                // We'll need a way to know when it's loaded. 
                // For now, let's just retry after a short delay or rely on the user clicking again.
                // A better way is to have a callback in loadAd.
                new Handler().postDelayed(() -> {
                    if (rewardedAdManager.isAdLoaded() && pendingIdentifier != null) {
                        hideLoader();
                        showAd(pendingIdentifier, pendingName);
                        pendingIdentifier = null;
                        pendingName = null;
                    } else {
                        hideLoader();
                        // If still not loaded, just add to WA to avoid blocking user
                        performValidationAndAddPack(identifier, stickerPackName);
                    }
                }, 3000);
            }
        }
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) return false;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities == null) return false;

            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
    }

    private void loadInterstitialAd() {
        if (!StickerApplication.isAdsInitialized()) {
            new Handler().postDelayed(this::loadInterstitialAd, 1000);
            return;
        }

        AdRequest adRequest = new AdRequest.Builder().build();

        InterstitialAd.load(this,
                getString(R.string.admob_interstitial_ad_unit_id),
                adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                interstitialAd = null;
                                loadInterstitialAd();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                interstitialAd = null;
                                loadInterstitialAd();
                            }
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        interstitialAd = null;
                    }
                });
    }

    private void showInterstitial() {
        if (interstitialAd != null) {
            interstitialAd.show(this);
        }
    }


    private void showAd(String identifier, String stickerPackName) {
        if (rewardedAdManager == null || !rewardedAdManager.isAdLoaded() || isFinishing() || isDestroyed()) return;

        isAdShowing = true;
        isRewardEarned = false;
        toggleAdsVisibility(false);

        rewardedAdManager.showAd(this, new RewardedAdManager.OnRewardListener() {
            @Override
            public void onRewardEarned(com.google.android.gms.ads.rewarded.RewardItem rewardItem) {
                if (isFinishing() || isDestroyed()) return;
                isRewardEarned = true;
                performValidationAndAddPack(identifier, stickerPackName);
            }

            @Override
            public void onAdClosed() {
                if (isFinishing() || isDestroyed()) return;
                isAdShowing = false;
                toggleAdsVisibility(true);
                if (!isRewardEarned) {
                    Toast.makeText(StickerPackDetailsActivity.this,
                            R.string.watch_ad_to_unlock,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAdFailedToShow() {
                if (isFinishing() || isDestroyed()) return;
                isAdShowing = false;
                toggleAdsVisibility(true);
                // Fallback: if ad fails to show, let them add it anyway to avoid bad UX
                performValidationAndAddPack(identifier, stickerPackName);
            }
        });
    }

    private void performValidationAndAddPack(String identifier, String stickerPackName) {
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected void onPreExecute() {
                showLoader();
            }

            @Override
            protected Exception doInBackground(Void... voids) {
                try {
                    StickerPackValidator.verifyStickerPackValidity(StickerPackDetailsActivity.this, stickerPack);
                    return null;
                } catch (Exception e) {
                    return e;
                }
            }

            @Override
            protected void onPostExecute(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                hideLoader();
                if (e == null) {
                    addStickerPackToWhatsApp(identifier, stickerPackName);
                } else {
                    Log.e("StickerPackDetails", "Sticker pack validation failed", e);
                    Toast.makeText(StickerPackDetailsActivity.this, "Validation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }

    private void launchInfoActivity(String publisherWebsite, String publisherEmail, String privacyPolicyWebsite, String licenseAgreementWebsite, String trayIconUriString) {
        Intent intent = new Intent(StickerPackDetailsActivity.this, StickerPackInfoActivity.class);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_ID, stickerPack.identifier);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_WEBSITE, publisherWebsite);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_EMAIL, publisherEmail);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_PRIVACY_POLICY, privacyPolicyWebsite);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_LICENSE_AGREEMENT, licenseAgreementWebsite);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_TRAY_ICON, trayIconUriString);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_info && stickerPack != null) {
            showInterstitial();
            Uri trayIconUri = StickerPackLoader.getStickerAssetUri(stickerPack.identifier, stickerPack.trayImageFile);
            launchInfoActivity(stickerPack.publisherWebsite, stickerPack.publisherEmail, stickerPack.privacyPolicyWebsite, stickerPack.licenseAgreementWebsite, trayIconUri.toString());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private final ViewTreeObserver.OnGlobalLayoutListener pageLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            setNumColumns(3);
        }
    };

    private void setNumColumns(int numColumns) {
        if (this.numColumns != numColumns) {
            layoutManager.setSpanCount(numColumns);
            this.numColumns = numColumns;
            if (stickerPreviewAdapter != null) {
                stickerPreviewAdapter.notifyDataSetChanged();
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mAdView1 != null) {
            mAdView1.resume();
        }
        whiteListCheckAsyncTask = new WhiteListCheckAsyncTask(this);
        whiteListCheckAsyncTask.execute(stickerPack);
    }

    @Override
    protected void onPause() {
        if (mAdView1 != null) {
            mAdView1.pause();
        }
        super.onPause();
        if (whiteListCheckAsyncTask != null && !whiteListCheckAsyncTask.isCancelled()) {
            whiteListCheckAsyncTask.cancel(true);
        }
    }

    @Override
    protected void onDestroy() {
        if (mAdView1 != null) {
            mAdView1.destroy();
        }
        super.onDestroy();
    }

    private void updateAddUI(Boolean isWhitelisted) {
        addButton.setVisibility(View.VISIBLE);
        alreadyAddedText.setVisibility(View.GONE);
        findViewById(R.id.sticker_pack_details_tap_to_preview).setVisibility(View.VISIBLE);
    }

    static class WhiteListCheckAsyncTask extends AsyncTask<StickerPack, Void, Boolean> {
        private final WeakReference<StickerPackDetailsActivity> stickerPackDetailsActivityWeakReference;

        WhiteListCheckAsyncTask(StickerPackDetailsActivity stickerPackListActivity) {
            this.stickerPackDetailsActivityWeakReference = new WeakReference<>(stickerPackListActivity);
        }

        @Override
        protected final Boolean doInBackground(StickerPack... stickerPacks) {
            StickerPack stickerPack = stickerPacks[0];
            final StickerPackDetailsActivity stickerPackDetailsActivity = stickerPackDetailsActivityWeakReference.get();
            if (stickerPackDetailsActivity == null) {
                return false;
            }
            return WhitelistCheck.isWhitelisted(stickerPackDetailsActivity, stickerPack.identifier);
        }

        @Override
        protected void onPostExecute(Boolean isWhitelisted) {
            final StickerPackDetailsActivity stickerPackDetailsActivity = stickerPackDetailsActivityWeakReference.get();
            if (stickerPackDetailsActivity != null && !stickerPackDetailsActivity.isFinishing() && !stickerPackDetailsActivity.isDestroyed()) {
                stickerPackDetailsActivity.updateAddUI(isWhitelisted);
            }
        }
    }
}
