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
    private TextView txtSelectedDate;
    private Button btnSelectDate, btnSaveEntry;
    private ImageButton btnBack;

    private Calendar calendar = Calendar.getInstance();
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String bookId;

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

        // Setup Back Button
        btnBack.setOnClickListener(v -> finish());

        // Setup Category Dropdown
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, CATEGORIES);
        actvCategory.setAdapter(adapter);
        actvCategory.setText(CATEGORIES[CATEGORIES.length - 1], false); // Default to "Other"

        // Setup Date Picker
        updateDateText();
        btnSelectDate.setOnClickListener(v -> showDatePicker());

        // Setup Save
        btnSaveEntry.setOnClickListener(v -> saveEntry());
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
        long createdAt = System.currentTimeMillis();

        String newEntryId = db.collection("cashbooks").document(bookId).collection("entries").document().getId();

        CashbookEntry entry = new CashbookEntry(newEntryId, bookId, dateMs, particulars, type, medium, amount, category, note, createdAt);
        entry.setContactName(contactName);
        entry.setCreatedBy(userId);
        entry.setLastModifiedAt(createdAt);

        btnSaveEntry.setEnabled(false);
        btnSaveEntry.setText("Saving...");

        db.collection("cashbooks").document(bookId)
                .collection("entries").document(newEntryId)
                .set(entry.toFirestoreMap())
                .addOnSuccessListener(aVoid -> {
                    // Write audit log in background
                    if (auth.getCurrentUser() != null) {
                        String actorName = auth.getCurrentUser().getDisplayName();
                        if (actorName == null || actorName.trim().isEmpty()) {
                            actorName = auth.getCurrentUser().getEmail();
                        }
                        if (actorName == null || actorName.trim().isEmpty()) {
                            actorName = "Unknown Member";
                        }
                        String logId = db.collection("cashbooks").document(bookId).collection("logs").document().getId();
                        String logDetails = actorName + " added " + ("CASH_IN".equalsIgnoreCase(type) ? "CASH IN" : "CASH OUT") + " of ₹" + String.format(Locale.getDefault(), "%.2f", amount) + " for \"" + particulars + "\"";

                        com.example.frienddebt.model.AuditLog audit = new com.example.frienddebt.model.AuditLog(
                                logId, bookId, "CREATE", userId, actorName, particulars, amount, type, System.currentTimeMillis(), logDetails
                        );
                        db.collection("cashbooks").document(bookId).collection("logs").document(logId).set(audit.toFirestoreMap());
                    }

                    Toast.makeText(AddCashbookEntryActivity.this, "Transaction saved!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSaveEntry.setEnabled(true);
                    btnSaveEntry.setText("Save Transaction");
                    Toast.makeText(AddCashbookEntryActivity.this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
