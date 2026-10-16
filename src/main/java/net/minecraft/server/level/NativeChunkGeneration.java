package net.minecraft.server.level;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.Consumer;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.jetbrains.annotations.Nullable;

/** Reachability and engine-effect adapter for Rust's retained generation control. */
public final class NativeChunkGeneration {
    private NativeChunkGeneration() {}

    private static MethodHandle handle(String name, FunctionDescriptor descriptor, Linker.Option... options) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_generation_" + name, descriptor, options);
    }
    private static final MethodHandle CREATE = handle("holder_create", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = handle("holder_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle HEADER = handle("holder_header", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle FIELDS = handle("holder_fields", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle STATUS_RESERVE = handle("holder_status_reserve", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    private static final MethodHandle STATUS_CHANGE = handle("holder_status_change", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    private static final MethodHandle CHANGE = handle("holder_change", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    private static final MethodHandle FUTURE_RESERVE = handle("holder_future_reserve", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    private static final MethodHandle ALLOWED_RESERVE = handle("holder_allowed_reserve", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    private static final MethodHandle FUTURE_PUBLISH = handle("holder_future_publish", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), Linker.Option.critical(false));
    private static final MethodHandle LEVELS_INIT = handle("holder_levels_init", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    private static final MethodHandle LEVEL_CHANGE = handle("holder_level_change", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    private static final MethodHandle DEPENDENCY_COMMAND = handle("holder_dependency_command", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    private static final MethodHandle LIFECYCLE = handle("holder_lifecycle", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle FUTURE_DRIVER = handle("holder_future_driver", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle PREPARE_CHUNK = handle("prepare_chunk", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final VarHandle INT = ValueLayout.JAVA_INT.varHandle();
    @FunctionalInterface interface LifecycleEffect { long apply(int op, int slot, int kind); }
    @FunctionalInterface interface FutureEffect { long apply(int op, int slot); }
    @FunctionalInterface interface DispatchEffect { long apply(int op); }
    private static final MethodHandle STATISTICS = handle("statistics", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final VarHandle LONG = ValueLayout.JAVA_LONG.varHandle();
    private static final List<ChunkStatus> STATUSES = ChunkStatus.getStatusList();

    // One non-owning read capability exposes the non-closeable scope as a
    // constant. It never escapes. Each access is first checked against its
    // bounded native projection, and its enclosing auto-arena owner remains
    // strongly reachable through the caller's finally/fence. This capability
    // neither allocates at access time nor grants reclamation authority.
    private static final MemorySegment ATOMIC_READS = MemorySegment.NULL.reinterpret(Long.MAX_VALUE).asReadOnly();

    private static long readWord(long address, long bytes, long offset) {
        Objects.checkFromIndexSize(offset, (long)Long.BYTES, bytes);
        return (long)LONG.getVolatile(ATOMIC_READS, address + offset);
    }


    /** Identity admission must not invoke foreign getIndex/equals/hashCode. */
    private static int canonicalIndex(@Nullable ChunkStatus status) {
        for (int index = 0; index < STATUSES.size(); index++) if (STATUSES.get(index) == status) return index;
        return -1;
    }

    /** During publication retain the old leaf as well as the pending one.
     * A paused writer must not make readers of the still-published old word spin. */
    private record StatusReference(long token, ChunkStatus value, @Nullable StatusReference previous) {}

    private static RuntimeException failure(Throwable throwable) {
        if (throwable instanceof RuntimeException runtime) return runtime;
        if (throwable instanceof Error error) throw error;
        return new IllegalStateException("Native chunk generation call failed", throwable);
    }

    /** Handle-only, idempotent cleanup. JDK25 registers reinterpret's cleanup
     * before allocating the returned segment; that allocation can still fail.
     * Constructor rollback and eventual arena cleanup must share this action. */
    private static final class NativeRelease implements Consumer<MemorySegment>, Runnable {
        private static final VarHandle HANDLE;
        static {
            try { HANDLE = MethodHandles.lookup().findVarHandle(NativeRelease.class, "handle", long.class); }
            catch (ReflectiveOperationException exception) { throw new ExceptionInInitializerError(exception); }
        }
        private volatile long handle;
        private final MethodHandle function;
        private final boolean ticketOwner;
        NativeRelease(long handle, MethodHandle function) { this(handle, function, false); }
        NativeRelease(long handle, MethodHandle function, boolean ticketOwner) {
            this.handle = handle; this.function = function; this.ticketOwner = ticketOwner;
        }
        public void accept(MemorySegment ignored) { run(); }
        public void run() {
            long owned = (long)HANDLE.getAndSet(this, 0L);
            if (owned != 0) {
                releaseHandle(function, owned);
                if (ticketOwner) net.minecraft.world.level.TicketStorage.finishNativeTicketRelease();
            }
        }
    }

    private static void releaseHandle(MethodHandle function, long handle) {
        try { function.invokeExact(handle); }
        catch (Throwable throwable) { throw failure(throwable); }
    }

    static MemorySegment adopt(long handle, long size, Arena arena, MethodHandle function) {
        return adopt(handle, size, arena, function, false);
    }
    private static MemorySegment adopt(long handle, long size, Arena arena, MethodHandle function, boolean ticketOwner) {
        NativeRelease cleanup;
        try { cleanup = ticketOwner ? new NativeRelease(handle, function, true) : new NativeRelease(handle, function); }
        catch (Throwable throwable) {
            releaseHandle(function, handle);
            if (ticketOwner) net.minecraft.world.level.TicketStorage.finishNativeTicketRelease();
            throw failure(throwable);
        }
        try { return MemorySegment.ofAddress(handle).reinterpret(size, arena, cleanup); }
        catch (Throwable throwable) { cleanup.run(); throw failure(throwable); }
    }

    /** VM resources and effects for one native range dependency owner. */
    private static final class RangeDependencies {
        private static final MethodHandle CREATE = handle("range_create", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        private static final MethodHandle RELEASE = handle("range_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
        private static final MethodHandle STATISTICS = handle("range_statistics", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        private static final MethodHandle EVENT = handle("range_event", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), Linker.Option.critical(false));
        private static final MethodHandle CLAIM = handle("range_claim", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), Linker.Option.critical(false));
        private static final MethodHandle INPUT = handle("range_input", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), Linker.Option.critical(false));
        private static final MethodHandle PREPARE = handle("range_prepare", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), Linker.Option.critical(false));
        private static final MethodHandle COMMIT = handle("range_commit", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), Linker.Option.critical(false));
        private static final MethodHandle NEXT = handle("range_next", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), Linker.Option.critical(false));
        private static final VarHandle ERROR = MethodHandles.arrayElementVarHandle(Throwable[].class);
        final long id;
        final MemorySegment owner;
        volatile List<? extends CompletableFuture<?>> prerequisites;
        final Throwable[] errors;
        final CompletableFuture<Void> ready = newChunkFuture();

        RangeDependencies(List<? extends CompletableFuture<?>> prerequisites, boolean fence) {
            this.prerequisites = prerequisites;
            errors = new Throwable[prerequisites.size()];
            Arena arena = Arena.ofAuto();
            long created;
            try { created = (long)CREATE.invokeExact(prerequisites.size(), fence ? 1 : 0); }
            catch (Throwable throwable) { throw failure(throwable); }
            if (created == 0) throw new OutOfMemoryError("Native chunk range allocation failed");
            id = created;
            owner = adopt(created, 1, arena, RELEASE);
        }

        private void publish(long command) {
            if (command == 0) return;
            int operation = (int)(command >>> 32);
            Throwable error = operation == 3 ? (Throwable)ERROR.getAcquire(errors, (int)command) : null;
            if (operation == 2 || operation == 3) releaseBindings();
            if (operation == 3) ready.completeExceptionally(error);
            else ready.complete(null);
        }

        private void releaseBindings() {
            prerequisites = List.of();
            Arrays.fill(errors, null);
        }

        private Throwable error(int input) {
            if (input >= 0) return (Throwable)ERROR.getAcquire(errors, input);
            // Read the direct future's current completion without invoking any
            // overridable leaf query/registration method. Internal nodes use
            // their frozen VM error binding; direct leaves can still be obtruded.
            return CompletableFuture.allOf(prerequisites.get(-input - 1)).handle((value, error) -> error).join();
        }

        void signal(int index) {
            try {
                publish((long)EVENT.invokeExact(id, index));
                long claimed;
                while ((claimed = (long)CLAIM.invokeExact(id)) != 0) {
                    int node = (int)(claimed - 1);
                    Throwable left = error((int)INPUT.invokeExact(id, node, 0));
                    Throwable right = error((int)INPUT.invokeExact(id, node, 1));
                    int selection = (int)PREPARE.invokeExact(id, node, left == null ? 0 : 1, right == null ? 0 : 1);
                    if (selection < 0) throw new IllegalStateException("Native range node lost its publication claim");
                    ERROR.setRelease(errors, node, selection == 1 ? left : selection == 2 ? right : null);
                    left = null;
                    right = null;
                    publish((long)COMMIT.invokeExact(id, node));
                }
            } catch (Throwable throwable) { ready.completeExceptionally(throwable); }
            finally { Reference.reachabilityFence(this); }
        }

        ChunkResult<ChunkAccess>[] join() {
            ChunkResult<ChunkAccess>[] results = new ChunkResult[prerequisites.size()];
            try {
                while (true) {
                    long command = (long)NEXT.invokeExact(id, 0);
                    switch ((int)(command >>> 32)) {
                        case 1: results[(int)command] = (ChunkResult<ChunkAccess>)prerequisites.get((int)command).join(); break;
                        case 2: releaseBindings(); return results;
                        default: throw new IllegalStateException("Invalid native range join command");
                    }
                }
            } catch (Throwable throwable) { return raise(throwable); }
            finally { Reference.reachabilityFence(this); }
        }

        ChunkResult<List<ChunkAccess>> collect(ChunkMap map, ChunkResult<List<ChunkAccess>> unloaded,
                ChunkResult<ChunkAccess>[] results) {
            List<ChunkAccess> chunks = new ArrayList<>(results.length);
            int present = 0;
            try {
                while (true) {
                    long command = (long)NEXT.invokeExact(id, present);
                    int row = (int)command;
                    switch ((int)(command >>> 32)) {
                        case 3:
                            ChunkResult<ChunkAccess> result = results[row];
                            if (result == null) throw map.debugFuturesAndCreateReportedException(
                                new IllegalStateException("At least one of the chunk futures were null"), "n/a");
                            ChunkAccess chunk = result.orElse(null);
                            present = chunk == null ? 0 : 1;
                            if (chunk != null) chunks.add(chunk);
                            break;
                        case 4: return ChunkResult.of(chunks);
                        case 5: return unloaded;
                        default: throw new IllegalStateException("Invalid native range dependency command");
                    }
                }
            } catch (Throwable throwable) { return raise(throwable); }
            finally { Reference.reachabilityFence(this); }
        }

        void register() {
            // One-leaf signals do not dispatch a prerequisite's overridable
            // registration/query methods. Rust owns the dependency tree.
            for (int index = 0; index < prerequisites.size(); index++) {
                int row = index;
                CompletableFuture.allOf(prerequisites.get(index)).whenComplete((value, error) -> signal(row));
            }
            signal(-1);
        }
    }

    static CompletableFuture<ChunkResult<List<ChunkAccess>>> rangeDependencies(ChunkMap map,
            List<CompletableFuture<ChunkResult<ChunkAccess>>> prerequisites, ChunkResult<List<ChunkAccess>> unloaded) {
        RangeDependencies range = new RangeDependencies(prerequisites, false);
        range.register();
        // The public result can be externally completed before prerequisites.
        // Keep the earlier join stage independent: the original sequence still
        // joins its actual leaves even when the later result stage is skipped.
        return range.ready.thenApply(value -> range.join()).thenApply(results -> range.collect(map, unloaded, results));
    }

    private static final BiFunction<Object, Object, Void> FENCE_COMBINER = (a, b) -> null;

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <A, B> BiFunction<A, B, Void> fenceCombiner() { return (BiFunction)FENCE_COMBINER; }

    /** Only the chunk engine's own fence operation uses native aggregation.
     * Every public virtual call still occurs; arbitrary user combinators retain
     * their existing CompletableFuture implementation and executor behavior. */
    private static final class ChunkFuture<T> extends CompletableFuture<T> {
        @Override public <U> CompletableFuture<U> newIncompleteFuture() { return newChunkFuture(); }
        @Override public <U, V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other,
                BiFunction<? super T, ? super U, ? extends V> action) {
            if ((Object)action != FENCE_COMBINER || other == null) return super.thenCombine(other, action);
            CompletableFuture<?> actual = other.toCompletableFuture();
            if (actual == null) throw new NullPointerException();
            return (CompletableFuture<V>)combineOwnedChunkDependencies(this, actual);
        }
    }

    static <T> CompletableFuture<T> newChunkFuture() { return new ChunkFuture<>(); }
    static <T> CompletableFuture<T> completedChunkFuture(T value) {
        CompletableFuture<T> future = newChunkFuture();
        future.complete(value);
        return future;
    }

    private static CompletableFuture<Void> combineOwnedChunkDependencies(CompletableFuture<?> first, CompletableFuture<?> second) {
        CompletableFuture<Void> adapter = newChunkFuture();
        RangeDependencies dependencies = new RangeDependencies(List.of(first, second), true);
        dependencies.register();
        dependencies.ready.whenComplete((value, error) -> {
            if (error == null) adapter.complete(null);
            else adapter.completeExceptionally(error);
        });
        return adapter;
    }

    private static final class StageStatistics {
        static final MethodHandle READ = handle("stage_statistics", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    }
    /** Stage admission, engine-dispatch, completion and fatal-report attempts. */
    static long[] stageStatistics() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment output = arena.allocate(4L * Long.BYTES, Long.BYTES);
            StageStatistics.READ.invokeExact(output);
            return output.toArray(ValueLayout.JAVA_LONG);
        } catch (Throwable throwable) { return raise(throwable); }
    }

    /** Owner/vector payload counters, excluding Arc/allocator/VM/process overhead. */
    static long[] rangeStatistics() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment output = arena.allocate(5L * Long.BYTES, Long.BYTES);
            RangeDependencies.STATISTICS.invokeExact(output);
            return output.toArray(ValueLayout.JAVA_LONG);
        } catch (Throwable throwable) { return raise(throwable); }
    }

    static void dispatchTask(ChunkGenerationTask task, boolean run, DispatchEffect effect) {
        Task resource = ChunkGenerationTask.nativeOwner(task);
        long driver = 0;
        try {
            driver = (long)TaskBindings.DISPATCH.invokeExact(resource == null ? 0L : resource.id, run ? 1 : 0);
            if (driver == 0) throw new OutOfMemoryError("Native chunk dispatch continuation allocation failed");
            long response = 0;
            while (true) {
                int result = (int)TaskBindings.DRIVER_NEXT.invokeExact(driver, response);
                if (result == 0) return;
                if (result != 1) throw new IllegalStateException("Native chunk dispatch failed");
                int operation = (int)readWord(driver, 24L, 0L);
                response = effect.apply(operation);
            }
        } catch (Throwable throwable) { NativeChunkGeneration.<RuntimeException, Object>raise(throwable); }
        finally {
            if (driver != 0) releaseHandle(TaskBindings.DRIVER_RELEASE, driver);
            Reference.reachabilityFence(resource);
            Reference.reachabilityFence(task);
        }
    }

    static void prepareChunk(ChunkHolder holder, int kind, int phase, FutureEffect effect) {
        Holder resource = holder == null ? null : GenerationChunkHolder.generationOwner(holder);
        long driver = 0;
        try {
            driver = (long)PREPARE_CHUNK.invokeExact(resource == null ? 0L : resource.id, kind, phase);
            if (driver == 0) throw new OutOfMemoryError("Native chunk preparation continuation allocation failed");
            long response = 0;
            while (true) {
                int result = (int)TaskBindings.DRIVER_NEXT.invokeExact(driver, response);
                if (result == 0) return;
                if (result != 1) throw new IllegalStateException("Native chunk preparation failed");
                long command = readWord(driver, 24L, 0L);
                response = effect.apply((int)command, (int)(command >>> 32));
            }
        } catch (Throwable throwable) { NativeChunkGeneration.<RuntimeException, Object>raise(throwable); }
        finally {
            if (driver != 0) releaseHandle(TaskBindings.DRIVER_RELEASE, driver);
            Reference.reachabilityFence(resource);
            Reference.reachabilityFence(holder);
        }
    }

    /** Live/created holders/tasks, runs/layers, live/created statuses, FULL/stage successes; no memory-size claim. */
    public static long[] statistics() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment output = arena.allocate(12L * Long.BYTES, Long.BYTES);
            STATISTICS.invokeExact(output);
            return output.toArray(ValueLayout.JAVA_LONG);
        } catch (Throwable throwable) { throw failure(throwable); }
    }

    private static final class StatusBindings {
        static final MethodHandle CREATE = handle("status_create", FunctionDescriptor.of(ValueLayout.JAVA_LONG));
        static final MethodHandle RELEASE = handle("status_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
        // One atomic write, no allocation/locks/callbacks.
        static final MethodHandle SET = handle("status_set", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), Linker.Option.critical(false));
        static final MethodHandle RESERVE = handle("status_reserve", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    }

    /** Native-owned persisted status with a volatile shared read and no getter downcall. */
    public static final class Status {
        private final MemorySegment owner;
        private final long id;
        private volatile StatusReference reference = new StatusReference(1L, ChunkStatus.EMPTY, null);
        public Status() {
            Arena arena = Arena.ofAuto();
            long created;
            try { created = (long)StatusBindings.CREATE.invokeExact(); }
            catch (Throwable throwable) { throw failure(throwable); }
            if (created == 0) throw new OutOfMemoryError("Native chunk status allocation failed");
            id = created;
            owner = adopt(created, Long.BYTES, arena, StatusBindings.RELEASE);
            // id is also the immutable value-cell address. Only the actual
            // auto-arena owner and the access fence supply allocation lifetime;
            // an address/tag alone never pins native memory.
        }
        public ChunkStatus get() {
            try {
                while (true) {
                    long word = readWord(id, Long.BYTES, 0L);
                    if (word == 0) return null;
                    StatusReference row = reference;
                    if (row != null && row.token == word) return row.value;
                    if (row != null && row.previous != null && row.previous.token == word) return row.previous.value;
                    Thread.onSpinWait();
                }
            } finally { Reference.reachabilityFence(this); }
        }
        public synchronized void set(@Nullable ChunkStatus status) {
            StatusReference previous = reference;
            StatusReference settled = null;
            long word = 0;
            boolean publishedRoot = false;
            try {
                int index = canonicalIndex(status);
                int identity = status == null ? 0 : index >= 0 ? index + 1 : 65535;
                word = (long)StatusBindings.RESERVE.invokeExact(id, identity);
                if (word == 0) throw new OutOfMemoryError("Native chunk status identity tags exhausted");
                settled = new StatusReference(word, status, null);
                StatusReference pending = new StatusReference(word, status, previous);
                reference = pending;
                publishedRoot = true;
                StatusBindings.SET.invokeExact(id, word);
                reference = settled;
            } catch (Throwable throwable) {
                if (publishedRoot) reference = readWord(id, Long.BYTES, 0L) == word ? settled : previous;
                throw failure(throwable);
            } finally { Reference.reachabilityFence(this); }
        }
    }

    /** One holder incarnation, including after registry detach and id reuse. */
    static final class Holder {
        private final MemorySegment owner;
        private final long id, headerAddress, fieldsAddress;
        private long levelsAddress;
        private static final long HEADER_BYTES = 24L;
        private static final long FIELD_BYTES = (STATUSES.size() + 8L) * Long.BYTES;
        // Native words select two fixed opaque binding locations per field.
        // Java tags validate heap publication/reuse; they cannot select a field
        // or establish membership/completion without its native word.
        private static final int FULL_FIELD = STATUSES.size() - 1;
        private static final int FULL_STATUS_BASE = (STATUSES.size() + 8) * 2;
        static final Object REQUEST_FALLBACK = new Object(), REQUEST_DISALLOWED = new Object();
        private static final VarHandle ROW = MethodHandles.arrayElementVarHandle(Object[].class);
        private static final VarHandle TAG = MethodHandles.arrayElementVarHandle(long[].class);
        private final Object[] references = new Object[FULL_STATUS_BASE + 2];
        private final long[] publicationTags = new long[FULL_STATUS_BASE];
        private volatile StatusReference startedReference;

        Holder() {
            Arena arena = Arena.ofAuto();
            long created;
            try { created = (long)CREATE.invokeExact(STATUSES.size()); }
            catch (Throwable throwable) { throw failure(throwable); }
            if (created == 0) throw new OutOfMemoryError("Native generation holder allocation failed");
            id = created;
            owner = adopt(created, 1, arena, RELEASE);
            try {
                // Immutable Rust layout metadata, never a lifetime owner or
                // copied mutable slot state. The Arc owns both fixed regions.
                headerAddress = ((MemorySegment)HEADER.invokeExact(id)).address();
                fieldsAddress = ((MemorySegment)FIELDS.invokeExact(id)).address();
                exchange(STATUSES.size() + 1, completedChunkFuture(null));
            } catch (Throwable throwable) {
                throw failure(throwable);
            }
        }

        static final int READY_BASE = STATUSES.size() + 2;
        void initializeLifecycle(int level, int unloaded, CompletableFuture<?> unloadedFuture) {
            try {
                levelsAddress = ((MemorySegment)LEVELS_INIT.invokeExact(id, level, unloaded,
                    ChunkLevel.byStatus(FullChunkStatus.FULL), ChunkLevel.byStatus(FullChunkStatus.BLOCK_TICKING),
                    ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING))).address();
                for (int i = 0; i < 3; i++) binding(READY_BASE + i, unloadedFuture);
                for (int i = 3; i < 6; i++) binding(READY_BASE + i, completedChunkFuture(null));
            } catch (Throwable throwable) { throw failure(throwable); }
            finally { Reference.reachabilityFence(this); }
        }
        int level(int field) {
            try {
                java.util.Objects.checkIndex(field, 4);
                return (int)INT.getVolatile(ATOMIC_READS, levelsAddress + field * (long)Integer.BYTES);
            } finally { Reference.reachabilityFence(this); }
        }
        void levelChange(int field, int value) {
            try { LEVEL_CHANGE.invokeExact(id, field, value); }
            catch (Throwable throwable) { throw failure(throwable); }
            finally { Reference.reachabilityFence(this); }
        }
        @SuppressWarnings("unchecked")
        <T> T binding(int slot) { return (T)get(slot); }
        void binding(int slot, Object value) { exchange(slot, value); }
        long dependencyCommand(int operation, boolean done) {
            try { return (long)DEPENDENCY_COMMAND.invokeExact(id, operation, done ? 1 : 0); }
            catch (Throwable t) { return raise(t); }
            finally { Reference.reachabilityFence(this); }
        }
        void lifecycle(int operation, LifecycleEffect effect) {
            long driver = 0;
            try {
                driver = (long)LIFECYCLE.invokeExact(id, operation);
                if (driver == 0) throw new OutOfMemoryError("Native holder lifecycle allocation failed");
                long response = 0;
                while (true) {
                    int result = (int)TaskBindings.DRIVER_NEXT.invokeExact(driver, response);
                    if (result == 0) return;
                    if (result != 1) throw new IllegalStateException("Native holder lifecycle failed");
                    long first = readWord(driver, 24L, 0L), second = readWord(driver, 24L, 8L);
                    response = effect.apply((int)first, (int)(first >>> 32), (int)second);
                }
            } catch (Throwable throwable) { NativeChunkGeneration.<RuntimeException, Object>raise(throwable); }
            finally {
                if (driver != 0) releaseHandle(TaskBindings.DRIVER_RELEASE, driver);
                Reference.reachabilityFence(this);
            }
        }

        void futureControl(int operation, FutureEffect effect) {
            long driver = 0;
            try {
                driver = (long)FUTURE_DRIVER.invokeExact(id, operation);
                if (driver == 0) throw new OutOfMemoryError("Native holder future continuation allocation failed");
                long response = 0;
                while (true) {
                    int result = (int)TaskBindings.DRIVER_NEXT.invokeExact(driver, response);
                    if (result == 0) return;
                    if (result != 1) throw new IllegalStateException("Native holder future control failed");
                    long command = readWord(driver, 24L, 0L);
                    response = effect.apply((int)command, (int)(command >>> 32));
                }
            } catch (Throwable throwable) { NativeChunkGeneration.<RuntimeException, Object>raise(throwable); }
            finally {
                if (driver != 0) releaseHandle(TaskBindings.DRIVER_RELEASE, driver);
                Reference.reachabilityFence(this);
            }
        }

        private long change(int op, int a, int b) {
            try { return (long)CHANGE.invokeExact(id, op, a, b); }
            catch (Throwable throwable) { throw failure(throwable); }
            finally { Reference.reachabilityFence(this); }
        }

        private long statusWord() { return readWord(headerAddress, HEADER_BYTES, 8L); }
        @Nullable ChunkStatus allowed() { return (ChunkStatus)readPayload(FULL_FIELD, true); }
        synchronized void allowed(@Nullable ChunkStatus status) { exchange(FULL_FIELD, null, status, true); }

        private static int bindingRow(int field, long token) { return field * 2 + (int)((token >>> 3) & 1); }

        /** Rust's command and actual FULL membership share one publication.
         * Metadata retry ends before any observable future method. */
        Object fullRequest() {
            try {
                while (true) {
                    long token = readWord(fieldsAddress, FIELD_BYTES, FULL_FIELD * (long)Long.BYTES);
                    if (token == 0) return REQUEST_DISALLOWED;
                    int row = bindingRow(FULL_FIELD, token);
                    if ((long)TAG.getAcquire(publicationTags, row) != token) { Thread.onSpinWait(); continue; }
                    int command = (int)(token >>> 1) & 3;
                    if (command == 0) return REQUEST_DISALLOWED;
                    if (command != 1 || (token & 1) == 0) return REQUEST_FALLBACK;
                    Object value = ROW.getAcquire(references, row);
                    if ((long)TAG.getAcquire(publicationTags, row) == token) return value;
                    Thread.onSpinWait();
                }
            } finally { Reference.reachabilityFence(this); }
        }

        @Nullable ChunkStatus started() {
            try {
                while (true) {
                    StatusReference row = startedReference;
                    long word = statusWord();
                    if (word == 0) return null;
                    if (row != null && row.token == word) return row.value;
                    if (row != null && row.previous != null && row.previous.token == word) return row.previous.value;
                    Thread.onSpinWait();
                }
            } finally { Reference.reachabilityFence(this); }
        }
        @Nullable synchronized ChunkStatus bump(@Nullable ChunkStatus expected, ChunkStatus target) {
            ChunkStatus current = started();
            if (current == expected) publishStarted(target, statusWord());
            return current;
        }
        /** All opaque roots precede native publication. Full tags select the
         * matching Java heap acquire even for canonical and null writes. */
        private void publishStarted(ChunkStatus status, long expected) {
            StatusReference previous = startedReference;
            StatusReference settled = null;
            long word = 0;
            boolean publishedRoot = false;
            try {
                int index = canonicalIndex(status);
                int identity = status == null ? 0 : index >= 0 ? index + 1 : 65535;
                word = (long)STATUS_RESERVE.invokeExact(id, 1, identity);
                if (word == 0) throw new OutOfMemoryError("Native holder status identity tags exhausted");
                settled = new StatusReference(word, status, null);
                StatusReference pending = new StatusReference(word, status, previous);
                startedReference = pending;
                publishedRoot = true;
                int changed = (int)STATUS_CHANGE.invokeExact(id, 1, expected, word, 1);
                if (changed != 1) throw new IllegalStateException("Native holder status changed outside its writer monitor");
                startedReference = settled;
            } catch (Throwable throwable) {
                if (publishedRoot) startedReference = statusWord() == word ? settled : previous;
                throw failure(throwable);
            } finally { Reference.reachabilityFence(this); }
        }
        long handleId() { return id; }
        void fence() { Reference.reachabilityFence(this); }
        int claim() { return (int)change(6, 0, 0); }
        int releaseClaim() { return (int)change(7, 0, 0); }

        @Nullable private Object get(int field) { return readPayload(field, false); }

        @Nullable private Object readPayload(int field, boolean status) {
            try {
                while (true) {
                    long token = readWord(fieldsAddress, FIELD_BYTES, field * (long)Long.BYTES);
                    if (token == 0) return null;
                    int row = bindingRow(field, token);
                    // Acquire the tag BEFORE the payload to see its published
                    // initialization, and AFTER it to reject a reused location.
                    if ((long)TAG.getAcquire(publicationTags, row) != token) { Thread.onSpinWait(); continue; }
                    Object value = ROW.getAcquire(references, status ? FULL_STATUS_BASE + (row & 1) : row);
                    if ((long)TAG.getAcquire(publicationTags, row) == token) return value;
                    Thread.onSpinWait();
                }
            } finally { Reference.reachabilityFence(this); }
        }

        /** Invalidate before clearing: a reader that already captured the
         * actual object retains it strongly; other readers retry metadata. */
        private void retireBinding(int row, boolean full) {
            TAG.setRelease(publicationTags, row, 0L);
            ROW.setRelease(references, row, null);
            if (full) ROW.setRelease(references, FULL_STATUS_BASE + (row & 1), null);
        }

        @Nullable private synchronized Object exchange(int field, @Nullable Object value) {
            return exchange(field, value, null, false);
        }

        @Nullable private synchronized Object exchange(int field, @Nullable Object value,
                @Nullable ChunkStatus allowed, boolean statusOnly) {
            long before = 0, word = 0;
            int oldRow = -1, nextRow = -1;
            boolean prepared = false;
            boolean full = field == FULL_FIELD;
            try {
                before = readWord(fieldsAddress, FIELD_BYTES, field * (long)Long.BYTES);
                oldRow = bindingRow(field, before);
                if (before != 0 && (long)TAG.getAcquire(publicationTags, oldRow) != before) {
                    throw new IllegalStateException("Native generation leaf tag mismatch");
                }
                Object old = before == 0 ? null : ROW.getAcquire(references, oldRow);
                if (statusOnly) {
                    value = old;
                    int index = canonicalIndex(allowed);
                    int identity = allowed == null ? 0 : index >= 0 ? index + 1 : 65535;
                    word = (long)ALLOWED_RESERVE.invokeExact(id, identity);
                } else {
                    if (full) allowed = before == 0 ? null : (ChunkStatus)ROW.getAcquire(references, FULL_STATUS_BASE + (oldRow & 1));
                    word = (long)FUTURE_RESERVE.invokeExact(id, field, value == null ? 0 : 1);
                }
                if (word == 0) throw new OutOfMemoryError("Native generation reference publication tags exhausted");
                nextRow = bindingRow(field, word);
                if (nextRow == oldRow) throw new IllegalStateException("Native generation binding reservation reused its published slot");
                prepared = true;
                TAG.setRelease(publicationTags, nextRow, 0L);
                ROW.setRelease(references, nextRow, value);
                if (full) ROW.setRelease(references, FULL_STATUS_BASE + (nextRow & 1), allowed);
                TAG.setRelease(publicationTags, nextRow, word);
                int changed = (int)FUTURE_PUBLISH.invokeExact(id, field, before, word);
                if (changed != 1) throw new IllegalStateException("Native generation field changed outside its writer monitor");
                retireBinding(oldRow, full);
                return old;
            } catch (Throwable throwable) {
                if (prepared) {
                    long current = readWord(fieldsAddress, FIELD_BYTES, field * (long)Long.BYTES);
                    if (current == word) retireBinding(oldRow, full);
                    else if (current == before) retireBinding(nextRow, full);
                }
                throw failure(throwable);
            } finally { Reference.reachabilityFence(this); }
        }

        /** Object identity is a Java leaf; Rust retains the sole slot membership. */
        @Nullable private synchronized Object compareExchange(int field, @Nullable Object expected, @Nullable Object value) {
            Object current = get(field);
            if (current == expected) exchange(field, value);
            return current;
        }

        @SuppressWarnings("unchecked")
        @Nullable CompletableFuture<ChunkResult<ChunkAccess>> future(int index) {
            checkFutureIndex(index);
            return (CompletableFuture<ChunkResult<ChunkAccess>>)get(index);
        }

        @SuppressWarnings("unchecked")
        @Nullable CompletableFuture<ChunkResult<ChunkAccess>> compareFuture(int index,
                @Nullable CompletableFuture<ChunkResult<ChunkAccess>> expected,
                @Nullable CompletableFuture<ChunkResult<ChunkAccess>> value) {
            checkFutureIndex(index);
            return (CompletableFuture<ChunkResult<ChunkAccess>>)compareExchange(index, expected, value);
        }

        private static void checkFutureIndex(int index) {
            // The combined allocation also contains task and save-fence fields.
            // Preserve AtomicReferenceArray's original future-only boundary.
            if (index < 0 || index >= STATUSES.size()) {
                throw new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for length " + STATUSES.size());
            }
        }

        @Nullable ChunkGenerationTask task() { return (ChunkGenerationTask)get(STATUSES.size()); }
        @Nullable ChunkGenerationTask exchangeTask(@Nullable ChunkGenerationTask task) {
            return (ChunkGenerationTask)exchange(STATUSES.size(), task);
        }
        void removeTask(ChunkGenerationTask expected) { compareExchange(STATUSES.size(), expected, null); }
        @SuppressWarnings("unchecked")
        CompletableFuture<Void> saveFence() { return (CompletableFuture<Void>)get(STATUSES.size() + 1); }
        void saveFence(CompletableFuture<Void> future) { exchange(STATUSES.size() + 1, future); }
    }

    private static final class TaskBindings {
        static final MethodHandle DISPATCH = handle("task_dispatch", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        static final MethodHandle CREATE = handle("task_create", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        static final MethodHandle RELEASE = handle("task_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
        static final MethodHandle CANCEL = handle("task_cancel", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
        static final MethodHandle DRIVER_CREATE = handle("task_driver_create", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        static final MethodHandle DRIVER_NEXT = handle("task_driver_next", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        static final MethodHandle DRIVER_RELEASE = handle("task_driver_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
        static final MethodHandle CACHE_RELEASE_COUNT = handle("task_cache_release_count", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), Linker.Option.critical(false));
        static final int[][] POLICIES = policies();

        private static int[][] policies() {
            int[][] policies = new int[STATUSES.size()][];
            for (ChunkStatus target : STATUSES) {
                int count = target.getIndex() + 1;
                var dependencies = ChunkPyramid.LOADING_PYRAMID.getStepTo(target).accumulatedDependencies();
                int[] words = new int[1 + count * 2 + dependencies.size()];
                words[0] = STATUSES.size();
                for (int index = 0; index < count; index++) {
                    words[1 + index] = ChunkPyramid.GENERATION_PYRAMID.getStepTo(target).getAccumulatedRadiusOf(STATUSES.get(index));
                    words[1 + count + index] = ChunkPyramid.LOADING_PYRAMID.getStepTo(target).getAccumulatedRadiusOf(STATUSES.get(index));
                }
                for (int radius = 0; radius < dependencies.size(); radius++) words[1 + 2 * count + radius] = dependencies.get(radius).getIndex();
                policies[target.getIndex()] = words;
            }
            return policies;
        }
    }

    private static final class RequestBindings {
        static final MethodHandle CALLER_BEGIN = handle("caller_begin", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        static final MethodHandle CACHE_CHANGE = handle("request_cache_change", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        static final MethodHandle GRAPH_CREATE = handle("request_graph_create", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        static final MethodHandle GRAPH_RELEASE = handle("request_graph_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
        static final MethodHandle BIND_UNKNOWN = handle("request_bind_unknown", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        static final MethodHandle ADD_UNKNOWN = handle("request_add_unknown", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), Linker.Option.critical(false));
        private static final FunctionDescriptor BEGIN_TYPE = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
        private static final FunctionDescriptor NEXT_TYPE = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT);
        static final MethodHandle BEGIN = handle("request_begin", BEGIN_TYPE);
        // Fast calls allocate nothing, never block and never invoke Java. An empty
        // pool falls back before any engine effect; replaced-holder retirement is normal.
        static final MethodHandle BEGIN_FAST = handle("request_begin_fast", BEGIN_TYPE, Linker.Option.critical(false));
        static final MethodHandle NEXT = handle("request_next", NEXT_TYPE);
        static final MethodHandle NEXT_FAST = handle("request_next_fast", NEXT_TYPE, Linker.Option.critical(false));
        static final MethodHandle ABORT = handle("request_abort", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    }
    record SourceResource(MemorySegment owner, Object source) {}
    static final class RequestGraph {
        final MemorySegment owner;
        final SourceResource resource;
        final long id;
        private final net.minecraft.world.level.TicketStorage storageBinding;
        RequestGraph(ChunkMap map, net.minecraft.world.level.TicketStorage storage) {
            storageBinding = storage;
            long descriptor = storage.generationTypeDescriptor();
            Arena arena = Arena.ofAuto();
            MemorySegment tickets = storage.generationOwner(), holders = map.generationRegistryOwner();
            long created;
            try { created = (long)RequestBindings.GRAPH_CREATE.invokeExact(tickets.address(), holders.address()); }
            catch (Throwable t) { throw failure(t); }
            finally { Reference.reachabilityFence(tickets); Reference.reachabilityFence(holders); }
            if (created == 0) throw new OutOfMemoryError("Native generation request graph allocation failed");
            id = created;
            owner = adopt(created, 1, arena, RequestBindings.GRAPH_RELEASE, true);
            resource = new SourceResource(owner, storage);
            try { RequestBindings.BIND_UNKNOWN.invokeExact(id, (int)descriptor, (int)(descriptor >>> 32), TicketType.UNKNOWN.timeout()); }
            catch (Throwable t) { throw failure(t); }
            finally { Reference.reachabilityFence(this); }
        }
        boolean owns(net.minecraft.world.level.TicketStorage storage) { return storage == storageBinding; }
        long addUnknown(long key, int level) {
            try { return (long)RequestBindings.ADD_UNKNOWN.invokeExact(id, key, level); }
            catch (Throwable t) { return raise(t); }
            finally { Reference.reachabilityFence(this); }
        }
    }
    static long beginCaller(RequestGraph graph, int operation, boolean main, long key, boolean create) {
        try {
            long issued = (long)RequestBindings.CALLER_BEGIN.invokeExact(graph.id, operation, main ? 1 : 0, key, create ? 1 : 0);
            if (issued == 0) throw new OutOfMemoryError("Native chunk caller allocation failed");
            return issued;
        } catch (Throwable t) { return raise(t); }
        finally { Reference.reachabilityFence(graph); }
    }
    static int changeRequestCache(RequestGraph graph, long key, boolean clear) {
        try { return (int)RequestBindings.CACHE_CHANGE.invokeExact(graph.id, key, clear ? 1 : 0); }
        catch (Throwable t) { return raise(t); }
        finally { Reference.reachabilityFence(graph); }
    }
    static long beginRequest(@Nullable RequestGraph graph, int level, boolean create, int operation) {
        try {
            long graphId = graph == null ? 0L : graph.id;
            long issued = (long)RequestBindings.BEGIN_FAST.invokeExact(graphId, level, create ? 1 : 0, operation);
            if (issued == 0) issued = (long)RequestBindings.BEGIN.invokeExact(graphId, level, create ? 1 : 0, operation);
            if (issued == 0) throw new OutOfMemoryError("Native generation request frame allocation failed");
            return issued;
        } catch (Throwable t) { return raise(t); }
        finally { Reference.reachabilityFence(graph); }
    }
    static long nextRequest(@Nullable RequestGraph graph, long active, long response, int auxiliary, int previousOp) {
        try {
            return graph != null && previousOp != 71 && previousOp < 80
                ? (long)RequestBindings.NEXT_FAST.invokeExact(active, response, auxiliary)
                : (long)RequestBindings.NEXT.invokeExact(active, response, auxiliary);
        } catch (Throwable t) { return raise(t); }
        finally { Reference.reachabilityFence(graph); }
    }
    static void abortRequest(long active) { if (active != 0) releaseHandle(RequestBindings.ABORT, active); }
    static void request(@Nullable RequestGraph graph, int level, boolean create, int operation, LifecycleEffect effect) {
        long active = 0;
        try {
            long graphId = graph == null ? 0L : graph.id;
            long issued = (long)RequestBindings.BEGIN_FAST.invokeExact(graphId, level, create ? 1 : 0, operation);
            if (issued == 0) issued = (long)RequestBindings.BEGIN.invokeExact(graphId, level, create ? 1 : 0, operation);
            if (issued == 0) throw new OutOfMemoryError("Native generation request frame allocation failed");
            active = issued & ~127L;
            long command = issued & 127L;
            while (true) {
                boolean terminal = command < 0;
                // Terminal commands contain no borrowed output. Native may already
                // have pooled this frame; nested calls cannot clobber the primitive.
                if (terminal && (command & (1L << 62)) == 0) active = 0;
                int op = (int)(command & 255L);
                long response = effect.apply(op, (int)(command >>> 8 & 65535L), 0);
                if (terminal) return;
                int auxiliary = op == 72 ? (int)effect.apply(73, 0, 0) : 0;
                command = graph != null && op != 71
                    ? (long)RequestBindings.NEXT_FAST.invokeExact(active, response, auxiliary)
                    : (long)RequestBindings.NEXT.invokeExact(active, response, auxiliary);
            }
        } catch (Throwable t) { NativeChunkGeneration.<RuntimeException, Object>raise(t); }
        finally {
            if (active != 0) releaseHandle(RequestBindings.ABORT, active);
            Reference.reachabilityFence(graph);
            Reference.reachabilityFence(effect);
        }
    }

    /** Immutable cache bindings and opaque future roots; all progression is Rust. */
    static final class Task {
        final ChunkGenerationTask task;
        final GeneratingChunkMap map;
        final ChunkStatus target;
        final ChunkPos pos;
        final StaticCache2D<GenerationChunkHolder> cache;
        private final MemorySegment owner;
        private final long id;
        private Object[] references = new Object[16];

        Task(ChunkGenerationTask task, GeneratingChunkMap map, ChunkStatus target, ChunkPos pos,
                StaticCache2D<GenerationChunkHolder> cache, int cacheRadius) {
            this.task = task; this.map = map; this.target = target; this.pos = pos; this.cache = cache;
            Arena ownerArena = Arena.ofAuto();
            long created;
            int canonical = canonicalIndex(target);
            int[] policy = canonical < 0 ? new int[]{STATUSES.size(), cacheRadius} : TaskBindings.POLICIES[canonical];
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = arena.allocateFrom(ValueLayout.JAVA_INT, policy);
                MemorySegment holders = MemorySegment.NULL;
                int holderCount = 0;
                if (canonical >= 0 && map.getClass() == ChunkMap.class) {
                    int side = cacheRadius * 2 + 1;
                    int count = side * side;
                    MemorySegment candidates = arena.allocate((long)count * Long.BYTES, Long.BYTES);
                    int[] cursor = {0, 1};
                    cache.forEach(holder -> {
                        int cell = cursor[0]++;
                        long handle = holder != null && holder.getClass() == ChunkHolder.class && holder.generationOwner() != null
                            ? holder.generationOwner().handleId() : 0L;
                        if (handle == 0) cursor[1] = 0;
                        else {
                            if (cell < 0 || cell >= count) throw new ArrayIndexOutOfBoundsException("Index " + cell + " out of bounds for length " + count);
                            candidates.setAtIndex(ValueLayout.JAVA_LONG, cell, handle);
                        }
                    });
                    if (cursor[1] != 0 && cursor[0] == count) { holders = candidates; holderCount = count; }
                }
                created = (long)TaskBindings.CREATE.invokeExact(canonical, pos.x, pos.z, data, policy.length, holders, holderCount);
            } catch (Throwable throwable) { throw failure(throwable); }
            finally { Reference.reachabilityFence(cache); }
            if (created == 0) throw new OutOfMemoryError("Native generation task allocation failed");
            id = created;
            owner = adopt(created, 1, ownerArena, TaskBindings.RELEASE, true);
        }

        void cancel() {
            try { TaskBindings.CANCEL.invokeExact(id); }
            catch (Throwable throwable) { throw failure(throwable); }
            finally { Reference.reachabilityFence(this); }
        }

        GenerationChunkHolder center() { return cache.get(pos.x, pos.z); }

        private synchronized void pin(int row, Object value) {
            if (row <= 0) throw new IllegalStateException("Invalid generation task reference");
            if (row > references.length) references = Arrays.copyOf(references, Math.max(row, references.length * 2));
            if (references[row - 1] != null) throw new IllegalStateException("Generation task reference was not retired");
            references[row - 1] = value;
        }

        @SuppressWarnings("unchecked")
        private synchronized CompletableFuture<ChunkResult<ChunkAccess>> future(int row) {
            return (CompletableFuture<ChunkResult<ChunkAccess>>)references[row - 1];
        }

        private synchronized void unpin(int row) { references[row - 1] = null; }

        long cacheReleaseCount() {
            try { return (long)TaskBindings.CACHE_RELEASE_COUNT.invokeExact(id); }
            catch (Throwable throwable) { throw failure(throwable); }
            finally { Reference.reachabilityFence(this); }
        }

        @Nullable CompletableFuture<?> run() {
            TaskFrame frame = new TaskFrame(this);
            long driver = 0;
            try {
                driver = (long)TaskBindings.DRIVER_CREATE.invokeExact(id);
                if (driver == 0) throw new OutOfMemoryError("Native generation task continuation allocation failed");
                long response = 0;
                while (true) {
                    int result = (int)TaskBindings.DRIVER_NEXT.invokeExact(driver, response);
                    if (result != 1) {
                        if (frame.failure != null) return raise(frame.failure);
                        if (result < 0) throw new IllegalStateException("Native generation task failed without an engine exception");
                        return frame.waiting;
                    }
                    // Copy the complete command before the effect. A nested
                    // invocation owns a different native continuation; no
                    // borrowed mutable output or native lock crosses Java.
                    long first = readWord(driver, 24L, 0L);
                    long second = readWord(driver, 24L, 8L);
                    long token = readWord(driver, 24L, 16L);
                    response = taskEffect(frame, (int)first, (int)(first >>> 32), (int)second, (int)(second >>> 32), token);
                }
            } catch (Throwable throwable) { return raise(throwable); }
            finally {
                if (driver != 0) releaseHandle(TaskBindings.DRIVER_RELEASE, driver);
                Reference.reachabilityFence(this);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable, R> R raise(Throwable throwable) throws E { throw (E)throwable; }

    private static final class TaskFrame {
        final Task owner;
        CompletableFuture<ChunkResult<ChunkAccess>> future;
        ChunkResult<ChunkAccess> result;
        CompletableFuture<?> waiting;
        Throwable failure;
        Zone zone;
        ChunkDependencies loadingDependencies;
        GenerationChunkHolder cell;
        CompletableFuture<Void> releaseFence;
        TaskFrame(Task owner) { this.owner = owner; }

        long closeZone() {
            try { if (zone != null) zone.close(); }
            catch (Throwable close) {
                if (failure == null) failure = close;
                else {
                    try { failure.addSuppressed(close); }
                    catch (Throwable suppression) { failure = suppression; }
                }
            } finally { zone = null; }
            return failure == null ? 0 : Long.MIN_VALUE;
        }
    }

    /** Engine failures resume Rust's original cleanup path. Reentry passes an
     * independent strong frame directly, with no task upcall/TLS lookup. */
    private static long taskEffect(TaskFrame frame, int op, int a, int b, int c, long token) {
        if (op == 8) return frame.closeZone();
        try {
            Task owner = frame.owner;
            switch (op) {
                case 1: {
                    var pyramid = token == 0 ? ChunkPyramid.LOADING_PYRAMID : ChunkPyramid.GENERATION_PYRAMID;
                    CompletableFuture<ChunkResult<ChunkAccess>> completableFuture =
                        frame.cell.applyStep(pyramid.getStepTo(STATUSES.get(a)), owner.map, owner.cache);
                    frame.future = completableFuture;
                    ChunkResult<ChunkAccess> chunkResult = completableFuture.getNow(null);
                    frame.result = chunkResult;
                    // The original immediate path has no task-state check or
                    // mutation between these calls. Reentry gets its own frame;
                    // keep these exact returned objects through every callback.
                    return chunkResult == null ? 0 : chunkResult.isSuccess() ? 1 : 2;
                }
                case 2: {
                    if (token != 0) frame.future = owner.future((int)token);
                    CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = frame.future;
                    frame.result = completableFuture.getNow(null);
                    return frame.result == null ? 0 : 1;
                }
                case 3: return frame.result.isSuccess() ? 1 : 0;
                case 4: owner.pin((int)token, frame.future); return 0;
                case 5: owner.unpin((int)token); return 0;
                // getNow may have reentered, removed the entry and reused its
                // row. Return the strong future captured BEFORE that callback.
                case 6: frame.waiting = frame.future; return 0;
                case 7: {
                    Zone zone = Profiler.get().zone("scheduleLayer");
                    frame.zone = zone;
                    zone.addText(STATUSES.get(a)::getName);
                    return 0;
                }
                case 9: owner.cache.get(a, b).removeTask(owner.task); return 0;
                case 10: owner.map.releaseGeneration(owner.cache.get(a, b)); return 0;
                case 11: throw new IllegalStateException("Can't load chunk, but didn't expect to need to generate");
                case 12: throw new OutOfMemoryError("Native pending generation layer allocation failed");
                case 13: throw new java.util.NoSuchElementException();
                case 14: {
                    var pyramid = b == 0 ? ChunkPyramid.LOADING_PYRAMID : ChunkPyramid.GENERATION_PYRAMID;
                    return pyramid.getStepTo(owner.target).getAccumulatedRadiusOf(STATUSES.get(a));
                }
                case 15: STATUSES.get(a); return 0;
                case 16: {
                    GenerationChunkHolder generationChunkHolder = token == 0 ? owner.cache.get(b, c) : frame.cell;
                    frame.cell = generationChunkHolder;
                    ChunkStatus persisted = generationChunkHolder.getPersistedStatus();
                    return persisted != null && STATUSES.get(a).isAfter(persisted) ? 1 : 0;
                }
                case 17: {
                    ChunkStatus chunkStatus = owner.cache.get(a, b).getPersistedStatus();
                    return chunkStatus != null && !chunkStatus.isBefore(owner.target) ? 1 : 0;
                }
                case 18:
                    frame.loadingDependencies = ChunkPyramid.LOADING_PYRAMID.getStepTo(owner.target).accumulatedDependencies();
                    return frame.loadingDependencies.getRadius();
                case 19: {
                    ChunkStatus required = token == 0 ? STATUSES.get(c) : frame.loadingDependencies.get(c);
                    ChunkStatus persisted = owner.cache.get(a, b).getPersistedStatus();
                    return persisted != null && !persisted.isBefore(required) ? 1 : 0;
                }
                case 20: frame.cell = owner.cache.get(a, b); return 0;
                case 21:
                    owner.center().removeTask(owner.task);
                    frame.releaseFence = owner.cache.get(a, b).generationOwner().saveFence();
                    return 0;
                case 22: {
                    if (a == 0) {
                        CompletableFuture<Void> completableFuture = frame.releaseFence;
                        completableFuture.complete(null);
                    }
                    if (a < 0) throw new IllegalStateException("More releases than claims. Count: " + a);
                    if (token != 0) frame.releaseFence = owner.cache.get(b, c).generationOwner().saveFence();
                    return 0;
                }
                default: throw new IllegalStateException("Unknown native generation task effect: " + op);
            }
        } catch (Throwable throwable) { frame.failure = throwable; return Long.MIN_VALUE; }
    }

    private static final class PublicationBindings {
        static final MethodHandle PUBLISH = handle("publish", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        static final MemorySegment EFFECT;
        static {
            try {
                EFFECT = Linker.nativeLinker().upcallStub(MethodHandles.lookup().findStatic(NativeChunkGeneration.class,
                    "publicationEffect", MethodType.methodType(int.class, int.class)),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), Arena.global());
            } catch (ReflectiveOperationException exception) { throw new ExceptionInInitializerError(exception); }
        }
    }

    private static final ThreadLocal<ArrayDeque<PublicationFrame>> PUBLICATION_FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private abstract static class PublicationFrame {
        Throwable failure;
        abstract int effect(int op);
    }

    private static int publicationEffect(int op) {
        PublicationFrame frame = PUBLICATION_FRAMES.get().peek();
        try { return frame.effect(op); }
        catch (Throwable throwable) { frame.failure = throwable; return Integer.MIN_VALUE; }
    }

    private static void publish(PublicationFrame frame, int kind) {
        var frames = PUBLICATION_FRAMES.get();
        frames.push(frame);
        try {
            int result = (int)PublicationBindings.PUBLISH.invokeExact(kind, PublicationBindings.EFFECT);
            if (frame.failure != null) NativeChunkGeneration.<RuntimeException, Void>raise(frame.failure);
            if (result < 0) throw new IllegalStateException("Native chunk publication failed without an engine exception");
        } catch (Throwable throwable) { NativeChunkGeneration.<RuntimeException, Void>raise(throwable); }
        finally { frames.pop(); if (frames.isEmpty()) PUBLICATION_FRAMES.remove(); }
    }

    /** FULL materialization order; the constructor and every engine operation are real Java leaves. */
    public static LevelChunk publishFull(WorldGenContext context, GenerationChunkHolder holder, ProtoChunk input, Supplier<LevelChunk> constructor) {
        class FullFrame extends PublicationFrame {
            LevelChunk chunk;
            int effect(int op) {
                LevelChunk levelChunk = chunk;
                switch (op) {
                    case 0: return input instanceof ImposterProtoChunk ? 1 : 0;
                    case 1: chunk = ((ImposterProtoChunk)input).getWrapped(); return 0;
                    case 2: chunk = constructor.get(); return 0;
                    case 3: holder.replaceProtoChunk(new ImposterProtoChunk(levelChunk, false)); return 0;
                    case 4: levelChunk.setFullStatus(holder::getFullStatus); return 0;
                    case 5: levelChunk.runPostLoad(); return 0;
                    case 6: levelChunk.setLoaded(true); return 0;
                    case 7: levelChunk.registerAllBlockEntitiesAfterLevelLoad(); return 0;
                    case 8: levelChunk.registerTickContainerInLevel(context.level()); return 0;
                    case 9: levelChunk.setUnsavedListener(context.unsavedListener()); return 0;
                    default: throw new IllegalStateException("Unknown native FULL effect: " + op);
                }
            }
        }
        FullFrame frame = new FullFrame();
        publish(frame, 0);
        return frame.chunk;
    }

    /** The original thenApply completion boundary: status/retrogen/dirty before profiler finish. */
    public static ChunkAccess completeStage(ChunkAccess chunk, ChunkStatus target, @Nullable ProfiledDuration duration) {
        publish(new PublicationFrame() {
            int effect(int op) {
                switch (op) {
                    case 10: return chunk instanceof ProtoChunk ? 1 : 0;
                    case 11: return ((ProtoChunk)chunk).getPersistedStatus().isBefore(target) ? 1 : 0;
                    case 12: ((ProtoChunk)chunk).setPersistedStatus(target); return 0;
                    case 13: if (duration != null) duration.finish(true); return 0;
                    default: throw new IllegalStateException("Unknown native stage completion effect: " + op);
                }
            }
        }, 1);
        return chunk;
    }

    private static final class PendingBindings {
        static final MethodHandle CREATE = handle("pending_create", FunctionDescriptor.of(ValueLayout.JAVA_LONG));
        static final MethodHandle RELEASE = handle("pending_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
        static final MethodHandle CHANGE = handle("pending_change", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        static final MethodHandle WALK_CREATE = handle("pending_walk_create", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        static final MethodHandle WALK_NEXT = handle("pending_walk_next", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        static final MethodHandle WALK_RELEASE = handle("pending_walk_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    }

    static final class Pending {
        private final MemorySegment owner;
        private final long id;
        private Object[] references = new Object[16];
        Pending() {
            Arena arena = Arena.ofAuto();
            long created;
            try { created = (long)PendingBindings.CREATE.invokeExact(); }
            catch (Throwable throwable) { throw failure(throwable); }
            if (created == 0) throw new OutOfMemoryError("Native pending generation tasks allocation failed");
            id = created;
            owner = adopt(created, 1, arena, PendingBindings.RELEASE);
        }
        private long change(int op, int row) {
            try {
                long result = (long)PendingBindings.CHANGE.invokeExact(id, op, row);
                if (result == -3) throw new OutOfMemoryError("Native pending generation reference allocation failed");
                if (result < 0) throw new IllegalStateException("Native pending generation fault: " + result);
                return result;
            } catch (Throwable throwable) { throw failure(throwable); }
            finally { Reference.reachabilityFence(this); }
        }
        synchronized void add(ChunkGenerationTask task) {
            int row = (int)change(0, 0);
            try {
                if (row > references.length) references = Arrays.copyOf(references, Math.max(row, references.length * 2));
                if (references[row - 1] != null) throw new IllegalStateException("Pending generation reference was not retired");
                references[row - 1] = task;
                change(1, row);
            } catch (Throwable throwable) {
                if (row <= references.length) references[row - 1] = null;
                change(2, row);
                throw throwable;
            }
        }
        synchronized void clear() { change(3, 0); Arrays.fill(references, null); }
        private synchronized ChunkGenerationTask task(int row) { return (ChunkGenerationTask)references[row - 1]; }
        void forEach(Consumer<ChunkGenerationTask> action) {
            Objects.requireNonNull(action);
            long walk = 0;
            try {
                walk = (long)PendingBindings.WALK_CREATE.invokeExact(id);
                if (walk == 0) return;
                while (true) {
                    long row = (long)PendingBindings.WALK_NEXT.invokeExact(walk);
                    if (row == 0) return;
                    if (row == -2) throw new java.util.ConcurrentModificationException();
                    if (row < 0) throw new IllegalStateException("Native pending generation pass failed without an engine exception");
                    // Strong actual task capture before invoking the callback.
                    // A callback throw skips subsequent version checks, as JDK.
                    ChunkGenerationTask task = task((int)row);
                    action.accept(task);
                }
            } catch (Throwable throwable) { NativeChunkGeneration.<RuntimeException, Void>raise(throwable); }
            finally {
                if (walk != 0) releaseHandle(PendingBindings.WALK_RELEASE, walk);
                Reference.reachabilityFence(this);
            }
        }
    }
}
