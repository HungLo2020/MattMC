package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class BoatRenderState extends EntityRenderState {
	public float yRot;
	public int hurtDir;
	public float hurtTime;
	public float damageTime;
	public float bubbleAngle;
	public boolean isUnderWater;
	public float rowingTimeLeft;
	public float rowingTimeRight;
}
