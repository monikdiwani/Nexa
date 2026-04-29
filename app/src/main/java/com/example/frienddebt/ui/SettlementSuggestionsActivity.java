package com.example.frienddebt.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.frienddebt.R;
import com.example.frienddebt.dsa.DebtCalculator;
import com.example.frienddebt.model.Group;
import com.example.frienddebt.model.Payment;
import com.example.frienddebt.model.SettlementSuggestion;
import com.example.frienddebt.model.Transaction;
import com.example.frienddebt.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettlementSuggestionsActivity extends AppCompatActivity {

    private RecyclerView recyclerViewSuggestions;
    private View emptyStateLayout;
    private MaterialToolbar toolbar;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private String groupId;
    private String ownerId;
    private Group group;

    private final List<Transaction> transactions = new ArrayList<>();
    private final List<Payment> payments = new ArrayList<>();
    private final Map<String, User> userCache = new HashMap<>();

    private SettlementAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settlement_suggestions);

        recyclerViewSuggestions = findViewById(R.id.recyclerViewSuggestions);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        toolbar = findViewById(R.id.toolbar);
        
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        groupId = getIntent().getStringExtra("GROUP_ID");
        ownerId = getIntent().getStringExtra("OWNER_ID");

        if (groupId == null || groupId.isEmpty()) {
            Toast.makeText(this, "Group not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                    : null;
        }

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        recyclerViewSuggestions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SettlementAdapter(new ArrayList<>(), this::onMarkAsPaid);
        recyclerViewSuggestions.setAdapter(adapter);

        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(groupId)
                .get()
                .addOnSuccessListener(doc -> {
                    String name = doc.exists() && doc.getString("name") != null
                            ? doc.getString("name") : "Group";
                    group = new Group(name);
                    loadExpensesAndPayments();
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

    private void loadExpensesAndPayments() {
        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(groupId)
                .collection("expenses")
                .get()
                .addOnSuccessListener(expenseSnapshot -> {
                    transactions.clear();
                    userCache.clear();

                    for (QueryDocumentSnapshot doc : expenseSnapshot) {
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
                        transactions.add(t);
                    }

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
                                    p.setId(doc.getId());
                                    payments.add(p);
                                }
                                showSettlementSuggestions();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to load payments: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void onMarkAsPaid(SettlementSuggestion suggestion) {
        Payment payment = new Payment(
                suggestion.getFrom(),
                suggestion.getTo(),
                suggestion.getAmount(),
                System.currentTimeMillis()
        );

        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(groupId)
                .collection("payments")
                .add(new HashMap<String, Object>() {{
                    put("fromName", suggestion.getFrom().getName());
                    put("toName", suggestion.getTo().getName());
                    put("amount", suggestion.getAmount());
                    put("timestamp", System.currentTimeMillis());
                }})
                .addOnSuccessListener(ref -> {
                    loadExpensesAndPayments();
                    
                    Snackbar snackbar = Snackbar.make(recyclerViewSuggestions, "Payment settled!", Snackbar.LENGTH_LONG);
                    snackbar.setAction("UNDO", v -> {
                        // Undo the settlement
                        ref.delete().addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Settlement undone", Toast.LENGTH_SHORT).show();
                            loadExpensesAndPayments();
                        });
                    });
                    snackbar.show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showSettlementSuggestions() {
        List<SettlementSuggestion> suggestions =
                DebtCalculator.buildSettlementSuggestionsFromTransactionsAndPayments(transactions, payments);

        if (suggestions == null || suggestions.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            recyclerViewSuggestions.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            recyclerViewSuggestions.setVisibility(View.VISIBLE);
            adapter.setSuggestions(suggestions);
        }
    }

    private static class SettlementAdapter extends RecyclerView.Adapter<SettlementAdapter.VH> {

        private List<SettlementSuggestion> suggestions = new ArrayList<>();
        private final OnMarkPaidListener listener;

        interface OnMarkPaidListener {
            void onMarkPaid(SettlementSuggestion s);
        }

        SettlementAdapter(List<SettlementSuggestion> initial, OnMarkPaidListener listener) {
            if (initial != null) suggestions.addAll(initial);
            this.listener = listener;
        }

        void setSuggestions(List<SettlementSuggestion> list) {
            suggestions.clear();
            if (list != null) suggestions.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_settlement_suggestion, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SettlementSuggestion s = suggestions.get(position);
            holder.txtSettlementFromTo.setText(s.getFrom().getName() + " → " + s.getTo().getName());
            holder.txtSettlementAmount.setText(String.format("₹%.2f", s.getAmount()));
            holder.btnMarkPaid.setOnClickListener(v -> {
                if (listener != null) listener.onMarkPaid(s);
            });
        }

        @Override
        public int getItemCount() {
            return suggestions.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView txtSettlementFromTo, txtSettlementAmount;
            Button btnMarkPaid;

            VH(@NonNull View itemView) {
                super(itemView);
                txtSettlementFromTo = itemView.findViewById(R.id.txtSettlementFromTo);
                txtSettlementAmount = itemView.findViewById(R.id.txtSettlementAmount);
                btnMarkPaid = itemView.findViewById(R.id.btnMarkPaid);
            }
        }
    }
}
