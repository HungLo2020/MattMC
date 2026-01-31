package net.alexsmobs.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Stub class for Hummingbird Feeder block
 * This is a simplified version that only includes what the Hummingbird entity needs
 */
public class BlockHummingbirdFeeder extends Block {
    public static final IntegerProperty CONTENTS = IntegerProperty.create("contents", 0, 3);
    
    public BlockHummingbirdFeeder(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(CONTENTS, 0));
    }
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONTENTS);
    }
}
