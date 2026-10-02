package net.minecraft.world.level.levelgen.carver;

import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarvingMask;

/** Full ellipsoid caller: bounds, native packing/copies, live mask probes and
 * ordered coordinate/column-flag consumption. No world-write speedup claim. */
public final class NativeCanyonGeometryVerification {
    static volatile long sink;
    final boolean nativeMode,overlap;
    final CarverKernel kernel=new CarverKernel();
    final List<Rig> rigs=new ArrayList<>();
    long probes,probeChecksum;
    record Rig(CarverGeometryFixtures.Ellipsoid e,CarverTestChunk chunk,CarvingMask mask,BitSet bits,
        WorldCarver.CarveSkipChecker nativeChecker,JavaWorldCarver.CarveSkipChecker originalChecker) {}
    NativeCanyonGeometryVerification(boolean mode,String name) {
        nativeMode=mode;overlap=name.endsWith("_overlap");var configs=CarverFixtures.configs();String key=name.replace("_overlap","");
        List<CarverGeometryFixtures.Ellipsoid> inputs;
        if(key.startsWith("minimum")||key.startsWith("short")||key.equals("tall")) {
            var config=configs.stream().filter(c->c.name().equals("canyon.json")).findFirst().orElseThrow();
            var context=CarverFixtures.context(-64,key.equals("tall")?2048:384);inputs=new ArrayList<>();
            var widths=new float[context.getGenDepth()];for(int y=0;y<widths.length;y++)widths[y]=key.endsWith("gaps")&&y%3==0?12F:1F;
            for(int i=0;i<128;i++){var pos=new ChunkPos(i%16-8,i/16-4);inputs.add(new CarverGeometryFixtures.Ellipsoid(context,config.value(),pos,
                pos.getMinBlockX()+7.5,key.equals("tall")?512:-32,pos.getMinBlockZ()+7.5,key.equals("tall")?16:3,key.equals("tall")?128:key.startsWith("short")?7:15,2,0,widths));}
        } else inputs=CarverGeometryFixtures.captured(configs.stream().filter(c->c.name().equals(key+".json")).findFirst().orElseThrow(),128);
        int eligible=0;long decisions=0;
        try {
            var field=CarvingMask.class.getDeclaredField("mask");field.setAccessible(true);
            for(var e:inputs) {
                var chunk=new CarverTestChunk(e.chunk(),e.context().getMinGenY(),e.context().getGenDepth(),false);chunk.recording=false;
                var mask=new CarvingMask(e.context().getGenDepth(),e.context().getMinGenY());var bits=(BitSet)field.get(mask);
                if(overlap)bits.set(0,256*e.context().getGenDepth());
                mask.setAdditionalMask((x,y,z)->{probes++;probeChecksum=probeChecksum*31+x;probeChecksum=probeChecksum*31+y;probeChecksum=probeChecksum*31+z;return false;});
                rigs.add(new Rig(e,chunk,mask,bits,e.nativeChecker(),e.originalChecker()));
                if(e.volume()>=NativeCanyonGeometry.MIN_VOLUME&&e.frame()[5]-e.frame()[4]>=NativeCanyonGeometry.MIN_HEIGHT)eligible++;decisions+=e.volume();
            }
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        System.out.println("CARVER_FIXTURE case="+name+" ellipsoids="+rigs.size()+" native_eligible="+eligible+" decisions="+decisions);
    }
    long run(int repeats) {
        long start=System.nanoTime();kernel.checksum=kernel.calls=probes=probeChecksum=0;long result=0;
        for(int r=0;r<repeats;r++)for(var rig:rigs) {
            if(!overlap)rig.bits.clear();
            result+=kernel.evaluate(nativeMode,rig.e,rig.chunk,rig.mask,rig.nativeChecker,rig.originalChecker)?1:0;
        }
        sink=kernel.checksum+kernel.calls+probeChecksum+probes+result;return System.nanoTime()-start;
    }
    static double median(long[] values){values=values.clone();Arrays.sort(values);return values[values.length/2];}
    void bench(String name,boolean quick) {
        var jit=ManagementFactory.getCompilationMXBean();int repeats=1;
        long minimum=quick?25_000_000L:120_000_000L;
        for(int attempt=0;attempt<6;attempt++) {
            long started=System.nanoTime();long[] times=new long[10],compilation=new long[10];boolean stable=false;
            for(int round=0;round<200;round++) {
                long before=jit.getTotalCompilationTime(),elapsed=run(repeats);
                if(elapsed<minimum){repeats*=Math.max(2,(int)Math.ceil((double)minimum/Math.max(1,elapsed)));round=-1;continue;}
                times[round%10]=elapsed;compilation[round%10]=jit.getTotalCompilationTime()-before;
                if(round>=9 && System.nanoTime()-started>(quick?500_000_000L:6_000_000_000L) && Arrays.stream(compilation).sum()==0) {
                    long[] a=new long[5],b=new long[5];for(int k=0;k<5;k++){a[k]=times[(round-k)%10];b[k]=times[(round-k-5)%10];}
                    if(Math.abs(median(a)/median(b)-1)<.05){stable=true;break;}
                }
            }
            if(!stable)throw new AssertionError("Warmup unstable");
            double[] ns=new double[quick?5:11];long[] js=new long[ns.length];boolean accepted=true;
            for(int round=0;round<ns.length;round++) {
                long before=jit.getTotalCompilationTime(),elapsed=run(repeats);js[round]=jit.getTotalCompilationTime()-before;
                if(js[round]!=0){accepted=false;break;}ns[round]=(double)elapsed/repeats/rigs.size();
            }
            if(accepted){run(1);System.out.println("CARVER_BENCH case="+name+" repeats="+repeats+" ns="+Arrays.toString(ns)+" jit="+Arrays.toString(js)+" checksum="+sink+" probes="+probes+" carved="+kernel.calls);return;}
        }
        throw new AssertionError("Late compilation");
    }
    public static void main(String[] args){NativeCanyonGeometryTest.bootstrap();new NativeCanyonGeometryVerification(args[0].equals("native"),args[1]).bench(args[1],Arrays.asList(args).contains("quick"));}
}
