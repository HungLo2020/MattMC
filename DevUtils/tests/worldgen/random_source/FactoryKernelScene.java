package net.minecraft.world.level.levelgen;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.*;
import java.nio.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.*;

/** Real NOISE ore kernel output and actual canonical-factory last-use lifetime. */
public final class FactoryKernelScene {
    static MethodHandle h(String name,FunctionDescriptor type) { return NativeLibraryLoader.downcallHandle("mattmc_rust",name,type); }
    static final MethodHandle WEAK=h("mattmc_positional_weak_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle COUNT=h("mattmc_positional_weak_count",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle DROP_WEAK=h("mattmc_positional_weak_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MethodHandle RELEASE=h("mattmc_noise_fill_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MethodHandle CELL=h("mattmc_noise_fill_cell",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    static final MethodHandle SECTION=h("mattmc_noise_fill_section",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    record Pending(long kernel,long probe,List<WeakReference<?>> facades) {}
    static int id(Block block) { return Block.getId(block.defaultBlockState()); }
    static Object field(Object object,String name)throws Exception { var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object); }
    static Pending prepare(int kind,long lo,long hi,boolean legacy)throws Throwable {
        PositionalRandomFactory facade=kind==1?new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(lo,hi):new LegacyRandomSource.LegacyPositionalRandomFactory(lo);
        NativePositionalFactory actual=kind==1?((XoroshiroRandomSource.XoroshiroPositionalRandomFactory)facade).nativeFactory():((LegacyRandomSource.LegacyPositionalRandomFactory)facade).nativeFactory();
        int[] ints={-64,128,-4,8,4,8,id(Blocks.AIR),id(Blocks.STONE),Mth.ceillog2(Block.BLOCK_STATE_REGISTRY.size()),
            2,-54,-54,id(Blocks.LAVA),63,id(Blocks.WATER),kind,id(Blocks.COPPER_ORE),id(Blocks.RAW_COPPER_BLOCK),id(Blocks.GRANITE),
            id(Blocks.DEEPSLATE_IRON_ORE),id(Blocks.RAW_IRON_BLOCK),id(Blocks.TUFF),0,0,id(Blocks.LAVA)};
        double[] doubles={-1,0,1,1};long kernel;
        try(Arena arena=Arena.ofConfined()) {
            var i=arena.allocateFrom(ValueLayout.JAVA_INT,ints);var d=arena.allocateFrom(ValueLayout.JAVA_DOUBLE,doubles);
            if(legacy) {
                var create=h("mattmc_noise_fill_create",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
                kernel=(long)create.invokeExact(i,arena.allocateFrom(ValueLayout.JAVA_LONG,lo,hi),d);
            } else {
                var create=h("mattmc_noise_fill_create_owned",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
                kernel=(long)create.invokeExact(i,d,actual.handle());
            }
        } finally {Reference.reachabilityFence(actual);}
        if(kernel==0)throw new AssertionError("Actual NOISE kernel rejected scene");
        long probe;
        try {probe=(long)WEAK.invokeExact(actual.handle());}
        catch(Throwable failure){RELEASE.invokeExact(kernel);throw failure;}
        MemorySegment owner=(MemorySegment)field(actual,"owner");
        var refs=List.<WeakReference<?>>of(new WeakReference<>(facade),new WeakReference<>(actual),
            new WeakReference<>(owner),new WeakReference<>(owner.scope()));
        Reference.reachabilityFence(facade);Reference.reachabilityFence(actual);
        return new Pending(kernel,probe,refs);
    }
    static void collect(Pending pending,long expected)throws Throwable {
        long deadline=System.nanoTime()+5_000_000_000L;
        while(true) {
            boolean gone=pending.facades.stream().allMatch(ref->ref.refersTo(null));long count=(long)COUNT.invokeExact(pending.probe);
            if(gone&&count==expected)return;
            if(System.nanoTime()>deadline)throw new AssertionError("Real pending kernel factory ownership: expected="+expected+" actual="+count+" facadesGone="+gone);
            System.gc();Thread.sleep(10);
        }
    }
    static byte[] consume(long kernel)throws Throwable {
        try(Arena arena=Arena.ofConfined()) {
            double[] density=new double[128];Arrays.fill(density,1);
            double[] corners=new double[24];Arrays.fill(corners,0,8,0.6);
            var ds=arena.allocateFrom(ValueLayout.JAVA_DOUBLE,density);var cs=arena.allocateFrom(ValueLayout.JAVA_DOUBLE,corners);
            for(int[] point:new int[][]{{0,16,0},{4,16,4},{8,24,8},{12,24,12}}) {
                int result=(int)CELL.invokeExact(kernel,point[0],point[1],point[2],ds,MemorySegment.NULL,cs,MemorySegment.NULL);
                if(result!=0)throw new AssertionError("Actual ore cell failed: "+result);
            }
            var info=arena.allocate(28,4);var palette=arena.allocate(4096L*4,4);var words=arena.allocate(4096L*8,8);
            int result=(int)SECTION.invokeExact(kernel,5,info,palette,4096,words,4096);
            if(result!=1)throw new AssertionError("Actual ore section missing");
            int[] header=info.toArray(ValueLayout.JAVA_INT);
            int[] states=palette.asSlice(0,header[2]*4L).toArray(ValueLayout.JAVA_INT);
            long[] packed=words.asSlice(0,header[3]*8L).toArray(ValueLayout.JAVA_LONG);
            if(header[4]!=512||Arrays.stream(states).noneMatch(value->value==id(Blocks.COPPER_ORE)||value==id(Blocks.RAW_COPPER_BLOCK)||value==id(Blocks.GRANITE)))
                throw new AssertionError("Scene did not exercise genuine positional ore draws");
            var bytes=ByteBuffer.allocate(28+states.length*4+packed.length*8).order(ByteOrder.LITTLE_ENDIAN);
            for(int value:header)bytes.putInt(value);for(int value:states)bytes.putInt(value);for(long value:packed)bytes.putLong(value);
            return bytes.array();
        }
    }
    public static void main(String[] args)throws Throwable {
        boolean legacy=Arrays.asList(args).contains("--legacy-native");SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        if(!NativeBlockRegistry.ready())throw new AssertionError("Actual native block registry unavailable",NativeBlockRegistry.failure());
        for(long[] input:new long[][]{{1,0,0},{1,123,456},{2,Long.MIN_VALUE,0},{2,123,0}}) {
            Pending pending=prepare((int)input[0],input[1],input[2],legacy);byte[] independent=null;
            try {
                try {collect(pending,legacy?0:1);independent=consume(pending.kernel);}
                finally {RELEASE.invokeExact(pending.kernel);}
                if((long)COUNT.invokeExact(pending.probe)!=0)throw new AssertionError("Actual kernel last release retained canonical factory");
                System.out.println("FACTORY_KERNEL kind="+input[0]+" lo="+input[1]+" hi="+input[2]+" sha256="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(independent)));
            } finally {DROP_WEAK.invokeExact(pending.probe);}
        }
    }
}
