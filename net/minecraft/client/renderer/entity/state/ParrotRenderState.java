package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.ParrotModel;
import net.minecraft.world.entity.animal.Parrot.Variant;

@Environment(EnvType.CLIENT)
public class ParrotRenderState extends LivingEntityRenderState {
	public Variant variant = Variant.RED_BLUE;
	public float flapAngle;
	public ParrotModel.Pose pose = ParrotModel.Pose.FLYING;
}
