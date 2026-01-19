package com.github.alexthe666.alexsmobs.entity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;

public class AMEntityRegistry {
    
    /**
     * Helper method for spawn roll logic
     * @param rolls Number of spawn rolls to attempt
     * @param random Random source
     * @param spawnReason Spawn reason
     * @return true if spawn should succeed
     */
    public static boolean rollSpawn(int rolls, RandomSource random, EntitySpawnReason spawnReason) {
        if (spawnReason == EntitySpawnReason.SPAWNER || spawnReason == EntitySpawnReason.SPAWN_EGG) {
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
