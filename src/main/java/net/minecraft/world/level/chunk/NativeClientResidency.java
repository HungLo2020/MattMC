package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.ArrayDeque;
import java.util.function.LongConsumer;
import net.minecraft.core.SectionPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/** Bounded CPU adapter: Rust policy, Java object bindings and callback delivery. */
public final class NativeClientResidency {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_client_residency_" + name, descriptor);
    }
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle VIEW = handle("view", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle IDENTITY = handle("identity", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle CHANGE = handle("change", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle EMPTY = handle("empty", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle SCAN = handle("scan", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle SNAPSHOT = handle("snapshot", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle SNAPSHOT_VALUE = handle("snapshot_value", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle SNAPSHOT_COPY = handle("snapshot_copy", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle SNAPSHOT_RELEASE = handle("snapshot_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle RANGE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_client_residency_range",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final ThreadLocal<ArrayDeque<long[]>> RANGE_BUFFERS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final VarHandle INT = ValueLayout.JAVA_INT.varHandle(), LONG = ValueLayout.JAVA_LONG.varHandle();
    private static final ThreadLocal<MemorySegment> RESULT = ThreadLocal.withInitial(() -> Arena.ofAuto().allocate(16, 8));
    private final MemorySegment owner, view;
    @Nullable private LongOpenHashSet escaped;

    @Nullable public static NativeClientResidency create(int radius) {
        try {
            var pointer = (MemorySegment)CREATE.invokeExact(radius);
            if (pointer.address() == 0) return null;
            boolean attached = false;
            try {
                var view = (MemorySegment)VIEW.invokeExact(pointer);
                var scoped = pointer.reinterpret(1, Arena.ofAuto(), NativeClientResidency::release);
                attached = true;
                return new NativeClientResidency(scoped, view.reinterpret(32).asReadOnly());
            } finally { if (!attached) release(pointer); }
        } catch (Throwable failure) { throw failure(failure); }
    }
    private NativeClientResidency(MemorySegment owner, MemorySegment view) { this.owner = owner; this.view = view; }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable failure) { throw failure(failure); }
    }
    public int count() { try { return (int)INT.getAcquire(view, 0L); } finally { Reference.reachabilityFence(this); } }
    public int centerX() { try { return (int)INT.getAcquire(view, 4L); } finally { Reference.reachabilityFence(this); } }
    public int centerZ() { try { return (int)INT.getAcquire(view, 8L); } finally { Reference.reachabilityFence(this); } }
    public long fingerprint() { try { return (long)LONG.getAcquire(view, 16L); } finally { Reference.reachabilityFence(this); } }
    public void center(int x, int z) { change(3, 0, x, z); }
    /** Call immediately after atomic Java-reference publication, before callbacks. */
    public long publish(int index, boolean present) { return change(0, index, present ? 1 : 0, 0); }
    /** Immutable stock coordinates join slot publication before unload callbacks. */
    public long publish(int index, @Nullable LevelChunk chunk) {
        if (chunk == null || chunk.getClass() != LevelChunk.class) return publish(index, chunk != null);
        var pos = chunk.getPos();
        return change(5, index, pos.x, pos.z);
    }
    private long revision() {
        try { return (long)LONG.getAcquire(view, 24L); }
        finally { Reference.reachabilityFence(this); }
    }
    public Range range(int minX, int maxX, int minZ, int maxZ) { return new Range(minX, maxX, minZ, maxZ); }
    /** Bounded borrowed CPU pages, revalidated after every external callback. */
    public final class Range implements AutoCloseable {
        private final int minX, maxX, minZ, maxZ;
        private long[] buffer;
        private int offset, count;
        private long pageRevision;
        private boolean loaded;
        public int x, z;
        private Range(int minX, int maxX, int minZ, int maxZ) {
            this.minX=minX; this.maxX=maxX; this.minZ=minZ; this.maxZ=maxZ;
            buffer=RANGE_BUFFERS.get().pollFirst();
            if (buffer==null) buffer=new long[194];
        }
        /** -1: exhausted; -2: preserve live custom getters at this coordinate. */
        public int next(int cursorX, int cursorZ) {
            if (buffer==null) throw new IllegalStateException("Closed residency range");
            if (!loaded || pageRevision!=revision() || offset==count && count==64) {
                try {
                    int status=(int)RANGE.invokeExact(owner,minX,maxX,minZ,maxZ,cursorX,cursorZ,
                        MemorySegment.ofArray(buffer),buffer.length);
                    if (status==-2) { loaded=false; return -2; }
                    if (status<0) throw new IllegalStateException("Invalid native residency range");
                    count=status; offset=0; pageRevision=buffer[1]; loaded=true;
                } catch (Throwable failure) { throw failure(failure); }
                finally { Reference.reachabilityFence(NativeClientResidency.this); }
            }
            if (offset==count) return -1;
            int at=2+3*offset++;
            x=(int)buffer[at+1]; z=(int)buffer[at+2];
            return (int)buffer[at];
        }
        @Override public void close() {
            if (buffer!=null) {
                var pool=RANGE_BUFFERS.get();
                if (pool.size()<4) pool.addFirst(buffer);
                buffer=null;
            }
        }
    }
    public void dropped(int index) { change(1, index, 0, 0); }
    public void admitted() { change(2, 0, 0, 0); }
    public long identity(int index) {
        try { return (long)IDENTITY.invokeExact(owner, index); }
        catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    public void position(int x, int z) { change(4, 0, x, z); }
    private long change(int action, int index, int a, int b) {
        try {
            long value = (long)CHANGE.invokeExact(owner, action, index, a, b);
            if (value < 0) throw new IllegalStateException("Invalid native client residency transition");
            return value;
        } catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    public boolean empty(long key, boolean empty) {
        if (escaped != null) return empty ? escaped.add(key) : escaped.remove(key);
        try {
            int status = (int)EMPTY.invokeExact(owner, key, empty ? 1 : 0);
            if (status < 0) throw new IllegalStateException("Invalid native empty-section transition");
            return status != 0;
        } catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    /** Public mutable escape transfers the exact physical table once. */
    public LongOpenHashSet emptySections() {
        if (escaped == null) escaped = snapshot(true);
        return escaped;
    }
    /** Independent verification reader; does not transfer authority. */
    LongOpenHashSet snapshot() { return escaped == null ? snapshot(false) : escaped.clone(); }
    private LongOpenHashSet snapshot(boolean detach) {
        MemorySegment snapshot = MemorySegment.NULL;
        try (Arena call = Arena.ofConfined()) {
            snapshot = (MemorySegment)SNAPSHOT.invokeExact(owner, 0);
            if (snapshot.address() == 0) throw new IllegalStateException("Cannot snapshot native empty sections");
            int n = Math.toIntExact((long)SNAPSHOT_VALUE.invokeExact(snapshot, 0));
            int size = Math.toIntExact((long)SNAPSHOT_VALUE.invokeExact(snapshot, 1));
            boolean zero = (long)SNAPSHOT_VALUE.invokeExact(snapshot, 2) != 0;
            var keys = call.allocate(n * 8L, 8);
            if ((int)SNAPSHOT_COPY.invokeExact(snapshot, keys, n) != 0) throw new IllegalStateException("Cannot copy native empty-section table");
            var result = new DetachedSet(n, size, zero, keys);
            if (detach) {
                // Construct the Java table first: allocation failure must leave Rust authoritative.
                var committed = (MemorySegment)SNAPSHOT.invokeExact(owner, 1);
                if (committed.address() == 0) throw new IllegalStateException("Cannot transfer empty sections");
                SNAPSHOT_RELEASE.invokeExact(committed);
            }
            return result;
        } catch (Throwable failure) { throw failure(failure); }
        finally {
            if (snapshot.address() != 0) {
                try { SNAPSHOT_RELEASE.invokeExact(snapshot); } catch (Throwable failure) { throw failure(failure); }
            }
            Reference.reachabilityFence(this);
        }
    }
    private static final class DetachedSet extends LongOpenHashSet {
        DetachedSet(int n, int size, boolean zero, MemorySegment keys) {
            super(); // Frozen default load factor and final minimum capacity.
            this.n = n; this.mask = n - 1; this.maxFill = Math.min((n * 3 + 3) / 4, n - 1);
            this.size = size; this.containsNull = zero; this.key = new long[n + 1];
            MemorySegment.copy(keys, ValueLayout.JAVA_LONG, 0, this.key, 0, n);
        }
    }
    private static boolean stockGeometry(LevelChunk chunk) {
        return chunk.getClass() == LevelChunk.class && chunk.getLevel() != null
            && chunk.getLevel().getClass() == ClientLevel.class && chunk.levelHeightAccessor == chunk.getLevel();
    }
    @Nullable private static NativeDhChunkInputs inputs(LevelChunk chunk, LevelChunkSection[] sections) {
        if (!stockGeometry(chunk) || chunk.getSectionsForRead() != sections) return null;
        for (var section : sections) if (section == null || section.getClass() != LevelChunkSection.class) return null;
        return chunk.nativeDhSections();
    }
    private int scan(@Nullable NativeDhChunkInputs inputs, int index, long id, int x, int z, int min, int count, int cursor, int mode, MemorySegment result) {
        try {
            var pointer = inputs == null ? MemorySegment.NULL : inputs.stageOwner();
            int status = (int)SCAN.invokeExact(owner, pointer, index, id, x, z, min, count, cursor, mode, result);
            if (status < 0) throw new IllegalStateException("Invalid native empty-section scan");
            return status;
        } catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(inputs); Reference.reachabilityFence(this); }
    }
    public void dropEmpty(LevelChunk chunk) {
        var sections = chunk.getSectionsForRead();
        if (escaped == null && stockGeometry(chunk) && sections.length <= 256) {
            var pos = chunk.getPos();
            scan(null, -1, 0, pos.x, pos.z, chunk.getMinSectionY(), sections.length, 0, 0, MemorySegment.NULL);
            return;
        }
        for (int i = 0; i < sections.length; i++) {
            var pos = chunk.getPos(); empty(SectionPos.asLong(pos.x, chunk.getSectionYFromSectionIndex(i), pos.z), false);
        }
    }
    public void addEmpty(LevelChunk chunk, int index, long id) {
        var sections = chunk.getSectionsForRead();
        var inputs = escaped == null ? inputs(chunk, sections) : null;
        if (inputs != null) {
            var pos = chunk.getPos();
            scan(inputs, index, id, pos.x, pos.z, inputs.minY() >> 4, sections.length, 0, 1, RESULT.get());
            return;
        }
        scan(null, index, id, 0, 0, 0, 0, 0, 3, MemorySegment.NULL);
        for (int i = 0; i < sections.length; i++) {
            if (sections[i].hasOnlyAir()) {
                var pos = chunk.getPos(); empty(SectionPos.asLong(pos.x, chunk.getSectionYFromSectionIndex(i), pos.z), true);
            }
        }
    }
    public void refresh(LevelChunk chunk, int index, long id, LongConsumer callback) {
        var pos = chunk.getPos(); var sections = chunk.getSectionsForRead();
        int cursor = 0;
        while (cursor < sections.length) {
            // Callbacks may replace a captured array entry, counter owner or container.
            var inputs = escaped == null ? inputs(chunk, sections) : null;
            if (inputs == null) {
                scan(null, index, id, 0, 0, 0, 0, 0, 3, MemorySegment.NULL);
                for (int i = cursor; i < sections.length; i++) {
                    long key = SectionPos.asLong(pos.x, chunk.getSectionYFromSectionIndex(i), pos.z);
                    if (sections[i].hasOnlyAir()) empty(key, true);
                    else if (empty(key, false)) callback.accept(key);
                }
                return;
            }
            var result = RESULT.get();
            if (scan(inputs, index, id, pos.x, pos.z, inputs.minY() >> 4, sections.length, cursor, 2, result) == 0) return;
            long key = result.get(ValueLayout.JAVA_LONG, 0);
            cursor = Math.toIntExact(result.get(ValueLayout.JAVA_LONG, 8));
            callback.accept(key); // No native lock/borrow crosses the Java callback.
        }
    }
    private static RuntimeException failure(Throwable failure) {
        if (failure instanceof RuntimeException exception) return exception;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Native client residency failed", failure);
    }
}
