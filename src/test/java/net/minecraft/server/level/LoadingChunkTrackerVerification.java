package net.minecraft.server.level;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

/** Production-path timing of the loading tracker. One JVM runs one backend
 * ({@code java}: pinned original, {@code native}: production tracker), each
 * with a real TicketStorage and ChunkMap-equivalent holder scheduling: player
 * loading tickets shifting as players walk, short-lived portal/pearl tickets,
 * per-tick runDistanceUpdates, unload processing and futures clearing.
 * Construction is untimed. */
public final class LoadingChunkTrackerVerification {
    /** Ticket operation: kind 0 add, 1 remove. */
    record Op(int kind, long position, int type, int level) {}
    record Tick(Op[] ops) {}

    interface Backend {
        TicketStorage storage();
        int run();
        NativeLoadingChunkTrackerTest.Manager manager();
    }

    static final class OriginalBackend implements Backend {
        final TicketStorage storage = new TicketStorage();
        final NativeLoadingChunkTrackerTest.Manager manager = new NativeLoadingChunkTrackerTest.Manager(this.storage);
        final JavaLoadingChunkTracker tracker = new JavaLoadingChunkTracker(this.manager, this.storage);
        @Override public TicketStorage storage() { return this.storage; }
        @Override public int run() { return this.tracker.runDistanceUpdates(Integer.MAX_VALUE); }
        @Override public NativeLoadingChunkTrackerTest.Manager manager() { return this.manager; }
    }

    static final class NativeBackend implements Backend {
        final TicketStorage storage = new TicketStorage();
        final NativeLoadingChunkTrackerTest.Manager manager = new NativeLoadingChunkTrackerTest.Manager(this.storage);
        final LoadingChunkTracker tracker = new LoadingChunkTracker(this.manager, this.storage);
        @Override public TicketStorage storage() { return this.storage; }
        @Override public int run() { return this.tracker.runDistanceUpdates(Integer.MAX_VALUE); }
        @Override public NativeLoadingChunkTrackerTest.Manager manager() { return this.manager; }
    }

    // Registry entries: initialized by main after bootstrap.
    static TicketType[] TYPES;
    static final int VIEW = 8;

    static long offset(long position, int dx, int dz) {
        return ChunkPos.asLong((int)position + dx, (int)(position >> 32) + dz);
    }

    static boolean inView(long centre, long chunk) {
        return Math.abs((int)chunk - (int)centre) <= VIEW && Math.abs((int)(chunk >> 32) - (int)(centre >> 32)) <= VIEW;
    }

    static List<Tick> workload(String name) {
        int players, ticks, pearlsPerTick;
        switch (name) {
            case "loading_walk" -> { players = 1; ticks = 400; pearlsPerTick = 0; }
            case "loading_group" -> { players = 4; ticks = 200; pearlsPerTick = 0; }
            case "loading_churn" -> { players = 2; ticks = 300; pearlsPerTick = 3; }
            default -> throw new IllegalArgumentException(name);
        }
        Random random = new Random(name.hashCode());
        long[] where = new long[players];
        List<Tick> workload = new ArrayList<>();
        List<Op> join = new ArrayList<>();
        for (int player = 0; player < players; player++) {
            where[player] = ChunkPos.asLong(random.nextInt(40) - 20, random.nextInt(40) - 20);
            for (int dx = -VIEW; dx <= VIEW; dx++) for (int dz = -VIEW; dz <= VIEW; dz++) join.add(new Op(0, offset(where[player], dx, dz), 0, 31));
        }
        workload.add(new Tick(join.toArray(Op[]::new)));
        List<long[]> expiring = new ArrayList<>();
        for (int tick = 1; tick < ticks; tick++) {
            List<Op> ops = new ArrayList<>();
            for (int player = 0; player < players; player++) {
                if (random.nextInt(4) != 0) continue;
                long next = offset(where[player], random.nextInt(3) - 1, random.nextInt(3) - 1);
                // PlayerTicketTracker: loading tickets enter/leave the view square.
                for (int dx = -VIEW - 1; dx <= VIEW + 1; dx++) for (int dz = -VIEW - 1; dz <= VIEW + 1; dz++) {
                    long chunk = offset(where[player], dx, dz);
                    boolean before = inView(where[player], chunk), after = inView(next, chunk);
                    if (before && !after) ops.add(new Op(1, chunk, 0, 31));
                    long entering = offset(next, dx, dz);
                    if (inView(next, entering) && !inView(where[player], entering)) ops.add(new Op(0, entering, 0, 31));
                }
                where[player] = next;
            }
            for (int pearl = 0; pearl < pearlsPerTick; pearl++) {
                long chunk = offset(where[random.nextInt(players)], random.nextInt(61) - 30, random.nextInt(61) - 30);
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
            workload.add(new Tick(ops.toArray(Op[]::new)));
        }
        return workload;
    }

    static long round(Backend backend, List<Tick> workload) {
        TicketStorage storage = backend.storage();
        NativeLoadingChunkTrackerTest.Manager manager = backend.manager();
        long checksum = 0;
        for (Tick tick : workload) {
            for (Op op : tick.ops()) {
                Ticket ticket = new Ticket(TYPES[op.type()], op.level());
                if (op.kind() == 0) storage.addTicket(op.position(), ticket); else storage.removeTicket(op.position(), ticket);
            }
            // DistanceManager.runAllUpdates: processed count, then futures handled and cleared.
            checksum = checksum * 31 + (Integer.MAX_VALUE - backend.run());
            checksum = checksum * 31 + manager.chunksToUpdateFutures.size() + manager.holders.size();
            manager.chunksToUpdateFutures.clear();
            manager.processUnloads();
        }
        return checksum;
    }

    static String trace(List<Tick> workload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Tick tick : workload) digest.update(Arrays.toString(tick.ops()).getBytes());
        return HexFormat.of().formatHex(digest.digest());
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        TYPES = new TicketType[]{TicketType.PLAYER_LOADING, TicketType.ENDER_PEARL, TicketType.PORTAL};
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
