package net.vulkanic.world;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.render.chunk.lists.ChunkRenderList;
import net.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.render.chunk.translucent_sorting.data.SharedIndexSorter;
import net.sodium.client.render.chunk.translucent_sorting.data.Sorter;
import net.sodium.client.render.chunk.translucent_sorting.SortType;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.sodium.client.util.iterator.ByteIterator;
import net.sodium.client.util.NativeBuffer;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.vulkanic.bridge.VulkanicGalBridge;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.SectionPos;
import org.slf4j.Logger;
import net.logging.LogUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RustGalTerrainRenderer {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int INDEX_TYPE_U16 = 1;
	private static final int INDEX_TYPE_U32 = 2;
	private static final int POSITION_MAX_VALUE = 1 << 20;
	private static final int TEXTURE_MAX_VALUE = 1 << 15;
	/** Frozen Sodium's non-macOS compact-vertex sampling precision. */
	private static final int COMPACT_TEXTURE_SUB_TEXEL_PRECISION = 1 << 8;
	private static final int COMPACT_PREFIX_STRIDE = 20;
	private static final int POSITION_OFFSET = 0;
	private static final int COLOR_OFFSET = 8;
	private static final int TEXTURE_OFFSET = 12;
	private static final int LIGHT_MATERIAL_OFFSET = 16;
	private static final float SECTION_LOCAL_MIN = -8.01F;
	private static final float SECTION_LOCAL_MAX = 24.01F;
	private static final int MAX_RECENT_EVENTS = 8192;
	private static final int MAX_LIFECYCLE_EVENTS = 256;
	// Keep enough bounded history for a deterministic camera sequence to retain
	// the initial mesh/accounting records while later frames append visibility
	// and sort receipts.  This is diagnostics-only state; it does not increase
	// semantic mesh residency or GPU resource bounds.
	// A seven-pose capture can legitimately produce tens of thousands of
	// visibility receipts before the final audit snapshot. Keep the ring bounded
	// but large enough to retain the initial source/copy sort receipts alongside
	// those frame-local submissions. This is diagnostics-only state.
	private static final int MAX_TRANSLUCENT_EVENTS = 32768;
	/** Bounds each copied atlas before base/normal/specular expansion. */
	private static final long MAX_RUST_ATLAS_PIXELS = 16_777_216L;
	private static final String STATIC_TERRAIN_SCENARIO_PROPERTY = "mattmc.dev.rustGalStaticTerrain.scenario";
	private static final String FAULT_PROPERTY = "mattmc.dev.rustGalStaticTerrain.fault";
	private static final Map<LayerKey, TerrainSectionAsset> SECTION_ASSETS = new ConcurrentHashMap<>();
	/**
	 * Per-section mirror of {@link #SECTION_ASSETS} for the solid, cutout, and
	 * translucent layers, indexed by {@link #rowSlot}. Frame traversal resolves
	 * all three layers with one lookup. Rows are immutable; every write
	 * re-reads the authoritative layer entry, so concurrent writers converge.
	 */
	private static final ConcurrentHashMap<Long, TerrainSectionAsset[]> SECTION_ASSET_ROWS = new ConcurrentHashMap<>();
	/** Direct semantic identity lookup for post-submit receipts and sort metadata. */
	private static final Map<Long, TerrainAssetIdentity> SECTION_ASSETS_BY_MESH_KEY = new ConcurrentHashMap<>();
	/** Replacement generation built off-screen while the published atlas remains live. */
	private static final Map<LayerKey, TerrainSectionAsset> RESOURCE_RELOAD_SECTION_ASSETS = new ConcurrentHashMap<>();
	private static volatile boolean resourceReloadStaging;
	private static volatile boolean resourceReloadCommitInProgress;
	/** Bounded semantic-only probe samples retained after Rust accepts a mesh. */
	private static final Map<BlockPos, List<VulkanicGalBridge.WorldMeshVertexRecord>> TEXTURE_PROBE_QUADS = new ConcurrentHashMap<>();
	private static volatile List<TerrainTextureProbe> activeTextureProbes = List.of();
	/** Must match the Rust whole-frame static-terrain residency bound. */
	private static final int MAX_SEMANTIC_TERRAIN_SECTIONS = 4096;
	/**
	 * Shadow-only caster sections per frame. Each adds at most one instance per
	 * SHADOW_CANDIDATE_LAYERS entry; with the camera-visible sections this stays
	 * inside Rust's 65,536 WORLD_MAX_FRAME_MESH_INSTANCES and covers a complete
	 * render-distance-10 window (23 x 23 columns x 24 sections).
	 */
	private static final int MAX_SHADOW_CANDIDATE_SECTIONS = 12_288;
	private static boolean shadowCandidateTruncationLogged;
	/** Indexed by {@link #rowSlot}. */
	private static final ChunkSectionLayer[] SHADOW_CANDIDATE_LAYERS = {
		ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT_MIPPED, ChunkSectionLayer.TRANSLUCENT
	};
	/** Primitive-only render-thread scratch; it retains no section or world objects. */
	private static final ThreadLocal<TerrainSetScratch> TERRAIN_SET_SCRATCH =
		ThreadLocal.withInitial(TerrainSetScratch::new);
	/** Read-only map probe reused by each caller; immutable LayerKey values remain the only stored keys. */
	private static final ThreadLocal<LayerLookup> SECTION_ASSET_LOOKUP =
		ThreadLocal.withInitial(LayerLookup::new);
	private static final ArrayDeque<TerrainDiagnosticEvent> RECENT_EVENTS = new ArrayDeque<>(MAX_RECENT_EVENTS);
	private static final ArrayDeque<TerrainDiagnosticEvent> LIFECYCLE_EVENTS = new ArrayDeque<>(MAX_LIFECYCLE_EVENTS);
	private static final ArrayDeque<TerrainDiagnosticEvent> TRANSLUCENT_EVENTS = new ArrayDeque<>(MAX_TRANSLUCENT_EVENTS);
	/**
	 * Sort metadata is part of the semantic submission, not mutable mesh-cache
	 * state.  Rust execution can occur after a later camera sort has replaced the
	 * cache entry, so retain the exact receipt by gameplay frame and mesh key.
	 */
	private static final Map<Long, ArrayDeque<TranslucentExecutionMetadata>> TRANSLUCENT_EXECUTION_METADATA = new ConcurrentHashMap<>();
	private static volatile long atlasGeneration;
	private static volatile boolean copiedShaderPackSeparateAo;
	private static volatile boolean copiedShaderPackDisableDirectionalShading;

	public static boolean copiedShaderPackSeparateAo() {
		return copiedShaderPackSeparateAo;
	}

	public static boolean copiedShaderPackDisableDirectionalShading() {
		return copiedShaderPackDisableDirectionalShading;
	}

	public static void setCopiedShaderPackTerrainPolicy(boolean separateAo, boolean disableDirectionalShading) {
		if (copiedShaderPackSeparateAo == separateAo
			&& copiedShaderPackDisableDirectionalShading == disableDirectionalShading) return;
		copiedShaderPackSeparateAo = separateAo;
		copiedShaderPackDisableDirectionalShading = disableDirectionalShading;
		RustGalWholeFrameTerrainSource.requestResourceReload();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null && minecraft.levelRenderer != null) {
			minecraft.levelRenderer.allChanged();
		}
	}
	private static volatile long registeredAtlasGeneration;
	private static volatile long publishedWorldMeshAtlasGeneration;
	private static VulkanicGalBridge.WorldMeshTextureAssetRecord publishedWorldMeshAtlasPayload;
	/** Semantic atlas generation last copied into the Rust-owned PNG payload. */
	private static volatile long copiedAtlasSemanticGeneration;
	/** Animation-frame key last copied into the Rust-owned PNG payload. */
	private static volatile long copiedAtlasSemanticFrameKey = Long.MIN_VALUE;
	private static volatile byte[] atlasPayload;
	/** Exact Frozen-compatible sprite-isolated atlas levels, excluding mip zero. */
	private static volatile List<byte[]> atlasMipPayloads = List.of();
	private static volatile byte[] normalAtlasPayload;
	private static volatile byte[] specularAtlasPayload;
	private static volatile FluidSpriteAsset waterStillAsset;
	private static volatile AtlasAnimationResource atlasAnimationSource;
	private static volatile FluidSpriteAsset waterFlowAsset;
	private static volatile FluidSpriteAsset waterOverlayAsset;
	private static final AtomicLong acceptedBuildOutputs = new AtomicLong();
	private static final AtomicLong skippedRouteBuildOutputs = new AtomicLong();
	private static final AtomicLong skippedUnsupportedAnimatedSections = new AtomicLong();
	private static final AtomicLong skippedUnsupportedFluidTranslucentSections = new AtomicLong();
	private static final AtomicLong acceptedWaterAnimatedSections = new AtomicLong();
	private static final AtomicLong unsupportedFluidRejectedSections = new AtomicLong();
	private static final AtomicLong skippedEmptyLayers = new AtomicLong();
	private static final AtomicLong registeredMeshes = new AtomicLong();
	private static final AtomicLong registeredTranslucentSorts = new AtomicLong();
	private static final AtomicLong registeredTranslucentSortBytes = new AtomicLong();
	private static final AtomicLong texturePayloadUpdates = new AtomicLong();
	private static final AtomicLong texturePayloadUpdateBytes = new AtomicLong();
	private static final AtomicLong atlasTextureOnlyUpdates = new AtomicLong();
	private static final AtomicLong atlasMissingPayloadUpdates = new AtomicLong();
	private static final AtomicLong atlasMalformedPayloadUpdates = new AtomicLong();
	private static final AtomicLong atlasPartialPayloadUpdates = new AtomicLong();
	private static final AtomicLong removedLayers = new AtomicLong();
	private static final AtomicLong visibleLayerProbes = new AtomicLong();
	private static final AtomicLong visibleLayerSubmissions = new AtomicLong();
	private static final AtomicLong failedLayerSubmissions = new AtomicLong();
	private static final AtomicLong lastVisibleSubmissionFrameId = new AtomicLong(-1L);
	private static final AtomicLong currentFrameVisibleLayerSubmissions = new AtomicLong();
	private static final AtomicLong currentFrameVisibleFingerprint = new AtomicLong();
	private static final AtomicLong lastExecutedStaticTerrainFrameId = new AtomicLong(-1L);
	private static final AtomicLong lastExecutedStaticTerrainSubmissionId = new AtomicLong(-1L);
	private static final AtomicLong lastExecutedStaticTerrainInstances = new AtomicLong();
	private static final AtomicLong invalidations = new AtomicLong();
	private static final AtomicLong terrainExtractionFrames = new AtomicLong();
	private static final AtomicLong rustEnqueueFrames = new AtomicLong();
	private static final AtomicLong translucentSortGenerations = new AtomicLong();
	private static volatile TerrainSectionAsset lastWorldUnloadAsset;
	private static volatile long lastWorldUnloadSectionPos;
	private static volatile ChunkSectionLayer lastWorldUnloadLayer = ChunkSectionLayer.SOLID;

	private record TranslucentExecutionMetadata(
		long sortGeneration,
		long sortedIndexHash,
		double cameraX,
		double cameraY,
		double cameraZ,
		int drawOrder
	) {}

	/** Growable compact static-terrain entries: identity, origin, policy and flags. */
	private static final class CompactTerrainArrays {
		long[] meshKeys = new long[1024];
		long[] meshGenerations = new long[1024];
		int[] sectionOrigins = new int[1024 * 3];
		int[] depthPolicies = new int[1024];
		int[] flags = new int[1024];
		int count;

		void append(long meshKey, long meshGeneration, int x, int y, int z, int depthPolicy, int entryFlags) {
			if (count == meshKeys.length) {
				int capacity = meshKeys.length * 2;
				meshKeys = Arrays.copyOf(meshKeys, capacity);
				meshGenerations = Arrays.copyOf(meshGenerations, capacity);
				sectionOrigins = Arrays.copyOf(sectionOrigins, capacity * 3);
				depthPolicies = Arrays.copyOf(depthPolicies, capacity);
				flags = Arrays.copyOf(flags, capacity);
			}
			int index = count++;
			meshKeys[index] = meshKey;
			meshGenerations[index] = meshGeneration;
			sectionOrigins[index * 3] = x;
			sectionOrigins[index * 3 + 1] = y;
			sectionOrigins[index * 3 + 2] = z;
			depthPolicies[index] = depthPolicy;
			flags[index] = entryFlags;
		}
	}

	private static final class TerrainSetScratch {
		final LongOpenHashSet seenPositions = new LongOpenHashSet();
		/** Shadow candidates whose publication rows one read fetches, and those rows. */
		long[] shadowRowSections = new long[256];
		long[] shadowRows = new long[256 * 6];
		final LongOpenHashSet visibleMeshKeys = new LongOpenHashSet();
		final LongOpenHashSet visibleSubmissions = new LongOpenHashSet();
		final LongOpenHashSet cachedOpaqueMeshKeys = new LongOpenHashSet();
		long[] cachedOpaqueMeshKeyArray = new long[256];
		long[] cachedOpaqueMeshGenerationArray = new long[256];
		int cachedOpaqueMeshKeyCount;
		/** Off-camera shadow casters of this frame. */
		final CompactTerrainArrays shadows = new CompactTerrainArrays();
		/** Camera-visible section layers of this frame, in draw order. */
		final CompactTerrainArrays sections = new CompactTerrainArrays();
		final ArrayList<RenderSection> sectionSnapshot = new ArrayList<>();
		final ArrayList<TerrainSectionAsset> solidAssetSnapshot = new ArrayList<>();
		final ArrayList<TerrainSectionAsset> cutoutAssetSnapshot = new ArrayList<>();
		final ArrayList<RenderSection> translucentSectionSnapshot = new ArrayList<>();

		void clearAll() {
			seenPositions.clear();
			visibleMeshKeys.clear();
			visibleSubmissions.clear();
			cachedOpaqueMeshKeys.clear();
			cachedOpaqueMeshKeyCount = 0;
			shadows.count = 0;
			sections.count = 0;
			sectionSnapshot.clear();
			solidAssetSnapshot.clear();
			cutoutAssetSnapshot.clear();
			translucentSectionSnapshot.clear();
		}
	}


	private static void retainTranslucentExecutionMetadata(long meshKey, TranslucentExecutionMetadata metadata) {
		ArrayDeque<TranslucentExecutionMetadata> queue = TRANSLUCENT_EXECUTION_METADATA.computeIfAbsent(
			meshKey, ignored -> new ArrayDeque<>()
		);
		synchronized (queue) {
			if (queue.size() >= 32) {
				queue.removeFirst();
			}
			queue.addLast(metadata);
		}
	}

	private static TranslucentExecutionMetadata takeTranslucentExecutionMetadata(long meshKey) {
		ArrayDeque<TranslucentExecutionMetadata> queue = TRANSLUCENT_EXECUTION_METADATA.get(meshKey);
		if (queue == null) {
			return null;
		}
		synchronized (queue) {
			TranslucentExecutionMetadata metadata = queue.pollFirst();
			if (queue.isEmpty()) {
				TRANSLUCENT_EXECUTION_METADATA.remove(meshKey, queue);
			}
			return metadata;
		}
	}

	private record FluidSpriteAsset(
		int textureId,
		ResourceLocation location,
		float u0,
		float u1,
		float v0,
		float v1,
		int frameWidth,
		int frameHeight,
		int frameCount,
		int frameTicks,
		int animationFlags,
		int frameRowSize,
		int interpolationPolicy,
		List<VulkanicGalBridge.WorldMeshAnimationFrameRecord> animationFrames,
		byte[] pngBytes,
		int mipLevels
	) {
		long animationHash() {
			long hash = 0xcbf29ce484222325L;
			hash = fnv64Int(hash, textureId);
			hash = fnv64Int(hash, frameWidth);
			hash = fnv64Int(hash, frameHeight);
			hash = fnv64Int(hash, frameCount);
			hash = fnv64Int(hash, frameTicks);
			hash = fnv64Int(hash, frameRowSize);
			hash = fnv64Int(hash, interpolationPolicy);
			for (VulkanicGalBridge.WorldMeshAnimationFrameRecord frame : animationFrames) {
				hash = fnv64Int(hash, frame.frameIndex());
				hash = fnv64Int(hash, frame.durationTicks());
			}
			return hash;
		}

		String animationSummary() {
			StringBuilder summary = new StringBuilder();
			summary.append(textureId)
				.append('/')
				.append(location.toString().replace(':', '~'))
				.append('/')
				.append(frameWidth)
				.append('x')
				.append(frameHeight)
				.append('/')
				.append(frameCount)
				.append('/')
				.append(frameTicks)
				.append('/')
				.append(frameRowSize)
				.append('/')
				.append(interpolationPolicy)
				.append('/');
			int limit = Math.min(animationFrames.size(), 64);
			for (int index = 0; index < limit; index++) {
				if (index > 0) {
					summary.append(',');
				}
				VulkanicGalBridge.WorldMeshAnimationFrameRecord frame = animationFrames.get(index);
				summary.append(frame.frameIndex()).append('@').append(frame.durationTicks());
			}
			if (animationFrames.size() > limit) {
				summary.append(",...");
			}
			return summary.toString();
		}

		VulkanicGalBridge.WorldMeshTextureAssetRecord textureRecord() {
			var record = new VulkanicGalBridge.WorldMeshTextureAssetRecord(
				textureId,
				pngBytes,
				frameWidth,
				frameHeight,
				frameCount,
				frameTicks,
				animationFlags,
				frameRowSize,
				interpolationPolicy,
				animationFrames
			);
			return Boolean.getBoolean("mattmc.dev.rustGalFluidMipLevels") ? record.withMipLevels(mipLevels) : record;
		}

		boolean contains(float u, float v) {
			float minU = Math.min(u0, u1) - 0.00001F;
			float maxU = Math.max(u0, u1) + 0.00001F;
			float minV = Math.min(v0, v1) - 0.00001F;
			float maxV = Math.max(v0, v1) + 0.00001F;
			return u >= minU && u <= maxU && v >= minV && v <= maxV;
		}

		float localU(float u) {
			return (u - u0) / (u1 - u0);
		}

		float localV(float v) {
			return (v - v0) / (v1 - v0);
		}
	}

	private RustGalTerrainRenderer() {
	}

	/** True when the published Rust terrain registry owns any layer for this section. */
	static boolean hasSectionAsset(long sectionPos) {
		return sectionAsset(sectionPos, ChunkSectionLayer.SOLID) != null
			|| sectionAsset(sectionPos, ChunkSectionLayer.CUTOUT_MIPPED) != null
			|| sectionAsset(sectionPos, ChunkSectionLayer.TRANSLUCENT) != null;
	}

	public static void acceptChunkBuildOutput(ChunkBuildOutput output) {
		acceptChunkBuildOutput(output, TerrainMeshLayout.compact(copiedShaderPackSeparateAo));
		return;
	}

	/**
	 * Admits a CPU-built section directly into the explicit Rust terrain route.
	 * The layout is carried by the producer rather than recovered from Iris at
	 * decode time, so selecting Vulkan cannot borrow shader-pack runtime state.
	 */
	public static void acceptWholeFrameChunkBuildOutput(ChunkBuildOutput output) {
		acceptChunkBuildOutput(output, TerrainMeshLayout.compact(copiedShaderPackSeparateAo));
	}

	private static void acceptChunkBuildOutput(ChunkBuildOutput output, TerrainMeshLayout layout) {
		if (output == null || output.info == null) {
			return;
		}
		net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainAdmission(
			output,
			"accepted"
		);
		if (output.info.animatedSprites != null) {
			acceptedWaterAnimatedSections.incrementAndGet();
		}
		acceptedBuildOutputs.incrementAndGet();
		long extractionFrameId = terrainExtractionFrames.incrementAndGet();
		ensureAtlasPayload();
		acceptLayer(output, DefaultTerrainRenderPasses.SOLID, ChunkSectionLayer.SOLID, extractionFrameId, layout);
		acceptLayer(output, DefaultTerrainRenderPasses.CUTOUT, ChunkSectionLayer.CUTOUT_MIPPED, extractionFrameId, layout);
		acceptLayer(output, DefaultTerrainRenderPasses.TRANSLUCENT, ChunkSectionLayer.TRANSLUCENT, extractionFrameId, layout);
		recordTranslucentSortData(output);
	}

	private static void recordTranslucentSortData(ChunkBuildOutput output) {
		if (output == null || output.translucentData == null || !isStaticTerrainTranslucentScenario()) {
			return;
		}
		TerrainSectionAsset asset = sectionAsset(output.render.getPositionAsLong(), ChunkSectionLayer.TRANSLUCENT);
		if (asset == null) {
			return;
		}
		String reason = "translucent-sort-data:"
			+ output.translucentData.getSortType().name().toLowerCase(Locale.ROOT)
			+ ":"
			+ output.translucentData.getClass().getSimpleName();
		recordEvent(
			output.render.getPositionAsLong(),
			ChunkSectionLayer.TRANSLUCENT,
			output.submitTime,
			asset.meshGeneration(),
			asset.meshGeneration(),
			atlasGeneration,
			asset,
			output.render.getOriginX(),
			output.render.getOriginY(),
			output.render.getOriginZ(),
			0.0F,
			0.0F,
			0.0F,
			reason,
			0L,
			0L,
			0L,
			0L,
			asset.meshGeneration(),
			currentCameraX(),
			currentCameraY(),
			currentCameraZ(),
			output.render.getOriginX() + 8.0D,
			output.render.getOriginY() + 8.0D,
			output.render.getOriginZ() + 8.0D,
			Math.max(0, asset.indexCount() / 6),
			asset.contentHash(),
			asset.meshGeneration(),
			0
		);
	}

	private static void recordInitialTranslucentSort(ChunkBuildOutput output, TerrainSectionAsset asset) {
		if (output == null || asset == null || asset.initialSortGeneration() <= 0L) {
			return;
		}
		byte[] indexBytes = asset.asset().indexBytes();
		if (indexBytes.length == 0) {
			return;
		}
		long sortedIndexHash = sortedIndexHash(indexBytes);
		long sortedIndexSampleHash = sortedIndexSampleHash(indexBytes);
		String sortedIndexSample = sortedIndexSample(indexBytes, asset.indexType(), 12);
		String sorterType = initialTranslucentSorterType(output);
		long sectionPos = output.render.getPositionAsLong();
		recordTranslucentSortPayloadEvent(
			sectionPos,
			output.submitTime,
			asset,
			output.render.getOriginX(),
			output.render.getOriginY(),
			output.render.getOriginZ(),
			"translucent-source-sort",
			asset.initialSortGeneration(),
			currentCameraX(),
			currentCameraY(),
			currentCameraZ(),
			output.render.getOriginX() + 8.0D,
			output.render.getOriginY() + 8.0D,
			output.render.getOriginZ() + 8.0D,
			Math.max(0, asset.indexCount() / 6),
			sortedIndexHash,
			asset.initialSortGeneration(),
			sorterType,
			sortedIndexHash,
			0L,
			sortedIndexSampleHash,
			0L,
			sortedIndexSample
		);
		recordTranslucentSortPayloadEvent(
			sectionPos,
			output.submitTime,
			asset,
			output.render.getOriginX(),
			output.render.getOriginY(),
			output.render.getOriginZ(),
			"translucent-rust-sort-copy",
			asset.initialSortGeneration(),
			currentCameraX(),
			currentCameraY(),
			currentCameraZ(),
			output.render.getOriginX() + 8.0D,
			output.render.getOriginY() + 8.0D,
			output.render.getOriginZ() + 8.0D,
			Math.max(0, asset.indexCount() / 6),
			sortedIndexHash,
			asset.initialSortGeneration(),
			"rust-route-cache-initial",
			0L,
			sortedIndexHash,
			0L,
			sortedIndexSampleHash,
			sortedIndexSample
		);
		recordTranslucentSortPayloadEvent(
			sectionPos,
			output.submitTime,
			asset,
			output.render.getOriginX(),
			output.render.getOriginY(),
			output.render.getOriginZ(),
			"translucent-sort-registered:authority=sodium-build-index-payload"
				+ ":sourceHash=" + sortedIndexHash
				+ ":copiedHash=" + sortedIndexHash
				+ ":sourceSampleHash=" + sortedIndexSampleHash
				+ ":copiedSampleHash=" + sortedIndexSampleHash
				+ ":sample=" + sortedIndexSample
				+ ":sorterType=" + sorterType,
			asset.initialSortGeneration(),
			currentCameraX(),
			currentCameraY(),
			currentCameraZ(),
			output.render.getOriginX() + 8.0D,
			output.render.getOriginY() + 8.0D,
			output.render.getOriginZ() + 8.0D,
			Math.max(0, asset.indexCount() / 6),
			sortedIndexHash,
			asset.initialSortGeneration(),
			sorterType,
			sortedIndexHash,
			sortedIndexHash,
			sortedIndexSampleHash,
			sortedIndexSampleHash,
			sortedIndexSample
		);
	}

	private static void recordTranslucentSortPayloadEvent(
		long sectionPos,
		long sourceGeneration,
		TerrainSectionAsset asset,
		int sectionOriginX,
		int sectionOriginY,
		int sectionOriginZ,
		String reason,
		long sortGeneration,
		double cameraX,
		double cameraY,
		double cameraZ,
		double sortOriginX,
		double sortOriginY,
		double sortOriginZ,
		int primitiveCount,
		long sortedIndexHash,
		long indexUploadGeneration,
		String sorterType,
		long sourceSortedIndexHash,
		long rustCopiedSortedIndexHash,
		long sourceSortedIndexSampleHash,
		long rustCopiedSortedIndexSampleHash,
		String sortedIndexSample
	) {
		recordEvent(
			sectionPos,
			ChunkSectionLayer.TRANSLUCENT,
			sourceGeneration,
			asset.meshGeneration(),
			asset.meshGeneration(),
			atlasGeneration,
			asset,
			sectionOriginX,
			sectionOriginY,
			sectionOriginZ,
			0.0F,
			0.0F,
			0.0F,
			reason,
			0L,
			0L,
			0L,
			0L,
			sortGeneration,
			cameraX,
			cameraY,
			cameraZ,
			sortOriginX,
			sortOriginY,
			sortOriginZ,
			primitiveCount,
			sortedIndexHash,
			indexUploadGeneration,
			0,
			sorterType,
			sourceSortedIndexHash,
			rustCopiedSortedIndexHash,
			sourceSortedIndexSampleHash,
			rustCopiedSortedIndexSampleHash,
			sortedIndexSample
		);
	}

	/**
	 * Submits the explicitly owned whole-frame source's CPU-built sections.  It
	 * intentionally accepts sections rather than Sodium render lists: those
	 * lists are coupled to Java GL region storage and must never exist on this
	 * route.
	 */
	public static void enqueueWholeFrameTerrainSections(Iterable<RenderSection> sections, Camera camera,
			int viewportWidth, int viewportHeight) {
		enqueueWholeFrameTerrainSections(sections, List.of(), camera, viewportWidth, viewportHeight);
	}

	public static void enqueueWholeFrameTerrainSections(Iterable<RenderSection> sections,
			Iterable<RenderSection> shadowCandidates, Camera camera,
			int viewportWidth, int viewportHeight) {
		if ((sections == null) || camera == null) {
			return;
		}
		RustGalWorldPrimitiveRenderer.seedStaticTerrainFrameCamera(
			camera.getPosition().x(), camera.getPosition().y(), camera.getPosition().z());
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.visibility-snapshot");
		TerrainSetScratch scratch = TERRAIN_SET_SCRATCH.get();
		scratch.clearAll();
		List<RenderSection> sectionSnapshot = snapshotBuiltTerrainSections(
			sections, scratch.seenPositions, scratch.sectionSnapshot);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.visibility-snapshot");
		// Mesh residency is intentionally longer lived than visibility. Publish
		// the complete semantic visibility replacement below so the primitive
		// frame cannot replay terrain that this frame's CPU source did not select.
		// This is derived only from the copied section/layer assets, never from a
		// Sodium render list or a backend handle.
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.mesh-key-set");
		LongOpenHashSet visibleMeshKeys = visibleWholeFrameMeshKeys(
			sectionSnapshot, scratch.visibleMeshKeys, scratch.solidAssetSnapshot,
			scratch.cutoutAssetSnapshot, scratch.translucentSectionSnapshot);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.mesh-key-set");
		if (net.minecraft.client.dev.GraphicsFrameBenchmark.isActiveForDiagnostics()) {
			recordTerrainIndexWorkload(sectionSnapshot);
		}
		LongOpenHashSet visibleSubmissions = scratch.visibleSubmissions;
		String fault = activeFault();
		boolean detailedDiagnostics = detailedTerrainDiagnosticsEnabled(fault);
		boolean trackTerrainCounters = terrainCountersEnabled(fault);
		// Ordinary frames send compact section entries; Rust places each one and
		// draws the generation it acknowledged. Diagnostic, fault-injection and
		// resource-reload frames keep the per-record path their receipts need.
		boolean compactTerrain = !resourceReloadStaging && fault.isEmpty() && !detailedDiagnostics
			&& !Boolean.getBoolean("mattmc.dev.perRecordStaticTerrain");
		if (compactTerrain) {
			enqueueCompactTerrainSections(scratch, sectionSnapshot, camera, trackTerrainCounters);
		} else {
			boolean cachedOpaqueReplay = false;
			if (!resourceReloadStaging && fault.isEmpty() && !detailedDiagnostics
				&& !Boolean.getBoolean("mattmc.dev.disableCachedStaticTerrainReplay")) {
				scratch.cachedOpaqueMeshKeys.clear();
				scratch.cachedOpaqueMeshKeyCount = 0;
				for (int index = 0; index < sectionSnapshot.size(); index++) {
					TerrainSectionAsset solid = scratch.solidAssetSnapshot.get(index);
					TerrainSectionAsset cutout = scratch.cutoutAssetSnapshot.get(index);
					if (solid != null && scratch.cachedOpaqueMeshKeys.add(solid.meshKey())) {
						appendCachedOpaqueMeshKey(scratch, solid.meshKey(), solid.meshGeneration());
					}
					if (cutout != null && scratch.cachedOpaqueMeshKeys.add(cutout.meshKey())) {
						appendCachedOpaqueMeshKey(scratch, cutout.meshKey(), cutout.meshGeneration());
					}
				}
				cachedOpaqueReplay = scratch.cachedOpaqueMeshKeyCount > 0
					&& RustGalWorldPrimitiveRenderer.enqueueCachedStaticTerrainInstances(
						scratch.cachedOpaqueMeshKeyArray, scratch.cachedOpaqueMeshGenerationArray,
						scratch.cachedOpaqueMeshKeyCount,
						viewportWidth, viewportHeight);
			}
			if (trackTerrainCounters) {
				net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
				"world.static-terrain.cached-opaque-replay",
				cachedOpaqueReplay ? scratch.cachedOpaqueMeshKeyCount : 0L);
			}
			int translucentDrawOrder = 0;
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.opaque-submit");
			if (cachedOpaqueReplay) {
				if (trackTerrainCounters) {
					visibleLayerProbes.addAndGet((long)sectionSnapshot.size() * 2L);
				}
				for (int index = 0; index < sectionSnapshot.size(); index++) {
					RenderSection section = sectionSnapshot.get(index);
					recordCachedOpaqueLayerSubmission(section, ChunkSectionLayer.SOLID,
						scratch.solidAssetSnapshot.get(index), visibleSubmissions, trackTerrainCounters);
					recordCachedOpaqueLayerSubmission(section, ChunkSectionLayer.CUTOUT_MIPPED,
						scratch.cutoutAssetSnapshot.get(index), visibleSubmissions, trackTerrainCounters);
				}
			} else {
				for (int index = 0; index < sectionSnapshot.size(); index++) {
					RenderSection section = sectionSnapshot.get(index);
					enqueueSectionLayer(section, ChunkSectionLayer.SOLID, scratch.solidAssetSnapshot.get(index),
						camera, viewportWidth, viewportHeight, 0,
						visibleSubmissions, fault, detailedDiagnostics, trackTerrainCounters);
					enqueueSectionLayer(section, ChunkSectionLayer.CUTOUT_MIPPED, scratch.cutoutAssetSnapshot.get(index),
						camera, viewportWidth, viewportHeight, 0,
						visibleSubmissions, fault, detailedDiagnostics, trackTerrainCounters);
				}
			}
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.opaque-submit");
			// Translucent sections are a single camera-sorted semantic stream. The
			// legacy Sodium render list is unavailable on the Rust whole-frame route,
			// so retain no list/GL state and order copied section centers explicitly.
			// Restrict the sort to sections that actually own a published translucent
			// asset; sorting every opaque/cutout-only section produced identical output
			// while adding O(visible sections log visible sections) frame work.
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.translucent-sort");
			ArrayList<RenderSection> translucentSections = scratch.translucentSectionSnapshot;
			translucentSections.sort(Comparator.comparingDouble((RenderSection section) -> {
				double dx = section.getOriginX() + 8.0D - camera.getPosition().x();
				double dy = section.getOriginY() + 8.0D - camera.getPosition().y();
				double dz = section.getOriginZ() + 8.0D - camera.getPosition().z();
				return dx * dx + dy * dy + dz * dz;
			}).reversed());
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.translucent-sort");
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.translucent-submit");
			for (RenderSection section : translucentSections) {
				enqueueSectionLayer(section, ChunkSectionLayer.TRANSLUCENT, camera, viewportWidth, viewportHeight,
					translucentDrawOrder++, visibleSubmissions, fault, detailedDiagnostics, trackTerrainCounters);
			}
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.translucent-submit");
		}
		// This is a separate bounded semantic stream. Candidate assets stay
		// resident, but only Rust may select them into a source shadow pass.
		if (shadowCandidates != null) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.shadow-candidate-limit");
			shadowCandidates = nearestShadowCandidates(shadowCandidates, camera);
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.shadow-candidate-limit");
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.shadow-submit");
			int[] depthPolicies = new int[SHADOW_CANDIDATE_LAYERS.length];
			for (int slot = 0; slot < depthPolicies.length; slot++) {
				depthPolicies[slot] = terrainDepthPolicy(SHADOW_CANDIDATE_LAYERS[slot]);
			}
			// A camera-visible section already contributed every layer asset.
			// Candidate sections are distinct and mesh keys are unique per
			// section layer, so no key here can repeat a visible or earlier one.
			// One publication read covers every other candidate's row.
			int lookups = 0;
			for (RenderSection section : shadowCandidates) {
				if (section == null || !section.isBuilt() || scratch.seenPositions.contains(section.getPositionAsLong())) continue;
				if (lookups == scratch.shadowRowSections.length) {
					scratch.shadowRowSections = Arrays.copyOf(scratch.shadowRowSections, lookups * 2);
				}
				scratch.shadowRowSections[lookups++] = section.getPositionAsLong();
			}
			if (scratch.shadowRows.length < lookups * 6) {
				scratch.shadowRows = new long[scratch.shadowRowSections.length * 6];
			}
			RustTerrainPublication.rows(scratch.shadowRowSections, lookups, scratch.shadowRows);
			int lookup = 0;
			for (RenderSection section : shadowCandidates) {
				if (section == null || !section.isBuilt()) continue;
				if (!scratch.seenPositions.contains(section.getPositionAsLong())) {
					int row = lookup++ * 6;
					for (int slot = 0; slot < SHADOW_CANDIDATE_LAYERS.length; slot++) {
						long meshKey = scratch.shadowRows[row + slot * 2];
						if (meshKey == 0L) continue;
						scratch.shadows.append(meshKey, scratch.shadowRows[row + slot * 2 + 1],
							section.getOriginX(), section.getOriginY(), section.getOriginZ(),
							depthPolicies[slot], 0);
					}
				}
				var animatedSprites = section.getAnimatedSprites();
				if (animatedSprites != null) {
					for (var sprite : animatedSprites) {
						RustGalWorldPrimitiveRenderer.recordAtlasSpriteUse(
							sprite.semanticAnimationResource(), sprite.atlasLocation(),
							sprite.contents().name());
					}
				}
			}
			RustGalWorldPrimitiveRenderer.enqueueStaticTerrainShadowCandidates(
				scratch.shadows.meshKeys, scratch.shadows.meshGenerations, scratch.shadows.sectionOrigins,
				scratch.shadows.depthPolicies, scratch.shadows.count,
				camera.getPosition().x(), camera.getPosition().y(), camera.getPosition().z());
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.shadow-submit");
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.visibility-reconcile");
		if (!resourceReloadStaging && !compactTerrain) {
			RustGalWorldPrimitiveRenderer.reconcileStaticTerrainVisibility(visibleMeshKeys);
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.visibility-reconcile");
		// Report the semantic terrain workload at the producer boundary. These
		// names are parity-family labels only: the work above is Rust-owned CPU
		// extraction and explicit mesh submission, never a Java Sodium draw.
		if (!sectionSnapshot.isEmpty()) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordPhaseSample("sodium.terrain.setup", 1L);
			// The explicit producer owns three layer passes (solid, cutout, and
			// translucent) even when one pass has zero geometry. Record each semantic
			// pass separately, matching Frozen's producer-phase accounting without
			// pretending that an empty pass issued a backend draw.
			for (int layerPass = 0; layerPass < 3; layerPass++) {
				net.minecraft.client.dev.GraphicsFrameBenchmark.recordPhaseSample("sodium.terrain.draw", 1L);
			}
		}
		// The companion receipt is built from the same CPU-owned RenderSection
		// values that this semantic callsite submitted. It deliberately contains
		// no Sodium render list, GL object, or backend handle.
		if (detailedDiagnostics) {
			net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustWholeFrameEnqueueCoverage(
				sectionSnapshot,
				currentGameplayFrameId(),
				RustGalWholeFrameTerrainSource.isWholeFrameTerrainQueueDrained(),
				camera.getPosition().x(),
				camera.getPosition().y(),
				camera.getPosition().z(),
				viewportWidth,
				viewportHeight
			);
		}
	}

	/**
	 * Appends the frame's visible section layers as compact entries: solid and
	 * cutout in visibility order, then translucent back to front by section
	 * centre. Each visible section reports its animated sprites, as the
	 * per-record path did for every accepted layer.
	 */
	private static void enqueueCompactTerrainSections(TerrainSetScratch scratch, List<RenderSection> sectionSnapshot,
			Camera camera, boolean trackTerrainCounters) {
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.compact-submit");
		CompactTerrainArrays entries = scratch.sections;
		LongOpenHashSet visibleSubmissions = scratch.visibleSubmissions;
		int solidDepth = terrainDepthPolicy(ChunkSectionLayer.SOLID);
		int cutoutDepth = terrainDepthPolicy(ChunkSectionLayer.CUTOUT_MIPPED);
		int translucentDepth = terrainDepthPolicy(ChunkSectionLayer.TRANSLUCENT);
		for (int index = 0; index < sectionSnapshot.size(); index++) {
			RenderSection section = sectionSnapshot.get(index);
			boolean submitted = appendCompactLayer(entries, visibleSubmissions, section, ChunkSectionLayer.SOLID,
				scratch.solidAssetSnapshot.get(index), solidDepth, 0, trackTerrainCounters);
			submitted |= appendCompactLayer(entries, visibleSubmissions, section, ChunkSectionLayer.CUTOUT_MIPPED,
				scratch.cutoutAssetSnapshot.get(index), cutoutDepth, 0, trackTerrainCounters);
			if (submitted) {
				recordAnimatedSpriteUse(section);
			}
		}
		ArrayList<RenderSection> translucentSections = scratch.translucentSectionSnapshot;
		double cameraX = camera.getPosition().x();
		double cameraY = camera.getPosition().y();
		double cameraZ = camera.getPosition().z();
		translucentSections.sort(Comparator.comparingDouble((RenderSection section) -> {
			double dx = section.getOriginX() + 8.0D - cameraX;
			double dy = section.getOriginY() + 8.0D - cameraY;
			double dz = section.getOriginZ() + 8.0D - cameraZ;
			return dx * dx + dy * dy + dz * dz;
		}).reversed());
		for (RenderSection section : translucentSections) {
			TerrainSectionAsset asset = sectionAsset(section.getPositionAsLong(), ChunkSectionLayer.TRANSLUCENT);
			int flags = asset != null && terrainCameraSortRequested(ChunkSectionLayer.TRANSLUCENT, asset.translucentSortType())
				? RustGalWorldPrimitiveRenderer.WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS : 0;
			if (appendCompactLayer(entries, visibleSubmissions, section, ChunkSectionLayer.TRANSLUCENT, asset,
					translucentDepth, flags, trackTerrainCounters)) {
				recordAnimatedSpriteUse(section);
			}
		}
		RustGalWorldPrimitiveRenderer.enqueueStaticTerrainSections(
			entries.meshKeys, entries.meshGenerations, entries.sectionOrigins, entries.depthPolicies, entries.flags,
			entries.count, cameraX, cameraY, cameraZ);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.compact-submit");
	}

	private static boolean appendCompactLayer(CompactTerrainArrays entries, LongOpenHashSet visibleSubmissions,
			RenderSection section, ChunkSectionLayer layer, TerrainSectionAsset asset, int depthPolicy, int flags,
			boolean trackTerrainCounters) {
		if (trackTerrainCounters) {
			visibleLayerProbes.incrementAndGet();
		}
		if (asset == null || !visibleSubmissions.add(asset.meshKey())) {
			return false;
		}
		entries.append(asset.meshKey(), asset.meshGeneration(),
			section.getOriginX(), section.getOriginY(), section.getOriginZ(), depthPolicy, flags);
		if (trackTerrainCounters) {
			long enqueueFrameId = rustEnqueueFrames.incrementAndGet();
			visibleLayerSubmissions.incrementAndGet();
			recordCurrentFrameVisibleSubmission(enqueueFrameId, section.getPositionAsLong(), layer,
				asset.meshKey(), asset.meshGeneration());
			recordVisibleSubmissionIdentity(section.getPositionAsLong(), layer, asset.meshGeneration());
		}
		return true;
	}

	static void recordAnimatedSpriteUse(RenderSection section) {
		var animatedSprites = section.getAnimatedSprites();
		if (animatedSprites != null) {
			for (var sprite : animatedSprites) {
				RustGalWorldPrimitiveRenderer.recordAtlasSpriteUse(
					sprite.semanticAnimationResource(), sprite.atlasLocation(), sprite.contents().name());
			}
		}
	}

	/** Benchmark-only geometry volume, derived from copied semantic assets. */
	private static void recordTerrainIndexWorkload(List<RenderSection> sections) {
		long solidIndices = 0L;
		long cutoutIndices = 0L;
		long translucentIndices = 0L;
		long staticTranslucentSections = 0L;
		long dynamicTranslucentSections = 0L;
		for (RenderSection section : sections) {
			long sectionPos = section.getPositionAsLong();
			TerrainSectionAsset[] row = sectionAssetRow(sectionPos);
			TerrainSectionAsset solid = row == null ? null : row[0];
			TerrainSectionAsset cutout = row == null ? null : row[1];
			TerrainSectionAsset translucent = row == null ? null : row[2];
			if (solid != null) {
				solidIndices += solid.indexCount();
			}
			if (cutout != null) {
				cutoutIndices += cutout.indexCount();
			}
			if (translucent != null) {
				translucentIndices += translucent.indexCount();
				if (translucent.translucentSortType() == SortType.DYNAMIC) dynamicTranslucentSections++;
				else staticTranslucentSections++;
			}
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample("world.static-terrain.solid-indices", solidIndices);
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample("world.static-terrain.cutout-indices", cutoutIndices);
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample("world.static-terrain.translucent-indices", translucentIndices);
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample("world.static-terrain.static-translucent-sections", staticTranslucentSections);
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample("world.static-terrain.dynamic-translucent-sections", dynamicTranslucentSections);
	}

	/**
	 * Resolves the exact asset identities addressed by a whole-frame semantic
	 * visibility set. Empty layers deliberately contribute nothing: they have
	 * no mesh instance to retain or execute.
	 */
	private static LongOpenHashSet visibleWholeFrameMeshKeys(Iterable<RenderSection> sections,
			LongOpenHashSet visibleMeshKeys, List<TerrainSectionAsset> solidAssets,
			List<TerrainSectionAsset> cutoutAssets, List<RenderSection> translucentSections) {
		visibleMeshKeys.clear();
		solidAssets.clear();
		cutoutAssets.clear();
		translucentSections.clear();
		for (RenderSection section : sections) {
			if (section == null) {
				continue;
			}
			long sectionPos = section.getPositionAsLong();
			TerrainSectionAsset[] row = sectionAssetRow(sectionPos);
			TerrainSectionAsset solid = row == null ? null : row[0];
			TerrainSectionAsset cutout = row == null ? null : row[1];
			TerrainSectionAsset translucent = row == null ? null : row[2];
			solidAssets.add(solid);
			cutoutAssets.add(cutout);
			if (solid != null) {
				visibleMeshKeys.add(solid.meshKey());
			}
			if (cutout != null) {
				visibleMeshKeys.add(cutout.meshKey());
			}
			if (translucent != null) {
				visibleMeshKeys.add(translucent.meshKey());
				translucentSections.add(section);
			}
		}
		return visibleMeshKeys;
	}

	/**
	 * Copies the unique built section identities for the Rust semantic route.
	 * Upstream visibility iterables may contain repeated entries; canonical
	 * packed positions are the stable section identity and prevent duplicate
	 * mesh instances without retaining Sodium render-list state.
	 */
	static List<RenderSection> snapshotBuiltTerrainSections(Iterable<RenderSection> sections) {
		if (sections == null) {
			return List.of();
		}
		TerrainSetScratch scratch = TERRAIN_SET_SCRATCH.get();
		return snapshotBuiltTerrainSections(sections, scratch.seenPositions, new ArrayList<>());
	}

	/**
	 * A wider window than the caster budget keeps the nearest casters (the
	 * shadow map's resolution concentrates there and distant ones fall outside
	 * the pack's shadow distance first) instead of failing the frame.
	 */
	private static Iterable<RenderSection> nearestShadowCandidates(Iterable<RenderSection> candidates, Camera camera) {
		int built = 0;
		for (RenderSection section : candidates) {
			if (section != null && section.isBuilt()) built++;
		}
		if (built <= MAX_SHADOW_CANDIDATE_SECTIONS) {
			return candidates;
		}
		if (!shadowCandidateTruncationLogged) {
			shadowCandidateTruncationLogged = true;
			LOGGER.warn("Rust whole-frame shadow casters limited to the nearest {} of {} sections",
				MAX_SHADOW_CANDIDATE_SECTIONS, built);
		}
		double cx = camera.getPosition().x();
		double cy = camera.getPosition().y();
		double cz = camera.getPosition().z();
		ArrayList<RenderSection> nearest = new ArrayList<>(built);
		for (RenderSection section : candidates) {
			if (section != null && section.isBuilt()) nearest.add(section);
		}
		nearest.sort(Comparator.comparingDouble((RenderSection section) -> {
			double dx = section.getOriginX() + 8.0D - cx;
			double dy = section.getOriginY() + 8.0D - cy;
			double dz = section.getOriginZ() + 8.0D - cz;
			return dx * dx + dy * dy + dz * dz;
		}).thenComparingLong(RenderSection::getPositionAsLong));
		return nearest.subList(0, MAX_SHADOW_CANDIDATE_SECTIONS);
	}

	private static List<RenderSection> snapshotBuiltTerrainSections(Iterable<RenderSection> sections,
			LongOpenHashSet seenPositions, List<RenderSection> snapshot) {
		seenPositions.clear();
		snapshot.clear();
		for (RenderSection section : sections) {
			if (section != null && section.isBuilt()
				&& seenPositions.add(section.getPositionAsLong())) {
				if (snapshot.size() >= MAX_SEMANTIC_TERRAIN_SECTIONS) {
					throw new IllegalStateException(
						"Rust whole-frame semantic terrain section bound exceeded "
							+ MAX_SEMANTIC_TERRAIN_SECTIONS
					);
				}
				snapshot.add(section);
			}
		}
		return snapshot;
	}

	public static void invalidateForResourceReload() {
		// Native upload acknowledgement permits Java to release opaque/cutout mesh
		// payloads. A reload therefore explicitly rebuilds the semantic source
		// rather than pretending an absent Java payload can recreate a Rust mesh.
		// Until the rebuild is acknowledged, this terrain family remains unavailable
		// instead of presenting stale resource-pack materials.
		RustGalWholeFrameTerrainSource.requestResourceReload();
		// Keep the last complete atlas/mesh generation drawable while the CPU source
		// rebuilds. New meshes use generation-scoped keys and upload off-screen; the
		// complete replacement and its atlas are published together after quiescence.
		discardStagedResourceReloadAssets();
		resourceReloadStaging = true;
		resourceReloadCommitInProgress = false;
		TRANSLUCENT_EXECUTION_METADATA.clear();
		TEXTURE_PROBE_QUADS.clear();
		DistantHorizonsFaceMaterialResolver.clearCachedStateResolutions();
		// DH column provenance contains atlas sprite identities as well as copied
		// geometry. Retire those generations with the same resource boundary so a
		// pack replacement cannot submit pre-reload materials through the Rust
		// whole-frame route.
		DistantHorizonsSemanticCollector.invalidateForResourceReload();
		synchronized (RustGalTerrainRenderer.class) {
				atlasPayload = null;
				atlasMipPayloads = List.of();
				atlasAnimationSource = null;
			copiedAtlasSemanticGeneration = 0L;
			copiedAtlasSemanticFrameKey = Long.MIN_VALUE;
			normalAtlasPayload = null;
			specularAtlasPayload = null;
			atlasGeneration++;
			registeredAtlasGeneration = 0L;
			publishedWorldMeshAtlasGeneration = 0L;
			publishedWorldMeshAtlasPayload = null;
		}
		invalidations.incrementAndGet();
			recordEvent(0L, ChunkSectionLayer.SOLID, 0L, 0L, 0L, atlasGeneration, null, 0, 0, 0, 0.0F, 0.0F, 0.0F, "resource-reload");
	}

	public static void invalidateForWorldUnload() {
		discardStagedResourceReloadAssets();
		resourceReloadStaging = false;
		resourceReloadCommitInProgress = false;
		// DH snapshots are CPU-only semantic copies, but they are world-scoped.
		// Discard them with the existing terrain world epoch so no later Rust
		// route can accidentally observe data from a disconnected level.
		DistantHorizonsSemanticCollector.clear();
		// The last submitted frame belongs to the disconnected level. Clear its
		// per-frame visibility receipt as part of the same boundary so menu-state
		// diagnostics cannot mistake stale submissions for active world work.
		lastVisibleSubmissionFrameId.set(Long.MIN_VALUE);
		currentFrameVisibleLayerSubmissions.set(0L);
		currentFrameVisibleFingerprint.set(0L);
		TRANSLUCENT_EXECUTION_METADATA.clear();
		TEXTURE_PROBE_QUADS.clear();
		for (LayerKey key : List.copyOf(SECTION_ASSETS.keySet())) {
			removeLayer(key.sectionPos(), key.layer(), "world-unload");
		}
		RustTerrainPublication.clear();
		invalidations.incrementAndGet();
			recordEvent(0L, ChunkSectionLayer.SOLID, 0L, 0L, 0L, atlasGeneration, null, 0, 0, 0, 0.0F, 0.0F, 0.0F, "world-unload");
	}

	public static void removeSection(int x, int y, int z, String reason) {
		long sectionPos = net.minecraft.core.SectionPos.asLong(x, y, z);
		removeLayer(sectionPos, ChunkSectionLayer.SOLID, reason);
		removeLayer(sectionPos, ChunkSectionLayer.CUTOUT_MIPPED, reason);
		removeLayer(sectionPos, ChunkSectionLayer.TRANSLUCENT, reason);
	}

	/**
	 * Rust owns section geometry after its explicit asset update is accepted
	 * (translucent order included: Rust re-sorts per frame). Keep the section's
	 * generation and draw metadata for the visible semantic callsite, but
	 * discard Java's duplicate vertex/index payload.
	 */
	public static void releaseUploadedStaticTerrainPayload(long meshKey, long meshGeneration) {
		TerrainAssetIdentity identity = SECTION_ASSETS_BY_MESH_KEY.get(meshKey);
		if (identity == null) {
			return;
		}
		LayerKey layerKey = identity.layerKey();
		TerrainSectionAsset asset = identity.asset();
		if (asset.meshGeneration() != meshGeneration || asset.asset() == null) {
			return;
		}
		TerrainSectionAsset released = asset.releaseCpuPayload();
		if (SECTION_ASSETS.replace(layerKey, asset, released)) {
			mirrorSectionAssetRow(layerKey);
			SECTION_ASSETS_BY_MESH_KEY.replace(meshKey, identity, new TerrainAssetIdentity(layerKey, released));
		}
	}

	public static TerrainDiagnostics diagnosticsSnapshot() {
		synchronized (RECENT_EVENTS) {
			long currentFrameId = currentGameplayFrameId();
			long currentVisibleSubmissions =
				lastVisibleSubmissionFrameId.get() == currentFrameId ? currentFrameVisibleLayerSubmissions.get() : 0L;
			long visibleFingerprint = currentVisibleSubmissions > 0L ? currentFrameVisibleFingerprint.get() : 0L;
			return new TerrainDiagnostics(
				SECTION_ASSETS.size(),
				SECTION_ASSETS.size(),
				SECTION_ASSETS.size(),
				currentVisibleSubmissions,
				visibleFingerprint,
				atlasGeneration,
				registeredAtlasGeneration,
				activeTerrainVertexStride(),
				activeTerrainVertexStride(),
				acceptedBuildOutputs.get(),
				skippedRouteBuildOutputs.get(),
				skippedUnsupportedAnimatedSections.get(),
				skippedUnsupportedFluidTranslucentSections.get(),
				acceptedWaterAnimatedSections.get(),
				unsupportedFluidRejectedSections.get(),
				skippedEmptyLayers.get(),
				registeredMeshes.get(),
				registeredTranslucentSorts.get(),
				registeredTranslucentSortBytes.get(),
				translucentSortGenerations.get(),
				texturePayloadUpdates.get(),
				texturePayloadUpdateBytes.get(),
				atlasTextureOnlyUpdates.get(),
				atlasMissingPayloadUpdates.get(),
				atlasMalformedPayloadUpdates.get(),
				atlasPartialPayloadUpdates.get(),
				removedLayers.get(),
				visibleLayerProbes.get(),
				visibleLayerSubmissions.get(),
				failedLayerSubmissions.get(),
				invalidations.get(),
				terrainExtractionFrames.get(),
				rustEnqueueFrames.get(),
				List.copyOf(LIFECYCLE_EVENTS),
				List.copyOf(TRANSLUCENT_EVENTS),
				List.copyOf(RECENT_EVENTS)
			);
		}
	}

	/**
	 * Counter-only view used by the steady-state benchmark. The full diagnostics
	 * snapshot intentionally copies bounded event histories for crash/capture
	 * evidence; polling those histories every measured frame would make the audit
	 * machinery part of the workload being measured.
	 */
	public static TerrainPerformanceCounters performanceCountersSnapshot() {
		long currentFrameId = currentGameplayFrameId();
		long currentVisibleSubmissions =
			lastVisibleSubmissionFrameId.get() == currentFrameId ? currentFrameVisibleLayerSubmissions.get() : 0L;
		return new TerrainPerformanceCounters(
			SECTION_ASSETS.size(),
			SECTION_ASSETS.size(),
			SECTION_ASSETS.size(),
			currentVisibleSubmissions > 0L ? currentFrameVisibleFingerprint.get() : 0L,
			atlasGeneration,
			acceptedBuildOutputs.get(),
			skippedUnsupportedAnimatedSections.get(),
			skippedEmptyLayers.get(),
			registeredMeshes.get(),
			texturePayloadUpdates.get(),
			texturePayloadUpdateBytes.get(),
			removedLayers.get(),
			failedLayerSubmissions.get(),
			invalidations.get(),
			terrainExtractionFrames.get()
		);
	}

	/**
	 * Completed whole-frame evidence for capture gates. This intentionally tracks
	 * static terrain separately from Distant Horizons so a distant-only frame
	 * cannot validate a static-terrain screenshot.
	 */
	public static StaticTerrainExecutionSnapshot staticTerrainExecutionSnapshot() {
		return new StaticTerrainExecutionSnapshot(
			lastExecutedStaticTerrainFrameId.get(),
			lastExecutedStaticTerrainSubmissionId.get(),
			lastExecutedStaticTerrainInstances.get()
		);
	}

	/**
	 * Capture-only route evidence for a specific client section. Client chunk
	 * residency outlives normal render-radius visibility, so deterministic DH
	 * scenarios must consult the actual completed static-terrain submission
	 * rather than {@code ClientLevel#isLoaded} when excluding the near route.
	 */
	public static boolean staticTerrainSectionExecutedInLastCompletedFrame(BlockPos blockPos) {
		if (blockPos == null) {
			return false;
		}
		long executionFrame = lastExecutedStaticTerrainFrameId.get();
		if (executionFrame <= 0L) {
			return false;
		}
		long sectionPos = net.minecraft.core.SectionPos.asLong(
			net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getX()),
			net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getY()),
			net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getZ())
		);
		synchronized (RECENT_EVENTS) {
			return RECENT_EVENTS.stream().anyMatch(event -> event.sectionPos() == sectionPos
				&& event.executionFrameId() == executionFrame
				&& "executed-submit".equals(event.reason()));
		}
	}

	/**
	 * Test-only semantic receipt for the copied terrain atlas. It deliberately
	 * compares source sprite pixels with the corresponding copied atlas rectangle
	 * instead of observing a GL/Vulkan texture or backend binding.
	 */
	public static TerrainAtlasReceipt terrainAtlasReceipt() {
		try {
			ensureAtlasPayload();
			byte[] payload = atlasPayload;
			if (payload == null) {
				return TerrainAtlasReceipt.unavailable("atlas payload is unavailable");
			}
			BufferedImage copiedAtlas = ImageIO.read(new java.io.ByteArrayInputStream(payload));
			if (copiedAtlas == null) {
				return TerrainAtlasReceipt.unavailable("atlas payload did not decode as an image");
			}
			TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
			List<TerrainAtlasSpriteReceipt> sprites = new ArrayList<>();
			for (String path : List.of(
				"block/grass_block_top",
				"block/grass_block_side",
				"block/redstone_ore",
				"block/yellow_terracotta",
				"block/oak_leaves"
			)) {
				ResourceLocation location = ResourceLocation.fromNamespaceAndPath("minecraft", path);
				TextureAtlasSprite sprite = atlas.getSprite(location);
				if (sprite == null || sprite.contents() == null) {
					sprites.add(TerrainAtlasSpriteReceipt.missing(location.toString()));
					continue;
				}
				int width = sprite.contents().width();
				int height = sprite.contents().height();
				long sourceHash = rgbaHash(sprite.contents().originalImage, 0, 0, width, height);
				long copiedHash = rgbaHash(copiedAtlas, sprite.getX(), sprite.getY(), width, height);
				int sampleX = sprite.getX() + width / 2;
				int sampleY = sprite.getY() + height / 2;
				int mirroredSampleY = copiedAtlas.getHeight() - 1 - sampleY;
				sprites.add(new TerrainAtlasSpriteReceipt(
					location.toString(),
					sprite.getX(),
					sprite.getY(),
					width,
					height,
					sourceHash,
					copiedHash,
					atlasSpriteIdentityAt(atlas, sampleX, sampleY),
					atlasSpriteIdentityAt(atlas, sampleX, mirroredSampleY),
					sampleX,
					sampleY,
					mirroredSampleY,
					sourceHash == copiedHash,
					"ok"
				));
			}
			return new TerrainAtlasReceipt(
				true,
				"ok",
				copiedAtlas.getWidth(),
				copiedAtlas.getHeight(),
				rgbaHash(copiedAtlas, 0, 0, copiedAtlas.getWidth(), copiedAtlas.getHeight()),
				List.copyOf(sprites)
			);
		} catch (RuntimeException | IOException error) {
			return TerrainAtlasReceipt.unavailable(error.getMessage());
		}
	}

	private static void cacheTextureProbeQuads(ChunkBuildOutput output, TerrainSectionAsset asset) {
		if (activeTextureProbes.isEmpty() || asset.asset() == null) {
			return;
		}
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = asset.asset().vertices();
		for (TerrainTextureProbe probe : activeTextureProbes) {
			if (TEXTURE_PROBE_QUADS.containsKey(probe.position())) continue;
			for (int first = 0; first + 3 < vertices.size(); first += 4) {
				if (quadBelongsToBlock(vertices, first, asset, probe.position())) {
					TEXTURE_PROBE_QUADS.putIfAbsent(probe.position(), List.copyOf(vertices.subList(first, first + 4)));
					break;
				}
			}
		}
	}

	private static String atlasSpriteIdentityAt(TextureAtlas atlas, int sampleX, int sampleY) {
		for (Map.Entry<ResourceLocation, TextureAtlasSprite> entry : atlas.texturesByName.entrySet()) {
			TextureAtlasSprite candidate = entry.getValue();
			if (candidate == null || candidate.contents() == null) {
				continue;
			}
			int minX = candidate.getX();
			int minY = candidate.getY();
			int maxX = minX + candidate.contents().width();
			int maxY = minY + candidate.contents().height();
			if (sampleX >= minX && sampleX < maxX && sampleY >= minY && sampleY < maxY) {
				return entry.getKey().toString();
			}
		}
		return "<atlas-padding-or-unassigned>";
	}

	/**
	 * Capture-only proof that a copied static-terrain quad still addresses the
	 * atlas region of the block that owns it. This observes CPU semantic mesh
	 * records before FFI; it never reads a backend texture or changes rendering.
	 */
	public static TerrainTextureProbeReceipt terrainTextureProbeReceipt(List<TerrainTextureProbe> probes) {
		if (probes == null || probes.isEmpty()) {
			return new TerrainTextureProbeReceipt(false, "no texture probes", List.of());
		}
		try {
			ensureAtlasPayload();
			TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
			List<TerrainTextureProbeResult> results = new ArrayList<>(probes.size());
			for (TerrainTextureProbe probe : probes) {
				if (probe == null || probe.position() == null || probe.allowedSprites().isEmpty()) {
					return new TerrainTextureProbeReceipt(false, "invalid texture probe", List.copyOf(results));
				}
				List<AtlasUvRegion> allowedRegions = new ArrayList<>(probe.allowedSprites().size());
				for (ResourceLocation identity : probe.allowedSprites()) {
					TextureAtlasSprite sprite = atlas.getSprite(identity);
					if (sprite == null || sprite.contents() == null) {
						results.add(new TerrainTextureProbeResult(
							probe.position(), probe.allowedSprites(), 0, 0, false,
							"missing expected sprite " + identity, List.of()
						));
						allowedRegions.clear();
						break;
					}
					allowedRegions.add(new AtlasUvRegion(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()));
				}
				if (allowedRegions.isEmpty()) {
					continue;
				}
				int matchingQuads = 0;
				int mismatchedQuads = 0;
				List<TerrainTextureProbeObservation> observations = new ArrayList<>();
				for (Map.Entry<LayerKey, TerrainSectionAsset> entry : SECTION_ASSETS.entrySet()) {
					if (entry.getKey().layer() != ChunkSectionLayer.SOLID
						&& entry.getKey().layer() != ChunkSectionLayer.CUTOUT
						&& entry.getKey().layer() != ChunkSectionLayer.CUTOUT_MIPPED) {
						continue;
					}
					TerrainSectionAsset asset = entry.getValue();
					if (asset.asset() == null) {
						// The explicit Rust upload owns this opaque/cutout payload now.
						// Probe diagnostics must report only source bytes that remain
						// resident rather than silently recreating a Java mesh copy.
						continue;
					}
					List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = asset.asset().vertices();
					for (int firstVertex = 0; firstVertex + 3 < vertices.size(); firstVertex += 4) {
						if (!quadBelongsToBlock(vertices, firstVertex, asset, probe.position())) {
							continue;
						}
						matchingQuads++;
						boolean uvMatches = quadUsesAnyAtlasRegion(vertices, firstVertex, allowedRegions);
						if (!uvMatches) {
							mismatchedQuads++;
						}
						if (observations.size() < 12) {
							observations.add(new TerrainTextureProbeObservation(
								entry.getKey().sectionPos(),
								entry.getKey().layer().name(),
								firstVertex / 4,
								uvMatches,
								atlasIdentityForQuad(atlas, vertices, firstVertex),
								vertices.get(firstVertex).atlasU(),
								vertices.get(firstVertex).atlasV()
							));
						}
					}
				}
				// Opaque/cutout payloads are released after explicit Rust upload. Use
				// the bounded semantic samples captured at admission for diagnostics.
				if (matchingQuads == 0) {
					List<VulkanicGalBridge.WorldMeshVertexRecord> cached = TEXTURE_PROBE_QUADS.get(probe.position());
					if (cached != null && cached.size() >= 4) {
						matchingQuads = 1;
						if (!quadUsesAnyAtlasRegion(cached, 0, allowedRegions)) {
							mismatchedQuads = 1;
						}
						observations.add(new TerrainTextureProbeObservation(
							0L, "semantic-probe-cache", 0, mismatchedQuads == 0,
							atlasIdentityForQuad(atlas, cached, 0), cached.get(0).atlasU(), cached.get(0).atlasV()
						));
					}
				}
				boolean matched = matchingQuads > 0 && mismatchedQuads == 0;
				results.add(new TerrainTextureProbeResult(
					probe.position(), probe.allowedSprites(), matchingQuads, mismatchedQuads, matched,
					matched ? "ok" : matchingQuads == 0 ? "no copied terrain quad for probe" : "atlas UV outside expected sprite",
					List.copyOf(observations)
				));
			}
			boolean matched = results.stream().allMatch(TerrainTextureProbeResult::matched);
			String status = matched
				? "ok"
				: results.stream()
					.filter(result -> !result.matched())
					.map(result -> result.position().toShortString() + ":" + result.status())
					.reduce((left, right) -> left + ";" + right)
					.orElse("texture probe mismatch");
			return new TerrainTextureProbeReceipt(matched, status, List.copyOf(results));
		} catch (RuntimeException error) {
			return new TerrainTextureProbeReceipt(false, error.getMessage(), List.of());
		}
	}

	public static void setActiveTextureProbes(List<TerrainTextureProbe> probes) {
		activeTextureProbes = probes == null ? List.of() : List.copyOf(probes);
		TEXTURE_PROBE_QUADS.clear();
	}

	private static String atlasIdentityForQuad(
		TextureAtlas atlas,
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices,
		int firstVertex
	) {
		for (Map.Entry<ResourceLocation, TextureAtlasSprite> entry : atlas.texturesByName.entrySet()) {
			TextureAtlasSprite sprite = entry.getValue();
			if (sprite != null && quadUsesAnyAtlasRegion(vertices, firstVertex, List.of(
				new AtlasUvRegion(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1())
			))) {
				return entry.getKey().toString();
			}
		}
		return "unresolved";
	}

	private static boolean quadBelongsToBlock(
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices,
		int firstVertex,
		TerrainSectionAsset asset,
		BlockPos position
	) {
		final float epsilon = 0.0001F;
		float minX = position.getX() - epsilon;
		float minY = position.getY() - epsilon;
		float minZ = position.getZ() - epsilon;
		float maxX = position.getX() + 1.0F + epsilon;
		float maxY = position.getY() + 1.0F + epsilon;
		float maxZ = position.getZ() + 1.0F + epsilon;
		boolean touchesBoundary = false;
		boolean allAtMinX = true;
		boolean allAtMaxX = true;
		boolean allAtMinY = true;
		boolean allAtMaxY = true;
		boolean allAtMinZ = true;
		boolean allAtMaxZ = true;
		for (int index = firstVertex; index < firstVertex + 4; index++) {
			VulkanicGalBridge.WorldMeshVertexRecord vertex = vertices.get(index);
			float x = asset.sectionOriginX() + vertex.x();
			float y = asset.sectionOriginY() + vertex.y();
			float z = asset.sectionOriginZ() + vertex.z();
			if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
				return false;
			}
			touchesBoundary |= Math.abs(x - position.getX()) <= epsilon || Math.abs(x - (position.getX() + 1.0F)) <= epsilon
				|| Math.abs(y - position.getY()) <= epsilon || Math.abs(y - (position.getY() + 1.0F)) <= epsilon
				|| Math.abs(z - position.getZ()) <= epsilon || Math.abs(z - (position.getZ() + 1.0F)) <= epsilon;
			allAtMinX &= Math.abs(x - position.getX()) <= epsilon;
			allAtMaxX &= Math.abs(x - (position.getX() + 1.0F)) <= epsilon;
			allAtMinY &= Math.abs(y - position.getY()) <= epsilon;
			allAtMaxY &= Math.abs(y - (position.getY() + 1.0F)) <= epsilon;
			allAtMinZ &= Math.abs(z - position.getZ()) <= epsilon;
			allAtMaxZ &= Math.abs(z - (position.getZ() + 1.0F)) <= epsilon;
		}
		if (!touchesBoundary) {
			return false;
		}
		VulkanicGalBridge.WorldMeshVertexRecord firstVertexRecord = vertices.get(firstVertex);
		float normalX = unpackPackedNormalComponent(firstVertexRecord.normalPacked(), 0);
		float normalY = unpackPackedNormalComponent(firstVertexRecord.normalPacked(), 8);
		float normalZ = unpackPackedNormalComponent(firstVertexRecord.normalPacked(), 16);
		return !(allAtMinX && normalX > epsilon)
			&& !(allAtMaxX && normalX < -epsilon)
			&& !(allAtMinY && normalY > epsilon)
			&& !(allAtMaxY && normalY < -epsilon)
			&& !(allAtMinZ && normalZ > epsilon)
			&& !(allAtMaxZ && normalZ < -epsilon);
	}

	private static boolean quadUsesAnyAtlasRegion(
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices,
		int firstVertex,
		List<AtlasUvRegion> allowedRegions
	) {
		for (AtlasUvRegion region : allowedRegions) {
			boolean everyVertexMatches = true;
			for (int index = firstVertex; index < firstVertex + 4; index++) {
				VulkanicGalBridge.WorldMeshVertexRecord vertex = vertices.get(index);
				if (!region.contains(vertex.atlasU(), vertex.atlasV())) {
					everyVertexMatches = false;
					break;
				}
			}
			if (everyVertexMatches) {
				return true;
			}
		}
		return false;
	}

	public static BlockPos chooseLifecycleEditTarget(String scenario) {
		String normalized = scenario == null ? "" : scenario.trim().toLowerCase(Locale.ROOT);
		synchronized (RECENT_EVENTS) {
			BlockPos fallback = null;
			Iterator<TerrainDiagnosticEvent> iterator = RECENT_EVENTS.descendingIterator();
			while (iterator.hasNext()) {
				TerrainDiagnosticEvent event = iterator.next();
				if ("visible-submit".equals(event.reason())
					&& "SOLID".equals(event.layer())
					&& event.sectionOriginValid()
					&& event.vertexCount() > 0
					&& event.localBoundsValid()) {
					int localX = chooseLifecycleLocalCoordinate(event.localMinX(), event.localMaxX());
					int localY = chooseLifecycleLocalCoordinate(event.localMinY(), event.localMaxY());
					int localZ = chooseLifecycleLocalCoordinate(event.localMinZ(), event.localMaxZ());
					BlockPos candidate = new BlockPos(
						event.sectionOriginX() + localX,
						event.sectionOriginY() + localY,
						event.sectionOriginZ() + localZ
					);
					if (fallback == null) {
						fallback = candidate;
					}
					if (switch (normalized) {
						case "boundary-x-edit" -> localX == 15;
						case "boundary-y-edit" -> localY == 15;
						case "boundary-z-edit" -> localZ == 15;
						default -> true;
					}) {
						return candidate;
					}
				}
			}
			return fallback;
		}
	}

	private static int chooseLifecycleLocalCoordinate(float minInclusive, float maxInclusive) {
		if (!Float.isFinite(minInclusive) || !Float.isFinite(maxInclusive)) {
			return 8;
		}
		int lower = Math.max(0, Math.min(15, (int)Math.floor(Math.min(minInclusive, maxInclusive))));
		int upperExclusive = Math.max(1, Math.min(16, (int)Math.ceil(Math.max(minInclusive, maxInclusive))));
		if (upperExclusive <= lower) {
			lower = Math.max(0, Math.min(15, upperExclusive - 1));
			upperExclusive = Math.max(lower + 1, upperExclusive);
		}
		return lower + ((upperExclusive - lower - 1) / 2);
	}

	public static TerrainLayerSnapshot snapshotLayer(BlockPos blockPos, ChunkSectionLayer layer) {
		if (blockPos == null || layer == null) {
			return null;
		}
		long sectionPos = net.minecraft.core.SectionPos.asLong(
			net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getX()),
			net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getY()),
			net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getZ())
		);
		TerrainSectionAsset asset = sectionAsset(sectionPos, layer);
		if (asset == null) {
			return new TerrainLayerSnapshot(sectionPos, layer.name(), 0L, 0L, 0L, 0, 0, 0);
		}
		return new TerrainLayerSnapshot(
			sectionPos,
			layer.name(),
			asset.meshKey(),
			asset.meshGeneration(),
			asset.contentHash(),
			asset.vertexCount(),
			asset.indexCount() * 2,
			asset.sectionCount()
		);
	}

	public static void recordLifecycleMarker(String reason, BlockPos blockPos, ChunkSectionLayer layer, String detail) {
		if (blockPos == null || layer == null) {
			return;
		}
		long sectionPos = net.minecraft.core.SectionPos.asLong(
			net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getX()),
			net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getY()),
			net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getZ())
		);
		TerrainSectionAsset asset = sectionAsset(sectionPos, layer);
		int originX = net.minecraft.core.SectionPos.sectionToBlockCoord(net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getX()));
		int originY = net.minecraft.core.SectionPos.sectionToBlockCoord(net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getY()));
		int originZ = net.minecraft.core.SectionPos.sectionToBlockCoord(net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getZ()));
		recordEvent(
			sectionPos,
			layer,
			0L,
			asset == null ? 0L : asset.meshGeneration(),
			asset == null ? 0L : asset.meshGeneration(),
			atlasGeneration,
			asset,
			asset == null ? originX : asset.sectionOriginX(),
			asset == null ? originY : asset.sectionOriginY(),
			asset == null ? originZ : asset.sectionOriginZ(),
			0.0F,
			0.0F,
			0.0F,
			(detail == null || detail.isBlank()) ? reason : reason + ":" + detail
		);
	}

	public static void recordTranslucentFaultMarker(String fault, BlockPos blockPos, String detail) {
		if (fault == null || fault.isBlank() || blockPos == null) {
			return;
		}
		long sectionPos = net.minecraft.core.SectionPos.asLong(
			net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getX()),
			net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getY()),
			net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getZ())
		);
		TerrainSectionAsset asset = sectionAsset(sectionPos, ChunkSectionLayer.TRANSLUCENT);
		int originX = net.minecraft.core.SectionPos.sectionToBlockCoord(net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getX()));
		int originY = net.minecraft.core.SectionPos.sectionToBlockCoord(net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getY()));
		int originZ = net.minecraft.core.SectionPos.sectionToBlockCoord(net.minecraft.core.SectionPos.blockToSectionCoord(blockPos.getZ()));
		String reason = "translucent-fault-" + fault.trim().toLowerCase(Locale.ROOT);
		if (detail != null && !detail.isBlank()) {
			reason += ":" + detail;
		}
		recordEvent(
			sectionPos,
			ChunkSectionLayer.TRANSLUCENT,
			0L,
			asset == null ? 0L : asset.meshGeneration(),
			asset == null ? 0L : asset.meshGeneration(),
			atlasGeneration,
			asset,
			asset == null ? originX : asset.sectionOriginX(),
			asset == null ? originY : asset.sectionOriginY(),
			asset == null ? originZ : asset.sectionOriginZ(),
			0.0F,
			0.0F,
			0.0F,
			reason,
			0L,
			0L,
			0L,
			0L,
			asset == null ? 0L : asset.meshGeneration(),
			currentCameraX(),
			currentCameraY(),
			currentCameraZ(),
			asset == null ? originX + 8.0D : asset.sectionOriginX() + 8.0D,
			asset == null ? originY + 8.0D : asset.sectionOriginY() + 8.0D,
			asset == null ? originZ + 8.0D : asset.sectionOriginZ() + 8.0D,
			asset == null ? 0 : Math.max(0, asset.indexCount() / 6),
			asset == null ? 0L : asset.contentHash(),
			asset == null ? 0L : asset.meshGeneration(),
			0
		);
	}

	public static void injectAtlasTexturePayloadForDiagnostics(byte[] payload, String reason) {
		byte[] copied = payload == null ? new byte[0] : payload.clone();
		long generation;
		synchronized (RustGalTerrainRenderer.class) {
			atlasPayload = copied;
			atlasMipPayloads = List.of();
			atlasAnimationSource = null;
			atlasGeneration++;
			registeredAtlasGeneration = atlasGeneration;
			generation = atlasGeneration;
		}
		atlasTextureOnlyUpdates.incrementAndGet();
		if (copied.length == 0) {
			atlasMissingPayloadUpdates.incrementAndGet();
		} else if (!isPngPayload(copied)) {
			atlasMalformedPayloadUpdates.incrementAndGet();
		} else if (copied.length < 256) {
			atlasPartialPayloadUpdates.incrementAndGet();
		}
		texturePayloadUpdates.incrementAndGet();
		texturePayloadUpdateBytes.addAndGet(copied.length);
		RustGalWorldPrimitiveRenderer.registerStaticTerrainAtlasTexture(
			new VulkanicGalBridge.WorldMeshTextureAssetRecord(
				RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS,
				copied
			)
		);
		recordEvent(
			0L,
			ChunkSectionLayer.SOLID,
			0L,
			0L,
			0L,
			generation,
			null,
			0,
			0,
			0,
			0.0F,
			0.0F,
			0.0F,
			(reason == null || reason.isBlank()) ? "atlas-texture-only-update" : reason
		);
	}

	/**
	 * Publishes the copied Minecraft block atlas as a Rust-owned world-mesh
	 * resource even when the first visible consumer is Distant Horizons. DH
	 * exact-atlas draws and shader material samplers must not depend on an
	 * unrelated near-terrain section happening to build first. Publish copied
	 * normal/specular maps with the same immutable atlas generation.
	 */
	public static void ensureTerrainAtlasAssetForWorldMesh() {
		// Semantic-only tests deliberately exercise routing without a live client.
		// Asset publication remains deferred until the frame coordinator flushes a
		// real client-owned atlas before native submission.
		if (Minecraft.getInstance() == null) {
			return;
		}
		if (resourceReloadStaging && !resourceReloadCommitInProgress) {
			return;
		}
		ensureAtlasPayload();
		byte[] payload;
		byte[] normalPayload;
		byte[] specularPayload;
		List<byte[]> mipPayloads;
		AtlasAnimationResource animation;
		long generation;
		synchronized (RustGalTerrainRenderer.class) {
			if (atlasPayload == null || publishedWorldMeshAtlasGeneration == atlasGeneration) {
				return;
			}
			payload = atlasPayload;
			normalPayload = normalAtlasPayload;
			specularPayload = specularAtlasPayload;
			mipPayloads = atlasMipPayloads;
			animation = atlasAnimationSource;
			generation = atlasGeneration;
		}
		texturePayloadUpdates.incrementAndGet();
		texturePayloadUpdateBytes.addAndGet(atlasPayloadByteCount(payload, mipPayloads));
		var texture = new VulkanicGalBridge.WorldMeshTextureAssetRecord(
				RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS,
				payload,
				mipPayloads
			);
		if (animation != null) {
			RustGalWorldPrimitiveRenderer.registerTerrainAtlasAnimation(texture, animation);
		} else {
			RustGalWorldPrimitiveRenderer.registerWorldMeshTexture(texture, "terrain-atlas");
		}
		if (normalPayload != null) {
			RustGalWorldPrimitiveRenderer.registerWorldMeshTexture(
				new VulkanicGalBridge.WorldMeshTextureAssetRecord(
					RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_NORMAL_ATLAS, normalPayload),
				"terrain-normal-atlas");
		}
		if (specularPayload != null) {
			RustGalWorldPrimitiveRenderer.registerWorldMeshTexture(
				new VulkanicGalBridge.WorldMeshTextureAssetRecord(
					RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_SPECULAR_ATLAS, specularPayload),
				"terrain-specular-atlas");
		}
		var registeredTexture = RustGalWorldPrimitiveRenderer.requireRegisteredWorldMeshTexturePayload(texture);
		synchronized (RustGalTerrainRenderer.class) {
			// Publication failure must leave this incarnation retryable.
			if (atlasGeneration == generation) {
				publishedWorldMeshAtlasGeneration = generation;
				publishedWorldMeshAtlasPayload = registeredTexture;
			}
		}
	}

	/** Semantic region in the same copied atlas used by terrain; no per-item image copy. */
	public static AtlasSpritePayload requireGuiAtlasSpritePayload(TextureAtlasSprite sprite) {
		TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
		if (sprite == null || atlas.getSprite(sprite.contents().name()) != sprite) {
			throw new IllegalStateException("GUI sprite does not belong to the current block atlas");
		}
		ensureTerrainAtlasAssetForWorldMesh();
		synchronized (RustGalTerrainRenderer.class) {
			if (publishedWorldMeshAtlasPayload == null || publishedWorldMeshAtlasGeneration != atlasGeneration
				|| copiedAtlasSemanticGeneration != atlas.semanticReloadGeneration()
				|| copiedAtlasSemanticFrameKey != atlas.semanticSnapshotFrameKey()
				|| atlas.getSprite(sprite.contents().name()) != sprite) {
				throw new IllegalStateException("GUI atlas changed during semantic collection");
			}
			return new AtlasSpritePayload(publishedWorldMeshAtlasPayload, atlas.width, atlas.height,
				sprite.getX(), sprite.getY(), sprite.contents().width(), sprite.contents().height());
		}
	}

	/**
	 * Resource reloads temporarily leave already-built GUI render state pointed
	 * at the previous atlas incarnation. That state is not valid semantic input
	 * for the new Rust frame, but it is expected while the reload overlay is
	 * still active. GUI collectors use this bit to reject that one stale item
	 * rather than turning an ordinary reload transition into a client crash.
	 */
	public static boolean isResourceReloadStaging() {
		return resourceReloadStaging && !resourceReloadCommitInProgress;
	}

	/**
	 * The direct CPU source may finish meshing before its copied assets have
	 * crossed the explicit VulkanicGAL upload boundary. A settled whole-frame
	 * capture is valid only once that bounded queue has drained; otherwise the
	 * backend would correctly omit unknown mesh resources from its draw plan.
	 */
	public static boolean areWholeFrameAssetsUploaded() {
		RustGalWorldPrimitiveRenderer.WorldMeshAssetMetrics metrics =
			RustGalWorldPrimitiveRenderer.worldMeshAssetMetrics();
		return metrics.failures() == 0L
			&& metrics.dirtyMeshes() == 0
			&& metrics.dirtyTextures() == 0
			&& metrics.pendingInstances() == 0
			&& metrics.uploadedMeshes() >= metrics.cachedMeshes()
			&& metrics.uploadedTextures() >= metrics.cachedTextures();
	}

	/** Capture diagnostic companion to {@link #areWholeFrameAssetsUploaded()}. */
	public static String wholeFrameAssetUploadSummary() {
		RustGalWorldPrimitiveRenderer.WorldMeshAssetMetrics metrics =
			RustGalWorldPrimitiveRenderer.worldMeshAssetMetrics();
		return "cachedMeshes=" + metrics.cachedMeshes()
			+ ",uploadedMeshes=" + metrics.uploadedMeshes()
			+ ",dirtyMeshes=" + metrics.dirtyMeshes()
			+ ",cachedTextures=" + metrics.cachedTextures()
			+ ",uploadedTextures=" + metrics.uploadedTextures()
			+ ",dirtyTextures=" + metrics.dirtyTextures()
			+ ",pendingInstances=" + metrics.pendingInstances()
			+ ",failures=" + metrics.failures()
			+ ",ready=" + areWholeFrameAssetsUploaded();
	}

	public static void recordExecutedStaticTerrainInstances(
		List<VulkanicGalBridge.WorldMeshInstanceRecord> instances,
		long frameId,
		long submissionId
	) {
		recordExecutedStaticTerrainInstances(instances, 0, frameId, submissionId);
	}

	/**
	 * As above, for a frame whose camera-visible terrain also travelled as
	 * {@code compactSections} compact entries. Outside detailed diagnostics
	 * only the count is kept, so no per-instance identity lookup runs.
	 */
	public static void recordExecutedStaticTerrainInstances(
		List<VulkanicGalBridge.WorldMeshInstanceRecord> instances,
		int compactSections,
		long frameId,
		long submissionId
	) {
		// This receipt describes every completed whole-frame submission, including
		// an empty terrain domain. Retaining the last non-empty value hid real
		// terrain holes during reload and made eventual-recovery tests unable to
		// distinguish continuous presentation from a blank interval.
		if (instances == null) {
			instances = List.of();
		}
		boolean detailedDiagnostics = detailedTerrainDiagnosticsEnabled();
		if (!detailedDiagnostics && !TRANSLUCENT_EXECUTION_METADATA.isEmpty()) {
			// A capture or fault fixture can end between semantic enqueue and its
			// execution receipt. Do not let that diagnostic-only metadata survive
			// and attach to a later capture; ordinary frames otherwise never touch
			// this map.
			TRANSLUCENT_EXECUTION_METADATA.clear();
		}
		long executedStaticTerrainInstances = compactSections;
		if (!detailedDiagnostics) {
			for (VulkanicGalBridge.WorldMeshInstanceRecord instance : instances) {
				if (instance.stratum() == RustGalWorldPrimitiveRenderer.STRATUM_WORLD_TERRAIN) {
					executedStaticTerrainInstances++;
				}
			}
			lastExecutedStaticTerrainFrameId.set(frameId);
			lastExecutedStaticTerrainSubmissionId.set(submissionId);
			lastExecutedStaticTerrainInstances.set(executedStaticTerrainInstances);
			return;
		}
		List<net.sodium.client.render.StaticTerrainParityDiagnostics.RustExecutionIdentity> executionReceipt =
			new ArrayList<>(instances.size());
		for (VulkanicGalBridge.WorldMeshInstanceRecord instance : instances) {
			TerrainAssetIdentity identity = SECTION_ASSETS_BY_MESH_KEY.get(instance.meshKey());
			if (identity == null) {
				continue;
			}
			TerrainSectionAsset asset = identity.asset();
			LayerKey layerKey = identity.layerKey();
			executedStaticTerrainInstances++;
			TranslucentExecutionMetadata submittedMetadata = null;
			TranslucentSortSnapshot sortedIndex = null;
			double cameraX = 0.0D;
			double cameraY = 0.0D;
			double cameraZ = 0.0D;
			if (detailedDiagnostics && layerKey.layer() == ChunkSectionLayer.TRANSLUCENT) {
				submittedMetadata = takeTranslucentExecutionMetadata(instance.meshKey());
				sortedIndex = currentTranslucentSortSnapshot(asset);
				cameraX = currentCameraX();
				cameraY = currentCameraY();
				cameraZ = currentCameraZ();
			}
			// Execution is recorded after command generation. Report the sorted
			// payload currently owned by Rust at that point; an older queued receipt
			// must not masquerade as the active draw after a camera resort.
			long sortGeneration = sortedIndex == null ? 0L : sortedIndex.sortGeneration();
			long sortedIndexHash = sortedIndex == null ? 0L : sortedIndex.indexHash();
			int drawOrder = submittedMetadata != null ? submittedMetadata.drawOrder() : 0;
			if (detailedDiagnostics) net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainExecution(
				"rust-vulkan-executed",
				layerKey.sectionPos(),
				layerKey.layer().name(),
				asset.meshKey(),
				asset.meshGeneration(),
				instance.meshGeneration(),
				asset.contentHash(),
				asset.vertexCount(),
				asset.indexCount(),
				asset.indexType(),
				Math.max(0, asset.indexCount() / 6),
				asset.sectionCount(),
				asset.sectionOriginX(),
				asset.sectionOriginY(),
				asset.sectionOriginZ(),
				asset.localMinX(),
				asset.localMinY(),
				asset.localMinZ(),
				asset.localMaxX(),
				asset.localMaxY(),
				asset.localMaxZ(),
				asset.uvMinU(),
				asset.uvMinV(),
				asset.uvMaxU(),
				asset.uvMaxV(),
				frameId,
				0L
			);
			if (detailedDiagnostics) executionReceipt.add(
				new net.sodium.client.render.StaticTerrainParityDiagnostics.RustExecutionIdentity(
					layerKey.sectionPos(), layerKey.layer().name(), instance.meshGeneration()
				)
			);
			if (detailedDiagnostics) recordEvent(
				layerKey.sectionPos(),
				layerKey.layer(),
				0L,
				asset.meshGeneration(),
				instance.meshGeneration(),
				atlasGeneration,
				asset,
				asset.sectionOriginX(),
				asset.sectionOriginY(),
				asset.sectionOriginZ(),
				instance.transform()[12],
				instance.transform()[13],
				instance.transform()[14],
				"executed-submit",
				0L,
				0L,
				frameId,
				submissionId,
				layerKey.layer() == ChunkSectionLayer.TRANSLUCENT ? sortGeneration : 0L,
				cameraX,
				cameraY,
				cameraZ,
				asset.sectionOriginX() + 8.0D,
				asset.sectionOriginY() + 8.0D,
				asset.sectionOriginZ() + 8.0D,
				layerKey.layer() == ChunkSectionLayer.TRANSLUCENT ? Math.max(0, asset.indexCount() / 6) : 0,
				sortedIndexHash,
				sortGeneration,
				drawOrder
			);
		}
		if (executedStaticTerrainInstances > 0L && detailedDiagnostics) {
				net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustWholeFrameExecutionCoverage(
					executionReceipt, frameId
				);
		}
		lastExecutedStaticTerrainFrameId.set(frameId);
		lastExecutedStaticTerrainSubmissionId.set(submissionId);
		lastExecutedStaticTerrainInstances.set(executedStaticTerrainInstances);
	}

	private static void acceptLayer(ChunkBuildOutput output, TerrainRenderPass pass, ChunkSectionLayer layer, long extractionFrameId,
			TerrainMeshLayout layout) {
		BuiltSectionMeshParts mesh = output.getMesh(pass);
		if (mesh == null || mesh.getVertexData().getLength() == 0) {
			skippedEmptyLayers.incrementAndGet();
			net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainAdmission(
				output, layer.name(), "source-layer-empty"
			);
			removeOrStageEmptyLayer(output.render.getPositionAsLong(), layer, "empty-layer");
			return;
		}
			try {
				TerrainSectionAsset asset = decodeMesh(output, mesh, layer, layout);
				if (asset == null) {
					skippedEmptyLayers.incrementAndGet();
					net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainAdmission(
						output, layer.name(), "source-layer-fully-filtered"
					);
					removeOrStageEmptyLayer(output.render.getPositionAsLong(), layer, "fully-filtered-layer");
					return;
				}
				cacheTextureProbeQuads(output, asset);
				net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainAppearanceCopy(
					output,
					layer.name(),
					asset.meshKey(),
					asset.meshGeneration(),
					asset.asset().vertices() instanceof VulkanicGalBridge.StagedWorldMeshVertices
						? null : asset.asset().vertices()
				);
				if (layer == ChunkSectionLayer.TRANSLUCENT && asset.unsupportedPrimitiveCount() > 0) {
					unsupportedFluidRejectedSections.incrementAndGet();
					throw new IllegalStateException("Rust whole-frame Vulkan encountered translucent fluid metadata without a semantic material route");
				}
				// Publish the section index only after the Rust-owned mesh registry has
				// accepted the complete asset transaction. If registry admission rejects
				// the copied mesh, SECTION_ASSETS must not advertise a drawable section
				// that the Rust frontend cannot consume.
				long atlasGenerationForRegistration = atlasGeneration;
				boolean stagingReload = resourceReloadStaging;
				RustGalWorldPrimitiveRenderer.registerStaticTerrainMeshAsset(
					asset.asset(), asset.stagedMeshKey(), stagingReload ? List.of()
						: atlasTextureUpdatePayload(waterTextureBinding(WorldRenderRoutePolicy.currentStaticTerrainRoute()))
				);
				if (stagingReload) {
					RESOURCE_RELOAD_SECTION_ASSETS.put(
						new LayerKey(output.render.getPositionAsLong(), layer), asset
					);
				} else {
					confirmAtlasPayloadRegistered(atlasGenerationForRegistration);
					publishSectionAsset(new LayerKey(output.render.getPositionAsLong(), layer), asset);
				}
				net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainAdmission(
					output, layer.name(), "asset-registered"
				);
				registeredMeshes.incrementAndGet();
				recordEvent(
					output.render.getPositionAsLong(),
					layer,
					output.submitTime,
					asset.meshGeneration(),
					0L,
					atlasGeneration,
					asset,
					output.render.getOriginX(),
					output.render.getOriginY(),
					output.render.getOriginZ(),
					0.0F,
					0.0F,
					0.0F,
					"mesh-registered",
					extractionFrameId,
					0L,
					0L,
					0L,
						layer == ChunkSectionLayer.TRANSLUCENT ? asset.initialSortGeneration() : 0L,
					currentCameraX(),
					currentCameraY(),
					currentCameraZ(),
					output.render.getOriginX() + 8.0D,
					output.render.getOriginY() + 8.0D,
					output.render.getOriginZ() + 8.0D,
					layer == ChunkSectionLayer.TRANSLUCENT ? Math.max(0, asset.indexCount() / 6) : 0,
						layer == ChunkSectionLayer.TRANSLUCENT ? asset.translucentIndexHash() : 0L,
						layer == ChunkSectionLayer.TRANSLUCENT ? asset.initialSortGeneration() : 0L,
					0
					);
					if (layer == ChunkSectionLayer.TRANSLUCENT) {
						recordInitialTranslucentSort(output, asset);
					}
			} catch (RuntimeException error) {
				LOGGER.warn("Failed to copy Rust static terrain section {} layer {}", output.render.getPosition(), layer, error);
				net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainAdmission(
					output, layer.name(), "asset-decode-failed"
				);
				removeOrStageEmptyLayer(output.render.getPositionAsLong(), layer, "decode-failed");
		}
	}

	private static TerrainSectionAsset decodeMesh(ChunkBuildOutput output, BuiltSectionMeshParts mesh, ChunkSectionLayer layer,
			TerrainMeshLayout layout) {
		ByteBuffer buffer = mesh.getVertexData().getDirectBuffer().duplicate().order(ByteOrder.nativeOrder());
		int vertexStride = layout.vertexStride();
		boolean separateAo = layout.separateAo();
		TextureAtlas copiedAtlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
		int copiedAtlasWidth = copiedAtlas.width;
		int copiedAtlasHeight = copiedAtlas.height;
		if (copiedAtlasWidth <= 0 || copiedAtlasHeight <= 0) {
			throw new IllegalStateException("copied terrain atlas has invalid dimensions " + copiedAtlasWidth + "x" + copiedAtlasHeight);
		}
		String fault = activeFault();
		if (vertexStride < COMPACT_PREFIX_STRIDE) {
			throw new IllegalArgumentException("static terrain vertex stride " + vertexStride + " is smaller than compact prefix " + COMPACT_PREFIX_STRIDE);
		}
		if (buffer.remaining() % vertexStride != 0) {
			throw new IllegalArgumentException("static terrain vertex buffer length is not aligned to stride " + vertexStride);
		}
		int bufferVertexCapacity = buffer.remaining() / vertexStride;
		int shaderBlockIdOffset = layout.shaderBlockIdOffset();
		int midBlockOffset = layout.midBlockOffset();
		int[] vertexSegments = mesh.getVertexSegments();
		if (vertexSegments.length % 2 != 0) {
			throw new IllegalArgumentException("static terrain vertex segment array length is odd");
		}
		int vertexCount = 0;
		for (int i = 0; i < vertexSegments.length; i += 2) {
			int segmentVertexCount = vertexSegments[i];
			if (segmentVertexCount <= 0) {
				continue;
			}
			if (segmentVertexCount % 4 != 0) {
				throw new IllegalArgumentException("static terrain vertex segment is not quad-aligned: " + segmentVertexCount);
			}
			vertexCount += segmentVertexCount;
		}
		if (vertexCount <= 0 || vertexCount > 0xffff) {
			throw new IllegalArgumentException("unsupported static terrain vertex count " + vertexCount);
		}
		if (vertexCount > bufferVertexCapacity) {
			throw new IllegalArgumentException("static terrain vertex segments require " + vertexCount + " vertices but buffer holds " + bufferVertexCapacity);
		}
		// Each vertex record is built once with its final semantics: the
		// canonical primitive identity (previously a second pass), and its
		// segment normal and fault-adjusted color (previously a third pass).
		int[] primitiveMetadata = mesh.getPrimitiveMetadata();
		int metadataStride = NativeSectionMeshBuilder.PRIMITIVE_METADATA_RECORD_INTS;
		if (vertexCount % 4 != 0) {
			throw new IllegalArgumentException("static terrain semantic vertices are not quad-aligned");
		}
		if (primitiveMetadata.length != vertexCount / 4 * metadataStride) {
			throw new IllegalArgumentException("static terrain primitive metadata count " + primitiveMetadata.length
				+ " does not match assembled primitive count " + vertexCount / 4);
		}
		int faultBits = ("inverted-ao".equals(fault) ? RustTerrainIntake.FAULT_INVERTED_AO : 0)
			| ("doubled-face-shade".equals(fault) ? RustTerrainIntake.FAULT_DOUBLED_FACE_SHADE : 0)
			| ("swapped-block-sky-light".equals(fault) ? RustTerrainIntake.FAULT_SWAPPED_BLOCK_SKY_LIGHT : 0)
			| ("inverted-normal".equals(fault) ? RustTerrainIntake.FAULT_INVERTED_NORMAL : 0)
			| ("wrong-top-face-shade".equals(fault) ? RustTerrainIntake.FAULT_WRONG_TOP_FACE_SHADE : 0);
		// Rust decodes and assembles the layer (worldrender/terrain/assembly.rs):
		// vertices, index bytes, draw ranges, translucent order, key, generation.
		long sectionPos = output.render.getPositionAsLong();
		RustTerrainIntake.AssembledLayer assembled = RustTerrainIntake.assemble(buffer, vertexStride, separateAo,
			copiedAtlasWidth, copiedAtlasHeight, midBlockOffset, faultBits, vertexSegments, primitiveMetadata,
			metadataStride, vertexCount, layer == ChunkSectionLayer.TRANSLUCENT ? sorterIndexBuffer(output.getSorter()) : null,
			sectionPos, layer.ordinal(), atlasGeneration,
			waterTextureBinding(WorldRenderRoutePolicy.currentStaticTerrainRoute()) == WaterTextureBinding.BLOCK_ATLAS,
			waterSprites(), !vertexCopyRequired(fault));
		RustTerrainIntake.DecodedVertices decoded = assembled.decoded();
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = decoded.encoded();
		float minX = decoded.minX();
		float minY = decoded.minY();
		float minZ = decoded.minZ();
		float maxX = decoded.maxX();
		float maxY = decoded.maxY();
		float maxZ = decoded.maxZ();
		float minU = decoded.minU();
		float minV = decoded.minV();
		float maxU = decoded.maxU();
		float maxV = decoded.maxV();
		int separateAoVertexCount = decoded.separateAoVertices();
		float minAo = decoded.minAo();
		float maxAo = decoded.maxAo();
		boolean aoContractValid = decoded.aoContractValid();
		boolean blockSkyLightContractValid = !"swapped-block-sky-light".equals(fault);
		boolean normalContractValid = !"inverted-normal".equals(fault) || vertexSegments.length == 0;
		boolean topFaceShadeContractValid = !("wrong-top-face-shade".equals(fault) && assembled.positiveYSections() > 0);
		int positiveYNormalSections = assembled.positiveYSections();
		int negativeYNormalSections = assembled.negativeYSections();
		int horizontalNormalSections = assembled.horizontalSections();
		int maxIndex = assembled.maxIndex();
		byte[] indexBytes = assembled.indexBytes();
		List<VulkanicGalBridge.WorldMeshSectionRecord> sections = assembled.sections();
		int[] translucentSourceSegmentQuadCounts = layer == ChunkSectionLayer.TRANSLUCENT
			? translucentSourceSegmentQuadCounts(vertexSegments)
			: new int[0];
		OrderedTranslucentMesh orderedTranslucentMesh = null;
		if (layer == ChunkSectionLayer.TRANSLUCENT) {
			if (sections.isEmpty()) {
				// Nothing to register: drop what assembly staged.
				RustTerrainIntake.discardStaged(assembled.meshKey(), assembled.meshGeneration());
				return null;
			}
			orderedTranslucentMesh = orderedTranslucentReceipt(assembled);
		}
		long meshKey = assembled.meshKey();
		long generation = assembled.meshGeneration();
		int diagnosticVertexCount = vertexCount;
		int diagnosticVertexStride = vertexStride;
		int diagnosticMaxIndex = maxIndex;
		int diagnosticIndexType = layer == ChunkSectionLayer.TRANSLUCENT ? INDEX_TYPE_U32 : INDEX_TYPE_U16;
		float diagnosticMaxX = maxX;
		boolean vertexPositionsFinite = finiteBounds(minX, minY, minZ, maxX, maxY, maxZ);
		boolean localBoundsValid = vertexPositionsFinite && minX >= SECTION_LOCAL_MIN && minY >= SECTION_LOCAL_MIN && minZ >= SECTION_LOCAL_MIN && maxX <= SECTION_LOCAL_MAX && maxY <= SECTION_LOCAL_MAX && maxZ <= SECTION_LOCAL_MAX;
		boolean indexRangeValid = maxIndex >= 0 && maxIndex < vertexCount;
		boolean sectionOriginValid = true;
		boolean indexOffsetAlignmentValid = true;
		switch (activeFault()) {
			case "old-stride", "incorrect-vertex-stride" -> diagnosticVertexStride = COMPACT_PREFIX_STRIDE;
			case "vertex-count-exceeds-capacity" -> diagnosticVertexCount = bufferVertexCapacity + 1;
			case "out-of-range-index" -> {
				diagnosticMaxIndex = vertexCount + 7;
				indexRangeValid = false;
			}
			case "index-type-invalid", "incorrect-index-type" -> diagnosticIndexType = 99;
			case "index-alignment-invalid", "incorrect-index-alignment" -> indexOffsetAlignmentValid = false;
			case "non-finite-position" -> {
				vertexPositionsFinite = false;
				localBoundsValid = false;
			}
			case "section-origin-offset", "replacement-previous-origin" -> sectionOriginValid = false;
			case "mesh-key-collision" -> meshKey = 0x5a17_5e77_a14c_0111L;
			case "bounds-out-of-range" -> {
				diagnosticMaxX = 4096.0F;
				localBoundsValid = false;
			}
			default -> {
			}
		}
			long initialSortGeneration = layer == ChunkSectionLayer.TRANSLUCENT ? translucentSortGenerations.incrementAndGet() : 0L;
		return new TerrainSectionAsset(
				meshKey,
				generation,
				generation,
				initialSortGeneration,
				diagnosticVertexCount,
			bufferVertexCapacity,
			diagnosticVertexStride,
			layer == ChunkSectionLayer.TRANSLUCENT ? indexBytes.length / Integer.BYTES : assembled.assembledIndexCount(),
			diagnosticMaxIndex,
			diagnosticIndexType,
			sections.size(),
			minX,
			minY,
			minZ,
			diagnosticMaxX,
			maxY,
			maxZ,
			minU,
			minV,
			maxU,
			maxV,
			vertexPositionsFinite,
			localBoundsValid,
			finiteBounds(minU, minV, 0.0F, maxU, maxV, 0.0F) && minU >= -0.01F && minV >= -0.01F && maxU <= 1.01F && maxV <= 1.01F,
			indexRangeValid,
			true,
			sectionOriginValid,
			indexOffsetAlignmentValid,
			normalContractValid,
			aoContractValid,
			blockSkyLightContractValid,
			topFaceShadeContractValid,
			separateAo,
			separateAoVertexCount,
			minAo,
			maxAo,
			positiveYNormalSections,
			negativeYNormalSections,
			horizontalNormalSections,
			output.render.getOriginX(),
			output.render.getOriginY(),
			output.render.getOriginZ(),
			layer == ChunkSectionLayer.TRANSLUCENT && output.translucentData != null
				? output.translucentData.getSortType() : SortType.NONE,
			orderedTranslucentMesh == null ? "" : orderedTranslucentMesh.accountingReason(),
			orderedTranslucentMesh == null ? 0 : orderedTranslucentMesh.unsupportedPrimitiveCount(),
			translucentSourceSegmentQuadCounts,
			layer == ChunkSectionLayer.TRANSLUCENT ? indexBytes.length : 0,
			layer == ChunkSectionLayer.TRANSLUCENT ? assembled.retainedHash() : 0L,
			new VulkanicGalBridge.WorldMeshAssetRecord(
				meshKey,
				generation,
				RustGalWorldPrimitiveRenderer.MESH_VERTEX_LAYOUT_V3,
				layer == ChunkSectionLayer.TRANSLUCENT ? INDEX_TYPE_U32 : INDEX_TYPE_U16,
				vertices,
				indexBytes,
				sections
			),
			assembled.meshKey()
		);
	}

	static void installTestingFluidSpriteAssetsForUnitTests() {
		waterStillAsset = new FluidSpriteAsset(
			RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_STILL,
			ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still"),
			0.0F,
			0.25F,
			0.0F,
			0.25F,
			16,
			16,
			1,
			1,
			0,
			0,
			0,
			List.of(),
			new byte[] { 1 }, 1
		);
		waterFlowAsset = new FluidSpriteAsset(
			RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_FLOW,
			ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_flow"),
			0.25F,
			0.5F,
			0.0F,
			0.25F,
			16,
			16,
			1,
			1,
			0,
			0,
			0,
			List.of(),
			new byte[] { 2 }, 1
		);
		waterOverlayAsset = new FluidSpriteAsset(
			RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_OVERLAY,
			ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_overlay"),
			0.5F,
			0.75F,
			0.0F,
			0.25F,
			16,
			16,
			1,
			1,
			0,
			0,
			0,
			List.of(),
			new byte[] { 3 }, 1
		);
	}

	/**
	 * Diagnostics that read a layer's vertices in Java after assembly. Without
	 * them the vertices stay staged in Rust and only their count reaches Java.
	 */
	private static boolean vertexCopyRequired(String fault) {
		boolean vertexReaders = !fault.isEmpty() || !activeTextureProbes.isEmpty()
			|| net.sodium.client.render.StaticTerrainParityDiagnostics.appearanceTraceActive();
		// Captures run with detailed diagnostics; this lets them verify staging.
		if (FORCE_VERTEX_STAGING) {
			return vertexReaders;
		}
		return vertexReaders || detailedTerrainDiagnosticsEnabled(fault);
	}

	private static final boolean FORCE_VERTEX_STAGING = Boolean.getBoolean("mattmc.dev.forceTerrainVertexStaging");

	/** The build sorter's u32 quad order, read in place (null when there is none). */
	private static ByteBuffer sorterIndexBuffer(Sorter sorter) {
		if (sorter == null) {
			return null;
		}
		NativeBuffer indexBuffer = sorter.getIndexBuffer();
		if (indexBuffer == null || indexBuffer.getLength() <= 0) {
			return null;
		}
		ByteBuffer src = indexBuffer.getDirectBuffer().duplicate();
		src.clear();
		src.limit(indexBuffer.getLength());
		return src;
	}

	/** Still, flow and overlay water sprites for Rust assembly; null until initialized. */
	private static RustTerrainIntake.WaterSprite[] waterSprites() {
		FluidSpriteAsset still = waterStillAsset;
		FluidSpriteAsset flow = waterFlowAsset;
		FluidSpriteAsset overlay = waterOverlayAsset;
		if (still == null || flow == null || overlay == null) {
			return null;
		}
		return new RustTerrainIntake.WaterSprite[] {
			new RustTerrainIntake.WaterSprite(still.textureId(), still.u0(), still.u1(), still.v0(), still.v1()),
			new RustTerrainIntake.WaterSprite(flow.textureId(), flow.u0(), flow.u1(), flow.v0(), flow.v1()),
			new RustTerrainIntake.WaterSprite(overlay.textureId(), overlay.u0(), overlay.u1(), overlay.v0(), overlay.v1())
		};
	}

	/** The translucent accounting receipt, as Java's ordered-mesh record. */
	private static OrderedTranslucentMesh orderedTranslucentReceipt(RustTerrainIntake.AssembledLayer assembled) {
		StringBuilder primitiveSample = new StringBuilder();
		int[] samples = assembled.samples();
		for (int offset = 0; offset + 5 < samples.length; offset += 6) {
			appendPrimitiveSample(primitiveSample, samples[offset], samples[offset + 1],
				samples[offset + 2] != 0 ? "retained" : "omitted", samples[offset + 3], samples[offset + 4],
				samples[offset + 5]);
		}
		return new OrderedTranslucentMesh(
			assembled.indexBytes(),
			assembled.sections(),
			assembled.sourcePrimitives(),
			assembled.nonFluidPrimitives(),
			assembled.waterPrimitives(),
			assembled.unsupportedPrimitives(),
			assembled.retainedPrimitives(),
			assembled.omittedPrimitives(),
			assembled.sourceIndices(),
			assembled.retainedIndices(),
			assembled.omittedIndices(),
			assembled.sourceHash(),
			assembled.retainedHash(),
			assembled.omittedHash(),
			assembled.materialSwitches(),
			assembled.waterStill(),
			assembled.waterFlow(),
			assembled.waterOverlay(),
			assembled.waterTextureSwitches(),
			waterAnimationHash(),
			waterAnimationSummary(),
			translucentRangeSummary(assembled.sections()),
			primitiveSample.toString()
		);
	}


	/**
	 * Restores terrain shader semantics from the native builder's per-quad
	 * metadata when the ordinary compact vertex format deliberately omits Iris
	 * extension fields. Metadata is copied in the same assembled quad order as
	 * vertex data, so this is a semantic mesh boundary rather than a read of
	 * renderer or GPU state.
	 */
	static void applyPrimitiveSemanticFallback(
		int[] primitiveMetadata,
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices,
		int vertexCount,
		boolean hasPackedShaderBlock,
		boolean hasPackedMidBlock
	) {
		if (vertexCount < 0 || vertexCount % 4 != 0 || vertices.size() != vertexCount) {
			throw new IllegalArgumentException("static terrain semantic vertices are not quad-aligned");
		}
		int metadataStride = NativeSectionMeshBuilder.PRIMITIVE_METADATA_RECORD_INTS;
		int primitiveCount = vertexCount / 4;
		if (primitiveMetadata.length != primitiveCount * metadataStride) {
			throw new IllegalArgumentException("static terrain primitive metadata count " + primitiveMetadata.length
				+ " does not match assembled primitive count " + primitiveCount);
		}
		for (int primitive = 0; primitive < primitiveCount; primitive++) {
			int metadataOffset = primitive * metadataStride;
			int blockId = primitiveMetadata[metadataOffset + 2];
			int localX = primitiveMetadata[metadataOffset + 3];
			int localY = primitiveMetadata[metadataOffset + 4];
			int localZ = primitiveMetadata[metadataOffset + 5];
			int renderType = primitiveMetadata[metadataOffset + 6];
			int blockEmission = primitiveMetadata[metadataOffset + 9];
			// Native render-pass IDs have more states than the single material bit
			// that the semantic terrain ABI carries. This must be derived from the
			// copied primitive metadata, never from an optional Iris extension in
			// the source vertex stream: that packed value is renderer-private and
			// can encode additional non-semantic render types.
			int shaderMaterialType = renderType & 1;
			if (blockEmission < 0 || blockEmission > 0xff) {
				throw new IllegalArgumentException("static terrain primitive " + primitive
					+ " has invalid semantic block emission " + blockEmission);
			}
			for (int vertexOffset = 0; vertexOffset < 4; vertexOffset++) {
				int vertexIndex = primitive * 4 + vertexOffset;
				VulkanicGalBridge.WorldMeshVertexRecord original = vertices.get(vertexIndex);
				if (blockId < 0) {
					throw new IllegalArgumentException("static terrain primitive " + primitive
						+ " lacks a canonical native block-state identity");
				}
				// A packed extension field may be present when Iris chose a wider
				// vertex layout, but that value is an Iris-private shader mapping.
				// The native primitive metadata carries the canonical raw block-state
				// ID that Rust-owned shader-pack resources resolve semantically.
				int shaderBlockId = blockId;
				int resolvedShaderMaterialType = shaderMaterialType;
				int midBlockPacked = hasPackedMidBlock
					? original.midBlockPacked()
					: semanticMidBlockPacked(original.x(), original.y(), original.z(), localX, localY, localZ, blockEmission);
				vertices.set(vertexIndex, new VulkanicGalBridge.WorldMeshVertexRecord(
					original.x(), original.y(), original.z(), original.u(), original.v(), original.atlasU(), original.atlasV(),
					shaderBlockId, resolvedShaderMaterialType, original.terrainMaterialBits(), original.colorArgb(), original.normalPacked(), original.light(), midBlockPacked
				));
			}
		}
	}

	private static int semanticMidBlockPacked(float vertexX, float vertexY, float vertexZ,
		int localX, int localY, int localZ, int blockEmission) {
		int x = ((int)((localX + 0.5F - vertexX) * 64.0F)) & 0xff;
		int y = ((int)((localY + 0.5F - vertexY) * 64.0F)) & 0xff;
		int z = ((int)((localZ + 0.5F - vertexZ) * 64.0F)) & 0xff;
		return x | (y << 8) | (z << 16) | (blockEmission << 24);
	}

	record OrderedTranslucentMesh(
		byte[] indexBytes,
		List<VulkanicGalBridge.WorldMeshSectionRecord> sections,
		int sourcePrimitiveCount,
		int nonFluidPrimitiveCount,
		int waterPrimitiveCount,
		int unsupportedPrimitiveCount,
		int retainedPrimitiveCount,
		int omittedPrimitiveCount,
		int sourceIndexCount,
		int retainedIndexCount,
		int omittedIndexCount,
		long sourceIndexHash,
		long retainedIndexHash,
		long omittedIndexHash,
		int materialSwitchCount,
		int waterStillPrimitiveCount,
		int waterFlowPrimitiveCount,
		int waterOverlayPrimitiveCount,
		int waterTextureSwitchCount,
		long waterAnimationHash,
		String waterAnimationSummary,
		String rangeSummary,
		String primitiveSample
	) {
		String accountingReason() {
			return "translucent-primitive-accounting"
				+ ":sourcePrimitives=" + sourcePrimitiveCount
				+ ":nonFluidPrimitives=" + nonFluidPrimitiveCount
				+ ":waterPrimitives=" + waterPrimitiveCount
				+ ":unsupportedPrimitives=" + unsupportedPrimitiveCount
				+ ":retainedPrimitives=" + retainedPrimitiveCount
				+ ":omittedPrimitives=" + omittedPrimitiveCount
				+ ":executedPrimitives=" + retainedPrimitiveCount
				+ ":sourceIndices=" + sourceIndexCount
				+ ":retainedIndices=" + retainedIndexCount
				+ ":omittedIndices=" + omittedIndexCount
				+ ":sourceHash=" + Long.toUnsignedString(sourceIndexHash)
				+ ":retainedHash=" + Long.toUnsignedString(retainedIndexHash)
				+ ":omittedHash=" + Long.toUnsignedString(omittedIndexHash)
				+ ":rangeCount=" + sections.size()
				+ ":materialSwitches=" + materialSwitchCount
				+ ":waterStillPrimitives=" + waterStillPrimitiveCount
				+ ":waterFlowPrimitives=" + waterFlowPrimitiveCount
				+ ":waterOverlayPrimitives=" + waterOverlayPrimitiveCount
				+ ":waterTextureSwitches=" + waterTextureSwitchCount
				+ ":waterAnimationHash=" + Long.toUnsignedString(waterAnimationHash)
				+ ":waterAnimationEntries=" + waterAnimationSummary
				+ ":ranges=" + rangeSummary
				+ ":sample=" + primitiveSample;
		}
	}

	/** Resource binding only: animation clocks and uploads remain owned by Rust. */
	enum WaterTextureBinding { BLOCK_ATLAS, SEPARATE_SHEETS }

	static WaterTextureBinding waterTextureBinding(WorldRenderRoutePolicy.Route route) {
		// The whole-frame route publishes the owned block atlas unconditionally.
		// Water must consume that same resource, with its declared mip chain and
		// visibility-driven animation, rather than an independently animated sheet.
		return route.usesRustWholeFrameVulkan()
			? WaterTextureBinding.BLOCK_ATLAS : WaterTextureBinding.SEPARATE_SHEETS;
	}



	private static void appendPrimitiveSample(StringBuilder sample, int primitiveId, int primitiveKind, String fate,
			int materialId, int textureId, int retainedIndexStart) {
		if (sample.length() > 512) {
			return;
		}
		if (sample.length() > 0) {
			sample.append(',');
		}
		sample.append(primitiveId)
			.append('/')
			.append(primitiveKind)
			.append('/')
			.append(fate)
			.append('/')
			.append(materialId)
			.append('/')
			.append(textureId)
			.append('/')
			.append(retainedIndexStart);
	}

	private static long waterAnimationHash() {
		long hash = 0xcbf29ce484222325L;
		FluidSpriteAsset still = waterStillAsset;
		FluidSpriteAsset flow = waterFlowAsset;
		FluidSpriteAsset overlay = waterOverlayAsset;
		if (still != null) {
			hash = fnv64Long(hash, still.animationHash());
		}
		if (flow != null) {
			hash = fnv64Long(hash, flow.animationHash());
		}
		if (overlay != null) {
			hash = fnv64Long(hash, overlay.animationHash());
		}
		return hash;
	}

	public static long waterAnimationHashForDiagnostics() {
		return waterAnimationHash();
	}

	private static String waterAnimationSummary() {
		FluidSpriteAsset still = waterStillAsset;
		FluidSpriteAsset flow = waterFlowAsset;
		FluidSpriteAsset overlay = waterOverlayAsset;
		if (still == null || flow == null || overlay == null) {
			return "missing";
		}
		return still.animationSummary() + "|" + flow.animationSummary() + "|" + overlay.animationSummary();
	}

	public static String waterAnimationSummaryForDiagnostics() {
		return waterAnimationSummary();
	}

	public static String waterAnimationFrameStateForDiagnostics(long frameTick) {
		return waterAnimationState("still", waterStillAsset, frameTick)
			+ "|" + waterAnimationState("flow", waterFlowAsset, frameTick)
			+ "|" + waterAnimationState("overlay", waterOverlayAsset, frameTick);
	}

	private static String waterAnimationState(String name, FluidSpriteAsset asset, long frameTick) {
		if (asset == null) {
			return name + "=missing";
		}
		List<VulkanicGalBridge.WorldMeshAnimationFrameRecord> frames = asset.animationFrames();
		if (frames.isEmpty()) {
			return name
				+ "=tex:" + asset.textureId()
				+ ",loc:" + asset.location().toString().replace(':', '~')
				+ ",generation:" + atlasGeneration
				+ ",current:0,next:0,elapsed:0,duration:1,fraction:0.000000,interpolation:" + asset.interpolationPolicy();
		}
		long totalDuration = 0L;
		for (VulkanicGalBridge.WorldMeshAnimationFrameRecord frame : frames) {
			totalDuration += Math.max(1, frame.durationTicks());
		}
		long cursor = Math.floorMod(frameTick, Math.max(1L, totalDuration));
		long elapsed = 0L;
		int frameListIndex = 0;
		for (int index = 0; index < frames.size(); index++) {
			long duration = Math.max(1, frames.get(index).durationTicks());
			if (cursor < elapsed + duration) {
				frameListIndex = index;
				break;
			}
			elapsed += duration;
		}
		VulkanicGalBridge.WorldMeshAnimationFrameRecord current = frames.get(frameListIndex);
		VulkanicGalBridge.WorldMeshAnimationFrameRecord next = frames.get((frameListIndex + 1) % frames.size());
		long duration = Math.max(1L, current.durationTicks());
		double fraction = (double)(cursor - elapsed) / (double)duration;
		return name
			+ "=tex:" + asset.textureId()
			+ ",loc:" + asset.location().toString().replace(':', '~')
			+ ",generation:" + atlasGeneration
			+ ",current:" + current.frameIndex()
			+ ",next:" + next.frameIndex()
			+ ",elapsed:" + (cursor - elapsed)
			+ ",duration:" + duration
			+ ",fraction:" + String.format(Locale.ROOT, "%.6f", fraction)
			+ ",interpolation:" + asset.interpolationPolicy();
	}

	private static String translucentRangeSummary(List<VulkanicGalBridge.WorldMeshSectionRecord> sections) {
		StringBuilder summary = new StringBuilder();
		for (int i = 0; i < sections.size() && i < 16; i++) {
			if (i > 0) {
				summary.append(',');
			}
			VulkanicGalBridge.WorldMeshSectionRecord section = sections.get(i);
			summary.append(section.materialId())
				.append('@')
				.append(section.indexOffset())
				.append('+')
				.append(section.indexCount());
		}
		if (sections.size() > 16) {
			summary.append(",...");
		}
		return summary.toString();
	}











	private static int[] translucentSourceSegmentQuadCounts(int[] vertexSegments) {
		int[] counts = new int[vertexSegments.length / 2];
		int count = 0;
		for (int index = 0; index < vertexSegments.length; index += 2) {
			int vertices = vertexSegments[index];
			if (vertices < 0 || vertices % 4 != 0) {
				throw new IllegalArgumentException("translucent source vertex segment is not quad-aligned");
			}
			if (vertices > 0) {
				counts[count++] = vertices / 4;
			}
		}
		return Arrays.copyOf(counts, count);
	}



	private static void appendCachedOpaqueMeshKey(TerrainSetScratch scratch, long meshKey, long meshGeneration) {
		if (scratch.cachedOpaqueMeshKeyCount == scratch.cachedOpaqueMeshKeyArray.length) {
			scratch.cachedOpaqueMeshKeyArray = Arrays.copyOf(
				scratch.cachedOpaqueMeshKeyArray,
				scratch.cachedOpaqueMeshKeyArray.length * 2
			);
			scratch.cachedOpaqueMeshGenerationArray = Arrays.copyOf(
				scratch.cachedOpaqueMeshGenerationArray,
				scratch.cachedOpaqueMeshGenerationArray.length * 2
			);
		}
		int index = scratch.cachedOpaqueMeshKeyCount++;
		scratch.cachedOpaqueMeshKeyArray[index] = meshKey;
		scratch.cachedOpaqueMeshGenerationArray[index] = meshGeneration;
	}

	/**
	 * Records the semantic receipt for a cached immutable terrain instance after
	 * the batch producer has admitted it. No resource or backend state is
	 * recreated here; the active record is the same one consumed by the normal
	 * frame path.
	 */
	private static void recordCachedOpaqueLayerSubmission(RenderSection section,
		ChunkSectionLayer layer, TerrainSectionAsset asset, LongOpenHashSet visibleSubmissions,
		boolean trackTerrainCounters) {
		if (asset == null || !visibleSubmissions.add(asset.meshKey())) {
			return;
		}
		if (trackTerrainCounters) {
			long enqueueFrameId = rustEnqueueFrames.incrementAndGet();
			visibleLayerSubmissions.incrementAndGet();
			recordCurrentFrameVisibleSubmission(enqueueFrameId, section.getPositionAsLong(), layer,
				asset.meshKey(), asset.meshGeneration());
			recordVisibleSubmissionIdentity(section.getPositionAsLong(), layer, asset.meshGeneration());
		}
		var animatedSprites = section.getAnimatedSprites();
		if (animatedSprites != null) {
			for (var sprite : animatedSprites) {
				RustGalWorldPrimitiveRenderer.recordAtlasSpriteUse(
					sprite.semanticAnimationResource(), sprite.atlasLocation(), sprite.contents().name());
			}
		}
	}

	private static boolean enqueueSectionLayer(RenderSection section, ChunkSectionLayer layer, Camera camera,
			int viewportWidth, int viewportHeight, int drawOrder, LongOpenHashSet visibleSubmissions,
			String fault, boolean detailedDiagnostics, boolean trackTerrainCounters) {
		long sectionPos = section.getPositionAsLong();
		TerrainSectionAsset asset = sectionAsset(sectionPos, layer);
		return enqueueSectionLayer(section, layer, asset, camera, viewportWidth, viewportHeight,
			drawOrder, visibleSubmissions, fault, detailedDiagnostics, trackTerrainCounters);
	}

	/** Uses the immutable asset resolved while building the same frame's visibility key set. */
	private static boolean enqueueSectionLayer(RenderSection section, ChunkSectionLayer layer,
			TerrainSectionAsset asset, Camera camera, int viewportWidth, int viewportHeight, int drawOrder,
			LongOpenHashSet visibleSubmissions, String fault, boolean detailedDiagnostics,
			boolean trackTerrainCounters) {
		if (trackTerrainCounters) {
			visibleLayerProbes.incrementAndGet();
		}
		long sectionPos = section.getPositionAsLong();
		if (asset == null) {
			if (detailedDiagnostics) {
			net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainNonExecution(
				sectionPos,
				layer.name(),
				"asset-missing:" + net.sodium.client.render.StaticTerrainParityDiagnostics
					.rustStaticTerrainAdmissionReason(sectionPos, layer.name()),
				0L,
				0L
			);
			}
			return false;
		}
		long visibleGeneration = "stale-generation".equals(fault) ? asset.meshGeneration() + 1L : asset.meshGeneration();
		TranslucentSortSnapshot sortedIndex = detailedDiagnostics && layer == ChunkSectionLayer.TRANSLUCENT
			? currentTranslucentSortSnapshot(asset) : null;
		long sortGeneration = sortedIndex == null ? 0L : sortedIndex.sortGeneration();
		long sortedIndexHash = sortedIndex == null ? 0L : sortedIndex.indexHash();
		// Queue the visible semantic instance even when this generation is still
		// awaiting its bounded Rust asset upload. The frame coordinator publishes
		// resources before consumeFrame(), which then admits only an acknowledged
		// generation. Refusing to queue here left the old active generation behind;
		// the upload changed the acknowledgement to the replacement generation and
		// consumeFrame() rejected the old instance, creating a one-frame terrain
		// hole. Queuing first makes upload plus active-instance replacement atomic.
		if (visibleSubmissions != null && !"duplicate-visible-section".equals(fault)) {
			if (!visibleSubmissions.add(asset.meshKey())) {
				if (detailedDiagnostics) {
				net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainNonExecution(
					sectionPos, layer.name(), "duplicate-visible-suppressed", visibleGeneration, 0L
				);
					recordEvent(
					sectionPos,
					layer,
					0L,
					asset.meshGeneration(),
					visibleGeneration,
					atlasGeneration,
					asset,
					section.getOriginX(),
					section.getOriginY(),
					section.getOriginZ(),
					(float)(section.getOriginX() - camera.getPosition().x()),
					(float)(section.getOriginY() - camera.getPosition().y()),
					(float)(section.getOriginZ() - camera.getPosition().z()),
					"duplicate-visible-suppressed",
					0L,
					0L,
					0L,
					0L,
					sortGeneration,
					camera.getPosition().x(),
					camera.getPosition().y(),
					camera.getPosition().z(),
					section.getOriginX() + 8.0D,
					section.getOriginY() + 8.0D,
					section.getOriginZ() + 8.0D,
					Math.max(0, asset.indexCount() / 6),
					sortedIndexHash,
					sortGeneration,
						drawOrder
					);
				}
					return false;
			}
		}
		boolean submitted = RustGalWorldPrimitiveRenderer.enqueueStaticTerrainSectionInstance(
			asset.meshKey(),
			visibleGeneration,
			section.getOriginX(), section.getOriginY(), section.getOriginZ(),
			camera.getPosition().x(), camera.getPosition().y(), camera.getPosition().z(),
			viewportWidth,
			viewportHeight,
			terrainDepthPolicy(layer),
			// Match Sodium's terrain pipeline contract for every layer. The
			// copied mesh preserves its authored winding, so this remains an
			// explicit semantic request rather than an OpenGL-state fallback.
			RustGalWorldPrimitiveRenderer.CULL_BACK,
			terrainCameraSortRequested(layer, asset.translucentSortType())
		);
		long enqueueFrameId = trackTerrainCounters ? rustEnqueueFrames.incrementAndGet() : 0L;
		if (submitted) {
			if (trackTerrainCounters) {
				visibleLayerSubmissions.incrementAndGet();
			}
			// This section's accepted semantic draw supplies CPU resource names,
			// never Sodium render-list storage or its mutable sprite-active flags.
			var animatedSprites = section.getAnimatedSprites();
			if (animatedSprites != null) {
				for (var sprite : animatedSprites) {
					RustGalWorldPrimitiveRenderer.recordAtlasSpriteUse(
						sprite.semanticAnimationResource(), sprite.atlasLocation(), sprite.contents().name());
				}
			}
			if (trackTerrainCounters) {
				recordCurrentFrameVisibleSubmission(enqueueFrameId, sectionPos, layer,
					asset.meshKey(), visibleGeneration);
				recordVisibleSubmissionIdentity(sectionPos, layer, visibleGeneration);
			}
			if (detailedDiagnostics && layer == ChunkSectionLayer.TRANSLUCENT) {
				retainTranslucentExecutionMetadata(asset.meshKey(), new TranslucentExecutionMetadata(
						sortGeneration,
						sortedIndexHash,
						camera.getPosition().x(),
						camera.getPosition().y(),
						camera.getPosition().z(),
						drawOrder
				));
			}
			if (detailedDiagnostics) net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainExecution(
				"rust-vulkan-enqueued",
				sectionPos,
				layer.name(),
				asset.meshKey(),
				asset.meshGeneration(),
				visibleGeneration,
				asset.contentHash(),
				asset.vertexCount(),
				asset.indexCount(),
				asset.indexType(),
				Math.max(0, asset.indexCount() / 6),
				asset.sectionCount(),
				asset.sectionOriginX(),
				asset.sectionOriginY(),
				asset.sectionOriginZ(),
				asset.localMinX(),
				asset.localMinY(),
				asset.localMinZ(),
				asset.localMaxX(),
				asset.localMaxY(),
				asset.localMaxZ(),
				asset.uvMinU(),
				asset.uvMinV(),
				asset.uvMaxU(),
				asset.uvMaxV(),
				currentGameplayFrameId(),
				enqueueFrameId
			);
			if (detailedDiagnostics) {
			recordEvent(
				sectionPos,
				layer,
				0L,
				asset.meshGeneration(),
				visibleGeneration,
				atlasGeneration,
				asset,
				section.getOriginX(),
				section.getOriginY(),
				section.getOriginZ(),
				(float)(section.getOriginX() - camera.getPosition().x()),
				(float)(section.getOriginY() - camera.getPosition().y()),
				(float)(section.getOriginZ() - camera.getPosition().z()),
				"visible-submit",
				0L,
				enqueueFrameId,
				0L,
				0L,
				layer == ChunkSectionLayer.TRANSLUCENT ? sortGeneration : 0L,
				camera.getPosition().x(),
				camera.getPosition().y(),
				camera.getPosition().z(),
				section.getOriginX() + 8.0D,
				section.getOriginY() + 8.0D,
				section.getOriginZ() + 8.0D,
				Math.max(0, asset.indexCount() / 6),
				sortedIndexHash,
				sortGeneration,
				drawOrder
			);
			}
			if (detailedDiagnostics && "duplicate-visible-section".equals(fault)) {
				recordEvent(
					sectionPos,
					layer,
					0L,
					asset.meshGeneration(),
					visibleGeneration,
					atlasGeneration,
					asset,
					section.getOriginX(),
					section.getOriginY(),
					section.getOriginZ(),
					(float)(section.getOriginX() - camera.getPosition().x()),
					(float)(section.getOriginY() - camera.getPosition().y()),
					(float)(section.getOriginZ() - camera.getPosition().z()),
					"visible-submit",
					0L,
					enqueueFrameId,
					0L,
					0L
				);
			}
		} else {
			// Registration can be retired concurrently with the visible-list walk.
			// A missing generation here is ordinary retirement latency rather than a
			// bad semantic submission. Pending uploads normally enqueue successfully
			// above and become drawable after the coordinator's pre-consume flush.
			if (!"stale-generation".equals(fault)
					&& !RustGalWorldPrimitiveRenderer.isStaticTerrainMeshGenerationUploaded(asset.meshKey(), asset.meshGeneration())) {
				if (detailedDiagnostics) {
				net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainNonExecution(
					sectionPos, layer.name(), "asset-upload-pending", asset.meshGeneration(), 0L
				);
				}
				return false;
			}
			failedLayerSubmissions.incrementAndGet();
			if (detailedDiagnostics) {
			net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainNonExecution(
				sectionPos, layer.name(), "stale-or-unregistered-submit", visibleGeneration, enqueueFrameId
			);
			recordEvent(sectionPos, layer, 0L, asset.meshGeneration(), visibleGeneration, atlasGeneration, asset, section.getOriginX(), section.getOriginY(), section.getOriginZ(), 0.0F, 0.0F, 0.0F, "stale-or-unregistered-submit", 0L, enqueueFrameId, 0L, 0L);
			}
		}
		return submitted;
	}

	/**
	 * Builds the column-major affine translation used by the semantic terrain
	 * instance ABI without constructing a temporary JOML matrix. The returned
	 * array is intentionally fresh because the queued semantic record retains
	 * it until the frame coordinator consumes the exact frame snapshot.
	 */
	private static float[] cameraRelativeTranslationTransform(float x, float y, float z) {
		return new float[] {
			1.0F, 0.0F, 0.0F, 0.0F,
			0.0F, 1.0F, 0.0F, 0.0F,
			0.0F, 0.0F, 1.0F, 0.0F,
			x, y, z, 1.0F
		};
	}

	/**
	 * The deterministic capture gate settles on the exact semantic work that
	 * reached the Rust route. A global mesh-cache counter is not a valid proxy:
	 * chunks outside the current camera can continue rebuilding while the
	 * visible terrain is already stable.
	 */
	private static void recordVisibleSubmissionIdentity(long sectionPos, ChunkSectionLayer layer, long generation) {
		boolean benchmarkNeedsIdentity = net.minecraft.client.dev.GraphicsFrameBenchmark.needsSubmittedWorkIdentity();
		boolean captureNeedsIdentity = net.minecraft.client.dev.DeterministicCameraCapture.needsSubmittedWorkIdentity();
		if (!benchmarkNeedsIdentity && !captureNeedsIdentity) {
			return;
		}
		String identity = "rust-vulkan-whole-frame:section=" + sectionPos
			+ ":layer=" + layer.name()
			+ ":generation=" + generation;
		// The direct CPU producer replaces Sodium's GL-region renderer on this
		// route, but it is still the semantic sodium-terrain family. Recording
		// that identity lets the shared parity capture wait for real submitted
		// terrain instead of accepting an empty first Rust frame.
		if (benchmarkNeedsIdentity) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordSubmittedWorkIdentity("sodium-terrain", identity);
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordSubmittedWorkIdentity("static-terrain", identity);
		}
		if (captureNeedsIdentity) {
			net.minecraft.client.dev.DeterministicCameraCapture.recordSubmittedWorkIdentity("sodium-terrain", identity);
			net.minecraft.client.dev.DeterministicCameraCapture.recordSubmittedWorkIdentity("static-terrain", identity);
		}
	}

	public static boolean injectCrossWorldStaleSubmissionForDiagnostics(int viewportWidth, int viewportHeight) {
		if (!"cross-world-stale-submission".equals(activeFault())) {
			return false;
		}
		TerrainSectionAsset stale = lastWorldUnloadAsset;
		if (stale == null) {
			return false;
		}
		long enqueueFrameId = rustEnqueueFrames.incrementAndGet();
		boolean submitted = RustGalWorldPrimitiveRenderer.enqueueStaticTerrainMeshInstance(
			stale.meshKey(),
			stale.meshGeneration(),
			cameraRelativeTranslationTransform(0.0F, 0.0F, 0.0F),
			viewportWidth,
			viewportHeight
		);
		if (submitted) {
			visibleLayerSubmissions.incrementAndGet();
			recordCurrentFrameVisibleSubmission(enqueueFrameId, lastWorldUnloadSectionPos,
				lastWorldUnloadLayer, stale.meshKey(), stale.meshGeneration());
			recordEvent(
				lastWorldUnloadSectionPos,
				lastWorldUnloadLayer,
				0L,
				stale.meshGeneration(),
				stale.meshGeneration(),
				atlasGeneration,
				stale,
				stale.sectionOriginX(),
				stale.sectionOriginY(),
				stale.sectionOriginZ(),
				0.0F,
				0.0F,
				0.0F,
				"cross_world_stale_submission_unexpected_success",
				0L,
				enqueueFrameId,
				0L,
				0L
			);
			return true;
		}
		failedLayerSubmissions.incrementAndGet();
		recordEvent(
			lastWorldUnloadSectionPos,
			lastWorldUnloadLayer,
			0L,
			stale.meshGeneration(),
			stale.meshGeneration(),
			atlasGeneration,
			stale,
			stale.sectionOriginX(),
			stale.sectionOriginY(),
			stale.sectionOriginZ(),
			0.0F,
			0.0F,
			0.0F,
			"cross_world_stale_submission",
			0L,
			enqueueFrameId,
			0L,
			0L
		);
		return false;
	}

	private static void recordCurrentFrameVisibleSubmission(long fallbackFrameId, long sectionPos,
		ChunkSectionLayer layer, long meshKey, long meshGeneration) {
		long frameId = currentGameplayFrameId();
		if (frameId <= 0L) {
			frameId = fallbackFrameId;
		}
		long previousFrameId = lastVisibleSubmissionFrameId.getAndSet(frameId);
		if (previousFrameId == frameId) {
			currentFrameVisibleLayerSubmissions.incrementAndGet();
		} else {
			currentFrameVisibleLayerSubmissions.set(1L);
			currentFrameVisibleFingerprint.set(0xcbf29ce484222325L);
		}
		long fingerprint = currentFrameVisibleFingerprint.get();
		fingerprint = terrainFingerprintLong(fingerprint, sectionPos);
		fingerprint = terrainFingerprintLong(fingerprint, meshKey);
		fingerprint = terrainFingerprintLong(fingerprint, meshGeneration);
		fingerprint = terrainFingerprintLong(fingerprint, layer.ordinal());
		currentFrameVisibleFingerprint.set(fingerprint);
	}

	/** One word of the visible-layer fingerprint; Rust's selection mixes identically. */
	private static long terrainFingerprintLong(long hash, long value) {
		long mixed = (hash ^ value) * 0x100000001b3L;
		return mixed ^ (mixed >>> 32);
	}

	private static boolean finiteBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(minZ)
			&& Float.isFinite(maxX) && Float.isFinite(maxY) && Float.isFinite(maxZ)
			&& minX <= maxX && minY <= maxY && minZ <= maxZ;
	}

	private static int activeTerrainVertexStride() {
		return COMPACT_PREFIX_STRIDE;
	}

	private record TerrainMeshLayout(int vertexStride, boolean separateAo, int shaderBlockIdOffset, int midBlockOffset) {
		private static TerrainMeshLayout compact(boolean separateAo) {
			// The compact stride is independent of the copied pack's AO policy.
			// A source pack may request a separate AO alpha for its terrain shader;
			// the direct vanilla shader consumes it explicitly when present.
			return new TerrainMeshLayout(COMPACT_PREFIX_STRIDE, separateAo, 0, 0);
		}

	}

	// Iris stores (blockId + 1) << 1 with the low bit reserved for render type.
	static int decodeIrisShaderBlockId(int packedBlockId) {
		return (packedBlockId >>> 1) - 1;
	}

	static int decodeIrisShaderRenderType(int packedBlockId) {
		return packedBlockId & 1;
	}

	private static String activeFault() {
		String scenario = System.getProperty(STATIC_TERRAIN_SCENARIO_PROPERTY, "").trim();
		if (scenario.isBlank() || "hidden".equalsIgnoreCase(scenario)) {
			return "";
		}
		return System.getProperty(FAULT_PROPERTY, "").trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Per-visible-layer diagnostic records are intentionally capture/fault-only.
	 * Ordinary gameplay still records semantic submission counts and Rust
	 * execution receipts, but constructing a large object plus locking the recent
	 * event deque for every visible layer would turn the producer into the frame
	 * bottleneck. Deterministic captures and fault fixtures retain the complete
	 * event stream needed by their validation contracts.
	 */
	private static boolean detailedTerrainDiagnosticsEnabled() {
		return detailedTerrainDiagnosticsEnabled(activeFault());
	}

	private static boolean detailedTerrainDiagnosticsEnabled(String fault) {
		return !net.minecraft.client.dev.GraphicsFrameBenchmark.isMeasurementWindowForDiagnostics()
			&& (Boolean.getBoolean("mattmc.dev.deterministicCameraCapture")
			|| Boolean.getBoolean("mattmc.dev.staticTerrainParityDiagnostics")
			|| !fault.isEmpty());
	}

	/** Cumulative and current-frame receipts have no ordinary gameplay consumer. */
	private static boolean terrainCountersEnabled(String fault) {
		return net.minecraft.client.dev.GraphicsFrameBenchmark.isActiveForDiagnostics()
			|| net.minecraft.client.dev.DeterministicCameraCapture.isActiveForDiagnostics()
			|| net.sodium.client.render.StaticTerrainParityDiagnostics.isEnabled()
			|| !fault.isEmpty();
	}

	private static boolean isStaticTerrainTranslucentScenario() {
		String scenario = System.getProperty(STATIC_TERRAIN_SCENARIO_PROPERTY, "").trim();
		return "translucent-glass".equalsIgnoreCase(scenario) || "translucent-overlap".equalsIgnoreCase(scenario);
	}

	private static float decodePosition(int hi, int lo, int component) {
		int shift = component * 10;
		int value = ((hi >>> shift) & 0x3ff) << 10 | ((lo >>> shift) & 0x3ff);
		return value / (float)POSITION_MAX_VALUE * 32.0F - 8.0F;
	}

	/**
	 * Decodes Sodium's compact terrain coordinate into the copied block-atlas
	 * coordinate system. The copied atlas is top-origin, so this is deliberately
	 * not an OpenGL-style {@code 1 - v} conversion.
	 */
	static float decodeTexture(int value) {
		return (value & 0x7fff) / (float)TEXTURE_MAX_VALUE;
	}


	private static float unpackPackedNormalComponent(int normalPacked, int shift) {
		return (byte)((normalPacked >>> shift) & 0xff) / 127.0F;
	}




		private static TranslucentSortSnapshot currentTranslucentSortSnapshot(TerrainSectionAsset asset) {
			if (asset == null) {
				return null;
			}
			// Rust orders translucent geometry per frame from this initial
			// order; the payload itself may already be released from Java.
			if (asset.initialSortGeneration() <= 0L || asset.translucentIndexBytes() == 0) {
				return null;
			}
			return new TranslucentSortSnapshot(
				asset.meshGeneration(),
				asset.initialSortGeneration(),
				asset.indexType(),
				asset.translucentIndexBytes(),
				asset.translucentIndexHash()
			);
		}


	static long sortedIndexHash(byte[] indexBytes) {
		return fnv64Bytes(fnv64("static-terrain-translucent-sort-bytes-v1"), indexBytes == null ? new byte[0] : indexBytes);
	}

	private static long sortedIndexSampleHash(byte[] indexBytes) {
		if (indexBytes == null || indexBytes.length == 0) {
			return 0L;
		}
		int sampleBytes = Math.min(indexBytes.length, Integer.BYTES * 12);
		long hash = fnv64("static-terrain-translucent-sort-sample-v1");
		hash = fnv64Int(hash, sampleBytes);
		for (int i = 0; i < sampleBytes; i++) {
			hash ^= indexBytes[i] & 0xffL;
			hash *= 0x100000001b3L;
		}
		return hash == 0L ? 1L : hash;
	}

	private static String sortedIndexSample(byte[] indexBytes, int indexType, int maxIndices) {
		if (indexBytes == null || indexBytes.length == 0 || maxIndices <= 0) {
			return "";
		}
		int stride = indexType == INDEX_TYPE_U16 ? Short.BYTES : Integer.BYTES;
		int count = Math.min(maxIndices, indexBytes.length / stride);
		ByteBuffer buffer = ByteBuffer.wrap(indexBytes).order(ByteOrder.LITTLE_ENDIAN);
		StringBuilder sample = new StringBuilder();
		for (int index = 0; index < count; index++) {
			if (index > 0) {
				sample.append(',');
			}
			int value = indexType == INDEX_TYPE_U16
				? buffer.getShort(index * stride) & 0xffff
				: buffer.getInt(index * stride);
			sample.append(value);
		}
		return sample.toString();
	}

		private static String initialTranslucentSorterType(ChunkBuildOutput output) {
			if (output == null || output.translucentData == null) {
				return "initial-build-index";
			}
			return "initial-build-index:"
				+ output.translucentData.getSortType().name().toLowerCase(Locale.ROOT)
				+ ":"
				+ output.translucentData.getClass().getSimpleName();
		}




	static List<VulkanicGalBridge.WorldMeshTextureAssetRecord> atlasTextureUpdatePayload(WaterTextureBinding waterBinding) {
		byte[] payload = atlasPayload;
		long generation = atlasGeneration;
		if (payload == null || registeredAtlasGeneration == generation) {
			return List.of();
		}
		synchronized (RustGalTerrainRenderer.class) {
			if (atlasPayload == null || registeredAtlasGeneration == atlasGeneration) {
				return List.of();
			}
			texturePayloadUpdates.incrementAndGet();
			texturePayloadUpdateBytes.addAndGet(atlasPayloadByteCount(atlasPayload, atlasMipPayloads));
			ArrayList<VulkanicGalBridge.WorldMeshTextureAssetRecord> records = new ArrayList<>(4);
			// This encoder emits the already-normalized row order consumed by the
			// Rust terrain UV contract.  It must agree with the eager whole-frame
			// publisher above: the same semantic atlas cannot acquire a different
			// origin merely because a section build happens first.
			records.add(new VulkanicGalBridge.WorldMeshTextureAssetRecord(
				RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS,
				atlasPayload,
				atlasMipPayloads
			));
			if (normalAtlasPayload != null) {
				records.add(new VulkanicGalBridge.WorldMeshTextureAssetRecord(
					RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_NORMAL_ATLAS,
					normalAtlasPayload
				));
			}
			if (specularAtlasPayload != null) {
				records.add(new VulkanicGalBridge.WorldMeshTextureAssetRecord(
					RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_SPECULAR_ATLAS,
					specularAtlasPayload
				));
			}
			// Atlas-bound water shares the explicit terrain resource. Do not
			// publish unused separate sheets into the Rust GPU resource registry.
			if (waterBinding == WaterTextureBinding.SEPARATE_SHEETS && waterStillAsset != null) {
				records.add(waterStillAsset.textureRecord());
			}
			if (waterBinding == WaterTextureBinding.SEPARATE_SHEETS && waterFlowAsset != null) {
				records.add(waterFlowAsset.textureRecord());
			}
			if (waterBinding == WaterTextureBinding.SEPARATE_SHEETS && waterOverlayAsset != null) {
				records.add(waterOverlayAsset.textureRecord());
			}
			return records;
		}
	}

	/** Commits atlas-generation residency only after Rust accepted a mesh batch. */
	private static void confirmAtlasPayloadRegistered(long generation) {
		synchronized (RustGalTerrainRenderer.class) {
			if (atlasGeneration == generation) {
				registeredAtlasGeneration = generation;
			}
		}
	}

	private static boolean isPngPayload(byte[] payload) {
		return payload != null
			&& payload.length >= 8
			&& (payload[0] & 0xff) == 0x89
			&& payload[1] == 0x50
			&& payload[2] == 0x4e
			&& payload[3] == 0x47
			&& payload[4] == 0x0d
			&& payload[5] == 0x0a
			&& payload[6] == 0x1a
			&& payload[7] == 0x0a;
	}

	/**
	 * Frame selection is part of an animated resource's Rust-owned clock, while
	 * the atlas declaration is replaced only by a semantic resource reload.
	 * Keep this identity rule pure so the publication boundary cannot regress to
	 * full-atlas uploads on every animation tick.
	 */
	static boolean canReuseSemanticAtlasPayload(long copiedSemanticGeneration, long semanticGeneration,
		long copiedFrameKey, long semanticFrameKey, boolean animationDeclarationStable) {
		return copiedSemanticGeneration == semanticGeneration
			&& (animationDeclarationStable || copiedFrameKey == semanticFrameKey);
	}

	private static void removeOrStageEmptyLayer(long sectionPos, ChunkSectionLayer layer, String reason) {
		if (resourceReloadStaging) {
			RESOURCE_RELOAD_SECTION_ASSETS.remove(new LayerKey(sectionPos, layer));
			return;
		}
		removeLayer(sectionPos, layer, reason);
	}

	/**
	 * Atomically replaces the published terrain generation after every rebuilt
	 * mesh has crossed the native asset boundary. The atlas remains old until
	 * this point, so no presented frame can combine old UVs with new pixels.
	 */
	static boolean finishResourceReloadIfReady() {
		if (!resourceReloadStaging) {
			return true;
		}
		for (TerrainSectionAsset asset : RESOURCE_RELOAD_SECTION_ASSETS.values()) {
			if (!RustGalWorldPrimitiveRenderer.isStaticTerrainMeshGenerationUploaded(
				asset.meshKey(), asset.meshGeneration()
			)) {
				return false;
			}
		}
		// Every staged layer is uploaded, but its acknowledgement could not
		// release the CPU payload while the layer was only staged (the release
		// resolves published layers); release it with the commit.
		Map<LayerKey, TerrainSectionAsset> replacement = new HashMap<>(RESOURCE_RELOAD_SECTION_ASSETS.size());
		for (Map.Entry<LayerKey, TerrainSectionAsset> entry : RESOURCE_RELOAD_SECTION_ASSETS.entrySet()) {
			TerrainSectionAsset asset = entry.getValue();
			replacement.put(entry.getKey(), asset.asset() == null ? asset : asset.releaseCpuPayload());
		}
		resourceReloadCommitInProgress = true;
		try {
			ensureTerrainAtlasAssetForWorldMesh();
			confirmAtlasPayloadRegistered(atlasGeneration);
			List<TerrainSectionAsset> previous = List.copyOf(SECTION_ASSETS.values());
			rebuildMeshKeyIdentityIndex(replacement);
			replacePublication(replacement);
			SECTION_ASSETS.clear();
			SECTION_ASSETS.putAll(replacement);
			SECTION_ASSET_ROWS.clear();
			for (LayerKey key : replacement.keySet()) {
				mirrorSectionAssetRow(key);
			}
			RESOURCE_RELOAD_SECTION_ASSETS.clear();
			resourceReloadStaging = false;
			for (TerrainSectionAsset asset : previous) {
				RustGalWorldPrimitiveRenderer.removeStaticTerrainMeshAsset(asset.meshKey());
			}
			recordEvent(0L, ChunkSectionLayer.SOLID, 0L, 0L, 0L, atlasGeneration,
				null, 0, 0, 0, 0.0F, 0.0F, 0.0F, "resource-reload-committed");
			return true;
		} finally {
			resourceReloadCommitInProgress = false;
		}
	}

	private static void discardStagedResourceReloadAssets() {
		for (TerrainSectionAsset asset : RESOURCE_RELOAD_SECTION_ASSETS.values()) {
			RustGalWorldPrimitiveRenderer.removeStaticTerrainMeshAsset(asset.meshKey());
		}
		RESOURCE_RELOAD_SECTION_ASSETS.clear();
	}

	/** Rust's publication registry owns each section's solid, cutout and
	 * translucent row (and rejects a mesh key another section layer holds);
	 * the camera section graph reads its rows from there. */
	private static void publishSectionAsset(LayerKey layerKey, TerrainSectionAsset asset) {
		TerrainAssetIdentity identity = new TerrainAssetIdentity(layerKey, asset);
		int slot = rowSlot(layerKey.layer());
		long[] collision = RustTerrainPublication.publish(layerKey.sectionPos(), slot, asset.meshKey(),
			asset.meshGeneration(), publicationFlags(layerKey.layer(), asset));
		if (collision != null) {
			throw new IllegalStateException("Rust terrain mesh key maps to multiple section layers: meshKey="
				+ asset.meshKey() + " existing=" + new LayerKey(collision[1], ROW_LAYERS[(int)collision[2]])
				+ " replacement=" + layerKey);
		}
		TerrainSectionAsset replaced = SECTION_ASSETS.put(layerKey, asset);
		mirrorSectionAssetRow(layerKey);
		if (replaced != null && replaced.meshKey() != asset.meshKey()) {
			SECTION_ASSETS_BY_MESH_KEY.computeIfPresent(replaced.meshKey(), (meshKey, previous) ->
				previous.layerKey().equals(layerKey) ? null : previous
			);
		}
		SECTION_ASSETS_BY_MESH_KEY.put(asset.meshKey(), identity);
	}

	/** Swaps Rust's publication rows for the reload's, at once. */
	private static void replacePublication(Map<LayerKey, TerrainSectionAsset> assets) {
		long[] layers = new long[assets.size() * 5];
		int count = 0;
		for (Map.Entry<LayerKey, TerrainSectionAsset> entry : assets.entrySet()) {
			LayerKey key = entry.getKey();
			TerrainSectionAsset asset = entry.getValue();
			layers[count * 5] = key.sectionPos();
			layers[count * 5 + 1] = rowSlot(key.layer());
			layers[count * 5 + 2] = asset.meshKey();
			layers[count * 5 + 3] = asset.meshGeneration();
			layers[count * 5 + 4] = publicationFlags(key.layer(), asset);
			count++;
		}
		// rebuildMeshKeyIdentityIndex has already rejected duplicate keys.
		if (RustTerrainPublication.replace(layers, count) != null) {
			throw new IllegalStateException("Rust terrain reload publication contains a duplicate mesh key");
		}
	}

	private static void rebuildMeshKeyIdentityIndex(Map<LayerKey, TerrainSectionAsset> assets) {
		Map<Long, TerrainAssetIdentity> rebuilt = new HashMap<>(assets.size());
		for (Map.Entry<LayerKey, TerrainSectionAsset> entry : assets.entrySet()) {
			TerrainAssetIdentity identity = new TerrainAssetIdentity(entry.getKey(), entry.getValue());
			TerrainAssetIdentity collision = rebuilt.putIfAbsent(entry.getValue().meshKey(), identity);
			if (collision != null && !collision.layerKey().equals(entry.getKey())) {
				throw new IllegalStateException("Rust terrain reload contains a duplicate mesh key: meshKey="
					+ entry.getValue().meshKey() + " first=" + collision.layerKey() + " second=" + entry.getKey());
			}
		}
		SECTION_ASSETS_BY_MESH_KEY.clear();
		SECTION_ASSETS_BY_MESH_KEY.putAll(rebuilt);
	}

	private static void removeLayer(long sectionPos, ChunkSectionLayer layer, String reason) {
		LayerKey layerKey = new LayerKey(sectionPos, layer);
		int slot = rowSlot(layer);
		if (slot >= 0) {
			RustTerrainPublication.remove(sectionPos, slot);
		}
		TerrainSectionAsset removed = SECTION_ASSETS.remove(layerKey);
		mirrorSectionAssetRow(layerKey);
		if (removed != null) {
			SECTION_ASSETS_BY_MESH_KEY.computeIfPresent(removed.meshKey(), (meshKey, identity) ->
				identity.layerKey().equals(layerKey) && identity.asset().meshGeneration() == removed.meshGeneration()
					? null
					: identity
			);
			if ("world-unload".equals(reason)) {
				lastWorldUnloadAsset = removed;
				lastWorldUnloadSectionPos = sectionPos;
				lastWorldUnloadLayer = layer;
			}
			removedLayers.incrementAndGet();
			TRANSLUCENT_EXECUTION_METADATA.remove(removed.meshKey());
			RustGalWorldPrimitiveRenderer.removeStaticTerrainMeshAsset(removed.meshKey());
				recordEvent(sectionPos, layer, 0L, removed.meshGeneration(), 0L, atlasGeneration, removed, 0, 0, 0, 0.0F, 0.0F, 0.0F, reason);
		}
	}

	private static void ensureAtlasPayload() {
		TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
		long atlasPixels = (long) atlas.width * atlas.height;
		if (atlas.width <= 0 || atlas.height <= 0) {
			throw new IllegalStateException("Rust terrain atlas is not uploaded: " + atlas.width + "x" + atlas.height);
		}
		if (atlasPixels > MAX_RUST_ATLAS_PIXELS) {
			throw new IllegalStateException("Rust terrain atlas pixel bound exceeded " + MAX_RUST_ATLAS_PIXELS
				+ " extent=" + atlas.width + "x" + atlas.height);
		}
		long semanticGeneration = atlas.semanticReloadGeneration();
		if (semanticGeneration <= 0L) return;
		long semanticFrameKey = atlas.semanticSnapshotFrameKey();
		synchronized (RustGalTerrainRenderer.class) {
			/*
			 * An animated atlas is an immutable declaration plus a Rust-owned pixel
			 * clock.  Its Java frame key is not a resource identity: rebuilding the
			 * full PNG atlas for each tick would replace the publication, clear queued
			 * sprite uses, and bypass the bounded per-sprite patch path.  A fresh
			 * semantic reload still changes both the atlas generation and resource
			 * object, so it remains a real replacement here.
			 */
			AtlasAnimationResource currentAnimation = atlas.semanticAnimationResource();
			boolean animationDeclarationStable = currentAnimation != null
				&& atlasAnimationSource == currentAnimation;
			if (atlasPayload != null && canReuseSemanticAtlasPayload(copiedAtlasSemanticGeneration,
				semanticGeneration, copiedAtlasSemanticFrameKey, semanticFrameKey, animationDeclarationStable)) return;
			semanticFrameKey = atlas.semanticSnapshotFrameKey();
			currentAnimation = atlas.semanticAnimationResource();
			animationDeclarationStable = currentAnimation != null && atlasAnimationSource == currentAnimation;
			if (atlasPayload != null && canReuseSemanticAtlasPayload(copiedAtlasSemanticGeneration,
				semanticGeneration, copiedAtlasSemanticFrameKey, semanticFrameKey, animationDeclarationStable)) return;
				try {
					// Keep the large base image's lifetime confined to this helper.  The
					// derived PBR atlases are independently bounded images; retaining the
					// base BufferedImage across their allocations can exhaust a small
					// capture heap even when each individual atlas is within its limit.
					TextureAtlas.SemanticRawSnapshot semanticRawSnapshot = atlas.semanticRawSnapshot();
					if (semanticRawSnapshot == null
						|| semanticRawSnapshot.generation() != atlas.semanticSnapshotGeneration()
						|| semanticRawSnapshot.width() != atlas.width
						|| semanticRawSnapshot.height() != atlas.height) {
						throw new IllegalStateException("Rust terrain atlas semantic snapshot is unavailable or incoherent");
					}
					// Re-read after snapshot construction. Animation can advance on the
					// client tick while this bounded CPU copy is being built; never tag a
					// payload with a key that describes a different selected frame.
					long snapshotFrameKey = atlas.semanticSnapshotFrameKey();
					if (snapshotFrameKey != semanticFrameKey) {
						semanticRawSnapshot = atlas.semanticRawSnapshot();
						snapshotFrameKey = atlas.semanticSnapshotFrameKey();
						if (semanticRawSnapshot == null
							|| semanticRawSnapshot.generation() != atlas.semanticSnapshotGeneration()
							|| snapshotFrameKey != atlas.semanticSnapshotFrameKey()) {
							throw new IllegalStateException("Rust terrain atlas semantic animation snapshot changed during copy");
						}
					}
					byte[] nextAtlasPayload = encodeSemanticAtlasPayload(semanticRawSnapshot);
					List<byte[]> nextAtlasMipPayloads = encodeSemanticAtlasMipPayloads(semanticRawSnapshot);
					var nextAnimationSource = atlas.semanticAnimationResource();
					if (nextAnimationSource != null && (nextAnimationSource.source().generation() != semanticRawSnapshot.generation()
						|| nextAnimationSource.source().width() != semanticRawSnapshot.width()
						|| nextAnimationSource.source().height() != semanticRawSnapshot.height())) {
						throw new IllegalStateException("Terrain atlas changed while copying animation declarations");
					}
					// Do not allocate full atlas-sized PBR images unless the active
					// resource manager actually exposes that semantic map.  A missing
					// map is a valid Rust resource state; constructing a default image
					// for every atlas needlessly duplicates tens of megabytes and can
					// exhaust the bounded capture heap during first world entry.
					byte[] nextNormalAtlasPayload = hasPbrResources(atlas, "_n")
						? buildPbrAtlasPayload(atlas, "_n", 0x7F7FFFFF) : null;
					byte[] nextSpecularAtlasPayload = hasPbrResources(atlas, "_s")
						? buildPbrAtlasPayload(atlas, "_s", 0x00000000) : null;
					FluidSpriteAsset nextWaterStillAsset = buildFluidSpriteAsset(
						atlas,
						"block/water_still",
						RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_STILL
					);
					FluidSpriteAsset nextWaterFlowAsset = buildFluidSpriteAsset(
						atlas,
						"block/water_flow",
						RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_FLOW
					);
					FluidSpriteAsset nextWaterOverlayAsset = buildFluidSpriteAsset(
						atlas,
						"block/water_overlay",
						RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_OVERLAY
					);
					atlasPayload = nextAtlasPayload;
					atlasMipPayloads = nextAtlasMipPayloads;
					atlasAnimationSource = nextAnimationSource;
					normalAtlasPayload = nextNormalAtlasPayload;
					specularAtlasPayload = nextSpecularAtlasPayload;
					waterStillAsset = nextWaterStillAsset;
					waterFlowAsset = nextWaterFlowAsset;
					waterOverlayAsset = nextWaterOverlayAsset;
					copiedAtlasSemanticGeneration = semanticGeneration;
					copiedAtlasSemanticFrameKey = snapshotFrameKey;
					atlasGeneration++;
			} catch (RuntimeException | IOException error) {
				throw new IllegalStateException("Failed to build Rust-owned block atlas payload for static terrain", error);
			}
		}
	}

	private static byte[] encodeSemanticAtlasPayload(TextureAtlas.SemanticRawSnapshot snapshot) throws IOException {
		return encodeSemanticRgbaPng(snapshot.width(), snapshot.height(), snapshot.pixels());
	}

	private static List<byte[]> encodeSemanticAtlasMipPayloads(TextureAtlas.SemanticRawSnapshot snapshot) throws IOException {
		List<byte[]> rawLevels = snapshot.mipPixels();
		if (rawLevels.isEmpty()) {
			throw new IOException("semantic atlas mip chain is incomplete");
		}
		ArrayList<byte[]> encoded = new ArrayList<>(Math.max(0, rawLevels.size() - 1));
		for (int mip = 1; mip < rawLevels.size(); mip++) {
			encoded.add(encodeSemanticRgbaPng(
				Math.max(1, snapshot.width() >> mip), Math.max(1, snapshot.height() >> mip), rawLevels.get(mip)
			));
		}
		return List.copyOf(encoded);
	}

	private static long atlasPayloadByteCount(byte[] baseLevel, List<byte[]> mipLevels) {
		long total = baseLevel.length;
		for (byte[] mipLevel : mipLevels) {
			total = Math.addExact(total, mipLevel.length);
		}
		return total;
	}

	private static byte[] encodeSemanticRgbaPng(int width, int height, byte[] pixels) throws IOException {
		if (pixels.length != Math.multiplyExact(Math.multiplyExact(width, height), 4)) {
			throw new IOException("semantic RGBA extent does not match pixel payload");
		}
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int offset = (y * width + x) * 4;
				int argb = net.minecraft.util.ARGB.color(
					pixels[offset + 3] & 0xff,
					pixels[offset] & 0xff,
					pixels[offset + 1] & 0xff,
					pixels[offset + 2] & 0xff
				);
				image.setRGB(x, y, argb);
			}
		}
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", output);
			return output.toByteArray();
		}
	}

	private static FluidSpriteAsset buildFluidSpriteAsset(TextureAtlas atlas, String spritePath, int textureId) throws IOException {
		TextureAtlasSprite sprite = atlas.getSprite(ResourceLocation.fromNamespaceAndPath("minecraft", spritePath));
		if (sprite == null || sprite.contents() == null) {
			throw new IllegalStateException("Missing built-in water sprite " + spritePath);
		}
		SpriteContents contents = sprite.contents();
		SpriteContents.AnimatedTexture animation = contents.animatedTexture;
		int frameCount = 1;
		int frameTicks = 1;
		int animationFlags = 0;
		int frameRowSize = 0;
		int interpolationPolicy = 0;
		List<VulkanicGalBridge.WorldMeshAnimationFrameRecord> animationFrames = List.of();
		if (animation != null && !animation.frames.isEmpty()) {
			frameCount = animation.frames.size();
			frameTicks = animation.frames.get(0).time();
			frameRowSize = animation.frameRowSize;
			interpolationPolicy = animation.interpolateFrames() ? 1 : 0;
			ArrayList<VulkanicGalBridge.WorldMeshAnimationFrameRecord> frames = new ArrayList<>(animation.frames.size());
			for (SpriteContents.FrameInfo frame : animation.frames) {
				frames.add(new VulkanicGalBridge.WorldMeshAnimationFrameRecord(frame.index(), frame.time()));
			}
			animationFrames = List.copyOf(frames);
		}
		byte[] png = encodeNativeSpriteImage(contents.originalImage);
		return new FluidSpriteAsset(
			textureId,
			contents.name(),
			sprite.getU0(),
			sprite.getU1(),
			sprite.getV0(),
			sprite.getV1(),
			contents.width(),
			contents.height(),
			Math.max(1, frameCount),
			Math.max(1, frameTicks),
			animationFlags,
			Math.max(0, frameRowSize),
			interpolationPolicy,
			animationFrames,
			png,
			atlas.mipLevel > 1 ? atlas.mipLevel + 1 : 1
		);
	}

	private static byte[] encodeNativeSpriteImage(net.blaze3d.platform.NativeImage image) throws IOException {
		BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				copy.setRGB(x, y, image.getPixel(x, y));
			}
		}
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(copy, "png", output);
			return output.toByteArray();
		}
	}

	private static long rgbaHash(net.blaze3d.platform.NativeImage image, int originX, int originY, int width, int height) {
		long hash = 0xcbf29ce484222325L;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				hash = fnv64Rgba(hash, image.getPixel(originX + x, originY + y));
			}
		}
		return hash;
	}

	private static long rgbaHash(BufferedImage image, int originX, int originY, int width, int height) {
		if (originX < 0 || originY < 0 || width < 0 || height < 0
			|| originX + width > image.getWidth() || originY + height > image.getHeight()) {
			throw new IllegalArgumentException("atlas receipt region is outside the decoded atlas");
		}
		long hash = 0xcbf29ce484222325L;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				hash = fnv64Rgba(hash, image.getRGB(originX + x, originY + y));
			}
		}
		return hash;
	}

	/**
	 * Builds an atlas-aligned semantic PBR payload directly from resolved
	 * resource-pack files. This intentionally does not query Iris PBR atlas
	 * objects or their GL textures. Missing sprites retain the source-defined
	 * default value for the requested semantic map.
	 */
	private static byte[] buildPbrAtlasPayload(TextureAtlas atlas, String suffix, int defaultArgb) throws IOException {
		long pixels = (long) atlas.width * atlas.height;
		if (atlas.width <= 0 || atlas.height <= 0 || pixels > MAX_RUST_ATLAS_PIXELS) {
			throw new IOException("Rust PBR atlas pixel bound exceeded " + MAX_RUST_ATLAS_PIXELS);
		}
		BufferedImage image = new BufferedImage(atlas.width, atlas.height, BufferedImage.TYPE_INT_ARGB);
		if (defaultArgb != 0) {
			java.awt.Graphics2D graphics = image.createGraphics();
			try {
				graphics.setColor(new java.awt.Color(defaultArgb, true));
				graphics.fillRect(0, 0, atlas.width, atlas.height);
			} finally {
				graphics.dispose();
			}
		}
		for (TextureAtlasSprite sprite : atlas.texturesByName.values()) {
			copyPbrSprite(image, sprite, suffix);
		}
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", output);
			return output.toByteArray();
		}
	}

	private static boolean hasPbrResources(TextureAtlas atlas, String suffix) {
		for (TextureAtlasSprite sprite : atlas.texturesByName.values()) {
			ResourceLocation spriteName = sprite.contents().name();
			String pbrPath = appendPbrSuffix(spriteName.getPath(), suffix);
			ResourceLocation pbrLocation = pbrPath.startsWith("optifine/cit/")
				? ResourceLocation.fromNamespaceAndPath(spriteName.getNamespace(), pbrPath + ".png")
				: ResourceLocation.fromNamespaceAndPath(spriteName.getNamespace(), "textures/" + pbrPath + ".png");
			if (Minecraft.getInstance().getResourceManager().getResource(pbrLocation).isPresent()) return true;
		}
		return false;
	}

	private static void copyPbrSprite(BufferedImage atlasImage, TextureAtlasSprite sprite, String suffix) throws IOException {
		ResourceLocation spriteName = sprite.contents().name();
		String pbrPath = appendPbrSuffix(spriteName.getPath(), suffix);
		ResourceLocation pbrLocation = pbrPath.startsWith("optifine/cit/")
			? ResourceLocation.fromNamespaceAndPath(spriteName.getNamespace(), pbrPath + ".png")
			: ResourceLocation.fromNamespaceAndPath(spriteName.getNamespace(), "textures/" + pbrPath + ".png");
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(pbrLocation);
		if (resource.isEmpty()) {
			return;
		}
		try (InputStream input = resource.get().open()) {
			BufferedImage source = ImageIO.read(input);
			if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
				throw new IOException("Unable to decode PBR sprite " + pbrLocation);
			}
			int targetWidth = sprite.contents().width();
			int targetHeight = sprite.contents().height();
			for (int y = 0; y < targetHeight; y++) {
				int sourceY = Math.min(source.getHeight() - 1, (int)((long)y * source.getHeight() / targetHeight));
				for (int x = 0; x < targetWidth; x++) {
					int sourceX = Math.min(source.getWidth() - 1, (int)((long)x * source.getWidth() / targetWidth));
					atlasImage.setRGB(sprite.getX() + x, sprite.getY() + y, source.getRGB(sourceX, sourceY));
				}
			}
		}
	}

	static int terrainDepthPolicy(ChunkSectionLayer layer) {
		return layer.pipeline().isWriteDepth()
			? RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_WRITE
			: RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_NO_WRITE;
	}

	static boolean terrainCameraSortRequested(ChunkSectionLayer layer, SortType sortType) {
		// Sodium's static sort modes already authored their one valid index order
		// into the copied asset. Re-sorting those quads every frame both changes
		// that order and explodes one section into many tiny draw batches.
		return layer == ChunkSectionLayer.TRANSLUCENT && sortType == SortType.DYNAMIC;
	}

	static String appendPbrSuffix(String path, String suffix) {
		int extension = path.lastIndexOf('.');
		return extension < 0 ? path + suffix : path.substring(0, extension) + suffix + path.substring(extension);
	}

	private static long fnv64(String value) {
		long hash = 0xcbf29ce484222325L;
		for (int i = 0; i < value.length(); i++) {
			hash ^= value.charAt(i);
			hash *= 0x100000001b3L;
		}
		return hash;
	}

	private static long fnv64Long(long hash, long value) {
		hash = fnv64Int(hash, (int)value);
		return fnv64Int(hash, (int)(value >>> 32));
	}

	private static long fnv64Int(long hash, int value) {
		for (int shift = 0; shift < 32; shift += 8) {
			hash ^= (value >>> shift) & 0xffL;
			hash *= 0x100000001b3L;
		}
		return hash;
	}

	private static long fnv64Rgba(long hash, int argb) {
		hash ^= (argb >>> 16) & 0xffL;
		hash *= 0x100000001b3L;
		hash ^= (argb >>> 8) & 0xffL;
		hash *= 0x100000001b3L;
		hash ^= argb & 0xffL;
		hash *= 0x100000001b3L;
		hash ^= (argb >>> 24) & 0xffL;
		hash *= 0x100000001b3L;
		return hash;
	}

	private static long fnv64Float(long hash, float value) {
		return fnv64Int(hash, Float.floatToIntBits(value));
	}

	private static long fnv64Bytes(long hash, byte[] bytes) {
		hash = fnv64Int(hash, bytes.length);
		for (byte value : bytes) {
			hash ^= value & 0xffL;
			hash *= 0x100000001b3L;
		}
		return hash;
	}

	private static void recordEvent(
		long sectionPos,
		ChunkSectionLayer layer,
		long sourceGeneration,
		long meshGeneration,
		long visibleGeneration,
		long uploadGeneration,
		TerrainSectionAsset asset,
		int sectionOriginX,
		int sectionOriginY,
		int sectionOriginZ,
		float transformX,
		float transformY,
		float transformZ,
		String reason
	) {
		recordEvent(sectionPos, layer, sourceGeneration, meshGeneration, visibleGeneration, uploadGeneration, asset,
			sectionOriginX, sectionOriginY, sectionOriginZ, transformX, transformY, transformZ, reason, 0L, 0L, 0L, 0L);
	}

	private static void recordEvent(
		long sectionPos,
		ChunkSectionLayer layer,
		long sourceGeneration,
		long meshGeneration,
		long visibleGeneration,
		long uploadGeneration,
		TerrainSectionAsset asset,
		int sectionOriginX,
		int sectionOriginY,
		int sectionOriginZ,
		float transformX,
		float transformY,
		float transformZ,
		String reason,
		long terrainExtractionFrameId,
		long rustEnqueueFrameId,
		long executionFrameId,
		long executionSubmissionId
	) {
		recordEvent(sectionPos, layer, sourceGeneration, meshGeneration, visibleGeneration, uploadGeneration, asset,
			sectionOriginX, sectionOriginY, sectionOriginZ, transformX, transformY, transformZ, reason,
			terrainExtractionFrameId, rustEnqueueFrameId, executionFrameId, executionSubmissionId,
			0L, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0, 0L, 0L, 0);
	}

	private static void recordEvent(
		long sectionPos,
		ChunkSectionLayer layer,
		long sourceGeneration,
		long meshGeneration,
		long visibleGeneration,
		long uploadGeneration,
		TerrainSectionAsset asset,
		int sectionOriginX,
		int sectionOriginY,
		int sectionOriginZ,
		float transformX,
		float transformY,
		float transformZ,
		String reason,
		long terrainExtractionFrameId,
		long rustEnqueueFrameId,
		long executionFrameId,
		long executionSubmissionId,
		long sortGeneration,
		double cameraX,
		double cameraY,
		double cameraZ,
		double sortOriginX,
		double sortOriginY,
		double sortOriginZ,
		int primitiveCount,
		long sortedIndexHash,
		long indexUploadGeneration,
		int translucentDrawOrder
	) {
		recordEvent(
			sectionPos,
			layer,
			sourceGeneration,
			meshGeneration,
			visibleGeneration,
			uploadGeneration,
			asset,
			sectionOriginX,
			sectionOriginY,
			sectionOriginZ,
			transformX,
			transformY,
			transformZ,
			reason,
			terrainExtractionFrameId,
			rustEnqueueFrameId,
			executionFrameId,
			executionSubmissionId,
			sortGeneration,
			cameraX,
			cameraY,
			cameraZ,
			sortOriginX,
			sortOriginY,
			sortOriginZ,
			primitiveCount,
			sortedIndexHash,
			indexUploadGeneration,
			translucentDrawOrder,
			"",
			0L,
			0L,
			0L,
			0L,
			""
		);
	}

	private static void recordEvent(
		long sectionPos,
		ChunkSectionLayer layer,
		long sourceGeneration,
		long meshGeneration,
		long visibleGeneration,
		long uploadGeneration,
		TerrainSectionAsset asset,
		int sectionOriginX,
		int sectionOriginY,
		int sectionOriginZ,
		float transformX,
		float transformY,
		float transformZ,
		String reason,
		long terrainExtractionFrameId,
		long rustEnqueueFrameId,
		long executionFrameId,
		long executionSubmissionId,
		long sortGeneration,
		double cameraX,
		double cameraY,
		double cameraZ,
		double sortOriginX,
		double sortOriginY,
		double sortOriginZ,
		int primitiveCount,
		long sortedIndexHash,
		long indexUploadGeneration,
		int translucentDrawOrder,
		String sorterType,
		long sourceSortedIndexHash,
		long rustCopiedSortedIndexHash,
		long sourceSortedIndexSampleHash,
		long rustCopiedSortedIndexSampleHash,
		String sortedIndexSample
	) {
		if (layer == ChunkSectionLayer.TRANSLUCENT
				&& "mesh-registered".equals(reason)
				&& asset != null
				&& !asset.translucentPrimitiveAccountingReason().isEmpty()) {
			reason = asset.translucentPrimitiveAccountingReason();
		}
		synchronized (RECENT_EVENTS) {
			TerrainDiagnosticEvent event = new TerrainDiagnosticEvent(
				diagnosticGameplayFrameId(terrainExtractionFrameId, rustEnqueueFrameId, executionFrameId),
				terrainExtractionFrameId,
				rustEnqueueFrameId,
				executionFrameId,
				executionSubmissionId,
				sectionPos,
				layer.name(),
				sourceGeneration,
				meshGeneration,
				visibleGeneration,
				uploadGeneration,
				asset == null ? 0L : asset.meshKey(),
				asset == null ? 0L : asset.contentHash(),
				asset == null ? 0 : asset.vertexCount(),
				asset == null ? 0 : asset.bufferVertexCapacity(),
				asset == null ? 0 : asset.vertexStride(),
				asset == null ? 0 : asset.indexCount(),
				asset == null ? -1 : asset.maxIndex(),
				asset == null ? 0 : asset.indexType(),
				asset == null ? 0 : asset.sectionCount(),
				sectionOriginX,
				sectionOriginY,
				sectionOriginZ,
				transformX,
				transformY,
				transformZ,
				asset == null ? 0.0F : asset.localMinX(),
				asset == null ? 0.0F : asset.localMinY(),
				asset == null ? 0.0F : asset.localMinZ(),
				asset == null ? 0.0F : asset.localMaxX(),
				asset == null ? 0.0F : asset.localMaxY(),
				asset == null ? 0.0F : asset.localMaxZ(),
				asset == null ? 0.0F : asset.uvMinU(),
				asset == null ? 0.0F : asset.uvMinV(),
				asset == null ? 0.0F : asset.uvMaxU(),
				asset == null ? 0.0F : asset.uvMaxV(),
				asset == null || asset.vertexPositionsFinite(),
				asset == null || asset.localBoundsValid(),
				asset == null || asset.uvBoundsValid(),
				asset == null || asset.indexRangeValid(),
				asset == null || asset.segmentLayoutValid(),
				asset == null || asset.sectionOriginValid(),
				asset == null || asset.indexOffsetAlignmentValid(),
				finiteBounds(
					(asset == null ? 0.0F : asset.localMinX()) + transformX,
					(asset == null ? 0.0F : asset.localMinY()) + transformY,
					(asset == null ? 0.0F : asset.localMinZ()) + transformZ,
					(asset == null ? 0.0F : asset.localMaxX()) + transformX,
					(asset == null ? 0.0F : asset.localMaxY()) + transformY,
					(asset == null ? 0.0F : asset.localMaxZ()) + transformZ
				),
				asset == null || asset.normalContractValid(),
				asset == null || asset.aoContractValid(),
				asset == null || asset.blockSkyLightContractValid(),
				asset == null || asset.topFaceShadeContractValid(),
				asset != null && asset.separateAoActive(),
				asset == null ? 0 : asset.separateAoVertexCount(),
				asset == null ? 1.0F : asset.minAo(),
				asset == null ? 1.0F : asset.maxAo(),
				asset == null ? 0 : asset.positiveYNormalSections(),
				asset == null ? 0 : asset.negativeYNormalSections(),
				asset == null ? 0 : asset.horizontalNormalSections(),
				sortGeneration,
				cameraX,
				cameraY,
				cameraZ,
				sortOriginX,
				sortOriginY,
				sortOriginZ,
				primitiveCount,
				sortedIndexHash,
				indexUploadGeneration,
				translucentDrawOrder,
				sorterType == null ? "" : sorterType,
				sourceSortedIndexHash,
				rustCopiedSortedIndexHash,
				sourceSortedIndexSampleHash,
				rustCopiedSortedIndexSampleHash,
				sortedIndexSample == null ? "" : sortedIndexSample,
				reason
			);
			if (RECENT_EVENTS.size() >= MAX_RECENT_EVENTS) {
				RECENT_EVENTS.removeFirst();
			}
			RECENT_EVENTS.addLast(event);
			if (reason.startsWith("lifecycle-")) {
				if (LIFECYCLE_EVENTS.size() >= MAX_LIFECYCLE_EVENTS) {
					LIFECYCLE_EVENTS.removeFirst();
				}
				LIFECYCLE_EVENTS.addLast(event);
			}
			if (layer == ChunkSectionLayer.TRANSLUCENT || reason.startsWith("translucent-")) {
				if (TRANSLUCENT_EVENTS.size() >= MAX_TRANSLUCENT_EVENTS) {
					TRANSLUCENT_EVENTS.removeFirst();
				}
				TRANSLUCENT_EVENTS.addLast(event);
			}
		}
	}

	private static long currentGameplayFrameId() {
		long semanticFrame = RustGalWorldPrimitiveRenderer.currentSemanticFrameSequence();
		if (semanticFrame > 0L) {
			return semanticFrame;
		}
		return Math.max(
			net.minecraft.client.dev.GraphicsFrameBenchmark.currentFrameIndex(),
			net.minecraft.client.dev.DeterministicCameraCapture.currentRenderedFrameIndex()
		);
	}

	/**
	 * The benchmark/capture clocks begin after ordinary world rendering has already
	 * submitted terrain. Keep those earlier diagnostic events frame-distinct so the
	 * geometry gate does not mistake consecutive normal frames for a duplicate draw.
	 */
	private static long diagnosticGameplayFrameId(long terrainExtractionFrameId, long rustEnqueueFrameId, long executionFrameId) {
		long frameId = currentGameplayFrameId();
		if (frameId > 0L) {
			return frameId;
		}
		if (executionFrameId > 0L) {
			return executionFrameId;
		}
		if (rustEnqueueFrameId > 0L) {
			return rustEnqueueFrameId;
		}
		return terrainExtractionFrameId;
	}

	private static double currentCameraX() {
		try {
			return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().x();
		} catch (RuntimeException error) {
			return 0.0D;
		}
	}

	private static double currentCameraY() {
		try {
			return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y();
		} catch (RuntimeException error) {
			return 0.0D;
		}
	}

	private static double currentCameraZ() {
		try {
			return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().z();
		} catch (RuntimeException error) {
			return 0.0D;
		}
	}

	private static int rowSlot(ChunkSectionLayer layer) {
		return switch (layer) {
			case SOLID -> 0;
			case CUTOUT_MIPPED -> 1;
			case TRANSLUCENT -> 2;
			default -> -1;
		};
	}

	private static void mirrorSectionAssetRow(LayerKey layerKey) {
		int slot = rowSlot(layerKey.layer());
		if (slot < 0) {
			return;
		}
		SECTION_ASSET_ROWS.compute(rowKey(layerKey.sectionPos()), (rowKey, row) -> {
			TerrainSectionAsset[] next = row == null ? new TerrainSectionAsset[3] : row.clone();
			next[slot] = SECTION_ASSETS.get(layerKey);
			return next[0] == null && next[1] == null && next[2] == null ? null : next;
		});
	}

	/** The row layer of each {@link #rowSlot}. */
	private static final ChunkSectionLayer[] ROW_LAYERS = {
		ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT_MIPPED, ChunkSectionLayer.TRANSLUCENT
	};

	private static int publicationFlags(ChunkSectionLayer layer, TerrainSectionAsset asset) {
		return terrainCameraSortRequested(layer, asset.translucentSortType()) ? RustTerrainPublication.FLAG_CAMERA_SORTED : 0;
	}

	/**
	 * Applies Rust's changed publication rows to {@code graph}; after a reset,
	 * or with {@code republish} for a new graph, every current row.
	 */
	static void drainGraphMeshRows(RustSectionGraph graph, boolean republish) {
		graph.syncPublishedMeshes(republish);
	}

	/**
	 * Whether this frame's static terrain comes from the Rust section graph's
	 * selection. Diagnostic, fault, reload and per-record frames, and frames
	 * whose readiness needs per-layer identity receipts, keep the Java path.
	 */
	static boolean wholeFrameTerrainSelectionEligible() {
		String fault = activeFault();
		return !resourceReloadStaging && fault.isEmpty() && !detailedTerrainDiagnosticsEnabled(fault)
			&& !Boolean.getBoolean("mattmc.dev.perRecordStaticTerrain")
			&& !net.minecraft.client.dev.GraphicsFrameBenchmark.needsSubmittedWorkIdentity()
			&& !net.minecraft.client.dev.DeterministicCameraCapture.needsSubmittedWorkIdentity()
			&& !net.sodium.client.render.StaticTerrainParityDiagnostics.isEnabled();
	}

	/** Whether the Rust selection must compute its layer fingerprint receipt. */
	static boolean selectionReceiptsRequested() {
		return terrainCountersEnabled("");
	}

	/** Depth policies of the solid, cutout and translucent rows. */
	static int[] selectionDepthPolicies() {
		return new int[] {
			terrainDepthPolicy(ChunkSectionLayer.SOLID),
			terrainDepthPolicy(ChunkSectionLayer.CUTOUT_MIPPED),
			terrainDepthPolicy(ChunkSectionLayer.TRANSLUCENT)
		};
	}

	static final int[] SELECTION_LAYER_ORDINALS = {
		ChunkSectionLayer.SOLID.ordinal(), ChunkSectionLayer.CUTOUT_MIPPED.ordinal(), ChunkSectionLayer.TRANSLUCENT.ordinal()
	};

	static final int MAX_SELECTION_SHADOW_CANDIDATES = MAX_SHADOW_CANDIDATE_SECTIONS;

	/**
	 * Enqueues a Rust terrain selection as the frame's static terrain: the
	 * same compact sections and casters the Java producer built, plus its
	 * producer receipts.
	 */
	static void enqueueWholeFrameTerrainSelection(RustSectionGraph.TerrainSelection selection, Camera camera) {
		double cameraX = camera.getPosition().x();
		double cameraY = camera.getPosition().y();
		double cameraZ = camera.getPosition().z();
		RustGalWorldPrimitiveRenderer.seedStaticTerrainFrameCamera(cameraX, cameraY, cameraZ);
		if (terrainCountersEnabled("") && selection.layerSubmissions() > 0) {
			visibleLayerProbes.addAndGet(selection.layerProbes());
			visibleLayerSubmissions.addAndGet(selection.layerSubmissions());
			long enqueued = rustEnqueueFrames.addAndGet(selection.layerSubmissions());
			long frameId = currentGameplayFrameId();
			lastVisibleSubmissionFrameId.set(frameId > 0L ? frameId : enqueued);
			currentFrameVisibleLayerSubmissions.set(selection.layerSubmissions());
			currentFrameVisibleFingerprint.set(selection.fingerprint());
		}
		RustGalWorldPrimitiveRenderer.enqueueStaticTerrainSelection(selection.sections(), selection.sectionCount(),
			selection.casters(), selection.casterCount(), cameraX, cameraY, cameraZ);
		if (selection.layerProbes() > 0) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordPhaseSample("sodium.terrain.setup", 1L);
			for (int layerPass = 0; layerPass < 3; layerPass++) {
				net.minecraft.client.dev.GraphicsFrameBenchmark.recordPhaseSample("sodium.terrain.draw", 1L);
			}
		}
	}

	/** Solid, cutout, and translucent assets of one section (slots per {@link #rowSlot}); null if none. */
	private static TerrainSectionAsset[] sectionAssetRow(long sectionPos) {
		return SECTION_ASSET_ROWS.get(rowKey(sectionPos));
	}

	/**
	 * Key of {@link #SECTION_ASSET_ROWS} and {@link #SECTION_SHADOW_IDENTITIES}.
	 * {@code Long.hashCode} of a packed section position keeps almost none of
	 * its X bits in the low bits a hash table indexes, so nearby sections share
	 * buckets; this bijective mix spreads them.
	 */
	private static long rowKey(long sectionPos) {
		return it.unimi.dsi.fastutil.HashCommon.mix(sectionPos);
	}

	private static int layerAddressHash(long sectionPos, ChunkSectionLayer layer) {
		return 31 * Long.hashCode(it.unimi.dsi.fastutil.HashCommon.mix(sectionPos)) + layer.ordinal();
	}

	private static TerrainSectionAsset sectionAsset(long sectionPos, ChunkSectionLayer layer) {
		int slot = rowSlot(layer);
		if (slot >= 0) {
			TerrainSectionAsset[] row = SECTION_ASSET_ROWS.get(rowKey(sectionPos));
			return row == null ? null : row[slot];
		}
		LayerLookup lookup = SECTION_ASSET_LOOKUP.get();
		lookup.set(sectionPos, layer);
		return SECTION_ASSETS.get(lookup);
	}

	private interface LayerAddress {
		long sectionPos();

		ChunkSectionLayer layer();
	}

	private record LayerKey(long sectionPos, ChunkSectionLayer layer) implements LayerAddress {
		@Override
		public boolean equals(Object other) {
			return this == other || other instanceof LayerAddress address
				&& sectionPos == address.sectionPos() && layer == address.layer();
		}

		@Override
		public int hashCode() {
			return layerAddressHash(sectionPos, layer);
		}
	}

	/** Never inserted into a map; get(Object) consumes its value synchronously before the next set. */
	private static final class LayerLookup implements LayerAddress {
		private long sectionPos;
		private ChunkSectionLayer layer;

		void set(long sectionPos, ChunkSectionLayer layer) {
			this.sectionPos = sectionPos;
			this.layer = layer;
		}

		@Override
		public long sectionPos() {
			return sectionPos;
		}

		@Override
		public ChunkSectionLayer layer() {
			return layer;
		}

		@Override
		public boolean equals(Object other) {
			return this == other || other instanceof LayerAddress address
				&& sectionPos == address.sectionPos() && layer == address.layer();
		}

		@Override
		public int hashCode() {
			return layerAddressHash(sectionPos, layer);
		}
	}

	private record TerrainAssetIdentity(LayerKey layerKey, TerrainSectionAsset asset) {
	}

	private record TerrainSectionAsset(
			long meshKey,
			long meshGeneration,
			long contentHash,
			long initialSortGeneration,
			int vertexCount,
		int bufferVertexCapacity,
		int vertexStride,
		int indexCount,
		int maxIndex,
		int indexType,
		int sectionCount,
		float localMinX,
		float localMinY,
		float localMinZ,
		float localMaxX,
		float localMaxY,
		float localMaxZ,
		float uvMinU,
		float uvMinV,
		float uvMaxU,
		float uvMaxV,
		boolean vertexPositionsFinite,
		boolean localBoundsValid,
		boolean uvBoundsValid,
		boolean indexRangeValid,
		boolean segmentLayoutValid,
		boolean sectionOriginValid,
		boolean indexOffsetAlignmentValid,
		boolean normalContractValid,
		boolean aoContractValid,
		boolean blockSkyLightContractValid,
		boolean topFaceShadeContractValid,
		boolean separateAoActive,
		int separateAoVertexCount,
		float minAo,
		float maxAo,
		int positiveYNormalSections,
		int negativeYNormalSections,
		int horizontalNormalSections,
		int sectionOriginX,
		int sectionOriginY,
		int sectionOriginZ,
		SortType translucentSortType,
		String translucentPrimitiveAccountingReason,
		int unsupportedPrimitiveCount,
		int[] translucentSourceSegmentQuadCounts,
		int translucentIndexBytes,
		long translucentIndexHash,
			VulkanicGalBridge.WorldMeshAssetRecord asset,
			// The key assembly staged the geometry under (differs from meshKey
			// only under the mesh-key-collision fault).
			long stagedMeshKey
		) {
		TerrainSectionAsset releaseCpuPayload() {
			return new TerrainSectionAsset(
				meshKey, meshGeneration, contentHash, initialSortGeneration, vertexCount,
				bufferVertexCapacity, vertexStride, indexCount, maxIndex, indexType, sectionCount,
				localMinX, localMinY, localMinZ, localMaxX, localMaxY, localMaxZ,
				uvMinU, uvMinV, uvMaxU, uvMaxV, vertexPositionsFinite, localBoundsValid,
				uvBoundsValid, indexRangeValid, segmentLayoutValid, sectionOriginValid,
				indexOffsetAlignmentValid, normalContractValid, aoContractValid,
				blockSkyLightContractValid, topFaceShadeContractValid, separateAoActive,
				separateAoVertexCount, minAo, maxAo, positiveYNormalSections,
				negativeYNormalSections, horizontalNormalSections, sectionOriginX,
				sectionOriginY, sectionOriginZ, translucentSortType, translucentPrimitiveAccountingReason,
				unsupportedPrimitiveCount, translucentSourceSegmentQuadCounts, translucentIndexBytes,
				translucentIndexHash, null, stagedMeshKey
			);
		}
	}

		private record TranslucentSortSnapshot(
			long meshGeneration,
			long sortGeneration,
			int indexType,
			int indexBytes,
			long indexHash
		) {
		}

	public record TerrainDiagnosticEvent(
		long gameplayFrameId,
		long terrainExtractionFrameId,
		long rustEnqueueFrameId,
		long executionFrameId,
		long executionSubmissionId,
		long sectionPos,
		String layer,
		long sourceGeneration,
		long meshGeneration,
		long visibleGeneration,
		long uploadGeneration,
		long meshKey,
		long contentHash,
		int vertexCount,
		int bufferVertexCapacity,
		int vertexStride,
		int indexCount,
		int maxIndex,
		int indexType,
		int sectionCount,
		int sectionOriginX,
		int sectionOriginY,
		int sectionOriginZ,
		float transformX,
		float transformY,
		float transformZ,
		float localMinX,
		float localMinY,
		float localMinZ,
		float localMaxX,
		float localMaxY,
		float localMaxZ,
		float uvMinU,
		float uvMinV,
		float uvMaxU,
		float uvMaxV,
		boolean vertexPositionsFinite,
		boolean localBoundsValid,
		boolean uvBoundsValid,
		boolean indexRangeValid,
		boolean segmentLayoutValid,
		boolean sectionOriginValid,
		boolean indexOffsetAlignmentValid,
		boolean cameraBoundsFinite,
		boolean normalContractValid,
		boolean aoContractValid,
		boolean blockSkyLightContractValid,
		boolean topFaceShadeContractValid,
		boolean separateAoActive,
		int separateAoVertexCount,
		float minAo,
		float maxAo,
			int positiveYNormalSections,
			int negativeYNormalSections,
			int horizontalNormalSections,
			long sortGeneration,
			double cameraX,
			double cameraY,
			double cameraZ,
			double sortOriginX,
			double sortOriginY,
			double sortOriginZ,
			int primitiveCount,
			long sortedIndexHash,
			long indexUploadGeneration,
			int translucentDrawOrder,
			String sorterType,
			long sourceSortedIndexHash,
			long rustCopiedSortedIndexHash,
			long sourceSortedIndexSampleHash,
			long rustCopiedSortedIndexSampleHash,
			String sortedIndexSample,
			String reason
		) {
		}

	public record TerrainDiagnostics(
		int cachedLayerAssets,
		int activeTerrainLayers,
		int activeSectionAssets,
		long currentFrameVisibleLayerSubmissions,
		long currentFrameVisibleFingerprint,
		long atlasGeneration,
		long registeredAtlasGeneration,
		int activeNativeVertexStride,
		int expectedNativeVertexStride,
		long acceptedBuildOutputs,
		long skippedRouteBuildOutputs,
		long skippedUnsupportedAnimatedSections,
		long skippedUnsupportedFluidTranslucentSections,
		long acceptedWaterAnimatedSections,
		long unsupportedFluidRejectedSections,
		long skippedEmptyLayers,
		long registeredMeshes,
		long registeredTranslucentSorts,
		long registeredTranslucentSortBytes,
		long translucentSortGenerations,
		long texturePayloadUpdates,
		long texturePayloadUpdateBytes,
		long atlasTextureOnlyUpdates,
		long atlasMissingPayloadUpdates,
		long atlasMalformedPayloadUpdates,
		long atlasPartialPayloadUpdates,
		long removedLayers,
		long visibleLayerProbes,
		long visibleLayerSubmissions,
		long failedLayerSubmissions,
			long invalidations,
			long terrainExtractionFrames,
			long rustEnqueueFrames,
			List<TerrainDiagnosticEvent> lifecycleEvents,
			List<TerrainDiagnosticEvent> translucentEvents,
			List<TerrainDiagnosticEvent> recentEvents
		) {
			public TerrainDiagnostics {
				lifecycleEvents = Collections.unmodifiableList(new ArrayList<>(lifecycleEvents));
				translucentEvents = Collections.unmodifiableList(new ArrayList<>(translucentEvents));
				recentEvents = Collections.unmodifiableList(new ArrayList<>(recentEvents));
			}
		}

	public record TerrainPerformanceCounters(
		int cachedLayerAssets,
		int activeTerrainLayers,
		int activeSectionAssets,
		long currentFrameVisibleFingerprint,
		long atlasGeneration,
		long acceptedBuildOutputs,
		long skippedUnsupportedAnimatedSections,
		long skippedEmptyLayers,
		long registeredMeshes,
		long texturePayloadUpdates,
		long texturePayloadUpdateBytes,
		long removedLayers,
		long failedLayerSubmissions,
		long invalidations,
		long terrainExtractionFrames
	) {
	}

	public record TerrainLayerSnapshot(
		long sectionPos,
		String layer,
		long meshKey,
		long meshGeneration,
		long contentHash,
		int vertexCount,
		int indexBytes,
		int sectionCount
	) {
	}

	public record StaticTerrainExecutionSnapshot(
		long frameId,
		long submissionId,
		long instances
	) {
		public boolean executedAfter(long submissionBaseline) {
			return instances > 0L && submissionId > submissionBaseline;
		}
	}

	public record TerrainAtlasReceipt(
		boolean available,
		String status,
		int width,
		int height,
		long copiedAtlasHash,
		List<TerrainAtlasSpriteReceipt> sprites
	) {
		static TerrainAtlasReceipt unavailable(String reason) {
			return new TerrainAtlasReceipt(false, reason == null ? "unknown atlas receipt failure" : reason, 0, 0, 0L, List.of());
		}

		public boolean allSpritesMatch() {
			return available && !sprites.isEmpty() && sprites.stream().allMatch(TerrainAtlasSpriteReceipt::matchesSource);
		}
	}

	public record TerrainAtlasSpriteReceipt(
		String identity,
		int x,
		int y,
		int width,
		int height,
		long sourceHash,
		long copiedHash,
		String directSampleIdentity,
		String mirroredVSampleIdentity,
		int sampleX,
		int sampleY,
		int mirroredSampleY,
		boolean matchesSource,
		String status
	) {
		static TerrainAtlasSpriteReceipt missing(String identity) {
			return new TerrainAtlasSpriteReceipt(
				identity,
				0,
				0,
				0,
				0,
				0L,
				0L,
				"<missing>",
				"<missing>",
				0,
				0,
				0,
				false,
				"missing"
			);
		}
	}

	/** Test-only semantic probe; no atlas object or native handle crosses this boundary. */
	public record TerrainTextureProbe(BlockPos position, List<ResourceLocation> allowedSprites) {
		public TerrainTextureProbe {
			allowedSprites = List.copyOf(allowedSprites == null ? List.of() : allowedSprites);
		}
	}

	public record TerrainTextureProbeReceipt(
		boolean matched,
		String status,
		List<TerrainTextureProbeResult> probes
	) {
	}

	public record TerrainTextureProbeResult(
		BlockPos position,
		List<ResourceLocation> allowedSprites,
		int matchingQuads,
		int mismatchedQuads,
		boolean matched,
		String status,
		List<TerrainTextureProbeObservation> observations
	) {
		public TerrainTextureProbeResult {
			allowedSprites = List.copyOf(allowedSprites);
			observations = List.copyOf(observations);
		}
	}

	/** Bounded capture-only UV evidence for one copied terrain quad. */
	public record TerrainTextureProbeObservation(
		long sectionPos,
		String layer,
		int quadIndex,
		boolean expectedSprite,
		String atlasIdentity,
		float atlasU,
		float atlasV
	) {
	}

	private record AtlasUvRegion(float u0, float v0, float u1, float v1) {
		boolean contains(float u, float v) {
			final float epsilon = 0.0001F;
			return Float.isFinite(u) && Float.isFinite(v)
				&& u >= u0 - epsilon && u <= u1 + epsilon
				&& v >= v0 - epsilon && v <= v1 + epsilon;
		}
	}

}
