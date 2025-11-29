package net.matt.quantize.mixin;

import net.matt.quantize.modules.biomes.BiomeGenerationConfig;
import net.matt.quantize.modules.biomes.BiomeGenerationNoiseCondition;
import net.matt.quantize.modules.biomes.ModBiomeRarity;
import net.matt.quantize.modules.biomes.BiomeSourceAccessor;
import net.matt.quantize.modules.biomes.MultiNoiseBiomeSourceAccessor;
import net.matt.quantize.utils.VoronoiGenerator;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.matt.quantize.Quantize;

import java.util.Map;

@Mixin(value = MultiNoiseBiomeSource.class, priority = -69420)
public class MultiNoiseBiomeSourceMixin implements MultiNoiseBiomeSourceAccessor {

    private long lastSampledWorldSeed;

    private ResourceKey<Level> lastSampledDimension;

    @Inject(at = @At("HEAD"),
            method = "Lnet/minecraft/world/level/biome/MultiNoiseBiomeSource;getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
            cancellable = true
    )
    private void citadel_getNoiseBiomeCoords(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> cir) {
        //Quantize.LOGGER.debug("MultiNoiseBiomeSourceMixin: Testing coordinates ({}, {}, {})", x, y, z);
        VoronoiGenerator.VoronoiInfo voronoiInfo = ModBiomeRarity.getRareBiomeInfoForQuad(lastSampledWorldSeed, x, z);
        if(voronoiInfo != null){
            //Quantize.LOGGER.debug("Voronoi Info: {}", voronoiInfo);
            float unquantizedDepth = Climate.unquantizeCoord(sampler.sample(x, y, z).depth());
            int foundRarityOffset = ModBiomeRarity.getRareBiomeOffsetId(voronoiInfo);
            //Quantize.LOGGER.debug("Found Rarity Offset: {}", foundRarityOffset);
            for (Map.Entry<ResourceKey<Biome>, BiomeGenerationNoiseCondition> condition : BiomeGenerationConfig.BIOMES.entrySet()) {
                //Quantize.LOGGER.debug("Testing Biome: {}, Condition: {}", condition.getKey(), condition.getValue());
                /*Quantize.LOGGER.debug("  cond={}  rarOff={}  myOff={}  pass={}  dim={}",
                        condition.getKey().location(),
                        condition.getValue().getRarityOffset(),
                        foundRarityOffset,
                        condition.getValue().test(x, y, z, unquantizedDepth,
                                sampler, lastSampledDimension,
                                voronoiInfo),
                        lastSampledDimension);*/
                if (foundRarityOffset == condition.getValue().getRarityOffset() && condition.getValue().test(x, y, z, unquantizedDepth, sampler, lastSampledDimension, voronoiInfo)) {
                    //Quantize.LOGGER.debug("Biome matched: {}", condition.getKey());
                    cir.setReturnValue(((BiomeSourceAccessor)this).getResourceKeyMap().get(condition.getKey()));
                }
            }
        }
    }

    @Override
    public void setLastSampledSeed(long seed) {
        lastSampledWorldSeed = seed;
    }

    @Override
    public void setLastSampledDimension(ResourceKey<Level> dimension) {
        lastSampledDimension = dimension;
    }
}
