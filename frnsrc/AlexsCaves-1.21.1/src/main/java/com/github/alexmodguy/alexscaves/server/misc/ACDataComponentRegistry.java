package com.github.alexmodguy.alexscaves.server.misc;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ACDataComponentRegistry {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AlexsCaves.MODID);

    public static final Supplier<DataComponentType<ResourceKey<Biome>>> CAVE_BIOME = DATA_COMPONENTS.register("cave_biome", () ->
            DataComponentType.<ResourceKey<Biome>>builder()
                    .persistent(ResourceKey.codec(Registries.BIOME))
                    .networkSynchronized(ResourceKey.streamCodec(Registries.BIOME))
                    .build());

    public static void init(IEventBus bus) {
        DATA_COMPONENTS.register(bus);
    }
}
