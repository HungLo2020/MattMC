package net.distanthorizons.core.sql.dto;

import net.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import net.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import net.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV1;
import net.distanthorizons.core.pos.DhSectionPos;
import net.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;

import java.io.IOException;

/**
 * Handles storing{@link FullDataSourceV1}'s in the database.
 */
public class FullDataSourceV1DTO implements IBaseDTO<Long>
{
	public long pos;
	public int checksum;
	public byte dataDetailLevel;
	public EDhApiWorldGenerationStep worldGenStep;
	
	// Loader stuff //
	/** indicates what data is held in this file, this is generally the data's name */
	public String dataType;
	public byte binaryDataFormatVersion;
	
	public final byte[] dataArray;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FullDataSourceV1DTO(long pos, int checksum, byte dataDetailLevel, EDhApiWorldGenerationStep worldGenStep, String dataType, byte binaryDataFormatVersion, byte[] dataArray)
	{
		this.pos = pos;
		this.checksum = checksum;
		this.dataDetailLevel = dataDetailLevel;
		this.worldGenStep = worldGenStep;
		
		this.dataType = dataType;
		this.binaryDataFormatVersion = binaryDataFormatVersion;
		
		this.dataArray = dataArray;
	}
	
	
	/** @return a stream for the data contained in this DTO. */
	public DhDataInputStream getInputStream() throws IOException
	{
		DhDataInputStream compressedStream = DhDataInputStream.create(this.dataArray, EDhApiDataCompressionMode.LZ4); // LZ4 was used by DH before 2.1.0 and as such must be used until the render data format is changed to record the compressor
		return compressedStream;
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public Long getKey() { return this.pos; }
	@Override
	public String getKeyDisplayString() { return DhSectionPos.toString(this.pos); }
	
	@Override 
	public void close()
	{ /* no closing needed */ }
	
}
