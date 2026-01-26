package com.github.alexthe666.alexsmobs.block;

import net.minecraft.world.level.block.Block;

/**
 * Stub registry class for AlexsMobs blocks
 * Points to actual vanilla-registered blocks in Blocks.java
 */
public class AMBlockRegistry {
    
    /**
     * Deferred holder stub that returns the actual vanilla-registered block
     */
    public static class DeferredHolder {
        private final java.util.function.Supplier<Block> blockSupplier;
        
        public DeferredHolder(java.util.function.Supplier<Block> blockSupplier) {
            this.blockSupplier = blockSupplier;
        }
        
        public Block get() {
            return blockSupplier.get();
        }
    }
    
    // Skunk Spray block - reference vanilla Blocks
    public static final DeferredHolder SKUNK_SPRAY = new DeferredHolder(() -> net.minecraft.world.level.block.Blocks.SKUNK_SPRAY);
    
    // Hummingbird Feeder block - reference vanilla Blocks
    public static final DeferredHolder HUMMINGBIRD_FEEDER = new DeferredHolder(() -> net.minecraft.world.level.block.Blocks.HUMMINGBIRD_FEEDER);
    
    // Leafcutter Ant Hill blocks - reference vanilla Blocks
    public static final DeferredHolder LEAFCUTTER_ANTHILL = new DeferredHolder(() -> net.minecraft.world.level.block.Blocks.LEAFCUTTER_ANTHILL);
    public static final DeferredHolder LEAFCUTTER_ANT_CHAMBER = new DeferredHolder(() -> net.minecraft.world.level.block.Blocks.LEAFCUTTER_ANT_CHAMBER);
}
