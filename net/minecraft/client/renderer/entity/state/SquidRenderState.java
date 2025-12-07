package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class SquidRenderState extends LivingEntityRenderState {
	public float tentacleAngle;
	public float xBodyRot;
	public float zBodyRot;
}
