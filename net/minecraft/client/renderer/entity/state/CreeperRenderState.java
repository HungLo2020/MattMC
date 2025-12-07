package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class CreeperRenderState extends LivingEntityRenderState {
	public float swelling;
	public boolean isPowered;
}
