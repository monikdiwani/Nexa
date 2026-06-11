package com.example.frienddebt.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.frienddebt.R;
import com.example.frienddebt.model.CashbookEntry;
import com.example.frienddebt.ui.AddEditNoteActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

public class BottomSheetConvertCashbookFragment extends BottomSheetDialogFragment {

    private EditText etAmount, etDescription;
    private RadioGroup rgType;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bottom_sheet_convert_cashbook, container, false);

        etAmount = view.findViewById(R.id.etAmount);
        etDescription = view.findViewById(R.id.etDescription);
        rgType = view.findViewById(R.id.rgType);
        
        rgType.check(R.id.rbCashOut);

        if (getActivity() instanceof AddEditNoteActivity) {
            AddEditNoteActivity activity = (AddEditNoteActivity) getActivity();
            String title = activity.getNoteTitle();
            if (title != null && !title.isEmpty()) {
                etDescription.setText("Note: " + title);
            }
        }

        view.findViewById(R.id.btnCreateCashbook).setOnClickListener(v -> createEntry());

        return view;
    }

    private void createEntry() {
        String amountStr = etAmount.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();

        if (amountStr.isEmpty()) {
            etAmount.setError("Required");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            etAmount.setError("Invalid amount");
            return;
        }

        if (desc.isEmpty()) {
            etDescription.setError("Required");
            return;
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        if (getView() != null) {
            getView().findViewById(R.id.btnCreateCashbook).setEnabled(false);
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Find the user's first cashbook to save to
        db.collection("cashbooks").whereArrayContains("members", userId).limit(1).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(requireContext(), "You need to create a Cashbook first", Toast.LENGTH_LONG).show();
                        dismiss();
                        return;
                    }
                    
                    String bookId = queryDocumentSnapshots.getDocuments().get(0).getId();
                    saveToCashbook(bookId, amount, desc, userId, db);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to fetch cashbooks", Toast.LENGTH_SHORT).show();
                    dismiss();
                });
    }

    private void saveToCashbook(String bookId, double amount, String desc, String userId, FirebaseFirestore db) {
        String type = rgType.getCheckedRadioButtonId() == R.id.rbCashIn ? "CASH_IN" : "CASH_OUT";
        String targetEntryId = db.collection("cashbooks").document(bookId).collection("entries").document().getId();
        long now = System.currentTimeMillis();
        
        CashbookEntry entry = new CashbookEntry(
                targetEntryId, bookId, now, desc, type, "CASH", amount, "Other", "Converted from Note", now
        );
        entry.setCreatedBy(userId);
        
        db.collection("cashbooks").document(bookId).collection("entries").document(targetEntryId)
                .set(entry.toFirestoreMap())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(requireContext(), "Cashbook Entry Created", Toast.LENGTH_SHORT).show();
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to create entry", Toast.LENGTH_SHORT).show();
                    dismiss();
                });
    }
}
