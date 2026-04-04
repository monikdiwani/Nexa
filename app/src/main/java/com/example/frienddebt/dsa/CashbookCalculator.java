package com.example.frienddebt.dsa;

import com.example.frienddebt.model.CashbookEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CashbookCalculator {

    public static double calculateCashBalance(List<CashbookEntry> entries) {
        double balance = 0;
        for (CashbookEntry entry : entries) {
            if ("CASH".equalsIgnoreCase(entry.getMedium())) {
                if ("CASH_IN".equalsIgnoreCase(entry.getType())) {
                    balance += entry.getAmount();
                } else if ("CASH_OUT".equalsIgnoreCase(entry.getType())) {
                    balance -= entry.getAmount();
                }
            }
        }
        return balance;
    }

    public static double calculateBankBalance(List<CashbookEntry> entries) {
        double balance = 0;
        for (CashbookEntry entry : entries) {
            if ("BANK".equalsIgnoreCase(entry.getMedium())) {
                if ("CASH_IN".equalsIgnoreCase(entry.getType())) {
                    balance += entry.getAmount();
                } else if ("CASH_OUT".equalsIgnoreCase(entry.getType())) {
                    balance -= entry.getAmount();
                }
            }
        }
        return balance;
    }

    public static double calculateTotalBalance(List<CashbookEntry> entries) {
        return calculateCashBalance(entries) + calculateBankBalance(entries);
    }

    public static List<CashbookEntry> filterByDateRange(List<CashbookEntry> entries, long start, long end) {
        List<CashbookEntry> filtered = new ArrayList<>();
        for (CashbookEntry entry : entries) {
            if (entry.getDate() >= start && entry.getDate() <= end) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    public static List<CashbookEntry> filterByMedium(List<CashbookEntry> entries, String medium) {
        List<CashbookEntry> filtered = new ArrayList<>();
        for (CashbookEntry entry : entries) {
            if (medium.equalsIgnoreCase(entry.getMedium())) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    public static Map<String, Double> groupByCategory(List<CashbookEntry> entries) {
        Map<String, Double> result = new HashMap<>();
        for (CashbookEntry entry : entries) {
            if ("CASH_OUT".equalsIgnoreCase(entry.getType())) {
                String cat = entry.getCategory();
                if (cat == null || cat.isEmpty()) {
                    cat = "Other";
                }
                result.put(cat, result.getOrDefault(cat, 0.0) + entry.getAmount());
            }
        }
        return result;
    }
}
