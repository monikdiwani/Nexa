package com.example.frienddebt.utils;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class StatusBarUtil {

    /**
     * Enables edge-to-edge display and applies correct inset padding to the root view.
     *
     * Key behaviour:
     *   • Top    → status bar height (so content doesn't sit under the clock/battery bar)
     *   • Bottom → MAX(nav-bar height, keyboard/IME height)
     *             When the soft keyboard is open the IME inset is larger than the nav-bar
     *             inset, so the layout root is padded by exactly the keyboard height.
     *             This keeps the bottom formatting bar always visible above the keyboard.
     *   • Left/Right → display cutout / nav-bar side insets
     *
     * This replaces the old approach that only used systemBars() and accidentally consumed
     * the IME inset, which caused the bottom formatting bar to hide under the keyboard.
     */
    public static void applyStatusBarPadding(Activity activity) {
        if (activity == null) return;

        // 1. Enable edge-to-edge (calls setDecorFitsSystemWindows(false) internally).
        //    After this call, the activity window extends behind the status and nav bars,
        //    and ALL insets (including IME) are delivered via ViewCompat inset listeners.
        if (activity instanceof androidx.activity.ComponentActivity) {
            EdgeToEdge.enable((androidx.activity.ComponentActivity) activity);
        }

        // 2. Apply insets to the root content view.
        View contentRoot = activity.findViewById(android.R.id.content);
        if (!(contentRoot instanceof ViewGroup)) return;
        ViewGroup viewGroup = (ViewGroup) contentRoot;
        if (viewGroup.getChildCount() == 0) return;

        View rootView = viewGroup.getChildAt(0);
        if (rootView == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            // System bars: status bar top, nav bar bottom/sides
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // IME (soft keyboard) insets — non-zero only when keyboard is visible
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());

            // Bottom padding = whichever is larger: nav bar or keyboard.
            // When the keyboard is open, imeInsets.bottom > sysBars.bottom,
            // so the layout shrinks to leave the keyboard area + the formatting bar visible.
            int bottomPad = Math.max(sysBars.bottom, imeInsets.bottom);

            v.setPadding(sysBars.left, sysBars.top, sysBars.right, bottomPad);

            // Return CONSUMED so child views don't receive duplicate inset padding.
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
