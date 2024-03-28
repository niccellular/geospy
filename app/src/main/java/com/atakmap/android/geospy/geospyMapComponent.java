
package com.atakmap.android.geospy;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.geospy.plugin.R;

public class geospyMapComponent extends DropDownMapComponent {

    private static final String TAG = "geospyMapComponent";

    private Context pluginContext;
    private DropDownReceiver ddr;

    public void onCreate(final Context context, Intent intent, final MapView view) {

        context.setTheme(R.style.ATAKPluginTheme);
        pluginContext = context;

        ToolsPreferenceFragment.register(
                new ToolsPreferenceFragment.ToolPreference(
                        pluginContext.getString(R.string.preferences_title),
                        pluginContext.getString(R.string.preferences_summary),
                        pluginContext.getString(R.string.geospy_preferences),
                        pluginContext.getResources().getDrawable(R.drawable.ic_launcher),
                        new PluginPreferencesFragment(
                                pluginContext)));


        ddr = new geospyDropDownReceiver(view, context);

        Log.d(TAG, "registering the plugin filter");
        DocumentedIntentFilter ddFilter = new DocumentedIntentFilter();
        ddFilter.addAction(geospyDropDownReceiver.SHOW_PLUGIN);
        registerDropDownReceiver(ddr, ddFilter);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);
    }
}
