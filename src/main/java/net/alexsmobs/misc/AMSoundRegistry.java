package net.alexsmobs.misc;

import net.minecraft.sounds.SoundEvent;

/**
 * Stub registry class for AlexsMobs sounds
 * Points to actual vanilla-registered sounds in SoundEvents.java
 */
public class AMSoundRegistry {
    
    /**
     * Deferred holder stub that returns the actual vanilla-registered sound
     */
    public static class DeferredHolder {
        private final java.util.function.Supplier<SoundEvent> soundSupplier;
        
        public DeferredHolder(java.util.function.Supplier<SoundEvent> soundSupplier) {
            this.soundSupplier = soundSupplier;
        }
        
        public SoundEvent get() {
            return soundSupplier.get();
        }
    }
    
    // Comb Jelly sounds - reference vanilla SoundEvents
    public static final DeferredHolder COMB_JELLY_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.COMB_JELLY_HURT);
    
    // Crow sounds - reference vanilla SoundEvents
    public static final DeferredHolder CROW_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.CROW_IDLE);
    public static final DeferredHolder CROW_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.CROW_HURT);
    
    // Endergrade sounds - reference vanilla SoundEvents
    public static final DeferredHolder ENDERGRADE_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ENDERGRADE_IDLE);
    public static final DeferredHolder ENDERGRADE_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ENDERGRADE_HURT);
    
    // Enderiophage sounds - reference vanilla SoundEvents
    public static final DeferredHolder ENDERIOPHAGE_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ENDERIOPHAGE_HURT);
    public static final DeferredHolder ENDERIOPHAGE_WALK = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ENDERIOPHAGE_WALK);
    public static final DeferredHolder ENDERIOPHAGE_SQUISH = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ENDERIOPHAGE_SQUISH);
    
    // Hummingbird sounds - reference vanilla SoundEvents
    public static final DeferredHolder HUMMINGBIRD_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.HUMMINGBIRD_IDLE);
    public static final DeferredHolder HUMMINGBIRD_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.HUMMINGBIRD_HURT);
    public static final DeferredHolder HUMMINGBIRD_LOOP = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.HUMMINGBIRD_LOOP);
    
    // Jerboa sounds - reference vanilla SoundEvents
    public static final DeferredHolder JERBOA_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.JERBOA_IDLE);
    public static final DeferredHolder JERBOA_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.JERBOA_HURT);
    
    // Mimic Octopus sounds - reference vanilla SoundEvents
    public static final DeferredHolder MIMIC_OCTOPUS_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.MIMIC_OCTOPUS_IDLE);
    public static final DeferredHolder MIMIC_OCTOPUS_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.MIMIC_OCTOPUS_HURT);
    
    // Mudskipper sounds - reference vanilla SoundEvents
    public static final DeferredHolder MUDSKIPPER_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.MUDSKIPPER_HURT);
    public static final DeferredHolder MUDSKIPPER_WALK = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.MUDSKIPPER_WALK);
    public static final DeferredHolder MUDSKIPPER_SPIT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.MUDSKIPPER_SPIT);
    
    // Rain Frog sounds - reference vanilla SoundEvents
    public static final DeferredHolder RAIN_FROG_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.RAIN_FROG_IDLE);
    public static final DeferredHolder RAIN_FROG_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.RAIN_FROG_HURT);
    
    // Roadrunner sounds - reference vanilla SoundEvents
    public static final DeferredHolder ROADRUNNER_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ROADRUNNER_IDLE);
    public static final DeferredHolder ROADRUNNER_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ROADRUNNER_HURT);
    public static final DeferredHolder ROADRUNNER_MEEP = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ROADRUNNER_MEEP);
    
    // Raccoon sounds - reference vanilla SoundEvents
    public static final DeferredHolder RACCOON_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.RACCOON_IDLE);
    public static final DeferredHolder RACCOON_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.RACCOON_HURT);
    
    // Rattlesnake sounds - reference vanilla SoundEvents
    public static final DeferredHolder RATTLESNAKE_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.RATTLESNAKE_HURT);
    public static final DeferredHolder RATTLESNAKE_LOOP = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.RATTLESNAKE_LOOP);
    public static final DeferredHolder RATTLESNAKE_ATTACK = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.RATTLESNAKE_ATTACK);
    
    // Rhinoceros sounds - reference vanilla SoundEvents
    public static final DeferredHolder RHINOCEROS_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.RHINOCEROS_IDLE);
    public static final DeferredHolder RHINOCEROS_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.RHINOCEROS_HURT);
    
    // Seagull sounds - reference vanilla SoundEvents
    public static final DeferredHolder SEAGULL_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.SEAGULL_IDLE);
    public static final DeferredHolder SEAGULL_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.SEAGULL_HURT);
    
    // Sunbird sounds - reference vanilla SoundEvents
    public static final DeferredHolder SUNBIRD_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.SUNBIRD_IDLE);
    public static final DeferredHolder SUNBIRD_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.SUNBIRD_HURT);
    
    // Orca sounds - reference vanilla SoundEvents
    public static final SoundEvent ORCA_IDLE = net.minecraft.sounds.SoundEvents.ORCA_IDLE;
    public static final SoundEvent ORCA_HURT = net.minecraft.sounds.SoundEvents.ORCA_HURT;
    public static final SoundEvent ORCA_DIE = net.minecraft.sounds.SoundEvents.ORCA_DIE;
    
    // Caiman sounds - reference vanilla SoundEvents
    public static final DeferredHolder CAIMAN_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.CAIMAN_IDLE);
    public static final DeferredHolder CAIMAN_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.CAIMAN_HURT);
    public static final DeferredHolder CAIMAN_SPLASH = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.CAIMAN_SPLASH);
    public static final DeferredHolder CROCODILE_BABY = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.CROCODILE_BABY);
    
    // Shoebill sounds - reference vanilla SoundEvents
    public static final DeferredHolder SHOEBILL_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.SHOEBILL_HURT);
    
    // Skunk sounds - reference vanilla SoundEvents
    public static final DeferredHolder SKUNK_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.SKUNK_IDLE);
    public static final DeferredHolder SKUNK_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.SKUNK_HURT);
    public static final DeferredHolder SKUNK_SPRAY = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.SKUNK_SPRAY);
    
    // Spectre sounds - reference vanilla SoundEvents
    public static final DeferredHolder SPECTRE_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.SPECTRE_IDLE);
    public static final DeferredHolder SPECTRE_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.SPECTRE_HURT);
    
    // Toucan sounds - reference vanilla SoundEvents
    public static final DeferredHolder TOUCAN_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.TOUCAN_IDLE);
    public static final DeferredHolder TOUCAN_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.TOUCAN_HURT);
    
    // Tasmanian Devil sounds - reference vanilla SoundEvents
    public static final DeferredHolder TASMANIAN_DEVIL_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.TASMANIAN_DEVIL_IDLE);
    public static final DeferredHolder TASMANIAN_DEVIL_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.TASMANIAN_DEVIL_HURT);
    public static final DeferredHolder TASMANIAN_DEVIL_ROAR = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.TASMANIAN_DEVIL_ROAR);
    
    // Tiger sounds - reference vanilla SoundEvents
    public static final DeferredHolder TIGER_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.TIGER_IDLE);
    public static final DeferredHolder TIGER_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.TIGER_HURT);
    public static final DeferredHolder TIGER_ANGRY = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.TIGER_ANGRY);
    
    // Anteater sounds - reference vanilla SoundEvents
    public static final SoundEvent ANTEATER_HURT = net.minecraft.sounds.SoundEvents.ANTEATER_HURT;
    
    // Cosmaw sounds - reference vanilla SoundEvents
    public static final DeferredHolder COSMAW_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.COSMAW_IDLE);
    public static final DeferredHolder COSMAW_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.COSMAW_HURT);
    
    // Elephant sounds - reference vanilla SoundEvents
    public static final DeferredHolder ELEPHANT_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ELEPHANT_IDLE);
    public static final DeferredHolder ELEPHANT_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ELEPHANT_HURT);
    public static final DeferredHolder ELEPHANT_DIE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ELEPHANT_DIE);
    public static final DeferredHolder ELEPHANT_WALK = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ELEPHANT_WALK);
    public static final DeferredHolder ELEPHANT_TRUMPET = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ELEPHANT_TRUMPET);
    
    // Emu sounds - reference vanilla SoundEvents
    public static final DeferredHolder EMU_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.EMU_IDLE);
    public static final DeferredHolder EMU_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.EMU_HURT);
    
    // Gelada Monkey sounds - reference vanilla SoundEvents
    public static final DeferredHolder GELADA_MONKEY_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.GELADA_MONKEY_IDLE);
    public static final DeferredHolder GELADA_MONKEY_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.GELADA_MONKEY_HURT);
    
    // Gorilla sounds - reference vanilla SoundEvents
    public static final DeferredHolder GORILLA_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.GORILLA_IDLE);
    public static final DeferredHolder GORILLA_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.GORILLA_HURT);
    
    // Giant Squid sounds - reference vanilla SoundEvents
    public static final DeferredHolder GIANT_SQUID_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.GIANT_SQUID_HURT);
    public static final DeferredHolder GIANT_SQUID_GAMES = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.GIANT_SQUID_GAMES);
    
    // Leafcutter Ant sounds - reference vanilla SoundEvents
    public static final DeferredHolder LEAFCUTTER_ANT_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.LEAFCUTTER_ANT_HURT);
    public static final DeferredHolder LEAFCUTTER_ANT_QUEEN_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.LEAFCUTTER_ANT_QUEEN_HURT);
    
    // Tarantula Hawk sounds - reference vanilla SoundEvents
    public static final DeferredHolder TARANTULA_HAWK_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.TARANTULA_HAWK_HURT);
    public static final DeferredHolder TARANTULA_HAWK_WING = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.TARANTULA_HAWK_WING);
    
    // Underminer sounds - reference vanilla SoundEvents
    public static final DeferredHolder UNDERMINER_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.UNDERMINER_IDLE);
    public static final DeferredHolder UNDERMINER_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.UNDERMINER_HURT);
    public static final DeferredHolder UNDERMINER_STEP = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.UNDERMINER_STEP);
    
    // Warped Toad sounds - reference vanilla SoundEvents
    public static final DeferredHolder WARPED_TOAD_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.WARPED_TOAD_IDLE);
    public static final DeferredHolder WARPED_TOAD_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.WARPED_TOAD_HURT);
    
    // Komodo Dragon sounds - reference vanilla SoundEvents
    public static final DeferredHolder KOMODO_DRAGON_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.KOMODO_DRAGON_IDLE);
    public static final DeferredHolder KOMODO_DRAGON_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.KOMODO_DRAGON_HURT);
}
