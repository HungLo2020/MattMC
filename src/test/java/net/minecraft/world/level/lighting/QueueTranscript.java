package net.minecraft.world.level.lighting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ChunkTracker;
import net.minecraft.world.level.ChunkPos;

/** Queue calls recorded from the original chunk-distance graph, not invented batches. */
final class QueueTranscript {
    record Call(int operation, long value, int level, int bound, int target, long output) {
        Call(int operation, long value, int level, int bound, long output) { this(operation, value, level, bound, 0, output); }
    }
    static final ThreadLocal<List<Call>> ACTIVE = new ThreadLocal<>();

    static final class RecordingQueue extends JavaLeveledPriorityQueue {
        final List<Call> calls;
        final int levels;
        int depth;
        RecordingQueue(int levels, int expected) { super(levels, expected); calls = ACTIVE.get(); this.levels = levels; }
        boolean beginAccess() { boolean acquired = depth++ == 0; if (calls != null) calls.add(new Call(4, 0, 0, acquired ? 1 : 0, 0)); return acquired; }
        void endAccess(boolean acquired) { depth--; if (calls != null) calls.add(new Call(5, 0, 0, acquired ? 1 : 0, 0)); }
        void reschedule(long value, int current, int previous, int next) {
            int old = priority(levels, current, previous), destination = priority(levels, current, next);
            if (previous != 255 && old != destination) super.dequeue(value, old, destination);
            super.enqueue(value, destination);
            if (calls != null) calls.add(new Call(6, value, current, previous, next, 0));
        }
        void cancelComputed(long value, int current, int computed) {
            super.dequeue(value, priority(levels, current, computed), levels);
            if (calls != null) calls.add(new Call(7, value, current, computed, 0));
        }
        void enqueueComputed(long value, int current, int computed) {
            super.enqueue(value, priority(levels, current, computed));
            if (calls != null) calls.add(new Call(8, value, current, computed, 0));
        }
        @Override public void enqueue(long value, int level) {
            super.enqueue(value, level);
            if (calls != null) calls.add(new Call(0, value, level, 0, 0));
        }
        @Override public void dequeue(long value, int level, int bound) {
            super.dequeue(value, level, bound);
            if (calls != null) calls.add(new Call(1, value, level, bound, 0));
        }
        @Override public long removeFirstLong() {
            long value = super.removeFirstLong();
            if (calls != null) calls.add(new Call(2, 0, 0, 0, value));
            return value;
        }
        @Override public boolean isEmpty() {
            boolean value = super.isEmpty();
            if (calls != null) calls.add(new Call(3, 0, 0, 0, value ? 1 : 0));
            return value;
        }
    }

    static long originalMutationCount(Call[] calls) {
        long count = 0;
        for (var call : calls) {
            if (call.operation() < 3 || call.operation() >= 6) count++;
            if (call.operation() == 6 && call.bound() != 255
                && priority(34, call.level(), call.bound()) != priority(34, call.level(), call.target())) count++;
        }
        return count;
    }

    static int priority(int levels, int current, int computed) {
        return Math.min(Math.min(current, computed), levels - 1);
    }

    // Literal queue actions extracted from the original graph, without native
    // ownership scopes. Keep this policy as the independent Java test oracle.
    static void rescheduleOriginal(JavaLeveledPriorityQueue queue, int levels, long value, int current, int previous, int next) {
        int old = priority(levels, current, previous), destination = priority(levels, current, next);
        if (previous != 255 && old != destination) queue.dequeue(value, old, destination);
        queue.enqueue(value, destination);
    }

    static Object replay(JavaLeveledPriorityQueue queue, int levels, Call call) {
        switch (call.operation()) {
            case 6: rescheduleOriginal(queue, levels, call.value(), call.level(), call.bound(), call.target()); return null;
            case 7: queue.dequeue(call.value(), priority(levels, call.level(), call.bound()), levels); return null;
            case 8: queue.enqueue(call.value(), priority(levels, call.level(), call.bound())); return null;
            case 0: queue.enqueue(call.value(), call.level()); return null;
            case 1: queue.dequeue(call.value(), call.level(), call.bound()); return null;
            case 2: return queue.removeFirstLong();
            case 3: return queue.isEmpty();
            default: throw new IllegalArgumentException("Not a queue operation: " + call.operation());
        }
    }

    static Object replay(LeveledPriorityQueue queue, Call call) {
        switch (call.operation()) {
            case 6: queue.reschedule(call.value(), call.level(), call.bound(), call.target()); return null;
            case 7: queue.cancelComputed(call.value(), call.level(), call.bound()); return null;
            case 8: queue.enqueueComputed(call.value(), call.level(), call.bound()); return null;
            case 0: queue.enqueue(call.value(), call.level()); return null;
            case 1: queue.dequeue(call.value(), call.level(), call.bound()); return null;
            case 2: return queue.removeFirstLong();
            case 3: return queue.isEmpty();
            default: throw new IllegalArgumentException("Not a queue operation: " + call.operation());
        }
    }

    interface Graph {
        void updateSource(long position, int level);
        int drain(int budget);
        int queueSize();
        boolean work();
        int[] values();
        List<Update> events();
    }

    // Identical backing-world callbacks for the original and production graph.
    record Update(long position, int level) {}

    static class World {
        final int[] values = new int[128 * 128], sources = new int[128 * 128];
        final List<Update> events = new ArrayList<>();
        World() { java.util.Arrays.fill(values, 33); java.util.Arrays.fill(sources, 33); }
        int index(long position) {
            int x = (int)position, z = (int)(position >>> 32);
            return x >= -64 && x < 64 && z >= -64 && z < 64 ? (z + 64) * 128 + x + 64 : -1;
        }
        int level(long position) { int i = index(position); return i < 0 ? 33 : values[i]; }
        int source(long position) { int i = index(position); return i < 0 ? 33 : sources[i]; }
        void set(long position, int level) {
            int i = index(position);
            if (i >= 0) { values[i] = level; events.add(new Update(position, level)); }
        }
        void source(long position, int level) { sources[index(position)] = level; }
    }

    static final class NativeGraph extends ChunkTracker implements Graph {
        final World world;
        NativeGraph() { this(new World()); }
        NativeGraph(World world) { super(34, 16, 256); this.world = world; }
        @Override protected int getLevel(long l) { return world.level(l); }
        @Override protected int getLevelFromSource(long l) { return world.source(l); }
        @Override protected void setLevel(long l, int i) { world.set(l, i); }
        public void updateSource(long l, int i) { world.source(l, i); update(l, i, false); }
        public int drain(int budget) { return runUpdates(budget); }
        public int queueSize() { return getQueueSize(); }
        public boolean work() { return hasWork(); }
        public int[] values() { return world.values; }
        public List<Update> events() { return world.events; }
    }

    static final class OriginalGraph extends JavaChunkTracker implements Graph {
        final World world;
        OriginalGraph() { this(new World()); }
        OriginalGraph(World world) { super(34, 16, 256); this.world = world; }
        @Override protected int getLevel(long l) { return world.level(l); }
        @Override protected int getLevelFromSource(long l) { return world.source(l); }
        @Override protected void setLevel(long l, int i) { world.set(l, i); }
        public void updateSource(long l, int i) { world.source(l, i); update(l, i, false); }
        public int drain(int budget) { return runUpdates(budget); }
        public int queueSize() { return getQueueSize(); }
        public boolean work() { return hasWork(); }
        public int[] values() { return world.values; }
        public List<Update> events() { return world.events; }
    }

    static final class RecordingGraph extends RecordingChunkTracker implements Graph {
        final World world = new World();
        RecordingGraph() { super(34, 16, 256); }
        @Override protected int getLevel(long l) { return world.level(l); }
        @Override protected int getLevelFromSource(long l) { return world.source(l); }
        @Override protected void setLevel(long l, int i) { world.set(l, i); }
        public void updateSource(long l, int i) { world.source(l, i); update(l, i, false); }
        public int drain(int budget) { return runUpdates(budget); }
        public int queueSize() { return getQueueSize(); }
        public boolean work() { return hasWork(); }
        public int[] values() { return world.values; }
        public List<Update> events() { return world.events; }
    }

    static void settle(NativeGraph graph, int budget) {
        int attempts = 0;
        while (graph.work()) {
            graph.drain(budget);
            if (++attempts > 100000) throw new AssertionError("Graph did not settle");
        }
    }

    static void settle(OriginalGraph graph, int budget) {
        int attempts = 0;
        while (graph.work()) {
            graph.drain(budget);
            if (++attempts > 100000) throw new AssertionError("Graph did not settle");
        }
    }

    static void settle(RecordingGraph graph, int budget) {
        int attempts = 0;
        while (graph.work()) {
            graph.drain(budget);
            if (++attempts > 100000) throw new AssertionError("Graph did not settle");
        }
    }

    static Call[] record(String name) {
        bootstrap();
        var calls = new ArrayList<Call>();
        ACTIVE.set(calls);
        try {
            var graph = new RecordingGraph();
            exercise(graph, name);
            if (graph.queueSize() != 0) throw new AssertionError("Transcript must return to an empty queue");
            var original = new OriginalGraph();
            exercise(original, name);
            if (!java.util.Arrays.equals(original.values(), graph.values())
                || !original.events().equals(graph.events())) throw new AssertionError("Recorded graph differs from literal original");
            int depth = 0;
            for (var call : calls) {
                if (call.operation() == 4) {
                    if ((call.bound() != 0) != (depth == 0)) throw new AssertionError("Unexpected scope acquisition");
                    depth++;
                } else if (call.operation() == 5) {
                    if (--depth < 0 || (call.bound() != 0) != (depth == 0)) throw new AssertionError("Unbalanced scope release");
                } else if ((call.operation() < 3 || call.operation() >= 6) && depth == 0) {
                    throw new AssertionError("Captured mutation has no production scope");
                }
            }
            if (depth != 0) throw new AssertionError("Transcript retains ownership");
            return calls.toArray(Call[]::new);
        } finally { ACTIVE.remove(); }
    }

    static void exercise(NativeGraph graph, String name) {
        int sources = name.startsWith("single_ticket") ? 1 : 8;
        int cycles = name.startsWith("ticket_churn") ? 3 : 1;
        int budget = name.endsWith("_incremental") ? 127 : Integer.MAX_VALUE;
        for (int cycle = 0; cycle < cycles; cycle++) {
            for (int i = 0; i < sources; i++) graph.updateSource(ChunkPos.asLong(i % 4 * 5 - 8, i / 4 * 5 - 5), 12 + (i + cycle) % 7);
            settle(graph, budget);
            for (int i = 0; i < sources; i++) graph.updateSource(ChunkPos.asLong(i % 4 * 5 - 8, i / 4 * 5 - 5), 33);
            settle(graph, budget);
        }
    }

    static void exercise(OriginalGraph graph, String name) {
        int sources = name.startsWith("single_ticket") ? 1 : 8;
        int cycles = name.startsWith("ticket_churn") ? 3 : 1;
        int budget = name.endsWith("_incremental") ? 127 : Integer.MAX_VALUE;
        for (int cycle = 0; cycle < cycles; cycle++) {
            for (int i = 0; i < sources; i++) graph.updateSource(ChunkPos.asLong(i % 4 * 5 - 8, i / 4 * 5 - 5), 12 + (i + cycle) % 7);
            settle(graph, budget);
            for (int i = 0; i < sources; i++) graph.updateSource(ChunkPos.asLong(i % 4 * 5 - 8, i / 4 * 5 - 5), 33);
            settle(graph, budget);
        }
    }

    static void exercise(RecordingGraph graph, String name) {
        int sources = name.startsWith("single_ticket") ? 1 : 8;
        int cycles = name.startsWith("ticket_churn") ? 3 : 1;
        int budget = name.endsWith("_incremental") ? 127 : Integer.MAX_VALUE;
        for (int cycle = 0; cycle < cycles; cycle++) {
            for (int i = 0; i < sources; i++) graph.updateSource(ChunkPos.asLong(i % 4 * 5 - 8, i / 4 * 5 - 5), 12 + (i + cycle) % 7);
            settle(graph, budget);
            for (int i = 0; i < sources; i++) graph.updateSource(ChunkPos.asLong(i % 4 * 5 - 8, i / 4 * 5 - 5), 33);
            settle(graph, budget);
        }
    }

    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
}
