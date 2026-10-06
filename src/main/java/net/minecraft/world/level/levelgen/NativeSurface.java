package net.minecraft.world.level.levelgen;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.placement.CaveSurface;

/** Forward-only native surface program. Java retains registry/extension bindings
 * and ordered chunk writes. Native requests are serviced after preceding writes
 * have been committed, including requests that observe evolving heightmaps. */
final class NativeSurface implements SurfaceRules.SurfaceRule {
    private static final int LIMIT = 4096;
    private static final MethodHandle VALIDATE = bind("validate", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle VALIDATE_LARGE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_validate",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle STEP = bind("step", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle BIOMES = bind("biomes", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static MethodHandle bind(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_" + name, descriptor, Linker.Option.critical(true));
    }
    private final SurfaceRules.Context context;
    private final Registry<Biome> registry;
    private final BiomeManager biomeManager;
    private final int[] program, cache;
    private final SurfaceRules.Condition[] conditions;
    private final SurfaceRules.SurfaceRule[] rules;
    // Each condition slot's source, for slots Rust can answer itself.
    private final SurfaceRules.ConditionSource[] conditionSources;
    private final int[] frame = new int[24];
    private int[] flags = new int[1], output = new int[1], biomes = new int[1], biomeCache = new int[1];
    private final boolean usesBiomes;
    final boolean canBatch;
    private double secondary;
    private long updateBase;
    private int syncedY;

    NativeSurface(SurfaceRules.RuleSource source, SurfaceRules.Context context, Registry<Biome> registry, BiomeManager biomeManager) {
        this.context = context; this.registry = registry;
        this.biomeManager = biomeManager != null && biomeManager.getClass() == BiomeManager.class ? biomeManager : null;
        // A batched program depends only on the rule, the system's bands, the
        // biome registry, native biomes and the generation height range: it is
        // compiled and validated once per surface system, and each context only
        // applies its condition sources.
        Key key = new Key(source, registry, this.biomeManager != null, context.context.getMinGenY(), context.context.getGenDepth());
        Compiled compiled = context.system.getClass() == SurfaceSystem.class && cacheCompiled
            ? COMPILED.computeIfAbsent(context.system, system -> new ConcurrentHashMap<>()).get(key) : null;
        if (compiled == null) {
            Compiler compiler = new Compiler(); compiler.rule(source, 0); compiler.emit(0, 0, 0, 0, 0);
            int end = compiler.words.size(); compiler.words.set(0, end);
            for (var data : compiler.biomeData) {
                compiler.words.set(data[0] + 1, compiler.words.size());
                for (int i = 1; i < data.length; i++) compiler.words.add(data[i]);
            }
            program = compiler.words.stream().mapToInt(Integer::intValue).toArray();
            conditions = compiler.conditions.toArray(SurfaceRules.Condition[]::new);
            rules = compiler.rules.toArray(SurfaceRules.SurfaceRule[]::new);
            conditionSources = compiler.sources.toArray(SurfaceRules.ConditionSource[]::new);
            usesBiomes = compiler.usesBiomes;
            validate(program);
            if (compiler.canBatch && context.system.getClass() == SurfaceSystem.class && cacheCompiled) {
                COMPILED.computeIfAbsent(context.system, system -> new ConcurrentHashMap<>()).put(key, new Compiled(program, conditionSources, usesBiomes));
            }
            canBatch = compiler.canBatch && this.biomeManager != null
                && context.system.getClass() == SurfaceSystem.class
                && context.chunk.getClass() == net.minecraft.world.level.chunk.ProtoChunk.class;
        } else {
            program = compiled.program();
            conditionSources = compiled.sources();
            conditions = new SurfaceRules.Condition[conditionSources.length];
            for (int slot = 0; slot < conditions.length; slot++) conditions[slot] = conditionSources[slot].apply(context);
            rules = new SurfaceRules.SurfaceRule[0];
            usesBiomes = compiled.usesBiomes();
            canBatch = this.biomeManager != null && context.chunk.getClass() == net.minecraft.world.level.chunk.ProtoChunk.class;
        }
        cache = new int[program[0] / 8];
    }

    /** The rule and registry by identity (hashing a rule tree would walk it), with the inputs compiling reads. */
    private record Key(SurfaceRules.RuleSource source, Registry<Biome> registry, boolean biomes, int minY, int height) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && key.source == this.source && key.registry == this.registry && key.biomes == this.biomes
                && key.minY == this.minY && key.height == this.height;
        }

        @Override
        public int hashCode() {
            return ((System.identityHashCode(this.source) * 31 + System.identityHashCode(this.registry)) * 31 + (this.biomes ? 1 : 0)) * 31
                + this.minY * 7919 + this.height;
        }
    }

    private record Compiled(int[] program, SurfaceRules.ConditionSource[] sources, boolean usesBiomes) {}

    // Comparison runs compile every context's program, as before the cache.
    static volatile boolean cacheCompiled = !Boolean.getBoolean("mattmc.worldgen.surfaceCompileEachChunk");
    // Batched programs per surface system; weak keys do not retain old RandomStates.
    private static final java.util.Map<SurfaceSystem, ConcurrentHashMap<Key, Compiled>> COMPILED =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static void validate(int[] program) {
        try {
            int status;
            if (program.length <= 131072) {
                status = (int)VALIDATE.invokeExact(MemorySegment.ofArray(program), program.length);
            } else {
                // Validation is linear; large custom data must not pin the heap.
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment copy = arena.allocateFrom(ValueLayout.JAVA_INT, program);
                    status = (int)VALIDATE_LARGE.invokeExact(copy, program.length);
                }
            }
            if (status != 0) throw new IllegalStateException("Invalid surface program: " + status);
        } catch (Throwable t) { throw failure(t); }
    }

    private final class Compiler {
        final List<Integer> words = new ArrayList<>(List.of(0,0,0,0,0,0,0,0));
        final List<int[]> biomeData = new ArrayList<>();
        final List<SurfaceRules.Condition> conditions = new ArrayList<>();
        final List<SurfaceRules.SurfaceRule> rules = new ArrayList<>();
        final List<SurfaceRules.ConditionSource> sources = new ArrayList<>();
        boolean canBatch = true, usesBiomes;
        int emit(int op, int a, int b, int c, int d) {
            int pc = words.size(); for (int v : new int[]{op,a,b,c,d,0,0,0}) words.add(v); return pc;
        }
        void continuation(SurfaceRules.RuleSource source) {
            canBatch = false;
            int slot = rules.size(); rules.add(new NativeSurface(source, context, registry, biomeManager));
            emit(9, slot, 0, 0, 0);
        }
        void rule(SurfaceRules.RuleSource source, int depth) {
            // Large/deep trees use multiple bounded native programs. The scalar
            // adapter preserves extension observations between their results.
            if (depth >= 128) { continuation(source); return; }
            if (source instanceof SurfaceRules.SequenceRuleSource sequence) {
                for (int i = 0; i < sequence.sequence().size(); i++) {
                    if (words.size() >= 32768) {
                        continuation(new SurfaceRules.SequenceRuleSource(sequence.sequence().subList(i, sequence.sequence().size())));
                        break;
                    }
                    rule(sequence.sequence().get(i), depth + 1);
                }
            } else if (source instanceof SurfaceRules.TestRuleSource test) {
                int pc = condition(test.ifTrue()); rule(test.thenRun(), depth + 1); words.set(pc + 5, words.size());
            } else if (source instanceof SurfaceRules.BlockRuleSource block) {
                emit(1, Block.getId(block.resultState()), 0, 0, 0);
            } else if (source == SurfaceRules.Bandlands.INSTANCE && context.system.getClass() == SurfaceSystem.class) {
                BlockState[] bands = context.system.bandStates();
                int pc = emit(10, 0, bands.length, 0, 0);
                int[] data = new int[bands.length + 1]; data[0] = pc;
                for (int i = 1; i < data.length; i++) data[i] = Block.getId(bands[i - 1]);
                biomeData.add(data);
            } else {
                canBatch = false;
                int slot = rules.size(); rules.add(source.apply(context)); emit(9, slot, 0, 0, 0);
            }
        }
        int condition(SurfaceRules.ConditionSource source) {
            boolean inverse = false;
            while (source instanceof SurfaceRules.NotConditionSource not) { inverse = !inverse; source = not.target(); }
            int pc;
            if (source instanceof SurfaceRules.StoneDepthCheck stone) {
                pc = emit(3, stone.offset(), stone.addSurfaceDepth() ? 1 : 0, stone.secondaryDepthRange(), stone.surfaceType() == CaveSurface.CEILING ? 1 : 0);
            } else if (source instanceof SurfaceRules.WaterConditionSource water) {
                pc = emit(4, water.offset(), water.surfaceDepthMultiplier(), water.addStoneDepth() ? 1 : 0, 0);
            } else if (source instanceof SurfaceRules.YConditionSource y) {
                pc = emit(5, y.anchor().resolveY(context.context), y.surfaceDepthMultiplier(), y.addStoneDepth() ? 1 : 0, 0);
            } else if (source == SurfaceRules.Hole.INSTANCE) {
                pc = emit(6, 0, 0, 0, 0);
            } else if (source == SurfaceRules.AbovePreliminarySurface.INSTANCE) {
                pc = emit(8, 0, 0, 0, 0);
            } else if (source instanceof SurfaceRules.VerticalGradientConditionSource gradient) {
                // Most blocks lie outside the random transition band. Preserve
                // Java's ordered bounds checks without a callback per block.
                int slot = conditions.size(); conditions.add(source.apply(context)); sources.add(source);
                pc = emit(11, slot, gradient.trueAtAndBelow().resolveY(context.context),
                    gradient.falseAtAndAbove().resolveY(context.context), 0);
            } else if (source instanceof SurfaceRules.BiomeConditionSource biome && biomeManager != null) {
                usesBiomes = true;
                // The Java predicate snapshots its keys when the source is
                // constructed. Its serialized list can subsequently mutate.
                int[] ids = registry.listElements().filter(holder -> biome.biomeNameTest.test(holder.key()))
                    .mapToInt(holder -> registry.getId(holder.value())).distinct().sorted().toArray();
                pc = emit(7, 0, ids.length, 0, 0);
                int[] data = new int[ids.length + 1]; data[0] = pc;
                System.arraycopy(ids, 0, data, 1, ids.length);
                biomeData.add(data);
            } else {
                boolean xz = source instanceof SurfaceRules.NoiseThresholdConditionSource || source == SurfaceRules.Steep.INSTANCE;
                boolean known = xz || source == SurfaceRules.Temperature.INSTANCE || source instanceof SurfaceRules.VerticalGradientConditionSource || source instanceof SurfaceRules.BiomeConditionSource;
                if (!known) canBatch = false;
                int slot = conditions.size(); conditions.add(source.apply(context)); sources.add(source); pc = emit(2, slot, xz ? 1 : 0, 0, 0);
            }
            words.set(pc + 6, inverse ? 1 : 0); return pc;
        }
    }

    private static final java.util.Map<SurfaceRules.RuleSource, Boolean> HEIGHTMAP_ONLY =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Whether a rule reads its chunk only through the world-generation heightmaps
     * (steep) when evaluated: built-in rules and conditions only. A native stage
     * that owns the chunk's blocks may then evaluate it after syncing heightmaps. */
    static boolean readsOnlyHeightmaps(SurfaceRules.RuleSource source) {
        Boolean known = HEIGHTMAP_ONLY.get(source);
        if (known == null) {
            known = readsOnlyHeightmaps(source, 0);
            HEIGHTMAP_ONLY.put(source, known);
        }
        return known;
    }

    private static boolean readsOnlyHeightmaps(SurfaceRules.RuleSource source, int depth) {
        if (depth > 512) return false;
        if (source instanceof SurfaceRules.SequenceRuleSource sequence) {
            for (var rule : sequence.sequence()) if (!readsOnlyHeightmaps(rule, depth + 1)) return false;
            return true;
        }
        if (source instanceof SurfaceRules.TestRuleSource test) {
            return knownCondition(test.ifTrue()) && readsOnlyHeightmaps(test.thenRun(), depth + 1);
        }
        return source instanceof SurfaceRules.BlockRuleSource || source == SurfaceRules.Bandlands.INSTANCE;
    }

    private static boolean knownCondition(SurfaceRules.ConditionSource source) {
        while (source instanceof SurfaceRules.NotConditionSource not) source = not.target();
        return source instanceof SurfaceRules.StoneDepthCheck || source instanceof SurfaceRules.WaterConditionSource
            || source instanceof SurfaceRules.YConditionSource || source == SurfaceRules.Hole.INSTANCE
            || source == SurfaceRules.AbovePreliminarySurface.INSTANCE || source instanceof SurfaceRules.VerticalGradientConditionSource
            || source instanceof SurfaceRules.BiomeConditionSource || source instanceof SurfaceRules.NoiseThresholdConditionSource
            || source == SurfaceRules.Steep.INSTANCE || source == SurfaceRules.Temperature.INSTANCE;
    }

    private void capacity(int count) {
        if (count <= 0 || count > LIMIT) throw new IllegalArgumentException("Invalid surface column height: " + count);
        if (flags.length < count) { flags = new int[count]; output = new int[count]; biomes = new int[count]; }
    }

    /** Returns quart-grid indices for one column, preserving Java's tie breaking. */
    static void biomeIndices(long seed, int x, int z, int minY, int count, int[] result) {
        if (count <= 0 || count > LIMIT || result.length < count) throw new IllegalArgumentException("Invalid biome column");
        try {
            int status = (int)BIOMES.invokeExact(seed, x, z, minY, count, MemorySegment.ofArray(result));
            if (status != 0) throw new IllegalStateException("Native biome column: " + status);
        } catch (Throwable t) { throw failure(t); }
    }

    private void prepareBiomes(int x, int z, int minY, int count) {
        if (!usesBiomes) return;
        biomeIndices(biomeManager.biomeZoomSeed, x, z, minY, count, biomes);
        int baseY = (minY - 2) >> 2, qCount = ((minY + count - 3) >> 2) - baseY + 2;
        if (biomeCache.length < qCount * 4) biomeCache = new int[qCount * 4];
        Arrays.fill(biomeCache, 0, qCount * 4, Integer.MIN_VALUE);
        int baseX = (x - 2) >> 2, baseZ = (z - 2) >> 2;
        for (int y = 0; y < count; y++) {
            int index = biomes[y]; int id = biomeCache[index];
            if (id == Integer.MIN_VALUE) {
                int column = index / qCount;
                var biome = biomeManager.getNoiseBiomeAtQuart(baseX + (column >> 1), baseY + index % qCount, baseZ + (column & 1));
                id = biome.unwrapKey().isPresent() ? registry.getId(biome.value()) : -1;
                biomeCache[index] = id;
            }
            biomes[y] = id;
        }
    }

    void column(ChunkAccess chunk, BlockColumn target, BlockState defaultBlock, int x, int z, int top) {
        int minY = chunk.getMinY(), count = top - minY + 1;
        if (count <= 0) return;
        capacity(count); Arrays.fill(output, 0, count, -1);
        int defaultFlag = defaultBlock.isAir() ? 0 : !defaultBlock.getFluidState().isEmpty() ? 1 : 3;
        var sections = chunk.getSections();
        for (int y = 0; y < count;) {
            int absolute = minY + y;
            int end = Math.min(count, y + 16 - (absolute & 15));
            int sectionIndex = chunk.getSectionIndex(absolute);
            if (sectionIndex < 0 || sectionIndex >= sections.length || sections[sectionIndex].hasOnlyAir()) {
                Arrays.fill(flags, y, end, 0); y = end; continue;
            }
            var section = sections[sectionIndex];
            for (; y < end; y++) {
                BlockState state = section.getBlockState(x & 15, (minY + y) & 15, z & 15);
                flags[y] = state == defaultBlock ? defaultFlag : state.isAir() ? 0 : !state.getFluidState().isEmpty() ? 1 : 2;
            }
        }
        prepareBiomes(x, z, minY, count);
        reset(minY, count); int committed = count;
        for (;;) {
            int status = step(count);
            // Commit only completed blocks. A request can observe all prior
            // writes, but never sees the current rule's as-yet undecided result.
            int boundary = frame[0] + 1;
            for (int y = committed - 1; y >= boundary; y--) if (output[y] >= 0) target.setBlock(minY + y, Block.stateById(output[y]));
            committed = boundary;
            if (status == 1) { context.lastUpdateY = updateBase + frame[6]; return; }
            if (status == 2) respond(x, z);
        }
    }

    boolean usesBiomes() {
        return usesBiomes;
    }

    /** How Rust answers each condition slot: per slot [kind (0 Java, 1 steep,
     * 2 vertical gradient, 3 noise threshold), low, high, positional random
     * kind] ints, two longs (gradient seeds or the noise state's address) and
     * two doubles (threshold bounds); {@code keep} holds what must stay live. */
    record Slots(int[] ints, long[] longs, double[] doubles, List<Object> keep) {}

    /** Every slot answered by Java except steep (comparison runs). */
    Slots javaSlots() {
        int[] ints = new int[conditionSources.length * 4];
        for (int slot = 0; slot < conditionSources.length; slot++) if (conditionSources[slot] == SurfaceRules.Steep.INSTANCE) ints[slot * 4] = 1;
        return new Slots(ints, new long[conditionSources.length * 2], new double[conditionSources.length * 2], List.of());
    }

    Slots nativeSlots() {
        int count = conditionSources.length;
        int[] ints = new int[count * 4];
        long[] longs = new long[count * 2];
        double[] doubles = new double[count * 2];
        List<Object> keep = new ArrayList<>();
        for (int slot = 0; slot < count; slot++) {
            var source = conditionSources[slot];
            if (source == SurfaceRules.Steep.INSTANCE) {
                ints[slot * 4] = 1;
            } else if (source instanceof SurfaceRules.VerticalGradientConditionSource gradient) {
                // The condition's own bounds and factory, as apply() resolves them.
                var factory = context.randomState.getOrCreateRandomFactory(gradient.randomName());
                if (factory.getClass() == XoroshiroRandomSource.XoroshiroPositionalRandomFactory.class) {
                    var xoroshiro = (XoroshiroRandomSource.XoroshiroPositionalRandomFactory)factory;
                    System.arraycopy(new int[]{2, gradient.trueAtAndBelow().resolveY(context.context), gradient.falseAtAndAbove().resolveY(context.context), 1},
                        0, ints, slot * 4, 4);
                    longs[slot * 2] = xoroshiro.seedLo();
                    longs[slot * 2 + 1] = xoroshiro.seedHi();
                } else if (factory.getClass() == LegacyRandomSource.LegacyPositionalRandomFactory.class) {
                    System.arraycopy(new int[]{2, gradient.trueAtAndBelow().resolveY(context.context), gradient.falseAtAndAbove().resolveY(context.context), 2},
                        0, ints, slot * 4, 4);
                    longs[slot * 2] = ((LegacyRandomSource.LegacyPositionalRandomFactory)factory).seed();
                }
            } else if (source instanceof SurfaceRules.NoiseThresholdConditionSource threshold) {
                var state = context.randomState.getOrCreateNoise(threshold.noise()).nativeState();
                if (state != null && state.state().isNative()) {
                    ints[slot * 4] = 3;
                    longs[slot * 2] = state.state().address();
                    doubles[slot * 2] = threshold.minThreshold();
                    doubles[slot * 2 + 1] = threshold.maxThreshold();
                    keep.add(state);
                }
            }
        }
        return new Slots(ints, longs, doubles, keep);
    }

    /** The context's noise chunk, for its preliminary surface levels. */
    NoiseChunk noiseChunk() {
        return context.noiseChunk();
    }

    SurfaceSystem system() {
        return context.system;
    }

    /** {@link #column} over Rust-owned storage: Rust scans the column, selects its
     * biomes, answers steep and commits blocks; Java answers its other requests. */
    void column(NativeSurfaceChunk owned, int x, int z, int top) {
        int count = owned.begin(x, z, top, usesBiomes);
        if (count == 0) return;
        reset(context.chunk.getMinY(), count);
        for (;;) {
            int status;
            try {
                status = (int)NativeSurfaceChunk.RUN.invokeExact(owned.handle, MemorySegment.ofArray(program), program.length,
                    MemorySegment.ofArray(frame), MemorySegment.ofArray(cache), secondary);
            } catch (Throwable t) { throw failure(t); }
            if (status == 1) { context.lastUpdateY = updateBase + frame[6]; return; }
            if (status != 2) throw new IllegalStateException("Native surface column: " + status);
            respond(x, z);
        }
    }

    private void reset(int minY, int count) {
        Arrays.fill(frame, 0); Arrays.fill(cache, -1); secondary = 0;
        frame[0] = count - 1; frame[2] = Integer.MIN_VALUE; frame[3] = Integer.MAX_VALUE;
        frame[11] = context.surfaceDepth; frame[12] = minY; frame[20] = DimensionType.WAY_BELOW_MIN_Y;
        updateBase = context.lastUpdateY; syncedY = Integer.MIN_VALUE;
    }
    private int step(int count) {
        try {
            int status = (int)STEP.invokeExact(MemorySegment.ofArray(program), program.length, MemorySegment.ofArray(flags),
                MemorySegment.ofArray(output), MemorySegment.ofArray(biomes), count, MemorySegment.ofArray(frame), MemorySegment.ofArray(cache), secondary);
            if (status < 0) throw new IllegalStateException("Native surface evaluation: " + status);
            return status;
        } catch (Throwable t) { throw failure(t); }
    }
    // Requests Java answered, by kind: band offset, secondary noise, minimum
    // surface level, steep, vertical gradient, noise threshold, other conditions.
    static final java.util.concurrent.atomic.AtomicLongArray JAVA_ANSWERS = new java.util.concurrent.atomic.AtomicLongArray(7);

    private void count(int request) {
        int kind;
        if (request < 0) {
            kind = request == -1 ? 0 : request == -2 ? 1 : request == -3 ? 2 : 6;
        } else {
            var source = conditionSources[request];
            kind = source == SurfaceRules.Steep.INSTANCE ? 3 : source instanceof SurfaceRules.VerticalGradientConditionSource ? 4
                : source instanceof SurfaceRules.NoiseThresholdConditionSource ? 5 : 6;
        }
        JAVA_ANSWERS.incrementAndGet(kind);
    }

    private void respond(int x, int z) {
        if (frame[17] >= -3 && frame[17] < conditionSources.length) count(frame[17]);
        int y = frame[12] + frame[0];
        if (syncedY != y) {
            context.updateY(frame[1], frame[10], frame[2], x, y, z);
            context.lastUpdateY = updateBase + frame[6]; syncedY = y;
        }
        int request = frame[17];
        if (request == -1) { frame[18] = context.system.bandOffset(x, z); frame[19] = 1; }
        else if (request == -2) { secondary = context.getSurfaceSecondary(); frame[16] = 1; }
        else if (request == -3) { frame[14] = context.getMinSurfaceLevel(); frame[15] = 1; }
        else {
            if (request >= 0) frame[7] = conditions[request].test() ? 1 : 0;
            else { BlockState state = rules[-4 - request].tryApply(x, y, z); frame[7] = state == null ? -1 : Block.getId(state); }
            frame[8] = 1;
        }
    }

    /** Scalar adapter used by carvers and extension-owned contexts. */
    @Override public BlockState tryApply(int x, int y, int z) {
        capacity(1); reset(y, 1); output[0] = -1;
        frame[1] = context.stoneDepthAbove; frame[2] = context.waterHeight; frame[10] = context.stoneDepthBelow;
        frame[4] = 8; frame[5] = 1; syncedY = y;
        if (usesBiomes) prepareBiomes(x, z, y, 1);
        for (;;) {
            int status = step(1);
            if (status == 1) return output[0] < 0 ? null : Block.stateById(output[0]);
            if (status == 2) respond(x, z);
        }
    }
    private static RuntimeException failure(Throwable t) {
        if (t instanceof Error e) throw e;
        if (t instanceof RuntimeException e) return e;
        return new IllegalStateException("Native surface evaluation failed", t);
    }
}
