package com.example.frienddebt.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.frienddebt.R;
import com.example.frienddebt.notification.DailySummaryWorker;
import com.example.frienddebt.notification.NightSummaryWorker;
import com.example.frienddebt.ui.Login;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class ProfileFragment extends Fragment {

    private TextView txtProfileInitials, txtProfileEmail;
    private SwitchCompat switchDarkMode, switchMorningDigest, switchEveningDigest;
    private Button btnLogout;

    private FirebaseAuth auth;
    private SharedPreferences sp;

    private static final String PREFS_NAME = "NexaPrefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_MORNING_DIGEST = "morning_digest";
    private static final String KEY_EVENING_DIGEST = "evening_digest";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        auth = FirebaseAuth.getInstance();
        sp = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        txtProfileInitials = view.findViewById(R.id.txtProfileInitials);
        txtProfileEmail = view.findViewById(R.id.txtProfileEmail);
        switchDarkMode = view.findViewById(R.id.switchDarkMode);
        switchMorningDigest = view.findViewById(R.id.switchMorningDigest);
        switchEveningDigest = view.findViewById(R.id.switchEveningDigest);
        btnLogout = view.findViewById(R.id.btnLogout);

        setupUserInfo();
        setupPreferences();

        btnLogout.setOnClickListener(v -> {
            Animation pop = AnimationUtils.loadAnimation(requireContext(), R.anim.button_pop);
            v.startAnimation(pop);

            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to log out?")
                    .setPositiveButton("Logout", (dialog, which) -> {
                        auth.signOut();
                        Intent intent = new Intent(requireActivity(), Login.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        requireActivity().finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        return view;
    }

    private void setupUserInfo() {
        if (auth.getCurrentUser() != null) {
            String email = auth.getCurrentUser().getEmail();
            if (email != null) {
                txtProfileEmail.setText(email);
                String initials = email.substring(0, 1).toUpperCase();
                txtProfileInitials.setText(initials);
            }
        }
    }

    private void setupPreferences() {
        boolean isDarkMode = sp.getBoolean(KEY_DARK_MODE, false);
        switchDarkMode.setChecked(isDarkMode);
        AppCompatDelegate.setDefaultNightMode(isDarkMode ?
            AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked ?
                    AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        boolean isMorningEnabled = sp.getBoolean(KEY_MORNING_DIGEST, true);
        switchMorningDigest.setChecked(isMorningEnabled);
        if (isMorningEnabled) {
            scheduleDailyDigest();
        } else {
            cancelDailyDigest();
        }
        switchMorningDigest.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean(KEY_MORNING_DIGEST, isChecked).apply();
            if (isChecked) {
                scheduleDailyDigest();
            } else {
                cancelDailyDigest();
            }
        });

        boolean isEveningEnabled = sp.getBoolean(KEY_EVENING_DIGEST, true);
        switchEveningDigest.setChecked(isEveningEnabled);
        if (isEveningEnabled) {
            scheduleNightDigest();
        } else {
            cancelNightDigest();
        }
        switchEveningDigest.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean(KEY_EVENING_DIGEST, isChecked).apply();
            if (isChecked) {
                scheduleNightDigest();
            } else {
                cancelNightDigest();
            }
        });
    }

    private void scheduleDailyDigest() {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 8);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        long initialDelay = target.getTimeInMillis() - now.getTimeInMillis();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                DailySummaryWorker.class,
                24,
                TimeUnit.HOURS
        )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag("NEXA_DAILY_DIGEST")
                .build();

        WorkManager.getInstance(requireContext())
                .enqueueUniquePeriodicWork(
                        "NEXA_DAILY_DIGEST",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        workRequest
                );
    }

    private void cancelDailyDigest() {
        WorkManager.getInstance(requireContext()).cancelAllWorkByTag("NEXA_DAILY_DIGEST");
    }

    private void scheduleNightDigest() {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 21);
        target.set(Calendar.MINUTE, 30);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        long initialDelay = target.getTimeInMillis() - now.getTimeInMillis();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                NightSummaryWorker.class,
                24,
                TimeUnit.HOURS
        )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag("NEXA_NIGHT_DIGEST")
                .build();

        WorkManager.getInstance(requireContext())
                .enqueueUniquePeriodicWork(
                        "NEXA_NIGHT_DIGEST",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        workRequest
                );
    }

    private void cancelNightDigest() {
        WorkManager.getInstance(requireContext()).cancelAllWorkByTag("NEXA_NIGHT_DIGEST");
    }
}
