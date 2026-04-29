package com.example.frienddebt.ui;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class GroupActivityLogActivity extends AppCompatActivity {

    private RecyclerView rvActivityLogs;
    private View emptyLogsState;
    private ProgressBar progressBar;

    private String groupId;
    private String ownerId;

    private FirebaseFirestore db;
    private LogAdapter adapter;
    private List<LogEntry> logsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_activity_log);

        groupId = getIntent().getStringExtra("GROUP_ID");
        ownerId = getIntent().getStringExtra("OWNER_ID");

        if (groupId == null || ownerId == null || groupId.isEmpty() || ownerId.isEmpty()) {
            Toast.makeText(this, "Group or Owner details missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvActivityLogs = findViewById(R.id.rvActivityLogs);
        emptyLogsState = findViewById(R.id.emptyLogsState);
        progressBar = findViewById(R.id.progressBar);

        rvActivityLogs.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LogAdapter(logsList);
        rvActivityLogs.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        loadActivityLogs();
    }

    private void loadActivityLogs() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(groupId)
                .collection("logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    progressBar.setVisibility(View.GONE);
                    logsList.clear();
                    if (snapshot != null && !snapshot.isEmpty()) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String type = doc.getString("type");
                            String userName = doc.getString("userName");
                            String description = doc.getString("description");
                            Long timestamp = doc.getLong("timestamp");

                            if (description != null && timestamp != null) {
                                logsList.add(new LogEntry(type, userName, description, timestamp));
                            }
                        }
                    }

                    adapter.notifyDataSetChanged();

                    if (logsList.isEmpty()) {
                        emptyLogsState.setVisibility(View.VISIBLE);
                        rvActivityLogs.setVisibility(View.GONE);
                    } else {
                        emptyLogsState.setVisibility(View.GONE);
                        rvActivityLogs.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load logs: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // ─── LOG MODEL CLASS ──────────────────────────────────────────────
    private static class LogEntry {
        public final String type;
        public final String userName;
        public final String description;
        public final long timestamp;

        public LogEntry(String type, String userName, String description, long timestamp) {
            this.type = type;
            this.userName = userName;
            this.description = description;
            this.timestamp = timestamp;
        }
    }

    // ─── ADAPTER & VIEWHOLDER ─────────────────────────────────────────
    private static class LogAdapter extends RecyclerView.Adapter<LogAdapter.VH> {
        private final List<LogEntry> list;

        public LogAdapter(List<LogEntry> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity_log, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            LogEntry entry = list.get(position);

            holder.txtDesc.setText(entry.description);
            holder.txtActor.setText(entry.userName != null ? entry.userName : "Someone");

            // Format relative time (e.g. 10m ago, 2h ago, etc.)
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    entry.timestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
            );
            holder.txtTime.setText(relativeTime);

            // Assign matching emoji based on log type
            String emoji = "📝";
            if (entry.type != null) {
                switch (entry.type) {
                    case "group_created":
                        emoji = "👑";
                        break;
                    case "member_joined":
                    case "member_added":
                        emoji = "👥";
                        break;
                    case "member_removed":
                        emoji = "❌";
                        break;
                    case "role_changed":
                        emoji = "⚙️";
                        break;
                    case "expense_added":
                        emoji = "💰";
                        break;
                    case "expense_updated":
                        emoji = "✏️";
                        break;
                    case "expense_deleted":
                        emoji = "🗑️";
                        break;
                    case "settlement_made":
                        emoji = "🤝";
                        break;
                    case "settlement_undone":
                        emoji = "↩️";
                        break;
                }
            }
            holder.txtIcon.setText(emoji);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView txtIcon, txtDesc, txtActor, txtTime;

            public VH(@NonNull View itemView) {
                super(itemView);
                txtIcon = itemView.findViewById(R.id.txtLogIcon);
                txtDesc = itemView.findViewById(R.id.txtLogDescription);
                txtActor = itemView.findViewById(R.id.txtLogActor);
                txtTime = itemView.findViewById(R.id.txtLogTime);
            }
        }
    }
}
