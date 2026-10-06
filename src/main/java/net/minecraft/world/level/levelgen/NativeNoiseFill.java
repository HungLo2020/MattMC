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
 * {@link NativeNoiseRouter} fills the interpolation slices; Java still fills
 * cell density caches and aquifer cell materials; Rust runs every block in the original order (interpolation,
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
    // Rust-owned cell traversal: bind once, then one call per slice and per column of cells.
    private static final MethodHandle BIND = bind("bind", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    // The structure Beardifier's packed geometry, copied once per fill.
    private static final MethodHandle BEARDIFIER = bind("beardifier", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle SLICE = bind("slice", false, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle UPLOAD = bind("upload", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    // A whole column of cells: an ordinary downcall with off-heap buffers.
    private static final MethodHandle CELLS = bind("cells", false, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    // Tests compare traversals in one JVM; production reads the property once.
    private static volatile boolean nativeTraversal = !Boolean.getBoolean("mattmc.worldgen.javaCellTraversal");
    // Chunks whose cells Rust traversed, so tests can prove the gate engaged.
    static final java.util.concurrent.atomic.AtomicLong TRAVERSALS = new java.util.concurrent.atomic.AtomicLong();
    // Fills of chunks instantiated natively, without a wrapped Java graph.
    static final java.util.concurrent.atomic.AtomicLong INSTANCES = new java.util.concurrent.atomic.AtomicLong();
    // Verification only: replays section writes without a fill.
    private static final MethodHandle REPLAY = bind("replay_section", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final int FLAG_AIR = 1, FLAG_BLOCKS_MOTION = 2, FLAG_FLUID = 4, FLAG_RANDOM_TICKS = 8, FLAG_AIR_BLOCK = 16;
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaNoiseFill");
    // Completed native fills, so tests can prove the gate engaged.
    static final java.util.concurrent.atomic.AtomicLong RUNS = new java.util.concurrent.atomic.AtomicLong();
    private static volatile MemorySegment flagTable;
    private static volatile int flagCount;

    /** The state flag table's length, after {@link #flags()}. */
    static int flagCount() {
        return flagCount;
    }

    private static MethodHandle bind(String suffix, boolean critical, FunctionDescriptor descriptor) {
        return critical
            ? NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_fill_" + suffix, descriptor, Linker.Option.critical(true))
            : NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_fill_" + suffix, descriptor);
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    static void setNativeTraversal(boolean value) {
        nativeTraversal = value;
    }

    // Verification only: Java fills and uploads every slice, as for a slice the router declines.
    static volatile boolean uploadSlices;

    /** Per block-state flags, built once from the frozen block-state registry. */
    static synchronized MemorySegment flags() {
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
                    if (state.is(Blocks.AIR)) flags |= FLAG_AIR_BLOCK;
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
    // Ore veins are configured (from the chunk's interpolators or its template).
    private boolean ore;

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
            || chunk.cellCountXZ * chunk.cellWidth != 16) {
            return null;
        }
        // Native chunks bring their own cell program; others need the wrapped cell cache.
        if (chunk.nativeNoise() == null && chunk.aquiferDensity == null) return null;
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
        DensityFunctions.Noise gap;
        double ridgedConstant, ridgedBound;
        NoiseChunk.NoiseInterpolator toggle = null, ridgedA = null, ridgedB = null;
        NativeChunkNoise template = chunk.nativeNoise();
        if (template != null) {
            // The template checked the same vanilla shape on the unwrapped router.
            if (template.ore == null) return null;
            gap = template.gap;
            ridgedConstant = template.ridgedConstant;
            ridgedBound = template.ridgedBound;
        } else {
            if (chunk.veinToggle == null || chunk.veinToggle.getClass() != NoiseChunk.NoiseInterpolator.class
                || !(chunk.veinRidged instanceof DensityFunctions.MulOrAdd ridged) || ridged.specificType() != DensityFunctions.MulOrAdd.Type.ADD
                || !(ridged.input() instanceof DensityFunctions.Ap2 max) || max.type() != DensityFunctions.TwoArgumentSimpleFunction.Type.MAX
                || !(max.argument1() instanceof DensityFunctions.Mapped a) || a.type() != DensityFunctions.Mapped.Type.ABS
                || a.input().getClass() != NoiseChunk.NoiseInterpolator.class
                || !(max.argument2() instanceof DensityFunctions.Mapped b) || b.type() != DensityFunctions.Mapped.Type.ABS
                || b.input().getClass() != NoiseChunk.NoiseInterpolator.class
                || !(chunk.veinGap instanceof DensityFunctions.Noise wrappedGap)) {
                return null;
            }
            toggle = (NoiseChunk.NoiseInterpolator)chunk.veinToggle;
            ridgedA = (NoiseChunk.NoiseInterpolator)a.input();
            ridgedB = (NoiseChunk.NoiseInterpolator)b.input();
            gap = wrappedGap;
            ridgedConstant = ridged.argument();
            ridgedBound = max.argument2().maxValue();
        }
        PositionalRandomFactory random = chunk.randomState().oreRandom();
        NativeNoiseFill fill;
        NativeNoiseState gapState = gap.noise().noise() == null ? null : gap.noise().noise().nativeState();
        fill = new NativeNoiseFill(chunk, chunkAccess, aquifer, toggle, ridgedA, ridgedB, gapState);
        fill.ore = true;
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
        fill.doubles[0] = ridgedConstant;
        fill.doubles[1] = ridgedBound;
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
        NativeNoiseRouter router = null;
        try {
            NativeChunkNoise template = this.chunk.nativeNoise();
            if (template != null) {
                // Rust instantiates this chunk's noise; Java never wraps its graph.
                router = template.instance(this.chunk);
                this.bindTemplate(handle, router, template);
                this.traverse(handle, router, cellCountY, this.chunk.nativeCellDensities());
                this.install(handle);
                RUNS.incrementAndGet();
                TRAVERSALS.incrementAndGet();
                INSTANCES.incrementAndGet();
                return;
            }
            router = NativeNoiseRouter.create(this.chunk);
            NativeCellDensity cellDensity = router == null ? null : this.bindTraversal(handle, router);
            if (cellDensity != null) {
                this.traverse(handle, router, cellCountY, this.chunk.aquiferDensity.values);
                Reference.reachabilityFence(cellDensity);
                this.install(handle);
                RUNS.incrementAndGet();
                TRAVERSALS.incrementAndGet();
                return;
            }
            int width = this.chunk.cellWidth, height = this.chunk.cellHeight, cells = 16 / width;
            int minX = this.chunkAccess.getPos().getMinBlockX(), minZ = this.chunkAccess.getPos().getMinBlockZ();
            MemorySegment density = MemorySegment.ofArray(this.chunk.aquiferDensity.values);
            MemorySegment cornerMemory = MemorySegment.ofArray(this.corners);
            MemorySegment gapMemory = this.gap == null ? MemorySegment.NULL : this.gap.state();
            boolean ore = this.toggle != null;
            this.chunk.initializeForFirstCellX(router);
            for (int cellX = 0; cellX < cells; cellX++) {
                this.chunk.advanceCellX(cellX, router);
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
                            MemorySegment materials = MemorySegment.ofArray(this.aquifer.prepareCellMaterials(x, y, z, this.chunk.aquiferDensity.values));
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
            try {
                if (router != null) router.close();
            } finally {
                try { RELEASE.invokeExact(handle); }
                catch (Throwable error) { throw new IllegalStateException("Cannot release native noise fill", error); }
            }
        }
    }

    /** Binds the router and the cell cache's program to the fill so Rust walks
     * every cell. Requires the cell cache to be the chunk's only cell cache and
     * a NativeCellDensity over this chunk's interpolators; null keeps Java's
     * traversal. The returned evaluator keeps the program memory reachable. */
    @Nullable
    private NativeCellDensity bindTraversal(long handle, NativeNoiseRouter router) throws Throwable {
        NoiseChunk.CacheAllInCell cache = this.chunk.aquiferDensity;
        if (!nativeTraversal || this.chunk.cellCaches.size() != 1 || this.chunk.cellCaches.get(0) != cache
            || cache.evaluator().getClass() != NativeCellDensity.class) {
            return null;
        }
        NativeCellDensity cells = (NativeCellDensity)cache.evaluator();
        List<NoiseChunk.NoiseInterpolator> interpolators = this.chunk.interpolators;
        NoiseChunk.NoiseInterpolator[] inputs = cells.inputs();
        if (cells.owner() != this.chunk) return null;
        int[] ints = new int[11 + inputs.length];
        ints[0] = interpolators.size();
        ints[1] = this.chunk.cellCountXZ + 1;
        ints[2] = this.chunk.cellCountY + 1;
        ints[3] = this.chunk.cellNoiseMinY;
        ints[4] = this.chunkAccess.getPos().getMinBlockX();
        ints[5] = this.chunkAccess.getPos().getMinBlockZ();
        if (this.toggle != null) {
            ints[6] = 1;
            ints[7] = indexOf(interpolators, this.toggle);
            ints[8] = indexOf(interpolators, this.ridgedA);
            ints[9] = indexOf(interpolators, this.ridgedB);
        }
        ints[10] = inputs.length;
        for (int index = 0; index < inputs.length; index++) ints[11 + index] = indexOf(interpolators, inputs[index]);
        // Interpolator indices only; Y, X and Z coordinates may be negative.
        for (int index = 11; index < ints.length; index++) if (ints[index] < 0) return null;
        if (ints[6] != 0 && (ints[7] < 0 || ints[8] < 0 || ints[9] < 0)) return null;
        MemorySegment program = cells.program().memory();
        int status = (int)BIND.invokeExact(handle, router.handle(), program, program.byteSize(), MemorySegment.ofArray(ints), ints.length);
        return status == 0 ? cells : null;
    }

    private static int indexOf(List<NoiseChunk.NoiseInterpolator> interpolators, NoiseChunk.NoiseInterpolator interpolator) {
        for (int index = 0; index < interpolators.size(); index++) if (interpolators.get(index) == interpolator) return index;
        return -1;
    }

    /** Binds a native chunk's template instance: the template's cell program and
     * slice roots replace the chunk's wrapped cell cache and interpolators. */
    private void bindTemplate(long handle, NativeNoiseRouter router, NativeChunkNoise template) throws Throwable {
        NativeBeardifier beardifier = this.chunk.nativeBeardifier();
        int[] inputs = beardifier == null ? template.cellInputs : template.densityInputs;
        NativeDensityProgram cells = beardifier == null ? template.cellProgram : template.densityProgram;
        int[] ints = new int[11 + inputs.length];
        ints[0] = template.roots;
        ints[1] = this.chunk.cellCountXZ + 1;
        ints[2] = this.chunk.cellCountY + 1;
        ints[3] = this.chunk.cellNoiseMinY;
        ints[4] = this.chunkAccess.getPos().getMinBlockX();
        ints[5] = this.chunkAccess.getPos().getMinBlockZ();
        if (this.ore) {
            ints[6] = 1;
            ints[7] = template.ore[0];
            ints[8] = template.ore[1];
            ints[9] = template.ore[2];
        }
        ints[10] = inputs.length;
        System.arraycopy(inputs, 0, ints, 11, inputs.length);
        MemorySegment program = cells.memory();
        int status = (int)BIND.invokeExact(handle, router.handle(), program, program.byteSize(), MemorySegment.ofArray(ints), ints.length);
        Reference.reachabilityFence(template);
        if (status != 0) throw new IllegalStateException("Native chunk noise binding rejected: " + status);
        if (beardifier != null) {
            int[] geometry = beardifier.packedGeometry();
            status = (int)BEARDIFIER.invokeExact(handle, MemorySegment.ofArray(geometry), geometry.length, NativeBeardifier.kernel());
            if (status != 0) throw new IllegalStateException("Native chunk noise Beardifier rejected: " + status);
        }
    }

    /** doFill's cell loop run by Rust; Java prepares aquifer materials on request. */
    private void traverse(long handle, NativeNoiseRouter router, int cellCountY, double[] cellDensities) throws Throwable {
        int cells = 16 / this.chunk.cellWidth, cellSize = this.chunk.cellWidth * this.chunk.cellWidth * this.chunk.cellHeight;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment materials = arena.allocate(cellSize * 8L, 8), density = arena.allocate(cellSize * 8L, 8), request = arena.allocate(12, 4);
            MemorySegment gapMemory = this.gap == null ? MemorySegment.NULL : this.gap.state();
            this.chunk.beginNativeTraversal();
            this.slice(handle, false, 0);
            for (int cellX = 0; cellX < cells; cellX++) {
                this.slice(handle, true, cellX + 1);
                MemorySegment given = MemorySegment.NULL;
                for (;;) {
                    int status = (int)CELLS.invokeExact(handle, cellX, given, gapMemory, density, request);
                    if (status == 0) break;
                    if (status != 1) throw new IllegalStateException("Native noise fill cells failed: " + status);
                    // The cell cache holds this cell's densities, as after selectCellYZ.
                    MemorySegment.copy(density, ValueLayout.JAVA_DOUBLE, 0, cellDensities, 0, cellSize);
                    int[] prepared = this.aquifer.prepareCellMaterials(request.get(ValueLayout.JAVA_INT, 0), request.get(ValueLayout.JAVA_INT, 4),
                        request.get(ValueLayout.JAVA_INT, 8), cellDensities);
                    MemorySegment.copy(prepared, 0, materials, ValueLayout.JAVA_INT, 0, cellSize * 2);
                    given = materials;
                }
                this.chunk.skipNativeCells(cells * cellCountY);
            }
            this.chunk.stopInterpolation();
        } finally {
            Reference.reachabilityFence(router);
        }
    }

    /** One slice into Rust's low or high buffer; Java fills a slice the router declines. */
    private void slice(long handle, boolean high, int cellX) throws Throwable {
        int index = this.chunk.firstCellX() + cellX;
        int status = uploadSlices ? 1 : (int)SLICE.invokeExact(handle, high ? 1 : 0, index * this.chunk.cellWidth);
        if (status == 0) {
            NativeNoiseRouter.SLICES.incrementAndGet();
            return;
        }
        if (status != 1) throw new IllegalStateException("Native noise fill slice failed: " + status);
        // A template instance's grid covers every 16-block chunk slice.
        if (this.chunk.nativeNoise() != null) throw new IllegalStateException("Native chunk noise declined a slice");
        this.chunk.fillJavaSlice(!high, index);
        List<NoiseChunk.NoiseInterpolator> interpolators = this.chunk.interpolators;
        int columns = this.chunk.cellCountXZ + 1, points = this.chunk.cellCountY + 1;
        double[] values = new double[interpolators.size() * columns * points];
        for (int k = 0; k < interpolators.size(); k++) {
            double[][] slice = high ? interpolators.get(k).slice1 : interpolators.get(k).slice0;
            for (int column = 0; column < columns; column++) System.arraycopy(slice[column], 0, values, (k * columns + column) * points, points);
        }
        status = (int)UPLOAD.invokeExact(handle, high ? 1 : 0, MemorySegment.ofArray(values), values.length);
        if (status != 0) throw new IllegalStateException("Native noise fill upload failed: " + status);
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
