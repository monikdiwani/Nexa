package com.example.frienddebt.utils;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class ActivityLogger {
    private static final String TAG = "ActivityLogger";

    public static void log(String ownerId, String groupId, String type, String description) {
        if (ownerId == null || groupId == null || ownerId.isEmpty() || groupId.isEmpty()) {
            Log.e(TAG, "Cannot log activity: ownerId or groupId is null/empty");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();

        String userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "unknown";
        String userName = auth.getCurrentUser() != null ? auth.getCurrentUser().getDisplayName() : "Unknown User";
        if (userName == null || userName.isEmpty()) {
            userName = auth.getCurrentUser() != null && auth.getCurrentUser().getEmail() != null
                    ? auth.getCurrentUser().getEmail().split("@")[0]
                    : "Unknown User";
        }

        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("type", type);
        logEntry.put("userId", userId);
        logEntry.put("userName", userName);
        logEntry.put("description", description);
        logEntry.put("timestamp", System.currentTimeMillis());

        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(groupId)
                .collection("logs")
                .add(logEntry)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to write activity log", e));
    }
}
