package com.example.frienddebt.model;

import com.google.firebase.firestore.DocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

public class Note {
    private String id;
    private String title;
    private String content;
    private long createdAt;
    private long updatedAt;
    
    // New Fields for Organization
    private String colorCode;
    private String label;
    private boolean isPinned;
    private boolean isArchived;
    private boolean isDeleted;
    private String imageUrl;
    
    // Nexa Notes Blueprint Extensions
    private String type; // "TEXT", "CHECKLIST", "IMAGE"
    private String folder;
    private java.util.List<String> tags;
    private long reminderAt;
    private java.util.List<String> imageUrls;
    private String linkedTaskId;
    private String linkedCashbookId;
    private boolean isLocked;
    private String pageStyle; // "blank", "lined", "dotted", "grid"

    public Note() {
        // Required for Firestore
    }

    public Note(String id, String title, String content, long createdAt, long updatedAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.colorCode = "#FFFFFF"; // Default white
        this.label = "";
        this.isPinned = false;
        this.isArchived = false;
        this.isDeleted = false;
        this.imageUrl = null;
        this.type = "TEXT";
        this.folder = "Personal";
        this.tags = new java.util.ArrayList<>();
        this.reminderAt = 0L;
        this.imageUrls = new java.util.ArrayList<>();
        this.linkedTaskId = null;
        this.linkedCashbookId = null;
        this.isLocked = false;
        this.pageStyle = "blank";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }

    public boolean isArchived() { return isArchived; }
    public void setArchived(boolean archived) { isArchived = archived; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }

    public java.util.List<String> getTags() { return tags; }
    public void setTags(java.util.List<String> tags) { this.tags = tags; }

    public long getReminderAt() { return reminderAt; }
    public void setReminderAt(long reminderAt) { this.reminderAt = reminderAt; }

    public java.util.List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(java.util.List<String> imageUrls) { this.imageUrls = imageUrls; }

    public String getLinkedTaskId() { return linkedTaskId; }
    public void setLinkedTaskId(String linkedTaskId) { this.linkedTaskId = linkedTaskId; }

    public String getLinkedCashbookId() { return linkedCashbookId; }
    public void setLinkedCashbookId(String linkedCashbookId) { this.linkedCashbookId = linkedCashbookId; }

    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) { isLocked = locked; }

    public String getPageStyle() { return pageStyle; }
    public void setPageStyle(String pageStyle) { this.pageStyle = pageStyle; }

    public static Note fromDocument(DocumentSnapshot doc) {
        Note n = new Note();
        n.setId(doc.getId());
        n.setTitle(doc.getString("title"));
        n.setContent(doc.getString("content"));
        n.setCreatedAt(doc.getLong("createdAt") != null ? doc.getLong("createdAt") : 0L);
        n.setUpdatedAt(doc.getLong("updatedAt") != null ? doc.getLong("updatedAt") : 0L);
        
        n.setColorCode(doc.getString("colorCode") != null ? doc.getString("colorCode") : "#FFFFFF");
        n.setLabel(doc.getString("label") != null ? doc.getString("label") : "");
        n.setPinned(doc.getBoolean("isPinned") != null ? doc.getBoolean("isPinned") : false);
        n.setArchived(doc.getBoolean("isArchived") != null ? doc.getBoolean("isArchived") : false);
        n.setDeleted(doc.getBoolean("isDeleted") != null ? doc.getBoolean("isDeleted") : false);
        n.setImageUrl(doc.getString("imageUrl"));
        
        n.setType(doc.getString("type") != null ? doc.getString("type") : "TEXT");
        n.setFolder(doc.getString("folder") != null ? doc.getString("folder") : "Personal");
        n.setReminderAt(doc.getLong("reminderAt") != null ? doc.getLong("reminderAt") : 0L);
        n.setLinkedTaskId(doc.getString("linkedTaskId"));
        n.setLinkedCashbookId(doc.getString("linkedCashbookId"));
        n.setLocked(doc.getBoolean("isLocked") != null ? doc.getBoolean("isLocked") : false);
        n.setPageStyle(doc.getString("pageStyle") != null ? doc.getString("pageStyle") : "blank");
        
        if (doc.get("tags") != null) {
            n.setTags((java.util.List<String>) doc.get("tags"));
        } else {
            n.setTags(new java.util.ArrayList<>());
        }
        
        if (doc.get("imageUrls") != null) {
            n.setImageUrls((java.util.List<String>) doc.get("imageUrls"));
        } else {
            n.setImageUrls(new java.util.ArrayList<>());
        }
        
        return n;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("content", content);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        map.put("colorCode", colorCode);
        map.put("label", label);
        map.put("isPinned", isPinned);
        map.put("isArchived", isArchived);
        map.put("isDeleted", isDeleted);
        map.put("imageUrl", imageUrl);
        
        map.put("type", type);
        map.put("folder", folder);
        map.put("tags", tags);
        map.put("reminderAt", reminderAt);
        map.put("imageUrls", imageUrls);
        map.put("linkedTaskId", linkedTaskId);
        map.put("linkedCashbookId", linkedCashbookId);
        map.put("isLocked", isLocked);
        map.put("pageStyle", pageStyle);
        
        return map;
    }
}
