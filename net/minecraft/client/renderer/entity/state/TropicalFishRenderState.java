package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.animal.TropicalFish.Pattern;

@Environment(EnvType.CLIENT)
public class TropicalFishRenderState extends LivingEntityRenderState {
	public Pattern pattern = Pattern.FLOPPER;
	public int baseColor = -1;
	public int patternColor = -1;
}
