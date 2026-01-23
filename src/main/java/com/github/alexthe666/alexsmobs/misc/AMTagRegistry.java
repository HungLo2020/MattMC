package com.github.alexthe666.alexsmobs.misc;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

public class AMTagRegistry {
    // Catfish-specific tags
    public static final TagKey<EntityType<?>> CATFISH_IGNORE_EATING = registerEntityTag("catfish_ignore_eating");
    public static final TagKey<Item> CATFISH_ITEM_FASCINATIONS = registerItemTag("catfish_item_fascinations");
    public static final TagKey<Block> CATFISH_BLOCK_FASCINATIONS = registerBlockTag("catfish_block_fascinations");
    public static final TagKey<Biome> SPAWNS_HUGE_CATFISH = registerBiomeTag("spawns_huge_catfish");
    
    // Crow-specific tags
    public static final TagKey<Item> CROW_BREEDABLES = registerItemTag("crow_breedables");
    public static final TagKey<Item> CROW_TAMEABLES = registerItemTag("crow_tameables");
    public static final TagKey<Item> CROW_FOODSTUFFS = registerItemTag("crow_foodstuffs");
    public static final TagKey<Block> CROW_HOME_BLOCKS = registerBlockTag("crow_home_blocks");
    public static final TagKey<Block> CROW_FEARS = registerBlockTag("crow_fears");
    public static final TagKey<Block> CROW_FOODBLOCKS = registerBlockTag("crow_foodblocks");
    public static final TagKey<EntityType<?>> SCATTERS_CROWS = registerEntityTag("scatters_crows");

    // Endergrade-specific tags
    public static final TagKey<Item> ENDERGRADE_BREEDABLES = registerItemTag("endergrade_breedables");
    public static final TagKey<Item> ENDERGRADE_FOLLOWS = registerItemTag("endergrade_follows");
    public static final TagKey<Item> ENDERGRADE_FOODSTUFFS = registerItemTag("endergrade_foodstuffs");
    public static final TagKey<Block> ENDERGRADE_BREAKABLES = registerBlockTag("endergrade_breakables");

    // Gazelle-specific tags
    public static final TagKey<Item> GAZELLE_BREEDABLES = registerItemTag("gazelle_breedables");
    
    // Hummingbird-specific tags
    public static final TagKey<Item> HUMMINGBIRD_BREEDABLES = registerItemTag("hummingbird_breedables");
    public static final TagKey<Block> HUMMINGBIRD_POLLINATES = registerBlockTag("hummingbird_pollinates");
    public static final TagKey<Block> HUMMINGBIRD_SPAWNS = registerBlockTag("hummingbird_spawns");

    // Jerboa-specific tags
    public static final TagKey<Item> JERBOA_BREEDABLES = registerItemTag("jerboa_breedables");
    public static final TagKey<Item> JERBOA_BEGS_FOR = registerItemTag("jerboa_begs_for");

    // Mimic Octopus-specific tags
    public static final TagKey<EntityType<?>> MIMIC_OCTOPUS_FEARS = registerEntityTag("mimic_octopus_fears");
    public static final TagKey<Item> MIMIC_OCTOPUS_CREEPER_ITEMS = registerItemTag("mimic_octopus_creeper_items");
    public static final TagKey<Item> MIMIC_OCTOPUS_GUARDIAN_ITEMS = registerItemTag("mimic_octopus_guardian_items");
    public static final TagKey<Item> MIMIC_OCTOPUS_PUFFERFISH_ITEMS = registerItemTag("mimic_octopus_pufferfish_items");
    public static final TagKey<Item> MIMIC_OCTOPUS_BREEDABLES = registerItemTag("mimic_octopus_breedables");
    public static final TagKey<Item> MIMIC_OCTOPUS_TAMEABLES = registerItemTag("mimic_octopus_tameables");
    public static final TagKey<Item> MIMIC_OCTOPUS_ATTACK_FOODS = registerItemTag("mimic_octopus_attack_foods");
    public static final TagKey<Item> MIMIC_OCTOPUS_TOGGLES_MIMIC = registerItemTag("mimic_octopus_toggles_mimic");
    public static final TagKey<Item> MIMIC_OCTOPUS_MOISTURIZES = registerItemTag("mimic_octopus_moisturizes");
    public static final TagKey<Block> MIMIC_OCTOPUS_SPAWNS = registerBlockTag("mimic_octopus_spawns");

    // Mudskipper-specific tags
    public static final TagKey<Item> MUDSKIPPER_BREEDABLES = registerItemTag("mudskipper_breedables");
    public static final TagKey<Item> MUDSKIPPER_TAMEABLES = registerItemTag("mudskipper_tameables");
    public static final TagKey<Item> MUDSKIPPER_FOODSTUFFS = registerItemTag("mudskipper_foodstuffs");

    // Rain Frog-specific tags
    public static final TagKey<Item> RAIN_FROG_BREEDABLES = registerItemTag("rain_frog_breedables");
    public static final TagKey<Block> RAIN_FROG_SPAWNS = registerBlockTag("rain_frog_spawns");
    public static final TagKey<Item> INSECT_ITEMS = registerItemTag("insect_items");
    // Fly-specific tags
    public static final TagKey<Item> FLY_BREEDABLES = registerItemTag("fly_breedables");

    // Potoo-specific tags
    public static final TagKey<Item> POTOO_BREEDABLES = registerItemTag("potoo_breedables");
    public static final TagKey<Block> POTOO_PERCHES = registerBlockTag("potoo_perches");

    // Roadrunner-specific tags
    public static final TagKey<Item> ROADRUNNER_BREEDABLES = registerItemTag("roadrunner_breedables");
    public static final TagKey<Block> ROADRUNNER_SPAWNS = registerBlockTag("roadrunner_spawns");
    
    // Seagull-specific tags
    public static final TagKey<Item> SEAGULL_BREEDABLES = registerItemTag("seagull_breedables");
    public static final TagKey<Item> SEAGULL_OFFERINGS = registerItemTag("seagull_offerings");
    
    // Shoebill-specific tags
    public static final TagKey<Item> SHOEBILL_FOODSTUFFS = registerItemTag("shoebill_foodstuffs");
    public static final TagKey<Item> SHOEBILL_LUCK_FOODS = registerItemTag("shoebill_luck_foods");
    public static final TagKey<Item> SHOEBILL_LURE_FOODS = registerItemTag("shoebill_lure_foods");
    
    // Toucan-specific tags
    public static final TagKey<Item> TOUCAN_BREEDABLES = registerItemTag("toucan_breedables");
    public static final TagKey<Item> TOUCAN_GOLDEN_FOODS = registerItemTag("toucan_golden_foods");
    public static final TagKey<Item> TOUCAN_ENCHANTED_GOLDEN_FOODS = registerItemTag("toucan_enchanted_golden_foods");

    // Anteater-specific tags
    public static final TagKey<Item> ANTEATER_BREEDABLES = registerItemTag("anteater_breedables");
    public static final TagKey<Item> ANTEATER_FOODSTUFFS = registerItemTag("anteater_foodstuffs");

    // Caiman-specific tags
    public static final TagKey<Item> CAIMAN_BREEDABLES = registerItemTag("caiman_breedables");
    public static final TagKey<Item> CAIMAN_FOODSTUFFS = registerItemTag("caiman_foodstuffs");
    public static final TagKey<EntityType<?>> CAIMAN_TARGETS = registerEntityTag("caiman_targets");
    public static final TagKey<Block> CAIMAN_SPAWNS = registerBlockTag("caiman_spawns");
    public static final TagKey<Block> CROCODILE_SPAWNS = registerBlockTag("crocodile_spawns");

    // Capuchin Monkey-specific tags
    public static final TagKey<Item> CAPUCHIN_MONKEY_TAMEABLES = registerItemTag("capuchin_monkey_tameables");
    public static final TagKey<Item> CAPUCHIN_MONKEY_BREEDABLES = registerItemTag("capuchin_monkey_breedables");
    public static final TagKey<Item> CAPUCHIN_MONKEY_FOODSTUFFS = registerItemTag("capuchin_monkey_foodstuffs");
    public static final TagKey<Block> CAPUCHIN_MONKEY_SPAWNS = registerBlockTag("capuchin_monkey_spawns");
    public static final TagKey<Item> BANANAS = registerItemTag("bananas");
    public static final TagKey<EntityType<?>> MONKEY_TARGET_WITH_DART = registerEntityTag("monkey_target_with_dart");

    // Cosmaw-specific tags
    public static final TagKey<Item> COSMAW_FOODSTUFFS = registerItemTag("cosmaw_foodstuffs");
    public static final TagKey<Item> COSMAW_BREEDABLES = registerItemTag("cosmaw_breedables");
    public static final TagKey<Item> COSMAW_TAMEABLES = registerItemTag("cosmaw_tameables");

    private static TagKey<Item> registerItemTag(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace(name));
    }

    private static TagKey<Block> registerBlockTag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace(name));
    }

    private static TagKey<EntityType<?>> registerEntityTag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace(name));
    }

    private static TagKey<Biome> registerBiomeTag(String name) {
        return TagKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace(name));
    }
}
