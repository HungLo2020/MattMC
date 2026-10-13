package com.seibel.distanthorizons.core.generation;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import java.util.ArrayList;
import net.minecraft.world.level.chunk.NativeDhLighting;
import net.minecraft.world.level.chunk.NativeDhMaterials;
import net.minecraft.world.level.chunk.NativeDhSkyLight;
import org.jetbrains.annotations.NotNull;

/**
 * DH chunk and data-source lighting. Both run in Rust: chunk lighting as one
 * pass over a 3x3 chunk neighborhood (`NativeDhLighting`), and data-source sky
 * light as one pass over the source table (`NativeDhSkyLight`).
 */
public class DhLightingEngine
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	public static final DhLightingEngine INSTANCE = new DhLightingEngine();

	private static volatile boolean chunkDeclineLogged = false;
	private static volatile boolean sourceDeclineLogged = false;

	private DhLightingEngine() { }



	//================//
	// chunk lighting //
	//================//

	/** Populates both block and sky lighting. */
	public void lightChunk(@NotNull IChunkWrapper centerChunk, @NotNull ArrayList<IChunkWrapper> nearbyChunkList, int maxSkyLight)
	{ this.lightChunk(centerChunk, nearbyChunkList, maxSkyLight, true, true); }

	/** Only populates block lights. */
	public void bakeChunkBlockLighting(@NotNull IChunkWrapper centerChunk, @NotNull ArrayList<IChunkWrapper> nearbyChunkList, int maxSkyLight)
	{ this.lightChunk(centerChunk, nearbyChunkList, maxSkyLight, true, false); }

	/**
	 * Lights the center chunk from its loaded neighbors in Rust and publishes the
	 * new light owners to their wrappers.
	 *
	 * @param nearbyChunkList should also contain centerChunk
	 * @param maxSkyLight should be a value between 0 and 15
	 * @return the number of light positions iterated over, for profiling; -1 if the chunk could not be lit.
	 */
	private int lightChunk(@NotNull IChunkWrapper centerChunk, @NotNull ArrayList<IChunkWrapper> nearbyChunkList,
			int maxSkyLight, boolean updateBlockLight, boolean updateSkyLight)
	{
		int iterations = -1;
		try
		{
			iterations = NativeDhLighting.tryLight(centerChunk, nearbyChunkList, maxSkyLight, updateBlockLight, updateSkyLight);
			if (iterations < 0 && !chunkDeclineLogged)
			{
				// Every stock chunk in a world with registered block states is admitted.
				chunkDeclineLogged = true;
				LOGGER.warn("DH could not light chunk " + centerChunk.getChunkPos() + "; its LOD keeps unlit values. Further cases are not logged.");
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Unexpected lighting issue for center chunk: " + centerChunk.getChunkPos(), e);
		}

		// As before, a failed pass is not retried: the chunk is treated as lit.
		if (updateBlockLight)
		{
			centerChunk.setIsDhBlockLightCorrect(true);
		}
		if (updateSkyLight)
		{
			centerChunk.setIsDhSkyLightCorrect(true);
		}
		return iterations;
	}



	//======================//
	// data source lighting //
	//======================//

	/** Sky light for every point of the source, in one Rust pass over its table. */
	public void bakeDataSourceSkyLight(FullDataSourceV2 dataSource, int maxSkyLight)
	{
		try (var policy = NativeDhMaterials.create(dataSource.mapping))
		{
			if (policy == null)
			{
				// An empty mapping holds no points.
				return;
			}
			if (!NativeDhSkyLight.tryBake(policy, dataSource.nativeSource(), dataSource.mapping.size(), maxSkyLight) && !sourceDeclineLogged)
			{
				// Only a point whose material is outside the mapping (corrupt data) declines.
				sourceDeclineLogged = true;
				LOGGER.warn("DH could not bake sky light for " + dataSource + ": a point's material is outside its mapping. Further cases are not logged.");
			}
		}
	}

}
