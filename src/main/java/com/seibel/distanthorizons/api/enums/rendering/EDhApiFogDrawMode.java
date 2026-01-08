package com.seibel.distanthorizons.api.enums.rendering;

/**
 * USE_OPTIFINE_FOG_SETTING, <br>
 * FOG_ENABLED, <br>
 * FOG_DISABLED <br>
 *
 * @deprecated since API 4.0.0 since {@link EDhApiFogDrawMode#USE_OPTIFINE_SETTING} is no longer supported.
 * 
 * @author James Seibel
 * @since API 2.0.0
 * @version 2022-6-2
 */
@Deprecated
public enum EDhApiFogDrawMode
{
	/**
	 * Use whatever Fog setting Optifine is using.
	 * If Optifine isn't installed this defaults to {@link EDhApiFogDrawMode#FOG_ENABLED}.
	 * 
	 * @deprecated Since API 4.0.0 is equivalent to {@link EDhApiFogDrawMode#FOG_ENABLED}
	 */
	@Deprecated
	USE_OPTIFINE_SETTING,
	
	FOG_ENABLED,
	FOG_DISABLED;
	
}
