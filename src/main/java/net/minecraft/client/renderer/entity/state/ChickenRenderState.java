package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.animal.ChickenVariant;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ChickenRenderState extends LivingEntityRenderState {
	public float flap;
	public float flapSpeed;
	@Nullable
	public ChickenVariant variant;
}
