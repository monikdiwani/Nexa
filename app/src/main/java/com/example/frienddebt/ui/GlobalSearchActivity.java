package com.example.frienddebt.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.model.CashbookEntry;
import com.example.frienddebt.model.GlobalSearchResult;
import com.example.frienddebt.model.LedgerBook;
import com.example.frienddebt.model.Note;
import com.example.frienddebt.model.Reminder;
import com.example.frienddebt.model.Task;
import com.example.frienddebt.utils.StatusBarUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GlobalSearchActivity extends AppCompatActivity {

    private EditText etSearch;
    private ImageButton btnBack;
    private TextView chipAll, chipTasks, chipNotes, chipMoney, chipReminders, chipLedgers;
    private RecyclerView rvSearchResults;
    private ProgressBar progressBar;
    private LinearLayout layoutEmptyState;

    private GlobalSearchAdapter adapter;
    private FirebaseFirestore db;
    private String currentUserId;

    // In-memory data store for ultra-fast searching
    private final List<GlobalSearchResult> allData = new ArrayList<>();
    private final List<GlobalSearchResult> filteredData = new ArrayList<>();
    
    private String activeFilter = "ALL";
    private String currentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_global_search);
        StatusBarUtil.applyStatusBarPadding(this);

        db = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            finish();
            return;
        }

        initViews();
        setupFilters();
        setupSearchListener();
        
        // Load data asynchronously into memory
        fetchAllData();
    }

    private void initViews() {
        etSearch = findViewById(R.id.etSearch);
        btnBack = findViewById(R.id.btnBack);
        chipAll = findViewById(R.id.chipAll);
        chipTasks = findViewById(R.id.chipTasks);
        chipNotes = findViewById(R.id.chipNotes);
        chipMoney = findViewById(R.id.chipMoney);
        chipReminders = findViewById(R.id.chipReminders);
        chipLedgers = findViewById(R.id.chipLedgers);
        rvSearchResults = findViewById(R.id.rvSearchResults);
        progressBar = findViewById(R.id.progressBar);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);

        btnBack.setOnClickListener(v -> finish());

        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GlobalSearchAdapter(this::onResultClicked);
        rvSearchResults.setAdapter(adapter);
    }

    private void setupFilters() {
        chipAll.setOnClickListener(v -> setFilter("ALL", chipAll));
        chipTasks.setOnClickListener(v -> setFilter(GlobalSearchResult.TYPE_TASK, chipTasks));
        chipNotes.setOnClickListener(v -> setFilter(GlobalSearchResult.TYPE_NOTE, chipNotes));
        chipMoney.setOnClickListener(v -> setFilter(GlobalSearchResult.TYPE_MONEY, chipMoney));
        chipReminders.setOnClickListener(v -> setFilter(GlobalSearchResult.TYPE_REMINDER, chipReminders));
        chipLedgers.setOnClickListener(v -> setFilter(GlobalSearchResult.TYPE_LEDGER, chipLedgers));
    }

    private void setFilter(String filter, TextView activeChip) {
        activeFilter = filter;
        
        TextView[] chips = {chipAll, chipTasks, chipNotes, chipMoney, chipReminders, chipLedgers};
        for (TextView chip : chips) {
            chip.setBackgroundResource(R.drawable.chip_background);
            chip.setTextColor(getResources().getColor(R.color.text_secondary));
        }
        
        activeChip.setBackgroundResource(R.drawable.rounded_button);
        activeChip.setTextColor(getResources().getColor(R.color.white));
        
        applySearch();
    }

    private void setupSearchListener() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                currentQuery = s.toString().trim().toLowerCase(Locale.getDefault());
                applySearch();
            }
        });
    }

    private void fetchAllData() {
        progressBar.setVisibility(View.VISIBLE);
        allData.clear();

        // Count to know when all async fetches are done
        final int totalFetches = 4;
        final int[] completedFetches = {0};

        Runnable checkCompletion = () -> {
            completedFetches[0]++;
            if (completedFetches[0] == totalFetches) {
                progressBar.setVisibility(View.GONE);
                applySearch(); // Initial render
            }
        };

        // 1. Fetch Tasks
        db.collection("tasks").whereEqualTo("userId", currentUserId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                for (DocumentSnapshot doc : task.getResult()) {
                    Task t = Task.fromDocument(doc);
                    GlobalSearchResult res = new GlobalSearchResult(
                            t.getId(), GlobalSearchResult.TYPE_TASK, t.getTitle(),
                            t.getDescription() != null ? t.getDescription() : "", "✅", t.getCreatedAt()
                    );
                    res.setData(t);
                    allData.add(res);
                }
            }
            checkCompletion.run();
        });

        // 2. Fetch Notes
        db.collection("notes").whereEqualTo("userId", currentUserId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                for (DocumentSnapshot doc : task.getResult()) {
                    Note n = Note.fromDocument(doc);
                    String subtitle = n.getContent();
                    if (subtitle == null || subtitle.isEmpty()) {
                        subtitle = n.getLabel() != null ? n.getLabel() : "";
                    }
                    GlobalSearchResult res = new GlobalSearchResult(
                            n.getId(), GlobalSearchResult.TYPE_NOTE, n.getTitle(),
                            subtitle, "📝", n.getUpdatedAt()
                    );
                    res.setData(n);
                    allData.add(res);
                }
            }
            checkCompletion.run();
        });

        // 3. Fetch Reminders
        db.collection("reminders").whereEqualTo("userId", currentUserId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                for (DocumentSnapshot doc : task.getResult()) {
                    Reminder r = Reminder.fromDocument(doc);
                    String subtitle = "Due: " + new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new java.util.Date(r.getTriggerTime()));
                    GlobalSearchResult res = new GlobalSearchResult(
                            r.getId(), GlobalSearchResult.TYPE_REMINDER, r.getTitle(),
                            subtitle, "🔔", r.getTriggerTime()
                    );
                    res.setData(r);
                    allData.add(res);
                }
            }
            checkCompletion.run();
        });

        // 4. Fetch Ledgers & Cashbook Entries
        db.collection("cashbooks").whereNotEqualTo("members." + currentUserId, null).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                int ledgerCount = task.getResult().size();
                if (ledgerCount == 0) {
                    checkCompletion.run();
                    return;
                }
                
                final int[] entriesFetched = {0};

                for (DocumentSnapshot doc : task.getResult()) {
                    LedgerBook book = LedgerBook.fromDocument(doc);
                    GlobalSearchResult res = new GlobalSearchResult(
                            book.getId(), GlobalSearchResult.TYPE_LEDGER, book.getName(),
                            "Ledger • Net Balance: ₹" + book.getNetBalance(), "📒", book.getCreatedAt()
                    );
                    res.setData(book);
                    allData.add(res);

                    // Fetch entries for this book
                    db.collection("cashbooks").document(book.getId()).collection("entries").get().addOnCompleteListener(entryTask -> {
                        if (entryTask.isSuccessful() && entryTask.getResult() != null) {
                            for (DocumentSnapshot edoc : entryTask.getResult()) {
                                CashbookEntry ce = CashbookEntry.fromDocument(edoc);
                                String prefix = "CASH_IN".equals(ce.getType()) ? "+" : "-";
                                GlobalSearchResult eRes = new GlobalSearchResult(
                                        ce.getId(), GlobalSearchResult.TYPE_MONEY, ce.getParticulars(),
                                        "₹" + prefix + ce.getAmount() + " • " + ce.getCategory() + " (" + book.getName() + ")", 
                                        "💵", ce.getDate()
                                );
                                eRes.setParentId(book.getId());
                                eRes.setData(ce);
                                allData.add(eRes);
                            }
                        }
                        entriesFetched[0]++;
                        if (entriesFetched[0] == ledgerCount) {
                            checkCompletion.run();
                        }
                    });
                }
            } else {
                checkCompletion.run();
            }
        });
    }

    private void applySearch() {
        filteredData.clear();
        
        for (GlobalSearchResult item : allData) {
            // Apply Type Filter
            if (!activeFilter.equals("ALL") && !item.getType().equals(activeFilter)) {
                continue;
            }
            
            // Apply Text Filter
            if (!currentQuery.isEmpty()) {
                boolean matchesTitle = item.getTitle() != null && item.getTitle().toLowerCase(Locale.getDefault()).contains(currentQuery);
                boolean matchesSubtitle = item.getSubtitle() != null && item.getSubtitle().toLowerCase(Locale.getDefault()).contains(currentQuery);
                if (!matchesTitle && !matchesSubtitle) {
                    continue;
                }
            }
            
            filteredData.add(item);
        }

        // Sort by recency
        Collections.sort(filteredData, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        adapter.setResults(filteredData);
        
        if (filteredData.isEmpty() && progressBar.getVisibility() == View.GONE) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            rvSearchResults.setVisibility(View.GONE);
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            rvSearchResults.setVisibility(View.VISIBLE);
        }
    }

    private void onResultClicked(GlobalSearchResult result) {
        switch (result.getType()) {
            case GlobalSearchResult.TYPE_TASK:
                // Route to Tasks Activity (assuming we don't have a specific task detail view yet, just the main list)
                startActivity(new Intent(this, TasksActivity.class));
                break;
            case GlobalSearchResult.TYPE_NOTE:
                Intent noteIntent = new Intent(this, AddEditNoteActivity.class);
                noteIntent.putExtra("NOTE_ID", result.getId());
                startActivity(noteIntent);
                break;
            case GlobalSearchResult.TYPE_MONEY:
                Intent moneyIntent = new Intent(this, LedgerBookDetailActivity.class);
                moneyIntent.putExtra("BOOK_ID", result.getParentId());
                if (result.getData() instanceof CashbookEntry) {
                    CashbookEntry ce = (CashbookEntry) result.getData();
                    // Just pass the book name if we have it, else generic
                }
                startActivity(moneyIntent);
                break;
            case GlobalSearchResult.TYPE_LEDGER:
                Intent ledgerIntent = new Intent(this, LedgerBookDetailActivity.class);
                ledgerIntent.putExtra("BOOK_ID", result.getId());
                if (result.getData() instanceof LedgerBook) {
                    ledgerIntent.putExtra("BOOK_NAME", ((LedgerBook) result.getData()).getName());
                }
                startActivity(ledgerIntent);
                break;
            case GlobalSearchResult.TYPE_REMINDER:
                startActivity(new Intent(this, RemindersActivity.class));
                break;
        }
    }
}
