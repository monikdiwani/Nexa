package com.example.frienddebt.model;

import com.google.firebase.firestore.DocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

public class DebtEdge {

    private String from;
    private String to;
    private double amount;

    public DebtEdge() {}

    public DebtEdge(String from, String to, double amount) {
        this.from = from;
        this.to = to;
        this.amount = amount;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public static DebtEdge fromDocument(DocumentSnapshot doc) {
        DebtEdge edge = new DebtEdge();
        edge.setFrom(doc.getString("from"));
        edge.setTo(doc.getString("to"));
        Double amt = doc.getDouble("amount");
        edge.setAmount(amt != null ? amt : 0.0);
        return edge;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("from", from);
        map.put("to", to);
        map.put("amount", amount);
        return map;
    }
}
