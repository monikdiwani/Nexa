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
        btnJoin.setText("JOINING...");

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

                    // Update the members map of the LedgerBook
                    db.collection("cashbooks").document(bookId)
                            .update("members." + userId, "VIEWER")
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Joined ledger successfully!", Toast.LENGTH_LONG).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to join ledger", e);
                                Toast.makeText(this, "Failed to join: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                resetButton();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Join ledger query failed", e);
                    Toast.makeText(this, "Failed to join: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    resetButton();
                });
    }

    private void resetButton() {
        btnJoin.setEnabled(true);
        btnJoin.setText("JOIN LEDGER");
    }
}
