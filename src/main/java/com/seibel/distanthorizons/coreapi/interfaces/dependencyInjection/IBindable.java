package com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection;

/**
 * Necessary for all singletons that can be dependency injected.
 *
 * @author James Seibel
 * @version 2022-7-16
 */
public interface IBindable
{
	/**
	 * Finish initializing this object. <br> <br>
	 *
	 * Generally this should just be used for getting other objects through
	 * dependency injection and is specifically designed to allow
	 * for circular references. <br><br>
	 *
	 * If no circular dependencies are required this method
	 * doesn't have to be implemented.
	 */
	default void finishDelayedSetup() { }
	
	/**
	 * Returns if this dependency has been setup yet. <Br> <Br>
	 *
	 * If this object doesn't require a delayed setup, this
	 * method doesn't have to be implemented and should always return true.
	 */
	default boolean getDelayedSetupComplete() { return true; }
	
}
