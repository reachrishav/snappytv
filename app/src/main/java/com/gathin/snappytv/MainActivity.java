package com.gathin.snappytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements PlayerManager.Listener {

    private static final String TAG = "MainActivity";
    private static final String PREF_NAME = "snappytv_prefs";
    private static final String KEY_LAST_CHANNEL_ID = "last_channel_id";
    private static final String KEY_LAST_CATEGORY = "last_category";

    private PlayerView playerView;
    private ProgressBar bufferingSpinner;
    private ViewGroup bottomDthBar;
    private TextView hudChannelNumber, hudChannelName, hudCategory, hudResolution, hudStreamInfo;
    private TextView hudClockTime, hudClockDate;
    private View btnHudPlayPause;
    private ImageView hudPlayPauseIcon;
    private View btnHudFavorite;
    private ImageView hudFavIcon;
    private View pauseOverlayBadge;
    private TextView statusToast;

    // Minimal Stream Switcher in HUD (just numbers like 1/6)
    private TextView btnHudStreamSwitch;

    // Stream Selection Modal Popup
    private FrameLayout streamSelectorOverlay;
    private View streamPopupCard;
    private TextView streamPopupTitle, streamPopupCount, streamPopupSubtitle;
    private RecyclerView streamOptionsRecyclerView;
    private StreamAdapter streamAdapter;

    // DTH Guide Two-Column Views
    private View channelSwitcherDrawer;
    private RecyclerView categoryRecyclerView;
    private View channelListContainer;
    private View searchHeaderContainer;
    private EditText searchEditText;
    private TextView drawerCategoryTitle, drawerChannelCount;
    private RecyclerView channelRecyclerView;

    // Backup & Restore Panel
    private View backupPanel;
    private TextView backupStatusText, btnExportBackup, btnImportBackup, btnSyncPlaylists;

    private PlayerManager playerManager;
    private PlaylistManager playlistManager;
    private FavoritesManager favoritesManager;
    private CategoryAdapter categoryAdapter;
    private ChannelAdapter channelAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideHudRunnable = this::hideBottomDthBar;
    private final Runnable hideToastRunnable = this::hideStatusToast;

    private String currentCategory = PlaylistManager.CAT_HINDI;
    private List<Channel> currentCategoryChannels;
    private long lastBackPressTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind Views
        playerView = findViewById(R.id.playerView);
        bufferingSpinner = findViewById(R.id.bufferingSpinner);

        bottomDthBar = findViewById(R.id.bottomDthBar);
        hudChannelNumber = findViewById(R.id.hudChannelNumber);
        hudChannelName = findViewById(R.id.hudChannelName);
        hudCategory = findViewById(R.id.hudCategory);
        hudResolution = findViewById(R.id.hudResolution);
        hudStreamInfo = findViewById(R.id.hudStreamInfo);
        hudClockTime = findViewById(R.id.hudClockTime);
        hudClockDate = findViewById(R.id.hudClockDate);
        btnHudPlayPause = findViewById(R.id.btnHudPlayPause);
        hudPlayPauseIcon = findViewById(R.id.hudPlayPauseIcon);
        btnHudFavorite = findViewById(R.id.btnHudFavorite);
        hudFavIcon = findViewById(R.id.hudFavIcon);
        pauseOverlayBadge = findViewById(R.id.pauseOverlayBadge);
        statusToast = findViewById(R.id.statusToast);

        btnHudPlayPause.setOnClickListener(v -> togglePlayPause());
        btnHudFavorite.setOnClickListener(v -> toggleFavoriteCurrentChannel());

        // Minimal Stream Switcher in HUD (just numbers like 1/6)
        btnHudStreamSwitch = findViewById(R.id.btnHudStreamSwitch);
        if (btnHudStreamSwitch != null) {
            btnHudStreamSwitch.setOnClickListener(v -> showStreamSelectionPopup());
        }

        // Stream Selection Modal Popup
        streamSelectorOverlay = findViewById(R.id.streamSelectorOverlay);
        streamPopupCard = findViewById(R.id.streamPopupCard);
        streamPopupTitle = findViewById(R.id.streamPopupTitle);
        streamPopupCount = findViewById(R.id.streamPopupCount);
        streamPopupSubtitle = findViewById(R.id.streamPopupSubtitle);
        streamOptionsRecyclerView = findViewById(R.id.streamOptionsRecyclerView);
        streamOptionsRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        streamSelectorOverlay.setOnClickListener(v -> hideStreamSelectionPopup());
        streamPopupCard.setOnClickListener(v -> {});

        channelSwitcherDrawer = findViewById(R.id.channelSwitcherDrawer);
        categoryRecyclerView = findViewById(R.id.categoryRecyclerView);
        channelListContainer = findViewById(R.id.channelListContainer);
        searchHeaderContainer = findViewById(R.id.searchHeaderContainer);
        searchEditText = findViewById(R.id.searchEditText);
        drawerCategoryTitle = findViewById(R.id.drawerCategoryTitle);
        drawerChannelCount = findViewById(R.id.drawerChannelCount);
        channelRecyclerView = findViewById(R.id.channelRecyclerView);

        backupPanel = findViewById(R.id.backupPanel);
        backupStatusText = findViewById(R.id.backupStatusText);
        btnSyncPlaylists = findViewById(R.id.btnSyncPlaylists);
        btnExportBackup = findViewById(R.id.btnExportBackup);
        btnImportBackup = findViewById(R.id.btnImportBackup);

        // Setup managers
        playerManager = new PlayerManager(this);
        playerManager.setListener(this);
        playerView.setPlayer(playerManager.getPlayer());

        playlistManager = new PlaylistManager(this);
        favoritesManager = new FavoritesManager(this);

        // Setup Adapters
        categoryRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        channelRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        channelAdapter = new ChannelAdapter(new ChannelAdapter.OnChannelActionListener() {
            @Override
            public void onChannelClick(Channel channel, int position) {
                switchChannel(channel, position);
                hideChannelDrawer();
            }

            @Override
            public void onChannelLongClick(Channel channel, int position) {
                toggleFavorite(channel, position);
            }
        });
        channelRecyclerView.setAdapter(channelAdapter);

        // Search text watcher
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (PlaylistManager.CAT_SEARCH.equals(currentCategory)) {
                    performSearch(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Backup Buttons
        btnSyncPlaylists.setOnClickListener(v -> performOnlineChannelsUpdate());
        btnExportBackup.setOnClickListener(v -> performExportBackup());
        btnImportBackup.setOnClickListener(v -> performImportBackup());

        // Load Playlist
        bufferingSpinner.setVisibility(View.VISIBLE);
        playlistManager.load(channelCount -> handler.post(() -> {
            bufferingSpinner.setVisibility(View.GONE);
            onPlaylistLoaded();
        }));
    }

    private void onPlaylistLoaded() {
        if (favoritesManager.getFavoritesCount() == 0) {
            try {
                favoritesManager.importBackup(playlistManager.getAllChannels());
            } catch (Exception ignored) {}
        }
        favoritesManager.applyFavorites(playlistManager.getAllChannels());

        List<String> categories = playlistManager.getCategories();
        categoryAdapter = new CategoryAdapter(categories, (category, pos) -> {
            selectCategory(category);
        });
        categoryRecyclerView.setAdapter(categoryAdapter);

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String lastCat = prefs.getString(KEY_LAST_CATEGORY, PlaylistManager.CAT_HINDI);
        String lastChId = prefs.getString(KEY_LAST_CHANNEL_ID, "");

        int catIdx = categories.indexOf(lastCat);
        if (catIdx >= 0) {
            categoryAdapter.setSelectedPosition(catIdx);
            selectCategory(lastCat);
        } else {
            selectCategory(PlaylistManager.CAT_HINDI);
        }

        // Auto-play channel
        Channel startChannel = null;
        if (!lastChId.isEmpty() && currentCategoryChannels != null) {
            for (Channel ch : currentCategoryChannels) {
                if (ch.getId().equals(lastChId)) {
                    startChannel = ch;
                    break;
                }
            }
        }
        if (startChannel == null && currentCategoryChannels != null && !currentCategoryChannels.isEmpty()) {
            startChannel = currentCategoryChannels.get(0);
        }

        if (startChannel != null) {
            int pos = currentCategoryChannels.indexOf(startChannel);
            switchChannel(startChannel, pos);
        }

        checkRemotePlaylistUpdateInBackground();
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            currentCategoryChannels = Collections.emptyList();
            drawerChannelCount.setText(playlistManager.getAllChannels().size() + " CHANNELS IN CATALOG");
        } else {
            currentCategoryChannels = playlistManager.searchChannels(query);
            drawerChannelCount.setText(currentCategoryChannels.size() + " MATCHES");
        }
        channelAdapter.setChannels(currentCategoryChannels);
    }

    private void selectCategory(String category) {
        this.currentCategory = category;

        if (PlaylistManager.CAT_BACKUP.equals(category)) {
            channelListContainer.setVisibility(View.GONE);
            backupPanel.setVisibility(View.VISIBLE);
            updateBackupStatus();
            return;
        }

        channelListContainer.setVisibility(View.VISIBLE);
        backupPanel.setVisibility(View.GONE);

        if (PlaylistManager.CAT_SEARCH.equals(category)) {
            searchHeaderContainer.setVisibility(View.VISIBLE);
            drawerCategoryTitle.setText("SEARCH CHANNELS");
            performSearch(searchEditText.getText().toString());
            return;
        }

        searchHeaderContainer.setVisibility(View.GONE);
        this.currentCategoryChannels = playlistManager.getChannelsForCategory(category);
        drawerCategoryTitle.setText(category.toUpperCase());
        drawerChannelCount.setText(currentCategoryChannels.size() + " CHANNELS");
        channelAdapter.setChannels(currentCategoryChannels);

        // If the active channel is in this new category, scroll to it, otherwise scroll to top
        Channel active = playerManager.getCurrentChannel();
        int activePos = channelAdapter.getPositionForChannel(active);
        if (activePos >= 0) {
            LinearLayoutManager llm = (LinearLayoutManager) channelRecyclerView.getLayoutManager();
            if (llm != null) {
                llm.scrollToPositionWithOffset(activePos, 120);
            } else {
                channelRecyclerView.scrollToPosition(activePos);
            }
        } else {
            channelRecyclerView.scrollToPosition(0);
        }
    }

    private void toggleFavorite(Channel channel, int position) {
        if (channel == null) return;
        boolean isFav = favoritesManager.toggleFavorite(channel);

        if (isFav) {
            showStatusToast("⭐ Added " + channel.getName() + " to Favorites");
        } else {
            showStatusToast("Removed " + channel.getName() + " from Favorites");
        }

        // Re-sort current category so favorites float to top
        if (!PlaylistManager.CAT_SEARCH.equals(currentCategory)) {
            currentCategoryChannels = playlistManager.getChannelsForCategory(currentCategory);
            channelAdapter.setChannels(currentCategoryChannels);
            int newPos = channelAdapter.getPositionForChannel(channel);
            if (newPos >= 0) {
                channelRecyclerView.scrollToPosition(newPos);
            }
        } else {
            channelAdapter.notifyChannelUpdated(position);
        }

        // Update bottom bar favorite button if active
        Channel active = playerManager.getCurrentChannel();
        if (active != null && active.getId().equals(channel.getId())) {
            updateFavoriteButtonState(isFav);
        }
    }

    private void toggleFavoriteCurrentChannel() {
        Channel current = playerManager.getCurrentChannel();
        if (current == null) return;
        boolean isFav = favoritesManager.toggleFavorite(current);
        updateFavoriteButtonState(isFav);

        if (isFav) {
            showStatusToast("⭐ Added " + current.getName() + " to Favorites");
        } else {
            showStatusToast("Removed " + current.getName() + " from Favorites");
        }

        if (!PlaylistManager.CAT_SEARCH.equals(currentCategory)) {
            currentCategoryChannels = playlistManager.getChannelsForCategory(currentCategory);
            channelAdapter.setChannels(currentCategoryChannels);
        }
    }

    private void updateFavoriteButtonState(boolean isFav) {
        if (hudFavIcon == null) return;
        // Yellow for favorited, dull/gray for unfavorited
        hudFavIcon.setColorFilter(isFav ? 0xFFFFD700 : 0xFF757575);
    }

    private void togglePlayPause() {
        if (playerManager == null) return;
        playerManager.togglePlayPause();
        showBottomDthBar(true);
    }

    private void updatePlayPauseUi(boolean isPlaying) {
        if (hudPlayPauseIcon == null) return;
        if (isPlaying) {
            hudPlayPauseIcon.setImageResource(R.drawable.ic_pause);
            hudPlayPauseIcon.setColorFilter(0xFFFFFFFF);
            if (pauseOverlayBadge != null) pauseOverlayBadge.setVisibility(View.GONE);
            if (bottomDthBar != null && bottomDthBar.getVisibility() == View.VISIBLE) {
                handler.removeCallbacks(hideHudRunnable);
                handler.postDelayed(hideHudRunnable, 5000);
            }
        } else {
            hudPlayPauseIcon.setImageResource(R.drawable.ic_play);
            hudPlayPauseIcon.setColorFilter(0xFFFFFFFF);
            if (pauseOverlayBadge != null) pauseOverlayBadge.setVisibility(View.VISIBLE);
            handler.removeCallbacks(hideHudRunnable); // Keep details visible while paused
        }
    }

    private void switchChannel(Channel channel, int position) {
        if (channel == null) return;
        hideStreamSelectionPopup();
        channelAdapter.setActiveChannelId(channel.getId());
        if (pauseOverlayBadge != null) pauseOverlayBadge.setVisibility(View.GONE);
        playerManager.playChannel(channel);
        showBottomDthBar(false);

        SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_LAST_CHANNEL_ID, channel.getId());
        editor.putString(KEY_LAST_CATEGORY, currentCategory);
        editor.apply();
    }

    private void showBottomDthBar(boolean focusPlayPause) {
        Channel channel = playerManager.getCurrentChannel();
        if (channel == null) return;

        int pos = (currentCategoryChannels != null) ? currentCategoryChannels.indexOf(channel) : 0;
        if (pos < 0) pos = 0;

        hudChannelNumber.setText(String.format("%02d", pos + 1));
        hudChannelName.setText(channel.getName());
        hudCategory.setText(channel.getDisplayCategory());
        hudResolution.setText("Auto ABR");

        // Real-time clock and date
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
        Date now = new Date();
        hudClockTime.setText(timeFormat.format(now));
        hudClockDate.setText(dateFormat.format(now));

        updateFavoriteButtonState(channel.isFavorite());
        updatePlayPauseUi(playerManager.isPlaying());

        int streamCount = channel.getStreamCount();
        if (streamCount > 1) {
            hudStreamInfo.setText("Stream " + (channel.getCurrentStreamIndex() + 1) + "/" + streamCount);
            hudStreamInfo.setVisibility(View.VISIBLE);
        } else {
            hudStreamInfo.setVisibility(View.GONE);
        }

        updateStreamSwitcherUi(channel);

        bottomDthBar.setVisibility(View.VISIBLE);
        bottomDthBar.setAlpha(1.0f);

        if (focusPlayPause) {
            bottomDthBar.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            if (btnHudPlayPause != null) {
                btnHudPlayPause.requestFocus();
            }
        } else {
            bottomDthBar.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            playerView.requestFocus();
        }

        handler.removeCallbacks(hideHudRunnable);
        if (playerManager.isPlaying()) {
            handler.postDelayed(hideHudRunnable, 5000);
        }
    }

    private void showStreamSelectionPopup() {
        Channel channel = playerManager.getCurrentChannel();
        if (channel == null || channel.getStreamCount() <= 1) return;

        streamPopupTitle.setText(channel.getName() + " • Streams");
        streamPopupCount.setText(channel.getStreamCount() + " Available");

        streamAdapter = new StreamAdapter(channel, channel.getCurrentStreamIndex(), position -> {
            playerManager.switchStream(position);
            updateStreamSwitcherUi(channel);
            hideStreamSelectionPopup();
            showStatusToast("Switched to Stream " + (position + 1) + "/" + channel.getStreamCount());
            if (bottomDthBar.getVisibility() == View.VISIBLE) {
                handler.removeCallbacks(hideHudRunnable);
                handler.postDelayed(hideHudRunnable, 5000);
            }
        });

        streamOptionsRecyclerView.setAdapter(streamAdapter);

        int currentIdx = channel.getCurrentStreamIndex();
        streamOptionsRecyclerView.scrollToPosition(currentIdx);

        streamSelectorOverlay.setVisibility(View.VISIBLE);
        streamSelectorOverlay.setAlpha(0f);
        streamSelectorOverlay.animate().alpha(1f).setDuration(160).withEndAction(() -> {
            streamOptionsRecyclerView.post(() -> {
                RecyclerView.ViewHolder vh = streamOptionsRecyclerView.findViewHolderForAdapterPosition(currentIdx);
                if (vh != null) {
                    vh.itemView.requestFocus();
                } else {
                    streamOptionsRecyclerView.requestFocus();
                }
            });
        }).start();
    }

    private void hideStreamSelectionPopup() {
        if (streamSelectorOverlay != null && streamSelectorOverlay.getVisibility() == View.VISIBLE) {
            streamSelectorOverlay.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                streamSelectorOverlay.setVisibility(View.GONE);
                if (bottomDthBar.getVisibility() == View.VISIBLE && btnHudStreamSwitch != null && btnHudStreamSwitch.getVisibility() == View.VISIBLE) {
                    btnHudStreamSwitch.requestFocus();
                } else {
                    playerView.requestFocus();
                }
            }).start();
        }
    }

    private void updateStreamSwitcherUi(Channel channel) {
        if (btnHudStreamSwitch == null) return;
        if (channel == null || channel.getStreamCount() <= 1) {
            btnHudStreamSwitch.setVisibility(View.GONE);
            return;
        }
        btnHudStreamSwitch.setVisibility(View.VISIBLE);
        int current = channel.getCurrentStreamIndex() + 1;
        int total = channel.getStreamCount();
        btnHudStreamSwitch.setText(current + "/" + total);
    }

    private void hideBottomDthBar() {
        if (bottomDthBar.getVisibility() == View.VISIBLE) {
            bottomDthBar.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                bottomDthBar.setVisibility(View.GONE);
                playerView.requestFocus();
            }).start();
        }
    }

    private void showStatusToast(String message) {
        statusToast.setText(message);
        statusToast.setVisibility(View.VISIBLE);
        statusToast.setAlpha(1.0f);
        handler.removeCallbacks(hideToastRunnable);
        handler.postDelayed(hideToastRunnable, 3500);
    }

    private void hideStatusToast() {
        if (statusToast.getVisibility() == View.VISIBLE) {
            statusToast.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                statusToast.setVisibility(View.GONE);
            }).start();
        }
    }

    private void showChannelDrawer() {
        hideBottomDthBar();

        // 1. Ensure currentCategory is valid and selected in category adapter
        List<String> categories = playlistManager.getCategories();
        int catIdx = categories.indexOf(currentCategory);
        if (catIdx < 0) {
            catIdx = 0;
            currentCategory = categories.get(0);
        }

        if (categoryAdapter != null) {
            categoryAdapter.setSelectedPosition(catIdx);
        }
        categoryRecyclerView.scrollToPosition(catIdx);

        // 2. If backup category, show backup panel
        if (PlaylistManager.CAT_BACKUP.equals(currentCategory)) {
            channelListContainer.setVisibility(View.GONE);
            backupPanel.setVisibility(View.VISIBLE);
            updateBackupStatus();
            categoryRecyclerView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            channelSwitcherDrawer.setVisibility(View.VISIBLE);
            channelSwitcherDrawer.setTranslationX(-channelSwitcherDrawer.getWidth());
            channelSwitcherDrawer.animate().translationX(0).setDuration(240).start();
            btnExportBackup.requestFocus();
            return;
        }

        channelListContainer.setVisibility(View.VISIBLE);
        backupPanel.setVisibility(View.GONE);

        if (PlaylistManager.CAT_SEARCH.equals(currentCategory)) {
            searchHeaderContainer.setVisibility(View.VISIBLE);
            drawerCategoryTitle.setText("SEARCH CHANNELS");
            performSearch(searchEditText.getText().toString());

            categoryRecyclerView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            channelSwitcherDrawer.setVisibility(View.VISIBLE);
            channelSwitcherDrawer.setTranslationX(-channelSwitcherDrawer.getWidth());
            channelSwitcherDrawer.animate().translationX(0).setDuration(240).start();

            channelRecyclerView.post(() -> {
                searchEditText.requestFocus();
                categoryRecyclerView.postDelayed(() -> {
                    categoryRecyclerView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                }, 60);
            });
            return;
        }

        searchHeaderContainer.setVisibility(View.GONE);
        drawerCategoryTitle.setText(currentCategory.toUpperCase());

        // Refresh channels for current category
        currentCategoryChannels = playlistManager.getChannelsForCategory(currentCategory);
        drawerChannelCount.setText(currentCategoryChannels.size() + " CHANNELS");
        channelAdapter.setChannels(currentCategoryChannels);

        // 3. Find active playing channel position in this category
        Channel active = playerManager.getCurrentChannel();
        int pos = -1;
        if (active != null) {
            channelAdapter.setActiveChannelId(active.getId());
            pos = channelAdapter.getPositionForChannel(active);
        }
        final int targetChannelPos = (pos >= 0) ? pos : 0;
        final int targetCatPos = catIdx;

        // 4. Block category descendants from stealing focus during drawer open
        categoryRecyclerView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        channelSwitcherDrawer.setVisibility(View.VISIBLE);
        channelSwitcherDrawer.setTranslationX(-channelSwitcherDrawer.getWidth());
        channelSwitcherDrawer.animate().translationX(0).setDuration(240).start();

        // 5. Scroll channel list to active channel
        LinearLayoutManager chLlm = (LinearLayoutManager) channelRecyclerView.getLayoutManager();
        if (chLlm != null) {
            chLlm.scrollToPositionWithOffset(targetChannelPos, 120);
        }

        // 6. Request focus on the target channel in channelRecyclerView
        channelRecyclerView.post(() -> {
            View targetV = (chLlm != null) ? chLlm.findViewByPosition(targetChannelPos) : null;
            if (targetV != null) {
                targetV.requestFocus();
            } else {
                RecyclerView.ViewHolder vh = channelRecyclerView.findViewHolderForAdapterPosition(targetChannelPos);
                if (vh != null) {
                    vh.itemView.requestFocus();
                } else {
                    channelRecyclerView.requestFocus();
                }
            }
            // Restore category focusability now that channel has focus
            categoryRecyclerView.postDelayed(() -> {
                categoryRecyclerView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                categoryRecyclerView.scrollToPosition(targetCatPos);
            }, 60);
        });
    }

    private void focusChannelAt(int pos) {
        if (channelRecyclerView == null || pos < 0 || pos >= channelAdapter.getItemCount()) return;

        LinearLayoutManager llm = (LinearLayoutManager) channelRecyclerView.getLayoutManager();
        int offset = channelRecyclerView.getHeight() > 0 ? (channelRecyclerView.getHeight() / 3) : 120;
        if (llm != null) {
            llm.scrollToPositionWithOffset(pos, offset);
        } else {
            channelRecyclerView.scrollToPosition(pos);
        }

        channelRecyclerView.post(() -> {
            View v = (llm != null) ? llm.findViewByPosition(pos) : null;
            if (v != null) {
                v.requestFocus();
            } else {
                RecyclerView.ViewHolder vh = channelRecyclerView.findViewHolderForAdapterPosition(pos);
                if (vh != null) {
                    vh.itemView.requestFocus();
                } else {
                    channelRecyclerView.postDelayed(() -> {
                        View v2 = (llm != null) ? llm.findViewByPosition(pos) : null;
                        if (v2 != null) v2.requestFocus();
                    }, 50);
                }
            }
        });
    }

    private void focusCategoryAt(int pos) {
        if (categoryRecyclerView == null || pos < 0 || (categoryAdapter != null && pos >= categoryAdapter.getItemCount())) return;

        categoryRecyclerView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        LinearLayoutManager llm = (LinearLayoutManager) categoryRecyclerView.getLayoutManager();
        int offset = categoryRecyclerView.getHeight() > 0 ? (categoryRecyclerView.getHeight() / 3) : 80;
        if (llm != null) {
            llm.scrollToPositionWithOffset(pos, offset);
        } else {
            categoryRecyclerView.scrollToPosition(pos);
        }

        categoryRecyclerView.post(() -> {
            View v = (llm != null) ? llm.findViewByPosition(pos) : null;
            if (v != null) {
                v.requestFocus();
            } else {
                RecyclerView.ViewHolder vh = categoryRecyclerView.findViewHolderForAdapterPosition(pos);
                if (vh != null) {
                    vh.itemView.requestFocus();
                } else {
                    categoryRecyclerView.postDelayed(() -> {
                        View v2 = (llm != null) ? llm.findViewByPosition(pos) : null;
                        if (v2 != null) v2.requestFocus();
                    }, 50);
                }
            }
        });
    }

    private void hideChannelDrawer() {
        if (channelSwitcherDrawer.getVisibility() == View.VISIBLE) {
            channelSwitcherDrawer.animate().translationX(-channelSwitcherDrawer.getWidth()).setDuration(200)
                    .withEndAction(() -> channelSwitcherDrawer.setVisibility(View.GONE)).start();
            playerView.requestFocus();
        }
    }

    private void zapNextChannel() {
        if (currentCategoryChannels == null || currentCategoryChannels.isEmpty()) {
            currentCategoryChannels = playlistManager.getChannelsForCategory(currentCategory);
        }
        if (currentCategoryChannels == null || currentCategoryChannels.isEmpty()) return;
        Channel current = playerManager.getCurrentChannel();
        int pos = -1;
        if (current != null) {
            for (int i = 0; i < currentCategoryChannels.size(); i++) {
                if (currentCategoryChannels.get(i).getId().equals(current.getId())) {
                    pos = i;
                    break;
                }
            }
        }
        int next = (pos + 1) % currentCategoryChannels.size();
        switchChannel(currentCategoryChannels.get(next), next);
    }

    private void zapPreviousChannel() {
        if (currentCategoryChannels == null || currentCategoryChannels.isEmpty()) {
            currentCategoryChannels = playlistManager.getChannelsForCategory(currentCategory);
        }
        if (currentCategoryChannels == null || currentCategoryChannels.isEmpty()) return;
        Channel current = playerManager.getCurrentChannel();
        int pos = 0;
        if (current != null) {
            for (int i = 0; i < currentCategoryChannels.size(); i++) {
                if (currentCategoryChannels.get(i).getId().equals(current.getId())) {
                    pos = i;
                    break;
                }
            }
        }
        int prev = (pos - 1 + currentCategoryChannels.size()) % currentCategoryChannels.size();
        switchChannel(currentCategoryChannels.get(prev), prev);
    }

    // --- Backup & Restore Actions ---
    private void updateBackupStatus() {
        int count = favoritesManager.getFavoritesCount();
        int totalChannels = playlistManager.getAllChannels().size();
        boolean hasCache = playlistManager.hasLocalCache();
        backupStatusText.setText("Favorites: " + count + " channels saved.\nTotal Catalog: " + totalChannels + " channels (" + (hasCache ? "Online Synced" : "Built-in Assets") + ").\nBackup: /sdcard/Download/snappytv_backup.json");
    }

    private void performOnlineChannelsUpdate() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String customUrl = prefs.getString("custom_playlist_url", PlaylistManager.DEFAULT_REMOTE_URL);
        showStatusToast("🔄 Checking for updated channels from Oracle VPS…");
        playlistManager.updateFromRemoteUrl(customUrl, new PlaylistManager.UpdateCallback() {
            @Override
            public void onSuccess(int count) {
                handler.post(() -> {
                    showStatusToast("✅ Successfully synced " + count + " live channels from VPS!");
                    updateBackupStatus();
                    favoritesManager.applyFavorites(playlistManager.getAllChannels());
                    selectCategory(currentCategory);
                });
            }

            @Override
            public void onError(String error) {
                handler.post(() -> {
                    showStatusToast("❌ VPS sync: " + error);
                });
            }
        });
    }

    private void checkRemotePlaylistUpdateInBackground() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String customUrl = prefs.getString("custom_playlist_url", PlaylistManager.DEFAULT_REMOTE_URL);
        long lastCheck = prefs.getLong("last_ota_check_time", 0);
        long now = System.currentTimeMillis();
        // Check at most once every 6 hours in background
        if (now - lastCheck > 6 * 3600 * 1000L) {
            prefs.edit().putLong("last_ota_check_time", now).apply();
            playlistManager.updateFromRemoteUrl(customUrl, new PlaylistManager.UpdateCallback() {
                @Override
                public void onSuccess(int count) {
                    Log.i(TAG, "Background OTA sync completed: " + count + " channels.");
                }

                @Override
                public void onError(String error) {
                    Log.w(TAG, "Background OTA sync check: " + error);
                }
            });
        }
    }

    private void performExportBackup() {
        try {
            Channel current = playerManager.getCurrentChannel();
            String path = favoritesManager.exportBackup(current != null ? current.getId() : "", currentCategory);
            showStatusToast("✅ Backup exported successfully to " + path);
            updateBackupStatus();
        } catch (Exception e) {
            showStatusToast("❌ Export failed: " + e.getMessage());
        }
    }

    private void performImportBackup() {
        try {
            int restored = favoritesManager.importBackup(playlistManager.getAllChannels());
            showStatusToast("✅ Restored " + restored + " favorites from backup!");
            channelAdapter.notifyDataSetChanged();
            updateBackupStatus();
        } catch (Exception e) {
            showStatusToast("❌ Import failed: " + e.getMessage());
        }
    }

    // --- Key Events for DTH Remote UX ---
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }

        int code = event.getKeyCode();

        // 1. If Stream Selection Popup is visible, handle BACK and let list handle navigation
        if (streamSelectorOverlay != null && streamSelectorOverlay.getVisibility() == View.VISIBLE) {
            if (code == KeyEvent.KEYCODE_BACK) {
                hideStreamSelectionPopup();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        boolean isDrawerOpen = channelSwitcherDrawer.getVisibility() == View.VISIBLE;

        if (!isDrawerOpen) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_CHANNEL_UP:
                    zapPreviousChannel();
                    showBottomDthBar(false);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                    zapNextChannel();
                    showBottomDthBar(false);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (bottomDthBar.getVisibility() == View.VISIBLE) {
                        View focused = getCurrentFocus();
                        if (focused != null && (focused.getId() == R.id.btnHudFavorite || btnHudFavorite.isFocused())) {
                            toggleFavoriteCurrentChannel();
                        } else if (focused != null && (focused.getId() == R.id.btnHudStreamSwitch || (btnHudStreamSwitch != null && btnHudStreamSwitch.isFocused()))) {
                            showStreamSelectionPopup();
                        } else {
                            // 2nd click on OK toggles pause/play
                            togglePlayPause();
                        }
                    } else {
                        // 1st click on OK gets details of the channel and focuses Play/Pause
                        showBottomDthBar(true);
                    }
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    togglePlayPause();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    if (playerManager != null) playerManager.pause();
                    showBottomDthBar(true);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    if (playerManager != null) playerManager.play();
                    showBottomDthBar(true);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (bottomDthBar.getVisibility() == View.VISIBLE) {
                        if (btnHudFavorite != null && btnHudFavorite.isFocused()) {
                            if (btnHudPlayPause != null) {
                                btnHudPlayPause.requestFocus();
                            }
                            return true;
                        } else if (btnHudPlayPause != null && btnHudPlayPause.isFocused()) {
                            if (btnHudStreamSwitch != null && btnHudStreamSwitch.getVisibility() == View.VISIBLE) {
                                btnHudStreamSwitch.requestFocus();
                            } else {
                                hideBottomDthBar();
                                showChannelDrawer();
                            }
                            return true;
                        } else if (btnHudStreamSwitch != null && btnHudStreamSwitch.isFocused()) {
                            hideBottomDthBar();
                            showChannelDrawer();
                            return true;
                        } else {
                            hideBottomDthBar();
                            showChannelDrawer();
                            return true;
                        }
                    } else {
                        showChannelDrawer();
                        return true;
                    }
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (bottomDthBar.getVisibility() == View.VISIBLE) {
                        if (btnHudStreamSwitch != null && btnHudStreamSwitch.isFocused()) {
                            if (btnHudPlayPause != null) {
                                btnHudPlayPause.requestFocus();
                            }
                            return true;
                        } else if (btnHudPlayPause != null && btnHudPlayPause.isFocused()) {
                            if (btnHudFavorite != null) {
                                btnHudFavorite.requestFocus();
                            }
                            return true;
                        } else {
                            return true;
                        }
                    } else {
                        showBottomDthBar(true);
                        return true;
                    }
                case KeyEvent.KEYCODE_MENU:
                    if (bottomDthBar.getVisibility() == View.VISIBLE) {
                        toggleFavoriteCurrentChannel();
                    } else {
                        showBottomDthBar(true);
                    }
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (bottomDthBar.getVisibility() == View.VISIBLE) {
                        hideBottomDthBar();
                        return true;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastBackPressTime < 2000) {
                        finish();
                    } else {
                        lastBackPressTime = now;
                        showStatusToast("Press BACK again to exit SnappyTV");
                    }
                    return true;
            }
        } else {
            // When drawer is open:
            View focused = getCurrentFocus();
            if (code == KeyEvent.KEYCODE_BACK) {
                hideChannelDrawer();
                return true;
            }

            // D-Pad Navigation between Two Panes
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // If focus is in Left Pane (Category), jump to Right Pane
                if (focused != null && categoryRecyclerView.findContainingItemView(focused) != null) {
                    if (PlaylistManager.CAT_SEARCH.equals(currentCategory)) {
                        searchEditText.requestFocus();
                    } else if (backupPanel.getVisibility() == View.VISIBLE) {
                        btnExportBackup.requestFocus();
                    } else {
                        Channel active = playerManager.getCurrentChannel();
                        int activePos = channelAdapter.getPositionForChannel(active);
                        if (activePos >= 0) {
                            focusChannelAt(activePos);
                        } else {
                            focusChannelAt(0);
                        }
                    }
                    return true;
                }
            } else if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                // If in search input, jump to category list
                if (focused != null && focused.getId() == R.id.searchEditText) {
                    int catPos = categoryAdapter != null ? categoryAdapter.getSelectedPosition() : 0;
                    focusCategoryAt(catPos);
                    return true;
                }
                // If focus is in Right Pane (Channel list or Backup), jump back to Left Pane (Category)
                if (focused != null && (channelRecyclerView.findContainingItemView(focused) != null
                        || focused.getId() == R.id.btnSyncPlaylists || focused.getId() == R.id.btnExportBackup || focused.getId() == R.id.btnImportBackup)) {
                    int catPos = categoryAdapter != null ? categoryAdapter.getSelectedPosition() : 0;
                    focusCategoryAt(catPos);
                    return true;
                }
            } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (focused != null && focused.getId() == R.id.searchEditText) {
                    if (channelAdapter.getItemCount() > 0) {
                        focusChannelAt(0);
                        return true;
                    }
                }
            } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                if (PlaylistManager.CAT_SEARCH.equals(currentCategory)) {
                    if (focused != null && channelRecyclerView.findContainingItemView(focused) != null) {
                        LinearLayoutManager llm = (LinearLayoutManager) channelRecyclerView.getLayoutManager();
                        if (llm != null && llm.findFirstCompletelyVisibleItemPosition() == 0) {
                            searchEditText.requestFocus();
                            return true;
                        }
                    }
                }
            }
        }

        return super.dispatchKeyEvent(event);
    }

    // --- Player Callbacks ---
    @Override
    public void onBuffering(boolean isBuffering) {
        bufferingSpinner.setVisibility(isBuffering ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPlaybackStarted() {
        bufferingSpinner.setVisibility(View.GONE);
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        updatePlayPauseUi(isPlaying);
    }

    @Override
    public void onVideoResolutionChanged(int width, int height, String qualityTag) {
        hudResolution.setText(qualityTag);
    }

    @Override
    public void onStreamChanged(Channel channel, int streamIndex, int totalStreams) {
        if (channel != null) {
            updateStreamSwitcherUi(channel);
            if (totalStreams > 1) {
                hudStreamInfo.setText("Stream " + streamIndex + "/" + totalStreams);
            }
            if (streamAdapter != null) {
                streamAdapter.setCurrentStreamIndex(channel.getCurrentStreamIndex());
            }
        }
    }

    @Override
    public void onStreamFallback(Channel channel, int streamIndex, int totalStreams) {
        showStatusToast("Stream mirror " + (streamIndex - 1) + " failed. Switched to fallback (" + streamIndex + "/" + totalStreams + ")");
        hudStreamInfo.setText("Fallback " + streamIndex + "/" + totalStreams);
        if (channel != null) {
            updateStreamSwitcherUi(channel);
            if (streamAdapter != null) {
                streamAdapter.setCurrentStreamIndex(channel.getCurrentStreamIndex());
            }
        }
    }

    @Override
    public void onChannelPlaybackFailed(Channel channel, String reason) {
        showStatusToast("Stream offline. Zapping to next channel in 3s…");
        handler.postDelayed(this::zapNextChannel, 3000);
    }

    @Override
    protected void onStart() {
        super.onStart();
        playerManager.retryCurrentChannel();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (playerManager != null && playerManager.getPlayer() != null) {
            playerManager.getPlayer().pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (playerManager != null) {
            playerManager.release();
        }
    }
}
