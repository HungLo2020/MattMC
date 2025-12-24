package com.seibel.distanthorizons.core.render;

import net.distant_horizons.api.interfaces.override.rendering.IDhApiCullingFrustum;
import net.distant_horizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import net.distant_horizons.api.objects.math.DhApiMat4f;
import net.distant_horizons.coreapi.interfaces.dependencyInjection.IOverrideInjector;
import net.distant_horizons.core.util.math.Mat4f;

/** 
 * Dummy {@link IDhApiCullingFrustum} that allows everything through. <br> 
 * Useful when a frustum is required, but culling shouldn't be done.
 */
public class NeverCullFrustum implements IDhApiCullingFrustum, IDhApiShadowCullingFrustum
{
	//=============//
	// constructor //
	//=============//
	
	public NeverCullFrustum() { }
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public void update(int worldMinBlockY, int worldMaxBlockY, DhApiMat4f dhWorldViewProjection) { /* update isn't needed */ }
	
	@Override
	public boolean intersects(int lodBlockPosMinX, int lodBlockPosMinZ, int lodBlockWidth, int lodDetailLevel) { return true; }
	
	
	
	//=====================//
	// overridable methods //
	//=====================//
	
	@Override 
	public int getPriority() { return IOverrideInjector.CORE_PRIORITY; }
	
}
