package com.skstudio.lovestickerss;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    public static final String PREF_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String PREF_NAME = "settings_prefs";

    private ViewPager2 viewPager;
    private MaterialButton btnNext;
    private MaterialButton btnSkip;
    private ImageView[] dots;
    private OnboardingAdapter adapter;
    private AdView mAdView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        viewPager = findViewById(R.id.view_pager);
        btnNext = findViewById(R.id.btn_next);
        btnSkip = findViewById(R.id.btn_skip);
        mAdView = findViewById(R.id.adView_onboarding);

        if (StickerApplication.isAdsInitialized()) {
            AdRequest adRequest = new AdRequest.Builder().build();
            mAdView.loadAd(adRequest);
        }
        dots = new ImageView[3];
        dots[0] = findViewById(R.id.dot1);
        dots[1] = findViewById(R.id.dot2);
        dots[2] = findViewById(R.id.dot3);

        List<OnboardingPage> pages = new ArrayList<>();
        pages.add(new OnboardingPage(R.drawable.appicon, getString(R.string.onboarding_title_1), getString(R.string.onboarding_desc_1)));
        pages.add(new OnboardingPage(R.drawable.babycouple, getString(R.string.onboarding_title_2), getString(R.string.onboarding_desc_2)));
        pages.add(new OnboardingPage(R.drawable.appicon, getString(R.string.onboarding_title_3), getString(R.string.onboarding_desc_3)));

        adapter = new OnboardingAdapter(pages);
        viewPager.setAdapter(adapter);

        updateDots(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                if (position == adapter.getItemCount() - 1) {
                    btnNext.setText(R.string.btn_get_started);
                } else {
                    btnNext.setText(R.string.btn_next);
                }
            }
        });

        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() < adapter.getItemCount() - 1) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
                completeOnboarding();
            }
        });

        btnSkip.setOnClickListener(v -> completeOnboarding());
    }

    private void updateDots(int position) {
        for (int i = 0; i < dots.length; i++) {
            if (dots[i] != null) {
                dots[i].setSelected(i == position);
            }
        }
    }

    private void completeOnboarding() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_ONBOARDING_COMPLETED, true).apply();
        startActivity(new Intent(OnboardingActivity.this, StickerPackListActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentItem() > 0) {
            viewPager.setCurrentItem(viewPager.getCurrentItem() - 1);
        } else {
            super.onBackPressed();
        }
    }

    private static class OnboardingPage {
        int imageResId;
        String title;
        String description;

        OnboardingPage(int imageResId, String title, String description) {
            this.imageResId = imageResId;
            this.title = title;
            this.description = description;
        }
    }

    private static class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.ViewHolder> {
        private final List<OnboardingPage> pages;

        OnboardingAdapter(List<OnboardingPage> pages) {
            this.pages = pages;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding_page, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OnboardingPage page = pages.get(position);
            holder.ivIllustration.setImageResource(page.imageResId);
            holder.tvTitle.setText(page.title);
            holder.tvDescription.setText(page.description);
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIllustration;
            TextView tvTitle;
            TextView tvDescription;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIllustration = itemView.findViewById(R.id.iv_illustration);
                tvTitle = itemView.findViewById(R.id.tv_title);
                tvDescription = itemView.findViewById(R.id.tv_description);
            }
        }
    }
}