package net.vulkanic.world;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.render.renderer.RenderParams;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import net.minecraft.core.BlockPos;
import net.vulkanic.bridge.VulkanicGalBridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Private CPU-side boundary for a future Rust-owned Distant Horizons route.
 *
 * <p>The collector is deliberately disabled unless its diagnostic switch is
 * selected. It copies builder output before the legacy renderer uploads and
 * frees the direct buffers. No OpenGL object, renderer callback, or Iris
 * state is retained here.</p>
 */
public final class DistantHorizonsSemanticCollector {
	public static final String CAPTURE_PROPERTY = "mattmc.dev.rustGalDistantHorizons.semanticCapture";
	/**
	 * Enables a bounded copied-column observation for the Java OpenGL control.
	 * It exists solely so a legacy DH VBO draw can be correlated with its
	 * semantic source after DH releases the VBO. It never selects Rust,
	 * publishes FFI work, or changes the legacy renderer's lifecycle.
	 */
	public static final String LEGACY_OBSERVATION_PROPERTY =
		"mattmc.dev.rustGalDistantHorizons.legacyObservation";
	public static final int VERTEX_LAYOUT_VERSION = 1;
	/** Matches {@code LodQuadBuilder.putVertex}: 3x u16 position, u16 metadata,
	 * rgba8, material8, normal8, and u16 reserved padding. */
	public static final int VERTEX_STRIDE_BYTES = 16;

	private static final int MAX_RETAINED_COLUMNS = 512;
	private static final long MAX_RETAINED_BYTES = 64L * 1024L * 1024L;
	/**
	 * A copied DH column can contain several large quad streams.  Publish a
	 * bounded semantic slice per frame so Panama never constructs one enormous
	 * temporary object graph for every pending column at once.  The Rust asset
	 * cache and generation checks remain the owner of publication ordering.
	 */
	private static final int MAX_PENDING_ASSET_COLUMNS_PER_UPDATE = 16;
	private static final long MAX_PENDING_ASSET_BYTES_PER_UPDATE = 4L * 1024L * 1024L;
	private static final int MAX_VISIBLE_SEGMENTS = 16_384;
	private static final int MAX_PUBLICATION_TRACE_EVENTS = 24;
	/** A bounded semantic transport segment. It is intentionally below Rust's
	 * ABI maximum so a single legacy CPU buffer cannot make the entire coarse
	 * asset update malformed. The boundary is quad aligned and has no native
	 * VBO meaning. */
	private static final int MAX_TRANSPORT_VERTICES_PER_SEGMENT = 65_536;
	public static final int RENDER_FLAG_WHITE_WORLD = 1;
	public static final int RENDER_FLAG_DITHER_FADE = 1 << 1;
	public static final int RENDER_FLAG_NOISE = 1 << 2;
	public static final int RENDER_FLAG_EARTH_CURVE = 1 << 3;
	/** This frame was preflighted as non-water and selected for the Rust
	 * whole-frame DH route. It is deliberately distinct from semantic capture. */
	public static final int RENDER_FLAG_RUST_NON_WATER_ROUTE_SELECTED = 1 << 4;
	/** ABI-compatible name retained for existing semantic transport consumers. */
	public static final int RENDER_FLAG_RUST_OPAQUE_ROUTE_SELECTED = RENDER_FLAG_RUST_NON_WATER_ROUTE_SELECTED;
	private static final AtomicLong NEXT_GENERATION = new AtomicLong(1L);
	private static final AtomicLong NEXT_UPDATE_GENERATION = new AtomicLong(1L);
	private static final Map<Long, LodColumnSnapshot> COLUMNS = new LinkedHashMap<>(16, 0.75F, true);
	/** Last acknowledged immutable asset per live column. The visible render list
	 * may keep using it while DH builds a replacement generation. */
	private static final Map<Long, LodColumnSnapshot> PUBLISHED_COLUMNS = new LinkedHashMap<>();
	/** Exact material provenance retained beside, never inside, the legacy LOD ABI. */
	private static final Map<Long, LodMaterialProvenanceSnapshot> MATERIAL_PROVENANCE = new LinkedHashMap<>();
	/** Provenance paired with the acknowledged asset, never with a newer build. */
	private static final Map<Long, LodMaterialProvenanceSnapshot> PUBLISHED_MATERIAL_PROVENANCE = new LinkedHashMap<>();
	private static final Map<Long, LodColumnSnapshot> PENDING_COLUMNS = new LinkedHashMap<>();
	/** Current real render-list columns awaiting publication. These are an asset
	 * upload priority only; they never select a route or synthesize visibility. */
	private static final Set<Long> PENDING_VISIBLE_COLUMN_KEYS = new LinkedHashSet<>();
	private static int publicationTraceEvents;
	/** Assets handed to the combined coordinator but not yet acknowledged. A
	 * column remains reserved until acknowledgement so replacement builds cannot
	 * starve its last coherent published generation. */
	private static final Map<Long, Long> IN_FLIGHT_ASSET_GENERATIONS = new LinkedHashMap<>();
	private static final Map<Long, Long> PENDING_RETIREMENTS = new LinkedHashMap<>();
	private static final Map<Long, Long> PUBLISHED_GENERATIONS = new LinkedHashMap<>();
	/** Capture-only first-difference evidence for columns rebuilt with new payloads. */
	private static final Map<Long, String> LAST_COLUMN_PAYLOAD_DIFFERENCES = new LinkedHashMap<>();
	private static final List<VulkanicGalBridge.WorldLodColumnInstanceRecord> PENDING_VISIBLE_SEGMENTS = new ArrayList<>();
	/** Last semantic DH segment set handed to the combined frame. Diagnostic
	 * capture may inspect this bounded copy, but it never feeds admission. */
	private static List<VulkanicGalBridge.WorldLodColumnInstanceRecord> LAST_CONSUMED_VISIBLE_SEGMENTS = List.of();
	/** Immutable source snapshots captured at the actual Java-to-Rust frame
	 * handoff. DH is allowed to publish replacements after this point, but those
	 * replacements must not rewrite capture provenance for the submitted frame. */
	private static List<ExecutedVisibleSegmentSnapshot> LAST_CONSUMED_VISIBLE_SEGMENT_SNAPSHOTS = List.of();
	/**
	 * A screenshot acknowledgement runs after presentation and can therefore
	 * observe a later empty visibility traversal. Keep a tiny immutable history
	 * keyed by the Rust world frame that actually executed the segment set so a
	 * capture can prove the same submitted work it displays.
	 */
	private static final int MAX_EXECUTED_VISIBLE_SEGMENT_SNAPSHOTS = 4;
	private static final Map<Long, List<ExecutedVisibleSegmentSnapshot>>
		EXECUTED_VISIBLE_SEGMENTS_BY_WORLD_FRAME = new LinkedHashMap<>();
	private static VulkanicGalBridge.WorldLodRenderFrameRecord PENDING_RENDER_FRAME =
		VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
	private static long routeFrame;
	private static String routeDecision = "not-attempted";
	private static String routeReason = "not-requested";
	private static int routeOpaqueSegments;
	/** Visible opaque segments whose copied quad sidecars still name one
	 * source material per quad. These are candidates for the private exact
	 * atlas route; they are deliberately separate from generic route selection
	 * because model-face resolution remains a Rust asset admission concern. */
	private static int routeExactAtlasIdentitySegments;
	private static int routeExactAtlasIdentityQuads;
	private static int routeExactAtlasMixedQuads;
	private static int routeExactAtlasUnavailableQuads;
	/** Bounded diagnostic split of unavailable coverage. These values only
	 * explain copied semantic sidecars; they do not affect route admission. */
	private static int routeExactAtlasMissingProvenanceQuads;
	private static int routeExactAtlasMisalignedProvenanceQuads;
	private static int routeExactAtlasInvalidIdentityQuads;
	private static int routeExactAtlasIdentityTableEntries;
	private static int routeExactAtlasInputKnownQuads;
	private static int routeExactAtlasInputMixedQuads;
	private static int routeExactAtlasInputUnavailableQuads;
	private static int routeExactAtlasInputOpaqueKnownQuads;
	private static int routeExactAtlasInputOpaqueMixedQuads;
	private static int routeExactAtlasInputOpaqueUnavailableQuads;
	private static int routeExactAtlasOutputKnownQuads;
	private static int routeExactAtlasOutputMixedQuads;
	private static int routeExactAtlasOutputUnavailableQuads;
	private static int routeExactAtlasOutputOpaqueKnownQuads;
	private static int routeExactAtlasOutputOpaqueMixedQuads;
	private static int routeExactAtlasOutputOpaqueUnavailableQuads;
	/** Bounded per-visible-column reconciliation; never a rendering decision. */
	private static final List<String> routeExactAtlasCoverageSamples = new ArrayList<>();
	/** Bounded evidence for why a copied semantic material did or did not
	 * produce a face-atlas table. This is diagnostic-only: it never changes
	 * route selection or causes a material fallback. */
	private static final Map<DistantHorizonsFaceMaterialResolver.Status, Integer> routeExactAtlasResolutionStatusCounts = new LinkedHashMap<>();
	private static final List<String> routeExactAtlasResolutionSamples = new ArrayList<>();
	private static int routeTransparentSegments;
	private static int routeWaterSegments;
	private static int routeVisibleColumns;
	private static int routeUnpublishedVisibleColumns;
	private static int routeCachedColumns;
	private static long semanticBuildAttempts;
	private static long semanticColumnsBuilt;
	private static long semanticColumnsReused;
	private static long semanticColumnsReplaced;
	private static String lastPayloadDifference = "none";
	private static String routeMatrixStatus = "not-observed";
	/** Bounded provenance for the DH matrix gate. This is diagnostic-only and
	 * deliberately records semantic values rather than renderer state. */
	private static String routeMatrixDetail = "not-observed";
	private static boolean routeSelected;
	private static long lastExecutedRouteFrame;
	private static long lastExecutedWorldFrame;
	private static long lastExecutedSubmission;
	private static long lastExecutedCaptureFrame;
	private static int lastExecutedInstances;
	private static int lastExecutedOpaqueInstances;
	private static int lastExecutedTransparentInstances;
	private static int lastExecutedWaterInstances;
	private static boolean lastExecutedFrameSemanticsEnabled;
	private static long retainedBytes;
	/** Bounded separately from the legacy vertex copies because this sidecar is
	 * deliberately outside their stable ABI. */
	private static long retainedMaterialProvenanceBytes;
	private static int nextVisibleOrder;
	/** Capture-only probes for the render-data boundary that precedes DH quad
	 * generation. They are configured solely by the deterministic fixture and
	 * never affect source conversion, mesh construction, or route selection. */
	private static List<BlockPos> waterSourceInputProbes = List.of();
	private static final Map<BlockPos, WaterSourceInputTrace> WATER_SOURCE_INPUT_TRACES = new LinkedHashMap<>();

	private DistantHorizonsSemanticCollector() {
	}

	/**
	 * Arms a bounded source-data diagnostic for deterministic water fixtures.
	 * Replacing the probe set clears previous observations so a copied-world run
	 * cannot accidentally certify a later fixture from stale data.
	 */
	public static void configureWaterSourceInputProbes(List<BlockPos> probes) {
		synchronized (COLUMNS) {
			waterSourceInputProbes = probes == null ? List.of() : List.copyOf(probes.stream().limit(8).toList());
			WATER_SOURCE_INPUT_TRACES.clear();
		}
	}

	/**
	 * Records the converted DH render-data interval covering a configured
	 * fixture cell. This is strictly before {@code ColumnBox} and
	 * {@code LodQuadBuilder}; it makes loss during full-data reduction visible
	 * without fabricating water geometry or inspecting renderer state.
	 */
	public static void recordWaterSourceInput(
		long sectionKey,
		byte detailLevel,
		int sourceMinX,
		int sourceOriginY,
		int sourceMinZ,
		long data,
		int semanticMaterialId
	) {
		synchronized (COLUMNS) {
			if (waterSourceInputProbes.isEmpty()) {
				return;
			}
			int width = 1 << detailLevel;
			int sourceMaxX = sourceMinX + width;
			int sourceMaxZ = sourceMinZ + width;
			int minY = sourceOriginY + RenderDataPointUtil.getYMin(data);
			int maxY = sourceOriginY + RenderDataPointUtil.getYMax(data);
			int materialId = RenderDataPointUtil.getBlockMaterialId(data);
			for (BlockPos probe : waterSourceInputProbes) {
				if (probe.getX() < sourceMinX || probe.getX() >= sourceMaxX
					|| probe.getZ() < sourceMinZ || probe.getZ() >= sourceMaxZ
					|| probe.getY() < minY || probe.getY() >= maxY) {
					continue;
				}
				WATER_SOURCE_INPUT_TRACES.put(probe, new WaterSourceInputTrace(
					probe.getX(), probe.getY(), probe.getZ(), sectionKey, detailLevel,
					sourceMinX, sourceMinZ, width, minY, maxY, materialId,
					semanticMaterialId
				));
			}
		}
	}

	public static WaterSourceInputReceipt waterSourceInputReceipt(List<BlockPos> probes) {
		if (probes == null || probes.isEmpty()) {
			return new WaterSourceInputReceipt(false, "no-water-probes", List.of());
		}
		List<WaterSourceInputTrace> traces = new ArrayList<>(Math.min(probes.size(), 8));
		synchronized (COLUMNS) {
			for (BlockPos probe : probes.stream().limit(8).toList()) {
				WaterSourceInputTrace trace = WATER_SOURCE_INPUT_TRACES.get(probe);
				if (trace != null) {
					traces.add(trace);
				}
			}
		}
		boolean complete = traces.size() == Math.min(probes.size(), 8);
		boolean water = complete && traces.stream()
			.allMatch(trace -> trace.dhMaterialId() == EDhApiBlockMaterial.WATER.index);
		String status = !complete
			? "no-render-data-interval-covering-fixture-water"
			: water ? "ok" : "render-data-covering-fixture-is-not-water";
		return new WaterSourceInputReceipt(water, status, List.copyOf(traces));
	}

	/**
	 * Immutable source evidence paired with the exact segment submitted to Rust.
	 * DH may publish a newer generation before a screenshot acknowledgement, so
	 * capture validation must not reread the mutable published-column maps.
	 */
	private record ExecutedVisibleSegmentSnapshot(
		VulkanicGalBridge.WorldLodColumnInstanceRecord instance,
		LodColumnSnapshot column,
		LodMaterialProvenanceSnapshot provenance
	) {
		private ExecutedVisibleSegmentSnapshot {
			Objects.requireNonNull(instance, "instance");
		}
	}

	private static List<VulkanicGalBridge.WorldLodColumnInstanceRecord> executedSegmentsForWorldFrameLocked(long worldFrame) {
		List<ExecutedVisibleSegmentSnapshot> snapshots = EXECUTED_VISIBLE_SEGMENTS_BY_WORLD_FRAME.get(worldFrame);
		if (snapshots == null) {
			return List.of();
		}
		return snapshots.stream().map(ExecutedVisibleSegmentSnapshot::instance).toList();
	}

	private static ExecutedVisibleSegmentSnapshot executedSegmentSnapshotLocked(
		long worldFrame,
		VulkanicGalBridge.WorldLodColumnInstanceRecord instance
	) {
		for (ExecutedVisibleSegmentSnapshot snapshot : EXECUTED_VISIBLE_SEGMENTS_BY_WORLD_FRAME
			.getOrDefault(worldFrame, List.of())) {
			if (snapshot.instance().equals(instance)) {
				return snapshot;
			}
		}
		return null;
	}

	public static boolean enabled() {
		return Boolean.getBoolean(CAPTURE_PROPERTY)
			|| Boolean.getBoolean(LEGACY_OBSERVATION_PROPERTY)
			|| WorldRenderRoutePolicy.currentDistantHorizonsOpaqueRoute().usesRustWholeFrameVulkan()
			|| selectedSourceExecutionRequested();
	}

	private static boolean retainsLegacyObservationSnapshots() {
		return Boolean.getBoolean(LEGACY_OBSERVATION_PROPERTY)
			&& !usesRustWholeFrameSemanticBuild();
	}

	/**
	 * A selected Rust shader-pack frame can need Distant Horizons' copied
	 * camera semantics even before the frame contains an owned LOD draw. This
	 * keeps that semantic collection separate from the stricter Rust geometry
	 * route decision below: no visible LOD segment is selected or rendered by
	 * Java merely because a pack-wide source branch is active.
	 */
	public static boolean requiresWholeFrameSemanticCollection() {
		return enabled();
	}

	private static boolean selectedSourceExecutionRequested() {
		String value = System.getenv("MATTMC_RUST_SELECTED_SOURCE_EXECUTION");
		return value != null && (value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes"));
	}

	/** True only for the backend-owned whole-frame route. Diagnostic capture by
	 * itself must continue through DH's ordinary Java upload lifecycle. */
	public static boolean usesRustWholeFrameSemanticBuild() {
		return WorldRenderRoutePolicy.currentDistantHorizonsOpaqueRoute().usesRustWholeFrameVulkan();
	}

	/** Whether copied CPU geometry for this real DH quadtree section is ready
	 * for semantic visibility selection. No legacy VBO is implied. */
	public static boolean hasColumn(long columnKey) {
		synchronized (COLUMNS) {
			return COLUMNS.containsKey(columnKey);
		}
	}

	/**
	 * Reports a real legacy or Rust-visible opaque segment covering a point in
	 * the current frame. This is capture evidence only; it neither publishes a
	 * Rust frame nor changes a route decision.
	 */
	public static boolean hasObservedVisibleOpaqueColumnCoveringBlock(int blockX, int blockZ) {
		synchronized (COLUMNS) {
			for (VulkanicGalBridge.WorldLodColumnInstanceRecord segment : PENDING_VISIBLE_SEGMENTS) {
				if (segment.layer() != 1) continue;
				LodColumnSnapshot column = COLUMNS.get(segment.columnKey());
				if (column == null || column.generation() != segment.columnGeneration()
					|| segment.segmentIndex() < 0 || segment.segmentIndex() >= column.opaque().size()) continue;
				List<LodVertex> vertices = column.opaque().get(segment.segmentIndex()).vertices();
				for (int quad = 0; quad < vertices.size() / 4; quad++) {
					if (opaqueQuadCoversBlock(column, vertices, quad, blockX, Integer.MIN_VALUE, blockZ)) {
						return true;
					}
				}
			}
			return false;
		}
	}

	/**
	 * Bounded deterministic-capture probe for copied, published DH material
	 * provenance. It compares only semantic block-state identities and never
	 * affects mesh admission, visibility, or backend execution.
	 */
	public static boolean hasPublishedSemanticMaterialIdentities(List<String> blockIds) {
		Objects.requireNonNull(blockIds, "blockIds");
		if (blockIds.isEmpty()) {
			return false;
		}
		synchronized (COLUMNS) {
			for (String blockId : blockIds) {
			if (blockId == null || blockId.isBlank()) {
				throw new IllegalArgumentException("DH semantic material identity must be non-blank");
			}
			String blockStatePrefix = blockId.endsWith("_STATE_")
				? blockId
				: blockId + "_STATE_";
			boolean found = false;
				for (Map.Entry<Long, LodMaterialProvenanceSnapshot> entry : MATERIAL_PROVENANCE.entrySet()) {
					LodColumnSnapshot column = COLUMNS.get(entry.getKey());
					if (column == null || !Objects.equals(PUBLISHED_GENERATIONS.get(entry.getKey()), column.generation())) {
						continue;
					}
					for (ColumnRenderSource.SemanticMaterialIdentity identity : entry.getValue().semanticMaterials()) {
						String state = identity.blockStateIdentity();
					if (state.startsWith(blockStatePrefix)) {
							found = true;
							break;
						}
					}
					if (found) {
						break;
					}
				}
				if (!found) {
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * True only when one column that was actually handed to the current Rust
	 * frame names every requested material. This prevents a capture from using
	 * unrelated cached DH provenance as proof that a target palette was drawn.
	 */
	public static boolean hasLastConsumedVisibleColumnWithSemanticMaterialIdentities(List<String> blockIds) {
		Objects.requireNonNull(blockIds, "blockIds");
		if (blockIds.isEmpty()) {
			return false;
		}
		List<String> prefixes = blockIds.stream().map(blockId -> {
			if (blockId == null || blockId.isBlank()) {
				throw new IllegalArgumentException("DH semantic material identity must be non-blank");
			}
			return blockId.endsWith("_STATE_") ? blockId : blockId + "_STATE_";
		}).toList();
		synchronized (COLUMNS) {
			for (VulkanicGalBridge.WorldLodColumnInstanceRecord instance : LAST_CONSUMED_VISIBLE_SEGMENTS) {
				LodColumnSnapshot column = publishedColumnLocked(instance.columnKey());
				LodMaterialProvenanceSnapshot provenance = publishedMaterialProvenanceLocked(instance.columnKey());
				if (column == null || column.generation() != instance.columnGeneration() || provenance == null) {
					continue;
				}
				boolean allFound = prefixes.stream().allMatch(prefix -> provenance.semanticMaterials().stream()
					.anyMatch(identity -> identity.blockStateIdentity().startsWith(prefix)));
				if (allFound) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Capture-only evidence that one actual, consumed DH source column covers a
	 * requested world position and retains every named semantic material. The
	 * ordinary name-only helper is intentionally broader for aggregate route
	 * diagnostics; a deterministic material fixture needs this stronger spatial
	 * correlation so unrelated cached terrain cannot satisfy it.
	 */
	public static boolean hasLastConsumedVisibleColumnCoveringBlockWithSemanticMaterialIdentities(
		int blockX,
		int blockZ,
		List<String> blockIds
	) {
		Objects.requireNonNull(blockIds, "blockIds");
		if (blockIds.isEmpty()) {
			return false;
		}
		List<String> prefixes = blockIds.stream().map(blockId -> {
			if (blockId == null || blockId.isBlank()) {
				throw new IllegalArgumentException("DH semantic material identity must be non-blank");
			}
			return blockId.endsWith("_STATE_") ? blockId : blockId + "_STATE_";
		}).toList();
		synchronized (COLUMNS) {
			for (VulkanicGalBridge.WorldLodColumnInstanceRecord instance : LAST_CONSUMED_VISIBLE_SEGMENTS) {
				long columnKey = instance.columnKey();
				int minX = DhSectionPos.getMinCornerBlockX(columnKey);
				int minZ = DhSectionPos.getMinCornerBlockZ(columnKey);
				int width = DhSectionPos.getBlockWidth(columnKey);
				if (blockX < minX || blockX >= minX + width || blockZ < minZ || blockZ >= minZ + width) {
					continue;
				}
				LodColumnSnapshot column = publishedColumnLocked(columnKey);
				LodMaterialProvenanceSnapshot provenance = publishedMaterialProvenanceLocked(columnKey);
				if (column == null || column.generation() != instance.columnGeneration() || provenance == null) {
					continue;
				}
				boolean allFound = prefixes.stream().allMatch(prefix -> provenance.semanticMaterials().stream()
					.anyMatch(identity -> identity.blockStateIdentity().startsWith(prefix)));
				if (allFound) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Capture-only proof that the actual consumed opaque DH column covers a
	 * requested world position. Unlike the exact-atlas provenance helpers this
	 * validates the native DH stream itself, whose reduced vertices retain only
	 * DH material categories and vertex color. It must not be used to infer an
	 * exact Minecraft sprite that the reduced source did not preserve.
	 */
	public static boolean hasLastConsumedVisibleOpaqueColumnCoveringBlock(int blockX, int blockZ) {
		synchronized (COLUMNS) {
			for (VulkanicGalBridge.WorldLodColumnInstanceRecord instance : LAST_CONSUMED_VISIBLE_SEGMENTS) {
				if (instance.layer() != 1) {
					continue;
				}
				long columnKey = instance.columnKey();
				int minX = DhSectionPos.getMinCornerBlockX(columnKey);
				int minZ = DhSectionPos.getMinCornerBlockZ(columnKey);
				int width = DhSectionPos.getBlockWidth(columnKey);
				if (blockX < minX || blockX >= minX + width || blockZ < minZ || blockZ >= minZ + width) {
					continue;
				}
				LodColumnSnapshot column = publishedColumnLocked(columnKey);
				if (column != null && column.generation() == instance.columnGeneration()) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Bounded capture-only reconciliation for a requested world position. This
	 * does not select a route or alter the copied column data; it distinguishes
	 * a column that is merely cached from one that was published and actually
	 * consumed by the current Rust frame.
	 */
	public static ColumnCoverageDiagnostics columnCoverageDiagnosticsAtBlock(int blockX, int blockZ) {
		synchronized (COLUMNS) {
			int cachedColumns = 0;
			int publishedColumns = 0;
			int consumedOpaqueSegments = 0;
			List<String> samples = new ArrayList<>();
			for (Map.Entry<Long, LodColumnSnapshot> entry : COLUMNS.entrySet()) {
				long columnKey = entry.getKey();
				int minX = DhSectionPos.getMinCornerBlockX(columnKey);
				int minZ = DhSectionPos.getMinCornerBlockZ(columnKey);
				int width = DhSectionPos.getBlockWidth(columnKey);
				if (blockX < minX || blockX >= minX + width || blockZ < minZ || blockZ >= minZ + width) {
					continue;
				}
				cachedColumns++;
				LodColumnSnapshot column = entry.getValue();
				boolean published = Objects.equals(PUBLISHED_GENERATIONS.get(columnKey), column.generation());
				if (published) {
					publishedColumns++;
				}
				int consumedOpaque = 0;
				for (VulkanicGalBridge.WorldLodColumnInstanceRecord instance : LAST_CONSUMED_VISIBLE_SEGMENTS) {
					if (instance.columnKey() == columnKey
						&& instance.columnGeneration() == column.generation()
						&& instance.layer() == 1) {
						consumedOpaque++;
					}
				}
				consumedOpaqueSegments += consumedOpaque;
				if (samples.size() < 8) {
					String payloadDifference = LAST_COLUMN_PAYLOAD_DIFFERENCES.get(columnKey);
					samples.add(
						"column=" + columnKey
							+ ",generation=" + column.generation()
							+ ",bounds=" + minX + "," + minZ + ",width=" + width
							+ ",published=" + published
							+ ",consumedOpaque=" + consumedOpaque
							+ ",payloadDifference=" + (payloadDifference == null ? "none" : payloadDifference)
					);
				}
			}
			return new ColumnCoverageDiagnostics(cachedColumns, publishedColumns, consumedOpaqueSegments, List.copyOf(samples));
		}
	}

	/**
	 * Capture-only proof that the consumed opaque draw covering a target block
	 * carries every requested semantic material on its copied quad sidecars.
	 * Unlike the wider column-table helper above, this deliberately rejects a
	 * reduced DH column whose source table knows a material while its emitted
	 * quads have already lost that identity. It has no route-selection effect.
	 */
	public static boolean hasLastConsumedVisibleColumnCoveringBlockWithExecutedOpaqueSemanticMaterialIdentities(
		int blockX,
		int blockZ,
		List<String> blockIds
	) {
		Objects.requireNonNull(blockIds, "blockIds");
		if (blockIds.isEmpty()) {
			return false;
		}
		List<String> prefixes = blockIds.stream().map(blockId -> {
			if (blockId == null || blockId.isBlank()) {
				throw new IllegalArgumentException("DH semantic material identity must be non-blank");
			}
			return blockId.endsWith("_STATE_") ? blockId : blockId + "_STATE_";
		}).toList();
		synchronized (COLUMNS) {
			Map<Integer, ColumnRenderSource.SemanticMaterialIdentity> visibleMaterials = new LinkedHashMap<>();
			for (VulkanicGalBridge.WorldLodColumnInstanceRecord instance : LAST_CONSUMED_VISIBLE_SEGMENTS) {
				if (instance.layer() != 1) {
					continue;
				}
				long columnKey = instance.columnKey();
				int minX = DhSectionPos.getMinCornerBlockX(columnKey);
				int minZ = DhSectionPos.getMinCornerBlockZ(columnKey);
				int width = DhSectionPos.getBlockWidth(columnKey);
				if (blockX < minX || blockX >= minX + width || blockZ < minZ || blockZ >= minZ + width) {
					continue;
				}
				LodColumnSnapshot column = publishedColumnLocked(columnKey);
				LodMaterialProvenanceSnapshot provenance = publishedMaterialProvenanceLocked(columnKey);
				if (column == null || column.generation() != instance.columnGeneration() || provenance == null) {
					continue;
				}
				for (int materialId : consumedOpaqueMaterialIds(column, provenance, instance.segmentIndex())) {
					if (materialId > ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE
						&& materialId <= provenance.semanticMaterials().size()) {
						visibleMaterials.putIfAbsent(materialId, provenance.semanticMaterials().get(materialId - 1));
					}
				}
			}
			return prefixes.stream().allMatch(prefix -> visibleMaterials.values().stream()
				.anyMatch(identity -> identity.blockStateIdentity().startsWith(prefix)));
		}
	}

	/**
	 * Capture-only spatial provenance check for one opaque DH quad. The broader
	 * palette helper above establishes that identities survived on consumed
	 * sidecars; this one also requires the matching sidecar to belong to a quad
	 * whose copied local geometry covers the requested world block. It remains
	 * observational and has no effect on DH route selection or rendering.
	 */
	public static boolean hasLastConsumedVisibleOpaqueSemanticMaterialAtBlock(
		int blockX,
		int blockZ,
		String blockId
	) {
		return hasLastConsumedVisibleOpaqueSemanticMaterialAtBlock(blockX, Integer.MIN_VALUE, blockZ, blockId);
	}

	/**
	 * Exact-cell variant of the capture-only provenance check. Unlike the
	 * historical X/Z helper, this refuses to certify a raised texture witness
	 * from an unrelated surface directly below it.
	 */
	public static boolean hasLastConsumedVisibleOpaqueSemanticMaterialAtBlock(
		int blockX,
		int blockY,
		int blockZ,
		String blockId
	) {
		if (blockId == null || blockId.isBlank()) {
			throw new IllegalArgumentException("DH semantic material identity must be non-blank");
		}
		String prefix = blockId.endsWith("_STATE_") ? blockId : blockId + "_STATE_";
		synchronized (COLUMNS) {
			List<VulkanicGalBridge.WorldLodColumnInstanceRecord> visibleSegments = lastExecutedWorldFrame > 0L
				? executedSegmentsForWorldFrameLocked(lastExecutedWorldFrame)
				: LAST_CONSUMED_VISIBLE_SEGMENTS;
			for (VulkanicGalBridge.WorldLodColumnInstanceRecord instance : visibleSegments) {
				if (instance.layer() != 1) {
					continue;
				}
				ExecutedVisibleSegmentSnapshot submittedSnapshot = lastExecutedWorldFrame > 0L
					? executedSegmentSnapshotLocked(lastExecutedWorldFrame, instance)
					: null;
				LodColumnSnapshot column = submittedSnapshot == null
					? publishedColumnLocked(instance.columnKey())
					: submittedSnapshot.column();
				LodMaterialProvenanceSnapshot provenance = submittedSnapshot == null
					? publishedMaterialProvenanceLocked(instance.columnKey())
					: submittedSnapshot.provenance();
				if (column == null || column.generation() != instance.columnGeneration() || provenance == null) {
					continue;
				}
				if (consumedOpaqueSegmentHasMaterialAtBlock(
					column, provenance, instance.segmentIndex(), blockX, blockY, blockZ, prefix
				)) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Capture-only evidence that a spatially matched, consumed opaque DH quad
	 * retains a source identity whose resolved block-model faces use one of the
	 * expected atlas sprites. This remains deliberately before FFI and has no
	 * route-selection effect: it guards the deterministic texture palette from
	 * treating a block-state name alone as texture-assignment proof.
	 */
	public static DistantHorizonsTextureProbeReceipt textureProbeReceipt(List<DistantHorizonsTextureProbe> probes) {
		return textureProbeReceipt(probes, false);
	}

	/**
	 * Capture-only counterpart for the Java OpenGL control. Legacy DH records
	 * the exact VBO segment it is about to draw in the pending visible list; it
	 * never hands that list to the Rust frame coordinator. Reading the consumed
	 * list here would therefore certify no Java draw by construction. This
	 * method stays observational: it resolves only copied CPU provenance for
	 * the segment selected by the legacy renderer and never exposes a VBO,
	 * changes route selection, or affects rendering.
	 */
	public static DistantHorizonsTextureProbeReceipt legacyTextureProbeReceipt(List<DistantHorizonsTextureProbe> probes) {
		return textureProbeReceipt(probes, true);
	}

	private static DistantHorizonsTextureProbeReceipt textureProbeReceipt(
		List<DistantHorizonsTextureProbe> probes,
		boolean legacyObservedSegments
	) {
		long executionWorldFrame = 0L;
		List<VulkanicGalBridge.WorldLodColumnInstanceRecord> visibleSegments;
		synchronized (COLUMNS) {
			if (legacyObservedSegments) {
				visibleSegments = List.copyOf(PENDING_VISIBLE_SEGMENTS);
			} else {
				executionWorldFrame = lastExecutedWorldFrame;
				visibleSegments = executionWorldFrame > 0L
					? executedSegmentsForWorldFrameLocked(executionWorldFrame)
					: LAST_CONSUMED_VISIBLE_SEGMENTS;
			}
		}
		if (probes == null || probes.isEmpty()) {
			return new DistantHorizonsTextureProbeReceipt(false, "no texture probes", executionWorldFrame, List.of());
		}
		List<DistantHorizonsTextureProbeResult> results = new ArrayList<>(probes.size());
		for (DistantHorizonsTextureProbe probe : probes) {
			if (probe == null || probe.blockId().isBlank() || probe.allowedSprites().isEmpty()) {
				return new DistantHorizonsTextureProbeReceipt(false, "invalid texture probe", executionWorldFrame, List.copyOf(results));
			}
			results.add(textureProbeResult(probe, visibleSegments, executionWorldFrame, legacyObservedSegments));
		}
		boolean matched = results.stream().allMatch(DistantHorizonsTextureProbeResult::matched);
		String status = matched
			? "ok"
			: results.stream()
				.filter(result -> !result.matched())
				.map(result -> result.blockX() + "," + result.blockZ() + ":" + result.status())
				.reduce((left, right) -> left + ";" + right)
				.orElse("texture probe mismatch");
		return new DistantHorizonsTextureProbeReceipt(matched, status, executionWorldFrame, List.copyOf(results));
	}

	private static DistantHorizonsTextureProbeResult textureProbeResult(
		DistantHorizonsTextureProbe probe,
		List<VulkanicGalBridge.WorldLodColumnInstanceRecord> visibleSegments,
		long executionWorldFrame,
		boolean legacyObservedSegments
	) {
		String expectedPrefix = probe.blockId().endsWith("_STATE_")
			? probe.blockId()
			: probe.blockId() + "_STATE_";
		int executedSegments = 0;
		int spatialQuads = 0;
		int materialMatches = 0;
		int exactVariantQuads = 0;
		int resolvedSpriteMatches = 0;
		synchronized (COLUMNS) {
			for (VulkanicGalBridge.WorldLodColumnInstanceRecord instance : visibleSegments) {
				if (instance.layer() != 1) {
					continue;
				}
				ExecutedVisibleSegmentSnapshot submittedSnapshot = executionWorldFrame > 0L
					? executedSegmentSnapshotLocked(executionWorldFrame, instance)
					: null;
				LodColumnSnapshot column = submittedSnapshot == null
					? publishedColumnLocked(instance.columnKey())
					: submittedSnapshot.column();
				LodMaterialProvenanceSnapshot provenance = submittedSnapshot == null
					? publishedMaterialProvenanceLocked(instance.columnKey())
					: submittedSnapshot.provenance();
				if (column == null || column.generation() != instance.columnGeneration() || provenance == null) {
					continue;
				}
				executedSegments++;
				for (OpaqueSegmentQuad quad : opaqueSegmentQuads(column, provenance, instance.segmentIndex())) {
					if (!opaqueQuadCoversBlock(
						column, quad.vertices(), quad.quadIndex(), probe.blockX(), probe.blockY(), probe.blockZ()
					)) {
						continue;
					}
					spatialQuads++;
					int materialId = quad.materialId();
					if (materialId <= ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE
						|| materialId > provenance.semanticMaterials().size()) {
						continue;
					}
					ColumnRenderSource.SemanticMaterialIdentity identity = provenance.semanticMaterials().get(materialId - 1);
					if (!identity.blockStateIdentity().startsWith(expectedPrefix)) {
						continue;
					}
					materialMatches++;
					if (quad.variantState() == ColumnRenderSource.SEMANTIC_VARIANT_EXACT) {
						exactVariantQuads++;
					}
					long resolutionPosition = quad.variantState() == ColumnRenderSource.SEMANTIC_VARIANT_EXACT
						&& quad.variantPosition() != 0L
						? quad.variantPosition()
						: BlockPos.asLong(probe.blockX(), probe.blockY(), probe.blockZ());
					DistantHorizonsFaceMaterialResolver.Resolution resolution =
						DistantHorizonsFaceMaterialResolver.resolveCurrentClientState(
							identity.blockStateIdentity(),
							resolutionPosition
						);
					if (!resolution.hasResolvedFaces()) {
						return new DistantHorizonsTextureProbeResult(
							probe.blockX(), probe.blockY(), probe.blockZ(), probe.blockId(), identity.blockStateIdentity(),
							false, resolution.status().name(), List.of(),
							textureProbeEvidence(executedSegments, spatialQuads, materialMatches, exactVariantQuads, resolvedSpriteMatches)
						);
					}
					List<String> sprites = resolution.faceLayers().values().stream()
						.flatMap(List::stream)
						.map(DistantHorizonsFaceMaterialResolver.FaceMaterial::spriteIdentity)
						.distinct()
						.sorted()
						.toList();
					boolean matched = sprites.stream().allMatch(probe.allowedSprites()::contains)
						&& sprites.containsAll(probe.requiredSprites());
					if (matched) {
						resolvedSpriteMatches++;
					}
					return new DistantHorizonsTextureProbeResult(
						probe.blockX(), probe.blockY(), probe.blockZ(), probe.blockId(), identity.blockStateIdentity(),
						matched, matched ? "ok" : "missing-required-or-unexpected-resolved-sprite", sprites,
						textureProbeEvidence(executedSegments, spatialQuads, materialMatches, exactVariantQuads, resolvedSpriteMatches)
					);
				}
			}
		}
		return new DistantHorizonsTextureProbeResult(
			probe.blockX(), probe.blockY(), probe.blockZ(), probe.blockId(), "", false,
			legacyObservedSegments ? "no-spatial-observed-material" : "no-spatial-consumed-material", List.of(),
			textureProbeEvidence(executedSegments, spatialQuads, materialMatches, exactVariantQuads, resolvedSpriteMatches)
		);
	}

	private static String textureProbeEvidence(
		int executedSegments,
		int spatialQuads,
		int materialMatches,
		int exactVariantQuads,
		int resolvedSpriteMatches
	) {
		return "executedSegments=" + executedSegments
			+ ",spatialQuads=" + spatialQuads
			+ ",materialMatches=" + materialMatches
			+ ",exactVariantQuads=" + exactVariantQuads
			+ ",resolvedSpriteMatches=" + resolvedSpriteMatches;
	}

	private static Set<Integer> consumedOpaqueMaterialIds(
		LodColumnSnapshot column,
		LodMaterialProvenanceSnapshot provenance,
		int compactSegmentIndex
	) {
		Set<Integer> result = new LinkedHashSet<>();
		for (OpaqueSegmentQuad quad : opaqueSegmentQuads(column, provenance, compactSegmentIndex)) {
			result.add(quad.materialId());
		}
		return Set.copyOf(result);
	}

	private static boolean consumedOpaqueSegmentHasMaterialAtBlock(
		LodColumnSnapshot column,
		LodMaterialProvenanceSnapshot provenance,
		int compactSegmentIndex,
		int blockX,
		int blockY,
		int blockZ,
		String expectedPrefix
	) {
		for (OpaqueSegmentQuad quad : opaqueSegmentQuads(column, provenance, compactSegmentIndex)) {
			int materialId = quad.materialId();
			if (materialId <= ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE
				|| materialId > provenance.semanticMaterials().size()) {
				continue;
			}
			ColumnRenderSource.SemanticMaterialIdentity identity = provenance.semanticMaterials().get(materialId - 1);
			if (identity.blockStateIdentity().startsWith(expectedPrefix)
				&& opaqueQuadCoversBlock(column, quad.vertices(), quad.quadIndex(), blockX, blockY, blockZ)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Aligns the copied opaque vertices with the per-quad material and variant
	 * sidecars once. Capture checks and Rust provenance construction must observe
	 * this same relationship; an invalid sidecar is evidence failure, never a
	 * guessed material assignment.
	 */
	private static List<OpaqueSegmentQuad> opaqueSegmentQuads(
		LodColumnSnapshot column,
		LodMaterialProvenanceSnapshot provenance,
		int compactSegmentIndex
	) {
		return segmentQuads(column, provenance, compactSegmentIndex, 1);
	}

	/**
	 * Matches one executed, compacted material segment to its copied per-quad
	 * provenance. Layers retain their original global compact index, so this is
	 * also suitable for the water stream whose indices follow opaque and the
	 * other transparent streams.
	 */
	private static List<OpaqueSegmentQuad> segmentQuads(
		LodColumnSnapshot column,
		LodMaterialProvenanceSnapshot provenance,
		int compactSegmentIndex,
		int layer
	) {
		List<LodBufferSnapshot> buffers = switch (layer) {
			case 1 -> column.opaque();
			case 2 -> column.transparentSide();
			case 3 -> column.transparentUp();
			case 4 -> column.transparentWaterUp();
			default -> List.of();
		};
		List<int[]> materialIds = switch (layer) {
			case 1 -> provenance.opaque();
			case 2 -> provenance.transparentSide();
			case 3 -> provenance.transparentUp();
			case 4 -> provenance.transparentWaterUp();
			default -> List.of();
		};
		List<byte[]> variantStates = switch (layer) {
			case 1 -> provenance.opaqueVariantStates();
			case 2 -> provenance.transparentSideVariantStates();
			case 3 -> provenance.transparentUpVariantStates();
			case 4 -> provenance.transparentWaterUpVariantStates();
			default -> List.of();
		};
		List<long[]> variantPositions = switch (layer) {
			case 1 -> provenance.opaqueVariantPositions();
			case 2 -> provenance.transparentSideVariantPositions();
			case 3 -> provenance.transparentUpVariantPositions();
			case 4 -> provenance.transparentWaterUpVariantPositions();
			default -> List.of();
		};
		if (compactSegmentIndex < 0 || materialIds.size() != variantStates.size()
			|| materialIds.size() != variantPositions.size()) {
			return List.of();
		}
		int compactIndex = switch (layer) {
			case 1 -> 0;
			case 2 -> nonEmptySegmentCount(column.opaque());
			case 3 -> nonEmptySegmentCount(column.opaque()) + nonEmptySegmentCount(column.transparentSide());
			case 4 -> nonEmptySegmentCount(column.opaque()) + nonEmptySegmentCount(column.transparentSide())
				+ nonEmptySegmentCount(column.transparentUp());
			default -> Integer.MAX_VALUE;
		};
		int[] sourceQuadOffsets = new int[materialIds.size()];
		for (LodBufferSnapshot buffer : buffers) {
			if (buffer.vertices().isEmpty()) {
				continue;
			}
			int sourceIndex = buffer.sourceBufferIndex();
			int quadCount = buffer.vertices().size() / 4;
			if (sourceIndex < 0 || sourceIndex >= materialIds.size()) {
				return List.of();
			}
			int[] sourceIds = materialIds.get(sourceIndex);
			byte[] sourceStates = variantStates.get(sourceIndex);
			long[] sourcePositions = variantPositions.get(sourceIndex);
			int quadOffset = sourceQuadOffsets[sourceIndex];
			if (sourceStates.length != sourceIds.length || sourcePositions.length != sourceIds.length
				|| quadOffset > sourceIds.length || quadCount > sourceIds.length - quadOffset) {
				return List.of();
			}
			sourceQuadOffsets[sourceIndex] += quadCount;
			if (compactIndex++ != compactSegmentIndex) {
				continue;
			}
			List<OpaqueSegmentQuad> quads = new ArrayList<>(quadCount);
			for (int quadIndex = 0; quadIndex < quadCount; quadIndex++) {
				int sidecarIndex = quadOffset + quadIndex;
				quads.add(new OpaqueSegmentQuad(
					buffer.vertices(), quadIndex, sourceIds[sidecarIndex], sourceStates[sidecarIndex], sourcePositions[sidecarIndex]
				));
			}
			return List.copyOf(quads);
		}
		return List.of();
	}

	private static int nonEmptySegmentCount(List<LodBufferSnapshot> buffers) {
		return (int) buffers.stream().filter(buffer -> !buffer.vertices().isEmpty()).count();
	}

	/**
	 * Capture-only receipt for the deterministic water plate. Unlike the route
	 * counter, this proves that the exact copied water geometry which covers a
	 * configured fixture cell reached the completed Rust submission.
	 */
	public static DistantHorizonsWaterProbeReceipt waterProbeReceipt(List<BlockPos> probes) {
		if (probes == null || probes.isEmpty()) {
			return new DistantHorizonsWaterProbeReceipt(false, "no-water-probes", 0L, List.of());
		}
		long executionWorldFrame;
		List<VulkanicGalBridge.WorldLodColumnInstanceRecord> visibleSegments;
		synchronized (COLUMNS) {
			executionWorldFrame = lastExecutedWorldFrame;
			visibleSegments = executionWorldFrame > 0L
				? executedSegmentsForWorldFrameLocked(executionWorldFrame)
				: LAST_CONSUMED_VISIBLE_SEGMENTS;
		}
		List<DistantHorizonsWaterProbeResult> results = new ArrayList<>(Math.min(probes.size(), 8));
		for (BlockPos probe : probes.stream().limit(8).toList()) {
			results.add(waterProbeResult(probe, visibleSegments, executionWorldFrame));
		}
		boolean matched = results.stream().allMatch(DistantHorizonsWaterProbeResult::matched);
		String status = matched ? "ok" : results.stream().filter(result -> !result.matched())
			.map(result -> result.blockX() + "," + result.blockY() + "," + result.blockZ() + ":" + result.status())
			.reduce((left, right) -> left + ";" + right).orElse("water-probe-mismatch");
		return new DistantHorizonsWaterProbeReceipt(matched, status, executionWorldFrame, List.copyOf(results));
	}

	/**
	 * Capture-only source receipt for the deterministic water plate. This is
	 * deliberately weaker than {@link #waterProbeReceipt(List)}: it proves that
	 * DH copied and published the exact fixture cell, but never treats that as
	 * evidence that a Rust draw executed. Keeping the two boundaries distinct
	 * makes a failed capture actionable without admitting unrelated world water.
	 */
	public static DistantHorizonsWaterProbeReceipt waterSourceProbeReceipt(List<BlockPos> probes) {
		if (probes == null || probes.isEmpty()) {
			return new DistantHorizonsWaterProbeReceipt(false, "no-water-probes", 0L, List.of());
		}
		List<DistantHorizonsWaterProbeResult> results = new ArrayList<>(Math.min(probes.size(), 8));
		synchronized (COLUMNS) {
			for (BlockPos probe : probes.stream().limit(8).toList()) {
				results.add(waterSourceProbeResultLocked(probe));
			}
		}
		boolean matched = results.stream().allMatch(DistantHorizonsWaterProbeResult::matched);
		String status = matched ? "ok" : results.stream().filter(result -> !result.matched())
			.map(result -> result.blockX() + "," + result.blockY() + "," + result.blockZ() + ":" + result.status())
			.reduce((left, right) -> left + ";" + right).orElse("water-source-probe-mismatch");
		return new DistantHorizonsWaterProbeReceipt(matched, status, 0L, List.copyOf(results));
	}

	/**
	 * Capture-only pre-publication counterpart to {@link #waterSourceProbeReceipt(List)}.
	 * It answers whether DH's copied CPU column contains the fixture water before
	 * asset acknowledgement. It is never accepted as visible or executed work.
	 */
	public static DistantHorizonsWaterProbeReceipt waterCachedProbeReceipt(List<BlockPos> probes) {
		if (probes == null || probes.isEmpty()) {
			return new DistantHorizonsWaterProbeReceipt(false, "no-water-probes", 0L, List.of());
		}
		List<DistantHorizonsWaterProbeResult> results = new ArrayList<>(Math.min(probes.size(), 8));
		synchronized (COLUMNS) {
			for (BlockPos probe : probes.stream().limit(8).toList()) {
				results.add(waterCachedProbeResultLocked(probe));
			}
		}
		boolean matched = results.stream().allMatch(DistantHorizonsWaterProbeResult::matched);
		String status = matched ? "ok" : results.stream().filter(result -> !result.matched())
			.map(result -> result.blockX() + "," + result.blockY() + "," + result.blockZ() + ":" + result.status())
			.reduce((left, right) -> left + ";" + right).orElse("water-cached-probe-mismatch");
		return new DistantHorizonsWaterProbeReceipt(matched, status, 0L, List.copyOf(results));
	}

	private static DistantHorizonsWaterProbeResult waterSourceProbeResultLocked(BlockPos probe) {
		for (Map.Entry<Long, LodColumnSnapshot> entry : PUBLISHED_COLUMNS.entrySet()) {
			long columnKey = entry.getKey();
			LodColumnSnapshot column = publishedColumnLocked(columnKey);
			LodMaterialProvenanceSnapshot provenance = publishedMaterialProvenanceLocked(columnKey);
			if (column == null || provenance == null) {
				continue;
			}
			int waterSegmentIndex = nonEmptySegmentCount(column.opaque())
				+ nonEmptySegmentCount(column.transparentSide())
				+ nonEmptySegmentCount(column.transparentUp());
			for (LodBufferSnapshot buffer : column.transparentWaterUp()) {
				if (buffer.vertices().isEmpty()) {
					continue;
				}
				for (OpaqueSegmentQuad quad : segmentQuads(column, provenance, waterSegmentIndex, 4)) {
					if (!waterTopQuadCoversBlock(column, quad.vertices(), quad.quadIndex(), probe)) {
						continue;
					}
					String materialIdentity = "";
					if (quad.materialId() > ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE
						&& quad.materialId() <= provenance.semanticMaterials().size()) {
						materialIdentity = provenance.semanticMaterials().get(quad.materialId() - 1).blockStateIdentity();
					}
					boolean waterMaterial = materialIdentity.startsWith("minecraft:water");
					return new DistantHorizonsWaterProbeResult(
						probe.getX(), probe.getY(), probe.getZ(), waterMaterial,
						waterMaterial ? "ok" : "spatial-water-layer-with-non-water-material",
						columnKey, column.generation(), waterSegmentIndex, quad.quadIndex(),
						column.originX(), column.originY(), column.originZ(), materialIdentity
					);
				}
				waterSegmentIndex++;
			}
		}
		return new DistantHorizonsWaterProbeResult(
			probe.getX(), probe.getY(), probe.getZ(), false, "no-spatial-published-water-quad",
			0L, 0L, -1, -1, 0, 0, 0, ""
		);
	}

	private static DistantHorizonsWaterProbeResult waterCachedProbeResultLocked(BlockPos probe) {
		for (Map.Entry<Long, LodColumnSnapshot> entry : COLUMNS.entrySet()) {
			long columnKey = entry.getKey();
			LodColumnSnapshot column = entry.getValue();
			LodMaterialProvenanceSnapshot provenance = MATERIAL_PROVENANCE.get(columnKey);
			if (provenance == null) {
				continue;
			}
			int waterSegmentIndex = nonEmptySegmentCount(column.opaque())
				+ nonEmptySegmentCount(column.transparentSide())
				+ nonEmptySegmentCount(column.transparentUp());
			for (LodBufferSnapshot buffer : column.transparentWaterUp()) {
				if (buffer.vertices().isEmpty()) {
					continue;
				}
				for (OpaqueSegmentQuad quad : segmentQuads(column, provenance, waterSegmentIndex, 4)) {
					if (!waterTopQuadCoversBlock(column, quad.vertices(), quad.quadIndex(), probe)) {
						continue;
					}
					String materialIdentity = "";
					if (quad.materialId() > ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE
						&& quad.materialId() <= provenance.semanticMaterials().size()) {
						materialIdentity = provenance.semanticMaterials().get(quad.materialId() - 1).blockStateIdentity();
					}
					boolean waterMaterial = materialIdentity.startsWith("minecraft:water");
					return new DistantHorizonsWaterProbeResult(
						probe.getX(), probe.getY(), probe.getZ(), waterMaterial,
						waterMaterial ? "ok" : "spatial-water-layer-with-non-water-material",
						columnKey, column.generation(), waterSegmentIndex, quad.quadIndex(),
						column.originX(), column.originY(), column.originZ(), materialIdentity
					);
				}
				waterSegmentIndex++;
			}
		}
		return new DistantHorizonsWaterProbeResult(
			probe.getX(), probe.getY(), probe.getZ(), false, "no-spatial-cached-water-quad",
			0L, 0L, -1, -1, 0, 0, 0, ""
		);
	}

	private static DistantHorizonsWaterProbeResult waterProbeResult(
		BlockPos probe,
		List<VulkanicGalBridge.WorldLodColumnInstanceRecord> visibleSegments,
		long executionWorldFrame
	) {
		synchronized (COLUMNS) {
			for (VulkanicGalBridge.WorldLodColumnInstanceRecord instance : visibleSegments) {
				if (instance.layer() != 4) {
					continue;
				}
				ExecutedVisibleSegmentSnapshot submittedSnapshot = executionWorldFrame > 0L
					? executedSegmentSnapshotLocked(executionWorldFrame, instance) : null;
				LodColumnSnapshot column = submittedSnapshot == null
					? publishedColumnLocked(instance.columnKey()) : submittedSnapshot.column();
				LodMaterialProvenanceSnapshot provenance = submittedSnapshot == null
					? publishedMaterialProvenanceLocked(instance.columnKey()) : submittedSnapshot.provenance();
				if (column == null || column.generation() != instance.columnGeneration() || provenance == null) {
					continue;
				}
				for (OpaqueSegmentQuad quad : segmentQuads(column, provenance, instance.segmentIndex(), 4)) {
					if (!waterTopQuadCoversBlock(column, quad.vertices(), quad.quadIndex(), probe)) {
						continue;
					}
					String materialIdentity = "";
					if (quad.materialId() > ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE
						&& quad.materialId() <= provenance.semanticMaterials().size()) {
						materialIdentity = provenance.semanticMaterials().get(quad.materialId() - 1).blockStateIdentity();
					}
					boolean waterMaterial = materialIdentity.startsWith("minecraft:water");
					return new DistantHorizonsWaterProbeResult(
						probe.getX(), probe.getY(), probe.getZ(), waterMaterial,
						waterMaterial ? "ok" : "spatial-water-layer-with-non-water-material",
						instance.columnKey(), instance.columnGeneration(), instance.segmentIndex(), quad.quadIndex(),
						column.originX(), column.originY(), column.originZ(), materialIdentity
					);
				}
			}
		}
		return new DistantHorizonsWaterProbeResult(
			probe.getX(), probe.getY(), probe.getZ(), false, "no-spatial-executed-water-quad",
			0L, 0L, -1, -1, 0, 0, 0, ""
		);
	}

	private record OpaqueSegmentQuad(
		List<LodVertex> vertices,
		int quadIndex,
		int materialId,
		byte variantState,
		long variantPosition
	) {
	}

	private static boolean opaqueQuadCoversBlock(
		LodColumnSnapshot column,
		List<LodVertex> vertices,
		int quadIndex,
		int blockX,
		int blockY,
		int blockZ
	) {
		int first = Math.multiplyExact(quadIndex, 4);
		if (first < 0 || first + 4 > vertices.size()) {
			return false;
		}
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int index = first; index < first + 4; index++) {
			LodVertex vertex = vertices.get(index);
			minX = Math.min(minX, column.originX() + vertex.localX());
			maxX = Math.max(maxX, column.originX() + vertex.localX());
			minZ = Math.min(minZ, column.originZ() + vertex.localZ());
			maxZ = Math.max(maxZ, column.originZ() + vertex.localZ());
			minY = Math.min(minY, column.originY() + vertex.localY());
			maxY = Math.max(maxY, column.originY() + vertex.localY());
		}
		return blockX >= minX && blockX <= maxX
			&& blockZ >= minZ && blockZ <= maxZ
			&& (blockY == Integer.MIN_VALUE || (blockY >= minY && blockY <= maxY));
	}

	/**
	 * A water-up quad lies on the top plane of a source water cell. The fixture
	 * identifies a water block position, while its emitted horizontal face is at
	 * {@code y + 1}; treating those as the same plane rejected genuine water
	 * geometry before it reached the final-frame gate.
	 */
	private static boolean waterTopQuadCoversBlock(
		LodColumnSnapshot column,
		List<LodVertex> vertices,
		int quadIndex,
		BlockPos probe
	) {
		int first = Math.multiplyExact(quadIndex, 4);
		if (first < 0 || first + 4 > vertices.size()) {
			return false;
		}
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int index = first; index < first + 4; index++) {
			LodVertex vertex = vertices.get(index);
			minX = Math.min(minX, column.originX() + vertex.localX());
			maxX = Math.max(maxX, column.originX() + vertex.localX());
			minZ = Math.min(minZ, column.originZ() + vertex.localZ());
			maxZ = Math.max(maxZ, column.originZ() + vertex.localZ());
			minY = Math.min(minY, column.originY() + vertex.localY());
			maxY = Math.max(maxY, column.originY() + vertex.localY());
		}
		return probe.getX() >= minX && probe.getX() <= maxX
			&& probe.getZ() >= minZ && probe.getZ() <= maxZ
			&& minY == maxY && minY == probe.getY() + 1;
	}

	public static void recordBuiltColumn(
		long columnKey,
		DhBlockPos origin,
		List<ByteBuffer> opaque,
		List<ByteBuffer> transparentSide,
		List<ByteBuffer> transparentUp,
		List<ByteBuffer> transparentWaterUp
	) {
		recordBuiltColumnSnapshot(columnKey, origin, opaque, transparentSide, transparentUp, transparentWaterUp, null);
	}

	/**
	 * Publishes geometry and its optional material provenance as one immutable
	 * transaction. The frame coordinator may flush pending columns immediately
	 * after this method returns, so installing provenance in a second lock scope
	 * can pair a new column with an old sidecar (or no sidecar at all).
	 */
	private static void recordBuiltColumnSnapshot(
		long columnKey,
		DhBlockPos origin,
		List<ByteBuffer> opaque,
		List<ByteBuffer> transparentSide,
		List<ByteBuffer> transparentUp,
		List<ByteBuffer> transparentWaterUp,
		LodMaterialProvenanceSnapshot provenance
	) {
		if (!enabled()) {
			return;
		}
		Objects.requireNonNull(origin, "origin");
		LodColumnSnapshot snapshot = new LodColumnSnapshot(
			columnKey,
			NEXT_GENERATION.getAndIncrement(),
			origin.getX(),
			origin.getY(),
			origin.getZ(),
			copyBuffers(opaque),
			copyBuffers(transparentSide),
			copyBuffers(transparentUp),
			copyBuffers(transparentWaterUp)
		);
		synchronized (COLUMNS) {
			if (!snapshot.hasSegments()) {
				removeColumnLocked(columnKey);
				return;
			}
			LodColumnSnapshot replaced = COLUMNS.get(columnKey);
			if (replaced != null && replaced.hasSamePayload(snapshot)) {
				// DH can rebuild an unchanged visible column on consecutive frames.
				// Keep its acknowledged semantic generation stable so the same Rust
				// asset remains eligible until its copied payload actually changes.
				if (provenance != null) {
					replaceMaterialProvenanceLocked(columnKey, provenance);
					if (retainsLegacyObservationSnapshots()) {
						PUBLISHED_MATERIAL_PROVENANCE.put(columnKey, provenance);
					} else {
						// Material identities can change while the compact geometry bytes
						// remain the same. Re-publish the acknowledged immutable column
						// with its new copied sidecar instead of leaving Rust stale.
						PENDING_COLUMNS.put(columnKey, replaced);
					}
				} else {
					removeMaterialProvenanceLocked(columnKey);
				}
				semanticColumnsBuilt++;
				semanticColumnsReused++;
				return;
			}
			if (replaced != null) {
				semanticColumnsReplaced++;
				lastPayloadDifference = replaced.payloadDifference(snapshot);
				LAST_COLUMN_PAYLOAD_DIFFERENCES.put(columnKey, lastPayloadDifference);
			}
			COLUMNS.put(columnKey, snapshot);
			if (provenance == null) {
				removeMaterialProvenanceLocked(columnKey);
			} else {
				replaceMaterialProvenanceLocked(columnKey, provenance);
			}
			if (replaced != null) {
				retainedBytes -= replaced.byteSize();
			}
			retainedBytes += snapshot.byteSize();
			PENDING_RETIREMENTS.remove(columnKey);
			if (retainsLegacyObservationSnapshots()) {
				// Java's VBO lifecycle may close this container before its real draw
				// reaches the capture hook. Publish only this copied observation so
				// recordVisibleSegment can correlate that draw; no FFI update is made.
				PUBLISHED_GENERATIONS.put(columnKey, snapshot.generation());
				PUBLISHED_COLUMNS.put(columnKey, snapshot);
				if (provenance != null) {
					PUBLISHED_MATERIAL_PROVENANCE.put(columnKey, provenance);
				}
				PENDING_COLUMNS.remove(columnKey);
			} else {
				PENDING_COLUMNS.put(columnKey, snapshot);
			}
			trimRetainedColumnsLocked(MAX_RETAINED_COLUMNS, MAX_RETAINED_BYTES);
			semanticColumnsBuilt++;
		}
	}

	/**
	 * Records a bounded, CPU-owned material provenance sidecar. The legacy
	 * column bytes and the Java-to-Rust LOD ABI remain unchanged until a
	 * complete atlas/material contract is explicitly admitted.
	 */
	public static void recordBuiltColumn(
		long columnKey,
		DhBlockPos origin,
		List<ColumnRenderSource.SemanticMaterialIdentity> semanticMaterials,
		LodQuadBuilder.VertexBufferBuild opaque,
		LodQuadBuilder.VertexBufferBuild transparentSide,
		LodQuadBuilder.VertexBufferBuild transparentUp,
		LodQuadBuilder.VertexBufferBuild transparentWaterUp
	) {
			recordBuiltColumn(
				columnKey,
				origin,
				semanticMaterials,
				new LodQuadBuilder.SemanticQuadCoverage(0, 0, 0),
				new LodQuadBuilder.SemanticQuadCoverage(0, 0, 0),
				opaque,
			transparentSide,
			transparentUp,
			transparentWaterUp
		);
	}

	/**
	 * Records a bounded, CPU-owned material provenance sidecar together with
	 * pre-compaction source coverage for capture diagnostics.
	 */
	public static void recordBuiltColumn(
		long columnKey,
		DhBlockPos origin,
		List<ColumnRenderSource.SemanticMaterialIdentity> semanticMaterials,
		LodQuadBuilder.SemanticQuadCoverage inputCoverage,
		LodQuadBuilder.SemanticQuadCoverage outputCoverage,
		LodQuadBuilder.VertexBufferBuild opaque,
		LodQuadBuilder.VertexBufferBuild transparentSide,
		LodQuadBuilder.VertexBufferBuild transparentUp,
		LodQuadBuilder.VertexBufferBuild transparentWaterUp
	) {
		if (!enabled()) {
			return;
		}
		Objects.requireNonNull(semanticMaterials, "semanticMaterials");
		Objects.requireNonNull(inputCoverage, "inputCoverage");
		Objects.requireNonNull(outputCoverage, "outputCoverage");
		LodMaterialProvenanceSnapshot provenance = new LodMaterialProvenanceSnapshot(
			semanticMaterials,
			inputCoverage,
			outputCoverage,
			copyMaterialIds(opaque, semanticMaterials.size()),
			copyVariantStates(opaque),
			copyVariantPositions(opaque),
			copyMaterialIds(transparentSide, semanticMaterials.size()),
			copyVariantStates(transparentSide),
			copyVariantPositions(transparentSide),
			copyMaterialIds(transparentUp, semanticMaterials.size()),
			copyVariantStates(transparentUp),
			copyVariantPositions(transparentUp),
			copyMaterialIds(transparentWaterUp, semanticMaterials.size())
			,copyVariantStates(transparentWaterUp),
			copyVariantPositions(transparentWaterUp)
		);
		recordBuiltColumnSnapshot(
			columnKey, origin,
			opaque.vertexBuffers(), transparentSide.vertexBuffers(),
			transparentUp.vertexBuffers(), transparentWaterUp.vertexBuffers(),
			provenance
		);
	}

	private static List<int[]> copyMaterialIds(
		LodQuadBuilder.VertexBufferBuild build,
		int semanticMaterialCount
	) {
		Objects.requireNonNull(build, "build");
		if (build.vertexBuffers().size() != build.semanticMaterialIds().size()) {
			throw new IllegalArgumentException("Distant Horizons semantic material sidecars must align with vertex buffers");
		}
		List<int[]> copied = new ArrayList<>(build.vertexBuffers().size());
		for (int index = 0; index < build.vertexBuffers().size(); index++) {
			ByteBuffer buffer = Objects.requireNonNull(build.vertexBuffers().get(index), "vertexBuffer").duplicate();
			if (buffer.remaining() % LodBufferContainer.QUADS_BYTE_SIZE != 0) {
				throw new IllegalArgumentException("Distant Horizons semantic material sidecar has a non-quad-aligned vertex buffer");
			}
			int[] source = Objects.requireNonNull(build.semanticMaterialIds().get(index), "semanticMaterialIds");
			int expectedQuads = buffer.remaining() / LodBufferContainer.QUADS_BYTE_SIZE;
			if (source.length != expectedQuads) {
				throw new IllegalArgumentException("Distant Horizons semantic material sidecar does not contain one ID per quad");
			}
			for (int materialId : source) {
				if (materialId < ColumnRenderSource.SEMANTIC_MATERIAL_MIXED || materialId > semanticMaterialCount) {
					throw new IllegalArgumentException("Distant Horizons semantic material ID is outside its builder-local table: " + materialId);
				}
			}
			// The snapshot constructor takes the caller-independent copy. Avoid a
			// second transient clone in this diagnostic-only capture path.
			copied.add(source);
		}
		return List.copyOf(copied);
	}

	private static List<byte[]> copyVariantStates(LodQuadBuilder.VertexBufferBuild build) {
		Objects.requireNonNull(build, "build");
		if (build.semanticVariantStates().isEmpty()) {
			return build.vertexBuffers().stream()
				.map(buffer -> new byte[buffer.duplicate().remaining() / LodBufferContainer.QUADS_BYTE_SIZE])
				.toList();
		}
		List<byte[]> copied = new ArrayList<>(build.vertexBuffers().size());
		for (int index = 0; index < build.vertexBuffers().size(); index++) {
			byte[] source = Objects.requireNonNull(build.semanticVariantStates().get(index), "semanticVariantStates");
			int expected = build.vertexBuffers().get(index).duplicate().remaining() / LodBufferContainer.QUADS_BYTE_SIZE;
			if (source.length != expected) {
				throw new IllegalArgumentException("Distant Horizons semantic variant state sidecar does not contain one state per quad");
			}
			copied.add(source);
		}
		return List.copyOf(copied);
	}

	private static List<long[]> copyVariantPositions(LodQuadBuilder.VertexBufferBuild build) {
		Objects.requireNonNull(build, "build");
		if (build.semanticVariantPositions().isEmpty()) {
			return build.vertexBuffers().stream()
				.map(buffer -> new long[buffer.duplicate().remaining() / LodBufferContainer.QUADS_BYTE_SIZE])
				.toList();
		}
		List<long[]> copied = new ArrayList<>(build.vertexBuffers().size());
		for (int index = 0; index < build.vertexBuffers().size(); index++) {
			long[] source = Objects.requireNonNull(build.semanticVariantPositions().get(index), "semanticVariantPositions");
			int expected = build.vertexBuffers().get(index).duplicate().remaining() / LodBufferContainer.QUADS_BYTE_SIZE;
			if (source.length != expected) {
				throw new IllegalArgumentException("Distant Horizons semantic variant position sidecar does not contain one position per quad");
			}
			copied.add(source);
		}
		return List.copyOf(copied);
	}

	/** Records a real quadtree request to materialize copied CPU column data.
	 * The counter is capture-lifetime bounded metadata, never a readiness
	 * substitute. */
	public static void recordSemanticBuildAttempt(long columnKey) {
		if (usesRustWholeFrameSemanticBuild()) {
			synchronized (COLUMNS) {
				semanticBuildAttempts++;
			}
		}
	}

	public static void removeColumn(long columnKey) {
		if (retainsLegacyObservationSnapshots()) {
			return;
		}
		synchronized (COLUMNS) {
			removeColumnLocked(columnKey);
		}
	}

	/** Starts the real DH render-list capture for one non-deferred game frame. */
	public static boolean beginVisibleFrame(RenderParams renderParams) {
		if (!enabled()) {
			return false;
		}
		Objects.requireNonNull(renderParams, "renderParams");
		// DhApiRenderParam already supplies the model-view transform used by the
		// authoritative DH shader path. Keep that semantic value intact: applying
		// the exact camera translation again changes the source contract and makes
		// DH depth reconstruction disagree with the geometry pass.
		boolean hasExactCamera = renderParams.exactCameraPosition != null;
		float[] modelViewValues = copyDhModelViewForRust(renderParams.dhModelViewMatrix.getValuesAsArray());
		Mat4f cameraRelativeModelView = new Mat4f(renderParams.dhModelViewMatrix);
		Mat4f combinedMatrix = new Mat4f(renderParams.dhProjectionMatrix);
		combinedMatrix.multiply(cameraRelativeModelView);
		// DH exposes matrices in row-major order. The semantic ABI is column-major,
		// matching the Rust-owned shader contract, so normalize here at the one
		// producer boundary rather than making either backend infer DH layout.
		float[] combinedValues = rowMajorToColumnMajor(combinedMatrix.getValuesAsArray());
		float[] projectionValues = rowMajorToColumnMajor(renderParams.dhProjectionMatrix.getValuesAsArray());
		// DhApiMat4f.invert() scales the source matrix by its determinant instead
		// of calculating its inverse. Do not let that implementation leak into
		// the copied shader semantic contract: deferred DH shaders reconstruct
		// view-space positions from this exact inverse.
		float[] projectionInverseValues = invertColumnMajorMatrix(projectionValues);
		String matrixStatus = hasExactCamera
			? matrixStatus(combinedValues, modelViewValues, projectionValues, projectionInverseValues)
			: "missing-exact-camera";
		String matrixDetail = matrixDetail(renderParams, modelViewValues, projectionValues, combinedValues, projectionInverseValues);
		if (!"finite".equals(matrixStatus)) {
			synchronized (COLUMNS) {
				PENDING_VISIBLE_SEGMENTS.clear();
				nextVisibleOrder = 0;
				PENDING_RENDER_FRAME = VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
				routeFrame++;
				routeDecision = "rejected";
				routeReason = "missing-exact-camera".equals(matrixStatus)
					? "missing-dh-camera-semantics"
					: "non-finite-dh-render-matrix";
				routeMatrixStatus = matrixStatus;
				routeMatrixDetail = matrixDetail;
				routeOpaqueSegments = 0;
				resetExactAtlasIdentityCoverage();
				routeTransparentSegments = 0;
				routeWaterSegments = 0;
				routeVisibleColumns = 0;
				routeUnpublishedVisibleColumns = 0;
				routeCachedColumns = COLUMNS.size();
				routeSelected = false;
			}
			return false;
		}
		float clipDistance = RenderUtil.getNearClipPlaneInBlocksForFading(renderParams.partialTicks);
		if (!Config.Client.Advanced.Debugging.lodOnlyMode.get()) {
			clipDistance += 16.0F;
		}
		if (RenderUtil.getHeightBasedNearClipOverride() != -1) {
			clipDistance = 1.0F;
		}
		int earthCurveRatio = Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.get();
		int flags = 0;
		if (Config.Client.Advanced.Debugging.enableWhiteWorld.get()) {
			flags |= RENDER_FLAG_WHITE_WORLD;
		}
		if (Config.Client.Advanced.Graphics.Quality.ditherDhFade.get()) {
			flags |= RENDER_FLAG_DITHER_FADE;
		}
		if (Config.Client.Advanced.Graphics.NoiseTexture.enableNoiseTexture.get()) {
			flags |= RENDER_FLAG_NOISE;
		}
		if (earthCurveRatio != 0) {
			flags |= RENDER_FLAG_EARTH_CURVE;
		}
		VulkanicGalBridge.WorldLodRenderFrameRecord renderFrame = new VulkanicGalBridge.WorldLodRenderFrameRecord(
			true,
			flags,
			renderParams.worldYOffset,
			combinedValues,
			modelViewValues,
			projectionValues,
			projectionInverseValues,
			clipDistance,
			0.01F,
			Config.Client.Advanced.Graphics.NoiseTexture.noiseIntensity.get().floatValue(),
			earthCurveRatio == 0 ? 0.0F : 6_371_000.0F / earthCurveRatio,
			Config.Client.Advanced.Graphics.NoiseTexture.noiseSteps.get(),
			Config.Client.Advanced.Graphics.NoiseTexture.noiseDropoff.get(),
			new float[] {
				(float)renderParams.exactCameraPosition.x,
				(float)renderParams.exactCameraPosition.y,
				(float)renderParams.exactCameraPosition.z
			}
		);
		synchronized (COLUMNS) {
			PENDING_VISIBLE_SEGMENTS.clear();
			nextVisibleOrder = 0;
			PENDING_RENDER_FRAME = renderFrame;
			routeFrame++;
			routeDecision = "preflight";
			routeReason = "pending";
			routeMatrixStatus = matrixStatus;
			routeMatrixDetail = matrixDetail;
			routeOpaqueSegments = 0;
			resetExactAtlasIdentityCoverage();
			routeTransparentSegments = 0;
			routeWaterSegments = 0;
			routeVisibleColumns = 0;
			routeUnpublishedVisibleColumns = 0;
			routeCachedColumns = COLUMNS.size();
			routeSelected = false;
		}
		return true;
	}

	/** Copies DH's authoritative model-view matrix without applying a second camera transform. */
	static float[] copyDhModelViewForRust(float[] dhModelViewRowMajor) {
		return rowMajorToColumnMajor(dhModelViewRowMajor);
	}

	private static boolean finite(float[] values) {
		for (float value : values) {
			if (!Float.isFinite(value)) {
				return false;
			}
		}
		return true;
	}

	private static String matrixStatus(
		float[] combined,
		float[] modelView,
		float[] projection,
		float[] projectionInverse
	) {
		if (!finite(projection)) {
			return "projection-non-finite";
		}
		if (!finite(modelView)) {
			return "model-view-non-finite";
		}
		if (!finite(combined)) {
			return "combined-non-finite";
		}
		if (projectionInverse == null) {
			return "projection-inverse-singular";
		}
		if (!finite(projectionInverse)) {
			return "projection-inverse-non-finite";
		}
		return matrixInverseResidual(projection, projectionInverse) <= 0.001F
			? "finite"
			: "projection-inverse-invalid";
	}

	private static String matrixDetail(
		RenderParams renderParams,
		float[] modelView,
		float[] projection,
		float[] combined,
		float[] projectionInverse
	) {
		return "mcProjection=" + matrixFiniteDetail(renderParams.mcProjectionMatrix.getValuesAsArray())
			+ ",dhProjection=" + matrixFiniteDetail(renderParams.dhProjectionMatrix.getValuesAsArray())
			+ ",modelView=" + matrixFiniteDetail(modelView)
			+ ",combined=" + matrixFiniteDetail(combined)
			+ ",projectionInverse=" + matrixFiniteDetail(projectionInverse)
			+ ",near=" + renderParams.nearClipPlane
			+ ",far=" + renderParams.farClipPlane;
	}

	private static String matrixFiniteDetail(float[] values) {
		if (values == null) {
			return "missing";
		}
		for (int index = 0; index < values.length; index++) {
			if (!Float.isFinite(values[index])) {
				return "non-finite[" + index + "]=" + values[index];
			}
		}
		return "finite";
	}

	/** Converts DH's documented row-major matrix array into ABI column-major order. */
	static float[] rowMajorToColumnMajor(float[] rowMajor) {
		if (rowMajor == null || rowMajor.length != 16) {
			throw new IllegalArgumentException("Distant Horizons matrix must contain exactly 16 values");
		}
		float[] columnMajor = new float[16];
		for (int row = 0; row < 4; row++) {
			for (int column = 0; column < 4; column++) {
				columnMajor[column * 4 + row] = rowMajor[row * 4 + column];
			}
		}
		return columnMajor;
	}

	/**
	 * Inverts one canonical ABI column-major matrix without relying on DH's
	 * broken mutable helper. Returning {@code null} makes singular source
	 * semantics reject before any native frame submission is attempted.
	 */
	static float[] invertColumnMajorMatrix(float[] matrix) {
		if (matrix == null || matrix.length != 16 || !finite(matrix)) {
			return null;
		}
		double[] inverse = new double[16];
		inverse[0] = matrix[5] * matrix[10] * matrix[15] - matrix[5] * matrix[11] * matrix[14]
			- matrix[9] * matrix[6] * matrix[15] + matrix[9] * matrix[7] * matrix[14]
			+ matrix[13] * matrix[6] * matrix[11] - matrix[13] * matrix[7] * matrix[10];
		inverse[4] = -matrix[4] * matrix[10] * matrix[15] + matrix[4] * matrix[11] * matrix[14]
			+ matrix[8] * matrix[6] * matrix[15] - matrix[8] * matrix[7] * matrix[14]
			- matrix[12] * matrix[6] * matrix[11] + matrix[12] * matrix[7] * matrix[10];
		inverse[8] = matrix[4] * matrix[9] * matrix[15] - matrix[4] * matrix[11] * matrix[13]
			- matrix[8] * matrix[5] * matrix[15] + matrix[8] * matrix[7] * matrix[13]
			+ matrix[12] * matrix[5] * matrix[11] - matrix[12] * matrix[7] * matrix[9];
		inverse[12] = -matrix[4] * matrix[9] * matrix[14] + matrix[4] * matrix[10] * matrix[13]
			+ matrix[8] * matrix[5] * matrix[14] - matrix[8] * matrix[6] * matrix[13]
			- matrix[12] * matrix[5] * matrix[10] + matrix[12] * matrix[6] * matrix[9];
		inverse[1] = -matrix[1] * matrix[10] * matrix[15] + matrix[1] * matrix[11] * matrix[14]
			+ matrix[9] * matrix[2] * matrix[15] - matrix[9] * matrix[3] * matrix[14]
			- matrix[13] * matrix[2] * matrix[11] + matrix[13] * matrix[3] * matrix[10];
		inverse[5] = matrix[0] * matrix[10] * matrix[15] - matrix[0] * matrix[11] * matrix[14]
			- matrix[8] * matrix[2] * matrix[15] + matrix[8] * matrix[3] * matrix[14]
			+ matrix[12] * matrix[2] * matrix[11] - matrix[12] * matrix[3] * matrix[10];
		inverse[9] = -matrix[0] * matrix[9] * matrix[15] + matrix[0] * matrix[11] * matrix[13]
			+ matrix[8] * matrix[1] * matrix[15] - matrix[8] * matrix[3] * matrix[13]
			- matrix[12] * matrix[1] * matrix[11] + matrix[12] * matrix[3] * matrix[9];
		inverse[13] = matrix[0] * matrix[9] * matrix[14] - matrix[0] * matrix[10] * matrix[13]
			- matrix[8] * matrix[1] * matrix[14] + matrix[8] * matrix[2] * matrix[13]
			+ matrix[12] * matrix[1] * matrix[10] - matrix[12] * matrix[2] * matrix[9];
		inverse[2] = matrix[1] * matrix[6] * matrix[15] - matrix[1] * matrix[7] * matrix[14]
			- matrix[5] * matrix[2] * matrix[15] + matrix[5] * matrix[3] * matrix[14]
			+ matrix[13] * matrix[2] * matrix[7] - matrix[13] * matrix[3] * matrix[6];
		inverse[6] = -matrix[0] * matrix[6] * matrix[15] + matrix[0] * matrix[7] * matrix[14]
			+ matrix[4] * matrix[2] * matrix[15] - matrix[4] * matrix[3] * matrix[14]
			- matrix[12] * matrix[2] * matrix[7] + matrix[12] * matrix[3] * matrix[6];
		inverse[10] = matrix[0] * matrix[5] * matrix[15] - matrix[0] * matrix[7] * matrix[13]
			- matrix[4] * matrix[1] * matrix[15] + matrix[4] * matrix[3] * matrix[13]
			+ matrix[12] * matrix[1] * matrix[7] - matrix[12] * matrix[3] * matrix[5];
		inverse[14] = -matrix[0] * matrix[5] * matrix[14] + matrix[0] * matrix[6] * matrix[13]
			+ matrix[4] * matrix[1] * matrix[14] - matrix[4] * matrix[2] * matrix[13]
			- matrix[12] * matrix[1] * matrix[6] + matrix[12] * matrix[2] * matrix[5];
		inverse[3] = -matrix[1] * matrix[6] * matrix[11] + matrix[1] * matrix[7] * matrix[10]
			+ matrix[5] * matrix[2] * matrix[11] - matrix[5] * matrix[3] * matrix[10]
			- matrix[9] * matrix[2] * matrix[7] + matrix[9] * matrix[3] * matrix[6];
		inverse[7] = matrix[0] * matrix[6] * matrix[11] - matrix[0] * matrix[7] * matrix[10]
			- matrix[4] * matrix[2] * matrix[11] + matrix[4] * matrix[3] * matrix[10]
			+ matrix[8] * matrix[2] * matrix[7] - matrix[8] * matrix[3] * matrix[6];
		inverse[11] = -matrix[0] * matrix[5] * matrix[11] + matrix[0] * matrix[7] * matrix[9]
			+ matrix[4] * matrix[1] * matrix[11] - matrix[4] * matrix[3] * matrix[9]
			- matrix[8] * matrix[1] * matrix[7] + matrix[8] * matrix[3] * matrix[5];
		inverse[15] = matrix[0] * matrix[5] * matrix[10] - matrix[0] * matrix[6] * matrix[9]
			- matrix[4] * matrix[1] * matrix[10] + matrix[4] * matrix[2] * matrix[9]
			+ matrix[8] * matrix[1] * matrix[6] - matrix[8] * matrix[2] * matrix[5];

		double determinant = matrix[0] * inverse[0] + matrix[1] * inverse[4]
			+ matrix[2] * inverse[8] + matrix[3] * inverse[12];
		if (!Double.isFinite(determinant) || Math.abs(determinant) <= 1.0E-12D) {
			return null;
		}
		float[] result = new float[16];
		for (int index = 0; index < result.length; index++) {
			result[index] = (float) (inverse[index] / determinant);
		}
		return finite(result) ? result : null;
	}

	static float matrixInverseResidual(float[] matrix, float[] inverse) {
		if (matrix == null || inverse == null || matrix.length != 16 || inverse.length != 16) {
			return Float.POSITIVE_INFINITY;
		}
		float maxResidual = 0.0F;
		for (int column = 0; column < 4; column++) {
			for (int row = 0; row < 4; row++) {
				float value = 0.0F;
				for (int term = 0; term < 4; term++) {
					value += matrix[term * 4 + row] * inverse[column * 4 + term];
				}
				float expected = row == column ? 1.0F : 0.0F;
				maxResidual = Math.max(maxResidual, Math.abs(value - expected));
			}
		}
		return maxResidual;
	}

	static void beginVisibleFrameForTest() {
		synchronized (COLUMNS) {
			PENDING_VISIBLE_SEGMENTS.clear();
			nextVisibleOrder = 0;
			PENDING_RENDER_FRAME = VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
			routeFrame++;
			routeDecision = "test-preflight";
			routeReason = "pending";
			routeOpaqueSegments = 0;
			resetExactAtlasIdentityCoverage();
			routeTransparentSegments = 0;
			routeWaterSegments = 0;
			routeVisibleColumns = 0;
			routeUnpublishedVisibleColumns = 0;
			routeCachedColumns = COLUMNS.size();
			routeSelected = false;
		}
	}

	static void beginRustOpaqueRouteFrameForTest() {
		synchronized (COLUMNS) {
			PENDING_VISIBLE_SEGMENTS.clear();
			nextVisibleOrder = 0;
			PENDING_RENDER_FRAME = new VulkanicGalBridge.WorldLodRenderFrameRecord(
				true, 0, 0, identityMatrix(), identityMatrix(), identityMatrix(), identityMatrix(),
				0.0F, 0.01F, 0.0F, 0.0F, 0, 0, new float[3]
			);
			routeFrame++;
			routeDecision = "test-preflight";
			routeReason = "pending";
			routeOpaqueSegments = 0;
			resetExactAtlasIdentityCoverage();
			routeTransparentSegments = 0;
			routeWaterSegments = 0;
			routeVisibleColumns = 0;
			routeUnpublishedVisibleColumns = 0;
			routeCachedColumns = 0;
			semanticBuildAttempts = 0L;
			semanticColumnsBuilt = 0L;
			semanticColumnsReused = 0L;
			semanticColumnsReplaced = 0L;
			lastPayloadDifference = "none";
			routeSelected = false;
			lastExecutedRouteFrame = 0L;
			lastExecutedWorldFrame = 0L;
			lastExecutedSubmission = 0L;
			lastExecutedCaptureFrame = 0L;
			lastExecutedInstances = 0;
			lastExecutedOpaqueInstances = 0;
			lastExecutedTransparentInstances = 0;
			lastExecutedWaterInstances = 0;
			lastExecutedFrameSemanticsEnabled = false;
		}
	}

	/**
	 * Records one segment only after the legacy renderer has selected a
	 * non-empty VBO for execution. The VBO identity itself never crosses this
	 * boundary; the original buffer index is compacted against the copied CPU
	 * segment list.
	 */
	public static void recordVisibleSegment(long columnKey, int layer, int sourceSegmentIndex) {
		if (!enabled()) {
			return;
		}
		synchronized (COLUMNS) {
			LodColumnSnapshot column = publishedColumnLocked(columnKey);
			if (column == null) {
				return;
			}
			List<Integer> segmentIndexes = column.compactSegmentIndexes(layer, sourceSegmentIndex);
			if (segmentIndexes.isEmpty()) {
				return;
			}
			if (PENDING_VISIBLE_SEGMENTS.size() + segmentIndexes.size() > MAX_VISIBLE_SEGMENTS) {
				throw new IllegalStateException("Distant Horizons visible LOD segment capture exceeds " + MAX_VISIBLE_SEGMENTS);
			}
			for (int segmentIndex : segmentIndexes) {
				PENDING_VISIBLE_SEGMENTS.add(new VulkanicGalBridge.WorldLodColumnInstanceRecord(
					column.columnKey(), column.generation(), layer, segmentIndex, nextVisibleOrder++
				));
			}
		}
	}

	/** Expands a real quadtree-visible column into copied material segments.
	 * The returned indices are global compact indices in the published column
	 * asset, never per-layer ordinals. Each material stream keeps its explicit
	 * source layer so Rust can select its private pipeline policy. */
	public static VisibleColumnSegments recordVisibleMaterialColumn(long columnKey) {
		if (!enabled()) {
			return VisibleColumnSegments.EMPTY;
		}
		synchronized (COLUMNS) {
			LodColumnSnapshot current = COLUMNS.get(columnKey);
			if (current == null) {
				return VisibleColumnSegments.EMPTY;
			}
			LodColumnSnapshot column = publishedColumnLocked(columnKey);
			if (column == null) {
				PENDING_VISIBLE_COLUMN_KEYS.add(columnKey);
				routeUnpublishedVisibleColumns++;
				return VisibleColumnSegments.EMPTY;
			}
			if (column.generation() != current.generation()) {
				// Preserve one acknowledged Rust asset during asynchronous rebuilds;
				// switch to the replacement only after its asset update is acknowledged.
				PENDING_VISIBLE_COLUMN_KEYS.add(columnKey);
			}
			int opaqueSegments = emittedSegmentCount(column.opaque());
			ExactAtlasIdentityCoverage exactAtlasCoverage = exactAtlasIdentityCoverage(column);
			routeExactAtlasIdentitySegments += exactAtlasCoverage.completeSegments();
			routeExactAtlasIdentityQuads += exactAtlasCoverage.completeQuads();
			routeExactAtlasMixedQuads += exactAtlasCoverage.mixedQuads();
			routeExactAtlasUnavailableQuads += exactAtlasCoverage.unavailableQuads();
			routeExactAtlasMissingProvenanceQuads += exactAtlasCoverage.missingProvenanceQuads();
			routeExactAtlasMisalignedProvenanceQuads += exactAtlasCoverage.misalignedProvenanceQuads();
			routeExactAtlasInvalidIdentityQuads += exactAtlasCoverage.invalidIdentityQuads();
			routeExactAtlasIdentityTableEntries += exactAtlasCoverage.identityTableEntries();
			routeExactAtlasInputKnownQuads += exactAtlasCoverage.inputKnownQuads();
			routeExactAtlasInputMixedQuads += exactAtlasCoverage.inputMixedQuads();
			routeExactAtlasInputUnavailableQuads += exactAtlasCoverage.inputUnavailableQuads();
			routeExactAtlasInputOpaqueKnownQuads += exactAtlasCoverage.inputOpaqueKnownQuads();
			routeExactAtlasInputOpaqueMixedQuads += exactAtlasCoverage.inputOpaqueMixedQuads();
			routeExactAtlasInputOpaqueUnavailableQuads += exactAtlasCoverage.inputOpaqueUnavailableQuads();
			routeExactAtlasOutputKnownQuads += exactAtlasCoverage.outputKnownQuads();
			routeExactAtlasOutputMixedQuads += exactAtlasCoverage.outputMixedQuads();
			routeExactAtlasOutputUnavailableQuads += exactAtlasCoverage.outputUnavailableQuads();
			routeExactAtlasOutputOpaqueKnownQuads += exactAtlasCoverage.outputOpaqueKnownQuads();
			routeExactAtlasOutputOpaqueMixedQuads += exactAtlasCoverage.outputOpaqueMixedQuads();
			routeExactAtlasOutputOpaqueUnavailableQuads += exactAtlasCoverage.outputOpaqueUnavailableQuads();
			if (routeExactAtlasCoverageSamples.size() < 12) {
				routeExactAtlasCoverageSamples.add(
					"column=" + columnKey
						+ ",generation=" + column.generation()
						+ ",inputOpaqueKnown=" + exactAtlasCoverage.inputOpaqueKnownQuads()
						+ ",outputOpaqueKnown=" + exactAtlasCoverage.outputOpaqueKnownQuads()
						+ ",outputOpaqueUnavailable=" + exactAtlasCoverage.outputOpaqueUnavailableQuads()
						+ ",copiedOpaqueQuads=" + exactAtlasCoverage.copiedOpaqueQuads()
						+ ",copiedUnavailable=" + exactAtlasCoverage.unavailableQuads()
						+ ",table=" + exactAtlasCoverage.identityTableEntries()
				);
			}
			int transparentSideSegments = emittedSegmentCount(column.transparentSide());
			int transparentUpSegments = emittedSegmentCount(column.transparentUp());
			int waterSegments = emittedSegmentCount(column.transparentWaterUp());
			int transparentSegments = transparentSideSegments + transparentUpSegments;
			int admittedSegments = opaqueSegments + transparentSegments + waterSegments;
			if (PENDING_VISIBLE_SEGMENTS.size() + admittedSegments > MAX_VISIBLE_SEGMENTS) {
				throw new IllegalStateException("Distant Horizons visible LOD segment capture exceeds " + MAX_VISIBLE_SEGMENTS);
			}
			appendVisibleSegments(column, 1, 0, column.opaque());
			appendVisibleSegments(column, 2, opaqueSegments, column.transparentSide());
			appendVisibleSegments(
				column, 3, opaqueSegments + transparentSideSegments, column.transparentUp()
			);
			appendVisibleSegments(
				column, 4, opaqueSegments + transparentSideSegments + transparentUpSegments,
				column.transparentWaterUp()
			);
			return new VisibleColumnSegments(opaqueSegments, transparentSegments, waterSegments);
		}
	}

	/** ABI/source compatibility name for callers that have not yet migrated to
	 * the material-route terminology. It now records every supported material
	 * stream, including the explicit water surface layer. */
	public static VisibleColumnSegments recordVisibleNonWaterColumn(long columnKey) {
		return recordVisibleMaterialColumn(columnKey);
	}

	private static int emittedSegmentCount(List<LodBufferSnapshot> buffers) {
		int count = 0;
		for (LodBufferSnapshot buffer : buffers) {
			if (!buffer.vertices().isEmpty()) count++;
		}
		return count;
	}

	private static void appendVisibleSegments(
		LodColumnSnapshot column,
		int layer,
		int globalSegmentOffset,
		List<LodBufferSnapshot> buffers
	) {
		int compactIndex = 0;
		for (LodBufferSnapshot buffer : buffers) {
			if (buffer.vertices().isEmpty()) continue;
			PENDING_VISIBLE_SEGMENTS.add(new VulkanicGalBridge.WorldLodColumnInstanceRecord(
				column.columnKey(), column.generation(), layer,
				globalSegmentOffset + compactIndex, nextVisibleOrder++
			));
			compactIndex++;
		}
	}

	/** Compatibility entrypoint retained for existing callers. */
	public static VisibleColumnSegments recordVisibleOpaqueColumn(long columnKey) {
		return recordVisibleMaterialColumn(columnKey);
	}

	/** Consumes only semantic visible-column references for the combined Rust frame. */
	public static List<VulkanicGalBridge.WorldLodColumnInstanceRecord> consumeVisibleSegments() {
		if (!enabled()) {
			return List.of();
		}
		synchronized (COLUMNS) {
			// The coordinator activates a pending replacement after this frame has
			// presented. The visible list therefore stays paired with the last
			// acknowledged immutable column generation for the entire submission.
			List<VulkanicGalBridge.WorldLodColumnInstanceRecord> result = routeSelected
				? List.copyOf(PENDING_VISIBLE_SEGMENTS)
				: List.of();
			if (!result.isEmpty()) {
				// The visible DH set uses the same copied block atlas as indexed
				// terrain meshes. Publish that semantic resource before the combined
				// frame consumes its exact-atlas draws; do not wait for a nearby
				// Sodium section to happen to build.
				RustGalTerrainRenderer.ensureTerrainAtlasAssetForWorldMesh();
			}
			LAST_CONSUMED_VISIBLE_SEGMENTS = result;
			LAST_CONSUMED_VISIBLE_SEGMENT_SNAPSHOTS = snapshotExecutedSegmentsLocked(result);
			PENDING_VISIBLE_SEGMENTS.clear();
			return result;
		}
	}

	/** Consumes resolved DH frame semantics without retaining a renderer object. */
	public static VulkanicGalBridge.WorldLodRenderFrameRecord consumeRenderFrame() {
		if (!enabled()) {
			return VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
		}
		synchronized (COLUMNS) {
			VulkanicGalBridge.WorldLodRenderFrameRecord result = PENDING_RENDER_FRAME;
			PENDING_RENDER_FRAME = VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
			return result;
		}
	}

	/**
	 * Marks the current copied frame only after Java has inspected the real DH
	 * render list and established that every visible segment is admitted by the
	 * Rust non-water material passes. This is a route decision, not renderer state.
	 */
	public static void markRustNonWaterRouteSelected() {
		if (!enabled()) {
			return;
		}
		synchronized (COLUMNS) {
			if (!PENDING_RENDER_FRAME.enabled()) {
				throw new IllegalStateException("Cannot select a Rust DH route without enabled frame semantics");
			}
			if (PENDING_VISIBLE_SEGMENTS.stream().anyMatch(instance -> instance.layer() < 1 || instance.layer() > 4)) {
				throw new IllegalStateException("Cannot select Rust DH material route with visible unsupported segments");
			}
			if ((PENDING_RENDER_FRAME.flags() & RENDER_FLAG_RUST_NON_WATER_ROUTE_SELECTED) != 0) {
				return;
			}
			PENDING_RENDER_FRAME = withFlags(
				PENDING_RENDER_FRAME,
				PENDING_RENDER_FRAME.flags() | RENDER_FLAG_RUST_NON_WATER_ROUTE_SELECTED
			);
			routeDecision = "selected";
			routeReason = "all-visible-material-segments-supported";
			routeOpaqueSegments = (int) PENDING_VISIBLE_SEGMENTS.stream().filter(instance -> instance.layer() == 1).count();
			routeTransparentSegments = (int) PENDING_VISIBLE_SEGMENTS.stream()
				.filter(instance -> instance.layer() == 2 || instance.layer() == 3).count();
			routeWaterSegments = (int) PENDING_VISIBLE_SEGMENTS.stream().filter(instance -> instance.layer() == 4).count();
			routeSelected = true;
		}
	}

	public static void markRustOpaqueRouteSelected() {
		markRustNonWaterRouteSelected();
	}

	/** Records why the explicit Rust whole-frame route was not selected. This is
	 * diagnostic route policy, not a rendering fallback. */
	public static void recordRustNonWaterRouteRejected(
		String reason,
		int opaqueSegments,
		int transparentSegments,
		int waterSegments
	) {
		if (!enabled()) {
			return;
		}
		Objects.requireNonNull(reason, "reason");
		if (opaqueSegments < 0 || transparentSegments < 0 || waterSegments < 0) {
			throw new IllegalArgumentException("Distant Horizons route diagnostics cannot contain negative segment counts");
		}
		synchronized (COLUMNS) {
			routeDecision = "rejected";
			routeReason = reason;
			routeOpaqueSegments = opaqueSegments;
			routeTransparentSegments = transparentSegments;
			routeWaterSegments = waterSegments;
			routeSelected = false;
			PENDING_VISIBLE_SEGMENTS.clear();
			PENDING_RENDER_FRAME = withFlags(
				PENDING_RENDER_FRAME,
				PENDING_RENDER_FRAME.flags() & ~RENDER_FLAG_RUST_NON_WATER_ROUTE_SELECTED
			);
		}
	}

	/** Compatibility entrypoint for early-preflight callers that do not have
	 * the actual visible-layer totals yet. */
	public static void recordRustOpaqueRouteRejected(String reason, int transparentSegments) {
		recordRustNonWaterRouteRejected(reason, 0, Math.max(0, transparentSegments), 0);
	}

	/**
	 * Records a successful whole-frame submission after the FFI call returns.
	 * This is capture-only correlation data: it contains no renderer object,
	 * backend handle, or Java draw state.
	 */
	public static void recordRustNonWaterRouteExecution(
		long worldFrame,
		long submission,
		long captureFrame,
		int instances,
		int opaqueInstances,
		int transparentInstances,
		boolean frameSemanticsEnabled
	) {
		recordRustMaterialRouteExecution(
			worldFrame, submission, captureFrame, instances, opaqueInstances, transparentInstances, 0,
			frameSemanticsEnabled
		);
	}

	/** Records successful execution of all supported DH material streams. */
	public static void recordRustMaterialRouteExecution(
		long worldFrame,
		long submission,
		long captureFrame,
		int instances,
		int opaqueInstances,
		int transparentInstances,
		int waterInstances,
		boolean frameSemanticsEnabled
	) {
		recordRustMaterialRouteExecution(
			worldFrame, submission, captureFrame, instances, opaqueInstances, transparentInstances, waterInstances,
			frameSemanticsEnabled, null
		);
	}

	/**
	 * Records execution together with the immutable semantic segment list that
	 * was submitted for this exact world frame. The coordinator owns this list,
	 * so capture correlation never depends on a later visibility traversal.
	 */
	public static void recordRustMaterialRouteExecution(
		long worldFrame,
		long submission,
		long captureFrame,
		int instances,
		int opaqueInstances,
		int transparentInstances,
		int waterInstances,
		boolean frameSemanticsEnabled,
		List<VulkanicGalBridge.WorldLodColumnInstanceRecord> submittedSegments
	) {
		if (worldFrame < 0L || submission <= 0L || captureFrame < 0L || instances <= 0
			|| opaqueInstances < 0 || transparentInstances < 0 || waterInstances < 0
			|| opaqueInstances + transparentInstances + waterInstances != instances || !frameSemanticsEnabled) {
			throw new IllegalArgumentException("invalid successful Rust DH material-route execution correlation");
		}
		synchronized (COLUMNS) {
			if (!routeSelected) {
				throw new IllegalStateException("cannot record Rust DH execution without the selected route");
			}
			lastExecutedRouteFrame = routeFrame;
			lastExecutedWorldFrame = worldFrame;
			lastExecutedSubmission = submission;
			lastExecutedCaptureFrame = captureFrame;
			lastExecutedInstances = instances;
			lastExecutedOpaqueInstances = opaqueInstances;
			lastExecutedTransparentInstances = transparentInstances;
			lastExecutedWaterInstances = waterInstances;
			lastExecutedFrameSemanticsEnabled = true;
			List<VulkanicGalBridge.WorldLodColumnInstanceRecord> executedSegments = submittedSegments == null
				? LAST_CONSUMED_VISIBLE_SEGMENTS
				: List.copyOf(submittedSegments);
			if (executedSegments.size() == instances) {
				List<ExecutedVisibleSegmentSnapshot> snapshots = executedSegments.equals(LAST_CONSUMED_VISIBLE_SEGMENTS)
					? LAST_CONSUMED_VISIBLE_SEGMENT_SNAPSHOTS
					: snapshotExecutedSegmentsLocked(executedSegments);
				EXECUTED_VISIBLE_SEGMENTS_BY_WORLD_FRAME.put(worldFrame, List.copyOf(snapshots));
				while (EXECUTED_VISIBLE_SEGMENTS_BY_WORLD_FRAME.size() > MAX_EXECUTED_VISIBLE_SEGMENT_SNAPSHOTS) {
					Long oldest = EXECUTED_VISIBLE_SEGMENTS_BY_WORLD_FRAME.keySet().iterator().next();
					EXECUTED_VISIBLE_SEGMENTS_BY_WORLD_FRAME.remove(oldest);
				}
			}
		}
	}

	private static List<ExecutedVisibleSegmentSnapshot> snapshotExecutedSegmentsLocked(
		List<VulkanicGalBridge.WorldLodColumnInstanceRecord> segments
	) {
		List<ExecutedVisibleSegmentSnapshot> snapshots = new ArrayList<>(segments.size());
		for (VulkanicGalBridge.WorldLodColumnInstanceRecord instance : segments) {
			LodColumnSnapshot column = publishedColumnLocked(instance.columnKey());
			LodMaterialProvenanceSnapshot provenance = publishedMaterialProvenanceLocked(instance.columnKey());
			if (column != null && column.generation() != instance.columnGeneration()) {
				column = null;
				provenance = null;
			}
			snapshots.add(new ExecutedVisibleSegmentSnapshot(instance, column, provenance));
		}
		return List.copyOf(snapshots);
	}

	public static void recordRustOpaqueRouteExecution(
		long worldFrame,
		long submission,
		long captureFrame,
		int instances,
		boolean frameSemanticsEnabled
	) {
		recordRustNonWaterRouteExecution(
			worldFrame, submission, captureFrame, instances, instances, 0, frameSemanticsEnabled
		);
	}

	/** Records bounded evidence from DH's real visible render list. It never
	 * retains VBOs or changes route selection; the counts distinguish a cold
	 * quadtree/buffer build from a missing semantic CPU snapshot. */
	public static void recordRenderListObservation(int visibleColumns) {
		if (!enabled()) {
			return;
		}
		if (visibleColumns < 0) {
			throw new IllegalArgumentException("visibleColumns must be non-negative");
		}
		synchronized (COLUMNS) {
			routeVisibleColumns = visibleColumns;
			routeCachedColumns = COLUMNS.size();
		}
	}

	/** Real visible columns rebuilt after the latest accepted Rust asset update
	 * must wait for publication instead of referencing an older Rust payload. */
	public static boolean hasUnpublishedVisibleColumns() {
		synchronized (COLUMNS) {
			return routeUnpublishedVisibleColumns > 0;
		}
	}

	/**
	 * Rust may select a visible DH frame only when every copied quad has an
	 * exact owned material identity. A colored-geometry approximation is not an
	 * acceptable whole-frame Vulkan fallback: it would make an unavailable
	 * texture/material look like admitted Distant Horizons support.
	 */
	public static boolean hasCompleteVisibleExactAtlasCoverage() {
		synchronized (COLUMNS) {
			return !PENDING_VISIBLE_SEGMENTS.isEmpty()
				&& routeExactAtlasOutputMixedQuads == 0
				&& routeExactAtlasOutputUnavailableQuads == 0
				&& routeExactAtlasInvalidIdentityQuads == 0;
		}
	}

	/** Bounded route evidence for deterministic captures and regression tests. */
	public static RouteDiagnostics routeDiagnosticsSnapshot() {
		synchronized (COLUMNS) {
			return new RouteDiagnostics(
				routeFrame,
				routeDecision,
				routeReason,
				routeMatrixStatus,
				routeMatrixDetail,
				routeOpaqueSegments,
				routeExactAtlasIdentitySegments,
				routeExactAtlasIdentityQuads,
				routeExactAtlasMixedQuads,
				routeExactAtlasUnavailableQuads,
				routeExactAtlasMissingProvenanceQuads,
				routeExactAtlasMisalignedProvenanceQuads,
				routeExactAtlasInvalidIdentityQuads,
				routeExactAtlasIdentityTableEntries,
				routeExactAtlasInputKnownQuads,
				routeExactAtlasInputMixedQuads,
				routeExactAtlasInputUnavailableQuads,
				routeExactAtlasInputOpaqueKnownQuads,
				routeExactAtlasInputOpaqueMixedQuads,
				routeExactAtlasInputOpaqueUnavailableQuads,
				routeExactAtlasOutputKnownQuads,
				routeExactAtlasOutputMixedQuads,
				routeExactAtlasOutputUnavailableQuads,
				routeExactAtlasOutputOpaqueKnownQuads,
				routeExactAtlasOutputOpaqueMixedQuads,
				routeExactAtlasOutputOpaqueUnavailableQuads,
				routeTransparentSegments,
				routeWaterSegments,
				routeVisibleColumns,
				routeCachedColumns,
				routeUnpublishedVisibleColumns,
				semanticBuildAttempts,
				semanticColumnsBuilt,
				semanticColumnsReused,
				semanticColumnsReplaced,
				lastPayloadDifference,
				List.copyOf(routeExactAtlasCoverageSamples),
				exactAtlasResolutionStatusSummary(),
				List.copyOf(routeExactAtlasResolutionSamples),
				retainedBytes,
				oversizedColumnCountLocked(),
				PENDING_RENDER_FRAME.enabled(),
				routeSelected,
				lastExecutedRouteFrame,
				lastExecutedWorldFrame,
				lastExecutedSubmission,
				lastExecutedCaptureFrame,
				lastExecutedInstances,
				lastExecutedOpaqueInstances,
				lastExecutedTransparentInstances,
				lastExecutedWaterInstances,
				lastExecutedFrameSemanticsEnabled
			);
		}
	}

	/** Clears a rejected preflight while keeping the real frame semantics for
	 * the ordinary Java route that will run immediately afterward. */
	public static void discardPreflightVisibleSegments() {
		if (!enabled()) {
			return;
		}
		synchronized (COLUMNS) {
			PENDING_VISIBLE_SEGMENTS.clear();
			LAST_CONSUMED_VISIBLE_SEGMENTS = List.of();
			LAST_CONSUMED_VISIBLE_SEGMENT_SNAPSHOTS = List.of();
			EXECUTED_VISIBLE_SEGMENTS_BY_WORLD_FRAME.clear();
			nextVisibleOrder = 0;
			if ((PENDING_RENDER_FRAME.flags() & RENDER_FLAG_RUST_OPAQUE_ROUTE_SELECTED) != 0) {
				PENDING_RENDER_FRAME = withFlags(
					PENDING_RENDER_FRAME,
					PENDING_RENDER_FRAME.flags() & ~RENDER_FLAG_RUST_OPAQUE_ROUTE_SELECTED
				);
			}
			routeSelected = false;
			lastExecutedRouteFrame = 0L;
			lastExecutedWorldFrame = 0L;
			lastExecutedSubmission = 0L;
			lastExecutedCaptureFrame = 0L;
			lastExecutedInstances = 0;
			lastExecutedOpaqueInstances = 0;
			lastExecutedTransparentInstances = 0;
			lastExecutedWaterInstances = 0;
			lastExecutedFrameSemanticsEnabled = false;
		}
	}

	public static void clear() {
		synchronized (COLUMNS) {
			for (Map.Entry<Long, Long> published : PUBLISHED_GENERATIONS.entrySet()) {
				PENDING_RETIREMENTS.put(published.getKey(), published.getValue());
			}
			COLUMNS.clear();
			PUBLISHED_COLUMNS.clear();
			MATERIAL_PROVENANCE.clear();
			PUBLISHED_MATERIAL_PROVENANCE.clear();
			PENDING_COLUMNS.clear();
			LAST_COLUMN_PAYLOAD_DIFFERENCES.clear();
			PENDING_VISIBLE_SEGMENTS.clear();
			LAST_CONSUMED_VISIBLE_SEGMENTS = List.of();
			LAST_CONSUMED_VISIBLE_SEGMENT_SNAPSHOTS = List.of();
			EXECUTED_VISIBLE_SEGMENTS_BY_WORLD_FRAME.clear();
			PENDING_RENDER_FRAME = VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
			nextVisibleOrder = 0;
			routeDecision = "cleared";
			routeReason = "world-unload";
			routeMatrixStatus = "not-observed";
			routeMatrixDetail = "not-observed";
			routeOpaqueSegments = 0;
			resetExactAtlasIdentityCoverage();
			routeTransparentSegments = 0;
			routeWaterSegments = 0;
			routeVisibleColumns = 0;
			routeUnpublishedVisibleColumns = 0;
			routeCachedColumns = 0;
			semanticBuildAttempts = 0L;
			semanticColumnsBuilt = 0L;
			semanticColumnsReused = 0L;
			semanticColumnsReplaced = 0L;
			lastPayloadDifference = "none";
			routeExactAtlasResolutionStatusCounts.clear();
			routeExactAtlasResolutionSamples.clear();
			routeSelected = false;
			lastExecutedRouteFrame = 0L;
			lastExecutedWorldFrame = 0L;
			lastExecutedSubmission = 0L;
			lastExecutedCaptureFrame = 0L;
			lastExecutedInstances = 0;
			lastExecutedOpaqueInstances = 0;
			lastExecutedTransparentInstances = 0;
			lastExecutedWaterInstances = 0;
			lastExecutedFrameSemanticsEnabled = false;
			retainedBytes = 0L;
			WATER_SOURCE_INPUT_TRACES.clear();
		}
	}

	private static VulkanicGalBridge.WorldLodRenderFrameRecord withFlags(
		VulkanicGalBridge.WorldLodRenderFrameRecord frame,
		int flags
	) {
		return new VulkanicGalBridge.WorldLodRenderFrameRecord(
			frame.enabled(), flags, frame.worldYOffset(), frame.combinedMatrix(), frame.modelViewMatrix(),
			frame.projectionMatrix(), frame.projectionInverseMatrix(), frame.clipDistance(),
			frame.microOffset(), frame.noiseIntensity(), frame.earthRadius(), frame.noiseSteps(), frame.noiseDropoff(),
			frame.cameraWorldPosition()
		);
	}

	private static LodColumnSnapshot publishedColumnLocked(long columnKey) {
		LodColumnSnapshot column = PUBLISHED_COLUMNS.get(columnKey);
		if (column == null) {
			return null;
		}
		Long publishedGeneration = PUBLISHED_GENERATIONS.get(columnKey);
		return publishedGeneration != null && publishedGeneration.longValue() == column.generation()
			? column
			: null;
	}

	private static LodMaterialProvenanceSnapshot publishedMaterialProvenanceLocked(long columnKey) {
		return PUBLISHED_MATERIAL_PROVENANCE.get(columnKey);
	}

	private static float[] identityMatrix() {
		return new float[] {
			1.0F, 0.0F, 0.0F, 0.0F,
			0.0F, 1.0F, 0.0F, 0.0F,
			0.0F, 0.0F, 1.0F, 0.0F,
			0.0F, 0.0F, 0.0F, 1.0F
		};
	}

	/**
	 * Flushes immutable CPU LOD assets only while the caller has already
	 * selected the Rust Vulkan whole-frame route. This performs no rendering;
	 * legacy Distant Horizons remains the sole renderer until a later LOD pass
	 * is explicitly admitted.
	 */
	public static VulkanicGalBridge.Status flushPendingAssets(VulkanicGalBridge bridge) {
		if (!enabled() || bridge == null) {
			return null;
		}
		PendingAssetUpdate update = pendingUpdate();
		if (update == null) {
			return null;
		}
		try {
			VulkanicGalBridge.Status status = bridge.updateWorldLodAssets(
				update.generation(), update.assets(), update.retirements(), update.materialProvenance()
			);
			acknowledge(update);
			return status;
		} catch (RuntimeException error) {
			releaseInFlightAssets(update);
			throw error;
		}
	}

	private static PendingAssetUpdate pendingUpdate() {
		synchronized (COLUMNS) {
			if (PENDING_COLUMNS.isEmpty() && PENDING_RETIREMENTS.isEmpty()) {
				return null;
			}
			List<LodColumnSnapshot> selectedSnapshots = selectPendingAssetSnapshotsLocked();
			if (selectedSnapshots.isEmpty() && PENDING_RETIREMENTS.isEmpty()) {
				return null;
			}
			List<VulkanicGalBridge.WorldLodColumnAssetRecord> assets = new ArrayList<>(selectedSnapshots.size());
			List<VulkanicGalBridge.WorldLodColumnMaterialProvenanceRecord> materialProvenance =
				new ArrayList<>(selectedSnapshots.size());
			Map<Long, LodMaterialProvenanceSnapshot> selectedProvenance = new LinkedHashMap<>();
			for (LodColumnSnapshot snapshot : selectedSnapshots) {
				assets.add(snapshot.toBridgeRecord());
				LodMaterialProvenanceSnapshot provenance = MATERIAL_PROVENANCE.get(snapshot.columnKey());
				if (provenance != null) {
					materialProvenance.add(snapshot.toBridgeMaterialProvenance(provenance));
					selectedProvenance.put(snapshot.columnKey(), provenance);
				}
			}
			List<VulkanicGalBridge.WorldLodColumnRetirementRecord> retirements = PENDING_RETIREMENTS.entrySet().stream()
				.map(entry -> new VulkanicGalBridge.WorldLodColumnRetirementRecord(entry.getKey(), entry.getValue()))
				.toList();
			return new PendingAssetUpdate(
				NEXT_UPDATE_GENERATION.get(), List.copyOf(selectedSnapshots), List.copyOf(assets), retirements,
				List.copyOf(materialProvenance), Map.copyOf(selectedProvenance)
			);
		}
	}

	private static List<LodColumnSnapshot> selectPendingAssetSnapshotsLocked() {
		List<LodColumnSnapshot> selected = new ArrayList<>(MAX_PENDING_ASSET_COLUMNS_PER_UPDATE);
		long selectedBytes = 0L;
		List<LodColumnSnapshot> ordered = new ArrayList<>(PENDING_COLUMNS.size());
		for (long columnKey : PENDING_VISIBLE_COLUMN_KEYS) {
			LodColumnSnapshot snapshot = PENDING_COLUMNS.get(columnKey);
			if (snapshot != null) ordered.add(snapshot);
		}
		for (LodColumnSnapshot snapshot : PENDING_COLUMNS.values()) {
			if (!PENDING_VISIBLE_COLUMN_KEYS.contains(snapshot.columnKey())) ordered.add(snapshot);
		}
		for (LodColumnSnapshot snapshot : ordered) {
			// The coordinator may be re-entered while native code owns the copied
			// payload. Do not let a small source-side rebuild replace that live
			// submission with another update for the same column. Once the first
			// update is acknowledged, the latest pending generation is selected.
			if (IN_FLIGHT_ASSET_GENERATIONS.containsKey(snapshot.columnKey())) {
				continue;
			}
			long snapshotBytes = snapshot.byteSize();
			boolean exceedsByteBudget = selectedBytes > 0L
				&& snapshotBytes > MAX_PENDING_ASSET_BYTES_PER_UPDATE - selectedBytes;
			if (selected.size() == MAX_PENDING_ASSET_COLUMNS_PER_UPDATE || exceedsByteBudget) {
				break;
			}
			selected.add(snapshot);
			selectedBytes = Math.addExact(selectedBytes, snapshotBytes);
		}
		for (LodColumnSnapshot snapshot : selected) {
			IN_FLIGHT_ASSET_GENERATIONS.put(snapshot.columnKey(), snapshot.generation());
		}
		tracePublicationLocked("select", selected);
		return selected;
	}

	private static void acknowledge(PendingAssetUpdate update) {
		synchronized (COLUMNS) {
			List<VulkanicGalBridge.WorldLodColumnAssetRecord> advancedAssets = new ArrayList<>();
			for (int assetIndex = 0; assetIndex < update.assets().size(); assetIndex++) {
				VulkanicGalBridge.WorldLodColumnAssetRecord asset = update.assets().get(assetIndex);
				LodColumnSnapshot snapshot = update.snapshots().get(assetIndex);
				if (Objects.equals(IN_FLIGHT_ASSET_GENERATIONS.get(asset.columnKey()), asset.columnGeneration())) {
					IN_FLIGHT_ASSET_GENERATIONS.remove(asset.columnKey());
				}
				boolean wasVisibleDemand = PENDING_VISIBLE_COLUMN_KEYS.contains(asset.columnKey());
				LodColumnSnapshot pending = PENDING_COLUMNS.get(asset.columnKey());
				if (pending != null && pending.generation() == asset.columnGeneration()) {
					PENDING_COLUMNS.remove(asset.columnKey());
				}
				Long publishedGeneration = PUBLISHED_GENERATIONS.get(asset.columnKey());
				if (publishedGeneration != null && publishedGeneration.longValue() != asset.columnGeneration()) {
					advancedAssets.add(asset);
				}
				LodColumnSnapshot current = COLUMNS.get(asset.columnKey());
				if (current == null) {
					// The column left the world while this copied asset was in flight.
					// Retire that exact Rust generation; never resurrect it locally.
					PUBLISHED_GENERATIONS.remove(asset.columnKey());
					PUBLISHED_COLUMNS.remove(asset.columnKey());
					PUBLISHED_MATERIAL_PROVENANCE.remove(asset.columnKey());
					PENDING_RETIREMENTS.put(asset.columnKey(), asset.columnGeneration());
				} else {
					PUBLISHED_GENERATIONS.put(asset.columnKey(), asset.columnGeneration());
					PUBLISHED_COLUMNS.put(asset.columnKey(), snapshot);
					LodMaterialProvenanceSnapshot publishedProvenance = update.materialProvenanceByColumn().get(asset.columnKey());
					if (publishedProvenance == null) {
						PUBLISHED_MATERIAL_PROVENANCE.remove(asset.columnKey());
					} else {
						PUBLISHED_MATERIAL_PROVENANCE.put(asset.columnKey(), publishedProvenance);
					}
				}
				if (current != null && current.generation() == asset.columnGeneration()) {
					PENDING_VISIBLE_COLUMN_KEYS.remove(asset.columnKey());
				}
				if (wasVisibleDemand) {
					tracePublicationLocked("ack key=" + asset.columnKey()
						+ " published=" + asset.columnGeneration()
						+ " current=" + (current == null ? "missing" : current.generation())
						+ " retained_visible_demand=" + PENDING_VISIBLE_COLUMN_KEYS.contains(asset.columnKey()));
				}
			}
			invalidateVisibleReferencesForAdvancedAssetsLocked(advancedAssets);
			for (VulkanicGalBridge.WorldLodColumnRetirementRecord retirement : update.retirements()) {
				Long pending = PENDING_RETIREMENTS.get(retirement.columnKey());
				if (pending != null && pending == retirement.columnGeneration()) {
					PENDING_RETIREMENTS.remove(retirement.columnKey());
				}
				if (Objects.equals(PUBLISHED_GENERATIONS.get(retirement.columnKey()), retirement.columnGeneration())) {
					PUBLISHED_GENERATIONS.remove(retirement.columnKey());
					PUBLISHED_COLUMNS.remove(retirement.columnKey());
					PUBLISHED_MATERIAL_PROVENANCE.remove(retirement.columnKey());
				}
			}
			NEXT_UPDATE_GENERATION.incrementAndGet();
		}
	}

	private static void releaseInFlightAssets(PendingAssetUpdate update) {
		synchronized (COLUMNS) {
			for (VulkanicGalBridge.WorldLodColumnAssetRecord asset : update.assets()) {
				if (Objects.equals(IN_FLIGHT_ASSET_GENERATIONS.get(asset.columnKey()), asset.columnGeneration())) {
					IN_FLIGHT_ASSET_GENERATIONS.remove(asset.columnKey());
				}
			}
		}
	}

	/**
	 * A visibility traversal may complete between Java producing an immutable
	 * column replacement and the coordinator publishing that replacement. Such
	 * references are no longer valid once Rust owns the newer generation. Drop
	 * only those stale references; the real DH traversal repopulates them on
	 * its next frame. This preserves the Rust generation invariant rather than
	 * allowing a stale instance to select an unrelated cached payload.
	 */
	private static void invalidateVisibleReferencesForAdvancedAssetsLocked(
		List<VulkanicGalBridge.WorldLodColumnAssetRecord> advancedAssets
	) {
		if (advancedAssets.isEmpty() || PENDING_VISIBLE_SEGMENTS.isEmpty()) {
			return;
		}
		int before = PENDING_VISIBLE_SEGMENTS.size();
		PENDING_VISIBLE_SEGMENTS.removeIf(instance -> advancedAssets.stream().anyMatch(asset ->
			asset.columnKey() == instance.columnKey()
				&& asset.columnGeneration() != instance.columnGeneration()
		));
		if (PENDING_VISIBLE_SEGMENTS.size() == before) {
			return;
		}
		routeOpaqueSegments = (int) PENDING_VISIBLE_SEGMENTS.stream().filter(instance -> instance.layer() == 1).count();
		routeTransparentSegments = (int) PENDING_VISIBLE_SEGMENTS.stream()
			.filter(instance -> instance.layer() == 2 || instance.layer() == 3)
			.count();
		routeWaterSegments = (int) PENDING_VISIBLE_SEGMENTS.stream().filter(instance -> instance.layer() == 4).count();
		if (PENDING_VISIBLE_SEGMENTS.isEmpty()) {
			PENDING_RENDER_FRAME = withFlags(
				PENDING_RENDER_FRAME,
				PENDING_RENDER_FRAME.flags() & ~RENDER_FLAG_RUST_NON_WATER_ROUTE_SELECTED
			);
			routeDecision = "rejected";
			routeReason = "asset-generation-advanced-before-submit";
			routeSelected = false;
		} else {
			routeDecision = "selected";
			routeReason = "stale-visible-references-pruned";
		}
	}

	private static void resetExactAtlasIdentityCoverage() {
		routeExactAtlasIdentitySegments = 0;
		routeExactAtlasIdentityQuads = 0;
		routeExactAtlasMixedQuads = 0;
		routeExactAtlasUnavailableQuads = 0;
		routeExactAtlasMissingProvenanceQuads = 0;
		routeExactAtlasMisalignedProvenanceQuads = 0;
		routeExactAtlasInvalidIdentityQuads = 0;
		routeExactAtlasIdentityTableEntries = 0;
		routeExactAtlasInputKnownQuads = 0;
		routeExactAtlasInputMixedQuads = 0;
		routeExactAtlasInputUnavailableQuads = 0;
		routeExactAtlasInputOpaqueKnownQuads = 0;
		routeExactAtlasInputOpaqueMixedQuads = 0;
		routeExactAtlasInputOpaqueUnavailableQuads = 0;
		routeExactAtlasOutputKnownQuads = 0;
		routeExactAtlasOutputMixedQuads = 0;
		routeExactAtlasOutputUnavailableQuads = 0;
		routeExactAtlasOutputOpaqueKnownQuads = 0;
		routeExactAtlasOutputOpaqueMixedQuads = 0;
		routeExactAtlasOutputOpaqueUnavailableQuads = 0;
		routeExactAtlasCoverageSamples.clear();
	}

	private static void recordExactAtlasResolution(
		String blockStateIdentity,
		DistantHorizonsFaceMaterialResolver.Resolution resolution
	) {
		DistantHorizonsFaceMaterialResolver.Status status = resolution.status();
		routeExactAtlasResolutionStatusCounts.merge(status, 1, Integer::sum);
		if (routeExactAtlasResolutionSamples.size() < 12) {
			String firstFace = resolution.faceLayers().isEmpty()
				? ""
				: ",atlas=" + resolution.faceLayers().values().iterator().next().getFirst().atlasIdentity()
					+ ",sprite=" + resolution.faceLayers().values().iterator().next().getFirst().spriteIdentity();
			routeExactAtlasResolutionSamples.add(
				"identity=" + blockStateIdentity + ",status=" + status + ",faces=" + resolution.faceLayers().size() + firstFace
			);
		}
	}

	private static String exactAtlasResolutionStatusSummary() {
		if (routeExactAtlasResolutionStatusCounts.isEmpty()) {
			return "none";
		}
		return routeExactAtlasResolutionStatusCounts.entrySet().stream()
			.map(entry -> entry.getKey() + "=" + entry.getValue())
			.collect(java.util.stream.Collectors.joining(","));
	}

	private static void removeColumnLocked(long columnKey) {
		LodColumnSnapshot removed = COLUMNS.remove(columnKey);
		removeMaterialProvenanceLocked(columnKey);
		if (removed != null) {
			retainedBytes -= removed.byteSize();
		}
		PENDING_COLUMNS.remove(columnKey);
		PUBLISHED_COLUMNS.remove(columnKey);
		PUBLISHED_MATERIAL_PROVENANCE.remove(columnKey);
		PENDING_VISIBLE_COLUMN_KEYS.remove(columnKey);
		IN_FLIGHT_ASSET_GENERATIONS.remove(columnKey);
		LAST_COLUMN_PAYLOAD_DIFFERENCES.remove(columnKey);
		Long publishedGeneration = PUBLISHED_GENERATIONS.get(columnKey);
		if (publishedGeneration != null) {
			PENDING_RETIREMENTS.put(columnKey, publishedGeneration);
		}
	}

	private static void tracePublicationLocked(String phase, List<LodColumnSnapshot> selected) {
		if (publicationTraceEvents >= MAX_PUBLICATION_TRACE_EVENTS
			|| PENDING_VISIBLE_COLUMN_KEYS.isEmpty()
			|| !Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		String keys = selected.stream()
			.limit(4)
			.map(snapshot -> snapshot.columnKey() + ":" + snapshot.generation())
			.collect(java.util.stream.Collectors.joining(","));
		tracePublicationLocked(phase + " pending=" + PENDING_COLUMNS.size()
			+ " visible_pending=" + PENDING_VISIBLE_COLUMN_KEYS.size()
			+ " selected=" + selected.size() + " keys=" + keys);
	}

	private static void tracePublicationLocked(String message) {
		if (publicationTraceEvents >= MAX_PUBLICATION_TRACE_EVENTS
			|| !Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		publicationTraceEvents++;
		System.out.println("[MattMC graphics audit] DH asset publication " + message);
	}

	private static void replaceMaterialProvenanceLocked(long columnKey, LodMaterialProvenanceSnapshot provenance) {
		LodMaterialProvenanceSnapshot previous = MATERIAL_PROVENANCE.put(columnKey, provenance);
		if (previous != null) {
			retainedMaterialProvenanceBytes -= previous.byteSize();
		}
		retainedMaterialProvenanceBytes += provenance.byteSize();
	}

	private static void removeMaterialProvenanceLocked(long columnKey) {
		LodMaterialProvenanceSnapshot removed = MATERIAL_PROVENANCE.remove(columnKey);
		if (removed != null) {
			retainedMaterialProvenanceBytes -= removed.byteSize();
		}
	}

	/**
	 * Keep copied CPU geometry bounded without discarding the sole oversized
	 * column before DH's real quadtree has a frame to select it. Legacy DH VBOs
	 * use fixed-size buffers, but the semantic representation can combine many
	 * valid transport segments for one column. Once another column arrives the
	 * ordinary LRU and byte limits resume immediately.
	 */
	private static void trimRetainedColumnsLocked(int maximumColumns, long maximumBytes) {
		if (maximumColumns <= 0 || maximumBytes <= 0L) {
			throw new IllegalArgumentException("Distant Horizons semantic retention bounds must be positive");
		}
		while (
			(COLUMNS.size() > maximumColumns || retainedBytes + retainedMaterialProvenanceBytes > maximumBytes)
			&& COLUMNS.size() > 1
		) {
			Map.Entry<Long, LodColumnSnapshot> eldest = COLUMNS.entrySet().iterator().next();
			removeColumnLocked(eldest.getKey());
		}
	}

	private static int oversizedColumnCountLocked() {
		int oversized = 0;
		for (LodColumnSnapshot snapshot : COLUMNS.values()) {
			if (snapshot.byteSize() > MAX_RETAINED_BYTES) {
				oversized++;
			}
		}
		return oversized;
	}

	static LodColumnSnapshot snapshotForTest(long columnKey) {
		synchronized (COLUMNS) {
			return COLUMNS.get(columnKey);
		}
	}

	static LodMaterialProvenanceSnapshot materialProvenanceForTest(long columnKey) {
		synchronized (COLUMNS) {
			return MATERIAL_PROVENANCE.get(columnKey);
		}
	}

	static PendingAssetUpdate pendingUpdateForTest() {
		return pendingUpdate();
	}

	static void acknowledgeForTest(PendingAssetUpdate update) {
		acknowledge(update);
	}

	static List<VulkanicGalBridge.WorldLodColumnInstanceRecord> executedVisibleSegmentsForTest(long worldFrame) {
		synchronized (COLUMNS) {
			return executedSegmentsForWorldFrameLocked(worldFrame);
		}
	}

	static void resetForTest() {
		synchronized (COLUMNS) {
			COLUMNS.clear();
			PUBLISHED_COLUMNS.clear();
			MATERIAL_PROVENANCE.clear();
			PUBLISHED_MATERIAL_PROVENANCE.clear();
			PENDING_COLUMNS.clear();
			PENDING_RETIREMENTS.clear();
			PUBLISHED_GENERATIONS.clear();
			LAST_COLUMN_PAYLOAD_DIFFERENCES.clear();
			PENDING_VISIBLE_SEGMENTS.clear();
			PENDING_VISIBLE_COLUMN_KEYS.clear();
			publicationTraceEvents = 0;
			IN_FLIGHT_ASSET_GENERATIONS.clear();
			LAST_CONSUMED_VISIBLE_SEGMENTS = List.of();
			LAST_CONSUMED_VISIBLE_SEGMENT_SNAPSHOTS = List.of();
			EXECUTED_VISIBLE_SEGMENTS_BY_WORLD_FRAME.clear();
			PENDING_RENDER_FRAME = VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
			retainedBytes = 0L;
			retainedMaterialProvenanceBytes = 0L;
			NEXT_GENERATION.set(1L);
			NEXT_UPDATE_GENERATION.set(1L);
			nextVisibleOrder = 0;
			routeFrame = 0L;
			routeDecision = "not-attempted";
			routeReason = "not-requested";
			routeMatrixStatus = "not-observed";
			routeMatrixDetail = "not-observed";
			routeOpaqueSegments = 0;
			resetExactAtlasIdentityCoverage();
			routeTransparentSegments = 0;
			routeWaterSegments = 0;
			routeVisibleColumns = 0;
			routeUnpublishedVisibleColumns = 0;
			routeCachedColumns = 0;
			semanticBuildAttempts = 0L;
			semanticColumnsBuilt = 0L;
			semanticColumnsReused = 0L;
			semanticColumnsReplaced = 0L;
			lastPayloadDifference = "none";
			routeSelected = false;
			lastExecutedRouteFrame = 0L;
			lastExecutedWorldFrame = 0L;
			lastExecutedSubmission = 0L;
			lastExecutedCaptureFrame = 0L;
			lastExecutedInstances = 0;
			lastExecutedOpaqueInstances = 0;
			lastExecutedTransparentInstances = 0;
			lastExecutedWaterInstances = 0;
			lastExecutedFrameSemanticsEnabled = false;
			waterSourceInputProbes = List.of();
			WATER_SOURCE_INPUT_TRACES.clear();
		}
	}

	static void trimRetainedColumnsForTest(int maximumColumns, long maximumBytes) {
		synchronized (COLUMNS) {
			trimRetainedColumnsLocked(maximumColumns, maximumBytes);
		}
	}

	/** Capture-only cache/publish/consumption state for one world position. */
	public record ColumnCoverageDiagnostics(
		int cachedColumns,
		int publishedColumns,
		int consumedOpaqueSegments,
		List<String> samples
	) {
	}

	/** Capture-only observation at the converted DH render-data boundary. */
	public record WaterSourceInputTrace(
		int blockX,
		int blockY,
		int blockZ,
		long sectionKey,
		byte detailLevel,
		int sourceMinX,
		int sourceMinZ,
		int sourceWidth,
		int minY,
		int maxY,
		int dhMaterialId,
		int semanticMaterialId
	) {
	}

	public record WaterSourceInputReceipt(
		boolean matched,
		String status,
		List<WaterSourceInputTrace> traces
	) {
	}

	public record RouteDiagnostics(
		long frame,
		String decision,
		String reason,
		String matrixStatus,
		String matrixDetail,
		int opaqueSegments,
		int exactAtlasIdentitySegments,
		int exactAtlasIdentityQuads,
		int exactAtlasMixedQuads,
		int exactAtlasUnavailableQuads,
		int exactAtlasMissingProvenanceQuads,
		int exactAtlasMisalignedProvenanceQuads,
		int exactAtlasInvalidIdentityQuads,
		int exactAtlasIdentityTableEntries,
		int exactAtlasInputKnownQuads,
		int exactAtlasInputMixedQuads,
		int exactAtlasInputUnavailableQuads,
		int exactAtlasInputOpaqueKnownQuads,
		int exactAtlasInputOpaqueMixedQuads,
		int exactAtlasInputOpaqueUnavailableQuads,
		int exactAtlasOutputKnownQuads,
		int exactAtlasOutputMixedQuads,
		int exactAtlasOutputUnavailableQuads,
		int exactAtlasOutputOpaqueKnownQuads,
		int exactAtlasOutputOpaqueMixedQuads,
		int exactAtlasOutputOpaqueUnavailableQuads,
		int transparentSegments,
		int waterSegments,
		int visibleColumns,
		int cachedColumns,
		int unpublishedVisibleColumns,
		long semanticBuildAttempts,
		long semanticColumnsBuilt,
		long semanticColumnsReused,
		long semanticColumnsReplaced,
		String lastPayloadDifference,
		List<String> exactAtlasCoverageSamples,
		String exactAtlasResolutionStatusSummary,
		List<String> exactAtlasResolutionSamples,
		long retainedBytes,
		int oversizedColumns,
		boolean frameSemanticsEnabled,
		boolean selected,
		long lastExecutedRouteFrame,
		long lastExecutedWorldFrame,
		long lastExecutedSubmission,
		long lastExecutedCaptureFrame,
		int lastExecutedInstances,
		int lastExecutedOpaqueInstances,
		int lastExecutedTransparentInstances,
		int lastExecutedWaterInstances,
		boolean lastExecutedFrameSemanticsEnabled
	) {
	}

	/**
	 * Counts only copied semantic identity coverage. It does not inspect a
	 * Minecraft model or predict backend execution; exact-atlas planning still
	 * rejects a segment unless Rust receives a complete face-material table.
	 */
	private static ExactAtlasIdentityCoverage exactAtlasIdentityCoverage(LodColumnSnapshot column) {
		LodMaterialProvenanceSnapshot provenance = publishedMaterialProvenanceLocked(column.columnKey());
		if (provenance == null) {
			int quads = opaqueQuadCount(column);
			return new ExactAtlasIdentityCoverage(
				0, 0, 0, quads, quads, 0, 0, 0,
				0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, quads
			);
		}
		int completeSegments = 0;
		int completeQuads = 0;
		int mixedQuads = 0;
		int unavailableQuads = 0;
		int misalignedProvenanceQuads = 0;
		int invalidIdentityQuads = 0;
		int[] sourceQuadOffsets = new int[provenance.opaque().size()];
		for (LodBufferSnapshot buffer : column.opaque()) {
			if (buffer.vertices().isEmpty()) continue;
			int sourceIndex = buffer.sourceBufferIndex();
			int quadCount = buffer.vertices().size() / 4;
			if (sourceIndex < 0 || sourceIndex >= provenance.opaque().size()) {
				unavailableQuads += quadCount;
				misalignedProvenanceQuads += quadCount;
				continue;
			}
			int[] sourceIds = provenance.opaque().get(sourceIndex);
			int quadOffset = sourceQuadOffsets[sourceIndex];
			if (quadOffset > sourceIds.length || quadCount > sourceIds.length - quadOffset) {
				unavailableQuads += quadCount;
				misalignedProvenanceQuads += quadCount;
				continue;
			}
			boolean complete = true;
			for (int quad = quadOffset; quad < quadOffset + quadCount; quad++) {
				int materialId = sourceIds[quad];
				if (materialId == ColumnRenderSource.SEMANTIC_MATERIAL_MIXED) {
					mixedQuads++;
					complete = false;
				} else if (materialId <= ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE) {
					unavailableQuads++;
					invalidIdentityQuads++;
					complete = false;
				}
			}
			sourceQuadOffsets[sourceIndex] += quadCount;
			if (complete) {
				completeSegments++;
				completeQuads += quadCount;
			}
		}
		return new ExactAtlasIdentityCoverage(
			completeSegments, completeQuads, mixedQuads, unavailableQuads,
			0, misalignedProvenanceQuads, invalidIdentityQuads, provenance.semanticMaterials().size(),
			provenance.inputCoverage().known(), provenance.inputCoverage().mixed(), provenance.inputCoverage().unavailable(),
			provenance.inputCoverage().opaqueKnown(), provenance.inputCoverage().opaqueMixed(), provenance.inputCoverage().opaqueUnavailable(),
			provenance.outputCoverage().known(), provenance.outputCoverage().mixed(), provenance.outputCoverage().unavailable(),
			provenance.outputCoverage().opaqueKnown(), provenance.outputCoverage().opaqueMixed(), provenance.outputCoverage().opaqueUnavailable(),
			opaqueQuadCount(column)
		);
	}

	private static int opaqueQuadCount(LodColumnSnapshot column) {
		int quads = 0;
		for (LodBufferSnapshot buffer : column.opaque()) {
			quads += buffer.vertices().size() / 4;
		}
		return quads;
	}

	private record ExactAtlasIdentityCoverage(
		int completeSegments,
		int completeQuads,
		int mixedQuads,
		int unavailableQuads,
		int missingProvenanceQuads,
		int misalignedProvenanceQuads,
		int invalidIdentityQuads,
		int identityTableEntries,
		int inputKnownQuads,
		int inputMixedQuads,
		int inputUnavailableQuads,
		int inputOpaqueKnownQuads,
		int inputOpaqueMixedQuads,
		int inputOpaqueUnavailableQuads,
		int outputKnownQuads,
		int outputMixedQuads,
		int outputUnavailableQuads,
		int outputOpaqueKnownQuads,
		int outputOpaqueMixedQuads,
		int outputOpaqueUnavailableQuads,
		int copiedOpaqueQuads
	) {
	}

	public record VisibleColumnSegments(int opaqueSegments, int transparentSegments, int waterSegments) {
		private static final VisibleColumnSegments EMPTY = new VisibleColumnSegments(0, 0, 0);
	}

	/** A bounded semantic expectation for one deterministic DH palette cell. */
	public record DistantHorizonsTextureProbe(
		int blockX,
		int blockY,
		int blockZ,
		String blockId,
		List<String> allowedSprites,
		List<String> requiredSprites
	) {
		public DistantHorizonsTextureProbe {
			if (blockId == null || blockId.isBlank()) {
				throw new IllegalArgumentException("DH texture probe block ID must be non-blank");
			}
			allowedSprites = List.copyOf(allowedSprites == null ? List.of() : allowedSprites);
			requiredSprites = List.copyOf(requiredSprites == null ? List.of() : requiredSprites);
			if (!allowedSprites.containsAll(requiredSprites)) {
				throw new IllegalArgumentException("DH texture probe required sprites must be allowed");
			}
		}
	}

	public record DistantHorizonsTextureProbeReceipt(
		boolean matched,
		String status,
		long executedWorldFrame,
		List<DistantHorizonsTextureProbeResult> probes
	) {
		public DistantHorizonsTextureProbeReceipt {
			status = status == null ? "" : status;
			if (executedWorldFrame < 0L) {
				throw new IllegalArgumentException("DH texture probe execution frame must be non-negative");
			}
			probes = List.copyOf(probes == null ? List.of() : probes);
		}
	}

	public record DistantHorizonsTextureProbeResult(
		int blockX,
		int blockY,
		int blockZ,
		String expectedBlockId,
		String resolvedBlockStateIdentity,
		boolean matched,
		String status,
		List<String> resolvedSprites,
		String evidence
	) {
		public DistantHorizonsTextureProbeResult {
			expectedBlockId = expectedBlockId == null ? "" : expectedBlockId;
			resolvedBlockStateIdentity = resolvedBlockStateIdentity == null ? "" : resolvedBlockStateIdentity;
			status = status == null ? "" : status;
			resolvedSprites = List.copyOf(resolvedSprites == null ? List.of() : resolvedSprites);
			evidence = evidence == null ? "" : evidence;
		}
	}

	public record DistantHorizonsWaterProbeReceipt(
		boolean matched,
		String status,
		long executedWorldFrame,
		List<DistantHorizonsWaterProbeResult> probes
	) {
		public DistantHorizonsWaterProbeReceipt {
			status = status == null ? "" : status;
			if (executedWorldFrame < 0L) {
				throw new IllegalArgumentException("DH water probe execution frame must be non-negative");
			}
			probes = List.copyOf(probes == null ? List.of() : probes);
		}
	}

	public record DistantHorizonsWaterProbeResult(
		int blockX,
		int blockY,
		int blockZ,
		boolean matched,
		String status,
		long columnKey,
		long columnGeneration,
		int segmentIndex,
		int quadIndex,
		int originX,
		int originY,
		int originZ,
		String materialIdentity
	) {
		public DistantHorizonsWaterProbeResult {
			status = status == null ? "" : status;
			materialIdentity = materialIdentity == null ? "" : materialIdentity;
		}
	}

	private static List<LodBufferSnapshot> copyBuffers(List<ByteBuffer> buffers) {
		return copyBuffers(buffers, MAX_TRANSPORT_VERTICES_PER_SEGMENT);
	}

	static List<LodBufferSnapshot> copyBuffersForTest(List<ByteBuffer> buffers, int maximumVerticesPerSegment) {
		return copyBuffers(buffers, maximumVerticesPerSegment);
	}

	private static List<LodBufferSnapshot> copyBuffers(
		List<ByteBuffer> buffers,
		int maximumVerticesPerSegment
	) {
		Objects.requireNonNull(buffers, "buffers");
		if (maximumVerticesPerSegment <= 0 || maximumVerticesPerSegment % 4 != 0) {
			throw new IllegalArgumentException("Distant Horizons transport segment limit must be positive and quad aligned");
		}
		List<LodBufferSnapshot> copies = new ArrayList<>(buffers.size());
		for (int sourceBufferIndex = 0; sourceBufferIndex < buffers.size(); sourceBufferIndex++) {
			ByteBuffer buffer = buffers.get(sourceBufferIndex);
			Objects.requireNonNull(buffer, "buffer");
			ByteBuffer source = buffer.duplicate().order(ByteOrder.nativeOrder());
			if (source.remaining() % VERTEX_STRIDE_BYTES != 0) {
				throw new IllegalArgumentException(
					"Distant Horizons semantic buffer length " + source.remaining()
						+ " is not aligned to " + VERTEX_STRIDE_BYTES + " bytes"
				);
			}
			int vertexCount = source.remaining() / VERTEX_STRIDE_BYTES;
			if (vertexCount % 4 != 0) {
				throw new IllegalArgumentException(
					"Distant Horizons semantic buffer has " + vertexCount
						+ " vertices, which is not quad aligned"
				);
			}
				if (vertexCount == 0) {
					// DH retains empty legacy CPU buffers while columns are being built.
					// They carry no semantic geometry and must not become zero-byte Rust
					// buffers, which GAL correctly rejects as non-drawable ranges.
					continue;
				}
			for (int copiedVertices = 0; copiedVertices < vertexCount; ) {
				int chunkVertexCount = Math.min(maximumVerticesPerSegment, vertexCount - copiedVertices);
				List<LodVertex> vertices = new ArrayList<>(chunkVertexCount);
				for (int chunkVertexIndex = 0; chunkVertexIndex < chunkVertexCount; chunkVertexIndex++) {
					int vertexIndex = copiedVertices + chunkVertexIndex;
				int localX = Short.toUnsignedInt(source.getShort());
				int localY = Short.toUnsignedInt(source.getShort());
				int localZ = Short.toUnsignedInt(source.getShort());
				int packedLightAndMicroOffset = Short.toUnsignedInt(source.getShort());
				int red = Byte.toUnsignedInt(source.get());
				int green = Byte.toUnsignedInt(source.get());
				int blue = Byte.toUnsignedInt(source.get());
				int alpha = Byte.toUnsignedInt(source.get());
				int materialId = Byte.toUnsignedInt(source.get());
				int normalIndex = Byte.toUnsignedInt(source.get());
				int padding = Short.toUnsignedInt(source.getShort());
				if (padding != 0) {
					throw new IllegalArgumentException(
						"Distant Horizons semantic vertex " + vertexIndex
							+ " has non-zero reserved padding"
					);
				}
				vertices.add(new LodVertex(
					localX,
					localY,
					localZ,
					packedLightAndMicroOffset,
					red,
					green,
					blue,
					alpha,
					materialId,
					normalIndex,
					padding
				));
				}
				copies.add(new LodBufferSnapshot(sourceBufferIndex, vertices));
				copiedVertices += chunkVertexCount;
			}
		}
		return List.copyOf(copies);
	}

	public record LodColumnSnapshot(
		long columnKey,
		long generation,
		int originX,
		int originY,
		int originZ,
		List<LodBufferSnapshot> opaque,
		List<LodBufferSnapshot> transparentSide,
		List<LodBufferSnapshot> transparentUp,
		List<LodBufferSnapshot> transparentWaterUp
	) {
		boolean hasSamePayload(LodColumnSnapshot other) {
			return this.columnKey == other.columnKey
				&& this.originX == other.originX
				&& this.originY == other.originY
				&& this.originZ == other.originZ
				&& this.opaque.equals(other.opaque)
				&& this.transparentSide.equals(other.transparentSide)
				&& this.transparentUp.equals(other.transparentUp)
				&& this.transparentWaterUp.equals(other.transparentWaterUp);
		}

		String payloadDifference(LodColumnSnapshot other) {
			if (this.originX != other.originX || this.originY != other.originY || this.originZ != other.originZ) {
				return "origin";
			}
			String opaqueDifference = bufferDifference(this.opaque, other.opaque, "opaque");
			if (opaqueDifference != null) return opaqueDifference;
			String sideDifference = bufferDifference(this.transparentSide, other.transparentSide, "transparent-side");
			if (sideDifference != null) return sideDifference;
			String upDifference = bufferDifference(this.transparentUp, other.transparentUp, "transparent-up");
			if (upDifference != null) return upDifference;
			String waterDifference = bufferDifference(this.transparentWaterUp, other.transparentWaterUp, "transparent-water-up");
			return waterDifference == null ? "unknown" : waterDifference;
		}

		private static String bufferDifference(List<LodBufferSnapshot> left, List<LodBufferSnapshot> right, String name) {
			if (left.size() != right.size()) return name + "-segment-count";
			for (int index = 0; index < left.size(); index++) {
				LodBufferSnapshot leftBuffer = left.get(index);
				LodBufferSnapshot rightBuffer = right.get(index);
				if (leftBuffer.sourceBufferIndex() != rightBuffer.sourceBufferIndex()) return name + "-source-index";
				if (leftBuffer.vertices().size() != rightBuffer.vertices().size()) return name + "-vertex-count";
				for (int vertexIndex = 0; vertexIndex < leftBuffer.vertices().size(); vertexIndex++) {
					String vertexDifference = vertexDifference(
						leftBuffer.vertices().get(vertexIndex),
						rightBuffer.vertices().get(vertexIndex)
					);
					if (vertexDifference != null) {
						return name + "[" + index + "].vertex[" + vertexIndex + "]." + vertexDifference;
					}
				}
			}
			return null;
		}

		private static String vertexDifference(LodVertex left, LodVertex right) {
			if (left.localX() != right.localX()) return valueDifference("local-x", left.localX(), right.localX());
			if (left.localY() != right.localY()) return valueDifference("local-y", left.localY(), right.localY());
			if (left.localZ() != right.localZ()) return valueDifference("local-z", left.localZ(), right.localZ());
			if (left.packedLightAndMicroOffset() != right.packedLightAndMicroOffset()) {
				return valueDifference("packed-light-micro", left.packedLightAndMicroOffset(), right.packedLightAndMicroOffset());
			}
			if (left.red() != right.red()) return valueDifference("red", left.red(), right.red());
			if (left.green() != right.green()) return valueDifference("green", left.green(), right.green());
			if (left.blue() != right.blue()) return valueDifference("blue", left.blue(), right.blue());
			if (left.alpha() != right.alpha()) return valueDifference("alpha", left.alpha(), right.alpha());
			if (left.materialId() != right.materialId()) return valueDifference("material-id", left.materialId(), right.materialId());
			if (left.normalIndex() != right.normalIndex()) return valueDifference("normal-index", left.normalIndex(), right.normalIndex());
			if (left.padding() != right.padding()) return valueDifference("padding", left.padding(), right.padding());
			return null;
		}

		private static String valueDifference(String field, int left, int right) {
			return field + "=" + left + "->" + right;
		}
		public LodColumnSnapshot {
			if (generation <= 0L) {
				throw new IllegalArgumentException("generation must be positive");
			}
			opaque = immutableBuffers(opaque);
			transparentSide = immutableBuffers(transparentSide);
			transparentUp = immutableBuffers(transparentUp);
			transparentWaterUp = immutableBuffers(transparentWaterUp);
		}

		private static List<LodBufferSnapshot> immutableBuffers(List<LodBufferSnapshot> buffers) {
			Objects.requireNonNull(buffers, "buffers");
			for (LodBufferSnapshot buffer : buffers) {
				Objects.requireNonNull(buffer, "buffer");
			}
			return List.copyOf(buffers);
		}

		long byteSize() {
			long vertices = 0L;
			for (LodBufferSnapshot buffer : opaque) {
				vertices += buffer.vertices().size();
			}
			for (LodBufferSnapshot buffer : transparentSide) {
				vertices += buffer.vertices().size();
			}
			for (LodBufferSnapshot buffer : transparentUp) {
				vertices += buffer.vertices().size();
			}
			for (LodBufferSnapshot buffer : transparentWaterUp) {
				vertices += buffer.vertices().size();
			}
			return Math.multiplyExact(vertices, VERTEX_STRIDE_BYTES);
		}

		boolean hasSegments() {
			return !opaque.isEmpty() || !transparentSide.isEmpty() || !transparentUp.isEmpty() || !transparentWaterUp.isEmpty();
		}

		VulkanicGalBridge.WorldLodColumnAssetRecord toBridgeRecord() {
			List<VulkanicGalBridge.WorldLodSegmentRecord> segments = new ArrayList<>();
			appendSegments(segments, 1, opaque);
			appendSegments(segments, 2, transparentSide);
			appendSegments(segments, 3, transparentUp);
			appendSegments(segments, 4, transparentWaterUp);
			return new VulkanicGalBridge.WorldLodColumnAssetRecord(
				columnKey,
				generation,
				VERTEX_LAYOUT_VERSION,
				originX,
				originY,
				originZ,
				segments
			);
		}

		VulkanicGalBridge.WorldLodColumnMaterialProvenanceRecord toBridgeMaterialProvenance(
			LodMaterialProvenanceSnapshot provenance
		) {
			Objects.requireNonNull(provenance, "provenance");
			List<VulkanicGalBridge.WorldLodMaterialIdentityRecord> identities = provenance.semanticMaterials().stream()
				.map(identity -> new VulkanicGalBridge.WorldLodMaterialIdentityRecord(
					identity.blockStateIdentity(), identity.biomeIdentity()
				))
				.toList();
			boolean[] variantDependent = new boolean[identities.size() + 1];
			boolean[] positionTinted = new boolean[identities.size() + 1];
			List<VulkanicGalBridge.WorldLodFaceMaterialRecord> faceMaterials = new ArrayList<>();
			for (int identityIndex = 0; identityIndex < provenance.semanticMaterials().size(); identityIndex++) {
				ColumnRenderSource.SemanticMaterialIdentity identity = provenance.semanticMaterials().get(identityIndex);
				DistantHorizonsFaceMaterialResolver.Resolution resolution =
					DistantHorizonsFaceMaterialResolver.resolveCurrentClientState(identity.blockStateIdentity());
				recordExactAtlasResolution(identity.blockStateIdentity(), resolution);
				if (resolution.status() == DistantHorizonsFaceMaterialResolver.Status.VARIANT_DEPENDENT) {
					variantDependent[identityIndex + 1] = true;
				} else {
					appendFaceMaterials(faceMaterials, identityIndex + 1, 0L, resolution);
					positionTinted[identityIndex + 1] = resolution.faceLayers().values().stream()
						.flatMap(List::stream)
						.anyMatch(DistantHorizonsFaceMaterialResolver.FaceMaterial::tinted);
				}
			}
			appendVariantFaceMaterials(faceMaterials, provenance, variantDependent, positionTinted);
			List<VulkanicGalBridge.WorldLodSegmentMaterialProvenanceRecord> segments = new ArrayList<>();
			appendMaterialProvenanceSegments(segments, 1, 0, opaque, provenance.opaque(),
				provenance.opaqueVariantStates(), provenance.opaqueVariantPositions(), variantDependent, positionTinted);
			appendMaterialProvenanceSegments(segments, 2, nonEmptyCount(opaque), transparentSide, provenance.transparentSide(),
				provenance.transparentSideVariantStates(), provenance.transparentSideVariantPositions(), variantDependent, positionTinted);
			appendMaterialProvenanceSegments(
				segments,
				3,
				nonEmptyCount(opaque) + nonEmptyCount(transparentSide),
				transparentUp,
				provenance.transparentUp(), provenance.transparentUpVariantStates(), provenance.transparentUpVariantPositions(), variantDependent, positionTinted
			);
			appendMaterialProvenanceSegments(
				segments,
				4,
				nonEmptyCount(opaque) + nonEmptyCount(transparentSide) + nonEmptyCount(transparentUp),
				transparentWaterUp,
				provenance.transparentWaterUp(), provenance.transparentWaterUpVariantStates(), provenance.transparentWaterUpVariantPositions(), variantDependent, positionTinted
			);
			return new VulkanicGalBridge.WorldLodColumnMaterialProvenanceRecord(
				columnKey, generation, identities, segments, faceMaterials
			);
		}

		List<Integer> compactSegmentIndexes(int layer, int sourceSegmentIndex) {
			if (sourceSegmentIndex < 0) {
				return List.of();
			}
			List<LodBufferSnapshot> buffers = switch (layer) {
				case 1 -> opaque;
				case 2 -> transparentSide;
				case 3 -> transparentUp;
				case 4 -> transparentWaterUp;
				default -> List.of();
			};
			List<Integer> result = new ArrayList<>();
			int globalSegmentOffset = switch (layer) {
				case 1 -> 0;
				case 2 -> nonEmptyCount(opaque);
				case 3 -> nonEmptyCount(opaque) + nonEmptyCount(transparentSide);
				case 4 -> nonEmptyCount(opaque) + nonEmptyCount(transparentSide) + nonEmptyCount(transparentUp);
				default -> 0;
			};
			int compactIndex = 0;
			for (LodBufferSnapshot buffer : buffers) {
				if (buffer.vertices().isEmpty()) {
					continue;
				}
				if (buffer.sourceBufferIndex() == sourceSegmentIndex) {
					result.add(globalSegmentOffset + compactIndex);
				}
				compactIndex++;
			}
			return List.copyOf(result);
		}

		private static int nonEmptyCount(List<LodBufferSnapshot> buffers) {
			int count = 0;
			for (LodBufferSnapshot buffer : buffers) {
				if (!buffer.vertices().isEmpty()) count++;
			}
			return count;
		}

		private static void appendSegments(
			List<VulkanicGalBridge.WorldLodSegmentRecord> target,
			int layer,
			List<LodBufferSnapshot> buffers
		) {
			for (LodBufferSnapshot buffer : buffers) {
				if (buffer.vertices().isEmpty()) {
					continue;
				}
				List<VulkanicGalBridge.WorldLodVertexRecord> vertices = buffer.vertices().stream()
					.map(vertex -> new VulkanicGalBridge.WorldLodVertexRecord(
						vertex.localX(), vertex.localY(), vertex.localZ(), vertex.packedLightAndMicroOffset(),
						(vertex.red() & 0xFF) | ((vertex.green() & 0xFF) << 8)
							| ((vertex.blue() & 0xFF) << 16) | ((vertex.alpha() & 0xFF) << 24),
						vertex.materialId(), vertex.normalIndex()
					))
					.toList();
				target.add(new VulkanicGalBridge.WorldLodSegmentRecord(layer, vertices));
			}
		}

		private static void appendMaterialProvenanceSegments(
			List<VulkanicGalBridge.WorldLodSegmentMaterialProvenanceRecord> target,
			int layer,
			int segmentOffset,
			List<LodBufferSnapshot> buffers,
			List<int[]> materialIds,
			List<byte[]> variantStates,
			List<long[]> variantPositions,
			boolean[] variantDependent,
			boolean[] positionTinted
		) {
			// `copyBuffers` may split one large DH CPU buffer into several bounded
			// transport segments. The provenance sidecar remains intentionally one
			// entry per original buffer, so align it through sourceBufferIndex and
			// advance a quad cursor instead of requiring the two list lengths to
			// match. Treat malformed coverage as a semantic rejection, never a
			// renderer crash or a guessed texture assignment.
			if (materialIds.size() != variantStates.size() || materialIds.size() != variantPositions.size()) {
				throw new IllegalArgumentException("Distant Horizons material variant provenance buffers do not align");
			}
			int[] copiedQuadOffsets = new int[materialIds.size()];
			int compactIndex = 0;
			for (LodBufferSnapshot buffer : buffers) {
				if (buffer.vertices().isEmpty()) {
					continue;
				}
				int sourceIndex = buffer.sourceBufferIndex();
				if (sourceIndex >= materialIds.size()) {
					throw new IllegalArgumentException("Distant Horizons material provenance is missing a copied source buffer");
				}
				int[] ids = materialIds.get(sourceIndex);
				byte[] states = variantStates.get(sourceIndex);
				long[] positions = variantPositions.get(sourceIndex);
				if (states.length != ids.length || positions.length != ids.length) {
					throw new IllegalArgumentException("Distant Horizons material variant provenance does not align one-for-one with IDs");
				}
				int quadCount = buffer.vertices().size() / 4;
				int quadOffset = copiedQuadOffsets[sourceIndex];
				if (quadOffset > ids.length || quadCount > ids.length - quadOffset) {
					throw new IllegalArgumentException("Distant Horizons material provenance count does not cover compact segment quads");
				}
				int[] compactIds = Arrays.copyOfRange(ids, quadOffset, quadOffset + quadCount);
				byte[] compactStates = Arrays.copyOfRange(states, quadOffset, quadOffset + quadCount);
				long[] compactPositions = Arrays.copyOfRange(positions, quadOffset, quadOffset + quadCount);
				for (int quad = 0; quad < compactIds.length; quad++) {
					int materialId = compactIds[quad];
					if (materialId <= ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE
						|| materialId >= variantDependent.length) {
						compactStates[quad] = ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE;
						compactPositions[quad] = 0L;
					} else if (!variantDependent[materialId] && !positionTinted[materialId]) {
						compactStates[quad] = ColumnRenderSource.SEMANTIC_VARIANT_EXACT;
						compactPositions[quad] = 0L;
					}
				}
				copiedQuadOffsets[sourceIndex] = quadOffset + quadCount;
				target.add(new VulkanicGalBridge.WorldLodSegmentMaterialProvenanceRecord(
					layer, segmentOffset + compactIndex, compactIds, compactStates, compactPositions
				));
				compactIndex++;
			}
			for (int sourceIndex = 0; sourceIndex < materialIds.size(); sourceIndex++) {
				if (copiedQuadOffsets[sourceIndex] != materialIds.get(sourceIndex).length) {
					throw new IllegalArgumentException("Distant Horizons material provenance contains uncovered source quads");
				}
			}
		}

		private static void appendFaceMaterials(
			List<VulkanicGalBridge.WorldLodFaceMaterialRecord> target,
			int materialId, long variantPosition, DistantHorizonsFaceMaterialResolver.Resolution resolution
		) {
			if (!resolution.hasResolvedFaces()) return;
			for (Map.Entry<net.minecraft.core.Direction, List<DistantHorizonsFaceMaterialResolver.FaceMaterial>> entry : resolution.faceLayers().entrySet()) {
				for (DistantHorizonsFaceMaterialResolver.FaceMaterial material : entry.getValue()) {
					target.add(new VulkanicGalBridge.WorldLodFaceMaterialRecord(
						materialId, DistantHorizonsFaceMaterialResolver.faceId(entry.getKey()), material.layer(),
						material.atlasIdentity(), material.spriteIdentity(),
						material.u0(), material.v0(), material.u1(), material.v1(), material.uvCornerOrder(), variantPosition,
						material.tinted(), material.tintArgb()
					));
				}
			}
		}

		private static void appendVariantFaceMaterials(
			List<VulkanicGalBridge.WorldLodFaceMaterialRecord> target,
			LodMaterialProvenanceSnapshot provenance, boolean[] variantDependent, boolean[] positionTinted
		) {
			Set<MaterialVariantKey> seen = new LinkedHashSet<>();
			collectVariantKeys(seen, provenance.opaque(), provenance.opaqueVariantStates(), provenance.opaqueVariantPositions(), variantDependent, positionTinted);
			collectVariantKeys(seen, provenance.transparentSide(), provenance.transparentSideVariantStates(), provenance.transparentSideVariantPositions(), variantDependent, positionTinted);
			collectVariantKeys(seen, provenance.transparentUp(), provenance.transparentUpVariantStates(), provenance.transparentUpVariantPositions(), variantDependent, positionTinted);
			collectVariantKeys(seen, provenance.transparentWaterUp(), provenance.transparentWaterUpVariantStates(), provenance.transparentWaterUpVariantPositions(), variantDependent, positionTinted);
			for (MaterialVariantKey key : seen) {
				ColumnRenderSource.SemanticMaterialIdentity identity = provenance.semanticMaterials().get(key.materialId() - 1);
				DistantHorizonsFaceMaterialResolver.Resolution resolution =
					DistantHorizonsFaceMaterialResolver.resolveCurrentClientState(identity.blockStateIdentity(), key.variantPosition());
				recordExactAtlasResolution(identity.blockStateIdentity(), resolution);
				appendFaceMaterials(target, key.materialId(), key.variantPosition(), resolution);
			}
		}

		private static void collectVariantKeys(
			Set<MaterialVariantKey> target, List<int[]> ids, List<byte[]> states, List<long[]> positions,
			boolean[] variantDependent, boolean[] positionTinted
		) {
			for (int source = 0; source < ids.size(); source++) {
				int[] materialIds = ids.get(source);
				byte[] sourceStates = states.get(source);
				long[] sourcePositions = positions.get(source);
				for (int index = 0; index < materialIds.length; index++) {
					int materialId = materialIds[index];
					if (materialId > 0 && materialId < variantDependent.length
						&& (variantDependent[materialId] || positionTinted[materialId])
						&& sourceStates[index] == ColumnRenderSource.SEMANTIC_VARIANT_EXACT) {
						target.add(new MaterialVariantKey(materialId, sourcePositions[index]));
					}
				}
			}
		}

		private record MaterialVariantKey(int materialId, long variantPosition) {
		}
	}

	static record PendingAssetUpdate(
		long generation,
		List<LodColumnSnapshot> snapshots,
		List<VulkanicGalBridge.WorldLodColumnAssetRecord> assets,
		List<VulkanicGalBridge.WorldLodColumnRetirementRecord> retirements,
		List<VulkanicGalBridge.WorldLodColumnMaterialProvenanceRecord> materialProvenance,
		Map<Long, LodMaterialProvenanceSnapshot> materialProvenanceByColumn
	) {
	}

	/**
	 * Per-source-buffer material IDs, one per emitted quad. This is deliberately
	 * a private copied diagnostic asset: it cannot be mistaken for a renderer
	 * texture table or a backend payload.
	 */
	static record LodMaterialProvenanceSnapshot(
		List<ColumnRenderSource.SemanticMaterialIdentity> semanticMaterials,
		LodQuadBuilder.SemanticQuadCoverage inputCoverage,
		LodQuadBuilder.SemanticQuadCoverage outputCoverage,
		List<int[]> opaque,
		List<byte[]> opaqueVariantStates,
		List<long[]> opaqueVariantPositions,
		List<int[]> transparentSide,
		List<byte[]> transparentSideVariantStates,
		List<long[]> transparentSideVariantPositions,
		List<int[]> transparentUp,
		List<byte[]> transparentUpVariantStates,
		List<long[]> transparentUpVariantPositions,
		List<int[]> transparentWaterUp,
		List<byte[]> transparentWaterUpVariantStates,
		List<long[]> transparentWaterUpVariantPositions
	) {
		LodMaterialProvenanceSnapshot {
		semanticMaterials = List.copyOf(Objects.requireNonNull(semanticMaterials, "semanticMaterials"));
		Objects.requireNonNull(inputCoverage, "inputCoverage");
		Objects.requireNonNull(outputCoverage, "outputCoverage");
			opaque = copyArrays(opaque);
			opaqueVariantStates = copyByteArrays(opaqueVariantStates, opaque, "opaque");
			opaqueVariantPositions = copyLongArrays(opaqueVariantPositions, opaque, "opaque");
			transparentSide = copyArrays(transparentSide);
			transparentSideVariantStates = copyByteArrays(transparentSideVariantStates, transparentSide, "transparent-side");
			transparentSideVariantPositions = copyLongArrays(transparentSideVariantPositions, transparentSide, "transparent-side");
			transparentUp = copyArrays(transparentUp);
			transparentUpVariantStates = copyByteArrays(transparentUpVariantStates, transparentUp, "transparent-up");
			transparentUpVariantPositions = copyLongArrays(transparentUpVariantPositions, transparentUp, "transparent-up");
			transparentWaterUp = copyArrays(transparentWaterUp);
			transparentWaterUpVariantStates = copyByteArrays(transparentWaterUpVariantStates, transparentWaterUp, "transparent-water-up");
			transparentWaterUpVariantPositions = copyLongArrays(transparentWaterUpVariantPositions, transparentWaterUp, "transparent-water-up");
	}

	LodMaterialProvenanceSnapshot(
		List<ColumnRenderSource.SemanticMaterialIdentity> semanticMaterials,
		List<int[]> opaque,
		List<int[]> transparentSide,
		List<int[]> transparentUp,
		List<int[]> transparentWaterUp
	) {
		this(
			semanticMaterials,
			new LodQuadBuilder.SemanticQuadCoverage(0, 0, 0),
			new LodQuadBuilder.SemanticQuadCoverage(0, 0, 0),
			opaque,
			unavailableVariantStates(opaque),
			unavailableVariantPositions(opaque),
			transparentSide,
			unavailableVariantStates(transparentSide),
			unavailableVariantPositions(transparentSide),
			transparentUp,
			unavailableVariantStates(transparentUp),
			unavailableVariantPositions(transparentUp),
			transparentWaterUp,
			unavailableVariantStates(transparentWaterUp),
			unavailableVariantPositions(transparentWaterUp)
		);
	}

		private static List<int[]> copyArrays(List<int[]> arrays) {
			Objects.requireNonNull(arrays, "arrays");
			List<int[]> copies = new ArrayList<>(arrays.size());
			for (int[] values : arrays) {
				copies.add(Objects.requireNonNull(values, "semanticMaterialIds").clone());
			}
			return List.copyOf(copies);
		}

		private static List<byte[]> copyByteArrays(List<byte[]> arrays, List<int[]> ids, String name) {
			Objects.requireNonNull(arrays, name + "VariantStates");
			if (arrays.size() != ids.size()) throw new IllegalArgumentException("DH " + name + " variant state buffers must align with material IDs");
			List<byte[]> copies = new ArrayList<>(arrays.size());
			for (int index = 0; index < arrays.size(); index++) {
				byte[] values = Objects.requireNonNull(arrays.get(index), name + "VariantStates");
				if (values.length != ids.get(index).length) throw new IllegalArgumentException("DH " + name + " variant states must align one-for-one with material IDs");
				copies.add(values.clone());
			}
			return List.copyOf(copies);
		}

		private static List<long[]> copyLongArrays(List<long[]> arrays, List<int[]> ids, String name) {
			Objects.requireNonNull(arrays, name + "VariantPositions");
			if (arrays.size() != ids.size()) throw new IllegalArgumentException("DH " + name + " variant position buffers must align with material IDs");
			List<long[]> copies = new ArrayList<>(arrays.size());
			for (int index = 0; index < arrays.size(); index++) {
				long[] values = Objects.requireNonNull(arrays.get(index), name + "VariantPositions");
				if (values.length != ids.get(index).length) throw new IllegalArgumentException("DH " + name + " variant positions must align one-for-one with material IDs");
				copies.add(values.clone());
			}
			return List.copyOf(copies);
		}

		private static List<byte[]> unavailableVariantStates(List<int[]> ids) {
			return ids.stream().map(values -> new byte[values.length]).toList();
		}

		private static List<long[]> unavailableVariantPositions(List<int[]> ids) {
			return ids.stream().map(values -> new long[values.length]).toList();
		}

		long byteSize() {
			long bytes = 0L;
			for (ColumnRenderSource.SemanticMaterialIdentity identity : semanticMaterials) {
				bytes = Math.addExact(bytes, (long)identity.blockStateIdentity().length() * Character.BYTES);
				bytes = Math.addExact(bytes, (long)identity.biomeIdentity().length() * Character.BYTES);
			}
			for (List<int[]> stream : List.of(opaque, transparentSide, transparentUp, transparentWaterUp)) {
				for (int[] ids : stream) {
					bytes = Math.addExact(bytes, (long)ids.length * Integer.BYTES);
				}
			}
			return bytes;
		}
	}

	/** One copied DH draw segment. Its vertices remain quad aligned. */
	public record LodBufferSnapshot(int sourceBufferIndex, List<LodVertex> vertices) {
		public LodBufferSnapshot {
			if (sourceBufferIndex < 0) {
				throw new IllegalArgumentException("Distant Horizons semantic segment source buffer index must be non-negative");
			}
			Objects.requireNonNull(vertices, "vertices");
			if (vertices.size() % 4 != 0) {
				throw new IllegalArgumentException("Distant Horizons semantic segment must contain complete quads");
			}
			vertices = List.copyOf(vertices);
		}
	}

	/**
	 * Backend-neutral fields decoded from DH's fixed CPU LOD format v1.
	 * Positions are column-local unsigned coordinates; the column origin carries
	 * their world placement. The packed metadata is preserved rather than
	 * interpreted as OpenGL vertex attributes.
	 */
	public record LodVertex(
		int localX,
		int localY,
		int localZ,
		int packedLightAndMicroOffset,
		int red,
		int green,
		int blue,
		int alpha,
		int materialId,
		int normalIndex,
		int padding
	) {
		public int skyLight() {
			return packedLightAndMicroOffset & 0xF;
		}

		public int blockLight() {
			return (packedLightAndMicroOffset >>> 4) & 0xF;
		}

		public int microOffset() {
			return (packedLightAndMicroOffset >>> 8) & 0xFF;
		}
	}
}
