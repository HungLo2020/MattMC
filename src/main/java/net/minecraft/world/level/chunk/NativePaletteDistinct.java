package net.minecraft.world.level.chunk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.BitStorage;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.SimpleBitStorage;

/** Bulk ordered-ID scan; Java retains palette resolution and consumer behavior. */
final class NativePaletteDistinct {
    private static final FunctionDescriptor DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
    private static final MethodHandle SCAN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_palette_distinct", DESCRIPTOR);
    // Fixed 64 fields, no allocation/blocking/callbacks: safe bounded heap access.
    // Larger sections use the ordinary copied-buffer call and permit GC.
    private static final MethodHandle BIOMES = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_palette_distinct_biomes",
        DESCRIPTOR, Linker.Option.critical(true));
    private static final ThreadLocal<Ids> SCRATCH = ThreadLocal.withInitial(Ids::new);
    private NativePaletteDistinct() {}

    static final class Ids implements AutoCloseable {
        private final Arena arena = Arena.ofAuto();
        private final MemorySegment words = arena.allocate(1024 * 8, 8);
        // Arena allocations start zero. Rust clears every touched workspace word.
        private final MemorySegment seen = arena.allocate(1024 * 8, 8);
        private final MemorySegment output = arena.allocate(4096 * 4, 4);
        private final int[] records = new int[4096];
        private final MemorySegment recordSegment = MemorySegment.ofArray(records);
        private boolean inUse;
        int size;
        int entry(int index) { return records[index]; }
        @Override public void close() { inUse = false; }
    }

    static Ids scan(BitStorage storage) {
        if (storage == null || storage.getClass() != SimpleBitStorage.class) return null;
        int size = storage.getSize(), bits = storage.getBits();
        if ((size != 64 && size != 4096) || bits > 16) return null;
        var ids = SCRATCH.get();
        if (ids.inUse) return null;
        ids.inUse = true;
        try {
            long[] raw = storage.getRaw();
            if (size == 64) {
                ids.size = (int)BIOMES.invokeExact(MemorySegment.ofArray(raw), raw.length, bits, size,
                    ids.seen, 1024, ids.recordSegment, size);
            } else {
                MemorySegment.copy(MemorySegment.ofArray(raw), 0, ids.words, 0, raw.length * 8L);
                ids.size = (int)SCAN.invokeExact(ids.words, raw.length, bits, size, ids.seen, 1024, ids.output, size);
                if (ids.size > 0) MemorySegment.copy(ids.output, 0, ids.recordSegment, 0, ids.size * 4L);
            }
            if (ids.size < 1) throw new IllegalStateException("Invalid native palette distinct scan: " + ids.size);
            return ids;
        } catch (RuntimeException | Error e) { ids.close(); throw e; }
        catch (Throwable e) { ids.close(); throw new IllegalStateException("Native palette distinct scan failed", e); }
    }
}
