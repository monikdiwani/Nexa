package com.example.frienddebt.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.dsa.DebtSimplifier;
import com.example.frienddebt.model.CashbookEntry;
import com.example.frienddebt.model.DebtEdge;
import com.example.frienddebt.model.LedgerBook;
import com.example.frienddebt.utils.StatusBarUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SettleUpActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private Spinner spinnerLedger;
    private RecyclerView rvSettlements;
    private TextView txtSettledUp;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String currentUserId;

    private List<LedgerBook> sharedLedgers = new ArrayList<>();
    private LedgerBook selectedLedger;
    private List<DebtEdge> suggestedSettlements = new ArrayList<>();
    private SettlementAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settle_up);
        StatusBarUtil.applyStatusBarPadding(this);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
        }

        btnBack = findViewById(R.id.btnBack);
        spinnerLedger = findViewById(R.id.spinnerLedger);
        rvSettlements = findViewById(R.id.rvSettlements);
        txtSettledUp = findViewById(R.id.txtSettledUp);

        btnBack.setOnClickListener(v -> finish());

        rvSettlements.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SettlementAdapter();
        rvSettlements.setAdapter(adapter);

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
                        if (book.getMembers() != null && book.getMembers().size() > 1) {
                            sharedLedgers.add(book);
                            ledgerNames.add(book.getName());
                        }
                    }

                    if (sharedLedgers.isEmpty()) {
                        Toast.makeText(this, "No shared groups found.", Toast.LENGTH_LONG).show();
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
                            loadEntriesForLedger(selectedLedger.getId());
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });
                });
    }

    private void loadEntriesForLedger(String ledgerId) {
        db.collection("cashbooks").document(ledgerId)
                .collection("entries")
                .orderBy("date", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;

                    List<CashbookEntry> entries = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots) {
                        entries.add(CashbookEntry.fromDocument(doc));
                    }

                    runDebtSimplification(entries);
                });
    }

    private void runDebtSimplification(List<CashbookEntry> entries) {
        suggestedSettlements = DebtSimplifier.simplifyDebts(entries);
        adapter.notifyDataSetChanged();

        if (suggestedSettlements.isEmpty()) {
            txtSettledUp.setVisibility(View.VISIBLE);
            rvSettlements.setVisibility(View.GONE);
        } else {
            txtSettledUp.setVisibility(View.GONE);
            rvSettlements.setVisibility(View.VISIBLE);
        }
    }

    private void markAsPaid(DebtEdge edge) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Settlement")
                .setMessage("Record a payment of ₹" + String.format(Locale.getDefault(), "%.2f", edge.getAmount()) + " from " + resolveName(edge.getFrom()) + " to " + resolveName(edge.getTo()) + "?")
                .setPositiveButton("Confirm", (dialog, which) -> processPayment(edge))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void processPayment(DebtEdge edge) {
        String entryId = UUID.randomUUID().toString();
        CashbookEntry entry = new CashbookEntry(
                entryId,
                selectedLedger.getId(),
                System.currentTimeMillis(),
                "Settlement",
                "SETTLEMENT",
                "CASH",
                edge.getAmount(),
                "Settlement",
                "Automated settlement",
                System.currentTimeMillis()
        );

        entry.setCreatedBy(currentUserId);
        entry.setPaidBy(edge.getFrom()); // Debtor pays
        
        List<String> participants = new ArrayList<>();
        participants.add(edge.getTo()); // Creditor receives
        entry.setParticipants(participants);

        db.collection("cashbooks").document(selectedLedger.getId())
                .collection("entries").document(entryId)
                .set(entry.toFirestoreMap())
                .addOnSuccessListener(aVoid -> {
                    // Write audit log in background
                    String actorName = resolveName(currentUserId);
                    String logId = db.collection("cashbooks").document(selectedLedger.getId()).collection("logs").document().getId();
                    String logDetails = actorName + " settled ₹" + String.format(Locale.getDefault(), "%.2f", edge.getAmount()) + " from " + resolveName(edge.getFrom()) + " to " + resolveName(edge.getTo());

                    com.example.frienddebt.model.AuditLog audit = new com.example.frienddebt.model.AuditLog(
                            logId, selectedLedger.getId(), "SETTLE", currentUserId, actorName, "Settlement", edge.getAmount(), "SETTLEMENT", System.currentTimeMillis(), logDetails
                    );
                    db.collection("cashbooks").document(selectedLedger.getId()).collection("logs").document(logId).set(audit.toFirestoreMap())
                            .addOnCompleteListener(task -> {
                                Toast.makeText(this, "Settlement Recorded!", Toast.LENGTH_SHORT).show();
                            });
                });
    }

    private String resolveName(String userId) {
        if (userId.equals(currentUserId)) return "You";
        // Simple fallback until a full users collection resolver is built
        return "User (" + userId.substring(0, 4) + ")";
    }

    private class SettlementAdapter extends RecyclerView.Adapter<SettlementAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_settlement, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DebtEdge edge = suggestedSettlements.get(position);

            holder.txtDebtor.setText(resolveName(edge.getFrom()));
            holder.txtCreditor.setText(resolveName(edge.getTo()));
            holder.txtAmount.setText(String.format(Locale.getDefault(), "₹%.2f", edge.getAmount()));

            // Only allow the person who owes the money (or the receiver) to mark it as paid ideally.
            // For now, anyone in the group can mark a settlement.
            holder.btnMarkPaid.setOnClickListener(v -> markAsPaid(edge));
        }

        @Override
        public int getItemCount() {
            return suggestedSettlements.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtDebtor, txtCreditor, txtAmount;
            Button btnMarkPaid;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtDebtor = itemView.findViewById(R.id.txtDebtor);
                txtCreditor = itemView.findViewById(R.id.txtCreditor);
                txtAmount = itemView.findViewById(R.id.txtAmount);
                btnMarkPaid = itemView.findViewById(R.id.btnMarkPaid);
            }
        }
    }
}
