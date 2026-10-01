package net.minecraft.world.level.levelgen;

import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Standalone original-Java differential checks and warmed production-call timings. */
public final class NativeSplineVerification {
    private static volatile long sink;
    private record Fixture(NoiseChunk chunk, DensityFunctions.Spline original, DensityFunction nativeFunction, DensityFunction.FunctionContext[] points) {}
    private static boolean lifecycle;
    private static void equal(double a, double b, String where) {
        if (Double.doubleToLongBits(a) != Double.doubleToLongBits(b)) throw new AssertionError(where+": "+Double.toHexString(a)+" != "+Double.toHexString(b));
    }
    private static List<Fixture> fixtures(String name, long seed) throws Exception {
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
                List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()))) {
            var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
            var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
            var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
            var registries = net.minecraft.resources.RegistryDataLoader.load(resources, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
            var key = net.minecraft.resources.ResourceKey.create(Registries.NOISE_SETTINGS, net.minecraft.resources.ResourceLocation.withDefaultNamespace(name));
            var config = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key).value();
            var random = RandomState.create(config, registries.lookupOrThrow(Registries.NOISE), seed);
            var wrapped = NoiseChunk.class.getDeclaredField("wrapped"); wrapped.setAccessible(true);
            var result = new ArrayList<Fixture>();
            for (int[] pos : new int[][]{{0,0},{-32,16},{1024,-2048},{29999968,-29999968}}) {
                var chunk = new NoiseChunk(16/config.noiseSettings().getCellWidth(), random, pos[0], pos[1], config.noiseSettings(),
                    DensityFunctions.BeardifierMarker.INSTANCE, config, (x,y,z)->new Aquifer.FluidStatus(config.seaLevel(),config.defaultFluid()), Blender.empty());
                var sampler = chunk.cachedClimateSampler(random.router(),config.spawnTarget());
                var points = new DensityFunction.FunctionContext[25];
                for (int i=0;i<points.length;i++) points[i]=new DensityFunction.SinglePointContext(pos[0]+(i%5)*4,0,pos[1]+(i/5)*4);
                var unique = Collections.newSetFromMap(new IdentityHashMap<DensityFunctions.Spline,Boolean>());
                for (Object v : ((Map<?,?>)wrapped.get(chunk)).values()) {
                    if (v instanceof DensityFunctions.Spline spline && unique.add(spline)) {
                        DensityFunction optimized = random.optimizeUnary(spline);
                        if (optimized instanceof NativeSpline nativeSpline && nativeSpline.canEvaluate(points[0])) result.add(new Fixture(chunk,spline,optimized,points));
                    }
                }
            }
            if(result.isEmpty()) throw new AssertionError("No production splines: "+name);
            if(Boolean.getBoolean("spline.inspect")) {
                var cf=NativeSpline.class.getDeclaredField("coordinates");cf.setAccessible(true);
                var ff=NativeSpline.class.getDeclaredField("flatCaches");ff.setAccessible(true);
                var pf=NativeSpline.class.getDeclaredField("program");pf.setAccessible(true);
                var sm=NativeSplineProgram.class.getDeclaredField("small");sm.setAccessible(true);
                for(var f:result) {
                    var fs=(DensityFunction[])cf.get(f.nativeFunction);
                    System.out.println("SPLINE_BINDINGS flat="+(ff.get(f.nativeFunction)!=null)+" small="+sm.get(pf.get(f.nativeFunction))+" coordinates="+Arrays.stream(fs).map(x->x.getClass().getSimpleName()).toList());
                }
            }
            System.out.println("SPLINE_FIXTURES name="+name+" seed="+seed+" count="+result.size());
            return result;
        }
    }
    private static long run(List<Fixture> fixtures, boolean nativeMode, int repeats) {
        long sum=0, start=System.nanoTime();
        for (int r=0;r<repeats;r++) for(var f:fixtures) {
            DensityFunction function=nativeMode?f.nativeFunction:f.original;
            if(lifecycle) {
                if(nativeMode) function=NativeSpline.compile((DensityFunctions.Spline)f.original.mapAll(f.chunk.splineWrappingVisitor()));
                else function=f.chunk.wrap(new DensityFunctions.Spline(f.original.spline().mapAll(coordinate->coordinate.mapAll(f.chunk.splineWrappingVisitor()))));
            }
            for(var c:f.points) sum=31*sum+Double.doubleToLongBits(function.compute(c));
        }
        sink=sum;return System.nanoTime()-start;
    }
    private static double median(long[] x) { x=x.clone();Arrays.sort(x);return x[x.length/2]; }
    private static void bench(List<Fixture> fixtures, boolean nativeMode, String name) {
        for(var f:fixtures) {
            var javaMapped=new DensityFunctions.Spline(f.original.spline().mapAll(coordinate->coordinate.mapAll(f.chunk.splineWrappingVisitor())));
            var nativeMapped=(DensityFunctions.Spline)f.original.mapAll(f.chunk.splineWrappingVisitor());
            equal(javaMapped.minValue(),nativeMapped.minValue(),"mapped minimum");
            equal(javaMapped.maxValue(),nativeMapped.maxValue(),"mapped maximum");
            if(!javaMapped.equals(nativeMapped))throw new AssertionError("Canonical mapped graph changed");
            DensityFunction mapped=NativeSpline.compile(nativeMapped);
            if(!(mapped instanceof NativeSpline n))throw new AssertionError("Lifecycle skipped native compilation");
            for(var c:f.points) {
                if(!n.canEvaluate(c))throw new AssertionError("Lifecycle used fallback");
                equal(f.original.compute(c),mapped.compute(c),"lifecycle admission");
                equal(f.original.compute(c),f.nativeFunction.compute(c),"steady admission");
            }
        }
        var jit=ManagementFactory.getCompilationMXBean();int repeats=1;
        for(int attempt=0;attempt<8;attempt++) {
            long start=System.nanoTime();long[] ns=new long[10],js=new long[10];boolean stable=false;
            for(int i=0;i<300;i++) {
                long before=jit.getTotalCompilationTime();long elapsed=run(fixtures,nativeMode,repeats);
                if(elapsed<200_000_000L) {repeats*=Math.max(2,(int)Math.ceil(200_000_000.0/Math.max(1,elapsed)));i=-1;continue;}
                ns[i%10]=elapsed;js[i%10]=jit.getTotalCompilationTime()-before;
                if(i>=9 && System.nanoTime()-start>3_000_000_000L && Arrays.stream(js).sum()==0) {
                    long[] a=new long[5],b=new long[5];for(int k=0;k<5;k++){a[k]=ns[(i-k)%10];b[k]=ns[(i-k-5)%10];}
                    if(Math.abs(median(a)/median(b)-1)<0.05){stable=true;break;}
                }
            }
            if(!stable)throw new AssertionError("Unstable warmup");
            double[] times=new double[11];long[] compilation=new long[11];boolean accept=true;
            for(int i=0;i<11;i++) {
                long before=jit.getTotalCompilationTime();long elapsed=run(fixtures,nativeMode,repeats);compilation[i]=jit.getTotalCompilationTime()-before;
                if(compilation[i]!=0){accept=false;break;}
                times[i]=(double)elapsed/repeats/fixtures.size()/fixtures.getFirst().points.length;
            }
            if(accept){System.out.println("SPLINE_BENCH name="+name+" lifecycle="+lifecycle+" native="+nativeMode+" ns="+Arrays.toString(times)+" jit="+Arrays.toString(compilation)+" checksum="+sink);return;}
        }
        throw new AssertionError("Late compilation");
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        if(args[0].equals("parity")) {
            long count=0;
            for(String name:new String[]{"overworld","large_biomes","amplified"}) for(long seed:new long[]{0,42,-123456789,Long.MIN_VALUE,Long.MAX_VALUE}) {
                for(var f:fixtures(name,seed)) {
                    for(var c:f.points){equal(f.original.compute(c),f.nativeFunction.compute(c),name+"/"+seed);count++;}
                    var first=f.points[0];
                    for(int dx=-1;dx<=17;dx++)for(int dz=-1;dz<=17;dz++) {
                        var c=new DensityFunction.SinglePointContext(first.blockX()+dx,-64+(dx+dz)*7,first.blockZ()+dz);
                        equal(f.original.compute(c),f.nativeFunction.compute(c),"quart boundaries "+name);count++;
                    }
                    var mapped=(DensityFunctions.Spline)f.original.mapAll(f.chunk.splineWrappingVisitor());
                    var expectedMapped=new DensityFunctions.Spline(f.original.spline().mapAll(coordinate->coordinate.mapAll(f.chunk.splineWrappingVisitor())));
                    if(!mapped.equals(expectedMapped))throw new AssertionError("Mapped graph parity");
                    // Cache misses preserve original evaluator/callback behavior.
                    for(var c:List.of(new DensityFunction.SinglePointContext(Integer.MIN_VALUE,0,Integer.MAX_VALUE),new DensityFunction.SinglePointContext(999999,0,-999999)))
                        equal(f.original.compute(c),f.nativeFunction.compute(c),"outside flat cache");
                }
            }
            System.out.println("SPLINE_PARITY decisions="+count+" exact=true");
        } else {
            String name=args.length>1?args[1]:"overworld";
            if(name.equals("all")) {
                for(String settings:new String[]{"overworld","large_biomes","amplified"}) {
                    var fs=fixtures(settings,42);
                    for(boolean cycle:new boolean[]{false,true}) {lifecycle=cycle;bench(fs,args[0].equals("native"),settings);}
                }
            } else {
                lifecycle=args.length>2&&args[2].equals("lifecycle");bench(fixtures(name,42),args[0].equals("native"),name);
            }
        }
    }
}
