package com.example.frienddebt.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import java.lang.reflect.Method;
import com.example.frienddebt.R;

public class AnimationHelper {

    public static void playPopAnimation(Context context, View view) {
        if (view != null && context != null) {
            Animation pop = AnimationUtils.loadAnimation(context, R.anim.button_pop);
            view.startAnimation(pop);
        }
    }

    public static boolean isSheetActivity(String className) {
        if (className == null) return false;
        return className.contains("AddEditNoteActivity") ||
               className.contains("AddTaskActivity") ||
               className.contains("AddReminderActivity") ||
               className.contains("AddCashbookEntryActivity") ||
               className.contains("AddSharedExpenseActivity") ||
               className.contains("CreateLedgerBookActivity") ||
               className.contains("AddBudgetActivity") ||
               className.contains("SettleUpActivity");
    }

    public static void applyStartTransition(Activity activity, Intent intent) {
        if (activity == null || intent == null) return;
        String targetClass = "";
        if (intent.getComponent() != null) {
            targetClass = intent.getComponent().getClassName();
        }
        if (isSheetActivity(targetClass)) {
            applyTransition(activity, R.anim.sheet_slide_up, R.anim.hold);
        } else {
            applyTransition(activity, R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    public static void applyFinishTransition(Activity activity) {
        if (activity == null) return;
        String currentClass = activity.getClass().getName();
        if (isSheetActivity(currentClass)) {
            applyTransition(activity, R.anim.hold, R.anim.sheet_slide_down);
        } else {
            applyTransition(activity, R.anim.slide_in_left, R.anim.slide_out_right);
        }
    }

    private static void applyTransition(Activity activity, int enterResId, int exitResId) {
        if (activity == null) return;
        // Prefer new API on Android 14+ if available; fallback to overridePendingTransition
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // Use reflection to call overrideActivityTransition if present to retain compilation compatibility
                Method m = Activity.class.getMethod("overrideActivityTransition", int.class, int.class);
                m.invoke(activity, enterResId, exitResId);
                return;
            }
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {
            // fall through to fallback
        }

        activity.overridePendingTransition(enterResId, exitResId);
    }
}
