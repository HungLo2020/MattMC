package com.github.alexthe666.citadel.animation;

import com.github.alexthe666.citadel.server.message.AnimationMessage;
import net.minecraft.world.entity.Entity;
// TODO: Replace with Fabric
// import net.neoforged.neoforge.common.NeoForge;
// TODO: Replace with Fabric
// import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.ArrayUtils;

/**
 * @author iLexiconn
 * @since 1.0.0
 */
public enum AnimationHandler {
    INSTANCE;

    /**
     * Sends an animation packet to all clients, notifying them of a changed animation
     *
     * @param entity    the entity with an animation to be updated
     * @param animation the animation to be updated
     * @param <T>       the entity type
     */
    public <T extends Entity & IAnimatedEntity> void sendAnimationMessage(T entity, Animation animation) {
        // Citadel: 1.21 API - Level.isClientSide is now private, check instanceof
        if (entity.level() instanceof net.minecraft.client.multiplayer.ClientLevel) {
            return;
        }
        entity.setAnimation(animation);
        PacketDistributor.sendToAllPlayers(new AnimationMessage(entity.getId(), ArrayUtils.indexOf(entity.getAnimations(), animation)));
    }

    /**
     * Updates all animations for a given entity
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
                    AnimationEvent.Start event = new AnimationEvent.Start<>(entity, entity.getAnimation());
                    // Citadel: TODO - Wire to Fabric event system
                    // if (!NeoForge.EVENT_BUS.post(event).isCanceled()) {
                        this.sendAnimationMessage(entity, event.getAnimation());
                    // }
                }
                if (entity.getAnimationTick() < entity.getAnimation().getDuration()) {
                    entity.setAnimationTick(entity.getAnimationTick() + 1);
                    // Citadel: TODO - Wire to Fabric event system
                    // NeoForge.EVENT_BUS.post(new AnimationEvent.Tick<>(entity, entity.getAnimation(), entity.getAnimationTick()));
                }
                if (entity.getAnimationTick() == entity.getAnimation().getDuration()) {
                    entity.setAnimationTick(0);
                    entity.setAnimation(IAnimatedEntity.NO_ANIMATION);
                }
            }
        }
    }
}