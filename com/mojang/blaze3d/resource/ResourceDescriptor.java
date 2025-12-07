package com.mojang.blaze3d.resource;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface ResourceDescriptor<T> {
	T allocate();

	default void prepare(T object) {
	}

	void free(T object);

	default boolean canUsePhysicalResource(ResourceDescriptor<?> resourceDescriptor) {
		return this.equals(resourceDescriptor);
	}
}
