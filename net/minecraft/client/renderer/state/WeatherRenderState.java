package net.minecraft.client.renderer.state;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.WeatherEffectRenderer;

@Environment(EnvType.CLIENT)
public class WeatherRenderState {
	public final List<WeatherEffectRenderer.ColumnInstance> rainColumns = new ArrayList();
	public final List<WeatherEffectRenderer.ColumnInstance> snowColumns = new ArrayList();
	public float intensity;
	public int radius;

	public void reset() {
		this.rainColumns.clear();
		this.snowColumns.clear();
		this.intensity = 0.0F;
		this.radius = 0;
	}
}
