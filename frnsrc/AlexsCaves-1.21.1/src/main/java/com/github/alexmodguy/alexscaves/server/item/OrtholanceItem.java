package com.github.alexmodguy.alexscaves.server.item;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.server.enchantment.ACEnchantmentHelper;
import com.github.alexmodguy.alexscaves.server.enchantment.ACEnchantmentRegistry;
import com.github.alexmodguy.alexscaves.server.entity.item.WaveEntity;
import com.github.alexmodguy.alexscaves.server.misc.ACSoundRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class OrtholanceItem extends Item {

    public OrtholanceItem(Item.Properties properties) {
        super(properties.attributes(createOrtholanceAttributes()));
    }

    private static ItemAttributeModifiers createOrtholanceAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "ortholance_attack_damage"), 5.0D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "ortholance_attack_speed"), -2.4D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    public UseAnim getUseAnimation(ItemStack p_43417_) {
        return UseAnim.BOW;
    }

    public int getEnchantmentValue() {
        return 1;
    }

    public int getUseDuration(ItemStack itemStack, LivingEntity entity) {
        return 72000;
    }

    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int useTime) {
        int i = Mth.clamp(this.getUseDuration(stack, livingEntity) - useTime, 0, 60);
        int flinging = ACEnchantmentHelper.getEnchantmentLevel(level, ACEnchantmentRegistry.FLINGING, stack);
        boolean tsunami = ACEnchantmentHelper.getEnchantmentLevel(level, ACEnchantmentRegistry.TSUNAMI, stack) > 0;
        if (i > 0) {
            float f = 0.1F * i + flinging * 0.1F;
            Vec3 vec3 = livingEntity.getDeltaMovement().add(livingEntity.getViewVector(1.0F).normalize().multiply(f, f * 0.15F, f));
            if (i >= 10 && !level.isClientSide) {
                level.playSound(null, livingEntity, ACSoundRegistry.ORTHOLANCE_WAVE.get(), SoundSource.NEUTRAL, 4.0F, 1.0F);
                stack.hurtAndBreak(1, livingEntity, EquipmentSlot.MAINHAND);
                int maxWaves = i / 5;
                if(tsunami){
                    maxWaves = 5;
                    Vec3 waveCenterPos = livingEntity.position().add(vec3);
                    WaveEntity tsunamiWaveEntity = new WaveEntity(level, livingEntity);
                    tsunamiWaveEntity.setPos(waveCenterPos.x, livingEntity.getY(), waveCenterPos.z);
                    tsunamiWaveEntity.setLifespan(20);
                    tsunamiWaveEntity.setWaveScale(5.0F);
                    tsunamiWaveEntity.setWaitingTicks(2);
                    tsunamiWaveEntity.setYRot(-(float) (Mth.atan2(vec3.x, vec3.z) * (double) (180F / (float) Math.PI)));
                    level.addFreshEntity(tsunamiWaveEntity);
                }else{
                    for (int wave = 0; wave < maxWaves; wave++) {
                        float f1 = (float) wave / maxWaves;
                        int lifespan = 3 + (int) ((1F - f1) * 3);
                        Vec3 waveCenterPos = livingEntity.position().add(vec3.scale(f1 * 2));
                        WaveEntity leftWaveEntity = new WaveEntity(level, livingEntity);
                        leftWaveEntity.setPos(waveCenterPos.x, livingEntity.getY(), waveCenterPos.z);
                        leftWaveEntity.setLifespan(lifespan);
                        leftWaveEntity.setYRot(-(float) (Mth.atan2(vec3.x, vec3.z) * (double) (180F / (float) Math.PI)) + 60 - 15 * wave);
                        level.addFreshEntity(leftWaveEntity);
                        WaveEntity rightWaveEntity = new WaveEntity(level, livingEntity);
                        rightWaveEntity.setPos(waveCenterPos.x, livingEntity.getY(), waveCenterPos.z);
                        rightWaveEntity.setLifespan(lifespan);
                        rightWaveEntity.setYRot(-(float) (Mth.atan2(vec3.x, vec3.z) * (double) (180F / (float) Math.PI)) - 60 + 15 * wave);
                        level.addFreshEntity(rightWaveEntity);
                    }
                    if(ACEnchantmentHelper.getEnchantmentLevel(level, ACEnchantmentRegistry.SECOND_WAVE, stack) > 0){
                        int maxSecondWaves = Math.max(1, maxWaves - 1);
                        for (int wave = 0; wave < maxSecondWaves; wave++) {
                            float f1 = (float) wave / maxSecondWaves;
                            int lifespan = 3 + (int) ((1F - f1) * 3);
                            Vec3 waveCenterPos = livingEntity.position().add(vec3.scale(f1 * 2));
                            WaveEntity leftWaveEntity = new WaveEntity(level, livingEntity);
                            leftWaveEntity.setPos(waveCenterPos.x, livingEntity.getY(), waveCenterPos.z);
                            leftWaveEntity.setLifespan(lifespan);
                            leftWaveEntity.setYRot(-(float) (Mth.atan2(vec3.x, vec3.z) * (double) (180F / (float) Math.PI)) + 60 - 15 * wave);
                            leftWaveEntity.setWaitingTicks(8);
                            level.addFreshEntity(leftWaveEntity);
                            WaveEntity rightWaveEntity = new WaveEntity(level, livingEntity);
                            rightWaveEntity.setPos(waveCenterPos.x, livingEntity.getY(), waveCenterPos.z);
                            rightWaveEntity.setLifespan(lifespan);
                            rightWaveEntity.setYRot(-(float) (Mth.atan2(vec3.x, vec3.z) * (double) (180F / (float) Math.PI)) - 60 + 15 * wave);
                            rightWaveEntity.setWaitingTicks(8);
                            level.addFreshEntity(rightWaveEntity);
                        }
                    }
                }
                AABB aabb = new AABB(livingEntity.position(), livingEntity.position().add(vec3.scale(maxWaves))).inflate(1);
                DamageSource source = livingEntity.damageSources().mobAttack(livingEntity);
                double d = 5.0D; // Base attack damage from attributes
                var modifiers = stack.getAttributeModifiers();
                for (var entry : modifiers.modifiers()) {
                    if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)) {
                        d += entry.modifier().amount();
                    }
                }
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, aabb)) {
                    if (!livingEntity.isAlliedTo(entity) && !livingEntity.equals(entity) && livingEntity.hasLineOfSight(entity)) {
                        entity.hurt(source, (float) d);
                        entity.stopRiding();
                    }
                }
            }
            livingEntity.setDeltaMovement(vec3.add(0, (livingEntity.onGround() ? 0.2F : 0) + (flinging * 0.1F), 0));
        }
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        ItemStack itemstack = player.getItemInHand(interactionHand);
        if (itemstack.getDamageValue() >= itemstack.getMaxDamage() - 1) {
            return InteractionResultHolder.fail(itemstack);
        } else {
            player.startUsingItem(interactionHand);
            return InteractionResultHolder.consume(itemstack);
        }
    }

    public boolean hurtEnemy(ItemStack stack, LivingEntity hurt, LivingEntity player) {
        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        Vec3 vec3 = player.getViewVector(1.0F);
        if(ACEnchantmentHelper.getEnchantmentLevel(player.level(), ACEnchantmentRegistry.SEA_SWING, stack) > 0){
            WaveEntity waveEntity = new WaveEntity(hurt.level(), player);
            waveEntity.setPos(player.getX(), hurt.getY(), player.getZ());
            waveEntity.setLifespan(5);
            waveEntity.setYRot(-(float) (Mth.atan2(vec3.x, vec3.z) * (double) (180F / (float) Math.PI)));
            player.level().addFreshEntity(waveEntity);
        }
        return true;
    }

    public boolean mineBlock(ItemStack itemStack, Level level, BlockState state, BlockPos blockPos, LivingEntity livingEntity) {
        if ((double) state.getDestroySpeed(level, blockPos) != 0.0D) {
            itemStack.hurtAndBreak(2, livingEntity, EquipmentSlot.MAINHAND);
        }

        return true;
    }


    @Override
    public void initializeClient(java.util.function.Consumer<IClientItemExtensions> consumer) {
        consumer.accept((IClientItemExtensions) AlexsCaves.PROXY.getISTERProperties());
    }
}
