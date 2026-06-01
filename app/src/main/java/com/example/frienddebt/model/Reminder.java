package com.example.frienddebt.model;

import com.google.firebase.firestore.DocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

public class Reminder {
    private String id;
    private String title;
    private String message;
    private long triggerTime;
    private String recurringPattern; // NONE, DAILY, WEEKLY, MONTHLY, YEARLY
    private String priority; // HIGH, MEDIUM, LOW
    private String category; // BILL, MEETING, TASK, MEDICINE, SHOPPING, CUSTOM
    private boolean isCompleted;
    private boolean isSnoozed;
    private Long snoozeUntil;
    private long createdAt;
    private Long completedAt;
    private String linkedTaskId;

    public Reminder() {
        // Required for Firestore
    }

    public Reminder(String id, String title, String message, long triggerTime, String recurringPattern, String priority, String category, boolean isCompleted, boolean isSnoozed, Long snoozeUntil, long createdAt, Long completedAt) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.triggerTime = triggerTime;
        this.recurringPattern = recurringPattern;
        this.priority = priority;
        this.category = category;
        this.isCompleted = isCompleted;
        this.isSnoozed = isSnoozed;
        this.snoozeUntil = snoozeUntil;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.linkedTaskId = null;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getTriggerTime() { return triggerTime; }
    public void setTriggerTime(long triggerTime) { this.triggerTime = triggerTime; }

    public String getRecurringPattern() { return recurringPattern; }
    public void setRecurringPattern(String recurringPattern) { this.recurringPattern = recurringPattern; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }

    public boolean isSnoozed() { return isSnoozed; }
    public void setSnoozed(boolean snoozed) { isSnoozed = snoozed; }

    public Long getSnoozeUntil() { return snoozeUntil; }
    public void setSnoozeUntil(Long snoozeUntil) { this.snoozeUntil = snoozeUntil; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }

    public String getLinkedTaskId() { return linkedTaskId; }
    public void setLinkedTaskId(String linkedTaskId) { this.linkedTaskId = linkedTaskId; }

    public static Reminder fromDocument(DocumentSnapshot doc) {
        Reminder r = new Reminder();
        r.setId(doc.getId());
        r.setTitle(doc.getString("title"));
        r.setMessage(doc.getString("message"));
        r.setTriggerTime(doc.getLong("triggerTime") != null ? doc.getLong("triggerTime") : 0L);
        r.setRecurringPattern(doc.getString("recurringPattern"));
        r.setPriority(doc.getString("priority"));
        r.setCategory(doc.getString("category"));
        r.setCompleted(doc.getBoolean("isCompleted") != null ? doc.getBoolean("isCompleted") : false);
        r.setSnoozed(doc.getBoolean("isSnoozed") != null ? doc.getBoolean("isSnoozed") : false);
        r.setSnoozeUntil(doc.getLong("snoozeUntil"));
        r.setCreatedAt(doc.getLong("createdAt") != null ? doc.getLong("createdAt") : 0L);
        r.setCompletedAt(doc.getLong("completedAt"));
        r.setLinkedTaskId(doc.getString("linkedTaskId"));
        return r;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("message", message);
        map.put("triggerTime", triggerTime);
        map.put("recurringPattern", recurringPattern);
        map.put("priority", priority);
        map.put("category", category);
        map.put("isCompleted", isCompleted);
        map.put("isSnoozed", isSnoozed);
        map.put("snoozeUntil", snoozeUntil);
        map.put("createdAt", createdAt);
        map.put("completedAt", completedAt);
        map.put("linkedTaskId", linkedTaskId);
        return map;
    }
}
