/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import net.distant_horizons.common.wrappers.chunk.ChunkWrapper;
import net.distant_horizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import net.distant_horizons.core.logging.DhLoggerBuilder;
import net.distant_horizons.core.util.LodUtil;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.distant_horizons.core.logging.DhLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

import net.minecraft.world.level.chunk.status.*;

import net.minecraft.util.StaticCache2D;
import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.GenerationChunkHolder;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;


public class DhLitWorldGenRegion extends WorldGenRegion
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static ChunkStatus debugTriggeredForStatus = null;
	
	
	public final ServerLevel serverLevel;
	public final DummyLightEngine lightEngine;
	public final BatchGenerationEnvironment.IEmptyChunkRetrievalFunc generator;
	public final int writeRadius;
	public final int size;
	
	private final ChunkPos firstPos;
	private final List<ChunkAccess> cache;
	private final Long2ObjectOpenHashMap<ChunkAccess> chunkMap = new Long2ObjectOpenHashMap<ChunkAccess>();
	
	/** 
	 * Present to reduce the chance that we accidentally break underlying MC code that isn't thread safe, 
	 * specifically: "it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap.getAndMoveToFirst()"
	 */
	private final ReentrantLock getChunkLock = new ReentrantLock();
	
	
	
	
	public DhLitWorldGenRegion(
			int centerChunkX, int centerChunkZ,
			ChunkAccess centerChunk,
			ServerLevel serverLevel, DummyLightEngine lightEngine,
			List<ChunkAccess> chunkList, ChunkStatus chunkStatus, int writeRadius,
			BatchGenerationEnvironment.IEmptyChunkRetrievalFunc generator)
	{
		super(serverLevel, 
				StaticCache2D.create(
					centerChunkX, centerChunkZ,
					writeRadius * 2, (x,z) -> new DhGenerationChunkHolder(new ChunkPos(x, z))), 
				new ChunkStep(chunkStatus,
						// reverse is needed because MC uses the index of the chunkStatus to determine how many items are in the list instead of the actual list count
						new ChunkDependencies(ImmutableList.copyOf(ChunkStatus.getStatusList()).reverse()),
						new ChunkDependencies(ImmutableList.copyOf(ChunkStatus.getStatusList()).reverse()),
						writeRadius, (WorldGenContext var1, ChunkStep var2, StaticCache2D<GenerationChunkHolder> var3, ChunkAccess var4) -> null),
				centerChunk);
		
		this.firstPos = chunkList.get(0).getPos();
		this.serverLevel = serverLevel;
		this.generator = generator;
		this.lightEngine = lightEngine;
		this.writeRadius = writeRadius;
		this.cache = chunkList;
		this.size = Mth.floor(Math.sqrt(chunkList.size()));
	}
	
	
	
	// Bypass BCLib mixin overrides.
	@Override
	public boolean ensureCanWrite(BlockPos blockPos)
	{
		int i = SectionPos.blockToSectionCoord(blockPos.getX());
		int j = SectionPos.blockToSectionCoord(blockPos.getZ());
		ChunkPos chunkPos = this.getCenter();
		ChunkAccess center = this.getChunk(chunkPos.x, chunkPos.z);
		int k = Math.abs(chunkPos.x - i);
		int l = Math.abs(chunkPos.z - j);
		if (k > this.writeRadius || l > this.writeRadius)
		{
			return false;
		}
		if (center.isUpgrading())
		{
			LevelHeightAccessor levelHeightAccessor = center.getHeightAccessorForGeneration();
			
			int minY;
			int maxY;
			minY = levelHeightAccessor.getMinY();
			maxY = levelHeightAccessor.getMaxY();
			
			if (blockPos.getY() < minY || blockPos.getY() >= maxY)
			{
				return false;
			}
		}
		return true;
	}
	
	@Override
	@NotNull
	public LevelTickAccess<Block> getBlockTicks()
	{
		// DH world gen doesn't need ticking, so return the BlackholeTickAccess list (which causes all ticks to be ignored).
		// If this isn't done the server may attempt to tick chunks outside the vanilla render distance,
		// which can throw warnings or cause other issues
		return BlackholeTickAccess.emptyLevelList();
	}
	
	@Override
	@NotNull
	public LevelTickAccess<Fluid> getFluidTicks() { return BlackholeTickAccess.emptyLevelList(); }
	
	// TODO Check this
//	@Override
//	public List<? extends StructureStart<?>> startsForFeature(SectionPos sectionPos,
//			StructureFeature<?> structureFeature) {
//		return structFeat.startsForFeature(sectionPos, structureFeature);
//	}
	
	// Skip updating the related tile entities
	@Override
	public boolean setBlock(BlockPos blockPos, BlockState blockState, int i, int j)
	{
		ChunkAccess chunkAccess = this.getChunk(blockPos);
		if (chunkAccess instanceof LevelChunk)
		{
			return true;
		}
		
		chunkAccess.setBlockState(blockPos, blockState, /*flags*/0);
		
		// This is for post ticking for water on gen and stuff like that. Not enabled
		// for now.
		// if (blockState.hasPostProcess(this, blockPos))
		// this.getChunk(blockPos).markPosForPostprocessing(blockPos);
		return true;
	}
	
	// Skip Dropping the item on destroy
	@Override
	public boolean destroyBlock(BlockPos blockPos, boolean bl, @Nullable Entity entity, int i)
	{
		BlockState blockState = this.getBlockState(blockPos);
		if (blockState.isAir())
		{
			return false;
		}
		return this.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3, i);
	}
	
	// Skip BlockEntity stuff. It aren't really needed
	@Override
	public BlockEntity getBlockEntity(BlockPos blockPos)
	{
		BlockState blockState = this.getBlockState(blockPos);
		
		// This is a bypass for the spawner block since MC complains about not having it
		if (blockState.getBlock() instanceof SpawnerBlock)
		{
			return ((EntityBlock) blockState.getBlock()).newBlockEntity(blockPos, blockState);
		}
		else return null;
	}
	
	/**
	 * This needs to be manually overridden to make sure Lithium 0.11.2 and lower
	 * don't try to get null chunks. <br><br>
	 *
	 * Problematic Lithium code was removed in 0.13.0 (MC 1.21.1) and higher: <br>
	 * https://github.com/CaffeineMC/lithium-fabric/commit/b7cfd53a1ed0197e1d13dea2799b898eb52ecab3
	 */
	@NotNull
	@Override
	public BlockState getBlockState(BlockPos blockPos)
	{
		int chunkX = SectionPos.blockToSectionCoord(blockPos.getX());
		int chunkZ = SectionPos.blockToSectionCoord(blockPos.getZ());
		return this.getChunk(chunkX, chunkZ).getBlockState(blockPos);
	}
	
	/** Skip BlockEntity stuff. They aren't needed for our use case. */
	@Override
	public boolean addFreshEntity(@NotNull Entity entity) { return true; }
	
	// Allays have empty chunks even if it's outside the worldGenRegion
	// @Override
	// public boolean hasChunk(int i, int j) {
	// return true;
	// }
	
	// Override to ensure no other mod mixins cause skipping the overrided
	// getChunk(...)
	@Override
	public @NotNull ChunkAccess getChunk(int chunkX, int chunkZ)
	{
		try
		{
			// lock is to prevent issues with underlying MC code that doesn't support multithreading
			this.getChunkLock.lock();
			return this.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY);
		}
		finally
		{
			this.getChunkLock.unlock();
		}
	}
	
	// Override to ensure no other mod mixins cause skipping the overrided
	// getChunk(...)
	@Override
	public @NotNull ChunkAccess getChunk(int chunkX, int chunkZ, @NotNull ChunkStatus chunkStatus)
	{
		try
		{
			// lock is to prevent issues with underlying MC code that doesn't support multithreading
			this.getChunkLock.lock();
			
			ChunkAccess chunk = this.getChunk(chunkX, chunkZ, chunkStatus, true);
			if (chunk == null)
			{
				LodUtil.assertNotReach("getChunk shouldn't return null values");
			}
			return chunk;
		}
		finally
		{
			this.getChunkLock.unlock();
		}
	}
	
	/** Allows creating empty chunks even if they're outside the worldGenRegion */
	@Override
	@Nullable
	public ChunkAccess getChunk(int chunkX, int chunkZ, @NotNull ChunkStatus chunkStatus, boolean returnNonNull)
	{
		ChunkAccess chunk = this.getChunkAccess(chunkX, chunkZ, chunkStatus, returnNonNull);
		if (chunk instanceof LevelChunk)
		{
			chunk = new ImposterProtoChunk((LevelChunk) chunk, /* allow writes */ false );
		}
		return chunk;
	}
	
	/**
	 * @param returnNonNull if true this method will always return a non-null chunk,
	 *                      if false it will return null if no chunk exists at the given position with the given status 
	 */
	private ChunkAccess getChunkAccess(int chunkX, int chunkZ, ChunkStatus chunkStatus, boolean returnNonNull)
	{
		ChunkAccess chunk = this.superHasChunk(chunkX, chunkZ) ? this.superGetChunk(chunkX, chunkZ) : null;
		if (chunk != null && ChunkWrapper.getStatus(chunk).isOrAfter(chunkStatus))
		{
			return chunk;
		}
		else if (!returnNonNull)
		{
			// no chunk found with the necessary status and null return values are allowed
			return null;
		}
		
		
		// we need a non-null chunk
		if (chunk == null)
		{
			// check memory
			chunk = this.chunkMap.get(ChunkPos.asLong(chunkX, chunkZ));
			if (chunk == null)
			{
				// chunk isn't in memory, generate a new one
				chunk = this.generator.getChunk(chunkX, chunkZ);
				if (chunk == null)
				{
					throw new NullPointerException("The provided generator should not return null!");
				}
				this.chunkMap.put(ChunkPos.asLong(chunkX, chunkZ), chunk);
			}
		}
		
		if (chunkStatus != ChunkStatus.EMPTY 
			&& chunkStatus != debugTriggeredForStatus)
		{
			LOGGER.info("WorldGen requiring [" + chunkStatus + "]"
					+ " is outside the expected range. Returning EMPTY chunk.");
			debugTriggeredForStatus = chunkStatus;
		}
		
		return chunk;
	}
	
	/** Use this instead of super.hasChunk() to bypass C2ME concurrency checks */
	public boolean superHasChunk(int x, int z)
	{
		int k = x - this.firstPos.x;
		int l = z - this.firstPos.z;
		return l >= 0 && l < this.size && k >= 0 && k < this.size;
	}
	
	/** Use this instead of super.getChunk() to bypass C2ME concurrency checks */
	private ChunkAccess superGetChunk(int x, int z)
	{
		int k = x - this.firstPos.x;
		int l = z - this.firstPos.z;
		return this.cache.get(k + l * this.size);
	}
	
	
	/** Overriding allows us to use our own lighting engine */
	@Override
	public @NotNull LevelLightEngine getLightEngine() { return this.lightEngine; }
	
	/** Overriding allows us to use our own lighting engine */
	@Override
	public int getBrightness(@NotNull LightLayer lightLayer, @NotNull BlockPos blockPos) { return 0; }
	
	/** Overriding allows us to use our own lighting engine */
	@Override
	public int getRawBrightness(@NotNull BlockPos blockPos, int i) { return 0; }
	
	/** Overriding allows us to use our own lighting engine */
	@Override
	public boolean canSeeSky(@NotNull BlockPos blockPos)
	{ return (this.getBrightness(LightLayer.SKY, blockPos) >= LodUtil.MAX_MC_LIGHT); }
	
	public int getBlockTint(@NotNull BlockPos blockPos, @NotNull ColorResolver colorResolver)
	{ return this.calculateBlockTint(blockPos, colorResolver); }
	
	private Biome _getBiome(BlockPos pos)
	{
		return this.getBiome(pos).value();
	}
	
	public int calculateBlockTint(BlockPos blockPos, ColorResolver colorResolver)
	{
		int i = (Minecraft.getInstance()).options.biomeBlendRadius().get();
		if (i == 0)
			return colorResolver.getColor((Biome) _getBiome(blockPos), blockPos.getX(), blockPos.getZ());
		int j = (i * 2 + 1) * (i * 2 + 1);
		int k = 0;
		int l = 0;
		int m = 0;
		Cursor3D cursor3D = new Cursor3D(blockPos.getX() - i, blockPos.getY(), blockPos.getZ() - i, blockPos.getX() + i, blockPos.getY(), blockPos.getZ() + i);
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
		while (cursor3D.advance())
		{
			mutableBlockPos.set(cursor3D.nextX(), cursor3D.nextY(), cursor3D.nextZ());
			int n = colorResolver.getColor((Biome) _getBiome((BlockPos) mutableBlockPos), mutableBlockPos.getX(), mutableBlockPos.getZ());
			k += (n & 0xFF0000) >> 16;
			l += (n & 0xFF00) >> 8;
			m += n & 0xFF;
		}
		return (k / j & 0xFF) << 16 | (l / j & 0xFF) << 8 | m / j & 0xFF;
	}
	
}