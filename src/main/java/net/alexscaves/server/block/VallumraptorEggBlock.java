package net.alexscaves.server.block;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class VallumraptorEggBlock extends MultipleDinosaurEggsBlock {
    
    public VallumraptorEggBlock(BlockBehaviour.Properties properties) {
        super(properties, 4);
    }
    
    @Override
    protected EntityType<?> getEntityType() {
        return EntityType.VALLUMRAPTOR;
    }
}
