package net.minecraft.hooks;

import net.minecraft.client.renderer.LightTexture;

/**
 * Hook interface for light texture updates.
 * Allows mods to react to lightmap updates.
 */
public interface LightTextureHooks {
    /**
     * Called after the light texture has been updated.
     * Allows mods to perform custom lightmap processing.
     * 
     * @param lightTexture The light texture that was updated
     * @param partialTicks Partial tick time for interpolation
     */
    void onLightTextureUpdated(LightTexture lightTexture, float partialTicks);
}
