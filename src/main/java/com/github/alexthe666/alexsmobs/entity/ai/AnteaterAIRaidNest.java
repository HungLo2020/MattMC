package com.github.alexthe666.alexsmobs.entity.ai;

import com.github.alexthe666.alexsmobs.entity.EntityAnteater;
import com.github.alexthe666.alexsmobs.entity.EntityLeafcutterAnt;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class AnteaterAIRaidNest extends MoveToBlockGoal {

    private final EntityAnteater anteater;
    private int idleAtHiveTime = 0;
    private boolean isAboveDestinationAnteater;
    private boolean shootTongue;
    private int maxEatingTime = 0;

    public AnteaterAIRaidNest(EntityAnteater anteater) {
        super(anteater, 1D, 32, 8);
        this.anteater = anteater;
    }

    private List<ItemStack> getItemStacks(EntityAnteater anteater) {
        // Stub - return simple loot
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.SUGAR, anteater.getRandom().nextInt(2) + 1));
        return loot;
    }

    private void dropDigItems(){
        List<ItemStack> lootList = getItemStacks(anteater);
        if (lootList.size() > 0) {
            for (ItemStack stack : lootList) {
                if (anteater.level() instanceof ServerLevel serverLevel) {
                    ItemEntity e = this.anteater.spawnAtLocation(serverLevel, stack.copy(), 0.0F);
                    if (e != null) {
                        e.hasImpulse = true;
                        e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 0.2, 0.2));
                    }
                }
            }
        }
    }
    
    public boolean canUse() {
        return !anteater.isBaby() && super.canUse() && anteater.eatAntCooldown <= 0;
    }

    public boolean canContinueToUse() {
        return super.canContinueToUse() && anteater.eatAntCooldown <= 0;
    }

    public void start() {
        super.start();
        maxEatingTime = 150 + anteater.getRandom().nextInt(200);
    }

    public void stop() {
        super.stop();
        idleAtHiveTime = 0;
        maxEatingTime = 150 + anteater.getRandom().nextInt(200);
        anteater.setLeaning(false);
        anteater.resetAntCooldown();
    }

    public double acceptedDistance() {
        return 1.2D;
    }

    public void tick() {
        super.tick();
        BlockPos blockpos = this.getMoveToTarget();
        if (!isWithinXZDist(blockpos, this.mob.position(), this.acceptedDistance())) {
            this.isAboveDestinationAnteater = false;
            ++this.tryTicks;
            if (this.shouldRecalculatePath()) {
                this.mob.getNavigation().moveTo((double) ((float) blockpos.getX()) + 0.5D, blockpos.getY(), (double) ((float) blockpos.getZ()) + 0.5D, this.speedModifier);
            }
        } else {
            this.isAboveDestinationAnteater = true;
            --this.tryTicks;
        }

        if (this.isReachedTarget()) {
            anteater.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(blockPos.getX() + 0.5D, blockPos.getY() - 1, blockPos.getZ() + 0.5));
            if (this.idleAtHiveTime >= 20 && this.idleAtHiveTime % 20 == 0) {
                shootTongue = anteater.getRandom().nextInt(2) == 0;
                if(shootTongue){
                    this.eatHive();
                }else{
                    this.breakHiveEffect();
                }
            }
            ++this.idleAtHiveTime;
            if (shootTongue && anteater.getAnimation() == com.github.alexthe666.citadel.animation.IAnimatedEntity.NO_ANIMATION) {
                anteater.setLeaning(false);
                anteater.setAnimation(EntityAnteater.ANIMATION_TOUNGE_IDLE);
            }else if (anteater.getAnimation() == com.github.alexthe666.citadel.animation.IAnimatedEntity.NO_ANIMATION) {
                anteater.setLeaning(true);
                anteater.setAnimation(anteater.getRandom().nextBoolean() ? EntityAnteater.ANIMATION_SLASH_L : EntityAnteater.ANIMATION_SLASH_R);
            }
            if(this.idleAtHiveTime > maxEatingTime){
                stop();
            }
        }

    }

    private boolean isWithinXZDist(BlockPos blockpos, Vec3 positionVec, double distance) {
        return blockpos.distSqr(new BlockPos((int)positionVec.x(), blockpos.getY(), (int)positionVec.z())) < distance * distance;
    }

    protected boolean isReachedTarget() {
        return this.isAboveDestinationAnteater;
    }

    private void breakHiveEffect(){
        // Simplified - just drop items without complex block/tile entity logic
        BlockState blockstate = anteater.level().getBlockState(this.blockPos);
        if (blockstate.is(Blocks.DIRT) || blockstate.is(Blocks.COARSE_DIRT)) {
            dropDigItems();
        }
    }

    private void eatHive() {
        // Simplified version without complex block/tile entity dependencies
        BlockState blockstate = anteater.level().getBlockState(this.blockPos);
        if (blockstate.is(Blocks.DIRT) || blockstate.is(Blocks.COARSE_DIRT)) {
            anteater.setAntOnTongue(true);
            dropDigItems();
        }
        
        // Anger nearby ants (stub entities)
        double d0 = 15;
        for (EntityLeafcutterAnt leafcutter : anteater.level().getEntitiesOfClass(EntityLeafcutterAnt.class, new AABB((double) blockPos.getX() - d0, (double) blockPos.getY() - d0, (double) blockPos.getZ() - d0, (double) blockPos.getX() + d0, (double) blockPos.getY() + d0, (double) blockPos.getZ() + d0))) {
            leafcutter.setRemainingPersistentAngerTime(100);
            leafcutter.setTarget(anteater);
            leafcutter.setStayOutOfHiveCountdown(400);
        }
    }

    @Override
    protected boolean isValidTarget(LevelReader worldIn, BlockPos pos) {
        // Simplified - look for dirt blocks as a placeholder for ant nests
        return worldIn.getBlockState(pos).is(Blocks.DIRT) || worldIn.getBlockState(pos).is(Blocks.COARSE_DIRT);
    }
}
