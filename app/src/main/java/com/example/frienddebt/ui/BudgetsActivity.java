package com.example.frienddebt.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.adapters.BudgetAdapter;
import com.example.frienddebt.model.Budget;
import com.example.frienddebt.model.CashbookEntry;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.example.frienddebt.utils.StatusBarUtil;

public class BudgetsActivity extends AppCompatActivity {

    private RecyclerView rvBudgets;
    private LinearLayout layoutEmpty;
    private FloatingActionButton fabAddBudget;
    private ImageButton btnBack;

    private BudgetAdapter adapter;
    private List<Budget> budgetList;
    private List<CashbookEntry> currentMonthEntries;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budgets);
        StatusBarUtil.applyStatusBarPadding(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            finish();
            return;
        }
        userId = auth.getCurrentUser().getUid();

        rvBudgets = findViewById(R.id.rvBudgets);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        fabAddBudget = findViewById(R.id.fabAddBudget);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        fabAddBudget.setOnClickListener(v -> startActivity(new Intent(this, AddBudgetActivity.class)));

        budgetList = new ArrayList<>();
        currentMonthEntries = new ArrayList<>();
        
        adapter = new BudgetAdapter(this, budgetList, budget -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Budget")
                    .setMessage("Are you sure you want to delete this budget?")
                    .setPositiveButton("Delete", (dialog, which) -> deleteBudget(budget.getId()))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        rvBudgets.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchBudgetsAndEntries();
    }

    private void fetchBudgetsAndEntries() {
        // 1. Fetch current month's expenses
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long startOfMonth = cal.getTimeInMillis();

        // Use a collectionGroup query to find all entries created by user in this month
        db.collectionGroup("entries")
                .whereEqualTo("createdBy", userId)
                .whereEqualTo("type", "CASH_OUT")
                .whereGreaterThanOrEqualTo("date", startOfMonth)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    currentMonthEntries.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        currentMonthEntries.add(CashbookEntry.fromDocument(doc));
                    }
                    
                    // 2. Fetch budgets
                    fetchBudgets();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load expenses: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void fetchBudgets() {
        db.collection("users").document(userId).collection("budgets")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    budgetList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Budget budget = Budget.fromDocument(doc);
                        
                        // Calculate spent amount
                        double spent = 0;
                        for (CashbookEntry entry : currentMonthEntries) {
                            if (budget.getCategory().equalsIgnoreCase(entry.getCategory())) {
                                spent += entry.getAmount();
                            }
                        }
                        budget.setSpentAmount(spent);
                        
                        budgetList.add(budget);
                    }
                    adapter.notifyDataSetChanged();
                    
                    if (budgetList.isEmpty()) {
                        layoutEmpty.setVisibility(View.VISIBLE);
                        rvBudgets.setVisibility(View.GONE);
                    } else {
                        layoutEmpty.setVisibility(View.GONE);
                        rvBudgets.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load budgets: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void deleteBudget(String budgetId) {
        db.collection("users").document(userId).collection("budgets").document(budgetId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Budget deleted", Toast.LENGTH_SHORT).show();
                    fetchBudgetsAndEntries(); // Refresh
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
