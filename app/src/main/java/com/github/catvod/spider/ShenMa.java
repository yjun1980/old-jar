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
 * 神马影院 - www.smyyok.cc（PiaoHua 旧版风格）
 */
public class ShenMa extends Spider {

    private static final String DEFAULT_HOST = "https://www.smyyok.cc";
    private String host = DEFAULT_HOST;

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern LIST_PATTERN = Pattern.compile(
        "<div class=\"public-list-box public-pic-b\">([\\s\\S]*?)</div>\\s*</div>");
    private static final Pattern PAGE_COUNT_PATTERN = Pattern.compile("共(\\d+)条数据,当前(\\d+)/(\\d+)页");
    private static final Pattern LAST_PAGE_PATTERN = Pattern.compile(
        "<a[^>]*href=\"[^\"]*-----(\\d+)---[^\"]*\"[^>]*>尾页</a>");
    private static final Pattern PARAM_BLOCK_PATTERN = Pattern.compile(
        "<div[^>]*class=\"info-parameter none\"[^>]*>([\\s\\S]*?)</div>\\s*</div>");
    private static final Pattern LI_PATTERN = Pattern.compile("<li>(.*?)</li>", Pattern.DOTALL);
    private static final Pattern EM_PATTERN = Pattern.compile("<em[^>]*class=\"cor4\"[^>]*>([^<]+)</em>");
    private static final Pattern A_PATTERN = Pattern.compile("<a[^>]*>([^<]+)</a>");
    private static final Pattern DESC_PATTERN = Pattern.compile(
        "<div[^>]*id=\"height_limit\"[^>]*class=\"text[^\"]*\"[^>]*>([\\s\\S]*?)</div>");
    private static final Pattern PIC_PATTERN = Pattern.compile(
        "<img[^>]*class=\"lazy lazy1 mask-1\"[^>]*data-src=\"([^\"]+)\"[^>]*>");
    private static final Pattern TAB_PATTERN = Pattern.compile(
        "<a[^>]*class=\"swiper-slide[^\"]*\"[^>]*>[\\s]*<i[^>]*></i>&nbsp;([^<]+)(?:<span[^>]*>\\d+</span>)?</a>");
    private static final Pattern NAV_TAB_PATTERN = Pattern.compile(
        "<a[^>]*class=\"swiper-slide[^\"]*nav-dt[^\"]*\"[^>]*>[\\s]*<i[^>]*></i>&nbsp;([^<]+)</a>");
    private static final Pattern UL_PATTERN = Pattern.compile(
        "<ul[^>]*class=\"anthology-list-play size\"[^>]*>([\\s\\S]*?)</ul>");
    private static final Pattern EP_PATTERN = Pattern.compile(
        "<a[^>]*class=\"hide[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>");
    private static final Pattern DOWNLOAD_LI_PATTERN = Pattern.compile(
        "<li[^>]*class=\"download-li\"[^>]*>([\\s\\S]*?)</li>");
    private static final Pattern PLAYER_AAAA_PATTERN = Pattern.compile(
        "var\\s+player_aaaa\\s*=\\s*(\\{[^;]+\\})");
    private static final Pattern M3U8_PATTERN = Pattern.compile(
        "(https?:\\/\\/[^\\s<>\"']+\\.m3u8[^\\s<>\"']*)");

    private Map<String, String> headers;
    private Map<String, String> m3u8Headers;

    @Override
    public void init(Context context, String extend) {
        if (!TextUtils.isEmpty(extend)) {
            extend = extend.trim();
            if (extend.startsWith("http")) {
                host = extend;
                if (host.endsWith("/")) host = host.substring(0, host.length() - 1);
            }
        }
        headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        headers.put("Referer", host + "/");

        m3u8Headers = new HashMap<>();
        m3u8Headers.put("User-Agent", UA);
        m3u8Headers.put("Referer", host + "/");
        m3u8Headers.put("Accept", "*/*");
    }

    private Map<String, String> getHeaders() {
        if (headers == null) init(null, null);
        return headers;
    }

    private Map<String, String> getM3u8Headers() {
        if (m3u8Headers == null) init(null, null);
        return m3u8Headers;
    }

    private String fetchHtml(String targetUrl) {
        try {
            Request request = new Request.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", host + "/")
                    .get()
                    .url(targetUrl)
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

    private String fixUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http")) return url;
        if (url.startsWith("/")) return host + url;
        return host + "/" + url;
    }

    private String cleanUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        url = url.replace("\\/", "/");
        while (url.startsWith("\"") || url.startsWith("'")) url = url.substring(1);
        while (url.endsWith("\"") || url.endsWith("'")) url = url.substring(0, url.length() - 1);
        url = url.trim();
        if (url.startsWith("//")) url = "https:" + url;
        return url;
    }

    private String urlEncode(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private String buildVodShowUrl(String tid, String area, String sort, String pg, String year, String cls) {
        String[] parts = new String[12];
        for (int i = 0; i < 12; i++) parts[i] = "";
        parts[1] = area == null ? "" : area;
        parts[2] = sort == null ? "" : sort;
        parts[3] = cls == null ? "" : cls;
        int page = parseIntSafe(pg, 1);
        parts[8] = page > 1 ? pg : "";

        StringBuilder url = new StringBuilder("/vodshow/").append(tid);
        url.append(String.join("-", parts));
        if (year != null && !year.isEmpty()) url.append(year);
        url.append(".html");
        return url.toString();
    }

    private JSONArray extractList(String html) {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;
        try {
            Matcher m = LIST_PATTERN.matcher(html);
            while (m.find()) {
                String item = m.group(1);
                String href = group("<a[^>]*class=\"public-list-exp\"[^>]*href=\"([^\"]+)\"[^>]*>", item, 1);
                String title = group("<a[^>]*class=\"time-title[^\"]*\"[^>]*title=\"([^\"]+)\"[^>]*>", item, 1);
                String pic = group("<img[^>]*data-src=\"([^\"]+)\"[^>]*>", item, 1);
                String remark = group("<span[^>]*class=\"public-list-prb[^\"]*\"[^>]*>([^<]+)</span>", item, 1);

                JSONObject o = new JSONObject();
                o.put("vod_id", href);
                o.put("vod_name", title.trim());
                o.put("vod_pic", pic.isEmpty() ? "" : fixUrl(pic));
                o.put("vod_remarks", remark.trim());
                list.put(o);
            }
        } catch (Exception e) {
            SpiderDebug.log("extractList error: " + e.getMessage());
        }
        return list;
    }

    private int extractPageCount(String html) {
        if (TextUtils.isEmpty(html)) return 1;
        String total = find(PAGE_COUNT_PATTERN, html);
        if (!total.isEmpty()) {
            try { return Integer.parseInt(total); } catch (Exception ignored) {}
        }
        String last = find(LAST_PAGE_PATTERN, html);
        if (!last.isEmpty()) {
            try { return Integer.parseInt(last); } catch (Exception ignored) {}
        }
        return 1;
    }

    private JSONObject extractDetail(String html) {
        try {
            JSONObject info = new JSONObject();
            info.put("vod_id", ""); info.put("vod_name", ""); info.put("vod_pic", "");
            info.put("vod_class", ""); info.put("vod_area", ""); info.put("vod_year", "");
            info.put("vod_actor", ""); info.put("vod_director", ""); info.put("vod_content", "");
            info.put("vod_play_from", ""); info.put("vod_play_url", "");

            String paramBlock = find(PARAM_BLOCK_PATTERN, html);
            if (!paramBlock.isEmpty()) {
                Matcher liM = LI_PATTERN.matcher(paramBlock);
                while (liM.find()) {
                    String li = liM.group(1);
                    String em = find(EM_PATTERN, li);
                    if (em.isEmpty()) continue;
                    String key = em.trim().replace("：", "").replace(":", "").trim();

                    String content = li.replaceFirst("<em[^>]*class=\"cor4\"[^>]*>.*?</em>", "");
                    List<String> aTexts = new ArrayList<>();
                    Matcher aM = A_PATTERN.matcher(content);
                    while (aM.find()) {
                        String t = aM.group(1).trim();
                        if (!t.isEmpty()) aTexts.add(t);
                    }
                    content = aTexts.isEmpty() ? content.replaceAll("<[^>]+>", "").trim() : String.join(" ", aTexts);
                    content = content.replaceAll("\\s+", " ").trim();

                    switch (key) {
                        case "片名": info.put("vod_name", content); break;
                        case "主演": info.put("vod_actor", content); break;
                        case "导演": info.put("vod_director", content); break;
                        case "年份": info.put("vod_year", content); break;
                        case "地区": info.put("vod_area", content); break;
                        case "类型": info.put("vod_class", content); break;
                    }
                }
            }

            String desc = find(DESC_PATTERN, html);
            if (!desc.isEmpty()) {
                String clean = desc.replaceAll("<[^>]+>", "").trim();
                info.put("vod_content", "神马影院提醒你注意广告防止被骗！" + clean);
            }

            String pic = find(PIC_PATTERN, html);
            if (!pic.isEmpty()) info.put("vod_pic", fixUrl(pic));

            String[] pl = extractPlaylist(html);
            info.put("vod_play_from", pl[0]);
            info.put("vod_play_url", pl[1]);

            return info;
        } catch (Exception e) {
            SpiderDebug.log("extractDetail error: " + e.getMessage());
            return null;
        }
    }

    private String[] extractPlaylist(String html) {
        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();
        List<String> names = new ArrayList<>();

        Matcher tabM = TAB_PATTERN.matcher(html);
        while (tabM.find()) {
            String name = tabM.group(1).trim();
            if (!name.isEmpty() && !names.contains(name)) names.add(name);
        }

        if (names.isEmpty()) {
            Matcher navM = NAV_TAB_PATTERN.matcher(html);
            while (navM.find()) {
                String name = navM.group(1).trim();
                if (!name.isEmpty() && !names.contains(name)) names.add(name);
            }
        }

        Matcher ulM = UL_PATTERN.matcher(html);
        int idx = 0;
        while (ulM.find()) {
            String ulHtml = ulM.group(1);
            List<String[]> eps = new ArrayList<>();
            Matcher epM = EP_PATTERN.matcher(ulHtml);
            while (epM.find()) eps.add(new String[]{epM.group(2).trim(), epM.group(1)});
            if (!eps.isEmpty()) {
                String fromName = idx < names.size() ? names.get(idx) : ("线路" + (idx + 1));
                List<String> epList = new ArrayList<>();
                for (String[] ep : eps) epList.add(ep[0] + "$" + fixUrl(ep[1]));
                playFrom.add("【神马影院】" + fromName);
                playUrl.add(String.join("#", epList));
                idx++;
            }
        }

        if (playFrom.isEmpty()) {
            Matcher dlM = DOWNLOAD_LI_PATTERN.matcher(html);
            List<String[]> eps = new ArrayList<>();
            while (dlM.find()) {
                String item = dlM.group(1);
                String name = group("<a[^>]*class=\"left\"[^>]*>[\\s]*<i[^>]*></i>([^<]+)</a>", item, 1);
                String url = group("<input[^>]*class=\"box[^\"]*\"[^>]*value=\"([^\"]+)\"[^>]*>", item, 1);
                if (!name.isEmpty() && !url.isEmpty()) eps.add(new String[]{name.trim(), url});
            }
            if (!eps.isEmpty()) {
                List<String> epList = new ArrayList<>();
                for (String[] ep : eps) epList.add(ep[0] + "$" + fixUrl(ep[1]));
                playFrom.add("下载");
                playUrl.add(String.join("#", epList));
            }
        }

        return new String[]{String.join("$$$", playFrom), String.join("$$$", playUrl)};
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();
            String[][] cfg = {{"1", "电影"}, {"2", "电视剧"}, {"3", "综艺"}, {"4", "动漫"}, {"5", "短剧"}};
            for (String[] c : cfg) {
                JSONObject o = new JSONObject();
                o.put("type_id", c[0]); o.put("type_name", c[1]);
                classes.put(o);
            }
            result.put("class", classes);
            result.put("filters", buildFilters());
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("homeContent error: " + e.getMessage());
            return "";
        }
    }

    private JSONObject buildFilters() {
        JSONObject filters = new JSONObject();
        try {
            JSONArray yearArr = buildYears();
            JSONArray areaArr = buildAreas();
            JSONArray sortArr = buildSort();

            String[][] classValues1 = {
                {"", "全部"}, {"动作片", "动作片"}, {"喜剧片", "喜剧片"}, {"科幻片", "科幻片"},
                {"恐怖片", "恐怖片"}, {"爱情片", "爱情片"}, {"剧情片", "剧情片"}, {"战争片", "战争片"},
                {"记录片", "记录片"}, {"动画片", "动画片"}, {"惊悚", "惊悚"}, {"犯罪", "犯罪"},
                {"悬疑", "悬疑"}, {"冒险", "冒险"}, {"奇幻", "奇幻"}, {"家庭", "家庭"},
                {"历史", "历史"}, {"传记", "传记"}, {"古装", "古装"}, {"音乐", "音乐"},
                {"同性", "同性"}, {"运动", "运动"}, {"武侠", "武侠"}, {"短片", "短片"},
                {"歌舞", "歌舞"}, {"西部", "西部"}, {"儿童", "儿童"}, {"灾难", "灾难"},
                {"戏曲", "戏曲"}, {"真人秀", "真人秀"}, {"青春", "青春"}
            };
            String[][] classValues2 = {
                {"", "全部"}, {"国产剧", "国产剧"}, {"欧美剧", "欧美剧"}, {"香港剧", "香港剧"},
                {"韩国剧", "韩国剧"}, {"台湾剧", "台湾剧"}, {"日本剧", "日本剧"}, {"海外剧", "海外剧"},
                {"泰国剧", "泰国剧"}, {"剧情", "剧情"}, {"爱情", "爱情"}, {"喜剧", "喜剧"},
                {"悬疑", "悬疑"}, {"犯罪", "犯罪"}, {"古装", "古装"}, {"动作", "动作"},
                {"奇幻", "奇幻"}, {"惊悚", "惊悚"}, {"家庭", "家庭"}, {"历史", "历史"},
                {"科幻", "科幻"}, {"战争", "战争"}, {"同性", "同性"}, {"武侠", "武侠"},
                {"冒险", "冒险"}, {"恐怖", "恐怖"}, {"纪录", "纪录"}, {"传记", "传记"},
                {"短片", "短片"}, {"运动", "运动"}, {"音乐", "音乐"}, {"儿童", "儿童"},
                {"歌舞", "歌舞"}, {"西部", "西部"}, {"灾难", "灾难"}
            };
            String[][] classValues3 = {
                {"", "全部"}, {"大陆综艺", "大陆综艺"}, {"港台综艺", "港台综艺"}, {"日韩综艺", "日韩综艺"},
                {"欧美综艺", "欧美综艺"}, {"真人秀", "真人秀"}, {"纪录片", "纪录片"}, {"脱口秀", "脱口秀"},
                {"音乐", "音乐"}, {"歌舞", "歌舞"}, {"相声", "相声"}, {"喜剧", "喜剧"},
                {"爱情", "爱情"}, {"历史", "历史"}, {"运动", "运动"}, {"冒险", "冒险"},
                {"剧情", "剧情"}, {"访谈", "访谈"}, {"旅游", "旅游"}, {"悬疑", "悬疑"},
                {"家庭", "家庭"}, {"短片", "短片"}, {"同性", "同性"}, {"动作", "动作"},
                {"儿童", "儿童"}, {"惊悚", "惊悚"}, {"美食", "美食"}
            };
            String[][] classValues4 = {
                {"", "全部"}, {"国产动漫", "国产动漫"}, {"日韩动漫", "日韩动漫"}, {"欧美动漫", "欧美动漫"},
                {"港台动漫", "港台动漫"}, {"海外动漫", "海外动漫"}, {"动画", "动画"}, {"喜剧", "喜剧"},
                {"剧情", "剧情"}, {"奇幻", "奇幻"}, {"冒险", "冒险"}, {"动作", "动作"},
                {"科幻", "科幻"}, {"爱情", "爱情"}, {"儿童", "儿童"}, {"家庭", "家庭"},
                {"短片", "短片"}, {"悬疑", "悬疑"}, {"运动", "运动"}, {"古装", "古装"},
                {"武侠", "武侠"}, {"音乐", "音乐"}, {"犯罪", "犯罪"}, {"惊悚", "惊悚"},
                {"战争", "战争"}, {"恐怖", "恐怖"}, {"历史", "历史"}, {"搞笑", "搞笑"},
                {"歌舞", "歌舞"}, {"热血", "热血"}
            };
            String[][] classValues5 = {
                {"", "全部"}, {"女频恋爱", "女频恋爱"}, {"反转爽剧", "反转爽剧"}, {"古装仙侠", "古装仙侠"},
                {"年代穿越", "年代穿越"}, {"脑洞悬疑", "脑洞悬疑"}, {"现代都市", "现代都市"}
            };

            filters.put("1", buildFilterArray(filterGroup("class", "类型", classValues1), filterGroup("area", "地区", areaArr), filterGroup("year", "年份", yearArr), filterGroup("sort", "排序", sortArr)));
            filters.put("2", buildFilterArray(filterGroup("class", "类型", classValues2), filterGroup("area", "地区", areaArr), filterGroup("year", "年份", yearArr), filterGroup("sort", "排序", sortArr)));
            filters.put("3", buildFilterArray(filterGroup("class", "类型", classValues3), filterGroup("area", "地区", areaArr), filterGroup("year", "年份", yearArr), filterGroup("sort", "排序", sortArr)));
            filters.put("4", buildFilterArray(filterGroup("class", "类型", classValues4), filterGroup("area", "地区", areaArr), filterGroup("year", "年份", yearArr), filterGroup("sort", "排序", sortArr)));
            filters.put("5", buildFilterArray(filterGroup("class", "类型", classValues5), filterGroup("area", "地区", areaArr), filterGroup("year", "年份", yearArr), filterGroup("sort", "排序", sortArr)));
        } catch (Exception e) {
            SpiderDebug.log("buildFilters error: " + e.getMessage());
        }
        return filters;
    }

    private JSONArray buildYears() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject all = new JSONObject(); all.put("n", "全部"); all.put("v", "");
        arr.put(all);
        for (int y = 2026; y >= 2000; y--) {
            JSONObject o = new JSONObject(); o.put("n", String.valueOf(y)); o.put("v", String.valueOf(y));
            arr.put(o);
        }
        return arr;
    }

    private JSONArray buildAreas() throws Exception {
        String[][] data = {{"", "全部"}, {"大陆", "大陆"}, {"香港", "香港"}, {"台湾", "台湾"}, {"美国", "美国"}, {"日本", "日本"}, {"韩国", "韩国"}, {"英国", "英国"}, {"法国", "法国"}, {"德国", "德国"}, {"意大利", "意大利"}, {"西班牙", "西班牙"}, {"俄罗斯", "俄罗斯"}, {"加拿大", "加拿大"}, {"印度", "印度"}, {"泰国", "泰国"}, {"其它", "其它"}, {"新加坡", "新加坡"}, {"菲律宾", "菲律宾"}, {"澳大利亚", "澳大利亚"}, {"土耳其", "土耳其"}, {"瑞典", "瑞典"}, {"巴西", "巴西"}, {"荷兰", "荷兰"}, {"印度尼西亚", "印度尼西亚"}, {"挪威", "挪威"}, {"智利", "智利"}, {"爱尔兰", "爱尔兰"}, {"伊朗", "伊朗"}, {"蒙古", "蒙古"}};
        JSONArray arr = new JSONArray();
        for (String[] kv : data) { JSONObject o = new JSONObject(); o.put("n", kv[1]); o.put("v", kv[0]); arr.put(o); }
        return arr;
    }

    private JSONArray buildSort() throws Exception {
        String[][] data = {{"time", "按最新"}, {"hits", "按最热"}, {"score", "按评分"}};
        JSONArray arr = new JSONArray();
        for (String[] kv : data) { JSONObject o = new JSONObject(); o.put("n", kv[1]); o.put("v", kv[0]); arr.put(o); }
        return arr;
    }

    private JSONArray buildFilterArray(JSONObject... groups) {
        JSONArray arr = new JSONArray();
        for (JSONObject g : groups) arr.put(g);
        return arr;
    }

    private JSONObject filterGroup(String key, String name, String[][] values) throws Exception {
        JSONArray arr = new JSONArray();
        for (String[] v : values) { JSONObject o = new JSONObject(); o.put("v", v[0]); o.put("n", v[1]); arr.put(o); }
        JSONObject obj = new JSONObject(); obj.put("key", key); obj.put("name", name); obj.put("value", arr);
        return obj;
    }

    private JSONObject filterGroup(String key, String name, JSONArray values) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("key", key); obj.put("name", name); obj.put("value", values);
        return obj;
    }

    @Override
    public String homeVideoContent() {
        try {
            String html = fetchHtml(host + "/");
            JSONArray list = extractList(html);
            JSONObject r = new JSONObject();
            r.put("list", list);
            return r.toString();
        } catch (Exception e) { return ""; }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = parseIntSafe(pg, 1);
            String cls  = extend != null && extend.get("class") != null ? extend.get("class") : "";
            String area = extend != null && extend.get("area") != null ? extend.get("area") : "";
            String year = extend != null && extend.get("year") != null ? extend.get("year") : "";
            String sort = extend != null && extend.get("sort") != null ? extend.get("sort") : "";

            String url = host + buildVodShowUrl(tid, area, sort, pg, year, cls);
            String html = fetchHtml(url);
            JSONArray list = extractList(html);
            int pagecount = extractPageCount(html);
            if (pagecount < 1) pagecount = 1;

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("list", list);
            result.put("pagecount", pagecount);
            result.put("limit", 24);
            result.put("total", pagecount * 24);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("categoryContent error: " + e.getMessage());
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = id.startsWith("http") ? id : (host + id);
            String html = fetchHtml(url);
            if (html.isEmpty()) return "{\"list\":[]}";

            JSONObject info = extractDetail(html);
            if (info == null) return "{\"list\":[]}";
            info.put("vod_id", id);

            JSONArray list = new JSONArray();
            list.put(info);
            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("detailContent error: " + e.getMessage());
            return "";
        }
    }

    @Override
    public String searchContent(String wd, boolean quick) {
        return searchContent(wd, quick, "1");
    }

    @Override
    public String searchContent(String wd, boolean quick, String pg) {
        try {
            int page = parseIntSafe(pg, 1);
            String url = host + "/vodsearch/" + urlEncode(wd) + "-------------.html";
            String html = fetchHtml(url);
            JSONArray list = extractList(html);
            JSONObject result = new JSONObject();
            result.put("list", list);
            result.put("page", page);
            result.put("pagecount", 1);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("searchContent error: " + e.getMessage());
            return "";
        }
    }

    // ★ playerContent 一字未改
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            if (id != null && id.matches(".*\\.(m3u8|mp4|flv|mkv|webm|ts)(\\?.*)?$")) {
                return buildResult(0, id, getM3u8Headers());
            }
            String html = fetchHtml(id);
            if (html == null || html.isEmpty()) return buildResult(1, id, getHeaders());

            String pJson = group("var\\s+player_aaaa\\s*=\\s*(\\{[^;]+\\})", html, 1);
            if (!pJson.isEmpty()) {
                try {
                    JSONObject data = null;
                    try { data = new JSONObject(pJson); }
                    catch (Exception e) {
                        String fixed = pJson.replaceAll("([{,])\\s*([a-zA-Z0-9_]+)\\s*:", "$1\"$2\":");
                        fixed = fixed.replaceAll(":\\s*'([^']*)'", ":\"$1\"");
                        fixed = fixed.replaceAll(",\\s*}", "}");
                        data = new JSONObject(fixed);
                    }
                    if (data != null && data.has("url")) {
                        String u = data.optString("url", "");
                        if (!u.isEmpty()) return buildResult(0, cleanUrl(u), getM3u8Headers());
                    }
                } catch (Exception ignored) {}
            }

            String m3u8 = group("(https?:\\/\\/[^\\s<>\"']+\\.m3u8[^\\s<>\"']*)", html, 1);
            if (!m3u8.isEmpty()) return buildResult(0, cleanUrl(m3u8), getM3u8Headers());

            return buildResult(1, id, getHeaders());
        } catch (Exception e) {
            SpiderDebug.log("playerContent error: " + e.getMessage());
            return "";
        }
    }

    private String buildResult(int parse, String url, Map<String, String> h) {
        try {
            JSONObject r = new JSONObject();
            r.put("parse", parse);
            r.put("url", url);
            if (h != null) r.put("header", new JSONObject(h));
            return r.toString();
        } catch (Exception e) { return ""; }
    }
}