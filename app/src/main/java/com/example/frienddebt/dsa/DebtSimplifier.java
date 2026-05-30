package com.example.frienddebt.dsa;

import com.example.frienddebt.model.CashbookEntry;
import com.example.frienddebt.model.DebtEdge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class DebtSimplifier {

    public static List<DebtEdge> simplifyDebts(List<CashbookEntry> entries) {
        // Step 1: Compute net balances for everyone
        Map<String, Double> balances = new HashMap<>();

        for (CashbookEntry entry : entries) {
            String type = entry.getType();

            if ("SETTLEMENT".equals(type)) {
                // For a settlement, paidBy paid someone else.
                // We store the receiver in the 'participants' list at index 0.
                String payer = entry.getPaidBy();
                String receiver = entry.getParticipants() != null && !entry.getParticipants().isEmpty() ? entry.getParticipants().get(0) : null;
                
                if (payer != null && receiver != null) {
                    double amt = entry.getAmount();
                    balances.put(payer, balances.getOrDefault(payer, 0.0) + amt);
                    balances.put(receiver, balances.getOrDefault(receiver, 0.0) - amt);
                }
            } else if ("EXPENSE".equals(type) && entry.getSplits() != null) {
                // Shared Expense
                String payer = entry.getPaidBy();
                if (payer == null) continue;

                double totalPaid = entry.getAmount();
                balances.put(payer, balances.getOrDefault(payer, 0.0) + totalPaid);

                for (Map.Entry<String, Double> split : entry.getSplits().entrySet()) {
                    String userId = split.getKey();
                    double owe = split.getValue();
                    balances.put(userId, balances.getOrDefault(userId, 0.0) - owe);
                }
            }
        }

        // Step 2: Split into debtors (negative balance) and creditors (positive balance)
        // Max-heaps based on absolute value
        PriorityQueue<Pair> debtors = new PriorityQueue<>((a, b) -> Double.compare(b.amount, a.amount));
        PriorityQueue<Pair> creditors = new PriorityQueue<>((a, b) -> Double.compare(b.amount, a.amount));

        for (Map.Entry<String, Double> b : balances.entrySet()) {
            double amount = Math.round(b.getValue() * 100.0) / 100.0; // Avoid floating point issues
            if (amount < 0) {
                debtors.add(new Pair(b.getKey(), -amount));
            } else if (amount > 0) {
                creditors.add(new Pair(b.getKey(), amount));
            }
        }

        // Step 3: Greedily settle debts
        List<DebtEdge> suggestedPayments = new ArrayList<>();

        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            Pair debtor = debtors.poll();
            Pair creditor = creditors.poll();

            double settleAmount = Math.min(debtor.amount, creditor.amount);
            
            suggestedPayments.add(new DebtEdge(debtor.userId, creditor.userId, settleAmount));

            debtor.amount -= settleAmount;
            creditor.amount -= settleAmount;

            // Push back if there is remaining balance
            if (Math.round(debtor.amount * 100.0) / 100.0 > 0) {
                debtors.add(debtor);
            }
            if (Math.round(creditor.amount * 100.0) / 100.0 > 0) {
                creditors.add(creditor);
            }
        }

        return suggestedPayments;
    }

    private static class Pair {
        String userId;
        double amount;

        Pair(String userId, double amount) {
            this.userId = userId;
            this.amount = amount;
        }
    }
}
