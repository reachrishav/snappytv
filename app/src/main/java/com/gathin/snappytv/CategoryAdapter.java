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
    private RecyclerView recyclerView;

    public CategoryAdapter(List<String> categories, OnCategoryListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        this.recyclerView = null;
    }

    public void safeNotifyDataSetChanged() {
        if (recyclerView != null && (recyclerView.isComputingLayout() || recyclerView.isAnimating())) {
            recyclerView.post(this::notifyDataSetChanged);
        } else {
            notifyDataSetChanged();
        }
    }

    public void safeNotifyItemChanged(int position) {
        if (position < 0 || position >= categories.size()) return;
        if (recyclerView != null && (recyclerView.isComputingLayout() || recyclerView.isAnimating())) {
            recyclerView.post(() -> notifyItemChanged(position));
        } else {
            notifyItemChanged(position);
        }
    }

    public void setSelectedPosition(int position) {
        int prev = selectedPosition;
        selectedPosition = position;
        safeNotifyItemChanged(prev);
        safeNotifyItemChanged(selectedPosition);
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
                    safeNotifyItemChanged(old);
                    safeNotifyItemChanged(selectedPosition);
                    if (listener != null && currentPos < categories.size()) {
                        String cat = categories.get(currentPos);
                        v.post(() -> listener.onCategorySelected(cat, currentPos));
                    }
                }
            }
        });

        holder.categoryName.setOnClickListener(v -> {
            int old = selectedPosition;
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION && currentPos != old) {
                selectedPosition = currentPos;
                safeNotifyItemChanged(old);
                safeNotifyItemChanged(selectedPosition);
                if (listener != null && currentPos < categories.size()) {
                    String cat = categories.get(currentPos);
                    v.post(() -> listener.onCategorySelected(cat, currentPos));
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
