package net.minecraft.client.renderer.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.renderer.DimensionSpecialEffects;

@Environment(EnvType.CLIENT)
public class SkyRenderState implements FabricRenderState {
	public DimensionSpecialEffects.SkyType skyType = DimensionSpecialEffects.SkyType.NONE;
	public boolean isSunriseOrSunset;
	public boolean shouldRenderDarkDisc;
	public float sunAngle;
	public float timeOfDay;
	public float rainBrightness;
	public float starBrightness;
	public int sunriseAndSunsetColor;
	public int moonPhase;
	public int skyColor;
	public float endFlashIntensity;
	public float endFlashXAngle;
	public float endFlashYAngle;

	public void reset() {
		this.skyType = DimensionSpecialEffects.SkyType.NONE;
	}
}
