package net.alexscaves.server.block;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class RelicheirusEggBlock extends DinosaurEggBlock {
    
    public RelicheirusEggBlock(BlockBehaviour.Properties properties) {
        super(properties, 14, 16);
    }
    
    @Override
    protected EntityType<?> getEntityType() {
        return EntityType.RELICHEIRUS;
    }
}
