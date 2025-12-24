package net.minecraft.client.resources.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface ModelDebugName {
	String debugName();
}
