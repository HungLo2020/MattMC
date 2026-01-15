package net.distanthorizons.common.wrappers.worldGeneration.step;

import net.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import net.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import net.distanthorizons.common.wrappers.worldGeneration.params.ThreadWorldGenParams;
import net.distanthorizons.common.wrappers.worldGeneration.mimicObject.DhLitWorldGenRegion;
import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.core.util.gridList.ArrayGridList;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.distanthorizons.core.logging.DhLogger;

import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;


public final class StepFeatures extends AbstractWorldGenStep
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	public static final ChunkStatus STATUS = ChunkStatus.FEATURES;
	
	private final BatchGenerationEnvironment environment;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public StepFeatures(BatchGenerationEnvironment batchGenerationEnvironment) { this.environment = batchGenerationEnvironment; }
	
	
	
	//==================//
	// abstract methods //
	//==================//
	
	@Override
	public ChunkStatus getChunkStatus() { return STATUS; }
	
	@Override
	public void generateGroup(
			ThreadWorldGenParams tParams, DhLitWorldGenRegion worldGenRegion,
			ArrayGridList<ChunkWrapper> chunkWrappers)
	{
		ArrayList<ChunkWrapper> chunksToDo = this.getChunkWrappersToGenerate(chunkWrappers);
		for (ChunkWrapper chunkWrapper : chunksToDo)
		{
			ChunkAccess chunk = chunkWrapper.getChunk();
			
			
			try
			{
				if (worldGenRegion.hasChunk(chunkWrapper.getChunkPos().getX(), chunkWrapper.getChunkPos().getZ()))
				{
					this.environment.globalParams.generator.applyBiomeDecoration(worldGenRegion, chunk, tParams.structFeatManager.forWorldGenRegion(worldGenRegion));
				}
				else
				{
					LOGGER.warn("Unable to generate features for chunk at pos ["+chunkWrapper.getChunkPos()+"], world gen region doesn't contain the chunk.");
				}
				
				Heightmap.primeHeightmaps(chunk, STATUS.heightmapsAfter());
			}
			catch (ConcurrentModificationException e) // ReportedException
			{
				// TODO
			}
			catch (Exception e)
			{
				LOGGER.warn("Unexpected issue when generating features for chunk at pos ["+chunkWrapper.getChunkPos()+"], error: ["+e.getMessage()+"].", e);
				// FIXME: Features concurrent modification issue. Something about cocobeans might just
				//  error out. For now just retry.
			}
		}
	}
	
}