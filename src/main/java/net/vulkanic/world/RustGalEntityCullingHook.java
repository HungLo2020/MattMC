package net.vulkanic.world;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.hooks.EntityRendererHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.sodium.client.SodiumClientMod;

/**
 * Frozen's Sodium entity culling for the camera pass: an entity whose culling
 * box touches no section the camera search visited is hidden before the
 * frustum test. Frozen skips this check in the shadow pass.
 */
public final class RustGalEntityCullingHook implements EntityRendererHooks {
	@Override
	public <T extends Entity, S extends EntityRenderState> Boolean onEntityFrustumCheck(EntityRenderer<T, S> renderer, T entity,
			Frustum frustum, AABB aabb) {
		if (!SodiumClientMod.options().performance.useEntityCulling) {
			return null;
		}
		Boolean visible = RustGalWholeFrameTerrainSource.isEntityVisible(renderer, entity);
		return visible == null || visible ? null : Boolean.FALSE;
	}

	@Override
	public boolean affectsShadowPass() {
		return false;
	}
}
