package net.distanthorizons.core.api.external.methods.config.client;

import net.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import net.distanthorizons.api.interfaces.config.client.IDhApiGenericRenderingConfig;
import net.distanthorizons.core.config.api.DhApiConfigValue;
import net.distanthorizons.core.config.Config;

public class DhApiGenericRenderingConfig implements IDhApiGenericRenderingConfig
{
	public static DhApiGenericRenderingConfig INSTANCE = new DhApiGenericRenderingConfig();
	
	private DhApiGenericRenderingConfig() { }
	
	
	
	@Override 
	public IDhApiConfigValue<Boolean> renderingEnabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.GenericRendering.enableGenericRendering); }
	@Override
	public IDhApiConfigValue<Boolean> beaconRenderingEnabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.GenericRendering.enableBeaconRendering); }
	@Override 
	public IDhApiConfigValue<Boolean> cloudRenderingEnabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.GenericRendering.enableCloudRendering); }
	
}
