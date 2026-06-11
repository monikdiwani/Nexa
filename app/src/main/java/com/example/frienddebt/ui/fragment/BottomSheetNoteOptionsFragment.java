package com.example.frienddebt.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.frienddebt.R;
import com.example.frienddebt.ui.AddEditNoteActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class BottomSheetNoteOptionsFragment extends BottomSheetDialogFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bottom_sheet_note_options, container, false);

        view.findViewById(R.id.btnChangeColor).setOnClickListener(v -> {
            dismiss();
            BottomSheetColorPickerFragment colorPicker = new BottomSheetColorPickerFragment();
            colorPicker.show(getParentFragmentManager(), "ColorPicker");
        });

        view.findViewById(R.id.btnChangeFolder).setOnClickListener(v -> {
            dismiss();
            BottomSheetFolderPickerFragment folderPicker = new BottomSheetFolderPickerFragment();
            folderPicker.show(getParentFragmentManager(), "FolderPicker");
        });

        view.findViewById(R.id.btnVersionHistory).setOnClickListener(v -> {
            dismiss();
            showHistoryDialog();
        });

        view.findViewById(R.id.btnShare).setOnClickListener(v -> {
            dismiss();
            shareNote();
        });

        view.findViewById(R.id.btnArchive).setOnClickListener(v -> {
            if (getActivity() instanceof AddEditNoteActivity) {
                AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();
                activity.isArchived = !activity.isArchived;
                activity.saveNote(true);
                activity.finish();
            }
        });

        view.findViewById(R.id.btnTrash).setOnClickListener(v -> {
            if (getActivity() instanceof AddEditNoteActivity) {
                AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();
                activity.isDeleted = true;
                activity.saveNote(true);
                activity.finish();
            }
        });

        return view;
    }

    private void shareNote() {
        if (getActivity() instanceof AddEditNoteActivity) {
            AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();
            String title = activity.getNoteTitle();
            String content = activity.getRawContent();

            if (title.isEmpty() && content.isEmpty()) {
                Toast.makeText(activity, "Nothing to share", Toast.LENGTH_SHORT).show();
                return;
            }

            String shareBody = title.isEmpty() ? content : title + "\n\n" + content;
            android.content.Intent sharingIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, title);
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
            startActivity(android.content.Intent.createChooser(sharingIntent, "Share Note via"));
        }
    }

    private void showHistoryDialog() {
        if (getActivity() instanceof AddEditNoteActivity) {
            AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();
            if (activity.noteId == null) {
                Toast.makeText(activity, "Save note first to view history", Toast.LENGTH_SHORT).show();
                return;
            }

            android.app.ProgressDialog pd = new android.app.ProgressDialog(activity);
            pd.setMessage("Loading history...");
            pd.show();

            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users")
                    .document(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid())
                    .collection("notes")
                    .document(activity.noteId)
                    .collection("history")
                    .orderBy("savedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        pd.dismiss();
                        if (queryDocumentSnapshots.isEmpty()) {
                            Toast.makeText(activity, "No previous versions found", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        java.util.List<com.google.firebase.firestore.DocumentSnapshot> docs = queryDocumentSnapshots.getDocuments();
                        String[] items = new String[docs.size()];
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy - hh:mm a", java.util.Locale.getDefault());

                        for (int i = 0; i < docs.size(); i++) {
                            Long savedAt = docs.get(i).getLong("savedAt");
                            if (savedAt != null) {
                                items[i] = sdf.format(new java.util.Date(savedAt));
                            } else {
                                items[i] = "Unknown Date";
                            }
                        }

                        new android.app.AlertDialog.Builder(activity)
                                .setTitle("Version History")
                                .setItems(items, (dialog, which) -> {
                                    com.google.firebase.firestore.DocumentSnapshot selected = docs.get(which);
                                    String oldTitle = selected.getString("title");
                                    String oldContent = selected.getString("content");
                                    activity.updateContentFromHistory(oldTitle, oldContent);
                                    Toast.makeText(activity, "Restored previous version", Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Close", null)
                                .show();
                    })
                    .addOnFailureListener(e -> {
                        pd.dismiss();
                        Toast.makeText(activity, "Failed to load history", Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
