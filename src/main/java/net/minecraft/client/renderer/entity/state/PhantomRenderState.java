package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class PhantomRenderState extends LivingEntityRenderState {
	public float flapTime;
	public int size;
}
