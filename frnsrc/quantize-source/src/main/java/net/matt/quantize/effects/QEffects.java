package net.matt.quantize.effects;

import net.matt.quantize.Quantize;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.matt.quantize.effects.effects.*;

public class QEffects {
    public static final DeferredRegister<MobEffect> EFFECT_DEF_REG = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Quantize.MOD_ID);

    public static final RegistryObject<MobEffect> OILED = EFFECT_DEF_REG.register("oiled", EffectOiled::new);
    public static final RegistryObject<MobEffect> ORCAS_MIGHT = EFFECT_DEF_REG.register("orcas_might", EffectOrcaMight::new);
    public static final RegistryObject<MobEffect> STUNNED = EFFECT_DEF_REG.register("stunned", () -> new StunnedEffect());
    public static final RegistryObject<MobEffect> RAGE = EFFECT_DEF_REG.register("rage", () -> new RageEffect());
    public static final RegistryObject<MobEffect> EXSANGUINATION = EFFECT_DEF_REG.register("exsanguination", ()-> new EffectExsanguination());
    public static final RegistryObject<MobEffect> ENDER_FLU = EFFECT_DEF_REG.register("ender_flu", ()-> new EffectEnderFlu());
    public static final RegistryObject<MobEffect> POWER_DOWN = EFFECT_DEF_REG.register("power_down", ()-> new EffectPowerDown());

}