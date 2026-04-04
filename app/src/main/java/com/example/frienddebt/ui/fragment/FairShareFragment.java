package com.example.frienddebt.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Group;
import com.example.frienddebt.ui.GroupDetailActivity;
import com.example.frienddebt.ui.JoinGroupActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FairShareFragment extends Fragment {

    private ListView listViewGroups;
    private Button btnAddGroup, btnJoinGroup;

    private ArrayAdapter<String> adapter;
    private List<Group> groups = new ArrayList<>();
    private List<String> groupNames = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private Set<String> loadedGroupIds = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_fair_share, container, false);

        listViewGroups = view.findViewById(R.id.listViewGroups);
        btnAddGroup = view.findViewById(R.id.btnAddGroup);
        btnJoinGroup = view.findViewById(R.id.btnJoinGroup);

        adapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_group,
                R.id.txtDebt,
                groupNames
        );
        listViewGroups.setAdapter(adapter);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() != null) {
            loadGroupsFromFirestore();
        }

        listViewGroups.setOnItemClickListener((parent, view1, position, id) -> {
            Group g = groups.get(position);
            Intent intent = new Intent(requireActivity(), GroupDetailActivity.class);
            intent.putExtra("GROUP_ID", g.getId());
            intent.putExtra("OWNER_ID", g.getOwnerId());
            startActivity(intent);
        });

        btnAddGroup.setOnClickListener(v -> {
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
        groupNames.clear();
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

                        if (!loadedGroupIds.contains(id)) {
                            loadedGroupIds.add(id);
                            groups.add(new Group(id, name, userId));
                            groupNames.add(name);
                        }
                    }
                    adapter.notifyDataSetChanged();
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
                                            groups.add(new Group(groupId, name, ownerId));
                                            groupNames.add(name);
                                            adapter.notifyDataSetChanged();
                                        }
                                    }
                                });
                    }
                });
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
                .addOnSuccessListener(ref ->
                        Toast.makeText(requireContext(), "Group created! Invite code: " + inviteCode, Toast.LENGTH_LONG).show()
                )
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
