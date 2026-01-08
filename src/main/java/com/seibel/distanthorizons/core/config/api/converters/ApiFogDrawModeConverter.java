package com.seibel.distanthorizons.core.config.api.converters;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogDrawMode;
import com.seibel.distanthorizons.coreapi.interfaces.config.IConverter;

/**
 * Used for supporting the deprecated {@link EDhApiFogDrawMode}.
 *
 * @author James Seibel
 * @version 2024-10-12
 */
@Deprecated
public class ApiFogDrawModeConverter implements IConverter<Boolean, EDhApiFogDrawMode>
{
	
	@Override 
	public Boolean convertToCoreType(EDhApiFogDrawMode renderingMode)
	{
		if (renderingMode == EDhApiFogDrawMode.USE_OPTIFINE_SETTING)
		{
			return true;
		}
		else
		{
			return renderingMode == EDhApiFogDrawMode.FOG_ENABLED;	
		}
	}
	
	@Override 
	public EDhApiFogDrawMode convertToApiType(Boolean renderingEnabled)
	{ return renderingEnabled ? EDhApiFogDrawMode.FOG_ENABLED : EDhApiFogDrawMode.FOG_DISABLED; }
	
}
