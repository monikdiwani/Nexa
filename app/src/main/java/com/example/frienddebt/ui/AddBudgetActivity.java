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
    private com.google.android.material.textfield.TextInputLayout layoutCustomCategory;
    private EditText etCustomCategory, etBudgetName;
    private EditText etAmount;
    private AutoCompleteTextView actvPeriod;
    private com.google.android.material.slider.Slider sliderAlertThreshold;
    private android.widget.TextView txtThresholdLabel;
    private Button btnSaveBudget;
    private ImageButton btnBack;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private static final String[] CATEGORIES = {
            "Sales", "Rent", "Salary", "Office", "Personal", "Food", "Transport", "Shopping", "Bills", "Other"
    };

    private static final String[] PERIODS = {"DAILY", "WEEKLY", "MONTHLY", "YEARLY"};

    private String editingBudgetId = null; // non-null = edit mode

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_budget);
        StatusBarUtil.applyStatusBarPadding(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        actvCategory = findViewById(R.id.actvCategory);
        etAmount = findViewById(R.id.etAmount);
        etBudgetName = findViewById(R.id.etBudgetName);
        btnSaveBudget = findViewById(R.id.btnSaveBudget);
        btnBack = findViewById(R.id.btnBack);
        actvPeriod = findViewById(R.id.actvPeriod);
        sliderAlertThreshold = findViewById(R.id.sliderAlertThreshold);
        txtThresholdLabel = findViewById(R.id.txtThresholdLabel);

        btnBack.setOnClickListener(v -> finish());

        // Setup period dropdown
        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, PERIODS);
        actvPeriod.setAdapter(periodAdapter);
        actvPeriod.setText(PERIODS[2], false); // Default to MONTHLY

        // Threshold slider label
        if (sliderAlertThreshold != null && txtThresholdLabel != null) {
            txtThresholdLabel.setText((int) sliderAlertThreshold.getValue() + "% of limit");
            sliderAlertThreshold.addOnChangeListener((slider, value, fromUser) ->
                txtThresholdLabel.setText((int) value + "% of limit"));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, CATEGORIES);
        actvCategory.setAdapter(adapter);
        actvCategory.setText(CATEGORIES[5], false); // Default to Food

        layoutCustomCategory = findViewById(R.id.layoutCustomCategory);
        etCustomCategory = findViewById(R.id.etCustomCategory);

        actvCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selected = adapter.getItem(position);
            if ("Other".equalsIgnoreCase(selected)) {
                layoutCustomCategory.setVisibility(android.view.View.VISIBLE);
            } else {
                layoutCustomCategory.setVisibility(android.view.View.GONE);
                etCustomCategory.setText("");
            }
        });

        btnSaveBudget.setOnClickListener(v -> saveBudget());

        // Detect edit mode
        editingBudgetId = getIntent().getStringExtra("BUDGET_ID");
        if (editingBudgetId != null) {
            btnSaveBudget.setText("Update Budget");
            loadBudgetForEdit(editingBudgetId);
        }
    }

    /** Load existing budget data for edit mode */
    private void loadBudgetForEdit(String budgetId) {
        if (auth.getCurrentUser() == null) return;
        db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .collection("budgets")
                .document(budgetId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String cat = doc.getString("category");
                    Double amount = doc.getDouble("amountLimit");
                    String period = doc.getString("period");

                    if (cat != null) {
                        boolean isPreset = false;
                        for (String c : CATEGORIES) { if (c.equals(cat)) { isPreset = true; break; } }
                        if (isPreset) {
                            actvCategory.setText(cat, false);
                            layoutCustomCategory.setVisibility(android.view.View.GONE);
                        } else {
                            actvCategory.setText("Other", false);
                            layoutCustomCategory.setVisibility(android.view.View.VISIBLE);
                            etCustomCategory.setText(cat);
                        }
                    }
                    if (amount != null) etAmount.setText(String.valueOf(amount.intValue()));
                    if (period != null) actvPeriod.setText(period, false);
                });
    }

    private void saveBudget() {
        String category = actvCategory.getText().toString().trim();
        if ("Other".equalsIgnoreCase(category)) {
            String customCat = etCustomCategory.getText().toString().trim();
            if (!customCat.isEmpty()) category = customCat;
        }
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
        String period = actvPeriod != null ? actvPeriod.getText().toString().trim() : "MONTHLY";
        if (period.isEmpty()) period = "MONTHLY";
        btnSaveBudget.setEnabled(false);
        btnSaveBudget.setText("Saving...");
        if (editingBudgetId != null) {
            Budget budget = new Budget(editingBudgetId, category, amountLimit, period, System.currentTimeMillis());
            db.collection("users").document(userId).collection("budgets").document(editingBudgetId)
                    .update(budget.toFirestoreMap())
                    .addOnSuccessListener(aVoid -> { Toast.makeText(this, "Budget updated!", Toast.LENGTH_SHORT).show(); finish(); })
                    .addOnFailureListener(e -> { btnSaveBudget.setEnabled(true); btnSaveBudget.setText("Update Budget"); Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); });
        } else {
            String budgetId = db.collection("users").document(userId).collection("budgets").document().getId();
            Budget budget = new Budget(budgetId, category, amountLimit, period, System.currentTimeMillis());
            db.collection("users").document(userId).collection("budgets").document(budgetId)
                    .set(budget.toFirestoreMap())
                    .addOnSuccessListener(aVoid -> { Toast.makeText(this, "Budget saved!", Toast.LENGTH_SHORT).show(); finish(); })
                    .addOnFailureListener(e -> { btnSaveBudget.setEnabled(true); btnSaveBudget.setText("Save Budget"); Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); });
        }
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
