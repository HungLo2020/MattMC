package net.minecraft.world.level.chunk.storage;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NativeChunkTapeTest {
    private static final RegionStorageInfo INFO = new RegionStorageInfo("owned-tape", null, "chunk");
    private static ChunkPos POS;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); POS = new ChunkPos(0, 0); }
    private static CompoundTag tag() {
        var tag = new CompoundTag();
        var bytes = new byte[250_000];
        new java.util.Random(42).nextBytes(bytes);
        tag.putByteArray("large", bytes);
        tag.putString("owner", "region-to-chunk");
        return tag;
    }
    @Test void retainedTapeSurvivesRegionCloseAndThreadTransfer(@TempDir Path dir) throws Exception {
        var expected = tag();
        NativeChunkTape tape;
        try (var storage = new RegionFileStorage(INFO, dir, false)) {
            storage.write(POS, expected);
            tape = storage.readOwnedTape(POS);
            assertNotNull(tape);
            assertArrayEquals(storage.readTape(POS), tape.copy());
        }
        try (tape) {
            assertEquals(expected, CompletableFuture.supplyAsync(() -> {
                try { return NativeNbtRegionAccess.readTape(tape.copy()); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }).get(30, TimeUnit.SECONDS));
        }
        tape.close();
        assertThrows(IllegalStateException.class, tape::copy);
    }
    @Test void validationFingerprintIsOptionalAndLegacyMetadataIsPreserved(@TempDir Path dir) throws Exception {
        try (var storage = new RegionFileStorage(INFO, dir, false)) { storage.write(POS, tag()); }
        long region = NativeRegionFileBridge.open(dir.resolve("r.0.0.mca"), false);
        try {
            var legacy = NativeRegionFileBridge.readNbtTape(region, 0, 0, 0, 0, 0, 0, 0, 0);
            var checked = NativeRegionFileBridge.readOwnedTape(region, 0, 0, true);
            try (var tape = checked.tape()) {
                assertEquals(legacy.result(), checked.result());
                assertArrayEquals(legacy.bytes(), tape.copy());
            }
            var unchecked = NativeRegionFileBridge.readOwnedTape(region, 0, 0, false);
            try (var tape = unchecked.tape()) {
                assertEquals(0, unchecked.result().fingerprint());
                assertEquals(legacy.result().outputLength(), unchecked.result().outputLength());
                assertArrayEquals(legacy.bytes(), tape.copy());
            }
            var absent = NativeRegionFileBridge.readOwnedTape(region, 31, 31, true);
            assertFalse(absent.result().present());
            assertNull(absent.tape());
        } finally { NativeRegionFileBridge.close(region); }
        assertThrows(java.io.IOException.class, () -> NativeRegionFileBridge.readOwnedTape(region, 0, 0, false));
    }
    @Test void canceledIoCompletionDiscardsTheProducedNativeTape(@TempDir Path dir) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var produced = new AtomicReference<NativeChunkTape>();
        try (var worker = new IOWorker(INFO, dir, false)) {
            worker.storage.write(POS, tag());
            var submit = IOWorker.class.getDeclaredMethod("submitThrowingTask", IOWorker.ThrowingSupplier.class, Consumer.class);
            submit.setAccessible(true);
            IOWorker.ThrowingSupplier<Optional<IOWorker.Loaded>> producer = () -> {
                var tape = worker.storage.readOwnedTape(POS);
                produced.set(tape);
                entered.countDown();
                if (!release.await(30, TimeUnit.SECONDS)) throw new AssertionError("Test release timed out");
                return Optional.of(new IOWorker.Loaded(null, null, tape));
            };
            Consumer<Optional<IOWorker.Loaded>> discard = value -> value.ifPresent(IOWorker.Loaded::close);
            var future = (CompletableFuture<?>)submit.invoke(worker, producer, discard);
            try {
                assertTrue(entered.await(30, TimeUnit.SECONDS));
                assertTrue(future.cancel(false));
            } finally { release.countDown(); }
            // This foreground task follows the canceled completion on the same IO executor.
            assertTrue(worker.loadAsync(new ChunkPos(31, 31)).get(30, TimeUnit.SECONDS).isEmpty());
            assertThrows(IllegalStateException.class, produced.get()::copy);
        } finally { release.countDown(); }
    }
}
