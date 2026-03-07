package com.example.frienddebt.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Transaction implements Serializable {

    public enum Type { EXPENSE }

    private static long NEXT_ID = 1;

    private long id;                 // Local ID (for old/offline logic)
    private String firestoreId;      // ✅ Firestore document ID

    private Type type;
    private Group group;
    private User paidBy;
    private List<User> sharedWith;
    private double amount;
    private long timestamp;
    private String description;

    // Optional title (Dinner, Taxi, etc.)
    private String title;

    public Transaction(Group group,
                       User paidBy,
                       List<User> sharedWith,
                       double amount,
                       String description,
                       long timestamp) {

        this.id = NEXT_ID++;
        this.type = Type.EXPENSE;
        this.group = group;
        this.paidBy = paidBy;
        this.sharedWith = sharedWith;
        this.amount = amount;
        this.description = description;
        this.timestamp = timestamp;

        // Default title = description
        this.title = description;
    }

    // ------------------ LOCAL ID ------------------
    public long getId() {
        return id;
    }

    // ------------------ FIRESTORE ID ------------------
    public String getFirestoreId() {
        return firestoreId;
    }

    public void setFirestoreId(String firestoreId) {
        this.firestoreId = firestoreId;
    }

    // ------------------ GETTERS ------------------
    public Group getGroup() {
        return group;
    }

    public User getPaidBy() {
        return paidBy;
    }

    public List<User> getSharedWith() {
        return sharedWith;
    }

    public double getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getDescription() {
        return description;
    }

    // ------------------ TITLE SUPPORT ------------------
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    // ------------------ PARTICIPANT NAME HELPER ------------------
    public List<String> getParticipantNames() {
        List<String> names = new ArrayList<>();
        if (sharedWith == null) return names;

        for (User u : sharedWith) {
            if (u != null && u.getName() != null) {
                names.add(u.getName());
            }
        }
        return names;
    }
}
