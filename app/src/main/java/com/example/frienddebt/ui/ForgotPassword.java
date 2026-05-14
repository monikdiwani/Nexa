package com.example.frienddebt.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.frienddebt.R;
import com.example.frienddebt.utils.StatusBarUtil;
import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPassword extends AppCompatActivity {

    private static final String TAG = "ForgotPassword";

    EditText emailInput;
    TextView btnSendReset, backToLogin;
    LinearLayout successState;
    TextView txtSuccessEmail, btnResend, btnOpenEmail;
    FirebaseAuth firebaseAuth;

    private String lastEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        StatusBarUtil.applyStatusBarPadding(this);

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance();

        // Bind views
        emailInput = findViewById(R.id.emailInput);
        btnSendReset = findViewById(R.id.btnStartNav);
        backToLogin = findViewById(R.id.backToLogin);
        successState = findViewById(R.id.successState);
        txtSuccessEmail = findViewById(R.id.txtSuccessEmail);
        btnResend = findViewById(R.id.btnResend);
        btnOpenEmail = findViewById(R.id.btnOpenEmail);

        // Send reset email
        btnSendReset.setOnClickListener(v -> sendResetEmail());

        // Resend button
        if (btnResend != null) {
            btnResend.setOnClickListener(v -> {
                if (!lastEmail.isEmpty()) {
                    sendResetEmailTo(lastEmail);
                }
            });
        }

        // Open email app
        if (btnOpenEmail != null) {
            btnOpenEmail.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_APP_EMAIL);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
                }
            });
        }

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

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

        lastEmail = email;
        sendResetEmailTo(email);
    }

    private void sendResetEmailTo(String email) {
        btnSendReset.setEnabled(false);
        btnSendReset.setText("Sending...");

        if (btnResend != null) {
            btnResend.setEnabled(false);
            btnResend.setText("Sending...");
        }

        firebaseAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Password reset email sent to: " + email);

                    btnSendReset.setEnabled(true);
                    btnSendReset.setText("Send Reset Link");

                    if (btnResend != null) {
                        btnResend.setEnabled(true);
                        btnResend.setText("📧  Resend Email");
                    }

                    // Show success state
                    showSuccessState(email);

                    Toast.makeText(this,
                            "Reset link sent! Check your inbox and spam folder.",
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send reset email", e);

                    btnSendReset.setEnabled(true);
                    btnSendReset.setText("Send Reset Link");

                    if (btnResend != null) {
                        btnResend.setEnabled(true);
                        btnResend.setText("📧  Resend Email");
                    }

                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("no user record")) {
                        Toast.makeText(this,
                                "No account found with this email. Please sign up first.",
                                Toast.LENGTH_LONG).show();
                    } else if (errorMsg != null && errorMsg.contains("INVALID_EMAIL")) {
                        Toast.makeText(this,
                                "Invalid email format. Please check and try again.",
                                Toast.LENGTH_LONG).show();
                    } else if (errorMsg != null && errorMsg.contains("TOO_MANY_REQUESTS")) {
                        Toast.makeText(this,
                                "Too many requests. Please wait a moment and try again.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                "Failed to send reset email: " + errorMsg,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showSuccessState(String email) {
        if (successState != null) {
            successState.setVisibility(View.VISIBLE);
            if (txtSuccessEmail != null) {
                txtSuccessEmail.setText("We've sent a password reset link to:\n" + email);
            }
        }
    }
}
