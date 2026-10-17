package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** Actual arena/Arc retirement, with a deliberately retained prerequisite. */
@Isolated
class NativeChunkDependencyLifetimeTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    record Lease(Object[] root, WeakReference<?> other, WeakReference<?> adapter) {}
    static Lease pending() {
        CompletableFuture<ChunkResult<ChunkAccess>> first = new CompletableFuture<>(), second = new CompletableFuture<>();
        var adapter = NativeChunkGeneration.rangeDependencies(null, List.of(first, second), ChunkResult.error("unloaded"));
        return new Lease(new Object[]{first}, new WeakReference<>(second), new WeakReference<>(adapter));
    }
    record Settled(CompletableFuture<?> output, WeakReference<?> first, WeakReference<?> second) {}
    static Settled settled(int outcome) {
        CompletableFuture<ChunkResult<ChunkAccess>> first = new CompletableFuture<>(), second = new CompletableFuture<>();
        var output = NativeChunkGeneration.rangeDependencies(null, List.of(first, second), ChunkResult.error("unloaded"));
        if (outcome == 2) assertTrue(output.cancel(false));
        if (outcome == 1) first.completeExceptionally(new IllegalStateException("failed prerequisite"));
        else first.complete(ChunkResult.error("ordinary unavailable"));
        second.complete(ChunkResult.error("ordinary unavailable"));
        assertTrue(output.isDone());
        return new Settled(output, new WeakReference<>(first), new WeakReference<>(second));
    }
    @Test void completedFailedAndExternallyCancelledOutputsReleaseTheirNativeOwners() throws Exception {
        for (int outcome = 0; outcome < 3; outcome++) {
            for (int i = 0; i < 10; i++) { System.gc(); Thread.sleep(20); }
            long[] before = NativeChunkGeneration.rangeStatistics();
            Settled settled = settled(outcome);
            long[] after = before;
            for (int i = 0; i < 160; i++) {
                System.gc(); Thread.sleep(20);
                after = NativeChunkGeneration.rangeStatistics();
                if (settled.first.get() == null && settled.second.get() == null
                        && after[0] <= before[0] && after[2] >= before[2] + 1) break;
            }
            assertNull(settled.first.get(), "terminal output retained its first prerequisite: " + outcome);
            assertNull(settled.second.get(), "terminal output retained its second prerequisite: " + outcome);
            assertEquals(before[1] + 1, after[1]);
            assertTrue(after[0] <= before[0], "terminal output retained its native owner: " + outcome);
            assertTrue(after[2] >= before[2] + 1, "terminal owner destructor did not run: " + outcome);
            assertTrue(after[3] <= before[3], "terminal owner retained native payload bytes: " + outcome);
            Reference.reachabilityFence(settled.output);
        }
    }
    @Test void retainedPrerequisiteKeepsTheNativeOwnerUntilTheActualVmCycleCollects() throws Exception {
        for (int i = 0; i < 10; i++) { System.gc(); Thread.sleep(20); }
        long[] before = NativeChunkGeneration.rangeStatistics();
        Lease lease = pending();
        for (int i = 0; i < 10; i++) { System.gc(); Thread.sleep(20); }
        long[] held = NativeChunkGeneration.rangeStatistics();
        assertEquals(before[1] + 1, held[1]);
        assertTrue(held[0] >= 1);
        assertTrue(held[3] > 0, "the retained native owner has requested payload bytes");
        assertTrue(held[4] >= held[3]);
        assertNotNull(lease.other.get(), "the retained native dependency needs its actual other VM future");
        Reference.reachabilityFence(lease.root[0]);
        lease.root[0] = null;
        long[] after = held;
        for (int i = 0; i < 160; i++) {
            System.gc(); Thread.sleep(20);
            after = NativeChunkGeneration.rangeStatistics();
            if (lease.other.get() == null && lease.adapter.get() == null && after[0] <= before[0] && after[2] >= before[2] + 1) break;
        }
        assertNull(lease.other.get());
        assertNull(lease.adapter.get());
        assertTrue(after[0] <= before[0], "last VM release left the native dependency owner live");
        assertTrue(after[2] >= before[2] + 1, "the actual native owner destructor must run");
        assertTrue(after[3] <= before[3], "released range left native payload bytes retained");
    }
}
