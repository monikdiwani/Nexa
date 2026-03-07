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

public class SignUp extends AppCompatActivity {

    EditText emailInput, passwordInput, confirmPasswordInput;
    Button btnStartNav;
    TextView tvLogin;
    FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        firebaseAuth = FirebaseAuth.getInstance();

        emailInput            = findViewById(R.id.emailInput);
        passwordInput         = findViewById(R.id.editTextTextPassword);
        confirmPasswordInput  = findViewById(R.id.confrimPassword);
        btnStartNav           = findViewById(R.id.btnStartNav);
        tvLogin               = findViewById(R.id.textView4);

        // Animate the whole screen on entry
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        findViewById(R.id.main).startAnimation(fadeIn);

        btnStartNav.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_pop));
            createUser();
        });

        // Handle "Already have an account? Login" click
        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignUp.this, Login.class));
            finish();
        });
    }

    private void createUser() {
        String email = emailInput.getText().toString().trim();
        String pass = passwordInput.getText().toString().trim();
        String confirm = confirmPasswordInput.getText().toString().trim();

        // Validation
        if (email.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Email validation
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!pass.equals(confirm)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pass.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button and show loading state
        btnStartNav.setEnabled(false);
        btnStartNav.setText("Creating Account...");

        firebaseAuth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    // Re-enable button
                    btnStartNav.setEnabled(true);
                    btnStartNav.setText("Create Account");

                    if(task.isSuccessful()){
                        Toast.makeText(this, "Account Created Successfully!", Toast.LENGTH_SHORT).show();

                        // Clear input fields
                        emailInput.setText("");
                        passwordInput.setText("");
                        confirmPasswordInput.setText("");

                        startActivity(new Intent(SignUp.this, Login.class));
                        finish();
                    } else {
                        String errorMessage = task.getException() != null ?
                                task.getException().getMessage() : "Registration failed";
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }
}