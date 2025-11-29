package net.matt.quantize.recipes;

import net.matt.quantize.Quantize;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class QRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Quantize.MOD_ID);

    public static final RegistryObject<RecipeSerializer<PulverizingRecipe>> PULVERIZING_SERIALIZER =
            SERIALIZERS.register("pulverizing", () -> PulverizingRecipe.Serializer.INSTANCE);
    public static final RegistryObject<RecipeSerializer<BotanyRecipe>> BOTANY_POT_SERIALIZER =
            SERIALIZERS.register("botany_pot", () -> BotanyRecipe.Serializer.INSTANCE);

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}