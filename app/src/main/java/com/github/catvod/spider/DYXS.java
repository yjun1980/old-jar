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

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 电影先生 - silidm.com
 * PiaoHua 旧版风格
 */
public class DYXS extends Spider {

    private static final String API_HOST = "https://silidm.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private Map<String, String> getHeader() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("accept-language", "zh-CN,zh;q=0.9");
        h.put("Referer", API_HOST + "/");
        h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        return h;
    }

    // ★ PiaoHua 风格 fetchHtml
    private String fetchHtml(String url) {
        try {
            Request request = new Request.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("accept-language", "zh-CN,zh;q=0.9")
                    .addHeader("Referer", API_HOST + "/")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
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
            SpiderDebug.log("fetchHtml error: " + e.getMessage());
            return "";
        }
    }

    private String cleanHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http")) return url;
        if (url.startsWith("/")) return API_HOST + url;
        return API_HOST + "/" + url;
    }

    private String decodeUnicode(String str) {
        if (TextUtils.isEmpty(str)) return str;
        try {
            Matcher m = Pattern.compile("\\\\u([\\dA-Fa-f]{4})").matcher(str);
            StringBuilder sb = new StringBuilder();
            int last = 0;
            while (m.find()) {
                sb.append(str, last, m.start());
                sb.append((char) Integer.parseInt(m.group(1), 16));
                last = m.end();
            }
            sb.append(str.substring(last));
            return sb.toString();
        } catch (Exception e) {
            return str;
        }
    }

    private String group(String regex, String text, int g) {
        if (text == null) return "";
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        return m.find() ? (m.group(g) == null ? "" : m.group(g)) : "";
    }

    private String urlEncode(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    // ============================================================
    // 列表解析
    // ============================================================
    private JSONArray extractVideos(String html) {
        JSONArray videos = new JSONArray();
        if (TextUtils.isEmpty(html)) return videos;

        Matcher blockM = Pattern.compile(
                "<div class=\"module-item\">[\\s\\S]*?</div>\\s*</div>\\s*</div>"
        ).matcher(html);

        while (blockM.find()) {
            try {
                String block = blockM.group();
                String id = group("/video/(\\d+)\\.html", block, 1);
                if (id.isEmpty()) continue;

                String title = group("class=\"module-item-title\"[^>]*title=\"([^\"]+)\"", block, 1);
                if (title.isEmpty()) title = group("<a[^>]*href=\"/video/\\d+\\.html\"[^>]*>([^<]+)</a>", block, 1).trim();
                if (title.isEmpty()) continue;

                String pic = group("data-src=\"([^\"]+)\"", block, 1);
                if (pic.isEmpty()) pic = group("src=\"([^\"]+\\.(?:png|jpg|jpeg|webp))\"", block, 1);
                if (!pic.isEmpty() && !pic.startsWith("http")) pic = fixUrl(pic);

                String remark = group("<div class=\"module-item-text\">([^<]*)</div>", block, 1).trim();
                if (remark.isEmpty()) remark = "更新中";

                JSONObject v = new JSONObject();
                v.put("vod_id", id);
                v.put("vod_name", title);
                v.put("vod_pic", pic);
                v.put("vod_remarks", remark);
                videos.put(v);
            } catch (Exception ignored) {}
        }

        // 备用正则
        if (videos.length() == 0) {
            Matcher m = Pattern.compile(
                    "/video/(\\d+)\\.html[^>]*>([^<]+)</a>[\\s\\S]*?(?:data-src|src)=\"([^\"]+)\""
            ).matcher(html);
            while (m.find()) {
                try {
                    String pic = m.group(3);
                    if (!pic.startsWith("http")) pic = fixUrl(pic);
                    JSONObject v = new JSONObject();
                    v.put("vod_id", m.group(1));
                    v.put("vod_name", m.group(2).trim());
                    v.put("vod_pic", pic);
                    v.put("vod_remarks", "更新中");
                    videos.put(v);
                } catch (Exception ignored) {}
            }
        }
        return videos;
    }

    @Override
    public void init(Context context, String extend) {
        SpiderDebug.log("DianYingXianSheng init");
    }

    // ============================================================
    // homeContent：手拼 JSON
    // ============================================================
    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();

            JSONArray classes = new JSONArray();
            String[][] cls = {
                    {"dy", "电影"}, {"juji", "剧集"}, {"dongman", "动漫"}, {"zongyi", "综艺"}
            };
            for (String[] c : cls) {
                JSONObject o = new JSONObject();
                o.put("type_id", c[0]);
                o.put("type_name", c[1]);
                classes.put(o);
            }
            result.put("class", classes);

            JSONObject filters = new JSONObject();

            // 电影筛选
            JSONArray dy = new JSONArray();
            dy.put(filterGroup("class", "类型", new String[][]{
                    {"", "全部"}, {"剧情", "剧情"}, {"喜剧", "喜剧"}, {"动作", "动作"},
                    {"爱情", "爱情"}, {"科幻", "科幻"}, {"恐怖", "恐怖"}, {"战争", "战争"},
                    {"悬疑", "悬疑"}, {"冒险", "冒险"}, {"犯罪", "犯罪"}, {"奇幻", "奇幻"},
                    {"动画", "动画"}
            }));
            dy.put(filterGroup("year", "年份", yearFilter()));
            filters.put("dy", dy);

            // 剧集筛选
            JSONArray juji = new JSONArray();
            juji.put(filterGroup("class", "剧情", new String[][]{
                    {"", "全部"}, {"国产剧", "国产剧"}, {"美剧", "美剧"}, {"韩国剧", "韩国剧"},
                    {"港剧", "港剧"}, {"台湾剧", "台湾剧"}, {"日本剧", "日本剧"}, {"泰国剧", "泰国剧"}
            }));
            juji.put(filterGroup("year", "年份", yearFilter()));
            filters.put("juji", juji);

            // 动漫筛选
            JSONArray dm = new JSONArray();
            dm.put(filterGroup("class", "类型", new String[][]{
                    {"", "全部"}, {"国产动漫", "国产动漫"}, {"日本动漫", "日本动漫"}, {"欧美动漫", "欧美动漫"}
            }));
            dm.put(filterGroup("year", "年份", yearFilter()));
            filters.put("dongman", dm);

            // 综艺筛选
            JSONArray zy = new JSONArray();
            zy.put(filterGroup("class", "类型", new String[][]{
                    {"", "全部"}, {"大陆综艺", "大陆综艺"}, {"港台综艺", "港台综艺"},
                    {"日韩综艺", "日韩综艺"}, {"欧美综艺", "欧美综艺"}
            }));
            zy.put(filterGroup("year", "年份", yearFilter()));
            filters.put("zongyi", zy);

            result.put("filters", filters);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("homeContent error: " + e.getMessage());
            return "";
        }
    }

    private JSONArray yearFilter() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject o0 = new JSONObject();
        o0.put("n", "全部"); o0.put("v", "");
        arr.put(o0);
        for (int y = 2026; y >= 2015; y--) {
            JSONObject o = new JSONObject();
            o.put("n", String.valueOf(y));
            o.put("v", String.valueOf(y));
            arr.put(o);
        }
        return arr;
    }

    private JSONObject filterGroup(String key, String name, String[][] values) throws Exception {
        JSONArray arr = new JSONArray();
        for (String[] kv : values) {
            JSONObject o = new JSONObject();
            o.put("v", kv[0]);
            o.put("n", kv[1]);
            arr.put(o);
        }
        JSONObject obj = new JSONObject();
        obj.put("key", key);
        obj.put("name", name);
        obj.put("value", arr);
        return obj;
    }

    private JSONObject filterGroup(String key, String name, JSONArray values) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("key", key);
        obj.put("name", name);
        obj.put("value", values);
        return obj;
    }

    // ============================================================
    // homeVideoContent
    // ============================================================
    @Override
    public String homeVideoContent() {
        try {
            String html = fetchHtml(API_HOST + "/");
            JSONArray videos = extractVideos(html);
            JSONArray list = new JSONArray();
            for (int i = 0; i < videos.length() && i < 12; i++) list.put(videos.get(i));
            JSONObject r = new JSONObject();
            r.put("list", list);
            return r.toString();
        } catch (Exception e) {
            return "{\"list\":[]}";
        }
    }

    // ============================================================
    // categoryContent
    // ============================================================
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = 1;
            try { page = Integer.parseInt(pg); } catch (Exception ignored) {}

            String classVal = extend != null && extend.get("class") != null ? extend.get("class") : "";
            String yearVal  = extend != null && extend.get("year") != null ? extend.get("year") : "";

            String url = API_HOST + "/show/" + tid + "---" + classVal + "--------" + yearVal + ".html";
            if (page > 1) url += "?page=" + page;

            String html = fetchHtml(url);
            JSONArray videos = extractVideos(html);

            JSONObject r = new JSONObject();
            r.put("page", page);
            r.put("list", videos);
            r.put("pagecount", 100);
            r.put("limit", 30);
            r.put("total", 3000);
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("categoryContent error: " + e.getMessage());
            return "{\"page\":1,\"list\":[]}";
        }
    }

    // ============================================================
    // detailContent
    // ============================================================
    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String html = fetchHtml(API_HOST + "/video/" + id + ".html");

            String title = group("<h1[^>]*class=\"[^\"]*page-title[^\"]*\"[^>]*>([^<]+)</h1>", html, 1);
            if (title.isEmpty()) title = group("<h1[^>]*>([^<]+)</h1>", html, 1);
            title = cleanHtml(title);

            String pic = group("data-src=\"([^\"]+)\"", html, 1);
            if (pic.isEmpty()) pic = group("<img[^>]*class=\"[^\"]*lazyload[^\"]*\"[^>]*src=\"([^\"]+)\"", html, 1);
            pic = fixUrl(pic);

            String content = group("<div[^>]*class=\"[^\"]*vod_content[^\"]*\"[^>]*>([\\s\\S]*?)</div>", html, 1);
            content = cleanHtml(content);

            String year = group("<a[^>]*href=\"/show/[^\"]*-[^-]*-[^-]*---[^-]*-(\\d{4})\"", html, 1);
            String area = group("<a[^>]*href=\"/show/[^\"]*-([^-]+)-[^-]*---\"", html, 1);

            String remark = "更新中";
            Matcher rm = Pattern.compile("<div[^>]*class=\"[^\"]*video-info-item[^\"]*\"[^>]*>([^<]*)</div>").matcher(html);
            while (rm.find()) {
                String item = rm.group(0);
                if (item.contains("备注")) {
                    String r = group(">([^<]+)</div>$", item, 1);
                    if (!r.isEmpty()) remark = r.trim();
                }
            }

            // ===== 播放列表（方案3：split 方法） =====
            List<String> playFrom = new ArrayList<>();
            List<String> playUrls = new ArrayList<>();

            // 1. 线路名
            List<String> tabs = new ArrayList<>();
            String[] tabParts = html.split("<div class=\"play-source-tab");
            for (int i = 1; i < tabParts.length; i++) {
                String name = group(">([^<]+)</div>", tabParts[i], 1).trim();
                if (!name.isEmpty() && !name.contains("夸克") && !name.contains("网盘")) {
                    tabs.add(name);
                }
            }

            // 2. 线路内容
            List<String> contents = new ArrayList<>();
            String[] contentParts = html.split("<div class=\"play-source-content");
            for (int i = 1; i < contentParts.length; i++) {
                int contentStart = contentParts[i].indexOf('>');
                if (contentStart == -1) continue;
                int contentEnd = contentParts[i].indexOf("</div>", contentStart);
                if (contentEnd == -1) continue;
                String block = contentParts[i].substring(contentStart + 1, contentEnd);

                List<String> episodes = new ArrayList<>();
                String[] linkParts = block.split("<a");
                for (int j = 1; j < linkParts.length; j++) {
                    String href = group("href=\"([^\"]+)\"", linkParts[j], 1);
                    String name = group(">([^<]+)</a>", linkParts[j], 1).trim();
                    if (!href.isEmpty() && !name.isEmpty() && !name.contains("script")) {
                        episodes.add(name + "$" + fixUrl(href));
                    }
                }
                if (!episodes.isEmpty()) {
                    contents.add(String.join("#", episodes));
                }
            }

            // 3. 按顺序匹配
            for (int i = 0; i < tabs.size() && i < contents.size(); i++) {
                playFrom.add(tabs.get(i));
                playUrls.add(contents.get(i));
            }

            // 4. 备用
            if (playFrom.isEmpty()) {
                String playList = group("<div[^>]*class=\"[^\"]*play-list[^\"]*\"[^>]*>([\\s\\S]*?)</div>\\s*</div>", html, 1);
                if (!playList.isEmpty()) {
                    List<String> episodes = new ArrayList<>();
                    String[] linkParts = playList.split("<a");
                    for (int j = 1; j < linkParts.length; j++) {
                        String href = group("href=\"([^\"]+)\"", linkParts[j], 1);
                        String name = group(">([^<]+)</a>", linkParts[j], 1).trim();
                        if (!href.isEmpty() && !name.isEmpty() && !name.contains("script")) {
                            episodes.add(name + "$" + fixUrl(href));
                        }
                    }
                    if (!episodes.isEmpty()) {
                        playFrom.add("电影先生");
                        playUrls.add(String.join("#", episodes));
                    }
                }
            }

            JSONObject vod = new JSONObject();
            vod.put("vod_id", id);
            vod.put("vod_name", title.isEmpty() ? "电影" : title);
            vod.put("vod_pic", pic);
            vod.put("vod_year", year);
            vod.put("vod_area", area);
            vod.put("vod_remarks", remark);
            vod.put("vod_content", content);
            vod.put("vod_actor", "");
            vod.put("vod_director", "");
            vod.put("vod_play_from", playFrom.isEmpty() ? "电影先生" : String.join("$$$", playFrom));
            vod.put("vod_play_url", playUrls.isEmpty() ? fixUrl("/play/" + id + "-1-1.html") : String.join("$$$", playUrls));

            JSONArray list = new JSONArray();
            list.put(vod);
            JSONObject r = new JSONObject();
            r.put("list", list);
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("detailContent error: " + e.getMessage());
            try {
                JSONObject vod = new JSONObject();
                vod.put("vod_id", ids.get(0));
                vod.put("vod_name", "加载失败");
                vod.put("vod_play_url", "");
                JSONArray list = new JSONArray();
                list.put(vod);
                JSONObject r = new JSONObject();
                r.put("list", list);
                return r.toString();
            } catch (Exception ex) {
                return "{\"list\":[]}";
            }
        }
    }

    // ============================================================
    // searchContent
    // ============================================================
    @Override
    public String searchContent(String wd, boolean quick) {
        return searchContent(wd, quick, "1");
    }

    @Override
    public String searchContent(String wd, boolean quick, String pg) {
        try {
            int page = 1;
            try { page = Integer.parseInt(pg); } catch (Exception ignored) {}
            String url = API_HOST + "/search/" + urlEncode(wd) + "-------------.html?page=" + page;
            String html = fetchHtml(url);
            JSONArray videos = extractVideos(html);

            JSONObject r = new JSONObject();
            r.put("list", videos);
            r.put("page", page);
            return r.toString();
        } catch (Exception e) {
            return "{\"list\":[]}";
        }
    }

    // ============================================================
    // playerContent：抠 player_aaaa / m3u8 / iframe
    // ============================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            if (id != null && id.matches(".*\\.(m3u8|mp4|flv|mkv|webm|ts)(\\?.*)?$")) {
                return buildResult(0, id);
            }

            if (id != null && id.contains("/play/")) {
                String html = fetchHtml(id);
                String realUrl = null;

                // 1. player_aaaa
                Matcher pm = Pattern.compile("var\\s+player_aaaa\\s*=\\s*(\\{[\\s\\S]+?\\});").matcher(html);
                if (pm.find()) {
                    try {
                        String jsonStr = pm.group(1)
                                .replace("\\/", "/")
                                .replaceAll(",(\\s*})", "}")
                                .replaceAll("\\s+", " ");
                        JSONObject player = new JSONObject(jsonStr);
                        if (player.has("url")) {
                            realUrl = decodeUnicode(player.optString("url", "").replace("\\/", "/"));
                        }
                    } catch (Exception e) {
                        String u = group("url\\s*:\\s*[\"']([^\"']+)[\"']", pm.group(1), 1);
                        if (!u.isEmpty()) realUrl = decodeUnicode(u.replace("\\/", "/"));
                    }
                }

                // 2. "url": "xxx.m3u8"
                if (realUrl == null) {
                    String u = group("\"url\"\\s*:\\s*\"([^\"]+\\.m3u8[^\"]*)\"", html, 1);
                    if (!u.isEmpty()) realUrl = decodeUnicode(u.replace("\\/", "/"));
                }

                // 3. iframe
                if (realUrl == null) {
                    String iframe = group("<iframe[^>]*src=\"([^\"]+)\"", html, 1);
                    if (!iframe.isEmpty()) {
                        String u = group("[?&]url=([^&]+)", iframe, 1);
                        if (!u.isEmpty()) iframe = urlDecode(u);
                        if (iframe.startsWith("http")) realUrl = decodeUnicode(iframe);
                    }
                }

                // 4. 裸 m3u8
                if (realUrl == null) {
                    String u = group("(https?://[^\"'\\s<>]+\\.m3u8[^\"'\\s<>]*)", html, 1);
                    if (!u.isEmpty()) realUrl = decodeUnicode(u);
                }

                if (realUrl != null) {
                    if (realUrl.startsWith("//")) realUrl = "https:" + realUrl;
                    return buildResult(0, realUrl);
                }
            }

            // 兜底 → parse:1 嗅探
            return buildResult(1, id);
        } catch (Exception e) {
            SpiderDebug.log("playerContent error: " + e.getMessage());
            return buildResult(1, id);
        }
    }

    private String buildResult(int parse, String url) {
        try {
            JSONObject r = new JSONObject();
            r.put("parse", parse);
            r.put("url", url);
            r.put("header", new JSONObject(getHeader()));
            return r.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String urlDecode(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }
}