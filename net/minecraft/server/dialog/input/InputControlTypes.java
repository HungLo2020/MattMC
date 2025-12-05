package net.minecraft.server.dialog.input;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

public class InputControlTypes {
	public static MapCodec<? extends InputControl> bootstrap(Registry<MapCodec<? extends InputControl>> registry) {
		Registry.register(registry, ResourceLocation.withDefaultNamespace("boolean"), BooleanInput.MAP_CODEC);
		Registry.register(registry, ResourceLocation.withDefaultNamespace("number_range"), NumberRangeInput.MAP_CODEC);
		Registry.register(registry, ResourceLocation.withDefaultNamespace("single_option"), SingleOptionInput.MAP_CODEC);
		return Registry.register(registry, ResourceLocation.withDefaultNamespace("text"), TextInput.MAP_CODEC);
	}
}
