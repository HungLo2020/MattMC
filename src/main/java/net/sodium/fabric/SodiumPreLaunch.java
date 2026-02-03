package net.sodium.fabric;

import net.sodium.client.compatibility.checks.PreLaunchChecks;
import net.sodium.client.compatibility.environment.probe.GraphicsAdapterProbe;
import net.sodium.client.compatibility.workarounds.Workarounds;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class SodiumPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        PreLaunchChecks.checkEnvironment();
        GraphicsAdapterProbe.findAdapters();
        Workarounds.init();
    }
}
