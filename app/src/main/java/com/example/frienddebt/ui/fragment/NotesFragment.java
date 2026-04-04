package com.example.frienddebt.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

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

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration notesListener;

    private List<Note> notes = new ArrayList<>();
    private NotesAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notes, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        txtEmptyNotes = view.findViewById(R.id.txtEmptyNotes);
        rvNotes = view.findViewById(R.id.rvNotes);
        fabAddNote = view.findViewById(R.id.fabAddNote);

        // Grid of 2 columns
        rvNotes.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        adapter = new NotesAdapter(notes);
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
                    notes.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        notes.add(Note.fromDocument(doc));
                    }
                    adapter.notifyDataSetChanged();

                    if (notes.isEmpty()) {
                        txtEmptyNotes.setVisibility(View.VISIBLE);
                        rvNotes.setVisibility(View.GONE);
                    } else {
                        txtEmptyNotes.setVisibility(View.GONE);
                        rvNotes.setVisibility(View.VISIBLE);
                    }
                });
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
            holder.txtContent.setText(content != null ? content : "");

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            holder.txtDate.setText(sdf.format(new Date(note.getUpdatedAt())));

            // Dynamic pastel background color based on ID hash code
            int[] pastelColors = {
                R.color.note_bg_1,
                R.color.note_bg_2,
                R.color.note_bg_3,
                R.color.note_bg_4,
                R.color.note_bg_5,
                R.color.note_bg_6
            };
            int colorIndex = Math.abs(note.getId() != null ? note.getId().hashCode() : position) % pastelColors.length;
            int colorVal = androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), pastelColors[colorIndex]);
            android.graphics.drawable.Drawable background = holder.itemView.getBackground();
            if (background instanceof android.graphics.drawable.GradientDrawable) {
                android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) background.mutate();
                gd.setColor(colorVal);
            } else {
                holder.itemView.setBackgroundColor(colorVal);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(requireActivity(), AddEditNoteActivity.class);
                intent.putExtra("NOTE_ID", note.getId());
                startActivity(intent);
            });

            holder.itemView.setOnLongClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(holder.itemView.getContext(), holder.itemView);
                popup.getMenu().add("Edit");
                popup.getMenu().add("Delete");
                popup.getMenu().add("Share");
                popup.setOnMenuItemClickListener(item -> {
                    String titleSelected = item.getTitle().toString();
                    if ("Edit".equals(titleSelected)) {
                        Intent intent = new Intent(requireActivity(), AddEditNoteActivity.class);
                        intent.putExtra("NOTE_ID", note.getId());
                        startActivity(intent);
                    } else if ("Delete".equals(titleSelected)) {
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

        private void showDeleteDialog(Note note) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Note")
                    .setMessage("Are you sure you want to delete this note?")
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
            TextView txtTitle, txtContent, txtDate;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtTitle = itemView.findViewById(R.id.txtNoteTitle);
                txtContent = itemView.findViewById(R.id.txtNoteContent);
                txtDate = itemView.findViewById(R.id.txtNoteDate);
            }
        }
    }
}
