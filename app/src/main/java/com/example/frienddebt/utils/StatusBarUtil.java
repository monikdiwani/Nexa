package com.example.frienddebt.utils;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class StatusBarUtil {

    public static void applyStatusBarPadding(Activity activity) {
        if (activity == null) return;
        
        // 1. Force window edge-to-edge layout and raw inset dispatching
        EdgeToEdge.enable(activity);
        
        // 2. Programmatically apply status bar/notch padding to the layout root view
        View contentRoot = activity.findViewById(android.R.id.content);
        if (contentRoot instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) contentRoot;
            if (viewGroup.getChildCount() > 0) {
                View rootView = viewGroup.getChildAt(0);
                if (rootView != null) {
                    ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                        Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                        v.setPadding(systemBars.left, systemBars.top, systemBars.right, v.getPaddingBottom());
                        return WindowInsetsCompat.CONSUMED;
                    });
                }
            }
        }
    }
}
