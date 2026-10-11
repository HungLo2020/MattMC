package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.IdentityHashMap;
import net.minecraft.util.NativeLibraryLoader;

/** Complete Frozen-order source culling over existing authoritative CPU storage. */
public final class NativeDhOcclusionPass {
    private static final MethodHandle CULL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_columns_cull_pass",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle OWNED = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_source_cull_readers",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final ThreadLocal<MemorySegment> OWNED_OUTPUT = ThreadLocal.withInitial(
        () -> Arena.ofAuto().allocate((1L + 4096L * 3) * 8, 8));
    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment inputs = arena.allocate(4096L * 24, 8), output = arena.allocate(4096L * 32, 8);
        final IdentityHashMap<NativeDhColumnList, Boolean> unique = new IdentityHashMap<>();
    }
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private NativeDhOcclusionPass() {}

    /** The Rust table owns admission and detachment; no Java input grid. */
    public static boolean tryCull(NativeDhMaterials policy, NativeDhSource source) {
        if (policy == null || !source.isLive()) return false;
        var output = OWNED_OUTPUT.get();
        try {
            int result = (int) OWNED.invokeExact(source.stageOwner(), policy.owner, output);
            if (result == 0) return false;
            int count = Math.toIntExact(output.getAtIndex(ValueLayout.JAVA_LONG, 0));
            for (int row = 0; row < count; row++) {
                long offset = 1L + row * 3L;
                var column = source.reader((int) output.getAtIndex(ValueLayout.JAVA_LONG, offset));
                if(column==null)continue;
                column.acceptMergeView(output.getAtIndex(ValueLayout.JAVA_LONG, offset + 1),
                    output.getAtIndex(ValueLayout.JAVA_LONG, offset + 2), true, null);
            }
            if (result == -1) throw new OutOfMemoryError("Cannot detach DH occlusion centers");
            if (result != 1) throw new IllegalStateException("Invalid DH source occlusion result");
            return true;
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(source); Reference.reachabilityFence(policy); }
    }

    /** Decline custom lists, aliases, oversized columns and unsupported materials. */
    public static boolean tryCull(NativeDhMaterials policy, LongList[] columns) {
        if (policy == null || columns.length != 4096) return false;
        var scratch = SCRATCH.get();
        try {
            for (var column : columns) {
                if (column == null) continue;
                if (column instanceof NativeDhColumnList nativeColumn) {
                    if (column.size() > 4096 || scratch.unique.put(nativeColumn, Boolean.TRUE) != null) return false;
                } else if (column.getClass() != LongArrayList.class || !column.isEmpty()) return false;
            }
            for (int i = 0; i < 4096; i++) {
                var column = columns[i] instanceof NativeDhColumnList nativeColumn ? nativeColumn : null;
                boolean center = column != null && column.size() > 1 && i / 64 > 0 && i / 64 < 63 && i % 64 > 0 && i % 64 < 63
                    && present(columns[i + 64]) && present(columns[i - 64]) && present(columns[i + 1]) && present(columns[i - 1]);
                var owner = column == null ? MemorySegment.NULL : column.stageOcclusionOwner(center);
                scratch.inputs.setAtIndex(ValueLayout.ADDRESS, i * 3L, owner);
                scratch.inputs.setAtIndex(ValueLayout.ADDRESS, i * 3L + 1,
                    column == null || owner.address() != 0 ? MemorySegment.NULL : column.stagePoints());
                scratch.inputs.setAtIndex(ValueLayout.JAVA_LONG, i * 3L + 2,
                    column == null || owner.address() != 0 ? 0L : (long) column.size());
            }
            if ((int) CULL.invokeExact(scratch.inputs, policy.owner, scratch.output) != 1) return false;
            for (int i = 0; i < 4096; i++) if (columns[i] instanceof NativeDhColumnList column) {
                column.acceptOcclusionView(scratch.output.getAtIndex(ValueLayout.ADDRESS, i * 4L),
                    scratch.output.getAtIndex(ValueLayout.JAVA_LONG, i * 4L + 1),
                    scratch.output.getAtIndex(ValueLayout.JAVA_LONG, i * 4L + 2) != 0,
                    (int) scratch.output.getAtIndex(ValueLayout.JAVA_LONG, i * 4L + 3));
            }
            return true;
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { scratch.unique.clear(); Reference.reachabilityFence(columns); Reference.reachabilityFence(policy); }
    }
    private static boolean present(LongList column) { return column != null && !column.isEmpty(); }
}
