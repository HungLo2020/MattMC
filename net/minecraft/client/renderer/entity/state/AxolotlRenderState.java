package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.animal.axolotl.Axolotl.Variant;

@Environment(EnvType.CLIENT)
public class AxolotlRenderState extends LivingEntityRenderState {
	public Variant variant = Variant.DEFAULT;
	public float playingDeadFactor;
	public float movingFactor;
	public float inWaterFactor = 1.0F;
	public float onGroundFactor;
}
