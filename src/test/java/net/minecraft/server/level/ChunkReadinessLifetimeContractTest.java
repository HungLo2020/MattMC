package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.parallel.Isolated
class ChunkReadinessLifetimeContractTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    record Lease(List<WeakReference<?>> weak, Object[] root) {}
    static Lease abandon(boolean retain) {
        var fixture = new ChunkReadinessDependencyContractTest.Fixture();
        var result = fixture.start();
        List<WeakReference<?>> weak = new ArrayList<>();
        weak.add(new WeakReference<>(fixture.map));
        weak.add(new WeakReference<>(result));
        for (var leaf : fixture.leaves) weak.add(new WeakReference<>(leaf));
        // Release both Mockito harness roots without changing the actual
        // pending futures or their completion callbacks.
        org.mockito.Mockito.framework().clearInlineMocks();
        org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress().resetOngoingStubbing();
        return new Lease(weak, new Object[]{retain ? fixture.leaves.getFirst() : null});
    }
    static void collect(Lease lease) throws Exception {
        for (int attempt = 0; attempt < 160; attempt++) {
            System.gc(); Thread.sleep(20);
            if (lease.weak.stream().allMatch(reference -> reference.get() == null)) return;
        }
        assertTrue(lease.weak.stream().allMatch(reference -> reference.get() == null), "unreachable range retained a VM callback/resource cycle");
    }
    @Test void unreachablePendingRangeAndItsCallbackCycleAreCollectible() throws Exception {
        collect(abandon(false));
    }
    @Test void actualPendingPrerequisiteRetainsItsCallbackResourcesUntilReleased() throws Exception {
        Lease lease = abandon(true);
        for (int attempt = 0; attempt < 10; attempt++) { System.gc(); Thread.sleep(20); }
        assertNotNull(lease.weak.getFirst().get(), "live prerequisite lost the map needed by its callback");
        for (int index = 2; index < lease.weak.size(); index++) assertNotNull(lease.weak.get(index).get(), "live range lost a pending prerequisite");
        Reference.reachabilityFence(lease.root[0]);
        lease.root[0] = null;
        collect(lease);
    }
}
