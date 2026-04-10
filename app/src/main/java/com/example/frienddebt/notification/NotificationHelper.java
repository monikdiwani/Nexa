package com.example.frienddebt.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.frienddebt.ui.DashboardActivity;

public class NotificationHelper {

    public static final String CHANNEL_REMINDERS_ID = "NEXA_REMINDERS";
    public static final String CHANNEL_REMINDERS_NAME = "Nexa Reminders";

    public static final String CHANNEL_SUMMARIES_ID = "NEXA_SUMMARIES";
    public static final String CHANNEL_SUMMARIES_NAME = "Nexa Summaries";

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;

            // Alarms & Reminders channel (high importance, sound)
            NotificationChannel remindersChannel = new NotificationChannel(
                    CHANNEL_REMINDERS_ID,
                    CHANNEL_REMINDERS_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            remindersChannel.setDescription("Notifications for set alarms and scheduled reminders");
            remindersChannel.enableVibration(true);
            remindersChannel.setBypassDnd(true);

            // Daily & Night summaries channel (default importance)
            NotificationChannel summariesChannel = new NotificationChannel(
                    CHANNEL_SUMMARIES_ID,
                    CHANNEL_SUMMARIES_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            summariesChannel.setDescription("Daily summaries of cashbook, tasks, and notes");

            manager.createNotificationChannel(remindersChannel);
            manager.createNotificationChannel(summariesChannel);
        }
    }

    public static void showNotification(Context context, String channelId, int notificationId, String title, String message, Intent clickIntent, Intent action1Intent, String action1Title, Intent action2Intent, String action2Title) {
        createNotificationChannels(context);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        PendingIntent clickPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                clickIntent != null ? clickIntent : new Intent(context, DashboardActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(com.example.frienddebt.R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setVibrate(new long[]{0, 500, 250, 500})
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS | NotificationCompat.DEFAULT_SOUND)
                .setContentIntent(clickPendingIntent)
                .setPriority(CHANNEL_REMINDERS_ID.equals(channelId) ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT);

        if (action1Intent != null && action1Title != null) {
            PendingIntent action1PendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId + 100,
                    action1Intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(0, action1Title, action1PendingIntent);
        }

        if (action2Intent != null && action2Title != null) {
            PendingIntent action2PendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId + 200,
                    action2Intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(0, action2Title, action2PendingIntent);
        }

        manager.notify(notificationId, builder.build());
    }
}
