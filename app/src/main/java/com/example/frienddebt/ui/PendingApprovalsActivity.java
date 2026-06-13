package com.example.frienddebt.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.utils.StatusBarUtil;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PendingApprovalsActivity — Admin-only screen.
 *
 * Shows all pending join requests for a specific cashbook.
 * Admin can Accept (assign a role) or Decline each request.
 *
 * Data source: cashbooks/{bookId}/pendingMembers where status == "PENDING"
 */
public class PendingApprovalsActivity extends AppCompatActivity {

    private RecyclerView rvPendingMembers;
    private View layoutEmptyPending;
    private TextView txtPendingSubtitle;

    private FirebaseFirestore db;
    private String bookId;
    private ListenerRegistration listener;

    private final List<PendingMember> pendingList = new ArrayList<>();
    private PendingAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_approvals);
        StatusBarUtil.applyStatusBarPadding(this);

        bookId = getIntent().getStringExtra("BOOK_ID");
        if (bookId == null) { finish(); return; }

        db = FirebaseFirestore.getInstance();

        ImageButton btnBack = findViewById(R.id.btnBack);
        rvPendingMembers    = findViewById(R.id.rvPendingMembers);
        layoutEmptyPending  = findViewById(R.id.layoutEmptyPending);
        txtPendingSubtitle  = findViewById(R.id.txtPendingSubtitle);

        btnBack.setOnClickListener(v -> finish());

        adapter = new PendingAdapter();
        rvPendingMembers.setLayoutManager(new LinearLayoutManager(this));
        rvPendingMembers.setAdapter(adapter);

        listenForPendingRequests();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }

    // ─── Real-time listener ───────────────────────────────────────────────────

    private void listenForPendingRequests() {
        listener = db.collection("cashbooks").document(bookId)
            .collection("pendingMembers")
            .addSnapshotListener((snapshots, e) -> {
                if (snapshots == null) return;
                pendingList.clear();
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    String status = doc.getString("status");
                    if ("PENDING".equals(status)) {
                        pendingList.add(new PendingMember(
                            doc.getId(),
                            doc.getString("displayName"),
                            doc.getString("email"),
                            doc.getLong("requestedAt") != null ? doc.getLong("requestedAt") : 0L
                        ));
                    }
                }
                adapter.notifyDataSetChanged();
                updateEmptyState();
            });
    }

    private void updateEmptyState() {
        int count = pendingList.size();
        txtPendingSubtitle.setText(count + " pending request" + (count == 1 ? "" : "s"));
        layoutEmptyPending.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        rvPendingMembers.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    // ─── Accept → role picker → move to members map ──────────────────────────

    private void acceptMember(PendingMember member) {
        String[] roles = {"VIEWER", "EDITOR", "ADMIN"};
        final int[] selected = {1}; // default EDITOR

        new AlertDialog.Builder(this)
            .setTitle("Accept " + member.displayName)
            .setMessage("Assign a role for " + member.displayName + ":")
            .setSingleChoiceItems(roles, 1, (dialog, which) -> selected[0] = which)
            .setPositiveButton("Accept", (dialog, which) -> {
                String role = roles[selected[0]];
                // 1. Add to members map
                db.collection("cashbooks").document(bookId)
                    .update("members." + member.uid, role)
                    .addOnSuccessListener(aVoid -> {
                        // 2. Remove from pendingMembers
                        db.collection("cashbooks").document(bookId)
                            .collection("pendingMembers").document(member.uid)
                            .delete();
                        Toast.makeText(this, member.displayName + " added as " + role, Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(ex ->
                        Toast.makeText(this, "Failed: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ─── Decline → delete pending document ───────────────────────────────────

    private void declineMember(PendingMember member) {
        new AlertDialog.Builder(this)
            .setTitle("Decline Request")
            .setMessage("Decline " + member.displayName + "'s request to join?")
            .setPositiveButton("Decline", (dialog, which) -> {
                db.collection("cashbooks").document(bookId)
                    .collection("pendingMembers").document(member.uid)
                    .delete()
                    .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, member.displayName + "'s request declined", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(ex ->
                        Toast.makeText(this, "Failed: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ─── Data class ──────────────────────────────────────────────────────────

    static class PendingMember {
        final String uid, displayName, email;
        final long requestedAt;

        PendingMember(String uid, String displayName, String email, long requestedAt) {
            this.uid = uid;
            this.displayName = displayName != null ? displayName : "User";
            this.email = email != null ? email : "";
            this.requestedAt = requestedAt;
        }
    }

    // ─── Adapter ─────────────────────────────────────────────────────────────

    private class PendingAdapter extends RecyclerView.Adapter<PendingAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pending_member, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            PendingMember m = pendingList.get(pos);

            // Avatar initials
            String initials = m.displayName.trim().isEmpty() ? "?" :
                String.valueOf(m.displayName.trim().charAt(0)).toUpperCase();
            h.avatar.setText(initials);
            h.name.setText(m.displayName);
            h.email.setText(m.email);

            // Time ago
            long diff = System.currentTimeMillis() - m.requestedAt;
            String timeAgo;
            if (diff < 60_000) timeAgo = "Just now";
            else if (diff < 3_600_000) timeAgo = (diff / 60_000) + " min ago";
            else if (diff < 86_400_000) timeAgo = (diff / 3_600_000) + " hr ago";
            else timeAgo = new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(m.requestedAt));
            h.time.setText(timeAgo);

            h.btnAccept.setOnClickListener(v -> acceptMember(m));
            h.btnDecline.setOnClickListener(v -> declineMember(m));
        }

        @Override
        public int getItemCount() { return pendingList.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView avatar, name, email, time;
            com.google.android.material.button.MaterialButton btnAccept, btnDecline;

            VH(View v) {
                super(v);
                avatar     = v.findViewById(R.id.txtPendingAvatar);
                name       = v.findViewById(R.id.txtPendingName);
                email      = v.findViewById(R.id.txtPendingEmail);
                time       = v.findViewById(R.id.txtPendingTime);
                btnAccept  = v.findViewById(R.id.btnAccept);
                btnDecline = v.findViewById(R.id.btnDecline);
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
