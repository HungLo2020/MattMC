package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongList;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;

/**
 * Frozen's same-detail full-data merge (`updateFromSameDetailLevel`), run in
 * Rust between two source tables, or from a generated chunk's columns. Rust
 * reads and writes columns, metadata planes and footprints; Java only refreshes
 * the CPU views of escaped column readers.
 */
public final class NativeDhSameDetailMerge {
    private static MethodHandle handle(String name, FunctionDescriptor signature) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_same_detail_" + name, signature);
    }
    private static final MethodHandle SOURCE_OWNED = handle("source_owned", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle COLUMNS = handle("columns", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle VIEW = handle("view", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle COMMIT_SOURCE = handle("commit_primary_extents", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle PROJECTION = NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_dh_same_detail_reader_projection",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS),Linker.Option.critical(true));
    private static final MethodHandle APPLY_OWNED_HASH = handle("metadata_owned_hash", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle PUBLISH_FOOTPRINTS = handle("publish_footprints", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment metadata = arena.allocate(88, 8);
        MemorySegment remap = arena.allocate(4096L * 4, 4);
        final MemorySegment output = arena.allocate(4096L * 16, 8), detached = arena.allocate(4096);
        final long[] projection = new long[4096 * 4];
        final MemorySegment projectionHeap = MemorySegment.ofArray(projection);
    }
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private NativeDhSameDetailMerge() {}

    /** Whether points changed, whether any metadata was written, and the tagged prepared hash (0: none). */
    public record OwnedUpdate(boolean changed, boolean metadataUpdated, long hash) {}

    /** Merge {@code source} into {@code target}, both of the same detail level. */
    public static OwnedUpdate update(NativeDhSource target, NativeDhSource source, int[] remap, long position) {
        return run(target, remap, position, source, scratch -> (MemorySegment) SOURCE_OWNED.invokeExact(
            target.stageOwner(), source.stageOwner(), stageRemap(scratch, remap), remap.length), true);
    }
    /**
     * {@link #update} with a generated chunk's columns as the source, read in
     * place (Rust {@code mattmc_dh_same_detail_columns}), so no producer data
     * source, table or ID map is built.
     */
    public static OwnedUpdate updateFromColumns(NativeDhSource target, NativeDhColumns producer, int[] remap, long position) {
        return run(target, remap, position, producer, scratch -> (MemorySegment) COLUMNS.invokeExact(
            target.stageOwner(), producer.stageSourceOwner(), stageRemap(scratch, remap), remap.length), false);
    }
    @FunctionalInterface private interface Prepare { MemorySegment apply(Scratch scratch) throws Throwable; }
    private static MemorySegment stageRemap(Scratch scratch, int[] remap) {
        // Mappings may exceed 4096 entries; the per-thread scratch grows to fit.
        if (scratch.remap.byteSize() < remap.length * 4L) scratch.remap = scratch.arena.allocate(remap.length * 8L, 4);
        MemorySegment.copy(MemorySegment.ofArray(remap), 0, scratch.remap, 0, remap.length * 4L);
        return scratch.remap;
    }
    private static OwnedUpdate run(NativeDhSource target, int[] remap, long position, Object source, Prepare prepare, boolean footprints) {
        var scratch = SCRATCH.get(); MemorySegment pointer = MemorySegment.NULL;
        try {
            pointer = prepare.apply(scratch);
            // Production sources never share columns, and their remaps cover
            // every point ID; a refusal means corrupt input, as Java's remap throws.
            if (pointer.address() == 0) throw new IllegalStateException("DH same-detail merge rejected its input");
            if ((int) VIEW.invokeExact(pointer, scratch.metadata) != 1) throw new IllegalStateException("Invalid DH merge");
            boolean changed = scratch.metadata.getAtIndex(ValueLayout.JAVA_LONG, 8) != 0;
            long actions = scratch.metadata.getAtIndex(ValueLayout.JAVA_LONG, 10);
            commit(pointer, target, scratch);
            int count = (int) APPLY_OWNED_HASH.invokeExact(pointer, target.stageOwner(), position, scratch.metadata);
            if (count < 0) throw new IllegalStateException("Invalid owned DH metadata publication");
            long hash = scratch.metadata.get(ValueLayout.JAVA_LONG, 0);
            if (footprints && actions != 0) {
                target.footprintsChanged();
                if ((int) PUBLISH_FOOTPRINTS.invokeExact(pointer, target.stageOwner()) < 0)
                    throw new IllegalStateException("Invalid DH footprint publication");
            }
            return new OwnedUpdate(changed, count != 0, hash);
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally {
            if (pointer.address() != 0) release(pointer);
            Reference.reachabilityFence(target); Reference.reachabilityFence(source);
        }
    }
    /** Publish new columns into the table, then refresh escaped readers' CPU extents. */
    private static void commit(MemorySegment owner, NativeDhSource target, Scratch scratch) throws Throwable {
        int result = (int) COMMIT_SOURCE.invokeExact(owner, target.stageOwner(), scratch.output, scratch.detached);
        if (result != 1 && result != -1) throw new IllegalStateException("Invalid retained DH publication");
        int rowCount = (int) PROJECTION.invokeExact(owner, target.stageOwner(), scratch.output, scratch.detached, scratch.projectionHeap);
        if (rowCount < 0 || rowCount > 4096) throw new IllegalStateException("Invalid DH projection");
        var rows = scratch.projection;
        for (int row = 0; row < rowCount; row++) {
            int i = (int) rows[row * 4];
            var column = target.reader(i);
            if (column == null) continue;
            boolean hasOwner = rows[row * 4 + 3] != 0;
            MemorySegment replacement = result == 1 && !hasOwner ? target.retainColumn(i) : null;
            // Read numeric addresses: unchanged extents need no temporary FFM segment.
            column.acceptMergeView(rows[row * 4 + 1], rows[row * 4 + 2], hasOwner, replacement);
        }
        if (result == -1) throw new OutOfMemoryError("Cannot reserve retained DH merge storage");
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
}
