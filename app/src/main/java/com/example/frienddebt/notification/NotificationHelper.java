package com.example.frienddebt.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.frienddebt.ui.DashboardActivity;

public class NotificationHelper {

    public static final String CHANNEL_REMINDERS_ID      = "NEXA_REMINDERS";
    public static final String CHANNEL_REMINDERS_NAME    = "Nexa Reminders";

    public static final String CHANNEL_SUMMARIES_ID      = "NEXA_SUMMARIES";
    public static final String CHANNEL_SUMMARIES_NAME    = "Nexa Summaries";

    // HIGH PRIORITY — for HIGH-priority reminders (alarm-style)
    public static final String CHANNEL_HIGH_PRIORITY_ID   = "NEXA_HIGH_PRIORITY";
    public static final String CHANNEL_HIGH_PRIORITY_NAME = "Nexa Urgent Reminders";

    // SharedPrefs keys for notification type toggles
    private static final String PREFS_NAME        = "NexaNotifPrefs";
    public static final String KEY_REMINDERS      = "notif_reminders";
    public static final String KEY_DIGEST         = "notif_digest";
    public static final String KEY_TASKS          = "notif_tasks";
    public static final String KEY_MONEY          = "notif_money";
    public static final String KEY_ACTIVITY       = "notif_activity";

    /**
     * Returns true if the given notification type is enabled by the user.
     * Defaults to true (on) for all types.
     */
    public static boolean shouldNotify(Context context, String typeKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(typeKey, true);
    }

    public static void setNotificationEnabled(Context context, String typeKey, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(typeKey, enabled).apply();
    }

    public static boolean isNotificationEnabled(Context context, String typeKey) {
        return shouldNotify(context, typeKey);
    }

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;

            // Standard reminders channel (high importance, sound)
            NotificationChannel remindersChannel = new NotificationChannel(
                    CHANNEL_REMINDERS_ID, CHANNEL_REMINDERS_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            remindersChannel.setDescription("Notifications for set alarms and scheduled reminders");
            remindersChannel.enableVibration(true);
            remindersChannel.setBypassDnd(true);

            // HIGH PRIORITY channel (max importance, alarm sound, persistent vibration)
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            NotificationChannel highChannel = new NotificationChannel(
                    CHANNEL_HIGH_PRIORITY_ID, CHANNEL_HIGH_PRIORITY_NAME,
                    NotificationManager.IMPORTANCE_MAX);
            highChannel.setDescription("Urgent reminders — used for HIGH priority");
            highChannel.enableVibration(true);
            highChannel.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500});
            highChannel.setBypassDnd(true);
            highChannel.setShowBadge(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                highChannel.setSound(alarmUri, attrs);
            }

            // Summaries channel (default importance)
            NotificationChannel summariesChannel = new NotificationChannel(
                    CHANNEL_SUMMARIES_ID, CHANNEL_SUMMARIES_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);
            summariesChannel.setDescription("Daily summaries of cashbook, tasks, and notes");

            manager.createNotificationChannel(remindersChannel);
            manager.createNotificationChannel(highChannel);
            manager.createNotificationChannel(summariesChannel);
        }
    }

    public static void showNotification(Context context, String channelId, int notificationId,
                                        String title, String message,
                                        Intent clickIntent,
                                        Intent action1Intent, String action1Title,
                                        Intent action2Intent, String action2Title) {
        createNotificationChannels(context);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        PendingIntent clickPendingIntent = PendingIntent.getActivity(
                context, notificationId,
                clickIntent != null ? clickIntent : new Intent(context, DashboardActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        boolean isHigh = CHANNEL_HIGH_PRIORITY_ID.equals(channelId);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(com.example.frienddebt.R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setVibrate(isHigh
                        ? new long[]{0, 500, 200, 500, 200, 500}
                        : new long[]{0, 500, 250, 500})
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS | NotificationCompat.DEFAULT_SOUND)
                .setContentIntent(clickPendingIntent)
                .setPriority(isHigh
                        ? NotificationCompat.PRIORITY_MAX
                        : (CHANNEL_REMINDERS_ID.equals(channelId)
                                ? NotificationCompat.PRIORITY_HIGH
                                : NotificationCompat.PRIORITY_DEFAULT));

        if (isHigh) {
            // Full-screen intent for heads-up on locked screen
            builder.setFullScreenIntent(clickPendingIntent, true);
            builder.setCategory(NotificationCompat.CATEGORY_ALARM);
        }

        if (action1Intent != null && action1Title != null) {
            PendingIntent a1 = PendingIntent.getBroadcast(context, notificationId + 100,
                    action1Intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(0, action1Title, a1);
        }

        if (action2Intent != null && action2Title != null) {
            PendingIntent a2 = PendingIntent.getBroadcast(context, notificationId + 200,
                    action2Intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(0, action2Title, a2);
        }

        manager.notify(notificationId, builder.build());
    }
}
