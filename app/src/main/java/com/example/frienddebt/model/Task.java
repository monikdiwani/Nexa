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
    
    // Phase 17: Advanced Tasks
    private boolean isArchived;
    private java.util.List<Subtask> subtasks;
    
    // Phase 19: Productivity Polish
    private boolean isImportant;
    private Long dueTime;
    private String recurringPattern; // NONE, DAILY, WEEKLY, MONTHLY, YEARLY
    
    // Phase 24: Recurring Engine
    private boolean isRecurring;
    private Long nextOccurrence;
    private String recurringId;
    
    // Phase 28: Cross-Module Linking
    private String linkedItemId;
    private String linkedItemType;

    public Task() {
        // Required for Firestore
        this.subtasks = new java.util.ArrayList<>();
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
        this.isArchived = false;
        this.isImportant = false;
        this.recurringPattern = "NONE";
        this.subtasks = new java.util.ArrayList<>();
    }

    // Getters and Setters
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
    
    public boolean isArchived() { return isArchived; }
    public void setArchived(boolean archived) { isArchived = archived; }
    
    public boolean isImportant() { return isImportant; }
    public void setImportant(boolean important) { isImportant = important; }
    
    public Long getDueTime() { return dueTime; }
    public void setDueTime(Long dueTime) { this.dueTime = dueTime; }
    
    public String getRecurringPattern() { return recurringPattern; }
    public void setRecurringPattern(String recurringPattern) { this.recurringPattern = recurringPattern; }
    
    public boolean isRecurring() { return isRecurring; }
    public void setRecurring(boolean recurring) { isRecurring = recurring; }
    
    public Long getNextOccurrence() { return nextOccurrence; }
    public void setNextOccurrence(Long nextOccurrence) { this.nextOccurrence = nextOccurrence; }
    
    public String getRecurringId() { return recurringId; }
    public void setRecurringId(String recurringId) { this.recurringId = recurringId; }
    
    public java.util.List<Subtask> getSubtasks() { return subtasks; }
    public void setSubtasks(java.util.List<Subtask> subtasks) { this.subtasks = subtasks; }
    
    public String getLinkedItemId() { return linkedItemId; }
    public void setLinkedItemId(String linkedItemId) { this.linkedItemId = linkedItemId; }
    
    public String getLinkedItemType() { return linkedItemType; }
    public void setLinkedItemType(String linkedItemType) { this.linkedItemType = linkedItemType; }

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
        
        t.setArchived(doc.getBoolean("isArchived") != null ? doc.getBoolean("isArchived") : false);
        t.setImportant(doc.getBoolean("isImportant") != null ? doc.getBoolean("isImportant") : false);
        t.setDueTime(doc.getLong("dueTime"));
        t.setRecurringPattern(doc.getString("recurringPattern") != null ? doc.getString("recurringPattern") : "NONE");
        
        t.setRecurring(doc.getBoolean("isRecurring") != null ? doc.getBoolean("isRecurring") : false);
        t.setNextOccurrence(doc.getLong("nextOccurrence"));
        t.setRecurringId(doc.getString("recurringId"));
        
        t.setLinkedItemId(doc.getString("linkedItemId"));
        t.setLinkedItemType(doc.getString("linkedItemType"));
        
        java.util.List<java.util.Map<String, Object>> subtasksMapList = (java.util.List<java.util.Map<String, Object>>) doc.get("subtasks");
        if (subtasksMapList != null) {
            java.util.List<Subtask> subtasks = new java.util.ArrayList<>();
            for (java.util.Map<String, Object> map : subtasksMapList) {
                Subtask subtask = new Subtask();
                subtask.setTitle((String) map.get("title"));
                Boolean completed = (Boolean) map.get("isCompleted");
                subtask.setCompleted(completed != null && completed);
                subtasks.add(subtask);
            }
            t.setSubtasks(subtasks);
        }
        
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
        map.put("isArchived", isArchived);
        map.put("isImportant", isImportant);
        map.put("dueTime", dueTime);
        map.put("recurringPattern", recurringPattern);
        
        map.put("isRecurring", isRecurring);
        map.put("nextOccurrence", nextOccurrence);
        map.put("recurringId", recurringId);
        
        map.put("linkedItemId", linkedItemId);
        map.put("linkedItemType", linkedItemType);
        
        if (subtasks != null) {
            java.util.List<java.util.Map<String, Object>> subtasksMapList = new java.util.ArrayList<>();
            for (Subtask subtask : subtasks) {
                java.util.Map<String, Object> subMap = new java.util.HashMap<>();
                subMap.put("title", subtask.getTitle());
                subMap.put("isCompleted", subtask.isCompleted());
                subtasksMapList.add(subMap);
            }
            map.put("subtasks", subtasksMapList);
        }
        
        return map;
    }
    
    public static class Subtask {
        private String title;
        private boolean isCompleted;
        
        public Subtask() {}
        
        public Subtask(String title, boolean isCompleted) {
            this.title = title;
            this.isCompleted = isCompleted;
        }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public boolean isCompleted() { return isCompleted; }
        public void setCompleted(boolean completed) { isCompleted = completed; }
    }
}
