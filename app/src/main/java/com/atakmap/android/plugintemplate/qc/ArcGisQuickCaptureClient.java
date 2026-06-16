package com.atakmap.android.plugintemplate.qc;

import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import com.atakmap.coremap.maps.coords.GeoPoint;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.caverock.androidsvg.SVG;

public final class ArcGisQuickCaptureClient {
    private static final int TIMEOUT = 20000;
    private static final Pattern ITEM_ID = Pattern.compile("(?i)(?<![0-9a-f])[0-9a-f]{32}(?![0-9a-f])");

    public QuickCaptureProject downloadProject(String source, String token) throws Exception {
        if (source.toLowerCase(Locale.US).contains("featureserver")) {
            JSONObject root = new JSONObject();
            root.put("dataSources", new JSONArray().put(new JSONObject()
                    .put("dataSourceId", "0").put("url", normalizeLayer(source))));
            return QuickCaptureProject.parse(source, "Feature Layer Capture", root);
        }
        ResolvedShareLink resolved = resolveShareLink(source);
        String itemId = itemId(resolved.url);
        if (itemId == null) itemId = itemId(resolved.body);
        if (itemId == null) {
            throw new Exception("Could not find an ArcGIS project item ID after resolving: " + resolved.url);
        }
        JSONObject metadata = new JSONObject(get("https://www.arcgis.com/sharing/rest/content/items/"
                + itemId + "?f=json" + token(token)));
        failOnArcGisError(metadata);
        String portal = metadata.optString("orgUrl", "https://www.arcgis.com");
        JSONObject data;
        try {
            // The mobile app downloads the packaged project resource. The /data
            // endpoint may contain an older/reduced designer representation.
            data = new JSONObject(get(portal + "/sharing/rest/content/items/" + itemId
                    + "/resources/qc.project.json" + queryToken(token)));
        } catch (Exception resourceError) {
            data = new JSONObject(get(portal + "/sharing/rest/content/items/" + itemId
                    + "/data?f=json" + token(token)));
            failOnArcGisError(data);
        }
        QuickCaptureProject project = QuickCaptureProject.parse(
                source, metadata.optString("title", "QuickCapture"), data);
        if (project.itemId == null || project.itemId.isEmpty()) project.itemId = itemId;
        JSONArray keywords = metadata.optJSONArray("typeKeywords");
        if (keywords != null) for (int i = 0; i < keywords.length(); i++) {
            String keyword = keywords.optString(i);
            if (keyword.startsWith("source-")) project.sourceItemId = keyword.substring(7);
        }
        return project;
    }

    public Bitmap downloadTemplateIcon(QuickCaptureProject project,
                                       QuickCaptureProject.Template template,
                                       String token, int sizePx) {
        if (template.image == null || template.image.isEmpty()) return null;
        String[] itemIds = {project.itemId, project.sourceItemId};
        for (String itemId : itemIds) {
            if (itemId == null || itemId.isEmpty()) continue;
            try {
                String url = "https://www.arcgis.com/sharing/rest/content/items/" + itemId
                        + "/resources/images/" + encPath(template.image) + "?f=json" + token(token);
                byte[] bytes = getBytes(url);
                if (template.image.toLowerCase(Locale.US).endsWith(".svg")) {
                    SVG svg = SVG.getFromString(new String(bytes, StandardCharsets.UTF_8));
                    Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
                    svg.setDocumentWidth(sizePx);
                    svg.setDocumentHeight(sizePx);
                    svg.renderToCanvas(new Canvas(bitmap));
                    return bitmap;
                }
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public void addFeature(String layerUrl, String token, GeoPoint point, JSONObject attributes)
            throws Exception {
        JSONObject geometry = new JSONObject().put("x", point.getLongitude()).put("y", point.getLatitude())
                .put("spatialReference", new JSONObject().put("wkid", 4326));
        if (!Double.isNaN(point.getAltitude())) geometry.put("z", point.getAltitude());
        JSONArray features = new JSONArray().put(new JSONObject()
                .put("geometry", geometry).put("attributes", attributes));
        String body = "f=json&features=" + enc(features.toString()) + token(token);
        JSONObject response = new JSONObject(post(normalizeLayer(layerUrl) + "/addFeatures", body));
        failOnArcGisError(response);
        JSONArray results = response.optJSONArray("addResults");
        if (results == null || results.length() == 0 || !results.getJSONObject(0).optBoolean("success")) {
            throw new Exception("ArcGIS rejected the capture: " + response);
        }
    }

    public static final class FeatureRecord {
        public final double lat;
        public final double lon;
        public final double alt;
        public final long objectId;
        public final JSONObject attributes;

        FeatureRecord(double lat, double lon, double alt, long objectId, JSONObject attributes) {
            this.lat = lat;
            this.lon = lon;
            this.alt = alt;
            this.objectId = objectId;
            this.attributes = attributes;
        }
    }

    public List<FeatureRecord> queryFeatures(String layerUrl, String token) throws Exception {
        String url = normalizeLayer(layerUrl) + "/query?where=1%3D1&outFields=*"
                + "&returnGeometry=true&outSR=4326&f=json" + token(token);
        JSONObject json = new JSONObject(get(url));
        failOnArcGisError(json);
        JSONArray features = json.optJSONArray("features");
        if (features == null) return Collections.emptyList();
        List<FeatureRecord> out = new ArrayList<>();
        for (int i = 0; i < features.length(); i++) {
            JSONObject feat = features.getJSONObject(i);
            JSONObject geom = feat.optJSONObject("geometry");
            JSONObject attrs = feat.optJSONObject("attributes");
            if (geom == null) continue;
            double x = geom.optDouble("x", Double.NaN);
            double y = geom.optDouble("y", Double.NaN);
            if (Double.isNaN(x) || Double.isNaN(y)) continue;
            long oid = -1;
            if (attrs != null) {
                oid = attrs.optLong("OBJECTID", attrs.optLong("objectid",
                        attrs.optLong("FID", -1)));
            }
            out.add(new FeatureRecord(y, x, geom.optDouble("z", 0), oid,
                    attrs != null ? attrs : new JSONObject()));
        }
        return out;
    }

    private String itemId(String source) {
        if (source == null || source.isEmpty()) return null;
        String trimmed = source.trim();
        Matcher anywhere = ITEM_ID.matcher(trimmed);
        if (anywhere.find()) return anywhere.group();
        try {
            Uri uri = Uri.parse(trimmed);
            String[] keys = {"itemId", "itemid", "appid", "appId", "projectId", "projectid", "id"};
            for (String key : keys) {
                String id = uri.getQueryParameter(key);
                if (id != null) {
                    Matcher match = ITEM_ID.matcher(id);
                    if (match.find()) return match.group();
                }
            }
            String decoded = URLDecoder.decode(trimmed, "UTF-8");
            if (!decoded.equals(trimmed)) {
                Matcher match = ITEM_ID.matcher(decoded);
                if (match.find()) return match.group();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * QuickCapture QR codes are often short web links. Follow redirects and retain
     * the landing-page body because Esri may place the app deep link in HTML/JS.
     */
    private ResolvedShareLink resolveShareLink(String source) throws Exception {
        if (!source.matches("(?i)^https?://.*")) return new ResolvedShareLink(source, source);
        HttpURLConnection connection = open(source, "GET");
        connection.setInstanceFollowRedirects(true);
        int code = connection.getResponseCode();
        String finalUrl = connection.getURL().toString();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        StringBuilder body = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && body.length() < 1_000_000) {
                    body.append(line);
                }
            }
        }
        connection.disconnect();
        if (code >= 400) throw new Exception("Could not resolve QR link (HTTP " + code + ")");
        return new ResolvedShareLink(finalUrl, body.toString());
    }

    private static final class ResolvedShareLink {
        final String url;
        final String body;
        ResolvedShareLink(String url, String body) {
            this.url = url;
            this.body = body;
        }
    }

    private String normalizeLayer(String url) {
        String result = url.replaceAll("/+$", "");
        return result.toLowerCase(Locale.US).endsWith("featureserver") ? result + "/0" : result;
    }

    private String token(String token) {
        return token == null || token.isEmpty() ? "" : "&token=" + enc(token);
    }

    private String queryToken(String token) {
        return token == null || token.isEmpty() ? "" : "?token=" + enc(token);
    }

    private void failOnArcGisError(JSONObject json) throws Exception {
        JSONObject error = json.optJSONObject("error");
        if (error != null) throw new Exception("ArcGIS error " + error.optInt("code") + ": "
                + error.optString("message"));
    }

    private String get(String url) throws Exception { return read(open(url, "GET")); }

    private byte[] getBytes(String url) throws Exception {
        HttpURLConnection connection = open(url, "GET");
        int code = connection.getResponseCode();
        if (code >= 400) {
            connection.disconnect();
            throw new Exception("HTTP " + code);
        }
        try (InputStream input = connection.getInputStream();
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally { connection.disconnect(); }
    }

    private String post(String url, String body) throws Exception {
        HttpURLConnection connection = open(url, "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
        return read(connection);
    }

    private HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);
        connection.setRequestProperty("User-Agent", "ATAK-QuickCapture/0.1");
        return connection;
    }

    private String read(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) throw new Exception("HTTP " + code);
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        } finally { connection.disconnect(); }
        if (code >= 400) throw new Exception("HTTP " + code + ": " + result);
        return result.toString();
    }

    private String enc(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); } catch (Exception e) { return value; }
    }

    private String encPath(String value) {
        return enc(value).replace("+", "%20");
    }
}
