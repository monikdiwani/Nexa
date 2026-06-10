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

import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.widget.ImageView;

public class AddEditNoteActivity extends AppCompatActivity {

    private EditText etNoteTitle, etNoteContent;
    private ImageButton btnBack, btnSave;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private String noteId = null;
    private String originalTitle = "";
    private String originalContent = "";
    private String selectedColor = "#FFFFFF";
    private boolean isPinned = false;
    
    private android.widget.EditText etNoteLabel;
    private android.widget.Spinner spinnerFolder;
    private ImageButton btnPin, btnArchive, btnDelete;
    private android.widget.LinearLayout layoutColors;
    private androidx.recyclerview.widget.RecyclerView rvImages;

    private java.util.List<String> imageUrls = new java.util.ArrayList<>();
    private String selectedImageUrl = null;
    private boolean isArchived = false;
    private boolean isDeleted = false;
    private String noteType = "TEXT";

    private android.widget.LinearLayout layoutChecklist;
    private android.widget.LinearLayout containerChecklistItems;
    private android.widget.Button btnAddChecklistItem;
    private android.widget.Button btnLinkReminder, btnLinkTask, btnLinkCashbook;

    private ImageAdapter imageAdapter;

    private android.os.Handler autoSaveHandler = new android.os.Handler();
    private Runnable autoSaveRunnable;

    // Guard flag to prevent double-finish
    private boolean isFinishing = false;

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
                            rvImages.setVisibility(android.view.View.VISIBLE);
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
        etNoteLabel = findViewById(R.id.etNoteLabel);
        spinnerFolder = findViewById(R.id.spinnerFolder);
        btnBack = findViewById(R.id.btnBack);
        btnSave = findViewById(R.id.btnSave);
        btnPin = findViewById(R.id.btnPin);
        btnArchive = findViewById(R.id.btnArchive);
        btnDelete = findViewById(R.id.btnDelete);
        layoutColors = findViewById(R.id.layoutColors);
        rvImages = findViewById(R.id.rvImages);
        
        layoutChecklist = findViewById(R.id.layoutChecklist);
        containerChecklistItems = findViewById(R.id.containerChecklistItems);
        btnAddChecklistItem = findViewById(R.id.btnAddChecklistItem);
        
        btnLinkReminder = findViewById(R.id.btnLinkReminder);
        btnLinkTask = findViewById(R.id.btnLinkTask);
        btnLinkCashbook = findViewById(R.id.btnLinkCashbook);
        
        imageAdapter = new ImageAdapter();
        rvImages.setAdapter(imageAdapter);

        noteId = getIntent().getStringExtra("NOTE_ID");

        setupColorPicker();
        setupFolderSpinner();
        updatePinIcon();

        btnPin.setOnClickListener(v -> {
            isPinned = !isPinned;
            updatePinIcon();
            triggerAutoSave();
        });
        
        btnArchive.setOnClickListener(v -> {
            isArchived = !isArchived;
            updateArchiveIcon();
            saveNote(true);
            finish();
        });

        btnDelete.setOnClickListener(v -> {
            isDeleted = true;
            saveNote(true);
            finish();
        });

        setupAutoSaveListeners();

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

        btnAddChecklistItem.setOnClickListener(v -> {
            addChecklistItemUI("", false);
            triggerAutoSave();
        });
        
        btnLinkReminder.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddReminderActivity.class);
            intent.putExtra("LINKED_TITLE", etNoteTitle.getText().toString());
            intent.putExtra("LINKED_ID", noteId);
            intent.putExtra("LINKED_TYPE", "NOTE");
            startActivity(intent);
        });
        
        btnLinkTask.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddTaskActivity.class);
            intent.putExtra("LINKED_TITLE", etNoteTitle.getText().toString());
            intent.putExtra("LINKED_ID", noteId);
            intent.putExtra("LINKED_TYPE", "NOTE");
            startActivity(intent);
        });
        
        btnLinkCashbook.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateLedgerBookActivity.class);
            intent.putExtra("LINKED_TITLE", etNoteTitle.getText().toString());
            intent.putExtra("LINKED_ID", noteId);
            intent.putExtra("LINKED_TYPE", "NOTE");
            startActivity(intent);
        });
    }

    private void setupAutoSaveListeners() {
        android.text.TextWatcher textWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { triggerAutoSave(); }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        };
        etNoteTitle.addTextChangedListener(textWatcher);
        etNoteContent.addTextChangedListener(textWatcher);
        etNoteLabel.addTextChangedListener(textWatcher);
    }

    private void triggerAutoSave() {
        hasSaved = false; // reset flag
        if (autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
        }
        autoSaveRunnable = () -> saveNote(false);
        autoSaveHandler.postDelayed(autoSaveRunnable, 2000); // 2 seconds debounce
    }

    private void setupMarkdownToolbar() {
        findViewById(R.id.btnFormatBold).setOnClickListener(v -> insertMarkdown("**", "**"));
        findViewById(R.id.btnFormatItalic).setOnClickListener(v -> insertMarkdown("*", "*"));
        findViewById(R.id.btnFormatHeader).setOnClickListener(v -> insertMarkdownAtLineStart("### "));
        findViewById(R.id.btnFormatBullet).setOnClickListener(v -> insertMarkdownAtLineStart("- "));
        findViewById(R.id.btnFormatTask).setOnClickListener(v -> {
            toggleChecklistMode();
        });

        findViewById(R.id.btnAddImage).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });

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

    private void toggleChecklistMode() {
        if ("CHECKLIST".equals(noteType)) {
            // Convert to TEXT mode
            noteType = "TEXT";
            etNoteContent.setText(getChecklistContentAsMarkdown());
            layoutChecklist.setVisibility(android.view.View.GONE);
            etNoteContent.setVisibility(android.view.View.VISIBLE);
        } else {
            // Convert to CHECKLIST mode
            noteType = "CHECKLIST";
            parseMarkdownToChecklist(etNoteContent.getText().toString());
            etNoteContent.setVisibility(android.view.View.GONE);
            layoutChecklist.setVisibility(android.view.View.VISIBLE);
        }
        triggerAutoSave();
    }

    private String getChecklistContentAsMarkdown() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < containerChecklistItems.getChildCount(); i++) {
            android.view.View child = containerChecklistItems.getChildAt(i);
            android.widget.CheckBox cb = child.findViewById(R.id.cbChecklistItem);
            android.widget.EditText et = child.findViewById(R.id.etChecklistItem);
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
        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.item_checklist, containerChecklistItems, false);
        android.widget.CheckBox cb = view.findViewById(R.id.cbChecklistItem);
        android.widget.EditText et = view.findViewById(R.id.etChecklistItem);
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

        et.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { triggerAutoSave(); }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
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
                        
                        selectedColor = note.getColorCode() != null ? note.getColorCode() : "#FFFFFF";
                        isPinned = note.isPinned();
                        isArchived = note.isArchived();
                        isDeleted = note.isDeleted();
                        noteType = note.getType() != null ? note.getType() : "TEXT";
                        
                        String label = "";
                        if (note.getTags() != null && !note.getTags().isEmpty()) {
                            label = android.text.TextUtils.join(", ", note.getTags());
                        } else if (note.getLabel() != null) {
                            label = note.getLabel(); // Fallback for old data
                        }
                        
                        String folder = note.getFolder() != null ? note.getFolder() : "Personal";
                        setSpinnerSelection(folder);
                        
                        if (note.getImageUrls() != null && !note.getImageUrls().isEmpty()) {
                            imageUrls.clear();
                            imageUrls.addAll(note.getImageUrls());
                        } else if (note.getImageUrl() != null && !note.getImageUrl().isEmpty()) {
                            imageUrls.clear();
                            imageUrls.add(note.getImageUrl()); // Legacy support
                        }

                        etNoteTitle.setText(originalTitle);
                        etNoteLabel.setText(label);
                        
                        if ("CHECKLIST".equals(noteType)) {
                            parseMarkdownToChecklist(originalContent);
                            etNoteContent.setVisibility(android.view.View.GONE);
                            layoutChecklist.setVisibility(android.view.View.VISIBLE);
                        } else {
                            etNoteContent.setText(originalContent);
                            etNoteContent.setVisibility(android.view.View.VISIBLE);
                            layoutChecklist.setVisibility(android.view.View.GONE);
                        }

                        applyColor(selectedColor);
                        updatePinIcon();
                        updateArchiveIcon();
                        if (!imageUrls.isEmpty()) {
                            rvImages.setVisibility(android.view.View.VISIBLE);
                            imageAdapter.notifyDataSetChanged();
                        } else {
                            rvImages.setVisibility(android.view.View.GONE);
                        }
                    }
                });
    }

    private void setupFolderSpinner() {
        String[] folders = {"Personal", "Work", "Study", "Finance", "Ideas", "Shopping", "Travel"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, folders);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFolder.setAdapter(adapter);
        
        spinnerFolder.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                triggerAutoSave();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setSpinnerSelection(String folder) {
        android.widget.ArrayAdapter<String> adapter = (android.widget.ArrayAdapter<String>) spinnerFolder.getAdapter();
        if (adapter != null) {
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).equalsIgnoreCase(folder)) {
                    spinnerFolder.setSelection(i);
                    break;
                }
            }
        }
    }

    private void setupColorPicker() {
        String[] colors = {"#FFFFFF", "#FFCDD2", "#F8BBD0", "#E1BEE7", "#D1C4E9", "#C5CAE9", "#BBDEFB", "#B3E5FC", "#B2EBF2", "#B2DFDB", "#C8E6C9", "#DCEDC8", "#F0F4C3", "#FFF9C4", "#FFECB3", "#FFE0B2", "#FFCCBC"};
        
        for (String color : colors) {
            android.view.View colorView = new android.view.View(this);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(90, 90);
            params.setMargins(16, 16, 16, 16);
            colorView.setLayoutParams(params);
            
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(android.graphics.Color.parseColor(color));
            gd.setStroke(2, android.graphics.Color.parseColor("#CCCCCC"));
            colorView.setBackground(gd);
            
            colorView.setOnClickListener(v -> applyColor(color));
            
            layoutColors.addView(colorView);
        }
    }

    private void applyColor(String color) {
        selectedColor = color;
        findViewById(android.R.id.content).setBackgroundColor(android.graphics.Color.parseColor(color));
        triggerAutoSave();
    }

    private void updatePinIcon() {
        if (isPinned) {
            btnPin.setColorFilter(getResources().getColor(R.color.primary));
        } else {
            btnPin.setColorFilter(getResources().getColor(R.color.text_secondary));
        }
    }

    private void updateArchiveIcon() {
        if (isArchived) {
            btnArchive.setColorFilter(getResources().getColor(R.color.primary));
        } else {
            btnArchive.setColorFilter(getResources().getColor(R.color.text_secondary));
        }
    }

    private class ImageAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ImageAdapter.ViewHolder> {
        @androidx.annotation.NonNull
        @Override
        public ViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_image, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull ViewHolder holder, int position) {
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
                    rvImages.setVisibility(android.view.View.GONE);
                }
                triggerAutoSave();
            });
        }

        @Override
        public int getItemCount() {
            return imageUrls.size();
        }

        class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            ImageView img;
            ImageButton btnRemove;
            public ViewHolder(android.view.View itemView) {
                super(itemView);
                img = itemView.findViewById(R.id.imgAttachment);
                btnRemove = itemView.findViewById(R.id.btnRemoveImage);
            }
        }
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
        String contentRaw = "";
        if ("CHECKLIST".equals(noteType)) {
            contentRaw = getChecklistContentAsMarkdown();
        } else {
            contentRaw = etNoteContent.getText().toString().trim();
        }
        final String content = contentRaw;
        
        final String labelStr = etNoteLabel.getText().toString().trim();
        final String folder = spinnerFolder.getSelectedItem() != null ? spinnerFolder.getSelectedItem().toString() : "Personal";
        
        java.util.List<String> tags = new java.util.ArrayList<>();
        if (!labelStr.isEmpty()) {
            String[] splitTags = labelStr.split(",");
            for (String t : splitTags) {
                String cleanT = t.trim();
                if (!cleanT.isEmpty()) {
                    tags.add(cleanT);
                }
            }
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
        data.put("type", noteType);
        data.put("colorCode", selectedColor);
        data.put("label", tags.isEmpty() ? "" : tags.get(0)); // Legacy support
        data.put("tags", tags);
        data.put("folder", folder);
        data.put("isPinned", isPinned);
        data.put("isArchived", isArchived);
        data.put("isDeleted", isDeleted);
        data.put("imageUrl", imageUrls.isEmpty() ? null : imageUrls.get(0)); // Legacy support
        data.put("imageUrls", imageUrls);
        data.put("updatedAt", System.currentTimeMillis());

        if (noteId == null) {
            noteId = db.collection("users")
                    .document(userId)
                    .collection("notes")
                    .document()
                    .getId();
            data.put("createdAt", System.currentTimeMillis());
            // type and folder are already set above
        }

        originalTitle = title;
        originalContent = content;

        db.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
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
