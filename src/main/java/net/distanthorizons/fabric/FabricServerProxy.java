package net.distanthorizons.fabric;

import net.distanthorizons.common.AbstractModInitializer;
import net.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import net.distanthorizons.common.wrappers.misc.ServerPlayerWrapper;
import net.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import net.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import net.distanthorizons.core.api.internal.ServerApi;
import net.distanthorizons.core.dependencyInjection.SingletonInjector;
import net.distanthorizons.common.AbstractPluginPacketSender;
import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.core.wrapperInterfaces.misc.IPluginPacketSender;
import net.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.distanthorizons.core.logging.DhLogger;

import net.distanthorizons.common.CommonPacketPayload;
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
		//LOGGER.info("[DH-VALIDATION] isValidTime() called");
		//LOGGER.info("[DH-VALIDATION] isDedicatedServer: " + this.isDedicatedServer);
		
		if (this.isDedicatedServer)
		{
			//LOGGER.info("[DH-VALIDATION] Dedicated server - returning true");
			return true;
		}
		
		boolean isOnTitleScreen = Minecraft.getInstance().screen instanceof TitleScreen;
		//LOGGER.info("[DH-VALIDATION] Is on title screen: " + isOnTitleScreen);
		
		//FIXME: This may cause init issue...
		boolean result = !isOnTitleScreen;
		//LOGGER.info("[DH-VALIDATION] isValidTime() returning: " + result);
		return result;
	}
	
	private IClientLevelWrapper getClientLevelWrapper(ClientLevel level) { return ClientLevelWrapper.getWrapper(level); }
	private ServerLevelWrapper getServerLevelWrapper(ServerLevel level) { return ServerLevelWrapper.getWrapper(level); }
	private ServerPlayerWrapper getServerPlayerWrapper(ServerPlayer player) { return ServerPlayerWrapper.getWrapper(player); }
	
	/** Registers Fabric Events */
	@Override
	public void registerEvents()
	{
		//LOGGER.info("[DH-EVENTS] ========== REGISTERING FABRIC SERVER EVENTS ==========");
		//LOGGER.info("[DH-EVENTS] isDedicatedServer: " + this.isDedicatedServer);
		//LOGGER.info("[DH-EVENTS] Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
		
		/* Register the mod needed event callbacks */
		
		
		// ServerWorldLoadEvent
		//TODO: Check if both of these use the correct timed events. (i.e. is it 'ed' or 'ing' one?)
		//LOGGER.info("[DH-EVENTS] Registering SERVER_STARTING event...");
		ServerLifecycleEvents.SERVER_STARTING.register((server) ->
		{
			//LOGGER.info("[DH-EVENT-CALLBACK] ========== SERVER_STARTING CALLBACK TRIGGERED ==========");
			//LOGGER.info("[DH-EVENT-CALLBACK] Server: " + server);
			//LOGGER.info("[DH-EVENT-CALLBACK] Is Dedicated: " + server.isDedicatedServer());
			//LOGGER.info("[DH-EVENT-CALLBACK] Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
			
			boolean isValid = this.isValidTime();
			//LOGGER.info("[DH-EVENT-CALLBACK] isValidTime: " + isValid);
			
			if (isValid)
			{
				//LOGGER.info("[DH-EVENT-CALLBACK] Calling ServerApi.serverLoadEvent(isDedicated=" + this.isDedicatedServer + ")");
				ServerApi.INSTANCE.serverLoadEvent(this.isDedicatedServer);
				//LOGGER.info("[DH-EVENT-CALLBACK] ServerApi.serverLoadEvent completed");
			}
			else
			{
				//LOGGER.warn("[DH-EVENT-CALLBACK] Skipped ServerApi.serverLoadEvent - isValidTime returned false");
			}
		});
		
		// ServerWorldUnloadEvent
		//LOGGER.info("[DH-EVENTS] Registering SERVER_STOPPED event...");
		ServerLifecycleEvents.SERVER_STOPPED.register((server) ->
		{
			//LOGGER.info("[DH-EVENT-CALLBACK] SERVER_STOPPED callback triggered");
			if (this.isValidTime())
			{
				//LOGGER.info("[DH-EVENT-CALLBACK] Calling ServerApi.serverUnloadEvent()");
				ServerApi.INSTANCE.serverUnloadEvent();
			}
		});
		
		// ServerLevelLoadEvent
		//LOGGER.info("[DH-EVENTS] Registering ServerWorldEvents.LOAD event...");
		ServerWorldEvents.LOAD.register((server, level) ->
		{
			//LOGGER.info("[DH-EVENT-CALLBACK] ServerWorldEvents.LOAD callback triggered for level: " + level);
			if (this.isValidTime())
			{
				//LOGGER.info("[DH-EVENT-CALLBACK] Calling ServerApi.serverLevelLoadEvent()");
				ServerApi.INSTANCE.serverLevelLoadEvent(this.getServerLevelWrapper(level));
			}
		});
		
		// ServerLevelUnloadEvent
		//LOGGER.info("[DH-EVENTS] Registering ServerWorldEvents.UNLOAD event...");
		ServerWorldEvents.UNLOAD.register((server, level) ->
		{
			//LOGGER.info("[DH-EVENT-CALLBACK] ServerWorldEvents.UNLOAD callback triggered for level: " + level);
			if (this.isValidTime())
			{
				ServerApi.INSTANCE.serverLevelUnloadEvent(this.getServerLevelWrapper(level));
			}
		});
		
		// ServerChunkLoadEvent
		//LOGGER.info("[DH-EVENTS] Registering ServerChunkEvents.CHUNK_LOAD event...");
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
		
		//LOGGER.info("[DH-EVENTS] Registering ServerPlayConnectionEvents.JOIN event...");
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
		{
			//LOGGER.info("[DH-EVENT-CALLBACK] Player joined: " + handler.getName());
			if (this.isValidTime())
			{
				ServerApi.INSTANCE.serverPlayerJoinEvent(this.getServerPlayerWrapper(handler));
			}
		});
		
		//LOGGER.info("[DH-EVENTS] Registering ServerPlayConnectionEvents.DISCONNECT event...");
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
		{
			//LOGGER.info("[DH-EVENT-CALLBACK] Player disconnected: " + handler.getName());
			if (this.isValidTime())
			{
				ServerApi.INSTANCE.serverPlayerDisconnectEvent(this.getServerPlayerWrapper(handler));
			}
		});
		
		//LOGGER.info("[DH-EVENTS] Registering ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD event...");
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
		
		//LOGGER.info("[DH-EVENTS] Registering packet handlers...");
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
		
		//LOGGER.info("[DH-EVENTS] ========== FABRIC SERVER EVENTS REGISTERED ==========");
	}
	
}
