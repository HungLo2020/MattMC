package net.minecraft.world.ticks;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Native-backed LevelChunkTicks against the unmodified original, operation by operation. */
class NativeChunkTicksTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    /** Objects the test scheduled into both containers; unpacked ticks are built separately per container. */
    private static final java.util.Set<Object> SHARED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /** Identity for shared objects, record equality for per-container unpacked ticks. */
    private static void matches(Object expected, Object actual, String where) {
        if (expected != null && SHARED.contains(expected)) assertSame(expected, actual, where);
        else assertEquals(expected, actual, where);
    }

    private static final class Pair {
        final LevelChunkTicks<Block> candidate;
        final OriginalLevelChunkTicks<Block> original;
        final List<Object> candidateEvents = new ArrayList<>(), originalEvents = new ArrayList<>();
        Pair(List<SavedTick<Block>> pending) {
            this.candidate = pending == null ? new LevelChunkTicks<>() : new LevelChunkTicks<>(new ArrayList<>(pending));
            this.original = pending == null ? new OriginalLevelChunkTicks<>() : new OriginalLevelChunkTicks<>(new ArrayList<>(pending));
            this.candidate.setOnTickAdded((c, t) -> this.candidateEvents.add(t));
            this.original.setOnTickAdded((c, t) -> this.originalEvents.add(t));
        }
        void same(String where) {
            matches(this.original.peek(), this.candidate.peek(), where + " peek identity");
            assertEquals(this.original.count(), this.candidate.count(), where + " count");
            assertIterableEquals(this.original.getAll().toList(), this.candidate.getAll().toList(), where + " getAll order");
            List<Object> a = this.original.getAll().map(t -> (Object)t).toList(), b = this.candidate.getAll().map(t -> (Object)t).toList();
            for (int i = 0; i < a.size(); i++) matches(a.get(i), b.get(i), where + " getAll identity " + i);
            assertEquals(this.originalEvents.size(), this.candidateEvents.size(), where + " listener calls");
            for (int i = 0; i < this.originalEvents.size(); i++) matches(this.originalEvents.get(i), this.candidateEvents.get(i), where + " listener " + i);
        }
    }

    /** Loaded after bootstrap. */
    private static final class Types {
        static final Block[] ALL = {Blocks.STONE, Blocks.REDSTONE_WIRE, Blocks.OBSERVER};
    }

    private static BlockPos pos(RandomSource r) {
        return switch (r.nextInt(8)) {
            case 0 -> new BlockPos(Integer.MIN_VALUE, Integer.MAX_VALUE, -1); // out-of-range coordinates must not alias
            case 1 -> new BlockPos(Integer.MIN_VALUE + (1 << 26), Integer.MAX_VALUE, -1);
            default -> new BlockPos(r.nextInt(4), r.nextInt(3) - 64, r.nextInt(4));
        };
    }

    private static ScheduledTick<Block> tick(RandomSource r, long[] sub) {
        // Narrow ranges force equal trigger ticks, priorities and even equal subTickOrders (comparator ties).
        long subTick = r.nextInt(5) == 0 ? sub[0] : ++sub[0];
        return shared(new ScheduledTick<>(Types.ALL[r.nextInt(Types.ALL.length)], pos(r), r.nextInt(6) + (r.nextInt(20) == 0 ? Long.MAX_VALUE - 3 : 0),
            TickPriority.values()[r.nextInt(TickPriority.values().length)], subTick));
    }

    private static <T> T shared(T tick) {
        SHARED.add(tick);
        return tick;
    }

    private static <T> List<Object> run(Runnable action) {
        try { action.run(); return List.of("ok"); } catch (RuntimeException e) { return List.of(e.getClass()); }
    }

    @Test void randomizedOperationsMatchTheOriginalContainer() {
        for (int seed = 0; seed < 300; seed++) {
            RandomSource r = RandomSource.create(seed);
            long[] sub = {0};
            List<SavedTick<Block>> pending = null;
            if (r.nextBoolean()) {
                pending = new ArrayList<>();
                for (int i = r.nextInt(12); i > 0; i--) {
                    // Saved lists may contain duplicates; they are neither deduplicated nor sorted.
                    pending.add(new SavedTick<>(Types.ALL[r.nextInt(Types.ALL.length)], pos(r), r.nextInt(7) - 3, TickPriority.values()[r.nextInt(7)]));
                }
            }
            Pair p = new Pair(pending);
            long gameTime = r.nextInt(1000) - 500;
            boolean unpacked = pending == null;
            for (int op = 0; op < 400; op++) {
                String where = "seed " + seed + " op " + op;
                switch (r.nextInt(unpacked ? 9 : 10)) {
                    case 0, 1, 2 -> {
                        ScheduledTick<Block> t = tick(r, sub);
                        p.original.schedule(t);
                        p.candidate.schedule(t);
                    }
                    case 3 -> matches(p.original.poll(), p.candidate.poll(), where + " poll");
                    case 4 -> {
                        BlockPos q = pos(r);
                        Block type = Types.ALL[r.nextInt(Types.ALL.length)];
                        assertEquals(p.original.hasScheduledTick(q, type), p.candidate.hasScheduledTick(q, type), where + " has");
                    }
                    case 5 -> {
                        // Generic predicate: records visit order; sometimes throws partway.
                        int mod = r.nextInt(3) + 2, throwAt = r.nextInt(4) == 0 ? r.nextInt(10) : -1;
                        List<Object> va = new ArrayList<>(), vb = new ArrayList<>();
                        Predicate<ScheduledTick<Block>> pa = t -> { if (va.size() == throwAt) throw new IllegalStateException("predicate"); va.add(t); return (t.subTickOrder() + t.pos().getX()) % mod == 0; };
                        Predicate<ScheduledTick<Block>> pb = t -> { if (vb.size() == throwAt) throw new IllegalStateException("predicate"); vb.add(t); return (t.subTickOrder() + t.pos().getX()) % mod == 0; };
                        assertEquals(run(() -> p.original.removeIf(pa)), run(() -> p.candidate.removeIf(pb)), where + " removeIf outcome");
                        assertEquals(va.size(), vb.size(), where + " predicate visits");
                        for (int i = 0; i < va.size(); i++) matches(va.get(i), vb.get(i), where + " visit " + i);
                    }
                    case 6 -> {
                        // A predicate that mutates the container mid-iteration fails fast identically.
                        ScheduledTick<Block> t = tick(r, sub);
                        assertEquals(run(() -> p.original.removeIf(x -> { p.original.schedule(t); return false; })),
                            run(() -> p.candidate.removeIf(x -> { p.candidate.schedule(t); return false; })), where + " reentrant removeIf");
                    }
                    case 7 -> {
                        long at = gameTime + r.nextInt(5) - (r.nextInt(30) == 0 ? (1L << 40) : 0); // narrowing int delay
                        assertEquals(p.original.pack(at), p.candidate.pack(at), where + " pack rows and order");
                    }
                    case 8 -> {
                        // Callback rescheduling: poll then immediately reschedule the same position/type.
                        ScheduledTick<Block> a = p.original.poll(), b = p.candidate.poll();
                        matches(a, b, where + " poll before reschedule");
                        if (a != null) {
                            ScheduledTick<Block> again = shared(new ScheduledTick<>(a.type(), a.pos(), a.triggerTick(), a.priority(), ++sub[0]));
                            p.original.schedule(again);
                            p.candidate.schedule(again);
                        }
                    }
                    case 9 -> {
                        gameTime += r.nextInt(3);
                        p.original.unpack(gameTime);
                        p.candidate.unpack(gameTime);
                        unpacked = true;
                    }
                    default -> throw new AssertionError();
                }
                p.same(where);
            }
            while (true) {
                ScheduledTick<Block> a = p.original.poll(), b = p.candidate.poll();
                matches(a, b, "seed " + seed + " drain");
                if (a == null) break;
            }
        }
    }

    @Test void pendingTicksCountAndBlockSchedulingButAreInvisibleUntilUnpacked() {
        BlockPos pos = new BlockPos(1, 2, 3);
        List<SavedTick<Block>> pending = List.of(new SavedTick<>(Blocks.STONE, pos, 2, TickPriority.HIGH), new SavedTick<>(Blocks.STONE, pos, 1, TickPriority.LOW));
        Pair p = new Pair(pending);
        assertEquals(2, p.candidate.count());
        assertTrue(p.candidate.hasScheduledTick(pos, Blocks.STONE));
        assertNull(p.candidate.peek());
        assertEquals(0, p.candidate.getAll().count());
        ScheduledTick<Block> extra = shared(new ScheduledTick<>(Blocks.STONE, pos, 5, 1));
        p.candidate.schedule(extra);
        p.original.schedule(extra);
        assertEquals(2, p.candidate.count(), "deduplicated against a pending key");
        assertEquals(p.original.pack(10), p.candidate.pack(10));
        p.original.unpack(10);
        p.candidate.unpack(10);
        assertEquals(2, p.candidate.getAll().count(), "pending duplicates are unpacked unchecked");
        p.same("after unpack");
    }

    @Test void unreachableContainersAreReleased() throws Exception {
        long before = NativeChunkTicks.liveContainers();
        for (int i = 0; i < 2000; i++) {
            var c = new LevelChunkTicks<Block>();
            c.schedule(new ScheduledTick<>(Blocks.STONE, new BlockPos(i, 0, 0), i, i));
        }
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (NativeChunkTicks.liveContainers() > before && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(20);
        }
        assertTrue(NativeChunkTicks.liveContainers() <= before, "retained " + (NativeChunkTicks.liveContainers() - before));
    }

    @Test void entriesAndTypeHandlesAreBoundedUnderChurnWithGenericTypes() {
        var c = new NativeChunkTicks<Object>();
        Object[] types = new Object[5];
        for (int i = 0; i < types.length; i++) types[i] = new Object(); // arbitrary generic T, not registry entries
        long sub = 0;
        for (int round = 0; round < 20_000; round++) {
            Object type = types[round % types.length];
            BlockPos pos = new BlockPos(round % 7, 0, 0);
            boolean added = c.schedule(new ScheduledTick<>(type, pos, round, TickPriority.NORMAL, sub++), false);
            if (!added) assertEquals(c.size(), c.liveEntries(), "a rejected duplicate allocates nothing");
            if (round % 3 == 0) c.poll();
            if (round % 11 == 0) {
                var it = c.iterator();
                while (it.hasNext()) if (it.next().pos().getX() == 3) it.remove();
            }
            assertEquals(c.size(), c.liveEntries(), "every freed entry is released");
        }
        assertEquals(types.length, c.typeHandles(), "handles are per container and bounded by its distinct types");
        while (c.poll() != null) { }
        assertEquals(0, c.liveEntries());
    }

    @Test void retainedIteratorsAndStreamsKeepTheNativeOwnerAlive() throws Exception {
        var c = new LevelChunkTicks<Block>();
        for (int i = 0; i < 50; i++) c.schedule(shared(new ScheduledTick<>(Blocks.STONE, new BlockPos(i, 0, 0), i, i)));
        var stream = c.getAll();
        c = null;
        for (int i = 0; i < 5; i++) { System.gc(); Thread.sleep(10); }
        assertEquals(50, stream.count(), "the stream's spliterator keeps the container reachable");
    }

    @Test void boxRemovalMatchesOriginalIteratorRemovalOnExtremeBounds() {
        int[] edges = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -17, -16, -1, 0, 15, 16, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
        for (int seed = 0; seed < 200; seed++) {
            RandomSource r = RandomSource.create(1000 + seed);
            Pair p = new Pair(null);
            long[] sub = {0};
            for (int i = 0; i < 60; i++) {
                BlockPos pos = new BlockPos(edges[r.nextInt(edges.length)], edges[r.nextInt(edges.length)], edges[r.nextInt(edges.length)]);
                ScheduledTick<Block> t = shared(new ScheduledTick<>(Types.ALL[r.nextInt(Types.ALL.length)], pos, r.nextInt(4), TickPriority.values()[r.nextInt(7)], r.nextInt(3) == 0 ? sub[0] : ++sub[0]));
                p.original.schedule(t);
                p.candidate.schedule(t);
            }
            int a = edges[r.nextInt(edges.length)], b = edges[r.nextInt(edges.length)], c = edges[r.nextInt(edges.length)];
            int d = edges[r.nextInt(edges.length)], e = edges[r.nextInt(edges.length)], f = edges[r.nextInt(edges.length)];
            var box = new net.minecraft.world.level.levelgen.structure.BoundingBox(Math.min(a, d), Math.min(b, e), Math.min(c, f), Math.max(a, d), Math.max(b, e), Math.max(c, f));
            p.original.removeIf(t -> box.isInside(t.pos()));
            p.candidate.removeInBox(box);
            p.same("seed " + seed + " box " + box);
            for (BlockPos q : List.of(new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE), new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE), new BlockPos(0, 0, 0))) {
                for (Block type : Types.ALL) assertEquals(p.original.hasScheduledTick(q, type), p.candidate.hasScheduledTick(q, type), "dedup keys after removal");
            }
            assertEquals(p.original.pack(0), p.candidate.pack(0));
        }
    }
}
