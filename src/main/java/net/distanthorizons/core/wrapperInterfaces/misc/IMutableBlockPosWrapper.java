package net.distanthorizons.core.wrapperInterfaces.misc;

import net.distanthorizons.api.interfaces.IDhApiUnsafeWrapper;
import net.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import net.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;

/**
 * Currently this wrapper is just used to prevent 
 * accidentally passing in the wrong object to
 * {@link IChunkWrapper#getBlockState(int, int, int, IMutableBlockPosWrapper, IBlockStateWrapper)}
 */
public interface IMutableBlockPosWrapper extends IDhApiUnsafeWrapper
{
	
	
}
