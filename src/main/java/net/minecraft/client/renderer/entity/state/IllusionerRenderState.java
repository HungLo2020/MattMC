package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class IllusionerRenderState extends IllagerRenderState {
	public Vec3[] illusionOffsets = new Vec3[0];
	public boolean isCastingSpell;
}
