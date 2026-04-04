package com.example.frienddebt.model;

import com.google.firebase.firestore.DocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

public class CashbookEntry {
    private String id;
    private long date;
    private String particulars;
    private String type; // CASH_IN or CASH_OUT
    private String medium; // CASH or BANK
    private double amount;
    private String category;
    private String note;
    private long createdAt;

    public CashbookEntry() {
        // Required for Firestore
    }

    public CashbookEntry(String id, long date, String particulars, String type, String medium, double amount, String category, String note, long createdAt) {
        this.id = id;
        this.date = date;
        this.particulars = particulars;
        this.type = type;
        this.medium = medium;
        this.amount = amount;
        this.category = category;
        this.note = note;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public String getParticulars() { return particulars; }
    public void setParticulars(String particulars) { this.particulars = particulars; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMedium() { return medium; }
    public void setMedium(String medium) { this.medium = medium; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public static CashbookEntry fromDocument(DocumentSnapshot doc) {
        CashbookEntry entry = new CashbookEntry();
        entry.setId(doc.getId());
        entry.setDate(doc.getLong("date") != null ? doc.getLong("date") : 0L);
        entry.setParticulars(doc.getString("particulars"));
        entry.setType(doc.getString("type"));
        entry.setMedium(doc.getString("medium"));
        entry.setAmount(doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0);
        entry.setCategory(doc.getString("category"));
        entry.setNote(doc.getString("note"));
        entry.setCreatedAt(doc.getLong("createdAt") != null ? doc.getLong("createdAt") : 0L);
        return entry;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("date", date);
        map.put("particulars", particulars);
        map.put("type", type);
        map.put("medium", medium);
        map.put("amount", amount);
        map.put("category", category);
        map.put("note", note);
        map.put("createdAt", createdAt);
        return map;
    }
}
