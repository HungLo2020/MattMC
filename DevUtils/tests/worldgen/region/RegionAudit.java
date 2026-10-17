package fallen;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import static fallen.FallenAudit.*;

/** Real public placements and actual exposed metadata/storage aliases.
 * All exceptions are recorded, so an inherited pre failure remains evidence. */
public final class RegionAudit {
    static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    static void rawHeight(long[] raw,FallenAudit.Fixture fixture,int value){
        int bits=Mth.ceillog2(fixture.region.getHeight()+1);
        new SimpleBitStorage(bits,256,raw).set(0,value-fixture.region.getMinY());
    }
    static void biomeConsumer(FallenAudit.Fixture f,PlacedFeature feature,WorldgenRandom random)throws Throwable{
        random.setFeatureSeed(999,0,0);
        if(Boolean.getBoolean("fallen.nativeParent")){
            f.prepare(feature);
            var owner=Class.forName("net.minecraft.world.level.levelgen.NativeDecoration");
            var method=owner.getMethod("place",f.programs.getClass(),f.binding.getClass(),ChunkGenerator.class,
                net.minecraft.world.level.WorldGenLevel.class,WorldgenRandom.class,PlacedFeature.class,long.class,int.class,int.class,BlockPos.class);
            try{
                if(!Boolean.TRUE.equals(method.invoke(null,f.programs,f.binding,f.generator,f.region,random,feature,999L,0,0,new BlockPos(0,80,0))))
                    throw new AssertionError("Native biome consumer declined");
            }catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
        }else feature.placeWithBiomeCheck(f.region,f.generator,random,new BlockPos(0,80,0));
    }
    static void one(ServerLevel level,String name)throws Throwable{
        try(var f=new FallenAudit.Fixture(level)){
            for(int z=-16;z<32;z++)for(int x=-16;x<32;x++)f.region.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
            Heightmap.primeHeightmaps(f.centerChunk,EnumSet.allOf(Heightmap.Types.class));
            var surface=f.centerChunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE);
            var wg=f.centerChunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
            long[] raw=surface.getRawData(),rawWg=wg.getRawData();
            var random=new WorldgenRandom(new XoroshiroRandomSource(42));
            var t=new Tape(random);t.fixtureName="parent-region-"+name;
            var observations=new ArrayList<String>();
            boolean lit=name.equals("sky-alias")||name.equals("sky-throw-prefix");
            if(lit)for(var chunk:f.chunks){
                chunk.setLightEngine(level.getChunkSource().getLightEngine());
                chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.INITIALIZE_LIGHT);
                chunk.getSkyLightSources().fillFrom(chunk);
            }
            Object skyStorage=field(f.centerChunk.getSkyLightSources(),"heightmap");
            long[] skyRaw=((BitStorage)skyStorage).getRaw();
            TreeDecorator effect=new Effect(context->{
                observations.add("prefix="+f.region.getBlockState(new BlockPos(8,80,8))+"/height="+surface.getFirstAvailable(8,8));
                switch(name){
                    case "height-alias", "height-throw-prefix" -> {
                        rawHeight(raw,f,199);rawHeight(rawWg,f,177);
                        observations.add("aliased-heights="+surface.getFirstAvailable(0,0)+"/"+wg.getFirstAvailable(0,0));
                    }
                    case "sky-alias", "sky-throw-prefix" -> {
                        int bits=Mth.ceillog2(f.region.getMaxY()+1-(f.region.getMinY()-1)+1);
                        new SimpleBitStorage(bits,256,skyRaw).set(0,31);
                        observations.add("aliased-sky="+skyRaw[0]);
                    }
                    case "section-replacement", "section-replacement-throw" -> {
                        f.centerChunk.getSections()[f.centerChunk.getSectionIndex(80)]=new LevelChunkSection(PalettedContainerFactory.create(level.registryAccess()));
                        observations.add("replaced-section="+f.region.getBlockState(new BlockPos(8,80,8)));
                    }
                    case "palette-growth" -> {
                        int count=0;
                        for(var state:net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY){
                            int at=count++;f.region.setBlock(new BlockPos(at&15,80+(at>>8),(at>>4)&15),state,19);
                            if(count==300)break;
                        }
                        observations.add("palette-states="+count);
                    }
                    case "biome-mutation" -> {
                        var biome=level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.DESERT);
                        @SuppressWarnings("unchecked") var container=(PalettedContainer<Holder<net.minecraft.world.level.biome.Biome>>)f.centerChunk.getSection(f.centerChunk.getSectionIndex(80)).getBiomes();
                        for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)container.set(x,y,z,biome);
                        observations.add("biome="+f.region.getBiome(new BlockPos(8,80,8)).unwrapKey().orElseThrow().location());
                    }
                    case "biome-native-consumer" -> {
                        var placed=level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
                            .getValue(net.minecraft.resources.ResourceLocation.parse("minecraft:patch_grass_plain"));
                        var probe=new WorldgenRandom(new XoroshiroRandomSource(123));
                        try{biomeConsumer(f,placed,probe);}catch(Throwable failure){throw FallenAudit.<RuntimeException>propagate(failure);}
                        observations.add("plain-consumer="+randomState(probe));
                        var desert=level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.DESERT);
                        for(var chunk:f.chunks)for(var section:chunk.getSections()){
                            @SuppressWarnings("unchecked") var container=(PalettedContainer<Holder<net.minecraft.world.level.biome.Biome>>)section.getBiomes();
                            for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)container.set(x,y,z,desert);
                        }
                        try{biomeConsumer(f,placed,probe);}catch(Throwable failure){throw FallenAudit.<RuntimeException>propagate(failure);}
                        observations.add("desert-consumer="+randomState(probe));
                    }
                    case "nested-effects" -> {
                        var inner=FallenAudit.config(BlockStateProvider.simple(Blocks.CHEST),4,List.of(),List.of());
                        var placed=new PlacedFeature(Holder.direct(new ConfiguredFeature<>(Feature.FALLEN_TREE,inner)),List.of());
                        try{
                            if(Boolean.getBoolean("fallen.nativeParent")){f.prepare(placed);f.placeParent(placed,random,new BlockPos(3,80,3));}
                            else placed.place(f.region,f.generator,random,new BlockPos(3,80,3));
                        }catch(Throwable failure){throw FallenAudit.<RuntimeException>propagate(failure);}
                        observations.add("nested-result="+f.region.getBlockState(new BlockPos(3,80,3)));
                    }
                }
                context.random().nextGaussian();
                if(name.endsWith("throw-prefix")||name.equals("section-replacement-throw"))throw t.failure;
            });
            var configuration=FallenAudit.config(BlockStateProvider.simple(name.equals("nested-effects")?Blocks.CHEST:Blocks.OAK_LOG),8,List.of(effect),List.of());
            var leaf=new PlacedFeature(Holder.direct(new ConfiguredFeature<>(Feature.FALLEN_TREE,configuration)),List.of());
            if(Boolean.getBoolean("fallen.nativeParent"))f.prepare(leaf);
            var row=new LinkedHashMap<String,Object>();row.put("name",name);tape=t;
            try{row.put("result",Boolean.getBoolean("fallen.nativeParent")?f.placeParent(leaf,random,t.origin):leaf.place(f.region,f.generator,random,t.origin));}
            catch(Throwable failure){row.put("error",failure.getClass().getName()+":"+failure.getMessage());row.put("sameError",failure==t.failure);}
            finally{tape=null;}
            row.put("observations",observations);row.put("random",randomState(random));row.put("nextRandom",List.of(random.nextLong(),random.nextLong()));
            row.put("sameHeightAlias",raw==surface.getRawData()&&rawWg==wg.getRawData());
            row.put("heightAliasAfter",List.of(surface.getFirstAvailable(0,0),wg.getFirstAvailable(0,0)));
            row.put("skyAfter",Arrays.toString(skyRaw));
            row.put("snapshot",f.snapshot());
            RESULTS.add(row);System.out.println("REGION_FIXTURE "+name+" "+row.getOrDefault("error","ok"));
        }
    }
    public static void run(MinecraftServer server){
        try{
            for(String name:List.of("height-alias","height-throw-prefix","sky-alias","sky-throw-prefix","section-replacement",
                "section-replacement-throw","palette-growth","biome-mutation","biome-native-consumer","nested-effects")){
                if(name.matches(System.getProperty("region.fixtures",".*")))one(server.overworld(),name);
            }
            var gson=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            Files.writeString(OUTPUT.resolve("fixtures.json"),gson.toJson(RESULTS));
            Files.writeString(OUTPUT.resolve("complete.json"),gson.toJson(Map.of("success",true,"fixtures",RESULTS.size())));
        }catch(Throwable failure){failure.printStackTrace();try{Files.writeString(OUTPUT.resolve("failed.txt"),failure.toString());}catch(Exception ignored){}}
        server.halt(false);
    }
}
