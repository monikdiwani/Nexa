package com.example.frienddebt.utils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.frienddebt.model.CashbookEntry;
import com.example.frienddebt.model.Note;
import com.example.frienddebt.model.Reminder;
import com.example.frienddebt.model.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DataExportHelper {

    public static void exportData(Activity context) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(context, "You must be logged in to export data.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setMessage("Generating Export...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        List<String[]> csvRows = new ArrayList<>();
        // Add Header
        csvRows.add(new String[]{"TYPE", "TITLE", "DETAILS", "AMOUNT", "DATE", "STATUS"});

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        // 1. Fetch Tasks
        db.collection("users")
                .document(userId)
                .collection("tasks")
                .get()
                .addOnSuccessListener(taskSnapshots -> {
                    for (DocumentSnapshot doc : taskSnapshots) {
                    Task task = Task.fromDocument(doc);
                    String status = task.isCompleted() ? "Completed" : "Pending";
                    csvRows.add(new String[]{
                        "Task",
                        sanitize(task.getTitle()),
                        sanitize(task.getDescription() != null ? task.getDescription() : ""),
                        "-",
                        formatDate(task.getDueDate(), sdf),
                        status
                    });
                    }

                    // 2. Fetch Reminders
                    db.collection("users")
                            .document(userId)
                            .collection("reminders")
                            .get()
                            .addOnSuccessListener(reminderSnapshots -> {
                                    for (DocumentSnapshot doc : reminderSnapshots) {
                                    Reminder r = Reminder.fromDocument(doc);
                                    String status = r.isCompleted() ? "Completed" : "Active";
                                    csvRows.add(new String[]{
                                        "Reminder",
                                        sanitize(r.getTitle()),
                                        sanitize(r.getMessage() != null ? r.getMessage() : ""),
                                        "-",
                                        formatDate(r.getTriggerTime(), sdf),
                                        status
                                    });
                                }

                                // 3. Fetch Notes
                                db.collection("users")
                                        .document(userId)
                                        .collection("notes")
                                        .get()
                                        .addOnSuccessListener(noteSnapshots -> {
                                            for (DocumentSnapshot doc : noteSnapshots) {
                                            Note n = Note.fromDocument(doc);
                                            csvRows.add(new String[]{
                                                "Note",
                                                sanitize(n.getTitle()),
                                                sanitize(n.getContent() != null ? n.getContent() : ""),
                                                "-",
                                                formatDate(n.getCreatedAt(), sdf),
                                                "-"
                                            });
                                            }

                                            // 4. Fetch Cashbook Entries
                                            db.collection("cashbooks")
                                                    .whereNotEqualTo("members." + userId, null)
                                                    .get()
                                                    .addOnSuccessListener(booksSnap -> {
                                                        java.util.List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>> bookTasks = new java.util.ArrayList<>();
                                                        for (DocumentSnapshot bookDoc : booksSnap.getDocuments()) {
                                                            bookTasks.add(db.collection("cashbooks").document(bookDoc.getId()).collection("entries").get());
                                                        }

                                                        if (bookTasks.isEmpty()) {
                                                            progressDialog.dismiss();
                                                            generateAndShareCSV(context, csvRows);
                                                            return;
                                                        }

                                                        com.google.android.gms.tasks.Tasks.whenAllSuccess(bookTasks)
                                                                .addOnSuccessListener(results -> {
                                                                    for (Object res : results) {
                                                                        com.google.firebase.firestore.QuerySnapshot entrySnap = (com.google.firebase.firestore.QuerySnapshot) res;
                                                                        for (DocumentSnapshot doc : entrySnap.getDocuments()) {
                                                                            CashbookEntry entry = CashbookEntry.fromDocument(doc);
                                                                            if (userId.equals(entry.getCreatedBy())) {
                                                                                String amt = String.format(Locale.getDefault(), "%.2f", entry.getAmount());
                                                                                if ("EXPENSE".equals(entry.getType()) || "CASH_OUT".equals(entry.getType())) {
                                                                                    amt = "-" + amt;
                                                                                } else {
                                                                                    amt = "+" + amt;
                                                                                }
                                                                                csvRows.add(new String[]{
                                                                                        "Cashbook",
                                                                                        sanitize(entry.getParticulars()),
                                                                                        sanitize(entry.getCategory()),
                                                                                        amt,
                                                                                        formatDate(entry.getDate(), sdf),
                                                                                        "-"
                                                                                    });
                                                                            }
                                                                        }
                                                                    }
                                                                    progressDialog.dismiss();
                                                                    generateAndShareCSV(context, csvRows);
                                                                })
                                                                .addOnFailureListener(e -> finishWithError(context, progressDialog, e));
                                                    })
                                                    .addOnFailureListener(e -> finishWithError(context, progressDialog, e));
                                        })
                                        .addOnFailureListener(e -> finishWithError(context, progressDialog, e));
                            })
                            .addOnFailureListener(e -> finishWithError(context, progressDialog, e));
                })
                .addOnFailureListener(e -> finishWithError(context, progressDialog, e));
    }

    private static void finishWithError(Context context, ProgressDialog dialog, Exception e) {
        dialog.dismiss();
        Toast.makeText(context, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    private static void generateAndShareCSV(Activity context, List<String[]> data) {
        String filename = "Nexa_Export_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".csv";
        File exportDir = new File(context.getCacheDir(), "exports");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        File file = new File(exportDir, filename);

        try {
            FileWriter writer = new FileWriter(file);
            for (String[] row : data) {
                writer.append(String.join(",", row));
                writer.append("\n");
            }
            writer.flush();
            writer.close();

            shareFile(context, file);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error writing file.", Toast.LENGTH_SHORT).show();
        }
    }

    private static void shareFile(Activity context, File file) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Nexa Data Export");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "Save or Share Export"));
    }

    private static String sanitize(String input) {
        if (input == null) return "";
        // Escape quotes and wrap in quotes if there are commas or newlines
        String escaped = input.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static String formatDate(Long ms, SimpleDateFormat sdf) {
        try {
            if (ms == null) return "";
            return sdf.format(new Date(ms));
        } catch (Exception e) {
            return "";
        }
    }
}
