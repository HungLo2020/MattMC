package net.sodium.client.render.chunk.compile.tasks;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.NativeLibraryLoader;

/** Temporary contextual inputs to the Rust terrain-light consumer. */
final class NativeTerrainLighting {
    static final int CELLS = 18 * 18 * 18;
    static final int CONTEXT_STRIDE = 8;
    private static final MethodHandle ADMIT = NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_terrain_light_admit", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle PREPARE = NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_terrain_light_prepare", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private NativeTerrainLighting() {}

    /** Decline custom/unregistered states before contextual callbacks begin. */
    static boolean admits(MemorySegment stateIds) {
        if (stateIds.byteSize() != (long) CELLS * Integer.BYTES) throw new IllegalArgumentException("Invalid terrain ID span");
        try { return (int) ADMIT.invokeExact(stateIds, CELLS) == 0; }
        catch (Throwable failure) { throw rethrow(failure); }
    }

    static void writeContext(long address, BlockAndTintGetter level, BlockState state, BlockPos pos) {
        boolean emissive = state.emissiveRendering(level, pos);
        boolean viewBlocking = state.isViewBlocking(level, pos);
        boolean fullOpaque = state.isSolidRender();
        boolean fullCube = state.isCollisionShapeFullBlock(level, pos);
        int luminance = state.getLightEmission(); // The admitted platform uses this immutable field.
        // Preserve the original second predicate call and its conditional order.
        boolean secondEmissive = !(fullOpaque && luminance == 0) && !emissive
            && state.emissiveRendering(level, pos);
        float shade = luminance == 0 ? state.getShadeBrightness(level, pos) : 1.0F;
        org.lwjgl.system.MemoryUtil.memPutInt(address, (emissive ? 1 : 0) | (viewBlocking ? 2 : 0)
            | (fullCube ? 4 : 0) | (secondEmissive ? 8 : 0));
        org.lwjgl.system.MemoryUtil.memPutFloat(address + 4, shade);
    }

    static void prepare(MemorySegment[] views, MemorySegment ids, MemorySegment contexts, MemorySegment output) {
        if (views.length != 54 || ids.byteSize() != (long) CELLS * 4
                || contexts.byteSize() != (long) CELLS * CONTEXT_STRIDE || output.byteSize() != (long) CELLS * 4)
            throw new IllegalArgumentException("Invalid terrain light spans");
        try (Arena call = Arena.ofConfined()) {
            MemorySegment pointers = call.allocate(54L * Long.BYTES, Long.BYTES);
            for (int i = 0; i < views.length; i++) {
                MemorySegment view = views[i];
                if (view != null && (!view.scope().isAlive() || view.byteSize() != 16))
                    throw new IllegalStateException("Invalid or expired terrain light lease");
                pointers.setAtIndex(ValueLayout.ADDRESS, i, view == null ? MemorySegment.NULL : view);
            }
            int status = (int) PREPARE.invokeExact(pointers, 54, ids, contexts, CELLS, output);
            if (status != 0) throw new IllegalStateException("Native terrain light preparation rejected: " + status);
        } catch (Throwable failure) { throw rethrow(failure); }
        finally { Reference.reachabilityFence(views); }
    }
    private static RuntimeException rethrow(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) return exception;
        return new IllegalStateException(failure);
    }
}
