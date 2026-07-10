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
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.LongConsumer;

final class NativeGfniTriggers implements AutoCloseable {
    private static final int OK = 0;
    private static final int ERR_CAPACITY = -3;
    private static final int OUTPUT_STATE_VALUES = 2;
    private static final Cleaner CLEANER = Cleaner.create();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_gfni_triggers_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_gfni_triggers_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle COUNTS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_gfni_triggers_counts",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle REMOVE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_gfni_triggers_remove",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle INTEGRATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_gfni_triggers_integrate",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle PROCESS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_gfni_triggers_process",
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
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle CATCHUP = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_gfni_triggers_catchup",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));

    private final State state;
    private final Cleaner.Cleanable cleanable;

    private NativeGfniTriggers(long handle) {
        this.state = new State(handle);
        this.cleanable = CLEANER.register(this, this.state);
    }

    static NativeGfniTriggers create() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeCreate(handleSegment), "native GFNI trigger creation");
            long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native GFNI trigger creation returned a null handle");
            }

            return new NativeGfniTriggers(handle);
        }
    }

    int processTriggers(CameraMovement movement, LongConsumer triggeredSectionConsumer) {
        return this.processWithRetry(false, 0L, movement, triggeredSectionConsumer).uniqueNormalCount();
    }

    void processCatchup(long sectionPos, CameraMovement movement, LongConsumer triggeredSectionConsumer) {
        this.processWithRetry(true, sectionPos, movement, triggeredSectionConsumer);
    }

    void integrateSection(SectionPos sectionPos, GeometryPlanes geometryPlanes) {
        ArrayList<NormalPlanes> normalPlanes = collectNormalPlanes(geometryPlanes);

        if (normalPlanes.isEmpty()) {
            check(invokeIntegrate(this.state.getHandle(), sectionPos.asLong(), MemorySegment.NULL,
                    MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL,
                    MemorySegment.NULL, MemorySegment.NULL, 0, 0), "native GFNI trigger integration");
            return;
        }

        int totalDistanceCount = 0;
        for (NormalPlanes planes : normalPlanes) {
            ensurePrepared(planes);
            totalDistanceCount += planes.relativeDistances.length;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment normals = arena.allocate(ValueLayout.JAVA_FLOAT, normalPlanes.size() * 3L);
            MemorySegment baseDistances = arena.allocate(ValueLayout.JAVA_DOUBLE, normalPlanes.size());
            MemorySegment ranges = arena.allocate(ValueLayout.JAVA_DOUBLE, normalPlanes.size() * 2L);
            MemorySegment hashes = arena.allocate(ValueLayout.JAVA_LONG, normalPlanes.size());
            MemorySegment distanceOffsets = arena.allocate(ValueLayout.JAVA_INT, normalPlanes.size());
            MemorySegment distanceCounts = arena.allocate(ValueLayout.JAVA_INT, normalPlanes.size());
            MemorySegment distances = arena.allocate(ValueLayout.JAVA_FLOAT, totalDistanceCount);

            int distanceOffset = 0;
            for (int index = 0; index < normalPlanes.size(); index++) {
                NormalPlanes planes = normalPlanes.get(index);

                normals.setAtIndex(ValueLayout.JAVA_FLOAT, index * 3L, planes.normal.x());
                normals.setAtIndex(ValueLayout.JAVA_FLOAT, index * 3L + 1, planes.normal.y());
                normals.setAtIndex(ValueLayout.JAVA_FLOAT, index * 3L + 2, planes.normal.z());
                baseDistances.setAtIndex(ValueLayout.JAVA_DOUBLE, index, planes.baseDistance);
                ranges.setAtIndex(ValueLayout.JAVA_DOUBLE, index * 2L, planes.relativeDistances[0] + planes.baseDistance);
                ranges.setAtIndex(ValueLayout.JAVA_DOUBLE, index * 2L + 1,
                        planes.relativeDistances[planes.relativeDistances.length - 1] + planes.baseDistance);
                hashes.setAtIndex(ValueLayout.JAVA_LONG, index, planes.relDistanceHash);
                distanceOffsets.setAtIndex(ValueLayout.JAVA_INT, index, distanceOffset);
                distanceCounts.setAtIndex(ValueLayout.JAVA_INT, index, planes.relativeDistances.length);

                for (int distanceIndex = 0; distanceIndex < planes.relativeDistances.length; distanceIndex++) {
                    distances.setAtIndex(ValueLayout.JAVA_FLOAT, distanceOffset + distanceIndex,
                            planes.relativeDistances[distanceIndex]);
                }
                distanceOffset += planes.relativeDistances.length;
            }

            check(invokeIntegrate(this.state.getHandle(), sectionPos.asLong(), normals, baseDistances, ranges,
                    hashes, distanceOffsets, distanceCounts, distances, normalPlanes.size(), totalDistanceCount),
                    "native GFNI trigger integration");
        }
    }

    void removeSection(long sectionPos) {
        check(invokeRemove(this.state.getHandle(), sectionPos), "native GFNI trigger removal");
    }

    int getUniqueNormalCount() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment counts = arena.allocate(ValueLayout.JAVA_INT, OUTPUT_STATE_VALUES);
            check(invokeCounts(this.state.getHandle(), counts, OUTPUT_STATE_VALUES),
                    "native GFNI trigger count query");
            return counts.getAtIndex(ValueLayout.JAVA_INT, 0);
        }
    }

    @Override
    public void close() {
        this.cleanable.clean();
    }

    private ProcessResult processWithRetry(boolean catchup, long sectionPos, CameraMovement movement,
            LongConsumer triggeredSectionConsumer) {
        Scratch scratch = SCRATCH.get();

        while (true) {
            scratch.ensureSectionCapacity(Math.max(16, scratch.sectionCapacity()));
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment outputState = arena.allocate(ValueLayout.JAVA_INT, OUTPUT_STATE_VALUES);
                long outputAddress = scratch.sectionCapacity() == 0 ? 0L : MemoryUtil.memAddress(scratch.outputSections);
                int status = catchup
                        ? invokeCatchup(this.state.getHandle(), sectionPos,
                                movement.start().x(), movement.start().y(), movement.start().z(),
                                movement.end().x(), movement.end().y(), movement.end().z(),
                                outputAddress, scratch.sectionCapacity(), outputState, OUTPUT_STATE_VALUES)
                        : invokeProcess(this.state.getHandle(),
                                movement.start().x(), movement.start().y(), movement.start().z(),
                                movement.end().x(), movement.end().y(), movement.end().z(),
                                outputAddress, scratch.sectionCapacity(), outputState, OUTPUT_STATE_VALUES);

                if (status == ERR_CAPACITY) {
                    scratch.ensureSectionCapacity(Math.max(16, scratch.sectionCapacity() * 2));
                    continue;
                }
                check(status, catchup ? "native GFNI catchup processing" : "native GFNI trigger processing");

                int sectionCount = outputState.getAtIndex(ValueLayout.JAVA_INT, 0);
                int uniqueNormalCount = outputState.getAtIndex(ValueLayout.JAVA_INT, 1);
                for (int index = 0; index < sectionCount; index++) {
                    triggeredSectionConsumer.accept(scratch.outputSections.getLong(index * Long.BYTES));
                }

                return new ProcessResult(sectionCount, uniqueNormalCount);
            }
        }
    }

    private static ArrayList<NormalPlanes> collectNormalPlanes(GeometryPlanes geometryPlanes) {
        ArrayList<NormalPlanes> normalPlanes = new ArrayList<>();

        NormalPlanes[] aligned = geometryPlanes.getAligned();
        if (aligned != null) {
            for (NormalPlanes planes : aligned) {
                if (planes != null) {
                    normalPlanes.add(planes);
                }
            }
        }

        Collection<NormalPlanes> unaligned = geometryPlanes.getUnaligned();
        if (unaligned != null) {
            normalPlanes.addAll(unaligned);
        }

        return normalPlanes;
    }

    private static void ensurePrepared(NormalPlanes planes) {
        if (planes.relativeDistances == null) {
            planes.prepareIntegration();
        }
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeCreate(MemorySegment outputHandle) {
        try {
            return (int) CREATE.invokeExact(outputHandle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust GFNI trigger creation downcall failed", throwable);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust GFNI trigger destroy downcall failed", throwable);
        }
    }

    private static int invokeCounts(long handle, MemorySegment outputCounts, int outputCountLen) {
        try {
            return (int) COUNTS.invokeExact(handle, outputCounts, outputCountLen);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust GFNI trigger count downcall failed", throwable);
        }
    }

    private static int invokeRemove(long handle, long sectionPos) {
        try {
            return (int) REMOVE.invokeExact(handle, sectionPos);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust GFNI trigger removal downcall failed", throwable);
        }
    }

    private static int invokeIntegrate(long handle, long sectionPos, MemorySegment normals,
            MemorySegment baseDistances, MemorySegment ranges, MemorySegment hashes, MemorySegment distanceOffsets,
            MemorySegment distanceCounts, MemorySegment distances, int groupCount, int distanceCount) {
        try {
            return (int) INTEGRATE.invokeExact(handle, sectionPos, normals, baseDistances, ranges, hashes,
                    distanceOffsets, distanceCounts, distances, groupCount, distanceCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust GFNI trigger integration downcall failed", throwable);
        }
    }

    private static int invokeProcess(long handle, double startX, double startY, double startZ, double endX,
            double endY, double endZ, long outputAddress, int outputCapacity, MemorySegment outputState,
            int outputStateLen) {
        try {
            return (int) PROCESS.invokeExact(handle, startX, startY, startZ, endX, endY, endZ, outputAddress,
                    outputCapacity, outputState, outputStateLen);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust GFNI trigger processing downcall failed", throwable);
        }
    }

    private static int invokeCatchup(long handle, long sectionPos, double startX, double startY, double startZ,
            double endX, double endY, double endZ, long outputAddress, int outputCapacity,
            MemorySegment outputState, int outputStateLen) {
        try {
            return (int) CATCHUP.invokeExact(handle, sectionPos, startX, startY, startZ, endX, endY, endZ,
                    outputAddress, outputCapacity, outputState, outputStateLen);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust GFNI trigger catchup downcall failed", throwable);
        }
    }

    private record ProcessResult(int sectionCount, int uniqueNormalCount) {
    }

    private static final class Scratch {
        private ByteBuffer outputSections = allocate(16 * Long.BYTES);

        int sectionCapacity() {
            return this.outputSections.capacity() / Long.BYTES;
        }

        void ensureSectionCapacity(int sectionCount) {
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
                throw new IllegalStateException("Native GFNI triggers have been closed");
            }
            return this.handle;
        }

        @Override
        public synchronized void run() {
            long handle = this.handle;
            if (handle == 0) {
                return;
            }

            check(invokeDestroy(handle), "native GFNI trigger destruction");
            this.handle = 0;
        }
    }
}
