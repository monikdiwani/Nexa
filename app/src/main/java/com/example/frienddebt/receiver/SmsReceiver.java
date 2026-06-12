package com.example.frienddebt.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.example.frienddebt.model.CashbookEntry;
import com.example.frienddebt.notification.NotificationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG        = "SmsReceiver";
    private static final String PREFS_NAME = "NexaSmsPrefs";
    public  static final String KEY_SMS_ENABLED = "sms_auto_add_enabled";

    // Regex to find amounts like Rs 100, INR 50.50, Rs. 500, etc.
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(?i)(?:Rs\\.?|INR)\\s*([0-9,]+\\.?[0-9]*)");

    /** Check whether SMS auto-add is enabled (default: false — user must opt-in). */
    public static boolean isSmsAutoAddEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SMS_ENABLED, false);
    }

    public static void setSmsAutoAddEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SMS_ENABLED, enabled).apply();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        // 🔒 Respect toggle — skip if disabled
        if (!isSmsAutoAddEnabled(context)) {
            Log.d(TAG, "SMS auto-add is disabled by user.");
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;

        String userId = auth.getCurrentUser().getUid();

        for (Object pdu : pdus) {
            SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
            String messageBody = smsMessage.getMessageBody();
            String sender      = smsMessage.getOriginatingAddress();

            if (messageBody == null) continue;

            Log.d(TAG, "Received SMS from " + sender + ": " + messageBody);

            String type = detectTransactionType(messageBody.toLowerCase());
            if (type == null) continue;

            double amount = extractAmount(messageBody);
            if (amount <= 0) continue;

            saveTransaction(context, userId, sender, messageBody, type, amount);
        }
    }

    private String detectTransactionType(String msg) {
        if (msg.contains("debited") || msg.contains("spent") || msg.contains("paid")) {
            return "CASH_OUT";
        } else if (msg.contains("credited") || msg.contains("received")) {
            return "CASH_IN";
        }
        return null;
    }

    private double extractAmount(String msg) {
        Matcher matcher = AMOUNT_PATTERN.matcher(msg);
        if (matcher.find()) {
            String amountStr = matcher.group(1).replace(",", "");
            try {
                return Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private void saveTransaction(Context context, String userId, String sender,
                                 String message, String type, double amount) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("cashbooks")
          .whereEqualTo("ownerId", userId)
          .limit(1)
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              if (!queryDocumentSnapshots.isEmpty()) {
                  QueryDocumentSnapshot doc =
                          (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                  String bookId = doc.getId();

                  String entryId = db.collection("cashbooks")
                          .document(bookId).collection("entries").document().getId();
                  long timestamp = System.currentTimeMillis();

                  CashbookEntry entry = new CashbookEntry(
                          entryId, bookId, timestamp,
                          "Auto-added from SMS: " + sender,
                          type, "BANK", amount, "Other", message, timestamp);

                  db.collection("cashbooks").document(bookId)
                    .collection("entries").document(entryId)
                    .set(entry.toFirestoreMap())
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "SMS transaction saved: ₹" + amount);
                        // 🔔 Confirmation notification
                        String title = "Transaction Auto-Added";
                        String body  = String.format("₹%.2f %s detected and added to your ledger.",
                                amount, "CASH_OUT".equals(type) ? "spent" : "received");
                        NotificationHelper.showNotification(
                                context,
                                NotificationHelper.CHANNEL_SUMMARIES_ID,
                                ("sms_" + entryId).hashCode(),
                                title, body, null, null, null, null, null);
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to save SMS transaction", e));
              } else {
                  Log.d(TAG, "No cashbook found for SMS auto-add.");
              }
          })
          .addOnFailureListener(e -> Log.e(TAG, "Failed to fetch cashbook for SMS", e));
    }
}
