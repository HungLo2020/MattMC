package net.vulkanic.bridge;

import java.lang.foreign.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.state.PreviousParticleState;
import static net.vulkanic.bridge.VulkanicGalBridge.*;

/** Capture, frame snapshot and ordered encoding; prior Java transport is an internal control. */
public final class VerifyParticleFrames {
    static volatile long blackhole;
    static final ParticleSurface[] SURFACES = ParticleSurface.values();
    static final Field CURRENT_LAYERS=field(QuadParticleRenderState.class,"particles");
    static final Field PREVIOUS_LAYERS=field(PreviousParticleState.class,"particles");
    static Field field(Class<?> type,String name) {
        try {var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
        catch(Exception e){throw new RuntimeException(e);}
    }
    static final class Work implements AutoCloseable {
        final QuadParticleRenderState current=new QuadParticleRenderState();
        final PreviousParticleState previous=new PreviousParticleState();
        final NativeParticleBuffer pending=new NativeParticleBuffer();
        final ArrayList<WorldParticleQuadRecord> records=new ArrayList<>();
        final int count; final boolean scalar;
        final NativeParticleBuffer captures; final Object oldStorage;
        final Field oldFloats,oldInts;
        Work(int count,boolean scalar)throws Exception {
            this.count=count;this.scalar=scalar;
            capture(true);capture(false);
            Object storage=((Map<?,?>)CURRENT_LAYERS.get(current)).values().iterator().next();
            captures=(NativeParticleBuffer)field(storage.getClass(),"nativeValues").get(storage);
            oldStorage=((Map<?,?>)PREVIOUS_LAYERS.get(previous)).values().iterator().next();
            oldFloats=field(oldStorage.getClass(),"floatValues");oldInts=field(oldStorage.getClass(),"intValues");
        }
        void capture(boolean nativeOwner) {
            if(nativeOwner)current.clear();else previous.clear();
            for(int i=0;i<count;i++) {
                if(nativeOwner)current.add(SingleQuadParticle.Layer.OPAQUE,i+.125F,-2,3,.1F,.2F,.3F,1,-.5F,1,0,.25F,.75F,0x80402010,240);
                else previous.add(SingleQuadParticle.Layer.OPAQUE,i+.125F,-2,3,.1F,.2F,.3F,1,-.5F,1,0,.25F,.75F,0x80402010,240);
            }
        }
        long run(boolean nativeOwner,boolean full)throws Exception {
            long result=0;
            for(int frame=0;frame<128;frame++) {
                if(!scalar)capture(nativeOwner);
                try(var arena=Arena.ofConfined()) {
                    if(nativeOwner) {
                        pending.clear();
                        if(scalar)for(int i=0;i<count;i++) {
                            PreviousParticleState.validateParticleQuadSemantics(null,i+.125F,-2,3,.1F,.2F,.3F,1,-.5F,1,0,.25F,.75F);
                            pending.append(17,i%5,0,i+.125F,-2,3,.1F,.2F,.3F,1,-.5F,1,0,.25F,.75F,0x80402010,240);
                        }
                        else pending.appendLayer(captures,17,0,0);
                        try(var snapshot=pending.snapshot()) {result+=consume(snapshot.encode(arena,List.of()),full);}
                    } else {
                        records.clear();
                        float[] f=(float[])oldFloats.get(oldStorage);int[] ints=(int[])oldInts.get(oldStorage);
                        for(int i=0;i<count;i++) {
                            int b=i*12;
                            if(scalar)PreviousParticleState.validateParticleQuadSemantics(null,i+.125F,-2,3,.1F,.2F,.3F,1,-.5F,1,0,.25F,.75F);
                            else PreviousParticleState.validateParticleQuadSemantics(null,f[b],f[b+1],f[b+2],f[b+3],f[b+4],f[b+5],f[b+6],f[b+7],f[b+8],f[b+9],f[b+10],f[b+11]);
                            records.add(scalar?new WorldParticleQuadRecord(17,SURFACES[i%5],0,
                                new float[]{i+.125F,-2,3},new float[]{.1F,.2F,.3F,1},-.5F,new float[]{1,0,.25F,.75F},0x80402010,240)
                                :new WorldParticleQuadRecord(17,false,0,new float[]{f[b],f[b+1],f[b+2]},
                                    new float[]{f[b+3],f[b+4],f[b+5],f[b+6]},f[b+7],new float[]{f[b+8],f[b+9],f[b+10],f[b+11]},ints[i*2],ints[i*2+1]));
                        }
                        var snapshot=List.copyOf(records);
                        result+=consume(encodeParticleQuads(arena,mapParticleMaterialOrder(List.of(),snapshot)),full);
                    }
                }
            }
            return result;
        }
        static long consume(MemorySegment data,boolean full) {
            long result=0;
            if(full)for(long i=0;i<data.byteSize();i+=4)result=31*result+data.get(ValueLayout.JAVA_INT,i);
            else result=data.get(ValueLayout.JAVA_INT,data.byteSize()-8);
            return result;
        }
        public void close(){pending.close();captures.close();}
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();var jit=ManagementFactory.getCompilationMXBean();
        long thread=Thread.currentThread().threadId();boolean current=args[0].equals("current");String[] scenario=args[1].split("-");
        try(var work=new Work(Integer.parseInt(scenario[0]),scenario[1].equals("scalar"))) {
            long a=work.run(true,true),b=work.run(false,true);if(a!=b)throw new AssertionError(a+" != "+b);
            System.out.println("PARTICLE_EQUIVALENCE "+a);
            for(int round=-256;round<8;round++) {
                long compiled=jit.getTotalCompilationTime(),bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                long sum=work.run(current,false),time=System.nanoTime()-start,allocated=bean.getThreadAllocatedBytes(thread)-bytes;
                blackhole=sum;if(round>=0)System.out.println("PARTICLE_TIMING "+time+" "+allocated+" "+sum+" "+(jit.getTotalCompilationTime()-compiled));
            }
        }
        var path=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so");
        System.out.println("NATIVE_IMAGE "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        for(String name:new String[]{"net.vulkanic.bridge.NativeParticleBuffer","net.vulkanic.bridge.VulkanicGalBridge","net.vulkanic.bridge.VerifyParticleFrames","net.minecraft.client.renderer.state.QuadParticleRenderState","net.minecraft.client.renderer.state.PreviousParticleState"}) {
            var type=Class.forName(name,false,VerifyParticleFrames.class.getClassLoader());var resource=type.getResource('/'+name.replace('.','/')+".class");
            try(var in=resource.openStream()){System.out.println("CLASS_IMAGE "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))+" "+resource);}
        }
    }
}
