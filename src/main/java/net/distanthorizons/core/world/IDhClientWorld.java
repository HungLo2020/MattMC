package net.distanthorizons.core.world;

import net.distanthorizons.core.level.IDhClientLevel;
import net.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;

public interface IDhClientWorld extends IDhWorld
{
	/** how long in between client ticks in milliseconds */
	long TICK_RATE_IN_MS = 100L;
	
	default IDhClientLevel getOrLoadClientLevel(ILevelWrapper levelWrapper) { return (IDhClientLevel) this.getOrLoadLevel(levelWrapper); }
	default IDhClientLevel getClientLevel(ILevelWrapper levelWrapper) { return (IDhClientLevel) this.getLevel(levelWrapper); }
	
}
