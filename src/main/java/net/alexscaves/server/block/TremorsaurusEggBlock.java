package net.alexscaves.server.block;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class TremorsaurusEggBlock extends DinosaurEggBlock {
    
    public TremorsaurusEggBlock(BlockBehaviour.Properties properties) {
        super(properties, 10, 16);
    }
    
    @Override
    protected EntityType<?> getEntityType() {
        return EntityType.TREMORSAURUS;
    }
}
