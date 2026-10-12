package net.minecraft.world.level.lighting;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.material.FluidState;

/** The same observer runs actual Frozen and Current engines; no expected light algorithm. */
public final class VerifyLightWork {
    private static final String[] TYPES = {"LightEngine", "BlockLightEngine", "SkyLightEngine", "LayerLightSectionStorage"};
    private static final String[] FROZEN = {
        "b6be7a9e3e43c4185ae76e3e390acb649acbf2ff7e0b793d3acb5c9592f7c453",
        "2b672b2b513751d8f9fcb89b319270e6d1a3171e5fc0d58d28786628f3ced727",
        "97e441d316271e8cbc125481e86187d970cf29058e7738a56e7fd5e786f8eeec",
        "31076b253b190711c0516653cb0a85718a7b96ea1af0883ed3e837720c051539"};
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static String layer(DataLayer layer) throws Exception {
        if (layer == null) return "null";
        return layer.isDefinitelyHomogenous() ? "lazy:" + layer.get(0, 0, 0) : "bytes:" + hash(layer.copy().getData());
    }
    @SuppressWarnings("unchecked")
    private static Map<Long, DataLayer> layers(DataLayerStorageMap<?> map) throws Exception {
        try {
            Method method = DataLayerStorageMap.class.getDeclaredMethod("snapshotForVerification");
            method.setAccessible(true);
            return (Map<Long, DataLayer>) method.invoke(map);
        } catch (NoSuchMethodException frozen) { return map.map; }
    }
    private static void map(PrintWriter out, String name, Map<Long, DataLayer> map) throws Exception {
        for (var e : new TreeMap<>(map).entrySet()) out.println(name + " " + e.getKey() + " " + layer(e.getValue()));
    }
    private static final class World implements LightChunkGetter, BlockGetter {
        final Map<Long, ProtoChunk> chunks = new TreeMap<>();
        final Set<String> notifications = new TreeSet<>();
        World(long seed) {
            var random = new Random(seed);
            for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
                var sections = new LevelChunkSection[8];
                for (int i = 0; i < 8; i++) sections[i] = new LevelChunkSection(new PalettedContainer<>(
                    Blocks.AIR.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)), null);
                var chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, sections,
                    new net.minecraft.world.ticks.ProtoChunkTicks<>(), new net.minecraft.world.ticks.ProtoChunkTicks<>(),
                    LevelHeightAccessor.create(0, 128), null, null);
                for (int i = 0; i < 160; i++) {
                    int bx = random.nextInt(16), bz = random.nextInt(16), y = 16 + random.nextInt(96);
                    var block = switch (i % 5) { case 0 -> Blocks.GLOWSTONE; case 1 -> Blocks.GLASS;
                        case 2 -> Blocks.OAK_SLAB; case 3 -> Blocks.OAK_STAIRS; default -> Blocks.STONE; };
                    sections[y >> 4].setBlockState(bx, y & 15, bz, block.defaultBlockState(), false);
                }
                chunks.put(chunk.getPos().toLong(), chunk);
                chunk.initializeLightSources();
            }
        }
        public LightChunk getChunkForLighting(int x, int z) { return chunks.get(ChunkPos.asLong(x, z)); }
        public BlockGetter getLevel() { return this; }
        public void onLightUpdate(LightLayer layer, SectionPos pos) { notifications.add(layer + " " + pos.asLong()); }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public BlockState getBlockState(BlockPos pos) {
            var chunk = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            return chunk == null ? Blocks.BEDROCK.defaultBlockState() : chunk.getBlockState(pos);
        }
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public int getMinY() { return 0; }
        public int getHeight() { return 128; }
    }
    private static final class Observer {
        final IdentityHashMap<DataLayer, String> published = new IdentityHashMap<>();
        int passes;
        void pass(PrintWriter out, LevelLightEngine engine, World world) throws Exception {
            out.println("pass " + passes++ + " work " + engine.runLightUpdates() + " pending " + engine.hasLightWork());
            for (var e : published.entrySet()) {
                if (!layer(e.getKey()).equals(e.getValue())) throw new AssertionError("Published layer mutated");
            }
            published.clear();
            for (var kind : LightLayer.values()) {
                if (!(engine.getLayerListener(kind) instanceof LightEngine<?, ?> light)) continue;
                var storage = light.storage;
                var updating = layers(storage.updatingSectionData);
                var visible = layers(storage.visibleSectionData);
                map(out, kind + " updating", updating); map(out, kind + " visible", visible);
                map(out, kind + " queued", storage.queuedSections);
                out.println(kind + " states " + new TreeMap<>(storage.sectionStates));
                out.println(kind + " changed " + new TreeSet<>(storage.changedSections));
                out.println(kind + " affected " + new TreeSet<>(storage.sectionsAffectedByLightUpdates));
                for (long key : new TreeSet<>(updating.keySet()))
                    out.println(kind + " sharing " + key + " " + (updating.get(key) == visible.get(key)));
                for (DataLayer data : visible.values()) if (data != null) published.put(data, layer(data));
            }
            for (var update : world.notifications) out.println("notification " + update);
            world.notifications.clear();
        }
    }
    private static void edit(World world, LevelLightEngine engine, BlockPos pos, BlockState value) {
        var chunk = world.chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        var section = chunk.getSection(pos.getY() >> 4);
        boolean wasEmpty = section.hasOnlyAir();
        var old = section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, value, false);
        if (wasEmpty != section.hasOnlyAir()) engine.updateSectionStatus(pos, section.hasOnlyAir());
        if (LightEngine.hasDifferentLightProperties(old, value)) {
            chunk.getSkyLightSources().update(chunk, pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            engine.checkBlock(pos);
        }
    }
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < TYPES.length; i++) {
            Class<?> type = Class.forName("net.minecraft.world.level.lighting." + TYPES[i]);
            String digest;
            try (var in = type.getResourceAsStream(TYPES[i] + ".class")) { digest = hash(in.readAllBytes()); }
            if (args[1].equals("frozen") && !digest.equals(FROZEN[i])) throw new AssertionError("Wrong Frozen " + TYPES[i]);
            System.out.println("LIGHT_CLASS " + TYPES[i] + " " + digest);
        }
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        java.util.concurrent.atomic.AtomicLong nativePasses = null, rejected = null;
        if (args[1].equals("current")) {
            Class<?> nativeType = Class.forName("net.minecraft.world.level.lighting.NativeLightPropagation");
            nativePasses = (java.util.concurrent.atomic.AtomicLong) nativeType.getDeclaredField("PASSES").get(null);
            rejected = (java.util.concurrent.atomic.AtomicLong) nativeType.getDeclaredField("REJECTED").get(null);
        }
        int passes = 0;
        try (var out = new PrintWriter(Files.newBufferedWriter(Path.of(args[0])))) {
            for (boolean sky : new boolean[]{false, true}) for (long seed : new long[]{13, -42, 71}) {
                out.println("scenario " + sky + " " + seed);
                World world = new World(seed);
                var engine = new LevelLightEngine(world, true, sky);
                var observer = new Observer();
                for (var chunk : world.chunks.values()) {
                    for (int i = 0; i < 8; i++) if (!chunk.getSection(i).hasOnlyAir())
                        engine.updateSectionStatus(SectionPos.of(chunk.getPos(), i), false);
                    observer.pass(out, engine, world);
                    engine.setLightEnabled(chunk.getPos(), true);
                    engine.propagateLightSources(chunk.getPos());
                    observer.pass(out, engine, world);
                }
                var random = new Random(seed);
                for (int step = 0; step < 64; step++) {
                    var pos = new BlockPos(random.nextInt(48) - 16, 16 + random.nextInt(96), random.nextInt(48) - 16);
                    edit(world, engine, pos, (step % 3 == 0 ? Blocks.GLOWSTONE : step % 3 == 1 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                    if (step % 8 == 0) {
                        var section = SectionPos.of(pos); var bytes = new byte[2048]; random.nextBytes(bytes);
                        engine.queueSectionData(LightLayer.BLOCK, section, new DataLayer(bytes));
                        engine.retainData(new ChunkPos(pos), true);
                    }
                    if (step % 11 == 0) engine.setLightEnabled(new ChunkPos(pos), false);
                    if (step % 11 == 1) engine.setLightEnabled(new ChunkPos(pos), true);
                    observer.pass(out, engine, world);
                }
                passes += observer.passes;
            }
        }
        System.out.println("LIGHT_WORK_PASSES " + passes);
        if (nativePasses != null) {
            if (nativePasses.get() == 0 || rejected.get() != 0) throw new AssertionError("Native route not accepted");
            System.out.println("LIGHT_NATIVE_PASSES " + nativePasses.get());
        }
    }
}
