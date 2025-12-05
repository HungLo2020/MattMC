package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ItemStackRenderState;

@Environment(EnvType.CLIENT)
public class ShelfRenderState extends BlockEntityRenderState {
	public ItemStackRenderState[] items = new ItemStackRenderState[3];
	public boolean alignToBottom;
}
