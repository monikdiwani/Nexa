package com.example.frienddebt.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.dsa.CashbookCalculator;
import com.example.frienddebt.model.CashbookEntry;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LedgerBookDetailActivity extends AppCompatActivity {

    private TextView txtBookTitle, txtCashBalance, txtBankBalance, txtEmptyCashbook;
    private TextView chipAll, chipCash, chipBank, chipToday, chipWeek, chipMonth;
    private RecyclerView rvCashbookEntries;
    private FloatingActionButton fabAddEntry;
    private ImageButton btnBack, btnBookSettings;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration entriesListener;

    private List<CashbookEntry> allEntries = new ArrayList<>();
    private List<CashbookEntry> filteredEntries = new ArrayList<>();
    private LedgerEntryAdapter adapter;

    private String activeFilter = "ALL";
    private String bookId;
    private String bookName;
    private String userRole;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ledger_book_detail);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        bookId = getIntent().getStringExtra("BOOK_ID");
        bookName = getIntent().getStringExtra("BOOK_NAME");
        userRole = getIntent().getStringExtra("USER_ROLE");

        if (bookId == null) {
            Toast.makeText(this, "Book ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        txtBookTitle = findViewById(R.id.txtBookTitle);
        txtCashBalance = findViewById(R.id.txtCashBalance);
        txtBankBalance = findViewById(R.id.txtBankBalance);
        txtEmptyCashbook = findViewById(R.id.txtEmptyCashbook);
        rvCashbookEntries = findViewById(R.id.rvCashbookEntries);
        fabAddEntry = findViewById(R.id.fabAddEntry);
        btnBack = findViewById(R.id.btnBack);
        btnBookSettings = findViewById(R.id.btnBookSettings);

        chipAll = findViewById(R.id.chipAll);
        chipCash = findViewById(R.id.chipCash);
        chipBank = findViewById(R.id.chipBank);
        chipToday = findViewById(R.id.chipToday);
        chipWeek = findViewById(R.id.chipWeek);
        chipMonth = findViewById(R.id.chipMonth);

        txtBookTitle.setText(bookName != null ? bookName : "Ledger Book");

        rvCashbookEntries.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LedgerEntryAdapter(filteredEntries);
        rvCashbookEntries.setAdapter(adapter);

        setupFilters();

        btnBack.setOnClickListener(v -> finish());
        
        btnBookSettings.setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, v);
            popup.getMenu().add(0, 1, 0, "Cash Counter");
            popup.getMenu().add(0, 2, 0, "Export PDF Report");
            popup.getMenu().add(0, 3, 0, "Share Invite Code");
            
            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        Intent ccIntent = new Intent(this, CashCounterActivity.class);
                        // We use the last calculated net balance
                        double currentNetBalance = 0;
                        try {
                            String balStr = txtCashBalance.getText().toString().replace("₹", "").replace(",", "");
                            String bankBalStr = txtBankBalance.getText().toString().replace("₹", "").replace(",", "");
                            currentNetBalance = Double.parseDouble(balStr) - Double.parseDouble(bankBalStr);
                        } catch(Exception e) {}
                        ccIntent.putExtra("APP_BALANCE", currentNetBalance);
                        startActivity(ccIntent);
                        return true;
                    case 2:
                        if (allEntries.isEmpty()) {
                            Toast.makeText(this, "No entries to export", Toast.LENGTH_SHORT).show();
                            return true;
                        }
                        android.net.Uri pdfUri = com.example.frienddebt.utils.ReportGenerator.generatePdfReport(this, bookName, allEntries);
                        if (pdfUri != null) {
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("application/pdf");
                            shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(shareIntent, "Share Cashbook Report"));
                        } else {
                            Toast.makeText(this, "Failed to generate PDF", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    case 3:
                        db.collection("cashbooks").document(bookId).get()
                            .addOnSuccessListener(doc -> {
                                String code = doc.getString("inviteCode");
                                if (code != null && !code.isEmpty()) {
                                    Intent shareCodeIntent = new Intent(Intent.ACTION_SEND);
                                    shareCodeIntent.setType("text/plain");
                                    shareCodeIntent.putExtra(Intent.EXTRA_TEXT, "Join my Ledger on Nexa! Invite Code: " + code);
                                    startActivity(Intent.createChooser(shareCodeIntent, "Share Invite Code"));
                                } else {
                                    Toast.makeText(LedgerBookDetailActivity.this, "No invite code found for this ledger.", Toast.LENGTH_SHORT).show();
                                }
                            });
                        return true;
                }
                return false;
            });
            popup.show();
        });

        // Role check
        if ("VIEWER".equalsIgnoreCase(userRole)) {
            fabAddEntry.setVisibility(View.GONE);
        } else {
            fabAddEntry.setOnClickListener(v -> {
                Animation pop = AnimationUtils.loadAnimation(this, R.anim.button_pop);
                v.startAnimation(pop);
                Intent intent = new Intent(LedgerBookDetailActivity.this, AddCashbookEntryActivity.class);
                intent.putExtra("BOOK_ID", bookId);
                startActivity(intent);
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadEntries();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (entriesListener != null) {
            entriesListener.remove();
            entriesListener = null;
        }
    }

    private void loadEntries() {
        if (entriesListener != null) {
            entriesListener.remove();
        }

        entriesListener = db.collection("cashbooks").document(bookId).collection("entries")
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;
                    allEntries.clear();
                    double totalIn = 0;
                    double totalOut = 0;
                    
                    for (DocumentSnapshot doc : snapshots) {
                        CashbookEntry entry = CashbookEntry.fromDocument(doc);
                        allEntries.add(entry);
                        
                        if ("CASH_IN".equals(entry.getType())) {
                            totalIn += entry.getAmount();
                        } else {
                            totalOut += entry.getAmount();
                        }
                    }
                    
                    // Update Book balances in background
                    updateBookBalances(totalIn, totalOut);
                    
                    updateBalances(totalIn, totalOut);
                    applyFilter();
                });
    }

    private void updateBookBalances(double totalIn, double totalOut) {
        db.collection("cashbooks").document(bookId)
            .update("totalCashIn", totalIn, "totalCashOut", totalOut, "netBalance", totalIn - totalOut);
    }

    private void updateBalances(double totalIn, double totalOut) {
        txtCashBalance.setText(String.format(Locale.getDefault(), "₹%.2f", totalIn));
        txtBankBalance.setText(String.format(Locale.getDefault(), "₹%.2f", totalOut));
    }

    private void setupFilters() {
        chipAll.setOnClickListener(v -> setFilter("ALL", chipAll));
        chipCash.setOnClickListener(v -> setFilter("CASH_IN", chipCash));
        chipBank.setOnClickListener(v -> setFilter("CASH_OUT", chipBank));
        chipToday.setOnClickListener(v -> setFilter("TODAY", chipToday));
        chipWeek.setOnClickListener(v -> setFilter("WEEK", chipWeek));
        chipMonth.setOnClickListener(v -> setFilter("MONTH", chipMonth));
    }

    private void setFilter(String filter, TextView activeChip) {
        activeFilter = filter;
        resetChipStyles();
        activeChip.setBackgroundResource(R.drawable.rounded_button);
        activeChip.setTextColor(getResources().getColor(R.color.on_primary));
        applyFilter();
    }

    private void resetChipStyles() {
        TextView[] chips = {chipAll, chipCash, chipBank, chipToday, chipWeek, chipMonth};
        for (TextView chip : chips) {
            chip.setBackgroundResource(R.drawable.chip_background);
            chip.setTextColor(getResources().getColor(R.color.text_secondary));
        }
    }

    private void applyFilter() {
        filteredEntries.clear();

        Calendar cal = Calendar.getInstance();
        long now = cal.getTimeInMillis();

        switch (activeFilter) {
            case "ALL":
                filteredEntries.addAll(allEntries);
                break;
            case "CASH_IN":
                filteredEntries.addAll(filterByType(allEntries, "CASH_IN"));
                break;
            case "CASH_OUT":
                filteredEntries.addAll(filterByType(allEntries, "CASH_OUT"));
                break;
            case "TODAY":
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                filteredEntries.addAll(filterByDateRange(allEntries, cal.getTimeInMillis(), now));
                break;
            case "WEEK":
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek()); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                filteredEntries.addAll(filterByDateRange(allEntries, cal.getTimeInMillis(), now));
                break;
            case "MONTH":
                cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                filteredEntries.addAll(filterByDateRange(allEntries, cal.getTimeInMillis(), now));
                break;
        }

        adapter.notifyDataSetChanged();

        if (filteredEntries.isEmpty()) {
            txtEmptyCashbook.setVisibility(View.VISIBLE);
            rvCashbookEntries.setVisibility(View.GONE);
        } else {
            txtEmptyCashbook.setVisibility(View.GONE);
            rvCashbookEntries.setVisibility(View.VISIBLE);
        }
    }

    private List<CashbookEntry> filterByType(List<CashbookEntry> list, String type) {
        List<CashbookEntry> res = new ArrayList<>();
        for (CashbookEntry e : list) if (type.equalsIgnoreCase(e.getType())) res.add(e);
        return res;
    }

    private List<CashbookEntry> filterByDateRange(List<CashbookEntry> list, long start, long end) {
        List<CashbookEntry> res = new ArrayList<>();
        for (CashbookEntry e : list) if (e.getDate() >= start && e.getDate() <= end) res.add(e);
        return res;
    }

    private class LedgerEntryAdapter extends RecyclerView.Adapter<LedgerEntryAdapter.ViewHolder> {
        private final List<CashbookEntry> list;

        public LedgerEntryAdapter(List<CashbookEntry> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cashbook_entry, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CashbookEntry entry = list.get(position);

            holder.txtParticulars.setText(entry.getParticulars() != null ? entry.getParticulars() : "");
            
            String category = entry.getCategory();
            if (category == null || category.trim().isEmpty()) category = "Other";
            
            // Append contact name if available (Udhaar)
            if (entry.getContactName() != null && !entry.getContactName().isEmpty()) {
                category += " • " + entry.getContactName();
            }
            holder.txtCategory.setText(category);

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            holder.txtDate.setText(sdf.format(new Date(entry.getDate())));

            String emoji = "💵";
            if (category.contains("Sales")) emoji = "📈";
            else if (category.contains("Rent")) emoji = "🏠";
            else if (category.contains("Salary")) emoji = "💰";
            holder.txtIcon.setText(emoji);

            String prefix = "CASH_IN".equalsIgnoreCase(entry.getType()) ? "+" : "-";
            holder.txtAmount.setText(String.format(Locale.getDefault(), "%s₹%.2f", prefix, entry.getAmount()));

            int colorRes = "CASH_IN".equalsIgnoreCase(entry.getType()) ? R.color.accent_positive : R.color.accent_negative;
            holder.txtAmount.setTextColor(getResources().getColor(colorRes));

            String mediumText = "CASH".equalsIgnoreCase(entry.getMedium()) ? "💵 Cash" : "🏦 Bank";
            holder.txtMedium.setText(mediumText);

            // Viewer cannot delete
            if (!"VIEWER".equalsIgnoreCase(userRole)) {
                holder.itemView.setOnLongClickListener(v -> {
                    showActionDialog(entry);
                    return true;
                });
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        private void showActionDialog(CashbookEntry entry) {
            boolean isUdhaar = entry.getContactName() != null && !entry.getContactName().isEmpty();
            String[] options = isUdhaar ? new String[]{"Send WhatsApp Reminder", "Delete Transaction"} : new String[]{"Delete Transaction"};

            new AlertDialog.Builder(LedgerBookDetailActivity.this)
                    .setTitle("Transaction Options")
                    .setItems(options, (dialog, which) -> {
                        if (isUdhaar && which == 0) {
                            // Send Reminder
                            com.example.frienddebt.utils.WhatsAppReminderUtil.sendUdhaarReminder(
                                    LedgerBookDetailActivity.this,
                                    entry.getContactPhone() != null ? entry.getContactPhone() : "", // Prompt for phone could be added
                                    entry.getContactName(),
                                    entry.getAmount(),
                                    bookName
                            );
                        } else {
                            // Delete
                            new AlertDialog.Builder(LedgerBookDetailActivity.this)
                                .setTitle("Delete Transaction")
                                .setMessage("Are you sure you want to delete this transaction?")
                                .setPositiveButton("Delete", (d, w) -> {
                                    db.collection("cashbooks").document(bookId)
                                      .collection("entries").document(entry.getId())
                                      .delete();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        }
                    })
                    .show();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtIcon, txtParticulars, txtDate, txtCategory, txtAmount, txtMedium;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtIcon = itemView.findViewById(R.id.txtEntryIcon);
                txtParticulars = itemView.findViewById(R.id.txtEntryParticulars);
                txtDate = itemView.findViewById(R.id.txtEntryDate);
                txtCategory = itemView.findViewById(R.id.txtEntryCategory);
                txtAmount = itemView.findViewById(R.id.txtEntryAmount);
                txtMedium = itemView.findViewById(R.id.txtEntryMedium);
            }
        }
    }
}
