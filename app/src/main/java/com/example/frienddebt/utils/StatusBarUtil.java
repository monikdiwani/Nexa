package com.example.frienddebt.utils;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class StatusBarUtil {

    public static void applyStatusBarPadding(Activity activity) {
        if (activity == null) return;
        View contentRoot = activity.findViewById(android.R.id.content);
        if (contentRoot instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) contentRoot;
            if (viewGroup.getChildCount() > 0) {
                View rootView = viewGroup.getChildAt(0);
                if (rootView != null) {
                    ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                        Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                        v.setPadding(systemBars.left, systemBars.top, systemBars.right, v.getPaddingBottom());
                        return insets;
                    });
                }
            }
        }
    }
}
