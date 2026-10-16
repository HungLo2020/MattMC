package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Fixed native slot authority and opaque Java bindings under concurrent
 * publication, null clears, callback reentry and escaped future lifetime. */
class NativeGenerationSlotReadTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    static final class Tagged extends CompletableFuture<ChunkResult<ChunkAccess>> {
        final int field;
        Tagged(int field) { this.field = field; }
    }

    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    void invalidFutureIndicesCannotReadOrOverwriteTaskAndSaveFenceFields() {
        var holder = new NativeChunkGeneration.Holder();
        var existing = new Tagged(0);
        var replacement = new Tagged(1);
        var save = new CompletableFuture<Void>();
        var fixture = new ChunkGenerationTaskContractTest.Fixture(net.minecraft.world.level.chunk.status.ChunkStatus.FULL);
        var task = fixture.create(net.minecraft.world.level.chunk.status.ChunkStatus.EMPTY);
        assertNull(holder.compareFuture(0, null, existing));
        holder.saveFence(save);
        CompletableFuture<ChunkResult<ChunkAccess>> expectedSave = (CompletableFuture)save;
        int taskField = net.minecraft.world.level.chunk.status.ChunkStatus.getStatusList().size();
        assertThrowsExactly(ArrayIndexOutOfBoundsException.class, () -> holder.compareFuture(taskField, null, replacement));
        assertNull(holder.task(), "invalid null-CAS cannot occupy the task field");
        for (int index : GenerationChunkHolderContractTest.invalidFutureIndices()) {
            assertThrowsExactly(ArrayIndexOutOfBoundsException.class, () -> holder.future(index));
            assertThrowsExactly(ArrayIndexOutOfBoundsException.class, () -> holder.compareFuture(index, null, replacement));
            assertThrowsExactly(ArrayIndexOutOfBoundsException.class, () -> holder.compareFuture(index, expectedSave, replacement));
            assertNull(holder.task(), "invalid null-CAS cannot occupy the task field");
            assertSame(save, holder.saveFence(), "invalid matching-CAS cannot replace the save fence");
            assertSame(existing, holder.future(0));
        }
        assertNull(holder.exchangeTask(task));
        for (int index : GenerationChunkHolderContractTest.invalidFutureIndices()) {
            assertThrowsExactly(ArrayIndexOutOfBoundsException.class, () -> holder.future(index));
            assertThrowsExactly(ArrayIndexOutOfBoundsException.class, () -> holder.compareFuture(index, null, replacement));
            assertSame(task, holder.task());
            assertSame(save, holder.saveFence());
        }
        assertSame(task, holder.exchangeTask(null));
        assertSame(existing, holder.compareFuture(0, existing, null));
        task.markForCancellation();
        assertNull(task.runUntilWait());
        assertEquals(fixture.acquired, fixture.released);
        java.lang.ref.Reference.reachabilityFence(holder);
    }

    /** Only isolated adapter copies call this pause, after publishing the
     * candidate heap binding but before native slot membership commits. */
    static volatile ReadGate gate;
    static final class ReadGate {
        final Object holder;
        final int field;
        final CountDownLatch published = new CountDownLatch(1), resume = new CountDownLatch(1);
        final AtomicBoolean once = new AtomicBoolean();
        Thread writer;
        ReadGate(Object holder) { this(holder, 0); }
        ReadGate(Object holder, int field) { this.holder = holder; this.field = field; }
    }
    static void afterReferencePublication(Object holder, int field, long token) {
        ReadGate current = gate;
        if (current == null || current.holder != holder || field != current.field || current.writer != Thread.currentThread()
                || !current.once.compareAndSet(false, true)) return;
        current.published.countDown();
        try {
            if (!current.resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("controlled slot writer was not resumed");
        } catch (InterruptedException exception) { throw new RuntimeException(exception); }
    }

    static volatile SelectionGate selectionGate;
    static final class SelectionGate {
        final Object holder;
        final CountDownLatch selected = new CountDownLatch(1), resume = new CountDownLatch(1);
        final AtomicBoolean once = new AtomicBoolean();
        Thread reader;
        SelectionGate(Object holder) { this.holder = holder; }
    }
    static void afterNativeSelection(Object holder, int field, long token) {
        SelectionGate current = selectionGate;
        if (current == null || current.holder != holder || field != 0 || current.reader != Thread.currentThread()
                || !current.once.compareAndSet(false, true)) return;
        current.selected.countDown();
        try {
            if (!current.resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("controlled native selection was not resumed");
        } catch (InterruptedException exception) { throw new RuntimeException(exception); }
    }

    @Test @Timeout(20)
    @EnabledIfSystemProperty(named = "generation.slotReadHook", matches = "true")
    void reusedLocationCannotExposeANewPendingFutureToAnOldNativeSelection() throws Exception {
        var holder = new NativeChunkGeneration.Holder();
        Tagged a = new Tagged(0), b = new Tagged(0), c = new Tagged(0);
        holder.compareFuture(0, null, a);
        SelectionGate selected = new SelectionGate(holder);
        CompletableFuture<CompletableFuture<ChunkResult<ChunkAccess>>> read = new CompletableFuture<>();
        selected.reader = new Thread(() -> {
            try { read.complete(holder.future(0)); }
            catch (Throwable failure) { read.completeExceptionally(failure); }
        }, "controlled-native-old-selection-reader");
        selectionGate = selected;
        ReadGate writing = new ReadGate(holder);
        CompletableFuture<Void> written = new CompletableFuture<>();
        writing.writer = new Thread(() -> {
            try { assertSame(b, holder.compareFuture(0, b, c)); written.complete(null); }
            catch (Throwable failure) { written.completeExceptionally(failure); }
        }, "controlled-native-reused-pending-writer");
        selected.reader.start();
        try {
            assertTrue(selected.selected.await(5, TimeUnit.SECONDS), "old native selection pause was not exercised");
            assertSame(a, holder.compareFuture(0, a, b));
            gate = writing; writing.writer.start();
            assertTrue(writing.published.await(5, TimeUnit.SECONDS), "reused location's pending publication was not exercised");
            selected.resume.countDown();
            assertSame(b, read.get(5, TimeUnit.SECONDS), "uncommitted binding exposed before native publication");
            assertSame(b, holder.future(0));
            System.gc();
            writing.resume.countDown(); written.get(5, TimeUnit.SECONDS);
            assertSame(c, holder.future(0));
        } finally {
            selected.resume.countDown(); writing.resume.countDown();
            selected.reader.join(5000);
            if (writing.writer.isAlive()) writing.writer.join(5000);
            selectionGate = null; gate = null;
            java.lang.ref.Reference.reachabilityFence(holder);
        }
    }

    @Test @Timeout(15)
    @EnabledIfSystemProperty(named = "generation.slotReadHook", matches = "true")
    void pendingBindingCannotBecomeVisibleBeforeItsNativePublication() throws Exception {
        var holder = new NativeChunkGeneration.Holder();
        Tagged original = new Tagged(0), replacement = new Tagged(0);
        holder.compareFuture(0, null, original);
        ReadGate current = new ReadGate(holder);
        CompletableFuture<Void> result = new CompletableFuture<>();
        current.writer = new Thread(() -> {
            try { assertSame(original, holder.compareFuture(0, original, replacement)); result.complete(null); }
            catch (Throwable throwable) { result.completeExceptionally(throwable); }
        }, "controlled-native-slot-writer");
        gate = current;
        current.writer.start();
        try {
            assertTrue(current.published.await(5, TimeUnit.SECONDS), "isolated publication pause was not exercised");
            assertSame(original, holder.future(0), "uncommitted binding exposed before native publication");
            System.gc();
            current.resume.countDown();
            result.get(5, TimeUnit.SECONDS);
            assertSame(replacement, holder.future(0));
        } finally {
            current.resume.countDown();
            current.writer.join(5000);
            gate = null;
            java.lang.ref.Reference.reachabilityFence(holder);
        }
    }

    @Test @Timeout(30) void simultaneousReadersNeverResolveCrossSlotAbaAndRetainReturnedFuture() throws Exception {
        var holder = new NativeChunkGeneration.Holder();
        // The native slot allocation and its opaque Java binding array stay
        // fixed while every field repeatedly publishes values and nulls.
        var roots = holder.getClass().getDeclaredField("references");
        roots.setAccessible(true);
        Object[] original = (Object[])roots.get(holder);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong reads = new AtomicLong();
        CyclicBarrier start = new CyclicBarrier(4);
        List<CompletableFuture<ChunkResult<ChunkAccess>>> escaped = new ArrayList<>();
        try (var workers = Executors.newFixedThreadPool(4)) {
            List<java.util.concurrent.Future<?>> readers = new ArrayList<>();
            for (int thread = 0; thread < 3; thread++) {
                readers.add(workers.submit(() -> {
                    try {
                        start.await();
                        while (running.get()) {
                            for (int field = 0; field < 12; field++) {
                                var future = holder.future(field);
                                if (future != null) assertEquals(field, ((Tagged)future).field, "resolved a future from another native slot");
                                reads.incrementAndGet();
                            }
                        }
                    } catch (Exception exception) { throw new RuntimeException(exception); }
                }));
            }
            var writer = workers.submit(() -> {
                try {
                    start.await();
                    for (int iteration = 0; iteration < 20_000; iteration++) {
                        // Clear every slot before rebinding: readers encounter
                        // a mix of live/null fields and newer publication tags.
                        for (int field = 0; field < 12; field++) {
                            var old = holder.future(field);
                            assertSame(old, holder.compareFuture(field, old, null));
                        }
                        for (int field = 0; field < 12; field++) {
                            var next = new Tagged(field);
                            assertNull(holder.compareFuture(field, null, next));
                            if (iteration == 0) escaped.add(next);
                        }
                        if (iteration % 2048 == 0) System.gc();
                    }
                } catch (Exception exception) { throw new RuntimeException(exception); }
                finally { running.set(false); }
            });
            writer.get(25, TimeUnit.SECONDS);
            for (var reader : readers) reader.get(25, TimeUnit.SECONDS);
        }
        assertTrue(reads.get() > 1000, "reader path was vacuous");
        assertSame(original, roots.get(holder), "fixed slots must keep the same opaque binding array");
        assertEquals(12, escaped.size());
        for (int field = 0; field < 12; field++) {
            var future = escaped.get(field);
            assertEquals(field, ((Tagged)future).field);
            assertFalse(future.isDone(), "retiring a row must not complete its escaped future");
            assertTrue(future.complete(GenerationChunkHolder.UNLOADED_CHUNK));
            assertSame(GenerationChunkHolder.UNLOADED_CHUNK, future.join());
        }
        System.out.println("GENERATION_SLOT_READS " + reads.get() + " writerReplacements=240000");
    }

    @Test void nullSlotPublicationRetainsItsNativeGenerationAndHeapBinding() throws Exception {
        var holder = new NativeChunkGeneration.Holder();
        Tagged value = new Tagged(0);
        assertNull(holder.compareFuture(0, null, value));
        assertSame(value, holder.compareFuture(0, value, null));
        var field = holder.getClass().getDeclaredField("fieldsAddress"); field.setAccessible(true);
        var slots = java.lang.foreign.MemorySegment.ofAddress(field.getLong(holder))
            .reinterpret((net.minecraft.world.level.chunk.status.ChunkStatus.getStatusList().size() + 2L) * Long.BYTES).asReadOnly();
        long cleared = (long)java.lang.foreign.ValueLayout.JAVA_LONG.varHandle().getVolatile(slots, 0L);
        assertNotEquals(0, cleared, "a clear must retain a publication generation for Java heap acquisition");
        assertEquals(0, cleared & 1);
        assertNull(holder.future(0));
        assertNull(holder.compareFuture(0, null, value));
        long restored = (long)java.lang.foreign.ValueLayout.JAVA_LONG.varHandle().getVolatile(slots, 0L);
        assertNotEquals(cleared, restored);
        assertEquals(1, restored & 1);
        assertSame(value, holder.future(0));
        java.lang.ref.Reference.reachabilityFence(holder);
    }

    @Test void fullAdmissionPublishesWithItsActualFutureAndPreservesForeignIdentity() {
        var holder = new NativeChunkGeneration.Holder();
        var full = net.minecraft.world.level.chunk.status.ChunkStatus.FULL;
        int field = full.getIndex();
        Tagged future = new Tagged(field);
        assertNull(holder.compareFuture(field, null, future));
        assertSame(NativeChunkGeneration.Holder.REQUEST_DISALLOWED, holder.fullRequest());
        holder.allowed(full);
        assertSame(full, holder.allowed());
        assertSame(future, holder.future(field));
        assertSame(future, holder.fullRequest(), "exercise the native admission path positively");
        var replacement = new Tagged(field);
        assertSame(future, holder.compareFuture(field, future, replacement));
        assertSame(full, holder.allowed(), "future publication retains the allowed identity");
        assertSame(replacement, holder.fullRequest());
        holder.allowed(net.minecraft.world.level.chunk.status.ChunkStatus.CARVERS);
        assertSame(replacement, holder.future(field), "admission publication retains the actual future");
        assertSame(NativeChunkGeneration.Holder.REQUEST_DISALLOWED, holder.fullRequest());
        holder.allowed(null);
        assertNull(holder.allowed());
        assertSame(replacement, holder.future(field));
        java.lang.ref.Reference.reachabilityFence(holder);
    }

    @Test void fullRequestCapturesActualFutureAcrossItsSingleIsDoneDemotionCallback() {
        var holder = new GenerationChunkHolderContractTest.Holder(0, 0);
        var full = net.minecraft.world.level.chunk.status.ChunkStatus.FULL;
        class ReentrantFuture extends CompletableFuture<ChunkResult<ChunkAccess>> {
            int checks;
            @Override public boolean isDone() { checks++; holder.disallow(); return false; }
        }
        var future = new ReentrantFuture();
        assertNull(holder.generationOwner().compareFuture(full.getIndex(), null, future));
        assertSame(future, holder.generationOwner().fullRequest());
        ChunkMap scheduler = mock(ChunkMap.class);
        assertSame(future, holder.scheduleChunkGenerationTask(full, scheduler));
        assertEquals(1, future.checks, "observable isDone must not be replayed after demotion");
        assertNull(holder.generationOwner().future(full.getIndex()));
        assertSame(GenerationChunkHolder.UNLOADED_CHUNK, future.join());
        verify(scheduler).scheduleGenerationTask(full, holder.getPos());
        verifyNoMoreInteractions(scheduler);
    }

    @Test void fullRequestPropagatesTheActualIsDoneFailureWithoutTaskAdmission() {
        var holder = new GenerationChunkHolderContractTest.Holder(0, 0);
        var full = net.minecraft.world.level.chunk.status.ChunkStatus.FULL;
        RuntimeException failure = new RuntimeException("actual FULL isDone");
        var future = new CompletableFuture<ChunkResult<ChunkAccess>>() {
            @Override public boolean isDone() { throw failure; }
        };
        holder.generationOwner().compareFuture(full.getIndex(), null, future);
        ChunkMap scheduler = mock(ChunkMap.class);
        assertSame(future, holder.generationOwner().fullRequest());
        assertSame(failure, assertThrows(RuntimeException.class, () -> holder.scheduleChunkGenerationTask(full, scheduler)));
        assertSame(future, holder.generationOwner().future(full.getIndex()));
        verifyNoInteractions(scheduler);
    }

    @Test void fullRequestRetainsForeignAllowedIndexQueryAndFailureBoundary() {
        var holder = new GenerationChunkHolderContractTest.Holder(0, 0);
        var full = net.minecraft.world.level.chunk.status.ChunkStatus.FULL;
        RuntimeException failure = new RuntimeException("foreign allowed index");
        class Foreign extends net.minecraft.world.level.chunk.status.ChunkStatus {
            int queries;
            Foreign() { super(net.minecraft.world.level.chunk.status.ChunkStatus.EMPTY,
                java.util.EnumSet.noneOf(net.minecraft.world.level.levelgen.Heightmap.Types.class),
                net.minecraft.world.level.chunk.status.ChunkType.PROTOCHUNK); }
            @Override public int getIndex() { queries++; throw failure; }
        }
        var foreign = new Foreign();
        var future = new CompletableFuture<ChunkResult<ChunkAccess>>() {
            @Override public boolean isDone() { throw new AssertionError("future observed before foreign status query"); }
        };
        holder.generationOwner().compareFuture(full.getIndex(), null, future);
        holder.generationOwner().allowed(foreign);
        assertSame(foreign, holder.generationOwner().allowed());
        assertEquals(0, foreign.queries);
        assertSame(NativeChunkGeneration.Holder.REQUEST_FALLBACK, holder.generationOwner().fullRequest());
        ChunkMap scheduler = mock(ChunkMap.class);
        assertSame(failure, assertThrows(RuntimeException.class, () -> holder.scheduleChunkGenerationTask(full, scheduler)));
        assertEquals(1, foreign.queries);
        assertSame(future, holder.generationOwner().future(full.getIndex()));
        verifyNoInteractions(scheduler);
    }

    @Test void foreignAllowedQueryReentryUsesTheLaterActualFutureWithoutObservingTheOldOne() {
        var holder = new GenerationChunkHolderContractTest.Holder(0, 0);
        var full = net.minecraft.world.level.chunk.status.ChunkStatus.FULL;
        var old = new CompletableFuture<ChunkResult<ChunkAccess>>() {
            @Override public boolean isDone() { throw new AssertionError("old future observed after query reentry"); }
        };
        var replacement = CompletableFuture.completedFuture(GenerationChunkHolder.UNLOADED_CHUNK);
        holder.generationOwner().compareFuture(full.getIndex(), null, old);
        class Foreign extends net.minecraft.world.level.chunk.status.ChunkStatus {
            int queries;
            Foreign() { super(net.minecraft.world.level.chunk.status.ChunkStatus.EMPTY,
                java.util.EnumSet.noneOf(net.minecraft.world.level.levelgen.Heightmap.Types.class),
                net.minecraft.world.level.chunk.status.ChunkType.PROTOCHUNK); }
            @Override public int getIndex() {
                queries++;
                holder.generationOwner().allowed(full);
                assertSame(old, holder.generationOwner().compareFuture(full.getIndex(), old, replacement));
                return full.getIndex();
            }
        }
        var foreign = new Foreign();
        holder.generationOwner().allowed(foreign);
        ChunkMap scheduler = mock(ChunkMap.class);
        assertSame(replacement, holder.scheduleChunkGenerationTask(full, scheduler));
        assertEquals(1, foreign.queries);
        assertSame(full, holder.generationOwner().allowed());
        assertSame(replacement, holder.generationOwner().future(full.getIndex()));
        verifyNoInteractions(scheduler);
    }

    @Test @Timeout(15)
    @EnabledIfSystemProperty(named = "generation.slotReadHook", matches = "true")
    void pendingAllowedBindingCannotAdmitFullBeforeItsNativePublication() throws Exception {
        var holder = new NativeChunkGeneration.Holder();
        var full = net.minecraft.world.level.chunk.status.ChunkStatus.FULL;
        int field = full.getIndex();
        Tagged future = new Tagged(field);
        holder.compareFuture(field, null, future);
        ReadGate current = new ReadGate(holder, field);
        CompletableFuture<Void> result = new CompletableFuture<>();
        current.writer = new Thread(() -> {
            try { holder.allowed(full); result.complete(null); }
            catch (Throwable throwable) { result.completeExceptionally(throwable); }
        }, "controlled-native-full-admission-writer");
        gate = current;
        current.writer.start();
        try {
            assertTrue(current.published.await(5, TimeUnit.SECONDS), "isolated allowed publication pause was not exercised");
            assertSame(NativeChunkGeneration.Holder.REQUEST_DISALLOWED, holder.fullRequest(),
                "uncommitted binding exposed before native publication");
            assertNull(holder.allowed());
            current.resume.countDown();
            result.get(5, TimeUnit.SECONDS);
            assertSame(future, holder.fullRequest());
            assertSame(full, holder.allowed());
        } finally {
            current.resume.countDown(); current.writer.join(5000); gate = null;
            java.lang.ref.Reference.reachabilityFence(holder);
        }
    }
}
