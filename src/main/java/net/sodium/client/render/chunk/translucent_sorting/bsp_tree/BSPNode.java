package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import net.minecraft.core.SectionPos;
import net.minecraft.util.NativeLibraryLoader;
import net.sodium.client.render.chunk.translucent_sorting.QuadSplittingMode;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;

/**
 * Stable Java-side handle for a dynamic BSP build.
 * <p>
 * BSP partition discovery, node construction, trigger-plane collection, and
 * runtime traversal now live in Rust. This class remains as the object retained
 * by DynamicBSPData so callers do not need to know about native ownership.
 */
public final class BSPNode implements AutoCloseable {
    private static final int MAX_ENCODED_QUAD_INDEX = 0x3FFFFFFF;
    private static final int OK = 0;
    private static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_reusable_root_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));

    private final State state;
    private final Cleaner.Cleanable cleanable;

    BSPNode(long nativeHandle) {
        this.state = new State(nativeHandle);
        this.cleanable = CLEANER.register(this, this.state);
    }

    public static NativeBspBuildResult buildBSP(TQuad[] quads, SectionPos sectionPos, BSPNode oldRoot,
            boolean prepareNodeReuse, QuadSplittingMode quadSplittingMode) {
        validateQuadCount(quads.length);

        NativeBspBuildResult result = new NativeBspBuildResult();
        try {
            NativeBspBuilder.BuiltTree builtTree = NativeBspBuilder.build(quads, result, oldRoot,
                    prepareNodeReuse, quadSplittingMode);
            BSPNode rootNode = new BSPNode(builtTree.reusableRootHandle());
            result.finishNative(rootNode, builtTree.indexQuadCount(), builtTree.updatedQuads(),
                    builtTree.handle());
            return result;
        } catch (RuntimeException exception) {
            result.close();
            throw exception;
        }
    }

    private static void validateQuadCount(int quadCount) {
        if (quadCount * 2 > MAX_ENCODED_QUAD_INDEX) {
            throw new IllegalArgumentException("Too many quads: " + quadCount);
        }
    }

    long nativeHandle() {
        return this.state.getHandleOrZero();
    }

    @Override
    public void close() {
        this.cleanable.clean();
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP reusable root destroy downcall failed", throwable);
        }
    }

    private static final class State implements Runnable {
        private long handle;

        private State(long handle) {
            this.handle = handle;
        }

        private synchronized long getHandleOrZero() {
            return this.handle;
        }

        @Override
        public synchronized void run() {
            long handle = this.handle;
            if (handle == 0) {
                return;
            }

            check(invokeDestroy(handle), "native BSP reusable root destroy");
            this.handle = 0;
        }
    }
}
