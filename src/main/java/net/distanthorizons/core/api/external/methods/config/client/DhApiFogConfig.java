package net.distanthorizons.core.api.external.methods.config.client;

import net.distanthorizons.api.enums.rendering.EDhApiFogColorMode;
import net.distanthorizons.api.enums.rendering.EDhApiFogDrawMode;
import net.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import net.distanthorizons.api.interfaces.config.client.IDhApiFarFogConfig;
import net.distanthorizons.api.interfaces.config.client.IDhApiFogConfig;
import net.distanthorizons.api.interfaces.config.client.IDhApiHeightFogConfig;
import net.distanthorizons.core.config.api.DhApiConfigValue;
import net.distanthorizons.core.config.Config;
import net.distanthorizons.core.config.api.converters.ApiFogDrawModeConverter;

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
