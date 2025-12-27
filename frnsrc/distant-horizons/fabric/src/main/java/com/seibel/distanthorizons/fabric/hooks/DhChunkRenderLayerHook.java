/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you cantml redistribute it and/or modify
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

import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.hooks.ChunkRenderLayerHooks;

/**
 * Hook implementation for chunk render layer rendering.
 * Replaces MixinChunkSectionsToRender.renderDeferredLayer.
 */
public class DhChunkRenderLayerHook implements ChunkRenderLayerHooks {
    @Override
    public void onBeforeRenderLayer(ChunkSectionLayerGroup layerGroup) {
        ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(
            ClientApi.RENDER_STATE.clientLevelWrapper, 
            Minecraft.getInstance().levelRenderer.level
        );

        if (layerGroup == ChunkSectionLayerGroup.TRANSLUCENT) {
            ClientApi.INSTANCE.renderFadeTransparent();
            ClientApi.INSTANCE.renderDeferredLodsForShaders();
        } else if (layerGroup == ChunkSectionLayerGroup.TRIPWIRE) {
            ClientApi.INSTANCE.renderFadeOpaque();
        }
    }
}
