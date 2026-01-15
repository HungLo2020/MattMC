package net.distanthorizons.core.level;

import net.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * Handles level overrides initiated by servers that
 * support differentiating between different levels.
 */
public interface IKeyedClientLevelManager extends IBindable
{
	IServerKeyedClientLevel getServerKeyedLevel();
	/** Called when a client level is wrapped by a ServerEnhancedClientLevel, for integration into mod internals. */
	IServerKeyedClientLevel setServerKeyedLevel(IClientLevelWrapper clientLevel, String serverKey, String levelKey);
	
	void clearKeyedLevel();
	boolean isEnabled();
	void disable();
	
}
