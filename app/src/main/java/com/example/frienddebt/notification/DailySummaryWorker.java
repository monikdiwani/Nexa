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

public class DailySummaryWorker extends Worker {

    private static final String TAG = "DailySummaryWorker";

    public DailySummaryWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Daily summary worker triggered");

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            return Result.success();
        }

        String userId = auth.getCurrentUser().getUid();
        String email = auth.getCurrentUser().getEmail();
        String name = "User";
        if (email != null) {
            int index = email.indexOf('@');
            name = index != -1 ? email.substring(0, index) : email;
            if (name.length() > 0) {
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
            }
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        try {
            // Fetch pending tasks count
            QuerySnapshot tasksSnap = Tasks.await(db.collection("users")
                    .document(userId)
                    .collection("tasks")
                    .whereEqualTo("isCompleted", false)
                    .get());
            int pendingTasksCount = tasksSnap.size();

            // Fetch cashbook weekly spend
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfWeek = cal.getTimeInMillis();

            QuerySnapshot cashSnap = Tasks.await(db.collection("users")
                    .document(userId)
                    .collection("cashbook")
                    .whereGreaterThanOrEqualTo("date", startOfWeek)
                    .get());

            double weeklySpent = 0;
            for (DocumentSnapshot doc : cashSnap.getDocuments()) {
                String type = doc.getString("type");
                Double amountVal = doc.getDouble("amount");
                if ("CASH_OUT".equalsIgnoreCase(type) && amountVal != null) {
                    weeklySpent += amountVal;
                }
            }

            StringBuilder msg = new StringBuilder();
            msg.append(String.format(Locale.getDefault(), "• %d tasks pending today\n", pendingTasksCount));
            msg.append(String.format(Locale.getDefault(), "• ₹%.2f spent this week", weeklySpent));

            NotificationHelper.showNotification(
                    getApplicationContext(),
                    NotificationHelper.CHANNEL_SUMMARIES_ID,
                    1001,
                    "Good Morning, " + name + "!",
                    msg.toString(),
                    null,
                    null,
                    null,
                    null,
                    null
            );

        } catch (Exception e) {
            Log.e(TAG, "Error in daily summary worker: " + e.getMessage());
        }

        return Result.success();
    }
}
