package net.minecraft.hooks;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;

/**
 * Hook interface for entity rendering customizations.
 * Allows mods to optimize or customize entity shadow rendering.
 */
public interface EntityRenderHooks {
    /**
     * Called to render entity shadows.
     * 
     * @param submitNodeCollection The submit node collection containing shadow data
     * @param bufferSource The buffer source for rendering
     * @return true to cancel vanilla shadow rendering, false to allow it
     */
    default boolean onRenderEntityShadows(SubmitNodeCollection submitNodeCollection, 
                                         MultiBufferSource.BufferSource bufferSource) {
        return false;
    }
}
