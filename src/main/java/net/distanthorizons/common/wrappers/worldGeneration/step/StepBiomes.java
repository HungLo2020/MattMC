package net.distanthorizons.common.wrappers.worldGeneration.step;

import java.util.ArrayList;

import net.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import net.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import net.distanthorizons.common.wrappers.worldGeneration.params.ThreadWorldGenParams;

import net.distanthorizons.common.wrappers.worldGeneration.mimicObject.DhLitWorldGenRegion;
import net.distanthorizons.core.util.gridList.ArrayGridList;
import net.minecraft.world.level.chunk.ChunkAccess;

import net.minecraft.world.level.levelgen.blending.Blender;

import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class StepBiomes extends AbstractWorldGenStep
{
	private final BatchGenerationEnvironment environment;
	
	public static final ChunkStatus STATUS = ChunkStatus.BIOMES;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public StepBiomes(BatchGenerationEnvironment batchGenerationEnvironment) { this.environment = batchGenerationEnvironment; }
	
	
	
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
			
			
			chunk = this.environment.confirmFutureWasRunSynchronously(
						this.environment.globalParams.generator.createBiomes(
							this.environment.globalParams.randomState, 
							Blender.of(worldGenRegion),
							tParams.structFeatManager.forWorldGenRegion(worldGenRegion), 
							chunk)
					);
		}
	}
	
}