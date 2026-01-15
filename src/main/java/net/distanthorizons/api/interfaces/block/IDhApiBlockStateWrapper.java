package net.distanthorizons.api.interfaces.block;

import net.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import net.distanthorizons.api.interfaces.IDhApiUnsafeWrapper;

/**
 * A Minecraft version independent way of handling Blocks.
 *
 * @author James Seibel
 * @version 2023-6-11
 * @since API 1.0.0
 */
public interface IDhApiBlockStateWrapper extends IDhApiUnsafeWrapper
{
	/** @since API 1.0.0 */
	boolean isAir();
	
	/** @since API 1.0.0 */
	boolean isSolid();
	/** @since API 1.0.0 */
	boolean isLiquid();
	
	/**
	 * Returns the full serialized form of the given block
	 * as defined by DH's serialization methods.
	 * @since API 3.0.0 
	 */
	String getSerialString();
	/**
	 * Returns the byte value representing the {@link EDhApiBlockMaterial} enum.
	 * @see EDhApiBlockMaterial 
	 * @since API 3.0.0 
	 */
	byte getMaterialId();
	
}
