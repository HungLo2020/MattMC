package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;

/** Frozen's complete source occlusion culling, run in Rust over the source table. */
public final class NativeDhOcclusionPass {
    private static final MethodHandle OWNED = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_source_cull_readers",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final ThreadLocal<MemorySegment> OWNED_OUTPUT = ThreadLocal.withInitial(
        () -> Arena.ofAuto().allocate((1L + 4096L * 3) * 8, 8));
    private NativeDhOcclusionPass() {}

    /** Cull hidden points in place; only escaped readers' CPU extents return to Java. */
    public static void cull(NativeDhMaterials policy, NativeDhSource source) {
        var output = OWNED_OUTPUT.get();
        try {
            int result = (int) OWNED.invokeExact(source.stageOwner(), policy.owner, output);
            if (result == 1 || result == -1) {
                int count = Math.toIntExact(output.getAtIndex(ValueLayout.JAVA_LONG, 0));
                for (int row = 0; row < count; row++) {
                    long offset = 1L + row * 3L;
                    var column = source.reader((int) output.getAtIndex(ValueLayout.JAVA_LONG, offset));
                    if (column == null) continue;
                    column.acceptMergeView(output.getAtIndex(ValueLayout.JAVA_LONG, offset + 1),
                        output.getAtIndex(ValueLayout.JAVA_LONG, offset + 2), true, null);
                }
            }
            switch (result) {
                case 1 -> {}
                case -1 -> throw new OutOfMemoryError("Cannot detach DH occlusion centers");
                case -2 -> throw new IllegalStateException("DH full-data source binds one column to two cells");
                case -3 -> throw new IndexOutOfBoundsException("DH full-data point ID has no mapping entry");
                default -> throw new IllegalStateException("Invalid DH source occlusion result");
            }
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(source); Reference.reachabilityFence(policy); }
    }
}
