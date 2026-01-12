package com.github.alexmodguy.alexscaves.server.entity;

// Stub - actual registration happens in EntityType.java
public class ACEntityRegistry {
    
    // Entity reference stubs
    public static class EntityHolder<T> {
        private final net.minecraft.world.entity.EntityType<T> type;
        public EntityHolder(net.minecraft.world.entity.EntityType<T> type) { this.type = type; }
        public net.minecraft.world.entity.EntityType<T> get() { return type; }
    }
    
    // Will be replaced with actual registration in EntityType.java
    public static final EntityHolder<com.github.alexmodguy.alexscaves.server.entity.living.SubterranodonEntity> SUBTERRANODON = 
        new EntityHolder<>(null); // TODO: register in EntityType.java
}

