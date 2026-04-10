package com.example.frienddebt.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Reminder;
import com.example.frienddebt.notification.ReminderScheduler;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RemindersActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextView chipPending, chipCompleted, chipMissed, txtEmptyReminders;
    private RecyclerView rvReminders;
    private FloatingActionButton fabAddReminder;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration remindersListener;

    private List<Reminder> allReminders = new ArrayList<>();
    private List<Reminder> filteredReminders = new ArrayList<>();
    private RemindersAdapter adapter;

    private String activeFilter = "PENDING"; // PENDING, COMPLETED, MISSED

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminders);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        btnBack = findViewById(R.id.btnBack);
        chipPending = findViewById(R.id.chipPending);
        chipCompleted = findViewById(R.id.chipCompleted);
        chipMissed = findViewById(R.id.chipMissed);
        txtEmptyReminders = findViewById(R.id.txtEmptyReminders);
        rvReminders = findViewById(R.id.rvReminders);
        fabAddReminder = findViewById(R.id.fabAddReminder);

        btnBack.setOnClickListener(v -> finish());

        rvReminders.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RemindersAdapter(filteredReminders);
        rvReminders.setAdapter(adapter);

        setupFilters();

        fabAddReminder.setOnClickListener(v -> {
            v.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.button_pop));
            startActivity(new Intent(RemindersActivity.this, AddReminderActivity.class));
        });

        loadReminders();

        // Request Notification Permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(
                        this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        1002
                );
            }
        }
    }

    private void setupFilters() {
        chipPending.setOnClickListener(v -> setFilter("PENDING", chipPending));
        chipCompleted.setOnClickListener(v -> setFilter("COMPLETED", chipCompleted));
        chipMissed.setOnClickListener(v -> setFilter("MISSED", chipMissed));
    }

    private void setFilter(String filter, TextView activeChip) {
        activeFilter = filter;
        resetChipStyles();
        activeChip.setBackgroundResource(R.drawable.rounded_button);
        activeChip.setTextColor(getResources().getColor(R.color.on_primary));
        applyFilter();
    }

    private void resetChipStyles() {
        TextView[] chips = {chipPending, chipCompleted, chipMissed};
        for (TextView chip : chips) {
            chip.setBackgroundResource(R.drawable.chip_background);
            chip.setTextColor(getResources().getColor(R.color.text_secondary));
        }
    }

    private void loadReminders() {
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        remindersListener = db.collection("users")
                .document(userId)
                .collection("reminders")
                .orderBy("triggerTime", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;
                    allReminders.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        allReminders.add(Reminder.fromDocument(doc));
                    }
                    applyFilter();
                });
    }

    private void applyFilter() {
        filteredReminders.clear();
        long now = System.currentTimeMillis();

        for (Reminder r : allReminders) {
            boolean matches = false;
            switch (activeFilter) {
                case "PENDING":
                    matches = !r.isCompleted() && (r.isSnoozed() || r.getTriggerTime() >= now);
                    break;
                case "COMPLETED":
                    matches = r.isCompleted();
                    break;
                case "MISSED":
                    matches = !r.isCompleted() && !r.isSnoozed() && r.getTriggerTime() < now;
                    break;
            }

            if (matches) {
                filteredReminders.add(r);
            }
        }

        adapter.notifyDataSetChanged();

        if (filteredReminders.isEmpty()) {
            txtEmptyReminders.setVisibility(View.VISIBLE);
            rvReminders.setVisibility(View.GONE);
        } else {
            txtEmptyReminders.setVisibility(View.GONE);
            rvReminders.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (remindersListener != null) {
            remindersListener.remove();
        }
    }

    // Adapter class
    private class RemindersAdapter extends RecyclerView.Adapter<RemindersAdapter.ViewHolder> {
        private final List<Reminder> list;

        public RemindersAdapter(List<Reminder> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reminder, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Reminder r = list.get(position);

            holder.txtTitle.setText(r.getTitle());
            String message = r.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = "No Description";
            }
            holder.txtMsg.setText(message);

            // Format date and time
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
            holder.txtTime.setText(sdf.format(new Date(r.getTriggerTime())));

            // Priority
            String priority = r.getPriority();
            if (priority == null || priority.trim().isEmpty()) {
                priority = "MEDIUM";
            }
            holder.txtPriority.setText(priority);
            int priorityColorBg = R.color.priority_medium;
            if ("HIGH".equalsIgnoreCase(priority)) {
                priorityColorBg = R.color.priority_high;
            } else if ("LOW".equalsIgnoreCase(priority)) {
                priorityColorBg = R.color.priority_low;
            }
            holder.txtPriority.setBackgroundColor(getResources().getColor(priorityColorBg));

            // Emoji Category Icon
            String category = r.getCategory();
            if (category == null || category.trim().isEmpty()) {
                category = "CUSTOM";
            }
            String emoji = "⏰";
            switch (category) {
                case "BILL": emoji = "📄"; break;
                case "MEETING": emoji = "👥"; break;
                case "TASK": emoji = "✅"; break;
                case "MEDICINE": emoji = "💊"; break;
                case "SHOPPING": emoji = "🛒"; break;
            }
            holder.txtIcon.setText(emoji);

            // Repeat pattern badge
            if (r.getRecurringPattern() != null && !"NONE".equalsIgnoreCase(r.getRecurringPattern())) {
                holder.txtRepeat.setVisibility(View.VISIBLE);
                holder.txtRepeat.setText(" • 🔁 " + r.getRecurringPattern());
            } else {
                holder.txtRepeat.setVisibility(View.GONE);
            }

            // Normal click option: Complete it
            holder.itemView.setOnClickListener(v -> {
                if (!r.isCompleted()) {
                    showCompleteDialog(r);
                }
            });

            // Long click option: Delete it
            holder.itemView.setOnLongClickListener(v -> {
                showDeleteDialog(r);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        private void showCompleteDialog(Reminder r) {
            new AlertDialog.Builder(RemindersActivity.this)
                    .setTitle("Complete Reminder")
                    .setMessage("Do you want to mark this reminder as completed?")
                    .setPositiveButton("Complete", (dialog, which) -> {
                        if (auth.getCurrentUser() != null) {
                            db.collection("users")
                                    .document(auth.getCurrentUser().getUid())
                                    .collection("reminders")
                                    .document(r.getId())
                                    .update(
                                            "isCompleted", true,
                                            "completedAt", System.currentTimeMillis()
                                    )
                                    .addOnSuccessListener(aVoid -> {
                                        ReminderScheduler.cancelReminder(RemindersActivity.this, r.getId());
                                        Toast.makeText(RemindersActivity.this, "Reminder completed!", Toast.LENGTH_SHORT).show();
                                    });
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void showDeleteDialog(Reminder r) {
            new AlertDialog.Builder(RemindersActivity.this)
                    .setTitle("Delete Reminder")
                    .setMessage("Are you sure you want to delete this reminder?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (auth.getCurrentUser() != null) {
                            db.collection("users")
                                    .document(auth.getCurrentUser().getUid())
                                    .collection("reminders")
                                    .document(r.getId())
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {
                                        ReminderScheduler.cancelReminder(RemindersActivity.this, r.getId());
                                        Toast.makeText(RemindersActivity.this, "Reminder deleted", Toast.LENGTH_SHORT).show();
                                    });
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtIcon, txtTitle, txtMsg, txtTime, txtRepeat, txtPriority;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtIcon = itemView.findViewById(R.id.txtReminderIcon);
                txtTitle = itemView.findViewById(R.id.txtReminderTitle);
                txtMsg = itemView.findViewById(R.id.txtReminderMessage);
                txtTime = itemView.findViewById(R.id.txtReminderTime);
                txtRepeat = itemView.findViewById(R.id.txtReminderRepeat);
                txtPriority = itemView.findViewById(R.id.txtReminderPriority);
            }
        }
    }
}
