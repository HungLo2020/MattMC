package net.minecraft.hooks;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Hook interface for EntityRenderer visibility checks.
 * Allows mods to add custom entity visibility logic.
 */
public interface EntityRendererHooks {
    /**
     * Called during entity frustum visibility check.
     * Allows mods to add additional visibility checks before the standard frustum check.
     * 
     * @param renderer The entity renderer
     * @param entity The entity being checked
     * @param frustum The frustum
     * @param aabb The entity's AABB
     * @return null to use default behavior, true to force visible, false to force hidden
     */
    @Nullable
    default <T extends Entity, S extends EntityRenderState> Boolean onEntityFrustumCheck(EntityRenderer<T, S> renderer, T entity, Frustum frustum, AABB aabb) {
        return null;
    }
}
