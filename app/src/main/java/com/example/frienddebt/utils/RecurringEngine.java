package com.example.frienddebt.utils;

import android.content.Context;
import android.util.Log;

import com.example.frienddebt.model.CashbookEntry;
import com.example.frienddebt.model.Reminder;
import com.example.frienddebt.model.Task;
import com.example.frienddebt.notification.ReminderScheduler;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.Calendar;

public class RecurringEngine {

    private static final String TAG = "RecurringEngine";

    public static void processRecurringItems(Context context, String userId) {
        if (userId == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        long currentTime = System.currentTimeMillis();

        // 1. Process Tasks
        db.collection("users").document(userId).collection("tasks")
                .whereEqualTo("isRecurring", true)
                .whereLessThanOrEqualTo("nextOccurrence", currentTime)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!snapshots.isEmpty()) {
                        WriteBatch batch = db.batch();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Task task = Task.fromDocument(doc);
                            
                            // A. Create new instance
                            Task newTask = cloneTaskForNextOccurrence(task);
                            if (newTask != null) {
                                String newId = db.collection("users").document(userId).collection("tasks").document().getId();
                                batch.set(db.collection("users").document(userId).collection("tasks").document(newId), newTask.toFirestoreMap());
                                
                                // B. Update original task to push its nextOccurrence forward
                                long updatedNext = calculateNextOccurrence(task.getNextOccurrence(), task.getRecurringPattern());
                                batch.update(doc.getReference(), "nextOccurrence", updatedNext);
                            }
                        }
                        batch.commit().addOnSuccessListener(aVoid -> Log.d(TAG, "Recurring tasks processed"));
                    }
                });

        // 2. Process Reminders
        db.collection("users").document(userId).collection("reminders")
                .whereEqualTo("isRecurring", true)
                .whereLessThanOrEqualTo("nextOccurrence", currentTime)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!snapshots.isEmpty()) {
                        WriteBatch batch = db.batch();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Reminder reminder = Reminder.fromDocument(doc);
                            
                            // A. Create new instance
                            Reminder newReminder = cloneReminderForNextOccurrence(reminder);
                            if (newReminder != null) {
                                String newId = db.collection("users").document(userId).collection("reminders").document().getId();
                                newReminder.setId(newId);
                                batch.set(db.collection("users").document(userId).collection("reminders").document(newId), newReminder.toFirestoreMap());
                                
                                // Schedule the alarm locally
                                ReminderScheduler.scheduleReminder(context, newReminder);
                                
                                // B. Update original reminder to push its nextOccurrence forward
                                long updatedNext = calculateNextOccurrence(reminder.getNextOccurrence(), reminder.getRecurringPattern());
                                batch.update(doc.getReference(), "nextOccurrence", updatedNext);
                            }
                        }
                        batch.commit().addOnSuccessListener(aVoid -> Log.d(TAG, "Recurring reminders processed"));
                    }
                });

        // 3. Process Cashbook Entries
        db.collection("cashbooks")
                .whereNotEqualTo("members." + userId, null)
                .get()
                .addOnSuccessListener(booksSnap -> {
                    java.util.List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>> tasks = new java.util.ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot bookDoc : booksSnap.getDocuments()) {
                        tasks.add(db.collection("cashbooks").document(bookDoc.getId()).collection("entries")
                                .whereLessThanOrEqualTo("nextOccurrence", currentTime)
                                .get());
                    }
                    if (tasks.isEmpty()) return;

                    com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
                        WriteBatch batch = db.batch();
                        boolean hasUpdates = false;
                        for (Object res : results) {
                            com.google.firebase.firestore.QuerySnapshot entrySnap = (com.google.firebase.firestore.QuerySnapshot) res;
                            for (QueryDocumentSnapshot doc : entrySnap) {
                                CashbookEntry entry = CashbookEntry.fromDocument(doc);
                                if (entry.isRecurring() && userId.equals(entry.getCreatedBy())) {
                                    CashbookEntry newEntry = cloneCashbookEntryForNextOccurrence(entry);
                                    if (newEntry != null) {
                                        hasUpdates = true;
                                        String newId = doc.getReference().getParent().document().getId();
                                        batch.set(doc.getReference().getParent().document(newId), newEntry.toFirestoreMap());

                                        long updatedNext = calculateNextOccurrence(entry.getNextOccurrence(), entry.getRecurringPattern());
                                        batch.update(doc.getReference(), "nextOccurrence", updatedNext);
                                    }
                                }
                            }
                        }
                        if (hasUpdates) {
                            batch.commit().addOnSuccessListener(aVoid -> Log.d(TAG, "Recurring cashbook entries processed"));
                        }
                    });
                });
    }

    private static long calculateNextOccurrence(long baseMs, String pattern) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(baseMs);
        if (pattern == null) pattern = "NONE";
        switch (pattern.toUpperCase()) {
            case "DAILY": cal.add(Calendar.DAY_OF_YEAR, 1); break;
            case "WEEKLY": cal.add(Calendar.WEEK_OF_YEAR, 1); break;
            case "MONTHLY": cal.add(Calendar.MONTH, 1); break;
            case "YEARLY": cal.add(Calendar.YEAR, 1); break;
            default: return baseMs + 86400000L; // default 1 day
        }
        return cal.getTimeInMillis();
    }

    private static Task cloneTaskForNextOccurrence(Task old) {
        Task t = new Task();
        t.setTitle(old.getTitle());
        t.setDescription(old.getDescription());
        t.setPriority(old.getPriority());
        t.setImportant(old.isImportant());
        t.setCompleted(false);
        t.setArchived(false);
        t.setCreatedAt(System.currentTimeMillis());
        
        t.setRecurring(false);
        t.setRecurringPattern(null);
        t.setRecurringId(null);
        
        t.setNextOccurrence(0L); // The clone is NOT recurring
        
        if (old.getDueDate() != null) {
            long newDueDate = calculateNextOccurrence(old.getDueDate(), old.getRecurringPattern());
            t.setDueDate(newDueDate);
            if (old.getDueTime() != null) {
                t.setDueTime(calculateNextOccurrence(old.getDueTime(), old.getRecurringPattern()));
            }
        }
        
        if (old.getSubtasks() != null) {
            java.util.List<Task.Subtask> clonedSubs = new java.util.ArrayList<>();
            for (Task.Subtask st : old.getSubtasks()) {
                clonedSubs.add(new Task.Subtask(st.getTitle(), false));
            }
            t.setSubtasks(clonedSubs);
        }
        return t;
    }

    private static Reminder cloneReminderForNextOccurrence(Reminder old) {
        Reminder r = new Reminder();
        r.setTitle(old.getTitle());
        r.setMessage(old.getMessage());
        r.setPriority(old.getPriority());
        r.setCategory(old.getCategory());
        r.setCompleted(false);
        r.setSnoozed(false);
        r.setCreatedAt(System.currentTimeMillis());
        r.setLinkedItemId(old.getLinkedItemId());
        r.setLinkedItemType(old.getLinkedItemType());
        
        r.setRecurring(false);
        r.setRecurringPattern(null);
        r.setRecurringId(null);
        
        long newTrigger = calculateNextOccurrence(old.getTriggerTime(), old.getRecurringPattern());
        r.setTriggerTime(newTrigger);
        
        r.setNextOccurrence(0L);
        
        return r;
    }

    private static CashbookEntry cloneCashbookEntryForNextOccurrence(CashbookEntry old) {
        CashbookEntry c = new CashbookEntry();
        c.setBookId(old.getBookId());
        c.setParticulars(old.getParticulars());
        c.setType(old.getType());
        c.setMedium(old.getMedium());
        c.setAmount(old.getAmount());
        c.setCategory(old.getCategory());
        c.setNote(old.getNote());
        c.setContactName(old.getContactName());
        c.setContactPhone(old.getContactPhone());
        c.setPaidBy(old.getPaidBy());
        c.setSplitMethod(old.getSplitMethod());
        c.setParticipants(old.getParticipants());
        c.setSplits(old.getSplits());
        
        c.setCreatedAt(System.currentTimeMillis());
        c.setLastModifiedAt(System.currentTimeMillis());
        c.setCreatedBy(old.getCreatedBy());
        
        c.setRecurring(false);
        c.setRecurringPattern(null);
        c.setRecurringId(null);
        
        long newDate = calculateNextOccurrence(old.getDate(), old.getRecurringPattern());
        c.setDate(newDate);
        
        c.setNextOccurrence(0L);
        
        return c;
    }
}
