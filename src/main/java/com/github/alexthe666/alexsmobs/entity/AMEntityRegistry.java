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
    
    // Entity type references
    public static final DeferredEntityHolder ANACONDA = new DeferredEntityHolder(() -> EntityType.ANACONDA);
    public static final DeferredEntityHolder ANACONDA_PART = new DeferredEntityHolder(() -> EntityType.ANACONDA_PART);
    public static final DeferredEntityHolder MIMIC_OCTOPUS = new DeferredEntityHolder(() -> EntityType.MIMIC_OCTOPUS);
    public static final DeferredEntityHolder MUDSKIPPER = new DeferredEntityHolder(() -> EntityType.MUDSKIPPER);
    public static final DeferredEntityHolder MUD_BALL = new DeferredEntityHolder(() -> EntityType.MUD_BALL);
    public static final DeferredEntityHolder SEAGULL = new DeferredEntityHolder(() -> EntityType.SEAGULL);
    public static final DeferredEntityHolder SHOEBILL = new DeferredEntityHolder(() -> EntityType.SHOEBILL);
    public static final DeferredEntityHolder SKUNK = new DeferredEntityHolder(() -> EntityType.SKUNK);
    public static final DeferredEntityHolder TOUCAN = new DeferredEntityHolder(() -> EntityType.TOUCAN);
    public static final DeferredEntityHolder ANTEATER = new DeferredEntityHolder(() -> EntityType.ANTEATER);
    public static final DeferredEntityHolder CAIMAN = new DeferredEntityHolder(() -> EntityType.CAIMAN);
    public static final DeferredEntityHolder CAPUCHIN_MONKEY = new DeferredEntityHolder(() -> EntityType.CAPUCHIN_MONKEY);
    public static final DeferredEntityHolder TOSSED_ITEM = new DeferredEntityHolder(() -> EntityType.TOSSED_ITEM);
    public static final DeferredEntityHolder COSMAW = new DeferredEntityHolder(() -> EntityType.COSMAW);
    public static final DeferredEntityHolder ELEPHANT = new DeferredEntityHolder(() -> EntityType.ELEPHANT);
    public static final DeferredEntityHolder EMU = new DeferredEntityHolder(() -> EntityType.EMU);
    public static final DeferredEntityHolder EMU_EGG = new DeferredEntityHolder(() -> EntityType.EMU_EGG);
    public static final DeferredEntityHolder GELADA_MONKEY = new DeferredEntityHolder(() -> EntityType.GELADA_MONKEY);
    public static final DeferredEntityHolder LEAFCUTTER_ANT = new DeferredEntityHolder(() -> EntityType.LEAFCUTTER_ANT);
    public static final DeferredEntityHolder MANTIS_SHRIMP = new DeferredEntityHolder(() -> EntityType.MANTIS_SHRIMP);
    public static final DeferredEntityHolder RATTLESNAKE = new DeferredEntityHolder(() -> EntityType.RATTLESNAKE);
    public static final DeferredEntityHolder RHINOCEROS = new DeferredEntityHolder(() -> EntityType.RHINOCEROS);
    public static final DeferredEntityHolder SNOW_LEOPARD = new DeferredEntityHolder(() -> EntityType.SNOW_LEOPARD);
    public static final DeferredEntityHolder TASMANIAN_DEVIL = new DeferredEntityHolder(() -> EntityType.TASMANIAN_DEVIL);
    public static final DeferredEntityHolder UNDERMINER = new DeferredEntityHolder(() -> EntityType.UNDERMINER);
    public static final DeferredEntityHolder WARPED_TOAD = new DeferredEntityHolder(() -> EntityType.WARPED_TOAD);
    public static final DeferredEntityHolder KOMODO_DRAGON = new DeferredEntityHolder(() -> EntityType.KOMODO_DRAGON);
    
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
