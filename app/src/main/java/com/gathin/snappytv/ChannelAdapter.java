package com.gathin.snappytv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    public interface OnChannelActionListener {
        void onChannelClick(Channel channel, int position);
        void onChannelLongClick(Channel channel, int position);
    }

    private final List<Channel> channels;
    private final OnChannelActionListener listener;
    private String activeChannelId = "";
    private RecyclerView recyclerView;

    public ChannelAdapter(OnChannelActionListener listener) {
        this.channels = new ArrayList<>();
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
        if (position < 0 || position >= channels.size()) return;
        if (recyclerView != null && (recyclerView.isComputingLayout() || recyclerView.isAnimating())) {
            recyclerView.post(() -> notifyItemChanged(position));
        } else {
            notifyItemChanged(position);
        }
    }

    public void setChannels(List<Channel> newChannels) {
        channels.clear();
        if (newChannels != null) {
            channels.addAll(newChannels);
        }
        safeNotifyDataSetChanged();
    }

    public void setActiveChannelId(String channelId) {
        String newId = channelId != null ? channelId : "";
        if (this.activeChannelId.equals(newId)) {
            return;
        }
        String oldId = this.activeChannelId;
        this.activeChannelId = newId;

        int oldPos = -1;
        int newPos = -1;
        for (int i = 0; i < channels.size(); i++) {
            String id = channels.get(i).getId();
            if (id.equals(oldId)) oldPos = i;
            if (id.equals(newId)) newPos = i;
            if (oldPos != -1 && newPos != -1) break;
        }

        if (oldPos != -1) safeNotifyItemChanged(oldPos);
        if (newPos != -1) safeNotifyItemChanged(newPos);
        if (oldPos == -1 && newPos == -1) {
            safeNotifyDataSetChanged();
        }
    }

    public Channel getChannelAt(int position) {
        if (position >= 0 && position < channels.size()) {
            return channels.get(position);
        }
        return null;
    }

    public int getPositionForChannel(Channel channel) {
        if (channel == null) return -1;
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).getId().equals(channel.getId())) return i;
        }
        return -1;
    }

    public void notifyChannelUpdated(int position) {
        safeNotifyItemChanged(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel channel = channels.get(position);
        boolean isActive = channel.getId().equals(activeChannelId);

        holder.channelNumber.setText(String.format("%02d", position + 1));
        holder.channelName.setText(channel.getName());

        int streamCount = channel.getStreamCount();
        String subtext = channel.getDisplayCategory() + (streamCount > 1 ? (" • " + streamCount + " feeds") : "");
        holder.channelSubtext.setText(subtext);

        holder.channelRow.setSelected(isActive);
        holder.badgeLive.setVisibility(isActive ? View.VISIBLE : View.GONE);

        // Favorite Star
        if (channel.isFavorite()) {
            holder.starFavorite.setVisibility(View.VISIBLE);
            holder.starFavorite.setColorFilter(0xFFFFD700); // Gold
        } else {
            holder.starFavorite.setVisibility(View.GONE);
        }

        holder.channelRow.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChannelClick(channel, holder.getAdapterPosition());
            }
        });

        holder.channelRow.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onChannelLongClick(channel, holder.getAdapterPosition());
            }
            return true;
        });

        holder.channelRow.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 1) {
                    if (listener != null) {
                        listener.onChannelLongClick(channel, holder.getAdapterPosition());
                    }
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View channelRow;
        final TextView channelNumber;
        final TextView channelName;
        final TextView channelSubtext;
        final ImageView starFavorite;
        final TextView badgeLive;

        ViewHolder(View itemView) {
            super(itemView);
            channelRow = itemView.findViewById(R.id.channelRow);
            channelNumber = itemView.findViewById(R.id.channelNumber);
            channelName = itemView.findViewById(R.id.channelName);
            channelSubtext = itemView.findViewById(R.id.channelSubtext);
            starFavorite = itemView.findViewById(R.id.starFavorite);
            badgeLive = itemView.findViewById(R.id.badgeLive);
        }
    }
}
