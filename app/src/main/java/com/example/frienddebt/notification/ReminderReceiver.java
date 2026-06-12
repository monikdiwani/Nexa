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
        if (userId == null) {
            android.content.SharedPreferences sp = context.getSharedPreferences("NexaPrefs", Context.MODE_PRIVATE);
            userId = sp.getString("user_id", null);
        }
        if (userId == null && auth.getCurrentUser() != null) {
            userId = auth.getCurrentUser().getUid();
        }

        if (ACTION_COMPLETE.equals(action)) {
            // Dismiss notification immediately for responsive UI
            android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(reminderId.hashCode());
            }
            ReminderScheduler.cancelReminder(context, reminderId);

            // Show Toast immediately
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> android.widget.Toast.makeText(context.getApplicationContext(), "Reminder completed!", android.widget.Toast.LENGTH_SHORT).show());

            if (userId == null) {
                Log.w(TAG, "Cannot mark complete: USER_ID is null");
                return;
            }

            final String finalUserId = userId;
            final BroadcastReceiver.PendingResult pendingResult = goAsync();
            db.collection("users")
                    .document(finalUserId)
                    .collection("reminders")
                    .document(reminderId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) {
                            pendingResult.finish();
                            return;
                        }
                        Reminder r = Reminder.fromDocument(doc);
                        
                        if (r.getRecurringPattern() != null && !"NONE".equals(r.getRecurringPattern())) {
                            // If it's recurring, complete action just means we cancel snooze and let next recurrence take over
                            db.collection("users")
                                    .document(finalUserId)
                                    .collection("reminders")
                                    .document(reminderId)
                                    .update(
                                            "isSnoozed", false,
                                            "snoozeUntil", null
                                    ).addOnCompleteListener(t -> pendingResult.finish());
                        } else {
                            // Non-recurring, mark as completed
                            db.collection("users")
                                    .document(finalUserId)
                                    .collection("reminders")
                                    .document(reminderId)
                                    .update(
                                            "isCompleted", true,
                                            "completedAt", System.currentTimeMillis()
                                    )
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful()) {
                                            Log.d(TAG, "Reminder marked complete in database: " + reminderId);
                                        } else {
                                            Log.e(TAG, "Failed to mark complete in database: " + reminderId, task.getException());
                                        }
                                        pendingResult.finish();
                                    });
                        }
                    });

        } else if (ACTION_SNOOZE.equals(action)) {
            // Dismiss notification immediately
            android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(reminderId.hashCode());
            }

            // Show Toast immediately
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> android.widget.Toast.makeText(context.getApplicationContext(), "Reminder snoozed for 15m", android.widget.Toast.LENGTH_SHORT).show());

            if (userId == null) {
                Log.w(TAG, "Cannot snooze: USER_ID is null");
                return;
            }
            long snoozeUntil = System.currentTimeMillis() + 15 * 60 * 1000;

            final String finalUserId = userId;
            final BroadcastReceiver.PendingResult pendingResult = goAsync();
            db.collection("users")
                    .document(finalUserId)
                    .collection("reminders")
                    .document(reminderId)
                    .update(
                            "isSnoozed", true,
                            "snoozeUntil", snoozeUntil
                    )
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Reminder snoozed in database: " + reminderId);
                            // Reschedule the reminder with snooze time
                            db.collection("users")
                                    .document(finalUserId)
                                    .collection("reminders")
                                    .document(reminderId)
                                    .get()
                                    .addOnCompleteListener(getTask -> {
                                        if (getTask.isSuccessful() && getTask.getResult().exists()) {
                                            Reminder r = Reminder.fromDocument(getTask.getResult());
                                            ReminderScheduler.scheduleReminder(context, r);
                                        }
                                        pendingResult.finish();
                                    });
                        } else {
                            Log.e(TAG, "Failed to snooze in database: " + reminderId, task.getException());
                            pendingResult.finish();
                        }
                    });

        } else {
            String title = intent.getStringExtra("REMINDER_TITLE");
            String msg = intent.getStringExtra("REMINDER_MSG");
            String priority = intent.getStringExtra("REMINDER_PRIORITY");

            // Check user preference
            if (!NotificationHelper.shouldNotify(context, NotificationHelper.KEY_REMINDERS)) {
                Log.d(TAG, "Reminders silenced by user prefs");
                return;
            }

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

            // Choose channel: HIGH priority → alarm-style, others → standard
            String channelId = "HIGH".equalsIgnoreCase(priority)
                    ? NotificationHelper.CHANNEL_HIGH_PRIORITY_ID
                    : NotificationHelper.CHANNEL_REMINDERS_ID;

            NotificationHelper.showNotification(
                    context,
                    channelId,
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
                                    // If it's a snooze alarm firing, do not advance the main recurrence time
                                    if (!r.isSnoozed()) {
                                        scheduleNextRecurringOccurrence(context, r, db, finalUserId);
                                    }
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
