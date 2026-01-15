package net.distanthorizons.api.objects.data;

import net.distanthorizons.api.enums.EDhApiDetailLevel;
import net.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import net.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;

/**
 * Holds a single datapoint of terrain data.
 *
 * @author James Seibel
 * @version 2025-11-15
 * @since API 1.0.0
 */
public class DhApiTerrainDataPoint
{
	/**
	 * 0 = block <br>
	 * 1 = 2x2 blocks <br>
	 * 2 = 4x4 blocks <br>
	 * 4 = chunk (16x16 blocks) <br>
	 * 9 = region (512x512 blocks) <br>
	 * 
	 * @see EDhApiDetailLevel
	 */
	public final byte detailLevel;
	
	public final int blockLightLevel;
	public final int skyLightLevel;
	/**
	 * An unsigned block position of the bottom vertex for this LOD relative to the level's minimum height. 
	 * Should be greater than or equal to 0.
	 */
	public final int bottomYBlockPos;
	public final int topYBlockPos;
	
	public final IDhApiBlockStateWrapper blockStateWrapper;
	public final IDhApiBiomeWrapper biomeWrapper;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	/** @since API 3.0.0 */
	public static DhApiTerrainDataPoint create(
			byte detailLevel,
			int blockLightLevel, int skyLightLevel,
			int bottomYBlockPos, int topYBlockPos,
			IDhApiBlockStateWrapper blockStateWrapper, IDhApiBiomeWrapper biomeWrapper
		)
	{ 
		return new DhApiTerrainDataPoint(
			detailLevel, blockLightLevel, skyLightLevel,
			bottomYBlockPos, topYBlockPos,
			blockStateWrapper, biomeWrapper); 
	}
	
	/** Only visible to internal DH methods */
	private DhApiTerrainDataPoint(
			byte detailLevel,
			int blockLightLevel, int skyLightLevel,
			int bottomYBlockPos, int topYBlockPos,
			IDhApiBlockStateWrapper blockStateWrapper, IDhApiBiomeWrapper biomeWrapper
		)
	{
		this.detailLevel = detailLevel;
		
		this.blockLightLevel = blockLightLevel;
		this.skyLightLevel = skyLightLevel;
		this.bottomYBlockPos = bottomYBlockPos;
		this.topYBlockPos = topYBlockPos;
		
		this.blockStateWrapper = blockStateWrapper;
		this.biomeWrapper = biomeWrapper;
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override 
	public String toString()
	{
		return "[Block:" + this.blockStateWrapper.getSerialString() + 
				",Biome:" + this.biomeWrapper.getName() + 
				",TopY:" + this.topYBlockPos + 
				",BottomY:" + this.bottomYBlockPos + 
				",BlockLight:" + this.blockLightLevel +
				",SkyLight:" + this.skyLightLevel + 
				"]";
	}
	
	
	
}
