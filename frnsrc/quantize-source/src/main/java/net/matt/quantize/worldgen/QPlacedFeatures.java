package net.matt.quantize.worldgen;


import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.*;

import java.util.List;

public class QPlacedFeatures {
    ///public static final ResourceKey<PlacedFeature> JADE_ORE_PLACED_KEY = registerKey("jade_ore_placed");
    ///public static final ResourceKey<PlacedFeature> LEAD_ORE_PLACED_KEY = registerKey("lead_ore_placed");
    ///public static final ResourceKey<PlacedFeature> TIN_ORE_PLACED_KEY = registerKey("tin_ore_placed.json");
    ///public static final ResourceKey<PlacedFeature> OSMIUM_ORE_PLACED_KEY = registerKey("osmium_ore_placed");
    ///public static final ResourceKey<PlacedFeature> URANIUM_ORE_PLACED_KEY = registerKey("uranium_ore_placed");
    public static final ResourceKey<PlacedFeature> SILVER_BIRCH_PLACED_KEY = registerKey("silver_birch_tree");
    public static final ResourceKey<PlacedFeature> BAYOU_VEGETATION = registerKey("bayou_vegetation");
    //public static final ResourceKey<PlacedFeature> ELEPHANT_EAR_SPARSE = registerKey("elephant_ear_sparse");
    //public static final ResourceKey<PlacedFeature> ELEPHANT_EAR_DENSE = registerKey("elephant_ear_dense");
    //public static final ResourceKey<PlacedFeature> ELEPHANT_EAR_AQUATIC = registerKey("elephant_ear_aquatic");

    public static void bootstrap(BootstapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

        /*register(context, JADE_ORE_PLACED_KEY, configuredFeatures.getOrThrow(QConfiguredFeatures.OVERWORLD_JADE_ORE_KEY),
                QOrePlacement.commonOrePlacement(12,
                        HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(80))));
        register(context, LEAD_ORE_PLACED_KEY, configuredFeatures.getOrThrow(QConfiguredFeatures.OVERWORLD_LEAD_ORE_KEY),
                QOrePlacement.commonOrePlacement(12,
                        HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(80))));
        register(context, TIN_ORE_PLACED_KEY, configuredFeatures.getOrThrow(QConfiguredFeatures.OVERWORLD_TIN_ORE_KEY),
                QOrePlacement.commonOrePlacement(12,
                        HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(80))));
        register(context, OSMIUM_ORE_PLACED_KEY, configuredFeatures.getOrThrow(QConfiguredFeatures.OVERWORLD_OSMIUM_ORE_KEY),
                QOrePlacement.commonOrePlacement(12,
                        HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(80))));
        register(context, URANIUM_ORE_PLACED_KEY, configuredFeatures.getOrThrow(QConfiguredFeatures.OVERWORLD_URANIUM_ORE_KEY),
                QOrePlacement.commonOrePlacement(12,
                        HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(80))));*/
        register(context, SILVER_BIRCH_PLACED_KEY,
                configuredFeatures.getOrThrow(QConfiguredFeatures.SILVER_BIRCH_TREE),
                List.of()               // no placement rules ‑ good for /place feature testing

        );
        /*register(context, ELEPHANT_EAR_SPARSE,
                configuredFeatures.getOrThrow(QConfiguredFeatures.ELEPHANT_EAR),
                List.of()               // no placement rules ‑ good for /place feature testing

        );
        register(context, ELEPHANT_EAR_AQUATIC,
                configuredFeatures.getOrThrow(QConfiguredFeatures.ELEPHANT_EAR),
                List.of()               // no placement rules ‑ good for /place feature testing

        );
        register(context, ELEPHANT_EAR_DENSE,
                configuredFeatures.getOrThrow(QConfiguredFeatures.ELEPHANT_EAR),
                List.of()               // no placement rules ‑ good for /place feature testing

        );*/
        register(context, BAYOU_VEGETATION, configuredFeatures.getOrThrow(QConfiguredFeatures.BAYOU_VEGETATION), List.of(NoiseThresholdCountPlacement.of(-0.8D, 5, 14), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP_WORLD_SURFACE, BiomeFilter.biome()));
    }


    private static ResourceKey<PlacedFeature> registerKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, new ResourceIdentifier(name));
    }

    private static void register(BootstapContext<PlacedFeature> context, ResourceKey<PlacedFeature> key, Holder<ConfiguredFeature<?, ?>> configuration,
                                 List<PlacementModifier> modifiers) {
        context.register(key, new PlacedFeature(configuration, List.copyOf(modifiers)));
    }
}
