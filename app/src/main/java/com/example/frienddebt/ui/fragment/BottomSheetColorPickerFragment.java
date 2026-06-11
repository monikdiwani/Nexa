package com.example.frienddebt.ui.fragment;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.frienddebt.R;
import com.example.frienddebt.ui.AddEditNoteActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class BottomSheetColorPickerFragment extends BottomSheetDialogFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bottom_sheet_color_picker, container, false);

        LinearLayout layoutColors = view.findViewById(R.id.layoutColors);

        // First color maps to theme surface (#surface_primary)
        String[] colors = {"#surface_primary", "#FFCDD2", "#E1BEE7", "#BBDEFB", "#C8E6C9", "#FFF9C4", "#FFE0B2"};

        for (String colorHex : colors) {
            View colorView = new View(getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(120, 120);
            params.setMargins(16, 16, 16, 16);
            colorView.setLayoutParams(params);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            
            if (colorHex.equals("#surface_primary")) {
                int colorSurface = com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, Color.WHITE);
                gd.setColor(colorSurface);
                gd.setStroke(2, Color.parseColor("#CCCCCC")); // border to show it's white
            } else {
                gd.setColor(Color.parseColor(colorHex));
            }
            
            colorView.setBackground(gd);

            colorView.setOnClickListener(v -> {
                if (getActivity() instanceof AddEditNoteActivity) {
                    ((AddEditNoteActivity) getActivity()).applyColor(colorHex);
                }
                dismiss();
            });

            layoutColors.addView(colorView);
        }

        return view;
    }
}
