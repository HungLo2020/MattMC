package com.github.alexmodguy.alexscaves.server.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;

// Stub for dinosaur egg block
public class DinosaurEggBlock extends Block {
    public DinosaurEggBlock(Properties properties) {
        super(properties);
    }
    
    // Stub method - always returns true for now
    public boolean isProperHabitat(LevelReader level, BlockPos pos) {
        return true;
    }
}
