package com.skstudio.lovestickers;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class SplashActivity extends AppCompatActivity {

    private static final int CHECK_INTERVAL = 2000;

    private Handler handler = new Handler(Looper.getMainLooper());
    private TextView dotsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_splash);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        dotsText = findViewById(R.id.loading_dots);

        startDotAnimation();
        checkInternetAndProceed();
    }

    // ✅ Internet check loop
    private void checkInternetAndProceed() {
        if (isInternetAvailable()) {
            // ✅ Internet available → go to StickerPackListActivity
            startActivity(new Intent(SplashActivity.this, StickerPackListActivity.class));
            finish();
        } else {
           Toast.makeText(this, "Internet not available", Toast.LENGTH_SHORT).show();

            // Retry after 2 sec
            handler.postDelayed(this::checkInternetAndProceed, CHECK_INTERVAL);
        }
    }

    // ✅ Modern internet check (no deprecated API)
    private boolean isInternetAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

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
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
    }

    // ✅ Dots animation
    private void startDotAnimation() {
        handler.post(new Runnable() {
            int count = 0;

            @Override
            public void run() {
                count++;
                switch (count % 4) {
                    case 0:
                        dotsText.setText(".");
                        break;
                    case 1:
                        dotsText.setText("..");
                        break;
                    case 2:
                        dotsText.setText("...");
                        break;
                    case 3:
                        dotsText.setText("");
                        break;
                }
                handler.postDelayed(this, 500);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}