package com.github.alexthe666.alexsmobs.entity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

public class AMEntityRegistry {
    
    /**
     * Deferred holder stub for entity types
     */
    public static class DeferredEntityHolder implements java.util.function.Supplier<EntityType<?>> {
        private final java.util.function.Supplier<EntityType<?>> entitySupplier;
        
        public DeferredEntityHolder(java.util.function.Supplier<EntityType<?>> entitySupplier) {
            this.entitySupplier = entitySupplier;
        }
        
        @Override
        public EntityType<?> get() {
            return entitySupplier.get();
        }
    }
    
    // Mimic Octopus entity type - reference vanilla EntityType
    public static final DeferredEntityHolder MIMIC_OCTOPUS = new DeferredEntityHolder(() -> EntityType.MIMIC_OCTOPUS);
    public static final DeferredEntityHolder MUDSKIPPER = new DeferredEntityHolder(() -> EntityType.MUDSKIPPER);
    public static final DeferredEntityHolder MUD_BALL = new DeferredEntityHolder(() -> EntityType.MUD_BALL);
    public static final DeferredEntityHolder SEAGULL = new DeferredEntityHolder(() -> EntityType.SEAGULL);
    public static final DeferredEntityHolder SHOEBILL = new DeferredEntityHolder(() -> EntityType.SHOEBILL);
    public static final DeferredEntityHolder TOUCAN = new DeferredEntityHolder(() -> EntityType.TOUCAN);
    public static final DeferredEntityHolder ANTEATER = new DeferredEntityHolder(() -> EntityType.ANTEATER);
    public static final DeferredEntityHolder CAIMAN = new DeferredEntityHolder(() -> EntityType.CAIMAN);
    public static final DeferredEntityHolder CAPUCHIN_MONKEY = new DeferredEntityHolder(() -> EntityType.CAPUCHIN_MONKEY);
    public static final DeferredEntityHolder TOSSED_ITEM = new DeferredEntityHolder(() -> EntityType.TOSSED_ITEM);
    public static final DeferredEntityHolder COSMAW = new DeferredEntityHolder(() -> EntityType.COSMAW);
    public static final DeferredEntityHolder ELEPHANT = new DeferredEntityHolder(() -> EntityType.ELEPHANT);
    public static final DeferredEntityHolder EMU = new DeferredEntityHolder(() -> EntityType.EMU);
    public static final DeferredEntityHolder EMU_EGG = new DeferredEntityHolder(() -> EntityType.EMU_EGG);
    
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
    
    /**
     * Build predicate from tag
     * @param tag The entity tag to check
     * @return Selector for targeting
     */
    public static net.minecraft.world.entity.ai.targeting.TargetingConditions.Selector buildPredicateFromTag(net.minecraft.tags.TagKey<EntityType<?>> tag) {
        return (living, serverLevel) -> living != null && living.getType().is(tag);
    }
}
