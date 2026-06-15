package com.atakmap.android.plugintemplate.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.plugintemplate.FieldTakMapComponent;
import gov.tak.api.plugin.IServiceController;

/**
 * Plugin entry point — registered in plugin.xml.
 * Uses AbstractPlugin so we get AbstractPluginTool + AbstractMapComponent wiring.
 */
public class FieldTakLifecycle extends AbstractPlugin {

    public FieldTakLifecycle(IServiceController serviceController) {
        super(serviceController,
                new FieldTakTool(serviceController
                        .getService(PluginContextProvider.class)
                        .getPluginContext()),
                new FieldTakMapComponent());
    }
}
