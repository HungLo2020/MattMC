package net.minecraft.server.level;

import org.junit.jupiter.api.Tag;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact parity of the native-owned simulation tracker with the pinned original,
 * each attached to its own real TicketStorage driven by identical operations. */
@Tag("parity")
class NativeSimulationChunkTrackerTest {
    // Ticket types are registry entries; initialize them only after bootstrap.
    static TicketType[] TYPES;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Load-only, simulation-only and both, with and without timeouts/persistence.
        TYPES = new TicketType[]{
            TicketType.PLAYER_LOADING, TicketType.PLAYER_SIMULATION, TicketType.DRAGON, TicketType.FORCED,
            TicketType.PORTAL, TicketType.ENDER_PEARL, TicketType.UNKNOWN
        };
    }

    record Change(long position, int level) {}

    static final class Original extends JavaSimulationChunkTracker {
        final List<Change> changes = new ArrayList<>();
        Original(TicketStorage storage) { super(storage); }
        @Override protected void setLevel(long l, int i) { this.changes.add(new Change(l, i)); super.setLevel(l, i); }
        int level(long l) { return this.getLevel(l); }
        Long2ByteMap view() { return this.chunks; }
    }

    static final class Native extends SimulationChunkTracker {
        final List<Change> changes = new ArrayList<>();
        Native(TicketStorage storage) { super(storage); }
        @Override protected void setLevel(long l, int i) { this.changes.add(new Change(l, i)); super.setLevel(l, i); }
        int level(long l) { return this.getLevel(l); }
        Long2ByteMap view() { return this.chunks; }
    }

    /** Both storages receive the same operation; each gets its own Ticket instances. */
    static final class Pair {
        final TicketStorage originalStorage = new TicketStorage();
        final TicketStorage nativeStorage = new TicketStorage();
        Original original;
        Native candidate;

        void attach() {
            this.original = new Original(this.originalStorage);
            this.candidate = new Native(this.nativeStorage);
        }

        void add(long position, TicketType type, int level) {
            assertEquals(this.originalStorage.addTicket(position, new Ticket(type, level)), this.nativeStorage.addTicket(position, new Ticket(type, level)));
        }

        void remove(long position, TicketType type, int level) {
            assertEquals(this.originalStorage.removeTicket(position, new Ticket(type, level)), this.nativeStorage.removeTicket(position, new Ticket(type, level)));
        }

        void run() {
            this.original.runAllUpdates();
            this.candidate.runAllUpdates();
        }

        void compare(long[] probes, String context) {
            assertEquals(this.original.changes, this.candidate.changes, () -> context + " setLevel transcript");
            assertEquals(entries(this.original.view()), entries(this.candidate.view()), () -> context + " published iteration order");
            for (long probe : probes) {
                assertEquals(this.original.level(probe), this.candidate.level(probe), () -> context + " level " + probe);
                assertEquals(this.original.getLevel(new ChunkPos(probe)), this.candidate.getLevel(new ChunkPos(probe)), () -> context + " chunk level " + probe);
            }
        }
    }

    static List<String> entries(Long2ByteMap map) {
        List<String> entries = new ArrayList<>();
        for (Long2ByteMap.Entry entry : map.long2ByteEntrySet()) entries.add(entry.getLongKey() + "=" + entry.getByteValue());
        return entries;
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
        // Every length-four history over two chunks and add/remove of three ticket kinds.
        long[] chunks = {ChunkPos.asLong(0, 0), ChunkPos.asLong(2, 1)};
        int[][] kinds = {{1, 31}, {2, 22}, {0, 20}}; // simulation-only, both, load-only
        long[] probes = probes(chunks[0], 14);
        int actions = 2 * 3 * 2 + 1, histories = 0;
        for (int path = 0, total = (int)Math.pow(actions, 4); path < total; path++) {
            Pair pair = new Pair();
            pair.attach();
            int remaining = path;
            for (int step = 0; step < 4; step++) {
                int action = remaining % actions;
                remaining /= actions;
                if (action == 12) {
                    pair.run();
                } else {
                    long chunk = chunks[action / 6];
                    int[] kind = kinds[(action / 2) % 3];
                    if (action % 2 == 0) pair.add(chunk, TYPES[kind[0]], kind[1]);
                    else pair.remove(chunk, TYPES[kind[0]], kind[1]);
                }
                pair.compare(probes, "history " + path + " step " + step);
            }
            pair.run();
            pair.compare(probes, "history " + path + " settled");
            histories++;
        }
        System.out.println("SIMULATION_DISTANCE_EXHAUSTIVE histories=" + histories);
    }

    static void churn(long seed, long anchor, int steps, int spread, boolean preexisting) {
        Random random = new Random(seed);
        Pair pair = new Pair();
        List<long[]> live = new ArrayList<>();
        if (preexisting) {
            // Tickets present before the tracker attaches must be read as the original reads them.
            for (int index = 0; index < 6; index++) {
                long chunk = offset(anchor, random.nextInt(spread) - spread / 2, random.nextInt(spread) - spread / 2);
                int type = random.nextInt(TYPES.length), level = random.nextInt(40) - 4;
                pair.add(chunk, TYPES[type], level);
                live.add(new long[]{chunk, type, level});
            }
        }
        pair.attach();
        long[] probes = probes(anchor, spread / 2 + 14);
        for (int step = 0; step < steps; step++) {
            int action = random.nextInt(20);
            if (action < 9 || live.isEmpty()) {
                long chunk = offset(anchor, random.nextInt(spread) - spread / 2, random.nextInt(spread) - spread / 2);
                int type = random.nextInt(TYPES.length), level = random.nextInt(40) - 4;
                pair.add(chunk, TYPES[type], level);
                live.add(new long[]{chunk, type, level});
            } else if (action < 16) {
                long[] ticket = live.remove(random.nextInt(live.size()));
                pair.remove(ticket[0], TYPES[(int)ticket[1]], (int)ticket[2]);
            } else if (action == 16) {
                // DistanceManager.updateSimulationDistance path.
                int level = random.nextInt(34);
                pair.originalStorage.replaceTicketLevelOfType(level, TicketType.PLAYER_SIMULATION);
                pair.nativeStorage.replaceTicketLevelOfType(level, TicketType.PLAYER_SIMULATION);
                for (long[] ticket : live) if (ticket[1] == 1) ticket[2] = level;
            } else if (action == 17) {
                long chunk = offset(anchor, random.nextInt(spread) - spread / 2, random.nextInt(spread) - spread / 2);
                boolean forced = random.nextBoolean();
                assertEquals(pair.originalStorage.updateChunkForced(new ChunkPos(chunk), forced), pair.nativeStorage.updateChunkForced(new ChunkPos(chunk), forced));
            } else if (action == 18) {
                // Closing and reopening: bulk removeTicketIf, then re-adds through addTicket.
                pair.originalStorage.deactivateTicketsOnClosing();
                pair.nativeStorage.deactivateTicketsOnClosing();
                pair.run();
                pair.compare(probes, "seed " + seed + " closed " + step);
                pair.originalStorage.activateAllDeactivatedTickets();
                pair.nativeStorage.activateAllDeactivatedTickets();
            } else {
                pair.run();
                pair.compare(probes, "seed " + seed + " step " + step);
            }
        }
        pair.run();
        pair.compare(probes, "seed " + seed + " final");
    }

    @Test void seededTicketChurnMatchesAcrossEdgePositions() {
        long[] anchors = {ChunkPos.asLong(0, 0), ChunkPos.asLong(Integer.MAX_VALUE, Integer.MIN_VALUE), ChunkPos.asLong(1875066, 1875060), ChunkPos.asLong(-300, 77)};
        for (int index = 0; index < anchors.length; index++) {
            churn(17L * index + 1, anchors[index], 300, 8, false);
            churn(17L * index + 2, anchors[index], 300, 30, true);
        }
    }

    @Test void preexistingTicketsAreReadWhenNeighboursRecompute() {
        // A ticket added before the tracker attaches is never notified. The original
        // reads it lazily once a neighbour's removal recomputes that chunk.
        long quiet = ChunkPos.asLong(0, 0), neighbour = ChunkPos.asLong(1, 0);
        for (int quietLevel : new int[]{25, 31, 32, 40}) {
            Pair pair = new Pair();
            pair.add(quiet, TicketType.FORCED, quietLevel);
            pair.attach();
            long[] probes = probes(quiet, 14);
            pair.add(neighbour, TicketType.PLAYER_SIMULATION, 20);
            pair.run();
            pair.compare(probes, "quiet " + quietLevel + " lowered");
            pair.remove(neighbour, TicketType.PLAYER_SIMULATION, 20);
            pair.run();
            pair.compare(probes, "quiet " + quietLevel + " recomputed");
        }
    }

    @Test void unreachableInstancesReleaseNativeState() throws Exception {
        for (int round = 0; round < 2000; round++) {
            TicketStorage storage = new TicketStorage();
            Native tracker = new Native(storage);
            storage.addTicket(ChunkPos.asLong(round, -round), new Ticket(TicketType.PLAYER_SIMULATION, 21));
            tracker.runAllUpdates();
        }
        System.gc();
        Thread.sleep(50);
        TicketStorage storage = new TicketStorage();
        Native survivor = new Native(storage);
        storage.addTicket(0, new Ticket(TicketType.FORCED, 31));
        survivor.runAllUpdates();
        assertEquals(31, survivor.level(0));
    }
}
