package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class HappyGhastRenderState extends LivingEntityRenderState {
	public ItemStack bodyItem = ItemStack.EMPTY;
	public boolean isRidden;
	public boolean isLeashHolder;
}
