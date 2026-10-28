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

    public ChannelAdapter(OnChannelActionListener listener) {
        this.channels = new ArrayList<>();
        this.listener = listener;
    }

    public void setChannels(List<Channel> newChannels) {
        channels.clear();
        if (newChannels != null) {
            channels.addAll(newChannels);
        }
        notifyDataSetChanged();
    }

    public void setActiveChannelId(String channelId) {
        this.activeChannelId = channelId != null ? channelId : "";
        notifyDataSetChanged();
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
        if (position >= 0 && position < channels.size()) {
            notifyItemChanged(position);
        }
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
