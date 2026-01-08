package com.seibel.distanthorizons.core.api.external.methods.config.client;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogFalloff;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFarFogConfig;
import com.seibel.distanthorizons.core.config.api.DhApiConfigValue;
import com.seibel.distanthorizons.core.config.Config;

public class DhApiFarFogConfig implements IDhApiFarFogConfig
{
	public static DhApiFarFogConfig INSTANCE = new DhApiFarFogConfig();
	
	private DhApiFarFogConfig() { }
	
	
	
	@Override
	public IDhApiConfigValue<Double> farFogStartDistance()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.farFogStart); }
	
	@Override
	public IDhApiConfigValue<Double> farFogEndDistance()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.farFogEnd); }
	
	@Override
	public IDhApiConfigValue<Double> farFogMinThickness()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.farFogMin); }
	
	@Override
	public IDhApiConfigValue<Double> farFogMaxThickness()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.farFogMax); }
	
	@Override
	public IDhApiConfigValue<EDhApiFogFalloff> farFogFalloff()
	{ return new DhApiConfigValue<EDhApiFogFalloff, EDhApiFogFalloff>(Config.Client.Advanced.Graphics.Fog.farFogFalloff); }
	
	@Override
	public IDhApiConfigValue<Double> farFogDensity()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.farFogDensity); }
	
}
