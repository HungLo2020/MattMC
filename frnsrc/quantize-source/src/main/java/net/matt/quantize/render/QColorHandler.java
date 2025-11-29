package net.matt.quantize.render;

import net.matt.quantize.block.QBlocks;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.awt.*;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class QColorHandler {
    @SubscribeEvent
    public static void grassBlockColorLoad(RegisterColorHandlersEvent.Block event) {
        event.getBlockColors().register((bs, world, pos, index) -> {
                    return world != null && pos != null ? BiomeColors.getAverageGrassColor(world, pos) : GrassColor.get(0.5D, 1.0D);
                }//,
                /*QBlocks.PEAT_GRASS_BLOCK.get(),
                QBlocks.SILT_GRASS_BLOCK.get(),
                QBlocks.STONE_GRASS_BLOCK.get(),
                QBlocks.ARGILLITE_GRASS_BLOCK.get(),
                QBlocks.DEEPSLATE_GRASS_BLOCK.get(),
                QBlocks.CHALK_GRASS_BLOCK.get(),
                QBlocks.MEDIUM_GRASS.get(),
                QBlocks.STEPPE_GRASS.get(),
                QBlocks.STONE_BUD.get(),
                QBlocks.ORANGE_CONEFLOWER.get(),
                QBlocks.PURPLE_CONEFLOWER.get(),
                QBlocks.POTTED_ORANGE_CONEFLOWER.get(),
                QBlocks.POTTED_PURPLE_CONEFLOWER.get(),
                QBlocks.TASSEL.get(),
                QBlocks.CLOVER.get(),
                QBlocks.BLADED_GRASS.get(),
                QBlocks.BLADED_TALL_GRASS.get()*/
        );
    }
    @SubscribeEvent
    public static void grassItemColorLoad(RegisterColorHandlersEvent.Item event) {
        event.getItemColors().register((stack, index) -> {
                    return GrassColor.get(0.5D, 1.0D);
                }//,
                /*QBlocks.PEAT_GRASS_BLOCK.get(),
                QBlocks.SILT_GRASS_BLOCK.get(),
                QBlocks.STONE_GRASS_BLOCK.get(),
                QBlocks.ARGILLITE_GRASS_BLOCK.get(),
                QBlocks.DEEPSLATE_GRASS_BLOCK.get(),
                QBlocks.CHALK_GRASS_BLOCK.get(),
                QBlocks.MEDIUM_GRASS.get(),
                QBlocks.STEPPE_GRASS.get(),
                QBlocks.STONE_BUD.get(),
                QBlocks.BLADED_GRASS.get(),
                QBlocks.CLOVER.get(),
                QBlocks.BLADED_TALL_GRASS.get()*/
        );
    }

    @SubscribeEvent
    public static void foliageBlockColorLoad(RegisterColorHandlersEvent.Block event) {
        event.getBlockColors().register((bs, world, pos, index) -> {
                    return world != null && pos != null ? BiomeColors.getAverageFoliageColor(world, pos) : FoliageColor.get(0.5D, 1.0D);
                },
                /*QBlocks.STEPPE_SHRUB.get(),
                QBlocks.STEPPE_TALL_GRASS.get(),
                QBlocks.BAOBAB_LEAVES.get(),
                QBlocks.MAGNOLIA_LEAVES.get(),
                QBlocks.APPLE_OAK_LEAVES.get(),
                QBlocks.FLOWERING_LEAVES.get(),
                QBlocks.EUCALYPTUS_LEAVES.get(),
                QBlocks.PALM_LEAVES.get(),
                QBlocks.JOSHUA_LEAVES.get(),
                QBlocks.PINE_LEAVES.get(),
                QBlocks.REDWOOD_LEAVES.get(),
                QBlocks.WILLOW_LEAVES.get(),
                QBlocks.MAPLE_LEAVES.get(),
                QBlocks.MAPLE_LEAF_PILE.get(),
                QBlocks.WINDSWEPT_GRASS.get(),
                QBlocks.SOCOTRA_LEAVES.get(),
                QBlocks.KAPOK_LEAVES.get(),
                QBlocks.KAPOK_VINES.get(),
                QBlocks.KAPOK_VINES_PLANT.get(),
                QBlocks.SMALL_OAK_LEAVES.get(),*/
                QBlocks.JOSHUA_LEAVES.get(),
                QBlocks.PALM_LEAVES.get(),
                QBlocks.CYPRESS_LEAVES.get(),
                QBlocks.ELEPHANT_EAR.get()
        );
    }
    @SubscribeEvent
    public static void foliageItemColorLoad(RegisterColorHandlersEvent.Item event) {
        event.getItemColors().register((stack, index) -> {
                    return FoliageColor.get(0.5D, 1.0D);
                },
                /*QBlocks.STEPPE_SHRUB.get(),
                QBlocks.STEPPE_TALL_GRASS.get(),
                QBlocks.ELEPHANT_EAR.get(),
                QBlocks.BAOBAB_LEAVES.get(),
                QBlocks.MAGNOLIA_LEAVES.get(),
                QBlocks.APPLE_OAK_LEAVES.get(),
                QBlocks.FLOWERING_LEAVES.get(),
                QBlocks.JOSHUA_LEAVES.get(),
                QBlocks.CYPRESS_LEAVES.get(),
                QBlocks.EUCALYPTUS_LEAVES.get(),
                QBlocks.PALM_LEAVES.get(),
                QBlocks.PINE_LEAVES.get(),
                QBlocks.REDWOOD_LEAVES.get(),
                QBlocks.WILLOW_LEAVES.get(),
                QBlocks.MAPLE_LEAVES.get(),
                QBlocks.MAPLE_LEAF_PILE.get(),
                QBlocks.WINDSWEPT_GRASS.get(),
                QBlocks.SOCOTRA_LEAVES.get(),
                QBlocks.KAPOK_LEAVES.get(),
                QBlocks.KAPOK_VINES.get(),
                QBlocks.KAPOK_VINES_PLANT.get(),
                QBlocks.SMALL_OAK_LEAVES.get(),*/
                QBlocks.CYPRESS_LEAVES.get(),
                QBlocks.JOSHUA_LEAVES.get(),
                QBlocks.PALM_LEAVES.get(),
                QBlocks.ELEPHANT_EAR.get()
        );
    }
    /*@SubscribeEvent
    public static void rainbowCrystalColorLoad(RegisterColorHandlersEvent.Block event) {
        event.getBlockColors().register((bs, world, pos, index) -> {
            return world != null && pos != null ? getRainbowColor(world, pos) : FoliageColor.getDefaultColor();
        },QBlocks.PRISMARITE_CLUSTER.get(), QBlocks.HANGING_PRISMARITE.get(), QBlocks.LARGE_PRISMARITE_CLUSTER.get(), QBlocks.PRISMOSS.get(),QBlocks.DEEPSLATE_PRISMOSS.get(),QBlocks.PRISMOSS_SPROUT.get());
    }*/

    /*@SubscribeEvent
    public static void rainbowGlassColorLoad(RegisterColorHandlersEvent.Block event) {
        event.getBlockColors().register((bs, world, pos, index) -> {
            return world != null && pos != null ? getRainbowGlassColor(world, pos) : FoliageColor.getDefaultColor();
        },QBlocks.PRISMAGLASS.get());
    }*/

    /*@SubscribeEvent
    public static void rainbowEucalyptusColorLoad(RegisterColorHandlersEvent.Block event) {
        event.getBlockColors().register((bs, world, pos, index) -> {
                    return world != null && pos != null ? getRainbowEucalyptusColor(world, pos) : FoliageColor.getDefaultColor();
                },      QBlocks.EUCALYPTUS_LOG.get(),
                QBlocks.EUCALYPTUS_WOOD.get()
        );
    }*/

    @SubscribeEvent
    public static void aspenColorLoad(RegisterColorHandlersEvent.Block event) {
        event.getBlockColors().register((bs, world, pos, index) -> {
                    return world != null && pos != null ? getAspenColor(world, pos) : FoliageColor.getDefaultColor();
                }, QBlocks.SILVER_BIRCH_LEAVES.get()//,
                //QBlocks.SILVER_BIRCH_LEAF_PILE.get()
        );
    }

    @SubscribeEvent
    public static void enchantedAspenColorLoad(RegisterColorHandlersEvent.Block event) {
        event.getBlockColors().register((bs, world, pos, index) -> {
                    return world != null && pos != null ? getEnchantedAspenColor(world, pos) : FoliageColor.getDefaultColor();
                }//, QBlocks.ENCHANTED_BIRCH_LEAVES.get(),
                //QBlocks.ENCHANTED_BIRCH_LEAF_PILE.get()
        );
    }

    public static int getAspenColor(BlockAndTintGetter world, BlockPos pos) {
        Color aspen = Color.getHSBColor(((Mth.sin(((float)pos.getX()/10) + Mth.sin(((float)pos.getZ() + (float)pos.getX()) / 50) * 3)) / 75)+0.15F, 0.8F, 1.0F);
        return aspen.getRGB();
    }

    public static int getEnchantedAspenColor(BlockAndTintGetter world, BlockPos pos) {
        Color aspen = Color.getHSBColor(((Mth.sin(((float)pos.getX()/10) + Mth.sin(((float)pos.getZ() + (float)pos.getX()) / 50) * 3)) / 50)+0.58F, 0.8F, 1.0F);
        return aspen.getRGB();
    }

    public static int getRainbowColor(BlockAndTintGetter world, BlockPos pos) {
        Color rainbow = Color.getHSBColor(((float)pos.getX() + (float)pos.getZ()) / 50.0F, 0.9F, 1.0F);
        return rainbow.getRGB();
    }

    public static int getRainbowGlassColor(BlockAndTintGetter world, BlockPos pos) {
        Color rainbow = Color.getHSBColor(((float)pos.getX() + (float)pos.getY() + (float)pos.getZ()) / 35.0F, 1.0F, 1.0F);
        return rainbow.getRGB();
    }
    /*public static int getRainbowEucalyptusColor(BlockAndTintGetter world, BlockPos pos) {
        Color rainbow = Color.getHSBColor(((float)pos.getX() + (float)pos.getY() + (float)pos.getZ()) /
                        RuCommonConfig.EUCALYPTUS_TRANSITION_SIZE.get().floatValue(),
                RuCommonConfig.EUCALYPTUS_SATURATION.get().floatValue(),
                RuCommonConfig.EUCALYPTUS_BRIGHTNESS.get().floatValue());
        return rainbow.getRGB();
    }*/
}