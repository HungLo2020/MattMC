package net.minecraft.server.level;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.util.NativeLibraryLoader;

/** Callable pins only. Rust owns priorities, membership, ordering and moves.
 * Mutations require the original consecutive dispatcher's single-writer exclusion,
 * through consuming borrowed output. Atomic header readers may run concurrently. */
final class NativeChunkTasks {
    private static MethodHandle handle(String name, FunctionDescriptor signature, Linker.Option... options) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", name, signature, options);
    }
    private static final MethodHandle CREATE = handle("mattmc_chunk_tasks_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle DESTROY = handle("mattmc_chunk_tasks_destroy", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle HEADER = handle("mattmc_chunk_tasks_header", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle CHANGE = handle("mattmc_chunk_tasks_change_v4", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    // This path is bounded to at most 4096 pending tasks and 64 levels, has no
    // callbacks or blocking locks, and uses off-heap metadata only. Larger
    // workloads retain the normal safepoint-capable call into the same owner.
    private static final MethodHandle FAST_CHANGE = handle("mattmc_chunk_tasks_change_v4", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), Linker.Option.critical(false));
    private static final VarHandle TOP = ValueLayout.JAVA_INT.varHandle();
    private static final VarHandle PENDING = ValueLayout.JAVA_LONG.varHandle();
    private static final Object NULL_TASK = new Object();
    private final int levels;
    private final MemorySegment owner, header, metadata;
    // Private CPU addresses on NativeLibraryLoader's supported 64-bit targets.
    // These auto-arena allocations never move. Every operation pins this owner
    // through output consumption; addresses never leave the callable adapter.
    private final long ownerAddress, metadataAddress;
    private Object[] references = new Object[64];
    // A read-only projection of this owner's reused numeric output allocation.
    // Borrowing still ends before the next mutation; only current outputCount
    // words may be read, even when the cached segment has a larger extent.
    private MemorySegment outputView = MemorySegment.NULL;
    private long outputAddress;
    private int outputCount;
    // Native result projections under the original writer exclusion. No Java
    // slot allocation or pending-count policy is duplicated here.
    private long nextRow = 1;
    private boolean fastChange;

    NativeChunkTasks(int levels) {
        this.levels = levels;
        this.fastChange = levels <= 64;
        MemorySegment pointer = MemorySegment.NULL;
        boolean adopted = false;
        try {
            pointer = (MemorySegment)CREATE.invokeExact(levels);
            if (pointer.address() == 0) throw new IllegalArgumentException("Native chunk task priority count: " + levels);
            Arena arena = Arena.ofAuto();
            owner = pointer.reinterpret(1, arena, NativeChunkTasks::destroy);
            adopted = true;
            header = ((MemorySegment)HEADER.invokeExact(pointer)).reinterpret(24, arena, null).asReadOnly();
            metadata = arena.allocate(24, 8);
            ownerAddress = pointer.address(); metadataAddress = metadata.address();
        } catch (RuntimeException | Error e) { if (!adopted && pointer.address() != 0) destroy(pointer); throw e; }
        catch (Throwable e) { if (!adopted && pointer.address() != 0) destroy(pointer); throw new IllegalStateException("Cannot create native chunk tasks", e); }
    }
    private static void destroy(MemorySegment pointer) {
        try { DESTROY.invokeExact(pointer); }
        catch (Throwable e) { throw new IllegalStateException("Cannot release native chunk tasks", e); }
    }
    int top() {
        try { return (int)TOP.getAcquire(header, 0L); }
        finally { Reference.reachabilityFence(this); }
    }
    boolean hasWork() { return top() < levels; }
    long pending() {
        try { return (long)PENDING.getAcquire(header, 8L); }
        finally { Reference.reachabilityFence(this); }
    }
    private int pin(Runnable task) {
        // Native arena allocation policy selects both the task node and the
        // temporary callable row. Original caller exclusion keeps this projection
        // stable until publication. Prepare Java capacity before native mutation.
        long id = nextRow;
        if (id <= 0 || id > Integer.MAX_VALUE - 8) throw new OutOfMemoryError("Too many chunk task references");
        int index = (int)id - 1;
        if (index >= references.length) {
            int capacity = (int)Math.min(Integer.MAX_VALUE - 8L, Math.max(id, references.length * 2L));
            references = Arrays.copyOf(references, capacity);
        }
        if (references[index] != null) throw new IllegalStateException("Native callable row was not retired");
        references[index] = task == null ? NULL_TASK : task;
        return index;
    }
    private Runnable unpin(long token) {
        if (token <= 0 || token > references.length) throw new IllegalStateException("Invalid chunk task token: " + token);
        int index = (int)token - 1;
        Object task = references[index];
        if (task == null) throw new IllegalStateException("Chunk task token already retired");
        references[index] = null;
        return task == NULL_TASK ? null : (Runnable)task;
    }
    private int change(int action, long pos, int a, long b) {
        try {
            long result = fastChange
                ? (long)FAST_CHANGE.invokeExact(ownerAddress, action, pos, a, b, metadataAddress)
                : (long)CHANGE.invokeExact(ownerAddress, action, pos, a, b, metadataAddress);
            nextRow = result >>> 32;
            fastChange = (result & 0x100) != 0;
            return (int)result & 0xff;
        }
        catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot update native chunk tasks", e); }
        finally { Reference.reachabilityFence(this); }
    }
    private MemorySegment output() {
        try {
            long count = metadata.getAtIndex(ValueLayout.JAVA_LONG, 1);
            if (count < 0 || count > references.length) throw new IllegalStateException("Invalid chunk task output length");
            outputCount = (int)count;
            long address = metadata.getAtIndex(ValueLayout.JAVA_LONG, 0);
            if (count != 0 && (address != outputAddress || count * 8L > outputView.byteSize())) {
                outputView = MemorySegment.ofAddress(address).reinterpret(count * 8L).asReadOnly();
                outputAddress = address;
            }
            return outputView;
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot read native chunk tasks", e); }
    }
    private void discardOutput() {
        var tokens = output();
        for (int i = 0; i < outputCount; i++) unpin(tokens.getAtIndex(ValueLayout.JAVA_LONG, i));
    }
    private static void check(int status, int level) {
        if (status == 2 || status == 3) throw new ArrayIndexOutOfBoundsException(level);
        if (status != 0) throw new IllegalStateException("Native chunk task operation failed: " + status);
    }
    void submit(Runnable task, long pos, int priority) {
        if (priority < 0 || priority >= levels) throw new ArrayIndexOutOfBoundsException(priority);
        int slot = pin(task);
        try {
            int status = change(0, pos, priority, slot + 1L);
            if (status != 0) { unpin(slot + 1L); check(status, priority); }
        } finally { Reference.reachabilityFence(this); }
    }
    void resort(long pos, int oldPriority, int newPriority) {
        try {
            int status = change(1, pos, oldPriority, newPriority);
            // Frozen removes the source before throwing for an invalid destination.
            if (status == 3) discardOutput();
            check(status, status == 2 ? oldPriority : newPriority);
        } finally { Reference.reachabilityFence(this); }
    }
    void release(long pos, boolean clear) {
        try { check(change(2, pos, clear ? 1 : 0, 0), 0); if (clear) discardOutput(); }
        finally { Reference.reachabilityFence(this); }
    }
    ChunkTaskPriorityQueue.TasksForChunk pop() {
        try {
            int status = change(3, 0, 0, 0);
            if (status == 1) return null;
            check(status, 0);
            var tokens = output();
            ArrayList<Runnable> tasks;
            try { tasks = new ArrayList<Runnable>(outputCount); }
            catch (RuntimeException | Error e) { discardOutput(); throw e; }
            for (int i = 0; i < outputCount; i++) tasks.add(unpin(tokens.getAtIndex(ValueLayout.JAVA_LONG, i)));
            return new ChunkTaskPriorityQueue.TasksForChunk(metadata.getAtIndex(ValueLayout.JAVA_LONG, 2), tasks);
        } finally { Reference.reachabilityFence(this); }
    }
    int pinnedCount() { int count = 0; for (Object task : references) if (task != null) count++; return count; }
    int referenceCapacity() { return references.length; }
}
