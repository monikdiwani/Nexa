package com.example.frienddebt.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;
import com.example.frienddebt.model.CashbookEntry;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import com.example.frienddebt.utils.StatusBarUtil;

public class AddCashbookEntryActivity extends AppCompatActivity {

    private EditText etParticulars, etContactName, etAmount, etNote;
    private RadioGroup rgType, rgMedium;
    private AutoCompleteTextView actvCategory;
    private com.google.android.material.textfield.TextInputLayout layoutCustomCategory;
    private EditText etCustomCategory;
    private TextView txtSelectedDate;
    private Button btnSelectDate, btnSaveEntry, btnScanReceipt;
    private ImageButton btnBack;
    
    private com.google.android.material.materialswitch.MaterialSwitch switchRecurring;
    private com.google.android.material.textfield.TextInputLayout layoutRecurringFrequency;
    private AutoCompleteTextView actvRecurringFrequency;

    private Calendar calendar = Calendar.getInstance();
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String bookId;
    private String entryId;
    private boolean isEditMode = false;
    private boolean isDuplicateMode = false;
    private CashbookEntry originalEntry;

    private static final String[] CATEGORIES = {
            "Sales", "Rent", "Salary", "Office", "Personal", "Food", "Transport", "Shopping", "Bills", "Other"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_cashbook_entry);
        StatusBarUtil.applyStatusBarPadding(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        bookId = getIntent().getStringExtra("BOOK_ID");
        entryId = getIntent().getStringExtra("ENTRY_ID");
        isEditMode = getIntent().getBooleanExtra("IS_EDIT_MODE", false);
        isDuplicateMode = getIntent().getBooleanExtra("IS_DUPLICATE_MODE", false);
        
        if (bookId == null) {
            Toast.makeText(this, "Book ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Bind Views
        etParticulars = findViewById(R.id.etParticulars);
        etContactName = findViewById(R.id.etContactName);
        etAmount = findViewById(R.id.etAmount);
        etNote = findViewById(R.id.etNote);
        rgType = findViewById(R.id.rgType);
        rgMedium = findViewById(R.id.rgMedium);
        actvCategory = findViewById(R.id.actvCategory);
        txtSelectedDate = findViewById(R.id.txtSelectedDate);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        btnSaveEntry = findViewById(R.id.btnSaveEntry);
        btnBack = findViewById(R.id.btnBack);

        btnScanReceipt = findViewById(R.id.btnScanReceipt);

        // Setup Back Button
        btnBack.setOnClickListener(v -> finish());

        // Setup Category Dropdown
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, CATEGORIES);
        actvCategory.setAdapter(adapter);
        actvCategory.setText(CATEGORIES[CATEGORIES.length - 1], false); // Default to "Other"
        
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
        
        // Ensure initial state
        if ("Other".equalsIgnoreCase(actvCategory.getText().toString())) {
            layoutCustomCategory.setVisibility(android.view.View.VISIBLE);
        }
        
        switchRecurring = findViewById(R.id.switchRecurring);
        layoutRecurringFrequency = findViewById(R.id.layoutRecurringFrequency);
        actvRecurringFrequency = findViewById(R.id.actvRecurringFrequency);

        switchRecurring.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutRecurringFrequency.setVisibility(isChecked ? android.view.View.VISIBLE : android.view.View.GONE);
        });

        String[] frequencies = {"DAILY", "WEEKLY", "MONTHLY", "YEARLY"};
        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, frequencies);
        actvRecurringFrequency.setAdapter(freqAdapter);
        actvRecurringFrequency.setText("MONTHLY", false);

        // Setup Date Picker
        updateDateText();
        btnSelectDate.setOnClickListener(v -> showDatePicker());

        // Setup Scan Receipt
        androidx.activity.result.ActivityResultLauncher<String> mGetContent = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        android.app.ProgressDialog pd = new android.app.ProgressDialog(this);
                        pd.setMessage("Scanning Receipt...");
                        pd.setCancelable(false);
                        pd.show();
                        
                        com.example.frienddebt.utils.ReceiptScanner.scanReceipt(this, uri, new com.example.frienddebt.utils.ReceiptScanner.ScanCallback() {
                            @Override
                            public void onSuccess(String vendorName, double totalAmount) {
                                pd.dismiss();
                                etParticulars.setText(vendorName);
                                if (totalAmount > 0) {
                                    etAmount.setText(String.format(Locale.getDefault(), "%.2f", totalAmount));
                                    rgType.check(R.id.rbCashOut); // usually receipts are expenses
                                }
                                Toast.makeText(AddCashbookEntryActivity.this, "Scan complete!", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onFailure(Exception e) {
                                pd.dismiss();
                                Toast.makeText(AddCashbookEntryActivity.this, "Scan failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });

        btnScanReceipt.setOnClickListener(v -> mGetContent.launch("image/*"));

        // Setup Save
        btnSaveEntry.setOnClickListener(v -> saveEntry());

        // Load existing entry if needed
        if ((isEditMode || isDuplicateMode) && entryId != null) {
            if (isEditMode) {
                ((TextView) findViewById(R.id.txtTitle)).setText("Edit Transaction");
                btnSaveEntry.setText("Update Transaction");
            } else {
                ((TextView) findViewById(R.id.txtTitle)).setText("Duplicate Transaction");
            }
            loadExistingEntry();
        }
    }

    private void loadExistingEntry() {
        db.collection("cashbooks").document(bookId).collection("entries").document(entryId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    originalEntry = CashbookEntry.fromDocument(doc);
                    populateUI(originalEntry);
                }
            })
            .addOnFailureListener(e -> Toast.makeText(this, "Failed to load entry", Toast.LENGTH_SHORT).show());
    }

    private void populateUI(CashbookEntry entry) {
        etParticulars.setText(entry.getParticulars());
        if (entry.getContactName() != null) etContactName.setText(entry.getContactName());
        etAmount.setText(String.valueOf(entry.getAmount()));
        if (entry.getCategory() != null) {
            boolean found = false;
            for (String c : CATEGORIES) {
                if (c.equalsIgnoreCase(entry.getCategory())) {
                    found = true; break;
                }
            }
            if (!found) {
                actvCategory.setText("Other", false);
                layoutCustomCategory.setVisibility(android.view.View.VISIBLE);
                etCustomCategory.setText(entry.getCategory());
            } else {
                actvCategory.setText(entry.getCategory(), false);
                layoutCustomCategory.setVisibility(android.view.View.GONE);
            }
        }
        if (entry.getNote() != null) etNote.setText(entry.getNote());
        
        if ("CASH_IN".equals(entry.getType())) rgType.check(R.id.rbCashIn);
        else rgType.check(R.id.rbCashOut);
        
        if ("BANK".equals(entry.getMedium())) rgMedium.check(R.id.rbMediumBank);
        else rgMedium.check(R.id.rbMediumCash);
        
        if (!isDuplicateMode) {
            calendar.setTimeInMillis(entry.getDate());
            updateDateText();
        }
        
        if (entry.isRecurring()) {
            switchRecurring.setChecked(true);
            if (entry.getRecurringPattern() != null) actvRecurringFrequency.setText(entry.getRecurringPattern(), false);
        }
    }

    private void updateDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        txtSelectedDate.setText("Date: " + sdf.format(calendar.getTime()));
    }

    private void showDatePicker() {
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateText();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void saveEntry() {
        String particulars = etParticulars.getText().toString().trim();
        String contactName = etContactName.getText().toString().trim();
        String amountStr = etAmount.getText().toString().trim();
        String category = actvCategory.getText().toString().trim();
        if ("Other".equalsIgnoreCase(category)) {
            String customCat = etCustomCategory.getText().toString().trim();
            if (!customCat.isEmpty()) {
                category = customCat;
            }
        }
        String note = etNote.getText().toString().trim();

        if (particulars.isEmpty()) {
            etParticulars.setError("Particulars are required");
            etParticulars.requestFocus();
            return;
        }

        if (amountStr.isEmpty()) {
            etAmount.setError("Amount is required");
            etAmount.requestFocus();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                etAmount.setError("Amount must be greater than zero");
                etAmount.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            etAmount.setError("Invalid amount format");
            etAmount.requestFocus();
            return;
        }

        String type = rgType.getCheckedRadioButtonId() == R.id.rbCashIn ? "CASH_IN" : "CASH_OUT";
        String medium = rgMedium.getCheckedRadioButtonId() == R.id.rbMediumCash ? "CASH" : "BANK";

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        long dateMs = calendar.getTimeInMillis();
        long createdAt = isEditMode ? (originalEntry != null ? originalEntry.getCreatedAt() : System.currentTimeMillis()) : System.currentTimeMillis();

        String targetEntryId = isEditMode ? entryId : db.collection("cashbooks").document(bookId).collection("entries").document().getId();

        CashbookEntry entry = new CashbookEntry(targetEntryId, bookId, dateMs, particulars, type, medium, amount, category, note, createdAt);
        entry.setContactName(contactName);
        entry.setCreatedBy(userId);
        entry.setLastModifiedAt(createdAt);
        
        if (switchRecurring.isChecked()) {
            entry.setRecurring(true);
            entry.setRecurringPattern(actvRecurringFrequency.getText().toString());
            entry.setRecurringId(java.util.UUID.randomUUID().toString());
            
            // Calculate next occurrence
            Calendar nextCal = Calendar.getInstance();
            nextCal.setTimeInMillis(dateMs);
            switch (entry.getRecurringPattern()) {
                case "DAILY": nextCal.add(Calendar.DAY_OF_YEAR, 1); break;
                case "WEEKLY": nextCal.add(Calendar.WEEK_OF_YEAR, 1); break;
                case "MONTHLY": nextCal.add(Calendar.MONTH, 1); break;
                case "YEARLY": nextCal.add(Calendar.YEAR, 1); break;
            }
            entry.setNextOccurrence(nextCal.getTimeInMillis());
        }

        btnSaveEntry.setEnabled(false);
        btnSaveEntry.setText("Saving...");

        db.collection("cashbooks").document(bookId)
                .collection("entries").document(targetEntryId)
                .set(entry.toFirestoreMap())
                .addOnSuccessListener(aVoid -> {
                    // Write audit log in background
                    if (auth.getCurrentUser() != null) {
                        String actorName = auth.getCurrentUser().getDisplayName();
                        if (actorName == null || actorName.trim().isEmpty()) actorName = auth.getCurrentUser().getEmail();
                        if (actorName == null || actorName.trim().isEmpty()) actorName = "Unknown Member";
                        
                        String action = isEditMode ? "EDIT" : "CREATE";
                        String verb = isEditMode ? "updated" : (isDuplicateMode ? "duplicated" : "added");
                        
                        String logId = db.collection("cashbooks").document(bookId).collection("logs").document().getId();
                        String logDetails = actorName + " " + verb + " " + ("CASH_IN".equalsIgnoreCase(type) ? "CASH IN" : "CASH OUT") + " of ₹" + String.format(Locale.getDefault(), "%.2f", amount) + " for \"" + particulars + "\"";

                        com.example.frienddebt.model.AuditLog audit = new com.example.frienddebt.model.AuditLog(
                                logId, bookId, action, userId, actorName, particulars, amount, type, System.currentTimeMillis(), logDetails
                        );
                        db.collection("cashbooks").document(bookId).collection("logs").document(logId).set(audit.toFirestoreMap())
                                .addOnCompleteListener(task -> {
                                    Toast.makeText(AddCashbookEntryActivity.this, "Transaction saved!", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                    } else {
                        Toast.makeText(AddCashbookEntryActivity.this, "Transaction saved!", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    btnSaveEntry.setEnabled(true);
                    btnSaveEntry.setText("Save Transaction");
                    Toast.makeText(AddCashbookEntryActivity.this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
