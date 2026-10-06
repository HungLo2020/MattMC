package net.minecraft.world.level.levelgen;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.atomic.AtomicLong;
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
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle BEGIN = bind("begin", false, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    // Bounded per call like the surface step; borrows the frame and cache.
    static final MethodHandle RUN = bind("run", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle RELEASE = bind("release", false, FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaSurfaceStorage");
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

    final long handle;
    private final NativeProtoChunk storage;

    private NativeSurfaceChunk(long handle, NativeProtoChunk storage) {
        this.handle = handle;
        this.storage = storage;
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
        boolean[] steep = rule.steepSlots();
        it.unimi.dsi.fastutil.ints.IntArrayList ints = new it.unimi.dsi.fastutil.ints.IntArrayList();
        ints.addElements(0, new int[]{Block.getId(defaultBlock), rule.usesBiomes() ? 1 : 0, qx, qy, qz, sizeX, sizeY, sizeZ, steep.length});
        for (boolean slot : steep) ints.add(slot ? 1 : 0);
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
            handle = (long)CREATE.invokeExact(storage.handle, MemorySegment.ofArray(intArray), intArray.length, biomes == null ? 0L : biomes.biomeZoomSeed);
        } catch (Throwable error) {
            storage.close();
            throw new IllegalStateException("Cannot create native surface stage", error);
        }
        if (handle == 0) {
            storage.close();
            return null;
        }
        return new NativeSurfaceChunk(handle, storage);
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
        }
    }
}
