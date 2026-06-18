package com.atakmap.android.plugintemplate;

import android.content.Context;
import com.atakmap.android.maps.MapView;

/** @deprecated Renamed to {@link QuickCaptureDropDownReceiver}. */
@Deprecated
public class FieldTakDropDownReceiver extends QuickCaptureDropDownReceiver {
    public FieldTakDropDownReceiver(MapView mapView, Context pluginContext) {
        super(mapView, pluginContext);
    }
}
