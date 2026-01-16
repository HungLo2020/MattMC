package com.seibel.distanthorizons.api.interfaces.world;

/**
 * Used to interact with Distant Horizons' current world. <br>
 * A world is equivalent to a single server connection or a singleplayer world.
 *
 * @author James Seibel
 * @version 2024-9-27
 * @since API 1.0.0
 */
public interface IDhApiWorldProxy
{
	//===================//
	// getters / setters //
	//===================//
	
	/** Returns true if a world is loaded. */
	boolean worldLoaded();
	
	/**
	 * Defaults to false. <br>
	 * Setting this to true will prevent DH from updating or creating new LODs.
	 *
	 * @since API 4.0.0
	 * @see IDhApiWorldProxy#getReadOnly()
	 * @throws IllegalStateException if no world is loaded
	 */
	void setReadOnly(boolean readOnly) throws IllegalStateException;
	/**
	 * @since API 4.0.0
	 * @see IDhApiWorldProxy#setReadOnly(boolean)
	 * @throws IllegalStateException if no world is loaded
	 */
	boolean getReadOnly() throws IllegalStateException;
	
	
	
	//================//
	// level handlers //
	//================//
	
	/**
	 * In singleplayer this will return the level the player is currently in. <br>
	 * In multiplayer this will return null.
	 *
	 * @throws IllegalStateException if no world is loaded
	 */
	IDhApiLevelWrapper getSinglePlayerLevel() throws IllegalStateException;
	
	/** @throws IllegalStateException if no world is loaded */
	Iterable<IDhApiLevelWrapper> getAllLoadedLevelWrappers() throws IllegalStateException;
	
	/**
	 * In the case of servers running multiverse there may be multiple levels for the same dimensionType.
	 *
	 * @throws IllegalStateException if no world is loaded
	 */
	Iterable<IDhApiLevelWrapper> getAllLoadedLevelsForDimensionType(IDhApiDimensionTypeWrapper dimensionTypeWrapper) throws IllegalStateException;
	
	/**
	 * Returns any dimensions that have names containing the given string (case-insensitive). <br>
	 * In the case of servers running multiverse there may be multiple levels for the same dimensionType.
	 *
	 * @throws IllegalStateException if no world is loaded
	 */
	Iterable<IDhApiLevelWrapper> getAllLoadedLevelsWithDimensionNameLike(String dimensionName) throws IllegalStateException;
	
}
