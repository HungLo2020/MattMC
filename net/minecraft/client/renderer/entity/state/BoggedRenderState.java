package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class BoggedRenderState extends SkeletonRenderState {
	public boolean isSheared;
}
