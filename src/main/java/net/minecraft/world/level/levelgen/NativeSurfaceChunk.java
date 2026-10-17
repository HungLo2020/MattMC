package net.minecraft.world.level.levelgen;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jetbrains.annotations.Nullable;

/** The SURFACE stage over Rust-owned chunk storage ({@link NativeProtoChunk}),
 * with a quart biome table. Columns scan, select biomes, evaluate and commit in
 * Rust; the extensions read and write through {@link #column}; Java answers only
 * its own conditions. The storage installs once at the end, as setBlockState
 * would have left the chunk: section palettes, storage and counters, both
 * heightmaps and fluid post-processing marks. */
final class NativeSurfaceChunk implements AutoCloseable {
    private static final MethodHandle CREATE = bind("create", true, FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle BEGIN = bind("begin", false, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    // Bounded per call like the surface step; borrows the frame and cache.
    static final MethodHandle RUN = bind("run", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle RELEASE = bind("release", false, FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle PASS_CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_pass_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
        Linker.Option.critical(true));
    // Runs until every column is done or Java must answer; borrows the frame, cache and state.
    static final MethodHandle PASS_RUN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_pass_run",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS), Linker.Option.critical(true));
    static final MethodHandle PASS_RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_pass_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    // Production reads the property once; tests compare the pass with Java's column loop.
    private static volatile boolean passEnabled = !Boolean.getBoolean("mattmc.worldgen.javaSurfacePass");
    // Chunks whose column loop ran in Rust, and the frozen ocean melt checks it asked Java.
    static final AtomicLong PASS_CHUNKS = new AtomicLong(), PASS_MELT_CALLS = new AtomicLong();
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaSurfaceStorage");
    // Comparison runs keep Java answering conditions and surface inputs on Rust storage.
    static volatile boolean nativeConditions = !Boolean.getBoolean("mattmc.worldgen.javaSurfaceConditions");
    // Chunks whose surface stage ran on Rust storage, so tests can prove the route engaged.
    static final AtomicLong CHUNKS = new AtomicLong();

    private static MethodHandle bind(String suffix, boolean critical, FunctionDescriptor descriptor) {
        return critical
            ? NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_chunk_" + suffix, descriptor, Linker.Option.critical(true))
            : NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_chunk_" + suffix, descriptor);
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    static void setPassEnabled(boolean value) {
        passEnabled = value;
    }

    final long handle;
    private final NativeProtoChunk storage;
    // The noise states and programs Rust reads; they stay reachable with the stage.
    private final List<Object> keep;

    private NativeSurfaceChunk(long handle, NativeProtoChunk storage, List<Object> keep) {
        this.handle = handle;
        this.storage = storage;
        this.keep = keep;
    }

    /** A noise's native state address for Rust, or 0 to keep Java answering. */
    private static long address(NormalNoise noise, List<Object> keep) {
        var state = noise.nativeState();
        if (state == null || !state.state().isNative()) return 0L;
        keep.add(state);
        return state.state().address();
    }

    /** Moves a chunk's surface stage to Rust storage; null keeps setBlockState
     * writes. Requires a batched rule and a storage {@link NativeProtoChunk} accepts. */
    @Nullable
    static NativeSurfaceChunk create(ChunkAccess chunk, NativeSurface rule, @Nullable BiomeManager biomes, Registry<Biome> registry, BlockState defaultBlock) {
        if (!enabled || !rule.canBatch || (rule.usesBiomes() && biomes == null)) return null;
        int minY = chunk.getMinY(), height = chunk.getHeight();
        // Quarts any column's biome corners can select: the chunk plus one quart
        // each way in X/Z, and the full height with one quart of margin.
        int qx = (chunk.getPos().getMinBlockX() >> 2) - 1, qz = (chunk.getPos().getMinBlockZ() >> 2) - 1, qy = (minY - 2) >> 2;
        // The tallest column starts at minY + height (one above the top block).
        int sizeX = 6, sizeZ = 6, sizeY = ((minY + height - 2) >> 2) + 1 - qy + 1;
        NativeSurface.Slots slots = nativeConditions ? rule.nativeSlots() : rule.javaSlots();
        List<Object> keep = new ArrayList<>(slots.keep());
        it.unimi.dsi.fastutil.ints.IntArrayList ints = new it.unimi.dsi.fastutil.ints.IntArrayList();
        ints.addElements(0, new int[]{Block.getId(defaultBlock), rule.usesBiomes() ? 1 : 0, qx, qy, qz, sizeX, sizeY, sizeZ, slots.ints().length / 4});
        ints.addElements(ints.size(), slots.ints());
        // The system's band offset and secondary noises, and the noise chunk's
        // preliminary surface program for minimum surface levels.
        SurfaceSystem system = rule.system();
        NoiseChunk noise = rule.noiseChunk();
        NativeSurfaceLevel levels = noise != null && noise.getClass() == NoiseChunk.class ? noise.nativeSurfaceLevel() : null;
        if (levels != null) keep.add(levels);
        long[] inputs = nativeConditions
            ? new long[]{address(system.clayBandsOffsetNoise(), keep), address(system.surfaceSecondaryNoise(), keep), levels == null ? 0L : levels.handle()}
            : new long[3];
        if (rule.usesBiomes()) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int y = 0; y < sizeY; y++) {
                        var biome = biomes.getNoiseBiomeAtQuart(qx + x, qy + y, qz + z);
                        ints.add(biome.unwrapKey().isPresent() ? registry.getId(biome.value()) : -1);
                    }
                }
            }
        }
        NativeProtoChunk storage = NativeProtoChunk.create(chunk);
        if (storage == null) return null;
        int[] intArray = ints.toIntArray();
        long handle;
        try {
            handle = (long)CREATE.invokeExact(storage.handle, MemorySegment.ofArray(intArray), intArray.length, biomes == null ? 0L : biomes.biomeZoomSeed,
                MemorySegment.ofArray(slots.longs()), MemorySegment.ofArray(slots.doubles()), MemorySegment.ofArray(inputs));
        } catch (Throwable error) {
            storage.close();
            throw new IllegalStateException("Cannot create native surface stage", error);
        }
        if (handle == 0) {
            storage.close();
            return null;
        }
        return new NativeSurfaceChunk(handle, storage, keep);
    }

    /** {@code getHeight(WORLD_SURFACE_WG, x, z)} on the Rust heightmap. */
    int height(int x, int z) {
        return this.storage.height(x, z);
    }

    /** buildSurface's block column over Rust storage, at the position's X and Z. */
    BlockColumn column(BlockPos.MutableBlockPos position) {
        return this.storage.column(position);
    }

    /** Starts column (x, z) to top; the column's length (0: nothing to evaluate). */
    int begin(int x, int z, int top, boolean usesBiomes) {
        int count;
        try {
            count = (int)BEGIN.invokeExact(this.handle, x, z, top, usesBiomes ? 1 : 0);
        } catch (Throwable error) {
            throw new IllegalStateException("Native surface column failed", error);
        }
        if (count < 0) throw new IllegalStateException("Native surface column rejected: " + count);
        return count;
    }

    /** The Rust column loop for this stage (a pass handle), or 0 to keep Java's
     * loop. Decided before any write: every noise, the positional random, the
     * preliminary surface program and the quart biome table must be native, and
     * the column biome's Y must fall inside the table. */
    long createPass(SurfaceSystem system, NativeSurface rule, Registry<Biome> registry, boolean biomeAtZero, ChunkAccess chunk) {
        if (!passEnabled || !nativeConditions || !rule.usesBiomes() || system.getClass() != SurfaceSystem.class) return 0L;
        // buildSurface reads the column biome at Y 0 with the legacy random; the table covers the chunk's height.
        if (biomeAtZero && (0 < chunk.getMinY() || 0 >= chunk.getMinY() + chunk.getHeight())) return 0L;
        long[] random = NativeTopMaterial.positional(system.noiseRandom());
        if (random == null) return 0L;
        NormalNoise[] badlands = system.badlandsNoises(), iceberg = system.icebergNoises();
        long[] longs = {address(system.surfaceNoise(), this.keep), address(badlands[0], this.keep), address(badlands[1], this.keep),
            address(badlands[2], this.keep), address(iceberg[0], this.keep), address(iceberg[1], this.keep), address(iceberg[2], this.keep),
            random[0], random[1], random[2]};
        for (int at = 0; at < 7; at++) if (longs[at] == 0) return 0L;
        int[] defaults = stateIds(system.defaultBlock().getBlock()), water = stateIds(net.minecraft.world.level.block.Blocks.WATER);
        int[] head = {1, chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ(), system.getSeaLevel(), biomeAtZero ? 1 : 0,
            biomeId(registry, net.minecraft.world.level.biome.Biomes.ERODED_BADLANDS), biomeId(registry, net.minecraft.world.level.biome.Biomes.FROZEN_OCEAN),
            biomeId(registry, net.minecraft.world.level.biome.Biomes.DEEP_FROZEN_OCEAN), Block.getId(system.defaultBlock()),
            Block.getId(net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState()),
            Block.getId(net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState()),
            net.minecraft.world.level.dimension.DimensionType.WAY_BELOW_MIN_Y, defaults.length, water.length};
        int[] ints = new int[head.length + defaults.length + water.length];
        System.arraycopy(head, 0, ints, 0, head.length);
        System.arraycopy(defaults, 0, ints, head.length, defaults.length);
        System.arraycopy(water, 0, ints, head.length + defaults.length, water.length);
        try {
            return (long)PASS_CREATE.invokeExact(this.handle, MemorySegment.ofArray(ints), ints.length, MemorySegment.ofArray(longs), longs.length);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot create native surface pass", error);
        }
    }

    private static int biomeId(Registry<Biome> registry, net.minecraft.resources.ResourceKey<Biome> key) {
        Biome biome = registry.getValue(key);
        return biome == null ? -1 : registry.getId(biome);
    }

    /** Every state id of the block, sorted (BlockState.is(block)). */
    private static int[] stateIds(Block block) {
        return block.getStateDefinition().getPossibleStates().stream().mapToInt(Block::getId).sorted().toArray();
    }

    /** Installs every modified section, both heightmaps and the fluid marks. */
    void install() {
        this.storage.install();
        CHUNKS.incrementAndGet();
    }

    @Override
    public void close() {
        try {
            RELEASE.invokeExact(this.handle);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot release native surface stage", error);
        } finally {
            this.storage.close();
            java.lang.ref.Reference.reachabilityFence(this.keep);
        }
    }
}
