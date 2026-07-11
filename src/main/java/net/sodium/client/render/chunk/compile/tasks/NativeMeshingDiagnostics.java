package net.sodium.client.render.chunk.compile.tasks;

import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class NativeMeshingDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger("MattMC-NativeMeshing");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final AtomicInteger DUMP_COUNTER = new AtomicInteger();

    private static final boolean DUMP_SECTIONS = Boolean.getBoolean("mattmc.nativeMeshing.dumpSections");
    private static final boolean REPORT_FALLBACKS = Boolean.getBoolean("mattmc.nativeMeshing.reportFallbacks");
    private static final boolean FORCE_JAVA_PRODUCERS = Boolean.getBoolean("mattmc.nativeMeshing.forceJavaProducers");
    private static final boolean FORCE_JAVA_MODELS = FORCE_JAVA_PRODUCERS
            || Boolean.getBoolean("mattmc.nativeMeshing.forceJavaModels");
    private static final boolean FORCE_JAVA_FLUIDS = FORCE_JAVA_PRODUCERS
            || Boolean.getBoolean("mattmc.nativeMeshing.forceJavaFluids");
    private static final boolean FORCE_WHITE_TINT = Boolean.getBoolean("mattmc.nativeMeshing.forceWhiteTint");
    private static final boolean FORCE_NO_TRANSLUCENT_SORT = Boolean.getBoolean("mattmc.nativeMeshing.forceNoTranslucentSort");
    private static final int DUMP_LIMIT = Integer.getInteger("mattmc.nativeMeshing.dumpSectionLimit", 16);
    private static final int SAMPLE_LIMIT = Integer.getInteger("mattmc.nativeMeshing.fallbackSampleLimit", 24);
    private static final Path DIAGNOSTICS_ROOT = Path.of(System.getProperty("mattmc.nativeMeshing.diagnosticsDir",
            "build/native-meshing-diagnostics"));

    private NativeMeshingDiagnostics() {
    }

    static boolean shouldDumpSections() {
        return DUMP_SECTIONS && DUMP_COUNTER.get() < DUMP_LIMIT;
    }

    static boolean forceJavaProducers() {
        return FORCE_JAVA_PRODUCERS;
    }

    static boolean forceJavaModels() {
        return FORCE_JAVA_MODELS;
    }

    static boolean forceJavaFluids() {
        return FORCE_JAVA_FLUIDS;
    }

    static boolean forceWhiteTint() {
        return FORCE_WHITE_TINT;
    }

    static boolean forceNoTranslucentSort() {
        return FORCE_NO_TRANSLUCENT_SORT;
    }

    static Path dumpSectionSnapshot(int sectionIndex, int minX, int minY, int minZ, long address, int recordCount) {
        if (!shouldDumpSections()) {
            return null;
        }

        int dumpIndex = DUMP_COUNTER.incrementAndGet();
        if (dumpIndex > DUMP_LIMIT) {
            return null;
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        Path directory = DIAGNOSTICS_ROOT.resolve(timestamp);
        String baseName = "section_" + sectionIndex + "_" + minX + "_" + minY + "_" + minZ + "_" + dumpIndex;
        Path binaryPath = directory.resolve(baseName + ".native-section.bin");
        Path metadataPath = directory.resolve(baseName + ".metadata.txt");
        int bytes = recordCount * NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE;

        try {
            Files.createDirectories(directory);
            ByteBuffer snapshot = MemoryUtil.memByteBuffer(address, bytes).duplicate();
            try (FileChannel channel = FileChannel.open(binaryPath, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                while (snapshot.hasRemaining()) {
                    channel.write(snapshot);
                }
            }

            String metadata = "sectionIndex=" + sectionIndex + "\n"
                    + "origin=" + minX + "," + minY + "," + minZ + "\n"
                    + "recordCount=" + recordCount + "\n"
                    + "recordStride=" + NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE + "\n"
                    + "binary=" + binaryPath.toAbsolutePath() + "\n";
            Files.writeString(metadataPath, metadata, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            LOGGER.info("Dumped native meshing section snapshot {} with metadata {}", binaryPath.toAbsolutePath(),
                    metadataPath.toAbsolutePath());
            return binaryPath;
        } catch (IOException exception) {
            LOGGER.warn("Failed to dump native meshing section snapshot for section {} at {},{},{}", sectionIndex,
                    minX, minY, minZ, exception);
            return null;
        }
    }

    static long loadSectionSnapshotDump(Path path) throws IOException {
        long bytes = Files.size(path);
        if (bytes % NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE != 0) {
            throw new IOException("Native section snapshot dump has invalid byte length " + bytes + ": " + path);
        }
        if (bytes > Integer.MAX_VALUE) {
            throw new IOException("Native section snapshot dump is too large to replay in one buffer: " + path);
        }

        long address = MemoryUtil.nmemAlloc(bytes);
        if (address == 0L) {
            throw new OutOfMemoryError("Could not allocate native replay buffer for " + path);
        }

        try {
            ByteBuffer target = MemoryUtil.memByteBuffer(address, (int) bytes);
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                while (target.hasRemaining()) {
                    if (channel.read(target) < 0) {
                        break;
                    }
                }
            }
            target.flip();
            return address;
        } catch (IOException | RuntimeException | Error throwable) {
            MemoryUtil.nmemFree(address);
            throw throwable;
        }
    }

    static FallbackStats createFallbackStats() {
        return REPORT_FALLBACKS ? new FallbackStats() : FallbackStats.DISABLED;
    }

    static final class FallbackStats {
        private static final FallbackStats DISABLED = new FallbackStats(false);

        private final boolean enabled;
        private int nativeModelBlocks;
        private int nativeFluidBlocks;
        private int modelFallbackBlocks;
        private int modelFallbackQuads;
        private int fluidFallbackBlocks;
        private int fluidFallbackQuads;
        private int appenderFallbackQuads;
        private int nativeSolidQuads;
        private int nativeCutoutQuads;
        private int nativeTranslucentQuads;
        private final LinkedHashMap<String, Integer> modelFallbackStates = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> fluidFallbackStates = new LinkedHashMap<>();

        FallbackStats() {
            this(true);
        }

        private FallbackStats(boolean enabled) {
            this.enabled = enabled;
        }

        boolean enabled() {
            return this.enabled;
        }

        void recordNativeModelBlock() {
            if (this.enabled) {
                this.nativeModelBlocks++;
            }
        }

        void recordNativeFluidBlock() {
            if (this.enabled) {
                this.nativeFluidBlocks++;
            }
        }

        void recordModelFallback(BlockState state, int quadCount) {
            if (!this.enabled) {
                return;
            }
            this.modelFallbackBlocks++;
            this.modelFallbackQuads += Math.max(quadCount, 0);
            incrementBounded(this.modelFallbackStates, state.toString());
        }

        void recordFluidFallback(BlockState blockState, FluidState fluidState, int quadCount) {
            if (!this.enabled) {
                return;
            }
            this.fluidFallbackBlocks++;
            this.fluidFallbackQuads += Math.max(quadCount, 0);
            incrementBounded(this.fluidFallbackStates, blockState + " fluid=" + fluidState);
        }

        void recordAppenderFallbackQuads(int quadCount) {
            if (this.enabled) {
                this.appenderFallbackQuads += Math.max(quadCount, 0);
            }
        }

        void recordNativeSnapshotQuads(int solid, int cutout, int translucent) {
            if (!this.enabled) {
                return;
            }
            this.nativeSolidQuads += Math.max(solid, 0);
            this.nativeCutoutQuads += Math.max(cutout, 0);
            this.nativeTranslucentQuads += Math.max(translucent, 0);
        }

        void report(int sectionIndex, BlockPos origin) {
            if (!this.enabled) {
                return;
            }

            int modelBlocks = this.nativeModelBlocks + this.modelFallbackBlocks;
            int fluidBlocks = this.nativeFluidBlocks + this.fluidFallbackBlocks;
            double modelFallbackRate = modelBlocks == 0 ? 0.0D : (double) this.modelFallbackBlocks / modelBlocks;
            double fluidFallbackRate = fluidBlocks == 0 ? 0.0D : (double) this.fluidFallbackBlocks / fluidBlocks;
            int nativeQuads = this.nativeSolidQuads + this.nativeCutoutQuads + this.nativeTranslucentQuads;

            LOGGER.info("Native meshing fallback report section={} origin={},{},{} "
                            + "nativeModelBlocks={} modelFallbackBlocks={} modelFallbackQuads={} modelFallbackRate={} "
                            + "nativeFluidBlocks={} fluidFallbackBlocks={} fluidFallbackQuads={} fluidFallbackRate={} "
                            + "appenderFallbackQuads={} nativeQuads={} nativeSolidQuads={} nativeCutoutQuads={} nativeTranslucentQuads={}",
                    sectionIndex, origin.getX(), origin.getY(), origin.getZ(),
                    this.nativeModelBlocks, this.modelFallbackBlocks, this.modelFallbackQuads,
                    String.format("%.4f", modelFallbackRate),
                    this.nativeFluidBlocks, this.fluidFallbackBlocks, this.fluidFallbackQuads,
                    String.format("%.4f", fluidFallbackRate),
                    this.appenderFallbackQuads, nativeQuads, this.nativeSolidQuads, this.nativeCutoutQuads,
                    this.nativeTranslucentQuads);

            if (!this.modelFallbackStates.isEmpty()) {
                LOGGER.info("Native meshing model fallback states: {}", this.modelFallbackStates);
            }
            if (!this.fluidFallbackStates.isEmpty()) {
                LOGGER.info("Native meshing fluid fallback states: {}", this.fluidFallbackStates);
            }
        }

        private static void incrementBounded(LinkedHashMap<String, Integer> map, String key) {
            Integer previous = map.get(key);
            if (previous != null) {
                map.put(key, previous + 1);
            } else if (map.size() < SAMPLE_LIMIT) {
                map.put(key, 1);
            }
        }
    }
}
