package com.github.alexmodguy.alexscaves.server.entity;

/**
 * Provides enum extension parameters for custom MobCategory values.
 * These methods are referenced by enumextensions.json to provide constructor parameters.
 * 
 * MobCategory constructor: (int ordinal, String name, String serializedName, int max, boolean isFriendly, boolean isPersistent, int despawnDistance)
 * 
 * Original 1.20 Forge definitions:
 * CAVE_CREATURE = MobCategory.create("cave_creature", "alexscaves:cave_creature", 10, true, true, 128)
 * DEEP_SEA_CREATURE = MobCategory.create("deep_sea_creature", "alexscaves:deep_sea_creature", 20, true, false, 128)
 */
@SuppressWarnings("unused") // Referenced by enumextensions.json
public class ACMobCategoryEnumParams {

    /**
     * Gets parameters for ALEXSCAVES_CAVE_CREATURE MobCategory.
     * Parameters: name="alexscaves:cave_creature", max=10, isFriendly=true, isPersistent=true, despawnDistance=128
     */
    public static Object getCaveCreatureParameter(int idx, Class<?> type) {
        return switch (idx) {
            case 0 -> "alexscaves:cave_creature"; // name (also used as serializedName)
            case 1 -> 10;                          // max spawn count
            case 2 -> true;                        // isFriendly
            case 3 -> true;                        // isPersistent  
            case 4 -> 128;                         // despawnDistance
            default -> throw new IllegalArgumentException("Unexpected parameter index: " + idx);
        };
    }

    /**
     * Gets parameters for ALEXSCAVES_DEEP_SEA_CREATURE MobCategory.
     * Parameters: name="alexscaves:deep_sea_creature", max=20, isFriendly=true, isPersistent=false, despawnDistance=128
     */
    public static Object getDeepSeaCreatureParameter(int idx, Class<?> type) {
        return switch (idx) {
            case 0 -> "alexscaves:deep_sea_creature"; // name (also used as serializedName)
            case 1 -> 20;                              // max spawn count
            case 2 -> true;                            // isFriendly
            case 3 -> false;                           // isPersistent (different from cave creature)
            case 4 -> 128;                             // despawnDistance
            default -> throw new IllegalArgumentException("Unexpected parameter index: " + idx);
        };
    }
}
