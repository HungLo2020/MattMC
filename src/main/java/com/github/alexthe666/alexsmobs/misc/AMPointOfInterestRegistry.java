package com.github.alexthe666.alexsmobs.misc;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;

/**
 * Stub registry for Point of Interest types used by AlexsMobs
 */
public class AMPointOfInterestRegistry {
    
    /**
     * Simple holder for POI types
     */
    public static class DeferredHolder {
        private final ResourceKey<PoiType> key;
        
        public DeferredHolder(ResourceKey<PoiType> key) {
            this.key = key;
        }
        
        public ResourceKey<PoiType> getKey() {
            return key;
        }
    }
    
    // Hummingbird feeder POI - just a stub for now
    public static final DeferredHolder HUMMINGBIRD_FEEDER = new DeferredHolder(
        ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, 
            ResourceLocation.withDefaultNamespace("hummingbird_feeder"))
    );
}
