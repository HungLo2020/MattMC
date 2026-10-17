package net.minecraft.world.level.levelgen.carver;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;

final class CarverFixtures {
    record Config(String name,String type,CarverConfiguration value) {}
    static final Holder<Biome> BIOME=Holder.direct(new Biome.BiomeBuilder().hasPrecipitation(false).temperature(.5F).downfall(.5F)
        .specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
        .mobSpawnSettings(new MobSpawnSettings.Builder().build()).generationSettings(BiomeGenerationSettings.EMPTY).build());
    static CarvingContext context(int minY,int height) {
        var settings=new NoiseGeneratorSettings(NoiseSettings.create(minY,height,1,2),Blocks.STONE.defaultBlockState(),Blocks.WATER.defaultBlockState(),
            null,null,List.of(),63,false,true,true,false);
        var generator=new NoiseBasedChunkGenerator(new FixedBiomeSource(BIOME),Holder.direct(settings));
        return new CarvingContext(generator,RegistryAccess.EMPTY,LevelHeightAccessor.create(minY,height),null,null,null);
    }
    static List<Config> configs() {
        var result=new ArrayList<Config>();
        var replaceable=HolderSet.direct(Blocks.STONE.builtInRegistryHolder(),Blocks.DIRT.builtInRegistryHolder(),Blocks.NETHERRACK.builtInRegistryHolder());
        try(var paths=Files.list(Path.of("src/main/resources/data/minecraft/worldgen/configured_carver"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".json")).sorted().toList()) {
                var obj=JsonParser.parseString(Files.readString(path)).getAsJsonObject();var json=obj.getAsJsonObject("config");
                String type=obj.get("type").getAsString();
                var base=new CarverConfiguration(json.get("probability").getAsFloat(),HeightProvider.CODEC.parse(JsonOps.INSTANCE,json.get("y")).getOrThrow(),
                    FloatProvider.CODEC.parse(JsonOps.INSTANCE,json.get("yScale")).getOrThrow(),VerticalAnchor.CODEC.parse(JsonOps.INSTANCE,json.get("lava_level")).getOrThrow(),
                    json.has("debug_settings")?CarverDebugSettings.CODEC.parse(JsonOps.INSTANCE,json.get("debug_settings")).getOrThrow():CarverDebugSettings.DEFAULT,replaceable);
                CarverConfiguration config=type.equals("minecraft:canyon")?
                    new CanyonCarverConfiguration(base,FloatProvider.CODEC.parse(JsonOps.INSTANCE,json.get("vertical_rotation")).getOrThrow(),
                        CanyonCarverConfiguration.CanyonShapeConfiguration.CODEC.parse(JsonOps.INSTANCE,json.get("shape")).getOrThrow()):
                    new CaveCarverConfiguration(base,FloatProvider.CODEC.parse(JsonOps.INSTANCE,json.get("horizontal_radius_multiplier")).getOrThrow(),
                        FloatProvider.CODEC.parse(JsonOps.INSTANCE,json.get("vertical_radius_multiplier")).getOrThrow(),FloatProvider.CODEC.parse(JsonOps.INSTANCE,json.get("floor_level")).getOrThrow());
                result.add(new Config(path.getFileName().toString(),type,config));
            }
        }catch(Exception e){throw new IllegalStateException(e);}return result;
    }
    static CarverConfiguration debug(CarverConfiguration c) {
        var debug=CarverDebugSettings.of(true,Blocks.GLASS.defaultBlockState());
        if(c instanceof CaveCarverConfiguration cave)return new CaveCarverConfiguration(c.probability,c.y,c.yScale,c.lavaLevel,debug,c.replaceable,
            cave.horizontalRadiusMultiplier,cave.verticalRadiusMultiplier,cave.floorLevel);
        var canyon=(CanyonCarverConfiguration)c;return new CanyonCarverConfiguration(c.probability,c.y,c.yScale,c.lavaLevel,debug,c.replaceable,canyon.verticalRotation,canyon.shape);
    }
}
