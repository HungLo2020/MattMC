package com.github.alexthe666.citadel.server.event;

import net.minecraft.world.entity.Entity;
// TODO: Replace with Fabric event system
// import net.neoforged.bus.api.Event;
// import net.neoforged.bus.api.ICancellableEvent;

// Temporary stub - TODO: Integrate with Fabric event system
public class EventChangeEntityTickRate {
    private Entity entity;
    private float targetTickRate;
    private boolean cancelled = false;

    public EventChangeEntityTickRate(Entity entity, float targetTickRate) {
        this.entity = entity;
        this.targetTickRate = targetTickRate;
    }

    public Entity getEntity() {
        return entity;
    }

    public float getTargetTickRate() {
        return targetTickRate;
    }
    
    public boolean isCancelled() {
        return cancelled;
    }
    
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
