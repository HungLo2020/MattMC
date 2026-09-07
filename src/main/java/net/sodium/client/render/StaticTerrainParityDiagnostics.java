package net.sodium.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.RenderSectionFlags;
import net.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.render.chunk.lists.ChunkRenderList;
import net.sodium.client.render.chunk.lists.SortedRenderLists;
import net.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.util.iterator.ByteIterator;
import net.vulkanic.VulkanicAPI;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

public final class StaticTerrainParityDiagnostics {
    private static final int INDEX_TYPE_U16 = 1;
    private static final int POSITION_MAX_VALUE = 1 << 20;
    private static final int TEXTURE_MAX_VALUE = 1 << 15;
    private static final int COMPACT_PREFIX_STRIDE = 20;
    private static final int POSITION_OFFSET = 0;
    private static final int COLOR_OFFSET = 8;
    private static final int TEXTURE_OFFSET = 12;
    private static final int LIGHT_MATERIAL_OFFSET = 16;

    private static final boolean ENABLED =
            Boolean.getBoolean("mattmc.dev.staticTerrainParityDiagnostics");
    private static final AtomicInteger OPENGL_BINDING_EVENTS = new AtomicInteger();
    private static final AtomicInteger OPENGL_VERTEX_EVENTS = new AtomicInteger();
    private static final net.minecraft.client.dev.GraphicsAuditFrameObservation OPENGL_FRAME_BINDING =
            new net.minecraft.client.dev.GraphicsAuditFrameObservation();
    private static final int MAX_EVENTS = Math.max(
            1,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxEvents", 512)
    );
    /**
     * Visible-list observations drive deterministic-capture readiness, so their
     * bounded receipt budget must not be shared with targeted source probes.
     */
    private static final int MAX_VISIBLE_LIST_EVENTS = Math.max(
            1,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxVisibleListEvents", MAX_EVENTS)
    );
    /** Stable Java-list records need their own small bound and sample budget. */
    private static final int MAX_READY_VISIBLE_LIST_EVENTS = Math.max(
            1,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxReadyVisibleListEvents", 4)
    );
    /** Shared harness policy for a stable capture-phase visible-list receipt. */
    private static final int READY_VISIBLE_LIST_FRAMES = Math.max(
            1,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.readyFrames", 3)
    );
    private static final int MAX_SAMPLES = Math.max(
            0,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxSamples", 32)
    );
    private static final int MAX_COVERAGE_EVENTS = Math.max(
            1,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxCoverageEvents", 8192)
    );
    private static final int MAX_COVERAGE_SAMPLES = Math.max(
            0,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxCoverageSamples", 768)
    );
    /**
     * Compact color/light receipts are independent observations of the CPU
     * terrain payload. They must not consume the generic coverage budget: a
     * whole-frame producer can legitimately build many sections before the
     * deterministic target section arrives.
     */
    private static final int MAX_COMPACT_LIGHTING_EVENTS = Math.max(
            1,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxCompactLightingEvents", 8192)
    );
    /** A tiny independent budget for populated terrain receipts used by the parity gate. */
    private static final int MAX_READY_COVERAGE_EVENTS = Math.max(
            1,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxReadyCoverageEvents", 4)
    );
    /** Screenshot-correlated receipts must not be displaced by startup probes. */
    private static final int MAX_CAPTURE_COVERAGE_EVENTS = Math.max(
            1,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxCaptureCoverageEvents", 4)
    );
	/** Separate bounded budget for CPU-only portal traversal comparison. */
	private static final int MAX_PORTAL_TRACE_EVENTS = Math.max(
			1, Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxPortalTraceEvents", 4096)
	);
	/** Optional exact section-key filter for bounded portal-path diagnosis. */
	private static final long[] PORTAL_TRACE_SECTIONS = parsePortalTraceSections();
	/** Optional exact source-mesh section filter for visibility-data diagnosis. */
	private static final long[] VISIBILITY_TRACE_SECTIONS = parseVisibilityTraceSections();
    private static final long TRANSFORM_TRACE_SECTION = parseTransformTraceSection();
    /** Startup culling can precede the deterministic capture pose by hundreds
     * of frames; permit the harness to retain the later capture-phase receipt. */
    private static final int MAX_TRANSFORM_TRACE_EVENTS = Math.max(
            1,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxTransformTraceEvents", 8)
    );
    private static final long APPEARANCE_TRACE_SECTION = parseAppearanceTraceSection();
    private static final int[] APPEARANCE_TRACE_BLOCK = parseAppearanceTraceBlock();
    /**
     * Optional bounded receipt for particular compact ABGR values in one
     * section. This is only an offline parity join key: raw CPU mesh words
     * are observed after construction and are never fed back to a renderer.
     */
    private static final int[] COMPACT_COLOR_TRACE_VALUES = parseCompactColorTraceValues();
    private static final int MAX_COMPACT_COLOR_TRACE_SAMPLES = Math.max(
            1,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.compactColorTraceMaxSamples", 128)
    );
    private static final long FACE_CULL_TRACE_SECTION = parseFaceCullTraceSection();
    private static final int MAX_FACE_CULL_TRACE_EVENTS = Math.max(
            1,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxFaceCullTraceEvents", 4096)
    );
    private static final Path OUTPUT_PATH = Path.of(System.getProperty(
            "mattmc.dev.staticTerrainParityDiagnostics.path",
            "run/static_terrain_parity_diagnostics.jsonl"
    ));
    private static final AtomicInteger EVENTS = new AtomicInteger();
    private static final AtomicInteger READY_VISIBLE_LIST_EVENTS = new AtomicInteger();
    private static final AtomicInteger COVERAGE_EVENTS = new AtomicInteger();
    private static final AtomicInteger CLASSIFICATION_EVENTS = new AtomicInteger();
    private static final AtomicInteger COMPACT_LIGHTING_EVENTS = new AtomicInteger();
    private static final AtomicInteger READY_COVERAGE_EVENTS = new AtomicInteger();
    private static final AtomicInteger CAPTURE_COVERAGE_EVENTS = new AtomicInteger();
	private static final AtomicInteger PORTAL_TRACE_EVENTS = new AtomicInteger();
    private static final AtomicInteger TRANSFORM_TRACE_EVENTS = new AtomicInteger();
    private static final AtomicInteger APPEARANCE_TRACE_EVENTS = new AtomicInteger();
    private static final AtomicInteger APPEARANCE_FRAGMENT_TRACE_EVENTS = new AtomicInteger();
    private static final AtomicInteger APPEARANCE_LIGHT_TRACE_EVENTS = new AtomicInteger();
    private static final AtomicInteger APPEARANCE_LIGHT_SNAPSHOT_EVENTS = new AtomicInteger();
    private static final AtomicInteger FACE_CULL_TRACE_EVENTS = new AtomicInteger();
    private static final AtomicInteger MAX_TEXTURE_LOD_BIAS_EVENTS = new AtomicInteger();
    private static final Map<CoverageKey, MeshCoverage> SOURCE_MESHES = new ConcurrentHashMap<>();
    private static final Map<CoverageKey, AppearanceSource> SOURCE_APPEARANCES = new ConcurrentHashMap<>();
    private static final Map<String, PendingIrisTerrainFragmentProbe> PENDING_IRIS_FRAGMENT_PROBES = new ConcurrentHashMap<>();
    private static volatile int latestSolidSectionCount;
    private static volatile long latestSolidHash;
    private static volatile long latestSolidGameTime;
    private static volatile int stableSolidFrames;
    private static volatile int appearanceLightSnapshotMask;
    private static volatile boolean appearanceLightSnapshotObserved;
    private static volatile CaptureCoverageSnapshot latestJavaDrawSolidCoverage;
    private static volatile CaptureCoverageSnapshot latestJavaDrawCutoutCoverage;

    private StaticTerrainParityDiagnostics() {
    }

    /**
     * Records Frozen OpenGL's capability-derived Sodium shader constant. This
     * is a capture-only observation: the caller still uses its original value
     * to compile and render the Java OpenGL pipeline.
     */
    public static void recordMaxTextureLodBias(int maxTextureLodBias) {
        if (!ENABLED || MAX_TEXTURE_LOD_BIAS_EVENTS.getAndIncrement() != 0) {
            return;
        }
        try {
            writeLine("{\"schema\":\"mattmc-static-terrain-texture-lod-bias-v1\","
                + "\"backend\":\"java-opengl\",\"maxTextureLodBias\":" + maxTextureLodBias + "}\n");
        } catch (IOException ignored) {
            // Diagnostics must never alter shader creation or rendering behavior.
        }
    }

    /**
     * Emits the latest real Java draw observation at the deterministic screenshot
     * boundary.  The snapshot is diagnostic data only: it retains no render list,
     * OpenGL object, or mutable renderer state.
     */
    public static void recordJavaDrawCaptureCoverage(long renderedFrameIndex) {
        if (!ENABLED || renderedFrameIndex <= 0L) {
            return;
        }
        emitJavaDrawCaptureCoverage(latestJavaDrawSolidCoverage, renderedFrameIndex);
        emitJavaDrawCaptureCoverage(latestJavaDrawCutoutCoverage, renderedFrameIndex);
    }

    private static void emitJavaDrawCaptureCoverage(CaptureCoverageSnapshot snapshot, long renderedFrameIndex) {
        if (snapshot == null || snapshot.sectionCount() <= 0) {
            return;
        }
        writeCoverageEvent(
                "java-opengl-draw-capture-ready-coverage",
                snapshot.layer(),
                renderedFrameIndex,
                0,
                snapshot.animatedSections(),
                snapshot.cameraX(), snapshot.cameraY(), snapshot.cameraZ(),
                snapshot.viewportWidth(), snapshot.viewportHeight(),
                snapshot.sectionCount(), snapshot.vertexTotal(), snapshot.indexTotal(), snapshot.primitiveTotal(),
                snapshot.missingCoverage(), snapshot.executedRecords(),
                builder -> builder.append(snapshot.records(), 1, snapshot.records().length() - 1)
        );
    }

	/** Capture-only observation of a finalized Sodium portal traversal step. */
	public static void recordPortalTraversal(String route, long sectionKey, long cameraSectionKey,
			int incomingDirections, int outgoingBeforeOutwardMask, int outgoingAfterOutwardMask,
			int adjacentMask, int outgoingDirections,
			long visibilityData, double cameraDeltaX, double cameraDeltaY, double cameraDeltaZ) {
		if (!matchesPortalTraceSection(sectionKey)) return;
		if (!ENABLED || PORTAL_TRACE_EVENTS.incrementAndGet() > MAX_PORTAL_TRACE_EVENTS) return;
		StringBuilder json = new StringBuilder(256);
		json.append("{");
		appendField(json, "schema", "mattmc-static-terrain-portal-trace-v1").append(", ");
		appendField(json, "route", route).append(", ");
		appendField(json, "sectionKey", sectionKey).append(", ");
		appendField(json, "cameraSectionKey", cameraSectionKey).append(", ");
		appendField(json, "incomingDirections", incomingDirections).append(", ");
		appendField(json, "outgoingBeforeOutwardMask", outgoingBeforeOutwardMask).append(", ");
		appendField(json, "outgoingAfterOutwardMask", outgoingAfterOutwardMask).append(", ");
		appendField(json, "adjacentMask", adjacentMask).append(", ");
		appendField(json, "outgoingDirections", outgoingDirections).append(", ");
		appendField(json, "visibilityData", String.format(java.util.Locale.ROOT, "%016x", visibilityData)).append(", ");
		appendField(json, "cameraDeltaX", cameraDeltaX).append(", ");
		appendField(json, "cameraDeltaY", cameraDeltaY).append(", ");
		appendField(json, "cameraDeltaZ", cameraDeltaZ).append(", ");
		appendField(json, "gameTime", Minecraft.getInstance().level == null ? -1L : Minecraft.getInstance().level.getGameTime());
		json.append("}\n");
		try {
			writeLine(json.toString());
		} catch (IOException ignored) {
			// Diagnostics must never alter traversal or rendering behavior.
		}
	}

	private static boolean matchesPortalTraceSection(long sectionKey) {
		if (PORTAL_TRACE_SECTIONS.length == 0) return true;
		for (long candidate : PORTAL_TRACE_SECTIONS) {
			if (candidate == sectionKey) return true;
		}
		return false;
	}

	private static long[] parsePortalTraceSections() {
		String configured = System.getProperty("mattmc.dev.staticTerrainParityDiagnostics.portalTraceSections", "").trim();
		if (configured.isEmpty()) return new long[0];
		return java.util.Arrays.stream(configured.split(","))
			.map(String::trim).filter(value -> !value.isEmpty()).mapToLong(Long::parseLong).toArray();
	}

	private static long[] parseVisibilityTraceSections() {
		String configured = System.getProperty("mattmc.dev.staticTerrainParityDiagnostics.visibilityTraceSections", "").trim();
		if (configured.isEmpty()) return new long[0];
		return java.util.Arrays.stream(configured.split(","))
			.map(String::trim).filter(value -> !value.isEmpty()).mapToLong(Long::parseLong).toArray();
	}

    public static void recordChunkBuildOutput(ChunkBuildOutput output) {
        if (!ENABLED || output == null || output.render == null) {
            return;
        }
		recordSourceVisibility(output);
        recordSourceMesh(output, DefaultTerrainRenderPasses.SOLID, "solid");
        recordSourceMesh(output, DefaultTerrainRenderPasses.CUTOUT, "cutout");
    }

	/** Records immutable build visibility only when explicitly selected. */
	private static void recordSourceVisibility(ChunkBuildOutput output) {
		if (VISIBILITY_TRACE_SECTIONS.length == 0 || output.info == null) return;
		long sectionKey = output.render.getPosition().asLong();
		boolean selected = false;
		for (long candidate : VISIBILITY_TRACE_SECTIONS) {
			if (candidate == sectionKey) {
				selected = true;
				break;
			}
		}
		if (!selected) return;
		try {
			StringBuilder json = new StringBuilder(256);
			json.append("{");
			appendField(json, "schema", "mattmc-static-terrain-source-visibility-v1").append(", ");
			appendField(json, "sectionKey", sectionKey).append(", ");
			appendField(json, "x", output.render.getChunkX()).append(", ");
			appendField(json, "y", output.render.getChunkY()).append(", ");
			appendField(json, "z", output.render.getChunkZ()).append(", ");
			appendField(json, "visibilityData", String.format(Locale.ROOT, "%016x", output.info.visibilityData)).append(", ");
			appendField(json, "flags", output.info.flags).append(", ");
			appendField(json, "animatedSpriteCount", output.info.animatedSprites == null ? 0 : output.info.animatedSprites.length).append(", ");
			appendField(json, "sourceGeneration", output.submitTime);
			json.append("}\n");
			writeLine(json.toString());
		} catch (IOException ignored) {
			// Diagnostics must never alter meshing or rendering behavior.
		}
	}

    /**
     * Captures section-build semantics independently of the renderer objects so
     * cross-repository parity can stop at the first block/fluid input divergence.
     */
    public static SourceBlockClassification beginSourceBlockClassification() {
        return ENABLED ? new SourceBlockClassification() : null;
    }

    public static void recordSourceBlockVisit(
            SourceBlockClassification classification,
            String blockIdentity,
            int localY,
            boolean model,
            boolean nativeModel,
            boolean fluid,
            boolean nativeFluid,
            boolean builtInWater
    ) {
        if (classification != null) {
            classification.record(blockIdentity, localY, model, nativeModel, fluid, nativeFluid, builtInWater);
        }
    }

    public static void recordSourceBlockClassification(ChunkBuildOutput output, SourceBlockClassification classification) {
        if (!ENABLED || output == null || output.render == null || classification == null) {
            return;
        }
        if (VISIBILITY_TRACE_SECTIONS.length != 0
                && !matchesVisibilityTraceSection(output.render.getPosition().asLong())) {
            return;
        }
        int eventIndex = CLASSIFICATION_EVENTS.incrementAndGet();
        if (eventIndex > MAX_COVERAGE_EVENTS) {
            return;
        }
        try {
            StringBuilder line = new StringBuilder(1024);
            line.append("{");
            appendField(line, "schema", "mattmc-static-terrain-source-classification-v1").append(", ");
            appendField(line, "eventIndex", eventIndex).append(", ");
            appendField(line, "sectionKey", output.render.getPosition().asLong()).append(", ");
            appendField(line, "x", output.render.getChunkX()).append(", ");
            appendField(line, "y", output.render.getChunkY()).append(", ");
            appendField(line, "z", output.render.getChunkZ()).append(", ");
            appendField(line, "sourceGeneration", output.submitTime).append(", ");
            appendField(line, "modelBlocks", classification.modelBlocks).append(", ");
            appendField(line, "nativeModelBlocks", classification.nativeModelBlocks).append(", ");
            appendField(line, "javaModelBlocks", classification.javaModelBlocks).append(", ");
            appendField(line, "fluidBlocks", classification.fluidBlocks).append(", ");
            appendField(line, "nativeFluidBlocks", classification.nativeFluidBlocks).append(", ");
            appendField(line, "javaFluidBlocks", classification.javaFluidBlocks).append(", ");
            appendField(line, "builtInWaterBlocks", classification.builtInWaterBlocks).append(", ");
            appendField(line, "modelMinLocalY", classification.modelMinLocalY()).append(", ");
            appendField(line, "modelMaxLocalY", classification.modelMaxLocalY()).append(", ");
            appendField(line, "fluidMinLocalY", classification.fluidMinLocalY()).append(", ");
            appendField(line, "fluidMaxLocalY", classification.fluidMaxLocalY()).append(", ");
            appendField(line, "identityCounts", classification.identityCounts());
            line.append("}\n");
            writeLine(line.toString());
        } catch (IOException ignored) {
            // Diagnostics must never alter render behavior.
        }
    }

    private static boolean matchesVisibilityTraceSection(long sectionKey) {
        for (long candidate : VISIBILITY_TRACE_SECTIONS) {
            if (candidate == sectionKey) {
                return true;
            }
        }
        return false;
    }

    /**
     * Opt-in observation of the real Java occlusion decision for one section. The cache keeps its
     * normal control flow; this method writes a bounded semantic record after that decision exists.
     */
    public static void recordFaceCullDecision(
            BlockPos selfPos,
            Direction facing,
            BlockState selfState,
            BlockState neighborState,
            boolean draw,
            String reason,
            boolean neighborFullShape,
            boolean neighborEmptyShape,
            boolean selfEmptyShape,
            boolean selfSkipRendering,
            boolean customCallback
    ) {
        if (!ENABLED || FACE_CULL_TRACE_SECTION == Long.MIN_VALUE
                || SectionPos.asLong(selfPos) != FACE_CULL_TRACE_SECTION) {
            return;
        }
        int eventIndex = FACE_CULL_TRACE_EVENTS.incrementAndGet();
        if (eventIndex > MAX_FACE_CULL_TRACE_EVENTS) {
            return;
        }
        try {
            StringBuilder line = new StringBuilder(512);
            line.append("{");
            appendField(line, "schema", "mattmc-static-terrain-face-cull-v1").append(", ");
            appendField(line, "eventIndex", eventIndex).append(", ");
            appendField(line, "sectionKey", FACE_CULL_TRACE_SECTION).append(", ");
            appendField(line, "x", selfPos.getX()).append(", ");
            appendField(line, "y", selfPos.getY()).append(", ");
            appendField(line, "z", selfPos.getZ()).append(", ");
            appendField(line, "localY", Math.floorMod(selfPos.getY(), 16)).append(", ");
            appendField(line, "face", facing.get3DDataValue()).append(", ");
            appendField(line, "block", String.valueOf(BuiltInRegistries.BLOCK.getKey(selfState.getBlock()))).append(", ");
            appendField(line, "neighbor", String.valueOf(BuiltInRegistries.BLOCK.getKey(neighborState.getBlock()))).append(", ");
            appendField(line, "draw", draw ? 1L : 0L).append(", ");
            appendField(line, "reason", reason).append(", ");
            appendField(line, "neighborFullShape", neighborFullShape ? 1L : 0L).append(", ");
            appendField(line, "neighborEmptyShape", neighborEmptyShape ? 1L : 0L).append(", ");
            appendField(line, "selfEmptyShape", selfEmptyShape ? 1L : 0L).append(", ");
            appendField(line, "selfSkipRendering", selfSkipRendering ? 1L : 0L).append(", ");
            appendField(line, "customCallback", customCallback ? 1L : 0L);
            line.append("}\n");
            writeLine(line.toString());
        } catch (IOException ignored) {
            // Diagnostics must never alter render behavior.
        }
    }

    static void recordVisibleLists(
            String stage,
            String layer,
            SortedRenderLists renderLists,
            double cameraX,
            double cameraY,
            double cameraZ,
            int viewportWidth,
            int viewportHeight
    ) {
        if (!ENABLED || renderLists == null) {
            return;
        }

        int eventIndex = EVENTS.incrementAndGet();
        boolean writeEvent = eventIndex <= MAX_VISIBLE_LIST_EVENTS;

        int regionCount = 0;
        int sectionCount = 0;
        long orderedHash = 0xcbf29ce484222325L;
        long setXor = 0L;
        long setSum = 0L;
        StringBuilder samples = new StringBuilder();
        samples.append("[");

        Iterator<ChunkRenderList> renderListIterator = renderLists.iterator();
        while (renderListIterator.hasNext()) {
            ChunkRenderList renderList = renderListIterator.next();
            regionCount++;
            ByteIterator sectionIterator = renderList.sectionsWithGeometryIterator(false);
            if (sectionIterator == null) {
                continue;
            }
            while (sectionIterator.hasNext()) {
                int localSectionIndex = sectionIterator.nextByteAsInt() & 0xFF;
                RenderSection section = renderList.getRegion().getSection(localSectionIndex);
                if (section == null) {
                    continue;
                }
                SectionPos position = section.getPosition();
                long sectionKey = position.asLong();
                int flags = section.getFlags();
                long sectionHash = 0xcbf29ce484222325L;
                sectionHash = mix(sectionHash, sectionKey);
                sectionHash = mix(sectionHash, flags);
                sectionHash = mix(sectionHash, section.getOriginX());
                sectionHash = mix(sectionHash, section.getOriginY());
                sectionHash = mix(sectionHash, section.getOriginZ());
                orderedHash = mix(orderedHash, sectionHash);
                setXor ^= sectionHash;
                setSum += Long.rotateLeft(sectionHash, (int) (sectionKey & 31L));
                if (sectionCount < MAX_SAMPLES) {
                    if (samples.length() > 1) {
                        samples.append(", ");
                    }
                    samples.append("{");
                    appendField(samples, "sectionKey", sectionKey).append(", ");
                    appendField(samples, "x", section.getChunkX()).append(", ");
                    appendField(samples, "y", section.getChunkY()).append(", ");
                    appendField(samples, "z", section.getChunkZ()).append(", ");
                    appendField(samples, "originX", section.getOriginX()).append(", ");
                    appendField(samples, "originY", section.getOriginY()).append(", ");
                    appendField(samples, "originZ", section.getOriginZ()).append(", ");
                    appendField(samples, "flags", flags);
                    samples.append("}");
                }
                sectionCount++;
            }
        }
        samples.append("]");

        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? -1L : minecraft.level.getGameTime();
        long hash = mix(mix(0xcbf29ce484222325L, setXor), setSum);
        if ("solid".equals(layer) && sectionCount > 0) {
            if (latestSolidSectionCount == sectionCount && latestSolidHash == hash) {
                stableSolidFrames++;
            } else {
                stableSolidFrames = 1;
            }
            latestSolidSectionCount = sectionCount;
            latestSolidHash = hash;
            latestSolidGameTime = gameTime;
        }
        // Continue observing every rendered frame after the bounded receipt
        // budget is consumed. Capture readiness is derived from these values;
        // returning before this point made a small source-probe budget freeze
        // readiness at the initially empty terrain list.
        // The baseline receipt must describe a stable draw list, rather than
        // the first partially populated world-render frame.
        boolean readyCoverage = "java-opengl-draw".equals(stage)
                && sectionCount > 0
                && isSolidVisibleListStable(READY_VISIBLE_LIST_FRAMES, 1);
        boolean writeReadyEvent = readyCoverage
                && READY_VISIBLE_LIST_EVENTS.incrementAndGet() <= MAX_READY_VISIBLE_LIST_EVENTS;
        // Snapshot the actual latest draw even after the startup log budget is
        // exhausted. The capture receipt must not replay an early partial list.
        if ("java-opengl-draw".equals(stage)) {
            recordVisibleCoverage(stage, layer, renderLists, cameraX, cameraY, cameraZ,
                    viewportWidth, viewportHeight, writeEvent);
        }
        if (!writeEvent && !writeReadyEvent) {
            return;
        }
        // A bounded startup trace alone cannot certify the settled terrain
        // domain. Retain the later stable baseline observation too; this is
        // capture-only and cannot affect Frozen rendering behavior.
        if (writeEvent || writeReadyEvent) {
            String backend = backendName();

        StringBuilder json = new StringBuilder(2048);
        json.append("{");
        appendField(json, "schema", "mattmc-static-terrain-parity-visible-list-v1").append(", ");
        appendField(json, "eventIndex", eventIndex).append(", ");
        appendField(json, "backend", backend).append(", ");
        appendField(json, "stage", stage).append(", ");
        appendField(json, "layer", layer).append(", ");
        appendField(json, "gameTime", gameTime).append(", ");
        appendField(json, "nanoTime", System.nanoTime()).append(", ");
        json.append("\"camera\": { ");
        appendField(json, "x", cameraX).append(", ");
        appendField(json, "y", cameraY).append(", ");
        appendField(json, "z", cameraZ);
        json.append(" }, ");
        json.append("\"viewport\": { ");
        appendField(json, "width", viewportWidth).append(", ");
        appendField(json, "height", viewportHeight);
        json.append(" }, ");
        appendField(json, "regionCount", regionCount).append(", ");
        appendField(json, "visibleSectionCount", sectionCount).append(", ");
        appendField(json, "visibleSectionHash", String.format(Locale.ROOT, "%016x", hash)).append(", ");
        appendField(json, "orderedSectionHash", String.format(Locale.ROOT, "%016x", orderedHash)).append(", ");
        json.append("\"samples\": ").append(samples);
        json.append("}\n");

        try {
            writeLine(json.toString());
        } catch (IOException ignored) {
            // Diagnostics must never alter render behavior.
        }

        if (writeEvent) {
            if (!"java-opengl-draw".equals(stage)) {
                recordVisibleCoverage(stage, layer, renderLists, cameraX, cameraY, cameraZ, viewportWidth, viewportHeight, true);
            }
        }
        }
        if (readyCoverage) {
            recordVisibleCoverage("java-opengl-draw-ready", layer, renderLists, cameraX, cameraY, cameraZ, viewportWidth, viewportHeight, true);
        }
    }

    public static void recordRustStaticTerrainExecution(
            long sectionKey,
            String layer,
            long meshKey,
            long meshGeneration,
            long visibleGeneration,
            long contentHash,
            int vertexCount,
            int indexCount,
            int indexType,
            int primitiveCount,
            int sectionCount,
            int sectionOriginX,
            int sectionOriginY,
            int sectionOriginZ,
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
            long gameplayFrameId,
            long rustEnqueueFrameId
    ) {
        if (!ENABLED) {
            return;
        }
        String normalizedLayer = normalizeLayer(layer);
        MeshCoverage coverage = new MeshCoverage(
                normalizedLayer,
                meshGeneration,
                meshGeneration,
                meshKey,
                contentHash,
                vertexCount,
                indexCount,
                indexType,
                primitiveCount,
                sectionCount,
                sectionOriginX,
                sectionOriginY,
                sectionOriginZ,
                localMinX,
                localMinY,
                localMinZ,
                localMaxX,
                localMaxY,
                localMaxZ,
                uvMinU,
                uvMinV,
                uvMaxU,
                uvMaxV,
                true,
                materialIdentity(normalizedLayer),
                textureIdentity(normalizedLayer),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                "",
                "rust-static-terrain-asset"
        );
        writeCoverageEvent(
                "rust-vulkan-executed",
                normalizedLayer,
                gameplayFrameId,
                0,
                0,
                0.0D,
                0.0D,
                0.0D,
                0,
                0,
                1,
                vertexCount,
                indexCount,
                primitiveCount,
                0,
                1,
                records -> appendCoverageRecord(
                        records,
                        sectionKey,
                        sectionOriginX >> 4,
                        sectionOriginY >> 4,
                        sectionOriginZ >> 4,
                        sectionOriginX,
                        sectionOriginY,
                        sectionOriginZ,
                        0,
                        false,
                        coverage,
                        true,
                        "rust-vulkan-static-terrain-submit",
                        visibleGeneration,
                        rustEnqueueFrameId
                )
        );
    }

    public static boolean isSolidVisibleListStable(int requiredFrames, int minimumSections) {
        if (!ENABLED) {
            return true;
        }
        // Deterministic parity captures intentionally pin game time.  Readiness
        // must therefore be based on consecutive rendered frames with the same
        // visible terrain list, rather than on advancing simulation ticks.
        return latestSolidSectionCount >= minimumSections && stableSolidFrames >= Math.max(1, requiredFrames);
    }

    public static String solidVisibleListSummary() {
        return "sections=" + latestSolidSectionCount
                + ",hash=" + String.format(Locale.ROOT, "%016x", latestSolidHash)
                + ",stableFrames=" + stableSolidFrames
                + ",readyFrames=" + stableSolidFrames
                + ",gameTime=" + latestSolidGameTime;
    }

    /** Observational-only semantic transform trace for one selected terrain section. */
    public static void recordTransformProbe(
            String stage,
            String layer,
            double cameraX,
            double cameraY,
            double cameraZ,
            double yaw,
            double pitch,
            Matrix4fc view,
            Matrix4fc projection,
            int viewportWidth,
            int viewportHeight,
            boolean vulkanZeroToOneDepth
    ) {
        if (!ENABLED || TRANSFORM_TRACE_SECTION == Long.MIN_VALUE || view == null || projection == null) {
            return;
        }
        MeshCoverage coverage = SOURCE_MESHES.get(new CoverageKey(TRANSFORM_TRACE_SECTION, normalizeLayer(layer)));
        if (coverage == null || !coverage.boundsValid() || coverage.primitiveCount() <= 0) {
            return;
        }
        int eventIndex = TRANSFORM_TRACE_EVENTS.incrementAndGet();
        if (eventIndex > MAX_TRANSFORM_TRACE_EVENTS) {
            return;
        }
        Matrix4f model = new Matrix4f().translation(
                (float) (coverage.sectionOriginX() - cameraX),
                (float) (coverage.sectionOriginY() - cameraY),
                (float) (coverage.sectionOriginZ() - cameraZ)
        );
        Matrix4f viewCopy = new Matrix4f(view);
        Matrix4f projectionCopy = new Matrix4f(projection);
        Matrix4f viewProjection = new Matrix4f(projectionCopy).mul(viewCopy);
        float[][] points = {
                {coverage.localMinX(), coverage.localMinY(), coverage.localMinZ()},
                {coverage.localMaxX(), coverage.localMinY(), coverage.localMinZ()},
                {coverage.localMinX(), coverage.localMaxY(), coverage.localMinZ()},
                {coverage.localMinX(), coverage.localMinY(), coverage.localMaxZ()},
                {coverage.localMaxX(), coverage.localMaxY(), coverage.localMaxZ()}
        };
        String[] names = {"min", "max_x", "max_y", "max_z", "max"};
        try {
            StringBuilder json = new StringBuilder(8192);
            json.append("{");
            appendField(json, "schema", "mattmc-static-terrain-transform-probe-v1").append(", ");
            appendField(json, "eventIndex", eventIndex).append(", ");
            appendField(json, "backend", backendName()).append(", ");
            appendField(json, "stage", stage).append(", ");
            appendField(json, "layer", normalizeLayer(layer)).append(", ");
            appendField(json, "sectionKey", TRANSFORM_TRACE_SECTION).append(", ");
            appendField(json, "gameTime", Minecraft.getInstance().level == null ? -1L : Minecraft.getInstance().level.getGameTime()).append(", ");
            json.append("\"camera\":{");
            appendField(json, "x", cameraX).append(", ");
            appendField(json, "y", cameraY).append(", ");
            appendField(json, "z", cameraZ).append(", ");
            appendField(json, "yaw", yaw).append(", ");
            appendField(json, "pitch", pitch);
            json.append("}, \"viewport\":{");
            appendField(json, "width", viewportWidth).append(", ");
            appendField(json, "height", viewportHeight);
            json.append("}, \"conventions\":{");
            appendField(json, "matrixLayout", "column-major").append(", ");
            appendField(json, "worldHandedness", "minecraft-right-handed").append(", ");
            appendField(json, "inputClipDepth", "negative-one-to-one").append(", ");
            appendField(json, "backendClipDepth", vulkanZeroToOneDepth ? "zero-to-one" : "negative-one-to-one").append(", ");
            appendField(json, "screenOrigin", "top-left");
            json.append("}, \"projectionDerived\":{");
            appendField(json, "verticalFovDegrees", (float) Math.toDegrees(2.0 * Math.atan(1.0 / projectionCopy.m11()))).append(", ");
            appendField(json, "aspect", projectionCopy.m11() / projectionCopy.m00()).append(", ");
            appendField(json, "near", projectionCopy.m32() / (projectionCopy.m22() - 1.0F)).append(", ");
            appendField(json, "far", projectionCopy.m32() / (projectionCopy.m22() + 1.0F));
            json.append("}, \"matrices\":{");
            appendMatrix(json, "model", model).append(", ");
            appendMatrix(json, "view", viewCopy).append(", ");
            appendMatrix(json, "projection", projectionCopy).append(", ");
            appendMatrix(json, "viewProjection", viewProjection);
            json.append("}, \"probes\":[");
            for (int i = 0; i < points.length; i++) {
                if (i > 0) {
                    json.append(",");
                }
                Vector4f local = new Vector4f(points[i][0], points[i][1], points[i][2], 1.0F);
                Vector4f relative = model.transform(new Vector4f(local));
                Vector4f viewSpace = viewCopy.transform(new Vector4f(relative));
                Vector4f clip = projectionCopy.transform(new Vector4f(viewSpace));
                float reciprocalW = Math.abs(clip.w()) > 1.0E-6F ? 1.0F / clip.w() : Float.NaN;
                float ndcX = clip.x() * reciprocalW;
                float ndcY = clip.y() * reciprocalW;
                float ndcZ = clip.z() * reciprocalW;
                float depth = vulkanZeroToOneDepth ? ndcZ * 0.5F + 0.5F : ndcZ;
                float screenX = (ndcX * 0.5F + 0.5F) * viewportWidth;
                float screenY = (0.5F - ndcY * 0.5F) * viewportHeight;
                json.append("{");
                appendField(json, "name", names[i]).append(", \"local\":");
                appendVector(json, local).append(", \"world\":[");
                appendFloat(json, coverage.sectionOriginX() + local.x()).append(",");
                appendFloat(json, coverage.sectionOriginY() + local.y()).append(",");
                appendFloat(json, coverage.sectionOriginZ() + local.z()).append("], \"relative\":");
                appendVector(json, relative).append(", \"view\":");
                appendVector(json, viewSpace).append(", \"clip\":");
                appendVector(json, clip).append(", \"ndc\":[");
                appendFloat(json, ndcX).append(",");
                appendFloat(json, ndcY).append(",");
                appendFloat(json, ndcZ).append("], \"backendDepth\":");
                appendFloat(json, depth).append(", \"screen\":[");
                appendFloat(json, screenX).append(",");
                appendFloat(json, screenY).append("]}");
            }
            json.append("]}\n");
            writeLine(json.toString());
        } catch (IOException ignored) {
            // Diagnostics must never alter render behavior.
        }
    }

    /** Observes semantic source attributes only at the normal, non-shadow terrain pass. */
    public static void recordAppearanceSourceProbe(String stage, String layer) {
        if (!ENABLED || APPEARANCE_TRACE_SECTION == Long.MIN_VALUE) {
            return;
        }
        String normalizedLayer = normalizeLayer(layer);
        AppearanceSource source = SOURCE_APPEARANCES.get(new CoverageKey(APPEARANCE_TRACE_SECTION, normalizedLayer));
        MeshCoverage coverage = SOURCE_MESHES.get(new CoverageKey(APPEARANCE_TRACE_SECTION, normalizedLayer));
        if (source == null || coverage == null) {
            return;
        }
        int eventIndex = APPEARANCE_TRACE_EVENTS.incrementAndGet();
        if (eventIndex > 8) {
            return;
        }
        try {
            StringBuilder json = new StringBuilder(8192);
            json.append("{");
            appendField(json, "schema", "mattmc-static-terrain-appearance-source-v1").append(", ");
            appendField(json, "eventIndex", eventIndex).append(", ");
            appendField(json, "backend", backendName()).append(", ");
            appendField(json, "stage", stage).append(", ");
            appendField(json, "layer", normalizedLayer).append(", ");
            appendField(json, "sectionKey", APPEARANCE_TRACE_SECTION).append(", ");
            appendField(json, "sourceGeneration", coverage.sourceGeneration()).append(", ");
            appendField(json, "meshKey", String.format(Locale.ROOT, "%016x", coverage.meshKey())).append(", ");
            appendField(json, "meshGeneration", coverage.meshGeneration()).append(", ");
            appendField(json, "gameTime", Minecraft.getInstance().level == null ? -1L : Minecraft.getInstance().level.getGameTime()).append(", ");
            appendField(json, "vertexStride", source.vertexStride()).append(", ");
            appendField(json, "separateAo", source.separateAo() ? 1 : 0).append(", ");
            appendField(json, "materialIdentity", coverage.materialIdentity()).append(", ");
            appendField(json, "textureIdentity", coverage.textureIdentity()).append(", ");
            json.append("\"samples\":[");
            appendAppearanceSamples(json, source.samples());
            json.append("]}\n");
            writeLine(json.toString());
        } catch (IOException ignored) {
            // Observability must never change the Java renderer.
        }
    }

    /** Observes the selected section's uploaded bytes while its normal draw VAO is still bound. */
    public static void recordOpenGlTerrainVertices(net.sodium.client.render.chunk.region.RenderRegion region) {
        if (!ENABLED || APPEARANCE_TRACE_SECTION == Long.MIN_VALUE
                || !Boolean.getBoolean("mattmc.dev.staticTerrainParityDiagnostics.openGlBindings")) return;
        RenderSection selected = null;
        for (int i = 0; i < net.sodium.client.render.chunk.region.RenderRegion.REGION_SIZE; i++) {
            RenderSection candidate = region.getSection(i);
            if (candidate != null && candidate.getPosition().asLong() == APPEARANCE_TRACE_SECTION) {
                selected = candidate;
                break;
            }
        }
        if (selected == null || OPENGL_VERTEX_EVENTS.incrementAndGet() > 4) return;
        var storage = region.getStorage(DefaultTerrainRenderPasses.SOLID);
        long data = storage.getDataPointer(selected.getSectionIndex());
        long base = net.sodium.client.render.chunk.data.SectionRenderDataUnsafe.getBaseVertex(data);
        long count = 0;
        for (int i = 0; i < 7; i++) count += net.sodium.client.render.chunk.data.SectionRenderDataUnsafe.getVertexCount(data, i);
        int stride = org.lwjgl.opengl.GL20.glGetVertexAttribi(3, org.lwjgl.opengl.GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE);
        int buffer = org.lwjgl.opengl.GL20.glGetVertexAttribi(3, org.lwjgl.opengl.GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
        if (stride != 20 || buffer == 0 || count <= 0 || count > 512) return;
        int previous = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER);
        try {
            StringBuilder json = new StringBuilder(24000);
            json.append("{");
            appendField(json, "schema", "mattmc-opengl-terrain-uploaded-vertices-v1").append(", ");
            appendField(json, "sectionKey", APPEARANCE_TRACE_SECTION).append(", ");
            appendField(json, "baseVertex", base).append(", ");
            appendField(json, "vertexCount", count).append(", ");
            appendField(json, "buffer", buffer);
            json.append(", \"attributes\":[");
            for (int index = 0; index < 4; index++) {
                if (index != 0) json.append(',');
                json.append('{');
                appendField(json, "index", index);
                for (int parameter : new int[]{org.lwjgl.opengl.GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED,
                        org.lwjgl.opengl.GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE, org.lwjgl.opengl.GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE,
                        org.lwjgl.opengl.GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED, org.lwjgl.opengl.GL30.GL_VERTEX_ATTRIB_ARRAY_INTEGER,
                        org.lwjgl.opengl.GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE, org.lwjgl.opengl.GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING}) {
                    json.append(',');
                    appendField(json, "parameter" + parameter, org.lwjgl.opengl.GL20.glGetVertexAttribi(index, parameter));
                }
                var pointer = org.lwjgl.BufferUtils.createPointerBuffer(1);
                org.lwjgl.opengl.GL20.glGetVertexAttribPointerv(index, org.lwjgl.opengl.GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER, pointer);
                json.append(',');
                appendField(json, "offset", pointer.get(0));
                json.append('}');
            }
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER, buffer);
            ByteBuffer vertices = org.lwjgl.BufferUtils.createByteBuffer((int) count * stride);
            org.lwjgl.opengl.GL15.glGetBufferSubData(org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER, base * stride, vertices);
            json.append("],\"vertexHex\":\"");
            for (int i = 0; i < vertices.capacity(); i++) {
                int value = Byte.toUnsignedInt(vertices.get(i));
                json.append(Character.forDigit(value >>> 4, 16)).append(Character.forDigit(value & 15, 16));
            }
            json.append("\"}\n");
            writeLine(json.toString());
        } catch (IOException ignored) {
            // Readback never supplies rendering inputs or changes the draw state.
        } finally {
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER, previous);
        }
    }

    /** Bounded, read-only observation of the shader actually used by the OpenGL terrain pass. */
    public static void recordOpenGlTerrainBindings() {
        if (!ENABLED || APPEARANCE_TRACE_SECTION == Long.MIN_VALUE
                || !Boolean.getBoolean("mattmc.dev.staticTerrainParityDiagnostics.openGlBindings")
                || !SOURCE_APPEARANCES.containsKey(new CoverageKey(APPEARANCE_TRACE_SECTION, "solid"))) {
            return;
        }
        observeOpenGlFrameBinding();
        if (OPENGL_BINDING_EVENTS.incrementAndGet() > 8) return;
        int activeTexture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        try {
            int program = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
            StringBuilder json = new StringBuilder(4096);
            json.append("{");
            appendField(json, "schema", "mattmc-opengl-terrain-bindings-v1").append(", ");
            appendField(json, "program", program).append(", ");
            appendField(json, "framebufferSrgb", org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB) ? 1 : 0);
            for (String name : new String[]{"u_FogColor", "u_EnvironmentFog", "u_RenderFog"}) {
                int location = org.lwjgl.opengl.GL20.glGetUniformLocation(program, name);
                json.append(", \"").append(name).append("\":[");
                if (location >= 0) {
                    float[] values = new float[4];
                    org.lwjgl.opengl.GL20.glGetUniformfv(program, location, values);
                    for (int i = 0; i < (name.equals("u_FogColor") ? 4 : 2); i++) {
                        if (i != 0) json.append(',');
                        appendFloat(json, values[i]);
                    }
                }
                json.append(']');
            }
            for (String name : new String[]{"u_BlockTex", "u_LightTex"}) {
                int location = org.lwjgl.opengl.GL20.glGetUniformLocation(program, name);
                if (location < 0) continue;
                int unit = org.lwjgl.opengl.GL20.glGetUniformi(program, location);
                org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + unit);
                int target = org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
                int texture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
                int sampler = org.lwjgl.opengl.GL30.glGetIntegeri(org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING, unit);
                int width = org.lwjgl.opengl.GL11.glGetTexLevelParameteri(target, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
                int height = org.lwjgl.opengl.GL11.glGetTexLevelParameteri(target, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
                json.append(", \"").append(name).append("\":{");
                appendField(json, "unit", unit).append(", ");
                appendField(json, "texture", texture).append(", ");
                appendField(json, "sampler", sampler).append(", ");
                appendField(json, "width", width).append(", ");
                appendField(json, "height", height).append(", ");
                appendField(json, "internalFormat", org.lwjgl.opengl.GL11.glGetTexLevelParameteri(target, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_INTERNAL_FORMAT));
                for (int parameter : new int[]{org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
                        org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T}) {
                    json.append(", ");
                    appendField(json, "parameter" + parameter, sampler == 0
                            ? org.lwjgl.opengl.GL11.glGetTexParameteri(target, parameter)
                            : org.lwjgl.opengl.GL33.glGetSamplerParameteri(sampler, parameter));
                }
                // Never change pack state or read an unbounded image. Respect the
                // existing row stride/skips (atlas diagnostics may leave a row length).
                int packBuffer = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING);
                int rowLength = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_PACK_ROW_LENGTH);
                int skipPixels = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_PACK_SKIP_PIXELS);
                int skipRows = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_PACK_SKIP_ROWS);
                int alignment = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT);
                long rowBytes = ((long) (rowLength == 0 ? width : rowLength) * 4 + alignment - 1) / alignment * alignment;
                long firstByte = skipRows * rowBytes + (long) skipPixels * 4;
                long readBytes = firstByte + Math.max(0, height - 1) * rowBytes + (long) width * 4;
                json.append(", ");
                appendField(json, "packBuffer", packBuffer).append(", ");
                appendField(json, "packRowLength", rowLength).append(", ");
                appendField(json, "packReadBytes", readBytes);
                if (name.equals("u_LightTex") && width == 16 && height == 16
                        && packBuffer == 0 && rowBytes >= 64 && readBytes > 0 && readBytes <= 1024 * 1024) {
                    ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer((int) readBytes);
                    org.lwjgl.opengl.GL11.glGetTexImage(target, 0, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, pixels);
                    json.append(", \"rgbaHex\":\"");
                    for (int i = 0; i < 1024; i++) {
                        int value = Byte.toUnsignedInt(pixels.get((int) (firstByte + i / 64 * rowBytes + i % 64)));
                        json.append(Character.forDigit(value >>> 4, 16)).append(Character.forDigit(value & 15, 16));
                    }
                    json.append('"');
                }
                json.append('}');
            }
            json.append("}\n");
            writeLine(json.toString());
        } catch (IOException ignored) {
            // Observability must never change the Java renderer.
        } finally {
            org.lwjgl.opengl.GL13.glActiveTexture(activeTexture);
        }
    }

    /** Keeps only a tiny read-only receipt; no texture/buffer readback per frame. */
    private static void observeOpenGlFrameBinding() {
        long frameIndex = net.minecraft.client.dev.DeterministicCameraCapture.currentRenderingFrameIndexForDiagnostics();
        if (frameIndex < 1) return;
        int previousUnit = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        try {
            int program = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
            int location = org.lwjgl.opengl.GL20.glGetUniformLocation(program, "u_LightTex");
            if (location < 0) return;
            int unit = org.lwjgl.opengl.GL20.glGetUniformi(program, location);
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + unit);
            int actual = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            int expected = net.vulkanic.VulkanicCoreAPI.textureId(Minecraft.getInstance().gameRenderer.lightTexture().getTextureView());
            StringBuilder json = new StringBuilder(256);
            json.append('{');
            appendField(json, "renderedFrameIndex", frameIndex).append(',');
            appendField(json, "program", program).append(',');
            appendField(json, "lightmapUnit", unit).append(',');
            appendField(json, "boundLightmap", actual).append(',');
            appendField(json, "expectedLightmap", expected);
            json.append('}');
            OPENGL_FRAME_BINDING.record(frameIndex, json.toString());
        } finally {
            org.lwjgl.opengl.GL13.glActiveTexture(previousUnit);
        }
    }

    /** Called after render, at the exact external screenshot request boundary. */
    public static void recordOpenGlCapturedFrameBinding(long capturedFrameIndex) {
        if (!ENABLED || !Boolean.getBoolean("mattmc.dev.staticTerrainParityDiagnostics.openGlBindings")) return;
        String observation = OPENGL_FRAME_BINDING.matching(capturedFrameIndex);
        StringBuilder json = new StringBuilder(512);
        json.append('{');
        appendField(json, "schema", "mattmc-opengl-terrain-capture-frame-v1").append(',');
        appendField(json, "capturedFrameIndex", capturedFrameIndex).append(',');
        appendField(json, "status", observation == null ? "matching-frame-unavailable" : "complete").append(',');
        json.append("\"observation\":").append(observation == null ? "null" : observation).append("}\n");
        try {
            writeLine(json.toString());
        } catch (IOException ignored) {
            // Diagnostics cannot change screenshot timing or rendering behavior.
        }
    }

    /** Prepares one semantic pixel probe before the terrain layer starts. */
    public static void prepareIrisTerrainFragmentProbe(
            String stage,
            String layer,
            double cameraX,
            double cameraY,
            double cameraZ,
            Matrix4fc view,
            Matrix4fc projection,
            int viewportWidth,
            int viewportHeight
    ) {
        if (!ENABLED || APPEARANCE_TRACE_SECTION == Long.MIN_VALUE || view == null || projection == null) {
            return;
        }
        AppearanceSource source = SOURCE_APPEARANCES.get(new CoverageKey(APPEARANCE_TRACE_SECTION, normalizeLayer(layer)));
        if (source == null || source.samples().length == 0 || viewportWidth < 3 || viewportHeight < 3) {
            return;
        }
        Matrix4f model = new Matrix4f().translation(
                (float) (SectionPos.x(APPEARANCE_TRACE_SECTION) * 16 - cameraX),
                (float) (SectionPos.y(APPEARANCE_TRACE_SECTION) * 16 - cameraY),
                (float) (SectionPos.z(APPEARANCE_TRACE_SECTION) * 16 - cameraZ)
        );
        Matrix4f viewCopy = new Matrix4f(view);
        Matrix4f projectionCopy = new Matrix4f(projection);
        AppearanceSample sample = null;
        int screenX = -1;
        int screenY = -1;
        for (AppearanceSample candidate : source.samples()) {
            Vector4f clip = projectionCopy.transform(viewCopy.transform(model.transform(new Vector4f(
                    candidate.worldX() - SectionPos.x(APPEARANCE_TRACE_SECTION) * 16,
                    candidate.worldY() - SectionPos.y(APPEARANCE_TRACE_SECTION) * 16,
                    candidate.worldZ() - SectionPos.z(APPEARANCE_TRACE_SECTION) * 16,
                    1.0F
            ))));
            if (Math.abs(clip.w()) < 1.0E-6F) {
                continue;
            }
            float ndcX = clip.x() / clip.w();
            float ndcY = clip.y() / clip.w();
            int candidateX = Math.round((ndcX * 0.5F + 0.5F) * (viewportWidth - 1));
            int candidateY = Math.round((0.5F - ndcY * 0.5F) * (viewportHeight - 1));
            if (candidateX >= 1 && candidateX < viewportWidth - 1 && candidateY >= 1 && candidateY < viewportHeight - 1) {
                sample = candidate;
                screenX = candidateX;
                screenY = candidateY;
                break;
            }
        }
        if (sample == null) {
            return;
        }
        PENDING_IRIS_FRAGMENT_PROBES.put(normalizeLayer(layer), new PendingIrisTerrainFragmentProbe(
                stage, normalizeLayer(layer), sample, screenX, screenY, viewportHeight
        ));
    }

    /**
     * Reads the prepared pixel while Sodium's normal terrain program and G-buffer
     * are still bound. The OpenGL readback itself remains backend-private.
     */
    public static void capturePreparedIrisTerrainFragmentProbe(String layer) {
        PendingIrisTerrainFragmentProbe pending = PENDING_IRIS_FRAGMENT_PROBES.remove(normalizeLayer(layer));
        if (pending == null) {
            return;
        }
        int eventIndex = APPEARANCE_FRAGMENT_TRACE_EVENTS.incrementAndGet();
        if (eventIndex > 4) {
            return;
        }
        try {
            VulkanicAPI.DrawFramebufferAttachmentProbeSnapshot probe =
                    VulkanicAPI.readDrawFramebufferAttachmentProbe(
                            pending.screenX(), pending.viewportHeight() - pending.screenY() - 1, 4
                    );
            StringBuilder attachments = new StringBuilder();
            for (VulkanicAPI.DrawFramebufferAttachmentProbe attachment : probe.attachments()) {
                if (attachments.length() > 0) {
                    attachments.append(',');
                }
                attachments.append("{\"drawBuffer\":").append(attachment.drawBuffer())
                        .append(",\"textureId\":").append(attachment.textureId())
                        .append(",\"centerRgba\":[")
                        .append(attachment.red()).append(',')
                        .append(attachment.green()).append(',')
                        .append(attachment.blue()).append(',')
                        .append(attachment.alpha()).append("]}");
            }
            StringBuilder json = new StringBuilder(2048);
            json.append("{");
            appendField(json, "schema", "mattmc-static-terrain-iris-fragment-output-v1").append(", ");
            appendField(json, "eventIndex", eventIndex).append(", ");
            appendField(json, "backend", backendName()).append(", ");
            appendField(json, "stage", pending.stage()).append(", ");
            appendField(json, "layer", pending.layer()).append(", ");
            appendField(json, "sectionKey", APPEARANCE_TRACE_SECTION).append(", ");
            appendField(json, "gameTime", Minecraft.getInstance().level == null ? -1L : Minecraft.getInstance().level.getGameTime()).append(", ");
            appendField(json, "framebuffer", probe.drawFramebuffer()).append(", ");
            appendField(json, "program", probe.currentProgram()).append(", ");
            json.append("\"screen\":[").append(pending.screenX()).append(',').append(pending.screenY()).append("], ");
            json.append("\"source\":{");
            appendField(json, "primitive", pending.sample().primitiveIndex()).append(", ");
            appendField(json, "vertex", pending.sample().vertexIndex()).append(", ");
            json.append("\"world\":[").append(String.format(Locale.ROOT, "%.6f,%.6f,%.6f", pending.sample().worldX(), pending.sample().worldY(), pending.sample().worldZ())).append("], ");
            json.append("\"uv\":[").append(String.format(Locale.ROOT, "%.6f,%.6f", pending.sample().u(), pending.sample().v())).append("], ");
            appendField(json, "ao", pending.sample().ao()).append(", ");
            appendField(json, "blockLight", pending.sample().blockLight()).append(", ");
            appendField(json, "skyLight", pending.sample().skyLight());
            json.append("}, \"attachments\":[").append(attachments).append("]}").append('\n');
            writeLine(json.toString());
        } catch (IOException | RuntimeException ignored) {
            // A diagnostic readback must not alter or fail the render path.
        }
    }

    /**
     * Observes raw light-cache inputs around one requested appearance probe. This
     * is intentionally a diagnostic seam: callers continue using their normal
     * light computation and the event contains no renderer state or handles.
     */
    public static void recordAppearanceLightInput(
            String source,
            int x,
            int y,
            int z,
            String blockIdentity,
            int lightWord
    ) {
        int[] target = APPEARANCE_TRACE_BLOCK;
        if (!ENABLED || target == null
                || Math.abs(x - target[0]) > 1
                || Math.abs(y - target[1]) > 1
                || Math.abs(z - target[2]) > 1) {
            return;
        }
        int eventIndex = APPEARANCE_LIGHT_TRACE_EVENTS.incrementAndGet();
        if (eventIndex > 64) {
            return;
        }
        try {
            StringBuilder json = new StringBuilder(384);
            json.append("{");
            appendField(json, "schema", "mattmc-static-terrain-appearance-light-input-v1").append(", ");
            appendField(json, "eventIndex", eventIndex).append(", ");
            appendField(json, "source", source).append(", ");
            appendField(json, "sectionKey", APPEARANCE_TRACE_SECTION).append(", ");
            appendField(json, "worldPosition", vector3(x, y, z)).append(", ");
            appendField(json, "blockIdentity", blockIdentity == null ? "unknown" : blockIdentity).append(", ");
            appendField(json, "rawLightWord", String.format(Locale.ROOT, "%08x", lightWord)).append(", ");
            appendField(json, "blockLight", lightWord & 0xf).append(", ");
            appendField(json, "skyLight", (lightWord >>> 4) & 0xf).append(", ");
            appendField(json, "luminance", (lightWord >>> 8) & 0xf).append(", ");
            appendField(json, "ao", ((lightWord >>> 12) & 0xffff) / 4096.0D);
            json.append("}\n");
            writeLine(json.toString());
        } catch (IOException ignored) {
            // Diagnostics must never alter render behavior.
        }
    }

    /** Observational light-layer provenance for the cross-repository fixture. */
    public static void recordAppearanceLightSnapshot(
            Level level,
            SectionPos section,
            LightLayer layer,
            DataLayer sourceLayer
    ) {
        if (!ENABLED || APPEARANCE_TRACE_SECTION == Long.MIN_VALUE
                || section == null || section.asLong() != APPEARANCE_TRACE_SECTION) {
            return;
        }
        int eventIndex = APPEARANCE_LIGHT_SNAPSHOT_EVENTS.incrementAndGet();
        if (eventIndex > 16) {
            return;
        }
        int[] target = APPEARANCE_TRACE_BLOCK;
        boolean targetInsideSection = target != null
                && SectionPos.blockToSectionCoord(target[0]) == section.getX()
                && SectionPos.blockToSectionCoord(target[1]) == section.getY()
                && SectionPos.blockToSectionCoord(target[2]) == section.getZ();
        int sampledLight = sourceLayer == null || !targetInsideSection ? -1
                : sourceLayer.get(target[0] & 15, target[1] & 15, target[2] & 15);
        if (sourceLayer != null) {
            appearanceLightSnapshotObserved = true;
            appearanceLightSnapshotMask |= 1 << layer.ordinal();
        }
        try {
            StringBuilder json = new StringBuilder(640);
            json.append("{");
            appendField(json, "schema", "mattmc-static-terrain-light-snapshot-v1").append(", ");
            appendField(json, "eventIndex", eventIndex).append(", ");
            appendField(json, "sectionKey", section.asLong()).append(", ");
            appendField(json, "sectionX", section.getX()).append(", ");
            appendField(json, "sectionY", section.getY()).append(", ");
            appendField(json, "sectionZ", section.getZ()).append(", ");
            appendField(json, "layer", layer.name().toLowerCase(Locale.ROOT)).append(", ");
            appendField(json, "sourceLayerPresent", sourceLayer != null).append(", ");
            appendField(json, "usingDefaultFallback", sourceLayer == null).append(", ");
            appendField(json, "targetInsideSection", targetInsideSection).append(", ");
            appendField(json, "sampledLight", sampledLight).append(", ");
            appendField(json, "gameTime", level == null ? -1L : level.getGameTime()).append(", ");
            appendField(json, "raining", level != null && level.isRaining()).append(", ");
            appendField(json, "thundering", level != null && level.isThundering()).append(", ");
            appendField(json, "dimensionHasSkyLight", level != null && level.dimensionType().hasSkyLight()).append(", ");
            appendField(json, "dimensionAmbientLight", level == null ? -1.0D : level.dimensionType().ambientLight());
            json.append("}\n");
            writeLine(json.toString());
        } catch (IOException ignored) {
            // Observational diagnostics must never alter Frozen rendering.
        }
    }

    /** Capture-only observation of the target chunk's normal light lifecycle. */
    public static void recordAppearanceLightLifecycle(Level level, String event, int chunkX, int chunkZ, int flags) {
        int[] target = APPEARANCE_TRACE_BLOCK;
        if (!ENABLED || target == null
                || SectionPos.blockToSectionCoord(target[0]) != chunkX
                || SectionPos.blockToSectionCoord(target[2]) != chunkZ) {
            return;
        }
        int eventIndex = APPEARANCE_LIGHT_SNAPSHOT_EVENTS.incrementAndGet();
        if (eventIndex > 16 || level == null) {
            return;
        }
        SectionPos section = SectionPos.of(target[0] >> 4, target[1] >> 4, target[2] >> 4);
        DataLayer block = level.getLightEngine().getLayerListener(LightLayer.BLOCK).getDataLayerData(section);
        DataLayer sky = level.getLightEngine().getLayerListener(LightLayer.SKY).getDataLayerData(section);
        try {
            StringBuilder json = new StringBuilder(512);
            json.append("{");
            appendField(json, "schema", "mattmc-static-terrain-light-lifecycle-v1").append(", ");
            appendField(json, "eventIndex", eventIndex).append(", ");
            appendField(json, "event", event).append(", ");
            appendField(json, "chunkX", chunkX).append(", ");
            appendField(json, "chunkZ", chunkZ).append(", ");
            appendField(json, "flags", flags).append(", ");
            appendField(json, "sectionKey", section.asLong()).append(", ");
            appendField(json, "blockLayerPresent", block != null).append(", ");
            appendField(json, "skyLayerPresent", sky != null).append(", ");
            appendField(json, "blockLight", block == null ? -1L : block.get(target[0] & 15, target[1] & 15, target[2] & 15)).append(", ");
            appendField(json, "skyLight", sky == null ? -1L : sky.get(target[0] & 15, target[1] & 15, target[2] & 15)).append(", ");
            appendField(json, "gameTime", level.getGameTime());
            json.append("}\n");
            writeLine(json.toString());
        } catch (IOException ignored) {
            // Diagnostics must never alter rendering behavior.
        }
    }

    private static void recordSourceMesh(ChunkBuildOutput output, TerrainRenderPass pass, String layer) {
        BuiltSectionMeshParts mesh = output.getMesh(pass);
        long sectionKey = output.render.getPosition().asLong();
        CoverageKey key = new CoverageKey(sectionKey, layer);
        if (mesh == null || mesh.getVertexData() == null || mesh.getVertexData().getLength() <= 0) {
            SOURCE_MESHES.remove(key);
            return;
        }
        MeshCoverage coverage = decodeSourceMesh(output, mesh, layer);
        SOURCE_MESHES.put(key, coverage);
		recordCompactLightingReceipt(output, mesh, layer);
        if (output.render.getPosition().asLong() == APPEARANCE_TRACE_SECTION) {
            SOURCE_APPEARANCES.put(key, decodeAppearanceSource(output, mesh, layer));
        }
        writeCoverageEvent(
                "source-mesh",
                layer,
                output.submitTime,
                output.render.getFlags(),
                output.info != null && output.info.animatedSprites != null ? output.info.animatedSprites.length : 0,
                0.0D,
                0.0D,
                0.0D,
                0,
                0,
                1,
                coverage.vertexCount(),
                coverage.indexCount(),
                coverage.primitiveCount(),
                0,
                0,
                records -> appendCoverageRecord(
                        records,
                        sectionKey,
                        output.render.getChunkX(),
                        output.render.getChunkY(),
                        output.render.getChunkZ(),
                        output.render.getOriginX(),
                        output.render.getOriginY(),
                        output.render.getOriginZ(),
                        output.render.getFlags(),
                        output.info != null && output.info.animatedSprites != null,
                        coverage,
                        false,
                        "source-build-output",
                        0L,
                        0L
                )
        );
    }

	/**
	 * Bounded pre-render receipt for the two compact vertex fields that control
	 * vanilla terrain brightness. This deliberately observes CPU mesh bytes
	 * before either backend owns a resource or issues a draw; it cannot affect
	 * Frozen rendering behavior.
	 */
	private static void recordCompactLightingReceipt(ChunkBuildOutput output, BuiltSectionMeshParts mesh, String layer) {
		if (!ENABLED || output == null || output.render == null || mesh == null || mesh.getVertexData() == null) {
			return;
		}
		int eventIndex = COMPACT_LIGHTING_EVENTS.incrementAndGet();
		if (eventIndex > MAX_COMPACT_LIGHTING_EVENTS) {
			return;
		}
		try {
			ByteBuffer buffer = mesh.getVertexData().getDirectBuffer().duplicate().order(ByteOrder.nativeOrder());
			int stride = activeTerrainVertexStride();
			if (stride < COMPACT_PREFIX_STRIDE) {
				return;
			}
			int requestedVertices = 0;
			for (int index = 0; index + 1 < mesh.getVertexSegments().length; index += 2) {
				requestedVertices += Math.max(0, mesh.getVertexSegments()[index]);
			}
			int vertexCount = Math.min(requestedVertices, buffer.remaining() / stride);
			CRC32 colorCrc = new CRC32();
			CRC32 lightCrc = new CRC32();
			int[] unorderedColors = new int[vertexCount];
			int[] unorderedLights = new int[vertexCount];
			Map<Integer, Integer> compactColorHistogram = new java.util.HashMap<>();
			long compactBlueSum = 0L;
			long compactGreenSum = 0L;
			long compactRedSum = 0L;
			long compactAlphaSum = 0L;
			StringBuilder compactColorTrace = new StringBuilder();
			int compactColorTraceSamples = 0;
			boolean traceCompactColors = output.render.getPosition().asLong() == APPEARANCE_TRACE_SECTION
					&& COMPACT_COLOR_TRACE_VALUES.length > 0;
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				int offset = vertex * stride;
				int color = buffer.getInt(offset + COLOR_OFFSET);
				int light = buffer.getInt(offset + LIGHT_MATERIAL_OFFSET);
				updateCrcInt(colorCrc, color);
				updateCrcInt(lightCrc, light);
				unorderedColors[vertex] = color;
				unorderedLights[vertex] = light;
				compactColorHistogram.merge(color, 1, Integer::sum);
				compactBlueSum += color & 0xff;
				compactGreenSum += (color >>> 8) & 0xff;
				compactRedSum += (color >>> 16) & 0xff;
				compactAlphaSum += (color >>> 24) & 0xff;
				if (traceCompactColors && compactColorTraceSamples < MAX_COMPACT_COLOR_TRACE_SAMPLES
						&& isCompactColorTraceValue(color)) {
					if (compactColorTraceSamples++ > 0) {
						compactColorTrace.append('|');
					}
					// Position, texture, and light/material are an exact compact
					// geometry key across the paired CPU meshes.
					compactColorTrace.append(String.format(Locale.ROOT, "%08x:%08x:%08x:%08x:%08x",
							buffer.getInt(offset + POSITION_OFFSET),
							buffer.getInt(offset + POSITION_OFFSET + Integer.BYTES),
							buffer.getInt(offset + TEXTURE_OFFSET),
							light,
							color));
				}
			}
			// Sodium is allowed to order independent quads differently when the
			// CPU source is rebuilt. Preserve the ordered receipts for local
			// debugging, but make the cross-producer equality witness a multiset.
			// This diagnostic never participates in mesh admission or rendering.
			java.util.Arrays.sort(unorderedColors);
			java.util.Arrays.sort(unorderedLights);
			CRC32 unorderedColorCrc = new CRC32();
			CRC32 unorderedLightCrc = new CRC32();
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				updateCrcInt(unorderedColorCrc, unorderedColors[vertex]);
				updateCrcInt(unorderedLightCrc, unorderedLights[vertex]);
			}
			StringBuilder line = new StringBuilder(384);
			line.append("{");
			appendField(line, "schema", "mattmc-static-terrain-compact-lighting-v1").append(", ");
			appendField(line, "eventIndex", eventIndex).append(", ");
			appendField(line, "sectionKey", output.render.getPosition().asLong()).append(", ");
			appendField(line, "layer", layer).append(", ");
			appendField(line, "vertexCount", vertexCount).append(", ");
			appendField(line, "compactColorHash", String.format(Locale.ROOT, "%08x", colorCrc.getValue())).append(", ");
			appendField(line, "packedLightMaterialHash", String.format(Locale.ROOT, "%08x", lightCrc.getValue())).append(", ");
			appendField(line, "unorderedCompactColorHash", String.format(Locale.ROOT, "%08x", unorderedColorCrc.getValue())).append(", ");
			appendField(line, "unorderedPackedLightMaterialHash", String.format(Locale.ROOT, "%08x", unorderedLightCrc.getValue())).append(", ");
			// Channel sums are diagnostic-only. They distinguish a tint-channel
			// error from a uniform AO/directional-shade error without sampling or
			// influencing a renderer-visible buffer.
			appendField(line, "compactColorChannelSums", "[" + compactBlueSum + "," + compactGreenSum + "," + compactRedSum + "," + compactAlphaSum + "]");
			String colorHistogram = compactColorHistogram.entrySet().stream()
					.sorted((left, right) -> {
						int byCount = Integer.compare(right.getValue(), left.getValue());
						return byCount != 0 ? byCount : Integer.compareUnsigned(left.getKey(), right.getKey());
					})
					.limit(16)
					.map(entry -> String.format(Locale.ROOT, "%08x:%d", entry.getKey(), entry.getValue()))
					.collect(java.util.stream.Collectors.joining("|"));
			line.append(", ");
			appendField(line, "compactColorHistogramTop16", colorHistogram);
			if (traceCompactColors) {
				line.append(", ");
				appendField(line, "compactColorTrace", compactColorTrace.toString());
			}
			line.append("}\n");
			writeLine(line.toString());
		} catch (IOException ignored) {
			// Diagnostics must not alter mesh construction or rendering.
		}
	}

    private static MeshCoverage decodeSourceMesh(ChunkBuildOutput output, BuiltSectionMeshParts mesh, String layer) {
        ByteBuffer buffer = mesh.getVertexData().getDirectBuffer().duplicate().order(ByteOrder.nativeOrder());
        int vertexStride = activeTerrainVertexStride();
        int[] vertexSegments = mesh.getVertexSegments();
        int vertexCount = 0;
        int sectionCount = 0;
        for (int i = 0; i < vertexSegments.length; i += 2) {
            int count = vertexSegments[i];
            if (count > 0) {
                vertexCount += count;
                sectionCount++;
            }
        }
        int bufferVertexCapacity = vertexStride <= 0 ? 0 : buffer.remaining() / Math.max(1, vertexStride);
        int safeVertexCount = Math.max(0, Math.min(vertexCount, bufferVertexCapacity));
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
        CRC32 crc = new CRC32();
        updateCrcLong(crc, output.render.getPosition().asLong());
        updateCrcString(crc, layer);
        updateCrcInt(crc, vertexStride);
        updateCrcInt(crc, vertexCount);
        updateCrcInt(crc, sectionCount);
        for (int value : vertexSegments) {
            updateCrcInt(crc, value);
        }
        boolean valid = vertexStride >= COMPACT_PREFIX_STRIDE && safeVertexCount > 0;
        if (valid) {
            for (int vertexIndex = 0; vertexIndex < safeVertexCount; vertexIndex++) {
                int offset = vertexIndex * vertexStride;
                int positionHi = buffer.getInt(offset + POSITION_OFFSET);
                int positionLo = buffer.getInt(offset + POSITION_OFFSET + 4);
                // These are part of Sodium's compact mesh payload, not merely
                // presentation metadata: RGB contains baked tint/AO and this
                // word carries the packed block/sky-light coordinates used by
                // the terrain shader. Include them in the diagnostic receipt
                // so a cross-route hash can actually detect a lighting-data
                // mismatch before either renderer draws the mesh.
                int compactColor = buffer.getInt(offset + COLOR_OFFSET);
                int texture = buffer.getInt(offset + TEXTURE_OFFSET);
                int lightMaterial = buffer.getInt(offset + LIGHT_MATERIAL_OFFSET);
                float x = decodePosition(positionHi, positionLo, 0);
                float y = decodePosition(positionHi, positionLo, 1);
                float z = decodePosition(positionHi, positionLo, 2);
                float u = decodeTextureForBlockAtlas(texture & 0xffff, true);
                float v = decodeTextureForBlockAtlas((texture >>> 16) & 0xffff, false);
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
                updateCrcInt(crc, positionHi);
                updateCrcInt(crc, positionLo);
                updateCrcInt(crc, compactColor);
                updateCrcInt(crc, texture);
                updateCrcInt(crc, lightMaterial);
            }
        }
        if (!valid) {
            minX = minY = minZ = maxX = maxY = maxZ = minU = minV = maxU = maxV = 0.0F;
        }
        int primitiveCount = Math.max(0, vertexCount / 4);
        int indexCount = primitiveCount * 6;
        long contentHash = crc.getValue();
        return new MeshCoverage(
                layer,
                output.submitTime,
                contentHash,
                semanticMeshKey(output.render.getPosition().asLong(), layer),
                contentHash,
                vertexCount,
                indexCount,
                INDEX_TYPE_U16,
                primitiveCount,
                sectionCount,
                output.render.getOriginX(),
                output.render.getOriginY(),
                output.render.getOriginZ(),
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                minU,
                minV,
                maxU,
                maxV,
                valid,
                materialIdentity(layer),
                textureIdentity(layer),
                vertexStride,
                buffer.remaining(),
                bufferVertexCapacity,
                0,
                0,
                0,
                0,
                0,
                animatedSpriteIdentities(output),
                "sodium-source-mesh"
        );
    }

    private static AppearanceSource decodeAppearanceSource(ChunkBuildOutput output, BuiltSectionMeshParts mesh, String layer) {
        ByteBuffer buffer = mesh.getVertexData().getDirectBuffer().duplicate().order(ByteOrder.nativeOrder());
        int stride = activeTerrainVertexStride();
        int vertexCapacity = stride <= 0 ? 0 : buffer.remaining() / stride;
        int[] segments = mesh.getVertexSegments();
        int vertices = 0;
        for (int i = 0; i + 1 < segments.length; i += 2) {
            vertices += Math.max(0, segments[i]);
        }
        // Retain a bounded, representative prefix solely so the post-draw
        // observer can select an in-viewport primitive without touching the
        // renderer's vertex stream or traversal.
        int limit = Math.min(Math.min(vertices, vertexCapacity), MAX_SAMPLES);
        AppearanceSample[] samples = new AppearanceSample[limit];
        boolean separateAo = usesSeparateAo();
        int segment = 0;
        int segmentStart = 0;
        for (int index = 0; index < limit; index++) {
            while (segment + 1 < segments.length && index >= segmentStart + Math.max(0, segments[segment])) {
                segmentStart += Math.max(0, segments[segment]);
                segment += 2;
            }
            int offset = index * stride;
            int compactColor = buffer.getInt(offset + COLOR_OFFSET);
            int texture = buffer.getInt(offset + TEXTURE_OFFSET);
            int lightMaterial = buffer.getInt(offset + LIGHT_MATERIAL_OFFSET);
            samples[index] = new AppearanceSample(
                    index, index / 4, segment + 1 < segments.length ? segments[segment + 1] : -1,
                    output.render.getOriginX() + decodePosition(buffer.getInt(offset), buffer.getInt(offset + 4), 0),
                    output.render.getOriginY() + decodePosition(buffer.getInt(offset), buffer.getInt(offset + 4), 1),
                    output.render.getOriginZ() + decodePosition(buffer.getInt(offset), buffer.getInt(offset + 4), 2),
                    decodeTexture(texture & 0xffff), decodeTexture((texture >>> 16) & 0xffff),
                    compactColor & 0xff, (compactColor >>> 8) & 0xff, (compactColor >>> 16) & 0xff,
                    (compactColor >>> 24) & 0xff,
                    separateAo ? ((compactColor >>> 24) & 0xff) / 255.0F : 1.0F,
                    (lightMaterial & 0xff) >>> 4, ((lightMaterial >>> 8) & 0xff) >>> 4,
                    (lightMaterial >>> 16) & 0xff,
                    wordAt(buffer, offset, stride, 20), wordAt(buffer, offset, stride, 24),
                    wordAt(buffer, offset, stride, 28), wordAt(buffer, offset, stride, 32), wordAt(buffer, offset, stride, 36)
            );
        }
        return new AppearanceSource(layer, stride, separateAo, samples);
    }

    private static void recordVisibleCoverage(
            String stage,
            String layer,
            SortedRenderLists renderLists,
            double cameraX,
            double cameraY,
            double cameraZ,
            int viewportWidth,
            int viewportHeight,
            boolean emitEvent
    ) {
        if (!ENABLED || renderLists == null) {
            return;
        }
        String normalizedLayer = normalizeLayer(layer);
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? -1L : minecraft.level.getGameTime();
        int sectionCount = 0;
        int recordCount = 0;
        int missingCoverage = 0;
        int animatedSections = 0;
        long vertexTotal = 0L;
        long indexTotal = 0L;
        long primitiveTotal = 0L;
        StringBuilder records = new StringBuilder();
        records.append("[");
        Iterator<ChunkRenderList> renderListIterator = renderLists.iterator();
        while (renderListIterator.hasNext()) {
            ChunkRenderList renderList = renderListIterator.next();
            ByteIterator sectionIterator = renderList.sectionsWithGeometryIterator(false);
            if (sectionIterator == null) {
                continue;
            }
            while (sectionIterator.hasNext()) {
                int localSectionIndex = sectionIterator.nextByteAsInt() & 0xFF;
                RenderSection section = renderList.getRegion().getSection(localSectionIndex);
                if (section == null) {
                    continue;
                }
                sectionCount++;
                boolean animated = (section.getFlags() & RenderSectionFlags.MASK_HAS_ANIMATED_SPRITES) != 0;
                if (animated) {
                    animatedSections++;
                }
                MeshCoverage coverage = SOURCE_MESHES.get(new CoverageKey(section.getPosition().asLong(), normalizedLayer));
                if (coverage == null) {
                    missingCoverage++;
                } else {
                    vertexTotal += coverage.vertexCount();
                    indexTotal += coverage.indexCount();
                    primitiveTotal += coverage.primitiveCount();
                }
                if (recordCount < MAX_COVERAGE_SAMPLES) {
                    appendCoverageRecord(
                            records,
                            section.getPosition().asLong(),
                            section.getChunkX(),
                            section.getChunkY(),
                            section.getChunkZ(),
                            section.getOriginX(),
                            section.getOriginY(),
                            section.getOriginZ(),
                            section.getFlags(),
                            animated,
                            coverage,
                            "java-opengl-draw".equals(stage),
                            "java-opengl-draw".equals(stage) ? "java-opengl-terrain-layer" : "rust-vulkan-source-visible",
                            0L,
                            0L
                    );
                    recordCount++;
                }
            }
        }
        records.append("]");
        if ("java-opengl-draw".equals(stage)) {
            CaptureCoverageSnapshot snapshot = new CaptureCoverageSnapshot(
                    normalizedLayer, animatedSections, sectionCount, vertexTotal, indexTotal, primitiveTotal,
                    missingCoverage, sectionCount - missingCoverage, cameraX, cameraY, cameraZ,
                    viewportWidth, viewportHeight, records.toString()
            );
            if ("solid".equals(normalizedLayer)) {
                latestJavaDrawSolidCoverage = snapshot;
            } else if ("cutout".equals(normalizedLayer)) {
                latestJavaDrawCutoutCoverage = snapshot;
            }
        }
        if (!emitEvent) {
            return;
        }
        writeCoverageEvent(
                stage + "-coverage",
                normalizedLayer,
                gameTime,
                0,
                animatedSections,
                cameraX,
                cameraY,
                cameraZ,
                viewportWidth,
                viewportHeight,
                sectionCount,
                vertexTotal,
                indexTotal,
                primitiveTotal,
                missingCoverage,
                "java-opengl-draw".equals(stage) ? sectionCount - missingCoverage : 0,
                builder -> builder.append(records, 1, records.length() - 1)
        );
    }

    private static void writeCoverageEvent(
            String stage,
            String layer,
            long frameId,
            int flags,
            int animatedSections,
            double cameraX,
            double cameraY,
            double cameraZ,
            int viewportWidth,
            int viewportHeight,
            int recordCount,
            long vertexTotal,
            long indexTotal,
            long primitiveTotal,
            int missingCoverage,
            int executedRecords,
            CoverageRecordAppender appender
    ) {
        int eventIndex = COVERAGE_EVENTS.incrementAndGet();
        boolean readyCoverage = stage.endsWith("-ready-coverage");
        if (readyCoverage) {
            boolean captureCoverage = stage.contains("-capture-ready-");
            AtomicInteger readyBudget = captureCoverage ? CAPTURE_COVERAGE_EVENTS : READY_COVERAGE_EVENTS;
            int readyLimit = captureCoverage ? MAX_CAPTURE_COVERAGE_EVENTS : MAX_READY_COVERAGE_EVENTS;
            if (readyBudget.incrementAndGet() > readyLimit) {
                return;
            }
        }
        if (!readyCoverage && eventIndex > MAX_COVERAGE_EVENTS) {
            return;
        }
        StringBuilder json = new StringBuilder(4096);
        json.append("{");
        appendField(json, "schema", "mattmc-static-terrain-draw-coverage-v1").append(", ");
        appendField(json, "eventIndex", eventIndex).append(", ");
        appendField(json, "backend", backendName()).append(", ");
        appendField(json, "stage", stage).append(", ");
        appendField(json, "layer", normalizeLayer(layer)).append(", ");
        appendField(json, "frameId", frameId).append(", ");
        appendField(json, "gameTime", Minecraft.getInstance().level == null ? -1L : Minecraft.getInstance().level.getGameTime()).append(", ");
        appendField(json, "nanoTime", System.nanoTime()).append(", ");
        json.append("\"camera\": { ");
        appendField(json, "x", cameraX).append(", ");
        appendField(json, "y", cameraY).append(", ");
        appendField(json, "z", cameraZ);
        json.append(" }, ");
        json.append("\"viewport\": { ");
        appendField(json, "width", viewportWidth).append(", ");
        appendField(json, "height", viewportHeight);
        json.append(" }, ");
        json.append("\"aggregate\": { ");
        appendField(json, "records", recordCount).append(", ");
        appendField(json, "vertexCount", vertexTotal).append(", ");
        appendField(json, "indexCount", indexTotal).append(", ");
        appendField(json, "primitiveCount", primitiveTotal).append(", ");
        appendField(json, "missingCoverage", missingCoverage).append(", ");
        appendField(json, "executedRecords", executedRecords).append(", ");
        appendField(json, "animatedSpriteSections", animatedSections).append(", ");
        appendField(json, "flags", flags);
        json.append(" }, ");
        json.append("\"records\": [");
        appender.append(json);
        json.append("]");
        json.append("}\n");
        try {
            writeLine(json.toString());
        } catch (IOException ignored) {
            // Diagnostics must never alter render behavior.
        }
    }

    private static void appendCoverageRecord(
            StringBuilder records,
            long sectionKey,
            int x,
            int y,
            int z,
            int originX,
            int originY,
            int originZ,
            int flags,
            boolean animatedSprites,
            MeshCoverage coverage,
            boolean executed,
            String executionKind,
            long visibleGeneration,
            long rustEnqueueFrameId
    ) {
        if (records.length() > 0 && records.charAt(records.length() - 1) != '[') {
            records.append(", ");
        }
        records.append("{");
        appendField(records, "sectionKey", sectionKey).append(", ");
        appendField(records, "x", x).append(", ");
        appendField(records, "y", y).append(", ");
        appendField(records, "z", z).append(", ");
        appendField(records, "originX", originX).append(", ");
        appendField(records, "originY", originY).append(", ");
        appendField(records, "originZ", originZ).append(", ");
        appendField(records, "flags", flags).append(", ");
        appendField(records, "animatedSprites", animatedSprites ? 1 : 0).append(", ");
        appendField(records, "coveragePresent", coverage == null ? 0 : 1).append(", ");
        appendField(records, "executed", executed ? 1 : 0).append(", ");
        appendField(records, "executionKind", executionKind).append(", ");
        appendField(records, "visibleGeneration", visibleGeneration).append(", ");
        appendField(records, "rustEnqueueFrameId", rustEnqueueFrameId).append(", ");
        MeshCoverage value = coverage == null ? MeshCoverage.EMPTY : coverage;
        appendField(records, "sourceGeneration", value.sourceGeneration()).append(", ");
        appendField(records, "meshGeneration", value.meshGeneration()).append(", ");
        appendField(records, "meshKey", String.format(Locale.ROOT, "%016x", value.meshKey())).append(", ");
        appendField(records, "contentHash", String.format(Locale.ROOT, "%016x", value.contentHash())).append(", ");
        appendField(records, "vertexCount", value.vertexCount()).append(", ");
        appendField(records, "indexCount", value.indexCount()).append(", ");
        appendField(records, "primitiveCount", value.primitiveCount()).append(", ");
        appendField(records, "indexType", value.indexType()).append(", ");
        appendField(records, "sectionCount", value.sectionCount()).append(", ");
        appendField(records, "materialIdentity", value.materialIdentity()).append(", ");
        appendField(records, "textureIdentity", value.textureIdentity()).append(", ");
        appendField(records, "vertexStride", value.vertexStride()).append(", ");
        appendField(records, "bufferBytes", value.bufferBytes()).append(", ");
        appendField(records, "bufferVertexCapacity", value.bufferVertexCapacity()).append(", ");
        appendField(records, "sectionAnimatedSpriteIdentities", value.sectionAnimatedSpriteIdentities()).append(", ");
        appendField(records, "primitiveMetadataRecords", value.primitiveMetadataRecords()).append(", ");
        appendField(records, "unknownPrimitiveCount", value.unknownPrimitiveCount()).append(", ");
        appendField(records, "nonFluidTranslucentPrimitiveCount", value.nonFluidTranslucentPrimitiveCount()).append(", ");
        appendField(records, "builtinWaterPrimitiveCount", value.builtinWaterPrimitiveCount()).append(", ");
        appendField(records, "unsupportedFluidPrimitiveCount", value.unsupportedFluidPrimitiveCount()).append(", ");
        appendField(records, "layerAnimatedMaterialClassification", layerAnimatedMaterialClassification(value)).append(", ");
        appendField(records, "source", value.source()).append(", ");
        appendField(records, "boundsValid", value.boundsValid() ? 1 : 0).append(", ");
        records.append("\"bounds\": { ");
        appendField(records, "minX", value.localMinX()).append(", ");
        appendField(records, "minY", value.localMinY()).append(", ");
        appendField(records, "minZ", value.localMinZ()).append(", ");
        appendField(records, "maxX", value.localMaxX()).append(", ");
        appendField(records, "maxY", value.localMaxY()).append(", ");
        appendField(records, "maxZ", value.localMaxZ());
        records.append(" }, ");
        records.append("\"uvBounds\": { ");
        appendField(records, "minU", value.uvMinU()).append(", ");
        appendField(records, "minV", value.uvMinV()).append(", ");
        appendField(records, "maxU", value.uvMaxU()).append(", ");
        appendField(records, "maxV", value.uvMaxV());
        records.append(" }");
        records.append("}");
    }

    private static String normalizeLayer(String layer) {
        String value = layer == null ? "" : layer.trim().toLowerCase(Locale.ROOT);
        if ("solid".equals(value) || "opaque".equals(value) || ChunkSectionLayer.SOLID.name().equalsIgnoreCase(value)) {
            return "solid";
        }
        if ("cutout".equals(value) || "cutout_mipped".equals(value) || ChunkSectionLayer.CUTOUT_MIPPED.name().equalsIgnoreCase(value)) {
            return "cutout";
        }
        return value;
    }

    private static void appendAppearanceSamples(StringBuilder json, AppearanceSample[] samples) {
        for (int i = 0; i < samples.length; i++) {
            if (i > 0) json.append(",");
            AppearanceSample sample = samples[i];
            json.append("{");
            appendField(json, "vertexIndex", sample.vertexIndex()).append(", ");
            appendField(json, "primitiveIndex", sample.primitiveIndex()).append(", ");
            appendField(json, "faceCode", sample.faceCode()).append(", ");
            appendField(json, "worldPosition", vector3(sample.worldX(), sample.worldY(), sample.worldZ())).append(", ");
            appendField(json, "uv", vector2(sample.u(), sample.v())).append(", ");
            appendField(json, "colorR", sample.red()).append(", ");
            appendField(json, "colorG", sample.green()).append(", ");
            appendField(json, "colorB", sample.blue()).append(", ");
            appendField(json, "alphaOrAo", sample.alphaOrAo()).append(", ");
            appendField(json, "ao", sample.ao()).append(", ");
            appendField(json, "blockLight", sample.blockLight()).append(", ");
            appendField(json, "skyLight", sample.skyLight()).append(", ");
            appendField(json, "materialBits", sample.materialBits()).append(", ");
            appendField(json, "extensionWord20", String.format(Locale.ROOT, "%08x", sample.extensionWord20())).append(", ");
            appendField(json, "extensionWord24", String.format(Locale.ROOT, "%08x", sample.extensionWord24())).append(", ");
            appendField(json, "extensionWord28", String.format(Locale.ROOT, "%08x", sample.extensionWord28())).append(", ");
            appendField(json, "extensionWord32", String.format(Locale.ROOT, "%08x", sample.extensionWord32())).append(", ");
            appendField(json, "extensionWord36", String.format(Locale.ROOT, "%08x", sample.extensionWord36()));
            json.append("}");
        }
    }

    private static boolean usesSeparateAo() {
        try {
            return net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.shouldUseSeparateAo();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static int wordAt(ByteBuffer buffer, int vertexOffset, int stride, int relativeOffset) {
        return relativeOffset + Integer.BYTES <= stride ? buffer.getInt(vertexOffset + relativeOffset) : 0;
    }

    private static String vector2(float x, float y) {
        return String.format(Locale.ROOT, "[%.6f,%.6f]", x, y);
    }

    private static String vector3(float x, float y, float z) {
        return String.format(Locale.ROOT, "[%.6f,%.6f,%.6f]", x, y, z);
    }

    private static String layerAnimatedMaterialClassification(MeshCoverage coverage) {
        if (coverage.builtinWaterPrimitiveCount() > 0) {
            return "builtin-water";
        }
        if (coverage.unsupportedFluidPrimitiveCount() > 0) {
            return "unsupported-fluid";
        }
        return "none";
    }

    private static String backendName() {
        return Boolean.getBoolean("mattmc.dev.rustGalVulkanWholeFrame")
                ? "rust-vulkan-whole-frame"
                : "java-opengl";
    }

    private static long semanticMeshKey(long sectionKey, String layer) {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, sectionKey);
        hash = mix(hash, normalizeLayer(layer).hashCode());
        return hash;
    }

    private static String materialIdentity(String layer) {
        return "terrain:" + normalizeLayer(layer);
    }

    private static String textureIdentity(String layer) {
        return "minecraft:textures/atlas/blocks.png#" + normalizeLayer(layer);
    }

    private static String animatedSpriteIdentities(ChunkBuildOutput output) {
        if (output == null || output.info == null || output.info.animatedSprites == null || output.info.animatedSprites.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int count = Math.min(output.info.animatedSprites.length, 8);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append("|");
            }
            var sprite = output.info.animatedSprites[i];
            if (sprite == null || sprite.contents() == null || sprite.contents().name() == null) {
                builder.append("unknown");
            } else {
                builder.append(sprite.contents().name());
            }
        }
        if (output.info.animatedSprites.length > count) {
            builder.append("|...");
        }
        return builder.toString();
    }

    private static int activeTerrainVertexStride() {
        try {
            return net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE
                    .getVertexFormat()
                    .getVertexFormat()
                    .getStride();
        } catch (RuntimeException error) {
            return COMPACT_PREFIX_STRIDE;
        }
    }

    private static float decodePosition(int hi, int lo, int component) {
        int shift = component * 10;
        int value = ((hi >>> shift) & 0x3ff) << 10 | ((lo >>> shift) & 0x3ff);
        return value / (float) POSITION_MAX_VALUE * 32.0F - 8.0F;
    }

    private static float decodeTexture(int value) {
        return (value & 0x7fff) / (float) TEXTURE_MAX_VALUE;
    }

    /** Diagnostic only: reports the same post-shrink sampling coordinate used
     * by Frozen's compact terrain vertex stage; this never participates in rendering. */
    private static float decodeTextureForBlockAtlas(int packedValue, boolean horizontal) {
        var texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        if (!(texture instanceof TextureAtlas atlas)) return decodeTexture(packedValue);
        int extent = horizontal ? atlas.getWidth() : atlas.getHeight();
        if (extent <= 0) return decodeTexture(packedValue);
        float shrink = (1.0F / TEXTURE_MAX_VALUE) - (1.0F / (extent * 256.0F));
        return decodeTexture(packedValue) + ((packedValue & 0x8000) == 0 ? -shrink : shrink);
    }

    private static long mix(long hash, long value) {
        long result = hash;
        result ^= value;
        result *= 0x100000001b3L;
        return result;
    }

    private static synchronized void writeLine(String line) throws IOException {
        Path parent = OUTPUT_PATH.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(
                OUTPUT_PATH,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private static void updateCrcString(CRC32 crc, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            crc.update(b & 0xff);
        }
    }

    private static void updateCrcInt(CRC32 crc, int value) {
        crc.update(value & 0xff);
        crc.update((value >>> 8) & 0xff);
        crc.update((value >>> 16) & 0xff);
        crc.update((value >>> 24) & 0xff);
    }

    private static void updateCrcLong(CRC32 crc, long value) {
        updateCrcInt(crc, (int) value);
        updateCrcInt(crc, (int) (value >>> 32));
    }

    private static StringBuilder appendField(StringBuilder builder, String name, String value) {
        builder.append("\"").append(name).append("\": \"").append(escape(value)).append("\"");
        return builder;
    }

    private static StringBuilder appendField(StringBuilder builder, String name, long value) {
        builder.append("\"").append(name).append("\": ").append(value);
        return builder;
    }

    private static StringBuilder appendField(StringBuilder builder, String name, boolean value) {
        builder.append("\"").append(name).append("\": ").append(value);
        return builder;
    }

    private static StringBuilder appendField(StringBuilder builder, String name, double value) {
        builder.append("\"").append(name).append("\": ")
                .append(String.format(Locale.ROOT, "%.6f", value));
        return builder;
    }

    private static StringBuilder appendMatrix(StringBuilder builder, String name, Matrix4fc matrix) {
        builder.append("\"").append(name).append("\":[");
        float[] values = new Matrix4f(matrix).get(new float[16]);
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            appendFloat(builder, values[i]);
        }
        return builder.append("]");
    }

    private static StringBuilder appendVector(StringBuilder builder, Vector4f value) {
        return builder.append("[")
                .append(String.format(Locale.ROOT, "%.6f", value.x())).append(",")
                .append(String.format(Locale.ROOT, "%.6f", value.y())).append(",")
                .append(String.format(Locale.ROOT, "%.6f", value.z())).append(",")
                .append(String.format(Locale.ROOT, "%.6f", value.w())).append("]");
    }

    private static StringBuilder appendFloat(StringBuilder builder, float value) {
        return builder.append(String.format(Locale.ROOT, "%.6f", value));
    }

    private static long parseTransformTraceSection() {
        String value = System.getProperty("mattmc.dev.staticTerrainParityDiagnostics.transformTraceSection");
        if (value == null || value.isBlank()) {
            value = System.getenv("MATTMC_STATIC_TERRAIN_TRANSFORM_TRACE_SECTION");
        }
        if (value == null || value.isBlank()) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static long parseAppearanceTraceSection() {
        String value = System.getProperty("mattmc.dev.staticTerrainParityDiagnostics.appearanceTraceSection");
        if (value == null || value.isBlank()) value = System.getenv("MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_SECTION");
        if (value == null || value.isBlank()) return Long.MIN_VALUE;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static int[] parseAppearanceTraceBlock() {
        String value = System.getProperty("mattmc.dev.staticTerrainParityDiagnostics.appearanceTraceBlock");
        if (value == null || value.isBlank()) {
            value = System.getenv("MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_BLOCK");
        }
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.trim().split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[] {
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int[] parseCompactColorTraceValues() {
        String value = System.getProperty("mattmc.dev.staticTerrainParityDiagnostics.compactColorTrace", "").trim();
        if (value.isEmpty()) {
            return new int[0];
        }
        String[] parts = value.split(",");
        int[] parsed = new int[Math.min(parts.length, 16)];
        int count = 0;
        for (String part : parts) {
            try {
                String normalized = part.trim().replace("0x", "").replace("0X", "");
                parsed[count++] = (int) Long.parseUnsignedLong(normalized, 16);
            } catch (NumberFormatException ignored) {
                // An invalid diagnostic selector simply contributes no samples.
            }
            if (count == parsed.length) {
                break;
            }
        }
        return java.util.Arrays.copyOf(parsed, count);
    }

    private static boolean isCompactColorTraceValue(int color) {
        for (int candidate : COMPACT_COLOR_TRACE_VALUES) {
            if (candidate == color) {
                return true;
            }
        }
        return false;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static long parseFaceCullTraceSection() {
        String value = System.getenv("MATTMC_NATIVE_CULL_TRACE_SECTION");
        if (value == null || value.isBlank()) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private interface CoverageRecordAppender {
        void append(StringBuilder records);
    }

    public static final class SourceBlockClassification {
        private static final int MAX_IDENTITIES = 16;
        private final Map<String, Integer> identities = new LinkedHashMap<>();
        private int omittedIdentities;
        private int modelBlocks;
        private int nativeModelBlocks;
        private int javaModelBlocks;
        private int fluidBlocks;
        private int nativeFluidBlocks;
        private int javaFluidBlocks;
        private int builtInWaterBlocks;
        private int modelMinLocalY = Integer.MAX_VALUE;
        private int modelMaxLocalY = Integer.MIN_VALUE;
        private int fluidMinLocalY = Integer.MAX_VALUE;
        private int fluidMaxLocalY = Integer.MIN_VALUE;

        private void record(
                String blockIdentity,
                int localY,
                boolean model,
                boolean nativeModel,
                boolean fluid,
                boolean nativeFluid,
                boolean builtInWater
        ) {
            if (model) {
                this.modelBlocks++;
                if (nativeModel) {
                    this.nativeModelBlocks++;
                } else {
                    this.javaModelBlocks++;
                }
                this.modelMinLocalY = Math.min(this.modelMinLocalY, localY);
                this.modelMaxLocalY = Math.max(this.modelMaxLocalY, localY);
            }
            if (fluid) {
                this.fluidBlocks++;
                if (nativeFluid) {
                    this.nativeFluidBlocks++;
                } else {
                    this.javaFluidBlocks++;
                }
                if (builtInWater) {
                    this.builtInWaterBlocks++;
                }
                this.fluidMinLocalY = Math.min(this.fluidMinLocalY, localY);
                this.fluidMaxLocalY = Math.max(this.fluidMaxLocalY, localY);
            }
            String identity = blockIdentity == null || blockIdentity.isBlank() ? "unknown" : blockIdentity;
            Integer previous = this.identities.get(identity);
            if (previous != null) {
                this.identities.put(identity, previous + 1);
            } else if (this.identities.size() < MAX_IDENTITIES) {
                this.identities.put(identity, 1);
            } else {
                this.omittedIdentities++;
            }
        }

        private int modelMinLocalY() {
            return this.modelMinLocalY == Integer.MAX_VALUE ? -1 : this.modelMinLocalY;
        }

        private int modelMaxLocalY() {
            return this.modelMaxLocalY == Integer.MIN_VALUE ? -1 : this.modelMaxLocalY;
        }

        private int fluidMinLocalY() {
            return this.fluidMinLocalY == Integer.MAX_VALUE ? -1 : this.fluidMinLocalY;
        }

        private int fluidMaxLocalY() {
            return this.fluidMaxLocalY == Integer.MIN_VALUE ? -1 : this.fluidMaxLocalY;
        }

        private String identityCounts() {
            StringBuilder result = new StringBuilder();
            for (Map.Entry<String, Integer> entry : this.identities.entrySet()) {
                if (result.length() > 0) {
                    result.append('|');
                }
                result.append(entry.getKey()).append(':').append(entry.getValue());
            }
            if (this.omittedIdentities > 0) {
                if (result.length() > 0) {
                    result.append('|');
                }
                result.append("other:").append(this.omittedIdentities);
            }
            return result.toString();
        }
    }

    private record CoverageKey(long sectionKey, String layer) {
    }

    private record CaptureCoverageSnapshot(
            String layer, int animatedSections, int sectionCount, long vertexTotal, long indexTotal,
            long primitiveTotal, int missingCoverage, int executedRecords,
            double cameraX, double cameraY, double cameraZ, int viewportWidth, int viewportHeight,
            String records
    ) {
    }

    private record AppearanceSource(String layer, int vertexStride, boolean separateAo, AppearanceSample[] samples) {
    }

    private record AppearanceSample(
            int vertexIndex, int primitiveIndex, int faceCode,
            float worldX, float worldY, float worldZ, float u, float v,
            int red, int green, int blue, int alphaOrAo, float ao,
            int blockLight, int skyLight, int materialBits,
            int extensionWord20, int extensionWord24, int extensionWord28, int extensionWord32, int extensionWord36
    ) {
    }

    private record PendingIrisTerrainFragmentProbe(
            String stage, String layer, AppearanceSample sample, int screenX, int screenY, int viewportHeight
    ) {
    }

    private record MeshCoverage(
            String layer,
            long sourceGeneration,
            long meshGeneration,
            long meshKey,
            long contentHash,
            int vertexCount,
            int indexCount,
            int indexType,
            int primitiveCount,
            int sectionCount,
            int sectionOriginX,
            int sectionOriginY,
            int sectionOriginZ,
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
            boolean boundsValid,
            String materialIdentity,
            String textureIdentity,
            int vertexStride,
            int bufferBytes,
            int bufferVertexCapacity,
            int primitiveMetadataRecords,
            int unknownPrimitiveCount,
            int nonFluidTranslucentPrimitiveCount,
            int builtinWaterPrimitiveCount,
            int unsupportedFluidPrimitiveCount,
            String sectionAnimatedSpriteIdentities,
            String source
    ) {
        private static final MeshCoverage EMPTY = new MeshCoverage(
                "",
                0L,
                0L,
                0L,
                0L,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                false,
                "",
                "",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                "",
                "missing"
        );
    }
}
