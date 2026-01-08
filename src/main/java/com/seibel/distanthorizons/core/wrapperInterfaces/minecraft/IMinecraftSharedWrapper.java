package com.seibel.distanthorizons.core.wrapperInterfaces.minecraft;

import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

import java.io.File;

public interface IMinecraftSharedWrapper extends IBindable
{
	boolean isDedicatedServer();
	
	File getInstallationDirectory();
	
	int getPlayerCount();
	
	
	
}
