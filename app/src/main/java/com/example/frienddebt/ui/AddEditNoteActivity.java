package com.example.frienddebt.ui;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
    private boolean isSaved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_note);

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

        btnBack.setOnClickListener(v -> {
            autoSaveAndFinish();
        });

        btnSave.setOnClickListener(v -> {
            saveNote(true);
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

    private void autoSaveAndFinish() {
        saveNote(false);
        finish();
    }

    @Override
    public void onBackPressed() {
        autoSaveAndFinish();
        super.onBackPressed();
    }

    private void saveNote(boolean showToast) {
        if (isSaved) return;

        String title = etNoteTitle.getText().toString().trim();
        String content = etNoteContent.getText().toString().trim();

        // If nothing changed, do not perform save
        if (noteId != null && title.equals(originalTitle) && content.equals(originalContent)) {
            return;
        }

        // If it's a new note and both fields are empty, do not save
        if (noteId == null && title.isEmpty() && content.isEmpty()) {
            return;
        }

        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("content", content);
        data.put("updatedAt", System.currentTimeMillis());

        if (noteId == null) {
            data.put("createdAt", System.currentTimeMillis());
            db.collection("users")
                    .document(userId)
                    .collection("notes")
                    .add(data)
                    .addOnSuccessListener(ref -> {
                        isSaved = true;
                        if (showToast) {
                            Toast.makeText(AddEditNoteActivity.this, "Note saved!", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
        } else {
            db.collection("users")
                    .document(userId)
                    .collection("notes")
                    .document(noteId)
                    .update(data)
                    .addOnSuccessListener(aVoid -> {
                        isSaved = true;
                        if (showToast) {
                            Toast.makeText(AddEditNoteActivity.this, "Note updated!", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
        }
    }
}
