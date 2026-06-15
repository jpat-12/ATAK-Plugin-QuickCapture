package com.atakmap.android.plugintemplate.qc;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists the current {@link QuickCaptureSession} across plugin restarts.
 */
public final class QcSessionStore {

    private static final String PREFS     = "fieldtak_qc";
    private static final String KEY_ITEM  = "item_id";
    private static final String KEY_URL   = "feature_url";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_NAME  = "project_name";

    private QcSessionStore() {}

    public static void save(Context ctx, QuickCaptureSession s) {
        prefs(ctx).edit()
                  .putString(KEY_ITEM,  s.itemId)
                  .putString(KEY_URL,   s.featureServiceUrl)
                  .putString(KEY_TOKEN, s.token)
                  .putString(KEY_NAME,  s.projectName)
                  .apply();
    }

    /** Returns a saved session or {@code null} if none is stored. */
    public static QuickCaptureSession load(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String item  = p.getString(KEY_ITEM,  null);
        String url   = p.getString(KEY_URL,   null);
        if (item == null || url == null) return null;
        String token = p.getString(KEY_TOKEN, "");
        String name  = p.getString(KEY_NAME,  "Unknown Project");
        return new QuickCaptureSession(item, url, token, name);
    }

    public static void clear(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
