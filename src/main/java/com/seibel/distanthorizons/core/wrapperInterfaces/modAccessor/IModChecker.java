package com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor;

import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

import java.io.File;

/**
 * Checks if a mod is loaded
 *
 * @author coolGi
 * @version 3-5-2022
 */
public interface IModChecker extends IBindable
{
	/** Checks if a mod is loaded */
	boolean isModLoaded(String modid);
	
	File modLocation(String modid);
}
