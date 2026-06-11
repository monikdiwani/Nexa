package com.example.frienddebt.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Note;
import com.example.frienddebt.utils.StatusBarUtil;
import com.example.frienddebt.ui.fragment.BottomSheetNoteOptionsFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tasklist.TaskListPlugin;

public class AddEditNoteActivity extends AppCompatActivity {

    private EditText etNoteTitle, etNoteContent;
    private ImageButton btnBack, btnMenu, btnPin;
    private RecyclerView rvImages;
    private LinearLayout layoutChecklist, containerChecklistItems;
    private Button btnAddChecklistItem;
    private TextView txtMarkdownPreview;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    public String noteId = null;
    private boolean isNewNote = false;
    private String originalTitle = "";
    private String originalContent = "";
    
    // Properties managed by bottom sheets
    public String selectedColor = "#surface_primary"; // default maps to theme surface
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
        rvImages = findViewById(R.id.rvImages);
        layoutChecklist = findViewById(R.id.layoutChecklist);
        containerChecklistItems = findViewById(R.id.containerChecklistItems);
        btnAddChecklistItem = findViewById(R.id.btnAddChecklistItem);
        txtMarkdownPreview = findViewById(R.id.txtMarkdownPreview);

        imageAdapter = new ImageAdapter();
        rvImages.setAdapter(imageAdapter);

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

        setupMarkdownToolbar();

        btnAddChecklistItem.setOnClickListener(v -> {
            addChecklistItemUI("", false);
            triggerAutoSave();
        });
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
        hasSaved = false; // Reset flag to allow saving
        if (autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
        }
        autoSaveRunnable = () -> saveNote(false);
        autoSaveHandler.postDelayed(autoSaveRunnable, 2000); // 2-second debounce
    }

    private void setupMarkdownToolbar() {
        findViewById(R.id.btnFormatBold).setOnClickListener(v -> insertMarkdown("**", "**"));
        findViewById(R.id.btnFormatItalic).setOnClickListener(v -> insertMarkdown("*", "*"));
        findViewById(R.id.btnFormatHeader).setOnClickListener(v -> insertMarkdownAtLineStart("### "));
        findViewById(R.id.btnFormatBullet).setOnClickListener(v -> insertMarkdownAtLineStart("- "));
        findViewById(R.id.btnFormatTask).setOnClickListener(v -> toggleChecklistMode());

        findViewById(R.id.btnAddImage).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });

        Button btnTogglePreview = findViewById(R.id.btnTogglePreview);

        btnTogglePreview.setOnClickListener(v -> {
            if (etNoteContent.getVisibility() == View.VISIBLE) {
                // Switch to Preview
                etNoteContent.setVisibility(View.GONE);
                txtMarkdownPreview.setVisibility(View.VISIBLE);
                btnTogglePreview.setText("Edit");
                
                // Render Markdown
                Markwon markwon = Markwon.builder(this)
                    .usePlugin(TaskListPlugin.create(this))
                    .build();
                markwon.setMarkdown(txtMarkdownPreview, etNoteContent.getText().toString());
            } else {
                // Switch to Edit
                etNoteContent.setVisibility(View.VISIBLE);
                txtMarkdownPreview.setVisibility(View.GONE);
                btnTogglePreview.setText("Preview");
            }
        });
    }

    private void insertMarkdown(String prefix, String suffix) {
        int start = etNoteContent.getSelectionStart();
        int end = etNoteContent.getSelectionEnd();
        
        Editable editable = etNoteContent.getText();
        if (editable == null) return;

        if (start < 0 || end < 0) {
            start = editable.length();
            end = editable.length();
        }

        if (start == end) {
            editable.insert(start, prefix + suffix);
            etNoteContent.setSelection(start + prefix.length());
        } else {
            editable.insert(start, prefix);
            editable.insert(end + prefix.length(), suffix);
            etNoteContent.setSelection(end + prefix.length() + suffix.length());
        }
    }

    private void insertMarkdownAtLineStart(String prefix) {
        int start = etNoteContent.getSelectionStart();
        Editable editable = etNoteContent.getText();
        if (editable == null) return;

        if (start < 0) start = editable.length();

        String text = editable.toString();
        int lineStart = start;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        editable.insert(lineStart, prefix);
        etNoteContent.setSelection(start + prefix.length());
    }

    private void toggleChecklistMode() {
        if ("CHECKLIST".equals(noteType)) {
            noteType = "TEXT";
            etNoteContent.setText(getChecklistContentAsMarkdown());
            layoutChecklist.setVisibility(View.GONE);
            etNoteContent.setVisibility(View.VISIBLE);
        } else {
            noteType = "CHECKLIST";
            parseMarkdownToChecklist(etNoteContent.getText().toString());
            etNoteContent.setVisibility(View.GONE);
            layoutChecklist.setVisibility(View.VISIBLE);
        }
        triggerAutoSave();
    }

    public String getChecklistContentAsMarkdown() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < containerChecklistItems.getChildCount(); i++) {
            View child = containerChecklistItems.getChildAt(i);
            CheckBox cb = child.findViewById(R.id.cbChecklistItem);
            EditText et = child.findViewById(R.id.etChecklistItem);
            String text = et.getText().toString().trim();
            if (!text.isEmpty() || cb.isChecked()) {
                sb.append(cb.isChecked() ? "- [x] " : "- [ ] ").append(text).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private void parseMarkdownToChecklist(String content) {
        containerChecklistItems.removeAllViews();
        if (content == null || content.trim().isEmpty()) {
            addChecklistItemUI("", false);
            return;
        }

        String[] lines = content.split("\n");
        boolean hasItems = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")) {
                addChecklistItemUI(trimmed.substring(6), true);
                hasItems = true;
            } else if (trimmed.startsWith("- [ ] ")) {
                addChecklistItemUI(trimmed.substring(6), false);
                hasItems = true;
            } else if (!trimmed.isEmpty()) {
                addChecklistItemUI(trimmed, false);
                hasItems = true;
            }
        }
        if (!hasItems) {
            addChecklistItemUI("", false);
        }
    }

    private void addChecklistItemUI(String text, boolean isChecked) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_checklist, containerChecklistItems, false);
        CheckBox cb = view.findViewById(R.id.cbChecklistItem);
        EditText et = view.findViewById(R.id.etChecklistItem);
        ImageButton btnRemove = view.findViewById(R.id.btnRemoveChecklistItem);

        cb.setChecked(isChecked);
        et.setText(text);
        
        if (isChecked) {
            et.setPaintFlags(et.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        }

        cb.setOnCheckedChangeListener((buttonView, isCbChecked) -> {
            if (isCbChecked) {
                et.setPaintFlags(et.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                et.setPaintFlags(et.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
            }
            triggerAutoSave();
        });

        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { triggerAutoSave(); }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnRemove.setOnClickListener(v -> {
            containerChecklistItems.removeView(view);
            triggerAutoSave();
        });

        containerChecklistItems.addView(view);
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
                            tags.add(note.getLabel()); // Legacy support
                        }
                        
                        if (note.getImageUrls() != null && !note.getImageUrls().isEmpty()) {
                            imageUrls.clear();
                            imageUrls.addAll(note.getImageUrls());
                        } else if (note.getImageUrl() != null && !note.getImageUrl().isEmpty()) {
                            imageUrls.clear();
                            imageUrls.add(note.getImageUrl()); // Legacy support
                        }

                        etNoteTitle.setText(originalTitle);
                        
                        if ("CHECKLIST".equals(noteType)) {
                            parseMarkdownToChecklist(originalContent);
                            etNoteContent.setVisibility(View.GONE);
                            layoutChecklist.setVisibility(View.VISIBLE);
                        } else {
                            etNoteContent.setText(originalContent);
                            etNoteContent.setVisibility(View.VISIBLE);
                            layoutChecklist.setVisibility(View.GONE);
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
            // Revert to theme default
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
        // Safety save on pause
        saveNote(false);
    }

    public void saveNote(boolean force) {
        if (hasSaved && !force) return; // Prevent redundant saves

        final String title = etNoteTitle.getText().toString().trim();
        String contentRaw = "";
        if ("CHECKLIST".equals(noteType)) {
            contentRaw = getChecklistContentAsMarkdown();
        } else {
            contentRaw = etNoteContent.getText().toString().trim();
        }
        final String content = contentRaw;
        
        if (isNewNote && title.isEmpty() && content.isEmpty()) {
            hasSaved = true;
            return;
        }

        if (auth.getCurrentUser() == null) {
            hasSaved = true;
            return;
        }

        hasSaved = true; // Mark as saved so concurrent triggers don't duplicate work
        String userId = auth.getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("content", content);
        data.put("type", noteType);
        data.put("colorCode", selectedColor);
        data.put("label", tags.isEmpty() ? "" : tags.get(0)); // Legacy support
        data.put("tags", tags);
        data.put("folder", selectedFolder);
        data.put("isPinned", isPinned);
        data.put("isArchived", isArchived);
        data.put("isDeleted", isDeleted);
        data.put("imageUrl", imageUrls.isEmpty() ? null : imageUrls.get(0)); // Legacy support
        data.put("imageUrls", imageUrls);
        data.put("updatedAt", System.currentTimeMillis());

        if (isNewNote) {
            data.put("createdAt", System.currentTimeMillis());
            isNewNote = false; 
        } else {
            // Version History
            if (!originalTitle.equals(title) || !originalContent.equals(content)) {
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
        originalContent = content;

        db.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    // Saved silently
                })
                .addOnFailureListener(e -> {
                    hasSaved = false; // Allow retry
                    Toast.makeText(this, "Save failed silently", Toast.LENGTH_SHORT).show();
                });
    }

    public void updateContentFromHistory(String oldTitle, String oldContent) {
        if (oldTitle != null) etNoteTitle.setText(oldTitle);
        if (oldContent != null) {
            if ("CHECKLIST".equals(noteType)) {
                parseMarkdownToChecklist(oldContent);
            } else {
                etNoteContent.setText(oldContent);
            }
        }
        triggerAutoSave();
    }

    public String getRawContent() {
        if ("CHECKLIST".equals(noteType)) {
            return getChecklistContentAsMarkdown();
        } else {
            return etNoteContent.getText().toString().trim();
        }
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
