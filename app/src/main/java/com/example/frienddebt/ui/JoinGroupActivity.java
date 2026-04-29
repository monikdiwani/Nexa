package com.example.frienddebt.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class JoinGroupActivity extends AppCompatActivity {

    private static final String TAG = "JoinGroupActivity";

    private EditText edtInviteCode;
    private Button btnJoin;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_group);

        edtInviteCode = findViewById(R.id.edtInviteCode);
        btnJoin = findViewById(R.id.btnJoinGroup);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        btnJoin.setOnClickListener(v -> joinGroupByCode());
    }

    private void joinGroupByCode() {
        String code = edtInviteCode.getText().toString().trim().toUpperCase();

        if (code.isEmpty()) {
            Toast.makeText(this, "Enter invite code", Toast.LENGTH_SHORT).show();
            return;
        }

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collectionGroup("groups")
                .whereEqualTo("inviteCode", code)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        Toast.makeText(this, "Invalid invite code", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 🔥 First matched group
                    DocumentSnapshot doc = snapshot.getDocuments().get(0);

                    String groupId = doc.getId();
                    String ownerUserId = doc.getReference()
                            .getParent()      // groups
                            .getParent()      // user doc
                            .getId();         // owner userId

                    String currentUserName = auth.getCurrentUser().getDisplayName();
                    if (currentUserName == null || currentUserName.isEmpty()) {
                        // fallback to email prefix if display name is not set
                        currentUserName = auth.getCurrentUser().getEmail() != null ? auth.getCurrentUser().getEmail().split("@")[0] : "Unknown User";
                    }
                    String memberDocId = currentUserName.trim().toLowerCase().replace(" ", "_");

                    Map<String, Object> member = new HashMap<>();
                    member.put("uid", auth.getCurrentUser().getUid());
                    member.put("name", currentUserName.trim());
                    member.put("role", "member");
                    member.put("joinedAt", System.currentTimeMillis());

                    // 1️⃣ Add user as member in OWNER'S group
                    db.collection("users")
                            .document(ownerUserId)
                            .collection("groups")
                            .document(groupId)
                            .collection("members")
                            .document(memberDocId)
                            .set(member)
                            .addOnSuccessListener(r -> {

                                // 🔥 STEP 3.1 — Save joined group reference for CURRENT USER
                                Map<String, Object> joinedGroupRef = new HashMap<>();
                                joinedGroupRef.put("groupId", groupId);
                                joinedGroupRef.put("ownerId", ownerUserId);
                                joinedGroupRef.put("joinedAt", System.currentTimeMillis());

                                db.collection("users")
                                        .document(auth.getCurrentUser().getUid())
                                        .collection("joined_groups")
                                        .document(groupId)
                                        .set(joinedGroupRef)
                                        .addOnSuccessListener(r2 -> {
                                            Toast.makeText(this, "Joined group successfully!", Toast.LENGTH_LONG).show();
                                            finish();
                                        })
                                        .addOnFailureListener(e2 -> {
                                            Log.e(TAG, "Failed to save joined group ref", e2);
                                            Toast.makeText(this, e2.getMessage(), Toast.LENGTH_LONG).show();
                                        });

                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to add member", e);
                                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Join group query failed", e);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
