package net.sodium.client.render.chunk.translucent_sorting.trigger;

import net.minecraft.core.SectionPos;
import net.minecraft.util.NativeLibraryLoader;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.function.LongConsumer;

final class NativeDirectTriggers implements AutoCloseable {
    private static final int OK = 0;
    private static final int LAST_ERROR_BYTES = 4096;
    private static final Cleaner CLEANER = Cleaner.create();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_direct_triggers_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_direct_triggers_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle COUNT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_direct_triggers_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle STATS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_direct_triggers_stats",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle REMOVE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_direct_triggers_remove",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle INTEGRATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_direct_triggers_integrate",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.ADDRESS));
    private static final MethodHandle PROCESS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_direct_triggers_process",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle LAST_ERROR = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_direct_triggers_last_error",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));

    private final State state;
    private final Cleaner.Cleanable cleanable;

    private NativeDirectTriggers(long handle) {
        this.state = new State(handle);
        this.cleanable = CLEANER.register(this, this.state);
    }

    static NativeDirectTriggers create() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeCreate(handleSegment), "native direct trigger creation", "operation=create");
            long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native direct trigger creation returned a null handle");
            }

            return new NativeDirectTriggers(handle);
        }
    }

    int processTriggers(CameraMovement movement, LongConsumer triggeredSectionConsumer) {
        int capacity = this.getDirectTriggerCount();
        Scratch scratch = SCRATCH.get();
        scratch.ensureCapacity(capacity);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countSegment = arena.allocate(ValueLayout.JAVA_INT);
            long outputAddress = capacity == 0 ? 0L : MemoryUtil.memAddress(scratch.outputSections);

            long handle = this.state.getHandle();
            check(invokeProcess(handle,
                    movement.start().x(), movement.start().y(), movement.start().z(),
                    movement.end().x(), movement.end().y(), movement.end().z(),
                    outputAddress, capacity, countSegment), "native direct trigger processing",
                    "operation=process handle=" + handle
                            + " capacity=" + capacity
                            + " outputAddress=" + outputAddress
                            + " " + movementContext(movement));

            int count = countSegment.get(ValueLayout.JAVA_INT, 0);
            for (int index = 0; index < count; index++) {
                triggeredSectionConsumer.accept(scratch.outputSections.getLong(index * Long.BYTES));
            }

            return count;
        }
    }

    boolean integrateSection(SectionPos sectionPos, CameraMovement movement) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment triggeredSegment = arena.allocate(ValueLayout.JAVA_INT);

            long handle = this.state.getHandle();
            check(invokeIntegrate(handle, sectionPos.asLong(), sectionPos.getX(), sectionPos.getY(),
                    sectionPos.getZ(), movement.start().x(), movement.start().y(), movement.start().z(),
                    movement.end().x(), movement.end().y(), movement.end().z(), triggeredSegment),
                    "native direct trigger integration",
                    "operation=integrate handle=" + handle
                            + " sectionPos=" + sectionPos.asLong()
                            + " section=(" + sectionPos.getX() + "," + sectionPos.getY() + "," + sectionPos.getZ() + ")"
                            + " " + movementContext(movement));

            return triggeredSegment.get(ValueLayout.JAVA_INT, 0) != 0;
        }
    }

    void removeSection(long sectionPos) {
        long handle = this.state.getHandle();
        check(invokeRemove(handle, sectionPos), "native direct trigger removal",
                "operation=remove handle=" + handle + " sectionPos=" + sectionPos);
    }

    int getDirectTriggerCount() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countSegment = arena.allocate(ValueLayout.JAVA_INT);
            long handle = this.state.getHandle();
            check(invokeCount(handle, countSegment), "native direct trigger count query",
                    "operation=count handle=" + handle);
            return countSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    Stats statsSnapshot() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment statsSegment = arena.allocate(Stats.BYTE_SIZE, Long.BYTES);
            long handle = this.state.getHandle();
            check(invokeStats(handle, statsSegment), "native direct trigger stats query",
                    "operation=stats handle=" + handle);
            return Stats.read(statsSegment);
        }
    }

    @Override
    public void close() {
        this.cleanable.clean();
    }

    private static void check(int status, String operation) {
        check(status, operation, "");
    }

    private static void check(int status, String operation, String context) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status
                    + " (" + statusName(status) + ")"
                    + " context={" + context + " thread=" + Thread.currentThread().getName() + "}"
                    + " rust={" + lastNativeError() + "}");
        }
    }

    private static String movementContext(CameraMovement movement) {
        return "start=(" + movement.start().x() + "," + movement.start().y() + "," + movement.start().z() + ")"
                + " end=(" + movement.end().x() + "," + movement.end().y() + "," + movement.end().z() + ")";
    }

    private static String statusName(int status) {
        return switch (status) {
            case 0 -> "OK";
            case -1 -> "ERR_NULL_POINTER";
            case -2 -> "ERR_INVALID_ARGUMENT";
            case -3 -> "ERR_CAPACITY";
            default -> "ERR_UNKNOWN";
        };
    }

    private static String lastNativeError() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment output = arena.allocate(LAST_ERROR_BYTES);
            MemorySegment lengthSegment = arena.allocate(ValueLayout.JAVA_LONG);
            int status = invokeLastError(output, LAST_ERROR_BYTES, lengthSegment);
            if (status != OK) {
                return "last_error_query_status=" + statusName(status);
            }

            long length = lengthSegment.get(ValueLayout.JAVA_LONG, 0);
            int copyLength = (int) Math.min(Math.max(length, 0L), LAST_ERROR_BYTES);
            if (copyLength == 0) {
                return "none";
            }

            byte[] bytes = new byte[copyLength];
            output.asSlice(0, copyLength).asByteBuffer().get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static int invokeCreate(MemorySegment outputHandle) {
        try {
            return (int) CREATE.invokeExact(outputHandle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust direct trigger creation downcall failed", throwable);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust direct trigger destroy downcall failed", throwable);
        }
    }

    private static int invokeCount(long handle, MemorySegment outputCount) {
        try {
            return (int) COUNT.invokeExact(handle, outputCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust direct trigger count downcall failed", throwable);
        }
    }

    private static int invokeStats(long handle, MemorySegment outputStats) {
        try {
            return (int) STATS.invokeExact(handle, outputStats);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust direct trigger stats downcall failed", throwable);
        }
    }

    private static int invokeRemove(long handle, long sectionPos) {
        try {
            return (int) REMOVE.invokeExact(handle, sectionPos);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust direct trigger removal downcall failed", throwable);
        }
    }

    private static int invokeIntegrate(long handle, long sectionPos, int sectionX, int sectionY, int sectionZ,
            double startX, double startY, double startZ, double endX, double endY, double endZ,
            MemorySegment outputTriggered) {
        try {
            return (int) INTEGRATE.invokeExact(handle, sectionPos, sectionX, sectionY, sectionZ,
                    startX, startY, startZ, endX, endY, endZ, outputTriggered);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust direct trigger integration downcall failed", throwable);
        }
    }

    private static int invokeProcess(long handle, double startX, double startY, double startZ, double endX,
            double endY, double endZ, long outputAddress, int outputCapacity, MemorySegment outputCount) {
        try {
            return (int) PROCESS.invokeExact(handle, startX, startY, startZ, endX, endY, endZ, outputAddress,
                    outputCapacity, outputCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust direct trigger processing downcall failed", throwable);
        }
    }

    private static int invokeLastError(MemorySegment output, long outputCapacity, MemorySegment outputLength) {
        try {
            return (int) LAST_ERROR.invokeExact(output, outputCapacity, outputLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust direct trigger last-error downcall failed", throwable);
        }
    }

    private static final class Scratch {
        private ByteBuffer outputSections = allocate(0);

        void ensureCapacity(int sectionCount) {
            int requiredBytes = sectionCount * Long.BYTES;
            if (this.outputSections.capacity() >= requiredBytes) {
                return;
            }

            this.outputSections = allocate(requiredBytes);
        }

        private static ByteBuffer allocate(int bytes) {
            return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        }
    }

    private static final class State implements Runnable {
        private long handle;

        private State(long handle) {
            this.handle = handle;
        }

        private synchronized long getHandle() {
            if (this.handle == 0) {
                throw new IllegalStateException("Native direct triggers have been closed");
            }
            return this.handle;
        }

        @Override
        public synchronized void run() {
            long handle = this.handle;
            if (handle == 0) {
                return;
            }

            check(invokeDestroy(handle), "native direct trigger destruction", "operation=destroy handle=" + handle);
            this.handle = 0;
        }
    }

    record Stats(long processCalls,
                 long integrateCalls,
                 long catchupIntegrations,
                 long anglePathIntegrations,
                 long distancePathIntegrations,
                 long invalidAngleInputFallbacks,
                 double totalMovementDistance,
                 double maxMovementDistance) {
        static final long BYTE_SIZE = 64;

        static Stats read(MemorySegment segment) {
            return new Stats(
                    segment.get(ValueLayout.JAVA_LONG, 0),
                    segment.get(ValueLayout.JAVA_LONG, 8),
                    segment.get(ValueLayout.JAVA_LONG, 16),
                    segment.get(ValueLayout.JAVA_LONG, 24),
                    segment.get(ValueLayout.JAVA_LONG, 32),
                    segment.get(ValueLayout.JAVA_LONG, 40),
                    segment.get(ValueLayout.JAVA_DOUBLE, 48),
                    segment.get(ValueLayout.JAVA_DOUBLE, 56));
        }
    }
}
