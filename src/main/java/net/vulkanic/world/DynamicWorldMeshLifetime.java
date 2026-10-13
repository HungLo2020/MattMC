package net.vulkanic.world;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;

/** Rust-owned semantic mesh usage, retirement order and transactional history.
 * Java projects retirement identities for the existing GAL asset update. This
 * owner never releases GPU resources and its frame grace is not a GPU fence. */
final class DynamicWorldMeshLifetime {
    private static MethodHandle bind(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_mesh_lifetime_" + name, descriptor);
    }
    private static final MethodHandle CREATE = bind("create", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = bind("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    // One bounded identity update, no locks or callbacks in Rust.
    private static final MethodHandle OBSERVE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_mesh_lifetime_observe", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), Linker.Option.critical(false));
    private static final MethodHandle OBSERVE_RIG = bind("observe_rig", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle CHECKPOINT = bind("checkpoint", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle ROLLBACK = bind("rollback", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle DISCARD = bind("discard", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle FORGET = bind("forget", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle SIZE = bind("size", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle RETIRE = bind("retire", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle RETIREMENTS = bind("retirements", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private final MemorySegment owner;

    DynamicWorldMeshLifetime() {
        try {
            MemorySegment pointer = (MemorySegment)CREATE.invokeExact();
            if (pointer.address() == 0) throw new IllegalStateException("Missing mesh lifetime owner");
            try { owner = pointer.reinterpret(0, Arena.ofAuto(), DynamicWorldMeshLifetime::release); }
            catch (Throwable failure) { release(pointer); throw failure; }
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw failed(failure); }
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); }
        catch (Throwable failure) { throw failed(failure); }
    }
    private static IllegalStateException failed(Throwable cause) { return new IllegalStateException("Native mesh lifetime call failed", cause); }

    synchronized Checkpoint checkpoint(long semanticFrame) {
        try {
            long count = (long)CHECKPOINT.invokeExact(owner, semanticFrame);
            if (count < 0) throw new IllegalArgumentException("dynamic mesh checkpoint frame must be positive");
            return new Checkpoint(semanticFrame, count);
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    synchronized void discardCheckpointHistory() {
        try { DISCARD.invokeExact(owner); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    synchronized void rollbackTo(Checkpoint checkpoint) {
        try {
            if (checkpoint == null || (int)ROLLBACK.invokeExact(owner, checkpoint.frame(), checkpoint.changeCount()) != 0)
                throw new IllegalArgumentException("dynamic mesh checkpoint is outside the current semantic frame");
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    synchronized void observe(long meshKey, long meshGeneration, long semanticFrame) {
        try {
            if ((int)OBSERVE.invokeExact(owner, meshKey, meshGeneration, semanticFrame) != 0)
                throw new IllegalArgumentException("dynamic mesh lifetime identity must be positive");
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    /** Uses the mesh identities already retained by Rust's registered rig. */
    synchronized void observeRig(long rigId, long semanticFrame) {
        try {
            if ((int)OBSERVE_RIG.invokeExact(owner, rigId, semanticFrame) != 0)
                throw new IllegalArgumentException("missing rig or invalid dynamic mesh frame");
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    synchronized List<Retirement> retireBeforeFrame(long semanticFrame) {
        try {
            int count = (int)RETIRE.invokeExact(owner, semanticFrame);
            if (count < 0) throw new IllegalArgumentException("dynamic mesh lifetime frame must be positive");
            if (count == 0) return List.of();
            MemorySegment records = ((MemorySegment)RETIREMENTS.invokeExact(owner)).reinterpret((long)count * 16);
            var result = new ArrayList<Retirement>(count);
            for (int i = 0; i < count; i++) result.add(new Retirement(records.get(ValueLayout.JAVA_LONG, (long)i * 16),
                records.get(ValueLayout.JAVA_LONG, (long)i * 16 + 8)));
            return result;
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    synchronized void forget(long meshKey) {
        try { FORGET.invokeExact(owner, meshKey); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    synchronized int size() {
        try { return (int)SIZE.invokeExact(owner); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    record Retirement(long meshKey, long meshGeneration) {}
    record Checkpoint(long frame, long changeCount) {}
}
