package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.TicketStorage;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact parity of the native-owned loading tracker with the pinned original.
 * Each tracker drives its own DistanceManager whose holder scheduling copies
 * ChunkMap's, attached to its own real TicketStorage driven identically. */
class NativeLoadingChunkTrackerTest {
    static TicketType[] TYPES;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Load-only, simulation-only and both, with and without timeouts.
        TYPES = new TicketType[]{
            TicketType.PLAYER_LOADING, TicketType.PLAYER_SIMULATION, TicketType.DRAGON, TicketType.FORCED,
            TicketType.PORTAL, TicketType.ENDER_PEARL, TicketType.UNKNOWN
        };
    }

    record Change(long position, int level) {}

    /** ChunkMap's holder bookkeeping for one DistanceManager. */
    static final class Manager extends DistanceManager {
        static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(0, 16);
        final Long2ObjectOpenHashMap<ChunkHolder> holders = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<ChunkHolder> pendingUnloads = new Long2ObjectOpenHashMap<>();
        final LongOpenHashSet toDrop = new LongOpenHashSet();

        Manager(TicketStorage storage) {
            super(storage, Runnable::run, Runnable::run);
        }

        @Override protected boolean isChunkToRemove(long l) { return this.toDrop.contains(l); }

        @Nullable @Override protected ChunkHolder getChunk(long l) { return this.holders.get(l); }

        // ChunkMap.updateChunkScheduling without its unrelated `modified` flag.
        @Nullable @Override
        protected ChunkHolder updateChunkScheduling(long l, int i, @Nullable ChunkHolder chunkHolder, int j) {
            if (!ChunkLevel.isLoaded(j) && !ChunkLevel.isLoaded(i)) return chunkHolder;
            if (chunkHolder != null) chunkHolder.setTicketLevel(i);
            if (chunkHolder != null) {
                if (!ChunkLevel.isLoaded(i)) this.toDrop.add(l); else this.toDrop.remove(l);
            }
            if (ChunkLevel.isLoaded(i) && chunkHolder == null) {
                chunkHolder = this.pendingUnloads.remove(l);
                if (chunkHolder != null) chunkHolder.setTicketLevel(i);
                else chunkHolder = new ChunkHolder(new ChunkPos(l), i, HEIGHT, null, (pos, level, setter, nextLevel) -> {}, null);
                this.holders.put(l, chunkHolder);
            }
            return chunkHolder;
        }

        /** ChunkMap.processUnloads' holder moves. */
        void processUnloads() {
            for (long l : this.toDrop.toLongArray()) {
                ChunkHolder chunkHolder = this.holders.remove(l);
                if (chunkHolder != null) this.pendingUnloads.put(l, chunkHolder);
            }
            this.toDrop.clear();
        }

        String state(long[] probes) {
            StringBuilder state = new StringBuilder();
            for (long probe : probes) {
                ChunkHolder holder = this.holders.get(probe);
                state.append(probe).append(':').append(holder == null ? "-" : holder.getTicketLevel()).append(this.toDrop.contains(probe) ? "d" : "")
                    .append(this.pendingUnloads.containsKey(probe) ? "p" : "").append(';');
            }
            long[] futures = this.chunksToUpdateFutures.stream().mapToLong(holder -> holder.getPos().toLong()).sorted().toArray();
            return state.append(Arrays.toString(futures)).toString();
        }
    }

    static final class Original extends JavaLoadingChunkTracker {
        final List<Change> changes = new ArrayList<>();
        Original(Manager manager, TicketStorage storage) { super(manager, storage); }
        @Override protected void setLevel(long l, int i) { this.changes.add(new Change(l, i)); super.setLevel(l, i); }
        int level(long l) { return this.getLevel(l); }
    }

    static final class Native extends LoadingChunkTracker {
        final List<Change> changes = new ArrayList<>();
        Native(Manager manager, TicketStorage storage) { super(manager, storage); }
        @Override protected void setLevel(long l, int i) { this.changes.add(new Change(l, i)); super.setLevel(l, i); }
        int level(long l) { return this.getLevel(l); }
    }

    static final class Pair {
        final TicketStorage originalStorage = new TicketStorage();
        final TicketStorage nativeStorage = new TicketStorage();
        final Manager originalManager = new Manager(this.originalStorage);
        final Manager nativeManager = new Manager(this.nativeStorage);
        Original original;
        Native candidate;

        // Constructing the trackers replaces each DistanceManager's own listener.
        void attach() {
            this.original = new Original(this.originalManager, this.originalStorage);
            this.candidate = new Native(this.nativeManager, this.nativeStorage);
        }

        void add(long position, TicketType type, int level) {
            assertEquals(this.originalStorage.addTicket(position, new Ticket(type, level)), this.nativeStorage.addTicket(position, new Ticket(type, level)));
        }

        void remove(long position, TicketType type, int level) {
            assertEquals(this.originalStorage.removeTicket(position, new Ticket(type, level)), this.nativeStorage.removeTicket(position, new Ticket(type, level)));
        }

        /** Returns and compares the remaining budget, or the identical exception
         * (out-of-bounds holder creation) both throw from setLevel mid-run. */
        void run(int budget, String context) {
            Object expected, actual;
            try { expected = this.original.runDistanceUpdates(budget); }
            catch (RuntimeException error) { expected = error.getClass() + ":" + error.getMessage(); }
            try { actual = this.candidate.runDistanceUpdates(budget); }
            catch (RuntimeException error) { actual = error.getClass() + ":" + error.getMessage(); }
            assertEquals(expected, actual, () -> context + " remaining budget or failure");
            if (expected instanceof String) this.failures++;
        }

        int failures;

        void processUnloads() {
            this.originalManager.processUnloads();
            this.nativeManager.processUnloads();
        }

        void clearFutures() {
            this.originalManager.chunksToUpdateFutures.clear();
            this.nativeManager.chunksToUpdateFutures.clear();
        }

        void compare(long[] probes, String context) {
            assertEquals(this.original.changes, this.candidate.changes, () -> context + " setLevel transcript");
            assertEquals(this.originalManager.state(probes), this.nativeManager.state(probes), () -> context + " holder state");
            for (long probe : probes) {
                assertEquals(this.original.level(probe), this.candidate.level(probe), () -> context + " level " + probe);
            }
        }
    }

    static long offset(long position, int dx, int dz) {
        return ChunkPos.asLong((int)position + dx, (int)(position >> 32) + dz);
    }

    static long[] probes(long anchor, int radius) {
        List<Long> probes = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) probes.add(offset(anchor, dx, dz));
        probes.add(ChunkPos.INVALID_CHUNK_POS);
        return probes.stream().mapToLong(Long::longValue).toArray();
    }

    @Test void exhaustiveShortTicketHistories() {
        // Every length-four history over two chunks: add/remove of load-only,
        // dual and simulation-only tickets, a one-node run, a full run and unloads.
        long[] chunks = {ChunkPos.asLong(0, 0), ChunkPos.asLong(3, 1)};
        int[][] kinds = {{0, 31}, {2, 30}, {1, 21}};
        long[] probes = probes(chunks[0], 18);
        int actions = 2 * 3 * 2 + 3, histories = 0;
        for (int path = 0, total = (int)Math.pow(actions, 4); path < total; path++) {
            Pair pair = new Pair();
            pair.attach();
            int remaining = path;
            for (int step = 0; step < 4; step++) {
                int action = remaining % actions;
                remaining /= actions;
                String context = "history " + path + " step " + step;
                if (action == 12) pair.run(1, context);
                else if (action == 13) pair.run(Integer.MAX_VALUE, context);
                else if (action == 14) pair.processUnloads();
                else {
                    long chunk = chunks[action / 6];
                    int[] kind = kinds[(action / 2) % 3];
                    if (action % 2 == 0) pair.add(chunk, TYPES[kind[0]], kind[1]);
                    else pair.remove(chunk, TYPES[kind[0]], kind[1]);
                }
                pair.compare(probes, context);
            }
            pair.run(Integer.MAX_VALUE, "history " + path);
            pair.compare(probes, "history " + path + " settled");
            histories++;
        }
        System.out.println("LOADING_DISTANCE_EXHAUSTIVE histories=" + histories);
    }

    static void churn(long seed, long anchor, int steps, int spread, boolean preexisting) {
        Random random = new Random(seed);
        Pair pair = new Pair();
        List<long[]> live = new ArrayList<>();
        if (preexisting) {
            // Tickets present before the trackers attach are read as the original reads them.
            for (int index = 0; index < 6; index++) {
                long chunk = offset(anchor, random.nextInt(spread) - spread / 2, random.nextInt(spread) - spread / 2);
                int type = random.nextInt(TYPES.length), level = 22 + random.nextInt(26);
                pair.add(chunk, TYPES[type], level);
                live.add(new long[]{chunk, type, level});
            }
        }
        pair.attach();
        long[] probes = probes(anchor, spread / 2 + 16);
        for (int step = 0; step < steps; step++) {
            String context = "seed " + seed + " step " + step;
            int action = random.nextInt(24);
            if (action < 9 || live.isEmpty()) {
                long chunk = offset(anchor, random.nextInt(spread) - spread / 2, random.nextInt(spread) - spread / 2);
                int type = random.nextInt(TYPES.length), level = 22 + random.nextInt(26);
                pair.add(chunk, TYPES[type], level);
                live.add(new long[]{chunk, type, level});
            } else if (action < 16) {
                long[] ticket = live.remove(random.nextInt(live.size()));
                pair.remove(ticket[0], TYPES[(int)ticket[1]], (int)ticket[2]);
            } else if (action == 16) {
                int level = 22 + random.nextInt(12);
                pair.originalStorage.replaceTicketLevelOfType(level, TicketType.PLAYER_LOADING);
                pair.nativeStorage.replaceTicketLevelOfType(level, TicketType.PLAYER_LOADING);
                for (long[] ticket : live) if (ticket[1] == 0) ticket[2] = level;
            } else if (action == 17) {
                pair.originalStorage.deactivateTicketsOnClosing();
                pair.nativeStorage.deactivateTicketsOnClosing();
                pair.run(Integer.MAX_VALUE, context);
                pair.compare(probes, context + " closed");
                pair.originalStorage.activateAllDeactivatedTickets();
                pair.nativeStorage.activateAllDeactivatedTickets();
            } else if (action == 18) {
                pair.processUnloads();
            } else if (action == 19) {
                pair.clearFutures();
            } else if (action == 20) {
                pair.run(1 + random.nextInt(40), context);
                pair.compare(probes, context);
            } else {
                pair.run(Integer.MAX_VALUE, context);
                pair.compare(probes, context);
            }
        }
        pair.run(Integer.MAX_VALUE, "seed " + seed + " final");
        pair.compare(probes, "seed " + seed + " final");
        failureRuns += pair.failures;
    }

    static int failureRuns;

    @Test void seededTicketChurnMatchesAcrossEdgePositions() {
        long[] anchors = {ChunkPos.asLong(0, 0), ChunkPos.asLong(Integer.MAX_VALUE, Integer.MIN_VALUE), ChunkPos.asLong(1875066, 1875060), ChunkPos.asLong(-300, 77)};
        for (int index = 0; index < anchors.length; index++) {
            churn(23L * index + 1, anchors[index], 250, 8, false);
            churn(23L * index + 2, anchors[index], 250, 30, true);
        }
        // The Integer.MAX_VALUE anchor exercises mid-run holder creation failures;
        // the state after each failure is compared like any other.
        assertTrue(failureRuns > 0, "out-of-bounds holder failures must be exercised");
        System.out.println("LOADING_DISTANCE_FAILURE_RUNS " + failureRuns);
    }

    @Test void preexistingTicketsAreReadWhenNeighboursRecompute() {
        // A ticket added before the tracker attaches is never notified. The original
        // reads it lazily once a neighbour's removal recomputes that chunk.
        long quiet = ChunkPos.asLong(0, 0), neighbour = ChunkPos.asLong(1, 0);
        for (int quietLevel : new int[]{33, 40, 44, 46}) {
            Pair pair = new Pair();
            pair.add(quiet, TicketType.FORCED, quietLevel);
            pair.attach();
            long[] probes = probes(quiet, 16);
            pair.add(neighbour, TicketType.PLAYER_LOADING, 31);
            pair.run(Integer.MAX_VALUE, "quiet " + quietLevel + " lowered");
            pair.compare(probes, "quiet " + quietLevel + " lowered");
            pair.remove(neighbour, TicketType.PLAYER_LOADING, 31);
            pair.run(Integer.MAX_VALUE, "quiet " + quietLevel + " recomputed");
            pair.compare(probes, "quiet " + quietLevel + " recomputed");
        }
    }

    @Test void unreachableInstancesReleaseNativeState() throws Exception {
        for (int round = 0; round < 1000; round++) {
            TicketStorage storage = new TicketStorage();
            Native tracker = new Native(new Manager(storage), storage);
            storage.addTicket(ChunkPos.asLong(round, -round), new Ticket(TicketType.FORCED, 31));
            tracker.runDistanceUpdates(Integer.MAX_VALUE);
        }
        System.gc();
        Thread.sleep(50);
        TicketStorage storage = new TicketStorage();
        Native survivor = new Native(new Manager(storage), storage);
        storage.addTicket(0, new Ticket(TicketType.FORCED, 31));
        survivor.runDistanceUpdates(Integer.MAX_VALUE);
        assertEquals(31, survivor.level(0));
    }
}
