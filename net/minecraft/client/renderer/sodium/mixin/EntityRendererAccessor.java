package net.minecraft.client.renderer.sodium.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Temporary accessor stub for EntityRenderer.
 * This will be replaced when mixins are inlined in Phase 4.
 */
public interface EntityRendererAccessor {
    AABB getCullingBox(Entity entity);
}
