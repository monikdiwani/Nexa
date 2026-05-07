package com.example.frienddebt.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.frienddebt.R;
import com.example.frienddebt.dsa.CashbookCalculator;
import com.example.frienddebt.model.CashbookEntry;
import com.example.frienddebt.ui.AddCashbookEntryActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Locale;

/**
 * Unified Money fragment that hosts:
 * - A net balance header card
 * - ViewPager2 with 3 sub-tabs: Personal | Groups | Overview
 * - Context-aware FAB (Add Transaction on Personal, hidden on others)
 */
public class MoneyFragment extends Fragment {

    private TextView txtNetBalance, txtMoneyIn, txtMoneyOut, txtYouOwe, txtOwedToYou;
    private TabLayout tabLayoutMoney;
    private ViewPager2 viewPagerMoney;
    private FloatingActionButton fabMoneyAdd;

    private CashbookFragment cashbookFragment;
    private MoneyGroupsFragment groupsFragment;
    private MoneyOverviewFragment overviewFragment;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private int currentTab = 0;

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
        tabLayoutMoney = view.findViewById(R.id.tabLayoutMoney);
        viewPagerMoney = view.findViewById(R.id.viewPagerMoney);
        fabMoneyAdd = view.findViewById(R.id.fabMoneyAdd);

        // Create child fragments
        cashbookFragment = new CashbookFragment();
        groupsFragment = new MoneyGroupsFragment();
        overviewFragment = new MoneyOverviewFragment();

        // ViewPager2 Adapter
        MoneyPagerAdapter pagerAdapter = new MoneyPagerAdapter(this);
        viewPagerMoney.setAdapter(pagerAdapter);

        // Connect TabLayout with ViewPager2
        String[] tabTitles = {"Ledgers", "Groups", "Overview"};
        new TabLayoutMediator(tabLayoutMoney, viewPagerMoney, (tab, position) -> {
            tab.setText(tabTitles[position]);
        }).attach();

        // Listen for tab changes to update FAB behavior
        viewPagerMoney.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentTab = position;
                updateFabVisibility(position);
            }
        });

        // FAB click — context-aware
        fabMoneyAdd.setOnClickListener(v -> {
            Animation pop = AnimationUtils.loadAnimation(requireContext(), R.anim.button_pop);
            v.startAnimation(pop);
            onFabClicked();
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadBalanceData();
    }

    /**
     * Called externally (e.g., from DashboardActivity) to switch to a specific sub-tab.
     * 0 = Personal, 1 = Groups, 2 = Overview
     */
    public void switchToTab(int tabIndex) {
        if (viewPagerMoney != null && tabIndex >= 0 && tabIndex <= 2) {
            viewPagerMoney.setCurrentItem(tabIndex, true);
        }
    }

    private void updateFabVisibility(int position) {
        if (position == 0 || position == 1) {
            fabMoneyAdd.show();
        } else {
            fabMoneyAdd.hide();
        }
    }

    private void onFabClicked() {
        if (currentTab == 0) {
            // Personal tab — add cashbook entry
            startActivity(new Intent(requireActivity(), com.example.frienddebt.ui.CreateLedgerBookActivity.class));
        } else if (currentTab == 1) {
            // Groups tab — create group (delegate to groups fragment)
            // The groups fragment has its own create group button, but FAB is a shortcut
            startActivity(new Intent(requireActivity(), AddCashbookEntryActivity.class));
        }
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

        // Group debts — simplified summary
        loadGroupDebtSummary(userId);
    }

    private void loadGroupDebtSummary(String userId) {
        // For now, show aggregate group expense as "You Owe" / "Owed"
        // This can be enhanced with per-user debt calculation later
        txtYouOwe.setText("₹0");
        txtOwedToYou.setText("₹0");

        db.collection("users")
                .document(userId)
                .collection("groups")
                .get()
                .addOnSuccessListener(groupSnap -> {
                    if (!isAdded()) return;
                    // For a quick summary, just count groups
                    int groupCount = groupSnap.size();
                    // You Owe / Owed fields stay ₹0 until we implement per-user settlement tracking
                });
    }

    // Legacy listener removed. For global balances, we would need to sum across all LedgerBooks.

    // ─── ViewPager2 Adapter ────────────────────────────────────────
    private class MoneyPagerAdapter extends FragmentStateAdapter {

        public MoneyPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return cashbookFragment;
                case 1: return groupsFragment;
                case 2: return overviewFragment;
                default: return cashbookFragment;
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}
