package com.example.frienddebt.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ProfileFragment extends Fragment {

    private TextView txtProfileInitials, txtProfileName, txtProfileEmail, txtMemberSince;
    private TextView btnEditProfile, txtPasswordHint, txtLoginProvider;
    private LinearLayout rowChangePassword;
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
        txtProfileName = view.findViewById(R.id.txtProfileName);
        txtProfileEmail = view.findViewById(R.id.txtProfileEmail);
        txtMemberSince = view.findViewById(R.id.txtMemberSince);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        txtPasswordHint = view.findViewById(R.id.txtPasswordHint);
        txtLoginProvider = view.findViewById(R.id.txtLoginProvider);
        rowChangePassword = view.findViewById(R.id.rowChangePassword);
        switchDarkMode = view.findViewById(R.id.switchDarkMode);
        switchMorningDigest = view.findViewById(R.id.switchMorningDigest);
        switchEveningDigest = view.findViewById(R.id.switchEveningDigest);
        btnLogout = view.findViewById(R.id.btnLogout);

        setupUserInfo();
        setupPreferences();
        setupAccountSection();

        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());

        rowChangePassword.setOnClickListener(v -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) return;

            if (isGoogleOnlyUser(user)) {
                showSetPasswordDialog(user);
            } else {
                showChangePasswordDialog(user);
            }
        });

        btnLogout.setOnClickListener(v -> {
            Animation pop = AnimationUtils.loadAnimation(requireContext(), R.anim.button_pop);
            v.startAnimation(pop);

            new AlertDialog.Builder(requireContext())
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to log out?")
                    .setPositiveButton("Logout", (dialog, which) -> {
                        auth.signOut();
                        sp.edit().remove("user_id").apply();
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
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Display name
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            txtProfileName.setText(displayName);
            String initials = getInitials(displayName);
            txtProfileInitials.setText(initials);
        } else {
            txtProfileName.setText("Set your name");
            String email = user.getEmail();
            if (email != null && !email.isEmpty()) {
                txtProfileInitials.setText(email.substring(0, 1).toUpperCase());
            }
        }

        // Email
        String email = user.getEmail();
        if (email != null) {
            txtProfileEmail.setText(email);
        }

        // Member since
        long creationTimestamp = user.getMetadata() != null ? user.getMetadata().getCreationTimestamp() : 0;
        if (creationTimestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
            txtMemberSince.setText("Member since " + sdf.format(new Date(creationTimestamp)));
        } else {
            txtMemberSince.setVisibility(View.GONE);
        }
    }

    private void setupAccountSection() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (isGoogleOnlyUser(user)) {
            txtLoginProvider.setText("Google Account");
            txtPasswordHint.setText("Set a password for email login");
        } else {
            boolean hasGoogle = false;
            for (UserInfo info : user.getProviderData()) {
                if ("google.com".equals(info.getProviderId())) {
                    hasGoogle = true;
                    break;
                }
            }
            if (hasGoogle) {
                txtLoginProvider.setText("Email + Google");
            } else {
                txtLoginProvider.setText("Email & Password");
            }
            txtPasswordHint.setText("Update your password");
        }
    }

    private boolean isGoogleOnlyUser(FirebaseUser user) {
        boolean hasPassword = false;
        boolean hasGoogle = false;
        for (UserInfo info : user.getProviderData()) {
            if ("password".equals(info.getProviderId())) {
                hasPassword = true;
            }
            if ("google.com".equals(info.getProviderId())) {
                hasGoogle = true;
            }
        }
        return hasGoogle && !hasPassword;
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "U";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        return parts[0].substring(0, 1).toUpperCase();
    }

    // ═══════════════════════════════════════════
    // EDIT PROFILE DIALOG
    // ═══════════════════════════════════════════
    private void showEditProfileDialog() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        EditText inputName = new EditText(requireContext());
        inputName.setHint("Display Name");
        inputName.setInputType(InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        if (user.getDisplayName() != null) {
            inputName.setText(user.getDisplayName());
        }
        layout.addView(inputName);

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit Profile")
                .setMessage("Update your display name")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = inputName.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                            .setDisplayName(newName)
                            .build();

                    user.updateProfile(profileUpdate)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show();
                                setupUserInfo(); // Refresh UI
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════
    // CHANGE PASSWORD DIALOG (for email/password users)
    // ═══════════════════════════════════════════════
    private void showChangePasswordDialog(FirebaseUser user) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        EditText inputCurrentPwd = new EditText(requireContext());
        inputCurrentPwd.setHint("Current Password");
        inputCurrentPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputCurrentPwd);

        EditText inputNewPwd = new EditText(requireContext());
        inputNewPwd.setHint("New Password (min 6 chars)");
        inputNewPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 24;
        inputNewPwd.setLayoutParams(params);
        layout.addView(inputNewPwd);

        EditText inputConfirmPwd = new EditText(requireContext());
        inputConfirmPwd.setHint("Confirm New Password");
        inputConfirmPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params2.topMargin = 24;
        inputConfirmPwd.setLayoutParams(params2);
        layout.addView(inputConfirmPwd);

        new AlertDialog.Builder(requireContext())
                .setTitle("Change Password")
                .setView(layout)
                .setPositiveButton("Update", (dialog, which) -> {
                    String currentPwd = inputCurrentPwd.getText().toString();
                    String newPwd = inputNewPwd.getText().toString();
                    String confirmPwd = inputConfirmPwd.getText().toString();

                    if (currentPwd.isEmpty()) {
                        Toast.makeText(requireContext(), "Enter current password", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPwd.length() < 6) {
                        Toast.makeText(requireContext(), "New password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPwd.equals(confirmPwd)) {
                        Toast.makeText(requireContext(), "Passwords don't match", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Re-authenticate then update
                    String email = user.getEmail();
                    if (email == null) return;

                    AuthCredential credential = EmailAuthProvider.getCredential(email, currentPwd);
                    user.reauthenticate(credential)
                            .addOnSuccessListener(aVoid -> {
                                user.updatePassword(newPwd)
                                        .addOnSuccessListener(aVoid2 -> {
                                            Toast.makeText(requireContext(), "Password updated successfully!", Toast.LENGTH_SHORT).show();
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(requireContext(), "Authentication failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════
    // SET PASSWORD DIALOG (for Google-only users)
    // ═══════════════════════════════════════════════════
    private void showSetPasswordDialog(FirebaseUser user) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        TextView info = new TextView(requireContext());
        info.setText("You signed in with Google. Set a password so you can also log in with email & password.");
        info.setTextColor(requireContext().getResources().getColor(R.color.text_secondary));
        info.setTextSize(13f);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoParams.bottomMargin = 24;
        info.setLayoutParams(infoParams);
        layout.addView(info);

        EditText inputNewPwd = new EditText(requireContext());
        inputNewPwd.setHint("New Password (min 6 chars)");
        inputNewPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputNewPwd);

        EditText inputConfirmPwd = new EditText(requireContext());
        inputConfirmPwd.setHint("Confirm Password");
        inputConfirmPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 24;
        inputConfirmPwd.setLayoutParams(params);
        layout.addView(inputConfirmPwd);

        new AlertDialog.Builder(requireContext())
                .setTitle("Set Password")
                .setView(layout)
                .setPositiveButton("Set Password", (dialog, which) -> {
                    String newPwd = inputNewPwd.getText().toString();
                    String confirmPwd = inputConfirmPwd.getText().toString();

                    if (newPwd.length() < 6) {
                        Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPwd.equals(confirmPwd)) {
                        Toast.makeText(requireContext(), "Passwords don't match", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String email = user.getEmail();
                    if (email == null) {
                        Toast.makeText(requireContext(), "No email associated with this account", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Link email/password credential to the Google account
                    AuthCredential emailCredential = EmailAuthProvider.getCredential(email, newPwd);
                    user.linkWithCredential(emailCredential)
                            .addOnSuccessListener(authResult -> {
                                Toast.makeText(requireContext(), "Password set! You can now log in with email too.", Toast.LENGTH_SHORT).show();
                                setupAccountSection(); // Refresh UI
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════
    // PREFERENCES
    // ═══════════════════════════════════════════
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
