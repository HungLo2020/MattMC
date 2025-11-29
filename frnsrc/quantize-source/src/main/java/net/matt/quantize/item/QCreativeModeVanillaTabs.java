package net.matt.quantize.item;

import net.matt.quantize.Quantize;
import net.matt.quantize.block.QBlocks;
import net.matt.quantize.item.QItems;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CreativeModeTab;

import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Quantize.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class QCreativeModeVanillaTabs {
    @SubscribeEvent
    public static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {

        // Spawn Eggs
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(QItems.CRAB_SPAWN_EGG.get());
            event.accept(QItems.CAIMAN_SPAWN_EGG.get());
            event.accept(QItems.CROCODILE_SPAWN_EGG.get());
            event.accept(QItems.LOBSTER_SPAWN_EGG.get());
            event.accept(QItems.MANTIS_SHRIMP_SPAWN_EGG.get());
            event.accept(QItems.MIMIC_OCTOPUS_SPAWN_EGG.get());
            event.accept(QItems.ALLIGATOR_SNAPPING_TURTLE_SPAWN_EGG.get());
            event.accept(QItems.ANACONDA_SPAWN_EGG.get());
            event.accept(QItems.ANTEATER_SPAWN_EGG.get());
            event.accept(QItems.LEAFCUTTER_ANT_SPAWN_EGG.get());
            event.accept(QItems.BALD_EAGLE_SPAWN_EGG.get());
            event.accept(QItems.SHOEBILL_SPAWN_EGG.get());
            event.accept(QItems.SHOEBILL_SPAWN_EGG.get());
            event.accept(QItems.CACHALOT_WHALE_SPAWN_EGG.get());
            event.accept(QItems.GIANT_SQUID_SPAWN_EGG.get());
            event.accept(QItems.ORCA_SPAWN_EGG.get());
            event.accept(QItems.SUBTERANODON_SPAWN_EGG.get());
            event.accept(QItems.VALLUMRAPTOR_SPAWN_EGG.get());
            event.accept(QItems.GROTTOCERATOPS_SPAWN_EGG.get());
            event.accept(QItems.TRILOCARIS_SPAWN_EGG.get());
            event.accept(QItems.TREMORSAURUS_SPAWN_EGG.get());
            event.accept(QItems.RELICHEIRUS_SPAWN_EGG.get());
            event.accept(QItems.LUXTRUCTOSAURUS_SPAWN_EGG.get());
            event.accept(QItems.ATLATITAN_SPAWN_EGG.get());
            event.accept(QItems.CAPUCHIN_MONKEY_SPAWN_EGG.get());
            event.accept(QItems.CATFISH_SPAWN_EGG.get());
            event.accept(QItems.FRILLED_SHARK_SPAWN_EGG.get());
            event.accept(QItems.BLOBFISH_SPAWN_EGG.get());
            event.accept(QItems.CAVE_CENTIPEDE_SPAWN_EGG.get());
            event.accept(QItems.COCKROACH_SPAWN_EGG.get());
            event.accept(QItems.EMU_SPAWN_EGG.get());
            event.accept(QItems.COMB_JELLY_SPAWN_EGG.get());
            event.accept(QItems.CROW_SPAWN_EGG.get());
            event.accept(QItems.COSMIC_COD_SPAWN_EGG.get());
            event.accept(QItems.ELEPHANT_SPAWN_EGG.get());
            event.accept(QItems.ENDERGRADE_SPAWN_EGG.get());
            event.accept(QItems.ENDERIOPHAGE_SPAWN_EGG.get());
            event.accept(QItems.GAZELLE_SPAWN_EGG.get());
            event.accept(QItems.GELADA_MONKEY_SPAWN_EGG.get());
            event.accept(QItems.GORILLA_SPAWN_EGG.get());
            event.accept(QItems.GRIZZLY_BEAR_SPAWN_EGG.get());
            event.accept(QItems.HAMMERHEAD_SHARK_SPAWN_EGG.get());
            event.accept(QItems.KOMODO_DRAGON_SPAWN_EGG.get());
            event.accept(QItems.UNDERMINER_SPAWN_EGG.get());
            event.accept(QItems.TOUCAN_SPAWN_EGG.get());
            event.accept(QItems.TIGER_SPAWN_EGG.get());
            event.accept(QItems.SNOW_LEOPARD_SPAWN_EGG.get());
            event.accept(QItems.SEAGULL_SPAWN_EGG.get());
            event.accept(QItems.SEAL_SPAWN_EGG.get());
            event.accept(QItems.ROADRUNNER_SPAWN_EGG.get());
            event.accept(QItems.RHINOCEROS_SPAWN_EGG.get());
            event.accept(QItems.POTOO_SPAWN_EGG.get());
        }

        // Mob Buckets
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            var entries = event.getEntries();
            var vis = CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;
            // start after vanilla tadpole bucket
            ItemStack anchor = new ItemStack(Items.TADPOLE_BUCKET);
            ItemStack s;
            s = new ItemStack(QItems.CRAB_BUCKET.get());              entries.putAfter(anchor, s, vis); anchor = s;
            s = new ItemStack(QItems.LOBSTER_BUCKET.get());           entries.putAfter(anchor, s, vis); anchor = s;
            s = new ItemStack(QItems.MIMIC_OCTOPUS_BUCKET.get());     entries.putAfter(anchor, s, vis); anchor = s;
            s = new ItemStack(QItems.TRILOCARIS_BUCKET.get());        entries.putAfter(anchor, s, vis); anchor = s;
            s = new ItemStack(QItems.SMALL_CATFISH_BUCKET.get());     entries.putAfter(anchor, s, vis); anchor = s;
            s = new ItemStack(QItems.MEDIUM_CATFISH_BUCKET.get());    entries.putAfter(anchor, s, vis); anchor = s;
            s = new ItemStack(QItems.LARGE_CATFISH_BUCKET.get());     entries.putAfter(anchor, s, vis); anchor = s;
            s = new ItemStack(QItems.FRILLED_SHARK_BUCKET.get());     entries.putAfter(anchor, s, vis); anchor = s;
            s = new ItemStack(QItems.BLOBFISH_BUCKET.get());          entries.putAfter(anchor, s, vis); anchor = s;
            s = new ItemStack(QItems.COMB_JELLY_BUCKET.get());        entries.putAfter(anchor, s, vis); anchor = s;
            s = new ItemStack(QItems.COSMIC_COD_BUCKET.get());        entries.putAfter(anchor, s, vis); anchor = s;
        }

        // Ingredients tab
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {

            //Eggs
            event.accept(QBlocks.CAIMAN_EGG.get());
            event.accept(QBlocks.CROCODILE_EGG.get());

            // Ingredients
            event.accept(QItems.CROCODILE_SCUTE.get());
            event.accept(QItems.CRAB_SHELL.get());
            event.accept(QItems.SPIKED_SCUTE.get());
            event.accept(QItems.SHED_SNAKE_SKIN.get());
            event.accept(QItems.GONGYLIDIA.get());
            event.accept(QItems.FISH_OIL.get());
            event.accept(QItems.CACHALOT_WHALE_TOOTH.get());
            event.accept(QItems.AMBERGRIS.get());
            event.accept(QItems.LOST_TENTACLE.get());
            event.accept(QItems.HEAVY_BONE.get());
            event.accept(QItems.TOUGH_HIDE.get());
            event.accept(QItems.SERRATED_SHARK_TOOTH.get());
            event.accept(QItems.CENTIPEDE_LEG.get());
            event.accept(QItems.COCKROACH_OOTHECA.get());
            event.accept(QItems.COCKROACH_WING.get());
            event.accept(QItems.COCKROACH_WING_FRAGMENT.get());
            event.accept(QItems.EMU_FEATHER.get());
            event.accept(QItems.RAINBOW_JELLY.get());
            event.accept(QItems.ACACIA_BLOSSOM.get());
            event.accept(QItems.GAZELLE_HORN.get());
            event.accept(QItems.BEAR_FUR.get());
            event.accept(QItems.SHARK_TOOTH.get());
            event.accept(QItems.KOMODO_SPIT.get());
            event.accept(QItems.KOMODO_SPIT_BOTTLE.get());
        }

        // Food & Drinks tab
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {

            // Raw Foods
            event.accept(QItems.CRAB_LEG.get());
            event.accept(QItems.LOBSTER_TAIL.get());
            event.accept(QBlocks.DINOSAUR_CHOP.get());
            event.accept(QItems.RAW_CATFISH.get());
            event.accept(QItems.BLOBFISH.get());
            event.accept(QItems.EMU_EGG.get());
            event.accept(QItems.COSMIC_COD.get());

            // Cooked Foods
            event.accept(QItems.COOKED_CRAB_LEG.get());
            event.accept(QItems.COOKED_LOBSTER_TAIL.get());
            event.accept(QItems.SHRIMP_FRIED_RICE.get());
            event.accept(QItems.SEETHING_STEW.get());
            event.accept(QItems.SERENE_SALAD.get());
            event.accept(QItems.PRIMORDIAL_SOUP.get());
            event.accept(QItems.DINOSAUR_NUGGET.get());
            event.accept(QBlocks.COOKED_DINOSAUR_CHOP.get());
            event.accept(QItems.COOKED_CATFISH.get());
            event.accept(QItems.BOILED_EMU_EGG.get());
        }

    }
}