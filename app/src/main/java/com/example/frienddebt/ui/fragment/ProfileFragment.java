package com.example.frienddebt.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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
import android.provider.Settings;
import java.io.File;
import java.io.FileWriter;
import androidx.core.content.FileProvider;
import android.widget.ImageView;
import com.bumptech.glide.Glide;

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
import com.example.frienddebt.notification.NotificationHelper;
import com.example.frienddebt.receiver.SmsReceiver;
import com.example.frienddebt.ui.Login;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ProfileFragment extends Fragment {

    private TextView txtProfileInitials, txtProfileName, txtProfileEmail, txtMemberSince;
    private ImageView imgProfilePicture;
    private androidx.activity.result.ActivityResultLauncher<Intent> galleryLauncher;
    private androidx.activity.result.ActivityResultLauncher<Uri> cameraLauncher;
    private androidx.activity.result.ActivityResultLauncher<String[]> smsPermissionLauncher;
    private Uri pendingPhotoUri = null;

    private TextView btnEditProfile, txtPasswordHint, txtLoginProvider;
    private LinearLayout rowChangePassword;
    private SwitchCompat switchDarkMode, switchMorningDigest, switchEveningDigest, switchAppLock;
    private Button btnLogout, btnDeleteAccount;
    private LinearLayout rowExportData, rowNotificationSettings, rowBatteryOptimization, rowSmsDefaultLedger;
    private androidx.appcompat.widget.SwitchCompat switchSmsAutoAdd;
    private TextView txtDefaultSmsLedger;

    private TextView btnAbout, btnFAQ, btnPrivacyPolicy;

    private FirebaseAuth auth;
    private SharedPreferences sp;

    private static final String PREFS_NAME = "NexaPrefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_MORNING_DIGEST = "morning_digest";
    private static final String KEY_EVENING_DIGEST = "evening_digest";
    private static final String KEY_APP_LOCK = "app_lock_enabled";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Register photo pickers
        smsPermissionLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                Boolean receiveGranted = result.getOrDefault(android.Manifest.permission.RECEIVE_SMS, false);
                Boolean readGranted = result.getOrDefault(android.Manifest.permission.READ_SMS, false);
                if (Boolean.TRUE.equals(receiveGranted) && Boolean.TRUE.equals(readGranted)) {
                    SmsReceiver.setSmsAutoAddEnabled(requireContext(), true);
                    if (switchSmsAutoAdd != null) switchSmsAutoAdd.setChecked(true);
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("SMS Auto-Add Enabled")
                            .setMessage("Nexa will detect bank SMS messages and auto-add transactions to your default ledger. You can disable this anytime.")
                            .setPositiveButton("Got it", null)
                            .show();
                } else {
                    SmsReceiver.setSmsAutoAddEnabled(requireContext(), false);
                    if (switchSmsAutoAdd != null) switchSmsAutoAdd.setChecked(false);
                    Toast.makeText(requireContext(), "SMS permissions are required to automate transactions.", Toast.LENGTH_LONG).show();
                }
            }
        );

        galleryLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) pendingPhotoUri = uri;
                }
            }
        );

        cameraLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
            success -> { /* Uri already set in pendingPhotoUri */ }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        auth = FirebaseAuth.getInstance();
        sp = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        txtProfileInitials = view.findViewById(R.id.txtProfileInitials);
        imgProfilePicture = view.findViewById(R.id.imgProfilePicture);

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
        switchAppLock = view.findViewById(R.id.switchAppLock);
        btnLogout = view.findViewById(R.id.btnLogout);
        btnDeleteAccount = view.findViewById(R.id.btnDeleteAccount);
        rowExportData = view.findViewById(R.id.rowExportData);
        rowNotificationSettings = view.findViewById(R.id.rowNotificationSettings);
        rowBatteryOptimization = view.findViewById(R.id.rowBatteryOptimization);
        switchSmsAutoAdd = view.findViewById(R.id.switchSmsAutoAdd);
        rowSmsDefaultLedger = view.findViewById(R.id.rowSmsDefaultLedger);
        txtDefaultSmsLedger = view.findViewById(R.id.txtDefaultSmsLedger);
        btnAbout = view.findViewById(R.id.btnAbout);
        btnFAQ = view.findViewById(R.id.btnFAQ);
        btnPrivacyPolicy = view.findViewById(R.id.btnPrivacyPolicy);

        setupUserInfo();
        setupPreferences();
        setupAccountSection();

        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());

        // SMS auto-add toggle
        if (switchSmsAutoAdd != null) {
            switchSmsAutoAdd.setOnCheckedChangeListener(null);
            switchSmsAutoAdd.setChecked(SmsReceiver.isSmsAutoAddEnabled(requireContext()));
            
            switchSmsAutoAdd.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        SmsReceiver.setSmsAutoAddEnabled(requireContext(), true);
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle("SMS Auto-Add Enabled")
                                .setMessage("Nexa will detect bank SMS messages and auto-add transactions to your default ledger. You can disable this anytime.")
                                .setPositiveButton("Got it", null)
                                .show();
                    } else {
                        btn.setChecked(false); // Revert visually until granted
                        smsPermissionLauncher.launch(new String[]{
                            android.Manifest.permission.RECEIVE_SMS,
                            android.Manifest.permission.READ_SMS
                        });
                    }
                } else {
                    SmsReceiver.setSmsAutoAddEnabled(requireContext(), false);
                }
            });
        }

        // SMS Default Ledger selection
        if (rowSmsDefaultLedger != null && txtDefaultSmsLedger != null) {
            txtDefaultSmsLedger.setText(SmsReceiver.getDefaultLedgerName(requireContext()));
            
            rowSmsDefaultLedger.setOnClickListener(v -> {
                FirebaseUser user = auth.getCurrentUser();
                if (user == null) return;
                
                FirebaseFirestore.getInstance().collection("cashbooks")
                    .whereEqualTo("ownerId", user.getUid())
                    .get()
                    .addOnSuccessListener(snapshots -> {
                        java.util.List<com.example.frienddebt.model.LedgerBook> personalBooks = new java.util.ArrayList<>();
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                            String type = doc.getString("type");
                            Object membersObj = doc.get("members");
                            int membersSize = membersObj instanceof java.util.Map ? ((java.util.Map) membersObj).size() : 0;
                            boolean isGroup = "GROUP".equals(type) || membersSize > 1;
                            
                            if (!isGroup) {
                                personalBooks.add(com.example.frienddebt.model.LedgerBook.fromDocument(doc));
                            }
                        }
                        
                        if (personalBooks.isEmpty()) {
                            Toast.makeText(requireContext(), "No personal cashbooks found.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        String[] names = new String[personalBooks.size()];
                        int checkedItem = -1;
                        String currentId = SmsReceiver.getDefaultLedgerId(requireContext());
                        
                        for (int i = 0; i < personalBooks.size(); i++) {
                            names[i] = personalBooks.get(i).getName();
                            if (personalBooks.get(i).getId().equals(currentId)) {
                                checkedItem = i;
                            }
                        }
                        
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Select Default SMS Ledger")
                            .setSingleChoiceItems(names, checkedItem, (dialog, which) -> {
                                com.example.frienddebt.model.LedgerBook selected = personalBooks.get(which);
                                SmsReceiver.setDefaultLedger(requireContext(), selected.getId(), selected.getName());
                                txtDefaultSmsLedger.setText(selected.getName());
                                dialog.dismiss();
                                Toast.makeText(requireContext(), "Default SMS Ledger updated", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    });
            });
        }

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

        rowExportData.setOnClickListener(v -> exportUserData());
        
        rowNotificationSettings.setOnClickListener(v -> showNotificationPrefsSheet());


        rowBatteryOptimization.setOnClickListener(v -> {
            Intent intent = new Intent();
            String packageName = requireContext().getPackageName();
            android.os.PowerManager pm = (android.os.PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            } else {
                Toast.makeText(requireContext(), "Battery Optimization already disabled for Nexa!", Toast.LENGTH_SHORT).show();
            }
        });

        btnAbout.setOnClickListener(v -> showLegalDialog("About Nexa", "Nexa is your ultimate productivity and finance companion. Built with passion to help you manage your tasks, notes, and money securely."));
        btnFAQ.setOnClickListener(v -> showLegalDialog("FAQ", "Q: Is my data secure?\nA: Yes, everything is securely stored.\n\nQ: Can I access Nexa offline?\nA: Nexa has basic offline caching but requires internet for syncing."));
        btnPrivacyPolicy.setOnClickListener(v -> showLegalDialog("Privacy Policy", "We respect your privacy. We do not sell your personal data. Your data is stored securely in Firebase and is only accessible by you."));

        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());

        return view;
    }

    private void setupUserInfo() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Display name and Profile Picture
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
        
        Uri photoUrl = user.getPhotoUrl();
        if (photoUrl != null) {
            imgProfilePicture.setVisibility(View.VISIBLE);
            txtProfileInitials.setVisibility(View.GONE);
            Glide.with(this)
                 .load(photoUrl)
                 .circleCrop()
                 .into(imgProfilePicture);
        } else {
            imgProfilePicture.setVisibility(View.GONE);
            txtProfileInitials.setVisibility(View.VISIBLE);
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

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());

        android.view.View sheetView = android.view.LayoutInflater.from(requireContext())
                .inflate(R.layout.layout_edit_profile_sheet, null);

        ImageView ivPreview = sheetView.findViewById(R.id.ivEditProfilePreview);
        android.widget.Button btnGallery = sheetView.findViewById(R.id.btnPickGallery);
        android.widget.Button btnCamera = sheetView.findViewById(R.id.btnPickCamera);
        EditText etName = sheetView.findViewById(R.id.etEditProfileName);
        android.widget.Button btnSave = sheetView.findViewById(R.id.btnSaveProfile);

        // Pre-fill
        if (user.getDisplayName() != null) etName.setText(user.getDisplayName());
        if (user.getPhotoUrl() != null) {
            com.bumptech.glide.Glide.with(this).load(user.getPhotoUrl()).circleCrop().into(ivPreview);
        }

        btnGallery.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(pick);
            // After picking, pendingPhotoUri will be set. Preview update on resume.
        });

        btnCamera.setOnClickListener(v -> {
            java.io.File tmp = new java.io.File(requireContext().getCacheDir(), "nexa_profile_tmp.jpg");
            Uri camUri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(), requireContext().getPackageName() + ".provider", tmp);
            pendingPhotoUri = camUri;
            cameraLauncher.launch(camUri);
        });

        btnSave.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (newName.isEmpty()) {
                etName.setError("Name cannot be empty");
                return;
            }
            btnSave.setEnabled(false);
            btnSave.setText("Saving...");

            if (pendingPhotoUri != null) {
                // Upload photo first
                com.google.firebase.storage.FirebaseStorage storage =
                        com.google.firebase.storage.FirebaseStorage.getInstance();
                com.google.firebase.storage.StorageReference ref =
                        storage.getReference("profile_photos/" + user.getUid() + ".jpg");
                ref.putFile(pendingPhotoUri).continueWithTask(task -> ref.getDownloadUrl())
                        .addOnSuccessListener(downloadUri -> {
                            UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(newName)
                                    .setPhotoUri(downloadUri)
                                    .build();
                            user.updateProfile(req).addOnSuccessListener(x -> {
                                Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show();
                                setupUserInfo();
                                sheet.dismiss();
                            });
                        })
                        .addOnFailureListener(e -> {
                            btnSave.setEnabled(true);
                            btnSave.setText("Save");
                            Toast.makeText(requireContext(), "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
            } else {
                // Name only
                UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                        .setDisplayName(newName).build();
                user.updateProfile(req).addOnSuccessListener(x -> {
                    Toast.makeText(requireContext(), "Name updated!", Toast.LENGTH_SHORT).show();
                    setupUserInfo();
                    sheet.dismiss();
                }).addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save");
                    Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });

        sheet.setContentView(sheetView);
        sheet.show();
    }

    // ═══════════════════════════════════════════════
    // NOTIFICATION PREFERENCES SHEET
    // ═══════════════════════════════════════════════
    private void showNotificationPrefsSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());

        android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(64, 48, 64, 48);
        root.setBackgroundColor(getResources().getColor(R.color.surface_primary));

        // Title
        android.widget.TextView title = new android.widget.TextView(requireContext());
        title.setText("Notification Preferences");
        title.setTextSize(18f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setPadding(0, 0, 0, 32);
        root.addView(title);

        // Helper to add a switch row
        android.widget.LinearLayout.LayoutParams rowParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 8, 0, 8);

        String[][] items = {
                {NotificationHelper.KEY_REMINDERS, "⏰ Reminders", "Alert when your reminders fire"},
                {NotificationHelper.KEY_DIGEST,    "📋 Daily Digest", "Morning & evening summaries"},
                {NotificationHelper.KEY_TASKS,     "✅ Tasks", "Task due date reminders"},
                {NotificationHelper.KEY_MONEY,     "💰 Money Activity", "New entries and settlements"},
                {NotificationHelper.KEY_ACTIVITY,  "🔔 General Activity", "Other Nexa alerts"}
        };

        for (String[] item : items) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setLayoutParams(rowParams);

            android.widget.LinearLayout textCol = new android.widget.LinearLayout(requireContext());
            textCol.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.LinearLayout.LayoutParams textParams =
                    new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textCol.setLayoutParams(textParams);

            android.widget.TextView labelView = new android.widget.TextView(requireContext());
            labelView.setText(item[1]);
            labelView.setTextSize(15f);
            labelView.setTextColor(getResources().getColor(R.color.text_primary));

            android.widget.TextView subView = new android.widget.TextView(requireContext());
            subView.setText(item[2]);
            subView.setTextSize(12f);
            subView.setTextColor(getResources().getColor(R.color.text_secondary));

            textCol.addView(labelView);
            textCol.addView(subView);
            row.addView(textCol);

            androidx.appcompat.widget.SwitchCompat sw = new androidx.appcompat.widget.SwitchCompat(requireContext());
            sw.setChecked(NotificationHelper.isNotificationEnabled(requireContext(), item[0]));
            final String key = item[0];
            sw.setOnCheckedChangeListener((btn, checked) ->
                    NotificationHelper.setNotificationEnabled(requireContext(), key, checked));
            row.addView(sw);

            root.addView(row);
        }

        // System settings link
        android.widget.TextView systemLink = new android.widget.TextView(requireContext());
        systemLink.setText("Advanced system notification settings →");
        systemLink.setTextSize(13f);
        systemLink.setTextColor(getResources().getColor(R.color.primary));
        systemLink.setPadding(0, 32, 0, 0);
        systemLink.setOnClickListener(v2 -> {
            Intent sysIntent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            sysIntent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
            startActivity(sysIntent);
        });
        root.addView(systemLink);

        sheet.setContentView(root);
        sheet.show();
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

        boolean isAppLockEnabled = sp.getBoolean(KEY_APP_LOCK, false);
        switchAppLock.setChecked(isAppLockEnabled);
        switchAppLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                androidx.biometric.BiometricManager biometricManager = androidx.biometric.BiometricManager.from(requireContext());
                int status = biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL);
                if (status == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE || status == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
                    Toast.makeText(requireContext(), "Biometric security is not supported on this device.", Toast.LENGTH_LONG).show();
                    switchAppLock.setChecked(false);
                    return;
                } else if (status == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                    Toast.makeText(requireContext(), "Please set up a Pattern, PIN, or Fingerprint in your device Settings first.", Toast.LENGTH_LONG).show();
                    switchAppLock.setChecked(false);
                    return;
                }
            }
            sp.edit().putBoolean(KEY_APP_LOCK, isChecked).apply();
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

    private void showLegalDialog(String title, String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }

    private void exportUserData() {
        com.example.frienddebt.utils.DataExportHelper.exportData(requireActivity());
    }

    private void showDeleteAccountDialog() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage("Are you absolutely sure you want to delete your account? This will permanently erase all your data from our servers. This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Toast.makeText(requireContext(), "Deleting account...", Toast.LENGTH_SHORT).show();
                    String uid = user.getUid();
                    
                    com.google.firebase.firestore.FirebaseFirestore firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                    com.google.firebase.firestore.WriteBatch batch = firestore.batch();
                    
                    // Fetch and delete tasks
                    firestore.collection("users").document(uid).collection("tasks").get()
                        .addOnSuccessListener(tasksSnap -> {
                            for (com.google.firebase.firestore.DocumentSnapshot doc : tasksSnap.getDocuments()) {
                                batch.delete(doc.getReference());
                            }
                            
                            // Fetch and delete notes
                            firestore.collection("users").document(uid).collection("notes").get()
                                .addOnSuccessListener(notesSnap -> {
                                    for (com.google.firebase.firestore.DocumentSnapshot doc : notesSnap.getDocuments()) {
                                        batch.delete(doc.getReference());
                                    }
                                    
                                    // Fetch and delete reminders
                                    firestore.collection("users").document(uid).collection("reminders").get()
                                        .addOnSuccessListener(remindersSnap -> {
                                            for (com.google.firebase.firestore.DocumentSnapshot doc : remindersSnap.getDocuments()) {
                                                batch.delete(doc.getReference());
                                            }
                                            
                                            // Fetch and delete budgets
                                            firestore.collection("users").document(uid).collection("budgets").get()
                                                .addOnSuccessListener(budgetsSnap -> {
                                                    for (com.google.firebase.firestore.DocumentSnapshot doc : budgetsSnap.getDocuments()) {
                                                        batch.delete(doc.getReference());
                                                    }
                                                    
                                                    // Clean up cashbooks
                                                    firestore.collection("cashbooks")
                                                        .whereNotEqualTo("members." + uid, null)
                                                        .get()
                                                        .addOnSuccessListener(cashbooksSnap -> {
                                                            for (com.google.firebase.firestore.DocumentSnapshot bookDoc : cashbooksSnap.getDocuments()) {
                                                                java.util.Map<String, Object> members = (java.util.Map<String, Object>) bookDoc.get("members");
                                                                if (members != null && members.size() <= 1 && uid.equals(bookDoc.getString("ownerId"))) {
                                                                    batch.delete(bookDoc.getReference());
                                                                } else {
                                                                    batch.update(bookDoc.getReference(), "members." + uid, com.google.firebase.firestore.FieldValue.delete());
                                                                }
                                                            }
                                                            
                                                            // Delete parent user doc
                                                            batch.delete(firestore.collection("users").document(uid));
                                                            
                                                            // Commit batch
                                                            batch.commit().addOnCompleteListener(batchTask -> {
                                                                // Delete Firebase Auth user
                                                                user.delete().addOnCompleteListener(task1 -> {
                                                                    if (task1.isSuccessful()) {
                                                                        sp.edit().remove("user_id").apply();
                                                                        Intent intent = new Intent(requireActivity(), Login.class);
                                                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                                        startActivity(intent);
                                                                        requireActivity().finish();
                                                                    } else {
                                                                        Toast.makeText(requireContext(), "Failed to delete account. You may need to re-authenticate first.", Toast.LENGTH_LONG).show();
                                                                    }
                                                                });
                                                            });
                                                        });
                                                });
                                        });
                                });
                        });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
