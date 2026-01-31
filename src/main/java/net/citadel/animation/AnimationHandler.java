package net.citadel.animation;

import net.minecraft.world.entity.Entity;

/**
 * Animation handler - inlined Forge event system functionality
 * Manages entity animations without event bus by directly applying changes
 * Forge NeoForge.EVENT_BUS functionality inlined - no external event system
 * Forge PacketDistributor network sync removed - animations are local only
 * @author iLexiconn
 * @since 1.0.0
 */
public enum AnimationHandler {
    INSTANCE;

    /**
     * Sets animation on entity (server-side only)
     * Inlined: Forge's PacketDistributor.sendToAllPlayers removed
     * Network sync would need custom packet implementation if required
     *
     * @param entity    the entity with an animation to be updated
     * @param animation the animation to be updated
     * @param <T>       the entity type
     */
    public <T extends Entity & IAnimatedEntity> void sendAnimationMessage(T entity, Animation animation) {
        if (entity.level().isClientSide()) {
            return;
        }
        entity.setAnimation(animation);
        // Inlined: Removed Forge PacketDistributor.sendToAllPlayers(new AnimationMessage(...))
        // Network sync removed - animations run locally. Add custom packet if needed.
    }

    /**
     * Updates all animations for a given entity
     * Inlined: Forge EVENT_BUS.post() calls removed - events processed directly
     *
     * @param entity the entity with an animation to be updated
     * @param <T>    the entity type
     */
    public <T extends Entity & IAnimatedEntity> void updateAnimations(T entity) {
        if (entity.getAnimation() == null) {
            entity.setAnimation(IAnimatedEntity.NO_ANIMATION);
        } else {
            if (entity.getAnimation() != IAnimatedEntity.NO_ANIMATION) {
                if (entity.getAnimationTick() == 0) {
                    // Inlined: Forge EVENT_BUS.post(event) removed
                    // Create event for potential custom listeners
                    AnimationEvent.Start event = new AnimationEvent.Start<>(entity, entity.getAnimation());
                    // Direct check instead of event bus posting
                    if (!event.isCanceled()) {
                        this.sendAnimationMessage(entity, event.getAnimation());
                    }
                }
                if (entity.getAnimationTick() < entity.getAnimation().getDuration()) {
                    entity.setAnimationTick(entity.getAnimationTick() + 1);
                    // Inlined: Forge EVENT_BUS.post(new AnimationEvent.Tick(...)) removed
                    // Tick events no longer posted - add custom listener interface if needed
                }
                if (entity.getAnimationTick() == entity.getAnimation().getDuration()) {
                    entity.setAnimationTick(0);
                    entity.setAnimation(IAnimatedEntity.NO_ANIMATION);
                }
            }
        }
    }
}
