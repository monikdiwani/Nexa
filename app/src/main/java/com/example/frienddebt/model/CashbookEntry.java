package com.example.frienddebt.model;

import com.google.firebase.firestore.DocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

public class CashbookEntry {
    private String id;
    private String bookId;
    private long date;
    private String particulars;
    private String type; // CASH_IN or CASH_OUT
    private String medium; // CASH or BANK
    private double amount;
    private String category;
    private String note;
    
    // New fields for Super App
    private String billImageUrl;
    private String contactName;
    private String contactPhone;
    private String createdBy;
    private String createdByName;
    private long lastModifiedAt;
    
    // Shared Expense fields
    private String paidBy; // User ID who paid
    private String splitMethod; // EQUAL, PERCENTAGE, EXACT
    private java.util.List<String> participants; // User IDs involved
    private java.util.Map<String, Double> splits; // User ID -> Exact amount they owe
    
    // Recurring fields
    private boolean isRecurring;
    private String recurringPattern; // NONE, DAILY, WEEKLY, MONTHLY, YEARLY
    private Long nextOccurrence;
    private String recurringId;

    private long createdAt;

    public CashbookEntry() {
        // Required for Firestore
    }

    public CashbookEntry(String id, String bookId, long date, String particulars, String type, String medium, double amount, String category, String note, long createdAt) {
        this.id = id;
        this.bookId = bookId;
        this.date = date;
        this.particulars = particulars;
        this.type = type;
        this.medium = medium;
        this.amount = amount;
        this.category = category;
        this.note = note;
        this.createdAt = createdAt;
        this.lastModifiedAt = createdAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }

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

    public String getBillImageUrl() { return billImageUrl; }
    public void setBillImageUrl(String billImageUrl) { this.billImageUrl = billImageUrl; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public long getLastModifiedAt() { return lastModifiedAt; }
    public void setLastModifiedAt(long lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }

    public String getPaidBy() { return paidBy; }
    public void setPaidBy(String paidBy) { this.paidBy = paidBy; }

    public String getSplitMethod() { return splitMethod; }
    public void setSplitMethod(String splitMethod) { this.splitMethod = splitMethod; }

    public java.util.List<String> getParticipants() { return participants; }
    public void setParticipants(java.util.List<String> participants) { this.participants = participants; }

    public java.util.Map<String, Double> getSplits() { return splits; }
    public void setSplits(java.util.Map<String, Double> splits) { this.splits = splits; }

    public boolean isRecurring() { return isRecurring; }
    public void setRecurring(boolean recurring) { isRecurring = recurring; }

    public String getRecurringPattern() { return recurringPattern; }
    public void setRecurringPattern(String recurringPattern) { this.recurringPattern = recurringPattern; }

    public Long getNextOccurrence() { return nextOccurrence; }
    public void setNextOccurrence(Long nextOccurrence) { this.nextOccurrence = nextOccurrence; }

    public String getRecurringId() { return recurringId; }
    public void setRecurringId(String recurringId) { this.recurringId = recurringId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public static CashbookEntry fromDocument(DocumentSnapshot doc) {
        CashbookEntry entry = new CashbookEntry();
        entry.setId(doc.getId());
        entry.setBookId(doc.getString("bookId"));
        entry.setDate(doc.getLong("date") != null ? doc.getLong("date") : 0L);
        entry.setParticulars(doc.getString("particulars"));
        entry.setType(doc.getString("type"));
        entry.setMedium(doc.getString("medium"));
        entry.setAmount(doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0);
        entry.setCategory(doc.getString("category"));
        entry.setNote(doc.getString("note"));
        
        entry.setBillImageUrl(doc.getString("billImageUrl"));
        entry.setContactName(doc.getString("contactName"));
        entry.setContactPhone(doc.getString("contactPhone"));
        entry.setCreatedBy(doc.getString("createdBy"));
        entry.setCreatedByName(doc.getString("createdByName"));
        entry.setLastModifiedAt(doc.getLong("lastModifiedAt") != null ? doc.getLong("lastModifiedAt") : 0L);
        
        entry.setPaidBy(doc.getString("paidBy"));
        entry.setSplitMethod(doc.getString("splitMethod"));
        entry.setParticipants((java.util.List<String>) doc.get("participants"));
        entry.setSplits((java.util.Map<String, Double>) doc.get("splits"));
        
        entry.setRecurring(doc.getBoolean("isRecurring") != null ? doc.getBoolean("isRecurring") : false);
        entry.setRecurringPattern(doc.getString("recurringPattern"));
        entry.setNextOccurrence(doc.getLong("nextOccurrence"));
        entry.setRecurringId(doc.getString("recurringId"));
        
        entry.setCreatedAt(doc.getLong("createdAt") != null ? doc.getLong("createdAt") : 0L);
        return entry;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("bookId", bookId);
        map.put("date", date);
        map.put("particulars", particulars);
        map.put("type", type);
        map.put("medium", medium);
        map.put("amount", amount);
        map.put("category", category);
        map.put("note", note);
        
        map.put("billImageUrl", billImageUrl);
        map.put("contactName", contactName);
        map.put("contactPhone", contactPhone);
        map.put("createdBy", createdBy);
        map.put("createdByName", createdByName);
        map.put("lastModifiedAt", lastModifiedAt);
        
        map.put("paidBy", paidBy);
        map.put("splitMethod", splitMethod);
        map.put("participants", participants);
        map.put("splits", splits);
        
        map.put("isRecurring", isRecurring);
        map.put("recurringPattern", recurringPattern);
        map.put("nextOccurrence", nextOccurrence);
        map.put("recurringId", recurringId);
        
        map.put("createdAt", createdAt);
        return map;
    }
}
