package com.example.frienddebt.model;

public class DebtEdge {

    private User from;
    private User to;
    private double amount;

    public DebtEdge(User from, User to, double amount) {
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

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
