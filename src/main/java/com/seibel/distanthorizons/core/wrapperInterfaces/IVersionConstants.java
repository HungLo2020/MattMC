package com.seibel.distanthorizons.core.wrapperInterfaces;

import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * A singleton that contains variables specific to each version of Minecraft
 * which can be used to change how DH-Core runs. For example: After MC 1.17
 * blocks can be negative, which changes how we generate LODs.
 *
 * @author James Seibel
 * @version 3-5-2022
 */
public interface IVersionConstants extends IBindable
{
	String getMinecraftVersion();
	
}
