package com.example.frienddebt.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.dsa.CashbookCalculator;
import com.example.frienddebt.model.CashbookEntry;
import com.example.frienddebt.ui.AddCashbookEntryActivity;
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

/**
 * Personal sub-tab inside MoneyFragment.
 * Shows cashbook entries (income/expense) with filter chips.
 */
public class MoneyPersonalFragment extends Fragment {

    private TextView txtEmptyPersonal;
    private TextView chipAll, chipCash, chipBank, chipToday, chipWeek, chipMonth;
    private RecyclerView rvPersonalEntries;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration cashbookListener;

    private List<CashbookEntry> allEntries = new ArrayList<>();
    private List<CashbookEntry> filteredEntries = new ArrayList<>();
    private PersonalAdapter adapter;

    private String activeFilter = "ALL";

    // Callback to update the parent's balance card
    public interface BalanceUpdateListener {
        void onCashbookDataChanged(List<CashbookEntry> entries);
    }

    private BalanceUpdateListener balanceListener;

    public void setBalanceUpdateListener(BalanceUpdateListener listener) {
        this.balanceListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_money_personal, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        txtEmptyPersonal = view.findViewById(R.id.txtEmptyPersonal);
        rvPersonalEntries = view.findViewById(R.id.rvPersonalEntries);

        chipAll = view.findViewById(R.id.chipAll);
        chipCash = view.findViewById(R.id.chipCash);
        chipBank = view.findViewById(R.id.chipBank);
        chipToday = view.findViewById(R.id.chipToday);
        chipWeek = view.findViewById(R.id.chipWeek);
        chipMonth = view.findViewById(R.id.chipMonth);

        rvPersonalEntries.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PersonalAdapter(filteredEntries);
        rvPersonalEntries.setAdapter(adapter);

        setupFilters();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadData();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (cashbookListener != null) {
            cashbookListener.remove();
            cashbookListener = null;
        }
    }

    public void loadData() {
        if (auth == null || db == null) return;
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        if (cashbookListener != null) {
            cashbookListener.remove();
        }

        cashbookListener = db.collection("users")
                .document(userId)
                .collection("cashbook")
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;
                    allEntries.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        allEntries.add(CashbookEntry.fromDocument(doc));
                    }
                    applyFilter();

                    // Notify parent about data changes for balance card
                    if (balanceListener != null) {
                        balanceListener.onCashbookDataChanged(allEntries);
                    }
                });
    }

    private void setupFilters() {
        chipAll.setOnClickListener(v -> setFilter("ALL", chipAll));
        chipCash.setOnClickListener(v -> setFilter("CASH", chipCash));
        chipBank.setOnClickListener(v -> setFilter("BANK", chipBank));
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
            case "CASH":
                filteredEntries.addAll(CashbookCalculator.filterByMedium(allEntries, "CASH"));
                break;
            case "BANK":
                filteredEntries.addAll(CashbookCalculator.filterByMedium(allEntries, "BANK"));
                break;
            case "TODAY":
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOfDay = cal.getTimeInMillis();
                filteredEntries.addAll(CashbookCalculator.filterByDateRange(allEntries, startOfDay, now));
                break;
            case "WEEK":
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOfWeek = cal.getTimeInMillis();
                filteredEntries.addAll(CashbookCalculator.filterByDateRange(allEntries, startOfWeek, now));
                break;
            case "MONTH":
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOfMonth = cal.getTimeInMillis();
                filteredEntries.addAll(CashbookCalculator.filterByDateRange(allEntries, startOfMonth, now));
                break;
        }

        adapter.notifyDataSetChanged();

        if (filteredEntries.isEmpty()) {
            txtEmptyPersonal.setVisibility(View.VISIBLE);
            rvPersonalEntries.setVisibility(View.GONE);
        } else {
            txtEmptyPersonal.setVisibility(View.GONE);
            rvPersonalEntries.setVisibility(View.VISIBLE);
        }
    }

    // ─── Inner Adapter ─────────────────────────────────────────────
    private class PersonalAdapter extends RecyclerView.Adapter<PersonalAdapter.ViewHolder> {
        private final List<CashbookEntry> list;

        public PersonalAdapter(List<CashbookEntry> list) {
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

            String particulars = entry.getParticulars();
            holder.txtParticulars.setText(particulars != null ? particulars : "");

            String category = entry.getCategory();
            if (category == null || category.trim().isEmpty()) {
                category = "Other";
            }
            holder.txtCategory.setText(category);

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            holder.txtDate.setText(sdf.format(new Date(entry.getDate())));

            String emoji = "💵";
            switch (category) {
                case "Sales": emoji = "📈"; break;
                case "Rent": emoji = "🏠"; break;
                case "Salary": emoji = "💰"; break;
                case "Office": emoji = "🏢"; break;
                case "Personal": emoji = "👤"; break;
                case "Food": emoji = "🍔"; break;
                case "Transport": emoji = "🚗"; break;
                case "Shopping": emoji = "🛍️"; break;
                case "Bills": emoji = "📄"; break;
            }
            holder.txtIcon.setText(emoji);

            String prefix = "CASH_IN".equalsIgnoreCase(entry.getType()) ? "+" : "-";
            holder.txtAmount.setText(String.format(Locale.getDefault(), "%s₹%.2f", prefix, entry.getAmount()));

            int colorRes = "CASH_IN".equalsIgnoreCase(entry.getType()) ? R.color.accent_positive : R.color.accent_negative;
            holder.txtAmount.setTextColor(getResources().getColor(colorRes));

            String mediumText = "CASH".equalsIgnoreCase(entry.getMedium()) ? "💵 Cash" : "🏦 Bank";
            holder.txtMedium.setText(mediumText);

            holder.itemView.setOnLongClickListener(v -> {
                showDeleteDialog(entry);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        private void showDeleteDialog(CashbookEntry entry) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Transaction")
                    .setMessage("Are you sure you want to delete this transaction?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (auth.getCurrentUser() != null) {
                            db.collection("users")
                                    .document(auth.getCurrentUser().getUid())
                                    .collection("cashbook")
                                    .document(entry.getId())
                                    .delete();
                        }
                    })
                    .setNegativeButton("Cancel", null)
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
