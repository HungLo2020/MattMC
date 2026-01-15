package net.distanthorizons.core.api.external.methods.config.client;

import net.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import net.distanthorizons.api.interfaces.config.client.IDhApiNoiseTextureConfig;
import net.distanthorizons.core.config.api.DhApiConfigValue;
import net.distanthorizons.core.config.Config;

public class DhApiNoiseTextureConfig implements IDhApiNoiseTextureConfig
{
	public static DhApiNoiseTextureConfig INSTANCE = new DhApiNoiseTextureConfig();
	
	private DhApiNoiseTextureConfig() { }
	
	
	
	@Override
	public IDhApiConfigValue<Boolean> noiseEnabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.NoiseTexture.enableNoiseTexture); }
	
	@Override
	public IDhApiConfigValue<Integer> noiseSteps()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.NoiseTexture.noiseSteps); }
	
	@Override
	public IDhApiConfigValue<Double> noiseIntensity()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.NoiseTexture.noiseIntensity); }
	
	@Override
	public IDhApiConfigValue<Integer> noiseDropoff()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.NoiseTexture.noiseDropoff); }
	
}
