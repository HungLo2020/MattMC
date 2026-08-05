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
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.render.chunk.lists.ChunkRenderList;
import net.sodium.client.render.chunk.lists.SortedRenderLists;
import net.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.render.chunk.translucent_sorting.data.SharedIndexSorter;
import net.sodium.client.render.chunk.translucent_sorting.data.Sorter;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.sodium.client.util.iterator.ByteIterator;
import net.sodium.client.util.NativeBuffer;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import net.logging.LogUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RustGalTerrainRenderer {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int INDEX_TYPE_U16 = 1;
	private static final int INDEX_TYPE_U32 = 2;
	private static final int POSITION_MAX_VALUE = 1 << 20;
	private static final int TEXTURE_MAX_VALUE = 1 << 15;
	private static final int COMPACT_PREFIX_STRIDE = 20;
	private static final int POSITION_OFFSET = 0;
	private static final int COLOR_OFFSET = 8;
	private static final int TEXTURE_OFFSET = 12;
	private static final int LIGHT_MATERIAL_OFFSET = 16;
	private static final float SECTION_LOCAL_MIN = -8.01F;
	private static final float SECTION_LOCAL_MAX = 24.01F;
	private static final int MAX_RECENT_EVENTS = 8192;
	private static final int MAX_LIFECYCLE_EVENTS = 256;
	private static final int MAX_TRANSLUCENT_EVENTS = 4096;
	private static final String STATIC_TERRAIN_SCENARIO_PROPERTY = "mattmc.dev.rustGalStaticTerrain.scenario";
	private static final String FAULT_PROPERTY = "mattmc.dev.rustGalStaticTerrain.fault";
	private static final Map<LayerKey, TerrainSectionAsset> SECTION_ASSETS = new ConcurrentHashMap<>();
	private static final ArrayDeque<TerrainDiagnosticEvent> RECENT_EVENTS = new ArrayDeque<>(MAX_RECENT_EVENTS);
	private static final ArrayDeque<TerrainDiagnosticEvent> LIFECYCLE_EVENTS = new ArrayDeque<>(MAX_LIFECYCLE_EVENTS);
	private static final ArrayDeque<TerrainDiagnosticEvent> TRANSLUCENT_EVENTS = new ArrayDeque<>(MAX_TRANSLUCENT_EVENTS);
	private static volatile long atlasGeneration;
	private static volatile long registeredAtlasGeneration;
	private static volatile byte[] atlasPayload;
	private static volatile FluidSpriteAsset waterStillAsset;
	private static volatile FluidSpriteAsset waterFlowAsset;
	private static volatile FluidSpriteAsset waterOverlayAsset;
	private static final AtomicLong acceptedBuildOutputs = new AtomicLong();
	private static final AtomicLong skippedRouteBuildOutputs = new AtomicLong();
	private static final AtomicLong skippedUnsupportedAnimatedSections = new AtomicLong();
	private static final AtomicLong skippedUnsupportedFluidTranslucentSections = new AtomicLong();
	private static final AtomicLong acceptedWaterAnimatedSections = new AtomicLong();
	private static final AtomicLong unsupportedFluidOmittedSections = new AtomicLong();
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
	private static final AtomicLong invalidations = new AtomicLong();
	private static final AtomicLong terrainExtractionFrames = new AtomicLong();
	private static final AtomicLong rustEnqueueFrames = new AtomicLong();
	private static final AtomicLong translucentSortGenerations = new AtomicLong();
	private static volatile TerrainSectionAsset lastWorldUnloadAsset;
	private static volatile long lastWorldUnloadSectionPos;
	private static volatile ChunkSectionLayer lastWorldUnloadLayer = ChunkSectionLayer.SOLID;

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
		byte[] pngBytes
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
			return new VulkanicGalBridge.WorldMeshTextureAssetRecord(
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
			new byte[] { 1 }
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
			new byte[] { 2 }
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
			new byte[] { 3 }
		);
	}

	private RustGalTerrainRenderer() {
	}

	public static void acceptChunkBuildOutput(ChunkBuildOutput output) {
		if (!WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan()) {
			skippedRouteBuildOutputs.incrementAndGet();
			return;
		}
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
		acceptLayer(output, DefaultTerrainRenderPasses.SOLID, ChunkSectionLayer.SOLID, extractionFrameId);
		acceptLayer(output, DefaultTerrainRenderPasses.CUTOUT, ChunkSectionLayer.CUTOUT_MIPPED, extractionFrameId);
		acceptLayer(output, DefaultTerrainRenderPasses.TRANSLUCENT, ChunkSectionLayer.TRANSLUCENT, extractionFrameId);
		recordTranslucentSortData(output);
	}

	private static void recordTranslucentSortData(ChunkBuildOutput output) {
		if (output == null || output.translucentData == null || !isStaticTerrainTranslucentScenario()) {
			return;
		}
		TerrainSectionAsset asset = SECTION_ASSETS.get(new LayerKey(output.render.getPosition().asLong(), ChunkSectionLayer.TRANSLUCENT));
		if (asset == null) {
			return;
		}
		String reason = "translucent-sort-data:"
			+ output.translucentData.getSortType().name().toLowerCase(Locale.ROOT)
			+ ":"
			+ output.translucentData.getClass().getSimpleName();
		recordEvent(
			output.render.getPosition().asLong(),
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
		long sectionPos = output.render.getPosition().asLong();
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

	public static void acceptChunkSortOutput(ChunkSortOutput output) {
		if (!WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan()) {
			return;
		}
		if (output == null || output.isReusingUploadedIndexData()) {
			return;
		}
		TerrainSectionAsset asset = SECTION_ASSETS.get(new LayerKey(output.render.getPosition().asLong(), ChunkSectionLayer.TRANSLUCENT));
		if (asset == null) {
			return;
		}
		byte[] indexBytes = copySorterIndexBytes(output.getSorter());
		if (indexBytes.length == 0) {
			recordEvent(
				output.render.getPosition().asLong(),
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
				"translucent-sort-missing",
				0L,
				0L,
				0L,
				0L,
				0L,
				currentCameraX(),
				currentCameraY(),
				currentCameraZ(),
				output.render.getOriginX() + 8.0D,
				output.render.getOriginY() + 8.0D,
				output.render.getOriginZ() + 8.0D,
				Math.max(0, asset.indexCount() / 6),
				0L,
				0L,
				0
			);
			return;
		}
		long indexGeneration = translucentSortGenerations.incrementAndGet();
		long sortedIndexHash = sortedIndexHash(indexBytes);
		long sortedIndexSampleHash = sortedIndexSampleHash(indexBytes);
		String sortedIndexSample = sortedIndexSample(indexBytes, INDEX_TYPE_U32, 12);
		String sorterType = translucentSorterType(output.getSorter());
		recordTranslucentSortPayloadEvent(
			output.render.getPosition().asLong(),
			output.submitTime,
			asset,
			output.render.getOriginX(),
			output.render.getOriginY(),
			output.render.getOriginZ(),
			"translucent-source-sort",
			indexGeneration,
			currentCameraX(),
			currentCameraY(),
			currentCameraZ(),
			output.render.getOriginX() + 8.0D,
			output.render.getOriginY() + 8.0D,
			output.render.getOriginZ() + 8.0D,
			Math.max(0, indexBytes.length / (Integer.BYTES * 6)),
			sortedIndexHash,
			indexGeneration,
			sorterType,
			sortedIndexHash,
			0L,
			sortedIndexSampleHash,
			0L,
			sortedIndexSample
		);
		RustGalWorldPrimitiveRenderer.registerStaticTerrainSortedIndex(new VulkanicGalBridge.WorldMeshSortedIndexRecord(
			asset.meshKey(),
			asset.meshGeneration(),
			indexGeneration,
			INDEX_TYPE_U32,
			indexBytes
		));
		registeredTranslucentSorts.incrementAndGet();
		registeredTranslucentSortBytes.addAndGet(indexBytes.length);
		recordEvent(
			output.render.getPosition().asLong(),
			ChunkSectionLayer.TRANSLUCENT,
			output.submitTime,
			asset.meshGeneration(),
			indexGeneration,
			atlasGeneration,
			asset,
			output.render.getOriginX(),
			output.render.getOriginY(),
			output.render.getOriginZ(),
			0.0F,
			0.0F,
			0.0F,
			"translucent-sort-registered"
				+ ":authority=sodium-source-payload"
				+ ":sourceHash=" + sortedIndexHash
				+ ":copiedHash=" + sortedIndexHash
				+ ":sourceSampleHash=" + sortedIndexSampleHash
				+ ":copiedSampleHash=" + sortedIndexSampleHash
				+ ":sample=" + sortedIndexSample
				+ ":sorterType=" + sorterType,
			0L,
			0L,
			0L,
			0L,
			indexGeneration,
			currentCameraX(),
			currentCameraY(),
			currentCameraZ(),
			output.render.getOriginX() + 8.0D,
			output.render.getOriginY() + 8.0D,
			output.render.getOriginZ() + 8.0D,
			Math.max(0, indexBytes.length / (Integer.BYTES * 6)),
			sortedIndexHash,
			indexGeneration,
			0,
			sorterType,
			sortedIndexHash,
			sortedIndexHash,
			sortedIndexSampleHash,
			sortedIndexSampleHash,
			sortedIndexSample
		);
	}

	static void recordTranslucentSortCopyRegistered(VulkanicGalBridge.WorldMeshSortedIndexRecord sortedIndex) {
		if (sortedIndex == null) {
			return;
		}
		LayerKey layerKey = null;
		TerrainSectionAsset asset = null;
		for (Map.Entry<LayerKey, TerrainSectionAsset> entry : SECTION_ASSETS.entrySet()) {
			if (entry.getKey().layer() == ChunkSectionLayer.TRANSLUCENT && entry.getValue().meshKey() == sortedIndex.meshKey()) {
				layerKey = entry.getKey();
				asset = entry.getValue();
				break;
			}
		}
		if (asset == null || layerKey == null) {
			return;
		}
		long hash = sortedIndexHash(sortedIndex.indexBytes());
		long sampleHash = sortedIndexSampleHash(sortedIndex.indexBytes());
		recordTranslucentSortPayloadEvent(
			layerKey.sectionPos(),
			0L,
			asset,
			asset.sectionOriginX(),
			asset.sectionOriginY(),
			asset.sectionOriginZ(),
			"translucent-rust-sort-copy",
			sortedIndex.indexGeneration(),
			currentCameraX(),
			currentCameraY(),
			currentCameraZ(),
			asset.sectionOriginX() + 8.0D,
			asset.sectionOriginY() + 8.0D,
			asset.sectionOriginZ() + 8.0D,
			Math.max(0, sortedIndex.indexBytes().length / (Integer.BYTES * 6)),
			hash,
			sortedIndex.indexGeneration(),
			"rust-route-cache",
			0L,
			hash,
			0L,
			sampleHash,
			sortedIndexSample(sortedIndex.indexBytes(), sortedIndex.indexType(), 12)
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

	public static void enqueueVisibleTerrain(SortedRenderLists renderLists, Camera camera, int viewportWidth, int viewportHeight) {
		if (!WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan() || renderLists == null || camera == null) {
			return;
		}
		Set<VisibleSubmitKey> visibleSubmissions = new HashSet<>();
		int submitted = 0;
		Iterator<ChunkRenderList> iterator = renderLists.iterator();
		while (iterator.hasNext()) {
			ChunkRenderList renderList = iterator.next();
			ByteIterator sectionIterator = renderList.sectionsWithGeometryIterator(false);
			if (sectionIterator == null) {
				continue;
			}
			while (sectionIterator.hasNext()) {
				RenderSection section = renderList.getRegion().getSection(sectionIterator.nextByteAsInt());
				if (section == null) {
					continue;
				}
				if (enqueueSectionLayer(section, ChunkSectionLayer.SOLID, camera, viewportWidth, viewportHeight, 0, visibleSubmissions)) {
					submitted++;
				}
				if (enqueueSectionLayer(section, ChunkSectionLayer.CUTOUT_MIPPED, camera, viewportWidth, viewportHeight, 0, visibleSubmissions)) {
					submitted++;
				}
			}
		}
		int translucentDrawOrder = 0;
		Iterator<ChunkRenderList> translucentIterator = renderLists.iterator();
		while (translucentIterator.hasNext()) {
			ChunkRenderList renderList = translucentIterator.next();
			ByteIterator sectionIterator = renderList.sectionsWithGeometryIterator(true);
			if (sectionIterator == null) {
				continue;
			}
			while (sectionIterator.hasNext()) {
				RenderSection section = renderList.getRegion().getSection(sectionIterator.nextByteAsInt());
				if (section == null) {
					continue;
				}
				if (enqueueSectionLayer(section, ChunkSectionLayer.TRANSLUCENT, camera, viewportWidth, viewportHeight, translucentDrawOrder++, visibleSubmissions)) {
					submitted++;
				}
			}
		}
		if (submitted > 0) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordSubmittedWorkIdentity("static-terrain", "rust-vulkan-whole-frame:visible-terrain");
			net.minecraft.client.dev.DeterministicCameraCapture.recordSubmittedWorkIdentity("static-terrain", "rust-vulkan-whole-frame:visible-terrain");
		}
	}

	public static void invalidateForResourceReload() {
		SECTION_ASSETS.clear();
		synchronized (RustGalTerrainRenderer.class) {
			atlasPayload = null;
			atlasGeneration++;
			registeredAtlasGeneration = 0L;
		}
		invalidations.incrementAndGet();
			recordEvent(0L, ChunkSectionLayer.SOLID, 0L, 0L, 0L, atlasGeneration, null, 0, 0, 0, 0.0F, 0.0F, 0.0F, "resource-reload");
	}

	public static void invalidateForWorldUnload() {
		for (LayerKey key : List.copyOf(SECTION_ASSETS.keySet())) {
			removeLayer(key.sectionPos(), key.layer(), "world-unload");
		}
		invalidations.incrementAndGet();
			recordEvent(0L, ChunkSectionLayer.SOLID, 0L, 0L, 0L, atlasGeneration, null, 0, 0, 0, 0.0F, 0.0F, 0.0F, "world-unload");
	}

	public static void removeSection(int x, int y, int z, String reason) {
		long sectionPos = net.minecraft.core.SectionPos.asLong(x, y, z);
		removeLayer(sectionPos, ChunkSectionLayer.SOLID, reason);
		removeLayer(sectionPos, ChunkSectionLayer.CUTOUT_MIPPED, reason);
		removeLayer(sectionPos, ChunkSectionLayer.TRANSLUCENT, reason);
	}

	public static TerrainDiagnostics diagnosticsSnapshot() {
		synchronized (RECENT_EVENTS) {
			long currentFrameId = currentGameplayFrameId();
			long currentVisibleSubmissions =
				lastVisibleSubmissionFrameId.get() == currentFrameId ? currentFrameVisibleLayerSubmissions.get() : 0L;
			return new TerrainDiagnostics(
				SECTION_ASSETS.size(),
				SECTION_ASSETS.size(),
				SECTION_ASSETS.size(),
				currentVisibleSubmissions,
				atlasGeneration,
				registeredAtlasGeneration,
				activeTerrainVertexStride(),
				activeTerrainVertexStride(),
				acceptedBuildOutputs.get(),
				skippedRouteBuildOutputs.get(),
				skippedUnsupportedAnimatedSections.get(),
				skippedUnsupportedFluidTranslucentSections.get(),
				acceptedWaterAnimatedSections.get(),
				unsupportedFluidOmittedSections.get(),
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
		TerrainSectionAsset asset = SECTION_ASSETS.get(new LayerKey(sectionPos, layer));
		if (asset == null) {
			return new TerrainLayerSnapshot(sectionPos, layer.name(), 0L, 0L, 0, 0, 0);
		}
		return new TerrainLayerSnapshot(
			sectionPos,
			layer.name(),
			asset.meshKey(),
			asset.meshGeneration(),
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
		TerrainSectionAsset asset = SECTION_ASSETS.get(new LayerKey(sectionPos, layer));
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
		TerrainSectionAsset asset = SECTION_ASSETS.get(new LayerKey(sectionPos, ChunkSectionLayer.TRANSLUCENT));
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
		if (!WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan()) {
			return;
		}
		byte[] copied = payload == null ? new byte[0] : payload.clone();
		long generation;
		synchronized (RustGalTerrainRenderer.class) {
			atlasPayload = copied;
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

	public static void recordExecutedStaticTerrainInstances(
		List<VulkanicGalBridge.WorldMeshInstanceRecord> instances,
		long frameId,
		long submissionId
	) {
		if (instances == null || instances.isEmpty()) {
			return;
		}
		for (VulkanicGalBridge.WorldMeshInstanceRecord instance : instances) {
			TerrainSectionAsset asset = null;
			LayerKey layerKey = null;
			for (Map.Entry<LayerKey, TerrainSectionAsset> entry : SECTION_ASSETS.entrySet()) {
				TerrainSectionAsset candidate = entry.getValue();
				if (candidate.meshKey() == instance.meshKey()) {
					asset = candidate;
					layerKey = entry.getKey();
					break;
				}
			}
			if (asset == null || layerKey == null) {
				continue;
			}
			TranslucentSortSnapshot sortedIndex =
				layerKey.layer() == ChunkSectionLayer.TRANSLUCENT ? currentTranslucentSortSnapshot(asset) : null;
			long sortGeneration = sortedIndex == null ? 0L : sortedIndex.sortGeneration();
			long sortedIndexHash = sortedIndex == null ? 0L : sortedIndex.indexHash();
			net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainExecution(
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
			recordEvent(
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
				0.0D,
				0.0D,
				0.0D,
				asset.sectionOriginX() + 8.0D,
				asset.sectionOriginY() + 8.0D,
				asset.sectionOriginZ() + 8.0D,
				layerKey.layer() == ChunkSectionLayer.TRANSLUCENT ? Math.max(0, asset.indexCount() / 6) : 0,
				sortedIndexHash,
				sortGeneration,
				0
			);
		}
	}

	private static void acceptLayer(ChunkBuildOutput output, TerrainRenderPass pass, ChunkSectionLayer layer, long extractionFrameId) {
		BuiltSectionMeshParts mesh = output.getMesh(pass);
		if (mesh == null || mesh.getVertexData().getLength() == 0) {
			skippedEmptyLayers.incrementAndGet();
			net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainAdmission(
				output, layer.name(), "source-layer-empty"
			);
			removeLayer(output.render.getPosition().asLong(), layer, "empty-layer");
			return;
		}
			try {
				TerrainSectionAsset asset = decodeMesh(output, mesh, layer);
				net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainAppearanceCopy(
					output,
					layer.name(),
					asset.meshKey(),
					asset.meshGeneration(),
					asset.asset().vertices()
				);
				if (layer == ChunkSectionLayer.TRANSLUCENT && asset.unsupportedPrimitiveCount() > 0) {
					unsupportedFluidOmittedSections.incrementAndGet();
					recordEvent(
						output.render.getPosition().asLong(),
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
						"unsupported-fluid-omitted",
						extractionFrameId,
						0L,
						0L,
						0L
					);
				}
				SECTION_ASSETS.put(new LayerKey(output.render.getPosition().asLong(), layer), asset);
				net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainAdmission(
					output, layer.name(), "asset-registered"
				);
				RustGalWorldPrimitiveRenderer.registerStaticTerrainMeshAsset(asset.asset(), atlasTextureUpdatePayload());
				registeredMeshes.incrementAndGet();
				recordEvent(
					output.render.getPosition().asLong(),
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
						layer == ChunkSectionLayer.TRANSLUCENT ? sortedIndexHash(asset.asset().indexBytes()) : 0L,
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
				removeLayer(output.render.getPosition().asLong(), layer, "decode-failed");
		}
	}

	private static TerrainSectionAsset decodeMesh(ChunkBuildOutput output, BuiltSectionMeshParts mesh, ChunkSectionLayer layer) {
		ByteBuffer buffer = mesh.getVertexData().getDirectBuffer().duplicate().order(ByteOrder.nativeOrder());
		int vertexStride = activeTerrainVertexStride();
		boolean separateAo = WorldRenderingSettings.INSTANCE.shouldUseSeparateAo();
		String fault = activeFault();
		if (vertexStride < COMPACT_PREFIX_STRIDE) {
			throw new IllegalArgumentException("static terrain vertex stride " + vertexStride + " is smaller than compact prefix " + COMPACT_PREFIX_STRIDE);
		}
		if (buffer.remaining() % vertexStride != 0) {
			throw new IllegalArgumentException("static terrain vertex buffer length is not aligned to stride " + vertexStride);
		}
		int bufferVertexCapacity = buffer.remaining() / vertexStride;
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
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = new ArrayList<>(vertexCount);
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		float minU = Float.POSITIVE_INFINITY;
		float minV = Float.POSITIVE_INFINITY;
		float maxU = Float.NEGATIVE_INFINITY;
		float maxV = Float.NEGATIVE_INFINITY;
		int separateAoVertexCount = 0;
		float minAo = 1.0F;
		float maxAo = 0.0F;
		boolean aoContractValid = true;
		boolean blockSkyLightContractValid = true;
		for (int vertexIndex = 0; vertexIndex < vertexCount; vertexIndex++) {
			int offset = vertexIndex * vertexStride;
			int positionHi = buffer.getInt(offset + POSITION_OFFSET);
			int positionLo = buffer.getInt(offset + POSITION_OFFSET + 4);
			int compactColor = buffer.getInt(offset + COLOR_OFFSET);
			int color = decodeCompactTerrainColorForRust(compactColor, separateAo, "inverted-ao".equals(fault), "doubled-face-shade".equals(fault));
			int texture = buffer.getInt(offset + TEXTURE_OFFSET);
			int lightMaterial = buffer.getInt(offset + LIGHT_MATERIAL_OFFSET);
			float ao = separateAo ? ((compactColor >>> 24) & 0xff) / 255.0F : 1.0F;
			if ("inverted-ao".equals(fault)) {
				ao = 1.0F - ao;
			}
			if (separateAo) {
				separateAoVertexCount++;
				minAo = Math.min(minAo, ao);
				maxAo = Math.max(maxAo, ao);
				if (((color >>> 24) & 0xff) != 0xff) {
					aoContractValid = false;
				}
			}
			if ("inverted-ao".equals(fault) || "doubled-face-shade".equals(fault)) {
				aoContractValid = false;
			}
			if ("swapped-block-sky-light".equals(fault)) {
				blockSkyLightContractValid = false;
			}
			float x = decodePosition(positionHi, positionLo, 0);
			float y = decodePosition(positionHi, positionLo, 1);
			float z = decodePosition(positionHi, positionLo, 2);
			float u = decodeTexture(texture & 0xffff);
			float v = decodeTexture((texture >>> 16) & 0xffff);
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
			maxZ = Math.max(maxZ, z);
			minU = Math.min(minU, u);
			minV = Math.min(minV, v);
			maxU = Math.max(maxU, u);
			maxV = Math.max(maxV, v);
			vertices.add(new VulkanicGalBridge.WorldMeshVertexRecord(
				x,
				y,
				z,
				u,
				v,
				u,
				v,
				(lightMaterial >>> 16) & 0xff,
				(lightMaterial >>> 16) & 0xff,
				color,
				0,
				decodeLight(lightMaterial, "swapped-block-sky-light".equals(fault))
			));
		}
		List<Integer> indices = new ArrayList<>(Math.max(6, vertexCount / 4 * 6));
		List<VulkanicGalBridge.WorldMeshSectionRecord> sections = new ArrayList<>();
		int cursor = 0;
		int maxIndex = -1;
		int positiveYNormalSections = 0;
		int negativeYNormalSections = 0;
		int horizontalNormalSections = 0;
		boolean normalContractValid = true;
		boolean topFaceShadeContractValid = true;
		for (int i = 0; i < vertexSegments.length; i += 2) {
			int segmentVertexCount = vertexSegments[i];
			if (segmentVertexCount <= 0) {
				continue;
			}
			if (cursor + segmentVertexCount > vertexCount) {
				throw new IllegalArgumentException("static terrain vertex segments exceed vertex payload");
			}
			int firstIndex = indices.size();
			int facing = vertexSegments[i + 1];
			int normalPacked = normalForSegment(vertices, cursor, segmentVertexCount, facing);
			if ("inverted-normal".equals(fault)) {
				normalPacked = invertPackedNormal(normalPacked);
				normalContractValid = false;
			}
			switch (facing) {
				case 1 -> positiveYNormalSections++;
				case 4 -> negativeYNormalSections++;
				case 0, 2, 3, 5 -> horizontalNormalSections++;
				default -> {
				}
			}
			if ("wrong-top-face-shade".equals(fault) && facing == 1) {
				topFaceShadeContractValid = false;
			}
			for (int vertex = cursor; vertex < cursor + segmentVertexCount; vertex++) {
				VulkanicGalBridge.WorldMeshVertexRecord original = vertices.get(vertex);
				int color = original.colorArgb();
				if ("wrong-top-face-shade".equals(fault) && facing == 1) {
					color = multiplyArgbRgb(color, 0x80);
				}
				vertices.set(vertex, new VulkanicGalBridge.WorldMeshVertexRecord(
					original.x(), original.y(), original.z(), original.u(), original.v(), original.atlasU(), original.atlasV(),
					original.shaderBlockId(), original.shaderMaterialType(), color, normalPacked, original.light()
				));
			}
			for (int quadBase = cursor; quadBase + 3 < cursor + segmentVertexCount; quadBase += 4) {
				indices.add(quadBase);
				indices.add(quadBase + 1);
				indices.add(quadBase + 2);
					indices.add(quadBase + 2);
					indices.add(quadBase + 3);
					indices.add(quadBase);
					maxIndex = Math.max(maxIndex, quadBase + 3);
				}
				if (layer != ChunkSectionLayer.TRANSLUCENT) {
					sections.add(new VulkanicGalBridge.WorldMeshSectionRecord(
						layer == ChunkSectionLayer.SOLID ? RustGalWorldPrimitiveRenderer.MATERIAL_ID_OPAQUE_TEXTURED : RustGalWorldPrimitiveRenderer.MATERIAL_ID_CUTOUT_TEXTURED,
						RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS,
						layer == ChunkSectionLayer.SOLID ? RustGalWorldPrimitiveRenderer.MATERIAL_MODE_OPAQUE : RustGalWorldPrimitiveRenderer.MATERIAL_MODE_CUTOUT,
						RustGalWorldPrimitiveRenderer.CULL_BACK,
						RustGalWorldPrimitiveRenderer.WORLD_WINDING_CCW,
						firstIndex * 2,
						indices.size() - firstIndex
					));
				}
				cursor += segmentVertexCount;
			}
		if (cursor != vertexCount) {
			throw new IllegalArgumentException("static terrain vertex segments cover " + cursor + " of " + vertexCount + " vertices");
		}
		byte[] indexBytes;
		OrderedTranslucentMesh orderedTranslucentMesh = null;
		if (layer == ChunkSectionLayer.TRANSLUCENT) {
			byte[] sourceSortedIndexBytes = copySorterIndexBytes(output.getSorter());
			if (sourceSortedIndexBytes.length == 0) {
				sourceSortedIndexBytes = packU32(indices);
			}
			orderedTranslucentMesh = buildOrderedTranslucentMesh(
				sourceSortedIndexBytes,
				mesh.getPrimitiveMetadata(),
				vertices,
				vertexCount
			);
			indexBytes = orderedTranslucentMesh.indexBytes();
			sections.addAll(orderedTranslucentMesh.sections());
		} else {
			indexBytes = packU16(indices);
		}
		if (sections.isEmpty()) {
			throw new IllegalArgumentException("static terrain mesh has no drawable sections");
		}
		long sectionPos = output.render.getPosition().asLong();
		long meshKey = meshKey(sectionPos, layer);
		long generation = meshGeneration(sectionPos, layer, vertices, indexBytes, sections);
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
			layer == ChunkSectionLayer.TRANSLUCENT ? indexBytes.length / Integer.BYTES : indices.size(),
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
			orderedTranslucentMesh == null ? "" : orderedTranslucentMesh.accountingReason(),
			orderedTranslucentMesh == null ? 0 : orderedTranslucentMesh.unsupportedPrimitiveCount(),
			new VulkanicGalBridge.WorldMeshAssetRecord(
				meshKey,
				generation,
				RustGalWorldPrimitiveRenderer.MESH_VERTEX_LAYOUT_V2,
				layer == ChunkSectionLayer.TRANSLUCENT ? INDEX_TYPE_U32 : INDEX_TYPE_U16,
				vertices,
				indexBytes,
				sections
			)
		);
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

	static OrderedTranslucentMesh buildOrderedTranslucentMesh(byte[] sourceSortedIndexBytes,
			int[] primitiveMetadata, List<VulkanicGalBridge.WorldMeshVertexRecord> vertices, int vertexCount) {
		if (sourceSortedIndexBytes.length == 0 || sourceSortedIndexBytes.length % (Integer.BYTES * 6) != 0) {
			throw new IllegalArgumentException("translucent sorted index payload must contain whole u32 quads");
		}
		int primitiveCount = vertexCount / 4;
		int metadataStride = NativeSectionMeshBuilder.PRIMITIVE_METADATA_RECORD_INTS;
		if (primitiveMetadata.length != primitiveCount * metadataStride) {
			throw new IllegalArgumentException("translucent primitive metadata count " + primitiveMetadata.length
				+ " does not match primitive count " + primitiveCount);
		}
		ByteArrayOutputStream retainedIndices = new ByteArrayOutputStream(sourceSortedIndexBytes.length);
		ByteArrayOutputStream omittedIndices = new ByteArrayOutputStream(sourceSortedIndexBytes.length);
		List<VulkanicGalBridge.WorldMeshSectionRecord> sections = new ArrayList<>();
		int openMaterialId = 0;
		int openTextureId = 0;
		int openIndexStart = 0;
		int retainedIndexCount = 0;
		int retainedPrimitiveCount = 0;
		int omittedPrimitiveCount = 0;
		int nonFluidPrimitiveCount = 0;
		int waterPrimitiveCount = 0;
		int unsupportedPrimitiveCount = 0;
		int previousRetainedMaterialId = 0;
		int previousRetainedTextureId = 0;
		int materialSwitchCount = 0;
		int waterStillPrimitiveCount = 0;
		int waterFlowPrimitiveCount = 0;
		int waterOverlayPrimitiveCount = 0;
		int waterTextureSwitchCount = 0;
		StringBuilder primitiveSample = new StringBuilder();
		boolean[] seen = new boolean[primitiveCount];
		for (int sourceOffset = 0; sourceOffset < sourceSortedIndexBytes.length; sourceOffset += Integer.BYTES * 6) {
			int primitiveId = primitiveIdFromSortedQuad(sourceSortedIndexBytes, sourceOffset, vertexCount);
			if (primitiveId < 0 || primitiveId >= primitiveCount) {
				throw new IllegalArgumentException("translucent sorted payload references primitive " + primitiveId
					+ " outside 0.." + (primitiveCount - 1));
			}
			if (seen[primitiveId]) {
				throw new IllegalArgumentException("translucent sorted payload references primitive " + primitiveId + " more than once");
			}
			seen[primitiveId] = true;
			int metadataOffset = primitiveId * metadataStride;
			int primitiveKind = primitiveMetadata[metadataOffset];
			switch (primitiveKind) {
				case NativeSectionMeshBuilder.PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT -> nonFluidPrimitiveCount++;
				case NativeSectionMeshBuilder.PRIMITIVE_KIND_BUILTIN_WATER -> waterPrimitiveCount++;
				case NativeSectionMeshBuilder.PRIMITIVE_KIND_UNSUPPORTED_FLUID -> unsupportedPrimitiveCount++;
				default -> {
				}
			}
			int materialId = translucentMaterialForPrimitiveKind(primitiveKind);
			if (materialId == 0) {
				closeTranslucentRange(sections, openMaterialId, openTextureId, openIndexStart, retainedIndexCount);
				openMaterialId = 0;
				openTextureId = 0;
				openIndexStart = retainedIndexCount;
				omittedIndices.write(sourceSortedIndexBytes, sourceOffset, Integer.BYTES * 6);
				omittedPrimitiveCount++;
				appendPrimitiveSample(primitiveSample, primitiveId, primitiveKind, "omitted", 0, 0, retainedIndexCount);
				continue;
			}
			int textureId = translucentTextureForPrimitive(primitiveKind, primitiveId, vertices);
			if (primitiveKind == NativeSectionMeshBuilder.PRIMITIVE_KIND_BUILTIN_WATER) {
				if (textureId == RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_STILL) {
					waterStillPrimitiveCount++;
				} else if (textureId == RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_FLOW) {
					waterFlowPrimitiveCount++;
				} else if (textureId == RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_OVERLAY) {
					waterOverlayPrimitiveCount++;
				}
				if (previousRetainedTextureId != 0 && previousRetainedTextureId != textureId) {
					waterTextureSwitchCount++;
				}
			}
			if (openMaterialId != materialId || openTextureId != textureId) {
				closeTranslucentRange(sections, openMaterialId, openTextureId, openIndexStart, retainedIndexCount);
				if (previousRetainedMaterialId != 0 && previousRetainedMaterialId != materialId) {
					materialSwitchCount++;
				}
				previousRetainedMaterialId = materialId;
				previousRetainedTextureId = textureId;
				openMaterialId = materialId;
				openTextureId = textureId;
				openIndexStart = retainedIndexCount;
			}
			retainedIndices.write(sourceSortedIndexBytes, sourceOffset, Integer.BYTES * 6);
			retainedIndexCount += 6;
			retainedPrimitiveCount++;
			appendPrimitiveSample(primitiveSample, primitiveId, primitiveKind, "retained", materialId, textureId, retainedIndexCount - 6);
		}
		closeTranslucentRange(sections, openMaterialId, openTextureId, openIndexStart, retainedIndexCount);
		for (int primitiveId = 0; primitiveId < primitiveCount; primitiveId++) {
			if (!seen[primitiveId]) {
				throw new IllegalArgumentException("translucent sorted payload omitted primitive " + primitiveId);
			}
		}
		if (retainedIndexCount == 0) {
			throw new IllegalArgumentException("translucent sorted payload retained no supported primitives");
		}
		byte[] retainedIndexBytes = retainedIndices.toByteArray();
		byte[] omittedIndexBytes = omittedIndices.toByteArray();
		return new OrderedTranslucentMesh(
			retainedIndexBytes,
			sections,
			primitiveCount,
			nonFluidPrimitiveCount,
			waterPrimitiveCount,
			unsupportedPrimitiveCount,
			retainedPrimitiveCount,
			omittedPrimitiveCount,
			sourceSortedIndexBytes.length / Integer.BYTES,
			retainedIndexCount,
			omittedIndexBytes.length / Integer.BYTES,
			sortedIndexHash(sourceSortedIndexBytes),
			sortedIndexHash(retainedIndexBytes),
			sortedIndexHash(omittedIndexBytes),
			materialSwitchCount,
			waterStillPrimitiveCount,
			waterFlowPrimitiveCount,
			waterOverlayPrimitiveCount,
			waterTextureSwitchCount,
			waterAnimationHash(),
			waterAnimationSummary(),
			translucentRangeSummary(sections),
			primitiveSample.toString()
		);
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

	private static void closeTranslucentRange(List<VulkanicGalBridge.WorldMeshSectionRecord> sections,
			int materialId, int textureId, int startIndex, int currentIndexCount) {
		if (materialId == 0 || currentIndexCount <= startIndex) {
			return;
		}
		sections.add(new VulkanicGalBridge.WorldMeshSectionRecord(
			materialId,
			textureId == 0 ? RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS : textureId,
			RustGalWorldPrimitiveRenderer.MATERIAL_MODE_TRANSLUCENT,
			RustGalWorldPrimitiveRenderer.CULL_BACK,
			RustGalWorldPrimitiveRenderer.WORLD_WINDING_CCW,
			startIndex * Integer.BYTES,
			currentIndexCount - startIndex
		));
	}

	private static int translucentMaterialForPrimitiveKind(int primitiveKind) {
		return switch (primitiveKind) {
			case NativeSectionMeshBuilder.PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT ->
				RustGalWorldPrimitiveRenderer.MATERIAL_ID_TRANSLUCENT_TEXTURED;
			case NativeSectionMeshBuilder.PRIMITIVE_KIND_BUILTIN_WATER ->
				RustGalWorldPrimitiveRenderer.MATERIAL_ID_WATER_TRANSLUCENT;
			case NativeSectionMeshBuilder.PRIMITIVE_KIND_UNSUPPORTED_FLUID -> 0;
			default -> throw new IllegalArgumentException("unsupported translucent primitive kind " + primitiveKind);
		};
	}

	private static int translucentTextureForPrimitive(int primitiveKind, int primitiveId,
			List<VulkanicGalBridge.WorldMeshVertexRecord> vertices) {
		if (primitiveKind != NativeSectionMeshBuilder.PRIMITIVE_KIND_BUILTIN_WATER) {
			return RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS;
		}
		int base = primitiveId * 4;
		if (base < 0 || base + 3 >= vertices.size()) {
			throw new IllegalArgumentException("water primitive " + primitiveId + " exceeds vertex payload");
		}
		FluidSpriteAsset asset = waterTextureForPrimitive(vertices.subList(base, base + 4));
		for (int index = base; index < base + 4; index++) {
			VulkanicGalBridge.WorldMeshVertexRecord original = vertices.get(index);
			vertices.set(index, new VulkanicGalBridge.WorldMeshVertexRecord(
				original.x(),
				original.y(),
				original.z(),
				clamp01(asset.localU(original.u())),
				clamp01(asset.localV(original.v())),
				original.atlasU(),
				original.atlasV(),
				original.shaderBlockId(),
				waterShaderMaterialType(asset.textureId()),
				original.colorArgb(),
				original.normalPacked(),
				original.light()
			));
		}
		return asset.textureId();
	}

	private static FluidSpriteAsset waterTextureForPrimitive(List<VulkanicGalBridge.WorldMeshVertexRecord> vertices) {
		FluidSpriteAsset still = waterStillAsset;
		FluidSpriteAsset flow = waterFlowAsset;
		FluidSpriteAsset overlay = waterOverlayAsset;
		if (still == null || flow == null || overlay == null) {
			throw new IllegalStateException("water animation texture assets have not been initialized");
		}
		if (allVerticesWithin(vertices, still)) {
			return still;
		}
		if (allVerticesWithin(vertices, overlay)) {
			return overlay;
		}
		if (allVerticesWithin(vertices, flow)) {
			return flow;
		}
		throw new IllegalArgumentException("built-in water primitive UVs do not match still, flow, or overlay sprites");
	}

	private static boolean allVerticesWithin(List<VulkanicGalBridge.WorldMeshVertexRecord> vertices, FluidSpriteAsset asset) {
		for (VulkanicGalBridge.WorldMeshVertexRecord vertex : vertices) {
			if (!asset.contains(vertex.u(), vertex.v())) {
				return false;
			}
		}
		return true;
	}

	private static float clamp01(float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
	}

	private static int waterShaderMaterialType(int textureId) {
		if (textureId == RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_STILL) {
			return 1;
		}
		if (textureId == RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_FLOW) {
			return 2;
		}
		if (textureId == RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_OVERLAY) {
			return 3;
		}
		return 0;
	}

	private static int primitiveIdFromSortedQuad(byte[] bytes, int offset, int vertexCount) {
		int i0 = readU32Index(bytes, offset);
		int i1 = readU32Index(bytes, offset + 4);
		int i2 = readU32Index(bytes, offset + 8);
		int i3 = readU32Index(bytes, offset + 12);
		int i4 = readU32Index(bytes, offset + 16);
		int i5 = readU32Index(bytes, offset + 20);
		if ((i0 & 3) != 0 || i1 != i0 + 1 || i2 != i0 + 2 || i3 != i0 + 2 || i4 != i0 + 3 || i5 != i0) {
			throw new IllegalArgumentException("translucent sorted payload contains an interleaved or malformed primitive at byte " + offset);
		}
		if (i4 < 0 || i4 >= vertexCount) {
			throw new IllegalArgumentException("translucent sorted payload references vertex " + i4 + " but vertex count is " + vertexCount);
		}
		return i0 / 4;
	}

	private static int readU32Index(byte[] bytes, int offset) {
		return (bytes[offset] & 0xff)
			| ((bytes[offset + 1] & 0xff) << 8)
			| ((bytes[offset + 2] & 0xff) << 16)
			| ((bytes[offset + 3] & 0xff) << 24);
	}

	private static boolean enqueueSectionLayer(RenderSection section, ChunkSectionLayer layer, Camera camera, int viewportWidth, int viewportHeight, int drawOrder,
			Set<VisibleSubmitKey> visibleSubmissions) {
		visibleLayerProbes.incrementAndGet();
		TerrainSectionAsset asset = SECTION_ASSETS.get(new LayerKey(section.getPosition().asLong(), layer));
		if (asset == null) {
			net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainNonExecution(
				section.getPosition().asLong(),
				layer.name(),
				"asset-missing:" + net.sodium.client.render.StaticTerrainParityDiagnostics
					.rustStaticTerrainAdmissionReason(section.getPosition().asLong(), layer.name()),
				0L,
				0L
			);
			return false;
		}
		float[] transform = new Matrix4f()
			.translation(
				(float)(section.getOriginX() - camera.getPosition().x()),
				(float)(section.getOriginY() - camera.getPosition().y()),
				(float)(section.getOriginZ() - camera.getPosition().z())
			)
			.get(new float[16]);
		long visibleGeneration = "stale-generation".equals(activeFault()) ? asset.meshGeneration() + 1L : asset.meshGeneration();
			TranslucentSortSnapshot sortedIndex =
				layer == ChunkSectionLayer.TRANSLUCENT ? currentTranslucentSortSnapshot(asset) : null;
			long sortGeneration = sortedIndex == null ? 0L : sortedIndex.sortGeneration();
			long sortedIndexHash = sortedIndex == null ? 0L : sortedIndex.indexHash();
		if (visibleSubmissions != null && !"duplicate-visible-section".equals(activeFault())) {
			VisibleSubmitKey submitKey = new VisibleSubmitKey(section.getPosition().asLong(), layer, visibleGeneration);
			if (!visibleSubmissions.add(submitKey)) {
				net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainNonExecution(
					section.getPosition().asLong(), layer.name(), "duplicate-visible-suppressed", visibleGeneration, 0L
				);
				recordEvent(
					section.getPosition().asLong(),
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
				return false;
			}
		}
		boolean submitted = RustGalWorldPrimitiveRenderer.enqueueStaticTerrainMeshInstance(
			asset.meshKey(),
			visibleGeneration,
			transform,
			viewportWidth,
			viewportHeight,
			layer == ChunkSectionLayer.TRANSLUCENT ? RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_NO_WRITE : RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_WRITE
		);
		long enqueueFrameId = rustEnqueueFrames.incrementAndGet();
		if (submitted) {
			visibleLayerSubmissions.incrementAndGet();
			recordCurrentFrameVisibleSubmission(enqueueFrameId);
			net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainExecution(
				"rust-vulkan-enqueued",
				section.getPosition().asLong(),
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
			recordEvent(
				section.getPosition().asLong(),
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
			if ("duplicate-visible-section".equals(activeFault())) {
				recordEvent(
					section.getPosition().asLong(),
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
			failedLayerSubmissions.incrementAndGet();
			net.sodium.client.render.StaticTerrainParityDiagnostics.recordRustStaticTerrainNonExecution(
				section.getPosition().asLong(), layer.name(), "stale-or-unregistered-submit", visibleGeneration, enqueueFrameId
			);
			recordEvent(section.getPosition().asLong(), layer, 0L, asset.meshGeneration(), visibleGeneration, atlasGeneration, asset, section.getOriginX(), section.getOriginY(), section.getOriginZ(), 0.0F, 0.0F, 0.0F, "stale-or-unregistered-submit", 0L, enqueueFrameId, 0L, 0L);
		}
		return submitted;
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
			new Matrix4f().identity().get(new float[16]),
			viewportWidth,
			viewportHeight
		);
		if (submitted) {
			visibleLayerSubmissions.incrementAndGet();
			recordCurrentFrameVisibleSubmission(enqueueFrameId);
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

	private static void recordCurrentFrameVisibleSubmission(long fallbackFrameId) {
		long frameId = currentGameplayFrameId();
		if (frameId <= 0L) {
			frameId = fallbackFrameId;
		}
		long previousFrameId = lastVisibleSubmissionFrameId.getAndSet(frameId);
		if (previousFrameId == frameId) {
			currentFrameVisibleLayerSubmissions.incrementAndGet();
		} else {
			currentFrameVisibleLayerSubmissions.set(1L);
		}
	}

	private static boolean finiteBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(minZ)
			&& Float.isFinite(maxX) && Float.isFinite(maxY) && Float.isFinite(maxZ)
			&& minX <= maxX && minY <= maxY && minZ <= maxZ;
	}

	private static int activeTerrainVertexStride() {
		try {
			return WorldRenderingSettings.INSTANCE.getVertexFormat().getNativeFormat().stride();
		} catch (RuntimeException error) {
			return COMPACT_PREFIX_STRIDE;
		}
	}

	private static String activeFault() {
		String scenario = System.getProperty(STATIC_TERRAIN_SCENARIO_PROPERTY, "").trim();
		if (scenario.isBlank() || "hidden".equalsIgnoreCase(scenario)) {
			return "";
		}
		return System.getProperty(FAULT_PROPERTY, "").trim().toLowerCase(Locale.ROOT);
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

	private static float decodeTexture(int value) {
		return (value & 0x7fff) / (float)TEXTURE_MAX_VALUE;
	}

	private static int decodeLight(int lightMaterial, boolean swapBlockAndSky) {
		int block = Math.max(0, Math.min(15, (lightMaterial & 0xff) >>> 4));
		int sky = Math.max(0, Math.min(15, ((lightMaterial >>> 8) & 0xff) >>> 4));
		if (swapBlockAndSky) {
			int swapped = block;
			block = sky;
			sky = swapped;
		}
		return (block << 4) | (sky << 20);
	}

	static int decodeCompactTerrainColorForRust(int compactAbgr, boolean separateAo) {
		return decodeCompactTerrainColorForRust(compactAbgr, separateAo, false, false);
	}

	private static int decodeCompactTerrainColorForRust(int compactAbgr, boolean separateAo, boolean invertAo, boolean doubleShade) {
		int alphaOrAo = (compactAbgr >>> 24) & 0xff;
		int blue = (compactAbgr >>> 16) & 0xff;
		int green = (compactAbgr >>> 8) & 0xff;
		int red = compactAbgr & 0xff;
		int alpha = alphaOrAo;
		if (separateAo) {
			if (invertAo) {
				alphaOrAo = 255 - alphaOrAo;
			}
			red = multiplyColorByte(red, alphaOrAo);
			green = multiplyColorByte(green, alphaOrAo);
			blue = multiplyColorByte(blue, alphaOrAo);
			if (doubleShade) {
				red = multiplyColorByte(red, alphaOrAo);
				green = multiplyColorByte(green, alphaOrAo);
				blue = multiplyColorByte(blue, alphaOrAo);
			}
			alpha = 0xff;
		}
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private static int multiplyColorByte(int color, int factor) {
		return Math.max(0, Math.min(255, (color * factor + 255) >>> 8));
	}

	private static int multiplyArgbRgb(int argb, int factor) {
		int alpha = argb & 0xff000000;
		int red = multiplyColorByte((argb >>> 16) & 0xff, factor);
		int green = multiplyColorByte((argb >>> 8) & 0xff, factor);
		int blue = multiplyColorByte(argb & 0xff, factor);
		return alpha | (red << 16) | (green << 8) | blue;
	}

	private static int invertPackedNormal(int normalPacked) {
		float x = -unpackPackedNormalComponent(normalPacked, 0);
		float y = -unpackPackedNormalComponent(normalPacked, 8);
		float z = -unpackPackedNormalComponent(normalPacked, 16);
		return packNormal(x, y, z);
	}

	private static float unpackPackedNormalComponent(int normalPacked, int shift) {
		return (byte)((normalPacked >>> shift) & 0xff) / 127.0F;
	}

	private static int normalForSegment(List<VulkanicGalBridge.WorldMeshVertexRecord> vertices, int start, int count, int facing) {
		return switch (facing) {
			case 0 -> packNormal(1, 0, 0);
			case 1 -> packNormal(0, 1, 0);
			case 2 -> packNormal(0, 0, 1);
			case 3 -> packNormal(-1, 0, 0);
			case 4 -> packNormal(0, -1, 0);
			case 5 -> packNormal(0, 0, -1);
			default -> count >= 3 ? computedNormal(vertices.get(start), vertices.get(start + 1), vertices.get(start + 2)) : packNormal(0, 1, 0);
		};
	}

	private static int computedNormal(
		VulkanicGalBridge.WorldMeshVertexRecord a,
		VulkanicGalBridge.WorldMeshVertexRecord b,
		VulkanicGalBridge.WorldMeshVertexRecord c
	) {
		float ax = b.x() - a.x();
		float ay = b.y() - a.y();
		float az = b.z() - a.z();
		float bx = c.x() - a.x();
		float by = c.y() - a.y();
		float bz = c.z() - a.z();
		float nx = ay * bz - az * by;
		float ny = az * bx - ax * bz;
		float nz = ax * by - ay * bx;
		float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length <= 0.00001F) {
			return packNormal(0, 1, 0);
		}
		return packNormal(nx / length, ny / length, nz / length);
	}

	private static int packNormal(float x, float y, float z) {
		int ix = Math.max(-127, Math.min(127, Math.round(x * 127.0F))) & 0xff;
		int iy = Math.max(-127, Math.min(127, Math.round(y * 127.0F))) & 0xff;
		int iz = Math.max(-127, Math.min(127, Math.round(z * 127.0F))) & 0xff;
		return ix | (iy << 8) | (iz << 16);
	}

	private static byte[] packU16(List<Integer> indices) {
		byte[] bytes = new byte[indices.size() * 2];
		for (int i = 0; i < indices.size(); i++) {
			int value = indices.get(i);
			bytes[i * 2] = (byte)(value & 0xff);
			bytes[i * 2 + 1] = (byte)((value >>> 8) & 0xff);
		}
		return bytes;
	}

	private static byte[] packU32(List<Integer> indices) {
		byte[] bytes = new byte[indices.size() * 4];
		for (int i = 0; i < indices.size(); i++) {
			int value = indices.get(i);
			int offset = i * 4;
			bytes[offset] = (byte)(value & 0xff);
			bytes[offset + 1] = (byte)((value >>> 8) & 0xff);
			bytes[offset + 2] = (byte)((value >>> 16) & 0xff);
			bytes[offset + 3] = (byte)((value >>> 24) & 0xff);
		}
		return bytes;
	}

	private static byte[] copySorterIndexBytes(Sorter sorter) {
		if (sorter == null) {
			return new byte[0];
		}
		NativeBuffer indexBuffer = sorter.getIndexBuffer();
		if (indexBuffer == null || indexBuffer.getLength() <= 0) {
			return new byte[0];
		}
		ByteBuffer src = indexBuffer.getDirectBuffer().duplicate();
		src.clear();
		src.limit(indexBuffer.getLength());
		byte[] bytes = new byte[indexBuffer.getLength()];
		src.get(bytes);
		return bytes;
	}

		private static RustGalWorldPrimitiveRenderer.StaticTerrainSortedIndexSnapshot sortedIndexSnapshot(long meshKey) {
			return RustGalWorldPrimitiveRenderer.staticTerrainSortedIndexSnapshot(meshKey);
		}

		private static TranslucentSortSnapshot currentTranslucentSortSnapshot(TerrainSectionAsset asset) {
			if (asset == null) {
				return null;
			}
			RustGalWorldPrimitiveRenderer.StaticTerrainSortedIndexSnapshot sortedIndex = sortedIndexSnapshot(asset.meshKey());
			if (sortedIndex != null) {
				return new TranslucentSortSnapshot(
					sortedIndex.meshGeneration(),
					sortedIndex.indexGeneration(),
					sortedIndex.indexType(),
					sortedIndex.indexBytes(),
					sortedIndex.indexHash()
				);
			}
			byte[] indexBytes = asset.asset().indexBytes();
			if (asset.initialSortGeneration() <= 0L || indexBytes.length == 0) {
				return null;
			}
			return new TranslucentSortSnapshot(
				asset.meshGeneration(),
				asset.initialSortGeneration(),
				asset.indexType(),
				indexBytes.length,
				sortedIndexHash(indexBytes)
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

		private static String translucentSorterType(Sorter sorter) {
			if (sorter == null) {
				return "none";
			}
			if (sorter instanceof SharedIndexSorter) {
				return "shared-index";
			}
			return sorter.getClass().getName();
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

	private static long sortedIndexGeneration(long meshKey, long meshGeneration, byte[] indexBytes) {
		long hash = fnv64("static-terrain-translucent-sort-v1");
		hash = fnv64Long(hash, meshKey);
		hash = fnv64Long(hash, meshGeneration);
		hash = fnv64Bytes(hash, indexBytes);
		return hash == 0L ? 1L : hash;
	}

	private static long meshKey(long sectionPos, ChunkSectionLayer layer) {
		long hash = fnv64("static-terrain-section-v1");
		hash = fnv64Long(hash, sectionPos);
		hash = fnv64Int(hash, layer.ordinal());
		return hash == 0L ? 1L : hash;
	}

	private static long meshGeneration(
		long sectionPos,
		ChunkSectionLayer layer,
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices,
		byte[] indexBytes,
		List<VulkanicGalBridge.WorldMeshSectionRecord> sections
	) {
		long hash = fnv64("static-terrain-generation-v1");
		hash = fnv64Long(hash, sectionPos);
		hash = fnv64Int(hash, layer.ordinal());
		hash = fnv64Int(hash, vertices.size());
		for (VulkanicGalBridge.WorldMeshVertexRecord vertex : vertices) {
			hash = fnv64Float(hash, vertex.x());
			hash = fnv64Float(hash, vertex.y());
			hash = fnv64Float(hash, vertex.z());
			hash = fnv64Float(hash, vertex.u());
			hash = fnv64Float(hash, vertex.v());
			hash = fnv64Float(hash, vertex.atlasU());
			hash = fnv64Float(hash, vertex.atlasV());
			hash = fnv64Int(hash, vertex.shaderBlockId());
			hash = fnv64Int(hash, vertex.shaderMaterialType());
			hash = fnv64Int(hash, vertex.colorArgb());
			hash = fnv64Int(hash, vertex.normalPacked());
			hash = fnv64Int(hash, vertex.light());
		}
		hash = fnv64Int(hash, sections.size());
		for (VulkanicGalBridge.WorldMeshSectionRecord section : sections) {
			hash = fnv64Int(hash, section.materialId());
			hash = fnv64Int(hash, section.textureId());
			hash = fnv64Int(hash, section.materialMode());
			hash = fnv64Int(hash, section.cullPolicy());
			hash = fnv64Int(hash, section.winding());
			hash = fnv64Int(hash, section.indexOffset());
			hash = fnv64Int(hash, section.indexCount());
		}
		hash = fnv64Bytes(hash, indexBytes);
		return hash == 0L ? 1L : hash;
	}

	private static List<VulkanicGalBridge.WorldMeshTextureAssetRecord> atlasTextureUpdatePayload() {
		byte[] payload = atlasPayload;
		long generation = atlasGeneration;
		if (payload == null || registeredAtlasGeneration == generation) {
			return List.of();
		}
		synchronized (RustGalTerrainRenderer.class) {
			if (atlasPayload == null || registeredAtlasGeneration == atlasGeneration) {
				return List.of();
			}
			registeredAtlasGeneration = atlasGeneration;
			texturePayloadUpdates.incrementAndGet();
			texturePayloadUpdateBytes.addAndGet(atlasPayload.length);
			ArrayList<VulkanicGalBridge.WorldMeshTextureAssetRecord> records = new ArrayList<>(4);
			records.add(new VulkanicGalBridge.WorldMeshTextureAssetRecord(RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS, atlasPayload));
			if (waterStillAsset != null) {
				records.add(waterStillAsset.textureRecord());
			}
			if (waterFlowAsset != null) {
				records.add(waterFlowAsset.textureRecord());
			}
			if (waterOverlayAsset != null) {
				records.add(waterOverlayAsset.textureRecord());
			}
			return records;
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

	private static void removeLayer(long sectionPos, ChunkSectionLayer layer, String reason) {
		TerrainSectionAsset removed = SECTION_ASSETS.remove(new LayerKey(sectionPos, layer));
		if (removed != null) {
			if ("world-unload".equals(reason)) {
				lastWorldUnloadAsset = removed;
				lastWorldUnloadSectionPos = sectionPos;
				lastWorldUnloadLayer = layer;
			}
			removedLayers.incrementAndGet();
			RustGalWorldPrimitiveRenderer.removeStaticTerrainMeshAsset(removed.meshKey());
				recordEvent(sectionPos, layer, 0L, removed.meshGeneration(), 0L, atlasGeneration, removed, 0, 0, 0, 0.0F, 0.0F, 0.0F, reason);
		}
	}

	private static void ensureAtlasPayload() {
		if (atlasPayload != null) {
			return;
		}
		synchronized (RustGalTerrainRenderer.class) {
			if (atlasPayload != null) {
				return;
			}
				try {
					TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
					BufferedImage image = new BufferedImage(atlas.width, atlas.height, BufferedImage.TYPE_INT_ARGB);
					for (TextureAtlasSprite sprite : atlas.texturesByName.values()) {
						copySprite(image, sprite);
					}
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
					try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
						ImageIO.write(image, "png", output);
						atlasPayload = output.toByteArray();
						waterStillAsset = nextWaterStillAsset;
						waterFlowAsset = nextWaterFlowAsset;
						waterOverlayAsset = nextWaterOverlayAsset;
						atlasGeneration++;
					}
			} catch (RuntimeException | IOException error) {
				throw new IllegalStateException("Failed to build Rust-owned block atlas payload for static terrain", error);
			}
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
			png
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

	private static void copySprite(BufferedImage atlasImage, TextureAtlasSprite sprite) {
		for (int y = 0; y < sprite.contents().height(); y++) {
			for (int x = 0; x < sprite.contents().width(); x++) {
				atlasImage.setRGB(sprite.getX() + x, sprite.getY() + y, sprite.contents().originalImage.getPixel(x, y));
			}
		}
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
				currentGameplayFrameId(),
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
		return Math.max(
			net.minecraft.client.dev.GraphicsFrameBenchmark.currentFrameIndex(),
			net.minecraft.client.dev.DeterministicCameraCapture.currentRenderedFrameIndex()
		);
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

	private record LayerKey(long sectionPos, ChunkSectionLayer layer) {
	}

	private record VisibleSubmitKey(long sectionPos, ChunkSectionLayer layer, long generation) {
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
		String translucentPrimitiveAccountingReason,
		int unsupportedPrimitiveCount,
			VulkanicGalBridge.WorldMeshAssetRecord asset
		) {
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
		long atlasGeneration,
		long registeredAtlasGeneration,
		int activeNativeVertexStride,
		int expectedNativeVertexStride,
		long acceptedBuildOutputs,
		long skippedRouteBuildOutputs,
		long skippedUnsupportedAnimatedSections,
		long skippedUnsupportedFluidTranslucentSections,
		long acceptedWaterAnimatedSections,
		long unsupportedFluidOmittedSections,
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

	public record TerrainLayerSnapshot(
		long sectionPos,
		String layer,
		long meshKey,
		long meshGeneration,
		int vertexCount,
		int indexBytes,
		int sectionCount
	) {
	}
}
