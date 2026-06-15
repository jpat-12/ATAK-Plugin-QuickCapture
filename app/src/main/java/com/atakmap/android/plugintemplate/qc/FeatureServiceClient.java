package com.atakmap.android.plugintemplate.qc;

import com.atakmap.android.plugintemplate.qc.model.QcField;
import com.atakmap.coremap.log.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Submits location records to an ArcGIS Feature Service via the REST {@code addFeatures} API.
 *
 * <p>All methods are blocking — always call from a background thread.
 *
 * <h3>Tier 2 flow</h3>
 * <ol>
 *   <li>Fetch item data: {@code GET /sharing/rest/content/items/{itemId}/data?f=json&token=…}
 *       to resolve the Feature Service URL.
 *   <li>POST to {@code {featureServiceUrl}/addFeatures} with a JSON features array.
 * </ol>
 */
public class FeatureServiceClient {

    private static final String TAG     = "FieldTak.FeatureServiceClient";
    private static final int    TIMEOUT = 15_000; // ms
    private static final String UA      = "CAP-FieldTAK/1.0";

    /** ArcGIS Online sharing REST base URL. */
    private static final String AGOL_BASE = "https://www.arcgis.com/sharing/rest";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolves the Feature Service URL from an ArcGIS item ID by fetching the item's data JSON.
     *
     * @param itemId ArcGIS Online item ID.
     * @param token  User access token (may be empty for public items).
     * @return Feature Service URL string, or {@code null} on failure.
     */
    public String resolveFeatureServiceUrl(String itemId, String token) {
        String urlStr = AGOL_BASE + "/content/items/" + itemId + "/data?f=json";
        if (token != null && !token.isEmpty()) urlStr += "&token=" + encodeParam(token);

        try {
            String body = httpGet(urlStr);
            if (body == null) return null;

            // Look for "featureServiceUrl":"…" in the response JSON
            String key = "\"featureServiceUrl\"";
            int idx = body.indexOf(key);
            if (idx < 0) {
                Log.w(TAG, "featureServiceUrl not found in item data");
                return null;
            }
            int colon = body.indexOf(':', idx + key.length());
            int start = body.indexOf('"', colon + 1) + 1;
            int end   = body.indexOf('"', start);
            if (start <= 0 || end <= start) return null;
            return body.substring(start, end).replace("\\/", "/");

        } catch (Exception e) {
            Log.e(TAG, "resolveFeatureServiceUrl failed", e);
            return null;
        }
    }

    /**
     * Resolves a project name from an ArcGIS item.
     *
     * @param itemId ArcGIS Online item ID.
     * @param token  User access token (may be empty).
     * @return Project title, or {@code "Unknown Project"} on failure.
     */
    public String resolveProjectName(String itemId, String token) {
        String urlStr = AGOL_BASE + "/content/items/" + itemId + "?f=json";
        if (token != null && !token.isEmpty()) urlStr += "&token=" + encodeParam(token);

        try {
            String body = httpGet(urlStr);
            if (body == null) return "Unknown Project";

            String key = "\"title\"";
            int idx = body.indexOf(key);
            if (idx < 0) return "Unknown Project";
            int colon = body.indexOf(':', idx + key.length());
            int start = body.indexOf('"', colon + 1) + 1;
            int end   = body.indexOf('"', start);
            if (start <= 0 || end <= start) return "Unknown Project";
            return body.substring(start, end);

        } catch (Exception e) {
            return "Unknown Project";
        }
    }

    /**
     * Submits a single location record (with optional attributes) to a Feature Service layer.
     *
     * @param session   The resolved QC session.
     * @param lat       Latitude (decimal degrees, WGS-84).
     * @param lon       Longitude (decimal degrees, WGS-84).
     * @param altM      Altitude in metres (HAE); use {@link Double#NaN} if unavailable.
     * @param extraJson Optional JSON object string of additional field attributes, e.g.
     *                  {@code "\"notes\":\"test\""}, or {@code null}.
     * @return HTTP response code, or {@code -1} on network error.
     */
    public int submitLocation(QuickCaptureSession session,
                               double lat, double lon, double altM,
                               String extraJson) {
        try {
            // Auto-populate ArcGIS GNSS telemetry fields so the service gets proper
            // location metadata even when submitted from a non-Collector app.
            StringBuilder attrs = new StringBuilder();
            attrs.append(String.format(Locale.US,
                    "\"esrignss_latitude\":%.7f,\"esrignss_longitude\":%.7f", lat, lon));
            if (!Double.isNaN(altM)) {
                attrs.append(String.format(Locale.US, ",\"esrignss_altitude\":%.2f", altM));
            }
            if (extraJson != null && !extraJson.isEmpty()) {
                attrs.append(",").append(extraJson);
            }

            String zPart = Double.isNaN(altM) ? ""
                    : String.format(Locale.US, ",\"z\":%.2f", altM);

            String featuresJson = String.format(Locale.US,
                    "[{\"geometry\":{\"x\":%.7f,\"y\":%.7f%s,"
                    + "\"spatialReference\":{\"wkid\":4326}},"
                    + "\"attributes\":{%s}}]",
                    lon, lat, zPart, attrs);

            // Build POST body (form-encoded)
            StringBuilder body = new StringBuilder();
            body.append("features=").append(encodeParam(featuresJson));
            body.append("&f=json");
            if (session.token != null && !session.token.isEmpty()) {
                body.append("&token=").append(encodeParam(session.token));
            }

            String endpoint = session.addFeaturesUrl();
            int code = httpPost(endpoint, body.toString(),
                                "application/x-www-form-urlencoded");
            Log.d(TAG, "addFeatures → " + code + " at " + endpoint);
            return code;

        } catch (Exception e) {
            Log.e(TAG, "submitLocation failed", e);
            return -1;
        }
    }

    // ── Field resolution ──────────────────────────────────────────────────────

    /**
     * Resolves the human-readable name of a Feature Service layer directly from its REST URL.
     *
     * @param featureServiceUrl Layer REST URL (e.g. {@code .../FeatureServer/0}).
     * @param token             Access token (may be empty for public layers).
     * @return Layer name string, or {@code "Unknown Layer"} on failure.
     */
    public String resolveLayerName(String featureServiceUrl, String token) {
        String urlStr = featureServiceUrl + "?f=json";
        if (token != null && !token.isEmpty()) urlStr += "&token=" + encodeParam(token);
        try {
            String body = httpGet(urlStr);
            if (body == null) return "Unknown Layer";
            String name = jsonStr(body, "name");
            return (name != null && !name.isEmpty()) ? name : "Unknown Layer";
        } catch (Exception e) {
            return "Unknown Layer";
        }
    }

    /**
     * Fields that are auto-managed by ArcGIS — never shown to the user in the form.
     */
    private static final Set<String> SYSTEM_FIELDS = new HashSet<>(Arrays.asList(
            "objectid", "fid", "globalid", "global_id",
            "creationdate", "creator", "editdate", "editor",
            "shape", "shape__length", "shape__area",
            "shape_length", "shape_area"));

    /**
     * Field name prefixes that represent auto-collected sensor / GPS telemetry.
     * These are submitted automatically and never shown in the user form.
     */
    private static final String[] AUTO_PREFIXES = {
            "esrignss_",   // ArcGIS GNSS metadata (lat, lon, altitude, accuracy, …)
            "esrisnsr_",   // ArcGIS sensor metadata (azimuth, …)
    };

    /**
     * Individual field names that are auto-populated from the capture device
     * and should never appear in the user form.
     */
    private static final Set<String> AUTO_FIELDS = new HashSet<>(Arrays.asList(
            "camheading", "campitch", "camroll",
            "hfov", "vfov", "fardist", "neardist", "avghtag",
            "acquisitiondate", "app_version", "operating_system"));

    /**
     * Fetches a feature layer's field definitions and returns only those that are
     * user-editable (non-system, editable, not geometry/OID/GlobalID).
     *
     * @param featureServiceUrl The layer REST URL (e.g. {@code .../FeatureServer/0}).
     * @param token             Access token (may be empty).
     * @return Ordered list of user-visible fields; empty if none or on error.
     */
    public List<QcField> resolveLayerFields(String featureServiceUrl, String token) {
        List<QcField> result = new ArrayList<>();
        try {
            String urlStr = featureServiceUrl + "?f=json";
            if (token != null && !token.isEmpty()) urlStr += "&token=" + encodeParam(token);

            String body = httpGet(urlStr);
            if (body == null) return result;

            // Parse the "fields":[...] array with a simple walking parser
            int fieldsStart = body.indexOf("\"fields\"");
            if (fieldsStart < 0) return result;
            int arrStart = body.indexOf('[', fieldsStart);
            int arrEnd   = body.indexOf(']', arrStart);
            if (arrStart < 0 || arrEnd < 0) return result;

            String arr = body.substring(arrStart + 1, arrEnd);

            // Each field object: {...}
            int pos = 0;
            while (pos < arr.length()) {
                int objStart = arr.indexOf('{', pos);
                if (objStart < 0) break;
                int objEnd = findClosingBrace(arr, objStart);
                if (objEnd < 0) break;

                String obj = arr.substring(objStart + 1, objEnd);
                pos = objEnd + 1;

                String name     = jsonStr(obj, "name");
                String alias    = jsonStr(obj, "alias");
                String type     = jsonStr(obj, "type");
                boolean editable = !"false".equalsIgnoreCase(jsonStr(obj, "editable"));
                boolean nullable = !"false".equalsIgnoreCase(jsonStr(obj, "nullable"));
                int length = jsonInt(obj, "length");

                if (name == null || name.isEmpty()) continue;
                if (!editable) continue;

                String nameLower = name.toLowerCase(Locale.US);

                // Skip ArcGIS system fields
                if (SYSTEM_FIELDS.contains(nameLower)) continue;

                // Skip auto-populated GPS/sensor telemetry by prefix
                boolean autoPrefix = false;
                for (String pfx : AUTO_PREFIXES) {
                    if (nameLower.startsWith(pfx)) { autoPrefix = true; break; }
                }
                if (autoPrefix) continue;

                // Skip individual auto-populated fields (camera, app metadata)
                if (AUTO_FIELDS.contains(nameLower)) continue;

                // Skip geometry/OID/GlobalID field types
                if (type != null && (type.contains("OID") || type.contains("GlobalID")
                        || type.contains("Geometry"))) continue;

                result.add(new QcField(name, alias, type, nullable, length));
            }

        } catch (Exception e) {
            Log.e(TAG, "resolveLayerFields failed", e);
        }
        return result;
    }

    // ── JSON micro-parser helpers ─────────────────────────────────────────────

    /** Extracts the string value of a JSON key (first occurrence). */
    private static String jsonStr(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int vs = json.indexOf('"', colon + 1);
        if (vs < 0) return null;
        int ve = json.indexOf('"', vs + 1);
        if (ve < 0) return null;
        return json.substring(vs + 1, ve);
    }

    /** Extracts an int value of a JSON key. Returns -1 on failure. */
    private static int jsonInt(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return -1;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return -1;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end))
                || json.charAt(end) == '-')) end++;
        try { return Integer.parseInt(json.substring(start, end).trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    /** Finds the matching closing brace for a '{' at position {@code open}. */
    private static int findClosingBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = openConn(urlStr, "GET");
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "GET " + urlStr + " → " + code);
                return null;
            }
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private int httpPost(String urlStr, String body, String contentType) throws Exception {
        HttpURLConnection conn = openConn(urlStr, "POST");
        try {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            OutputStream out = conn.getOutputStream();
            out.write(bytes);
            out.flush();
            return conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection openConn(String urlStr, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(TIMEOUT);
        c.setReadTimeout(TIMEOUT);
        c.setRequestProperty("User-Agent", UA);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    private static String encodeParam(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
