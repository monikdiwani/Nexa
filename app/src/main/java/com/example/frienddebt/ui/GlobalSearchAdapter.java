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

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import java.util.Locale;

public class GlobalSearchAdapter extends RecyclerView.Adapter<GlobalSearchAdapter.ViewHolder> {

    private String currentQuery = "";

    private List<GlobalSearchResult> results = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(GlobalSearchResult result);
    }

    public GlobalSearchAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setResults(List<GlobalSearchResult> newResults, String query) {
        this.results = newResults;
        this.currentQuery = query != null ? query.toLowerCase(Locale.getDefault()) : "";
        notifyDataSetChanged();
    }

    private CharSequence highlightText(String text, int color) {
        if (text == null) return "";
        if (currentQuery.isEmpty()) return text;
        
        String lowerText = text.toLowerCase(Locale.getDefault());
        int start = lowerText.indexOf(currentQuery);
        if (start < 0) return text;
        
        SpannableString spannable = new SpannableString(text);
        spannable.setSpan(new ForegroundColorSpan(color), start, start + currentQuery.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
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
        
        int primaryColor = holder.itemView.getContext().getResources().getColor(R.color.primary);
        holder.txtTitle.setText(highlightText(result.getTitle(), primaryColor));
        holder.txtSubtitle.setText(highlightText(result.getSubtitle(), primaryColor));
        
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
