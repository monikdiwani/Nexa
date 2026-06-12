package com.example.frienddebt.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.AlignmentSpan;
import android.text.style.BulletSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Note;
import com.example.frienddebt.utils.StatusBarUtil;
import com.example.frienddebt.utils.UndoRedoManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddEditNoteActivity extends AppCompatActivity {

    private EditText etNoteTitle, etNoteContent;
    private ImageButton btnBack, btnMenu, btnUndo, btnRedo;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private UndoRedoManager undoRedoManager;

    public String noteId = null;
    private boolean isNewNote = false;
    private String originalTitle = "";
    private String originalContent = ""; 
    
    public String selectedColor = "#surface_primary"; 
    public String selectedFolder = "Personal";
    public List<String> tags = new ArrayList<>();
    public boolean isPinned = false;
    public boolean isArchived = false;
    public boolean isDeleted = false;
    public String noteType = "TEXT";
    private List<String> imageUrls = new ArrayList<>();

    private Handler autoSaveHandler = new Handler();
    private Runnable autoSaveRunnable;
    private boolean hasSaved = false;

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
        btnMenu = findViewById(R.id.btnMenu);
        btnUndo = findViewById(R.id.btnUndo);
        btnRedo = findViewById(R.id.btnRedo);

        undoRedoManager = new UndoRedoManager(etNoteContent);

        noteId = getIntent().getStringExtra("NOTE_ID");
        if (noteId == null && auth.getCurrentUser() != null) {
            String userId = auth.getCurrentUser().getUid();
            noteId = db.collection("users")
                    .document(userId)
                    .collection("notes")
                    .document()
                    .getId();
            isNewNote = true;
        }

        btnMenu.setOnClickListener(v -> showSamsungMenu());

        setupAutoSaveListeners();

        if (noteId != null && !isNewNote) {
            loadNoteDetails();
        }

        btnBack.setOnClickListener(v -> {
            saveNote(false);
            finish();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveNote(false);
                finish();
            }
        });

        setupFormattingToolbar();
    }

    private void showSamsungMenu() {
        View popupView = LayoutInflater.from(this).inflate(R.layout.layout_samsung_note_menu, null);
        PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(16f);
        
        ImageButton menuStar = popupView.findViewById(R.id.menuStar);
        ImageButton menuLock = popupView.findViewById(R.id.menuLock);
        
        if (isPinned) {
            menuStar.setColorFilter(getResources().getColor(R.color.primary));
        }

        popupView.findViewById(R.id.menuStar).setOnClickListener(v -> {
            isPinned = !isPinned;
            triggerAutoSave();
            popupWindow.dismiss();
            Toast.makeText(this, isPinned ? "Pinned" : "Unpinned", Toast.LENGTH_SHORT).show();
        });

        popupView.findViewById(R.id.menuSearch).setOnClickListener(v -> {
            popupWindow.dismiss();
            Toast.makeText(this, "Search clicked", Toast.LENGTH_SHORT).show();
        });

        popupView.findViewById(R.id.menuAddTags).setOnClickListener(v -> {
            popupWindow.dismiss();
            Toast.makeText(this, "Add tags clicked", Toast.LENGTH_SHORT).show();
        });

        popupWindow.showAsDropDown(btnMenu, 0, 16);
    }

    private void setupAutoSaveListeners() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { triggerAutoSave(); }
            @Override
            public void afterTextChanged(Editable s) {}
        };
        etNoteTitle.addTextChangedListener(textWatcher);
        etNoteContent.addTextChangedListener(textWatcher);
    }

    private void triggerAutoSave() {
        hasSaved = false;
        if (autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
        }
        autoSaveRunnable = () -> saveNote(false);
        autoSaveHandler.postDelayed(autoSaveRunnable, 2000);
    }

    private void setupFormattingToolbar() {
        btnUndo.setOnClickListener(v -> undoRedoManager.undo());
        btnRedo.setOnClickListener(v -> undoRedoManager.redo());

        findViewById(R.id.btnFormatBold).setOnClickListener(v -> toggleStyleSpan(Typeface.BOLD));
        findViewById(R.id.btnFormatItalic).setOnClickListener(v -> toggleStyleSpan(Typeface.ITALIC));
        findViewById(R.id.btnFormatUnderline).setOnClickListener(v -> toggleUnderlineSpan());
        findViewById(R.id.btnFormatStrikethrough).setOnClickListener(v -> toggleStrikethroughSpan());
        findViewById(R.id.btnFormatBullet).setOnClickListener(v -> toggleBulletSpan());
        findViewById(R.id.btnFormatTask).setOnClickListener(v -> insertCheckbox());
        
        findViewById(R.id.btnFormatAlignLeft).setOnClickListener(v -> toggleAlignment(Layout.Alignment.ALIGN_NORMAL));
        findViewById(R.id.btnFormatAlignCenter).setOnClickListener(v -> toggleAlignment(Layout.Alignment.ALIGN_CENTER));
        findViewById(R.id.btnFormatAlignRight).setOnClickListener(v -> toggleAlignment(Layout.Alignment.ALIGN_OPPOSITE));
        
        findViewById(R.id.btnFormatListNumber).setOnClickListener(v -> {
             int start = etNoteContent.getSelectionStart();
             if (start >= 0) {
                 etNoteContent.getText().insert(start, "1. ");
             }
        });
    }

    private void toggleStyleSpan(int style) {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        int end = etNoteContent.getSelectionEnd();
        if (start < 0 || end < 0 || start == end) return; 

        Editable editable = etNoteContent.getText();
        StyleSpan[] spans = editable.getSpans(start, end, StyleSpan.class);
        boolean exists = false;
        for (StyleSpan span : spans) {
            if (span.getStyle() == style) {
                editable.removeSpan(span);
                exists = true;
            }
        }
        if (!exists) {
            editable.setSpan(new StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        triggerAutoSave();
    }

    private void toggleUnderlineSpan() {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        int end = etNoteContent.getSelectionEnd();
        if (start < 0 || end < 0 || start == end) return;

        Editable editable = etNoteContent.getText();
        UnderlineSpan[] spans = editable.getSpans(start, end, UnderlineSpan.class);
        if (spans.length > 0) {
            for (UnderlineSpan span : spans) editable.removeSpan(span);
        } else {
            editable.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        triggerAutoSave();
    }

    private void toggleStrikethroughSpan() {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        int end = etNoteContent.getSelectionEnd();
        if (start < 0 || end < 0 || start == end) return;

        Editable editable = etNoteContent.getText();
        StrikethroughSpan[] spans = editable.getSpans(start, end, StrikethroughSpan.class);
        if (spans.length > 0) {
            for (StrikethroughSpan span : spans) editable.removeSpan(span);
        } else {
            editable.setSpan(new StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        triggerAutoSave();
    }

    private void toggleBulletSpan() {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        Editable editable = etNoteContent.getText();
        if (start < 0) return;

        String text = editable.toString();
        int lineStart = start;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        int lineEnd = start;
        while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;

        BulletSpan[] spans = editable.getSpans(lineStart, lineEnd, BulletSpan.class);
        if (spans.length > 0) {
            for (BulletSpan span : spans) editable.removeSpan(span);
        } else {
            editable.setSpan(new BulletSpan(40), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        triggerAutoSave();
    }
    
    private void toggleAlignment(Layout.Alignment alignment) {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        Editable editable = etNoteContent.getText();
        if (start < 0) return;

        String text = editable.toString();
        int lineStart = start;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        int lineEnd = start;
        while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;

        AlignmentSpan[] spans = editable.getSpans(lineStart, lineEnd, AlignmentSpan.class);
        for (AlignmentSpan span : spans) editable.removeSpan(span);
        
        editable.setSpan(new AlignmentSpan.Standard(alignment), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        triggerAutoSave();
    }

    private void insertCheckbox() {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        Editable editable = etNoteContent.getText();
        if (start < 0) start = editable.length();
        editable.insert(start, "\u2610 "); 
        triggerAutoSave();
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
                        
                        selectedColor = note.getColorCode() != null ? note.getColorCode() : "#surface_primary";
                        isPinned = note.isPinned();
                        isArchived = note.isArchived();
                        isDeleted = note.isDeleted();
                        noteType = note.getType() != null ? note.getType() : "TEXT";
                        selectedFolder = note.getFolder() != null ? note.getFolder() : "Personal";
                        
                        tags.clear();
                        if (note.getTags() != null && !note.getTags().isEmpty()) {
                            tags.addAll(note.getTags());
                        }
                        if (note.getImageUrls() != null && !note.getImageUrls().isEmpty()) {
                            imageUrls.clear();
                            imageUrls.addAll(note.getImageUrls());
                        } 

                        etNoteTitle.setText(originalTitle);
                        
                        if (originalContent.contains("<") && originalContent.contains(">")) {
                            etNoteContent.setText(Html.fromHtml(originalContent, Html.FROM_HTML_MODE_LEGACY));
                        } else {
                            etNoteContent.setText(originalContent);
                        }
                        
                        applyColor(selectedColor);
                    }
                });
    }

    public void applyColor(String colorHex) {
        selectedColor = colorHex;
        if (colorHex == null || colorHex.equals("#surface_primary")) {
            int colorSurface = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, android.graphics.Color.WHITE);
            findViewById(android.R.id.content).setBackgroundColor(colorSurface);
        } else {
            findViewById(android.R.id.content).setBackgroundColor(android.graphics.Color.parseColor(colorHex));
        }
        triggerAutoSave();
    }
    
    public void updateContentFromHistory(String oldTitle, String oldContent) {
        if (oldTitle != null) etNoteTitle.setText(oldTitle);
        if (oldContent != null) {
            if (oldContent.contains("<") && oldContent.contains(">")) {
                etNoteContent.setText(Html.fromHtml(oldContent, Html.FROM_HTML_MODE_LEGACY));
            } else {
                etNoteContent.setText(oldContent);
            }
        }
        triggerAutoSave();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNote(false);
    }

    public void saveNote(boolean force) {
        if (hasSaved && !force) return; 

        final String title = etNoteTitle.getText().toString().trim();
        final String contentHtml = Html.toHtml(etNoteContent.getText(), Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
        
        if (isNewNote && title.isEmpty() && etNoteContent.getText().toString().trim().isEmpty()) {
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
        data.put("content", contentHtml);
        data.put("type", noteType);
        data.put("colorCode", selectedColor);
        data.put("label", tags.isEmpty() ? "" : tags.get(0)); 
        data.put("tags", tags);
        data.put("folder", selectedFolder);
        data.put("isPinned", isPinned);
        data.put("isArchived", isArchived);
        data.put("isDeleted", isDeleted);
        data.put("imageUrl", imageUrls.isEmpty() ? null : imageUrls.get(0)); 
        data.put("imageUrls", imageUrls);
        data.put("updatedAt", System.currentTimeMillis());

        if (isNewNote) {
            data.put("createdAt", System.currentTimeMillis());
            isNewNote = false; 
        }

        originalTitle = title;
        originalContent = contentHtml;

        db.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId)
                .set(data, SetOptions.merge());
    }

    public String getRawContent() {
        return etNoteContent.getText().toString().trim();
    }

    public String getNoteTitle() {
        return etNoteTitle.getText().toString().trim();
    }
}
