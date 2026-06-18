package com.atakmap.android.plugintemplate.plugin;

import gov.tak.api.plugin.IServiceController;

/** @deprecated Renamed to {@link QuickCaptureLifecycle}. */
@Deprecated
public class FieldTakLifecycle extends QuickCaptureLifecycle {
    public FieldTakLifecycle(IServiceController serviceController) {
        super(serviceController);
    }
}
