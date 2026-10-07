package net.minecraft.world.level.levelgen;

import com.google.common.collect.MapMaker;
import com.mojang.datafixers.util.Pair;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.synth.NativeNoiseState;
import org.jetbrains.annotations.Nullable;

/** Rust biome searches for a plain {@link MultiNoiseBiomeSource}
 * ({@code world/level/biome/search/}): {@code findBiomeHorizontal} and
 * {@code findClosestBiome3d} sample every position's climate with the
 * sampler's six functions compiled as one point program and search the
 * climate tree in Rust, in Java's visiting order and from this thread's
 * previous leaf. Java keeps the result objects and replays
 * {@code findBiomeHorizontal}'s random choice among the matches. */
public final class NativeBiomeSearch {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_climate_program_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_program_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle HORIZONTAL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_biome_search_horizontal",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle CLOSEST = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_biome_search_closest",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final Cleaner CLEANER = Cleaner.create();
    private static final Object UNSUPPORTED = new Object();
    // One point program per sampler; weak keys compare samplers by identity.
    private static final ConcurrentMap<Climate.Sampler, Object> PROGRAMS = new MapMaker().weakKeys().makeMap();
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaBiomeSearch");
    // Searches Rust completed, so tests can prove the route.
    public static final AtomicLong SEARCHES = new AtomicLong();

    /** A search Rust ran (with its result, possibly null), or {@link #UNHANDLED}. */
    public record Search(boolean handled, @Nullable Pair<BlockPos, Holder<Biome>> result) {}

    public static final Search UNHANDLED = new Search(false, null);

    private NativeBiomeSearch() {}

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    private static final class Program {
        final long handle;
        // The program references these states; they stay reachable while it lives.
        final NativeNoiseState[] states;

        Program(long handle, NativeNoiseState[] states) {
            this.handle = handle;
            this.states = states;
            CLEANER.register(this, () -> {
                try {
                    RELEASE.invokeExact(handle);
                } catch (Throwable error) {
                    throw new IllegalStateException("Cannot release native climate program", error);
                }
            });
        }
    }

    /** The sampler's six functions as a point program, or null when one has a
     * node Rust does not model. */
    @Nullable
    private static Program program(Climate.Sampler sampler) {
        Object cached = PROGRAMS.get(sampler);
        if (cached == null) {
            cached = compile(sampler);
            PROGRAMS.putIfAbsent(sampler, cached);
            cached = PROGRAMS.get(sampler);
        }
        return cached instanceof Program program ? program : null;
    }

    private static Object compile(Climate.Sampler sampler) {
        var compiler = new NativeNoiseRouter.Compiler(true);
        DensityFunction[] functions = {sampler.temperature(), sampler.humidity(), sampler.continentalness(), sampler.erosion(),
            sampler.depth(), sampler.weirdness()};
        int[] roots = new int[functions.length];
        for (int index = 0; index < roots.length; index++) {
            roots[index] = compiler.addRoot(functions[index]);
            if (roots[index] < 0) return UNSUPPORTED;
        }
        NativeNoiseRouter.Packed packed = compiler.pack(roots, 1, 1, 1, 1, 0, 0, 0, 0, 1);
        long handle;
        try {
            handle = (long)CREATE.invokeExact(MemorySegment.ofArray(packed.ints()), packed.ints().length, MemorySegment.ofArray(packed.doubles()),
                packed.doubles().length, MemorySegment.ofArray(packed.longs()), packed.longs().length);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot create native climate program", error);
        } finally {
            Reference.reachabilityFence(packed.states());
        }
        return handle == 0 ? UNSUPPORTED : new Program(handle, packed.states());
    }

    @Nullable
    private static Climate.ParameterList<Holder<Biome>> list(MultiNoiseBiomeSource source) {
        if (!enabled || source.getClass() != MultiNoiseBiomeSource.class) return null;
        var list = source.nativeParameters();
        return list.getClass() == Climate.ParameterList.class ? list : null;
    }

    /** Each node's predicate: 1 for leaves whose biome is accepted. */
    private static byte[] accept(Climate.ParameterList<Holder<Biome>> list, Predicate<Holder<Biome>> accepted) {
        byte[] accept = new byte[list.nativeNodeCount()];
        for (int node = 0; node < accept.length; node++) {
            Holder<Biome> value = list.nativeLeafValue(node);
            accept[node] = (byte)(value != null && accepted.test(value) ? 1 : 0);
        }
        return accept;
    }

    /** {@code findBiomeHorizontal(i, j, k, l, 1, biomes::contains, random, false, sampler)}
     * with a pure membership test, evaluated once per climate leaf. */
    public static Search horizontal(MultiNoiseBiomeSource source, int i, int j, int k, int l, Predicate<Holder<Biome>> biomes,
                                    RandomSource random, Climate.Sampler sampler) {
        var list = list(source);
        if (list == null || SharedConstants.DEBUG_ONLY_GENERATE_HALF_THE_WORLD || SharedConstants.debugGenerateSquareTerrainWithoutNoise) {
            return UNHANDLED;
        }
        Program program = program(sampler);
        if (program == null) return UNHANDLED;
        int n = QuartPos.fromBlock(i), o = QuartPos.fromBlock(k), p = QuartPos.fromBlock(l), q = QuartPos.fromBlock(j);
        long side = 2L * p + 1;
        if (p < 0 || side * side > 1 << 22) return UNHANDLED;
        int cap = (int)(side * side);
        byte[] accept = accept(list, biomes);
        int status;
        int[] matches, info = new int[2];
        try (Arena arena = Arena.ofConfined()) {
            var out = arena.allocate(ValueLayout.JAVA_INT, 3L * Math.max(cap, 1));
            var infoMemory = arena.allocate(ValueLayout.JAVA_INT, 2);
            status = (int)HORIZONTAL.invokeExact(program.handle, list.nativeNodes(), accept.length, arena.allocateFrom(ValueLayout.JAVA_BYTE, accept),
                list.previousNativeLeaf(), arena.allocateFrom(ValueLayout.JAVA_INT, n, q, o, p, 1), out, cap, infoMemory);
            MemorySegment.copy(infoMemory, ValueLayout.JAVA_INT, 0, info, 0, 2);
            matches = status == 0 ? out.asSlice(0, 12L * info[0]).toArray(ValueLayout.JAVA_INT) : null;
        } catch (Throwable error) {
            throw new IllegalStateException("Native biome search failed", error);
        } finally {
            Reference.reachabilityFence(program);
        }
        // No leaf or a sampling failure: Java's search reproduces the outcome.
        if (status == 1 || status == 2) return UNHANDLED;
        if (status != 0) throw new IllegalStateException("Native biome search rejected: " + status);
        list.setPreviousNativeLeaf(info[1]);
        // The original's reservoir choice, replayed over the matches in visiting order.
        Pair<BlockPos, Holder<Biome>> pair = null;
        int r = 0;
        for (int at = 0; at < info[0]; at++) {
            if (pair == null || random.nextInt(r + 1) == 0) {
                BlockPos blockPos = new BlockPos(QuartPos.toBlock(matches[3 * at]), j, QuartPos.toBlock(matches[3 * at + 1]));
                pair = Pair.of(blockPos, list.nativeLeafValue(matches[3 * at + 2]));
            }
            r++;
        }
        SEARCHES.incrementAndGet();
        return new Search(true, pair);
    }

    /** The search of {@code findClosestBiome3d} once its accepted biomes and
     * Ys ({@code Mth.outFromOrigin}) are known. */
    public static Search closest(MultiNoiseBiomeSource source, BlockPos center, int radius, int step, Set<Holder<Biome>> accepted, int[] ys,
                                 Climate.Sampler sampler) {
        var list = list(source);
        if (list == null) return UNHANDLED;
        Program program = program(sampler);
        if (program == null) return UNHANDLED;
        int spiral = Math.floorDiv(radius, step);
        if (spiral < 0 || spiral > 1 << 20) return UNHANDLED;
        byte[] accept = accept(list, accepted::contains);
        int status;
        int[] result;
        try (Arena arena = Arena.ofConfined()) {
            var out = arena.allocate(ValueLayout.JAVA_INT, 6);
            status = (int)CLOSEST.invokeExact(program.handle, list.nativeNodes(), accept.length, arena.allocateFrom(ValueLayout.JAVA_BYTE, accept),
                list.previousNativeLeaf(), arena.allocateFrom(ValueLayout.JAVA_INT, center.getX(), center.getZ(), spiral, step),
                arena.allocateFrom(ValueLayout.JAVA_INT, ys), ys.length, out);
            result = out.toArray(ValueLayout.JAVA_INT);
        } catch (Throwable error) {
            throw new IllegalStateException("Native biome search failed", error);
        } finally {
            Reference.reachabilityFence(program);
        }
        if (status == 1 || status == 2) return UNHANDLED;
        if (status != 0) throw new IllegalStateException("Native biome search rejected: " + status);
        list.setPreviousNativeLeaf(result[5]);
        SEARCHES.incrementAndGet();
        return new Search(true, result[0] == 0 ? null
            : Pair.of(new BlockPos(result[1], result[2], result[3]), list.nativeLeafValue(result[4])));
    }
}
