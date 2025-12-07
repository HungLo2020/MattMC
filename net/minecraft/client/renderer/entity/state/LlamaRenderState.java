package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.animal.horse.Llama.Variant;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class LlamaRenderState extends LivingEntityRenderState {
	public Variant variant = Variant.DEFAULT;
	public boolean hasChest;
	public ItemStack bodyItem = ItemStack.EMPTY;
	public boolean isTraderLlama;
}
