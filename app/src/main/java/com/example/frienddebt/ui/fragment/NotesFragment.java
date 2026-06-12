package com.example.frienddebt.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Note;
import com.example.frienddebt.ui.AddEditNoteActivity;
import com.example.frienddebt.ui.GlobalSearchActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesFragment extends Fragment {

    private LinearLayout layoutEmptyState;
    private RecyclerView rvNotes;
    private FloatingActionButton fabAddNote;

    private TextView chipAll, chipArchive, chipTrash, chipPinned, chipChecklist, chipImages;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration notesListener;

    private List<Note> allNotes = new ArrayList<>();
    private List<Object> displayItems = new ArrayList<>();
    private NotesAdapter adapter;

    private String activeFilter = "ALL";
    private String searchQuery = "";

    private ActionMode actionMode;
    private boolean isMultiSelectMode = false;
    private List<Note> selectedNotes = new ArrayList<>();

    private android.widget.ImageButton btnSearch, btnMenu;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notes, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        rvNotes = view.findViewById(R.id.rvNotes);
        fabAddNote = view.findViewById(R.id.fabAddNote);

        btnSearch = view.findViewById(R.id.btnSearch);
        btnMenu = view.findViewById(R.id.btnMenu);

        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> startActivity(new Intent(requireContext(), GlobalSearchActivity.class)));
        }

        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), btnMenu);
                popup.getMenu().add("Select Notes");
                popup.setOnMenuItemClickListener(item -> {
                    if ("Select Notes".equals(item.getTitle().toString())) {
                        startActionMode();
                    }
                    return true;
                });
                popup.show();
            });
        }

        chipAll = view.findViewById(R.id.chipAll);
        chipArchive = view.findViewById(R.id.chipArchive);
        chipTrash = view.findViewById(R.id.chipTrash);
        chipPinned = view.findViewById(R.id.chipPinned);
        chipChecklist = view.findViewById(R.id.chipChecklist);
        chipImages = view.findViewById(R.id.chipImages);

        rvNotes.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        adapter = new NotesAdapter();
        rvNotes.setAdapter(adapter);
        
        setupFilters();

        fabAddNote.setOnClickListener(v -> {
            Animation pop = AnimationUtils.loadAnimation(requireContext(), R.anim.button_pop);
            v.startAnimation(pop);
            startActivity(new Intent(requireActivity(), AddEditNoteActivity.class));
        });

        return view;
    }

    private void setupFilters() {
        chipAll.setOnClickListener(v -> setFilter("ALL", chipAll));
        chipPinned.setOnClickListener(v -> setFilter("PINNED", chipPinned));
        chipChecklist.setOnClickListener(v -> setFilter("CHECKLIST", chipChecklist));
        chipImages.setOnClickListener(v -> setFilter("IMAGES", chipImages));
        chipArchive.setOnClickListener(v -> setFilter("ARCHIVE", chipArchive));
        chipTrash.setOnClickListener(v -> setFilter("TRASH", chipTrash));
    }

    private void setFilter(String filter, TextView activeChip) {
        activeFilter = filter;
        resetChipStyles();
        activeChip.setBackgroundResource(R.drawable.rounded_button);
        activeChip.setTextColor(getResources().getColor(R.color.on_primary));
        applyFilter();
    }

    private void resetChipStyles() {
        TextView[] chips = {chipAll, chipPinned, chipChecklist, chipImages, chipArchive, chipTrash};
        for (TextView chip : chips) {
            chip.setBackgroundResource(R.drawable.chip_background);
            chip.setTextColor(getResources().getColor(R.color.text_secondary));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        loadNotes();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (notesListener != null) {
            notesListener.remove();
            notesListener = null;
        }
    }

    public void loadNotes() {
        if (auth == null || db == null) return;
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        if (notesListener != null) {
            notesListener.remove();
        }

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

    private void applyFilter() {
        List<Note> filteredNotes = new ArrayList<>();

        for (Note n : allNotes) {
            boolean matchesFilter = false;
            switch (activeFilter) {
                case "ALL":
                    matchesFilter = !n.isArchived() && !n.isDeleted();
                    break;
                case "PINNED":
                    matchesFilter = n.isPinned() && !n.isArchived() && !n.isDeleted();
                    break;
                case "CHECKLIST":
                    matchesFilter = "CHECKLIST".equals(n.getType()) && !n.isArchived() && !n.isDeleted();
                    break;
                case "IMAGES":
                    matchesFilter = ("IMAGE".equals(n.getType()) || (n.getImageUrl() != null && !n.getImageUrl().isEmpty()) || (n.getImageUrls() != null && !n.getImageUrls().isEmpty())) && !n.isArchived() && !n.isDeleted();
                    break;
                case "ARCHIVE":
                    matchesFilter = n.isArchived() && !n.isDeleted();
                    break;
                case "TRASH":
                    matchesFilter = n.isDeleted();
                    break;
            }

            if (matchesFilter) {
                filteredNotes.add(n);
            }
        }

        // Prepare display items (inject headers if needed)
        displayItems.clear();
        
        List<Note> pinned = new ArrayList<>();
        List<Note> unpinned = new ArrayList<>();
        
        for (Note n : filteredNotes) {
            if (n.isPinned()) pinned.add(n);
            else unpinned.add(n);
        }

        if (!pinned.isEmpty() && !unpinned.isEmpty() && activeFilter.equals("ALL")) {
            displayItems.add("PINNED");
            displayItems.addAll(pinned);
            displayItems.add("ALL NOTES");
            displayItems.addAll(unpinned);
        } else {
            // Sort by date descending
            filteredNotes.sort((n1, n2) -> Long.compare(n2.getUpdatedAt(), n1.getUpdatedAt()));
            displayItems.addAll(filteredNotes);
        }

        adapter.notifyDataSetChanged();
        rvNotes.scheduleLayoutAnimation();

        if (displayItems.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            rvNotes.setVisibility(View.GONE);
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            rvNotes.setVisibility(View.VISIBLE);
        }
    }

    // ======================== ACTION MODE ========================

    private void startActionMode() {
        if (actionMode == null) {
            actionMode = requireActivity().startActionMode(actionModeCallback);
        }
    }

    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            isMultiSelectMode = true;
            mode.getMenuInflater().inflate(R.menu.menu_note_selection, menu);
            
            // Adjust menu based on active filter
            if (activeFilter.equals("TRASH")) {
                menu.findItem(R.id.action_pin).setVisible(false);
                menu.findItem(R.id.action_archive).setVisible(false);
                menu.findItem(R.id.action_trash).setVisible(false);
                menu.findItem(R.id.action_restore).setVisible(true);
            } else if (activeFilter.equals("ARCHIVE")) {
                menu.findItem(R.id.action_archive).setTitle("Unarchive");
            }
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (selectedNotes.isEmpty()) return false;

            WriteBatch batch = db.batch();
            String userId = auth.getCurrentUser().getUid();
            
            if (item.getItemId() == R.id.action_trash) {
                for (Note n : selectedNotes) {
                    batch.update(db.collection("users").document(userId).collection("notes").document(n.getId()), "isDeleted", true);
                }
                batch.commit().addOnSuccessListener(a -> Toast.makeText(requireContext(), "Moved to trash", Toast.LENGTH_SHORT).show());
                mode.finish();
                return true;
            } else if (item.getItemId() == R.id.action_archive) {
                boolean isUnarchiving = activeFilter.equals("ARCHIVE");
                for (Note n : selectedNotes) {
                    batch.update(db.collection("users").document(userId).collection("notes").document(n.getId()), "isArchived", !isUnarchiving);
                }
                batch.commit().addOnSuccessListener(a -> Toast.makeText(requireContext(), isUnarchiving ? "Unarchived" : "Archived", Toast.LENGTH_SHORT).show());
                mode.finish();
                return true;
            } else if (item.getItemId() == R.id.action_pin) {
                for (Note n : selectedNotes) {
                    batch.update(db.collection("users").document(userId).collection("notes").document(n.getId()), "isPinned", true);
                }
                batch.commit().addOnSuccessListener(a -> Toast.makeText(requireContext(), "Pinned", Toast.LENGTH_SHORT).show());
                mode.finish();
                return true;
            } else if (item.getItemId() == R.id.action_restore) {
                for (Note n : selectedNotes) {
                    batch.update(db.collection("users").document(userId).collection("notes").document(n.getId()), "isDeleted", false);
                }
                batch.commit().addOnSuccessListener(a -> Toast.makeText(requireContext(), "Restored", Toast.LENGTH_SHORT).show());
                mode.finish();
                return true;
            } else if (item.getItemId() == R.id.action_delete_forever) {
                for (Note n : selectedNotes) {
                    batch.delete(db.collection("users").document(userId).collection("notes").document(n.getId()));
                }
                batch.commit().addOnSuccessListener(a -> Toast.makeText(requireContext(), "Deleted permanently", Toast.LENGTH_SHORT).show());
                mode.finish();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            isMultiSelectMode = false;
            selectedNotes.clear();
            actionMode = null;
            adapter.notifyDataSetChanged();
        }
    };

    // ======================== ADAPTER ========================

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_NOTE = 1;

    private class NotesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            if (displayItems.get(position) instanceof String) {
                return TYPE_HEADER;
            }
            return TYPE_NOTE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                TextView header = new TextView(parent.getContext());
                header.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                header.setPadding(24, 32, 24, 8);
                header.setTextSize(11);
                header.setLetterSpacing(0.1f);
                header.setAllCaps(true);
                header.setTextColor(getResources().getColor(R.color.text_hint));
                return new HeaderViewHolder(header);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
                return new NoteViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == TYPE_HEADER) {
                StaggeredGridLayoutManager.LayoutParams layoutParams = new StaggeredGridLayoutManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                layoutParams.setFullSpan(true);
                holder.itemView.setLayoutParams(layoutParams);
                
                ((HeaderViewHolder) holder).txtHeader.setText((String) displayItems.get(position));
            } else {
                NoteViewHolder noteHolder = (NoteViewHolder) holder;
                Note note = (Note) displayItems.get(position);

                String title = note.getTitle();
                if (title == null || title.trim().isEmpty()) {
                    title = "Untitled Note";
                }
                noteHolder.txtTitle.setText(title);

                String content = note.getContent();
                if (content == null) content = "";
                
                if (content.contains("<") && content.contains(">")) {
                    noteHolder.txtContent.setText(android.text.Html.fromHtml(content, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim());
                } else {
                    noteHolder.txtContent.setText(content.trim());
                }

                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                noteHolder.txtDate.setText(sdf.format(new Date(note.getUpdatedAt())));

                // Color Strip mapping
                String colorStr = note.getColorCode();
                if (colorStr != null && !colorStr.isEmpty() && !colorStr.equals("#surface_primary")) {
                    int colorVal = android.graphics.Color.parseColor(colorStr);
                    noteHolder.viewColorStrip.setBackgroundColor(colorVal);
                } else {
                    noteHolder.viewColorStrip.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                }
                
                // Selection styling
                if (isMultiSelectMode && selectedNotes.contains(note)) {
                    if (noteHolder.itemView instanceof androidx.cardview.widget.CardView) {
                        ((androidx.cardview.widget.CardView) noteHolder.itemView).setCardBackgroundColor(android.graphics.Color.LTGRAY);
                        noteHolder.itemView.setAlpha(0.7f);
                    }
                } else {
                    if (noteHolder.itemView instanceof androidx.cardview.widget.CardView) {
                        int colorSurface = com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, android.graphics.Color.WHITE);
                        ((androidx.cardview.widget.CardView) noteHolder.itemView).setCardBackgroundColor(colorSurface);
                        noteHolder.itemView.setAlpha(1.0f);
                    }
                }

                if (note.isPinned()) {
                    noteHolder.imgPinned.setVisibility(View.VISIBLE);
                } else {
                    noteHolder.imgPinned.setVisibility(View.GONE);
                }

                if ("CHECKLIST".equals(note.getType())) {
                    noteHolder.imgChecklist.setVisibility(View.VISIBLE);
                } else {
                    noteHolder.imgChecklist.setVisibility(View.GONE);
                }

                if (note.getTags() != null && !note.getTags().isEmpty()) {
                    noteHolder.txtLabel.setText(note.getTags().get(0));
                    noteHolder.txtLabel.setVisibility(View.VISIBLE);
                } else {
                    noteHolder.txtLabel.setVisibility(View.GONE);
                }

                noteHolder.itemView.setOnClickListener(v -> {
                    if (isMultiSelectMode) {
                        toggleSelection(note);
                    } else {
                        Intent intent = new Intent(requireActivity(), AddEditNoteActivity.class);
                        intent.putExtra("NOTE_ID", note.getId());
                        startActivity(intent);
                    }
                });

                noteHolder.itemView.setOnLongClickListener(v -> {
                    if (!isMultiSelectMode) {
                        startActionMode();
                    }
                    toggleSelection(note);
                    return true;
                });
            }
        }

        private void toggleSelection(Note note) {
            if (selectedNotes.contains(note)) {
                selectedNotes.remove(note);
                if (selectedNotes.isEmpty() && actionMode != null) {
                    actionMode.finish();
                }
            } else {
                selectedNotes.add(note);
            }
            
            if (actionMode != null) {
                actionMode.setTitle(selectedNotes.size() + " selected");
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return displayItems.size();
        }

        class NoteViewHolder extends RecyclerView.ViewHolder {
            TextView txtTitle, txtContent, txtDate, txtLabel;
            ImageView imgPinned, imgChecklist;
            View viewColorStrip;

            public NoteViewHolder(@NonNull View itemView) {
                super(itemView);
                txtTitle = itemView.findViewById(R.id.txtNoteTitle);
                txtContent = itemView.findViewById(R.id.txtNoteContent);
                txtDate = itemView.findViewById(R.id.txtNoteDate);
                txtLabel = itemView.findViewById(R.id.txtNoteLabel);
                imgPinned = itemView.findViewById(R.id.imgPinned);
                imgChecklist = itemView.findViewById(R.id.imgChecklist);
                viewColorStrip = itemView.findViewById(R.id.viewColorStrip);
            }
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView txtHeader;
            public HeaderViewHolder(@NonNull TextView itemView) {
                super(itemView);
                txtHeader = itemView;
            }
        }
    }
}
