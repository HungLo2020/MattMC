package com.seibel.distanthorizons.core.dataObjects.render;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnQuadView;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.vulkanic.world.NativeDhRenderSource;
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
public class ColumnRenderSource implements AutoCloseable
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
	
	/** will be zero if an empty data source was created */
	public int verticalDataCount;
	public long pos;
	public int yOffset;
	
	public final LongList renderDataContainer;
	private final NativeDhRenderSource nativeData;
	/** Sparse ordered provenance for ambiguously reduced entries. Dense point,
	 * material, variant and uniformity planes belong to the Rust CPU owner. */
	private final Int2ObjectOpenHashMap<List<SemanticMaterialSpan>> semanticMaterialSpansByDataPoint = new Int2ObjectOpenHashMap<>();
	/** Bounded raw source footprints for heterogeneous reduced columns. */
	private final Int2ObjectOpenHashMap<LongList[]> semanticHorizontalContributorsByColumn = new Int2ObjectOpenHashMap<>();
	private final Int2ObjectOpenHashMap<SemanticHorizontalContributor[]> semanticHorizontalContributorSpansByDataPoint = new Int2ObjectOpenHashMap<>();
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
		
		this.pos = pos;
		this.yOffset = yOffset;
		
		this.verticalDataCount = maxVerticalSize;
		
		this.nativeData = new NativeDhRenderSource(this.verticalDataCount);
		this.renderDataContainer = this.nativeData;
		
		this.debugSourceFlags = new DebugSourceFlag[WIDTH * WIDTH];
	}
	
	
	
	//========================//
	// datapoint manipulation //
	//========================//
	
	public long getDataPoint(int posX, int posZ, int verticalIndex) { return this.renderDataContainer.getLong(posX * WIDTH * this.verticalDataCount + posZ * this.verticalDataCount + verticalIndex); }

	public int getSemanticMaterialId(int posX, int posZ, int verticalIndex)
	{
		return this.nativeData.material(dataPointIndex(posX, posZ, verticalIndex));
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
		return this.nativeData.variant(dataPointIndex(posX, posZ, verticalIndex));
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
		return this.nativeData.uniform(columnIndex(posX, posZ));
	}

	public void setSemanticHorizontalUniformity(int posX, int posZ, int verticalIndex, boolean uniform)
	{
		this.nativeData.uniform(columnIndex(posX, posZ), uniform);
	}

	public void setSemanticHorizontalContributors(int posX, int posZ, LongList[] contributors)
	{
		int index = columnIndex(posX, posZ);
		if (contributors == null || contributors.length != 4)
		{
			this.semanticHorizontalContributorsByColumn.remove(index);
			return;
		}
		LongList[] nativeSnapshot = net.minecraft.world.level.chunk.NativeDhContributors.snapshot(contributors, null, Integer.MAX_VALUE);
		if (nativeSnapshot != null)
		{
			this.semanticHorizontalContributorsByColumn.put(index, nativeSnapshot);
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
		LongList[] contributors = this.semanticHorizontalContributorsByColumn.get(columnIndex(posX, posZ));
		if (contributors == null) return null;
		LongArrayList[] copy = new LongArrayList[4];
		for (int i = 0; i < 4; i++)
		{
			copy[i] = contributors[i] == null ? null : new LongArrayList(contributors[i]);
		}
		return copy;
	}

	/** Immutable CPU readers survive source replacement; compatibility lists are copied. */
	public LongList[] getRetainedSemanticHorizontalContributors(int posX, int posZ)
	{
		LongList[] columns = this.semanticHorizontalContributorsByColumn.get(columnIndex(posX, posZ));
		return net.minecraft.world.level.chunk.NativeDhContributors.isSnapshot(columns)
			? columns.clone() : this.getSemanticHorizontalContributors(posX, posZ);
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
		int endIndex = firstIndex + this.verticalDataCount;
		this.nativeData.clearSemantics(columnIndex(posX, posZ), this.verticalDataCount);
		this.semanticHorizontalContributorsByColumn.remove(columnIndex(posX, posZ));
		// Sparse sidecars use the same contiguous column range as the dense arrays.
		// Scanning the entire section's maps for every column makes conversion
		// quadratic as earlier columns accumulate provenance during a fresh build.
		if (!this.semanticMaterialSpansByDataPoint.isEmpty()
			|| !this.semanticHorizontalContributorSpansByDataPoint.isEmpty())
		{
			for (int index = firstIndex; index < endIndex; index++)
			{
				this.semanticMaterialSpansByDataPoint.remove(index);
				this.semanticHorizontalContributorSpansByDataPoint.remove(index);
			}
		}
	}

	public void setSemanticMaterialId(int posX, int posZ, int verticalIndex, int materialId)
	{
		if (materialId < SEMANTIC_MATERIAL_MIXED || materialId > this.semanticMaterials.size())
		{
			throw new IllegalArgumentException("Unknown semantic material id: " + materialId);
		}
		this.nativeData.material(dataPointIndex(posX, posZ, verticalIndex), (short) materialId);
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
		this.nativeData.variant(index, state);
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
		
		
		if (this.verticalDataCount > 0 && this.renderDataContainer.size() == WIDTH * WIDTH * this.verticalDataCount)
		{
			return NativeDhRenderSource.span(this.renderDataContainer, 0, this.renderDataContainer.size(), 2, 0L) != 0;
		}
		// Keep the original traversal for zero/mutated compatibility dimensions.
		for (int x = 0; x < WIDTH; x++) for (int z = 0; z < WIDTH; z++)
		{
			ColumnArrayView view = this.getVerticalDataPointView(x, z);
			for (int i = 0; i < view.size; i++) if (!RenderDataPointUtil.hasZeroHeight(view.get(i))) return true;
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
	public void close() { this.nativeData.close(); }

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
