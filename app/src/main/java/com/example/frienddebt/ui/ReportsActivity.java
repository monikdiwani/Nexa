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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportsActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextView chip7Days, chip30Days;
    private TextView txtTotalCashIn, txtTotalCashOut, txtNetBalance, txtTaskProgressLabel;
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

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        btnBack = findViewById(R.id.btnBack);
        chip7Days = findViewById(R.id.chip7Days);
        chip30Days = findViewById(R.id.chip30Days);
        txtTotalCashIn = findViewById(R.id.txtTotalCashIn);
        txtTotalCashOut = findViewById(R.id.txtTotalCashOut);
        txtNetBalance = findViewById(R.id.txtNetBalance);
        txtTaskProgressLabel = findViewById(R.id.txtTaskProgressLabel);
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

        inactiveChip.setBackgroundResource(R.drawable.chip_background);
        inactiveChip.setTextColor(getResources().getColor(R.color.text_secondary));

        calculateReports();
    }

    private void loadData() {
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(userId)
                .collection("cashbook")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    cashbookEntries.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        cashbookEntries.add(CashbookEntry.fromDocument(doc));
                    }
                    calculateReports();
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

        List<CashbookEntry> filteredEntries = new ArrayList<>();
        double totalIn = 0;
        double totalOut = 0;

        for (CashbookEntry entry : cashbookEntries) {
            if (entry.getDate() >= cutoffTime) {
                filteredEntries.add(entry);
                if ("CASH_IN".equalsIgnoreCase(entry.getType())) {
                    totalIn += entry.getAmount();
                } else if ("CASH_OUT".equalsIgnoreCase(entry.getType())) {
                    totalOut += entry.getAmount();
                }
            }
        }

        txtTotalCashIn.setText(String.format(Locale.getDefault(), "₹%.2f", totalIn));
        txtTotalCashOut.setText(String.format(Locale.getDefault(), "₹%.2f", totalOut));
        txtNetBalance.setText(String.format(Locale.getDefault(), "₹%.2f", totalIn - totalOut));

        Map<String, Double> trend = ReportCalculator.getDailyTrend(filteredEntries, activeDays);
        barChart.setData(trend);

        categoryContainer.removeAllViews();
        Map<String, Double> breakdown = ReportCalculator.getCategoryBreakdown(filteredEntries);

        if (breakdown.isEmpty()) {
            TextView txtEmpty = new TextView(this);
            txtEmpty.setText("No category data found for this period");
            txtEmpty.setTextColor(getResources().getColor(R.color.text_hint));
            categoryContainer.addView(txtEmpty);
        } else {
            for (Map.Entry<String, Double> item : breakdown.entrySet()) {
                String cat = item.getKey();
                double amount = item.getValue();
                double percent = totalOut > 0 ? (amount / totalOut) * 100 : 0;

                View row = getLayoutInflater().inflate(R.layout.item_category_report, categoryContainer, false);
                TextView txtName = row.findViewById(R.id.txtCatName);
                TextView txtAmount = row.findViewById(R.id.txtCatAmount);
                ProgressBar progress = row.findViewById(R.id.progressCat);

                txtName.setText(cat);
                txtAmount.setText(String.format(Locale.getDefault(), "₹%.2f (%.1f%%)", amount, percent));
                progress.setProgress((int) percent);

                categoryContainer.addView(row);
            }
        }
    }

    private void calculateTaskAnalytics() {
        int rate = ReportCalculator.getTaskCompletionRate(taskList);
        taskProgress.setProgress(rate);
        txtTaskProgressLabel.setText("Completion Rate: " + rate + "%");
    }
}
