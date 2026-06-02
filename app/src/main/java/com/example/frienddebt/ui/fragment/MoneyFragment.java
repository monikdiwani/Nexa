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

    private TextView txtNetBalance, txtMoneyIn, txtMoneyOut;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private com.google.android.material.tabs.TabLayout tabLayoutMoney;
    private androidx.viewpager2.widget.ViewPager2 viewPagerMoney;

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

        android.widget.ImageButton btnSearchMoney = view.findViewById(R.id.btnSearchMoney);
        if (btnSearchMoney != null) {
            btnSearchMoney.setOnClickListener(v -> startActivity(new android.content.Intent(requireContext(), com.example.frienddebt.ui.GlobalSearchActivity.class)));
        }

        tabLayoutMoney = view.findViewById(R.id.tabLayoutMoney);
        viewPagerMoney = view.findViewById(R.id.viewPagerMoney);

        setupTabs();

        com.google.android.material.floatingactionbutton.FloatingActionButton fabMoneyActions = view.findViewById(R.id.fabMoneyActions);
        if (fabMoneyActions != null) {
            fabMoneyActions.setOnClickListener(v -> showMoneyActionsBottomSheet());
        }

        return view;
    }

    private void showMoneyActionsBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_money_actions, null);

        sheetView.findViewById(R.id.btnActionAddIncome).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            // Default to personal add cashbook entry (Wait, AddCashbookEntryActivity currently requires BOOK_ID)
            // Let's just launch CreateLedgerBookActivity if no book is selected, or a ledger selector. 
            // For now, if the user doesn't pass a BOOK_ID to AddCashbookEntryActivity, it might fail.
            // Ideally we pass an intent without BOOK_ID and let the activity handle ledger selection, 
            // or we just toast for now until we build the universal flow.
            android.widget.Toast.makeText(requireContext(), "Select a ledger first to add income.", android.widget.Toast.LENGTH_SHORT).show();
        });

        sheetView.findViewById(R.id.btnActionAddExpense).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            android.widget.Toast.makeText(requireContext(), "Select a ledger first to add expense.", android.widget.Toast.LENGTH_SHORT).show();
        });

        sheetView.findViewById(R.id.btnActionAddSharedExpense).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            startActivity(new android.content.Intent(requireActivity(), com.example.frienddebt.ui.AddSharedExpenseActivity.class));
        });

        sheetView.findViewById(R.id.btnActionSettleUp).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            startActivity(new android.content.Intent(requireActivity(), com.example.frienddebt.ui.SettleUpActivity.class));
        });

        sheetView.findViewById(R.id.btnActionCreateLedger).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            startActivity(new android.content.Intent(requireActivity(), com.example.frienddebt.ui.CreateLedgerBookActivity.class));
        });

        sheetView.findViewById(R.id.btnActionJoinLedger).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            startActivity(new android.content.Intent(requireActivity(), com.example.frienddebt.ui.JoinGroupActivity.class));
        });

        bottomSheetDialog.setContentView(sheetView);
        bottomSheetDialog.show();
    }

    private void setupTabs() {
        MoneyPagerAdapter adapter = new MoneyPagerAdapter(this);
        viewPagerMoney.setAdapter(adapter);

        new com.google.android.material.tabs.TabLayoutMediator(tabLayoutMoney, viewPagerMoney,
                (tab, position) -> {
                    switch (position) {
                        case 0: tab.setText("Personal"); break;
                        case 1: tab.setText("Shared"); break;
                        case 2: tab.setText("All"); break;
                    }
                }).attach();
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

    private static class MoneyPagerAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {
        public MoneyPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            CashbookFragment fragment = new CashbookFragment();
            Bundle args = new Bundle();
            switch (position) {
                case 0: args.putString("FILTER", "PERSONAL"); break;
                case 1: args.putString("FILTER", "SHARED"); break;
                case 2: args.putString("FILTER", "ALL"); break;
            }
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}
