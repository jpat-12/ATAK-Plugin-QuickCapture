package com.atakmap.android.plugintemplate.topo;

/**
 * Immutable value object representing a resolved CALTopo/SARTopo connection.
 */
public final class CalTopoSession {

    public final String serverBase;   // e.g. "https://caltopo.com"
    public final String mapId;        // e.g. "A1B2C"
    public final String connectKey;   // e.g. "abc123xyz"

    public CalTopoSession(String serverBase, String mapId, String connectKey) {
        this.serverBase = serverBase;
        this.mapId      = mapId;
        this.connectKey = connectKey;
    }

    /** URL for position reporting — no auth header required, key is auth. */
    public String positionReportUrl(String deviceId, double lat, double lng,
                                     double altM, long epochMs) {
        return serverBase
                + "/api/v1/position/report/" + connectKey
                + "?id=" + encode(deviceId)
                + "&lat=" + lat
                + "&lng=" + lng
                + "&altitude=" + altM
                + "&time=" + epochMs;
    }

    /** Base URL for CRUD operations (requires HMAC auth header). */
    public String featureUrl(String featureType) {
        return serverBase + "/api/v1/map/" + mapId + "/" + featureType;
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    @Override
    public String toString() {
        return serverBase + "/m/" + mapId;
    }
}
