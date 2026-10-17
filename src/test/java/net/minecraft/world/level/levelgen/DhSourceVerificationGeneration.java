package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.dataObjects.transformers.LodDataBuilder;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.ticks.ProtoChunkTicks;

/**
 * "generation" mode of {@link DhSourceVerification}: the per-chunk DH
 * generation work for recorded chunks loaded as from disk (wrappers built with
 * {@code recreateHeightmaps=false}, or {@code true} with
 * {@code -Ddh.verify.recreate=true}), through the production entry points:
 * {@code bakeChunkBlockLighting}, then {@code LodDataBuilder.createFromChunk}.
 * The checksum covers every produced source hash, so builds that produce
 * different LOD data fail the runner's checksum comparison.
 */
final class DhSourceVerificationGeneration {
    private DhSourceVerificationGeneration() {}

    private static Object allocate(Class<?> target) throws Exception {
        Class<?> type = Class.forName("sun.misc.Unsafe"); var field = type.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.getMethod("allocateInstance", Class.class).invoke(field.get(null), target);
    }

    static void run(int rounds, int warmup) throws Exception {
        ILevelWrapper level; PalettedContainerFactory factory; List<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> pattern;
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
                List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()))) {
            var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
            var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
            var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
            var worldgen = net.minecraft.resources.RegistryDataLoader.load(resources, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
            var access = layers.replaceFrom(net.minecraft.server.RegistryLayer.WORLDGEN, worldgen).compositeAccess();
            var biomes = access.lookupOrThrow(Registries.BIOME); factory = PalettedContainerFactory.create(access);
            pattern = List.of(biomes.getOrThrow(Biomes.PLAINS), biomes.getOrThrow(Biomes.OCEAN), biomes.getOrThrow(Biomes.DESERT), biomes.getOrThrow(Biomes.SAVANNA));
            var world = (ServerLevel) allocate(ServerLevel.class);
            var registry = Level.class.getDeclaredField("registryAccess"); registry.setAccessible(true); registry.set(world, access);
            level = (ServerLevelWrapper) allocate(ServerLevelWrapper.class);
            var wrapped = ServerLevelWrapper.class.getDeclaredField("level"); wrapped.setAccessible(true); wrapped.set(level, world);
        }
        // Every recorded chunk, each with a varied biome pattern.
        var chunks = new ArrayList<ProtoChunk>();
        var saved = HeightmapFixtures.saved();
        for (int index = 0; index < saved.size(); index++) {
            var source = HeightmapFixtures.recorded(saved.get(index));
            var sections = new LevelChunkSection[source.getSections().length];
            for (int s = 0; s < sections.length; s++) {
                var data = factory.createForBiomes();
                for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++)
                    data.getAndSetUnchecked(x, y, z, pattern.get((x + 3 * z + y + s) % 4));
                sections[s] = new LevelChunkSection(source.getSections()[s].getStates(), data);
            }
            var chunk = new ProtoChunk(new ChunkPos(-37 + index, 41 - index), UpgradeData.EMPTY, sections, new ProtoChunkTicks<>(), new ProtoChunkTicks<>(),
                LevelHeightAccessor.create(source.getMinY(), source.getHeight()), factory, null);
            chunk.setPersistedStatus(ChunkStatus.BIOMES);
            chunks.add(chunk);
        }
        for (int i = 0; i < 5; i++) round(level, chunks, warmup);
        long start = System.nanoTime(); long checksum = round(level, chunks, rounds); long elapsed = System.nanoTime() - start;
        System.out.println("DH_SOURCE " + elapsed + " " + checksum);
    }

    /** Fresh-chunk wrappers ({@code recreateHeightmaps=true}) instead of saved ones. */
    private static final boolean RECREATE = Boolean.getBoolean("dh.verify.recreate");
    private static long round(ILevelWrapper level, List<ProtoChunk> chunks, int rounds) {
        long checksum = 0;
        var nearby = new ArrayList<IChunkWrapper>(1);
        for (int r = 0; r < rounds; r++) {
            for (var chunk : chunks) {
                var wrapper = new ChunkWrapper(chunk, level, RECREATE);
                nearby.clear(); nearby.add(wrapper);
                DhLightingEngine.INSTANCE.bakeChunkBlockLighting(wrapper, nearby, 15);
                try (var source = LodDataBuilder.createFromChunk(level, wrapper)) {
                    checksum = checksum * 31 + (source == null ? 0 : source.hashCode());
                }
            }
        }
        return checksum;
    }
}
