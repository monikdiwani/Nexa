package com.example.frienddebt.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.frienddebt.R;
import com.example.frienddebt.model.CashbookEntry;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Overview sub-tab inside MoneyFragment.
 * Shows combined analytics: spending by category, group debts, recent activity, monthly summary.
 */
public class MoneyOverviewFragment extends Fragment {

    private LinearLayout layoutCategoryBars, layoutGroupDebts, layoutRecentActivity;
    private TextView txtNoCategoryData, txtNoGroupDebts, txtNoRecentActivity;
    private TextView txtMonthTxCount, txtMonthSpent, txtMonthEarned;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_money_overview, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        layoutCategoryBars = view.findViewById(R.id.layoutCategoryBars);
        layoutGroupDebts = view.findViewById(R.id.layoutGroupDebts);
        layoutRecentActivity = view.findViewById(R.id.layoutRecentActivity);
        txtNoCategoryData = view.findViewById(R.id.txtNoCategoryData);
        txtNoGroupDebts = view.findViewById(R.id.txtNoGroupDebts);
        txtNoRecentActivity = view.findViewById(R.id.txtNoRecentActivity);
        txtMonthTxCount = view.findViewById(R.id.txtMonthTxCount);
        txtMonthSpent = view.findViewById(R.id.txtMonthSpent);
        txtMonthEarned = view.findViewById(R.id.txtMonthEarned);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadOverviewData();
    }

    public void loadOverviewData() {
        if (auth == null || auth.getCurrentUser() == null || db == null) return;
        String userId = auth.getCurrentUser().getUid();

        loadCategoryBreakdown(userId);
        loadGroupDebts(userId);
        loadRecentActivity(userId);
        loadMonthlySummary(userId);
    }

    // ─── Category Breakdown ────────────────────────────────────────
    private void loadCategoryBreakdown(String userId) {
        db.collection("users")
                .document(userId)
                .collection("cashbook")
                .whereEqualTo("type", "CASH_OUT")
                .get()
                .addOnSuccessListener(snapshots -> {
                    Map<String, Double> categoryTotals = new HashMap<>();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String category = doc.getString("category");
                        Double amount = doc.getDouble("amount");
                        if (category == null || category.trim().isEmpty()) category = "Other";
                        if (amount == null) amount = 0.0;

                        categoryTotals.put(category, categoryTotals.getOrDefault(category, 0.0) + amount);
                    }

                    layoutCategoryBars.removeAllViews();

                    if (categoryTotals.isEmpty()) {
                        txtNoCategoryData.setVisibility(View.VISIBLE);
                        return;
                    }

                    txtNoCategoryData.setVisibility(View.GONE);

                    // Sort by value descending, take top 5
                    List<Map.Entry<String, Double>> sorted = new ArrayList<>(categoryTotals.entrySet());
                    sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

                    double maxAmount = sorted.get(0).getValue();
                    int count = Math.min(sorted.size(), 5);

                    String[] emojis = {"🍔", "🏠", "💰", "🏢", "👤", "🚗", "🛍️", "📄", "📈", "💵"};
                    int[] barColors = {
                            0xFF5C6BC0, 0xFF26C6DA, 0xFF66BB6A,
                            0xFFFFA726, 0xFFEF5350
                    };

                    for (int i = 0; i < count; i++) {
                        Map.Entry<String, Double> entry = sorted.get(i);
                        addCategoryBar(entry.getKey(), entry.getValue(), maxAmount,
                                getCategoryEmoji(entry.getKey()), barColors[i % barColors.length]);
                    }
                });
    }

    private String getCategoryEmoji(String category) {
        switch (category) {
            case "Sales": return "📈";
            case "Rent": return "🏠";
            case "Salary": return "💰";
            case "Office": return "🏢";
            case "Personal": return "👤";
            case "Food": return "🍔";
            case "Transport": return "🚗";
            case "Shopping": return "🛍️";
            case "Bills": return "📄";
            default: return "💵";
        }
    }

    private void addCategoryBar(String category, double amount, double maxAmount, String emoji, int barColor) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 0, 0, dpToPx(10));

        // Label row
        LinearLayout labelRow = new LinearLayout(requireContext());
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView txtLabel = new TextView(requireContext());
        txtLabel.setText(emoji + " " + category);
        txtLabel.setTextSize(13);
        txtLabel.setTextColor(getResources().getColor(R.color.text_primary));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        txtLabel.setLayoutParams(lp1);

        TextView txtAmount = new TextView(requireContext());
        txtAmount.setText(String.format(Locale.getDefault(), "₹%.0f", amount));
        txtAmount.setTextSize(13);
        txtAmount.setTextColor(getResources().getColor(R.color.text_secondary));

        labelRow.addView(txtLabel);
        labelRow.addView(txtAmount);
        row.addView(labelRow);

        // Progress bar
        LinearLayout barContainer = new LinearLayout(requireContext());
        barContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(8)));
        barContainer.setBackgroundResource(R.drawable.chip_background);

        View bar = new View(requireContext());
        int width = (int) ((amount / maxAmount) * 1.0 * 100);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, width);
        bar.setLayoutParams(barLp);
        bar.setBackgroundColor(barColor);

        View spacer = new View(requireContext());
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100 - width);
        spacer.setLayoutParams(spacerLp);

        barContainer.addView(bar);
        barContainer.addView(spacer);

        LinearLayout.LayoutParams barContainerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(8));
        barContainerLp.topMargin = dpToPx(4);
        barContainer.setLayoutParams(barContainerLp);

        row.addView(barContainer);
        layoutCategoryBars.addView(row);
    }

    // ─── Group Debts ───────────────────────────────────────────────
    private void loadGroupDebts(String userId) {
        db.collection("users")
                .document(userId)
                .collection("groups")
                .get()
                .addOnSuccessListener(groupSnap -> {
                    layoutGroupDebts.removeAllViews();

                    if (groupSnap.isEmpty()) {
                        txtNoGroupDebts.setVisibility(View.VISIBLE);
                        return;
                    }

                    txtNoGroupDebts.setVisibility(View.GONE);

                    for (QueryDocumentSnapshot groupDoc : groupSnap) {
                        String groupName = groupDoc.getString("name");
                        String groupId = groupDoc.getId();

                        // Load expenses for this group
                        db.collection("users")
                                .document(userId)
                                .collection("groups")
                                .document(groupId)
                                .collection("expenses")
                                .get()
                                .addOnSuccessListener(expSnap -> {
                                    double total = 0;
                                    for (QueryDocumentSnapshot exp : expSnap) {
                                        Double amt = exp.getDouble("amount");
                                        if (amt != null) total += amt;
                                    }
                                    addGroupDebtRow(groupName, total, expSnap.size());
                                });
                    }
                });
    }

    private void addGroupDebtRow(String groupName, double totalExpense, int txCount) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dpToPx(6), 0, dpToPx(6));

        // Group initial circle
        TextView avatar = new TextView(requireContext());
        avatar.setText(groupName != null && !groupName.isEmpty() ? groupName.substring(0, 1).toUpperCase() : "G");
        avatar.setTextSize(14);
        avatar.setTextColor(getResources().getColor(R.color.white));
        avatar.setGravity(android.view.Gravity.CENTER);
        avatar.setBackgroundResource(R.drawable.card_gradient_purple);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
        avLp.rightMargin = dpToPx(10);
        avatar.setLayoutParams(avLp);

        // Info
        LinearLayout info = new LinearLayout(requireContext());
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        info.setLayoutParams(infoLp);

        TextView name = new TextView(requireContext());
        name.setText(groupName != null ? groupName : "Group");
        name.setTextSize(14);
        name.setTextColor(getResources().getColor(R.color.text_primary));

        TextView subtitle = new TextView(requireContext());
        subtitle.setText(txCount + " transactions");
        subtitle.setTextSize(11);
        subtitle.setTextColor(getResources().getColor(R.color.text_secondary));

        info.addView(name);
        info.addView(subtitle);

        // Amount
        TextView amount = new TextView(requireContext());
        amount.setText(String.format(Locale.getDefault(), "₹%.0f", totalExpense));
        amount.setTextSize(14);
        amount.setTextColor(getResources().getColor(R.color.primary));

        row.addView(avatar);
        row.addView(info);
        row.addView(amount);

        layoutGroupDebts.addView(row);
    }

    // ─── Recent Activity ───────────────────────────────────────────
    private void loadRecentActivity(String userId) {
        db.collection("users")
                .document(userId)
                .collection("cashbook")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(snapshots -> {
                    layoutRecentActivity.removeAllViews();

                    if (snapshots.isEmpty()) {
                        txtNoRecentActivity.setVisibility(View.VISIBLE);
                        return;
                    }

                    txtNoRecentActivity.setVisibility(View.GONE);

                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String particulars = doc.getString("particulars");
                        String type = doc.getString("type");
                        Double amount = doc.getDouble("amount");
                        Long date = doc.getLong("date");

                        if (particulars == null) particulars = "Transaction";
                        if (amount == null) amount = 0.0;

                        addRecentActivityRow(
                                particulars,
                                "CASH_IN".equals(type) ? "+" : "-",
                                amount,
                                date != null ? sdf.format(new Date(date)) : "",
                                "CASH_IN".equals(type)
                        );
                    }
                });
    }

    private void addRecentActivityRow(String title, String prefix, double amount, String dateStr, boolean isIncome) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dpToPx(6), 0, dpToPx(6));

        // Info
        LinearLayout info = new LinearLayout(requireContext());
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        info.setLayoutParams(infoLp);

        TextView nameView = new TextView(requireContext());
        nameView.setText(title);
        nameView.setTextSize(14);
        nameView.setTextColor(getResources().getColor(R.color.text_primary));
        nameView.setMaxLines(1);

        TextView dateView = new TextView(requireContext());
        dateView.setText(dateStr);
        dateView.setTextSize(11);
        dateView.setTextColor(getResources().getColor(R.color.text_secondary));

        info.addView(nameView);
        info.addView(dateView);

        // Amount
        TextView amountView = new TextView(requireContext());
        amountView.setText(String.format(Locale.getDefault(), "%s₹%.0f", prefix, amount));
        amountView.setTextSize(14);
        amountView.setTextColor(getResources().getColor(isIncome ? R.color.accent_positive : R.color.accent_negative));

        row.addView(info);
        row.addView(amountView);

        layoutRecentActivity.addView(row);
    }

    // ─── Monthly Summary ───────────────────────────────────────────
    private void loadMonthlySummary(String userId) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfMonth = cal.getTimeInMillis();

        db.collection("users")
                .document(userId)
                .collection("cashbook")
                .whereGreaterThanOrEqualTo("date", startOfMonth)
                .get()
                .addOnSuccessListener(snapshots -> {
                    int count = snapshots.size();
                    double spent = 0, earned = 0;

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String type = doc.getString("type");
                        Double amount = doc.getDouble("amount");
                        if (amount == null) amount = 0.0;

                        if ("CASH_IN".equals(type)) {
                            earned += amount;
                        } else {
                            spent += amount;
                        }
                    }

                    txtMonthTxCount.setText(String.valueOf(count));
                    txtMonthSpent.setText(String.format(Locale.getDefault(), "₹%.0f", spent));
                    txtMonthEarned.setText(String.format(Locale.getDefault(), "₹%.0f", earned));
                });
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
