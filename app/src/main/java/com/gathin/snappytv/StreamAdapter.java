package com.gathin.snappytv;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class StreamAdapter extends RecyclerView.Adapter<StreamAdapter.StreamViewHolder> {

    public interface OnStreamSelectedListener {
        void onStreamSelected(int position);
    }

    private final Channel channel;
    private int currentStreamIndex;
    private final OnStreamSelectedListener listener;
    private RecyclerView recyclerView;

    public StreamAdapter(Channel channel, int currentStreamIndex, OnStreamSelectedListener listener) {
        this.channel = channel;
        this.currentStreamIndex = currentStreamIndex;
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
        int count = getItemCount();
        if (position < 0 || position >= count) return;
        if (recyclerView != null && (recyclerView.isComputingLayout() || recyclerView.isAnimating())) {
            recyclerView.post(() -> notifyItemChanged(position));
        } else {
            notifyItemChanged(position);
        }
    }

    public void setCurrentStreamIndex(int index) {
        int prev = currentStreamIndex;
        this.currentStreamIndex = index;
        safeNotifyItemChanged(prev);
        safeNotifyItemChanged(currentStreamIndex);
    }

    @NonNull
    @Override
    public StreamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stream_option, parent, false);
        return new StreamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StreamViewHolder holder, int position) {
        if (channel == null) return;

        int rank = position + 1;
        holder.streamRankBadge.setText(String.valueOf(rank));

        String role = channel.getStreamRole(position);
        holder.streamTitle.setText("Stream " + rank + " • " + role);

        String source = channel.getStreamSource(position);
        holder.streamSource.setText("Source: " + source);

        boolean isActive = (position == currentStreamIndex);
        holder.streamActiveTag.setVisibility(isActive ? View.VISIBLE : View.GONE);
        holder.itemView.setSelected(isActive);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onStreamSelected(position);
            }
        });

        holder.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (listener != null) {
                        listener.onStreamSelected(position);
                    }
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return (channel != null) ? channel.getStreamCount() : 0;
    }

    static class StreamViewHolder extends RecyclerView.ViewHolder {
        final TextView streamRankBadge;
        final TextView streamTitle;
        final TextView streamSource;
        final TextView streamActiveTag;

        StreamViewHolder(@NonNull View itemView) {
            super(itemView);
            streamRankBadge = itemView.findViewById(R.id.streamRankBadge);
            streamTitle = itemView.findViewById(R.id.streamTitle);
            streamSource = itemView.findViewById(R.id.streamSource);
            streamActiveTag = itemView.findViewById(R.id.streamActiveTag);
        }
    }
}

