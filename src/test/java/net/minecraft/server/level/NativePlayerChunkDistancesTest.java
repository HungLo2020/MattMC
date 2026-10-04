package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact parity of the native-owned player distance fields with the pinned
 * original Java trackers, driven through DistanceManager's add/remove protocol. */
class NativePlayerChunkDistancesTest {
    static final int SPAWN = 8;
    static final int TICKETS = 32;

    // ChunkPos constants initialize chunk statuses through the registries.
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    record Change(long position, int previous, int level) {}

    /** Shared driver: identical player bookkeeping for both backends. */
    abstract static class Model {
        final Long2ObjectMap<ObjectSet<Object>> playersPerChunk = new Long2ObjectOpenHashMap<>();
        final List<List<Change>> changes = List.of(new ArrayList<>(), new ArrayList<>());

        void add(long position, Object player) {
            this.playersPerChunk.computeIfAbsent(position, ignored -> new ObjectOpenHashSet<>()).add(player);
            this.entered(position);
        }

        void remove(long position, Object player) {
            ObjectSet<Object> players = this.playersPerChunk.get(position);
            players.remove(player);
            if (players.isEmpty()) {
                this.playersPerChunk.remove(position);
                this.vacated(position);
            }
        }

        abstract void entered(long position);
        abstract void vacated(long position);
        abstract void run(int field);
        abstract Long2ByteMap chunks(int field);
        abstract int level(int field, long position);
    }

    static final class Original extends Model {
        final JavaFixedPlayerDistanceChunkTracker[] fields = new JavaFixedPlayerDistanceChunkTracker[2];

        Original(int spawn, int tickets) {
            int[] distances = {spawn, tickets};
            for (int field = 0; field < 2; field++) {
                List<Change> log = this.changes.get(field);
                this.fields[field] = new JavaFixedPlayerDistanceChunkTracker(distances[field], this.playersPerChunk) {
                    @Override
                    protected void onLevelChange(long l, int i, int j) {
                        log.add(new Change(l, i, j));
                    }
                };
            }
        }

        // DistanceManager.addPlayer/removePlayer order: spawn counter, then tickets.
        @Override void entered(long position) { for (var field : this.fields) field.update(position, 0, true); }
        @Override void vacated(long position) { for (var field : this.fields) field.update(position, Integer.MAX_VALUE, false); }
        @Override void run(int field) { this.fields[field].runAllUpdates(); }
        @Override Long2ByteMap chunks(int field) { return this.fields[field].chunks; }
        @Override int level(int field, long position) { return this.fields[field].getLevel(position); }
    }

    static final class Native extends Model {
        final PlayerChunkDistances distances;
        final DistanceManager.FixedPlayerDistanceChunkTracker[] fields = new DistanceManager.FixedPlayerDistanceChunkTracker[2];

        Native(int spawn, int tickets) {
            this.distances = new PlayerChunkDistances(spawn, tickets);
            for (int field = 0; field < 2; field++) {
                List<Change> log = this.changes.get(field);
                this.fields[field] = new DistanceManager.FixedPlayerDistanceChunkTracker(this.distances, field) {
                    @Override
                    protected void onLevelChange(long l, int i, int j) {
                        log.add(new Change(l, i, j));
                    }
                };
            }
        }

        @Override void entered(long position) { this.distances.playerEntered(position); }
        @Override void vacated(long position) { this.distances.chunkVacated(position); }
        @Override void run(int field) { this.fields[field].runAllUpdates(); }
        @Override Long2ByteMap chunks(int field) { return this.fields[field].chunks; }
        @Override int level(int field, long position) { return this.fields[field].getLevel(position); }
    }

    static List<String> entries(Long2ByteMap map) {
        List<String> entries = new ArrayList<>();
        for (Long2ByteMap.Entry entry : map.long2ByteEntrySet()) entries.add(entry.getLongKey() + "=" + entry.getByteValue());
        return entries;
    }

    static void compare(Original original, Native candidate, long[] probes, String context) {
        for (int field = 0; field < 2; field++) {
            int f = field;
            assertEquals(original.changes.get(field), candidate.changes.get(field), () -> context + " field " + f + " setLevel transcript");
            assertEquals(entries(original.chunks(field)), entries(candidate.chunks(field)), () -> context + " field " + f + " published iteration order");
            for (long probe : probes) {
                assertEquals(original.level(field, probe), candidate.level(field, probe), () -> context + " field " + f + " level " + probe);
            }
        }
    }

    static long offset(long position, int dx, int dz) {
        return ChunkPos.asLong((int)position + dx, (int)(position >> 32) + dz);
    }

    static long[] probes(long... anchors) {
        List<Long> probes = new ArrayList<>();
        for (long anchor : anchors) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) probes.add(offset(anchor, dx, dz));
        probes.add(ChunkPos.INVALID_CHUNK_POS);
        return probes.stream().mapToLong(Long::longValue).toArray();
    }

    @Test void constructorsMatchOriginalValidation() {
        for (int distance : new int[]{Integer.MIN_VALUE, -3, -2, -1, 0, 8, 32, 251, 252, 253, Integer.MAX_VALUE}) {
            Object expected, actual;
            try { new JavaFixedPlayerDistanceChunkTracker(distance, new Long2ObjectOpenHashMap<>()); expected = "ok"; }
            catch (RuntimeException error) { expected = error.getClass() + ":" + error.getMessage(); }
            try { new PlayerChunkDistances(distance, distance); actual = "ok"; }
            catch (RuntimeException error) { actual = error.getClass() + ":" + error.getMessage(); }
            assertEquals(expected, actual, "distance " + distance);
        }
    }

    @Test void exhaustiveShortHistories() {
        // Two players over three nearby chunks: every length-four action history
        // (enter/leave either player at any chunk, or run either field).
        long[] chunks = {ChunkPos.asLong(0, 0), ChunkPos.asLong(1, 1), ChunkPos.asLong(3, -1)};
        long[] probes = probes(chunks);
        int actions = 14, histories = 0;
        for (int path = 0, total = (int)Math.pow(actions, 4); path < total; path++) {
            Original original = new Original(2, 4);
            Native candidate = new Native(2, 4);
            int[] where = {-1, -1};
            int remaining = path;
            for (int step = 0; step < 4; step++) {
                int action = remaining % actions;
                remaining /= actions;
                if (action < 12) {
                    int player = action / 6, chunk = action % 3;
                    boolean enter = action % 6 < 3;
                    for (Model model : new Model[]{original, candidate}) {
                        if (enter && where[player] < 0) model.add(chunks[chunk], player);
                        else if (!enter && where[player] == chunk) model.remove(chunks[chunk], player);
                    }
                    if (enter && where[player] < 0) where[player] = chunk;
                    else if (!enter && where[player] == chunk) where[player] = -1;
                } else {
                    original.run(action - 12);
                    candidate.run(action - 12);
                }
                compare(original, candidate, probes, "history " + path + " step " + step);
            }
            for (int field = 0; field < 2; field++) { original.run(field); candidate.run(field); }
            compare(original, candidate, probes, "history " + path + " settled");
            histories++;
        }
        System.out.println("PLAYER_DISTANCE_EXHAUSTIVE histories=" + histories);
    }

    static void walk(long seed, long anchor, int players, int steps, int spread, int teleportPercent) {
        Random random = new Random(seed);
        Original original = new Original(SPAWN, TICKETS);
        Native candidate = new Native(SPAWN, TICKETS);
        long[] where = new long[players];
        for (int player = 0; player < players; player++) {
            where[player] = offset(anchor, random.nextInt(spread) - spread / 2, random.nextInt(spread) - spread / 2);
            original.add(where[player], player);
            candidate.add(where[player], player);
        }
        for (int step = 0; step < steps; step++) {
            int player = random.nextInt(players);
            long next = random.nextInt(100) < teleportPercent
                ? offset(anchor, random.nextInt(spread * 4) - spread * 2, random.nextInt(spread * 4) - spread * 2)
                : offset(where[player], random.nextInt(3) - 1, random.nextInt(3) - 1);
            // ChunkMap.move removes the old section before adding the new one,
            // including vertical section changes within the same chunk.
            original.remove(where[player], player);
            candidate.remove(where[player], player);
            original.add(next, player);
            candidate.add(next, player);
            where[player] = next;
            int run = random.nextInt(4);
            if (run < 2) { original.run(run); candidate.run(run); }
            if (run == 3 || step == steps - 1) {
                for (int field = 0; field < 2; field++) { original.run(field); candidate.run(field); }
                compare(original, candidate, probes(where), "seed " + seed + " step " + step);
            }
        }
    }

    @Test void seededWalksMatchAcrossEdgePositions() {
        long[] anchors = {
            ChunkPos.asLong(0, 0), ChunkPos.asLong(-40, 77), ChunkPos.asLong(Integer.MAX_VALUE, Integer.MIN_VALUE),
            ChunkPos.asLong(1875066, 1875060), ChunkPos.INVALID_CHUNK_POS
        };
        for (int index = 0; index < anchors.length; index++) {
            walk(31L * index + 1, anchors[index], 1, 300, 6, 0);
            walk(31L * index + 2, anchors[index], 6, 400, 10, 5);
            walk(31L * index + 3, anchors[index], 12, 300, 60, 40);
        }
    }

    @Test void sharedChunksOnlyVacateWhenEmpty() {
        Original original = new Original(SPAWN, TICKETS);
        Native candidate = new Native(SPAWN, TICKETS);
        long chunk = ChunkPos.asLong(5, 5);
        for (Model model : new Model[]{original, candidate}) {
            model.add(chunk, "a");
            model.add(chunk, "b");
            model.add(chunk, "a");
            for (int field = 0; field < 2; field++) model.run(field);
            model.remove(chunk, "a");
            for (int field = 0; field < 2; field++) model.run(field);
        }
        compare(original, candidate, probes(chunk), "shared");
        assertEquals(0, original.level(0, chunk));
        for (Model model : new Model[]{original, candidate}) {
            model.remove(chunk, "b");
            for (int field = 0; field < 2; field++) model.run(field);
        }
        compare(original, candidate, probes(chunk), "vacated");
        assertTrue(candidate.chunks(0).isEmpty() && candidate.chunks(1).isEmpty());
    }

    @Test void unreachableInstancesReleaseNativeState() throws Exception {
        for (int round = 0; round < 2000; round++) {
            Native candidate = new Native(SPAWN, TICKETS);
            candidate.add(ChunkPos.asLong(round, -round), round);
            candidate.run(1);
        }
        System.gc();
        Thread.sleep(50);
        Native survivor = new Native(SPAWN, TICKETS);
        survivor.add(0, "after");
        survivor.run(0);
        assertEquals(0, survivor.level(0, 0));
    }
}
