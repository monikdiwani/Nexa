package com.example.frienddebt.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.frienddebt.R;
import com.example.frienddebt.ui.AddEditNoteActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class PageSettingsBottomSheet extends BottomSheetDialogFragment {

    // Font sizes: index 0 = 14sp, each step +1 → max index 14 = 28sp
    private static final int BASE_FONT_SIZE = 14;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bottom_sheet_page_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AddEditNoteActivity activity = (AddEditNoteActivity) requireActivity();

        SeekBar seekFontSize = view.findViewById(R.id.seekFontSize);
        TextView txtFontSizeValue = view.findViewById(R.id.txtFontSizeValue);

        // Initialize seek position from current font size
        int currentSize = activity.getCurrentFontSize();
        int progress = Math.max(0, Math.min(14, currentSize - BASE_FONT_SIZE));
        seekFontSize.setProgress(progress);
        txtFontSizeValue.setText(currentSize + "sp");

        seekFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int prog, boolean fromUser) {
                int size = BASE_FONT_SIZE + prog;
                txtFontSizeValue.setText(size + "sp");
                activity.setFontSize(size);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Line spacing buttons
        view.findViewById(R.id.spacingCompact).setOnClickListener(v -> activity.setLineSpacing(2f));
        view.findViewById(R.id.spacingNormal).setOnClickListener(v -> activity.setLineSpacing(5f));
        view.findViewById(R.id.spacingRelaxed).setOnClickListener(v -> activity.setLineSpacing(10f));

        // Paper type buttons
        view.findViewById(R.id.styleBlank).setOnClickListener(v -> {
            activity.applyPageStyle("blank");
            dismiss();
        });
        view.findViewById(R.id.styleLined).setOnClickListener(v -> {
            activity.applyPageStyle("lined");
            dismiss();
        });
        view.findViewById(R.id.styleGrid).setOnClickListener(v -> {
            activity.applyPageStyle("grid");
            dismiss();
        });
        view.findViewById(R.id.styleDotted).setOnClickListener(v -> {
            activity.applyPageStyle("dotted");
            dismiss();
        });
    }
}
