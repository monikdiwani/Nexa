package com.example.frienddebt.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Note;
import com.example.frienddebt.ui.AddEditNoteActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesFragment extends Fragment {

    private TextView txtEmptyNotes;
    private RecyclerView rvNotes;
    private FloatingActionButton fabAddNote;

    private TextView chipAll, chipArchive, chipTrash;
    private EditText etSearchNotes;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration notesListener;

    private List<Note> allNotes = new ArrayList<>();
    private List<Note> filteredNotes = new ArrayList<>();
    private NotesAdapter adapter;

    private String activeFilter = "ALL"; // ALL, ARCHIVE, TRASH
    private String searchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notes, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        txtEmptyNotes = view.findViewById(R.id.txtEmptyNotes);
        rvNotes = view.findViewById(R.id.rvNotes);
        fabAddNote = view.findViewById(R.id.fabAddNote);

        android.widget.ImageButton btnSearchNotes = view.findViewById(R.id.btnSearchNotes);
        if (btnSearchNotes != null) {
            btnSearchNotes.setOnClickListener(v -> startActivity(new android.content.Intent(requireContext(), com.example.frienddebt.ui.GlobalSearchActivity.class)));
        }

        chipAll = view.findViewById(R.id.chipAll);
        chipArchive = view.findViewById(R.id.chipArchive);
        chipTrash = view.findViewById(R.id.chipTrash);
        etSearchNotes = view.findViewById(R.id.etSearchNotes);

        // Grid of 2 columns
        rvNotes.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        adapter = new NotesAdapter(filteredNotes);
        rvNotes.setAdapter(adapter);
        
        setupFilters();
        setupSearch();

        fabAddNote.setOnClickListener(v -> {
            Animation pop = AnimationUtils.loadAnimation(requireContext(), R.anim.button_pop);
            v.startAnimation(pop);
            startActivity(new Intent(requireActivity(), AddEditNoteActivity.class));
        });

        return view;
    }

    private void setupFilters() {
        chipAll.setOnClickListener(v -> setFilter("ALL", chipAll));
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
        TextView[] chips = {chipAll, chipArchive, chipTrash};
        for (TextView chip : chips) {
            chip.setBackgroundResource(R.drawable.chip_background);
            chip.setTextColor(getResources().getColor(R.color.text_secondary));
        }
    }

    private void setupSearch() {
        etSearchNotes.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase().trim();
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
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
        filteredNotes.clear();

        for (Note n : allNotes) {
            boolean matchesFilter = false;
            switch (activeFilter) {
                case "ALL":
                    matchesFilter = !n.isArchived() && !n.isDeleted();
                    break;
                case "ARCHIVE":
                    matchesFilter = n.isArchived() && !n.isDeleted();
                    break;
                case "TRASH":
                    matchesFilter = n.isDeleted();
                    break;
            }

            if (!matchesFilter) continue;

            if (!searchQuery.isEmpty()) {
                boolean matchesSearch = false;
                if (n.getTitle() != null && n.getTitle().toLowerCase().contains(searchQuery)) matchesSearch = true;
                if (n.getContent() != null && n.getContent().toLowerCase().contains(searchQuery)) matchesSearch = true;
                if (n.getLabel() != null && n.getLabel().toLowerCase().contains(searchQuery)) matchesSearch = true;
                if (!matchesSearch) continue;
            }

            filteredNotes.add(n);
        }

        // Sort locally: Pinned notes first, then by updatedAt
        filteredNotes.sort((n1, n2) -> {
            if (n1.isPinned() && !n2.isPinned()) return -1;
            if (!n1.isPinned() && n2.isPinned()) return 1;
            return Long.compare(n2.getUpdatedAt(), n1.getUpdatedAt());
        });
        
        adapter.notifyDataSetChanged();

        if (filteredNotes.isEmpty()) {
            txtEmptyNotes.setVisibility(View.VISIBLE);
            rvNotes.setVisibility(View.GONE);
        } else {
            txtEmptyNotes.setVisibility(View.GONE);
            rvNotes.setVisibility(View.VISIBLE);
        }
    }

    // Grid Adapter
    private class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.ViewHolder> {
        private final List<Note> list;

        public NotesAdapter(List<Note> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Note note = list.get(position);

            String title = note.getTitle();
            if (title == null || title.trim().isEmpty()) {
                title = getString(R.string.untitled_note);
            }
            holder.txtTitle.setText(title);

            String content = note.getContent();
            if (content == null) content = "";
            io.noties.markwon.Markwon markwon = io.noties.markwon.Markwon.builder(holder.itemView.getContext())
                .usePlugin(io.noties.markwon.ext.tasklist.TaskListPlugin.create(holder.itemView.getContext()))
                .build();
            markwon.setMarkdown(holder.txtContent, content);

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            holder.txtDate.setText(sdf.format(new Date(note.getUpdatedAt())));

            // Custom color background
            String colorStr = note.getColorCode();
            if (colorStr == null || colorStr.isEmpty()) colorStr = "#FFFFFF";
            int colorVal = android.graphics.Color.parseColor(colorStr);
            android.graphics.drawable.Drawable background = holder.itemView.getBackground();
            if (background instanceof android.graphics.drawable.GradientDrawable) {
                android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) background.mutate();
                gd.setColor(colorVal);
            } else {
                holder.itemView.setBackgroundColor(colorVal);
            }
            
            // Pin Icon
            if (note.isPinned()) {
                holder.imgPinned.setVisibility(View.VISIBLE);
            } else {
                holder.imgPinned.setVisibility(View.GONE);
            }
            
            // Image Preview
            if (note.getImageUrl() != null && !note.getImageUrl().isEmpty()) {
                holder.imgNoteAttachment.setVisibility(View.VISIBLE);
                try {
                    holder.imgNoteAttachment.setImageURI(android.net.Uri.parse(note.getImageUrl()));
                } catch (Exception e) {
                    holder.imgNoteAttachment.setVisibility(View.GONE);
                }
            } else {
                holder.imgNoteAttachment.setVisibility(View.GONE);
            }
            
            // Label
            String label = note.getLabel();
            if (label != null && !label.trim().isEmpty()) {
                holder.txtLabel.setText(label);
                holder.txtLabel.setVisibility(View.VISIBLE);
            } else {
                holder.txtLabel.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(requireActivity(), AddEditNoteActivity.class);
                intent.putExtra("NOTE_ID", note.getId());
                startActivity(intent);
            });

            holder.itemView.setOnLongClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(holder.itemView.getContext(), holder.itemView);
                popup.getMenu().add("Edit");
                if (activeFilter.equals("TRASH")) {
                    popup.getMenu().add("Restore");
                    popup.getMenu().add("Delete Permanently");
                } else {
                    popup.getMenu().add(note.isArchived() ? "Unarchive" : "Archive");
                    popup.getMenu().add("Move to Recycle Bin");
                    popup.getMenu().add("Share");
                }
                
                popup.setOnMenuItemClickListener(item -> {
                    String titleSelected = item.getTitle().toString();
                    if ("Edit".equals(titleSelected)) {
                        Intent intent = new Intent(requireActivity(), AddEditNoteActivity.class);
                        intent.putExtra("NOTE_ID", note.getId());
                        startActivity(intent);
                    } else if ("Move to Recycle Bin".equals(titleSelected)) {
                        updateNoteStatus(note.getId(), "isDeleted", true);
                    } else if ("Archive".equals(titleSelected)) {
                        updateNoteStatus(note.getId(), "isArchived", true);
                    } else if ("Unarchive".equals(titleSelected)) {
                        updateNoteStatus(note.getId(), "isArchived", false);
                    } else if ("Restore".equals(titleSelected)) {
                        updateNoteStatus(note.getId(), "isDeleted", false);
                    } else if ("Delete Permanently".equals(titleSelected)) {
                        showDeleteDialog(note);
                    } else if ("Share".equals(titleSelected)) {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, note.getTitle());
                        shareIntent.putExtra(Intent.EXTRA_TEXT, note.getContent());
                        startActivity(Intent.createChooser(shareIntent, "Share Note"));
                    }
                    return true;
                });
                popup.show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        private void updateNoteStatus(String noteId, String field, boolean value) {
            if (auth.getCurrentUser() != null) {
                db.collection("users")
                        .document(auth.getCurrentUser().getUid())
                        .collection("notes")
                        .document(noteId)
                        .update(field, value);
            }
        }

        private void showDeleteDialog(Note note) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Permanently")
                    .setMessage("Are you sure you want to permanently delete this note?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (auth.getCurrentUser() != null) {
                            db.collection("users")
                                    .document(auth.getCurrentUser().getUid())
                                    .collection("notes")
                                    .document(note.getId())
                                    .delete();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtTitle, txtContent, txtDate, txtLabel;
            android.widget.ImageView imgPinned, imgNoteAttachment;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtTitle = itemView.findViewById(R.id.txtNoteTitle);
                txtContent = itemView.findViewById(R.id.txtNoteContent);
                txtDate = itemView.findViewById(R.id.txtNoteDate);
                txtLabel = itemView.findViewById(R.id.txtNoteLabel);
                imgPinned = itemView.findViewById(R.id.imgPinned);
                imgNoteAttachment = itemView.findViewById(R.id.imgNoteAttachment);
            }
        }
    }
}
