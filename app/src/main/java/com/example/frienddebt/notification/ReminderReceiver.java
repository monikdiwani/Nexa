package com.example.frienddebt.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.frienddebt.model.Reminder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";

    public static final String ACTION_COMPLETE = "com.example.frienddebt.ACTION_COMPLETE";
    public static final String ACTION_SNOOZE = "com.example.frienddebt.ACTION_SNOOZE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String reminderId = intent.getStringExtra("REMINDER_ID");

        if (reminderId == null) return;

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String userId = intent.getStringExtra("USER_ID");
        if (userId == null && auth.getCurrentUser() != null) {
            userId = auth.getCurrentUser().getUid();
        }

        if (ACTION_COMPLETE.equals(action)) {
            if (userId == null) {
                Log.w(TAG, "Cannot mark complete: USER_ID is null");
                return;
            }
            db.collection("users")
                    .document(userId)
                    .collection("reminders")
                    .document(reminderId)
                    .update(
                            "isCompleted", true,
                            "completedAt", System.currentTimeMillis()
                    )
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Reminder marked complete: " + reminderId);
                        ReminderScheduler.cancelReminder(context, reminderId);
                        android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                        if (nm != null) {
                            nm.cancel(reminderId.hashCode());
                        }
                    });

        } else if (ACTION_SNOOZE.equals(action)) {
            if (userId == null) {
                Log.w(TAG, "Cannot snooze: USER_ID is null");
                return;
            }
            long snoozeUntil = System.currentTimeMillis() + 15 * 60 * 1000;

            final String finalUserId = userId;
            db.collection("users")
                    .document(finalUserId)
                    .collection("reminders")
                    .document(reminderId)
                    .update(
                            "isSnoozed", true,
                            "snoozeUntil", snoozeUntil
                    )
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Reminder snoozed: " + reminderId);
                        db.collection("users")
                                .document(finalUserId)
                                .collection("reminders")
                                .document(reminderId)
                                .get()
                                .addOnSuccessListener(documentSnapshot -> {
                                    if (documentSnapshot.exists()) {
                                        Reminder r = Reminder.fromDocument(documentSnapshot);
                                        ReminderScheduler.scheduleReminder(context, r);
                                    }
                                });

                        android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                        if (nm != null) {
                            nm.cancel(reminderId.hashCode());
                        }
                    });

        } else {
            String title = intent.getStringExtra("REMINDER_TITLE");
            String msg = intent.getStringExtra("REMINDER_MSG");

            if (title == null) title = "Reminder";
            if (msg == null) msg = "Nexa Reminder Alert";

            Intent clickIntent = new Intent(context, com.example.frienddebt.ui.RemindersActivity.class);

            Intent completeIntent = new Intent(context, ReminderReceiver.class);
            completeIntent.setAction(ACTION_COMPLETE);
            completeIntent.putExtra("REMINDER_ID", reminderId);
            completeIntent.putExtra("USER_ID", userId);

            Intent snoozeIntent = new Intent(context, ReminderReceiver.class);
            snoozeIntent.setAction(ACTION_SNOOZE);
            snoozeIntent.putExtra("REMINDER_ID", reminderId);
            snoozeIntent.putExtra("USER_ID", userId);

            NotificationHelper.showNotification(
                    context,
                    NotificationHelper.CHANNEL_REMINDERS_ID,
                    reminderId.hashCode(),
                    title,
                    msg,
                    clickIntent,
                    completeIntent,
                    "Complete",
                    snoozeIntent,
                    "Snooze (15m)"
            );

            if (userId != null) {
                final String finalUserId = userId;
                db.collection("users")
                        .document(finalUserId)
                        .collection("reminders")
                        .document(reminderId)
                        .get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                Reminder r = Reminder.fromDocument(doc);
                                if (r.getRecurringPattern() != null && !"NONE".equals(r.getRecurringPattern())) {
                                    scheduleNextRecurringOccurrence(context, r, db, finalUserId);
                                }
                            }
                        });
            }
        }
    }

    private void scheduleNextRecurringOccurrence(Context context, Reminder r, FirebaseFirestore db, String userId) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(r.getTriggerTime());

        switch (r.getRecurringPattern()) {
            case "DAILY":
                cal.add(Calendar.DAY_OF_YEAR, 1);
                break;
            case "WEEKLY":
                cal.add(Calendar.WEEK_OF_YEAR, 1);
                break;
            case "MONTHLY":
                cal.add(Calendar.MONTH, 1);
                break;
            case "YEARLY":
                cal.add(Calendar.YEAR, 1);
                break;
        }

        long nextTrigger = cal.getTimeInMillis();

        db.collection("users")
                .document(userId)
                .collection("reminders")
                .document(r.getId())
                .update(
                        "triggerTime", nextTrigger,
                        "isSnoozed", false,
                        "snoozeUntil", null
                )
                .addOnSuccessListener(aVoid -> {
                    r.setTriggerTime(nextTrigger);
                    r.setSnoozed(false);
                    r.setSnoozeUntil(null);
                    ReminderScheduler.scheduleReminder(context, r);
                });
    }
}
