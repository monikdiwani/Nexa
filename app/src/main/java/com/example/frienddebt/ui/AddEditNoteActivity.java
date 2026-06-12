package com.example.frienddebt.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.AlignmentSpan;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Note;
import com.example.frienddebt.ui.fragment.BottomSheetConvertCashbookFragment;
import com.example.frienddebt.ui.fragment.BottomSheetConvertReminderFragment;
import com.example.frienddebt.ui.fragment.BottomSheetConvertTaskFragment;
import com.example.frienddebt.ui.fragment.NoteStyleBottomSheet;
import com.example.frienddebt.utils.StatusBarUtil;
import com.example.frienddebt.utils.UndoRedoManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class AddEditNoteActivity extends AppCompatActivity {

    // UI refs
    private EditText etNoteTitle, etNoteContent;
    private ImageButton btnBack, btnMenu, btnUndo, btnRedo, btnReadMode, btnAddAttachment;
    private LinearLayout layoutTopBar, layoutDrawingBar, layoutChecklist;
    private LinearLayout containerChecklistItems;
    private Button btnAddChecklistItem;
    private RecyclerView rvImages;
    private View editorRoot;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private UndoRedoManager undoRedoManager;

    // Note state
    public String noteId = null;
    private boolean isNewNote = false;
    private String originalTitle = "";
    private String originalContent = "";
    public String selectedColor = null;
    public String selectedFolder = "Personal";
    public List<String> tags = new ArrayList<>();
    public boolean isPinned = false;
    public boolean isArchived = false;
    public boolean isDeleted = false;
    public boolean isLocked = false;
    public String noteType = "TEXT";
    public String pageStyle = "blank";
    private List<String> imageUrls = new ArrayList<>();
    private ImageAdapter imageAdapter;

    // Auto-save
    private final Handler autoSaveHandler = new Handler();
    private Runnable autoSaveRunnable;
    private boolean hasSaved = false;

    // Full-screen mode
    private boolean isFullScreen = false;

    // Checklist items
    private List<ChecklistItem> checklistItems = new ArrayList<>();

    private static class ChecklistItem {
        String text;
        boolean checked;
        ChecklistItem(String text, boolean checked) { this.text = text; this.checked = checked; }
    }

    // ─── Image picker launcher ────────────────────────────────────────────────

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                        catch (Exception ignored) {}
                        String uriStr = uri.toString();
                        if (!imageUrls.contains(uriStr)) {
                            imageUrls.add(uriStr);
                            imageAdapter.notifyItemInserted(imageUrls.size() - 1);
                            rvImages.setVisibility(View.VISIBLE);
                            triggerAutoSave();
                        }
                    }
                }
            });

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_note);
        StatusBarUtil.applyStatusBarPadding(this);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        etNoteTitle       = findViewById(R.id.etNoteTitle);
        etNoteContent     = findViewById(R.id.etNoteContent);
        btnBack           = findViewById(R.id.btnBack);
        btnMenu           = findViewById(R.id.btnMenu);
        btnUndo           = findViewById(R.id.btnUndo);
        btnRedo           = findViewById(R.id.btnRedo);
        btnReadMode       = findViewById(R.id.btnReadMode);
        btnAddAttachment  = findViewById(R.id.btnAddAttachment);
        layoutTopBar      = findViewById(R.id.layoutTopBar);
        layoutDrawingBar  = findViewById(R.id.layoutDrawingBar);
        layoutChecklist   = findViewById(R.id.layoutChecklist);
        containerChecklistItems = findViewById(R.id.containerChecklistItems);
        btnAddChecklistItem = findViewById(R.id.btnAddChecklistItem);
        rvImages          = findViewById(R.id.rvImages);
        editorRoot        = findViewById(R.id.editorRoot);

        imageAdapter = new ImageAdapter();
        rvImages.setAdapter(imageAdapter);

        undoRedoManager = new UndoRedoManager(etNoteContent);

        noteId = getIntent().getStringExtra("NOTE_ID");
        if (noteId == null && auth.getCurrentUser() != null) {
            noteId = db.collection("users")
                    .document(auth.getCurrentUser().getUid())
                    .collection("notes").document().getId();
            isNewNote = true;
        }

        if (noteId != null && !isNewNote) loadNoteDetails();

        setupButtons();
        setupFormattingToolbar();
        setupAutoSave();
        setupChecklist();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNote(false);
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private void setupButtons() {
        btnBack.setOnClickListener(v -> { saveNote(false); finish(); });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { saveNote(false); finish(); }
        });

        btnMenu.setOnClickListener(this::showSamsungMenu);

        btnReadMode.setOnClickListener(v -> toggleFullScreen());

        btnAddAttachment.setOnClickListener(v -> showAttachmentSheet());

        if (btnUndo != null) btnUndo.setOnClickListener(v -> undoRedoManager.undo());
        if (btnRedo != null) btnRedo.setOnClickListener(v -> undoRedoManager.redo());
    }

    private void setupAutoSave() {
        TextWatcher tw = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { triggerAutoSave(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etNoteTitle.addTextChangedListener(tw);
        etNoteContent.addTextChangedListener(tw);
    }

    private void setupFormattingToolbar() {
        findViewById(R.id.btnFormatBold).setOnClickListener(v -> toggleStyleSpan(Typeface.BOLD));
        findViewById(R.id.btnFormatItalic).setOnClickListener(v -> toggleStyleSpan(Typeface.ITALIC));
        findViewById(R.id.btnFormatUnderline).setOnClickListener(v -> toggleUnderlineSpan());
        findViewById(R.id.btnFormatStrikethrough).setOnClickListener(v -> toggleStrikethroughSpan());
        findViewById(R.id.btnFormatHeader).setOnClickListener(v -> toggleHeaderSpan());
        findViewById(R.id.btnFormatBullet).setOnClickListener(v -> toggleBulletSpan());
        findViewById(R.id.btnFormatTask).setOnClickListener(v -> switchToChecklistMode());
        findViewById(R.id.btnFormatAlignLeft).setOnClickListener(v -> toggleAlignment(Layout.Alignment.ALIGN_NORMAL));
        findViewById(R.id.btnFormatAlignCenter).setOnClickListener(v -> toggleAlignment(Layout.Alignment.ALIGN_CENTER));
        findViewById(R.id.btnFormatAlignRight).setOnClickListener(v -> toggleAlignment(Layout.Alignment.ALIGN_OPPOSITE));
        findViewById(R.id.btnFormatListNumber).setOnClickListener(v -> insertNumberedListItem());
        findViewById(R.id.btnAddImage).setOnClickListener(v -> openGallery());
    }

    private void setupChecklist() {
        if (btnAddChecklistItem != null) {
            btnAddChecklistItem.setOnClickListener(v -> addChecklistRow("", false));
        }
    }

    // ─── Samsung Popup Menu ───────────────────────────────────────────────────

    private void showSamsungMenu(View anchor) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.layout_samsung_note_menu, null);
        PopupWindow popup = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(16f);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

        popupView.findViewById(R.id.menuSearch).setOnClickListener(v -> {
            popup.dismiss();
            Toast.makeText(this, "In-note search coming soon", Toast.LENGTH_SHORT).show();
        });
        popupView.findViewById(R.id.menuNoteStyle).setOnClickListener(v -> {
            popup.dismiss();
            new NoteStyleBottomSheet().show(getSupportFragmentManager(), "NoteStyle");
        });
        popupView.findViewById(R.id.menuPageSettings).setOnClickListener(v -> {
            popup.dismiss();
            Toast.makeText(this, "Page settings", Toast.LENGTH_SHORT).show();
        });
        popupView.findViewById(R.id.menuFullScreen).setOnClickListener(v -> {
            popup.dismiss();
            toggleFullScreen();
        });
        popupView.findViewById(R.id.menuAddTags).setOnClickListener(v -> {
            popup.dismiss();
            showAddTagsDialog();
        });
        popupView.findViewById(R.id.menuVersionHistory).setOnClickListener(v -> {
            popup.dismiss();
            showVersionHistory();
        });
        popupView.findViewById(R.id.menuShare).setOnClickListener(v -> {
            popup.dismiss();
            shareNote();
        });
        popupView.findViewById(R.id.menuArchive).setOnClickListener(v -> {
            popup.dismiss();
            isArchived = !isArchived;
            saveNote(true);
            finish();
        });
        popupView.findViewById(R.id.menuTrash).setOnClickListener(v -> {
            popup.dismiss();
            isDeleted = true;
            saveNote(true);
            finish();
        });
        popupView.findViewById(R.id.menuStar).setOnClickListener(v -> {
            popup.dismiss();
            isPinned = !isPinned;
            triggerAutoSave();
            Toast.makeText(this, isPinned ? "Pinned" : "Unpinned", Toast.LENGTH_SHORT).show();
        });
        popupView.findViewById(R.id.menuLock).setOnClickListener(v -> {
            popup.dismiss();
            toggleNoteLock();
        });
        popupView.findViewById(R.id.menuConvert).setOnClickListener(v -> {
            popup.dismiss();
            new BottomSheetConvertTaskFragment().show(getSupportFragmentManager(), "Convert");
        });

        popup.showAsDropDown(anchor, 0, 8);
    }

    // ─── Full Screen Mode ─────────────────────────────────────────────────────

    private void toggleFullScreen() {
        isFullScreen = !isFullScreen;
        if (isFullScreen) {
            if (layoutTopBar != null) layoutTopBar.setVisibility(View.GONE);
            if (layoutDrawingBar != null) layoutDrawingBar.setVisibility(View.GONE);
            View formBar = findViewById(R.id.layoutFormattingBar);
            if (formBar != null) formBar.setVisibility(View.GONE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            Toast.makeText(this, "Tap back to exit full screen", Toast.LENGTH_SHORT).show();
        } else {
            if (layoutTopBar != null) layoutTopBar.setVisibility(View.VISIBLE);
            if (layoutDrawingBar != null) layoutDrawingBar.setVisibility(View.VISIBLE);
            View formBar = findViewById(R.id.layoutFormattingBar);
            if (formBar != null) formBar.setVisibility(View.VISIBLE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    // ─── Note Lock (Biometric) ────────────────────────────────────────────────

    private void toggleNoteLock() {
        BiometricManager bm = BiometricManager.from(this);
        if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Biometric not available", Toast.LENGTH_SHORT).show();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        isLocked = !isLocked;
                        saveNote(true);
                        Toast.makeText(AddEditNoteActivity.this,
                                isLocked ? "Note locked" : "Note unlocked", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence msg) {
                        Toast.makeText(AddEditNoteActivity.this, "Auth error: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(isLocked ? "Unlock Note" : "Lock Note")
                .setSubtitle("Confirm your identity")
                .setNegativeButtonText("Cancel")
                .build();

        prompt.authenticate(info);
    }

    private void showLockedNote() {
        BiometricManager bm = BiometricManager.from(this);
        if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Note is locked", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        // Unlock succeeded — note already loaded, just show UI
                        etNoteContent.setVisibility(View.VISIBLE);
                        etNoteTitle.setEnabled(true);
                    }
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence msg) {
                        finish();
                    }
                    @Override
                    public void onAuthenticationFailed() {
                        finish();
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Note")
                .setSubtitle("Authenticate to view this note")
                .setNegativeButtonText("Cancel")
                .build();

        prompt.authenticate(info);
    }

    // ─── Attachment Sheet ─────────────────────────────────────────────────────

    private void showAttachmentSheet() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Add attachment")
                .setItems(new String[]{"Photo from Gallery", "Camera", "Audio Recording"},
                        (dialog, which) -> {
                            if (which == 0) openGallery();
                            else if (which == 1) Toast.makeText(this, "Camera coming soon", Toast.LENGTH_SHORT).show();
                            else Toast.makeText(this, "Audio coming soon", Toast.LENGTH_SHORT).show();
                        })
                .show();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    // ─── Tags Dialog ──────────────────────────────────────────────────────────

    private void showAddTagsDialog() {
        final EditText et = new EditText(this);
        et.setHint("tag1, tag2, tag3");
        if (!tags.isEmpty()) et.setText(String.join(", ", tags));
        et.setTextColor(Color.WHITE);
        et.setPadding(48, 24, 48, 24);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Add tags")
                .setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    tags.clear();
                    String[] parts = et.getText().toString().split(",");
                    for (String p : parts) {
                        String t = p.trim();
                        if (!t.isEmpty()) tags.add(t);
                    }
                    triggerAutoSave();
                    Toast.makeText(this, "Tags saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Share ────────────────────────────────────────────────────────────────

    private void shareNote() {
        String title = etNoteTitle.getText().toString().trim();
        String content = Html.fromHtml(Html.toHtml(etNoteContent.getText(),
                Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE), Html.FROM_HTML_MODE_LEGACY).toString().trim();
        String shareText = title.isEmpty() ? content : title + "\n\n" + content;
        if (shareText.trim().isEmpty()) { Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show(); return; }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "Share note via"));
    }

    // ─── Version History ──────────────────────────────────────────────────────

    private void showVersionHistory() {
        if (noteId == null || auth.getCurrentUser() == null) return;
        db.collection("users").document(auth.getCurrentUser().getUid())
                .collection("notes").document(noteId).collection("history")
                .orderBy("savedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) { Toast.makeText(this, "No history yet", Toast.LENGTH_SHORT).show(); return; }
                    String[] items = new String[snap.size()];
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault());
                    java.util.List<com.google.firebase.firestore.DocumentSnapshot> docs = snap.getDocuments();
                    for (int i = 0; i < docs.size(); i++) {
                        Long t = docs.get(i).getLong("savedAt");
                        items[i] = t != null ? sdf.format(new java.util.Date(t)) : "Unknown";
                    }
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Version History")
                            .setItems(items, (d, w) -> {
                                updateContentFromHistory(docs.get(w).getString("title"), docs.get(w).getString("content"));
                            })
                            .setNegativeButton("Close", null)
                            .show();
                });
    }

    // ─── Note Style (applyColor / applyPageStyle) ─────────────────────────────

    public void applyColor(String colorHex) {
        selectedColor = colorHex;
        if (colorHex == null) {
            editorRoot.setBackgroundColor(getResources().getColor(R.color.background_primary));
        } else {
            editorRoot.setBackgroundColor(Color.parseColor(colorHex));
        }
        triggerAutoSave();
    }

    public void applyPageStyle(String style) {
        pageStyle = style;
        View contentArea = etNoteContent;
        switch (style) {
            case "lined": contentArea.setBackground(getResources().getDrawable(R.drawable.bg_note_lined)); break;
            case "grid":  contentArea.setBackground(getResources().getDrawable(R.drawable.bg_note_grid));  break;
            default:      contentArea.setBackground(null); break;
        }
        triggerAutoSave();
    }

    // ─── Formatting helpers ───────────────────────────────────────────────────

    private void toggleStyleSpan(int style) {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart(), end = etNoteContent.getSelectionEnd();
        if (start < 0 || end < 0 || start == end) return;
        Editable e = etNoteContent.getText();
        StyleSpan[] spans = e.getSpans(start, end, StyleSpan.class);
        boolean exists = false;
        for (StyleSpan s : spans) if (s.getStyle() == style) { e.removeSpan(s); exists = true; }
        if (!exists) e.setSpan(new StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        triggerAutoSave();
    }

    private void toggleUnderlineSpan() {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart(), end = etNoteContent.getSelectionEnd();
        if (start < 0 || end < 0 || start == end) return;
        Editable e = etNoteContent.getText();
        UnderlineSpan[] spans = e.getSpans(start, end, UnderlineSpan.class);
        if (spans.length > 0) for (UnderlineSpan s : spans) e.removeSpan(s);
        else e.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        triggerAutoSave();
    }

    private void toggleStrikethroughSpan() {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart(), end = etNoteContent.getSelectionEnd();
        if (start < 0 || end < 0 || start == end) return;
        Editable e = etNoteContent.getText();
        StrikethroughSpan[] spans = e.getSpans(start, end, StrikethroughSpan.class);
        if (spans.length > 0) for (StrikethroughSpan s : spans) e.removeSpan(s);
        else e.setSpan(new StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        triggerAutoSave();
    }

    private void toggleHeaderSpan() {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart(), end = etNoteContent.getSelectionEnd();
        if (start < 0 || end < 0) return;
        Editable e = etNoteContent.getText();
        RelativeSizeSpan[] spans = e.getSpans(start, end, RelativeSizeSpan.class);
        if (spans.length > 0) { for (RelativeSizeSpan s : spans) e.removeSpan(s); }
        else {
            e.setSpan(new RelativeSizeSpan(1.5f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            e.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        triggerAutoSave();
    }

    private void toggleBulletSpan() {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        Editable e = etNoteContent.getText();
        if (start < 0) return;
        String text = e.toString();
        int lineStart = start; while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        int lineEnd = start; while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;
        BulletSpan[] spans = e.getSpans(lineStart, lineEnd, BulletSpan.class);
        if (spans.length > 0) for (BulletSpan s : spans) e.removeSpan(s);
        else e.setSpan(new BulletSpan(40), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        triggerAutoSave();
    }

    private void toggleAlignment(Layout.Alignment alignment) {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        Editable e = etNoteContent.getText();
        if (start < 0) return;
        String text = e.toString();
        int lineStart = start; while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        int lineEnd = start; while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;
        for (AlignmentSpan s : e.getSpans(lineStart, lineEnd, AlignmentSpan.class)) e.removeSpan(s);
        e.setSpan(new AlignmentSpan.Standard(alignment), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        triggerAutoSave();
    }

    private void insertNumberedListItem() {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        Editable e = etNoteContent.getText();
        if (start < 0) start = e.length();
        // Count existing numbered items on previous lines
        String before = e.toString().substring(0, start);
        int count = 0;
        for (String line : before.split("\n")) {
            if (line.matches("^\\d+\\..*")) count++;
        }
        e.insert(start, (count + 1) + ". ");
        triggerAutoSave();
    }

    // ─── Checklist mode ───────────────────────────────────────────────────────

    private void switchToChecklistMode() {
        if (noteType.equals("CHECKLIST")) {
            // Switch back to text
            noteType = "TEXT";
            etNoteContent.setVisibility(View.VISIBLE);
            layoutChecklist.setVisibility(View.GONE);
        } else {
            noteType = "CHECKLIST";
            etNoteContent.setVisibility(View.GONE);
            layoutChecklist.setVisibility(View.VISIBLE);
            if (checklistItems.isEmpty()) addChecklistRow("", false);
        }
    }

    private void addChecklistRow(String text, boolean checked) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_checklist_row, containerChecklistItems, false);
        CheckBox cb = row.findViewById(R.id.checkboxItem);
        EditText et = row.findViewById(R.id.etChecklistItem);
        cb.setChecked(checked);
        et.setText(text);
        if (checked) et.setPaintFlags(et.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        cb.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) et.setPaintFlags(et.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            else et.setPaintFlags(et.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            triggerAutoSave();
        });
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { triggerAutoSave(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        containerChecklistItems.addView(row);
        checklistItems.add(new ChecklistItem(text, checked));
    }

    private String serializeChecklist() {
        org.json.JSONArray arr = new org.json.JSONArray();
        int childCount = containerChecklistItems.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View row = containerChecklistItems.getChildAt(i);
            CheckBox cb = row.findViewById(R.id.checkboxItem);
            EditText et = row.findViewById(R.id.etChecklistItem);
            org.json.JSONObject obj = new org.json.JSONObject();
            try {
                obj.put("text", et.getText().toString());
                obj.put("checked", cb.isChecked());
            } catch (org.json.JSONException ignored) {}
            arr.put(obj);
        }
        return arr.toString();
    }

    private void deserializeChecklist(String json) {
        containerChecklistItems.removeAllViews();
        checklistItems.clear();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                addChecklistRow(obj.optString("text", ""), obj.optBoolean("checked", false));
            }
        } catch (Exception ignored) {
            addChecklistRow("", false);
        }
    }

    // ─── Load Note ────────────────────────────────────────────────────────────

    private void loadNoteDetails() {
        if (auth.getCurrentUser() == null) return;
        db.collection("users").document(auth.getCurrentUser().getUid())
                .collection("notes").document(noteId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    Note note = Note.fromDocument(doc);
                    originalTitle = note.getTitle() != null ? note.getTitle() : "";
                    originalContent = note.getContent() != null ? note.getContent() : "";
                    selectedColor = note.getColorCode() != null && !note.getColorCode().equals("#FFFFFF") ? note.getColorCode() : null;
                    isPinned = note.isPinned();
                    isArchived = note.isArchived();
                    isDeleted = note.isDeleted();
                    isLocked = note.isLocked();
                    noteType = note.getType() != null ? note.getType() : "TEXT";
                    selectedFolder = note.getFolder() != null ? note.getFolder() : "Personal";
                    pageStyle = note.getPageStyle() != null ? note.getPageStyle() : "blank";
                    tags.clear();
                    if (note.getTags() != null) tags.addAll(note.getTags());
                    if (note.getImageUrls() != null && !note.getImageUrls().isEmpty()) {
                        imageUrls.clear(); imageUrls.addAll(note.getImageUrls());
                        rvImages.setVisibility(View.VISIBLE); imageAdapter.notifyDataSetChanged();
                    }

                    etNoteTitle.setText(originalTitle);

                    if (isLocked) {
                        // Hide content and require biometric
                        etNoteContent.setText("🔒 Locked");
                        etNoteContent.setEnabled(false);
                        showLockedNote();
                        return;
                    }

                    if (noteType.equals("CHECKLIST") && originalContent.startsWith("[")) {
                        noteType = "CHECKLIST";
                        etNoteContent.setVisibility(View.GONE);
                        layoutChecklist.setVisibility(View.VISIBLE);
                        deserializeChecklist(originalContent);
                    } else {
                        if (originalContent.startsWith("<") && originalContent.contains(">")) {
                            etNoteContent.setText(Html.fromHtml(originalContent, Html.FROM_HTML_MODE_LEGACY));
                        } else {
                            etNoteContent.setText(originalContent);
                        }
                    }

                    applyColor(selectedColor);
                    applyPageStyle(pageStyle);
                });
    }

    // ─── Save ────────────────────────────────────────────────────────────────

    private void triggerAutoSave() {
        hasSaved = false;
        if (autoSaveRunnable != null) autoSaveHandler.removeCallbacks(autoSaveRunnable);
        autoSaveRunnable = () -> saveNote(false);
        autoSaveHandler.postDelayed(autoSaveRunnable, 2000);
    }

    public void saveNote(boolean force) {
        if (hasSaved && !force) return;

        String title = etNoteTitle.getText().toString().trim();
        String contentHtml;

        if (noteType.equals("CHECKLIST")) {
            contentHtml = serializeChecklist();
        } else {
            contentHtml = Html.toHtml(etNoteContent.getText(), Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
        }

        if (isNewNote && title.isEmpty() && (etNoteContent.getText().toString().trim().isEmpty())
                && checklistItems.isEmpty()) {
            hasSaved = true; return;
        }
        if (auth.getCurrentUser() == null) { hasSaved = true; return; }

        hasSaved = true;
        String userId = auth.getCurrentUser().getUid();

        // Save history snapshot before overwriting
        if (!isNewNote && (!originalTitle.equals(title) || !originalContent.equals(contentHtml))) {
            Map<String, Object> hist = new HashMap<>();
            hist.put("title", originalTitle);
            hist.put("content", originalContent);
            hist.put("savedAt", System.currentTimeMillis());
            db.collection("users").document(userId).collection("notes").document(noteId)
                    .collection("history").add(hist);
        }

        originalTitle = title;
        originalContent = contentHtml;

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("content", contentHtml);
        data.put("type", noteType);
        data.put("colorCode", selectedColor != null ? selectedColor : "#1C1C1E");
        data.put("pageStyle", pageStyle);
        data.put("label", tags.isEmpty() ? "" : tags.get(0));
        data.put("tags", tags);
        data.put("folder", selectedFolder);
        data.put("isPinned", isPinned);
        data.put("isArchived", isArchived);
        data.put("isDeleted", isDeleted);
        data.put("isLocked", isLocked);
        data.put("imageUrl", imageUrls.isEmpty() ? null : imageUrls.get(0));
        data.put("imageUrls", imageUrls);
        data.put("updatedAt", System.currentTimeMillis());
        if (isNewNote) { data.put("createdAt", System.currentTimeMillis()); isNewNote = false; }

        db.collection("users").document(userId).collection("notes").document(noteId)
                .set(data, SetOptions.merge())
                .addOnFailureListener(e -> hasSaved = false);
    }

    // ─── Public API (used by fragments / history) ─────────────────────────────

    public String getRawContent() { return etNoteContent.getText().toString().trim(); }
    public String getNoteTitle() { return etNoteTitle.getText().toString().trim(); }

    public void updateContentFromHistory(String oldTitle, String oldContent) {
        if (oldTitle != null) etNoteTitle.setText(oldTitle);
        if (oldContent != null) {
            if (oldContent.startsWith("<") && oldContent.contains(">")) {
                etNoteContent.setText(Html.fromHtml(oldContent, Html.FROM_HTML_MODE_LEGACY));
            } else {
                etNoteContent.setText(oldContent);
            }
        }
        triggerAutoSave();
    }

    // ─── Image adapter ────────────────────────────────────────────────────────

    private class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_image, parent, false);
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            try { holder.img.setImageURI(Uri.parse(imageUrls.get(position))); } catch (Exception ignored) {}
            holder.btnRemove.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos >= 0 && pos < imageUrls.size()) {
                    imageUrls.remove(pos);
                    notifyItemRemoved(pos);
                    if (imageUrls.isEmpty()) rvImages.setVisibility(View.GONE);
                    triggerAutoSave();
                }
            });
        }
        @Override
        public int getItemCount() { return imageUrls.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView img; ImageButton btnRemove;
            ViewHolder(View v) { super(v); img = v.findViewById(R.id.imgAttachment); btnRemove = v.findViewById(R.id.btnRemoveImage); }
        }
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
