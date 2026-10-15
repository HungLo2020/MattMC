package net.minecraft.world.level.levelgen;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jetbrains.annotations.Nullable;

/** {@code CarvingContext.topMaterial} answered in Rust for one chunk's carvers:
 * the chunk's surface rule, its condition inputs and the biome source the
 * carvers' biome function samples. Temperature conditions call back into
 * {@code Biome.coldEnoughToSnow}, whose per-thread cache is part of its result. Null from {@link #create}
 * keeps Java's upcall (an extension rule or condition, a non-native noise or
 * random, or a biome Rust cannot name). */
final class NativeTopMaterial {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_scalar_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_scalar_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MethodHandle NATIVE_CALLS = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_carvers_native_top_calls",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG));

    private static final MethodHandle VERIFY = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_carvers_verify_top",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
    private static final MethodHandle VERIFICATION = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_carvers_top_verification",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    private static final MethodHandle REQUESTS = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_scalar_requests",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaCarverTopMaterial");
    static {
        if (Boolean.getBoolean("mattmc.worldgen.verifyCarverTop")) setVerify(true);
    }
    // Chunks whose rule kept Java's upcall.
    static final AtomicLong DECLINED = new AtomicLong();
    private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();

    /** What a binding depends on besides its surface system, by identity. */
    private record Key(Object rule, Object registry, @Nullable Object list, @Nullable Object fixed, long zoomSeed, Object levels) {
        boolean matches(Key other) {
            return rule == other.rule && registry == other.registry && list == other.list && fixed == other.fixed && zoomSeed == other.zoomSeed
                && levels == other.levels;
        }
    }

    /** A cached binding (null when the rule keeps Java's upcall). */
    private record Cached(Key key, @Nullable NativeTopMaterial binding) {}

    final long handle;
    /** Each climate leaf's biome registry id, or the fixed biome's id. */
    @Nullable
    final int[] leafBiomes;
    final int fixedBiome;
    final long zoomSeed;
    /** For Java's temperature answers: the biome registry and the system's sea level. */
    final Registry<Biome> registry;
    final int seaLevel;
    // The noise states and preliminary surface program Rust reads.
    private final List<Object> keep;

    private NativeTopMaterial(long handle, @Nullable int[] leafBiomes, int fixedBiome, long zoomSeed, Registry<Biome> registry, int seaLevel, List<Object> keep) {
        this.handle = handle;
        this.leafBiomes = leafBiomes;
        this.fixedBiome = fixedBiome;
        this.zoomSeed = zoomSeed;
        this.registry = registry;
        this.seaLevel = seaLevel;
        this.keep = keep;
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    /** Verification: every native top material also runs Java's upcall from the
     * same previous leaf, compares the state and the leaf afterwards, and keeps Java's. */
    static void setVerify(boolean value) {
        try {
            VERIFY.invokeExact(value ? 1 : 0);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot set top material verification", error);
        }
    }

    /** [verified, mismatched, then the first mismatch's x, y, z, native state,
     * Java state, native leaf, Java leaf]. */
    static long[] verification() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ValueLayout.JAVA_LONG, 9);
            VERIFICATION.invokeExact(out);
            return out.toArray(ValueLayout.JAVA_LONG);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot read top material verification", error);
        }
    }

    /** Requests the native route answered, by kind: band offset, secondary noise,
     * minimum surface level, steep, vertical gradient, noise threshold, biome, temperature. */
    static long[] requests() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ValueLayout.JAVA_LONG, 8);
            REQUESTS.invokeExact(out);
            return out.toArray(ValueLayout.JAVA_LONG);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot read native top material requests", error);
        }
    }

    /** Native top material evaluations since start. */
    static long nativeCalls() {
        try {
            return (long)NATIVE_CALLS.invokeExact();
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot read native top material count", error);
        }
    }

    /** The binding for a carved chunk whose biome function is {@code biomeManager::getBiome}
     * over a plain multi-noise source's climate tree ({@code list}) or a fixed {@code biome}.
     * Bindings are immutable in Rust and shared by every chunk (and thread) of the
     * surface system with the same inputs. */
    @Nullable
    static NativeTopMaterial create(CarvingContext context, ChunkAccess chunk, NoiseChunk noise, BiomeManager biomeManager,
                                    Function<BlockPos, Holder<Biome>> biomes, @Nullable Climate.ParameterList<Holder<Biome>> list,
                                    @Nullable Holder<Biome> fixed) {
        if (!enabled) return null;
        SurfaceSystem system = context.randomState().surfaceSystem();
        Key key = new Key(context.surfaceRule(), context.registryAccess().lookupOrThrow(Registries.BIOME), list, fixed, biomeManager.biomeZoomSeed,
            noise.nativeSurfaceLevel() == null ? noise : noise.nativeSurfaceLevel());
        @SuppressWarnings("unchecked")
        List<Cached> cached = (List<Cached>)system.nativeTopMaterials;
        if (cached != null) {
            for (Cached entry : cached) {
                if (entry.key().matches(key)) {
                    if (entry.binding() == null) DECLINED.incrementAndGet();
                    return entry.binding();
                }
            }
        }
        NativeTopMaterial binding = bind(context, chunk, noise, biomeManager, biomes, list, fixed);
        if (binding == null) DECLINED.incrementAndGet();
        synchronized (system) {
            @SuppressWarnings("unchecked")
            List<Cached> current = (List<Cached>)system.nativeTopMaterials;
            List<Cached> next = current == null ? new ArrayList<>() : new ArrayList<>(current);
            // A few inputs per system (one per dimension using it); keep the newest.
            if (next.size() >= 8) next.remove(0);
            next.add(new Cached(key, binding));
            system.nativeTopMaterials = List.copyOf(next);
        }
        return binding;
    }

    @Nullable
    private static NativeTopMaterial bind(CarvingContext context, ChunkAccess chunk, NoiseChunk noise, BiomeManager biomeManager,
                                          Function<BlockPos, Holder<Biome>> biomes, @Nullable Climate.ParameterList<Holder<Biome>> list,
                                          @Nullable Holder<Biome> fixed) {
        SurfaceSystem system = context.randomState().surfaceSystem();
        if (system.getClass() != SurfaceSystem.class || biomeManager.getClass() != BiomeManager.class || noise.getClass() != NoiseChunk.class
            || (list == null) == (fixed == null)) return null;
        Registry<Biome> registry = context.registryAccess().lookupOrThrow(Registries.BIOME);
        // The same compiled rule and condition sources topMaterial's scalar adapter uses.
        var surfaceContext = new SurfaceRules.Context(system, context.randomState(), chunk, noise, biomes, registry, context);
        var rule = new NativeSurface(context.surfaceRule(), surfaceContext, registry, null);
        IntArrayList biomeIds = new IntArrayList();
        NativeSurface.Slots slots = rule.scalarSlots(biomeIds);
        if (slots == null) return null;
        List<Object> keep = new ArrayList<>(slots.keep());
        NativeSurfaceLevel levels = noise.nativeSurfaceLevel();
        if (levels == null) return null;
        keep.add(levels);
        long band = address(system.clayBandsOffsetNoise(), keep), secondary = address(system.surfaceSecondaryNoise(), keep);
        long surface = address(system.surfaceNoise(), keep);
        long[] depth = positional(system.noiseRandom());
        if (band == 0 || secondary == 0 || surface == 0 || depth == null) return null;
        int[] leafBiomes = null;
        int fixedBiome = -1;
        if (list != null) {
            leafBiomes = new int[list.nativeNodeCount()];
            for (int node = 0; node < leafBiomes.length; node++) {
                Holder<Biome> value = list.nativeLeafValue(node);
                leafBiomes[node] = value == null ? -1 : id(registry, value);
            }
        } else {
            fixedBiome = id(registry, fixed);
            // Every biome condition and temperature then needs a named biome.
            if (fixedBiome < 0) return null;
        }
        int[] program = rule.program();
        long handle;
        try (Arena arena = Arena.ofConfined()) {
            long[] inputs = {band, secondary, levels.handle(), surface};
            handle = (long)CREATE.invokeExact(arena.allocateFrom(ValueLayout.JAVA_INT, program), program.length,
                arena.allocateFrom(ValueLayout.JAVA_INT, slots.ints()), slots.ints().length / 4, arena.allocateFrom(ValueLayout.JAVA_LONG, slots.longs()),
                arena.allocateFrom(ValueLayout.JAVA_DOUBLE, slots.doubles()), arena.allocateFrom(ValueLayout.JAVA_INT, biomeIds.toIntArray()), biomeIds.size(),
                arena.allocateFrom(ValueLayout.JAVA_LONG, inputs), arena.allocateFrom(ValueLayout.JAVA_LONG, depth), DimensionType.WAY_BELOW_MIN_Y);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot bind native top material", error);
        }
        if (handle == 0) return null;
        NativeTopMaterial binding = new NativeTopMaterial(handle, leafBiomes, fixedBiome, biomeManager.biomeZoomSeed, registry, system.getSeaLevel(), keep);
        CLEANER.register(binding, new Release(handle));
        return binding;
    }

    /** A biome's registry id, or -1 when the holder names none. */
    private static int id(Registry<Biome> registry, Holder<Biome> biome) {
        return biome.unwrapKey().isPresent() ? registry.getId(biome.value()) : -1;
    }

    private static long address(NormalNoise noise, List<Object> keep) {
        var state = noise.nativeState();
        if (state == null || !state.state().isNative()) return 0L;
        keep.add(state);
        return state.state().address();
    }

    /** A positional random's ABI [kind, a, b] (1 Xoroshiro, 2 Legacy), or null. */
    @Nullable
    static long[] positional(PositionalRandomFactory factory) {
        if (factory.getClass() == XoroshiroRandomSource.XoroshiroPositionalRandomFactory.class) {
            var xoroshiro = (XoroshiroRandomSource.XoroshiroPositionalRandomFactory)factory;
            return new long[]{1, xoroshiro.seedLo(), xoroshiro.seedHi()};
        }
        if (factory.getClass() == LegacyRandomSource.LegacyPositionalRandomFactory.class) {
            return new long[]{2, ((LegacyRandomSource.LegacyPositionalRandomFactory)factory).seed(), 0};
        }
        return null;
    }

    /** Frees the Rust binding once its NativeTopMaterial is unreachable. */
    private record Release(long handle) implements Runnable {
        @Override
        public void run() {
            try {
                RELEASE.invokeExact(this.handle);
            } catch (Throwable error) {
                throw new IllegalStateException("Cannot release native top material", error);
            }
        }
    }

    /** Keeps the inputs Rust reads reachable until the carvers finish. */
    List<Object> keep() {
        return this.keep;
    }
}
