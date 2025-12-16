package net.caffeinemc.mods.sodium.fabric;

import net.minecraft.client.renderer.sodium.compatibility.checks.PreLaunchChecks;
import net.minecraft.client.renderer.sodium.compatibility.environment.probe.GraphicsAdapterProbe;
import net.minecraft.client.renderer.sodium.compatibility.workarounds.Workarounds;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class SodiumPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        net.minecraft.client.renderer.sodium.compatibility.checks.PreLaunchChecks.checkEnvironment();
        GraphicsAdapterProbe.findAdapters();
        Workarounds.init();
    }
}
