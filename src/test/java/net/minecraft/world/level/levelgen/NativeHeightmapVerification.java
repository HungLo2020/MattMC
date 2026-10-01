package net.minecraft.world.level.levelgen;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;

/** Equal real packed-section inputs; times the complete caller and consumes raw output. */
public final class NativeHeightmapVerification {
    private static volatile long sink;
    private static final Field MAPS;
    static {
        try { MAPS = ChunkAccess.class.getDeclaredField("heightmaps"); MAPS.setAccessible(true); }
        catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private final boolean nativeMode, missing;
    private final EnumSet<Heightmap.Types> types;
    private final ProtoChunk[] chunks = new ProtoChunk[16];
    private final Map<?,?>[] maps = new Map<?,?>[16];

    private NativeHeightmapVerification(boolean nativeMode, String pattern, boolean all, boolean missing) throws IllegalAccessException {
        this.nativeMode = nativeMode; this.missing = missing;
        types = all ? EnumSet.allOf(Heightmap.Types.class) : EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG);
        int height = pattern.equals("nether") ? 128 : 384;
        var records = pattern.startsWith("recorded_") ? HeightmapFixtures.saved().stream()
            .filter(record -> record.get("dimension").getAsString().equals(pattern.substring(9))).toList() : List.<com.google.gson.JsonObject>of();
        for (int i = 0; i < chunks.length; i++) {
            chunks[i] = records.isEmpty() ? HeightmapFixtures.chunk(pattern,-64,height,1937L+i*137)
                : HeightmapFixtures.recorded(records.get(i%records.size()));
            maps[i] = (Map<?,?>)MAPS.get(chunks[i]);
            for (var type : types) chunks[i].getOrCreateHeightmapUnprimed(type);
            if (nativeMode && !net.minecraft.world.level.chunk.NativeHeightmap.prime(chunks[i],types))
                throw new AssertionError("Benchmark fixture bypasses Rust: "+pattern);
        }
    }

    private long run(int repeats) {
        long sum = 0, start = System.nanoTime();
        for (int r = 0; r < repeats; r++) for (int i = 0; i < chunks.length; i++) {
            // Same test-only clear each side. Missing-map creation stays INSIDE timing.
            if (missing) maps[i].clear();
            if (nativeMode) Heightmap.primeHeightmaps(chunks[i],types);
            else JavaHeightmapPrimer.primeHeightmaps(chunks[i],types);
            sum += HeightmapFixtures.checksum(chunks[i],types);
        }
        sink = sum; return System.nanoTime()-start;
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
            if(accepted){run(1);System.out.println("HEIGHTMAP_BENCH name="+name+" repeats="+repeats+" ns="+Arrays.toString(ns)+" jit="+Arrays.toString(js)+" checksum="+sink);return;}
        }
        throw new AssertionError("Late compilation: "+name);
    }

    public static void main(String[] args) throws Exception {
        NativeHeightmapTest.bootstrap(); boolean nativeMode=args[0].equals("native");
        for(var pattern:new String[]{"solid","water","leaves","empty","global","recorded_overworld","recorded_nether","recorded_end","recorded_primordial"}) {
            if(args.length>1 && !args[1].equals("all") && !args[1].equals(pattern))continue;
            for(boolean all:new boolean[]{false,true})new NativeHeightmapVerification(nativeMode,pattern,all,false).bench(pattern+(all?"_all":"_worldgen")+"_existing");
            if(pattern.startsWith("recorded_"))new NativeHeightmapVerification(nativeMode,pattern,true,true).bench(pattern+"_all_missing");
        }
    }
}
