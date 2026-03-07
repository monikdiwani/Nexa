package com.example.frienddebt.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class Login extends AppCompatActivity {

    EditText emailInput, passwordInput;
    Button btnStartNav;
    TextView forgotPassword, signUpText;
    FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        firebaseAuth = FirebaseAuth.getInstance();

        // Check if user is already logged in
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            // User is already logged in, redirect to MainActivity
            navigateToMainActivity();
            return;
        }

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.editTextTextPassword);
        btnStartNav = findViewById(R.id.btnStartNav);
        forgotPassword = findViewById(R.id.forgot);
        signUpText = findViewById(R.id.signUpLink); // Updated ID

        // Fade-in content for a smoother feel
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        findViewById(R.id.main).startAnimation(fadeIn);

        btnStartNav.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_pop));
            userLogin();
        });

        signUpText.setOnClickListener(v -> {
            startActivity(new Intent(Login.this, SignUp.class));
            finish();
        });

        forgotPassword.setOnClickListener(v -> {
            startActivity(new Intent(Login.this, ForgotPassword.class));
        });
    }

    private void userLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        // Validation
        if (email.isEmpty()) {
            emailInput.setError("Email is required");
            emailInput.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Please enter a valid email");
            emailInput.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwordInput.setError("Password is required");
            passwordInput.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters");
            passwordInput.requestFocus();
            return;
        }

        btnStartNav.setEnabled(false);
        btnStartNav.setText("Logging in...");

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    btnStartNav.setEnabled(true);
                    btnStartNav.setText("Login");

                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Logged in Successfully", Toast.LENGTH_SHORT).show();
                        navigateToMainActivity();
                    } else {
                        String errorMessage = task.getException() != null ?
                                task.getException().getMessage() : "Login failed";
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void navigateToMainActivity() {
        // Navigate to MainActivity (your groups list) instead of GroupDetailActivity
        startActivity(new Intent(Login.this, MainActivity.class));
        finish();
    }
}