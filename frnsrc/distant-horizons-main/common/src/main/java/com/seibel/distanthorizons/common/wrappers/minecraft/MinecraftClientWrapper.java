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

import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.file.structure.ClientOnlySaveStructure;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.logging.DhLogger;

import net.minecraft.CrashReport;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;

import org.jetbrains.annotations.Nullable;

#if MC_VER < MC_1_19_2
import net.minecraft.network.chat.TextComponent;
#endif

#if MC_VER < MC_1_21_3
#else
import net.minecraft.util.profiling.Profiler;
#endif

/**
 * A singleton that wraps the Minecraft object.
 *
 * @author James Seibel
 */
public class MinecraftClientWrapper implements IMinecraftClientWrapper, IMinecraftSharedWrapper
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	private static final Minecraft MINECRAFT = Minecraft.getInstance();
	
	public static final MinecraftClientWrapper INSTANCE = new MinecraftClientWrapper();
	
	
	private ProfilerWrapper profilerWrapper;
	
	
	
	//======================//
	// multiplayer handling //
	//======================//
	
	@Override
	public boolean hasSinglePlayerServer() { return MINECRAFT.hasSingleplayerServer(); }
	@Override
	public boolean clientConnectedToDedicatedServer() 
	{ 
		return MINECRAFT.getCurrentServer() != null 
				&& !this.hasSinglePlayerServer(); 
	}
	@Override
	public boolean connectedToReplay() 
	{ 
		return MINECRAFT.getCurrentServer() == null
				&& !this.hasSinglePlayerServer() ; 
	}
	
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
	
	
	
	//=================//
	// player handling //
	//=================//
	
	public LocalPlayer getPlayer() { return MINECRAFT.player; }
	
	@Override
	public boolean playerExists() { return MINECRAFT.player != null; }
	
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
		
        #if MC_VER < MC_1_17_1
        ChunkPos playerPos = new ChunkPos(player.blockPosition());
        #else
		ChunkPos playerPos = player.chunkPosition();
        #endif
		return new DhChunkPos(playerPos.x, playerPos.z);
	}
	
	
	
	//================//
	// level handling //
	//================//
	
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
	
	
	
	//===========//
	// messaging //
	//===========//
	
	@Override
	public void sendChatMessage(String string)
	{
		LocalPlayer player = this.getPlayer();
		if (player == null)
		{
			return;
		}
		
        #if MC_VER < MC_1_19_2
		player.sendMessage(new TextComponent(string), getPlayer().getUUID());
        #elif MC_VER < MC_1_21_9
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable(string), /*isOverlay*/false);
		#else
		
		GLProxy.queueRunningOnRenderThread(() -> 
		{
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(string), /*isOverlay*/false);
		});
        #endif
	}
	
	@Override
	public void sendOverlayMessage(String string)
	{
		LocalPlayer player = this.getPlayer();
		if (player == null)
		{
			return;
		}
		
        #if MC_VER < MC_1_19_2
		player.displayClientMessage(new TextComponent(string), /*isOverlay*/true);
        #else
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable(string), /*isOverlay*/true);
        #endif
	}
	
	
	
	//==========================//
	// vanilla option overrides //
	//==========================//
	
	public void disableVanillaClouds()
	{
		#if MC_VER <= MC_1_18_2
		MINECRAFT.options.renderClouds = CloudStatus.OFF;
		#else
		MINECRAFT.options.cloudStatus().set(CloudStatus.OFF);
		#endif
	}
	
	public void disableVanillaChunkFadeIn()
	{
		#if MC_VER <= MC_1_21_10
		// chunk fade in was added MC 1.21.11
		#else
		MINECRAFT.options.chunkSectionFadeInTime().set(0.0);
		#endif
	}
	
	
	
	//======//
	// misc //
	//======//
	
	@Override
	public IProfilerWrapper getProfiler()
	{
		ProfilerFiller profiler;
		#if MC_VER < MC_1_21_3
		profiler = MINECRAFT.getProfiler();
		#else
		profiler = Profiler.get();
		#endif
		
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
	
	@Override
	public void crashMinecraft(String errorMessage, Throwable exception)
	{
		LOGGER.fatal(ModInfo.READABLE_NAME + " had the following error: [" + errorMessage + "]. Crashing Minecraft...", exception);
		CrashReport report = new CrashReport(errorMessage, exception);
		#if MC_VER < MC_1_20_4
		Minecraft.crash(report);
		#else
		MINECRAFT.delayCrash(report);
		#endif
	}
	
	
	
	//=============//
	// mod support //
	//=============//
	
	@Override
	public Object getOptionsObject() { return MINECRAFT.options; }
	
	
	
	//========//
	// shared //
	//========//
	
	@Override
	public boolean isDedicatedServer() { return false; }
	
	@Override
	public File getInstallationDirectory() { return MINECRAFT.gameDirectory; }
	
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
