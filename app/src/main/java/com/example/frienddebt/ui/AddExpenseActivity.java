package com.example.frienddebt.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.frienddebt.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AddExpenseActivity extends AppCompatActivity {

    private EditText edtAmount, edtDescription;
    private AutoCompleteTextView edtPayer;
    private EditText edtParticipants; // fallback manual input
    private ChipGroup chipGroupParticipants;
    private TextView btnSelectAll;
    private Button btnSave;

    private String groupId;
    private String ownerId;
    private String expenseId;   // for edit mode

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private List<String> memberNames = new ArrayList<>();
    private boolean allSelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        edtAmount = findViewById(R.id.edtAmount);
        edtPayer = findViewById(R.id.edtPayer);
        edtParticipants = findViewById(R.id.edtParticipants);
        edtDescription = findViewById(R.id.edtDescription);
        chipGroupParticipants = findViewById(R.id.chipGroupParticipants);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnSave = findViewById(R.id.btnSaveExpense);

        // Animate form container
        findViewById(R.id.main).startAnimation(
                AnimationUtils.loadAnimation(this, R.anim.slide_up)
        );

        groupId = getIntent().getStringExtra("GROUP_ID");
        ownerId = getIntent().getStringExtra("OWNER_ID");
        expenseId = getIntent().getStringExtra("EXPENSE_ID"); // null if new

        if (groupId == null || groupId.isEmpty()) {
            Toast.makeText(this, "Group not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // If ownerId not passed, default to current user
        if (ownerId == null) {
            ownerId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        }

        // Load members for dropdown and chips
        loadGroupMembers();

        // Select all toggle
        btnSelectAll.setOnClickListener(v -> {
            allSelected = !allSelected;
            for (int i = 0; i < chipGroupParticipants.getChildCount(); i++) {
                View child = chipGroupParticipants.getChildAt(i);
                if (child instanceof Chip) {
                    ((Chip) child).setChecked(allSelected);
                }
            }
            btnSelectAll.setText(allSelected ? "Deselect All" : "Select All");
        });

        if (expenseId != null) {
            loadExpenseForEdit();
            btnSave.setText("Update Expense");
            TextView title = findViewById(R.id.txtTitle);
            if (title != null) title.setText("Edit Expense");
        }

        btnSave.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_pop));
            saveOrUpdateExpense();
        });
    }

    private void loadGroupMembers() {
        if (ownerId == null) return;

        // Load members from members subcollection
        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(groupId)
                .collection("members")
                .get()
                .addOnSuccessListener(snap -> {
                    memberNames.clear();
                    for (DocumentSnapshot doc : snap) {
                        String name = doc.getString("name");
                        if (name != null && !name.isEmpty()) {
                            memberNames.add(name);
                        }
                    }

                    // Also collect unique names from existing expenses
                    loadNamesFromExpenses();
                })
                .addOnFailureListener(e -> {
                    // Fall back to loading from expenses
                    loadNamesFromExpenses();
                });
    }

    private void loadNamesFromExpenses() {
        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(groupId)
                .collection("expenses")
                .get()
                .addOnSuccessListener(snap -> {
                    Set<String> allNames = new HashSet<>(memberNames);
                    for (DocumentSnapshot doc : snap) {
                        String paidBy = doc.getString("paidBy");
                        if (paidBy != null && !paidBy.isEmpty()) {
                            allNames.add(paidBy);
                        }
                        List<String> participants = (List<String>) doc.get("participants");
                        if (participants != null) {
                            allNames.addAll(participants);
                        }
                    }
                    memberNames = new ArrayList<>(allNames);
                    setupMemberUI();
                })
                .addOnFailureListener(e -> setupMemberUI());
    }

    private void setupMemberUI() {
        if (memberNames.isEmpty()) {
            // Show manual input fallback
            edtParticipants.setVisibility(View.VISIBLE);
            chipGroupParticipants.setVisibility(View.GONE);
            btnSelectAll.setVisibility(View.GONE);

            // Still set up autocomplete for payer
            edtPayer.setThreshold(100); // effectively disable autocomplete
            return;
        }

        // Set up AutoCompleteTextView for payer dropdown
        ArrayAdapter<String> payerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                memberNames
        );
        edtPayer.setAdapter(payerAdapter);
        edtPayer.setThreshold(1);

        // Show dropdown on focus
        edtPayer.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) edtPayer.showDropDown();
        });
        edtPayer.setOnClickListener(v -> edtPayer.showDropDown());

        // Set up participant chips
        chipGroupParticipants.removeAllViews();
        chipGroupParticipants.setVisibility(View.VISIBLE);
        edtParticipants.setVisibility(View.GONE);
        btnSelectAll.setVisibility(View.VISIBLE);

        for (String name : memberNames) {
            Chip chip = new Chip(this);
            chip.setText(name);
            chip.setCheckable(true);
            chip.setChecked(false);
            chip.setChipBackgroundColorResource(R.color.primary_surface);
            chip.setCheckedIconVisible(true);
            chip.setTextColor(getResources().getColor(R.color.text_primary));
            chipGroupParticipants.addView(chip);
        }
    }

    private void loadExpenseForEdit() {
        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(groupId)
                .collection("expenses")
                .document(expenseId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Double amount = doc.getDouble("amount");
                        if (amount != null) edtAmount.setText(String.valueOf(amount));
                        edtPayer.setText(doc.getString("paidBy"));

                        List<String> parts = (List<String>) doc.get("participants");
                        if (parts != null) {
                            // Check chips for participants
                            for (int i = 0; i < chipGroupParticipants.getChildCount(); i++) {
                                View child = chipGroupParticipants.getChildAt(i);
                                if (child instanceof Chip) {
                                    Chip chip = (Chip) child;
                                    chip.setChecked(parts.contains(chip.getText().toString()));
                                }
                            }
                            // Also set fallback text
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
        String desc = edtDescription.getText().toString().trim();

        if (amountStr.isEmpty() || payerName.isEmpty()) {
            Toast.makeText(this, "Enter amount and who paid", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (Exception e) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (amount <= 0) {
            Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show();
            return;
        }

        // Collect participants from chips or manual input
        List<String> participants = new ArrayList<>();

        if (chipGroupParticipants.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < chipGroupParticipants.getChildCount(); i++) {
                View child = chipGroupParticipants.getChildAt(i);
                if (child instanceof Chip && ((Chip) child).isChecked()) {
                    participants.add(((Chip) child).getText().toString());
                }
            }
        }

        // Fall back to manual input if no chips selected
        if (participants.isEmpty()) {
            String participantsStr = edtParticipants.getText().toString().trim();
            if (!participantsStr.isEmpty()) {
                for (String n : participantsStr.split(",")) {
                    String p = n.trim();
                    if (!p.isEmpty()) participants.add(p);
                }
            }
        }

        if (participants.isEmpty()) {
            Toast.makeText(this, "Select at least one participant", Toast.LENGTH_SHORT).show();
            return;
        }

        // Auto-add payer as a member if not in the list
        ensureMemberExists(payerName);
        for (String p : participants) {
            ensureMemberExists(p);
        }

        Map<String, Object> expense = new HashMap<>();
        expense.put("amount", amount);
        expense.put("paidBy", payerName);
        expense.put("participants", participants);
        expense.put("description", desc);
        expense.put("updatedAt", System.currentTimeMillis());

        if (expenseId == null) {
            // ADD NEW
            db.collection("users")
                    .document(ownerId)
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
            // UPDATE EXISTING
            db.collection("users")
                    .document(ownerId)
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

    /**
     * Auto-saves a member name to the group's members subcollection if not already there.
     */
    private void ensureMemberExists(String name) {
        if (ownerId == null || name == null || name.isEmpty()) return;

        String memberDocId = name.trim().toLowerCase().replace(" ", "_");

        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(groupId)
                .collection("members")
                .document(memberDocId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Map<String, Object> member = new HashMap<>();
                        member.put("name", name.trim());
                        member.put("role", "viewer");
                        member.put("addedAt", System.currentTimeMillis());

                        db.collection("users")
                                .document(ownerId)
                                .collection("groups")
                                .document(groupId)
                                .collection("members")
                                .document(memberDocId)
                                .set(member);
                    }
                });
    }
}
