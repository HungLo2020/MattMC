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
    
    /**
     * Tiger's Blessing effect - protection from tigers after feeding them
     * Registered in MobEffects.java
     */
    public static final Holder<MobEffect> TIGERS_BLESSING = MobEffects.TIGERS_BLESSING;
    
    /**
     * Fear effect - placeholder using weakness
     */
    public static final Holder<MobEffect> FEAR = MobEffects.WEAKNESS;
    
    /**
     * Sunbird Blessing effect - grants slow falling and elytra boost
     * Registered in MobEffects.java
     */
    public static final Holder<MobEffect> SUNBIRD_BLESSING = MobEffects.SUNBIRD_BLESSING;
    
    /**
     * Sunbird Curse effect - increased gravity and no elytra
     * Registered in MobEffects.java
     */
    public static final Holder<MobEffect> SUNBIRD_CURSE = MobEffects.SUNBIRD_CURSE;
}
