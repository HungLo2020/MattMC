package net.distanthorizons.core.config.api.converters;

import net.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import net.distanthorizons.coreapi.interfaces.config.IConverter;

/**
 * Used for simplifying the fake chunk rendering on/off setting.
 *
 * @author James Seibel
 * @version 2022-6-30
 */
public class RenderModeEnabledConverter implements IConverter<EDhApiRendererMode, Boolean>
{
	
	@Override 
	public EDhApiRendererMode convertToCoreType(Boolean renderingEnabled)
	{ return renderingEnabled ? EDhApiRendererMode.DEFAULT : EDhApiRendererMode.DISABLED; }
	
	@Override 
	public Boolean convertToApiType(EDhApiRendererMode renderingMode)
	{ return renderingMode == EDhApiRendererMode.DEFAULT; }
	
}
