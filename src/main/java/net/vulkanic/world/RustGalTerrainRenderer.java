package net.vulkanic.world;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.render.chunk.lists.ChunkRenderList;
import net.sodium.client.render.chunk.lists.SortedRenderLists;
import net.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.util.iterator.ByteIterator;
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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RustGalTerrainRenderer {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int INDEX_TYPE_U16 = 1;
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
	private static final String STATIC_TERRAIN_SCENARIO_PROPERTY = "mattmc.dev.rustGalStaticTerrain.scenario";
	private static final String FAULT_PROPERTY = "mattmc.dev.rustGalStaticTerrain.fault";
	private static final Map<LayerKey, TerrainSectionAsset> SECTION_ASSETS = new ConcurrentHashMap<>();
	private static final ArrayDeque<TerrainDiagnosticEvent> RECENT_EVENTS = new ArrayDeque<>(MAX_RECENT_EVENTS);
	private static final ArrayDeque<TerrainDiagnosticEvent> LIFECYCLE_EVENTS = new ArrayDeque<>(MAX_LIFECYCLE_EVENTS);
	private static volatile long atlasGeneration;
	private static volatile long registeredAtlasGeneration;
	private static volatile byte[] atlasPayload;
	private static final AtomicLong acceptedBuildOutputs = new AtomicLong();
	private static final AtomicLong skippedRouteBuildOutputs = new AtomicLong();
	private static final AtomicLong skippedUnsupportedAnimatedSections = new AtomicLong();
	private static final AtomicLong skippedEmptyLayers = new AtomicLong();
	private static final AtomicLong registeredMeshes = new AtomicLong();
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
	private static volatile TerrainSectionAsset lastWorldUnloadAsset;
	private static volatile long lastWorldUnloadSectionPos;
	private static volatile ChunkSectionLayer lastWorldUnloadLayer = ChunkSectionLayer.SOLID;

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
		if (output.info.animatedSprites != null) {
			skippedUnsupportedAnimatedSections.incrementAndGet();
			recordEvent(output.render.getPosition().asLong(), ChunkSectionLayer.SOLID, output.submitTime, 0L, 0L, atlasGeneration, null, output.render.getOriginX(), output.render.getOriginY(), output.render.getOriginZ(), 0.0F, 0.0F, 0.0F, "unsupported-animated-sprite");
			recordEvent(output.render.getPosition().asLong(), ChunkSectionLayer.CUTOUT_MIPPED, output.submitTime, 0L, 0L, atlasGeneration, null, output.render.getOriginX(), output.render.getOriginY(), output.render.getOriginZ(), 0.0F, 0.0F, 0.0F, "unsupported-animated-sprite");
			return;
		}
		acceptedBuildOutputs.incrementAndGet();
		long extractionFrameId = terrainExtractionFrames.incrementAndGet();
		ensureAtlasPayload();
		acceptLayer(output, DefaultTerrainRenderPasses.SOLID, ChunkSectionLayer.SOLID, extractionFrameId);
		acceptLayer(output, DefaultTerrainRenderPasses.CUTOUT, ChunkSectionLayer.CUTOUT_MIPPED, extractionFrameId);
	}

	public static void enqueueVisibleTerrain(SortedRenderLists renderLists, Camera camera, int viewportWidth, int viewportHeight) {
		if (!WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan() || renderLists == null || camera == null) {
			return;
		}
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
				if (enqueueSectionLayer(section, ChunkSectionLayer.SOLID, camera, viewportWidth, viewportHeight)) {
					submitted++;
				}
				if (enqueueSectionLayer(section, ChunkSectionLayer.CUTOUT_MIPPED, camera, viewportWidth, viewportHeight)) {
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
				skippedEmptyLayers.get(),
				registeredMeshes.get(),
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
				submissionId
			);
		}
	}

	private static void acceptLayer(ChunkBuildOutput output, TerrainRenderPass pass, ChunkSectionLayer layer, long extractionFrameId) {
		BuiltSectionMeshParts mesh = output.getMesh(pass);
		if (mesh == null || mesh.getVertexData().getLength() == 0) {
			skippedEmptyLayers.incrementAndGet();
			removeLayer(output.render.getPosition().asLong(), layer, "empty-layer");
			return;
		}
			try {
				TerrainSectionAsset asset = decodeMesh(output, mesh, layer);
				SECTION_ASSETS.put(new LayerKey(output.render.getPosition().asLong(), layer), asset);
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
					0L
				);
			} catch (RuntimeException error) {
				LOGGER.warn("Failed to copy Rust static terrain section {} layer {}", output.render.getPosition(), layer, error);
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
				sections.add(new VulkanicGalBridge.WorldMeshSectionRecord(
					layer == ChunkSectionLayer.SOLID ? RustGalWorldPrimitiveRenderer.MATERIAL_ID_OPAQUE_TEXTURED : RustGalWorldPrimitiveRenderer.MATERIAL_ID_CUTOUT_TEXTURED,
				RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS,
				layer == ChunkSectionLayer.SOLID ? RustGalWorldPrimitiveRenderer.MATERIAL_MODE_OPAQUE : RustGalWorldPrimitiveRenderer.MATERIAL_MODE_CUTOUT,
				RustGalWorldPrimitiveRenderer.CULL_BACK,
				RustGalWorldPrimitiveRenderer.WORLD_WINDING_CCW,
				firstIndex * 2,
				indices.size() - firstIndex
				));
				cursor += segmentVertexCount;
			}
		if (cursor != vertexCount) {
			throw new IllegalArgumentException("static terrain vertex segments cover " + cursor + " of " + vertexCount + " vertices");
		}
		if (sections.isEmpty()) {
			throw new IllegalArgumentException("static terrain mesh has no drawable sections");
		}
		byte[] indexBytes = packU16(indices);
		long sectionPos = output.render.getPosition().asLong();
		long meshKey = meshKey(sectionPos, layer);
		long generation = meshGeneration(sectionPos, layer, vertices, indexBytes, sections);
		int diagnosticVertexCount = vertexCount;
		int diagnosticVertexStride = vertexStride;
		int diagnosticMaxIndex = maxIndex;
		int diagnosticIndexType = INDEX_TYPE_U16;
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
		return new TerrainSectionAsset(
			meshKey,
			generation,
			generation,
			diagnosticVertexCount,
			bufferVertexCapacity,
			diagnosticVertexStride,
			indices.size(),
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
			new VulkanicGalBridge.WorldMeshAssetRecord(
				meshKey,
				generation,
				RustGalWorldPrimitiveRenderer.MESH_VERTEX_LAYOUT_V2,
				INDEX_TYPE_U16,
				vertices,
				indexBytes,
				sections
			)
		);
	}

	private static boolean enqueueSectionLayer(RenderSection section, ChunkSectionLayer layer, Camera camera, int viewportWidth, int viewportHeight) {
		visibleLayerProbes.incrementAndGet();
		TerrainSectionAsset asset = SECTION_ASSETS.get(new LayerKey(section.getPosition().asLong(), layer));
		if (asset == null) {
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
		boolean submitted = RustGalWorldPrimitiveRenderer.enqueueStaticTerrainMeshInstance(asset.meshKey(), visibleGeneration, transform, viewportWidth, viewportHeight);
		long enqueueFrameId = rustEnqueueFrames.incrementAndGet();
		if (submitted) {
			visibleLayerSubmissions.incrementAndGet();
			recordCurrentFrameVisibleSubmission(enqueueFrameId);
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
			return List.of(new VulkanicGalBridge.WorldMeshTextureAssetRecord(RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS, atlasPayload));
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
				try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
					ImageIO.write(image, "png", output);
					atlasPayload = output.toByteArray();
					atlasGeneration++;
				}
			} catch (RuntimeException | IOException error) {
				throw new IllegalStateException("Failed to build Rust-owned block atlas payload for static terrain", error);
			}
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
		}
	}

	private static long currentGameplayFrameId() {
		return Math.max(
			net.minecraft.client.dev.GraphicsFrameBenchmark.currentFrameIndex(),
			net.minecraft.client.dev.DeterministicCameraCapture.currentRenderedFrameIndex()
		);
	}

	private record LayerKey(long sectionPos, ChunkSectionLayer layer) {
	}

	private record TerrainSectionAsset(
		long meshKey,
		long meshGeneration,
		long contentHash,
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
		VulkanicGalBridge.WorldMeshAssetRecord asset
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
		long skippedEmptyLayers,
		long registeredMeshes,
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
		List<TerrainDiagnosticEvent> recentEvents
	) {
		public TerrainDiagnostics {
			lifecycleEvents = Collections.unmodifiableList(new ArrayList<>(lifecycleEvents));
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
