package com.github.alexthe666.alexsmobs.block;

import com.github.alexthe666.alexsmobs.entity.AMEntityRegistry;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.SoundType;

public class BlockCaimanEgg extends BlockReptileEgg {
    
    public BlockCaimanEgg(BlockBehaviour.Properties properties) {
        super(() -> AMEntityRegistry.CAIMAN.get());
    }
    
    public BlockCaimanEgg() {
        super(() -> AMEntityRegistry.CAIMAN.get());
    }
}
