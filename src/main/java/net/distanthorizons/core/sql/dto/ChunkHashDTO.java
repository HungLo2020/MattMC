package net.distanthorizons.core.sql.dto;

import net.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import net.distanthorizons.core.pos.DhChunkPos;

/** handles storing {@link FullDataSourceV2}'s in the database. */
public class ChunkHashDTO implements IBaseDTO<DhChunkPos>
{
	public DhChunkPos pos;
	public int chunkHash;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ChunkHashDTO(DhChunkPos pos, int chunkHash)
	{
		this.pos = pos;
		this.chunkHash = chunkHash;
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override 
	public DhChunkPos getKey() { return this.pos; }
	
	@Override
	public void close()
	{ /* no closing needed */ }
	
	
	
}
