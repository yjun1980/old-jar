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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DuShe extends Spider {

    private static final String DEFAULT_HOST = "https://www.dushehub.com";
    private static final String PROXY_HOST = "https://v.dushe.online";

    private String host = DEFAULT_HOST;

    private static final String UA = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";

    private Map<String, String> headers;
    private Map<String, String> m3u8Headers;

    // ============================================================
    // ★ 静态 Pattern 常量（PiaoHua 风格）
    // ============================================================
    private static final Pattern VOD_LIST_PATTERN = Pattern.compile(
        "<a[^>]*href=\"(/album/\\d+\\.html)\"[^>]*title=\"([^\"]*)\"[^>]*>[\\s\\S]*?" +
        "<div[^>]*class=\"[^\"]*module-item-note[^\"]*\"[^>]*>([^<]*)</div>[\\s\\S]*?" +
        "<img[^>]*data-original=\"([^\"]+)\"[^>]*>",
        Pattern.DOTALL);

    private static final Pattern TAB_PATTERN = Pattern.compile(
        "<div[^>]*class=\"[^\"]*module-tab-item[^\"]*\"[^>]*data-dropdown-value=\"([^\"]+)\"[^>]*>");

    private static final Pattern TAB_ALT_PATTERN = Pattern.compile(
        "<span[^>]*class=\"[^\"]*module-tab-value[^\"]*\"[^>]*>([^<]+)</span>");

    private static final Pattern EPISODE_PATTERN = Pattern.compile(
        "<a[^>]*class=\"[^\"]*module-play-list-link[^\"]*\"[^>]*href=\"(/play/\\d+-\\d+-\\d+\\.html)\"[^>]*>[\\s\\S]*?<span>([^<]+)</span>");

    private static final Pattern EPISODE_ALT_PATTERN = Pattern.compile(
        "<a[^>]*class=\"[^\"]*module-play-list-link[^\"]*\"[^>]*href=\"(/play/\\d+-\\d+-\\d+\\.html)\"[^>]*>([^<]+)</a>");

    private static final Pattern SID_PATTERN = Pattern.compile("/play/\\d+-(\\d+)-\\d+\\.html");

    private static final Pattern PLAYER_AAAA_PATTERN = Pattern.compile(
        "var\\s+player_aaaa\\s*=\\s*(\\{[^;]+\\})");

    private static final Pattern CONFIG_URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private static final Pattern PAGE_LINK_PATTERN = Pattern.compile(
        "<a[^>]*href=\"[^\"]*---(\\d+)---[^\"]*\"[^>]*>(\\d+)</a>");

    // ============================================================
    // init
    // ============================================================
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
        headers.put("Referer", host + "/");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

        m3u8Headers = new HashMap<>();
        m3u8Headers.put("User-Agent", UA);
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

    // ============================================================
    // ★ fetchHtml —— PiaoHua 风格
    // ============================================================
    private String fetchHtml(String url) {
        return fetchHtml(url, null);
    }

    private String fetchHtml(String url, String referer) {
        try {
            String fullUrl = url.startsWith("http") ? url : (host + url);
            Request.Builder builder = new Request.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .get()
                    .url(fullUrl);
            if (referer != null) builder.addHeader("Referer", referer);
            else builder.addHeader("Referer", host + "/");

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

    private String fixUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        url = url.trim();
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http")) return url;
        if (url.startsWith("/")) return host + url;
        return host + "/" + url;
    }

    private String cleanHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private String group(String regex, String text, int g) {
        if (text == null) return "";
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        return m.find() ? (m.group(g) == null ? "" : m.group(g)) : "";
    }

    private String urlEncode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private String buildResult(int parse, String url, Map<String, String> h) {
        try {
            JSONObject r = new JSONObject();
            r.put("parse", parse);
            r.put("url", url);
            if (h != null) {
                JSONObject hObj = new JSONObject();
                for (Map.Entry<String, String> e : h.entrySet()) hObj.put(e.getKey(), e.getValue());
                r.put("header", hObj);
            }
            return r.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ============================================================
    // buildCategoryUrl（不变）
    // ============================================================
    private String buildCategoryUrl(String tid, HashMap<String, String> extend) {
        String classVal = extend != null && extend.get("class") != null ? extend.get("class") : "";
        String storyVal = extend != null && extend.get("story") != null ? extend.get("story") : "";
        String areaVal  = extend != null && extend.get("area") != null ? extend.get("area") : "";
        String yearVal  = extend != null && extend.get("year") != null ? extend.get("year") : "";
        String sortVal  = extend != null && extend.get("sort") != null ? extend.get("sort") : "";
        int pg = 1;
        if (extend != null && extend.get("page") != null) {
            try { pg = Integer.parseInt(extend.get("page")); } catch (Exception ignored) {}
        }

        String mainType = TextUtils.isEmpty(tid) ? "dianying" : tid;

        if (!TextUtils.isEmpty(classVal)) {
            String[] validTypes = {
                "dongzuo", "xiju", "aiqing", "kehuan", "jilu", "zhanzheng",
                "juqing", "lishi", "fanzui", "kongbu", "qihuan", "donghua",
                "guochan", "gangju", "zilei10", "hanju", "riju", "taiju", "meiju", "haiwaiju",
                "dalu", "gangtai", "rihan", "hiwai",
                "guoman", "riman", "oumei"
            };
            for (String t : validTypes) {
                if (t.equals(classVal)) { mainType = classVal; break; }
            }
        }

        StringBuilder url = new StringBuilder("/show/").append(mainType);

        url.append(TextUtils.isEmpty(areaVal) ? "-" : "-" + urlEncode(areaVal));
        url.append(("time".equals(sortVal) || "hits".equals(sortVal) || "score".equals(sortVal)) ? "-" + sortVal : "-");
        url.append(TextUtils.isEmpty(storyVal) ? "-" : "-" + urlEncode(storyVal));

        int dashCount = 0;
        for (int i = 0; i < url.length(); i++) if (url.charAt(i) == '-') dashCount++;
        int needDashes = 8 - dashCount;
        if (needDashes < 0) needDashes = 0;
        for (int i = 0; i < needDashes; i++) url.append('-');

        if (pg > 1) url.append(pg);
        url.append("---");

        if (!TextUtils.isEmpty(yearVal)) url.append(yearVal);

        url.append(".html");
        return url.toString();
    }

    // ============================================================
    // extractVodList（用静态 Pattern）
    // ============================================================
    private List<JSONObject> extractVodList(String html) {
        List<JSONObject> list = new ArrayList<>();
        if (html == null || html.isEmpty()) return list;
        try {
            Matcher m = VOD_LIST_PATTERN.matcher(html);
            Set<String> seen = new LinkedHashSet<>();
            while (m.find()) {
                String vid = fixUrl(m.group(1));
                if (seen.contains(vid)) continue;
                seen.add(vid);

                JSONObject o = new JSONObject();
                o.put("vod_id", vid);
                o.put("vod_name", m.group(2) == null ? "" : m.group(2).trim());
                o.put("vod_pic", fixUrl(m.group(4)));
                o.put("vod_remarks", m.group(3) == null ? "" : m.group(3).trim());
                o.put("vod_actor", "");
                list.add(o);
            }
        } catch (Exception e) {
            SpiderDebug.log("extractVodList error: " + e.getMessage());
        }
        return list;
    }

    // ============================================================
    // extractTabs（用静态 Pattern）
    // ============================================================
    private List<String> extractTabs(String html) {
        List<String> tabs = new ArrayList<>();

        Matcher m = TAB_PATTERN.matcher(html);
        while (m.find()) {
            String name = m.group(1).trim();
            if (!name.isEmpty() && !tabs.contains(name)) tabs.add(name);
        }

        if (tabs.isEmpty()) {
            Matcher m2 = TAB_ALT_PATTERN.matcher(html);
            while (m2.find()) {
                String name = m2.group(1).trim();
                if (!name.isEmpty() && !tabs.contains(name)) tabs.add(name);
            }
        }

        SpiderDebug.log("提取到线路: " + tabs);
        return tabs;
    }

    // ============================================================
    // extractEpisodes（用静态 Pattern）
    // ============================================================
    private List<String[]> extractEpisodes(String html) {
        List<String[]> eps = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        Matcher m = EPISODE_PATTERN.matcher(html);
        while (m.find()) {
            String url = fixUrl(m.group(1));
            String name = m.group(2).trim();
            if (name.isEmpty()) name = "第1集";
            if (!seen.contains(url)) {
                seen.add(url);
                eps.add(new String[]{name, url});
            }
        }

        if (eps.isEmpty()) {
            Matcher m2 = EPISODE_ALT_PATTERN.matcher(html);
            while (m2.find()) {
                String url = fixUrl(m2.group(1));
                String name = m2.group(2).trim();
                if (name.isEmpty()) name = "第1集";
                if (!seen.contains(url)) {
                    seen.add(url);
                    eps.add(new String[]{name, url});
                }
            }
        }

        return eps;
    }

    // ============================================================
    // extractSids（用静态 Pattern）
    // ============================================================
    private List<Integer> extractSids(List<String[]> episodes) {
        TreeSet<Integer> set = new TreeSet<>();
        for (String[] ep : episodes) {
            Matcher m = SID_PATTERN.matcher(ep[1]);
            if (m.find()) {
                try { set.add(Integer.parseInt(m.group(1))); } catch (Exception ignored) {}
            }
        }
        return new ArrayList<>(set);
    }

    // ============================================================
    // getVodDetail（用静态 Pattern）
    // ============================================================
    private JSONObject getVodDetail(String vodId) {
        try {
            String url = vodId.startsWith("http") ? vodId : fixUrl(vodId);
            String html = fetchHtml(url);
            if (html.isEmpty()) return null;

            JSONObject info = new JSONObject();
            info.put("vod_id", vodId);

            String name = group("<h1[^>]*>([^<]+)</h1>", html, 1);
            info.put("vod_name", name.isEmpty() ? "未知影片" : name.trim());

            String pic = group("<div[^>]*class=\"[^\"]*module-item-pic[^\"]*\"[^>]*>\\s*<img[^>]*data-original=\"([^\"]+)\"", html, 1);
            info.put("vod_pic", pic.isEmpty() ? "" : fixUrl(pic));

            String dir = group("导演：</span>\\s*<div[^>]*class=\"[^\"]*module-info-item-content[^\"]*\"[^>]*>([\\s\\S]*?)</div>", html, 1);
            info.put("vod_director", cleanHtml(dir));

            String actorBlock = group("主演：</span>\\s*<div[^>]*class=\"[^\"]*module-info-item-content[^\"]*\"[^>]*>([\\s\\S]*?)</div>", html, 1);
            if (!actorBlock.isEmpty()) {
                List<String> actorList = new ArrayList<>();
                Matcher aM = Pattern.compile("<a[^>]*>([^<]+)</a>").matcher(actorBlock);
                while (aM.find()) actorList.add(aM.group(1).trim());
                if (actorList.isEmpty()) actorList.add(cleanHtml(actorBlock));
                info.put("vod_actor", String.join(",", actorList));
            } else {
                info.put("vod_actor", "");
            }

            String year = group("<a[^>]*title=\"(\\d{4})\"[^>]*>", html, 1);
            info.put("vod_year", year);

            String area = group("地区：</span>\\s*<div[^>]*class=\"[^\"]*module-info-item-content[^\"]*\"[^>]*>([^<]+)</div>", html, 1);
            info.put("vod_area", area.trim());

            String remarks = group("更新：</span>\\s*<div[^>]*class=\"[^\"]*module-info-item-content[^\"]*\"[^>]*>([^<]+)</div>", html, 1);
            info.put("vod_remarks", remarks.trim());

            String content = group("<div[^>]*class=\"[^\"]*module-info-introduction-content[^\"]*\"[^>]*>[\\s\\S]*?<p>([\\s\\S]*?)</p>", html, 1);
            info.put("vod_content", cleanHtml(content));

            List<String> tabs = extractTabs(html);
            List<String[]> episodes = extractEpisodes(html);
            List<Integer> sids = extractSids(episodes);

            SpiderDebug.log("剧集数: " + episodes.size());
            SpiderDebug.log("SID列表: " + sids);

            if (!episodes.isEmpty() && !sids.isEmpty()) {
                List<String> playFrom = new ArrayList<>();
                List<String> playUrlList = new ArrayList<>();

                for (int i = 0; i < sids.size(); i++) {
                    int sid = sids.get(i);
                    List<String> eps = new ArrayList<>();
                    for (String[] ep : episodes) {
                        Matcher m = SID_PATTERN.matcher(ep[1]);
                        if (m.find() && Integer.parseInt(m.group(1)) == sid) {
                            eps.add(ep[0] + "$" + ep[1]);
                        }
                    }
                    if (!eps.isEmpty()) {
                        playFrom.add(i < tabs.size() ? tabs.get(i) : ("线路" + (i + 1)));
                        playUrlList.add(String.join("#", eps));
                    }
                }

                if (!playFrom.isEmpty()) {
                    info.put("vod_play_from", String.join("$$$", playFrom));
                    info.put("vod_play_url", String.join("$$$", playUrlList));
                    SpiderDebug.log("播放列表: " + playFrom);
                }
            }

            if (!info.has("vod_play_from") || info.optString("vod_play_from").isEmpty()) {
                if (!episodes.isEmpty()) {
                    Map<Integer, List<String>> sidMap = new LinkedHashMap<>();
                    for (String[] ep : episodes) {
                        Matcher m = SID_PATTERN.matcher(ep[1]);
                        if (m.find()) {
                            int sid = Integer.parseInt(m.group(1));
                            sidMap.computeIfAbsent(sid, k -> new ArrayList<>()).add(ep[0] + "$" + ep[1]);
                        }
                    }
                    List<Integer> keys = new ArrayList<>(sidMap.keySet());
                    java.util.Collections.sort(keys);
                    List<String> pf = new ArrayList<>();
                    List<String> pu = new ArrayList<>();
                    for (int i = 0; i < keys.size(); i++) {
                        pf.add(i < tabs.size() ? tabs.get(i) : ("线路" + (i + 1)));
                        pu.add(String.join("#", sidMap.get(keys.get(i))));
                    }
                    if (!pf.isEmpty()) {
                        info.put("vod_play_from", String.join("$$$", pf));
                        info.put("vod_play_url", String.join("$$$", pu));
                    }
                }
            }

            return info;
        } catch (Exception e) {
            SpiderDebug.log("getVodDetail error: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // homeContent（不变）
    // ============================================================
    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();

            JSONArray classes = new JSONArray();
            String[][] cls = {
                {"dianying", "电影"}, {"dianshiju", "电视剧"},
                {"zongyi", "综艺"}, {"dongman", "动漫"}
            };
            for (String[] c : cls) {
                JSONObject o = new JSONObject();
                o.put("type_id", c[0]);
                o.put("type_name", c[1]);
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

    // ============================================================
    // buildFilters（不变）
    // ============================================================
    private JSONObject buildFilters() throws Exception {
        JSONObject filters = new JSONObject();
        String[] cats = {"dianying", "dianshiju", "zongyi", "dongman"};
        for (String cat : cats) {
            JSONArray arr = new JSONArray();
            arr.put(filterGroup("class", "类型", TYPE_FILTERS(cat)));
            arr.put(filterGroup("story", "剧情", STORY_FILTERS(cat)));
            arr.put(filterGroup("area",  "地区", AREA_FILTERS(cat)));
            arr.put(filterGroup("year",  "年份", YEAR_FILTERS()));
            arr.put(filterGroup("sort",  "排序", SORT_FILTERS()));
            filters.put(cat, arr);
        }
        return filters;
    }

    private JSONArray TYPE_FILTERS(String cat) throws Exception {
        String[][] dy = {
            {"", "全部"}, {"dongzuo", "动作"}, {"xiju", "喜剧"}, {"aiqing", "爱情"},
            {"kehuan", "科幻"}, {"jilu", "纪录传记"}, {"zhanzheng", "战争灾难"},
            {"juqing", "家庭剧情"}, {"lishi", "古装历史"}, {"fanzui", "犯罪悬疑"},
            {"kongbu", "惊悚恐怖"}, {"qihuan", "奇幻冒险"}, {"donghua", "动画影院"}
        };
        String[][] ds = {
            {"", "全部"}, {"guochan", "国产"}, {"gangju", "港剧"}, {"zilei10", "台剧"},
            {"hanju", "韩剧"}, {"riju", "日剧"}, {"taiju", "泰剧"}, {"meiju", "欧美"},
            {"haiwaiju", "海外"}
        };
        String[][] zy = {
            {"", "全部"}, {"dalu", "大陆"}, {"gangtai", "港台"}, {"rihan", "日韩"}, {"hiwai", "海外"}
        };
        String[][] dm = {
            {"", "全部"}, {"guoman", "国漫"}, {"riman", "日漫"}, {"oumei", "欧美"}
        };
        String[][] data;
        if ("dianying".equals(cat)) data = dy;
        else if ("dianshiju".equals(cat)) data = ds;
        else if ("zongyi".equals(cat)) data = zy;
        else data = dm;

        JSONArray arr = new JSONArray();
        for (String[] kv : data) {
            JSONObject o = new JSONObject();
            o.put("v", kv[0]);
            o.put("n", kv[1]);
            arr.put(o);
        }
        return arr;
    }

    private JSONArray STORY_FILTERS(String cat) throws Exception {
        String[][] dy = {
            {"", "全部"}, {"喜剧", "喜剧"}, {"爱情", "爱情"}, {"恐怖", "恐怖"},
            {"动作", "动作"}, {"科幻", "科幻"}, {"剧情", "剧情"}, {"战争", "战争"},
            {"警匪", "警匪"}, {"犯罪", "犯罪"}, {"动画", "动画"}, {"奇幻", "奇幻"},
            {"武侠", "武侠"}, {"冒险", "冒险"}, {"枪战", "枪战"}, {"悬疑", "悬疑"},
            {"惊悚", "惊悚"}, {"经典", "经典"}, {"青春", "青春"}, {"文艺", "文艺"},
            {"微电影", "微电影"}, {"古装", "古装"}, {"历史", "历史"}, {"运动", "运动"},
            {"农村", "农村"}, {"儿童", "儿童"}, {"网络电影", "网络电影"}
        };
        String[][] ds = {
            {"", "全部"}, {"古装", "古装"}, {"战争", "战争"}, {"青春偶像", "青春偶像"},
            {"喜剧", "喜剧"}, {"家庭", "家庭"}, {"犯罪", "犯罪"}, {"动作", "动作"},
            {"奇幻", "奇幻"}, {"剧情", "剧情"}, {"历史", "历史"}, {"经典", "经典"},
            {"乡村", "乡村"}, {"情景", "情景"}, {"商战", "商战"}, {"网剧", "网剧"}
        };
        String[][] zy = {
            {"", "全部"}, {"选秀", "选秀"}, {"情感", "情感"}, {"访谈", "访谈"},
            {"播报", "播报"}, {"旅游", "旅游"}, {"音乐", "音乐"}, {"美食", "美食"},
            {"纪实", "纪实"}, {"曲艺", "曲艺"}, {"生活", "生活"}, {"游戏互动", "游戏互动"},
            {"财经", "财经"}, {"求职", "求职"}
        };
        String[][] dm = {
            {"", "全部"}, {"情感", "情感"}, {"科幻", "科幻"}, {"热血", "热血"},
            {"推理", "推理"}, {"搞笑", "搞笑"}, {"冒险", "冒险"}, {"萝莉", "萝莉"},
            {"校园", "校园"}, {"动作", "动作"}, {"机战", "机战"}, {"运动", "运动"},
            {"战争", "战争"}, {"少年", "少年"}, {"少女", "少女"}, {"社会", "社会"},
            {"原创", "原创"}, {"亲子", "亲子"}, {"益智", "益智"}, {"励志", "励志"},
            {"其他", "其他"}
        };
        String[][] data;
        if ("dianying".equals(cat)) data = dy;
        else if ("dianshiju".equals(cat)) data = ds;
        else if ("zongyi".equals(cat)) data = zy;
        else data = dm;

        JSONArray arr = new JSONArray();
        for (String[] kv : data) {
            JSONObject o = new JSONObject();
            o.put("v", kv[0]);
            o.put("n", kv[1]);
            arr.put(o);
        }
        return arr;
    }

    private JSONArray AREA_FILTERS(String cat) throws Exception {
        String[][] dy = {
            {"", "全部"}, {"大陆", "大陆"}, {"香港", "香港"}, {"台湾", "台湾"},
            {"美国", "美国"}, {"法国", "法国"}, {"英国", "英国"}, {"日本", "日本"},
            {"韩国", "韩国"}, {"德国", "德国"}, {"泰国", "泰国"}, {"印度", "印度"},
            {"意大利", "意大利"}, {"西班牙", "西班牙"}, {"加拿大", "加拿大"}, {"其他", "其他"}
        };
        String[][] ds = {
            {"", "全部"}, {"中国大陆", "中国大陆"}, {"中国香港", "中国香港"},
            {"中国台湾", "中国台湾"}, {"美国", "美国"}, {"韩国", "韩国"}, {"日本", "日本"},
            {"泰国", "泰国"}, {"新加坡", "新加坡"}, {"德国", "德国"}, {"马来西亚", "马来西亚"},
            {"印度", "印度"}, {"英国", "英国"}, {"法国", "法国"}, {"加拿大", "加拿大"},
            {"西班牙", "西班牙"}, {"俄罗斯", "俄罗斯"}, {"其它", "其它"}
        };
        String[][] zy = {
            {"", "全部"}, {"内地", "内地"}, {"港台", "港台"}, {"日韩", "日韩"}, {"欧美", "欧美"}
        };
        String[][] dm = {
            {"", "全部"}, {"国产", "国产"}, {"日本", "日本"}, {"欧美", "欧美"}, {"其他", "其他"}
        };
        String[][] data;
        if ("dianying".equals(cat)) data = dy;
        else if ("dianshiju".equals(cat)) data = ds;
        else if ("zongyi".equals(cat)) data = zy;
        else data = dm;

        JSONArray arr = new JSONArray();
        for (String[] kv : data) {
            JSONObject o = new JSONObject();
            o.put("v", kv[0]);
            o.put("n", kv[1]);
            arr.put(o);
        }
        return arr;
    }

    private JSONArray YEAR_FILTERS() throws Exception {
        String[] years = {"", "2027", "2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018"};
        JSONArray arr = new JSONArray();
        for (String y : years) {
            JSONObject o = new JSONObject();
            o.put("v", y);
            o.put("n", y.isEmpty() ? "全部" : y);
            arr.put(o);
        }
        return arr;
    }

    private JSONArray SORT_FILTERS() throws Exception {
        String[][] data = {
            {"", "默认"}, {"time", "时间排序"}, {"hits", "人气排序"}, {"score", "评分排序"}
        };
        JSONArray arr = new JSONArray();
        for (String[] kv : data) {
            JSONObject o = new JSONObject();
            o.put("v", kv[0]);
            o.put("n", kv[1]);
            arr.put(o);
        }
        return arr;
    }

    private JSONObject filterGroup(String key, String name, JSONArray values) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("key", key);
        obj.put("name", name);
        obj.put("value", values);
        return obj;
    }

    // ============================================================
    // homeVideoContent（不变）
    // ============================================================
    @Override
    public String homeVideoContent() {
        try {
            String html = fetchHtml(host + "/");
            List<JSONObject> list = extractVodList(html);
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
            HashMap<String, String> ext = extend == null ? new HashMap<>() : new HashMap<>(extend);
            ext.put("page", String.valueOf(Integer.parseInt(pg)));

            String urlPath = buildCategoryUrl(tid, ext);
            String url = host + urlPath;
            SpiderDebug.log("category URL: " + url);

            String html = fetchHtml(url);
            List<JSONObject> list = extractVodList(html);
            SpiderDebug.log("category list size: " + list.size());

            int pagecount = 1;
            Matcher m = PAGE_LINK_PATTERN.matcher(html);
            int maxPage = 1;
            while (m.find()) {
                try {
                    int n = Integer.parseInt(m.group(1));
                    if (n > maxPage) maxPage = n;
                } catch (Exception ignored) {}
            }
            if (maxPage > 1) pagecount = maxPage;

            int page = Integer.parseInt(pg);
            JSONArray arr = new JSONArray();
            for (JSONObject o : list) arr.put(o);

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("list", arr);
            result.put("pagecount", pagecount);
            result.put("limit", 40);
            result.put("total", pagecount * 40);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("categoryContent error: " + e.getMessage());
            return "";
        }
    }

    // ============================================================
    // detailContent（不变）
    // ============================================================
    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            JSONObject info = getVodDetail(id);
            if (info == null) {
                JSONObject r = new JSONObject();
                JSONArray arr = new JSONArray();
                JSONObject item = new JSONObject();
                item.put("vod_id", id);
                item.put("vod_name", "获取失败");
                item.put("vod_play_url", "");
                arr.put(item);
                r.put("list", arr);
                return r.toString();
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
            int page = Integer.parseInt(pg);
            String url = host + "/search/" + urlEncode(wd) + "-------------.html";
            SpiderDebug.log("search URL: " + url);

            String html = fetchHtml(url);
            List<JSONObject> list = extractVodList(html);

            JSONArray arr = new JSONArray();
            for (JSONObject o : list) arr.put(o);
            JSONObject result = new JSONObject();
            result.put("list", arr);
            result.put("page", page);
            result.put("pagecount", 1);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("searchContent error: " + e.getMessage());
            return "";
        }
    }

    // ============================================================
    // ★★ playerContent —— 完全保留原逻辑 ★★
    // ============================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            // ① 入参直链
            if (id != null && id.matches(".*\\.(m3u8|mp4|flv|mkv|webm|ts)(\\?.*)?$")) {
                return buildResult(0, id, getM3u8Headers());
            }

            // ② 抓播放页
            String pageUrl = id.startsWith("http") ? id : fixUrl(id);
            String html = fetchHtml(pageUrl);
            if (html.isEmpty()) {
                return buildResult(0, "", null);
            }

            // ③ 抠 player_aaaa
            String realUrl = "";
            String from = "";
            String pJson = find(PLAYER_AAAA_PATTERN, html);
            if (!pJson.isEmpty()) {
                try {
                    JSONObject pdata = new JSONObject(pJson);
                    realUrl = pdata.optString("url", "").replace("\\/", "/");
                    if (realUrl.startsWith("//")) realUrl = "https:" + realUrl;
                    from = pdata.optString("from", "");
                } catch (Exception e) {
                    SpiderDebug.log("player_aaaa parse error: " + e.getMessage());
                }
            }

            SpiderDebug.log("player.url = " + realUrl);
            SpiderDebug.log("player.from = " + from);

            // ④ 直链 m3u8/mp4 → parse:0
            if (!realUrl.isEmpty() && realUrl.matches(".*\\.(m3u8|mp4|flv|mkv|webm|ts)(\\?.*)?$")) {
                SpiderDebug.log("✅ 直链: " + realUrl);
                return buildResult(0, realUrl, getM3u8Headers());
            }

            // ⑤ 第三方 → v.dushe.online 解析
            if (!realUrl.isEmpty()) {
                String jxUrl = PROXY_HOST + "/?url=" + urlEncode(realUrl)
                             + "&t=" + urlEncode(from) + "&d=v2";
                SpiderDebug.log("→ v.dushe.online: " + jxUrl);

                String jxHtml = fetchHtml(jxUrl);
                if (jxHtml.isEmpty()) {
                    SpiderDebug.log("❌ v.dushe.online 空响应");
                    return buildResult(0, "", null);
                }

                String configUrl = find(CONFIG_URL_PATTERN, jxHtml);
                if (configUrl.isEmpty()) {
                    SpiderDebug.log("❌ 没抠到 config.url");
                    return buildResult(0, "", null);
                }
                SpiderDebug.log("config.url = " + configUrl);

                String apiUrl = PROXY_HOST + "/api.php";
                LinkedHashMap<String, String> postData = new LinkedHashMap<>();
                postData.put("url", configUrl);
                postData.put("time", "");
                postData.put("key", "");
                postData.put("token", "");

                Map<String, String> h = new HashMap<>();
                h.put("User-Agent", UA);
                h.put("Content-Type", "application/x-www-form-urlencoded");
                h.put("X-Requested-With", "XMLHttpRequest");
                h.put("Referer", jxUrl);

                String apiResp = "";
                try {
                    apiResp = OkHttp.post(apiUrl, postData, h).getBody();
                } catch (Exception e) {
                    SpiderDebug.log("❌ POST api.php error: " + e.getMessage());
                }

                if (apiResp == null || apiResp.isEmpty()) {
                    SpiderDebug.log("❌ api.php 空响应");
                    return buildResult(0, "", null);
                }
                SpiderDebug.log("api resp: " + (apiResp.length() > 300 ? apiResp.substring(0, 300) : apiResp));

                try {
                    JSONObject j = new JSONObject(apiResp);
                    if (j.optInt("code", 0) == 200 && j.has("url")) {
                        String m3u8 = j.optString("url").replace("\\/", "/");
                        SpiderDebug.log("✅✅ 解析成功: " + m3u8);
                        return buildResult(0, m3u8, getM3u8Headers());
                    }
                    SpiderDebug.log("❌ api code != 200");
                } catch (Exception e) {
                    SpiderDebug.log("❌ JSON error: " + e.getMessage());
                }
            }

            SpiderDebug.log("❌ 全部失败");
            return buildResult(0, "", null);
        } catch (Exception e) {
            SpiderDebug.log("playerContent error: " + e.getMessage());
            return "";
        }
    }

    // ============================================================
    // destroy（去掉 @Override）
    // ============================================================
    public void destroy() {
        SpiderDebug.log("DuShe destroy");
    }
}
