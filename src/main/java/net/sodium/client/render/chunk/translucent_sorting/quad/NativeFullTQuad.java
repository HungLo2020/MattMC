package net.sodium.client.render.chunk.translucent_sorting.quad;

import net.minecraft.util.NativeLibraryLoader;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;

public final class NativeFullTQuad extends RegularTQuad {
    private static final int OK = 0;
    private static final int SORT_FAILED = 1;
    private static final int NO_WRITE = -1;
    private static final int STATE_STRIDE = 128;
    private static final int STATE_POSITION_FLOATS = 12;
    private static final int STATE_EXTENT_FLOATS = 6;
    private static final int OFFSET_POSITIONS = 0;
    private static final int OFFSET_EXTENTS = OFFSET_POSITIONS + STATE_POSITION_FLOATS * Float.BYTES;
    private static final int OFFSET_CENTER = OFFSET_EXTENTS + STATE_EXTENT_FLOATS * Float.BYTES;
    private static final int OFFSET_ACCURATE_NORMAL = OFFSET_CENTER + 3 * Float.BYTES;
    private static final int OFFSET_ACCURATE_DOT_PRODUCT = OFFSET_ACCURATE_NORMAL + 3 * Float.BYTES;
    private static final int OFFSET_QUANTIZED_DOT_PRODUCT = OFFSET_ACCURATE_DOT_PRODUCT + Float.BYTES;
    private static final int OFFSET_FACING = OFFSET_QUANTIZED_DOT_PRODUCT + Float.BYTES;
    private static final int OFFSET_PACKED_NORMAL = OFFSET_FACING + Integer.BYTES;
    private static final int OFFSET_SAME_VERTEX_MAP = OFFSET_PACKED_NORMAL + Integer.BYTES;
    private static final int OFFSET_NORMAL_IS_VERY_ACCURATE = OFFSET_SAME_VERTEX_MAP + Integer.BYTES;
    private static final int OFFSET_HAS_UPDATED_VERTICES = OFFSET_NORMAL_IS_VERY_ACCURATE + Integer.BYTES;
    private static final int OFFSET_WRITE_TO_INDEX = OFFSET_HAS_UPDATED_VERTICES + Integer.BYTES;
    private static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_full_quad_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_full_quad_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle COPY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_full_quad_copy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
    private static final MethodHandle GET_VERY_ACCURATE_NORMAL = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_full_quad_get_very_accurate_normal",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle CLASSIFY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_full_quad_classify",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle TRIGGER_UPDATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_full_quad_trigger_update",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle SET_WRITE_TO_INDEX = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_full_quad_set_write_to_index",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_TO_NATIVE_BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_full_quad_write_to_native_buffer",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle SPLIT_EVEN = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_full_quad_split_even",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
    private static final MethodHandle SPLIT_ODD = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_full_quad_split_odd",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
    private static final MethodHandle SPLIT_TRIANGLE_CORNER = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_full_quad_split_triangle_corner",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
    private static final MethodHandle SPLIT_TRIANGLE_VERTEX = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_full_quad_split_triangle_vertex",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));

    private final State state;
    private final Cleaner.Cleanable cleanable;

    private int sameVertexMap;
    private boolean normalIsVeryAccurate;
    private boolean hasUpdatedVertices;
    private int writeToIndex = NO_WRITE;

    private NativeFullTQuad(long handle, MemorySegment stateSegment, ModelQuadFacing fallbackFacing, int fallbackPackedNormal) {
        super(fallbackFacing, fallbackPackedNormal);
        this.state = new State(handle);
        this.cleanable = CLEANER.register(this, this.state);
        this.applyState(stateSegment);
    }

    public static NativeFullTQuad fromNativeQuad(long nativeQuadAddress, ModelQuadFacing facing, int packedNormal) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment stateSegment = arena.allocate(STATE_STRIDE, Integer.BYTES);

            int status = invokeCreate(nativeQuadAddress, facing.ordinal(), packedNormal, handleSegment, stateSegment);
            if (status == SORT_FAILED) {
                return null;
            }
            check(status, "native full translucent quad creation");

            long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native full translucent quad creation returned a null handle");
            }
            return new NativeFullTQuad(handle, stateSegment, facing, packedNormal);
        }
    }

    public int[] classifyAgainst(Vector3fc splitPlane, float splitDistance) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment mapsSegment = arena.allocate(ValueLayout.JAVA_INT, 2);
            check(invokeClassify(this.getHandle(), splitPlane.x(), splitPlane.y(), splitPlane.z(), splitDistance,
                    mapsSegment), "native full translucent quad classification");
            return new int[] {
                    mapsSegment.getAtIndex(ValueLayout.JAVA_INT, 0),
                    mapsSegment.getAtIndex(ValueLayout.JAVA_INT, 1)
            };
        }
    }

    public boolean isInvalid() {
        return isInvalid(this.sameVertexMap);
    }

    public int getUniqueVertexMap() {
        return (~this.sameVertexMap) & 0b1111;
    }

    public int getSameVertexMap() {
        return this.sameVertexMap;
    }

    public boolean triggerAndSetUpdatedVertices() {
        int result = invokeTriggerUpdate(this.getHandle());
        if (result < OK) {
            check(result, "native full translucent quad update trigger");
        }

        boolean triggered = result != 0;
        if (triggered) {
            this.hasUpdatedVertices = true;
        }
        return triggered;
    }

    public void setWriteToIndex(int writeToIndex) {
        check(invokeSetWriteToIndex(this.getHandle(), writeToIndex), "native full translucent quad write index update");
        this.writeToIndex = writeToIndex;
    }

    public void setNoWrite() {
        this.setWriteToIndex(NO_WRITE);
    }

    public int getWriteToIndex() {
        return this.writeToIndex;
    }

    @Override
    public float[] getVertexPositions() {
        return this.vertexPositions;
    }

    public Vector3fc getVeryAccurateNormal() {
        if (this.facing.isAligned()) {
            return this.facing.getAlignedNormal();
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment stateSegment = arena.allocate(STATE_STRIDE, Integer.BYTES);
            check(invokeGetVeryAccurateNormal(this.getHandle(), stateSegment),
                    "native full translucent quad accurate normal calculation");
            this.applyState(stateSegment);
        }
        return this.accurateNormal;
    }

    public void writeToNativeBuffer(long nativeQuadAddress) {
        check(invokeWriteToNativeBuffer(this.getHandle(), nativeQuadAddress, DefaultMaterials.TRANSLUCENT.bits()),
                "native full translucent quad buffer write");
    }

    public long nativeHandle() {
        return this.getHandle();
    }

    public void close() {
        this.cleanable.clean();
    }

    private void applyState(MemorySegment stateSegment) {
        this.vertexPositions = new float[STATE_POSITION_FLOATS];
        for (int index = 0; index < STATE_POSITION_FLOATS; index++) {
            this.vertexPositions[index] = stateSegment.get(ValueLayout.JAVA_FLOAT,
                    OFFSET_POSITIONS + (long) index * Float.BYTES);
        }

        this.extents = new float[STATE_EXTENT_FLOATS];
        for (int index = 0; index < STATE_EXTENT_FLOATS; index++) {
            this.extents[index] = stateSegment.get(ValueLayout.JAVA_FLOAT,
                    OFFSET_EXTENTS + (long) index * Float.BYTES);
        }

        this.center = new Vector3f(
                stateSegment.get(ValueLayout.JAVA_FLOAT, OFFSET_CENTER),
                stateSegment.get(ValueLayout.JAVA_FLOAT, OFFSET_CENTER + Float.BYTES),
                stateSegment.get(ValueLayout.JAVA_FLOAT, OFFSET_CENTER + 2L * Float.BYTES));

        this.accurateNormal = new Vector3f(
                stateSegment.get(ValueLayout.JAVA_FLOAT, OFFSET_ACCURATE_NORMAL),
                stateSegment.get(ValueLayout.JAVA_FLOAT, OFFSET_ACCURATE_NORMAL + Float.BYTES),
                stateSegment.get(ValueLayout.JAVA_FLOAT, OFFSET_ACCURATE_NORMAL + 2L * Float.BYTES));
        this.accurateDotProduct = stateSegment.get(ValueLayout.JAVA_FLOAT, OFFSET_ACCURATE_DOT_PRODUCT);
        this.quantizedDotProduct = stateSegment.get(ValueLayout.JAVA_FLOAT, OFFSET_QUANTIZED_DOT_PRODUCT);
        this.facing = ModelQuadFacing.VALUES[stateSegment.get(ValueLayout.JAVA_INT, OFFSET_FACING)];
        this.sameVertexMap = stateSegment.get(ValueLayout.JAVA_INT, OFFSET_SAME_VERTEX_MAP);
        this.normalIsVeryAccurate = stateSegment.get(ValueLayout.JAVA_INT, OFFSET_NORMAL_IS_VERY_ACCURATE) != 0;
        this.hasUpdatedVertices = stateSegment.get(ValueLayout.JAVA_INT, OFFSET_HAS_UPDATED_VERTICES) != 0;
        this.writeToIndex = stateSegment.get(ValueLayout.JAVA_INT, OFFSET_WRITE_TO_INDEX);

        int nativePackedNormal = stateSegment.get(ValueLayout.JAVA_INT, OFFSET_PACKED_NORMAL);
        if (nativePackedNormal != this.packedNormal) {
            throw new IllegalStateException("Native full translucent quad changed immutable packed normal");
        }
    }

    private long getHandle() {
        return this.state.getHandle();
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeCreate(long nativeQuadAddress, int facing, int packedNormal, MemorySegment handleOutput,
            MemorySegment stateOutput) {
        try {
            return (int) CREATE.invokeExact(nativeQuadAddress, facing, packedNormal, handleOutput, stateOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust full translucent quad creation downcall failed", throwable);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust full translucent quad destroy downcall failed", throwable);
        }
    }

    private static int invokeGetVeryAccurateNormal(long handle, MemorySegment stateOutput) {
        try {
            return (int) GET_VERY_ACCURATE_NORMAL.invokeExact(handle, stateOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust full translucent quad accurate normal downcall failed", throwable);
        }
    }

    private static int invokeClassify(long handle, float normalX, float normalY, float normalZ, float distance,
            MemorySegment mapsOutput) {
        try {
            return (int) CLASSIFY.invokeExact(handle, normalX, normalY, normalZ, distance, mapsOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust full translucent quad classification downcall failed", throwable);
        }
    }

    private static int invokeTriggerUpdate(long handle) {
        try {
            return (int) TRIGGER_UPDATE.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust full translucent quad update trigger downcall failed", throwable);
        }
    }

    private static int invokeSetWriteToIndex(long handle, int writeToIndex) {
        try {
            return (int) SET_WRITE_TO_INDEX.invokeExact(handle, writeToIndex);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust full translucent quad write index downcall failed", throwable);
        }
    }

    private static int invokeWriteToNativeBuffer(long handle, long nativeQuadAddress, int materialBits) {
        try {
            return (int) WRITE_TO_NATIVE_BUFFER.invokeExact(handle, nativeQuadAddress, materialBits);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust full translucent quad native buffer write downcall failed", throwable);
        }
    }

    private static final class State implements Runnable {
        private long handle;

        private State(long handle) {
            this.handle = handle;
        }

        private synchronized long getHandle() {
            if (this.handle == 0) {
                throw new IllegalStateException("Native full translucent quad has been closed");
            }
            return this.handle;
        }

        @Override
        public synchronized void run() {
            long handle = this.handle;
            if (handle == 0) {
                return;
            }

            check(invokeDestroy(handle), "native full translucent quad destroy");
            this.handle = 0;
        }
    }
}
