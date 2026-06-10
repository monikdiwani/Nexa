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

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class NotesFragment extends Fragment {

    private TextView txtEmptyNotes;
    private RecyclerView rvNotes;
    private FloatingActionButton fabAddNote;

    private TextView chipAll, chipArchive, chipTrash, chipPinned, chipChecklist, chipImages;
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

        android.widget.ImageButton btnExport = view.findViewById(R.id.btnExport);
        android.widget.ImageButton btnImport = view.findViewById(R.id.btnImport);
        android.widget.ImageButton btnSearch = view.findViewById(R.id.btnSearch);
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> startActivity(new android.content.Intent(requireContext(), com.example.frienddebt.ui.GlobalSearchActivity.class)));
        }
        if (btnExport != null) {
            btnExport.setOnClickListener(v -> exportNotes());
        }
        if (btnImport != null) {
            btnImport.setOnClickListener(v -> importNotes());
        }

        chipAll = view.findViewById(R.id.chipAll);
        chipArchive = view.findViewById(R.id.chipArchive);
        chipTrash = view.findViewById(R.id.chipTrash);
        chipPinned = view.findViewById(R.id.chipPinned);
        chipChecklist = view.findViewById(R.id.chipChecklist);
        chipImages = view.findViewById(R.id.chipImages);
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

    private void setChipActive(TextView active) {
        chipAll.setBackgroundResource(R.drawable.chip_background);
        chipArchive.setBackgroundResource(R.drawable.chip_background);
        chipTrash.setBackgroundResource(R.drawable.chip_background);
        chipPinned.setBackgroundResource(R.drawable.chip_background);
        chipChecklist.setBackgroundResource(R.drawable.chip_background);
        chipImages.setBackgroundResource(R.drawable.chip_background);

        chipAll.setTextColor(getResources().getColor(R.color.text_secondary));
        chipArchive.setTextColor(getResources().getColor(R.color.text_secondary));
        chipTrash.setTextColor(getResources().getColor(R.color.text_secondary));
        chipPinned.setTextColor(getResources().getColor(R.color.text_secondary));
        chipChecklist.setTextColor(getResources().getColor(R.color.text_secondary));
        chipImages.setTextColor(getResources().getColor(R.color.text_secondary));

        active.setBackgroundResource(R.drawable.rounded_button);
        active.setTextColor(getResources().getColor(android.R.color.white));
    }
    
    // ====================== BACKUP & RESTORE (JSON) ======================

    private final ActivityResultLauncher<String> createBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    writeNotesToJsonFile(uri);
                }
            });

    private final ActivityResultLauncher<String[]> openBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    readNotesFromJsonFile(uri);
                }
            });

    private void exportNotes() {
        if (allNotes.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "No notes to export", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        createBackupLauncher.launch("nexa_notes_backup.json");
    }

    private void importNotes() {
        openBackupLauncher.launch(new String[]{"application/json"});
    }

    private void writeNotesToJsonFile(android.net.Uri uri) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Note note : allNotes) {
                JSONObject jsonNote = new JSONObject(note.toFirestoreMap());
                jsonArray.put(jsonNote);
            }

            OutputStream os = requireContext().getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(jsonArray.toString(4).getBytes());
                os.close();
                android.widget.Toast.makeText(requireContext(), "Backup Exported Successfully!", android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(requireContext(), "Export Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void readNotesFromJsonFile(android.net.Uri uri) {
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is == null) return;
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            is.close();

            JSONArray jsonArray = new JSONArray(sb.toString());
            if (jsonArray.length() == 0) return;

            android.app.ProgressDialog pd = new android.app.ProgressDialog(requireContext());
            pd.setMessage("Restoring Notes...");
            pd.show();

            com.google.firebase.firestore.WriteBatch batch = db.batch();
            String userId = auth.getCurrentUser().getUid();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonNote = jsonArray.getJSONObject(i);
                String newId = db.collection("users").document(userId).collection("notes").document().getId();
                
                // Convert JSON to Map
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                java.util.Iterator<String> keys = jsonNote.keys();
                while(keys.hasNext()) {
                    String key = keys.next();
                    Object value = jsonNote.get(key);
                    
                    // Special handling for JSONArrays
                    if (value instanceof JSONArray) {
                        JSONArray array = (JSONArray) value;
                        java.util.List<String> list = new java.util.ArrayList<>();
                        for(int j=0; j<array.length(); j++) {
                            list.add(array.getString(j));
                        }
                        map.put(key, list);
                    } else {
                        map.put(key, value);
                    }
                }
                
                com.google.firebase.firestore.DocumentReference docRef = db.collection("users").document(userId).collection("notes").document(newId);
                batch.set(docRef, map);
            }

            batch.commit()
                .addOnSuccessListener(aVoid -> {
                    pd.dismiss();
                    android.widget.Toast.makeText(requireContext(), "Backup Restored!", android.widget.Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    pd.dismiss();
                    android.widget.Toast.makeText(requireContext(), "Restore Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                });

        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(requireContext(), "Import Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
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
                case "PINNED":
                    matchesFilter = n.isPinned() && !n.isArchived() && !n.isDeleted();
                    break;
                case "CHECKLIST":
                    matchesFilter = "CHECKLIST".equals(n.getType()) && !n.isArchived() && !n.isDeleted();
                    break;
                case "IMAGES":
                    matchesFilter = ("IMAGE".equals(n.getType()) || (n.getImageUrl() != null && !n.getImageUrl().isEmpty())) && !n.isArchived() && !n.isDeleted();
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
        rvNotes.scheduleLayoutAnimation();

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
            if (holder.itemView instanceof androidx.cardview.widget.CardView) {
                ((androidx.cardview.widget.CardView) holder.itemView).setCardBackgroundColor(colorVal);
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

            android.view.View.OnClickListener clickListener = v -> {
                Intent intent = new Intent(requireActivity(), AddEditNoteActivity.class);
                intent.putExtra("NOTE_ID", note.getId());
                startActivity(intent);
            };

            holder.itemView.setOnClickListener(clickListener);
            holder.txtContent.setOnClickListener(clickListener);
            holder.txtTitle.setOnClickListener(clickListener);

            android.view.View.OnLongClickListener longClickListener = v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(holder.itemView.getContext(), holder.itemView);
                popup.getMenu().add("Edit");
                if (activeFilter.equals("TRASH")) {
                    popup.getMenu().add("Restore");
                    popup.getMenu().add("Delete Permanently");
                } else {
                    popup.getMenu().add("Create Task");
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
                    } else if ("Create Task".equals(titleSelected)) {
                        Intent intent = new Intent(requireActivity(), com.example.frienddebt.ui.AddTaskActivity.class);
                        intent.putExtra("LINKED_TITLE", note.getTitle() != null && !note.getTitle().isEmpty() ? note.getTitle() : "Note");
                        intent.putExtra("LINKED_ID", note.getId());
                        intent.putExtra("LINKED_TYPE", "NOTE");
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
            };

            holder.itemView.setOnLongClickListener(longClickListener);
            holder.txtContent.setOnLongClickListener(longClickListener);
            holder.txtTitle.setOnLongClickListener(longClickListener);
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
