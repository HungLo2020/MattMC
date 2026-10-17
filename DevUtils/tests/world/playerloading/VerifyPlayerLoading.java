package net.minecraft.server.level;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.LevelChunk;
import org.mockito.Mockito;

/**
 * Runs the actual DistanceManager player-ticket policy and actual throttling
 * dispatcher on any classpath. The two executors are stepped explicitly; holder
 * callbacks and readiness futures are fixture boundaries, not copied policy.
 * Frozen, the pre-change classes and the candidate compile this identical source.
 * Use equal JVM identity hashes for ordered holder passes. A real dedicated-server
 * player lifecycle is a separate mandatory gate, not certified by this driver.
 */
public final class VerifyPlayerLoading {
    private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(0, 16);

    static final class Trace {
        final PrintWriter writer;
        final MessageDigest digest;
        long events, adds, removes, attachments, completions, rejected, failures;
        int maxInFlight;

        Trace(PrintWriter writer) throws Exception {
            this.writer = writer;
            this.digest = MessageDigest.getInstance("SHA-256");
        }

        void event(String value) {
            writer.println(value);
            digest.update((value + "\n").getBytes(StandardCharsets.UTF_8));
            events++;
        }

        void attempt(String name, Runnable action) {
            event("BEGIN " + name);
            try {
                action.run();
                event("END " + name);
            } catch (RuntimeException failure) {
                failures++;
                event("THROW " + name + " " + failure.getClass().getName() + " " + failure.getMessage());
            }
        }
    }

    record Work(long id, Runnable body) {}

    static final class SteppedExecutor implements Executor {
        final String name;
        final Trace trace;
        final ArrayDeque<Work> queue = new ArrayDeque<>();
        long next;
        int reject;
        boolean immediate;
        Runnable beforeExecute;

        SteppedExecutor(String name, Trace trace) {
            this.name = name;
            this.trace = trace;
        }

        @Override
        public void execute(Runnable action) {
            long id = ++next;
            if (reject > 0) {
                reject--;
                trace.rejected++;
                trace.event("REJECT " + name + " " + id);
                throw new RejectedExecutionException("fixture " + name);
            }
            trace.event("ENQUEUE " + name + " " + id);
            if (beforeExecute != null) {
                Runnable hook = beforeExecute;
                beforeExecute = null;
                trace.event("EXECUTOR-REENTRY " + name + " " + id);
                hook.run();
            }
            if (immediate) {
                trace.event("INLINE " + name + " " + id);
                action.run();
                return;
            }
            queue.addLast(new Work(id, action));
        }

        boolean step() {
            Work work = queue.pollFirst();
            if (work == null) return false;
            trace.attempt("EXEC " + name + " " + work.id(), work.body());
            return true;
        }
    }

    static final class Storage extends TicketStorage {
        final Trace trace;
        final TreeSet<Long> observed = new TreeSet<>();
        final IdentityHashMap<Ticket, Integer> identities = new IdentityHashMap<>();

        Storage(Trace trace) { this.trace = trace; }

        int identity(Ticket ticket) {
            return identities.computeIfAbsent(ticket, unused -> identities.size() + 1);
        }

        String ticket(Ticket ticket) {
            TicketType type = ticket.getType();
            String name = type == TicketType.PLAYER_LOADING ? "loading"
                : type == TicketType.PLAYER_SIMULATION ? "simulation"
                : type == TicketType.FORCED ? "forced" : "custom";
            return identity(ticket) + ":" + name + ":" + type.timeout() + ":" + type.flags() + ":" + ticket.getTicketLevel();
        }

        @Override
        public boolean addTicket(long key, Ticket ticket) {
            observed.add(key);
            trace.event("ADD? " + key + " " + ticket(ticket));
            boolean result = super.addTicket(key, ticket);
            if (ticket.getType() == TicketType.PLAYER_LOADING) trace.adds++;
            trace.event("ADD! " + key + " " + result);
            return result;
        }

        @Override
        public boolean removeTicket(long key, Ticket ticket) {
            observed.add(key);
            trace.event("REMOVE? " + key + " " + ticket(ticket));
            boolean result = super.removeTicket(key, ticket);
            if (ticket.getType() == TicketType.PLAYER_LOADING) trace.removes++;
            trace.event("REMOVE! " + key + " " + result);
            return result;
        }

        void snapshot() {
            for (long key : observed) {
                List<Ticket> tickets = getTickets(key);
                if (!tickets.isEmpty()) {
                    StringBuilder out = new StringBuilder("TICKETS ").append(key);
                    for (Ticket ticket : tickets) out.append(' ').append(ticket(ticket));
                    trace.event(out.toString());
                }
            }
        }
    }

    static final class Readiness extends CompletableFuture<ChunkResult<LevelChunk>> {
        final Trace trace;
        final long key;
        final int generation;
        boolean throwOnAttach;

        Readiness(Trace trace, long key, int generation) {
            this.trace = trace;
            this.key = key;
            this.generation = generation;
        }

        @Override
        public CompletableFuture<Void> thenAccept(Consumer<? super ChunkResult<LevelChunk>> action) {
            trace.attachments++;
            trace.event("ATTACH " + key + " " + generation);
            if (throwOnAttach) throw new IllegalStateException("fixture attach");
            return super.thenAccept(value -> {
                trace.completions++;
                trace.event("READY-CALLBACK " + key + " " + generation + " " + value.isSuccess());
                action.accept(value);
            });
        }
    }

    static final class Holder extends ChunkHolder {
        final Fixture fixture;
        final long key;
        Readiness readiness;
        int generation;
        boolean throwHighest, throwFutures;
        Runnable onFutures;

        Holder(Fixture fixture, long key, int level) {
            super(new ChunkPos(key), level, HEIGHT, null, (pos, previous, next, setter) -> {}, null);
            this.fixture = fixture;
            this.key = key;
            resetReadiness();
        }

        void resetReadiness() {
            readiness = new Readiness(fixture.trace, key, ++generation);
        }

        @Override
        protected void updateHighestAllowedStatus(ChunkMap map) {
            fixture.trace.event("HIGHEST " + key + " " + getTicketLevel());
            if (throwHighest) throw new IllegalStateException("fixture highest");
        }

        @Override
        protected void updateFutures(ChunkMap map, Executor executor) {
            fixture.trace.event("FUTURES " + key + " " + getTicketLevel());
            if (throwFutures) throw new IllegalStateException("fixture futures");
            if (onFutures != null) {
                Runnable action = onFutures;
                onFutures = null;
                action.run();
            }
        }

        @Override
        public CompletableFuture<ChunkResult<LevelChunk>> getEntityTickingChunkFuture() {
            fixture.trace.event("GET-READY " + key + " " + generation);
            return readiness;
        }
    }

    static final class Manager extends DistanceManager {
        final Fixture fixture;
        final Map<Long, Holder> holders = new TreeMap<>();

        Manager(Fixture fixture) {
            super(fixture.storage, fixture.dispatcher, fixture.main);
            this.fixture = fixture;
        }

        @Override protected boolean isChunkToRemove(long key) { return false; }
        @Override protected ChunkHolder getChunk(long key) { return holders.get(key); }

        @Override
        protected ChunkHolder updateChunkScheduling(long key, int level, ChunkHolder previous, int oldLevel) {
            fixture.trace.event("LEVEL " + key + " " + oldLevel + " " + level);
            Holder holder = holders.get(key);
            if (holder == null) {
                holder = new Holder(fixture, key, level);
                holders.put(key, holder);
            } else holder.setTicketLevel(level);
            return holder;
        }
    }

    static final class Fixture {
        final Trace trace;
        final Storage storage;
        final SteppedExecutor dispatcher, main;
        final Manager manager;
        final ChunkMap map;
        final TreeSet<Long> missing = new TreeSet<>();
        final List<Readiness> retired = new ArrayList<>();
        final ServerPlayer[] players = new ServerPlayer[3];
        final SectionPos[] positions = new SectionPos[3];

        Fixture(Trace trace) {
            this.trace = trace;
            storage = new Storage(trace);
            dispatcher = new SteppedExecutor("dispatcher", trace);
            main = new SteppedExecutor("main", trace);
            manager = new Manager(this);
            map = Mockito.mock(ChunkMap.class, Mockito.withSettings().stubOnly());
            Mockito.when(map.getUpdatingChunkIfPresent(Mockito.anyLong())).thenAnswer(call -> {
                long key = call.getArgument(0);
                trace.event("UPDATING " + key + " " + !missing.contains(key));
                return missing.contains(key) ? null : manager.holders.get(key);
            });
            for (int i = 0; i < players.length; i++)
                players[i] = Mockito.mock(ServerPlayer.class, Mockito.withSettings().stubOnly());
        }

        void enter(int player, long key, int y) {
            if (positions[player] != null) leave(player);
            positions[player] = SectionPos.of(new ChunkPos(key), y);
            manager.addPlayer(positions[player], players[player]);
        }

        void leave(int player) {
            if (positions[player] != null) {
                manager.removePlayer(positions[player], players[player]);
                positions[player] = null;
            }
        }

        void run() { trace.event("RUN-RESULT " + manager.runAllUpdates(map)); }

        void pump(int limit) {
            for (int i = 0; i < limit; i++) {
                boolean work = dispatcher.step();
                work |= main.step();
                if (!work) break;
            }
        }

        void complete(int kind) {
            // Complete current readiness in stable key order. Old callbacks keep
            // the original future instance if the holder's field was replaced.
            for (Holder holder : manager.holders.values()) {
                Readiness future = holder.readiness;
                if (!future.isDone()) {
                    trace.event("COMPLETE " + holder.key + " " + future.generation + " " + kind);
                    if (kind == 0) future.complete(ChunkResult.error("fixture unloaded"));
                    else if (kind == 1) future.completeExceptionally(new IllegalStateException("fixture failed"));
                    else future.cancel(false);
                }
            }
        }

        void snapshot(int step) {
            String status = manager.getDebugStatus();
            // ChunkPos.toString uses [x, z]; count its distinct position entries.
            int inFlight = status.split(":\\[", -1).length - 1;
            trace.maxInFlight = Math.max(trace.maxInFlight, inFlight);
            trace.event("SNAPSHOT " + step + " " + main.queue.size() + " " + dispatcher.queue.size()
                + " " + status);
            storage.snapshot();
        }
    }

    /** One admitted key with a pending readiness future and the release scan not
     * yet run. All graph/dispatch/ticket work is the production implementation. */
    static Fixture pending(Trace trace) {
        Fixture fixture = new Fixture(trace);
        fixture.manager.updatePlayerTickets(0);
        fixture.enter(0, 0, 4);
        fixture.run();
        fixture.pump(50000);
        fixture.run();
        if (fixture.manager.holders.get(0L) == null || fixture.storage.getTickets(0L).stream()
                .noneMatch(ticket -> ticket.getType() == TicketType.PLAYER_LOADING))
            throw new AssertionError("No actual admitted player-loading ticket");
        return fixture;
    }

    record RetainedCallback(WeakReference<Manager> manager, Readiness future,
                            SteppedExecutor dispatcher, SteppedExecutor main) {}

    static RetainedCallback retainCallback(Trace trace) {
        Fixture fixture = pending(trace);
        fixture.run();
        return new RetainedCallback(new WeakReference<>(fixture.manager),
            fixture.manager.holders.get(0L).readiness, fixture.dispatcher, fixture.main);
    }

    static void callbackLifetime(Trace trace) {
        trace.event("CASE retained-readiness-callback");
        RetainedCallback retained = retainCallback(trace);
        // Ordinary caller references to the manager/owner are gone. The actual
        // pending production callback must keep its Java/native chain reachable.
        System.gc();
        if (retained.manager().get() == null) throw new AssertionError("Pending callback lost its manager");
        long before = trace.completions;
        retained.future().complete(ChunkResult.error("retained readiness"));
        for (int attempt = 0; attempt < 50000; attempt++) {
            boolean work = retained.dispatcher().step();
            work |= retained.main().step();
            if (!work) break;
        }
        if (trace.completions != before + 1) throw new AssertionError("Retained callback did not execute once");
        trace.event("RETAINED-CALLBACK completed once");
    }

    static void scriptedCases(Trace trace) {
        for (String kind : new String[]{"normal-error", "exceptional", "cancelled", "attach-throws",
                "missing-holder", "highest-throws", "futures-throws", "reentry", "view-reentry", "type-identity"}) {
            trace.event("CASE " + kind);
            Fixture f = pending(trace);
            Holder holder = f.manager.holders.get(0L);
            switch (kind) {
                case "normal-error" -> holder.readiness.complete(ChunkResult.error("normal failure result"));
                case "exceptional" -> holder.readiness.completeExceptionally(new IllegalStateException("fixture exceptional"));
                case "cancelled" -> holder.readiness.cancel(false);
                case "attach-throws" -> holder.readiness.throwOnAttach = true;
                case "missing-holder" -> f.missing.add(0L);
                case "highest-throws" -> {
                    holder.throwHighest = true;
                    f.manager.chunksToUpdateFutures.add(holder);
                }
                case "futures-throws" -> {
                    holder.throwFutures = true;
                    f.manager.chunksToUpdateFutures.add(holder);
                }
                case "reentry" -> {
                    holder.onFutures = () -> {
                        trace.event("REENTER-VIEW");
                        f.manager.updatePlayerTickets(1);
                    };
                    f.manager.chunksToUpdateFutures.add(holder);
                }
                case "view-reentry" -> {
                    f.dispatcher.beforeExecute = () -> {
                        f.enter(1, ChunkPos.asLong(7, -3), 4);
                        f.run();
                    };
                    trace.attempt("view-scan-executor-reentry", () -> f.manager.updatePlayerTickets(1));
                }
                case "type-identity" -> {
                    f.storage.removeTicket(0L, new Ticket(TicketType.PLAYER_LOADING, DistanceManager.PLAYER_TICKET_LEVEL));
                    f.storage.addTicket(0L, new Ticket(new TicketType(0, 2), DistanceManager.PLAYER_TICKET_LEVEL));
                }
                default -> throw new AssertionError(kind);
            }
            trace.attempt("scripted " + kind, f::run);
            f.snapshot(-10);
            holder.throwHighest = holder.throwFutures = holder.readiness.throwOnAttach = false;
            f.missing.clear();
            trace.attempt("retry " + kind, f::run);
            f.complete(0);
            f.pump(50000);
            f.snapshot(-11);
            f.leave(0);
            for (int round = 0; round < 3; round++) {
                trace.attempt("scripted-cleanup " + kind + " " + round, f::run);
                f.pump(50000);
            }
            f.snapshot(-12);
        }
        trace.event("CASE inline-executors");
        Fixture inline = new Fixture(trace);
        inline.main.immediate = inline.dispatcher.immediate = true;
        inline.manager.updatePlayerTickets(1);
        inline.enter(0, 0L, 4);
        trace.attempt("inline-run", inline::run);
        trace.attempt("inline-release", inline::run);
        inline.complete(0);
        inline.manager.updatePlayerTickets(0);
        inline.leave(0);
        for (int round = 0; round < 4; round++) trace.attempt("inline-final " + round, inline::run);
        inline.snapshot(-20);
        trace.event("CASE queued-stale-admission");
        Fixture stale = new Fixture(trace);
        stale.manager.updatePlayerTickets(0);
        stale.enter(0, 0L, 4);
        stale.run();
        // First run only the dispatcher, then the TaskScheduler wrapper. The
        // actual admission callback is the next main-thread hop, still queued.
        for (int step = 0; step < 10000 && stale.main.queue.isEmpty(); step++) {
            if (!stale.dispatcher.step()) break;
        }
        if (stale.main.queue.size() != 1 || !stale.main.step() || stale.main.queue.size() != 1)
            throw new AssertionError("Did not capture the actual delayed admission callback");
        stale.snapshot(-30);
        stale.leave(0);
        stale.run();
        trace.event("STALE-ADMISSION callback after membership withdrawal");
        if (!stale.main.step()) throw new AssertionError("Captured admission callback disappeared");
        trace.event("STALE-ADMISSION ticket=" + stale.storage.getTickets(0L).stream()
            .anyMatch(ticket -> ticket.getType() == TicketType.PLAYER_LOADING));
        stale.snapshot(-31);
        for (int round = 0; round < 3; round++) { stale.pump(50000); stale.run(); }
        stale.snapshot(-32);
        trace.event("CASE view-and-position-boundaries");
        Fixture bounds = pending(trace);
        for (int view : new int[]{8, 32, -1, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 2}) {
            trace.event("BOUNDARY-VIEW " + view);
            bounds.manager.updatePlayerTickets(view);
            bounds.pump(50000);
            bounds.run();
            bounds.snapshot(view);
        }
        bounds.leave(0);
        for (int round = 0; round < 4; round++) { bounds.run(); bounds.complete(0); bounds.pump(50000); }
        // Legal world edge with margin for graph/holder neighbours. SectionPos
        // can encode farther coordinates, but real holders reject those.
        int edge = ChunkPos.MAX_COORDINATE_VALUE - 64;
        bounds.enter(0, ChunkPos.asLong(edge, -edge), 4);
        bounds.run();
        bounds.pump(50000);
        bounds.run();
        bounds.snapshot(-40);
        bounds.leave(0);
        for (int round = 0; round < 4; round++) { bounds.run(); bounds.complete(0); bounds.pump(50000); }
        bounds.snapshot(-41);
        callbackLifetime(trace);
    }

    static void history(Trace trace, long seed, int steps) {
        Fixture f = new Fixture(trace);
        Random random = new Random(seed);
        long[] keys = {0L, ChunkPos.asLong(1, 0), ChunkPos.asLong(-1, -1),
            ChunkPos.asLong(7, -3), ChunkPos.asLong(-8, 9)};
        trace.event("SEED " + seed);
        trace.attempt("view2", () -> f.manager.updatePlayerTickets(2));
        trace.attempt("join0", () -> f.enter(0, 0, 4));
        trace.attempt("initial-run", f::run);
        f.pump(50000);
        f.snapshot(-3);
        trace.attempt("initial-release", f::run);
        trace.attempt("initial-attach", f::run);
        f.complete(0);
        // Normal readiness feedback must be exercised before random executor
        // rejection can strand the original dispatcher in its RUNNING state.
        for (int round = 0; round < 8; round++) {
            f.pump(50000);
            trace.attempt("ready-drain " + round, f::run);
            f.complete(0);
        }
        trace.attempt("prefix-shrink", () -> f.manager.updatePlayerTickets(0));
        f.pump(50000);
        trace.attempt("prefix-shrink-run", f::run);
        trace.attempt("prefix-expand", () -> f.manager.updatePlayerTickets(2));
        // Change eligibility again while the entering callables are queued.
        trace.attempt("prefix-stale", () -> f.manager.updatePlayerTickets(1));
        f.pump(50000);
        f.snapshot(-1);
        for (int step = 0; step < steps; step++) {
            int action = random.nextInt(14);
            int player = random.nextInt(f.players.length);
            long key = keys[random.nextInt(keys.length)];
            int selected = step;
            trace.attempt("STEP " + step + " " + action + " " + player + " " + key, () -> {
                switch (action) {
                    case 0 -> f.enter(player, key, 4);
                    case 1 -> f.leave(player);
                    case 2 -> f.enter(player, key, random.nextBoolean() ? 4 : 5);
                    case 3 -> f.manager.updatePlayerTickets(new int[]{0, 1, 2, 3, 4}[random.nextInt(5)]);
                    case 4, 5 -> f.run();
                    case 6 -> f.dispatcher.step();
                    case 7 -> f.main.step();
                    case 8 -> f.pump(1 + random.nextInt(30));
                    case 9 -> f.complete(0);
                    case 10 -> f.complete(1 + random.nextInt(2));
                    case 11 -> {
                        Holder holder = f.manager.holders.get(key);
                        if (holder != null) {
                            f.retired.add(holder.readiness);
                            holder.resetReadiness();
                            trace.event("REPLACE-READY " + key + " " + holder.generation);
                        }
                    }
                    case 12 -> {
                        if (!f.retired.isEmpty()) {
                            Readiness old = f.retired.get(random.nextInt(f.retired.size()));
                            trace.event("COMPLETE-OLD " + old.key + " " + old.generation);
                            old.complete(ChunkResult.error("old"));
                        }
                    }
                    default -> {
                        if (selected % 3 == 0) f.main.reject = 1;
                        else if (selected % 3 == 1) f.dispatcher.reject = 2;
                        else {
                            if (!f.missing.add(key)) f.missing.remove(key);
                        }
                    }
                }
            });
            f.snapshot(step);
        }
        // Drain normal cancellation paths after restoring the view/players.
        f.main.reject = f.dispatcher.reject = 0;
        f.missing.clear();
        for (int player = 0; player < f.players.length; player++) f.leave(player);
        for (int round = 0; round < 4; round++) {
            trace.attempt("final-run " + round, f::run);
            f.complete(0);
            f.pump(50000);
        }
        f.snapshot(steps);
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        int seeds = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        int steps = args.length > 3 ? Integer.parseInt(args[3]) : 160;
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[1]), StandardCharsets.UTF_8))) {
            Trace trace = new Trace(out);
            scriptedCases(trace);
            for (int seed = 0; seed < seeds; seed++) history(trace, 43 + seed, steps);
            System.out.println("TRACE " + HexFormat.of().formatHex(trace.digest.digest()) + " " + trace.events);
            System.out.println("COVERAGE adds=" + trace.adds + " removes=" + trace.removes + " attachments="
                + trace.attachments + " callbacks=" + trace.completions + " rejected=" + trace.rejected
                + " failures=" + trace.failures + " maxInFlight=" + trace.maxInFlight);
            if (trace.adds == 0 || trace.removes == 0 || trace.attachments == 0 || trace.completions == 0
                    || trace.failures == 0 || (seeds > 0 && trace.maxInFlight != 4))
                throw new AssertionError("Vacuous player-loading fixture");
        }
    }
}
