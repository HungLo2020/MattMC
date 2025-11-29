package net.matt.quantize.worldgen;

import net.matt.quantize.Quantize;
import net.matt.quantize.worldgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.registries.RegistryObject;

public class QFeatures {
    public static final DeferredRegister<Feature<?>> DEF_REG = DeferredRegister.create(ForgeRegistries.FEATURES, Quantize.MOD_ID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> PEWEN_TREE = DEF_REG.register("pewen_tree", () -> new PewenTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> LEAFCUTTER_ANTHILL = DEF_REG.register("leafcutter_anthill", () -> new FeatureLeafcutterAnthill(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> ANCIENT_TREE = DEF_REG.register("ancient_tree", () -> new AncientTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> GIANT_ANCIENT_TREE = DEF_REG.register("giant_ancient_tree", () -> new GiantAncientTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> SUBTERRANODON_ROOST = DEF_REG.register("subterranodon_roost", () -> new SubterranodonRoostFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> AMBERSOL = DEF_REG.register("ambersol", () -> new AmbersolFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> AMBER_MONOLITH = DEF_REG.register("amber_monolith", () -> new AmberMonolithFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> CYCAD = DEF_REG.register("cycad", () -> new CycadFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> VOLCANO_BOULDER = DEF_REG.register("volcano_boulder", () -> new VolcanoBoulderFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<CoveredBlockBlobConfiguration>> COVERED_BLOCK_BLOB = DEF_REG.register("covered_block_blob", () -> new CoveredBlockBlobFeature(CoveredBlockBlobConfiguration.CODEC));
    public static final RegistryObject<Feature<UndergroundRuinsFeatureConfiguration>> FORLORN_RUINS = DEF_REG.register("forlorn_ruins", () -> new ForlornRuinsFeature(UndergroundRuinsFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<UndergroundRuinsFeatureConfiguration>> UNDERGROUND_RUINS = DEF_REG.register("underground_ruins", () -> new UndergroundRuinsFeature(UndergroundRuinsFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<RuTreeConfiguration>> CYPRESS_TREE = DEF_REG.register("cypress_tree", () -> new CypressTreeFeature(RuTreeConfiguration.CODEC));
    public static final RegistryObject<Feature<RuTreeConfiguration>> GIANT_CYPRESS_TREE = DEF_REG.register("giant_cypress_tree", () -> new GiantCypressTreeFeature(RuTreeConfiguration.CODEC));
    public static final RegistryObject<Feature<RuTreeConfiguration>> PALM_TREE = DEF_REG.register("palm_tree", () -> new PalmTreeFeature(RuTreeConfiguration.CODEC));
    public static final RegistryObject<Feature<RuTreeConfiguration>> SMALL_JOSHUA_TREE = DEF_REG.register("small_joshua_tree", () -> new SmallJoshuaTreeFeature(RuTreeConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> MEDIUM_JOSHUA_TREE = DEF_REG.register("medium_joshua_tree", () -> new MediumJoshuaTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> LARGE_JOSHUA_TREE = DEF_REG.register("large_joshua_tree", () -> new LargeJoshuaTreeFeature(NoneFeatureConfiguration.CODEC));





    public static void register(IEventBus bus) {
        DEF_REG.register(bus);
    }

    private QFeatures() {}

}
