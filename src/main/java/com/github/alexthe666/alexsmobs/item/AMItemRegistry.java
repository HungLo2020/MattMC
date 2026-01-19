package com.github.alexthe666.alexsmobs.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Stub registry class for AlexsMobs items
 * Actual items are registered in vanilla Items.java
 */
public class AMItemRegistry {
    
    /**
     * Deferred holder stub that returns the actual vanilla-registered item
     */
    public static class DeferredHolder {
        private final Item item;
        
        public DeferredHolder(Item item) {
            this.item = item;
        }
        
        public Item get() {
            return item;
        }
    }
    
    // Catfish bucket items - these will reference vanilla Items after registration
    public static final DeferredHolder SMALL_CATFISH_BUCKET = new DeferredHolder(Items.AIR); // Will be updated after Items.java registration
    public static final DeferredHolder MEDIUM_CATFISH_BUCKET = new DeferredHolder(Items.AIR); // Will be updated after Items.java registration
    public static final DeferredHolder LARGE_CATFISH_BUCKET = new DeferredHolder(Items.AIR); // Will be updated after Items.java registration
    public static final DeferredHolder RAW_CATFISH = new DeferredHolder(Items.AIR); // Will be updated after Items.java registration
    public static final DeferredHolder COOKED_CATFISH = new DeferredHolder(Items.AIR); // Will be updated after Items.java registration
}
