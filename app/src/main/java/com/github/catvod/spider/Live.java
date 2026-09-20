package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.okhttp.OkHttpUtil;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live 直播源播放器（PiaoHua 旧版风格）
 */
public class Live extends Spider {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Map<String, String> HEADERS = new HashMap<>();
    static {
        HEADERS.put("User-Agent", UA);
    }

    private List<Source> sources = new ArrayList<>();
    private final Map<String, Map<String, List<Channel>>> dataCache = new HashMap<>();
    private String extend;

    private static class Source {
        String name;
        String url;
        String pic;
    }

    private static class Channel {
        String name;
        String url;
    }

    private String fetchText(String url) {
        try {
            Request request = new Request.Builder()
                    .addHeader("User-Agent", UA)
                    .get()
                    .url(url)
                    .build();
            OkHttpClient client = OkHttpUtil.defaultClient();
            Response response = client.newCall(request).execute();
            if (response.body() == null) return "";
            byte[] bytes = response.body().bytes();
            response.close();
            return new String(bytes, "utf-8");
        } catch (Exception e) {
            SpiderDebug.log("fetchText error: " + e.getMessage());
            return "";
        }
    }

    @Override
    public void init(Context context, String extend) {
        this.extend = extend;
        this.sources = new ArrayList<>();
        this.dataCache.clear();
        loadSources();
    }

    private void loadSources() {
        if (TextUtils.isEmpty(extend)) return;
        if (!extend.startsWith("http")) {
            SpiderDebug.log("Live: extend 不是 URL");
            return;
        }
        String content = fetchText(extend);
        if (TextUtils.isEmpty(content)) return;
        try {
            JSONArray arr = new JSONArray(content);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                Source src = new Source();
                src.name = obj.optString("name", "");
                src.url  = obj.optString("url", "");
                src.pic  = obj.optString("pic", "");

                if (src.url.contains("&&&")) {
                    String[] parts = src.url.split("&&&");
                    src.url = parts[0];
                    if (parts.length > 1) src.pic = parts[1];
                }
                if (!TextUtils.isEmpty(src.name) && !TextUtils.isEmpty(src.url)) {
                    sources.add(src);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("loadSources error: " + e.getMessage());
        }
    }

    private Map<String, List<Channel>> getSourceData(String name, String url) {
        if (dataCache.containsKey(name)) return dataCache.get(name);
        String content = fetchText(url);
        if (TextUtils.isEmpty(content)) {
            Map<String, List<Channel>> empty = new HashMap<>();
            dataCache.put(name, empty);
            return empty;
        }
        Map<String, List<Channel>> parsed;
        String head = content.length() > 2000 ? content.substring(0, 2000) : content;
        if (head.contains("#EXTM3U") || head.contains("#EXTINF")) parsed = parseM3U(content);
        else parsed = parseTxt(content);
        dataCache.put(name, parsed);
        return parsed;
    }

    private Map<String, List<Channel>> parseTxt(String content) {
        Map<String, List<Channel>> result = new LinkedHashMap<>();
        String current = "未分类";
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (TextUtils.isEmpty(line)) continue;
            if (line.endsWith(",#genre#")) {
                current = line.replace(",#genre#", "").trim();
                result.computeIfAbsent(current, k -> new ArrayList<>());
                continue;
            }
            if (line.contains(",")) {
                int idx = line.indexOf(',');
                String name = line.substring(0, idx).trim();
                String url  = line.substring(idx + 1).trim();
                if (!TextUtils.isEmpty(name) && url.contains("://")) {
                    result.computeIfAbsent(current, k -> new ArrayList<>());
                    Channel ch = new Channel();
                    ch.name = name;
                    ch.url  = url;
                    result.get(current).add(ch);
                }
            }
        }
        return result;
    }

    private Map<String, List<Channel>> parseM3U(String content) {
        Map<String, List<Channel>> result = new LinkedHashMap<>();
        String name = null;
        String group = "未分类";
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (TextUtils.isEmpty(line)) continue;
            if (line.startsWith("#EXTINF")) {
                int gStart = line.indexOf("group-title=\"");
                if (gStart >= 0) {
                    int gEnd = line.indexOf("\"", gStart + 13);
                    if (gEnd > gStart) {
                        String g = line.substring(gStart + 13, gEnd).trim();
                        group = TextUtils.isEmpty(g) ? "未分类" : g;
                    }
                }
                int comma = line.lastIndexOf(',');
                if (comma >= 0) {
                    name = line.substring(comma + 1).trim();
                    if (TextUtils.isEmpty(name)) name = "未命名";
                }
            } else if (line.startsWith("#")) {
                continue;
            } else {
                if (!TextUtils.isEmpty(name) && line.contains("://")) {
                    result.computeIfAbsent(group, k -> new ArrayList<>());
                    Channel ch = new Channel();
                    ch.name = name;
                    ch.url  = line;
                    result.get(group).add(ch);
                }
                name = null;
            }
        }
        return result;
    }

    // ★ homeContent：手拼 JSON
    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();
            for (Source src : sources) {
                JSONObject o = new JSONObject();
                o.put("type_id", src.name);
                o.put("type_name", src.name);
                o.put("type_pic", src.pic);
                classes.put(o);
            }
            result.put("class", classes);
            result.put("filters", new JSONObject());
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("homeContent error: " + e.getMessage());
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = 1;
            try { page = Integer.parseInt(pg); } catch (Exception ignored) {}
            int start = (page - 1) * 50;

            String url = "";
            String pic = "";
            for (Source src : sources) {
                if (src.name.equals(tid)) {
                    url = src.url;
                    pic = src.pic;
                    break;
                }
            }

            JSONArray videos = new JSONArray();
            int total = 0;
            if (!TextUtils.isEmpty(url)) {
                Map<String, List<Channel>> data = getSourceData(tid, url);
                List<String> cats = new ArrayList<>(data.keySet());
                total = cats.size();
                int end = Math.min(start + 50, cats.size());
                for (int i = start; i < end; i++) {
                    String cat = cats.get(i);
                    JSONObject v = new JSONObject();
                    v.put("vod_id", tid + "||" + cat);
                    v.put("vod_name", cat);
                    v.put("vod_pic", pic);
                    v.put("vod_remarks", data.get(cat).size() + "个频道");
                    videos.put(v);
                }
            }
            int pagecount = total > 0 ? (total + 49) / 50 : 1;

            JSONObject r = new JSONObject();
            r.put("list", videos);
            r.put("page", page);
            r.put("pagecount", pagecount);
            r.put("limit", 50);
            r.put("total", total);
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("categoryContent error: " + e.getMessage());
            return "";
        }
    }

    // ★ detailContent：手拼 JSON
    @Override
    public String detailContent(List<String> ids) {
        try {
            String vid = ids.get(0);
            JSONObject info = new JSONObject();
            info.put("vod_id", vid);

            if (!vid.contains("||")) {
                JSONArray arr = new JSONArray();
                arr.put(info);
                JSONObject r = new JSONObject();
                r.put("list", arr);
                return r.toString();
            }

            String[] parts = vid.split("\\|\\|");

            if (parts.length == 3) {
                String src = parts[0], cat = parts[1], ch = parts[2];
                Map<String, List<Channel>> data = dataCache.get(src);
                if (data != null && data.containsKey(cat)) {
                    for (Channel item : data.get(cat)) {
                        if (item.name.equals(ch)) {
                            info.put("vod_name", ch);
                            info.put("vod_remarks", src + " · " + cat);
                            info.put("vod_play_from", "播放");
                            info.put("vod_play_url", ch + "$" + item.url);
                            info.put("vod_content", "线路: " + src + "\n分类: " + cat);
                            break;
                        }
                    }
                }
            } else if (parts.length >= 2) {
                String src = parts[0], cat = parts[1];
                Map<String, List<Channel>> data = dataCache.get(src);
                if (data != null && data.containsKey(cat)) {
                    List<Channel> chList = data.get(cat);
                    info.put("vod_name", cat);
                    info.put("vod_remarks", src + " · " + chList.size() + "个频道");
                    List<String> playUrls = new ArrayList<>();
                    for (Channel item : chList) playUrls.add(item.name + "$" + item.url);
                    info.put("vod_play_from", cat);
                    info.put("vod_play_url", TextUtils.join("#", playUrls));
                    info.put("vod_content", "线路: " + src + "\n分类: " + cat + "\n频道数: " + chList.size());
                }
            }

            JSONArray list = new JSONArray();
            list.put(info);
            JSONObject r = new JSONObject();
            r.put("list", list);
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("detailContent error: " + e.getMessage());
            return "";
        }
    }

    // ★ searchContent：手拼 JSON
    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        try {
            JSONArray results = new JSONArray();
            String lower = key.toLowerCase();
            outer:
            for (Map.Entry<String, Map<String, List<Channel>>> entry : dataCache.entrySet()) {
                String src = entry.getKey();
                for (Map.Entry<String, List<Channel>> catEntry : entry.getValue().entrySet()) {
                    String cat = catEntry.getKey();
                    for (Channel item : catEntry.getValue()) {
                        if (item.name.toLowerCase().contains(lower)) {
                            JSONObject v = new JSONObject();
                            v.put("vod_id", src + "||" + cat + "||" + item.name);
                            v.put("vod_name", "[" + src + "] " + item.name);
                            v.put("vod_pic", "");
                            v.put("vod_remarks", cat);
                            results.put(v);
                            if (results.length() >= 50) break outer;
                        }
                    }
                }
            }
            JSONObject r = new JSONObject();
            r.put("list", results);
            r.put("page", 1);
            r.put("pagecount", 1);
            r.put("limit", 50);
            r.put("total", results.length());
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("searchContent error: " + e.getMessage());
            return "";
        }
    }

    // ★ playerContent 一字未改
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            JSONObject r = new JSONObject();
            r.put("parse", 0);
            r.put("url", id);
            r.put("header", new JSONObject(HEADERS));
            return r.toString();
        } catch (Exception e) {
            return "";
        }
    }
}