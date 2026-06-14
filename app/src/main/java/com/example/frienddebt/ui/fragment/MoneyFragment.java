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
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import com.example.frienddebt.model.CashbookEntry;
import com.example.frienddebt.model.LedgerBook;
import com.example.frienddebt.dsa.DebtSimplifier;
import com.example.frienddebt.model.DebtEdge;
import com.google.android.gms.tasks.Tasks;

/**
 * Unified Money fragment that hosts:
 * - A net balance header card
 * - Embedded CashbookFragment
 */
public class MoneyFragment extends Fragment {

    private TextView txtNetBalance, txtMoneyIn, txtMoneyOut;
    private android.widget.LinearLayout layoutPendingSettlements, layoutRecentActivity;
    private TextView btnViewSettlements;
    private androidx.recyclerview.widget.RecyclerView rvRecentTransactions;
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
        
        layoutPendingSettlements = view.findViewById(R.id.layoutPendingSettlements);
        btnViewSettlements = view.findViewById(R.id.btnViewSettlements);
        
        btnViewSettlements.setOnClickListener(v -> {
            startActivity(new android.content.Intent(requireContext(), com.example.frienddebt.ui.SettleUpActivity.class));
        });

        android.widget.ImageButton btnSearchMoney = view.findViewById(R.id.btnSearchMoney);
        if (btnSearchMoney != null) {
            btnSearchMoney.setOnClickListener(v -> startActivity(new android.content.Intent(requireContext(), com.example.frienddebt.ui.GlobalSearchActivity.class)));
        }

        android.widget.ImageButton btnBudgets = view.findViewById(R.id.btnBudgets);
        if (btnBudgets != null) {
            btnBudgets.setOnClickListener(v -> startActivity(new android.content.Intent(requireContext(), com.example.frienddebt.ui.BudgetsActivity.class)));
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
            showLedgerPickerForCashbookEntry(true);
        });

        sheetView.findViewById(R.id.btnActionAddExpense).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showLedgerPickerForCashbookEntry(false);
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

    private void showLedgerPickerForCashbookEntry(boolean isIncome) {
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();
        db.collection("cashbooks")
            .whereNotEqualTo("members." + userId, null)
            .limit(10)
            .get()
            .addOnSuccessListener(snapshots -> {
                if (snapshots.isEmpty()) {
                    android.widget.Toast.makeText(requireContext(), "Create a ledger first.", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                List<LedgerBook> books = new ArrayList<>();
                List<String> bookNames = new ArrayList<>();
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                    LedgerBook b = LedgerBook.fromDocument(doc);
                    books.add(b);
                    bookNames.add(b.getName());
                }
                new android.app.AlertDialog.Builder(requireContext())
                    .setTitle(isIncome ? "Select Ledger for Income" : "Select Ledger for Expense")
                    .setItems(bookNames.toArray(new String[0]), (dialog, which) -> {
                        LedgerBook selected = books.get(which);
                        android.content.Intent intent = new android.content.Intent(requireContext(), com.example.frienddebt.ui.AddCashbookEntryActivity.class);
                        intent.putExtra("BOOK_ID", selected.getId());
                        intent.putExtra("BOOK_NAME", selected.getName());
                        startActivity(intent);
                    })
                    .show();
            });
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

        // Update header totals when tab changes
        tabLayoutMoney.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                loadBalanceData();
            }
            @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });
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

        // Determine filter based on selected tab
        int selectedTab = tabLayoutMoney != null ? tabLayoutMoney.getSelectedTabPosition() : 2;
        // 0=Personal, 1=Shared, 2=All

        db.collection("cashbooks")
                .whereNotEqualTo("members." + userId, null)
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null || !isAdded()) return;

                    double cashIn = 0, cashOut = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                        boolean isShared = false;
                        java.util.Map<String, Object> members = (java.util.Map<String, Object>) doc.get("members");
                        if (members != null && members.size() > 1) isShared = true;

                        // Filter by tab
                        if (selectedTab == 0 && isShared) continue;   // Personal only
                        if (selectedTab == 1 && !isShared) continue;  // Shared only
                        // selectedTab == 2 = All, no filter

                        Double in = doc.getDouble("totalCashIn");
                        Double out = doc.getDouble("totalCashOut");
                        if (in != null) cashIn += in;
                        if (out != null) cashOut += out;
                    }

                    double net = cashIn - cashOut;
                    txtNetBalance.setText(String.format(Locale.getDefault(), "₹%.2f", net));
                    txtMoneyIn.setText(String.format(Locale.getDefault(), "₹%.0f", cashIn));
                    txtMoneyOut.setText(String.format(Locale.getDefault(), "₹%.0f", cashOut));

                    loadDeepInsights(snapshots.getDocuments());
                });
    }

    private void loadDeepInsights(List<com.google.firebase.firestore.DocumentSnapshot> bookDocs) {
        if (auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>> tasks = new ArrayList<>();
        List<LedgerBook> sharedBooks = new ArrayList<>();
        
        for (com.google.firebase.firestore.DocumentSnapshot bookDoc : bookDocs) {
            LedgerBook book = LedgerBook.fromDocument(bookDoc);
            if (book.getMembers() != null && book.getMembers().size() > 1) {
                sharedBooks.add(book);
            }
            tasks.add(db.collection("cashbooks").document(book.getId()).collection("entries").get());
        }

        if (tasks.isEmpty()) return;

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            List<CashbookEntry> allEntries = new ArrayList<>();
            for (Object res : results) {
                com.google.firebase.firestore.QuerySnapshot snap = (com.google.firebase.firestore.QuerySnapshot) res;
                for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                    allEntries.add(CashbookEntry.fromDocument(doc));
                }
            }
            
            // 1. Check Pending Settlements
            boolean hasPending = false;
            for (LedgerBook sBook : sharedBooks) {
                List<CashbookEntry> bookEntries = new ArrayList<>();
                for (CashbookEntry e : allEntries) {
                    if (sBook.getId().equals(e.getBookId())) bookEntries.add(e);
                }
                List<DebtEdge> edges = DebtSimplifier.simplifyDebts(bookEntries);
                for (DebtEdge edge : edges) {
                    if (edge.getFrom().equals(userId)) {
                        hasPending = true; break;
                    }
                }
                if (hasPending) break;
            }
            layoutPendingSettlements.setVisibility(hasPending ? View.VISIBLE : View.GONE);

            // 2. Recent Activity removed as per user request
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
