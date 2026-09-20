package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
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

public class KuKe extends Spider {

    private static final String HOST = "https://www.554dy.com";

    private static final String UA = "Mozilla/5.0 (Linux; Android 13; SM-G9910) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    // ============================================================
    // ★ 静态 Pattern 常量（PiaoHua 风格）
    // ============================================================
    private static final Pattern ITEM_PATTERN = Pattern.compile(
        "<li[^>]*class=\"[^\"]*col-md-[^\"]*\"[^>]*>([\\s\\S]*?)</li>");

    private static final Pattern LINK_PATTERN = Pattern.compile(
        "<a[^>]*>([^<]+)</a>");

    private static final Pattern LAST_PAGE_PATTERN = Pattern.compile(
        "<a[^>]*href=\"[^\"]*/list/\\d+_(\\d+)\\.html[^\"]*\"[^>]*>尾页</");

    private static final Pattern NAV_LI_PATTERN = Pattern.compile(
        "<li[^>]*>[\\s\\S]*?<a[^>]*>([^<]+)</a>[\\s\\S]*?</li>");

    private static final Pattern PLAYLIST_UL_PATTERN = Pattern.compile(
        "<ul[^>]*class=\"[^\"]*stui-content__playlist[^\"]*\"[^>]*>([\\s\\S]*?)</ul>");

    private static final Pattern TAB_PANE_PATTERN = Pattern.compile(
        "<div[^>]*class=\"[^\"]*tab-pane[^\"]*\"[^>]*>([\\s\\S]*?)</div>");

    private static final Pattern EP_PATTERN = Pattern.compile(
        "<a[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>");

    private static final Pattern M3U8_FULL_PATTERN = Pattern.compile(
        "(https?://[^\\s<>\"']+\\.m3u8[^\\s<>\"']*)");

    private static final Pattern M3U8_VAR_PATTERN = Pattern.compile(
        "var\\s+now\\s*=\\s*[\"']([^\"']+)[\"']");

    private static final Pattern M3U8_JSON_PATTERN = Pattern.compile(
        "\"url\"\\s*:\\s*\"([^\"]+)\"");

    private static final Pattern M3U8_REL_PATTERN = Pattern.compile(
        "[\"'](/[^\"']+\\.m3u8[^\"']*)[\"']");

    // ============================================================
    // init
    // ============================================================
    @Override
    public void init(Context context, String extend) {
        SpiderDebug.log("Kuke init");
    }

    // ============================================================
    // ★ fetchHtml —— 改成 PiaoHua 风格
    // ============================================================
    private String fetchHtml(String url) {
        return fetchHtml(url, null);
    }

    private String fetchHtml(String url, Map<String, String> headers) {
        try {
            Request.Builder builder = new Request.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                    .addHeader("Referer", HOST + "/")
                    .get()
                    .url(url);

            // 如果传了 headers，覆盖默认的
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    builder.addHeader(e.getKey(), e.getValue());
                }
            }

            Request request = builder.build();
            OkHttpClient okHttpClient = OkHttpUtil.defaultClient();
            Response response = okHttpClient.newCall(request).execute();
            if (response.body() == null) return "";
            byte[] bytes = response.body().bytes();
            response.close();
            return new String(bytes, "utf-8");
        } catch (Exception e) {
            SpiderDebug.log("fetchHtml error: " + e.getMessage());
            return "";
        }
    }

    // ============================================================
    // 工具方法（PiaoHua 风格）
    // ============================================================
    private String find(Pattern pattern, String html) {
        if (TextUtils.isEmpty(html)) return "";
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? (matcher.group(1) == null ? "" : matcher.group(1)) : "";
    }

    private Map<String, String> getHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        h.put("Accept-Language", "zh-CN,zh;q=0.9");
        h.put("Referer", HOST + "/");
        return h;
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http")) return url;
        if (url.startsWith("/")) return HOST + url;
        return HOST + "/" + url;
    }

    private String cleanHtmlTags(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private List<String> extractLinks(String text) {
        List<String> list = new ArrayList<>();
        if (text == null) return list;
        Matcher m = LINK_PATTERN.matcher(text);
        while (m.find()) {
            String s = m.group(1).trim();
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    private String urlEncode(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    private String group(String regex, String text, int g) {
        if (text == null) return "";
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        return m.find() ? (m.group(g) == null ? "" : m.group(g)) : "";
    }

    // ============================================================
    // 动态 header（不变）
    // ============================================================
    private Map<String, String> getHeaderByUrl(String url) {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Accept", "*/*");

        String[] noRefererHosts = {
            "blbtgg.com",
            "rr.kukedy6.com"
        };

        boolean needReferer = true;
        if (url != null) {
            for (String host : noRefererHosts) {
                if (url.contains(host)) {
                    needReferer = false;
                    break;
                }
            }
        }

        if (needReferer) {
            h.put("Referer", HOST + "/");
        }

        return h;
    }

    // ============================================================
    // 中文 URL 编码（不变）
    // ============================================================
    private String encodeChineseUrl(String url) {
        if (TextUtils.isEmpty(url)) return url;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c < 128) {
                sb.append(c);
            } else {
                try {
                    sb.append(URLEncoder.encode(String.valueOf(c), "UTF-8"));
                } catch (Exception e) {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    // ============================================================
    // 加时间戳（不变）
    // ============================================================
    private String addTimestamp(String url) {
        if (TextUtils.isEmpty(url)) return url;
        if (url.contains("auth_key")) return url;
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "_t=" + System.currentTimeMillis();
    }

    // ============================================================
    // 展开二级 m3u8（不变）
    // ============================================================
    private String resolveM3u8(String m3u8Url, Map<String, String> headers) {
        try {
            String content = OkHttp.string(m3u8Url, headers);
            if (content == null) content = "";
            if (!content.contains(".ts")) {
                Matcher m = M3U8_FULL_PATTERN.matcher(content);
                if (m.find()) {
                    String u = m.group(1);
                    if (!u.equals(m3u8Url)) {
                        SpiderDebug.log("展开二级 m3u8: " + u);
                        return u;
                    }
                }
            }
            return m3u8Url;
        } catch (Exception e) {
            SpiderDebug.log("resolveM3u8 error: " + e.getMessage());
            return m3u8Url;
        }
    }

    // ============================================================
    // 提取列表（用静态 Pattern）
    // ============================================================
    private List<JSONObject> extractList(String html) {
        List<JSONObject> list = new ArrayList<>();
        if (TextUtils.isEmpty(html)) return list;
        try {
            Matcher itemsM = ITEM_PATTERN.matcher(html);

            while (itemsM.find()) {
                String item = itemsM.group(0);
                // 跳过广告
                if (item.contains("tbbrt.com") || item.contains("tu.chexin.cc")
                    || item.contains("channelCode") || item.contains("tu.cdn5.vip")) continue;

                String href = group("<a[^>]*href=\"([^\"]+)\"[^>]*>", item, 1);
                if (TextUtils.isEmpty(href) || !href.startsWith("/edu-")) continue;

                String name = group("<h4[^>]*>[\\s\\S]*?<a[^>]*>([^<]+)</a>", item, 1);
                if (TextUtils.isEmpty(name)) continue;

                String pic = group("data-original=\"([^\"]+)\"", item, 1);
                String remark = group("<span[^>]*class=\"[^\"]*pic-text[^\"]*\"[^>]*>([^<]+)</span>", item, 1);

                JSONObject o = new JSONObject();
                o.put("vod_id", href);
                o.put("vod_name", name.trim());
                o.put("vod_pic", TextUtils.isEmpty(pic) ? "" : fixUrl(pic));
                o.put("vod_remarks", remark.trim());
                list.add(o);
            }
        } catch (Exception e) {
            SpiderDebug.log("extractList error: " + e.getMessage());
        }
        return list;
    }

    // ============================================================
    // homeContent（不变）
    // ============================================================
    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();

            JSONArray classes = new JSONArray();
            String[][] cfg = {
                {"1", "电影"}, {"2", "电视剧"}, {"3", "动漫"}, {"4", "综艺"}, {"67", "伦理"}
            };
            for (String[] c : cfg) {
                JSONObject o = new JSONObject();
                o.put("type_id", c[0]);
                o.put("type_name", c[1]);
                classes.put(o);
            }
            result.put("class", classes);

            JSONObject filters = new JSONObject();
            for (String[] c : cfg) {
                JSONArray arr = new JSONArray();
                JSONObject g = new JSONObject();
                g.put("key", "order");
                g.put("name", "排序");
                JSONArray vals = new JSONArray();
                JSONObject v1 = new JSONObject(); v1.put("v", "hit"); v1.put("n", "人气"); vals.put(v1);
                JSONObject v2 = new JSONObject(); v2.put("v", "time"); v2.put("n", "时间"); vals.put(v2);
                g.put("value", vals);
                arr.put(g);
                filters.put(c[0], arr);
            }
            result.put("filters", filters);

            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("homeContent error: " + e.getMessage());
            return "";
        }
    }

    // ============================================================
    // homeVideoContent（不变）
    // ============================================================
    @Override
    public String homeVideoContent() {
        try {
            String html = fetchHtml(HOST + "/");
            List<JSONObject> list = extractList(html);
            if (list.size() > 12) list = list.subList(0, 12);

            JSONArray arr = new JSONArray();
            for (JSONObject o : list) arr.put(o);
            JSONObject r = new JSONObject();
            r.put("list", arr);
            return r.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ============================================================
    // categoryContent（用静态 Pattern）
    // ============================================================
    @Override
    public String categoryContent(String tid, String pg, boolean filter,
                                  HashMap<String, String> extend) {
        try {
            int page = 1;
            try { page = Integer.parseInt(pg); } catch (Exception ignored) {}

            String order = "hit";
            if (extend != null && extend.get("order") != null) {
                order = extend.get("order");
                if (TextUtils.isEmpty(order)) order = "hit";
            }

            String url;
            if (page > 1) {
                url = HOST + "/list/" + tid + "_" + page + ".html?order=" + order;
            } else {
                url = HOST + "/list/" + tid + ".html?order=" + order;
            }

            String html = fetchHtml(url);
            List<JSONObject> list = extractList(html);

            int pagecount = 1;
            Matcher lm = LAST_PAGE_PATTERN.matcher(html);
            if (lm.find()) {
                try { pagecount = Integer.parseInt(lm.group(1)); } catch (Exception ignored) {}
            }
            if (pagecount < 1) pagecount = 1;

            JSONArray arr = new JSONArray();
            for (JSONObject o : list) arr.put(o);

            JSONObject r = new JSONObject();
            r.put("page", page);
            r.put("list", arr);
            r.put("pagecount", pagecount);
            r.put("limit", 24);
            r.put("total", pagecount * 24);
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("categoryContent error: " + e.getMessage());
            return "";
        }
    }

    // ============================================================
    // detailContent（用静态 Pattern）
    // ============================================================
    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = id.startsWith("http") ? id : (HOST + id);
            String html = fetchHtml(url);
            if (TextUtils.isEmpty(html)) {
                JSONObject r = new JSONObject();
                r.put("list", new JSONArray());
                return r.toString();
            }

            JSONObject info = new JSONObject();
            info.put("vod_id", id);
            info.put("vod_name", group("<h1[^>]*>([^<]+)</h1>", html, 1).trim());

            String pic = group("<img[^>]*class=\"[^\"]*stui-vodlist__thumb[^\"]*\"[^>]*data-original=\"([^\"]+)\"", html, 1);
            info.put("vod_pic", TextUtils.isEmpty(pic) ? "" : fixUrl(pic));

            info.put("vod_class", group("类型：</span><a[^>]*>([^<]+)</a>", html, 1).trim());
            info.put("vod_area", group("地区：</span><a[^>]*>([^<]+)</a>", html, 1).trim());
            info.put("vod_year", group("年份：</span><a[^>]*>(\\d{4})</a>", html, 1));

            // 主演
            String actorBlock = group("主演：</span>([\\s\\S]*?)</p>", html, 1);
            if (!actorBlock.isEmpty()) {
                List<String> names = extractLinks(actorBlock);
                info.put("vod_actor", names.isEmpty() ? cleanHtmlTags(actorBlock) : String.join(" / ", names));
            }

            // 导演
            String dirBlock = group("导演：</span>([\\s\\S]*?)</p>", html, 1);
            if (!dirBlock.isEmpty()) {
                List<String> names = extractLinks(dirBlock);
                info.put("vod_director", names.isEmpty() ? cleanHtmlTags(dirBlock) : String.join(" / ", names));
            }

            // 简介
            String contentMatch = group(
                "<div[^>]*class=\"[^\"]*stui-pannel_bd[^\"]*\"[^>]*>[\\s\\S]*?<div[^>]*class=\"[^\"]*col-pd[^\"]*\"[^>]*>([\\s\\S]*?)</div>",
                html, 1
            );
            if (!contentMatch.isEmpty()) {
                String c = contentMatch.replaceAll("<a[^>]*>([^<]+)</a>", "$1")
                                       .replaceAll("<[^>]+>", "")
                                       .replaceAll("\\s+", " ")
                                       .trim();
                info.put("vod_content", c);
            }

            // 线路名
            List<String> tabNames = new ArrayList<>();
            String navBlock = group("<ul[^>]*class=\"[^\"]*nav-tabs[^\"]*\"[^>]*>([\\s\\S]*?)</ul>", html, 1);
            if (!navBlock.isEmpty()) {
                Matcher tm = NAV_LI_PATTERN.matcher(navBlock);
                while (tm.find()) {
                    String name = cleanHtmlTags(tm.group(1));
                    if (!name.isEmpty() && !"首页".equals(name) && !"推荐".equals(name) && !tabNames.contains(name)) {
                        tabNames.add(name);
                    }
                }
            }

            // 播放列表
            List<String> playlists = new ArrayList<>();
            Matcher plM = PLAYLIST_UL_PATTERN.matcher(html);
            while (plM.find()) playlists.add(plM.group(1));

            if (playlists.isEmpty()) {
                String tabContent = group("<div[^>]*class=\"[^\"]*tab-content[^\"]*\"[^>]*>([\\s\\S]*?)</div>", html, 1);
                if (!tabContent.isEmpty()) {
                    Matcher paneM = TAB_PANE_PATTERN.matcher(tabContent);
                    while (paneM.find()) {
                        String ul = group("<ul[^>]*class=\"[^\"]*stui-content__playlist[^\"]*\"[^>]*>([\\s\\S]*?)</ul>", paneM.group(1), 1);
                        if (!ul.isEmpty()) playlists.add(ul);
                    }
                }
            }

            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();

            for (int i = 0; i < playlists.size(); i++) {
                String playlistHtml = playlists.get(i);
                List<String> eps = new ArrayList<>();
                Matcher epM = EP_PATTERN.matcher(playlistHtml);
                while (epM.find()) {
                    String eu = epM.group(1);
                    String en = cleanHtmlTags(epM.group(2));
                    if (en.isEmpty()) continue;
                    if (en.contains("更多视频") || en.contains("13699") || en.contains("www.")) continue;
                    eps.add(en + "$" + fixUrl(eu));
                }
                if (!eps.isEmpty()) {
                    String fromName = i < tabNames.size() ? tabNames.get(i) : ("线路" + (i + 1));
                    playFrom.add(fromName);
                    playUrl.add(String.join("#", eps));
                }
            }

            info.put("vod_play_from", String.join("$$$", playFrom));
            info.put("vod_play_url", String.join("$$$", playUrl));

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

    // ============================================================
    // searchContent（不变）
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
            String url = HOST + "/search.php?searchword=" + urlEncode(wd);
            String html = fetchHtml(url);
            List<JSONObject> list = extractList(html);

            JSONArray arr = new JSONArray();
            for (JSONObject o : list) arr.put(o);
            JSONObject r = new JSONObject();
            r.put("list", arr);
            r.put("page", page);
            r.put("pagecount", 1);
            return r.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ============================================================
    // ★★ playerContent —— 完全保留原逻辑 ★★
    // ============================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            // 已经是直链
            if (id != null && id.matches(".*\\.(m3u8|mp4|flv|mkv|webm|ts)(\\?.*)?$")) {
                String u = encodeChineseUrl(id);
                u = addTimestamp(u);
                Map<String, String> h = getHeaderByUrl(u);
                JSONObject r = new JSONObject();
                r.put("parse", 0);
                r.put("url", u);
                r.put("header", new JSONObject(h));
                return r.toString();
            }

            // ① 请求播放页
            String url = id.startsWith("http") ? id : (HOST + id);
            String html = fetchHtml(url);
            if (TextUtils.isEmpty(html)) {
                SpiderDebug.log("播放页第一次失败，重试...");
                try { Thread.sleep(500); } catch (Exception ignored) {}
                html = fetchHtml(url);
            }
            if (TextUtils.isEmpty(html)) {
                JSONObject r = new JSONObject();
                r.put("parse", 0);
                r.put("url", id);
                r.put("header", new JSONObject(getHeaders()));
                return r.toString();
            }

            // ② 直接从播放页找 m3u8
            String videoUrl = extractM3u8(html);
            if (!TextUtils.isEmpty(videoUrl) && !videoUrl.contains("content.php")) {
                SpiderDebug.log("✅ 直接提取 m3u8: " + videoUrl);
                videoUrl = encodeChineseUrl(videoUrl);
                videoUrl = resolveM3u8(videoUrl, getHeaderByUrl(videoUrl));
                videoUrl = addTimestamp(videoUrl);
                JSONObject r = new JSONObject();
                r.put("parse", 0);
                r.put("url", videoUrl);
                r.put("header", new JSONObject(getHeaderByUrl(videoUrl)));
                return r.toString();
            }

            // ③ 找 iframe
            String proxyUrl = group("<iframe[^>]*src=\"([^\"]+)\"[^>]*>", html, 1);
            if (TextUtils.isEmpty(proxyUrl)) {
                JSONObject r = new JSONObject();
                r.put("parse", 0);
                r.put("url", id);
                r.put("header", new JSONObject(getHeaders()));
                return r.toString();
            }

            proxyUrl = proxyUrl.replace("&amp;", "&");
            if (proxyUrl.startsWith("//")) proxyUrl = "https:" + proxyUrl;
            if (proxyUrl.startsWith("/")) proxyUrl = HOST + proxyUrl;

            SpiderDebug.log("代理地址: " + proxyUrl);

            // ④ 请求代理
            String proxyHtml = fetchHtml(proxyUrl);
            if (TextUtils.isEmpty(proxyHtml)) {
                try { Thread.sleep(500); } catch (Exception ignored) {}
                proxyHtml = fetchHtml(proxyUrl);
            }

            if (TextUtils.isEmpty(proxyHtml)) {
                JSONObject r = new JSONObject();
                r.put("parse", 1);
                r.put("url", proxyUrl);
                r.put("header", new JSONObject(getHeaders()));
                return r.toString();
            }

            // ⑤ 从代理 HTML 里提取 m3u8
            videoUrl = extractM3u8(proxyHtml);
            if (TextUtils.isEmpty(videoUrl)) {
                SpiderDebug.log("代理返回页面里没找到 m3u8");
                JSONObject r = new JSONObject();
                r.put("parse", 1);
                r.put("url", proxyUrl);
                r.put("header", new JSONObject(getHeaders()));
                return r.toString();
            }

            // ⑥ 编码 + 展开二级 + 加时间戳
            videoUrl = encodeChineseUrl(videoUrl);
            videoUrl = resolveM3u8(videoUrl, getHeaderByUrl(videoUrl));
            videoUrl = addTimestamp(videoUrl);

            SpiderDebug.log("✅ 最终 m3u8: " + videoUrl);
            JSONObject r = new JSONObject();
            r.put("parse", 0);
            r.put("url", videoUrl);
            r.put("header", new JSONObject(getHeaderByUrl(videoUrl)));
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("playerContent error: " + e.getMessage());
            return "";
        }
    }

    // ============================================================
    // extractM3u8（用静态 Pattern）
    // ============================================================
    private String extractM3u8(String html) {
        if (TextUtils.isEmpty(html)) return "";

        // ① 完整 m3u8 URL
        Matcher m1 = M3U8_FULL_PATTERN.matcher(html);
        if (m1.find()) {
            return m1.group(1).replaceAll("\\s", "").replace("\\/", "/");
        }

        // ② var now = "xxx.m3u8"
        Matcher m2 = M3U8_VAR_PATTERN.matcher(html);
        if (m2.find()) {
            String u = m2.group(1);
            if (u.contains(".m3u8")) return u;
        }

        // ③ "url": "xxx.m3u8"
        Matcher m3 = M3U8_JSON_PATTERN.matcher(html);
        if (m3.find()) {
            String u = m3.group(1);
            if (u.contains(".m3u8")) return u.replace("\\/", "/");
        }

        // ④ 相对路径
        Matcher m4 = M3U8_REL_PATTERN.matcher(html);
        if (m4.find()) return HOST + m4.group(1);

        return "";
    }

    // ============================================================
    // destroy（去掉 @Override）
    // ============================================================
    public void destroy() {
        SpiderDebug.log("Kuke destroy");
    }
}