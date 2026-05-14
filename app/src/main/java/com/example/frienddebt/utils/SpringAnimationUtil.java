package com.example.frienddebt.utils;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;

import com.example.frienddebt.R;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

public class SpringAnimationUtil {

    @SuppressLint("ClickableViewAccessibility")
    public static void applySpringEffect(View view) {
        if (view == null) return;

        view.setOnTouchListener((v, event) -> {
            SpringAnimation animX = getOrCreateAnimation(view, DynamicAnimation.SCALE_X);
            SpringAnimation animY = getOrCreateAnimation(view, DynamicAnimation.SCALE_Y);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animX.animateToFinalPosition(0.94f);
                    animY.animateToFinalPosition(0.94f);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animX.animateToFinalPosition(1.0f);
                    animY.animateToFinalPosition(1.0f);
                    break;
            }
            return false;
        });
    }

    private static SpringAnimation getOrCreateAnimation(View view, DynamicAnimation.ViewProperty property) {
        int key = property == DynamicAnimation.SCALE_X ? R.id.spring_anim_x : R.id.spring_anim_y;
        SpringAnimation anim = (SpringAnimation) view.getTag(key);
        if (anim == null) {
            anim = new SpringAnimation(view, property, 1.0f);
            SpringForce force = new SpringForce(1.0f);
            force.setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            force.setStiffness(SpringForce.STIFFNESS_MEDIUM);
            anim.setSpring(force);
            view.setTag(key, anim);
        }
        return anim;
    }
}
