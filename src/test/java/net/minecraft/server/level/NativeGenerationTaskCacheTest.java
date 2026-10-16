package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual stock holder methods and native cache consumer. Only the unused map
 * constructor is bypassed; real-server composition verifies full construction. */
class NativeGenerationTaskCacheTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    static <T> T unconstructed(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)field.get(null)).allocateInstance(type));
    }
    static void assertNativeRelease(Fixture fixture) {
        assertTrue(fixture.nativeTask.cacheReleaseCount() > 0,
            "the actual Rust cache-release consumer must run before its engine completion callback");
    }
    static final class Fixture {
        final List<ChunkHolder> holders = new ArrayList<>();
        final ChunkGenerationTask task;
        final NativeChunkGeneration.Task nativeTask;
        Fixture(ChunkStatus target) throws Exception {
            ChunkPos pos = new ChunkPos(4, -7);
            int radius = ChunkPyramid.GENERATION_PYRAMID.getStepTo(target).getAccumulatedRadiusOf(ChunkStatus.EMPTY);
            var height = LevelHeightAccessor.create(0, 16);
            StaticCache2D<GenerationChunkHolder> cache = StaticCache2D.create(pos.x, pos.z, radius, (x, z) -> {
                ChunkHolder holder = new ChunkHolder(new ChunkPos(x, z), 33, height, null, null, null);
                holder.increaseGenerationRefCount(); holders.add(holder); return holder;
            });
            task = unconstructed(ChunkGenerationTask.class);
            set(ChunkGenerationTask.class, task, "targetStatus", target);
            nativeTask = new NativeChunkGeneration.Task(task, unconstructed(ChunkMap.class), target, pos, cache, radius);
            set(ChunkGenerationTask.class, task, "generation", nativeTask);
            cache.get(pos.x, pos.z).generationOwner().exchangeTask(task);
        }
    }

    @Test void stockCacheReleaseCapturesTheNextEpochAfterCompletionReentry() throws Exception {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        ChunkHolder first = fixture.holders.getFirst(), next = fixture.holders.get(1);
        var old = next.getSaveSyncFuture();
        var callback = first.getSaveSyncFuture().thenRun(() -> {
            assertNativeRelease(fixture);
            next.decreaseGenerationRefCount();
            assertTrue(old.isDone());
            next.increaseGenerationRefCount();
            assertNotSame(old, next.getSaveSyncFuture());
            assertFalse(next.getSaveSyncFuture().isDone());
        });
        fixture.task.markForCancellation();
        assertNull(fixture.task.runUntilWait());
        callback.join();
        assertTrue(next.getSaveSyncFuture().isDone(), "the later cell must complete its new live save epoch");
        assertTrue(fixture.holders.stream().allMatch(h -> h.getSaveSyncFuture().isDone()));
    }

    @Test void completionThrowKeepsTheNativeClaimPrefixAndRetryBeginsAtFirstCell() throws Exception {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        ChunkHolder first = fixture.holders.getFirst(), next = fixture.holders.get(1);
        RuntimeException failure = new RuntimeException("actual fence completion");
        CompletableFuture<Void> throwing = new CompletableFuture<>() {
            @Override public boolean complete(Void value) { assertNativeRelease(fixture); throw failure; }
        };
        first.generationOwner().saveFence(throwing);
        first.generationOwner().binding(NativeChunkGeneration.Holder.READY_BASE + 5, throwing);
        fixture.task.markForCancellation();
        assertSame(failure, assertThrows(RuntimeException.class, fixture.task::runUntilWait));
        assertFalse(next.getSaveSyncFuture().isDone(), "later claims cannot decrement before the throwing completion");
        var retry = assertThrows(IllegalStateException.class, fixture.task::runUntilWait);
        assertEquals("More releases than claims. Count: -1", retry.getMessage());
        assertFalse(next.getSaveSyncFuture().isDone());
        for (int i = 1; i < fixture.holders.size(); i++) fixture.holders.get(i).decreaseGenerationRefCount();
    }

    @Test void sameTaskCanReenterItsActualNativeCacheRelease() throws Exception {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        var callback = fixture.holders.getFirst().getSaveSyncFuture().thenRun(() -> {
            assertNativeRelease(fixture);
            var failure = assertThrows(IllegalStateException.class, fixture.task::runUntilWait);
            assertEquals("More releases than claims. Count: -1", failure.getMessage());
        });
        fixture.task.markForCancellation();
        assertNull(fixture.task.runUntilWait());
        callback.join();
        assertTrue(fixture.holders.stream().allMatch(h -> h.getSaveSyncFuture().isDone()));
    }

    @Test void capturedFenceSurvivesReacquireBetweenNativeDecrementAndEngineCompletion() throws Throwable {
        Fixture fixture = new Fixture(ChunkStatus.EMPTY);
        ChunkHolder first = fixture.holders.getFirst();
        CompletableFuture<Void> old = first.generationOwner().saveFence();
        fixture.task.markForCancellation();
        Class<?> bindings = Class.forName("net.minecraft.server.level.NativeChunkGeneration$TaskBindings");
        java.lang.invoke.MethodHandle create = binding(bindings, "DRIVER_CREATE");
        java.lang.invoke.MethodHandle next = binding(bindings, "DRIVER_NEXT");
        java.lang.invoke.MethodHandle release = binding(bindings, "DRIVER_RELEASE");
        Field idField = fixture.nativeTask.getClass().getDeclaredField("id"); idField.setAccessible(true);
        long taskId = idField.getLong(fixture.nativeTask);
        Class<?> frameType = Class.forName("net.minecraft.server.level.NativeChunkGeneration$TaskFrame");
        var constructor = frameType.getDeclaredConstructor(NativeChunkGeneration.Task.class); constructor.setAccessible(true);
        Object frame = constructor.newInstance(fixture.nativeTask);
        var effect = NativeChunkGeneration.class.getDeclaredMethod("taskEffect", frameType,
            int.class, int.class, int.class, int.class, long.class); effect.setAccessible(true);
        long driver = (long)create.invokeExact(taskId);
        try {
            assertEquals(1, (int)next.invokeExact(driver, 0L));
            long[] command = command(driver);
            assertEquals(21, (int)command[0]);
            assertEquals(0L, (long)effect.invoke(null, frame, (int)command[0], (int)(command[0] >>> 32),
                (int)command[1], (int)(command[1] >>> 32), command[2]));
            assertEquals(1, (int)next.invokeExact(driver, 0L));
            command = command(driver);
            assertEquals(22, (int)command[0]);
            assertEquals(0, (int)(command[0] >>> 32), "native claim reached zero before engine completion");
            first.increaseGenerationRefCount();
            CompletableFuture<Void> replacement = first.generationOwner().saveFence();
            assertNotSame(old, replacement);
            assertFalse(old.isDone()); assertFalse(replacement.isDone());
            assertEquals(0L, (long)effect.invoke(null, frame, (int)command[0], (int)(command[0] >>> 32),
                (int)command[1], (int)(command[1] >>> 32), command[2]));
            assertTrue(old.isDone(), "complete the fence captured before decrement");
            assertFalse(replacement.isDone(), "do not resolve the later live epoch after decrement");
            assertEquals(0, (int)next.invokeExact(driver, 0L));
            assertNativeRelease(fixture);
            first.decreaseGenerationRefCount();
            assertTrue(replacement.isDone());
        } finally {
            release.invokeExact(driver);
            java.lang.ref.Reference.reachabilityFence(fixture.nativeTask);
        }
    }

    static java.lang.invoke.MethodHandle binding(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true);
        return (java.lang.invoke.MethodHandle)field.get(null);
    }
    static long[] command(long driver) {
        var data = java.lang.foreign.MemorySegment.ofAddress(driver).reinterpret(24L);
        var word = java.lang.foreign.ValueLayout.JAVA_LONG.varHandle();
        return new long[]{(long)word.getVolatile(data, 0L), (long)word.getVolatile(data, 8L), (long)word.getVolatile(data, 16L)};
    }

    @Test void nullFenceKeepsOriginalExceptionBlameAfterTheNativeDecrement() throws Exception {
        Fixture fixture = new Fixture(ChunkStatus.EMPTY);
        fixture.holders.getFirst().generationOwner().saveFence(null);
        fixture.task.markForCancellation();
        var failure = assertThrows(NullPointerException.class, fixture.task::runUntilWait);
        assertTrue(failure.getMessage().endsWith("\"completableFuture\" is null"), failure.getMessage());
        var retry = assertThrows(IllegalStateException.class, fixture.task::runUntilWait);
        assertEquals("More releases than claims. Count: -1", retry.getMessage());
    }
}
