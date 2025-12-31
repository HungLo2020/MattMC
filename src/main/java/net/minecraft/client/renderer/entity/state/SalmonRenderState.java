package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.animal.Salmon.Variant;

@Environment(EnvType.CLIENT)
public class SalmonRenderState extends LivingEntityRenderState {
	public Variant variant = Variant.MEDIUM;
}
