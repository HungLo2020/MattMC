package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.hooks.EntityRendererHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Sodium implementation of EntityRendererHooks.
 * Adds Sodium's entity visibility check to the frustum culling.
 */
public class SodiumEntityRendererHook implements EntityRendererHooks {
    @Override
    public <T extends Entity, S extends EntityRenderState> Boolean onEntityFrustumCheck(EntityRenderer<T, S> renderer, T entity, Frustum frustum, AABB aabb) {
        var sodiumRenderer = SodiumWorldRenderer.instanceNullable();
        
        if (sodiumRenderer == null) {
            return null; // No Sodium renderer, use default behavior
        }
        
        // Check Sodium's entity visibility before the frustum check
        if (!sodiumRenderer.isEntityVisible(renderer, entity)) {
            return false; // Sodium says not visible
        }
        
        return null; // Let the default frustum check proceed
    }
}
