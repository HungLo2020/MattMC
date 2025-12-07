package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class FishingHookRenderState extends EntityRenderState {
	public Vec3 lineOriginOffset = Vec3.ZERO;
}
