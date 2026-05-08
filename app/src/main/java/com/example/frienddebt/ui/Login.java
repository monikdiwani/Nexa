package com.example.frienddebt.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.frienddebt.R;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Login extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    EditText emailInput, passwordInput;
    Button btnStartNav, btnGoogleLogin;
    TextView forgotPassword, signUpText;
    FirebaseAuth firebaseAuth;
    CredentialManager credentialManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        firebaseAuth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(this);

        // Check if user is already logged in
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            navigateToMainActivity();
            return;
        }

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.editTextTextPassword);
        btnStartNav = findViewById(R.id.btnStartNav);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        forgotPassword = findViewById(R.id.forgot);
        signUpText = findViewById(R.id.signUpLink);

        // Fade-in animation
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        findViewById(R.id.main).startAnimation(fadeIn);

        // Spring animations
        com.example.frienddebt.utils.SpringAnimationUtil.applySpringEffect(btnStartNav);
        com.example.frienddebt.utils.SpringAnimationUtil.applySpringEffect(btnGoogleLogin);

        btnStartNav.setOnClickListener(v -> {
            userLogin();
        });

        btnGoogleLogin.setOnClickListener(v -> {
            signInWithGoogle();
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

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email");
            emailInput.requestFocus();
            return;
        }

        if (password.isEmpty() || password.length() < 6) {
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
                        Toast.makeText(this, "Login failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void signInWithGoogle() {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(true)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        Executor executor = ContextCompat.getMainExecutor(this);
        
        credentialManager.getCredentialAsync(this, request, null, executor, new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
            @Override
            public void onResult(GetCredentialResponse result) {
                try {
                    GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.getCredential().getData());
                    String idToken = googleIdTokenCredential.getIdToken();
                    firebaseAuthWithGoogle(idToken);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse Google ID Token: " + e.getMessage(), e);
                    Toast.makeText(Login.this, "Failed to parse credentials: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(@NonNull GetCredentialException e) {
                Log.e(TAG, "Google Sign-In Error: " + e.getMessage(), e);
                Toast.makeText(Login.this, "Google Sign-In Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(Login.this, "Google Login Successful", Toast.LENGTH_SHORT).show();
                        navigateToMainActivity();
                    } else {
                        String errMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(Login.this, "Firebase Auth Failed: " + errMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void navigateToMainActivity() {
        startActivity(new Intent(Login.this, DashboardActivity.class));
        finish();
    }
}