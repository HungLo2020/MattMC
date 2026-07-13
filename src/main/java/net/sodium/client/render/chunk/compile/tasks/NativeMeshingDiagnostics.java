package net.sodium.client.render.chunk.compile.tasks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;

final class NativeMeshingDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger("MattMC-NativeMeshing");

    private static final boolean REPORT_FALLBACKS = Boolean.getBoolean("mattmc.nativeMeshing.reportFallbacks");
    // Diagnostic A/B switches only. They intentionally route work to Java for comparison captures,
    // but production coverage should keep ordinary vanilla/MattMC geometry on the native path.
    private static final boolean FORCE_JAVA_PRODUCERS = Boolean.getBoolean("mattmc.nativeMeshing.forceJavaProducers");
    private static final boolean FORCE_JAVA_MODELS = FORCE_JAVA_PRODUCERS
            || Boolean.getBoolean("mattmc.nativeMeshing.forceJavaModels");
    private static final boolean FORCE_JAVA_FLUIDS = FORCE_JAVA_PRODUCERS
            || Boolean.getBoolean("mattmc.nativeMeshing.forceJavaFluids");
    private static final boolean FORCE_WHITE_TINT = Boolean.getBoolean("mattmc.nativeMeshing.forceWhiteTint");
    private static final boolean FORCE_NO_TRANSLUCENT_SORT = Boolean.getBoolean("mattmc.nativeMeshing.forceNoTranslucentSort");
    private static final int SAMPLE_LIMIT = Integer.getInteger("mattmc.nativeMeshing.fallbackSampleLimit", 24);

    private NativeMeshingDiagnostics() {
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

    static FallbackStats createFallbackStats() {
        return REPORT_FALLBACKS ? new FallbackStats() : FallbackStats.DISABLED;
    }

    static final class FallbackStats {
        private static final FallbackStats DISABLED = new FallbackStats(false);

        private final boolean enabled;
        private int nativeModelBlocks;
        private int nativeFluidBlocks;
        private int nativeWaterBlocks;
        private int modelFallbackBlocks;
        private int modelFallbackQuads;
        private int fluidFallbackBlocks;
        private int waterFallbackBlocks;
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

        void recordNativeFluidBlock(FluidState fluidState) {
            if (this.enabled) {
                this.nativeFluidBlocks++;
                if (fluidState.is(Fluids.WATER)) {
                    this.nativeWaterBlocks++;
                }
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
            if (fluidState.is(Fluids.WATER)) {
                this.waterFallbackBlocks++;
            }
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
                            + "nativeWaterBlocks={} waterFallbackBlocks={} "
                            + "appenderFallbackQuads={} nativeQuads={} nativeSolidQuads={} nativeCutoutQuads={} nativeTranslucentQuads={}",
                    sectionIndex, origin.getX(), origin.getY(), origin.getZ(),
                    this.nativeModelBlocks, this.modelFallbackBlocks, this.modelFallbackQuads,
                    String.format("%.4f", modelFallbackRate),
                    this.nativeFluidBlocks, this.fluidFallbackBlocks, this.fluidFallbackQuads,
                    String.format("%.4f", fluidFallbackRate),
                    this.nativeWaterBlocks, this.waterFallbackBlocks,
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
