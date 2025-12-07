package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.animal.CowVariant;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CowRenderState extends LivingEntityRenderState {
	@Nullable
	public CowVariant variant;
}
