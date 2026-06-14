package com.example.frienddebt.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (isSheetActivity(targetClass)) {
                activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.sheet_slide_up, R.anim.hold);
            } else {
                activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left);
            }
        } else {
            if (isSheetActivity(targetClass)) {
                activity.overridePendingTransition(R.anim.sheet_slide_up, R.anim.hold);
            } else {
                activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        }
    }

    public static void applyFinishTransition(Activity activity) {
        if (activity == null) return;
        String currentClass = activity.getClass().getName();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (isSheetActivity(currentClass)) {
                activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, R.anim.hold, R.anim.sheet_slide_down);
            } else {
                activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right);
            }
        } else {
            if (isSheetActivity(currentClass)) {
                activity.overridePendingTransition(R.anim.hold, R.anim.sheet_slide_down);
            } else {
                activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            }
        }
    }
}
