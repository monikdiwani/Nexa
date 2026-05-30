package com.example.frienddebt.model;

public class DebtEdge {

    private String from;
    private String to;
    private double amount;

    public DebtEdge(String from, String to, double amount) {
        this.from = from;
        this.to = to;
        this.amount = amount;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
