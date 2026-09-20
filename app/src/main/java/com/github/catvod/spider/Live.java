package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.okhttp.OkHttpUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * yatv 直播源播放器（Java 版 · PiaoHua 旧版风格）
 * 支持：txt（,#genre# 分组）+ m3u/m3u8（group-title 分组）
 */
public class Live extends Spider {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Map<String, String> HEADERS = new HashMap<>();
    static {
        HEADERS.put("User-Agent", UA);
    }

    // 源配置：[{name, url, pic}]
    private List<Source> sources = new ArrayList<>();
    // 缓存：线路名 -> {分类名 -> [频道]}
    private final Map<String, Map<String, List<Channel>>> dataCache = new HashMap<>();
    // 当前 extend 参数
    private String extend;

    // ============================================================
    // 内部数据类
    // ============================================================
    private static class Source {
        String name;
        String url;
        String pic;
    }

    private static class Channel {
        String name;
        String url;
    }

    // ============================================================
    // ★ fetchText —— PiaoHua 风格
    // ============================================================
    private String fetchText(String url) {
        try {
            Request request = new Request.Builder()
                    .addHeader("User-Agent", UA)
                    .get()
                    .url(url)
                    .build();

            OkHttpClient okHttpClient = OkHttpUtil.defaultClient();
            Response response = okHttpClient.newCall(request).execute();
            if (response.body() == null) return "";
            byte[] bytes = response.body().bytes();
            response.close();
            return new String(bytes, "utf-8");
        } catch (Exception e) {
            SpiderDebug.log("fetchText error: " + e.getMessage());
            return "";
        }
    }

    // ============================================================
    // init：加载源配置
    // ============================================================
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
            SpiderDebug.log("YaTV: extend 不是 URL，只支持 URL 方式");
            return;
        }

        String content = fetchText(extend);
        if (TextUtils.isEmpty(content)) return;

        try {
            JsonArray arr = Json.parse(content).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject obj = arr.get(i).getAsJsonObject();
                Source src = new Source();
                src.name = obj.has("name") ? obj.get("name").getAsString() : "";
                src.url  = obj.has("url")  ? obj.get("url").getAsString()  : "";
                src.pic  = obj.has("pic")  ? obj.get("pic").getAsString()  : "";

                // 处理 &&& 分隔 pic
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
            SpiderDebug.log("YaTV loadSources error: " + e.getMessage());
        }
    }

    // ============================================================
    // 拉取单个源的文本，解析成分类
    // ============================================================
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
        if (head.contains("#EXTM3U") || head.contains("#EXTINF")) {
            parsed = parseM3U(content);
        } else {
            parsed = parseTxt(content);
        }

        dataCache.put(name, parsed);
        return parsed;
    }

    // ============================================================
    // 解析 txt（,#genre# 分组）
    // ============================================================
    private Map<String, List<Channel>> parseTxt(String content) {
        Map<String, List<Channel>> result = new LinkedHashMap<>();
        String current = "未分类";

        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (TextUtils.isEmpty(line)) continue;

            // 分组行：xxx,#genre#
            if (line.endsWith(",#genre#")) {
                current = line.replace(",#genre#", "").trim();
                result.computeIfAbsent(current, k -> new ArrayList<>());
                continue;
            }

            // 频道行：名字,URL
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

    // ============================================================
    // 解析 m3u（group-title 分组）
    // ============================================================
    private Map<String, List<Channel>> parseM3U(String content) {
        Map<String, List<Channel>> result = new LinkedHashMap<>();
        String name = null;
        String group = "未分类";

        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (TextUtils.isEmpty(line)) continue;

            if (line.startsWith("#EXTINF")) {
                // 取 group-title="xxx"
                int gStart = line.indexOf("group-title=\"");
                if (gStart >= 0) {
                    int gEnd = line.indexOf("\"", gStart + 13);
                    if (gEnd > gStart) {
                        String g = line.substring(gStart + 13, gEnd).trim();
                        group = TextUtils.isEmpty(g) ? "未分类" : g;
                    }
                }
                // 取最后一个逗号后的名称
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

    // ============================================================
    // homeContent：源名 = 分类（不变）
    // ============================================================
    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        for (Source src : sources) {
            classes.add(new Class(src.name, src.name));
        }
        return Result.string(classes);
    }

    // ============================================================
    // categoryContent：列分类（不变）
    // ============================================================
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
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

        List<Vod> videos = new ArrayList<>();
        int total = 0;

        if (!TextUtils.isEmpty(url)) {
            Map<String, List<Channel>> data = getSourceData(tid, url);
            List<String> cats = new ArrayList<>(data.keySet());
            total = cats.size();

            int end = Math.min(start + 50, cats.size());
            for (int i = start; i < end; i++) {
                String cat = cats.get(i);
                Vod v = new Vod();
                v.setVodId(tid + "||" + cat);
                v.setVodName(cat);
                v.setVodPic(pic);
                v.setVodRemarks(data.get(cat).size() + "个频道");
                videos.add(v);
            }
        }

        int pagecount = total > 0 ? (total + 49) / 50 : 1;
        return Result.get()
                .vod(videos)
                .page(page, pagecount, 50, total)
                .string();
    }

    // ============================================================
    // detailContent：分类 -> 频道列表（不变）
    // ============================================================
    @Override
    public String detailContent(List<String> ids) {
        String vid = ids.get(0);
        Vod info = new Vod();
        info.setVodId(vid);

        if (!vid.contains("||")) return Result.string(info);

        String[] parts = vid.split("\\|\\|");

        // 情况 1：点到具体频道（src||cat||channel）
        if (parts.length == 3) {
            String src = parts[0], cat = parts[1], ch = parts[2];
            Map<String, List<Channel>> data = dataCache.get(src);
            if (data != null && data.containsKey(cat)) {
                for (Channel item : data.get(cat)) {
                    if (item.name.equals(ch)) {
                        info.setVodName(ch);
                        info.setVodRemarks(src + " · " + cat);
                        info.setVodPlayFrom("播放");
                        info.setVodPlayUrl(ch + "$" + item.url);
                        info.setVodContent("线路: " + src + "\n分类: " + cat);
                        return Result.string(info);
                    }
                }
            }
        }

        // 情况 2：点到分类（src||cat）
        if (parts.length >= 2) {
            String src = parts[0], cat = parts[1];
            Map<String, List<Channel>> data = dataCache.get(src);
            if (data != null && data.containsKey(cat)) {
                List<Channel> chList = data.get(cat);
                info.setVodName(cat);
                info.setVodRemarks(src + " · " + chList.size() + "个频道");

                List<String> playUrls = new ArrayList<>();
                for (Channel item : chList) {
                    playUrls.add(item.name + "$" + item.url);
                }

                info.setVodPlayFrom(cat);
                info.setVodPlayUrl(TextUtils.join("#", playUrls));
                info.setVodContent("线路: " + src + "\n分类: " + cat + "\n频道数: " + chList.size());
                return Result.string(info);
            }
        }

        return Result.string(info);
    }

    // ============================================================
    // searchContent（不变）
    // ============================================================
    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        List<Vod> results = new ArrayList<>();
        String lower = key.toLowerCase();

        for (Map.Entry<String, Map<String, List<Channel>>> entry : dataCache.entrySet()) {
            String src = entry.getKey();
            for (Map.Entry<String, List<Channel>> catEntry : entry.getValue().entrySet()) {
                String cat = catEntry.getKey();
                for (Channel item : catEntry.getValue()) {
                    if (item.name.toLowerCase().contains(lower)) {
                        Vod v = new Vod();
                        v.setVodId(src + "||" + cat + "||" + item.name);
                        v.setVodName("[" + src + "] " + item.name);
                        v.setVodPic("");
                        v.setVodRemarks(cat);
                        results.add(v);
                        if (results.size() >= 50) break;
                    }
                }
            }
        }

        return Result.get()
                .vod(results)
                .page(1, 1, 50, results.size())
                .string();
    }

    // ============================================================
    // ★★ playerContent —— 一字未改 ★★
    // ============================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).header(HEADERS).string();
    }

    // ============================================================
    // destroy（去掉 @Override）
    // ============================================================
    public void destroy() {
        dataCache.clear();
    }
}