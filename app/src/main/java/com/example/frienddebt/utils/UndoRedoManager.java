package com.example.frienddebt.utils;

import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.widget.EditText;

import java.util.LinkedList;

public class UndoRedoManager {

    private final EditText editText;
    private final LinkedList<CharSequence> undoStack = new LinkedList<>();
    private final LinkedList<CharSequence> redoStack = new LinkedList<>();
    private static final int MAX_HISTORY_SIZE = 50;
    
    private boolean isUndoingOrRedoing = false;
    private Runnable saveStateRunnable;

    public UndoRedoManager(EditText editText) {
        this.editText = editText;
        
        // Save initial state
        undoStack.add(new SpannableStringBuilder(editText.getText()));

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUndoingOrRedoing) return;

                // Debounce saving state to avoid saving every single character
                editText.removeCallbacks(saveStateRunnable);
                saveStateRunnable = () -> saveState();
                editText.postDelayed(saveStateRunnable, 500); // 500ms delay
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Do nothing
            }
        });
    }

    public void saveState() {
        if (isUndoingOrRedoing) return;
        
        CharSequence currentState = new SpannableStringBuilder(editText.getText());
        
        // Only save if it's different from the last state
        if (!undoStack.isEmpty() && undoStack.getLast().toString().equals(currentState.toString())) {
            // It might be the same string but different spans. 
            // In a simple app, we can just save it anyway, or compare spans. 
            // Let's save it to be safe, as spans might have changed.
        }

        undoStack.add(currentState);
        if (undoStack.size() > MAX_HISTORY_SIZE) {
            undoStack.removeFirst();
        }
        redoStack.clear(); // Clear redo stack on new action
    }

    public void undo() {
        if (undoStack.size() <= 1) return; // Need at least the initial state

        isUndoingOrRedoing = true;

        CharSequence currentState = undoStack.removeLast();
        redoStack.add(currentState);

        CharSequence previousState = undoStack.getLast();
        
        int cursorPosition = editText.getSelectionStart();
        
        editText.setText(new SpannableStringBuilder(previousState));
        
        // Try to restore cursor position safely
        if (cursorPosition >= 0 && cursorPosition <= editText.length()) {
            editText.setSelection(cursorPosition);
        } else {
            editText.setSelection(editText.length());
        }

        isUndoingOrRedoing = false;
    }

    public void redo() {
        if (redoStack.isEmpty()) return;

        isUndoingOrRedoing = true;

        CharSequence nextState = redoStack.removeLast();
        undoStack.add(nextState);

        int cursorPosition = editText.getSelectionStart();

        editText.setText(new SpannableStringBuilder(nextState));

        // Try to restore cursor position safely
        if (cursorPosition >= 0 && cursorPosition <= editText.length()) {
            editText.setSelection(cursorPosition);
        } else {
            editText.setSelection(editText.length());
        }

        isUndoingOrRedoing = false;
    }
    
    public boolean canUndo() {
        return undoStack.size() > 1;
    }
    
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
}
