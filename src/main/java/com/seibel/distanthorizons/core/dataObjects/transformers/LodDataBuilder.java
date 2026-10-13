package com.seibel.distanthorizons.core.dataObjects.transformers;


import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IMutableBlockPosWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import com.seibel.distanthorizons.core.logging.DhLogger;
import org.jetbrains.annotations.Nullable;

public class LodDataBuilder
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	private static final IWrapperFactory WRAPPER_FACTORY = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
	private static final IBlockStateWrapper AIR = WRAPPER_FACTORY.getAirBlockStateWrapper();
	
	private static boolean getTopErrorLogged = false;
	
	
	
	//============//
	// converters //
	//============//
	
	/**
	 * The chunk's columns built natively, or null when the native producer
	 * declines its inputs (for example biomes not yet generated).
	 */
	@Nullable
	public static net.minecraft.world.level.chunk.NativeDhColumns nativeColumnsFromChunk(ILevelWrapper levelWrapper, IChunkWrapper chunkWrapper)
	{
		// only block lighting is needed here, sky lighting is populated at the data source stage
		LodUtil.assertTrue(chunkWrapper.isDhBlockLightingCorrect(), "Provided chunk's DH Block lighting hasn't been baked.");
		if (!(chunkWrapper instanceof com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper wrapper)) return null;
		return net.minecraft.world.level.chunk.NativeDhColumns.build(levelWrapper, wrapper,
			Config.Common.LodBuilding.worldCompression.get().value);
	}
	
	public static FullDataSourceV2 createFromChunk(ILevelWrapper levelWrapper, IChunkWrapper chunkWrapper)
	{
		var columns = nativeColumnsFromChunk(levelWrapper, chunkWrapper);
		if (columns != null) return FullDataSourceV2.createNative(columns);
		
		
		int sectionPosX = getXOrZSectionPosFromChunkPos(chunkWrapper.getChunkPos().getX());
		int sectionPosZ = getXOrZSectionPosFromChunkPos(chunkWrapper.getChunkPos().getZ());
		long pos = DhSectionPos.encode(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, sectionPosX, sectionPosZ);
		
		FullDataSourceV2 dataSource = FullDataSourceV2.createEmpty(pos);
		dataSource.isEmpty = false; // this will be set to "true" if any blocks are found
		// chunk updates always propagate up
		dataSource.applyToParent = true;
		
		
		
		// compute the chunk dataSource offset
		// this offset is used to determine where in the dataSource this chunk's data should go
		
		// expected offset positions:
		// chunkPos -> offset
		//  5 -> 1
		//  4 -> 0 ---
		//  3 -> 3
		//  2 -> 2
		//  1 -> 1
		//  0 -> 0 ===
		// -1 -> 3
		// -2 -> 2
		// -3 -> 1
		// -4 -> 0 ---
		// -5 -> 3
		
		// Fast modulo calculation using bitwise AND since NUMB_OF_CHUNKS_WIDE is a power of 2 (4)
		// For any number n: n & (2^k - 1) is equivalent to Math.floorMod(n, 2^k)
		// Original: Math.floorMod(x, 4) - Handles negative numbers, gives non-negative result in range [0,3]
		// Bitwise: x & (4-1) - Also gives non-negative result in range [0,3]
		// Example: -5 & 3 = 3, which equals Math.floorMod(-5, 4) = 3
		int chunkOffsetX = chunkWrapper.getChunkPos().getX() & (FullDataSourceV2.NUMB_OF_CHUNKS_WIDE - 1);
		int chunkOffsetZ = chunkWrapper.getChunkPos().getZ() & (FullDataSourceV2.NUMB_OF_CHUNKS_WIDE - 1);
		
		// Convert from chunk coordinates to block coordinates
		chunkOffsetX *= LodUtil.CHUNK_WIDTH;
		chunkOffsetZ *= LodUtil.CHUNK_WIDTH;
		
		
		
		//==========================//
		// populate the data source //
		//==========================//
		
		EDhApiWorldCompressionMode worldCompressionMode = Config.Common.LodBuilding.worldCompression.get();
		
		try
		{
			IMutableBlockPosWrapper mcBlockPos = chunkWrapper.getMutableBlockPosWrapper();
			IBlockStateWrapper previousBlockState = null;
			
			int minBuildHeight = chunkWrapper.getMinNonEmptyHeight();
			int exclusiveMaxBuildHeight = chunkWrapper.getExclusiveMaxBuildHeight();
			int inclusiveMinBuildHeight = chunkWrapper.getInclusiveMinBuildHeight();
			int dataCapacity = chunkWrapper.getHeight() / 4;
			
			for (int relBlockX = 0; relBlockX < LodUtil.CHUNK_WIDTH; relBlockX++)
			{
				for (int relBlockZ = 0; relBlockZ < LodUtil.CHUNK_WIDTH; relBlockZ++)
				{
					// Calculate column position
					int columnX = relBlockX + chunkOffsetX;
					int columnZ = relBlockZ + chunkOffsetZ;
					
					// Get column data
					LongList longs = dataSource.getColumnAtRelPos(columnX, columnZ);
					if (longs == null)
					{
						longs = new LongArrayList(dataCapacity);
					}
					else
					{
						longs.clear();
					}
					
					int lastY = exclusiveMaxBuildHeight;
					IBiomeWrapper currentBiome = chunkWrapper.getBiome(relBlockX, lastY, relBlockZ);
					IBlockStateWrapper currentBlockState = AIR;
					int mappedId = dataSource.mapping.addIfNotPresentAndGetId(currentBiome, currentBlockState);
					
					// Determine lighting (we are at the height limit. There are no torches here, and sky is not obscured.) // TODO: Per face lighting someday?
					byte blockLight = LodUtil.MIN_MC_LIGHT;
					byte skyLight = LodUtil.MAX_MC_LIGHT;
					
					// Get the maximum height from both heightmaps
					int y = Math.max(
							// max between both heightmaps to account for solid invisible blocks (glass)
							// and non-solid opaque blocks (at one point this was stairs, not sure what would fit this now)
							chunkWrapper.getLightBlockingHeightMapValue(relBlockX, relBlockZ),
							chunkWrapper.getSolidHeightMapValue(relBlockX, relBlockZ)
					);
					
					// Go up until we reach open air or the world limit
					IBlockStateWrapper topBlockState = previousBlockState = chunkWrapper.getBlockState(relBlockX, y, relBlockZ, mcBlockPos, previousBlockState);
					while (!topBlockState.isAir() 
							&& y < exclusiveMaxBuildHeight)
					{
						try
						{
							// This is necessary in some edge cases with snow layers and some other blocks that may not appear in the height map but do block light.
							// Interestingly this doesn't appear to be the case in the DhLightingEngine, if this same logic is added there the lighting breaks for the affected blocks.
							y++;
							topBlockState = previousBlockState = chunkWrapper.getBlockState(relBlockX, y, relBlockZ, mcBlockPos, previousBlockState);
						}
						catch (Exception e)
						{
							if (!getTopErrorLogged)
							{
								LOGGER.warn("Unexpected issue in LodDataBuilder, future errors won't be logged. Chunk [" + chunkWrapper.getChunkPos() + "] with max height: [" + exclusiveMaxBuildHeight + "] had issue getting block at pos [" + relBlockX + "," + y + "," + relBlockZ + "] error: " + e.getMessage(), e);
								getTopErrorLogged = true;
							}
							
							y--;
							break;
						}
					}
					
					// Process blocks from top to bottom
					boolean forceSingleBlock = false;
					for (; y >= minBuildHeight; y--)
					{
						IBiomeWrapper newBiome = chunkWrapper.getBiome(relBlockX, y, relBlockZ);
						IBlockStateWrapper newBlockState = previousBlockState = chunkWrapper.getBlockState(relBlockX, y, relBlockZ, mcBlockPos, previousBlockState);
						byte newBlockLight = (byte) chunkWrapper.getDhBlockLight(relBlockX, y + 1, relBlockZ);
						byte newSkyLight = (byte) chunkWrapper.getDhSkyLight(relBlockX, y + 1, relBlockZ);
						
						// Save the biome/block change if different from previous
						if (!newBiome.equals(currentBiome) 
							|| !newBlockState.equals(currentBlockState) 
							|| forceSingleBlock)
						{
							// if the previous block potentially colors this block
							// make this block a single entry, aka add the next block even if it is the same
							// this is done to allow fire, snow, flowers, etc. to properly color the top of columns vs the whole column
							forceSingleBlock = 
									!currentBlockState.isAir()
									&& !currentBlockState.isSolid()
									&& !currentBlockState.isLiquid()
									&& currentBlockState.getOpacity() != LodUtil.BLOCK_FULLY_OPAQUE;
							
							
							longs.add(FullDataPointUtil.encode(mappedId, lastY - y, y + 1 - inclusiveMinBuildHeight, blockLight, skyLight));
							currentBiome = newBiome;
							currentBlockState = newBlockState;
							
							mappedId = dataSource.mapping.addIfNotPresentAndGetId(currentBiome, currentBlockState);
							blockLight = newBlockLight;
							skyLight = newSkyLight;
							lastY = y;
							
							
							// mark the data source as non-empty if we find any non-air blocks
							if (dataSource.isEmpty
								&& newBlockState != null 
								&& !newBlockState.isAir())
							{
								dataSource.isEmpty = false;
							}
						}
					}
					
					// Add the final data point
					longs.add(FullDataPointUtil.encode(mappedId, lastY - y, y + 1 - inclusiveMinBuildHeight, blockLight, skyLight));
					
					// Set the column in the data source
					dataSource.setSingleColumn(longs, columnX, columnZ, EDhApiWorldGenerationStep.LIGHT, worldCompressionMode);
				}
			}
		}
		catch (DataCorruptedException e)
		{
			LOGGER.error("Unable to convert chunk at pos ["+chunkWrapper.getChunkPos()+"] to an LOD. Error: "+e.getMessage(), e);
			return null;
		}
		
		return dataSource;
	}
	
	
	
	//==================//
	// internal helpers //
	//==================//
	
	private static int getXOrZSectionPosFromChunkPos(int chunkXOrZPos)
	{
		// get the section position
		int sectionPos = chunkXOrZPos;
		// negative positions start at -1 so the logic there is slightly different
		sectionPos = (sectionPos < 0) ? ((sectionPos + 1) / FullDataSourceV2.NUMB_OF_CHUNKS_WIDE) - 1 : (sectionPos / FullDataSourceV2.NUMB_OF_CHUNKS_WIDE);
		return sectionPos;
	}
	
	
	
}
