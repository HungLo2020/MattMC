package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class CamelRenderState extends LivingEntityRenderState {
	public ItemStack saddle = ItemStack.EMPTY;
	public boolean isRidden;
	public float jumpCooldown;
	public final AnimationState sitAnimationState = new AnimationState();
	public final AnimationState sitPoseAnimationState = new AnimationState();
	public final AnimationState sitUpAnimationState = new AnimationState();
	public final AnimationState idleAnimationState = new AnimationState();
	public final AnimationState dashAnimationState = new AnimationState();
}
