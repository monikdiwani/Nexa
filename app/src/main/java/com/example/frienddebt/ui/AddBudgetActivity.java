package com.example.frienddebt.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Budget;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import com.example.frienddebt.utils.StatusBarUtil;

public class AddBudgetActivity extends AppCompatActivity {

    private AutoCompleteTextView actvCategory;
    private EditText etAmount;
    private Button btnSaveBudget;
    private ImageButton btnBack;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private static final String[] CATEGORIES = {
            "Sales", "Rent", "Salary", "Office", "Personal", "Food", "Transport", "Shopping", "Bills", "Other"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_budget);
        StatusBarUtil.applyStatusBarPadding(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        actvCategory = findViewById(R.id.actvCategory);
        etAmount = findViewById(R.id.etAmount);
        btnSaveBudget = findViewById(R.id.btnSaveBudget);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, CATEGORIES);
        actvCategory.setAdapter(adapter);
        actvCategory.setText(CATEGORIES[5], false); // Default to Food

        btnSaveBudget.setOnClickListener(v -> saveBudget());
    }

    private void saveBudget() {
        String category = actvCategory.getText().toString().trim();
        String amountStr = etAmount.getText().toString().trim();

        if (amountStr.isEmpty()) {
            etAmount.setError("Amount limit is required");
            etAmount.requestFocus();
            return;
        }

        double amountLimit;
        try {
            amountLimit = Double.parseDouble(amountStr);
            if (amountLimit <= 0) {
                etAmount.setError("Amount must be greater than zero");
                etAmount.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            etAmount.setError("Invalid amount format");
            etAmount.requestFocus();
            return;
        }

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        String budgetId = db.collection("users").document(userId).collection("budgets").document().getId();

        Budget budget = new Budget(budgetId, category, amountLimit, "MONTHLY", System.currentTimeMillis());

        btnSaveBudget.setEnabled(false);
        btnSaveBudget.setText("Saving...");

        db.collection("users").document(userId).collection("budgets").document(budgetId)
                .set(budget.toFirestoreMap())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Budget saved!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSaveBudget.setEnabled(true);
                    btnSaveBudget.setText("Save Budget");
                    Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
