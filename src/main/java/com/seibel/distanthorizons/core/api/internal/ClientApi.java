package com.seibel.distanthorizons.core.api.internal;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiMcRenderingFadeMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.*;
import com.seibel.distanthorizons.core.api.internal.rendering.DhRenderState;
import com.seibel.distanthorizons.core.file.structure.ClientOnlySaveStructure;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.network.messages.MessageRegistry;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
import com.seibel.distanthorizons.core.render.renderer.*;
import com.seibel.distanthorizons.core.util.TimerUtil;
import com.seibel.distanthorizons.core.util.objects.Pair;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.network.session.NetworkSession;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.IServerKeyedClientLevel;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.world.DhClientWorld;
import com.seibel.distanthorizons.core.world.IDhClientWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.logging.DhLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This holds the methods that should be called
 * by the host mod loader (Fabric, Forge, etc.).
 * Specifically for the client.
 */
public class ClientApi
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	public static boolean prefLoggerEnabled = false;
	
	public static final ClientApi INSTANCE = new ClientApi();
	
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	/** this includes the is dev build message and low allocated memory warning */
	private static final int MS_BETWEEN_STATIC_STARTUP_MESSAGES = 4_000;
	
	/** 
	 * This isn't the cleanest way of storing variables before passing them to the LOD renderer, 
	 * but due to how mixins work and the inconsistency between MC versions,
	 * having a static object that stores a single frame's data
	 * is often the easiest solution. <br><br>
	 * 
	 * Only downside is making sure each variable is populated before rendering.
	 */
	public static final DhRenderState RENDER_STATE = new DhRenderState();
	
	
	private boolean isDevBuildMessagePrinted = false;
	private boolean lowMemoryWarningPrinted = false;
	private boolean highVanillaRenderDistanceWarningPrinted = false;
	private boolean g1GarbageCollectorWarningPrinted = false;
	
	private long lastStaticWarningMessageSentMsTime = 0L;
	
	private final Queue<String> chatMessageQueueForNextFrame = new LinkedBlockingQueue<>();
	private final Queue<String> overlayMessageQueueForNextFrame = new LinkedBlockingQueue<>();
	
	public boolean rendererDisabledBecauseOfExceptions = false;
	
	private final ClientPluginChannelApi pluginChannelApi = new ClientPluginChannelApi(this::clientLevelLoadEvent, this::clientLevelUnloadEvent);
	
	/** Delay loading the first level to give the server some time to respond with level to actually load */
	private Timer firstLevelLoadTimer;
	private static final long FIRST_LEVEL_LOAD_DELAY_IN_MS = 1_000;
	
	/** Holds any levels that were loaded before the {@link ClientApi#onClientOnlyConnected} was fired. */
	public final HashSet<IClientLevelWrapper> waitingClientLevels = new HashSet<>();
	/** Holds any chunks that were loaded before the {@link ClientApi#clientLevelLoadEvent(IClientLevelWrapper)} was fired. */
	public final HashMap<Pair<IClientLevelWrapper, DhChunkPos>, IChunkWrapper> waitingChunkByClientLevelAndPos = new HashMap<>();
	
	@Nullable
	public String lastRenderParamValidationMessage = null;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	private ClientApi() { }
	
	
	
	//==============//
	// world events //
	//==============//
	
	/**
	 * May be fired slightly before or after the associated
	 * {@link ClientApi#clientLevelLoadEvent(IClientLevelWrapper)} event
	 * depending on how the host mod loader functions. <br><br>
	 * 
	 * Synchronized shouldn't be necessary, but is present to match {@see onClientOnlyDisconnected} and prevent any unforeseen issues. 
	 */
	public synchronized void onClientOnlyConnected()
	{
		LOGGER.info("[DH-CLIENT-CONNECT] ========== CLIENT ONLY CONNECTED ==========");
		LOGGER.info("[DH-CLIENT-CONNECT] Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
		
		// only continue if the client is connected to a different server
		boolean connectedToServer = MC_CLIENT.clientConnectedToDedicatedServer();
		boolean connectedToReplay = MC_CLIENT.connectedToReplay();
		
		LOGGER.info("[DH-CLIENT-CONNECT] Connected to dedicated server: " + connectedToServer);
		LOGGER.info("[DH-CLIENT-CONNECT] Connected to replay: " + connectedToReplay);
		
		if (connectedToServer || connectedToReplay)
		{
			if (connectedToServer)
			{
				LOGGER.info("Client on ClientOnly mode connecting.");
			}
			else
			{
				LOGGER.info("Replay on ClientServer mode connecting.");
				
				if (Config.Common.Logging.Warning.showReplayWarningOnStartup.get())
				{
					MC_CLIENT.sendChatMessage("\u00A76" + "Distant Horizons: Replay detected." + "\u00A7r"); // gold color
					MC_CLIENT.sendChatMessage("DH may behave strangely or have missing functionality.");
					MC_CLIENT.sendChatMessage("In order to use pre-generated LODs, put your DH database(s) in:");
					MC_CLIENT.sendChatMessage("\u00A77"+".Minecraft" + File.separator + ClientOnlySaveStructure.SERVER_DATA_FOLDER_NAME + File.separator + ClientOnlySaveStructure.REPLAY_SERVER_FOLDER_NAME + File.separator + "DIMENSION_NAME"+"\u00A7r"); // light gray color
					MC_CLIENT.sendChatMessage("This can be disabled in DH's config under Advanced -> Logging.");
					MC_CLIENT.sendChatMessage("");
				}
			}
			
			// firing after clientLevelLoadEvent
			// TODO if level has prepped to load it should fire level load event
			LOGGER.info("[DH-CLIENT-CONNECT] Creating DhClientWorld (client-only mode)");
			DhClientWorld world = new DhClientWorld();
			LOGGER.info("[DH-CLIENT-CONNECT] Created DhClientWorld: " + world);
			LOGGER.info("[DH-CLIENT-CONNECT] Calling SharedApi.setDhWorld()");
			SharedApi.setDhWorld(world);
			
			this.pluginChannelApi.onJoinServer(world.networkState.getSession());
			world.networkState.sendConfigMessage();
			
			LOGGER.info("[DH-CLIENT-CONNECT] Loading [" + this.waitingClientLevels.size() + "] waiting client level wrappers.");
			for (IClientLevelWrapper level : this.waitingClientLevels)
			{
				this.clientLevelLoadEvent(level);
			}
			
			this.waitingClientLevels.clear();
			LOGGER.info("[DH-CLIENT-CONNECT] ========== CLIENT ONLY CONNECTED COMPLETE ==========");
		}
		else
		{
			LOGGER.info("[DH-CLIENT-CONNECT] Not connected to dedicated server or replay - skipping DhClientWorld creation");
			LOGGER.info("[DH-CLIENT-CONNECT] This is expected for integrated/singleplayer - world should be created by ServerApi");
		}
	}
	
	/** Synchronized to prevent a rare issue where multiple disconnect events are triggered on top of each other. */
	public synchronized void onClientOnlyDisconnected()
	{
		// clear the first time timer
		if (this.firstLevelLoadTimer != null)
		{
			this.firstLevelLoadTimer.cancel();
			this.firstLevelLoadTimer = null;
		}
		
		AbstractDhWorld world = SharedApi.getAbstractDhWorld();
		if (world != null)
		{
			LOGGER.info("Client on ClientOnly mode disconnecting.");
			
			world.close();
			SharedApi.setDhWorld(null);
		}
		
		this.pluginChannelApi.reset();
		
		// remove any waiting items
		this.waitingChunkByClientLevelAndPos.clear();
		this.waitingClientLevels.clear();
	}
	
	
	
	//==============//
	// level events //
	//==============//
	
	public void clientLevelUnloadEvent(IClientLevelWrapper level)
	{
		try
		{
			LOGGER.info("Unloading client level [" + level.getClass().getSimpleName() + "]-[" + level.getDhIdentifier() + "].");
			
			if (level instanceof IServerKeyedClientLevel)
			{
				this.pluginChannelApi.onClientLevelUnload();
			}
			
			AbstractDhWorld world = SharedApi.getAbstractDhWorld();
			if (world != null)
			{
				world.unloadLevel(level);
				SharedApi.INSTANCE.clearQueuedChunkUpdates();
				ApiEventInjector.INSTANCE.fireAllEvents(DhApiLevelUnloadEvent.class, new DhApiLevelUnloadEvent.EventParam(level));
			}
			else
			{
				this.waitingClientLevels.remove(level);
			}
		}
		catch (Exception e)
		{
			// handle errors here to prevent blowing up a mixin or API up stream
			LOGGER.error("Unexpected error in ClientApi.clientLevelUnloadEvent(), error: "+e.getMessage(), e);
		}
	}
	
	public void clientLevelLoadEvent(IClientLevelWrapper levelWrapper)
	{
		//LOGGER.info("[DH-CLIENT-LEVEL] ========== CLIENT LEVEL LOAD EVENT ==========");
		//LOGGER.info("[DH-CLIENT-LEVEL] Level: " + levelWrapper);
		//LOGGER.info("[DH-CLIENT-LEVEL] Level identifier: " + levelWrapper.getDhIdentifier());
		//LOGGER.info("[DH-CLIENT-LEVEL] Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
		
		// wait a moment before loading the level to give the server a chance to handle the client's login request
		if (MC_CLIENT.clientConnectedToDedicatedServer())
		{
			if (this.firstLevelLoadTimer == null)
			{
				//LOGGER.info("[DH-CLIENT-LEVEL] Dedicated server detected - delaying level load by " + FIRST_LEVEL_LOAD_DELAY_IN_MS + "ms");
				this.firstLevelLoadTimer = TimerUtil.CreateTimer("FirstLevelLoadTimer");
				this.firstLevelLoadTimer.schedule(new TimerTask()
				{
					@Override
					public void run() { ClientApi.this.clientLevelLoadEvent(levelWrapper); }
				}, FIRST_LEVEL_LOAD_DELAY_IN_MS);
				return;
			}
			this.firstLevelLoadTimer.cancel();
			//LOGGER.info("[DH-CLIENT-LEVEL] Level load delay completed");
		}
		
		
		try
		{
			//LOGGER.info("[DH-CLIENT-LEVEL] Loading client level [" + levelWrapper + "]-[" + levelWrapper.getDhIdentifier() + "].");
			
			AbstractDhWorld world = SharedApi.getAbstractDhWorld();
			//LOGGER.info("[DH-CLIENT-LEVEL] Current DH world: " + world);
			
			if (world != null)
			{
				if (!this.pluginChannelApi.allowLevelLoading(levelWrapper))
				{
					//LOGGER.info("[DH-CLIENT-LEVEL] Levels in this connection are managed by the server, skipping auto-load.");
					
					// Instead of attempting to load themselves, send the config and wait for a server provided level key.
					((DhClientWorld) world).networkState.sendConfigMessage();
					return;
				}
				
				
				//LOGGER.info("[DH-CLIENT-LEVEL] Loading level into DH world...");
				world.getOrLoadLevel(levelWrapper);
				//LOGGER.info("[DH-CLIENT-LEVEL] Level loaded, firing DhApiLevelLoadEvent");
				ApiEventInjector.INSTANCE.fireAllEvents(DhApiLevelLoadEvent.class, new DhApiLevelLoadEvent.EventParam(levelWrapper));
				
				//LOGGER.info("[DH-CLIENT-LEVEL] Loading waiting chunks for level...");
				this.loadWaitingChunksForLevel(levelWrapper);
				//LOGGER.info("[DH-CLIENT-LEVEL] ========== CLIENT LEVEL LOAD EVENT COMPLETE ==========");
			}
			else
			{
				LOGGER.warn("[DH-CLIENT-LEVEL] DH world is null - adding to waiting levels");
				this.waitingClientLevels.add(levelWrapper);
			}
		}
		catch (Exception e)
		{
			// handle errors here to prevent blowing up a mixin or API up stream
			LOGGER.error("Unexpected error in ClientApi.clientLevelLoadEvent(), error: "+e.getMessage(), e);
		}
	}
	private void loadWaitingChunksForLevel(IClientLevelWrapper level)
	{
		HashSet<Pair<IClientLevelWrapper, DhChunkPos>> keysToRemove = new HashSet<>();
		for (Pair<IClientLevelWrapper, DhChunkPos> levelChunkPair : this.waitingChunkByClientLevelAndPos.keySet())
		{
			// only load chunks that came from this level
			IClientLevelWrapper levelWrapper = levelChunkPair.first;
			if (levelWrapper.equals(level))
			{
				IChunkWrapper chunkWrapper = this.waitingChunkByClientLevelAndPos.get(levelChunkPair);
				SharedApi.INSTANCE.chunkLoadEvent(chunkWrapper, levelWrapper);
				keysToRemove.add(levelChunkPair);
			}
		}
		LOGGER.info("Loaded [" + keysToRemove.size() + "] waiting chunk wrappers.");
		
		for (Pair<IClientLevelWrapper, DhChunkPos> keyToRemove : keysToRemove)
		{
			this.waitingChunkByClientLevelAndPos.remove(keyToRemove);
		}
	}
	
	
	
	//============//
	// networking //
	//============//
	
	/**
	 * Forwards a decoded message into the registered handlers.
	 *
	 * @see MessageRegistry
	 */
	public void pluginMessageReceived(@NotNull AbstractNetworkMessage message)
	{
		NetworkSession networkSession = this.pluginChannelApi.networkSession;
		if (networkSession != null)
		{
			networkSession.tryHandleMessage(message);
		}
	}
	
	
	
	//===============//
	// LOD rendering //
	//===============//
	

	/**
	 * Builds the normal DH render parameters and performs only the bounded
	 * opaque semantic traversal needed by the Rust Vulkan whole-frame route.
	 * It intentionally skips GLProxy work and never invokes Java rendering.
	 */
	public boolean collectRustOpaqueLodsForWholeFrame()
	{
		if (this.rendererDisabledBecauseOfExceptions
			|| Config.Client.Advanced.Debugging.rendererMode.get() != EDhApiRendererMode.DEFAULT)
		{
			net.vulkanic.world.DistantHorizonsSemanticCollector.recordRustOpaqueRouteRejected(
				"renderer-disabled-or-nondefault-mode", 0
			);
			return false;
		}
		RenderParams renderParams = new RenderParams(
			EDhApiRenderPass.OPAQUE_AND_TRANSPARENT,
			RENDER_STATE.frameTime,
			RENDER_STATE.mcProjectionMatrix,
			RENDER_STATE.mcModelViewMatrix,
			RENDER_STATE.clientLevelWrapper
		);
		String validationMessage = renderParams.getSemanticTraversalValidationErrorMessage();
		if (validationMessage != null)
		{
			this.lastRenderParamValidationMessage = validationMessage;
			net.vulkanic.world.DistantHorizonsSemanticCollector.recordRustOpaqueRouteRejected(
				"render-param-validation:" + validationMessage, 0
			);
			return false;
		}
		this.lastRenderParamValidationMessage = null;
		return LodRenderer.INSTANCE.collectRustOpaqueRouteForWholeFrame(renderParams, MC_CLIENT.getProfiler());
	}
	
	
	
	
	
	//================//
	// fade rendering //
	//================//
	
	
	
	
	//=================//
	//    DEBUG USE    //
	//=================//
	
	/** Trigger once on key press, with CLIENT PLAYER. */
	public void keyPressedEvent(int glfwKey)
	{
		if (!Config.Client.Advanced.Debugging.enableDebugKeybindings.get())
		{
			// keybindings are disabled
			return;
		}
		
		
		if (glfwKey == GLFW.GLFW_KEY_F8)
		{
			Config.Client.Advanced.Debugging.debugRendering.set(EDhApiDebugRendering.next(Config.Client.Advanced.Debugging.debugRendering.get()));
			MC_CLIENT.sendChatMessage("F8: Set debug mode to " + Config.Client.Advanced.Debugging.debugRendering.get());
		}
		else if (glfwKey == GLFW.GLFW_KEY_F6)
		{
			Config.Client.Advanced.Debugging.rendererMode.set(EDhApiRendererMode.next(Config.Client.Advanced.Debugging.rendererMode.get()));
			MC_CLIENT.sendChatMessage("F6: Set rendering to " + Config.Client.Advanced.Debugging.rendererMode.get());
		}
		else if (glfwKey == GLFW.GLFW_KEY_P)
		{
			prefLoggerEnabled = !prefLoggerEnabled;
			MC_CLIENT.sendChatMessage("P: Debug Pref Logger is " + (prefLoggerEnabled ? "enabled" : "disabled"));
		}
	}
	
	
	
	/** 
	 * Queues the given message to appear in chat the next valid frame.
	 * Useful for queueing up messages that may be triggered before the user has loaded into the world. 
	 */
	public void showChatMessageNextFrame(String chatMessage) { this.chatMessageQueueForNextFrame.add(chatMessage); }
	
	/**
	 * Similar to {@link ClientApi#showChatMessageNextFrame(String)} but appears above the toolbar.
	 */
	public void showOverlayMessageNextFrame(String message) { this.overlayMessageQueueForNextFrame.add(message); }
	
	
	
}
