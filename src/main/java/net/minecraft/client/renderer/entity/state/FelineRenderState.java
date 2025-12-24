package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class FelineRenderState extends LivingEntityRenderState {
	public boolean isCrouching;
	public boolean isSprinting;
	public boolean isSitting;
	public float lieDownAmount;
	public float lieDownAmountTail;
	public float relaxStateOneAmount;
}
