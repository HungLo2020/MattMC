package net.minecraft.world.level.lighting;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeLeveledPriorityQueueTest {
    interface Action { Object run(); }
    record Failure(Class<?> type, String message) {}
    static Object result(Action action) {
        try { return action.run(); }
        catch (RuntimeException error) { return new Failure(error.getClass(), error.getMessage()); }
    }
    static void compare(JavaLeveledPriorityQueue a, LeveledPriorityQueue b, int op, long value, int level, int bound) {
        assertEquals(result(() -> operate(a, op, value, level, bound)), result(() -> operate(b, op, value, level, bound)),
            () -> "operation=" + op + " value=" + value + " level=" + level + " bound=" + bound);
        assertEquals(a.isEmpty(), b.isEmpty(), "State after call/failure");
    }
    static Object operate(JavaLeveledPriorityQueue queue, int op, long value, int level, int bound) {
        switch(op) {
            case 0: queue.enqueue(value, level); return null;
            case 1: queue.dequeue(value, level, bound); return null;
            case 2: return queue.removeFirstLong();
            default: return queue.isEmpty();
        }
    }
    static Object operate(LeveledPriorityQueue queue, int op, long value, int level, int bound) {
        switch(op) {
            case 0: queue.enqueue(value, level); return null;
            case 1: queue.dequeue(value, level, bound); return null;
            case 2: return queue.removeFirstLong();
            default: return queue.isEmpty();
        }
    }

    @Test void constructorsMatchOriginalValidation() {
        for (int levels : new int[]{-2, -1, 0, 1, 3, 34}) for (int expected : new int[]{-1, 0, 1, 16, 64, Integer.MAX_VALUE}) {
            assertEquals(result(() -> { new JavaLeveledPriorityQueue(levels, expected); return null; }),
                result(() -> { new LeveledPriorityQueue(levels, expected); return null; }), levels + ":" + expected);
        }
    }

    @Test void exhaustiveShortHistories() {
        // Every length-five history over insert/duplicate/remove/pop/empty actions.
        int histories = 0;
        for (int levels = 1; levels <= 3; levels++) for (int path = 0; path < 7776; path++) {
            var a = new JavaLeveledPriorityQueue(levels, 0);
            var b = new LeveledPriorityQueue(levels, 0);
            int remaining = path;
            for (int step = 0; step < 5; step++) {
                int action = remaining % 6; remaining /= 6;
                compare(a, b, action < 3 ? 0 : action == 3 ? 1 : action == 4 ? 2 : 3,
                    action == 1 ? Long.MIN_VALUE : 0, action == 2 ? levels - 1 : 0, levels);
            }
            for (int step = 0; step < 4; step++) compare(a, b, 2, 0, 0, levels);
            histories++;
        }
        System.out.println("LIGHT_QUEUE_EXHAUSTIVE histories=" + histories + " calls=" + (histories * 9));
    }

    @Test void seededTransitionsIncludingFailures() {
        long calls = 0;
        for (int levels : new int[]{0, 1, 3, 7, 16, 34, 253, 257}) for (int seed = 0; seed < 16; seed++) {
            var a = new JavaLeveledPriorityQueue(levels, seed % 4);
            var b = new LeveledPriorityQueue(levels, seed % 4);
            var random = new SplittableRandom(1977 + seed);
            for (int step = 0; step < 5000; step++) {
                long value = switch (step % 7) { case 0 -> 0; case 1 -> Long.MIN_VALUE; case 2 -> Long.MAX_VALUE; default -> random.nextLong(128); };
                int level = random.nextInt(levels + 2) - 1;
                int bound = switch (step % 5) { case 0 -> -1; case 1 -> Integer.MAX_VALUE; default -> random.nextInt(levels + 3); };
                compare(a, b, random.nextInt(4), value, level, bound);
                calls++;
            }
            for (int level = 0; level < levels; level++) compare(a, b, 0, 17, level, levels);
            for (int step = 0; step < levels * 129 + 1; step++) compare(a, b, 2, 0, 0, levels);
        }
        System.out.println("LIGHT_QUEUE_RANDOM calls=" + calls);
    }

    @Test void growthDeletionAndReinsertion() {
        var a = new JavaLeveledPriorityQueue(34, 0);
        var b = new LeveledPriorityQueue(34, 0);
        for (int i = 0; i < 65536; i++) compare(a, b, 0, i ^ Long.MIN_VALUE, i % 34, 34);
        for (int i = 0; i < 65536; i += 2) compare(a, b, 1, i ^ Long.MIN_VALUE, i % 34, 34);
        for (int i = 0; i < 65536; i += 2) compare(a, b, 0, i ^ Long.MIN_VALUE, i % 34, 34);
        while (!a.isEmpty()) compare(a, b, 2, 0, 0, 34);
        assertTrue(b.isEmpty());
    }

    @Test void actualChunkDistanceGraphMatchesAtEveryBudget() {
        QueueTranscript.bootstrap();
        var a = new QueueTranscript.OriginalGraph();
        var b = new QueueTranscript.NativeGraph();
        var random = new SplittableRandom(1977);
        for (int phase = 0; phase < 40; phase++) {
            long position = ChunkPos.asLong(random.nextInt(17) - 8, random.nextInt(17) - 8);
            int level = phase % 3 == 0 ? 33 : random.nextInt(20, 31);
            a.updateSource(position, level); b.updateSource(position, level);
            do {
                int budget = random.nextInt(1, 128);
                assertEquals(a.drain(budget), b.drain(budget));
                assertEquals(a.queueSize(), b.queueSize());
                assertEquals(a.work(), b.work());
                assertArrayEquals(a.values(), b.values());
                assertEquals(a.events(), b.events(), "Exact update callback order");
                a.events().clear(); b.events().clear();
            } while (a.work());
        }
        System.out.println("LIGHT_QUEUE_GRAPH phases=40 state_and_callback_order=exact");
    }

    @Test void independentOwnersSurviveCollectionAndThreadHandoff() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int worker = 0; worker < 4; worker++) futures.add(executor.submit(() -> {
                for (int round = 0; round < 100; round++) {
                    var a = new JavaLeveledPriorityQueue(16, 0);
                    var b = new LeveledPriorityQueue(16, 0);
                    for (int i = 0; i < 256; i++) compare(a, b, 0, i, i % 16, 16);
                    if (round % 10 == 0) System.gc();
                    while (!a.isEmpty()) compare(a, b, 2, 0, 0, 16);
                }
            }));
            for (var future : futures) future.get();
            var queue = new LeveledPriorityQueue(3, 0);
            executor.submit(() -> queue.enqueue(Long.MIN_VALUE, 1)).get();
            assertEquals(Long.MIN_VALUE, queue.removeFirstLong());
        } finally { executor.shutdownNow(); }
    }

    @Test void anAccessLeaseExcludesOtherCallersAndSupportsReentry() throws Exception {
        var queue = new LeveledPriorityQueue(34, 0);
        var executor = Executors.newSingleThreadExecutor();
        Future<?> future;
        var attempted = new CountDownLatch(1);
        boolean outer = queue.beginAccess();
        try {
            boolean inner = queue.beginAccess();
            assertTrue(outer);
            assertFalse(inner);
            try { queue.enqueue(17, 3); } finally { queue.endAccess(inner); }
            future = executor.submit(() -> { attempted.countDown(); queue.enqueue(23, 3); });
            assertTrue(attempted.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> future.get(100, TimeUnit.MILLISECONDS));
            assertEquals(17, queue.removeFirstLong());
            assertTrue(queue.isEmpty());
        } finally { queue.endAccess(outer); }
        try {
            future.get(10, TimeUnit.SECONDS);
            assertEquals(23, queue.removeFirstLong());
            assertTrue(queue.isEmpty());
        } finally { executor.shutdownNow(); }
    }

    @Test void failedOperationsReleaseOwnershipAndKeepOriginalFailureState() throws Exception {
        var original = new JavaLeveledPriorityQueue(3, 0);
        var nativeQueue = new LeveledPriorityQueue(3, 0);
        var executor = Executors.newSingleThreadExecutor();
        try {
            boolean acquired = nativeQueue.beginAccess();
            try {
                compare(original, nativeQueue, 0, 17, 0, 3);
                compare(original, nativeQueue, 1, 17, 0, Integer.MAX_VALUE);
                compare(original, nativeQueue, 2, 0, 0, 3);
            } finally { nativeQueue.endAccess(acquired); }
            executor.submit(() -> nativeQueue.enqueue(23, 1)).get(10, TimeUnit.SECONDS);
            original.enqueue(23, 1);
            compare(original, nativeQueue, 2, 0, 0, 3);
            // Unscoped failures must also release the internally acquired lock.
            compare(original, nativeQueue, 0, 17, -1, 3);
            executor.submit(() -> nativeQueue.enqueue(29, 0)).get(10, TimeUnit.SECONDS);
            original.enqueue(29, 0);
            compare(original, nativeQueue, 2, 0, 0, 3);
        } finally { executor.shutdownNow(); }
    }


    @Test void adversarialWrappedCollisionsMatchThroughProductionRetryPath() {
        long phi = 0x9e3779b97f4a7c15L, inverse = 1;
        for (int i = 0; i < 6; i++) inverse *= 2 - phi * inverse;
        long[] positions = new long[128];
        for (int i = 0; i < positions.length; i++) {
            long hash = ((long)i << 11) | 2047;
            long mixed = hash ^ (hash >>> 16) ^ (hash >>> 32) ^ (hash >>> 48);
            positions[i] = (mixed ^ (mixed >>> 32)) * inverse;
        }
        for (int levels : new int[]{34, 257}) {
            var original = new JavaLeveledPriorityQueue(levels, 256);
            var nativeQueue = new LeveledPriorityQueue(levels, 256);
            boolean acquired = nativeQueue.beginAccess();
            try {
                for (long position : positions) compare(original, nativeQueue, 0, position, 7, levels);
                for (long position : positions) compare(original, nativeQueue, 0, position, 7, levels);
                for (int i : new int[]{127, 64, 0}) compare(original, nativeQueue, 1, positions[i], 7, levels);
                for (int i : new int[]{64, 127, 0}) compare(original, nativeQueue, 0, positions[i], 7, levels);
                while (!original.isEmpty()) compare(original, nativeQueue, 2, 0, 0, levels);
                compare(original, nativeQueue, 2, 0, 0, levels);
            } finally { nativeQueue.endAccess(acquired); }
        }
    }


    @Test void nativeSchedulingMatchesOriginalPriorityAndQueueActions() {
        long calls = 0;
        for (int levels : new int[]{0, 1, 7, 34, 253}) for (int seed = 0; seed < 8; seed++) {
            var original = new JavaLeveledPriorityQueue(levels, seed % 4);
            var nativeQueue = new LeveledPriorityQueue(levels, seed % 4);
            var random = new SplittableRandom(7331 + seed);
            boolean acquired = nativeQueue.beginAccess();
            try {
                for (int i = 0; i < 1000; i++) {
                    int current = i % 17 == 0 ? Integer.MIN_VALUE : random.nextInt(levels + 3) - 1;
                    int previous = i % 5 == 0 ? 255 : random.nextInt(levels + 3) - 1;
                    int next = i % 19 == 0 ? Integer.MAX_VALUE : random.nextInt(levels + 3) - 1;
                    int operation = i % 7 < 4 ? 6 : i % 7 == 4 ? 7 : i % 7 == 5 ? 8 : 2;
                    var call = new QueueTranscript.Call(operation, i % 11 == 0 ? Long.MIN_VALUE : random.nextLong(128), current, previous, next, 0);
                    assertEquals(result(() -> QueueTranscript.replay(original, levels, call)),
                                 result(() -> QueueTranscript.replay(nativeQueue, call)), "Scheduling operation: " + call);
                    assertEquals(original.isEmpty(), nativeQueue.isEmpty(), "Post-scheduling/failure state");
                    calls++;
                }
            } finally { nativeQueue.endAccess(acquired); }
        }
        System.out.println("LIGHT_QUEUE_SCHEDULING calls=" + calls + " priority_and_failure_state=exact");
    }


    static final class ScalarState {
        final Map<Long, Integer> values = new HashMap<>();
        final List<String> callbacks = new ArrayList<>();
        int computed;
        int level(long position) { callbacks.add("read:" + position); return values.getOrDefault(position, 0); }
        void set(long position, int level) { callbacks.add("write:" + position + ":" + level); values.put(position, level); }
    }

    static final class ScalarNativeGraph extends DynamicGraphMinFixedPoint {
        final ScalarState state = new ScalarState();
        ScalarNativeGraph(int levels) { super(levels, 1, 1); }
        void schedule(long position, int edge, boolean decrease) { checkEdge(SOURCE, position, edge, decrease); }
        int drain(int budget) { return runUpdates(budget); }
        boolean work() { return hasWork(); }
        @Override protected int getComputedLevel(long target, long source, int edge) { state.callbacks.add("computed:" + target); return state.computed; }
        @Override protected void checkNeighborsAfterUpdate(long position, int level, boolean decrease) { state.callbacks.add("neighbors:" + position + ":" + level + ":" + decrease); }
        @Override protected int getLevel(long position) { return state.level(position); }
        @Override protected void setLevel(long position, int level) { state.set(position, level); }
        @Override protected int computeLevelFromNeighbor(long source, long target, int level) { return level; }
    }

    static final class ScalarOriginalGraph extends JavaDynamicGraphMinFixedPoint {
        final ScalarState state = new ScalarState();
        ScalarOriginalGraph(int levels) { super(levels, 1, 1); }
        void schedule(long position, int edge, boolean decrease) { checkEdge(SOURCE, position, edge, decrease); }
        int drain(int budget) { return runUpdates(budget); }
        boolean work() { return hasWork(); }
        @Override protected int getComputedLevel(long target, long source, int edge) { state.callbacks.add("computed:" + target); return state.computed; }
        @Override protected void checkNeighborsAfterUpdate(long position, int level, boolean decrease) { state.callbacks.add("neighbors:" + position + ":" + level + ":" + decrease); }
        @Override protected int getLevel(long position) { return state.level(position); }
        @Override protected void setLevel(long position, int level) { state.set(position, level); }
        @Override protected int computeLevelFromNeighbor(long source, long target, int level) { return level; }
    }

    @Test void graphClampingSentinelAndRepeatedPendingWritesMatch() {
        long calls = 0;
        for (int levels : new int[]{0, 1, 2, 7, 34, 253}) {
            var original = new ScalarOriginalGraph(levels);
            var nativeGraph = new ScalarNativeGraph(levels);
            int[] inputs = {Integer.MIN_VALUE, -1, 0, 1, levels - 1, levels, levels + 1, Integer.MAX_VALUE};
            for (int current : inputs) for (int computed : inputs) for (boolean decrease : new boolean[]{false, true}) {
                long position = calls % 7;
                original.state.values.put(position, current); nativeGraph.state.values.put(position, current);
                original.state.computed = computed; nativeGraph.state.computed = computed;
                for (int repeat = 0; repeat < 3; repeat++) {
                    assertEquals(result(() -> { original.schedule(position, computed, decrease); return null; }),
                                 result(() -> { nativeGraph.schedule(position, computed, decrease); return null; }));
                    assertEquals(original.getQueueSize(), nativeGraph.getQueueSize());
                    assertEquals(original.work(), nativeGraph.work());
                    assertEquals(original.state.callbacks, nativeGraph.state.callbacks);
                    calls++;
                }
                for (int budget : new int[]{0, 1, 3, 32}) {
                    assertEquals(result(() -> original.drain(budget)), result(() -> nativeGraph.drain(budget)));
                    assertEquals(original.getQueueSize(), nativeGraph.getQueueSize());
                    assertEquals(original.work(), nativeGraph.work());
                    assertEquals(original.state.values, nativeGraph.state.values);
                    assertEquals(original.state.callbacks, nativeGraph.state.callbacks);
                    calls++;
                }
                original.state.callbacks.clear(); nativeGraph.state.callbacks.clear();
            }
        }
        System.out.println("LIGHT_QUEUE_CLAMPING calls=" + calls + " levels_and_pending_write_state=exact");
    }

    static class FailingWorld extends QueueTranscript.World {
        int reads = -1, writes = -1;
        boolean afterWrite;
        @Override int level(long position) {
            if (reads >= 0 && reads-- == 0) throw new IllegalStateException("Injected level-read failure");
            return super.level(position);
        }
        @Override void set(long position, int level) {
            boolean fail = writes >= 0 && writes-- == 0;
            if (fail && !afterWrite) throw new IllegalStateException("Injected write failure");
            super.set(position, level);
            if (fail) throw new IllegalStateException("Injected write failure");
        }
    }

    @Test void callbackFailuresPreserveStateAndLaterRescheduling() {
        QueueTranscript.bootstrap();
        for (int stage = 0; stage < 3; stage++) for (int at : new int[]{0, 1, 17, 64}) {
            var aWorld = new FailingWorld(); var bWorld = new FailingWorld();
            var original = new QueueTranscript.OriginalGraph(aWorld);
            var nativeGraph = new QueueTranscript.NativeGraph(bWorld);
            original.updateSource(0, 28); nativeGraph.updateSource(0, 28);
            if (stage == 0) { aWorld.reads = at; bWorld.reads = at; }
            else { aWorld.writes = at; bWorld.writes = at; aWorld.afterWrite = bWorld.afterWrite = stage == 2; }
            for (int step = 0; step < 12; step++) {
                assertEquals(result(() -> original.drain(127)), result(() -> nativeGraph.drain(127)));
                assertEquals(original.queueSize(), nativeGraph.queueSize());
                assertEquals(original.work(), nativeGraph.work());
                assertArrayEquals(original.values(), nativeGraph.values());
                assertEquals(original.events(), nativeGraph.events(), "Post-failure callback order");
                long position = ChunkPos.asLong(step % 3 - 1, step % 5 - 2);
                int sourceLevel = 27 + step % 3;
                assertEquals(result(() -> { original.updateSource(position, sourceLevel); return null; }),
                             result(() -> { nativeGraph.updateSource(position, sourceLevel); return null; }));
            }
        }
    }

    static class ReentrantWorld extends QueueTranscript.World {
        Runnable nextWrite;
        @Override void set(long position, int level) {
            super.set(position, level);
            if (nextWrite != null) {
                Runnable hook = nextWrite; nextWrite = null; hook.run();
            }
        }
    }

    @Test void graphCallbacksMayReenterTheirOwnSchedulingScope() {
        QueueTranscript.bootstrap();
        var aWorld = new ReentrantWorld(); var bWorld = new ReentrantWorld();
        var original = new QueueTranscript.OriginalGraph(aWorld);
        var nativeGraph = new QueueTranscript.NativeGraph(bWorld);
        aWorld.nextWrite = () -> original.updateSource(ChunkPos.asLong(2, -3), 29);
        bWorld.nextWrite = () -> nativeGraph.updateSource(ChunkPos.asLong(2, -3), 29);
        original.updateSource(0, 28); nativeGraph.updateSource(0, 28);
        for (int guard = 0; original.work() && guard < 1000; guard++) {
            assertEquals(original.drain(7), nativeGraph.drain(7));
            assertEquals(original.queueSize(), nativeGraph.queueSize());
            assertEquals(original.work(), nativeGraph.work());
            assertArrayEquals(original.values(), nativeGraph.values());
            assertEquals(original.events(), nativeGraph.events(), "Reentrant callback order");
        }
        assertFalse(original.work()); assertFalse(nativeGraph.work());
    }


    static long useEphemeralQueue(long value) {
        var queue = new LeveledPriorityQueue(1, 0);
        queue.enqueue(value, 0);
        return queue.removeFirstLong();
    }

    @Test void numericHandlesKeepTheirLifetimeOwnerAliveDuringCollection() throws Exception {
        // Warm the short-lived owner path so this checks optimized calls as well
        // as interpreted ones; only the arena/fences keep the native handle alive.
        for (int i = 0; i < 5000; i++) assertEquals((long)i, useEphemeralQueue(i));
        var executor = Executors.newFixedThreadPool(3);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                start.await();
                for (long i = 0; i < 2000; i++) assertEquals(i ^ Long.MIN_VALUE, useEphemeralQueue(i ^ Long.MIN_VALUE));
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                for (long i = 0; i < 2000; i++) assertEquals(~i, useEphemeralQueue(~i));
                return null;
            });
            var collector = executor.submit(() -> {
                start.await();
                for (int i = 0; i < 20; i++) System.gc();
                return null;
            });
            start.countDown();
            first.get(30, TimeUnit.SECONDS); second.get(30, TimeUnit.SECONDS); collector.get(30, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }

}
