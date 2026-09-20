package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.okhttp.OkHttpUtil;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Qile（PiaoHua 旧版风格）
 */
public class Kanqiu extends Spider {

    private static final String DEFAULT_SITE = "https://www.88kanqiu.tw";
    private String siteUrl = DEFAULT_SITE;

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final String DEFAULT_PIC = "https://pic.imgdb.cn/item/657673d6c458853aeff94ab9.jpg";

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", UA);
        return header;
    }

    // ★ PiaoHua 风格：fetchText
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
        if (!TextUtils.isEmpty(extend)) siteUrl = extend.trim();
    }

    // ★ homeContent：手拼 JSON
    @Override
    public String homeContent(boolean filter) throws JSONException {
        JSONObject result = new JSONObject();
        try {
            JSONArray classes = new JSONArray();
            List<String> typeIds = Arrays.asList("", "1", "8", "21");
            List<String> typeNames = Arrays.asList("全部直播", "篮球直播", "足球直播", "其他直播");
            for (int i = 0; i < typeIds.size(); i++) {
                JSONObject o = new JSONObject();
                o.put("type_id", typeIds.get(i));
                o.put("type_name", typeNames.get(i));
                classes.put(o);
            }
            result.put("class", classes);

            String f = "{\"1\": [{\"key\": \"cateId\", \"name\": \"类型\", \"value\": [{\"n\": \"NBA\", \"v\": \"1\"}, {\"n\": \"CBA\", \"v\": \"2\"}, {\"n\": \"篮球综合\", \"v\": \"4\"}, {\"n\": \"纬来体育\", \"v\": \"21\"}]}],\"8\": [{\"key\": \"cateId\", \"name\": \"类型\", \"value\": [{\"n\": \"英超\", \"v\": \"8\"}, {\"n\": \"西甲\", \"v\": \"9\"}, {\"n\": \"意甲\", \"v\": \"10\"}, {\"n\": \"欧冠\", \"v\": \"12\"}, {\"n\": \"欧联\", \"v\": \"13\"}, {\"n\": \"德甲\", \"v\": \"14\"}, {\"n\": \"法甲\", \"v\": \"15\"}, {\"n\": \"欧国联\", \"v\": \"16\"}, {\"n\": \"足总杯\", \"v\": \"27\"}, {\"n\": \"国王杯\", \"v\": \"33\"}, {\"n\": \"中超\", \"v\": \"7\"}, {\"n\": \"亚冠\", \"v\": \"11\"}, {\"n\": \"足球综合\", \"v\": \"23\"}, {\"n\": \"欧协联\", \"v\": \"28\"}, {\"n\": \"美职联\", \"v\": \"26\"}]}], \"29\": [{\"key\": \"cateId\", \"name\": \"类型\", \"value\": [{\"n\": \"网球\", \"v\": \"29\"}, {\"n\": \"斯洛克\", \"v\": \"30\"}, {\"n\": \"MLB\", \"v\": \"38\"}, {\"n\": \"UFC\", \"v\": \"32\"}, {\"n\": \"NFL\", \"v\": \"25\"}, {\"n\": \"CCTV5\", \"v\": \"18\"}]}]}";
            result.put("filters", new JSONObject(f));
        } catch (Exception e) {
            SpiderDebug.log("homeContent error: " + e.getMessage());
        }
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        JSONObject result = new JSONObject();
        try {
            String cateId = extend != null && extend.get("cateId") != null ? extend.get("cateId") : tid;
            String urlPath = cateId == null || cateId.isEmpty() ? "" : String.format("/match/%s/live", cateId);
            Document doc = Jsoup.parse(fetchText(siteUrl + urlPath));
            List<JSONObject> list = parseVods(doc);
            JSONArray arr = new JSONArray();
            for (JSONObject o : list) arr.put(o);
            result.put("page", 1);
            result.put("pagecount", 1);
            result.put("limit", 0);
            result.put("total", list.size());
            result.put("list", arr);
        } catch (Exception e) {
            SpiderDebug.log("categoryContent error: " + e.getMessage());
        }
        return result.toString();
    }

    List<JSONObject> parseVods(Document doc) {
        List<JSONObject> list = new ArrayList<>();
        for (Element li : doc.select(".list-group-item.group-game-item")) {
            try {
                Element link = li.selectFirst(".pay-btn > a[href]");
                if (link == null) continue;
                String vid = resolveUrl(link.attr("href"));
                String name = li.select(".row.d-none").text();
                if (name.isEmpty()) name = li.text();
                Element image = li.selectFirst(".col-xs-1 img");
                String pic = image == null ? "" : image.attr("data-src").trim();
                if (pic.isEmpty() && image != null) pic = image.attr("src").trim();
                pic = pic.isEmpty() ? DEFAULT_PIC : resolveUrl(pic);
                String remark = link.text();

                JSONObject o = new JSONObject();
                o.put("vod_id", vid);
                o.put("vod_name", name);
                o.put("vod_pic", pic);
                o.put("vod_remarks", remark);
                list.add(o);
            } catch (Exception ignored) {}
        }
        return list;
    }

    private String resolveUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        String base = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
        return base + (url.startsWith("/") ? "" : "/") + url;
    }

    String getSourceUrl(String id) {
        return id.endsWith("/play") ? id.substring(0, id.length() - 5) + "/source" : id + "/source";
    }

    String extractPayload(String content) {
        try {
            String data = new JSONObject(content).optString("data");
            return data.length() > 8 ? data.substring(6, data.length() - 2) : "";
        } catch (JSONException e) {
            return "";
        }
    }

    // ★ detailContent：手拼 JSON
    @Override
    public String detailContent(List<String> ids) {
        try {
            if (ids.get(0).equals(siteUrl)) return errorResult("比赛尚未开始");
            String content = fetchText(getSourceUrl(ids.get(0)));
            String result = extractPayload(content);
            if (result.isEmpty()) return errorResult("比赛尚未开始");
            JSONArray linksArray;
            try {
                String json = new String(Base64.decode(result, Base64.DEFAULT));
                linksArray = new JSONObject(json).getJSONArray("links");
            } catch (Exception e) {
                return errorResult("比赛尚未开始");
            }
            List<String> vodItems = new ArrayList<>();
            for (int i = 0; i < linksArray.length(); i++) {
                JSONObject linkObject = linksArray.optJSONObject(i);
                if (linkObject == null) continue;
                String text = linkObject.optString("name");
                String href = linkObject.optString("url").replace("#", "***");
                vodItems.add(text + "$" + href);
            }
            JSONObject vod = new JSONObject();
            vod.put("vod_id", ids.get(0));
            vod.put("vod_play_from", "Qile");
            vod.put("vod_play_url", TextUtils.join("#", vodItems));

            JSONArray list = new JSONArray();
            list.put(vod);
            JSONObject r = new JSONObject();
            r.put("list", list);
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("detailContent error: " + e.getMessage());
            return errorResult("比赛尚未开始");
        }
    }

    private String errorResult(String msg) {
        try {
            JSONObject r = new JSONObject();
            JSONArray list = new JSONArray();
            r.put("list", list);
            r.put("msg", msg);
            return r.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ★ playerContent 一字未改
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            JSONObject result = new JSONObject();
            result.put("parse", 1);
            result.put("url", id.replace("***", "#"));
            result.put("header", new JSONObject(getHeader()));
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }
}