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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.List;

import com.example.frienddebt.utils.StatusBarUtil;

public class BudgetsActivity extends AppCompatActivity {

    private RecyclerView rvBudgets;
    private LinearLayout layoutEmpty;
    private FloatingActionButton fabAddBudget;
    private ImageButton btnBack;

    private BudgetAdapter adapter;
    private List<Budget> budgetList;
    private List<CashbookEntry> userEntries;

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
        userEntries = new ArrayList<>();
        
        adapter = new BudgetAdapter(this, budgetList, new BudgetAdapter.OnBudgetInteractionListener() {
            @Override
            public void onDeleteClick(Budget budget) {
                new MaterialAlertDialogBuilder(BudgetsActivity.this)
                        .setTitle("Delete Budget")
                        .setMessage("Are you sure you want to delete this budget?")
                        .setPositiveButton("Delete", (dialog, which) -> deleteBudget(budget.getId()))
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onEditClick(Budget budget) {
                Intent intent = new Intent(BudgetsActivity.this, AddBudgetActivity.class);
                intent.putExtra("BUDGET_ID", budget.getId());
                startActivity(intent);
            }
        });
        rvBudgets.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchBudgetsAndEntries();
    }

    private void fetchBudgetsAndEntries() {
        loadUserEntriesAndApplyBudgets();
    }

    private void loadUserEntriesAndApplyBudgets() {
        db.collection("cashbooks").whereNotEqualTo("members." + userId, null).get()
                .addOnSuccessListener(memberBooksSnap -> db.collection("cashbooks").whereEqualTo("ownerId", userId).get()
                        .addOnSuccessListener(ownerBooksSnap -> {
                            Set<String> bookIds = new HashSet<>();
                            for (DocumentSnapshot doc : memberBooksSnap.getDocuments()) {
                                bookIds.add(doc.getId());
                            }
                            for (DocumentSnapshot doc : ownerBooksSnap.getDocuments()) {
                                bookIds.add(doc.getId());
                            }

                            if (bookIds.isEmpty()) {
                                userEntries.clear();
                                fetchBudgets();
                                return;
                            }

                            List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>> tasks = new ArrayList<>();
                            for (String bookId : bookIds) {
                                tasks.add(db.collection("cashbooks")
                                        .document(bookId)
                                        .collection("entries")
                                        .get());
                            }

                            com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks)
                                    .addOnSuccessListener(results -> {
                                        userEntries.clear();
                                        for (Object result : results) {
                                            com.google.firebase.firestore.QuerySnapshot entrySnap = (com.google.firebase.firestore.QuerySnapshot) result;
                                            for (DocumentSnapshot entryDoc : entrySnap.getDocuments()) {
                                                CashbookEntry entry = CashbookEntry.fromDocument(entryDoc);
                                                if (entry == null) continue;

                                                String type = entry.getType();
                                                if ("CASH_OUT".equals(type) && userId.equals(entry.getCreatedBy())) {
                                                    userEntries.add(entry);
                                                } else if ("EXPENSE".equals(type) && entry.getSplits() != null && entry.getSplits().containsKey(userId)) {
                                                    Double userShare = entry.getSplits().get(userId);
                                                    if (userShare != null && userShare > 0) {
                                                        CashbookEntry budgetEntry = new CashbookEntry(
                                                                entry.getId(), entry.getBookId(), entry.getDate(),
                                                                entry.getParticulars(), "CASH_OUT", entry.getMedium(),
                                                                userShare, entry.getCategory(), entry.getNote(), entry.getCreatedAt()
                                                        );
                                                        userEntries.add(budgetEntry);
                                                    }
                                                }
                                            }
                                        }
                                        fetchBudgets();
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to load expenses: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Failed to load ledgers: " + e.getMessage(), Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load ledgers: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void fetchBudgets() {
        db.collection("users").document(userId).collection("budgets")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    budgetList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Budget budget = Budget.fromDocument(doc);

                        double spent = calculateSpentForBudget(budget, userEntries);
                        budget.setSpentAmount(spent);
                        
                        budgetList.add(budget);
                    }
                    adapter.notifyDataSetChanged();
                    rvBudgets.scheduleLayoutAnimation();
                    
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

    private double calculateSpentForBudget(Budget budget, List<CashbookEntry> entries) {
        if (budget == null || budget.getCategory() == null || entries == null) return 0.0;

        long windowStart = getBudgetWindowStart(budget);
        double spent = 0.0;
        for (CashbookEntry entry : entries) {
            if (entry == null || entry.getCategory() == null) continue;
            if (entry.getDate() < windowStart) continue;
            if (!budget.getCategory().equalsIgnoreCase(entry.getCategory())) continue;
            spent += entry.getAmount();
        }
        return spent;
    }

    private long getBudgetWindowStart(Budget budget) {
        long createdAt = budget.getCreatedAt() > 0 ? budget.getCreatedAt() : 0L;
        Calendar cal = Calendar.getInstance();
        switch (budget.getPeriod() != null ? budget.getPeriod().toUpperCase(Locale.getDefault()) : "MONTHLY") {
            case "DAILY":
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                break;
            case "WEEKLY":
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                break;
            case "YEARLY":
                cal.set(Calendar.DAY_OF_YEAR, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                break;
            case "MONTHLY":
            default:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                break;
        }
        long windowStart = cal.getTimeInMillis();
        if (createdAt > 0) {
            windowStart = Math.max(windowStart, createdAt);
        }
        return windowStart;
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
