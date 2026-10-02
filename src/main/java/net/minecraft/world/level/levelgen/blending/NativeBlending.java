package net.minecraft.world.level.levelgen.blending;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.core.QuartPos;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.ChunkPos;

/** Fresh height samples per grid; no cached terrain, callbacks or borrowed heap. */
final class NativeBlending {
    static final int MIN_SAMPLES = 32;
    static final int MIN_MISSING = 16;
    private static final MethodHandle FILL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_blending_heights",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private NativeBlending() {}
    private static final class Scratch implements BlendingData.HeightConsumer {
        final Arena arena = Arena.ofAuto();
        final int[] coordinates = new int[8192], queries = new int[578];
        final double[] heights = new double[4096], direct = new double[289], results = new double[578];
        final MemorySegment nativeCoordinates = arena.allocate(8192 * 4, 4);
        final MemorySegment nativeHeights = arena.allocate(4096 * 8, 8);
        final MemorySegment nativeQueries = arena.allocate(578 * 4, 4);
        final MemorySegment nativeDirect = arena.allocate(289 * 8, 8);
        final MemorySegment nativeResults = arena.allocate(578 * 8, 8);
        boolean inUse, invalid;
        int count;
        @Override public void consume(int x, int z, double height) {
            if (count == heights.length || !Double.isFinite(height)) { invalid = true; return; }
            coordinates[count * 2] = x; coordinates[count * 2 + 1] = z;
            heights[count++] = height;
        }
    }

    /** false retains the literal streaming caller for unsupported/custom inputs. */
    static boolean fill(Blender blender, Long2ObjectOpenHashMap<BlendingData> data,
        int firstX, int firstZ, int size, double[] alpha, double[] offset) {
        if (blender.getClass() != Blender.class || size < 5 || size > 17
            || data.getClass() != Long2ObjectOpenHashMap.class || data.isEmpty() || data.size() > 256) return false;
        for (var value : data.values()) if (value == null || value.getClass() != BlendingData.class || !value.hasNativeHeightLayout()) return false;
        var s = SCRATCH.get();
        if (s.inUse) return false;
        s.inUse = true;
        try {
            int missing = 0;
            // Original X outer / Z inner traversal and wrapped toBlock/fromBlock.
            for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
                int index = x + z * size;
                int qx = QuartPos.fromBlock(QuartPos.toBlock(firstX + x));
                int qz = QuartPos.fromBlock(QuartPos.toBlock(firstZ + z));
                s.queries[index * 2] = qx; s.queries[index * 2 + 1] = qz;
                double direct = blender.heightForNativeGrid(qx, qz);
                if (!Double.isFinite(direct)) return false;
                s.direct[index] = direct;
                if (direct == Double.MAX_VALUE) missing++;
            }
            if (missing < MIN_MISSING) return false;
            s.count = 0; s.invalid = false;
            // Use the exact fastutil forEach visitation order of the original.
            data.forEach((key, value) -> value.iterateHeights(
                QuartPos.fromSection(ChunkPos.getX(key)), QuartPos.fromSection(ChunkPos.getZ(key)), s));
            if (s.invalid || s.count < MIN_SAMPLES) return false;
            int queries = size * size;
            MemorySegment.copy(MemorySegment.ofArray(s.coordinates), 0, s.nativeCoordinates, 0, s.count * 8L);
            MemorySegment.copy(MemorySegment.ofArray(s.heights), 0, s.nativeHeights, 0, s.count * 8L);
            MemorySegment.copy(MemorySegment.ofArray(s.queries), 0, s.nativeQueries, 0, queries * 8L);
            MemorySegment.copy(MemorySegment.ofArray(s.direct), 0, s.nativeDirect, 0, queries * 8L);
            int status = (int)FILL.invokeExact(s.nativeCoordinates, s.nativeHeights, s.count,
                s.nativeQueries, s.nativeDirect, queries, s.nativeResults, queries * 2);
            if (status == 1) return false;
            if (status != 0) throw new IllegalStateException("Invalid native height blending: " + status);
            MemorySegment.copy(s.nativeResults, 0, MemorySegment.ofArray(s.results), 0, queries * 16L);
            // Publish only a complete successful grid. Caller arrays keep identity.
            for (int q = 0; q < queries; q++) { alpha[q] = s.results[q * 2]; offset[q] = s.results[q * 2 + 1]; }
            return true;
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Native height blending failed", e); }
        finally { s.inUse = false; }
    }
}
