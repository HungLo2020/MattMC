package net.distanthorizons.core.wrapperInterfaces.block;

import net.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import net.distanthorizons.core.util.LodUtil;

import java.awt.*;

/** A Minecraft version independent way of handling Blocks. */
public interface IBlockStateWrapper extends IDhApiBlockStateWrapper
{
	//=========//
	// methods //
	//=========//
	
	String getSerialString();
	
	/**
	 * Returning a value of 0 means the block is completely transparent. <br.
	 * Returning a value of 15 means the block is completely opaque.
	 * 
	 * @see LodUtil#BLOCK_FULLY_OPAQUE
	 * @see LodUtil#BLOCK_FULLY_TRANSPARENT
	 */
	int getOpacity();
	
	int getLightEmission();
	
	byte getMaterialId();
	
	boolean isBeaconBlock();
	/** IE a glass block that can affect the beacon beam color */
	boolean isBeaconTintBlock();
	/** 
	 * Returns true for any blocks that allow beacon beams to go through.
	 * IE: glass, stairs, bedrock, chests, end portal frames, carpet, cake 
	 */
	boolean allowsBeaconBeamPassage();
	/** 
	 * The blocks used by a beacon's base
	 * IE Iron, diamond, gold, etc. 
	 */
	boolean isBeaconBaseBlock();
	
	Color getMapColor();
	Color getBeaconTintColor();
	
}
