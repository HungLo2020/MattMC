package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.LevelChunk;
import org.objenesis.ObjenesisStd;

/** Identical subsystem workload on each classpath. No trace formatting, Mockito
 * dispatch or bytecode instrumentation is included in timed windows. Holder
 * work is a controlled boundary; this does not time chunk generation or IO. */
public final class BenchmarkPlayerLoading {
    private static final ObjenesisStd OBJECTS = new ObjenesisStd();
    private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(0, 16);
    private static final CompletableFuture<ChunkResult<LevelChunk>> READY =
        CompletableFuture.completedFuture(ChunkResult.error("benchmark ready result"));

    static final class Tasks implements Executor {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final ArrayList<Long> latencies = new ArrayList<>();
        boolean observeLatency;
        long submitted, executed;
        @Override public void execute(Runnable task) {
            submitted++;
            if (observeLatency) {
                long submittedAt = System.nanoTime();
                queue.addLast(() -> { latencies.add(System.nanoTime() - submittedAt); task.run(); });
            } else queue.addLast(task);
        }
        boolean step() {
            Runnable task = queue.pollFirst();
            if (task == null) return false;
            executed++;
            task.run();
            return true;
        }

        long[] latencySummary() {
            long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
            latencies.clear();
            latencies.trimToSize();
            if (sorted.length == 0) return new long[]{0, 0, 0, 0};
            return new long[]{sorted.length, sorted[(sorted.length - 1) / 2],
                sorted[(int)((sorted.length - 1) * 0.95)], sorted[sorted.length - 1]};
        }
    }

    static final class Storage extends TicketStorage {
        long adds, removals, checksum;
        @Override public boolean addTicket(long key, Ticket ticket) {
            boolean result = super.addTicket(key, ticket);
            adds++;
            checksum = Long.rotateLeft(checksum, 7) ^ key ^ ((long)ticket.getTicketLevel() << 32) ^ (result ? 1 : 0);
            return result;
        }
        @Override public boolean removeTicket(long key, Ticket ticket) {
            boolean result = super.removeTicket(key, ticket);
            removals++;
            checksum = Long.rotateLeft(checksum, 11) ^ key ^ ticket.getTicketLevel() ^ (result ? 1 : 0);
            return result;
        }
    }

    /** Only identity is used by DistanceManager; no entity behavior is invoked. */
    static final class PlayerToken extends ServerPlayer {
        private PlayerToken() { super(null, null, null, null); }
        @Override public int hashCode() { return System.identityHashCode(this); }
        @Override public boolean equals(Object other) { return this == other; }
    }

    /** Constructor is never executed. Only the overridden readiness lookup is
     * used, avoiding a mock interceptor in every measured future attachment. */
    static final class MapBoundary extends ChunkMap {
        Manager manager;
        private MapBoundary() { super(null, null, null, null, null, null, null, null, null, null, null, 0, false); }
        @Override public ChunkHolder getUpdatingChunkIfPresent(long key) { return manager.holders.get(key); }
    }

    static final class HolderBoundary extends ChunkHolder {
        CompletableFuture<ChunkResult<LevelChunk>> readiness;
        final Fixture fixture;
        HolderBoundary(Fixture fixture, long key, int level) {
            super(new ChunkPos(key), level, HEIGHT, null, (pos, previous, next, setter) -> {}, null);
            this.fixture = fixture;
            readiness = fixture.delayed ? new CompletableFuture<>() : READY;
        }
        @Override protected void updateHighestAllowedStatus(ChunkMap map) { fixture.highest++; }
        @Override protected void updateFutures(ChunkMap map, Executor executor) { fixture.futurePasses++; }
        @Override public CompletableFuture<ChunkResult<LevelChunk>> getEntityTickingChunkFuture() {
            fixture.attachments++;
            return readiness;
        }
    }

    static final class Manager extends DistanceManager {
        final Fixture fixture;
        final Long2ObjectOpenHashMap<HolderBoundary> holders = new Long2ObjectOpenHashMap<>();
        Manager(Fixture fixture) { super(fixture.storage, fixture.dispatcher, fixture.main); this.fixture = fixture; }
        @Override protected boolean isChunkToRemove(long key) { return false; }
        @Override protected ChunkHolder getChunk(long key) { return holders.get(key); }
        @Override protected ChunkHolder updateChunkScheduling(long key, int level, ChunkHolder previous, int old) {
            fixture.levelChanges++;
            HolderBoundary holder = holders.get(key);
            if (holder == null) {
                holder = new HolderBoundary(fixture, key, level);
                holders.put(key, holder);
            } else {
                if (fixture.delayed && ChunkLevel.isEntityTicking(level) && !ChunkLevel.isEntityTicking(old))
                    holder.readiness = new CompletableFuture<>();
                holder.setTicketLevel(level);
            }
            return holder;
        }
    }

    static final class Fixture {
        final Storage storage = new Storage();
        final Tasks dispatcher = new Tasks(), main = new Tasks();
        final Manager manager;
        final MapBoundary map;
        final PlayerToken[] players = new PlayerToken[4];
        final SectionPos[] positions = new SectionPos[4];
        final boolean delayed;
        long highest, futurePasses, attachments, levelChanges, runs;

        Fixture(boolean delayed) {
            this.delayed = delayed;
            manager = new Manager(this);
            map = OBJECTS.newInstance(MapBoundary.class);
            map.manager = manager;
            for (int i = 0; i < players.length; i++) players[i] = OBJECTS.newInstance(PlayerToken.class);
        }

        void move(int player, int x, int z) {
            leave(player);
            positions[player] = SectionPos.of(new ChunkPos(x, z), 4);
            manager.addPlayer(positions[player], players[player]);
        }

        void leave(int player) {
            if (positions[player] != null) {
                manager.removePlayer(positions[player], players[player]);
                positions[player] = null;
            }
        }

        void pump() {
            for (int attempt = 0; attempt < 1000000; attempt++) {
                boolean work = dispatcher.step();
                work |= main.step();
                if (!work) return;
            }
            throw new AssertionError("Unbounded benchmark executor drain");
        }

        void update() { runs += manager.runAllUpdates(map) ? 1 : 0; pump(); }

        void complete() {
            for (HolderBoundary holder : manager.holders.values())
                if (!holder.readiness.isDone()) holder.readiness.complete(ChunkResult.error("ready"));
        }

        long policyNativeBytes() {
            try {
                Field trackerField = DistanceManager.class.getDeclaredField("playerTicketManager");
                trackerField.setAccessible(true);
                Object tracker = trackerField.get(manager);
                Field policyField = tracker.getClass().getDeclaredField("policy");
                policyField.setAccessible(true);
                Object policy = policyField.get(tracker);
                Field admissionField = ThrottlingChunkTaskDispatcher.class.getDeclaredField("admission");
                admissionField.setAccessible(true);
                Object admission = admissionField.get(manager.ticketDispatcher);
                Method policyBytes = policy.getClass().getDeclaredMethod("retainedBytes");
                Method admissionBytes = admission.getClass().getDeclaredMethod("retainedBytes");
                policyBytes.setAccessible(true);
                admissionBytes.setAccessible(true);
                return (long)policyBytes.invoke(policy) + (long)admissionBytes.invoke(admission);
            } catch (NoSuchFieldException absentBeforeMigration) { return 0; }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }

        long sample(String workload, int steps) {
            storage.adds = storage.removals = storage.checksum = 0;
            dispatcher.submitted = dispatcher.executed = main.submitted = main.executed = 0;
            highest = futurePasses = attachments = levelChanges = runs = 0;
            manager.updatePlayerTickets(workload.equals("views") ? 2 : 8);
            for (int player = 0; player < 4; player++) move(player, player * 3, player * 2);
            for (int step = 0; step < steps; step++) {
                int player = step & 3;
                if (workload.equals("teleport")) {
                    int region = (step / 4) & 3;
                    move(player, region * 80 + player * 3, -region * 64 + player * 2);
                } else if (workload.equals("views")) {
                    if (step % 4 == 0) manager.updatePlayerTickets(new int[]{2, 8, 4, 12}[(step / 4) & 3]);
                } else {
                    int offset = (step / 4) & 7;
                    move(player, player * 3 + offset, player * 2 + (offset & 1));
                }
                update();
                if (workload.equals("disconnect") && step % 5 == 0) {
                    // Membership changes before the main-thread admission callback.
                    move(player, 600 + player, 600);
                    runs += manager.runAllUpdates(map) ? 1 : 0;
                    leave(player);
                    runs += manager.runAllUpdates(map) ? 1 : 0;
                    pump();
                }
                if (delayed && step % 4 == 3) complete();
                update();
            }
            for (int player = 0; player < players.length; player++) leave(player);
            for (int round = 0; round < 8; round++) { complete(); update(); }
            if (!dispatcher.queue.isEmpty() || !main.queue.isEmpty()) throw new AssertionError("Undrained benchmark work");
            return storage.checksum ^ storage.adds ^ Long.rotateLeft(storage.removals, 3)
                ^ Long.rotateLeft(dispatcher.executed, 7) ^ Long.rotateLeft(main.executed, 13)
                ^ Long.rotateLeft(highest, 19) ^ Long.rotateLeft(futurePasses, 23)
                ^ Long.rotateLeft(attachments, 29) ^ Long.rotateLeft(levelChanges, 31) ^ runs;
        }
    }

    static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
    }

    static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }

    static long threadAllocated() {
        var bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean allocation && allocation.isThreadAllocatedMemorySupported())
            return allocation.getThreadAllocatedBytes(Thread.currentThread().threadId());
        return -1;
    }

    static long[] residentAndSwap() throws Exception {
        long resident = -1, swap = -1;
        for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
            if (line.startsWith("VmRSS:")) resident = Long.parseLong(line.split("\\s+")[1]) * 1024;
            if (line.startsWith("VmSwap:")) swap = Long.parseLong(line.split("\\s+")[1]) * 1024;
        }
        return new long[]{resident, swap};
    }

    static void observe(String label, int index, Fixture fixture, String workload, int steps) throws Exception {
        long gc = gcMillis(), allocation = threadAllocated();
        long compilation = ManagementFactory.getCompilationMXBean().getTotalCompilationTime();
        long start = System.nanoTime();
        long checksum = fixture.sample(workload, steps);
        long elapsed = System.nanoTime() - start;
        long after = ManagementFactory.getCompilationMXBean().getTotalCompilationTime();
        long allocationAfter = threadAllocated();
        long allocated = allocation < 0 || allocationAfter < 0 ? -1 : allocationAfter - allocation;
        long retained = fixture.policyNativeBytes();
        long[] resident = residentAndSwap();
        System.out.println(label + " " + index + " " + elapsed + " CHECKSUM " + checksum
            + " COMPILE_MS " + (after - compilation) + " GC_MS " + (gcMillis() - gc)
            + " ADDS " + fixture.storage.adds + " REMOVALS " + fixture.storage.removals
            + " ATTACHMENTS " + fixture.attachments + " ALLOCATED_BYTES " + allocated
            + " POLICY_NATIVE_BYTES " + retained + " RSS_BYTES " + resident[0] + " SWAP_BYTES " + resident[1]
            + " HEAP_USED_BYTES " + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
    }

    public static void main(String[] args) throws Exception {
        String workload = args[0];
        int warmups = Integer.parseInt(args[1]), samples = Integer.parseInt(args[2]), steps = Integer.parseInt(args[3]);
        long cold = System.nanoTime();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        long constructor = System.nanoTime();
        Fixture fixture = new Fixture(workload.equals("readiness"));
        long constructed = System.nanoTime();
        System.out.println("COLD " + (constructed - cold) + " CONSTRUCT " + (constructed - constructor));
        long firstStart = System.nanoTime();
        long firstChecksum = fixture.sample(workload, steps);
        System.out.println("FIRST_FEEDBACK " + (System.nanoTime() - firstStart) + " CHECKSUM " + firstChecksum);
        int cycles = 0, quiet = 0;
        while ((cycles < warmups || quiet < 12) && cycles < Math.max(warmups, 512)) {
            long compilation = ManagementFactory.getCompilationMXBean().getTotalCompilationTime();
            observe("WARMUP", cycles, fixture, workload, steps);
            // Include the reporting helpers in warmup so their delayed class/
            // reflection/compilation work cannot spill into measured feedback.
            long after = ManagementFactory.getCompilationMXBean().getTotalCompilationTime();
            quiet = after == compilation ? quiet + 1 : 0;
            cycles++;
        }
        System.out.println("WARMUP_COMPLETE CYCLES " + cycles + " QUIET_STREAK " + quiet);
        if (quiet < 12) throw new IllegalStateException("Compiler warmup did not settle within its bound");
        for (int index = 0; index < samples; index++) observe("TIMING", index, fixture, workload, steps);
        // Separate diagnostic pass: timestamping/wrapping executor tasks is
        // deliberately outside every primary timing/allocation window.
        fixture.dispatcher.observeLatency = fixture.main.observeLatency = true;
        long latencyStart = System.nanoTime();
        long checksum = fixture.sample(workload, steps);
        long latencyElapsed = System.nanoTime() - latencyStart;
        long[] dispatched = fixture.dispatcher.latencySummary(), main = fixture.main.latencySummary();
        fixture.dispatcher.observeLatency = fixture.main.observeLatency = false;
        System.out.println("LATENCY CHECKSUM " + checksum + " ELAPSED_NS " + latencyElapsed
            + " DISPATCH_COUNT " + dispatched[0] + " DISPATCH_P50_NS " + dispatched[1]
            + " DISPATCH_P95_NS " + dispatched[2] + " DISPATCH_MAX_NS " + dispatched[3]
            + " MAIN_COUNT " + main[0] + " MAIN_P50_NS " + main[1]
            + " MAIN_P95_NS " + main[2] + " MAIN_MAX_NS " + main[3]);
        long beforeGc = gcCount();
        System.gc();
        Thread.sleep(100);
        System.gc();
        Thread.sleep(100);
        long[] settled = residentAndSwap();
        System.out.println("SETTLED GC_COLLECTIONS " + (gcCount() - beforeGc)
            + " RSS_BYTES " + settled[0] + " SWAP_BYTES " + settled[1]
            + " HEAP_USED_BYTES " + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()
            + " POLICY_NATIVE_BYTES " + fixture.policyNativeBytes());
    }
}
