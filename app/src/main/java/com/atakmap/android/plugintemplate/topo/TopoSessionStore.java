package com.atakmap.android.plugintemplate.topo;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists the active CALTopo session across plugin restarts.
 */
public class TopoSessionStore {

    private static final String PREFS = "fieldtak_topo";
    private static final String KEY_SERVER  = "topo_server";
    private static final String KEY_MAP_ID  = "topo_map_id";
    private static final String KEY_KEY     = "topo_connect_key";
    private static final String KEY_INTERVAL= "topo_interval_s";
    private static final String KEY_AUTO    = "topo_auto_reconnect";

    private final SharedPreferences prefs;

    public TopoSessionStore(Context atakContext) {
        prefs = atakContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(CalTopoSession session) {
        prefs.edit()
             .putString(KEY_SERVER, session.serverBase)
             .putString(KEY_MAP_ID, session.mapId)
             .putString(KEY_KEY,    session.connectKey)
             .apply();
    }

    public void clear() {
        prefs.edit()
             .remove(KEY_SERVER)
             .remove(KEY_MAP_ID)
             .remove(KEY_KEY)
             .apply();
    }

    /** Returns a saved session, or null if none stored. */
    public CalTopoSession load() {
        String server = prefs.getString(KEY_SERVER, null);
        String mapId  = prefs.getString(KEY_MAP_ID, null);
        String key    = prefs.getString(KEY_KEY, null);
        if (server == null || mapId == null || key == null) return null;
        return new CalTopoSession(server, mapId, key);
    }

    public int getIntervalSeconds() {
        return prefs.getInt(KEY_INTERVAL, 15);
    }

    public void setIntervalSeconds(int s) {
        prefs.edit().putInt(KEY_INTERVAL, s).apply();
    }

    public boolean isAutoReconnect() {
        return prefs.getBoolean(KEY_AUTO, true);
    }

    public void setAutoReconnect(boolean v) {
        prefs.edit().putBoolean(KEY_AUTO, v).apply();
    }
}
