package net.vulkanic.world;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.api.enums.config.EDhApiMcRenderingFadeMode;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Private CPU-side boundary for the Rust-owned Distant Horizons route.
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

	private static final int MAX_LOD_SEGMENTS_PER_COLUMN = 512;
	private static final int MAX_LOD_VERTICES_PER_SEGMENT = 2_097_152;
	private static final int MAX_LOD_MATERIAL_ID = 15;
	private static final int MAX_LOD_NORMAL_INDEX = 5;
	private static final int MAX_LOD_MATERIAL_IDENTITIES_PER_COLUMN = 4_096;
	/** A not-yet-published column still consumes visibility bookkeeping. */
	private static final int MAX_PENDING_VISIBLE_COLUMN_KEYS = 16_384;
	/** A bounded semantic transport segment. It is intentionally below Rust's
	 * ABI maximum so a single legacy CPU buffer cannot make the entire coarse
	 * asset update malformed. The boundary is quad aligned and has no native
	 * VBO meaning. */
	private static final int MAX_TRANSPORT_VERTICES_PER_SEGMENT = 65_536;

	/** Package-independent producer boundary for exact-size Rust semantic
	 * packets. Four vertices form one DH quad. */
	public static int maxRustSemanticQuadsPerPacket() {
		return MAX_TRANSPORT_VERTICES_PER_SEGMENT / 4;
	}
	public static final int RENDER_FLAG_WHITE_WORLD = 1;
	public static final int RENDER_FLAG_DITHER_FADE = 1 << 1;
	public static final int RENDER_FLAG_NOISE = 1 << 2;
	public static final int RENDER_FLAG_EARTH_CURVE = 1 << 3;
	/** This frame was preflighted as non-water and selected for the Rust
	 * whole-frame DH route. It is deliberately distinct from semantic capture. */
	public static final int RENDER_FLAG_RUST_NON_WATER_ROUTE_SELECTED = 1 << 4;
	/** ABI-compatible name retained for existing semantic transport consumers. */
	public static final int RENDER_FLAG_RUST_OPAQUE_ROUTE_SELECTED = RENDER_FLAG_RUST_NON_WATER_ROUTE_SELECTED;
	/** The copied vanilla/DH fade is applied after the translucent semantic
	 * world pass. A single bit selects the normal source fade mode; both bits
	 * together encode DH's LOD-only debug composition, which invokes both
	 * source fade boundaries and replaces vanilla color with the DH image.
	 * These remain CPU-only policy data; Rust owns the actual resources. */
	public static final int RENDER_FLAG_VANILLA_FADE_SINGLE_PASS = 1 << 5;
	public static final int RENDER_FLAG_VANILLA_FADE_DOUBLE_PASS = 1 << 6;
	/** Frozen's optional DH far-clip fade runs before the vanilla transition;
	 * Rust consumes this as policy data and owns the actual composition pass. */
	public static final int RENDER_FLAG_DH_FAR_CLIP_FADE = 1 << 7;
	/** Guards the provenance maps below together with the Rust ledger
	 * ({@link DhCollectorLedger}), which owns every column decision (current,
	 * pending, in-flight, published and retiring generations, owner leases,
	 * lifecycle resets, the visible segments being prepared and the route
	 * receipts) and every column payload. Each ledger call returns effects
	 * that keep these maps holding exactly the provenance it references. */
	private static final Object LOCK = new Object();
	/** Exact material provenance retained beside, never inside, the legacy LOD ABI. */
	private static final Map<Long, LodMaterialProvenanceSnapshot> MATERIAL_PROVENANCE = new java.util.HashMap<>();
	/** Java copies of ledger payloads, made only when a diagnostic, probe or
	 * the provenance publication path reads one ({@link #currentColumnLocked},
	 * {@link #publishedColumnLocked}) and dropped by the effects that replace
	 * them. */
	private static final Map<Long, LodColumnSnapshot> CURRENT_COPIES = new java.util.HashMap<>();
	private static final Map<Long, LodColumnSnapshot> PUBLISHED_COPIES = new java.util.HashMap<>();
	/** Provenance paired with the acknowledged asset, never with a newer build. */
	private static final Map<Long, LodMaterialProvenanceSnapshot> PUBLISHED_MATERIAL_PROVENANCE = new LinkedHashMap<>();
	/** Exact-atlas coverage is derived solely from an immutable published column
	 * generation. Keep a bounded cache so visibility traversal does not rescan
	 * every copied quad on every frame. */
	private static final int MAX_EXACT_ATLAS_COVERAGE_CACHE_ENTRIES = 2048;
	private static final Map<Long, ExactAtlasCoverageCacheEntry> EXACT_ATLAS_COVERAGE_CACHE =
		new LinkedHashMap<>(128, 0.75F, true);
	/**
	 * Face-material conversion is immutable for one resource-pack generation,
	 * but a dense DH view repeats the same block-state identities across many
	 * column generations. Cache only copied semantic templates; the per-column
	 * material ID and weighted-model position are still written into fresh ABI
	 * records below. Clearing the collector also clears this cache, so atlas
	 * reloads can never reuse stale Java model data.
	 */
	private static final int MAX_BRIDGE_FACE_MATERIAL_CACHE_ENTRIES = 2048;
	private static final int MAX_BRIDGE_VARIANT_FACE_MATERIAL_CACHE_ENTRIES = 4096;
	private static final Map<String, BridgeFaceMaterialResolution> BRIDGE_FACE_MATERIAL_CACHE =
		new LinkedHashMap<>(128, 0.75F, true);
	private static final Map<BridgeVariantMaterialKey, BridgeFaceMaterialResolution> BRIDGE_VARIANT_FACE_MATERIAL_CACHE =
		new LinkedHashMap<>(256, 0.75F, true);
	private static int executionTraceEvents;
	private static long lastExecutionTraceNanos;
	private static int rejectionTraceEvents;
	private static long lastRejectionTraceNanos;
	/** Last semantic DH segment set handed to the combined frame (the ledger's
	 * last consumed set, as records). Diagnostic capture may inspect this
	 * bounded copy, but it never feeds admission. */
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
	/** The copied render parameters of the frame being prepared. Whether that
	 * frame is enabled, and its flags, are the ledger's. */
	private static VulkanicGalBridge.WorldLodRenderFrameRecord PENDING_RENDER_FRAME =
		VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
	/** Visible opaque segments whose copied quad sidecars still name one
	 * source material per quad. These are candidates for the private exact
	 * atlas route; they are deliberately separate from generic route selection
	 * because model-face resolution remains a Rust asset admission concern. */
	private static int routeExactAtlasIdentitySegments;
	private static int routeExactAtlasIdentityQuads;
	/** Visible opaque segments containing both proven exact quads and explicit
	 * unresolved quads. These are now rendered as an exact subset plus the
	 * complementary reduced-color index range; they are not complete
	 * replacement segments. */
	private static int routeExactAtlasPartialSegments;
	private static int routeExactAtlasPartialQuads;
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
	private static long exactAtlasCoverageStableSignature;
	private static long exactAtlasCoverageStableFrame = Long.MIN_VALUE;
	private static int exactAtlasCoverageStableFrames;
	/** Bounded per-visible-column reconciliation; never a rendering decision. */
	private static final List<String> routeExactAtlasCoverageSamples = new ArrayList<>();
	/** Bounded evidence for why a copied semantic material did or did not
	 * produce a face-atlas table. This is diagnostic-only: it never changes
	 * route selection or causes a material fallback. */
	private static final Map<DistantHorizonsFaceMaterialResolver.Status, Integer> routeExactAtlasResolutionStatusCounts = new LinkedHashMap<>();
	private static final List<String> routeExactAtlasResolutionSamples = new ArrayList<>();
	/** Capture-only packed DH water-color samples. This is deliberately
	 * bounded and records the bytes at the Java-to-Rust semantic boundary,
	 * before any Rust shader or lightmap stage can alter them. */
	private static final LinkedHashSet<String> AUDIT_PACKED_WATER_COLORS = new LinkedHashSet<>();
	/** Bounded identity-level evidence for unresolved coarse contributors. */
	private static final int MAX_ROUTE_EXACT_ATLAS_IDENTITY_COUNTS = 128;
	private static final Map<String, Integer> routeExactAtlasResolutionIdentityCounts = new LinkedHashMap<>();
	/** Capture-only probes for the render-data boundary that precedes DH quad
	 * generation. They are configured solely by the deterministic fixture and
	 * never affect source conversion, mesh construction, or route selection. */
	private static List<BlockPos> waterSourceInputProbes = List.of();
	private static List<BlockPos> configuredWaterSourceInputProbes = List.of();
	private static final Map<BlockPos, WaterSourceInputTrace> WATER_SOURCE_INPUT_TRACES = new LinkedHashMap<>();

	/** The configuration the ledger needs for this call. */
	private static int ledgerConfig() {
		return (usesRustWholeFrameSemanticBuild() ? DhCollectorLedger.CONFIG_RUST_WHOLE_FRAME : 0)
			| (retainsLegacyObservationSnapshots() ? DhCollectorLedger.CONFIG_LEGACY_OBSERVATION : 0)
			| (executionSnapshotsEnabled() ? DhCollectorLedger.CONFIG_EXECUTION_SNAPSHOTS : 0)
			| (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics") ? DhCollectorLedger.CONFIG_TRACE_PUBLICATION : 0);
	}

	/**
	 * Applies a ledger call's effects to the provenance maps and payload
	 * copies. {@code builtProvenance} is the provenance of the column that call
	 * recorded; {@code updateProvenance} that of the update it acknowledged.
	 */
	private static void applyEffectsLocked(
		int count,
		LodMaterialProvenanceSnapshot builtProvenance,
		Map<Long, LodMaterialProvenanceSnapshot> updateProvenance
	) {
		long[] effects = DhCollectorLedger.takeEffects(count);
		for (int at = 0; at < effects.length; at += 3) {
			long key = effects[at + 1];
			long detail = effects[at + 2];
			switch ((int)effects[at]) {
				case DhCollectorLedger.EFFECT_SET_CURRENT -> {
					CURRENT_COPIES.remove(key);
					if (builtProvenance != null) MATERIAL_PROVENANCE.put(key, builtProvenance);
					else MATERIAL_PROVENANCE.remove(key);
				}
				case DhCollectorLedger.EFFECT_DROP_CURRENT -> {
					CURRENT_COPIES.remove(key);
					MATERIAL_PROVENANCE.remove(key);
				}
				case DhCollectorLedger.EFFECT_PUBLISH_CURRENT -> {
					PUBLISHED_COPIES.remove(key);
					if (builtProvenance != null) PUBLISHED_MATERIAL_PROVENANCE.put(key, builtProvenance);
					else if (detail != 0L) PUBLISHED_MATERIAL_PROVENANCE.remove(key);
				}
				case DhCollectorLedger.EFFECT_PUBLISH_UPDATE -> {
					PUBLISHED_COPIES.remove(key);
					LodMaterialProvenanceSnapshot provenance = updateProvenance.get(key);
					if (provenance == null) PUBLISHED_MATERIAL_PROVENANCE.remove(key);
					else PUBLISHED_MATERIAL_PROVENANCE.put(key, provenance);
				}
				case DhCollectorLedger.EFFECT_DROP_PUBLISHED -> {
					PUBLISHED_COPIES.remove(key);
					PUBLISHED_MATERIAL_PROVENANCE.remove(key);
				}
				case DhCollectorLedger.EFFECT_CLEAR -> {
					CURRENT_COPIES.clear();
					MATERIAL_PROVENANCE.clear();
					PUBLISHED_COPIES.clear();
					PUBLISHED_MATERIAL_PROVENANCE.clear();
				}
				default -> throw new IllegalStateException("Unknown DH ledger effect " + effects[at]);
			}
		}
	}

	/** Segment records from the ledger's five-long encoding. */
	private static List<VulkanicGalBridge.WorldLodColumnInstanceRecord> records(long[] values) {
		List<VulkanicGalBridge.WorldLodColumnInstanceRecord> records = new ArrayList<>(values.length / 5);
		for (int at = 0; at + 4 < values.length; at += 5) {
			records.add(new VulkanicGalBridge.WorldLodColumnInstanceRecord(
				values[at], values[at + 1], (int)values[at + 2], (int)values[at + 3], (int)values[at + 4]
			));
		}
		return List.copyOf(records);
	}

	private static List<VulkanicGalBridge.WorldLodColumnInstanceRecord> pendingVisibleSegmentsLocked() {
		return records(DhCollectorLedger.segments(DhCollectorLedger.SEGMENTS_PENDING));
	}

	/** The prepared frame as the ledger has it: the copied parameters with its flags, or disabled. */
	private static VulkanicGalBridge.WorldLodRenderFrameRecord frameRecord(boolean enabled, int flags) {
		return enabled ? withFlags(PENDING_RENDER_FRAME, flags) : VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
	}

	private DistantHorizonsSemanticCollector() {
	}

	/**
	 * Arms a bounded source-data diagnostic for deterministic water fixtures.
	 * Replacing the probe set clears previous observations so a copied-world run
	 * cannot accidentally certify a later fixture from stale data.
	 */
	public static void configureWaterSourceInputProbes(List<BlockPos> probes) {
		synchronized (LOCK) {
			configuredWaterSourceInputProbes = probes == null ? List.of() : List.copyOf(probes.stream().limit(8).toList());
			waterSourceInputProbes = configuredWaterSourceInputProbes;
			WATER_SOURCE_INPUT_TRACES.clear();
		}
	}

	/**
	 * Re-arms capture-only probes after a DH world reset without discarding an
	 * observation that is already in flight. This is intentionally idempotent:
	 * fixture orchestration may call it once per frame while the real producer
	 * initializes, but it never changes semantic conversion or submitted data.
	 */
	public static void ensureWaterSourceInputProbes(List<BlockPos> probes) {
		synchronized (LOCK) {
			if (waterSourceInputProbes.isEmpty() && probes != null && !probes.isEmpty()) {
				configuredWaterSourceInputProbes = List.copyOf(probes.stream().limit(8).toList());
				waterSourceInputProbes = configuredWaterSourceInputProbes;
				WATER_SOURCE_INPUT_TRACES.clear();
			}
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
		synchronized (LOCK) {
			if (waterSourceInputProbes.isEmpty()) {
				return;
			}
			int width = 1 << detailLevel;
			int sourceMaxX = sourceMinX + width;
			int sourceMaxZ = sourceMinZ + width;
			int minY = sourceOriginY + RenderDataPointUtil.getYMin(data);
			int maxY = sourceOriginY + RenderDataPointUtil.getYMax(data);
			int materialId = RenderDataPointUtil.getBlockMaterialId(data);
			// DH's reduced water surface can place the fixture probe exactly on
			// the source interval's upper boundary.  Preserve the normal half-open
			// interval for every other material; only WATER may use that boundary,
			// and it is still required to match the configured x/z cell.
			boolean waterUpperSurface = materialId == EDhApiBlockMaterial.WATER.index;
			for (BlockPos probe : waterSourceInputProbes) {
				if (probe.getX() < sourceMinX || probe.getX() >= sourceMaxX
					|| probe.getZ() < sourceMinZ || probe.getZ() >= sourceMaxZ
					|| probe.getY() < minY
					|| (probe.getY() >= maxY && !(waterUpperSurface && probe.getY() == maxY))) {
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
		synchronized (LOCK) {
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
			|| usesRustWholeFrameSemanticBuild();
	}

	private static boolean exactAtlasCoverageRequested() {
		return Boolean.getBoolean(CAPTURE_PROPERTY)
			|| Boolean.getBoolean(LEGACY_OBSERVATION_PROPERTY);
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

	private static boolean graphicsAuditEnabled() {
		String value = System.getenv("MATTMC_GRAPHICS_AUDIT");
		return value != null && (value.equals("1") || value.equalsIgnoreCase("true"));
	}

	/**
	 * Execution snapshots retain copied columns and material sidecars solely so
	 * a later capture probe can prove the exact generation that was submitted.
	 * They are not render inputs. Keep that bounded diagnostic history disabled
	 * for ordinary gameplay, while retaining it for semantic captures, legacy
	 * observations, graphics-audit runs, and the explicitly selected source
	 * execution route.
	 */
	private static boolean executionSnapshotsEnabled() {
		return graphicsAuditEnabled()
			|| Boolean.getBoolean(CAPTURE_PROPERTY)
			|| Boolean.getBoolean(LEGACY_OBSERVATION_PROPERTY)
			|| selectedSourceExecutionRequested();
	}

	/**
	 * Exact material sidecars are source-execution and capture inputs. The
	 * ordinary DH path consumes the copied reduced-color/material-category
	 * stream, so retaining and expanding an exact-atlas representation there
	 * only duplicates memory and CPU work.
	 */
	private static boolean materialProvenancePublicationRequired() {
		if (Boolean.getBoolean(EXACT_MATERIAL_TOPOLOGY_PROPERTY)) {
			return true;
		}
		// A selected shader pack shades DH's reduced-color stream (Iris); the
		// per-face exact-material sidecar has no consumer there, and resolving it
		// (positional biome tint for every face) stalled each column publication.
		return exactAtlasCoverageRequested()
			&& !net.vulkanic.gui.RustGalFrameCoordinator.shaderPackSourceActive();
	}

	/** True only for the backend-owned whole-frame route. Diagnostic capture by
	 * itself must continue through DH's ordinary Java upload lifecycle. */
	public static boolean usesRustWholeFrameSemanticBuild() {
		// The backend route remains selected while DH's quick toggle is off, but
		// its background loader can still finish database work already in flight.
		// Do not divert that work into Rust semantic buffers unless a renderer can
		// consume them. Selected-source execution is an explicit independent
		// consumer and therefore keeps semantic construction enabled.
		return selectedSourceExecutionRequested()
			|| (Config.Client.Advanced.Debugging.rendererMode.get() == EDhApiRendererMode.DEFAULT);
	}

	/**
	 * Whether this semantic build needs topology split at exact material and
	 * weighted-variant boundaries. Vanilla DH's reduced-color renderer does not:
	 * it greedily merges those faces, and changing that topology both diverges
	 * from Frozen and multiplies the copied/GPU workload. Keep the stricter
	 * topology tied to the explicitly selected source-material contract instead
	 * of treating every Rust-owned DH frame as an exact-atlas frame.
	 */
	public static boolean usesExactMaterialTopologyBuild() {
		// Iris shader packs consume DH's own reduced-color/material-id stream on
		// DH's greedy topology (Frozen), exactly as ordinary gameplay with a
		// selected pack does here. The exact split is an explicit diagnostic
		// build only; the selected-source harness flag must not change geometry.
		return usesRustWholeFrameSemanticBuild() && Boolean.getBoolean(EXACT_MATERIAL_TOPOLOGY_PROPERTY);
	}

	/** Opt-in exact-material DH topology for exact-atlas source diagnostics. */
	public static final String EXACT_MATERIAL_TOPOLOGY_PROPERTY =
		"mattmc.dev.rustGalDistantHorizons.exactMaterialTopology";

	/** Whether copied CPU geometry exists for this real DH quadtree section.
	 * This is quadtree bookkeeping only: it stops DH from indefinitely queuing
	 * the same source work while Rust owns the eventual presentation. */
	public static boolean hasColumn(long columnKey) {
		synchronized (LOCK) {
			return DhCollectorLedger.query(DhCollectorLedger.QUERY_HAS_COLUMN, columnKey) != 0L;
		}
	}

	/** Generation-qualified CPU lifecycle check for DH container owners. */
	public static boolean hasColumn(long columnKey, long columnGeneration) {
		if (columnGeneration == 0L) return false;
		synchronized (LOCK) {
			return DhCollectorLedger.hasColumnGeneration(columnKey, columnGeneration);
		}
	}

	/** True only once a copied column has completed its explicit Rust asset
	 * transaction. Callers that construct a visible Rust render list must use
	 * this boundary, never the merely-collected quadtree state above. */
	public static boolean hasPublishedColumn(long columnKey) {
		synchronized (LOCK) {
			return DhCollectorLedger.query(DhCollectorLedger.QUERY_HAS_PUBLISHED_COLUMN, columnKey) != 0L;
		}
	}

	/**
	 * Requests bounded native publication for a DH section that is about to
	 * participate in a parent/child visibility transition. The quadtree must not
	 * call a CPU-built column renderable before its immutable Rust asset is
	 * acknowledged: doing so disables the covering parent and presents a hole.
	 * An older acknowledged generation remains renderable while its replacement
	 * is queued, preserving the existing generation-safe rebuild contract.
	 */
	public static boolean requestColumnPublication(long columnKey) {
		if (!enabled()) {
			return false;
		}
		synchronized (LOCK) {
			return DhCollectorLedger.requestPublication(columnKey);
		}
	}

	/**
	 * Reports a real legacy or Rust-visible opaque segment covering a point in
	 * the current frame. This is capture evidence only; it neither publishes a
	 * Rust frame nor changes a route decision.
	 */
	public static boolean hasObservedVisibleOpaqueColumnCoveringBlock(int blockX, int blockZ) {
		synchronized (LOCK) {
			for (VulkanicGalBridge.WorldLodColumnInstanceRecord segment : pendingVisibleSegmentsLocked()) {
				if (segment.layer() != 1) continue;
				// COLUMNS.get: an access in the ledger's LRU, as it was in Java.
				long current = DhCollectorLedger.query(DhCollectorLedger.QUERY_TOUCH, segment.columnKey());
				LodColumnSnapshot column = current == 0L ? null : currentColumnLocked(segment.columnKey());
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
	 * Capture-only proof that the actual consumed opaque DH column covers a
	 * requested world position. Unlike the exact-atlas provenance helpers this
	 * validates the native DH stream itself, whose reduced vertices retain only
	 * DH material categories and vertex color. It must not be used to infer an
	 * exact Minecraft sprite that the reduced source did not preserve.
	 */
	public static boolean hasLastConsumedVisibleOpaqueColumnCoveringBlock(int blockX, int blockZ) {
		synchronized (LOCK) {
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
		synchronized (LOCK) {
			int cachedColumns = 0;
			int publishedColumns = 0;
			int consumedOpaqueSegments = 0;
			List<String> samples = new ArrayList<>();
			for (long columnKey : DhCollectorLedger.columnKeys()) {
				int minX = DhSectionPos.getMinCornerBlockX(columnKey);
				int minZ = DhSectionPos.getMinCornerBlockZ(columnKey);
				int width = DhSectionPos.getBlockWidth(columnKey);
				if (blockX < minX || blockX >= minX + width || blockZ < minZ || blockZ >= minZ + width) {
					continue;
				}
				cachedColumns++;
				LodColumnSnapshot column = currentColumnLocked(columnKey);
				boolean published = DhCollectorLedger.query(DhCollectorLedger.QUERY_PUBLISHED_GENERATION, columnKey) == column.generation();
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
					String payloadDifference = DhCollectorLedger.payloadDifference(columnKey);
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
		synchronized (LOCK) {
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
		synchronized (LOCK) {
			long lastExecutedWorldFrame = DhCollectorLedger.query(DhCollectorLedger.QUERY_LAST_EXECUTED_WORLD_FRAME, 0L);
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
		synchronized (LOCK) {
			if (legacyObservedSegments) {
				visibleSegments = pendingVisibleSegmentsLocked();
			} else {
				executionWorldFrame = DhCollectorLedger.query(DhCollectorLedger.QUERY_LAST_EXECUTED_WORLD_FRAME, 0L);
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
		synchronized (LOCK) {
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
		synchronized (LOCK) {
			executionWorldFrame = DhCollectorLedger.query(DhCollectorLedger.QUERY_LAST_EXECUTED_WORLD_FRAME, 0L);
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
		synchronized (LOCK) {
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
		synchronized (LOCK) {
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
		String firstWaterBounds = "";
		for (long columnKey : DhCollectorLedger.publishedPayloadKeys()) {
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
					if (firstWaterBounds.isEmpty()) {
						firstWaterBounds = waterQuadBounds(column, quad.vertices(), quad.quadIndex());
					}
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
			probe.getX(), probe.getY(), probe.getZ(), false,
			firstWaterBounds.isEmpty() ? "no-spatial-published-water-quad" : "no-spatial-published-water-quad:first=" + firstWaterBounds,
			0L, 0L, -1, -1, 0, 0, 0, ""
		);
	}

	private static String waterQuadBounds(LodColumnSnapshot column, List<LodVertex> vertices, int quadIndex) {
		int first = Math.multiplyExact(quadIndex, 4);
		if (first < 0 || first + 4 > vertices.size()) return "invalid";
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (int i = first; i < first + 4; i++) {
			LodVertex vertex = vertices.get(i);
			minX = Math.min(minX, column.originX() + vertex.localX());
			maxX = Math.max(maxX, column.originX() + vertex.localX());
			minY = Math.min(minY, column.originY() + vertex.localY());
			maxY = Math.max(maxY, column.originY() + vertex.localY());
			minZ = Math.min(minZ, column.originZ() + vertex.localZ());
			maxZ = Math.max(maxZ, column.originZ() + vertex.localZ());
		}
		return minX + ".." + maxX + "," + minY + ".." + maxY + "," + minZ + ".." + maxZ;
	}

	private static DistantHorizonsWaterProbeResult waterCachedProbeResultLocked(BlockPos probe) {
		for (long columnKey : DhCollectorLedger.columnKeys()) {
			LodMaterialProvenanceSnapshot provenance = MATERIAL_PROVENANCE.get(columnKey);
			if (provenance == null) {
				continue;
			}
			LodColumnSnapshot column = currentColumnLocked(columnKey);
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
		synchronized (LOCK) {
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
		boolean occupiesQueriedBlock = blockY >= minY && blockY <= maxY;
		// DH's ordinary terrain source emits a horizontal top face on the upper
		// plane of the source block. Preserve exact-cell matching while accepting
		// that one-block offset; arbitrary raised geometry remains rejected.
		boolean topFaceOfQueriedBlock = blockY != Integer.MAX_VALUE && minY == maxY && minY == blockY + 1;
		return blockX >= minX && blockX <= maxX
			&& blockZ >= minZ && blockZ <= maxZ
			&& (blockY == Integer.MIN_VALUE || occupiesQueriedBlock || topFaceOfQueriedBlock);
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
			// Depending on DH's reduction level, the copied water cell is
			// represented either by its top plane (y + 1) or by the collapsed
			// surface plane itself (y). Accept only those two exact planes.
			&& minY == maxY && (minY == probe.getY() || minY == probe.getY() + 1);
	}

	/**
	 * Takes ownership of exact-size Java packets produced exclusively for the
	 * Rust whole-frame route.  The packets are already bounded to the semantic
	 * transport granularity, so copying them through legacy native VBO staging
	 * would add allocation pressure without adding information.
	 */
	public static long recordRustSemanticBuiltColumn(
		long columnKey,
		DhBlockPos origin,
		List<ColumnRenderSource.SemanticMaterialIdentity> semanticMaterials,
		LodQuadBuilder.SemanticQuadCoverage inputCoverage,
		LodQuadBuilder.SemanticQuadCoverage outputCoverage,
		LodQuadBuilder.SemanticVertexBufferBuild opaque,
		LodQuadBuilder.SemanticVertexBufferBuild transparentSide,
		LodQuadBuilder.SemanticVertexBufferBuild transparentUp,
		LodQuadBuilder.SemanticVertexBufferBuild transparentWaterUp
	) {
		return recordRustSemanticColumn(columnKey, origin, semanticMaterials, inputCoverage, outputCoverage,
			opaque, transparentSide, transparentUp, transparentWaterUp, false).generation();
	}

	/** Publishes and acquires CPU ownership atomically, including generation reuse. */
	public static SemanticColumnLease recordOwnedRustSemanticBuiltColumn(
		long columnKey,
		DhBlockPos origin,
		List<ColumnRenderSource.SemanticMaterialIdentity> semanticMaterials,
		LodQuadBuilder.SemanticQuadCoverage inputCoverage,
		LodQuadBuilder.SemanticQuadCoverage outputCoverage,
		LodQuadBuilder.SemanticVertexBufferBuild opaque,
		LodQuadBuilder.SemanticVertexBufferBuild transparentSide,
		LodQuadBuilder.SemanticVertexBufferBuild transparentUp,
		LodQuadBuilder.SemanticVertexBufferBuild transparentWaterUp
	) {
		return recordRustSemanticColumn(columnKey, origin, semanticMaterials, inputCoverage, outputCoverage,
			opaque, transparentSide, transparentUp, transparentWaterUp, true);
	}

	private static SemanticColumnLease recordRustSemanticColumn(
		long columnKey,
		DhBlockPos origin,
		List<ColumnRenderSource.SemanticMaterialIdentity> semanticMaterials,
		LodQuadBuilder.SemanticQuadCoverage inputCoverage,
		LodQuadBuilder.SemanticQuadCoverage outputCoverage,
		LodQuadBuilder.SemanticVertexBufferBuild opaque,
		LodQuadBuilder.SemanticVertexBufferBuild transparentSide,
		LodQuadBuilder.SemanticVertexBufferBuild transparentUp,
		LodQuadBuilder.SemanticVertexBufferBuild transparentWaterUp,
		boolean retainOwner
	) {
		if (!enabled()) return new SemanticColumnLease(columnKey, 0L, 0L);
		Objects.requireNonNull(semanticMaterials, "semanticMaterials");
		recordPackedWaterColorSamples(opaque.packedVertexBuffers(), "opaque");
		recordPackedWaterColorSamples(transparentSide.packedVertexBuffers(), "transparent-side");
		recordPackedWaterColorSamples(transparentUp.packedVertexBuffers(), "transparent-up");
		recordPackedWaterColorSamples(transparentWaterUp.packedVertexBuffers(), "water-up");
		LodMaterialProvenanceSnapshot provenance = materialProvenancePublicationRequired()
			? new LodMaterialProvenanceSnapshot(
				semanticMaterials, inputCoverage, outputCoverage,
				validateOwnedMaterialIds(opaque, semanticMaterials.size()), opaque.semanticVariantStates(), opaque.semanticVariantPositions(),
				validateOwnedMaterialIds(transparentSide, semanticMaterials.size()), transparentSide.semanticVariantStates(), transparentSide.semanticVariantPositions(),
				validateOwnedMaterialIds(transparentUp, semanticMaterials.size()), transparentUp.semanticVariantStates(), transparentUp.semanticVariantPositions(),
				validateOwnedMaterialIds(transparentWaterUp, semanticMaterials.size()), transparentWaterUp.semanticVariantStates(), transparentWaterUp.semanticVariantPositions()
			)
			: null;
		return recordOwnedPackedColumnSnapshot(
			columnKey, origin, opaque.packedVertexBuffers(), transparentSide.packedVertexBuffers(),
			transparentUp.packedVertexBuffers(), transparentWaterUp.packedVertexBuffers(), provenance, retainOwner
		);
	}

	private static void recordPackedWaterColorSamples(List<byte[]> packets, String stream) {
		if (!graphicsAuditEnabled() || packets == null) return;
		for (byte[] packet : packets) {
			for (int offset = 0; offset + VERTEX_STRIDE_BYTES <= packet.length; offset += VERTEX_STRIDE_BYTES) {
				int material = packet[offset + 12] & 0xff;
				if (material != EDhApiBlockMaterial.WATER.index) continue;
				String sample = stream + ":rgb="
					+ (packet[offset + 8] & 0xff) + ","
					+ (packet[offset + 9] & 0xff) + ","
					+ (packet[offset + 10] & 0xff)
					+ ":alpha=" + (packet[offset + 11] & 0xff);
				synchronized (AUDIT_PACKED_WATER_COLORS) {
					if (AUDIT_PACKED_WATER_COLORS.size() >= 16 || !AUDIT_PACKED_WATER_COLORS.add(sample)) continue;
				}
				System.out.println("[MattMC graphics audit] DH packed water vertex " + sample);
			}
		}
	}

	private static List<int[]> validateOwnedMaterialIds(
		LodQuadBuilder.SemanticVertexBufferBuild build,
		int semanticMaterialCount
	) {
		for (int[] source : build.semanticMaterialIds()) {
			for (int materialId : source) {
				if (materialId < ColumnRenderSource.SEMANTIC_MATERIAL_MIXED || materialId > semanticMaterialCount) {
					throw new IllegalArgumentException("Distant Horizons semantic material ID is outside its builder-local table: " + materialId);
				}
			}
		}
		return build.semanticMaterialIds();
	}

	private static SemanticColumnLease recordOwnedPackedColumnSnapshot(
		long columnKey,
		DhBlockPos origin,
		List<byte[]> opaque,
		List<byte[]> transparentSide,
		List<byte[]> transparentUp,
		List<byte[]> transparentWaterUp,
		LodMaterialProvenanceSnapshot provenance,
		boolean retainOwner
	) {
		if (!enabled()) return new SemanticColumnLease(columnKey, 0L, 0L);
		Objects.requireNonNull(origin, "origin");
		LodColumnSnapshot snapshot = new LodColumnSnapshot(
			columnKey, DhCollectorLedger.allocateGeneration(), origin.getX(), origin.getY(), origin.getZ(),
			ownedPackedBuffers(opaque), ownedPackedBuffers(transparentSide),
			ownedPackedBuffers(transparentUp), ownedPackedBuffers(transparentWaterUp)
		);
		synchronized (LOCK) {
			long[] result = new long[2];
			recordBuiltSnapshotLocked(columnKey, snapshot, provenance, retainOwner, result);
			return new SemanticColumnLease(columnKey, result[0], result[1]);
		}
	}

	/** An idempotent CPU lifetime token, never a native resource or GPU handle.
	 * Its owner group lives in the ledger; replacement and reset start a new
	 * group, so a late close cannot release a newer group for the same key. */
	public static final class SemanticColumnLease implements AutoCloseable {
		private final long columnKey;
		private final long generation;
		private final long ownerToken;
		private boolean closed;

		private SemanticColumnLease(long columnKey, long generation, long ownerToken) {
			this.columnKey = columnKey;
			this.generation = generation;
			this.ownerToken = ownerToken;
		}

		public long generation() { return this.generation; }

		@Override
		public void close() {
			synchronized (LOCK) {
				if (this.closed) return;
				this.closed = true;
				if (this.ownerToken == 0L) return;
				applyEffectsLocked(DhCollectorLedger.releaseOwner(ledgerConfig(), this.columnKey, this.generation, this.ownerToken),
					null, Map.of());
			}
		}
	}

	/** Hands one validated snapshot's payload to the ledger while holding
	 * {@link #LOCK}: Rust copies the packed segments, compares them with the
	 * current column and decides; Java keeps only the provenance. Writes
	 * [generation, owner token] to {@code result} and returns the generation
	 * (0 for an empty build). */
	private static long recordBuiltSnapshotLocked(
		long columnKey,
		LodColumnSnapshot snapshot,
		LodMaterialProvenanceSnapshot provenance,
		boolean retainOwner,
		long[] result
	) {
		List<List<LodBufferSnapshot>> layers = List.of(
			snapshot.opaque(), snapshot.transparentSide(), snapshot.transparentUp(), snapshot.transparentWaterUp());
		for (int layer = 0; layer < layers.size(); layer++) {
			for (LodBufferSnapshot segment : layers.get(layer)) {
				DhCollectorLedger.stageSegment(layer, segment.sourceBufferIndex(), segment.packedVerticesForRust());
			}
		}
		boolean sameProvenance = Objects.equals(MATERIAL_PROVENANCE.get(columnKey), provenance);
		int effects = DhCollectorLedger.recordBuilt(
			ledgerConfig(), columnKey, snapshot.generation(), snapshot.originX(), snapshot.originY(), snapshot.originZ(),
			provenance == null ? -1L : provenance.byteSize(), sameProvenance, retainOwner, result
		);
		applyEffectsLocked(effects, provenance, Map.of());
		return result[0];
	}

	/** Capture-only hook used at LodQuadBuilder's shared packed-byte boundary.
	 * It remains active for the Frozen control as well as the Rust route, so the
	 * two renderers can be compared before either shader sees the bytes. */
	public static void recordPackedWaterVertexForAudit(int red, int green, int blue, int alpha) {
		if (!graphicsAuditEnabled()) return;
		String sample = "builder:rgb=" + red + "," + green + "," + blue + ":alpha=" + alpha;
		synchronized (AUDIT_PACKED_WATER_COLORS) {
			if (AUDIT_PACKED_WATER_COLORS.size() >= 16 || !AUDIT_PACKED_WATER_COLORS.add(sample)) return;
		}
		System.out.println("[MattMC graphics audit] DH packed water vertex " + sample);
	}

	/** Records a real quadtree request to materialize copied CPU column data.
	 * The counter is capture-lifetime bounded metadata, never a readiness
	 * substitute. */
	public static void recordSemanticBuildAttempt(long columnKey) {
		if (usesRustWholeFrameSemanticBuild()) {
			synchronized (LOCK) {
				DhCollectorLedger.recordBuildAttempt(DhCollectorLedger.CONFIG_RUST_WHOLE_FRAME);
			}
		}
	}

	public static void removeColumn(long columnKey) {
		if (retainsLegacyObservationSnapshots()) {
			return;
		}
		synchronized (LOCK) {
			applyEffectsLocked(DhCollectorLedger.removeColumn(ledgerConfig(), columnKey, -1L), null, Map.of());
		}
	}

	/** Retires only the semantic generation owned by one DH buffer container.
	 * A late close from a replaced container must not remove the newer immutable
	 * generation already installed for the same section key. */
	public static void removeColumn(long columnKey, long columnGeneration) {
		if (retainsLegacyObservationSnapshots() || columnGeneration <= 0L) {
			return;
		}
		synchronized (LOCK) {
			applyEffectsLocked(DhCollectorLedger.removeColumn(ledgerConfig(), columnKey, columnGeneration), null, Map.of());
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
			synchronized (LOCK) {
				PENDING_RENDER_FRAME = VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
				DhCollectorLedger.beginFrame(false, 0, "missing-exact-camera".equals(matrixStatus)
					? "missing-dh-camera-semantics"
					: "non-finite-dh-render-matrix", matrixStatus, matrixDetail);
				resetExactAtlasIdentityCoverage();
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
		// Capture-only diagnostic: isolate the DH transition coverage from the
		// copied geometry and the near-world terrain handoff.  This is deliberately
		// an environment gate rather than a gameplay setting; normal runs retain
		// DH's authored clip distance and future Iris/DH integration sees the same
		// semantic value as the source renderer.
		if (graphicsAuditEnabled()) {
			String clipOverride = System.getenv("MATTMC_CAPTURE_DH_CLIP_DISTANCE_OVERRIDE");
			if (clipOverride != null && !clipOverride.isBlank()) {
				try {
					float parsed = Float.parseFloat(clipOverride.trim());
					if (Float.isFinite(parsed) && parsed >= 0.0F) {
						clipDistance = parsed;
					}
				} catch (NumberFormatException ignored) {
					// Keep the source-derived clip value on malformed diagnostics.
				}
			}
		}
		synchronized (LOCK) {
			DhCollectorLedger.setClipDistance(clipDistance);
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
		if (Config.Client.Advanced.Debugging.lodOnlyMode.get()) {
			flags |= RENDER_FLAG_VANILLA_FADE_SINGLE_PASS | RENDER_FLAG_VANILLA_FADE_DOUBLE_PASS;
		} else {
			switch (Config.Client.Advanced.Graphics.Quality.vanillaFadeMode.get()) {
				case SINGLE_PASS -> flags |= RENDER_FLAG_VANILLA_FADE_SINGLE_PASS;
				case DOUBLE_PASS -> flags |= RENDER_FLAG_VANILLA_FADE_DOUBLE_PASS;
				case NONE -> { }
			}
		}
		if (Config.Client.Advanced.Graphics.Quality.dhFadeFarClipPlane.get()) {
			flags |= RENDER_FLAG_DH_FAR_CLIP_FADE;
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
			},
			dhFogParameters(),
			renderParams.clientLevelWrapper.getMaxHeight(),
			ssaoParameters()
		);
		synchronized (LOCK) {
			PENDING_RENDER_FRAME = renderFrame;
			DhCollectorLedger.beginFrame(true, renderFrame.flags(), "pending", matrixStatus, matrixDetail);
			resetExactAtlasIdentityCoverage();
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

	/** Expands a real quadtree-visible column into copied material segments.
	 * The returned indices are global compact indices in the published column
	 * asset, never per-layer ordinals. Each material stream keeps its explicit
	 * source layer so Rust can select its private pipeline policy. */
	public static VisibleColumnSegments recordVisibleMaterialColumn(long columnKey) {
		if (!enabled()) {
			return VisibleColumnSegments.EMPTY;
		}
		synchronized (LOCK) {
			// The ledger admits only the acknowledged generation: a closed column
			// awaiting retirement is not eligible, an unpublished one is queued for
			// publication, and a rebuilt one keeps its acknowledged asset until the
			// replacement is acknowledged.
			boolean exactAtlas = exactAtlasCoverageRequested();
			long[] admitted = new long[5];
			if (!DhCollectorLedger.visibleColumn(columnKey, !exactAtlas, admitted)) {
				return VisibleColumnSegments.EMPTY;
			}
			long generation = admitted[0];
			int opaqueSegments = (int)admitted[1];
			int transparentSideSegments = (int)admitted[2];
			int transparentUpSegments = (int)admitted[3];
			int waterSegments = (int)admitted[4];
			if (exactAtlas) {
				LodColumnSnapshot exactAtlasColumn = publishedColumnLocked(columnKey);
				if (exactAtlasColumn == null || exactAtlasColumn.generation() != generation) {
					throw new IllegalStateException("exact-atlas coverage requires its published LOD geometry");
				}
				ExactAtlasIdentityCoverage exactAtlasCoverage = exactAtlasIdentityCoverageCached(exactAtlasColumn);
				routeExactAtlasIdentitySegments += exactAtlasCoverage.completeSegments();
				routeExactAtlasIdentityQuads += exactAtlasCoverage.completeQuads();
				routeExactAtlasPartialSegments += exactAtlasCoverage.partialSegments();
				routeExactAtlasPartialQuads += exactAtlasCoverage.partialQuads();
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
						+ ",generation=" + generation
						+ ",inputOpaqueKnown=" + exactAtlasCoverage.inputOpaqueKnownQuads()
						+ ",outputOpaqueKnown=" + exactAtlasCoverage.outputOpaqueKnownQuads()
						+ ",outputOpaqueUnavailable=" + exactAtlasCoverage.outputOpaqueUnavailableQuads()
						+ ",copiedOpaqueQuads=" + exactAtlasCoverage.copiedOpaqueQuads()
						+ ",copiedUnavailable=" + exactAtlasCoverage.unavailableQuads()
						+ ",table=" + exactAtlasCoverage.identityTableEntries()
					);
				}
				DhCollectorLedger.appendVisibleColumn(columnKey, generation, opaqueSegments, transparentSideSegments,
					transparentUpSegments, waterSegments);
			}
			return new VisibleColumnSegments(opaqueSegments, transparentSideSegments + transparentUpSegments, waterSegments);
		}
	}

	private static int emittedSegmentCount(List<LodBufferSnapshot> buffers) {
		int count = 0;
		for (LodBufferSnapshot buffer : buffers) {
			if (!buffer.vertices().isEmpty()) count++;
		}
		return count;
	}

	/**
	 * Consumes the visible DH instances and their copied render-frame decision as
	 * one semantic transaction. The two legacy accessors remain for tests and
	 * compatibility, but the gameplay frame builder must use this paired path:
	 * otherwise a route reset between calls can combine visible instances from
	 * one generation with disabled flags from the next one.
	 */
	/** A consumed frame's DH semantics and the collector lifecycle they belong to. */
	public record ConsumedVisibleFrame(
		List<VulkanicGalBridge.WorldLodColumnInstanceRecord> visibleSegments,
		VulkanicGalBridge.WorldLodRenderFrameRecord renderFrame,
		long lifecycle
	) {}

	/** Completed-frame DH receipts dropped because their lifecycle had ended. */
	public static long staleRouteExecutionReceipts() {
		synchronized (LOCK) {
			return DhCollectorLedger.query(DhCollectorLedger.QUERY_STALE_RECEIPTS, 0L);
		}
	}

	public static ConsumedVisibleFrame consumeVisibleFrame() {
		if (!enabled()) {
			synchronized (LOCK) {
				return new ConsumedVisibleFrame(List.of(), VulkanicGalBridge.WorldLodRenderFrameRecord.disabled(),
					DhCollectorLedger.query(DhCollectorLedger.QUERY_LIFECYCLE, 0L));
			}
		}
		synchronized (LOCK) {
			// The ledger hands over the selected segments with the frame they were
			// prepared for, clears both and tracks visible-set stability.
			long[] header = new long[4];
			List<VulkanicGalBridge.WorldLodColumnInstanceRecord> result =
				records(DhCollectorLedger.consume(DhCollectorLedger.CONSUME_VISIBLE_FRAME, header));
			VulkanicGalBridge.WorldLodRenderFrameRecord renderFrame = frameRecord(header[0] != 0L, (int)header[1]);
			if (!result.isEmpty()) {
				RustGalTerrainRenderer.ensureTerrainAtlasAssetForWorldMesh();
			}
			LAST_CONSUMED_VISIBLE_SEGMENTS = result;
			LAST_CONSUMED_VISIBLE_SEGMENT_SNAPSHOTS = executionSnapshotsEnabled()
				? snapshotExecutedSegmentsLocked(result)
				: List.of();
			PENDING_RENDER_FRAME = VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
			ConsumedVisibleFrame consumed = new ConsumedVisibleFrame(result, renderFrame, header[2]);
			writeSemanticPayloadReceiptLocked(header[3], result);
			return consumed;
		}
	}

	private static long fnvUpdate(long hash, long value) {
		long result = hash;
		for (int shift = 0; shift < Long.SIZE; shift += 8) {
			result = (result ^ ((value >>> shift) & 0xffL)) * 0x100000001b3L;
		}
		return result;
	}

	/**
	 * Writes a bounded, capture-only hash of the exact packed DH source stream
	 * paired with the visible segment list.  Rust writes the same hash from its
	 * decoded semantic asset, allowing a capture to distinguish a producer/FFI
	 * mismatch from a later raster or composition problem.  The receipt is never
	 * read by rendering code and is disabled outside graphics-audit captures.
	 */
	private static void writeSemanticPayloadReceiptLocked(
		long frame,
		List<VulkanicGalBridge.WorldLodColumnInstanceRecord> visible
	) {
		if (!graphicsAuditEnabled() || visible.isEmpty()) {
			return;
		}
		String diagnosticDir = System.getenv("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR");
		if (diagnosticDir == null || diagnosticDir.isBlank()) {
			return;
		}
		StringBuilder json = new StringBuilder("{\"schema\":\"mattmc-world-lod-source-semantic-v1\",\"frame\":")
			.append(frame).append(",\"segments\":[");
		boolean first = true;
		for (VulkanicGalBridge.WorldLodColumnInstanceRecord instance : visible) {
			LodColumnSnapshot column = publishedColumnLocked(instance.columnKey());
			if (column == null || column.generation() != instance.columnGeneration()) {
				continue;
			}
			LodBufferSnapshot segment = semanticSegmentForInstance(column, instance);
			if (segment == null) {
				continue;
			}
			if (!first) json.append(',');
			first = false;
			json.append("{\"columnKey\":").append(instance.columnKey())
				.append(",\"columnGeneration\":").append(instance.columnGeneration())
				.append(",\"layer\":").append(instance.layer())
				.append(",\"segment\":").append(instance.segmentIndex())
				.append(",\"vertices\":").append(segment.vertices().size())
				.append(",\"semanticHash\":\"")
				.append(Long.toUnsignedString(cachedSemanticPayloadHash(
					instance.columnKey(), instance.columnGeneration(), instance.segmentIndex(), segment), 16))
				.append("\"}");
		}
		json.append("]}");
		Path directory = Path.of(diagnosticDir);
		Path target = directory.resolve("world-lod-source-semantic-frame-" + frame + ".json");
		Path temporary = directory.resolve(target.getFileName() + ".tmp");
		try {
			Files.createDirectories(directory);
			Files.writeString(temporary, json, StandardCharsets.UTF_8);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (java.io.IOException atomicMoveFailure) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (java.io.IOException ignored) {
			// Diagnostics must never make the render route fail.
		}
	}

	private static LodBufferSnapshot semanticSegmentForInstance(
		LodColumnSnapshot column,
		VulkanicGalBridge.WorldLodColumnInstanceRecord instance
	) {
		List<LodBufferSnapshot> buffers = switch (instance.layer()) {
			case 1 -> column.opaque();
			case 2 -> column.transparentSide();
			case 3 -> column.transparentUp();
			case 4 -> column.transparentWaterUp();
			default -> List.of();
		};
		int offset = switch (instance.layer()) {
			case 1 -> 0;
			case 2 -> emittedSegmentCount(column.opaque());
			case 3 -> emittedSegmentCount(column.opaque()) + emittedSegmentCount(column.transparentSide());
			case 4 -> emittedSegmentCount(column.opaque()) + emittedSegmentCount(column.transparentSide())
				+ emittedSegmentCount(column.transparentUp());
			default -> Integer.MAX_VALUE;
		};
		int compactIndex = instance.segmentIndex() - offset;
		if (compactIndex < 0) return null;
		for (LodBufferSnapshot buffer : buffers) {
			if (buffer.vertices().isEmpty()) continue;
			if (compactIndex-- == 0) return buffer;
		}
		return null;
	}

	private record SemanticHashKey(long columnKey, long columnGeneration, int segmentIndex) {}

	/** Segment payloads are immutable per column generation; hash each once. */
	private static final Map<SemanticHashKey, Long> SEMANTIC_PAYLOAD_HASHES =
		new LinkedHashMap<>(256, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<SemanticHashKey, Long> eldest) {
				return size() > 8192;
			}
		};

	private static long cachedSemanticPayloadHash(
		long columnKey, long columnGeneration, int segmentIndex, LodBufferSnapshot segment
	) {
		synchronized (SEMANTIC_PAYLOAD_HASHES) {
			return SEMANTIC_PAYLOAD_HASHES.computeIfAbsent(
				new SemanticHashKey(columnKey, columnGeneration, segmentIndex),
				key -> semanticPayloadHash(segment)
			);
		}
	}

	private static long semanticPayloadHash(LodBufferSnapshot segment) {
		long hash = 0xcbf29ce484222325L;
		ByteBuffer input = ByteBuffer.wrap(segment.packedVerticesForRust()).order(ByteOrder.LITTLE_ENDIAN);
		while (input.remaining() >= VERTEX_STRIDE_BYTES) {
			hash = fnvUpdate(hash, input.getShort());
			hash = fnvUpdate(hash, input.getShort());
			hash = fnvUpdate(hash, input.getShort());
			hash = fnvUpdate(hash, input.getShort());
			for (int i = 0; i < 6; i++) hash = fnvUpdate(hash, input.get());
			hash = fnvUpdate(hash, input.getShort());
		}
		return hash;
	}

	private static long fnvUpdate(long hash, short value) {
		return fnvUpdate(hash, new byte[] {(byte)value, (byte)(value >>> 8)});
	}

	private static long fnvUpdate(long hash, byte value) {
		return (hash ^ (value & 0xffL)) * 0x100000001b3L;
	}

	private static long fnvUpdate(long hash, byte[] values) {
		for (byte value : values) hash = fnvUpdate(hash, value);
		return hash;
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
		synchronized (LOCK) {
			if (!DhCollectorLedger.selectRoute(hasCompleteVisibleExactAtlasCoverage())) {
				return;
			}
			// Capture-only semantic receipt: this proves that the real DH visible
			// list reached Rust route admission, without retaining a Java renderer
			// object or authorizing a fallback draw.
			String selectionIdentity = "selected:rust-material-route";
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
				"distant-horizons-selection", selectionIdentity
			);
			net.minecraft.client.dev.DeterministicCameraCapture.recordSubmittedWorkIdentityForCompletedFrame(
				"distant-horizons-selection", selectionIdentity
			);
		}
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
		synchronized (LOCK) {
			DhCollectorLedger.rejectRoute(reason, opaqueSegments, transparentSegments, waterSegments);
			if (Boolean.getBoolean("mattmc.dev.rustGalDistantHorizons.traceExecution")
				&& rejectionTraceEvents < 240) {
				long now = System.nanoTime();
				if (rejectionTraceEvents == 0 || now - lastRejectionTraceNanos >= 1_000_000_000L) {
					lastRejectionTraceNanos = now;
					rejectionTraceEvents++;
					System.out.println("[MattMC DH] rejected reason=" + reason
						+ " opaque=" + opaqueSegments + " transparent=" + transparentSegments
						+ " water=" + waterSegments + " wall_ms=" + System.currentTimeMillis());
				}
			}
		}
	}

	/** Compatibility entrypoint for early-preflight callers that do not have
	 * the actual visible-layer totals yet. */
	public static void recordRustOpaqueRouteRejected(String reason, int transparentSegments) {
		recordRustNonWaterRouteRejected(reason, 0, Math.max(0, transparentSegments), 0);
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
		// The current lifecycle: this caller's frame cannot predate it.
		recordRustMaterialRouteExecution(worldFrame, submission, captureFrame, instances, opaqueInstances,
			transparentInstances, waterInstances, frameSemanticsEnabled, submittedSegments, Long.MIN_VALUE);
	}

	/**
	 * Records a completed frame's DH execution. {@code lifecycle} is the DH
	 * lifecycle the frame was consumed under ({@link ConsumedVisibleFrame#lifecycle()}):
	 * frames are pipelined, so a frame produced while the route was selected
	 * can complete after a world unload or resource reload cleared the
	 * collector (save and quit, a reload). That receipt describes columns
	 * that no longer exist and is dropped, never recorded against the new
	 * lifecycle. Within one lifecycle the frame's own segments prove the route
	 * was selected when it was consumed, whatever the route state is now.
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
		List<VulkanicGalBridge.WorldLodColumnInstanceRecord> submittedSegments,
		long lifecycle
	) {
		if (worldFrame < 0L || submission <= 0L || captureFrame < 0L || instances <= 0
			|| opaqueInstances < 0 || transparentInstances < 0 || waterInstances < 0
			|| opaqueInstances + transparentInstances + waterInstances != instances || !frameSemanticsEnabled) {
			throw new IllegalArgumentException("invalid successful Rust DH material-route execution correlation");
		}
		synchronized (LOCK) {
			// The ledger drops a receipt from an ended lifecycle and records the rest.
			long routeFrame = DhCollectorLedger.recordExecution(lifecycle, worldFrame, submission, captureFrame, instances,
				opaqueInstances, transparentInstances, waterInstances);
			if (routeFrame < 0L) {
				return;
			}
			if (Boolean.getBoolean("mattmc.dev.rustGalDistantHorizons.traceExecution")
				&& executionTraceEvents < 240) {
				long now = System.nanoTime();
				if (executionTraceEvents == 0 || now - lastExecutionTraceNanos >= 1_000_000_000L) {
					lastExecutionTraceNanos = now;
					executionTraceEvents++;
					// Successful native submission carrying these immutable DH segments.
					// No geometry snapshots, GPU state or alternate execution path.
					System.out.println("[MattMC DH] submitted world_frame=" + worldFrame
						+ " submission=" + submission + " instances=" + instances
						+ " opaque=" + opaqueInstances + " transparent=" + transparentInstances
						+ " water=" + waterInstances + " wall_ms=" + System.currentTimeMillis());
				}
			}
			if (exactAtlasCoverageRequested()) {
				long signature = 17L;
				signature = 31L * signature + routeExactAtlasOutputKnownQuads;
				signature = 31L * signature + routeExactAtlasOutputMixedQuads;
				signature = 31L * signature + routeExactAtlasOutputUnavailableQuads;
				signature = 31L * signature + routeExactAtlasInvalidIdentityQuads;
				signature = 31L * signature + routeExactAtlasIdentityTableEntries;
				signature = 31L * signature + instances;
				if (signature == exactAtlasCoverageStableSignature
					&& routeFrame == exactAtlasCoverageStableFrame + 1L) {
					exactAtlasCoverageStableFrames = Math.min(3, exactAtlasCoverageStableFrames + 1);
				} else {
					exactAtlasCoverageStableFrames = 1;
				}
				exactAtlasCoverageStableSignature = signature;
				exactAtlasCoverageStableFrame = routeFrame;
			}
			String executionIdentity = "executed:rust-material-route";
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
				"distant-horizons", executionIdentity
			);
			net.minecraft.client.dev.DeterministicCameraCapture.recordSubmittedWorkIdentityForCompletedFrame(
				"distant-horizons", executionIdentity
			);
			List<VulkanicGalBridge.WorldLodColumnInstanceRecord> executedSegments = submittedSegments == null
				? LAST_CONSUMED_VISIBLE_SEGMENTS
				: List.copyOf(submittedSegments);
			if (executionSnapshotsEnabled() && executedSegments.size() == instances) {
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
		synchronized (LOCK) {
			DhCollectorLedger.observeRenderList(visibleColumns);
		}
	}

	/** Bounded visibility evidence from DH's quadtree traversal. */
	/**
	 * One frame of DH's semantic render list: the near-to-far column keys, the
	 * candidates still waiting for publication, and (when {@code admitted})
	 * the segments admitted for the frame being prepared. Publication requests
	 * that failed were skipped, as the per-column walk skipped them after
	 * logging; {@code requestFailure} describes the first.
	 */
	public record VisibleFrame(
		long[] nearToFar,
		int unpublished,
		boolean admitted,
		VisibleColumnSegments segments,
		int requestFailures,
		String requestFailure
	) {
		public static final VisibleFrame EMPTY = new VisibleFrame(new long[0], 0, false, VisibleColumnSegments.EMPTY, 0, null);
	}

	/**
	 * DH's per-frame render list in one ledger call. {@code walked} holds the
	 * first {@code count} drawable containers the quadtree walk reached, as
	 * (key, generation) pairs. In walk order this drops containers whose
	 * generation is no longer current ({@link #hasColumn}) and requests
	 * publication of each unpublished column; it then sorts the candidates near
	 * to far around the quadtree's center, records them as this frame's
	 * visibility, and admits their visible segments. Exact-atlas coverage needs
	 * Java's per-column accounting, so then the caller admits each column
	 * through {@link #recordVisibleMaterialColumn}.
	 */
	public static VisibleFrame collectVisibleFrame(long[] walked, int count, int centerX, int centerZ) {
		boolean enabled = enabled();
		boolean admit = enabled && !exactAtlasCoverageRequested();
		int flags = (enabled ? DhCollectorLedger.VISIBLE_FRAME_ENABLED : 0) | (admit ? DhCollectorLedger.VISIBLE_FRAME_ADMIT : 0);
		long[] frame;
		synchronized (LOCK) {
			frame = DhCollectorLedger.visibleFrame(walked, count, centerX, centerZ, flags);
		}
		String requestFailure = null;
		if (frame[1] > 0L) {
			try {
				DhCollectorLedger.check((int)frame[3]);
			} catch (RuntimeException error) {
				requestFailure = "column " + frame[2] + ": " + error.getMessage();
			}
		}
		VisibleColumnSegments segments = new VisibleColumnSegments(
			(int)frame[4], (int)(frame[5] + frame[6]), (int)frame[7]);
		return new VisibleFrame(Arrays.copyOfRange(frame, 9, frame.length), (int)frame[0], admit, segments,
			(int)frame[1], requestFailure);
	}

	public static void recordRenderListVisibilityStats(
		int candidates,
		int unpublished,
		List<Long> candidateColumnKeys
	) {
		if (!enabled() || candidates < 0 || unpublished < 0 || unpublished > candidates
			|| candidateColumnKeys == null || candidateColumnKeys.size() != candidates
			|| candidates > MAX_PENDING_VISIBLE_COLUMN_KEYS) return;
		synchronized (LOCK) {
			DhCollectorLedger.recordVisibility(candidates, unpublished, candidateColumnKeys);
		}
	}

	/** Real visible columns rebuilt after the latest accepted Rust asset update
	 * must wait for publication instead of referencing an older Rust payload. */
	public static boolean hasUnpublishedVisibleColumns() {
		synchronized (LOCK) {
			return DhCollectorLedger.query(DhCollectorLedger.QUERY_UNPUBLISHED_VISIBLE, 0L) > 0L;
		}
	}

	/**
	 * Capture-only readiness signal. A visible DH payload and its selected
	 * segment set must remain unchanged for the requested number of route frames
	 * before a screenshot can claim a stable semantic generation. This never
	 * selects or rejects the normal gameplay route; it prevents a deterministic
	 * capture from sampling a frame while DH is replacing or reselecting visible
	 * columns underneath it.
	 */
	public static boolean visiblePayloadStableForCapture(int requiredFrames) {
		if (requiredFrames <= 0) return true;
		synchronized (LOCK) {
			return DhCollectorLedger.query(DhCollectorLedger.QUERY_PAYLOAD_STABLE, requiredFrames) != 0L;
		}
	}

	/**
	 * Reports whether every copied visible quad can receive an exact atlas
	 * overlay. Rust admission does not require this to be complete: its
	 * explicit reduced-color stream remains authoritative for unresolved quads,
	 * while exact atlas records are overlaid only for proven identities.
	 */
	public static boolean hasCompleteVisibleExactAtlasCoverage() {
		synchronized (LOCK) {
			return exactAtlasCoverageRequested()
				&& DhCollectorLedger.query(DhCollectorLedger.QUERY_PENDING_SEGMENTS, 0L) > 0L
				&& routeExactAtlasOutputMixedQuads == 0
				&& routeExactAtlasOutputUnavailableQuads == 0
				&& routeExactAtlasInvalidIdentityQuads == 0;
		}
	}

	/**
	 * Returns true only after the exact-atlas accounting has described the same
	 * visible Rust-owned DH set for several consecutive route frames.  A single
	 * successful submission is not sufficient: DH may publish replacement
	 * generations immediately afterward, invalidating the screenshot's plan.
	 */
	public static boolean exactAtlasCoverageStableForCapture() {
		synchronized (LOCK) {
			return exactAtlasCoverageStableFrames >= 3
				&& routeExactAtlasOutputKnownQuads > 0
				&& routeExactAtlasOutputMixedQuads == 0;
		}
	}

	/** Bounded route evidence for deterministic captures and regression tests. */
	public static int routeExecutionCount() {
		synchronized (LOCK) {
			return (int)DhCollectorLedger.query(DhCollectorLedger.QUERY_EXECUTIONS, 0L);
		}
	}

	public static RouteDiagnostics routeDiagnosticsSnapshot() {
		synchronized (LOCK) {
			// Ledger numbers in mattmc_dh_collector_diagnostics order; the
			// exact-atlas evidence stays with these Java diagnostics.
			DhCollectorLedger.Diagnostics ledger = DhCollectorLedger.diagnostics();
			long[] n = ledger.numbers();
			String[] s = ledger.text();
			return new RouteDiagnostics(
				n[0],
				s[0],
				s[1],
				s[2],
				s[3],
				Float.intBitsToFloat((int)n[1]),
				(int)n[2],
				routeExactAtlasIdentitySegments,
				routeExactAtlasIdentityQuads,
				routeExactAtlasPartialSegments,
				routeExactAtlasPartialQuads,
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
				(int)n[3],
				(int)n[4],
				(int)n[5],
				(int)n[6],
				(int)n[7],
				(int)n[8],
				(int)n[9],
				n[10],
				n[11],
				n[12],
				n[13],
				s[4],
				n[14],
				n[15],
				n[16],
				s[5],
				(int)n[17],
				(int)n[18],
				(int)n[19],
				(int)n[20],
				(int)n[21],
				(int)n[22],
				(int)n[23],
				n[24],
				n[25],
				List.copyOf(routeExactAtlasCoverageSamples),
				exactAtlasResolutionStatusSummary(),
				exactAtlasResolutionSamplesSnapshot(),
				n[26],
				(int)n[27],
				n[28] != 0L,
				n[29] != 0L,
				n[30],
				n[31],
				n[32],
				n[33],
				(int)n[34],
				(int)n[35],
				(int)n[36],
				(int)n[37],
				n[38] != 0L
			);
		}
	}

	/**
	 * Retires all copied DH assets at a resource-pack boundary.  DH columns
	 * carry face-material provenance resolved against the current block atlas;
	 * keeping them live across a reload would let a stale atlas identity reach
	 * Rust after the resource generation changed.  The native retirement is
	 * deliberately delivered through the normal pending-update path, just as
	 * it is for a world unload.
	 */
	public static void invalidateForResourceReload() {
		clearWithReason("resource-reload");
	}

	public static void clear() {
		clearWithReason("world-unload");
	}

	private static void clearWithReason(String reason) {
		synchronized (LOCK) {
			// The ledger retires every published generation through the normal
			// pending-update path and keeps the lifecycle receipts.
			applyEffectsLocked(DhCollectorLedger.clear(reason), null, Map.of());
			DistantHorizonsFaceMaterialResolver.clearCachedStateResolutions();
			EXACT_ATLAS_COVERAGE_CACHE.clear();
			BRIDGE_FACE_MATERIAL_CACHE.clear();
			BRIDGE_VARIANT_FACE_MATERIAL_CACHE.clear();
			LAST_CONSUMED_VISIBLE_SEGMENTS = List.of();
			LAST_CONSUMED_VISIBLE_SEGMENT_SNAPSHOTS = List.of();
			EXECUTED_VISIBLE_SEGMENTS_BY_WORLD_FRAME.clear();
			PENDING_RENDER_FRAME = VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
			resetExactAtlasIdentityCoverage();
			routeExactAtlasResolutionStatusCounts.clear();
			routeExactAtlasResolutionSamples.clear();
			routeExactAtlasResolutionIdentityCounts.clear();
			exactAtlasCoverageStableSignature = 0L;
			exactAtlasCoverageStableFrame = Long.MIN_VALUE;
			exactAtlasCoverageStableFrames = 0;
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
			frame.cameraWorldPosition(), frame.dhFogParameters(), frame.maxLevelHeight(), frame.ssaoParameters()
		);
	}

	/** Immutable DH fog configuration copied at the Java semantic boundary. */
	private static float[] dhFogParameters() {
		var direction = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogDirection.get();
		var mix = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogMixMode.get();
		float[] values = new float[20];
		values[0] = Config.Client.Advanced.Graphics.Fog.farFogStart.get().floatValue();
		values[1] = Config.Client.Advanced.Graphics.Fog.farFogEnd.get().floatValue();
		values[2] = Config.Client.Advanced.Graphics.Fog.farFogMin.get().floatValue();
		values[3] = Config.Client.Advanced.Graphics.Fog.farFogMax.get().floatValue();
		values[4] = Config.Client.Advanced.Graphics.Fog.farFogDensity.get().floatValue();
		values[5] = Math.max(1.0F, Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get() * 16.0F);
		values[6] = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogBaseHeight.get().floatValue();
		values[7] = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogStart.get().floatValue();
		values[8] = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogEnd.get().floatValue();
		values[9] = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogMin.get().floatValue();
		values[10] = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogMax.get().floatValue();
		values[11] = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogDensity.get().floatValue();
		values[12] = Config.Client.Advanced.Graphics.Fog.farFogFalloff.get().value;
		values[13] = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogFalloff.get().value;
		values[14] = mix.value;
		values[15] = (direction.basedOnCamera ? 1.0F : 0.0F)
			+ (direction.fogAppliesUp ? 2.0F : 0.0F)
			+ (direction.fogAppliesDown ? 4.0F : 0.0F);
		values[16] = Config.Client.Advanced.Graphics.Fog.enableDhFog.get() ? 1.0F : 0.0F;
		values[17] = (mix != com.seibel.distanthorizons.api.enums.rendering.EDhApiHeightFogMixMode.SPHERICAL
			&& mix != com.seibel.distanthorizons.api.enums.rendering.EDhApiHeightFogMixMode.CYLINDRICAL) ? 1.0F : 0.0F;
		values[18] = mix == com.seibel.distanthorizons.api.enums.rendering.EDhApiHeightFogMixMode.SPHERICAL ? 1.0F : 0.0F;
		values[19] = 1.0F / 384.0F;
		return values;
	}

	/** Immutable DH SSAO configuration copied with the frame semantics. */
	private static float[] ssaoParameters() {
		boolean enabled = Config.Client.Advanced.Graphics.Ssao.enableSsao.get();
		float sampleCount = Math.max(1.0F, Math.min(64.0F,
			Config.Client.Advanced.Graphics.Ssao.sampleCount.get().floatValue()));
		float radius = Math.max(0.0F, Config.Client.Advanced.Graphics.Ssao.radius.get().floatValue());
		float strength = Math.max(0.0F, Config.Client.Advanced.Graphics.Ssao.strength.get().floatValue());
		float minLight = Math.max(0.0F, Math.min(1.0F,
			Config.Client.Advanced.Graphics.Ssao.minLight.get().floatValue()));
		float bias = Math.max(0.0F, Config.Client.Advanced.Graphics.Ssao.bias.get().floatValue());
		float fadeDistance = Math.max(0.0F,
			Config.Client.Advanced.Graphics.Ssao.fadeDistanceInBlocks.get().floatValue());
		float blurRadius = Math.max(0.0F, Math.min(3.0F,
			Config.Client.Advanced.Graphics.Ssao.blurRadius.get().floatValue()));
		return new float[] {
			enabled ? 1.0F : 0.0F, sampleCount, radius, strength,
			minLight, bias, fadeDistance, blurRadius
		};
	}

	/** A copy of the retained published payload, when it is the ledger's published generation. */
	private static LodColumnSnapshot publishedColumnLocked(long columnKey) {
		long generation = DhCollectorLedger.query(DhCollectorLedger.QUERY_PUBLISHED_PAYLOAD_GENERATION, columnKey);
		return generation == 0L ? null
			: payloadCopyLocked(PUBLISHED_COPIES, columnKey, generation, DhCollectorLedger.PAYLOAD_PUBLISHED);
	}

	/** A copy of the current payload, without counting as a column access. */
	private static LodColumnSnapshot currentColumnLocked(long columnKey) {
		long generation = DhCollectorLedger.query(DhCollectorLedger.QUERY_CURRENT_GENERATION, columnKey);
		return generation == 0L ? null
			: payloadCopyLocked(CURRENT_COPIES, columnKey, generation, DhCollectorLedger.PAYLOAD_CURRENT);
	}

	private static LodColumnSnapshot payloadCopyLocked(
		Map<Long, LodColumnSnapshot> copies, long columnKey, long generation, int which
	) {
		LodColumnSnapshot copy = copies.get(columnKey);
		if (copy != null && copy.generation() == generation) return copy;
		DhCollectorLedger.PayloadCopy payload = DhCollectorLedger.payload(columnKey, which);
		if (payload == null) {
			copies.remove(columnKey);
			return null;
		}
		long[] header = payload.header();
		List<List<LodBufferSnapshot>> layers = new ArrayList<>(4);
		int segment = 8;
		int offset = 0;
		for (int layer = 0; layer < 4; layer++) {
			List<LodBufferSnapshot> buffers = new ArrayList<>((int)header[4 + layer]);
			for (int index = 0; index < header[4 + layer]; index++, segment += 2) {
				int length = (int)header[segment + 1];
				buffers.add(LodBufferSnapshot.fromPacked((int)header[segment],
					Arrays.copyOfRange(payload.bytes(), offset, offset + length)));
				offset += length;
			}
			layers.add(buffers);
		}
		copy = new LodColumnSnapshot(columnKey, header[0], (int)header[1], (int)header[2], (int)header[3],
			layers.get(0), layers.get(1), layers.get(2), layers.get(3));
		copies.put(columnKey, copy);
		return copy;
	}

	private static LodMaterialProvenanceSnapshot publishedMaterialProvenanceLocked(long columnKey) {
		return PUBLISHED_MATERIAL_PROVENANCE.get(columnKey);
	}

	/**
	 * Flushes immutable CPU LOD assets only while the caller has already
	 * selected the Rust Vulkan whole-frame route. This performs no rendering;
	 * the Rust world frontend consumes the resulting generation after explicit
	 * asset admission and confirmation.
	 */
	public static VulkanicGalBridge.Status flushPendingAssets(VulkanicGalBridge bridge) {
		if (!enabled() || bridge == null) {
			return null;
		}
		boolean profileUpdate = Boolean.getBoolean("mattmc.dev.graphicsFrameBenchmark.dhChurnCounters");
		long[] outcome = new long[6];
		long flushStarted = System.nanoTime();
		VulkanicGalBridge.Status status = bridge.flushWorldLodCollector(ledgerConfig(), outcome);
		if (outcome[0] == 3L) {
			DhCollectorLedger.check((int)outcome[1]);
		}
		if (outcome[0] == 2L) {
			return flushPendingAssetsFromJava(bridge, profileUpdate);
		}
		if (profileUpdate) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordPhaseSample(
				"world.distant-horizons.asset-select", outcome[0] == 1L ? outcome[4] : System.nanoTime() - flushStarted);
		}
		if (outcome[0] != 1L) {
			return null;
		}
		long[] update = DhCollectorLedger.takeOutput(3 + 2 * (int)(outcome[1] + outcome[3]));
		int assetsAt = 3;
		int retirementsAt = assetsAt + (int)update[1] * 2;
		long[] assets = Arrays.copyOfRange(update, assetsAt, retirementsAt);
		long[] retirements = Arrays.copyOfRange(update, retirementsAt, update.length);
		if (profileUpdate) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
				"world.distant-horizons.asset-update-columns", outcome[1]);
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
				"world.distant-horizons.asset-update-bytes", outcome[2]);
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordPhaseSample(
				"world.distant-horizons.asset-native-update", outcome[5]);
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordPhaseSample(
				"world.distant-horizons.asset-bridge", outcome[5]);
		}
		if (System.getenv("MATTMC_TRACE_WHOLE_FRAME") != null) {
			System.err.println("whole-frame.dh-assets.begin generation=" + update[0]
				+ " columns=" + outcome[1]
				+ " snapshots_bytes=" + outcome[2]
				+ " retirements=" + outcome[3]);
			System.err.println("whole-frame.dh-assets.end generation=" + update[0]
				+ " elapsed_nanos=" + outcome[5]);
		}
		long acknowledgeStarted = profileUpdate ? System.nanoTime() : 0L;
		try {
			acknowledge(assets, retirements, Map.of());
		} catch (RuntimeException error) {
			synchronized (LOCK) {
				DhCollectorLedger.releaseInFlight(assets);
			}
			throw error;
		}
		if (profileUpdate) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordPhaseSample(
				"world.distant-horizons.asset-acknowledge", System.nanoTime() - acknowledgeStarted);
		}
		return status;
	}

	/** Publishes an update carrying material provenance, which needs Java's
	 * model resolution, through the Java-packed bridge call. */
	private static VulkanicGalBridge.Status flushPendingAssetsFromJava(VulkanicGalBridge bridge, boolean profileUpdate) {
		PendingAssetUpdate update = pendingUpdate(true);
		if (update == null) {
			return null;
		}
		try {
			long updateStarted = System.nanoTime();
			if (profileUpdate) {
				net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
					"world.distant-horizons.asset-update-columns", update.assets().size());
				long snapshotBytes = 0L;
				for (LodColumnSnapshot snapshot : update.snapshots()) {
					snapshotBytes += snapshot.byteSize();
				}
				net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
					"world.distant-horizons.asset-update-bytes", snapshotBytes);
			}
			if (System.getenv("MATTMC_TRACE_WHOLE_FRAME") != null) {
				long snapshotBytes = update.snapshots().stream()
					.mapToLong(LodColumnSnapshot::byteSize)
					.sum();
				System.err.println("whole-frame.dh-assets.begin generation=" + update.generation()
					+ " columns=" + update.assets().size()
					+ " snapshots_bytes=" + snapshotBytes
					+ " retirements=" + update.retirements().size());
			}
			VulkanicGalBridge.Status status = bridge.updateWorldLodAssets(
				update.generation(), update.assets(), update.retirements(), update.materialProvenance()
			);
			long bridgeEnded = profileUpdate ? System.nanoTime() : 0L;
			if (profileUpdate) {
				net.minecraft.client.dev.GraphicsFrameBenchmark.recordPhaseSample(
					"world.distant-horizons.asset-bridge", bridgeEnded - updateStarted);
			}
			if (System.getenv("MATTMC_TRACE_WHOLE_FRAME") != null) {
				System.err.println("whole-frame.dh-assets.end generation=" + update.generation()
					+ " elapsed_nanos=" + (System.nanoTime() - updateStarted));
			}
			acknowledge(update);
			if (profileUpdate) {
				net.minecraft.client.dev.GraphicsFrameBenchmark.recordPhaseSample(
					"world.distant-horizons.asset-acknowledge", System.nanoTime() - bridgeEnded);
			}
			return status;
		} catch (RuntimeException error) {
			releaseInFlightAssets(update);
			throw error;
		}
	}

	private static PendingAssetUpdate pendingUpdate() {
		return pendingUpdate(false);
	}

	/**
	 * The ledger selects the bounded update (visible demand first, never a
	 * column selected for the frame being prepared or already in flight) and
	 * marks it in flight; Java builds the copied records from copies of its
	 * payloads.
	 */
	private static PendingAssetUpdate pendingUpdate(boolean visibleCandidatesOnly) {
		synchronized (LOCK) {
			long[] selected = DhCollectorLedger.pendingUpdate(ledgerConfig(), visibleCandidatesOnly);
			if (selected.length == 0) {
				return null;
			}
			int assetCount = (int)selected[1];
			int retirementCount = (int)selected[2];
			List<LodColumnSnapshot> selectedSnapshots = new ArrayList<>(assetCount);
			List<VulkanicGalBridge.WorldLodColumnAssetRecord> assets = new ArrayList<>(assetCount);
			List<VulkanicGalBridge.WorldLodColumnMaterialProvenanceRecord> materialProvenance = new ArrayList<>(assetCount);
			Map<Long, LodMaterialProvenanceSnapshot> selectedProvenance = new LinkedHashMap<>();
			for (int index = 0; index < assetCount; index++) {
				long columnKey = selected[3 + index * 2];
				long generation = selected[4 + index * 2];
				LodColumnSnapshot snapshot = currentColumnLocked(columnKey);
				if (snapshot == null || snapshot.generation() != generation) {
					throw new IllegalStateException("Distant Horizons ledger selected column " + columnKey
						+ " generation " + generation + " without its copied payload");
				}
				selectedSnapshots.add(snapshot);
				assets.add(snapshot.toBridgeRecord());
				LodMaterialProvenanceSnapshot provenance = MATERIAL_PROVENANCE.get(columnKey);
				if (provenance != null) {
					materialProvenance.add(snapshot.toBridgeMaterialProvenance(provenance));
					selectedProvenance.put(columnKey, provenance);
				}
			}
			List<VulkanicGalBridge.WorldLodColumnRetirementRecord> retirements = new ArrayList<>(retirementCount);
			int retirementsAt = 3 + assetCount * 2;
			for (int index = 0; index < retirementCount; index++) {
				retirements.add(new VulkanicGalBridge.WorldLodColumnRetirementRecord(
					selected[retirementsAt + index * 2], selected[retirementsAt + index * 2 + 1]
				));
			}
			return new PendingAssetUpdate(
				selected[0], List.copyOf(selectedSnapshots), List.copyOf(assets), List.copyOf(retirements),
				List.copyOf(materialProvenance), Map.copyOf(selectedProvenance)
			);
		}
	}

	private static void acknowledge(PendingAssetUpdate update) {
		long[] assets = new long[update.assets().size() * 2];
		for (int index = 0; index < update.assets().size(); index++) {
			assets[index * 2] = update.assets().get(index).columnKey();
			assets[index * 2 + 1] = update.assets().get(index).columnGeneration();
		}
		long[] retirements = new long[update.retirements().size() * 2];
		for (int index = 0; index < update.retirements().size(); index++) {
			retirements[index * 2] = update.retirements().get(index).columnKey();
			retirements[index * 2 + 1] = update.retirements().get(index).columnGeneration();
		}
		acknowledge(assets, retirements, update.materialProvenanceByColumn());
	}

	/** {@code assets} and {@code retirements}: (key, generation) each. */
	private static void acknowledge(long[] assets, long[] retirements, Map<Long, LodMaterialProvenanceSnapshot> provenance) {
		synchronized (LOCK) {
			applyEffectsLocked(DhCollectorLedger.acknowledge(ledgerConfig(), assets, retirements), null, provenance);
		}
	}

	private static void releaseInFlightAssets(PendingAssetUpdate update) {
		synchronized (LOCK) {
			long[] assets = new long[update.assets().size() * 2];
			for (int index = 0; index < update.assets().size(); index++) {
				assets[index * 2] = update.assets().get(index).columnKey();
				assets[index * 2 + 1] = update.assets().get(index).columnGeneration();
			}
			DhCollectorLedger.releaseInFlight(assets);
		}
	}

	private static void resetExactAtlasIdentityCoverage() {
		routeExactAtlasIdentitySegments = 0;
		routeExactAtlasIdentityQuads = 0;
		routeExactAtlasPartialSegments = 0;
		routeExactAtlasPartialQuads = 0;
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
		String identityKey = status.name() + "\u0000" + blockStateIdentity;
		if (routeExactAtlasResolutionIdentityCounts.containsKey(identityKey)
			|| routeExactAtlasResolutionIdentityCounts.size() < MAX_ROUTE_EXACT_ATLAS_IDENTITY_COUNTS) {
			routeExactAtlasResolutionIdentityCounts.merge(identityKey, 1, Integer::sum);
		}
		boolean statusAlreadySampled = routeExactAtlasResolutionSamples.stream()
			.anyMatch(sample -> sample.startsWith("status=" + status + ",")
				|| sample.contains(",status=" + status + ","));
		if (routeExactAtlasResolutionSamples.size() < 24 && !statusAlreadySampled) {
			String firstFace = resolution.faceLayers().isEmpty()
				? ""
				: ",atlas=" + resolution.faceLayers().values().iterator().next().getFirst().atlasIdentity()
					+ ",sprite=" + resolution.faceLayers().values().iterator().next().getFirst().spriteIdentity();
			routeExactAtlasResolutionSamples.add(
				"identity=" + blockStateIdentity + ",status=" + status + ",faces=" + resolution.faceLayers().size() + firstFace
			);
		}
	}

	private static List<String> exactAtlasResolutionSamplesSnapshot() {
		List<String> samples = new ArrayList<>(routeExactAtlasResolutionSamples);
		int remaining = 24 - samples.size();
		if (remaining <= 0 || routeExactAtlasResolutionIdentityCounts.isEmpty()) {
			return List.copyOf(samples);
		}
		routeExactAtlasResolutionIdentityCounts.entrySet().stream()
			.sorted(java.util.Comparator.<Map.Entry<String, Integer>>comparingInt(entry ->
				entry.getKey().startsWith(DistantHorizonsFaceMaterialResolver.Status.COMPLETE.name() + "\u0000") ? 1 : 0)
				.thenComparing(Map.Entry.<String, Integer>comparingByValue().reversed())
				.thenComparing(Map.Entry.comparingByKey()))
			.limit(remaining)
			.forEach(entry -> {
				int separator = entry.getKey().indexOf('\u0000');
				String status = separator < 0 ? "UNKNOWN" : entry.getKey().substring(0, separator);
				String identity = separator < 0 ? entry.getKey() : entry.getKey().substring(separator + 1);
				samples.add("identity=" + identity + ",status=" + status + ",count=" + entry.getValue());
			});
		return List.copyOf(samples);
	}

	private static String exactAtlasResolutionStatusSummary() {
		if (routeExactAtlasResolutionStatusCounts.isEmpty()) {
			return "none";
		}
		return routeExactAtlasResolutionStatusCounts.entrySet().stream()
			.map(entry -> entry.getKey() + "=" + entry.getValue())
			.collect(java.util.stream.Collectors.joining(","));
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
		float clipDistance,
		int opaqueSegments,
		int exactAtlasIdentitySegments,
		int exactAtlasIdentityQuads,
		int exactAtlasPartialSegments,
		int exactAtlasPartialQuads,
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
		int semanticCandidateColumns,
		int semanticUnpublishedCandidates,
		int unpublishedVisibleColumns,
		long semanticBuildAttempts,
		long semanticColumnsBuilt,
		long semanticColumnsReused,
		long semanticColumnsReplaced,
		String lastPayloadDifference,
		long lifecycleResetCount,
		long resourceReloadResetCount,
		long worldUnloadResetCount,
		String lastLifecycleResetReason,
		int lastLifecyclePublishedRetirements,
		int lastLifecycleInvalidatedInFlight,
		int lastLifecycleRetirementsAcknowledged,
		int lastLifecycleRetirementsSupersededByReplacement,
		int lastLifecycleRetirementsOutstanding,
		int pendingRetirements,
		int invalidatedInFlight,
		long lastLifecycleGenerationFloor,
		long minimumPublishedGeneration,
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
	 * Minecraft model or predict backend execution. Exact-atlas planning admits
	 * only proven quads; unresolved quads remain explicit for the complementary
	 * reduced-color range.
	 */
	private static ExactAtlasIdentityCoverage exactAtlasIdentityCoverage(LodColumnSnapshot column) {
		LodMaterialProvenanceSnapshot provenance = publishedMaterialProvenanceLocked(column.columnKey());
		if (provenance == null) {
			int quads = opaqueQuadCount(column);
			return new ExactAtlasIdentityCoverage(
				0, 0, 0, 0, 0, quads, quads, 0, 0,
				0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, quads
			);
		}
		int completeSegments = 0;
		int completeQuads = 0;
		int partialSegments = 0;
		int partialQuads = 0;
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
			int segmentMixedQuads = 0;
			int segmentUnavailableQuads = 0;
			for (int quad = quadOffset; quad < quadOffset + quadCount; quad++) {
				int materialId = sourceIds[quad];
				if (materialId == ColumnRenderSource.SEMANTIC_MATERIAL_MIXED) {
					mixedQuads++;
					segmentMixedQuads++;
					complete = false;
				} else if (materialId <= ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE) {
					unavailableQuads++;
					segmentUnavailableQuads++;
					invalidIdentityQuads++;
					complete = false;
				}
			}
			sourceQuadOffsets[sourceIndex] += quadCount;
			if (complete) {
				completeSegments++;
				completeQuads += quadCount;
			} else {
				int resolvedQuads = quadCount - segmentMixedQuads - segmentUnavailableQuads;
				if (resolvedQuads > 0) {
					partialSegments++;
					partialQuads += resolvedQuads;
				}
			}
		}
		return new ExactAtlasIdentityCoverage(
			completeSegments, completeQuads, partialSegments, partialQuads, mixedQuads, unavailableQuads,
			0, misalignedProvenanceQuads, invalidIdentityQuads, provenance.semanticMaterials().size(),
			provenance.inputCoverage().known(), provenance.inputCoverage().mixed(), provenance.inputCoverage().unavailable(),
			provenance.inputCoverage().opaqueKnown(), provenance.inputCoverage().opaqueMixed(), provenance.inputCoverage().opaqueUnavailable(),
			provenance.outputCoverage().known(), provenance.outputCoverage().mixed(), provenance.outputCoverage().unavailable(),
			provenance.outputCoverage().opaqueKnown(), provenance.outputCoverage().opaqueMixed(), provenance.outputCoverage().opaqueUnavailable(),
			opaqueQuadCount(column)
		);
	}

	private static ExactAtlasIdentityCoverage exactAtlasIdentityCoverageCached(LodColumnSnapshot column) {
		long columnKey = column.columnKey();
		long generation = column.generation();
		ExactAtlasCoverageCacheEntry cached = EXACT_ATLAS_COVERAGE_CACHE.get(columnKey);
		if (cached != null && cached.generation() == generation) {
			return cached.coverage();
		}
		ExactAtlasIdentityCoverage coverage = exactAtlasIdentityCoverage(column);
		EXACT_ATLAS_COVERAGE_CACHE.put(columnKey, new ExactAtlasCoverageCacheEntry(generation, coverage));
		if (EXACT_ATLAS_COVERAGE_CACHE.size() > MAX_EXACT_ATLAS_COVERAGE_CACHE_ENTRIES) {
			EXACT_ATLAS_COVERAGE_CACHE.remove(EXACT_ATLAS_COVERAGE_CACHE.entrySet().iterator().next().getKey());
		}
		return coverage;
	}

	private static BridgeFaceMaterialResolution bridgeFaceMaterialResolution(String blockStateIdentity) {
		synchronized (LOCK) {
			BridgeFaceMaterialResolution cached = BRIDGE_FACE_MATERIAL_CACHE.get(blockStateIdentity);
			if (cached != null) {
				return cached;
			}
			DistantHorizonsFaceMaterialResolver.Resolution resolution =
				DistantHorizonsFaceMaterialResolver.resolveCurrentClientState(blockStateIdentity);
			BridgeFaceMaterialResolution computed = bridgeFaceMaterialResolution(resolution);
			BRIDGE_FACE_MATERIAL_CACHE.put(blockStateIdentity, computed);
			if (BRIDGE_FACE_MATERIAL_CACHE.size() > MAX_BRIDGE_FACE_MATERIAL_CACHE_ENTRIES) {
				BRIDGE_FACE_MATERIAL_CACHE.remove(BRIDGE_FACE_MATERIAL_CACHE.entrySet().iterator().next().getKey());
			}
			return computed;
		}
	}

	private static BridgeFaceMaterialResolution bridgeVariantFaceMaterialResolution(
		String blockStateIdentity,
		long packedBlockPosition
	) {
		BridgeVariantMaterialKey key = new BridgeVariantMaterialKey(blockStateIdentity, packedBlockPosition);
		synchronized (LOCK) {
			BridgeFaceMaterialResolution cached = BRIDGE_VARIANT_FACE_MATERIAL_CACHE.get(key);
			if (cached != null) {
				return cached;
			}
			DistantHorizonsFaceMaterialResolver.Resolution resolution =
				DistantHorizonsFaceMaterialResolver.resolveCurrentClientState(blockStateIdentity, packedBlockPosition);
			BridgeFaceMaterialResolution computed = bridgeFaceMaterialResolution(resolution);
			BRIDGE_VARIANT_FACE_MATERIAL_CACHE.put(key, computed);
			if (BRIDGE_VARIANT_FACE_MATERIAL_CACHE.size() > MAX_BRIDGE_VARIANT_FACE_MATERIAL_CACHE_ENTRIES) {
				BRIDGE_VARIANT_FACE_MATERIAL_CACHE.remove(BRIDGE_VARIANT_FACE_MATERIAL_CACHE.entrySet().iterator().next().getKey());
			}
			return computed;
		}
	}

	private static BridgeFaceMaterialResolution bridgeFaceMaterialResolution(
		DistantHorizonsFaceMaterialResolver.Resolution resolution
	) {
		List<BridgeFaceMaterialTemplate> templates = new ArrayList<>();
		for (Map.Entry<net.minecraft.core.Direction, List<DistantHorizonsFaceMaterialResolver.FaceMaterial>> entry
			: resolution.faceLayers().entrySet()) {
			for (DistantHorizonsFaceMaterialResolver.FaceMaterial material : entry.getValue()) {
				templates.add(new BridgeFaceMaterialTemplate(
					DistantHorizonsFaceMaterialResolver.faceId(entry.getKey()), material.layer(),
					material.atlasIdentity(), material.spriteIdentity(), material.u0(), material.v0(),
					material.u1(), material.v1(), material.uvCornerOrder(), material.tinted(), material.tintArgb()
				));
			}
		}
		return new BridgeFaceMaterialResolution(resolution, List.copyOf(templates));
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
		int partialSegments,
		int partialQuads,
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

	private record ExactAtlasCoverageCacheEntry(long generation, ExactAtlasIdentityCoverage coverage) {
	}

	private record BridgeVariantMaterialKey(String blockStateIdentity, long packedBlockPosition) {
	}

	private record BridgeFaceMaterialResolution(
		DistantHorizonsFaceMaterialResolver.Resolution resolution,
		List<BridgeFaceMaterialTemplate> templates
	) {
	}

	private record BridgeFaceMaterialTemplate(
		int face,
		int layer,
		String atlasIdentity,
		String spriteIdentity,
		float u0,
		float v0,
		float u1,
		float v1,
		int uvCornerOrder,
		boolean tinted,
		int tintArgb
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

	/** Transfers producer-owned, already bounded packets into the immutable
	 * semantic snapshot. No clone is made: this method is the sole ownership
	 * handoff and the packet record exposes only immutable list structure. */
	private static List<LodBufferSnapshot> ownedPackedBuffers(List<byte[]> buffers) {
		Objects.requireNonNull(buffers, "buffers");
		List<LodBufferSnapshot> snapshots = new ArrayList<>(buffers.size());
		for (int sourceBufferIndex = 0; sourceBufferIndex < buffers.size(); sourceBufferIndex++) {
			byte[] bytes = Objects.requireNonNull(buffers.get(sourceBufferIndex), "packed semantic buffer");
			if (bytes.length == 0 || bytes.length % VERTEX_STRIDE_BYTES != 0
				|| (bytes.length / VERTEX_STRIDE_BYTES) % 4 != 0
				|| bytes.length > MAX_TRANSPORT_VERTICES_PER_SEGMENT * VERTEX_STRIDE_BYTES) {
				throw new IllegalArgumentException("Distant Horizons owned semantic packet is not a bounded quad-aligned vertex stream");
			}
			ByteBuffer packet = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
			for (int vertexOffset = 0; vertexOffset < bytes.length; vertexOffset += VERTEX_STRIDE_BYTES) {
				packet.position(vertexOffset + 14);
				if (Short.toUnsignedInt(packet.getShort()) != 0) {
					throw new IllegalArgumentException("Distant Horizons owned semantic packet has non-zero reserved padding");
				}
			}
			snapshots.add(LodBufferSnapshot.fromPacked(sourceBufferIndex, bytes));
		}
		return List.copyOf(snapshots);
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
		public LodColumnSnapshot {
			if (generation <= 0L) {
				throw new IllegalArgumentException("generation must be positive");
			}
			opaque = immutableBuffers(opaque);
			transparentSide = immutableBuffers(transparentSide);
			transparentUp = immutableBuffers(transparentUp);
			transparentWaterUp = immutableBuffers(transparentWaterUp);
			int segmentCount = opaque.size() + transparentSide.size() + transparentUp.size() + transparentWaterUp.size();
			if (segmentCount > MAX_LOD_SEGMENTS_PER_COLUMN) {
				throw new IllegalArgumentException("Distant Horizons LOD segment count must be <= "
					+ MAX_LOD_SEGMENTS_PER_COLUMN);
			}
		if (segmentCount > 0) {
				validateBuffers(opaque);
				validateBuffers(transparentSide);
				validateBuffers(transparentUp);
				validateBuffers(transparentWaterUp);
			}
		}

		private static List<LodBufferSnapshot> immutableBuffers(List<LodBufferSnapshot> buffers) {
			Objects.requireNonNull(buffers, "buffers");
			for (LodBufferSnapshot buffer : buffers) {
				Objects.requireNonNull(buffer, "buffer");
			}
			return List.copyOf(buffers);
		}

		private static void validateBuffers(List<LodBufferSnapshot> buffers) {
			for (LodBufferSnapshot buffer : buffers) {
				if (buffer.vertices().isEmpty() || buffer.vertices().size() > MAX_LOD_VERTICES_PER_SEGMENT) {
					throw new IllegalArgumentException("Distant Horizons LOD segment vertex count exceeds Rust bound");
				}
				for (LodVertex vertex : buffer.vertices()) {
					if (vertex.localX() < 0 || vertex.localX() > 0xFFFF
						|| vertex.localY() < 0 || vertex.localY() > 0xFFFF
						|| vertex.localZ() < 0 || vertex.localZ() > 0xFFFF
						|| vertex.packedLightAndMicroOffset() < 0 || vertex.packedLightAndMicroOffset() > 0xFFFF
						|| vertex.materialId() < 0 || vertex.materialId() > MAX_LOD_MATERIAL_ID
						|| vertex.normalIndex() < 0 || vertex.normalIndex() > MAX_LOD_NORMAL_INDEX) {
						throw new IllegalArgumentException("Distant Horizons LOD vertex is outside Rust semantic bounds");
					}
				}
			}
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
				BridgeFaceMaterialResolution resolution = bridgeFaceMaterialResolution(identity.blockStateIdentity());
				recordExactAtlasResolution(identity.blockStateIdentity(), resolution.resolution());
				if (resolution.resolution().status() == DistantHorizonsFaceMaterialResolver.Status.VARIANT_DEPENDENT) {
					variantDependent[identityIndex + 1] = true;
				} else {
					appendFaceMaterialTemplates(faceMaterials, identityIndex + 1, 0L, resolution);
					positionTinted[identityIndex + 1] = resolution.templates().stream()
						.anyMatch(BridgeFaceMaterialTemplate::tinted);
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
				target.add(VulkanicGalBridge.WorldLodSegmentRecord.packed(layer, buffer.packedVerticesForRust()));
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

		private static void appendFaceMaterialTemplates(
			List<VulkanicGalBridge.WorldLodFaceMaterialRecord> target,
			int materialId, long variantPosition, BridgeFaceMaterialResolution resolution
		) {
			// Publish every copied face that resolved successfully, including from a
			// partial model mapping. Rust keeps unresolved faces as explicit
			// unavailable quads, so omitting the whole table here needlessly throws
			// away safe exact-atlas coverage for the other faces. No face is guessed:
			// the planner still rejects any quad without a matching record.
			for (BridgeFaceMaterialTemplate material : resolution.templates()) {
				target.add(new VulkanicGalBridge.WorldLodFaceMaterialRecord(
					materialId, material.face(), material.layer(), material.atlasIdentity(), material.spriteIdentity(),
					material.u0(), material.v0(), material.u1(), material.v1(), material.uvCornerOrder(), variantPosition,
					material.tinted(), material.tintArgb()
				));
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
				BridgeFaceMaterialResolution resolution = bridgeVariantFaceMaterialResolution(
					identity.blockStateIdentity(), key.variantPosition()
				);
				recordExactAtlasResolution(identity.blockStateIdentity(), resolution.resolution());
				appendFaceMaterialTemplates(target, key.materialId(), key.variantPosition(), resolution);
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
		if (semanticMaterials.size() > MAX_LOD_MATERIAL_IDENTITIES_PER_COLUMN) {
			throw new IllegalArgumentException("Distant Horizons LOD material identity table exceeds Rust bound");
		}
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

	/**
	 * Record equality must compare the copied primitive sidecars by value. The
	 * generated record implementation compares List elements with
	 * {@code int[]}/{@code byte[]}/{@code long[]} identity, which made every DH
	 * rebuild look like a semantic change even when all material IDs and
	 * variants were identical. That needlessly advanced generations, kept the
	 * visible column unpublished, and could invalidate an otherwise coherent
	 * Rust submission. The sidecars are immutable constructor-owned snapshots,
	 * so deep value equality is the correct publication identity.
	 */
	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof LodMaterialProvenanceSnapshot that)) return false;
		return Objects.equals(semanticMaterials, that.semanticMaterials)
			&& Objects.equals(inputCoverage, that.inputCoverage)
			&& Objects.equals(outputCoverage, that.outputCoverage)
			&& intArraysEqual(opaque, that.opaque)
			&& byteArraysEqual(opaqueVariantStates, that.opaqueVariantStates)
			&& longArraysEqual(opaqueVariantPositions, that.opaqueVariantPositions)
			&& intArraysEqual(transparentSide, that.transparentSide)
			&& byteArraysEqual(transparentSideVariantStates, that.transparentSideVariantStates)
			&& longArraysEqual(transparentSideVariantPositions, that.transparentSideVariantPositions)
			&& intArraysEqual(transparentUp, that.transparentUp)
			&& byteArraysEqual(transparentUpVariantStates, that.transparentUpVariantStates)
			&& longArraysEqual(transparentUpVariantPositions, that.transparentUpVariantPositions)
			&& intArraysEqual(transparentWaterUp, that.transparentWaterUp)
			&& byteArraysEqual(transparentWaterUpVariantStates, that.transparentWaterUpVariantStates)
			&& longArraysEqual(transparentWaterUpVariantPositions, that.transparentWaterUpVariantPositions);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(semanticMaterials, inputCoverage, outputCoverage);
		result = 31 * result + intArraysHash(opaque);
		result = 31 * result + byteArraysHash(opaqueVariantStates);
		result = 31 * result + longArraysHash(opaqueVariantPositions);
		result = 31 * result + intArraysHash(transparentSide);
		result = 31 * result + byteArraysHash(transparentSideVariantStates);
		result = 31 * result + longArraysHash(transparentSideVariantPositions);
		result = 31 * result + intArraysHash(transparentUp);
		result = 31 * result + byteArraysHash(transparentUpVariantStates);
		result = 31 * result + longArraysHash(transparentUpVariantPositions);
		result = 31 * result + intArraysHash(transparentWaterUp);
		result = 31 * result + byteArraysHash(transparentWaterUpVariantStates);
		return 31 * result + longArraysHash(transparentWaterUpVariantPositions);
	}

	private static boolean intArraysEqual(List<int[]> left, List<int[]> right) {
		if (left.size() != right.size()) return false;
		for (int index = 0; index < left.size(); index++) {
			if (!java.util.Arrays.equals(left.get(index), right.get(index))) return false;
		}
		return true;
	}

	private static boolean byteArraysEqual(List<byte[]> left, List<byte[]> right) {
		if (left.size() != right.size()) return false;
		for (int index = 0; index < left.size(); index++) {
			if (!java.util.Arrays.equals(left.get(index), right.get(index))) return false;
		}
		return true;
	}

	private static boolean longArraysEqual(List<long[]> left, List<long[]> right) {
		if (left.size() != right.size()) return false;
		for (int index = 0; index < left.size(); index++) {
			if (!java.util.Arrays.equals(left.get(index), right.get(index))) return false;
		}
		return true;
	}

	private static int intArraysHash(List<int[]> values) {
		int result = 1;
		for (int[] value : values) result = 31 * result + java.util.Arrays.hashCode(value);
		return result;
	}

	private static int byteArraysHash(List<byte[]> values) {
		int result = 1;
		for (byte[] value : values) result = 31 * result + java.util.Arrays.hashCode(value);
		return result;
	}

	private static int longArraysHash(List<long[]> values) {
		int result = 1;
		for (long[] value : values) result = 31 * result + java.util.Arrays.hashCode(value);
		return result;
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
		static LodBufferSnapshot fromPacked(int sourceBufferIndex, byte[] packedVertices) {
			return new LodBufferSnapshot(sourceBufferIndex, new PackedLodVertexList(packedVertices));
		}

		public LodBufferSnapshot {
			if (sourceBufferIndex < 0) {
				throw new IllegalArgumentException("Distant Horizons semantic segment source buffer index must be non-negative");
			}
			Objects.requireNonNull(vertices, "vertices");
			if (vertices.size() % 4 != 0) {
				throw new IllegalArgumentException("Distant Horizons semantic segment must contain complete quads");
			}
			// Real DH capture keeps the exact fixed-layout vertex bytes in one
			// compact semantic stream.  Retaining one Java record per vertex made
			// world entry allocate millions of objects before Rust could consume a
			// single explicit LOD asset.  Tests and external semantic producers may
			// still provide records; pack those at this boundary as well.
			vertices = vertices instanceof PackedLodVertexList
				? vertices
				: new PackedLodVertexList(packVertices(vertices));
		}

		private static byte[] packVertices(List<LodVertex> vertices) {
			byte[] packed = new byte[Math.multiplyExact(vertices.size(), VERTEX_STRIDE_BYTES)];
			ByteBuffer output = ByteBuffer.wrap(packed).order(ByteOrder.nativeOrder());
			for (LodVertex vertex : vertices) {
				output.putShort((short)vertex.localX());
				output.putShort((short)vertex.localY());
				output.putShort((short)vertex.localZ());
				output.putShort((short)vertex.packedLightAndMicroOffset());
				output.put((byte)vertex.red());
				output.put((byte)vertex.green());
				output.put((byte)vertex.blue());
				output.put((byte)vertex.alpha());
				output.put((byte)vertex.materialId());
				output.put((byte)vertex.normalIndex());
				output.putShort((short)vertex.padding());
			}
			return packed;
		}

		/** Internal one-way transport view. The enclosing immutable snapshot owns
		 * these bytes; the bridge reads them synchronously and never retains a
		 * Java GPU or renderer object. */
		public byte[] packedVerticesForRust() {
			return ((PackedLodVertexList) vertices).bytes;
		}
	}

	/**
	 * Read-only view of the fixed DH semantic layout.  It creates a vertex
	 * value only for a consumer that asks for one, while the retained collector
	 * state stays a contiguous 16-byte-per-vertex stream.
	 */
	private static final class PackedLodVertexList extends AbstractList<LodVertex> {
		private final byte[] bytes;

		private PackedLodVertexList(byte[] bytes) {
			this.bytes = Objects.requireNonNull(bytes, "bytes");
			if (bytes.length % VERTEX_STRIDE_BYTES != 0) {
				throw new IllegalArgumentException("packed Distant Horizons vertices are not stride aligned");
			}
		}

		@Override
		public LodVertex get(int index) {
			if (index < 0 || index >= size()) throw new IndexOutOfBoundsException(index);
			ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
			input.position(Math.multiplyExact(index, VERTEX_STRIDE_BYTES));
			return new LodVertex(
				Short.toUnsignedInt(input.getShort()), Short.toUnsignedInt(input.getShort()),
				Short.toUnsignedInt(input.getShort()), Short.toUnsignedInt(input.getShort()),
				Byte.toUnsignedInt(input.get()), Byte.toUnsignedInt(input.get()),
				Byte.toUnsignedInt(input.get()), Byte.toUnsignedInt(input.get()),
				Byte.toUnsignedInt(input.get()), Byte.toUnsignedInt(input.get()), Short.toUnsignedInt(input.getShort())
			);
		}

		@Override
		public int size() {
			return bytes.length / VERTEX_STRIDE_BYTES;
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

	static void acknowledgeForTest(PendingAssetUpdate update) {
		acknowledge(update);
	}

	static void beginRustOpaqueRouteFrameForTest() {
		synchronized (LOCK) {
			PENDING_RENDER_FRAME = new VulkanicGalBridge.WorldLodRenderFrameRecord(
				true, 0, 0, identityMatrix(), identityMatrix(), identityMatrix(), identityMatrix(),
				0.0F, 0.01F, 0.0F, 0.0F, 0, 0, new float[3], new float[20], 0
			);
			DhCollectorLedger.beginTestFrame(true);
			resetExactAtlasIdentityCoverage();
		}
	}

	static void beginVisibleFrameForTest() {
		synchronized (LOCK) {
			PENDING_RENDER_FRAME = VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
			DhCollectorLedger.beginTestFrame(false);
			resetExactAtlasIdentityCoverage();
		}
	}

	/** Consumes resolved DH frame semantics without retaining a renderer object. */
	public static VulkanicGalBridge.WorldLodRenderFrameRecord consumeRenderFrame() {
		if (!enabled()) {
			return VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
		}
		synchronized (LOCK) {
			long[] header = new long[4];
			DhCollectorLedger.consume(DhCollectorLedger.CONSUME_RENDER_FRAME, header);
			VulkanicGalBridge.WorldLodRenderFrameRecord result = frameRecord(header[0] != 0L, (int)header[1]);
			PENDING_RENDER_FRAME = VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
			return result;
		}
	}

	/** Consumes only semantic visible-column references for the combined Rust frame. */
	public static List<VulkanicGalBridge.WorldLodColumnInstanceRecord> consumeVisibleSegments() {
		if (!enabled()) {
			return List.of();
		}
		synchronized (LOCK) {
			// The coordinator activates a pending replacement after this frame has
			// presented. The visible list therefore stays paired with the last
			// acknowledged immutable column generation for the entire submission.
			List<VulkanicGalBridge.WorldLodColumnInstanceRecord> result =
				records(DhCollectorLedger.consume(DhCollectorLedger.CONSUME_VISIBLE_SEGMENTS, new long[4]));
			if (!result.isEmpty()) {
				// The visible DH set uses the same copied block atlas as indexed
				// terrain meshes. Publish that semantic resource before the combined
				// frame consumes its exact-atlas draws; do not wait for a nearby
				// Sodium section to happen to build.
				RustGalTerrainRenderer.ensureTerrainAtlasAssetForWorldMesh();
			}
			LAST_CONSUMED_VISIBLE_SEGMENTS = result;
			LAST_CONSUMED_VISIBLE_SEGMENT_SNAPSHOTS = executionSnapshotsEnabled()
				? snapshotExecutedSegmentsLocked(result)
				: List.of();
			return result;
		}
	}

	static List<LodBufferSnapshot> copyBuffersForTest(List<ByteBuffer> buffers, int maximumVerticesPerSegment) {
		return copyBuffers(buffers, maximumVerticesPerSegment);
	}

	static List<VulkanicGalBridge.WorldLodColumnInstanceRecord> executedVisibleSegmentsForTest(long worldFrame) {
		synchronized (LOCK) {
			return executedSegmentsForWorldFrameLocked(worldFrame);
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
		synchronized (LOCK) {
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

	public static void markRustOpaqueRouteSelected() {
		markRustNonWaterRouteSelected();
	}

	static LodMaterialProvenanceSnapshot materialProvenanceForTest(long columnKey) {
		synchronized (LOCK) {
			return MATERIAL_PROVENANCE.get(columnKey);
		}
	}

	static PendingAssetUpdate pendingUpdateForTest() {
		return pendingUpdate();
	}

	static PendingAssetUpdate pendingVisibleUpdateForTest() {
		return pendingUpdate(true);
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
		recordPackedWaterColorBufferSamples(opaque.vertexBuffers(), "opaque");
		recordPackedWaterColorBufferSamples(transparentSide.vertexBuffers(), "transparent-side");
		recordPackedWaterColorBufferSamples(transparentUp.vertexBuffers(), "transparent-up");
		recordPackedWaterColorBufferSamples(transparentWaterUp.vertexBuffers(), "water-up");
		Objects.requireNonNull(inputCoverage, "inputCoverage");
		Objects.requireNonNull(outputCoverage, "outputCoverage");
		LodMaterialProvenanceSnapshot provenance = materialProvenancePublicationRequired()
			? new LodMaterialProvenanceSnapshot(
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
				copyMaterialIds(transparentWaterUp, semanticMaterials.size()),
				copyVariantStates(transparentWaterUp),
				copyVariantPositions(transparentWaterUp)
			)
			: null;
		recordBuiltColumnSnapshot(
			columnKey, origin,
			opaque.vertexBuffers(), transparentSide.vertexBuffers(),
			transparentUp.vertexBuffers(), transparentWaterUp.vertexBuffers(),
			provenance
		);
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

	/** Compatibility entrypoint retained for existing callers. */
	public static VisibleColumnSegments recordVisibleOpaqueColumn(long columnKey) {
		return recordVisibleMaterialColumn(columnKey);
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
		synchronized (LOCK) {
			LodColumnSnapshot column = publishedColumnLocked(columnKey);
			if (column == null) {
				return;
			}
			List<Integer> segmentIndexes = column.compactSegmentIndexes(layer, sourceSegmentIndex);
			if (segmentIndexes.isEmpty()) {
				return;
			}
			DhCollectorLedger.appendSegments(column.columnKey(), column.generation(), layer,
				segmentIndexes.stream().mapToInt(Integer::intValue).toArray());
		}
	}

	static void resetForTest() {
		synchronized (LOCK) {
			DistantHorizonsFaceMaterialResolver.clearCachedStateResolutions();
			applyEffectsLocked(DhCollectorLedger.resetForTest(), null, Map.of());
			EXACT_ATLAS_COVERAGE_CACHE.clear();
			BRIDGE_FACE_MATERIAL_CACHE.clear();
			BRIDGE_VARIANT_FACE_MATERIAL_CACHE.clear();
			executionTraceEvents = 0;
			lastExecutionTraceNanos = 0L;
			rejectionTraceEvents = 0;
			lastRejectionTraceNanos = 0L;
			LAST_CONSUMED_VISIBLE_SEGMENTS = List.of();
			LAST_CONSUMED_VISIBLE_SEGMENT_SNAPSHOTS = List.of();
			EXECUTED_VISIBLE_SEGMENTS_BY_WORLD_FRAME.clear();
			PENDING_RENDER_FRAME = VulkanicGalBridge.WorldLodRenderFrameRecord.disabled();
			resetExactAtlasIdentityCoverage();
			waterSourceInputProbes = configuredWaterSourceInputProbes;
			WATER_SOURCE_INPUT_TRACES.clear();
		}
	}

	static LodColumnSnapshot snapshotForTest(long columnKey) {
		synchronized (LOCK) {
			// COLUMNS.get: an access in the ledger's LRU, as it was in Java.
			return DhCollectorLedger.query(DhCollectorLedger.QUERY_TOUCH, columnKey) == 0L ? null : currentColumnLocked(columnKey);
		}
	}

	static void trimRetainedColumnsForTest(int maximumColumns, long maximumBytes) {
		synchronized (LOCK) {
			applyEffectsLocked(DhCollectorLedger.trim(maximumColumns, maximumBytes), null, Map.of());
		}
	}

	private static List<LodBufferSnapshot> copyBuffers(List<ByteBuffer> buffers) {
		return copyBuffers(buffers, MAX_TRANSPORT_VERTICES_PER_SEGMENT);
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
				byte[] packedVertices = new byte[Math.multiplyExact(chunkVertexCount, VERTEX_STRIDE_BYTES)];
				source.get(packedVertices);
				ByteBuffer packedSource = ByteBuffer.wrap(packedVertices).order(ByteOrder.nativeOrder());
				for (int chunkVertexIndex = 0; chunkVertexIndex < chunkVertexCount; chunkVertexIndex++) {
					int vertexIndex = copiedVertices + chunkVertexIndex;
					packedSource.position(Math.multiplyExact(chunkVertexIndex, VERTEX_STRIDE_BYTES) + 14);
					int padding = Short.toUnsignedInt(packedSource.getShort());
				if (padding != 0) {
					throw new IllegalArgumentException(
						"Distant Horizons semantic vertex " + vertexIndex
							+ " has non-zero reserved padding"
					);
				}
				}
				copies.add(LodBufferSnapshot.fromPacked(sourceBufferIndex, packedVertices));
				copiedVertices += chunkVertexCount;
			}
		}
		return List.copyOf(copies);
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

	private static float[] identityMatrix() {
		return new float[] {
			1.0F, 0.0F, 0.0F, 0.0F,
			0.0F, 1.0F, 0.0F, 0.0F,
			0.0F, 0.0F, 1.0F, 0.0F,
			0.0F, 0.0F, 0.0F, 1.0F
		};
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
			DhCollectorLedger.allocateGeneration(),
			origin.getX(),
			origin.getY(),
			origin.getZ(),
			copyBuffers(opaque),
			copyBuffers(transparentSide),
			copyBuffers(transparentUp),
			copyBuffers(transparentWaterUp)
		);
		synchronized (LOCK) {
			recordBuiltSnapshotLocked(columnKey, snapshot, provenance, false, new long[2]);
		}
	}

	private static void recordPackedWaterColorBufferSamples(List<ByteBuffer> buffers, String stream) {
		if (!graphicsAuditEnabled() || buffers == null) return;
		for (ByteBuffer source : buffers) {
			ByteBuffer packet = source.duplicate();
			for (int offset = packet.position(); offset + VERTEX_STRIDE_BYTES <= packet.limit(); offset += VERTEX_STRIDE_BYTES) {
				if (Byte.toUnsignedInt(packet.get(offset + 12)) != EDhApiBlockMaterial.WATER.index) continue;
				String sample = stream + ":rgb="
					+ Byte.toUnsignedInt(packet.get(offset + 8)) + ","
					+ Byte.toUnsignedInt(packet.get(offset + 9)) + ","
					+ Byte.toUnsignedInt(packet.get(offset + 10))
					+ ":alpha=" + Byte.toUnsignedInt(packet.get(offset + 11));
				synchronized (AUDIT_PACKED_WATER_COLORS) {
					if (AUDIT_PACKED_WATER_COLORS.size() >= 16 || !AUDIT_PACKED_WATER_COLORS.add(sample)) continue;
				}
				System.out.println("[MattMC graphics audit] DH packed water vertex " + sample);
			}
		}
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
}
