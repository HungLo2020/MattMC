package net.minecraft.core;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DefaultedRegistry<T> extends Registry<T> {
	@NotNull
	@Override
	ResourceLocation getKey(T object);

	@NotNull
	@Override
	T getValue(@Nullable ResourceLocation resourceLocation);

	@NotNull
	@Override
	T byId(int i);

	ResourceLocation getDefaultKey();
}
