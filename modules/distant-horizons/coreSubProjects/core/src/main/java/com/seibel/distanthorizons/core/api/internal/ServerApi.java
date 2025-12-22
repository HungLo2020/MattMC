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

package com.seibel.distanthorizons.core.api.internal;

import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelUnloadEvent;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.network.messages.MessageRegistry;
import com.seibel.distanthorizons.core.world.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import com.seibel.distanthorizons.core.logging.DhLogger;
import org.jetbrains.annotations.NotNull;

/**
 * This holds the methods that should be called by the host mod loader (Fabric,
 * Forge, etc.). Specifically server events.
 */
public class ServerApi
{
	public static final ServerApi INSTANCE = new ServerApi();
	
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private ServerApi() { }
	
	
	
	//===============//
	// server events //
	//===============//
	
	public void serverLoadEvent(boolean isDedicatedEnvironment)
	{
		LOGGER.info("[DH-WORLD] ========== SERVER LOAD EVENT ==========");
		LOGGER.info("[DH-WORLD] isDedicatedEnvironment: " + isDedicatedEnvironment);
		LOGGER.info("[DH-WORLD] Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().threadId() + ")");
		LOGGER.info("[DH-WORLD] Current world before: " + SharedApi.getAbstractDhWorld());
		
		AbstractDhWorld newWorld;
		if (isDedicatedEnvironment)
		{
			LOGGER.info("[DH-WORLD] Creating DhServerWorld (dedicated server)");
			newWorld = new DhServerWorld();
		}
		else
		{
			LOGGER.info("[DH-WORLD] Creating DhClientServerWorld (integrated server)");
			newWorld = new DhClientServerWorld();
		}
		
		LOGGER.info("[DH-WORLD] Created world: " + newWorld);
		LOGGER.info("[DH-WORLD] World class: " + newWorld.getClass().getName());
		LOGGER.info("[DH-WORLD] Calling SharedApi.setDhWorld()");
		
		SharedApi.setDhWorld(newWorld);
		
		LOGGER.info("[DH-WORLD] Current world after: " + SharedApi.getAbstractDhWorld());
		LOGGER.info("[DH-WORLD] tryGetDhClientWorld: " + SharedApi.tryGetDhClientWorld());
		LOGGER.info("[DH-WORLD] ========== SERVER LOAD EVENT COMPLETE ==========");
	}
	
	public void serverUnloadEvent()
	{
		LOGGER.debug("Server World " + SharedApi.getAbstractDhWorld() + " unloading");
		
		// shutdown the world if it isn't already
		AbstractDhWorld dhWorld = SharedApi.getAbstractDhWorld();
		if (dhWorld != null)
		{
			dhWorld.close();
			SharedApi.setDhWorld(null);
		}
	}
	
	
	
	//==============//
	// level events //
	//==============//
	
	public void serverLevelLoadEvent(IServerLevelWrapper level)
	{
		LOGGER.info("[DH-LEVEL] ========== SERVER LEVEL LOAD EVENT ==========");
		LOGGER.info("[DH-LEVEL] Level: " + level);
		LOGGER.info("[DH-LEVEL] Level identifier: " + level.getDhIdentifier());
		LOGGER.info("[DH-LEVEL] Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().threadId() + ")");
		
		AbstractDhWorld serverWorld = SharedApi.getAbstractDhWorld();
		LOGGER.info("[DH-LEVEL] Current DH world: " + serverWorld);
		
		if (serverWorld != null)
		{
			LOGGER.info("[DH-LEVEL] Loading level into DH world...");
			serverWorld.getOrLoadLevel(level);
			LOGGER.info("[DH-LEVEL] Level loaded, firing DhApiLevelLoadEvent");
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiLevelLoadEvent.class, new DhApiLevelLoadEvent.EventParam(level));
			LOGGER.info("[DH-LEVEL] ========== SERVER LEVEL LOAD EVENT COMPLETE ==========");
		}
		else
		{
			LOGGER.warn("[DH-LEVEL] Cannot load level - DH world is null!");
		}
	}
	public void serverLevelUnloadEvent(IServerLevelWrapper level)
	{
		LOGGER.debug("Server Level " + level + " unloading");
		
		AbstractDhWorld serverWorld = SharedApi.getAbstractDhWorld();
		if (serverWorld != null)
		{
			serverWorld.unloadLevel(level);
			SharedApi.INSTANCE.clearQueuedChunkUpdates();
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiLevelUnloadEvent.class, new DhApiLevelUnloadEvent.EventParam(level));
		}
	}
	
	
	
	//=======================//
	// chunk modified events //
	//=======================//
	
	public void serverChunkLoadEvent(IChunkWrapper chunkWrapper, ILevelWrapper level) { SharedApi.INSTANCE.applyChunkUpdate(chunkWrapper, level, false, false); }
	public void serverChunkSaveEvent(IChunkWrapper chunkWrapper, ILevelWrapper level) { SharedApi.INSTANCE.applyChunkUpdate(chunkWrapper, level, true, false); }
	
	
	
	//===============//
	// player events //
	//===============//
	
	public void serverPlayerJoinEvent(IServerPlayerWrapper player)
	{
		if (DhApiWorldProxy.INSTANCE.worldLoaded() && DhApiWorldProxy.INSTANCE.getReadOnly())
		{
			return;
		}
		
		IDhServerWorld serverWorld = SharedApi.tryGetDhServerWorld();
		LOGGER.info("Player ["+player.getName()+"] joined.");
		if (serverWorld != null)
		{
			serverWorld.addPlayer(player);
		}
	}
	public void serverPlayerDisconnectEvent(IServerPlayerWrapper player)
	{
		if (DhApiWorldProxy.INSTANCE.worldLoaded() && DhApiWorldProxy.INSTANCE.getReadOnly())
		{
			return;
		}
		
		IDhServerWorld serverWorld = SharedApi.tryGetDhServerWorld();
		LOGGER.info("Player ["+player.getName()+"] disconnected.");
		if (serverWorld != null)
		{
			serverWorld.removePlayer(player);
		}
	}
	public void serverPlayerLevelChangeEvent(IServerPlayerWrapper player, IServerLevelWrapper originLevel, IServerLevelWrapper destinationLevel)
	{
		if (DhApiWorldProxy.INSTANCE.worldLoaded() && DhApiWorldProxy.INSTANCE.getReadOnly())
		{
			return;
		}
		
		IDhServerWorld serverWorld = SharedApi.tryGetDhServerWorld();
		LOGGER.info("Player ["+player.getName()+"] changed level: ["+originLevel.getKeyedLevelDimensionName()+"] -> ["+destinationLevel.getKeyedLevelDimensionName()+"].");
		if (serverWorld != null)
		{
			serverWorld.changePlayerLevel(player, originLevel, destinationLevel);
		}
	}
	
	/**
	 * Forwards a decoded message into the registered handlers.
	 *
	 * @see MessageRegistry
	 */
	public void pluginMessageReceived(IServerPlayerWrapper player, @NotNull AbstractNetworkMessage message)
	{
		if (DhApiWorldProxy.INSTANCE.worldLoaded() && DhApiWorldProxy.INSTANCE.getReadOnly())
		{
			return;
		}
		
		IDhServerWorld serverWorld = SharedApi.tryGetDhServerWorld();
		if (serverWorld != null)
		{
			serverWorld.getServerPlayerStateManager().handlePluginMessage(player, message);
		}
	}
	
}
