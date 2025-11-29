package net.matt.quantize.worldgen;

import net.matt.quantize.modules.biomes.BiomeSampler;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Holder;
import net.matt.quantize.modules.biomes.ExpandedBiomes;
import net.minecraft.world.entity.Entity;

import java.util.List;

public class QBiomes {

    // Add biomes here
    public static final ResourceKey<Biome> PRIMORDIAL_CAVES = ResourceKey.create(Registries.BIOME, new ResourceIdentifier("primordial_caves"));
    public static final ResourceKey<Biome> SILVER_BIRCH_FOREST = ResourceKey.create(Registries.BIOME, new ResourceIdentifier("silver_birch_forest"));
    public static final ResourceKey<Biome> BAYOU = ResourceKey.create(Registries.BIOME, new ResourceIdentifier("bayou"));
    public static final ResourceKey<Biome> MOJAVE_DESERT = ResourceKey.create(Registries.BIOME, new ResourceIdentifier("mojave_desert"));
    public static final ResourceKey<Biome> LUSH_BEACH = ResourceKey.create(Registries.BIOME, new ResourceIdentifier("lush_beach"));





    // helpers and misc
    public static void init() {
        ExpandedBiomes.addExpandedBiome(PRIMORDIAL_CAVES, LevelStem.OVERWORLD);
    }



    public static final List<ResourceKey<Biome>> MOD_CAVE_BIOMES = List.of(PRIMORDIAL_CAVES);

    private static final Vec3 DEFAULT_LIGHT_COLOR = new Vec3(1, 1, 1);

    public static float getBiomeAmbientLight(Holder<Biome> value) {
        if (value.is(PRIMORDIAL_CAVES)) {
            return 0.125F;
        }
        return 0.0F;
    }

    public static float getBiomeFogNearness(Holder<Biome> value) {
        if (value.is(PRIMORDIAL_CAVES)) {
            return 0.5F;
        }
        return 1.0F;
    }
    public static float getBiomeWaterFogFarness(Holder<Biome> value) {
        if (value.is(PRIMORDIAL_CAVES)) {
            return 1.0F;
        }
        return 1.0F;
    }

    public static float getBiomeSkyOverride(Holder<Biome> value) {
        if (value.is(PRIMORDIAL_CAVES)) {
            return 1.0F;
        }
        return 0.0F;
    }

    public static Vec3 getBiomeLightColorOverride(Holder<Biome> value) {
        if (value.is(PRIMORDIAL_CAVES)) {
            return DEFAULT_LIGHT_COLOR;
        }
        return DEFAULT_LIGHT_COLOR;
    }

    public static int getBiomeTabletColor(ResourceKey<Biome> value) {
        if (value.equals(PRIMORDIAL_CAVES)) {
            return 0XFCBA00;
        }
        return -1;
    }

    public static float calculateBiomeSkyOverride(Entity player) {
        int i = Minecraft.getInstance().options.biomeBlendRadius().get();
        if (i == 0) {
            return QBiomes.getBiomeSkyOverride(player.level().getBiome(player.blockPosition()));
        } else {
            return BiomeSampler.sampleBiomesFloat(player.level(), player.position(), QBiomes::getBiomeSkyOverride);
        }
    }
}