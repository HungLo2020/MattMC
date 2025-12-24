package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class GoatRenderState extends LivingEntityRenderState {
	public boolean hasLeftHorn = true;
	public boolean hasRightHorn = true;
	public float rammingXHeadRot;
}
