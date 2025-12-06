package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.AbstractIllager.IllagerArmPose;

@Environment(EnvType.CLIENT)
public class IllagerRenderState extends ArmedEntityRenderState {
	public boolean isRiding;
	public boolean isAggressive;
	public HumanoidArm mainArm = HumanoidArm.RIGHT;
	public IllagerArmPose armPose = IllagerArmPose.NEUTRAL;
	public int maxCrossbowChargeDuration;
	public int ticksUsingItem;
	public float attackAnim;
}
