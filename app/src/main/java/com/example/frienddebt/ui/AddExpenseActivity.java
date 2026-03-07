package com.example.frienddebt.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.frienddebt.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddExpenseActivity extends AppCompatActivity {

    private EditText edtAmount, edtPayer, edtParticipants, edtDescription;
    private Button btnSave;

    private String groupId;
    private String expenseId;   // 🔥 for edit mode

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        edtAmount = findViewById(R.id.edtAmount);
        edtPayer = findViewById(R.id.edtPayer);
        edtParticipants = findViewById(R.id.edtParticipants);
        edtDescription = findViewById(R.id.edtDescription);
        btnSave = findViewById(R.id.btnSaveExpense);

        // Animate form container
        findViewById(R.id.main).startAnimation(
                AnimationUtils.loadAnimation(this, R.anim.slide_up)
        );

        groupId = getIntent().getStringExtra("GROUP_ID");
        expenseId = getIntent().getStringExtra("EXPENSE_ID"); // null if new

        if (groupId == null || groupId.isEmpty()) {
            Toast.makeText(this, "Group not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (expenseId != null) {
            loadExpenseForEdit();
            btnSave.setText("Update Expense");
        }

        btnSave.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_pop));
            saveOrUpdateExpense();
        });
    }

    private void loadExpenseForEdit() {
        db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .collection("groups")
                .document(groupId)
                .collection("expenses")
                .document(expenseId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        edtAmount.setText(String.valueOf(doc.getDouble("amount")));
                        edtPayer.setText(doc.getString("paidBy"));

                        List<String> parts = (List<String>) doc.get("participants");
                        if (parts != null) {
                            edtParticipants.setText(String.join(", ", parts));
                        }

                        edtDescription.setText(doc.getString("description"));
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private void saveOrUpdateExpense() {
        String amountStr = edtAmount.getText().toString().trim();
        String payerName = edtPayer.getText().toString().trim();
        String participantsStr = edtParticipants.getText().toString().trim();
        String desc = edtDescription.getText().toString().trim();

        if (amountStr.isEmpty() || payerName.isEmpty() || participantsStr.isEmpty()) {
            Toast.makeText(this, "Fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (Exception e) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> participants = new ArrayList<>();
        for (String n : participantsStr.split(",")) {
            String p = n.trim();
            if (!p.isEmpty()) participants.add(p);
        }

        Map<String, Object> expense = new HashMap<>();
        expense.put("amount", amount);
        expense.put("paidBy", payerName);
        expense.put("participants", participants);
        expense.put("description", desc);
        expense.put("updatedAt", System.currentTimeMillis());

        if (expenseId == null) {
            // ➕ ADD NEW
            db.collection("users")
                    .document(auth.getCurrentUser().getUid())
                    .collection("groups")
                    .document(groupId)
                    .collection("expenses")
                    .add(expense)
                    .addOnSuccessListener(ref -> {
                        Toast.makeText(this, "Expense added", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show()
                    );
        } else {
            // ✏️ UPDATE EXISTING
            db.collection("users")
                    .document(auth.getCurrentUser().getUid())
                    .collection("groups")
                    .document(groupId)
                    .collection("expenses")
                    .document(expenseId)
                    .update(expense)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Expense updated", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show()
                    );
        }
    }
}
