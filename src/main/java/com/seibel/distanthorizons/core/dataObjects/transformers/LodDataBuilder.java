package com.seibel.distanthorizons.core.dataObjects.transformers;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.world.level.chunk.NativeDhColumns;
import org.jetbrains.annotations.Nullable;

/** Converts a lit chunk into DH full-data columns, built in Rust (`NativeDhColumns`). */
public class LodDataBuilder
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	private static volatile boolean declineLogged = false;

	/**
	 * The chunk's columns built in Rust, or null when the chunk cannot be
	 * converted: an unsupported level or chunk, or one whose biomes are not
	 * generated yet. A null chunk produces no LOD data.
	 */
	@Nullable
	public static NativeDhColumns nativeColumnsFromChunk(ILevelWrapper levelWrapper, IChunkWrapper chunkWrapper)
	{
		// only block lighting is needed here, sky lighting is populated at the data source stage
		LodUtil.assertTrue(chunkWrapper.isDhBlockLightingCorrect(), "Provided chunk's DH Block lighting hasn't been baked.");
		if (!(chunkWrapper instanceof com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper wrapper)) return null;
		var columns = NativeDhColumns.build(levelWrapper, wrapper, Config.Common.LodBuilding.worldCompression.get().value);
		if (columns == null && !declineLogged)
		{
			declineLogged = true;
			LOGGER.warn("DH could not convert chunk " + chunkWrapper.getChunkPos() + " to LOD data. Further cases are not logged.");
		}
		return columns;
	}

	/** A full-data source for the chunk's section, or null when the chunk cannot be converted. */
	@Nullable
	public static FullDataSourceV2 createFromChunk(ILevelWrapper levelWrapper, IChunkWrapper chunkWrapper)
	{
		var columns = nativeColumnsFromChunk(levelWrapper, chunkWrapper);
		return columns == null ? null : FullDataSourceV2.createNative(columns);
	}

}
