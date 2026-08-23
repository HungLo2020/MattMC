package net.sodium.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.SectionPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.RenderSectionFlags;
import net.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.render.chunk.lists.ChunkRenderList;
import net.sodium.client.render.chunk.lists.SortedRenderLists;
import net.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.sodium.client.util.iterator.ByteIterator;
import net.vulkanic.bridge.VulkanicGalBridge;
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
import java.util.List;
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
    private static final int MAX_EVENTS = Math.max(
            1,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxEvents", 512)
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
    /** Reserved independently from build tracing for capture-frame receipts. */
    private static final int MAX_WHOLE_FRAME_COVERAGE_EVENTS = Math.max(
            1,
            Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.maxWholeFrameCoverageEvents", 512)
    );
    private static final long TRANSFORM_TRACE_SECTION = parseTransformTraceSection();
    private static final long APPEARANCE_TRACE_SECTION = parseAppearanceTraceSection();
    private static final int[] APPEARANCE_TRACE_BLOCK = parseAppearanceTraceBlock();
    private static final Path OUTPUT_PATH = Path.of(System.getProperty(
            "mattmc.dev.staticTerrainParityDiagnostics.path",
            "run/static_terrain_parity_diagnostics.jsonl"
    ));
    private static final AtomicInteger EVENTS = new AtomicInteger();
    private static final AtomicInteger COVERAGE_EVENTS = new AtomicInteger();
    private static final AtomicInteger WHOLE_FRAME_COVERAGE_EVENTS = new AtomicInteger();
    private static final AtomicInteger TRANSFORM_TRACE_EVENTS = new AtomicInteger();
    private static final AtomicInteger APPEARANCE_TRACE_EVENTS = new AtomicInteger();
    private static final AtomicInteger APPEARANCE_LIGHT_TRACE_EVENTS = new AtomicInteger();
    private static final AtomicInteger APPEARANCE_LIGHT_SNAPSHOT_EVENTS = new AtomicInteger();
    private static final Map<CoverageKey, MeshCoverage> SOURCE_MESHES = new ConcurrentHashMap<>();
    /** Copied explicit terrain assets keyed by semantic section/layer. */
    private static final Map<CoverageKey, MeshCoverage> RUST_ENQUEUE_MESHES = new ConcurrentHashMap<>();
    /** Actual Rust-backend execution metadata, retained only for frame receipts. */
    private static final Map<CoverageKey, MeshCoverage> RUST_EXECUTED_MESHES = new ConcurrentHashMap<>();
    private static final Map<CoverageKey, AppearanceSource> SOURCE_APPEARANCES = new ConcurrentHashMap<>();
    private static final Map<CoverageKey, String> RUST_ADMISSION_REASONS = new ConcurrentHashMap<>();
    private static volatile int latestSolidSectionCount;
    private static volatile long latestSolidHash;
    private static volatile long latestSolidGameTime;
    private static volatile int stableSolidFrames;
    private static volatile int readySolidGameFrames;
    private static volatile long readySolidLastGameTime = Long.MIN_VALUE;
    private static volatile int appearanceLightSnapshotMask;
    private static volatile boolean appearanceLightSnapshotObserved;
    private static volatile boolean appearanceLightFallbackObserved;

    private StaticTerrainParityDiagnostics() {
    }

    public static void recordChunkBuildOutput(ChunkBuildOutput output) {
        if (!ENABLED || output == null || output.render == null) {
            return;
        }
        recordSourceMesh(output, DefaultTerrainRenderPasses.SOLID, "solid");
        recordSourceMesh(output, DefaultTerrainRenderPasses.CUTOUT, "cutout");
    }

    /**
     * Captures only the semantic inputs to a section build. This is deliberately
     * independent of native mesh records so a parity investigation can identify
     * an upstream model/fluid divergence before comparing renderer resources.
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
        int eventIndex = COVERAGE_EVENTS.incrementAndGet();
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

    /**
     * Records route admission separately from source mesh construction. A section can have valid
     * Sodium geometry while the Rust route intentionally has no registered asset for it; keeping
     * that distinction explicit is essential for cross-renderer coverage diagnostics.
     */
    public static void recordRustStaticTerrainAdmission(ChunkBuildOutput output, String reason) {
        if (!ENABLED || output == null || output.render == null) {
            return;
        }
        recordRustStaticTerrainAdmission(output, "solid", reason);
        recordRustStaticTerrainAdmission(output, "cutout", reason);
    }

    public static void recordRustStaticTerrainNonExecution(
            long sectionKey,
            String layer,
            String reason,
            long visibleGeneration,
            long rustEnqueueFrameId
    ) {
        if (!ENABLED) {
            return;
        }
        String normalizedLayer = normalizeLayer(layer);
        MeshCoverage coverage = SOURCE_MESHES.get(new CoverageKey(sectionKey, normalizedLayer));
        if (coverage == null) {
            return;
        }
        writeCoverageEvent(
                "rust-vulkan-non-executed",
                normalizedLayer,
                Minecraft.getInstance().level == null ? -1L : Minecraft.getInstance().level.getGameTime(),
                0,
                0,
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
                        coverage.sectionOriginX() >> 4,
                        coverage.sectionOriginY() >> 4,
                        coverage.sectionOriginZ() >> 4,
                        coverage.sectionOriginX(),
                        coverage.sectionOriginY(),
                        coverage.sectionOriginZ(),
                        0,
                        false,
                        coverage,
                        false,
                        reason,
                        visibleGeneration,
                        rustEnqueueFrameId
                )
        );
    }

    /**
     * Writes one frame-scoped receipt for the CPU sections selected by the
     * whole-frame semantic terrain callsite.  This is intentionally emitted
     * from copied mesh metadata, not a Sodium render list or GPU resource, so
     * Current/Frozen coverage comparison can join the exact semantic frame
     * without borrowing Java OpenGL state.
     */
    public static void recordRustWholeFrameEnqueueCoverage(
            Iterable<RenderSection> sections,
            long semanticFrameId,
            double cameraX,
            double cameraY,
            double cameraZ,
            int viewportWidth,
            int viewportHeight
    ) {
        if (!ENABLED || sections == null || semanticFrameId <= 0L) {
            return;
        }
        for (String layer : List.of("solid", "cutout")) {
            int sectionCount = 0;
            int recordCount = 0;
            int missingCoverage = 0;
            int animatedSections = 0;
            long vertexTotal = 0L;
            long indexTotal = 0L;
            long primitiveTotal = 0L;
            StringBuilder records = new StringBuilder("[");
            for (RenderSection section : sections) {
                if (section == null || !section.isBuilt()) {
                    continue;
                }
                long sectionKey = section.getPosition().asLong();
                MeshCoverage coverage = RUST_ENQUEUE_MESHES.get(new CoverageKey(sectionKey, layer));
                if (coverage == null) {
                    coverage = SOURCE_MESHES.get(new CoverageKey(sectionKey, layer));
                }
                if (coverage == null) {
                    // Empty layers are not draw coverage; preserve only a real
                    // missing copied asset as a diagnostic failure candidate.
                    continue;
                }
                sectionCount++;
                boolean animated = (section.getFlags() & RenderSectionFlags.MASK_HAS_ANIMATED_SPRITES) != 0;
                if (animated) {
                    animatedSections++;
                }
                vertexTotal += coverage.vertexCount();
                indexTotal += coverage.indexCount();
                primitiveTotal += coverage.primitiveCount();
                if (recordCount < MAX_COVERAGE_SAMPLES) {
                    appendCoverageRecord(
                            records,
                            sectionKey,
                            section.getChunkX(),
                            section.getChunkY(),
                            section.getChunkZ(),
                            section.getOriginX(),
                            section.getOriginY(),
                            section.getOriginZ(),
                            section.getFlags(),
                            animated,
                            coverage,
                            true,
                            "rust-vulkan-whole-frame-semantic-enqueue",
                            coverage.meshGeneration(),
                            0L
                    );
                    recordCount++;
                }
            }
            records.append("]");
            writeWholeFrameCoverageEvent(
                    "rust-vulkan-enqueue-source-coverage",
                    layer,
                    semanticFrameId,
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
                    sectionCount,
                    builder -> {
                        if (records.length() > 1) {
                            builder.append(records, 1, records.length() - 1);
                        }
                    }
            );
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
        if (eventIndex > MAX_EVENTS) {
            return;
        }

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
            if (sectionCount > 0 && gameTime != readySolidLastGameTime) {
                readySolidGameFrames++;
                readySolidLastGameTime = gameTime;
            }
            latestSolidSectionCount = sectionCount;
            latestSolidHash = hash;
            latestSolidGameTime = gameTime;
        }
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

        recordVisibleCoverage(stage, layer, renderLists, cameraX, cameraY, cameraZ, viewportWidth, viewportHeight);
    }

    public static void recordRustStaticTerrainExecution(
            String stage,
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
        MeshCoverage sourceCoverage = SOURCE_MESHES.get(new CoverageKey(sectionKey, normalizedLayer));
        // The semantic receipt is derived from the same immutable CPU mesh as
        // the source build. Keep its layout and primitive provenance so parity
        // diagnostics do not mistake a valid Rust mesh for an opaque zero-layout
        // asset (and retain animated-sprite identity evidence).
        int semanticVertexStride = sourceCoverage == null ? 0 : sourceCoverage.vertexStride();
        int semanticBufferBytes = sourceCoverage == null ? 0 : sourceCoverage.bufferBytes();
        int semanticBufferVertexCapacity = sourceCoverage == null ? 0 : sourceCoverage.bufferVertexCapacity();
        int semanticPrimitiveMetadataRecords = sourceCoverage == null ? 0 : sourceCoverage.primitiveMetadataRecords();
        int semanticUnknownPrimitiveCount = sourceCoverage == null ? 0 : sourceCoverage.unknownPrimitiveCount();
        int semanticNonFluidTranslucentPrimitiveCount = sourceCoverage == null ? 0 : sourceCoverage.nonFluidTranslucentPrimitiveCount();
        int semanticGenericFluidPrimitiveCount = sourceCoverage == null ? 0 : sourceCoverage.genericFluidPrimitiveCount();
        int semanticBuiltinWaterPrimitiveCount = sourceCoverage == null ? 0 : sourceCoverage.builtinWaterPrimitiveCount();
        int semanticUnsupportedFluidPrimitiveCount = sourceCoverage == null ? 0 : sourceCoverage.unsupportedFluidPrimitiveCount();
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
                semanticVertexStride,
                semanticBufferBytes,
                semanticBufferVertexCapacity,
                semanticPrimitiveMetadataRecords,
                semanticUnknownPrimitiveCount,
                semanticNonFluidTranslucentPrimitiveCount,
                semanticGenericFluidPrimitiveCount,
                semanticBuiltinWaterPrimitiveCount,
                semanticUnsupportedFluidPrimitiveCount,
                sourceCoverage == null ? "" : sourceCoverage.sectionAnimatedSpriteIdentities(),
                "rust-static-terrain-asset"
        );
        RUST_ENQUEUE_MESHES.put(new CoverageKey(sectionKey, normalizedLayer), coverage);
        if ("rust-vulkan-executed".equals(stage)) {
            RUST_EXECUTED_MESHES.put(new CoverageKey(sectionKey, normalizedLayer), coverage);
        }
        writeCoverageEvent(
                stage,
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

    /**
     * Copied identity of a mesh instance that the Rust backend actually
     * encoded for a submitted frame. This is deliberately distinct from the
     * semantic enqueue receipt: it is emitted only after Rust reports the
     * instance as executed, and carries no GPU/native handle.
     */
    public record RustExecutionIdentity(long sectionKey, String layer, long visibleGeneration) {
    }

    /**
     * Writes one exact-frame, bounded execution receipt for the Rust backend.
     * Per-instance trace events are intentionally budgeted and can become
     * stale during initial terrain population; this grouped receipt cannot
     * silently turn that diagnostic truncation into a false execution gap.
     */
    public static void recordRustWholeFrameExecutionCoverage(
            Iterable<RustExecutionIdentity> executions,
            long backendFrameId
    ) {
        if (!ENABLED || executions == null || backendFrameId <= 0L) {
            return;
        }
        for (String layer : List.of("solid", "cutout")) {
            int recordCount = 0;
            int animatedSections = 0;
            long vertexTotal = 0L;
            long indexTotal = 0L;
            long primitiveTotal = 0L;
            StringBuilder records = new StringBuilder("[");
            for (RustExecutionIdentity execution : executions) {
                if (execution == null || !layer.equals(normalizeLayer(execution.layer()))) {
                    continue;
                }
                MeshCoverage coverage = RUST_EXECUTED_MESHES.get(
                        new CoverageKey(execution.sectionKey(), layer)
                );
                if (coverage == null) {
                    continue;
                }
                vertexTotal += coverage.vertexCount();
                indexTotal += coverage.indexCount();
                primitiveTotal += coverage.primitiveCount();
                appendCoverageRecord(
                        records,
                        execution.sectionKey(),
                        coverage.sectionOriginX() >> 4,
                        coverage.sectionOriginY() >> 4,
                        coverage.sectionOriginZ() >> 4,
                        coverage.sectionOriginX(),
                        coverage.sectionOriginY(),
                        coverage.sectionOriginZ(),
                        0,
                        false,
                        coverage,
                        true,
                        "rust-vulkan-whole-frame-backend-executed",
                        execution.visibleGeneration(),
                        0L
                );
                recordCount++;
            }
            records.append("]");
            if (recordCount == 0) {
                continue;
            }
            writeWholeFrameCoverageEvent(
                    "rust-vulkan-executed-coverage",
                    layer,
                    backendFrameId,
                    0,
                    animatedSections,
                    0.0D,
                    0.0D,
                    0.0D,
                    0,
                    0,
                    recordCount,
                    vertexTotal,
                    indexTotal,
                    primitiveTotal,
                    0,
                    recordCount,
                    builder -> builder.append(records, 1, records.length() - 1)
            );
        }
    }

    public static boolean isSolidVisibleListStable(int requiredFrames, int minimumSections) {
        if (!ENABLED) {
            return true;
        }
        return latestSolidSectionCount >= minimumSections && readySolidGameFrames >= Math.max(1, requiredFrames);
    }

    public static String solidVisibleListSummary() {
        return "sections=" + latestSolidSectionCount
                + ",hash=" + String.format(Locale.ROOT, "%016x", latestSolidHash)
                + ",stableFrames=" + stableSolidFrames
                + ",readyGameFrames=" + readySolidGameFrames
                + ",gameTime=" + latestSolidGameTime;
    }

    /**
     * Observes one semantically selected terrain section at the real draw/enqueue boundary.
     * Matrix arrays are JOML column-major values; probe results are normalized to a top-left
     * screenshot coordinate system so the two backends can compare semantics without exposing
     * either backend's objects.
     */
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
        if (eventIndex > 8) {
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

    /**
     * Observes selected source attributes only at a normal terrain draw/enqueue boundary.
     * It deliberately records semantic vertex payloads, never renderer objects or handles.
     */
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
            // Diagnostics must never alter render behavior.
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

    /**
     * Records whether a cloned section observed real client light data or
     * Sodium's documented default layer. This is a capture-only readiness seam.
     */
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
        } else {
            appearanceLightFallbackObserved = true;
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
            // Diagnostics must never alter render behavior.
        }
    }

    /**
     * Observes the real client lifecycle that makes a chunk available to Sodium.
     * This is capture-only: it reads the published source layers but never changes
     * tracker status, light storage, or terrain admission.
     */
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
            // Diagnostics must never alter render behavior.
        }
    }

    public static boolean isAppearanceLightReady() {
        if (!Boolean.getBoolean("mattmc.dev.staticTerrainParityDiagnostics.requireAppearanceLightReady")) {
            return true;
        }
        int required = (1 << LightLayer.BLOCK.ordinal()) | (1 << LightLayer.SKY.ordinal());
        return appearanceLightSnapshotObserved
                && (appearanceLightSnapshotMask & required) == required
                && !appearanceLightFallbackObserved;
    }

    public static String appearanceLightReadinessSummary() {
        int required = (1 << LightLayer.BLOCK.ordinal()) | (1 << LightLayer.SKY.ordinal());
        return "observed=" + appearanceLightSnapshotObserved
                + ";layers=" + appearanceLightSnapshotMask
                + ";required=" + required
                + ";fallbackObserved=" + appearanceLightFallbackObserved
                + ";ready=" + isAppearanceLightReady();
    }

    /** Records the copied Current payload before it crosses FFI. */
    public static void recordRustStaticTerrainAppearanceCopy(
            ChunkBuildOutput output,
            String layer,
            long meshKey,
            long meshGeneration,
            List<VulkanicGalBridge.WorldMeshVertexRecord> vertices
    ) {
        if (!ENABLED || APPEARANCE_TRACE_SECTION == Long.MIN_VALUE || output == null || output.render == null
                || output.render.getPosition().asLong() != APPEARANCE_TRACE_SECTION || vertices == null) {
            return;
        }
        String normalizedLayer = normalizeLayer(layer);
        int eventIndex = APPEARANCE_TRACE_EVENTS.incrementAndGet();
        if (eventIndex > 8) {
            return;
        }
        try {
            StringBuilder json = new StringBuilder(8192);
            json.append("{");
            appendField(json, "schema", "mattmc-static-terrain-appearance-rust-copy-v2").append(", ");
            appendField(json, "eventIndex", eventIndex).append(", ");
            appendField(json, "backend", backendName()).append(", ");
            appendField(json, "stage", "java-to-rust-copy").append(", ");
            appendField(json, "layer", normalizedLayer).append(", ");
            appendField(json, "sectionKey", APPEARANCE_TRACE_SECTION).append(", ");
            appendField(json, "sourceGeneration", output.submitTime).append(", ");
            appendField(json, "meshKey", String.format(Locale.ROOT, "%016x", meshKey)).append(", ");
            appendField(json, "meshGeneration", meshGeneration).append(", ");
            appendField(json, "gameTime", Minecraft.getInstance().level == null ? -1L : Minecraft.getInstance().level.getGameTime()).append(", ");
            appendField(json, "vertexCount", vertices.size()).append(", ");
            appendField(json, "semanticMaterial", "terrain:" + normalizedLayer).append(", ");
            appendField(json, "semanticTexture", "minecraft:textures/atlas/blocks.png#" + normalizedLayer).append(", ");
            json.append("\"samples\":[");
            int sampleCount = Math.min(vertices.size(), MAX_SAMPLES);
            for (int i = 0; i < sampleCount; i++) {
                if (i > 0) {
                    json.append(",");
                }
                VulkanicGalBridge.WorldMeshVertexRecord vertex = vertices.get(i);
                int color = vertex.colorArgb();
                json.append("{");
                appendField(json, "vertexIndex", i).append(", ");
                appendField(json, "primitiveIndex", i / 4).append(", ");
                appendField(json, "position", vector3(vertex.x(), vertex.y(), vertex.z())).append(", ");
                appendField(json, "uv", vector2(vertex.u(), vertex.v())).append(", ");
                appendField(json, "atlasUv", vector2(vertex.atlasU(), vertex.atlasV())).append(", ");
                appendField(json, "colorR", (color >>> 16) & 0xff).append(", ");
                appendField(json, "colorG", (color >>> 8) & 0xff).append(", ");
                appendField(json, "colorB", color & 0xff).append(", ");
                appendField(json, "colorA", (color >>> 24) & 0xff).append(", ");
                appendField(json, "normalPacked", String.format(Locale.ROOT, "%08x", vertex.normalPacked())).append(", ");
                appendField(json, "normal", vector3(
                        signedNormal(vertex.normalPacked(), 0),
                        signedNormal(vertex.normalPacked(), 8),
                        signedNormal(vertex.normalPacked(), 16)
                )).append(", ");
                appendField(json, "packedLight", String.format(Locale.ROOT, "%08x", vertex.light())).append(", ");
                appendField(json, "blockLight", (vertex.light() >>> 4) & 0xf).append(", ");
                appendField(json, "skyLight", (vertex.light() >>> 20) & 0xf).append(", ");
                appendField(json, "shaderBlockId", vertex.shaderBlockId()).append(", ");
                appendField(json, "shaderMaterialType", vertex.shaderMaterialType());
                json.append("}");
            }
            json.append("],\"targetBlockColorSamples\":[");
            appendTargetBlockColorSamples(json, output, vertices);
            json.append("]}\n");
            writeLine(json.toString());
        } catch (IOException ignored) {
            // Diagnostics must never alter render behavior.
        }
    }

    /**
     * Copies only the semantic vertex colors whose section-local positions
     * land in the requested probe block. This is deliberately a source-side
     * receipt: it does not inspect a Java atlas, renderer object, or GPU
     * resource, and it lets parity captures distinguish missing biome tint
     * from a final-frame crop that contains neighboring faces.
     */
    private static void appendTargetBlockColorSamples(
            StringBuilder json,
            ChunkBuildOutput output,
            List<VulkanicGalBridge.WorldMeshVertexRecord> vertices
    ) {
        int[] target = APPEARANCE_TRACE_BLOCK;
        if (target == null || output == null || output.render == null || vertices == null) {
            return;
        }
        double originX = output.render.getOriginX();
        double originY = output.render.getOriginY();
        double originZ = output.render.getOriginZ();
        int written = 0;
        for (int index = 0; index < vertices.size() && written < MAX_SAMPLES; index++) {
            VulkanicGalBridge.WorldMeshVertexRecord vertex = vertices.get(index);
            int worldX = (int) Math.floor(originX + vertex.x());
            int worldY = (int) Math.floor(originY + vertex.y());
            int worldZ = (int) Math.floor(originZ + vertex.z());
            if (worldX != target[0] || worldY != target[1] || worldZ != target[2]) {
                continue;
            }
            if (written > 0) {
                json.append(",");
            }
            int color = vertex.colorArgb();
            json.append("{");
            appendField(json, "vertexIndex", index).append(", ");
            appendField(json, "colorR", (color >>> 16) & 0xff).append(", ");
            appendField(json, "colorG", (color >>> 8) & 0xff).append(", ");
            appendField(json, "colorB", color & 0xff).append(", ");
            appendField(json, "colorA", (color >>> 24) & 0xff);
            json.append("}");
            written++;
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

    public static void recordRustStaticTerrainAdmission(ChunkBuildOutput output, String layer, String reason) {
        String normalizedLayer = normalizeLayer(layer);
        long sectionKey = output.render.getPosition().asLong();
        MeshCoverage coverage = SOURCE_MESHES.get(new CoverageKey(sectionKey, normalizedLayer));
        if (coverage == null) {
            RUST_ADMISSION_REASONS.remove(new CoverageKey(sectionKey, normalizedLayer));
            return;
        }
        String normalizedReason = reason == null || reason.isBlank() ? "admission-unknown" : reason;
        RUST_ADMISSION_REASONS.put(new CoverageKey(sectionKey, normalizedLayer), normalizedReason);
        writeCoverageEvent(
                "rust-vulkan-admission",
                normalizedLayer,
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
                        normalizedReason,
                        0L,
                        0L
                )
        );
    }

    public static String rustStaticTerrainAdmissionReason(long sectionKey, String layer) {
        if (!ENABLED) {
            return "diagnostics-disabled";
        }
        return RUST_ADMISSION_REASONS.getOrDefault(
                new CoverageKey(sectionKey, normalizeLayer(layer)),
                "no-rust-admission-record"
        );
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
                int texture = buffer.getInt(offset + TEXTURE_OFFSET);
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
                updateCrcInt(crc, positionHi);
                updateCrcInt(crc, positionLo);
                updateCrcInt(crc, texture);
            }
        }
        if (!valid) {
            minX = minY = minZ = maxX = maxY = maxZ = minU = minV = maxU = maxV = 0.0F;
        }
        int primitiveCount = Math.max(0, vertexCount / 4);
        int indexCount = primitiveCount * 6;
        long contentHash = crc.getValue();
        int primitiveMetadataRecords = 0;
        int unknownPrimitiveCount = 0;
        int nonFluidTranslucentPrimitiveCount = 0;
        int genericFluidPrimitiveCount = 0;
        int builtinWaterPrimitiveCount = 0;
        int unsupportedFluidPrimitiveCount = 0;
        int[] primitiveMetadata = mesh.getPrimitiveMetadata();
        if (primitiveMetadata != null) {
            for (int offset = 0; offset + NativeSectionMeshBuilder.PRIMITIVE_METADATA_RECORD_INTS <= primitiveMetadata.length;
                 offset += NativeSectionMeshBuilder.PRIMITIVE_METADATA_RECORD_INTS) {
                primitiveMetadataRecords++;
                switch (primitiveMetadata[offset]) {
                    case NativeSectionMeshBuilder.PRIMITIVE_KIND_UNKNOWN -> unknownPrimitiveCount++;
                    case NativeSectionMeshBuilder.PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT -> nonFluidTranslucentPrimitiveCount++;
                    case NativeSectionMeshBuilder.PRIMITIVE_KIND_GENERIC_FLUID -> genericFluidPrimitiveCount++;
                    case NativeSectionMeshBuilder.PRIMITIVE_KIND_BUILTIN_WATER -> builtinWaterPrimitiveCount++;
                    case NativeSectionMeshBuilder.PRIMITIVE_KIND_UNSUPPORTED_FLUID -> unsupportedFluidPrimitiveCount++;
                    default -> unknownPrimitiveCount++;
                }
            }
        }
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
                primitiveMetadataRecords,
                unknownPrimitiveCount,
                nonFluidTranslucentPrimitiveCount,
                genericFluidPrimitiveCount,
                builtinWaterPrimitiveCount,
                unsupportedFluidPrimitiveCount,
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
                    index,
                    index / 4,
                    segment + 1 < segments.length ? segments[segment + 1] : -1,
                    output.render.getOriginX() + decodePosition(buffer.getInt(offset), buffer.getInt(offset + 4), 0),
                    output.render.getOriginY() + decodePosition(buffer.getInt(offset), buffer.getInt(offset + 4), 1),
                    output.render.getOriginZ() + decodePosition(buffer.getInt(offset), buffer.getInt(offset + 4), 2),
                    decodeTexture(texture & 0xffff),
                    decodeTexture((texture >>> 16) & 0xffff),
                    compactColor & 0xff,
                    (compactColor >>> 8) & 0xff,
                    (compactColor >>> 16) & 0xff,
                    (compactColor >>> 24) & 0xff,
                    separateAo ? ((compactColor >>> 24) & 0xff) / 255.0F : 1.0F,
                    (lightMaterial & 0xff) >>> 4,
                    ((lightMaterial >>> 8) & 0xff) >>> 4,
                    (lightMaterial >>> 16) & 0xff,
                    wordAt(buffer, offset, stride, 20),
                    wordAt(buffer, offset, stride, 24),
                    wordAt(buffer, offset, stride, 28),
                    wordAt(buffer, offset, stride, 32),
                    wordAt(buffer, offset, stride, 36)
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
            int viewportHeight
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
        writeCoverageEvent(false, stage, layer, frameId, flags, animatedSections, cameraX, cameraY, cameraZ,
                viewportWidth, viewportHeight, recordCount, vertexTotal, indexTotal, primitiveTotal,
                missingCoverage, executedRecords, appender);
    }

    /**
     * Whole-frame capture receipts must survive an arbitrarily busy CPU mesh
     * trace. They retain their own bounded budget rather than displacing or
     * being displaced by build diagnostics.
     */
    private static void writeWholeFrameCoverageEvent(
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
        writeCoverageEvent(true, stage, layer, frameId, flags, animatedSections, cameraX, cameraY, cameraZ,
                viewportWidth, viewportHeight, recordCount, vertexTotal, indexTotal, primitiveTotal,
                missingCoverage, executedRecords, appender);
    }

    private static void writeCoverageEvent(
            boolean wholeFrameCritical,
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
        if (wholeFrameCritical
                && WHOLE_FRAME_COVERAGE_EVENTS.incrementAndGet() > MAX_WHOLE_FRAME_COVERAGE_EVENTS) {
            return;
        }
        if (!wholeFrameCritical && eventIndex > MAX_COVERAGE_EVENTS) {
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
        appendField(json, "deterministicRenderedFrameIndex",
                net.minecraft.client.dev.DeterministicCameraCapture.currentRenderedFrameIndex()).append(", ");
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
        appendField(records, "genericFluidPrimitiveCount", value.genericFluidPrimitiveCount()).append(", ");
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
            if (i > 0) {
                json.append(",");
            }
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
            appendField(json, "extensionWord36", String.format(Locale.ROOT, "%08x", sample.extensionWord36())).append(", ");
            appendTerrainAtlasSample(json, sample.u(), sample.v());
            json.append("}");
        }
    }

    /**
     * Bounded observability for the copied atlas contract. The normal source
     * renderer continues to use its existing texture and UV path; this merely
     * shows whether a vertex's source UV and a vertically mirrored UV resolve
     * to the same semantic sprite.
     */
    private static void appendTerrainAtlasSample(StringBuilder builder, float u, float v) {
        builder.append("\"atlasSampling\": {");
        try {
            TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
            int width = atlas.width;
            int height = atlas.height;
            appendField(builder, "width", width).append(", ");
            appendField(builder, "height", height).append(", ");
            appendField(builder, "sourcePixel", atlasPixel(u, v, width, height)).append(", ");
            appendSpriteAtUv(builder, "source", atlas, u, v);
            builder.append(", ");
            float mirroredV = 1.0F - v;
            appendField(builder, "mirroredPixel", atlasPixel(u, mirroredV, width, height)).append(", ");
            appendSpriteAtUv(builder, "mirrored", atlas, u, mirroredV);
        } catch (RuntimeException ignored) {
            appendField(builder, "status", "atlas-unavailable");
        }
        builder.append("}");
    }

    private static String atlasPixel(float u, float v, int width, int height) {
        int x = clampAtlasPixel(u, width);
        int y = clampAtlasPixel(v, height);
        return x + "," + y;
    }

    private static int clampAtlasPixel(float coordinate, int extent) {
        if (extent <= 0 || !Float.isFinite(coordinate)) {
            return -1;
        }
        return Math.max(0, Math.min(extent - 1, (int)Math.floor(coordinate * extent)));
    }

    private static void appendSpriteAtUv(StringBuilder builder, String name, TextureAtlas atlas, float u, float v) {
        TextureAtlasSprite selected = null;
        for (TextureAtlasSprite sprite : atlas.texturesByName.values()) {
            if (u >= sprite.getU0() && u <= sprite.getU1() && v >= sprite.getV0() && v <= sprite.getV1()) {
                selected = sprite;
                break;
            }
        }
        if (selected == null || selected.contents() == null || selected.contents().name() == null) {
            appendField(builder, name + "Sprite", "none");
            return;
        }
        appendField(builder, name + "Sprite", selected.contents().name().toString()).append(", ");
        appendField(builder, name + "Bounds", selected.getX() + "," + selected.getY() + ","
                + selected.contents().width() + "," + selected.contents().height());
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

    private static float signedNormal(int packed, int shift) {
        return (byte) ((packed >>> shift) & 0xff) / 127.0F;
    }

    private static String layerAnimatedMaterialClassification(MeshCoverage coverage) {
        if (coverage.genericFluidPrimitiveCount() > 0) {
            return "generic-fluid";
        }
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
        if (value == null || value.isBlank()) {
            value = System.getenv("MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_SECTION");
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

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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

    private record AppearanceSource(String layer, int vertexStride, boolean separateAo, AppearanceSample[] samples) {
    }

    private record AppearanceSample(
            int vertexIndex,
            int primitiveIndex,
            int faceCode,
            float worldX,
            float worldY,
            float worldZ,
            float u,
            float v,
            int red,
            int green,
            int blue,
            int alphaOrAo,
            float ao,
            int blockLight,
            int skyLight,
            int materialBits,
            int extensionWord20,
            int extensionWord24,
            int extensionWord28,
            int extensionWord32,
            int extensionWord36
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
            int genericFluidPrimitiveCount,
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
                0,
                "",
                "missing"
        );
    }
}
