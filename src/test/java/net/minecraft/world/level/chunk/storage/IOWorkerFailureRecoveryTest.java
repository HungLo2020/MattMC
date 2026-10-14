package net.minecraft.world.level.chunk.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.nbt.visitors.CollectToTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Real worker/executor/native region integration; no mocked storage or scheduling. */
@Timeout(value = 45, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class IOWorkerFailureRecoveryTest {
    @TempDir Path directory;
    private static ChunkPos POS;
    private static final RegionStorageInfo INFO = new RegionStorageInfo("recovery", null, "chunk");

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        POS = new ChunkPos(1, 2);
    }

    private IOWorker open() { return new IOWorker(INFO, directory, false); }
    private static CompoundTag tag(int value) {
        var tag = new CompoundTag();
        tag.putInt("value", value);
        return tag;
    }
    private static <T> T await(CompletableFuture<T> future) throws Exception {
        // get(timeout) observes the original future without completing it (unlike orTimeout).
        return future.get(5, TimeUnit.SECONDS);
    }
    private static Throwable failure(CompletableFuture<?> future) {
        return assertThrows(ExecutionException.class, () -> await(future)).getCause();
    }
    private static SerializableChunkData.Encoded encoded(CompoundTag tag) throws IOException {
        return new SerializableChunkData.Encoded(NativeNbtRegionAccess.writeTape(tag), () -> tag);
    }
    private static void checkReadAndScan(IOWorker worker, ChunkPos pos, CompoundTag expected) throws Exception {
        assertEquals(expected, await(worker.loadAsync(pos)).orElseThrow());
        var visitor = new CollectToTag();
        await(worker.scanChunk(pos, visitor));
        assertEquals(expected, visitor.getResult());
        try (var loaded = await(worker.loadForParse(pos)).orElseThrow()) {
            assertEquals(expected, loaded.fullTag());
        }
    }
    private static final class Gate implements AutoCloseable {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CompletableFuture<Void> stored;
        Gate(IOWorker worker, int x) throws Exception {
            stored = worker.store(new ChunkPos(x, 0), () -> {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("gate timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return tag(x);
            });
        }
        void awaitEntry() throws Exception { assertTrue(entered.await(5, TimeUnit.SECONDS)); }
        @Override public void close() { release.countDown(); }
    }

    @Test void throwingNormalSupplierCompletesAccountingAndLaterSaves() throws Exception {
        throwingSupplier(false);
    }
    @Test void throwingEncodedSupplierCompletesAccountingAndLaterSaves() throws Exception {
        throwingSupplier(true);
    }
    private void throwingSupplier(boolean encodedRoute) throws Exception {
        var marker = new IllegalArgumentException("supplier failed before returning data");
        var inFlight = new AtomicInteger(1);
        var completed = new AtomicInteger();
        try (var worker = open()) {
            CompletableFuture<Void> save = encodedRoute
                ? worker.storeEncoded(POS, () -> { throw marker; })
                : worker.store(POS, () -> { throw marker; });
            var accounted = save.whenComplete((unused, error) -> {
                inFlight.decrementAndGet();
                completed.incrementAndGet();
            });
            assertSame(marker, failure(accounted));
            assertEquals(0, inFlight.get(), "the caller's save accounting hook must run");
            assertEquals(1, completed.get());
            assertTrue(await(worker.loadAsync(POS)).isEmpty());
            await(worker.store(POS, tag(10)));
            var tape = encoded(tag(20));
            await(worker.storeEncoded(POS, () -> tape));
            await(worker.synchronize(true));
            checkReadAndScan(worker, POS, tag(20));
        }
        try (var reopened = open()) { checkReadAndScan(reopened, POS, tag(20)); }
    }

    @Test void deterministicPendingCoalescingAndReadsSurviveSupplierFailure() throws Exception {
        try (var worker = open(); var first = new Gate(worker, 20)) {
            first.awaitEntry();
            var old = worker.store(POS, tag(1));
            var marker = new IllegalStateException("coalesced supplier failed");
            var broken = worker.storeEncoded(POS, () -> { throw marker; });
            var prior = worker.loadAsync(POS);
            var tape = encoded(tag(3));
            var newest = worker.storeEncoded(POS, () -> tape);
            var scan = new CollectToTag();
            var scanned = worker.scanChunk(POS, scan);
            var read = worker.loadAsync(POS);
            var parse = worker.loadForParse(POS);
            try (var last = new Gate(worker, 21)) {
                first.close();
                last.awaitEntry(); // All reads ran, but no BACKGROUND write could run.
                assertFalse(old.isDone());
                assertFalse(newest.isDone());
                assertSame(marker, failure(broken));
                assertEquals(tag(1), await(prior).orElseThrow(), "throwing supplier preserves the pending value");
                await(scanned);
                assertEquals(tag(3), scan.getResult());
                var copy = await(read).orElseThrow();
                assertEquals(tag(3), copy);
                copy.putInt("value", -1);
                try (var loaded = await(parse).orElseThrow()) {
                    assertNotNull(loaded.tape(), "must take pending tape branch, not disk branch");
                    assertNull(loaded.nativeTape());
                    assertEquals(tag(3), loaded.fullTag());
                }
            }
            await(old);
            await(newest);
            await(worker.synchronize(true));
        }
        try (var reopened = open()) { checkReadAndScan(reopened, POS, tag(3)); }
    }

    @Test void pendingEncodedFailureCanBeReplacedByNormalWrite() throws Exception {
        try (var worker = open(); var first = new Gate(worker, 23)) {
            first.awaitEntry();
            var encodedFailure = worker.storeEncoded(POS, () ->
                new SerializableChunkData.Encoded(null, () -> tag(-1), new IOException("superseded tape failure")));
            var normal = worker.store(POS, tag(4));
            var parsed = worker.loadForParse(POS);
            var scan = new CollectToTag();
            var scanned = worker.scanChunk(POS, scan);
            try (var last = new Gate(worker, 24)) {
                first.close();
                last.awaitEntry();
                assertFalse(encodedFailure.isDone());
                assertFalse(normal.isDone());
                try (var loaded = await(parsed).orElseThrow()) {
                    assertNull(loaded.tape());
                    assertNull(loaded.nativeTape());
                    assertEquals(tag(4), loaded.tag());
                }
                await(scanned);
                assertEquals(tag(4), scan.getResult());
            }
            await(encodedFailure);
            await(normal);
            await(worker.synchronize(true));
        }
        try (var reopened = open()) { checkReadAndScan(reopened, POS, tag(4)); }
    }

    @Test void closeDrainsAcceptedWorkAndRejectsRacingSubmissions() throws Exception {
        var worker = open();
        CompletableFuture<Void> closing = null;
        try (var gate = new Gate(worker, 22)) {
            gate.awaitEntry();
            var queued = worker.store(POS, tag(7));
            var syncWithoutFlush = worker.synchronize(false);
            var syncWithFlush = worker.synchronize(true);
            closing = CompletableFuture.runAsync(() -> {
                try { worker.close(); } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
            });
            // Observe the real shutdown admission boundary without guessing a delay.
            var field = IOWorker.class.getDeclaredField("shutdownRequested");
            field.setAccessible(true);
            var shuttingDown = (java.util.concurrent.atomic.AtomicBoolean)field.get(worker);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!shuttingDown.get() && System.nanoTime() < deadline) Thread.sleep(1);
            assertTrue(shuttingDown.get());
            assertInstanceOf(java.util.concurrent.RejectedExecutionException.class,
                failure(worker.store(new ChunkPos(5, 5), tag(9))));
            assertFalse(closing.isDone(), "close must wait for accepted work");
            gate.close();
            await(gate.stored);
            await(queued);
            assertInstanceOf(java.util.concurrent.RejectedExecutionException.class, failure(syncWithoutFlush));
            assertInstanceOf(java.util.concurrent.RejectedExecutionException.class, failure(syncWithFlush));
            await(closing);
        } finally {
            if (closing != null) await(closing);
            else worker.close();
        }
        try (var reopened = open()) { checkReadAndScan(reopened, POS, tag(7)); }
    }

    @Test void regionWriteFailureCompletesAndWorkerRecovers() throws Exception {
        // A directory where the target region file belongs deterministically rejects the actual write.
        Files.createDirectory(directory.resolve("r.0.0.mca"));
        try (var worker = open()) {
            assertInstanceOf(IOException.class, failure(worker.store(POS, tag(1))));
            Files.delete(directory.resolve("r.0.0.mca"));
            await(worker.store(POS, tag(2)));
            await(worker.synchronize(true));
        }
        try (var reopened = open()) { checkReadAndScan(reopened, POS, tag(2)); }
    }

    @Test void concurrentSavesFlushCloseReopenAndRejectNewWork() throws Exception {
        var worker = open();
        try {
            var saves = new ArrayList<CompletableFuture<Void>>();
            for (int i = 0; i < 16; i++) {
                final int value = i;
                saves.add(CompletableFuture.supplyAsync(() -> worker.store(new ChunkPos(value, 3), tag(value)))
                    .thenCompose(future -> future));
            }
            await(CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)));
            await(worker.synchronize(false));
            await(worker.synchronize(true));
        } finally { worker.close(); }
        worker.close();
        assertInstanceOf(java.util.concurrent.RejectedExecutionException.class, failure(worker.store(POS, tag(99))));
        assertInstanceOf(java.util.concurrent.RejectedExecutionException.class, failure(worker.storeEncoded(POS, () -> null)));
        assertInstanceOf(java.util.concurrent.RejectedExecutionException.class, failure(worker.loadAsync(POS)));
        assertInstanceOf(java.util.concurrent.RejectedExecutionException.class, failure(worker.synchronize(true)));
        try (var reopened = open()) {
            for (int i = 0; i < 16; i++) checkReadAndScan(reopened, new ChunkPos(i, 3), tag(i));
        }
    }
}
