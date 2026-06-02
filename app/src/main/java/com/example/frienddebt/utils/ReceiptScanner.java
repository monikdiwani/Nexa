package com.example.frienddebt.utils;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptScanner {

    public interface ScanCallback {
        void onSuccess(String vendorName, double totalAmount);
        void onFailure(Exception e);
    }

    public static void scanReceipt(Context context, Uri imageUri, ScanCallback callback) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String fullText = visionText.getText();
                        double total = extractTotal(fullText);
                        String vendor = extractVendor(fullText);
                        callback.onSuccess(vendor, total);
                    })
                    .addOnFailureListener(callback::onFailure);

        } catch (IOException e) {
            callback.onFailure(e);
        }
    }

    private static double extractTotal(String text) {
        double maxAmount = 0.0;
        // Looking for numbers that look like currency (e.g., 12.34, 1,234.56, 1234.56)
        Pattern pattern = Pattern.compile("[$₹]?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)");
        Matcher matcher = pattern.matcher(text);

        boolean foundTotalKeyword = text.toLowerCase().contains("total");

        while (matcher.find()) {
            String match = matcher.group(1);
            if (match != null) {
                try {
                    match = match.replace(",", "");
                    double amount = Double.parseDouble(match);
                    
                    // Basic heuristic: The largest amount is usually the total,
                    // especially if "total" keyword is somewhere in the text
                    if (amount > maxAmount) {
                        maxAmount = amount;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return maxAmount;
    }

    private static String extractVendor(String text) {
        // Basic heuristic: The vendor name is often the first line of the receipt
        if (text != null && !text.isEmpty()) {
            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                // Ignore empty lines or typical header garbage
                if (!trimmed.isEmpty() && trimmed.length() > 2) {
                    // Let's assume the first substantive line is the vendor
                    return trimmed;
                }
            }
        }
        return "Receipt Scan";
    }
}
