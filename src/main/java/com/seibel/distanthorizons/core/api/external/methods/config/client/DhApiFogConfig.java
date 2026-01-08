package com.seibel.distanthorizons.core.api.external.methods.config.client;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogColorMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogDrawMode;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFarFogConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFogConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiHeightFogConfig;
import com.seibel.distanthorizons.core.config.api.DhApiConfigValue;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.api.converters.ApiFogDrawModeConverter;

public class DhApiFogConfig implements IDhApiFogConfig
{
	public static DhApiFogConfig INSTANCE = new DhApiFogConfig();
	
	private DhApiFogConfig() { }
	
	
	
	//===============//
	// inner configs //
	//===============//
	
	@Override
	public IDhApiFarFogConfig farFog() { return DhApiFarFogConfig.INSTANCE; }
	@Override
	public IDhApiHeightFogConfig heightFog() { return DhApiHeightFogConfig.INSTANCE; }
	
	
	
	//====================//
	// basic fog settings //
	//====================//
	
	@Deprecated
	@Override
	public IDhApiConfigValue<EDhApiFogDrawMode> drawMode()
	{ return new DhApiConfigValue<Boolean, EDhApiFogDrawMode>(Config.Client.Advanced.Graphics.Fog.enableDhFog, new ApiFogDrawModeConverter()); }
	@Override
	public IDhApiConfigValue<Boolean> enableDhFog()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Fog.enableDhFog); }
	
	@Override
	public IDhApiConfigValue<EDhApiFogColorMode> color()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Fog.colorMode); }
	
	@Override
	@Deprecated
	public IDhApiConfigValue<Boolean> disableVanillaFog()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Fog.disableVanillaFog); }
	@Override
	public IDhApiConfigValue<Boolean> enableVanillaFog()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Fog.enableVanillaFog); }
	
	
	
}
