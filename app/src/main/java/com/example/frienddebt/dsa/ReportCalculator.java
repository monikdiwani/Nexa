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

    public static Map<String, Double> getCategoryBreakdown(List<CashbookEntry> entries, String userId) {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        for (CashbookEntry entry : entries) {
            if ("CASH_OUT".equalsIgnoreCase(entry.getType())) {
                String cat = entry.getCategory();
                if (cat == null || cat.isEmpty()) {
                    cat = "Other";
                }
                double amount = getPersonalAmount(entry, userId);
                if (amount > 0) {
                    breakdown.put(cat, breakdown.getOrDefault(cat, 0.0) + amount);
                }
            }
        }
        return breakdown;
    }

    /**
     * Returns daily trend for 7-day view (one bar per day),
     * or weekly trend for 30-day view (bars grouped by week).
     */
    public static Map<String, Double> getDailyTrend(List<CashbookEntry> entries, int days, String userId) {
        if (days <= 7) {
            return getDailyTrendDaily(entries, days, userId);
        } else {
            return getWeeklyTrend(entries, days, userId);
        }
    }

    private static Map<String, Double> getDailyTrendDaily(List<CashbookEntry> entries, int days, String userId) {
        Map<String, Double> trend = new LinkedHashMap<>();
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
                double amount = getPersonalAmount(entry, userId);
                if (amount > 0) {
                    for (int i = 0; i < days; i++) {
                        if (date >= startTimes[i] && date <= endTimes[i]) {
                            trend.put(dateLabels[i], trend.get(dateLabels[i]) + amount);
                            break;
                        }
                    }
                }
            }
        }

        return trend;
    }

    /**
     * Groups spending into weekly buckets for 30-day view.
     * Produces ~4-5 bars labeled "Week 1", "Week 2", etc. with date ranges.
     */
    private static Map<String, Double> getWeeklyTrend(List<CashbookEntry> entries, int days, String userId) {
        Map<String, Double> trend = new LinkedHashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.getDefault());

        int numWeeks = (int) Math.ceil(days / 7.0);
        long[] startTimes = new long[numWeeks];
        long[] endTimes = new long[numWeeks];
        String[] labels = new String[numWeeks];

        for (int w = 0; w < numWeeks; w++) {
            Calendar startCal = Calendar.getInstance();
            startCal.add(Calendar.DAY_OF_YEAR, -(days - 1) + (w * 7));
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);
            startTimes[w] = startCal.getTimeInMillis();

            Calendar endCal = (Calendar) startCal.clone();
            endCal.add(Calendar.DAY_OF_YEAR, 6);
            if (w == numWeeks - 1) {
                // Last week ends today
                endCal = Calendar.getInstance();
            }
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            endCal.set(Calendar.MILLISECOND, 999);
            endTimes[w] = endCal.getTimeInMillis();

            labels[w] = sdf.format(startCal.getTime()) + "-" + sdf.format(endCal.getTime());
            trend.put(labels[w], 0.0);
        }

        for (CashbookEntry entry : entries) {
            if ("CASH_OUT".equalsIgnoreCase(entry.getType())) {
                long date = entry.getDate();
                double amount = getPersonalAmount(entry, userId);
                if (amount > 0) {
                    for (int w = 0; w < numWeeks; w++) {
                        if (date >= startTimes[w] && date <= endTimes[w]) {
                            trend.put(labels[w], trend.get(labels[w]) + amount);
                            break;
                        }
                    }
                }
            }
        }

        return trend;
    }

    /**
     * Returns the average daily spending for the period.
     */
    public static double getAverageDailySpending(List<CashbookEntry> entries, int days, String userId) {
        double total = 0;
        for (CashbookEntry entry : entries) {
            if ("CASH_OUT".equalsIgnoreCase(entry.getType())) {
                total += getPersonalAmount(entry, userId);
            }
        }
        return days > 0 ? total / days : 0;
    }

    /**
     * Returns the highest single-day spending amount.
     */
    public static double getHighestDaySpending(List<CashbookEntry> entries, int days, String userId) {
        Map<String, Double> daily = getDailyTrendDaily(entries, days, userId);
        double max = 0;
        for (Double val : daily.values()) {
            if (val > max) max = val;
        }
        return max;
    }

    /**
     * Returns the number of days with zero spending.
     */
    public static int getZeroSpendDays(List<CashbookEntry> entries, int days, String userId) {
        Map<String, Double> daily = getDailyTrendDaily(entries, days, userId);
        int count = 0;
        for (Double val : daily.values()) {
            if (val == 0) count++;
        }
        return count;
    }

    /**
     * Returns the total number of transactions in the period.
     */
    public static int getTransactionCount(List<CashbookEntry> entries) {
        return entries.size();
    }

    /**
     * Returns cash in total for filtered entries.
     */
    public static double getTotalCashIn(List<CashbookEntry> entries, String userId) {
        double total = 0;
        for (CashbookEntry entry : entries) {
            if ("CASH_IN".equalsIgnoreCase(entry.getType())) {
                total += getPersonalAmount(entry, userId);
            }
        }
        return total;
    }

    /**
     * Returns cash out total for filtered entries.
     */
    public static double getTotalCashOut(List<CashbookEntry> entries, String userId) {
        double total = 0;
        for (CashbookEntry entry : entries) {
            if ("CASH_OUT".equalsIgnoreCase(entry.getType())) {
                total += getPersonalAmount(entry, userId);
            }
        }
        return total;
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

    private static double getPersonalAmount(CashbookEntry entry, String userId) {
        if (entry.getSplits() != null && entry.getSplits().containsKey(userId)) {
            return entry.getSplits().get(userId);
        }
        return entry.getAmount();
    }
}
