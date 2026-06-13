package com.example.frienddebt.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;
import com.example.frienddebt.utils.StatusBarUtil;
import com.example.frienddebt.utils.UserProfileHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * JoinGroupActivity — Pending Approval Flow
 *
 * Instead of letting the joining user pick their own role, we now:
 * 1. Verify the invite code is valid
 * 2. Write a pending join request to  cashbooks/{bookId}/pendingMembers/{userId}
 * 3. Notify the cashbook admin via notifications/{adminUid}/pending
 * 4. Show a "Request sent!" confirmation screen
 *
 * The admin then opens PendingApprovalsActivity to accept/decline and assign a role.
 */
public class JoinGroupActivity extends AppCompatActivity {

    private static final String TAG = "JoinGroupActivity";

    private EditText edtInviteCode;
    private Button btnJoin;
    private View layoutCodeInput;   // the initial code-entry panel
    private View layoutSuccess;     // the success/waiting panel
    private TextView txtSuccessBook;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_group);
        StatusBarUtil.applyStatusBarPadding(this);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        edtInviteCode = findViewById(R.id.edtInviteCode);
        btnJoin = findViewById(R.id.btnJoinGroup);
        btnJoin.setText("SEND JOIN REQUEST");

        // Success panel (we reuse the same XML but toggle visibility)
        layoutCodeInput  = findViewById(R.id.layoutCodeInput);
        layoutSuccess    = findViewById(R.id.layoutSuccess);
        txtSuccessBook   = findViewById(R.id.txtSuccessBook);

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        btnJoin.setOnClickListener(v -> sendJoinRequest());
    }

    // ─── Step 1: Validate the invite code ─────────────────────────────────────

    private void sendJoinRequest() {
        String code = edtInviteCode.getText().toString().trim().toUpperCase();

        if (code.isEmpty()) {
            Toast.makeText(this, "Enter invite code", Toast.LENGTH_SHORT).show();
            return;
        }
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        btnJoin.setEnabled(false);
        btnJoin.setText("CHECKING...");

        db.collection("invite_codes").document(code).get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) {
                    Toast.makeText(this, "Invalid invite code", Toast.LENGTH_SHORT).show();
                    resetButton();
                    return;
                }

                String bookId = doc.getString("bookId");
                if (bookId == null) {
                    Toast.makeText(this, "Corrupted invite code data", Toast.LENGTH_SHORT).show();
                    resetButton();
                    return;
                }

                db.collection("cashbooks").document(bookId).get()
                    .addOnSuccessListener(bookDoc -> {
                        // Already a member?
                        if (bookDoc.get("members." + userId) != null) {
                            Toast.makeText(this, "You are already a member of this cashbook!", Toast.LENGTH_LONG).show();
                            resetButton();
                            return;
                        }
                        // Already have a pending request?
                        db.collection("cashbooks").document(bookId)
                            .collection("pendingMembers").document(userId).get()
                            .addOnSuccessListener(pendingDoc -> {
                                if (pendingDoc.exists()) {
                                    Toast.makeText(this, "You already have a pending request for this cashbook.", Toast.LENGTH_LONG).show();
                                    resetButton();
                                    return;
                                }
                                // All good — submit the request
                                String bookName = bookDoc.getString("name");
                                String ownerId  = bookDoc.getString("ownerId");
                                submitPendingRequest(bookId, userId, bookName, ownerId);
                            })
                            .addOnFailureListener(e -> { Toast.makeText(this, "Error checking request: " + e.getMessage(), Toast.LENGTH_LONG).show(); resetButton(); });
                    })
                    .addOnFailureListener(e -> { Toast.makeText(this, "Failed to verify cashbook: " + e.getMessage(), Toast.LENGTH_LONG).show(); resetButton(); });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Join request failed", e);
                Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                resetButton();
            });
    }

    // ─── Step 2: Write pending request + notify admin ──────────────────────────

    private void submitPendingRequest(String bookId, String userId, String bookName, String ownerId) {
        String displayName = auth.getCurrentUser().getDisplayName();
        String email       = auth.getCurrentUser().getEmail();
        if (displayName == null || displayName.isEmpty()) displayName = email;

        Map<String, Object> pending = new HashMap<>();
        pending.put("displayName", displayName);
        pending.put("email",       email != null ? email : "");
        pending.put("status",      "PENDING");
        pending.put("requestedAt", System.currentTimeMillis());

        String finalDisplayName = displayName;
        db.collection("cashbooks").document(bookId)
            .collection("pendingMembers").document(userId)
            .set(pending)
            .addOnSuccessListener(aVoid -> {
                // Notify the admin
                if (ownerId != null) {
                    UserProfileHelper.sendJoinRequestNotification(db, ownerId, finalDisplayName,
                            bookName != null ? bookName : "a cashbook");
                }
                showSuccessScreen(bookName);
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to send request: " + e.getMessage(), Toast.LENGTH_LONG).show();
                resetButton();
            });
    }

    // ─── Step 3: Show "waiting for admin" screen ───────────────────────────────

    private void showSuccessScreen(String bookName) {
        if (layoutCodeInput != null) layoutCodeInput.setVisibility(View.GONE);
        if (layoutSuccess   != null) layoutSuccess.setVisibility(View.VISIBLE);
        if (txtSuccessBook  != null) {
            txtSuccessBook.setText("Your request to join \"" +
                    (bookName != null ? bookName : "the cashbook") +
                    "\" has been sent.\n\nThe admin will review your request and assign your role.");
        }
    }

    private void resetButton() {
        btnJoin.setEnabled(true);
        btnJoin.setText("SEND JOIN REQUEST");
    }

    @Override
    public void startActivity(android.content.Intent intent) {
        super.startActivity(intent);
        com.example.frienddebt.utils.AnimationHelper.applyStartTransition(this, intent);
    }

    @Override
    public void finish() {
        super.finish();
        com.example.frienddebt.utils.AnimationHelper.applyFinishTransition(this);
    }
}
