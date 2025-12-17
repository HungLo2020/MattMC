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

package com.seibel.distanthorizons.common.wrappers.minecraft;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import com.mojang.blaze3d.platform.NativeImage;
import com.seibel.distanthorizons.api.enums.config.EDhApiLodShading;
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.file.structure.ClientOnlySaveStructure;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;

import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import net.minecraft.util.profiling.Profiler;

/**
 * A singleton that wraps the Minecraft object.
 *
 * @author James Seibel
 */
public class MinecraftClientWrapper implements IMinecraftClientWrapper, IMinecraftSharedWrapper
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());
	private static final Minecraft MINECRAFT = Minecraft.getInstance();
	
	public static final MinecraftClientWrapper INSTANCE = new MinecraftClientWrapper();
	
	
	
	/**
	 * The lightmap for the current:
	 * Time, dimension, brightness setting, etc.
	 */
	private NativeImage lightMap = null;
	
	private ProfilerWrapper profilerWrapper;
	
	
	private MinecraftClientWrapper()
	{
		
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/**
	 * This should be called at the beginning of every frame to
	 * clear any Minecraft data that becomes out of date after a frame. <br> <br>
	 * <p>
	 * LightMaps and other time sensitive objects fall in this category. <br> <br>
	 * <p>
	 * This doesn't affect OpenGL objects in any way.
	 */
	@Override
	public void clearFrameObjectCache() { this.lightMap = null; }
	
	
	
	//=================//
	// method wrappers //
	//=================//
	
	@Override
	public float getShade(EDhDirection lodDirection)
	{
		EDhApiLodShading lodShading = Config.Client.Advanced.Graphics.Quality.lodShading.get();
		switch (lodShading)
		{
			default:
			case AUTO:
				if (MINECRAFT.level != null)
				{
					Direction mcDir = McObjectConverter.Convert(lodDirection);
					return MINECRAFT.level.getShade(mcDir, true);
				}
				else
				{
					return 0.0f;
				}
			
			case ENABLED:
				switch (lodDirection)
				{
					case DOWN:
						return 0.5F;
					default:
					case UP:
						return 1.0F;
					case NORTH:
					case SOUTH:
						return 0.8F;
					case WEST:
					case EAST:
						return 0.6F;
				}
			
			case DISABLED:
				return 1.0F;
		}
	}
	
	@Override
	public boolean hasSinglePlayerServer() { return MINECRAFT.hasSingleplayerServer(); }
	@Override
	public boolean clientConnectedToDedicatedServer() { return MINECRAFT.getCurrentServer() != null && !this.hasSinglePlayerServer(); }
	@Override
	public boolean connectedToReplay() { return !MINECRAFT.hasSingleplayerServer() && MINECRAFT.getCurrentServer() == null; }
	
	@Override
	public String getCurrentServerName() 
	{
		if (this.connectedToReplay())
		{
			return ClientOnlySaveStructure.REPLAY_SERVER_FOLDER_NAME;
		}
		else
		{
			ServerData server = MINECRAFT.getCurrentServer();
			return (server != null) ? server.name : "NULL";
		}
	}
	
	@Override
	public String getCurrentServerIp() 
	{
		if (this.connectedToReplay())
		{
			return "";
		}
		else
		{
			ServerData server = MINECRAFT.getCurrentServer();
			return (server != null) ? server.ip : "NA";
		}
	}
	
	@Override
	public String getCurrentServerVersion()
	{
		ServerData server = MINECRAFT.getCurrentServer();
		return (server != null) ? server.version.getString() : "UNKOWN";
	}
	
	//=============//
	// Simple gets //
	//=============//
	
	public LocalPlayer getPlayer() { return MINECRAFT.player; }
	
	@Override
	public boolean playerExists() { return MINECRAFT.player != null; }
	
	@Override
	public UUID getPlayerUUID() { return this.getPlayer().getUUID(); }
	
	@Override
	public String getUsername() { return MINECRAFT.getUser().getName(); }
	
	@Override
	public DhBlockPos getPlayerBlockPos()
	{
		LocalPlayer player = this.getPlayer();
		if (player == null)
		{
			return new DhBlockPos(0, 0, 0);	
		}
		
		BlockPos playerPos = player.blockPosition();
		return new DhBlockPos(playerPos.getX(), playerPos.getY(), playerPos.getZ());
	}
	
	@Override
	public DhChunkPos getPlayerChunkPos()
	{
		LocalPlayer player = this.getPlayer();
		if (player == null)
		{
			return new DhChunkPos(0, 0);
		}
		
        ChunkPos playerPos = player.chunkPosition();
		return new DhChunkPos(playerPos.x, playerPos.z);
	}
	
	@Nullable
	@Override
	public IClientLevelWrapper getWrappedClientLevel() { return this.getWrappedClientLevel(false); }
	
	@Override
	@Nullable
	public IClientLevelWrapper getWrappedClientLevel(boolean bypassLevelKeyManager)
	{
		ClientLevel level = MINECRAFT.level;
		if (level == null)
		{
			return null;
		}
		
		return ClientLevelWrapper.getWrapper(level, bypassLevelKeyManager);
	}
	
	@Override
	public IProfilerWrapper getProfiler()
	{
		ProfilerFiller profiler;
		profiler = Profiler.get();
		
		if (this.profilerWrapper == null)
		{
			this.profilerWrapper = new ProfilerWrapper(profiler);
		}
		else if (profiler != this.profilerWrapper.profiler)
		{
			this.profilerWrapper.profiler = profiler;
		}
		
		return this.profilerWrapper;
	}
	
	/** Returns all worlds available to the server */
	@Override
	public ArrayList<ILevelWrapper> getAllServerWorlds()
	{
		ArrayList<ILevelWrapper> worlds = new ArrayList<ILevelWrapper>();
		
		Iterable<ServerLevel> serverWorlds = MINECRAFT.getSingleplayerServer().getAllLevels();
		for (ServerLevel world : serverWorlds)
		{
			worlds.add(ServerLevelWrapper.getWrapper(world));
		}
		
		return worlds;
	}
	
	
	
	@Override
	public void sendChatMessage(String string)
	{
		LocalPlayer player = this.getPlayer();
		if (player == null)
		{
			return;
		}
		
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(string), /*isOverlay*/false);
	}
	
	@Override
	public void sendOverlayMessage(String string)
	{
		LocalPlayer player = this.getPlayer();
		if (player == null)
		{
			return;
		}
		
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(string), /*isOverlay*/true);
	}
	
	/**
	 * Crashes Minecraft, displaying the given errorMessage <br> <br>
	 * In the following format: <br>
	 *
	 * The game crashed whilst <strong>errorMessage</strong>  <br>
	 * Error: <strong>ExceptionClass: exceptionErrorMessage</strong>  <br>
	 * Exit Code: -1  <br>
	 */
	@Override
	public void crashMinecraft(String errorMessage, Throwable exception)
	{
		LOGGER.error(ModInfo.READABLE_NAME + " had the following error: [" + errorMessage + "]. Crashing Minecraft...", exception);
		CrashReport report = new CrashReport(errorMessage, exception);
		Minecraft.getInstance().delayCrash(report);
	}
	
	@Override
	public Object getOptionsObject() { return MINECRAFT.options; }
	
	@Override
	public boolean isDedicatedServer() { return false; }
	
	@Override
	public File getInstallationDirectory() { return MINECRAFT.gameDirectory; }
	
	@Override
	public void executeOnRenderThread(Runnable runnable) { MINECRAFT.execute(runnable); }
	
	@Override
	public int getPlayerCount()
	{
		// can be null if the server hasn't finished booting up yet
		if (MINECRAFT.getSingleplayerServer() == null)
		{
			return 1;
		}
		else
		{
			return MINECRAFT.getSingleplayerServer().getPlayerCount();
		}
	}
	
}
