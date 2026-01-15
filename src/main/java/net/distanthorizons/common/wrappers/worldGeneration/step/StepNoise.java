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

public final class StepNoise extends AbstractWorldGenStep
{
	private static final ChunkStatus STATUS = ChunkStatus.NOISE;
	
	private final BatchGenerationEnvironment environment;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public StepNoise(BatchGenerationEnvironment batchGenerationEnvironment) { this.environment = batchGenerationEnvironment; }
	
	
	
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
						this.environment.globalParams.generator.fillFromNoise(
							Blender.of(worldGenRegion), 
							this.environment.globalParams.randomState,
							tParams.structFeatManager.forWorldGenRegion(worldGenRegion), 
							chunk));
		}
	}
	
}