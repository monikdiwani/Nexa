package com.example.frienddebt.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Group implements Serializable {

    // ✅ Firestore document ID (MAIN ID)
    private String id;

    // 🔥 Owner userId (needed for joined groups)
    private String ownerId;

    private String name;
    private List<User> members = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();

    // ✅ Constructor for Firestore-loaded OWN groups
    public Group(String id, String name) {
        this.id = id;
        this.name = name;
    }

    // 🔥 Constructor for JOINED groups (Phase 3)
    public Group(String id, String name, String ownerId) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
    }

    // ✅ Simple constructor (temporary/local use)
    public Group(String name) {
        this.name = name;
    }

    // ------------------ GETTERS ------------------

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<User> getMembers() {
        return members;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    // ------------------ MEMBERS ------------------

    public void addMember(User u) {
        if (u == null) return;
        if (!members.contains(u)) {
            members.add(u);
        }
    }

    public User getOrCreateMember(String name) {
        if (name == null) return null;

        String trimmed = name.trim();
        if (trimmed.isEmpty()) return null;

        for (User u : members) {
            if (u.getName().equalsIgnoreCase(trimmed)) {
                return u;
            }
        }

        User u = new User(trimmed);
        members.add(u);
        return u;
    }
}
