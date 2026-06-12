package com.example.frienddebt.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.AlignmentSpan;
import com.example.frienddebt.ui.text.PaddingBackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
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
import com.example.frienddebt.ui.fragment.PageSettingsBottomSheet;
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

    // ─── UI refs ─────────────────────────────────────────────────────────────
    private EditText etNoteTitle, etNoteContent;
    private ImageButton btnBack, btnMenu, btnUndo, btnRedo, btnAddAttachment;
    private LinearLayout layoutTopBar, layoutDrawingBar;
    private RecyclerView rvImages;
    private View editorRoot;
    private Button btnFontSizeLabel;
    private TextView btnFolderChip;

    // ─── Firebase ─────────────────────────────────────────────────────────────
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private UndoRedoManager undoRedoManager;

    // ─── Note state ───────────────────────────────────────────────────────────
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

    // ─── Formatting state ─────────────────────────────────────────────────────
    private int currentFontSize = 16;   // sp
    private float currentLineSpacing = 5f; // dp extra

    // ─── Auto-save ────────────────────────────────────────────────────────────
    private final Handler autoSaveHandler = new Handler();
    private Runnable autoSaveRunnable;
    private boolean hasSaved = false;

    // ─── Full-screen ──────────────────────────────────────────────────────────
    private boolean isFullScreen = false;

    // ─── Image picker ─────────────────────────────────────────────────────────
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

        etNoteTitle      = findViewById(R.id.etNoteTitle);
        etNoteContent    = findViewById(R.id.etNoteContent);
        btnBack          = findViewById(R.id.btnBack);
        btnMenu          = findViewById(R.id.btnMenu);
        btnUndo          = findViewById(R.id.btnUndo);
        btnRedo          = findViewById(R.id.btnRedo);
        btnAddAttachment = findViewById(R.id.btnAddAttachment);
        layoutTopBar     = findViewById(R.id.layoutTopBar);
        layoutDrawingBar = findViewById(R.id.layoutDrawingBar);
        rvImages         = findViewById(R.id.rvImages);
        editorRoot       = findViewById(R.id.editorRoot);
        btnFolderChip    = findViewById(R.id.btnFolderChip);
        btnFontSizeLabel = findViewById(R.id.btnFontSize);

        imageAdapter = new ImageAdapter();
        rvImages.setAdapter(imageAdapter);

        undoRedoManager = new UndoRedoManager(etNoteContent);

        // Read default folder from intent (e.g. launched from Work filter in drawer)
        String folderDefault = getIntent().getStringExtra("FOLDER_DEFAULT");
        if (folderDefault != null && !folderDefault.isEmpty()) {
            selectedFolder = folderDefault;
        }

        noteId = getIntent().getStringExtra("NOTE_ID");
        if (noteId == null && auth.getCurrentUser() != null) {
            noteId = db.collection("users")
                    .document(auth.getCurrentUser().getUid())
                    .collection("notes").document().getId();
            isNewNote = true;
        }

        if (noteId != null && !isNewNote) loadNoteDetails();

        updateFolderChip();
        setupButtons();
        setupFormattingToolbar();
        setupAutoSave();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Cancel pending auto-save before forcing a save
        if (autoSaveRunnable != null) autoSaveHandler.removeCallbacks(autoSaveRunnable);
        saveNote(false);
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private void setupButtons() {
        btnBack.setOnClickListener(v -> { saveNote(false); finish(); });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { saveNote(false); finish(); }
        });

        btnMenu.setOnClickListener(this::showSamsungMenu);
        btnAddAttachment.setOnClickListener(v -> showAttachmentSheet());

        if (btnUndo != null) btnUndo.setOnClickListener(v -> undoRedoManager.undo());
        if (btnRedo != null) btnRedo.setOnClickListener(v -> undoRedoManager.redo());

        // Folder chip
        if (btnFolderChip != null) btnFolderChip.setOnClickListener(v -> showFolderPicker());

        // Row 2 drawing tools — show "coming soon" toast
        String drawingMsg = "Drawing tools coming soon";
        View.OnClickListener drawingToast = v -> Toast.makeText(this, drawingMsg, Toast.LENGTH_SHORT).show();
        View pen = findViewById(R.id.btnToolPen);
        View hl  = findViewById(R.id.btnToolHighlighter);
        View er  = findViewById(R.id.btnToolEraser);
        View la  = findViewById(R.id.btnToolLasso);
        View mk  = findViewById(R.id.btnToolMarker);
        if (pen != null) pen.setOnClickListener(drawingToast);
        if (hl  != null) hl.setOnClickListener(drawingToast);
        if (er  != null) er.setOnClickListener(drawingToast);
        if (la  != null) la.setOnClickListener(drawingToast);
        if (mk  != null) mk.setOnClickListener(drawingToast);
    }

    private void setupAutoSave() {
        TextWatcher tw = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                triggerAutoSave();
                // Scroll the ScrollView so the cursor line is always visible
                etNoteContent.post(() -> scrollToCursor());
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        etNoteTitle.addTextChangedListener(tw);
        etNoteContent.addTextChangedListener(tw);
    }

    /** Scrolls the parent ScrollView so the cursor position inside etNoteContent is visible. */
    private void scrollToCursor() {
        try {
            int cursorLine = getCursorLine();
            if (cursorLine < 0) return;
            android.widget.ScrollView sv = findParentScrollView(etNoteContent);
            if (sv == null) return;
            // Line height in px
            int lineHeight = etNoteContent.getLineHeight();
            int topOfLine  = cursorLine * lineHeight;
            int botOfLine  = topOfLine + lineHeight;
            // Add EditText's top offset inside the ScrollView
            int etTop = etNoteContent.getTop();
            sv.scrollTo(0, Math.max(0, etTop + topOfLine - lineHeight));
        } catch (Exception ignored) {}
    }

    private int getCursorLine() {
        int selStart = etNoteContent.getSelectionStart();
        if (selStart < 0 || etNoteContent.getLayout() == null) return -1;
        return etNoteContent.getLayout().getLineForOffset(selStart);
    }

    private android.widget.ScrollView findParentScrollView(View child) {
        if (child == null) return null;
        android.view.ViewParent parent = child.getParent();
        while (parent != null) {
            if (parent instanceof android.widget.ScrollView) return (android.widget.ScrollView) parent;
            parent = parent.getParent();
        }
        return null;
    }


    /**
     * Wire all formatting bar buttons.
     * KEY: each button is set non-focusable so clicking it does NOT steal focus from
     * etNoteContent. This preserves the text selection so getSelectionStart/End still
     * returns the correct range when the span is applied.
     */
    private void setupFormattingToolbar() {
        int[] nonFocusableIds = {
            R.id.btnFormatTask, R.id.btnFontColor, R.id.btnFontBg,
            R.id.btnTextStyle, R.id.btnFontSize, R.id.btnFormatBold,
            R.id.btnFormatItalic, R.id.btnFormatUnderline, R.id.btnFormatMore
        };
        for (int id : nonFocusableIds) {
            View v = findViewById(id);
            if (v != null) {
                v.setFocusable(false);
                v.setFocusableInTouchMode(false);
            }
        }

        // Checklist inline
        findViewById(R.id.btnFormatTask).setOnClickListener(v -> { insertInlineCheckbox(); restoreEditTextFocus(); });

        // Font colour
        findViewById(R.id.btnFontColor).setOnClickListener(v -> showFontColorPopup(v));

        // Font background
        findViewById(R.id.btnFontBg).setOnClickListener(v -> showFontBgPopup(v));

        // Text style (Aa)
        findViewById(R.id.btnTextStyle).setOnClickListener(v -> showTextStylePopup(v));

        // Font size
        if (btnFontSizeLabel != null) {
            btnFontSizeLabel.setFocusable(false);
            btnFontSizeLabel.setFocusableInTouchMode(false);
            btnFontSizeLabel.setOnClickListener(v -> showFontSizePopup(v));
        }

        // Bold, Italic, Underline — live formatting with focus preservation
        btnBold      = findViewById(R.id.btnFormatBold);
        btnItalic    = findViewById(R.id.btnFormatItalic);
        btnUnderline = findViewById(R.id.btnFormatUnderline);

        btnBold.setOnClickListener(v      -> { toggleStyleSpan(Typeface.BOLD);    refreshToolbarState(); restoreEditTextFocus(); });
        btnItalic.setOnClickListener(v    -> { toggleStyleSpan(Typeface.ITALIC);  refreshToolbarState(); restoreEditTextFocus(); });
        btnUnderline.setOnClickListener(v -> { toggleUnderlineSpan();             refreshToolbarState(); restoreEditTextFocus(); });

        // Overflow → more options
        findViewById(R.id.btnFormatMore).setOnClickListener(v -> showOverflowPopup(v));

        // Listen for selection changes to light up B/I/U contextually
        setupSelectionListener();
    }

    /** Keep a reference so we can update colors without repeated findViewById */
    private Button btnBold, btnItalic, btnUnderline;

    /**
     * After tapping a formatting button we force focus back to the EditText
     * so the keyboard stays up and the cursor is visible.
     */
    private void restoreEditTextFocus() {
        etNoteContent.requestFocus();
    }

    /**
     * Touch listener on the EditText: whenever the user lifts their finger,
     * check what spans are at the cursor / selection and highlight the relevant
     * B / I / U buttons in the toolbar (Samsung Notes contextual behavior).
     */
    private void setupSelectionListener() {
        etNoteContent.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                // Post so the selection is finalized before we read it
                etNoteContent.postDelayed(this::refreshToolbarState, 80);
            }
            return false; // don't consume — let EditText handle it normally
        });
    }

    /**
     * Reads the current selection (or cursor position) and highlights the formatting
     * buttons that are active for the selected span range — exactly like Samsung Notes.
     */
    private void refreshToolbarState() {
        if (btnBold == null || btnItalic == null || btnUnderline == null) return;
        int start = etNoteContent.getSelectionStart();
        int end   = etNoteContent.getSelectionEnd();
        if (start < 0 || end < 0) return;
        // If no selection, check the span at cursor - 1
        int checkStart = (start == end) ? Math.max(0, start - 1) : start;
        int checkEnd   = (start == end) ? start : end;
        if (checkEnd <= checkStart) checkEnd = checkStart + 1;
        Editable e = etNoteContent.getText();
        if (checkEnd > e.length()) checkEnd = e.length();
        if (checkStart >= checkEnd) {
            // Nothing to check — neutral state
            resetToolbarColors();
            return;
        }

        boolean isBold = false, isItalic = false, isUnderline = false;
        for (StyleSpan s : e.getSpans(checkStart, checkEnd, StyleSpan.class)) {
            if (s.getStyle() == Typeface.BOLD)   isBold   = true;
            if (s.getStyle() == Typeface.ITALIC) isItalic = true;
        }
        for (UnderlineSpan ignored : e.getSpans(checkStart, checkEnd, UnderlineSpan.class)) {
            isUnderline = true;
        }

        int activeColor  = getColor(R.color.primary);
        int normalColor  = getColor(R.color.text_primary);

        btnBold.setTextColor(isBold      ? activeColor : normalColor);
        btnItalic.setTextColor(isItalic  ? activeColor : normalColor);
        btnUnderline.setTextColor(isUnderline ? activeColor : normalColor);
    }

    private void resetToolbarColors() {
        int normalColor = getColor(R.color.text_primary);
        if (btnBold      != null) btnBold.setTextColor(normalColor);
        if (btnItalic    != null) btnItalic.setTextColor(normalColor);
        if (btnUnderline != null) btnUnderline.setTextColor(normalColor);
    }

    // ─── Folder Picker ────────────────────────────────────────────────────────

    private void showFolderPicker() {
        String[] folders = {"Personal", "Work"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Select folder")
                .setSingleChoiceItems(folders,
                        selectedFolder.equals("Work") ? 1 : 0,
                        (dialog, which) -> {
                            selectedFolder = folders[which];
                            updateFolderChip();
                            triggerAutoSave();
                            dialog.dismiss();
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateFolderChip() {
        if (btnFolderChip != null) btnFolderChip.setText(selectedFolder);
    }

    // ─── Font Colour Popup ────────────────────────────────────────────────────

    private void showFontColorPopup(View anchor) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_font_color, null);
        PopupWindow popup = buildPopupWindow(popupView);

        LinearLayout row = popupView.findViewById(R.id.colorRowFontColor);
        popupView.findViewById(R.id.btnCloseFontColor).setOnClickListener(v -> popup.dismiss());

        int[] fontColors = {
                Color.parseColor("#F44336"), // Red
                Color.parseColor("#FF9800"), // Orange
                Color.parseColor("#FFEB3B"), // Yellow
                Color.parseColor("#009688"), // Teal
                Color.parseColor("#2196F3"), // Blue
                Color.parseColor("#3F51B5"), // Indigo
                Color.parseColor("#9C27B0"), // Purple
                Color.parseColor("#9E9E9E"), // Gray
                Color.WHITE                  // White (reset/default)
        };

        for (int color : fontColors) {
            View dot = buildColorDot(color);
            dot.setOnClickListener(v -> {
                applyFontColor(color);
                popup.dismiss();
            });
            row.addView(dot);
        }

        showPopupAbove(popup, anchor);
    }

    private void applyFontColor(int color) {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        int end   = etNoteContent.getSelectionEnd();
        if (start < 0 || end < 0 || start == end) return;
        Editable e = etNoteContent.getText();
        for (ForegroundColorSpan s : e.getSpans(start, end, ForegroundColorSpan.class)) e.removeSpan(s);
        e.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        triggerAutoSave();
    }

    // ─── Font Background Popup ────────────────────────────────────────────────

    private void showFontBgPopup(View anchor) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_font_bg, null);
        PopupWindow popup = buildPopupWindow(popupView);

        LinearLayout row = popupView.findViewById(R.id.colorRowFontBg);
        popupView.findViewById(R.id.btnCloseFontBg).setOnClickListener(v -> popup.dismiss());

        // First dot is black (= "clear" / default) with a checkmark
        int[] bgColors = {
                Color.TRANSPARENT,           // Clear/default
                Color.parseColor("#F44336"), // Red
                Color.parseColor("#FF9800"), // Orange
                Color.parseColor("#FFEB3B"), // Yellow
                Color.parseColor("#009688"), // Teal
                Color.parseColor("#2196F3"), // Blue
                Color.parseColor("#9C27B0"), // Purple
                Color.WHITE,                 // White
        };

        for (int color : bgColors) {
            View dot = buildColorDot(color == Color.TRANSPARENT ? Color.parseColor("#333333") : color);
            dot.setOnClickListener(v -> {
                applyFontBg(color);
                popup.dismiss();
            });
            row.addView(dot);
        }

        showPopupAbove(popup, anchor);
    }

    private void applyFontBg(int color) {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        int end   = etNoteContent.getSelectionEnd();
        if (start < 0 || end < 0 || start == end) return;
        Editable e = etNoteContent.getText();
        for (PaddingBackgroundColorSpan s : e.getSpans(start, end, PaddingBackgroundColorSpan.class)) e.removeSpan(s);
        if (color != Color.TRANSPARENT) {
            // Apply 6px padding and 8px corner radius
            e.setSpan(new PaddingBackgroundColorSpan(color, 6, 8f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        triggerAutoSave();
    }

    // ─── Text Style Popup (Aa) ────────────────────────────────────────────────

    private void showTextStylePopup(View anchor) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_text_style, null);
        PopupWindow popup = buildPopupWindow(popupView);

        popupView.findViewById(R.id.styleHeading1).setOnClickListener(v -> { applyTextStyle(1.6f, Typeface.BOLD); popup.dismiss(); });
        popupView.findViewById(R.id.styleHeading2).setOnClickListener(v -> { applyTextStyle(1.3f, Typeface.BOLD); popup.dismiss(); });
        popupView.findViewById(R.id.styleHeading3).setOnClickListener(v -> { applyTextStyle(1.15f, Typeface.BOLD); popup.dismiss(); });
        popupView.findViewById(R.id.styleBody1).setOnClickListener(v -> { applyTextStyle(1.0f, Typeface.NORMAL); popup.dismiss(); });
        popupView.findViewById(R.id.styleBody2).setOnClickListener(v -> { applyTextStyle(0.875f, Typeface.NORMAL); popup.dismiss(); });

        showPopupAbove(popup, anchor);
    }

    private void applyTextStyle(float sizeMultiplier, int typefaceStyle) {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        int end   = etNoteContent.getSelectionEnd();
        if (start < 0) start = 0;
        if (end < 0 || end == start) end = etNoteContent.getText().length();

        // Get line range
        String text = etNoteContent.getText().toString();
        int lineStart = start;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        int lineEnd = end;
        while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;

        Editable e = etNoteContent.getText();
        // Remove existing
        for (RelativeSizeSpan s : e.getSpans(lineStart, lineEnd, RelativeSizeSpan.class)) e.removeSpan(s);
        for (StyleSpan s : e.getSpans(lineStart, lineEnd, StyleSpan.class)) e.removeSpan(s);
        // Apply new
        if (sizeMultiplier != 1.0f)
            e.setSpan(new RelativeSizeSpan(sizeMultiplier), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (typefaceStyle != Typeface.NORMAL)
            e.setSpan(new StyleSpan(typefaceStyle), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        triggerAutoSave();
    }

    // ─── Font Size Popup ──────────────────────────────────────────────────────

    private void showFontSizePopup(View anchor) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_font_size, null);
        PopupWindow popup = buildPopupWindow(popupView);

        // Build size list 10–40
        Integer[] sizes = new Integer[31];
        for (int i = 0; i <= 30; i++) sizes[i] = 10 + i;

        ListView listView = popupView.findViewById(R.id.listFontSize);
        ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(this,
                android.R.layout.simple_list_item_single_choice, sizes) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                int size = sizes[position];
                tv.setTextColor(size == currentFontSize
                        ? getColor(R.color.primary)
                        : getColor(R.color.text_primary));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                tv.setPadding(48, 0, 48, 0);
                return tv;
            }
        };
        listView.setAdapter(adapter);
        listView.setSelection(currentFontSize - 10);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            setFontSize(sizes[position]);
            popup.dismiss();
        });

        showPopupAbove(popup, anchor);
    }

    // ─── Overflow Popup ───────────────────────────────────────────────────────

    private void showOverflowPopup(View anchor) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_format_overflow, null);
        PopupWindow popup = buildPopupWindow(popupView);

        popupView.findViewById(R.id.overflowQuote).setOnClickListener(v -> {
            toggleQuoteSpan(); restoreEditTextFocus(); popup.dismiss(); });
        popupView.findViewById(R.id.overflowStrikethrough).setOnClickListener(v -> {
            toggleStrikethroughSpan(); restoreEditTextFocus(); popup.dismiss(); });
        popupView.findViewById(R.id.overflowBullet).setOnClickListener(v -> {
            toggleBulletSpan(); restoreEditTextFocus(); popup.dismiss(); });
        popupView.findViewById(R.id.overflowNumbered).setOnClickListener(v -> {
            insertNumberedListItem(); restoreEditTextFocus(); popup.dismiss(); });
        popupView.findViewById(R.id.overflowAlignLeft).setOnClickListener(v -> {
            toggleAlignment(Layout.Alignment.ALIGN_NORMAL); restoreEditTextFocus(); popup.dismiss(); });
        popupView.findViewById(R.id.overflowAlignCenter).setOnClickListener(v -> {
            toggleAlignment(Layout.Alignment.ALIGN_CENTER); restoreEditTextFocus(); popup.dismiss(); });
        popupView.findViewById(R.id.overflowAlignRight).setOnClickListener(v -> {
            toggleAlignment(Layout.Alignment.ALIGN_OPPOSITE); restoreEditTextFocus(); popup.dismiss(); });
        popupView.findViewById(R.id.overflowAddImage).setOnClickListener(v -> {
            openGallery(); popup.dismiss(); });

        showPopupAbove(popup, anchor);
    }

    // ─── Popup helpers ────────────────────────────────────────────────────────

    private PopupWindow buildPopupWindow(View content) {
        PopupWindow pw = new PopupWindow(content,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setElevation(16f);
        pw.setOutsideTouchable(true);
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return pw;
    }

    /** Show the popup window anchored above the given view (so it doesn't go under keyboard) */
    private void showPopupAbove(PopupWindow popup, View anchor) {
        popup.getContentView().measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeight = popup.getContentView().getMeasuredHeight();
        popup.showAsDropDown(anchor, 0, -(anchor.getHeight() + popupHeight + 8));
    }

    /** Build a 36dp colored circle View for color pickers */
    private View buildColorDot(int color) {
        View dot = new View(this);
        int size = (int) (36 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(8, 4, 8, 4);
        dot.setLayoutParams(lp);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        gd.setStroke(2, Color.parseColor("#44FFFFFF"));
        dot.setBackground(gd);
        dot.setClickable(true);
        dot.setFocusable(true);
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        dot.setForeground(ContextCompat.getDrawable(this, outValue.resourceId));
        return dot;
    }

    // ─── Inline Checklist ─────────────────────────────────────────────────────

    /**
     * Insert ☐ at the start of each selected line.
     * If the line already starts with ☐ or ☑, remove the prefix (toggle off).
     */
    private void insertInlineCheckbox() {
        undoRedoManager.saveState();
        Editable e = etNoteContent.getText();
        int start = etNoteContent.getSelectionStart();
        int end   = etNoteContent.getSelectionEnd();
        if (start < 0) start = e.length();
        if (end < 0) end = start;

        // Find line start
        String text = e.toString();
        int lineStart = start;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;

        String line = text.substring(lineStart, Math.min(lineStart + 2, text.length()));
        if (line.startsWith("☐ ") || line.startsWith("☑ ")) {
            // Remove prefix
            e.delete(lineStart, lineStart + 2);
        } else {
            // Insert ☐
            e.insert(lineStart, "☐ ");
        }
        triggerAutoSave();
    }

    /**
     * Handle tapping on a ☐ or ☑ character in the editor to toggle it.
     * Called from the touch listener set on etNoteContent.
     */
    private void toggleCheckboxAtCursor() {
        int pos = etNoteContent.getSelectionStart();
        if (pos < 0) return;
        Editable e = etNoteContent.getText();
        String text = e.toString();
        // Find line start
        int lineStart = pos;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;

        if (lineStart + 2 > text.length()) return;
        String prefix = text.substring(lineStart, lineStart + 2);
        if (prefix.equals("☐ ")) {
            // Check: ☐ → ☑ + strikethrough
            e.replace(lineStart, lineStart + 1, "☑");
            int lineEnd = lineStart;
            while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;
            e.setSpan(new StrikethroughSpan(), lineStart + 2, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            triggerAutoSave();
        } else if (prefix.equals("☑ ")) {
            // Uncheck: ☑ → ☐, remove strikethrough
            e.replace(lineStart, lineStart + 1, "☐");
            int lineEnd = lineStart;
            while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;
            for (StrikethroughSpan s : e.getSpans(lineStart, lineEnd, StrikethroughSpan.class)) e.removeSpan(s);
            triggerAutoSave();
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
            new PageSettingsBottomSheet().show(getSupportFragmentManager(), "PageSettings");
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

    // ─── Full Screen ──────────────────────────────────────────────────────────

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
                        etNoteContent.setVisibility(View.VISIBLE);
                        etNoteTitle.setEnabled(true);
                    }
                    @Override public void onAuthenticationError(int errorCode, @NonNull CharSequence msg) { finish(); }
                    @Override public void onAuthenticationFailed() { finish(); }
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
                            else Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show();
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
        // Use theme color instead of hardcoded white
        et.setTextColor(getColor(R.color.text_primary));
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
        if (shareText.trim().isEmpty()) {
            Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show();
            return;
        }
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
                            .setItems(items, (d, w) -> updateContentFromHistory(
                                    docs.get(w).getString("title"),
                                    docs.get(w).getString("content")))
                            .setNegativeButton("Close", null)
                            .show();
                });
    }

    // ─── Note Style / Page Style ──────────────────────────────────────────────

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
        switch (style) {
            case "lined":  etNoteContent.setBackground(getResources().getDrawable(R.drawable.bg_note_lined)); break;
            case "grid":   etNoteContent.setBackground(getResources().getDrawable(R.drawable.bg_note_grid)); break;
            default:       etNoteContent.setBackground(null); break;
        }
        triggerAutoSave();
    }

    // ─── Public API (used by PageSettingsBottomSheet etc.) ────────────────────

    public int getCurrentFontSize() { return currentFontSize; }

    public void setFontSize(int sizeSp) {
        currentFontSize = sizeSp;
        etNoteContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (btnFontSizeLabel != null) btnFontSizeLabel.setText(sizeSp + "▼");
        triggerAutoSave();
    }

    public void setLineSpacing(float extraDp) {
        currentLineSpacing = extraDp;
        float extraPx = extraDp * getResources().getDisplayMetrics().density;
        etNoteContent.setLineSpacing(extraPx, 1f);
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

    /**
     * Quote/blockquote: applies a colored vertical-bar QuoteSpan on the current paragraph.
     * Tapping again removes the quote. Works live, no preview needed.
     */
    private void toggleQuoteSpan() {
        undoRedoManager.saveState();
        int start = etNoteContent.getSelectionStart();
        Editable e = etNoteContent.getText();
        if (start < 0) return;
        String text = e.toString();
        int lineStart = start; while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        int lineEnd = start; while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;
        android.text.style.QuoteSpan[] existing = e.getSpans(lineStart, lineEnd, android.text.style.QuoteSpan.class);
        if (existing.length > 0) {
            for (android.text.style.QuoteSpan s : existing) e.removeSpan(s);
        } else {
            int quoteColor = ContextCompat.getColor(this, R.color.primary);
            e.setSpan(new android.text.style.QuoteSpan(quoteColor), lineStart, lineEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
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
        String before = e.toString().substring(0, start);
        int count = 0;
        for (String line : before.split("\n")) { if (line.matches("^\\d+\\..*")) count++; }
        e.insert(start, (count + 1) + ". ");
        triggerAutoSave();
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
                    originalTitle   = note.getTitle() != null ? note.getTitle() : "";
                    originalContent = note.getContent() != null ? note.getContent() : "";
                    selectedColor   = note.getColorCode() != null && !note.getColorCode().equals("#FFFFFF") ? note.getColorCode() : null;
                    isPinned        = note.isPinned();
                    isArchived      = note.isArchived();
                    isDeleted       = note.isDeleted();
                    isLocked        = note.isLocked();
                    noteType        = note.getType() != null ? note.getType() : "TEXT";
                    selectedFolder  = note.getFolder() != null ? note.getFolder() : "Personal";
                    pageStyle       = note.getPageStyle() != null ? note.getPageStyle() : "blank";
                    tags.clear();
                    if (note.getTags() != null) tags.addAll(note.getTags());
                    if (note.getImageUrls() != null && !note.getImageUrls().isEmpty()) {
                        imageUrls.clear(); imageUrls.addAll(note.getImageUrls());
                        rvImages.setVisibility(View.VISIBLE); imageAdapter.notifyDataSetChanged();
                    }
                    // Load font size if saved
                    Long savedFontSize = doc.getLong("fontSize");
                    if (savedFontSize != null && savedFontSize > 0) {
                        setFontSize(savedFontSize.intValue());
                    }

                    updateFolderChip();
                    etNoteTitle.setText(originalTitle);

                    if (isLocked) {
                        etNoteContent.setText("🔒 Locked");
                        etNoteContent.setEnabled(false);
                        showLockedNote();
                        return;
                    }

                    // Old CHECKLIST notes: convert JSON items to ☐/☑ lines for inline display
                    if (noteType.equals("CHECKLIST") && originalContent.startsWith("[")) {
                        StringBuilder sb = new StringBuilder();
                        try {
                            org.json.JSONArray arr = new org.json.JSONArray(originalContent);
                            for (int i = 0; i < arr.length(); i++) {
                                org.json.JSONObject obj = arr.getJSONObject(i);
                                boolean checked = obj.optBoolean("checked", false);
                                String text = obj.optString("text", "");
                                sb.append(checked ? "☑ " : "☐ ").append(text).append("\n");
                            }
                        } catch (Exception ignored) {}
                        etNoteContent.setText(sb.toString().trim());
                        noteType = "TEXT"; // Migrate to TEXT mode going forward
                    } else if (originalContent.startsWith("<") && originalContent.contains(">")) {
                        etNoteContent.setText(Html.fromHtml(originalContent, Html.FROM_HTML_MODE_LEGACY));
                    } else {
                        etNoteContent.setText(originalContent);
                    }

                    applyColor(selectedColor);
                    applyPageStyle(pageStyle);
                });
    }

    // ─── Auto-save ────────────────────────────────────────────────────────────

    private void triggerAutoSave() {
        hasSaved = false;
        if (autoSaveRunnable != null) autoSaveHandler.removeCallbacks(autoSaveRunnable);
        autoSaveRunnable = () -> saveNote(false);
        autoSaveHandler.postDelayed(autoSaveRunnable, 2000);
    }

    public void saveNote(boolean force) {
        if (hasSaved && !force) return;

        String title = etNoteTitle.getText().toString().trim();
        String contentHtml = Html.toHtml(etNoteContent.getText(), Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);

        if (isNewNote && title.isEmpty() && etNoteContent.getText().toString().trim().isEmpty()) {
            hasSaved = true; return;
        }
        if (auth.getCurrentUser() == null) { hasSaved = true; return; }

        hasSaved = true;
        String userId = auth.getCurrentUser().getUid();

        // Save history snapshot
        if (!isNewNote && (!originalTitle.equals(title) || !originalContent.equals(contentHtml))) {
            Map<String, Object> hist = new HashMap<>();
            hist.put("title", originalTitle);
            hist.put("content", originalContent);
            hist.put("savedAt", System.currentTimeMillis());
            db.collection("users").document(userId).collection("notes").document(noteId)
                    .collection("history").add(hist);
        }

        originalTitle   = title;
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
        data.put("fontSize", currentFontSize);
        data.put("imageUrl", imageUrls.isEmpty() ? null : imageUrls.get(0));
        data.put("imageUrls", imageUrls);
        data.put("updatedAt", System.currentTimeMillis());
        if (isNewNote) { data.put("createdAt", System.currentTimeMillis()); isNewNote = false; }

        db.collection("users").document(userId).collection("notes").document(noteId)
                .set(data, SetOptions.merge())
                .addOnFailureListener(e -> hasSaved = false);
    }

    // ─── Public API (for history restore etc.) ────────────────────────────────

    public String getRawContent() { return etNoteContent.getText().toString().trim(); }
    public String getNoteTitle()  { return etNoteTitle.getText().toString().trim(); }

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
        @Override public int getItemCount() { return imageUrls.size(); }
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
