package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class RavagerRenderState extends LivingEntityRenderState {
	public float stunnedTicksRemaining;
	public float attackTicksRemaining;
	public float roarAnimation;
}
