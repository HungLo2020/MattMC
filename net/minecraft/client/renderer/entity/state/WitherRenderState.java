package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class WitherRenderState extends LivingEntityRenderState {
	public float[] xHeadRots = new float[2];
	public float[] yHeadRots = new float[2];
	public float invulnerableTicks;
	public boolean isPowered;
}
