package com.example.frienddebt.ui.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.frienddebt.R;
import com.example.frienddebt.ui.AddEditNoteActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class NoteStyleBottomSheet extends BottomSheetDialogFragment {

    private static final String[] SWATCH_COLORS = {
        null, // default (use null to mean "use theme background")
        "#3A3000",
        "#0D2A0D",
        "#0D1A2E",
        "#1A0D2E",
        "#2E0D1A",
        "#2E1A00",
        "#0A0A0A"
    };

    private static final String[] SWATCH_IDS_NAMES = {
        "swatchDefault", "swatchYellow", "swatchGreen", "swatchBlue",
        "swatchPurple", "swatchPink", "swatchOrange", "swatchDark"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_note_style_sheet, container, false);

        if (!(getActivity() instanceof AddEditNoteActivity)) return view;
        AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();

        // Color swatches
        int[] swatchIds = {
            R.id.swatchDefault, R.id.swatchYellow, R.id.swatchGreen, R.id.swatchBlue,
            R.id.swatchPurple, R.id.swatchPink, R.id.swatchOrange, R.id.swatchDark
        };

        for (int i = 0; i < swatchIds.length; i++) {
            final String colorHex = SWATCH_COLORS[i];
            View swatch = view.findViewById(swatchIds[i]);
            if (swatch != null) {
                swatch.setOnClickListener(v -> {
                    activity.applyColor(colorHex);
                    dismiss();
                });
            }
        }

        // Page style buttons
        view.findViewById(R.id.styleBlank).setOnClickListener(v -> {
            activity.applyPageStyle("blank");
            dismiss();
        });
        view.findViewById(R.id.styleLined).setOnClickListener(v -> {
            activity.applyPageStyle("lined");
            dismiss();
        });
        view.findViewById(R.id.styleDotted).setOnClickListener(v -> {
            activity.applyPageStyle("dotted");
            dismiss();
        });
        view.findViewById(R.id.styleGrid).setOnClickListener(v -> {
            activity.applyPageStyle("grid");
            dismiss();
        });

        return view;
    }
}
