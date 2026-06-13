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
import com.example.frienddebt.utils.UserProfileHelper;

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
    private RadioButton rbEqually, rbExact, rbPercent, rbShares;
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
    private Map<String, android.widget.CheckBox> optOutCheckboxes = new HashMap<>();
    
    private boolean isEditMode = false;
    private boolean isDuplicateMode = false;
    private String entryId = null;
    private com.example.frienddebt.model.CashbookEntry existingEntry = null;

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
        rbPercent = findViewById(R.id.rbPercent);
        rbShares = findViewById(R.id.rbShares);
        containerSplits = findViewById(R.id.containerSplits);
        btnSaveExpense = findViewById(R.id.btnSaveExpense);

        btnBack.setOnClickListener(v -> finish());

        isEditMode = getIntent().getBooleanExtra("IS_EDIT_MODE", false);
        isDuplicateMode = getIntent().getBooleanExtra("IS_DUPLICATE_MODE", false);
        entryId = getIntent().getStringExtra("ENTRY_ID");

        if (isEditMode) {
            btnSaveExpense.setText("Update Expense");
            android.widget.TextView title = findViewById(R.id.txtHeaderTitle);
            if (title != null) title.setText("Edit Shared Expense");
        }

        rgSplitMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbEqually) {
                containerSplits.setVisibility(View.GONE);
            } else {
                containerSplits.setVisibility(View.VISIBLE);
                buildDynamicSplitUI(checkedId);
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

                    String preselectedBookId = getIntent().getStringExtra("BOOK_ID");
                    int preselectedIndex = -1;

                    if (preselectedBookId != null) {
                        for (int i = 0; i < sharedLedgers.size(); i++) {
                            if (sharedLedgers.get(i).getId().equals(preselectedBookId)) {
                                preselectedIndex = i;
                                break;
                            }
                        }
                    }

                    if (preselectedIndex != -1) {
                        spinnerLedger.setSelection(preselectedIndex);
                        spinnerLedger.setEnabled(false); // Lock it so they can't change it if launched from a specific group
                        selectedLedger = sharedLedgers.get(preselectedIndex);
                        loadMembersForLedger(selectedLedger);
                    }

                    spinnerLedger.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (preselectedBookId != null) return; // Prevent double-loading
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

        if (ledger.getMembers() == null || ledger.getMembers().isEmpty()) return;

        List<String> uids = new ArrayList<>(ledger.getMembers().keySet());

        // Batch-fetch real display names before building the UI
        UserProfileHelper.resolveNames(com.google.firebase.firestore.FirebaseFirestore.getInstance(), uids, nameMap -> {
            memberIds.clear();
            memberNames.clear();
            for (String uid : uids) {
                memberIds.add(uid);
                String name = uid.equals(currentUserId) ? "Me" : nameMap.getOrDefault(uid, "User");
                memberNames.add(name);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, memberNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerPaidBy.setAdapter(adapter);

            // Select 'Me' by default if present
            int myIndex = memberIds.indexOf(currentUserId);
            if (myIndex != -1) spinnerPaidBy.setSelection(myIndex);

            if (!rbEqually.isChecked() || isEditMode || isDuplicateMode) {
                buildDynamicSplitUI(rgSplitMethod.getCheckedRadioButtonId());
                if (entryId != null && (isEditMode || isDuplicateMode)) {
                    loadEntryDetails();
                }
            } else {
                buildDynamicSplitUI(R.id.rbEqually);
            }
        });
    }

    private void buildDynamicSplitUI(int checkedId) {
        containerSplits.removeAllViews();
        exactAmountInputs.clear();
        optOutCheckboxes.clear();

        boolean showInput = (checkedId != R.id.rbEqually);

        String hint = "0.00";
        if (checkedId == R.id.rbPercent) hint = "0 %";
        else if (checkedId == R.id.rbShares) hint = "0 Shares";

        for (int i = 0; i < memberIds.size(); i++) {
            String uId = memberIds.get(i);
            String uName = memberNames.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 16, 0, 16);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            android.widget.CheckBox cbOpt = new android.widget.CheckBox(this);
            cbOpt.setChecked(true); // By default, everyone is included
            cbOpt.setButtonTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.primary)));
            
            TextView txtName = new TextView(this);
            txtName.setText(uName);
            txtName.setTextSize(16f);
            txtName.setTextColor(getResources().getColor(R.color.text_primary));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            txtName.setLayoutParams(nameParams);

            TextInputEditText etSplitInput = new TextInputEditText(this);
            etSplitInput.setHint(hint);
            etSplitInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dpToPx(120), LinearLayout.LayoutParams.WRAP_CONTENT);
            etSplitInput.setLayoutParams(inputParams);
            etSplitInput.setVisibility(showInput ? View.VISIBLE : View.GONE);

            cbOpt.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (showInput) {
                    etSplitInput.setEnabled(isChecked);
                    if (!isChecked) etSplitInput.setText("");
                }
            });

            row.addView(cbOpt);
            row.addView(txtName);
            row.addView(etSplitInput);
            containerSplits.addView(row);

            exactAmountInputs.put(uId, etSplitInput);
            optOutCheckboxes.put(uId, cbOpt);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private void loadEntryDetails() {
        if (selectedLedger == null || entryId == null) return;
        db.collection("cashbooks").document(selectedLedger.getId()).collection("entries").document(entryId)
            .get().addOnSuccessListener(doc -> {
                if (!doc.exists()) return;
                existingEntry = com.example.frienddebt.model.CashbookEntry.fromDocument(doc);
                etTitle.setText(existingEntry.getCategory());
                etAmount.setText(String.valueOf(existingEntry.getAmount()));
                
                // Set Paid By
                int payerIdx = memberIds.indexOf(existingEntry.getPaidBy());
                if (payerIdx != -1) spinnerPaidBy.setSelection(payerIdx);

                // Load Splits
                Map<String, Double> splits = existingEntry.getSplits();
                if (splits != null && !splits.isEmpty()) {
                    boolean isEqual = true;
                    // Check if it was equally split
                    double expectedSplit = existingEntry.getAmount() / splits.size();
                    for (Double amt : splits.values()) {
                        if (Math.abs(amt - expectedSplit) > 0.05) { isEqual = false; break; }
                    }

                    if (isEqual && "EQUALLY".equals(existingEntry.getSplitMethod())) {
                        rbEqually.setChecked(true);
                        // Uncheck anyone not in the splits
                        for (String uId : memberIds) {
                            android.widget.CheckBox cb = optOutCheckboxes.get(uId);
                            if (cb != null) {
                                cb.setChecked(splits.containsKey(uId));
                            }
                        }
                    } else {
                        if ("PERCENT".equals(existingEntry.getSplitMethod())) rbPercent.setChecked(true);
                        else if ("SHARES".equals(existingEntry.getSplitMethod())) rbShares.setChecked(true);
                        else rbExact.setChecked(true);
                        
                        // Wait for RadioButton listener to rebuild UI if needed
                        // Populate exact values or percentages
                        for (String uId : memberIds) {
                            android.widget.CheckBox cb = optOutCheckboxes.get(uId);
                            TextInputEditText input = exactAmountInputs.get(uId);
                            if (splits.containsKey(uId)) {
                                if (cb != null) cb.setChecked(true);
                                if (input != null && input.getVisibility() == View.VISIBLE) {
                                    // If we stored original percentages or shares, we'd load those. But we only store exact double amounts in the model.
                                    // We will just show the exact amounts and force exact mode if we don't have the ratio.
                                    // To keep it simple, we just write the amount.
                                    input.setText(String.format(java.util.Locale.getDefault(), "%.2f", splits.get(uId)));
                                }
                            } else {
                                if (cb != null) cb.setChecked(false);
                            }
                        }
                    }
                }
            });
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
            List<String> optedInMembers = new ArrayList<>();
            for (String uid : memberIds) {
                android.widget.CheckBox cb = optOutCheckboxes.get(uid);
                if (cb != null && cb.isChecked()) {
                    optedInMembers.add(uid);
                }
            }
            if (optedInMembers.isEmpty()) {
                Toast.makeText(this, "At least one person must be included in the split", Toast.LENGTH_SHORT).show();
                return;
            }
            double splitAmount = totalAmount / optedInMembers.size();
            for (String uid : optedInMembers) {
                splits.put(uid, splitAmount);
            }
        } else {
            double sumInput = 0;
            Map<String, Double> inputVals = new HashMap<>();
            for (Map.Entry<String, TextInputEditText> entry : exactAmountInputs.entrySet()) {
                String uId = entry.getKey();
                android.widget.CheckBox cb = optOutCheckboxes.get(uId);
                if (cb == null || !cb.isChecked()) continue; // Skip opted-out members
                
                String val = entry.getValue().getText().toString();
                if (!val.isEmpty()) {
                    try {
                        double valDouble = Double.parseDouble(val);
                        if (valDouble < 0) {
                            Toast.makeText(this, "Negative values are not allowed", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        inputVals.put(uId, valDouble);
                        sumInput += valDouble;
                    } catch (Exception ignored) {}
                }
            }

            if (rbExact.isChecked()) {
                if (Math.abs(sumInput - totalAmount) > 0.01) {
                    Toast.makeText(this, "Exact amounts must equal total amount!", Toast.LENGTH_SHORT).show();
                    return;
                }
                splits.putAll(inputVals);
            } else if (rbPercent.isChecked()) {
                if (Math.abs(sumInput - 100.0) > 0.01) {
                    Toast.makeText(this, "Percentages must add up to 100!", Toast.LENGTH_SHORT).show();
                    return;
                }
                for (Map.Entry<String, Double> entry : inputVals.entrySet()) {
                    splits.put(entry.getKey(), totalAmount * (entry.getValue() / 100.0));
                }
            } else if (rbShares.isChecked()) {
                if (sumInput <= 0) {
                    Toast.makeText(this, "Total shares must be > 0!", Toast.LENGTH_SHORT).show();
                    return;
                }
                for (Map.Entry<String, Double> entry : inputVals.entrySet()) {
                    splits.put(entry.getKey(), totalAmount * (entry.getValue() / sumInput));
                }
            }
        }

        if (splits.isEmpty()) {
            Toast.makeText(this, "Split cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSaveExpense.setEnabled(false);

        String newEntryId = (this.entryId != null && isEditMode) ? this.entryId : java.util.UUID.randomUUID().toString();
        long date = (existingEntry != null && isEditMode) ? existingEntry.getDate() : System.currentTimeMillis();
        long createdAt = (existingEntry != null && isEditMode) ? existingEntry.getCreatedAt() : System.currentTimeMillis();
        String createdBy = (existingEntry != null && isEditMode) ? existingEntry.getCreatedBy() : currentUserId;
        String createdByName = (existingEntry != null && isEditMode) ? existingEntry.getCreatedByName() : "";

        com.example.frienddebt.model.CashbookEntry entry = new com.example.frienddebt.model.CashbookEntry(
                newEntryId,
                selectedLedger.getId(),
                date,
                title,
                "EXPENSE",
                "CASH",
                totalAmount,
                "Shared",
                "",
                createdAt
        );
        entry.setCreatedBy(createdBy);
        entry.setCreatedByName(createdByName);
        entry.setPaidBy(payerId);
        String splitMethodStr = "EQUALLY";
        if (rbExact.isChecked()) splitMethodStr = "EXACT";
        else if (rbPercent.isChecked()) splitMethodStr = "PERCENT";
        else if (rbShares.isChecked()) splitMethodStr = "SHARES";
        entry.setSplitMethod(splitMethodStr);
        entry.setParticipants(memberIds);
        entry.setSplits(splits);

        final double finalTotalAmount = totalAmount;
        final double oldAmount = (existingEntry != null && isEditMode) ? existingEntry.getAmount() : 0.0;

        db.collection("cashbooks").document(selectedLedger.getId())
                .collection("entries").document(newEntryId)
                .set(entry.toFirestoreMap())
                .addOnSuccessListener(aVoid -> {
                    updateLedgerTotals(selectedLedger.getId(), oldAmount, finalTotalAmount);
                })
                .addOnFailureListener(e -> {
                    btnSaveExpense.setEnabled(true);
                    Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateLedgerTotals(String bookId, double oldAmount, double newAmount) {
        if (Math.abs(oldAmount - newAmount) < 0.01) {
            Toast.makeText(this, isEditMode ? "Expense updated" : "Expense added", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        db.collection("cashbooks").document(bookId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        com.example.frienddebt.model.LedgerBook book = com.example.frienddebt.model.LedgerBook.fromDocument(doc);
                        double newTotal = book.getTotalCashOut() - oldAmount + newAmount;
                        db.collection("cashbooks").document(bookId)
                                .update("totalCashOut", newTotal)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, isEditMode ? "Expense updated" : "Expense added", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                    }
                });
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
