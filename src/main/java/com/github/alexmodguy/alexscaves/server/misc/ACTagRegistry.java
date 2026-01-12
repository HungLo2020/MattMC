package com.github.alexmodguy.alexscaves.server.misc;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public class ACTagRegistry {
    public static final TagKey<Block> DINOSAURS_SPAWNABLE_ON = BlockTags.create(ResourceLocation.fromNamespaceAndPath("alexscaves", "dinosaurs_spawnable_on"));
    public static final TagKey<EntityType<?>> FLEEABLE_FROM = EntityTypeTags.create(ResourceLocation.fromNamespaceAndPath("alexscaves", "fleeable_from"));
}
