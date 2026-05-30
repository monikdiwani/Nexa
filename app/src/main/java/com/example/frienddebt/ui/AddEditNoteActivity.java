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
        btnBack.setOnClickListener(v -> {
            saveNote(false);
            finish();
        });

        // Manual save button: save and show toast then exit
        btnSave.setOnClickListener(v -> {
            saveNote(true);
            finish();
        });

        // System navigation bar back button: save silently then exit
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveNote(false);
                finish();
            }
        });

        setupMarkdownToolbar();
    }

    private void setupMarkdownToolbar() {
        findViewById(R.id.btnFormatBold).setOnClickListener(v -> insertMarkdown("**", "**"));
        findViewById(R.id.btnFormatItalic).setOnClickListener(v -> insertMarkdown("*", "*"));
        findViewById(R.id.btnFormatHeader).setOnClickListener(v -> insertMarkdownAtLineStart("### "));
        findViewById(R.id.btnFormatBullet).setOnClickListener(v -> insertMarkdownAtLineStart("- "));
        findViewById(R.id.btnFormatTask).setOnClickListener(v -> insertMarkdownAtLineStart("- [ ] "));

        android.widget.Button btnTogglePreview = findViewById(R.id.btnTogglePreview);
        android.widget.TextView txtPreview = findViewById(R.id.txtMarkdownPreview);

        btnTogglePreview.setOnClickListener(v -> {
            if (etNoteContent.getVisibility() == android.view.View.VISIBLE) {
                // Switch to Preview
                etNoteContent.setVisibility(android.view.View.GONE);
                txtPreview.setVisibility(android.view.View.VISIBLE);
                btnTogglePreview.setText("Edit");
                
                // Render Markdown
                io.noties.markwon.Markwon markwon = io.noties.markwon.Markwon.builder(this)
                    .usePlugin(io.noties.markwon.ext.tasklist.TaskListPlugin.create(this))
                    .build();
                markwon.setMarkdown(txtPreview, etNoteContent.getText().toString());
            } else {
                // Switch to Edit
                etNoteContent.setVisibility(android.view.View.VISIBLE);
                txtPreview.setVisibility(android.view.View.GONE);
                btnTogglePreview.setText("Preview");
            }
        });
    }

    private void insertMarkdown(String prefix, String suffix) {
        int start = etNoteContent.getSelectionStart();
        int end = etNoteContent.getSelectionEnd();
        
        android.text.Editable editable = etNoteContent.getText();
        if (editable == null) return;

        if (start < 0 || end < 0) {
            start = editable.length();
            end = editable.length();
        }

        if (start == end) {
            // No selection
            editable.insert(start, prefix + suffix);
            etNoteContent.setSelection(start + prefix.length());
        } else {
            // Wrap selection
            editable.insert(start, prefix);
            editable.insert(end + prefix.length(), suffix);
            etNoteContent.setSelection(end + prefix.length() + suffix.length());
        }
    }

    private void insertMarkdownAtLineStart(String prefix) {
        int start = etNoteContent.getSelectionStart();
        android.text.Editable editable = etNoteContent.getText();
        if (editable == null) return;

        if (start < 0) start = editable.length();

        // Find the start of the current line
        String text = editable.toString();
        int lineStart = start;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        editable.insert(lineStart, prefix);
        etNoteContent.setSelection(start + prefix.length());
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

    private boolean hasSaved = false;

    @Override
    protected void onPause() {
        super.onPause();
        // Auto-save when activity loses focus (home button, app switch, or finishing)
        if (!hasSaved) {
            saveNote(false);
        }
    }

    /**
     * Saves the note to Firestore.
     */
    private void saveNote(boolean showToast) {
        if (hasSaved) return;

        final String title = etNoteTitle.getText().toString().trim();
        final String content = etNoteContent.getText().toString().trim();

        if (noteId != null && title.equals(originalTitle) && content.equals(originalContent)) {
            hasSaved = true;
            return;
        }

        if (noteId == null && title.isEmpty() && content.isEmpty()) {
            hasSaved = true;
            return;
        }

        if (auth.getCurrentUser() == null) {
            hasSaved = true;
            return;
        }

        hasSaved = true;
        String userId = auth.getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("content", content);
        data.put("updatedAt", System.currentTimeMillis());

        if (noteId == null) {
            noteId = db.collection("users")
                    .document(userId)
                    .collection("notes")
                    .document()
                    .getId();
            data.put("createdAt", System.currentTimeMillis());
        }

        originalTitle = title;
        originalContent = content;

        db.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId)
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    if (showToast) {
                        Toast.makeText(AddEditNoteActivity.this, "Note saved!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    hasSaved = false;
                    originalTitle = "";
                    originalContent = "";
                    Toast.makeText(AddEditNoteActivity.this,
                            "Save failed, please try again", Toast.LENGTH_SHORT).show();
                });
    }
}
