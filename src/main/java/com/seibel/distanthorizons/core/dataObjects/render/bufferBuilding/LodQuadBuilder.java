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
public class LodQuadBuilder
{
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	@SuppressWarnings("unchecked")
	private final ArrayList<BufferQuad>[] opaqueQuads = (ArrayList<BufferQuad>[]) new ArrayList[6];
	@SuppressWarnings("unchecked")
	private final ArrayList<BufferQuad>[] transparentQuads = (ArrayList<BufferQuad>[]) new ArrayList[6];
	
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
	
	public LodQuadBuilder(boolean doTransparency, IClientLevelWrapper clientLevelWrapper)
	{
		this.doTransparency = doTransparency;
		for (int i = 0; i < 6; i++)
		{
			this.opaqueQuads[i] = new ArrayList<>();
			this.transparentQuads[i] = new ArrayList<>();
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
	public SemanticQuadCoverage semanticQuadCoverage()
	{
		return new SemanticQuadCoverage(
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
				DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild(),
				quadList.get(quadList.size() - 1).semanticMaterialId,
				quad.semanticMaterialId
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
	
	/** Merges all of this builder's quads for the given directionIndex (up, down, left, etc.) in the given direction */
	private static long mergeQuadsInternal(ArrayList<BufferQuad>[] list, int directionIndex, BufferMergeDirectionEnum mergeDirection)
	{
		if (list[directionIndex].size() <= 1)
		{
			return 0;
		}
		
		list[directionIndex].sort((objOne, objTwo) -> objOne.compare(objTwo, mergeDirection));
		
		long mergeCount = 0;
		ListIterator<BufferQuad> iter = list[directionIndex].listIterator();
		BufferQuad currentQuad = iter.next();
		while (iter.hasNext())
		{
			BufferQuad nextQuad = iter.next();
			
			if (canMergeSemanticMaterials(
				DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild(),
				currentQuad.semanticMaterialId,
				nextQuad.semanticMaterialId
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
		return !exactAtlasRoute || leftMaterialId == rightMaterialId;
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
	
	public ArrayList<ByteBuffer> makeOpaqueVertexBuffers() { return this.makeVertexBuffers(this.opaqueQuads, DEFAULT_DIRECTION_RENDER_ORDER); }
	public ArrayList<ByteBuffer> makeTransparentVertexBuffers() { return this.makeVertexBuffers(this.transparentQuads, TRANSPARENT_NON_UP_DIRECTION_RENDER_ORDER); }
	public ArrayList<ByteBuffer> makeTransparentUpVertexBuffers() { return this.makeVertexBuffers(this.transparentQuads, TRANSPARENT_UP_DIRECTION_RENDER_ORDER, quad -> !isWater(quad)); }
	public ArrayList<ByteBuffer> makeTransparentWaterUpVertexBuffers() { return this.makeVertexBuffers(this.transparentQuads, TRANSPARENT_UP_DIRECTION_RENDER_ORDER, LodQuadBuilder::isWater); }

	/**
	 * Creates the normal DH CPU vertex buffers plus one semantic identity per
	 * emitted quad. The sidecar is opt-in so the legacy GL route does not pay
	 * for data it cannot consume.
	 */
	public VertexBufferBuild makeOpaqueVertexBuffersWithSemanticMaterials() { return this.makeVertexBufferBuild(this.opaqueQuads, DEFAULT_DIRECTION_RENDER_ORDER); }
	public VertexBufferBuild makeTransparentVertexBuffersWithSemanticMaterials() { return this.makeVertexBufferBuild(this.transparentQuads, TRANSPARENT_NON_UP_DIRECTION_RENDER_ORDER); }
	public VertexBufferBuild makeTransparentUpVertexBuffersWithSemanticMaterials() { return this.makeVertexBufferBuild(this.transparentQuads, TRANSPARENT_UP_DIRECTION_RENDER_ORDER, quad -> !isWater(quad)); }
	public VertexBufferBuild makeTransparentWaterUpVertexBuffersWithSemanticMaterials() { return this.makeVertexBufferBuild(this.transparentQuads, TRANSPARENT_UP_DIRECTION_RENDER_ORDER, LodQuadBuilder::isWater); }

	/**
	 * Builds the Rust semantic transport directly into exact-size Java-owned
	 * packets.  This route deliberately never creates DH's legacy native VBO
	 * staging buffers: Rust owns the eventual GPU asset and the fixed-layout
	 * bytes are the semantic boundary, not an OpenGL upload source.
	 */
	public SemanticVertexBufferBuild makeOpaqueRustSemanticBuffers() { return this.makeSemanticVertexBufferBuild(this.opaqueQuads, DEFAULT_DIRECTION_RENDER_ORDER, quad -> true); }
	public SemanticVertexBufferBuild makeTransparentRustSemanticBuffers() { return this.makeSemanticVertexBufferBuild(this.transparentQuads, TRANSPARENT_NON_UP_DIRECTION_RENDER_ORDER, quad -> true); }
	public SemanticVertexBufferBuild makeTransparentUpRustSemanticBuffers() { return this.makeSemanticVertexBufferBuild(this.transparentQuads, TRANSPARENT_UP_DIRECTION_RENDER_ORDER, quad -> !isWater(quad)); }
	public SemanticVertexBufferBuild makeTransparentWaterUpRustSemanticBuffers() { return this.makeSemanticVertexBufferBuild(this.transparentQuads, TRANSPARENT_UP_DIRECTION_RENDER_ORDER, LodQuadBuilder::isWater); }

	private ArrayList<ByteBuffer> makeVertexBuffers(ArrayList<BufferQuad>[] quadList, int[] directionRenderOrder)
	{
		return this.makeVertexBuffers(quadList, directionRenderOrder, quad -> true);
	}
	private ArrayList<ByteBuffer> makeVertexBuffers(ArrayList<BufferQuad>[] quadList, int[] directionRenderOrder, Predicate<BufferQuad> quadFilter)
	{
		ArrayList<ByteBuffer> byteBufferList = new ArrayList<>(3);
		
		ByteBuffer buffer = null;
		for (int directionIndex : directionRenderOrder)
		{
			// ignore empty directions
			if (quadList[directionIndex].isEmpty())
			{
				continue;
			}
			
			// put all the quads in this direction into the buffer
			for (int quadIndex = 0; quadIndex < quadList[directionIndex].size(); quadIndex++)
			{
				BufferQuad quad = quadList[directionIndex].get(quadIndex);
				if (!quadFilter.test(quad))
				{
					continue;
				}

				// if this is the first iteration or the buffer is full, 
				// create a new buffer
				if (buffer == null || !buffer.hasRemaining())
				{
					buffer = MemoryUtil.memAlloc(LodBufferContainer.FULL_SIZED_BUFFER);
					byteBufferList.add(buffer);
				}
				
				this.putQuad(buffer, quad);
			}
		}
		
		// rewind all the buffers so they can be read from
		for (int i = 0; i < byteBufferList.size(); i++)
		{
			buffer = byteBufferList.get(i);
			buffer.limit(buffer.position());
			buffer.rewind();
		}
		
		return byteBufferList;
	}

	private VertexBufferBuild makeVertexBufferBuild(ArrayList<BufferQuad>[] quadList, int[] directionRenderOrder)
	{
		return this.makeVertexBufferBuild(quadList, directionRenderOrder, quad -> true);
	}

	private VertexBufferBuild makeVertexBufferBuild(ArrayList<BufferQuad>[] quadList, int[] directionRenderOrder, Predicate<BufferQuad> quadFilter)
	{
		ArrayList<ByteBuffer> vertexBuffers = new ArrayList<>(3);
		ArrayList<int[]> semanticMaterialIds = new ArrayList<>(3);
		ArrayList<byte[]> semanticVariantStates = new ArrayList<>(3);
		ArrayList<long[]> semanticVariantPositions = new ArrayList<>(3);
		ByteBuffer buffer = null;
		int[] materialIds = null;
		byte[] variantStates = null;
		long[] variantPositions = null;
		for (int directionIndex : directionRenderOrder)
		{
			for (BufferQuad quad : quadList[directionIndex])
			{
				if (!quadFilter.test(quad))
				{
					continue;
				}
				if (buffer == null || !buffer.hasRemaining())
				{
					buffer = MemoryUtil.memAlloc(LodBufferContainer.FULL_SIZED_BUFFER);
					vertexBuffers.add(buffer);
					materialIds = new int[LodBufferContainer.MAX_QUADS_PER_BUFFER];
					variantStates = new byte[LodBufferContainer.MAX_QUADS_PER_BUFFER];
					variantPositions = new long[LodBufferContainer.MAX_QUADS_PER_BUFFER];
					semanticMaterialIds.add(materialIds);
					semanticVariantStates.add(variantStates);
					semanticVariantPositions.add(variantPositions);
				}
				int quadIndex = buffer.position() / LodBufferContainer.QUADS_BYTE_SIZE;
				materialIds[quadIndex] = quad.semanticMaterialId;
				variantStates[quadIndex] = quad.semanticVariantState;
				variantPositions[quadIndex] = quad.semanticVariantPosition;
				this.putQuad(buffer, quad);
			}
		}
		for (int index = 0; index < vertexBuffers.size(); index++)
		{
			buffer = vertexBuffers.get(index);
			int quadCount = buffer.position() / LodBufferContainer.QUADS_BYTE_SIZE;
			buffer.limit(buffer.position());
			buffer.rewind();
			semanticMaterialIds.set(index, Arrays.copyOf(semanticMaterialIds.get(index), quadCount));
			semanticVariantStates.set(index, Arrays.copyOf(semanticVariantStates.get(index), quadCount));
			semanticVariantPositions.set(index, Arrays.copyOf(semanticVariantPositions.get(index), quadCount));
		}
		return new VertexBufferBuild(vertexBuffers, semanticMaterialIds, semanticVariantStates, semanticVariantPositions);
	}

	private SemanticVertexBufferBuild makeSemanticVertexBufferBuild(
		ArrayList<BufferQuad>[] quadList,
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
		return new SemanticVertexBufferBuild(vertexPackets, materialIds, variantStates, variantPositions);
	}

	private static int countMatchingQuads(
		ArrayList<BufferQuad>[] quadList,
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

	/** CPU-owned sidecar; it intentionally contains no GL/Vulkan object. */
	public record VertexBufferBuild(
		List<ByteBuffer> vertexBuffers,
		List<int[]> semanticMaterialIds,
		List<byte[]> semanticVariantStates,
		List<long[]> semanticVariantPositions)
	{
		public VertexBufferBuild(List<ByteBuffer> vertexBuffers, List<int[]> semanticMaterialIds)
		{
			this(vertexBuffers, semanticMaterialIds, List.of(), List.of());
		}

		public VertexBufferBuild
		{
			Objects.requireNonNull(vertexBuffers, "vertexBuffers");
			Objects.requireNonNull(semanticMaterialIds, "semanticMaterialIds");
			Objects.requireNonNull(semanticVariantStates, "semanticVariantStates");
			Objects.requireNonNull(semanticVariantPositions, "semanticVariantPositions");
			if (!semanticMaterialIds.isEmpty() && vertexBuffers.size() != semanticMaterialIds.size())
			{
				throw new IllegalArgumentException("DH semantic material sidecars must align with vertex buffers");
			}
			if ((!semanticVariantStates.isEmpty() && vertexBuffers.size() != semanticVariantStates.size())
				|| (!semanticVariantPositions.isEmpty() && vertexBuffers.size() != semanticVariantPositions.size())
				|| semanticVariantStates.size() != semanticVariantPositions.size())
			{
				throw new IllegalArgumentException("DH semantic variant sidecars must align with vertex buffers");
			}
			for (int index = 0; index < semanticMaterialIds.size(); index++)
			{
				if (!semanticVariantStates.isEmpty()
					&& (semanticMaterialIds.get(index).length != semanticVariantStates.get(index).length
						|| semanticMaterialIds.get(index).length != semanticVariantPositions.get(index).length))
				{
					throw new IllegalArgumentException("DH semantic variant records must align one-for-one with material IDs");
				}
			}
		}
	}

	/** Exact-size, Java-owned packets transferred once into the Rust semantic
	 * collector. They cannot be submitted to a Java GL buffer. */
	public record SemanticVertexBufferBuild(
		List<byte[]> packedVertexBuffers,
		List<int[]> semanticMaterialIds,
		List<byte[]> semanticVariantStates,
		List<long[]> semanticVariantPositions
	) {
		public SemanticVertexBufferBuild {
			Objects.requireNonNull(packedVertexBuffers, "packedVertexBuffers");
			Objects.requireNonNull(semanticMaterialIds, "semanticMaterialIds");
			Objects.requireNonNull(semanticVariantStates, "semanticVariantStates");
			Objects.requireNonNull(semanticVariantPositions, "semanticVariantPositions");
			if (packedVertexBuffers.size() != semanticMaterialIds.size()
				|| packedVertexBuffers.size() != semanticVariantStates.size()
				|| packedVertexBuffers.size() != semanticVariantPositions.size()) {
				throw new IllegalArgumentException("DH Rust semantic packets and sidecars must align");
			}
			for (int index = 0; index < packedVertexBuffers.size(); index++) {
				byte[] vertices = Objects.requireNonNull(packedVertexBuffers.get(index), "packedVertexBuffer");
				if (vertices.length == 0 || vertices.length % LodBufferContainer.QUADS_BYTE_SIZE != 0) {
					throw new IllegalArgumentException("DH Rust semantic packet must contain complete quads");
				}
				int quadCount = vertices.length / LodBufferContainer.QUADS_BYTE_SIZE;
				if (semanticMaterialIds.get(index).length != quadCount
					|| semanticVariantStates.get(index).length != quadCount
					|| semanticVariantPositions.get(index).length != quadCount) {
					throw new IllegalArgumentException("DH Rust semantic packet sidecars must contain one value per quad");
				}
			}
			packedVertexBuffers = List.copyOf(packedVertexBuffers);
			semanticMaterialIds = List.copyOf(semanticMaterialIds);
			semanticVariantStates = List.copyOf(semanticVariantStates);
			semanticVariantPositions = List.copyOf(semanticVariantPositions);
		}
	}

	/**
	 * Counts IDs from the completed sidecars rather than from source quad
	 * construction. This is diagnostic-only: it lets the capture harness
	 * distinguish an identity lost while building the CPU buffers from one lost
	 * later while retaining a copied column snapshot.
	 */
	public static SemanticQuadCoverage semanticQuadCoverage(VertexBufferBuild opaque, VertexBufferBuild transparentSide, VertexBufferBuild transparentUp, VertexBufferBuild transparentWaterUp)
	{
		return semanticQuadCoverage(List.of(
			new SemanticBuildLayer(Objects.requireNonNull(opaque, "opaque"), true),
			new SemanticBuildLayer(Objects.requireNonNull(transparentSide, "transparentSide"), false),
			new SemanticBuildLayer(Objects.requireNonNull(transparentUp, "transparentUp"), false),
			new SemanticBuildLayer(Objects.requireNonNull(transparentWaterUp, "transparentWaterUp"), false)
		));
	}

	/** Coverage counterpart for the exact-size Rust semantic packets. */
	public static SemanticQuadCoverage semanticQuadCoverage(
		SemanticVertexBufferBuild opaque,
		SemanticVertexBufferBuild transparentSide,
		SemanticVertexBufferBuild transparentUp,
		SemanticVertexBufferBuild transparentWaterUp
	) {
		return semanticQuadCoverageFromMaterialIds(List.of(
			new SemanticMaterialLayer(Objects.requireNonNull(opaque, "opaque").semanticMaterialIds(), true),
			new SemanticMaterialLayer(Objects.requireNonNull(transparentSide, "transparentSide").semanticMaterialIds(), false),
			new SemanticMaterialLayer(Objects.requireNonNull(transparentUp, "transparentUp").semanticMaterialIds(), false),
			new SemanticMaterialLayer(Objects.requireNonNull(transparentWaterUp, "transparentWaterUp").semanticMaterialIds(), false)
		));
	}

	private static SemanticQuadCoverage semanticQuadCoverage(List<SemanticBuildLayer> layers)
	{
		return semanticQuadCoverageFromMaterialIds(layers.stream()
			.map(layer -> new SemanticMaterialLayer(layer.build().semanticMaterialIds(), layer.opaque()))
			.toList());
	}

	private static SemanticQuadCoverage semanticQuadCoverageFromMaterialIds(List<SemanticMaterialLayer> layers)
	{
		int known = 0;
		int mixed = 0;
		int unavailable = 0;
		int opaqueKnown = 0;
		int opaqueMixed = 0;
		int opaqueUnavailable = 0;
		for (SemanticMaterialLayer layer : layers)
		{
			for (int[] materialIds : layer.materialIds())
			{
				for (int materialId : materialIds)
				{
					if (materialId > ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE)
					{
						known++;
						if (layer.opaque()) opaqueKnown++;
					}
					else if (materialId == ColumnRenderSource.SEMANTIC_MATERIAL_MIXED)
					{
						mixed++;
						if (layer.opaque()) opaqueMixed++;
					}
					else
					{
						unavailable++;
						if (layer.opaque()) opaqueUnavailable++;
					}
				}
			}
		}
		return new SemanticQuadCoverage(known, mixed, unavailable, opaqueKnown, opaqueMixed, opaqueUnavailable);
	}

	private record SemanticMaterialLayer(List<int[]> materialIds, boolean opaque) { }

	private record SemanticBuildLayer(VertexBufferBuild build, boolean opaque) { }

	public record SemanticQuadCoverage(
		int known,
		int mixed,
		int unavailable,
		int opaqueKnown,
		int opaqueMixed,
		int opaqueUnavailable
	)
	{
		public SemanticQuadCoverage
		{
			if (known < 0 || mixed < 0 || unavailable < 0
				|| opaqueKnown < 0 || opaqueMixed < 0 || opaqueUnavailable < 0
				|| opaqueKnown > known || opaqueMixed > mixed || opaqueUnavailable > unavailable)
			{
				throw new IllegalArgumentException("Semantic quad coverage cannot be negative");
			}
		}

		public SemanticQuadCoverage(int known, int mixed, int unavailable)
		{
			this(known, mixed, unavailable, 0, 0, 0);
		}
	}
	private static boolean isWater(BufferQuad quad)
	{
		return quad.irisBlockMaterialId == EDhApiBlockMaterial.WATER.index;
	}
	private boolean shouldUseTransparentBuffer(int color, byte irisBlockMaterialId)
	{
		return this.doTransparency
			&& (ColorUtil.getAlpha(color) < 255 || isTransparentMaterial(irisBlockMaterialId));
	}
	/**
	 * The column builder has already classified these DH material categories as
	 * transparent. Preserve that semantic classification when the quad stream is
	 * split; alpha alone is insufficient for foliage because its packed LOD
	 * color is commonly opaque.
	 */
	private static boolean isTransparentMaterial(byte materialId)
	{
		return materialId == EDhApiBlockMaterial.WATER.index
			|| materialId == EDhApiBlockMaterial.LEAVES.index;
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
		bb.put(r);
		bb.put(g);
		bb.put(b);
		bb.put(a);
		
		// Block ID and normal index are used by the Iris format
		bb.put(irisBlockMaterialId);
		bb.put(normalIndex);
		bb.putShort((short) 0); // padding to make sure the vertex format as a whole is a multiple of 4
	}
	
	
	
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
	
	/** Returns how many GpuBuffers will be needed to render opaque quads in this builder. */
	public int getCurrentNeededOpaqueVertexBufferCount() { return MathUtil.ceilDiv(this.getCurrentOpaqueQuadsCount(), LodBufferContainer.MAX_QUADS_PER_BUFFER); }
	/** Returns how many GpuBuffers will be needed to render transparent quads in this builder. */
	public int getCurrentNeededTransparentVertexBufferCount()
	{
		if (!this.doTransparency)
		{
			return 0;
		}
		
		return MathUtil.ceilDiv(this.getCurrentTransparentQuadsCount(), LodBufferContainer.MAX_QUADS_PER_BUFFER);
	}
	
}
