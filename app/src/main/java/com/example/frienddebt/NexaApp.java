package com.example.frienddebt;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.example.frienddebt.notification.BootReceiver;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class NexaApp extends Application {

    private static final String TAG = "NexaApp";
    private static final String KEY_WORKERS_SCHEDULED = "workers_scheduled";

    @Override
    public void onCreate() {
        super.onCreate();

        // Enable Firestore Offline Persistence globally
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        db.setFirestoreSettings(settings);

        // Schedule WorkManager periodic jobs once on first run. BootReceiver will re-schedule after device reboot.
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean scheduled = prefs.getBoolean(KEY_WORKERS_SCHEDULED, false);
            if (!scheduled) {
                Log.d(TAG, "Scheduling WorkManager periodic workers on first run");
                BootReceiver.scheduleWorkers(getApplicationContext());
                prefs.edit().putBoolean(KEY_WORKERS_SCHEDULED, true).apply();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to schedule workers on startup: " + e.getMessage());
        }
    }
}
