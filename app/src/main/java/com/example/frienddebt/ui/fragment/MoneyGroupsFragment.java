package com.example.frienddebt.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Group;
import com.example.frienddebt.ui.GroupDetailActivity;
import com.example.frienddebt.ui.JoinGroupActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Groups sub-tab inside MoneyFragment.
 * Shows groups with create/join and enhanced cards showing expense + member info.
 */
public class MoneyGroupsFragment extends Fragment {

    private RecyclerView rvGroups;
    private LinearLayout emptyGroupsState;
    private Button btnCreateGroup, btnJoinGroup;

    private GroupsAdapter adapter;
    private List<GroupInfo> groups = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private Set<String> loadedGroupIds = new HashSet<>();

    // Enhanced group info with member count and expense total
    public static class GroupInfo {
        public final String id;
        public final String name;
        public final String ownerId;
        public int memberCount = 0;
        public double totalExpense = 0;
        public boolean isSettled = true;

        public GroupInfo(String id, String name, String ownerId) {
            this.id = id;
            this.name = name;
            this.ownerId = ownerId;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_money_groups, container, false);

        rvGroups = view.findViewById(R.id.rvGroups);
        emptyGroupsState = view.findViewById(R.id.emptyGroupsState);
        btnCreateGroup = view.findViewById(R.id.btnCreateGroup);
        btnJoinGroup = view.findViewById(R.id.btnJoinGroup);

        rvGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new GroupsAdapter(groups);
        rvGroups.setAdapter(adapter);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() != null) {
            loadGroupsFromFirestore();
        }

        btnCreateGroup.setOnClickListener(v -> {
            playButtonPop(v);
            showCreateGroupDialog();
        });

        btnJoinGroup.setOnClickListener(v -> {
            playButtonPop(v);
            startActivity(new Intent(requireActivity(), JoinGroupActivity.class));
            requireActivity().overridePendingTransition(R.anim.slide_up, R.anim.fade_in);
        });

        return view;
    }

    private void playButtonPop(View v) {
        Animation pop = AnimationUtils.loadAnimation(requireContext(), R.anim.button_pop);
        v.startAnimation(pop);
    }

    private void loadGroupsFromFirestore() {
        String userId = auth.getCurrentUser().getUid();

        groups.clear();
        loadedGroupIds.clear();

        // 1. Groups created by this user
        db.collection("users")
                .document(userId)
                .collection("groups")
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;

                    for (DocumentSnapshot doc : snapshots) {
                        String id = doc.getId();
                        String name = doc.getString("name");
                        String inviteCode = doc.getString("inviteCode");

                        // Passive backfill for existing groups to the root invite_codes collection
                        if (inviteCode != null && !inviteCode.isEmpty()) {
                            Map<String, Object> inviteMapping = new HashMap<>();
                            inviteMapping.put("groupId", id);
                            inviteMapping.put("ownerId", userId);
                            inviteMapping.put("createdAt", doc.getLong("createdAt"));
                            db.collection("invite_codes").document(inviteCode).set(inviteMapping);
                        }

                        if (!loadedGroupIds.contains(id)) {
                            loadedGroupIds.add(id);
                            GroupInfo gi = new GroupInfo(id, name, userId);
                            groups.add(gi);

                            // Load extra info (member count + total expense)
                            loadGroupExtras(gi, userId);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                });

        // 2. Groups joined by this user
        db.collection("users")
                .document(userId)
                .collection("joined_groups")
                .addSnapshotListener((joinedSnap, e) -> {
                    if (joinedSnap == null) return;

                    for (DocumentSnapshot joinedDoc : joinedSnap) {
                        String groupId = joinedDoc.getString("groupId");
                        String ownerId = joinedDoc.getString("ownerId");

                        if (groupId == null || ownerId == null) continue;
                        if (loadedGroupIds.contains(groupId)) continue;

                        db.collection("users")
                                .document(ownerId)
                                .collection("groups")
                                .document(groupId)
                                .get()
                                .addOnSuccessListener(groupDoc -> {
                                    if (groupDoc.exists()) {
                                        String name = groupDoc.getString("name");

                                        if (!loadedGroupIds.contains(groupId)) {
                                            loadedGroupIds.add(groupId);
                                            GroupInfo gi = new GroupInfo(groupId, name, ownerId);
                                            groups.add(gi);

                                            loadGroupExtras(gi, ownerId);
                                            adapter.notifyDataSetChanged();
                                            updateEmptyState();
                                        }
                                    }
                                });
                    }
                });
    }

    private void loadGroupExtras(GroupInfo gi, String ownerId) {
        // Load member count from members subcollection
        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(gi.id)
                .collection("members")
                .get()
                .addOnSuccessListener(snap -> {
                    gi.memberCount = snap.size();
                    adapter.notifyDataSetChanged();
                });

        // Load total expense
        db.collection("users")
                .document(ownerId)
                .collection("groups")
                .document(gi.id)
                .collection("expenses")
                .get()
                .addOnSuccessListener(snap -> {
                    double total = 0;
                    for (QueryDocumentSnapshot doc : snap) {
                        Double amount = doc.getDouble("amount");
                        if (amount != null) total += amount;
                    }
                    gi.totalExpense = total;
                    gi.isSettled = total == 0;
                    adapter.notifyDataSetChanged();
                });
    }

    private void updateEmptyState() {
        if (groups.isEmpty()) {
            emptyGroupsState.setVisibility(View.VISIBLE);
            rvGroups.setVisibility(View.GONE);
        } else {
            emptyGroupsState.setVisibility(View.GONE);
            rvGroups.setVisibility(View.VISIBLE);
        }
    }

    // ─── RecyclerView Adapter ──────────────────────────────────────
    private class GroupsAdapter extends RecyclerView.Adapter<GroupsAdapter.ViewHolder> {
        private final List<GroupInfo> list;

        // Avatar gradient backgrounds cycling
        private final int[] avatarBgs = {
                R.drawable.card_gradient_purple,
                R.drawable.card_gradient_blue,
                R.drawable.card_gradient_green,
                R.drawable.card_gradient_amber,
                R.drawable.card_gradient_red
        };

        public GroupsAdapter(List<GroupInfo> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            GroupInfo gi = list.get(position);

            // Avatar — first letter of group name
            String initial = (gi.name != null && !gi.name.isEmpty()) ? gi.name.substring(0, 1).toUpperCase() : "G";
            holder.txtGroupAvatar.setText(initial);
            holder.txtGroupAvatar.setBackgroundResource(avatarBgs[position % avatarBgs.length]);

            // Group name
            holder.txtGroupName.setText(gi.name != null ? gi.name : "");

            // Role
            boolean isOwner = auth.getCurrentUser() != null && auth.getCurrentUser().getUid().equals(gi.ownerId);
            holder.txtGroupRole.setText(isOwner ? "👑 Owner" : "👥 Member");

            // Member count
            holder.txtGroupMembers.setText(gi.memberCount > 0 ? gi.memberCount + " members" : "");

            // Total expense
            if (gi.totalExpense > 0) {
                holder.txtGroupExpense.setText(String.format(Locale.getDefault(), "₹%.0f", gi.totalExpense));
                holder.txtGroupExpense.setVisibility(View.VISIBLE);
            } else {
                holder.txtGroupExpense.setText("₹0");
                holder.txtGroupExpense.setVisibility(View.VISIBLE);
            }

            // Settlement status
            if (gi.totalExpense == 0) {
                holder.txtGroupStatus.setText("No expenses");
                holder.txtGroupStatus.setTextColor(getResources().getColor(R.color.text_hint));
            } else if (gi.isSettled) {
                holder.txtGroupStatus.setText("Settled ✓");
                holder.txtGroupStatus.setTextColor(getResources().getColor(R.color.accent_positive));
            } else {
                holder.txtGroupStatus.setText("Active");
                holder.txtGroupStatus.setTextColor(getResources().getColor(R.color.accent_warning));
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(requireActivity(), GroupDetailActivity.class);
                intent.putExtra("GROUP_ID", gi.id);
                intent.putExtra("OWNER_ID", gi.ownerId);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtGroupAvatar, txtGroupName, txtGroupRole, txtGroupMembers;
            TextView txtGroupExpense, txtGroupStatus;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtGroupAvatar = itemView.findViewById(R.id.txtGroupAvatar);
                txtGroupName = itemView.findViewById(R.id.txtGroupName);
                txtGroupRole = itemView.findViewById(R.id.txtGroupRole);
                txtGroupMembers = itemView.findViewById(R.id.txtGroupMembers);
                txtGroupExpense = itemView.findViewById(R.id.txtGroupExpense);
                txtGroupStatus = itemView.findViewById(R.id.txtGroupStatus);
            }
        }
    }

    private void showCreateGroupDialog() {
        final android.widget.EditText input = new android.widget.EditText(requireContext());

        new AlertDialog.Builder(requireContext())
                .setTitle("New Group")
                .setMessage("Enter group name")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        saveGroupToFirestore(name);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveGroupToFirestore(String name) {
        String userId = auth.getCurrentUser().getUid();
        String inviteCode = generateInviteCode();

        Map<String, Object> group = new HashMap<>();
        group.put("name", name);
        group.put("inviteCode", inviteCode);
        group.put("ownerId", userId);
        group.put("createdAt", System.currentTimeMillis());

        db.collection("users")
                .document(userId)
                .collection("groups")
                .add(group)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(requireContext(), "Group created! Invite code: " + inviteCode, Toast.LENGTH_LONG).show();
                    
                    // Add creator to members subcollection as admin
                    String currentUserName = auth.getCurrentUser().getDisplayName();
                    if (currentUserName == null || currentUserName.isEmpty()) {
                        currentUserName = auth.getCurrentUser().getEmail() != null ? auth.getCurrentUser().getEmail().split("@")[0] : "Owner";
                    }
                    String memberDocId = currentUserName.trim().toLowerCase().replace(" ", "_");
                    
                    Map<String, Object> member = new HashMap<>();
                    member.put("name", currentUserName.trim());
                    member.put("role", "admin");
                    member.put("addedAt", System.currentTimeMillis());
                    
                    ref.collection("members").document(memberDocId).set(member);

                    // Add to global invite_codes collection to bypass collectionGroup index
                    Map<String, Object> inviteMapping = new HashMap<>();
                    inviteMapping.put("groupId", ref.getId());
                    inviteMapping.put("ownerId", userId);
                    inviteMapping.put("createdAt", System.currentTimeMillis());
                    db.collection("invite_codes").document(inviteCode).set(inviteMapping);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int idx = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }
}
