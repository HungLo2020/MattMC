package com.github.alexmodguy.alexscaves.server.block.grower;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.Optional;

public class PewenGrower {

    public static final ResourceKey<ConfiguredFeature<?, ?>> PEWEN_TREE = ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath("minecraft", "pewen_tree"));

    public static final TreeGrower GROWER = new TreeGrower(
            "pewen",
            Optional.empty(),
            Optional.of(PEWEN_TREE),
            Optional.empty()
    );
}
