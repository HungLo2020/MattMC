package net.voxelmap.util;

import net.voxelmap.VoxelConstants;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.ZombifiedPiglin;

public enum MobCategory {
    HOSTILE,
    NEUTRAL,
    TAMEABLE,
    PLAYER;

    public static boolean isHostile(Entity entity) {
        if (entity instanceof ZombifiedPiglin zombifiedPiglinEntity) {
            return zombifiedPiglinEntity.getPersistentAngerTarget() != null && zombifiedPiglinEntity.getPersistentAngerTarget().equals(VoxelConstants.getPlayer().getUUID());
        } else if (entity instanceof Enemy) {
            return true;
        } else if (entity instanceof Bee beeEntity) {
            return beeEntity.isAngry();
        } else {
            if (entity instanceof PolarBear polarBearEntity) {

                for (PolarBear object : polarBearEntity.level().getEntitiesOfClass(PolarBear.class, polarBearEntity.getBoundingBox().inflate(8.0, 4.0, 8.0))) {
                    if (object.isBaby()) {
                        return true;
                    }
                }
            }

            if (entity instanceof Rabbit rabbitEntity) {
                return rabbitEntity.getVariant() == Rabbit.Variant.EVIL;
            } else if (entity instanceof Wolf wolfEntity) {
                return wolfEntity.isAngry();
            } else {
                return false;
            }
        }
    }

    public static boolean isPlayer(Entity entity) {
        return entity instanceof RemotePlayer;
    }

}
