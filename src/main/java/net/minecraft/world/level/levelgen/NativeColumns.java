package net.minecraft.world.level.levelgen;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.SharedConstants;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;

/** Rust-owned {@code NoiseBasedChunkGenerator.getBaseHeight}
 * ({@code noise_fill/column.rs}): the column of the chunk's native noise fill
 * with the structure Beardifier marker's zero, top down until a state passes
 * the heightmap's predicate. A {@link RandomState} exports its chunk noise
 * template, cell program and aquifer inputs once per height range; Rust keeps
 * each chunk's router and aquifer for the queries around it. Other
 * generators, settings and predicates keep Java's one-cell noise chunk. */
final class NativeColumns {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_columns_create_owned",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_columns_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle HEIGHT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_columns_height",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    static final int UNHANDLED = Integer.MIN_VALUE + 1;
    private static final int NO_MATCH = Integer.MIN_VALUE;
    private static final Cleaner CLEANER = Cleaner.create();
    private static final Object UNSUPPORTED = new Object();
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaBaseHeight");
    // Heights Rust answered, so tests and drivers can prove the route engaged.
    static final AtomicLong HEIGHTS = new AtomicLong();

    private final long handle;
    private final NativeChunkNoise noise;
    // The programs and noise states the handle points at.
    private final Object[] keep;
    private final int defaultBlock;
    private final int[] states;
    private final Map<Heightmap.Types, int[]> matches = new ConcurrentHashMap<>();

    private NativeColumns(long handle, NativeChunkNoise noise, Object[] keep, int defaultBlock, int[] states) {
        this.handle = handle;
        this.noise = noise;
        this.keep = keep;
        this.defaultBlock = defaultBlock;
        this.states = states;
        CLEANER.register(this, () -> {
            try {
                RELEASE.invokeExact(handle);
            } catch (Throwable error) {
                throw new IllegalStateException("Cannot release native columns", error);
            }
        });
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    private record Key(NoiseGeneratorSettings settings, int minY, int height) {}

    // Per RandomState and height range; weak keys do not retain old RandomStates.
    private static final Map<RandomState, Map<Key, Object>> SOURCES = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** The column source for a generator's settings over {@code height}, or null for Java's columns. */
    @Nullable
    static NativeColumns source(NoiseBasedChunkGenerator generator, LevelHeightAccessor height, RandomState random) {
        if (!enabled) return null;
        NoiseGeneratorSettings settings = generator.nativeSettings();
        NoiseSettings noise = settings.noiseSettings().clampToHeightAccessor(height);
        if (Math.floorDiv(noise.height(), noise.getCellHeight()) <= 0) return null;
        var key = new Key(settings, noise.minY(), noise.height());
        Map<Key, Object> sources = SOURCES.computeIfAbsent(random, r -> new ConcurrentHashMap<>());
        Object source = sources.computeIfAbsent(key, k -> {
            NativeColumns created = create(settings, noise, generator.nativeFluidPicker(), random);
            return created == null ? UNSUPPORTED : created;
        });
        return source instanceof NativeColumns columns ? columns : null;
    }

    long handle() {
        return this.handle;
    }

    /** The cell program the handle's columns evaluate (its memory may be rebuilt). */
    MemorySegment program() {
        return this.noise.cellProgram.memory();
    }

    /** The states passing {@code types}' predicate, or null when Java must evaluate the column. */
    @Nullable
    int[] matches(Heightmap.Types types) {
        int[] states = this.matches.computeIfAbsent(types, this::matching);
        return states == NO_STATES ? null : states;
    }

    /** {@code getBaseHeight(x, z, types, height, random)}, or {@link #UNHANDLED} for Java's column. */
    static int baseHeight(NoiseBasedChunkGenerator generator, int x, int z, Heightmap.Types types, LevelHeightAccessor height, RandomState random) {
        NativeColumns columns = source(generator, height, random);
        if (columns == null) return UNHANDLED;
        int[] states = columns.matches(types);
        if (states == null) return UNHANDLED;
        int result;
        try (Arena query=Arena.ofConfined()) {
            var accepted=query.allocate(Math.max(4L,states.length*4L),4);
            MemorySegment.copy(states,0,accepted,ValueLayout.JAVA_INT,0,states.length);
            result = (int)HEIGHT.invokeExact(columns.handle, columns.noise.cellProgram.memory(), x, z, accepted, states.length);
        } catch (Throwable error) {
            throw new IllegalStateException("Native base height failed", error);
        } finally {
            Reference.reachabilityFence(columns);
        }
        if (result == UNHANDLED) return UNHANDLED;
        HEIGHTS.incrementAndGet();
        return result == NO_MATCH ? height.getMinY() : result;
    }

    private static final int[] NO_STATES = new int[0];

    private int[] matching(Heightmap.Types types) {
        int[] states = this.passing(types.isOpaque());
        return states == null ? NO_STATES : states;
    }

    /** The candidate states a column can hold passing {@code predicate}, or null when ore veins and the default block disagree. */
    @Nullable
    int[] passing(java.util.function.Predicate<BlockState> predicate) {
        boolean solid = predicate.test(Block.stateById(this.defaultBlock));
        List<Integer> passing = new ArrayList<>();
        for (int i = 0; i < this.states.length; i++) {
            int id = this.states[i];
            boolean passes = predicate.test(Block.stateById(id));
            // Ore vein states replace the default block: they must agree with it.
            if (i >= this.states.length - VEIN_STATES && passes != solid) return null;
            if (passes) passing.add(id);
        }
        return passing.stream().mapToInt(Integer::intValue).distinct().toArray();
    }

    private static final int VEIN_STATES = 6;

    @Nullable
    private static NativeColumns create(NoiseGeneratorSettings settings, NoiseSettings noise, Aquifer.FluidPicker picker, RandomState random) {
        if (random.nativeSurfaceLevel() == null) return null;
        NativeChunkNoise chunkNoise = random.nativeChunkNoise(settings);
        if (chunkNoise == null || !(picker instanceof AquiferFluidPicker global)) return null;
        if (global.lava.fluidType() != Blocks.LAVA.defaultBlockState()) return null;
        BlockState fluid = global.fluid.fluidType();
        if (fluid != Blocks.WATER.defaultBlockState() && fluid != Blocks.LAVA.defaultBlockState() && fluid != Blocks.AIR.defaultBlockState()) return null;
        int[] policy = {global.lava.fluidLevel(), id(global.lava.fluidType()), global.fluid.fluidLevel(), id(fluid), fluid.isAir() ? 1 : 0,
            SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? 1 : 0, global.disabled.fluidLevel(), id(Blocks.AIR.defaultBlockState())};
        long[] longs = new long[7];
        double[] doubles = new double[2];
        int aquifer = 0, randomKind = 0;
        NativePositionalFactory factoryOwner=null;
        List<Object> keep = new ArrayList<>(List.of(chunkNoise));
        if (settings.isAquifersEnabled()) {
            NativeFluidSources sources = random.nativeFluidSources();
            NativeSurfaceLevel levels = random.nativeSurfaceLevel();
            var barrier = chunkNoise.barrier;
            var factory = random.aquiferRandom();
            if (sources == null || levels == null || barrier == null || !random.aquiferBatchSafe()) return null;
            if (factory.getClass() == XoroshiroRandomSource.XoroshiroPositionalRandomFactory.class) {
                var xoroshiro = (XoroshiroRandomSource.XoroshiroPositionalRandomFactory)factory;
                randomKind = 1;
                factoryOwner=xoroshiro.nativeFactory();
            } else if (factory.getClass() == LegacyRandomSource.LegacyPositionalRandomFactory.class) {
                randomKind = 2;
                factoryOwner=((LegacyRandomSource.LegacyPositionalRandomFactory)factory).nativeFactory();
            } else {
                return null;
            }
            longs[2]=factoryOwner.handle();
            var barrierNoise = barrier.noise().noise();
            var state = barrierNoise == null ? null : barrierNoise.nativeState();
            if (barrierNoise != null && (state == null || !state.state().isNative())) return null;
            aquifer = 1;
            longs[4] = sources.handle();
            longs[5] = levels.handle();
            longs[6] = state == null ? 0 : state.state().address();
            doubles[0] = barrier.xzScale();
            doubles[1] = barrier.yScale();
            keep.addAll(List.of(sources, levels));
            if (state != null) keep.add(state);
        }
        longs[0] = chunkNoise.templateHandle();
        longs[1] = chunkNoise.flatsHandle();
        int defaultBlock = id(settings.defaultBlock());
        int[] ints = new int[20];
        System.arraycopy(new int[]{chunkNoise.roots, chunkNoise.cellInputs.length, noise.getCellWidth(), noise.getCellHeight(), noise.minY(), noise.height(),
            defaultBlock, aquifer, randomKind}, 0, ints, 0, 9);
        System.arraycopy(policy, 0, ints, 9, 8);
        ints[17] = id(Blocks.WATER.defaultBlockState());
        ints[18] = id(Blocks.LAVA.defaultBlockState());
        ints[19] = DimensionType.WAY_BELOW_MIN_Y;
        long handle;
        try (Arena admission=Arena.ofConfined()) {
            handle = (long)CREATE.invokeExact(admission.allocateFrom(ValueLayout.JAVA_INT,ints),
                admission.allocateFrom(ValueLayout.JAVA_LONG,longs),admission.allocateFrom(ValueLayout.JAVA_DOUBLE,doubles));
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot create native columns", error);
        } finally { Reference.reachabilityFence(factoryOwner); }
        if (handle == 0) return null;
        // Candidates a column can hold: the default block, air, the aquifer's fluids, then the ore vein states.
        List<Integer> candidates = new ArrayList<>(List.of(defaultBlock, id(Blocks.AIR.defaultBlockState()), id(Blocks.WATER.defaultBlockState()),
            id(Blocks.LAVA.defaultBlockState()), policy[1], policy[3]));
        for (BlockState vein : List.of(Blocks.COPPER_ORE.defaultBlockState(), Blocks.RAW_COPPER_BLOCK.defaultBlockState(), Blocks.GRANITE.defaultBlockState(),
            Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), Blocks.RAW_IRON_BLOCK.defaultBlockState(), Blocks.TUFF.defaultBlockState())) {
            candidates.add(id(vein));
        }
        return new NativeColumns(handle, chunkNoise, keep.toArray(), defaultBlock, candidates.stream().mapToInt(Integer::intValue).toArray());
    }

    private static int id(BlockState state) {
        return Block.BLOCK_STATE_REGISTRY.getId(state);
    }
}
