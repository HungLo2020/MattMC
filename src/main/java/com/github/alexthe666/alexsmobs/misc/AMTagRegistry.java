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
