package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Predicate;

import com.seibel.distanthorizons.api.enums.config.EDhApiGrassSideRendering;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.MathUtil;
import net.vulkanic.world.DistantHorizonsSemanticCollector;
import org.lwjgl.system.MemoryUtil;

/**
 * Used to create the quads before they are converted to render-able buffers. <br><br>
 *
 * Note: the magic number 6 you see throughout this method represents the number of sides on a cube.
 */
/** The Java quad lists, greedy merge and packet encoding before they became Rust-only (HEAD 1490391f9); test oracle only. */
public class JavaQuadListReference implements AutoCloseable, JavaQuadSink
{
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	@SuppressWarnings("unchecked")
	private final ArrayList<BufferQuad>[] opaqueQuads = (ArrayList<BufferQuad>[]) new ArrayList[6];
	@SuppressWarnings("unchecked")
	private final ArrayList<BufferQuad>[] transparentQuads = (ArrayList<BufferQuad>[]) new ArrayList[6];
	
	private static final Object nativeQuads = null; // always the Java lists
	private final boolean doTransparency;
	private final IClientLevelWrapper clientLevelWrapper;
	
	private final EDhApiDebugRendering debugRenderingMode;
	private final EDhApiGrassSideRendering grassSideRenderingMode;
	private static final int MAX_SEMANTIC_MATERIAL_IDENTITIES = 4096;
	/** Builder-local table whose IDs are safe to retain with the CPU build. */
	private final List<ColumnRenderSource.SemanticMaterialIdentity> semanticMaterials = new ArrayList<>();
	private final Map<ColumnRenderSource.SemanticMaterialIdentity, Integer> semanticMaterialIds = new HashMap<>();
	private int emittedKnownSemanticQuads;
	private int emittedMixedSemanticQuads;
	private int emittedUnavailableSemanticQuads;
	private int emittedOpaqueKnownSemanticQuads;
	private int emittedOpaqueMixedSemanticQuads;
	private int emittedOpaqueUnavailableSemanticQuads;

	private static final int[] DEFAULT_DIRECTION_RENDER_ORDER = new int[] {
			EDhDirection.DOWN.ordinal(),
			EDhDirection.UP.ordinal(),
			EDhDirection.NORTH.ordinal(),
			EDhDirection.SOUTH.ordinal(),
			EDhDirection.WEST.ordinal(),
			EDhDirection.EAST.ordinal()
	};
	private static final int[] TRANSPARENT_NON_UP_DIRECTION_RENDER_ORDER = new int[] {
			EDhDirection.DOWN.ordinal(),
			EDhDirection.NORTH.ordinal(),
			EDhDirection.SOUTH.ordinal(),
			EDhDirection.WEST.ordinal(),
			EDhDirection.EAST.ordinal()
	};
	private static final int[] TRANSPARENT_UP_DIRECTION_RENDER_ORDER = new int[] {
			EDhDirection.UP.ordinal()
	};
	
	
	public static final int[][][] DIRECTION_VERTEX_IBO_QUAD = new int[][][]
			{
					// X,Z //
					{ // UP
							{1, 0}, // 0
							{1, 1}, // 1
							{0, 1}, // 2
							{0, 0}, // 3
					},
					{ // DOWN
							{0, 0}, // 0
							{0, 1}, // 1
							{1, 1}, // 2
							{1, 0}, // 3
					},
					
					// X,Y //
					{ // NORTH
							{0, 0}, // 0
							{0, 1}, // 1
							{1, 1}, // 2
							
							{1, 0}, // 3
					},
					{ // SOUTH
							{1, 0}, // 0
							{1, 1}, // 1
							{0, 1}, // 2
							
							{0, 0}, // 3
					},
					
					// Z,Y //
					{ // WEST
							{0, 0}, // 0
							{1, 0}, // 1
							{1, 1}, // 2
							
							{0, 1}, // 3
					},
					{ // EAST
							{0, 1}, // 0
							{1, 1}, // 1
							{1, 0}, // 2
							
							{0, 0}, // 3
					},
			};
	
	private int premergeCount = 0;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public JavaQuadListReference(boolean doTransparency, IClientLevelWrapper clientLevelWrapper)
	{
		this.doTransparency = doTransparency;
		for (int i = 0; i < 6; i++)
		{
			if (nativeQuads == null) {
				this.opaqueQuads[i] = new ArrayList<>();
				this.transparentQuads[i] = new ArrayList<>();
			}
		}
		
		this.clientLevelWrapper = clientLevelWrapper;
		
		this.debugRenderingMode = Config.Client.Advanced.Debugging.debugRendering.get();
		this.grassSideRenderingMode = Config.Client.Advanced.Graphics.Quality.grassSideRendering.get();
		
	}

	/**
	 * Converts a render-source-local identity into a bounded builder-local ID.
	 * The returned ID is never written into DH's legacy vertex bytes.
	 */
	public int internSemanticMaterial(ColumnRenderSource.SemanticMaterialIdentity identity)
	{
		if (identity == null)
		{
			return ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE;
		}
		Integer existing = this.semanticMaterialIds.get(identity);
		if (existing != null)
		{
			return existing;
		}
		if (this.semanticMaterials.size() >= MAX_SEMANTIC_MATERIAL_IDENTITIES)
		{
			return ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE;
		}
		int materialId = this.semanticMaterials.size() + 1;
		this.semanticMaterials.add(identity);
		this.semanticMaterialIds.put(identity, materialId);
		return materialId;
	}

	public List<ColumnRenderSource.SemanticMaterialIdentity> semanticMaterials()
	{
		return List.copyOf(this.semanticMaterials);
	}

	/** Bounded source-side evidence for copied material provenance. */
	public LodQuadBuilder.SemanticQuadCoverage semanticQuadCoverage()
	{
		return new LodQuadBuilder.SemanticQuadCoverage(
			this.emittedKnownSemanticQuads,
			this.emittedMixedSemanticQuads,
			this.emittedUnavailableSemanticQuads,
			this.emittedOpaqueKnownSemanticQuads,
			this.emittedOpaqueMixedSemanticQuads,
			this.emittedOpaqueUnavailableSemanticQuads
		);
	}
	
	
	
	//===========//
	// add quads //
	//===========//

	public void addQuadAdj(
			EDhDirection dir, 
			short x, short y, short z,
			short widthEastWest, short widthNorthSouthOrUpDown,
			int color, byte irisBlockMaterialId, byte skyLight, byte blockLight)
	{
		this.addQuadAdj(
			dir, x, y, z, widthEastWest, widthNorthSouthOrUpDown,
			color, irisBlockMaterialId, skyLight, blockLight,
			ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE);
	}

	public void addQuadAdj(
			EDhDirection dir,
			short x, short y, short z,
			short widthEastWest, short widthNorthSouthOrUpDown,
			int color, byte irisBlockMaterialId, byte skyLight, byte blockLight,
		int semanticMaterialId)
	{
		this.addQuadAdj(dir, x, y, z, widthEastWest, widthNorthSouthOrUpDown,
			color, irisBlockMaterialId, skyLight, blockLight, semanticMaterialId,
			ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, 0L);
	}

	public void addQuadAdj(
			EDhDirection dir,
			short x, short y, short z,
			short widthEastWest, short widthNorthSouthOrUpDown,
			int color, byte irisBlockMaterialId, byte skyLight, byte blockLight,
			int semanticMaterialId, byte semanticVariantState, long semanticVariantPosition)
	{
		if (dir == EDhDirection.DOWN)
		{
			throw new IllegalArgumentException("addQuadAdj() is only for adj direction! Not UP or Down!");
		}
		
		
		ArrayList<BufferQuad> quadList;
		if (this.shouldUseTransparentBuffer(color, irisBlockMaterialId))
		{
			quadList = this.transparentQuads[dir.ordinal()];
		}
		else
		{
			quadList = this.opaqueQuads[dir.ordinal()]; 
		}
		
		BufferQuad quad = new BufferQuad(x, y, z, widthEastWest, widthNorthSouthOrUpDown, color, irisBlockMaterialId, skyLight, blockLight, dir, semanticMaterialId, semanticVariantState, semanticVariantPosition);
		this.recordSemanticQuad(semanticMaterialId, this.shouldUseTransparentBuffer(color, irisBlockMaterialId));
		if (!quadList.isEmpty()
			&& canMergeSemanticMaterials(
				DistantHorizonsSemanticCollector.usesExactMaterialTopologyBuild(),
				quadList.get(quadList.size() - 1).semanticMaterialId,
				quadList.get(quadList.size() - 1).semanticVariantState,
				quadList.get(quadList.size() - 1).semanticVariantPosition,
				quad.semanticMaterialId,
				quad.semanticVariantState,
				quad.semanticVariantPosition
			)
			&& (
				quadList.get(quadList.size() - 1).tryMerge(quad, BufferMergeDirectionEnum.EastWest)
				|| quadList.get(quadList.size() - 1).tryMerge(quad, BufferMergeDirectionEnum.NorthSouthOrUpDown))
			)
		{
			this.premergeCount++;
			return;
		}
		
		quadList.add(quad);
	}
	
	// XZ
	public void addQuadUp(short minX, short maxY, short minZ, short widthEastWest, short widthNorthSouthOrUpDown, int color, byte irisBlockMaterialId, byte skylight, byte blocklight) // TODO argument names are wrong
	{
		this.addQuadUp(
			minX, maxY, minZ, widthEastWest, widthNorthSouthOrUpDown,
			color, irisBlockMaterialId, skylight, blocklight,
			ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE);
	}

	public void addQuadUp(short minX, short maxY, short minZ, short widthEastWest, short widthNorthSouthOrUpDown, int color, byte irisBlockMaterialId, byte skylight, byte blocklight, int semanticMaterialId) // TODO argument names are wrong
	{
		this.addQuadUp(minX, maxY, minZ, widthEastWest, widthNorthSouthOrUpDown,
			color, irisBlockMaterialId, skylight, blocklight, semanticMaterialId,
			ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, 0L);
	}

	public void addQuadUp(short minX, short maxY, short minZ, short widthEastWest, short widthNorthSouthOrUpDown, int color, byte irisBlockMaterialId, byte skylight, byte blocklight, int semanticMaterialId, byte semanticVariantState, long semanticVariantPosition) // TODO argument names are wrong
	{
		boolean isTransparent = this.shouldUseTransparentBuffer(color, irisBlockMaterialId);
		ArrayList<BufferQuad> quadList = isTransparent 
				? this.transparentQuads[EDhDirection.UP.ordinal()] 
				: this.opaqueQuads[EDhDirection.UP.ordinal()];
		
		BufferQuad quad = new BufferQuad(minX, maxY, minZ, widthEastWest, widthNorthSouthOrUpDown, color, irisBlockMaterialId, skylight, blocklight, EDhDirection.UP, semanticMaterialId, semanticVariantState, semanticVariantPosition);
		this.recordSemanticQuad(semanticMaterialId, isTransparent);
		quadList.add(quad);
	}
	
	public void addQuadDown(short x, short y, short z, short width, short wz, int color, byte irisBlockMaterialId, byte skylight, byte blocklight)
	{
		this.addQuadDown(
			x, y, z, width, wz, color, irisBlockMaterialId, skylight, blocklight,
			ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE);
	}

	public void addQuadDown(short x, short y, short z, short width, short wz, int color, byte irisBlockMaterialId, byte skylight, byte blocklight, int semanticMaterialId)
	{
		this.addQuadDown(x, y, z, width, wz, color, irisBlockMaterialId, skylight, blocklight,
			semanticMaterialId, ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, 0L);
	}

	public void addQuadDown(short x, short y, short z, short width, short wz, int color, byte irisBlockMaterialId, byte skylight, byte blocklight, int semanticMaterialId, byte semanticVariantState, long semanticVariantPosition)
	{
		ArrayList<BufferQuad> quadArray = this.shouldUseTransparentBuffer(color, irisBlockMaterialId)
				? this.transparentQuads[EDhDirection.DOWN.ordinal()]
				: this.opaqueQuads[EDhDirection.DOWN.ordinal()];
		
		BufferQuad quad = new BufferQuad(x, y, z, width, wz, color, irisBlockMaterialId, skylight, blocklight, EDhDirection.DOWN, semanticMaterialId, semanticVariantState, semanticVariantPosition);
		this.recordSemanticQuad(semanticMaterialId, this.shouldUseTransparentBuffer(color, irisBlockMaterialId));
		quadArray.add(quad);
	}
	
	
	
	//=================//
	// data finalizing //
	//=================//
	
	/** Uses Greedy meshing to merge this builder's Quads. */
	public void mergeQuads()
	{
		long mergeCount = 0; // can be used for debugging
		long preQuadsCount = this.getCurrentOpaqueQuadsCount() + this.getCurrentTransparentQuadsCount();
		if (preQuadsCount <= 1)
		{
			return;
		}
		
		for (int directionIndex = 0; directionIndex < 6; directionIndex++)
		{
			mergeCount += mergeQuadsInternal(this.opaqueQuads, directionIndex, BufferMergeDirectionEnum.EastWest);
			if (this.doTransparency)
			{
				mergeCount += mergeQuadsInternal(this.transparentQuads, directionIndex, BufferMergeDirectionEnum.EastWest);
			}
			
			
			// only run the second merge if the face is the top or bottom
			if (directionIndex == EDhDirection.UP.ordinal() || directionIndex == EDhDirection.DOWN.ordinal())
			{
				mergeCount += mergeQuadsInternal(this.opaqueQuads, directionIndex, BufferMergeDirectionEnum.NorthSouthOrUpDown);
				if (this.doTransparency)
				{
					mergeCount += mergeQuadsInternal(this.transparentQuads, directionIndex, BufferMergeDirectionEnum.NorthSouthOrUpDown);
				}
			}
		}
		
		//long postQuadsCount = this.getCurrentOpaqueQuadsCount() + this.getCurrentTransparentQuadsCount();
		//LOGGER.trace("Merged "+mergeCount+"/"+preQuadsCount+"("+(mergeCount / (double) preQuadsCount)+") quads");
	}
	
	/** Lists up to this size sort by primitive key, the index in the key's free low 16 bits. */
	private static final int PRIMITIVE_MERGE_SORT_LIMIT = 1 << 16;
	
	/**
	 * Exactly {@code quads.sort((a, b) -> a.compare(b, mergeDirection))}: that
	 * stable sort orders by {@link BufferQuad#mergeSortKey} and then by original
	 * index, which is the order of the distinct {@code key | index} values.
	 * Sorting those primitives avoids a comparator call per comparison.
	 */
	static void sortForMerge(ArrayList<BufferQuad> quads, BufferMergeDirectionEnum mergeDirection)
	{
		int size = quads.size();
		if (size <= 1) return;
		EDhDirection direction = quads.get(0).direction;
		long[] keys = size <= PRIMITIVE_MERGE_SORT_LIMIT ? new long[size] : null;
		for (int index = 0; keys != null && index < size; index++)
		{
			BufferQuad quad = quads.get(index);
			// Mixed directions are rejected by compare(); let it throw as before.
			if (quad.direction != direction) keys = null;
			else keys[index] = quad.mergeSortKey(mergeDirection) | index;
		}
		if (keys == null)
		{
			quads.sort((objOne, objTwo) -> objOne.compare(objTwo, mergeDirection));
			return;
		}
		Arrays.sort(keys);
		BufferQuad[] original = quads.toArray(new BufferQuad[0]);
		for (int index = 0; index < size; index++)
		{
			quads.set(index, original[(int) (keys[index] & 0xFFFFL)]);
		}
	}
	
	/** Merges all of this builder's quads for the given directionIndex (up, down, left, etc.) in the given direction */
	private static long mergeQuadsInternal(ArrayList<BufferQuad>[] list, int directionIndex, BufferMergeDirectionEnum mergeDirection)
	{
		if (list[directionIndex].size() <= 1)
		{
			return 0;
		}
		
		sortForMerge(list[directionIndex], mergeDirection);
		
		boolean exactMaterialTopology = DistantHorizonsSemanticCollector.usesExactMaterialTopologyBuild();
		long mergeCount = 0;
		ListIterator<BufferQuad> iter = list[directionIndex].listIterator();
		BufferQuad currentQuad = iter.next();
		while (iter.hasNext())
		{
			BufferQuad nextQuad = iter.next();
			
			if (canMergeSemanticMaterials(
				exactMaterialTopology,
				currentQuad.semanticMaterialId,
				currentQuad.semanticVariantState,
				currentQuad.semanticVariantPosition,
				nextQuad.semanticMaterialId,
				nextQuad.semanticVariantState,
				nextQuad.semanticVariantPosition
			) && currentQuad.tryMerge(nextQuad, mergeDirection))
			{
				// merge successful, attempt to merge the next quad
				mergeCount++;
				iter.set(null);
			}
			else
			{
				// merge fail, move on to the next quad
				currentQuad = nextQuad;
			}
		}
		list[directionIndex].removeIf(Objects::isNull);
		return mergeCount;
	}

	/**
	 * The legacy DH route intentionally keeps its existing greedy reduction.
	 * Rust's exact-atlas route must not collapse two source identities into one
	 * quad: a single atlas region cannot truthfully represent both. Keeping the
	 * boundary here preserves the producer's semantic identity without putting
	 * texture selection or batching policy into Java's renderer path.
	 */
	static boolean canMergeSemanticMaterials(boolean exactAtlasRoute, int leftMaterialId, int rightMaterialId)
	{
		return canMergeSemanticMaterials(
			exactAtlasRoute,
			leftMaterialId, ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, 0L,
			rightMaterialId, ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, 0L
		);
	}

	/**
	 * Exact-atlas geometry must not merge two equal material IDs when their
	 * weighted-model selections differ. Legacy DH keeps its original greedy
	 * merge behavior; only the Rust exact-atlas route uses this stricter
	 * identity boundary.
	 */
	static boolean canMergeSemanticMaterials(
		boolean exactAtlasRoute,
		int leftMaterialId, byte leftVariantState, long leftVariantPosition,
		int rightMaterialId, byte rightVariantState, long rightVariantPosition
	)
	{
		if (!exactAtlasRoute) return true;
		if (leftMaterialId != rightMaterialId || leftVariantState != rightVariantState) return false;
		return leftVariantState != ColumnRenderSource.SEMANTIC_VARIANT_EXACT
			|| leftVariantPosition == rightVariantPosition;
	}

	private void recordSemanticQuad(int semanticMaterialId, boolean transparent)
	{
		if (semanticMaterialId > ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE)
		{
			this.emittedKnownSemanticQuads++;
			if (!transparent) this.emittedOpaqueKnownSemanticQuads++;
		}
		else if (semanticMaterialId == ColumnRenderSource.SEMANTIC_MATERIAL_MIXED)
		{
			this.emittedMixedSemanticQuads++;
			if (!transparent) this.emittedOpaqueMixedSemanticQuads++;
		}
		else
		{
			this.emittedUnavailableSemanticQuads++;
			if (!transparent) this.emittedOpaqueUnavailableSemanticQuads++;
		}
	}
	
	
	
	//==============//
	// buffer setup //
	//==============//
	


	/**
	 * Builds the Rust semantic transport directly into exact-size Java-owned
	 * packets.  This route deliberately never creates DH's legacy native VBO
	 * staging buffers: Rust owns the eventual GPU asset and the fixed-layout
	 * bytes are the semantic boundary, not an OpenGL upload source.
	 */
	public LodQuadBuilder.SemanticVertexBufferBuild makeOpaqueRustSemanticBuffers() { return makePacketBuild(0, opaqueQuads, DEFAULT_DIRECTION_RENDER_ORDER, quad -> true); }
	public LodQuadBuilder.SemanticVertexBufferBuild makeTransparentRustSemanticBuffers() { return makePacketBuild(1, transparentQuads, TRANSPARENT_NON_UP_DIRECTION_RENDER_ORDER, quad -> true); }
	public LodQuadBuilder.SemanticVertexBufferBuild makeTransparentUpRustSemanticBuffers() { return makePacketBuild(2, transparentQuads, TRANSPARENT_UP_DIRECTION_RENDER_ORDER, quad -> !isWater(quad)); }
	public LodQuadBuilder.SemanticVertexBufferBuild makeTransparentWaterUpRustSemanticBuffers() { return makePacketBuild(3, transparentQuads, TRANSPARENT_UP_DIRECTION_RENDER_ORDER, JavaQuadListReference::isWater); }

	private LodQuadBuilder.SemanticVertexBufferBuild makePacketBuild(int route, List<BufferQuad>[] compatibility, int[] directions, Predicate<BufferQuad> filter) {
		return makeSemanticVertexBufferBuild(compatibility, directions, filter);
	}

	private int packetGrassMode() {
		return debugRenderingMode != EDhApiDebugRendering.OFF || grassSideRenderingMode == EDhApiGrassSideRendering.AS_GRASS
			? 0 : grassSideRenderingMode == EDhApiGrassSideRendering.AS_DIRT ? 1
			: grassSideRenderingMode == EDhApiGrassSideRendering.FADE_TO_DIRT ? 2 : 3;
	}

	private LodQuadBuilder.SemanticVertexBufferBuild makeSemanticVertexBufferBuild(
		List<BufferQuad>[] quadList,
		int[] directionRenderOrder,
		Predicate<BufferQuad> quadFilter
	) {
		// Keep a packet within the collector's transport limit.  Unlike the old
		// 10 MiB native chunks, every allocation is exact-size and becomes the
		// immutable Java-to-Rust semantic payload without a second copy.
		final int maxQuadsPerPacket = DistantHorizonsSemanticCollector.maxRustSemanticQuadsPerPacket();
		final int totalQuadCount = countMatchingQuads(quadList, directionRenderOrder, quadFilter);
		ArrayList<byte[]> vertexPackets = new ArrayList<>(3);
		ArrayList<int[]> materialIds = new ArrayList<>(3);
		ArrayList<byte[]> variantStates = new ArrayList<>(3);
		ArrayList<long[]> variantPositions = new ArrayList<>(3);
		int remainingInPacket = 0;
		ByteBuffer packet = null;
		int[] packetMaterialIds = null;
		byte[] packetVariantStates = null;
		long[] packetVariantPositions = null;
		int packedQuadCount = 0;
		for (int directionIndex : directionRenderOrder) {
			for (BufferQuad quad : quadList[directionIndex]) {
				if (!quadFilter.test(quad)) continue;
				if (remainingInPacket == 0) {
					int packetQuadCount = Math.min(maxQuadsPerPacket, totalQuadCount - packedQuadCount);
					if (packetQuadCount <= 0) throw new IllegalStateException("Distant Horizons semantic packet count underflow");
					byte[] vertices = new byte[Math.multiplyExact(packetQuadCount, LodBufferContainer.QUADS_BYTE_SIZE)];
					vertexPackets.add(vertices);
					packet = ByteBuffer.wrap(vertices).order(java.nio.ByteOrder.nativeOrder());
					packetMaterialIds = new int[packetQuadCount];
					packetVariantStates = new byte[packetQuadCount];
					packetVariantPositions = new long[packetQuadCount];
					materialIds.add(packetMaterialIds);
					variantStates.add(packetVariantStates);
					variantPositions.add(packetVariantPositions);
					remainingInPacket = packetQuadCount;
				}
				int quadIndex = packet.position() / LodBufferContainer.QUADS_BYTE_SIZE;
				packetMaterialIds[quadIndex] = quad.semanticMaterialId;
				packetVariantStates[quadIndex] = quad.semanticVariantState;
				packetVariantPositions[quadIndex] = quad.semanticVariantPosition;
				this.putQuad(packet, quad);
				remainingInPacket--;
				packedQuadCount++;
			}
		}
		return new LodQuadBuilder.SemanticVertexBufferBuild(vertexPackets, materialIds, variantStates, variantPositions);
	}

	private static int countMatchingQuads(
		List<BufferQuad>[] quadList,
		int[] directionRenderOrder,
		Predicate<BufferQuad> quadFilter
	) {
		int count = 0;
		for (int directionIndex : directionRenderOrder) {
			for (BufferQuad quad : quadList[directionIndex]) {
				if (!quadFilter.test(quad)) continue;
				count = Math.incrementExact(count);
			}
		}
		return count;
	}

	private static boolean isWater(BufferQuad quad)
	{
		return quad.irisBlockMaterialId == EDhApiBlockMaterial.WATER.index;
	}
	private boolean shouldUseTransparentBuffer(int color, byte irisBlockMaterialId)
	{
		// Reduced-color opacity selects the DH program. Opaque leaves belong
		// to dh_terrain; forcing them into dh_water changes their lighting and
		// fog. Water alone keeps its transparent layer at packed alpha 255.
		return this.doTransparency
			&& (ColorUtil.getAlpha(color) < 255 || irisBlockMaterialId == EDhApiBlockMaterial.WATER.index);
	}
	private void putQuad(ByteBuffer bb, BufferQuad quad)
	{
		int[][] quadBase = DIRECTION_VERTEX_IBO_QUAD[quad.direction.ordinal()];
		short widthEastWest = quad.widthEastWest;
		short widthNorthSouth = quad.widthNorthSouthOrUpDown;
		byte normalIndex = (byte) quad.direction.ordinal();
		EDhDirection.Axis axis = quad.direction.axis;
		for (int i = 0; i < quadBase.length; i++)
		{
			short dx, dy, dz;
			int mx, my, mz;
			switch (axis)
			{
				case X: // ZY
					dx = 0;
					dy = quadBase[i][1] == 1 ? widthNorthSouth : 0;
					dz = quadBase[i][0] == 1 ? widthEastWest : 0;
					mx = 0;
					my = quadBase[i][1] == 1 ? 1 : -1;
					mz = quadBase[i][0] == 1 ? 1 : -1;
					break;
				case Y: // XZ
					dx = quadBase[i][0] == 1 ? widthEastWest : 0;
					dy = 0;
					dz = quadBase[i][1] == 1 ? widthNorthSouth : 0;
					mx = quadBase[i][0] == 1 ? 1 : -1;
					my = 0;
					mz = quadBase[i][1] == 1 ? 1 : -1;
					break;
				case Z: // XY
					dx = quadBase[i][0] == 1 ? widthEastWest : 0;
					dy = quadBase[i][1] == 1 ? widthNorthSouth : 0;
					dz = 0;
					mx = quadBase[i][0] == 1 ? 1 : -1;
					my = quadBase[i][1] == 1 ? 1 : -1;
					mz = 0;
					break;
				default:
					throw new IllegalArgumentException("Invalid Axis enum: " + axis);
			}
			
			
			int color = quad.color;
			// use custom side color logic for grass blocks
			if (quad.irisBlockMaterialId == EDhApiBlockMaterial.GRASS.index)
			{
				// only use dirt colors if debug rendering is disabled
				if (this.debugRenderingMode == EDhApiDebugRendering.OFF)
				{
					// determine if any custom coloring logic should be used
					if (this.grassSideRenderingMode != EDhApiGrassSideRendering.AS_GRASS)
					{
						// only change the vertex color if it's on the side or bottom
						if (quad.direction.axis.isHorizontal() || quad.direction == EDhDirection.DOWN)
						{
							if (this.grassSideRenderingMode == EDhApiGrassSideRendering.AS_DIRT
									// if we want the color to fade, only apply the dirt color to the bottom vertices
									|| (this.grassSideRenderingMode == EDhApiGrassSideRendering.FADE_TO_DIRT && quadBase[i][1] == 0)
									// always render the bottom as dirt
									|| quad.direction == EDhDirection.DOWN)
							{
								// for horizontal and bottom faces of grass blocks, use the  dirt color to
								// prevent green cliff walls
								color = this.clientLevelWrapper.getDirtBlockColor();
								color = ColorUtil.applyShade(color, MC_RENDER.getShade(quad.direction));
							}
						}
					}
				}
			}
			
			
			this.putVertex(bb, (short) (quad.x + dx), (short) (quad.y + dy), (short) (quad.z + dz),
					quad.hasError ? ColorUtil.RED : color,
					quad.hasError ? 0 : normalIndex,
					quad.hasError ? 0 : quad.irisBlockMaterialId,
					quad.hasError ? 15 : quad.skyLight,
					quad.hasError ? 15 : quad.blockLight,
					mx, my, mz);
		}
	}
	private void putVertex(ByteBuffer bb, short x, short y, short z, int color, byte normalIndex, byte irisBlockMaterialId, byte skylight, byte blocklight, int mx, int my, int mz)
	{
		bb.putShort(x);
		bb.putShort(y);
		bb.putShort(z);
		
		short meta = 0;
		{
			skylight %= 16;
			blocklight %= 16;
			meta |= (short) (skylight | (blocklight << 4));
			
			byte mircoOffset = 0;
			// mirco offset which is a xyz 2bit value
			// 0b00 = no offset
			// 0b01 = positive offset
			// 0b11 = negative offset
			// format is: 0b00zzyyxx
			if (mx != 0) { mircoOffset |= (byte) (mx > 0 ? 0b01 : 0b11); }
			if (my != 0) { mircoOffset |= (byte) (my > 0 ? 0b0100 : 0b1100); }
			if (mz != 0) { mircoOffset |= (byte) (mz > 0 ? 0b010000 : 0b110000); }
			meta |= (short) (mircoOffset << 8);
		}
		bb.putShort(meta);
		
		byte r = (byte) ColorUtil.getRed(color);
		byte g = (byte) ColorUtil.getGreen(color);
		byte b = (byte) ColorUtil.getBlue(color);
		byte a = this.doTransparency ? (byte) ColorUtil.getAlpha(color) : (byte) 255;
		if (irisBlockMaterialId == EDhApiBlockMaterial.WATER.index) {
			DistantHorizonsSemanticCollector.recordPackedWaterVertexForAudit(
				Byte.toUnsignedInt(r), Byte.toUnsignedInt(g), Byte.toUnsignedInt(b), Byte.toUnsignedInt(a));
		}
		bb.put(r);
		bb.put(g);
		bb.put(b);
		bb.put(a);
		
		// Block ID and normal index are used by the Iris format
		bb.put(irisBlockMaterialId);
		bb.put(normalIndex);
		bb.putShort((short) 0); // padding to make sure the vertex format as a whole is a multiple of 4
	}
	
	
	
	/** Release transient native topology after synchronous packet construction. */
	@Override public void close() { }

	//=========//
	// getters //
	//=========//
	
	public int getCurrentOpaqueQuadsCount()
	{
		int i = 0;
		for (ArrayList<BufferQuad> quadList : this.opaqueQuads)
		{
			i += quadList.size();
		}
		
		return i;
	}
	public int getCurrentTransparentQuadsCount()
	{
		if (!this.doTransparency)
		{
			return 0;
		}
		
		int i = 0;
		for (ArrayList<BufferQuad> quadList : this.transparentQuads)
		{
			i += quadList.size();
		}
		
		return i;
	}
	
	
}
