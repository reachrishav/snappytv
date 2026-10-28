package com.gathin.snappytv;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.datasource.DefaultHttpDataSource;

public class PlayerManager {
    private static final String TAG = "PlayerManager";
    private static final long BUFFERING_TIMEOUT_MS = 4500; // 4.5s watchdog for frozen/stalled streams

    public interface Listener {
        void onBuffering(boolean isBuffering);
        void onPlaybackStarted();
        void onIsPlayingChanged(boolean isPlaying);
        void onVideoResolutionChanged(int width, int height, String qualityTag);
        void onStreamChanged(Channel channel, int streamIndex, int totalStreams);
        void onStreamFallback(Channel channel, int streamIndex, int totalStreams);
        void onChannelPlaybackFailed(Channel channel, String reason);
    }

    private final Context context;
    private final ExoPlayer player;
    private final DefaultTrackSelector trackSelector;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private Channel currentChannel;
    private boolean isReleased = false;
    private int rebufferRetryCount = 0;

    // Self-healing Watchdog: If a live stream stays stuck in buffering (e.g. Sangeet Bangla edge freeze),
    // automatically recover without requiring the user to switch channels.
    private final Runnable bufferingWatchdog = new Runnable() {
        @Override
        public void run() {
            if (isReleased) return;
            if (player != null && player.getPlaybackState() == Player.STATE_BUFFERING) {
                rebufferRetryCount++;
                Log.w(TAG, "Stream stalled in buffering for >" + BUFFERING_TIMEOUT_MS + "ms (recovery attempt #" + rebufferRetryCount + ")");
                if (rebufferRetryCount <= 1) {
                    // Step 1: Soft catch-up to the live edge window
                    player.seekToDefaultPosition();
                    player.prepare();
                    mainHandler.postDelayed(this, BUFFERING_TIMEOUT_MS);
                } else if (currentChannel != null && currentChannel.hasFallbackStream()) {
                    // Step 2: Switch to fallback/mirror stream if available
                    rebufferRetryCount = 0;
                    handlePlaybackError(new PlaybackException("Buffering stall timeout", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT));
                } else {
                    // Step 3: Hard re-initialization of current stream
                    rebufferRetryCount = 0;
                    playCurrentStream();
                }
            }
        }
    };

    public PlayerManager(Context context) {
        this.context = context.getApplicationContext();

        // 1. Resilient Load Control: Ample buffer margin to prevent edge-freeze while maintaining snappy 1s zapping.
        // Back-buffer is kept at 0 to keep RAM footprint low (<10MB) for 1GB Android TV devices (e.g. Mi TV 4A).
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        15000, // Min buffer: 15.0s (absorbs broadband & server packet jitter)
                        30000, // Max buffer: 30.0s (healthy buffer depth)
                        1000,  // Buffer for playback: 1.0s (instant channel start)
                        2000   // Buffer for playback after rebuffer: 2.0s
                )
                .setBackBuffer(0, false)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        // 2. Adaptive Bitrate (ABR) track selector
        AdaptiveTrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(
                1500, // Min duration for quality increase (1.5s)
                2000, // Max duration for quality decrease (2s)
                1500, // Min duration to retain current selection
                0.75f // Bandwidth fraction
        );
        this.trackSelector = new DefaultTrackSelector(context, trackSelectionFactory);
        this.trackSelector.setParameters(trackSelector.buildUponParameters()
                .setForceHighestSupportedBitrate(false)
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
        );

        // 3. Build ExoPlayer
        this.player = new ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setTrackSelector(trackSelector)
                .build();

        this.player.setPlayWhenReady(true);
        this.player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (listener == null) return;
                switch (playbackState) {
                    case Player.STATE_BUFFERING:
                        listener.onBuffering(true);
                        mainHandler.removeCallbacks(bufferingWatchdog);
                        mainHandler.postDelayed(bufferingWatchdog, BUFFERING_TIMEOUT_MS);
                        break;
                    case Player.STATE_READY:
                        mainHandler.removeCallbacks(bufferingWatchdog);
                        rebufferRetryCount = 0;
                        listener.onBuffering(false);
                        listener.onPlaybackStarted();
                        break;
                    case Player.STATE_ENDED:
                    case Player.STATE_IDLE:
                        mainHandler.removeCallbacks(bufferingWatchdog);
                        listener.onBuffering(false);
                        break;
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (listener != null) {
                    listener.onIsPlayingChanged(isPlaying);
                }
            }

            @Override
            public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                if (listener != null && videoSize.width > 0 && videoSize.height > 0) {
                    String tag;
                    if (videoSize.height >= 1080) tag = "1080p HD";
                    else if (videoSize.height >= 720) tag = "720p HD";
                    else if (videoSize.height >= 576) tag = "576p SD";
                    else if (videoSize.height >= 480) tag = "480p";
                    else tag = videoSize.height + "p";
                    listener.onVideoResolutionChanged(videoSize.width, videoSize.height, tag);
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.w(TAG, "Playback error: " + error.getMessage() + " code=" + error.errorCode);
                mainHandler.removeCallbacks(bufferingWatchdog);

                // Handle behind live window gracefully by catching up to latest live position
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    Log.w(TAG, "Behind live window detected, recovering to live edge");
                    player.seekToDefaultPosition();
                    player.prepare();
                    player.play();
                    return;
                }

                handlePlaybackError(error);
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    public Channel getCurrentChannel() {
        return currentChannel;
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public void pause() {
        if (player != null) {
            player.pause();
        }
    }

    public void play() {
        if (player != null) {
            player.play();
        }
    }

    public void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    public void playChannel(Channel channel) {
        if (channel == null) return;
        this.currentChannel = channel;
        channel.resetStreamIndex();
        rebufferRetryCount = 0;
        mainHandler.removeCallbacks(bufferingWatchdog);
        playCurrentStream();
    }

    public void switchStream(int streamIndex) {
        if (currentChannel == null) return;
        if (streamIndex < 0 || streamIndex >= currentChannel.getStreamCount()) return;
        currentChannel.setCurrentStreamIndex(streamIndex);
        rebufferRetryCount = 0;
        mainHandler.removeCallbacks(bufferingWatchdog);
        Log.i(TAG, "Manually switched stream to index " + streamIndex + " for channel " + currentChannel.getName());
        playCurrentStream();
    }

    private void playCurrentStream() {
        if (currentChannel == null) return;
        String streamUrl = currentChannel.getCurrentStreamUrl();
        if (streamUrl == null || streamUrl.isEmpty()) {
            if (listener != null) listener.onChannelPlaybackFailed(currentChannel, "No stream URL");
            return;
        }

        if (listener != null) {
            listener.onBuffering(true);
            listener.onStreamChanged(currentChannel, currentChannel.getCurrentStreamIndex() + 1, currentChannel.getStreamCount());
        }

        DefaultHttpDataSource.Factory httpSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (SmartTV; Linux; Android 9; MiTV) AppleWebKit/537.36")
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(10000)
                .setAllowCrossProtocolRedirects(true);

        Uri uri = Uri.parse(streamUrl);

        // Configure Live stream offset and speed adaptiveness to stay in safe live window
        MediaItem.LiveConfiguration liveConfig = new MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(6000)
                .setMinOffsetMs(2000)
                .setMaxOffsetMs(15000)
                .setMinPlaybackSpeed(0.97f)
                .setMaxPlaybackSpeed(1.03f)
                .build();

        boolean isHls = streamUrl.contains(".m3u8")
                || streamUrl.contains("hls")
                || streamUrl.contains("m3u")
                || streamUrl.contains("stvp-")
                || streamUrl.contains("plu-")
                || streamUrl.contains("rok-")
                || streamUrl.contains("streamtest")
                || streamUrl.contains("chunklist")
                || (!streamUrl.endsWith(".mp4") && !streamUrl.endsWith(".mkv") && !streamUrl.endsWith(".flv") && !streamUrl.endsWith(".avi"));

        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                .setUri(uri)
                .setLiveConfiguration(liveConfig);

        MediaSource mediaSource;
        if (isHls) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8);
            mediaSource = new HlsMediaSource.Factory(httpSourceFactory)
                    .setAllowChunklessPreparation(false) // Disable chunkless to prevent track/codec mismatch freezes
                    .createMediaSource(mediaItemBuilder.build());
        } else {
            mediaSource = new ProgressiveMediaSource.Factory(httpSourceFactory)
                    .createMediaSource(mediaItemBuilder.build());
        }

        player.stop();
        player.setMediaSource(mediaSource);
        player.prepare();
        player.play();
    }

    private void handlePlaybackError(PlaybackException error) {
        if (currentChannel != null && currentChannel.hasFallbackStream()) {
            String fallbackUrl = currentChannel.moveToNextStream();
            Log.i(TAG, "Stream failed, trying fallback stream: " + fallbackUrl);
            if (listener != null) {
                listener.onStreamFallback(currentChannel, currentChannel.getCurrentStreamIndex() + 1, currentChannel.getStreamCount());
            }
            playCurrentStream();
        } else {
            Log.e(TAG, "All streams exhausted for channel " + (currentChannel != null ? currentChannel.getName() : ""));
            if (listener != null) {
                listener.onChannelPlaybackFailed(currentChannel, error.getMessage());
            }
        }
    }

    public void retryCurrentChannel() {
        if (currentChannel != null) {
            playCurrentStream();
        }
    }

    public void release() {
        if (!isReleased) {
            isReleased = true;
            mainHandler.removeCallbacks(bufferingWatchdog);
            player.release();
        }
    }
}
