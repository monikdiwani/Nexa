package com.example.frienddebt.ui;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Group;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private ListView listViewGroups;
    private Button btnAddGroup, btnJoinGroup, btnLogout;

    private ArrayAdapter<String> adapter;
    private List<Group> groups = new ArrayList<>();
    private List<String> groupNames = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // 🔥 Avoid duplicates
    private Set<String> loadedGroupIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listViewGroups = findViewById(R.id.listViewGroups);
        btnAddGroup = findViewById(R.id.btnAddGroup);
        btnJoinGroup = findViewById(R.id.btnJoinGroup);
        btnLogout = findViewById(R.id.btnLogout);

        // Subtle fade-in for the main screen
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        findViewById(R.id.mainContent).startAnimation(fadeIn);

        adapter = new ArrayAdapter<>(
                this,
                R.layout.item_group,
                R.id.txtDebt,
                groupNames
        );
        listViewGroups.setAdapter(adapter);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadGroupsFromFirestore();

        listViewGroups.setOnItemClickListener((parent, view, position, id) -> {
            Group g = groups.get(position);
            Intent intent = new Intent(MainActivity.this, GroupDetailActivity.class);
            intent.putExtra("GROUP_ID", g.getId());
            intent.putExtra("OWNER_ID", g.getOwnerId());   // 🔥 IMPORTANT
            startActivity(intent);
        });

        btnAddGroup.setOnClickListener(v -> {
            playButtonPop(v);
            showCreateGroupDialog();
        });

        btnJoinGroup.setOnClickListener(v -> {
            playButtonPop(v);
            startActivity(new Intent(MainActivity.this, JoinGroupActivity.class));
            overridePendingTransition(R.anim.slide_up, R.anim.fade_in);
        });

        btnLogout.setOnClickListener(v -> {
            playButtonPop(v);
            // Sign out the current user
            auth.signOut();

            // Navigate back to Login screen and clear the back stack
            Intent intent = new Intent(MainActivity.this, Login.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void playButtonPop(android.view.View v) {
        Animation pop = AnimationUtils.loadAnimation(this, R.anim.button_pop);
        v.startAnimation(pop);
    }

    // ✅ Load OWN + JOINED groups
    private void loadGroupsFromFirestore() {
        String userId = auth.getCurrentUser().getUid();

        groups.clear();
        groupNames.clear();
        loadedGroupIds.clear();

        // 1️⃣ Groups created by this user
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
                            groups.add(new Group(id, name, userId));   // 🔥 ownerId = current user
                            groupNames.add(name);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });

        // 2️⃣ Groups joined by this user
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

                                        loadedGroupIds.add(groupId);
                                        groups.add(new Group(groupId, name, ownerId));  // 🔥 ownerId saved
                                        groupNames.add(name);
                                        adapter.notifyDataSetChanged();
                                    }
                                });
                    }
                });
    }

    private void showCreateGroupDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);

        new AlertDialog.Builder(this)
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
                        Toast.makeText(this, "Group created! Invite code: " + inviteCode, Toast.LENGTH_LONG).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show()
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
