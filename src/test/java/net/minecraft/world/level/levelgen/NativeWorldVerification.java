package net.minecraft.world.level.levelgen;

import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.*;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.function.IntToLongFunction;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Opt-in original-Java/production-Rust comparison. Invoked by VerifyRustWorld.py;
 * never runs as an ordinary unit test and contains no copied noise algorithm. */
public final class NativeWorldVerification {
    public static void main(String[] args) throws Exception {
        if(args.length==0)throw new IllegalArgumentException("fingerprint or benchmark required");
        String[] tail=Arrays.copyOfRange(args,1,args.length);
        switch(args[0]) {
            case "fingerprint" -> NativeWorldFingerprintProbe.main(tail);
            case "benchmark" -> NativeWorldTimingProbe.main(tail);
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}


/** Runs identically against pre-migration Java classes and the production Rust route.
 * Hashes raw density bits, base-terrain block IDs, and aquifer update decisions.
 * This is the NOISE stage, not a claim to exercise structures/carving/decoration.
 */
final class NativeWorldFingerprintProbe {
    private static void hashLong(MessageDigest digest,long value) {
        for(int b=0;b<8;b++)digest.update((byte)(value >>> (8*b)));
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
            net.minecraft.server.packs.PackType.SERVER_DATA,
            java.util.List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()))) {
        var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
        var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources,
            layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
        var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(
            layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
        var lookup = net.minecraft.resources.RegistryDataLoader.load(resources, lookups,
            net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
        var noises=lookup.lookupOrThrow(Registries.NOISE);
        var settings=lookup.lookupOrThrow(Registries.NOISE_SETTINGS).listElements().sorted(java.util.Comparator.comparing(h->h.key().location().toString())).toList();
        long[] seeds={0,42,-123456789,Long.MIN_VALUE,Long.MAX_VALUE};
        int[][] positions={{0,0},{-16,16},{16,-16},{1024,2048},{-16384,-32768},{29999968,-29999968}};
        for(var holder:settings)for(long seed:seeds) {
            var config=holder.value();var random=RandomState.create(config,noises,seed);
            var digest=MessageDigest.getInstance("SHA-256");long blocks=0;
            var router=random.router();
            DensityFunction[] functions={router.barrierNoise(),router.fluidLevelFloodednessNoise(),router.fluidLevelSpreadNoise(),router.lavaNoise(),router.temperature(),router.vegetation(),router.continents(),router.erosion(),router.depth(),router.ridges(),router.preliminarySurfaceLevel(),router.finalDensity(),router.veinToggle(),router.veinRidged(),router.veinGap()};
            for(int[] pos:positions) {
                for(int y=config.noiseSettings().minY();y<config.noiseSettings().minY()+config.noiseSettings().height();y+=7)
                    for(var f:functions)hashLong(digest,Double.doubleToLongBits(f.compute(new DensityFunction.SinglePointContext(pos[0],y,pos[1]))));
                var ns=config.noiseSettings();int cw=ns.getCellWidth(),ch=ns.getCellHeight();
                var chunk=new NoiseChunk(16/cw,random,pos[0],pos[1],ns,DensityFunctions.BeardifierMarker.INSTANCE,config,
                    Boolean.getBoolean("mattmc.test.aquiferPicker") ? new AquiferFluidPicker(config.seaLevel(),config.defaultFluid()) : (x,y,z)->new Aquifer.FluidStatus(config.seaLevel(),config.defaultFluid()),Blender.empty());
                // Exercise cached biome-climate values both inside and outside
                // the flat-cache footprint, before terrain interpolation begins.
                var climate=chunk.cachedClimateSampler(router,config.spawnTarget());
                for(int qx=-1;qx<=5;qx++)for(int qz=-1;qz<=5;qz++)for(int qy:new int[]{ns.minY()/4,0,(ns.minY()+ns.height()-1)/4}) {
                    var target=climate.sample(Math.floorDiv(pos[0],4)+qx,qy,Math.floorDiv(pos[1],4)+qz);
                    hashLong(digest,target.temperature());hashLong(digest,target.humidity());
                    hashLong(digest,target.continentalness());hashLong(digest,target.erosion());
                    hashLong(digest,target.depth());hashLong(digest,target.weirdness());
                }
                chunk.initializeForFirstCellX();
                for(int cx=0;cx<16/cw;cx++) {
                    chunk.advanceCellX(cx);
                    for(int cz=0;cz<16/cw;cz++)for(int cy=ns.height()/ch-1;cy>=0;cy--) {
                        chunk.selectCellYZ(cy,cz);
                        for(int iy=ch-1;iy>=0;iy--) {
                            chunk.updateForY(ns.minY()+cy*ch+iy,(double)iy/ch);
                            for(int ix=0;ix<cw;ix++) {
                                chunk.updateForX(pos[0]+cx*cw+ix,(double)ix/cw);
                                for(int iz=0;iz<cw;iz++) {
                                    chunk.updateForZ(pos[1]+cz*cw+iz,(double)iz/cw);
                                    var block=chunk.getInterpolatedState();
                                    hashLong(digest,Block.getId(block==null?config.defaultBlock():block));
                                    digest.update((byte)(chunk.aquifer().shouldScheduleFluidUpdate()?1:0));blocks++;
                                }
                            }
                        }
                    }
                    chunk.swapSlices();
                }
                chunk.stopInterpolation();
            }
            System.out.println("WORLD "+holder.key().location()+" seed="+seed+" blocks="+blocks+" sha256="+HexFormat.of().formatHex(digest.digest()));
        }
        }
    }
}



/** Identical Java benchmark driver for both implementations; includes chunk setup,
 * interpolation, density evaluation, aquifers, ore decisions, and block selection. */
final class NativeWorldTimingProbe {
    private static volatile long sink;
    private static long generate(NoiseGeneratorSettings config,RandomState random,int[][] positions) {
        long checksum=0;
        for(int[] pos:positions) {
                var ns=config.noiseSettings();int cw=ns.getCellWidth(),ch=ns.getCellHeight();
                var chunk=new NoiseChunk(16/cw,random,pos[0],pos[1],ns,DensityFunctions.BeardifierMarker.INSTANCE,config,
                    Boolean.getBoolean("mattmc.test.aquiferPicker") ? new AquiferFluidPicker(config.seaLevel(),config.defaultFluid()) : (x,y,z)->new Aquifer.FluidStatus(config.seaLevel(),config.defaultFluid()),Blender.empty());
                chunk.initializeForFirstCellX();
                for(int cx=0;cx<16/cw;cx++) {
                    chunk.advanceCellX(cx);
                    for(int cz=0;cz<16/cw;cz++)for(int cy=ns.height()/ch-1;cy>=0;cy--) {
                        chunk.selectCellYZ(cy,cz);
                        for(int iy=ch-1;iy>=0;iy--) {
                            chunk.updateForY(ns.minY()+cy*ch+iy,(double)iy/ch);
                            for(int ix=0;ix<cw;ix++) {
                                chunk.updateForX(pos[0]+cx*cw+ix,(double)ix/cw);
                                for(int iz=0;iz<cw;iz++) {
                                    chunk.updateForZ(pos[1]+cz*cw+iz,(double)iz/cw);
                                    var block=chunk.getInterpolatedState();
                                    checksum = checksum * 31 + Block.getId(block==null?config.defaultBlock():block);
                                    checksum = checksum * 31 + (chunk.aquifer().shouldScheduleFluidUpdate()?1:0);
                                }
                            }
                        }
                    }
                    chunk.swapSlices();
                }
                chunk.stopInterpolation();
        }
        return checksum;
    }
    private static long measure(NoiseGeneratorSettings config,RandomState random,int[][] positions,int repeats) {
        long start=System.nanoTime();
        for(int r=0;r<repeats;r++)sink=generate(config,random,positions);
        return System.nanoTime()-start;
    }
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
            net.minecraft.server.packs.PackType.SERVER_DATA,
            java.util.List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()))) {
        var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
        var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources,
            layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
        var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(
            layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
        var lookup = net.minecraft.resources.RegistryDataLoader.load(resources, lookups,
            net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
        var noises=lookup.lookupOrThrow(Registries.NOISE);
        var settings=lookup.lookupOrThrow(Registries.NOISE_SETTINGS).listElements().sorted(java.util.Comparator.comparing(h->h.key().location().toString())).toList();

        int[][] positions={{0,0},{-16,16},{1024,2048},{-16384,-32768},{29999968,-29999968}};
        for(var holder:settings) {
            if(args.length>0 && !java.util.Set.of(args[0].split(",")).contains(holder.key().location().getPath()))continue;
            var config=holder.value();var random=RandomState.create(config,noises,Long.getLong("mattmc.test.seed",42L));
            // Whole-chunk gains are smaller than leaf-kernel gains. Longer windows
            // include more GC cycles and reduce sensitivity to scheduling delays.
            var result=NativeWorldTimingSupport.run(repeats->measure(config,random,positions,repeats),positions.length,1_000_000_000L,10_000_000_000L,0.08);
            System.out.println("WORLD_BENCH name="+holder.key().location().getPath()+" warmup="+result.warmup()+" attempts="+result.attempts()+" samples="+Arrays.toString(result.samples())+" conservative="+Arrays.toString(result.conservative())+" jit_ms="+Arrays.toString(result.compilationMillis()));
        }
        }
    }
}



/** Shared by original and native drivers. Acceptance never depends on which is faster. */
final class NativeWorldTimingSupport {
    private static double median(long[] values) {long[] copy=values.clone();Arrays.sort(copy);return copy[copy.length/2];}
    record Result(double[] samples,double[] conservative,long[] compilationMillis,int warmup,int attempts,int repeats) {}
    static Result run(IntToLongFunction measure,int operations,long minimumRound,long minimumWarmup,double tolerance) {
        var compilation=ManagementFactory.getCompilationMXBean();
        int repeats=1,totalWarmup=0;
        for(int attempt=1;attempt<=5;attempt++) {
            long start=System.nanoTime();long[] window=new long[5],jit=new long[10];
            int collected=0;boolean stable=false;
            for(int warm=0;warm<400;warm++) {
                long before=compilation.getTotalCompilationTime();
                long elapsed=measure.applyAsLong(repeats);totalWarmup++;
                long compilationMillis=compilation.getTotalCompilationTime()-before;
                if(elapsed<minimumRound) {
                    repeats=Math.multiplyExact(repeats,Math.max(2,(int)Math.ceil((double)minimumRound/Math.max(1,elapsed))));
                    collected=0;continue;
                }
                jit[collected%10]=compilationMillis;window[collected++%5]=elapsed;
                if(collected>=10 && System.nanoTime()-start>=minimumWarmup && Arrays.stream(jit).sum()==0) {
                    long lo=Arrays.stream(window).min().orElseThrow(),hi=Arrays.stream(window).max().orElseThrow();
                    if((hi-lo)/median(window)<tolerance){stable=true;break;}
                }
            }
            if(!stable)throw new AssertionError("Warmup did not stabilize: "+Arrays.toString(window));
            double[] samples=new double[11],conservative=new double[11];long[] compilationMillis=new long[11];
            long totalElapsed=0,totalCompilation=0;boolean significant=false;
            for(int i=0;i<samples.length;i++) {
                long before=compilation.getTotalCompilationTime();
                long elapsed=measure.applyAsLong(repeats);
                compilationMillis[i]=compilation.getTotalCompilationTime()-before;
                // The counter covers the WHOLE JVM, including compilation outside
                // the timer, so subtracting all of it favors the Java baseline.
                // Add 1 ms to every round for the counter's rounding uncertainty.
                long allowance=(compilationMillis[i]+1)*1_000_000L;
                samples[i]=(double)elapsed/repeats/operations;
                conservative[i]=(double)Math.max(1,elapsed-allowance)/repeats/operations;
                totalElapsed+=elapsed;totalCompilation+=compilationMillis[i]*1_000_000L;
                significant|=compilationMillis[i]*1_000_000L>elapsed/10;
            }
            significant|=totalCompilation>totalElapsed/100;
            if(!significant)return new Result(samples,conservative,compilationMillis,totalWarmup,attempt,repeats);
            // Retain the WHOLE invalid window. Never remove individual slow rounds
            // or retry based on elapsed time, ratios, or a failed performance gate.
            System.out.println("WARMUP_CONTINUATION attempt="+attempt+" jit_ms="+totalCompilation/1_000_000+" samples="+Arrays.toString(samples));
        }
        throw new AssertionError("Significant JIT compilation continued in five measurement windows");
    }
}
