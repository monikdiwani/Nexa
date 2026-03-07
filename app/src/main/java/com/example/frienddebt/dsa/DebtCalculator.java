package com.example.frienddebt.dsa;

import com.example.frienddebt.model.DebtEdge;
import com.example.frienddebt.model.Group;
import com.example.frienddebt.model.Payment;
import com.example.frienddebt.model.SettlementSuggestion;
import com.example.frienddebt.model.Transaction;
import com.example.frienddebt.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DebtCalculator {

    // Container for raw graph + balances
    public static class GraphAndBalances {
        public HashMap<User, LinkedList<DebtEdge>> rawGraph;
        public HashMap<User, Double> balances;
    }

    // ---------------- OLD GROUP-BASED LOGIC (UNCHANGED) ----------------

    public static HashMap<User, LinkedList<DebtEdge>> buildDebtGraph(Group group) {
        HashMap<User, LinkedList<DebtEdge>> graph = new HashMap<>();

        for (Transaction t : group.getTransactions()) {
            double perHead = t.getAmount() / t.getSharedWith().size();
            User payer = t.getPaidBy();

            for (User u : t.getSharedWith()) {
                if (u.getId() == payer.getId()) continue;
                addDebt(graph, u, payer, perHead);
            }
        }
        return graph;
    }

    private static void addDebt(HashMap<User, LinkedList<DebtEdge>> graph,
                                User from,
                                User to,
                                double amount) {

        LinkedList<DebtEdge> list = graph.get(from);
        if (list == null) {
            list = new LinkedList<>();
            graph.put(from, list);
        }

        for (DebtEdge edge : list) {
            if (edge.getTo().getId() == to.getId()) {
                edge.setAmount(edge.getAmount() + amount);
                return;
            }
        }
        list.add(new DebtEdge(from, to, amount));
    }

    public static HashMap<User, Double> computeBalances(Group group) {
        HashMap<User, Double> bal = new HashMap<>();

        for (Transaction t : group.getTransactions()) {
            double perHead = t.getAmount() / t.getSharedWith().size();
            User payer = t.getPaidBy();

            for (User u : t.getSharedWith()) {
                if (u.getId() == payer.getId()) continue;

                bal.put(payer, bal.getOrDefault(payer, 0.0) + perHead);
                bal.put(u, bal.getOrDefault(u, 0.0) - perHead);
            }
        }
        return bal;
    }

    public static GraphAndBalances computeGraphAndBalances(Group group) {
        GraphAndBalances gab = new GraphAndBalances();
        gab.rawGraph = buildDebtGraph(group);
        gab.balances = computeBalances(group);
        return gab;
    }

    public static List<SettlementSuggestion> buildSettlementSuggestions(Group group) {
        HashMap<User, Double> bal = computeBalances(group);
        List<SettlementSuggestion> result = new ArrayList<>();

        List<Map.Entry<User, Double>> positives = new ArrayList<>();
        List<Map.Entry<User, Double>> negatives = new ArrayList<>();

        for (Map.Entry<User, Double> e : bal.entrySet()) {
            if (Math.abs(e.getValue()) < 0.01) continue;
            if (e.getValue() > 0) positives.add(e);
            else negatives.add(e);
        }

        int i = 0, j = 0;
        while (i < negatives.size() && j < positives.size()) {
            Map.Entry<User, Double> neg = negatives.get(i);
            Map.Entry<User, Double> pos = positives.get(j);

            double pay = Math.min(-neg.getValue(), pos.getValue());

            result.add(new SettlementSuggestion(neg.getKey(), pos.getKey(), pay));

            neg.setValue(neg.getValue() + pay);
            pos.setValue(pos.getValue() - pay);

            if (Math.abs(neg.getValue()) < 0.01) i++;
            if (Math.abs(pos.getValue()) < 0.01) j++;
        }

        return result;
    }

    // ---------------- FINAL FIRESTORE-BASED LOGIC ----------------

    public static List<SettlementSuggestion> buildSettlementSuggestionsFromTransactions(
            List<Transaction> transactions) {

        Map<User, Double> balance = new HashMap<>();

        // STEP 1: calculate balances
        for (Transaction tx : transactions) {
            if (tx == null) continue;

            double amount = tx.getAmount();
            User payer = tx.getPaidBy();
            List<User> participants = tx.getSharedWith();

            if (payer == null || participants == null || participants.isEmpty()) continue;

            double share = amount / participants.size();

            for (User u : participants) {
                if (u == null) continue;
                if (u.getId() == payer.getId()) continue;
                balance.put(u, balance.getOrDefault(u, 0.0) - share);
            }

            double credit = amount - share;
            balance.put(payer, balance.getOrDefault(payer, 0.0) + credit);
        }

        // STEP 2: split debtors / creditors
        List<Map.Entry<User, Double>> debtors = new ArrayList<>();
        List<Map.Entry<User, Double>> creditors = new ArrayList<>();

        for (Map.Entry<User, Double> e : balance.entrySet()) {
            if (Math.abs(e.getValue()) < 0.01) continue;
            if (e.getValue() < 0) debtors.add(e);
            else creditors.add(e);
        }

        // STEP 3: generate raw settlements
        List<SettlementSuggestion> result = new ArrayList<>();

        int i = 0, j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            Map.Entry<User, Double> d = debtors.get(i);
            Map.Entry<User, Double> c = creditors.get(j);

            if (d.getKey().getId() == c.getKey().getId()) {
                i++;
                continue;
            }

            double pay = Math.min(-d.getValue(), c.getValue());

            result.add(new SettlementSuggestion(d.getKey(), c.getKey(), pay));

            d.setValue(d.getValue() + pay);
            c.setValue(c.getValue() - pay);

            if (Math.abs(d.getValue()) < 0.01) i++;
            if (Math.abs(c.getValue()) < 0.01) j++;
        }

        // 🔥 STEP 4: MERGE duplicate settlements
        Map<String, SettlementSuggestion> merged = new HashMap<>();

        for (SettlementSuggestion s : result) {
            if (s.getFrom().getId() == s.getTo().getId()) continue;

            String key = s.getFrom().getId() + "->" + s.getTo().getId();

            if (!merged.containsKey(key)) {
                merged.put(key, new SettlementSuggestion(
                        s.getFrom(),
                        s.getTo(),
                        s.getAmount()
                ));
            } else {
                SettlementSuggestion existing = merged.get(key);
                existing.setAmount(existing.getAmount() + s.getAmount());
            }
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * Build settlement suggestions from transactions, minus any payments already made.
     * Payments reduce debt: when A pays B, A's balance increases, B's balance decreases.
     */
    public static List<SettlementSuggestion> buildSettlementSuggestionsFromTransactionsAndPayments(
            List<Transaction> transactions,
            List<Payment> payments) {

        Map<User, Double> balance = new HashMap<>();

        // STEP 1: calculate balances from transactions
        for (Transaction tx : transactions) {
            if (tx == null) continue;

            double amount = tx.getAmount();
            User payer = tx.getPaidBy();
            List<User> participants = tx.getSharedWith();

            if (payer == null || participants == null || participants.isEmpty()) continue;

            double share = amount / participants.size();

            for (User u : participants) {
                if (u == null) continue;
                if (u.getId() == payer.getId()) continue;
                balance.put(u, balance.getOrDefault(u, 0.0) - share);
            }

            double credit = amount - share;
            balance.put(payer, balance.getOrDefault(payer, 0.0) + credit);
        }

        // STEP 2: subtract payments (A paid B → A's balance +amount, B's balance -amount)
        if (payments != null) {
            for (Payment p : payments) {
                if (p == null || p.getFrom() == null || p.getTo() == null) continue;
                double amt = p.getAmount();
                balance.put(p.getFrom(), balance.getOrDefault(p.getFrom(), 0.0) + amt);
                balance.put(p.getTo(), balance.getOrDefault(p.getTo(), 0.0) - amt);
            }
        }

        // STEP 3: split debtors / creditors
        List<Map.Entry<User, Double>> debtors = new ArrayList<>();
        List<Map.Entry<User, Double>> creditors = new ArrayList<>();

        for (Map.Entry<User, Double> e : balance.entrySet()) {
            if (Math.abs(e.getValue()) < 0.01) continue;
            if (e.getValue() < 0) debtors.add(e);
            else creditors.add(e);
        }

        // STEP 4: generate settlements
        List<SettlementSuggestion> result = new ArrayList<>();

        int i = 0, j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            Map.Entry<User, Double> d = debtors.get(i);
            Map.Entry<User, Double> c = creditors.get(j);

            if (d.getKey().getId() == c.getKey().getId()) {
                i++;
                continue;
            }

            double pay = Math.min(-d.getValue(), c.getValue());

            result.add(new SettlementSuggestion(d.getKey(), c.getKey(), pay));

            d.setValue(d.getValue() + pay);
            c.setValue(c.getValue() - pay);

            if (Math.abs(d.getValue()) < 0.01) i++;
            if (Math.abs(c.getValue()) < 0.01) j++;
        }

        // STEP 5: merge duplicate settlements
        Map<String, SettlementSuggestion> merged = new HashMap<>();

        for (SettlementSuggestion s : result) {
            if (s.getFrom().getId() == s.getTo().getId()) continue;

            String key = s.getFrom().getId() + "->" + s.getTo().getId();

            if (!merged.containsKey(key)) {
                merged.put(key, new SettlementSuggestion(
                        s.getFrom(),
                        s.getTo(),
                        s.getAmount()
                ));
            } else {
                SettlementSuggestion existing = merged.get(key);
                existing.setAmount(existing.getAmount() + s.getAmount());
            }
        }

        return new ArrayList<>(merged.values());
    }
}
