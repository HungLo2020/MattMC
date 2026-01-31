package net.alexscaves.server.misc;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public class ACDamageTypes {
    public static final ResourceKey<DamageType> DINOSAUR_ATTACK = ResourceKey.create(
        Registries.DAMAGE_TYPE, 
        ResourceLocation.fromNamespaceAndPath("alexscaves", "dinosaur_attack")
    );
}
