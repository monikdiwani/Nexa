package com.example.frienddebt.notification;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Calendar;
import java.util.Locale;

public class NightSummaryWorker extends Worker {

    private static final String TAG = "NightSummaryWorker";

    public NightSummaryWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Night summary worker triggered");

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            return Result.success();
        }

        String userId = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        try {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfToday = cal.getTimeInMillis();

            // Completed tasks today
            QuerySnapshot tasksSnap = Tasks.await(db.collection("users")
                    .document(userId)
                    .collection("tasks")
                    .whereEqualTo("isCompleted", true)
                    .get());
            int tasksCompleted = 0;
            for (DocumentSnapshot doc : tasksSnap.getDocuments()) {
                Long completedAt = doc.getLong("completedAt");
                if (completedAt != null && completedAt >= startOfToday) {
                    tasksCompleted++;
                }
            }

            // Notes created today
            QuerySnapshot notesSnap = Tasks.await(db.collection("users")
                    .document(userId)
                    .collection("notes")
                    .whereGreaterThanOrEqualTo("createdAt", startOfToday)
                    .get());
            int notesCreated = notesSnap.size();

            // Spend today
            QuerySnapshot cashSnap = Tasks.await(db.collection("users")
                    .document(userId)
                    .collection("cashbook")
                    .whereGreaterThanOrEqualTo("date", startOfToday)
                    .get());

            double spentToday = 0;
            for (DocumentSnapshot doc : cashSnap.getDocuments()) {
                String type = doc.getString("type");
                Double amountVal = doc.getDouble("amount");
                if ("CASH_OUT".equalsIgnoreCase(type) && amountVal != null) {
                    spentToday += amountVal;
                }
            }

            StringBuilder msg = new StringBuilder();
            msg.append(String.format(Locale.getDefault(), "✓ %d tasks completed\n", tasksCompleted));
            msg.append(String.format(Locale.getDefault(), "✓ %d notes created\n", notesCreated));
            msg.append(String.format(Locale.getDefault(), "✓ ₹%.2f spent today", spentToday));

            NotificationHelper.showNotification(
                    getApplicationContext(),
                    NotificationHelper.CHANNEL_SUMMARIES_ID,
                    1002,
                    "Today's Summary",
                    msg.toString(),
                    null,
                    null,
                    null,
                    null,
                    null
            );

        } catch (Exception e) {
            Log.e(TAG, "Error in night summary worker: " + e.getMessage());
        }

        return Result.success();
    }
}
