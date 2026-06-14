package com.example.frienddebt;

import android.app.Application;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class NexaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        android.content.SharedPreferences sp = getSharedPreferences("NexaPrefs", MODE_PRIVATE);
        boolean workersScheduled = sp.getBoolean("workers_scheduled", false);
        if (!workersScheduled) {
            androidx.work.PeriodicWorkRequest morningWork = new androidx.work.PeriodicWorkRequest.Builder(
                com.example.frienddebt.notification.DailySummaryWorker.class, 24, java.util.concurrent.TimeUnit.HOURS)
                .build();
            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "NEXA_MORNING_DIGEST", androidx.work.ExistingPeriodicWorkPolicy.KEEP, morningWork);
            
            androidx.work.PeriodicWorkRequest nightWork = new androidx.work.PeriodicWorkRequest.Builder(
                com.example.frienddebt.notification.NightSummaryWorker.class, 24, java.util.concurrent.TimeUnit.HOURS)
                .build();
            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "NEXA_NIGHT_SUMMARY", androidx.work.ExistingPeriodicWorkPolicy.KEEP, nightWork);
                
            sp.edit().putBoolean("workers_scheduled", true).apply();
        }
    }
}
