package net.minecraft.world.level.levelgen.feature;

import com.github.alexmodguy.alexscaves.server.level.feature.PewenTreeFeature;
import com.github.alexmodguy.alexscaves.server.level.feature.AncientTreeFeature;
import com.github.alexmodguy.alexscaves.server.level.feature.GiantAncientTreeFeature;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Registration for custom tree features from Alex's Caves
 */
public class ACFeatures {
    
    public static final Feature<NoneFeatureConfiguration> PEWEN_TREE = register(
        "pewen_tree",
        new PewenTreeFeature(NoneFeatureConfiguration.CODEC)
    );
    
    public static final Feature<NoneFeatureConfiguration> ANCIENT_TREE = register(
        "ancient_tree",
        new AncientTreeFeature(NoneFeatureConfiguration.CODEC)
    );
    
    public static final Feature<NoneFeatureConfiguration> GIANT_ANCIENT_TREE = register(
        "giant_ancient_tree",
        new GiantAncientTreeFeature(NoneFeatureConfiguration.CODEC)
    );
    
    private static <C extends net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration, F extends Feature<C>> F register(String name, F feature) {
        return Registry.register(BuiltInRegistries.FEATURE, ResourceLocation.withDefaultNamespace(name), feature);
    }
    
    public static void init() {
        // Static initialization trigger
    }
}
