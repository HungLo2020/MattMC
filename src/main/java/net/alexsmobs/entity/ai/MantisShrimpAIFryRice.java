package net.alexsmobs.entity.ai;

import net.alexsmobs.entity.EntityMantisShrimp;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class MantisShrimpAIFryRice extends MoveToBlockGoal {

    private final EntityMantisShrimp mantisShrimp;
    private boolean wasLitPrior = false;
    private int cookingTicks = 0;

    public MantisShrimpAIFryRice(EntityMantisShrimp entityMantisShrimp) {
        super(entityMantisShrimp, 1, 8);
        this.mantisShrimp = entityMantisShrimp;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public void stop(){
        cookingTicks = 0;
        if(!wasLitPrior){
            BlockPos blockpos = this.getMoveToTarget().below();
            BlockState state = mantisShrimp.level().getBlockState(blockpos);
            if(state.getBlock() instanceof AbstractFurnaceBlock && !wasLitPrior){
                mantisShrimp.level().setBlockAndUpdate(blockpos, state.setValue(AbstractFurnaceBlock.LIT, false));
            }
        }
        super.stop();
    }

    public void tick() {
        super.tick();
        BlockPos blockpos = this.getMoveToTarget().below();
        if(this.isReachedTarget()){
            BlockState state = mantisShrimp.level().getBlockState(blockpos);
            if(mantisShrimp.punchProgress == 0){
                mantisShrimp.punch();
            }
            if(state.getBlock() instanceof AbstractFurnaceBlock && !wasLitPrior){
                mantisShrimp.level().setBlockAndUpdate(blockpos, state.setValue(AbstractFurnaceBlock.LIT, true));
            }
            cookingTicks++;
            if(cookingTicks > 200){
                cookingTicks = 0;
                // Stubbed out - fried rice item not available
                // ItemStack rice = new ItemStack(AMItemRegistry.SHRIMP_FRIED_RICE.get());
                // rice.setCount(mantisShrimp.getMainHandItem().getCount());
                // mantisShrimp.setItemInHand(InteractionHand.MAIN_HAND, rice);

            }
        }else{
            cookingTicks = 0;
        }
    }

    @Override
    public boolean canUse() {
        // Simplified - check for wheat item (rice doesn't exist)
        return this.mantisShrimp.getMainHandItem().getItem() == Items.WHEAT && !mantisShrimp.isSitting() && super.canUse();
    }

    public boolean canContinueToUse() {
        // Simplified - check for wheat item (rice doesn't exist)
        return this.mantisShrimp.getMainHandItem().getItem() == Items.WHEAT && !mantisShrimp.isSitting() && super.canContinueToUse();
    }

    public double acceptedDistance() {
        return 3.9F;
    }

    @Override
    protected boolean isValidTarget(LevelReader worldIn, BlockPos pos) {
        if (!worldIn.isEmptyBlock(pos.above())) {
            return false;
        } else {
            BlockState blockstate = worldIn.getBlockState(pos);
            if(blockstate.getBlock() instanceof AbstractFurnaceBlock){
                wasLitPrior = blockstate.getValue(AbstractFurnaceBlock.LIT);
                return true;
            }
            return blockstate.is(BlockTags.CAMPFIRES);
        }
    }


}
