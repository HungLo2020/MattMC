package com.seibel.distanthorizons.core.dataObjects.render;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pooling.AbstractPhantomArrayList;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListPool;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnQuadView;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import com.seibel.distanthorizons.core.logging.DhLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores the render data used to generate OpenGL buffers.
 *
 * @see RenderDataPointUtil
 */
public class ColumnRenderSource extends AbstractPhantomArrayList
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	/** No exact source material survived this render-data entry. */
	public static final int SEMANTIC_MATERIAL_UNAVAILABLE = 0;
	/** Multiple source materials were reduced into one render-data entry. */
	public static final int SEMANTIC_MATERIAL_MIXED = -1;
	/** No one source position survived this render-data entry. */
	public static final byte SEMANTIC_VARIANT_UNAVAILABLE = 0;
	/** One exact source block position selected this material/model variant. */
	public static final byte SEMANTIC_VARIANT_EXACT = 1;
	/** More than one source position contributed to this render-data entry. */
	public static final byte SEMANTIC_VARIANT_MIXED = 2;
	private static final int MAX_SEMANTIC_MATERIAL_IDENTITIES = 4096;
	
	/** measured in data columns */
	public static final int WIDTH = 64;
	
	public static final PhantomArrayListPool ARRAY_LIST_POOL = new PhantomArrayListPool("Render Source");
	
	
	
	/** will be zero if an empty data source was created */
	public int verticalDataCount;
	public long pos;
	public int yOffset;
	
	public final LongArrayList renderDataContainer;
	/**
	 * Private provenance alongside the compact render-data points. The legacy
	 * LOD vertex format intentionally remains unchanged: this table exists so a
	 * later semantic renderer can distinguish a recoverable source material
	 * from a color-only or mixed reduction without treating either as a sprite.
	 */
	/*
	 * These are intentionally compact CPU semantics, not renderer state.  A
	 * render source can contain millions of cells at high quality, so retaining
	 * an int, byte, long and boolean for every cell multiplied the normal DH
	 * source residency several times over. Material identities are capped at
	 * 4096 and the mixed sentinel is -1, which fit in a short.  An exact model
	 * seed is reconstructed from this source's section, cell and render-data
	 * Y-min; it must not be retained as a second dense world-position table.
	 */
	private final short[] semanticMaterialByDataPoint;
	/**
	 * Ordered source intervals retained only when vertical LOD reduction made a
	 * render-data entry ambiguous.  The compact render-data and legacy vertex
	 * layouts remain unchanged; a later semantic material route can use these
	 * intervals instead of assigning one arbitrary sprite to a mixed face.
	 */
	private final Map<Integer, List<SemanticMaterialSpan>> semanticMaterialSpansByDataPoint = new HashMap<>();
	/**
	 * Position provenance is deliberately separate from the semantic material
	 * table. Weighted Minecraft models select parts from the block position, so
	 * retaining only a block state would force a later renderer to guess a
	 * sprite. The legacy compact render-data and vertex layouts remain unchanged.
	 */
	private final byte[] semanticVariantStateByDataPoint;
	/** Copied proof that a coarse column's horizontal contributors were identical. */
	private final boolean[] semanticHorizontalUniformByColumn;
	/** Bounded raw source footprints for heterogeneous reduced columns. */
	private final Map<Integer, LongArrayList[]> semanticHorizontalContributorsByColumn = new HashMap<>();
	private final Map<Integer, SemanticHorizontalContributor[]> semanticHorizontalContributorSpansByDataPoint = new HashMap<>();
	private final List<SemanticMaterialIdentity> semanticMaterials = new ArrayList<>();
	private final Map<SemanticMaterialIdentity, Integer> semanticMaterialIds = new HashMap<>();
	
	public final DebugSourceFlag[] debugSourceFlags;
	
	private boolean isEmpty = true;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public static ColumnRenderSource createEmpty(long pos, int maxVerticalSize, int yOffset)
	{ return new ColumnRenderSource(pos, maxVerticalSize, yOffset); }
	/**
	 * Creates an empty ColumnRenderSource.
	 *
	 * @param pos the relative position of the container
	 * @param maxVerticalSize the maximum vertical size of the container
	 */
	private ColumnRenderSource(long pos, int maxVerticalSize, int yOffset)
	{
		super(ARRAY_LIST_POOL, 0, 0, 1);
		
		this.pos = pos;
		this.yOffset = yOffset;
		
		this.verticalDataCount = maxVerticalSize;
		
		this.renderDataContainer = this.pooledArraysCheckout.getLongArray(0, WIDTH * WIDTH * this.verticalDataCount);
		this.semanticMaterialByDataPoint = new short[WIDTH * WIDTH * this.verticalDataCount];
		this.semanticVariantStateByDataPoint = new byte[WIDTH * WIDTH * this.verticalDataCount];
		this.semanticHorizontalUniformByColumn = new boolean[WIDTH * WIDTH];
		
		this.debugSourceFlags = new DebugSourceFlag[WIDTH * WIDTH];
	}
	
	
	
	//========================//
	// datapoint manipulation //
	//========================//
	
	public long getDataPoint(int posX, int posZ, int verticalIndex) { return this.renderDataContainer.getLong(posX * WIDTH * this.verticalDataCount + posZ * this.verticalDataCount + verticalIndex); }

	public int getSemanticMaterialId(int posX, int posZ, int verticalIndex)
	{
		return this.semanticMaterialByDataPoint[dataPointIndex(posX, posZ, verticalIndex)];
	}

	public SemanticMaterialIdentity getSemanticMaterialIdentity(int materialId)
	{
		if (materialId <= SEMANTIC_MATERIAL_UNAVAILABLE || materialId > this.semanticMaterials.size())
		{
			return null;
		}
		return this.semanticMaterials.get(materialId - 1);
	}

	public byte getSemanticVariantState(int posX, int posZ, int verticalIndex)
	{
		return this.semanticVariantStateByDataPoint[dataPointIndex(posX, posZ, verticalIndex)];
	}

	public long getSemanticVariantPosition(int posX, int posZ, int verticalIndex)
	{
		if (this.getSemanticVariantState(posX, posZ, verticalIndex) != SEMANTIC_VARIANT_EXACT)
		{
			return 0L;
		}
		int detailLevel = this.getDataDetailLevel();
		int blockX = DhSectionPos.getMinCornerBlockX(this.pos) + BitShiftUtil.pow(posX, detailLevel);
		int blockZ = DhSectionPos.getMinCornerBlockZ(this.pos) + BitShiftUtil.pow(posZ, detailLevel);
		int blockY = RenderDataPointUtil.getYMin(this.getDataPoint(posX, posZ, verticalIndex)) + this.yOffset;
		return packSemanticVariantPosition(blockX, blockY, blockZ);
	}

	public boolean hasSemanticHorizontalUniformity(int posX, int posZ, int verticalIndex)
	{
		return this.semanticHorizontalUniformByColumn[columnIndex(posX, posZ)];
	}

	public void setSemanticHorizontalUniformity(int posX, int posZ, int verticalIndex, boolean uniform)
	{
		this.semanticHorizontalUniformByColumn[columnIndex(posX, posZ)] = uniform;
	}

	public void setSemanticHorizontalContributors(int posX, int posZ, LongArrayList[] contributors)
	{
		int index = columnIndex(posX, posZ);
		if (contributors == null || contributors.length != 4)
		{
			this.semanticHorizontalContributorsByColumn.remove(index);
			return;
		}
		LongArrayList[] copy = new LongArrayList[4];
		for (int i = 0; i < 4; i++)
		{
			copy[i] = contributors[i] == null ? null : new LongArrayList(contributors[i]);
		}
		this.semanticHorizontalContributorsByColumn.put(index, copy);
	}

	public LongArrayList[] getSemanticHorizontalContributors(int posX, int posZ)
	{
		LongArrayList[] contributors = this.semanticHorizontalContributorsByColumn.get(columnIndex(posX, posZ));
		if (contributors == null) return null;
		LongArrayList[] copy = new LongArrayList[4];
		for (int i = 0; i < 4; i++)
		{
			copy[i] = contributors[i] == null ? null : new LongArrayList(contributors[i]);
		}
		return copy;
	}

	public void setSemanticHorizontalContributorSpans(int posX, int posZ, int verticalIndex,
		SemanticHorizontalContributor[] contributors)
	{
		int index = dataPointIndex(posX, posZ, verticalIndex);
		if (contributors == null || contributors.length != 4)
		{
			this.semanticHorizontalContributorSpansByDataPoint.remove(index);
			return;
		}
		SemanticHorizontalContributor[] copy = new SemanticHorizontalContributor[4];
		for (int i = 0; i < 4; i++) copy[i] = contributors[i];
		this.semanticHorizontalContributorSpansByDataPoint.put(index, copy);
	}

	public SemanticHorizontalContributor[] getSemanticHorizontalContributorSpans(
		int posX, int posZ, int verticalIndex)
	{
		SemanticHorizontalContributor[] contributors = this.semanticHorizontalContributorSpansByDataPoint.get(
			dataPointIndex(posX, posZ, verticalIndex));
		return contributors == null ? null : contributors.clone();
	}

	public List<SemanticMaterialIdentity> semanticMaterials()
	{
		return List.copyOf(this.semanticMaterials);
	}

	/**
	 * Returns the source intervals for an ambiguously reduced entry. An empty
	 * result means the entry's ordinary material/variant sidecars are complete.
	 */
	public List<SemanticMaterialSpan> getSemanticMaterialSpans(int posX, int posZ, int verticalIndex)
	{
		return this.semanticMaterialSpansByDataPoint.getOrDefault(
			this.dataPointIndex(posX, posZ, verticalIndex), List.of()
		);
	}

	/**
	 * Replaces one reduced entry's ordered raw semantic contributors. These are
	 * Java gameplay/model semantics, not atlas or renderer objects.
	 */
	public void setSemanticMaterialSpans(
		int posX, int posZ, int verticalIndex, List<SemanticMaterialSpan> spans
	)
	{
		int index = this.dataPointIndex(posX, posZ, verticalIndex);
		if (spans == null || spans.isEmpty())
		{
			this.semanticMaterialSpansByDataPoint.remove(index);
			return;
		}
		int previousMaxY = Integer.MIN_VALUE;
		for (SemanticMaterialSpan span : spans)
		{
			if (span == null
				|| span.materialId() < SEMANTIC_MATERIAL_UNAVAILABLE
				|| span.materialId() > this.semanticMaterials.size()
				|| span.materialId() == SEMANTIC_MATERIAL_MIXED
				|| span.minY() < previousMaxY)
			{
				throw new IllegalArgumentException("Invalid ordered semantic material spans");
			}
			previousMaxY = span.maxY();
		}
		this.semanticMaterialSpansByDataPoint.put(index, List.copyOf(spans));
	}

	public void clearSemanticMaterialsForColumn(int posX, int posZ)
	{
		int firstIndex = dataPointIndex(posX, posZ, 0);
		java.util.Arrays.fill(
			this.semanticMaterialByDataPoint,
			firstIndex,
			firstIndex + this.verticalDataCount,
			(short) SEMANTIC_MATERIAL_UNAVAILABLE
		);
		java.util.Arrays.fill(this.semanticVariantStateByDataPoint, firstIndex,
			firstIndex + this.verticalDataCount, SEMANTIC_VARIANT_UNAVAILABLE);
		this.semanticHorizontalUniformByColumn[columnIndex(posX, posZ)] = false;
		this.semanticHorizontalContributorsByColumn.remove(columnIndex(posX, posZ));
		this.semanticMaterialSpansByDataPoint.keySet().removeIf(index ->
			index >= firstIndex && index < firstIndex + this.verticalDataCount
		);
		this.semanticHorizontalContributorSpansByDataPoint.keySet().removeIf(index ->
			index >= firstIndex && index < firstIndex + this.verticalDataCount
		);
	}

	public void setSemanticMaterialId(int posX, int posZ, int verticalIndex, int materialId)
	{
		if (materialId < SEMANTIC_MATERIAL_MIXED || materialId > this.semanticMaterials.size())
		{
			throw new IllegalArgumentException("Unknown semantic material id: " + materialId);
		}
		this.semanticMaterialByDataPoint[dataPointIndex(posX, posZ, verticalIndex)] = (short) materialId;
	}

	public void setSemanticVariantProvenance(
		int posX, int posZ, int verticalIndex, byte state, long position
	)
	{
		if (state != SEMANTIC_VARIANT_UNAVAILABLE
			&& state != SEMANTIC_VARIANT_EXACT
			&& state != SEMANTIC_VARIANT_MIXED)
		{
			throw new IllegalArgumentException("Unknown semantic variant state: " + state);
		}
		int index = dataPointIndex(posX, posZ, verticalIndex);
		this.semanticVariantStateByDataPoint[index] = state;
	}

	/** Packs the same semantic world position consumed by Minecraft's model seed. */
	public static long packSemanticVariantPosition(int blockX, int blockY, int blockZ)
	{
		return ((long) blockX & 0x3FFFFFFL) << 38
			| ((long) blockZ & 0x3FFFFFFL) << 12
			| ((long) blockY & 0xFFFL);
	}

	public int internSemanticMaterial(String blockStateIdentity, String biomeIdentity)
	{
		SemanticMaterialIdentity identity = new SemanticMaterialIdentity(blockStateIdentity, biomeIdentity);
		Integer existing = this.semanticMaterialIds.get(identity);
		if (existing != null)
		{
			return existing;
		}
		if (this.semanticMaterials.size() >= MAX_SEMANTIC_MATERIAL_IDENTITIES)
		{
			return SEMANTIC_MATERIAL_UNAVAILABLE;
		}
		int materialId = this.semanticMaterials.size() + 1;
		this.semanticMaterials.add(identity);
		this.semanticMaterialIds.put(identity, materialId);
		return materialId;
	}

	private int dataPointIndex(int posX, int posZ, int verticalIndex)
	{
		if (posX < 0 || posX >= WIDTH || posZ < 0 || posZ >= WIDTH
			|| verticalIndex < 0 || verticalIndex >= this.verticalDataCount)
		{
			throw new IndexOutOfBoundsException("Invalid render-data material position: " + posX + "," + posZ + "," + verticalIndex);
		}
		return posX * WIDTH * this.verticalDataCount + posZ * this.verticalDataCount + verticalIndex;
	}

	private static int columnIndex(int posX, int posZ)
	{
		if (posX < 0 || posX >= WIDTH || posZ < 0 || posZ >= WIDTH)
		{
			throw new IndexOutOfBoundsException("Invalid render-data semantic column position: " + posX + "," + posZ);
		}
		return posX * WIDTH + posZ;
	}

	/** Stable Java-side semantic source key; never a renderer or backend object. */
	public record SemanticMaterialIdentity(String blockStateIdentity, String biomeIdentity)
	{
		public SemanticMaterialIdentity
		{
			if (blockStateIdentity == null || blockStateIdentity.isEmpty()
				|| biomeIdentity == null || biomeIdentity.isEmpty())
			{
				throw new IllegalArgumentException("Semantic material identities must be non-empty");
			}
		}
	}

	/** One non-overlapping source interval contributing to a reduced data point. */
	public record SemanticMaterialSpan(
		int minY, int maxY, int materialId, byte variantState, long variantPosition
	)
	{
		public SemanticMaterialSpan
		{
			if (maxY <= minY)
			{
				throw new IllegalArgumentException("Semantic material span must have positive height");
			}
			if (materialId < SEMANTIC_MATERIAL_UNAVAILABLE || materialId == SEMANTIC_MATERIAL_MIXED)
			{
				throw new IllegalArgumentException("Semantic material span must name one source material or unavailable");
			}
			if (variantState != SEMANTIC_VARIANT_UNAVAILABLE
				&& variantState != SEMANTIC_VARIANT_EXACT
				&& variantState != SEMANTIC_VARIANT_MIXED)
			{
				throw new IllegalArgumentException("Unknown semantic material span variant state");
			}
			if (variantState != SEMANTIC_VARIANT_EXACT && variantPosition != 0L)
			{
				throw new IllegalArgumentException("Only exact semantic material spans may retain a variant position");
			}
		}
	}

	/** Four-way horizontal source coverage for one reduced render-data entry. */
	public record SemanticHorizontalContributor(List<SemanticMaterialSpan> spans)
	{
		public SemanticHorizontalContributor
		{
			spans = spans == null ? List.of() : List.copyOf(spans);
		}
	}
	
	public ColumnArrayView getVerticalDataPointView(int posX, int posZ)
	{
		int offset = posX * WIDTH * this.verticalDataCount + posZ * this.verticalDataCount;
		
		// don't allow returning views that are outside this render source's bounds
		if (offset >= this.renderDataContainer.size())
		{
			return null;
		}
		else if (posX < 0 || posX >= WIDTH
				|| posZ < 0 || posZ >= WIDTH)
		{
			return null;
		}
		
		return new ColumnArrayView(this.renderDataContainer, this.verticalDataCount,
				offset, this.verticalDataCount);
	}
	
	public ColumnQuadView getFullQuadView() { return this.getQuadViewOverRange(0, 0, WIDTH, WIDTH); }
	public ColumnQuadView getQuadViewOverRange(int quadX, int quadZ, int quadXSize, int quadZSize) { return new ColumnQuadView(this.renderDataContainer, WIDTH, this.verticalDataCount, quadX, quadZ, quadXSize, quadZSize); }
	
	
	
	//=====================//
	// data helper methods //
	//=====================//
	
	public Long getPos() { return this.pos; }
	public Long getKey() { return this.pos; }
	
	public byte getDataDetailLevel() { return (byte) (DhSectionPos.getDetailLevel(this.pos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL); }
	
	public boolean isEmpty() { return this.isEmpty; }
	public void markNotEmpty() { this.isEmpty = false; }
	
	/** can be used when debugging */
	public boolean hasNonVoidDataPoints()
	{
		if (this.isEmpty)
		{
			return false;
		}
		
		
		for (int x = 0; x < WIDTH; x++)
		{
			for (int z = 0; z < WIDTH; z++)
			{
				ColumnArrayView columnArrayView = this.getVerticalDataPointView(x,z);
				for (int i = 0; i < columnArrayView.size; i++)
				{
					long dataPoint = columnArrayView.get(i);
					if (!RenderDataPointUtil.hasZeroHeight(dataPoint))
					{
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	
	
	//=======//
	// debug //
	//=======//
	
	/** Sets the debug flag for the given area */
	public void fillDebugFlag(int xStart, int zStart, int xWidth, int zWidth, DebugSourceFlag flag)
	{
		for (int x = xStart; x < xStart + xWidth; x++)
		{
			for (int z = zStart; z < zStart + zWidth; z++)
			{
				this.debugSourceFlags[x * WIDTH + z] = flag;
			}
		}
	}
	
	public DebugSourceFlag debugGetFlag(int ox, int oz) { return this.debugSourceFlags[ox * WIDTH + oz]; }
	
	
	
	//==============//
	// base methods //
	//==============//
	
	@Override
	public String toString()
	{
		String LINE_DELIMITER = "\n";
		String DATA_DELIMITER = " ";
		String SUBDATA_DELIMITER = ",";
		StringBuilder stringBuilder = new StringBuilder();
		
		stringBuilder.append(DhSectionPos.toString(this.pos));
		stringBuilder.append(LINE_DELIMITER);
		
		int size = 1;
		for (int z = 0; z < size; z++)
		{
			for (int x = 0; x < size; x++)
			{
				for (int y = 0; y < this.verticalDataCount; y++)
				{
					//Converting the dataToHex
					stringBuilder.append(Long.toHexString(this.getDataPoint(x, z, y)));
					if (y != this.verticalDataCount - 1)
						stringBuilder.append(SUBDATA_DELIMITER);
				}
				
				if (x != size - 1)
					stringBuilder.append(DATA_DELIMITER);
			}
			
			if (z != size - 1)
				stringBuilder.append(LINE_DELIMITER);
		}
		return stringBuilder.toString();
	}
	
	
	
	//==============//
	// helper enums //
	//==============//
	
	public enum DebugSourceFlag
	{
		FULL(ColorUtil.BLUE),
		DIRECT(ColorUtil.WHITE),
		FILE(ColorUtil.BROWN);
		
		public final int color;
		
		DebugSourceFlag(int color) { this.color = color; }
	}
	
}
