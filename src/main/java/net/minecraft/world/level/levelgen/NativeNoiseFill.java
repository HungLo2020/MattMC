package net.minecraft.world.level.levelgen;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.NativeNoiseState;
import org.jetbrains.annotations.Nullable;

/** Native-owned NOISE-stage block fill for {@code NoiseBasedChunkGenerator.doFill}.
 * Java still fills interpolation slices, cell density caches and aquifer cell
 * materials; Rust runs every block in the original order (interpolation,
 * substance, ore veins, default block) and owns the resulting section palettes,
 * counters, both world-generation heightmaps and fluid post-processing marks,
 * which Java installs once. Chunks outside the gate keep the Java loop. */
final class NativeNoiseFill {
    // Copies its configuration from borrowed heap arrays; one small allocation.
    private static final MethodHandle CREATE = bind("create", true, FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = bind("release", false, FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    // Bounded to one cell; borrows heap arrays for that call only.
    private static final MethodHandle CELL = bind("cell", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle SECTION = bind("section", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle HEIGHTMAPS = bind("heightmaps", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle POST_PROCESS = bind("post_process", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    // Verification only: replays section writes without a fill.
    private static final MethodHandle REPLAY = bind("replay_section", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final int FLAG_AIR = 1, FLAG_BLOCKS_MOTION = 2, FLAG_FLUID = 4, FLAG_RANDOM_TICKS = 8;
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaNoiseFill");
    // Completed native fills, so tests can prove the gate engaged.
    static final java.util.concurrent.atomic.AtomicLong RUNS = new java.util.concurrent.atomic.AtomicLong();
    private static volatile MemorySegment flagTable;
    private static volatile int flagCount;

    private static MethodHandle bind(String suffix, boolean critical, FunctionDescriptor descriptor) {
        return critical
            ? NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_fill_" + suffix, descriptor, Linker.Option.critical(true))
            : NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_fill_" + suffix, descriptor);
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    /** Per block-state flags, built once from the frozen block-state registry. */
    private static synchronized MemorySegment flags() {
        int size = Block.BLOCK_STATE_REGISTRY.size();
        if (flagTable == null || flagCount != size) {
            MemorySegment table = Arena.global().allocate(size);
            for (int id = 0; id < size; id++) {
                BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
                int flags = 0;
                if (state != null) {
                    if (state.isAir()) flags |= FLAG_AIR;
                    if (state.blocksMotion()) flags |= FLAG_BLOCKS_MOTION;
                    if (!state.getFluidState().isEmpty()) flags |= FLAG_FLUID;
                    if (state.isRandomlyTicking()) flags |= FLAG_RANDOM_TICKS;
                }
                table.set(ValueLayout.JAVA_BYTE, id, (byte)flags);
            }
            flagTable = table;
            flagCount = size;
        }
        return flagTable;
    }

    private static int id(BlockState state) {
        return Block.BLOCK_STATE_REGISTRY.getId(state);
    }

    private final NoiseChunk chunk;
    private final ChunkAccess chunkAccess;
    @Nullable
    private final NativeAquifer aquifer;
    @Nullable
    private final NoiseChunk.NoiseInterpolator toggle, ridgedA, ridgedB;
    @Nullable
    private final NativeNoiseState gap;
    private final int[] ints = new int[25];
    private final long[] longs = new long[2];
    private final double[] doubles = new double[4];
    private final double[] corners = new double[24];

    private NativeNoiseFill(NoiseChunk chunk, ChunkAccess chunkAccess, @Nullable NativeAquifer aquifer, @Nullable NoiseChunk.NoiseInterpolator toggle,
                            @Nullable NoiseChunk.NoiseInterpolator ridgedA, @Nullable NoiseChunk.NoiseInterpolator ridgedB, @Nullable NativeNoiseState gap) {
        this.chunk = chunk;
        this.chunkAccess = chunkAccess;
        this.aquifer = aquifer;
        this.toggle = toggle;
        this.ridgedA = ridgedA;
        this.ridgedB = ridgedB;
        this.gap = gap;
    }

    /** The eligibility gate; null keeps the Java loop. Every condition the
     * native loop assumes about the material rules and chunk state is checked. */
    @Nullable
    static NativeNoiseFill prepare(NoiseGeneratorSettings settings, NoiseChunk chunk, ChunkAccess chunkAccess, Heightmap oceanFloor, Heightmap worldSurface,
                                   int minCellY, int cellCountY) {
        if (!enabled || SharedConstants.DEBUG_ORE_VEINS || SharedConstants.DEBUG_AQUIFERS || SharedConstants.DEBUG_DISABLE_FLUID_GENERATION
            || SharedConstants.debugVoidTerrain(chunkAccess.getPos()) || chunk.getClass() != NoiseChunk.class || chunk.getBlender() != Blender.empty()
            || chunk.aquiferDensity == null || chunk.cellCountXZ * chunk.cellWidth != 16) {
            return null;
        }
        int defaultBlock = id(settings.defaultBlock()), air = id(Blocks.AIR.defaultBlockState());
        if (defaultBlock < 0 || air < 0) return null;
        // Every section the fill can write must be fresh air; heightmaps unwritten.
        int top = chunkAccess.getSectionIndex((minCellY + cellCountY) * chunk.cellHeight - 1), bottom = chunkAccess.getSectionIndex(minCellY * chunk.cellHeight);
        if (bottom < 0 || top >= chunkAccess.getSectionsCount()) return null;
        for (int index = bottom; index <= top; index++) {
            if (!chunkAccess.getSection(index).isUntouchedAirForGeneration()) return null;
        }
        for (long word : oceanFloor.getRawData()) if (word != 0) return null;
        for (long word : worldSurface.getRawData()) if (word != 0) return null;

        NativeNoiseFill fill;
        Aquifer aquifer = chunk.aquifer();
        int[] ints = new int[25];
        AquiferFluidPicker picker;
        if (aquifer instanceof Aquifer.NoiseBasedAquifer noiseAquifer && noiseAquifer.globalFluidPicker() instanceof AquiferFluidPicker known) {
            NativeAquifer nativeAquifer = noiseAquifer.nativeAquifer();
            if (!nativeAquifer.nativeFillReady()) return null;
            picker = known;
            ints[9] = 1;
            ints[22] = noiseAquifer.skipSamplingAboveY();
            ints[23] = picker.fluid.fluidType().is(Blocks.LAVA) ? 1 : 0;
            ints[24] = id(Blocks.LAVA.defaultBlockState());
            fill = oreVeins(settings, chunk, chunkAccess, nativeAquifer);
        } else if (aquifer instanceof Aquifer.Disabled disabled && disabled.fluidPicker() instanceof AquiferFluidPicker known) {
            picker = known;
            ints[9] = 2;
            fill = oreVeins(settings, chunk, chunkAccess, null);
        } else {
            return null;
        }
        int lava = id(picker.lava.fluidType()), fluid = id(picker.fluid.fluidType());
        if (fill == null || lava < 0 || fluid < 0 || ints[24] < 0) return null;
        ints[10] = Math.min(-54, picker.fluid.fluidLevel());
        ints[11] = picker.lava.fluidLevel();
        ints[12] = lava;
        ints[13] = picker.fluid.fluidLevel();
        ints[14] = fluid;
        System.arraycopy(ints, 9, fill.ints, 9, 6);
        System.arraycopy(ints, 22, fill.ints, 22, 3);
        fill.ints[0] = chunkAccess.getMinY();
        fill.ints[1] = chunkAccess.getHeight();
        fill.ints[2] = chunkAccess.getMinSectionY();
        fill.ints[3] = chunkAccess.getSectionsCount();
        fill.ints[4] = chunk.cellWidth;
        fill.ints[5] = chunk.cellHeight;
        fill.ints[6] = air;
        fill.ints[7] = defaultBlock;
        fill.ints[8] = chunkAccess.getSection(bottom).generatedGlobalPaletteBits();
        return fill;
    }

    /** Matches the vanilla ore vein rule; a non-matching shape keeps the Java loop. */
    @Nullable
    private static NativeNoiseFill oreVeins(NoiseGeneratorSettings settings, NoiseChunk chunk, ChunkAccess chunkAccess, @Nullable NativeAquifer aquifer) {
        if (!settings.oreVeinsEnabled()) {
            return new NativeNoiseFill(chunk, chunkAccess, aquifer, null, null, null, null);
        }
        if (chunk.veinToggle == null || chunk.veinToggle.getClass() != NoiseChunk.NoiseInterpolator.class
            || !(chunk.veinRidged instanceof DensityFunctions.MulOrAdd ridged) || ridged.specificType() != DensityFunctions.MulOrAdd.Type.ADD
            || !(ridged.input() instanceof DensityFunctions.Ap2 max) || max.type() != DensityFunctions.TwoArgumentSimpleFunction.Type.MAX
            || !(max.argument1() instanceof DensityFunctions.Mapped a) || a.type() != DensityFunctions.Mapped.Type.ABS
            || a.input().getClass() != NoiseChunk.NoiseInterpolator.class
            || !(max.argument2() instanceof DensityFunctions.Mapped b) || b.type() != DensityFunctions.Mapped.Type.ABS
            || b.input().getClass() != NoiseChunk.NoiseInterpolator.class
            || !(chunk.veinGap instanceof DensityFunctions.Noise gap)) {
            return null;
        }
        PositionalRandomFactory random = chunk.randomState().oreRandom();
        NativeNoiseFill fill;
        NativeNoiseState gapState = gap.noise().noise() == null ? null : gap.noise().noise().nativeState();
        fill = new NativeNoiseFill(chunk, chunkAccess, aquifer, (NoiseChunk.NoiseInterpolator)chunk.veinToggle,
            (NoiseChunk.NoiseInterpolator)a.input(), (NoiseChunk.NoiseInterpolator)b.input(), gapState);
        if (random.getClass() == XoroshiroRandomSource.XoroshiroPositionalRandomFactory.class) {
            var factory = (XoroshiroRandomSource.XoroshiroPositionalRandomFactory)random;
            fill.ints[15] = 1;
            fill.longs[0] = factory.seedLo();
            fill.longs[1] = factory.seedHi();
        } else if (random.getClass() == LegacyRandomSource.LegacyPositionalRandomFactory.class) {
            fill.ints[15] = 2;
            fill.longs[0] = ((LegacyRandomSource.LegacyPositionalRandomFactory)random).seed();
        } else {
            return null;
        }
        BlockState[] states = {
            Blocks.COPPER_ORE.defaultBlockState(), Blocks.RAW_COPPER_BLOCK.defaultBlockState(), Blocks.GRANITE.defaultBlockState(),
            Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), Blocks.RAW_IRON_BLOCK.defaultBlockState(), Blocks.TUFF.defaultBlockState()
        };
        for (int index = 0; index < states.length; index++) {
            fill.ints[16 + index] = id(states[index]);
            if (fill.ints[16 + index] < 0) return null;
        }
        fill.doubles[0] = ridged.argument();
        fill.doubles[1] = max.argument2().maxValue();
        fill.doubles[2] = gap.xzScale();
        fill.doubles[3] = gap.yScale();
        return fill;
    }

    /** doFill's cell loop with every block decided and written natively. */
    void run(int minCellY, int cellCountY) {
        long handle;
        try {
            handle = (long)CREATE.invokeExact(MemorySegment.ofArray(this.ints), MemorySegment.ofArray(this.longs), MemorySegment.ofArray(this.doubles),
                flags(), flagCount);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot start native noise fill", error);
        }
        if (handle == 0) throw new IllegalStateException("Native noise fill rejected its configuration");
        try {
            int width = this.chunk.cellWidth, height = this.chunk.cellHeight, cells = 16 / width;
            int minX = this.chunkAccess.getPos().getMinBlockX(), minZ = this.chunkAccess.getPos().getMinBlockZ();
            MemorySegment density = MemorySegment.ofArray(this.chunk.aquiferDensity.values);
            MemorySegment cornerMemory = MemorySegment.ofArray(this.corners);
            MemorySegment gapMemory = this.gap == null ? MemorySegment.NULL : this.gap.state();
            boolean ore = this.toggle != null;
            this.chunk.initializeForFirstCellX();
            for (int cellX = 0; cellX < cells; cellX++) {
                this.chunk.advanceCellX(cellX);
                for (int cellZ = 0; cellZ < cells; cellZ++) {
                    for (int cellY = cellCountY - 1; cellY >= 0; cellY--) {
                        this.chunk.selectCellYZ(cellY, cellZ);
                        int x = minX + cellX * width, y = (minCellY + cellY) * height, z = minZ + cellZ * width;
                        if (ore) {
                            this.toggle.copyCellCorners(this.corners, 0);
                            this.ridgedA.copyCellCorners(this.corners, 8);
                            this.ridgedB.copyCellCorners(this.corners, 16);
                        }
                        MemorySegment cellCorners = ore ? cornerMemory : MemorySegment.NULL;
                        int status = (int)CELL.invokeExact(handle, x, y, z, density, MemorySegment.NULL, cellCorners, gapMemory);
                        if (status == 1) {
                            // Some block reaches the aquifer batch: prepare it as the
                            // first such block would have, then fill the cell.
                            MemorySegment materials = MemorySegment.ofArray(this.aquifer.prepareCellMaterials(x, y, z));
                            status = (int)CELL.invokeExact(handle, x, y, z, density, materials, cellCorners, gapMemory);
                        }
                        if (status != 0) throw new IllegalStateException("Native noise fill cell failed: " + status);
                    }
                }
                this.chunk.swapSlices();
            }
            this.chunk.stopInterpolation();
            this.install(handle);
            RUNS.incrementAndGet();
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Native noise fill failed", error);
        } finally {
            Reference.reachabilityFence(this.gap);
            try { RELEASE.invokeExact(handle); }
            catch (Throwable error) { throw new IllegalStateException("Cannot release native noise fill", error); }
        }
    }

    /** Installs a native replay of (index, state id) writes into a fresh section; for verification. */
    static void replayInto(LevelChunkSection section, int[] writes) {
        int[] info = new int[7];
        int[] palette = new int[512];
        long[] raw = new long[4096];
        int air = id(Blocks.AIR.defaultBlockState());
        int result;
        try {
            result = (int)REPLAY.invokeExact(air, section.generatedGlobalPaletteBits(), MemorySegment.ofArray(writes), writes.length / 2,
                MemorySegment.ofArray(info), MemorySegment.ofArray(palette), palette.length, MemorySegment.ofArray(raw), raw.length);
        } catch (Throwable error) {
            throw new IllegalStateException("Native section replay failed", error);
        }
        if (result != 1) throw new IllegalStateException("Native section replay rejected: " + result);
        List<BlockState> states = new ArrayList<>(info[2]);
        for (int entry = 0; entry < info[2]; entry++) states.add(Block.stateById(palette[entry]));
        section.installGenerated(info[0], states, java.util.Arrays.copyOf(raw, info[3]), 0, 0, 0);
    }

    private void install(long handle) throws Throwable {
        int[] info = new int[7];
        int[] palette = new int[256];
        long[] raw = new long[4096];
        for (int index = 0; index < this.chunkAccess.getSectionsCount(); index++) {
            int written = (int)SECTION.invokeExact(handle, index, MemorySegment.ofArray(info), MemorySegment.ofArray(palette), palette.length,
                MemorySegment.ofArray(raw), raw.length);
            if (written == 0) continue;
            if (written != 1) throw new IllegalStateException("Native noise fill section overflow");
            List<BlockState> states = new ArrayList<>(info[2]);
            for (int entry = 0; entry < info[2]; entry++) states.add(Block.stateById(palette[entry]));
            LevelChunkSection section = this.chunkAccess.getSection(index);
            section.installGenerated(info[0], states, java.util.Arrays.copyOf(raw, info[3]), info[4], info[5], info[6]);
        }
        Heightmap oceanFloor = this.chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = this.chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        long[] ocean = new long[oceanFloor.getRawData().length], surface = new long[ocean.length];
        int length = (int)HEIGHTMAPS.invokeExact(handle, MemorySegment.ofArray(ocean), MemorySegment.ofArray(surface), ocean.length);
        if (length != ocean.length) throw new IllegalStateException("Native noise fill heightmap size " + length);
        oceanFloor.setRawData(this.chunkAccess, Heightmap.Types.OCEAN_FLOOR_WG, ocean);
        worldSurface.setRawData(this.chunkAccess, Heightmap.Types.WORLD_SURFACE_WG, surface);
        int[] positions = new int[3 * 64];
        int count = (int)POST_PROCESS.invokeExact(handle, MemorySegment.ofArray(positions), 64);
        if (count < 0) {
            positions = new int[3 * -count];
            count = (int)POST_PROCESS.invokeExact(handle, MemorySegment.ofArray(positions), -count);
        }
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int index = 0; index < count; index++) {
            position.set(positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2]);
            this.chunkAccess.markPosForPostprocessing(position);
        }
    }
}
