package com.example.frienddebt.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public class WhatsAppReminderUtil {

    public static void sendUdhaarReminder(Context context, String contactPhone, String contactName, double amount, String bookName) {
        if (contactPhone == null || contactPhone.isEmpty()) {
            Toast.makeText(context, "No phone number available for this contact", Toast.LENGTH_SHORT).show();
            return;
        }

        String message = "Namaste " + (contactName != null ? contactName : "Customer") + ",\n\n" +
                "This is a gentle reminder that your outstanding balance in our CashBook (" + bookName + ") is ₹" + amount + ".\n" +
                "Kindly make the payment at your earliest convenience.\n\n" +
                "Thank you!";

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            // Ensure phone number has country code. Assuming India (+91) for default if not present, though better to let user manage it.
            String formattedPhone = contactPhone.replaceAll("[^0-9]", "");
            if (!formattedPhone.startsWith("91") && formattedPhone.length() == 10) {
                formattedPhone = "91" + formattedPhone;
            }
            
            String url = "https://api.whatsapp.com/send?phone=" + formattedPhone + "&text=" + Uri.encode(message);
            intent.setData(Uri.parse(url));
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "WhatsApp not installed or error opening link", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
}
