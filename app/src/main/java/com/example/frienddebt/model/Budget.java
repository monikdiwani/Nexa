package com.example.frienddebt.model;

import com.google.firebase.firestore.DocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

public class Budget {
    private String id;
    private String category;
    private double amountLimit;
    private String period; // e.g. "MONTHLY"
    private long createdAt;

    // Transient field for dynamic calculation (not saved to Firestore)
    private double spentAmount;

    public Budget() {}

    public Budget(String id, String category, double amountLimit, String period, long createdAt) {
        this.id = id;
        this.category = category;
        this.amountLimit = amountLimit;
        this.period = period;
        this.createdAt = createdAt;
        this.spentAmount = 0.0;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getAmountLimit() { return amountLimit; }
    public void setAmountLimit(double amountLimit) { this.amountLimit = amountLimit; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public double getSpentAmount() { return spentAmount; }
    public void setSpentAmount(double spentAmount) { this.spentAmount = spentAmount; }

    public static Budget fromDocument(DocumentSnapshot doc) {
        Budget b = new Budget();
        b.setId(doc.getId());
        b.setCategory(doc.getString("category"));
        b.setAmountLimit(doc.getDouble("amountLimit") != null ? doc.getDouble("amountLimit") : 0.0);
        b.setPeriod(doc.getString("period") != null ? doc.getString("period") : "MONTHLY");
        b.setCreatedAt(doc.getLong("createdAt") != null ? doc.getLong("createdAt") : 0L);
        return b;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("category", category);
        map.put("amountLimit", amountLimit);
        map.put("period", period);
        map.put("createdAt", createdAt);
        return map;
    }
}
