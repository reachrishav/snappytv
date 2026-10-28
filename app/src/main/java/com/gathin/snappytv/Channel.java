package com.gathin.snappytv;

import java.util.ArrayList;
import java.util.List;

public class Channel {
    private final String id;
    private final String name;
    private final String logoUrl;
    private final List<String> languages;
    private final List<String> genres;
    private final List<String> streams;
    private int currentStreamIndex;
    private boolean isFavorite;

    public Channel(String id, String name, String logoUrl, List<String> languages, List<String> genres, List<String> streams) {
        this.id = id;
        this.name = name;
        this.logoUrl = logoUrl != null ? logoUrl : "";
        this.languages = languages != null ? languages : new ArrayList<>();
        this.genres = genres != null ? genres : new ArrayList<>();
        this.streams = streams != null ? streams : new ArrayList<>();
        this.currentStreamIndex = 0;
        this.isFavorite = false;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public List<String> getGenres() {
        return genres;
    }

    public List<String> getStreams() {
        return streams;
    }

    public int getCurrentStreamIndex() {
        return currentStreamIndex;
    }

    public int getStreamCount() {
        return streams.size();
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        this.isFavorite = favorite;
    }

    public boolean matchesCategory(String category) {
        if (category == null || category.equalsIgnoreCase("All") || category.equalsIgnoreCase("All Channels")) {
            return true;
        }
        if (category.equalsIgnoreCase("Favorites") || category.contains("Favorite")) {
            return isFavorite;
        }
        if (category.contains("Regional")) {
            for (String lang : languages) {
                if (lang.equalsIgnoreCase("Regional")) return true;
            }
            return false;
        }
        for (String lang : languages) {
            if (lang.equalsIgnoreCase(category)) return true;
        }
        for (String genre : genres) {
            if (genre.equalsIgnoreCase(category)) return true;
        }
        return false;
    }

    public String getDisplayCategory() {
        if (!languages.isEmpty() && !genres.isEmpty()) {
            return languages.get(0) + " • " + genres.get(0);
        } else if (!genres.isEmpty()) {
            return genres.get(0);
        } else if (!languages.isEmpty()) {
            return languages.get(0);
        }
        return "Live TV";
    }

    public String getCurrentStreamUrl() {
        if (streams.isEmpty()) return null;
        if (currentStreamIndex >= streams.size()) currentStreamIndex = 0;
        return streams.get(currentStreamIndex);
    }

    public boolean hasFallbackStream() {
        return currentStreamIndex + 1 < streams.size();
    }

    public String moveToNextStream() {
        if (hasFallbackStream()) {
            currentStreamIndex++;
            return streams.get(currentStreamIndex);
        }
        return null;
    }

    public void resetStreamIndex() {
        currentStreamIndex = 0;
    }

    public void setCurrentStreamIndex(int index) {
        if (index >= 0 && index < streams.size()) {
            this.currentStreamIndex = index;
        }
    }

    public String getStreamRole(int index) {
        if (index < 0 || index >= streams.size()) {
            return "";
        }
        return (index == 0) ? "Primary" : ("Mirror " + index);
    }

    public String getStreamSource(int index) {
        if (index < 0 || index >= streams.size()) {
            return "";
        }
        String url = streams.get(index);
        String source = "";
        if (url.contains("cloudfront.net")) {
            source = "CloudFront CDN";
        } else if (url.contains("akamaized.net") || url.contains("akamai")) {
            source = "Akamai CDN";
        } else if (url.contains("tangotv")) {
            source = "TangoTV";
        } else if (url.contains("pishow")) {
            source = "PiShow";
        } else if (url.contains("wiseplayout")) {
            source = "WisePlay";
        } else if (url.contains("stvp-")) {
            source = "Samsung FAST";
        } else if (url.contains("plu-") || url.contains("pluto")) {
            source = "Pluto TV";
        } else if (url.contains("rok-") || url.contains("roku")) {
            source = "Roku";
        } else if (url.contains("intoday")) {
            source = "India Today";
        } else if (url.contains("aynascope") || url.contains("aynaott")) {
            source = "Ayna OTT";
        } else {
            try {
                android.net.Uri uri = android.net.Uri.parse(url);
                String host = uri.getHost();
                if (host != null) {
                    source = host.replace("www.", "");
                    if (source.length() > 20) {
                        source = source.substring(0, 20) + "…";
                    }
                }
            } catch (Exception ignored) {}
        }
        return source.isEmpty() ? "Direct Stream" : source;
    }

    public String getStreamSourceLabel(int index) {
        if (index < 0 || index >= streams.size()) {
            return "";
        }
        String role = getStreamRole(index);
        String source = getStreamSource(index);
        return source.isEmpty() ? role : (role + " (" + source + ")");
    }
}
