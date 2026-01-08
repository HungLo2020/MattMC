package com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor;

import com.seibel.distanthorizons.core.pos.DhChunkPos;

import java.util.HashSet;

public interface ISodiumAccessor extends IModAccessor
{
	/** A temporary overwrite for a config in sodium 0.5 to fix their terrain from showing, will be removed once a proper fix is added */
	void setFogOcclusion(boolean b); // FIXME
	
}
