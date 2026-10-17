package net.minecraft.world.level.lighting;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

/** Real source/layer/virtual-enqueue effects and committed queue prefixes on Frozen/current. */
public final class VerifyLiveSkySourceContracts {
    static final List<String> trace = new ArrayList<>();
    static final Error MARKER = new AssertionError("live sky callback");

    static Object field(Object owner, String name) throws Exception {
        for (Class<?> type=owner.getClass();type!=null;type=type.getSuperclass()) {
            try {Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
            catch (NoSuchFieldException absent) {}
        }
        throw new NoSuchFieldException(name);
    }

    static Object invoke(Object owner, String name, Class<?>[] types, Object... values) throws Exception {
        Method method=owner.getClass().getDeclaredMethod(name,types);method.setAccessible(true);
        try {return method.invoke(owner,values);}
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Error error) throw error;
            if (failure.getCause() instanceof RuntimeException error) throw error;
            throw failure;
        }
    }

    static final class Sources extends ChunkSkyLightSources {
        final String name;
        int lowest;
        boolean reject;
        Sources(String name, int lowest) {super(LevelHeightAccessor.create(0,64));this.name=name;this.lowest=lowest;}
        @Override public int getLowestSourceY(int x,int z) {
            trace.add("read "+name+" "+x+" "+z);
            if (reject) throw MARKER;
            return name.equals("center") && (x!=0 || z!=0) ? Integer.MAX_VALUE : lowest;
        }
    }

    static final class Layer extends DataLayer {
        Runnable callback;
        boolean reject;
        int writes;
        Layer() {super(0);}
        @Override public DataLayer copy() {trace.add("copy");return this;}
        @Override public void set(int x,int y,int z,int value) {
            super.set(x,y,z,value);trace.add("set "+x+" "+y+" "+z+" "+value);writes++;
            Runnable action=callback;callback=null;if (action!=null) action.run();
            if (reject && writes==2) throw MARKER;
        }
    }


    static List<Long> queue(SkyLightEngine engine) throws Exception {
        // Observation happens after the invocation. Drain/restore the actual
        // queue in its original order; no expected queue is substituted.
        try {
            Object work=field(engine,"lightWork");
            int words=((Number)invoke(work,"size",new Class<?>[]{int.class},1)).intValue();
            var values=new ArrayList<Long>();
            for (int i=0;i<words;i++) values.add(((Number)invoke(work,"dequeue",new Class<?>[]{int.class},1)).longValue());
            for (int i=0;i<words;i+=2) invoke(work,"enqueue",new Class<?>[]{int.class,long.class,long.class},1,values.get(i),values.get(i+1));
            return values;
        } catch (NoSuchFieldException frozen) {
            var actual=(it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue)field(engine,"increaseQueue");
            var values=new ArrayList<Long>();while(!actual.isEmpty()) values.add(actual.dequeueLong());
            for(long value:values)actual.enqueue(value);
            return values;
        }
    }

    @SuppressWarnings("unchecked")
    static void run(String mode) throws Exception {
        trace.add("case "+mode);
        Class<?> type=Class.forName(VerifyLightCoordinator.class.getName()+"$Fixture");
        Constructor<?> constructor=type.getDeclaredConstructor(boolean.class);constructor.setAccessible(true);
        Object fixture=constructor.newInstance(true);Object world=field(fixture,"world");
        var chunks=(Map<Long,ChunkAccess>)field(world,"chunks");
        var center=new Sources("center",62);var north=new Sources("north",64);
        Field sources=ChunkAccess.class.getDeclaredField("skyLightSources");sources.setAccessible(true);
        sources.set(chunks.get(ChunkPos.asLong(0,0)),center);
        sources.set(chunks.get(ChunkPos.asLong(0,-1)),north);
        sources.set(chunks.get(ChunkPos.asLong(0,1)),new Sources("south",64));
        sources.set(chunks.get(ChunkPos.asLong(-1,0)),new Sources("west",64));
        sources.set(chunks.get(ChunkPos.asLong(1,0)),new Sources("east",64));
        var storage=new SkyLightSectionStorage((LightChunkGetter)world) {
            @Override protected int getTopSectionY(long column) {return 4;}
            @Override protected int getBottomSectionY() {return 3;}
        };
        var layer=new Layer();long key=SectionPos.asLong(0,3,0);
        storage.updatingSectionData.setLayer(key,layer);storage.updatingSectionData.clearCache();
        SkyLightEngine engine=new SkyLightEngine((LightChunkGetter)world,storage);
        if (mode.equals("neighbor-error")) north.reject=true;
        if (mode.equals("write-error")) layer.reject=true;
        if (mode.equals("nested")) layer.callback=()->{
            trace.add("nested begin");center.lowest=64;engine.propagateLightSources(new ChunkPos(0,0));trace.add("nested end");
        };
        boolean failed=false;
        try {engine.propagateLightSources(new ChunkPos(0,0));}
        catch (Error failure) {if (failure!=MARKER) throw failure;failed=true;trace.add("original error");}
        if (failed!=mode.endsWith("error")) throw new AssertionError("error identity/prefix changed");
        trace.add("pixels "+layer.get(0,15,0)+" "+layer.get(0,14,0));
        trace.add("queue "+queue(engine));
        trace.add("done "+failed);
        java.lang.ref.Reference.reachabilityFence(fixture);
        java.lang.ref.Reference.reachabilityFence(engine);
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        Field output=VerifyLightCoordinator.class.getDeclaredField("out");output.setAccessible(true);
        try(var quiet=new PrintWriter(OutputStream.nullOutputStream())) {
            output.set(null,quiet);
            for(String mode:List.of("neighbor-error","write-error","nested","native-queue")) run(mode);
        }
        Files.write(Path.of(args[0]),trace);
        System.out.println("LIVE_SKY_CONTRACTS 4 "+trace.size());
    }
}
