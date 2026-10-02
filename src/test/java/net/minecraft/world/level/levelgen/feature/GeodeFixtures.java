package net.minecraft.world.level.levelgen.feature;

import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

final class GeodeFixtures {
    record Config(String name, GeodeConfiguration value) {}
    record Grid(NormalNoise noise, BlockPos origin, int min, int max,
        List<Pair<BlockPos,Integer>> points, List<BlockPos> cracks, int crackOffset, double multiplier) {}
    static List<Config> configs() {
        var result = new ArrayList<Config>();
        try (var paths = Files.walk(Path.of("src/main/resources/data/minecraft/worldgen/configured_feature"))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (json.get("type").getAsString().equals("minecraft:geode"))
                    result.add(new Config(path.getFileName().toString().replace(".json", ""),
                        GeodeConfiguration.CODEC.parse(JsonOps.INSTANCE, json.get("config")).getOrThrow()));
            }
        } catch (Exception e) { throw new IllegalStateException(e); }
        if (result.size() != 15) throw new AssertionError("Review the registered geode fixture coverage: " + result.size());
        return result;
    }
    static NormalNoise noise(long seed) {
        return NormalNoise.create(new WorldgenRandom(new LegacyRandomSource(seed)), -4, 1.0);
    }
    // Same prelude draws and point construction as place(); world validation is
    // separate from this numeric operation and covered by full placement tests.
    static Grid grid(GeodeConfiguration c, long worldSeed, RandomSource random, BlockPos origin, Boolean crackOverride) {
        int k = c.distributionPoints.sample(random);
        random.nextDouble();
        boolean cracked = random.nextFloat() < c.geodeCrackSettings.generateCrackChance;
        if (crackOverride != null) cracked = crackOverride;
        List<Pair<BlockPos,Integer>> points = new LinkedList<>();
        List<BlockPos> cracks = new LinkedList<>();
        for (int n=0;n<k;n++) {
            int x=c.outerWallDistance.sample(random),y=c.outerWallDistance.sample(random),z=c.outerWallDistance.sample(random);
            points.add(Pair.of(origin.offset(x,y,z),c.pointOffset.sample(random)));
        }
        if (cracked) {
            int n=random.nextInt(4),o=k*2+1;
            int x=n==0||n==2?o:0,z=n==1||n==2?o:0;
            cracks.add(origin.offset(x,7,z));cracks.add(origin.offset(x,5,z));cracks.add(origin.offset(x,1,z));
        }
        return new Grid(noise(worldSeed),origin,c.minGenOffset,c.maxGenOffset,points,cracks,
            c.geodeCrackSettings.crackPointOffset,c.noiseMultiplier);
    }
    static GeodeConfiguration bounds(GeodeConfiguration c, int min, int max) {
        return new GeodeConfiguration(c.geodeBlockSettings,c.geodeLayerSettings,c.geodeCrackSettings,
            c.usePotentialPlacementsChance,c.useAlternateLayer0Chance,c.placementsRequireLayer0Alternate,
            c.outerWallDistance,c.distributionPoints,c.pointOffset,min,max,c.noiseMultiplier,c.invalidBlocksThreshold);
    }
}
