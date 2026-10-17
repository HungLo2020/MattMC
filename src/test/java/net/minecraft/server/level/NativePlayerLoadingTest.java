package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/** Bridge lifetime/concurrency and observable fastutil admission contracts.
 * Full policy/effect ordering is checked by VerifyPlayerLoading on the actual
 * Frozen and pre-change classes, rather than a copied expected policy here. */
class NativePlayerLoadingTest {
    static List<Long> keys(NativePlayerLoading.Admission owner) {
        List<Long> result = new ArrayList<>();
        try (NativePlayerLoading.Walk walk = owner.walk()) {
            int action;
            while ((action = walk.next(-1)) != NativePlayerLoading.DONE) {
                assertEquals(NativePlayerLoading.KEY, action);
                result.add(walk.key());
            }
        }
        return result;
    }

    @Test
    void admissionMatchesActualLongOpenHashSetOrderAndDistinctCount() {
        NativePlayerLoading.Admission candidate = new NativePlayerLoading.Admission(4);
        LongOpenHashSet original = new LongOpenHashSet();
        Random random = new Random(2901);
        for (int step = 0; step < 5000; step++) {
            long key = random.nextInt(5) == 0 ? random.nextLong() : random.nextInt(256) - 128;
            if (random.nextBoolean()) {
                original.add(key);
                candidate.started(key);
            } else {
                original.remove(key);
                candidate.released(key);
            }
            assertEquals(original.size() < 4, candidate.mayPop(), "step " + step);
            if (step % 23 == 0) {
                List<Long> expected = original.longStream().boxed().toList();
                assertEquals(expected, keys(candidate), "step " + step);
            }
        }
        candidate.started(0);
        candidate.started(0);
        candidate.released(0);
        assertFalse(keys(candidate).contains(0L));
        assertFalse(new NativePlayerLoading.Admission(0).mayPop());
        assertFalse(new NativePlayerLoading.Admission(-1).mayPop());
    }

    record Retained(WeakReference<NativePlayerLoading.Admission> owner, NativePlayerLoading.Walk walk) {}

    static Retained retained() {
        NativePlayerLoading.Admission owner = new NativePlayerLoading.Admission(4);
        owner.started(0);
        owner.started(19);
        return new Retained(new WeakReference<>(owner), owner.walk());
    }

    @Test
    void anInvocationKeepsItsNativeOwnerAliveAfterTheJavaFacadeIsCollected() throws Exception {
        Retained retained = retained();
        try (NativePlayerLoading.Walk walk = retained.walk()) {
            for (int attempt = 0; attempt < 50 && retained.owner().get() != null; attempt++) {
                System.gc();
                Thread.sleep(10);
            }
            assertNull(retained.owner().get(), "the fixture must actually release its Java facade");
            List<Long> values = new ArrayList<>();
            while (walk.next(-1) != NativePlayerLoading.DONE) values.add(walk.key());
            assertEquals(List.of(0L, 19L), values);
        }
        assertThrows(IllegalStateException.class, () -> retained.walk().next(-1));
        assertThrows(IllegalStateException.class, () -> retained.walk().key());
        assertThrows(IllegalStateException.class, () -> retained.walk().level());
    }

    @Test
    @Timeout(15)
    void queuedLevelReadsAndWritesRemainSafeAcrossExecutorThreads() throws Exception {
        NativePlayerLoading owner = new NativePlayerLoading(32);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();
        for (int worker = 0; worker < 3; worker++) {
            int id = worker;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int index = 0; index < 40000; index++) {
                        long key = index & 15;
                        if (id == 0) owner.queueLevel(key, index % 40);
                        else {
                            int level = owner.queueLevel(key);
                            if (level < 0 || level > 34) throw new AssertionError("torn queue level " + level);
                        }
                    }
                } catch (Throwable error) { failure.compareAndSet(null, error); }
            }, "player-loading-level-" + worker);
            thread.start();
            threads.add(thread);
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        assertNull(failure.get());
    }

    @Test
    void repeatedQueueAndAdmissionChurnReturnsToItsRetainedCapacityBound() {
        NativePlayerLoading policy = new NativePlayerLoading(32);
        NativePlayerLoading.Admission admission = new NativePlayerLoading.Admission(4);
        long policyInitial = policy.retainedBytes(), admissionInitial = admission.retainedBytes();
        for (int round = 0; round < 20; round++) {
            for (int key = 0; key < 2500; key++) {
                policy.queueLevel(key, key % 34);
                admission.started(key);
            }
            assertTrue(policy.retainedBytes() > policyInitial);
            assertTrue(admission.retainedBytes() > admissionInitial);
            for (int key = 0; key < 2500; key++) {
                policy.queueLevel(key, 34);
                admission.released(key);
            }
            assertEquals(policyInitial, policy.retainedBytes(), "policy capacity after round " + round);
            assertEquals(admissionInitial, admission.retainedBytes(), "admission capacity after round " + round);
        }
    }

    @Test
    void aWalkRejectsAnotherThreadBeforeAccessingItsInvocationStorage() throws Exception {
        NativePlayerLoading.Admission owner = new NativePlayerLoading.Admission(4);
        owner.started(0);
        try (NativePlayerLoading.Walk walk = owner.walk()) {
            AtomicReference<Throwable> unexpected = new AtomicReference<>();
            Thread other = new Thread(() -> {
                try {
                    assertThrows(IllegalStateException.class, () -> walk.next(-1));
                    assertThrows(IllegalStateException.class, walk::close);
                } catch (Throwable error) { unexpected.set(error); }
            });
            other.start();
            other.join();
            assertNull(unexpected.get());
            assertEquals(NativePlayerLoading.KEY, walk.next(-1));
            assertEquals(0, walk.key());
        }
    }
}
