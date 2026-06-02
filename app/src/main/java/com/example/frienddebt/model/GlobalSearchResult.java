package com.example.frienddebt.model;

public class GlobalSearchResult {

    public static final String TYPE_TASK = "TASK";
    public static final String TYPE_NOTE = "NOTE";
    public static final String TYPE_MONEY = "MONEY";
    public static final String TYPE_REMINDER = "REMINDER";
    public static final String TYPE_LEDGER = "LEDGER";

    private String id;
    private String type;
    private String title;
    private String subtitle;
    private String icon;
    private long timestamp;
    private Object data; // Optional raw data
    private String parentId; // Used for cashbook entries (ledgerId)

    public GlobalSearchResult(String id, String type, String title, String subtitle, String icon, long timestamp) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getIcon() {
        return icon;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
}
