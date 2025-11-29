package net.matt.quantize.item;

import net.matt.quantize.Quantize;
import net.matt.quantize.effects.QEffects;
import net.matt.quantize.entities.QEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.matt.quantize.item.item.*;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Rarity;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ArmorMaterials;


public class QItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Quantize.MOD_ID);



    public static final Rarity RARITY_DEMONIC = Rarity.create("quantize:demonic", ChatFormatting.DARK_RED);



    // Custom boring items
    ///public static final RegistryObject<Item> JADE = ITEMS.register("jade", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> RAW_TIN = ITEMS.register("raw_tin", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> RAW_LEAD = ITEMS.register("raw_lead", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> RAW_OSMIUM = ITEMS.register("raw_osmium", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> RAW_URANIUM = ITEMS.register("raw_uranium", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> TIN_INGOT = ITEMS.register("tin_ingot", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> LEAD_INGOT = ITEMS.register("lead_ingot", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> OSMIUM_INGOT = ITEMS.register("osmium_ingot", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> URANIUM_INGOT = ITEMS.register("uranium_ingot", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> BASIC_CONTROL_CIRCUIT = ITEMS.register("basic_control_circuit", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> ADVANCED_CONTROL_CIRCUIT = ITEMS.register("advanced_control_circuit", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> ELITE_CONTROL_CIRCUIT = ITEMS.register("elite_control_circuit", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> ULTIMATE_CONTROL_CIRCUIT = ITEMS.register("ultimate_control_circuit", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> BRONZE_INGOT = ITEMS.register("bronze_ingot", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> STEEL_INGOT = ITEMS.register("steel_ingot", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> HDPE_SHEET = ITEMS.register("hdpe_sheet", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> BRONZE_DUST = ITEMS.register("bronze_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> COAL_DUST = ITEMS.register("coal_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> COPPER_DUST = ITEMS.register("copper_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> DIAMOND_DUST = ITEMS.register("diamond_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> EMERALD_DUST = ITEMS.register("emerald_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> GOLD_DUST = ITEMS.register("gold_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> JADE_DUST = ITEMS.register("jade_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> LAPIS_LAZULI_DUST = ITEMS.register("lapis_lazuli_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> LEAD_DUST = ITEMS.register("lead_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> LITHIUM_DUST = ITEMS.register("lithium_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> NETHERITE_DUST = ITEMS.register("netherite_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> OBSIDIAN_DUST = ITEMS.register("obsidian_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> OSMIUM_DUST = ITEMS.register("osmium_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> QUARTZ_DUST = ITEMS.register("quartz_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> STEEL_DUST = ITEMS.register("steel_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> TIN_DUST = ITEMS.register("tin_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> URANIUM_DUST = ITEMS.register("uranium_dust", () -> new Item(new Item.Properties()));
    ///public static final RegistryObject<Item> IRON_DUST = ITEMS.register("iron_dust", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CROCODILE_SCUTE = ITEMS.register("crocodile_scute", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CRAB_SHELL = ITEMS.register("crab_shell", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SPIKED_SCUTE = ITEMS.register("spiked_scute", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SHED_SNAKE_SKIN = ITEMS.register("shed_snake_skin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> FALCONRY_HOOD = ITEMS.register("falconry_hood", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CACHALOT_WHALE_TOOTH = ITEMS.register("cachalot_whale_tooth", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> LOST_TENTACLE = ITEMS.register("lost_tentacle", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> HEAVY_BONE = ITEMS.register("heavy_bone", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TOUGH_HIDE = ITEMS.register("tough_hide", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CENTIPEDE_LEG = ITEMS.register("centipede_leg", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ANCIENT_DART = ITEMS.register("ancient_dart", () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SERRATED_SHARK_TOOTH = ITEMS.register("serrated_shark_tooth", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> MARACA = ITEMS.register("maraca", () -> new ItemMaraca(new Item.Properties()));
    public static final RegistryObject<Item> COCKROACH_WING_FRAGMENT = ITEMS.register("cockroach_wing_fragment", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> COCKROACH_WING = ITEMS.register("cockroach_wing", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> COCKROACH_OOTHECA = ITEMS.register("cockroach_ootheca", () -> new ItemAnimalEgg(new Item.Properties()));
    public static final RegistryObject<Item> EMU_EGG = ITEMS.register("emu_egg", () -> new ItemAnimalEgg(new Item.Properties().stacksTo(8)));
    public static final RegistryObject<Item> EMU_FEATHER = ITEMS.register("emu_feather", () -> new Item(new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> RAINBOW_JELLY = ITEMS.register("rainbow_jelly", () -> new Item(new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> ACACIA_BLOSSOM = ITEMS.register("acacia_blossom", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GAZELLE_HORN = ITEMS.register("gazelle_horn", () -> new Item(new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> HALO = ITEMS.register("halo", () -> new ItemInventoryOnly(new Item.Properties()));
    public static final RegistryObject<Item> BEAR_FUR = ITEMS.register("bear_fur", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SHARK_TOOTH = ITEMS.register("shark_tooth", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> KOMODO_SPIT = ITEMS.register("komodo_spit", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> KOMODO_SPIT_BOTTLE = ITEMS.register("komodo_spit_bottle", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GHOSTLY_PICKAXE = ITEMS.register("ghostly_pickaxe", () -> new ItemGhostlyPickaxe(new Item.Properties()));


    // food
    public static final RegistryObject<Item> COOKED_CRAB_LEG = ITEMS.register("cooked_crab_leg",
            () -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder()
                            .meat()
                            .nutrition(8)
                            .saturationMod(0.8F)
                            .build())));
    public static final RegistryObject<Item> CRAB_LEG = ITEMS.register("crab_leg",
            () -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder()
                            .meat()
                            .nutrition(1)
                            .saturationMod(0.3F)
                            .build())));
    public static final RegistryObject<Item> LOBSTER_TAIL = ITEMS.register("lobster_tail",
            () -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder()
                            .meat()
                            .nutrition(2)
                            .saturationMod(0.4F)
                            .build())));
    public static final RegistryObject<Item> COOKED_LOBSTER_TAIL = ITEMS.register("cooked_lobster_tail",
            () -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder()
                            .meat()
                            .nutrition(6)
                            .saturationMod(0.65F)
                            .build())));
    public static final RegistryObject<Item> SHRIMP_FRIED_RICE = ITEMS.register("shrimp_fried_rice",
            () -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder()
                            .meat()
                            .nutrition(12)
                            .saturationMod(1F)
                            .build())));
    public static final RegistryObject<Item> GONGYLIDIA = ITEMS.register("gongylidia", () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationMod(1.2F).build())));
    public static final RegistryObject<Item> FISH_OIL = ITEMS.register("fish_oil", () -> new ItemFishOil(new Item.Properties().craftRemainder(Items.GLASS_BOTTLE).food(new FoodProperties.Builder().nutrition(0).saturationMod(0.2F).build())));
    public static final RegistryObject<Item> TRILOCARIS_TAIL = ITEMS.register("trilocaris_tail",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationMod(0.3F).meat().build())));
    public static final RegistryObject<Item> COOKED_TRILOCARIS_TAIL = ITEMS.register("cooked_trilocaris_tail",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(5).saturationMod(0.5F).meat().build())));
    public static final RegistryObject<Item> PINE_NUTS = ITEMS.register("pine_nuts",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(2)
                    .saturationMod(0.175F)
                    .build())));
    public static final RegistryObject<Item> DINOSAUR_NUGGET = ITEMS.register("dinosaur_nugget",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(3)
                    .saturationMod(0.3F)
                    .meat()
                    .fast()
                    .build())));
    public static final RegistryObject<Item> SERENE_SALAD = ITEMS.register("serene_salad",
            () -> new PrehistoricMixtureItem(new Item.Properties()
                    .stacksTo(1)
                    .food(new FoodProperties.Builder()
                            .nutrition(5)
                            .saturationMod(0.35F)
                            .build())));
    public static final RegistryObject<Item> SEETHING_STEW = ITEMS.register("seething_stew",
            () -> new PrehistoricMixtureItem(new Item.Properties()
                    .stacksTo(1)
                    .food(new FoodProperties.Builder()
                            .nutrition(6)
                            .saturationMod(0.6F)
                            .effect(() -> new MobEffectInstance(QEffects.RAGE.get(), 2200), 1.0F)
                            .build())));
    public static final RegistryObject<Item> PRIMORDIAL_SOUP = ITEMS.register("primordial_soup",
            () -> new PrehistoricMixtureItem(new Item.Properties()
                    .stacksTo(1)
                    .food(new FoodProperties.Builder()
                            .nutrition(6)
                            .saturationMod(0.6F)
                            .effect(() -> new MobEffectInstance(MobEffects.DIG_SPEED, 800), 1.0F)
                            .build())));
    public static final RegistryObject<Item> RAW_CATFISH = ITEMS.register("raw_catfish", () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationMod(0.3F).meat().build())));
    public static final RegistryObject<Item> COOKED_CATFISH = ITEMS.register("cooked_catfish", () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(5).saturationMod(0.5F).meat().build())));
    public static final RegistryObject<Item> BLOBFISH = ITEMS.register("blobfish", () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationMod(0.4F).meat().effect(new MobEffectInstance(MobEffects.POISON, 120, 0), 1F).build())));
    public static final RegistryObject<Item> BOILED_EMU_EGG = ITEMS.register("boiled_emu_egg", () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationMod(1F).meat().build())));
    public static final RegistryObject<Item> COSMIC_COD = ITEMS.register("cosmic_cod", () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationMod(0.3F).effect(new MobEffectInstance(QEffects.ENDER_FLU.get(), 12000), 0.15F).build())));


    // spawn eggs
    public static final RegistryObject<Item> CAIMAN_SPAWN_EGG = ITEMS.register("caiman_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.CAIMAN, 0X5C5631, 0XBBC45C, new Item.Properties()));
    public static final RegistryObject<Item> CROCODILE_SPAWN_EGG = ITEMS.register("crocodile_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.CROCODILE, 0X738940,0XA6A15E, new Item.Properties()));
    public static final RegistryObject<Item> CRAB_SPAWN_EGG = ITEMS.register("crab_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.CRAB, 0x893c22, 0x916548, new Item.Properties()));
    public static final RegistryObject<Item> LOBSTER_SPAWN_EGG = ITEMS.register("lobster_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.LOBSTER, 0XC43123,0XDD5F38, new Item.Properties()));
    public static final RegistryObject<Item> MANTIS_SHRIMP_SPAWN_EGG = ITEMS.register("mantis_shrimp_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.MANTIS_SHRIMP, 0XDB4858,0X15991E, new Item.Properties()));
    public static final RegistryObject<Item> MIMIC_OCTOPUS_SPAWN_EGG = ITEMS.register("mimic_octopus_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.MIMIC_OCTOPUS, 0XFFEBDC,0X1D1C1F, new Item.Properties()));
    public static final RegistryObject<Item> ALLIGATOR_SNAPPING_TURTLE_SPAWN_EGG = ITEMS.register("alligator_snapping_turtle_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.ALLIGATOR_SNAPPING_TURTLE, 0X6C5C52,0X456926, new Item.Properties()));
    public static final RegistryObject<Item> ANACONDA_SPAWN_EGG = ITEMS.register("anaconda_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.ANACONDA, 0X565C22,0XD3763F, new Item.Properties()));
    public static final RegistryObject<Item> LEAFCUTTER_ANT_SPAWN_EGG = ITEMS.register("leafcutter_ant_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.LEAFCUTTER_ANT, 0X964023,0XA65930, new Item.Properties()));
    public static final RegistryObject<Item> ANTEATER_SPAWN_EGG = ITEMS.register("anteater_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.ANTEATER, 0X4C3F3A, 0XCCBCB4, new Item.Properties()));
    public static final RegistryObject<Item> BALD_EAGLE_SPAWN_EGG = ITEMS.register("bald_eagle_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.BALD_EAGLE, 0X321F18,0XF4F4F4, new Item.Properties()));
    public static final RegistryObject<Item> SHOEBILL_SPAWN_EGG = ITEMS.register("shoebill_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.SHOEBILL, 0X828282,0XD5B48A, new Item.Properties()));
    public static final RegistryObject<Item> CACHALOT_WHALE_SPAWN_EGG = ITEMS.register("cachalot_whale_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.CACHALOT_WHALE, 0X949899,0X5F666E, new Item.Properties()));
    public static final RegistryObject<Item> GIANT_SQUID_SPAWN_EGG = ITEMS.register("giant_squid_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.GIANT_SQUID, 0XAB4B4D, 0XD67D6B, new Item.Properties()));
    public static final RegistryObject<Item> ORCA_SPAWN_EGG = ITEMS.register("orca_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.ORCA, 0X2C2C2C,0XD6D8E4, new Item.Properties()));
    public static final RegistryObject<Item> SUBTERANODON_SPAWN_EGG = ITEMS.register("subterranodon_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.SUBTERRANODON, 0X00B1B2, 0XFFF11C, new Item.Properties()));
    public static final RegistryObject<Item> VALLUMRAPTOR_SPAWN_EGG = ITEMS.register("vallumraptor_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.VALLUMRAPTOR, 0X22389A, 0XEEE5AB, new Item.Properties()));
    public static final RegistryObject<Item> GROTTOCERATOPS_SPAWN_EGG = ITEMS.register("grottoceratops_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.GROTTOCERATOPS, 0XAC3B03, 0XD39B4E, new Item.Properties()));
    public static final RegistryObject<Item> TRILOCARIS_SPAWN_EGG = ITEMS.register("trilocaris_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.TRILOCARIS, 0X713E0D, 0X8B2010, new Item.Properties()));
    public static final RegistryObject<Item> TREMORSAURUS_SPAWN_EGG = ITEMS.register("tremorsaurus_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.TREMORSAURUS, 0X53780E, 0XDFA211, new Item.Properties()));
    public static final RegistryObject<Item> RELICHEIRUS_SPAWN_EGG = ITEMS.register("relicheirus_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.RELICHEIRUS, 0X6AE4F9, 0X5B2152, new Item.Properties()));
    public static final RegistryObject<Item> LUXTRUCTOSAURUS_SPAWN_EGG = ITEMS.register("luxtructosaurus_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.LUXTRUCTOSAURUS, 0X1F0E15, 0XB30C03, new Item.Properties()));
    public static final RegistryObject<Item> ATLATITAN_SPAWN_EGG = ITEMS.register("atlatitan_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.ATLATITAN, 0XB67000, 0XBFBAA4, new Item.Properties()));
    public static final RegistryObject<Item> CAPUCHIN_MONKEY_SPAWN_EGG = ITEMS.register("capuchin_monkey_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.CAPUCHIN_MONKEY, 0X25211F,0XF1DAB3, new Item.Properties()));
    public static final RegistryObject<Item> CATFISH_SPAWN_EGG = ITEMS.register("catfish_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.CATFISH, 0X807757, 0X8A7466, new Item.Properties()));
    public static final RegistryObject<Item> FRILLED_SHARK_SPAWN_EGG = ITEMS.register("frilled_shark_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.FRILLED_SHARK, 0X726B6B,0X873D3D, new Item.Properties()));
    public static final RegistryObject<Item> BLOBFISH_SPAWN_EGG = ITEMS.register("blobfish_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.BLOBFISH, 0XDBC6BD,0X9E7A7F, new Item.Properties()));
    public static final RegistryObject<Item> CAVE_CENTIPEDE_SPAWN_EGG = ITEMS.register("cave_centipede_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.CENTIPEDE_HEAD, 0X342B2E,0X733449, new Item.Properties()));
    public static final RegistryObject<Item> COCKROACH_SPAWN_EGG = ITEMS.register("cockroach_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.COCKROACH, 0X0D0909,0X42241E, new Item.Properties()));
    public static final RegistryObject<Item> EMU_SPAWN_EGG = ITEMS.register("emu_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.EMU, 0X665346,0X3B3938, new Item.Properties()));
    public static final RegistryObject<Item> COMB_JELLY_SPAWN_EGG = ITEMS.register("comb_jelly_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.COMB_JELLY, 0XCFE9FE, 0X6EFF8B, new Item.Properties()));
    public static final RegistryObject<Item> CROW_SPAWN_EGG = ITEMS.register("crow_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.CROW, 0X0D111C,0X1C2030, new Item.Properties()));
    public static final RegistryObject<Item> COSMIC_COD_SPAWN_EGG = ITEMS.register("cosmic_cod_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.COSMIC_COD, 0X6985C7, 0XE2D1FF, new Item.Properties()));
    public static final RegistryObject<Item> ELEPHANT_SPAWN_EGG = ITEMS.register("elephant_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.ELEPHANT, 0X8D8987,0XEDE5D1, new Item.Properties()));
    public static final RegistryObject<Item> ENDERGRADE_SPAWN_EGG = ITEMS.register("endergrade_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.ENDERGRADE, 0X7862B3,0x81BDEB, new Item.Properties()));
    public static final RegistryObject<Item> ENDERIOPHAGE_SPAWN_EGG = ITEMS.register("enderiophage_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.ENDERIOPHAGE, 0X872D83,0XF6E2CD, new Item.Properties()));
    public static final RegistryObject<Item> GAZELLE_SPAWN_EGG = ITEMS.register("gazelle_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.GAZELLE, 0XDDA675,0X2C2925, new Item.Properties()));
    public static final RegistryObject<Item> GELADA_MONKEY_SPAWN_EGG = ITEMS.register("gelada_monkey_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.GELADA_MONKEY, 0XB08C64, 0XFF4F53, new Item.Properties()));
    public static final RegistryObject<Item> GORILLA_SPAWN_EGG = ITEMS.register("gorilla_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.GORILLA, 0X595B5D,0X1C1C21, new Item.Properties()));
    public static final RegistryObject<Item> GRIZZLY_BEAR_SPAWN_EGG = ITEMS.register("grizzly_bear_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.GRIZZLY_BEAR, 0X693A2C, 0X976144, new Item.Properties()));
    public static final RegistryObject<Item> HAMMERHEAD_SHARK_SPAWN_EGG = ITEMS.register("hammerhead_shark_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.HAMMERHEAD_SHARK, 0X8A92B5,0XB9BED8, new Item.Properties()));
    public static final RegistryObject<Item> KOMODO_DRAGON_SPAWN_EGG = ITEMS.register("komodo_dragon_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.KOMODO_DRAGON, 0X746C4F,0X564231, new Item.Properties()));
    public static final RegistryObject<Item> UNDERMINER_SPAWN_EGG = ITEMS.register("underminer_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.UNDERMINER, 0XD6E2FF, 0X6C84C4, new Item.Properties()));
    public static final RegistryObject<Item> TOUCAN_SPAWN_EGG = ITEMS.register("toucan_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.TOUCAN, 0XF58F33,0X1E2133, new Item.Properties()));
    public static final RegistryObject<Item> TIGER_SPAWN_EGG = ITEMS.register("tiger_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.TIGER, 0XC7612E,0X2A3233, new Item.Properties()));
    public static final RegistryObject<Item> SNOW_LEOPARD_SPAWN_EGG = ITEMS.register("snow_leopard_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.SNOW_LEOPARD, 0XACA293,0X26201D, new Item.Properties()));
    public static final RegistryObject<Item> SEAL_SPAWN_EGG = ITEMS.register("seal_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.SEAL, 0X483C32,0X66594C, new Item.Properties()));
    public static final RegistryObject<Item> SEAGULL_SPAWN_EGG = ITEMS.register("seagull_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.SEAGULL, 0XC9D2DC,0XFFD850, new Item.Properties()));
    public static final RegistryObject<Item> ROADRUNNER_SPAWN_EGG = ITEMS.register("roadrunner_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.ROADRUNNER, 0X3A2E26, 0XFBE9CE, new Item.Properties()));
    public static final RegistryObject<Item> RHINOCEROS_SPAWN_EGG = ITEMS.register("rhinoceros_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.RHINOCEROS, 0XA19594, 0X827474, new Item.Properties()));
    public static final RegistryObject<Item> POTOO_SPAWN_EGG = ITEMS.register("potoo_spawn_egg",
            () -> new ForgeSpawnEggItem(QEntities.POTOO, 0X8C7753, 0XFFC042, new Item.Properties()));





    // Gun Stuff
    //public static RegistryObject<ModernKineticGunItem> MODERN_KINETIC_GUN = ITEMS.register("modern_kinetic_gun", ModernKineticGunItem::new);





    // bucket items
    public static final RegistryObject<Item> CRAB_BUCKET = ITEMS.register("crab_bucket",
            () -> new MobBucketItem(() -> QEntities.CRAB.get(), () -> Fluids.WATER, () -> SoundEvents.BUCKET_EMPTY_FISH, //add .get() for QSounds.CRAB_BUCKET_EMPTY.get() for example
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> LOBSTER_BUCKET = ITEMS.register("lobster_bucket",
            () -> new MobBucketItem(() -> QEntities.LOBSTER.get(), () -> Fluids.WATER, () -> SoundEvents.BUCKET_EMPTY_FISH,
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MIMIC_OCTOPUS_BUCKET = ITEMS.register("mimic_octopus_bucket",
            () -> new MobBucketItem(() -> QEntities.MIMIC_OCTOPUS.get(), () -> Fluids.WATER, () -> SoundEvents.BUCKET_EMPTY_FISH,
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TRILOCARIS_BUCKET = ITEMS.register("trilocaris_bucket",
            () -> new MobBucketItem(() -> QEntities.TRILOCARIS.get(), () -> Fluids.WATER, () -> SoundEvents.BUCKET_EMPTY_FISH,
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SMALL_CATFISH_BUCKET = ITEMS.register("small_catfish_bucket",
            () -> new MobBucketItem(() -> QEntities.CATFISH.get(), () -> Fluids.WATER, () -> SoundEvents.BUCKET_EMPTY_FISH,
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MEDIUM_CATFISH_BUCKET = ITEMS.register("medium_catfish_bucket",
            () -> new MobBucketItem(() -> QEntities.CATFISH.get(), () -> Fluids.WATER, () -> SoundEvents.BUCKET_EMPTY_FISH,
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> LARGE_CATFISH_BUCKET = ITEMS.register("large_catfish_bucket",
            () -> new MobBucketItem(() -> QEntities.CATFISH.get(), () -> Fluids.WATER, () -> SoundEvents.BUCKET_EMPTY_FISH,
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FRILLED_SHARK_BUCKET = ITEMS.register("frilled_shark_bucket",
            () -> new MobBucketItem(() -> QEntities.FRILLED_SHARK.get(), () -> Fluids.WATER, () -> SoundEvents.BUCKET_EMPTY_FISH,
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BLOBFISH_BUCKET = ITEMS.register("blobfish_bucket",
            () -> new MobBucketItem(() -> QEntities.BLOBFISH.get(), () -> Fluids.WATER, () -> SoundEvents.BUCKET_EMPTY_FISH,
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COMB_JELLY_BUCKET = ITEMS.register("comb_jelly_bucket",
            () -> new MobBucketItem(() -> QEntities.COMB_JELLY.get(), () -> Fluids.WATER, () -> SoundEvents.BUCKET_EMPTY_FISH,
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COSMIC_COD_BUCKET = ITEMS.register("cosmic_cod_bucket", () -> new ItemCosmicCodBucket(new Item.Properties()));



    // custom items
    public static final RegistryObject<Item> JETPACK = ITEMS.register("jetpack",
            () -> new JetpackItem(ArmorMaterials.LEATHER, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> APPLE_WATCH = ITEMS.register("apple_watch",
            () -> new AppleWatchItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PERSONAL_BATTERY = ITEMS.register("personal_battery",
            () -> new PersonalBatteryItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PERSONAL_SUPER_BATTERY = ITEMS.register("personal_super_battery",
            () -> new PersonalSuperBatteryItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TROWEL = ITEMS.register("trowel",
            () -> new TrowelItem(new Item.Properties()));
    public static final RegistryObject<Item> FALCONRY_GLOVE = ITEMS.register("falconry_glove", () -> new ItemFalconryGlove(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TECTONIC_SHARD = ITEMS.register("tectonic_shard", () -> new Item(new Item.Properties().rarity(RARITY_DEMONIC).fireResistant()));
    public static final RegistryObject<Item> AMBER_CURIOSITY = ITEMS.register("amber_curiosity", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> OMINOUS_CATALYST = ITEMS.register("ominous_catalyst", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant()));
    public static final RegistryObject<Item> ENERGY_ROCKET = ITEMS.register("energy_rocket",
            () -> new EnergyRocketItem(new Item.Properties().stacksTo(1)));



    // Fuel Items
    public static final RegistryObject<Item> AMBERGRIS = ITEMS.register("ambergris", () -> new ItemFuel(new Item.Properties(), 12800));


    // Signs



    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
