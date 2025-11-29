package net.matt.quantize.item;

import net.matt.quantize.Quantize;
import net.matt.quantize.block.QBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class QCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Quantize.MOD_ID);

    public static final RegistryObject<CreativeModeTab> TUTUORIAL_TAB = CREATIVE_MODE_TABS.register("quantize_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(QBlocks.FIREFLYBUSH.get()))
                    .title(Component.translatable("creativetab.quantize_tab"))
                    .displayItems((pParameters, pOutput) -> {

                        //Add Items Here
                        //Spawn Eggs
                        pOutput.accept(QItems.CRAB_SPAWN_EGG.get());
                        pOutput.accept(QItems.CAIMAN_SPAWN_EGG.get());
                        pOutput.accept(QItems.CROCODILE_SPAWN_EGG.get());
                        pOutput.accept(QItems.LOBSTER_SPAWN_EGG.get());
                        pOutput.accept(QItems.MANTIS_SHRIMP_SPAWN_EGG.get());
                        pOutput.accept(QItems.MIMIC_OCTOPUS_SPAWN_EGG.get());
                        pOutput.accept(QItems.ALLIGATOR_SNAPPING_TURTLE_SPAWN_EGG.get());
                        pOutput.accept(QItems.ANACONDA_SPAWN_EGG.get());
                        pOutput.accept(QItems.ANTEATER_SPAWN_EGG.get());
                        pOutput.accept(QItems.LEAFCUTTER_ANT_SPAWN_EGG.get());
                        pOutput.accept(QItems.BALD_EAGLE_SPAWN_EGG.get());
                        pOutput.accept(QItems.SHOEBILL_SPAWN_EGG.get());
                        pOutput.accept(QItems.CACHALOT_WHALE_SPAWN_EGG.get());
                        pOutput.accept(QItems.GIANT_SQUID_SPAWN_EGG.get());
                        pOutput.accept(QItems.ORCA_SPAWN_EGG.get());
                        pOutput.accept(QItems.SUBTERANODON_SPAWN_EGG.get());
                        pOutput.accept(QItems.VALLUMRAPTOR_SPAWN_EGG.get());
                        pOutput.accept(QItems.GROTTOCERATOPS_SPAWN_EGG.get());
                        pOutput.accept(QItems.TRILOCARIS_SPAWN_EGG.get());
                        pOutput.accept(QItems.TREMORSAURUS_SPAWN_EGG.get());
                        pOutput.accept(QItems.RELICHEIRUS_SPAWN_EGG.get());
                        pOutput.accept(QItems.LUXTRUCTOSAURUS_SPAWN_EGG.get());
                        pOutput.accept(QItems.ATLATITAN_SPAWN_EGG.get());
                        pOutput.accept(QItems.CAPUCHIN_MONKEY_SPAWN_EGG.get());
                        pOutput.accept(QItems.CATFISH_SPAWN_EGG.get());
                        pOutput.accept(QItems.FRILLED_SHARK_SPAWN_EGG.get());
                        pOutput.accept(QItems.BLOBFISH_SPAWN_EGG.get());
                        pOutput.accept(QItems.CAVE_CENTIPEDE_SPAWN_EGG.get());
                        pOutput.accept(QItems.COCKROACH_SPAWN_EGG.get());
                        pOutput.accept(QItems.EMU_SPAWN_EGG.get());
                        pOutput.accept(QItems.COMB_JELLY_SPAWN_EGG.get());
                        pOutput.accept(QItems.CROW_SPAWN_EGG.get());
                        pOutput.accept(QItems.COSMIC_COD_SPAWN_EGG.get());
                        pOutput.accept(QItems.ELEPHANT_SPAWN_EGG.get());
                        pOutput.accept(QItems.ENDERGRADE_SPAWN_EGG.get());
                        pOutput.accept(QItems.ENDERIOPHAGE_SPAWN_EGG.get());
                        pOutput.accept(QItems.GAZELLE_SPAWN_EGG.get());
                        pOutput.accept(QItems.GELADA_MONKEY_SPAWN_EGG.get());
                        pOutput.accept(QItems.GORILLA_SPAWN_EGG.get());
                        pOutput.accept(QItems.GRIZZLY_BEAR_SPAWN_EGG.get());
                        pOutput.accept(QItems.HAMMERHEAD_SHARK_SPAWN_EGG.get());
                        pOutput.accept(QItems.KOMODO_DRAGON_SPAWN_EGG.get());
                        pOutput.accept(QItems.UNDERMINER_SPAWN_EGG.get());
                        pOutput.accept(QItems.TOUCAN_SPAWN_EGG.get());
                        pOutput.accept(QItems.TIGER_SPAWN_EGG.get());
                        pOutput.accept(QItems.SNOW_LEOPARD_SPAWN_EGG.get());
                        pOutput.accept(QItems.SEAGULL_SPAWN_EGG.get());
                        pOutput.accept(QItems.SEAL_SPAWN_EGG.get());
                        pOutput.accept(QItems.ROADRUNNER_SPAWN_EGG.get());
                        pOutput.accept(QItems.RHINOCEROS_SPAWN_EGG.get());
                        pOutput.accept(QItems.POTOO_SPAWN_EGG.get());

                        //Mob Buckets
                        pOutput.accept(QItems.CRAB_BUCKET.get());
                        pOutput.accept(QItems.LOBSTER_BUCKET.get());
                        pOutput.accept(QItems.MIMIC_OCTOPUS_BUCKET.get());
                        pOutput.accept(QItems.TRILOCARIS_BUCKET.get());
                        pOutput.accept(QItems.SMALL_CATFISH_BUCKET.get());
                        pOutput.accept(QItems.MEDIUM_CATFISH_BUCKET.get());
                        pOutput.accept(QItems.LARGE_CATFISH_BUCKET.get());
                        pOutput.accept(QItems.FRILLED_SHARK_BUCKET.get());
                        pOutput.accept(QItems.BLOBFISH_BUCKET.get());
                        pOutput.accept(QItems.COMB_JELLY_BUCKET.get());
                        pOutput.accept(QItems.COSMIC_COD_BUCKET.get());

                        //Mob Eggs
                        pOutput.accept(QBlocks.CAIMAN_EGG.get());
                        pOutput.accept(QBlocks.CROCODILE_EGG.get());

                        //Mob Crafting Drops
                        pOutput.accept(QItems.CROCODILE_SCUTE.get());
                        pOutput.accept(QItems.CRAB_SHELL.get());
                        pOutput.accept(QItems.SPIKED_SCUTE.get());
                        pOutput.accept(QItems.SHED_SNAKE_SKIN.get());
                        pOutput.accept(QItems.GONGYLIDIA.get());
                        pOutput.accept(QItems.FISH_OIL.get());
                        pOutput.accept(QItems.CACHALOT_WHALE_TOOTH.get());
                        pOutput.accept(QItems.AMBERGRIS.get());
                        pOutput.accept(QItems.LOST_TENTACLE.get());
                        pOutput.accept(QItems.HEAVY_BONE.get());
                        pOutput.accept(QItems.TOUGH_HIDE.get());
                        pOutput.accept(QItems.SERRATED_SHARK_TOOTH.get());
                        pOutput.accept(QItems.CENTIPEDE_LEG.get());
                        pOutput.accept(QItems.COCKROACH_OOTHECA.get());
                        pOutput.accept(QItems.COCKROACH_WING.get());
                        pOutput.accept(QItems.COCKROACH_WING_FRAGMENT.get());
                        pOutput.accept(QItems.EMU_FEATHER.get());
                        pOutput.accept(QItems.RAINBOW_JELLY.get());
                        pOutput.accept(QItems.ACACIA_BLOSSOM.get());
                        pOutput.accept(QItems.GAZELLE_HORN.get());
                        pOutput.accept(QItems.BEAR_FUR.get());
                        pOutput.accept(QItems.SHARK_TOOTH.get());
                        pOutput.accept(QItems.KOMODO_SPIT.get());
                        pOutput.accept(QItems.KOMODO_SPIT_BOTTLE.get());

                        //Raw foods
                        pOutput.accept(QItems.CRAB_LEG.get());
                        pOutput.accept(QItems.LOBSTER_TAIL.get());
                        pOutput.accept(QBlocks.DINOSAUR_CHOP.get());
                        pOutput.accept(QItems.RAW_CATFISH.get());
                        pOutput.accept(QItems.BLOBFISH.get());
                        pOutput.accept(QItems.EMU_EGG.get());
                        pOutput.accept(QItems.COSMIC_COD.get());

                        //cooked foods
                        pOutput.accept(QItems.COOKED_CRAB_LEG.get());
                        pOutput.accept(QItems.COOKED_LOBSTER_TAIL.get());
                        pOutput.accept(QItems.SHRIMP_FRIED_RICE.get());
                        pOutput.accept(QItems.SEETHING_STEW.get());
                        pOutput.accept(QItems.SERENE_SALAD.get());
                        pOutput.accept(QItems.PRIMORDIAL_SOUP.get());
                        pOutput.accept(QItems.DINOSAUR_NUGGET.get());
                        pOutput.accept(QBlocks.COOKED_DINOSAUR_CHOP.get());
                        pOutput.accept(QItems.COOKED_CATFISH.get());
                        pOutput.accept(QItems.BOILED_EMU_EGG.get());

                        //Ores
                        ///pOutput.accept(QBlocks.JADE_ORE.get());
                        ///pOutput.accept(QBlocks.DEEPSLATE_JADE_ORE.get());
                        ///pOutput.accept(QBlocks.TIN_ORE.get());
                        ///pOutput.accept(QBlocks.DEEPSLATE_TIN_ORE.get());
                        ///pOutput.accept(QBlocks.LEAD_ORE.get());
                        ///pOutput.accept(QBlocks.DEEPSLATE_LEAD_ORE.get());
                        ///pOutput.accept(QBlocks.URANIUM_ORE.get());
                        ///pOutput.accept(QBlocks.DEEPSLATE_URANIUM_ORE.get());
                        ///pOutput.accept(QBlocks.OSMIUM_ORE.get());
                        ///pOutput.accept(QBlocks.DEEPSLATE_OSMIUM_ORE.get());

                        //Raw Ores
                        ///pOutput.accept(QItems.RAW_TIN.get());
                        ///pOutput.accept(QItems.RAW_URANIUM.get());
                        ///pOutput.accept(QItems.RAW_LEAD.get());
                        ///pOutput.accept(QItems.RAW_OSMIUM.get());

                        //Ingots
                        ///pOutput.accept(QItems.JADE.get());
                        ///pOutput.accept(QItems.TIN_INGOT.get());
                        ///pOutput.accept(QItems.URANIUM_INGOT.get());
                        ///pOutput.accept(QItems.LEAD_INGOT.get());
                        ///pOutput.accept(QItems.OSMIUM_INGOT.get());
                        ///pOutput.accept(QItems.BRONZE_INGOT.get());
                        ///pOutput.accept(QItems.STEEL_INGOT.get());

                        //Dusts
                        ///pOutput.accept(QItems.BRONZE_DUST.get());
                        ///pOutput.accept(QItems.COAL_DUST.get());
                        ///pOutput.accept(QItems.COPPER_DUST.get());
                        ///pOutput.accept(QItems.DIAMOND_DUST.get());
                        ///pOutput.accept(QItems.EMERALD_DUST.get());
                        ///pOutput.accept(QItems.GOLD_DUST.get());
                        ///pOutput.accept(QItems.JADE_DUST.get());
                        ///pOutput.accept(QItems.LAPIS_LAZULI_DUST.get());
                        ///pOutput.accept(QItems.LEAD_DUST.get());
                        ///pOutput.accept(QItems.LITHIUM_DUST.get());
                        ///pOutput.accept(QItems.NETHERITE_DUST.get());
                        ///pOutput.accept(QItems.OBSIDIAN_DUST.get());
                        ///pOutput.accept(QItems.OSMIUM_DUST.get());
                        ///pOutput.accept(QItems.QUARTZ_DUST.get());
                        ///pOutput.accept(QItems.STEEL_DUST.get());
                        ///pOutput.accept(QItems.TIN_DUST.get());
                        ///pOutput.accept(QItems.URANIUM_DUST.get());
                        ///pOutput.accept(QItems.IRON_DUST.get());

                        // Crafting Items
                        ///pOutput.accept(QItems.BASIC_CONTROL_CIRCUIT.get());
                        ///pOutput.accept(QItems.ADVANCED_CONTROL_CIRCUIT.get());
                        ///pOutput.accept(QItems.ELITE_CONTROL_CIRCUIT.get());
                        ///pOutput.accept(QItems.ULTIMATE_CONTROL_CIRCUIT.get());
                        ///pOutput.accept(QItems.HDPE_SHEET.get());

                        //Blocks
                        ///pOutput.accept(QBlocks.JADE_BRICKS.get());
                        pOutput.accept(QBlocks.FERN_THATCH.get());
                        pOutput.accept(QBlocks.LIMESTONE.get());
                        pOutput.accept(QBlocks.LIMESTONE_PILLAR.get());
                        pOutput.accept(QBlocks.LIMESTONE_CHISELED.get());
                        pOutput.accept(QBlocks.SMOOTH_LIMESTONE.get());
                        pOutput.accept(QBlocks.AMBER.get());
                        pOutput.accept(QBlocks.AMBER_MONOLITH.get());
                        pOutput.accept(QBlocks.AMBERSOL.get());
                        pOutput.accept(QBlocks.FLOOD_BASALT.get());
                        pOutput.accept(QBlocks.PRIMAL_MAGMA.get());
                        pOutput.accept(QBlocks.FIREFLY_JAR.get());
                        pOutput.accept(QBlocks.COPPER_GRATE.get());
                        pOutput.accept(QBlocks.EXPOSED_COPPER_GRATE.get());
                        pOutput.accept(QBlocks.WEATHERED_COPPER_GRATE.get());
                        pOutput.accept(QBlocks.OXIDIZED_COPPER_GRATE.get());
                        pOutput.accept(QBlocks.WAXED_COPPER_GRATE.get());
                        pOutput.accept(QBlocks.WAXED_EXPOSED_COPPER_GRATE.get());
                        pOutput.accept(QBlocks.WAXED_WEATHERED_COPPER_GRATE.get());
                        pOutput.accept(QBlocks.WAXED_OXIDIZED_COPPER_GRATE.get());
                        pOutput.accept(QBlocks.CHISELED_TUFF.get());
                        pOutput.accept(QBlocks.CHISELED_TUFF_BRICKS.get());
                        pOutput.accept(QBlocks.POLISHED_TUFF.get());
                        pOutput.accept(QBlocks.POLISHED_TUFF_SLAB.get());
                        pOutput.accept(QBlocks.POLISHED_TUFF_STAIRS.get());
                        pOutput.accept(QBlocks.POLISHED_TUFF_WALL.get());
                        pOutput.accept(QBlocks.TUFF_BRICK_SLAB.get());
                        pOutput.accept(QBlocks.TUFF_BRICK_STAIRS.get());
                        pOutput.accept(QBlocks.TUFF_BRICK_WALL.get());
                        pOutput.accept(QBlocks.TUFF_BRICKS.get());
                        pOutput.accept(QBlocks.TUFF_SLAB.get());
                        pOutput.accept(QBlocks.TUFF_STAIRS.get());
                        pOutput.accept(QBlocks.TUFF_WALL.get());

                        //Redstone Blocks
                        pOutput.accept(QBlocks.COG_BLOCK.get());
                        pOutput.accept(QBlocks.REDSTONE_RANDOMIZER.get());
                        pOutput.accept(QBlocks.PEWEN_BUTTON.get());

                        //Pressure Plates
                        pOutput.accept(QBlocks.PEWEN_PRESSURE_PLATE.get());

                        //Generators
                        pOutput.accept(QBlocks.SOLAR_PANEL.get());

                        //Machines
                        pOutput.accept(QBlocks.ENERGY_CONDUIT.get());
                        pOutput.accept(QBlocks.ELECTRIC_FURNACE.get());
                        pOutput.accept(QBlocks.BATTERY.get());
                        pOutput.accept(QBlocks.PULVERIZER.get());
                        pOutput.accept(QBlocks.WIRELESS_CAPACITOR.get());
                        pOutput.accept(QBlocks.BOTANY_POT.get());
                        pOutput.accept(QBlocks.HYDROPONICS_BASIN.get());
                        pOutput.accept(QBlocks.CRAFTER.get());

                        // Misc Utility Blocks
                        pOutput.accept(QBlocks.ELEVATOR.get());

                        //Tools
                        pOutput.accept(QItems.JETPACK.get());
                        pOutput.accept(QItems.TROWEL.get());
                        pOutput.accept(QItems.FALCONRY_GLOVE.get());
                        pOutput.accept(QItems.GHOSTLY_PICKAXE.get());

                        //Armor

                        // Equipment
                        pOutput.accept(QItems.APPLE_WATCH.get());
                        pOutput.accept(QItems.PERSONAL_BATTERY.get());
                        pOutput.accept(QItems.PERSONAL_SUPER_BATTERY.get());
                        pOutput.accept(QItems.ENERGY_ROCKET.get());

                        //Misc Items
                        pOutput.accept(QItems.FALCONRY_HOOD.get());
                        pOutput.accept(QItems.TECTONIC_SHARD.get());
                        pOutput.accept(QItems.AMBER_CURIOSITY.get());
                        pOutput.accept(QItems.OMINOUS_CATALYST.get());
                        pOutput.accept(QItems.ANCIENT_DART.get());

                        // Misc Vegetation
                        pOutput.accept(QBlocks.FIREFLYBUSH.get());
                        pOutput.accept(QBlocks.FIDDLEHEAD.get());
                        pOutput.accept(QBlocks.CURLY_FERN.get());
                        pOutput.accept(QBlocks.FLYTRAP.get());
                        pOutput.accept(QBlocks.CYCAD.get());
                        pOutput.accept(QBlocks.TREE_STAR.get());
                        pOutput.accept(QBlocks.ARCHAIC_VINE.get());
                        pOutput.accept(QBlocks.SPANISH_MOSS.get());
                        pOutput.accept(QBlocks.FLOWERING_LILY_PAD.get());
                        pOutput.accept(QBlocks.ELEPHANT_EAR.get());
                        pOutput.accept(QBlocks.SANDY_GRASS.get());

                        // Misc Feature Blocks
                        pOutput.accept(QBlocks.LEAFCUTTER_ANTHILL.get());
                        pOutput.accept(QBlocks.LEAFCUTTER_ANT_CHAMBER.get());

                        //Saplings
                        pOutput.accept(QBlocks.SILVER_BIRCH_SAPLING.get());
                        pOutput.accept(QBlocks.PEWEN_SAPLING.get());
                        pOutput.accept(QBlocks.ANCIENT_SAPLING.get());
                        pOutput.accept(QBlocks.CYPRESS_SAPLING.get());
                        pOutput.accept(QBlocks.PALM_SAPLING.get());
                        pOutput.accept(QBlocks.JOSHUA_SAPLING.get());

                        //Logs
                        pOutput.accept(QBlocks.SILVER_BIRCH_WOOD.get());
                        pOutput.accept(QBlocks.SILVER_BIRCH_LOG.get());
                        pOutput.accept(QBlocks.STRIPPED_SILVER_BIRCH_LOG.get());
                        pOutput.accept(QBlocks.PEWEN_WOOD.get());
                        pOutput.accept(QBlocks.PEWEN_LOG.get());
                        pOutput.accept(QBlocks.STRIPPED_PEWEN_WOOD.get());
                        pOutput.accept(QBlocks.STRIPPED_PEWEN_LOG.get());
                        pOutput.accept(QBlocks.CYPRESS_WOOD.get());
                        pOutput.accept(QBlocks.CYPRESS_LOG.get());
                        pOutput.accept(QBlocks.STRIPPED_CYPRESS_LOG.get());
                        pOutput.accept(QBlocks.PALM_WOOD.get());
                        pOutput.accept(QBlocks.PALM_LOG.get());
                        pOutput.accept(QBlocks.JOSHUA_WOOD.get());
                        pOutput.accept(QBlocks.JOSHUA_LOG.get());


                        //Leaves
                        pOutput.accept(QBlocks.SILVER_BIRCH_LEAVES.get());
                        pOutput.accept(QBlocks.PEWEN_BRANCH.get());
                        pOutput.accept(QBlocks.PEWEN_PINES.get());
                        pOutput.accept(QBlocks.ANCIENT_LEAVES.get());
                        pOutput.accept(QBlocks.CYPRESS_LEAVES.get());
                        pOutput.accept(QBlocks.PALM_LEAVES.get());
                        pOutput.accept(QBlocks.PALM_BEARD.get());
                        pOutput.accept(QBlocks.JOSHUA_LEAVES.get());
                        pOutput.accept(QBlocks.JOSHUA_BEARD.get());

                        //Planks
                        pOutput.accept(QBlocks.SILVER_BIRCH_PLANKS.get());
                        pOutput.accept(QBlocks.PEWEN_PLANKS.get());
                        pOutput.accept(QBlocks.CYPRESS_PLANKS.get());

                        //Stairs
                        pOutput.accept(QBlocks.SILVER_BIRCH_STAIRS.get());
                        pOutput.accept(QBlocks.PEWEN_PLANKS_STAIRS.get());
                        pOutput.accept(QBlocks.LIMESTONE_STAIRS.get());
                        pOutput.accept(QBlocks.SMOOTH_LIMESTONE_STAIRS.get());
                        pOutput.accept(QBlocks.CYPRESS_STAIRS.get());

                        //Slabs
                        pOutput.accept(QBlocks.SILVER_BIRCH_SLAB.get());
                        pOutput.accept(QBlocks.PEWEN_PLANKS_SLAB.get());
                        pOutput.accept(QBlocks.LIMESTONE_SLAB.get());
                        pOutput.accept(QBlocks.SMOOTH_LIMESTONE_SLAB.get());
                        pOutput.accept(QBlocks.CYPRESS_SLAB.get());

                        //Walls
                        pOutput.accept(QBlocks.LIMESTONE_WALL.get());
                        pOutput.accept(QBlocks.SMOOTH_LIMESTONE_WALL.get());

                        //Fences
                        pOutput.accept(QBlocks.PEWEN_PLANKS_FENCE.get());
                        pOutput.accept(QBlocks.PEWEN_FENCE_GATE.get());
                        pOutput.accept(QBlocks.CYPRESS_FENCE.get());
                        pOutput.accept(QBlocks.CYPRESS_FENCE_GATE.get());

                        // Doors
                        pOutput.accept(QBlocks.SILVER_BIRCH_DOOR.get());
                        pOutput.accept(QBlocks.PEWEN_DOOR.get());
                        pOutput.accept(QBlocks.CYPRESS_DOOR.get());

                        //Trapdoors
                        pOutput.accept(QBlocks.SILVER_BIRCH_TRAPDOOR.get());
                        pOutput.accept(QBlocks.PEWEN_TRAPDOOR.get());
                        pOutput.accept(QBlocks.CYPRESS_TRAPDOOR.get());

                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
