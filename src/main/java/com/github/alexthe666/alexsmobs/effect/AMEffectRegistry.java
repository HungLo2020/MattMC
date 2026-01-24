package com.github.alexthe666.alexsmobs.effect;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * Stub registry for AlexsMobs effects
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
     */
    public static final Holder<MobEffect> ORCAS_MIGHT;
    
    /**
     * Debilitating Sting effect - from Tarantula Hawk sting
     */
    public static final Holder<MobEffect> DEBILITATING_STING;
    
    static {
        // Register Orca's Might effect
        MobEffect orcasMightEffect = new EffectOrcaMight();
        ORCAS_MIGHT = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT,
            ResourceLocation.withDefaultNamespace("orcas_might"),
            orcasMightEffect
        );
        
        // Register Debilitating Sting effect
        MobEffect debilitatingStingEffect = new EffectDebilitatingSting();
        DEBILITATING_STING = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT,
            ResourceLocation.withDefaultNamespace("debilitating_sting"),
            debilitatingStingEffect
        );
    }
}
