package com.example.frienddebt.model;

import java.io.Serializable;

public class User implements Serializable {

    private static long NEXT_ID = 1;

    private long id;
    private String name;

    public User(String name) {
        this.id = NEXT_ID++;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // ✅ VERY IMPORTANT FIX
    // Ensures the same user is treated as the same everywhere
    // (HashMap keys, comparisons, settlement logic)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;
        return id == user.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
