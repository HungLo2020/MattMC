package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;

/** Rust-owned light generations with leased read-only CPU views. */
final class NativeLightLayer {
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle COPY = handle("copy", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle REPEAT = handle("repeat", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle IMPORT = handle("import", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS), Linker.Option.critical(true));
    private static final MethodHandle SET = handle("set", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle FILL = handle("fill", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle MATERIALIZE = handle("materialize", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle INSTALL_RESULT = net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_light_result_install", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle SKY_OWNED = net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_light_sky_owned", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS), Linker.Option.critical(true));
    private static final MethodHandle SKY_QUEUED = net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_light_sky_queued", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle INVALIDATE = handle("invalidate", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE_VIEW = handle("view_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    private static MethodHandle handle(String suffix, FunctionDescriptor descriptor, Linker.Option... options) {
        return net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_light_layer_" + suffix, descriptor, options);
    }
    private MemorySegment owner;
    /** Out-slot for a replaced view; read right after each call on the same thread. */
    private static final ThreadLocal<MemorySegment> CHANGED_VIEW = ThreadLocal.withInitial(() -> Arena.global().allocate(ValueLayout.ADDRESS));
    private Projection view;

    private NativeLightLayer() {}
    private NativeLightLayer adopt(MemorySegment owner, MemorySegment view) {
        if (owner.address() == 0 || view.address() == 0) throw new IllegalStateException("No light owner/projection");
        try {
            // Callers pin the layer by holding this segment (DataLayer -> NativeLightLayer -> owner),
            // so it is the batched reclaimer's referent; every call fences this layer.
            this.owner = owner.reinterpret(1).asReadOnly();
            NativeReclaimer.register(this.owner, owner, NativeReclaimer.LIGHT_LAYER);
        } catch (RuntimeException | Error failure) {
            releaseOwner(owner);
            releaseView(view);
            throw failure;
        }
        this.view = Projection.adoptOwned(view);
        return this;
    }
    /** Consumes both unique leases, including on projection/allocation failure. */
    static NativeLightLayer adoptOwned(MemorySegment owner, MemorySegment view) {
        NativeLightLayer result;
        try { result = new NativeLightLayer(); }
        catch (RuntimeException | Error failure) { releaseOwner(owner); releaseView(view); throw failure; }
        return result.adopt(owner, view);
    }
    static NativeLightLayer create(int value) {
        NativeLightLayer result = new NativeLightLayer();
        try (Arena output = Arena.ofConfined()) {
            MemorySegment view = output.allocate(ValueLayout.ADDRESS);
            MemorySegment owner = (MemorySegment) CREATE.invokeExact(value, view);
            return result.adopt(owner, view.get(ValueLayout.ADDRESS, 0));
        } catch (Throwable failure) { throw rethrow(failure); }
    }
    static NativeLightLayer importBytes(byte[] bytes) {
        NativeLightLayer result = new NativeLightLayer();
        try (Arena output = Arena.ofConfined()) {
            MemorySegment view = output.allocate(ValueLayout.ADDRESS);
            MemorySegment owner = (MemorySegment) IMPORT.invokeExact(MemorySegment.ofArray(bytes), bytes.length, view);
            return result.adopt(owner, view.get(ValueLayout.ADDRESS, 0));
        } catch (Throwable failure) { throw rethrow(failure); }
    }
    NativeLightLayer copy() { return duplicate(COPY); }
    NativeLightLayer repeatFirst() { return duplicate(REPEAT); }
    private NativeLightLayer duplicate(MethodHandle operation) {
        NativeLightLayer resultLayer = new NativeLightLayer();
        try (Arena output = Arena.ofConfined()) {
            MemorySegment result = output.allocate(ValueLayout.ADDRESS);
            MemorySegment copied = (MemorySegment) operation.invokeExact(owner, result);
            return resultLayer.adopt(copied, result.get(ValueLayout.ADDRESS, 0));
        } catch (Throwable failure) { throw rethrow(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    /** The thread's out-slot, cleared before a call that may replace the view. */
    private static MemorySegment changedView() {
        var slot = CHANGED_VIEW.get();
        slot.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        return slot;
    }
    private void updateView(int status) {
        if (status != 0) throw new IllegalArgumentException("Invalid native light input");
        MemorySegment changed = CHANGED_VIEW.get().get(ValueLayout.ADDRESS, 0);
        if (changed.address() != 0) view = Projection.adoptOwned(changed);
    }
    void set(int index, int value) {
        try { updateView((int) SET.invokeExact(owner, index, value, changedView())); }
        catch (Throwable failure) { throw rethrow(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    void fill(int value) {
        try { updateView((int) FILL.invokeExact(owner, value, changedView())); }
        catch (Throwable failure) { throw rethrow(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    void materialize() {
        try { updateView((int) MATERIALIZE.invokeExact(owner, changedView())); }
        catch (Throwable failure) { throw rethrow(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    void installResult(long engine, int index) {
        try { updateView((int) INSTALL_RESULT.invokeExact(engine, index, owner, changedView())); }
        catch (Throwable failure) { throw rethrow(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    void seedSky(int[] columns, int bottom, int minX, int minZ, long[] entries, int[] output) {
        if (columns.length != 1280 || entries.length != 8192 || output.length != 3)
            throw new IllegalArgumentException("Invalid sky source spans");
        try { updateView((int) SKY_OWNED.invokeExact(owner, MemorySegment.ofArray(columns), bottom, minX, minZ,
                MemorySegment.ofArray(entries), MemorySegment.ofArray(output), changedView())); }
        catch (Throwable failure) { throw rethrow(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    void seedSkyQueued(MemorySegment columns, int bottom, int minX, int minZ,
            net.minecraft.world.level.lighting.NativeLightQueues queues, MemorySegment output) {
        if (columns.byteSize() != 1280 * 4L || output.byteSize() != 3 * 4L)
            throw new IllegalArgumentException("Invalid sky source spans");
        try { updateView((int) SKY_QUEUED.invokeExact(owner, columns, bottom, minX, minZ,
                queues.ownerForNativeCall(), output, changedView())); }
        catch (Throwable failure) { throw rethrow(failure); }
        finally { Reference.reachabilityFence(queues); Reference.reachabilityFence(this); }
    }
    void invalidate() {
        try { INVALIDATE.invokeExact(owner); }
        catch (Throwable failure) { throw rethrow(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    Projection view() { return view; }
    MemorySegment ownerForNativeCall() { return owner; }

    private static void releaseOwner(MemorySegment owner) {
        try { RELEASE.invokeExact(owner); }
        catch (Throwable failure) { throw rethrow(failure); }
    }
    private static void releaseView(MemorySegment view) {
        try { RELEASE_VIEW.invokeExact(view); }
        catch (Throwable failure) { throw rethrow(failure); }
    }
    private static RuntimeException rethrow(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) return exception;
        return new IllegalStateException(failure);
    }
    /**
     * A leased read-only generation. The metadata segment is the batched
     * reclaimer's referent: native consumers hold it (terrain light views),
     * and every read here fences this projection, which holds it. A view keeps
     * its generation after its owner is released.
     */
    static final class Projection {
        private final MemorySegment metadata;
        private final MemorySegment bytes;
        static Projection adoptOwned(MemorySegment pointer) {
            MemorySegment metadata;
            try {
                metadata = pointer.reinterpret(16).asReadOnly();
                NativeReclaimer.register(metadata, pointer, NativeReclaimer.LIGHT_VIEW);
            } catch (RuntimeException | Error failure) {
                releaseView(pointer);
                throw failure;
            }
            return new Projection(metadata);
        }
        private Projection(MemorySegment metadata) {
            this.metadata = metadata;
            MemorySegment address = metadata.get(ValueLayout.ADDRESS, 8);
            bytes = address.address() == 0 ? null : address.reinterpret(2048).asReadOnly();
        }
        int rawDefault() {
            try { return metadata.get(ValueLayout.JAVA_INT, 0); } finally { Reference.reachabilityFence(this); }
        }
        boolean allocated() {
            try { return metadata.get(ValueLayout.JAVA_INT, 4) != 0; } finally { Reference.reachabilityFence(this); }
        }
        int get(int index) {
            try {
                if (bytes == null) return metadata.get(ValueLayout.JAVA_INT, 0);
                return (bytes.get(ValueLayout.JAVA_BYTE, index >> 1) >> ((index & 1) << 2)) & 15;
            } finally { Reference.reachabilityFence(this); }
        }
        /** The 2048 light bytes, copied while this projection is held. */
        byte[] copyBytes() {
            try { return bytes.toArray(ValueLayout.JAVA_BYTE); } finally { Reference.reachabilityFence(this); }
        }
        MemorySegment bytes() { return bytes; }
        MemorySegment metadata() { return metadata; }
    }
}
