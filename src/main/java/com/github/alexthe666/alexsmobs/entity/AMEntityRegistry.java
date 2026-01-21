package com.github.alexthe666.alexsmobs.entity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

public class AMEntityRegistry {
    
    /**
     * Deferred holder stub for entity types
     */
    public static class DeferredEntityHolder {
        private final java.util.function.Supplier<EntityType<?>> entitySupplier;
        
        public DeferredEntityHolder(java.util.function.Supplier<EntityType<?>> entitySupplier) {
            this.entitySupplier = entitySupplier;
        }
        
        public EntityType<?> get() {
            return entitySupplier.get();
        }
    }
    
    // Mimic Octopus entity type - reference vanilla EntityType
    public static final DeferredEntityHolder MIMIC_OCTOPUS = new DeferredEntityHolder(() -> EntityType.MIMIC_OCTOPUS);
    
    /**
     * Helper method for spawn roll logic
     * @param rolls Number of spawn rolls to attempt
     * @param random Random source
     * @param spawnReason Spawn reason
     * @return true if spawn should succeed
     */
    public static boolean rollSpawn(int rolls, RandomSource random, EntitySpawnReason spawnReason) {
        if (spawnReason == EntitySpawnReason.SPAWNER || spawnReason == EntitySpawnReason.BUCKET || spawnReason == EntitySpawnReason.COMMAND) {
            return true;
        }
        for (int i = 0; i < rolls; i++) {
            if (random.nextInt(3) == 0) {
                return true;
            }
        }
        return false;
    }
}
