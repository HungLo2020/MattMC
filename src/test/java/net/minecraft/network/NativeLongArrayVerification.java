package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.util.SimpleBitStorage;

/** Actual fixed/prefixed array callers: buffer checks, metadata/borrowing,
 * FFM, conversion and cursor updates are all timed. */
public final class NativeLongArrayVerification implements AutoCloseable {
    static volatile long sink;
    final boolean nativeMode,read,prefixed;
    final List<Rig> rigs=new ArrayList<>();
    final String name;
    record Rig(ByteBuf buffer,long[] input,long[] output) {}

    NativeLongArrayVerification(boolean mode,String name) {
        // Allocation sampling can randomly wrap only one JVM's fixtures. It is
        // outside this operation; use identical concrete buffers in both modes.
        io.netty.util.ResourceLeakDetector.setLevel(io.netty.util.ResourceLeakDetector.Level.DISABLED);
        nativeMode=mode;this.name=name;
        int end=name.lastIndexOf('_'),direction=name.lastIndexOf('_',end-1);
        int count=Integer.parseInt(name.substring(end+1));read=name.substring(direction+1,end).equals("read");
        String kind=name.substring(0,direction);prefixed=kind.endsWith("_prefixed");if(prefixed)kind=kind.substring(0,kind.length()-9);
        int bits=switch(count){case 256->4;case 342->5;case 456->7;case 512->8;case 586->9;case 1024->16;default->0;};
        for(int fixture=0;fixture<8;fixture++) {
            long[] input;
            if(bits!=0) {
                var random=new SplittableRandom(1977+fixture);var ids=new int[4096];for(int i=0;i<ids.length;i++)ids[i]=random.nextInt(1<<bits);
                input=new SimpleBitStorage(bits,4096,ids).getRaw().clone();if(input.length!=count)throw new AssertionError("Wrong palette payload");
            } else input=LongArrayFixtures.values(count,1977+fixture);
            int capacity=count*8+32;var buffer=LongArrayFixtures.buffer(kind,capacity,capacity);
            buffer.setIndex(3,3);if(prefixed)JavaLongArray.writeLongArray(buffer,input);else JavaLongArray.writeFixedSizeLongArray(buffer,input);
            // Assert the measured buffer type really takes the native route.
            buffer.writerIndex(3);if(!NativeLongArray.write(buffer,input))throw new AssertionError("Non-native benchmark buffer: "+buffer.getClass());
            buffer.setIndex(3,3);if(prefixed)JavaLongArray.writeLongArray(buffer,input);else JavaLongArray.writeFixedSizeLongArray(buffer,input);
            rigs.add(new Rig(new FriendlyByteBuf(buffer),input,new long[count]));
        }
        System.out.println("LONG_ARRAY_FIXTURE case="+name+" count="+count+" fixtures=8 buffer="+rigs.getFirst().buffer.unwrap().getClass().getName()+" palette_bits="+bits);
    }

    long run(int repeats) {
        long sum=0,start=System.nanoTime();
        for(int repeat=0;repeat<repeats;repeat++)for(var rig:rigs) {
            var buffer=rig.buffer;long[] values;
            if(read) {
                buffer.readerIndex(3);
                values=prefixed?(nativeMode?FriendlyByteBuf.readLongArray(buffer):JavaLongArray.readLongArray(buffer)):
                    (nativeMode?FriendlyByteBuf.readFixedSizeLongArray(buffer,rig.output):JavaLongArray.readFixedSizeLongArray(buffer,rig.output));
                sum+=values[0]+values[values.length-1]+buffer.readerIndex();
            } else {
                buffer.writerIndex(3);
                if(prefixed){if(nativeMode)FriendlyByteBuf.writeLongArray(buffer,rig.input);else JavaLongArray.writeLongArray(buffer,rig.input);}
                else {if(nativeMode)FriendlyByteBuf.writeFixedSizeLongArray(buffer,rig.input);else JavaLongArray.writeFixedSizeLongArray(buffer,rig.input);}
                sum+=buffer.getLong(buffer.writerIndex()-8)+buffer.writerIndex();
            }
        }
        sink=sum;return System.nanoTime()-start;
    }

    void validate() {
        for(var rig:rigs) {
            rig.buffer.readerIndex(3);
            var output=prefixed?JavaLongArray.readLongArray(rig.buffer):JavaLongArray.readFixedSizeLongArray(rig.buffer,new long[rig.input.length]);
            if(!Arrays.equals(output,rig.input))throw new AssertionError("Output changed");
            rig.buffer.readerIndex(3);
        }
    }

    static double median(long[] values){values=values.clone();Arrays.sort(values);return values[values.length/2];}
    private static long collections(){long sum=0;for(var gc:ManagementFactory.getGarbageCollectorMXBeans())sum+=Math.max(0,gc.getCollectionCount());return sum;}

    void bench(boolean quick) {
        var jit=ManagementFactory.getCompilationMXBean();int repeats=1;long minimum=quick?25_000_000L:200_000_000L;
        // Same untimed collector preparation in both modes; fixed-array callers
        // can allocate nothing after JIT optimization, so natural GCs may be zero.
        System.gc();System.gc();
        for(int attempt=0;attempt<6;attempt++) {
            long started=System.nanoTime(),initialGc=collections();long[] times=new long[10],compilation=new long[10];boolean stable=false;
            for(int round=0;round<200;round++) {
                long before=jit.getTotalCompilationTime(),elapsed=run(repeats);
                if(elapsed<minimum){repeats*=Math.max(2,(int)Math.ceil((double)minimum/Math.max(1,elapsed)));round=-1;continue;}
                times[round%10]=elapsed;compilation[round%10]=jit.getTotalCompilationTime()-before;
                if(round>=9&&System.nanoTime()-started>(quick?1_000_000_000L:15_000_000_000L)&&Arrays.stream(compilation).sum()==0) {
                    long[] a=new long[5],b=new long[5];for(int k=0;k<5;k++){a[k]=times[(round-k)%10];b[k]=times[(round-k-5)%10];}
                    if(Math.abs(median(a)/median(b)-1)<.05){stable=true;break;}
                }
            }
            if(!stable)throw new AssertionError("Warmup unstable");
            long warmup=System.nanoTime()-started,gc=collections()-initialGc;
            double[] ns=new double[quick?5:11];long[] js=new long[ns.length];boolean accepted=true;
            for(int round=0;round<ns.length;round++) {
                long before=jit.getTotalCompilationTime(),elapsed=run(repeats);js[round]=jit.getTotalCompilationTime()-before;
                if(js[round]!=0){accepted=false;break;}ns[round]=(double)elapsed/repeats/rigs.size();
            }
            if(accepted) {
                validate();run(1);long checksum=sink;validate();
                System.out.println("LONG_ARRAY_BENCH case="+name+" warmup_ns="+warmup+" natural_warmup_gc="+gc+" forced_pre_warm_gc=2 repeats="+repeats+" ns="+Arrays.toString(ns)+" jit="+Arrays.toString(js)+" checksum="+checksum);
                return;
            }
        }
        throw new AssertionError("Late compilation");
    }

    @Override public void close(){for(var rig:rigs)rig.buffer.release();}
    public static void main(String[] args) throws Throwable {
        try(var benchmark=new NativeLongArrayVerification(args[0].equals("native"),args[1])) {
            LongArrayAffinity.pin();benchmark.bench(Arrays.asList(args).contains("quick"));
        }
    }
}
