package net.distanthorizons.common.wrappers;

import net.distanthorizons.core.wrapperInterfaces.IVersionConstants;
import net.minecraft.SharedConstants;

/**
 * @author James Seibel
 * @version 12-11-2021
 */
public class VersionConstants implements IVersionConstants
{
	public static final VersionConstants INSTANCE = new VersionConstants();
	
	
	private VersionConstants()
	{
		
	}
	
	
	@Override
	public String getMinecraftVersion()
	{
		// Use Minecraft's native version detection
		return SharedConstants.getCurrentVersion().name();
	}
	
}