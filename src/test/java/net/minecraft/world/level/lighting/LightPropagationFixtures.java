package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/** Real terrain, scripted light work and storage comparison for the light
 * propagation parity tests and benchmark. Engines share one world's chunks. */
final class LightPropagationFixtures {
    private LightPropagationFixtures() {}

    static final BlockState[] PALETTE = {
        Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), Blocks.CAVE_AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(),
        Blocks.STONE.defaultBlockState(), Blocks.GLASS.defaultBlockState(), Blocks.TORCH.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState(),
        Blocks.LAVA.defaultBlockState(), Blocks.WATER.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(), Blocks.ICE.defaultBlockState(),
        Blocks.OAK_SLAB.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
        Blocks.OAK_STAIRS.defaultBlockState(), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP),
        Blocks.SNOW.defaultBlockState(), Blocks.DIRT_PATH.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.REDSTONE_TORCH.defaultBlockState(),
        Blocks.COBWEB.defaultBlockState(), Blocks.DAYLIGHT_DETECTOR.defaultBlockState(), Blocks.SOUL_TORCH.defaultBlockState(), Blocks.BEDROCK.defaultBlockState(),
        Blocks.SCULK_SENSOR.defaultBlockState(), Blocks.FARMLAND.defaultBlockState(), Blocks.HONEY_BLOCK.defaultBlockState(), Blocks.TINTED_GLASS.defaultBlockState()
    };

    /** The saved FULL overworld chunks of {@code lighting/terrain.json.gz}, at their own positions. */
    static List<ProtoChunk> corpus() {
        try (var stream = new java.util.zip.GZIPInputStream(java.util.Objects.requireNonNull(
            LightPropagationFixtures.class.getResourceAsStream("/lighting/terrain.json.gz")))) {
            var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var chunks = new ArrayList<ProtoChunk>();
            for (var item : root.getAsJsonArray("chunks")) {
                var record = item.getAsJsonObject();
                var pos = record.getAsJsonArray("chunk");
                var chunk = SkyLightSourcesFixtures.recorded(record);
                chunks.add(new ProtoChunk(new ChunkPos(pos.get(0).getAsInt(), pos.get(1).getAsInt()), net.minecraft.world.level.chunk.UpgradeData.EMPTY,
                    chunk.getSections(), new net.minecraft.world.ticks.ProtoChunkTicks<>(), new net.minecraft.world.ticks.ProtoChunkTicks<>(),
                    net.minecraft.world.level.LevelHeightAccessor.create(chunk.getMinY(), chunk.getHeight()), null, null));
            }
            return chunks;
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    /** Chunks shared by every engine; {@code wrapped} positions are served
     * through a {@link LightChunk} the Rust bridge does not model. */
    static final class World {
        final Map<Long, ProtoChunk> chunks = new HashMap<>();
        final Set<Long> wrapped = new HashSet<>();
        final int minY, height;
        final BlockGetter level;

        World(List<ProtoChunk> chunks) {
            for (ProtoChunk chunk : chunks) this.chunks.put(chunk.getPos().toLong(), chunk);
            this.minY = chunks.getFirst().getMinY();
            this.height = chunks.getFirst().getHeight();
            this.level = new Delegate(null, this);
        }

        @Nullable
        ProtoChunk chunk(int x, int z) {
            return this.chunks.get(ChunkPos.asLong(x, z));
        }
    }

    /** A light chunk outside the modelled classes, or the world view itself. */
    private record Delegate(@Nullable ProtoChunk chunk, World world) implements LightChunk {
        @Override
        public void findBlockLightSources(BiConsumer<BlockPos, BlockState> consumer) {
            this.chunk.findBlockLightSources(consumer);
        }

        @Override
        public ChunkSkyLightSources getSkyLightSources() {
            return this.chunk.getSkyLightSources();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            ProtoChunk owner = this.chunk != null ? this.chunk : this.world.chunk(pos.getX() >> 4, pos.getZ() >> 4);
            return owner == null ? Blocks.AIR.defaultBlockState() : owner.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return this.world.height;
        }

        @Override
        public int getMinY() {
            return this.world.minY;
        }
    }

    /** One engine's view: its update notifications, in arrival order. */
    static final class Getter implements LightChunkGetter {
        final World world;
        final LongArrayList blockUpdates = new LongArrayList(), skyUpdates = new LongArrayList();

        Getter(World world) {
            this.world = world;
        }

        @Nullable
        @Override
        public LightChunk getChunkForLighting(int x, int z) {
            ProtoChunk chunk = this.world.chunk(x, z);
            if (chunk == null) return null;
            return this.world.wrapped.contains(ChunkPos.asLong(x, z)) ? new Delegate(chunk, this.world) : chunk;
        }

        @Override
        public BlockGetter getLevel() {
            return this.world.level;
        }

        @Override
        public void onLightUpdate(LightLayer layer, SectionPos pos) {
            (layer == LightLayer.SKY ? this.skyUpdates : this.blockUpdates).add(pos.asLong());
        }
    }

    /** An engine and whether its passes use Rust. */
    record Side(LevelLightEngine engine, Getter getter, boolean rust) {
        static Side of(World world, boolean sky, boolean rust) {
            Getter getter = new Getter(world);
            return new Side(new LevelLightEngine(getter, true, sky), getter, rust);
        }

        int run() {
            // Layers published before the pass must never change during it (copy-on-write).
            var published = new java.util.IdentityHashMap<DataLayer, Object>();
            for (LightLayer layer : LightLayer.values()) {
                if (this.engine.getLayerListener(layer) instanceof LightEngine<?, ?> light) {
                    for (DataLayer data : light.storage.visibleSectionData.map.values()) published.put(data, describe(data));
                }
            }
            NativeLightPropagation.setEnabled(this.rust);
            try {
                return this.engine.runLightUpdates();
            } finally {
                NativeLightPropagation.setEnabled(true);
                for (var entry : published.entrySet()) {
                    if (!describe(entry.getKey()).equals(entry.getValue())) throw new AssertionError("A published layer changed during a pass");
                }
            }
        }
    }

    /** ThreadedLevelLightEngine.initializeLight's section statuses. */
    static void initialize(LevelLightEngine engine, ProtoChunk chunk) {
        var sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            if (!sections[i].hasOnlyAir()) engine.updateSectionStatus(SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(i)), false);
        }
    }

    static void enable(LevelLightEngine engine, ProtoChunk chunk) {
        engine.setLightEnabled(chunk.getPos(), true);
        engine.retainData(chunk.getPos(), false);
    }

    /** ProtoChunk.setBlockState's light notifications, for every engine. */
    static void set(World world, List<LevelLightEngine> engines, BlockPos pos, BlockState state) {
        ProtoChunk chunk = world.chunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || chunk.isOutsideBuildHeight(pos.getY())) return;
        var section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        boolean before = section.hasOnlyAir();
        if (before && state.isAir()) return;
        BlockState old = section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state, false);
        boolean after = section.hasOnlyAir();
        if (after != before) for (var engine : engines) engine.updateSectionStatus(pos, after);
        if (LightEngine.hasDifferentLightProperties(old, state)) {
            chunk.getSkyLightSources().update(chunk, pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            for (var engine : engines) engine.checkBlock(pos);
        }
    }

    /** Lights every chunk in a shuffled order and batches, comparing after each pass. */
    static void lightAll(World world, List<Side> sides, long seed, @Nullable Runnable afterPass) {
        var random = new Random(seed);
        var order = new ArrayList<>(world.chunks.values());
        order.sort((a, b) -> Long.compare(a.getPos().toLong(), b.getPos().toLong()));
        java.util.Collections.shuffle(order, random);
        for (int at = 0; at < order.size(); ) {
            int batch = 1 + random.nextInt(4);
            var chunks = order.subList(at, Math.min(order.size(), at + batch));
            at += chunks.size();
            for (var side : sides) {
                for (var chunk : chunks) initialize(side.engine(), chunk);
            }
            pass(sides, afterPass);
            for (var side : sides) {
                for (var chunk : chunks) enable(side.engine(), chunk);
                NativeLightPropagation.setEnabled(side.rust());
                try {
                    for (var chunk : chunks) side.engine().propagateLightSources(chunk.getPos());
                } finally {
                    NativeLightPropagation.setEnabled(true);
                }
            }
            pass(sides, afterPass);
        }
    }

    /** One runLightUpdates() per engine; every engine must report the same work. */
    static void pass(List<Side> sides, @Nullable Runnable afterPass) {
        int expected = sides.getFirst().run();
        for (int i = 1; i < sides.size(); i++) {
            int work = sides.get(i).run();
            if (work != expected) throw new AssertionError("runLightUpdates returned " + work + " and " + expected);
        }
        if (afterPass != null) afterPass.run();
    }

    /** Scripted edits: single blocks near the surface and in caves, shafts,
     * roofs, cleared sections and queued section data. */
    static void edit(World world, List<LevelLightEngine> engines, Random random, int minX, int minZ, int span) {
        var pos = new BlockPos.MutableBlockPos();
        int kind = random.nextInt(12);
        int x = minX + random.nextInt(span), z = minZ + random.nextInt(span);
        ProtoChunk chunk = world.chunk(x >> 4, z >> 4);
        if (chunk == null) return;
        int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15);
        switch (kind) {
            case 0, 1, 2, 3 -> {
                for (int i = 0; i < 24; i++) {
                    int y = surface - 10 + random.nextInt(16);
                    set(world, engines, pos.set(x + random.nextInt(9) - 4, y, z + random.nextInt(9) - 4), PALETTE[random.nextInt(PALETTE.length)]);
                }
            }
            case 4 -> {
                for (int i = 0; i < 16; i++) {
                    int y = world.minY + random.nextInt(world.height);
                    set(world, engines, pos.set(x + random.nextInt(5) - 2, y, z + random.nextInt(5) - 2), PALETTE[random.nextInt(PALETTE.length)]);
                }
            }
            case 5 -> {
                // A shaft: sky light reaches deep, then a cap removes it again.
                int bottom = Math.max(world.minY, surface - 40 - random.nextInt(40));
                for (int y = surface + 1; y >= bottom; y--) set(world, engines, pos.set(x, y, z), Blocks.AIR.defaultBlockState());
                if (random.nextBoolean()) set(world, engines, pos.set(x, surface + 1, z), Blocks.STONE.defaultBlockState());
            }
            case 6 -> {
                BlockState roof = random.nextBoolean() ? Blocks.STONE.defaultBlockState() : Blocks.GLASS.defaultBlockState();
                int y = surface + 3 + random.nextInt(4);
                for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) set(world, engines, pos.set(x + dx, y, z + dz), roof);
            }
            case 7 -> {
                // Empty a whole section, crossing the section-status and empty-section paths.
                int sy = (surface >> 4) - random.nextInt(3);
                for (int dy = 0; dy < 16; dy++) for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
                    set(world, engines, pos.set((x & ~15) + dx, sy * 16 + dy, (z & ~15) + dz), Blocks.AIR.defaultBlockState());
                }
            }
            case 8 -> {
                // Loaded data for one section, as chunk loading queues it.
                int sy = (surface >> 4) + random.nextInt(3) - 1;
                byte[] bytes = new byte[2048];
                random.nextBytes(bytes);
                DataLayer layer = random.nextBoolean() ? new DataLayer(bytes) : new DataLayer(random.nextInt(16));
                LightLayer target = random.nextBoolean() ? LightLayer.BLOCK : LightLayer.SKY;
                for (var engine : engines) {
                    engine.queueSectionData(target, SectionPos.of(x >> 4, sy, z >> 4), layer.copy());
                    engine.retainData(new ChunkPos(x >> 4, z >> 4), true);
                }
            }
            case 10 -> {
                // Light off then on for one chunk: emitters there stop counting until re-enabled.
                var column = new ChunkPos(x >> 4, z >> 4);
                for (var engine : engines) engine.setLightEnabled(column, false);
                set(world, engines, pos.set(x, surface + 1, z), Blocks.AIR.defaultBlockState());
                set(world, engines, pos.set(x + 1, surface, z), Blocks.STONE.defaultBlockState());
                if (random.nextBoolean()) {
                    for (var engine : engines) engine.setLightEnabled(column, true);
                }
            }
            case 11 -> {
                // A floating block on a section's bottom face over empty sections, beside a pillar
                // across the section border: skylight crosses into sections below empty ones.
                int top = world.minY + world.height - 1 - random.nextInt(48);
                int y = top & ~15;
                int edge = (x & ~15) + (random.nextBoolean() ? 0 : 15);
                set(world, engines, pos.set(edge, y, z), Blocks.STONE.defaultBlockState());
                int across = edge + ((edge & 15) == 0 ? -1 : 1);
                for (int dy = y + 2; dy >= Math.max(world.minY, surface - 2); dy -= random.nextInt(3) + 1) {
                    set(world, engines, pos.set(across, dy, z), Blocks.GLASS.defaultBlockState());
                }
                set(world, engines, pos.set(edge, y, z), Blocks.AIR.defaultBlockState());
            }
            default -> {
                for (int i = 0; i < 6; i++) {
                    set(world, engines, pos.set(x + random.nextInt(3) - 1, surface + random.nextInt(3), z + random.nextInt(3) - 1),
                        random.nextBoolean() ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** A layer's observable state: absent, lazy with its default, or its bytes.
     * Compared by value; byte content uses ByteBuffer equality instead of a
     * multi-kilobyte string per section and pass. */
    static Object describe(@Nullable DataLayer layer) {
        if (layer == null) return "null";
        if (layer.isDefinitelyHomogenous()) return "lazy" + layer.get(0, 0, 0);
        return java.nio.ByteBuffer.wrap(layer.copy().getData());
    }

    /** Differences between two engines' complete light storage, or empty. */
    static List<String> differences(LevelLightEngine a, LevelLightEngine b) {
        var out = new ArrayList<String>();
        for (LightLayer layer : LightLayer.values()) {
            if (!(a.getLayerListener(layer) instanceof LightEngine<?, ?> x) || !(b.getLayerListener(layer) instanceof LightEngine<?, ?> y)) continue;
            compareStorage(layer, x.storage, y.storage, out);
            if (x.hasLightWork() != y.hasLightWork()) out.add(layer + " hasLightWork");
        }
        return out;
    }

    private static void compareStorage(LightLayer layer, LayerLightSectionStorage<?> a, LayerLightSectionStorage<?> b, List<String> out) {
        compareMap(layer + " updating", a.updatingSectionData.map, b.updatingSectionData.map, out);
        compareMap(layer + " visible", a.visibleSectionData.map, b.visibleSectionData.map, out);
        for (long key : a.updatingSectionData.map.keySet()) {
            boolean sharedA = a.updatingSectionData.map.get(key) == a.visibleSectionData.map.get(key);
            boolean sharedB = b.updatingSectionData.map.get(key) == b.visibleSectionData.map.get(key);
            if (sharedA != sharedB) out.add(layer + " sharing " + SectionPos.of(key));
        }
        compareMap(layer + " queued", a.queuedSections, b.queuedSections, out);
        if (!a.sectionStates.equals(b.sectionStates)) out.add(layer + " section states");
        if (!a.changedSections.equals(b.changedSections)) out.add(layer + " changed sections");
        if (!a.sectionsAffectedByLightUpdates.equals(b.sectionsAffectedByLightUpdates)) out.add(layer + " affected sections");
        if (a instanceof SkyLightSectionStorage sa && b instanceof SkyLightSectionStorage sb) {
            if (!sa.updatingSectionData.topSections.equals(sb.updatingSectionData.topSections)
                || sa.updatingSectionData.currentLowestY != sb.updatingSectionData.currentLowestY) out.add(layer + " top sections");
        }
    }

    private static void compareMap(String what, Map<Long, DataLayer> a, Map<Long, DataLayer> b, List<String> out) {
        if (!a.keySet().equals(b.keySet())) {
            out.add(what + " keys " + a.size() + " vs " + b.size());
            return;
        }
        for (var entry : a.entrySet()) {
            if (!describe(entry.getValue()).equals(describe(b.get(entry.getKey())))) out.add(what + " " + SectionPos.of(entry.getKey()));
        }
    }

    /** Light values over every stored section, for checksums. */
    static long checksum(LevelLightEngine engine) {
        long sum = 0;
        for (LightLayer layer : LightLayer.values()) {
            if (!(engine.getLayerListener(layer) instanceof LightEngine<?, ?> light)) continue;
            var keys = new ArrayList<>(light.storage.visibleSectionData.map.keySet());
            keys.sort(Long::compare);
            for (long key : keys) sum = sum * 31 + describe(light.storage.visibleSectionData.map.get(key)).hashCode();
        }
        return sum;
    }
}
