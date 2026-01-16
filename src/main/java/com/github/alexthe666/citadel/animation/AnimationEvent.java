package com.github.alexthe666.citadel.animation;

import net.minecraft.world.entity.Entity;

/**
 * Inlined replacement for Forge Event system - simple base class for animation events
 * No actual event bus - just a data holder for animation state
 * Forge Event and ICancellableEvent functionality inlined directly
 */
public class AnimationEvent<T extends Entity & IAnimatedEntity> {
    protected Animation animation;
    private T entity;
    private boolean cancelled = false;

    AnimationEvent(T entity, Animation animation) {
        this.entity = entity;
        this.animation = animation;
    }

    public T getEntity() {
        return this.entity;
    }

    public Animation getAnimation() {
        return this.animation;
    }

    public boolean isCanceled() {
        return this.cancelled;
    }

    public void setCanceled(boolean cancel) {
        this.cancelled = cancel;
    }

    public static class Start<T extends Entity & IAnimatedEntity> extends AnimationEvent<T> {
        public Start(T entity, Animation animation) {
            super(entity, animation);
        }

        public void setAnimation(Animation animation) {
            this.animation = animation;
        }
    }

    public static class Tick<T extends Entity & IAnimatedEntity> extends AnimationEvent<T> {
        protected int tick;

        public Tick(T entity, Animation animation, int tick) {
            super(entity, animation);
            this.tick = tick;
        }

        public int getTick() {
            return this.tick;
        }
    }
}
