package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PistonHeadRenderState extends BlockEntityRenderState {
	@Nullable
	public MovingBlockRenderState block;
	@Nullable
	public MovingBlockRenderState base;
	public float xOffset;
	public float yOffset;
	public float zOffset;
}
