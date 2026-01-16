package com.github.alexmodguy.alexscaves.server.block;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class GrottoceratopsEggBlock extends DinosaurEggBlock {
    
    public GrottoceratopsEggBlock(BlockBehaviour.Properties properties) {
        super(properties, 8, 10);
    }
    
    @Override
    protected EntityType<?> getEntityType() {
        return EntityType.GROTTOCERATOPS;
    }
}
