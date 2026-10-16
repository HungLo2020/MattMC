package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Actual GC/arena cleanup, including Java cycles through native-owned rows. */
class NativeGenerationLifetimeTest {
    static final AtomicInteger RELEASE_CALLS = new AtomicInteger();
    static void countRelease(long handle) { assertEquals(42L, handle); RELEASE_CALLS.incrementAndGet(); }

    /** Models JDK25's registration-before-returned-segment-allocation failure
     * point without exhausting the heap or freeing a real pointer twice. */
    static void registerThenFail(Arena arena, Consumer<MemorySegment> cleanup, OutOfMemoryError failure) {
        MemorySegment.ofAddress(42L).reinterpret(0, arena, cleanup);
        throw failure;
    }

    @Test void registeredCleanupAndFailedAdoptionRollbackShareOneRelease() throws Exception {
        for (String className : List.of("net.minecraft.server.level.NativeChunkGeneration$NativeRelease",
                "net.minecraft.server.level.NativeChunkHolders$NativeRelease")) {
            RELEASE_CALLS.set(0);
            Class<?> type = Class.forName(className);
            var constructor = type.getDeclaredConstructor(long.class, java.lang.invoke.MethodHandle.class);
            constructor.setAccessible(true);
            var function = MethodHandles.lookup().findStatic(NativeGenerationLifetimeTest.class, "countRelease", MethodType.methodType(void.class, long.class));
            Object action = constructor.newInstance(42L, function);
            @SuppressWarnings("unchecked") Consumer<MemorySegment> cleanup = (Consumer<MemorySegment>)action;
            OutOfMemoryError failure = new OutOfMemoryError("after cleanup registration, before returning the segment");
            try (Arena arena = Arena.ofConfined()) {
                assertSame(failure, assertThrows(OutOfMemoryError.class, () -> registerThenFail(arena, cleanup, failure)));
                ((Runnable)action).run();
                assertEquals(1, RELEASE_CALLS.get());
            }
            ((Runnable)action).run();
            assertEquals(1, RELEASE_CALLS.get(), "rollback and eventual arena cleanup must release the same handle exactly once");
        }
    }

    @Test void oldRawRollbackPatternDemonstratesTwoReleasesAtTheSameFailurePoint() {
        AtomicInteger calls = new AtomicInteger();
        Consumer<MemorySegment> raw = segment -> calls.incrementAndGet();
        OutOfMemoryError failure = new OutOfMemoryError("after cleanup registration");
        try (Arena arena = Arena.ofConfined()) {
            assertSame(failure, assertThrows(OutOfMemoryError.class, () -> registerThenFail(arena, raw, failure)));
            raw.accept(MemorySegment.NULL);
        }
        assertEquals(2, calls.get(), "counter-only reproduction of the unsafe constructor rollback pattern");
    }
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    static List<WeakReference<?>> createAndRelease() {
        List<WeakReference<?>> weak = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            var holder = new NativeChunkGeneration.Holder();
            CompletableFuture<ChunkResult<net.minecraft.world.level.chunk.ChunkAccess>> future = new CompletableFuture<>();
            future.thenRun(holder::started);
            holder.compareFuture(0, null, future);
            weak.add(new WeakReference<>(holder));
            weak.add(new WeakReference<>(future));
        }
        // The registry and holder retain one another on the Java heap, and the
        // native registry independently retains this generation Arc. Its live
        // counter cannot return to baseline until BOTH native owners release.
        var registry = new NativeChunkHolders(ChunkLevel.MAX_LEVEL);
        var pos = new net.minecraft.world.level.ChunkPos(11, -19);
        long action = registry.schedule(pos.toLong(), 33, false, ChunkLevel.MAX_LEVEL + 1);
        assertEquals(NativeChunkHolders.NEW, (int)(action & 255));
        int holderId = (int)(action >>> 8);
        var attached = new ChunkHolder(pos, 33, net.minecraft.world.level.LevelHeightAccessor.create(0, 16),
            null, null, null, registry, holderId);
        registry.commitNew(pos.toLong(), holderId, attached);
        assertSame(attached, registry.updating(pos.toLong()));
        weak.add(new WeakReference<>(registry));
        weak.add(new WeakReference<>(attached));
        weak.add(new WeakReference<>(attached.generationOwner()));
        var fixture = new ChunkGenerationTaskContractTest.Fixture(ChunkStatus.FULL);
        var task = fixture.create(ChunkStatus.EMPTY);
        assertNull(task.runUntilWait());
        var pending = new NativeChunkGeneration.Pending();
        pending.add(task);
        pending.forEach(value -> assertSame(task, value));
        weak.add(new WeakReference<>(task));
        weak.add(new WeakReference<>(pending));
        weak.add(new WeakReference<>(fixture));
        var foreignOwner = new NativeChunkGeneration.Status();
        class CyclicStatus extends ChunkStatus {
            final NativeChunkGeneration.Status backReference = foreignOwner;
            CyclicStatus() {
                super(ChunkStatus.EMPTY, java.util.EnumSet.noneOf(net.minecraft.world.level.levelgen.Heightmap.Types.class),
                    net.minecraft.world.level.chunk.status.ChunkType.PROTOCHUNK);
            }
            @Override public String getName() { return "test:cyclic_status"; }
            @Override public int getIndex() { throw new AssertionError("foreign status assignment queried index"); }
        }
        ChunkStatus foreign = new CyclicStatus();
        foreignOwner.set(foreign);
        assertSame(foreign, foreignOwner.get());
        weak.add(new WeakReference<>(foreignOwner));
        weak.add(new WeakReference<>(foreign));
        var status = new NativeChunkGeneration.Status();
        status.set(ChunkStatus.CARVERS);
        Supplier<ChunkStatus> escapedRead = status::get;
        System.gc();
        assertSame(ChunkStatus.CARVERS, escapedRead.get(), "escaped reader must keep its native status alive");
        weak.add(new WeakReference<>(status));
        return weak;
    }

    static WeakReference<?> publishAndRetireFuture(NativeChunkGeneration.Holder holder) {
        var future = new CompletableFuture<ChunkResult<net.minecraft.world.level.chunk.ChunkAccess>>();
        future.thenRun(holder::started);
        assertNull(holder.compareFuture(0, null, future));
        assertSame(future, holder.compareFuture(0, future, null));
        return new WeakReference<>(future);
    }

    @Test @Timeout(10) void retiredFutureIsCollectibleWhileItsNativeHolderRemainsLive() throws Exception {
        var holder = new NativeChunkGeneration.Holder();
        var retired = publishAndRetireFuture(holder);
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (retired.get() != null && System.nanoTime() < deadline) {
            System.gc(); Thread.sleep(25);
        }
        assertNull(retired.get(), "retired opaque binding must not retain its future until another publication");
        assertNull(holder.future(0));
        assertNull(holder.started());
        java.lang.ref.Reference.reachabilityFence(holder);
    }

    @Test @Timeout(20) void unreachableCyclesReleaseNativeOwnersAndEscapedReadPinsLifetime() throws Exception {
        System.gc();
        Thread.sleep(100);
        long[] before = NativeChunkGeneration.statistics();
        List<WeakReference<?>> weak = createAndRelease();
        long deadline = System.nanoTime() + 15_000_000_000L;
        long[] after;
        do {
            System.gc();
            Thread.sleep(25);
            after = NativeChunkGeneration.statistics();
            if (weak.stream().allMatch(reference -> reference.get() == null)
                && after[0] <= before[0] && after[1] <= before[1] && after[6] <= before[6] && after[10] <= before[10]) break;
        } while (System.nanoTime() < deadline);
        assertTrue(weak.stream().allMatch(reference -> reference.get() == null), "Java roots retained a generation cycle");
        for (int index : new int[]{0, 1, 6, 10}) assertTrue(after[index] <= before[index], "native owner not retired, counter " + index);
        System.out.println("GENERATION_GC before=" + java.util.Arrays.toString(before) + " after=" + java.util.Arrays.toString(after));
    }
}
