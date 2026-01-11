package com.github.alexmodguy.alexscaves.server.misc;

import com.github.alexmodguy.alexscaves.AlexsCaves;

/**
 * In 1.21, DecoratedPotPatterns has completely changed.
 * Pot patterns are now data-driven and should be defined via JSON files.
 * 
 * Custom pot patterns need to be registered through data packs:
 * - Pattern data in: data/alexscaves/decorated_pot_pattern/*.json
 * - Textures in: assets/alexscaves/textures/entity/decorated_pot/*.png
 * 
 * The ITEM_TO_POT_TEXTURE map is now private and final, and cannot be modified at runtime.
 * Instead, use the data-driven approach.
 */
public class ACPotPatternRegistry {

    public static void init() {
        AlexsCaves.LOGGER.info("ACPotPatternRegistry: Decorated pot patterns are now data-driven in 1.21");
    }
    
    // Pot pattern registration is now handled through data packs
    // See: data/alexscaves/decorated_pot_pattern/
}
