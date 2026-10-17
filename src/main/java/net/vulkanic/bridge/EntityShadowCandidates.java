package net.vulkanic.bridge;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.phys.AABB;

/** Confined producer view of bounded Rust-owned shadow candidate storage.
 * Slots are immutable between publication to the query and the next clear.
 * This owner contains CPU facts only; selection and GPU ownership stay in Rust. */
public final class EntityShadowCandidates implements AutoCloseable {
    private static MethodHandle bind(String suffix, FunctionDescriptor type) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_entity_shadow_candidates_" + suffix, type);
    }
    private static final MethodHandle CREATE = bind("create", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle GROW = bind("grow", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = bind("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private final Arena arena = Arena.ofConfined();
    private final MemorySegment owner;
    private MemorySegment slots = MemorySegment.NULL, admitted = MemorySegment.NULL;
    private int capacity, count;

    public EntityShadowCandidates() {
        try {
            var pointer = (MemorySegment)CREATE.invokeExact();
            try { owner = pointer.reinterpret(24, arena, EntityShadowCandidates::release).asReadOnly(); }
            catch (Throwable failure) { release(pointer); throw failure; }
        } catch (Throwable failure) { arena.close(); throw failure(failure); }
    }
    private static RuntimeException failure(Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Native shadow candidate call failed", failure);
    }
    private static void release(MemorySegment owner) {
        try { RELEASE.invokeExact(owner); } catch (Throwable failure) { throw failure(failure); }
    }
    @Override public void close() { if (arena.scope().isAlive()) arena.close(); }
    public void clear() { check(); count = 0; }
    public int size() { check(); return count; }
    MemorySegment owner() { check(); return owner; }
    private void check() { owner.get(ValueLayout.JAVA_INT, 16); }
    public boolean admitted(int index) {
        check();
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException(index);
        return admitted.get(ValueLayout.JAVA_BYTE, index) != 0;
    }
    private void grow() {
        try {
            int ok = (int)GROW.invokeExact(owner, count + 1);
            // Refresh on failure too: one allocation could already have moved.
            capacity = owner.get(ValueLayout.JAVA_INT, 16);
            slots = MemorySegment.ofAddress(owner.get(ValueLayout.JAVA_LONG, 0)).reinterpret((long)capacity * 104, arena, null);
            admitted = MemorySegment.ofAddress(owner.get(ValueLayout.JAVA_LONG, 8)).reinterpret(capacity, arena, null).asReadOnly();
            if (ok == 0) throw new IllegalStateException("shadow candidates exceed bounded capacity or allocation failed");
        } catch (Throwable failure) { throw failure(failure); }
    }
    public void add(int flags, AABB bounds, AABB holder) {
        check();
        // The capture previously constructed the holder record first, then
        // the entity record. Keep the same invalid-input failure ordering.
        if (holder != null) validate(holder);
        validate(bounds);
        if ((flags & ~63) != 0 || ((flags & 16) != 0) != (holder != null)
                || ((flags & 32) != 0 && (flags & 4) == 0))
            throw new IllegalArgumentException("incoherent entity culling flags");
        if (count == capacity) grow();
        MemorySegment slot = slots.asSlice((long)count * 104, 104);
        slot.set(ValueLayout.JAVA_INT, 0, flags);
        slot.set(ValueLayout.JAVA_INT, 4, 0);
        writeBox(slot, 8, bounds);
        if (holder != null) writeBox(slot, 56, holder);
        else slot.asSlice(56, 48).fill((byte)0);
        count++;
    }
    private static void validate(AABB box) {
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)
                || box.minX > box.maxX || box.minY > box.maxY || box.minZ > box.maxZ)
            throw new IllegalArgumentException("entity culling bounds must be finite and ordered");
    }
    private static void writeBox(MemorySegment slot, long offset, AABB box) {
        slot.set(ValueLayout.JAVA_DOUBLE, offset, box.minX);
        slot.set(ValueLayout.JAVA_DOUBLE, offset + 8, box.minY);
        slot.set(ValueLayout.JAVA_DOUBLE, offset + 16, box.minZ);
        slot.set(ValueLayout.JAVA_DOUBLE, offset + 24, box.maxX);
        slot.set(ValueLayout.JAVA_DOUBLE, offset + 32, box.maxY);
        slot.set(ValueLayout.JAVA_DOUBLE, offset + 40, box.maxZ);
    }
}
