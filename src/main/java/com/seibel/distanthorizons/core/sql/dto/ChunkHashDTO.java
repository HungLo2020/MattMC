package com.seibel.distanthorizons.core.sql.dto;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;

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
