package com.example.frienddebt.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.frienddebt.R;
import com.example.frienddebt.model.Budget;

import java.util.List;
import java.util.Locale;

public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder> {

    private Context context;
    private List<Budget> budgetList;
    private OnBudgetInteractionListener listener;

    public interface OnBudgetInteractionListener {
        void onDeleteClick(Budget budget);
        void onEditClick(Budget budget);
    }

    public BudgetAdapter(Context context, List<Budget> budgetList, OnBudgetInteractionListener listener) {
        this.context = context;
        this.budgetList = budgetList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BudgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_budget, parent, false);
        return new BudgetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BudgetViewHolder holder, int position) {
        Budget budget = budgetList.get(position);

        holder.tvCategoryName.setText(budget.getCategory());
        holder.tvPeriod.setText(budget.getPeriod());

        holder.tvSpentAmount.setText(String.format(Locale.getDefault(), "₹%.0f spent", budget.getSpentAmount()));
        holder.tvLimitAmount.setText(String.format(Locale.getDefault(), "of ₹%.0f", budget.getAmountLimit()));

        double remaining = budget.getAmountLimit() - budget.getSpentAmount();
        if (remaining < 0) {
            holder.tvRemainingStatus.setText(String.format(Locale.getDefault(), "Over budget by ₹%.0f", Math.abs(remaining)));
            holder.tvRemainingStatus.setTextColor(Color.parseColor("#FF5252"));
        } else {
            holder.tvRemainingStatus.setText(String.format(Locale.getDefault(), "₹%.0f left", remaining));
            holder.tvRemainingStatus.setTextColor(Color.parseColor("#757575")); // Secondary text color
        }

        int progress = (int) ((budget.getSpentAmount() / budget.getAmountLimit()) * 100);
        holder.pbBudget.setProgress(Math.min(progress, 100)); // Cap at 100%

        // Color coding
        if (progress >= 100) {
            holder.pbBudget.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF5252"))); // Red
        } else if (progress >= 80) {
            holder.pbBudget.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FFA000"))); // Orange
        } else {
            holder.pbBudget.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50"))); // Green
        }

        holder.btnDeleteBudget.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(budget);
        });

        // Full card tap = edit
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEditClick(budget);
        });
    }

    @Override
    public int getItemCount() {
        return budgetList != null ? budgetList.size() : 0;
    }

    public static class BudgetViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategoryName, tvPeriod, tvSpentAmount, tvLimitAmount, tvRemainingStatus;
        ProgressBar pbBudget;
        ImageView ivCategoryIcon;
        ImageButton btnDeleteBudget;

        public BudgetViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvPeriod = itemView.findViewById(R.id.tvPeriod);
            tvSpentAmount = itemView.findViewById(R.id.tvSpentAmount);
            tvLimitAmount = itemView.findViewById(R.id.tvLimitAmount);
            tvRemainingStatus = itemView.findViewById(R.id.tvRemainingStatus);
            pbBudget = itemView.findViewById(R.id.pbBudget);
            ivCategoryIcon = itemView.findViewById(R.id.ivCategoryIcon);
            btnDeleteBudget = itemView.findViewById(R.id.btnDeleteBudget);
        }
    }
}
