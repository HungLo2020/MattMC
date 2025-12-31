package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.AnimationState;

@Environment(EnvType.CLIENT)
public class SnifferRenderState extends LivingEntityRenderState {
	public boolean isSearching;
	public final AnimationState diggingAnimationState = new AnimationState();
	public final AnimationState sniffingAnimationState = new AnimationState();
	public final AnimationState risingAnimationState = new AnimationState();
	public final AnimationState feelingHappyAnimationState = new AnimationState();
	public final AnimationState scentingAnimationState = new AnimationState();
}
