package com.seibel.distanthorizons.fabric.hooks;

import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
import com.seibel.distanthorizons.coreapi.ModInfo;
import net.irisshaders.iris.compat.dh.DHCompatInternal;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.hooks.LevelRendererHooks;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Hook implementation for Distant Horizons level rendering.
 * Replaces the mixin-based injection into LevelRenderer.renderLevel and prepareChunkRenders.
 */
public class DistantHorizonsLevelRenderHook implements LevelRendererHooks {
    private static final DhLogger LOGGER = new DhLoggerBuilder().build();

    @Override
    public void onBeforeRenderLevel(Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
            return;
        }
        seedRenderState(positionMatrix, projectionMatrix);

        // handled here and in MixinChunkSectionsToRender (now DistantHorizonsChunkRenderHook)
    }

    /**
     * Invoked by the Rust Vulkan whole-frame shell because that shell does
     * not enter LevelRenderer's normal DH hook. This shares the normal DH
     * camera/world semantic setup, then asks ClientApi for a draw-free opaque
     * preflight. A rejected frame contributes no Java draw to the shell.
     */
    public static boolean collectRustOpaqueForWholeFrame(Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        seedRenderState(positionMatrix, projectionMatrix);
        return ClientApi.INSTANCE.collectRustOpaqueLodsForWholeFrame();
    }

    private static void seedRenderState(Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.Convert(positionMatrix);
        ClientApi.RENDER_STATE.mcProjectionMatrix = McObjectConverter.Convert(projectionMatrix);
        
        // TODO move this into a common place
        ClientApi.RENDER_STATE.frameTime = Minecraft.getInstance().deltaTracker.getRealtimeDeltaTicks();
        
        LevelRenderer levelRenderer = Minecraft.getInstance().levelRenderer;
        ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(
            ClientApi.RENDER_STATE.clientLevelWrapper,
            levelRenderer.level
        );
    }

    @Override
    public void onBeforePrepareChunkRenders(Matrix4fc modelViewMatrix, double camX, double camY, double camZ) {
        if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
            return;
        }
        ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.Convert(modelViewMatrix);
        
        LevelRenderer levelRenderer = Minecraft.getInstance().levelRenderer;
        ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(
            ClientApi.RENDER_STATE.clientLevelWrapper,
            levelRenderer.level
        );
        
        // only crash during development
        if (ModInfo.IS_DEV_BUILD) {
            try {
                ClientApi.RENDER_STATE.canRenderOrThrow();
            } catch (Exception ex) {
                LOGGER.error("[DH-RENDER-HOOK] canRenderOrThrow() failed: " + ex.getMessage(), ex);
                throw ex;
            }
	        }

		        try {
		            net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("distant-horizons.lod-render");
			            boolean deferTransparentRendering = !net.vulkanic.world.WorldRenderRoutePolicy
			                .currentDistantHorizonsOpaqueRoute().usesRustWholeFrameVulkan()
			                && DHCompatInternal.shouldUseShaderOverrides();
		            DhApiRenderProxy.INSTANCE.setDeferTransparentRendering(deferTransparentRendering);
		            ClientApi.INSTANCE.renderLods();
		        } catch (Exception ex) {
	            LOGGER.error("[DH-RENDER-HOOK] renderLods() failed: " + ex.getMessage(), ex);
	        } finally {
	            net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("distant-horizons.lod-render");
	        }
	    }
	}
