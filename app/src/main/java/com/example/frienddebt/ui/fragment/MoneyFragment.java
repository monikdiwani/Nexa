package com.example.frienddebt.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.frienddebt.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

/**
 * Unified Money fragment that hosts:
 * - A net balance header card
 * - Embedded CashbookFragment
 */
public class MoneyFragment extends Fragment {

    private TextView txtNetBalance, txtMoneyIn, txtMoneyOut, txtYouOwe, txtOwedToYou;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_money, container, false);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind views
        txtNetBalance = view.findViewById(R.id.txtNetBalance);
        txtMoneyIn = view.findViewById(R.id.txtMoneyIn);
        txtMoneyOut = view.findViewById(R.id.txtMoneyOut);
        txtYouOwe = view.findViewById(R.id.txtYouOwe);
        txtOwedToYou = view.findViewById(R.id.txtOwedToYou);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadBalanceData();
    }

    /**
     * Loads balance data for the header card from Firestore.
     */
    public void loadBalanceData() {
        if (auth == null || auth.getCurrentUser() == null || db == null) return;
        String userId = auth.getCurrentUser().getUid();

        // Cashbook data for net balance
        db.collection("cashbooks")
                .whereNotEqualTo("members." + userId, null)
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null || !isAdded()) return;

                    double cashIn = 0, cashOut = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                        Double in = doc.getDouble("totalCashIn");
                        Double out = doc.getDouble("totalCashOut");
                        if (in != null) cashIn += in;
                        if (out != null) cashOut += out;
                    }

                    double net = cashIn - cashOut;
                    txtNetBalance.setText(String.format(Locale.getDefault(), "₹%.2f", net));
                    txtMoneyIn.setText(String.format(Locale.getDefault(), "₹%.0f", cashIn));
                    txtMoneyOut.setText(String.format(Locale.getDefault(), "₹%.0f", cashOut));
                });
    }
}
