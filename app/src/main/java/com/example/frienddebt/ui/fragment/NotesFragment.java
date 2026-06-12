package com.example.frienddebt.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesFragment extends Fragment {

    private LinearLayout layoutEmptyState;
    private RecyclerView rvNotes;
    private FloatingActionButton fabAddNote;
    
    private TextView txtHeaderSubtitle;
    private ImageButton btnSearch, btnMenu, btnMore;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration notesListener;

    private List<Note> allNotes = new ArrayList<>();
    private List<Object> displayItems = new ArrayList<>();
    private NotesAdapter adapter;

    private ActionMode actionMode;
    private boolean isMultiSelectMode = false;
    private List<Note> selectedNotes = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notes, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        rvNotes = view.findViewById(R.id.rvNotes);
        fabAddNote = view.findViewById(R.id.fabAddNote);
        
        txtHeaderSubtitle = view.findViewById(R.id.txtHeaderSubtitle);
        btnSearch = view.findViewById(R.id.btnSearch);
        btnMenu = view.findViewById(R.id.btnMenu);
        btnMore = view.findViewById(R.id.btnMore);

        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> startActivity(new Intent(requireContext(), GlobalSearchActivity.class)));
        }

        if (btnMore != null) {
            btnMore.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), btnMore);
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

        rvNotes.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NotesAdapter();
        rvNotes.setAdapter(adapter);

        fabAddNote.setOnClickListener(v -> {
            Animation pop = AnimationUtils.loadAnimation(requireContext(), R.anim.button_pop);
            v.startAnimation(pop);
            startActivity(new Intent(requireActivity(), AddEditNoteActivity.class));
        });

        return view;
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
                    applyGrouping();
                });
    }

    private void applyGrouping() {
        displayItems.clear();
        
        List<Note> validNotes = new ArrayList<>();
        for (Note n : allNotes) {
            if (!n.isDeleted()) {
                validNotes.add(n);
            }
        }
        
        txtHeaderSubtitle.setText(validNotes.size() + " notes");

        validNotes.sort((n1, n2) -> Long.compare(n2.getUpdatedAt(), n1.getUpdatedAt()));

        Calendar todayCal = Calendar.getInstance();
        Calendar yesterdayCal = Calendar.getInstance();
        yesterdayCal.add(Calendar.DAY_OF_YEAR, -1);
        
        String currentHeader = "";
        
        for (Note note : validNotes) {
            Calendar noteCal = Calendar.getInstance();
            noteCal.setTimeInMillis(note.getUpdatedAt());
            
            String headerTitle;
            
            if (isSameDay(noteCal, todayCal)) {
                headerTitle = "Today";
            } else if (isSameDay(noteCal, yesterdayCal)) {
                headerTitle = "Yesterday";
            } else if (noteCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)) {
                SimpleDateFormat monthFormat = new SimpleDateFormat("MMM", Locale.getDefault());
                headerTitle = monthFormat.format(noteCal.getTime());
            } else {
                SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
                headerTitle = yearFormat.format(noteCal.getTime());
            }
            
            if (!headerTitle.equals(currentHeader)) {
                displayItems.add(headerTitle);
                currentHeader = headerTitle;
            }
            displayItems.add(note);
        }

        adapter.notifyDataSetChanged();
        rvNotes.scheduleLayoutAnimation();

        if (validNotes.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            rvNotes.setVisibility(View.GONE);
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            rvNotes.setVisibility(View.VISIBLE);
        }
    }
    
    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
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
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

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
                for (Note n : selectedNotes) {
                    batch.update(db.collection("users").document(userId).collection("notes").document(n.getId()), "isArchived", true);
                }
                batch.commit().addOnSuccessListener(a -> Toast.makeText(requireContext(), "Archived", Toast.LENGTH_SHORT).show());
                mode.finish();
                return true;
            } else if (item.getItemId() == R.id.action_pin) {
                for (Note n : selectedNotes) {
                    batch.update(db.collection("users").document(userId).collection("notes").document(n.getId()), "isPinned", true);
                }
                batch.commit().addOnSuccessListener(a -> Toast.makeText(requireContext(), "Pinned", Toast.LENGTH_SHORT).show());
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
            if (displayItems.get(position) instanceof String) return TYPE_HEADER;
            return TYPE_NOTE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                TextView header = new TextView(parent.getContext());
                header.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                header.setPadding(24, 32, 24, 8);
                header.setTextSize(14);
                header.setLetterSpacing(0.05f);
                header.setAllCaps(false);
                header.setTypeface(null, android.graphics.Typeface.BOLD);
                header.setTextColor(android.graphics.Color.WHITE);
                return new HeaderViewHolder(header);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_samsung, parent, false);
                return new NoteViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == TYPE_HEADER) {
                ((HeaderViewHolder) holder).txtHeader.setText((String) displayItems.get(position));
            } else {
                NoteViewHolder noteHolder = (NoteViewHolder) holder;
                Note note = (Note) displayItems.get(position);

                String title = note.getTitle();
                if (title == null || title.trim().isEmpty()) {
                    title = "Untitled";
                }
                noteHolder.txtTitle.setText(title);

                String content = note.getContent();
                if (content == null) content = "";
                
                String plainContent;
                if (content.contains("<") && content.contains(">")) {
                    plainContent = Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY).toString().trim();
                } else {
                    plainContent = content.trim();
                }
                noteHolder.txtPreview.setText(plainContent);

                Calendar noteCal = Calendar.getInstance();
                noteCal.setTimeInMillis(note.getUpdatedAt());
                Calendar todayCal = Calendar.getInstance();
                
                if (isSameDay(noteCal, todayCal)) {
                    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    noteHolder.txtDate.setText(timeFormat.format(noteCal.getTime()));
                } else {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());
                    noteHolder.txtDate.setText(dateFormat.format(noteCal.getTime()));
                }

                if (isMultiSelectMode && selectedNotes.contains(note)) {
                    noteHolder.itemView.setBackgroundColor(android.graphics.Color.parseColor("#333333"));
                } else {
                    noteHolder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                }

                if (note.isPinned()) {
                    noteHolder.imgPinned.setVisibility(View.VISIBLE);
                } else {
                    noteHolder.imgPinned.setVisibility(View.GONE);
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
                if (selectedNotes.isEmpty() && actionMode != null) actionMode.finish();
            } else {
                selectedNotes.add(note);
            }
            if (actionMode != null) actionMode.setTitle(selectedNotes.size() + " selected");
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return displayItems.size();
        }

        class NoteViewHolder extends RecyclerView.ViewHolder {
            TextView txtTitle, txtDate, txtPreview;
            ImageView imgPinned, imgLock;

            public NoteViewHolder(@NonNull View itemView) {
                super(itemView);
                txtTitle = itemView.findViewById(R.id.txtNoteTitle);
                txtDate = itemView.findViewById(R.id.txtNoteDate);
                txtPreview = itemView.findViewById(R.id.txtPreview);
                imgPinned = itemView.findViewById(R.id.imgPinned);
                imgLock = itemView.findViewById(R.id.imgLock);
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
