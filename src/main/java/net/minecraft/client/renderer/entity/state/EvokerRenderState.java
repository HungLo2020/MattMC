package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class EvokerRenderState extends IllagerRenderState {
	public boolean isCastingSpell;
}
