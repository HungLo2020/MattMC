package com.seibel.distanthorizons.fabric.testing;

import net.distant_horizons.api.DhApi;
import net.distant_horizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import net.distant_horizons.api.enums.worldGeneration.EDhApiWorldGeneratorReturnType;
import net.distant_horizons.api.interfaces.block.IDhApiBiomeWrapper;
import net.distant_horizons.api.interfaces.block.IDhApiBlockStateWrapper;
import net.distant_horizons.api.interfaces.override.worldGenerator.AbstractDhApiChunkWorldGenerator;
import net.distant_horizons.api.interfaces.world.IDhApiLevelWrapper;
import net.distant_horizons.api.objects.data.DhApiChunk;
import net.distant_horizons.api.objects.data.DhApiTerrainDataPoint;
import net.distant_horizons.common.wrappers.world.ServerLevelWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;

public class TestChunkWorldGenerator extends AbstractDhApiChunkWorldGenerator
{
	private final ServerLevel level;
	private final IDhApiLevelWrapper levelWrapper;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public TestChunkWorldGenerator(ServerLevel level)
	{
		this.level = level;
		this.levelWrapper = ServerLevelWrapper.getWrapper(level);
	}
	
	
	
	//============//
	// properties //
	//============//
	
	@Override
	public EDhApiWorldGeneratorReturnType getReturnType() { return EDhApiWorldGeneratorReturnType.API_CHUNKS; }
	
	@Override 
	public boolean runApiValidation() { return true; }
	
	
	
	//==================//
	// chunk generation //
	//==================//
	
	@Override
	public Object[] generateChunk(int chunkX, int chunkZ, EDhApiDistantGeneratorMode eDhApiDistantGeneratorMode)
	{
		ChunkAccess chunk = this.level.getChunk(chunkX, chunkZ);
		return new Object[] { chunk, this.level };
	}
	
	@Override
	public DhApiChunk generateApiChunk(int chunkPosX, int chunkPosZ, EDhApiDistantGeneratorMode generatorMode)
	{
		// this test is only validated for 1.18.2 and up 
		// (and it is only needed when testing world gen overrides/API chunks, so it isn't normally needed)
		ChunkAccess chunk = this.level.getChunk(chunkPosX, chunkPosZ);
		
		
		int minBuildHeight = this.levelWrapper.getMinHeight();
		int maxBuildHeight = this.levelWrapper.getMaxHeight();
		
		DhApiChunk apiChunk = DhApiChunk.create(chunkPosX, chunkPosZ, minBuildHeight, maxBuildHeight);
		for (int x = 0; x < 16; x++)
		{
			for (int z = 0; z < 16; z++)
			{
				ArrayList<DhApiTerrainDataPoint> dataPoints = new ArrayList<>();
				
				IDhApiBlockStateWrapper block = null;
				IDhApiBiomeWrapper biome = null;
				
				for (int y = minBuildHeight; y < maxBuildHeight; y++)
				{
					block = DhApi.Delayed.wrapperFactory.getBlockStateWrapper(new Object[]{chunk.getBlockState(new BlockPos(x, y, z))}, this.levelWrapper);
					biome = DhApi.Delayed.wrapperFactory.getBiomeWrapper(new Object[]{chunk.getNoiseBiome(x, y, z)}, this.levelWrapper);
					dataPoints.add(DhApiTerrainDataPoint.create((byte) 0, 0, 15, y, y + 1, block, biome));
				}
				
				apiChunk.setDataPoints(x, z, dataPoints);
			}
		}
		return apiChunk;
	}
	
	@Override
	public void preGeneratorTaskStart() { /* do nothing */ }
	
	
	
	//=========//
	// cleanup //
	//=========//
	
	@Override
	public void close() { /* do nothing */ }
	
}
