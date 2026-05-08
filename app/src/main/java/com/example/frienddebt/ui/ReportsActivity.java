package com.example.frienddebt.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;
import com.example.frienddebt.dsa.ReportCalculator;
import com.example.frienddebt.model.CashbookEntry;
import com.example.frienddebt.model.Task;
import com.example.frienddebt.ui.view.SimpleBarChartView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.frienddebt.utils.StatusBarUtil;

public class ReportsActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextView chip7Days, chip30Days;
    private TextView txtTotalCashIn, txtTotalCashOut, txtNetBalance;
    private TextView txtAvgDaily, txtHighestDay, txtTransactions;
    private TextView txtChartTitle, txtZeroSpendDays;
    private TextView txtTaskProgressLabel, txtTaskProgressPercent, txtTaskCounts;
    private CircularProgressIndicator taskProgress;
    private SimpleBarChartView barChart;
    private LinearLayout categoryContainer;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private List<CashbookEntry> cashbookEntries = new ArrayList<>();
    private List<Task> taskList = new ArrayList<>();

    private int activeDays = 7; // 7 or 30

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        StatusBarUtil.applyStatusBarPadding(this);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        btnBack = findViewById(R.id.btnBack);
        chip7Days = findViewById(R.id.chip7Days);
        chip30Days = findViewById(R.id.chip30Days);
        txtTotalCashIn = findViewById(R.id.txtTotalCashIn);
        txtTotalCashOut = findViewById(R.id.txtTotalCashOut);
        txtNetBalance = findViewById(R.id.txtNetBalance);
        txtAvgDaily = findViewById(R.id.txtAvgDaily);
        txtHighestDay = findViewById(R.id.txtHighestDay);
        txtTransactions = findViewById(R.id.txtTransactions);
        txtChartTitle = findViewById(R.id.txtChartTitle);
        txtZeroSpendDays = findViewById(R.id.txtZeroSpendDays);
        txtTaskProgressLabel = findViewById(R.id.txtTaskProgressLabel);
        txtTaskProgressPercent = findViewById(R.id.txtTaskProgressPercent);
        txtTaskCounts = findViewById(R.id.txtTaskCounts);
        taskProgress = findViewById(R.id.taskProgress);
        barChart = findViewById(R.id.barChart);
        categoryContainer = findViewById(R.id.categoryContainer);

        btnBack.setOnClickListener(v -> finish());

        chip7Days.setOnClickListener(v -> setDays(7, chip7Days, chip30Days));
        chip30Days.setOnClickListener(v -> setDays(30, chip30Days, chip7Days));

        loadData();
    }

    private void setDays(int days, TextView activeChip, TextView inactiveChip) {
        activeDays = days;
        activeChip.setBackgroundResource(R.drawable.rounded_button);
        activeChip.setTextColor(getResources().getColor(R.color.on_primary));

        inactiveChip.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        inactiveChip.setTextColor(getResources().getColor(R.color.text_secondary));

        calculateReports();
    }

    private void loadData() {
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        db.collection("cashbooks")
                .whereNotEqualTo("members." + userId, null)
                .get()
                .addOnSuccessListener(booksSnap -> {
                    List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>> tasks = new ArrayList<>();
                    for (DocumentSnapshot bookDoc : booksSnap.getDocuments()) {
                        String bookId = bookDoc.getId();
                        tasks.add(db.collection("cashbooks")
                                .document(bookId)
                                .collection("entries")
                                .get());
                    }

                    if (tasks.isEmpty()) {
                        cashbookEntries.clear();
                        calculateReports();
                        return;
                    }

                    Tasks.whenAllSuccess(tasks)
                            .addOnSuccessListener(results -> {
                                cashbookEntries.clear();
                                for (Object res : results) {
                                    com.google.firebase.firestore.QuerySnapshot entrySnap = (com.google.firebase.firestore.QuerySnapshot) res;
                                    for (DocumentSnapshot entryDoc : entrySnap.getDocuments()) {
                                        cashbookEntries.add(CashbookEntry.fromDocument(entryDoc));
                                    }
                                }
                                calculateReports();
                            });
                });

        db.collection("users")
                .document(userId)
                .collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    taskList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        taskList.add(Task.fromDocument(doc));
                    }
                    calculateTaskAnalytics();
                });
    }

    private void calculateReports() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -activeDays);
        long cutoffTime = cal.getTimeInMillis();

        // Filter entries to period
        List<CashbookEntry> filteredEntries = new ArrayList<>();
        for (CashbookEntry entry : cashbookEntries) {
            if (entry.getDate() >= cutoffTime) {
                filteredEntries.add(entry);
            }
        }

        // Calculate totals
        double totalIn = ReportCalculator.getTotalCashIn(filteredEntries);
        double totalOut = ReportCalculator.getTotalCashOut(filteredEntries);
        double netBalance = totalIn - totalOut;

        txtTotalCashIn.setText(formatCurrency(totalIn));
        txtTotalCashOut.setText(formatCurrency(totalOut));
        txtNetBalance.setText(formatCurrency(netBalance));
        txtNetBalance.setTextColor(getResources().getColor(
                netBalance >= 0 ? R.color.accent_positive : R.color.accent_negative));

        // Insight stats
        double avgDaily = ReportCalculator.getAverageDailySpending(filteredEntries, activeDays);
        double highestDay = ReportCalculator.getHighestDaySpending(filteredEntries, activeDays);
        int transactionCount = ReportCalculator.getTransactionCount(filteredEntries);
        int zeroSpendDays = ReportCalculator.getZeroSpendDays(filteredEntries, activeDays);

        txtAvgDaily.setText(formatCurrencyShort(avgDaily));
        txtHighestDay.setText(formatCurrencyShort(highestDay));
        txtTransactions.setText(String.valueOf(transactionCount));

        // Chart title and zero-spend badge
        if (activeDays <= 7) {
            txtChartTitle.setText("📈  Daily Outflow Trend");
        } else {
            txtChartTitle.setText("📈  Weekly Outflow Trend");
        }
        txtZeroSpendDays.setText(zeroSpendDays + " zero days");
        txtZeroSpendDays.setTextColor(getResources().getColor(
                zeroSpendDays > 0 ? R.color.accent_positive : R.color.text_hint));

        // Bar chart data
        Map<String, Double> trend = ReportCalculator.getDailyTrend(filteredEntries, activeDays);
        barChart.setData(trend);

        // Category breakdown
        categoryContainer.removeAllViews();
        Map<String, Double> breakdown = ReportCalculator.getCategoryBreakdown(filteredEntries);

        if (breakdown.isEmpty()) {
            TextView txtEmpty = new TextView(this);
            txtEmpty.setText("No category data found for this period");
            txtEmpty.setTextColor(getResources().getColor(R.color.text_hint));
            txtEmpty.setTextSize(13f);
            txtEmpty.setPadding(0, 12, 0, 12);
            categoryContainer.addView(txtEmpty);
        } else {
            // Assign colors to categories
            int[] catColors = {
                    getResources().getColor(R.color.primary),
                    getResources().getColor(R.color.accent_negative),
                    getResources().getColor(R.color.accent_warning),
                    getResources().getColor(R.color.accent_positive),
                    getResources().getColor(R.color.accent_info),
                    getResources().getColor(R.color.secondary),
            };
            int colorIdx = 0;

            for (Map.Entry<String, Double> item : breakdown.entrySet()) {
                String cat = item.getKey();
                double amount = item.getValue();
                double percent = totalOut > 0 ? (amount / totalOut) * 100 : 0;

                View row = getLayoutInflater().inflate(R.layout.item_category_report, categoryContainer, false);
                TextView txtName = row.findViewById(R.id.txtCatName);
                TextView txtAmount = row.findViewById(R.id.txtCatAmount);
                ProgressBar progress = row.findViewById(R.id.progressCat);

                txtName.setText(cat);
                txtAmount.setText(String.format(Locale.getDefault(), "%s (%.0f%%)", formatCurrency(amount), percent));
                progress.setProgress((int) percent);

                // Color the progress bar by category
                int barColor = catColors[colorIdx % catColors.length];
                progress.setProgressTintList(android.content.res.ColorStateList.valueOf(barColor));
                colorIdx++;

                categoryContainer.addView(row);
            }
        }
    }

    private void calculateTaskAnalytics() {
        int rate = ReportCalculator.getTaskCompletionRate(taskList);
        taskProgress.setProgress(rate);
        txtTaskProgressPercent.setText(rate + "%");

        int completed = 0;
        for (Task t : taskList) {
            if (t.isCompleted()) completed++;
        }
        txtTaskCounts.setText(completed + " completed / " + taskList.size() + " total");
    }

    private String formatCurrency(double amount) {
        if (Math.abs(amount) >= 100000) {
            return String.format(Locale.getDefault(), "₹%.1fL", amount / 100000);
        } else if (Math.abs(amount) >= 1000) {
            return String.format(Locale.getDefault(), "₹%.1fk", amount / 1000);
        } else {
            return String.format(Locale.getDefault(), "₹%.0f", amount);
        }
    }

    private String formatCurrencyShort(double amount) {
        if (Math.abs(amount) >= 100000) {
            return String.format(Locale.getDefault(), "₹%.0fL", amount / 100000);
        } else if (Math.abs(amount) >= 1000) {
            return String.format(Locale.getDefault(), "₹%.1fk", amount / 1000);
        } else {
            return String.format(Locale.getDefault(), "₹%.0f", amount);
        }
    }
}
