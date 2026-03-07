package com.example.frienddebt.model;

import java.io.Serializable;

/**
 * Represents a payment made from one user to another (settling a debt).
 */
public class Payment implements Serializable {

    private String id;
    private User from;
    private User to;
    private double amount;
    private long timestamp;

    public Payment(User from, User to, double amount, long timestamp) {
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public long getTimestamp() {
        return timestamp;
    }
}
