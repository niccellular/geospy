package com.atakmap.android.geospy.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.geospy.geospyMapComponent;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.plugin.IPlugin;


public class geospyLifecycle extends AbstractPlugin implements IPlugin {

    public geospyLifecycle(IServiceController serviceController) {
        super(serviceController, new geospyTool(serviceController.getService(PluginContextProvider.class).getPluginContext()), new geospyMapComponent());
    }
}