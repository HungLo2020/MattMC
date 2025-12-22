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


@Mixin(ChunkSectionsToRender.class)
public class MixinChunkSectionsToRender
{
	@Unique
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	// needs to fire at HEAD with a lower than normal order (less than 1000)
	// otherwise it will be canceled by Sodium
	@Inject(at = @At("HEAD"), method = "renderGroup", order = 800)
	private void renderDeferredLayer(ChunkSectionLayerGroup chunkSectionLayerGroup, CallbackInfo ci)
	{
		LOGGER.info("[DH-RENDER-LAYER] ========== RENDER GROUP CALLED ==========");
		LOGGER.info("[DH-RENDER-LAYER] Layer: " + chunkSectionLayerGroup);
		LOGGER.info("[DH-RENDER-LAYER] Thread: " + Thread.currentThread().getName());
		
		ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(ClientApi.RENDER_STATE.clientLevelWrapper, Minecraft.getInstance().levelRenderer.level);
		
		LOGGER.info("[DH-RENDER-LAYER] clientLevelWrapper: " + ClientApi.RENDER_STATE.clientLevelWrapper);
		
		if (chunkSectionLayerGroup == ChunkSectionLayerGroup.TRANSLUCENT)
		{
			LOGGER.info("[DH-RENDER-LAYER] TRANSLUCENT layer - rendering fade transparent and deferred LODs");
			try
			{
				ClientApi.INSTANCE.renderFadeTransparent();
				LOGGER.info("[DH-RENDER-LAYER] renderFadeTransparent() completed");
				ClientApi.INSTANCE.renderDeferredLodsForShaders();
				LOGGER.info("[DH-RENDER-LAYER] renderDeferredLodsForShaders() completed");
			}
			catch (Exception e)
			{
				LOGGER.error("[DH-RENDER-LAYER] Error rendering translucent: " + e.getMessage(), e);
			}
		}
		else if (chunkSectionLayerGroup == ChunkSectionLayerGroup.TRIPWIRE)
		{
			LOGGER.info("[DH-RENDER-LAYER] TRIPWIRE layer - rendering fade opaque");
			try
			{
				ClientApi.INSTANCE.renderFadeOpaque();
				LOGGER.info("[DH-RENDER-LAYER] renderFadeOpaque() completed");
			}
			catch (Exception e)
			{
				LOGGER.error("[DH-RENDER-LAYER] Error rendering tripwire: " + e.getMessage(), e);
			}
		}
		else
		{
			LOGGER.info("[DH-RENDER-LAYER] Other layer (" + chunkSectionLayerGroup + ") - no LOD rendering");
		}
		LOGGER.info("[DH-RENDER-LAYER] ========== RENDER GROUP COMPLETE ==========");
	}
	
	
	
}


