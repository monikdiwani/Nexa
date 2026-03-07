package com.example.frienddebt.data;

import com.example.frienddebt.model.Group;

import java.util.ArrayList;
import java.util.List;

public class AppData {

    private static AppData instance;

    private List<Group> groups = new ArrayList<>();

    private AppData() {
    }

    public static AppData getInstance() {
        if (instance == null) {
            instance = new AppData();
        }
        return instance;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public void addGroup(Group g) {
        groups.add(g);
    }

    public Group getGroupById(String id) {
        for (Group g : groups) {
            if (g.getId() != null && g.getId().equals(id)) {
                return g;
            }
        }
        return null;
    }
}
