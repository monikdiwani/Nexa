package com.example.frienddebt.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.frienddebt.R;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPassword extends AppCompatActivity {

    EditText emailInput;
    TextView btnSendReset, backToLogin;
    FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance();

        // Bind views
        emailInput = findViewById(R.id.emailInput);
        btnSendReset = findViewById(R.id.btnStartNav);
        backToLogin = findViewById(R.id.backToLogin);

        // Send reset email
        btnSendReset.setOnClickListener(v -> sendResetEmail());

        // Back to login screen
        backToLogin.setOnClickListener(v -> {
            startActivity(new Intent(ForgotPassword.this, Login.class));
            finish();
        });

        // Optional: EdgeToEdge padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void sendResetEmail() {
        String email = emailInput.getText().toString().trim();

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSendReset.setEnabled(false);
        btnSendReset.setText("Sending...");

        firebaseAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {

                    btnSendReset.setEnabled(true);
                    btnSendReset.setText("Send Reset Link");
                    Toast.makeText(this, "Password reset email sent!", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace(); // <-- Correct: log the error here
                    btnSendReset.setEnabled(true);
                    btnSendReset.setText("Send Reset Link");
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
