package net.distanthorizons.core.wrapperInterfaces.minecraft;

import net.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

import java.io.File;

public interface IMinecraftSharedWrapper extends IBindable
{
	boolean isDedicatedServer();
	
	File getInstallationDirectory();
	
	int getPlayerCount();
	
	
	
}
