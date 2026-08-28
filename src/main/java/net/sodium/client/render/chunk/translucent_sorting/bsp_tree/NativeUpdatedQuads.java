package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import net.minecraft.util.NativeLibraryLoader;
import net.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.sodium.client.render.chunk.translucent_sorting.quad.NativeFullTQuad;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.render.chunk.vertex.format.NativeChunkVertexFormat;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public final class NativeUpdatedQuads implements AutoCloseable {
    private static final int OK = 0;
    private static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_updated_quads_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_updated_quads_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle ADD = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_updated_quads_add",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle SET_COUNTS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_updated_quads_set_counts",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle COUNTS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_updated_quads_counts",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle APPLY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_updated_quads_apply",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));

    private final State state;
    private final Cleaner.Cleanable cleanable;
    private final ArrayList<NativeFullTQuad> keepAlive = new ArrayList<>();

    public NativeUpdatedQuads() {
        this(createNativeHandle());
    }

    private NativeUpdatedQuads(long handle) {
        if (handle == 0) {
            throw new IllegalArgumentException("Native updated quad handle must not be null");
        }
        this.state = new State(handle);
        this.cleanable = CLEANER.register(this, this.state);
    }

    static NativeUpdatedQuads fromHandle(long handle, NativeFullTQuad[] keepAlive) {
        NativeUpdatedQuads updatedQuads = new NativeUpdatedQuads(handle);
        updatedQuads.keepAlive.addAll(java.util.Arrays.asList(keepAlive));
        return updatedQuads;
    }

    private static long createNativeHandle() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeCreate(handleSegment), "native updated quad list creation");
            long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native updated quad list creation returned a null handle");
            }
            return handle;
        }
    }

    public void add(NativeFullTQuad quad) {
        check(invokeAdd(this.state.getHandle(), quad.nativeHandle()), "native updated quad addition");
        this.keepAlive.add(quad);
    }

    public void setQuadCounts(int meshQuadCount, int indexQuadCount) {
        check(invokeSetCounts(this.state.getHandle(), meshQuadCount, indexQuadCount),
                "native updated quad count update");
    }

    public int getMeshQuadCount() {
        return this.counts()[0];
    }

    public int getIndexQuadCount() {
        return this.counts()[1];
    }

    public void applyBufferUpdates(NativeChunkVertexFormat format, int sectionIndex, ByteBuffer buffer) {
        check(invokeApply(this.state.getHandle(), MemoryUtil.memAddress(buffer), buffer.remaining(),
                NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE, format.stride(), format.blockIdOffset(),
                format.normalOffset(), format.tangentOffset(), format.midUvOffset(), format.midBlockOffset(),
                sectionIndex, usesSeparateAo() ? 1 : 0, DefaultMaterials.TRANSLUCENT.bits()),
                "native updated quad buffer application");
    }

    public void applyBufferUpdates(NativeSectionMeshBuilder.FacingBuffer builder, ByteBuffer buffer) {
        this.applyBufferUpdates(builder.nativeFormat(), builder.sectionIndex(), buffer);
    }

    @Override
    public void close() {
        this.cleanable.clean();
        this.keepAlive.clear();
    }

    private int[] counts() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countsSegment = arena.allocate(ValueLayout.JAVA_INT, 2);
            check(invokeCounts(this.state.getHandle(), countsSegment, 2), "native updated quad count query");
            return new int[] {
                    countsSegment.getAtIndex(ValueLayout.JAVA_INT, 0),
                    countsSegment.getAtIndex(ValueLayout.JAVA_INT, 1)
            };
        }
    }

    private static boolean usesSeparateAo() {
        if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
                || net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
            return false;
        }
        return net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.shouldUseSeparateAo();
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeCreate(MemorySegment handleOutput) {
        try {
            return (int) CREATE.invokeExact(handleOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust updated quad list creation downcall failed", throwable);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust updated quad list destroy downcall failed", throwable);
        }
    }

    private static int invokeAdd(long handle, long quadHandle) {
        try {
            return (int) ADD.invokeExact(handle, quadHandle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust updated quad list add downcall failed", throwable);
        }
    }

    private static int invokeSetCounts(long handle, int meshQuadCount, int indexQuadCount) {
        try {
            return (int) SET_COUNTS.invokeExact(handle, meshQuadCount, indexQuadCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust updated quad count downcall failed", throwable);
        }
    }

    private static int invokeCounts(long handle, MemorySegment countsOutput, int countsOutputLength) {
        try {
            return (int) COUNTS.invokeExact(handle, countsOutput, countsOutputLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust updated quad count query downcall failed", throwable);
        }
    }

    private static int invokeApply(long handle, long outputAddress, int outputCapacity, int quadStride,
            int vertexStride, int blockIdOffset, int normalOffset, int tangentOffset, int midUvOffset,
            int midBlockOffset, int sectionIndex, int separateAo, int materialBits) {
        try {
            return (int) APPLY.invokeExact(handle, outputAddress, outputCapacity, quadStride, vertexStride,
                    blockIdOffset, normalOffset, tangentOffset, midUvOffset, midBlockOffset, sectionIndex,
                    separateAo, materialBits);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust updated quad apply downcall failed", throwable);
        }
    }

    private static final class State implements Runnable {
        private long handle;

        private State(long handle) {
            this.handle = handle;
        }

        private synchronized long getHandle() {
            if (this.handle == 0) {
                throw new IllegalStateException("Native updated quad list has been closed");
            }
            return this.handle;
        }

        @Override
        public synchronized void run() {
            long handle = this.handle;
            if (handle == 0) {
                return;
            }

            check(invokeDestroy(handle), "native updated quad list destroy");
            this.handle = 0;
        }
    }
}
