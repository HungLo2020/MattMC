package net.vulkanic.world;

import java.lang.foreign.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.joml.Matrix4f;
import static net.vulkanic.bridge.VulkanicGalBridge.*;

/** Supplemental producer/encoding comparison. Frozen gameplay remains the acceptance baseline. */
public final class VerifyModelFrames {
    static final int MODELS=16, NODES=12, FRAMES=256;
    static volatile long blackhole;
    static final class Work implements AutoCloseable {
        final Arena arena=Arena.ofConfined();
        final MemorySegment instances=Struct.WORLD_MESH_INSTANCE_RECORD.array(arena,MODELS);
        final MemorySegment poseStaging=Struct.MODEL_RIG_POSE.array(arena,MODELS*NODES);
        final ModelPart[] nodes=new ModelPart[NODES];
        final float[][] values=new float[3][MODELS*NODES*9];
        final int[][] flags=new int[3][MODELS*NODES];
        final Matrix4f matrix=new Matrix4f();
        final StandardItemFoilRecord foil=new StandardItemFoilRecord(123456L,.5,.75f,StandardFoilKind.ARMOR);
        Work() { for(int i=0;i<NODES;i++) {nodes[i]=new ModelPart(List.of(),Map.of());nodes[i].x=i;nodes[i].yRot=i*.1f;} }
        long run(boolean nativeFrames, boolean variants, boolean fullReadback) {
            long sum=0;
            for(int frame=0;frame<FRAMES;frame++) {
                NativeModelFrame owner=nativeFrames?new NativeModelFrame():null;
                try {
                    float[] v=values[frame%3];int[] f=flags[frame%3];
                    for(int model=0;model<MODELS;model++) {
                        matrix.identity().translate(model+.125f,frame*.25f,-model);
                        WorldMeshInstance instance;
                        if(nativeFrames) instance=owner.append(nodes,model+1,matrix,1,2,-1,0,0,0xf000f0,1280,720,0,variants?foil:null);
                        else {
                            // Previous producer: reusable Java pose ring, copied root matrix,
                            // constructor defensive copy, light copy and optional foil copy.
                            for(int node=0;node<NODES;node++) {
                                ModelPart p=nodes[node];int base=(model*NODES+node)*9;
                                v[base]=p.x;v[base+1]=p.y;v[base+2]=p.z;
                                v[base+3]=p.xRot;v[base+4]=p.yRot;v[base+5]=p.zRot;
                                v[base+6]=p.xScale;v[base+7]=p.yScale;v[base+8]=p.zScale;
                                f[model*NODES+node]=(p.visible?1:0)|(p.skipDraw?2:0);
                            }
                            instance=new WorldMeshInstanceRecord(67,model+1,model*NODES+1,-1,1,2,1,-1,
                                matrix.get(new float[16]),1280,720,0,0,0,WORLD_MESH_INSTANCE_FLAG_MODEL_RIG).withPackedLight(0xf000f0);
                            if(variants)instance=instance.withItemFoil(foil);
                        }
                        if(variants)instance=instance.withViewport(640,480).withModelSubmissionOrder(model);
                        instance.encodeInto(instances.asSlice((long)model*Struct.WORLD_MESH_INSTANCE_RECORD.byteSize(),
                            Struct.WORLD_MESH_INSTANCE_RECORD.byteSize()),null);
                        sum+=instance.meshGeneration()+instance.viewportWidth()+instance.packedLight();
                    }
                    MemorySegment poses;
                    if(nativeFrames) {owner.seal();poses=owner.poseData();}
                    else {
                        for(int node=0;node<MODELS*NODES;node++) {
                            MemorySegment.copy(v,node*9,poseStaging,ValueLayout.JAVA_FLOAT,node*40L,9);
                            poseStaging.set(ValueLayout.JAVA_INT,node*40L+36,f[node]);
                        }
                        poses=poseStaging;
                    }
                    if(fullReadback) {
                    // Every emitted field participates, with padding excluded.
                    for(int model=0;model<MODELS;model++) {
                        long base=(long)model*Struct.WORLD_MESH_INSTANCE_RECORD.byteSize();
                        for(int field=0;field<=40;field++) {
                            int bytes=switch(field) {case 9,10,21,22,36->8;case 13,26->64;case 18->12;
                                case 19,35->24;case 27,39->36;case 33,34->48;default->4;};
                            long offset=base+Struct.WORLD_MESH_INSTANCE_RECORD.offset(field);
                            for(int word=0;word<bytes;word+=4)sum=31*sum+instances.get(ValueLayout.JAVA_INT,offset+word);
                        }
                    }
                    for(int word=0;word<MODELS*NODES*10;word++)sum=31*sum+poses.get(ValueLayout.JAVA_INT,word*4L);
                    } else {
                        sum+=poses.get(ValueLayout.JAVA_INT,0)+poses.get(ValueLayout.JAVA_INT,(MODELS*NODES-1)*40L+36);
                    }
                } finally {if(owner!=null)owner.close();}
            }
            return sum;
        }
        @Override public void close(){arena.close();}
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        boolean current=args[0].equals("current"), variants=args[1].equals("variants");
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        var compiler=ManagementFactory.getCompilationMXBean();long thread=Thread.currentThread().threadId();
        try(var work=new Work()) {
            long reference=work.run(false,variants,true),actual=work.run(true,variants,true);
            if(reference!=actual)throw new AssertionError("Full field/pose replay differs");
            System.out.println("MODEL_EQUIVALENCE "+actual);
            long[][] rows=new long[8][];
            for(int round=-64;round<8;round++) {
                long compiled=compiler.getTotalCompilationTime(),bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                long sum=work.run(current,variants,false);long elapsed=System.nanoTime()-start;
                long allocated=bean.getThreadAllocatedBytes(thread)-bytes;blackhole=sum;
                if(round>=0)rows[round]=new long[]{elapsed,allocated,sum,compiler.getTotalCompilationTime()-compiled};
            }
            if(args.length>2) {
                try(var recording=new jdk.jfr.Recording()) {
                    recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(1));
                    recording.enable("jdk.NativeMethodSample").withPeriod(java.time.Duration.ofMillis(1));
                    recording.enable("jdk.ObjectAllocationSample").with("throttle","500/s");
                    recording.start();
                    for(int i=0;i<3000;i++)blackhole=work.run(current,variants,false);
                    recording.stop();recording.dump(Path.of(args[2]));
                }
                System.out.println("MODEL_PROFILE diagnostic only; timing rows excluded from acceptance");
            }
            for(long[] row:rows)System.out.println("MODEL_TIMING "+row[0]+" "+row[1]+" "+row[2]+" "+row[3]);
        }
        var nativePath=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so");
        System.out.println("NATIVE_IMAGE "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(nativePath))));
        for(String name:new String[]{"net.vulkanic.world.NativeModelFrame","net.vulkanic.bridge.VulkanicGalBridge","net.vulkanic.world.VerifyModelFrames"}) {
            var type=Class.forName(name,false,VerifyModelFrames.class.getClassLoader());
            var resource=type.getResource('/'+name.replace('.','/')+".class");
            try(var in=resource.openStream()) {System.out.println("CLASS_IMAGE "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))+" "+resource);}
        }
    }
}
