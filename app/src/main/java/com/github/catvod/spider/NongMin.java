package com.github.catvod.spider;

import android.text.TextUtils;
import android.util.Base64;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NongMin extends Spider {

    private static final String DEFAULT_HOST = "https://vip.wwgz.cn:5200";
    private String host = DEFAULT_HOST;

    private static final String UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 13_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.5 Mobile/15E148 Snapchat/10.77.5.59 (like Safari/604.1)";

    private Map<String, String> headers;

    private static final Pattern LI_PATTERN = Pattern.compile("<li[^>]*>([\\s\\S]*?)</li>");
    private static final Pattern DETAIL_HREF_PATTERN = Pattern.compile("<a[^>]*href=\"([^\"]*vod-detail-id[^\"]*)\"");
    private static final Pattern S_DES_PATTERN = Pattern.compile("<[^>]*class=\"[^\"]*sDes[^\"]*\"[^>]*>([^<]+)</");
    private static final Pattern TITLE_ATTR_PATTERN = Pattern.compile("<a[^>]*title=\"([^\"]*)\"");
    private static final Pattern ACTOR_A_PATTERN = Pattern.compile(">([^<]+)</a>");
    private static final Pattern TAB_LI_PATTERN = Pattern.compile("<li[^>]*>[\\s\\S]*?</li>");
    private static final Pattern NUM_PATTERN = Pattern.compile("\\d+");
    private static final Pattern NM_PLAYER_URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    @Override
    public void init(android.content.Context context, String extend) {
        if (!TextUtils.isEmpty(extend)) {
            extend = extend.trim();
            if (extend.startsWith("http")) {
                host = extend;
                if (host.endsWith("/")) host = host.substring(0, host.length() - 1);
            }
        }
    }

    private Map<String, String> getHeaders() {
        if (headers != null) return headers;
        headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        headers.put("accept-language", "zh-CN,zh;q=0.9");
        headers.put("cache-control", "no-cache");
        headers.put("pragma", "no-cache");
        headers.put("upgrade-insecure-requests", "1");
        return headers;
    }

    private String fetch(String url, String referer) {
        try {
            Request.Builder builder = new Request.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("accept-language", "zh-CN,zh;q=0.9")
                    .addHeader("Referer", TextUtils.isEmpty(referer) ? (host + "/") : referer)
                    .get()
                    .url(url);
            Request request = builder.build();
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

    private String post(String url, Map<String, String> data, String referer) {
        try {
            StringBuilder form = new StringBuilder();
            for (Map.Entry<String, String> e : data.entrySet()) {
                if (form.length() > 0) form.append("&");
                form.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                    .append("=")
                    .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
            }
            RequestBody body = RequestBody.create(
                    MediaType.parse("application/x-www-form-urlencoded"),
                    form.toString());
            Request request = new Request.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", TextUtils.isEmpty(referer) ? (host + "/") : referer)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .post(body)
                    .url(url)
                    .build();
            OkHttpClient client = OkHttpUtil.defaultClient();
            Response response = client.newCall(request).execute();
            if (response.body() == null) return "";
            byte[] bytes = response.body().bytes();
            response.close();
            return new String(bytes, "utf-8");
        } catch (Exception e) {
            SpiderDebug.log("post error: " + e.getMessage());
            return "";
        }
    }

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

    private String fixPic(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("//")) return "http:" + url;
        if (url.startsWith("/")) return host + url;
        return url;
    }

    private String urlEncode(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    // ★ homeContent：手拼 JSON
    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();
        String[][] cfg = {
            {"1", "电影"}, {"2", "连续剧"}, {"3", "综艺"},
            {"4", "动漫"}, {"26", "短剧"}
        };
        for (String[] c : cfg) {
            JSONObject o = new JSONObject();
            o.put("type_id", c[0]);
            o.put("type_name", c[1]);
            classes.put(o);
        }
        result.put("class", classes);

        JSONObject filters = new JSONObject();
        JSONArray yearArr = buildYears();
        JSONArray areaArr = buildAreas();
        JSONArray byArr = buildBy();

        JSONArray f1 = new JSONArray();
        f1.put(filterGroup("id", "类型", new String[][]{
            {"全部", ""}, {"动作片", "5"}, {"喜剧片", "6"}, {"爱情片", "7"},
            {"科幻片", "8"}, {"恐怖片", "9"}, {"剧情片", "10"}, {"战争片", "11"},
            {"惊悚片", "16"}, {"奇幻片", "17"}
        }));
        f1.put(filterGroup("area", "地区", areaArr));
        f1.put(filterGroup("year", "年份", yearArr));
        f1.put(filterGroup("by", "排序", byArr));
        filters.put("1", f1);

        JSONArray f2 = new JSONArray();
        f2.put(filterGroup("id", "类型", new String[][]{
            {"全部", ""}, {"国产剧", "12"}, {"港台泰", "13"}, {"日韩剧", "14"}, {"欧美剧", "15"}
        }));
        f2.put(filterGroup("area", "地区", areaArr));
        f2.put(filterGroup("year", "年份", yearArr));
        f2.put(filterGroup("by", "排序", byArr));
        filters.put("2", f2);

        JSONArray f3 = new JSONArray();
        f3.put(filterGroup("id", "类型", new String[][]{{"全部", ""}}));
        f3.put(filterGroup("area", "地区", areaArr));
        f3.put(filterGroup("year", "年份", yearArr));
        f3.put(filterGroup("by", "排序", byArr));
        filters.put("3", f3);

        JSONArray f4 = new JSONArray();
        f4.put(filterGroup("id", "类型", new String[][]{{"全部", ""}, {"动漫剧", "18"}}));
        f4.put(filterGroup("area", "地区", areaArr));
        f4.put(filterGroup("year", "年份", yearArr));
        f4.put(filterGroup("by", "排序", byArr));
        filters.put("4", f4);

        JSONArray f26 = new JSONArray();
        f26.put(filterGroup("id", "类型", new String[][]{{"全部", ""}}));
        f26.put(filterGroup("area", "地区", areaArr));
        f26.put(filterGroup("year", "年份", yearArr));
        f26.put(filterGroup("by", "排序", byArr));
        filters.put("26", f26);

        result.put("filters", filters);
        return result.toString();
    }

    private JSONArray buildYears() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject all = new JSONObject();
        all.put("n", "全部"); all.put("v", "");
        arr.put(all);
        for (int y = 2026; y >= 1900; y--) {
            JSONObject o = new JSONObject();
            o.put("n", String.valueOf(y));
            o.put("v", String.valueOf(y));
            arr.put(o);
        }
        return arr;
    }

    private JSONArray buildAreas() throws Exception {
        String[][] data = {
            {"全部", ""}, {"大陆", "大陆"}, {"香港", "香港"}, {"台湾", "台湾"},
            {"美国", "美国"}, {"韩国", "韩国"}, {"日本", "日本"}, {"泰国", "泰国"},
            {"新加坡", "新加坡"}, {"马来西亚", "马来西亚"}, {"印度", "印度"},
            {"英国", "英国"}, {"法国", "法国"}, {"加拿大", "加拿大"},
            {"西班牙", "西班牙"}, {"俄罗斯", "俄罗斯"}, {"其它", "其它"}
        };
        JSONArray arr = new JSONArray();
        for (String[] kv : data) {
            JSONObject o = new JSONObject();
            o.put("n", kv[0]); o.put("v", kv[1]);
            arr.put(o);
        }
        return arr;
    }

    private JSONArray buildBy() throws Exception {
        String[][] data = {{"全部", "time"}, {"人气", "hits"}, {"评分", "score"}};
        JSONArray arr = new JSONArray();
        for (String[] kv : data) {
            JSONObject o = new JSONObject();
            o.put("n", kv[0]); o.put("v", kv[1]);
            arr.put(o);
        }
        return arr;
    }

    private JSONObject filterGroup(String key, String name, String[][] data) throws Exception {
        JSONArray arr = new JSONArray();
        for (String[] kv : data) {
            JSONObject o = new JSONObject();
            o.put("n", kv[0]); o.put("v", kv[1]);
            arr.put(o);
        }
        return filterGroup(key, name, arr);
    }

    private JSONObject filterGroup(String key, String name, JSONArray arr) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("key", key);
        obj.put("name", name);
        obj.put("init", "");
        obj.put("value", arr);
        return obj;
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = fetch(host + "/", null);
        JSONArray arr = parseList(html);
        JSONObject r = new JSONObject();
        r.put("list", arr);
        return r.toString();
    }

    private JSONArray parseList(String html) {
        JSONArray list = new JSONArray();
        if (html == null || html.isEmpty()) return list;
        try {
            Matcher liM = LI_PATTERN.matcher(html);
            List<String> seen = new ArrayList<>();
            while (liM.find()) {
                String block = liM.group(1);
                String href = find(DETAIL_HREF_PATTERN, block);
                if (href.isEmpty() || seen.contains(href)) continue;
                seen.add(href);

                String title = find(TITLE_ATTR_PATTERN, block);
                if (title.isEmpty()) title = group("<[^>]*class=\"[^\"]*sTit[^\"]*\"[^>]*>([^<]+)</", block, 1);
                String pic = group("<img[^>]*data-echo=\"([^\"]+)\"", block, 1);
                if (pic.isEmpty()) pic = group("<img[^>]*src=\"([^\"]+)\"", block, 1);
                String remark = group("<[^>]*class=\"[^\"]*sBottom[^\"]*\"[^>]*>[\\s\\S]*?<span[^>]*>([^<]*)</span>", block, 1);
                if (remark.isEmpty()) remark = group("<[^>]*class=\"[^\"]*covericon[^\"]*\"[^>]*>([^<]*)</", block, 1);
                remark = remark.replaceAll("<[^>]+>", "").trim();

                JSONObject o = new JSONObject();
                o.put("vod_id", href);
                o.put("vod_name", title.trim());
                o.put("vod_pic", fixPic(pic));
                o.put("vod_remarks", remark);
                list.put(o);
            }
        } catch (Exception e) {
            SpiderDebug.log("parseList error: " + e.getMessage());
        }
        return list;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}

        if ("home".equals(tid)) {
            String html = fetch(host + "/", null);
            JSONObject r = new JSONObject();
            r.put("page", 1);
            r.put("pagecount", 1);
            r.put("list", parseList(html));
            return r.toString();
        }

        String id = tid;
        if (extend != null && extend.get("id") != null && !extend.get("id").isEmpty()) id = extend.get("id");
        Matcher m = NUM_PATTERN.matcher(id);
        String s = m.find() ? m.group() : "1";

        String rBy = extend != null && extend.get("by") != null ? extend.get("by") : "time";
        if (rBy.isEmpty()) rBy = "time";
        String cls = extend != null && extend.get("class") != null ? extend.get("class") : "";
        String year = extend != null && extend.get("year") != null ? extend.get("year") : "";
        String letter = extend != null && extend.get("letter") != null ? extend.get("letter") : "";
        String area = extend != null && extend.get("area") != null ? extend.get("area") : "";
        String lang = extend != null && extend.get("lang") != null ? extend.get("lang") : "";

        String url = host + "/index.php?m=vod-list-id-" + s
                + "-pg-" + page
                + "-order--by-" + rBy
                + "-class-" + cls
                + "-year-" + year
                + "-letter-" + letter
                + "-area-" + urlEncode(area)
                + "-lang-" + urlEncode(lang)
                + ".html";

        String html = fetch(url, null);
        JSONArray list = parseList(html);
        JSONObject r = new JSONObject();
        r.put("page", page);
        r.put("pagecount", list.length() > 0 ? page + 1 : 1);
        r.put("list", list);
        return r.toString();
    }

    @Override
    public String searchContent(String wd, boolean quick) throws Exception {
        return searchContent(wd, quick, "1");
    }

    @Override
    public String searchContent(String wd, boolean quick, String pg) throws Exception {
        String url = host + "/index.php?m=vod-search";
        Map<String, String> data = new HashMap<>();
        data.put("wd", wd);
        String resp = post(url, data, host + "/vod-search");

        JSONArray list = new JSONArray();
        Matcher liM = LI_PATTERN.matcher(resp);
        while (liM.find()) {
            String block = liM.group(1);
            if (!block.contains("vod-detail-id")) continue;
            String href = find(DETAIL_HREF_PATTERN, block);
            if (href.isEmpty()) continue;
            String pic = group("<img[^>]*data-src=\"([^\"]+)\"", block, 1);
            if (pic.isEmpty()) pic = group("<img[^>]*src=\"([^\"]+)\"", block, 1);
            String title = group("<[^>]*class=\"[^\"]*sTit[^\"]*\"[^>]*>([^<]+)</", block, 1);
            String style = group("<[^>]*class=\"[^\"]*sStyle[^\"]*\"[^>]*>([^<]+)</", block, 1);
            String score = "", actor = "";
            Matcher dM = S_DES_PATTERN.matcher(block);
            while (dM.find()) {
                String txt = dM.group(1);
                if (txt.contains("评分")) score = txt.replaceAll("^.*?评分[：:]?\\s*", "").trim();
                if (txt.contains("主演")) actor = txt.replaceAll("^.*?主演[：:]?\\s*", "").trim();
            }
            List<String> parts = new ArrayList<>();
            if (!style.isEmpty()) parts.add(style);
            if (!score.isEmpty()) parts.add(score + "分");
            if (!actor.isEmpty()) parts.add(actor);
            String remark = String.join(" | ", parts);

            JSONObject o = new JSONObject();
            o.put("vod_id", href);
            o.put("vod_name", title.trim());
            o.put("vod_pic", fixPic(pic));
            o.put("vod_remarks", remark);
            list.put(o);
        }
        JSONObject r = new JSONObject();
        r.put("list", list);
        return r.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vid = ids.get(0);
        String url = vid.startsWith("http") ? vid : (host + vid);
        String html = fetch(url, null);

        JSONObject info = new JSONObject();
        info.put("vod_id", vid);

        String name = group("<h1[^>]*class=\"title\"[^>]*>[\\s\\S]*?<a[^>]*title=\"([^\"]+)\"", html, 1);
        if (name.isEmpty()) name = group("<title>\\s*《([^》]+)》", html, 1);
        info.put("vod_name", name.trim());

        String pic = group("<[^>]*class=\"page-hd\"[^>]*>[\\s\\S]*?<img[^>]*src=\"([^\"]+)\"", html, 1);
        info.put("vod_pic", fixPic(pic));

        String remarks = group("<div[^>]*class=\"desc_item\"[^>]*>[\\s\\S]*?状态[:：][\\s\\S]*?<font[^>]*>([^<]+)</font>", html, 1);
        info.put("vod_remarks", remarks.trim());

        String year = group("<article[^>]*class=\"detail-con\"[^>]*>[\\s\\S]*?年代[：:][\\s\\S]*?<em[^>]*>([^<]+)</em>", html, 1);
        if (year.isEmpty()) year = group("<div[^>]*class=\"desc_item\"[^>]*>[\\s\\S]*?年代[:：][\\s\\S]*?<a[^>]*>([^<]+)</a>", html, 1);
        info.put("vod_year", year.trim());

        String actorBlock = group("<div[^>]*class=\"desc_item\"[^>]*>[\\s\\S]*?主演[:：]([\\s\\S]*?)</div>", html, 1);
        List<String> actorList = new ArrayList<>();
        if (!actorBlock.isEmpty()) {
            Matcher aM = ACTOR_A_PATTERN.matcher(actorBlock);
            while (aM.find()) {
                String a = aM.group(1).trim();
                if (!a.isEmpty() && !a.startsWith("http") && !a.contains("target")) actorList.add(a);
            }
        }
        info.put("vod_actor", String.join(" ", actorList));

        String dirBlock = group("<div[^>]*class=\"desc_item\"[^>]*>[\\s\\S]*?导演[:：]([\\s\\S]*?)</div>", html, 1);
        List<String> dirList = new ArrayList<>();
        if (!dirBlock.isEmpty()) {
            Matcher dM = ACTOR_A_PATTERN.matcher(dirBlock);
            while (dM.find()) {
                String d = dM.group(1).trim();
                if (!d.isEmpty() && !d.startsWith("http") && !d.contains("target")) dirList.add(d);
            }
        }
        info.put("vod_director", String.join(" ", dirList));

        String content = group("<article[^>]*class=\"detail-con\"[^>]*>[\\s\\S]*?<p>([\\s\\S]*?)</p>", html, 1);
        content = content.replaceAll("<[^>]+>", "").replaceAll("简[\\s\\S]*?介[：:]\\s*", "").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
        info.put("vod_content", content);

        String playBtn = group("<a[^>]*href=\"([^\"]+)\"[^>]*class=\"greenBtn\"", html, 1);
        if (playBtn.isEmpty()) playBtn = group("<a[^>]*class=\"greenBtn\"[^>]*href=\"([^\"]+)\"", html, 1);
        if (playBtn.isEmpty()) playBtn = group("href=\"(/vod-play-id-[^\"]+)\"", html, 1);

        info.put("vod_play_from", "");
        info.put("vod_play_url", "");

        if (!playBtn.isEmpty()) {
            String playUrl = playBtn.startsWith("http") ? playBtn : (host + playBtn);
            String playHtml = fetch(playUrl, url);
            String macFrom = group("mac_from='([^']+)'", playHtml, 1);
            String macUrl  = group("mac_url='([^']+)'", playHtml, 1);

            if (!macFrom.isEmpty() && !macUrl.isEmpty()) {
                List<String> lineNames = new ArrayList<>();
                String tabBox = group("<div[^>]*id=\"leftTabBox\"[^>]*>[\\s\\S]*?<ul>([\\s\\S]*?)</ul>", playHtml, 1);
                if (!tabBox.isEmpty()) {
                    Matcher liM = TAB_LI_PATTERN.matcher(tabBox);
                    while (liM.find()) {
                        String nm = group("<a[^>]*>([^<]+)</a>", liM.group(), 1);
                        if (!nm.isEmpty()) lineNames.add(nm.trim());
                    }
                }
                if (lineNames.isEmpty()) for (String x : macFrom.split("\\$\\$\\$")) lineNames.add(x);

                String[] urlLines = macUrl.split("\\$\\$\\$");
                List<String> playFrom = new ArrayList<>();
                List<String> playUrlList = new ArrayList<>();
                for (int j = 0; j < urlLines.length; j++) {
                    String nm = (j < lineNames.size() && !lineNames.get(j).isEmpty()) ? lineNames.get(j) : ("线路" + (j + 1));
                    String[] eps = urlLines[j].split("#");
                    List<String> epList = new ArrayList<>();
                    for (String ep : eps) {
                        String[] parts = ep.split("\\$");
                        if (parts.length == 2) epList.add(parts[0] + "$" + parts[1]);
                        else epList.add(ep);
                    }
                    playFrom.add(nm);
                    playUrlList.add(String.join("#", epList));
                }
                info.put("vod_play_from", String.join("$$$", playFrom));
                info.put("vod_play_url", String.join("$$$", playUrlList));
            }
        }

        JSONArray list = new JSONArray();
        list.put(info);
        JSONObject r = new JSONObject();
        r.put("list", list);
        return r.toString();
    }

    // ★ playerContent 一字未改
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        int parse = 0;
        String realUrl = decodeNmUrl(id);
        if (realUrl.isEmpty() || !realUrl.startsWith("http")) {
            String nmUrl = "https://api.nmvod.me:520/player/?url=";
            String v = fetch(nmUrl + id, null);
            String m = find(NM_PLAYER_URL_PATTERN, v);
            if (!m.isEmpty()) realUrl = m.replace("\\/", "/");
            else { realUrl = nmUrl + id; parse = 1; }
        }
        result.put("parse", parse);
        result.put("url", realUrl);
        JSONObject h = new JSONObject();
        h.put("User-Agent", UA);
        result.put("header", h);
        return result.toString();
    }

    private String decodeNmUrl(String s) {
        try {
            if (s == null) return "";
            s = s.replaceAll("#+$", "");
            if (s.length() < 66) return "";
            String first = s.substring(0, 66);
            String rest = s.substring(66);
            StringBuilder even = new StringBuilder();
            for (int i = 0; i < first.length(); i += 2) even.append(first.charAt(i));
            String merged = even.toString() + rest.replace("O0O0O", "=").replace("oo00o", "/").replace("o000o", "+");
            byte[] decoded = Base64.decode(merged, Base64.DEFAULT);
            return new String(decoded, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}