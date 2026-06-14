package com.example.frienddebt.ui.fragment;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Html;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Note;
import com.example.frienddebt.ui.AddEditNoteActivity;
import com.example.frienddebt.ui.GlobalSearchActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.net.Uri;
import android.widget.ImageView;
import androidx.cardview.widget.CardView;

import com.google.firebase.firestore.QueryDocumentSnapshot;

public class NotesFragment extends Fragment {

    private DrawerLayout drawerLayout;
    private RecyclerView rvNotes;
    private FloatingActionButton fabAddNote;
    private LinearLayout layoutEmptyState;
    private LinearLayout layoutCustomFolders;
    private TextView txtHeaderSubtitle;
    private ImageButton btnSearch, btnMenu, btnMore;

    // Drawer filter targets
    private TextView drawerAll, drawerPinned, drawerPersonal, drawerWork, drawerArchive, drawerTrash;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration notesListener;
    private ListenerRegistration foldersListener;

    private List<Note> allNotes = new ArrayList<>();
    private List<Object> displayItems = new ArrayList<>(); // String headers + Note objects
    private NotesAdapter adapter;

    // Active filter
    private String activeFilter = "all"; // all, pinned, personal, work, archived, trash

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notes, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        drawerLayout    = view.findViewById(R.id.drawerLayout);
        rvNotes         = view.findViewById(R.id.rvNotes);
        fabAddNote      = view.findViewById(R.id.fabAddNote);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        layoutCustomFolders = view.findViewById(R.id.layoutCustomFolders);
        txtHeaderSubtitle = view.findViewById(R.id.txtHeaderSubtitle);
        btnSearch       = view.findViewById(R.id.btnSearch);
        btnMenu         = view.findViewById(R.id.btnMenu);
        btnMore         = view.findViewById(R.id.btnMore);

        drawerAll      = view.findViewById(R.id.drawerAll);
        drawerPinned   = view.findViewById(R.id.drawerPinned);
        drawerPersonal = view.findViewById(R.id.drawerPersonal);
        drawerWork     = view.findViewById(R.id.drawerWork);
        drawerArchive  = view.findViewById(R.id.drawerArchive);
        drawerTrash    = view.findViewById(R.id.drawerTrash);

        setupDrawer();
        setupRecyclerView();
        setupFab();
        setupTopBarButtons();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadNotes();
        loadFolders();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (notesListener != null) { notesListener.remove(); notesListener = null; }
        if (foldersListener != null) { foldersListener.remove(); foldersListener = null; }
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private void setupDrawer() {
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(androidx.core.view.GravityCompat.START);
            });
        }

        setDrawerItem(drawerAll, "all");
        setDrawerItem(drawerPinned, "pinned");
        setDrawerItem(drawerPersonal, "personal");
        setDrawerItem(drawerWork, "work");
        setDrawerItem(drawerArchive, "archived");
        setDrawerItem(drawerTrash, "trash");
    }

    private void setDrawerItem(TextView tv, String filter) {
        if (tv == null) return;
        tv.setOnClickListener(v -> {
            activeFilter = filter;
            if (drawerLayout != null) drawerLayout.closeDrawers();
            applyFilter();
        });
    }

    private void setupRecyclerView() {
        rvNotes.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NotesAdapter();
        rvNotes.setAdapter(adapter);

        // Swipe gestures
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                // Only allow swipe on Note items, not on section headers
                int pos = vh.getAdapterPosition();
                if (pos >= 0 && pos < displayItems.size() && displayItems.get(pos) instanceof String) {
                    return makeMovementFlags(0, 0);
                }
                return makeMovementFlags(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) { return false; }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                if (pos < 0 || pos >= displayItems.size()) return;
                Object item = displayItems.get(pos);
                if (!(item instanceof Note)) return;
                Note note = (Note) item;

                if (direction == ItemTouchHelper.LEFT) {
                    // Archive
                    archiveNote(note);
                    Snackbar.make(rvNotes, "Note archived", Snackbar.LENGTH_LONG)
                            .setAction("Undo", v -> unarchiveNote(note))
                            .setActionTextColor(getResources().getColor(R.color.primary))
                            .show();
                } else {
                    // Trash
                    trashNote(note);
                    Snackbar.make(rvNotes, "Moved to trash", Snackbar.LENGTH_LONG)
                            .setAction("Undo", v -> untrashNote(note))
                            .setActionTextColor(getResources().getColor(R.color.primary))
                            .show();
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh, float dX, float dY,
                                    int actionState, boolean active) {
                View itemView = vh.itemView;
                Paint paint = new Paint();
                if (dX < 0) { // Swipe left = archive (blue)
                    paint.setColor(Color.parseColor("#0A84FF"));
                    c.drawRect((float) itemView.getRight() + dX, itemView.getTop(),
                            itemView.getRight(), itemView.getBottom(), paint);
                } else { // Swipe right = trash (red)
                    paint.setColor(Color.parseColor("#FF453A"));
                    c.drawRect(itemView.getLeft(), itemView.getTop(),
                            (float) itemView.getLeft() + dX, itemView.getBottom(), paint);
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, active);
            }
        });
        itemTouchHelper.attachToRecyclerView(rvNotes);
    }

    private void setupFab() {
        fabAddNote.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(requireContext(), android.R.anim.fade_in));
            startActivity(new Intent(requireActivity(), AddEditNoteActivity.class));
        });
    }

    private void setupTopBarButtons() {
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> startActivity(new Intent(requireContext(), GlobalSearchActivity.class)));
        }
        if (btnMore != null) {
            btnMore.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), btnMore);
                popup.getMenu().add("Select");
                popup.getMenu().add("Sort by date");
                popup.getMenu().add("Sort by name");
                popup.getMenu().add("New folder");
                popup.setOnMenuItemClickListener(item -> {
                    if ("New folder".contentEquals(item.getTitle())) {
                        showCreateFolderDialog();
                    } else {
                        Toast.makeText(requireContext(), item.getTitle(), Toast.LENGTH_SHORT).show();
                    }
                    return true;
                });
                popup.show();
            });
        }
    }

    // ─── Data Loading ─────────────────────────────────────────────────────────

    public void loadNotes() {
        if (auth == null || auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();
        if (notesListener != null) notesListener.remove();

        notesListener = db.collection("users")
                .document(userId)
                .collection("notes")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;
                    allNotes.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        allNotes.add(Note.fromDocument(doc));
                    }
                    applyFilter();
                });
    }

    private void loadFolders() {
        if (auth == null || auth.getCurrentUser() == null || db == null || layoutCustomFolders == null) return;
        if (foldersListener != null) foldersListener.remove();

        foldersListener = db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .collection("noteFolders")
                .orderBy("name")
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null || !isAdded()) return;
                    renderCustomFolders(snapshots.getDocuments());
                });
    }

    private void renderCustomFolders(List<DocumentSnapshot> folderDocs) {
        if (layoutCustomFolders == null) return;
        layoutCustomFolders.removeAllViews();

        if (folderDocs.isEmpty()) {
            return;
        }

        for (DocumentSnapshot doc : folderDocs) {
            String folderName = doc.getString("name");
            if (folderName == null || folderName.trim().isEmpty()) continue;

            TextView folderView = new TextView(requireContext());
            folderView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            folderView.setText(folderName);
            folderView.setTextSize(15f);
            folderView.setTextColor(getResources().getColor(R.color.text_primary));
            folderView.setGravity(android.view.Gravity.CENTER_VERTICAL);
            folderView.setPadding(dp(20), dp(12), dp(20), dp(12));
            folderView.setBackgroundResource(androidx.appcompat.R.drawable.abc_list_selector_holo_dark);
            folderView.setOnClickListener(v -> {
                activeFilter = folderName;
                if (drawerLayout != null) drawerLayout.closeDrawers();
                applyFilter();
            });
            layoutCustomFolders.addView(folderView);
        }
    }

    private void showCreateFolderDialog() {
        if (!isAdded() || auth == null || auth.getCurrentUser() == null || db == null) return;

        EditText input = new EditText(requireContext());
        input.setHint("Folder name");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int padding = dp(16);
        input.setPadding(padding, padding, padding, padding);

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Create folder")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), "Folder name is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createFolder(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createFolder(String name) {
        String folderId = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("name", name);
        data.put("createdAt", System.currentTimeMillis());

        db.collection("users").document(auth.getCurrentUser().getUid())
                .collection("noteFolders")
                .document(folderId)
                .set(data)
                .addOnSuccessListener(unused -> Toast.makeText(requireContext(), "Folder created", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Failed to create folder", Toast.LENGTH_SHORT).show());
    }

    private void applyFilter() {
        List<Note> filtered = new ArrayList<>();
        for (Note n : allNotes) {
            switch (activeFilter) {
                case "pinned":   if (n.isPinned() && !n.isDeleted() && !n.isArchived()) filtered.add(n); break;
                case "personal": if ("Personal".equalsIgnoreCase(n.getFolder()) && !n.isDeleted() && !n.isArchived()) filtered.add(n); break;
                case "work":     if ("Work".equalsIgnoreCase(n.getFolder()) && !n.isDeleted() && !n.isArchived()) filtered.add(n); break;
                case "archived": if (n.isArchived() && !n.isDeleted()) filtered.add(n); break;
                case "trash":    if (n.isDeleted()) filtered.add(n); break;
                default:
                    if (!n.isDeleted() && !n.isArchived()) {
                        if ("all".equalsIgnoreCase(activeFilter) || activeFilter.equalsIgnoreCase(n.getFolder())) {
                            filtered.add(n);
                        }
                    }
                    break;
            }
        }

        txtHeaderSubtitle.setText(filtered.size() + " notes");
        buildDisplayList(filtered);
    }

    private void buildDisplayList(List<Note> notes) {
        displayItems.clear();

        Calendar todayCal = Calendar.getInstance();
        Calendar yesterdayCal = Calendar.getInstance();
        yesterdayCal.add(Calendar.DAY_OF_YEAR, -1);

        String currentHeader = "";
        for (Note note : notes) {
            Calendar noteCal = Calendar.getInstance();
            noteCal.setTimeInMillis(note.getUpdatedAt());

            String header;
            if (isSameDay(noteCal, todayCal)) {
                header = "Today";
            } else if (isSameDay(noteCal, yesterdayCal)) {
                header = "Yesterday";
            } else if (noteCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)) {
                header = new SimpleDateFormat("MMMM", Locale.getDefault()).format(noteCal.getTime());
            } else {
                header = String.valueOf(noteCal.get(Calendar.YEAR));
            }

            if (!header.equals(currentHeader)) {
                displayItems.add(header);
                currentHeader = header;
            }
            displayItems.add(note);
        }

        adapter.notifyDataSetChanged();

        if (notes.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            rvNotes.setVisibility(View.GONE);
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            rvNotes.setVisibility(View.VISIBLE);
        }
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density);
    }

    // ─── Firestore mutations ───────────────────────────────────────────────────

    private void archiveNote(Note note) {
        if (auth.getCurrentUser() == null) return;
        db.collection("users").document(auth.getCurrentUser().getUid())
                .collection("notes").document(note.getId())
                .update("isArchived", true);
    }

    private void unarchiveNote(Note note) {
        if (auth.getCurrentUser() == null) return;
        db.collection("users").document(auth.getCurrentUser().getUid())
                .collection("notes").document(note.getId())
                .update("isArchived", false);
    }

    private void trashNote(Note note) {
        if (auth.getCurrentUser() == null) return;
        db.collection("users").document(auth.getCurrentUser().getUid())
                .collection("notes").document(note.getId())
                .update("isDeleted", true);
    }

    private void untrashNote(Note note) {
        if (auth.getCurrentUser() == null) return;
        db.collection("users").document(auth.getCurrentUser().getUid())
                .collection("notes").document(note.getId())
                .update("isDeleted", false);
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_NOTE = 1;

    private class NotesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return (displayItems.get(position) instanceof String) ? TYPE_HEADER : TYPE_NOTE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                TextView tv = new TextView(parent.getContext());
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT);
                tv.setLayoutParams(lp);
                tv.setPadding(dpToPx(16), dpToPx(20), dpToPx(16), dpToPx(6));
                tv.setTextSize(13f);
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
                tv.setLetterSpacing(0.04f);
                tv.setTextColor(getResources().getColor(R.color.note_section_header));
                return new HeaderViewHolder(tv);
            } else {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_note_samsung, parent, false);
                return new NoteViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == TYPE_HEADER) {
                ((HeaderViewHolder) holder).tv.setText((String) displayItems.get(position));
            } else {
                Note note = (Note) displayItems.get(position);
                NoteViewHolder h = (NoteViewHolder) holder;

                // Title
                String title = note.getTitle();
                if (title == null || title.trim().isEmpty()) title = "Untitled";
                h.txtTitle.setText(title);

                // Date
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(note.getUpdatedAt());
                Calendar today = Calendar.getInstance();
                if (isSameDay(cal, today)) {
                    h.txtDate.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.getTime()));
                } else {
                    h.txtDate.setText(new SimpleDateFormat("d MMM", Locale.getDefault()).format(cal.getTime()));
                }

                // Thumbnail: prefer image, else locked, else text preview
                if (note.isLocked()) {
                    h.imgThumbnail.setVisibility(View.GONE);
                    h.imgLock.setVisibility(View.VISIBLE);
                    h.txtPreviewLines.setVisibility(View.GONE);
                    h.txtTitle.setText("Locked note");
                    h.txtTitle.setAlpha(0.4f);
                } else if (note.getImageUrls() != null && !note.getImageUrls().isEmpty()) {
                    h.imgLock.setVisibility(View.GONE);
                    h.txtPreviewLines.setVisibility(View.GONE);
                    h.imgThumbnail.setVisibility(View.VISIBLE);
                    try {
                        h.imgThumbnail.setImageURI(Uri.parse(note.getImageUrls().get(0)));
                    } catch (Exception e) {
                        h.imgThumbnail.setVisibility(View.GONE);
                        h.txtPreviewLines.setVisibility(View.VISIBLE);
                    }
                } else {
                    h.imgThumbnail.setVisibility(View.GONE);
                    h.imgLock.setVisibility(View.GONE);
                    h.txtPreviewLines.setVisibility(View.VISIBLE);
                    h.txtTitle.setAlpha(1f);
                    String content = note.getContent();
                    if (content == null) content = "";
                    if (content.startsWith("<") && content.contains(">")) {
                        h.txtPreviewLines.setText(Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY).toString().trim());
                    } else {
                        h.txtPreviewLines.setText(content.trim());
                    }
                }

                // Pin
                h.imgPinned.setVisibility(note.isPinned() ? View.VISIBLE : View.GONE);

                // Click to open
                holder.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(requireActivity(), AddEditNoteActivity.class);
                    intent.putExtra("NOTE_ID", note.getId());
                    startActivity(intent);
                });

                // Long press to archive/trash
                holder.itemView.setOnLongClickListener(v -> {
                    new android.app.AlertDialog.Builder(requireContext())
                        .setItems(new String[]{"Archive", "Move to Trash", "Cancel"}, (d, which) -> {
                            if (which == 0) archiveNote(note);
                            else if (which == 1) trashNote(note);
                        }).show();
                    return true;
                });
            }
        }

        @Override
        public int getItemCount() { return displayItems.size(); }

        private int dpToPx(int dp) {
            return (int) (dp * getResources().getDisplayMetrics().density);
        }

        class NoteViewHolder extends RecyclerView.ViewHolder {
            TextView txtTitle, txtDate, txtPreviewLines;
            ImageView imgPinned, imgLock, imgThumbnail;

            NoteViewHolder(@NonNull View itemView) {
                super(itemView);
                txtTitle = itemView.findViewById(R.id.txtNoteTitle);
                txtDate = itemView.findViewById(R.id.txtNoteDate);
                txtPreviewLines = itemView.findViewById(R.id.txtPreviewLines);
                imgPinned = itemView.findViewById(R.id.imgPinned);
                imgLock = itemView.findViewById(R.id.imgLock);
                imgThumbnail = itemView.findViewById(R.id.imgThumbnail);
            }
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView tv;
            HeaderViewHolder(@NonNull TextView itemView) { super(itemView); tv = itemView; }
        }
    }
}
