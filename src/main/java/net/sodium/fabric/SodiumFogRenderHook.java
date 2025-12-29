package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.hooks.FogRenderHooks;
import org.joml.Vector4f;

/**
 * Sodium implementation of FogRenderHooks.
 * Stores fog parameters for use by Sodium's rendering system.
 */
public class SodiumFogRenderHook implements FogRenderHooks {
    private static FogParameters cachedParameters = FogParameters.NONE;
    
    @Override
    public void onFogParametersCalculated(Camera camera, int fogMode, boolean bl,
                                         DeltaTracker deltaTracker, float partialTicks,
                                         ClientLevel level, FogData fogData, Vector4f fogColor) {
        // Store fog parameters for Sodium's rendering system
        cachedParameters = new FogParameters(
            fogColor.x, fogColor.y, fogColor.z, fogColor.w,
            fogData.environmentalStart, fogData.environmentalEnd,
            fogData.renderDistanceStart, fogData.renderDistanceEnd
        );
    }
    
    /**
     * Get the cached fog parameters.
     * @return The most recently calculated fog parameters
     */
    public static FogParameters getFogParameters() {
        return cachedParameters;
    }
}
