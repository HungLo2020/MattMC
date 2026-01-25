package com.github.alexthe666.alexsmobs.effect;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * Registry for AlexsMobs effects
 * References effects registered in vanilla MobEffects.java
 */
public class AMEffectRegistry {
    
    /**
     * Ender Flu effect - a stub that points to vanilla poison for now
     * In full implementation this would be a custom effect
     */
    public static final Holder<MobEffect> ENDER_FLU = MobEffects.POISON;
    
    /**
     * Fleet Footed effect - points to vanilla speed effect
     */
    public static final Holder<MobEffect> FLEET_FOOTED = MobEffects.SPEED;
    
    /**
     * Orca's Might effect - provides attack speed boost when swimming with orcas
     * Registered in MobEffects.java
     */
    public static final Holder<MobEffect> ORCAS_MIGHT = MobEffects.ORCAS_MIGHT;
    
    /**
     * Debilitating Sting effect - from Tarantula Hawk sting
     * Registered in MobEffects.java
     */
    public static final Holder<MobEffect> DEBILITATING_STING = MobEffects.DEBILITATING_STING;
}
