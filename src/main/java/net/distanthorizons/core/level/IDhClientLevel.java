package net.distanthorizons.core.level;

import net.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Used when running in singleplayer
 * or when connected to a server.
 */
public interface IDhClientLevel extends IDhLevel
{
	void clientTick();
	
	@Nullable
	IClientLevelWrapper getClientLevelWrapper();
	
	/**
	 * Re-creates the color, render data.
	 * This method should be called after resource packs are changed or LOD settings are modified.
	 */
	void clearRenderCache();
	
}
