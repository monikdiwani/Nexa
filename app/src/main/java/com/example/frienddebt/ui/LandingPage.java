package com.example.frienddebt.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;

public class LandingPage extends AppCompatActivity {

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landing_page);

        // Remove EdgeToEdge.enable(this);
        // Remove ViewCompat.setOnApplyWindowInsetsListener() - this is what's crashing!

        // Fun entrance for landing content
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        findViewById(R.id.main).startAnimation(fadeIn);

        Button startButton = findViewById(R.id.btnStart);
        startButton.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_pop));
            Intent i = new Intent(LandingPage.this, Login.class);
            startActivity(i);
            overridePendingTransition(R.anim.slide_up, R.anim.fade_in);
        });
    }
}