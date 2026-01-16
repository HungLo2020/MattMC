package com.seibel.distanthorizons.core.world;

import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;

public interface IDhClientWorld extends IDhWorld
{
	/** how long in between client ticks in milliseconds */
	long TICK_RATE_IN_MS = 100L;
	
	default IDhClientLevel getOrLoadClientLevel(ILevelWrapper levelWrapper) { return (IDhClientLevel) this.getOrLoadLevel(levelWrapper); }
	default IDhClientLevel getClientLevel(ILevelWrapper levelWrapper) { return (IDhClientLevel) this.getLevel(levelWrapper); }
	
}
