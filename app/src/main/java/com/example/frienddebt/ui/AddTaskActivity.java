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
import java.util.Calendar;
import java.util.Locale;

import com.example.frienddebt.utils.StatusBarUtil;

public class AddTaskActivity extends AppCompatActivity {

    private EditText etTaskTitle, etTaskDesc;
    private RadioGroup rgPriority;
    private CheckBox cbHasDueDate;
    private LinearLayout layoutDueDate;
    private TextView txtSelectedDate;
    private Button btnSelectDate, btnSaveTask;
    private ImageButton btnBack;

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
        layoutDueDate = findViewById(R.id.layoutDueDate);
        txtSelectedDate = findViewById(R.id.txtSelectedDate);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        btnSaveTask = findViewById(R.id.btnSaveTask);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        // Toggle Due Date Selection Layout
        cbHasDueDate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutDueDate.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Setup Date Picker
        updateDateText();
        btnSelectDate.setOnClickListener(v -> showDatePicker());

        // Save Button Click
        btnSaveTask.setOnClickListener(v -> saveTask());
    }

    private void updateDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        txtSelectedDate.setText("Due Date: " + sdf.format(calendar.getTime()));
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

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        long createdAt = System.currentTimeMillis();

        Task task = new Task(null, title, desc, dueDate, priority, false, createdAt, null);

        btnSaveTask.setEnabled(false);
        btnSaveTask.setText("Saving...");

        db.collection("users")
                .document(userId)
                .collection("tasks")
                .add(task.toFirestoreMap())
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(AddTaskActivity.this, "Task saved successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSaveTask.setEnabled(true);
                    btnSaveTask.setText("Save Task");
                    Toast.makeText(AddTaskActivity.this, "Failed to save task: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
