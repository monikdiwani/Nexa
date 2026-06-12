package com.example.frienddebt.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import com.example.frienddebt.utils.StatusBarUtil;

public class JoinGroupActivity extends AppCompatActivity {

    private static final String TAG = "JoinGroupActivity";

    private EditText edtInviteCode;
    private Button btnJoin;

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

    

    

        // We reuse the XML but change the logic to Ledgers
        edtInviteCode = findViewById(R.id.edtInviteCode);
        btnJoin = findViewById(R.id.btnJoinGroup);
        btnJoin.setText("JOIN LEDGER");

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        btnJoin.setOnClickListener(v -> joinLedgerByCode());
    }

    private void joinLedgerByCode() {
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

        db.collection("invite_codes")
                .document(code)
                .get()
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

                    // Check if already a member
                    db.collection("cashbooks").document(bookId).get()
                        .addOnSuccessListener(bookDoc -> {
                            Object existingRole = bookDoc.get("members." + userId);
                            if (existingRole != null) {
                                Toast.makeText(this, "You are already a member of this cashbook!", Toast.LENGTH_LONG).show();
                                resetButton();
                                return;
                            }
                            // Show role picker dialog
                            showRolePickerDialog(bookId, userId, bookDoc.getString("name"));
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Failed to verify cashbook: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            resetButton();
                        });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e(TAG, "Join ledger query failed", e);
                    Toast.makeText(this, "Failed to join: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    resetButton();
                });
    }

    private void showRolePickerDialog(String bookId, String userId, String bookName) {
        String displayName = bookName != null ? bookName : "this cashbook";

        // Build a custom dialog with two clear options
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Join \"" + displayName + "\"");
        builder.setMessage("Choose your role in this cashbook:\n\n"
            + "🔍  Viewer — Can see all entries but cannot add or edit.\n\n"
            + "✏️  Editor — Can add and edit cash entries.");

        builder.setPositiveButton("✏️  Join as Editor", (dialog, which) -> {
            joinWithRole(bookId, userId, "EDITOR");
        });
        builder.setNegativeButton("🔍  Join as Viewer", (dialog, which) -> {
            joinWithRole(bookId, userId, "VIEWER");
        });
        builder.setNeutralButton("Cancel", (dialog, which) -> resetButton());
        builder.setCancelable(false);
        builder.show();
    }

    private void joinWithRole(String bookId, String userId, String role) {
        db.collection("cashbooks").document(bookId)
                .update("members." + userId, role)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Joined as " + role + "! Welcome aboard.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e(TAG, "Failed to join ledger", e);
                    Toast.makeText(this, "Failed to join: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    resetButton();
                });
    }

    private void resetButton() {
        btnJoin.setEnabled(true);
        btnJoin.setText("JOIN LEDGER");
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
