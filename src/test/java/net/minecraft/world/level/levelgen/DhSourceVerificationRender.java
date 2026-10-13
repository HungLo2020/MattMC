package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateColorCache;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBufferBuilder;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer;
import com.seibel.distanthorizons.core.dataObjects.transformers.LodDataBuilder;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.ticks.ProtoChunkTicks;

/**
 * "render" mode of {@link DhSourceVerification}: the production DH render
 * build for real recorded terrain. A 3 x 3 grid of block-detail sections (and
 * their two coarser parent levels) is converted to render sources, built with
 * its four neighbors and encoded into packets, as LodRenderSection does. The
 * checksum covers every packet byte and semantic identity, so builds that
 * render differently fail the runner's comparison.
 */
final class DhSourceVerificationRender {
    private DhSourceVerificationRender() {}
    private static final float[] SHADES = {.5f, 1f, .8f, .8f, .6f, .6f};

    private static Object allocate(Class<?> target) throws Exception {
        Class<?> type = Class.forName("sun.misc.Unsafe"); var field = type.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.getMethod("allocateInstance", Class.class).invoke(field.get(null), target);
    }
    private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }

    /** Recorded terrain as full-data sources, with the client color fixture and a DH level. */
    record Fixture(List<FullDataSourceV2> sources, Map<Long, FullDataSourceV2> all, IClientLevelWrapper client, IDhClientLevel level) {
        long round(int rounds) { return DhSourceVerificationRender.round(sources, all, client, level, rounds); }
        void warmTints(String mode) { DhSourceVerificationRender.warmTints(all.values(), client, mode); }
    }

    static void run(int rounds, int warmup) throws Exception {
        configure(Integer.getInteger("dh.verify.renderConfig", 0));
        com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.lodBiomeBlending.setWithoutFiringEvents(
            Integer.getInteger("dh.verify.blend", com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.lodBiomeBlending.get()));
        var fixture = fixture();
        fixture.warmTints(System.getProperty("dh.verify.tint", "all"));
        for (int i = 0; i < 5; i++) fixture.round(warmup);
        var threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long allocated = threads.getCurrentThreadAllocatedBytes();
        long start = System.nanoTime(); long checksum = fixture.round(rounds); long elapsed = System.nanoTime() - start;
        allocated = threads.getCurrentThreadAllocatedBytes() - allocated;
        System.out.println("DH_SOURCE " + elapsed + " " + checksum);
        // Java heap allocated per measured round (native storage is not counted).
        System.out.println("DH_ALLOCATED_PER_ROUND " + allocated / rounds);
    }

    /**
     * The conversion's native tint route against Java's per-point colors in one
     * JVM, with warm caches (misses would switch states to broken between runs).
     */
    static void tintCheck() throws Exception {
        var fixture = fixture();
        fixture.warmTints("all");
        var toggle = Class.forName("com.seibel.distanthorizons.core.dataObjects.transformers.RenderColumnConversionContext").getDeclaredField("nativeTint");
        toggle.setAccessible(true);
        var blending = com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.lodBiomeBlending;
        for (int radius : new int[]{0, 1, 3}) {
            blending.setWithoutFiringEvents(radius);
            toggle.setBoolean(null, true); long nativeRoute = fixture.round(1);
            toggle.setBoolean(null, false); long javaRoute = fixture.round(1);
            toggle.setBoolean(null, true);
            if (nativeRoute != javaRoute) throw new AssertionError("Tint routes differ at blend radius " + radius);
        }
        System.out.println("TINT ROUTES MATCH");
    }

    static Fixture fixture() throws Exception {
        // Server side: recorded chunks become full-data sections.
        ILevelWrapper server; PalettedContainerFactory factory; List<Holder<net.minecraft.world.level.biome.Biome>> pattern;
        RegistryAccess access;
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
                List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()))) {
            var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
            var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
            var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
            var worldgen = net.minecraft.resources.RegistryDataLoader.load(resources, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
            access = layers.replaceFrom(net.minecraft.server.RegistryLayer.WORLDGEN, worldgen).compositeAccess();
            var biomes = access.lookupOrThrow(Registries.BIOME); factory = PalettedContainerFactory.create(access);
            pattern = List.of(biomes.getOrThrow(Biomes.PLAINS), biomes.getOrThrow(Biomes.OCEAN), biomes.getOrThrow(Biomes.DESERT), biomes.getOrThrow(Biomes.SAVANNA));
            var world = (ServerLevel) allocate(ServerLevel.class);
            set(Level.class, world, "registryAccess", access);
            server = (ServerLevelWrapper) allocate(ServerLevelWrapper.class);
            set(ServerLevelWrapper.class, server, "level", world);
        }
        var saved = HeightmapFixtures.saved();
        var sections = new TreeMap<Long, FullDataSourceV2>();
        for (int sx = 0; sx < 3; sx++) for (int sz = 0; sz < 3; sz++) {
            long pos = DhSectionPos.encode(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, sx, sz);
            var section = FullDataSourceV2.createEmpty(pos);
            for (int cx = 0; cx < 4; cx++) for (int cz = 0; cz < 4; cz++) {
                var record = saved.get((cx * 4 + cz + sx * 5 + sz * 7) % saved.size());
                var source = HeightmapFixtures.recorded(record);
                var chunkSections = new LevelChunkSection[source.getSections().length];
                for (int s = 0; s < chunkSections.length; s++) {
                    var data = factory.createForBiomes();
                    for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++)
                        data.getAndSetUnchecked(x, y, z, pattern.get((x + 3 * z + y + s + cx) % 4));
                    chunkSections[s] = new LevelChunkSection(source.getSections()[s].getStates(), data);
                }
                var chunk = new ProtoChunk(new ChunkPos(sx * 4 + cx, sz * 4 + cz), UpgradeData.EMPTY, chunkSections,
                    new ProtoChunkTicks<>(), new ProtoChunkTicks<>(), LevelHeightAccessor.create(source.getMinY(), source.getHeight()), factory, null);
                chunk.setPersistedStatus(ChunkStatus.BIOMES);
                var wrapper = new ChunkWrapper(chunk, server, true);
                var nearby = new ArrayList<IChunkWrapper>(List.of(wrapper));
                DhLightingEngine.INSTANCE.lightChunk(wrapper, nearby, 15);
                try (var chunkSource = LodDataBuilder.createFromChunk(server, wrapper)) {
                    section.updateFromDataSource(chunkSource);
                }
            }
            sections.put(pos, section);
        }
        // Two coarser levels exercise reduced columns and contributor footprints.
        var all = new TreeMap<Long, FullDataSourceV2>(sections);
        var level = sections;
        for (int step = 0; step < 2; step++) {
            var parents = new TreeMap<Long, FullDataSourceV2>();
            for (var child : level.values())
                parents.computeIfAbsent(DhSectionPos.getParentPos(child.getPos()), FullDataSourceV2::createEmpty).updateFromDataSource(child);
            all.putAll(parents);
            level = parents;
        }

        // Client side: every registered state has a resolved base color; tinted
        // vegetation and fluids take the contextual per-point color query.
        var client = clientLevel();
        var render = (IMinecraftRenderWrapper) Proxy.newProxyInstance(IMinecraftRenderWrapper.class.getClassLoader(),
            new Class<?>[]{IMinecraftRenderWrapper.class, IMinecraftRenderWrapper.NativeShadeFacts.class}, (p, m, a) -> switch (m.getName()) {
                case "hasNativeShadeFacts", "getDelayedSetupComplete" -> true;
                case "getNativeShadeFacts" -> 1;
                case "getShade" -> SHADES[((EDhDirection) a[0]).ordinal()];
                default -> m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
            });
        SingletonInjector.INSTANCE.bind(IMinecraftRenderWrapper.class, render);
        var dhLevel = (IDhClientLevel) Proxy.newProxyInstance(IDhClientLevel.class.getClassLoader(),
            new Class<?>[]{IDhClientLevel.class}, (p, m, a) -> switch (m.getName()) {
                case "getLevelWrapper", "getClientLevelWrapper" -> client;
                default -> m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
            });

        return new Fixture(new ArrayList<>(all.values()), all, client, dhLevel);
    }

    /**
     * Publishes biome tints for tinted blocks, as a running client's resolvers
     * would: "all" pairs (every point takes the warm route), "partial" (some
     * biomes miss and fall back to Minecraft's resolver, unavailable here, so
     * those states become broken) or "none".
     */
    private static void warmTints(Collection<FullDataSourceV2> sources, IClientLevelWrapper client, String mode) {
        if (mode.equals("none")) return;
        for (var source : sources) {
            var blocks = new LinkedHashSet<BlockStateWrapper>();
            var biomes = new LinkedHashSet<com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper>();
            for (int id = 0; id < source.mapping.size(); id++) {
                if (source.mapping.getBlockStateWrapper(id) instanceof BlockStateWrapper block && block.blockState != null) blocks.add(block);
                if (source.mapping.getBiomeWrapper(id) instanceof com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper biome) biomes.add(biome);
            }
            for (var block : blocks) for (var biome : biomes) {
                int key = Objects.hash(block.getSerialString(), biome.getSerialString());
                if (mode.equals("partial") && Math.floorMod(key, 7) == 0) continue;
                var tints = block.nativeTintCache(); var identity = biome.nativeTintIdentity();
                if (tints != null && identity != null) tints.put(identity, 0xff000000 | (key * 0x2545F491 >>> 8));
            }
        }
    }

    private static IClientLevelWrapper clientLevel() throws Exception {
        var world = (ClientLevel) allocate(ClientLevel.class);
        set(Level.class, world, "registryAccess", RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var dimension = new DimensionType(OptionalLong.empty(), true, false, false, true, 1.0, true, false,
            -64, 384, 384, BlockTags.INFINIBURN_OVERWORLD, BuiltinDimensionTypes.OVERWORLD_EFFECTS, 0,
            Optional.empty(), new DimensionType.MonsterSettings(false, true, ConstantInt.of(0), 0));
        set(Level.class, world, "dimensionTypeRegistration", Holder.direct(dimension));
        var constructor = ClientLevelWrapper.class.getDeclaredConstructor(ClientLevel.class);
        constructor.setAccessible(true);
        var wrapper = constructor.newInstance(world);
        var field = ClientLevelWrapper.class.getDeclaredField("blockColorCacheByBlockState"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var cache = (ConcurrentHashMap<BlockState, ClientBlockStateColorCache>) field.get(wrapper);
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            int id = Block.getId(state);
            int base = 0xff000000 | (id * 0x9E3779B1 >>> 8);
            if (!state.getFluidState().isEmpty()) base = (base & 0xffffff) | 0xb0000000;
            var colors = (ClientBlockStateColorCache) allocate(ClientBlockStateColorCache.class);
            set(ClientBlockStateColorCache.class, colors, "clientLevelWrapper", wrapper);
            set(ClientBlockStateColorCache.class, colors, "blockState", state);
            set(ClientBlockStateColorCache.class, colors, "blockStateWrapper", BlockStateWrapper.fromBlockState(state, wrapper));
            set(ClientBlockStateColorCache.class, colors, "baseColor", base);
            // Tinted states resolve per position; without a client their tint falls back to the base color.
            set(ClientBlockStateColorCache.class, colors, "needPostTinting", state.is(BlockTags.LEAVES) || !state.getFluidState().isEmpty()
                || state.getBlock() == net.minecraft.world.level.block.Blocks.GRASS_BLOCK || state.getBlock() == net.minecraft.world.level.block.Blocks.SHORT_GRASS);
            set(ClientBlockStateColorCache.class, colors, "isColorResolved", true);
            cache.put(state, colors);
        }
        return wrapper;
    }

    /** Setting presets: 0 defaults; 1 culling, fake ocean and color changes; 2 debug material colors, no transparency. */
    private static void configure(int preset) {
        if (preset == 1) {
            com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Culling.enableCaveCulling.setWithoutFiringEvents(true);
            com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Culling.caveCullingHeight.setWithoutFiringEvents(60);
            com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.transparency.setWithoutFiringEvents(com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency.FAKE);
            com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.tintWithAvoidedBlocks.setWithoutFiringEvents(false);
            com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.saturationMultiplier.setWithoutFiringEvents(1.3);
            com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.brightnessMultiplier.setWithoutFiringEvents(0.8);
        } else if (preset == 2) {
            com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.transparency.setWithoutFiringEvents(com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency.DISABLED);
            com.seibel.distanthorizons.core.config.Config.Client.Advanced.Debugging.debugRendering.setWithoutFiringEvents(com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering.SHOW_BLOCK_MATERIAL);
            com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.blocksToIgnore.setWithoutFiringEvents(com.seibel.distanthorizons.api.enums.config.EDhApiBlocksToAvoid.NONE);
        }
    }

    private static final EDhDirection[] COMPASS = {EDhDirection.NORTH, EDhDirection.SOUTH, EDhDirection.WEST, EDhDirection.EAST};

    private static long round(List<FullDataSourceV2> sources, Map<Long, FullDataSourceV2> all, IClientLevelWrapper client,
        IDhClientLevel dhLevel, int rounds) {
        long checksum = 0;
        for (int r = 0; r < rounds; r++) {
            for (var source : sources) {
                var center = FullDataToRenderDataTransformer.transformFullDataToRenderSource(source, client);
                var adjacent = new ColumnRenderSource[4];
                var same = new boolean[4];
                boolean transparency = com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled;
                try (var builder = new LodQuadBuilder(transparency, client)) {
                    for (var direction : COMPASS) {
                        var neighbor = all.get(DhSectionPos.getAdjacentPos(source.getPos(), direction));
                        adjacent[direction.compassIndex] = neighbor == null ? null : FullDataToRenderDataTransformer.transformFullDataToRenderSource(neighbor, client);
                        same[direction.compassIndex] = (direction.compassIndex + r) % 3 != 0;
                    }
                    ColumnRenderBufferBuilder.makeLodRenderData(builder, center, dhLevel, adjacent, same);
                    try (var column = builder.makeNativeRustSemanticColumn()) {
                        for (int layer = 0; layer < 4; layer++) {
                            var packets = column.project(layer);
                            for (byte[] bytes : packets.packedVertexBuffers()) checksum = checksum * 31 + Arrays.hashCode(bytes);
                            for (int[] ids : packets.semanticMaterialIds()) checksum = checksum * 31 + Arrays.hashCode(ids);
                        }
                    }
                } finally {
                    center.close();
                    for (var neighbor : adjacent) if (neighbor != null) neighbor.close();
                }
            }
        }
        return checksum;
    }
}
