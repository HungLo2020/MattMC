package net.distanthorizons.api.interfaces.config.client;

import net.distanthorizons.api.enums.rendering.EDhApiFogColorMode;
import net.distanthorizons.api.enums.rendering.EDhApiFogDrawMode;
import net.distanthorizons.api.interfaces.config.IDhApiConfigGroup;
import net.distanthorizons.api.interfaces.config.IDhApiConfigValue;

/**
 * Distant Horizons' fog configuration. <br><br>
 *
 * Note: unless an option explicitly states that it modifies
 * Minecraft's vanilla rendering (like DisableVanillaFog)
 * these settings will only affect Distant horizons' fog.
 *
 * @author James Seibel
 * @version 2022-6-14
 * @since API 1.0.0
 */
public interface IDhApiFogConfig extends IDhApiConfigGroup
{
	
	//===============//
	// inner configs //
	//===============//
	
	/** The advanced fog config. */
	IDhApiFarFogConfig farFog();
	
	/** The height fog config. */
	IDhApiHeightFogConfig heightFog();
	
	//====================//
	// basic fog settings //
	//====================//
	
	/** 
	 * Used to enable/disable DH fog rendering.
	 * @deprecated since API 4.0.0 use {@link IDhApiFogConfig#enableDhFog}
	 */
	@Deprecated
	IDhApiConfigValue<EDhApiFogDrawMode> drawMode();
	/** 
	 * Used to enable/disable DH fog rendering. 
	 * 
	 * @since API 4.0.0
	 */
	IDhApiConfigValue<Boolean> enableDhFog();
	
	/** Can be used to enable support with mods that change vanilla MC's fog color. */
	IDhApiConfigValue<EDhApiFogColorMode> color();
	
	/**
	 * If enabled attempts to disable vanilla MC's fog on real chunks. <br>
	 * May not play nice with other fog editing mods.
	 * 
	 * @deprecated since API 4.0.0 use {@link IDhApiFogConfig#enableVanillaFog()}
	 */
	@Deprecated
	IDhApiConfigValue<Boolean> disableVanillaFog();
	/**
	 * If set to false DH will attempt to disable vanilla MC's fog on real chunks. <br>
	 * May not play nice with other fog editing mods.
	 *
	 * @since API 4.0.0
	 */
	IDhApiConfigValue<Boolean> enableVanillaFog();
	
	
	
}
