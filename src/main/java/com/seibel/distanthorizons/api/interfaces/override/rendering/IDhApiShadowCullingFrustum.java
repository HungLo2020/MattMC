package com.seibel.distanthorizons.api.interfaces.override.rendering;

/**
 * The culling frustum used during Distant Horizons' shadow pass
 * if another mod has enabled Distant Horizons' shadow
 * pass via the API. <br><br>
 * 
 * If no {@link IDhApiShadowCullingFrustum} is bound then culling
 * will not be done in the shadow pass.
 * 
 * @see IDhApiCullingFrustum
 * 
 * @author James Seibel
 * @version 2024-2-10
 * @since API 2.0.0
 */
public interface IDhApiShadowCullingFrustum extends IDhApiCullingFrustum
{
	// should be identical to the parent culling frustum
}
