package com.example.frienddebt.model;

import com.google.firebase.firestore.DocumentSnapshot;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class LedgerBook implements Serializable {

    private String id;
    private String name;
    private String currency;
    private String ownerId;
    private long createdAt;
    
    // Derived balances
    private double totalCashIn;
    private double totalCashOut;
    private double netBalance;

    private Map<String, String> members; // map of userId to Role (e.g., "ADMIN", "VIEWER")

    public LedgerBook() {
        // Default constructor required for Firestore
    }

    public LedgerBook(String id, String name, String currency, String ownerId, long createdAt) {
        this.id = id;
        this.name = name;
        this.currency = currency;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
        this.totalCashIn = 0.0;
        this.totalCashOut = 0.0;
        this.netBalance = 0.0;
        this.members = new HashMap<>();
        this.members.put(ownerId, "ADMIN");
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public double getTotalCashIn() { return totalCashIn; }
    public void setTotalCashIn(double totalCashIn) { this.totalCashIn = totalCashIn; }

    public double getTotalCashOut() { return totalCashOut; }
    public void setTotalCashOut(double totalCashOut) { this.totalCashOut = totalCashOut; }

    public double getNetBalance() { return netBalance; }
    public void setNetBalance(double netBalance) { this.netBalance = netBalance; }

    public Map<String, String> getMembers() { return members; }
    public void setMembers(Map<String, String> members) { this.members = members; }

    public static LedgerBook fromDocument(DocumentSnapshot doc) {
        LedgerBook book = new LedgerBook();
        book.setId(doc.getId());
        book.setName(doc.getString("name"));
        book.setCurrency(doc.getString("currency"));
        book.setOwnerId(doc.getString("ownerId"));
        book.setCreatedAt(doc.getLong("createdAt") != null ? doc.getLong("createdAt") : 0L);
        book.setTotalCashIn(doc.getDouble("totalCashIn") != null ? doc.getDouble("totalCashIn") : 0.0);
        book.setTotalCashOut(doc.getDouble("totalCashOut") != null ? doc.getDouble("totalCashOut") : 0.0);
        book.setNetBalance(doc.getDouble("netBalance") != null ? doc.getDouble("netBalance") : 0.0);
        
        Object membersObj = doc.get("members");
        if (membersObj instanceof Map) {
            book.setMembers((Map<String, String>) membersObj);
        } else {
            book.setMembers(new HashMap<>());
        }
        return book;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("currency", currency);
        map.put("ownerId", ownerId);
        map.put("createdAt", createdAt);
        map.put("totalCashIn", totalCashIn);
        map.put("totalCashOut", totalCashOut);
        map.put("netBalance", netBalance);
        map.put("members", members);
        return map;
    }
}
