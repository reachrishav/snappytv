package com.gathin.snappytv;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaylistManager {
    private static final String TAG = "PlaylistManager";
    public static final String CACHE_FILENAME = "channels_cache.json";
    public static final String DEFAULT_REMOTE_URL = BuildConfig.DEFAULT_PLAYLIST_URL;

    public static final String CAT_SEARCH = "🔍 Search";
    public static final String CAT_FAVORITES = "⭐ Favorites";
    public static final String CAT_ALL = "All Channels";
    public static final String CAT_HINDI = "Hindi";
    public static final String CAT_BENGALI = "Bengali";
    public static final String CAT_ENGLISH = "English";
    public static final String CAT_REGIONAL = "Regional (India)";
    public static final String CAT_SPORTS = "Sports";
    public static final String CAT_NEWS = "News";
    public static final String CAT_MUSIC = "Music";
    public static final String CAT_MOVIES = "Movies";
    public static final String CAT_ENTERTAINMENT = "Entertainment";
    public static final String CAT_DEVOTIONAL = "Devotional";
    public static final String CAT_KIDS = "Kids";
    public static final String CAT_BACKUP = "⚙ Backup & Restore";

    private final Context context;
    private final ExecutorService executor;
    private final List<Channel> allChannels;
    private final List<String> categories;

    public interface Callback {
        void onLoaded(int channelCount);
    }

    public interface UpdateCallback {
        void onSuccess(int channelCount);
        void onError(String error);
    }

    public PlaylistManager(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.allChannels = new ArrayList<>();
        this.categories = new ArrayList<>();
        initCategories();
    }

    private void initCategories() {
        categories.clear();
        categories.add(CAT_SEARCH);
        categories.add(CAT_FAVORITES);
        categories.add(CAT_ALL);
        categories.add(CAT_HINDI);
        categories.add(CAT_BENGALI);
        categories.add(CAT_ENGLISH);
        categories.add(CAT_REGIONAL);
        categories.add(CAT_SPORTS);
        categories.add(CAT_NEWS);
        categories.add(CAT_MUSIC);
        categories.add(CAT_MOVIES);
        categories.add(CAT_ENTERTAINMENT);
        categories.add(CAT_DEVOTIONAL);
        categories.add(CAT_KIDS);
        categories.add(CAT_BACKUP);
    }

    public void load(Callback callback) {
        executor.execute(() -> {
            List<Channel> loaded = loadFromDiskOrAssets();
            synchronized (allChannels) {
                allChannels.clear();
                allChannels.addAll(loaded);
            }
            if (callback != null) {
                callback.onLoaded(allChannels.size());
            }
        });
    }

    private List<Channel> parseChannelsFromJson(String jsonStr) throws Exception {
        List<Channel> list = new ArrayList<>();
        JSONArray array = new JSONArray(jsonStr);
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String id = obj.optString("id", "ch_" + i);
            String name = obj.optString("name", "Channel " + (i + 1));
            String logo = obj.optString("logo", "");

            List<String> langs = new ArrayList<>();
            JSONArray langArray = obj.optJSONArray("languages");
            if (langArray != null) {
                for (int l = 0; l < langArray.length(); l++) {
                    langs.add(langArray.getString(l));
                }
            }

            List<String> genres = new ArrayList<>();
            JSONArray genreArray = obj.optJSONArray("genres");
            if (genreArray != null) {
                for (int g = 0; g < genreArray.length(); g++) {
                    genres.add(genreArray.getString(g));
                }
            }

            List<String> streams = new ArrayList<>();
            JSONArray streamArray = obj.optJSONArray("streams");
            if (streamArray != null) {
                for (int s = 0; s < streamArray.length(); s++) {
                    String st = streamArray.getString(s);
                    if (st != null && !st.isEmpty()) streams.add(st);
                }
            }

            if (!streams.isEmpty()) {
                list.add(new Channel(id, name, logo, langs, genres, streams));
            }
        }
        return list;
    }

    private List<Channel> loadFromDiskOrAssets() {
        InputStream is = null;
        File cachedFile = new File(context.getFilesDir(), CACHE_FILENAME);
        android.content.SharedPreferences prefs = context.getSharedPreferences("snappy_tv_prefs", Context.MODE_PRIVATE);
        long currentVersion = 1;
        try {
            currentVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (Exception ignored) {}
        long cachedVersion = prefs.getLong("cached_playlist_version", 0);
        if (cachedVersion < currentVersion) {
            if (cachedFile.exists()) {
                Log.i(TAG, "App updated from version " + cachedVersion + " to " + currentVersion + ". Clearing outdated channel cache.");
                cachedFile.delete();
            }
            prefs.edit().putLong("cached_playlist_version", currentVersion).apply();
        }

        try {
            if (cachedFile.exists() && cachedFile.length() > 1024) {
                Log.i(TAG, "Loading channels from local cache: " + cachedFile.getAbsolutePath());
                is = new FileInputStream(cachedFile);
            } else {
                Log.i(TAG, "Loading channels from assets/channels.json");
                is = context.getAssets().open("channels.json");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return parseChannelsFromJson(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error loading channels json", e);
            // If cache was corrupted, try falling back to assets directly
            if (cachedFile.exists()) {
                cachedFile.delete();
                try {
                    InputStream assetIs = context.getAssets().open("channels.json");
                    BufferedReader r = new BufferedReader(new InputStreamReader(assetIs, "UTF-8"));
                    StringBuilder s = new StringBuilder();
                    String l;
                    while ((l = r.readLine()) != null) s.append(l);
                    r.close();
                    return parseChannelsFromJson(s.toString());
                } catch (Exception ignored) {}
            }
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignored) {}
            }
        }
        return new ArrayList<>();
    }

    public boolean hasLocalCache() {
        File cached = new File(context.getFilesDir(), CACHE_FILENAME);
        return cached.exists() && cached.length() > 1024;
    }

    public String getPlaylistSyncInfo() {
        File cached = new File(context.getFilesDir(), CACHE_FILENAME);
        android.content.SharedPreferences prefs = context.getSharedPreferences("snappy_tv_prefs", Context.MODE_PRIVATE);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault());

        if (cached.exists() && cached.length() > 1024) {
            long ts = prefs.getLong("last_playlist_sync_timestamp", 0);
            if (ts <= 0) {
                ts = cached.lastModified();
            }
            if (ts > 0) {
                return "Last Synced: " + sdf.format(new java.util.Date(ts)) + " (Online Sync)";
            }
            return "Catalog: Online Synced";
        } else {
            long installTime = 0;
            String verName = "1.9";
            try {
                android.content.pm.PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                installTime = pi.lastUpdateTime;
                verName = pi.versionName;
            } catch (Exception ignored) {}

            if (installTime > 0) {
                return "Catalog: Built-in (App v" + verName + ", " + sdf.format(new java.util.Date(installTime)) + ")";
            }
            return "Catalog: Built-in Assets (App v" + verName + ")";
        }
    }

    public void updateFromRemoteUrl(String remoteUrl, UpdateCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            InputStream is = null;
            try {
                URL url = new URL(remoteUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (SmartTV; Linux; Android 9)");

                int code = conn.getResponseCode();
                if (code != 200) {
                    if (callback != null) callback.onError("Server returned HTTP " + code);
                    return;
                }

                is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                String jsonStr = sb.toString();
                // Validate JSON
                JSONArray array = new JSONArray(jsonStr);
                if (array.length() < 10) {
                    if (callback != null) callback.onError("Invalid channel data received");
                    return;
                }

                // Write to cache atomically
                File cacheDir = context.getFilesDir();
                File tempFile = new File(cacheDir, CACHE_FILENAME + ".tmp");
                File finalFile = new File(cacheDir, CACHE_FILENAME);

                FileOutputStream fos = new FileOutputStream(tempFile);
                fos.write(jsonStr.getBytes("UTF-8"));
                fos.flush();
                fos.close();

                if (tempFile.renameTo(finalFile) || (finalFile.delete() && tempFile.renameTo(finalFile))) {
                    android.content.SharedPreferences prefs = context.getSharedPreferences("snappy_tv_prefs", Context.MODE_PRIVATE);
                    prefs.edit().putLong("last_playlist_sync_timestamp", System.currentTimeMillis()).apply();

                    List<Channel> reloaded = parseChannelsFromJson(jsonStr);
                    synchronized (allChannels) {
                        allChannels.clear();
                        allChannels.addAll(reloaded);
                    }
                    if (callback != null) callback.onSuccess(allChannels.size());
                } else {
                    if (callback != null) callback.onError("Failed to save channels cache file");
                }
            } catch (Exception e) {
                Log.e(TAG, "Remote update error", e);
                if (callback != null) callback.onError(e.getMessage() != null ? e.getMessage() : "Network error");
            } finally {
                if (is != null) {
                    try { is.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    public void clearCache(Callback callback) {
        executor.execute(() -> {
            File cached = new File(context.getFilesDir(), CACHE_FILENAME);
            if (cached.exists()) {
                cached.delete();
            }
            List<Channel> loaded = loadFromDiskOrAssets();
            synchronized (allChannels) {
                allChannels.clear();
                allChannels.addAll(loaded);
            }
            if (callback != null) {
                callback.onLoaded(allChannels.size());
            }
        });
    }

    public List<String> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    public List<Channel> getAllChannels() {
        return allChannels;
    }

    public List<Channel> searchChannels(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String q = query.toLowerCase().trim();
        List<Channel> matches = new ArrayList<>();
        synchronized (allChannels) {
            for (Channel ch : allChannels) {
                if (ch.getName().toLowerCase().contains(q)) {
                    matches.add(ch);
                }
            }
        }
        sortFavoritesFirst(matches);
        return matches;
    }

    public List<Channel> getChannelsForCategory(String category) {
        if (category == null || category.equals(CAT_ALL)) {
            List<Channel> list = new ArrayList<>(allChannels);
            sortFavoritesFirst(list);
            return list;
        }
        if (CAT_SEARCH.equals(category)) {
            return Collections.emptyList();
        }

        List<Channel> filtered = new ArrayList<>();
        boolean isFav = category.equals(CAT_FAVORITES);

        synchronized (allChannels) {
            for (Channel ch : allChannels) {
                if (isFav) {
                    if (ch.isFavorite()) filtered.add(ch);
                } else if (ch.matchesCategory(category)) {
                    filtered.add(ch);
                }
            }
        }

        if (!isFav) {
            sortFavoritesFirst(filtered);
        }
        return filtered;
    }

    private void sortFavoritesFirst(List<Channel> list) {
        if (list == null || list.size() <= 1) return;
        Collections.sort(list, (c1, c2) -> {
            boolean f1 = c1.isFavorite();
            boolean f2 = c2.isFavorite();
            if (f1 != f2) {
                return f1 ? -1 : 1;
            }
            return 0;
        });
    }
}
