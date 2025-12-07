package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.animal.Rabbit.Variant;

@Environment(EnvType.CLIENT)
public class RabbitRenderState extends LivingEntityRenderState {
	public float jumpCompletion;
	public boolean isToast;
	public Variant variant = Variant.DEFAULT;
}
