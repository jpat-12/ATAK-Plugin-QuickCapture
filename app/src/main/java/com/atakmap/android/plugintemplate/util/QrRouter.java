package com.atakmap.android.plugintemplate.util;

import android.net.Uri;

import com.atakmap.android.plugintemplate.topo.CalTopoSession;

/**
 * Parses a URL scanned from a QR code and determines whether it represents a
 * CALTopo/SARTopo map link or an ArcGIS Quick Capture project link.
 */
public final class QrRouter {

    private QrRouter() {}

    public enum Type { CALTOPO, QUICK_CAPTURE, UNKNOWN }

    public static final class ParseResult {
        public final Type   type;
        public final String raw;

        // CALTopo fields
        public final CalTopoSession topoSession; // non-null if type == CALTOPO

        // Quick Capture fields
        public final String qcItemId; // non-null if type == QUICK_CAPTURE
        public final String qcToken;  // may be null

        private ParseResult(Type type, String raw,
                            CalTopoSession topoSession,
                            String qcItemId, String qcToken) {
            this.type         = type;
            this.raw          = raw;
            this.topoSession  = topoSession;
            this.qcItemId     = qcItemId;
            this.qcToken      = qcToken;
        }
    }

    /**
     * Parses a raw URL string from a QR code.
     *
     * <p>Handled URL patterns:
     * <ul>
     *   <li>CALTopo: {@code https://caltopo.com/m/{mapId}?key={connectKey}}
     *   <li>SARTopo: {@code https://sartopo.com/m/{mapId}?key={connectKey}}
     *   <li>ArcGIS QC: {@code https://www.arcgis.com/apps/quickcapture/index.html?itemId={id}}
     *   <li>ArcGIS QC: {@code https://quickcapture.arcgis.com/{id}}
     *   <li>ArcGIS QC: {@code arcgis-quickcapture://?itemid={id}&token={token}}
     * </ul>
     */
    public static ParseResult parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return unknown(rawUrl);
        }

        String url = rawUrl.trim();

        // ── CALTopo / SARTopo ─────────────────────────────────────────────────
        if (isCaltopo(url)) {
            return parseCaltopo(url);
        }

        // ── ArcGIS Quick Capture ──────────────────────────────────────────────
        if (isQuickCapture(url)) {
            return parseQuickCapture(url);
        }

        return unknown(url);
    }

    // ── Detection ─────────────────────────────────────────────────────────────

    private static boolean isCaltopo(String url) {
        return url.contains("caltopo.com") || url.contains("sartopo.com");
    }

    private static boolean isQuickCapture(String url) {
        return url.contains("quickcapture.arcgis.com")
                || url.contains("arcgis.com/apps/quickcapture")
                || url.startsWith("arcgis-quickcapture://");
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private static ParseResult parseCaltopo(String url) {
        try {
            Uri uri = Uri.parse(url);

            // Determine server base: https://caltopo.com or https://sartopo.com
            String host       = uri.getHost();          // e.g. "caltopo.com"
            String serverBase = uri.getScheme() + "://" + host;

            // Path: /m/{mapId}
            String path  = uri.getPath();               // e.g. "/m/A1B2C"
            String mapId = null;
            if (path != null && path.startsWith("/m/")) {
                mapId = path.substring(3).replaceAll("[^A-Za-z0-9]", "");
            }

            String key = uri.getQueryParameter("key");

            if (mapId == null || mapId.isEmpty() || key == null || key.isEmpty()) {
                return unknown(url);
            }

            CalTopoSession session = new CalTopoSession(serverBase, mapId, key);
            return new ParseResult(Type.CALTOPO, url, session, null, null);

        } catch (Exception e) {
            return unknown(url);
        }
    }

    private static ParseResult parseQuickCapture(String url) {
        try {
            Uri uri = Uri.parse(url);
            String itemId = null;
            String token  = null;

            if (url.startsWith("arcgis-quickcapture://")) {
                // arcgis-quickcapture://?itemid=abc123&token=xyz
                itemId = uri.getQueryParameter("itemid");
                if (itemId == null) itemId = uri.getQueryParameter("itemId");
                token  = uri.getQueryParameter("token");

            } else if (url.contains("arcgis.com/apps/quickcapture")) {
                // https://www.arcgis.com/apps/quickcapture/index.html?itemId=abc123
                itemId = uri.getQueryParameter("itemId");
                if (itemId == null) itemId = uri.getQueryParameter("itemid");
                token  = uri.getQueryParameter("token");

            } else if (url.contains("quickcapture.arcgis.com")) {
                // https://quickcapture.arcgis.com/{shortcode}  or  ?itemId=…
                itemId = uri.getQueryParameter("itemId");
                if (itemId == null) itemId = uri.getQueryParameter("itemid");
                if (itemId == null) {
                    // Some share links embed item ID in the path
                    String path = uri.getPath();
                    if (path != null && path.length() > 1) {
                        itemId = path.replaceAll("[^A-Za-z0-9]", "");
                    }
                }
                token = uri.getQueryParameter("token");
            }

            if (itemId == null || itemId.isEmpty()) {
                return unknown(url);
            }

            return new ParseResult(Type.QUICK_CAPTURE, url, null,
                                   itemId, token != null ? token : "");

        } catch (Exception e) {
            return unknown(url);
        }
    }

    private static ParseResult unknown(String url) {
        return new ParseResult(Type.UNKNOWN, url, null, null, null);
    }
}
