package com.example.frienddebt.utils;

import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Utility for saving and batch-fetching user display names from Firestore users/{uid}.
 */
public class UserProfileHelper {

    public interface NamesCallback {
        void onResult(Map<String, String> uidToName);
    }

    /**
     * Save (or update) the current user's profile in users/{uid}.
     * Call this after every successful login / sign-up.
     */
    public static void saveProfile(String uid, String displayName, String email) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> data = new HashMap<>();
        data.put("displayName", displayName != null && !displayName.isEmpty() ? displayName : "Nexa User");
        data.put("email", email != null ? email : "");
        data.put("updatedAt", System.currentTimeMillis());
        db.collection("users").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge());
    }

    /**
     * Batch-fetch display names for a list of UIDs.
     * Returns a map uid → displayName via callback.
     * If a uid cannot be resolved, falls back to "User (xxxx)".
     */
    public static void resolveNames(FirebaseFirestore db, List<String> uids, NamesCallback callback) {
        if (uids == null || uids.isEmpty()) {
            callback.onResult(new HashMap<>());
            return;
        }

        // De-duplicate
        List<String> uniqueUids = new ArrayList<>();
        for (String uid : uids) {
            if (!uniqueUids.contains(uid)) uniqueUids.add(uid);
        }

        db.collection("users")
                .whereIn(FieldPath.documentId(), uniqueUids)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Map<String, String> result = new HashMap<>();
                    // Pre-fill with fallback
                    for (String uid : uniqueUids) {
                        result.put(uid, "User (" + uid.substring(0, Math.min(4, uid.length())) + ")");
                    }
                    // Overwrite with real names
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String name = doc.getString("displayName");
                        if (name != null && !name.isEmpty()) {
                            result.put(doc.getId(), name);
                        }
                    }
                    callback.onResult(result);
                })
                .addOnFailureListener(e -> {
                    // Return fallback on failure
                    Map<String, String> fallback = new HashMap<>();
                    for (String uid : uniqueUids) {
                        fallback.put(uid, "User (" + uid.substring(0, Math.min(4, uid.length())) + ")");
                    }
                    callback.onResult(fallback);
                });
    }

    /**
     * Send a push notification to a specific user via their stored FCM token.
     * Uses a Firestore document in notifications/{targetUid}/pending/ to trigger Cloud Messaging.
     * (Alternatively you can call FCM REST API from here, but Firestore trigger is simpler without a backend.)
     */
    public static void sendJoinRequestNotification(FirebaseFirestore db, String targetUid,
                                                   String requesterName, String bookName) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "JOIN_REQUEST");
        notification.put("title", "New join request 🔔");
        notification.put("body", requesterName + " wants to join \"" + bookName + "\"");
        notification.put("targetUid", targetUid);
        notification.put("createdAt", System.currentTimeMillis());
        notification.put("read", false);

        db.collection("notifications").document(targetUid)
                .collection("pending")
                .add(notification);
    }

    public static void sendExpenseNotification(FirebaseFirestore db, String targetUid,
                                               String actorName, String title, double amount, String bookName) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NEW_EXPENSE");
        notification.put("title", "New Expense in " + bookName + " 💸");
        notification.put("body", actorName + " added \"" + title + "\" for ₹" + String.format(java.util.Locale.getDefault(), "%.2f", amount));
        notification.put("targetUid", targetUid);
        notification.put("createdAt", System.currentTimeMillis());
        notification.put("read", false);

        db.collection("notifications").document(targetUid)
                .collection("pending")
                .add(notification);
    }
}
