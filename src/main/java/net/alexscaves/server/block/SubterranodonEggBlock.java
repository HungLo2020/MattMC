package net.alexscaves.server.block;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class SubterranodonEggBlock extends MultipleDinosaurEggsBlock {
    
    public SubterranodonEggBlock(BlockBehaviour.Properties properties) {
        super(properties, 4);
    }
    
    @Override
    protected EntityType<?> getEntityType() {
        return EntityType.SUBTERRANODON;
    }
}
