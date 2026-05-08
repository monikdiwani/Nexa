package com.example.frienddebt.utils;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

public class SpringAnimationUtil {

    @SuppressLint("ClickableViewAccessibility")
    public static void applySpringEffect(View view) {
        if (view == null) return;

        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    createSpringAnimation(view, DynamicAnimation.SCALE_X, 0.94f).start();
                    createSpringAnimation(view, DynamicAnimation.SCALE_Y, 0.94f).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    createSpringAnimation(view, DynamicAnimation.SCALE_X, 1.0f).start();
                    createSpringAnimation(view, DynamicAnimation.SCALE_Y, 1.0f).start();
                    break;
            }
            return false;
        });
    }

    private static SpringAnimation createSpringAnimation(View view, 
                                                         DynamicAnimation.ViewProperty property, 
                                                         float finalPosition) {
        SpringAnimation anim = new SpringAnimation(view, property, finalPosition);
        SpringForce force = new SpringForce();
        force.setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        force.setStiffness(SpringForce.STIFFNESS_MEDIUM);
        anim.setSpring(force);
        return anim;
    }
}
