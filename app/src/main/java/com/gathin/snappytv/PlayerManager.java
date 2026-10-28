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
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.datasource.DefaultHttpDataSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PlayerManager {
    private static final String TAG = "PlayerManager";
    private static final long BUFFERING_TIMEOUT_MS = 8000; // 8.0s watchdog to allow slow/jittery chunk downloads

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

    // Diagnostic Metrics for Playback Continuity
    private int totalStallCount = 0;
    private long totalStallDurationMs = 0;
    private long stallStartTime = 0;

    // Chronic Stalling Detector:
    // If an upstream stream stalls 3 times within 45 seconds (e.g. server throttling/congested .ts chunks),
    // automatically migrate to the next fallback stream so playback stays continuous.
    private final List<Long> recentStallTimestamps = new ArrayList<>();
    private static final int MAX_STALLS_BEFORE_FALLBACK = 3;
    private static final long STALL_WINDOW_MS = 45000;

    // Self-healing Watchdog: If a live stream stays stuck in buffering (e.g. edge freeze),
    // automatically recover without requiring the user to switch channels.
    private final Runnable bufferingWatchdog = new Runnable() {
        @Override
        public void run() {
            if (isReleased) return;
            if (player != null && player.getPlaybackState() == Player.STATE_BUFFERING) {
                rebufferRetryCount++;
                Log.w(TAG, "Stream stalled in buffering for >" + BUFFERING_TIMEOUT_MS + "ms (recovery attempt #" + rebufferRetryCount + ")");
                if (rebufferRetryCount <= 1) {
                    // Step 1: Soft catch-up to the safe live edge window
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

        // 1. Resilient Load Control: Deep buffer depth (25s–50s) to completely absorb broadband & CDN packet jitter.
        // 2.5s initial buffer allows the next segment to download in the background before the first finishes playing.
        // 5.0s buffer after rebuffer guarantees a full chunk cushion before resuming.
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        25000, // Min buffer: 25.0s (healthy buffer ahead of playhead)
                        50000, // Max buffer: 50.0s (ceiling to avoid excess RAM)
                        2500,  // Buffer for initial playback: 2.5s (prevents initial underrun)
                        5000   // Buffer for playback after rebuffer: 5.0s (ensures full segment loaded)
                )
                .setBackBuffer(0, false) // 0s back buffer keeps RAM usage under 15MB on 1GB Android TV
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        // 2. Television-optimized ABR track selector:
        // Uses 10s upswitch / 25s downswitch to prevent constant decoder flushes and micro-stutters.
        AdaptiveTrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(
                10000, // Min duration for quality increase (10s sustained bandwidth)
                25000, // Max duration for quality decrease (25s tolerance for transient dips)
                25000, // Min duration to retain current selection (25s)
                0.70f  // Bandwidth fraction (leaves 30% headroom for stability)
        );
        this.trackSelector = new DefaultTrackSelector(context, trackSelectionFactory);
        this.trackSelector.setParameters(trackSelector.buildUponParameters()
                .setForceHighestSupportedBitrate(false)
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED)
                .setSelectUndeterminedTextLanguage(false)
        );

        // 3. Resilient Hardware Decoder with Fallback
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                .setEnableDecoderFallback(true);

        // 4. Build ExoPlayer
        this.player = new ExoPlayer.Builder(context, renderersFactory)
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
                        stallStartTime = System.currentTimeMillis();
                        long bufferedMs = player.getTotalBufferedDuration();
                        Log.w(TAG, "Playback entered STATE_BUFFERING (buffered ahead: " + bufferedMs + "ms)");
                        listener.onBuffering(true);
                        mainHandler.removeCallbacks(bufferingWatchdog);
                        mainHandler.postDelayed(bufferingWatchdog, BUFFERING_TIMEOUT_MS);
                        break;
                    case Player.STATE_READY:
                        mainHandler.removeCallbacks(bufferingWatchdog);
                        rebufferRetryCount = 0;
                        if (stallStartTime > 0) {
                            long now = System.currentTimeMillis();
                            long stallDuration = now - stallStartTime;
                            totalStallDurationMs += stallDuration;
                            totalStallCount++;
                            stallStartTime = 0;

                            // Prune stall timestamps older than 45s
                            Iterator<Long> it = recentStallTimestamps.iterator();
                            while (it.hasNext()) {
                                if (now - it.next() > STALL_WINDOW_MS) {
                                    it.remove();
                                }
                            }
                            recentStallTimestamps.add(now);

                            Log.i(TAG, "Playback recovered from stall #" + totalStallCount + " (duration: " + stallDuration + "ms, " + recentStallTimestamps.size() + " stalls in last 45s, buffered: " + player.getTotalBufferedDuration() + "ms)");

                            if (recentStallTimestamps.size() >= MAX_STALLS_BEFORE_FALLBACK && currentChannel != null && currentChannel.hasFallbackStream()) {
                                Log.w(TAG, "Stream experienced " + recentStallTimestamps.size() + " stalls in " + (STALL_WINDOW_MS / 1000) + "s. Auto-switching to fallback mirror!");
                                recentStallTimestamps.clear();
                                handlePlaybackError(new PlaybackException("Chronic buffer underruns", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT));
                                return;
                            }
                        }
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
        recentStallTimestamps.clear();
        mainHandler.removeCallbacks(bufferingWatchdog);
        playCurrentStream();
    }

    public void switchStream(int streamIndex) {
        if (currentChannel == null) return;
        if (streamIndex < 0 || streamIndex >= currentChannel.getStreamCount()) return;
        currentChannel.setCurrentStreamIndex(streamIndex);
        rebufferRetryCount = 0;
        recentStallTimestamps.clear();
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

        Map<String, String> defaultHeaders = new HashMap<>();
        defaultHeaders.put("Connection", "keep-alive");
        defaultHeaders.put("Accept", "*/*");
        defaultHeaders.put("Accept-Encoding", "gzip, deflate");

        DefaultHttpDataSource.Factory httpSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (SmartTV; Linux; Android 9; MiTV) AppleWebKit/537.36")
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(defaultHeaders);

        Uri uri = Uri.parse(streamUrl);

        // Safe Live Offset (18.0s): Maintains a deep 2-3 segment cushion ahead of playhead (RFC 8216 recommendation)
        // Prevents playhead from colliding with the encoder's live publication edge
        MediaItem.LiveConfiguration liveConfig = new MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(18000)   // Target offset: 18.0s from live edge (3 x 6s segments)
                .setMinOffsetMs(8000)       // Minimum offset: 8.0s (never play closer than 8s to edge)
                .setMaxOffsetMs(36000)      // Maximum offset: 36.0s
                .setMinPlaybackSpeed(0.98f) // Subtle 2% speed adjustment to eliminate pitch flutter
                .setMaxPlaybackSpeed(1.02f)
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
                    .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(3)) // Retry transient chunk network drops up to 3 times
                    .createMediaSource(mediaItemBuilder.build());
        } else {
            mediaSource = new ProgressiveMediaSource.Factory(httpSourceFactory)
                    .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(3))
                    .createMediaSource(mediaItemBuilder.build());
        }

        player.stop();
        player.setMediaSource(mediaSource);
        player.prepare();
        player.play();
    }

    public int getTotalStallCount() {
        return totalStallCount;
    }

    public long getTotalStallDurationMs() {
        return totalStallDurationMs;
    }

    public long getCurrentBufferedDurationMs() {
        return (player != null) ? player.getTotalBufferedDuration() : 0;
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
