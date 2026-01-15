package net.distanthorizons.api.interfaces.override.rendering;

import net.distanthorizons.api.enums.EDhApiDetailLevel;
import net.distanthorizons.api.interfaces.override.IDhApiOverrideable;
import net.distanthorizons.api.objects.math.DhApiMat4f;

/**
 * Used to determine if a LOD should be rendered or is outside the
 * user's field of view.
 * 
 * @author James Seibel
 * @version 2024-2-6
 * @since API 2.0.0
 */
public interface IDhApiCullingFrustum extends IDhApiOverrideable
{
	
	/** 
	 * Called before a render pass is done.
	 * 
	 * @param worldMinBlockY the lowest block position this level allows.
	 * @param worldMaxBlockY the highest block position this level allows.
	 * @param worldViewProjection the projection matrix used in this render pass.
	 */
	void update(int worldMinBlockY, int worldMaxBlockY, DhApiMat4f worldViewProjection);
	
	/** 
	 * returns true if the LOD bounds intersect this frustum 
	 * 
	 * @param lodBlockPosMinX this LOD's starting block X position closest to negative infinity
	 * @param lodBlockPosMinZ this LOD's starting block Z position closest to negative infinity
	 * @param lodBlockWidth this LOD's width in blocks
	 * @param lodDetailLevel this LOD's detail level
	 * 
	 * @see EDhApiDetailLevel
	 */
	boolean intersects(int lodBlockPosMinX, int lodBlockPosMinZ, int lodBlockWidth, int lodDetailLevel);
	
}
