package net.alexscaves.server.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PewenPinesBlock extends BushBlock {

    public static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

    public PewenPinesBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public boolean canSurvive(BlockState state, LevelReader levelReader, BlockPos pos) {
        BlockPos blockpos = pos.below();
        if (state.getBlock() == this) {
            return levelReader.getBlockState(blockpos).isFaceSturdy(levelReader, blockpos, Direction.UP);
        }
        return this.mayPlaceOn(levelReader.getBlockState(blockpos), levelReader, blockpos);
    }

    public VoxelShape getShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        Vec3 vec3 = state.getOffset(pos);
        return SHAPE.move(vec3.x, vec3.y, vec3.z);
    }

    @Override
    public float getMaxHorizontalOffset() {
        return 0.1F;
    }

}
