package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import java.nio.ByteBuffer;
import java.util.*;

import com.seibel.distanthorizons.api.enums.config.EDhApiGrassSideRendering;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;

/**
 * Used to create the quads before they are converted to render-able buffers. <br><br>
 *
 * Note: the magic number 6 you see throughout this method represents the number of sides on a cube.
 */
public final class LodQuadBuilder implements AutoCloseable
{
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	private final NativeDhQuadBuilder nativeQuads;
	private final IClientLevelWrapper clientLevelWrapper;
	
	private final EDhApiDebugRendering debugRenderingMode;
	private final EDhApiGrassSideRendering grassSideRenderingMode;
	private static final int MAX_SEMANTIC_MATERIAL_IDENTITIES = 4096;
	/** Builder-local table whose IDs are safe to retain with the CPU build. */
	private final List<ColumnRenderSource.SemanticMaterialIdentity> semanticMaterials = new ArrayList<>();
	private final Map<ColumnRenderSource.SemanticMaterialIdentity, Integer> semanticMaterialIds = new HashMap<>();
	//=============//
	// constructor //
	//=============//
	
	public LodQuadBuilder(boolean doTransparency, IClientLevelWrapper clientLevelWrapper)
	{
		this.nativeQuads = new NativeDhQuadBuilder(doTransparency);
		this.clientLevelWrapper = clientLevelWrapper;
		
		this.debugRenderingMode = Config.Client.Advanced.Debugging.debugRendering.get();
		this.grassSideRenderingMode = Config.Client.Advanced.Graphics.Quality.grassSideRendering.get();
		
	}

	/**
	 * Converts a render-source-local identity into a bounded builder-local ID.
	 * The returned ID is a packet companion; it is never written into the vertex bytes.
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

	/**
	 * Builds one section's boxes in Rust and interns the center source's
	 * semantic identities here in the same first-use order.
	 */
	void buildSection(ColumnRenderSource center, long[] params, net.vulkanic.world.NativeDhRenderSource[] neighbors,
		short[] minX, short[] minZ, int[] flagColors)
	{
		int base = this.semanticMaterials.size();
		params[ColumnRenderBufferBuilder.P_MATERIALS] = center.semanticMaterialCount();
		params[ColumnRenderBufferBuilder.P_INTERN_BASE] = base;
		int[] order = this.nativeQuads.section(params, center.nativeData(), neighbors, minX, minZ, flagColors,
			center.flattenSemanticMaterialSpans(), center.flattenHorizontalContributorSpans(),
			Math.max(0, MAX_SEMANTIC_MATERIAL_IDENTITIES - base));
		for (int index = 0; index < order.length; index++)
		{
			int id = this.internSemanticMaterial(center.getSemanticMaterialIdentity(order[index]));
			if (id != base + index + 1) throw new IllegalStateException("DH semantic identity order diverged from Rust");
		}
	}

	public List<ColumnRenderSource.SemanticMaterialIdentity> semanticMaterials()
	{
		return List.copyOf(this.semanticMaterials);
	}

	/** Bounded source-side evidence for copied material provenance. */
	public SemanticQuadCoverage semanticQuadCoverage()
	{
		return new SemanticQuadCoverage(nativeQuads.count(2), nativeQuads.count(3), nativeQuads.count(4),
			nativeQuads.count(5), nativeQuads.count(6), nativeQuads.count(7));
	}
	
	
	
	//===========//
	// add quads //
	//===========//

	boolean addNativeBox(short width, short height, short x, short y, short z, int color, byte material,
		int identity, byte variant, long position, List<ColumnRenderSource.SemanticMaterialSpan> spans,
		byte sky, byte block, long top, long bottom,
		com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView[] neighbors, boolean[] same,
		int caveY, boolean transparency, boolean fakeOcean, int shadeFacts, float[] replayShades) {
		return nativeQuads.box(width,height,x,y,z,color,material,identity,variant,position,
			spans,sky,block,top,bottom,neighbors,same,caveY,transparency,fakeOcean,shadeFacts,replayShades);
	}

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
		
		
		nativeQuads.add(dir, x, y, z, widthEastWest, widthNorthSouthOrUpDown, color, irisBlockMaterialId, skyLight, blockLight,
			semanticMaterialId, semanticVariantState, semanticVariantPosition, true);
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
		nativeQuads.add(EDhDirection.UP, minX, maxY, minZ, widthEastWest, widthNorthSouthOrUpDown, color, irisBlockMaterialId, skylight, blocklight,
			semanticMaterialId, semanticVariantState, semanticVariantPosition, false);
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
		nativeQuads.add(EDhDirection.DOWN, x, y, z, width, wz, color, irisBlockMaterialId, skylight, blocklight,
			semanticMaterialId, semanticVariantState, semanticVariantPosition, false);
	}
	
	
	
	//=================//
	// data finalizing //
	//=================//
	
	/** Uses Greedy meshing to merge this builder's Quads. */
	public void mergeQuads() { nativeQuads.merge(); }
	
	
	
	//==============//
	// buffer setup //
	//==============//
	
	private int packetGrassMode() {
		return debugRenderingMode != EDhApiDebugRendering.OFF || grassSideRenderingMode == EDhApiGrassSideRendering.AS_GRASS
			? 0 : grassSideRenderingMode == EDhApiGrassSideRendering.AS_DIRT ? 1
			: grassSideRenderingMode == EDhApiGrassSideRendering.FADE_TO_DIRT ? 2 : 3;
	}

	/** Encodes the four packet layers in Rust; the bytes stay native through ledger publication. */
	public NativeDhBuiltColumn makeNativeRustSemanticColumn() {
		var column = new NativeDhBuiltColumn();
		try {
			for (int route = 0; route < 4; route++) {
				try (var plan = nativeQuads.packetPlan(route, packetGrassMode())) {
					plan.appendTo(column, route, clientLevelWrapper, MC_RENDER);
				}
			}
			return column;
		} catch (Throwable failure) { column.close(); throw failure; }
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
	
	
	
	/** Release transient native topology after synchronous packet construction. */
	@Override public void close() { nativeQuads.close(); }

	//=========//
	// getters //
	//=========//
	
	public int getCurrentOpaqueQuadsCount() { return nativeQuads.count(0); }
	public int getCurrentTransparentQuadsCount() { return nativeQuads.count(1); }
	
}
