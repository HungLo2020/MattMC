package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
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
import net.minecraft.world.level.TicketStorage;

/** Production-path timing of the simulation tracker. One JVM runs one backend
 * ({@code java}: pinned original, {@code native}: production adapter), each fed
 * by a real TicketStorage: DistanceManager's player simulation tickets, short
 * lived ender-pearl/portal tickets, simulation distance changes, per-tick
 * runAllUpdates, entity-ticking range queries and entity-ticking chunk
 * iteration. Construction is untimed. */
public final class SimulationChunkTrackerVerification {
    /** Ticket operation: kind 0 add, 1 remove, 2 replace player simulation level. */
    record Op(int kind, long position, int type, int level) {}
    record Tick(Op[] ops, long[] queries) {}

    interface Backend {
        TicketStorage storage();
        void runAll();
        int level(long position);
        Long2ByteMap view();
    }

    static final class OriginalBackend implements Backend {
        final TicketStorage storage = new TicketStorage();
        final JavaSimulationChunkTracker tracker = new JavaSimulationChunkTracker(this.storage);
        @Override public TicketStorage storage() { return this.storage; }
        @Override public void runAll() { this.tracker.runAllUpdates(); }
        @Override public int level(long position) { return this.tracker.getLevel(position); }
        @Override public Long2ByteMap view() { return this.tracker.chunks; }
    }

    static final class NativeBackend implements Backend {
        final TicketStorage storage = new TicketStorage();
        final SimulationChunkTracker tracker = new SimulationChunkTracker(this.storage);
        @Override public TicketStorage storage() { return this.storage; }
        @Override public void runAll() { this.tracker.runAllUpdates(); }
        @Override public int level(long position) { return this.tracker.getLevel(position); }
        @Override public Long2ByteMap view() { return this.tracker.chunks; }
    }

    // Registry entries: initialized by main after bootstrap.
    static TicketType[] TYPES;

    static long offset(long position, int dx, int dz) {
        return ChunkPos.asLong((int)position + dx, (int)(position >> 32) + dz);
    }

    static List<Tick> workload(String name) {
        int players, ticks, pearlsPerTick, distanceEvery;
        switch (name) {
            case "player_walk" -> { players = 4; ticks = 600; pearlsPerTick = 0; distanceEvery = 0; }
            case "ticket_churn" -> { players = 4; ticks = 600; pearlsPerTick = 2; distanceEvery = 0; }
            case "distance_changes" -> { players = 4; ticks = 300; pearlsPerTick = 0; distanceEvery = 30; }
            default -> throw new IllegalArgumentException(name);
        }
        Random random = new Random(name.hashCode());
        int simulationLevel = 31 - 10;
        long[] where = new long[players];
        List<Tick> workload = new ArrayList<>();
        List<Op> join = new ArrayList<>();
        for (int player = 0; player < players; player++) {
            where[player] = ChunkPos.asLong(random.nextInt(60) - 30, random.nextInt(60) - 30);
            join.add(new Op(0, where[player], 0, simulationLevel));
        }
        workload.add(new Tick(join.toArray(Op[]::new), new long[0]));
        List<long[]> expiring = new ArrayList<>();
        for (int tick = 1; tick < ticks; tick++) {
            List<Op> ops = new ArrayList<>();
            for (int player = 0; player < players; player++) {
                if (random.nextInt(4) != 0) continue;
                long next = offset(where[player], random.nextInt(3) - 1, random.nextInt(3) - 1);
                // DistanceManager: remove the old chunk's ticket once it is empty, add at the new one.
                boolean shared = false;
                for (int other = 0; other < players; other++) shared |= other != player && where[other] == where[player];
                if (!shared) ops.add(new Op(1, where[player], 0, simulationLevel));
                ops.add(new Op(0, next, 0, simulationLevel));
                where[player] = next;
            }
            for (int pearl = 0; pearl < pearlsPerTick; pearl++) {
                long chunk = offset(where[random.nextInt(players)], random.nextInt(41) - 20, random.nextInt(41) - 20);
                int type = 1 + random.nextInt(2), level = 31 - random.nextInt(3);
                ops.add(new Op(0, chunk, type, level));
                expiring.add(new long[]{chunk, type, level, tick + 20 + random.nextInt(40)});
            }
            for (int index = expiring.size() - 1; index >= 0; index--) {
                long[] ticket = expiring.get(index);
                if (ticket[3] == tick) {
                    ops.add(new Op(1, ticket[0], (int)ticket[1], (int)ticket[2]));
                    expiring.remove(index);
                }
            }
            if (distanceEvery > 0 && tick % distanceEvery == 0) {
                simulationLevel = 31 - (4 + random.nextInt(9));
                ops.add(new Op(2, 0, 0, simulationLevel));
            }
            long[] queries = new long[64];
            for (int query = 0; query < queries.length; query++) {
                queries[query] = offset(where[random.nextInt(players)], random.nextInt(25) - 12, random.nextInt(25) - 12);
            }
            workload.add(new Tick(ops.toArray(Op[]::new), queries));
        }
        return workload;
    }

    static long round(Backend backend, List<Tick> workload) {
        TicketStorage storage = backend.storage();
        long checksum = 0;
        for (Tick tick : workload) {
            for (Op op : tick.ops()) {
                switch (op.kind()) {
                    case 0 -> storage.addTicket(op.position(), new Ticket(TYPES[op.type()], op.level()));
                    case 1 -> storage.removeTicket(op.position(), new Ticket(TYPES[op.type()], op.level()));
                    default -> storage.replaceTicketLevelOfType(op.level(), TicketType.PLAYER_SIMULATION);
                }
            }
            backend.runAll();
            for (long query : tick.queries()) checksum = checksum * 31 + (ChunkLevel.isEntityTicking(backend.level(query)) ? 1 : 2);
            // DistanceManager.forEachEntityTickingChunk
            for (Long2ByteMap.Entry entry : Long2ByteMaps.fastIterable(backend.view())) {
                if (ChunkLevel.isEntityTicking(entry.getByteValue())) checksum += entry.getLongKey();
            }
        }
        return checksum;
    }

    static String trace(List<Tick> workload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Tick tick : workload) {
            digest.update(Arrays.toString(tick.ops()).getBytes());
            digest.update(Arrays.toString(tick.queries()).getBytes());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        TYPES = new TicketType[]{TicketType.PLAYER_SIMULATION, TicketType.ENDER_PEARL, TicketType.PORTAL};
        String mode = args[0], name = args[1];
        boolean quick = args.length > 2 && args[2].equals("quick");
        List<Tick> workload = workload(name);
        java.util.function.Supplier<Backend> factory = switch (mode) {
            case "java" -> OriginalBackend::new;
            case "native" -> NativeBackend::new;
            default -> throw new IllegalArgumentException(mode);
        };
        long operations = workload.stream().mapToLong(tick -> tick.ops().length).sum();
        System.out.println("PLAYER_DISTANCE_FIXTURE case=" + name + " ticks=" + workload.size() + " moves=" + operations + " trace=" + trace(workload));
        PlayerChunkDistancesVerification.measure(mode, name, quick, factory, backend -> round(backend, workload));
    }
}
