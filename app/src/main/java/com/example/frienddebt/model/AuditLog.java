package com.example.frienddebt.model;

import com.google.firebase.firestore.DocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

public class AuditLog {
    private String id;
    private String bookId;
    private String actionType; // CREATE or DELETE
    private String actorId;
    private String actorName;
    private String particulars;
    private double amount;
    private String transactionType; // CASH_IN or CASH_OUT
    private long timestamp;
    private String details;

    public AuditLog() {
        // Required for Firestore
    }

    public AuditLog(String id, String bookId, String actionType, String actorId, String actorName, 
                    String particulars, double amount, String transactionType, long timestamp, String details) {
        this.id = id;
        this.bookId = bookId;
        this.actionType = actionType;
        this.actorId = actorId;
        this.actorName = actorName;
        this.particulars = particulars;
        this.amount = amount;
        this.transactionType = transactionType;
        this.timestamp = timestamp;
        this.details = details;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public String getParticulars() { return particulars; }
    public void setParticulars(String particulars) { this.particulars = particulars; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public static AuditLog fromDocument(DocumentSnapshot doc) {
        AuditLog log = new AuditLog();
        log.setId(doc.getId());
        log.setBookId(doc.getString("bookId"));
        log.setActionType(doc.getString("actionType"));
        log.setActorId(doc.getString("actorId"));
        log.setActorName(doc.getString("actorName"));
        log.setParticulars(doc.getString("particulars"));
        log.setAmount(doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0);
        log.setTransactionType(doc.getString("transactionType"));
        log.setTimestamp(doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0L);
        log.setDetails(doc.getString("details"));
        return log;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("bookId", bookId);
        map.put("actionType", actionType);
        map.put("actorId", actorId);
        map.put("actorName", actorName);
        map.put("particulars", particulars);
        map.put("amount", amount);
        map.put("transactionType", transactionType);
        map.put("timestamp", timestamp);
        map.put("details", details);
        return map;
    }
}
