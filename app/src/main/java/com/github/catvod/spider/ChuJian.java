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

public class ChuJian extends Spider {

    private static final String API_HOST = "https://cjysw.cc";
    private static final String UA = "Mozilla/5.0 (Linux; Android 13; SM-G9910) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final Pattern CARD_PATTERN = Pattern.compile(
        "<a[^>]*href=\"([^\"]+)\"[^>]*class=\"[^\"]*module-poster-item[^\"]*\"[^>]*>([\\s\\S]*?)</a>");
    private static final Pattern PAGE_LINK_PATTERN = Pattern.compile(
        "<a[^>]*class=\"[^\"]*page-link[^\"]*\"[^>]*>(\\d+)</a>");
    private static final Pattern INFO_ITEM_PATTERN = Pattern.compile(
        "<div[^>]*class=\"[^\"]*module-info-item[^\"]*\"[^>]*>[\\s\\S]*?" +
        "<span[^>]*class=\"[^\"]*module-info-item-title[^\"]*\"[^>]*>([^<]*)</span>[\\s\\S]*?" +
        "<div[^>]*class=\"[^\"]*module-info-item-content[^\"]*\"[^>]*>([\\s\\S]*?)</div>");
    private static final Pattern TAB_NAME_PATTERN = Pattern.compile(
        "<div[^>]*class=\"[^\"]*module-tab-item[^\"]*\"[^>]*>[\\s\\S]*?<span>([^<]+)</span>");
    private static final Pattern PLAY_BLOCK_PATTERN = Pattern.compile(
        "<div[^>]*class=\"[^\"]*module-play-list[^\"]*\"[^>]*>([\\s\\S]*?)</div>\\s*</div>\\s*</div>");
    private static final Pattern PLAY_EP_PATTERN = Pattern.compile(
        "<a[^>]*class=\"[^\"]*module-play-list-link[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>[\\s\\S]*?<span>([^<]+)</span>");
    private static final Pattern PLAYER_AAAA_PATTERN = Pattern.compile(
        "var\\s+player_aaaa\\s*=\\s*(\\{[\\s\\S]*?\\})\\s*</script>");
    private static final Pattern JXAPI_SRC_PATTERN = Pattern.compile("src=\"([^\"]*playerconfig\\.js[^\"]*)\"");
    private static final Pattern JXAPI_PARSE_PATTERN = Pattern.compile("\"parse\"\\s*:\\s*\"([^\"]*jxapi[^\"]*)\"");

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        h.put("Referer", API_HOST + "/");
        return h;
    }

    private Map<String, String> m3u8Headers() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Accept", "*/*");
        return h;
    }

    private Map<String, String> jsonHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Accept", "application/json,*/*");
        h.put("Referer", API_HOST + "/");
        return h;
    }

    private String fetchHtml(String url) {
        return fetchHtml(url, headers());
    }

    private String fetchHtml(String url, Map<String, String> hs) {
        try {
            Request.Builder builder = new Request.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", API_HOST + "/")
                    .get()
                    .url(url);
            if (hs != null) for (Map.Entry<String, String> e : hs.entrySet()) builder.addHeader(e.getKey(), e.getValue());
            Request request = builder.build();
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

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http")) return url;
        if (url.startsWith("/")) return API_HOST + url;
        return API_HOST + "/" + url;
    }

    private String cleanText(String t) {
        if (t == null) return "";
        return t.replace("&nbsp;", " ").replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private void sleep(long ms) { try { Thread.sleep(ms); } catch (Exception ignored) {} }

    private String find(Pattern pattern, String html) {
        if (TextUtils.isEmpty(html)) return "";
        Matcher m = pattern.matcher(html);
        return m.find() ? (m.group(1) == null ? "" : m.group(1)) : "";
    }

    private String group(String regex, String text, int g) {
        if (text == null) return "";
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        return m.find() ? (m.group(g) == null ? "" : m.group(g)) : "";
    }

    private String getJxapiTemplate(String html) {
        Matcher cfgM = JXAPI_SRC_PATTERN.matcher(html);
        if (!cfgM.find()) return "";
        String cfgUrl = cfgM.group(1);
        if (cfgUrl.startsWith("//")) cfgUrl = "https:" + cfgUrl;
        else if (cfgUrl.startsWith("/")) cfgUrl = API_HOST + cfgUrl;
        String cfgJs = fetchHtml(cfgUrl, headers());
        if (TextUtils.isEmpty(cfgJs)) return "";
        String tpl = find(JXAPI_PARSE_PATTERN, cfgJs).replace("\\/", "/");
        if (TextUtils.isEmpty(tpl)) return "";
        if (Pattern.compile("[?&]player(&|$)").matcher(tpl).find()) tpl = tpl.replaceFirst("([?&])player(&|$)", "$1from=player$2");
        else tpl = tpl + (tpl.contains("?") ? "&" : "?") + "from=player";
        return tpl;
    }

    private JSONArray parseCards(String html) {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;
        try {
            Matcher m = CARD_PATTERN.matcher(html);
            while (m.find()) {
                String href = m.group(1);
                String body = m.group(2);
                String title = group("<div[^>]*class=\"[^\"]*module-poster-item-title[^\"]*\"[^>]*>([^<]*)</div>", body, 1).trim();
                String note  = group("<div[^>]*class=\"[^\"]*module-item-note[^\"]*\"[^>]*>([^<]*)</div>", body, 1).trim();
                String pic = group("<img[^>]*data-original=\"([^\"]+)\"", body, 1);
                if (TextUtils.isEmpty(pic)) pic = group("<img[^>]*src=\"([^\"]+)\"", body, 1);
                if (!TextUtils.isEmpty(title)) {
                    JSONObject o = new JSONObject();
                    o.put("vod_id", fixUrl(href));
                    o.put("vod_name", title);
                    o.put("vod_pic", fixUrl(pic));
                    o.put("vod_remarks", note);
                    list.put(o);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    @Override
    public void init(Context context, String extend) {
        SpiderDebug.log("ChuJian init");
    }

    // ★ homeContent：手拼 JSON
    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();
            String[][] cfg = {
                {"1","电影"},{"15","剧集"},{"30","动漫"},{"24","综艺"},
                {"63","纪录片"},{"47","短剧"},{"60","Netflix"}
            };
            for (String[] c : cfg) {
                JSONObject o = new JSONObject();
                o.put("type_id", c[0]); o.put("type_name", c[1]);
                classes.put(o);
            }
            result.put("class", classes);

            JSONObject filters = new JSONObject();
            String[][] MOVIE_CLASS = {{"", "全部"}, {"动作","动作"}, {"喜剧","喜剧"}, {"爱情","爱情"}, {"科幻","科幻"}, {"恐怖","恐怖"}, {"剧情","剧情"}, {"战争","战争"}, {"犯罪","犯罪"}, {"动画","动画"}, {"奇幻","奇幻"}, {"冒险","冒险"}, {"悬疑","悬疑"}, {"惊悚","惊悚"}, {"古装","古装"}, {"历史","历史"}};
            String[][] TV_CLASS = {{"", "全部"}, {"国产剧","国产剧"}, {"港剧","港剧"}, {"台剧","台剧"}, {"日剧","日剧"}, {"韩剧","韩剧"}, {"美剧","美剧"}, {"英剧","英剧"}, {"泰剧","泰剧"}};
            String[][] AREA_LIST = {{"", "全部"}, {"大陆","大陆"}, {"香港","香港"}, {"台湾","台湾"}, {"美国","美国"}, {"日本","日本"}, {"韩国","韩国"}, {"英国","英国"}, {"法国","法国"}, {"德国","德国"}, {"泰国","泰国"}, {"印度","印度"}, {"其他","其他"}};
            String[][] YEAR_LIST = {{"", "全部"}, {"2026","2026"}, {"2025","2025"}, {"2024","2024"}, {"2023","2023"}, {"2022","2022"}, {"2021","2021"}, {"2020","2020"}};
            String[][] YEAR_SHORT = {{"", "全部"}, {"2026","2026"}, {"2025","2025"}, {"2024","2024"}};
            String[][] YEAR_SHORT2 = {{"", "全部"}, {"2026","2026"}, {"2025","2025"}};
            String[][] AREA_ANIME = {{"", "全部"}, {"大陆","大陆"}, {"日本","日本"}, {"美国","美国"}};

            JSONArray f1 = new JSONArray();
            f1.put(filterGroup("class", "类型", arrOf(MOVIE_CLASS)));
            f1.put(filterGroup("area", "地区", arrOf(AREA_LIST)));
            f1.put(filterGroup("year", "年份", arrOf(YEAR_LIST)));
            filters.put("1", f1);

            JSONArray f15 = new JSONArray();
            f15.put(filterGroup("class", "类型", arrOf(TV_CLASS)));
            f15.put(filterGroup("area", "地区", arrOf(AREA_LIST)));
            f15.put(filterGroup("year", "年份", arrOf(YEAR_LIST)));
            filters.put("15", f15);

            JSONArray f30 = new JSONArray();
            f30.put(filterGroup("area", "地区", arrOf(AREA_ANIME)));
            f30.put(filterGroup("year", "年份", arrOf(YEAR_SHORT)));
            filters.put("30", f30);

            for (String tid : new String[]{"24", "63"}) {
                JSONArray fa = new JSONArray();
                fa.put(filterGroup("year", "年份", arrOf(YEAR_SHORT)));
                filters.put(tid, fa);
            }
            for (String tid : new String[]{"47", "60"}) {
                JSONArray fa = new JSONArray();
                fa.put(filterGroup("year", "年份", arrOf(YEAR_SHORT2)));
                filters.put(tid, fa);
            }

            result.put("filters", filters);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("homeContent error: " + e.getMessage());
            return "";
        }
    }

    private JSONArray arrOf(String[][] data) throws Exception {
        JSONArray a = new JSONArray();
        for (String[] kv : data) {
            JSONObject o = new JSONObject();
            o.put("v", kv[0]); o.put("n", kv[1]);
            a.put(o);
        }
        return a;
    }

    private JSONObject filterGroup(String key, String name, JSONArray values) throws Exception {
        JSONObject g = new JSONObject();
        g.put("key", key); g.put("name", name); g.put("value", values);
        return g;
    }

    @Override
    public String homeVideoContent() {
        try {
            String html = fetchHtml(API_HOST + "/");
            JSONArray arr = parseCards(html);
            JSONObject r = new JSONObject();
            r.put("list", arr);
            return r.toString();
        } catch (Exception e) {
            return "{\"list\":[]}";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = 1;
            try { page = Integer.parseInt(pg); } catch (Exception ignored) {}
            String cls  = (extend != null && extend.get("class") != null) ? extend.get("class") : "";
            String area = (extend != null && extend.get("area")  != null) ? extend.get("area")  : "";
            String year = (extend != null && extend.get("year")  != null) ? extend.get("year")  : "";

            StringBuilder path = new StringBuilder("/index.php/vod/show/id/").append(tid);
            if (!TextUtils.isEmpty(cls))  path.append("/class/").append(cls);
            if (!TextUtils.isEmpty(area)) path.append("/area/").append(area);
            if (!TextUtils.isEmpty(year)) path.append("/year/").append(year);
            if (page > 1) path.append("/page/").append(page);
            path.append(".html");

            String html = fetchHtml(API_HOST + path.toString());
            JSONArray list = parseCards(html);

            int pagecount = page + 1;
            List<Integer> pages = new ArrayList<>();
            Matcher pm = PAGE_LINK_PATTERN.matcher(html);
            while (pm.find()) {
                try { pages.add(Integer.parseInt(pm.group(1))); } catch (Exception ignored) {}
            }
            for (Integer p : pages) if (p > pagecount) pagecount = p;

            JSONObject r = new JSONObject();
            r.put("page", page);
            r.put("list", list);
            r.put("pagecount", pagecount);
            r.put("limit", 90);
            r.put("total", 999999);
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("category error: " + e.getMessage());
            return "{\"page\":1,\"list\":[],\"pagecount\":1,\"limit\":90,\"total\":0}";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = id.startsWith("http") ? id : API_HOST + id;
            String html = fetchHtml(url);
            if (TextUtils.isEmpty(html)) return "{\"list\":[]}";

            JSONObject info = new JSONObject();
            info.put("vod_id", id);
            info.put("vod_name", ""); info.put("vod_pic", ""); info.put("vod_content", "");
            info.put("vod_actor", ""); info.put("vod_director", ""); info.put("vod_year", ""); info.put("vod_remarks", "");
            info.put("vod_play_from", ""); info.put("vod_play_url", "");

            String name = group("<h1[^>]*>([^<]+)</h1>", html, 1).trim();
            if (!TextUtils.isEmpty(name)) info.put("vod_name", name);

            String pic = group("<img[^>]*class=\"[^\"]*lazy[^\"]*\"[^>]*data-original=\"([^\"]+)\"", html, 1);
            if (TextUtils.isEmpty(pic)) pic = group("<img[^>]*class=\"[^\"]*lazy[^\"]*\"[^>]*src=\"([^\"]+)\"", html, 1);
            if (TextUtils.isEmpty(pic)) pic = group("<img[^>]*data-original=\"([^\"]+)\"", html, 1);
            if (!TextUtils.isEmpty(pic)) info.put("vod_pic", fixUrl(pic));

            String desc = group("<div[^>]*class=\"[^\"]*module-info-introduction-content[^\"]*\"[^>]*>([\\s\\S]*?)</div>", html, 1);
            if (!TextUtils.isEmpty(desc)) info.put("vod_content", cleanText(desc));

            Matcher iM = INFO_ITEM_PATTERN.matcher(html);
            while (iM.find()) {
                String title = iM.group(1).replace("：", "").replace(":", "").trim();
                String value = cleanText(iM.group(2));
                if (title.contains("导演")) info.put("vod_director", value);
                else if (title.contains("主演")) info.put("vod_actor", value);
                else if (title.contains("年份")) info.put("vod_year", value);
                else if (title.contains("集数") || title.contains("更新")) info.put("vod_remarks", value);
            }

            List<String> tabNames = new ArrayList<>();
            Matcher tm = TAB_NAME_PATTERN.matcher(html);
            while (tm.find()) {
                String n = tm.group(1).trim();
                if (!TextUtils.isEmpty(n) && !tabNames.contains(n)) tabNames.add(n);
            }

            List<String> playFrom = new ArrayList<>();
            List<String> playUrl  = new ArrayList<>();

            Matcher bm = PLAY_BLOCK_PATTERN.matcher(html);
            int idx = 0;
            while (bm.find()) {
                String body = bm.group(1);
                List<String> eps = new ArrayList<>();
                Matcher am = PLAY_EP_PATTERN.matcher(body);
                while (am.find()) {
                    String epUrl  = am.group(1);
                    String epName = cleanText(am.group(2));
                    if (!TextUtils.isEmpty(epName)) eps.add(epName + "$" + fixUrl(epUrl));
                }
                if (!eps.isEmpty()) {
                    String fromName = idx < tabNames.size() ? tabNames.get(idx) : ("线路" + (idx + 1));
                    playFrom.add(fromName);
                    playUrl.add(TextUtils.join("#", eps));
                    idx++;
                }
            }

            if (playFrom.isEmpty()) {
                List<String> eps = new ArrayList<>();
                Matcher am = PLAY_EP_PATTERN.matcher(html);
                while (am.find()) {
                    String epUrl  = am.group(1);
                    String epName = cleanText(am.group(2));
                    if (!TextUtils.isEmpty(epName)) eps.add(epName + "$" + fixUrl(epUrl));
                }
                if (!eps.isEmpty()) {
                    playFrom.add("默认");
                    playUrl.add(TextUtils.join("#", eps));
                }
            }

            info.put("vod_play_from", TextUtils.join("$$$", playFrom));
            info.put("vod_play_url",  TextUtils.join("$$$", playUrl));

            JSONArray arr = new JSONArray();
            arr.put(info);
            JSONObject r = new JSONObject();
            r.put("list", arr);
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("detail error: " + e.getMessage());
            return "{\"list\":[]}";
        }
    }

    @Override
    public String searchContent(String wd, boolean quick) {
        return searchContent(wd, quick, "1");
    }

    @Override
    public String searchContent(String wd, boolean quick, String pg) {
        try {
            int page = 1;
            try { page = Integer.parseInt(pg); } catch (Exception ignored) {}
            String enc = URLEncoder.encode(wd == null ? "" : wd, "UTF-8");
            String url = API_HOST + "/index.php/vod/search.html?wd=" + enc + "&page=" + page;
            String html = fetchHtml(url);

            JSONArray list = new JSONArray();
            Matcher m = Pattern.compile("<div[^>]*class=\"[^\"]*module-card-item[^\"]*\"[^>]*>([\\s\\S]*?)</div>\\s*</div>\\s*</div>").matcher(html);
            while (m.find()) {
                String body = m.group(1);
                String href = group("<a[^>]*href=\"([^\"]+)\"[^>]*class=\"[^\"]*module-card-item-poster[^\"]*\"", body, 1);
                if (TextUtils.isEmpty(href)) href = group("<a[^>]*href=\"([^\"]+)\"[^>]*>", body, 1);
                String title = group("<strong>([^<]+)</strong>", body, 1);
                if (TextUtils.isEmpty(title)) title = group("module-card-item-title[^>]*>[\\s\\S]*?<a[^>]*>([^<]+)</a>", body, 1);
                String note = group("<div[^>]*class=\"[^\"]*module-item-note[^\"]*\"[^>]*>([^<]*)</div>", body, 1);
                String pic = group("<img[^>]*data-original=\"([^\"]+)\"", body, 1);
                if (TextUtils.isEmpty(pic)) pic = group("<img[^>]*src=\"([^\"]+)\"", body, 1);

                if (!TextUtils.isEmpty(title)) {
                    JSONObject o = new JSONObject();
                    o.put("vod_id", fixUrl(href));
                    o.put("vod_name", title.trim());
                    o.put("vod_pic", fixUrl(pic));
                    o.put("vod_remarks", note.trim());
                    list.put(o);
                }
            }
            JSONObject r = new JSONObject();
            r.put("list", list);
            r.put("page", page);
            r.put("pagecount", 9999);
            return r.toString();
        } catch (Exception e) {
            return "{\"list\":[]}";
        }
    }

    // ★ playerContent 一字未改
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            if (!TextUtils.isEmpty(id) && id.matches("(?i).*\\.(m3u8|mp4|flv|mkv|webm|ts)(\\?.*)?$")) return buildPlayer(id);

            String pageUrl = id.startsWith("http") ? id : API_HOST + id;
            String html = fetchHtml(pageUrl);
            if (TextUtils.isEmpty(html) || !html.contains("player_aaaa")) { sleep(400); html = fetchHtml(pageUrl); }
            if (TextUtils.isEmpty(html)) return buildEmpty();

            String realUrl = "";
            Matcher pm = PLAYER_AAAA_PATTERN.matcher(html);
            if (pm.find()) {
                try {
                    JSONObject p = new JSONObject(pm.group(1));
                    if (p.has("url")) {
                        realUrl = p.optString("url", "").replace("\\/", "/");
                        if (realUrl.startsWith("//")) realUrl = "https:" + realUrl;
                    }
                } catch (Exception ignored) {}
            }

            if (!TextUtils.isEmpty(realUrl) && realUrl.matches("(?i).*\\.(m3u8|mp4|flv)(\\?.*)?$")) return buildPlayer(realUrl);

            if (!TextUtils.isEmpty(realUrl)) {
                String tpl = getJxapiTemplate(html);
                if (TextUtils.isEmpty(tpl)) return buildEmpty();
                String apiUrl = tpl + URLEncoder.encode(realUrl, "UTF-8");
                String resp = fetchHtml(apiUrl, jsonHeaders());
                if (!TextUtils.isEmpty(resp)) {
                    try {
                        JSONObject j = new JSONObject(resp);
                        if (j.optInt("code", 0) == 200 && j.has("url")) return buildPlayer(j.optString("url").replace("\\/", "/"));
                    } catch (Exception ignored) {}
                }
            }
            return buildEmpty();
        } catch (Exception e) {
            SpiderDebug.log("playerContent error: " + e.getMessage());
            return buildEmpty();
        }
    }

    private String buildPlayer(String url) {
        try {
            JSONObject r = new JSONObject();
            r.put("parse", 0);
            r.put("url", url);
            r.put("header", new JSONObject(m3u8Headers()));
            return r.toString();
        } catch (Exception e) { return ""; }
    }

    private String buildEmpty() {
        try {
            JSONObject r = new JSONObject();
            r.put("parse", 0); r.put("url", ""); r.put("header", new JSONObject(m3u8Headers()));
            return r.toString();
        } catch (Exception e) { return ""; }
    }
}