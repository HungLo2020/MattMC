package com.github.alexthe666.citadel.animation;

import net.minecraft.world.entity.Entity;
// TODO: Integrate with Fabric event system
// import net.neoforged.bus.api.Event;
// import net.neoforged.bus.api.ICancellableEvent;

public class AnimationEvent<T extends Entity & IAnimatedEntity> {
    protected Animation animation;
    private T entity;

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

    public static class Start<T extends Entity & IAnimatedEntity> extends AnimationEvent<T> {
        private boolean cancelled = false;
        
        public Start(T entity, Animation animation) {
            super(entity, animation);
        }

        public void setAnimation(Animation animation) {
            this.animation = animation;
        }
        
        public boolean isCancelled() {
            return cancelled;
        }
        
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
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