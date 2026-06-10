package com.example.frienddebt.ui;

import android.content.Intent;
import android.os.Bundle;
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
import com.example.frienddebt.utils.StatusBarUtil;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import android.util.Log;
import java.util.concurrent.Executor;

public class SignUp extends AppCompatActivity {

    private static final String TAG = "SignUpActivity";
    EditText nameInput, emailInput, passwordInput, confirmPasswordInput;
    Button btnStartNav, btnGoogleSignUp;
    TextView tvLogin;
    FirebaseAuth firebaseAuth;
    CredentialManager credentialManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
        StatusBarUtil.applyStatusBarPadding(this);

        firebaseAuth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(this);

        nameInput             = findViewById(R.id.nameInput);
        emailInput            = findViewById(R.id.emailInput);
        passwordInput         = findViewById(R.id.editTextTextPassword);
        confirmPasswordInput  = findViewById(R.id.confrimPassword);
        btnStartNav           = findViewById(R.id.btnStartNav);
        btnGoogleSignUp       = findViewById(R.id.btnGoogleSignUp);
        tvLogin               = findViewById(R.id.textView4);

        // Animate the whole screen on entry
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        findViewById(R.id.main).startAnimation(fadeIn);

        // Spring animations
        com.example.frienddebt.utils.SpringAnimationUtil.applySpringEffect(btnStartNav);
        com.example.frienddebt.utils.SpringAnimationUtil.applySpringEffect(btnGoogleSignUp);

        btnStartNav.setOnClickListener(v -> {
            createUser();
        });

        btnGoogleSignUp.setOnClickListener(v -> {
            signInWithGoogle();
        });

        // Handle "Already have an account? Login" click
        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignUp.this, Login.class));
            finish();
        });
    }

    private void createUser() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String pass = passwordInput.getText().toString().trim();
        String confirm = confirmPasswordInput.getText().toString().trim();

        // Validation
        if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
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
                    if (task.isSuccessful()) {
                        com.google.firebase.auth.FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null) {
                            com.google.firebase.auth.UserProfileChangeRequest profileUpdates = new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();
                            user.updateProfile(profileUpdates).addOnCompleteListener(profileTask -> {
                                btnStartNav.setEnabled(true);
                                btnStartNav.setText("Create Account");
                                Toast.makeText(SignUp.this, "Account Created Successfully!", Toast.LENGTH_SHORT).show();

                                // Clear input fields
                                nameInput.setText("");
                                emailInput.setText("");
                                passwordInput.setText("");
                                confirmPasswordInput.setText("");

                                // Redirect straight to Dashboard
                                startActivity(new Intent(SignUp.this, DashboardActivity.class));
                                finish();
                            });
                        } else {
                            btnStartNav.setEnabled(true);
                            btnStartNav.setText("Create Account");
                            Toast.makeText(this, "Account Created Successfully!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(SignUp.this, DashboardActivity.class));
                            finish();
                        }
                    } else {
                        btnStartNav.setEnabled(true);
                        btnStartNav.setText("Create Account");
                        String errorMessage = task.getException() != null ?
                                task.getException().getMessage() : "Registration failed";
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
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
                    Toast.makeText(SignUp.this, "Failed to parse credentials: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(@NonNull GetCredentialException e) {
                Log.e(TAG, "Google Sign-In Error: " + e.getMessage(), e);
                Toast.makeText(SignUp.this, "Google Sign-In Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(SignUp.this, "Google Registration Successful", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SignUp.this, DashboardActivity.class));
                        finish();
                    } else {
                        String errMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(SignUp.this, "Firebase Auth Failed: " + errMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    public void startActivity(android.content.Intent intent) {
        super.startActivity(intent);
        com.example.frienddebt.utils.AnimationHelper.applyStartTransition(this, intent);
    }

    @Override
    public void finish() {
        super.finish();
        com.example.frienddebt.utils.AnimationHelper.applyFinishTransition(this);
    }

}
