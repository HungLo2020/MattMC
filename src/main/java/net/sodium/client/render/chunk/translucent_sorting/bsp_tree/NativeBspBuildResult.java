package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import net.minecraft.util.NativeLibraryLoader;
import net.sodium.client.util.NativeBuffer;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;

/**
 * Native-owned result of dynamic BSP construction. The Java root node is retained
 * only so the existing Java partition builder can reuse old nodes on later
 * builds; geometry planes and the finished BSP traversal tree live in Rust.
 */
public final class NativeBspBuildResult implements AutoCloseable {
    private static final int OK = 0;
    private static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_build_result_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_build_result_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle ADD_ALIGNED_PLANE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_build_result_add_aligned_plane",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT));
    private static final MethodHandle ADD_UNALIGNED_PLANE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_build_result_add_unaligned_plane",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT));
    private static final MethodHandle TAKE_GEOMETRY_PLANES = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_build_result_take_geometry_planes",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle SET_TREE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_build_result_set_tree",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle WRITE_INDEX_BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_build_result_write_index_buffer",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT));

    private final State state;
    private final Cleaner.Cleanable cleanable;
    private BSPNode rootNode;
    private NativeUpdatedQuads updatedQuads;
    private int indexQuadCount;

    public NativeBspBuildResult() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeCreate(handleSegment), "native BSP build result creation");
            long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native BSP build result creation returned a null handle");
            }
            this.state = new State(handle);
            this.cleanable = CLEANER.register(this, this.state);
        }
    }

    public BSPNode rootNode() {
        return this.rootNode;
    }

    void addDoubleSidedAlignedPlane(int axis, float distance) {
        check(invokeAddAlignedPlane(this.state.getHandle(), axis, distance),
                "native BSP build result aligned plane insertion");
    }

    void addDoubleSidedUnalignedPlane(Vector3fc normal, float distance) {
        check(invokeAddUnalignedPlane(this.state.getHandle(), normal.x(), normal.y(), normal.z(), distance),
                "native BSP build result unaligned plane insertion");
    }

    void finish(BSPNode rootNode, int indexQuadCount, NativeUpdatedQuads updatedQuads, long treeHandle) {
        if (rootNode == null) {
            throw new IllegalArgumentException("BSP root node must not be null");
        }
        if (indexQuadCount < 0) {
            throw new IllegalArgumentException("Invalid BSP index quad count: " + indexQuadCount);
        }
        if (treeHandle == 0) {
            throw new IllegalArgumentException("Native BSP tree handle must not be null");
        }

        long transferredTreeHandle = treeHandle;
        try {
            check(invokeSetTree(this.state.getHandle(), treeHandle), "native BSP build result tree transfer");
            transferredTreeHandle = 0;
        } finally {
            if (transferredTreeHandle != 0) {
                NativeBspTree.destroyHandle(transferredTreeHandle);
            }
        }

        this.rootNode = rootNode;
        this.indexQuadCount = indexQuadCount;
        this.updatedQuads = updatedQuads;
    }

    public int indexQuadCount() {
        return this.indexQuadCount;
    }

    public NativeUpdatedQuads updatedQuads() {
        return this.updatedQuads;
    }

    public long takeGeometryPlanesHandle() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeTakeGeometryPlanes(this.state.getHandle(), handleSegment),
                    "native BSP build result geometry plane transfer");
            long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native BSP build result returned a null geometry plane handle");
            }
            return handle;
        }
    }

    public void writeIndexBuffer(NativeBuffer indexBuffer, Vector3fc cameraPos) {
        check(invokeWriteIndexBuffer(this.state.getHandle(), MemoryUtil.memAddress(indexBuffer.getDirectBuffer()),
                indexBuffer.getLength(), cameraPos.x(), cameraPos.y(), cameraPos.z()),
                "native BSP build result index buffer writing");
    }

    @Override
    public void close() {
        this.cleanable.clean();
        if (this.updatedQuads != null) {
            this.updatedQuads.close();
            this.updatedQuads = null;
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
            throw new IllegalStateException("Rust BSP build result creation downcall failed", throwable);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP build result destroy downcall failed", throwable);
        }
    }

    private static int invokeAddAlignedPlane(long handle, int axis, float distance) {
        try {
            return (int) ADD_ALIGNED_PLANE.invokeExact(handle, axis, distance);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP build result aligned plane downcall failed", throwable);
        }
    }

    private static int invokeAddUnalignedPlane(long handle, float normalX, float normalY, float normalZ,
            float distance) {
        try {
            return (int) ADD_UNALIGNED_PLANE.invokeExact(handle, normalX, normalY, normalZ, distance);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP build result unaligned plane downcall failed", throwable);
        }
    }

    private static int invokeTakeGeometryPlanes(long handle, MemorySegment outputHandle) {
        try {
            return (int) TAKE_GEOMETRY_PLANES.invokeExact(handle, outputHandle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP build result geometry plane downcall failed", throwable);
        }
    }

    private static int invokeSetTree(long handle, long treeHandle) {
        try {
            return (int) SET_TREE.invokeExact(handle, treeHandle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP build result tree downcall failed", throwable);
        }
    }

    private static int invokeWriteIndexBuffer(long handle, long outputAddress, int outputCapacity,
            float cameraX, float cameraY, float cameraZ) {
        try {
            return (int) WRITE_INDEX_BUFFER.invokeExact(handle, outputAddress, outputCapacity,
                    cameraX, cameraY, cameraZ);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP build result write downcall failed", throwable);
        }
    }

    private static final class State implements Runnable {
        private long handle;

        private State(long handle) {
            this.handle = handle;
        }

        private synchronized long getHandle() {
            if (this.handle == 0) {
                throw new IllegalStateException("Native BSP build result has been closed");
            }
            return this.handle;
        }

        @Override
        public synchronized void run() {
            long handle = this.handle;
            if (handle == 0) {
                return;
            }

            check(invokeDestroy(handle), "native BSP build result destroy");
            this.handle = 0;
        }
    }
}
