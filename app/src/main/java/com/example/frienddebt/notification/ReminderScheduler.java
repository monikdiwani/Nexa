package com.example.frienddebt.notification;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.frienddebt.model.Reminder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;

public class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";

    @SuppressLint("ScheduleExactAlarm")
    public static void scheduleReminder(Context context, Reminder reminder) {
        if (reminder == null || reminder.getId() == null) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("REMINDER_ID", reminder.getId());
        intent.putExtra("REMINDER_TITLE", reminder.getTitle());
        intent.putExtra("REMINDER_MSG", reminder.getMessage());

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            intent.putExtra("USER_ID", auth.getCurrentUser().getUid());
        }

        int requestCode = reminder.getId().hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerTime = reminder.isSnoozed() && reminder.getSnoozeUntil() != null ?
                reminder.getSnoozeUntil() : reminder.getTriggerTime();

        if (triggerTime < System.currentTimeMillis()) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                } else {
                    Log.w(TAG, "Exact alarms not allowed, falling back to inexact setAndAllowWhileIdle");
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException scheduling exact alarm, falling back to inexact alarm", se);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                }
            } catch (Exception ex) {
                Log.e(TAG, "Failed to schedule inexact alarm fallback", ex);
            }
        }

        Log.d(TAG, "Scheduled alarm for reminder: " + reminder.getId() + " at " + triggerTime);
    }

    public static void cancelReminder(Context context, String reminderId) {
        if (reminderId == null) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        int requestCode = reminderId.hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            Log.d(TAG, "Cancelled alarm for reminder: " + reminderId);
        }
    }

    public static void rescheduleAllReminders(Context context) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("reminders")
                .whereEqualTo("isCompleted", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Reminder reminder = Reminder.fromDocument(doc);
                        long now = System.currentTimeMillis();
                        long triggerTime = reminder.isSnoozed() && reminder.getSnoozeUntil() != null
                                ? reminder.getSnoozeUntil()
                                : reminder.getTriggerTime();

                        if (triggerTime < now) {
                            long nextTrigger = getNextRecurringTrigger(reminder, now);
                            if (nextTrigger <= 0) {
                                continue;
                            }

                            FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document(userId)
                                    .collection("reminders")
                                    .document(reminder.getId())
                                    .update(
                                            "triggerTime", nextTrigger,
                                            "isSnoozed", false,
                                            "snoozeUntil", null
                                    );

                            reminder.setTriggerTime(nextTrigger);
                            reminder.setSnoozed(false);
                            reminder.setSnoozeUntil(null);
                        }

                        scheduleReminder(context, reminder);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to reschedule reminders: " + e.getMessage()));
    }

    private static long getNextRecurringTrigger(Reminder reminder, long now) {
        if (reminder == null) return -1;
        String pattern = reminder.getRecurringPattern();
        if (pattern == null || "NONE".equalsIgnoreCase(pattern)) return -1;

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(reminder.getTriggerTime());

        while (cal.getTimeInMillis() <= now) {
            switch (pattern) {
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
                default:
                    return -1;
            }
        }

        return cal.getTimeInMillis();
    }
}
