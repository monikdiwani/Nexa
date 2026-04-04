package com.example.frienddebt.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.frienddebt.R;
import com.example.frienddebt.ui.LandingPage;
import com.example.frienddebt.ui.DashboardActivity;
import com.google.firebase.auth.FirebaseAuth;

public class Splash extends AppCompatActivity {

    private static final int SPLASH_DELAY = 2000;
    private static final int REQ_POST_NOTIFICATIONS = 1001;
    private static final String PREFS_NAME = "NexaPrefs";
    private static final String KEY_NOTIF_PERMISSION_ASKED = "notif_permission_asked";

    private boolean hasNavigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply dark mode immediately before super.onCreate to prevent flashes
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isDarkMode = sp.getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(isDarkMode ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        // Handle system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // DELAY AND CHECK AUTO-LOGIN
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (shouldRequestNotificationsPermission()) {
                requestNotificationsPermission();
            } else {
                checkAutoLoginAndNavigate();
            }
        }, SPLASH_DELAY);
    }

    private boolean shouldRequestNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return !sp.getBoolean(KEY_NOTIF_PERMISSION_ASKED, false);
    }

    private void requestNotificationsPermission() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_NOTIF_PERMISSION_ASKED, true).apply();

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQ_POST_NOTIFICATIONS
        );
    }

    private void checkAutoLoginAndNavigate() {
        if (hasNavigated) return;
        hasNavigated = true;

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            Intent intent = new Intent(Splash.this, DashboardActivity.class);
            startActivity(intent);
            finish();
        } else {
            Intent intent = new Intent(Splash.this, LandingPage.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIFICATIONS) {
            checkAutoLoginAndNavigate();
        }
    }
}
