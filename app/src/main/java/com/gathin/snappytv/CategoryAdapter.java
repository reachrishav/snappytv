package com.gathin.snappytv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {

    public interface OnCategoryListener {
        void onCategorySelected(String category, int position);
    }

    private final List<String> categories;
    private final OnCategoryListener listener;
    private int selectedPosition = 0;

    public CategoryAdapter(List<String> categories, OnCategoryListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    public void setSelectedPosition(int position) {
        int prev = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(prev);
        notifyItemChanged(selectedPosition);
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public String getCategoryAt(int position) {
        if (position >= 0 && position < categories.size()) {
            return categories.get(position);
        }
        return "";
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String category = categories.get(position);
        holder.categoryName.setText(category);
        holder.categoryName.setSelected(position == selectedPosition);

        holder.categoryName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                int old = selectedPosition;
                int currentPos = holder.getAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION && currentPos != old) {
                    selectedPosition = currentPos;
                    notifyItemChanged(old);
                    notifyItemChanged(selectedPosition);
                    if (listener != null && currentPos < categories.size()) {
                        listener.onCategorySelected(categories.get(currentPos), selectedPosition);
                    }
                }
            }
        });

        holder.categoryName.setOnClickListener(v -> {
            int old = selectedPosition;
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION && currentPos != old) {
                selectedPosition = currentPos;
                notifyItemChanged(old);
                notifyItemChanged(selectedPosition);
                if (listener != null && currentPos < categories.size()) {
                    listener.onCategorySelected(categories.get(currentPos), selectedPosition);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView categoryName;

        ViewHolder(View itemView) {
            super(itemView);
            categoryName = itemView.findViewById(R.id.categoryName);
        }
    }
}
