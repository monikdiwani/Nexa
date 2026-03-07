package com.example.frienddebt.model;

public class SettlementSuggestion {

    private User from;
    private User to;
    private double amount;

    public SettlementSuggestion(User from, User to, double amount) {
        this.from = from;
        this.to = to;
        this.amount = amount;
    }

    public User getFrom() {
        return from;
    }

    public User getTo() {
        return to;
    }

    public double getAmount() {
        return amount;
    }

    // ✅ REQUIRED FOR MERGING SETTLEMENTS
    public void setAmount(double amount) {
        this.amount = amount;
    }
}
