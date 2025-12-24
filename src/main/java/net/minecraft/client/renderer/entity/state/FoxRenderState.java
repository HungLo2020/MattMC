package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.animal.Fox.Variant;

@Environment(EnvType.CLIENT)
public class FoxRenderState extends HoldingEntityRenderState {
	public float headRollAngle;
	public float crouchAmount;
	public boolean isCrouching;
	public boolean isSleeping;
	public boolean isSitting;
	public boolean isFaceplanted;
	public boolean isPouncing;
	public Variant variant = Variant.DEFAULT;
}
