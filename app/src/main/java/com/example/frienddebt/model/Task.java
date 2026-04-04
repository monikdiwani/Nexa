package com.example.frienddebt.model;

import com.google.firebase.firestore.DocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

public class Task {
    private String id;
    private String title;
    private String description;
    private Long dueDate; // Milliseconds, nullable
    private String priority; // HIGH, MEDIUM, LOW
    private boolean isCompleted;
    private long createdAt;
    private Long completedAt;

    public Task() {
        // Required for Firestore
    }

    public Task(String id, String title, String description, Long dueDate, String priority, boolean isCompleted, long createdAt, Long completedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.priority = priority;
        this.isCompleted = isCompleted;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getDueDate() { return dueDate; }
    public void setDueDate(Long dueDate) { this.dueDate = dueDate; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }

    public static Task fromDocument(DocumentSnapshot doc) {
        Task t = new Task();
        t.setId(doc.getId());
        t.setTitle(doc.getString("title"));
        t.setDescription(doc.getString("description"));
        t.setDueDate(doc.getLong("dueDate"));
        t.setPriority(doc.getString("priority"));
        t.setCompleted(doc.getBoolean("isCompleted") != null ? doc.getBoolean("isCompleted") : false);
        t.setCreatedAt(doc.getLong("createdAt") != null ? doc.getLong("createdAt") : 0L);
        t.setCompletedAt(doc.getLong("completedAt"));
        return t;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("description", description);
        map.put("dueDate", dueDate);
        map.put("priority", priority);
        map.put("isCompleted", isCompleted);
        map.put("createdAt", createdAt);
        map.put("completedAt", completedAt);
        return map;
    }
}
