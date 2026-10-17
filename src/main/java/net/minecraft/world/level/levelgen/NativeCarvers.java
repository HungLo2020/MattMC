package net.minecraft.world.level.levelgen;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;
import java.util.function.Function;
import net.minecraft.world.level.biome.BiomeManager;
import java.util.concurrent.atomic.AtomicLong;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NativeBlockRegistry;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CanyonCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CaveWorldCarver;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.carver.NetherWorldCarver;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.util.valueproviders.TrapezoidFloat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/** Rust-owned CARVERS stage: applyCarvers' loop over the carvers of every chunk
 * within 8 (each carver's seeding, start check, configuration sampling and
 * carving) runs in one native call over the chunk's Rust-owned storage
 * ({@link NativeProtoChunk}), its carving mask and the chunk's aquifer, whose
 * substance decisions run in Rust. Java supplies each neighbour's carvers from
 * its carver biome, and answers only the top material below a carved grass or
 * mycelium block, through an upcall. The storage, mask and aquifer state
 * install once at the end. */
final class NativeCarvers {
    private static final MethodHandle RUN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_carvers_run",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MemorySegment TOP;
    private static final MemorySegment COLD;
    private static final MemorySegment SIN;
    // The stage running on this thread, for the top-material upcall.
    private static final ThreadLocal<Top> CURRENT = new ThreadLocal<>();
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaCarvers");
    // Chunks carved natively, so tests can prove the route engaged.
    static final AtomicLong CHUNKS = new AtomicLong();
    // Top-material upcalls answered, so tests can prove the callback ran.
    static final AtomicLong TOP_CALLS = new AtomicLong();
    // Temperature conditions of native top materials, which Java answers.
    static final AtomicLong COLD_CALLS = new AtomicLong();
    // Neighbour carver biomes Rust sampled.
    static final AtomicLong SAMPLED = new AtomicLong();

    static String statistics() {
        return "carvedChunks=" + CHUNKS.get() + " carverBiomesSampled=" + SAMPLED.get() + " carverTopCalls=" + TOP_CALLS.get()
            + " carverTopNative=" + NativeTopMaterial.nativeCalls() + " carverTopDeclined=" + NativeTopMaterial.DECLINED.get() + " carverTopCold=" + COLD_CALLS.get()
            + " carverTopVerified=" + NativeTopMaterial.verification()[0] + " carverTopMismatched=" + NativeTopMaterial.verification()[1];
    }

    static {
        try {
            MethodHandle top = MethodHandles.lookup().findStatic(NativeCarvers.class, "top", MethodType.methodType(int.class, int.class, int.class,
                int.class, int.class, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class));
            TOP = Linker.nativeLinker().upcallStub(top, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS), Arena.global());
            MethodHandle cold = MethodHandles.lookup().findStatic(NativeCarvers.class, "cold", MethodType.methodType(int.class, int.class, int.class,
                int.class, int.class));
            COLD = Linker.nativeLinker().upcallStub(cold, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), Arena.global());
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
        SIN = Arena.global().allocateFrom(ValueLayout.JAVA_FLOAT, Mth.sinTable());
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    static boolean enabled() {
        return enabled;
    }

    /**
     * Per generator and biome source: the distinct carver settings of its biomes, the
     * union of their carvers, each settings' carvers as indices into that union, and
     * (for a plain multi-noise source) each climate-tree node's leaf settings.
     */
    record Tables(BiomeSource source, List<BiomeGenerationSettings> settings, java.util.IdentityHashMap<BiomeGenerationSettings, Integer> index,
                  List<ConfiguredWorldCarver<?>> carvers, int[][] lists, @Nullable int[] leafSettings) {}

    private static final java.util.Map<ChunkGenerator, Tables> TABLES = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * One chunk's carver selection: each neighbour's cached carver settings in
     * {@code applyCarvers}' order, or -1 for Rust to sample as {@code carverBiome} would,
     * with what sampling needs; Rust's choices fill the neighbours' caches afterwards.
     */
    static final class Selection {
        final Tables tables;
        final int[] chosen;
        final boolean[] uncached;
        final ChunkAccess[] chunks;
        @Nullable
        final NativeBiomeSearch.Program program;
        @Nullable
        final Climate.ParameterList<Holder<Biome>> list;

        Selection(Tables tables, int[] chosen, boolean[] uncached, ChunkAccess[] chunks, @Nullable NativeBiomeSearch.Program program,
                  @Nullable Climate.ParameterList<Holder<Biome>> list) {
            this.tables = tables;
            this.chosen = chosen;
            this.uncached = uncached;
            this.chunks = chunks;
            this.program = program;
            this.list = list;
        }
    }

    @Nullable
    private static Tables tables(ChunkGenerator generator, BiomeSource source) {
        Tables cached = TABLES.get(generator);
        if (cached != null && cached.source() == source) return cached;
        List<BiomeGenerationSettings> settings = new ArrayList<>();
        var index = new java.util.IdentityHashMap<BiomeGenerationSettings, Integer>();
        java.util.function.Function<Holder<Biome>, Integer> settingsOf = biome -> index.computeIfAbsent(generator.getBiomeGenerationSettings(biome), s -> {
            settings.add(s);
            return settings.size() - 1;
        });
        for (Holder<Biome> biome : source.possibleBiomes()) settingsOf.apply(biome);
        int[] leafSettings = null;
        if (source.getClass() == MultiNoiseBiomeSource.class && ((MultiNoiseBiomeSource)source).nativeParameters().getClass() == Climate.ParameterList.class) {
            var list = ((MultiNoiseBiomeSource)source).nativeParameters();
            leafSettings = new int[list.nativeNodeCount()];
            for (int node = 0; node < leafSettings.length; node++) {
                Holder<Biome> value = list.nativeLeafValue(node);
                leafSettings[node] = value == null ? -1 : settingsOf.apply(value);
            }
        }
        List<ConfiguredWorldCarver<?>> carvers = new ArrayList<>();
        var carverIndex = new java.util.IdentityHashMap<ConfiguredWorldCarver<?>, Integer>();
        int[][] lists = new int[settings.size()][];
        for (int s = 0; s < settings.size(); s++) {
            List<Integer> list = new ArrayList<>();
            for (Holder<ConfiguredWorldCarver<?>> holder : settings.get(s).getCarvers()) {
                list.add(carverIndex.computeIfAbsent(holder.value(), c -> {
                    carvers.add(c);
                    return carvers.size() - 1;
                }));
            }
            lists[s] = list.stream().mapToInt(Integer::intValue).toArray();
        }
        var tables = new Tables(source, settings, index, carvers, lists, leafSettings);
        TABLES.put(generator, tables);
        return tables;
    }

    /**
     * The chunk's selection: neighbours' cached carver settings, the constant settings of a
     * fixed source, or -1 for Rust to sample with a plain multi-noise source's climate. Null
     * keeps Java's loop.
     */
    @Nullable
    static Selection select(ChunkGenerator generator, BiomeSource source, RandomState randomState, NoiseBasedChunkGenerator.CarverChunks chunks, ChunkPos pos) {
        Tables tables = tables(generator, source);
        if (tables == null) return null;
        NativeBiomeSearch.Program program = null;
        Climate.ParameterList<Holder<Biome>> list = null;
        int fixed = -1;
        if (tables.leafSettings() != null) {
            program = NativeBiomeSearch.climateProgram(randomState.sampler());
            if (program == null) return null;
            list = ((MultiNoiseBiomeSource)source).nativeParameters();
        } else if (source.getClass() == net.minecraft.world.level.biome.FixedBiomeSource.class) {
            Integer index = tables.index().get(generator.getBiomeGenerationSettings(source.possibleBiomes().iterator().next()));
            if (index == null) return null;
            fixed = index;
        } else {
            return null;
        }
        int[] chosen = new int[289];
        boolean[] uncached = new boolean[289];
        ChunkAccess[] neighbours = new ChunkAccess[289];
        for (int j = -8; j <= 8; j++) {
            for (int k = -8; k <= 8; k++) {
                int n = (j + 8) * 17 + k + 8;
                ChunkAccess neighbour = chunks.getChunk(pos.x + j, pos.z + k);
                neighbours[n] = neighbour;
                BiomeGenerationSettings cached = neighbour.cachedCarverBiome();
                if (cached == null) {
                    uncached[n] = true;
                    chosen[n] = fixed;
                } else {
                    Integer index = tables.index().get(cached);
                    if (index == null) return null;
                    chosen[n] = index;
                }
            }
        }
        return new Selection(tables, chosen, uncached, neighbours, program, list);
    }

    /** What the top-material upcall needs, and the first failure it saw. */
    private static final class Top {
        final CarvingContext context;
        final Function<BlockPos, Holder<Biome>> biomes;
        final ChunkAccess chunk;
        /** The climate tree whose previous leaf Rust carries across this callback, or null. */
        @Nullable
        Climate.ParameterList<Holder<Biome>> list;
        final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        @Nullable
        Throwable failure;
        /** The chunk's native top material binding, for the temperature upcall. */
        @Nullable
        NativeTopMaterial nativeTop;

        Top(CarvingContext context, Function<BlockPos, Holder<Biome>> biomes, ChunkAccess chunk) {
            this.context = context;
            this.biomes = biomes;
            this.chunk = chunk;
        }
    }

    /** {@code carvingContext.topMaterial(...)} for Rust: the Java chunk's
     * world-generation heightmaps take the storage's current values first (the
     * rule may test steep). Returns a state id, -1 for none, -2 on failure. */
    private static int top(int x, int y, int z, int fluid, MemorySegment surface, MemorySegment floor, int words, MemorySegment previous) {
        Top top = CURRENT.get();
        if (top == null) return -2;
        TOP_CALLS.incrementAndGet();
        var leaf = previous.reinterpret(4);
        if (top.list != null) top.list.setPreviousNativeLeaf(leaf.get(ValueLayout.JAVA_INT, 0));
        try {
            long[] a = surface.reinterpret(words * 8L).toArray(ValueLayout.JAVA_LONG);
            long[] b = floor.reinterpret(words * 8L).toArray(ValueLayout.JAVA_LONG);
            top.chunk.setHeightmap(Heightmap.Types.WORLD_SURFACE_WG, a);
            top.chunk.setHeightmap(Heightmap.Types.OCEAN_FLOOR_WG, b);
            var state = top.context.topMaterial(top.biomes, top.chunk, top.position.set(x, y, z), fluid != 0);
            return state.isPresent() ? Block.getId(state.get()) : -1;
        } catch (Throwable error) {
            if (top.failure == null) top.failure = error;
            return -2;
        } finally {
            if (top.list != null) leaf.set(ValueLayout.JAVA_INT, 0, top.list.previousNativeLeaf());
        }
    }

    /** {@code Biome.coldEnoughToSnow} for a native top material's temperature
     * condition, where Java's evaluation would make the same call: the biome's
     * per-thread temperature cache sees the same sequence. 1 cold, 0 warm, -2 on failure. */
    private static int cold(int biome, int x, int y, int z) {
        Top top = CURRENT.get();
        if (top == null || top.nativeTop == null) return -2;
        COLD_CALLS.incrementAndGet();
        try {
            Biome value = top.nativeTop.registry.byId(biome);
            if (value == null) return -2;
            return value.coldEnoughToSnow(top.position.set(x, y, z), top.nativeTop.seaLevel) ? 1 : 0;
        } catch (Throwable error) {
            if (top.failure == null) top.failure = error;
            return -2;
        }
    }

    /** Whether the stage may record this chunk's carving at all: a plain ProtoChunk
     * at the CARVERS stage with an unblended noise chunk and its own carving mask,
     * no carver debugging, and a top-material rule that reads only heightmaps. */
    static boolean eligible(ChunkAccess chunk, NoiseChunk noise, CarvingContext context) {
        // Rust reads each state's block from its block registry.
        return enabled && NativeBlockRegistry.ready() && chunk.getClass() == ProtoChunk.class && !SharedConstants.DEBUG_CARVERS && !SharedConstants.debugVoidTerrain(chunk.getPos())
            && noise.getBlender() == Blender.empty() && ((ProtoChunk)chunk).getOrCreateCarvingMask().hasOnlyOwnBits()
            && context.randomState().surfaceSystem().getClass() == SurfaceSystem.class && NativeSurface.readsOnlyHeightmaps(context.surfaceRule());
    }

    private record Replaceable(HolderSet<Block> set, long generation, int[] ids) {}

    // Replaceable block ids per configuration, while its set and the tags hold.
    private static final java.util.Map<CarverConfiguration, Replaceable> REPLACEABLE =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** The ids of blocks canReplaceBlock accepts: its own predicate,
     * {@code blockState.is(replaceable)}, per block. Named and direct sets are
     * cached until any holder's tags are rebound; other sets are recomputed. */
    private static int[] replaceable(CarverConfiguration config) {
        HolderSet<Block> set = config.replaceable;
        long generation = Holder.TAG_GENERATION.get();
        boolean cacheable = set.getClass() == HolderSet.Named.class || set.getClass() == HolderSet.Direct.class;
        Replaceable cached = cacheable ? REPLACEABLE.get(config) : null;
        if (cached != null && cached.set() == set && cached.generation() == generation) return cached.ids();
        IntArrayList ids = new IntArrayList();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (set.contains(block.builtInRegistryHolder())) ids.add(BuiltInRegistries.BLOCK.getId(block));
        }
        int[] result = ids.toIntArray();
        // A rebind during the scan leaves the old generation; the next call recomputes.
        if (cacheable) REPLACEABLE.put(config, new Replaceable(set, generation, result));
        return result;
    }

    /** A float provider as [kind (0 constant, 1 uniform, 2 trapezoid), a, b, c]; null if not modelled. */
    @Nullable
    private static double[] provider(FloatProvider provider) {
        if (provider.getClass() == ConstantFloat.class) return new double[]{0, ((ConstantFloat)provider).getValue(), 0, 0};
        if (provider.getClass() == UniformFloat.class) return new double[]{1, provider.getMinValue(), provider.getMaxValue(), 0};
        if (provider.getClass() == TrapezoidFloat.class) {
            var trapezoid = (TrapezoidFloat)provider;
            return new double[]{2, trapezoid.getMinValue(), trapezoid.getMaxValue(), trapezoid.plateau()};
        }
        return null;
    }

    /** Whether a configured carver runs natively: a built-in carver class with its
     * own configuration class and modelled providers. */
    static boolean supports(ConfiguredWorldCarver<?> carver) {
        Class<?> type = carver.worldCarver().getClass();
        CarverConfiguration config = carver.config();
        if (config.debugSettings.isDebugMode() || config.y.getClass() != UniformHeight.class || provider(config.yScale) == null) return false;
        if (type == CaveWorldCarver.class || type == NetherWorldCarver.class) {
            return config.getClass() == CaveCarverConfiguration.class && provider(((CaveCarverConfiguration)config).horizontalRadiusMultiplier) != null
                && provider(((CaveCarverConfiguration)config).verticalRadiusMultiplier) != null && provider(((CaveCarverConfiguration)config).floorLevel()) != null;
        }
        if (type == CanyonWorldCarver.class && config.getClass() == CanyonCarverConfiguration.class) {
            var canyon = (CanyonCarverConfiguration)config;
            var shape = canyon.shape;
            return shape.getClass() == CanyonCarverConfiguration.CanyonShapeConfiguration.class && provider(canyon.verticalRotation) != null
                && provider(shape.thickness) != null && provider(shape.distanceFactor) != null && provider(shape.horizontalRadiusFactor) != null;
        }
        return false;
    }

    /** Carves the chunk natively: {@code neighbours} are the configured carvers of
     * every chunk within 8, in applyCarvers' order (x, z, carvers). False, with
     * the chunk untouched, leaves the carving to Java. */
    static boolean run(CarvingContext context, ChunkAccess chunk, NoiseChunk noise, Aquifer aquifer, long seed, Selection selection,
                       BiomeManager biomeManager) {
        Function<BlockPos, Holder<Biome>> biomes = biomeManager::getBiome;
        List<ConfiguredWorldCarver<?>> carvers = selection.tables.carvers();
        for (ConfiguredWorldCarver<?> carver : carvers) {
            if (!supports(carver)) return false;
        }
        // Each settings' carvers, then each neighbour's settings (-1: Rust samples it) and the previous leaf.
        IntArrayList neighbourInts = new IntArrayList();
        int[][] lists = selection.tables.lists();
        neighbourInts.add(lists.length);
        for (int[] list : lists) {
            neighbourInts.add(list.length);
            neighbourInts.addElements(neighbourInts.size(), list);
        }
        neighbourInts.add(selection.chosen.length);
        int chosenAt = neighbourInts.size();
        neighbourInts.addElements(neighbourInts.size(), selection.chosen);
        neighbourInts.add(selection.list != null ? selection.list.previousNativeLeaf() : -1);
        int configs = carvers.size();
        if (configs > 30) return false;
        boolean needsAquifer = false;
        IntArrayList ints = new IntArrayList();
        double[] configDoubles = new double[configs * 23];
        IntArrayList replaceable = new IntArrayList();
        int[] configInts = new int[configs * 6];
        for (int index = 0; index < configs; index++) {
            ConfiguredWorldCarver<?> carver = carvers.get(index);
            CarverConfiguration config = carver.config();
            Class<?> type = carver.worldCarver().getClass();
            int kind = type == NetherWorldCarver.class ? 1 : type == CanyonWorldCarver.class ? 2 : 0;
            needsAquifer |= kind != 1;
            var height = (UniformHeight)config.y;
            int min = height.minInclusive().resolveY(context), max = height.maxInclusive().resolveY(context);
            // An empty range logs a warning in Java; a range too wide for nextInt throws there.
            if (min > max || max - min + 1 <= 0) return false;
            double[] scale = provider(config.yScale);
            configDoubles[index * 23] = config.probability;
            System.arraycopy(scale, 0, configDoubles, index * 23 + 1, 4);
            FloatProvider[] shapeProviders;
            int widthSmoothness = 0;
            if (kind == 2) {
                var canyon = (CanyonCarverConfiguration)config;
                shapeProviders = new FloatProvider[]{canyon.verticalRotation, canyon.shape.thickness, canyon.shape.distanceFactor, canyon.shape.horizontalRadiusFactor};
                widthSmoothness = canyon.shape.widthSmoothness;
                if (widthSmoothness <= 0) return false;
                configDoubles[index * 23 + 21] = canyon.shape.verticalRadiusDefaultFactor;
                configDoubles[index * 23 + 22] = canyon.shape.verticalRadiusCenterFactor;
            } else {
                var cave = (CaveCarverConfiguration)config;
                shapeProviders = new FloatProvider[]{cave.horizontalRadiusMultiplier, cave.verticalRadiusMultiplier, cave.floorLevel(), ConstantFloat.ZERO};
            }
            for (int n = 0; n < 4; n++) System.arraycopy(provider(shapeProviders[n]), 0, configDoubles, index * 23 + 5 + n * 4, 4);
            int[] ids = replaceable(config);
            replaceable.addElements(replaceable.size(), ids);
            System.arraycopy(new int[]{kind, config.lavaLevel.resolveY(context), min, max, widthSmoothness, ids.length}, 0, configInts, index * 6, 6);
        }
        NativeAquifer.NativeBinding binding = null;
        int[] disabledPolicy = null;
        if (needsAquifer) {
            if (aquifer instanceof Aquifer.Disabled disabled) {
                // Disabled aquifers answer their picker's status and never schedule.
                if (disabled.fluidPicker().getClass() != AquiferFluidPicker.class) return false;
                var picker = (AquiferFluidPicker)disabled.fluidPicker();
                disabledPolicy = new int[]{picker.lava.fluidLevel(), Block.getId(picker.lava.fluidType()), picker.fluid.fluidLevel(),
                    Block.getId(picker.fluid.fluidType()), picker.fluid.fluidType().isAir() ? 1 : 0, SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? 1 : 0,
                    picker.disabled.fluidLevel(), Block.getId(Blocks.AIR.defaultBlockState())};
                for (int id : disabledPolicy) if (id == -1) return false;
            } else if (aquifer instanceof Aquifer.NoiseBasedAquifer noiseAquifer) {
                binding = noiseAquifer.nativeAquifer().nativeBinding();
                if (binding == null) return false;
            } else {
                return false;
            }
        }
        CarvingMask mask = ((ProtoChunk)chunk).getOrCreateCarvingMask();
        NativeProtoChunk storage = NativeProtoChunk.create(chunk);
        if (storage == null) return false;
        try {
            int lava = Block.getId(Fluids.LAVA.defaultFluidState().createLegacyBlock());
            ints.addElements(0, new int[]{chunk.getPos().x, chunk.getPos().z, context.getMinGenY(), context.getGenDepth(), chunk.isUpgrading() ? 1 : 0,
                Block.getId(Blocks.AIR.defaultBlockState()), Block.getId(Blocks.CAVE_AIR.defaultBlockState()), lava,
                Block.getId(Blocks.WATER.defaultBlockState()), configs, 0, aquifer.shouldScheduleFluidUpdate() ? 1 : 0,
                binding != null ? 1 : disabledPolicy != null ? 2 : 0});
            if (binding != null) {
                ints.add(binding.skipY());
                ints.add(binding.locationKind());
                ints.addElements(ints.size(), binding.shape());
                ints.addElements(ints.size(), binding.surfaceRect());
                ints.addElements(ints.size(), binding.grid());
                ints.add(DimensionType.WAY_BELOW_MIN_Y);
                ints.addElements(ints.size(), binding.policy());
            } else {
                ints.addElements(ints.size(), new int[15]);
                ints.addElements(ints.size(), disabledPolicy != null ? disabledPolicy : new int[8]);
            }
            ints.add(BuiltInRegistries.BLOCK.getId(Blocks.GRASS_BLOCK));
            ints.add(BuiltInRegistries.BLOCK.getId(Blocks.MYCELIUM));
            ints.add(BuiltInRegistries.BLOCK.getId(Blocks.DIRT));
            ints.addElements(ints.size(), configInts);
            int selectionAt = ints.size();
            ints.addElements(ints.size(), neighbourInts.toIntArray());
            ints.addElements(ints.size(), replaceable.toIntArray());
            double[] doubles = configDoubles;
            long[] seeds = {seed};
            long[] maskWords = java.util.Arrays.copyOf(mask.toArray(), chunk.getHeight() * 4);
            int status;
            Top top = new Top(context, biomes, chunk);
            top.list = selection.list;
            Holder<Biome> fixed = selection.list == null && selection.tables.source() instanceof net.minecraft.world.level.biome.FixedBiomeSource source
                ? source.possibleBiomes().iterator().next() : null;
            NativeTopMaterial nativeTop = NativeTopMaterial.create(context, chunk, noise, biomeManager, biomes, selection.list, fixed);
            top.nativeTop = nativeTop;
            try (Arena arena = Arena.ofConfined()) {
                int[] intArray = ints.toIntArray();
                MemorySegment intMemory = arena.allocateFrom(ValueLayout.JAVA_INT, intArray);
                MemorySegment maskMemory = arena.allocateFrom(ValueLayout.JAVA_LONG, maskWords);
                long[] pointers = new long[27];
                MemorySegment leafBiomes = null;
                if (nativeTop != null) {
                    pointers[22] = nativeTop.handle;
                    pointers[23] = nativeTop.zoomSeed;
                    if (nativeTop.leafBiomes != null) {
                        leafBiomes = arena.allocateFrom(ValueLayout.JAVA_INT, nativeTop.leafBiomes);
                        pointers[24] = leafBiomes.address();
                    }
                    pointers[25] = nativeTop.fixedBiome;
                    pointers[26] = COLD.address();
                }
                MemorySegment leafSettings = null;
                if (selection.program != null) {
                    leafSettings = arena.allocateFrom(ValueLayout.JAVA_INT, selection.tables.leafSettings());
                    pointers[18] = selection.program.handle;
                    pointers[19] = selection.list.nativeNodes().address();
                    pointers[20] = selection.list.nativeNodeCount();
                    pointers[21] = leafSettings.address();
                }
                pointers[0] = maskMemory.address();
                pointers[1] = maskWords.length;
                pointers[2] = SIN.address();
                MemorySegment grid = null, cache = null, surface = null, memo = null, present = null;
                if (binding != null) {
                    grid = arena.allocateFrom(ValueLayout.JAVA_LONG, binding.locations());
                    cache = arena.allocateFrom(ValueLayout.JAVA_INT, binding.cache());
                    surface = arena.allocateFrom(ValueLayout.JAVA_INT, binding.surface());
                    memo = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, binding.memo());
                    present = arena.allocateFrom(ValueLayout.JAVA_BYTE, binding.present());
                    pointers[3] = grid.address();
                    pointers[4] = binding.locations().length;
                    pointers[5] = cache.address();
                    pointers[6] = surface.address();
                    pointers[7] = binding.surface().length;
                    pointers[8] = binding.sources().handle();
                    pointers[9] = binding.levels().handle();
                    pointers[10] = memo.address();
                    pointers[11] = present.address();
                    pointers[12] = binding.memo().length;
                    pointers[13] = binding.barrierState().address();
                    pointers[14] = binding.factory().handle();
                    pointers[15] = 0;
                    pointers[16] = Double.doubleToRawLongBits(binding.barrierXz());
                    pointers[17] = Double.doubleToRawLongBits(binding.barrierY());
                }
                CURRENT.set(top);
                try {
                    status = (int)RUN.invokeExact(storage.handle, arena.allocateFrom(ValueLayout.JAVA_LONG, pointers), intMemory, intArray.length,
                        arena.allocateFrom(ValueLayout.JAVA_DOUBLE, doubles), doubles.length, arena.allocateFrom(ValueLayout.JAVA_LONG, seeds),
                        seeds.length, TOP);
                } finally {
                    CURRENT.remove();
                    Reference.reachabilityFence(binding);
                    Reference.reachabilityFence(selection.program);
                    if (nativeTop != null) Reference.reachabilityFence(nativeTop.keep());
                }
                if (status == 0) {
                    // Rust's carver biome choices fill the neighbours' caches; its previous leaf is the thread's.
                    int at = selectionAt + chosenAt;
                    for (int n = 0; n < selection.chosen.length; n++) {
                        if (selection.uncached[n]) {
                            if (selection.chosen[n] < 0) SAMPLED.incrementAndGet();
                            var chosen = selection.tables.settings().get(intMemory.getAtIndex(ValueLayout.JAVA_INT, at + n));
                            selection.chunks[n].carverBiome(() -> chosen);
                        }
                    }
                    if (selection.list != null) {
                        selection.list.setPreviousNativeLeaf(intMemory.getAtIndex(ValueLayout.JAVA_INT, at + selection.chosen.length));
                    }
                    mask.installGenerated(maskMemory.toArray(ValueLayout.JAVA_LONG));
                    if (binding != null) {
                        // The aquifer's caches are pure memos; keep what Rust filled.
                        MemorySegment.copy(grid, ValueLayout.JAVA_LONG, 0, binding.locations(), 0, binding.locations().length);
                        MemorySegment.copy(cache, ValueLayout.JAVA_INT, 0, binding.cache(), 0, binding.cache().length);
                        MemorySegment.copy(surface, ValueLayout.JAVA_INT, 0, binding.surface(), 0, binding.surface().length);
                        MemorySegment.copy(memo, ValueLayout.JAVA_DOUBLE, 0, binding.memo(), 0, binding.memo().length);
                        MemorySegment.copy(present, ValueLayout.JAVA_BYTE, 0, binding.present(), 0, binding.present().length);
                        ((Aquifer.NoiseBasedAquifer)aquifer).setScheduleFluidUpdate(intMemory.getAtIndex(ValueLayout.JAVA_INT, 11) != 0);
                    }
                }
            } catch (RuntimeException | Error error) {
                throw error;
            } catch (Throwable error) {
                throw new IllegalStateException("Native carving failed", error);
            } finally {
                // The binding is shared and freed with its NativeTopMaterial; keep it reachable through the stage.
                Reference.reachabilityFence(nativeTop);
            }
            // Input the stage does not model: Java carves the chunk.
            if (status == -1) return false;
            if (status == -4) {
                if (top.failure instanceof RuntimeException runtime) throw runtime;
                if (top.failure instanceof Error error) throw error;
                throw new IllegalStateException("Native carving top material failed", top.failure);
            }
            if (status == -5) throw new IllegalStateException("Native carver biome selection failed");
            if (status != 0) throw new IllegalStateException("Native carving rejected: " + status);
            storage.install();
            CHUNKS.incrementAndGet();
            return true;
        } finally {
            storage.close();
        }
    }
}
