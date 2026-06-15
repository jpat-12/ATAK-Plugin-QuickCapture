package com.atakmap.android.plugintemplate.topo;

import com.atakmap.coremap.log.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Thin HTTP client for the CALTopo/SARTopo REST API.
 * All methods are blocking — always call from a background thread.
 */
public class CalTopoApiClient {

    private static final String TAG      = "CalTopoApiClient";
    private static final int    TIMEOUT  = 10_000; // ms
    private static final String UA       = "CAP-FieldTAK/1.0";

    /**
     * Sends a position report (GET).
     * @return HTTP response code, or -1 on IOException.
     */
    public int sendPositionReport(String url) {
        HttpURLConnection conn = null;
        try {
            conn = open(url, "GET");
            int code = conn.getResponseCode();
            Log.d(TAG, "Position report → " + code);
            return code;
        } catch (Exception e) {
            Log.e(TAG, "Position report failed: " + e.getMessage());
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Posts a JSON body to the given URL.
     * @return HTTP response code, or -1 on IOException.
     */
    public int postJson(String urlStr, String json, String authHeader, String expiresHeader) {
        HttpURLConnection conn = null;
        try {
            conn = open(urlStr, "POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            if (authHeader    != null) conn.setRequestProperty("Authorization", authHeader);
            if (expiresHeader != null) conn.setRequestProperty("X-Expires",     expiresHeader);

            byte[] body = json.getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(body.length);
            conn.getOutputStream().write(body);

            int code = conn.getResponseCode();
            Log.d(TAG, "POST → " + code);
            return code;
        } catch (Exception e) {
            Log.e(TAG, "POST failed: " + e.getMessage());
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // -------------------------------------------------------------------------

    private HttpURLConnection open(String urlStr, String method) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setRequestProperty("User-Agent", UA);
        return conn;
    }
}
