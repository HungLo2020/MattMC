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

package com.seibel.distanthorizons.neoforge.mixins.client;

#if MC_VER < MC_1_21_6
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
#else
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
	
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
#endif



import org.apache.logging.log4j.Logger;

import com.seibel.distanthorizons.neoforge.NeoforgeClientProxy;
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.coreapi.ModInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This class is used to mix in DH's rendering code
 * before Minecraft starts rendering blocks.
 * If this wasn't done, and we used Forge's
 * render last event, the LODs would render on top
 * of the normal terrain. <br><br>
 *
 * This is also the mixin for rendering the clouds
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer
{
	@Shadow
	#if MC_VER >= MC_1_20_4
			(remap = false)
	#endif
	private ClientLevel level;
	
	@Unique
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	
	#if MC_VER < MC_1_21_6
	@Inject(at = @At("HEAD"), method = "renderSectionLayer", cancellable = true)
	private void renderChunkLayer(RenderType renderType, double x, double y, double z, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, CallbackInfo callback)
	#else
	@Inject(at = @At("HEAD"), method = "renderLevel", cancellable = true)
	private void onRenderLevel(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, GpuBufferSlice gpuBufferSlice, Vector4f skyColor, boolean thinFog, CallbackInfo callback)
    #endif
	{
		#if MC_VER < MC_1_21_6
		// MC combined the model view and projection matricies
		ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.Convert(modelViewMatrix);
		ClientApi.RENDER_STATE.mcProjectionMatrix = McObjectConverter.Convert(projectionMatrix);
		#else
		ClientApi.RENDER_STATE.mcProjectionMatrix = McObjectConverter.Convert(projectionMatrix);
		#endif
	
		//LOGGER.info("\n\n" +
		//		"Level Mixin\n" +
		//		"Mc MVM: \n" + ClientApi.RENDER_STATE.mcModelViewMatrix.toString() + "\n" +
		//		"Mc Proj: \n" + ClientApi.RENDER_STATE.mcProjectionMatrix.toString()
		//);
		
		
		
		#if MC_VER < MC_1_21_1
		ClientApi.RENDER_STATE.frameTime = Minecraft.getInstance().getFrameTime();
		#elif MC_VER < MC_1_21_3
		ClientApi.RENDER_STATE.frameTime = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
		#else
		ClientApi.RENDER_STATE.frameTime = Minecraft.getInstance().deltaTracker.getRealtimeDeltaTicks();
		#endif
		
		
		#if MC_VER < MC_1_21_6
		
		// only crash during development
		if (ModInfo.IS_DEV_BUILD)
		{
			ClientApi.RENDER_STATE.canRenderOrThrow();
		}
		
		// render LODs
		if (renderType.equals(RenderType.solid()))
		{
			ClientApi.INSTANCE.renderLods(ClientLevelWrapper.getWrapper(this.level),
					ClientApi.RENDER_STATE.mcModelViewMatrix,
					ClientApi.RENDER_STATE.mcProjectionMatrix,
					ClientApi.RENDER_STATE.frameTime);
		} 
		else if (renderType.equals(RenderType.translucent())) 
		{
			ClientApi.INSTANCE.renderDeferredLodsForShaders(ClientLevelWrapper.getWrapper(this.level),
					ClientApi.RENDER_STATE.mcModelViewMatrix,
					ClientApi.RENDER_STATE.mcProjectionMatrix,
					ClientApi.RENDER_STATE.frameTime);
		}
		
		// render fade
		// fade rendering needs to happen AFTER_ENTITIES and AFTER_TRANSLUCENT respectively (fabric names)
		// however since this method intjects at the beginning of the rendertype,
		// we need to trigger for the renderType after those passes are done
		if (renderType.equals(RenderType.cutout()))
		{
			ClientApi.INSTANCE.renderFadeOpaque(
					ClientApi.RENDER_STATE.mcModelViewMatrix,
					ClientApi.RENDER_STATE.mcProjectionMatrix,
					ClientApi.RENDER_STATE.frameTime,
					ClientLevelWrapper.getWrapper(this.level)
			);
		}
		else if (renderType.equals(RenderType.tripwire()))
		{
			ClientApi.INSTANCE.renderFade(
					ClientApi.RENDER_STATE.mcModelViewMatrix,
					ClientApi.RENDER_STATE.mcProjectionMatrix,
					ClientApi.RENDER_STATE.frameTime,
					ClientLevelWrapper.getWrapper(this.level)
			);
		}
		#endif
		
		if (Config.Client.Advanced.Debugging.lodOnlyMode.get())
		{
			callback.cancel();
		}
	}
	
	
	#if MC_VER < MC_1_21_6
	
	// formerly handled in renderChunkLayer()
	
	#else
	@Inject(at = @At("HEAD"), method = "prepareChunkRenders", cancellable = true)
	private void renderChunkLayer(Matrix4fc modelViewMatrix, double d, double e, double f, CallbackInfoReturnable<ChunkSectionsToRender> callback)
	{
		ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.Convert(modelViewMatrix);

		// only crash during development
		if (ModInfo.IS_DEV_BUILD)
		{
			ClientApi.RENDER_STATE.canRenderOrThrow();
		}
		
		ClientApi.INSTANCE.renderLods(ClientLevelWrapper.getWrapper(this.level), 
				ClientApi.RENDER_STATE.mcModelViewMatrix, 
				ClientApi.RENDER_STATE.mcProjectionMatrix, 
				ClientApi.RENDER_STATE.frameTime);
	}
	
	#endif
	
	
	
}
