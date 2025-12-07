package net.minecraft.client.renderer.texture;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface Tickable {
	void tick();
}
