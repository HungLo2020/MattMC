package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class TurtleRenderState extends LivingEntityRenderState {
	public boolean isOnLand;
	public boolean isLayingEgg;
	public boolean hasEgg;
}
