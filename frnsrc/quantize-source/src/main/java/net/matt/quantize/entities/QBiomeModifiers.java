package net.matt.quantize.entities;

import com.mojang.serialization.Codec;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.matt.quantize.Quantize;

public class QBiomeModifiers {
    public static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Quantize.MOD_ID);

    public static final RegistryObject<Codec<? extends BiomeModifier>> CRAB_SPAWNS =
            BIOME_MODIFIERS.register("crab_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> CAIMAN_SPAWNS =
            BIOME_MODIFIERS.register("caiman_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> CROCODILE_SPAWNS =
            BIOME_MODIFIERS.register("crocodile_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> LOBSTER_SPAWNS =
            BIOME_MODIFIERS.register("lobster_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> MANTIS_SHRIMP_SPAWNS =
            BIOME_MODIFIERS.register("mantis_shrimp_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> MIMIC_OCTOPUS_SPAWNS =
            BIOME_MODIFIERS.register("mimic_octopus_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> ALLIGATOR_SNAPPING_TURTLE_SPAWNS =
            BIOME_MODIFIERS.register("alligator_snapping_turtle_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> ANACONDA_SPAWNS =
            BIOME_MODIFIERS.register("anaconda_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> ANTEATER_SPAWNS =
            BIOME_MODIFIERS.register("anteater_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> LEAFCUTTER_ANT_SPAWNS =
            BIOME_MODIFIERS.register("leafcutter_ant_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> BALD_EAGLE_SPAWNS =
            BIOME_MODIFIERS.register("bald_eagle_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> SHOEBILL_SPAWNS =
            BIOME_MODIFIERS.register("shoebill_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> CACHALOT_WHALE_SPAWNS =
            BIOME_MODIFIERS.register("cachalot_whale_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> GIANT_SQUID_SPAWNS =
            BIOME_MODIFIERS.register("giant_squid_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> ORCA_SPAWNS =
            BIOME_MODIFIERS.register("orca_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> CAPUCHIN_MONKEY_SPAWNS =
            BIOME_MODIFIERS.register("capuchin_monkey_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> CATFISH_SPAWNS =
            BIOME_MODIFIERS.register("catfish_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> FRILLED_SHARK_SPAWNS =
            BIOME_MODIFIERS.register("frilled_shark_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> BLOBFISH_SPAWNS =
            BIOME_MODIFIERS.register("blobfish_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> CAVE_CENTIPEDE_SPAWNS =
            BIOME_MODIFIERS.register("cave_centipede_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> COCKROACH_SPAWNS =
            BIOME_MODIFIERS.register("cockroach_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> EMU_SPAWNS =
            BIOME_MODIFIERS.register("emu_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> COMB_JELLY_SPAWNS =
            BIOME_MODIFIERS.register("comb_jelly_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> COSMIC_COD_SPAWNS =
            BIOME_MODIFIERS.register("cosmic_cod_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> ELEPHANT_SPAWNS =
            BIOME_MODIFIERS.register("elephant_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> ENDERGRADE_SPAWNS =
            BIOME_MODIFIERS.register("endergrade_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> ENDERIOPHAGE_SPAWNS =
            BIOME_MODIFIERS.register("enderiophage_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> GAZELLE_SPAWNS =
            BIOME_MODIFIERS.register("gazelle_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> GELADA_MONKEY_SPAWNS =
            BIOME_MODIFIERS.register("gelada_monkey_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> GORILLA_SPAWNS =
            BIOME_MODIFIERS.register("gorilla_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> GRIZZLY_BEAR_SPAWNS =
            BIOME_MODIFIERS.register("grizzly_bear_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> HAMMERHEAD_SHARK_SPAWNS =
            BIOME_MODIFIERS.register("hammerhead_shark_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> KOMODO_DRAGON_SPAWNS =
            BIOME_MODIFIERS.register("komodo_dragon_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> UNDERMINER_SPAWNS =
            BIOME_MODIFIERS.register("underminer_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> TOUCAN_SPAWNS =
            BIOME_MODIFIERS.register("toucan_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> TIGER_SPAWNS =
            BIOME_MODIFIERS.register("tiger_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> SNOW_LEOPARD_SPAWNS =
            BIOME_MODIFIERS.register("snow_leopard_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> SEAL_SPAWNS =
            BIOME_MODIFIERS.register("seal_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> SEAGULL_SPAWNS =
            BIOME_MODIFIERS.register("seagull_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> ROADRUNNER_SPAWNS =
            BIOME_MODIFIERS.register("roadrunner_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> RHINOCEROS_SPAWNS =
            BIOME_MODIFIERS.register("rhinoceros_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));
    public static final RegistryObject<Codec<? extends BiomeModifier>> POTOO_SPAWNS =
            BIOME_MODIFIERS.register("potoo_spawns", () -> Codec.unit(() -> new MobSpawnBiomeModifier()));


    public static void register(IEventBus bus) {
        BIOME_MODIFIERS.register(bus);
    }
}