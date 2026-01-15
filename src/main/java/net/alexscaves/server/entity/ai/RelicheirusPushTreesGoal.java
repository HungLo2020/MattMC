package net.alexscaves.server.entity.ai;

import net.alexscaves.server.entity.living.RelicheirusEntity;
import net.citadel.animation.IAnimatedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class RelicheirusPushTreesGoal extends MoveToBlockGoal {

    private RelicheirusEntity relicheirus;
    private boolean madeTreeEntity = false;

    public RelicheirusPushTreesGoal(RelicheirusEntity relicheirus, int range) {
        super(relicheirus, 1.0F, range, 6);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        this.relicheirus = relicheirus;
    }

    public boolean canUse() {
        return relicheirus.getPushingTreesFor() > 0 && !relicheirus.isBaby() && super.canUse();
    }

    public boolean canContinueToUse() {
        return super.canContinueToUse() && !madeTreeEntity;
    }

    protected int nextStartTick(PathfinderMob mob) {
        return reducedTickDelay(10 + relicheirus.getRandom().nextInt(20));
    }

    public double acceptedDistance() {
        return 4.0D;
    }

    @Override
    protected boolean isReachedTarget() {
        BlockPos target = getMoveToTarget();
        return target != null && relicheirus.distanceToSqr(target.getX() + 0.5F, relicheirus.getY(), target.getZ() + 0.5F) < acceptedDistance();
    }

    protected BlockPos getMoveToTarget() {
        return relicheirus.getStandAtTreePos(getBottomOfTree(relicheirus.level(), blockPos));
    }

    @Override
    public void tick() {
        super.tick();
        BlockPos target = getMoveToTarget();
        if (target != null) {
            if (isReachedTarget()) {
                if (relicheirus.lockTreePosition(blockPos)) {
                    if (relicheirus.getAnimation() == IAnimatedEntity.NO_ANIMATION) {
                        relicheirus.setPeckY(blockPos.getY());
                        relicheirus.setAnimation(RelicheirusEntity.ANIMATION_PUSH_TREE);
                    } else if (relicheirus.getAnimation() == RelicheirusEntity.ANIMATION_PUSH_TREE) {
                        if (relicheirus.getAnimationTick() >= 35 && !madeTreeEntity) {
                            madeTreeEntity = true;
                            relicheirus.playSound(SoundEvents.RELICHEIRUS_TOPPLE);
                            // Simplified: just break the block instead of creating falling tree entity
                            relicheirus.level().destroyBlock(blockPos, true, relicheirus);
                        }
                    }
                }
            } else {
                if (relicheirus.getNavigation().isDone()) {
                    Vec3 vec31 = Vec3.atCenterOf(target);
                    Vec3 vec32 = vec31.subtract(relicheirus.position());
                    if (vec32.length() > 1) {
                        vec32 = vec32.normalize();
                    }
                    Vec3 delta = new Vec3(vec32.x * 0.1F, 0F, vec32.z * 0.1F);
                    relicheirus.setDeltaMovement(relicheirus.getDeltaMovement().add(delta));
                }
            }
        }
    }

    protected void moveMobToBlock() {
        BlockPos pos = getMoveToTarget();
        this.mob.getNavigation().moveTo((double) ((float) pos.getX()) + 0.5D, (double) (pos.getY()), (double) ((float) pos.getZ()) + 0.5D, this.speedModifier);
    }


    public void stop() {
        this.blockPos = BlockPos.ZERO;
        madeTreeEntity = false;
        super.stop();
    }

    private BlockPos getBottomOfTree(LevelReader worldIn, BlockPos pos) {
        int minY = worldIn instanceof net.minecraft.world.level.LevelHeightAccessor ? ((net.minecraft.world.level.LevelHeightAccessor)worldIn).getMinY() : -64;
        while (pos.getY() > minY && (worldIn.getBlockState(pos).is(BlockTags.LEAVES) || worldIn.getBlockState(pos).isAir() || worldIn.getBlockState(pos).is(BlockTags.LOGS))) {
            pos = pos.below();
        }
        return pos;
    }

    @Override
    protected boolean isValidTarget(LevelReader worldIn, BlockPos pos) {
        int maxY = worldIn instanceof net.minecraft.world.level.LevelHeightAccessor ? ((net.minecraft.world.level.LevelHeightAccessor)worldIn).getMaxY() : 320;
        if (worldIn.getBlockState(pos).is(BlockTags.LOGS)) {
            BlockPos treeTop = new BlockPos(pos);
            while (worldIn.getBlockState(treeTop).is(BlockTags.LOGS) && treeTop.getY() < maxY) {
                treeTop = treeTop.above();
            }
            if (worldIn.getBlockState(treeTop).is(BlockTags.LEAVES)) {
                return true;
            }
        }
        return false;
    }
}
