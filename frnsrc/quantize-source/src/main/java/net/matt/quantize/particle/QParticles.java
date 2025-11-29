package net.matt.quantize.particle;

import net.matt.quantize.Quantize;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public class QParticles {

    public static final DeferredRegister<ParticleType<?>> DEF_REG = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Quantize.MOD_ID);

    // old registered particles from Alex's Mobs
    //public static final RegistryObject<SimpleParticleType> GUSTER_SAND_SPIN = DEF_REG.register("guster_sand_spin", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> GUSTER_SAND_SHOT = DEF_REG.register("guster_sand_shot", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> GUSTER_SAND_SPIN_RED = DEF_REG.register("guster_sand_spin_red", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> GUSTER_SAND_SHOT_RED = DEF_REG.register("guster_sand_shot_red", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> GUSTER_SAND_SPIN_SOUL = DEF_REG.register("guster_sand_spin_soul", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> GUSTER_SAND_SHOT_SOUL = DEF_REG.register("guster_sand_shot_soul", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> HEMOLYMPH = DEF_REG.register("hemolymph", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> PLATYPUS_SENSE = DEF_REG.register("platypus_sense", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> WHALE_SPLASH = DEF_REG.register("whale_splash", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> DNA = DEF_REG.register("dna", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> WORM_PORTAL = DEF_REG.register("worm_portal", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> INVERT_DIG = DEF_REG.register("invert_dig", ()-> new SimpleParticleType(true));
    //public static final RegistryObject<SimpleParticleType> TEETH_GLINT = DEF_REG.register("teeth_glint", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> SMELLY = DEF_REG.register("smelly", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> BUNFUNGUS_TRANSFORMATION = DEF_REG.register("bunfungus_transformation", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> FUNGUS_BUBBLE = DEF_REG.register("fungus_bubble", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> BEAR_FREDDY = DEF_REG.register("bear_freddy", ()-> new SimpleParticleType(true));
    //public static final RegistryObject<SimpleParticleType> SUNBIRD_FEATHER = DEF_REG.register("sunbird_feather", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> STATIC_SPARK = DEF_REG.register("static_spark", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> SKULK_BOOM = DEF_REG.register("skulk_boom", ()-> new SimpleParticleType(false));
    //public static final RegistryObject<SimpleParticleType> BIRD_SONG = DEF_REG.register("bird_song", ()-> new SimpleParticleType(false));

    // new particles for Quantize
    public static final RegistryObject<SimpleParticleType> FIREFLIES = DEF_REG.register("fireflies", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SHOCKED = DEF_REG.register("shocked", ()-> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> SILVER_BIRCH_LEAVES = DEF_REG.register("silver_birch_leaves", ()-> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> FLY = DEF_REG.register("fly", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> WHALE_SPLASH = DEF_REG.register("whale_splash", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> DINOSAUR_TRANSFORMATION_AMBER = DEF_REG.register("dinosaur_transformation_amber", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> DINOSAUR_TRANSFORMATION_TECTONIC = DEF_REG.register("dinosaur_transformation_tectonic", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> WATER_TREMOR = DEF_REG.register("water_tremor", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> AMBER_MONOLITH = DEF_REG.register("amber_monolith", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> AMBER_EXPLOSION = DEF_REG.register("amber_explosion", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TEPHRA = DEF_REG.register("tephra", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TEPHRA_SMALL = DEF_REG.register("tephra_small", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TEPHRA_FLAME = DEF_REG.register("tephra_flame", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> LUXTRUCTOSAURUS_SPIT = DEF_REG.register("luxtructosaurus_spit", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> LUXTRUCTOSAURUS_ASH = DEF_REG.register("luxtructosaurus_ash", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TREMORZILLA_EXPLOSION = DEF_REG.register("tremorzilla_explosion", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TREMORZILLA_RETRO_EXPLOSION = DEF_REG.register("tremorzilla_retro_explosion", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TREMORZILLA_TECTONIC_EXPLOSION = DEF_REG.register("tremorzilla_tectonic_explosion", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TREMORZILLA_PROTON = DEF_REG.register("tremorzilla_proton", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TREMORZILLA_RETRO_PROTON = DEF_REG.register("tremorzilla_retro_proton", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TREMORZILLA_TECTONIC_PROTON = DEF_REG.register("tremorzilla_tectonic_proton", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TREMORZILLA_LIGHTNING = DEF_REG.register("tremorzilla_lightning", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TREMORZILLA_RETRO_LIGHTNING = DEF_REG.register("tremorzilla_retro_lightning", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TREMORZILLA_TECTONIC_LIGHTNING = DEF_REG.register("tremorzilla_tectonic_lightning", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TREMORZILLA_BLAST = DEF_REG.register("tremorzilla_blast", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TREMORZILLA_STEAM = DEF_REG.register("tremorzilla_steam", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> HAPPINESS = DEF_REG.register("happiness", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> STUN_STAR = DEF_REG.register("stun_star", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TEETH_GLINT = DEF_REG.register("teeth_glint", ()-> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> DNA = DEF_REG.register("dna", ()-> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> BEAR_FREDDY = DEF_REG.register("bear_freddy", ()-> new SimpleParticleType(true));



    //helpers
    public static void register(IEventBus eventBus) {
        DEF_REG.register(eventBus);
    }
}
