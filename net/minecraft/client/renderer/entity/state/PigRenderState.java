package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.animal.PigVariant;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PigRenderState extends LivingEntityRenderState {
	public ItemStack saddle = ItemStack.EMPTY;
	@Nullable
	public PigVariant variant;
}
