// Java
package net.matt.quantize.sounds;

import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.matt.quantize.Quantize;


public class QSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Quantize.MOD_ID);

    //entities
    public static final RegistryObject<SoundEvent> ENTITY_CRAB_HURT = registerSoundEvent("entity.crab.hurt");
    public static final RegistryObject<SoundEvent> ENTITY_CRAB_IDLE = registerSoundEvent("entity.crab.idle");
    public static final RegistryObject<SoundEvent> ENTITY_CRAB_DIE = registerSoundEvent("entity.crab.die");
    public static final RegistryObject<SoundEvent> BUCKET_FILL_CRAB = registerSoundEvent("bucket.fill.crab");
    public static final RegistryObject<SoundEvent> BUCKET_EMPTY_CRAB = registerSoundEvent("bucket.empty.crab");
    public static final RegistryObject<SoundEvent> CROCODILE_IDLE = registerSoundEvent("entity.crocodile.idle");
    public static final RegistryObject<SoundEvent> CROCODILE_HURT = registerSoundEvent("entity.crocodile.hurt");
    public static final RegistryObject<SoundEvent> CROCODILE_BITE = registerSoundEvent("entity.crocodile.bite");
    public static final RegistryObject<SoundEvent> CROCODILE_BABY = registerSoundEvent("entity.crocodile.baby");
    public static final RegistryObject<SoundEvent> CAIMAN_IDLE = registerSoundEvent("entity.caiman.idle");
    public static final RegistryObject<SoundEvent> CAIMAN_HURT = registerSoundEvent("entity.caiman.hurt");
    public static final RegistryObject<SoundEvent> CAIMAN_SPLASH = registerSoundEvent("entity.caiman.splash");
    public static final RegistryObject<SoundEvent> LOBSTER_ATTACK = registerSoundEvent("entity.lobster.attack");
    public static final RegistryObject<SoundEvent> LOBSTER_HURT = registerSoundEvent("entity.lobster.hurt");
    public static final RegistryObject<SoundEvent> MANTIS_SHRIMP_SNAP = registerSoundEvent("entity.mantis_shrimp.snap");
    public static final RegistryObject<SoundEvent> MANTIS_SHRIMP_HURT = registerSoundEvent("entity.mantis_shrimp.hurt");
    public static final RegistryObject<SoundEvent> MIMIC_OCTOPUS_IDLE = registerSoundEvent("entity.mimic_octopus.idle");
    public static final RegistryObject<SoundEvent> MIMIC_OCTOPUS_HURT = registerSoundEvent("entity.mimic_octopus.hurt");
    public static final RegistryObject<SoundEvent> ALLIGATOR_SNAPPING_TURTLE_IDLE = registerSoundEvent("entity.alligator_snapping_turtle.idle");
    public static final RegistryObject<SoundEvent> ALLIGATOR_SNAPPING_TURTLE_HURT = registerSoundEvent("entity.alligator_snapping_turtle.hurt");
    public static final RegistryObject<SoundEvent> ANACONDA_ATTACK = registerSoundEvent("entity.anaconda.attack");
    public static final RegistryObject<SoundEvent> ANACONDA_HURT = registerSoundEvent("entity.anaconda.hurt");
    public static final RegistryObject<SoundEvent> ANACONDA_SLITHER = registerSoundEvent("entity.anaconda.slither");
    public static final RegistryObject<SoundEvent> ANTEATER_HURT = registerSoundEvent("entity.anteater.hurt");
    public static final RegistryObject<SoundEvent> LEAFCUTTER_ANT_HURT = registerSoundEvent("entity.leafcutter_ant.hurt");
    public static final RegistryObject<SoundEvent> LEAFCUTTER_ANT_QUEEN_HURT = registerSoundEvent("entity.leafcutter_ant.queen_hurt");
    public static final RegistryObject<SoundEvent> BALD_EAGLE_HURT = registerSoundEvent("entity.bald_eagle.hurt");
    public static final RegistryObject<SoundEvent> BALD_EAGLE_IDLE = registerSoundEvent("entity.bald_eagle.idle");
    public static final RegistryObject<SoundEvent> SHOEBILL_HURT = registerSoundEvent("entity.shoebill.hurt");
    public static final RegistryObject<SoundEvent> CACHALOT_WHALE_HURT = registerSoundEvent("entity.cachalot_whale.hurt");
    public static final RegistryObject<SoundEvent> CACHALOT_WHALE_IDLE = registerSoundEvent("entity.cachalot_whale.idle");
    public static final RegistryObject<SoundEvent> CACHALOT_WHALE_CLICK = registerSoundEvent("entity.chachalot_whale.click");
    public static final RegistryObject<SoundEvent> GIANT_SQUID_HURT = registerSoundEvent("entity.giant_squid.hurt");
    public static final RegistryObject<SoundEvent> GIANT_SQUID_GAMES = registerSoundEvent("entity.giant_squid.games");
    public static final RegistryObject<SoundEvent> GIANT_SQUID_TENTACLE = registerSoundEvent("entity.giant_squid.tentacle");
    public static final RegistryObject<SoundEvent> ORCA_IDLE = registerSoundEvent("entity.orca.idle");
    public static final RegistryObject<SoundEvent> ORCA_HURT = registerSoundEvent("entity.orca.hurt");
    public static final RegistryObject<SoundEvent> ORCA_DIE = registerSoundEvent("entity.orca.die");
    public static final RegistryObject<SoundEvent> TEPHRA_WHISTLE = registerSoundEvent("entity.tephra.whistle");
    public static final RegistryObject<SoundEvent> TEPHRA_HIT = registerSoundEvent("entity.tephra.hit");
    public static final RegistryObject<SoundEvent> CAPUCHIN_MONKEY_HURT = registerSoundEvent("entity.capuchin_monkey.hurt");
    public static final RegistryObject<SoundEvent> CAPUCHIN_MONKEY_IDLE = registerSoundEvent("entity.capuchin_monkey.idle");
    public static final RegistryObject<SoundEvent> CENTIPEDE_ATTACK = registerSoundEvent("entity.cave_centipede.attack");
    public static final RegistryObject<SoundEvent> CENTIPEDE_HURT = registerSoundEvent("entity.cave_centipede.hurt");
    public static final RegistryObject<SoundEvent> CENTIPEDE_WALK = registerSoundEvent("entity.cave_centipede.walk");
    public static final RegistryObject<SoundEvent> COCKROACH_HURT = registerSoundEvent("entity.cockroach.hurt");
    public static final RegistryObject<SoundEvent> EMU_HURT = registerSoundEvent("entity.emu.hurt");
    public static final RegistryObject<SoundEvent> EMU_IDLE = registerSoundEvent("entity.emu.idle");
    public static final RegistryObject<SoundEvent> COMB_JELLY_HURT = registerSoundEvent("entity.comb_jelly.hurt");
    public static final RegistryObject<SoundEvent> CROW_HURT = registerSoundEvent("entity.crow.hurt");
    public static final RegistryObject<SoundEvent> CROW_IDLE = registerSoundEvent("entity.crow.idle");
    public static final RegistryObject<SoundEvent> COSMIC_COD_HURT = registerSoundEvent("entity.cosmic_cod.hurt");
    public static final RegistryObject<SoundEvent> ELEPHANT_WALK = registerSoundEvent("entity.elephant.walk");
    public static final RegistryObject<SoundEvent> ELEPHANT_TRUMPET = registerSoundEvent("entity.elephant.trumpet");
    public static final RegistryObject<SoundEvent> ELEPHANT_HURT = registerSoundEvent("entity.elephant.hurt");
    public static final RegistryObject<SoundEvent> ELEPHANT_IDLE = registerSoundEvent("entity.elephant.idle");
    public static final RegistryObject<SoundEvent> ELEPHANT_DIE = registerSoundEvent("entity.elephant.die");
    public static final RegistryObject<SoundEvent> ENDERGRADE_HURT = registerSoundEvent("entity.endergrade.hurt");
    public static final RegistryObject<SoundEvent> ENDERGRADE_IDLE = registerSoundEvent("entity.endergrade.idle");
    public static final RegistryObject<SoundEvent> ENDERIOPHAGE_WALK = registerSoundEvent("entity.enderiophage.walk");
    public static final RegistryObject<SoundEvent> ENDERIOPHAGE_SQUISH = registerSoundEvent("entity.enderiophage.squish");
    public static final RegistryObject<SoundEvent> ENDERIOPHAGE_HURT = registerSoundEvent("entity.enderiophage.hurt");
    public static final RegistryObject<SoundEvent> GAZELLE_HURT = registerSoundEvent("entity.gazelle.hurt");
    public static final RegistryObject<SoundEvent> GELADA_MONKEY_HURT = registerSoundEvent("entity.gelada_monkey.hurt");
    public static final RegistryObject<SoundEvent> GELADA_MONKEY_IDLE = registerSoundEvent("entity.gelada_monkey.idle");
    public static final RegistryObject<SoundEvent> GORILLA_IDLE = registerSoundEvent("entity.gorilla.idle");
    public static final RegistryObject<SoundEvent> GORILLA_HURT = registerSoundEvent("entity.gorilla.hurt");
    public static final RegistryObject<SoundEvent> GRIZZLY_BEAR_HURT = registerSoundEvent("entity.grizzly_bear.hurt");
    public static final RegistryObject<SoundEvent> GRIZZLY_BEAR_DIE = registerSoundEvent("entity.grizzly_bear.die");
    public static final RegistryObject<SoundEvent> GRIZZLY_BEAR_IDLE = registerSoundEvent("entity.grizzly_bear.idle");
    public static final RegistryObject<SoundEvent> APRIL_FOOLS_POWER_OUTAGE = registerSoundEvent("entity.grizzly_bear.power_outage");
    public static final RegistryObject<SoundEvent> APRIL_FOOLS_SCREAM = registerSoundEvent("entity.grizzly_bear.scream");
    public static final RegistryObject<SoundEvent> KOMODO_DRAGON_HURT = registerSoundEvent("entity.komodo_dragon.hurt");
    public static final RegistryObject<SoundEvent> KOMODO_DRAGON_IDLE = registerSoundEvent("entity.komodo_dragon.idle");
    public static final RegistryObject<SoundEvent> UNDERMINER_HURT = registerSoundEvent("entity.underminer.hurt");
    public static final RegistryObject<SoundEvent> UNDERMINER_STEP = registerSoundEvent("entity.underminer.step");
    public static final RegistryObject<SoundEvent> UNDERMINER_IDLE = registerSoundEvent("entity.underminer.idle");
    public static final RegistryObject<SoundEvent> TOUCAN_HURT = registerSoundEvent("entity.toucan.hurt");
    public static final RegistryObject<SoundEvent> TOUCAN_IDLE = registerSoundEvent("entity.toucan.idle");
    public static final RegistryObject<SoundEvent> TIGER_IDLE = registerSoundEvent("entity.tiger.idle");
    public static final RegistryObject<SoundEvent> TIGER_HURT = registerSoundEvent("entity.tiger.hurt");
    public static final RegistryObject<SoundEvent> TIGER_ANGRY = registerSoundEvent("entity.tiger.angry");
    public static final RegistryObject<SoundEvent> SNOW_LEOPARD_HURT = registerSoundEvent("entity.snow_leopard.hurt");
    public static final RegistryObject<SoundEvent> SNOW_LEOPARD_IDLE = registerSoundEvent("entity.snow_leopard.idle");
    public static final RegistryObject<SoundEvent> SEAGULL_IDLE = registerSoundEvent("entity.seagull.idle");
    public static final RegistryObject<SoundEvent> SEAGULL_HURT = registerSoundEvent("entity.seagull.hurt");
    public static final RegistryObject<SoundEvent> SEAL_IDLE = registerSoundEvent("entity.seal.idle");
    public static final RegistryObject<SoundEvent> SEAL_HURT = registerSoundEvent("entity.seal.hurt");
    public static final RegistryObject<SoundEvent> ROADRUNNER_HURT = registerSoundEvent("entity.roadrunner.hurt");
    public static final RegistryObject<SoundEvent> ROADRUNNER_IDLE = registerSoundEvent("entity.roadrunner.idle");
    public static final RegistryObject<SoundEvent> ROADRUNNER_MEEP = registerSoundEvent("entity.roadrunner.meep");
    public static final RegistryObject<SoundEvent> RHINOCEROS_HURT = registerSoundEvent("entity.rhinoceros.hurt");
    public static final RegistryObject<SoundEvent> RHINOCEROS_IDLE = registerSoundEvent("entity.rhinoceros.idle");
    public static final RegistryObject<SoundEvent> POTOO_CALL = registerSoundEvent("entity.potoo.call");
    public static final RegistryObject<SoundEvent> POTOO_HURT = registerSoundEvent("entity.potoo.hurt");




    // blocks
    public static final RegistryObject<SoundEvent> BUSH1 = registerSoundEvent("block.firefly_bush.bush1");
    public static final RegistryObject<SoundEvent> BUSH2 = registerSoundEvent("block.firefly_bush.bush2");
    public static final RegistryObject<SoundEvent> BUSH3 = registerSoundEvent("block.firefly_bush.bush3");
    public static final RegistryObject<SoundEvent> BUSH4 = registerSoundEvent("block.firefly_bush.bush4");
    public static final RegistryObject<SoundEvent> BUSH5 = registerSoundEvent("block.firefly_bush.bush5");
    public static final RegistryObject<SoundEvent> BUSH6 = registerSoundEvent("block.firefly_bush.bush6");
    public static final RegistryObject<SoundEvent> BUSH7 = registerSoundEvent("block.firefly_bush.bush7");
    public static final RegistryObject<SoundEvent> BUSH8 = registerSoundEvent("block.firefly_bush.bush8");
    public static final RegistryObject<SoundEvent> BUSH9 = registerSoundEvent("block.firefly_bush.bush9");
    public static final RegistryObject<SoundEvent> BUSH10 = registerSoundEvent("block.firefly_bush.bush10");
    public static final RegistryObject<SoundEvent> BUSH11 = registerSoundEvent("block.firefly_bush.bush11");
    public static final RegistryObject<SoundEvent> PEWEN_BRANCH_BREAK = registerSoundEvent("block.pewen_branch.break");
    public static final RegistryObject<SoundEvent> AMBER_BREAK = registerSoundEvent("block.amber.break");
    public static final RegistryObject<SoundEvent> AMBER_BREAKING = registerSoundEvent("block.amber.breaking");
    public static final RegistryObject<SoundEvent> AMBER_MONOLITH_PLACE = registerSoundEvent("block.amber.monolith_place");
    public static final RegistryObject<SoundEvent> AMBER_MONOLITH_SUMMON = registerSoundEvent("block.amber.monolith_summon");
    public static final RegistryObject<SoundEvent> AMBER_PLACE = registerSoundEvent("block.amber.place");
    public static final RegistryObject<SoundEvent> AMBER_STEP = registerSoundEvent("block.amber.step");
    public static final RegistryObject<SoundEvent> FLOOD_BASALT_STEP = registerSoundEvent("block.flood_basalt.step");
    public static final RegistryObject<SoundEvent> FLOOD_BASALT_PLACE = registerSoundEvent("block.flood_basalt.place");
    public static final RegistryObject<SoundEvent> FLOOD_BASALT_BREAK = registerSoundEvent("block.flood_basalt.break");
    public static final RegistryObject<SoundEvent> FLOOD_BASALT_BREAKING = registerSoundEvent("block.flood_basalt.breaking");
    public static final RegistryObject<SoundEvent> PRIMAL_MAGMA_FISSURE_CLOSE = registerSoundEvent("block.primal_magma.fissure_close");
    public static final RegistryObject<SoundEvent> TELEPORT = registerSoundEvent("block.elevator.teleport");


    // Items
    public static final RegistryObject<SoundEvent> TECTONIC_SHARD_TRANSFORM = registerSoundEvent("item.tectonic_shard.transform");
    public static final RegistryObject<SoundEvent> MARACA = registerSoundEvent("item.maracas.maraca");




    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_BOSS_MUSIC = registerSoundEvent("luxtructosaurus_boss_music");
    public static final RegistryObject<SoundEvent> PRIMORDIAL_CAVES_MUSIC = registerSoundEvent("primordial_caves_music");
    public static final RegistryObject<SoundEvent> PRIMRDIAL_CAVES_AMBIENCE = registerSoundEvent("primordial_caves_ambience");
    public static final RegistryObject<SoundEvent> PRIMORDIAL_CAVES_AMBIENCE_ADDITIONS = registerSoundEvent("primordial_caves_ambience_additions");
    public static final RegistryObject<SoundEvent> PRIMORDIAL_CAVES_AMBIENCE_MOOD = registerSoundEvent("primordial_caves_ambience_mood");

    public static final RegistryObject<SoundEvent> SUBTERRANODON_IDLE = registerSoundEvent("entity.subterranodon.idle");
    public static final RegistryObject<SoundEvent> SUBTERRANODON_HURT = registerSoundEvent("entity.subterranodon.hurt");
    public static final RegistryObject<SoundEvent> SUBTERRANODON_DEATH = registerSoundEvent("entity.subterranodon.death");
    public static final RegistryObject<SoundEvent> SUBTERRANODON_FLAP = registerSoundEvent("entity.subterranodon.flap");
    public static final RegistryObject<SoundEvent> SUBTERRANODON_ATTACK = registerSoundEvent("entity.subterranodon.attack");
    public static final RegistryObject<SoundEvent> VALLUMRAPTOR_IDLE = registerSoundEvent("entity.vallumraptor.idle");
    public static final RegistryObject<SoundEvent> VALLUMRAPTOR_HURT = registerSoundEvent("entity.vallumraptor.hurt");
    public static final RegistryObject<SoundEvent> VALLUMRAPTOR_DEATH = registerSoundEvent("entity.vallumraptor.death");
    public static final RegistryObject<SoundEvent> VALLUMRAPTOR_CALL = registerSoundEvent("entity.vallumraptor.call");
    public static final RegistryObject<SoundEvent> VALLUMRAPTOR_ATTACK = registerSoundEvent("entity.vallumraptor.attack");
    public static final RegistryObject<SoundEvent> VALLUMRAPTOR_SCRATCH = registerSoundEvent("entity.vallumraptor.scratch");
    public static final RegistryObject<SoundEvent> VALLUMRAPTOR_SLEEP = registerSoundEvent("entity.vallumraptor.sleep");
    public static final RegistryObject<SoundEvent> GROTTOCERATOPS_IDLE = registerSoundEvent("entity.grottoceratops.idle");
    public static final RegistryObject<SoundEvent> GROTTOCERATOPS_HURT = registerSoundEvent("entity.grottoceratops.hurt");
    public static final RegistryObject<SoundEvent> GROTTOCERATOPS_DEATH = registerSoundEvent("entity.grottoceratops.death");
    public static final RegistryObject<SoundEvent> GROTTOCERATOPS_CALL = registerSoundEvent("entity.grottoceratops.call");
    public static final RegistryObject<SoundEvent> GROTTOCERATOPS_ATTACK = registerSoundEvent("entity.grottoceratops.attack");
    public static final RegistryObject<SoundEvent> GROTTOCERATOPS_GRAZE = registerSoundEvent("entity.grottoceratops.graze");
    public static final RegistryObject<SoundEvent> GROTTOCERATOPS_STEP = registerSoundEvent("entity.grottoceratops.step");
    public static final RegistryObject<SoundEvent> TRILOCARIS_HURT = registerSoundEvent("entity.trilocaris.hurt");
    public static final RegistryObject<SoundEvent> TRILOCARIS_DEATH = registerSoundEvent("entity.trilocaris.death");
    public static final RegistryObject<SoundEvent> TRILOCARIS_STEP = registerSoundEvent("entity.trilocaris.step");
    public static final RegistryObject<SoundEvent> TREMORSAURUS_IDLE = registerSoundEvent("entity.tremorsaurus.idle");
    public static final RegistryObject<SoundEvent> TREMORSAURUS_HURT = registerSoundEvent("entity.tremorsaurus.hurt");
    public static final RegistryObject<SoundEvent> TREMORSAURUS_DEATH = registerSoundEvent("entity.tremorsaurus.death");
    public static final RegistryObject<SoundEvent> TREMORSAURUS_BITE = registerSoundEvent("entity.tremorsaurus.bite");
    public static final RegistryObject<SoundEvent> TREMORSAURUS_ROAR = registerSoundEvent("entity.tremorsaurus.roar");
    public static final RegistryObject<SoundEvent> TREMORSAURUS_THROW = registerSoundEvent("entity.tremorsaurus.throw");
    public static final RegistryObject<SoundEvent> TREMORSAURUS_STOMP = registerSoundEvent("entity.tremorsaurus.stomp");
    public static final RegistryObject<SoundEvent> RELICHEIRUS_IDLE = registerSoundEvent("entity.relicheirus.idle");
    public static final RegistryObject<SoundEvent> RELICHEIRUS_HURT = registerSoundEvent("entity.relicheirus.hurt");
    public static final RegistryObject<SoundEvent> RELICHEIRUS_DEATH = registerSoundEvent("entity.relicheirus.death");
    public static final RegistryObject<SoundEvent> RELICHEIRUS_SCRATCH = registerSoundEvent("entity.relicheirus.scratch");
    public static final RegistryObject<SoundEvent> RELICHEIRUS_STEP = registerSoundEvent("entity.relicheirus.step");
    public static final RegistryObject<SoundEvent> RELICHEIRUS_TOPPLE = registerSoundEvent("entity.relicheirus.topple");
    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_IDLE = registerSoundEvent("entity.luxtructosaurus.idle");
    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_HURT = registerSoundEvent("entity.luxtructosaurus.hurt");
    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_DEATH = registerSoundEvent("entity.luxtructosaurus.death");
    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_SNORT = registerSoundEvent("entity.luxtructosaurus.snort");
    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_STEP = registerSoundEvent("entity.luxtructosaurus.step");
    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_STOMP = registerSoundEvent("entity.luxtructosaurus.stomp");
    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_ROAR = registerSoundEvent("entity.luxtructosaurus.roar");
    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_ATTACK_STOMP = registerSoundEvent("entity.luxtructosaurus.attack_stomp");
    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_KICK = registerSoundEvent("entity.luxtructosaurus.kick");
    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_TAIL = registerSoundEvent("entity.luxtructosaurus.tail");
    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_BREATH = registerSoundEvent("entity.luxtructosaurus.breath");
    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_SUMMON = registerSoundEvent("entity.luxtructosaurus.summon");
    public static final RegistryObject<SoundEvent> LUXTRUCTOSAURUS_JUMP = registerSoundEvent("entity.luxtructosaurus.jump");
    public static final RegistryObject<SoundEvent> ATLATITAN_IDLE = registerSoundEvent("entity.atlatitan.idle");
    public static final RegistryObject<SoundEvent> ATLATITAN_HURT = registerSoundEvent("entity.atlatitan.hurt");
    public static final RegistryObject<SoundEvent> ATLATITAN_DEATH = registerSoundEvent("entity.atlatitan.death");
    public static final RegistryObject<SoundEvent> ATLATITAN_STEP = registerSoundEvent("entity.atlatitan.step");
    public static final RegistryObject<SoundEvent> ATLATITAN_STOMP = registerSoundEvent("entity.atlatitan.stomp");
    public static final RegistryObject<SoundEvent> ATLATITAN_KICK = registerSoundEvent("entity.atlatitan.kick");
    public static final RegistryObject<SoundEvent> ATLATITAN_TAIL = registerSoundEvent("entity.atlatitan.tail");
    public static final RegistryObject<SoundEvent> TREMORZILLA_IDLE = registerSoundEvent("entity.tremorzilla.idle");
    public static final RegistryObject<SoundEvent> TREMORZILLA_HURT = registerSoundEvent("entity.tremorzilla.hurt");
    public static final RegistryObject<SoundEvent> TREMORZILLA_DEATH = registerSoundEvent("entity.tremorzilla.death");
    public static final RegistryObject<SoundEvent> TREMORZILLA_STOMP = registerSoundEvent("entity.tremorzilla.stomp");
    public static final RegistryObject<SoundEvent> TREMORZILLA_BEAM_START = registerSoundEvent("entity.tremorzilla.beam_start");
    public static final RegistryObject<SoundEvent> TREMORZILLA_BEAM_END = registerSoundEvent("entity.tremorzilla.beam_end");
    public static final RegistryObject<SoundEvent> TREMORZILLA_BEAM_LOOP = registerSoundEvent("entity.tremorzilla.beam_loop");
    public static final RegistryObject<SoundEvent> TREMORZILLA_CHARGE_NORMAL = registerSoundEvent("entity.tremorzilla.charge_normal");
    public static final RegistryObject<SoundEvent> TREMORZILLA_CHARGE_COMPLETE = registerSoundEvent("entity.tremorzilla.charge_complete");
    public static final RegistryObject<SoundEvent> TREMORZILLA_ROAR = registerSoundEvent("entity.tremorzilla.roar");
    public static final RegistryObject<SoundEvent> TREMORZILLA_EAT = registerSoundEvent("entity.tremorzilla.eat");
    public static final RegistryObject<SoundEvent> TREMORZILLA_BITE = registerSoundEvent("entity.tremorzilla.bite");
    public static final RegistryObject<SoundEvent> TREMORZILLA_SCRATCH_ATTACK = registerSoundEvent("entity.tremorzilla.scratch_attack");
    public static final RegistryObject<SoundEvent> TREMORZILLA_STOMP_ATTACK = registerSoundEvent("entity.tremorzilla.stomp_attack");
    public static final RegistryObject<SoundEvent> TREMORZILLA_TAIL_ATTACK = registerSoundEvent("entity.tremorzilla.tail_attack");



    //helpers
    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceIdentifier(name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}