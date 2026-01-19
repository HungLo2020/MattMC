package com.github.alexthe666.alexsmobs.item;

import net.minecraft.world.item.Item;

/**
 * Stub registry class for AlexsMobs items
 * Points to actual vanilla-registered items in Items.java
 */
public class AMItemRegistry {
    
    /**
     * Deferred holder stub that returns the actual vanilla-registered item
     */
    public static class DeferredHolder {
        private final java.util.function.Supplier<Item> itemSupplier;
        
        public DeferredHolder(java.util.function.Supplier<Item> itemSupplier) {
            this.itemSupplier = itemSupplier;
        }
        
        public Item get() {
            return itemSupplier.get();
        }
    }
    
    // Catfish bucket items - reference vanilla Items
    public static final DeferredHolder SMALL_CATFISH_BUCKET = new DeferredHolder(() -> net.minecraft.world.item.Items.SMALL_CATFISH_BUCKET);
    public static final DeferredHolder MEDIUM_CATFISH_BUCKET = new DeferredHolder(() -> net.minecraft.world.item.Items.MEDIUM_CATFISH_BUCKET);
    public static final DeferredHolder LARGE_CATFISH_BUCKET = new DeferredHolder(() -> net.minecraft.world.item.Items.LARGE_CATFISH_BUCKET);
    public static final DeferredHolder RAW_CATFISH = new DeferredHolder(() -> net.minecraft.world.item.Items.RAW_CATFISH);
    public static final DeferredHolder COOKED_CATFISH = new DeferredHolder(() -> net.minecraft.world.item.Items.COOKED_CATFISH);
}
