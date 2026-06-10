package com.example.frienddebt.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.example.frienddebt.utils.StatusBarUtil;

public class TasksActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextView chipAll, chipToday, chipWeek, chipCompleted, chipArchived, txtEmptyTasks;
    private RecyclerView rvTasks;
    private FloatingActionButton fabAddTask;
    private android.widget.EditText edtSearchTask;
    private ImageButton btnSortTasks;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration tasksListener;

    private List<Task> allTasks = new ArrayList<>();
    private List<Task> filteredTasks = new ArrayList<>();
    private TasksAdapter adapter;

    private String activeFilter = "ALL"; // ALL, TODAY, WEEK, COMPLETED
    private String sortMode = "PRIORITY"; // PRIORITY or DUE_DATE

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasks);
        StatusBarUtil.applyStatusBarPadding(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind Views
        btnBack = findViewById(R.id.btnBack);
        chipAll = findViewById(R.id.chipAll);
        chipToday = findViewById(R.id.chipToday);
        chipWeek = findViewById(R.id.chipWeek);
        chipCompleted = findViewById(R.id.chipCompleted);
        chipArchived = findViewById(R.id.chipArchived);
        txtEmptyTasks = findViewById(R.id.txtEmptyTasks);
        rvTasks = findViewById(R.id.rvTasks);
        fabAddTask = findViewById(R.id.fabAddTask);
        
        android.widget.ImageButton btnSearchGlobal = findViewById(R.id.btnSearchGlobal);
        if (btnSearchGlobal != null) {
            btnSearchGlobal.setOnClickListener(v -> startActivity(new android.content.Intent(this, com.example.frienddebt.ui.GlobalSearchActivity.class)));
        }

        edtSearchTask = findViewById(R.id.edtSearchTask);
        btnSortTasks = findViewById(R.id.btnSortTasks);

        btnBack.setOnClickListener(v -> finish());
        
        edtSearchTask.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
        
        btnSortTasks.setOnClickListener(v -> {
            new AlertDialog.Builder(TasksActivity.this)
                .setTitle("Sort Tasks")
                .setItems(new String[]{"By Priority (High -> Low)", "By Date (Soonest first)"}, (dialog, which) -> {
                    if (which == 0) {
                        sortMode = "PRIORITY";
                    } else {
                        sortMode = "DUE_DATE";
                    }
                    Toast.makeText(TasksActivity.this, "Sort applied", Toast.LENGTH_SHORT).show();
                    applyFilter();
                })
                .show();
        });

        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TasksAdapter(filteredTasks);
        rvTasks.setAdapter(adapter);

        setupFilters();

        // Spring animations
        com.example.frienddebt.utils.SpringAnimationUtil.applySpringEffect(fabAddTask);

        fabAddTask.setOnClickListener(v -> {
            startActivity(new Intent(TasksActivity.this, AddTaskActivity.class));
        });

        loadTasks();
        setupSwipeActions();
    }

    private void setupFilters() {
        chipAll.setOnClickListener(v -> setFilter("ALL", chipAll));
        chipToday.setOnClickListener(v -> setFilter("TODAY", chipToday));
        chipWeek.setOnClickListener(v -> setFilter("WEEK", chipWeek));
        chipCompleted.setOnClickListener(v -> setFilter("COMPLETED", chipCompleted));
        chipArchived.setOnClickListener(v -> setFilter("ARCHIVED", chipArchived));
    }

    private void setFilter(String filter, TextView activeChip) {
        activeFilter = filter;
        resetChipStyles();
        activeChip.setBackgroundResource(R.drawable.rounded_button);
        activeChip.setTextColor(getResources().getColor(R.color.on_primary));
        applyFilter();
    }

    private void resetChipStyles() {
        TextView[] chips = {chipAll, chipToday, chipWeek, chipCompleted, chipArchived};
        for (TextView chip : chips) {
            chip.setBackgroundResource(R.drawable.chip_background);
            chip.setTextColor(getResources().getColor(R.color.text_secondary));
        }
    }

    private void loadTasks() {
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        tasksListener = db.collection("users")
                .document(userId)
                .collection("tasks")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;
                    allTasks.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        allTasks.add(Task.fromDocument(doc));
                    }
                    applyFilter();
                });
    }

    private void applyFilter() {
        filteredTasks.clear();

        Calendar cal = Calendar.getInstance();
        long now = cal.getTimeInMillis();
        String query = "";
        if (edtSearchTask != null) {
            query = edtSearchTask.getText().toString().trim().toLowerCase();
        }

        List<Task> incomplete = new ArrayList<>();
        List<Task> completed = new ArrayList<>();

        for (Task task : allTasks) {
            // Search Text Filter
            if (!query.isEmpty()) {
                boolean textMatch = task.getTitle().toLowerCase().contains(query) || 
                                   (task.getDescription() != null && task.getDescription().toLowerCase().contains(query));
                if (!textMatch) continue;
            }
            
            boolean matches = false;
            
            // If the filter is ARCHIVED, show only archived tasks
            if ("ARCHIVED".equals(activeFilter)) {
                if (task.isArchived()) matches = true;
            } else {
                // If it's anything else, hide archived tasks
                if (task.isArchived()) continue;
                
                switch (activeFilter) {
                    case "ALL":
                        matches = true;
                        break;
                    case "TODAY":
                        if (!task.isCompleted() && task.getDueDate() != null) {
                            cal.set(Calendar.HOUR_OF_DAY, 0);
                            cal.set(Calendar.MINUTE, 0);
                            cal.set(Calendar.SECOND, 0);
                            cal.set(Calendar.MILLISECOND, 0);
                            long startOfDay = cal.getTimeInMillis();
                            cal.set(Calendar.HOUR_OF_DAY, 23);
                            cal.set(Calendar.MINUTE, 59);
                            cal.set(Calendar.SECOND, 59);
                            cal.set(Calendar.MILLISECOND, 999);
                            long endOfDay = cal.getTimeInMillis();
                            matches = task.getDueDate() >= startOfDay && task.getDueDate() <= endOfDay;
                        }
                        break;
                    case "WEEK":
                        if (!task.isCompleted() && task.getDueDate() != null) {
                            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                            cal.set(Calendar.HOUR_OF_DAY, 0);
                            cal.set(Calendar.MINUTE, 0);
                            cal.set(Calendar.SECOND, 0);
                            cal.set(Calendar.MILLISECOND, 0);
                            long startOfWeek = cal.getTimeInMillis();
                            cal.add(Calendar.DAY_OF_YEAR, 7);
                            long endOfWeek = cal.getTimeInMillis();
                            matches = task.getDueDate() >= startOfWeek && task.getDueDate() <= endOfWeek;
                        }
                        break;
                    case "COMPLETED":
                        matches = task.isCompleted();
                        break;
                }
            }

            if (matches) {
                if (task.isCompleted()) {
                    completed.add(task);
                } else {
                    incomplete.add(task);
                }
            }
        }

        // Sort incomplete tasks based on sortMode
        if ("DUE_DATE".equals(sortMode)) {
            incomplete.sort((t1, t2) -> {
                Long d1 = t1.getDueDate();
                Long d2 = t2.getDueDate();
                if (d1 == null && d2 == null) return 0;
                if (d1 == null) return 1;
                if (d2 == null) return -1;
                long time1 = t1.getDueTime() != null && t1.getDueTime() > 0 ? t1.getDueTime() : t1.getDueDate();
                long time2 = t2.getDueTime() != null && t2.getDueTime() > 0 ? t2.getDueTime() : t2.getDueDate();
                return Long.compare(time1, time2);
            });
        } else {
            // Sort by Priority: High -> Medium -> Low
            incomplete.sort((t1, t2) -> getPriorityValue(t2.getPriority()) - getPriorityValue(t1.getPriority()));
        }

        filteredTasks.addAll(incomplete);
        filteredTasks.addAll(completed);

        adapter.notifyDataSetChanged();
        rvTasks.scheduleLayoutAnimation();

        if (filteredTasks.isEmpty()) {
            txtEmptyTasks.setVisibility(View.VISIBLE);
            rvTasks.setVisibility(View.GONE);
        } else {
            txtEmptyTasks.setVisibility(View.GONE);
            rvTasks.setVisibility(View.VISIBLE);
        }
    }

    private int getPriorityValue(String priority) {
        if ("HIGH".equalsIgnoreCase(priority)) return 3;
        if ("MEDIUM".equalsIgnoreCase(priority)) return 2;
        return 1;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tasksListener != null) {
            tasksListener.remove();
        }
    }

    

    

    private void setupSwipeActions() {
        androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback callback = 
            new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, 
                androidx.recyclerview.widget.ItemTouchHelper.LEFT | androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
                
                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, 
                                      @NonNull RecyclerView.ViewHolder viewHolder, 
                                      @NonNull RecyclerView.ViewHolder target) {
                    return false;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                    int position = viewHolder.getAdapterPosition();
                    if (position < 0 || position >= filteredTasks.size()) {
                        adapter.notifyItemChanged(position);
                        return;
                    }
                    Task task = filteredTasks.get(position);

                    if (direction == androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
                        boolean isChecked = !task.isCompleted();
                        if (auth.getCurrentUser() != null) {
                            db.collection("users")
                                    .document(auth.getCurrentUser().getUid())
                                    .collection("tasks")
                                    .document(task.getId())
                                    .update(
                                            "isCompleted", isChecked,
                                            "completedAt", isChecked ? System.currentTimeMillis() : null
                                    )
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(TasksActivity.this, isChecked ? "Task completed!" : "Task updated!", Toast.LENGTH_SHORT).show();
                                    });
                            adapter.notifyItemChanged(position);
                        }
                    } else if (direction == androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
                        String archiveLabel = task.isArchived() ? "Unarchive Task" : "Archive Task";
                        new AlertDialog.Builder(TasksActivity.this)
                                .setTitle("Task Action")
                                .setItems(new String[]{archiveLabel, "Delete Task"}, (dialog, which) -> {
                                    if (auth.getCurrentUser() != null) {
                                        if (which == 0) {
                                            // Toggle Archive
                                            boolean newState = !task.isArchived();
                                            db.collection("users")
                                                    .document(auth.getCurrentUser().getUid())
                                                    .collection("tasks")
                                                    .document(task.getId())
                                                    .update("isArchived", newState);
                                            Toast.makeText(TasksActivity.this, newState ? "Task Archived" : "Task Unarchived", Toast.LENGTH_SHORT).show();
                                        } else {
                                            // Delete
                                            db.collection("users")
                                                    .document(auth.getCurrentUser().getUid())
                                                    .collection("tasks")
                                                    .document(task.getId())
                                                    .delete();
                                            Toast.makeText(TasksActivity.this, "Task Deleted", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                })
                                .setOnCancelListener(dialog -> {
                                    adapter.notifyItemChanged(position);
                                })
                                .show();
                      }
                  }
              };
          new androidx.recyclerview.widget.ItemTouchHelper(callback).attachToRecyclerView(rvTasks);
      }

    // Recycler Adapter
    private class TasksAdapter extends RecyclerView.Adapter<TasksAdapter.ViewHolder> {
        private final List<Task> list;

        public TasksAdapter(List<Task> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Task task = list.get(position);

            holder.txtTitle.setText(task.getTitle());

            // Checkbox logic
            holder.cbStatus.setOnCheckedChangeListener(null);
            holder.cbStatus.setChecked(task.isCompleted());

            if (task.isCompleted()) {
                holder.txtTitle.setPaintFlags(holder.txtTitle.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                holder.txtTitle.setTextColor(getResources().getColor(R.color.text_hint));
            } else {
                holder.txtTitle.setPaintFlags(holder.txtTitle.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                holder.txtTitle.setTextColor(getResources().getColor(R.color.text_primary));
            }

            holder.cbStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (auth.getCurrentUser() != null) {
                    db.collection("users")
                            .document(auth.getCurrentUser().getUid())
                            .collection("tasks")
                            .document(task.getId())
                            .update(
                                    "isCompleted", isChecked,
                                    "completedAt", isChecked ? System.currentTimeMillis() : null
                            )
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(TasksActivity.this, isChecked ? "Task completed!" : "Task updated!", Toast.LENGTH_SHORT).show();
                            });
                }
            });

            // Due Date & Time
            if (task.getDueDate() != null && task.getDueDate() > 0) {
                holder.txtDueDate.setVisibility(View.VISIBLE);
                String pattern = "MMM dd, yyyy";
                long timeToFormat = task.getDueDate();
                if (task.getDueTime() != null && task.getDueTime() > 0) {
                    pattern += " 'at' hh:mm a";
                    timeToFormat = task.getDueTime(); // Using dueTime as the full combined timestamp
                }
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
                holder.txtDueDate.setText("Due: " + sdf.format(new Date(timeToFormat)));
                
                // Overdue Check
                if (!task.isCompleted() && timeToFormat < System.currentTimeMillis()) {
                    holder.txtDueDate.setTextColor(android.graphics.Color.parseColor("#FF5252"));
                    holder.txtDueDate.setText("Overdue: " + sdf.format(new Date(timeToFormat)));
                } else {
                    holder.txtDueDate.setTextColor(getResources().getColor(R.color.text_secondary));
                }
            } else {
                holder.txtDueDate.setVisibility(View.GONE);
            }
            
            // Important Badge
            if (task.isImportant()) {
                holder.txtImportant.setVisibility(View.VISIBLE);
            } else {
                holder.txtImportant.setVisibility(View.GONE);
            }
            
            // Repeat Badge
            if (task.getRecurringPattern() != null && !"NONE".equals(task.getRecurringPattern())) {
                holder.txtRepeat.setVisibility(View.VISIBLE);
                holder.txtRepeat.setText("🔁 " + task.getRecurringPattern());
            } else {
                holder.txtRepeat.setVisibility(View.GONE);
            }
            
            // Subtask Progress
            if (task.getSubtasks() != null && !task.getSubtasks().isEmpty()) {
                holder.layoutSubtaskProgress.setVisibility(View.VISIBLE);
                int total = task.getSubtasks().size();
                int completed = 0;
                for (Task.Subtask st : task.getSubtasks()) {
                    if (st.isCompleted()) completed++;
                }
                holder.pbSubtasks.setMax(total);
                holder.pbSubtasks.setProgress(completed);
                holder.txtSubtaskCount.setText(completed + "/" + total);
            } else {
                holder.layoutSubtaskProgress.setVisibility(View.GONE);
            }

            // Priority styling
            holder.txtPriority.setText(task.getPriority());
            int priorityColorBg = R.color.priority_medium;
            if ("HIGH".equalsIgnoreCase(task.getPriority())) {
                priorityColorBg = R.color.priority_high;
            } else if ("LOW".equalsIgnoreCase(task.getPriority())) {
                priorityColorBg = R.color.priority_low;
            }
            holder.txtPriority.setBackgroundColor(getResources().getColor(priorityColorBg));

            // Options action
            holder.itemView.setOnLongClickListener(v -> {
                showOptionsDialog(task);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        private void showOptionsDialog(Task task) {
            new AlertDialog.Builder(TasksActivity.this)
                    .setTitle("Task Options")
                    .setItems(new String[]{"Set Reminder", "Delete Task"}, (dialog, which) -> {
                        if (which == 0) {
                            // Phase 28: Set Reminder (Cross-Module Linking)
                            Intent intent = new Intent(TasksActivity.this, AddReminderActivity.class);
                            intent.putExtra("LINKED_TITLE", task.getTitle());
                            intent.putExtra("LINKED_ID", task.getId());
                            intent.putExtra("LINKED_TYPE", "TASK");
                            startActivity(intent);
                        } else if (which == 1) {
                            new AlertDialog.Builder(TasksActivity.this)
                                    .setTitle("Delete Task")
                                    .setMessage("Are you sure you want to delete this task?")
                                    .setPositiveButton("Delete", (d, w) -> {
                                        if (auth.getCurrentUser() != null) {
                                            db.collection("users")
                                                    .document(auth.getCurrentUser().getUid())
                                                    .collection("tasks")
                                                    .document(task.getId())
                                                    .delete();
                                        }
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        }
                    })
                    .show();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox cbStatus;
            TextView txtTitle, txtDueDate, txtPriority, txtSubtaskCount, txtImportant, txtRepeat;
            android.widget.ProgressBar pbSubtasks;
            android.widget.LinearLayout layoutSubtaskProgress;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                cbStatus = itemView.findViewById(R.id.cbTaskStatus);
                txtTitle = itemView.findViewById(R.id.txtTaskTitle);
                txtDueDate = itemView.findViewById(R.id.txtTaskDueDate);
                txtPriority = itemView.findViewById(R.id.txtTaskPriority);
                txtSubtaskCount = itemView.findViewById(R.id.txtSubtaskCount);
                pbSubtasks = itemView.findViewById(R.id.pbSubtasks);
                layoutSubtaskProgress = itemView.findViewById(R.id.layoutSubtaskProgress);
                txtImportant = itemView.findViewById(R.id.txtTaskImportant);
                txtRepeat = itemView.findViewById(R.id.txtTaskRepeat);
                
                // Allow tapping the task to edit
                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        Task task = list.get(pos);
                        Intent intent = new Intent(TasksActivity.this, AddTaskActivity.class);
                        intent.putExtra("TASK_ID", task.getId());
                        startActivity(intent);
                    }
                });
            }
        }
    }

    @Override
    public void startActivity(android.content.Intent intent) {
        super.startActivity(intent);
        com.example.frienddebt.utils.AnimationHelper.applyStartTransition(this, intent);
    }

    @Override
    public void finish() {
        super.finish();
        com.example.frienddebt.utils.AnimationHelper.applyFinishTransition(this);
    }

}
