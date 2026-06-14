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
import com.example.frienddebt.utils.UserProfileHelper;

public class LedgerBookDetailActivity extends AppCompatActivity {

    private TextView txtBookTitle, txtTotalIn, txtTotalOut, txtCashInLabel, txtCashOutLabel, txtEmptyCashbook;
    private TextView chipAll, chipCash, chipBank, chipToday, chipWeek, chipMonth;
    private RecyclerView rvCashbookEntries;
    private RecyclerView rvDebtEdges;
    private RecyclerView rvMemberBalances;
    private TextView txtOutstandingTotal;
    private android.widget.LinearLayout containerDebtSummary, layoutDebtEdges;
    private FloatingActionButton fabAddEntry;
    private ImageButton btnBack, btnBookSettings, btnSearchTransactions;
    private android.widget.EditText etSearchTransaction;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration entriesListener;
    private ListenerRegistration pendingListener;

    private List<CashbookEntry> allEntries = new ArrayList<>();
    private List<CashbookEntry> filteredEntries = new ArrayList<>();
    private LedgerEntryAdapter adapter;

    private String activeFilter = "ALL";
    private String bookId;
    private String bookName;
    private String userRole;
    private String searchQuery = "";
    private Map<String, Double> runningBalances = new HashMap<>();
    private Map<String, String> resolvedUserNames = new HashMap<>();
    private int pendingCount = 0;
    private boolean isSharedGroup = false;

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
        txtCashInLabel = findViewById(R.id.txtCashInLabel);
        txtCashOutLabel = findViewById(R.id.txtCashOutLabel);
        txtEmptyCashbook = findViewById(R.id.txtEmptyCashbook);
        rvCashbookEntries = findViewById(R.id.rvCashbookEntries);
        containerDebtSummary  = findViewById(R.id.containerDebtSummary);
        layoutDebtEdges        = findViewById(R.id.layoutDebtEdges);
        rvDebtEdges            = findViewById(R.id.rvDebtEdges);
        rvMemberBalances       = findViewById(R.id.rvMemberBalances);
        txtOutstandingTotal    = findViewById(R.id.txtOutstandingTotal);
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

            // We determine if it's a shared group by checking if the owner role is present, or if there's a userRole (which usually means shared)
            // A better way is checking if 'members' exists, but we can just show these options inside any ledger for now and AddSharedExpenseActivity will handle the rest.
            popup.getMenu().add(0, 10, 0, "Add Shared Expense");
            popup.getMenu().add(0, 11, 0, "Settle Up Debts");

            // Feature 21: Only ADMIN/OWNER can share invite code or manage members
            if ("ADMIN".equalsIgnoreCase(userRole) || "OWNER".equalsIgnoreCase(userRole)) {
                popup.getMenu().add(0, 3, 0, "Share Invite Code");
                popup.getMenu().add(0, 7, 0, "Manage Members");
                popup.getMenu().add(0, 12, 0, "Add Offline Member");
                // Pending approvals with live count badge
                String pendingLabel = pendingCount > 0
                    ? "Pending Approvals  (" + pendingCount + ")"
                    : "Pending Approvals";
                popup.getMenu().add(0, 8, 0, pendingLabel);
                popup.getMenu().add(0, 5, 0, "Rename Cashbook");
                popup.getMenu().add(0, 6, 0, "Delete Cashbook");
            } else if ("VIEWER".equalsIgnoreCase(userRole) || "EDITOR".equalsIgnoreCase(userRole)) {
                // Non-admin members can leave the group
                popup.getMenu().add(0, 9, 0, "Leave Group");
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
                        android.net.Uri pdfUri = com.example.frienddebt.utils.ReportGenerator.generatePdfReport(this, bookName, allEntries, resolvedUserNames, auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null);
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
                                    generateAndSaveInviteCode(bookId);
                                }
                            });
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
                    case 8:
                        // Open Pending Approvals screen (admin only)
                        Intent pendingIntent = new Intent(this, PendingApprovalsActivity.class);
                        pendingIntent.putExtra("BOOK_ID", bookId);
                        startActivity(pendingIntent);
                        return true;
                    case 9:
                        // Leave Group (non-admin only)
                        showLeaveGroupDialog();
                        return true;
                    case 10:
                        Intent splitIntent = new Intent(this, AddSharedExpenseActivity.class);
                        splitIntent.putExtra("BOOK_ID", bookId);
                        startActivity(splitIntent);
                        return true;
                    case 11:
                        Intent settleIntent = new Intent(this, SettleUpActivity.class);
                        settleIntent.putExtra("BOOK_ID", bookId);
                        startActivity(settleIntent);
                        return true;
                    case 12:
                        showAddOfflineMemberDialog();
                        return true;
                    default:
                        return false;
                }
            });
            popup.show();
        });


        // Role check — EDITOR and ADMIN can add entries
        if ("VIEWER".equalsIgnoreCase(userRole)) {
            fabAddEntry.setVisibility(View.GONE);
        } else {
            com.example.frienddebt.utils.SpringAnimationUtil.applySpringEffect(fabAddEntry);
            fabAddEntry.setOnClickListener(v -> {
                if (isSharedGroup) {
                    showAddEntryBottomSheet(bookId);
                } else {
                    Intent intent = new Intent(LedgerBookDetailActivity.this, AddCashbookEntryActivity.class);
                    intent.putExtra("BOOK_ID", bookId);
                    startActivity(intent);
                }
            });
        }
    }

    private void showAddEntryBottomSheet(String bId) {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_money_actions, null);

        // Hide the universal actions that don't apply here
        sheetView.findViewById(R.id.btnActionCreateLedger).setVisibility(View.GONE);
        sheetView.findViewById(R.id.btnActionJoinLedger).setVisibility(View.GONE);

        if (isSharedGroup) {
            sheetView.findViewById(R.id.btnActionAddIncome).setVisibility(View.GONE);
            sheetView.findViewById(R.id.btnActionAddExpense).setVisibility(View.GONE);
        }

        sheetView.findViewById(R.id.btnActionAddIncome).setOnClickListener(v1 -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(LedgerBookDetailActivity.this, AddCashbookEntryActivity.class);
            intent.putExtra("BOOK_ID", bId);
            startActivity(intent);
        });

        sheetView.findViewById(R.id.btnActionAddExpense).setOnClickListener(v2 -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(LedgerBookDetailActivity.this, AddCashbookEntryActivity.class);
            intent.putExtra("BOOK_ID", bId);
            startActivity(intent);
        });

        sheetView.findViewById(R.id.btnActionAddSharedExpense).setOnClickListener(v3 -> {
            bottomSheetDialog.dismiss();
            Intent splitIntent = new Intent(LedgerBookDetailActivity.this, AddSharedExpenseActivity.class);
            splitIntent.putExtra("BOOK_ID", bId);
            startActivity(splitIntent);
        });

        sheetView.findViewById(R.id.btnActionSettleUp).setOnClickListener(v4 -> {
            bottomSheetDialog.dismiss();
            Intent settleIntent = new Intent(LedgerBookDetailActivity.this, SettleUpActivity.class);
            settleIntent.putExtra("BOOK_ID", bId);
            startActivity(settleIntent);
        });

        bottomSheetDialog.setContentView(sheetView);
        bottomSheetDialog.show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadEntries();
        // Admins get a live pending-requests count shown in the settings menu
        if ("ADMIN".equalsIgnoreCase(userRole) || "OWNER".equalsIgnoreCase(userRole)) {
            listenForPendingCount();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (entriesListener != null) { entriesListener.remove(); entriesListener = null; }
        if (pendingListener  != null) { pendingListener.remove();  pendingListener  = null; }
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
                    
                    applyFilter();
                    
                    // Run Debt Simplification
                    final double finalTotalIn = totalIn;
                    final double finalTotalOut = totalOut;
                    db.collection("cashbooks").document(bookId).get().addOnSuccessListener(doc -> {
                        com.example.frienddebt.model.LedgerBook book = com.example.frienddebt.model.LedgerBook.fromDocument(doc);
                        isSharedGroup = (book.getMembers() != null && book.getMembers().size() > 1);
                        if (isSharedGroup) {
                            if (txtCashInLabel != null) txtCashInLabel.setText("My Balance");
                            if (txtCashOutLabel != null) txtCashOutLabel.setText("Group Spending");

                            List<com.example.frienddebt.model.DebtEdge> edges = com.example.frienddebt.dsa.DebtSimplifier.simplifyDebts(allEntries);
                            updateDebtSummary(edges, allEntries);

                            double myBalance = 0;
                            String currentUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
                            for (com.example.frienddebt.model.DebtEdge edge : edges) {
                                if (edge.getFrom().equals(currentUid)) myBalance -= edge.getAmount();
                                if (edge.getTo().equals(currentUid)) myBalance += edge.getAmount();
                            }
                            if (txtTotalIn != null) txtTotalIn.setText(String.format(Locale.getDefault(), "₹%.2f", myBalance));

                            double groupSpend = 0;
                            for (CashbookEntry entryObj2 : allEntries) {
                                if ("EXPENSE".equals(entryObj2.getType())) groupSpend += entryObj2.getAmount();
                            }
                            if (txtTotalOut != null) txtTotalOut.setText(String.format(Locale.getDefault(), "₹%.2f", groupSpend));
                        } else {
                            if (txtCashInLabel != null) txtCashInLabel.setText("Total Cash In");
                            if (txtCashOutLabel != null) txtCashOutLabel.setText("Total Cash Out");
                            containerDebtSummary.setVisibility(View.GONE);
                            updateBalances(finalTotalIn, finalTotalOut);
                        }
                        if (adapter != null) adapter.notifyDataSetChanged();

                        // Fetch names for all participants to render nice labels
                        List<String> allUids = new ArrayList<>();
                        for (CashbookEntry entryObj3 : allEntries) {
                            if (entryObj3.getPaidBy() != null && !allUids.contains(entryObj3.getPaidBy())) allUids.add(entryObj3.getPaidBy());
                            if (entryObj3.getSplits() != null) {
                                for (String uid : entryObj3.getSplits().keySet()) {
                                    if (!allUids.contains(uid)) allUids.add(uid);
                                }
                            }
                        }
                        if (!allUids.isEmpty()) {
                            UserProfileHelper.resolveNames(db, allUids, nameMap -> {
                                resolvedUserNames.putAll(nameMap);
                                if (adapter != null) adapter.notifyDataSetChanged();
                            });
                        }
                    });
                });
    }

    private void updateDebtSummary(List<com.example.frienddebt.model.DebtEdge> edges, List<CashbookEntry> entries) {
        Map<String, Double> memberBalances = com.example.frienddebt.dsa.DebtSimplifier.calculateNetBalances(entries);
        
        if (edges.isEmpty() && memberBalances.isEmpty()) {
            containerDebtSummary.setVisibility(View.GONE);
            return;
        }

        containerDebtSummary.setVisibility(View.VISIBLE);

        // Collect unique UIDs
        List<String> allUids = new ArrayList<>(memberBalances.keySet());
        for (com.example.frienddebt.model.DebtEdge edge : edges) {
            if (!allUids.contains(edge.getFrom())) allUids.add(edge.getFrom());
            if (!allUids.contains(edge.getTo()))   allUids.add(edge.getTo());
        }

        // Outstanding total
        double total = 0;
        for (com.example.frienddebt.model.DebtEdge e : edges) total += e.getAmount();
        final double outstandingTotal = total;

        String currentUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";

        // Batch-fetch real display names then render via RecyclerView
        UserProfileHelper.resolveNames(db, allUids, nameMap -> {
            if (txtOutstandingTotal != null) {
                txtOutstandingTotal.setText("\u20b9" + String.format(Locale.getDefault(), "%.2f", outstandingTotal) + " total outstanding");
            }

            if (rvMemberBalances != null) {
                rvMemberBalances.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                rvMemberBalances.setAdapter(new MemberBalanceAdapter(memberBalances, nameMap, currentUid));
            }

            rvDebtEdges.setLayoutManager(new LinearLayoutManager(this));
            rvDebtEdges.setAdapter(new DebtEdgeAdapter(edges, nameMap, currentUid));
        });
    }

    // ─── Member Balance Adapter (Horizontal List) ───────
    private class MemberBalanceAdapter extends RecyclerView.Adapter<MemberBalanceAdapter.VH> {
        private final List<Map.Entry<String, Double>> balances;
        private final Map<String, String> nameMap;
        private final String currentUid;

        MemberBalanceAdapter(Map<String, Double> balanceMap, Map<String, String> nameMap, String currentUid) {
            this.balances = new ArrayList<>(balanceMap.entrySet());
            this.nameMap = nameMap;
            this.currentUid = currentUid;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_member_balance, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map.Entry<String, Double> entry = balances.get(pos);
            String uid = entry.getKey();
            double amount = entry.getValue();
            
            String name = uid.equals(currentUid) ? "You" : nameMap.getOrDefault(uid, "User");
            h.txtName.setText(name);
            
            String initials = name.length() > 0 ? name.substring(0, 1).toUpperCase() : "?";
            h.txtInitials.setText(initials);
            
            if (amount > 0.01) { // Owed
                h.txtBalance.setText("+" + String.format(Locale.getDefault(), "₹%.2f", amount));
                h.txtBalance.setTextColor(getResources().getColor(R.color.accent_positive));
            } else if (amount < -0.01) { // Owes
                h.txtBalance.setText("-" + String.format(Locale.getDefault(), "₹%.2f", Math.abs(amount)));
                h.txtBalance.setTextColor(getResources().getColor(R.color.accent_negative));
            } else {
                h.txtBalance.setText("Settled");
                h.txtBalance.setTextColor(getResources().getColor(R.color.text_secondary));
            }
        }

        @Override
        public int getItemCount() { return balances.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView txtInitials, txtName, txtBalance;
            VH(View v) {
                super(v);
                txtInitials = v.findViewById(R.id.txtInitials);
                txtName = v.findViewById(R.id.txtName);
                txtBalance = v.findViewById(R.id.txtBalance);
            }
        }
    }

    // ─── Inline DebtEdge Adapter (Who Owes Whom card with Settle button) ───────

    private class DebtEdgeAdapter extends RecyclerView.Adapter<DebtEdgeAdapter.VH> {
        private final List<com.example.frienddebt.model.DebtEdge> edges;
        private final Map<String, String> nameMap;
        private final String currentUid;

        DebtEdgeAdapter(List<com.example.frienddebt.model.DebtEdge> edges,
                        Map<String, String> nameMap, String currentUid) {
            this.edges      = edges;
            this.nameMap    = nameMap;
            this.currentUid = currentUid;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_settlement, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            com.example.frienddebt.model.DebtEdge edge = edges.get(pos);
            String fromName = edge.getFrom().equals(currentUid)
                ? "You" : nameMap.getOrDefault(edge.getFrom(), "User");
            String toName = edge.getTo().equals(currentUid)
                ? "You" : nameMap.getOrDefault(edge.getTo(), "User");

            h.txtDebtor.setText(fromName);
            h.txtCreditor.setText(toName);
            h.txtAmount.setText(String.format(Locale.getDefault(), "₹%.2f", edge.getAmount()));

            // Inline settle button
            h.btnMarkPaid.setText("Settle");
            h.btnMarkPaid.setOnClickListener(v -> {
                new AlertDialog.Builder(LedgerBookDetailActivity.this)
                    .setTitle("Confirm Settlement")
                    .setMessage(fromName + " pays " + toName + " ₹" +
                        String.format(Locale.getDefault(), "%.2f", edge.getAmount()))
                    .setPositiveButton("Confirm", (d, w) -> recordSettlement(edge, fromName, toName))
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }

        @Override
        public int getItemCount() { return edges.size(); }

        class VH extends RecyclerView.ViewHolder {
            android.widget.TextView txtDebtor, txtCreditor, txtAmount;
            android.widget.Button btnMarkPaid;
            VH(View v) {
                super(v);
                txtDebtor   = v.findViewById(R.id.txtDebtor);
                txtCreditor = v.findViewById(R.id.txtCreditor);
                txtAmount   = v.findViewById(R.id.txtAmount);
                btnMarkPaid = v.findViewById(R.id.btnMarkPaid);
            }
        }
    }

    private void recordSettlement(com.example.frienddebt.model.DebtEdge edge,
                                  String fromName, String toName) {
        String entryId = java.util.UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        // CRITICAL: paidBy must be the UID (not display name) so DebtSimplifier can process it.
        // participants[0] = the UID of the receiver (creditor).
        java.util.List<String> participants = new java.util.ArrayList<>();
        participants.add(edge.getTo()); // receiver UID

        Map<String, Object> data = new HashMap<>();
        data.put("id",           entryId);
        data.put("bookId",       bookId);
        data.put("date",         now);
        data.put("particulars",  fromName + " → " + toName);
        data.put("type",         "SETTLEMENT");
        data.put("medium",       "BANK");
        data.put("amount",       edge.getAmount());
        data.put("category",     "Settlement");
        data.put("note",         "");
        data.put("createdAt",    now);
        data.put("lastModifiedAt", now);
        data.put("addedBy",      auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "");
        data.put("paidBy",       edge.getFrom());      // UID of the payer
        data.put("participants", participants);          // [UID of receiver]

        db.collection("cashbooks").document(bookId).collection("entries")
            .document(entryId).set(data)
            .addOnSuccessListener(aVoid -> {
                String actorName = auth.getCurrentUser() != null ? auth.getCurrentUser().getDisplayName() : "Unknown User";
                if (actorName == null || actorName.isEmpty()) actorName = "Nexa User";
                String logId = db.collection("cashbooks").document(bookId).collection("logs").document().getId();
                String logDetails = actorName + " settled ₹" + String.format(Locale.getDefault(), "%.2f", edge.getAmount()) + " from " + fromName + " to " + toName;

                com.example.frienddebt.model.AuditLog audit = new com.example.frienddebt.model.AuditLog(
                        logId, bookId, "SETTLE", auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "", actorName, "Settlement", edge.getAmount(), "SETTLEMENT", System.currentTimeMillis(), logDetails
                );
                db.collection("cashbooks").document(bookId).collection("logs").document(logId).set(audit.toFirestoreMap())
                        .addOnCompleteListener(task -> {
                            com.google.android.material.snackbar.Snackbar snackbar = com.google.android.material.snackbar.Snackbar.make(
                                findViewById(android.R.id.content), 
                                "Settlement recorded! ✅", 
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                            );
                            snackbar.setAction("UNDO", v -> {
                                db.collection("cashbooks").document(bookId).collection("entries").document(entryId).delete();
                                db.collection("cashbooks").document(bookId).collection("logs").document(logId).delete();
                                Toast.makeText(this, "Settlement Undone", Toast.LENGTH_SHORT).show();
                            });
                            snackbar.show();
                        });
            })
            .addOnFailureListener(e ->
                Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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

    private void generateAndSaveInviteCode(String bookId) {
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
                        
                        db.collection("invite_codes").document(newCode).set(inviteMapping)
                            .addOnSuccessListener(aVoid2 -> {
                                Intent shareCodeIntent = new Intent(Intent.ACTION_SEND);
                                shareCodeIntent.setType("text/plain");
                                shareCodeIntent.putExtra(Intent.EXTRA_TEXT, "Join my Ledger on Nexa! Invite Code: " + newCode);
                                startActivity(Intent.createChooser(shareCodeIntent, "Share Invite Code"));
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(LedgerBookDetailActivity.this, "Failed to save invite code mapping", Toast.LENGTH_SHORT).show();
                            });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(LedgerBookDetailActivity.this, "Failed to update ledger invite code", Toast.LENGTH_SHORT).show();
                    });
            })
            .addOnFailureListener(e -> {
                Toast.makeText(LedgerBookDetailActivity.this, "Failed to retrieve ledger details", Toast.LENGTH_SHORT).show();
            });
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

            // Feature 20: Show who added this entry
            String addedByLabel = "";
            if (entry.getCreatedByName() != null && !entry.getCreatedByName().isEmpty()) {
                String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
                if (uid.equals(entry.getCreatedBy())) {
                    addedByLabel = "by You";
                } else {
                    addedByLabel = "by " + entry.getCreatedByName();
                }
            }
            if (holder.txtAddedBy != null) {
                if (!addedByLabel.isEmpty()) {
                    holder.txtAddedBy.setText(addedByLabel);
                    holder.txtAddedBy.setVisibility(View.VISIBLE);
                } else {
                    holder.txtAddedBy.setVisibility(View.GONE);
                }
            }
            
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

            if (isSharedGroup) {
                holder.txtRunningBalance.setVisibility(View.GONE);
                holder.txtAddedBy.setVisibility(View.GONE);
                
                String payerName = "Someone";
                if (entry.getPaidBy() != null) {
                    if (entry.getPaidBy().equals(currentUserId)) payerName = "You";
                    else if (resolvedUserNames.containsKey(entry.getPaidBy())) payerName = resolvedUserNames.get(entry.getPaidBy());
                } else if (entry.getCreatedBy() != null) {
                    if (entry.getCreatedBy().equals(currentUserId)) payerName = "You";
                    else if (resolvedUserNames.containsKey(entry.getCreatedBy())) payerName = resolvedUserNames.get(entry.getCreatedBy());
                }

                if ("EXPENSE".equalsIgnoreCase(entry.getType()) || entry.getSplits() != null) {
                    holder.txtCategory.setText(payerName + " paid ₹" + String.format(Locale.getDefault(), "%.2f", entry.getAmount()));
                    
                    double myStake = 0;
                    if (entry.getSplits() != null && entry.getSplits().containsKey(currentUserId)) {
                        myStake = entry.getSplits().get(currentUserId);
                    } else if (entry.getSplits() == null) {
                        myStake = entry.getAmount(); 
                    }
                    
                    if (payerName.equals("You")) {
                        double lent = entry.getAmount() - myStake;
                        if (lent > 0) {
                            holder.txtAmount.setText("+" + String.format(Locale.getDefault(), "₹%.2f", lent));
                            holder.txtAmount.setTextColor(getResources().getColor(R.color.accent_positive));
                        } else {
                            holder.txtAmount.setText("₹0.00");
                            holder.txtAmount.setTextColor(getResources().getColor(R.color.text_secondary));
                        }
                    } else {
                        if (myStake > 0) {
                            holder.txtAmount.setText("-" + String.format(Locale.getDefault(), "₹%.2f", myStake));
                            holder.txtAmount.setTextColor(getResources().getColor(R.color.accent_negative));
                        } else {
                            holder.txtAmount.setText("₹0.00");
                            holder.txtAmount.setTextColor(getResources().getColor(R.color.text_secondary));
                        }
                    }
                } else if ("SETTLEMENT".equalsIgnoreCase(entry.getType())) {
                    String receiverName = "Someone";
                    if (entry.getParticipants() != null && !entry.getParticipants().isEmpty()) {
                        String recUid = entry.getParticipants().get(0);
                        if (recUid.equals(currentUserId)) receiverName = "You";
                        else if (resolvedUserNames.containsKey(recUid)) receiverName = resolvedUserNames.get(recUid);
                    }
                    holder.txtCategory.setText(payerName + " paid " + receiverName);
                    
                    if (payerName.equals("You")) {
                        holder.txtAmount.setText("-" + String.format(Locale.getDefault(), "₹%.2f", entry.getAmount()));
                        holder.txtAmount.setTextColor(getResources().getColor(R.color.text_primary));
                    } else if (receiverName.equals("You")) {
                        holder.txtAmount.setText("+" + String.format(Locale.getDefault(), "₹%.2f", entry.getAmount()));
                        holder.txtAmount.setTextColor(getResources().getColor(R.color.accent_positive));
                    } else {
                        holder.txtAmount.setText(String.format(Locale.getDefault(), "₹%.2f", entry.getAmount()));
                        holder.txtAmount.setTextColor(getResources().getColor(R.color.text_secondary));
                    }
                } else {
                    holder.txtCategory.setText(category);
                    holder.txtAmount.setText(String.format(Locale.getDefault(), "₹%.2f", entry.getAmount()));
                    holder.txtAmount.setTextColor(getResources().getColor(R.color.text_primary));
                }
                
                String mediumText = "CASH".equalsIgnoreCase(entry.getMedium()) ? "💵 Cash" : "🏦 Bank";
                holder.txtMedium.setText(mediumText);

            } else {
                // Personal Ledger Logic
                holder.txtCategory.setText(category);
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
            if (entry.getBillImageUrl() != null && !entry.getBillImageUrl().isEmpty()) {
                options.add("View Receipt");
            }
            if (isUdhaar) options.add("Send WhatsApp Reminder");
            options.add("Create App Reminder");
            if (!"SETTLEMENT".equals(entry.getType())) {
                options.add("Edit Transaction");
                options.add("Duplicate Transaction");
            }
            options.add("Delete Transaction");

            new AlertDialog.Builder(LedgerBookDetailActivity.this)
                    .setTitle("Transaction Options")
                    .setItems(options.toArray(new String[0]), (dialog, which) -> {
                        String selected = options.get(which);
                        if ("View Receipt".equals(selected)) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(android.net.Uri.parse(entry.getBillImageUrl()));
                            startActivity(intent);
                        } else if ("Send WhatsApp Reminder".equals(selected)) {
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
                            boolean isShared = isSharedGroup || ("EXPENSE".equals(entry.getType()) && entry.getSplits() != null && !entry.getSplits().isEmpty());
                            Intent intent = new Intent(LedgerBookDetailActivity.this, isShared ? AddSharedExpenseActivity.class : AddCashbookEntryActivity.class);
                            intent.putExtra("BOOK_ID", bookId);
                            intent.putExtra("ENTRY_ID", entry.getId());
                            intent.putExtra("IS_EDIT_MODE", true);
                            startActivity(intent);
                        } else if ("Duplicate Transaction".equals(selected)) {
                            boolean isShared = isSharedGroup || ("EXPENSE".equals(entry.getType()) && entry.getSplits() != null && !entry.getSplits().isEmpty());
                            Intent intent = new Intent(LedgerBookDetailActivity.this, isShared ? AddSharedExpenseActivity.class : AddCashbookEntryActivity.class);
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

            String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
            
            String msg = "";
            if (isSharedGroup) {
                String payerName = "Someone";
                if (entry.getPaidBy() != null) {
                    payerName = entry.getPaidBy().equals(currentUserId) ? "You" : resolvedUserNames.getOrDefault(entry.getPaidBy(), "Unknown");
                } else if (entry.getCreatedBy() != null) {
                    payerName = entry.getCreatedBy().equals(currentUserId) ? "You" : resolvedUserNames.getOrDefault(entry.getCreatedBy(), "Unknown");
                }

                msg += "Amount: ₹" + String.format(Locale.getDefault(), "%.2f", entry.getAmount()) + "\n";
                msg += "Category: " + (entry.getParticulars() != null && !entry.getParticulars().isEmpty() ? entry.getParticulars() : entry.getCategory()) + "\n";
                msg += "Date: " + dateStr + "\n";
                msg += "Paid by: " + payerName + "\n\n";

                if ("EXPENSE".equalsIgnoreCase(entry.getType()) || entry.getSplits() != null) {
                    msg += "--- SPLIT DETAILS ---\n";
                    if (entry.getSplits() != null && !entry.getSplits().isEmpty()) {
                        for (Map.Entry<String, Double> split : entry.getSplits().entrySet()) {
                            String name = split.getKey().equals(currentUserId) ? "You" : resolvedUserNames.getOrDefault(split.getKey(), "Unknown Member");
                            msg += name + " owes ₹" + String.format(Locale.getDefault(), "%.2f", split.getValue()) + "\n";
                        }
                    } else {
                        msg += "Split equally (Legacy Entry)\n";
                    }
                } else if ("SETTLEMENT".equalsIgnoreCase(entry.getType())) {
                    msg += "This is a settlement transaction.\n";
                }
            } else {
                String addedBy = entry.getCreatedByName() != null && !entry.getCreatedByName().isEmpty() ? 
                    (currentUserId.equals(entry.getCreatedBy()) ? "You" : entry.getCreatedByName()) : "Unknown";
                
                msg += "Amount: ₹" + entry.getAmount() + "\n" +
                       "Type: " + entry.getType() + "\n" +
                       "Category: " + (entry.getCategory() != null ? entry.getCategory() : "Other") + "\n" +
                       "Medium: " + entry.getMedium() + "\n" +
                       "Date: " + dateStr + "\n" +
                       "Particulars: " + entry.getParticulars() + "\n" +
                       "Added by: " + addedBy + "\n";
                             
                if (entry.getContactName() != null && !entry.getContactName().isEmpty()) {
                    msg += "Contact: " + entry.getContactName() + "\n";
                }
                Double rb = runningBalances.get(entry.getId());
                if (rb != null) {
                    msg += "Running Balance after this: ₹" + String.format(Locale.getDefault(), "%.2f", rb) + "\n";
                }
            }
            
            androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(LedgerBookDetailActivity.this)
                .setTitle("Transaction Details")
                .setMessage(msg)
                .setPositiveButton("Close", null);
                
            if (!"VIEWER".equalsIgnoreCase(userRole) && !"SETTLEMENT".equalsIgnoreCase(entry.getType())) {
                builder.setNeutralButton("Options", (dialog, which) -> {
                    showActionDialog(entry);
                });
                builder.setNegativeButton("Edit", (dialog, which) -> {
                    boolean isShared = isSharedGroup || ("EXPENSE".equals(entry.getType()) && entry.getSplits() != null && !entry.getSplits().isEmpty());
                    Intent intent = new Intent(LedgerBookDetailActivity.this, isShared ? AddSharedExpenseActivity.class : AddCashbookEntryActivity.class);
                    intent.putExtra("BOOK_ID", bookId);
                    intent.putExtra("ENTRY_ID", entry.getId());
                    intent.putExtra("IS_EDIT_MODE", true);
                    startActivity(intent);
                });
            }
            builder.show();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtIcon, txtParticulars, txtAddedBy, txtDate, txtCategory, txtAmount, txtMedium, txtRunningBalance;
            android.widget.ImageButton btnOptions;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtIcon = itemView.findViewById(R.id.txtEntryIcon);
                txtParticulars = itemView.findViewById(R.id.txtEntryParticulars);
                txtAddedBy = itemView.findViewById(R.id.txtEntryAddedBy); // may be null on older layouts
                txtDate = itemView.findViewById(R.id.txtEntryDate);
                txtCategory = itemView.findViewById(R.id.txtEntryCategory);
                txtAmount = itemView.findViewById(R.id.txtEntryAmount);
                txtMedium = itemView.findViewById(R.id.txtEntryMedium);
                txtRunningBalance = itemView.findViewById(R.id.txtRunningBalance);
                btnOptions = itemView.findViewById(R.id.btnEntryOptions);
            }
        }
    }

    // Feature 19: Manage Members dialog (admin only) — with real display names
    private void showManageMembersDialog() {
        db.collection("cashbooks").document(bookId).get()
            .addOnSuccessListener(doc -> {
                Map<String, Object> rawMembers = (Map<String, Object>) doc.get("members");
                if (rawMembers == null || rawMembers.isEmpty()) {
                    Toast.makeText(this, "No members found", Toast.LENGTH_SHORT).show();
                    return;
                }

                String currentUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
                List<String> memberUids = new ArrayList<>(rawMembers.keySet());

                // Batch-fetch real display names
                UserProfileHelper.resolveNames(db, memberUids, nameMap -> {
                    String[] labels = new String[memberUids.size()];
                    for (int i = 0; i < memberUids.size(); i++) {
                        String uid = memberUids.get(i);
                        String role = String.valueOf(rawMembers.get(uid));
                        String name = uid.equals(currentUid) ? "You" : nameMap.getOrDefault(uid, "User");
                        labels[i] = name + "  —  " + role;
                    }

                    new AlertDialog.Builder(this)
                        .setTitle("Members (" + memberUids.size() + ")")
                        .setItems(labels, (dialog, which) -> {
                            String selectedUid = memberUids.get(which);
                            if (selectedUid.equals(currentUid)) {
                                Toast.makeText(this, "You cannot change your own role", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String resolvedName = nameMap.getOrDefault(selectedUid, "User");
                            showChangeRoleDialog(selectedUid, String.valueOf(rawMembers.get(selectedUid)), resolvedName);
                        })
                        .setNegativeButton("Close", null)
                        .show();
                });
            })
            .addOnFailureListener(e -> Toast.makeText(this, "Failed to load members: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showAddOfflineMemberDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Offline Member Name");
        
        new AlertDialog.Builder(this)
            .setTitle("Add Offline Member")
            .setView(input)
            .setPositiveButton("Add", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) return;
                
                String fakeUid = "offline_" + System.currentTimeMillis();
                
                // 1. Create fake user in users collection
                java.util.Map<String, Object> fakeUser = new java.util.HashMap<>();
                fakeUser.put("displayName", name + " (Offline)");
                fakeUser.put("isOffline", true);
                
                db.collection("users").document(fakeUid).set(fakeUser)
                    .addOnSuccessListener(aVoid -> {
                        // 2. Add to group
                        db.collection("cashbooks").document(bookId)
                            .update("members." + fakeUid, "VIEWER")
                            .addOnSuccessListener(aVoid2 -> {
                                Toast.makeText(this, name + " added as offline member", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to add to group", Toast.LENGTH_SHORT).show());
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to create user", Toast.LENGTH_SHORT).show());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showChangeRoleDialog(String targetUid, String currentRole, String displayName) {
        String[] roles = {"VIEWER", "EDITOR", "ADMIN"};
        int checkedItem = 0;
        for (int i = 0; i < roles.length; i++) {
            if (roles[i].equalsIgnoreCase(currentRole)) { checkedItem = i; break; }
        }
        final int[] selected = {checkedItem};

        new AlertDialog.Builder(this)
            .setTitle("Change Role — " + displayName)
            .setSingleChoiceItems(roles, checkedItem, (dialog, which) -> selected[0] = which)
            .setPositiveButton("Save", (dialog, which) -> {
                String newRole = roles[selected[0]];
                db.collection("cashbooks").document(bookId)
                    .update("members." + targetUid, newRole)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Role updated to " + newRole, Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update role: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
            })
            .setNeutralButton("Remove Member", (dialog, which) -> {
                new AlertDialog.Builder(this)
                    .setTitle("Remove Member")
                    .setMessage("Remove " + displayName + " from the cashbook?")
                    .setPositiveButton("Remove", (d2, w2) -> {
                        db.collection("cashbooks").document(bookId)
                            .update("members." + targetUid, com.google.firebase.firestore.FieldValue.delete())
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, displayName + " removed", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ─── Pending join-request counter (admin only) ────────────────────────────

    private void listenForPendingCount() {
        if (pendingListener != null) pendingListener.remove();
        pendingListener = db.collection("cashbooks").document(bookId)
            .collection("pendingMembers")
            .addSnapshotListener((snapshots, e) -> {
                if (snapshots == null) return;
                int count = 0;
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                    if ("PENDING".equals(doc.getString("status"))) count++;
                }
                pendingCount = count;
            });
    }

    // ─── Leave Group (non-admin) ──────────────────────────────────────────────

    private void showLeaveGroupDialog() {
        String currentUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        if (currentUid.isEmpty()) return;

        new AlertDialog.Builder(this)
            .setTitle("Leave Group")
            .setMessage("Are you sure you want to leave \"" + bookName + "\"? You will lose access to all entries.")
            .setPositiveButton("Leave", (dialog, which) -> {
                db.collection("cashbooks").document(bookId)
                    .update("members." + currentUid, com.google.firebase.firestore.FieldValue.delete())
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "You have left \"" + bookName + "\"", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(ex ->
                        Toast.makeText(this, "Failed to leave: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ─── Admin leaves — promote oldest member or delete ───────────────────────

    private void showAdminLeaveGroupDialog() {
        String currentUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        if (currentUid.isEmpty()) return;

        db.collection("cashbooks").document(bookId).get().addOnSuccessListener(doc -> {
            Map<String, Object> members = (Map<String, Object>) doc.get("members");
            if (members == null) { finish(); return; }

            // Remove current admin from the list to find others
            List<String> others = new ArrayList<>(members.keySet());
            others.remove(currentUid);

            if (others.isEmpty()) {
                // Admin is the only member — delete the cashbook
                new AlertDialog.Builder(this)
                    .setTitle("Delete Cashbook")
                    .setMessage("You are the only member. Leaving will permanently delete \"" + bookName + "\". Continue?")
                    .setPositiveButton("Delete", (d, w) -> {
                        db.collection("cashbooks").document(bookId).delete()
                            .addOnSuccessListener(aVoid -> { finish(); })
                            .addOnFailureListener(ex -> Toast.makeText(this, "Failed: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            } else {
                // Promote the first other member to ADMIN, then leave
                new AlertDialog.Builder(this)
                    .setTitle("Leave Group")
                    .setMessage("You are the Admin. Leaving will promote another member to Admin. Continue?")
                    .setPositiveButton("Leave & Promote", (d, w) -> {
                        String newAdmin = others.get(0);
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("members." + newAdmin, "ADMIN");
                        updates.put("members." + currentUid, com.google.firebase.firestore.FieldValue.delete());
                        updates.put("ownerId", newAdmin);
                        db.collection("cashbooks").document(bookId).update(updates)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Left group. Another member is now Admin.", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(ex -> Toast.makeText(this, "Failed: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
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
