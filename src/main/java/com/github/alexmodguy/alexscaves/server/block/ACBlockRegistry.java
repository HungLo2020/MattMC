package com.github.alexmodguy.alexscaves.server.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

// Stub - using vanilla blocks as placeholders
public class ACBlockRegistry {
    public static class BlockHolder {
        private final Block block;
        public BlockHolder(Block block) { this.block = block; }
        public Block get() { return block; }
    }
    
    public static final BlockHolder PEWEN_BRANCH = new BlockHolder(Blocks.OAK_LEAVES);
}
