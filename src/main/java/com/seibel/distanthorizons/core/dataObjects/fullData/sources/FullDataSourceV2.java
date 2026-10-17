package com.seibel.distanthorizons.core.dataObjects.fullData.sources;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.transformers.LodDataBuilder;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.pos.DhLodPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.util.FullDataMinMaxPosUtil;
import com.seibel.distanthorizons.core.util.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import it.unimi.dsi.fastutil.bytes.ByteList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.world.level.chunk.NativeDhColumnGroup;
import net.minecraft.world.level.chunk.NativeDhColumns;
import net.minecraft.world.level.chunk.NativeDhMaterials;
import net.minecraft.world.level.chunk.NativeDhOcclusionPass;
import net.minecraft.world.level.chunk.NativeDhSameDetailMerge;
import net.minecraft.world.level.chunk.NativeDhSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Every datapoint over one {@link DhSectionPos}: a handle over the Rust source
 * table ({@link NativeDhSource}), which owns the columns, the generation,
 * compression and uniformity planes, the semantic footprints, and every
 * update (same-detail merges, parent reduction, downsampling, culling, hashing).
 * Java keeps the material mapping, the source flags and list views of columns.
 *
 * @see FullDataPointUtil
 * @see FullDataSourceV1
 */
public class FullDataSourceV2 implements AutoCloseable
{
	/** measured in data columns */
	public static final int WIDTH = 64;
	/** how many chunks wide this datasource is at detail level 0. */
	public static final int NUMB_OF_CHUNKS_WIDE = WIDTH / LodUtil.CHUNK_WIDTH;
	/** Hard bound for each copied contributor column in the semantic sidecar. */
	public static final int MAX_SEMANTIC_HORIZONTAL_CONTRIBUTOR_POINTS = NativeDhSource.FOOTPRINT_POINTS;

	private int cachedHashCode = 0;
	private final long pos;

	public final FullDataPointIdMap mapping;
	private NativeDhColumns nativeOriginalColumns;
	/** One arena for all of this source's mutable native columns (see NativeDhColumnGroup). */
	private final NativeDhColumnGroup nativeColumnGroup = new NativeDhColumnGroup();

	public long lastModifiedUnixDateTime;
	public long createdUnixDateTime;

	/**
	 * How far each column has been generated, one value per column (fixed size).
	 * @see EDhApiWorldGenerationStep
	 */
	public final ByteList columnGenerationSteps;
	/**
	 * The world compression used for each column, one value per column (fixed size).
	 * @see EDhApiWorldCompressionMode
	 */
	public final ByteList columnWorldCompressionMode;

	/** Demand-created list views of the Rust columns (y sorted top to bottom). */
	private final LongList[] dataPoints;
	private final NativeDhSource nativeSource;

	public boolean isEmpty;
	/** Will be null if we don't want to update this value in the DB */
	@Nullable
	public Boolean applyToParent = null;
	/** Will be null if we don't want to update this value in the DB */
	@Nullable
	public Boolean applyToChildren = null;



	//==============//
	// constructors //
	//==============//

	public static FullDataSourceV2 createFromChunk(ILevelWrapper levelWrapper, IChunkWrapper chunkWrapper) { return LodDataBuilder.createFromChunk(levelWrapper, chunkWrapper); }

	public static FullDataSourceV2 createFromLegacyDataSourceV1(FullDataSourceV1 legacyData)
	{
		if (FullDataSourceV1.WIDTH != WIDTH)
		{
			throw new UnsupportedOperationException(
					"Unable to convert ["+FullDataSourceV1.class.getSimpleName()+"] into ["+FullDataSourceV2.class.getSimpleName()+"]. " +
					"Data sources have different data point widths and no converter is present. " +
					"input width ["+ FullDataSourceV1.WIDTH+"], recipient width ["+WIDTH+"].");
		}


		// Note: this logic only works if the data point data is the same between both versions
		byte[] columnGenerationSteps = new byte[WIDTH * WIDTH];
		byte[] columnWorldCompressionMode = new byte[WIDTH * WIDTH];
		LongList[] dataPoints = new LongList[WIDTH * WIDTH];
		for (int x = 0; x < WIDTH; x++)
		{
			for (int z = 0; z < WIDTH; z++)
			{
				long[] legacyDataColumn = legacyData.get(x, z);
				if (legacyDataColumn != null && legacyDataColumn.length != 0)
				{
					int index = relativePosToIndex(x, z);
					LongList newDataColumn = new LongArrayList(legacyDataColumn);


					// convert the data point format
					boolean columnHasNonAirBlock = false;
					for (int i = 0; i < legacyDataColumn.length; i++)
					{
						long dataPoint = legacyDataColumn[i];

						boolean isAir = legacyData.mapping.getBlockStateWrapper(FullDataPointUtil.getId(dataPoint)).isAir();
						byte blockLight = (byte) FullDataPointUtil.getBlockLight(dataPoint);

						if (isAir)
						{
							// air shouldn't have any light, otherwise down sampling will look weird
							blockLight = 0;
						}

						dataPoint = FullDataPointUtil.setBlockLight(dataPoint, blockLight);
						newDataColumn.set(i, dataPoint);


						// check if this datapoint is air
						if (!columnHasNonAirBlock && !isAir)
						{
							columnHasNonAirBlock = true;
						}
					}


					// save the converted data point
					ensureDataColumnOrder(newDataColumn);
					dataPoints[index] = newDataColumn;

					// the old data sources didn't have a generation step written down
					// if the column has any data points, assume it's fully generated, otherwise assume it's empty
					columnGenerationSteps[index] = (columnHasNonAirBlock ? EDhApiWorldGenerationStep.LIGHT.value : EDhApiWorldGenerationStep.EMPTY.value);
					columnWorldCompressionMode[index] = EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS.value;
				}
			}
		}

		FullDataSourceV2 fullDataSource = FullDataSourceV2.createWithData(legacyData.getPos(), legacyData.mapping, dataPoints, columnGenerationSteps, columnWorldCompressionMode);
		return fullDataSource;
	}

	/** Adopt retained CPU columns without constructing Java point arrays. */
	public static FullDataSourceV2 createNative(NativeDhColumns columns)
	{
		var mapping = FullDataPointIdMap.fromNative(columns);
		var source = new FullDataSourceV2(columns.position(), mapping, null, null, null, false);
		source.nativeOriginalColumns = columns;
		source.applyToParent = true;
		for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
		{
			int index = relativePosToIndex(x + columns.offsetX(), z + columns.offsetZ());
			source.setColumn(index, columns.column(x, z));
			source.columnGenerationSteps.set(index, EDhApiWorldGenerationStep.LIGHT.value);
			source.columnWorldCompressionMode.set(index, (byte)columns.compression());
		}
		return source;
	}

	public static FullDataSourceV2 createEmpty(long pos)
	{
		// data points, genSteps, and columnCompression all start empty/zero
		// since nothing has been generated yet.
		return new FullDataSourceV2(pos, new FullDataPointIdMap(pos), null, null, null, true);
	}

	/** Columns are copied into the source; null entries stay present and empty. Metadata arrays have one value per column. */
	public static FullDataSourceV2 createWithData(long pos, FullDataPointIdMap mapping, LongList[] data, byte[] columnGenerationStep, byte[] columnWorldCompressionMode)
	{ return new FullDataSourceV2(pos, mapping, data, columnGenerationStep, columnWorldCompressionMode, false); }

	private FullDataSourceV2(
			long pos,
			FullDataPointIdMap mapping, @Nullable LongList[] data,
			byte @Nullable [] columnGenerationSteps, byte @Nullable [] columnWorldCompressionMode,
			boolean empty)
	{
		LodUtil.assertTrue(data == null || data.length == WIDTH * WIDTH);

		this.pos = pos;
		this.mapping = mapping;
		this.isEmpty = empty;

		this.dataPoints = new LongList[WIDTH * WIDTH];
		this.nativeSource = new NativeDhSource(this.dataPoints, this.nativeColumnGroup);
		this.nativeSource.initializeUniform(DhSectionPos.getDetailLevel(pos) == DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL);
		this.columnGenerationSteps = this.nativeSource.generation();
		this.columnWorldCompressionMode = this.nativeSource.compression();

		if (data != null)
		{
			for (int i = 0; i < WIDTH * WIDTH; i++)
			{
				if (data[i] != null && !data[i].isEmpty()) this.setColumn(i, data[i]);
			}
		}
		if (columnGenerationSteps != null) this.nativeSource.generation().setAll(columnGenerationSteps);
		if (columnWorldCompressionMode != null) this.nativeSource.compression().setAll(columnWorldCompressionMode);
	}



	//=========//
	// getters //
	//=========//

	@Override public void close() { this.nativeSource.close(); }
	/** Stable list view of a Rust column; null for a missing column. */
	public LongList getColumn(int index) { return this.nativeSource.getColumn(index); }
	/** Bind a column; see {@link NativeDhSource#setColumn}. */
	public void setColumn(int index, LongList column) { this.nativeSource.setColumn(index, column); }
	/** Views of every column, creating any missing views; editing the array cannot replace cells. */
	public LongList[] columnReferences() { return this.nativeSource.columnReferences(); }
	public NativeDhSource nativeSource() { return this.nativeSource; }

	public LongList getColumnAtRelPos(int relX, int relZ) throws IndexOutOfBoundsException
	{ return this.getColumn(relativePosToIndex(relX, relZ)); }

	/** Returns only a copied-data proof; callers must still validate material identity. */
	public boolean hasSemanticHorizontalUniformity(int relX, int relZ)
	{ return this.nativeSource.uniform(relativePosToIndex(relX, relZ)); }

	/** A copy of the cell's bounded footprint, or null when it has none. */
	public LongArrayList[] getSemanticHorizontalContributors(int relX, int relZ)
	{
		LongList[] contributors = this.nativeSource.footprint(relativePosToIndex(relX, relZ));
		if (contributors == null) return null;
		LongArrayList[] copy = new LongArrayList[contributors.length];
		for (int i = 0; i < contributors.length; i++)
		{
			copy[i] = contributors[i] == null ? null : new LongArrayList(contributors[i]);
		}
		return copy;
	}

	/** Read-only views of the cell's footprint, retained independently of this source, or null. */
	public LongList[] getRetainedSemanticHorizontalContributors(int relX, int relZ)
	{ return this.nativeSource.footprint(relativePosToIndex(relX, relZ)); }

	/** Rehydrates a bounded contributor footprint (four columns), or clears it. */
	public void setSemanticHorizontalContributors(int relX, int relZ, LongList[] contributors)
	{
		this.nativeSource.setFootprint(relativePosToIndex(relX, relZ),
			contributors == null || contributors.length != 4 ? null : contributors);
	}

	public void setSemanticHorizontalUniformity(int relX, int relZ, boolean uniform)
	{ this.nativeSource.uniform(relativePosToIndex(relX, relZ), uniform); }

	/**
	 * returns {@link FullDataPointUtil#EMPTY_DATA_POINT} if the given block position
	 * is outside this data source's boundaries.
	 */
	public long getDataPointAtBlockPos(int blockPosX, int blockPosY, int blockPosZ, int levelMinY)
	{
		DhLodPos requestedPos = new DhLodPos(LodUtil.BLOCK_DETAIL_LEVEL, blockPosX, blockPosZ);

		// stop if the requested blockPos is outside this datasource
		{
			byte requestedDetailLevel = requestedPos.detailLevel;
			byte requestedSectionDetailLevel = (byte) (requestedDetailLevel + DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
			long sectionPos = requestedPos.getSectionPosWithSectionDetailLevel(requestedSectionDetailLevel);
			if (!DhSectionPos.contains(this.pos, sectionPos))
			{
				return FullDataPointUtil.EMPTY_DATA_POINT;
			}
		}

		// get the relative data source position
		byte requestDetailLevel = (byte) (DhSectionPos.getDetailLevel(this.pos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
		DhLodPos relativePos = requestedPos.getDhSectionRelativePositionForDetailLevel(requestDetailLevel);

		LongList dataColumn = this.getColumnAtRelPos(relativePos.x, relativePos.z);
		if (dataColumn == null)
		{
			return FullDataPointUtil.EMPTY_DATA_POINT;
		}

		// search for a datapoint that contains the given block y position
		int relBlockPosY = blockPosY - levelMinY;
		for (int i = 0; i < dataColumn.size(); i++)
		{
			long dataPoint = dataColumn.getLong(i);
			if (dataPoint == FullDataPointUtil.EMPTY_DATA_POINT)
			{
				continue;
			}

			int bottomY = FullDataPointUtil.getBottomY(dataPoint);
			int topY = bottomY + FullDataPointUtil.getHeight(dataPoint);
			// blockPositions start from the bottom of the block, thus "<=" for bottomY, just "<" for topY
			if (bottomY <= relBlockPosY && relBlockPosY < topY)
			{
				return dataPoint;
			}
		}

		return FullDataPointUtil.EMPTY_DATA_POINT;
	}



	//==========//
	// updating //
	//==========//

	public boolean updateFromDataSource(@NotNull FullDataSourceV2 inputDataSource)
	{
		// don't try updating if the input is empty
		if (inputDataSource.mapping.isEmpty())
		{
			return false;
		}

		byte thisDetailLevel = DhSectionPos.getDetailLevel(this.pos);
		byte inputDetailLevel = DhSectionPos.getDetailLevel(inputDataSource.pos);

		// determine the mapping changes necessary for the input to map onto this datasource
		int[] remappedIds = this.mapping.mergeAndReturnRemappedEntityIds(inputDataSource.mapping);

		boolean dataChanged;
		long preparedHash = 0;
		if (inputDetailLevel == thisDetailLevel)
		{
			var update = NativeDhSameDetailMerge.update(this.nativeSource, inputDataSource.nativeSource, remappedIds, this.pos);
			if (update.metadataUpdated()) this.isEmpty = false;
			dataChanged = update.changed();
			preparedHash = update.hash();

			// same detail level, propagate parent/children update flags from input
			if (this.applyToParent != null || inputDataSource.applyToParent != null)
			{
				this.applyToParent =
						// copy over application flag if either are set to continue propagating
						(BoolUtil.falseIfNull(this.applyToParent) || BoolUtil.falseIfNull(inputDataSource.applyToParent))
						// don't propagate past the top of the tree
						&& (DhSectionPos.getDetailLevel(this.pos) < FullDataSourceProviderV2.ROOT_SECTION_DETAIL_LEVEL);
			}

			// null check to prevent setting a flag we don't want to save in the DB
			if (this.applyToChildren != null || inputDataSource.applyToChildren != null)
			{
				this.applyToChildren =
						(BoolUtil.falseIfNull(this.applyToChildren) || BoolUtil.falseIfNull(inputDataSource.applyToChildren))
						// don't propagate past the bottom of the tree
						&& (DhSectionPos.getDetailLevel(this.pos) > FullDataSourceProviderV2.LEAF_SECTION_DETAIL_LEVEL);
			}
		}
		else if (inputDetailLevel + 1 == thisDetailLevel)
		{
			dataChanged = this.updateFromOneBelowDetailLevel(inputDataSource, remappedIds);

			// propagating up, parent will need changes
			this.applyToParent =
					dataChanged
					&& (BoolUtil.falseIfNull(this.applyToParent) || BoolUtil.falseIfNull(inputDataSource.applyToParent))
					&& (DhSectionPos.getDetailLevel(this.pos) < FullDataSourceProviderV2.ROOT_SECTION_DETAIL_LEVEL);
		}
		else if (inputDetailLevel - 1 == thisDetailLevel)
		{
			dataChanged = this.downsampleFromOneAboveDetailLevel(inputDataSource, remappedIds);

			// propagating down, children will need changes
			this.applyToChildren =
					dataChanged
					&& (BoolUtil.falseIfNull(this.applyToChildren) || BoolUtil.falseIfNull(inputDataSource.applyToChildren))
					&& (DhSectionPos.getDetailLevel(this.pos) > FullDataSourceProviderV2.LEAF_SECTION_DETAIL_LEVEL);
		}
		else
		{
			// other detail levels aren't supported since it would be more difficult to maintain
			// and would lead to edge cases that don't necessarily need to be supported
			// (IE what do you do when the input is smaller than a single datapoint in the receiving data source?)
			// instead it's better to just percolate the updates up
			throw new UnsupportedOperationException("Unsupported data source update. Expected input detail level of ["+(thisDetailLevel-1)+"], ["+thisDetailLevel+"], or ["+(thisDetailLevel+1)+"], received detail level ["+inputDetailLevel+"].");
		}

		return this.finishUpdate(dataChanged, preparedHash);
	}

	/** After any merge: cull hidden points and refresh the hash when points changed. */
	private boolean finishUpdate(boolean dataChanged, long preparedHash)
	{
		if (dataChanged)
		{
			EDhApiWorldCompressionMode worldCompressionMode = Config.Common.LodBuilding.worldCompression.get();
			boolean cullHiddenBlocks = (worldCompressionMode != EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
			if (cullHiddenBlocks)
			{
				try (var nativePolicy = NativeDhMaterials.create(this.mapping))
				{
					if (nativePolicy != null) NativeDhOcclusionPass.cull(nativePolicy, this.nativeSource);
				}
			}

			// update the hash code
			if (!cullHiddenBlocks && (preparedHash >>> 32) != 0) this.cachedHashCode = (int) preparedHash;
			else this.generateHashCode();
		}

		return dataChanged;
	}

	/**
	 * {@link #updateFromDataSource} for a generated chunk's native columns, without a
	 * producer data source (no producer table, mapping or ID map). It follows the
	 * same-detail route exactly: remap, merge, the input's parent/children
	 * flags (a producer's {@code applyToParent} is true and {@code applyToChildren}
	 * null), then culling and hashing. Returns false, with only the idempotent
	 * mapping merge done, when this source is not the producer's block-detail
	 * section; the caller then merges {@link #createNative} through {@link #updateFromDataSource}.
	 */
	public boolean updateFromNativeColumns(NativeDhColumns columns)
	{
		if (this.pos != columns.position()
			|| DhSectionPos.getDetailLevel(this.pos) != DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL
			|| !this.nativeSource.isLive())
		{
			return false;
		}
		// An empty producer mapping changes nothing, as in updateFromDataSource.
		if (columns.materialCount() == 0) return true;
		int[] remappedIds = this.mapping.mergeAndReturnRemappedEntityIds(columns);
		var update = NativeDhSameDetailMerge.updateFromColumns(this.nativeSource, columns, remappedIds, this.pos);
		if (update.metadataUpdated()) this.isEmpty = false;
		this.applyToParent = DhSectionPos.getDetailLevel(this.pos) < FullDataSourceProviderV2.ROOT_SECTION_DETAIL_LEVEL;
		if (this.applyToChildren != null)
		{
			this.applyToChildren = this.applyToChildren
				&& (DhSectionPos.getDetailLevel(this.pos) > FullDataSourceProviderV2.LEAF_SECTION_DETAIL_LEVEL);
		}
		this.finishUpdate(update.changed(), update.hash());
		return true;
	}

	/** 2x2 input cells reduce into each cell of this source's matching quadrant, in Rust. */
	private boolean updateFromOneBelowDetailLevel(FullDataSourceV2 inputDataSource, int[] remappedIds)
	{
		if (DhSectionPos.getDetailLevel(inputDataSource.pos) + 1 != DhSectionPos.getDetailLevel(this.pos))
		{
			throw new IllegalArgumentException("Input data source must be exactly 1 detail level below this data source. Expected [" + (DhSectionPos.getDetailLevel(this.pos) - 1) + "], received [" + DhSectionPos.getDetailLevel(inputDataSource.pos) + "].");
		}

		// the input is one of this position's 4 children
		int minChildXPos = DhSectionPos.getX(DhSectionPos.getChildByIndex(this.pos, 0));
		int recipientOffsetX = (DhSectionPos.getX(inputDataSource.pos) == minChildXPos) ? 0 : (WIDTH / 2);
		int minChildZPos = DhSectionPos.getZ(DhSectionPos.getChildByIndex(this.pos, 0));
		int recipientOffsetZ = (DhSectionPos.getZ(inputDataSource.pos) == minChildZPos) ? 0 : (WIDTH / 2);

		boolean dataChanged;
		try (var materials = NativeDhMaterials.create(inputDataSource.mapping))
		{
			dataChanged = this.nativeSource.updateParent(inputDataSource.nativeSource, materials, remappedIds, recipientOffsetX, recipientOffsetZ);
		}
		this.isEmpty = false;
		return dataChanged;
	}

	/**
	 * Projects the coarser input onto this source in Rust, writing only columns
	 * that hold no data yet so higher-detail data is never overwritten.
	 */
	public boolean downsampleFromOneAboveDetailLevel(FullDataSourceV2 inputDataSource, int[] remappedIds)
	{
		if (DhSectionPos.getDetailLevel(inputDataSource.pos) - 1 != DhSectionPos.getDetailLevel(this.pos))
		{
			throw new IllegalArgumentException("Input data source must be exactly 1 detail level above this data source. Expected [" + (DhSectionPos.getDetailLevel(this.pos) - 1) + "], received [" + DhSectionPos.getDetailLevel(inputDataSource.pos) + "].");
		}

		// this source is one of the input position's 4 children
		int minParentXPos = DhSectionPos.getX(DhSectionPos.getChildByIndex(inputDataSource.pos, 0));
		int inputOffsetX = (DhSectionPos.getX(this.pos) == minParentXPos) ? 0 : (WIDTH / 2);
		int minParentZPos = DhSectionPos.getZ(DhSectionPos.getChildByIndex(inputDataSource.pos, 0));
		int inputOffsetZ = (DhSectionPos.getZ(this.pos) == minParentZPos) ? 0 : (WIDTH / 2);

		boolean dataChanged = this.nativeSource.downsample(inputDataSource.nativeSource, remappedIds, inputOffsetX, inputOffsetZ);
		this.isEmpty = false;
		return dataChanged;
	}



	//===================//
	// adjacent clearing //
	//===================//

	/** Removes any non-adjacent data from the given direction. */
	public void clearAllNonAdjData(EDhDirection direction)
	{
		long encodedMinMaxPos = FullDataMinMaxPosUtil.getEncodedMinMaxPos(direction);
		int minX = FullDataMinMaxPosUtil.getAdjMinX(encodedMinMaxPos);
		int maxX = FullDataMinMaxPosUtil.getAdjMaxX(encodedMinMaxPos);
		int minZ = FullDataMinMaxPosUtil.getAdjMinZ(encodedMinMaxPos);
		int maxZ = FullDataMinMaxPosUtil.getAdjMaxZ(encodedMinMaxPos);

		for (int relX = 0; relX < FullDataSourceV2.WIDTH; relX++)
		{
			for (int relZ = 0; relZ < FullDataSourceV2.WIDTH; relZ++)
			{
				// skip non-adjacent data
				if (relX >= minX && relX < maxX
						&& relZ >= minZ && relZ < maxZ)
				{
					continue;
				}

				LongList dataColumn = this.getColumnAtRelPos(relX, relZ);
				dataColumn.clear();
				dataColumn.add(FullDataPointUtil.EMPTY_DATA_POINT);
			}
		}
	}



	//================//
	// helper methods //
	//================//

	/**
	 * Usually this should just be used internally, but there may be instances
	 * where the raw data arrays are available without the data source object.
	 *
	 * @return -1 if given an out-of-bounds relative position
	 */
	public static int tryGetRelativePosToIndex(int relX, int relZ)
	{
		if (relX < 0 || relZ < 0
				|| relX >= WIDTH || relZ >= WIDTH)
		{
			return -1;
		}

		return (relX * WIDTH) + relZ;
	}

	/**
	 * Usually this should just be used internally, but there may be instances
	 * where the raw data arrays are available without the data source object.
	 */
	public static int relativePosToIndex(int relX, int relZ) throws IndexOutOfBoundsException
	{
		int index = tryGetRelativePosToIndex(relX, relZ);
		if (index < 0)
		{
			throw new IndexOutOfBoundsException("Relative data source positions must be between [0] (inclusive) and ["+WIDTH+"] (exclusive) the relative pos: ["+relX+","+relZ+"] is outside those boundaries.");
		}

		return index;
	}

	/**
	 * Ensures the given data column is in the correct Y order, specifically
	 * top-to-bottom.
	 */
	private static void ensureDataColumnOrder(LongList dataColumn)
	{
		if (dataColumn.size() < 2)
		{
			return;
		}

		int firstBottomY = FullDataPointUtil.getBottomY(dataColumn.getLong(0));
		int lastBottomY = FullDataPointUtil.getBottomY(dataColumn.getLong(dataColumn.size() - 1));
		if (firstBottomY < lastBottomY)
		{
			// reverse the array so index 0 is the highest,
			// this is necessary for later logic
			for (int i = 0; i < dataColumn.size() / 2; i++)
			{
				long temp = dataColumn.getLong(i);
				dataColumn.set(i, dataColumn.getLong(dataColumn.size() - i - 1));
				dataColumn.set(dataColumn.size() - i - 1, temp);
			}
		}
	}



	//=====================//
	// setters and getters //
	//=====================//

	public long getPos() { return this.pos; }

	public byte getDataDetailLevel() { return (byte) (DhSectionPos.getDetailLevel(this.pos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL); }

	public void setSingleColumn(LongList longArray, int relX, int relZ, EDhApiWorldGenerationStep worldGenStep, EDhApiWorldCompressionMode worldCompressionMode)
	{
		int index = relativePosToIndex(relX, relZ);
		this.setColumn(index, longArray);
		this.columnGenerationSteps.set(index, worldGenStep.value);
		this.columnWorldCompressionMode.set(index, worldCompressionMode.value);
		this.nativeSource.uniform(index, DhSectionPos.getDetailLevel(this.pos)
			== DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL);
		this.nativeSource.setFootprint(index, null);
	}



	//================//
	// base overrides //
	//================//

	@Override
	public String toString() { return DhSectionPos.toString(this.pos); }

	/** Only includes the base data in this object, not the mapping */
	@Override
	public int hashCode()
	{
		if (this.cachedHashCode == 0)
		{
			this.generateHashCode();
		}
		return this.cachedHashCode;
	}
	private void generateHashCode()
	{
		var nativeColumns = this.nativeOriginalColumns;
		if (nativeColumns != null && this.nativeSource.isLive()
			&& this.nativeSource.originalProducer(nativeColumns, this.pos, this.columnGenerationSteps, this.columnWorldCompressionMode) == 1)
		{
			// Still exactly the producer's columns: its immutable hash applies.
			this.cachedHashCode = nativeColumns.sourceHash();
			return;
		}
		this.nativeOriginalColumns = null;
		this.cachedHashCode = this.nativeSource.hash(this.pos);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof FullDataSourceV2 other))
		{
			return false;
		}

		if (other.pos != this.pos)
		{
			return false;
		}
		else
		{
			// the positions are the same, use the hash as a quick method
			// to determine if the data inside is the same.
			// Note: this isn't perfect, but should work well enough for our use case.
			return other.hashCode() == this.hashCode();
		}
	}

}
