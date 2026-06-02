package com.example.frienddebt.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.BuildConfig;
import com.example.frienddebt.R;
import com.example.frienddebt.utils.StatusBarUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIAssistantActivity extends AppCompatActivity {

    private RecyclerView rvChat;
    private EditText etMessage;
    private ImageButton btnSend, btnBack;
    private ProgressBar progressBar;

    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatMessages;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private String systemContext = "";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_assistant);
        StatusBarUtil.applyStatusBarPadding(this);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        rvChat = findViewById(R.id.rvChat);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        progressBar = findViewById(R.id.progressBar);

        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvChat.setLayoutManager(layoutManager);
        rvChat.setAdapter(chatAdapter);

        btnBack.setOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                sendMessage(text);
            }
        });

        // Initialize greeting
        addBotMessage("Hi there! I am Nexa Bot \uD83E\uDD16. I can help you summarize your finances, track tasks, or just give general advice. How can I assist you today?");

        // Fetch context asynchronously
        fetchUserContext();
    }

    private void fetchUserContext() {
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        // Very basic summary for context. You can expand this to fetch real data.
        systemContext = "You are Nexa Bot, an AI assistant built into the Nexa productivity and finance app. " +
                "You are friendly, concise, and helpful. " +
                "The user is asking you for help related to their productivity and cashbooks.";
                
        // Let's grab total active tasks as an example
        db.collection("users").document(userId).collection("tasks")
                .whereEqualTo("isCompleted", false)
                .get()
                .addOnSuccessListener(snapshots -> {
                    int taskCount = snapshots.size();
                    systemContext += "\nCurrent app context: The user has " + taskCount + " pending tasks.";
                });
    }

    private void sendMessage(String text) {
        etMessage.setText("");
        addMessage(new ChatMessage("user", text));
        
        if (GEMINI_API_KEY == null || GEMINI_API_KEY.isEmpty() || GEMINI_API_KEY.equals("YOUR_API_KEY_HERE")) {
            addBotMessage("Please configure your GEMINI_API_KEY in local.properties to enable AI responses.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        executor.execute(() -> {
            String responseText = callGeminiAPI(text);
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                btnSend.setEnabled(true);
                if (responseText != null) {
                    addBotMessage(responseText);
                } else {
                    addBotMessage("Sorry, I'm having trouble connecting right now.");
                }
            });
        });
    }

    private void addMessage(ChatMessage message) {
        chatMessages.add(message);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        rvChat.scrollToPosition(chatMessages.size() - 1);
    }

    private void addBotMessage(String text) {
        addMessage(new ChatMessage("model", text));
    }

    private String callGeminiAPI(String prompt) {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Build JSON Body
            JSONObject body = new JSONObject();
            
            // System instructions
            JSONObject systemInstruction = new JSONObject();
            JSONArray sysParts = new JSONArray();
            sysParts.put(new JSONObject().put("text", systemContext));
            systemInstruction.put("parts", sysParts);
            body.put("systemInstruction", systemInstruction);

            // Contents (Chat history)
            JSONArray contents = new JSONArray();
            
            // Add previous messages up to last 10 for context limit
            int start = Math.max(0, chatMessages.size() - 10);
            for (int i = start; i < chatMessages.size(); i++) {
                ChatMessage cm = chatMessages.get(i);
                JSONObject msgNode = new JSONObject();
                msgNode.put("role", cm.role);
                JSONArray partsNode = new JSONArray();
                partsNode.put(new JSONObject().put("text", cm.text));
                msgNode.put("parts", partsNode);
                contents.put(msgNode);
            }
            body.put("contents", contents);

            // Write Body
            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Parse response
                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray candidates = jsonResponse.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    JSONObject content = candidates.getJSONObject(0).getJSONObject("content");
                    JSONArray parts = content.getJSONArray("parts");
                    if (parts.length() > 0) {
                        return parts.getJSONObject(0).getString("text");
                    }
                }
            } else {
                Log.e("NexaBot", "API Error: " + responseCode);
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                Log.e("NexaBot", "Error Body: " + response.toString());
            }
        } catch (Exception e) {
            Log.e("NexaBot", "Exception calling Gemini", e);
        }
        return null;
    }

    // Models & Adapter
    private static class ChatMessage {
        String role; // "user" or "model"
        String text;

        public ChatMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private final List<ChatMessage> list;

        public ChatAdapter(List<ChatMessage> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage msg = list.get(position);
            if ("user".equals(msg.role)) {
                holder.layoutUser.setVisibility(View.VISIBLE);
                holder.layoutBot.setVisibility(View.GONE);
                holder.txtUserMessage.setText(msg.text);
            } else {
                holder.layoutUser.setVisibility(View.GONE);
                holder.layoutBot.setVisibility(View.VISIBLE);
                
                // Let's use Markwon if we want, or just basic text
                io.noties.markwon.Markwon markwon = io.noties.markwon.Markwon.create(AIAssistantActivity.this);
                markwon.setMarkdown(holder.txtBotMessage, msg.text);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            LinearLayout layoutUser, layoutBot;
            TextView txtUserMessage, txtBotMessage;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                layoutUser = itemView.findViewById(R.id.layoutUser);
                layoutBot = itemView.findViewById(R.id.layoutBot);
                txtUserMessage = itemView.findViewById(R.id.txtUserMessage);
                txtBotMessage = itemView.findViewById(R.id.txtBotMessage);
            }
        }
    }
}
