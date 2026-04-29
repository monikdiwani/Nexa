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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.model.LedgerBook;
import com.example.frienddebt.ui.CreateLedgerBookActivity;
import com.example.frienddebt.ui.LedgerBookDetailActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CashbookFragment extends Fragment {

    private RecyclerView rvLedgerBooks;
    private TextView txtEmptyBooks;
    private FloatingActionButton fabAddBook;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration booksListener;

    private List<LedgerBook> ledgerBooks = new ArrayList<>();
    private LedgerBookAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cashbook, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        rvLedgerBooks = view.findViewById(R.id.rvLedgerBooks);
        txtEmptyBooks = view.findViewById(R.id.txtEmptyBooks);
        fabAddBook = view.findViewById(R.id.fabAddBook);

        rvLedgerBooks.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new LedgerBookAdapter(ledgerBooks);
        rvLedgerBooks.setAdapter(adapter);

        fabAddBook.setOnClickListener(v -> {
            Animation pop = AnimationUtils.loadAnimation(requireContext(), R.anim.button_pop);
            v.startAnimation(pop);
            startActivity(new Intent(requireActivity(), CreateLedgerBookActivity.class));
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadBooks();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (booksListener != null) {
            booksListener.remove();
            booksListener = null;
        }
    }

    private void loadBooks() {
        if (auth == null || db == null || auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();

        if (booksListener != null) {
            booksListener.remove();
        }

        // Query all books where the current user is a member
        booksListener = db.collection("cashbooks")
                .whereNotEqualTo("members." + userId, null)
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;
                    ledgerBooks.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        ledgerBooks.add(LedgerBook.fromDocument(doc));
                    }
                    
                    adapter.notifyDataSetChanged();
                    
                    if (ledgerBooks.isEmpty()) {
                        txtEmptyBooks.setVisibility(View.VISIBLE);
                        rvLedgerBooks.setVisibility(View.GONE);
                    } else {
                        txtEmptyBooks.setVisibility(View.GONE);
                        rvLedgerBooks.setVisibility(View.VISIBLE);
                    }
                });
    }

    private class LedgerBookAdapter extends RecyclerView.Adapter<LedgerBookAdapter.ViewHolder> {
        private final List<LedgerBook> list;

        public LedgerBookAdapter(List<LedgerBook> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ledger_book, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LedgerBook book = list.get(position);
            
            holder.txtBookName.setText(book.getName() != null ? book.getName() : "Unnamed Book");
            
            String userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
            String role = book.getMembers() != null ? book.getMembers().get(userId) : "Unknown";
            holder.txtRole.setText("Role: " + role);
            
            holder.txtTotalIn.setText(String.format(Locale.getDefault(), "₹%.2f", book.getTotalCashIn()));
            holder.txtTotalOut.setText(String.format(Locale.getDefault(), "₹%.2f", book.getTotalCashOut()));
            holder.txtNetBalance.setText(String.format(Locale.getDefault(), "₹%.2f", book.getNetBalance()));
            
            if (book.getNetBalance() < 0) {
                holder.txtNetBalance.setTextColor(getResources().getColor(R.color.accent_negative));
            } else if (book.getNetBalance() > 0) {
                holder.txtNetBalance.setTextColor(getResources().getColor(R.color.accent_positive));
            } else {
                holder.txtNetBalance.setTextColor(getResources().getColor(R.color.text_primary));
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), LedgerBookDetailActivity.class);
                intent.putExtra("BOOK_ID", book.getId());
                intent.putExtra("BOOK_NAME", book.getName());
                intent.putExtra("USER_ROLE", role);
                intent.putExtra("TOTAL_IN", book.getTotalCashIn());
                intent.putExtra("TOTAL_OUT", book.getTotalCashOut());
                intent.putExtra("NET_BALANCE", book.getNetBalance());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtBookName, txtRole, txtTotalIn, txtTotalOut, txtNetBalance;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtBookName = itemView.findViewById(R.id.txtBookName);
                txtRole = itemView.findViewById(R.id.txtRole);
                txtTotalIn = itemView.findViewById(R.id.txtTotalIn);
                txtTotalOut = itemView.findViewById(R.id.txtTotalOut);
                txtNetBalance = itemView.findViewById(R.id.txtNetBalance);
            }
        }
    }
}
