package net.minecraft.world.level.levelgen;

import com.google.common.collect.MapMaker;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.QuartPos;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.NativeNoiseState;
import org.jetbrains.annotations.Nullable;

/** Rust-owned BIOMES stage: {@code ChunkAccess.fillBiomesFromNoise} with a plain
 * {@link MultiNoiseBiomeSource}. A {@link RandomState} compiles its router's six
 * climate functions once into a native point program; for each chunk one call
 * samples every quart through the chunk's FlatCache grid, searches the climate
 * tree in section order from the thread's previous leaf and replays each
 * section's biome container writes. Java installs the containers and the new
 * previous leaf. Anything else keeps Java's fill. */
final class NativeBiomeFill {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_climate_program_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    // A whole chunk of sampling and searches: an ordinary downcall over native buffers.
    private static final MethodHandle FILL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_biome_fill",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_program_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final int PALETTE_CAP = 8, RAW_CAP = 32;
    private static final Cleaner CLEANER = Cleaner.create();
    // Leaf registry IDs per parameter list and ID map; weak keys do not retain lists.
    private static final ConcurrentMap<Climate.ParameterList<?>, LeafIds> LEAF_IDS = new MapMaker().weakKeys().makeMap();
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaBiomeFill");
    // Chunks whose biomes were filled natively, so tests can prove the route engaged.
    static final AtomicLong CHUNKS = new AtomicLong();

    private final long handle;
    // The program references these states; they stay reachable while it lives.
    private final NativeNoiseState[] states;

    private NativeBiomeFill(long handle, NativeNoiseState[] states) {
        this.handle = handle;
        this.states = states;
        CLEANER.register(this, new Release(handle));
    }

    private record Release(long handle) implements Runnable {
        @Override
        public void run() {
            try {
                RELEASE.invokeExact(this.handle);
            } catch (Throwable error) {
                throw new IllegalStateException("Cannot release native climate program", error);
            }
        }
    }

    private record LeafIds(IdMap<Holder<Biome>> ids, int[] values) {}

    static void setEnabled(boolean value) {
        enabled = value;
    }

    static boolean enabled() {
        return enabled;
    }

    /** Compiles a RandomState's climate functions as {@code NoiseChunk.cachedClimateSampler}
     * wraps them; null keeps Java's fill. */
    @Nullable
    static NativeBiomeFill compile(NoiseRouter router) {
        // Chunk wrapping rebuilds every node (see NativeChunkNoise.compile).
        var normalized = new java.util.HashMap<DensityFunction, DensityFunction>();
        NoiseRouter rebuilt = router.mapAll(f -> normalized.computeIfAbsent(f,
            g -> g instanceof DensityFunctions.HolderHolder holder ? holder.function().value() : g));
        var compiler = new NativeNoiseRouter.Compiler(true, true);
        DensityFunction[] functions = {rebuilt.temperature(), rebuilt.vegetation(), rebuilt.continents(), rebuilt.erosion(), rebuilt.depth(),
            rebuilt.ridges()};
        int[] roots = new int[functions.length];
        for (int index = 0; index < roots.length; index++) {
            roots[index] = compiler.addRoot(functions[index]);
            if (roots[index] < 0) return null;
        }
        // No FlatCache tables; chunk bindings supply the grid.
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
        return handle == 0 ? null : new NativeBiomeFill(handle, packed.states());
    }

    /** Registry IDs of each node's leaf value (-1 for branches), or null when a
     * value is not the ID map's own holder (palettes compare holders by identity). */
    @Nullable
    private static int[] leafIds(Climate.ParameterList<Holder<Biome>> list, IdMap<Holder<Biome>> ids) {
        LeafIds cached = LEAF_IDS.get(list);
        if (cached != null && cached.ids() == ids) return cached.values();
        int[] values = new int[list.nativeNodeCount()];
        for (int node = 0; node < values.length; node++) {
            Holder<Biome> value = list.nativeLeafValue(node);
            if (value == null) {
                values[node] = -1;
                continue;
            }
            int id = ids.getId(value);
            if (id < 0 || ids.byId(id) != value) return null;
            values[node] = id;
        }
        LEAF_IDS.put(list, new LeafIds(ids, values));
        return values;
    }

    /** Fills every section's biomes as {@code chunk.fillBiomesFromNoise(resolver,
     * noise.cachedClimateSampler(...))} would; false leaves the chunk to Java. */
    static boolean fill(ChunkAccess chunk, BiomeResolver resolver, NoiseChunk noise, RandomState random) {
        if (!enabled || chunk.getClass() != ProtoChunk.class || !(resolver instanceof MultiNoiseBiomeSource source)
            || source.getClass() != MultiNoiseBiomeSource.class || noise.getClass() != NoiseChunk.class || noise.getBlender() != Blender.empty()
            || source.nativeParameters().getClass() != Climate.ParameterList.class) {
            return false;
        }
        NativeBiomeFill program = random.nativeBiomeFill();
        if (program == null) return false;
        var list = source.nativeParameters();
        var height = chunk.getHeightAccessorForGeneration();
        int minSection = height.getMinSectionY(), count = height.getMaxSectionY() - minSection + 1;
        if (count <= 0 || count > 256) return false;
        LevelChunkSection[] sections = new LevelChunkSection[count];
        int globalBits = -1;
        IdMap<Holder<Biome>> ids = null;
        int[] initial = new int[count];
        for (int index = 0; index < count; index++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(minSection + index));
            int bits = section.getClass() == LevelChunkSection.class ? section.generatedBiomeGlobalBits() : -1;
            if (bits < 4 || bits > 32 || (globalBits >= 0 && bits != globalBits)) return false;
            if (ids == null) ids = section.generatedBiomeIds();
            else if (section.generatedBiomeIds() != ids) return false;
            Holder<Biome> seed = section.generatedBiomeSeed();
            int id = ids.getId(seed);
            if (id < 0 || ids.byId(id) != seed) return false;
            initial[index] = id;
            globalBits = bits;
            sections[index] = section;
        }
        int[] values = leafIds(list, ids);
        if (values == null) return false;
        int nodes = list.nativeNodeCount();
        int[] geometry = {noise.firstNoiseX, noise.firstNoiseZ, QuartPos.fromSection(minSection), count, noise.noiseSizeXZ + 1, globalBits};
        // The fill samples at chunk quarts; Java's sampler would too.
        if (QuartPos.fromBlock(chunk.getPos().getMinBlockX()) != noise.firstNoiseX
            || QuartPos.fromBlock(chunk.getPos().getMinBlockZ()) != noise.firstNoiseZ) {
            return false;
        }
        int status;
        int[] info = new int[3 * count + 1], palettes = new int[PALETTE_CAP * count];
        long[] storage = new long[RAW_CAP * count];
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment valueMemory = arena.allocateFrom(ValueLayout.JAVA_INT, values);
            MemorySegment geometryMemory = arena.allocateFrom(ValueLayout.JAVA_INT, geometry);
            MemorySegment initialMemory = arena.allocateFrom(ValueLayout.JAVA_INT, initial);
            MemorySegment infoMemory = arena.allocate(ValueLayout.JAVA_INT, info.length);
            MemorySegment paletteMemory = arena.allocate(ValueLayout.JAVA_INT, palettes.length);
            MemorySegment rawMemory = arena.allocate(ValueLayout.JAVA_LONG, storage.length);
            status = (int)FILL.invokeExact(program.handle, list.nativeNodes(), nodes, valueMemory, list.previousNativeLeaf(), geometryMemory,
                initialMemory, infoMemory, paletteMemory, rawMemory);
            if (status == 0) {
                MemorySegment.copy(infoMemory, ValueLayout.JAVA_INT, 0, info, 0, info.length);
                MemorySegment.copy(paletteMemory, ValueLayout.JAVA_INT, 0, palettes, 0, palettes.length);
                MemorySegment.copy(rawMemory, ValueLayout.JAVA_LONG, 0, storage, 0, storage.length);
            }
        } catch (Throwable error) {
            throw new IllegalStateException("Native biome fill failed", error);
        } finally {
            Reference.reachabilityFence(program);
        }
        // A search with no leaf: Java's fill reproduces its failure.
        if (status == 1) return false;
        if (status != 0) throw new IllegalStateException("Native biome fill rejected: " + status);
        for (int index = 0; index < count; index++) {
            int requested = info[index * 3], entries = info[index * 3 + 1], longs = info[index * 3 + 2];
            List<Holder<Biome>> palette = new ArrayList<>(entries);
            for (int entry = 0; entry < entries; entry++) palette.add(ids.byId(palettes[index * PALETTE_CAP + entry]));
            sections[index].installGeneratedBiomes(requested, palette, java.util.Arrays.copyOfRange(storage, index * RAW_CAP, index * RAW_CAP + longs));
        }
        list.setPreviousNativeLeaf(info[3 * count]);
        CHUNKS.incrementAndGet();
        return true;
    }
}
