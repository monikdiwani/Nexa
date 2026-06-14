package com.example.frienddebt.ui.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.frienddebt.R;
import com.example.frienddebt.ui.AddEditNoteActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class BottomSheetFolderPickerFragment extends BottomSheetDialogFragment {

    private Spinner spinnerFolder;
    private EditText etNoteTags;
    private Button btnCreateFolder;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private final List<String> folderItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bottom_sheet_folder_picker, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        spinnerFolder = view.findViewById(R.id.spinnerFolder);
        etNoteTags = view.findViewById(R.id.etNoteTags);
        btnCreateFolder = view.findViewById(R.id.btnCreateFolder);

        setupFolderSpinner();

        btnCreateFolder.setOnClickListener(v -> showCreateFolderDialog());

        if (getActivity() instanceof AddEditNoteActivity) {
            AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();
            setSpinnerSelection(activity.selectedFolder);
            if (activity.tags != null && !activity.tags.isEmpty()) {
                etNoteTags.setText(TextUtils.join(", ", activity.tags));
            }
        }

        view.findViewById(R.id.btnDone).setOnClickListener(v -> {
            if (getActivity() instanceof AddEditNoteActivity) {
                AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();
                
                activity.selectedFolder = spinnerFolder.getSelectedItem() != null ? spinnerFolder.getSelectedItem().toString() : "Personal";
                
                String labelStr = etNoteTags.getText().toString().trim();
                List<String> newTags = new ArrayList<>();
                if (!labelStr.isEmpty()) {
                    String[] splitTags = labelStr.split(",");
                    for (String t : splitTags) {
                        String cleanT = t.trim();
                        if (!cleanT.isEmpty()) {
                            newTags.add(cleanT);
                        }
                    }
                }
                activity.tags = newTags;
                activity.saveNote(true); // force save since we changed metadata
            }
            dismiss();
        });

        return view;
    }

    private void setupFolderSpinner() {
        folderItems.clear();
        folderItems.addAll(Arrays.asList("Personal", "Work"));

        if (auth != null && auth.getCurrentUser() != null) {
            db.collection("users").document(auth.getCurrentUser().getUid())
                    .collection("noteFolders")
                    .orderBy("name")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            String name = doc.getString("name");
                            if (name != null && !containsIgnoreCase(folderItems, name)) {
                                folderItems.add(name);
                            }
                        }
                        refreshSpinner();
                    })
                    .addOnFailureListener(e -> refreshSpinner());
        } else {
            refreshSpinner();
        }
    }

    private void refreshSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, folderItems);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFolder.setAdapter(adapter);

        if (getActivity() instanceof AddEditNoteActivity) {
            AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();
            setSpinnerSelection(activity.selectedFolder);
        }
    }

    private void showCreateFolderDialog() {
        if (auth == null || auth.getCurrentUser() == null || db == null) return;

        EditText input = new EditText(requireContext());
        input.setHint("Folder name");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int padding = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Create folder")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), "Folder name is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (containsIgnoreCase(folderItems, name)) {
                        Toast.makeText(requireContext(), "Folder already exists", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createFolder(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createFolder(String name) {
        String folderId = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        db.collection("users").document(auth.getCurrentUser().getUid())
                .collection("noteFolders")
                .document(folderId)
                .set(new java.util.HashMap<String, Object>() {{
                    put("name", name.trim());
                    put("createdAt", System.currentTimeMillis());
                }})
                .addOnSuccessListener(unused -> {
                    Toast.makeText(requireContext(), "Folder created", Toast.LENGTH_SHORT).show();
                    folderItems.add(name.trim());
                    refreshSpinner();
                    setSpinnerSelection(name.trim());
                })
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Failed to create folder", Toast.LENGTH_SHORT).show());
    }

    private void setSpinnerSelection(String folder) {
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerFolder.getAdapter();
        if (adapter != null) {
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i) != null && adapter.getItem(i).equalsIgnoreCase(folder)) {
                    spinnerFolder.setSelection(i);
                    break;
                }
            }
        }
    }

    private boolean containsIgnoreCase(List<String> items, String candidate) {
        for (String item : items) {
            if (item != null && item.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }
}
