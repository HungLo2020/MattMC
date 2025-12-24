package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.AnimationState;

@Environment(EnvType.CLIENT)
public class CreakingRenderState extends LivingEntityRenderState {
	public final AnimationState invulnerabilityAnimationState = new AnimationState();
	public final AnimationState attackAnimationState = new AnimationState();
	public final AnimationState deathAnimationState = new AnimationState();
	public boolean eyesGlowing;
	public boolean canMove;
}
