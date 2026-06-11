package com.example.frienddebt.ui.fragment;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Reminder;
import com.example.frienddebt.notification.ReminderScheduler;
import com.example.frienddebt.ui.AddEditNoteActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class BottomSheetConvertReminderFragment extends BottomSheetDialogFragment {

    private EditText etReminderTitle;
    private TextView txtDate, txtTime;
    
    private Calendar calendar = Calendar.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bottom_sheet_convert_reminder, container, false);

        etReminderTitle = view.findViewById(R.id.etReminderTitle);
        txtDate = view.findViewById(R.id.txtDate);
        txtTime = view.findViewById(R.id.txtTime);

        // Default to tomorrow 9 AM
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        updateDateTimeText();

        if (getActivity() instanceof AddEditNoteActivity) {
            AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();
            String title = activity.getNoteTitle();
            if (title != null && !title.isEmpty()) {
                etReminderTitle.setText(title);
            }
        }

        txtDate.setOnClickListener(v -> showDatePicker());
        txtTime.setOnClickListener(v -> showTimePicker());

        view.findViewById(R.id.btnCreateReminder).setOnClickListener(v -> createReminder());

        return view;
    }

    private void updateDateTimeText() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        txtDate.setText(sdfDate.format(calendar.getTime()));

        SimpleDateFormat sdfTime = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        txtTime.setText(sdfTime.format(calendar.getTime()));
    }

    private void showDatePicker() {
        new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateTimeText();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void showTimePicker() {
        new TimePickerDialog(
                requireContext(),
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    calendar.set(Calendar.SECOND, 0);
                    updateDateTimeText();
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
        ).show();
    }

    private void createReminder() {
        String title = etReminderTitle.getText().toString().trim();
        if (title.isEmpty()) {
            etReminderTitle.setError("Required");
            return;
        }

        long triggerTime = calendar.getTimeInMillis();
        if (triggerTime <= System.currentTimeMillis()) {
            Toast.makeText(requireContext(), "Reminder must be in the future", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!(getActivity() instanceof AddEditNoteActivity)) return;
        AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();
        
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;

        String msg = "Linked to Note: " + title;
        
        Reminder reminder = new Reminder(
                null, 
                title, 
                msg, 
                triggerTime, 
                "NONE", 
                "MEDIUM", 
                "NOTE", 
                false, 
                false, 
                null, 
                System.currentTimeMillis(), 
                null
        );

        if (activity.noteId != null) {
            reminder.setLinkedItemId(activity.noteId);
            reminder.setLinkedItemType("NOTE");
        }

        if (getView() != null) {
            getView().findViewById(R.id.btnCreateReminder).setEnabled(false);
        }

        FirebaseFirestore.getInstance().collection("users")
                .document(auth.getCurrentUser().getUid())
                .collection("reminders")
                .add(reminder.toFirestoreMap())
                .addOnSuccessListener(ref -> {
                    reminder.setId(ref.getId());
                    ReminderScheduler.scheduleReminder(requireContext(), reminder);
                    Toast.makeText(requireContext(), "Reminder Scheduled", Toast.LENGTH_SHORT).show();
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to schedule reminder", Toast.LENGTH_SHORT).show();
                });
    }
}
