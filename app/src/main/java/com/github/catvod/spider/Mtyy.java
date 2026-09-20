package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.okhttp.OkHttpUtil;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Mtyy extends Spider {

    private static final String HOST = "https://www.mtyy7.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern VIDEO_LIST_PATTERN = Pattern.compile(
        "<div class=\"public-list-box[^\"]*\">.*?" +
        "<a[^>]*class=\"public-list-exp\"[^>]*href=\"([^\"]+)\"[^>]*title=\"([^\"]+)\"[^>]*>.*?" +
        "<img[^>]*class=\"lazy[^\"]*\"[^>]*data-src=\"([^\"]+)\"[^>]*>.*?" +
        "<span[^>]*class=\"public-list-prb[^\"]*\"[^>]*>([^<]*)</span>", Pattern.DOTALL);
    private static final Pattern SEARCH_LIST_PATTERN = Pattern.compile(
        "<a[^>]*href=\"(/voddetail/[^\"]+)\"[^>]*title=\"([^\"]+)\"[^>]*>.*?" +
        "<img[^>]*(?:data-src|src)=\"([^\"]+)\"[^>]*>.*?" +
        "<span[^>]*>([^<]*)</span>", Pattern.DOTALL);
    private static final Pattern PLAY_LINK_PATTERN = Pattern.compile(
        "<a[^>]*href=\"(/vodplay/(\\d+)-(\\d+)-(\\d+)\\.html)\"[^>]*>([^<]+)</a>", Pattern.DOTALL);
    private static final Pattern DATA_FORM_PATTERN = Pattern.compile(
        "data-form=\"([^\"]+)\"[^>]*>[\\s\\S]*?&nbsp;([^<]+?)(?:<span|</a>)", Pattern.DOTALL);
    private static final Pattern TAGS_SPAN_PATTERN = Pattern.compile("<span[^>]*>([^<]+)</span>");
    private static final Pattern INFO_SPAN_PATTERN = Pattern.compile("<span[^>]*>([^<]*)</span>");
    private static final Pattern M3U8_PATTERN = Pattern.compile("(https?://[^\\s\"'<>]+?\\.m3u8[^\\s\"'<>]*)");
    private static final Pattern JSON_URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_FROM_PATTERN = Pattern.compile("\"from\"\\s*:\\s*\"([^\"]+)\"");

    private Map<String, String> getHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Referer", HOST + "/");
        return h;
    }

    private String fetch(String url) {
        try {
            Request request = new Request.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", HOST + "/")
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
            SpiderDebug.log("fetch error: " + e.getMessage());
            return "";
        }
    }

    // ★ 需要 POST 请求给 Mtyy 的 playerContent 用（art.php）
    private String fetchPost(String url, Map<String, String> extraHeaders) {
        try {
            RequestBody body = RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), "");
            Request.Builder builder = new Request.Builder()
                    .addHeader("User-Agent", UA)
                    .post(body)
                    .url(url);
            if (extraHeaders != null) for (Map.Entry<String, String> e : extraHeaders.entrySet()) builder.addHeader(e.getKey(), e.getValue());
            Request request = builder.build();
            OkHttpClient client = OkHttpUtil.defaultClient();
            Response response = client.newCall(request).execute();
            if (response.body() == null) return "";
            byte[] bytes = response.body().bytes();
            response.close();
            return new String(bytes, "utf-8");
        } catch (Exception e) {
            SpiderDebug.log("fetchPost error: " + e.getMessage());
            return "";
        }
    }

    private String find(Pattern pattern, String html) {
        if (TextUtils.isEmpty(html)) return "";
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? (matcher.group(1) == null ? "" : matcher.group(1)) : "";
    }

    private String group(String regex, String text, int g) {
        if (text == null) return "";
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        return m.find() ? (m.group(g) == null ? "" : m.group(g)) : "";
    }

    private String cleanText(String s) {
        if (s == null) return "";
        s = s.replace("&nbsp;", " ").replace("\n", " ").replace("\r", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        url = url.trim();
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return HOST + url;
        return HOST + "/" + url;
    }

    private String urlEncode(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    private JSONArray parseVideoList(String html) {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;
        Matcher m = VIDEO_LIST_PATTERN.matcher(html);
        while (m.find()) {
            try {
                String href = fixUrl(m.group(1));
                String name = cleanText(m.group(2));
                String pic = fixUrl(m.group(3));
                String remark = cleanText(m.group(4));
                if (!name.isEmpty()) {
                    JSONObject o = new JSONObject();
                    o.put("vod_id", href);
                    o.put("vod_name", name);
                    o.put("vod_pic", pic);
                    o.put("vod_remarks", remark);
                    list.put(o);
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    private JSONArray parseSearchList(String html) {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;
        Matcher m = SEARCH_LIST_PATTERN.matcher(html);
        while (m.find()) {
            try {
                String href = fixUrl(m.group(1));
                String name = cleanText(m.group(2));
                String pic = fixUrl(m.group(3));
                String remark = cleanText(m.group(4));
                if (!name.isEmpty()) {
                    JSONObject o = new JSONObject();
                    o.put("vod_id", href);
                    o.put("vod_name", name);
                    o.put("vod_pic", pic);
                    o.put("vod_remarks", remark);
                    list.put(o);
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    // ★ filter 改成手拼
    private JSONObject filter(String key, String name, String[][] values) {
        try {
            JSONArray arr = new JSONArray();
            for (String[] kv : values) {
                JSONObject o = new JSONObject();
                o.put("n", kv[0]); o.put("v", kv[1]);
                arr.put(o);
            }
            JSONObject g = new JSONObject();
            g.put("key", key); g.put("name", name); g.put("value", arr);
            return g;
        } catch (Exception e) { return new JSONObject(); }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();
            String[][] cfg = {{"1", "电影"}, {"2", "电视剧"}, {"3", "综艺"}, {"4", "动漫"}};
            for (String[] c : cfg) {
                JSONObject o = new JSONObject();
                o.put("type_id", c[0]); o.put("type_name", c[1]);
                classes.put(o);
            }
            result.put("class", classes);

            JSONObject filters = new JSONObject();

            JSONArray f1 = new JSONArray();
            f1.put(filter("class", "类型", new String[][]{{"全部", ""}, {"动作", "动作"}, {"喜剧", "喜剧"}, {"爱情", "爱情"}, {"科幻", "科幻"}, {"恐怖", "恐怖"}, {"剧情", "剧情"}, {"战争", "战争"}, {"动画", "动画"}, {"悬疑", "悬疑"}, {"犯罪", "犯罪"}, {"奇幻", "奇幻"}, {"冒险", "冒险"}, {"纪录", "纪录"}}));
            f1.put(filter("area", "地区", new String[][]{{"全部", ""}, {"中国大陆", "中国大陆"}, {"香港", "香港"}, {"台湾", "台湾"}, {"美国", "美国"}, {"日本", "日本"}, {"韩国", "韩国"}, {"英国", "英国"}, {"法国", "法国"}, {"印度", "印度"}, {"泰国", "泰国"}}));
            f1.put(filter("year", "年份", new String[][]{{"全部", ""}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"}, {"2023", "2023"}, {"2022", "2022"}, {"2021", "2021"}, {"2020", "2020"}}));
            f1.put(filter("by", "排序", new String[][]{{"最热", "hits_week"}, {"最新", "time"}, {"高分", "score"}}));
            filters.put("1", f1);

            JSONArray f2 = new JSONArray();
            f2.put(filter("class", "类型", new String[][]{{"全部", ""}, {"国产剧", "国产剧"}, {"港剧", "港剧"}, {"台剧", "台剧"}, {"日剧", "日剧"}, {"韩剧", "韩剧"}, {"美剧", "美剧"}, {"英剧", "英剧"}, {"泰剧", "泰剧"}, {"海外剧", "海外剧"}}));
            f2.put(filter("area", "地区", new String[][]{{"全部", ""}, {"中国大陆", "中国大陆"}, {"香港", "香港"}, {"台湾", "台湾"}, {"日本", "日本"}, {"韩国", "韩国"}, {"美国", "美国"}, {"英国", "英国"}}));
            f2.put(filter("year", "年份", new String[][]{{"全部", ""}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"}, {"2023", "2023"}, {"2022", "2022"}}));
            f2.put(filter("by", "排序", new String[][]{{"最热", "hits_week"}, {"最新", "time"}, {"高分", "score"}}));
            filters.put("2", f2);

            JSONArray f3 = new JSONArray();
            f3.put(filter("year", "年份", new String[][]{{"全部", ""}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"}, {"2023", "2023"}}));
            f3.put(filter("by", "排序", new String[][]{{"最热", "hits_week"}, {"最新", "time"}, {"高分", "score"}}));
            filters.put("3", f3);

            JSONArray f4 = new JSONArray();
            f4.put(filter("area", "地区", new String[][]{{"全部", ""}, {"中国大陆", "中国大陆"}, {"日本", "日本"}, {"美国", "美国"}}));
            f4.put(filter("year", "年份", new String[][]{{"全部", ""}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"}, {"2023", "2023"}}));
            f4.put(filter("by", "排序", new String[][]{{"最热", "hits_week"}, {"最新", "time"}, {"高分", "score"}}));
            filters.put("4", f4);

            result.put("filters", filters);

            String html = fetch(HOST + "/");
            JSONArray list = parseVideoList(html);
            result.put("list", list);

            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("homeContent error: " + e.getMessage());
            return "";
        }
    }

    @Override
    public String homeVideoContent() throws Exception { return "{}"; }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = 1;
            try { page = Integer.parseInt(pg); } catch (Exception ignored) {}

            String classVal = extend != null && extend.get("class") != null ? extend.get("class") : "";
            String areaVal  = extend != null && extend.get("area") != null ? extend.get("area") : "";
            String yearVal  = extend != null && extend.get("year") != null ? extend.get("year") : "";
            String byVal    = extend != null && extend.get("by") != null ? extend.get("by") : "hits_week";
            if (byVal.isEmpty()) byVal = "hits_week";

            if ("中国香港".equals(areaVal)) areaVal = "香港";
            if ("中国台湾".equals(areaVal)) areaVal = "台湾";

            boolean hasFilter = !classVal.isEmpty() || !areaVal.isEmpty() || !yearVal.isEmpty() || !"hits_week".equals(byVal);
            JSONArray list = new JSONArray();
            String html;

            if (hasFilter) {
                String[] parts = {tid, areaVal, byVal, classVal, "", "", yearVal, "", "", "", ""};
                String showUrl = HOST + "/vodshow/" + String.join("-", parts) + ".html";
                html = fetch(showUrl);
                list = parseVideoList(html);
            }

            if (list.length() == 0) {
                String url;
                if (page == 1) url = HOST + "/vodtype/" + tid + ".html";
                else url = HOST + "/vodtype/" + tid + "-" + page + ".html";
                html = fetch(url);
                list = parseVideoList(html);
            }

            JSONObject r = new JSONObject();
            r.put("page", page);
            r.put("pagecount", 9999);
            r.put("limit", 90);
            r.put("total", 999999);
            r.put("list", list);
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("categoryContent error: " + e.getMessage());
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String vid = ids.get(0);
            String url = vid.startsWith("http") ? vid : (vid.startsWith("/") ? HOST + vid : HOST + "/" + vid);
            String html = fetch(url);
            if (TextUtils.isEmpty(html)) return "{\"list\":[]}";

            JSONObject vod = new JSONObject();
            vod.put("vod_id", vid);

            String title = group("<div class=\"this-desc-title\"[^>]*>([^<]+)</div>", html, 1);
            vod.put("vod_name", cleanText(title));

            String pic = group("style=\"background-image:\\s*url\\('([^']+)'\\)\"", html, 1);
            if (pic.isEmpty()) pic = group("<img[^>]*class=\"[^\"]*this-pic[^\"]*\"[^>]*src=\"([^\"]+)\"", html, 1);
            if (pic.isEmpty()) pic = group("data-src=\"([^\"]+)\"[^>]*class=\"[^\"]*this-pic[^\"]*\"", html, 1);
            vod.put("vod_pic", pic);

            String actor = group("<strong class=\"r6\">演员[：:]</strong>(.*?)</div>", html, 1);
            vod.put("vod_actor", cleanText(actor.replaceAll("<[^>]+>", "")));

            String director = group("<strong class=\"r6\">导演[：:]</strong>(.*?)</div>", html, 1);
            vod.put("vod_director", cleanText(director.replaceAll("<[^>]+>", "")));

            String typeBlock = group("<div class=\"this-desc-tags\"[^>]*>(.*?)</div>", html, 1);
            if (!typeBlock.isEmpty()) {
                List<String> tags = new ArrayList<>();
                Matcher tm = TAGS_SPAN_PATTERN.matcher(typeBlock);
                while (tm.find()) tags.add(tm.group(1).trim());
                vod.put("type_name", String.join(" ", tags));
            }

            String content = group("<div id=\"height_limit\"[^>]*>.*?<strong class=\"r6\">描述[：:]</strong>(.*?)</div>", html, 1);
            if (content.isEmpty()) content = group("<strong class=\"r6\">描述[：:]</strong>(.*?)</div>", html, 1);
            if (content.isEmpty()) content = group("<div class=\"this-desc-text\"[^>]*>(.*?)</div>", html, 1);
            vod.put("vod_content", cleanText(content.replaceAll("<[^>]+>", "")));

            String labelsBlock = group("<div class=\"this-desc-labels flex\"[^>]*>(.*?)</div>", html, 1);
            if (!labelsBlock.isEmpty()) {
                String year = group("<i[^>]*>年份</i>([^<]+)</span>", labelsBlock, 1);
                vod.put("vod_year", cleanText(year));
            }

            String infoBlock = group("<div class=\"this-desc-info\"[^>]*>(.*?)</div>", html, 1);
            if (!infoBlock.isEmpty()) {
                List<String> spans = new ArrayList<>();
                Matcher sm = INFO_SPAN_PATTERN.matcher(infoBlock);
                while (sm.find()) {
                    String s = cleanText(sm.group(1));
                    if (!s.isEmpty() && !s.matches("^[0-9.]+$")) spans.add(s);
                }
                String status = "", area = "";
                for (String span : spans) {
                    if (span.matches(".*[集期].*|.*完结.*|.*更新.*|.*连载.*")) status = span;
                    else if (Arrays.asList("中国大陆", "香港", "台湾", "美国", "日本", "韩国", "英国", "法国", "泰国").contains(span)) area = span;
                }
                if (area.isEmpty() && !spans.isEmpty()) area = spans.get(0);
                if (status.isEmpty() && spans.size() >= 3) status = spans.get(2);
                vod.put("vod_area", area);
                vod.put("vod_remarks", status);
            }

            // 播放列表（方案 A）
            Map<String, List<String>> sidGroups = new LinkedHashMap<>();
            Matcher allM = PLAY_LINK_PATTERN.matcher(html);
            while (allM.find()) {
                String fullPath = allM.group(1);
                String sid = allM.group(3);
                String epName = cleanText(allM.group(5));
                if (!sidGroups.containsKey(sid)) sidGroups.put(sid, new ArrayList<>());
                sidGroups.get(sid).add(epName + "$" + HOST + fullPath);
            }

            if (sidGroups.isEmpty()) {
                vod.put("vod_play_from", "麦田影院");
                vod.put("vod_play_url", "");
                JSONArray arr = new JSONArray();
                arr.put(vod);
                JSONObject r = new JSONObject();
                r.put("list", arr);
                return r.toString();
            }

            List<String> sidKeys = new ArrayList<>(sidGroups.keySet());
            try { sidKeys.sort((a, b) -> Integer.parseInt(a) - Integer.parseInt(b)); } catch (Exception ignored) {}

            String detailId = vid.replaceAll(".*?(\\d+)\\.html.*", "$1");
            if (detailId.isEmpty()) detailId = vid.replaceAll("\\D+", "");

            Map<String, String> formToName = new HashMap<>();
            List<String> ktabs = new ArrayList<>();
            List<String> klists = new ArrayList<>();

            for (String sid : sidKeys) {
                List<String> eps = sidGroups.get(sid);
                if (eps.isEmpty()) continue;

                String testUrl = HOST + "/vodplay/" + detailId + "-" + sid + "-1.html";
                String testHtml = fetch(testUrl);
                String from = "";

                if (!TextUtils.isEmpty(testHtml)) {
                    String testJson = group("var\\s+player_data\\s*=\\s*(\\{.*?\\})\\s*<", testHtml, 1);
                    if (TextUtils.isEmpty(testJson)) testJson = group("player_aaaa\\s*=\\s*(\\{.*?\\})\\s*</script>", testHtml, 1);
                    if (!TextUtils.isEmpty(testJson)) {
                        try { from = new JSONObject(testJson).optString("from", ""); } catch (Exception ignored) {}
                    }
                    if (formToName.isEmpty()) {
                        Matcher formM = DATA_FORM_PATTERN.matcher(testHtml);
                        while (formM.find()) {
                            String form = formM.group(1).trim();
                            String name = cleanText(formM.group(2));
                            if (!form.isEmpty() && !name.isEmpty()) formToName.put(form, name);
                        }
                    }
                }

                String name = formToName.containsKey(from) ? formToName.get(from) : ("播放源" + sid);
                ktabs.add(name);
                klists.add(String.join("#", eps));
            }

            vod.put("vod_play_from", ktabs.isEmpty() ? "麦田影院" : String.join("$$$", ktabs));
            vod.put("vod_play_url", klists.isEmpty() ? "" : String.join("$$$", klists));

            JSONArray arr = new JSONArray();
            arr.put(vod);
            JSONObject r = new JSONObject();
            r.put("list", arr);
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("detailContent error: " + e.getMessage());
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}

        String encoded = urlEncode(key);
        String url = HOST + "/vodsearch/-------------.html?wd=" + encoded + "&page=" + page;
        String html = fetch(url);
        JSONArray list = parseVideoList(html);
        if (list.length() == 0) list = parseSearchList(html);

        JSONObject r = new JSONObject();
        r.put("list", list);
        return r.toString();
    }

    // ★ playerContent 一字未改
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = id.startsWith("http") ? id : (id.startsWith("/") ? HOST + id : HOST + "/" + id);
        String html = fetch(playUrl);
        if (TextUtils.isEmpty(html)) return buildResult("");

        String json = group("var\\s+player_data\\s*=\\s*(\\{.*?\\})\\s*<", html, 1);
        if (TextUtils.isEmpty(json)) json = group("player_aaaa\\s*=\\s*(\\{.*?\\})\\s*</script>", html, 1);

        String url = "";
        String from = "";

        if (!TextUtils.isEmpty(json)) {
            try {
                JSONObject player = new JSONObject(json);
                url = player.optString("url", "");
                from = player.optString("from", "");
            } catch (Exception e) {
                url = find(JSON_URL_PATTERN, json);
                from = find(JSON_FROM_PATTERN, json);
            }
        }

        url = url.replace("\\/", "/");
        if (url.startsWith("//")) url = "https:" + url;
        if (url.startsWith("/")) url = HOST + url;

        if (!TextUtils.isEmpty(url) && url.matches(".*\\.(m3u8|mp4|flv|mkv|webm|ts)(\\?.*)?$")) {
            return buildResult(url);
        }

        if (!TextUtils.isEmpty(url)) {
            String api1 = HOST + "/static/player/art.php?get_signed_url=1&url=" + urlEncode(url);
            Map<String, String> h1 = new HashMap<>();
            h1.put("User-Agent", UA);
            h1.put("X-Requested-With", "XMLHttpRequest");
            h1.put("Referer", playUrl);

            String resp1 = "";
            try {
                Request request = new Request.Builder()
                        .addHeader("User-Agent", UA)
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .addHeader("Referer", playUrl)
                        .get()
                        .url(api1)
                        .build();
                OkHttpClient client = OkHttpUtil.defaultClient();
                Response response = client.newCall(request).execute();
                if (response.body() != null) {
                    resp1 = new String(response.body().bytes(), "utf-8");
                    response.close();
                }
            } catch (Exception e) { SpiderDebug.log("get_signed_url error: " + e.getMessage()); }

            String signedUrl = "";
            try { signedUrl = new JSONObject(resp1).optString("signed_url", ""); } catch (Exception ignored) {}

            if (!TextUtils.isEmpty(signedUrl)) {
                String api2 = HOST + "/static/player/art.php" + signedUrl;
                String resp2 = "";
                try {
                    Request request = new Request.Builder()
                            .addHeader("User-Agent", UA)
                            .addHeader("X-Requested-With", "XMLHttpRequest")
                            .addHeader("Referer", playUrl)
                            .get()
                            .url(api2)
                            .build();
                    OkHttpClient client = OkHttpUtil.defaultClient();
                    Response response = client.newCall(request).execute();
                    if (response.body() != null) {
                        resp2 = new String(response.body().bytes(), "utf-8");
                        response.close();
                    }
                } catch (Exception e) { SpiderDebug.log("signed_url error: " + e.getMessage()); }

                try {
                    JSONObject j2 = new JSONObject(resp2);
                    String jmurl = j2.optString("jmurl", "");
                    if (TextUtils.isEmpty(jmurl)) jmurl = j2.optString("url", "");
                    if (TextUtils.isEmpty(jmurl)) jmurl = j2.optString("signedUrl", "");
                    if (!TextUtils.isEmpty(jmurl)) {
                        jmurl = jmurl.replace("\\/", "/");
                        return buildResult(jmurl);
                    }
                } catch (Exception e) {
                    String m3u8 = find(M3U8_PATTERN, resp2);
                    if (!TextUtils.isEmpty(m3u8)) return buildResult(m3u8);
                }
            }
        }

        String m3u8 = find(M3U8_PATTERN, html);
        if (!TextUtils.isEmpty(m3u8)) return buildResult(m3u8);

        return buildResult("");
    }

    private String buildResult(String url) {
        try {
            JSONObject r = new JSONObject();
            r.put("parse", 0);
            r.put("url", url);
            r.put("header", new JSONObject(getHeaders()));
            return r.toString();
        } catch (Exception e) { return ""; }
    }
}