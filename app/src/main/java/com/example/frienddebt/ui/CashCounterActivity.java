package com.example.frienddebt.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.frienddebt.R;

import java.util.HashMap;
import java.util.Map;

import com.example.frienddebt.utils.StatusBarUtil;

public class CashCounterActivity extends AppCompatActivity {

    private LinearLayout llDenominations;
    private TextView txtTotalCounted, txtAppBalance, txtDifference;
    private ImageButton btnBack;

    private double appBalance = 0.0;
    private Map<Integer, Integer> counts = new HashMap<>();
    
    // Standard Indian Denominations
    private final int[] denominations = {500, 200, 100, 50, 20, 10, 5, 2, 1};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_counter);
        StatusBarUtil.applyStatusBarPadding(this);

        llDenominations = findViewById(R.id.llDenominations);
        txtTotalCounted = findViewById(R.id.txtTotalCounted);
        txtAppBalance = findViewById(R.id.txtAppBalance);
        txtDifference = findViewById(R.id.txtDifference);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        // Get app balance from intent
        appBalance = getIntent().getDoubleExtra("APP_BALANCE", 0.0);
        txtAppBalance.setText("₹" + appBalance);

        setupTable();
        calculateTotal();
    }

    private void setupTable() {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int denom : denominations) {
            counts.put(denom, 0);

            View row = inflater.inflate(R.layout.item_denomination_row, llDenominations, false);
            TextView txtDenomination = row.findViewById(R.id.txtDenomination);
            EditText etCount = row.findViewById(R.id.etCount);
            TextView txtRowTotal = row.findViewById(R.id.txtRowTotal);

            txtDenomination.setText("₹" + denom);

            etCount.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    int c = 0;
                    if (s != null && s.length() > 0) {
                        try {
                            c = Integer.parseInt(s.toString());
                        } catch (NumberFormatException e) {
                            c = 0;
                        }
                    }
                    counts.put(denom, c);
                    txtRowTotal.setText("₹" + (denom * c));
                    calculateTotal();
                }
            });

            llDenominations.addView(row);
        }
    }

    private void calculateTotal() {
        int totalPhysical = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            totalPhysical += (entry.getKey() * entry.getValue());
        }

        txtTotalCounted.setText("₹" + totalPhysical);

        double diff = totalPhysical - appBalance;
        if (diff > 0) {
            txtDifference.setText("Difference: +₹" + diff + " (Excess)");
            txtDifference.setTextColor(getResources().getColor(R.color.accent_positive));
        } else if (diff < 0) {
            txtDifference.setText("Difference: -₹" + Math.abs(diff) + " (Shortage)");
            txtDifference.setTextColor(getResources().getColor(R.color.accent_negative));
        } else {
            txtDifference.setText("Difference: ₹0 (Matched!)");
            txtDifference.setTextColor(getResources().getColor(R.color.text_primary));
        }
    }
}
