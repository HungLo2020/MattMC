// Java
package net.matt.quantize.tags;

import net.matt.quantize.Quantize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

public class QTags {

    /*public static class Blocks {
        public static final TagKey<Block> CRAB_SPAWNABLE = TagKey.create(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getRegistryKey(),
                new ResourceIdentifier("crab_spawnable"));
    }

    public static class Items {
        public static final TagKey<Item> CRAB_TEMPT_ITEMS = TagKey.create(net.minecraftforge.registries.ForgeRegistries.ITEMS.getRegistryKey(),
                new ResourceIdentifier("crab_tempt_items"));
    }*/

    // Item Tags
    public static final TagKey<Item> CAIMAN_BREEDABLES = registerItemTag("caiman_breedables");
    public static final TagKey<Item> CAIMAN_FOODSTUFFS = registerItemTag("caiman_foodstuffs");
    public static final TagKey<Item> CRAB_TEMPT_ITEMS = registerItemTag("crab_tempt_items");
    public static final TagKey<Item> CROCODILE_BREEDABLES = registerItemTag("crocodile_breedables");
    public static final TagKey<Item> MANTIS_SHRIMP_BREEDABLES = registerItemTag("mantis_shrimp_breedables");
    public static final TagKey<Item> MANTIS_SHRIMP_TAMEABLES = registerItemTag("mantis_shrimp_tameables");
    public static final TagKey<Item> SHRIMP_RICE_FRYABLES = registerItemTag("shrimp_rice_fryables");
    public static final TagKey<Item> MIMIC_OCTOPUS_ATTACK_FOODS = registerItemTag("mimic_octopus_attack_foods");
    public static final TagKey<Item> MIMIC_OCTOPUS_MOISTURIZES = registerItemTag("mimic_octopus_moisturizes");
    public static final TagKey<Item> MIMIC_OCTOPUS_TAMEABLES = registerItemTag("mimic_octopus_tameables");
    public static final TagKey<Item> MIMIC_OCTOPUS_TOGGLES_MIMIC = registerItemTag("mimic_octopus_toggles_mimic");
    public static final TagKey<Item> MIMIC_OCTOPUS_BREEDABLES = registerItemTag("mimic_octopus_breedables");
    public static final TagKey<Item> MIMIC_OCTOPUS_CREEPER_ITEMS = registerItemTag("mimic_octopus_creeper_items");
    public static final TagKey<Item> MIMIC_OCTOPUS_GUARDIAN_ITEMS = registerItemTag("mimic_octopus_guardian_items");
    public static final TagKey<Item> MIMIC_OCTOPUS_PUFFERFISH_ITEMS = registerItemTag("mimic_octopus_pufferfish_items");
    public static final TagKey<Item> ALLIGATOR_SNAPPING_TURTLE_BREEDABLES = registerItemTag("alligator_snapping_turtle_breedables");
    public static final TagKey<Item> ANACONDA_FOODSTUFFS = registerItemTag("anaconda_foodstuffs");
    public static final TagKey<Item> LEAFCUTTER_ANT_FOODSTUFFS = registerItemTag("leafcutter_ant_foodstuffs");
    public static final TagKey<Item> ANTEATER_BREEDABLES = registerItemTag("anteater_breedables");
    public static final TagKey<Item> ANTEATER_FOODSTUFFS = registerItemTag("anteater_foodstuffs");
    public static final TagKey<Item> INSECT_ITEMS = registerItemTag("insect_items");
    public static final TagKey<Item> BALD_EAGLE_TAMEABLES = registerItemTag("bald_eagle_tameables");
    public static final TagKey<Item> BALD_EAGLE_FOODSTUFFS = registerItemTag("bald_eagle_foodstuffs");
    public static final TagKey<Item> BALD_EAGLE_BREEDABLES = registerItemTag("bald_eagle_breedables");
    public static final TagKey<Item> SHOEBILL_LURE_FOODS = registerItemTag("shoebill_lure_foods");
    public static final TagKey<Item> SHOEBILL_LUCK_FOODS = registerItemTag("shoebill_luck_foods");
    public static final TagKey<Item> SHOEBILL_FOODSTUFFS = registerItemTag("shoebill_foodstuffs");
    public static final TagKey<Item> VALLUMRAPTOR_STEALS = registerItemTag("vallumraptor_steals");
    public static final TagKey<Item> CAPUCHIN_MONKEY_TAMEABLES = registerItemTag("capuchin_monkey_tameables");
    public static final TagKey<Item> CAPUCHIN_MONKEY_BREEDABLES = registerItemTag("capuchin_monkey_breedables");
    public static final TagKey<Item> CAPUCHIN_MONKEY_FOODSTUFFS = registerItemTag("capuchin_monkey_foodstuffs");
    public static final TagKey<Item> BANANAS = registerItemTag("bananas");
    public static final TagKey<Item> CATFISH_ITEM_FASCINATIONS = registerItemTag("catfish_item_fascinations");
    public static final TagKey<Item> COCKROACH_BREEDABLES = registerItemTag("cockroach_breedables");
    public static final TagKey<Item> COCKROACH_FOODSTUFFS = registerItemTag("cockroach_foodstuffs");
    public static final TagKey<Item> EMU_BREEDABLES = registerItemTag("emu_breedables");
    public static final TagKey<Item> CROW_FOODSTUFFS = registerItemTag("crow_foodstuffs");
    public static final TagKey<Item> CROW_BREEDABLES = registerItemTag("crow_breedables");
    public static final TagKey<Item> CROW_TAMEABLES = registerItemTag("crow_tameables");
    public static final TagKey<Item> ELEPHANT_FOODSTUFFS = registerItemTag("elephant_foodstuffs");
    public static final TagKey<Item> ELEPHANT_TAMEABLES = registerItemTag("elephant_tameables");
    public static final TagKey<Item> ELEPHANT_BREEDABLES = registerItemTag("elephant_breedables");
    public static final TagKey<Item> ENDERGRADE_FOODSTUFFS = registerItemTag("endergrade_foodstuffs");
    public static final TagKey<Item> ENDERGRADE_FOLLOWS = registerItemTag("endergrade_follows");
    public static final TagKey<Item> ENDERGRADE_BREEDABLES = registerItemTag("endergrade_breedables");
    public static final TagKey<Item> GAZELLE_BREEDABLES = registerItemTag("gazelle_breedables");
    public static final TagKey<Item> GELADA_MONKEY_BREEDABLES = registerItemTag("gelada_monkey_breedables");
    public static final TagKey<Item> GELADA_MONKEY_LAND_CLEARING_FOODS = registerItemTag("gelada_monkey_land_clearing_foods");
    public static final TagKey<Item> GORILLA_BREEDABLES = registerItemTag("gorilla_breedables");
    public static final TagKey<Item> GORILLA_FOODSTUFFS = registerItemTag("gorilla_foodstuffs");
    public static final TagKey<Item> GORILLA_TAMEABLES = registerItemTag("gorilla_tameables");
    public static final TagKey<Item> GRIZZLY_TAMEABLES = registerItemTag("grizzly_tameables");
    public static final TagKey<Item> GRIZZLY_BREEDABLES = registerItemTag("grizzly_breedables");
    public static final TagKey<Item> GRIZZLY_FOODSTUFFS = registerItemTag("grizzly_foodstuffs");
    public static final TagKey<Item> GRIZZLY_HONEY = registerItemTag("grizzly_honey");
    public static final TagKey<Item> KOMODO_DRAGON_BREEDABLES = registerItemTag("komodo_dragon_breedables");
    public static final TagKey<Item> KOMODO_DRAGON_TAMEABLES = registerItemTag("komodo_dragon_tameables");
    public static final TagKey<Item> UNDERMINER_ORES = registerItemTag("underminer_ores");
    public static final TagKey<Item> TOUCAN_BREEDABLES = registerItemTag("toucan_breedables");
    public static final TagKey<Item> TOUCAN_ENCHANTED_GOLDEN_FOODS = registerItemTag("toucan_enchanted_golden_foods");
    public static final TagKey<Item> TOUCAN_GOLDEN_FOODS = registerItemTag("toucan_golden_foods");
    public static final TagKey<Item> TIGER_BREEDABLES = registerItemTag("tiger_breedables");
    public static final TagKey<Item> SNOW_LEOPARD_BREEDABLES = registerItemTag("snow_leopard_breedables");
    public static final TagKey<Item> SEAGULL_BREEDABLES = registerItemTag("seagull_breedables");
    public static final TagKey<Item> SEAGULL_OFFERINGS = registerItemTag("seagull_offerings");
    public static final TagKey<Item> SEAL_BREEDABLES = registerItemTag("seal_breedables");
    public static final TagKey<Item> SEAL_OFFERINGS = registerItemTag("seal_offerings");
    public static final TagKey<Item> ROADRUNNER_BREEDABLES = registerItemTag("roadrunner_breedables");
    public static final TagKey<Item> RHINOCEROS_BREEDABLES = registerItemTag("rhinoceros_breedables");
    public static final TagKey<Item> RHINOCEROS_FOODSTUFFS = registerItemTag("rhinoceros_foodstuffs");
    public static final TagKey<Item> POTOO_BREEDABLES = registerItemTag("potoo_breedables");


    // Block Tags
    public static final TagKey<Block> SANDY_PLANT_CAN_SURVIVE_ON = registerBlockTag("sandy_plant_can_survive_on");
    public static final TagKey<Block> CRAB_SPAWNABLE = registerBlockTag("crab_spawnable");
    public static final TagKey<Block> CAIMAN_SPAWNABLE = registerBlockTag("caiman_spawnable");
    public static final TagKey<Block> CROCODILE_SPAWNABLE = registerBlockTag("crocodile_spawnable");
    public static final TagKey<Block> LOBSTER_SPAWNABLE = registerBlockTag("lobster_spawnable");
    public static final TagKey<Block> MANTIS_SHRIMP_SPAWNABLE = registerBlockTag("mantis_shrimp_spawnable");
    public static final TagKey<Block> MIMIC_OCTOPUS_SPAWNABLE = registerBlockTag("mimic_octopus_spawnable");
    public static final TagKey<Block> ALLIGATOR_SNAPPING_TURTLE_SPAWNS = registerBlockTag("alligator_snapping_turtle_spawns");
    public static final TagKey<Block> ANACONDA_SPAWNS = registerBlockTag("anaconda_spawns");
    public static final TagKey<Block> LEAFCUTTER_PUPA_USABLE_ON = registerBlockTag("leafcutter_pupa_usable_on");
    public static final TagKey<Block> LEAFCUTTER_ANT_BREAKABLES = registerBlockTag("leafcutter_ant_breakables");
    public static final TagKey<Block> TREE_REPLACEABLE_BLOCKS = registerBlockTag("tree_replaceable_blocks");
    public static final TagKey<Block> REPLACEABLE_BLOCKS = registerBlockTag("replaceable_blocks");
    public static final TagKey<Block> TREE_GRASS_REPLACEABLES = registerBlockTag("tree_grass_replaceables");
    public static final TagKey<Block> ORCA_BREAKABLES = registerBlockTag("orca_breakables");
    public static final TagKey<Block> CACHALOT_WHALE_BREAKABLES = registerBlockTag("cachalot_whale_breakables");
    public static final TagKey<Block> UNMOVEABLE = registerBlockTag("unmoveable");
    public static final TagKey<Block> DINOSAURS_SPAWNABLE_ON = registerBlockTag("dinosaurs_spawnable_on");
    public static final TagKey<Block> VOLCANO_BLOCKS = registerBlockTag("volcano_blocks");
    public static final TagKey<Block> STOPS_DINOSAUR_EGGS = registerBlockTag("stops_dinosaur_eggs");
    public static final TagKey<Block> GROTTOCERATOPS_FOOD_BLOCKS = registerBlockTag("grottoceratops_food_blocks");
    public static final TagKey<Block> RELICHEIRUS_NIBBLES = registerBlockTag("relicheirus_nibbles");
    public static final TagKey<Block> RELICHEIRUS_KNOCKABLE_LEAVES = registerBlockTag("relicheirus_knockable_leaves");
    public static final TagKey<Block> RELICHEIRUS_KNOCKABLE_LOGS = registerBlockTag("relicheirus_knockable_logs");
    public static final TagKey<Block> COOKS_MEAT_BLOCKS = registerBlockTag("cooks_meat_blocks");
    public static final TagKey<Block> REGENERATES_AFTER_PRIMORDIAL_BOSS_FIGHT = registerBlockTag("regenerates_after_primordial_boss_fight");
    public static final TagKey<Block> LUXTRUCTOSAURUS_BREAKS = registerBlockTag("luxtructosaurus_breaks");
    public static final TagKey<Block> CAPUCHIN_MONKEY_SPAWNS = registerBlockTag("capuchin_monkey_spawns");
    public static final TagKey<Block> CATFISH_BLOCK_FASCINATIONS = registerBlockTag("catfish_block_fascinations");
    public static final TagKey<Block> ORCA_SPAWNS = registerBlockTag("orca_spawns");
    public static final TagKey<Block> CACHALOT_WHALE_SPAWNS = registerBlockTag("cachalot_whale_spawns");
    public static final TagKey<Block> BLOBFISH_SPAWNS = registerBlockTag("blobfish_spawns");
    public static final TagKey<Block> GIANT_SQUID_SPAWNS = registerBlockTag("giant_squid_spawns");
    public static final TagKey<Block> EMU_SPAWNS = registerBlockTag("emu_spawns");
    public static final TagKey<Block> BRANCHES_CAN_SURVIVE_ON = registerBlockTag("branches_can_survive_on");
    public static final TagKey<Block> CROW_HOME_BLOCKS = registerBlockTag("crow_home_blocks");
    public static final TagKey<Block> CROW_FOODBLOCKS = registerBlockTag("crow_foodblocks");
    public static final TagKey<Block> CROW_FEARS = registerBlockTag("crow_fears");
    public static final TagKey<Block> ELEPHANT_FOODBLOCKS = registerBlockTag("elephant_foodblocks");
    public static final TagKey<Block> DROPS_ACACIA_BLOSSOMS = registerBlockTag("drops_acacia_blossoms");
    public static final TagKey<Block> ENDERGRADE_BREAKABLES = registerBlockTag("endergrade_breakables");
    public static final TagKey<Block> GELADA_MONKEY_GRASS = registerBlockTag("gelada_monkey_grass");
    public static final TagKey<Block> GORILLA_BREAKABLES = registerBlockTag("gorilla_breakables");
    public static final TagKey<Block> GORILLA_SPAWNS = registerBlockTag("gorilla_spawns");
    public static final TagKey<Block> GRIZZLY_BEEHIVE = registerBlockTag("grizzly_beehive");
    public static final TagKey<Block> KOMODO_DRAGON_SPAWNS = registerBlockTag("komodo_dragon_spawns");
    public static final TagKey<Block> SNOW_LEOPARD_SPAWNS = registerBlockTag("snow_leopard_spawns");
    public static final TagKey<Block> SEAL_DIGABLES = registerBlockTag("seal_digables");
    public static final TagKey<Block> SEAL_SPAWNS = registerBlockTag("seal_spawns");
    public static final TagKey<Block> ROADRUNNER_SPAWNS = registerBlockTag("roadrunner_spawns");
    public static final TagKey<Block> POTOO_PERCHES = registerBlockTag("potoo_perches");


    // Entity Tags
    public static final TagKey<EntityType<?>> CAIMAN_TARGETS = registerEntityTag("caiman_targets");
    public static final TagKey<EntityType<?>> CROCODILE_TARGETS = registerEntityTag("crocodile_targets");
    public static final TagKey<EntityType<?>> MANTIS_SHRIMP_TARGETS = registerEntityTag("mantis_shrimp_targets");
    public static final TagKey<EntityType<?>> MIMIC_OCTOPUS_FEARS = registerEntityTag("mimic_octopus_fears");
    public static final TagKey<EntityType<?>> ANACONDA_TARGETS = registerEntityTag("anaconda_targets");
    public static final TagKey<EntityType<?>> BALD_EAGLE_TARGETS = registerEntityTag("bald_eagle_targets");
    public static final TagKey<EntityType<?>> CACHALOT_WHALE_TARGETS = registerEntityTag("cachalot_whale_targets");
    public static final TagKey<EntityType<?>> GIANT_SQUID_TARGETS = registerEntityTag("giant_squid_targets");
    public static final TagKey<EntityType<?>> ORCA_TARGETS = registerEntityTag("orca_targets");
    public static final TagKey<EntityType<?>> DINOSAURS = registerEntityTag("dinosaurs");
    public static final TagKey<EntityType<?>> SUBTERRANODON_FLEES = registerEntityTag("subterranodon_flees");
    public static final TagKey<EntityType<?>> VALLUMRAPTOR_TARGETS = registerEntityTag("vallumraptor_targets");
    public static final TagKey<EntityType<?>> RESISTS_TREMORSAURUS_ROAR = registerEntityTag("resists_tremorsaurus_roar");
    public static final TagKey<EntityType<?>> AMBER_MONOLITH_SKIPS = registerEntityTag("amber_monolith_skips");
    public static final TagKey<EntityType<?>> MONKEY_TARGET_WITH_DART = registerEntityTag("monkey_target_with_dart");
    public static final TagKey<EntityType<?>> CATFISH_IGNORE_EATING = registerEntityTag("catfish_ignore_eating");
    public static final TagKey<EntityType<?>> SCATTERS_CROWS = registerEntityTag("scatters_crows");
    public static final TagKey<EntityType<?>> KOMODO_DRAGON_TARGETS = registerEntityTag("komodo_dragon_targets");
    public static final TagKey<EntityType<?>> TIGER_TARGETS = registerEntityTag("tiger_targets");
    public static final TagKey<EntityType<?>> SNOW_LEOPARD_TARGETS = registerEntityTag("snow_leopard_targets");



    // Biome Spawn Tags
    public static final TagKey<Biome> SPAWNS_DESERT_CROCODILES = registerBiomeTag("spawns_desert_crocodiles");
    public static final TagKey<Biome> SPAWNS_WHITE_MANTIS_SHRIMP = registerBiomeTag("spawns_white_mantis_shrimp");
    public static final TagKey<Biome> SPAWNS_HUGE_CATFISH = registerBiomeTag("spawns_huge_catfish");
    public static final TagKey<Biome> SPAWNS_WOODS_WOLF = registerBiomeTag("spawns_woods_wolf");
    public static final TagKey<Biome> SPAWNS_ASHEN_WOLF = registerBiomeTag("spawns_ashen_wolf");
    public static final TagKey<Biome> SPAWNS_BLACK_WOLF = registerBiomeTag("spawns_black_wolf");
    public static final TagKey<Biome> SPAWNS_CHESTNUT_WOLF = registerBiomeTag("spawns_chestnut_wolf");
    public static final TagKey<Biome> SPAWNS_RUSTY_WOLF = registerBiomeTag("spawns_rusty_wolf");
    public static final TagKey<Biome> SPAWNS_SPOTTED_WOLF = registerBiomeTag("spawns_spotted_wolf");
    public static final TagKey<Biome> SPAWNS_STRIPED_WOLF = registerBiomeTag("spawns_striped_wolf");
    public static final TagKey<Biome> SPAWNS_SNOWY_WOLF = registerBiomeTag("spawns_snowy_wolf");
    public static final TagKey<Biome> SPAWNS_WHITE_SEALS = registerBiomeTag("spawns_white_seals");
    // Biome Tags
    public static final TagKey<Biome> IS_SWAMP = registerBiomeTag("is_swamp");
    public static final TagKey<Biome> IS_DEEP_OCEAN = registerBiomeTag("is_deep_ocean");
    public static final TagKey<Biome> IS_OCEAN = registerBiomeTag("is_ocean");
    public static final TagKey<Biome> IS_JUNGLE = registerBiomeTag("is_jungle");
    public static final TagKey<Biome> IS_EAGLE_SPAWNS = registerBiomeTag("is_eagle_spawns");
    public static final TagKey<Biome> IS_ANY_BIOME = registerBiomeTag("is_any_biome");
    public static final TagKey<Biome> IS_SAVANNA_OR_MESA = registerBiomeTag("is_savanna_or_mesa");
    public static final TagKey<Biome> IS_COLD_BIOME = registerBiomeTag("is_cold_biome");
    public static final TagKey<Biome> IS_WARM_BIOME = registerBiomeTag("is_warm_biome");
    public static final TagKey<Biome> IS_END_BIOME = registerBiomeTag("is_end_biome");

    // Structure Tags
    public static final TagKey<Structure> SPAWNS_UNDERMINERS = registerStructureTag("spawns_underminers");





    // Helpers
    private static TagKey<EntityType<?>> registerEntityTag(String name) {
        validateTagFile("entity_types", name);
        return TagKey.create(Registries.ENTITY_TYPE, new ResourceIdentifier(name));
    }

    private static TagKey<Item> registerItemTag(String name) {
        validateTagFile("items", name);
        return TagKey.create(Registries.ITEM, new ResourceIdentifier(name));
    }

    private static TagKey<Block> registerBlockTag(String name) {
        validateTagFile("blocks", name);
        return TagKey.create(Registries.BLOCK, new ResourceIdentifier(name));
    }

    private static TagKey<Biome> registerBiomeTag(String name) {
        validateTagFile("biome", name);
        return TagKey.create(Registries.BIOME, new ResourceIdentifier(name));
    }

    private static TagKey<Structure> registerStructureTag(String name) {
        validateTagFile("structure", name);
        return TagKey.create(Registries.STRUCTURE, new ResourceIdentifier(name));
    }

    private static void validateTagFile(String registry, String name) {
        ResourceLocation resourceLocation = new ResourceIdentifier(registry + "/" + name + ".json");
        if (!Quantize.MOD_ID.equals(resourceLocation.getNamespace())) {
            throw new RuntimeException("Invalid namespace for tag: " + resourceLocation.toString());
        }
        try {
            net.minecraft.server.packs.resources.ResourceManager resourceManager = net.minecraft.client.Minecraft.getInstance().getResourceManager();
            resourceManager.getResource(resourceLocation); // Attempt to fetch the resource
        } catch (Exception e) {
            Quantize.LOGGER.error("Missing tag JSON file for: " + name + " in registry: " + registry, e);
            throw new RuntimeException("Tag JSON file not found: " + resourceLocation.toString(), e);
        }
    }
}