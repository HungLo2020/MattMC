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

package com.seibel.distanthorizons.fabric;

import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.common.AbstractPluginPacketSender;
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.mojang.blaze3d.platform.InputConstants;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;

import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IPluginPacketSender;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.ISodiumAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.fabric.wrappers.modAccessor.SodiumAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

#if MC_VER >= MC_1_20_6
import com.seibel.distanthorizons.common.CommonPacketPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
#else
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
#endif

#if MC_VER < MC_1_19_4
import java.nio.FloatBuffer;
#endif
import java.util.HashSet;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.HitResult;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * This handles all events sent to the client,
 * and is the starting point for most of the mod.
 * 
 * @author coolGi
 * @author Ran
 * @version 2023-7-27
 */
@Environment(EnvType.CLIENT)
public class FabricClientProxy implements AbstractModInitializer.IEventProxy
{
	private final ClientApi clientApi = ClientApi.INSTANCE;
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final AbstractPluginPacketSender PACKET_SENDER = (AbstractPluginPacketSender) SingletonInjector.INSTANCE.get(IPluginPacketSender.class);
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	// TODO we shouldn't be filtering keys on the Forge/Fabric side, only in ClientApi
	private static final int[] KEY_TO_CHECK_FOR = { GLFW.GLFW_KEY_F6, GLFW.GLFW_KEY_F8, GLFW.GLFW_KEY_P};
	
	HashSet<Integer> previouslyPressKeyCodes = new HashSet<>();
	
	
	
	/**
	 * Registers Fabric Events
	 * @author Ran
	 */
	@Override
	public void registerEvents()
	{
		LOGGER.info("Registering Fabric Client Events");
		
		final java.util.function.Consumer<WorldRenderEvents.WorldRenderContext> updateRenderState = (renderContext) -> {
			ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.Convert(renderContext.matrixStack().last().pose());
			ClientApi.RENDER_STATE.mcProjectionMatrix = McObjectConverter.Convert(renderContext.projectionMatrix());
			ClientApi.RENDER_STATE.frameTime = renderContext.tickDelta();
			ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapper(renderContext.world());
		};
		
		//========================//
		// register mod accessors //
		//========================//
		
		SodiumAccessor sodiumAccessor = (SodiumAccessor) ModAccessorInjector.INSTANCE.get(ISodiumAccessor.class);
		
		
		
		//=============//
		// tick events //
		//=============//
		
		ClientTickEvents.START_CLIENT_TICK.register((client) -> { ClientApi.INSTANCE.clientTickEvent(); });
		
		
		
		//==============//
		// world events //
		//==============//
		
		// ClientWorldLoadEvent - for client-side level registration
		ClientWorldEvents.WORLD_LOAD.register((client, world) ->
		{
			ClientApi.INSTANCE.clientLevelLoadEvent(ClientLevelWrapper.getWrapper(world, true));
		});
		
		// ClientWorldUnloadEvent
		ClientWorldEvents.WORLD_UNLOAD.register((client, world) ->
		{
			if (world != null)
			{
				ClientApi.INSTANCE.clientLevelUnloadEvent(ClientLevelWrapper.getWrapper(world));
			}
		});
		
		
		
		//==============//
		// chunk events //
		//==============//
		
		// ClientChunkLoadEvent
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
		{
			if (MC.clientConnectedToDedicatedServer())
			{
				IClientLevelWrapper wrappedLevel = ClientLevelWrapper.getWrapper(level);
				SharedApi.INSTANCE.chunkLoadEvent(new ChunkWrapper(chunk, wrappedLevel), wrappedLevel);
			}
		});
		
		// (kinda) block break event
		// Since fabric doesn't have a client-side break-block API event, this is the next best thing
		AttackBlockCallback.EVENT.register((player, level, interactionHand, blockPos, direction) ->
		{
			// if we have access to the server, use the chunk save event instead 
			if (MC.clientConnectedToDedicatedServer())
			{
				if (SharedApi.isChunkAtBlockPosAlreadyUpdating(blockPos.getX(), blockPos.getZ()))
				{
					// executor to prevent locking up the render/event thread
					// if the getChunk() takes longer than expected 
					// (which can be caused by certain mods) 
					AbstractExecutorService executor = ThreadPoolUtil.getFileHandlerExecutor();
					if (executor != null)
					{
						executor.execute(() ->
						{
							ChunkAccess chunk = level.getChunk(blockPos);
							if (chunk != null)
							{
								//LOGGER.trace("attack block at blockPos: " + blockPos);
								
								IClientLevelWrapper wrappedLevel = ClientLevelWrapper.getWrapper((ClientLevel) level);
								SharedApi.INSTANCE.chunkBlockChangedEvent(
										new ChunkWrapper(chunk, wrappedLevel),
										wrappedLevel
								);
							}
						});
					}
				}
			}
			
			// don't stop the callback
			return InteractionResult.PASS;
		});
		
		// (kinda) block place event
		// Since fabric doesn't have a client-side place-block API event, this is the next best thing
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> 
		{
			// if we have access to the server, use the chunk save event instead 
			if (MC.clientConnectedToDedicatedServer())
			{
				if (hitResult.getType() == HitResult.Type.BLOCK
						&& !hitResult.isInside())
				{
					if (SharedApi.isChunkAtBlockPosAlreadyUpdating(hitResult.getBlockPos().getX(), hitResult.getBlockPos().getZ()))
					{
						// executor to prevent locking up the render/event thread
						// if the getChunk() takes longer than expected 
						// (which can be caused by certain mods) 
						AbstractExecutorService executor = ThreadPoolUtil.getFileHandlerExecutor();
						if (executor != null)
						{
							executor.execute(() ->
							{
								ChunkAccess chunk = level.getChunk(hitResult.getBlockPos());
								if (chunk != null)
								{
									//LOGGER.trace("use block at blockPos: " + hitResult.getBlockPos());
									
									IClientLevelWrapper wrappedLevel = ClientLevelWrapper.getWrapper((ClientLevel) level);
									SharedApi.INSTANCE.chunkBlockChangedEvent(
											new ChunkWrapper(chunk, wrappedLevel),
											wrappedLevel
									);
								}
							});
						}
					}
				}
			}
			
			// don't stop the callback
			return InteractionResult.PASS;
		});
		
		
		
		//==============//
		// render event //
		//==============//

		WorldRenderEvents.AFTER_SETUP.register((renderContext) ->
		{
			updateRenderState.accept(renderContext);
			// Store render state for DH to use
			// New API uses parameterless methods that get state internally
			this.clientApi.renderLods();
		});
		
		
		// TODO add to forge and neo
		WorldRenderEvents.AFTER_ENTITIES.register((renderContext) ->
		{
			updateRenderState.accept(renderContext);
			// New API uses parameterless methods
			this.clientApi.renderFadeOpaque();
		});
		
		// TODO add to forge and neo
		WorldRenderEvents.AFTER_TRANSLUCENT.register((renderContext) ->
		{
			updateRenderState.accept(renderContext);
			#if MC_VER < MC_1_21_6
			// rendered in MixinLevelRenderer
			#else
			ClientApi.INSTANCE.renderDeferredLodsForShaders();
			#endif
			
			// New API uses parameterless methods - renderFade is now renderFadeTransparent
			this.clientApi.renderFadeTransparent();
		});
		
		
		// Debug keyboard event
		// FIXME: Use better hooks so it doesn't trigger key press events in text boxes
		ClientTickEvents.END_CLIENT_TICK.register(client -> 
		{
			if (client.player != null && !(Minecraft.getInstance().screen instanceof TitleScreen))
			{
				this.onKeyInput();
			}
		});
		
		
		
		//==================//
		// networking event //
		//==================//
		
		#if MC_VER >= MC_1_20_6
		PayloadTypeRegistry.playS2C().register(CommonPacketPayload.TYPE, new CommonPacketPayload.Codec());
		ClientPlayNetworking.registerGlobalReceiver(CommonPacketPayload.TYPE, (payload, context) ->
		{
			if (payload.message() == null)
			{
				return;
			}
			ClientApi.INSTANCE.pluginMessageReceived(payload.message());
		});
		#else
		ClientPlayNetworking.registerGlobalReceiver(AbstractPluginPacketSender.WRAPPER_PACKET_RESOURCE, (client, handler, buffer, packetSender) ->
		{
			AbstractNetworkMessage message = PACKET_SENDER.decodeMessage(buffer);
			if (message != null)
			{
				ClientApi.INSTANCE.pluginMessageReceived(message);
			}
		});
		#endif
	}
	
	public void onKeyInput()
	{
		HashSet<Integer> currentKeyDown = new HashSet<>();
		
		// Note: Minecraft's InputConstants is same as GLFW Key values
		//TODO: Use mixin to hook directly into the GLFW Keyboard event in minecraft KeyboardHandler
		// Check all keys we need
		for (int keyCode = GLFW.GLFW_KEY_A; keyCode <= GLFW.GLFW_KEY_Z; keyCode++)
		{
			if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), keyCode))
			{
				currentKeyDown.add(keyCode);
			}
		}
		
		for (int keyCode : KEY_TO_CHECK_FOR)
		{
			if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), keyCode))
			{
				currentKeyDown.add(keyCode);
			}
		}
		
		// Diff and trigger events
		for (int keyCode : currentKeyDown)
		{
			if (!this.previouslyPressKeyCodes.contains(keyCode))
			{
				ClientApi.INSTANCE.keyPressedEvent(keyCode);
			}
		}
		
		// Update the set
		this.previouslyPressKeyCodes = currentKeyDown;
	}
	
}
