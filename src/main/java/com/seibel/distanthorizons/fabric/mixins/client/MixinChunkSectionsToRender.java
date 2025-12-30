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

package com.seibel.distanthorizons.fabric.mixins.client;

import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to inject DH rendering at specific chunk layer groups.
 * This must run with order = 800 to execute BEFORE Sodium's mixin (order 1000)
 * which cancels the renderGroup method when Sodium rendering is active.
 */
@Mixin(ChunkSectionsToRender.class)
public class MixinChunkSectionsToRender {
	@Unique
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	// needs to fire at HEAD with order = 800 (higher priority than default 1000)
	// so it runs before Sodium's mixin which would otherwise cancel the method
	@Inject(at = @At("HEAD"), method = "renderGroup", order = 800)
	private void renderDeferredLayer(ChunkSectionLayerGroup chunkSectionLayerGroup, CallbackInfo ci) {
		ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(
			ClientApi.RENDER_STATE.clientLevelWrapper, 
			Minecraft.getInstance().levelRenderer.level
		);

		if (chunkSectionLayerGroup == ChunkSectionLayerGroup.TRANSLUCENT) {
			try {
				ClientApi.INSTANCE.renderFadeTransparent();
				ClientApi.INSTANCE.renderDeferredLodsForShaders();
			} catch (Exception e) {
				LOGGER.error("[DH-RENDER-LAYER] Error rendering translucent: " + e.getMessage(), e);
			}
		} else if (chunkSectionLayerGroup == ChunkSectionLayerGroup.TRIPWIRE) {
			try {
				ClientApi.INSTANCE.renderFadeOpaque();
			} catch (Exception e) {
				LOGGER.error("[DH-RENDER-LAYER] Error rendering tripwire: " + e.getMessage(), e);
			}
		}
	}
}
