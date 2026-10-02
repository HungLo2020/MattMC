package net.minecraft.world.level.levelgen.feature;

import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.*;

/** Equivalent numeric fields including native packing, FFM, copies and traversal.
 * Existing native NormalNoise is shared by both implementations. */
public final class NativeGeodeGeometryVerification {
    static volatile long sink;
    final List<GeodeFixtures.Grid> fixtures=new ArrayList<>();
    final boolean nativeMode;
    final Checksum consumer=new Checksum();
    static final class Checksum implements JavaGeodeFields.Consumer {
        long value;
        public void accept(BlockPos pos,double body,double crack) {
            value=value*31+Double.doubleToRawLongBits(body);
            value=value*31+Double.doubleToRawLongBits(crack);
        }
    }
    NativeGeodeGeometryVerification(boolean mode,String name) {
        nativeMode=mode;var configs=GeodeFixtures.configs();
        String configName=(name.startsWith("amethyst")||name.startsWith("small")||name.startsWith("large"))?"amethyst_geode":name.startsWith("ore")?"iron":"coral_brain";
        var config=configs.stream().filter(c->c.name().equals(configName)).findFirst().orElseThrow().value();
        if(name.equals("small_1")||name.equals("large_20")) {
            boolean small=name.equals("small_1");
            config=new net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration(config.geodeBlockSettings,
                config.geodeLayerSettings,config.geodeCrackSettings,config.usePotentialPlacementsChance,config.useAlternateLayer0Chance,
                config.placementsRequireLayer0Alternate,config.outerWallDistance,net.minecraft.util.valueproviders.ConstantInt.of(small?1:20),
                config.pointOffset,small?0:-19,small?7:20,config.noiseMultiplier,config.invalidBlocksThreshold);
        }
        Boolean crack=name.equals("large_20")||name.endsWith("crack")&&!name.endsWith("no_crack");
        for(int i=0;i<32;i++) {
            var pos=new BlockPos((i%4-2)*4096,32+(i%8)*16,(i/4-2)*8192);
            fixtures.add(GeodeFixtures.grid(config,0x9e3779b97f4a7c15L*i,new WorldgenRandom(new XoroshiroRandomSource(i+37)),pos,crack));
        }
    }
    long run(int repeats) {
        long start=System.nanoTime(),sum=0;
        for(int r=0;r<repeats;r++)for(var grid:fixtures) {
            consumer.value=0;
            if(nativeMode) {
                try(var fields=NativeGeodeGeometry.prepare(grid.noise(),grid.origin(),grid.min(),grid.max(),grid.points(),grid.cracks(),grid.crackOffset(),grid.multiplier())) {
                    if(fields==null)throw new AssertionError("Benchmark must exercise Rust");int index=0;
                    for(var p:BlockPos.betweenClosed(grid.origin().offset(grid.min(),grid.min(),grid.min()),grid.origin().offset(grid.max(),grid.max(),grid.max()))) {
                        consumer.accept(p,fields.body(index),fields.crack(index));index++;
                    }
                }
            } else JavaGeodeFields.evaluate(grid,consumer);
            sum+=consumer.value;
        }
        sink=sum;return System.nanoTime()-start;
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
                if(round>=9 && System.nanoTime()-started>(quick?500_000_000L:3_000_000_000L) && Arrays.stream(compilation).sum()==0) {
                    long[] a=new long[5],b=new long[5];for(int k=0;k<5;k++){a[k]=times[(round-k)%10];b[k]=times[(round-k-5)%10];}
                    if(Math.abs(median(a)/median(b)-1)<.05){stable=true;break;}
                }
            }
            if(!stable)throw new AssertionError("Warmup unstable");
            double[] ns=new double[quick?5:11];long[] js=new long[ns.length];boolean accepted=true;
            for(int round=0;round<ns.length;round++) {
                long before=jit.getTotalCompilationTime(),elapsed=run(repeats);js[round]=jit.getTotalCompilationTime()-before;
                if(js[round]!=0){accepted=false;break;}ns[round]=(double)elapsed/repeats/fixtures.size();
            }
            if(accepted){run(1);System.out.println("GEODE_BENCH case="+name+" repeats="+repeats+" ns="+Arrays.toString(ns)+" jit="+Arrays.toString(js)+" checksum="+sink);return;}
        }
        throw new AssertionError("Late compilation");
    }
    public static void main(String[] args) {
        NativeGeodeGeometryTest.bootstrap();boolean nativeMode=args[0].equals("native"),quick=Arrays.asList(args).contains("quick");
        String[] names=args.length>1&&!args[1].equals("all")?new String[]{args[1]}:
            new String[]{"amethyst_crack","amethyst_no_crack","ore_crack","ore_no_crack","coral_no_crack","small_1","large_20"};
        for(var name:names)new NativeGeodeGeometryVerification(nativeMode,name).bench(name,quick);
    }
}
