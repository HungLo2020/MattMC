package net.alexsmobs.message;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class MessageKangarooEat {
    
    public static void spawnEatParticles(Entity entity, ItemStack stack) {
        if (entity.level().isClientSide()) {
            for (int i = 0; i < 7; i++) {
                double d2 = entity.getRandom().nextGaussian() * 0.02D;
                double d0 = entity.getRandom().nextGaussian() * 0.02D;
                double d1 = entity.getRandom().nextGaussian() * 0.02D;
                entity.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, stack), 
                    entity.getX() + (double)(entity.getRandom().nextFloat() * entity.getBbWidth()) - (double)entity.getBbWidth() * 0.5F, 
                    entity.getY() + entity.getBbHeight() * 0.5F + (double)(entity.getRandom().nextFloat() * entity.getBbHeight() * 0.5F), 
                    entity.getZ() + (double)(entity.getRandom().nextFloat() * entity.getBbWidth()) - (double)entity.getBbWidth() * 0.5F, 
                    d0, d1, d2);
            }
        }
    }
}
