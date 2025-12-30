/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.fabric.hooks;

import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.coreapi.ModInfo;
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
        ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.Convert(positionMatrix);
        ClientApi.RENDER_STATE.mcProjectionMatrix = McObjectConverter.Convert(projectionMatrix);
        
        // TODO move this into a common place
        ClientApi.RENDER_STATE.frameTime = Minecraft.getInstance().deltaTracker.getRealtimeDeltaTicks();
        
        LevelRenderer levelRenderer = Minecraft.getInstance().levelRenderer;
        ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(
            ClientApi.RENDER_STATE.clientLevelWrapper,
            levelRenderer.level
        );
        
        // handled here and in MixinChunkSectionsToRender (now DistantHorizonsChunkRenderHook)
    }

    @Override
    public void onBeforePrepareChunkRenders(Matrix4fc modelViewMatrix, double camX, double camY, double camZ) {
        //LOGGER.info("[DH-RENDER-HOOK] ========== prepareChunkRenders() CALLED ==========");
        //LOGGER.info("[DH-RENDER-HOOK] Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
        
        ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.Convert(modelViewMatrix);
        
        LevelRenderer levelRenderer = Minecraft.getInstance().levelRenderer;
        ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(
            ClientApi.RENDER_STATE.clientLevelWrapper,
            levelRenderer.level
        );
        
        //LOGGER.info("[DH-RENDER-HOOK] clientLevelWrapper: " + ClientApi.RENDER_STATE.clientLevelWrapper);
        //LOGGER.info("[DH-RENDER-HOOK] ClientApi.INSTANCE: " + ClientApi.INSTANCE);
        
        // only crash during development
        if (ModInfo.IS_DEV_BUILD) {
            //LOGGER.info("[DH-RENDER-HOOK] Development build - checking canRenderOrThrow()");
            try {
                ClientApi.RENDER_STATE.canRenderOrThrow();
                //LOGGER.info("[DH-RENDER-HOOK] canRenderOrThrow() passed");
            } catch (Exception ex) {
                LOGGER.error("[DH-RENDER-HOOK] canRenderOrThrow() failed: " + ex.getMessage(), ex);
                throw ex;
            }
        }
        
        //LOGGER.info("[DH-RENDER-HOOK] Calling ClientApi.INSTANCE.renderLods()");
        try {
            ClientApi.INSTANCE.renderLods();
            //LOGGER.info("[DH-RENDER-HOOK] ClientApi.INSTANCE.renderLods() completed successfully");
        } catch (Exception ex) {
            LOGGER.error("[DH-RENDER-HOOK] renderLods() failed: " + ex.getMessage(), ex);
        }
        //LOGGER.info("[DH-RENDER-HOOK] ========== prepareChunkRenders() COMPLETE ==========");
    }
}
