package com.example.frienddebt.ui;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.utils.StatusBarUtil;
import com.example.frienddebt.R;
import com.example.frienddebt.model.Note;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AddEditNoteActivity extends AppCompatActivity {

    private EditText etNoteTitle, etNoteContent;
    private ImageButton btnBack, btnSave;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private String noteId = null;
    private String originalTitle = "";
    private String originalContent = "";

    // Guard flag to prevent double-finish
    private boolean isFinishing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_note);
        StatusBarUtil.applyStatusBarPadding(this);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        etNoteTitle = findViewById(R.id.etNoteTitle);
        etNoteContent = findViewById(R.id.etNoteContent);
        btnBack = findViewById(R.id.btnBack);
        btnSave = findViewById(R.id.btnSave);

        noteId = getIntent().getStringExtra("NOTE_ID");

        if (noteId != null) {
            loadNoteDetails();
        }

        // In-app back arrow: save silently then exit
        btnBack.setOnClickListener(v -> saveAndExit(false));

        // Manual save button: save and show toast then exit
        btnSave.setOnClickListener(v -> saveAndExit(true));

        // System navigation bar back button: save silently then exit
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveAndExit(false);
            }
        });
    }

    private void loadNoteDetails() {
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Note note = Note.fromDocument(documentSnapshot);
                        originalTitle = note.getTitle() != null ? note.getTitle() : "";
                        originalContent = note.getContent() != null ? note.getContent() : "";

                        etNoteTitle.setText(originalTitle);
                        etNoteContent.setText(originalContent);
                    }
                });
    }

    /**
     * Saves the note and then calls finish() only after Firestore confirms the write.
     * This ensures no data is lost when exiting via the system navigation back button.
     *
     * @param showToast whether to show a "Note saved!" toast on success
     */
    private void saveAndExit(boolean showToast) {
        // Prevent duplicate calls
        if (isFinishing) return;

        final String title = etNoteTitle.getText().toString().trim();
        final String content = etNoteContent.getText().toString().trim();

        // Nothing changed — no need to write, just exit
        if (noteId != null && title.equals(originalTitle) && content.equals(originalContent)) {
            isFinishing = true;
            finish();
            return;
        }

        // New empty note — nothing to save, just exit
        if (noteId == null && title.isEmpty() && content.isEmpty()) {
            isFinishing = true;
            finish();
            return;
        }

        if (auth.getCurrentUser() == null) {
            isFinishing = true;
            finish();
            return;
        }

        isFinishing = true;
        String userId = auth.getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("content", content);
        data.put("updatedAt", System.currentTimeMillis());

        if (noteId == null) {
            // Pre-generate ID so we never create duplicates
            noteId = db.collection("users")
                    .document(userId)
                    .collection("notes")
                    .document()
                    .getId();
            data.put("createdAt", System.currentTimeMillis());
        }

        // Update local originals immediately to prevent re-triggers
        originalTitle = title;
        originalContent = content;

        // Write to Firestore — finish() is called INSIDE the callback
        // so the activity never closes before the write is complete
        db.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId)
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    if (showToast) {
                        Toast.makeText(AddEditNoteActivity.this, "Note saved!", Toast.LENGTH_SHORT).show();
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    // Reset so user can try again
                    isFinishing = false;
                    originalTitle = "";
                    originalContent = "";
                    Toast.makeText(AddEditNoteActivity.this,
                            "Save failed, please try again", Toast.LENGTH_SHORT).show();
                });
    }
}
