package com.example.frienddebt.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.frienddebt.R;
import com.example.frienddebt.model.GlobalSearchResult;
import java.util.ArrayList;
import java.util.List;

public class GlobalSearchAdapter extends RecyclerView.Adapter<GlobalSearchAdapter.ViewHolder> {

    private List<GlobalSearchResult> results = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(GlobalSearchResult result);
    }

    public GlobalSearchAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setResults(List<GlobalSearchResult> newResults) {
        this.results = newResults;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_global_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GlobalSearchResult result = results.get(position);
        
        holder.txtIcon.setText(result.getIcon() != null ? result.getIcon() : "🔍");
        holder.txtTitle.setText(result.getTitle());
        holder.txtSubtitle.setText(result.getSubtitle());
        holder.txtType.setText(result.getType());

        // Set type color based on type
        int typeColor;
        switch (result.getType()) {
            case GlobalSearchResult.TYPE_TASK:
                typeColor = holder.itemView.getContext().getResources().getColor(R.color.accent_positive);
                break;
            case GlobalSearchResult.TYPE_NOTE:
                typeColor = holder.itemView.getContext().getResources().getColor(R.color.accent_warning);
                break;
            case GlobalSearchResult.TYPE_MONEY:
                typeColor = holder.itemView.getContext().getResources().getColor(R.color.primary);
                break;
            case GlobalSearchResult.TYPE_REMINDER:
                typeColor = holder.itemView.getContext().getResources().getColor(R.color.accent_negative);
                break;
            case GlobalSearchResult.TYPE_LEDGER:
                typeColor = holder.itemView.getContext().getResources().getColor(R.color.text_secondary);
                break;
            default:
                typeColor = holder.itemView.getContext().getResources().getColor(R.color.text_primary);
                break;
        }
        holder.txtType.setTextColor(typeColor);

        holder.itemView.setOnClickListener(v -> listener.onItemClick(result));
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtIcon, txtTitle, txtSubtitle, txtType;

        ViewHolder(View itemView) {
            super(itemView);
            txtIcon = itemView.findViewById(R.id.txtResultIcon);
            txtTitle = itemView.findViewById(R.id.txtResultTitle);
            txtSubtitle = itemView.findViewById(R.id.txtResultSubtitle);
            txtType = itemView.findViewById(R.id.txtResultType);
        }
    }
}
