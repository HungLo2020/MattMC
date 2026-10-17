package net.minecraft.world.level.chunk;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Lifetime owner for every mutable native column of one owner (a DH
 * full-data source). A per-column automatic arena gave each Rust vector its
 * own GC cleanable and cleanup record: millions per session, which made every
 * collection, and the crash path's explicit ones, scan them. Grouped columns
 * register only their pointer here; one cleanup frees them all once the owner
 * and all of its columns are unreachable, as Frozen's pooled Java arrays live
 * until their data source closes.
 */
public final class NativeDhColumnGroup {
    /** Pointers of the group's Rust vectors; reachable from the cleanup, never the group itself. */
    private static final class Owned {
        private long[] pointers = new long[64];
        private int count;
        synchronized void add(long pointer) {
            if (count == pointers.length) pointers = Arrays.copyOf(pointers, count * 2);
            pointers[count++] = pointer;
        }
        synchronized void releaseAll(Consumer<MemorySegment> release) {
            for (int i = 0; i < count; i++) release.accept(MemorySegment.ofAddress(pointers[i]));
            count = 0;
        }
    }

    final Arena arena = Arena.ofAuto();
    private final Owned owned = new Owned();

    public NativeDhColumnGroup() {
        Owned released = owned;
        // One cleanup for the whole group, run when the arena becomes unreachable.
        MemorySegment.ofAddress(0L).reinterpret(1, arena, ignored -> released.releaseAll(NativeDhMutableColumn::release));
    }

    /** Adopts one Rust vector; it is freed with the group. */
    MemorySegment adopt(MemorySegment pointer) {
        owned.add(pointer.address());
        return pointer.reinterpret(1, arena, null);
    }
}
