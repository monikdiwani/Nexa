package com.example.frienddebt.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.frienddebt.utils.StatusBarUtil;

public class AddTaskActivity extends AppCompatActivity {

    private EditText etTaskTitle, etTaskDesc;
    private RadioGroup rgPriority;
    private CheckBox cbHasDueDate, cbImportant;
    private LinearLayout layoutDueDate;
    private TextView txtSelectedDate, txtSelectedTime;
    private Button btnSelectDate, btnSelectTime, btnSaveTask, btnAddSubtask;
    private LinearLayout layoutSubtasks;
    private ImageButton btnBack;
    private android.widget.Spinner spinnerRepeat;
    private boolean hasSelectedTime = false;

    private String taskId;
    private Calendar calendar = Calendar.getInstance();
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle Bundle) {
        super.onCreate(Bundle);
        setContentView(R.layout.activity_add_task);
        StatusBarUtil.applyStatusBarPadding(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind Views
        etTaskTitle = findViewById(R.id.etTaskTitle);
        etTaskDesc = findViewById(R.id.etTaskDesc);
        rgPriority = findViewById(R.id.rgPriority);
        cbHasDueDate = findViewById(R.id.cbHasDueDate);
        cbImportant = findViewById(R.id.cbImportant);
        layoutDueDate = findViewById(R.id.layoutDueDate);
        txtSelectedDate = findViewById(R.id.txtSelectedDate);
        txtSelectedTime = findViewById(R.id.txtSelectedTime);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        btnSelectTime = findViewById(R.id.btnSelectTime);
        btnSaveTask = findViewById(R.id.btnSaveTask);
        btnBack = findViewById(R.id.btnBack);
        btnAddSubtask = findViewById(R.id.btnAddSubtask);
        layoutSubtasks = findViewById(R.id.layoutSubtasks);
        spinnerRepeat = findViewById(R.id.spinnerRepeat);

        // Setup Spinner
        String[] repeatOptions = {"None", "Daily", "Weekly", "Monthly"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, repeatOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRepeat.setAdapter(adapter);

        taskId = getIntent().getStringExtra("TASK_ID");

        if (taskId != null) {
            btnSaveTask.setText("Update Task");
            loadTaskDetails();
        }

        btnBack.setOnClickListener(v -> finish());

        // Toggle Due Date Selection Layout
        cbHasDueDate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutDueDate.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Setup Date & Time Pickers
        updateDateText();
        updateTimeText();
        btnSelectDate.setOnClickListener(v -> showDatePicker());
        btnSelectTime.setOnClickListener(v -> showTimePicker());

        // Save Button Click
        btnSaveTask.setOnClickListener(v -> saveTask());
        btnAddSubtask.setOnClickListener(v -> addSubtaskView("", false));
    }

    private void loadTaskDetails() {
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(userId)
                .collection("tasks")
                .document(taskId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Task task = Task.fromDocument(documentSnapshot);
                        etTaskTitle.setText(task.getTitle());
                        etTaskDesc.setText(task.getDescription());

                        if (task.getDueDate() != null) {
                            cbHasDueDate.setChecked(true);
                            calendar.setTimeInMillis(task.getDueDate());
                            updateDateText();
                            if (task.getDueTime() != null && task.getDueTime() > 0) {
                                hasSelectedTime = true;
                                updateTimeText();
                            }
                        }
                        
                        cbImportant.setChecked(task.isImportant());
                        
                        String repeat = task.getRecurringPattern();
                        if ("Daily".equalsIgnoreCase(repeat)) spinnerRepeat.setSelection(1);
                        else if ("Weekly".equalsIgnoreCase(repeat)) spinnerRepeat.setSelection(2);
                        else if ("Monthly".equalsIgnoreCase(repeat)) spinnerRepeat.setSelection(3);
                        else spinnerRepeat.setSelection(0);

                        if ("LOW".equals(task.getPriority())) {
                            rgPriority.check(R.id.rbLow);
                        } else if ("MEDIUM".equals(task.getPriority())) {
                            rgPriority.check(R.id.rbMedium);
                        } else {
                            rgPriority.check(R.id.rbHigh);
                        }
                        
                        layoutSubtasks.removeAllViews();
                        if (task.getSubtasks() != null) {
                            for (Task.Subtask subtask : task.getSubtasks()) {
                                addSubtaskView(subtask.getTitle(), subtask.isCompleted());
                            }
                        }
                    }
                });
    }

    private void updateDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        txtSelectedDate.setText("Date: " + sdf.format(calendar.getTime()));
    }

    private void updateTimeText() {
        if (!hasSelectedTime) {
            txtSelectedTime.setText("Time: None");
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        txtSelectedTime.setText("Time: " + sdf.format(calendar.getTime()));
    }

    private void showDatePicker() {
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateText();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        ).show();
    }
    
    private void showTimePicker() {
        new android.app.TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    calendar.set(Calendar.SECOND, 0);
                    hasSelectedTime = true;
                    updateTimeText();
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
        ).show();
    }

    private void addSubtaskView(String title, boolean isCompleted) {
        View view = getLayoutInflater().inflate(R.layout.item_subtask_edit, layoutSubtasks, false);
        EditText etSubtaskTitle = view.findViewById(R.id.etSubtaskTitle);
        CheckBox cbSubtask = view.findViewById(R.id.cbSubtask);
        ImageButton btnRemoveSubtask = view.findViewById(R.id.btnRemoveSubtask);

        etSubtaskTitle.setText(title);
        cbSubtask.setChecked(isCompleted);

        btnRemoveSubtask.setOnClickListener(v -> layoutSubtasks.removeView(view));

        layoutSubtasks.addView(view);
    }

    private void saveTask() {
        String title = etTaskTitle.getText().toString().trim();
        String desc = etTaskDesc.getText().toString().trim();

        if (title.isEmpty()) {
            etTaskTitle.setError("Task title is required");
            etTaskTitle.requestFocus();
            return;
        }

        String priority = "MEDIUM";
        int checkedId = rgPriority.getCheckedRadioButtonId();
        if (checkedId == R.id.rbLow) {
            priority = "LOW";
        } else if (checkedId == R.id.rbHigh) {
            priority = "HIGH";
        }

        Long dueDate = cbHasDueDate.isChecked() ? calendar.getTimeInMillis() : null;
        Long dueTime = (cbHasDueDate.isChecked() && hasSelectedTime) ? calendar.getTimeInMillis() : null;

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("description", desc);
        data.put("dueDate", dueDate);
        data.put("dueTime", dueTime);
        data.put("priority", priority);
        data.put("isImportant", cbImportant.isChecked());
        data.put("recurringPattern", spinnerRepeat.getSelectedItem().toString());
        
        List<Map<String, Object>> subtasksList = new ArrayList<>();
        for (int i = 0; i < layoutSubtasks.getChildCount(); i++) {
            View view = layoutSubtasks.getChildAt(i);
            EditText etSubTitle = view.findViewById(R.id.etSubtaskTitle);
            CheckBox cbSub = view.findViewById(R.id.cbSubtask);
            String subTitle = etSubTitle.getText().toString().trim();
            if (!subTitle.isEmpty()) {
                Map<String, Object> subMap = new HashMap<>();
                subMap.put("title", subTitle);
                subMap.put("isCompleted", cbSub.isChecked());
                subtasksList.add(subMap);
            }
        }
        data.put("subtasks", subtasksList);

        btnSaveTask.setEnabled(false);
        btnSaveTask.setText("Saving...");

        if (taskId == null) {
            data.put("isCompleted", false);
            data.put("isArchived", false);
            data.put("createdAt", System.currentTimeMillis());
            db.collection("users")
                    .document(userId)
                    .collection("tasks")
                    .add(data)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(AddTaskActivity.this, "Task saved successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        btnSaveTask.setEnabled(true);
                        btnSaveTask.setText("Save Task");
                        Toast.makeText(AddTaskActivity.this, "Failed to save task: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else {
            db.collection("users")
                    .document(userId)
                    .collection("tasks")
                    .document(taskId)
                    .update(data)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(AddTaskActivity.this, "Task updated successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        btnSaveTask.setEnabled(true);
                        btnSaveTask.setText("Update Task");
                        Toast.makeText(AddTaskActivity.this, "Failed to update task: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }
}
