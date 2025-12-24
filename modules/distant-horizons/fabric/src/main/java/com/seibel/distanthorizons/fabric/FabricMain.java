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

import com.mojang.brigadier.CommandDispatcher;
import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.NativeDialogUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IPluginPacketSender;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.*;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.fabric.wrappers.modAccessor.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import com.seibel.distanthorizons.core.logging.DhLogger;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * Initialize and setup the Mod. <br>
 * If you are looking for the real start of the mod
 * check out the ClientProxy.
 */
public class FabricMain extends AbstractModInitializer implements ClientModInitializer, DedicatedServerModInitializer
{
	private static final ResourceLocation INITIAL_PHASE = ResourceLocation.fromNamespaceAndPath(ModInfo.RESOURCE_NAMESPACE, ModInfo.DEDICATED_SERVER_INITIAL_PATH);
	
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	
	@Override
	protected void createInitialSharedBindings()
	{
		SingletonInjector.INSTANCE.bind(IModChecker.class, ModChecker.INSTANCE);
		SingletonInjector.INSTANCE.bind(IPluginPacketSender.class, new FabricPluginPacketSender());
	}
	@Override
	protected void createInitialClientBindings() { /* no additional setup needed currently */ }
	
	
	@Override
	protected IEventProxy createClientProxy() { return new FabricClientProxy(); }
	
	@Override
	protected IEventProxy createServerProxy(boolean isDedicated) { return new FabricServerProxy(isDedicated); }
	
	@Override
	protected void initializeModCompat()
	{
		IModChecker modChecker = SingletonInjector.INSTANCE.get(IModChecker.class);
		if (modChecker.isModLoaded("sodium"))
		{
			ModAccessorInjector.INSTANCE.bind(ISodiumAccessor.class, new SodiumAccessor());
			
			// If sodium is installed Indium is also necessary for versions 0.5 and less in order to use the Fabric rendering API
			if (!modChecker.isModLoaded("indium") && SodiumAccessor.isSodiumV5OrLess)
			{
				String indiumMissingMessage = ModInfo.READABLE_NAME + " needs Indium to work with Sodium.\nPlease download Indium from https://modrinth.com/mod/indium";
				LOGGER.fatal(indiumMissingMessage);
				
				NativeDialogUtil.showDialog(ModInfo.READABLE_NAME, indiumMissingMessage, "ok", "error");
				
				IMinecraftClientWrapper mc = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
				String errorMessage = "loading Distant Horizons. Distant Horizons requires Indium in order to run with Sodium.";
				String exceptionError = "Distant Horizons conditional mod Exception";
				mc.crashMinecraft(errorMessage, new Exception(exceptionError));
			}
		}
		
		this.tryCreateModCompatAccessor("starlight", IStarlightAccessor.class, StarlightAccessor::new);
		this.tryCreateModCompatAccessor("optifine", IOptifineAccessor.class, OptifineAccessor::new);
		this.tryCreateModCompatAccessor("bclib", IBCLibAccessor.class, BCLibAccessor::new);
		this.tryCreateModCompatAccessor("c2me", IC2meAccessor.class, C2meAccessor::new);
		// 1.19.4 is the lowest version Iris supports DH
		this.tryCreateModCompatAccessor("iris", IIrisAccessor.class, IrisAccessor::new);
	}
	
	@Override
	protected void subscribeRegisterCommandsEvent(Consumer<CommandDispatcher<CommandSourceStack>> eventHandler)
	{
		CommandRegistrationCallback.EVENT.register(
			(dispatcher, registryAccess, environment) -> 
			{
				eventHandler.accept(dispatcher);
			}
		);
	}
	
	@Override
	protected void subscribeClientStartedEvent(Runnable eventHandler) 
	{ ClientLifecycleEvents.CLIENT_STARTED.register((mc) -> eventHandler.run()); }
	
	@Override
	protected void subscribeServerStartingEvent(Consumer<MinecraftServer> eventHandler)
	{
		//LOGGER.info("[DH-EVENT-SUB] Subscribing to server lifecycle events...");
		//LOGGER.info("[DH-EVENT-SUB] Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
		
		// Try ALL server lifecycle events to see which ones fire for integrated servers
		//LOGGER.info("[DH-EVENT-SUB] Registering SERVER_STARTING event handler");
		ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
			//LOGGER.info("[DH-EVENT-FIRE] ========== SERVER_STARTING EVENT FIRED ==========");
			//LOGGER.info("[DH-EVENT-FIRE] Server: " + server);
			//LOGGER.info("[DH-EVENT-FIRE] Is Dedicated: " + server.isDedicatedServer());
			//LOGGER.info("[DH-EVENT-FIRE] Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
			eventHandler.accept(server);
			//LOGGER.info("[DH-EVENT-FIRE] SERVER_STARTING event handler completed");
		});
		
		//LOGGER.info("[DH-EVENT-SUB] Registering SERVER_STARTED event handler");
		ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
			//LOGGER.info("[DH-EVENT-FIRE] ========== SERVER_STARTED EVENT FIRED ==========");
			//LOGGER.info("[DH-EVENT-FIRE] Server: " + server);
			//LOGGER.info("[DH-EVENT-FIRE] Is Dedicated: " + server.isDedicatedServer());
			//LOGGER.info("[DH-EVENT-FIRE] Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
			if (!server.isDedicatedServer())
			{
				//LOGGER.info("[DH-EVENT-FIRE] Integrated server detected in SERVER_STARTED - calling handler");
				eventHandler.accept(server);
			}
			//LOGGER.info("[DH-EVENT-FIRE] SERVER_STARTED event handler completed");
		});
		
		//LOGGER.info("[DH-EVENT-SUB] Registering SERVER_STOPPING event handler");
		ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
			//LOGGER.info("[DH-EVENT-FIRE] ========== SERVER_STOPPING EVENT FIRED ==========");
			//LOGGER.info("[DH-EVENT-FIRE] Server: " + server);
			//LOGGER.info("[DH-EVENT-FIRE] Is Dedicated: " + server.isDedicatedServer());
		});
		
		//LOGGER.info("[DH-EVENT-SUB] Registering SERVER_STOPPED event handler");
		ServerLifecycleEvents.SERVER_STOPPED.register((server) -> {
			//LOGGER.info("[DH-EVENT-FIRE] ========== SERVER_STOPPED EVENT FIRED ==========");
			//LOGGER.info("[DH-EVENT-FIRE] Server: " + server);
			//LOGGER.info("[DH-EVENT-FIRE] Is Dedicated: " + server.isDedicatedServer());
		});
		
		//LOGGER.info("[DH-EVENT-SUB] Server lifecycle event subscriptions complete");
	}
	
	@Override
	protected void runDelayedSetup()
	{
		SingletonInjector.INSTANCE.runDelayedSetup();
		
		if (!Config.Client.Advanced.Graphics.Fog.enableVanillaFog.get() && SingletonInjector.INSTANCE.get(IModChecker.class).isModLoaded("bclib"))
		{
			ModAccessorInjector.INSTANCE.get(IBCLibAccessor.class).setRenderCustomFog(false); // Remove BCLib's fog
		}
		
		if (SingletonInjector.INSTANCE.get(IModChecker.class).isModLoaded("sodium"))
		{
			ModAccessorInjector.INSTANCE.get(ISodiumAccessor.class).setFogOcclusion(false);
		}
	}
	
}
