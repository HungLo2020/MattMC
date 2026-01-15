package net.distanthorizons.core.world;

import net.distanthorizons.core.api.internal.ClientApi;
import net.distanthorizons.core.level.DhServerLevel;
import net.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import org.jetbrains.annotations.NotNull;

public class DhServerWorld extends AbstractDhServerWorld<DhServerLevel>
{
	//==============//
	// constructors //
	//==============//
	
	public DhServerWorld()
	{
		super(EWorldEnvironment.SERVER_ONLY);
		LOGGER.info("Started ["+DhServerWorld.class.getSimpleName()+"] of type ["+this.environment+"].");
	}
	
	
	
	//================//
	// level handling //
	//================//
	
	@Override
	public DhServerLevel getOrLoadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IServerLevelWrapper))
		{
			return null;
		}
		
		return this.dhLevelByLevelWrapper.computeIfAbsent(wrapper, 
			(serverLevelWrapper) ->
			{
				try
				{
					return new DhServerLevel(this.saveStructure, (IServerLevelWrapper) serverLevelWrapper, this.getServerPlayerStateManager());
				}
				catch (Exception e)
				{
					LOGGER.fatal("Failed to load server level, error: ["+e.getMessage()+"].", e);
					
					ClientApi.INSTANCE.showChatMessageNextFrame(// red text		
						"\u00A7c" + "Distant Horizons: Server level loading failed." + "\u00A7r \n" +
							"Unable to load level ["+serverLevelWrapper.getDhIdentifier()+"], LODs may not appear. See log for more information.");
					
					return null;
				}
			});
	}
	
	@Override
	public void unloadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IServerLevelWrapper))
		{
			return;
		}
		
		if (this.dhLevelByLevelWrapper.containsKey(wrapper))
		{
			DhServerLevel level = this.dhLevelByLevelWrapper.get(wrapper);
			wrapper.onUnload();
			this.dhLevelByLevelWrapper.remove(wrapper).close();
		}
	}
	
}
