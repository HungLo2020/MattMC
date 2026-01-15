package net.distanthorizons.core.world;

import net.distanthorizons.core.api.internal.ClientApi;
import net.distanthorizons.core.file.structure.ClientOnlySaveStructure;
import net.distanthorizons.core.level.DhClientLevel;
import net.distanthorizons.core.level.IDhLevel;
import net.distanthorizons.core.multiplayer.client.ClientNetworkState;
import net.distanthorizons.core.util.TimerUtil;
import net.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DhClientWorld extends AbstractDhWorld implements IDhClientWorld
{
	private final ConcurrentHashMap<IClientLevelWrapper, DhClientLevel> levels;
	public final ClientOnlySaveStructure saveStructure;
	public final ClientNetworkState networkState = new ClientNetworkState();
	
	private final Timer clientTickTimer = TimerUtil.CreateTimer("ClientTickTimer");
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public DhClientWorld()
	{
		super(EWorldEnvironment.CLIENT_ONLY);
		
		this.saveStructure = new ClientOnlySaveStructure();
		this.levels = new ConcurrentHashMap<>();
		
		LOGGER.info("Started DhWorld of type " + this.environment);
		
		this.clientTickTimer.scheduleAtFixedRate(new TimerTask()
		{
			@Override
			public void run()
			{
				DhClientWorld.this.levels.values().forEach(DhClientLevel::clientTick);
			}
		}, 0, IDhClientWorld.TICK_RATE_IN_MS);
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public DhClientLevel getOrLoadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IClientLevelWrapper))
		{
			return null;
		}
		
		return this.levels.computeIfAbsent((IClientLevelWrapper) wrapper,
			(clientLevelWrapper) ->
			{
				try
				{
					return new DhClientLevel(this.saveStructure, clientLevelWrapper, this.networkState);
				}
				catch (Exception e)
				{
					LOGGER.fatal("Failed to load client level, error: ["+e.getMessage()+"].", e);
					
					ClientApi.INSTANCE.showChatMessageNextFrame(// red text		
						"\u00A7c" + "Distant Horizons: Client level loading failed." + "\u00A7r \n" +
							"Unable to load level ["+clientLevelWrapper.getDhIdentifier()+"], LODs may not appear. See log for more information.");
					
					return null;
				}
			});
	}
	
	@Override
	public DhClientLevel getLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IClientLevelWrapper))
		{
			return null;
		}
		
		return this.levels.get(wrapper);
	}
	
	@Override
	public Iterable<? extends IDhLevel> getAllLoadedLevels() { return this.levels.values(); }
	@Override
	public int getLoadedLevelCount() { return this.levels.size(); }
	
	@Override
	public void unloadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IClientLevelWrapper))
		{
			return;
		}
		
		if (this.levels.containsKey(wrapper))
		{
			LOGGER.info("Unloading level " + this.levels.get(wrapper));
			wrapper.onUnload();
			this.levels.remove(wrapper).close();
		}
	}
	
	@Override
	public void addDebugMenuStringsToList(List<String> messageList)
	{
		super.addDebugMenuStringsToList(messageList);
		this.networkState.addDebugMenuStringsToList(messageList);
	}
	
	@Override
	public void close()
	{
		this.networkState.close();
		
		ArrayList<CompletableFuture<Void>> closeFutures = new ArrayList<>();
		for (DhClientLevel dhClientLevel : this.levels.values())
		{
			// level wrapper shouldn't be null, but just in case
			IClientLevelWrapper clientLevelWrapper = dhClientLevel.getClientLevelWrapper();
			if (clientLevelWrapper != null)
			{
				clientLevelWrapper.onUnload();
			}
			
			
			// close levels asynchronously to speed up
			// shutdown on servers with a lot of levels
			CompletableFuture<Void> closeFuture = new CompletableFuture<>();
			Thread closeThread = new Thread(() ->
			{
				dhClientLevel.close();
				closeFuture.complete(null);
			}, "level shutdown");
			closeThread.start();
			closeFutures.add(closeFuture);
		}
		
		// wait for all the levels to finish closing
		for (CompletableFuture<Void> future : closeFutures)
		{
			future.join();
		}
		
		this.levels.clear();
		this.clientTickTimer.cancel();
		LOGGER.info("Closed DhWorld of type [" + this.environment + "].");
	}
	
}
