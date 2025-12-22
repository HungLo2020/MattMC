package com.seibel.distanthorizons.fabric;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.misc.ServerPlayerWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.common.AbstractPluginPacketSender;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IPluginPacketSender;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.fabric.testing.TestChunkInputReplacerEvent;
import com.seibel.distanthorizons.fabric.testing.TestWorldGenBindingEvent;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.seibel.distanthorizons.core.logging.DhLogger;

import com.seibel.distanthorizons.common.CommonPacketPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * This handles all events sent to the server,
 * and is the starting point for most of the mod.
 *
 * @author Ran
 * @author Tomlee
 * @version 5-11-2022
 */
public class FabricServerProxy implements AbstractModInitializer.IEventProxy
{
	private static final ServerApi SERVER_API = ServerApi.INSTANCE;
	@SuppressWarnings("unused")
	private static final AbstractPluginPacketSender PACKET_SENDER = (AbstractPluginPacketSender) SingletonInjector.INSTANCE.get(IPluginPacketSender.class);
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private final boolean isDedicatedServer;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FabricServerProxy(boolean isDedicatedServer)
	{
		this.isDedicatedServer = isDedicatedServer;
	}
	
	
	
	// TODO rename
	private boolean isValidTime()
	{
		if (this.isDedicatedServer)
		{
			return true;
		}
		
		//FIXME: This may cause init issue...
		// Always return true for integrated servers - the TitleScreen check was preventing
		// server-side initialization in singleplayer, causing "No DH Client World Loaded" errors
		return true; // Was: !(Minecraft.getInstance().screen instanceof TitleScreen);
	}
	
	private IClientLevelWrapper getClientLevelWrapper(ClientLevel level) { return ClientLevelWrapper.getWrapper(level); }
	private ServerLevelWrapper getServerLevelWrapper(ServerLevel level) { return ServerLevelWrapper.getWrapper(level); }
	private ServerPlayerWrapper getServerPlayerWrapper(ServerPlayer player) { return ServerPlayerWrapper.getWrapper(player); }
	
	/** Registers Fabric Events */
	@Override
	public void registerEvents()
	{
		System.out.println("!!!!! FabricServerProxy.registerEvents() CALLED, isDedicatedServer=" + this.isDedicatedServer);
		LOGGER.info("Registering Fabric Server Events");
		
		/* Register the mod needed event callbacks */
		
		// can be enabled to test overrides/events without having to build a separate API project 
		if (false)
		{
			DhApiEventRegister.on(DhApiLevelLoadEvent.class, new TestWorldGenBindingEvent());
			DhApi.events.bind(DhApiChunkProcessingEvent.class, new TestChunkInputReplacerEvent());
		}
		
		
		// ServerWorldLoadEvent
		// Use SERVER_STARTING with phase ordering to ensure event fires for integrated servers.
		// Set up phase ordering first (normally done in FabricMain.subscribeServerStartingEvent,
		// but that's only called from onInitializeServer which doesn't run for integrated servers)
		ServerLifecycleEvents.SERVER_STARTING.addPhaseOrdering(FabricMain.INITIAL_PHASE, Event.DEFAULT_PHASE);
		
		// Register AFTER FabricMain's INITIAL_PHASE so server wrapper is initialized first.
		ServerLifecycleEvents.SERVER_STARTING.register(Event.DEFAULT_PHASE, (server) ->
		{
			System.out.println("!!!!! SERVER_STARTING event fired (DEFAULT_PHASE)! isDedicatedServer=" + this.isDedicatedServer + ", isValidTime()=" + this.isValidTime());
			if (this.isValidTime())
			{
				System.out.println("!!!!! About to call ServerApi.INSTANCE.serverLoadEvent()");
				ServerApi.INSTANCE.serverLoadEvent(this.isDedicatedServer);
			}
			else
			{
				System.out.println("!!!!! isValidTime() returned false, skipping serverLoadEvent()");
			}
		});
		// ServerWorldUnloadEvent
		ServerLifecycleEvents.SERVER_STOPPED.register((server) ->
		{
			if (this.isValidTime())
			{
				ServerApi.INSTANCE.serverUnloadEvent();
			}
		});
		
		// ServerLevelLoadEvent
		ServerWorldEvents.LOAD.register((server, level) ->
		{
			if (this.isValidTime())
			{
				ServerApi.INSTANCE.serverLevelLoadEvent(this.getServerLevelWrapper(level));
			}
		});
		// ServerLevelUnloadEvent
		ServerWorldEvents.UNLOAD.register((server, level) ->
		{
			if (this.isValidTime())
			{
				ServerApi.INSTANCE.serverLevelUnloadEvent(this.getServerLevelWrapper(level));
			}
		});
		
		// ServerChunkLoadEvent
		ServerChunkEvents.CHUNK_LOAD.register((server, chunk) ->
		{
			ILevelWrapper level = this.getServerLevelWrapper((ServerLevel) chunk.getLevel());
			if (this.isValidTime())
			{
				ServerApi.INSTANCE.serverChunkLoadEvent(
						new ChunkWrapper(chunk, level),
						level);
			}
		});
		// ServerChunkSaveEvent - Done in MixinChunkMap
		
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
		{
			if (this.isValidTime())
			{
				ServerApi.INSTANCE.serverPlayerJoinEvent(this.getServerPlayerWrapper(handler));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
		{
			if (this.isValidTime())
			{
				ServerApi.INSTANCE.serverPlayerDisconnectEvent(this.getServerPlayerWrapper(handler));
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, originLevel, destinationLevel) ->
		{
			if (this.isValidTime())
			{
				ServerApi.INSTANCE.serverPlayerLevelChangeEvent(
						this.getServerPlayerWrapper(player),
						this.getServerLevelWrapper(originLevel),
						this.getServerLevelWrapper(destinationLevel)
				);
			}
		});
		
		PayloadTypeRegistry.playC2S().register(CommonPacketPayload.TYPE, new CommonPacketPayload.Codec());
		if (this.isDedicatedServer)
		{
			PayloadTypeRegistry.playS2C().register(CommonPacketPayload.TYPE, new CommonPacketPayload.Codec());
		}
		
		ServerPlayNetworking.registerGlobalReceiver(CommonPacketPayload.TYPE, (payload, context) ->
		{
			if (payload.message() == null)
			{
				return;
			}
			ServerApi.INSTANCE.pluginMessageReceived(ServerPlayerWrapper.getWrapper(context.player()), payload.message());
		});
	}
	
}
