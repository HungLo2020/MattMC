package com.seibel.distanthorizons.core.util;

import net.distant_horizons.core.config.Config;
import net.distant_horizons.core.pos.DhChunkPos;
import net.distant_horizons.core.pos.DhSectionPos;
import net.distant_horizons.core.pos.blockPos.DhBlockPos;

public class WorldGenUtil
{
	
	/** will always return true if a generation max radius isn't set */
	public static boolean isPosInWorldGenRange(
		long requestedPos,
		int centerChunkX, int centerChunkZ,
		int maxChunkRadius)
	{
		if (Config.Common.WorldGenerator.generationMaxChunkRadius.get() <= 0)
		{
			return true;
		}
		
		
		DhBlockPos centerBlockPos = new DhChunkPos(centerChunkX, centerChunkZ).centerBlockPos();
		int blockDistanceFromCenter = DhSectionPos.getChebyshevSignedBlockDistance(requestedPos, centerBlockPos);
		int maxBlockRadius = maxChunkRadius * LodUtil.CHUNK_WIDTH;
		boolean requestInRadius = (blockDistanceFromCenter <= maxBlockRadius);
		return requestInRadius;
	}
	
	
	
}
