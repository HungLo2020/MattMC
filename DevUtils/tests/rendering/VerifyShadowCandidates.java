package net.vulkanic.bridge;

import java.lang.foreign.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.minecraft.world.phys.AABB;
import static net.vulkanic.bridge.VulkanicGalBridge.*;

/** Internal transport comparison; actual Frozen gameplay remains the baseline. */
public final class VerifyShadowCandidates {
    static volatile long blackhole;
    // Previous EntityShadowQuery allocation, growth and record-copy sequence.
    static final class Previous implements AutoCloseable {
        Arena arena;
        MemorySegment flags=MemorySegment.NULL,bounds=MemorySegment.NULL,holder=MemorySegment.NULL,admitted=MemorySegment.NULL;
        int capacity,count;
        void grow(int next) {
            var a=Arena.ofConfined();var f=a.allocate(next*4L,8);var b=a.allocate(next*48L,8);
            var h=a.allocate(next*48L,8);var out=a.allocate(next,8);
            if(count>0) {MemorySegment.copy(flags,0,f,0,count*4L);MemorySegment.copy(bounds,0,b,0,count*48L);MemorySegment.copy(holder,0,h,0,count*48L);}
            if(arena!=null)arena.close();arena=a;flags=f;bounds=b;holder=h;admitted=out;capacity=next;
        }
        void add(WorldEntityCullingRecord input) {
            if(count==capacity)grow(Math.max(64,capacity*2));int index=count++;
            flags.setAtIndex(ValueLayout.JAVA_INT,index,input.flags());box(bounds,index,input.bounds());box(holder,index,input.leashHolderBounds());
        }
        static void box(MemorySegment out,int index,WorldAabbRecord b) {
            long offset=index*6L;
            out.setAtIndex(ValueLayout.JAVA_DOUBLE,offset,b==null?0:b.minX());
            out.setAtIndex(ValueLayout.JAVA_DOUBLE,offset+1,b==null?0:b.minY());
            out.setAtIndex(ValueLayout.JAVA_DOUBLE,offset+2,b==null?0:b.minZ());
            out.setAtIndex(ValueLayout.JAVA_DOUBLE,offset+3,b==null?0:b.maxX());
            out.setAtIndex(ValueLayout.JAVA_DOUBLE,offset+4,b==null?0:b.maxY());
            out.setAtIndex(ValueLayout.JAVA_DOUBLE,offset+5,b==null?0:b.maxZ());
        }
        public void close(){if(arena!=null)arena.close();}
    }
    static WorldAabbRecord copy(AABB b) { return new WorldAabbRecord(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ); }
    static MemorySegment data(EntityShadowCandidates c) {
        var h=c.owner();return MemorySegment.ofAddress(h.get(ValueLayout.JAVA_LONG,0)).reinterpret(h.get(ValueLayout.JAVA_INT,16)*104L);
    }
    static final class Work implements AutoCloseable {
        final EntityShadowCandidates current=new EntityShadowCandidates();final Previous previous=new Previous();
        final AABB[] boxes;final boolean leashes;MemorySegment capturedSlots;
        Work(int count,boolean leashes){this.leashes=leashes;boxes=new AABB[count];for(int i=0;i<count;i++)boxes[i]=new AABB(i+.125,-2,-3,i+1.25,4,5);}
        long run(boolean nativeOwner,boolean full) {
            long sum=0;
            for(int frame=0;frame<128;frame++) {
                if(nativeOwner)current.clear();else previous.count=0;
                for(int i=0;i<boxes.length;i++) {
                    AABB b=boxes[i].inflate(.5),h=leashes&&i%3==0?boxes[(i+1)%boxes.length]:null;int flags=h==null?1:17;
                    if(nativeOwner)current.add(flags,b,h);else previous.add(new WorldEntityCullingRecord(flags,copy(b),h==null?null:copy(h)));
                }
                if(nativeOwner && capturedSlots==null)capturedSlots=data(current);
                MemorySegment slots=nativeOwner?capturedSlots:MemorySegment.NULL;
                if(full)for(int i=0;i<boxes.length;i++) {
                    sum=31*sum+(nativeOwner?slots.get(ValueLayout.JAVA_INT,i*104L):previous.flags.get(ValueLayout.JAVA_INT,i*4L));
                    for(int axis=0;axis<12;axis++)sum=31*sum+(nativeOwner?slots.get(ValueLayout.JAVA_LONG,i*104L+8+axis*8L)
                        :(axis<6?previous.bounds:previous.holder).get(ValueLayout.JAVA_LONG,i*48L+(axis%6)*8L));
                } else sum+=nativeOwner?slots.get(ValueLayout.JAVA_LONG,(boxes.length-1)*104L+8):previous.bounds.get(ValueLayout.JAVA_LONG,(boxes.length-1)*48L);
            }
            return sum;
        }
        public void close(){current.close();previous.close();}
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();var jit=ManagementFactory.getCompilationMXBean();
        long thread=Thread.currentThread().threadId();boolean current=args[0].equals("current");String[] scenario=args[1].split("-");
        try(var w=new Work(Integer.parseInt(scenario[0]),scenario[1].equals("leash"))) {
            long a=w.run(true,true),b=w.run(false,true);if(a!=b)throw new AssertionError(a+" != "+b);
            System.out.println("CANDIDATE_EQUIVALENCE "+a);
            for(int round=-64;round<8;round++) {
                long compiled=jit.getTotalCompilationTime(),bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                long sum=w.run(current,false),time=System.nanoTime()-start,allocated=bean.getThreadAllocatedBytes(thread)-bytes;
                blackhole=sum;if(round>=0)System.out.println("CANDIDATE_TIMING "+time+" "+allocated+" "+sum+" "+(jit.getTotalCompilationTime()-compiled));
            }
        }
        var path=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so");
        System.out.println("NATIVE_IMAGE "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        for(String name:new String[]{"net.vulkanic.bridge.EntityShadowCandidates","net.vulkanic.bridge.VulkanicGalBridge","net.vulkanic.bridge.VerifyShadowCandidates"}) {
            var type=Class.forName(name,false,VerifyShadowCandidates.class.getClassLoader());var resource=type.getResource('/'+name.replace('.','/')+".class");
            try(var in=resource.openStream()){System.out.println("CLASS_IMAGE "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))+" "+resource);}
        }
    }
}
