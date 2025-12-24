package com.seibel.distanthorizons.core.api.internal.chunkUpdating;

import net.distant_horizons.core.level.IDhLevel;
import net.distant_horizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class ChunkUpdateData
{
	public IChunkWrapper chunkWrapper;
	@Nullable
	public ArrayList<IChunkWrapper> neighborChunkList;
	public IDhLevel dhLevel;
	public boolean canGetNeighboringChunks;
	
	
	
	public ChunkUpdateData(IChunkWrapper chunkWrapper, @Nullable ArrayList<IChunkWrapper> neighborChunkList, IDhLevel dhLevel, boolean canGetNeighborChunks)
	{
		this.chunkWrapper = chunkWrapper;
		this.neighborChunkList = neighborChunkList;
		this.dhLevel = dhLevel;
		this.canGetNeighboringChunks = canGetNeighborChunks;
	}
}
