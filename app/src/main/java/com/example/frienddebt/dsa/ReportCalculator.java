package com.example.frienddebt.dsa;

import com.example.frienddebt.model.CashbookEntry;
import com.example.frienddebt.model.Task;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportCalculator {

    public static Map<String, Double> getCategoryBreakdown(List<CashbookEntry> entries) {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        for (CashbookEntry entry : entries) {
            if ("CASH_OUT".equalsIgnoreCase(entry.getType())) {
                String cat = entry.getCategory();
                if (cat == null || cat.isEmpty()) {
                    cat = "Other";
                }
                breakdown.put(cat, breakdown.getOrDefault(cat, 0.0) + entry.getAmount());
            }
        }
        return breakdown;
    }

    public static Map<String, Double> getDailyTrend(List<CashbookEntry> entries, int days) {
        Map<String, Double> trend = new LinkedHashMap<>();

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());
        String[] dateLabels = new String[days];
        long[] startTimes = new long[days];
        long[] endTimes = new long[days];

        for (int i = days - 1; i >= 0; i--) {
            Calendar dayCal = Calendar.getInstance();
            dayCal.add(Calendar.DAY_OF_YEAR, -i);
            dayCal.set(Calendar.HOUR_OF_DAY, 0);
            dayCal.set(Calendar.MINUTE, 0);
            dayCal.set(Calendar.SECOND, 0);
            dayCal.set(Calendar.MILLISECOND, 0);
            startTimes[days - 1 - i] = dayCal.getTimeInMillis();

            dayCal.set(Calendar.HOUR_OF_DAY, 23);
            dayCal.set(Calendar.MINUTE, 59);
            dayCal.set(Calendar.SECOND, 59);
            dayCal.set(Calendar.MILLISECOND, 999);
            endTimes[days - 1 - i] = dayCal.getTimeInMillis();

            dateLabels[days - 1 - i] = sdf.format(dayCal.getTime());
            trend.put(dateLabels[days - 1 - i], 0.0);
        }

        for (CashbookEntry entry : entries) {
            if ("CASH_OUT".equalsIgnoreCase(entry.getType())) {
                long date = entry.getDate();
                for (int i = 0; i < days; i++) {
                    if (date >= startTimes[i] && date <= endTimes[i]) {
                        trend.put(dateLabels[i], trend.get(dateLabels[i]) + entry.getAmount());
                        break;
                    }
                }
            }
        }

        return trend;
    }

    public static int getTaskCompletionRate(List<Task> tasks) {
        if (tasks.isEmpty()) return 0;
        int completed = 0;
        for (Task t : tasks) {
            if (t.isCompleted()) {
                completed++;
            }
        }
        return (int) (((double) completed / tasks.size()) * 100);
    }
}
