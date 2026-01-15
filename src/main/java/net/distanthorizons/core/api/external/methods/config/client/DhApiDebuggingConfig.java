package net.distanthorizons.core.api.external.methods.config.client;

import net.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import net.distanthorizons.api.interfaces.config.client.IDhApiDebuggingConfig;
import net.distanthorizons.core.config.api.DhApiConfigValue;
import net.distanthorizons.core.config.Config;
import net.distanthorizons.api.enums.rendering.EDhApiDebugRendering;

public class DhApiDebuggingConfig implements IDhApiDebuggingConfig
{
	public static DhApiDebuggingConfig INSTANCE = new DhApiDebuggingConfig();
	
	private DhApiDebuggingConfig() { }
	
	
	
	public IDhApiConfigValue<EDhApiDebugRendering> debugRendering()
	{ return new DhApiConfigValue<EDhApiDebugRendering, EDhApiDebugRendering>(Config.Client.Advanced.Debugging.debugRendering); }
	
	public IDhApiConfigValue<Boolean> debugKeybindings()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Debugging.enableDebugKeybindings); }
	
	public IDhApiConfigValue<Boolean> renderWireframe()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Debugging.renderWireframe); }
	
	public IDhApiConfigValue<Boolean> lodOnlyMode()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Debugging.lodOnlyMode); }
	
	public IDhApiConfigValue<Boolean> debugWireframeRendering()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Debugging.DebugWireframe.enableRendering); }
	
}
