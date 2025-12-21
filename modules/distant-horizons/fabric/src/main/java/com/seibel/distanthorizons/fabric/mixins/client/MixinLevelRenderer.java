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

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import com.seibel.distanthorizons.core.logging.DhLogger;



@Mixin(LevelRenderer.class)
public class MixinLevelRenderer
{
    @Shadow
    private ClientLevel level;
	
	@Unique
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	static {
		System.out.println("========================================");
		System.out.println("DH MixinLevelRenderer CLASS LOADED!");
		System.out.println("========================================");
	}
	
	
	@Inject(at = @At("HEAD"), method = "renderLevel")
	private void renderLevel(
			GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker,
			boolean renderBlockOutline, Camera camera,
			Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f idkMatrix, GpuBufferSlice gpuBufferSlice,
			Vector4f skyColor, boolean thinFog, CallbackInfo callback)
    {
	    LOGGER.info("=== DH MixinLevelRenderer.renderLevel CALLED ===");
	    LOGGER.info("DH Mixin renderLevel: this.level = " + this.level);
	    
	    ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.Convert(positionMatrix);
	    ClientApi.RENDER_STATE.mcProjectionMatrix = McObjectConverter.Convert(projectionMatrix);
	    
		// TODO move this into a common place
	    ClientApi.RENDER_STATE.frameTime = Minecraft.getInstance().deltaTracker.getRealtimeDeltaTicks();
	    
	    LOGGER.info("DH Mixin renderLevel: About to call ClientLevelWrapper.getWrapperIfDifferent");
	    LOGGER.info("DH Mixin renderLevel: Current clientLevelWrapper = " + ClientApi.RENDER_STATE.clientLevelWrapper);
	    ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(ClientApi.RENDER_STATE.clientLevelWrapper, this.level);
	    LOGGER.info("DH Mixin renderLevel: After getWrapperIfDifferent, clientLevelWrapper = " + ClientApi.RENDER_STATE.clientLevelWrapper);
	    
	    LOGGER.info("DH MixinLevelRenderer.renderLevel finished setting render state");
	    
		// handled here and in MixinChunkSectionsToRender
    }
	
	
	
	@Inject(at = @At("HEAD"), method = "prepareChunkRenders")
	private void prepareChunkRenders(Matrix4fc modelViewMatrix, double d, double e, double f, CallbackInfoReturnable<ChunkSectionsToRender> callback)
	{
		LOGGER.info("=== DH MixinLevelRenderer.prepareChunkRenders CALLED ===");
		LOGGER.info("DH Mixin: this.level = " + this.level);
		LOGGER.info("DH Mixin: Setting model view matrix");
		
		ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.Convert(modelViewMatrix);
		
		LOGGER.info("DH Mixin: About to call ClientLevelWrapper.getWrapperIfDifferent");
		LOGGER.info("DH Mixin: Current clientLevelWrapper = " + ClientApi.RENDER_STATE.clientLevelWrapper);
		ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(ClientApi.RENDER_STATE.clientLevelWrapper, this.level);
		LOGGER.info("DH Mixin: After getWrapperIfDifferent, clientLevelWrapper = " + ClientApi.RENDER_STATE.clientLevelWrapper);
		
		LOGGER.info("DH Mixin: About to check if dev build");
		// only crash during development
		if (ModInfo.IS_DEV_BUILD)
		{
			LOGGER.info("DH Mixin: IS_DEV_BUILD = true, checking render state");
			ClientApi.RENDER_STATE.canRenderOrThrow();
		}
		else
		{
			LOGGER.info("DH Mixin: IS_DEV_BUILD = false");
		}
		
		LOGGER.info("DH Mixin: About to call ClientApi.INSTANCE.renderLods()");
		ClientApi.INSTANCE.renderLods();
		LOGGER.info("DH Mixin: Finished calling renderLods()");
		
	}
	
	
	
	
}
