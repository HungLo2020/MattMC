package net.minecraft.world.level.levelgen.feature;

import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

/** Times the original and migrated complete place() calls, including random
 * sources, geometry, FFM marshalling, block rules, palette writes and cleanup. */
public final class NativeOreGeometryVerification {
    static volatile long sink;
    final OreTestWorld world;
    final Feature<OreConfiguration> feature;
    final OreConfiguration config;
    final boolean nativeMode;
    final boolean geometryOnly;
    final List<FeaturePlaceContext<OreConfiguration>> fixtures=new ArrayList<>();
    NativeOreGeometryVerification(boolean nativeMode,int size,boolean mixed,boolean geometryOnly){
        this.nativeMode=nativeMode;this.geometryOnly=geometryOnly;
        world=new OreTestWorld(-64,128,mixed,false);
        feature=nativeMode?new OreFeature(OreConfiguration.CODEC):new JavaOreFeature(OreConfiguration.CODEC);
        config=NativeOreGeometryTest.configuration(size,mixed?.5F:0F,true);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.chunk(x,z);
        for(int i=0;i<128;i++)fixtures.add(new FeaturePlaceContext<>(Optional.empty(),world.level,null,new WorldgenRandom(new XoroshiroRandomSource(i)),new BlockPos(i%16,-32+i%48,(i*7)%16),config));
    }
    long run(int repeats){
        long sum=0,start=System.nanoTime();
        world.checks=0;
        for(int r=0;r<repeats;r++)for(int i=0;i<fixtures.size();i++){
            var fixture=fixtures.get(i);fixture.random().setSeed(i*0x9e3779b97f4a7c15L+r%32);
            if(geometryOnly) {
                var random=fixture.random();var pos=fixture.origin();int size=config.size;
                float angle=random.nextFloat()*(float)Math.PI,g=size/8.0F;
                double x0=pos.getX()+Math.sin(angle)*g,x1=pos.getX()-Math.sin(angle)*g,z0=pos.getZ()+Math.cos(angle)*g,z1=pos.getZ()-Math.cos(angle)*g;
                double y0=pos.getY()+random.nextInt(3)-2,y1=pos.getY()+random.nextInt(3)-2;
                int padding=net.minecraft.util.Mth.ceil((size/16.0F*2.0F+1.0F)/2.0F);
                int minX=pos.getX()-net.minecraft.util.Mth.ceil(g)-padding,minY=pos.getY()-2-padding,minZ=pos.getZ()-net.minecraft.util.Mth.ceil(g)-padding;
                if(nativeMode) {
                    try(var geometry=NativeOreGeometry.vein(random,size,x0,x1,y0,y1,z0,z1,minX,minY,minZ)) {
                    var spans=geometry.values;
                    long checksum=0;
                    for(int at=0;at<geometry.length;at+=4)for(int z=spans[at+2];z<=spans[at+3];z++) {
                        checksum=checksum*31+spans[at];checksum=checksum*31+spans[at+1];checksum=checksum*31+z;
                    }
                    sum+=checksum;
                    }
                } else sum+=JavaOreKernel.evaluate(random,size,x0,x1,z0,z1,y0,y1,minX,minY,minZ);
            } else sum+=feature.place(fixture)?1:0;
        }
        sink=sum+world.checks;return System.nanoTime()-start;
    }
    static double median(long[] values){values=values.clone();Arrays.sort(values);return values[values.length/2];}
    void bench(int size,boolean mixed,boolean quick){
        var jit=ManagementFactory.getCompilationMXBean();int repeats=32;
        long minimum=quick?30_000_000L:150_000_000L;
        for(int attempt=0;attempt<6;attempt++){
            long started=System.nanoTime();long[] times=new long[10],compilation=new long[10];boolean stable=false;
            for(int round=0;round<300;round++){
                long before=jit.getTotalCompilationTime(),elapsed=run(repeats);
                if(elapsed<minimum){repeats=32*((repeats*Math.max(2,(int)Math.ceil((double)minimum/Math.max(1,elapsed)))+31)/32);round=-1;continue;}
                times[round%10]=elapsed;compilation[round%10]=jit.getTotalCompilationTime()-before;
                if(round>=9 && System.nanoTime()-started>(quick?500_000_000L:3_000_000_000L) && Arrays.stream(compilation).sum()==0){
                    long[] a=new long[5],b=new long[5];for(int k=0;k<5;k++){a[k]=times[(round-k)%10];b[k]=times[(round-k-5)%10];}
                    if(Math.abs(median(a)/median(b)-1)<.05){stable=true;break;}
                }
            }
            if(!stable)throw new AssertionError("Warmup unstable");
            double[] ns=new double[quick?5:11];long[] js=new long[ns.length];boolean accepted=true;
            for(int round=0;round<ns.length;round++){
                long before=jit.getTotalCompilationTime(),elapsed=run(repeats);js[round]=jit.getTotalCompilationTime()-before;
                if(js[round]!=0){accepted=false;break;}ns[round]=(double)elapsed/repeats/fixtures.size();
            }
            if(accepted){run(32);System.out.println("ORE_BENCH scope="+(geometryOnly?"geometry":"placement")+" size="+size+" mixed="+mixed+" repeats="+repeats+" ns="+Arrays.toString(ns)+" jit="+Arrays.toString(js)+" checksum="+sink);return;}
        }
        throw new AssertionError("Late compilation");
    }
    public static void main(String[] args){
        NativeOreGeometryTest.bootstrap();boolean nativeMode=args[0].equals("native"),quick=Arrays.asList(args).contains("quick");
        int[] sizes=args.length>1&&!args[1].equals("all")?new int[]{Integer.parseInt(args[1])}:new int[]{3,4,7,8,9,10,12,14,17,20,33,64};
        boolean geometryOnly=Arrays.asList(args).contains("geometry");
        for(int size:sizes)for(boolean mixed:geometryOnly?new boolean[]{false}:new boolean[]{false,true})new NativeOreGeometryVerification(nativeMode,size,mixed,geometryOnly).bench(size,mixed,quick);
    }
}
