package com.example.frienddebt.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.example.frienddebt.model.CashbookEntry;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    
    // Regex to find amounts like Rs 100, INR 50.50, Rs. 500, etc.
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(?:Rs\\.?|INR)\\s*([0-9,]+\\.?[0-9]*)");

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return; // User not logged in

        String userId = auth.getCurrentUser().getUid();

        for (Object pdu : pdus) {
            SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
            String messageBody = smsMessage.getMessageBody();
            String sender = smsMessage.getOriginatingAddress();
            
            if (messageBody == null) continue;

            Log.d(TAG, "Received SMS from " + sender + ": " + messageBody);

            String type = detectTransactionType(messageBody.toLowerCase());
            if (type == null) continue; // Not a bank transaction

            double amount = extractAmount(messageBody);
            if (amount <= 0) continue; // No amount found

            saveTransaction(userId, sender, messageBody, type, amount);
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

    private void saveTransaction(String userId, String sender, String message, String type, double amount) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Find the user's default/first cashbook to add to
        db.collection("cashbooks")
          .whereEqualTo("ownerId", userId)
          .limit(1)
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              if (!queryDocumentSnapshots.isEmpty()) {
                  QueryDocumentSnapshot doc = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                  String bookId = doc.getId();
                  
                  String entryId = db.collection("cashbooks").document(bookId).collection("entries").document().getId();
                  long timestamp = System.currentTimeMillis();
                  
                  CashbookEntry entry = new CashbookEntry(
                          entryId,
                          bookId,
                          timestamp,
                          "Auto-added from SMS: " + sender,
                          type,
                          "BANK", // medium
                          amount,
                          "Other", // category
                          message, // note
                          timestamp // createdAt
                  );
                  
                  db.collection("cashbooks").document(bookId).collection("entries").document(entryId)
                    .set(entry.toFirestoreMap())
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "SMS transaction saved successfully!"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to save SMS transaction", e));
              } else {
                  Log.d(TAG, "No cashbook found for user to add SMS transaction.");
              }
          })
          .addOnFailureListener(e -> Log.e(TAG, "Failed to fetch cashbook for SMS", e));
    }
}
