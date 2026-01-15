package net.distanthorizons.fabric.hooks;

import net.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import net.distanthorizons.core.api.internal.ClientApi;
import net.distanthorizons.core.logging.DhLogger;
import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.hooks.ChunkRenderLayerHooks;

/**
 * Hook implementation for Distant Horizons chunk rendering.
 * Replaces the mixin-based injection into ChunkSectionsToRender.renderGroup.
 */
public class DistantHorizonsChunkRenderHook implements ChunkRenderLayerHooks {
    private static final DhLogger LOGGER = new DhLoggerBuilder().build();

    @Override
    public void onBeforeRenderLayer(ChunkSectionLayerGroup layerGroup) {
        ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(
            ClientApi.RENDER_STATE.clientLevelWrapper,
            Minecraft.getInstance().levelRenderer.level
        );

        if (layerGroup == ChunkSectionLayerGroup.TRANSLUCENT) {
            try {
                ClientApi.INSTANCE.renderFadeTransparent();
                ClientApi.INSTANCE.renderDeferredLodsForShaders();
            } catch (Exception e) {
                LOGGER.error("[DH-RENDER-LAYER] Error rendering translucent: " + e.getMessage(), e);
            }
        } else if (layerGroup == ChunkSectionLayerGroup.TRIPWIRE) {
            try {
                ClientApi.INSTANCE.renderFadeOpaque();
            } catch (Exception e) {
                LOGGER.error("[DH-RENDER-LAYER] Error rendering tripwire: " + e.getMessage(), e);
            }
        }
    }
}
