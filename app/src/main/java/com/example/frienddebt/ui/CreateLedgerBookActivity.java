package com.example.frienddebt.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;
import com.example.frienddebt.model.LedgerBook;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import com.example.frienddebt.utils.StatusBarUtil;

public class CreateLedgerBookActivity extends AppCompatActivity {

    private EditText etBookName;
    private Button btnCreateBook;
    private ImageButton btnBack;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_ledger_book);
        StatusBarUtil.applyStatusBarPadding(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etBookName = findViewById(R.id.etBookName);
        btnCreateBook = findViewById(R.id.btnCreateBook);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        
        String incomingTitle = getIntent().getStringExtra("LINKED_TITLE");
        if (incomingTitle != null && !incomingTitle.isEmpty()) {
            etBookName.setText(incomingTitle + " Ledger");
        }

        btnCreateBook.setOnClickListener(v -> createBook());
    }

    private void createBook() {
        String bookName = etBookName.getText().toString().trim();

        if (bookName.isEmpty()) {
            etBookName.setError("Book name is required");
            etBookName.requestFocus();
            return;
        }

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        long createdAt = System.currentTimeMillis();

        btnCreateBook.setEnabled(false);
        btnCreateBook.setText("CREATING...");

        // Generate a new ID
        String newId = db.collection("cashbooks").document().getId();
        String inviteCode = generateInviteCode();

        LedgerBook newBook = new LedgerBook(newId, bookName, "INR", userId, createdAt);
        newBook.setInviteCode(inviteCode);

        db.collection("cashbooks").document(newId)
                .set(newBook.toFirestoreMap())
                .addOnSuccessListener(aVoid -> {
                    // Save the invite code mapping globally
                    java.util.Map<String, Object> inviteMapping = new java.util.HashMap<>();
                    inviteMapping.put("bookId", newId);
                    inviteMapping.put("ownerId", userId);
                    inviteMapping.put("createdAt", createdAt);
                    db.collection("invite_codes").document(inviteCode).set(inviteMapping);

                    Toast.makeText(CreateLedgerBookActivity.this, "Cashbook created!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnCreateBook.setEnabled(true);
                    btnCreateBook.setText("CREATE CASHBOOK");
                    Toast.makeText(CreateLedgerBookActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int idx = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }
}
