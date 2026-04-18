package com.inulute.mediumunlocker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HistoryManager {

    private static final String PREFS_NAME = "MediumUnlockerHistory";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final int MAX_HISTORY = 100;

    private static HistoryManager instance;
    private final SharedPreferences prefs;

    public static class HistoryItem {
        public String title;
        public String originalUrl;
        public String freediumUrl;
        public long timestamp;

        public HistoryItem(String title, String originalUrl, String freediumUrl, long timestamp) {
            this.title = title != null ? title : "";
            this.originalUrl = originalUrl != null ? originalUrl : "";
            this.freediumUrl = freediumUrl != null ? freediumUrl : "";
            this.timestamp = timestamp;
        }

        public JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("title", title);
            obj.put("originalUrl", originalUrl);
            obj.put("freediumUrl", freediumUrl);
            obj.put("timestamp", timestamp);
            return obj;
        }

        public static HistoryItem fromJson(JSONObject obj) throws Exception {
            return new HistoryItem(
                    obj.optString("title", ""),
                    obj.optString("originalUrl", ""),
                    obj.optString("freediumUrl", ""),
                    obj.optLong("timestamp", 0)
            );
        }
    }

    private HistoryManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static HistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new HistoryManager(context);
        }
        return instance;
    }

    public void saveToHistory(String title, String originalUrl, String freediumUrl) {
        List<HistoryItem> history = getHistory();
        List<HistoryItem> filtered = new ArrayList<>();
        for (HistoryItem item : history) {
            if (!item.originalUrl.equals(originalUrl)) filtered.add(item);
        }
        filtered.add(0, new HistoryItem(title, originalUrl, freediumUrl, System.currentTimeMillis()));
        if (filtered.size() > MAX_HISTORY) filtered = filtered.subList(0, MAX_HISTORY);
        saveList(KEY_HISTORY, filtered);
    }

    public void removeFromHistory(String originalUrl) {
        List<HistoryItem> history = getHistory();
        List<HistoryItem> filtered = new ArrayList<>();
        for (HistoryItem item : history) {
            if (!item.originalUrl.equals(originalUrl)) filtered.add(item);
        }
        saveList(KEY_HISTORY, filtered);
    }

    public void addBookmark(String title, String originalUrl, String freediumUrl) {
        List<HistoryItem> bookmarks = getBookmarks();
        List<HistoryItem> filtered = new ArrayList<>();
        for (HistoryItem item : bookmarks) {
            if (!item.originalUrl.equals(originalUrl)) filtered.add(item);
        }
        filtered.add(0, new HistoryItem(title, originalUrl, freediumUrl, System.currentTimeMillis()));
        saveList(KEY_BOOKMARKS, filtered);
    }

    public void removeBookmark(String originalUrl) {
        List<HistoryItem> bookmarks = getBookmarks();
        List<HistoryItem> filtered = new ArrayList<>();
        for (HistoryItem item : bookmarks) {
            if (!item.originalUrl.equals(originalUrl)) filtered.add(item);
        }
        saveList(KEY_BOOKMARKS, filtered);
    }

    public boolean isBookmarked(String originalUrl) {
        if (originalUrl == null) return false;
        for (HistoryItem item : getBookmarks()) {
            if (item.originalUrl.equals(originalUrl)) return true;
        }
        return false;
    }

    public List<HistoryItem> getHistory() {
        return loadList(KEY_HISTORY);
    }

    public List<HistoryItem> getBookmarks() {
        return loadList(KEY_BOOKMARKS);
    }

    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }

    public void clearBookmarks() {
        prefs.edit().remove(KEY_BOOKMARKS).apply();
    }

    public String exportToJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("history", listToJsonArray(getHistory()));
            root.put("bookmarks", listToJsonArray(getBookmarks()));
            root.put("exportedAt", System.currentTimeMillis());
            root.put("version", 1);
            return root.toString(2);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean importFromJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (root.has("history")) {
                saveList(KEY_HISTORY, jsonArrayToList(root.getJSONArray("history")));
            }
            if (root.has("bookmarks")) {
                saveList(KEY_BOOKMARKS, jsonArrayToList(root.getJSONArray("bookmarks")));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<HistoryItem> loadList(String key) {
        try {
            String json = prefs.getString(key, "[]");
            JSONArray array = new JSONArray(json);
            List<HistoryItem> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                list.add(HistoryItem.fromJson(array.getJSONObject(i)));
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveList(String key, List<HistoryItem> list) {
        try {
            prefs.edit().putString(key, listToJsonArray(list).toString()).apply();
        } catch (Exception ignored) { }
    }

    private JSONArray listToJsonArray(List<HistoryItem> list) throws Exception {
        JSONArray array = new JSONArray();
        for (HistoryItem item : list) {
            array.put(item.toJson());
        }
        return array;
    }

    private List<HistoryItem> jsonArrayToList(JSONArray array) throws Exception {
        List<HistoryItem> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            list.add(HistoryItem.fromJson(array.getJSONObject(i)));
        }
        return list;
    }
}
