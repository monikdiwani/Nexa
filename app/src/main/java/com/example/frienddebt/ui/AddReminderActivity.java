package com.example.frienddebt.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Reminder;
import com.example.frienddebt.notification.ReminderScheduler;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AddReminderActivity extends AppCompatActivity {

    private EditText etReminderTitle, etReminderMsg;
    private AutoCompleteTextView actvCategory, actvRepeat;
    private TextInputLayout tilCustomCategory;
    private TextInputEditText etCustomCategory;
    private RadioGroup rgPriority;
    private TextView txtSelectedDate, txtSelectedTime;
    private Button btnSelectDate, btnSelectTime, btnSaveReminder;
    private ImageButton btnBack;

    private Calendar calendar = Calendar.getInstance();
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private static final String[] CATEGORIES = {"BILL", "MEETING", "TASK", "MEDICINE", "SHOPPING", "CUSTOM"};
    private static final String[] REPEAT_OPTIONS = {"NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_reminder);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind Views
        etReminderTitle = findViewById(R.id.etReminderTitle);
        etReminderMsg = findViewById(R.id.etReminderMsg);
        actvCategory = findViewById(R.id.actvCategory);
        actvRepeat = findViewById(R.id.actvRepeat);
        rgPriority = findViewById(R.id.rgPriority);
        txtSelectedDate = findViewById(R.id.txtSelectedDate);
        txtSelectedTime = findViewById(R.id.txtSelectedTime);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        btnSelectTime = findViewById(R.id.btnSelectTime);
        btnSaveReminder = findViewById(R.id.btnSaveReminder);
        btnBack = findViewById(R.id.btnBack);
        tilCustomCategory = findViewById(R.id.tilCustomCategory);
        etCustomCategory = findViewById(R.id.etCustomCategory);

        btnBack.setOnClickListener(v -> finish());

        // Category dropdown Setup
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, CATEGORIES);
        actvCategory.setAdapter(catAdapter);
        actvCategory.setText("CUSTOM", false); // Default to CUSTOM
        tilCustomCategory.setVisibility(View.VISIBLE); // Show since CUSTOM is default

        actvCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selected = actvCategory.getText().toString().trim();
            if ("CUSTOM".equals(selected)) {
                tilCustomCategory.setVisibility(View.VISIBLE);
            } else {
                tilCustomCategory.setVisibility(View.GONE);
                etCustomCategory.setText("");
            }
        });

        // Repeat dropdown Setup
        ArrayAdapter<String> repeatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, REPEAT_OPTIONS);
        actvRepeat.setAdapter(repeatAdapter);
        actvRepeat.setText("NONE", false); // Default to NONE

        // Default 10 minutes in future
        calendar.add(Calendar.MINUTE, 10);
        updateDateText();
        updateTimeText();

        btnSelectDate.setOnClickListener(v -> showDatePicker());
        btnSelectTime.setOnClickListener(v -> showTimePicker());

        btnSaveReminder.setOnClickListener(v -> saveReminder());
    }

    private void updateDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        txtSelectedDate.setText("Date: " + sdf.format(calendar.getTime()));
    }

    private void updateTimeText() {
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
        new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    updateTimeText();
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
        ).show();
    }

    private void saveReminder() {
        String title = etReminderTitle.getText().toString().trim();
        String msg = etReminderMsg.getText().toString().trim();

        if (title.isEmpty()) {
            etReminderTitle.setError("Title is required");
            etReminderTitle.requestFocus();
            return;
        }

        long triggerTime = calendar.getTimeInMillis();
        if (triggerTime <= System.currentTimeMillis()) {
            Toast.makeText(this, "Reminder must be set in the future", Toast.LENGTH_SHORT).show();
            return;
        }

        String priority = "MEDIUM";
        int checkedId = rgPriority.getCheckedRadioButtonId();
        if (checkedId == R.id.rbLow) {
            priority = "LOW";
        } else if (checkedId == R.id.rbHigh) {
            priority = "HIGH";
        }

        String category = actvCategory.getText().toString().trim();
        if ("CUSTOM".equals(category)) {
            String customCat = etCustomCategory.getText().toString().trim();
            if (customCat.isEmpty()) {
                etCustomCategory.setError("Custom category is required");
                etCustomCategory.requestFocus();
                return;
            }
            category = customCat;
        }
        String repeat = actvRepeat.getText().toString().trim();

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        long createdAt = System.currentTimeMillis();

        Reminder reminder = new Reminder(null, title, msg, triggerTime, repeat, priority, category, false, false, null, createdAt, null);

        btnSaveReminder.setEnabled(false);
        btnSaveReminder.setText("Scheduling...");

        db.collection("users")
                .document(userId)
                .collection("reminders")
                .add(reminder.toFirestoreMap())
                .addOnSuccessListener(ref -> {
                    String id = ref.getId();
                    reminder.setId(id);
                    ReminderScheduler.scheduleReminder(AddReminderActivity.this, reminder);

                    Toast.makeText(AddReminderActivity.this, "Reminder scheduled!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSaveReminder.setEnabled(true);
                    btnSaveReminder.setText("Schedule Reminder");
                    Toast.makeText(AddReminderActivity.this, "Failed to schedule: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
