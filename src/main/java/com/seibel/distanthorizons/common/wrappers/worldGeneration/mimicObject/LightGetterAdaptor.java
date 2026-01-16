package com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject;

import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IStarlightAccessor;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.LightChunkGetter;

import net.minecraft.world.level.LevelHeightAccessor;

import net.minecraft.world.level.chunk.LightChunk;

import net.minecraft.world.level.chunk.status.ChunkStatus;



public class LightGetterAdaptor implements LightChunkGetter
{
	private final BlockGetter heightGetter;
	public DhLitWorldGenRegion genRegion = null;
	final boolean shouldReturnNull;
	
	public LightGetterAdaptor(BlockGetter heightAccessor)
	{
		this.heightGetter = heightAccessor;
		shouldReturnNull = ModAccessorInjector.INSTANCE.get(IStarlightAccessor.class) != null;
	}
	
	public void setRegion(DhLitWorldGenRegion region)
	{
		genRegion = region;
	}
	
	@Override
	public LightChunk getChunkForLighting(int chunkX, int chunkZ)
	{
		if (genRegion == null)
			throw new IllegalStateException("World Gen region has not been set!");
		// May be null
		return genRegion.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false);
	}
	
	@Override
	public BlockGetter getLevel()
	{
		return shouldReturnNull ? null : (genRegion != null ? genRegion : heightGetter);
	}
	
	public LevelHeightAccessor getLevelHeightAccessor()
	{
		return heightGetter;
	}
}