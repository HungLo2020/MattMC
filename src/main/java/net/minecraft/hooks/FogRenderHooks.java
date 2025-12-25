package net.minecraft.hooks;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import org.joml.Vector4f;

/**
 * Hook interface for fog rendering customizations.
 * Allows mods to intercept fog parameter updates.
 */
public interface FogRenderHooks {
    /**
     * Called after fog parameters are calculated but before they are applied.
     * 
     * @param camera The camera
     * @param fogMode The fog mode
     * @param bl Unknown boolean parameter
     * @param deltaTracker Delta tracker for interpolation
     * @param partialTicks Partial tick time
     * @param level The client level
     * @param fogData The calculated fog data
     * @param fogColor The calculated fog color
     */
    default void onFogParametersCalculated(Camera camera, int fogMode, boolean bl, 
                                          DeltaTracker deltaTracker, float partialTicks, 
                                          ClientLevel level, FogData fogData, Vector4f fogColor) {}
    
    /**
     * Allows mods to provide fog parameters for shader rendering (for Iris compatibility).
     * Returns fog parameters object (typically FogParameters from Sodium).
     */
    default Object getFogParameters() {
        return null;
    }
}
