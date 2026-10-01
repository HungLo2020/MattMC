package net.minecraft.world.level.lighting;

import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;

/** Equal real packed-section inputs; times the complete caller and consumes raw output. */
public final class NativeSkyLightSourcesVerification {
    private static volatile long sink;
    private final boolean nativeMode;
    private final ProtoChunk[] chunks=new ProtoChunk[16];
    private final ChunkSkyLightSources[] sources=new ChunkSkyLightSources[16];
    private final long[][] raw=new long[16][];
    private NativeSkyLightSourcesVerification(boolean nativeMode,String pattern) {
        this.nativeMode=nativeMode;
        var records=pattern.startsWith("recorded_")?SkyLightSourcesFixtures.records().stream()
            .filter(r->r.get("dimension").getAsString().equals(pattern.substring(9))).toList():List.<com.google.gson.JsonObject>of();
        for(int i=0;i<chunks.length;i++) {
            chunks[i]=records.isEmpty()?SkyLightSourcesFixtures.chunk(pattern,-64,384,1977+i*137):SkyLightSourcesFixtures.recorded(records.get(i%records.size()));
            sources[i]=new ChunkSkyLightSources(chunks[i]);raw[i]=SkyLightSourcesFixtures.storage(sources[i]).getRaw();
            if(nativeMode && !net.minecraft.world.level.chunk.NativeSkyLightSources.fill(chunks[i],chunks[i].getMinY()-1,SkyLightSourcesFixtures.storage(sources[i])))
                throw new AssertionError("Benchmark fixture bypasses Rust: "+pattern);
        }
    }
    private long run(int repeats) {
        long sum=0,start=System.nanoTime();
        for(int r=0;r<repeats;r++)for(int i=0;i<chunks.length;i++) {
            if(nativeMode)sources[i].fillFrom(chunks[i]);else JavaSkyLightSources.fill(sources[i],chunks[i]);
            // Consume the exact packed result with equal work on each side.
            for(long value:raw[i])sum=sum*31+value;
        }
        sink=sum;return System.nanoTime()-start;
    }

    private static double median(long[] a) { a=a.clone();Arrays.sort(a);return a[a.length/2]; }
    private void bench(String name) {
        var jit = ManagementFactory.getCompilationMXBean(); int repeats = 1;
        for (int attempt = 0; attempt < 6; attempt++) {
            long started = System.nanoTime(); long[] recent = new long[10], compilation = new long[10]; boolean stable = false;
            for (int round = 0; round < 300; round++) {
                long before=jit.getTotalCompilationTime(),elapsed=run(repeats);
                if (elapsed < 120_000_000) { repeats *= Math.max(2,(int)Math.ceil(120_000_000.0/Math.max(elapsed,1)));round=-1;continue; }
                recent[round%10]=elapsed;compilation[round%10]=jit.getTotalCompilationTime()-before;
                if (round>=9 && System.nanoTime()-started>3_000_000_000L && Arrays.stream(compilation).sum()==0) {
                    long[] a=new long[5],b=new long[5];
                    for(int i=0;i<5;i++){a[i]=recent[(round-i)%10];b[i]=recent[(round-i-5)%10];}
                    if(Math.abs(median(a)/median(b)-1)<.05){stable=true;break;}
                }
            }
            if(!stable)throw new AssertionError("Unstable warmup: "+name);
            double[] ns=new double[11];long[] js=new long[11];boolean accepted=true;
            for(int round=0;round<11;round++){
                long before=jit.getTotalCompilationTime(),elapsed=run(repeats);js[round]=jit.getTotalCompilationTime()-before;
                if(js[round]!=0){accepted=false;break;}ns[round]=(double)elapsed/repeats/chunks.length;
            }
            if(accepted){run(1);System.out.println("SKYLIGHT_BENCH name="+name+" repeats="+repeats+" ns="+Arrays.toString(ns)+" jit="+Arrays.toString(js)+" checksum="+sink);return;}
        }
        throw new AssertionError("Late compilation: "+name);
    }

    public static void main(String[] args) throws Exception {
        NativeSkyLightSourcesTest.bootstrap(); boolean nativeMode=args[0].equals("native");
        for(var pattern:new String[]{"solid","water","leaves","glass","empty","shapes","global","islands","recorded_overworld","recorded_nether","recorded_end","recorded_primordial"}) {
            if(args.length>1 && !args[1].equals("all") && !args[1].equals(pattern))continue;
            new NativeSkyLightSourcesVerification(nativeMode,pattern).bench(pattern);
        }
    }
}
