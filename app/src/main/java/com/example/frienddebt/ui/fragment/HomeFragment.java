package com.example.frienddebt.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.frienddebt.R;
import com.example.frienddebt.ui.AddTaskActivity;
import com.example.frienddebt.ui.AddReminderActivity;
import com.example.frienddebt.ui.DashboardActivity;
import com.example.frienddebt.ui.ReportsActivity;
import com.example.frienddebt.ui.TasksActivity;
import com.example.frienddebt.ui.RemindersActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private TextView txtGreeting, txtUserName, txtTotalBalance, txtHomeCashIn, txtHomeCashOut;
    private TextView txtTaskCount, txtTaskPreview;
    private TextView txtReminderCount, txtReminderPreview;
    private TextView txtGroupCount, txtGroupPreview;

    private LinearLayout btnQuickExpense, btnQuickTask, btnQuickReminder, btnQuickReports;
    private LinearLayout cardTasks, cardReminders, cardGroups;
    private ImageView imgProfile;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private ListenerRegistration cashbookListener;
    private ListenerRegistration tasksListener;
    private ListenerRegistration remindersListener;
    private ListenerRegistration groupsCreatedListener;
    private ListenerRegistration groupsJoinedListener;

    private int createdGroupsCount = 0;
    private int joinedGroupsCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Bind Views
        txtGreeting = view.findViewById(R.id.txtGreeting);
        txtUserName = view.findViewById(R.id.txtUserName);
        txtTotalBalance = view.findViewById(R.id.txtTotalBalance);
        txtHomeCashIn = view.findViewById(R.id.txtHomeCashIn);
        txtHomeCashOut = view.findViewById(R.id.txtHomeCashOut);

        txtTaskCount = view.findViewById(R.id.txtTaskCount);
        txtTaskPreview = view.findViewById(R.id.txtTaskPreview);

        txtReminderCount = view.findViewById(R.id.txtReminderCount);
        txtReminderPreview = view.findViewById(R.id.txtReminderPreview);

        txtGroupCount = view.findViewById(R.id.txtGroupCount);
        txtGroupPreview = view.findViewById(R.id.txtGroupPreview);

        btnQuickExpense = view.findViewById(R.id.btnQuickExpense);
        btnQuickTask = view.findViewById(R.id.btnQuickTask);
        btnQuickReminder = view.findViewById(R.id.btnQuickReminder);
        btnQuickReports = view.findViewById(R.id.btnQuickReports);

        cardTasks = view.findViewById(R.id.cardTasks);
        cardReminders = view.findViewById(R.id.cardReminders);
        cardGroups = view.findViewById(R.id.cardGroups);
        imgProfile = view.findViewById(R.id.imgProfile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupGreeting();
        setupClickListeners();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadData();
    }

    @Override
    public void onStop() {
        super.onStop();
        removeListeners();
    }

    private void setupGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) {
            greeting = "Good Morning!";
        } else if (hour < 16) {
            greeting = "Good Afternoon!";
        } else if (hour < 21) {
            greeting = "Good Evening!";
        } else {
            greeting = "Good Night!";
        }
        txtGreeting.setText(greeting);

        if (auth.getCurrentUser() != null) {
            String email = auth.getCurrentUser().getEmail();
            if (email != null) {
                int index = email.indexOf('@');
                String name = index != -1 ? email.substring(0, index) : email;
                if (name.length() > 0) {
                    name = name.substring(0, 1).toUpperCase() + name.substring(1);
                }
                txtUserName.setText(name);
            }
        }
    }

    private void setupClickListeners() {
        btnQuickExpense.setOnClickListener(v -> {
            playButtonPop(v);
            if (requireActivity() instanceof DashboardActivity) {
                ((DashboardActivity) requireActivity()).selectTab(R.id.nav_cashbook);
            }
        });

        btnQuickTask.setOnClickListener(v -> {
            playButtonPop(v);
            startActivity(new Intent(requireActivity(), AddTaskActivity.class));
        });

        btnQuickReminder.setOnClickListener(v -> {
            playButtonPop(v);
            startActivity(new Intent(requireActivity(), AddReminderActivity.class));
        });

        btnQuickReports.setOnClickListener(v -> {
            playButtonPop(v);
            startActivity(new Intent(requireActivity(), ReportsActivity.class));
        });

        cardTasks.setOnClickListener(v -> {
            playButtonPop(v);
            startActivity(new Intent(requireActivity(), TasksActivity.class));
        });

        cardReminders.setOnClickListener(v -> {
            playButtonPop(v);
            startActivity(new Intent(requireActivity(), RemindersActivity.class));
        });

        cardGroups.setOnClickListener(v -> {
            playButtonPop(v);
            if (requireActivity() instanceof DashboardActivity) {
                ((DashboardActivity) requireActivity()).selectTab(R.id.nav_groups);
            }
        });

        imgProfile.setOnClickListener(v -> {
            playButtonPop(v);
            if (requireActivity() instanceof DashboardActivity) {
                ((DashboardActivity) requireActivity()).selectTab(R.id.nav_profile);
            }
        });
    }

    private void playButtonPop(View v) {
        Animation pop = AnimationUtils.loadAnimation(requireContext(), R.anim.button_pop);
        v.startAnimation(pop);
    }

    public void loadData() {
        if (auth == null || db == null) return;
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        removeListeners();

        // 1. Cashbook calculations
        cashbookListener = db.collection("users")
                .document(userId)
                .collection("cashbook")
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;
                    double cashInSum = 0;
                    double cashOutSum = 0;
                    for (DocumentSnapshot doc : snapshots) {
                        Double amountObj = doc.getDouble("amount");
                        double amount = amountObj != null ? amountObj : 0.0;
                        String type = doc.getString("type");
                        if ("CASH_IN".equals(type)) {
                            cashInSum += amount;
                        } else if ("CASH_OUT".equals(type)) {
                            cashOutSum += amount;
                        }
                    }
                    double total = cashInSum - cashOutSum;
                    txtTotalBalance.setText(String.format(Locale.getDefault(), "₹%.2f", total));
                    txtHomeCashIn.setText(String.format(Locale.getDefault(), "₹%.2f", cashInSum));
                    txtHomeCashOut.setText(String.format(Locale.getDefault(), "₹%.2f", cashOutSum));
                });

        // 2. Tasks preview
        tasksListener = db.collection("users")
                .document(userId)
                .collection("tasks")
                .whereEqualTo("isCompleted", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;
                    int count = snapshots.size();
                    txtTaskCount.setText(count + " pending");

                    if (count > 0) {
                        java.util.List<DocumentSnapshot> docs = new java.util.ArrayList<>(snapshots.getDocuments());
                        docs.sort((d1, d2) -> {
                            Long c1 = d1.getLong("createdAt");
                            Long c2 = d2.getLong("createdAt");
                            long val1 = c1 != null ? c1 : 0L;
                            long val2 = c2 != null ? c2 : 0L;
                            return Long.compare(val2, val1); // Descending (newest first)
                        });
                        DocumentSnapshot firstTask = docs.get(0);
                        String title = firstTask.getString("title");
                        txtTaskPreview.setText("Next: " + title);
                    } else {
                        txtTaskPreview.setText("No tasks for today");
                    }
                });

        // 3. Reminders preview
        remindersListener = db.collection("users")
                .document(userId)
                .collection("reminders")
                .whereEqualTo("isCompleted", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;
                    int count = snapshots.size();
                    txtReminderCount.setText(String.valueOf(count));

                    if (count > 0) {
                        java.util.List<DocumentSnapshot> docs = new java.util.ArrayList<>(snapshots.getDocuments());
                        docs.sort((d1, d2) -> {
                            Long t1 = d1.getLong("triggerTime");
                            Long t2 = d2.getLong("triggerTime");
                            long val1 = t1 != null ? t1 : 0L;
                            long val2 = t2 != null ? t2 : 0L;
                            return Long.compare(val1, val2); // Ascending (nearest first)
                        });
                        DocumentSnapshot firstReminder = docs.get(0);
                        String title = firstReminder.getString("title");
                        Long triggerTime = firstReminder.getLong("triggerTime");
                        if (triggerTime != null) {
                            String timeStr = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(triggerTime));
                            txtReminderPreview.setText("Next: " + title + " at " + timeStr);
                        } else {
                            txtReminderPreview.setText("Next: " + title);
                        }
                    } else {
                        txtReminderPreview.setText("No upcoming reminders");
                    }
                });

        // 4. Groups count
        groupsCreatedListener = db.collection("users")
                .document(userId)
                .collection("groups")
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;
                    createdGroupsCount = snapshots.size();
                    updateGroupsSummary();
                });

        groupsJoinedListener = db.collection("users")
                .document(userId)
                .collection("joined_groups")
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;
                    joinedGroupsCount = snapshots.size();
                    updateGroupsSummary();
                });
    }

    private void updateGroupsSummary() {
        int total = createdGroupsCount + joinedGroupsCount;
        txtGroupCount.setText(total + (total == 1 ? " group" : " groups"));
        if (total > 0) {
            txtGroupPreview.setText("Keep splitting and settling debts");
        } else {
            txtGroupPreview.setText("Create or join a group to split expenses");
        }
    }

    private void removeListeners() {
        if (cashbookListener != null) {
            cashbookListener.remove();
            cashbookListener = null;
        }
        if (tasksListener != null) {
            tasksListener.remove();
            tasksListener = null;
        }
        if (remindersListener != null) {
            remindersListener.remove();
            remindersListener = null;
        }
        if (groupsCreatedListener != null) {
            groupsCreatedListener.remove();
            groupsCreatedListener = null;
        }
        if (groupsJoinedListener != null) {
            groupsJoinedListener.remove();
            groupsJoinedListener = null;
        }
    }
}
