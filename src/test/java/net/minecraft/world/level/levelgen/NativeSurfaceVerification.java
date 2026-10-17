package net.minecraft.world.level.levelgen;

import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Opt-in surface oracle/benchmark driver. Runs unchanged with original Java
 * SurfaceSystem/SurfaceRules classes extracted from Git, or production Rust. */
public final class NativeSurfaceVerification {
    private static volatile long sink;
    static final int[][] POSITIONS={{0,0},{-2,1},{1,-2},{64,128},{-1024,-2048},{1874998,-1874998}};
    private static void put(MessageDigest d,long value) {for(int i=0;i<8;i++)d.update((byte)(value>>>(8*i)));}
    public static String fingerprint(ChunkAccess chunk) throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");var pos=new BlockPos.MutableBlockPos();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=chunk.getMinY();y<=chunk.getMaxY();y++)
            put(digest,Block.getId(chunk.getBlockState(pos.set(x,y,z))));
        for(var type:Heightmap.Types.values())if(chunk.hasPrimedHeightmap(type)) {
            put(digest,type.ordinal());for(long v:chunk.getOrCreateHeightmapUnprimed(type).getRawData())put(digest,v);
        }
        for(var list:chunk.getPostProcessing()) {put(digest,list==null?-1:list.size());if(list!=null)for(short v:list)put(digest,v);}
        return HexFormat.of().formatHex(digest.digest());
    }
    record Fixture(ProtoChunk chunk, NoiseChunk noise, NoiseBasedChunkGenerator generator, BiomeManager manager,
                           WorldGenerationContext context, RandomState random, Registry<Biome> biomes, NoiseGeneratorSettings settings) {
        void run() {random.surfaceSystem().buildSurface(random,manager,biomes,settings.useLegacyRandomSource(),context,chunk,noise,settings.surfaceRule());}
        void run(SurfaceRules.RuleSource rule) {random.surfaceSystem().buildSurface(random,manager,biomes,settings.useLegacyRandomSource(),context,chunk,noise,rule);}
    }
    static List<SurfaceRules.RuleSource> customRules() {
        var keys = new ArrayList<>(List.of(Biomes.PLAINS));
        var frozenBiome = new SurfaceRules.BiomeConditionSource(keys);
        keys.clear(); // Evaluation must retain the original predicate snapshot.
        return List.of(
            SurfaceRules.ifTrue(frozenBiome,SurfaceRules.state(Blocks.CLAY.defaultBlockState())),
            SurfaceRules.state(Blocks.AIR.defaultBlockState()),
            SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,SurfaceRules.state(Blocks.AIR.defaultBlockState())),
                SurfaceRules.ifTrue(SurfaceRules.steep(),SurfaceRules.state(Blocks.DIRT.defaultBlockState())),SurfaceRules.state(Blocks.GRAVEL.defaultBlockState())),
            SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.ON_CEILING,SurfaceRules.state(Blocks.WATER.defaultBlockState())),
                SurfaceRules.ifTrue(SurfaceRules.waterStartCheck(1,-3),SurfaceRules.state(Blocks.LAVA.defaultBlockState()))),
            SurfaceRules.sequence(new SurfaceRules.SequenceRuleSource(List.of()),SurfaceRules.ifTrue(SurfaceRules.not(SurfaceRules.not(SurfaceRules.UNDER_FLOOR)),SurfaceRules.state(Blocks.SAND.defaultBlockState()))),
            SurfaceRules.bandlands(),
            SurfaceRules.ifTrue(SurfaceRules.verticalGradient("surface_equal_bounds",VerticalAnchor.absolute(10),VerticalAnchor.absolute(10)),SurfaceRules.state(Blocks.BEDROCK.defaultBlockState())),
            SurfaceRules.ifTrue(new SurfaceRules.ConditionSource(){
                public net.minecraft.util.KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec(){throw new UnsupportedOperationException();}
                public SurfaceRules.Condition apply(SurfaceRules.Context c){return ()->{
                    if(c.blockY>c.chunk.getMinY())c.chunk.setBlockState(new BlockPos(c.blockX,c.blockY-1,c.blockZ),Blocks.WATER.defaultBlockState());
                    return c.stoneDepthAbove%3==0;
                };}
            },SurfaceRules.state(Blocks.CLAY.defaultBlockState()))
        );
    }
    static Fixture fixture(RegistryAccess registries, NoiseGeneratorSettings config, RandomState random, long seed, int variant, int[] location, boolean mixed) {
        var biomes=registries.lookupOrThrow(Registries.BIOME);var available=biomes.listElements().sorted(Comparator.comparing(h->h.key().location().toString())).toList();
        var height=LevelHeightAccessor.create(config.noiseSettings().minY(),config.noiseSettings().height());
        var chunk=new ProtoChunk(new ChunkPos(location[0],location[1]),UpgradeData.EMPTY,height,PalettedContainerFactory.create(registries),null);
        int base=height.getMinY(),max=height.getMaxY();var pos=new BlockPos.MutableBlockPos();
        var rng=new Random(seed^variant*719L);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
            int top=Math.min(max,base+Math.min(height.getHeight()-1,50+Math.floorMod(x*13+z*29+variant*31,Math.max(1,height.getHeight()-50))));
            for(int y=base;y<=top;y++) {
                var state=config.defaultBlock();
                if(y>base+7 && Math.floorMod(y+x*3+z*7+variant,43)<7)state=Blocks.CAVE_AIR.defaultBlockState();
                else if(y<top-4 && rng.nextInt(137)==0)state=Blocks.GRANITE.defaultBlockState();
                chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x,y&15,z,state,false);
            }
            for(int y=top+1;y<Math.min(config.seaLevel(),max+1);y++)chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x,y&15,z,config.defaultFluid(),false);
        }
        chunk.setPersistedStatus(ChunkStatus.NOISE);
        Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));
        var fixed=available.get(Math.floorMod(variant,available.size()));
        BiomeManager.NoiseBiomeSource source=(x,y,z)->mixed?available.get(Math.floorMod(x*31+y*17+z*71,available.size())):fixed;
        var manager=new BiomeManager(source,BiomeManager.obfuscateSeed(seed));
        var generator=new NoiseBasedChunkGenerator(new FixedBiomeSource(fixed),Holder.direct(config));
        var noise=new NoiseChunk(16/config.noiseSettings().getCellWidth(),random,chunk.getPos().getMinBlockX(),chunk.getPos().getMinBlockZ(),config.noiseSettings(),
            DensityFunctions.BeardifierMarker.INSTANCE,config,(x,y,z)->new Aquifer.FluidStatus(config.seaLevel(),config.defaultFluid()),Blender.empty());
        return new Fixture(chunk,noise,generator,manager,new WorldGenerationContext(generator,height),random,biomes,config);
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        try(var resources=new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
            List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()))) {
            var layers=net.minecraft.server.RegistryLayer.createRegistryAccess();
            var tags=net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources,layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
            var lookups=net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN),tags);
            var registries=net.minecraft.resources.RegistryDataLoader.load(resources,lookups,net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
            var settings=registries.lookupOrThrow(Registries.NOISE_SETTINGS).listElements().sorted(Comparator.comparing(h->h.key().location().toString())).toList();
            var noises=registries.lookupOrThrow(Registries.NOISE);boolean benchmark=args.length>0&&args[0].equals("benchmark");
            for(var holder:settings) {
                String name=holder.key().location().getPath();if(args.length>1&&!args[1].equals(name))continue;
                var config=holder.value();
                if(!benchmark) {
                    for(long seed:new long[]{0,42,-123456789,Long.MIN_VALUE,Long.MAX_VALUE}) {
                        var random=RandomState.create(config,noises,seed);var digest=MessageDigest.getInstance("SHA-256");
                        int biomeCount=registries.lookupOrThrow(Registries.BIOME).size();
                        for(int i=0;i<biomeCount+POSITIONS.length;i++) {
                            var f=fixture(registries,config,random,seed,i,POSITIONS[i%POSITIONS.length],i>=biomeCount);
                            f.run();digest.update(fingerprint(f.chunk).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                        }
                        System.out.println("SURFACE_PARITY name="+name+" seed="+seed+" chunks="+(biomeCount+POSITIONS.length)+" sha256="+HexFormat.of().formatHex(digest.digest()));
                        digest.reset();int customCount=0;
                        for(var rule:customRules()) {
                            var f=fixture(registries,config,random,seed,customCount,POSITIONS[customCount%POSITIONS.length],true);
                            f.run(rule);digest.update(fingerprint(f.chunk).getBytes(java.nio.charset.StandardCharsets.US_ASCII));customCount++;
                        }
                        System.out.println("SURFACE_PARITY name="+name+"/custom seed="+seed+" chunks="+customCount+" sha256="+HexFormat.of().formatHex(digest.digest()));
                    }
                } else {
                    var random=RandomState.create(config,noises,42);List<Long> times=new ArrayList<>(),jit=new ArrayList<>();
                    int warmups=Integer.getInteger("surface.warmups",12),rounds=Integer.getInteger("surface.rounds",12),count=24;
                    for(int round=-warmups;round<rounds;round++) {
                        var fixtures=new ArrayList<Fixture>();for(int i=0;i<count;i++)fixtures.add(fixture(registries,config,random,42,i,POSITIONS[i%POSITIONS.length],true));
                        long compilation=ManagementFactory.getCompilationMXBean().getTotalCompilationTime(),start=System.nanoTime();
                        for(var f:fixtures)f.run();long elapsed=System.nanoTime()-start;
                        compilation=ManagementFactory.getCompilationMXBean().getTotalCompilationTime()-compilation;
                        for(var f:fixtures)sink+=f.chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG,0,0);
                        if(round>=0){times.add(elapsed);jit.add(compilation);}
                    }
                    System.out.println("SURFACE_BENCH name="+name+" chunks="+count+" samples="+times+" jit_ms="+jit+" sink="+sink);
                }
            }
        }
    }
}
