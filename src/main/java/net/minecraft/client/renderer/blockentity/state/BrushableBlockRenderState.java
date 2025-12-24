package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BrushableBlockRenderState extends BlockEntityRenderState {
	public ItemStackRenderState itemState = new ItemStackRenderState();
	public int dustProgress;
	@Nullable
	public Direction hitDirection;
}
