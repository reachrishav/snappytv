package com.gathin.snappytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FavoritesManager {
    private static final String TAG = "FavoritesManager";
    private static final String PREF_NAME = "snappytv_favorites_pref";
    private static final String KEY_FAVORITES = "favorite_channel_ids";
    private static final String BACKUP_FILENAME = "snappytv_backup.json";

    private final Context context;
    private final SharedPreferences prefs;
    private final Set<String> favoriteIds;

    public FavoritesManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.favoriteIds = new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
        if (this.favoriteIds.isEmpty()) {
            loadFromBackupIfExists();
        }
    }

    private void loadFromBackupIfExists() {
        try {
            File backup = findBackupFile();
            if (backup != null && backup.exists() && backup.length() > 0) {
                FileInputStream fis = new FileInputStream(backup);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray favArray = json.optJSONArray("favorites");
                if (favArray != null && favArray.length() > 0) {
                    for (int i = 0; i < favArray.length(); i++) {
                        favoriteIds.add(favArray.getString(i));
                    }
                    saveFavorites();
                    Log.i(TAG, "Auto-restored " + favoriteIds.size() + " favorites from " + backup.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Auto-restore from backup: " + e.getMessage());
        }
    }

    public synchronized boolean isFavorite(String channelId) {
        return favoriteIds.contains(channelId);
    }

    public synchronized boolean toggleFavorite(Channel channel) {
        if (channel == null) return false;
        String id = channel.getId();
        boolean nowFav;
        if (favoriteIds.contains(id)) {
            favoriteIds.remove(id);
            nowFav = false;
        } else {
            favoriteIds.add(id);
            nowFav = true;
        }
        channel.setFavorite(nowFav);
        saveFavorites();
        try {
            exportBackup(id, "");
        } catch (Exception ignored) {}
        return nowFav;
    }

    public synchronized void applyFavorites(List<Channel> channels) {
        if (channels == null) return;
        for (Channel ch : channels) {
            ch.setFavorite(favoriteIds.contains(ch.getId()));
        }
    }

    public synchronized int getFavoritesCount() {
        return favoriteIds.size();
    }

    private void saveFavorites() {
        prefs.edit().putStringSet(KEY_FAVORITES, new HashSet<>(favoriteIds)).apply();
    }

    // --- Export / Import Backup ---
    public synchronized String exportBackup(String lastChannelId, String lastCategory) throws Exception {
        JSONObject json = new JSONObject();
        json.put("app", "SnappyTV");
        json.put("version", 1);
        json.put("timestamp", System.currentTimeMillis());
        json.put("last_channel_id", lastChannelId != null ? lastChannelId : "");
        json.put("last_category", lastCategory != null ? lastCategory : "");

        JSONArray favArray = new JSONArray();
        for (String id : favoriteIds) {
            favArray.put(id);
        }
        json.put("favorites", favArray);

        File[] candidateDirs = new File[] {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStorageDirectory(),
                context.getExternalFilesDir(null),
                context.getFilesDir()
        };

        File targetFile = null;
        Exception lastError = null;
        for (File dir : candidateDirs) {
            if (dir == null) continue;
            try {
                if (!dir.exists()) dir.mkdirs();
                File candidate = new File(dir, BACKUP_FILENAME);
                FileOutputStream fos = new FileOutputStream(candidate);
                fos.write(json.toString(2).getBytes("UTF-8"));
                fos.flush();
                fos.close();
                targetFile = candidate;
                break;
            } catch (Exception e) {
                lastError = e;
            }
        }

        if (targetFile == null) {
            throw (lastError != null ? lastError : new Exception("Could not write backup to storage"));
        }

        Log.i(TAG, "Exported " + favoriteIds.size() + " favorites to " + targetFile.getAbsolutePath());
        return targetFile.getAbsolutePath();
    }

    public synchronized int importBackup(List<Channel> channels) throws Exception {
        File backupFile = findBackupFile();
        if (backupFile == null || !backupFile.exists()) {
            throw new Exception("Backup file not found at /sdcard/Download/" + BACKUP_FILENAME);
        }

        FileInputStream fis = new FileInputStream(backupFile);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();

        JSONObject json = new JSONObject(sb.toString());
        JSONArray favArray = json.optJSONArray("favorites");
        if (favArray == null) {
            throw new Exception("Invalid backup file: missing favorites array");
        }

        favoriteIds.clear();
        for (int i = 0; i < favArray.length(); i++) {
            favoriteIds.add(favArray.getString(i));
        }

        saveFavorites();
        applyFavorites(channels);
        Log.i(TAG, "Imported " + favoriteIds.size() + " favorites from " + backupFile.getAbsolutePath());
        return favoriteIds.size();
    }

    private File findBackupFile() {
        List<File> candidateDirs = new ArrayList<>();
        candidateDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        candidateDirs.add(new File("/sdcard/Download"));
        candidateDirs.add(new File("/storage/emulated/0/Download"));
        candidateDirs.add(Environment.getExternalStorageDirectory());
        candidateDirs.add(new File("/sdcard"));
        candidateDirs.add(new File("/storage/emulated/0"));
        candidateDirs.add(context.getExternalFilesDir(null));
        candidateDirs.add(context.getFilesDir());

        // Check connected USB / OTG storage volumes
        try {
            File storageRoot = new File("/storage");
            if (storageRoot.exists() && storageRoot.isDirectory()) {
                File[] volumes = storageRoot.listFiles();
                if (volumes != null) {
                    for (File vol : volumes) {
                        if (vol.isDirectory() && !vol.getName().equalsIgnoreCase("emulated") && !vol.getName().equalsIgnoreCase("self")) {
                            candidateDirs.add(vol);
                            candidateDirs.add(new File(vol, "Download"));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        for (File dir : candidateDirs) {
            if (dir == null) continue;
            try {
                File f = new File(dir, BACKUP_FILENAME);
                if (f.exists() && f.isFile() && f.length() > 0) return f;
            } catch (Exception ignored) {}
        }
        return null;
    }
}
