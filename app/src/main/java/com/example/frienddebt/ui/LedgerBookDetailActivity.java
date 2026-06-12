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
import java.util.Map;
import java.util.HashMap;

import com.example.frienddebt.utils.StatusBarUtil;

public class LedgerBookDetailActivity extends AppCompatActivity {

    private TextView txtBookTitle, txtTotalIn, txtTotalOut, txtEmptyCashbook;
    private TextView chipAll, chipCash, chipBank, chipToday, chipWeek, chipMonth;
    private RecyclerView rvCashbookEntries;
    private android.widget.LinearLayout containerDebtSummary, layoutDebtEdges;
    private FloatingActionButton fabAddEntry;
    private ImageButton btnBack, btnBookSettings, btnSearchTransactions;
    private android.widget.EditText etSearchTransaction;

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
    private String searchQuery = "";
    private Map<String, Double> runningBalances = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ledger_book_detail);
        StatusBarUtil.applyStatusBarPadding(this);

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
        txtTotalIn = findViewById(R.id.txtCashBalance);
        txtTotalOut = findViewById(R.id.txtBankBalance);
        txtEmptyCashbook = findViewById(R.id.txtEmptyCashbook);
        rvCashbookEntries = findViewById(R.id.rvCashbookEntries);
        containerDebtSummary = findViewById(R.id.containerDebtSummary);
        layoutDebtEdges = findViewById(R.id.layoutDebtEdges);
        fabAddEntry = findViewById(R.id.fabAddEntry);
        btnBack = findViewById(R.id.btnBack);
        btnBookSettings = findViewById(R.id.btnBookSettings);
        btnSearchTransactions = findViewById(R.id.btnSearchTransactions);
        etSearchTransaction = findViewById(R.id.etSearchTransaction);

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

    

    
        
        btnSearchTransactions.setOnClickListener(v -> {
            if (etSearchTransaction.getVisibility() == View.VISIBLE) {
                etSearchTransaction.setVisibility(View.GONE);
                etSearchTransaction.setText("");
                searchQuery = "";
                applyFilter();
            } else {
                etSearchTransaction.setVisibility(View.VISIBLE);
                etSearchTransaction.requestFocus();
                // show keyboard
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etSearchTransaction, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
        
        etSearchTransaction.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                searchQuery = s.toString().toLowerCase().trim();
                applyFilter();
            }
        });
        
        btnBookSettings.setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, v);
            popup.getMenu().add(0, 1, 0, "Cash Counter");
            popup.getMenu().add(0, 2, 0, "Export PDF Report");
            popup.getMenu().add(0, 4, 0, "View Activity Log");
            if ("ADMIN".equalsIgnoreCase(userRole)) {
                popup.getMenu().add(0, 3, 0, "Share Invite Code");
                popup.getMenu().add(0, 7, 0, "Manage Members");
                popup.getMenu().add(0, 5, 0, "Rename Cashbook");
                popup.getMenu().add(0, 6, 0, "Delete Cashbook");
            }
            
            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        Intent ccIntent = new Intent(this, CashCounterActivity.class);
                        // We use the last calculated net balance
                        double currentNetBalance = 0;
                        try {
                            String balStr = txtTotalIn.getText().toString().replace("₹", "").replace(",", "");
                            String bankBalStr = txtTotalOut.getText().toString().replace("₹", "").replace(",", "");
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
                        showInviteRoleDialog();
                        return true;
                    case 4:
                        Intent activityLogIntent = new Intent(this, ActivityLogActivity.class);
                        activityLogIntent.putExtra("BOOK_ID", bookId);
                        startActivity(activityLogIntent);
                        return true;
                    case 5:
                        showRenameDialog();
                        return true;
                    case 6:
                        showDeleteCashbookDialog();
                        return true;
                    case 7:
                        showManageMembersDialog();
                        return true;
                    default:
                        return false;
                }
            });
            popup.show();
        });

        // Role check
        if ("VIEWER".equalsIgnoreCase(userRole)) {
            fabAddEntry.setVisibility(View.GONE);
        } else {
            com.example.frienddebt.utils.SpringAnimationUtil.applySpringEffect(fabAddEntry);
            fabAddEntry.setOnClickListener(v -> {
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
                        } else if ("CASH_OUT".equals(entry.getType()) || "EXPENSE".equals(entry.getType())) {
                            totalOut += entry.getAmount();
                        }
                    }
                    
                    // Calculate running balances (assuming entries are sorted descending by date)
                    double rb = 0.0;
                    runningBalances.clear();
                    // Iterate backwards (from oldest to newest) to calculate running balance correctly
                    for (int i = allEntries.size() - 1; i >= 0; i--) {
                        CashbookEntry entryObj = allEntries.get(i);
                        if ("CASH_IN".equals(entryObj.getType())) {
                            rb += entryObj.getAmount();
                        } else if ("CASH_OUT".equals(entryObj.getType()) || "EXPENSE".equals(entryObj.getType())) {
                            rb -= entryObj.getAmount();
                        }
                        runningBalances.put(entryObj.getId(), rb);
                    }
                    
                    // Update Book balances in background
                    updateBookBalances(totalIn, totalOut);
                    
                    updateBalances(totalIn, totalOut);
                    applyFilter();
                    
                    // Run Debt Simplification
                    db.collection("cashbooks").document(bookId).get().addOnSuccessListener(doc -> {
                        com.example.frienddebt.model.LedgerBook book = com.example.frienddebt.model.LedgerBook.fromDocument(doc);
                        if (book.getMembers() != null && book.getMembers().size() > 1) {
                            List<com.example.frienddebt.model.DebtEdge> edges = com.example.frienddebt.dsa.DebtSimplifier.simplifyDebts(allEntries);
                            updateDebtSummary(edges);
                        } else {
                            containerDebtSummary.setVisibility(View.GONE);
                        }
                    });
                });
    }

    private void updateDebtSummary(List<com.example.frienddebt.model.DebtEdge> edges) {
        if (edges.isEmpty()) {
            containerDebtSummary.setVisibility(View.GONE);
            return;
        }

        containerDebtSummary.setVisibility(View.VISIBLE);
        layoutDebtEdges.removeAllViews();

        for (com.example.frienddebt.model.DebtEdge edge : edges) {
            TextView txtEdge = new TextView(this);
            String fromName = edge.getFrom().equals(auth.getCurrentUser().getUid()) ? "You" : "User (" + edge.getFrom().substring(0, 4) + ")";
            String toName = edge.getTo().equals(auth.getCurrentUser().getUid()) ? "You" : "User (" + edge.getTo().substring(0, 4) + ")";
            txtEdge.setText(fromName + " owes " + toName + " ₹" + String.format(Locale.getDefault(), "%.2f", edge.getAmount()));
            txtEdge.setTextColor(getResources().getColor(R.color.text_primary));
            txtEdge.setPadding(0, 4, 0, 4);
            layoutDebtEdges.addView(txtEdge);
        }
    }

    private void updateBookBalances(double totalIn, double totalOut) {
        db.collection("cashbooks").document(bookId)
            .update("totalCashIn", totalIn, "totalCashOut", totalOut, "netBalance", totalIn - totalOut);
    }

    private void updateBalances(double totalIn, double totalOut) {
        txtTotalIn.setText(String.format(Locale.getDefault(), "₹%.2f", totalIn));
        txtTotalOut.setText(String.format(Locale.getDefault(), "₹%.2f", totalOut));
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

        // Apply Search Text Filter
        if (!searchQuery.isEmpty()) {
            List<CashbookEntry> searchResult = new ArrayList<>();
            for (CashbookEntry e : filteredEntries) {
                boolean matches = false;
                if (e.getParticulars() != null && e.getParticulars().toLowerCase().contains(searchQuery)) matches = true;
                if (e.getCategory() != null && e.getCategory().toLowerCase().contains(searchQuery)) matches = true;
                if (String.valueOf(e.getAmount()).contains(searchQuery)) matches = true;
                if (matches) searchResult.add(e);
            }
            filteredEntries.clear();
            filteredEntries.addAll(searchResult);
        }

        adapter.notifyDataSetChanged();
        rvCashbookEntries.scheduleLayoutAnimation();

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
        for (CashbookEntry e : list) {
            if (type.equalsIgnoreCase(e.getType())) {
                res.add(e);
            } else if ("CASH_OUT".equalsIgnoreCase(type) && "EXPENSE".equalsIgnoreCase(e.getType())) {
                res.add(e);
            }
        }
        return res;
    }

    private List<CashbookEntry> filterByDateRange(List<CashbookEntry> list, long start, long end) {
        List<CashbookEntry> res = new ArrayList<>();
        for (CashbookEntry e : list) if (e.getDate() >= start && e.getDate() <= end) res.add(e);
        return res;
    }

    private void showInviteRoleDialog() {
        String[] roles = {"Viewer", "Editor"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Role for Invite")
            .setSingleChoiceItems(roles, 0, null)
            .setPositiveButton("Generate Link", (dialog, which) -> {
                int selectedPosition = ((androidx.appcompat.app.AlertDialog)dialog).getListView().getCheckedItemPosition();
                String role = selectedPosition == 0 ? "VIEWER" : "EDITOR";
                generateAndSaveInviteCode(bookId, role);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void generateAndSaveInviteCode(String bookId, String role) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int idx = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(idx));
        }
        String newCode = sb.toString();

        db.collection("cashbooks").document(bookId).get()
            .addOnSuccessListener(doc -> {
                String ownerId = doc.getString("ownerId");
                if (ownerId == null) {
                    ownerId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
                }
                Long createdAt = doc.getLong("createdAt");
                if (createdAt == null) {
                    createdAt = System.currentTimeMillis();
                }

                String finalOwnerId = ownerId;
                Long finalCreatedAt = createdAt;
                db.collection("cashbooks").document(bookId)
                    .update("inviteCode", newCode)
                    .addOnSuccessListener(aVoid -> {
                        java.util.Map<String, Object> inviteMapping = new java.util.HashMap<>();
                        inviteMapping.put("bookId", bookId);
                        inviteMapping.put("ownerId", finalOwnerId);
                        inviteMapping.put("createdAt", finalCreatedAt);
                        inviteMapping.put("role", role);
                        
                        db.collection("invite_codes").document(newCode).set(inviteMapping)
                            .addOnSuccessListener(aVoid2 -> {
                                Intent shareCodeIntent = new Intent(Intent.ACTION_SEND);
                                shareCodeIntent.setType("text/plain");
                                shareCodeIntent.putExtra(Intent.EXTRA_TEXT, "Join my Ledger on Nexa as " + role + "! Invite Code: " + newCode);
                                startActivity(Intent.createChooser(shareCodeIntent, "Share Invite Code"));
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(LedgerBookDetailActivity.this, "Failed to save invite", Toast.LENGTH_SHORT).show();
                            });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(LedgerBookDetailActivity.this, "Failed to update cashbook", Toast.LENGTH_SHORT).show();
                    });
            })
            .addOnFailureListener(e -> {
                Toast.makeText(LedgerBookDetailActivity.this, "Failed to retrieve ledger details", Toast.LENGTH_SHORT).show();
            });
    }

    private void showManageMembersDialog() {
        db.collection("cashbooks").document(bookId).get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) return;
                
                java.util.Map<String, String> members = (java.util.Map<String, String>) doc.get("members");
                java.util.Map<String, String> memberNames = (java.util.Map<String, String>) doc.get("memberNames");
                if (members == null) members = new java.util.HashMap<>();
                if (memberNames == null) memberNames = new java.util.HashMap<>();
                
                final java.util.Map<String, String> finalMembers = members;
                
                java.util.List<String> userIds = new java.util.ArrayList<>(members.keySet());
                String[] displayNames = new String[userIds.size()];
                
                for (int i = 0; i < userIds.size(); i++) {
                    String uid = userIds.get(i);
                    String role = members.get(uid);
                    String name = memberNames.containsKey(uid) ? memberNames.get(uid) : "User (" + uid.substring(0, 5) + "...)";
                    displayNames[i] = name + " - " + role;
                }
                
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Manage Members")
                    .setItems(displayNames, (dialog, which) -> {
                        String selectedUid = userIds.get(which);
                        String currentRole = finalMembers.get(selectedUid);
                        if ("ADMIN".equalsIgnoreCase(currentRole)) {
                            Toast.makeText(this, "Cannot change ADMIN role", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        String[] options = {"Make Editor", "Make Viewer", "Remove from Cashbook"};
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Change Role")
                            .setItems(options, (d2, w2) -> {
                                if (w2 == 0) {
                                    updateMemberRole(selectedUid, "EDITOR");
                                } else if (w2 == 1) {
                                    updateMemberRole(selectedUid, "VIEWER");
                                } else if (w2 == 2) {
                                    removeMember(selectedUid);
                                }
                            })
                            .show();
                    })
                    .setPositiveButton("Close", null)
                    .show();
            });
    }

    private void updateMemberRole(String uid, String newRole) {
        db.collection("cashbooks").document(bookId)
            .update("members." + uid, newRole)
            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Role updated to " + newRole, Toast.LENGTH_SHORT).show())
            .addOnFailureListener(e -> Toast.makeText(this, "Failed to update role", Toast.LENGTH_SHORT).show());
    }

    private void removeMember(String uid) {
        db.collection("cashbooks").document(bookId)
            .update("members." + uid, com.google.firebase.firestore.FieldValue.delete(),
                    "memberNames." + uid, com.google.firebase.firestore.FieldValue.delete())
            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Member removed", Toast.LENGTH_SHORT).show())
            .addOnFailureListener(e -> Toast.makeText(this, "Failed to remove member", Toast.LENGTH_SHORT).show());
    }

    private void showRenameDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(bookName);
        input.setSelection(bookName.length());
        
        new AlertDialog.Builder(this)
            .setTitle("Rename Cashbook")
            .setView(input)
            .setPositiveButton("Rename", (dialog, which) -> {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty() && !newName.equals(bookName)) {
                    db.collection("cashbooks").document(bookId)
                        .update("name", newName)
                        .addOnSuccessListener(aVoid -> {
                            bookName = newName;
                            txtBookTitle.setText(newName);
                            Toast.makeText(this, "Renamed successfully", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Failed to rename", Toast.LENGTH_SHORT).show());
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showDeleteCashbookDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Delete Cashbook")
            .setMessage("Are you sure you want to delete this cashbook? All transactions inside it will be lost. This cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                db.collection("cashbooks").document(bookId)
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Cashbook deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show());
            })
            .setNegativeButton("Cancel", null)
            .show();
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

            String currentUserId = "";
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }

            String prefix = "-";
            int colorRes = R.color.accent_negative;

            if ("CASH_IN".equalsIgnoreCase(entry.getType())) {
                prefix = "+";
                colorRes = R.color.accent_positive;
            } else if ("SETTLEMENT".equalsIgnoreCase(entry.getType())) {
                if (entry.getParticipants() != null && entry.getParticipants().contains(currentUserId)) {
                    prefix = "+";
                    colorRes = R.color.accent_positive;
                } else if (currentUserId.equals(entry.getPaidBy())) {
                    prefix = "-";
                    colorRes = R.color.accent_negative;
                } else {
                    prefix = ""; // Neutral if viewing another person's settlement
                    colorRes = R.color.text_primary;
                }
            }

            holder.txtAmount.setText(String.format(Locale.getDefault(), "%s₹%.2f", prefix, entry.getAmount()));
            holder.txtAmount.setTextColor(getResources().getColor(colorRes));

            String mediumText = "CASH".equalsIgnoreCase(entry.getMedium()) ? "💵 Cash" : "🏦 Bank";
            holder.txtMedium.setText(mediumText);

            Double rb = runningBalances.get(entry.getId());
            if (rb != null) {
                holder.txtRunningBalance.setText(String.format(Locale.getDefault(), "Bal: ₹%.2f", rb));
                holder.txtRunningBalance.setVisibility(View.VISIBLE);
            } else {
                holder.txtRunningBalance.setVisibility(View.GONE);
            }

            if (entry.getCreatedByName() != null && !entry.getCreatedByName().isEmpty()) {
                holder.txtEntryAddedBy.setText("Added by: " + entry.getCreatedByName());
                holder.txtEntryAddedBy.setVisibility(View.VISIBLE);
            } else {
                holder.txtEntryAddedBy.setVisibility(View.GONE);
            }

            // Viewer cannot delete
            if (!"VIEWER".equalsIgnoreCase(userRole)) {
                holder.itemView.setOnLongClickListener(v -> {
                    showActionDialog(entry);
                    return true;
                });
            }
            
            holder.btnOptions.setOnClickListener(v -> {
                showActionDialog(entry);
            });
            
            holder.itemView.setOnClickListener(v -> {
                showTransactionDetails(entry);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        private void showActionDialog(CashbookEntry entry) {
            boolean isUdhaar = entry.getContactName() != null && !entry.getContactName().isEmpty();
            List<String> options = new ArrayList<>();
            if (isUdhaar) options.add("Send WhatsApp Reminder");
            options.add("Create App Reminder");
            options.add("Edit Transaction");
            options.add("Duplicate Transaction");
            options.add("Delete Transaction");

            new AlertDialog.Builder(LedgerBookDetailActivity.this)
                    .setTitle("Transaction Options")
                    .setItems(options.toArray(new String[0]), (dialog, which) -> {
                        String selected = options.get(which);
                        if ("Send WhatsApp Reminder".equals(selected)) {
                            // Send Reminder
                            com.example.frienddebt.utils.WhatsAppReminderUtil.sendUdhaarReminder(
                                    LedgerBookDetailActivity.this,
                                    entry.getContactPhone() != null ? entry.getContactPhone() : "",
                                    entry.getContactName(),
                                    entry.getAmount(),
                                    bookName
                            );
                        } else if ("Create App Reminder".equals(selected)) {
                            Intent intent = new Intent(LedgerBookDetailActivity.this, AddReminderActivity.class);
                            intent.putExtra("LINKED_ID", entry.getId());
                            intent.putExtra("LINKED_TYPE", "CASHBOOK");
                            String prefix = "CASH_IN".equals(entry.getType()) ? "Collect" : "Pay";
                            String name = (entry.getContactName() != null && !entry.getContactName().isEmpty()) ? entry.getContactName() : entry.getCategory();
                            intent.putExtra("LINKED_TITLE", prefix + " ₹" + entry.getAmount() + " - " + name);
                            startActivity(intent);
                        } else if ("Edit Transaction".equals(selected)) {
                            Intent intent = new Intent(LedgerBookDetailActivity.this, AddCashbookEntryActivity.class);
                            intent.putExtra("BOOK_ID", bookId);
                            intent.putExtra("ENTRY_ID", entry.getId());
                            intent.putExtra("IS_EDIT_MODE", true);
                            startActivity(intent);
                        } else if ("Duplicate Transaction".equals(selected)) {
                            Intent intent = new Intent(LedgerBookDetailActivity.this, AddCashbookEntryActivity.class);
                            intent.putExtra("BOOK_ID", bookId);
                            intent.putExtra("ENTRY_ID", entry.getId());
                            intent.putExtra("IS_DUPLICATE_MODE", true);
                            startActivity(intent);
                        } else if ("Delete Transaction".equals(selected)) {
                            // Delete
                            new AlertDialog.Builder(LedgerBookDetailActivity.this)
                                .setTitle("Delete Transaction")
                                .setMessage("Are you sure you want to delete this transaction?")
                                .setPositiveButton("Delete", (d, w) -> {
                                    // Log deletion first
                                    if (auth.getCurrentUser() != null) {
                                        String actorName = auth.getCurrentUser().getDisplayName();
                                        if (actorName == null || actorName.trim().isEmpty()) {
                                            actorName = auth.getCurrentUser().getEmail();
                                        }
                                        if (actorName == null || actorName.trim().isEmpty()) {
                                            actorName = "Unknown Member";
                                        }
                                        String logId = db.collection("cashbooks").document(bookId).collection("logs").document().getId();
                                        String logDetails = actorName + " deleted " + ("CASH_IN".equalsIgnoreCase(entry.getType()) ? "CASH IN" : "CASH OUT") + " of ₹" + String.format(Locale.getDefault(), "%.2f", entry.getAmount()) + " for \"" + entry.getParticulars() + "\"";

                                        com.example.frienddebt.model.AuditLog audit = new com.example.frienddebt.model.AuditLog(
                                                logId, bookId, "DELETE", auth.getCurrentUser().getUid(), actorName, entry.getParticulars(), entry.getAmount(), entry.getType(), System.currentTimeMillis(), logDetails
                                        );
                                        db.collection("cashbooks").document(bookId).collection("logs").document(logId).set(audit.toFirestoreMap());
                                    }

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

        private void showTransactionDetails(CashbookEntry entry) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
            String dateStr = sdf.format(new Date(entry.getDate()));
            String createdBy = entry.getCreatedByName() != null ? entry.getCreatedByName() : 
                               (entry.getCreatedBy() != null ? entry.getCreatedBy() : "Unknown");
            
            String msg = "Amount: ₹" + entry.getAmount() + "\n" +
                         "Type: " + entry.getType() + "\n" +
                         "Category: " + (entry.getCategory() != null ? entry.getCategory() : "Other") + "\n" +
                         "Medium: " + entry.getMedium() + "\n" +
                         "Date: " + dateStr + "\n" +
                         "Particulars: " + entry.getParticulars() + "\n" +
                         "Added by: " + createdBy + "\n";
                         
            if (entry.getContactName() != null && !entry.getContactName().isEmpty()) {
                msg += "Contact: " + entry.getContactName() + "\n";
            }
            if (entry.getSplitMethod() != null && !entry.getSplitMethod().isEmpty()) {
                msg += "Split: " + entry.getSplitMethod() + "\n";
            }
            
            Double rb = runningBalances.get(entry.getId());
            if (rb != null) {
                msg += "Running Balance after this: ₹" + String.format(Locale.getDefault(), "%.2f", rb) + "\n";
            }
            
            androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(LedgerBookDetailActivity.this)
                .setTitle("Transaction Details")
                .setMessage(msg)
                .setPositiveButton("Close", null);
                
            if (!"VIEWER".equalsIgnoreCase(userRole)) {
                builder.setNeutralButton("Options", (dialog, which) -> {
                    showActionDialog(entry);
                });
                builder.setNegativeButton("Edit", (dialog, which) -> {
                    Intent intent = new Intent(LedgerBookDetailActivity.this, AddCashbookEntryActivity.class);
                    intent.putExtra("BOOK_ID", bookId);
                    intent.putExtra("ENTRY_ID", entry.getId());
                    intent.putExtra("IS_EDIT_MODE", true);
                    startActivity(intent);
                });
            }
            builder.show();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtIcon, txtParticulars, txtDate, txtCategory, txtAmount, txtMedium, txtRunningBalance, txtEntryAddedBy;
            android.widget.ImageButton btnOptions;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtIcon = itemView.findViewById(R.id.txtEntryIcon);
                txtParticulars = itemView.findViewById(R.id.txtEntryParticulars);
                txtDate = itemView.findViewById(R.id.txtEntryDate);
                txtCategory = itemView.findViewById(R.id.txtEntryCategory);
                txtAmount = itemView.findViewById(R.id.txtEntryAmount);
                txtMedium = itemView.findViewById(R.id.txtEntryMedium);
                txtRunningBalance = itemView.findViewById(R.id.txtRunningBalance);
                txtEntryAddedBy = itemView.findViewById(R.id.txtEntryAddedBy);
                btnOptions = itemView.findViewById(R.id.btnEntryOptions);
            }
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
