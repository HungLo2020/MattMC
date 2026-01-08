package com.seibel.distanthorizons.common.wrappers.worldGeneration.step;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.params.ThreadWorldGenParams;

import com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject.DhLitWorldGenRegion;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.gridList.ArrayGridList;
import net.minecraft.world.level.chunk.ChunkAccess;
import com.seibel.distanthorizons.core.logging.DhLogger;

import net.minecraft.world.level.chunk.status.ChunkStatus;


public final class StepStructureStart extends AbstractWorldGenStep
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	private static final ChunkStatus STATUS = ChunkStatus.STRUCTURE_STARTS;
	private static final ReentrantLock STRUCTURE_PLACEMENT_LOCK = new ReentrantLock();
	
	private final BatchGenerationEnvironment environment;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public StepStructureStart(BatchGenerationEnvironment batchGenerationEnvironment) { this.environment = batchGenerationEnvironment; }
	
	
	
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
		
		// TODO should be put in wrapped environment so we can skip some other world gen steps
		//  SURFACE wouldn't need structure generation either
		if (!this.environment.globalParams.worldOptions.generateStructures())
		{
			return;
		}
		
		
		
		for (ChunkWrapper chunkWrapper : chunksToDo)
		{
			ChunkAccess chunk = chunkWrapper.getChunk();
			
			// hopefully this shouldn't cause any performance issues (this step is generally quite quick so hopefully it should be fine)
			// and should prevent some concurrency issues
			STRUCTURE_PLACEMENT_LOCK.lock();
			
			this.environment.globalParams.generator.createStructures(this.environment.globalParams.registry,
					this.environment.globalParams.mcServerLevel.getChunkSource().getGeneratorState(),
					tParams.structFeatManager, chunk, this.environment.globalParams.structures, 
					this.environment.globalParams.mcServerLevel.dimension());
			
			try
			{
				tParams.structCheck.onStructureLoad(chunk.getPos(), chunk.getAllStarts());
			}
			catch (ArrayIndexOutOfBoundsException firstEx)
			{
				// There's a rare issue with StructStart where it throws ArrayIndexOutOfBounds
				// This means the structFeat is corrupted (For some reason) and I need to reset it.
				// TODO: Figure out in the future why this happens even though I am using new structFeat - OLD
				
				// reset the structureStart
				tParams.recreateStructureCheck();
				
				try
				{
					// try running the structure logic again
					tParams.structCheck.onStructureLoad(chunk.getPos(), chunk.getAllStarts());
				}
				catch (ArrayIndexOutOfBoundsException secondEx)
				{
					// the structure logic failed again, log it and move on
					LOGGER.error("Unable to create structure starts for " + chunk.getPos() + ". This is an error with MC's world generation. Ignoring and continuing generation. Error: " + secondEx.getMessage()); // don't log the full stack trace since it is long and will generally end up in MC's code
				}
			}
			
			
			STRUCTURE_PLACEMENT_LOCK.unlock();
		}
	}
	
}