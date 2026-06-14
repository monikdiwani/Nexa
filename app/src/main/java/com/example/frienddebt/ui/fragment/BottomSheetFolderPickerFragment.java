package com.example.frienddebt.ui.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.frienddebt.R;
import com.example.frienddebt.ui.AddEditNoteActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class BottomSheetFolderPickerFragment extends BottomSheetDialogFragment {

    private Spinner spinnerFolder;
    private EditText etNoteTags;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bottom_sheet_folder_picker, container, false);

        spinnerFolder = view.findViewById(R.id.spinnerFolder);
        etNoteTags = view.findViewById(R.id.etNoteTags);

        setupFolderSpinner();

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
        List<String> folders = new ArrayList<>();
        folders.add("Personal");
        folders.add("Work");
        folders.add("Study");
        folders.add("Finance");
        folders.add("Ideas");
        folders.add("Shopping");
        folders.add("Travel");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, folders);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFolder.setAdapter(adapter);

        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users")
                .document(auth.getCurrentUser().getUid())
                .collection("noteFolders")
                .get().addOnSuccessListener(snapshots -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots.getDocuments()) {
                        String name = doc.getString("name");
                        if (name != null && !folders.contains(name)) {
                            folders.add(name);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    
                    if (getActivity() instanceof AddEditNoteActivity) {
                        AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();
                        setSpinnerSelection(activity.selectedFolder);
                    }
                });
        }
    }

    private void setSpinnerSelection(String folder) {
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerFolder.getAdapter();
        if (adapter != null) {
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).equalsIgnoreCase(folder)) {
                    spinnerFolder.setSelection(i);
                    break;
                }
            }
        }
    }
}
