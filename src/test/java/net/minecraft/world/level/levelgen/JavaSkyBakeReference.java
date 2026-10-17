package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.BitSet;

/**
 * Test reference: the original Java data-source sky-light bake (BuilderB0y's,
 * formerly {@code DhLightingEngine.bakeDataSourceSkyLightInJava}), kept to
 * check the Rust bake.
 */
final class JavaSkyBakeReference
{
	private static final byte[] ADJACENT_DIRECTION_OFFSETS = new byte[]
			{
					-1, 0,
					+1, 0,
					0, -1,
					0, +1
			};

	private JavaSkyBakeReference() {}
	
	static void bake(FullDataSourceV2 dataSource, int maxSkyLight)
	{
		// create a cache of all the IDs which are completely transparent.
		// FullDataPointIdMap is thread-safe with locks, and is also a map lookup,
		// and both of these things add a bit of overhead which is not necessary
		// in this context.
		// note: since IDs map to both biomes and blocks, there can be more than
		// one ID which corresponds to air.
		BitSet airIDs = new BitSet(dataSource.mapping.size());
		for (int id = 0, size = dataSource.mapping.size(); id < size; id++)
		{
			if (dataSource.mapping.getBlockStateWrapper(id).getOpacity() == 0)
			{
				airIDs.set(id, true);
			}
		}
		
		
		for (int z = 0; z < FullDataSourceV2.WIDTH; z++)
		{
			for (int x = 0; x < FullDataSourceV2.WIDTH; x++)
			{
				LongList dataPoints = dataSource.getColumnAtRelPos(x, z);
				if (dataPoints != null && !dataPoints.isEmpty())
				{
					// iterate through the data points in this column top-down
					// until we reach light level 0 in some way. at this point,
					// no more propagation needs to be performed for this column.
					int size = dataPoints.size();
					for (int index = 0; index < size; index++)
					{
						long point = dataPoints.getLong(index);
						// if the data point in the column is transparent,
						// then fill it with light and then propagate 
						// that light both horizontally and downwards.
						if (airIDs.get(FullDataPointUtil.getId(point)))
						{
							int skylight;
							if (index == 0)
							{
								// top-most data point in the column.
								skylight = maxSkyLight;
							}
							else
							{
								// handle down propagation here. sort of.
								// down propagation is also handled partially elsewhere.
								// basically if the data point above is transparent,
								// we copy its light level.
								// otherwise, if the data point above is opaque,
								// then no light can propagate downwards from it.
								// therefore, this data point should be light level 0*
								// and no more propagation needs to be performed for this column.
								//
								// *unless light propagates into it horizontally,
								// but that is handled separately.
								long above = dataPoints.getLong(index - 1);
								if (airIDs.get(FullDataPointUtil.getId(above)))
								{
									skylight = FullDataPointUtil.getSkyLight(above);
								}
								else
								{
									continue;
								}
							}
							
							// update the data point to contain the correct starting skylight level.
							point = FullDataPointUtil.setSkyLight(point, skylight);
							dataPoints.set(index, point);
							// now for the propagation.
							recursivelyLightAdjacentDataPoints(dataSource, airIDs, x, z, point);
						}
					}
				}
			}
		}
		
		
		// at this point, all transparent data points have been lit,
		// but opaque ones still have light level 0.
		// in this loop we make opaque data points copy the light level
		// above them if, and only if, the data point above is translucent.
		// with one exception: if the data point above is only partially translucent,
		// we use a slightly different way of computing how much light it absorbed.
		// this is how we handle water and ocean floors.
		// note that this alternate logic assumes the 
		// data point above is being lit from the top.
		// this is a fine assumption for water and oceans.
		for (int columnIndex = 0; columnIndex < FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH; columnIndex++)
		{
			LongList list = dataSource.getColumn(columnIndex);
			if (list != null)
			{
				for (int index = 0, size = list.size(); index < size; index++)
				{
					long dataPoint = list.getLong(index);
					if (index == 0)
					{
						// top data point, assume "above" has the max sky light.
						dataPoint = FullDataPointUtil.setSkyLight(dataPoint, maxSkyLight);
						list.set(index, dataPoint);
					}
					else
					{
						// there is another data point above this one.
						// check to see how opaque this data point is first.
						// we will check the above one after that.
						if (!airIDs.get(FullDataPointUtil.getId(dataPoint)))
						{
							// this data point is not transparent.
							// it should be lit from above.
							long above = list.getLong(index - 1);
							int aboveLight = FullDataPointUtil.getSkyLight(above);
							if (airIDs.get(FullDataPointUtil.getId(above)))
							{
								// the above data point is transparent,
								// and does not absorb any light.
								// its light level can be copied as-is.
								dataPoint = FullDataPointUtil.setSkyLight(dataPoint, aboveLight);
								list.set(index, dataPoint);
							}
							else
							{
								// determine how much light should be absorbed by this column
								int absorption = dataSource.mapping.getBlockStateWrapper(FullDataPointUtil.getId(above)).getOpacity() * FullDataPointUtil.getHeight(above);
								if (absorption < aboveLight)
								{
									// the above data point is partially translucent,
									// and absorbs some light. however, it did not absorb
									// enough light to bring the light level down to 0.
									// so, the remaining light can still be copied.
									dataPoint = FullDataPointUtil.setSkyLight(dataPoint, aboveLight - absorption);
									list.set(index, dataPoint);
								}
							}
						}
					}
				}
			}
		}
	}

	private static void recursivelyLightAdjacentDataPoints(
			FullDataSourceV2 chunk,
			BitSet airIDs,
			int relativeX,
			int relativeZ,
			long dataPoint
		)
	{
		int lightLevel = FullDataPointUtil.getSkyLight(dataPoint);
		// early exit condition:
		// in this case, propagating light is guaranteed to be 0 at adjacent positions,
		// and therefore we do not need to waste time propagating it.
		if (lightLevel <= 1)
		{
			return;
		}
		
		
		
		int minY = FullDataPointUtil.getBottomY(dataPoint);
		int maxY = FullDataPointUtil.getHeight(dataPoint) + minY;
		// try to propagate in all 4 directions.
		for (int offsetIndex = 0; offsetIndex < ADJACENT_DIRECTION_OFFSETS.length; )
		{
			int adjacentX = relativeX + ADJACENT_DIRECTION_OFFSETS[offsetIndex++];
			int adjacentZ = relativeZ + ADJACENT_DIRECTION_OFFSETS[offsetIndex++];
			
			// check if the adjacent position is within the bounds of this data source...
			if (adjacentX >= 0 && adjacentX < FullDataSourceV2.WIDTH && adjacentZ >= 0 && adjacentZ < FullDataSourceV2.WIDTH)
			{
				LongList adjacentDataPoints = chunk.getColumnAtRelPos(adjacentX, adjacentZ);
				// ...and also check to make sure we have some data points
				// (potentially transparent ones) to propagate through in the adjacent column.
				if (adjacentDataPoints != null)
				{
					// try to find adjacent data points we can propagate into.
					// we go top-down for this, which will be important for some
					// later conditions.
					int size = adjacentDataPoints.size();
					for (int adjacentIndex = 0; adjacentIndex < size; adjacentIndex++)
					{
						long adjacentDataPoint = adjacentDataPoints.getLong(adjacentIndex);
						int adjacentMinY = FullDataPointUtil.getBottomY(adjacentDataPoint);
						int adjacentMaxY = FullDataPointUtil.getHeight(adjacentDataPoint) + adjacentMinY;
						if (adjacentMinY >= maxY)
						{
							// if the adjacent data point is completely above this one,
							// then there is no overlap between this one and the adjacent one,
							// and therefore light cannot propagate here.
							// try to propagate to the next data point down from the adjacent one.
							continue;
						}
						else if (adjacentMaxY <= minY)
						{
							// if the adjacent data point is completely below this one,
							// then it also has no overlap and can't propagate,
							// but since we're going top-down, neither can any subsequent adjacent data points.
							break;
						}
						else if (!airIDs.get(FullDataPointUtil.getId(adjacentDataPoint)))
						{
							// assume for now that we cannot propagate into non-transparent data points.
							continue;
						}
						else
						{
							// now we can try to propagate.
							int adjacentLightLevel = FullDataPointUtil.getSkyLight(adjacentDataPoint);
							// if the resulting light level after propagation would INCREASE
							// the light level of the adjacent data point, then propagate to it.
							// otherwise, don't do that.
							if (lightLevel - 1 > adjacentLightLevel)
							{
								adjacentDataPoint = FullDataPointUtil.setSkyLight(adjacentDataPoint, lightLevel - 1);
								adjacentDataPoints.set(adjacentIndex, adjacentDataPoint);
								// if propagation succeeded, recursively propagate again starting at the adjacent data point.
								recursivelyLightAdjacentDataPoints(chunk, airIDs, adjacentX, adjacentZ, adjacentDataPoint);
							}
						}
					}
				}
			}
		}
	}
}
