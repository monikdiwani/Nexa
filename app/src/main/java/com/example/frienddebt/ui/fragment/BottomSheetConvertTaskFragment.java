package com.example.frienddebt.ui.fragment;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.frienddebt.R;
import com.example.frienddebt.ui.AddEditNoteActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BottomSheetConvertTaskFragment extends BottomSheetDialogFragment {

    private EditText etTaskTitle;
    private TextView txtDueDate;
    private Spinner spinnerPriority;
    
    private Calendar calendar = Calendar.getInstance();
    private boolean hasSelectedDate = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bottom_sheet_convert_task, container, false);

        etTaskTitle = view.findViewById(R.id.etTaskTitle);
        txtDueDate = view.findViewById(R.id.txtDueDate);
        spinnerPriority = view.findViewById(R.id.spinnerPriority);

        setupPrioritySpinner();

        if (getActivity() instanceof AddEditNoteActivity) {
            AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();
            String title = activity.getNoteTitle();
            if (title != null && !title.isEmpty()) {
                etTaskTitle.setText(title);
            }
        }

        txtDueDate.setOnClickListener(v -> showDatePicker());

        view.findViewById(R.id.btnCreateTask).setOnClickListener(v -> createTask());

        return view;
    }

    private void setupPrioritySpinner() {
        String[] priorities = {"LOW", "MEDIUM", "HIGH"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, priorities);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPriority.setAdapter(adapter);
        spinnerPriority.setSelection(1); // Default MEDIUM
    }

    private void showDatePicker() {
        new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    txtDueDate.setText(sdf.format(calendar.getTime()));
                    hasSelectedDate = true;
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void createTask() {
        String title = etTaskTitle.getText().toString().trim();
        if (title.isEmpty()) {
            etTaskTitle.setError("Required");
            return;
        }

        if (!(getActivity() instanceof AddEditNoteActivity)) return;
        AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();
        
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("description", "Converted from Note: " + title);
        data.put("priority", spinnerPriority.getSelectedItem().toString());
        data.put("isImportant", false);
        data.put("isRecurring", false);
        data.put("recurringPattern", "NONE");
        data.put("subtasks", new java.util.ArrayList<>());
        
        if (hasSelectedDate) {
            data.put("dueDate", calendar.getTimeInMillis());
        } else {
            data.put("dueDate", null);
        }
        
        data.put("dueTime", null);
        
        if (activity.noteId != null) {
            data.put("linkedItemId", activity.noteId);
            data.put("linkedItemType", "NOTE");
        }

        data.put("isCompleted", false);
        data.put("isArchived", false);
        data.put("createdAt", System.currentTimeMillis());

        FirebaseFirestore.getInstance().collection("users")
                .document(auth.getCurrentUser().getUid())
                .collection("tasks")
                .add(data)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(requireContext(), "Task Created Successfully", Toast.LENGTH_SHORT).show();
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to create task", Toast.LENGTH_SHORT).show();
                });
    }
}
