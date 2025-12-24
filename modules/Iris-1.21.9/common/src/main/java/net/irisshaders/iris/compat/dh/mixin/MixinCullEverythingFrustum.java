package net.irisshaders.iris.compat.dh.mixin;

import net.distant_horizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import net.distant_horizons.api.objects.math.DhApiMat4f;
import net.iris.shadows.frustum.CullEverythingFrustum;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CullEverythingFrustum.class)
public class MixinCullEverythingFrustum implements IDhApiShadowCullingFrustum {
	@Override
	public void update(int worldMinBlockY, int worldMaxBlockY, DhApiMat4f worldViewProjection) {

	}

	@Override
	public boolean intersects(int lodBlockPosMinX, int lodBlockPosMinZ, int lodBlockWidth, int lodDetailLevel) {
		return false;
	}
}
