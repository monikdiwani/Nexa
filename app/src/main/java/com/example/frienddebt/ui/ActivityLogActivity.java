package com.example.frienddebt.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.frienddebt.R;
import com.example.frienddebt.model.AuditLog;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.example.frienddebt.utils.StatusBarUtil;

public class ActivityLogActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String bookId;
    private String bookName;

    private RecyclerView rvLogs;
    private TextView txtEmptyLogs;
    private LogAdapter adapter;
    private List<AuditLog> logsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_log);
        StatusBarUtil.applyStatusBarPadding(this);

        db = FirebaseFirestore.getInstance();

        bookId = getIntent().getStringExtra("BOOK_ID");
        bookName = getIntent().getStringExtra("BOOK_NAME");

        ImageButton btnBack = findViewById(R.id.btnBack);
        TextView txtToolbarTitle = findViewById(R.id.txtToolbarTitle);
        rvLogs = findViewById(R.id.rvLogs);
        txtEmptyLogs = findViewById(R.id.txtEmptyLogs);

        btnBack.setOnClickListener(v -> finish());
        
        if (bookName != null) {
            txtToolbarTitle.setText("Log: " + bookName);
        }

        rvLogs.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LogAdapter(this, logsList);
        rvLogs.setAdapter(adapter);

        loadLogs();
    }

    private void loadLogs() {
        if (bookId == null) return;

        db.collection("cashbooks").document(bookId).collection("logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e("ActivityLogActivity", "Error loading logs", e);
                        android.widget.Toast.makeText(ActivityLogActivity.this, "Error loading logs: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (snapshots != null) {
                        logsList.clear();
                        for (DocumentSnapshot doc : snapshots) {
                            logsList.add(AuditLog.fromDocument(doc));
                        }
                        adapter.notifyDataSetChanged();
                        txtEmptyLogs.setVisibility(logsList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private String formatElapsedTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 0) diff = 0;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds < 60) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + "m ago";
        } else if (hours < 24) {
            return hours + "h ago";
        } else if (days < 7) {
            return days + "d ago";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }

    private class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {
        private final Context context;
        private final List<AuditLog> list;

        public LogAdapter(Context context, List<AuditLog> list) {
            this.context = context;
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_activity_log, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AuditLog log = list.get(position);

            holder.txtLogActor.setText(log.getActorName() != null ? log.getActorName() : "Unknown Member");
            holder.txtLogTime.setText(formatElapsedTime(log.getTimestamp()));
            holder.txtLogDescription.setText(log.getDetails() != null ? log.getDetails() : "");

            // Change background circle and emoji dynamically depending on Action type
            Drawable circleDrawable = ContextCompat.getDrawable(context, R.drawable.circle_background_light);
            if (circleDrawable != null) {
                circleDrawable = circleDrawable.mutate();
                int colorRes = R.color.accent_negative; // Default for delete
                if ("CREATE".equalsIgnoreCase(log.getActionType())) {
                    colorRes = R.color.accent_positive;
                } else if ("SETTLE".equalsIgnoreCase(log.getActionType())) {
                    colorRes = R.color.accent_positive; // or a neutral blue if available, positive is fine
                }
                circleDrawable.setColorFilter(ContextCompat.getColor(context, colorRes), PorterDuff.Mode.SRC_IN);
                holder.txtLogIcon.setBackground(circleDrawable);
            }

            if ("CREATE".equalsIgnoreCase(log.getActionType())) {
                holder.txtLogIcon.setText("➕");
            } else if ("DELETE".equalsIgnoreCase(log.getActionType())) {
                holder.txtLogIcon.setText("🗑️");
            } else if ("SETTLE".equalsIgnoreCase(log.getActionType())) {
                holder.txtLogIcon.setText("🤝");
            } else {
                holder.txtLogIcon.setText("📝");
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtLogIcon, txtLogDescription, txtLogActor, txtLogTime;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtLogIcon = itemView.findViewById(R.id.txtLogIcon);
                txtLogDescription = itemView.findViewById(R.id.txtLogDescription);
                txtLogActor = itemView.findViewById(R.id.txtLogActor);
                txtLogTime = itemView.findViewById(R.id.txtLogTime);
            }
        }
    }
}
