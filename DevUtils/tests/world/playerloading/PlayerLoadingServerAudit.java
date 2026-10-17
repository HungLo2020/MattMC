package net.minecraft.server.level;

import com.google.gson.GsonBuilder;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.profile.PlayerProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;

/** Actual dedicated-server player-loading lifecycle. Only packet transport and
 * the external test controller are replaced; ordinary player/chunk paths run. */
public final class PlayerLoadingServerAudit {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final Map<Object, Set<Long>> IN_FLIGHT = new IdentityHashMap<>();
    private static final AtomicLong NATIVE_ACCEPTED = new AtomicLong(), NATIVE_REJECTED = new AtomicLong();
    private static long starts, releases, queuedRegionStarts;
    private static int peak;

    public static synchronized void admission(Object owner, long key, boolean starting) {
        Set<Long> keys = IN_FLIGHT.computeIfAbsent(owner, ignored -> new HashSet<>());
        if (starting) {
            keys.add(key);
            starts++;
            if (Math.abs((int)key - 600) <= 2 && Math.abs((int)(key >> 32) - 600) <= 2) queuedRegionStarts++;
            peak = Math.max(peak, keys.size());
        } else {
            keys.remove(key);
            releases++;
        }
    }

    public static void nativeRecheck(boolean admitted) {
        (admitted ? NATIVE_ACCEPTED : NATIVE_REJECTED).incrementAndGet();
    }

    public static void capture(Object server) {
        if (STARTED.compareAndSet(false, true))
            new Thread(() -> run((MinecraftServer)server), "player-loading-audit").start();
    }

    /** Records/succeeds writes in memory. This does not certify a network client. */
    static final class PacketSink extends Connection implements AutoCloseable {
        final EmbeddedChannel transport = new EmbeddedChannel();
        final AtomicLong packets = new AtomicLong();
        PacketListener listener;
        boolean open = true;

        PacketSink() { super(PacketFlow.SERVERBOUND); }
        @Override public boolean isConnected() { return open; }
        @Override public boolean isConnecting() { return false; }
        @Override public boolean isMemoryConnection() { return true; }
        @Override public SocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 0); }
        @Override public String getLoggableAddress(boolean includeIp) { return "player-loading-fixture"; }
        @Override public PacketListener getPacketListener() { return listener; }
        @Override public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T value) { listener = value; }
        @Override public void setupOutboundProtocol(ProtocolInfo<?> protocol) {}
        @Override public void flushChannel() {}
        @Override public void send(Packet<?> packet) { send(packet, null, true); }
        @Override public void send(Packet<?> packet, ChannelFutureListener callback) { send(packet, callback, true); }
        @Override public void send(Packet<?> packet, ChannelFutureListener callback, boolean flush) {
            packets.incrementAndGet();
            if (callback != null) {
                try { callback.operationComplete(transport.newSucceededFuture()); }
                catch (Exception error) { throw new IllegalStateException(error); }
            }
        }
        @Override public void disconnect(Component reason) { open = false; }
        @Override public void disconnect(DisconnectionDetails reason) { open = false; }
        @Override public void close() { open = false; transport.finishAndReleaseAll(); }
    }

    record Player(ServerPlayer player, PacketSink sink) {}

    static <T> T on(MinecraftServer server, Supplier<T> action) throws Exception {
        return server.submit(action).get(120, TimeUnit.SECONDS);
    }

    static void action(MinecraftServer server, Runnable action) throws Exception {
        on(server, () -> { action.run(); return null; });
    }

    static void check(List<Map<String, Object>> checks, String name, boolean passed) {
        checks.add(Map.of("check", name, "passed", passed));
        System.out.println("PLAYER_LOADING_CHECK " + name + " " + passed);
        if (!passed) throw new AssertionError(name);
    }

    static void await(MinecraftServer server, List<Map<String, Object>> checks, String name, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (System.nanoTime() < deadline) {
            if (on(server, condition::getAsBoolean)) { check(checks, name, true); return; }
            Thread.sleep(25);
        }
        check(checks, name, false);
    }

    static boolean loading(ServerLevel level, int x, int z) {
        return level.getChunkSource().chunkMap.getDistanceManager().ticketStorage.getTickets(ChunkPos.asLong(x, z))
            .stream().anyMatch(ticket -> ticket.getType() == TicketType.PLAYER_LOADING);
    }

    static boolean noLoadingRegion(ServerLevel level, int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++)
            if (loading(level, x + dx, z + dz)) return false;
        return true;
    }

    static boolean ready(ServerLevel level, int x, int z) {
        ChunkHolder holder = level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(ChunkPos.asLong(x, z));
        return loading(level, x, z) && holder != null
            && holder.getEntityTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).isSuccess();
    }

    static Player join(MinecraftServer server, ServerLevel level, String name, double x, double z) {
        PlayerProfile profile = PlayerProfile.createOffline(name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.absSnapTo(x, -55, z, 0, 0);
        PacketSink sink = new PacketSink();
        server.getPlayerList().placeNewPlayer(sink, player, CommonListenerCookie.createInitial(profile, false));
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.setNoGravity(true);
        return new Player(player, sink);
    }

    static void move(Player player, double x, double y, double z) {
        ServerPlayer actual = player.player();
        actual.absSnapTo(x, y, z, 0, 0);
        actual.connection.resetPosition();
        actual.level().getChunkSource().move(actual);
    }

    static void remove(MinecraftServer server, Player player) {
        server.getPlayerList().remove(player.player());
        player.sink().close();
    }

    static synchronized boolean noInFlight() { return IN_FLIGHT.values().stream().allMatch(Set::isEmpty); }

    static synchronized Map<String, Object> coverage() {
        return Map.of("starts", starts, "releases", releases, "peakDistinctPositions", peak,
            "queuedRegionStarts", queuedRegionStarts, "nativeAccepted", NATIVE_ACCEPTED.get(),
            "nativeRejected", NATIVE_REJECTED.get(), "allReleased", noInFlight());
    }

    static void run(MinecraftServer server) {
        Path output = Path.of(System.getProperty("playerLoading.output")).toAbsolutePath();
        List<Map<String, Object>> checks = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        List<Player> players = new ArrayList<>();
        try {
            Files.createDirectories(output);
            check(checks, "actual DedicatedServer", server instanceof DedicatedServer);
            ServerLevel level = server.overworld();
            action(server, () -> {
                server.tickRateManager().setTickRate(100);
                server.getPlayerList().setViewDistance(2);
                for (String rule : List.of("doMobSpawning false", "randomTickSpeed 0", "doWeatherCycle false",
                        "doDaylightCycle false", "spectatorsGenerateChunks false"))
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule " + rule);
            });
            Player first = on(server, () -> join(server, level, "SwarmLoadingOne", 320.5, 320.5));
            players.add(first);
            await(server, checks, "real player-loading ticket reaches entity ticking", () -> ready(level, 20, 20));
            check(checks, "joined through PlayerList and sent packets", on(server,
                () -> server.getPlayerList().getPlayers().contains(first.player()) && first.sink().packets.get() > 0));
            BlockPos gold = new BlockPos(321, -55, 321), lamp = new BlockPos(335, -55, 321), across = lamp.east();
            if (Boolean.getBoolean("playerLoading.reopen")) {
                check(checks, "edited block survives save and reopen", on(server, () -> level.getBlockState(gold).is(Blocks.GOLD_BLOCK)));
                check(checks, "light source survives save and reopen", on(server, () -> level.getBlockState(lamp).is(Blocks.GLOWSTONE)));
                await(server, checks, "boundary light survives reopen", () -> level.getChunkSource().getLightEngine()
                    .getLayerListener(LightLayer.BLOCK).getLightValue(across) >= 14);
            } else {
                Player second = on(server, () -> join(server, level, "SwarmLoadingTwo", 321.5, 321.5));
                players.add(second);
                check(checks, "shared chunk retains one loading ticket", on(server,
                    () -> level.getChunkSource().chunkMap.getDistanceManager().ticketStorage.getTickets(ChunkPos.asLong(20, 20))
                        .stream().filter(ticket -> ticket.getType() == TicketType.PLAYER_LOADING).count() == 1));
                action(server, () -> { level.setBlock(gold, Blocks.GOLD_BLOCK.defaultBlockState(), 3); level.setBlock(lamp, Blocks.GLOWSTONE.defaultBlockState(), 3); });
                await(server, checks, "block light crosses loaded chunk boundary", () -> level.getChunkSource().getLightEngine()
                    .getLayerListener(LightLayer.BLOCK).getLightValue(across) >= 14);
                action(server, () -> move(first, 640.5, -55, 640.5));
                await(server, checks, "movement loads new player region", () -> ready(level, 40, 40));
                check(checks, "remaining shared player keeps old ticket", on(server, () -> loading(level, 20, 20)));
                action(server, () -> server.getPlayerList().setViewDistance(4));
                await(server, checks, "expanded view adds outer player ticket", () -> loading(level, 44, 40));
                action(server, () -> server.getPlayerList().setViewDistance(2));
                await(server, checks, "shrunk view removes outer player ticket", () -> !loading(level, 44, 40));
                action(server, () -> move(first, 640.5, -39, 640.5));
                await(server, checks, "vertical section move preserves loading", () -> ready(level, 40, 40));
                action(server, () -> { first.player().setGameMode(GameType.SPECTATOR); level.getChunkSource().move(first.player()); });
                await(server, checks, "spectator rule withdraws loading", () -> !loading(level, 40, 40));
                action(server, () -> { first.player().setGameMode(GameType.CREATIVE); level.getChunkSource().move(first.player()); });
                await(server, checks, "return from spectator restores loading", () -> ready(level, 40, 40));
                ServerLevel nether = server.getLevel(Level.NETHER);
                check(checks, "real dimension transfer accepted", on(server, () -> first.player().teleportTo(nether,
                    1280.5, 70, 1280.5, Set.of(), 0, 0, false)));
                await(server, checks, "new dimension owns player-loading ticket", () -> ready(nether, 80, 80));
                await(server, checks, "initial player admissions settle before cancellation fixture", PlayerLoadingServerAudit::noInFlight);
                action(server, () -> {
                    move(second, 9600.5, -55, 9600.5);
                    level.getChunkSource().runDistanceManagerUpdates();
                    // Allow the real dispatcher worker to queue admission callbacks
                    // while this test action still owns the main server thread.
                    long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100);
                    while (System.nanoTime() < end) Thread.onSpinWait();
                    remove(server, second);
                    level.getChunkSource().runDistanceManagerUpdates();
                });
                players.remove(second);
                await(server, checks, "disconnect cancels queued player admission", () -> noLoadingRegion(level, 600, 600, 2));
                await(server, checks, "old player region unloads", () -> !level.getChunkSource().hasChunk(20, 20));
                synchronized (PlayerLoadingServerAudit.class) {
                    check(checks, "actual four-position throttle saturated", peak == 4);
                    check(checks, "queued-disconnect region exercised actual dispatch", queuedRegionStarts > 0);
                }
            }
            for (Player player : List.copyOf(players)) action(server, () -> remove(server, player));
            players.clear();
            await(server, checks, "normal readiness and cancellation release all positions", PlayerLoadingServerAudit::noInFlight);
            if (Boolean.getBoolean("playerLoading.expectNative")) {
                check(checks, "actual native admission rechecks executed", NATIVE_ACCEPTED.get() > 0);
                if (!Boolean.getBoolean("playerLoading.reopen"))
                    check(checks, "actual native stale admission rejected", NATIVE_REJECTED.get() > 0);
            }
            action(server, () -> server.saveEverything(false, true, false));
            if (Boolean.getBoolean("playerLoading.shutdownPending")) {
                if (Boolean.getBoolean("playerLoading.reopen")) {
                    action(server, () -> {
                        for (int z = 1000; z < 1003; z++) for (int x = 1000; x < 1003; x++)
                            level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, new ChunkPos(x, z), 0);
                    });
                    for (int z = 1000; z < 1003; z++) for (int x = 1000; x < 1003; x++) {
                        var chunk = level.getChunkSource().getChunkFuture(x, z,
                            net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true)
                            .get(120, TimeUnit.SECONDS);
                        check(checks, "pending-shutdown region reopens at FULL " + x + "," + z, chunk.isSuccess());
                    }
                    action(server, () -> {
                        for (int z = 1000; z < 1003; z++) for (int x = 1000; x < 1003; x++)
                            level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, new ChunkPos(x, z), 0);
                    });
                } else {
                    action(server, () -> {
                        var futures = new ArrayList<java.util.concurrent.CompletableFuture<?>>();
                        for (int z = 1000; z < 1009; z++) for (int x = 1000; x < 1009; x++)
                            level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, new ChunkPos(x, z), 0);
                        level.getChunkSource().runDistanceManagerUpdates();
                        for (int z = 1000; z < 1009; z++) for (int x = 1000; x < 1009; x++) {
                            ChunkHolder holder = level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(ChunkPos.asLong(x, z));
                            if (holder == null) throw new AssertionError("pending-shutdown holder missing");
                            // The public cache request blocks when called on its
                            // main thread. Schedule the genuine holder request
                            // directly so shutdown can begin with work pending.
                            futures.add(holder.scheduleChunkGenerationTask(
                                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, level.getChunkSource().chunkMap));
                        }
                        long pending = futures.stream().filter(future -> !future.isDone()).count();
                        check(checks, "shutdown starts with real FULL requests pending", pending > 0);
                        check(checks, "shutdown starts with real chunk-map work", level.getChunkSource().chunkMap.hasWork());
                        result.put("shutdownPendingRequests", pending);
                        // Halt while this main-thread action still owns the queue.
                        // Ordinary stopServer drains/deactivates/saves the work.
                        server.halt(false);
                    });
                }
            }
            result.put("status", "passed");
        } catch (Throwable error) {
            error.printStackTrace();
            result.put("status", "failed");
            result.put("error", error.toString());
        } finally {
            for (Player player : List.copyOf(players)) {
                try { action(server, () -> remove(server, player)); }
                catch (Exception error) { result.put("status", "failed"); result.put("cleanupError", error.toString()); }
            }
            result.put("checks", checks);
            result.put("coverage", coverage());
            try { Files.writeString(output.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
            catch (Exception error) { error.printStackTrace(); }
            server.halt(false);
        }
    }
}
