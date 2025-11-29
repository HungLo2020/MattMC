package net.matt.quantize.worldgen;

import net.matt.quantize.block.QBlocks;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import java.util.List;

public class QConfiguredFeatures {

    ///public static final ResourceKey<ConfiguredFeature<?, ?>> OVERWORLD_JADE_ORE_KEY = registerKey("jade_ore");
    ///public static final ResourceKey<ConfiguredFeature<?, ?>> OVERWORLD_LEAD_ORE_KEY = registerKey("lead_ore");
    ///public static final ResourceKey<ConfiguredFeature<?, ?>> OVERWORLD_TIN_ORE_KEY = registerKey("tin_ore");
    ///public static final ResourceKey<ConfiguredFeature<?, ?>> OVERWORLD_OSMIUM_ORE_KEY = registerKey("osmium_ore");
    ///public static final ResourceKey<ConfiguredFeature<?, ?>> OVERWORLD_URANIUM_ORE_KEY = registerKey("uranium_ore");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SILVER_BIRCH_TREE = registerKey("silver_birch_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CYPRESS_TREE = registerKey("cypress_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> GIANT_CYPRESS_TREE = registerKey("giant_cypress_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BAYOU_VEGETATION = registerKey("patch_bayou_vegetation");
    //public static final ResourceKey<ConfiguredFeature<?, ?>> ELEPHANT_EAR = registerKey("elephant_ear");
    //public static final ResourceKey<ConfiguredFeature<?, ?>> ELEPHANT_EAR_AQUATIC = registerKey("elephant_ear_aquatic");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PALM_TREE = registerKey("palm_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TALL_PALM_TREE = registerKey("tall_palm_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> JOSHUA_TREE_SHRUB = registerKey("joshua_tree_shrub");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MEDIUM_JOSHUA_TREE = registerKey("medium_joshua_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> LARGE_JOSHUA_TREE = registerKey("large_joshua_tree");



    public static void bootstrap(BootstapContext<ConfiguredFeature<?, ?>> context) {
        RuleTest stoneReplaceable = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
        RuleTest deepslateReplaceables = new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
        RuleTest netherrackReplacables = new BlockMatchTest(Blocks.NETHERRACK);
        RuleTest endReplaceables = new BlockMatchTest(Blocks.END_STONE);

        // Ores Generation
        /*List<OreConfiguration.TargetBlockState> overworldJadeOres = List.of(OreConfiguration.target(stoneReplaceable,
                        QBlocks.JADE_ORE.get().defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, QBlocks.DEEPSLATE_JADE_ORE.get().defaultBlockState()));
        register(context, OVERWORLD_JADE_ORE_KEY, Feature.ORE, new OreConfiguration(overworldJadeOres, 9));
        List<OreConfiguration.TargetBlockState> overworldLeadOres = List.of(OreConfiguration.target(stoneReplaceable,
                        QBlocks.LEAD_ORE.get().defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, QBlocks.DEEPSLATE_LEAD_ORE.get().defaultBlockState()));
        register(context, OVERWORLD_LEAD_ORE_KEY, Feature.ORE, new OreConfiguration(overworldLeadOres, 9));
        List<OreConfiguration.TargetBlockState> overworldTinOres = List.of(OreConfiguration.target(stoneReplaceable,
                        QBlocks.TIN_ORE.get().defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, QBlocks.DEEPSLATE_TIN_ORE.get().defaultBlockState()));
        register(context, OVERWORLD_TIN_ORE_KEY, Feature.ORE, new OreConfiguration(overworldTinOres, 9));
        List<OreConfiguration.TargetBlockState> overworldOsmiumOres = List.of(OreConfiguration.target(stoneReplaceable,
                        QBlocks.OSMIUM_ORE.get().defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, QBlocks.DEEPSLATE_OSMIUM_ORE.get().defaultBlockState()));
        register(context, OVERWORLD_OSMIUM_ORE_KEY, Feature.ORE, new OreConfiguration(overworldOsmiumOres, 9));
        List<OreConfiguration.TargetBlockState> overworldUraniumOres = List.of(OreConfiguration.target(stoneReplaceable,
                        QBlocks.URANIUM_ORE.get().defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, QBlocks.DEEPSLATE_URANIUM_ORE.get().defaultBlockState()));
        register(context, OVERWORLD_URANIUM_ORE_KEY, Feature.ORE, new OreConfiguration(overworldUraniumOres, 9));*/

        // Tree Generation
        /*TreeConfiguration silverBirchCfg = new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(QBlocks.SILVER_BIRCH_LOG.get()),   // trunk block
                new StraightTrunkPlacer(5, 2, 0),                              // 5‑block trunk, 2 log base height
                BlockStateProvider.simple(QBlocks.SILVER_BIRCH_LEAVES.get()),                // foliage block
                new BlobFoliagePlacer(ConstantInt.of(2), ConstantInt.of(0), 3),// standard birch blob
                new TwoLayersFeatureSize(1, 0, 1))                             // layer settings like vanilla birch
                .ignoreVines()                                                     // optional, stops vine replacement
                .build();
        register(context, SILVER_BIRCH_TREE, Feature.TREE, silverBirchCfg);*/
    }

    public static ResourceKey<ConfiguredFeature<?, ?>> registerKey(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, new ResourceIdentifier(name));
    }

    private static <FC extends FeatureConfiguration, F extends Feature<FC>> void register(BootstapContext<ConfiguredFeature<?, ?>> context,
                                                                                          ResourceKey<ConfiguredFeature<?, ?>> key, F feature, FC configuration) {
        context.register(key, new ConfiguredFeature<>(feature, configuration));
    }
}


