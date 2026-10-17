package net.vulkanic.world;

import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.vulkanic.bridge.VulkanicGalBridge.WorldMeshAssetRecord;
import net.vulkanic.bridge.VulkanicGalBridge.WorldMeshTextureAssetRecord;

/** Supplemental internal-policy replay and actual rig-admission observer.
 * The renderer control comes from the pinned previous Current source. */
public final class VerifyAssetRegistry {
    static void equal(Object a, Object b) { if (!Objects.equals(a,b)) throw new AssertionError(a+" != "+b); }
    static void replay() {
        var current = new WorldAssetRegistry<Long,String>(Long.class);
        var previous = new LegacyMembershipJournaledMap<Long,String>();
        var cm = current.mark(); var pm = previous.mark();
        var random = new Random(71);
        for (int i=0;i<200000;i++) {
            long key = switch(random.nextInt(20)) { case 0 -> Long.MIN_VALUE; case 1 -> Long.MAX_VALUE; default -> random.nextInt(256)-128; };
            String value = random.nextBoolean() ? "v"+random.nextInt(8) : null;
            switch(random.nextInt(20)) {
                case 0,1,2,3,4,5,6,7 -> equal(current.put(key,value),previous.put(key,value));
                case 8,9,10 -> equal(current.remove(key),previous.remove(key));
                case 11 -> { current.clear(); previous.clear(); }
                case 12 -> { current.resetJournal(); previous.resetJournal(); }
                case 13 -> { cm=current.mark(); pm=previous.mark(); equal(cm.epoch(),pm.epoch()); equal(cm.position(),pm.position()); }
                case 14 -> {
                    Object a,b;
                    try { a=current.keysAddedSince(cm); } catch(RuntimeException e) { a=e.getClass(); }
                    try { b=previous.keysAddedSince(pm); } catch(RuntimeException e) { b=e.getClass(); }
                    equal(a,b);
                }
                default -> { equal(current.get(key),previous.get(key)); equal(current.containsKey(key),previous.containsKey(key)); }
            }
            equal(current.size(),previous.size());
            if(i%127==0) {
                equal(new ArrayList<>(current.entrySet()),new ArrayList<>(previous.entrySet()));
                equal(new ArrayList<>(current.values()),new ArrayList<>(previous.values()));
            }
        }
        System.out.println("REGISTRY_REPLAY 200000 PASS");
    }
    static Field field(String name) throws Exception {
        var f=RustGalWorldPrimitiveRenderer.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
    static final class Rigs {
        final Object[] topologies=new Object[16];
        final MethodHandle prepare, setFrame;
        final Map<Integer,WorldMeshTextureAssetRecord> textures;
        final WorldMeshTextureAssetRecord[] replacements;
        long frame=1;
        @SuppressWarnings("unchecked") Rigs(boolean aliases) throws Exception {
            net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
            var assets=(Map<Long,WorldMeshAssetRecord>)field("WORLD_MESH_ASSETS").get(null);
            textures=(Map<Integer,WorldMeshTextureAssetRecord>)field("WORLD_MESH_TEXTURES").get(null);
            Class<?> extraction=Class.forName(RustGalWorldPrimitiveRenderer.class.getName()+"$BlockMeshExtraction");
            var ec=extraction.getDeclaredConstructors()[0];ec.setAccessible(true);
            Class<?> topology=Class.forName(RustGalWorldPrimitiveRenderer.class.getName()+"$CachedModelTopology");
            var tc=topology.getDeclaredConstructors()[0];tc.setAccessible(true);
            var method=RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("prepareModelRigLocked",topology);method.setAccessible(true);
            prepare=MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(long.class,Object.class));
            setFrame=MethodHandles.lookup().unreflectSetter(field("semanticFrameSequence"));
            for(int rig=0;rig<topologies.length;rig++) {
                int textureId=0x73420000+rig;
                var texture=new WorldMeshTextureAssetRecord(textureId,new byte[]{1,2});textures.put(textureId,texture);
                Object meshes=Array.newInstance(extraction,12);int[] parents=new int[12];Arrays.fill(parents,0);parents[0]=-1;
                for(int node=0;node<12;node++) {
                    long key=0x7d040000L+rig*12+node;
                    // Already-admitted immutable records isolate admission, not upload/GPU work.
                    var resident=new WorldMeshAssetRecord(key,101,2,1,List.of(),new byte[0],List.of());assets.put(key,resident);
                    var input=aliases?new WorldMeshAssetRecord(key,101,2,1,List.of(),new byte[0],List.of()):resident;
                    Array.set(meshes,node,ec.newInstance(key,101L,input,List.of(texture)));
                }
                topologies[rig]=tc.newInstance(new ModelPart[12],parents,meshes,Map.of());
            }
            replacements=new WorldMeshTextureAssetRecord[]{new WorldMeshTextureAssetRecord(0x73420000,new byte[]{1,2}),
                new WorldMeshTextureAssetRecord(0x73420000,new byte[]{1,2})};
        }
        long run(boolean churn) throws Throwable {
            long sum=0;
            for(int i=0;i<1024;i++) {
                setFrame.invokeExact(++frame);
                if(churn) textures.put(replacements[i&1].textureId(),replacements[i&1]);
                for(Object topology:topologies) sum+= (long)prepare.invokeExact(topology);
            }
            return sum;
        }
    }
    static volatile long blackhole;
    static void image(String name) throws Exception {
        Class<?> type=Class.forName(name, false, VerifyAssetRegistry.class.getClassLoader());
        var resource=type.getResource('/'+name.replace('.','/')+".class");
        byte[] data;
        try(var stream=resource.openStream()){data=stream.readAllBytes();}
        System.out.println("CLASS_IMAGE "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data))+" "+resource);
    }
    public static void main(String[] args) throws Throwable {
        if(args.length==0) replay();
        else {
            var rigs=new Rigs(args[0].equals("aliases"));
            var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
            var compiler=ManagementFactory.getCompilationMXBean();long thread=Thread.currentThread().threadId();
            long[][] rows=new long[8][];
            for(int round=-64;round<8;round++) {
                long compiled=compiler.getTotalCompilationTime(),bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                long sum=rigs.run(args[0].equals("churn"));
                long elapsed=System.nanoTime()-start,allocated=bean.getThreadAllocatedBytes(thread)-bytes;
                long compilation=compiler.getTotalCompilationTime()-compiled;blackhole=sum;
                if(round>=0)rows[round]=new long[]{elapsed,allocated,sum,compilation};
            }
            for(var row:rows)System.out.println("REGISTRY_TIMING "+row[0]+" "+row[1]+" "+row[2]+" "+row[3]);
        }
        image("net.vulkanic.world.RustGalWorldPrimitiveRenderer");
        image("net.vulkanic.world.DynamicWorldMeshLifetime");
        image(args.length==0 || System.getProperty("registry.side","current").equals("current")
            ? "net.vulkanic.world.WorldAssetRegistry" : "net.vulkanic.world.MembershipJournaledMap");
        var nativePath=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so").toRealPath();
        if(!Files.readString(Path.of("/proc/self/maps")).contains(nativePath.toString()))throw new AssertionError("Native image not loaded");
        System.out.println("NATIVE_IMAGE "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(nativePath)))+" "+nativePath);
    }
}
