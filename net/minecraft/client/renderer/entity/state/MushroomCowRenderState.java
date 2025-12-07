package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.animal.MushroomCow.Variant;

@Environment(EnvType.CLIENT)
public class MushroomCowRenderState extends LivingEntityRenderState {
	public Variant variant = Variant.RED;
}
