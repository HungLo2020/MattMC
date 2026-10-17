package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import net.minecraft.world.level.ChunkPos;

/** Production-path timing of the player distance fields. One JVM runs one
 * backend: {@code java} drives the pinned original trackers, {@code native}
 * the production DistanceManager trackers over PlayerChunkDistances. Both use
 * DistanceManager's player bookkeeping, ChunkMap's remove-then-add move order,
 * runAllUpdates per tick (spawn counter then tickets), the ticket tracker's
 * toUpdate collection and spawn-range level queries. Construction is untimed. */
public final class PlayerChunkDistancesVerification {
    static final int SPAWN = 8;
    static final int TICKETS = 32;

    /** One tick: ordered moves (player, from, to), then queries around players. */
    record Tick(int[] players, long[] from, long[] to, long[] queries) {}

    interface Backend {
        void add(long position, Object player);
        void remove(long position, Object player);
        void runAll();
        int spawnLevel(long position);
        long drainTicketUpdates();
    }

    abstract static class Players implements Backend {
        final Long2ObjectMap<ObjectSet<Object>> playersPerChunk = new Long2ObjectOpenHashMap<>();
        final LongSet toUpdate = new LongOpenHashSet();

        @Override public void add(long position, Object player) {
            this.playersPerChunk.computeIfAbsent(position, ignored -> new ObjectOpenHashSet<>()).add(player);
            this.entered(position);
        }

        @Override public void remove(long position, Object player) {
            ObjectSet<Object> players = this.playersPerChunk.get(position);
            players.remove(player);
            if (players.isEmpty()) {
                this.playersPerChunk.remove(position);
                this.vacated(position);
            }
        }

        // PlayerTicketTracker.runAllUpdates consumes and clears toUpdate.
        @Override public long drainTicketUpdates() {
            long checksum = 0;
            for (LongIterator iterator = this.toUpdate.iterator(); iterator.hasNext(); ) checksum = checksum * 31 + iterator.nextLong();
            this.toUpdate.clear();
            return checksum;
        }

        abstract void entered(long position);
        abstract void vacated(long position);
    }

    static final class OriginalBackend extends Players {
        final JavaFixedPlayerDistanceChunkTracker spawn = new JavaFixedPlayerDistanceChunkTracker(SPAWN, this.playersPerChunk);
        final JavaFixedPlayerDistanceChunkTracker tickets = new JavaFixedPlayerDistanceChunkTracker(TICKETS, this.playersPerChunk) {
            @Override protected void onLevelChange(long l, int i, int j) { OriginalBackend.this.toUpdate.add(l); }
        };

        @Override void entered(long position) { this.spawn.update(position, 0, true); this.tickets.update(position, 0, true); }
        @Override void vacated(long position) {
            this.spawn.update(position, Integer.MAX_VALUE, false);
            this.tickets.update(position, Integer.MAX_VALUE, false);
        }
        @Override public void runAll() { this.spawn.runAllUpdates(); this.tickets.runAllUpdates(); }
        @Override public int spawnLevel(long position) { this.spawn.runAllUpdates(); return this.spawn.getLevel(position); }
    }

    static final class NativeBackend extends Players {
        final PlayerChunkDistances distances = new PlayerChunkDistances(SPAWN, TICKETS);
        final DistanceManager.FixedPlayerDistanceChunkTracker spawn =
            new DistanceManager.FixedPlayerDistanceChunkTracker(this.distances, PlayerChunkDistances.NATURAL_SPAWN);
        final DistanceManager.FixedPlayerDistanceChunkTracker tickets =
            new DistanceManager.FixedPlayerDistanceChunkTracker(this.distances, PlayerChunkDistances.PLAYER_TICKETS) {
                @Override protected void onLevelChange(long l, int i, int j) { NativeBackend.this.toUpdate.add(l); }
            };

        @Override void entered(long position) { this.distances.playerEntered(position); }
        @Override void vacated(long position) { this.distances.chunkVacated(position); }
        @Override public void runAll() { this.spawn.runAllUpdates(); this.tickets.runAllUpdates(); }
        @Override public int spawnLevel(long position) { this.spawn.runAllUpdates(); return this.spawn.getLevel(position); }
    }

    static long offset(long position, int dx, int dz) {
        return ChunkPos.asLong((int)position + dx, (int)(position >> 32) + dz);
    }

    /** Deterministic workload. Walkers cross a chunk about every fourth tick
     * (sprinting speed); some ticks are vertical section moves in place. */
    static List<Tick> workload(String name) {
        int players, ticks, teleportEvery;
        switch (name) {
            case "single_walk" -> { players = 1; ticks = 1200; teleportEvery = 0; }
            case "group_walk" -> { players = 8; ticks = 300; teleportEvery = 0; }
            case "teleport_churn" -> { players = 4; ticks = 300; teleportEvery = 20; }
            default -> throw new IllegalArgumentException(name);
        }
        Random random = new Random(name.hashCode());
        long[] where = new long[players];
        for (int player = 0; player < players; player++) where[player] = ChunkPos.asLong(random.nextInt(40) - 20, random.nextInt(40) - 20);
        List<Tick> workload = new ArrayList<>();
        // Tick 0 joins every player from nowhere.
        workload.add(new Tick(new int[0], new long[0], where.clone(), new long[0]));
        for (int tick = 1; tick < ticks; tick++) {
            List<Integer> moved = new ArrayList<>();
            List<Long> from = new ArrayList<>(), to = new ArrayList<>();
            for (int player = 0; player < players; player++) {
                long next = where[player];
                if (teleportEvery > 0 && (tick + player * 5) % teleportEvery == 0) {
                    next = ChunkPos.asLong(random.nextInt(4000) - 2000, random.nextInt(4000) - 2000);
                } else if (random.nextInt(4) == 0) {
                    next = offset(where[player], random.nextInt(3) - 1, random.nextInt(3) - 1);
                } else if (random.nextInt(16) != 0) {
                    continue; // otherwise a vertical section move within the chunk
                }
                moved.add(player); from.add(where[player]); to.add(next);
                where[player] = next;
            }
            long[] queries = new long[16];
            for (int query = 0; query < queries.length; query++) {
                queries[query] = offset(where[random.nextInt(players)], random.nextInt(21) - 10, random.nextInt(21) - 10);
            }
            workload.add(new Tick(moved.stream().mapToInt(Integer::intValue).toArray(),
                from.stream().mapToLong(Long::longValue).toArray(), to.stream().mapToLong(Long::longValue).toArray(), queries));
        }
        return workload;
    }

    static long round(Backend backend, List<Tick> workload) {
        long checksum = 0;
        Tick join = workload.get(0);
        for (int player = 0; player < join.to().length; player++) backend.add(join.to()[player], player);
        for (Tick tick : workload) {
            for (int move = 0; move < tick.players().length; move++) {
                backend.remove(tick.from()[move], tick.players()[move]);
                backend.add(tick.to()[move], tick.players()[move]);
            }
            backend.runAll();
            checksum = checksum * 1_000_003 + backend.drainTicketUpdates();
            for (long query : tick.queries()) checksum = checksum * 31 + backend.spawnLevel(query);
        }
        return checksum;
    }

    static String trace(List<Tick> workload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Tick tick : workload) {
            digest.update(Arrays.toString(tick.players()).getBytes());
            digest.update(Arrays.toString(tick.from()).getBytes());
            digest.update(Arrays.toString(tick.to()).getBytes());
            digest.update(Arrays.toString(tick.queries()).getBytes());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0], name = args[1];
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        boolean quick = args.length > 2 && args[2].equals("quick");
        List<Tick> workload = workload(name);
        java.util.function.Supplier<Backend> factory = switch (mode) {
            case "java" -> OriginalBackend::new;
            case "native" -> NativeBackend::new;
            default -> throw new IllegalArgumentException(mode);
        };
        long moves = workload.stream().mapToLong(tick -> tick.players().length).sum();
        System.out.println("PLAYER_DISTANCE_FIXTURE case=" + name + " ticks=" + workload.size() + " moves=" + moves + " trace=" + trace(workload));
        measure(mode, name, quick, factory, backend -> round(backend, workload));
    }

    /** Warmup and sampling shared by the distance benchmarks: {@code setup}
     * builds a fresh untimed state; only {@code body} on it is timed. */
    public static <T> void measure(String mode, String name, boolean quick, java.util.function.Supplier<T> setup,
                            java.util.function.ToLongFunction<T> body) {
        CompilationMXBean compiler = ManagementFactory.getCompilationMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long minimumWarmup = quick ? 1_000_000_000L : 15_000_000_000L;
        long warmStart = System.nanoTime();
        long checksum = 0;
        List<Long> recent = new ArrayList<>();
        // Warm until the minimum elapsed, recent medians are stable and JIT is idle.
        while (true) {
            T state = setup.get();
            long compiled = compiler.getTotalCompilationTime();
            long start = System.nanoTime();
            checksum = body.applyAsLong(state);
            recent.add(System.nanoTime() - start);
            boolean idle = compiler.getTotalCompilationTime() == compiled;
            if (recent.size() > 20) recent.remove(0);
            if (System.nanoTime() - warmStart >= minimumWarmup && recent.size() == 20 && idle) {
                long[] sorted = recent.stream().mapToLong(Long::longValue).sorted().toArray();
                long early = median(Arrays.copyOfRange(recent.stream().mapToLong(Long::longValue).toArray(), 0, 10));
                long late = median(Arrays.copyOfRange(recent.stream().mapToLong(Long::longValue).toArray(), 10, 20));
                if (Math.abs(early - late) <= sorted[10] / 50) break;
            }
            if (System.nanoTime() - warmStart > 4 * minimumWarmup) break;
        }
        long warmup = System.nanoTime() - warmStart;
        int repeats = quick ? 5 : 30;
        long[] samples = new long[repeats], cpu = new long[repeats], jit = new long[repeats];
        for (int repeat = 0; repeat < repeats; repeat++) {
            T state = setup.get();
            long compiled = compiler.getTotalCompilationTime();
            long cpuStart = threads.getCurrentThreadCpuTime();
            long start = System.nanoTime();
            long result = body.applyAsLong(state);
            samples[repeat] = System.nanoTime() - start;
            cpu[repeat] = threads.getCurrentThreadCpuTime() - cpuStart;
            jit[repeat] = compiler.getTotalCompilationTime() - compiled;
            if (result != checksum) throw new IllegalStateException("Nondeterministic output");
        }
        System.out.println("PLAYER_DISTANCE_BENCH case=" + name + " mode=" + mode + " warmup_ns=" + warmup + " repeats=" + repeats
            + " ns=" + Arrays.toString(samples) + " cpu_ns=" + Arrays.toString(cpu) + " jit=" + Arrays.toString(jit) + " checksum=" + checksum);
    }

    static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
