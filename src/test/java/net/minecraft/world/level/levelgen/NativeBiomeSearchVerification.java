package net.minecraft.world.level.levelgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.PlayerChunkDistancesVerification;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSearchAccess;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;

/** Opt-in benchmark of Rust biome searches against Java's per-sample search
 * (-Dmattmc.worldgen.javaBiomeSearch=true). {@code rings}: an overworld's
 * stronghold ring positions through {@code ChunkGeneratorStructureState}
 * (128 {@code findBiomeHorizontal} searches on the background executor,
 * timed from creation until every position is known). {@code locate}: twelve
 * {@code findClosestBiome3d} spiral searches for rare biomes, as
 * {@code /locate biome} runs them (radius 6400, step 32, Ys every 64).
 * Registry and RandomState setup are untimed. */
public final class NativeBiomeSearchVerification {
    public static void main(String[] args) {
        String mode = args[0], name = args[1];
        boolean quick = args.length > 2 && args[2].equals("quick");
        if (!List.of("java", "native").contains(mode) || !List.of("rings", "locate").contains(name)) throw new IllegalArgumentException();
        NativeNoiseFillTest.load();
        try {
            NativeBiomeSearch.setEnabled(mode.equals("native"));
            long before = NativeBiomeSearch.SEARCHES.get();
            var world = NativeBiomeSearchTest.world(NoiseGeneratorSettings.OVERWORLD, MultiNoiseBiomeSourceParameterLists.OVERWORLD, 7_340_013L);
            var registries = NativeNoiseFillTest.registries;
            if (name.equals("rings")) {
                var sets = registries.lookupOrThrow(Registries.STRUCTURE_SET);
                PlayerChunkDistancesVerification.measure(mode, name, quick, () -> world, w -> {
                    var state = ChunkGeneratorStructureState.createForNormal(w.random(), 7_340_013L, w.source(), sets);
                    long checksum = 0;
                    for (var set : state.possibleStructureSets()) {
                        if (set.value().placement() instanceof ConcentricRingsStructurePlacement rings) {
                            for (ChunkPos pos : state.getRingPositionsFor(rings)) checksum = checksum * 31 + pos.toLong();
                        }
                    }
                    return checksum;
                });
            } else {
                var biomes = registries.lookupOrThrow(Registries.BIOME);
                var rare = new HashSet<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>(List.of(biomes.getOrThrow(Biomes.MUSHROOM_FIELDS), biomes.getOrThrow(Biomes.ICE_SPIKES),
                    biomes.getOrThrow(Biomes.BAMBOO_JUNGLE), biomes.getOrThrow(Biomes.CHERRY_GROVE)));
                var centers = new ArrayList<BlockPos>();
                var random = new java.util.Random(3);
                for (int i = 0; i < 12; i++) centers.add(new BlockPos(random.nextInt(20_000) - 10_000, 64, random.nextInt(20_000) - 10_000));
                int[] ys = Mth.outFromOrigin(64, -63, 321, 64).toArray();
                PlayerChunkDistancesVerification.measure(mode, name, quick, () -> world, w -> {
                    long checksum = 0;
                    for (BlockPos center : centers) {
                        var found = BiomeSearchAccess.closest(w.source(), center, 6400, 32, rare, ys, w.random().sampler());
                        checksum = checksum * 31 + (found == null ? -1 : found.getFirst().asLong());
                    }
                    return checksum;
                });
            }
            long searches = NativeBiomeSearch.SEARCHES.get() - before;
            if (mode.equals("java") != (searches == 0)) throw new IllegalStateException("Route not taken: " + searches);
            System.out.println("BIOME_SEARCH_ROUTE case=" + name + " mode=" + mode + " searches=" + searches);
        } finally {
            NativeBiomeSearch.setEnabled(true);
            NativeNoiseFillTest.close();
        }
    }
}
