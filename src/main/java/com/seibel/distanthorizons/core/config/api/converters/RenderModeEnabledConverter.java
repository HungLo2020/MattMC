package com.seibel.distanthorizons.core.config.api.converters;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.coreapi.interfaces.config.IConverter;

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
