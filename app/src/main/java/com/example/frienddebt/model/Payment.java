package com.example.frienddebt.model;

import com.google.firebase.firestore.DocumentSnapshot;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a payment made from one user to another (settling a debt).
 */
public class Payment implements Serializable {

    private String id;
    private String fromUserId;
    private String toUserId;
    private double amount;
    private long timestamp;

    public Payment() {}

    public Payment(String fromUserId, String toUserId, double amount, long timestamp) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public static Payment fromDocument(DocumentSnapshot doc) {
        Payment p = new Payment();
        p.setId(doc.getId());
        p.setFromUserId(doc.getString("fromUserId"));
        p.setToUserId(doc.getString("toUserId"));
        Double amt = doc.getDouble("amount");
        p.setAmount(amt != null ? amt : 0.0);
        Long ts = doc.getLong("timestamp");
        p.setTimestamp(ts != null ? ts : 0L);
        return p;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("fromUserId", fromUserId);
        map.put("toUserId", toUserId);
        map.put("amount", amount);
        map.put("timestamp", timestamp);
        return map;
    }
}
