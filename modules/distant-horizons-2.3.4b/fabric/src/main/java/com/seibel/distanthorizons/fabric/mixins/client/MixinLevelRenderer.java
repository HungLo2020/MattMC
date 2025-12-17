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

import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.fabric.FabricClientProxy;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import net.minecraft.client.Minecraft;
import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.apache.logging.log4j.Logger;


@Mixin(LevelRenderer.class)
public class MixinLevelRenderer
{
    @Shadow
    private ClientLevel level;
	
	@Unique
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
		@Inject(at = @At("HEAD"), method = "prepareChunkRenders", cancellable = true)
	private void prepareChunkRenders(Matrix4fc projectionMatrix, double d, double e, double f, CallbackInfoReturnable<ChunkSectionsToRender> callback)
        {
			    // MC combined the model view and projection matricies
			    // Matrix4fc is the immutable interface, create a mutable Matrix4f from it
	    ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.Convert(new org.joml.Matrix4f(projectionMatrix));
	    ClientApi.RENDER_STATE.mcProjectionMatrix = new Mat4f();
	    ClientApi.RENDER_STATE.mcProjectionMatrix.setIdentity();
			    
		// TODO move this into a common place
			    ClientApi.RENDER_STATE.frameTime = Minecraft.getInstance().deltaTracker.getRealtimeDeltaTicks();
			    
	    
	    //LOGGER.info("\n\n" +
		//	    "Level Mixin\n" +
		//	    "Mc MVM: \n" + mcModelViewMatrix.toString() + "\n" +
		//	    "Mc Proj: \n" + mcProjectionMatrix.toString()
	    //);
	    
	    
	    		// rendering handled via Fabric Api render event
	    		
		// FIXME completely disables rendering when sodium is installed
		if (Config.Client.Advanced.Debugging.lodOnlyMode.get())
		{
		    callback.cancel();
		}
    }
	
	
	
}
