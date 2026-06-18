package com.atakmap.android.plugintemplate;

import android.content.Context;
import android.content.Intent;

import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;

/**
 * AbstractMapComponent — registered with ATAK's component system.
 * Creates and registers the DropDownReceiver.
 */
public class QuickCaptureMapComponent extends com.atakmap.android.maps.AbstractMapComponent {

    private QuickCaptureDropDownReceiver dropDown;

    @Override
    public void onCreate(Context context, Intent intent, MapView mapView) {
        Context pluginContext = context;
        dropDown = new QuickCaptureDropDownReceiver(mapView, pluginContext);

        AtakBroadcast.DocumentedIntentFilter filter =
                new AtakBroadcast.DocumentedIntentFilter();
        filter.addAction(QuickCaptureDropDownReceiver.SHOW,
                "Show the QuickCapture plugin panel");
        registerReceiver(context, dropDown, filter);
    }

    @Override
    public void onDestroyImpl(Context context, MapView mapView) {
        if (dropDown != null) {
            unregisterReceiver(context, dropDown);
            dropDown.dispose();
            dropDown = null;
        }
    }
}
