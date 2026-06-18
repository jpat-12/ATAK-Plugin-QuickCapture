package com.atakmap.android.plugintemplate.plugin;

import android.content.Context;
import com.atak.plugins.impl.AbstractPluginTool;
import com.atakmap.android.quickcapture.plugin.R;

/**
 * Toolbar button — fires SHOW_FIELDTAK intent when tapped.
 */
public class FieldTakTool extends AbstractPluginTool {

    public FieldTakTool(Context context) {
        super(context,
                context.getString(R.string.app_name),
                context.getString(R.string.app_name),
                context.getResources().getDrawable(R.drawable.ic_launcher),
                "com.atakmap.android.quickcapture.SHOW_PLUGIN");
    }
}
