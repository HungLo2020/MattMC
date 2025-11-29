package net.matt.quantize.entities;

import com.mojang.serialization.Codec;
import net.matt.quantize.tags.QTags;
import net.matt.quantize.worldgen.QBiomes;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;

import java.util.List;

public class MobSpawnBiomeModifier implements BiomeModifier {

    public static final Codec<MobSpawnBiomeModifier> CODEC =
            Codec.unit(MobSpawnBiomeModifier::new);


    private static final class SpawnConfig {
        final EntityType<?> entity;
        final MobCategory   category;
        final int           weight, min, max;

        final ResourceKey<Biome>[] keys;   // concrete biome keys
        final TagKey<Biome>[]      tags;   // biome tags

        /* concrete biomes -------------------------------------------------- */
        @SafeVarargs
        SpawnConfig(EntityType<?> e, MobCategory c, int w, int mi, int ma,
                    ResourceKey<Biome>... keys) {
            this(e, c, w, mi, ma, keys, new TagKey[0]);
        }

        /* biome tags ------------------------------------------------------- */
        @SafeVarargs
        SpawnConfig(EntityType<?> e, MobCategory c, int w, int mi, int ma,
                    TagKey<Biome>... tags) {
            this(e, c, w, mi, ma, new ResourceKey[0], tags);
        }

        /* common private ctor --------------------------------------------- */
        private SpawnConfig(EntityType<?> e, MobCategory c, int w, int mi, int ma,
                            ResourceKey<Biome>[] keys, TagKey<Biome>[] tags) {
            this.entity = e;
            this.category = c;
            this.weight = w;
            this.min = mi;
            this.max = ma;
            this.keys = keys;
            this.tags = tags;
        }

        /* does the biome match this config? -------------------------------- */
        boolean matches(Holder<Biome> biome) {
            for (ResourceKey<Biome> k : keys) if (biome.is(k)) return true;
            for (TagKey<Biome>      t : tags) if (biome.is(t)) return true;
            return false;
        }
    }

    /* --------------------------------------------------------------------- */
    /*  All spawn entries                                                    */
    /* --------------------------------------------------------------------- */
    private static final List<SpawnConfig> SPAWN_CONFIGS = List.of(
            new SpawnConfig(QEntities.CRAB.get(),    MobCategory.CREATURE,      10, 2, 5, Biomes.BEACH),
            new SpawnConfig(QEntities.CAIMAN.get(),   MobCategory.CREATURE,      29, 2, 4, QTags.IS_SWAMP),
            new SpawnConfig(QEntities.CROCODILE.get(),MobCategory.CREATURE,      20, 1, 2, QTags.IS_SWAMP),
            new SpawnConfig(QEntities.LOBSTER.get(),  MobCategory.WATER_AMBIENT, 7,  3, 5, Biomes.OCEAN, Biomes.WARM_OCEAN),
            new SpawnConfig(QEntities.MANTIS_SHRIMP.get(),  MobCategory.WATER_CREATURE, 15, 1, 4, Biomes.WARM_OCEAN),
            new SpawnConfig(QEntities.MIMIC_OCTOPUS.get(),  MobCategory.WATER_CREATURE,  9, 1, 2, Biomes.WARM_OCEAN),
            new SpawnConfig(QEntities.ALLIGATOR_SNAPPING_TURTLE.get(), MobCategory.CREATURE, 20, 1, 2, QTags.IS_SWAMP),
            new SpawnConfig(QEntities.ANACONDA.get(), MobCategory.CREATURE, 12, 1, 1, QTags.IS_SWAMP),      // accepts TagKey<Biome>
            new SpawnConfig(QEntities.BALD_EAGLE.get(), MobCategory.CREATURE, 15, 2, 4, QTags.IS_EAGLE_SPAWNS),
            new SpawnConfig(QEntities.SHOEBILL.get(), MobCategory.CREATURE, 10, 1, 2, QTags.IS_SWAMP),
            new SpawnConfig(QEntities.CACHALOT_WHALE.get(), MobCategory.WATER_CREATURE, 2, 1, 2, QTags.IS_DEEP_OCEAN),
            new SpawnConfig(QEntities.GIANT_SQUID.get(), MobCategory.WATER_CREATURE, 2, 1, 2, QTags.IS_DEEP_OCEAN),
            new SpawnConfig(QEntities.ORCA.get(), MobCategory.WATER_CREATURE, 2, 3, 4, QTags.IS_DEEP_OCEAN),
            new SpawnConfig(QEntities.COMB_JELLY.get(), MobCategory.WATER_AMBIENT, 2, 3, 4, QTags.IS_OCEAN),
            new SpawnConfig(QEntities.CATFISH.get(), MobCategory.WATER_AMBIENT, 2, 3, 4, QTags.IS_SWAMP),
            new SpawnConfig(QEntities.FRILLED_SHARK.get(), MobCategory.WATER_CREATURE, 11, 3, 4, QTags.IS_DEEP_OCEAN),
            new SpawnConfig(QEntities.BLOBFISH.get(), MobCategory.WATER_AMBIENT, 2, 3, 4, QTags.IS_DEEP_OCEAN),
            new SpawnConfig(QEntities.CAPUCHIN_MONKEY.get(), MobCategory.CREATURE, 2, 3, 4, QTags.IS_JUNGLE),
            new SpawnConfig(QEntities.CENTIPEDE_HEAD.get(), MobCategory.MONSTER, 2, 3, 4, QTags.IS_ANY_BIOME),
            new SpawnConfig(QEntities.UNDERMINER.get(), MobCategory.AMBIENT, 2, 3, 4, QTags.IS_ANY_BIOME),
            new SpawnConfig(QEntities.COCKROACH.get(), MobCategory.AMBIENT, 2, 3, 4, QTags.IS_ANY_BIOME),
            new SpawnConfig(QEntities.EMU.get(), MobCategory.CREATURE, 2, 3, 4, QTags.IS_SAVANNA_OR_MESA),
            new SpawnConfig(QEntities.CROW.get(), MobCategory.CREATURE, 2, 3, 4, QTags.IS_ANY_BIOME),
            new SpawnConfig(EntityType.WOLF, MobCategory.CREATURE, 8, 2, 4, QTags.SPAWNS_RUSTY_WOLF),
            new SpawnConfig(EntityType.WOLF, MobCategory.CREATURE, 8, 4, 8, QTags.SPAWNS_SPOTTED_WOLF),
            new SpawnConfig(EntityType.WOLF, MobCategory.CREATURE, 8, 4, 8, QTags.SPAWNS_STRIPED_WOLF),
            new SpawnConfig(QEntities.COSMIC_COD.get(), MobCategory.AMBIENT, 2, 4, 4, QTags.IS_END_BIOME),
            new SpawnConfig(QEntities.ELEPHANT.get(), MobCategory.CREATURE, 2, 4, 4, QTags.IS_SAVANNA_OR_MESA),
            new SpawnConfig(QEntities.ENDERGRADE.get(), MobCategory.CREATURE, 2, 4, 4, QTags.IS_END_BIOME),
            new SpawnConfig(QEntities.ENDERIOPHAGE.get(), MobCategory.CREATURE, 2, 4, 4, QTags.IS_END_BIOME),
            new SpawnConfig(QEntities.GELADA_MONKEY.get(), MobCategory.CREATURE, 2, 4, 4, QTags.IS_JUNGLE),
            new SpawnConfig(QEntities.KOMODO_DRAGON.get(), MobCategory.CREATURE, 2, 4, 4, QTags.IS_JUNGLE),
            new SpawnConfig(QEntities.TOUCAN.get(), MobCategory.CREATURE, 2, 4, 4, QTags.IS_JUNGLE),
            new SpawnConfig(QEntities.TIGER.get(), MobCategory.CREATURE, 2, 4, 4, QTags.IS_JUNGLE),
            new SpawnConfig(QEntities.SNOW_LEOPARD.get(), MobCategory.CREATURE, 2, 4, 4, Biomes.SNOWY_TAIGA),
            new SpawnConfig(QEntities.GORILLA.get(), MobCategory.CREATURE, 2, 4, 4, QTags.IS_JUNGLE),
            new SpawnConfig(QEntities.GAZELLE.get(), MobCategory.CREATURE, 2, 4, 4, QTags.IS_SAVANNA_OR_MESA),
            new SpawnConfig(QEntities.RHINOCEROS.get(), MobCategory.CREATURE, 2, 4, 4, QTags.IS_SAVANNA_OR_MESA),
            new SpawnConfig(QEntities.GRIZZLY_BEAR.get(), MobCategory.CREATURE, 2, 4, 4, QTags.IS_SAVANNA_OR_MESA),
            new SpawnConfig(QEntities.HAMMERHEAD_SHARK.get(), MobCategory.WATER_CREATURE, 2, 3, 4, QTags.IS_OCEAN),
            new SpawnConfig(QEntities.SEAL.get(), MobCategory.CREATURE, 2, 3, 4, Biomes.BEACH),
            new SpawnConfig(QEntities.ROADRUNNER.get(), MobCategory.CREATURE, 2, 3, 4, QBiomes.MOJAVE_DESERT),
            new SpawnConfig(QEntities.SEAGULL.get(), MobCategory.CREATURE, 2, 3, 4, Biomes.BEACH),
            new SpawnConfig(QEntities.POTOO.get(), MobCategory.CREATURE, 2, 3, 4, Biomes.DARK_FOREST)




    );

    /* --------------------------------------------------------------------- */
    /*  BiomeModifier implementation                                         */
    /* --------------------------------------------------------------------- */
    @Override
    public void modify(Holder<Biome> biome, Phase phase,
                       ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD) return;

        for (SpawnConfig cfg : SPAWN_CONFIGS) {
            if (cfg.matches(biome)) {
                builder.getMobSpawnSettings().addSpawn(
                        cfg.category,
                        new MobSpawnSettings.SpawnerData(
                                cfg.entity, cfg.weight, cfg.min, cfg.max));
            }
        }
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
