package com.example.frienddebt.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.view.MenuItem;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.dsa.DebtCalculator;
import com.example.frienddebt.model.Group;
import com.example.frienddebt.model.Payment;
import com.example.frienddebt.model.SettlementSuggestion;
import com.example.frienddebt.model.Transaction;
import com.example.frienddebt.model.User;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupDetailActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "GROUP_ID";
    public static final String EXTRA_OWNER_ID = "OWNER_ID";

    private Group group;
    private RecyclerView rvTransactions;
    private TransactionsAdapter adapter;
    private TextView txtGroupName, txtTransactionCount, txtInviteCode;
    private TextView txtTotalExpense, txtSettleUpSummary;
    private Button btnSettleUp, btnExportPdf;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private String groupId;
    private String ownerId;

    private final List<Transaction> transactions = new ArrayList<>();
    private final List<Payment> payments = new ArrayList<>();
    private final Map<String, User> userCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        txtGroupName = findViewById(R.id.txtGroupName);
        txtInviteCode = findViewById(R.id.txtInviteCode);
        txtTransactionCount = findViewById(R.id.txtTransactionCount);
        txtTotalExpense = findViewById(R.id.txtTotalExpense);
        txtSettleUpSummary = findViewById(R.id.txtSettleUpSummary);
        rvTransactions = findViewById(R.id.rvTransactions);
        btnSettleUp = findViewById(R.id.btnSettleUp);
        btnExportPdf = findViewById(R.id.btnExportPdf);

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvTransactions.setNestedScrollingEnabled(false);

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        ownerId = getIntent().getStringExtra(EXTRA_OWNER_ID);

        if (groupId == null || ownerId == null) {
            Toast.makeText(this, "Group info missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(groupId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Group not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    String name = doc.getString("name");
                    String inviteCode = doc.getString("inviteCode");

                    group = new Group(groupId, name, ownerId);

                    txtGroupName.setText(name);
                    txtInviteCode.setText("Invite Code: " + inviteCode);
                    if (getSupportActionBar() != null) getSupportActionBar().setTitle(name);

                    adapter = new TransactionsAdapter(new ArrayList<>());
                    rvTransactions.setAdapter(adapter);

                    setupButtons();
                    loadExpensesFromFirestore();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private User getOrCreateUser(String name) {
        if (name == null) return null;
        String cacheKey = name.trim().toLowerCase();
        if (!userCache.containsKey(cacheKey)) {
            userCache.put(cacheKey, new User(name.trim()));
        }
        return userCache.get(cacheKey);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_group_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_rename_group) {
            showRenameGroupDialog();
            return true;
        } else if (id == R.id.action_delete_group) {
            showDeleteGroupDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showRenameGroupDialog() {
        if (auth.getCurrentUser() == null || !auth.getCurrentUser().getUid().equals(ownerId)) {
            Toast.makeText(this, "Only the group owner can rename the group", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText input = new EditText(this);
        input.setHint("Group name");
        input.setText(group != null ? group.getName() : "");
        input.setSelectAllOnFocus(true);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle("Rename Group")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        renameGroup(newName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void renameGroup(String newName) {
        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(groupId)
                .update("name", newName)
                .addOnSuccessListener(aVoid -> {
                    group = new Group(groupId, newName, ownerId);
                    txtGroupName.setText(newName);
                    if (getSupportActionBar() != null) getSupportActionBar().setTitle(newName);
                    Toast.makeText(this, "Group renamed", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showDeleteGroupDialog() {
        if (auth.getCurrentUser() == null || !auth.getCurrentUser().getUid().equals(ownerId)) {
            Toast.makeText(this, "Only the group owner can delete the group", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Group")
                .setMessage("Are you sure? This will permanently delete the group and all its expenses and payments.")
                .setPositiveButton("Delete", (dialog, which) -> deleteGroup())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteGroup() {
        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(groupId)
                .collection("expenses")
                .get()
                .addOnSuccessListener(expenseSnapshot -> {
                    WriteBatch batch = db.batch();

                    for (QueryDocumentSnapshot doc : expenseSnapshot) {
                        batch.delete(doc.getReference());
                    }

                    db.collection("users")
                            .document(ownerId)
                            .collection("groups")
                            .document(groupId)
                            .collection("payments")
                            .get()
                            .addOnSuccessListener(paymentSnapshot -> {
                                for (QueryDocumentSnapshot doc : paymentSnapshot) {
                                    batch.delete(doc.getReference());
                                }
                                batch.delete(db.collection("users")
                                        .document(ownerId)
                                        .collection("groups")
                                        .document(groupId));
                                batch.commit()
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "Group deleted", Toast.LENGTH_SHORT).show();
                                            finish();
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void setupButtons() {
        btnSettleUp.setOnClickListener(view -> {
            Intent i = new Intent(GroupDetailActivity.this, SettlementSuggestionsActivity.class);
            i.putExtra(EXTRA_GROUP_ID, groupId);
            i.putExtra(EXTRA_OWNER_ID, ownerId);
            startActivity(i);
        });

        btnExportPdf.setOnClickListener(v -> exportToPdf());

        findViewById(R.id.fabAddExpense).setOnClickListener(v -> {
            Intent add = new Intent(GroupDetailActivity.this, AddExpenseActivity.class);
            add.putExtra(EXTRA_GROUP_ID, groupId);
            add.putExtra(EXTRA_OWNER_ID, ownerId);
            startActivity(add);
        });
    }

    private void loadExpensesFromFirestore() {
        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(groupId)
                .collection("expenses")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }

                    transactions.clear();
                    userCache.clear();

                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Double amount = doc.getDouble("amount");
                            String paidBy = doc.getString("paidBy");
                            List<String> participants = (List<String>) doc.get("participants");
                            String desc = doc.getString("description");

                            if (paidBy == null || participants == null) continue;

                            User payer = getOrCreateUser(paidBy);

                            List<User> users = new ArrayList<>();
                            for (String p : participants) {
                                users.add(getOrCreateUser(p));
                            }

                            Long updatedAtVal = doc.getLong("updatedAt");
                            long txTimestamp = (updatedAtVal != null) ? updatedAtVal : System.currentTimeMillis();

                            Transaction t = new Transaction(
                                    group,
                                    payer,
                                    users,
                                    amount != null ? amount : 0,
                                    desc != null ? desc : "",
                                    txTimestamp
                            );

                            t.setFirestoreId(doc.getId());
                            transactions.add(t);
                        }
                    }

                    adapter.setTransactions(new ArrayList<>(transactions));
                    txtTransactionCount.setText(transactions.size() + " transactions");

                    db.collection("users")
                            .document(ownerId)
                            .collection("groups")
                            .document(groupId)
                            .collection("payments")
                            .get()
                            .addOnSuccessListener(paymentSnapshot -> {
                                payments.clear();
                                for (QueryDocumentSnapshot doc : paymentSnapshot) {
                                    Double amount = doc.getDouble("amount");
                                    String fromName = doc.getString("fromName");
                                    String toName = doc.getString("toName");
                                    Long ts = doc.getLong("timestamp");
                                    if (fromName == null || toName == null || amount == null) continue;
                                    User from = getOrCreateUser(fromName);
                                    User to = getOrCreateUser(toName);
                                    Payment p = new Payment(from, to, amount, ts != null ? ts : System.currentTimeMillis());
                                    payments.add(p);
                                }
                                updateTotalExpenseAndSettlement();
                            })
                            .addOnFailureListener(err -> updateTotalExpenseAndSettlement());
                });
    }

    private void updateTotalExpenseAndSettlement() {
        double total = 0;
        for (Transaction t : transactions) {
            total += t.getAmount();
        }
        txtTotalExpense.setText("Total Expense: ₹" + String.format("%.2f", total));

        List<SettlementSuggestion> suggestions =
                DebtCalculator.buildSettlementSuggestionsFromTransactionsAndPayments(transactions, payments);

        if (suggestions == null || suggestions.isEmpty()) {
            txtSettleUpSummary.setText("Everyone is settled up!");
        } else {
            StringBuilder sb = new StringBuilder();
            for (SettlementSuggestion s : suggestions) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(s.getFrom().getName())
                        .append(" should pay ")
                        .append(s.getTo().getName())
                        .append(" ₹")
                        .append(String.format("%.2f", s.getAmount()));
            }
            txtSettleUpSummary.setText(sb.toString());
        }
    }

    private void exportToPdf() {
        try {
            String groupName = group != null ? group.getName() : "Group";
            double total = 0;
            for (Transaction t : transactions) total += t.getAmount();

            List<SettlementSuggestion> suggestions =
                    DebtCalculator.buildSettlementSuggestionsFromTransactionsAndPayments(transactions, payments);

            PdfDocument pdfDocument = new PdfDocument();
            Paint paint = new Paint();
            paint.setTextSize(12);
            Paint titlePaint = new Paint();
            titlePaint.setTextSize(16);
            titlePaint.setFakeBoldText(true);

            int pageWidth = 595;
            int pageHeight = 842;
            int margin = 40;
            int y = margin;

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            canvas.drawText("Group: " + groupName, margin, y, titlePaint);
            y += 30;

            canvas.drawText("Total Expense: ₹" + String.format("%.2f", total), margin, y, paint);
            y += 25;

            canvas.drawText("Expenses:", margin, y, titlePaint);
            y += 20;

            for (Transaction t : transactions) {
                String desc = (t.getDescription() != null && !t.getDescription().isEmpty())
                        ? t.getDescription() : "Expense";
                String payerName = t.getPaidBy() != null ? t.getPaidBy().getName() : "Unknown";
                canvas.drawText(payerName + " paid ₹" + String.format("%.2f", t.getAmount()) + " (" + desc + ")", margin + 10, y, paint);
                y += 16;
                List<User> parts = t.getSharedWith();
                StringBuilder sb = new StringBuilder();
                if (parts != null) {
                    for (User u : parts) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(u.getName());
                    }
                }
                canvas.drawText("Participants: " + sb.toString(), margin + 20, y, paint);
                y += 24;
            }

            y += 10;
            canvas.drawText("Settle Up Summary:", margin, y, titlePaint);
            y += 20;

            if (suggestions == null || suggestions.isEmpty()) {
                canvas.drawText("Everyone is settled up!", margin + 10, y, paint);
            } else {
                for (SettlementSuggestion s : suggestions) {
                    canvas.drawText(s.getFrom().getName() + " should pay " + s.getTo().getName() + " ₹" + String.format("%.2f", s.getAmount()), margin + 10, y, paint);
                    y += 18;
                }
            }

            pdfDocument.finishPage(page);

            File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    "Group_" + groupName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".pdf");
            file.getParentFile().mkdirs();
            pdfDocument.writeTo(new FileOutputStream(file));
            pdfDocument.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Expense Summary - " + groupName);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Save or share PDF"));

            Toast.makeText(this, "PDF created successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException ex) {
            Log.e("GroupDetail", "PDF export failed", ex);
            Toast.makeText(this, "Failed to create PDF: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private class TransactionsAdapter extends RecyclerView.Adapter<TransactionsAdapter.VH> {

        List<Transaction> transactions = new ArrayList<>();

        TransactionsAdapter(List<Transaction> initial) {
            if (initial != null) transactions.addAll(initial);
        }

        void setTransactions(List<Transaction> txs) {
            transactions.clear();
            if (txs != null) transactions.addAll(txs);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final Transaction tx = transactions.get(position);

            holder.txtTitle.setText(
                    tx.getDescription() != null && !tx.getDescription().isEmpty()
                            ? tx.getDescription()
                            : "Expense"
            );

            String payerName = (tx.getPaidBy() != null ? tx.getPaidBy().getName() : "Unknown");
            String amount = String.format("₹%.2f", tx.getAmount());
            holder.txtAmountLine.setText(payerName + " • " + amount);

            List<User> parts = tx.getSharedWith();
            StringBuilder sb = new StringBuilder();
            if (parts != null) {
                for (User u : parts) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(u.getName());
                }
            }
            holder.txtParticipants.setText(sb.toString());

            holder.btnEdit.setOnClickListener(v -> {
                Intent i = new Intent(GroupDetailActivity.this, AddExpenseActivity.class);
                i.putExtra(EXTRA_GROUP_ID, groupId);
                i.putExtra(EXTRA_OWNER_ID, ownerId);
                i.putExtra("EXPENSE_ID", tx.getFirestoreId());
                startActivity(i);
            });

            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(GroupDetailActivity.this)
                        .setTitle("Delete expense")
                        .setMessage("Are you sure you want to delete this expense?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            db.collection("users")
                                    .document(ownerId)   // 🔥 OWNER PATH
                                    .collection("groups")
                                    .document(groupId)
                                    .collection("expenses")
                                    .document(tx.getFirestoreId())
                                    .delete()
                                    .addOnSuccessListener(aVoid ->
                                            Toast.makeText(GroupDetailActivity.this, "Deleted", Toast.LENGTH_SHORT).show()
                                    )
                                    .addOnFailureListener(e ->
                                            Toast.makeText(GroupDetailActivity.this, e.getMessage(), Toast.LENGTH_LONG).show()
                                    );
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return transactions.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView txtTitle, txtAmountLine, txtParticipants;
            ImageButton btnEdit, btnDelete;

            VH(@NonNull View itemView) {
                super(itemView);
                txtTitle = itemView.findViewById(R.id.txtTxTitle);
                txtAmountLine = itemView.findViewById(R.id.txtTxAmountLine);
                txtParticipants = itemView.findViewById(R.id.txtTxParticipants);
                btnEdit = itemView.findViewById(R.id.btnEditTx);
                btnDelete = itemView.findViewById(R.id.btnDeleteTx);
            }
        }
    }
}
