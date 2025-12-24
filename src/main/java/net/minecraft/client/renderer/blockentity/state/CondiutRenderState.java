package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class CondiutRenderState extends BlockEntityRenderState {
	public float animTime;
	public boolean isActive;
	public float activeRotation;
	public int animationPhase;
	public boolean isHunting;
}
