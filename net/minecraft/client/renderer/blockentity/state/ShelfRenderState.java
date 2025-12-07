package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.item.ItemStackRenderState;

@Environment(EnvType.CLIENT)
public class ShelfRenderState extends BlockEntityRenderState {
	public ItemStackRenderState[] items = new ItemStackRenderState[3];
	public boolean alignToBottom;
}
