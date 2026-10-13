package net.vulkanic.gui;

import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.vulkanic.bridge.NativeGuiItemTemplate;
import static net.vulkanic.bridge.VulkanicGalBridge.*;
import org.joml.Matrix3x2f;

/** Measures the actual cached producer, with source-compiled prior Java control.
 * This isolates instantiation; it is not Frozen or end-to-end timing evidence. */
public final class VerifyFlatItemFrames {
    static volatile long blackhole;
    static final class Work implements AutoCloseable {
        final Object topology;
        final Method instantiate, batches;
        final GuiItemRenderState item;
        final GuiItemCacheRecord cache;
        final int layers;
        final boolean foil;
        NativeGuiItemTemplate owner;
        Work(boolean current, String scenario) throws Exception {
            foil = !scenario.equals("plain"); layers = foil ? 4 : 2;
            String name = "net.vulkanic.gui."+(current ? "GuiFlatItemMeshCollector" : "PreviousFlatItemCollector");
            Class<?> collector = Class.forName(name), layer = Class.forName(name+"$FlatLayer"), type = Class.forName(name+"$FlatTopology");
            var constructLayer = layer.getDeclaredConstructors()[0]; constructLayer.setAccessible(true);
            var vertices = new ArrayList<GuiMeshVertexRecord>();
            for (int i=0;i<24;i++) vertices.add(new GuiMeshVertexRecord(new float[]{i%2,(i/2)%2,0.5F},
                new float[]{0.25F,0.75F},new float[]{0.25F,0.75F},-1,0x007f0000));
            var indices = new ArrayList<Integer>();
            for(int i=0;i<24;i+=4)indices.addAll(List.of(i,i+1,i+2,i+2,i+3,i));
            float[] transform={1,0,0,0,0,1,0,0,0,0,1,0,-.5F,-.5F,-.5F,1};
            var inputs = new ArrayList<Object>();
            for(int i=0;i<layers;i++) {
                boolean glint=foil && i%2==1;
                inputs.add(constructLayer.newInstance(glint?4:2,17L+i,transform,vertices,indices,glint,
                    glint && scenario.equals("special"),null));
            }
            var construct = type.getDeclaredConstructor(List.class,List.class);construct.setAccessible(true);
            topology=construct.newInstance(inputs,List.of());
            instantiate=collector.getDeclaredMethod("instantiate",GuiItemRenderState.class,int.class,int.class,int.class,
                int.class,type,StandardItemFoilRecord.class,GuiItemCacheRecord.class);instantiate.setAccessible(true);
            batches=Class.forName(name+"$Snapshot").getDeclaredMethod("batches");batches.setAccessible(true);
            item=new GuiItemRenderState("bench",new Matrix3x2f(),new TrackingItemStackRenderState(),12,34,new ScreenRectangle(1,2,100,120));
            cache=new GuiItemCacheRecord(91,foil,true);
            verify(17);verify(99);
        }
        @SuppressWarnings("unchecked") List<GuiMeshBatchRecord> frame(long clock) throws Exception {
            return (List<GuiMeshBatchRecord>)batches.invoke(instantiate.invoke(null,item,320,180,2,420,topology,
                foil?new StandardItemFoilRecord(clock,.5,.75F):null,cache));
        }
        void verify(long clock) throws Exception {
            var result=frame(clock);var batch=result.getFirst();owner=batch.nativeTemplate();
            if((owner==null?result.size():owner.batchCount())!=layers)throw new AssertionError("layers");
            if(owner!=null && (owner.vertexCount()!=24L*layers || owner.indexCount()!=36L*layers))throw new AssertionError("geometry count");
            if(batch.left()!=12 || batch.top()!=34 || batch.clipWidth()!=100 || batch.clipHeight()!=120)throw new AssertionError("placement");
            if(foil) {
                var fact=owner==null?result.get(1).itemFoil():batch.layerFoils()[0];
                if(fact.clockMillis()!=clock || fact.speed()!=.5 || fact.strength()!=.75F)throw new AssertionError("foil facts");
            }
            if(owner!=null) {
                var f=topology.getClass().getDeclaredField("layers");f.setAccessible(true);
                if(!((List<?>)f.get(topology)).isEmpty())throw new AssertionError("Java geometry still retained");
            }
        }
        long run() throws Exception {
            long sum=0;
            for(int frame=0;frame<128;frame++)for(int slot=0;slot<9;slot++) {
                var records=frame(frame*9L+slot);var first=records.getFirst();
                sum+=first.left()+first.top()+first.clipWidth()+first.sequence();
            }
            return sum;
        }
        public void close(){if(owner!=null)owner.close();}
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();var jit=ManagementFactory.getCompilationMXBean();
        long thread=Thread.currentThread().threadId();
        try(var work=new Work(args[0].equals("current"),args[1])) {
            System.out.println("FLAT_EQUIVALENCE placement, layer/geometry counts, current foil facts, ownership");
            for(int round=-256;round<8;round++) {
                long compiled=jit.getTotalCompilationTime(),bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                long sum=work.run(),elapsed=System.nanoTime()-start,allocated=bean.getThreadAllocatedBytes(thread)-bytes;
                blackhole=sum;
                if(round>=0)System.out.println("FLAT_TIMING "+elapsed+" "+allocated+" "+sum+" "+(jit.getTotalCompilationTime()-compiled));
            }
        }
        var path=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so");
        System.out.println("NATIVE_IMAGE "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        for(String name:new String[]{"net.vulkanic.gui.GuiFlatItemMeshCollector","net.vulkanic.gui.PreviousFlatItemCollector",
            "net.vulkanic.gui.VerifyFlatItemFrames","net.vulkanic.bridge.VulkanicGalBridge","net.vulkanic.bridge.NativeGuiItemTemplate"}) {
            var type=Class.forName(name,false,VerifyFlatItemFrames.class.getClassLoader());var resource=type.getResource('/'+name.replace('.','/')+".class");
            try(var in=resource.openStream()){System.out.println("CLASS_IMAGE "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))+" "+resource);}
        }
    }
}
