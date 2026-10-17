package net.minecraft.world.level.chunk;

import net.minecraft.util.BitStorage;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** Ordinary bulk calls; the lease protects callback records during reentrant consumers. */
final class NativePaletteHistogram {
    private static final int WORK_SIZE = 65536 + 2 * 8192;
    private static final MethodHandle SCAN =
            NativeLibraryLoader.downcallHandle(
                    "mattmc_rust",
                    "mattmc_palette_histogram",
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT));
    private static final ThreadLocal<Counts> SCRATCH = ThreadLocal.withInitial(Counts::new);

    private NativePaletteHistogram() {}

    static final class Counts implements AutoCloseable {
        private final MemorySegment words = Arena.ofAuto().allocate(2048 * 8, 8);
        // Arena allocation is zero-initialized. Rust resets all touched dense counts.
        private final MemorySegment workspace = Arena.ofAuto().allocate(WORK_SIZE * 4L, 4);
        private final MemorySegment output = Arena.ofAuto().allocate(4096 * 8, 8);
        private final long[] records = new long[4096];
        private boolean inUse;
        int size;

        long entry(int index) {
            return records[index];
        }

        @Override
        public void close() {
            inUse = false;
        }
    }

    static Counts scan(NativeLiveBlockSection live) {
        var counts = SCRATCH.get();
        if (counts.inUse) return null;
        counts.inUse = true;
        try {
            counts.size = live.histogram(counts.workspace, WORK_SIZE, counts.output);
            if (counts.size < 1) { counts.close(); return null; }
            MemorySegment.copy(counts.output, 0, MemorySegment.ofArray(counts.records), 0, counts.size * 8L);
            return counts;
        } catch (RuntimeException | Error failure) { counts.close(); throw failure; }
    }

    static Counts scan(BitStorage storage) {
        if (storage == null
                || (storage.getClass() != SimpleBitStorage.class
                        && storage.getClass() != ZeroBitStorage.class)) return null;
        if (storage.getSize() != 4096 || storage.getBits() > 16) return null;
        var counts = SCRATCH.get();
        if (counts.inUse) return null;
        counts.inUse = true;
        try {
            var raw = storage.getRaw();
            MemorySegment.copy(MemorySegment.ofArray(raw), 0, counts.words, 0, raw.length * 8L);
            counts.size =
                    (int)
                            SCAN.invokeExact(
                                    counts.words,
                                    raw.length,
                                    storage.getBits(),
                                    counts.workspace,
                                    WORK_SIZE,
                                    counts.output,
                                    4096);
            if (counts.size < 1) {
                counts.close();
                return null;
            }
            MemorySegment.copy(
                    counts.output, 0, MemorySegment.ofArray(counts.records), 0, counts.size * 8L);
            return counts;
        } catch (RuntimeException | Error e) {
            counts.close();
            throw e;
        } catch (Throwable e) {
            counts.close();
            throw new IllegalStateException("Native palette histogram failed", e);
        }
    }
}
