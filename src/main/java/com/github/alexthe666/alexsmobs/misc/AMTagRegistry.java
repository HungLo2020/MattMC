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
