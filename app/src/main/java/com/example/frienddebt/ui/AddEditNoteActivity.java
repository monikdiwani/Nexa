package com.example.frienddebt.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Note;
import com.example.frienddebt.utils.StatusBarUtil;
import com.example.frienddebt.utils.UndoRedoManager;
import com.example.frienddebt.ui.fragment.BottomSheetNoteOptionsFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddEditNoteActivity extends AppCompatActivity {

    private EditText etNoteTitle, etNoteContent;
    private ImageButton btnBack, btnMenu, btnPin, btnUndo, btnRedo;
    private RecyclerView rvImages;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private UndoRedoManager undoRedoManager;

    public String noteId = null;
    private boolean isNewNote = false;
    private String originalTitle = "";
    private String originalContent = ""; // Stores HTML
    
    // Properties managed by bottom sheets
    public String selectedColor = "#surface_primary"; 
    public String selectedFolder = "Personal";
    public List<String> tags = new ArrayList<>();
    public boolean isPinned = false;
    public boolean isArchived = false;
    public boolean isDeleted = false;
    public String noteType = "TEXT";

    private List<String> imageUrls = new ArrayList<>();
    private ImageAdapter imageAdapter;

    private Handler autoSaveHandler = new Handler();
    private Runnable autoSaveRunnable;
    private boolean hasSaved = false;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        String newImageUrl = imageUri.toString();
                        if (!imageUrls.contains(newImageUrl)) {
                            imageUrls.add(newImageUrl);
                            imageAdapter.notifyItemInserted(imageUrls.size() - 1);
                            rvImages.setVisibility(View.VISIBLE);
                            triggerAutoSave();
                        }
                    }
                }
            }
    );

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
        btnPin = findViewById(R.id.btnPin);
        btnMenu = findViewById(R.id.btnMenu);
        btnUndo = findViewById(R.id.btnUndo);
        btnRedo = findViewById(R.id.btnRedo);
        rvImages = findViewById(R.id.rvImages);

        imageAdapter = new ImageAdapter();
        rvImages.setAdapter(imageAdapter);

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

        updatePinIcon();

        btnPin.setOnClickListener(v -> {
            isPinned = !isPinned;
            updatePinIcon();
            triggerAutoSave();
        });

        btnMenu.setOnClickListener(v -> {
            BottomSheetNoteOptionsFragment bottomSheet = new BottomSheetNoteOptionsFragment();
            bottomSheet.show(getSupportFragmentManager(), "NoteOptions");
        });

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
        findViewById(R.id.btnFormatHeader).setOnClickListener(v -> toggleHeaderSpan());
        findViewById(R.id.btnFormatBullet).setOnClickListener(v -> toggleBulletSpan());
        findViewById(R.id.btnFormatTask).setOnClickListener(v -> insertCheckbox());

        findViewById(R.id.btnAddImage).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });
    }

    private void toggleStyleSpan(int style) {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        int end = etNoteContent.getSelectionEnd();
        if (start < 0 || end < 0 || start == end) return; // Only apply if text selected

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

    private void toggleHeaderSpan() {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        int end = etNoteContent.getSelectionEnd();
        if (start < 0 || end < 0) return;

        Editable editable = etNoteContent.getText();
        RelativeSizeSpan[] spans = editable.getSpans(start, end, RelativeSizeSpan.class);
        if (spans.length > 0) {
            for (RelativeSizeSpan span : spans) editable.removeSpan(span);
        } else {
            editable.setSpan(new RelativeSizeSpan(1.5f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            editable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        triggerAutoSave();
    }

    private void toggleBulletSpan() {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        Editable editable = etNoteContent.getText();
        if (start < 0) return;

        // Find paragraph start and end
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

    private void insertCheckbox() {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        Editable editable = etNoteContent.getText();
        if (start < 0) start = editable.length();

        // Insert a Unicode checkbox
        editable.insert(start, "\u2610 "); // Empty checkbox U+2610
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
                        } else if (note.getLabel() != null && !note.getLabel().isEmpty()) {
                            tags.add(note.getLabel()); 
                        }
                        
                        if (note.getImageUrls() != null && !note.getImageUrls().isEmpty()) {
                            imageUrls.clear();
                            imageUrls.addAll(note.getImageUrls());
                        } else if (note.getImageUrl() != null && !note.getImageUrl().isEmpty()) {
                            imageUrls.clear();
                            imageUrls.add(note.getImageUrl()); 
                        }

                        etNoteTitle.setText(originalTitle);
                        
                        // Parse HTML into Spannable
                        if (originalContent.contains("<") && originalContent.contains(">")) {
                            etNoteContent.setText(Html.fromHtml(originalContent, Html.FROM_HTML_MODE_LEGACY));
                        } else {
                            // Legacy plain text or markdown
                            etNoteContent.setText(originalContent);
                        }

                        applyColor(selectedColor);
                        updatePinIcon();
                        
                        if (!imageUrls.isEmpty()) {
                            rvImages.setVisibility(View.VISIBLE);
                            imageAdapter.notifyDataSetChanged();
                        } else {
                            rvImages.setVisibility(View.GONE);
                        }
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

    private void updatePinIcon() {
        if (isPinned) {
            btnPin.setColorFilter(getResources().getColor(R.color.primary));
        } else {
            btnPin.setColorFilter(getResources().getColor(R.color.text_secondary));
        }
    }

    private class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_image, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String uriStr = imageUrls.get(position);
            try {
                holder.img.setImageURI(Uri.parse(uriStr));
            } catch (Exception e) {
                e.printStackTrace();
            }
            holder.btnRemove.setOnClickListener(v -> {
                imageUrls.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, imageUrls.size());
                if (imageUrls.isEmpty()) {
                    rvImages.setVisibility(View.GONE);
                }
                triggerAutoSave();
            });
        }

        @Override
        public int getItemCount() {
            return imageUrls.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView img;
            ImageButton btnRemove;
            public ViewHolder(View itemView) {
                super(itemView);
                img = itemView.findViewById(R.id.imgAttachment);
                btnRemove = itemView.findViewById(R.id.btnRemoveImage);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNote(false);
    }

    public void saveNote(boolean force) {
        if (hasSaved && !force) return; 

        final String title = etNoteTitle.getText().toString().trim();
        
        // Convert Spannable to HTML for storage
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
        } else {
            if (!originalTitle.equals(title) || !originalContent.equals(contentHtml)) {
                Map<String, Object> historyData = new HashMap<>();
                historyData.put("title", originalTitle);
                historyData.put("content", originalContent);
                historyData.put("savedAt", System.currentTimeMillis());
                
                db.collection("users")
                        .document(userId)
                        .collection("notes")
                        .document(noteId)
                        .collection("history")
                        .add(historyData);
            }
        }

        originalTitle = title;
        originalContent = contentHtml;

        db.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                })
                .addOnFailureListener(e -> {
                    hasSaved = false; 
                });
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

    public String getRawContent() {
        return etNoteContent.getText().toString().trim();
    }

    public String getNoteTitle() {
        return etNoteTitle.getText().toString().trim();
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
