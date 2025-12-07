package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class BeeRenderState extends LivingEntityRenderState {
	public float rollAmount;
	public boolean hasStinger = true;
	public boolean isOnGround;
	public boolean isAngry;
	public boolean hasNectar;
}
