package com.example.frienddebt.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;
import com.example.frienddebt.model.CashbookEntry;
import com.example.frienddebt.model.LedgerBook;
import com.example.frienddebt.utils.StatusBarUtil;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AddSharedExpenseActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private Spinner spinnerLedger, spinnerPaidBy;
    private TextInputEditText etTitle, etAmount;
    private RadioGroup rgSplitMethod;
    private RadioButton rbEqually, rbExact;
    private LinearLayout containerSplits;
    private Button btnSaveExpense;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String currentUserId;

    private List<LedgerBook> sharedLedgers = new ArrayList<>();
    private LedgerBook selectedLedger;
    private List<String> memberIds = new ArrayList<>();
    private List<String> memberNames = new ArrayList<>();
    
    // Map of UserID to Exact Amount input field
    private Map<String, TextInputEditText> exactAmountInputs = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_shared_expense);
        StatusBarUtil.applyStatusBarPadding(this);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
        }

        btnBack = findViewById(R.id.btnBack);
        spinnerLedger = findViewById(R.id.spinnerLedger);
        spinnerPaidBy = findViewById(R.id.spinnerPaidBy);
        etTitle = findViewById(R.id.etTitle);
        etAmount = findViewById(R.id.etAmount);
        rgSplitMethod = findViewById(R.id.rgSplitMethod);
        rbEqually = findViewById(R.id.rbEqually);
        rbExact = findViewById(R.id.rbExact);
        containerSplits = findViewById(R.id.containerSplits);
        btnSaveExpense = findViewById(R.id.btnSaveExpense);

        btnBack.setOnClickListener(v -> finish());

        rgSplitMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbEqually) {
                containerSplits.setVisibility(View.GONE);
            } else if (checkedId == R.id.rbExact) {
                containerSplits.setVisibility(View.VISIBLE);
                buildExactAmountUI();
            }
        });
        
        etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                // If in EQUAL mode, we might want to show a live preview, but for now just wait for save.
            }
        });

        btnSaveExpense.setOnClickListener(v -> saveExpense());

        loadSharedLedgers();
    }

    private void loadSharedLedgers() {
        if (currentUserId == null) return;

        db.collection("cashbooks")
                .whereNotEqualTo("members." + currentUserId, null)
                .get()
                .addOnSuccessListener(snapshots -> {
                    sharedLedgers.clear();
                    List<String> ledgerNames = new ArrayList<>();
                    
                    for (DocumentSnapshot doc : snapshots) {
                        LedgerBook book = LedgerBook.fromDocument(doc);
                        // Only add ledgers with > 1 member
                        if (book.getMembers() != null && book.getMembers().size() > 1) {
                            sharedLedgers.add(book);
                            ledgerNames.add(book.getName());
                        }
                    }

                    if (sharedLedgers.isEmpty()) {
                        Toast.makeText(this, "You need to join or create a shared group first.", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ledgerNames);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerLedger.setAdapter(adapter);

                    spinnerLedger.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            selectedLedger = sharedLedgers.get(position);
                            loadMembersForLedger(selectedLedger);
                        }
                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });
                });
    }

    private void loadMembersForLedger(LedgerBook ledger) {
        memberIds.clear();
        memberNames.clear();
        exactAmountInputs.clear();
        containerSplits.removeAllViews();

        if (ledger.getMembers() != null) {
            for (Map.Entry<String, String> entry : ledger.getMembers().entrySet()) {
                memberIds.add(entry.getKey());
                // In a real app we would resolve IDs to Display Names from a "users" collection.
                // For now, we will just use the role or a shortened ID if it's not the current user.
                String displayName = entry.getKey().equals(currentUserId) ? "Me" : "User (" + entry.getKey().substring(0, 4) + ")";
                memberNames.add(displayName);
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, memberNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPaidBy.setAdapter(adapter);
        
        // Select 'Me' by default if present
        int myIndex = memberIds.indexOf(currentUserId);
        if (myIndex != -1) {
            spinnerPaidBy.setSelection(myIndex);
        }

        if (rbExact.isChecked()) {
            buildExactAmountUI();
        }
    }

    private void buildExactAmountUI() {
        containerSplits.removeAllViews();
        exactAmountInputs.clear();

        for (int i = 0; i < memberIds.size(); i++) {
            String uId = memberIds.get(i);
            String uName = memberNames.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 16, 0, 16);

            TextView txtName = new TextView(this);
            txtName.setText(uName);
            txtName.setTextSize(16f);
            txtName.setTextColor(getResources().getColor(R.color.text_primary));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            txtName.setLayoutParams(nameParams);

            TextInputEditText etSplitAmount = new TextInputEditText(this);
            etSplitAmount.setHint("0.00");
            etSplitAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dpToPx(120), LinearLayout.LayoutParams.WRAP_CONTENT);
            etSplitAmount.setLayoutParams(inputParams);

            row.addView(txtName);
            row.addView(etSplitAmount);
            containerSplits.addView(row);

            exactAmountInputs.put(uId, etSplitAmount);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private void saveExpense() {
        if (selectedLedger == null) return;

        String title = etTitle.getText().toString().trim();
        String amountStr = etAmount.getText().toString().trim();

        if (title.isEmpty() || amountStr.isEmpty()) {
            Toast.makeText(this, "Please enter title and amount", Toast.LENGTH_SHORT).show();
            return;
        }

        double totalAmount = 0;
        try {
            totalAmount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        int payerIndex = spinnerPaidBy.getSelectedItemPosition();
        String payerId = memberIds.get(payerIndex);

        Map<String, Double> splits = new HashMap<>();

        if (rbEqually.isChecked()) {
            double splitAmount = totalAmount / memberIds.size();
            for (String uid : memberIds) {
                splits.put(uid, splitAmount);
            }
        } else {
            double sum = 0;
            for (Map.Entry<String, TextInputEditText> entry : exactAmountInputs.entrySet()) {
                String val = entry.getValue().getText().toString();
                if (!val.isEmpty()) {
                    try {
                        double amt = Double.parseDouble(val);
                        splits.put(entry.getKey(), amt);
                        sum += amt;
                    } catch (Exception ignored) {}
                }
            }
            if (Math.abs(sum - totalAmount) > 0.01) {
                Toast.makeText(this, "Split amounts must equal total amount!", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        btnSaveExpense.setEnabled(false);

        String entryId = UUID.randomUUID().toString();
        CashbookEntry entry = new CashbookEntry(
                entryId,
                selectedLedger.getId(),
                System.currentTimeMillis(),
                title,
                "EXPENSE",
                "CASH",
                totalAmount,
                "Shared",
                "",
                System.currentTimeMillis()
        );
        entry.setCreatedBy(currentUserId);
        entry.setPaidBy(payerId);
        entry.setSplitMethod(rbEqually.isChecked() ? "EQUAL" : "EXACT");
        entry.setParticipants(memberIds);
        entry.setSplits(splits);

        db.collection("cashbooks").document(selectedLedger.getId())
                .collection("entries").document(entryId)
                .set(entry.toFirestoreMap())
                .addOnSuccessListener(aVoid -> {
                    // Update ledger totals (this is a simplified logic. Real split logic updates member balances)
                    updateLedgerTotals(selectedLedger.getId(), totalAmount);
                })
                .addOnFailureListener(e -> {
                    btnSaveExpense.setEnabled(true);
                    Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateLedgerTotals(String bookId, double amount) {
        db.collection("cashbooks").document(bookId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        LedgerBook book = LedgerBook.fromDocument(doc);
                        double newTotal = book.getTotalCashOut() + amount;
                        db.collection("cashbooks").document(bookId)
                                .update("totalCashOut", newTotal)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Expense added", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                    }
                });
    }
}
