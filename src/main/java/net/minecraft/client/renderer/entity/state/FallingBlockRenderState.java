package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.block.MovingBlockRenderState;

@Environment(EnvType.CLIENT)
public class FallingBlockRenderState extends EntityRenderState {
	public MovingBlockRenderState movingBlockRenderState = new MovingBlockRenderState();
}
