/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.skstudio.BabyWAstickersApp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import android.widget.FrameLayout;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.LoadAdError;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;


public class StickerPackListActivity extends AddStickerPackActivity {
    public static final String EXTRA_STICKER_PACK_LIST_DATA = "sticker_pack_list";
    private static final int STICKER_PREVIEW_DISPLAY_LIMIT = 4;
    private LinearLayoutManager packLayoutManager;
    private RecyclerView packRecyclerView;
    private StickerPackListAdapter allStickerPacksListAdapter;
    private WhiteListCheckAsyncTask whiteListCheckAsyncTask;
    private ArrayList<StickerPack> stickerPackList;
    private RecyclerView categoryRecyclerView;
    private CategoryAdapter categoryAdapter;
    private List<StickerPack> horizontalStickerPacks = new ArrayList<>();
    private AdView mAdView;
    private AdView mAdView1;
    private RewardedAdManager rewardedAdManager;
    private View loadingOverlay;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerFrameLayout;
    private View downloadProgressContainer;
    private android.widget.ProgressBar downloadProgressBar;
    private android.widget.TextView downloadProgressText;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View errorStateView;
    private int currentPage = 1;
    private boolean isLoading = false;
    private int loadAttemptCount = 0;
    private boolean hasNextPage = true;
    private java.util.Map<String, StickerPack> packMap = new java.util.LinkedHashMap<>();
    private LoadMoreAsyncTask loadMoreAsyncTask;
    private RefreshAsyncTask refreshAsyncTask;
    private final android.os.Handler slowLoadingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable slowLoadingRunnable;

    private List<StickerPack> originalStickerPackList;
    private View emptyStateView;
    private String currentCategory = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_list);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        rewardedAdManager = RewardedAdManager.getInstance();
        rewardedAdManager.loadAd(this);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setOnRefreshListener(this::refreshStickerPacks);
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            updateAdVisibility(id);
            if (id == R.id.nav_home) {
                showRecyclerView();
                filterByCategory("ALL");
                return true;
            } else if (id == R.id.nav_favorites) {
                showRecyclerView();
                filterByCategory("FAVORITES");
                return true;
            } else if (id == R.id.nav_settings) {
                showSettingsFragment();
                return true;
            }
            return false;
        });

        mAdView = findViewById(R.id.adView);
        mAdView1 = findViewById(R.id.adView7);
        loadingOverlay = findViewById(R.id.loading_overlay);
        shimmerFrameLayout = findViewById(R.id.shimmer_view_container);
        downloadProgressContainer = findViewById(R.id.download_progress_container);
        downloadProgressBar = findViewById(R.id.download_progress_bar);
        downloadProgressText = findViewById(R.id.download_progress_text);
        emptyStateView = findViewById(R.id.empty_state_view);
        errorStateView = findViewById(R.id.error_state_view);
        findViewById(R.id.retry_button).setOnClickListener(v -> refreshStickerPacks());

        categoryRecyclerView = findViewById(R.id.category_list);
        categoryAdapter = new CategoryAdapter(horizontalStickerPacks, new CategoryAdapter.OnStickerPackClickListener() {
            @Override
            public void onStickerPackClick(StickerPack pack) {
                Intent intent = new Intent(StickerPackListActivity.this, StickerPackDetailsActivity.class);
                intent.putExtra(StickerPackDetailsActivity.EXTRA_SHOW_UP_BUTTON, true);
                intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_DATA, pack);
                startActivity(intent);
            }

            @Override
            public void onAllClick() {
                filterByCategory("ALL");
            }
        });
        categoryRecyclerView.setAdapter(categoryAdapter);

        packRecyclerView = findViewById(R.id.sticker_pack_list);
        packRecyclerView.setHasFixedSize(true);
        packRecyclerView.setItemViewCacheSize(20);
        packRecyclerView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING);

        stickerPackList = getIntent().getParcelableArrayListExtra(EXTRA_STICKER_PACK_LIST_DATA);
        if (stickerPackList == null || stickerPackList.isEmpty()) {
            // Try to load from cache
            List<StickerPack> cachedPacks = StickerPackLoader.loadRemotePacks(this);
            if (!cachedPacks.isEmpty()) {
                stickerPackList = new ArrayList<>(cachedPacks);
            }
        }

        if (stickerPackList != null) {
            originalStickerPackList = new ArrayList<>(stickerPackList);
            packMap = new LinkedHashMap<>();
            for (StickerPack pack : originalStickerPackList) {
                if (pack != null && pack.identifier != null) {
                    packMap.put(pack.identifier, pack);
                }
            }
        } else {
            originalStickerPackList = new ArrayList<>();
            packMap = new LinkedHashMap<>();
        }
        showStickerPackList(stickerPackList != null ? stickerPackList : new ArrayList<>());

        if (originalStickerPackList.isEmpty()) {
            refreshStickerPacks();
        } else if (isInternetAvailable()) {
            // Even if we have cache, refresh in background if internet is available
            new RefreshAsyncTask(this).execute();
        }
        AdRequest adRequest = new AdRequest.Builder().build();
        if (mAdView != null) {
            mAdView.loadAd(adRequest);
        }
        if (mAdView1 != null) {
            mAdView1.loadAd(adRequest);
        }
        loadNativeAd("ca-app-pub-6979979912689100/4535593027", findViewById(R.id.native_ad_container));
        updateAdVisibility(R.id.nav_home);
        MobileAds.initialize(this, initializationStatus -> {});
        emptyStateView = findViewById(R.id.empty_state_view);
        findViewById(R.id.empty_state_button).setOnClickListener(v -> {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        });
    }

    private void updateAdVisibility(int navId) {
        if (mAdView == null || mAdView1 == null) return;
        if (navId == R.id.nav_home) {
            mAdView.setVisibility(View.GONE);
            mAdView1.setVisibility(View.GONE);
            if (findViewById(R.id.native_ad_container) != null) {
                findViewById(R.id.native_ad_container).setVisibility(View.VISIBLE);
            }
        } else {
            mAdView.setVisibility(View.VISIBLE);
            mAdView1.setVisibility(View.VISIBLE);
            if (findViewById(R.id.native_ad_container) != null) {
                findViewById(R.id.native_ad_container).setVisibility(View.GONE);
            }
        }
    }

    private void showSettingsFragment() {
        findViewById(R.id.swipe_refresh).setVisibility(View.GONE);
        if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
        findViewById(R.id.fragment_container).setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new SettingsFragment())
                .commit();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Settings");
        }
    }

    private void showRecyclerView() {
        findViewById(R.id.swipe_refresh).setVisibility(View.VISIBLE);
        if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
        if (errorStateView != null) errorStateView.setVisibility(View.GONE);
        findViewById(R.id.fragment_container).setVisibility(View.GONE);
        if (getSupportActionBar() != null) {
            if (stickerPackList != null) {
                getSupportActionBar().setTitle(getResources().getQuantityString(R.plurals.title_activity_sticker_packs_list, stickerPackList.size()));
            } else {
                getSupportActionBar().setTitle(R.string.app_name);
            }
        }
    }

    private void refreshStickerPacks() {
        if (isLoading) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        isLoading = true;
        loadAttemptCount++;
        currentPage = 1;
        hasNextPage = true;
        if (errorStateView != null) errorStateView.setVisibility(View.GONE);
        if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
        showLoader(); // Show Shimmer
        
        // Show the message immediately whenever shimmer is shown for loading packs
        Toast.makeText(this, R.string.loading_footer_message, Toast.LENGTH_LONG).show();

        // Start a 5-second timer to handle slow loading
        if (slowLoadingRunnable != null) {
            slowLoadingHandler.removeCallbacks(slowLoadingRunnable);
        }
        slowLoadingRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (isLoading && (originalStickerPackList == null || originalStickerPackList.isEmpty())) {
                    if (loadAttemptCount <= 1) {
                        // First time, show retry button
                        isLoading = false; // Allow retry
                        hideLoader();
                        if (errorStateView != null) {
                            errorStateView.setVisibility(View.VISIBLE);
                            TextView errorText = errorStateView.findViewById(R.id.error_state_text);
                            TextView errorHint = errorStateView.findViewById(R.id.error_state_hint);
                            if (errorText != null) errorText.setText(R.string.failed_to_load_stickers);
                            if (errorHint != null) errorHint.setText(R.string.error_hint);
                        }
                        swipeRefreshLayout.setVisibility(View.GONE);
                    } else {
                        // Subsequent attempts: keep shimmer and show toast again
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(StickerPackListActivity.this, R.string.loading_footer_message, Toast.LENGTH_LONG).show();
                            slowLoadingHandler.postDelayed(this, 5000);
                        }
                    }
                }
            }
        };
        slowLoadingHandler.postDelayed(slowLoadingRunnable, 5000);

        if (refreshAsyncTask != null && !refreshAsyncTask.isCancelled()) {
            refreshAsyncTask.cancel(true);
        }
        refreshAsyncTask = new RefreshAsyncTask(this);
        refreshAsyncTask.execute();
    }

    static class RefreshAsyncTask extends AsyncTask<Void, Void, StickerPackLoader.FetchResult> {
        private final WeakReference<StickerPackListActivity> activityRef;

        RefreshAsyncTask(StickerPackListActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        protected StickerPackLoader.FetchResult doInBackground(Void... voids) {
            try {
                StickerPackListActivity activity = activityRef.get();
                if (activity != null) {
                    // Start fresh for refresh
                    return StickerPackLoader.fetchFromRemoteApi(1, 200, new java.util.LinkedHashMap<>());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(StickerPackLoader.FetchResult result) {
            StickerPackListActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                activity.isLoading = false;
                activity.swipeRefreshLayout.setRefreshing(false);
                if (activity.slowLoadingRunnable != null) {
                    activity.slowLoadingHandler.removeCallbacks(activity.slowLoadingRunnable);
                }
                
                if (result != null && result.stickerPacks != null && !result.stickerPacks.isEmpty()) {
                    activity.loadAttemptCount = 0; // Reset on success
                    activity.hideLoader(); // Hide Shimmer immediately on success
                    if (activity.errorStateView != null) activity.errorStateView.setVisibility(View.GONE);
                    activity.swipeRefreshLayout.setVisibility(View.VISIBLE);
                    activity.packMap.clear();
                    for (StickerPack pack : result.stickerPacks) {
                        if (pack != null && pack.identifier != null) {
                            activity.packMap.put(pack.identifier, pack);
                        }
                    }
                    activity.originalStickerPackList = new ArrayList<>(activity.packMap.values());
                    
                    // Update horizontal list
                    activity.horizontalStickerPacks.clear();
                    activity.horizontalStickerPacks.addAll(activity.originalStickerPackList);
                    if (activity.categoryAdapter != null) {
                        activity.categoryAdapter.notifyDataSetChanged();
                    }

                    activity.filterByCategory(activity.currentCategory);
                    // Save to cache
                    if (activity.originalStickerPackList != null && !activity.originalStickerPackList.isEmpty()) {
                        StickerPackLoader.saveRemotePacks(activity, activity.originalStickerPackList);
                    }
                } else if (activity.originalStickerPackList == null || activity.originalStickerPackList.isEmpty()) {
                    // Show error state only if it's the first attempt or if not loading slowly
                    if (activity.loadAttemptCount <= 1) {
                        if (activity.errorStateView != null) {
                            activity.errorStateView.setVisibility(View.VISIBLE);
                            TextView errorText = activity.errorStateView.findViewById(R.id.error_state_text);
                            TextView errorHint = activity.errorStateView.findViewById(R.id.error_state_hint);
                            if (errorText != null) errorText.setText(R.string.failed_to_load_stickers);
                            if (errorHint != null) errorHint.setText(R.string.error_hint);
                        }
                        activity.swipeRefreshLayout.setVisibility(View.GONE);
                    } else {
                        // If it failed after multiple retries, we might still want to show error,
                        // but the user's requirement says "just show Stickers are loading slowly"
                        // which is handled by the timer. If it actually fails, we'll show a toast and retry
                        Toast.makeText(activity, R.string.failed_to_load_stickers, Toast.LENGTH_SHORT).show();
                        activity.refreshStickerPacks();
                    }
                } else {
                    Toast.makeText(activity, "Failed to refresh stickers", Toast.LENGTH_SHORT).show();
                    if (activity.loadAttemptCount > 1) {
                        activity.refreshStickerPacks();
                    }
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdView != null) {
            mAdView.resume();
        }
        if (mAdView1 != null) {
            mAdView1.resume();
        }
        if (stickerPackList != null && !stickerPackList.isEmpty()) {
            whiteListCheckAsyncTask = new WhiteListCheckAsyncTask(this);
            whiteListCheckAsyncTask.execute(stickerPackList.toArray(new StickerPack[0]));
        }
        if (rewardedAdManager != null) {
            rewardedAdManager.loadAd(this);
        }
    }

    @Override
    protected void onPause() {
        if (mAdView != null) {
            mAdView.pause();
        }
        if (mAdView1 != null) {
            mAdView1.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mAdView != null) {
            mAdView.destroy();
        }
        if (mAdView1 != null) {
            mAdView1.destroy();
        }
        if (allStickerPacksListAdapter != null) {
            allStickerPacksListAdapter.releaseAds();
        }
        super.onDestroy();
        if (whiteListCheckAsyncTask != null && !whiteListCheckAsyncTask.isCancelled()) {
            whiteListCheckAsyncTask.cancel(true);
        }
        if (loadMoreAsyncTask != null && !loadMoreAsyncTask.isCancelled()) {
            loadMoreAsyncTask.cancel(true);
        }
        if (refreshAsyncTask != null && !refreshAsyncTask.isCancelled()) {
            refreshAsyncTask.cancel(true);
        }
        if (slowLoadingRunnable != null) {
            slowLoadingHandler.removeCallbacks(slowLoadingRunnable);
        }
    }

    private void filterByCategory(String category) {
        this.currentCategory = category;
        if (originalStickerPackList == null) {
            originalStickerPackList = new ArrayList<>();
        }
        if (category.equals("ALL")) {
            stickerPackList = new ArrayList<>(originalStickerPackList);
            if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
            if (stickerPackList.isEmpty()) {
                swipeRefreshLayout.setVisibility(View.GONE);
                if (errorStateView != null) {
                    errorStateView.setVisibility(View.VISIBLE);
                    TextView errorText = errorStateView.findViewById(R.id.error_state_text);
                    TextView errorHint = errorStateView.findViewById(R.id.error_state_hint);
                    if (errorText != null) errorText.setText(R.string.failed_to_load_stickers);
                    if (errorHint != null) errorHint.setText(R.string.error_hint);
                }
            } else {
                swipeRefreshLayout.setVisibility(View.VISIBLE);
                if (errorStateView != null) errorStateView.setVisibility(View.GONE);
            }
        } else if (category.equals("FAVORITES")) {
            stickerPackList = new ArrayList<>();
            FavoriteManager fm = new FavoriteManager(this);
            for (StickerPack pack : originalStickerPackList) {
                if (pack != null && pack.identifier != null && fm.isFavorite(pack.identifier)) {
                    stickerPackList.add(pack);
                }
            }
            if (stickerPackList.isEmpty()) {
                if (emptyStateView != null) {
                    emptyStateView.setVisibility(View.VISIBLE);
                    TextView tv = emptyStateView.findViewById(R.id.empty_state_text);
                    if (tv != null) tv.setText("No favorites yet");
                }
                swipeRefreshLayout.setVisibility(View.GONE);
            } else {
                if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
                swipeRefreshLayout.setVisibility(View.VISIBLE);
            }
        } else {
            stickerPackList = new ArrayList<>();
            for (StickerPack pack : originalStickerPackList) {
                if (pack != null && category.equals(pack.category)) {
                    stickerPackList.add(pack);
                }
            }
            if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
            swipeRefreshLayout.setVisibility(View.VISIBLE);
            if (errorStateView != null) errorStateView.setVisibility(View.GONE);
        }
        if (allStickerPacksListAdapter != null) {
            allStickerPacksListAdapter.setData(stickerPackList);
        }
        updateActionBarTitle();
    }

    private void updateActionBarTitle() {
        if (getSupportActionBar() != null) {
            if (stickerPackList != null && !stickerPackList.isEmpty()) {
                getSupportActionBar().setTitle(getResources().getQuantityString(R.plurals.title_activity_sticker_packs_list, stickerPackList.size()));
            } else {
                getSupportActionBar().setTitle(R.string.app_name);
            }
        }
    }

    private void showStickerPackList(List<StickerPack> stickerPackList) {
        allStickerPacksListAdapter = new StickerPackListAdapter(stickerPackList, onAddButtonClickedListener);
        packRecyclerView.setAdapter(allStickerPacksListAdapter);
        packRecyclerView.setHasFixedSize(true);
        packRecyclerView.setItemViewCacheSize(20);
        packRecyclerView.setDrawingCacheEnabled(true);
        packRecyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        
        packLayoutManager = new LinearLayoutManager(this);
        packLayoutManager.setOrientation(RecyclerView.VERTICAL);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(
                packRecyclerView.getContext(),
                packLayoutManager.getOrientation()
        );
        packRecyclerView.addItemDecoration(dividerItemDecoration);
        packRecyclerView.setLayoutManager(packLayoutManager);
        packRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                recalculateColumnCount();
                if (packRecyclerView.getWidth() > 0 && packRecyclerView.getHeight() > 0) {
                    packRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });
        setupPagination();
    }

    private void setupPagination() {
        packRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (allStickerPacksListAdapter != null) {
                    boolean isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                    allStickerPacksListAdapter.setScrolling(isScrolling);
                    allStickerPacksListAdapter.updateAnimationState(recyclerView);
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) { // check for scroll down
                    int visibleItemCount = packLayoutManager.getChildCount();
                    int totalItemCount = packLayoutManager.getItemCount();
                    int pastVisibleItems = packLayoutManager.findFirstVisibleItemPosition();

                    if (!isLoading && hasNextPage && "ALL".equals(currentCategory)) {
                        if ((visibleItemCount + pastVisibleItems) >= totalItemCount - 10) {
                            loadNextPage();
                        }
                    }
                }
            }
        });
        
        // Initialize packMap with initial data
        if (stickerPackList != null) {
            for (StickerPack pack : stickerPackList) {
                packMap.put(pack.identifier, pack);
            }
        }
    }

    private void loadNextPage() {
        isLoading = true;
        allStickerPacksListAdapter.showLoadingFooter(true);
        loadMoreAsyncTask = new LoadMoreAsyncTask(this);
        loadMoreAsyncTask.execute(currentPage + 1);
    }

    static class LoadMoreAsyncTask extends AsyncTask<Integer, Void, StickerPackLoader.FetchResult> {
        private final WeakReference<StickerPackListActivity> activityRef;

        LoadMoreAsyncTask(StickerPackListActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        protected StickerPackLoader.FetchResult doInBackground(Integer... params) {
            int page = params[0];
            try {
                StickerPackListActivity activity = activityRef.get();
                if (activity != null) {
                    return StickerPackLoader.fetchFromRemoteApi(page, 200, activity.packMap);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(StickerPackLoader.FetchResult result) {
            StickerPackListActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                activity.isLoading = false;
                if (activity.allStickerPacksListAdapter != null) {
                    activity.allStickerPacksListAdapter.showLoadingFooter(false);
                }
                if (result != null) {
                    activity.currentPage++;
                    activity.hasNextPage = result.hasNextPage;
                    activity.originalStickerPackList = new ArrayList<>(activity.packMap.values());
                    
                    // Update horizontal list
                    activity.horizontalStickerPacks.clear();
                    activity.horizontalStickerPacks.addAll(activity.originalStickerPackList);
                    if (activity.categoryAdapter != null) {
                        activity.categoryAdapter.notifyDataSetChanged();
                    }

                    activity.filterByCategory(activity.currentCategory);
                } else {
                    Toast.makeText(activity, "Failed to load more stickers", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


    private final StickerPackListAdapter.OnAddButtonClickedListener onAddButtonClickedListener =
            pack -> {
                if (!isInternetAvailable()) {
                    Toast.makeText(this, R.string.internet_not_available, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!StickerPackLoader.isStickerPackDownloaded(this, pack)) {
                    showDownloadProgress();
                    StickerPackLoader.downloadStickers(this, pack, progress -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (downloadProgressBar != null) {
                            if (loadingOverlay != null && loadingOverlay.getVisibility() == View.VISIBLE) {
                                hideLoader();
                            }
                            android.animation.ObjectAnimator.ofInt(downloadProgressBar, "progress", downloadProgressBar.getProgress(), progress)
                                    .setDuration(400)
                                    .start();
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
                        showAdAndAdd(pack);
                    });
                } else {
                    showAdAndAdd(pack);
                }
            };

    private void showAdAndAdd(StickerPack pack) {
        if (isFinishing() || isDestroyed()) return;
        if (rewardedAdManager != null && rewardedAdManager.isAdLoaded()) {
            rewardedAdManager.showAd(this, rewardItem -> {
                if (!isFinishing() && !isDestroyed()) {
                    addStickerPackToWhatsApp(pack.identifier, pack.name);
                }
            });
        } else {
            // If ad is not ready, add the pack directly to ensure good UX, but try loading for next time
            addStickerPackToWhatsApp(pack.identifier, pack.name);
            if (rewardedAdManager != null) {
                rewardedAdManager.loadAd(this);
            }
        }
    }

    private void showLoader() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        if (shimmerFrameLayout != null) {
            shimmerFrameLayout.startShimmer();
        }
    }

    private void hideLoader() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
        if (shimmerFrameLayout != null) {
            shimmerFrameLayout.stopShimmer();
        }
    }

    private void showDownloadProgress() {
        hideLoader();
        if (downloadProgressContainer != null) {
            downloadProgressContainer.setVisibility(View.VISIBLE);
            downloadProgressBar.setProgress(0);
            downloadProgressText.setText("0%");
        }
    }

    private void hideDownloadProgress() {
        if (downloadProgressContainer != null) {
            downloadProgressContainer.setVisibility(View.GONE);
        }
    }

    private void loadNativeAd(String adUnitId, FrameLayout container) {
        if (container == null || isFinishing() || isDestroyed()) return;
        AdLoader adLoader = new AdLoader.Builder(this, adUnitId)
                .forNativeAd(nativeAd -> {
                    if (container == null || isFinishing() || isDestroyed()) return;
                    NativeAdView adView = (NativeAdView) LayoutInflater.from(this)
                            .inflate(R.layout.layout_native_ad, container, false);
                    populateNativeAdView(nativeAd, adView);
                    container.removeAllViews();
                    container.addView(adView);
                    container.setVisibility(View.VISIBLE);
                })
                .withAdListener(new com.google.android.gms.ads.AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                        if (container != null) {
                            container.setVisibility(View.GONE);
                        }
                    }
                })
                .build();
        adLoader.loadAd(new AdRequest.Builder().build());
    }

    private void populateNativeAdView(NativeAd nativeAd, NativeAdView adView) {
        adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
        adView.setBodyView(adView.findViewById(R.id.ad_body));
        adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
        adView.setIconView(adView.findViewById(R.id.ad_app_icon));
        adView.setAdvertiserView(adView.findViewById(R.id.ad_advertiser));
        adView.setMediaView(adView.findViewById(R.id.ad_media));

        ((TextView) adView.getHeadlineView()).setText(nativeAd.getHeadline());

        if (nativeAd.getBody() == null) {
            adView.getBodyView().setVisibility(View.INVISIBLE);
        } else {
            adView.getBodyView().setVisibility(View.VISIBLE);
            ((TextView) adView.getBodyView()).setText(nativeAd.getBody());
        }

        if (nativeAd.getCallToAction() == null) {
            adView.getCallToActionView().setVisibility(View.INVISIBLE);
        } else {
            adView.getCallToActionView().setVisibility(View.VISIBLE);
            ((Button) adView.getCallToActionView()).setText(nativeAd.getCallToAction());
        }

        if (nativeAd.getIcon() == null) {
            View icon = adView.getIconView();
            if (icon != null) icon.setVisibility(View.GONE);
        } else {
            View icon = adView.getIconView();
            if (icon != null) {
                ((ImageView) icon).setImageDrawable(nativeAd.getIcon().getDrawable());
                icon.setVisibility(View.VISIBLE);
            }
        }

        if (nativeAd.getStarRating() == null) {
            View stars = adView.getStarRatingView();
            if (stars != null) stars.setVisibility(View.INVISIBLE);
        } else {
            View stars = adView.getStarRatingView();
            if (stars != null) {
                ((RatingBar) stars).setRating(nativeAd.getStarRating().floatValue());
                stars.setVisibility(View.VISIBLE);
            }
        }

        if (nativeAd.getAdvertiser() == null) {
            View advertiser = adView.getAdvertiserView();
            if (advertiser != null) advertiser.setVisibility(View.INVISIBLE);
        } else {
            View advertiser = adView.getAdvertiserView();
            if (advertiser != null) {
                ((TextView) advertiser).setText(nativeAd.getAdvertiser());
                advertiser.setVisibility(View.VISIBLE);
            }
        }

        if (nativeAd.getPrice() == null) {
            View price = adView.getPriceView();
            if (price != null) price.setVisibility(View.INVISIBLE);
        } else {
            View price = adView.getPriceView();
            if (price != null) {
                ((TextView) price).setText(nativeAd.getPrice());
                price.setVisibility(View.VISIBLE);
            }
        }

        if (nativeAd.getStore() == null) {
            View store = adView.getStoreView();
            if (store != null) store.setVisibility(View.INVISIBLE);
        } else {
            View store = adView.getStoreView();
            if (store != null) {
                ((TextView) store).setText(nativeAd.getStore());
                store.setVisibility(View.VISIBLE);
            }
        }

        adView.setNativeAd(nativeAd);
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities == null) return false;

            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                   (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
        } else {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
    }

    private void recalculateColumnCount() {
        if (packLayoutManager == null || packRecyclerView == null) {
            return;
        }
        final int previewSize = getResources().getDimensionPixelSize(R.dimen.sticker_pack_list_item_preview_image_size);
        int firstVisibleItemPosition = packLayoutManager.findFirstVisibleItemPosition();
        if (firstVisibleItemPosition == RecyclerView.NO_POSITION) {
            // If no items are visible yet, try to get a measurement from the recycler view width itself
            // to provide a sensible default.
            int recyclerViewWidth = packRecyclerView.getWidth();
            if (recyclerViewWidth > 0) {
                // Approximate width for the image row (subtracting margins/padding)
                int estimatedImageRowWidth = recyclerViewWidth - (int)(64 * getResources().getDisplayMetrics().density);
                updateImageRowSpec(estimatedImageRowWidth, previewSize);
            }
            return;
        }
        RecyclerView.ViewHolder holder = packRecyclerView.findViewHolderForAdapterPosition(firstVisibleItemPosition);
        if (holder instanceof StickerPackListItemViewHolder) {
            StickerPackListItemViewHolder viewHolder = (StickerPackListItemViewHolder) holder;
            final int widthOfImageRow = viewHolder.imageRowView.getMeasuredWidth();
            updateImageRowSpec(widthOfImageRow, previewSize);
        }
    }

    private void updateImageRowSpec(int widthOfImageRow, int previewSize) {
        if (widthOfImageRow > 0) {
            final int max = Math.max(widthOfImageRow / previewSize, 1);
            int maxNumberOfImagesInARow = Math.min(STICKER_PREVIEW_DISPLAY_LIMIT, max);
            int minMarginBetweenImages = maxNumberOfImagesInARow > 1 ? (widthOfImageRow - maxNumberOfImagesInARow * previewSize) / (maxNumberOfImagesInARow - 1) : 0;
            if (allStickerPacksListAdapter != null) {
                allStickerPacksListAdapter.setImageRowSpec(maxNumberOfImagesInARow, minMarginBetweenImages, previewSize);
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_sticker_pack_list, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint("Search stickers, emojis...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (allStickerPacksListAdapter != null) {
                    allStickerPacksListAdapter.filter(newText);
                }
                return true;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            refreshStickerPacks();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (findViewById(R.id.fragment_container).getVisibility() == View.VISIBLE) {
            BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }

    private void shareApp() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, "https://play.google.com/store/apps/details?id=" + getPackageName()));
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share via"));
    }

    private void showHowToUse() {
        new AlertDialog.Builder(this)
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
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
        }
    }

    private void moreApps() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://dev?id=8066128835537801410")));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/dev?id=8066128835537801410")));
        }
    }

    private void openPrivacyPolicy() {
        String url = getString(R.string.privacy_policy);
        if (!url.startsWith("http")) {
            url = "https://skstudio.com/privacy-policy"; // Fallback or use real URL from strings
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    static class WhiteListCheckAsyncTask extends AsyncTask<StickerPack, Void, List<StickerPack>> {
        private final WeakReference<StickerPackListActivity> stickerPackListActivityWeakReference;

        WhiteListCheckAsyncTask(StickerPackListActivity stickerPackListActivity) {
            this.stickerPackListActivityWeakReference = new WeakReference<>(stickerPackListActivity);
        }

        @Override
        protected final List<StickerPack> doInBackground(StickerPack... stickerPackArray) {
            final StickerPackListActivity stickerPackListActivity = stickerPackListActivityWeakReference.get();
            if (stickerPackListActivity == null) {
                return Arrays.asList(stickerPackArray);
            }
            for (StickerPack stickerPack : stickerPackArray) {
                stickerPack.setIsWhitelisted(WhitelistCheck.isWhitelisted(stickerPackListActivity, stickerPack.identifier));
                stickerPack.setIsDownloaded(StickerPackLoader.isStickerPackDownloaded(stickerPackListActivity, stickerPack));
            }
            return Arrays.asList(stickerPackArray);
        }

        @Override
        protected void onPostExecute(List<StickerPack> stickerPackList) {
            final StickerPackListActivity stickerPackListActivity = stickerPackListActivityWeakReference.get();
            if (stickerPackListActivity != null && !stickerPackListActivity.isFinishing() && !stickerPackListActivity.isDestroyed()) {
                stickerPackListActivity.allStickerPacksListAdapter.updateData(stickerPackList);
            }
        }
    }
}
