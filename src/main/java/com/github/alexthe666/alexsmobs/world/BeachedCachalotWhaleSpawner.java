package com.github.alexthe666.alexsmobs.world;

// Removed: import com.github.alexthe666.alexsmobs.config.AMConfig;
// Removed: import com.github.alexthe666.alexsmobs.config.BiomeConfig;
// Removed: import com.github.alexthe666.alexsmobs.entity.AMEntityRegistry;
import com.github.alexthe666.alexsmobs.entity.EntityCachalotWhale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Random;

public class BeachedCachalotWhaleSpawner {
    private final Random random = new Random();
    private final ServerLevel world;
    private int timer;
    private int delay;
    private int chance;

    public BeachedCachalotWhaleSpawner(ServerLevel p_i50177_1_) {
        this.world = p_i50177_1_;
        this.timer = 1200;
        AMWorldData worldinfo = AMWorldData.get(p_i50177_1_);
        this.delay = worldinfo.getBeachedCachalotSpawnDelay();
        this.chance = worldinfo.getBeachedCachalotSpawnChance();
        if (this.delay == 0 && this.chance == 0) {
            // STUB: AMConfig.beachedCachalotWhaleSpawnDelay not available, using default
            this.delay = 24000; // Default: 20 minutes
            worldinfo.setBeachedCachalotSpawnDelay(this.delay);
            this.chance = 25;
            worldinfo.setBeachedCachalotSpawnChance(this.chance);
        }

    }

    public void tick() {
        // STUB: AMConfig.beachedCachalotWhales not available, assuming true for now
        if (true && --this.timer <= 0 && world.isThundering()) {
            this.timer = 1200;
            AMWorldData worldinfo = AMWorldData.get(world);
            this.delay -= 1200;
            if(delay < 0){
                delay = 0;
            }
            worldinfo.setBeachedCachalotSpawnDelay(this.delay);
            if (this.delay <= 0) {
                // STUB: AMConfig.beachedCachalotWhaleSpawnDelay not available, using default
                this.delay = 24000; // Default: 20 minutes
                if (this.world.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                    int i = this.chance;
                    // STUB: AMConfig.beachedCachalotWhaleSpawnChance not available, using default
                    this.chance = Mth.clamp(this.chance + 10, 5, 100);
                    worldinfo.setBeachedCachalotSpawnChance(this.chance);
                    if (this.random.nextInt(100) <= i && this.attemptSpawnWhale()) {
                        // STUB: AMConfig.beachedCachalotWhaleSpawnChance not available, using default
                        this.chance = 10;
                    }
                }
            }
        }

    }

    private boolean attemptSpawnWhale() {
        Player playerentity = this.world.getRandomPlayer();
        if (playerentity == null) {
            return true;
        } else if (this.random.nextInt(5) != 0) {
            return false;
        } else {
            BlockPos blockpos = playerentity.blockPosition();
            BlockPos blockpos2 = this.func_221244_a(blockpos, 84);
            if (blockpos2 != null && this.func_226559_a_(blockpos2) && blockpos2.distSqr(blockpos) > 225) {
                BlockPos upPos = new BlockPos(blockpos2.getX(), blockpos2.getY() + 2, blockpos2.getZ());
                EntityCachalotWhale whale = EntityType.CACHALOT_WHALE.create(world, EntitySpawnReason.EVENT);
                if (whale != null) {
                    whale.setPos(upPos.getX() + 0.5D, upPos.getY() + 0.5D, upPos.getZ() + 0.5D);
                    whale.setYRot(random.nextFloat() * 360 - 180F);
                    whale.setXRot(0);
                    whale.finalizeSpawn(world, world.getCurrentDifficultyAt(upPos), EntitySpawnReason.EVENT, null);
                    whale.setBeached(true);
                    AMWorldData worldinfo = AMWorldData.get(world);
                    worldinfo.setBeachedCachalotID(whale.getUUID());
                    whale.restrictTo(upPos, 16);
                    whale.setDespawnBeach(true);
                    world.addFreshEntity(whale);
                }
                return true;
            }
            return false;
        }
    }

    @Nullable
    private BlockPos func_221244_a(BlockPos p_221244_1_, int p_221244_2_) {
        BlockPos blockpos = null;

        for(int i = 0; i < 10; ++i) {
            int j = p_221244_1_.getX() + this.random.nextInt(p_221244_2_ * 2) - p_221244_2_;
            int k = p_221244_1_.getZ() + this.random.nextInt(p_221244_2_ * 2) - p_221244_2_;
            int l = this.world.getHeight(Types.WORLD_SURFACE, j, k);
            BlockPos blockpos1 = new BlockPos(j, l, k);
            // In 1.21+, check spawn position manually instead of using NaturalSpawner.isSpawnPositionOk
            BlockState blockState = this.world.getBlockState(blockpos1.below());
            boolean isValidSpawn = blockState.isValidSpawn(this.world, blockpos1.below(), EntityType.WANDERING_TRADER);
            // STUB: BiomeConfig.cachalot_whale_beached_spawns not available, allowing all biomes
            if (isValidSpawn) {
                blockpos = blockpos1;
                break;
            }
        }

        return blockpos;
    }

    private boolean func_226559_a_(BlockPos p_226559_1_) {
        Iterator var2 = BlockPos.betweenClosed(p_226559_1_, p_226559_1_.offset(1, 2, 1)).iterator();

        BlockPos blockpos;
        do {
            if (!var2.hasNext()) {
                return true;
            }

            blockpos = (BlockPos)var2.next();
        } while(this.world.getBlockState(blockpos).getBlockSupportShape(this.world, blockpos).isEmpty() && world.getFluidState(blockpos).isEmpty());

        return false;
    }
}
