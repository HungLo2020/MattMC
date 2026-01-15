package net.distanthorizons.core.api.external.methods.config.client;

import net.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import net.distanthorizons.api.interfaces.config.client.IDhApiMultiplayerConfig;
import net.distanthorizons.core.config.api.DhApiConfigValue;
import net.distanthorizons.core.config.Config;
import net.distanthorizons.api.enums.config.EDhApiServerFolderNameMode;

public class DhApiMultiplayerConfig implements IDhApiMultiplayerConfig
{
	public static DhApiMultiplayerConfig INSTANCE = new DhApiMultiplayerConfig();
	
	private DhApiMultiplayerConfig() { }
	
	
	
	public IDhApiConfigValue<EDhApiServerFolderNameMode> folderSavingMode()
	{ return new DhApiConfigValue<EDhApiServerFolderNameMode, EDhApiServerFolderNameMode>(Config.Client.Advanced.Multiplayer.serverFolderNameMode); }
	
}
