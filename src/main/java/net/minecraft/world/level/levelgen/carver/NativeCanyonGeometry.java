package net.minecraft.world.level.levelgen.carver;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Pure built-in canyon predicates; the caller retains live masks and callbacks. */
final class NativeCanyonGeometry {
    static final int MIN_VOLUME = 512;
    static final int MIN_HEIGHT = 16;
    private static final FunctionDescriptor DESCRIPTOR = FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
    private static final MethodHandle EVALUATE = NativeLibraryLoader.downcallHandle(
        "mattmc_rust", "mattmc_canyon_candidates", DESCRIPTOR);
    // Rust enforces at most 64 Y samples and 8,192 decisions: no allocation,
    // blocking, callbacks or pointer retention while Java arrays are borrowed.
    private static final MethodHandle SMALL = NativeLibraryLoader.downcallHandle(
        "mattmc_rust", "mattmc_canyon_candidates_small", DESCRIPTOR, Linker.Option.critical(true));
    private static final MethodHandle INITIALIZE = NativeLibraryLoader.downcallHandle(
        "mattmc_rust", "mattmc_canyon_initialize", FunctionDescriptor.of(ValueLayout.JAVA_INT));
    static {
        try { int dispatch = (int) INITIALIZE.invokeExact(); }
        catch (Throwable e) { throw new ExceptionInInitializerError(e); }
    }
    private static final ThreadLocal<Columns> SCRATCH = ThreadLocal.withInitial(Columns::new);

    private NativeCanyonGeometry() {}

    private static final class Shape implements WorldCarver.CarveSkipChecker {
        final int widthCount;
        final MemorySegment widthSegment;
        final WorldCarver.CarveSkipChecker original;

        Shape(float[] widths, WorldCarver.CarveSkipChecker original) {
            this.widthCount = widths == null ? 0 : widths.length;
            this.widthSegment = widths == null ? MemorySegment.NULL : MemorySegment.ofArray(widths);
            this.original = original;
        }

        @Override
        public boolean shouldSkip(CarvingContext context, double x, double y, double z, int blockY) {
            return original.shouldSkip(context, x, y, z, blockY);
        }
    }

    static boolean isCanyon(WorldCarver.CarveSkipChecker checker) { return checker instanceof Shape; }

    // Canyon factors are generated once and privately owned by doCarve().
    // The segment retains that Java array; ordinary calls copy it inside prepare.
    static WorldCarver.CarveSkipChecker canyon(float[] widths, WorldCarver.CarveSkipChecker original) {
        if (widths.length == 0 || widths.length > 2048) return original;
        for (float factor : widths) {
            if (!Float.isFinite(factor) || factor < 0) return original;
        }
        return new Shape(widths, original);
    }

    static WorldCarver.CarveSkipChecker original(WorldCarver.CarveSkipChecker checker) {
        return checker instanceof Shape profile ? profile.original : checker;
    }

    static final class Columns implements AutoCloseable {
        private final Arena metadata = Arena.ofAuto();
        private final MemorySegment frame = metadata.allocate(36, 4);
        private final MemorySegment shape = metadata.allocate(40, 8);
        private final MemorySegment widths = metadata.allocate(2048 * 4, 4);
        private final int[] frameValues = new int[9];
        private final double[] shapeValues = new double[5];
        private final MemorySegment heapFrame = MemorySegment.ofArray(frameValues);
        private final MemorySegment heapShape = MemorySegment.ofArray(shapeValues);
        private MemorySegment heapOutput;
        private Arena arena;
        private MemorySegment output;
        private long[] values = new long[0];
        private int words;
        private boolean inUse;

        long word(int column, int index) { return values[column * words + index]; }
        int words() { return words; }

        private void ensure(int count) {
            if (values.length >= count) return;
            // Publish together: a failed allocation must preserve the previous buffers.
            var nextValues = new long[count];
            var nextHeap = MemorySegment.ofArray(nextValues);
            var nextArena = Arena.ofAuto();
            var nextOutput = nextArena.allocate(count * 8L, 8);
            values = nextValues;
            heapOutput = nextHeap;
            arena = nextArena;
            output = nextOutput;
        }

        @Override
        public void close() { inUse = false; }
    }

    static Columns prepare(CarvingContext context, WorldCarver.CarveSkipChecker checker,
        int baseX, int baseZ, int minX, int maxX, int lowerY, int upperY, int minZ, int maxZ,
        double centerX, double centerY, double centerZ, double horizontal, double vertical) {
        long height = (long) upperY - lowerY;
        long volume = (maxX - minX + 1L) * (maxZ - minZ + 1L) * height;
        if (height < MIN_HEIGHT || height > 2048 || minX < 0 || maxX > 15 || minZ < 0 || maxZ > 15
            || maxX < minX || maxZ < minZ || volume < MIN_VOLUME
            || context.getClass() != CarvingContext.class || !(checker instanceof Shape profile)
            || !Double.isFinite(centerX) || !Double.isFinite(centerY) || !Double.isFinite(centerZ)
            || !Double.isFinite(horizontal) || horizontal <= 0 || !Double.isFinite(vertical) || vertical <= 0) return null;
        int minY = context.getMinGenY();
        if (((long) lowerY - minY < 0 || (long) upperY - minY - 1 >= profile.widthCount)) return null;
        var columns = SCRATCH.get();
        if (columns.inUse) return null;
        columns.inUse = true;
        try {
            int words = (int) ((height + 63) / 64);
            int count = (maxX - minX + 1) * (maxZ - minZ + 1) * words;
            columns.ensure(count);
            columns.words = words;
            var frame = columns.frameValues;
            frame[0] = baseX; frame[1] = baseZ; frame[2] = minX; frame[3] = maxX;
            frame[4] = lowerY; frame[5] = upperY; frame[6] = minZ; frame[7] = maxZ;
            frame[8] = minY;
            var shape = columns.shapeValues;
            shape[0] = centerX; shape[1] = centerY; shape[2] = centerZ;
            shape[3] = horizontal; shape[4] = vertical;
            int status;
            if (height <= 64 && volume <= 8192) {
                status = (int) SMALL.invokeExact(columns.heapFrame, 9, columns.heapShape, 5,
                    profile.widthSegment, profile.widthCount, columns.heapOutput, count);
            } else {
                frame[8] = lowerY; // Rebase the copied factor window.
                MemorySegment.copy(columns.heapFrame, 0, columns.frame, 0, 36);
                MemorySegment.copy(columns.heapShape, 0, columns.shape, 0, 40);
                MemorySegment.copy(profile.widthSegment, ((long) lowerY - minY) * 4L, columns.widths, 0, height * 4L);
                int rowCount = (int) height;
                status = (int) EVALUATE.invokeExact(columns.frame, 9, columns.shape, 5,
                    columns.widths, rowCount, columns.output, count);
                if (status == 0) {
                    MemorySegment.copy(columns.output, 0, columns.heapOutput, 0, count * 8L);
                }
            }
            if (status != 0) throw new IllegalStateException("Invalid native carver geometry: " + status);
            return columns;
        } catch (RuntimeException | Error e) {
            columns.close();
            throw e;
        } catch (Throwable e) {
            columns.close();
            throw new IllegalStateException("Native carver geometry failed", e);
        }
    }
}
