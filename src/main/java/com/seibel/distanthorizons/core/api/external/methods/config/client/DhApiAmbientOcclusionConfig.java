package com.seibel.distanthorizons.core.api.external.methods.config.client;

import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiAmbientOcclusionConfig;
import com.seibel.distanthorizons.core.config.api.DhApiConfigValue;
import com.seibel.distanthorizons.core.config.Config;

public class DhApiAmbientOcclusionConfig implements IDhApiAmbientOcclusionConfig
{
	public static DhApiAmbientOcclusionConfig INSTANCE = new DhApiAmbientOcclusionConfig();
	
	private DhApiAmbientOcclusionConfig() { }
	
	
	
	
	@Override
	public IDhApiConfigValue<Boolean> enabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.Ssao.enableSsao); }
	
	@Override
	public IDhApiConfigValue<Integer> sampleCount()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.Ssao.sampleCount); }
	
	@Override
	public IDhApiConfigValue<Double> radius()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Ssao.radius); }
	
	@Override
	public IDhApiConfigValue<Double> strength()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Ssao.strength); }
	
	@Override
	public IDhApiConfigValue<Double> bias()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Ssao.bias); }
	
	@Override
	public IDhApiConfigValue<Double> minLight()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Ssao.minLight); }
	
	@Override
	public IDhApiConfigValue<Integer> blurRadius()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.Ssao.blurRadius); }
	
}
