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
        return map;
    }
}
