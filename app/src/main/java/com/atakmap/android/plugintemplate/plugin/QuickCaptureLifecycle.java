package com.atakmap.android.plugintemplate.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.plugintemplate.QuickCaptureMapComponent;
import gov.tak.api.plugin.IServiceController;

/**
 * Plugin entry point — registered in plugin.xml.
 * Uses AbstractPlugin so we get AbstractPluginTool + AbstractMapComponent wiring.
 */
public class QuickCaptureLifecycle extends AbstractPlugin {

    public QuickCaptureLifecycle(IServiceController serviceController) {
        super(serviceController,
                new QuickCaptureTool(serviceController
                        .getService(PluginContextProvider.class)
                        .getPluginContext()),
                new QuickCaptureMapComponent());
    }
}
