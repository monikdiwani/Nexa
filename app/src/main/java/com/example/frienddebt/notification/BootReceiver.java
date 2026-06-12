package com.example.frienddebt.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Rescheduled ALL alarms + WorkManager periodic workers after device boot.
 * Handles BOOT_COMPLETED, QUICKBOOT_POWERON (HTC/LG), and LOCKED_BOOT_COMPLETED.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {

            Log.d(TAG, "Boot detected (" + action + "). Re-scheduling reminders + workers...");

            // 1. Re-schedule all reminder alarms
            ReminderScheduler.rescheduleAllReminders(context);

            // 2. Re-enqueue WorkManager periodic jobs (KEEP_EXISTING avoids duplicates)
            scheduleWorkers(context);

            Log.d(TAG, "Boot rescheduling complete.");
        }
    }

    public static void scheduleWorkers(Context context) {
        WorkManager wm = WorkManager.getInstance(context);

        Constraints netConstraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Morning digest — run daily, target 8 AM
        Calendar morningCal = Calendar.getInstance();
        morningCal.set(Calendar.HOUR_OF_DAY, 8);
        morningCal.set(Calendar.MINUTE, 0);
        morningCal.set(Calendar.SECOND, 0);
        if (morningCal.getTimeInMillis() < System.currentTimeMillis()) {
            morningCal.add(Calendar.DAY_OF_YEAR, 1);
        }
        long morningDelay = morningCal.getTimeInMillis() - System.currentTimeMillis();

        PeriodicWorkRequest morningWork = new PeriodicWorkRequest.Builder(
                DailySummaryWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(morningDelay, TimeUnit.MILLISECONDS)
                .setConstraints(netConstraints)
                .build();
        wm.enqueueUniquePeriodicWork(
                "NEXA_MORNING_DIGEST",
                ExistingPeriodicWorkPolicy.KEEP,
                morningWork);

        // Evening summary — run daily, target 9 PM
        Calendar eveningCal = Calendar.getInstance();
        eveningCal.set(Calendar.HOUR_OF_DAY, 21);
        eveningCal.set(Calendar.MINUTE, 0);
        eveningCal.set(Calendar.SECOND, 0);
        if (eveningCal.getTimeInMillis() < System.currentTimeMillis()) {
            eveningCal.add(Calendar.DAY_OF_YEAR, 1);
        }
        long eveningDelay = eveningCal.getTimeInMillis() - System.currentTimeMillis();

        PeriodicWorkRequest eveningWork = new PeriodicWorkRequest.Builder(
                NightSummaryWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(eveningDelay, TimeUnit.MILLISECONDS)
                .setConstraints(netConstraints)
                .build();
        wm.enqueueUniquePeriodicWork(
                "NEXA_EVENING_DIGEST",
                ExistingPeriodicWorkPolicy.KEEP,
                eveningWork);

        Log.d(TAG, "WorkManager periodic jobs re-enqueued.");
    }
}
