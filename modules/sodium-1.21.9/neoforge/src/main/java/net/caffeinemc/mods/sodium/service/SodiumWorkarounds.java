package net.caffeinemc.mods.sodium.service;

import net.minecraft.client.renderer.sodium.compatibility.checks.PreLaunchChecks;
import net.minecraft.client.renderer.sodium.compatibility.environment.probe.GraphicsAdapterProbe;
import net.minecraft.client.renderer.sodium.compatibility.workarounds.Workarounds;
import net.minecraft.client.renderer.sodium.compatibility.workarounds.nvidia.NvidiaWorkarounds;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;

public class SodiumWorkarounds implements GraphicsBootstrapper {
    @Override
    public String name() {
        return "sodium";
    }

    @Override
    public void bootstrap(String[] arguments) {
        net.minecraft.client.renderer.sodium.compatibility.checks.PreLaunchChecks.checkEnvironment();
        GraphicsAdapterProbe.findAdapters();
        Workarounds.init();

        // Context creation happens earlier on NeoForge, so we need to apply this now
        NvidiaWorkarounds.applyEnvironmentChanges();
    }
}
