package com.seibel.distanthorizons.core.wrapperInterfaces.misc;

import net.distant_horizons.api.interfaces.IDhApiUnsafeWrapper;
import net.distant_horizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import net.distant_horizons.core.wrapperInterfaces.chunk.IChunkWrapper;

/**
 * Currently this wrapper is just used to prevent 
 * accidentally passing in the wrong object to
 * {@link IChunkWrapper#getBlockState(int, int, int, IMutableBlockPosWrapper, IBlockStateWrapper)}
 */
public interface IMutableBlockPosWrapper extends IDhApiUnsafeWrapper
{
	
	
}
