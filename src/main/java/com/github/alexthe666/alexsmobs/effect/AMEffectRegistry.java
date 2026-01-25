package com.github.alexthe666.alexsmobs.effect;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * Stub registry for AlexsMobs effects
 * Uses vanilla effects as placeholders to avoid registry freeze issues
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
     * Orca's Might effect - stub using vanilla strength effect
     * In full implementation this would be a custom effect
     */
    public static final Holder<MobEffect> ORCAS_MIGHT = MobEffects.STRENGTH;
    
    /**
     * Debilitating Sting effect - stub using vanilla weakness effect  
     * In full implementation this would be a custom effect
     */
    public static final Holder<MobEffect> DEBILITATING_STING = MobEffects.WEAKNESS;
}
