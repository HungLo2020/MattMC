package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class AllayRenderState extends ArmedEntityRenderState {
	public boolean isDancing;
	public boolean isSpinning;
	public float spinningProgress;
	public float holdingAnimationProgress;
}
