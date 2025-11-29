// Java
package net.matt.quantize.entities;

import net.matt.quantize.block.BlockEntity.CrushedBlockEntity;
import net.matt.quantize.entities.mobs.EntityTossedItem;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.matt.quantize.Quantize;
import net.matt.quantize.entities.mobs.*;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import java.util.function.Predicate;
import com.google.common.base.Predicates;
import net.matt.quantize.block.BlockEntity.FallingTreeBlockEntity;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.Blocks;

@Mod.EventBusSubscriber(modid = Quantize.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class QEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Quantize.MOD_ID);
    public static final MobCategory CAVE_CREATURES = MobCategory.create("cave_creatures", "quantize:cave_creatures", 10, true, true, 128);

    public static final RegistryObject<EntityType<Crab>> CRAB = ENTITY_TYPES.register("crab",
            () -> EntityType.Builder.<Crab>of(Crab::new, MobCategory.CREATURE)
                    .sized(0.9F, 0.5F)
                    .clientTrackingRange(8)
                    .build(new ResourceIdentifier("crab").toString()));
    public static final RegistryObject<EntityType<EntityCaiman>> CAIMAN = ENTITY_TYPES.register("caiman",
            () -> EntityType.Builder.<EntityCaiman>of(EntityCaiman::new, MobCategory.CREATURE)
                    .sized(1.3F, 0.6F)
                    .clientTrackingRange(10)
                    .build(new ResourceIdentifier("caiman").toString()));
    public static final RegistryObject<EntityType<EntityCrocodile>> CROCODILE = ENTITY_TYPES.register("crocodile",
            () -> EntityType.Builder.<EntityCrocodile>of(EntityCrocodile::create, MobCategory.CREATURE)
                    .sized(2.15F, 0.75F)
                    .clientTrackingRange(5)
                    .build(new ResourceIdentifier("crocodile").toString()));
    public static final RegistryObject<EntityType<EntityLobster>> LOBSTER = ENTITY_TYPES.register("lobster",
            () -> EntityType.Builder.<EntityLobster>of(EntityLobster::create, MobCategory.WATER_AMBIENT)
                    .sized(0.7F, 0.4F)
                    .clientTrackingRange(5)
                    .build(new ResourceIdentifier("lobster").toString()));
    public static final RegistryObject<EntityType<EntityMantisShrimp>> MANTIS_SHRIMP = ENTITY_TYPES.register("mantis_shrimp",
            () -> EntityType.Builder.<EntityMantisShrimp>of(EntityMantisShrimp::create, MobCategory.WATER_CREATURE)
                    .sized(1.25F, 1.2F)
                    .clientTrackingRange(10)
                    .build(new ResourceIdentifier("mantis_shrimp").toString()));
    public static final RegistryObject<EntityType<EntityMimicOctopus>> MIMIC_OCTOPUS = ENTITY_TYPES.register("mimic_octopus",
            () -> EntityType.Builder.<EntityMimicOctopus>of(EntityMimicOctopus::create, MobCategory.WATER_CREATURE)
                    .sized(0.9F, 0.6F)
                    .clientTrackingRange(8)
                    .build(new ResourceIdentifier("mimic_octopus").toString()));
    public static final RegistryObject<EntityType<EntityAlligatorSnappingTurtle>> ALLIGATOR_SNAPPING_TURTLE = ENTITY_TYPES.register("alligator_snapping_turtle",
            () -> EntityType.Builder.<EntityAlligatorSnappingTurtle>of(EntityAlligatorSnappingTurtle::create, MobCategory.CREATURE)
                    .sized(1.25F, 0.65F)
                    .clientTrackingRange(10)
                    .build(new ResourceIdentifier("alligator_snapping_turtle").toString()));
    public static final RegistryObject<EntityType<EntityAnaconda>> ANACONDA = ENTITY_TYPES.register("anaconda",
            () -> EntityType.Builder.<EntityAnaconda>of(EntityAnaconda::create, MobCategory.CREATURE)
                    .sized(0.8F, 0.8F)
                    .clientTrackingRange(10)
                    .build(new ResourceIdentifier("anaconda").toString()));
    public static final RegistryObject<EntityType<EntityAnacondaPart>> ANACONDA_PART = ENTITY_TYPES.register("anaconda_part",
            () -> EntityType.Builder.<EntityAnacondaPart>of(EntityAnacondaPart::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F)
                    .clientTrackingRange(1)
                    .build(new ResourceIdentifier("anaconda_part").toString()));
    public static final RegistryObject<EntityType<EntityAnteater>> ANTEATER = ENTITY_TYPES.register("anteater",
            () -> EntityType.Builder.<EntityAnteater>of(EntityAnteater::create, MobCategory.CREATURE)
                    .sized(1.3F, 1.1F)
                    .clientTrackingRange(10)
                    .build(new ResourceIdentifier("anteater").toString()));
    public static final RegistryObject<EntityType<EntityLeafcutterAnt>> LEAFCUTTER_ANT = ENTITY_TYPES.register("leafcutter_ant",
            () -> EntityType.Builder.<EntityLeafcutterAnt>of(EntityLeafcutterAnt::create, MobCategory.CREATURE)
                    .sized(0.8F, 0.5F)
                    .clientTrackingRange(5)
                    .build(new ResourceIdentifier("leafcutter_ant").toString()));
    public static final RegistryObject<EntityType<EntityBaldEagle>> BALD_EAGLE = ENTITY_TYPES.register("bald_eagle",
            () -> EntityType.Builder.<EntityBaldEagle>of(EntityBaldEagle::create, MobCategory.CREATURE)
                    .sized(0.5F, 0.95F)
                    .clientTrackingRange(14)
                    .build(new ResourceIdentifier("bald_eagle").toString()));
    public static final RegistryObject<EntityType<EntityShoebill>> SHOEBILL = ENTITY_TYPES.register("shoebill",
            () -> EntityType.Builder.<EntityShoebill>of(EntityShoebill::create, MobCategory.CREATURE)
                    .sized(0.8F, 1.5F)
                    .clientTrackingRange(10)
                    .build(new ResourceIdentifier("shoebill").toString()));
    public static final RegistryObject<EntityType<EntityCachalotWhale>> CACHALOT_WHALE = ENTITY_TYPES.register("cachalot_whale",
            () -> EntityType.Builder.<EntityCachalotWhale>of(EntityCachalotWhale::create, MobCategory.WATER_CREATURE)
                    .sized(9F, 4F)
                    .clientTrackingRange(10)
                    .build(new ResourceIdentifier("cachalot_whale").toString()));
    public static final RegistryObject<EntityType<EntityCachalotEcho>> CACHALOT_ECHO = ENTITY_TYPES.register("cachalot_echo",
            () -> EntityType.Builder.<EntityCachalotEcho>of(EntityCachalotEcho::new, MobCategory.MISC)
                    .sized(2F, 2F)
                    .setCustomClientFactory(EntityCachalotEcho::new)
                    .fireImmune()
                    .build(new ResourceIdentifier("cachalot_echo").toString()));
    public static final RegistryObject<EntityType<EntityGiantSquid>> GIANT_SQUID = ENTITY_TYPES.register("giant_squid",
            () -> EntityType.Builder.<EntityGiantSquid>of(EntityGiantSquid::create, MobCategory.WATER_CREATURE)
                    .sized(0.9F, 1.2F)
                    .clientTrackingRange(10)
                    .build(new ResourceIdentifier("giant_squid").toString()));
    public static final RegistryObject<EntityType<EntityOrca>> ORCA = ENTITY_TYPES.register("orca",
            () -> EntityType.Builder.<EntityOrca>of(EntityOrca::create, MobCategory.WATER_CREATURE)
                    .sized(3.75F, 1.75F)
                    .clientTrackingRange(10)
                    .build(new ResourceIdentifier("orca").toString()));
    public static final RegistryObject<EntityType<EntityCapuchinMonkey>> CAPUCHIN_MONKEY = ENTITY_TYPES.register("capuchin_monkey",
            () -> EntityType.Builder.<EntityCapuchinMonkey>of(EntityCapuchinMonkey::create, MobCategory.CREATURE)
                    .sized(0.65F, 0.75F)
                    .clientTrackingRange(10)
                    .build(new ResourceIdentifier("capuchin_monkey").toString()));
    public static final RegistryObject<EntityType<EntityTossedItem>> TOSSED_ITEM = ENTITY_TYPES.register("tossed_item", () -> registerEntity(EntityType.Builder.of(EntityTossedItem::new, MobCategory.MISC).sized(0.5F, 0.5F).setCustomClientFactory(EntityTossedItem::new).fireImmune(), "tossed_item"));
    public static final RegistryObject<EntityType<EntityCatfish>> CATFISH = ENTITY_TYPES.register("catfish",
            () -> EntityType.Builder.<EntityCatfish>of(EntityCatfish::create, MobCategory.WATER_AMBIENT)
                    .sized(0.9F, 0.6F)
                    .clientTrackingRange(5)
                    .build(new ResourceIdentifier("catfish").toString()));
    public static final RegistryObject<EntityType<SubterranodonEntity>> SUBTERRANODON = ENTITY_TYPES.register("subterranodon", () -> (EntityType) EntityType.Builder.of(SubterranodonEntity::new, CAVE_CREATURES).sized(1.75F, 1.2F).setTrackingRange(12).setShouldReceiveVelocityUpdates(true).setUpdateInterval(1).build("subterranodon"));
    public static final RegistryObject<EntityType<VallumraptorEntity>> VALLUMRAPTOR = ENTITY_TYPES.register("vallumraptor", () -> (EntityType) EntityType.Builder.of(VallumraptorEntity::new, CAVE_CREATURES).sized(0.8F, 1.5F).setTrackingRange(8).build("vallumraptor"));
    public static final RegistryObject<EntityType<GrottoceratopsEntity>> GROTTOCERATOPS = ENTITY_TYPES.register("grottoceratops", () -> (EntityType) EntityType.Builder.of(GrottoceratopsEntity::new, CAVE_CREATURES).sized(2.3F, 2.5F).setTrackingRange(8).build("grottoceratops"));
    public static final RegistryObject<EntityType<TrilocarisEntity>> TRILOCARIS = ENTITY_TYPES.register("trilocaris", () -> (EntityType) EntityType.Builder.of(TrilocarisEntity::new, MobCategory.WATER_AMBIENT).sized(0.9F, 0.4F).build("trilocaris"));
    public static final RegistryObject<EntityType<TremorsaurusEntity>> TREMORSAURUS = ENTITY_TYPES.register("tremorsaurus", () -> (EntityType) EntityType.Builder.of(TremorsaurusEntity::new, CAVE_CREATURES).sized(2.5F, 3.85F).setTrackingRange(8).build("tremorsaurus"));
    public static final RegistryObject<EntityType<RelicheirusEntity>> RELICHEIRUS = ENTITY_TYPES.register("relicheirus", () -> (EntityType) EntityType.Builder.of(RelicheirusEntity::new, CAVE_CREATURES).sized(2.65F, 5.9F).setTrackingRange(9).build("relicheirus"));
    public static final RegistryObject<EntityType<LuxtructosaurusEntity>> LUXTRUCTOSAURUS = ENTITY_TYPES.register("luxtructosaurus", () -> (EntityType) EntityType.Builder.of(LuxtructosaurusEntity::new, MobCategory.MONSTER).sized(6.0F, 8.5F).setTrackingRange(12).fireImmune().build("luxtructosaurus"));
    public static final RegistryObject<EntityType<TephraEntity>> TEPHRA = ENTITY_TYPES.register("tephra", () -> (EntityType) EntityType.Builder.of(TephraEntity::new, MobCategory.MISC).sized(0.6F, 0.6F).setCustomClientFactory(TephraEntity::new).fireImmune().setShouldReceiveVelocityUpdates(true).setUpdateInterval(1).build("tephra"));
    public static final RegistryObject<EntityType<AtlatitanEntity>> ATLATITAN = ENTITY_TYPES.register("atlatitan", () -> (EntityType) EntityType.Builder.of(AtlatitanEntity::new, CAVE_CREATURES).sized(5.0F, 8.0F).setTrackingRange(11).build("atlatitan"));
    public static final RegistryObject<EntityType<FallingTreeBlockEntity>> FALLING_TREE_BLOCK = ENTITY_TYPES.register("falling_tree_block", () -> (EntityType) EntityType.Builder.of(FallingTreeBlockEntity::new, MobCategory.MISC).sized(0.99F, 0.99F).setCustomClientFactory(FallingTreeBlockEntity::new).setUpdateInterval(1).setShouldReceiveVelocityUpdates(true).updateInterval(10).clientTrackingRange(20).build("falling_tree_block"));
    public static final RegistryObject<EntityType<CrushedBlockEntity>> CRUSHED_BLOCK = ENTITY_TYPES.register("crushed_block", () -> (EntityType) EntityType.Builder.of(CrushedBlockEntity::new, MobCategory.MISC).sized(0.99F, 0.99F).setCustomClientFactory(CrushedBlockEntity::new).setUpdateInterval(1).setShouldReceiveVelocityUpdates(true).updateInterval(10).clientTrackingRange(20).build("crushed_block"));
    public static final RegistryObject<EntityType<DinosaurSpiritEntity>> DINOSAUR_SPIRIT = ENTITY_TYPES.register("dinosaur_spirit", () -> (EntityType) EntityType.Builder.of(DinosaurSpiritEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).setCustomClientFactory(DinosaurSpiritEntity::new).setUpdateInterval(1).setShouldReceiveVelocityUpdates(true).fireImmune().build("dinosaur_spirit"));
    public static final RegistryObject<EntityType<EntityFrilledShark>> FRILLED_SHARK = ENTITY_TYPES.register("frilled_shark",
            () -> EntityType.Builder.<EntityFrilledShark>of(EntityFrilledShark::create, MobCategory.WATER_CREATURE)
                    .sized(1.3F, 0.4F)
                    .clientTrackingRange(8)
                    .build(new ResourceIdentifier("frilled_shark").toString()));
    public static final RegistryObject<EntityType<EntityBlobfish>> BLOBFISH = ENTITY_TYPES.register("blobfish",
            () -> EntityType.Builder.<EntityBlobfish>of(EntityBlobfish::create, MobCategory.WATER_AMBIENT)
                    .sized(0.7F, 0.45F)
                    .clientTrackingRange(5)
                    .build(new ResourceIdentifier("blobfish").toString()));
    public static final RegistryObject<EntityType<EntityCombJelly>> COMB_JELLY = ENTITY_TYPES.register("comb_jelly",
            () -> EntityType.Builder.<EntityCombJelly>of(EntityCombJelly::create, MobCategory.WATER_AMBIENT)
                    .sized(0.7F, 0.45F)
                    .clientTrackingRange(5)
                    .build(new ResourceIdentifier("comb_jelly").toString()));
    public static final RegistryObject<EntityType<EntityCrow>> CROW = ENTITY_TYPES.register("crow",
            () -> EntityType.Builder.<EntityCrow>of(EntityCrow::create, MobCategory.CREATURE)
                    .sized(0.45F, 0.45F)
                    .clientTrackingRange(10)
                    .build(new ResourceIdentifier("crow").toString()));
    public static final RegistryObject<EntityType<EntityCosmicCod>> COSMIC_COD = ENTITY_TYPES.register("cosmic_cod",
            () -> EntityType.Builder.<EntityCosmicCod>of(EntityCosmicCod::create, MobCategory.AMBIENT)
                    .sized(0.85F, 0.4F)
                    .clientTrackingRange(5)
                    .build(new ResourceIdentifier("cosmic_cod").toString()));
    public static final RegistryObject<EntityType<EntityGazelle>> GAZELLE = ENTITY_TYPES.register("gazelle", () -> registerEntity(EntityType.Builder.of(EntityGazelle::new, MobCategory.CREATURE).sized(0.85F, 1.25F).setTrackingRange(10), "gazelle"));
    public static final RegistryObject<EntityType<EntityCentipedeHead>> CENTIPEDE_HEAD = ENTITY_TYPES.register("centipede_head", () -> registerEntity(EntityType.Builder.of(EntityCentipedeHead::new, MobCategory.MONSTER).sized(0.9F, 0.9F).setTrackingRange(8), "centipede_head"));
    public static final RegistryObject<EntityType<EntityCentipedeBody>> CENTIPEDE_BODY = ENTITY_TYPES.register("centipede_body", () -> registerEntity(EntityType.Builder.of(EntityCentipedeBody::new, MobCategory.MISC).sized(0.9F, 0.9F).fireImmune().setShouldReceiveVelocityUpdates(true).setUpdateInterval(1).setTrackingRange(8), "centipede_body"));
    public static final RegistryObject<EntityType<EntityCentipedeTail>> CENTIPEDE_TAIL = ENTITY_TYPES.register("centipede_tail", () -> registerEntity(EntityType.Builder.of(EntityCentipedeTail::new, MobCategory.MISC).sized(0.9F, 0.9F).fireImmune().setShouldReceiveVelocityUpdates(true).setUpdateInterval(1).setTrackingRange(8), "centipede_tail"));
    public static final RegistryObject<EntityType<EntityCockroach>> COCKROACH = ENTITY_TYPES.register("cockroach", () -> registerEntity(EntityType.Builder.of(EntityCockroach::new, MobCategory.AMBIENT).sized(0.7F, 0.3F).setTrackingRange(5), "cockroach"));
    public static final RegistryObject<EntityType<EntityCockroachEgg>> COCKROACH_EGG = ENTITY_TYPES.register("cockroach_egg", () -> registerEntity(EntityType.Builder.of(EntityCockroachEgg::new, MobCategory.MISC).sized(0.5F, 0.5F).setCustomClientFactory(EntityCockroachEgg::new).fireImmune(), "cockroach_egg"));
    public static final RegistryObject<EntityType<EntityEmu>> EMU = ENTITY_TYPES.register("emu", () -> registerEntity(EntityType.Builder.of(EntityEmu::new, MobCategory.CREATURE).sized(1.1F, 1.8F).setTrackingRange(10), "emu"));
    public static final RegistryObject<EntityType<EntityEmuEgg>> EMU_EGG = ENTITY_TYPES.register("emu_egg", () -> registerEntity(EntityType.Builder.of(EntityEmuEgg::new, MobCategory.MISC).sized(0.5F, 0.5F).setCustomClientFactory(EntityEmuEgg::new).fireImmune(), "emu_egg"));
    public static final RegistryObject<EntityType<EntityElephant>> ELEPHANT = ENTITY_TYPES.register("elephant", () -> registerEntity(EntityType.Builder.of(EntityElephant::new, MobCategory.CREATURE).sized(3.1F, 3.5F).setUpdateInterval(1).setTrackingRange(10), "elephant"));
    public static final RegistryObject<EntityType<EntityEndergrade>> ENDERGRADE = ENTITY_TYPES.register("endergrade", () -> registerEntity(EntityType.Builder.of(EntityEndergrade::new, MobCategory.CREATURE).sized(0.95F, 0.85F).setTrackingRange(10), "endergrade"));
    public static final RegistryObject<EntityType<EntityEnderiophage>> ENDERIOPHAGE = ENTITY_TYPES.register("enderiophage", () -> registerEntity(EntityType.Builder.of(EntityEnderiophage::new, MobCategory.CREATURE).sized(0.85F, 1.95F).setUpdateInterval(1).setTrackingRange(8), "enderiophage"));
    public static final RegistryObject<EntityType<EntityGeladaMonkey>> GELADA_MONKEY = ENTITY_TYPES.register("gelada_monkey", () -> registerEntity(EntityType.Builder.of(EntityGeladaMonkey::new, MobCategory.CREATURE).sized(1.2F, 1.2F).setTrackingRange(10), "gelada_monkey"));
    public static final RegistryObject<EntityType<EntityGorilla>> GORILLA = ENTITY_TYPES.register("gorilla", () -> registerEntity(EntityType.Builder.of(EntityGorilla::new, MobCategory.CREATURE).sized(1.15F, 1.35F).setTrackingRange(10), "gorilla"));
    public static final RegistryObject<EntityType<EntityGrizzlyBear>> GRIZZLY_BEAR = ENTITY_TYPES.register("grizzly_bear", () -> registerEntity(EntityType.Builder.of(EntityGrizzlyBear::new, MobCategory.CREATURE).sized(1.6F, 1.8F).setTrackingRange(10), "grizzly_bear"));
    public static final RegistryObject<EntityType<EntityHammerheadShark>> HAMMERHEAD_SHARK = ENTITY_TYPES.register("hammerhead_shark", () -> registerEntity(EntityType.Builder.of(EntityHammerheadShark::new, MobCategory.WATER_CREATURE).sized(2.4F, 1.25F).setTrackingRange(10), "hammerhead_shark"));
    public static final RegistryObject<EntityType<EntityKomodoDragon>> KOMODO_DRAGON = ENTITY_TYPES.register("komodo_dragon", () -> registerEntity(EntityType.Builder.of(EntityKomodoDragon::new, MobCategory.CREATURE).sized(1.9F, 0.9F).setTrackingRange(10), "komodo_dragon"));
    public static final RegistryObject<EntityType<EntityUnderminer>> UNDERMINER = ENTITY_TYPES.register("underminer", () -> registerEntity(EntityType.Builder.of(EntityUnderminer::new, MobCategory.AMBIENT).sized(0.8F, 1.8F).setTrackingRange(8), "underminer"));
    public static final RegistryObject<EntityType<EntityToucan>> TOUCAN = ENTITY_TYPES.register("toucan", () -> registerEntity(EntityType.Builder.of(EntityToucan::new, MobCategory.CREATURE).sized(0.45F, 0.45F).setTrackingRange(10), "toucan"));
    public static final RegistryObject<EntityType<EntityTiger>> TIGER = ENTITY_TYPES.register("tiger", () -> registerEntity(EntityType.Builder.of(EntityTiger::new, MobCategory.CREATURE).sized(1.45F, 1.2F).setTrackingRange(10), "tiger"));
    public static final RegistryObject<EntityType<EntitySnowLeopard>> SNOW_LEOPARD = ENTITY_TYPES.register("snow_leopard", () -> registerEntity(EntityType.Builder.of(EntitySnowLeopard::new, MobCategory.CREATURE).sized(1.2F, 1.3F).immuneTo(Blocks.POWDER_SNOW).setTrackingRange(10), "snow_leopard"));
    public static final RegistryObject<EntityType<EntitySeal>> SEAL = ENTITY_TYPES.register("seal", () -> registerEntity(EntityType.Builder.of(EntitySeal::new, MobCategory.CREATURE).sized(1.45F, 0.9F).setTrackingRange(10), "seal"));
    public static final RegistryObject<EntityType<EntitySeagull>> SEAGULL = ENTITY_TYPES.register("seagull", () -> registerEntity(EntityType.Builder.of(EntitySeagull::new, MobCategory.CREATURE).sized(0.45F, 0.45F).setTrackingRange(10), "seagull"));
    public static final RegistryObject<EntityType<EntityRoadrunner>> ROADRUNNER = ENTITY_TYPES.register("roadrunner", () -> registerEntity(EntityType.Builder.of(EntityRoadrunner::new, MobCategory.CREATURE).sized(0.45F, 0.75F).setTrackingRange(10), "roadrunner"));
    public static final RegistryObject<EntityType<EntityRhinoceros>> RHINOCEROS = ENTITY_TYPES.register("rhinoceros", () -> registerEntity(EntityType.Builder.of(EntityRhinoceros::new, MobCategory.CREATURE).sized(2.3F, 2.4F).setTrackingRange(10), "rhinoceros"));
    public static final RegistryObject<EntityType<EntityPotoo>> POTOO = ENTITY_TYPES.register("potoo", () -> registerEntity(EntityType.Builder.of(EntityPotoo::new, MobCategory.CREATURE).sized(0.6F, 0.8F).setTrackingRange(10), "potoo"));




    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        // Register Crab attributes using its prepareAttributes method
        event.put(CRAB.get(), Crab.prepareAttributes().build());
        event.put(CAIMAN.get(), EntityCaiman.bakeAttributes().build());
        event.put(CROCODILE.get(), EntityCrocodile.bakeAttributes().build());
        event.put(LOBSTER.get(), EntityLobster.bakeAttributes().build());
        event.put(MANTIS_SHRIMP.get(), EntityMantisShrimp.bakeAttributes().build());
        event.put(MIMIC_OCTOPUS.get(), EntityMimicOctopus.bakeAttributes().build());
        event.put(ALLIGATOR_SNAPPING_TURTLE.get(), EntityAlligatorSnappingTurtle.bakeAttributes().build());
        event.put(ANACONDA.get(), EntityAnaconda.bakeAttributes().build());
        event.put(ANACONDA_PART.get(), EntityAnacondaPart.bakeAttributes().build());
        event.put(ANTEATER.get(), EntityAnteater.bakeAttributes().build());
        event.put(LEAFCUTTER_ANT.get(), EntityLeafcutterAnt.bakeAttributes().build());
        event.put(BALD_EAGLE.get(), EntityBaldEagle.bakeAttributes().build());
        event.put(SHOEBILL.get(), EntityShoebill.bakeAttributes().build());
        event.put(CACHALOT_WHALE.get(), EntityCachalotWhale.bakeAttributes().build());
        event.put(GIANT_SQUID.get(), EntityGiantSquid.bakeAttributes().build());
        event.put(ORCA.get(), EntityOrca.bakeAttributes().build());
        event.put(SUBTERRANODON.get(), SubterranodonEntity.createAttributes().build());
        event.put(VALLUMRAPTOR.get(), VallumraptorEntity.createAttributes().build());
        event.put(GROTTOCERATOPS.get(), GrottoceratopsEntity.createAttributes().build());
        event.put(TRILOCARIS.get(), TrilocarisEntity.createAttributes().build());
        event.put(TREMORSAURUS.get(), TremorsaurusEntity.createAttributes().build());
        event.put(RELICHEIRUS.get(), RelicheirusEntity.createAttributes().build());
        event.put(LUXTRUCTOSAURUS.get(), LuxtructosaurusEntity.createAttributes().build());
        event.put(ATLATITAN.get(), AtlatitanEntity.createAttributes().build());
        event.put(CAPUCHIN_MONKEY.get(), EntityCapuchinMonkey.bakeAttributes().build());
        event.put(CATFISH.get(), EntityCatfish.bakeAttributes().build());
        event.put(FRILLED_SHARK.get(), EntityFrilledShark.bakeAttributes().build());
        event.put(BLOBFISH.get(), EntityBlobfish.bakeAttributes().build());
        event.put(CENTIPEDE_BODY.get(), EntityCentipedeBody.bakeAttributes().build());
        event.put(CENTIPEDE_HEAD.get(), EntityCentipedeHead.bakeAttributes().build());
        event.put(CENTIPEDE_TAIL.get(), EntityCentipedeTail.bakeAttributes().build());
        event.put(COCKROACH.get(), EntityCockroach.bakeAttributes().build());
        event.put(EMU.get(), EntityEmu.bakeAttributes().build());
        event.put(COMB_JELLY.get(), EntityCombJelly.bakeAttributes().build());
        event.put(CROW.get(), EntityCrow.bakeAttributes().build());
        event.put(COSMIC_COD.get(), EntityCosmicCod.bakeAttributes().build());
        event.put(ELEPHANT.get(), EntityElephant.bakeAttributes().build());
        event.put(ENDERGRADE.get(), EntityEndergrade.bakeAttributes().build());
        event.put(ENDERIOPHAGE.get(), EntityEnderiophage.bakeAttributes().build());
        event.put(GAZELLE.get(), EntityGazelle.bakeAttributes().build());
        event.put(GELADA_MONKEY.get(), EntityGeladaMonkey.bakeAttributes().build());
        event.put(GORILLA.get(), EntityGorilla.bakeAttributes().build());
        event.put(GRIZZLY_BEAR.get(), EntityGrizzlyBear.bakeAttributes().build());
        event.put(HAMMERHEAD_SHARK.get(), EntityHammerheadShark.bakeAttributes().build());
        event.put(KOMODO_DRAGON.get(), EntityKomodoDragon.bakeAttributes().build());
        event.put(UNDERMINER.get(), EntityUnderminer.bakeAttributes().build());
        event.put(TOUCAN.get(), EntityToucan.bakeAttributes().build());
        event.put(TIGER.get(), EntityTiger.bakeAttributes().build());
        event.put(SNOW_LEOPARD.get(), EntitySnowLeopard.bakeAttributes().build());
        event.put(SEAGULL.get(), EntitySeagull.bakeAttributes().build());
        event.put(SEAL.get(), EntitySeal.bakeAttributes().build());
        event.put(ROADRUNNER.get(), EntityRoadrunner.bakeAttributes().build());
        event.put(RHINOCEROS.get(), EntityRhinoceros.bakeAttributes().build());
        event.put(POTOO.get(), EntityPotoo.bakeAttributes().build());



        // Register Spawn Placements
        SpawnPlacements.Type spawnsOnLeaves = SpawnPlacements.Type.create("q_leaves", QEntities::createLeavesSpawnPlacement);
        SpawnPlacements.register(CATFISH.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityCatfish::canCatfishSpawn);
        SpawnPlacements.register(TRILOCARIS.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, TrilocarisEntity::checkTrilocarisSpawnRules);
        SpawnPlacements.register(FRILLED_SHARK.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityFrilledShark::canFrilledSharkSpawn);
        SpawnPlacements.register(BLOBFISH.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityBlobfish::canBlobfishSpawn);
        SpawnPlacements.register(CENTIPEDE_HEAD.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityCentipedeHead::canCentipedeSpawn);
        SpawnPlacements.register(COCKROACH.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityCockroach::canCockroachSpawn);
        SpawnPlacements.register(EMU.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityEmu::canEmuSpawn);
        SpawnPlacements.register(QEntities.CRAB.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Crab::spawnPredicate);
        //SpawnPlacements.register(EntityType.WOLF, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Wolf::checkWolfSpawnRules);
        SpawnPlacements.register(COMB_JELLY.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityCombJelly::canCombJellySpawn);
        SpawnPlacements.register(CROW.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityCrow::canCrowSpawn);
        SpawnPlacements.register(ELEPHANT.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
        SpawnPlacements.register(ENDERGRADE.get(), SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityEndergrade::canEndergradeSpawn);
        SpawnPlacements.register(ENDERIOPHAGE.get(), SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityEnderiophage::canEnderiophageSpawn);
        SpawnPlacements.register(GAZELLE.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
        SpawnPlacements.register(GELADA_MONKEY.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityGeladaMonkey::checkAnimalSpawnRules);
        SpawnPlacements.register(GORILLA.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING, EntityGorilla::canGorillaSpawn);
        SpawnPlacements.register(GRIZZLY_BEAR.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
        SpawnPlacements.register(HAMMERHEAD_SHARK.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityHammerheadShark::canHammerheadSharkSpawn);
        SpawnPlacements.register(KOMODO_DRAGON.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityKomodoDragon::canKomodoDragonSpawn);
        SpawnPlacements.register(UNDERMINER.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityUnderminer::checkUnderminerSpawnRules);
        SpawnPlacements.register(TOUCAN.get(), spawnsOnLeaves, Heightmap.Types.MOTION_BLOCKING, EntityToucan::canToucanSpawn);
        SpawnPlacements.register(TIGER.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityTiger::canTigerSpawn);
        SpawnPlacements.register(SNOW_LEOPARD.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntitySnowLeopard::canSnowLeopardSpawn);
        SpawnPlacements.register(SEAGULL.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntitySeagull::canSeagullSpawn);
        SpawnPlacements.register(SEAL.get(), SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntitySeal::canSealSpawn);
        SpawnPlacements.register(ROADRUNNER.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityRoadrunner::canRoadrunnerSpawn);
        SpawnPlacements.register(RHINOCEROS.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityRhinoceros::checkAnimalSpawnRules);
        SpawnPlacements.register(POTOO.get(), spawnsOnLeaves, Heightmap.Types.MOTION_BLOCKING, EntityPotoo::canPotooSpawn);
        SpawnPlacements.register(LOBSTER.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityLobster::canLobsterSpawn);
        SpawnPlacements.register(MANTIS_SHRIMP.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityMantisShrimp::canMantisShrimpSpawn);
        SpawnPlacements.register(CACHALOT_WHALE.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityCachalotWhale::canCachalotWhaleSpawn);
        SpawnPlacements.register(GIANT_SQUID.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityGiantSquid::canGiantSquidSpawn);
        SpawnPlacements.register(ORCA.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EntityOrca::canOrcaSpawn);

    }

    // helpers
    public static boolean rollSpawn(int rolls, RandomSource random, MobSpawnType reason){
        if(reason == MobSpawnType.SPAWNER){
            return true;
        }else{
            return rolls <= 0 || random.nextInt(rolls) == 0;
        }
    }
    public static Predicate<LivingEntity> buildPredicateFromTag(TagKey<EntityType<?>> entityTag){
        if(entityTag == null){
            return Predicates.alwaysFalse();
        }else{
            return (com.google.common.base.Predicate<LivingEntity>) e -> e.isAlive() && e.getType().is(entityTag);
        }
    }

    private static EntityType registerEntity(EntityType.Builder builder, String entityName) {
        return builder.build(entityName);
    }


    public static boolean createLeavesSpawnPlacement(LevelReader level, BlockPos pos, EntityType<?> type){
        BlockPos blockpos = pos.above();
        BlockPos blockpos1 = pos.below();
        FluidState fluidstate = level.getFluidState(pos);
        BlockState blockstate = level.getBlockState(pos);
        BlockState blockstate1 = level.getBlockState(blockpos1);
        if (!blockstate1.isValidSpawn(level, blockpos1, SpawnPlacements.Type.ON_GROUND, type) && !blockstate1.is(BlockTags.LEAVES)) {
            return false;
        } else {
            return NaturalSpawner.isValidEmptySpawnBlock(level, pos, blockstate, fluidstate, type) && NaturalSpawner.isValidEmptySpawnBlock(level, blockpos, level.getBlockState(blockpos), level.getFluidState(blockpos), type);
        }
    }
}
