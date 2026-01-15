package net.distanthorizons.api.interfaces.config.client;

import net.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import net.distanthorizons.api.enums.config.EDhApiServerFolderNameMode;
import net.distanthorizons.api.interfaces.config.IDhApiConfigGroup;

/**
 * Distant Horizons' client-side multiplayer configuration.
 *
 * @author James Seibel
 * @version 2023-6-14
 * @since API 1.0.0
 */
public interface IDhApiMultiplayerConfig extends IDhApiConfigGroup
{
	
	/**
	 * Defines how multiplayer server folders are named. <br>
	 * Note: Changing this while connected to a multiplayer world will cause undefined behavior!
	 */
	IDhApiConfigValue<EDhApiServerFolderNameMode> folderSavingMode();
	
}
