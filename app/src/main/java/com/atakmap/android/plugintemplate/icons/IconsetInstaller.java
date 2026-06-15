package com.atakmap.android.plugintemplate.icons;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Installs the single FieldTAK message marker icon.
 */
public final class IconsetInstaller {

    private static final String TAG             = "FieldTak.IconsetInstaller";
    private static final String ASSET_PATH      = "iconsets/FieldTAK Messages.zip";
    private static final String DEST_FILENAME   = "FieldTAK Messages.zip";
    private static final int    BUNDLED_VERSION = 2;
    private static final String PREFS           = "fieldtak_message_icon";
    private static final String KEY_VERSION     = "iconset_version";

    public static void install(Context pluginContext) {
        SharedPreferences prefs =
                // Use ATAK host context for prefs (plugin context prefs are wiped on update)
                pluginContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int installed = prefs.getInt(KEY_VERSION, 0);

        File atakIconsetsDir = new File(
                Environment.getExternalStorageDirectory(), "atak/iconsets");
        //noinspection ResultOfMethodCallIgnored
        atakIconsetsDir.mkdirs();

        File dest = new File(atakIconsetsDir, DEST_FILENAME);

        if (dest.exists() && installed >= BUNDLED_VERSION) {
            Log.d(TAG, "Iconset already at version " + installed + ", skipping");
            return;
        }

        try (InputStream in  = pluginContext.getAssets().open(ASSET_PATH);
             FileOutputStream out = new FileOutputStream(dest)) {

            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);

            prefs.edit().putInt(KEY_VERSION, BUNDLED_VERSION).apply();
            Log.i(TAG, "Installed iconset to " + dest.getAbsolutePath());

            // Tell ATAK to reload iconsets
            AtakBroadcast.getInstance().sendBroadcast(
                    new android.content.Intent("com.atakmap.app.REFRESH_ICONSET"));

        } catch (Exception e) {
            Log.e(TAG, "Failed to install iconset", e);
        }
    }

    private IconsetInstaller() {}
}
