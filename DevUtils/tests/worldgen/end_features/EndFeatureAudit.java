package endfeatures;

import com.google.gson.GsonBuilder;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.*;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Compiled independently against unchanged Frozen and Current APIs. All
 * generation writes use real WorldGenRegion/ProtoChunk methods and real server
 * services. No source substitution or independent Java geometry implementation. */
public final class EndFeatureAudit {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final Path OUTPUT = Path.of(System.getProperty("audit.output"));
    private static final boolean NATIVE = Boolean.getBoolean("end.native");
    private static final boolean PROFILE = Boolean.getBoolean("end.profileBoundary");
    private static final Set<String> WORKLOADS = Set.of(System.getProperty("end.workloads", "").split(","));
    private static final String[] PROFILE_NAMES = {"refresh", "afterPlacement", "upcall", "copyWritableState"};
    private static final long[] PROFILE_NS = new long[4], PROFILE_CALLS = new long[4];
    private static volatile boolean profileActive;
    private static Thread profileThread;
    private static final List<Map<String,Object>> RESULTS = new ArrayList<>();

    static final java.util.concurrent.atomic.AtomicLongArray NATIVE_ENTRIES = new java.util.concurrent.atomic.AtomicLongArray(3);
    public static void nativeEntry(int kind) { NATIVE_ENTRIES.incrementAndGet(kind); }
    public static void profileDuration(int kind, long started) {
        if (profileActive && Thread.currentThread() == profileThread) {
            PROFILE_NS[kind] += System.nanoTime() - started;
            PROFILE_CALLS[kind]++;
        }
    }
    static boolean selected(String name) { return WORKLOADS.contains("") || WORKLOADS.contains(name); }
    static List<Long> nativeEntries() { return List.of(NATIVE_ENTRIES.get(0), NATIVE_ENTRIES.get(1), NATIVE_ENTRIES.get(2)); }
    static Tape tape;
    static final class Tape {
        final List<String> writes = new ArrayList<>();
        final RuntimeException failure = new IllegalStateException("End audit effect sentinel");
        boolean failAfterEntity, entityAdded;
        List<SpikeFeature.EndSpike> mutate;
        Runnable reenter;
        Runnable onWrite;
        BlockPos lastWrite;
        boolean retainBodyCursor;
        BlockPos bodyCursor;
        boolean retainGatewayCursor;
        BlockPos gatewayCursor;
        String gatewayCursorAtCenter;
        int writeCount, failAtWrite;
        boolean moveWriteCursor;
    }
    public static void write(Object value, Object state, int flags) {
        if (tape == null) return;
        BlockPos pos = (BlockPos)value;
        if (tape.retainBodyCursor && tape.bodyCursor == null
            && ((net.minecraft.world.level.block.state.BlockState)state).is(Blocks.OBSIDIAN)) tape.bodyCursor = pos;
        if (tape.retainGatewayCursor) {
            if (tape.gatewayCursor == null && pos instanceof BlockPos.MutableBlockPos) tape.gatewayCursor = pos;
            if (((net.minecraft.world.level.block.state.BlockState)state).is(Blocks.END_GATEWAY))
                tape.gatewayCursorAtCenter = tape.gatewayCursor.toShortString();
        }
        tape.writes.add(pos.immutable() + ":" + state + ":" + flags + ":" + pos.getClass().getSimpleName());
        if (tape.failAfterEntity && tape.entityAdded) throw tape.failure;
        tape.lastWrite = pos.immutable();
        if (++tape.writeCount == tape.failAtWrite) throw tape.failure;
        if (tape.writeCount == 7 && tape.onWrite != null) {
            var action = tape.onWrite; tape.onWrite = null;
            if (tape.moveWriteCursor && pos instanceof BlockPos.MutableBlockPos cursor) cursor.move(0, 1, 0);
            action.run();
        }
    }
    public static void entity(Object value) {
        if (tape == null) return;
        var entity = (net.minecraft.world.entity.Entity)value;
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity item)
            tape.writes.add("drop:" + entity.getX() + ":" + entity.getY() + ":" + entity.getZ() + ":" + item.getItem());
        else tape.writes.add("entity:" + entity.getX() + ":" + entity.getY() + ":" + entity.getZ() + ":" + entity.getYRot());
        tape.entityAdded = true;
        if (tape.mutate != null) { tape.mutate.add(new SpikeFeature.EndSpike(9, 9, 1, 85, false)); tape.mutate = null; }
        if (tape.reenter != null) { var nested = tape.reenter; tape.reenter = null; nested.run(); }
    }

    static boolean preload;
    static int reopenTicks;
    public static void capture(Object value) {
        if (Boolean.getBoolean("end.reopen")) {
            MinecraftServer server = (MinecraftServer)value;
            if (!preload) {
                preload = true;
                for (int x : new int[]{8192, 8256, 8320, 8448}) {
                    var end = server.getLevel(Level.END);
                    end.setChunkForced(x >> 4, 8192 >> 4, true);
                    end.getChunk(x >> 4, 8192 >> 4);
                    end.waitForEntities(new ChunkPos(x >> 4, 8192 >> 4), 0);
                }
            }
            // Let the real persistent entity manager finish its chunk loads.
            if (++reopenTicks < 40) return;
        }
        if (STARTED.compareAndSet(false, true)) {
            // All fixture effects run on the server thread; no tick can race them.
            MinecraftServer server = (MinecraftServer)value;
            server.execute(() -> run(server));
        }
    }

    static final class HolderStub extends GenerationChunkHolder {
        final ProtoChunk chunk;
        HolderStub(ProtoChunk chunk) { super(chunk.getPos()); this.chunk = chunk; }
        @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) { return chunk; }
        @Override protected void addSaveDependency(CompletableFuture<?> dependency) {}
        @Override public int getTicketLevel() { return 0; }
        @Override public int getQueueLevel() { return 0; }
    }

    static final class Fixture implements AutoCloseable {
        final ProtoChunk[] chunks = new ProtoChunk[9];
        final WorldGenRegion region;
        final ChunkGenerator generator;
        Object binding;
        java.lang.reflect.Method nativePlace;
        final IdentityHashMap<ConfiguredFeature<?, ?>, PlacedFeature> placements = new IdentityHashMap<>();
        final ServerLevel server;
        Fixture(ServerLevel server) { this(server, new ChunkPos(0, 0)); }
        Fixture(ServerLevel server, ChunkPos center) {
            this.server = server;
            generator = server.getChunkSource().getGenerator();
            var factory = PalettedContainerFactory.create(server.registryAccess());
            var cache = StaticCache2D.<GenerationChunkHolder>create(center.x, center.z, 1, (x, z) -> {
                var chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, server, factory, null);
                chunk.setPersistedStatus(ChunkStatus.CARVERS);
                chunks[(z - center.z + 1) * 3 + x - center.x + 1] = chunk;
                return new HolderStub(chunk);
            });
            region = new WorldGenRegion(server, cache, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), chunks[4]);
        }
        boolean place(ConfiguredFeature<?, ?> feature, BlockPos origin, WorldgenRandom random, Object programs) throws Exception {
            var placed = placements.computeIfAbsent(feature, key -> new PlacedFeature(Holder.direct(key), List.of()));
            if (!NATIVE) return placed.place(region, generator, random, origin);
            Class<?> api;
            if (binding == null) {
                api = Class.forName("net.minecraft.world.level.levelgen.NativeDecoration");
                binding = api.getMethod("bind", WorldGenLevel.class, ChunkAccess.class, ChunkGenerator.class).invoke(null, region, chunks[4], generator);
                if (binding != null) nativePlace = api.getMethod("placeWithRandom", programs.getClass(), binding.getClass(), ChunkGenerator.class,
                    WorldGenLevel.class, WorldgenRandom.class, PlacedFeature.class, BlockPos.class);
            }
            if (binding == null) throw new AssertionError("native binding declined");
            Object result = nativePlace.invoke(null, programs, binding, generator, region, random, placed, origin);
            if (result == null) throw new AssertionError("native feature declined " + feature);
            return (boolean)result;
        }
        Map<String,Object> snapshot() throws Exception {
            List<String> blocks = new ArrayList<>(), maps = new ArrayList<>(), entities = new ArrayList<>(), blockEntities = new ArrayList<>();
            int nonAir = 0;
            for (var chunk : chunks) {
                for (var section : chunk.getSections()) {
                    var packed = section.getStates().pack(Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
                    blocks.add(packed.paletteEntries() + " " + packed.storage().map(s -> Arrays.toString(s.toArray())).orElse("-") + " " + packed.bitsPerEntry());
                    for (int i = 0; i < 4096; i++) if (!section.getBlockState(i & 15, i >> 8, (i >> 4) & 15).isAir()) nonAir++;
                }
                for (var type : Heightmap.Types.values()) maps.add(type + ":" + (chunk.hasPrimedHeightmap(type)
                    ? Arrays.toString(chunk.getOrCreateHeightmapUnprimed(type).getRawData()) : "-"));
                maps.add("marks:" + Arrays.deepToString(chunk.getPostProcessing()));
                for (var pos : chunk.getBlockEntitiesPos().stream().sorted().toList())
                    blockEntities.add(pos + ":" + canonical(chunk.getBlockEntityNbtForSaving(pos, server.registryAccess())));
                for (var tag : chunk.getEntities()) {
                    var copy = tag.copy(); copy.remove("UUID");
                    entities.add(canonical(copy));
                }
            }
            return Map.of("blocks", digest(blocks.toString()), "metadata", digest(maps.toString()), "nonAir", nonAir,
                "blockEntities", blockEntities, "entities", entities);
        }
        public void close() throws Exception {
            if (binding != null) Class.forName("net.minecraft.world.level.levelgen.NativeDecoration")
                .getMethod("release", binding.getClass()).invoke(null, binding);
        }
    }

    static String canonical(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            var out = new TreeMap<String,String>();
            for (var key : compound.keySet()) out.put(key, canonical(compound.get(key)));
            return out.toString();
        }
        if (tag instanceof ListTag list) {
            var out = new ArrayList<String>(); for (var child : list) out.add(canonical(child)); return out.toString();
        }
        return String.valueOf(tag);
    }
    static String digest(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    static WorldgenRandom random() { return new WorldgenRandom(new XoroshiroRandomSource(42)); }
    static Object programs() throws Exception {
        return NATIVE ? Class.forName("net.minecraft.world.level.levelgen.NativeDecoration$Programs").getConstructor().newInstance() : null;
    }
    static void fixture(ServerLevel level, String name, ConfiguredFeature<?, ?> feature, BlockPos origin) throws Exception {
        try (var f = new Fixture(level, new ChunkPos(origin))) {
            var random = random(); var row = new LinkedHashMap<String,Object>();
            row.put("name", name);
            Tape observed = new Tape();
            observed.failAfterEntity = name.equals("spike-fail-after-yaw");
            if (name.equals("spike-iterator-failure")) observed.mutate = ((SpikeConfiguration)feature.config()).getSpikes();
            if (name.equals("spike-reentry")) observed.reenter = () -> {
                try {
                    var nested = new ConfiguredFeature<>(Feature.END_SPIKE, new SpikeConfiguration(false,
                        List.of(new SpikeFeature.EndSpike(9, 9, 1, 78, false)), (BlockPos)null));
                    if (!f.place(nested, origin, random, programs())) throw new AssertionError("nested spike declined");
                } catch (Exception failure) { throw new AssertionError("nested spike failed", failure); }
            };
            tape = observed;
            try { row.put("placed", f.place(feature, origin, random, programs())); }
            catch (Exception error) {
                Throwable cause = error instanceof InvocationTargetException ? error.getCause() : error;
                row.put("exception", cause.getClass().getName() + ":" + cause.getMessage());
                if (observed.failAfterEntity && cause != observed.failure) throw new AssertionError("effect identity changed", cause);
                if (name.equals("spike-iterator-failure") && !(cause instanceof ConcurrentModificationException)) throw error;
                if (name.equals("spike-null-descriptor") && !(cause instanceof NullPointerException)) throw error;
                if (!name.equals("spike-iterator-failure") && !name.equals("spike-null-descriptor") && !observed.failAfterEntity) throw error;
            } finally { tape = null; }
            if ((observed.failAfterEntity || name.equals("spike-iterator-failure") || name.equals("spike-null-descriptor")) && !row.containsKey("exception"))
                throw new AssertionError("expected End effect/iterator failure did not occur: " + name);
            row.put("state", f.snapshot()); row.put("nextRandom", random.nextLong());
            row.put("writes", observed.writes);
            RESULTS.add(row);
            System.out.println("END_FIXTURE " + name + " " + row.get("state"));
        }
    }
    static final BlockPos LIVE_SPIKE = new BlockPos(8200, 80, 8200);
    static final BlockPos LIVE_GATEWAY = new BlockPos(8264, 95, 8200);
    static final BlockPos LIVE_PLATFORM = new BlockPos(8328, 80, 8200);
    static final BlockPos LIVE_NESTED = new BlockPos(8456, 80, 8200);

    static Map<String,Object> liveSnapshot(ServerLevel level, BlockPos center, boolean reopen) throws Exception {
        List<String> blocks = new ArrayList<>(), entities = new ArrayList<>();
        for (int z = center.getZ() - 5; z <= center.getZ() + 5; z++) {
            for (int y = level.getMinY(); y <= 105; y++) {
                for (int x = center.getX() - 5; x <= center.getX() + 5; x++) {
                    var state = level.getBlockState(new BlockPos(x, y, z));
                    if (reopen && state.is(Blocks.FIRE)) state = Blocks.AIR.defaultBlockState();
                    blocks.add(state.toString());
                }
            }
        }
        var bounds = new net.minecraft.world.phys.AABB(center.getX() - 6, 0, center.getZ() - 6, center.getX() + 6, 110, center.getZ() + 6);
        for (var crystal : level.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class, bounds)) {
            entities.add("crystal:" + crystal.getX() + ":" + crystal.getY() + ":" + crystal.getZ() + ":" + crystal.getYRot()
                + ":" + crystal.getBeamTarget() + ":" + crystal.isInvulnerable());
        }
        if (!reopen) for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds)) {
            // Drop velocity/yaw comes from each entity's independent unseeded
            // random. Compare actual retained item contents/count, not UUIDs.
            entities.add("drop:" + item.getItem());
        }
        entities.sort(null);
        var be = level.getBlockEntity(center);
        var blockEntityTag = be == null ? null : be.saveWithFullMetadata(level.registryAccess());
        // Reopening deliberately runs 40 real ticks to finish entity loading.
        // Gateway age advances with those ticks; its exit/exact flag must persist.
        if (reopen && blockEntityTag != null && be instanceof net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity)
            blockEntityTag.remove("Age");
        String blockEntity = blockEntityTag == null ? "" : canonical(blockEntityTag);
        return Map.of("blocks", digest(blocks.toString()), "entities", entities, "blockEntity", blockEntity);
    }
    static void clearLive(ServerLevel level, BlockPos center) {
        level.setChunkForced(center.getX() >> 4, center.getZ() >> 4, true);
        level.getChunk(center.getX() >> 4, center.getZ() >> 4);
        level.waitForEntities(new ChunkPos(center), 0);
        var bounds = new net.minecraft.world.phys.AABB(center.getX() - 6, 0, center.getZ() - 6, center.getX() + 6, 110, center.getZ() + 6);
        for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, bounds)) entity.discard();
        for (int z = center.getZ() - 5; z <= center.getZ() + 5; z++)
            for (int y = level.getMinY(); y <= 105; y++)
                for (int x = center.getX() - 5; x <= center.getX() + 5; x++)
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        level.getRandom().setSeed(0x55aa7);
    }
    static void live(MinecraftServer server, ServerLevel level) throws Exception {
        var reopen = new TreeMap<String,Object>();
        if (Boolean.getBoolean("end.reopen")) {
            for (var pos : List.of(LIVE_SPIKE, LIVE_GATEWAY, LIVE_PLATFORM, LIVE_NESTED)) reopen.put(pos.toShortString(), liveSnapshot(level, pos, true));
            Files.writeString(OUTPUT.resolve("reopened.json"), new GsonBuilder().setPrettyPrinting().create().toJson(reopen));
            return;
        }
        var generator = level.getChunkSource().getGenerator();
        for (var pos : List.of(LIVE_SPIKE, LIVE_GATEWAY, LIVE_PLATFORM, LIVE_NESTED)) clearLive(level, pos);
        var failed = new Tape(); failed.failAtWrite = 7;
        var failedRandom = random();
        tape = failed;
        try {
            Feature.END_SPIKE.place(new SpikeConfiguration(false,
                List.of(new SpikeFeature.EndSpike(LIVE_SPIKE.getX(), LIVE_SPIKE.getZ(), 2, 80, true)), (BlockPos)null),
                level, generator, failedRandom, LIVE_SPIKE);
            throw new AssertionError("missing live write failure");
        } catch (RuntimeException failure) {
            if (failure != failed.failure) throw new AssertionError("live failure identity changed", failure);
        } finally { tape = null; }
        RESULTS.add(Map.of("name", "live-failure-prefix", "state", liveSnapshot(level, LIVE_SPIKE, false),
            "nextRandom", failedRandom.nextLong(), "writes", failed.writes));
        clearLive(level, LIVE_SPIKE);
        var shortSpike = new Tape(); shortSpike.retainBodyCursor = true;
        var shortRandom = random();
        tape = shortSpike;
        try {
            Feature.END_SPIKE.place(new SpikeConfiguration(false,
                List.of(new SpikeFeature.EndSpike(LIVE_SPIKE.getX(), LIVE_SPIKE.getZ(), 2, 10, true)), (BlockPos)null),
                level, generator, shortRandom, LIVE_SPIKE);
        } finally { tape = null; }
        if (!(shortSpike.bodyCursor instanceof BlockPos.MutableBlockPos)) throw new AssertionError("no retained live body cursor");
        RESULTS.add(Map.of("name", "live-short-spike-cursor", "state", liveSnapshot(level, LIVE_SPIKE, false),
            "nextRandom", shortRandom.nextLong(), "writes", shortSpike.writes,
            "retainedBodyCursor", shortSpike.bodyCursor.toShortString()));
        clearLive(level, LIVE_SPIKE);
        var random = random();
        Tape observed = new Tape(); observed.moveWriteCursor = true;
        observed.onWrite = () -> {
            level.setBlock(observed.lastWrite.offset(1, 0, 0), Blocks.CHEST.defaultBlockState(), 3);
            EndPlatformFeature.createEndPlatform(level, LIVE_NESTED, false);
        };
        tape = observed;
        try {
            Feature.END_SPIKE.place(new SpikeConfiguration(true,
                List.of(new SpikeFeature.EndSpike(LIVE_SPIKE.getX(), LIVE_SPIKE.getZ(), 2, 80, true)), new BlockPos(0, 128, 0)),
                level, generator, random, LIVE_SPIKE);
            observed.retainGatewayCursor = true;
            Feature.END_GATEWAY.place(EndGatewayConfiguration.knownExit(LIVE_SPIKE, false), level, generator, random, LIVE_GATEWAY);
        } finally { tape = null; }
        if (observed.onWrite != null) throw new AssertionError("live write callback did not run");
        level.setBlock(LIVE_PLATFORM, Blocks.CHEST.defaultBlockState(), 3);
        if (!(level.getBlockEntity(LIVE_PLATFORM) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest))
            throw new AssertionError("missing real inventory fixture");
        chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE, 32));
        tape = observed;
        try { EndPlatformFeature.createEndPlatform(level, LIVE_PLATFORM, true); }
        finally { tape = null; }
        var row = new LinkedHashMap<String,Object>(); row.put("name", "live-effects");
        var states = new ArrayList<Object>();
        for (var pos : List.of(LIVE_SPIKE, LIVE_GATEWAY, LIVE_PLATFORM, LIVE_NESTED)) {
            states.add(liveSnapshot(level, pos, false)); reopen.put(pos.toShortString(), liveSnapshot(level, pos, true));
        }
        if (observed.gatewayCursorAtCenter == null) throw new AssertionError("no retained gateway cursor");
        row.put("retainedGatewayAtCenter", observed.gatewayCursorAtCenter);
        row.put("state", states); row.put("nextRandom", random.nextLong()); row.put("writes", observed.writes); RESULTS.add(row);
        Files.writeString(OUTPUT.resolve("saved-live.json"), new GsonBuilder().setPrettyPrinting().create().toJson(reopen));
        if (!server.saveEverything(true, true, true)) throw new AssertionError("server save did not succeed");
    }

    interface Operation { void run() throws Exception; }
    static void transactions(ServerLevel level) throws Exception {
        Class<?> type;
        try { type = Class.forName("net.minecraft.world.level.chunk.DecorationTransaction"); }
        catch (ClassNotFoundException originalJava) { return; }
        var begin = type.getMethod("begin", List.class, ChunkAccess.class, net.minecraft.core.HolderLookup.Provider.class);
        var rows = new ArrayList<Object>();
        for (boolean nativeFailure : NATIVE ? new boolean[]{false, true} : new boolean[]{false}) {
            try (var f = new Fixture(level)) {
                var before = f.snapshot();
                var feature = new ConfiguredFeature<>(Feature.END_SPIKE, new SpikeConfiguration(false,
                    List.of(new SpikeFeature.EndSpike(0, 0, 2, 80, true)), (BlockPos)null));
                var random = random();
                var observed = new Tape(); observed.failAfterEntity = !nativeFailure;
                var transaction = begin.invoke(null, Arrays.asList(f.chunks), f.chunks[4], level.registryAccess());
                Throwable failure = null;
                try {
                    if (nativeFailure) Class.forName("net.minecraft.world.level.levelgen.NativeDecoration").getMethod("armTestFailure", String.class).invoke(null, "fail:50");
                    tape = observed;
                    f.place(feature, BlockPos.ZERO, random, programs());
                } catch (Exception thrown) {
                    failure = thrown instanceof InvocationTargetException wrapped ? wrapped.getCause() : thrown;
                } finally {
                    tape = null;
                    if (nativeFailure) Class.forName("net.minecraft.world.level.levelgen.NativeDecoration").getMethod("armTestFailure", String.class).invoke(null, "fail:-1");
                    type.getMethod("rollback").invoke(transaction);
                }
                if (failure == null || (!nativeFailure && failure != observed.failure)) throw new AssertionError("missing original End failure", failure);
                if (!before.equals(f.snapshot())) throw new AssertionError("End rollback changed captured storage/entities/metadata");
                // A new FEATURES attempt obtains a new binding after rollback.
                f.close(); f.binding = null;
                transaction = begin.invoke(null, Arrays.asList(f.chunks), f.chunks[4], level.registryAccess());
                try { if (!f.place(feature, BlockPos.ZERO, random, programs())) throw new AssertionError("End retry declined"); }
                catch (Exception error) { type.getMethod("rollback").invoke(transaction); throw error; }
                type.getMethod("commit").invoke(transaction);
                var after = f.snapshot();
                if (((Number)after.get("nonAir")).intValue() == 0 || ((List<?>)after.get("entities")).size() != 1)
                    throw new AssertionError("End retry did not publish blocks and crystal");
                rows.add(Map.of("nativeWriteFailure", nativeFailure, "rolledBack", true, "retryCommitted", true,
                    "failure", failure.getClass().getName(), "nextRandom", random.nextLong(), "state", after));
            }
        }
        Files.writeString(OUTPUT.resolve("transactions.json"), new GsonBuilder().setPrettyPrinting().create().toJson(rows));
    }
    static void resetFeatureBlocks(ServerLevelAccessor level, BlockPos origin, int kind) {
        if (!Boolean.getBoolean("end.freshWrites") || kind == 2) return;
        int radius = kind == 0 ? 2 : 1;
        int bottom = kind == 0 ? level.getMinY() : origin.getY() - 2;
        int top = kind == 0 ? 90 : origin.getY() + 2;
        for (var pos : BlockPos.betweenClosed(new BlockPos(origin.getX() - radius, bottom, origin.getZ() - radius),
                new BlockPos(origin.getX() + radius, top, origin.getZ() + radius)))
            level.setBlock(pos, Blocks.END_STONE.defaultBlockState(), 3);
    }
    static void benchmark(MinecraftServer server, ServerLevel level) throws Exception {
        if (PROFILE) profileThread = Thread.currentThread();
        int warmup = Integer.getInteger("end.warmup", 30), samples = Integer.getInteger("end.samples", 15), iterations = Integer.getInteger("end.iterations", 30);
        var rows = new ArrayList<Map<String,Object>>();
        var features = List.<ConfiguredFeature<?, ?>>of(
            new ConfiguredFeature<>(Feature.END_SPIKE, new SpikeConfiguration(true, List.of(new SpikeFeature.EndSpike(8, 8, 2, 80, true)), new BlockPos(0, 128, 0))),
            new ConfiguredFeature<>(Feature.END_GATEWAY, EndGatewayConfiguration.knownExit(BlockPos.ZERO, true)),
            new ConfiguredFeature<>(Feature.END_PLATFORM, NoneFeatureConfiguration.INSTANCE));
        for (int family = 0; family < 3; family++) {
            if (!selected("worldgen-" + family)) continue;
            final int kind = family;
            var origin = kind == 0 ? BlockPos.ZERO : new BlockPos(8, 80, 8);
            try (var fixture = new Fixture(level)) {
                var random = random(); var programs = programs(); var feature = features.get(kind);
                Operation operation = () -> { if (!fixture.place(feature, origin, random, programs)) throw new AssertionError("feature returned false"); };
                Operation reset = () -> {
                    for (var chunk : fixture.chunks) chunk.getEntities().clear();
                    resetFeatureBlocks(fixture.region, kind == 0 ? new BlockPos(8, 80, 8) : origin, kind);
                    if (kind == 2) for (int z = -2; z <= 2; z++) for (int x = -2; x <= 2; x++) for (int y = -1; y < 3; y++)
                        fixture.region.setBlock(origin.offset(x, y, z), Blocks.END_STONE.defaultBlockState(), 3);
                };
                rows.add(measure("worldgen-" + kind, operation, reset, random, warmup, samples, iterations));
                rows.getLast().put("state", fixture.snapshot()); rows.getLast().put("nextRandom", random.nextLong());
            }
        }
        var generator = level.getChunkSource().getGenerator();
        for (int family = 0; family < 4; family++) {
            if (!selected("live-" + family)) continue;
            final int kind = family == 3 ? 2 : family;
            final boolean destroy = family == 2;
            var origin = List.of(LIVE_SPIKE, LIVE_GATEWAY, LIVE_PLATFORM).get(kind);
            clearLive(level, origin);
            var feature = kind == 0 ? new ConfiguredFeature<>(Feature.END_SPIKE,
                new SpikeConfiguration(true, List.of(new SpikeFeature.EndSpike(origin.getX(), origin.getZ(), 2, 80, true)), new BlockPos(0, 128, 0))) : features.get(kind);
            var random = random();
            Operation operation = () -> {
                if (kind == 2) EndPlatformFeature.createEndPlatform(level, origin, destroy);
                else if (!feature.place(level, generator, random, origin)) throw new AssertionError("live feature returned false");
            };
            Operation reset = () -> {
                var bounds = new net.minecraft.world.phys.AABB(origin.getX() - 6, 0, origin.getZ() - 6, origin.getX() + 6, 110, origin.getZ() + 6);
                for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, bounds)) entity.discard();
                resetFeatureBlocks(level, origin, kind);
                if (kind == 2) for (int z = -2; z <= 2; z++) for (int x = -2; x <= 2; x++) for (int y = -1; y < 3; y++)
                    level.setBlock(origin.offset(x, y, z), Blocks.END_STONE.defaultBlockState(), 3);
            };
            rows.add(measure("live-" + family, operation, reset, random, warmup, samples, iterations));
            rows.getLast().put("state", liveSnapshot(level, origin, false)); rows.getLast().put("nextRandom", random.nextLong());
        }
        var gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(OUTPUT.resolve("timings.json"), gson.toJson(rows));
        Files.writeString(OUTPUT.resolve("complete.json"), gson.toJson(Map.of("success", true, "timing", true, "nativeEntries", nativeEntries())));
    }
    static Map<String,Object> measure(String name, Operation operation, Operation reset, WorldgenRandom random, int warmup, int samples, int iterations) throws Exception {
        reset.run(); long cold = System.nanoTime(); operation.run(); cold = System.nanoTime() - cold;
        var compiler = java.lang.management.ManagementFactory.getCompilationMXBean();
        if (compiler == null || !compiler.isCompilationTimeMonitoringSupported()) throw new AssertionError("compiler timing is unavailable");
        long warmStart = System.nanoTime(), lastCompilation = warmStart, compilationTime = compiler.getTotalCompilationTime();
        long minimum = (long)(Double.parseDouble(System.getProperty("end.warmupSeconds", "3")) * 1e9);
        long quiet = Long.getLong("end.compilerQuietMs", 500L) * 1_000_000;
        int warmed = 0;
        do {
            reset.run();
            if (PROFILE) profileActive = true;
            try { operation.run(); } finally { if (PROFILE) profileActive = false; }
            warmed++;
            long currentCompilation = compiler.getTotalCompilationTime();
            if (currentCompilation != compilationTime) { compilationTime = currentCompilation; lastCompilation = System.nanoTime(); }
            if (System.nanoTime() - warmStart > 60_000_000_000L) throw new AssertionError("compiler did not settle for " + name);
        } while (warmed < warmup || System.nanoTime() - warmStart < minimum || System.nanoTime() - lastCompilation < quiet);
        double warmElapsed = (System.nanoTime() - warmStart) / 1e9;
        var values = new ArrayList<Long>();
        var attempts = new ArrayList<Object>();
        long[] acceptedNs = new long[4], acceptedCalls = new long[4];
        for (int sample = 0; values.size() < samples && sample < samples * 4; sample++) {
            // Variable warmup and discarded attempts must not change the
            // measured caller RNG sequence or final state between sides.
            random.setSeed(42);
            long compilationBefore = compiler.getTotalCompilationTime();
            long gcBefore = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum();
            long elapsed = 0;
            if (PROFILE) { Arrays.fill(PROFILE_NS, 0); Arrays.fill(PROFILE_CALLS, 0); }
            for (int iteration = 0; iteration < iterations; iteration++) {
                reset.run();
                if (PROFILE) profileActive = true;
                long start = System.nanoTime();
                try { operation.run(); elapsed += System.nanoTime() - start; }
                finally { if (PROFILE) profileActive = false; }
            }
            long compilationAfter = compiler.getTotalCompilationTime();
            long gcAfter = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum();
            boolean accepted = compilationBefore == compilationAfter;
            attempts.add(Map.of("ns", elapsed / iterations, "compilerBeforeMs", compilationBefore, "compilerAfterMs", compilationAfter,
                "gcCollections", gcAfter - gcBefore, "accepted", accepted));
            if (accepted) {
                values.add(elapsed / iterations);
                if (PROFILE) for (int i = 0; i < 4; i++) { acceptedNs[i] += PROFILE_NS[i]; acceptedCalls[i] += PROFILE_CALLS[i]; }
            }
        }
        if (values.size() != samples) throw new AssertionError("insufficient compiler-quiet samples for " + name);
        var row = new LinkedHashMap<String,Object>(); row.put("name", name); row.put("cold_ns", cold); row.put("samples_ns", values);
        row.put("warmup", warmup); row.put("iterations_per_sample", iterations);
        row.put("warmup_completed", warmed); row.put("warmup_seconds", warmElapsed);
        row.put("attempts", attempts); row.put("compilerQuietMs", quiet / 1_000_000);
        if (PROFILE) {
            var profile = new LinkedHashMap<String,Object>();
            for (int i = 0; i < 4; i++) profile.put(PROFILE_NAMES[i], Map.of("ns_per_operation", acceptedNs[i] / (double)(samples * iterations),
                "calls_per_operation", acceptedCalls[i] / (double)(samples * iterations)));
            row.put("boundary_profile_inclusive", profile);
        }
        System.out.println("END_TIMING " + name + " " + values);
        return row;
    }

    static void run(MinecraftServer server) {
        try {
            ServerLevel level = server.getLevel(Level.END);
            if (level == null) throw new AssertionError("missing End server level");
            if (Boolean.getBoolean("end.timing")) {
                benchmark(server, level);
                server.halt(false); return;
            }
            if (Boolean.getBoolean("end.reopen")) {
                live(server, level);
                Files.writeString(OUTPUT.resolve("regions.json"), "[]");
                Files.writeString(OUTPUT.resolve("complete.json"), "{\"success\":true,\"reopen\":true}");
                server.halt(false); return;
            }
            for (boolean guarded : new boolean[]{false, true}) {
                for (int radius : new int[]{0, 2, 5}) {
                    fixture(level, "spike-r" + radius + "-guarded" + guarded,
                        new ConfiguredFeature<>(Feature.END_SPIKE, new SpikeConfiguration(false,
                            List.of(new SpikeFeature.EndSpike(8, 8, radius, 80, guarded)), (BlockPos)null)), BlockPos.ZERO);
                }
                fixture(level, "spike-negative-radius-guarded" + guarded,
                    new ConfiguredFeature<>(Feature.END_SPIKE, new SpikeConfiguration(false,
                        List.of(new SpikeFeature.EndSpike(8, 8, -2, 80, guarded)), (BlockPos)null)), BlockPos.ZERO);
            }
            int defaultIndex = 0;
            for (var spike : SpikeFeature.getSpikesForLevel(level)) {
                var center = new ChunkPos(spike.getCenterX() >> 4, spike.getCenterZ() >> 4);
                fixture(level, "default-spike-" + defaultIndex++ + "-guarded" + spike.isGuarded(),
                    new ConfiguredFeature<>(Feature.END_SPIKE, new SpikeConfiguration(false, List.of(), (BlockPos)null)), center.getWorldPosition());
            }
            fixture(level, "spike-explicit-beam", new ConfiguredFeature<>(Feature.END_SPIKE,
                new SpikeConfiguration(true, List.of(new SpikeFeature.EndSpike(8, 8, 3, 100, true)), new BlockPos(0, 128, 0))), BlockPos.ZERO);
            fixture(level, "spike-no-matching-chunk", new ConfiguredFeature<>(Feature.END_SPIKE,
                new SpikeConfiguration(false, List.of(new SpikeFeature.EndSpike(42, 42, 3, 100, true)), (BlockPos)null)), BlockPos.ZERO);
            fixture(level, "spike-null-descriptor", new ConfiguredFeature<>(Feature.END_SPIKE,
                new SpikeConfiguration(false, Arrays.asList((SpikeFeature.EndSpike)null), (BlockPos)null)), BlockPos.ZERO);
            fixture(level, "gateway-known-exact", new ConfiguredFeature<>(Feature.END_GATEWAY,
                EndGatewayConfiguration.knownExit(new BlockPos(100, 50, -100), true)), new BlockPos(8, 70, 8));
            fixture(level, "gateway-known-inexact-edge", new ConfiguredFeature<>(Feature.END_GATEWAY,
                EndGatewayConfiguration.knownExit(new BlockPos(0, 50, 0), false)), new BlockPos(15, 70, 15));
            fixture(level, "gateway-delayed", new ConfiguredFeature<>(Feature.END_GATEWAY,
                EndGatewayConfiguration.delayedExitSearch()), new BlockPos(8, 70, 8));
            fixture(level, "platform-edge", new ConfiguredFeature<>(Feature.END_PLATFORM, NoneFeatureConfiguration.INSTANCE), new BlockPos(15, 70, 15));
            fixture(level, "spike-fail-after-yaw", new ConfiguredFeature<>(Feature.END_SPIKE,
                new SpikeConfiguration(false, List.of(new SpikeFeature.EndSpike(8, 8, 2, 80, true)), (BlockPos)null)), BlockPos.ZERO);
            fixture(level, "spike-iterator-failure", new ConfiguredFeature<>(Feature.END_SPIKE,
                new SpikeConfiguration(false, new ArrayList<>(List.of(new SpikeFeature.EndSpike(8, 8, 2, 80, true))), (BlockPos)null)), BlockPos.ZERO);
            fixture(level, "spike-reentry", new ConfiguredFeature<>(Feature.END_SPIKE,
                new SpikeConfiguration(false, List.of(new SpikeFeature.EndSpike(8, 8, 2, 80, true)), (BlockPos)null)), BlockPos.ZERO);
            transactions(level);
            live(server, level);
            var gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(OUTPUT.resolve("fixtures.json"), gson.toJson(RESULTS));
            // The shared process launcher returns this JSON payload. These are
            // controlled feature fixtures, not FULL-generation region results.
            Files.writeString(OUTPUT.resolve("regions.json"), gson.toJson(RESULTS));
            Files.writeString(OUTPUT.resolve("complete.json"), gson.toJson(Map.of("success", true, "fixtures", RESULTS.size(), "native", NATIVE, "nativeEntries", nativeEntries())));
            System.out.println("END_FEATURES_COMPLETE " + RESULTS.size());
        } catch (Throwable error) {
            if (error instanceof InvocationTargetException wrapped) error = wrapped.getCause();
            error.printStackTrace();
            try { Files.writeString(OUTPUT.resolve("failed.txt"), error.toString()); } catch (Exception ignored) {}
        }
        server.halt(false);
    }
}
